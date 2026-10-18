package com.termux.app.iqcode.api.deepseek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek SSE p/o/v patch 状态机的 Java 移植，对齐网页端 DeltaParser 与
 * ds-free-api ds_core/src/chat/response.rs：
 * - p / o 跨事件持久化，o 缺省 "SET"
 * - 初始快照（无 path 且 v 含 response）
 * - BATCH 递归分解，子项独立 path/op
 * - 关注路径：response/status、response/accumulated_token_usage、
 *   response/fragments/-1/content、response/fragments(APPEND)
 * - status FINISHED/INCOMPLETE → Done（仅 FINISHED 给 finish_reason=stop）
 */
public final class DeepSeekPatchParser {
    public static final String FRAG_THINK = "THINK";
    public static final String FRAG_RESPONSE = "RESPONSE";

    public enum Kind { THINK_START, THINK_DELTA, CONTENT_START, CONTENT_DELTA, DONE }

    public static final class Event {
        public final Kind kind;
        public final String text;
        public final String finishReason;
        public final long usage;

        private Event(Kind kind, String text, String finishReason, long usage) {
            this.kind = kind;
            this.text = text;
            this.finishReason = finishReason;
            this.usage = usage;
        }

        static Event thinkStart() { return new Event(Kind.THINK_START, null, null, -1); }
        static Event thinkDelta(String c) { return new Event(Kind.THINK_DELTA, c, null, -1); }
        static Event contentStart() { return new Event(Kind.CONTENT_START, null, null, -1); }
        static Event contentDelta(String c) { return new Event(Kind.CONTENT_DELTA, c, null, -1); }
        static Event done(String finishReason, long usage) { return new Event(Kind.DONE, null, finishReason, usage); }
    }

    enum Phase { INIT, THINKING, CONTENT, DONE }

    private static final class Fragment {
        final String ty;
        final StringBuilder content = new StringBuilder();
        Fragment(String ty) { this.ty = ty; }
    }

    private String currentPath;
    private String currentOp;
    private final List<Fragment> fragments = new ArrayList<>();
    private String status;
    private long accumulatedUsage = -1;
    private Phase phase = Phase.INIT;

    /** 消费一帧 SSE 文本（不含结尾空行），返回零个或多个事件。hint 帧抛 ApiError。 */
    public List<Event> applyFrame(String frame) throws DeepSeekRestClient.ApiError {
        List<Event> out = new ArrayList<>();
        if (frame == null || frame.isEmpty()) return out;

        String eventType = null;
        String data = null;
        for (String lineRaw : frame.split("\n", -1)) {
            String line = lineRaw.trim();
            if (line.startsWith("event:") && eventType == null) {
                eventType = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:") && data == null) {
                data = line.substring("data:".length()).trim();
            }
        }

        if ("hint".equals(eventType) && data != null) {
            throw hintToError(data);
        }

        if (data != null) {
            try {
                JSONObject val = new JSONObject(data);
                applyPatch(val, out);
            } catch (DeepSeekRestClient.ApiError e) {
                throw e;
            } catch (Exception ignored) {
                // 非 JSON data：与上游一致，静默跳过
            }
        }
        return out;
    }

    /** 后处理：插入阶段切换信号，status 结束时追加 Done（可只传状态机已知信息调用）。 */
    public void finalizeEvents(List<Event> events, List<Event> out) {
        for (Event evt : events) {
            if (evt.kind == Kind.THINK_DELTA && (phase == Phase.INIT || phase == Phase.CONTENT)) {
                phase = Phase.THINKING;
                out.add(Event.thinkStart());
            } else if (evt.kind == Kind.CONTENT_DELTA && (phase == Phase.INIT || phase == Phase.THINKING)) {
                phase = Phase.CONTENT;
                out.add(Event.contentStart());
            }
            out.add(evt);
        }
        if (status != null && ("FINISHED".equals(status) || "INCOMPLETE".equals(status)) && phase != Phase.DONE) {
            phase = Phase.DONE;
            String finish = "FINISHED".equals(status) ? "stop" : null;
            out.add(Event.done(finish, accumulatedUsage));
        }
    }

    public boolean isDone() { return phase == Phase.DONE; }

    private void applyPatch(JSONObject val, List<Event> out) throws DeepSeekRestClient.ApiError, org.json.JSONException {
        String p = val.optString("p", null);
        if (p != null) currentPath = p;
        String o = val.optString("o", null);
        if (o != null) currentOp = o;

        String op = currentOp == null ? "SET" : currentOp;
        String path = currentPath == null ? "" : currentPath;

        if (!val.has("v")) return;
        Object v = val.get("v");

        // 初始快照：无 path 且 v 含 response
        if (currentPath == null && v instanceof JSONObject && ((JSONObject) v).has("response")) {
            applyInitialSnapshot(((JSONObject) v).getJSONObject("response"), out);
            return;
        }

        if ("BATCH".equals(op) && v instanceof JSONArray) {
            applyBatch(path, (JSONArray) v, out);
            return;
        }

        applyPath(path, op, v, out);
    }

    private void applyBatch(String parentPath, JSONArray arr, List<Event> out) throws DeepSeekRestClient.ApiError, org.json.JSONException {
        String subPath = "";
        String subOp = "SET";
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String p = item.optString("p", null);
            if (p != null) subPath = p;
            String o = item.optString("o", null);
            if (o != null) subOp = o;
            if (!item.has("v")) continue;
            Object v = item.get("v");

            String fullPath;
            if (parentPath.isEmpty()) fullPath = subPath;
            else if (subPath.isEmpty()) fullPath = parentPath;
            else fullPath = parentPath + "/" + subPath;

            if ("BATCH".equals(subOp)) {
                applyBatch(fullPath, (JSONArray) v, out);
            } else {
                applyPath(fullPath, subOp, v, out);
            }
        }
    }

    private void applyInitialSnapshot(JSONObject response, List<Event> out) {
        String s = response.optString("status", null);
        if (s != null) status = s;
        if (response.has("accumulated_token_usage")) {
            accumulatedUsage = response.optLong("accumulated_token_usage", -1);
        }
        JSONArray arr = response.optJSONArray("fragments");
        if (arr != null) {
            fragments.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject frag = arr.optJSONObject(i);
                if (frag == null) continue;
                String ty = frag.optString("type", null);
                if (ty == null) continue;
                String content = frag.optString("content", "");
                fragments.add(new Fragment(ty));
                appendToLastFragment(ty, content, out);
            }
        }
    }

    private void applyPath(String path, String op, Object val, List<Event> out) {
        switch (path) {
            case "response/status":
            case "/response/status": {
                if (val instanceof String) status = (String) val;
                break;
            }
            case "response/accumulated_token_usage":
            case "accumulated_token_usage":
            case "/response/accumulated_token_usage":
            case "/accumulated_token_usage": {
                if (val instanceof Number) accumulatedUsage = ((Number) val).longValue();
                break;
            }
            case "response/fragments/-1/content":
            case "/response/fragments/-1/content": {
                if (val instanceof String && !fragments.isEmpty()) {
                    Fragment frag = fragments.get(fragments.size() - 1);
                    appendToLastFragment(frag.ty, (String) val, out);
                }
                break;
            }
            case "response/fragments":
            case "/response/fragments": {
                if ("APPEND".equals(op) && val instanceof JSONArray) {
                    JSONArray arr = (JSONArray) val;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.optJSONObject(i);
                        if (item == null) continue;
                        String ty = item.optString("type", null);
                        if (ty == null) continue;
                        String content = item.optString("content", "");
                        fragments.add(new Fragment(ty));
                        appendToLastFragment(ty, content, out);
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    private void appendToLastFragment(String ty, String content, List<Event> out) {
        if (content == null || content.isEmpty()) return;
        if (!fragments.isEmpty()) fragments.get(fragments.size() - 1).content.append(content);
        if (FRAG_THINK.equals(ty)) out.add(Event.thinkDelta(content));
        else if (FRAG_RESPONSE.equals(ty)) out.add(Event.contentDelta(content));
    }

    static DeepSeekRestClient.ApiError hintToError(String data) {
        String content = "(unknown)";
        try {
            JSONObject val = new JSONObject(data);
            content = val.optString("content", null);
            if (content == null) content = val.optString("finish_reason", "(unknown)");
        } catch (Exception ignored) { }
        if (content.contains("rate_limit")) {
            return new DeepSeekRestClient.ApiError(429, "", "请求过于频繁（rate_limit），请稍后重试");
        }
        if (content.contains("input_exceeds_limit")) {
            return new DeepSeekRestClient.ApiError(-1, "", "输入内容超长，请缩短后重试");
        }
        return new DeepSeekRestClient.ApiError(-1, "", "hint: " + content);
    }

    /** 从已收到的 SSE 文本里找 event:hint 错误；无错返回 null。 */
    public static DeepSeekRestClient.ApiError checkHint(String eventBlock) {
        boolean isHint = false;
        for (String lineRaw : eventBlock.split("\n", -1)) {
            String line = lineRaw.trim();
            if (line.startsWith("event:") && "hint".equals(line.substring("event:".length()).trim())) {
                isHint = true;
                break;
            }
        }
        if (!isHint) return null;
        if (eventBlock.contains("rate_limit")) {
            return new DeepSeekRestClient.ApiError(429, "", "请求过于频繁（rate_limit），请稍后重试");
        }
        if (eventBlock.contains("input_exceeds_limit")) {
            return new DeepSeekRestClient.ApiError(-1, "", "输入内容超长，请缩短后重试");
        }
        return null;
    }

    /** 从 ready 事件文本解析 (request_message_id, response_message_id)，缺省 (1, 2)。 */
    public static long[] parseReadyMessageIds(String text) {
        if (text != null) {
            for (String line : text.split("\n", -1)) {
                if (line.startsWith("data: ")) {
                    try {
                        JSONObject val = new JSONObject(line.substring("data: ".length()));
                        if (val.has("request_message_id") && val.has("response_message_id")) {
                            return new long[]{val.getLong("request_message_id"), val.getLong("response_message_id")};
                        }
                    } catch (Exception ignored) { }
                }
            }
        }
        return new long[]{1, 2};
    }
}
