package com.iqge.sandbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import com.iqge.R;
import com.iqge.UiMotion;
import com.termux.app.iqcode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Human-facing sandbox manager intentionally hosted in IQ Code's stable main process.
 * BlackBox itself lives behind SandboxControlProvider in :iqsandbox. If the backend
 * process dies, this Activity stays alive and shows the startup stage instead of
 * disappearing with the backend.
 */
public final class SandboxDashboardActivity extends Activity {
    private static final int PICK_APK=4811;
    private static final String UI_PREFS="iqge_ui_preferences",UI_THEME_KEY="ui_theme";
    private int BG=0xFF090D18,CARD=0xFF131B2F,TEXT=0xFFEFF3FF,MUTED=0xFF99A6C4,ACCENT=0xFF6C8CFF,RED=0xFFFF8899,GREEN=0xFF73D69C;
    /** 由主题色派生：卡内次级底色（按钮/图标底）与分隔线。 */
    private int SURFACE_2=0xFF1D2740,SURFACE_3=0xFF26314E,BORDER_SOFT=0xFF2A3550;
    private boolean lightTheme,neonTheme;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout list; private TextView status; private View statusDot; private Switch rootHideSwitch, floatingLogSwitch;
    private volatile int startupRetry; private volatile boolean destroyed; private boolean syncingRootSwitch,rootSettingInFlight,syncingFloatingLog,floatingLogInFlight;

    @Override protected void onCreate(Bundle state){super.onCreate(state);applyThemeFromPreferences();applySystemBars();setContentView(build());}
    @Override protected void onResume(){
        super.onResume();
        int oldBg=BG,oldCard=CARD,oldText=TEXT,oldMuted=MUTED,oldAccent=ACCENT,oldRed=RED,oldGreen=GREEN;
        applyThemeFromPreferences();applySystemBars();
        if(list==null||oldBg!=BG||oldCard!=CARD||oldText!=TEXT||oldMuted!=MUTED||oldAccent!=ACCENT||oldRed!=RED||oldGreen!=GREEN)setContentView(build());
        reload();
    }

    private void applyThemeFromPreferences(){
        String theme=getSharedPreferences(UI_PREFS,MODE_PRIVATE).getString(UI_THEME_KEY,"classic");
        lightTheme="day".equals(theme);neonTheme=false;
        if(lightTheme){BG=0xFFF7F6F2;CARD=0xFFFDFCF9;TEXT=0xFF282723;MUTED=0xFF656159;ACCENT=0xFF37694B;RED=0xFFC2414B;GREEN=0xFF1C895B;}
        else if("custom".equals(theme)){
            android.content.SharedPreferences p=getSharedPreferences(UI_PREFS,MODE_PRIVATE);
            BG=p.getInt("palette_background",0xFF12110F);CARD=p.getInt("palette_surface",0xFF191816);TEXT=p.getInt("palette_text",0xFFF0ECE5);
            ACCENT=p.getInt("palette_accent",0xFF7EB288);GREEN=p.getInt("palette_green",0xFF7EB288);RED=p.getInt("palette_red",0xFFD6786F);MUTED=mix(TEXT,BG,.42f);
        }else{BG=0xFF12110F;CARD=0xFF191816;TEXT=0xFFF0ECE5;MUTED=0xFFA49D93;ACCENT=0xFF7EB288;RED=0xFFD6786F;GREEN=0xFF7EB288;}
        SURFACE_2=mix(CARD,TEXT,.06f);SURFACE_3=mix(CARD,TEXT,.14f);BORDER_SOFT=mix(CARD,TEXT,.14f);
    }

    private void applySystemBars(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(android.os.Build.VERSION.SDK_INT>=23){int flags=getWindow().getDecorView().getSystemUiVisibility()&~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(android.os.Build.VERSION.SDK_INT>=26)flags&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;if(lightTheme)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(lightTheme&&android.os.Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;getWindow().getDecorView().setSystemUiVisibility(flags);}
    }

    private int mix(int from,int to,float amount){return Color.rgb(Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*amount),Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*amount),Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*amount));}

    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();super.onDestroy();}

    private View build(){
        LinearLayout root=vbox();root.setPadding(dp(14),dp(10),dp(14),dp(12));root.setBackgroundColor(BG);

        LinearLayout top=hbox();top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=iconButton(R.drawable.ic_back,20,TEXT);back.setContentDescription("返回");back.setOnClickListener(v->finish());top.addView(back,lp(dp(40),dp(40)));
        LinearLayout titles=vbox();titles.setPadding(dp(12),0,dp(10),0);
        TextView title=text("IQ 沙箱",20,TEXT);title.setTypeface(Typeface.DEFAULT_BOLD);titles.addView(title,lp(-1,dp(25)));
        titles.addView(text("虚拟安卓运行环境 · BlackBox",10.5f,MUTED),lp(-1,dp(15)));
        top.addView(titles,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView debug=ghostButton("诊断",R.drawable.ic_info,ACCENT);debug.setOnClickListener(v->showDebug());top.addView(debug,lp(dp(82),dp(38)));
        root.addView(top,lp(-1,dp(50)));

        LinearLayout statusCard=card();
        LinearLayout statusRow=hbox();statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot=new View(this);statusDot.setBackground(round(GREEN,5));statusRow.addView(statusDot,lp(dp(10),dp(10)));
        LinearLayout statusCol=vbox();statusCol.setPadding(dp(12),0,0,0);
        status=text("正在连接沙箱后端…",12.5f,TEXT);
        LinearLayout.LayoutParams statusLp=lp(-1,-2);statusLp.setMargins(0,0,0,dp(3));statusCol.addView(status,statusLp);
        statusCol.addView(text("导入一个 APK，或让 Agent 调用 Sandbox 工具",10.5f,MUTED),lp(-1,-2));
        statusRow.addView(statusCol,new LinearLayout.LayoutParams(0,-2,1));
        statusCard.addView(statusRow,lp(-1,-2));
        root.addView(statusCard,stack(-2,0,10));

        LinearLayout settings=card();
        rootHideSwitch=new Switch(this);rootHideSwitch.setChecked(true);rootHideSwitch.setEnabled(false);
        rootHideSwitch.setThumbTintList(ColorStateList.valueOf(ACCENT));rootHideSwitch.setTrackTintList(ColorStateList.valueOf(SURFACE_3));
        rootHideSwitch.setOnCheckedChangeListener((button,hidden)->{if(!syncingRootSwitch)confirmRootVisibilityChange(hidden);});
        settings.addView(settingRow(R.drawable.ic_settings,"隐藏 Root","Guest 隐藏常见 root 路径与管理包",rootHideSwitch),lp(-1,dp(56)));
        settings.addView(divider(),lp(-1,dp(1)));
        floatingLogSwitch=new Switch(this);floatingLogSwitch.setChecked(true);floatingLogSwitch.setEnabled(false);
        floatingLogSwitch.setThumbTintList(ColorStateList.valueOf(ACCENT));floatingLogSwitch.setTrackTintList(ColorStateList.valueOf(SURFACE_3));
        floatingLogSwitch.setOnCheckedChangeListener((button,enabled)->{if(!syncingFloatingLog)applyFloatingLog(enabled);});
        settings.addView(settingRow(R.drawable.ic_floating,"日志悬浮窗","在 Guest 界面显示可拖动的调试浮层",floatingLogSwitch),lp(-1,dp(56)));
        root.addView(settings,stack(-2,0,12));

        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);
        Button install=primaryButton("导入 APK",R.drawable.ic_plus);install.setOnClickListener(v->pick());actions.addView(install,new LinearLayout.LayoutParams(0,dp(46),1));
        Button refresh=ghostButton("刷新",R.drawable.ic_refresh,TEXT);refresh.setOnClickListener(v->reload());
        LinearLayout.LayoutParams refreshLp=lp(dp(94),dp(46));refreshLp.setMargins(dp(10),0,0,0);actions.addView(refresh,refreshLp);
        root.addView(actions,lp(-1,dp(46)));

        TextView section=text("沙箱应用",11,MUTED);section.setPadding(dp(4),dp(14),0,dp(6));root.addView(section,lp(-1,dp(32)));
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);list=vbox();scroll.addView(list,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        UiMotion.bindInteractive(root);return root;
    }

    private void reload(){
        if(destroyed)return;
        worker.execute(()->{
            JSONObject st=SandboxHostClient.call(this,"status",new JSONObject());
            if(destroyed)return;
            if(!st.optBoolean("ok")) { retryOrShow(st.optString("error","沙箱后端不可用")); return; }
            JSONObject r=SandboxHostClient.call(this,"list",new JSONObject());
            if(destroyed)return;
            if(!r.optBoolean("ok")) { retryOrShow(r.optString("error","读取沙箱应用失败")); return; }
            startupRetry=0;
            List<String> apps=new ArrayList<>();JSONArray a=r.optJSONArray("apps");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){String p=o.optString("package","");if(!p.isEmpty())apps.add(p);}}
            apps.sort(String.CASE_INSENSITIVE_ORDER);
            String s=st.optString("status","沙箱后端在线");boolean hideRoot=st.optBoolean("hide_root",true);boolean showLog=st.optBoolean("show_floating_log",false);runOnUiThread(()->{if(destroyed)return;setStatus(s+" · "+apps.size()+" 个应用",GREEN);setRootSwitch(hideRoot,!rootSettingInFlight);setFloatingLog(showLog,!floatingLogInFlight);render(apps);});
        });
    }


    private void retryOrShow(String error){
        if(destroyed)return;
        String e=error==null?"":error;
        if(startupRetry<2 && (e.contains("初始化")||e.contains("timeout")||e.contains("超时")||e.contains("Application.onCreate"))){
            startupRetry++;
            runOnUiThread(()->{if(!destroyed)setStatus("沙箱后端启动中… 自动重试 "+startupRetry+"/2",ACCENT);});
            try{Thread.sleep(450L*startupRetry);}catch(InterruptedException x){Thread.currentThread().interrupt();return;}
            reload();
            return;
        }
        showBackendError(e);
    }

    private void showBackendError(String error){if(destroyed)return;String stage=readStartupStage();runOnUiThread(()->{if(destroyed)return;setStatus("沙箱后端异常",RED);if(rootHideSwitch!=null)rootHideSwitch.setEnabled(false);list.removeAllViews();LinearLayout box=card();TextView head=text("BlackBox 后端没有正常响应",13,RED);head.setTypeface(Typeface.DEFAULT_BOLD);head.setCompoundDrawablePadding(dp(8));head.setCompoundDrawables(vectorIcon(R.drawable.ic_warning,16,RED),null,null,null);box.addView(head,lp(-1,dp(30)));TextView detail=text(error+"\n\n最后启动阶段：\n"+stage,11,MUTED);detail.setTextIsSelectable(true);detail.setPadding(0,dp(4),0,dp(8));box.addView(detail,lp(-1,-2));TextView tip=text("点右上角「诊断」可查看完整 IQSandbox 事件。",10.5f,ACCENT);box.addView(tip,lp(-1,dp(26)));list.addView(box,stack(-2,2,10));});}

    private void confirmRootVisibilityChange(boolean hidden){
        if(destroyed||rootSettingInFlight)return;
        boolean previous=!hidden;
        setRootSwitch(previous,true);
        String title=hidden?"开启 Root 隐藏？":"关闭 Root 隐藏？";
        String message=(hidden?"Guest 将隐藏常见 Root 路径和管理包。":"Guest 将可以发现并请求设备上的 su，授权仍由设备 Root 管理器决定。")+"\n\n所有正在运行的 Guest 将停止，重新启动后生效。";
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->applyRootVisibility(hidden,previous)).show();
    }

    private void applyRootVisibility(boolean hidden,boolean previous){
        if(destroyed)return;
        rootSettingInFlight=true;
        setRootSwitch(previous,false);
        worker.execute(()->{
            try{
                JSONObject r=SandboxHostClient.call(this,"set_hide_root",new JSONObject().put("hide_root",hidden));
                if(!r.optBoolean("ok"))throw new IllegalStateException(r.optString("error","设置失败"));
                boolean effective=r.optBoolean("hide_root",hidden);
                runOnUiThread(()->{if(destroyed)return;rootSettingInFlight=false;setRootSwitch(effective,true);toast("Root 隐藏已"+(effective?"开启":"关闭"));reload();});
            }catch(Throwable e){
                runOnUiThread(()->{if(destroyed)return;rootSettingInFlight=false;setRootSwitch(previous,true);toast("Root 隐藏设置失败: "+e.getMessage());});
            }
        });
    }

    private void setRootSwitch(boolean hidden,boolean enabled){
        if(rootHideSwitch==null)return;
        syncingRootSwitch=true;
        try{rootHideSwitch.setChecked(hidden);rootHideSwitch.setEnabled(enabled);}finally{syncingRootSwitch=false;}
    }

    private void setFloatingLog(boolean enabled,boolean interactive){
        if(floatingLogSwitch==null)return;
        syncingFloatingLog=true;
        try{floatingLogSwitch.setChecked(enabled);floatingLogSwitch.setEnabled(interactive);}finally{syncingFloatingLog=false;}
    }

    private void applyFloatingLog(boolean enabled){
        if(destroyed||floatingLogInFlight)return;
        floatingLogInFlight=true;setFloatingLog(!enabled,false);
        worker.execute(()->{
            try{
                JSONObject r=SandboxHostClient.call(this,"set_show_floating_log",new JSONObject().put("show_floating_log",enabled));
                if(!r.optBoolean("ok"))throw new IllegalStateException(r.optString("error","设置失败"));
                boolean effective=r.optBoolean("show_floating_log",enabled);
                runOnUiThread(()->{if(destroyed)return;floatingLogInFlight=false;setFloatingLog(effective,true);toast("日志悬浮窗已"+(effective?"开启":"关闭"));reload();});
            }catch(Throwable e){runOnUiThread(()->{if(destroyed)return;floatingLogInFlight=false;setFloatingLog(!enabled,true);toast("日志悬浮窗设置失败: "+e.getMessage());});}
        });
    }

    private void render(List<String> apps){
        list.removeAllViews();
        if(apps.isEmpty()){
            LinearLayout empty=vbox();empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(34),0,dp(34));
            TextView icon=iconOnly(R.drawable.ic_box,34,MUTED);empty.addView(icon,lp(-1,dp(40)));
            TextView hint=text("还没有沙箱应用\n导入一个 APK，或让 Agent 调用 Sandbox install。",11.5f,MUTED);hint.setGravity(Gravity.CENTER);empty.addView(hint,lp(-1,dp(48)));
            list.addView(empty,stack(-2,0,10));return;
        }
        for(int i=0;i<apps.size();i++){View row=appRow(apps.get(i));UiMotion.bindInteractive(row);list.addView(row,stack(-2,0,10));UiMotion.listItemIn(row,i);}
    }

    private View appRow(String pkg){
        LinearLayout card=card();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar=text(initialOf(pkg),16,ACCENT);avatar.setTypeface(Typeface.DEFAULT_BOLD);avatar.setGravity(Gravity.CENTER);avatar.setBackground(round(mix(CARD,ACCENT,.18f),13));head.addView(avatar,lp(dp(40),dp(40)));
        LinearLayout meta=vbox();meta.setPadding(dp(10),0,0,0);
        TextView name=text(pkg,13.5f,TEXT);name.setTypeface(Typeface.DEFAULT_BOLD);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.addView(name,lp(-1,dp(19)));
        meta.addView(text("已安装到 IQ 沙箱",10.5f,MUTED),lp(-1,dp(15)));
        head.addView(meta,new LinearLayout.LayoutParams(0,dp(40),1));
        card.addView(head,lp(-1,dp(40)));
        card.addView(divider(),stack(1,8,8));
        LinearLayout row=hbox();
        row.addView(chip("运行",R.drawable.ic_arrow_up,GREEN,()->rpcAction("launch",pkg,"已启动")),chipLp(0,4));
        row.addView(chip("停止",R.drawable.ic_stop,TEXT,()->rpcAction("stop",pkg,"已停止")),chipLp(1,4));
        row.addView(chip("清数据",R.drawable.ic_minus,TEXT,()->confirm("清除沙箱数据？",()->rpcAction("clear_data",pkg,"已清除"))),chipLp(2,4));
        row.addView(chip("卸载",R.drawable.ic_close,RED,()->confirm("从 IQ 沙箱卸载？",()->rpcAction("uninstall",pkg,"已卸载"))),chipLp(3,4));
        card.addView(row,lp(-1,dp(40)));
        LinearLayout dbg=hbox();
        dbg.addView(chip("进程 / SO 基址",R.drawable.ic_files,MUTED,()->showProcesses(pkg)),chipLp(0,2));
        dbg.addView(chip("Frida 动态调试",R.drawable.ic_skill,ACCENT,()->fridaFor(pkg)),chipLp(1,2));
        LinearLayout.LayoutParams dbgLp=lp(-1,dp(40));dbgLp.setMargins(0,dp(8),0,0);card.addView(dbg,dbgLp);
        return card;
    }

    private void rpcAction(String action,String pkg,String okText){worker.execute(()->{try{JSONObject p=new JSONObject().put("package",pkg);JSONObject r=SandboxHostClient.call(this,action,p);if(!r.optBoolean("ok"))throw new IllegalStateException(r.optString("error","操作失败"));if("launch".equals(action)&&!r.optBoolean("success",true))throw new IllegalStateException("没有可启动 Activity");runOnUiThread(()->{toast(okText);reload();});}catch(Throwable e){runOnUiThread(()->toast(action+": "+e.getMessage()));}});}

    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.android.package-archive");startActivityForResult(i,PICK_APK);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=PICK_APK||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();worker.execute(()->installUri(uri));}
    private void installUri(Uri uri){
        try{File dir=new File(getCacheDir(),"sandbox-import");dir.mkdirs();String name=fileName(uri);File target=new File(dir,System.currentTimeMillis()+"-"+name.replaceAll("[^A-Za-z0-9._-]","_"));try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(target)){if(in==null)throw new IllegalArgumentException("无法读取 APK");byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}PackageInfo pi=getPackageManager().getPackageArchiveInfo(target.getAbsolutePath(),PackageManager.GET_ACTIVITIES);if(pi==null||pi.packageName==null)throw new IllegalArgumentException("不是有效普通 APK");if(getPackageName().equals(pi.packageName))throw new IllegalArgumentException("不能导入 IQ Code 自身");JSONObject r=SandboxHostClient.call(this,"install",new JSONObject().put("path",target.getAbsolutePath()));if(!r.optBoolean("ok")||!r.optBoolean("success"))throw new IllegalStateException(r.optString("error",r.optString("message","安装失败")));runOnUiThread(()->{toast("已安装到沙箱: "+r.optString("package",pi.packageName));reload();});}catch(Throwable e){runOnUiThread(()->toast("安装失败: "+e.getMessage()));}
    }

    private List<JSONObject> processList(String pkg)throws Exception{JSONObject r=SandboxHostClient.call(this,"process_list",new JSONObject().put("package",pkg));if(!r.optBoolean("ok"))throw new IllegalStateException(r.optString("error","读取进程失败"));List<JSONObject> out=new ArrayList<>();JSONArray a=r.optJSONArray("processes");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(o);}return out;}
    private void fridaFor(String pkg){worker.execute(()->{try{List<JSONObject> ps=processList(pkg);if(ps.isEmpty()){runOnUiThread(()->toast("先运行 Guest，再加载 Frida"));return;}JSONObject chosen=ps.get(0);for(JSONObject p:ps)if(pkg.equals(p.optString("process"))){chosen=p;break;}int pid=chosen.optInt("pid",-1);if(pid<=0)throw new IllegalStateException("无有效 PID");if(!FridaRuntimeManager.isInstalled(this)){final int fpid=pid;runOnUiThread(()->new AlertDialog.Builder(this).setTitle("安装 Frida Gadget "+FridaRuntimeManager.VERSION).setMessage("首次使用需要从 Frida 官方发布页下载 arm64 Gadget并校验固定 SHA-256。").setNegativeButton("取消",null).setPositiveButton("安装并加载",(d,w)->worker.execute(()->installAndLoadFrida(pkg,fpid))).show());return;}loadFrida(pkg,pid);}catch(Throwable e){runOnUiThread(()->toast("Frida: "+e.getMessage()));}});}
    private void installAndLoadFrida(String pkg,int pid){try{runOnUiThread(()->setStatus("正在安装 Frida Gadget "+FridaRuntimeManager.VERSION+"…",ACCENT));FridaRuntimeManager.install(this,new TermuxShellExecutor(this),TermuxConstants.TERMUX_HOME_DIR_PATH);loadFrida(pkg,pid);}catch(Throwable e){runOnUiThread(()->toast("Frida 安装失败: "+e.getMessage()));}}
    private void loadFrida(String pkg,int pid){try{JSONObject r=SandboxAgentBridge.request(this,"proc_frida_load",pkg,pid,new JSONObject().put("pid",pid),15000);runOnUiThread(()->showMonoDialog(pkg+" · Frida",r.toString()));}catch(Throwable e){runOnUiThread(()->toast("Frida 加载失败: "+e.getMessage()));}}
    private void showProcesses(String pkg){worker.execute(()->{try{StringBuilder b=new StringBuilder();List<JSONObject> ps=processList(pkg);if(ps.isEmpty())b.append("当前没有运行进程。先启动这个 Guest。\n");for(JSONObject p:ps){int pid=p.optInt("pid",-1);b.append("PID ").append(pid).append(" · ").append(p.optString("process")).append("\n");JSONObject r=SandboxAgentBridge.request(this,"proc_modules",pkg,pid,new JSONObject().put("pid",pid).put("max_modules",80),4000);if(r.optBoolean("ok")){JSONArray mods=r.optJSONArray("modules");if(mods!=null){int shown=0;for(int i=0;i<mods.length()&&shown<24;i++){JSONObject m=mods.optJSONObject(i);if(m==null)continue;String path=m.optString("path","");if(!(path.endsWith(".so")||path.contains(".apk")))continue;b.append("  ").append(m.optString("base","?")).append("  ").append(new File(path).getName()).append("\n");shown++;}}}else b.append("  [调试桥] ").append(r.optString("error","未响应")).append("\n");b.append('\n');}String text=b.toString();runOnUiThread(()->showMonoDialog(pkg+" · 进程 / SO 基址",text));}catch(Throwable e){runOnUiThread(()->toast("读取进程失败: "+e.getMessage()));}});}

    private void showDebug(){
        if(status!=null)setStatus("正在读取诊断信息…",ACCENT);
        worker.execute(()->{
            String s=SandboxDebugLog.snapshot(this)+"\n===== STARTUP STAGE =====\n"+readStartupStage();
            runOnUiThread(()->{if(!destroyed)showMonoDialog("IQ 沙箱诊断",s);});
        });
    }
    private String readStartupStage(){File f=new File(getFilesDir(),"sandbox/startup-stage.txt");if(!f.isFile())return "(尚无后端启动记录)";try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[(int)Math.min(f.length(),8192)];int n=in.read(b);return n<=0?"(空)":new String(b,0,n,StandardCharsets.UTF_8).trim();}catch(Throwable e){return "读取失败: "+e;}}
    private void showMonoDialog(String title,String s){TextView body=text(s,10,TEXT);body.setTypeface(Typeface.MONOSPACE);body.setTextIsSelectable(true);ScrollView sc=new ScrollView(this);sc.setPadding(dp(12),dp(8),dp(12),dp(8));sc.addView(body);new AlertDialog.Builder(this).setTitle(title).setView(sc).setPositiveButton("关闭",null).show();}
    private void confirm(String title,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->yes.run()).show();}
    private String fileName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String s=c.getString(0);if(s!=null&&!s.isEmpty())return s;}}catch(Throwable ignored){}return "imported.apk";}
    private void toast(String s){Toast.makeText(this,s==null?"":s,Toast.LENGTH_SHORT).show();}

    /** 顶部状态卡：文案与状态点颜色一起变，避免只改文字看不出成败。 */
    private void setStatus(String text,int dotColor){
        if(status!=null)status.setText(text);
        if(statusDot!=null&&statusDot.getBackground() instanceof GradientDrawable)((GradientDrawable)statusDot.getBackground()).setColor(dotColor);
    }

    private LinearLayout vbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout hbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout card(){LinearLayout c=vbox();c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackground(round(CARD,18));return c;}
    private View divider(){View v=new View(this);v.setBackgroundColor(BORDER_SOFT);return v;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private TextView iconOnly(int res,int sizeDp,int color){TextView t=text("",sizeDp,color);t.setGravity(Gravity.CENTER);t.setCompoundDrawables(vectorIcon(res,sizeDp,color),null,null,null);return t;}
    private TextView iconButton(int res,int sizeDp,int color){TextView t=iconOnly(res,sizeDp,color);t.setBackground(round(SURFACE_2,20));return t;}
    private View settingRow(int iconRes,String label,String hint,Switch sw){
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(iconOnly(iconRes,18,ACCENT),lp(dp(28),dp(40)));
        LinearLayout col=vbox();col.setPadding(dp(6),0,dp(8),0);
        col.addView(text(label,12.5f,TEXT),lp(-1,dp(20)));
        TextView sub=text(hint,9.5f,MUTED);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);col.addView(sub,lp(-1,dp(15)));
        row.addView(col,new LinearLayout.LayoutParams(0,dp(40),1));
        row.addView(sw,lp(dp(54),dp(40)));return row;
    }
    private Button baseButton(String label,int iconRes,int color){
        Button b=new Button(this);b.setText(label);b.setTextSize(10.5f);b.setAllCaps(false);b.setTextColor(color);b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);
        b.setPadding(dp(2),0,dp(2),0);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setStateListAnimator(null);
        b.setCompoundDrawablePadding(dp(5));b.setCompoundDrawables(vectorIcon(iconRes,13,color),null,null,null);return b;
    }
    private Button chip(String label,int iconRes,int color,Runnable onClick){Button b=baseButton(label,iconRes,color);b.setBackground(round(SURFACE_2,13));b.setOnClickListener(v->onClick.run());return b;}
    private Button primaryButton(String label,int iconRes){
        float luminance=(Color.red(ACCENT)*.299f+Color.green(ACCENT)*.587f+Color.blue(ACCENT)*.114f)/255f;
        int ink=luminance>.62f?0xFF12110F:0xFFFFFFFF;
        Button b=baseButton(label,iconRes,ink);b.setTextSize(12.5f);
        b.setCompoundDrawables(vectorIcon(iconRes,15,ink),null,null,null);b.setBackground(accentGradient(12));return b;
    }
    private Button ghostButton(String label,int iconRes,int color){Button b=baseButton(label,iconRes,color);b.setBackground(round(SURFACE_2,13));return b;}
    private LinearLayout.LayoutParams stack(int heightDp,int topDp,int bottomDp){LinearLayout.LayoutParams p=lp(-1,heightDp>0?dp(heightDp):-2);p.setMargins(0,dp(topDp),0,dp(bottomDp));return p;}
    private LinearLayout.LayoutParams chipLp(int index,int count){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);if(index<count-1)p.setMargins(0,0,dp(8),0);return p;}
    private static String initialOf(String pkg){for(int i=0;i<pkg.length();i++){char c=pkg.charAt(i);if(Character.isLetterOrDigit(c))return String.valueOf(Character.toUpperCase(c));}return "A";}
    private GradientDrawable round(int fill,float radiusDp){return round(fill,radiusDp,Color.TRANSPARENT,0);}
    private GradientDrawable round(int fill,float radiusDp,int stroke,int strokeDp){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radiusDp));if(strokeDp>0)d.setStroke(dp(strokeDp),stroke);return d;}
    private GradientDrawable accentGradient(float radiusDp){return round(ACCENT,radiusDp);}
    private Drawable vectorIcon(int res,int sizeDp,int color){Drawable d=getResources().getDrawable(res,getTheme());d.setBounds(0,0,dp(sizeDp),dp(sizeDp));d.setTint(color);return d;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
