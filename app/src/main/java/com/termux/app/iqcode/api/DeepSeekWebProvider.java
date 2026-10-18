package com.termux.app.iqcode.api;

import com.termux.app.iqcode.api.deepseek.DeepSeekChatPrompt;
import com.termux.app.iqcode.api.deepseek.DeepSeekPatchParser;
import com.termux.app.iqcode.api.deepseek.DeepSeekPowSolver;
import com.termux.app.iqcode.api.deepseek.DeepSeekRestClient;
import com.termux.app.iqcode.api.deepseek.DeepSeekToolCallStream;
import com.termux.app.iqcode.model.AssistantTurn;
import com.termux.app.iqcode.model.SessionConfig;
import com.termux.app.iqcode.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeepSeek 免费网页端协议适配 —— ds-free-api 反代逻辑的 Java 内嵌移植。
 * 凭据存放在 profile 的 apiKey 字段，格式：邮箱|密码|设备ID(可选)。
 * 每次请求：登录态 token（缓存）→ 建 session →（超限则历史上传）→ PoW →
 * completion SSE → p/o/v patch 事件 → 工具调用标记解析 → AssistantTurn。
 */
public final class DeepSeekWebProvider implements ModelProvider {
    private static final String POW_TARGET_COMPLETION = "/api/v0/chat/completion";
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_SLEEP_MS = 500;

    /** token 缓存：creds → userToken；40003 时失效重登一次。 */
    private static final Map<String, String> TOKEN_CACHE = new ConcurrentHashMap<>();

    private final android.content.Context appContext;
    private final ConcurrentHashMap<Thread, InputStream> activeStreams = new ConcurrentHashMap<>();

    public DeepSeekWebProvider(android.content.Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ── 凭据 ────────────────────────────────────────────────────────

    private static final class Creds {
        final String email;
        final String password;
        final String deviceId;
        /** 网页登录抓到的 Bearer token；非空时跳过账号密码登录。 */
        final String token;
        final String key;

        Creds(String email, String password, String deviceId, String token) {
            this.email = email;
            this.password = password;
            this.deviceId = deviceId;
            this.token = token == null ? "" : token;
            this.key = email + "|" + password + "|" + deviceId;
        }
    }

    private static Creds parseCreds(SessionConfig config) throws StreamFailure {
        String raw = config.apiKey == null ? "" : config.apiKey.trim();
        String[] parts = raw.split("\\|");
        if (parts.length >= 3 && "token".equals(parts[0].trim())) {
            if (parts[1].trim().isEmpty()) {
                throw new StreamFailure("invalid_api_key", "token 凭据为空，请重新网页登录");
            }
            return new Creds("", "", parts.length > 2 ? parts[2].trim() : "", parts[1].trim());
        }
        if (parts.length < 2 || parts[0].trim().isEmpty() || parts[1].isEmpty()) {
            throw new StreamFailure("invalid_api_key",
                "DeepSeek 免费网页版凭据格式应为：邮箱|密码|设备ID(可选)，或点「网页登录」自动获取");
        }
        return new Creds(parts[0].trim(), parts[1], parts.length > 2 ? parts[2].trim() : "", null);
    }

    // ── 主流程 ──────────────────────────────────────────────────────

    @Override
    public AssistantTurn createMessage(SessionConfig config, String systemPrompt,
                                       JSONArray messages, JSONArray tools, StreamListener listener) throws Exception {
        Creds creds = parseCreds(config);
        DeepSeekRestClient client = new DeepSeekRestClient(creds.deviceId);
        boolean webToken = !creds.token.isEmpty();
        String token;
        boolean tokenFromCache = false;
        if (webToken) {
            token = creds.token;
        } else {
            token = TOKEN_CACHE.get(creds.key);
            tokenFromCache = token != null;
            if (token == null) {
                token = client.login(creds.email, creds.password, "");
                TOKEN_CACHE.put(creds.key, token);
            }
        }

        String modelType = modelTypeFor(config.model);
        List<DeepSeekChatPrompt.Msg> msgs = toPromptMessages(systemPrompt, messages);
        String[] toolTexts = buildToolTexts(tools);
        String prompt = DeepSeekChatPrompt.build(msgs, toolTexts[0], toolTexts[1], toolTexts[2]);

        AssistantTurn turn = null;
        int reloginBudget = webToken ? 0 : (tokenFromCache ? 1 : 0);
        Exception last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                turn = runOnce(client, token, modelType, prompt, listener);
                break;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                DeepSeekRestClient.ApiError api = findApiError(e);
                if (api != null && api.code == 40003 && reloginBudget > 0) {
                    reloginBudget--;
                    TOKEN_CACHE.put(creds.key, client.login(creds.email, creds.password, ""));
                    continue;
                }
                if (api != null && api.code == 40003 && webToken) {
                    throw new StreamFailure("invalid_api_key",
                        "网页登录态已失效（40003），请到 API 管理里重新「网页登录」", e);
                }
                last = e;
                if (attempt + 1 < MAX_ATTEMPTS) Thread.sleep(RETRY_SLEEP_MS);
            }
        }
        if (turn == null) throw asStreamFailure(last);
        return turn;
    }

    /** 单次完整请求：session → (历史上传) → PoW → SSE → 事件消费 → 清理 session。 */
    private AssistantTurn runOnce(DeepSeekRestClient client, String token,
                                  String modelType, String prompt, StreamListener listener) throws Exception {
        String sessionId = client.createSession(token);
        boolean finished = false;
        boolean streamReady = false;
        long stopMessageId = 0;
        JSONArray refFileIds = new JSONArray();
        String inlinePrompt = prompt;
        try {
            int threshold = DeepSeekChatPrompt.oversizedThreshold(modelType);
            if (prompt.codePointCount(0, prompt.length()) > threshold) {
                DeepSeekChatPrompt.SplitResult split = DeepSeekChatPrompt.splitHistoryPrompt(prompt);
                try {
                    String fileId = client.uploadAndPoll(appContext, token, "EMPTY.txt", "text/plain",
                        split.historyContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    refFileIds.put(fileId);
                    inlinePrompt = split.inlinePrompt;
                } catch (Exception e) {
                    // 上传失败退回完整 prompt 内联直发（与上游一致）
                    inlinePrompt = prompt;
                }
            }

            String powHeader = DeepSeekPowSolver.solveHeader(appContext, client, token, POW_TARGET_COMPLETION);
            InputStream stream = client.completion(token, powHeader, sessionId, modelType,
                inlinePrompt, refFileIds, true, false);
            activeStreams.put(Thread.currentThread(), stream);
            streamReady = true;

            Object[] result = consumeStream(client, stream, listener);
            stopMessageId = (Long) result[0];
            finished = (Boolean) result[1];
            return (AssistantTurn) result[2];
        } finally {
            activeStreams.remove(Thread.currentThread());
            if (streamReady && !finished) {
                try { client.stopStream(token, sessionId, stopMessageId); } catch (Exception ignored) { }
            }
            client.deleteSession(token, sessionId);
        }
    }

    /** 读取 SSE 并喂状态机；返回 {stopMessageId, finished, turn}。 */
    private Object[] consumeStream(DeepSeekRestClient client, InputStream raw,
                                   StreamListener listener) throws Exception {
        BufferedReader reader = DeepSeekRestClient.streamReader(raw);
        DeepSeekPatchParser parser = new DeepSeekPatchParser();
        DeepSeekToolCallStream toolStream = new DeepSeekToolCallStream();

        StringBuilder thinking = new StringBuilder();
        StringBuilder visible = new StringBuilder();
        List<DeepSeekToolCallStream.ToolCallData> calls = new ArrayList<>();
        long usage = -1;
        boolean done = false;

        // 前两帧：ready + 可能的 hint
        String readyBlock = nextFrame(reader);
        if (readyBlock == null) throw new StreamFailure("stream_read_error", "DeepSeek 空流");
        long stopMessageId = DeepSeekPatchParser.parseReadyMessageIds(readyBlock)[1];
        String second = nextFrame(reader);
        if (second != null) {
            DeepSeekRestClient.ApiError hint = DeepSeekPatchParser.checkHint(second);
            if (hint != null) throw wrapApiError(hint);
            applyFrame(parser, toolStream, second, thinking, visible, calls, listener);
        }

        while (!done) {
            String frame = nextFrame(reader);
            if (frame == null) {
                // EOF：状态机强制收尾（上游 FINISHED 后偶发直接断流）
                if (!parser.isDone()) {
                    List<DeepSeekPatchParser.Event> tail = new ArrayList<>();
                    parser.finalizeEvents(new ArrayList<>(), tail);
                    for (DeepSeekPatchParser.Event evt : tail) {
                        if (evt.kind == DeepSeekPatchParser.Kind.DONE) {
                            done = true;
                            if (evt.usage >= 0) usage = evt.usage;
                        }
                    }
                }
                break;
            }
            try {
                List<DeepSeekPatchParser.Event> events = parser.applyFrame(frame);
                List<DeepSeekPatchParser.Event> outEvents = new ArrayList<>();
                parser.finalizeEvents(events, outEvents);
                for (DeepSeekPatchParser.Event evt : outEvents) {
                    switch (evt.kind) {
                        case THINK_DELTA:
                            thinking.append(evt.text);
                            if (listener != null) listener.onThinkingDelta(evt.text);
                            break;
                        case CONTENT_DELTA:
                            for (DeepSeekToolCallStream.Out o : toolStream.feed(evt.text)) {
                                if (o.text != null) {
                                    visible.append(o.text);
                                    if (listener != null) listener.onTextDelta(o.text);
                                }
                                if (o.calls != null) calls.addAll(o.calls);
                            }
                            break;
                        case DONE:
                            done = true;
                            if (evt.usage >= 0) usage = evt.usage;
                            break;
                        default:
                            break;
                    }
                }
            } catch (DeepSeekRestClient.ApiError e) {
                throw wrapApiError(e);
            }
        }

        for (DeepSeekToolCallStream.Out o : toolStream.finish()) {
            if (o.text != null) {
                visible.append(o.text);
                if (listener != null) listener.onTextDelta(o.text);
            }
            if (o.calls != null) calls.addAll(o.calls);
        }

        AssistantTurn turn = new AssistantTurn();
        if (thinking.length() > 0) {
            turn.content.put(new JSONObject().put("type", "thinking").put("thinking", thinking.toString()));
        }
        if (visible.length() > 0) {
            turn.content.put(new JSONObject().put("type", "text").put("text", visible.toString()));
        }
        for (DeepSeekToolCallStream.ToolCallData call : calls) {
            JSONObject input;
            try {
                input = new JSONObject(call.arguments);
            } catch (Exception e) {
                input = new JSONObject();
            }
            turn.content.put(new JSONObject().put("type", "tool_use")
                .put("id", call.id).put("name", call.name).put("input", input));
            turn.toolCalls.add(new ToolCall(call.id, call.name, input));
        }
        turn.stopReason = turn.toolCalls.isEmpty() ? "end_turn" : "tool_use";
        turn.inputTokens = 0;
        turn.outputTokens = Math.max(0, usage);
        if (listener != null) listener.onUsage(0, Math.max(0, usage));
        return new Object[]{stopMessageId, done, turn};
    }

    private static void applyFrame(DeepSeekPatchParser parser, DeepSeekToolCallStream toolStream, String frame,
                                   StringBuilder thinking, StringBuilder visible,
                                   List<DeepSeekToolCallStream.ToolCallData> calls,
                                   StreamListener listener) throws Exception {
        try {
            List<DeepSeekPatchParser.Event> events = parser.applyFrame(frame);
            List<DeepSeekPatchParser.Event> outEvents = new ArrayList<>();
            parser.finalizeEvents(events, outEvents);
            for (DeepSeekPatchParser.Event evt : outEvents) {
                if (evt.kind == DeepSeekPatchParser.Kind.THINK_DELTA) {
                    thinking.append(evt.text);
                    if (listener != null) listener.onThinkingDelta(evt.text);
                } else if (evt.kind == DeepSeekPatchParser.Kind.CONTENT_DELTA) {
                    for (DeepSeekToolCallStream.Out o : toolStream.feed(evt.text)) {
                        if (o.text != null) {
                            visible.append(o.text);
                            if (listener != null) listener.onTextDelta(o.text);
                        }
                        if (o.calls != null) calls.addAll(o.calls);
                    }
                }
            }
        } catch (DeepSeekRestClient.ApiError e) {
            throw wrapApiErrorStatic(e);
        }
    }

    /** 读取一个 SSE 帧（以空行结尾）；EOF 返回 null（残帧丢弃，由调用方 finalize 兜底）。 */
    private static String nextFrame(BufferedReader reader) throws java.io.IOException {
        StringBuilder frame = new StringBuilder();
        String line;
        boolean any = false;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (any) return frame.toString();
                continue;
            }
            any = true;
            frame.append(line).append('\n');
        }
        return any ? frame.toString() : null;
    }

    // ── 消息转换 ────────────────────────────────────────────────────

    private static List<DeepSeekChatPrompt.Msg> toPromptMessages(String systemPrompt, JSONArray messages) {
        List<DeepSeekChatPrompt.Msg> out = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            out.add(new DeepSeekChatPrompt.Msg("system", systemPrompt, null, null));
        }
        if (messages == null) return out;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg == null) continue;
            String role = msg.optString("role", "user");
            switch (role) {
                case "assistant": {
                    StringBuilder body = new StringBuilder();
                    List<String[]> toolCalls = new ArrayList<>();
                    JSONArray content = msg.optJSONArray("content");
                    if (content != null) {
                        for (int b = 0; b < content.length(); b++) {
                            JSONObject block = content.optJSONObject(b);
                            if (block == null) continue;
                            String type = block.optString("type", "");
                            if ("text".equals(type)) {
                                String t = block.optString("text", "");
                                if (!t.isEmpty()) {
                                    if (body.length() > 0) body.append('\n');
                                    body.append(t);
                                }
                            } else if ("tool_use".equals(type)) {
                                JSONObject input = block.optJSONObject("input");
                                toolCalls.add(new String[]{block.optString("name", ""),
                                    input == null ? "{}" : input.toString()});
                            }
                            // thinking 块不回放（与上游 OpenAI 适配一致）
                        }
                    } else {
                        String t = msg.optString("content", "");
                        if (!t.isEmpty()) body.append(t);
                    }
                    out.add(new DeepSeekChatPrompt.Msg("assistant", body.toString(),
                        toolCalls.isEmpty() ? null : toolCalls, null));
                    break;
                }
                case "tool": {
                    out.add(new DeepSeekChatPrompt.Msg("tool", msg.optString("content", ""), null, null));
                    break;
                }
                default: {
                    String name = msg.optString("name", "");
                    out.add(new DeepSeekChatPrompt.Msg("user", extractUserText(msg), null,
                        name.isEmpty() ? null : name));
                }
            }
        }
        return out;
    }

    private static String extractUserText(JSONObject msg) {
        JSONArray content = msg.optJSONArray("content");
        if (content == null) return msg.optString("content", "");
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) continue;
            String type = block.optString("type", "text");
            if ("image_url".equals(type) || "image".equals(type)) {
                parts.add("[图片]");
            } else {
                String t = block.optString("text", "");
                if (!t.isEmpty()) parts.add(t);
            }
        }
        return String.join("\n", parts);
    }

    // ── 工具注入文本（tools.rs 移植） ───────────────────────────────

    /** 返回 {defsText, formatBlock, instructionText}，无工具时全 null。 */
    static String[] buildToolTexts(JSONArray tools) {
        if (tools == null || tools.length() == 0) return new String[]{null, null, null};
        List<String> names = new ArrayList<>();
        List<String> defs = new ArrayList<>();
        defs.add("你可以使用以下工具：");
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null) continue;
            JSONObject fn = tool.optJSONObject("function");
            if (fn == null) continue;
            String name = fn.optString("name", "");
            if (name.trim().isEmpty()) continue;
            names.add(name);
            Object params = fn.opt("parameters");
            String paramsText = params == null ? "{}" : params.toString();
            String desc = fn.optString("description", "").trim();
            String descBlock = desc.isEmpty() ? "  无描述" : "~~~markdown\n  " + desc + "\n~~~\n";
            defs.add("- **" + name + "** (function):\n  - 调用方法: `"
                + DeepSeekToolCallStream.TOOL_CALL_START + "[{\"name\": \"" + name
                + "\", \"arguments\": " + paramsText + "}]" + DeepSeekToolCallStream.TOOL_CALL_END
                + "`\n  - 简要说明:\n" + descBlock);
        }
        if (names.isEmpty()) return new String[]{null, null, null};

        List<String> lines = new ArrayList<>();
        lines.add("**工具调用格式 — 请严格遵守：**");
        lines.add("");
        lines.add("将 JSON 数组包裹在工具调用标记中：");
        lines.add("");
        lines.add(DeepSeekToolCallStream.TOOL_CALL_START + "[{\"name\": \"工具名\", \"arguments\": {参数JSON}}]"
            + DeepSeekToolCallStream.TOOL_CALL_END);
        lines.add("");
        lines.add("**规则：**");
        lines.add("");
        lines.add("**核心：决定调用工具时，你的响应中只允许出现工具调用文本本身，禁止任何解释、前缀、总结、问候语等额外内容。**");
        lines.add("");
        lines.add("1. JSON 数组必须以 `" + DeepSeekToolCallStream.TOOL_CALL_START + "` 开头、以 `"
            + DeepSeekToolCallStream.TOOL_CALL_END + "` 结尾，将数组**完整包裹**在标记内。");
        lines.add("2. 所有工具调用必须放在**一个** JSON 数组中，多个调用用逗号分隔。");
        lines.add("3. 输出 `" + DeepSeekToolCallStream.TOOL_CALL_END + "` 后**立即停止**，不得添加后续文本、XML 标签或说明文字。");
        lines.add("4. 不要将工具调用包裹在 markdown 代码块中。");
        lines.add("5. 字符串参数值必须用**双引号**包裹（JSON 标准）。");
        lines.add("6. 决定调用工具时，输出的**第一个非空白字符**必须是 `" + DeepSeekToolCallStream.TOOL_CALL_START + "`。");
        lines.add("7. 整个响应中**只能出现一个 `" + DeepSeekToolCallStream.TOOL_CALL_START + "` 块**，不要重复输出多个块。");
        lines.add("8. **重复：** 如果你已经输出了一个工具调用块，绝对不要再输出第二个。");
        lines.add("9. **重复：** 禁止在工具调用标记之前输出任何文字，包括但不限于解释、确认、总结、问候语。");
        lines.add("10. 不要把回复和工具调用置于思考内容中。");
        lines.add("11. **重复：** 思考内容（<think> 标签内）仅用于内部推理过程，不要将最终回复或工具调用放在 <think> 标签中。");
        lines.add("");
        String a = names.get(0);
        lines.add("**正确示例：**");
        lines.add("");
        lines.add("**示例A** — 调用一个工具：");
        lines.add(DeepSeekToolCallStream.TOOL_CALL_START + "[{\"name\": \"" + a + "\", \"arguments\": "
            + exampleArgs(a) + "}]" + DeepSeekToolCallStream.TOOL_CALL_END);
        lines.add("");
        if (names.size() >= 2) {
            lines.add("**示例B** — 同时调用多个工具（一个数组包含全部调用）：");
            lines.add("");
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                items.add("{\"name\": \"" + names.get(i) + "\", \"arguments\": " + exampleArgs(names.get(i)) + "}");
            }
            lines.add(DeepSeekToolCallStream.TOOL_CALL_START + "[" + String.join(", ", items) + "]"
                + DeepSeekToolCallStream.TOOL_CALL_END);
            lines.add("");
        }
        if (names.size() >= 3) {
            lines.add("**示例C** — 同时调用三个工具（所有调用在一个数组中）：");
            lines.add("");
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                items.add("{\"name\": \"" + names.get(i) + "\", \"arguments\": " + exampleArgs(names.get(i)) + "}");
            }
            lines.add(DeepSeekToolCallStream.TOOL_CALL_START + "[" + String.join(", ", items) + "]"
                + DeepSeekToolCallStream.TOOL_CALL_END);
            lines.add("");
        }
        lines.add("**示例D** — 参数值为嵌套对象/数组（仍然是标准 JSON）：");
        lines.add("");
        lines.add(DeepSeekToolCallStream.TOOL_CALL_START + "[{\"name\": \"" + a + "\", \"arguments\": "
            + exampleNestedArgs(a) + "}]" + DeepSeekToolCallStream.TOOL_CALL_END);
        lines.add("");
        return new String[]{String.join("\n", defs), String.join("\n", lines), null};
    }

    private static String exampleArgs(String name) {
        String args;
        switch (name) {
            case "Read": case "read_file": args = "\"file_path\": \"/path/to/file\""; break;
            case "Bash": case "execute_command": case "exec_command": args = "\"command\": \"ls -la\""; break;
            case "Write": case "write_to_file": args = "\"file_path\": \"/path/to/file\", \"content\": \"hello\""; break;
            case "Edit": args = "\"file_path\": \"/path/to/file\", \"old_string\": \"foo\", \"new_string\": \"bar\""; break;
            case "Glob": args = "\"pattern\": \"**/*.rs\", \"path\": \".\""; break;
            case "search_files": args = "\"query\": \"TODO\", \"path\": \".\""; break;
            case "get_weather": args = "\"city\": \"Beijing\""; break;
            case "get_time": args = "\"timezone\": \"Asia/Shanghai\""; break;
            case "list_files": args = "\"path\": \".\""; break;
            default: args = "\"key\": \"value\"";
        }
        return "{" + args + "}";
    }

    private static String exampleNestedArgs(String name) {
        if ("Edit".equals(name)) {
            return "{\"file_path\": \"/path/to/file\", \"edits\": [{\"old_string\": \"foo\", \"new_string\": \"bar\"}, {\"old_string\": \"x\", \"new_string\": \"y\"}]}";
        }
        return "{\"config\": {\"enabled\": true, \"items\": [\"a\", \"b\"]}}";
    }

    // ── 杂项 ────────────────────────────────────────────────────────

    private static String modelTypeFor(String model) {
        String m = model == null ? "" : model.toLowerCase(Locale.US);
        if (m.contains("expert")) return "expert";
        if (m.contains("vision")) return "vision";
        return "default";
    }

    private static StreamFailure wrapApiError(DeepSeekRestClient.ApiError e) {
        return wrapApiErrorStatic(e);
    }

    private static StreamFailure wrapApiErrorStatic(DeepSeekRestClient.ApiError e) {
        if (e.code == 429 || e.bizMsg.contains("rate_limit")) {
            return new StreamFailure("temporarily_unavailable", e.getMessage(), e);
        }
        return new StreamFailure("api_error", e.getMessage(), e);
    }

    private static StreamFailure asStreamFailure(Exception e) {
        if (e instanceof StreamFailure) return (StreamFailure) e;
        return new StreamFailure("api_error", e == null ? "DeepSeek 请求失败" : String.valueOf(e.getMessage()), e);
    }

    private static DeepSeekRestClient.ApiError findApiError(Throwable t) {
        while (t != null) {
            if (t instanceof DeepSeekRestClient.ApiError) return (DeepSeekRestClient.ApiError) t;
            t = t.getCause();
        }
        return null;
    }

    @Override
    public void cancelRequest(Thread worker) {
        InputStream stream = activeStreams.get(worker);
        if (stream != null) {
            try { stream.close(); } catch (Exception ignored) { }
        }
        worker.interrupt();
    }
}
