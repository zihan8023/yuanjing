package com.termux.app.iqcode.api.deepseek;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek 原生 ChatML prompt 构建，移植 ds-free-api
 * src/openai_adapter/request/prompt.rs + ds_core/src/chat/request.rs 的拆分逻辑：
 * - 连续同角色（非 tool）消息合并
 * - user 消息前缀 <｜end▁of▁sentence｜>
 * - 工具定义/格式规范/指令作为普通 System 内容注入一次
 * - 末尾 <｜Assistant｜> 锚点（末块已是 assistant 则不重复）
 * 纯字符串实现，不依赖 org.json。arguments 由调用方传已序列化的 JSON 文本。
 */
public final class DeepSeekChatPrompt {
    public static final String TAG_START = "<｜";
    public static final String TAG_END = "｜>";
    public static final String TAG_EOS = "<｜end▁of▁sentence｜>";
    public static final String TOOL_CALL_START = DeepSeekToolCallStream.TOOL_CALL_START;
    public static final String TOOL_CALL_END = DeepSeekToolCallStream.TOOL_CALL_END;

    /** 与上游一致：已知类型 default 上限 2,621,440 字符，未知类型回退 163,840。 */
    public static int inputCharacterLimitFor(String modelType) {
        return "default".equals(modelType) || "vision".equals(modelType) ? 2_621_440 : 163_840;
    }

    public static int oversizedThreshold(String modelType) {
        return inputCharacterLimitFor(modelType) * 75 / 100;
    }

    /** 单条消息：role=system/user/assistant/tool；toolCalls 元素为 {name, argumentsJson}。 */
    public static final class Msg {
        public final String role;
        public final String content;
        public final List<String[]> toolCalls;
        public final String name;

        public Msg(String role, String content, List<String[]> toolCalls, String name) {
            this.role = role;
            this.content = content;
            this.toolCalls = toolCalls;
            this.name = name;
        }

        public static Msg of(String role, String content) {
            return new Msg(role, content, null, null);
        }
    }

    /** 构建 ChatML prompt。三段注入文本可为 null。 */
    public static String build(List<Msg> messages, String defsText, String formatBlock, String instructionText) {
        List<Msg> merged = mergeMessages(messages);
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < merged.size()) {
            if ("tool".equals(merged.get(i).role)) {
                StringBuilder toolBlock = new StringBuilder();
                toolBlock.append("<｜tool▁outputs▁begin｜>");
                while (i < merged.size() && "tool".equals(merged.get(i).role)) {
                    String c = merged.get(i).content == null ? "" : merged.get(i).content;
                    toolBlock.append("<｜tool▁output▁begin｜>").append(c).append("<｜tool▁output▁end｜>");
                    i++;
                }
                toolBlock.append("<｜tool▁outputs▁end｜>");
                parts.add(toolBlock.toString());
            } else {
                parts.add(formatMessage(merged.get(i)));
                i++;
            }
        }

        List<String> sections = new ArrayList<>();
        if (defsText != null && !defsText.isEmpty()) sections.add(defsText);
        if (formatBlock != null && !formatBlock.isEmpty()) sections.add(formatBlock);
        if (instructionText != null && !instructionText.isEmpty()) sections.add(instructionText);
        if (!sections.isEmpty()) {
            String body = String.join("\n\n", sections);
            int sysIdx = -1;
            for (int p = 0; p < parts.size(); p++) {
                if (parts.get(p).startsWith("<｜System｜>")) { sysIdx = p; break; }
            }
            if (sysIdx >= 0) {
                String sys = parts.get(sysIdx);
                int insertAt = sys.lastIndexOf('\n');
                if (insertAt < 0) insertAt = sys.length();
                parts.set(sysIdx, sys.substring(0, insertAt) + "\n\n" + body + sys.substring(insertAt));
            } else {
                parts.add(0, "<｜System｜>" + body + "\n");
            }
        }

        if (parts.isEmpty() || !parts.get(parts.size() - 1).startsWith("<｜Assistant｜>")) {
            parts.add("<｜Assistant｜>\n");
        }
        return String.join("", parts);
    }

    public static List<Msg> mergeMessages(List<Msg> messages) {
        List<Msg> merged = new ArrayList<>();
        for (Msg msg : messages) {
            if (!merged.isEmpty()) {
                Msg last = merged.get(merged.size() - 1);
                if (last.role.equals(msg.role) && !"tool".equals(msg.role)) {
                    String newContent = msg.content == null ? "" : msg.content;
                    String lastContent = last.content == null ? "" : last.content;
                    String joined = lastContent.isEmpty() ? newContent
                        : newContent.isEmpty() ? lastContent : lastContent + "\n" + newContent;
                    List<String[]> calls = new ArrayList<>();
                    if (last.toolCalls != null) calls.addAll(last.toolCalls);
                    if (msg.toolCalls != null) calls.addAll(msg.toolCalls);
                    String name = msg.name != null ? msg.name : last.name;
                    merged.set(merged.size() - 1, new Msg(last.role, joined, calls.isEmpty() ? null : calls, name));
                    continue;
                }
            }
            merged.add(msg);
        }
        return merged;
    }

    private static String roleTag(String role) {
        String r = role == null || role.isEmpty() ? role
            : Character.toUpperCase(role.charAt(0)) + role.substring(1);
        return "<｜" + r + "｜>";
    }

    private static String formatMessage(Msg msg) {
        String body;
        switch (msg.role) {
            case "assistant": body = formatAssistant(msg); break;
            case "tool": body = formatTool(msg); break;
            default:
                List<String> pp = new ArrayList<>();
                if (msg.name != null) pp.add("(name: " + msg.name + ")");
                if (msg.content != null) pp.add(msg.content);
                body = String.join("\n", pp);
        }
        String tag = "tool".equals(msg.role) ? "" : roleTag(msg.role);
        String prefix = "user".equals(msg.role) ? TAG_EOS : "";
        return prefix + tag + body;
    }

    private static String formatAssistant(Msg msg) {
        List<String> pp = new ArrayList<>();
        if (msg.content != null && !msg.content.isEmpty()) pp.add(msg.content);
        if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
            List<String> items = new ArrayList<>();
            for (String[] tc : msg.toolCalls) {
                items.add("{\"name\": " + quoteJson(tc[0]) + ", \"arguments\": " + normalizeArgsJson(tc[1]) + "}");
            }
            pp.add(TOOL_CALL_START + "\n[" + String.join(", ", items) + "]\n" + TOOL_CALL_END);
        }
        return String.join("\n", pp);
    }

    private static String formatTool(Msg msg) {
        String c = msg.content == null ? "" : msg.content;
        return "<｜tool▁outputs▁begin｜><｜tool▁output▁begin｜>" + c
            + "<｜tool▁output▁end｜><｜tool▁outputs▁end｜>";
    }

    /** arguments 文本 → 紧凑 JSON；无法解析则按 null 序列化（与上游 unwrap_or(Null) 一致）。 */
    static String normalizeArgsJson(String arguments) {
        Object parsed = DeepSeekToolCallStream.parseJson(arguments == null ? "" : arguments);
        return parsed == null ? "null" : DeepSeekToolCallStream.serialize(parsed);
    }

    static String quoteJson(String s) {
        return DeepSeekToolCallStream.serialize(s);
    }

    // ── 历史拆分（超限回退方案 A） ───────────────────────────────────

    public static final class SplitResult {
        public final String inlinePrompt;
        public final String historyContent;
        SplitResult(String inline, String history) { this.inlinePrompt = inline; this.historyContent = history; }
    }

    /** 优先最后一块 assistant 作为 inline；其余块包装为上传文件内容。 */
    public static SplitResult splitHistoryPrompt(String prompt) {
        List<String[]> blocks = parseNativeBlocks(prompt);
        int astIdx = -1;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if ("assistant".equals(blocks.get(i)[0])) { astIdx = i; break; }
        }
        if (astIdx >= 0) {
            StringBuilder inline = new StringBuilder();
            inline.append(roleTag(blocks.get(astIdx)[0])).append(blocks.get(astIdx)[1]).append('\n');
            StringBuilder history = new StringBuilder();
            history.append("[file content end]\n\n");
            for (int i = 0; i < astIdx; i++) {
                history.append(roleTag(blocks.get(i)[0])).append(blocks.get(i)[1]).append('\n');
            }
            history.append("[file name]: IGNORE\n[file content begin]\n");
            return new SplitResult(inline.toString(), history.toString());
        }
        return new SplitResult(prompt, "");
    }

    /** 按 <｜Role｜> 标签边界解析为 {role, content} 块。 */
    static List<String[]> parseNativeBlocks(String prompt) {
        List<String[]> blocks = new ArrayList<>();
        int pos = 0;
        while (true) {
            int startIdx = prompt.indexOf(TAG_START, pos);
            if (startIdx < 0) break;
            int roleStart = startIdx + TAG_START.length();
            int roleEnd = prompt.indexOf(TAG_END, roleStart);
            if (roleEnd < 0) break;
            String role = prompt.substring(roleStart, roleEnd).trim().toLowerCase();
            int contentStart = roleEnd + TAG_END.length();
            int nextTag = prompt.indexOf(TAG_START, contentStart);
            int contentEnd = nextTag < 0 ? prompt.length() : nextTag;
            String content = trailingNewlinesRemoved(prompt.substring(contentStart, contentEnd));
            blocks.add(new String[]{role, content});
            pos = contentEnd;
        }
        return blocks;
    }

    private static String trailingNewlinesRemoved(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') end--;
        return s.substring(0, end);
    }
}
