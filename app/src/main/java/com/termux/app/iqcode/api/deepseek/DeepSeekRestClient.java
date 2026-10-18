package com.termux.app.iqcode.api.deepseek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * DeepSeek 免费网页端的原始 REST 客户端 —— ds-free-api ds_core::client 的 Java 直译。
 * 每个方法对应一个 chat.deepseek.com 端点；无重试、无会话状态。SSE 流以原始字节返回，由上层解析。
 */
public final class DeepSeekRestClient {
    static final String API_BASE = "https://chat.deepseek.com/api/v0";
    static final String DEFAULT_WASM_URL = "https://fe-static.deepseek.com/chat/static/sha3_wasm_bg.7b9ca65ddd.wasm";
    static final String POW_TARGET_COMPLETION = "/api/v0/chat/completion";
    static final String POW_TARGET_UPLOAD = "/api/v0/file/upload_file";
    private static final String UA = "DeepSeek/2.5.0 Android/35";

    private final String deviceId;

    public DeepSeekRestClient(String userDeviceId) {
        this.deviceId = userDeviceId == null || userDeviceId.trim().isEmpty()
            ? deriveDeviceUuid(API_BASE.getBytes(StandardCharsets.UTF_8)) : userDeviceId.trim();
    }

    String deviceId() { return deviceId; }

    public static final class ApiError extends IOException {
        public final int code;       // envelope code 或 HTTP 状态
        public final String bizMsg;
        public ApiError(int code, String bizMsg, String message) { super(message); this.code = code; this.bizMsg = bizMsg == null ? "" : bizMsg; }
    }

    /** org.json 的 put 声明受检异常，这里统一吞掉（键值为常量，不会失败）。 */
    private static JSONObject jput(JSONObject o, String key, Object val) {
        try {
            o.put(key, val);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return o;
    }

    /** 登录并返回 user token；调用方负责缓存。biz_code 2=密码错误 5=禁言 10=封禁 11=设备风控。 */
    public String login(String email, String password, String areaCode) throws IOException {
        JSONObject payload = jput(jput(jput(jput(jput(new JSONObject(),
            "email", email == null || email.isEmpty() ? JSONObject.NULL : email),
            "mobile", JSONObject.NULL),
            "password", password),
            "area_code", areaCode == null || areaCode.isEmpty() ? JSONObject.NULL : areaCode),
            "device_id", deviceId);
        jput(payload, "os", "android");
        JSONObject user = postJson("/users/login", payload, null, true).optJSONObject("user");
        if (user == null) throw new ApiError(-1, "", "登录响应缺少 user 字段");
        JSONObject chatStatus = user.optJSONObject("chat");
        if (chatStatus != null && chatStatus.optInt("is_muted", 0) != 0) {
            throw new ApiError(5, "USER_IS_MUTED", "账号被禁言（mute_until=" + chatStatus.opt("mute_until") + "）");
        }
        String token = user.optString("token", "");
        if (token.isEmpty()) throw new ApiError(-1, "", "登录响应缺少 token");
        // 真实客户端登录后立即 check_device；rotate 非 null 时按指令轮换令牌，失败不阻断
        try {
            JSONObject check = postJson("/users/auth_token/check_device",
                jput(jput(new JSONObject(), "device_id", deviceId), "device_model", ""), token, false);
            String rotated = extractRotateToken(check.opt("rotate"));
            if (!rotated.isEmpty()) return rotated;
        } catch (IOException ignored) { }
        return token;
    }

    public String createSession(String token) throws IOException {
        JSONObject data = postJson("/chat_session/create", new JSONObject(), token, false);
        JSONObject session = data.optJSONObject("chat_session");
        if (session == null) throw new ApiError(-1, "", "create 响应缺少 chat_session");
        return session.optString("id", "");
    }

    public void deleteSession(String token, String sessionId) {
        try {
            postJson("/chat_session/delete", jput(new JSONObject(), "chat_session_id", sessionId), token, false);
        } catch (IOException ignored) { }
    }

    public void stopStream(String token, String sessionId, long messageId) {
        try {
            postJson("/chat/stop_stream",
                jput(jput(new JSONObject(), "chat_session_id", sessionId), "message_id", messageId), token, false);
        } catch (IOException ignored) { }
    }

    public JSONObject createPowChallenge(String token, String targetPath) throws IOException {
        JSONObject data = postJson("/chat/create_pow_challenge",
            jput(new JSONObject(), "target_path", targetPath), token, false);
        JSONObject challenge = data.optJSONObject("challenge");
        if (challenge == null) throw new ApiError(-1, "", "PoW 响应缺少 challenge");
        return challenge;
    }

    public InputStream completion(String token, String powHeader, String sessionId, String modelType,
                                  String prompt, JSONArray refFileIds, boolean thinking, boolean search) throws IOException {
        JSONObject body = jput(jput(jput(jput(jput(jput(jput(jput(new JSONObject(),
            "parent_message_id", JSONObject.NULL),
            "chat_session_id", sessionId),
            "model_type", modelType),
            "prompt", prompt),
            "ref_file_ids", refFileIds),
            "thinking_enabled", thinking),
            "search_enabled", search),
            "preempt", false);
        HttpURLConnection conn = open("POST", "/chat/completion", authHeaders(token));
        conn.setRequestProperty("X-Ds-Pow-Response", powHeader);
        return sendForStream(conn, body);
    }

    /** 上传文件并轮询处理状态，返回 file_id（ds_core upload_and_poll）。 */
    public String uploadAndPoll(android.content.Context context, String token, String filename,
                                String contentType, byte[] content) throws Exception {
        String powHeader = DeepSeekPowSolver.solveHeader(context, this, token, POW_TARGET_UPLOAD);
        String boundary = "----IQCode" + UUID.randomUUID();
        HttpURLConnection conn = open("POST", "/file/upload_file", authHeaders(token));
        conn.setRequestProperty("X-Ds-Pow-Response", powHeader);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename.replace("\"", "") + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        JSONObject data = readEnvelope(conn);
        String fileId = data.optString("id", "");
        if (fileId.isEmpty()) throw new ApiError(-1, "", "上传响应缺少文件 id");
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            JSONObject files = getJson("/file/fetch_files?file_ids=" + fileId, token);
            JSONArray list = files.optJSONArray("files");
            String status = list != null && list.length() > 0 ? list.optJSONObject(0).optString("status", "") : "";
            if ("SUCCESS".equals(status)) return fileId;
            if ("FAILED".equals(status)) throw new ApiError(-1, "", "文件上传失败: " + filename);
        }
        throw new ApiError(-1, "", "文件处理超时: " + filename);
    }

    // ── HTTP 基础 ─────────────────────────────────────────────────────

    private HttpURLConnection open(String method, String path, java.util.Map<String, String> headers) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(120_000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("X-Client-Version", "2.5.0");
        conn.setRequestProperty("X-Client-Platform", "android");
        conn.setRequestProperty("X-Client-Locale", "zh_CN");
        conn.setRequestProperty("X-Client-Bundle-Id", "com.deepseek.chat");
        conn.setRequestProperty("X-Device-Id", deviceId);
        conn.setRequestProperty("X-Device-Model", "");
        conn.setRequestProperty("X-Client-Timezone-Offset", "28800");
        if (headers != null) for (java.util.Map.Entry<String, String> h : headers.entrySet()) conn.setRequestProperty(h.getKey(), h.getValue());
        return conn;
    }

    private java.util.Map<String, String> authHeaders(String token) {
        java.util.Map<String, String> h = new java.util.HashMap<>();
        h.put("Authorization", "Bearer " + token);
        return h;
    }

    private JSONObject postJson(String path, JSONObject body, String token, boolean loginReferer) throws IOException {
        HttpURLConnection conn = open("POST", path, authHeaders(token == null ? "" : token));
        conn.setRequestProperty("Content-Type", "application/json");
        if (loginReferer) conn.setRequestProperty("Referer", "https://chat.deepseek.com/sign_in");
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readEnvelope(conn);
    }

    private JSONObject getJson(String path, String token) throws IOException {
        HttpURLConnection conn = open("GET", path, authHeaders(token));
        return readEnvelope(conn);
    }

    private InputStream sendForStream(HttpURLConnection conn, JSONObject body) throws IOException {
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        if (status == 202 && conn.getHeaderField("x-amzn-waf-action") != null) {
            throw new ApiError(202, "WAF", "DeepSeek WAF 拦截（多为美国 IP）；请更换网络环境后重试");
        }
        if (status < 200 || status >= 300) {
            throw new ApiError(status, "", "HTTP " + status + ": " + readAll(conn.getErrorStream()));
        }
        return conn.getInputStream();
    }

    /** envelope 解包：code!=0 或 biz_code!=0 都按 ApiError 抛出；返回 biz_data。 */
    private JSONObject readEnvelope(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        String body = readAll(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (status < 200 || status >= 300) throw new ApiError(status, "", "HTTP " + status + ": " + truncate(body));
        JSONObject root;
        try { root = new JSONObject(body); } catch (Exception e) { throw new ApiError(-1, "", "响应不是 JSON: " + truncate(body)); }
        int code = root.optInt("code", -1);
        if (code != 0) {
            throw new ApiError(code, root.optString("msg", ""), code == 40003
                ? "登录态失效（40003），需要重新登录" : "API 错误 code=" + code + ": " + root.optString("msg", ""));
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new ApiError(-1, "", "响应缺少 data");
        int bizCode = data.optInt("biz_code", 0);
        if (bizCode != 0) throw new ApiError(bizCode, data.optString("biz_msg", ""), describeBiz(bizCode, data.optString("biz_msg", "")));
        JSONObject biz = data.optJSONObject("biz_data");
        return biz == null ? new JSONObject() : biz;
    }

    static String describeBiz(int bizCode, String bizMsg) {
        switch (bizCode) {
            case 2: return "邮箱或密码错误（" + bizMsg + "）";
            case 5: return "账号被禁言（" + bizMsg + "），通常数周后解封";
            case 10: return "账号已被封禁（" + bizMsg + "），需要换号";
            case 11: return "设备风控拦截（" + bizMsg + "）：需要真实浏览器抓取的 device_id";
            default: return "业务错误 biz_code=" + bizCode + ": " + bizMsg;
        }
    }

    private static String extractRotateToken(Object rotate) {
        if (rotate instanceof String && !((String) rotate).isEmpty()) return (String) rotate;
        if (rotate instanceof JSONObject) {
            String token = ((JSONObject) rotate).optString("token", "");
            return token.isEmpty() ? "" : token;
        }
        return "";
    }

    /** 由固定种子确定性派生 RFC 4122 v4 形态的设备 UUID（与 ds_core derive_device_uuid 一致）。 */
    static String deriveDeviceUuid(byte[] seed) {
        long hi = fnv1a(seed, 0L);
        long lo = fnv1a(seed, 0x9e3779b97f4a7c15L);
        long[] b = {hi, lo};
        StringBuilder hex = new StringBuilder();
        for (long v : b) for (int i = 7; i >= 0; i--) hex.append(String.format("%02x", (v >> (i * 8)) & 0xffL));
        String h = hex.toString();
        return h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16) + "-" + h.substring(16, 20) + "-" + h.substring(20, 32);
    }

    private static long fnv1a(byte[] data, long offset) {
        long hash = 0xcbf29ce484222325L ^ offset;
        for (byte b : data) {
            hash ^= (b & 0xffL);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try (InputStream in = stream) {
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 400 ? s.substring(0, 400) : s;
    }

    public static BufferedReader streamReader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
