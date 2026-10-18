package com.termux.app.iqcode.local;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地小模型管理器：模型注册表 / 推荐选档 / 下载（断点续传+白名单校验）/
 * llama-server 子进程生命周期 / 本地推理（llama.cpp /completion SSE 流式）。
 *
 * 关键设计：
 * - llama-server 是常驻进程，TermuxShellExecutor（一次性命令）不适合，
 *   这里用 ProcessBuilder 直接 fork，并带上 Termux 环境变量；
 * - 端口动态探测（18080 起，占用则 +1）；
 * - 空闲回收：handle 记录 lastUsed，LocalModelProvider 每轮结束时调用 touch/stop；
 * - 下载源白名单：只允许 ModelScope / hf-mirror / HuggingFace 三个域名，防供应链替换。
 */
public final class LocalModelManager {

    // ==================== 模型注册表 ====================

    public static final class ModelDef {
        public final String id;
        public final String ggufFile;
        public final long sizeMb;
        public final long minRamMb;
        public final int contextTokens;
        public final boolean supportsTools;

        ModelDef(String id, String ggufFile, long sizeMb, long minRamMb, int contextTokens, boolean supportsTools) {
            this.id = id;
            this.ggufFile = ggufFile;
            this.sizeMb = sizeMb;
            this.minRamMb = minRamMb;
            this.contextTokens = contextTokens;
            this.supportsTools = supportsTools;
        }
    }

    /** Qwen2.5-Coder GGUF 档位（q4_K_M 量化，工具调用能力：>=1.5B 支持）。
     *  文件名以 ModelScope 官方仓库 Qwen/Qwen2.5-Coder-{N}B-Instruct-GGUF 实际文件名为准（全小写）。 */
    private static final ModelDef[] CATALOG = {
        new ModelDef("qwen2.5-coder-0.5b-q4",  "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",  470, 1800,  8192,  false),
        new ModelDef("qwen2.5-coder-1.5b-q4", "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",  1066, 3500,  8192,  true),
        new ModelDef("qwen2.5-coder-3b-q4",   "qwen2.5-coder-3b-instruct-q4_k_m.gguf",   2008, 6000,  16384, true),
        new ModelDef("qwen2.5-coder-7b-q4",   "qwen2.5-coder-7b-instruct-q4_k_m.gguf",   4467, 10000, 16384, true),
        new ModelDef("qwen2.5-coder-14b-q4", "qwen2.5-coder-14b-instruct-q4_k_m.gguf",   8572, 18000, 16384, true),
    };

    public static ModelDef def(String id) {
        for (ModelDef d : CATALOG) if (d.id.equals(id)) return d;
        return null;
    }

    /** 全量注册表（UI 面板枚举用）。 */
    public static ModelDef[] catalog() {
        return CATALOG.clone();
    }

    /**
     * 按设备画像推荐"能跑的最高档"：
     * 内存要求 = minRamMb，存储要求 = sizeMb*1.5；0.5B 是最后兜底。
     */
    public static ModelDef recommend(DeviceProfile dp) {
        if (dp == null) return CATALOG[0];
        ModelDef best = null;
        for (ModelDef d : CATALOG) {
            if (!dp.canLoadModel(d.minRamMb)) continue;
            if (!dp.canStoreModel(d.sizeMb)) continue;
            best = d;
        }
        if (best == null && dp.canLoadModel(CATALOG[0].minRamMb)) return CATALOG[0];
        return best;
    }

    // ==================== 路径与二进制 ====================

    public static File modelsDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".iq/models");
    }

    public static File binDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".iq/bin");
    }

    public static File llamaServerBinary() {
        return new File(binDir(), "llama-server");
    }

    /** 模型文件是否已下载完整（按注册表大小粗校验）。 */
    public static boolean isDownloaded(ModelDef def) {
        File f = new File(modelsDir(), def.ggufFile);
        if (!f.isFile()) return false;
        long expected = def.sizeMb * 1024L * 1024L;
        // 允许 5% 误差（GGUF 实际文件与估算值会有出入）
        return Math.abs(f.length() - expected) <= expected / 20L;
    }

    public static List<ModelDef> installed() {
        List<ModelDef> out = new ArrayList<>();
        for (ModelDef d : CATALOG) if (isDownloaded(d)) out.add(d);
        return out;
    }

    // ==================== 下载（断点续传 + 白名单） ====================

    public interface DownloadListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    /** 下载源白名单：只允许这三个域名，避免供应链替换。 */
    private static boolean allowedHost(String host) {
        return host.equals("modelscope.cn")
            || host.endsWith(".modelscope.cn")
            || host.equals("hf-mirror.com")
            || host.endsWith(".hf-mirror.com")
            || host.equals("huggingface.co")
            || host.endsWith(".huggingface.co");
    }

    /** mirror=false → ModelScope 主源；mirror=true → hf-mirror 备选镜像。 */
    public static String downloadUrl(ModelDef def, boolean mirror) {
        if (mirror) {
            return "https://hf-mirror.com/Qwen/Qwen2.5-Coder-" + sizeLabel(def)
                + "B-Instruct-GGUF/resolve/main/" + def.ggufFile;
        }
        return "https://modelscope.cn/models/Qwen/Qwen2.5-Coder-" + sizeLabel(def)
            + "B-Instruct-GGUF/resolve/master/" + def.ggufFile;
    }

    private static String sizeLabel(ModelDef def) {
        switch (def.id) {
            case "qwen2.5-coder-0.5b-q4": return "0.5";
            case "qwen2.5-coder-1.5b-q4": return "1.5";
            case "qwen2.5-coder-3b-q4": return "3";
            case "qwen2.5-coder-7b-q4": return "7";
            default: return "14";
        }
    }

    /**
     * 断点续传下载到 modelsDir()。仅 Wi-Fi 下允许（省流量）；下载后按注册表大小校验。
     */
    public static void download(Context context, ModelDef def, boolean mirror, DownloadListener listener) throws Exception {
        File dir = modelsDir();
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("无法创建模型目录: " + dir);
        }
        if (!isWifi(context)) {
            throw new IllegalStateException("模型较大，仅在 Wi-Fi 下允许下载（当前不是 Wi-Fi）");
        }
        URL url = new URL(downloadUrl(def, mirror));
        if (!allowedHost(url.getHost())) {
            throw new IllegalStateException("下载源不在白名单: " + url.getHost());
        }
        File target = new File(dir, def.ggufFile);
        File part = new File(dir, def.ggufFile + ".part");

        long existing = part.isFile() ? part.length() : 0;
        long expected = def.sizeMb * 1024L * 1024L;

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
        conn.setRequestProperty("User-Agent", "IQCodeAndroid-LocalModel/0.30");
        int code = conn.getResponseCode();
        if (code != 200 && code != 206) {
            throw new IOException("下载失败 HTTP " + code + ": " + readError(conn));
        }
        long total = code == 206 ? existing + conn.getContentLengthLong() : conn.getContentLengthLong();
        if (total <= 0) total = expected;

        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(part, existing > 0)) {
            byte[] buf = new byte[65536];
            long done = existing;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (listener != null) listener.onProgress(done, total);
            }
        }
        conn.disconnect();

        long actual = part.length();
        if (Math.abs(actual - expected) > expected / 20L) {
            throw new IOException("下载校验失败：期望约 " + expected + " 字节，实际 " + actual);
        }
        if (!part.renameTo(target)) {
            throw new IOException("无法完成模型文件落盘");
        }
    }

    // ==================== llama-server 二进制 ====================

    /**
     * 检查 llama-server 是否可用。不可用时返回原因；可用返回 null。
     * 安装路径二选一：
     *   1) 已有 $HOME/.iq/bin/llama-server（用户自备/内置）→ 直接可用；
     *   2) Termux 包管理器安装 llama.cpp（推荐，需用户确认执行 pkg install）。
     */
    public static String missingBinaryReason() {
        File bin = llamaServerBinary();
        if (bin.isFile() && bin.canExecute()) return null;
        // Termux llama.cpp 包提供的二进制也在 PREFIX/bin，检测一下
        File pkgBin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "llama-server");
        if (pkgBin.isFile() && pkgBin.canExecute()) return null;
        return "缺少 llama-server：请先在 Termux 执行 pkg install llama.cpp，"
            + "或把 Android arm64 的 llama-server 放到 " + bin.getParent();
    }

    /** 返回实际可用的 llama-server 二进制路径。 */
    public static String resolveBinary() {
        File bin = llamaServerBinary();
        if (bin.isFile() && bin.canExecute()) return bin.getAbsolutePath();
        File pkgBin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "llama-server");
        if (pkgBin.isFile() && pkgBin.canExecute()) return pkgBin.getAbsolutePath();
        return null;
    }

    // ==================== 服务进程生命周期 ====================

    /** 一个运行中的 llama-server 句柄（端口 + 进程 + 空闲计时）。 */
    public static final class ServerHandle {
        public final Process process;
        public final int port;
        public final ModelDef def;
        volatile long lastUsedMs;

        ServerHandle(Process process, int port, ModelDef def) {
            this.process = process;
            this.port = port;
            this.def = def;
            this.lastUsedMs = System.currentTimeMillis();
        }

        public boolean alive() {
            return process != null && process.isAlive();
        }

        void touch() { lastUsedMs = System.currentTimeMillis(); }
    }

    private static final int BASE_PORT = 18080;
    private static final long IDLE_RECLAIM_MS = 10 * 60 * 1000L; // 空闲 10 分钟回收
    private static ServerHandle active;

    public static synchronized ServerHandle acquire(Context context, ModelDef def) throws Exception {
        // 已有同模型进程 → 复用
        if (active != null && active.alive() && active.def.id.equals(def.id)) {
            active.touch();
            return active;
        }
        stop(active);
        active = start(context, def);
        return active;
    }

    /** 启动 llama-server（ProcessBuilder fork + Termux 环境变量 + 动态端口）。 */
    private static ServerHandle start(Context context, ModelDef def) throws Exception {
        String binary = resolveBinary();
        if (binary == null) {
            throw new IllegalStateException("缺少 llama-server 二进制，请先 pkg install llama.cpp");
        }
        File model = new File(modelsDir(), def.ggufFile);
        if (!model.isFile()) {
            throw new IllegalStateException("模型未下载: " + def.ggufFile);
        }
        int port = findFreePort(BASE_PORT, 20);
        int ctx = Math.max(2048, Math.min(def.contextTokens, 16384));

        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("-m"); cmd.add(model.getAbsolutePath());
        cmd.add("-c"); cmd.add(String.valueOf(ctx));
        cmd.add("--port"); cmd.add(String.valueOf(port));
        cmd.add("--no-webui");
        cmd.add("--log-disable");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); // 日志进 stdout，失败时可读
        // Termux 运行环境（对齐 TermuxShellExecutor 的环境约定）
        java.util.Map<String, String> env = pb.environment();
        env.put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
        env.put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        env.put("TMPDIR", TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/tmp");
        env.put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        env.put("LANG", "en_US.UTF-8");
        env.put("TERM", "xterm-256color");
        env.put("IQ_CODE_ANDROID", "1");
        pb.directory(new File(TermuxConstants.TERMUX_HOME_DIR_PATH));

        Process process = pb.start();

        // 轮询等待 HTTP 就绪（最多 30s）
        long deadline = System.currentTimeMillis() + 30_000L;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                String log = readAvailable(process);
                throw new IllegalStateException("llama-server 启动失败: " + log);
            }
            if (ping(port)) { ready = true; break; }
            Thread.sleep(300L);
        }
        if (!ready) {
            process.destroy();
            throw new IllegalStateException("llama-server 30 秒内未就绪（端口 " + port + "）");
        }
        return new ServerHandle(process, port, def);
    }

    public static synchronized void stop(ServerHandle handle) {
        if (handle == null) return;
        try { handle.process.destroy(); } catch (Throwable ignored) { }
        try { handle.process.destroyForcibly(); } catch (Throwable ignored) { }
        if (active == handle) active = null;
    }

    /** 空闲回收：LocalModelProvider 每轮结束时调用；超时自动停进程释放内存。 */
    public static synchronized void reclaimIdle() {
        if (active != null && active.alive()
            && System.currentTimeMillis() - active.lastUsedMs > IDLE_RECLAIM_MS) {
            stop(active);
        }
    }

    private static boolean ping(int port) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/health").openConnection();
            c.setConnectTimeout(1000);
            c.setReadTimeout(1000);
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 500;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int findFreePort(int base, int attempts) {
        for (int i = 0; i < attempts; i++) {
            int port = base + i;
            try (java.net.ServerSocket s = new java.net.ServerSocket(port)) {
                s.setReuseAddress(true);
                return port;
            } catch (IOException ignored) { }
        }
        throw new IllegalStateException("找不到可用端口");
    }

    private static String readAvailable(Process process) {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream in = process.getInputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && sb.length() < 4000) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) { }
        return sb.length() == 0 ? "(无日志)" : sb.toString().trim();
    }

    // ==================== 本地推理（llama.cpp /completion SSE） ====================

    /** 单次推理结果。 */
    public static final class CompletionResult {
        public String text = "";
        public String stopReason = "stop";
        public long inputTokens;
        public long outputTokens;
    }

    /**
     * 向已启动的 server 发 /completion 请求，SSE 流式返回。
     * prompt 需已是完整 chat template（含 <|im_start|>assistant 前缀），
     * 模型只续写 assistant 部分。
     */
    public static CompletionResult completion(ServerHandle handle, String prompt, int maxTokens,
                                              com.termux.app.iqcode.api.ModelProvider.StreamListener listener)
            throws Exception {
        if (handle == null || !handle.alive()) {
            throw new IllegalStateException("本地推理进程不可用");
        }
        handle.touch();

        JSONObject body = new JSONObject();
        body.put("prompt", prompt);
        body.put("stream", true);
        body.put("n_predict", Math.max(1, Math.min(maxTokens, 8192)));
        body.put("temperature", 0.2);
        body.put("top_p", 0.9);

        CompletionResult result = new CompletionResult();
        StringBuilder text = new StringBuilder();

        HttpURLConnection conn = (HttpURLConnection)
            new URL("http://127.0.0.1:" + handle.port + "/completion").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(300_000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "text/event-stream");

        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("本地推理 HTTP " + code + ": " + readError(conn));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean stop = false;
            while (!stop && (line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("本地推理被取消");
                }
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    if ("[DONE]".equals(payload)) break;
                    continue;
                }
                JSONObject event = new JSONObject(payload);
                String content = event.optString("content", "");
                if (!content.isEmpty()) {
                    text.append(content);
                    if (listener != null) listener.onTextDelta(content);
                }
                JSONObject timings = event.optJSONObject("timings");
                if (timings != null) {
                    int p = timings.optInt("prompt_n", -1);
                    int g = timings.optInt("predicted_n", -1);
                    if (p >= 0) result.inputTokens = p;
                    if (g >= 0) result.outputTokens = g;
                }
                if (event.optBoolean("stop", false)) stop = true;
            }
        }
        conn.disconnect();

        result.text = text.toString();
        result.stopReason = stopFromText(result.text);
        return result;
    }

    private static String stopFromText(String text) {
        String t = text == null ? "" : text;
        if (t.contains("<|im_end|>")) return "end_turn";
        if (t.contains("<tool_call>")) return "tool_use";
        return "stop";
    }

    private static String readError(HttpURLConnection conn) {
        try {
            InputStream in = conn.getErrorStream();
            if (in == null) return "";
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null && sb.length() < 2000) sb.append(line).append('\n');
            return sb.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Wi-Fi 检查（下载模型用）。 */
    public static boolean isWifi(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) return false;
            return info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== Qwen Chat Template ====================

    /** 把 system/user/assistant/tool_result 消息转成 Qwen2.5 chat template 文本。 */
    public static String buildPrompt(String systemPrompt, org.json.JSONArray messages,
                                     org.json.JSONArray tools, ModelDef def, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n");
        String sys = systemPrompt == null ? "" : systemPrompt;
        if (def.supportsTools && tools != null && tools.length() > 0) {
            sys += "\n\n你可以调用以下工具，需要调用工具时输出格式："
                + "<tool_call>{\"name\":\"工具名\",\"arguments\":{...}}</tool_call>\n"
                + "工具列表:\n" + tools.toString();
        }
        sb.append(sys).append("<|im_end|>\n");

        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                org.json.JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role", "user");
                Object content = m.opt("content");
                String text = content == null ? "" : content.toString();
                if (text.isEmpty()) continue;
                switch (role) {
                    case "assistant":
                        sb.append("<|im_start|>assistant\n").append(text).append("<|im_end|>\n");
                        break;
                    case "tool_result":
                    case "tool":
                        // 工具结果以 user 角色回填，保证消息结构合法
                        sb.append("<|im_start|>user\n[工具结果]\n").append(text).append("<|im_end|>\n");
                        break;
                    default: // user / system
                        sb.append("<|im_start|>user\n").append(text).append("<|im_end|>\n");
                        break;
                }
            }
        }
        // 预留最大输出长度，避免模型把保留位全吃掉
        int used = sb.length() + 2048;
        int headroom = Math.max(512, Math.min(2048, maxTokens / 4));
        if (used + headroom > 48000) {
            // 本地模型上下文有限：粗暴截断最老的消息（保 system + 最新 2 轮）
            sb.setLength(0);
            sb.append("<|im_start|>system\n").append(sys).append("<|im_end|>\n");
            if (messages != null && messages.length() > 0) {
                for (int i = Math.max(0, messages.length() - 4); i < messages.length(); i++) {
                    org.json.JSONObject m = messages.optJSONObject(i);
                    if (m == null) continue;
                    sb.append("<|im_start|>").append("user".equals(m.optString("role")) ? "user" : "assistant")
                      .append("\n").append(m.opt("content") == null ? "" : m.opt("content").toString())
                      .append("<|im_end|>\n");
                }
            }
        }
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }
}
