package com.termux.app.iqcode.tools;

import com.iqge.shizuku.ShizukuShell;
import com.termux.app.iqcode.model.SessionConfig;
import com.termux.app.iqcode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;

/** Explicit, opt-in shell-identity command surface backed by Shizuku; needs no device root. */
public final class ShizukuBashTool implements IQTool {
    @Override public String name(){return "Shizuku";}
    @Override public String description(){return "Run an Android shell command through Shizuku (adb-shell equivalent: uid 2000, or uid 0 when Shizuku was started with root). Use it for shell-level system work the app-uid Bash cannot do (pm/am/settings/appops/cmd/dumpsys/input/screencap); keep ordinary file work in Bash. The embedded Termux userland stays app-private.";}
    @Override public PermissionKind permissionKind(){return PermissionKind.SYSTEM;}

    @Override public JSONObject inputSchema(){
        JSONObject p=new JSONObject();
        try{
            p.put("command",ToolSchemas.string("Command to execute as the Android shell user."));
            p.put("cwd",ToolSchemas.string("Optional absolute working directory the shell user can enter (default /). Paths inside this app's private Termux home are not readable by the shell user."));
            p.put("timeout_ms",ToolSchemas.integer("Timeout in milliseconds (maximum 3600000).",1000));
        }catch(Exception e){throw new IllegalStateException(e);}
        return ToolSchemas.object(p,"command");
    }

    @Override public ToolExecutionResult execute(SessionConfig config,JSONObject input)throws Exception{return execute(config,input,null);}

    @Override public ToolExecutionResult execute(SessionConfig config,JSONObject input,ProgressListener progress)throws Exception{
        if(config==null||!config.shizukuExecutionEnabled)return ToolExecutionResult.error("Agent Shizuku is disabled in Settings");
        String command=input.optString("command","").trim();if(command.isEmpty())return ToolExecutionResult.error("Shizuku command is empty");
        String cwd=input.optString("cwd","").trim();
        if(!cwd.isEmpty()&&!new File(cwd).isAbsolute())return ToolExecutionResult.error("cwd must be an absolute path the shell user can enter");
        int timeout=Math.max(1000,Math.min(3_600_000,input.optInt("timeout_ms",ShizukuShell.DEFAULT_TIMEOUT_MS)));
        String verified="uid=$(id -u); if [ \"$uid\" != 2000 ] && [ \"$uid\" != 0 ]; then echo \"[IQGE] Shizuku 未以 shell/root 身份运行 (uid=$uid)\" >&2; exit 126; fi; "+command;
        ShizukuShell.OutputListener live=progress==null?null:(chunk,stderr,elapsed)->progress.onProgress(chunk,stderr,elapsed);
        ShizukuShell.Result result=ShizukuShell.execute(verified,cwd,timeout,live);
        return ToolExecutionResult.command(result.exitCode,result.combined());
    }
}
