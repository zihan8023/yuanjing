package com.termux.app.iqcode.api.deepseek;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 工具调用流式解析，移植 ds-free-api src/openai_adapter/response/tool_parser.rs：
 * - Detecting：滑窗缓冲（W=71 字符），检测 &lt;|tool▁calls▁begin|&gt;（含全角 ｜/▁ 模糊等价）
 * - CollectingXml：收集到结束标记；超 64K 回退纯文本
 * - Done：工具调用已产出，丢弃后续内容（防幻觉）
 * 纯字符串实现，不依赖 org.json（内部带最小 JSON 解析器，便于单测）。
 */
public final class DeepSeekToolCallStream {
    public static final String TOOL_CALL_START = "<|tool▁calls▁begin|>";
    public static final String TOOL_CALL_END = "<|tool▁calls▁end|>";
    private static final int W = 71;
    private static final int MAX_XML_BUF_LEN = 64 * 1024;

    /** 解析出的单条工具调用（arguments 为紧凑 JSON 文本）。 */
    public static final class ToolCallData {
        public final String id;
        public final String name;
        public final String arguments;
        ToolCallData(long seq, String name, String arguments) {
            this.id = String.format("call_%016x", seq);
            this.name = name;
            this.arguments = arguments;
        }
    }

    /** 一轮 feed 的产物：普通文本片段和/或工具调用集合。 */
    public static final class Out {
        public String text;
        public List<ToolCallData> calls;
    }

    private enum State { DETECTING, COLLECTING, COLLECTING_DSML, COLLECTING_INVOKE, DONE }
    private State state = State.DETECTING;
    private final StringBuilder buf = new StringBuilder();
    private String startTag;
    private java.util.regex.Pattern invokeEnd;
    private boolean invokeIsInvoke;
    private long seq = 1;

    public synchronized List<Out> feed(String content) {
        List<Out> outs = new ArrayList<>();
        if (content == null || content.isEmpty()) return outs;
        if (state == State.DONE) return outs;
        if (state == State.DETECTING) feedDetecting(content, outs);
        else if (state == State.COLLECTING) feedCollecting(content, outs);
        else if (state == State.COLLECTING_DSML) feedCollectingDsml(content, outs);
        else feedCollectingInvoke(content, outs);
        return outs;
    }

    /** 流结束：冲刷残余缓冲。 */
    public synchronized List<Out> finish() {
        List<Out> outs = new ArrayList<>();
        if (state == State.DETECTING) {
            if (buf.length() > 0) {
                outs.add(textOut(buf.toString()));
                buf.setLength(0);
            }
        } else if (state == State.COLLECTING) {
            List<ToolCallData> calls = parseToolCalls(buf.toString());
            if (calls != null && !calls.isEmpty()) {
                outs.add(callsOut(calls));
            } else {
                outs.add(textOut(buf.toString()));
            }
            buf.setLength(0);
        } else if (state == State.COLLECTING_DSML) {
            List<ToolCallData> calls = parseDsmlCalls(buf.toString());
            if (calls != null && !calls.isEmpty()) {
                outs.add(callsOut(calls));
            } else {
                outs.add(textOut(buf.toString()));
            }
            buf.setLength(0);
        } else if (state == State.COLLECTING_INVOKE) {
            List<ToolCallData> calls = invokeIsInvoke
                ? parseInvokeCalls(buf.toString()) : parseBareToolCalls(buf.toString());
            if (calls != null && !calls.isEmpty()) {
                outs.add(callsOut(calls));
            } else {
                outs.add(textOut(buf.toString()));
            }
            buf.setLength(0);
        }
        state = State.DONE;
        return outs;
    }

    public synchronized boolean isDone() { return state == State.DONE; }

    private void feedDetecting(String content, List<Out> outs) {
        buf.append(content);
        // DeepSeek 网页模型可能直接吐 DSML 工具标记，优先于标准标记检测
        java.util.regex.Matcher dsml = DSML_START.matcher(buf);
        if (dsml.find()) {
            int pos = dsml.start();
            String before = buf.substring(0, pos);
            if (!before.isEmpty()) {
                outs.add(textOut(before));
                buf.delete(0, pos);
            }
            state = State.COLLECTING_DSML;
            return;
        }
        // 网页模型可能直接泄漏 antml 风格标记：<invoke name="...">…</invoke> 或 <ToolName><parameter …>
        java.util.regex.Matcher inv = INVOKE_TAG_START.matcher(buf);
        if (inv.find() && !isInsideCodeFence(buf.toString(), inv.start())) {
            String before = buf.substring(0, inv.start());
            if (!before.isEmpty()) {
                outs.add(textOut(before));
                buf.delete(0, inv.start());
            }
            invokeEnd = INVOKE_END;
            invokeIsInvoke = true;
            state = State.COLLECTING_INVOKE;
            return;
        }
        java.util.regex.Matcher bare = BARE_TOOL_START.matcher(buf);
        if (bare.find() && !isInsideCodeFence(buf.toString(), bare.start())) {
            // 先取组名再改 buf：Matcher 惰性读取 CharSequence，delete 会让 group 漂移
            String name = bare.group(1).trim();
            String before = buf.substring(0, bare.start());
            if (!before.isEmpty()) {
                outs.add(textOut(before));
                buf.delete(0, bare.start());
            }
            invokeEnd = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote("</" + name + ">"),
                java.util.regex.Pattern.CASE_INSENSITIVE);
            invokeIsInvoke = false;
            state = State.COLLECTING_INVOKE;
            return;
        }
        long[] m = findStartTag(buf, 0);
        if (m != null) {
            int pos = (int) m[0];
            String tag = buf.substring((int) m[1], (int) m[2]);
            String before = buf.substring(0, pos);
            String rest = buf.substring(pos);
            long[] end = findEndTag(rest, tag.length(), tag);
            if (end != null) {
                int endPos = (int) end[0];
                String matchedEnd = rest.substring((int) end[1], (int) end[2]);
                if (looksLikeStartTag(matchedEnd) && rest.substring(tag.length(), endPos).trim().isEmpty()) {
                    if (!before.isEmpty()) outs.add(textOut(before));
                    buf.setLength(0);
                    buf.append(rest);
                    startTag = tag;
                    state = State.COLLECTING;
                    return;
                }
                int endAbs = endPos + (int) (end[2] - end[1]);
                String collected = rest.substring(0, endAbs);
                List<ToolCallData> calls = parseToolCalls(collected);
                if (calls != null && !calls.isEmpty()) {
                    if (!before.isEmpty()) outs.add(textOut(before));
                    outs.add(callsOut(calls));
                } else {
                    // 解析失败：退化为纯文本，避免整段丢失
                    outs.add(textOut(before + collected));
                }
                buf.setLength(0);
                state = State.DONE;
                return;
            }
            if (!before.isEmpty()) {
                outs.add(textOut(before));
                buf.setLength(0);
                buf.append(rest);
                startTag = tag;
                state = State.COLLECTING;
            } else {
                startTag = tag;
                state = State.COLLECTING;
            }
            return;
        }
        int safe = floorCharBoundary(buf, Math.max(0, buf.length() - W));
        if (safe > 0) {
            outs.add(textOut(buf.substring(0, safe)));
            buf.delete(0, safe);
        }
    }

    private static final java.util.regex.Pattern DSML_START = java.util.regex.Pattern.compile(
        "<[｜|]{1,2}\\s*DSML[｜|]{1,2}\\s*calls\\s*>", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DSML_END = java.util.regex.Pattern.compile(
        "</[｜|]{1,2}\\s*DSML[｜|]{1,2}\\s*calls\\s*>", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DSML_INVOKE = java.util.regex.Pattern.compile(
        "<[｜|]{1,2}\\s*DSML[｜|]{1,2}\\s*invoke\\s+name=\"([^\"]*)\"\\s*>([\\s\\S]*?)</[｜|]{1,2}\\s*DSML[｜|]{1,2}\\s*invoke\\s*>",
        java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DSML_PARAM = java.util.regex.Pattern.compile(
        "<[｜|]{1,2}\\s*DSML[｜|]{1,2}\\s*parameter\\s+name=\"([^\"]*)\"\\s*>([\\s\\S]*?)</[｜|]{1,2}\\s*DSML[｜|]{1,2}\\s*parameter\\s*>",
        java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern INVOKE_TAG_START = java.util.regex.Pattern.compile(
        "<invoke\\s+name\\s*=\\s*\"", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern INVOKE_END = java.util.regex.Pattern.compile(
        "</\\s*invoke\\s*>", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern BARE_TOOL_START = java.util.regex.Pattern.compile(
        "<([A-Za-z_][A-Za-z0-9_.-]*)>[ \\t\\r\\n]{0,64}(<parameter\\b)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern BARE_PARAM = java.util.regex.Pattern.compile(
        "<parameter\\b[^>]*\\bname=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</parameter\\s*>",
        java.util.regex.Pattern.CASE_INSENSITIVE);

    /** 网页模型泄漏的 DSML 工具标记块 → 工具调用。 */
    public static List<ToolCallData> parseDsmlCalls(String xml) {
        if (xml == null || xml.isEmpty()) return null;
        java.util.regex.Matcher inv = DSML_INVOKE.matcher(xml);
        List<ToolCallData> calls = new ArrayList<>();
        long seqCounter = 1;
        while (inv.find()) {
            String name = inv.group(1).trim();
            if (name.isEmpty()) continue;
            String body = inv.group(2);
            Map<String, Object> params = new LinkedHashMap<>();
            java.util.regex.Matcher pm = DSML_PARAM.matcher(body);
            while (pm.find()) {
                String key = pm.group(1).trim();
                String raw = pm.group(2);
                Object val = parseJson(raw.trim());
                params.put(key, val != null ? val : raw);
            }
            calls.add(new ToolCallData(seqCounter++, name, serialize(params)));
        }
        if (!calls.isEmpty()) return calls;
        // 泄漏变体：calls 包装直接跟 <invoke>/<parameter>（无 DSML invoke 名），混用普通 parameter 标签
        List<ToolCallData> inner = parseInvokeCalls(xml);
        if (inner != null && !inner.isEmpty()) return inner;
        Map<String, Object> params = new LinkedHashMap<>();
        collectDsmlParams(xml, params);
        if (params.isEmpty()) return null;
        // 无工具名可提取：参数是 command 时按 Bash 处理
        calls.add(new ToolCallData(1, "Bash", serialize(params)));
        return calls;
    }

    private static void collectDsmlParams(String xml, Map<String, Object> params) {
        java.util.regex.Matcher pm = DSML_PARAM.matcher(xml);
        while (pm.find()) {
            String key = pm.group(1).trim();
            String raw = pm.group(2);
            Object val = parseJson(raw.trim());
            params.put(key, val != null ? val : raw);
        }
        pm = BARE_PARAM.matcher(xml);
        while (pm.find()) {
            String key = pm.group(1).trim();
            if (params.containsKey(key)) continue;
            String raw = pm.group(2);
            Object val = parseJson(raw.trim());
            params.put(key, val != null ? val : raw);
        }
    }

    private void feedCollectingDsml(String content, List<Out> outs) {
        buf.append(content);
        if (buf.length() > MAX_XML_BUF_LEN) {
            outs.add(textOut(buf.toString()));
            buf.setLength(0);
            state = State.DETECTING;
            return;
        }
        java.util.regex.Matcher end = DSML_END.matcher(buf);
        if (!end.find()) return;
        int endAbs = end.end();
        String collected = buf.substring(0, endAbs);
        buf.delete(0, endAbs);
        // 结束标记后的剩余文本回到检测态继续处理
        String rest = buf.toString();
        buf.setLength(0);
        buf.append(rest);
        state = State.DETECTING;
        List<ToolCallData> calls = parseDsmlCalls(collected);
        if (calls != null && !calls.isEmpty()) {
            outs.add(callsOut(calls));
        } else {
            outs.add(textOut(collected));
        }
    }

    /** 网页模型泄漏的 antml 风格裸块 <ToolName><parameter name="k">v</parameter>…</ToolName> → 工具调用。 */
    public static List<ToolCallData> parseBareToolCalls(String xml) {
        if (xml == null || xml.isEmpty()) return null;
        java.util.regex.Matcher m = BARE_TOOL_START.matcher(xml);
        if (!m.find() || isInsideCodeFence(xml, m.start())) return null;
        String name = m.group(1).trim();
        if (name.isEmpty()) return null;
        int closePos = indexOfIgnoreCase(xml, "</" + name + ">", m.end());
        if (closePos < 0) return null;
        String body = xml.substring(m.start(2), closePos);
        Map<String, Object> params = new LinkedHashMap<>();
        java.util.regex.Matcher pm = BARE_PARAM.matcher(body);
        while (pm.find()) {
            String key = pm.group(1).trim();
            String raw = pm.group(2);
            Object val = parseJson(raw.trim());
            params.put(key, val != null ? val : raw);
        }
        if (params.isEmpty()) return null;
        List<ToolCallData> calls = new ArrayList<>();
        calls.add(new ToolCallData(1, name, serialize(params)));
        return calls;
    }

    private static int indexOfIgnoreCase(String s, String tag, int from) {
        return s.toLowerCase().indexOf(tag.toLowerCase(), from);
    }

    private void feedCollectingInvoke(String content, List<Out> outs) {
        buf.append(content);
        if (buf.length() > MAX_XML_BUF_LEN) {
            outs.add(textOut(buf.toString()));
            buf.setLength(0);
            state = State.DETECTING;
            return;
        }
        java.util.regex.Matcher end = invokeEnd.matcher(buf);
        if (!end.find()) return;
        int endAbs = end.end();
        String collected = buf.substring(0, endAbs);
        buf.delete(0, endAbs);
        // 结束标记后的剩余文本回到检测态继续处理
        String rest = buf.toString();
        buf.setLength(0);
        buf.append(rest);
        state = State.DETECTING;
        List<ToolCallData> calls = invokeIsInvoke
            ? parseInvokeCalls(collected) : parseBareToolCalls(collected);
        if (calls != null && !calls.isEmpty()) {
            outs.add(callsOut(calls));
        } else {
            outs.add(textOut(collected));
        }
    }

    private void feedCollecting(String content, List<Out> outs) {
        buf.append(content);
        if (buf.length() > MAX_XML_BUF_LEN) {
            outs.add(textOut(buf.toString()));
            buf.setLength(0);
            state = State.DETECTING;
            return;
        }
        int startEnd = buf.indexOf(">");
        startEnd = startEnd < 0 ? 0 : startEnd + 1;
        long[] end = findEndTag(buf.toString(), startEnd, startTag);
        if (end == null) return;
        int endPos = (int) end[0];
        String matchedEnd = buf.substring((int) end[1], (int) end[2]);
        if (looksLikeStartTag(matchedEnd) && buf.substring(startEnd, endPos).trim().isEmpty()) {
            return;
        }
        int endAbs = endPos + (int) (end[2] - end[1]);
        String collected = buf.substring(0, endAbs);
        List<ToolCallData> calls = parseToolCalls(collected);
        if (calls != null && !calls.isEmpty()) {
            outs.add(callsOut(calls));
        } else {
            outs.add(textOut(collected));
        }
        buf.setLength(0);
        state = State.DONE;
    }

    private static Out textOut(String text) {
        Out o = new Out();
        o.text = text;
        return o;
    }

    private Out callsOut(List<ToolCallData> calls) {
        Out o = new Out();
        o.calls = calls;
        return o;
    }

    // ── 标记匹配（｜↔| ▁↔_ 模糊等价） ─────────────────────────────────

    private static char normTagChar(char c) {
        if (c == '｜') return '|';
        if (c == '▁') return '_';
        return c;
    }

    private static boolean eqTagChar(char a, char b) {
        return a == b || normTagChar(a) == normTagChar(b);
    }

    /** 在 s 中模糊查找 partial；返回 {startChar, srcStart, srcEnd}（char 索引）。 */
    private static long[] fuzzyMatchTag(String s, String partial) {
        int n = partial.length();
        int h = s.length();
        if (n == 0 || h < n) return null;
        for (int start = 0; start <= h - n; start++) {
            boolean matched = true;
            for (int j = 0; j < n; j++) {
                if (!eqTagChar(partial.charAt(j), s.charAt(start + j))) {
                    matched = false;
                    break;
                }
            }
            if (matched) return new long[]{start, start, start + n};
        }
        return null;
    }

    /** match_start_tag：先精确 find，再模糊；返回 {pos, srcStart, srcEnd}。 */
    private static long[] matchStartTag(String s, String tag) {
        String partial = trimEndGt(tag);
        int pos = s.indexOf(partial);
        if (pos >= 0) return new long[]{pos, pos, pos + partial.length()};
        long[] f = fuzzyMatchTag(s, partial);
        return f;
    }

    private static String trimEndGt(String tag) {
        int end = tag.length();
        while (end > 0 && tag.charAt(end - 1) == '>') end--;
        return tag.substring(0, end);
    }

    private static long[] findStartTag(CharSequence s, int from) {
        String str = s.toString().substring(from);
        long[] m = matchStartTag(str, TOOL_CALL_START);
        if (m == null) return null;
        return new long[]{m[0] + from, m[1] + from, m[2] + from};
    }

    /** find_end_tag 移植：close(startTag) → 已知 END → 再找 start（新开块）。 */
    private static long[] findEndTag(String s, int from, String startTag) {
        String search = s.substring(from);
        if (startTag != null) {
            String openTag = trimEndGt(startTag);
            String closeTag = "</" + openTag.substring(1) + ">";
            int pos = search.indexOf(closeTag);
            if (pos >= 0) return new long[]{from + pos, from + pos, from + pos + closeTag.length()};
            String closePartial = trimEndGt(closeTag);
            long[] f = fuzzyMatchTag(search, closePartial);
            if (f != null) return new long[]{from + (int) f[0], from + (int) f[1], from + (int) f[2]};
        }
        {
            int pos = search.indexOf(TOOL_CALL_END);
            if (pos >= 0) return new long[]{from + pos, from + pos, from + pos + TOOL_CALL_END.length()};
            long[] f = fuzzyMatchTag(search, trimEndGt(TOOL_CALL_END));
            if (f != null) return new long[]{from + (int) f[0], from + (int) f[1], from + (int) f[2]};
        }
        if (startTag != null) {
            long[] m = matchStartTag(search, startTag);
            if (m != null) return new long[]{from + (int) m[0], from + (int) m[1], from + (int) m[2]};
        }
        long[] m = matchStartTag(search, TOOL_CALL_START);
        if (m != null) return new long[]{from + (int) m[0], from + (int) m[1], from + (int) m[2]};
        return null;
    }

    private static boolean looksLikeStartTag(String tag) {
        if (!tag.startsWith("<")) return false;
        String tagNorm = normAll(tag);
        String partial = normAll(trimEndGt(TOOL_CALL_START));
        return partial.startsWith(tagNorm) || tagNorm.startsWith(partial);
    }

    private static String normAll(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) sb.append(normTagChar(s.charAt(i)));
        return sb.toString();
    }

    private static int floorCharBoundary(StringBuilder s, int max) {
        if (max >= s.length()) return s.length();
        int i = max;
        while (i > 0 && Character.isLowSurrogate(s.charAt(i))) i--;
        return i;
    }

    private static boolean isInsideCodeFence(String xml, int tagPos) {
        int count = 0;
        int idx = 0;
        while ((idx = xml.indexOf("```", idx)) >= 0 && idx < tagPos) {
            count++;
            idx += 3;
        }
        return count % 2 == 1;
    }

    // ── parse_tool_calls 移植 ────────────────────────────────────────

    /** 解析收集到的完整标记块；无有效调用返回 null。 */
    public static List<ToolCallData> parseToolCalls(String xml) {
        long[] startM = matchStartTag(xml, TOOL_CALL_START);
        if (startM == null) return null;
        int start = (int) startM[0];
        String startTagStr = xml.substring((int) startM[1], (int) startM[2]);
        int afterStart = (int) startM[2];
        if (isInsideCodeFence(xml, start)) return null;

        long[] endM = findEndTag(xml, afterStart, startTagStr);
        int end;
        int innerEnd;
        if (endM != null) {
            innerEnd = (int) endM[0];
            end = (int) endM[2];
        } else {
            end = xml.length();
            innerEnd = xml.length();
        }
        String inner = xml.substring(afterStart, innerEnd);

        List<Object> arr = null;
        int arrStart = inner.indexOf('[');
        if (arrStart >= 0) {
            int arrEnd = inner.lastIndexOf(']');
            String jsonStr = arrEnd >= 0 ? inner.substring(arrStart, arrEnd + 1) : inner.substring(arrStart);
            if (jsonStr.trim().equals("[]")) return null;
            Object parsed = parseJson(jsonStr);
            if (parsed instanceof List) {
                arr = (List<Object>) parsed;
            } else {
                Object repaired = parseJson(repairInvalidBackslashes(jsonStr));
                if (repaired == null) repaired = parseJson(repairUnquotedKeys(repairInvalidBackslashes(jsonStr)));
                if (repaired instanceof List) {
                    arr = (List<Object>) repaired;
                } else if (repaired instanceof Map) {
                    List<Object> single = new ArrayList<>();
                    single.add(repaired);
                    arr = single;
                } else {
                    // 逐元素抢救：剥掉 '[' 后找 {...}
                    String trimmed = jsonStr.trim();
                    String objStr = trimmed.startsWith("[") ? trimmed.substring(1) : trimmed;
                    int objStart = objStr.indexOf('{');
                    if (objStart < 0) return null;
                    int objEnd = objStr.lastIndexOf('}');
                    Object obj = objEnd > objStart
                        ? parseJson(objStr.substring(objStart, objEnd + 1)) : null;
                    if (!(obj instanceof Map)) return null;
                    List<Object> single = new ArrayList<>();
                    single.add(obj);
                    arr = single;
                }
            }
        } else {
            int objStart = inner.indexOf('{');
            if (objStart < 0) return parseInvokeCalls(inner);
            int objEnd = inner.lastIndexOf('}');
            String jsonStr = objEnd > objStart ? inner.substring(objStart, objEnd + 1) : inner.substring(objStart);
            Object obj = parseJson(jsonStr);
            if (!(obj instanceof Map)) {
                String fixed = repairInvalidBackslashes(jsonStr);
                obj = parseJson(fixed);
                if (!(obj instanceof Map)) {
                    obj = parseJson(repairUnquotedKeys(fixed));
                }
            }
            if (!(obj instanceof Map)) return null;
            List<Object> single = new ArrayList<>();
            single.add(obj);
            arr = single;
        }

        List<ToolCallData> calls = new ArrayList<>();
        long seqCounter = 1;
        for (Object item : arr) {
            if (!(item instanceof Map)) return null;
            Object name = ((Map<String, Object>) item).get("name");
            if (!(name instanceof String)) return null;
            Object rawArgs = ((Map<String, Object>) item).get("arguments");
            String arguments = normalizeArguments(rawArgs);
            calls.add(new ToolCallData(seqCounter++, (String) name, arguments));
        }
        if (calls.isEmpty()) return null;
        return calls;
    }

    /** arguments 规范化：字符串则尽量解析再紧凑输出；对象/数组紧凑序列化；缺省 "{}"。 */
    static String normalizeArguments(Object rawArgs) {
        if (rawArgs == null) return "{}";
        if (rawArgs instanceof String) {
            String s = (String) rawArgs;
            Object parsed = parseJson(s);
            if (parsed == null) {
                String fixed = repairInvalidBackslashes(s);
                parsed = parseJson(fixed);
                if (parsed == null) return s;
                s = fixed;
            }
            return serialize(parsed);
        }
        return serialize(rawArgs);
    }

    /** ds-free-api 的 <invoke name="..."><parameter> 兜底格式。 */
    private static List<ToolCallData> parseInvokeCalls(String inner) {
        List<ToolCallData> calls = new ArrayList<>();
        String lower = inner.toLowerCase();
        int pos = 0;
        long seqCounter = 1;
        while (true) {
            int invokeStart = lower.indexOf("<invoke ", pos);
            if (invokeStart < 0) break;
            int nameStart = inner.indexOf("name=\"", invokeStart);
            if (nameStart < 0) return calls.isEmpty() ? null : calls;
            nameStart += 6;
            int nameEnd = inner.indexOf('"', nameStart);
            if (nameEnd < 0) return calls.isEmpty() ? null : calls;
            String name = inner.substring(nameStart, nameEnd);
            String closeTag = "</invoke>";
            int closePos = lower.indexOf(closeTag, invokeStart);
            if (closePos < 0) return calls.isEmpty() ? null : calls;
            String body = inner.substring(invokeStart, closePos + closeTag.length());
            String bodyLower = body.toLowerCase();
            Map<String, Object> params = new LinkedHashMap<>();
            int ppos = 0;
            boolean bad = false;
            while (true) {
                int pStart = bodyLower.indexOf("<parameter ", ppos);
                if (pStart < 0) break;
                int pNameStart = body.indexOf("name=\"", pStart);
                if (pNameStart < 0) { bad = true; break; }
                pNameStart += 6;
                int pNameEnd = body.indexOf('"', pNameStart);
                if (pNameEnd < 0) { bad = true; break; }
                String pName = body.substring(pNameStart, pNameEnd);
                int pBodyStart = body.indexOf('>', pNameEnd);
                if (pBodyStart < 0) { bad = true; break; }
                pBodyStart += 1;
                int pClose = bodyLower.indexOf("</parameter>", pBodyStart);
                if (pClose < 0) { bad = true; break; }
                String pValue = body.substring(pBodyStart, pClose);
                Object val = parseJson(pValue.trim());
                params.put(pName, val != null ? val : pValue);
                ppos = pClose + "</parameter>".length();
            }
            if (!bad && !params.isEmpty()) {
                calls.add(new ToolCallData(seqCounter++, name, serialize(params)));
            }
            pos = closePos + closeTag.length();
        }
        return calls.isEmpty() ? null : calls;
    }

    /** 测试桥：裸 &lt;invoke&gt; 兜底格式解析。 */
    public static List<ToolCallData> parseInvokeCallsForTest(String inner) {
        return parseInvokeCalls(inner);
    }

    // ── JSON 修复 ────────────────────────────────────────────────────

    static String repairInvalidBackslashes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                if (i + 1 < s.length()) {
                    char next = s.charAt(i + 1);
                    if ("\"\\/bfnrtu".indexOf(next) >= 0) {
                        out.append('\\').append(next);
                    } else {
                        out.append("\\\\").append(next);
                    }
                    i++;
                } else {
                    out.append('\\');
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static String repairUnquotedKeys(String s) {
        StringBuilder out = new StringBuilder(s.length() + 32);
        int len = s.length();
        int i = 0;
        while (i < len) {
            char c = s.charAt(i);
            if ((c == '{' || c == ',') && i + 1 < len) {
                out.append(c);
                i++;
                while (i < len && Character.isWhitespace(s.charAt(i))) {
                    out.append(s.charAt(i));
                    i++;
                }
                if (i < len && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '_')) {
                    int keyStart = i;
                    while (i < len && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                    if (i < len && s.charAt(i) == ':') {
                        out.append('"').append(s, keyStart, i).append('"');
                    } else {
                        out.append(s, keyStart, i);
                        continue;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ── 最小 JSON 解析器（无 org.json 依赖） ─────────────────────────

    private static final class JsonCursor {
        final String s;
        int i;
        JsonCursor(String s) { this.s = s; }
    }

    /** 解析失败返回 null。 */
    static Object parseJson(String s) {
        if (s == null) return null;
        try {
            JsonCursor cur = new JsonCursor(s);
            skipWs(cur);
            Object v = parseValue(cur);
            skipWs(cur);
            if (cur.i != cur.s.length()) return null;
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private static void skipWs(JsonCursor cur) {
        while (cur.i < cur.s.length() && Character.isWhitespace(cur.s.charAt(cur.i))) cur.i++;
    }

    private static Object parseValue(JsonCursor cur) {
        if (cur.i >= cur.s.length()) throw new IllegalStateException("eof");
        char c = cur.s.charAt(cur.i);
        switch (c) {
            case '{': return parseObj(cur);
            case '[': return parseArr(cur);
            case '"': return parseStr(cur);
            case 't': expect(cur, "true"); return Boolean.TRUE;
            case 'f': expect(cur, "false"); return Boolean.FALSE;
            case 'n': expect(cur, "null"); return null;
            default: return parseNum(cur);
        }
    }

    private static void expect(JsonCursor cur, String lit) {
        if (!cur.s.startsWith(lit, cur.i)) throw new IllegalStateException("literal");
        cur.i += lit.length();
    }

    private static Map<String, Object> parseObj(JsonCursor cur) {
        cur.i++;
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs(cur);
        if (cur.i < cur.s.length() && cur.s.charAt(cur.i) == '}') { cur.i++; return map; }
        while (true) {
            skipWs(cur);
            String key = parseStr(cur);
            skipWs(cur);
            if (cur.i >= cur.s.length() || cur.s.charAt(cur.i) != ':') throw new IllegalStateException("colon");
            cur.i++;
            skipWs(cur);
            map.put(key, parseValue(cur));
            skipWs(cur);
            if (cur.i >= cur.s.length()) throw new IllegalStateException("obj-eof");
            char c = cur.s.charAt(cur.i);
            if (c == ',') { cur.i++; continue; }
            if (c == '}') { cur.i++; return map; }
            throw new IllegalStateException("obj-sep");
        }
    }

    private static List<Object> parseArr(JsonCursor cur) {
        cur.i++;
        List<Object> list = new ArrayList<>();
        skipWs(cur);
        if (cur.i < cur.s.length() && cur.s.charAt(cur.i) == ']') { cur.i++; return list; }
        while (true) {
            skipWs(cur);
            list.add(parseValue(cur));
            skipWs(cur);
            if (cur.i >= cur.s.length()) throw new IllegalStateException("arr-eof");
            char c = cur.s.charAt(cur.i);
            if (c == ',') { cur.i++; continue; }
            if (c == ']') { cur.i++; return list; }
            throw new IllegalStateException("arr-sep");
        }
    }

    private static String parseStr(JsonCursor cur) {
        if (cur.s.charAt(cur.i) != '"') throw new IllegalStateException("quote");
        cur.i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (cur.i >= cur.s.length()) throw new IllegalStateException("str-eof");
            char c = cur.s.charAt(cur.i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (cur.i >= cur.s.length()) throw new IllegalStateException("esc-eof");
                char e = cur.s.charAt(cur.i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (cur.i + 4 > cur.s.length()) throw new IllegalStateException("u-eof");
                        sb.append((char) Integer.parseInt(cur.s.substring(cur.i, cur.i + 4), 16));
                        cur.i += 4;
                        break;
                    default: throw new IllegalStateException("esc");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private static Object parseNum(JsonCursor cur) {
        int start = cur.i;
        if (cur.i < cur.s.length() && (cur.s.charAt(cur.i) == '-' || cur.s.charAt(cur.i) == '+')) cur.i++;
        boolean isDouble = false;
        while (cur.i < cur.s.length()) {
            char c = cur.s.charAt(cur.i);
            if (c >= '0' && c <= '9') cur.i++;
            else if (c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') { isDouble = isDouble || c == '.' || c == 'e' || c == 'E'; cur.i++; }
            else break;
        }
        if (cur.i == start) throw new IllegalStateException("num");
        String num = cur.s.substring(start, cur.i);
        return isDouble ? (Object) Double.parseDouble(num) : (Object) Long.parseLong(num);
    }

    /** 紧凑序列化（等价 serde_json::to_string）。 */
    static String serialize(Object v) {
        StringBuilder sb = new StringBuilder();
        serializeTo(v, sb);
        return sb.toString();
    }

    private static void serializeTo(Object v, StringBuilder sb) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String) { escapeTo((String) v, sb); return; }
        if (v instanceof Boolean) { sb.append(v); return; }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 9.007199254740992E15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
            return;
        }
        if (v instanceof Number) { sb.append(v); return; }
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                escapeTo(e.getKey(), sb);
                sb.append(':');
                serializeTo(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<Object>) v) {
                if (!first) sb.append(',');
                first = false;
                serializeTo(item, sb);
            }
            sb.append(']');
            return;
        }
        escapeTo(String.valueOf(v), sb);
    }

    private static void escapeTo(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
