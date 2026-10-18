package com.iqge.opencode;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.termux.app.iqcode.termux.TermuxShellExecutor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenCode Desktop 会话：负责准备 → 启动 → 输入桥 + 状态回调的编排。
 * 生命周期由 Activity 持有，所有阻塞动作都丢到后台线程，回调回主线程。
 */
public final class OpenCodeSession {
    public interface Observer {
        void onStatus(String text);
        void onLog(String line);
        void onReady();
        void onFailed(String reason);
    }

    private final Context context;
    private final Observer observer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private OpenCodeInputBridge input;
    private volatile boolean ready;

    public OpenCodeSession(Context c, Observer observer) {
        this.context = c.getApplicationContext();
        this.observer = observer;
    }

    public boolean isReady() { return ready; }

    public OpenCodeInputBridge input() { return input; }

    private void status(String s) { main.post(() -> { if (observer != null) observer.onStatus(s); }); }
    private void log(String s) { main.post(() -> { if (observer != null) observer.onLog(s); }); }

    public void prepareAndStart() {
        if (!busy.compareAndSet(false, true)) { log("[IQGE] 已有任务在运行"); return; }
        new Thread(() -> {
            try {
                boolean provisioned = OpenCodeRuntime.isProvisioned(context);
                if (!provisioned) {
                    status("首次准备：安装 X11 / glibc 运行库 / OpenCode Desktop（约 200MB）");
                    TermuxShellExecutor.Result r = OpenCodeRuntime.provision(context, this::log);
                    if (r.exitCode != 0) { fail("运行时准备失败 (exit " + r.exitCode + ")"); return; }
                }
                status("启动 X 服务器 / 输入泵 / OpenCode Desktop");
                TermuxShellExecutor.Result r = OpenCodeRuntime.startAll(context, this::log);
                if (r.exitCode != 0) { fail("启动失败 (exit " + r.exitCode + ")"); return; }
                input = new OpenCodeInputBridge(context);
                ready = true;
                status("OpenCode Desktop 已启动");
                main.post(() -> { if (observer != null) observer.onReady(); });
            } catch (Exception e) {
                fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                busy.set(false);
            }
        }, "opencode-start").start();
    }

    public void stop() {
        closeInput();
        new Thread(() -> {
            try { OpenCodeRuntime.stopAll(context); }
            catch (Exception e) { log("[IQGE] 停止时出错: " + e.getMessage()); }
            finally { ready = false; status("已停止"); }
        }, "opencode-stop").start();
    }

    private void fail(String reason) {
        status(reason);
        main.post(() -> { if (observer != null) observer.onFailed(reason); });
    }

    public void closeInput() {
        if (input != null) { input.close(); input = null; }
    }
}
