package com.iqge.shizuku;

import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * 用 Shizuku 以 shell 身份执行命令（等价于 adb shell），不需要设备 Root。
 *
 * IShizukuService 的 newProcess 由 Shizuku 服务端 fork 出进程，实际 uid 是 shell(2000)——如果用户的
 * Shizuku 是用 Root 启动的，则可能是 0。输出/超时/退出码的形态与
 * {@link com.termux.app.iqcode.termux.TermuxShellExecutor.Result} 对齐，方便上层复用。
 */
public final class ShizukuShell {
    public static final int DEFAULT_TIMEOUT_MS = 120_000;
    public static final int MAX_CAPTURE_CHARS = 2_000_000;
    private static final long POLL_SLICE_MS = 250L;

    /** 实时输出回调，语义与 TermuxShellExecutor.OutputListener 一致。 */
    public interface OutputListener {
        void onOutput(String chunk, boolean stderr, long elapsedMs);
    }

    public static final class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;

        Result(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }

        public String combined() {
            StringBuilder out = new StringBuilder();
            if (!stdout.isEmpty()) out.append(stdout);
            if (!stderr.isEmpty()) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                out.append("[stderr]\n").append(stderr);
            }
            if (timedOut) out.append("\n[command timed out]");
            if (out.length() == 0) out.append("(no output)");
            return out.toString();
        }
    }

    private ShizukuShell() { }

    /** Shizuku 是否已授权并且服务在线。 */
    public static boolean ready() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 以 shell 身份执行 command。cwd 为空时用 "/"（shell 身份进不去应用私有目录，别拿项目目录当默认值）；
     * 超时会 destroy 远端进程并返回 timedOut=true（退出码按 124 处理）。
     */
    public static Result execute(String command, String cwd, int timeoutMs, OutputListener listener) throws Exception {
        if (command == null || command.trim().isEmpty()) throw new IllegalArgumentException("command is empty");
        IShizukuService service = service();
        String dir = cwd == null || cwd.trim().isEmpty() ? "/" : cwd.trim();
        IRemoteProcess process = service.newProcess(new String[]{"sh", "-c", command}, null, dir);
        if (process == null) throw new IllegalStateException("Shizuku 没有返回进程（命令被拒绝或参数非法）");
        long started = System.currentTimeMillis();
        StreamDrain out = null;
        StreamDrain err = null;
        try {
            closeQuietly(process.getOutputStream());
            out = new StreamDrain(process.getInputStream(), false, started, listener);
            err = new StreamDrain(process.getErrorStream(), true, started, listener);
            out.start();
            err.start();
            long deadline = started + Math.max(1, timeoutMs);
            boolean finished = false;
            while (true) {
                if (process.waitForTimeout(POLL_SLICE_MS, TimeUnit.MILLISECONDS.toString())) { finished = true; break; }
                if (Thread.currentThread().isInterrupted()) {
                    destroyQuietly(process);
                    throw new InterruptedException("Shizuku command cancelled");
                }
                if (System.currentTimeMillis() >= deadline) break;
            }
            if (!finished) {
                destroyQuietly(process);
                out.joinQuietly(1_500L);
                err.joinQuietly(1_500L);
                return new Result(124, out.text(), err.text(), true);
            }
            int exitCode;
            try {
                exitCode = process.exitValue();
            } catch (Throwable e) {
                exitCode = 1;
            }
            out.joinQuietly(1_500L);
            err.joinQuietly(1_500L);
            return new Result(exitCode, out.text(), err.text(), false);
        } finally {
            if (out == null || err == null) destroyQuietly(process);
            if (out != null) out.close();
            if (err != null) err.close();
        }
    }

    public static Result execute(String command, String cwd, int timeoutMs) throws Exception {
        return execute(command, cwd, timeoutMs, null);
    }

    private static IShizukuService service() {
        try {
            if (!Shizuku.pingBinder()) throw new IllegalStateException("Shizuku 服务未运行：请先打开 Shizuku 并启动服务");
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                throw new IllegalStateException("IQ Code 还没有 Shizuku 授权：请在 设置 → Shizuku 里申请");
            }
            IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
            if (service == null) throw new IllegalStateException("Shizuku 服务不可用");
            return service;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            String message = e.getMessage();
            throw new IllegalStateException("连接 Shizuku 失败：" + (message == null ? e.toString() : message));
        }
    }

    private static void destroyQuietly(IRemoteProcess process) {
        try { process.destroy(); } catch (Throwable ignored) { }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        try { if (descriptor != null) descriptor.close(); } catch (Throwable ignored) { }
    }

    /** 逐块读取远端流，边读边回调，并限制累计字符数避免刷爆上下文。 */
    private static final class StreamDrain extends Thread {
        private final ParcelFileDescriptor descriptor;
        private final boolean stderr;
        private final long startedAt;
        private final OutputListener listener;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamDrain(ParcelFileDescriptor descriptor, boolean stderr, long startedAt, OutputListener listener) {
            this.descriptor = descriptor;
            this.stderr = stderr;
            this.startedAt = startedAt;
            this.listener = listener;
            setDaemon(true);
        }

        @Override public void run() {
            InputStream in = null;
            try {
                if (descriptor == null) return;
                in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    if (buffer.size() < MAX_CAPTURE_CHARS) buffer.write(chunk, 0, read);
                    if (listener != null) listener.onOutput(new String(chunk, 0, read, StandardCharsets.UTF_8), stderr, System.currentTimeMillis() - startedAt);
                }
            } catch (Throwable ignored) {
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) { }
            }
        }

        String text() { return new String(buffer.toByteArray(), StandardCharsets.UTF_8); }

        void joinQuietly(long millis) { try { super.join(millis); } catch (InterruptedException e) { interrupt(); } }

        void close() { closeQuietly(descriptor); }
    }
}
