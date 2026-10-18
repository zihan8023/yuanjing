import com.termux.app.iqcode.api.deepseek.DeepSeekChatPrompt;
import com.termux.app.iqcode.api.deepseek.DeepSeekToolCallStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** DeepSeek 免费网页版移植的结构与行为断言（纯字符串路径，不依赖 org.json/Android）。 */
public class DeepSeekFreePortTest {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testToolCallParse();
        testToolCallRepairs();
        testFuzzyEndTag();
        testDsmlParse();
        testDsmlBareParamsLeak();
        testBareInvokeLeak();
        testCodeFenceSkip();
        testChatMlBuild();
        testSplitHistory();
        testStructureAssertions();
        if (failures > 0) {
            System.out.println("DeepSeekFreePortTest FAIL (" + failures + ")");
            System.exit(1);
        }
        System.out.println("DeepSeekFreePortTest PASS");
    }

    private static void check(boolean cond, String what) {
        if (!cond) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static void testToolCallParse() {
        String xml = DeepSeekToolCallStream.TOOL_CALL_START
            + "[{\"name\": \"get_weather\", \"arguments\": {\"city\": \"北京\"}}]"
            + DeepSeekToolCallStream.TOOL_CALL_END;
        List<DeepSeekToolCallStream.ToolCallData> calls = DeepSeekToolCallStream.parseToolCalls(xml);
        check(calls != null && calls.size() == 1, "单个工具调用解析");
        check(calls != null && "get_weather".equals(calls.get(0).name), "工具名提取");
        check(calls != null && calls.get(0).arguments.contains("北京"), "参数保留中文");

        String multi = DeepSeekToolCallStream.TOOL_CALL_START
            + "[{\"name\": \"a\", \"arguments\": {}}, {\"name\": \"b\", \"arguments\": {\"tz\": \"bj\"}}]"
            + DeepSeekToolCallStream.TOOL_CALL_END;
        List<DeepSeekToolCallStream.ToolCallData> multiCalls = DeepSeekToolCallStream.parseToolCalls(multi);
        check(multiCalls != null && multiCalls.size() == 2, "多工具调用解析");
    }

    private static void testToolCallRepairs() {
        String unquoted = DeepSeekToolCallStream.TOOL_CALL_START
            + "[{name: \"get_weather\", arguments: {city: \"北京\"}}]"
            + DeepSeekToolCallStream.TOOL_CALL_END;
        List<DeepSeekToolCallStream.ToolCallData> calls = DeepSeekToolCallStream.parseToolCalls(unquoted);
        check(calls != null && calls.size() == 1, "未加引号 key 修复");

        String badBackslash = DeepSeekToolCallStream.TOOL_CALL_START
            + "[{\"name\": \"read_file\", \"arguments\": {\"path\": \"C:\\Users\\name\"}}]"
            + DeepSeekToolCallStream.TOOL_CALL_END;
        List<DeepSeekToolCallStream.ToolCallData> calls2 = DeepSeekToolCallStream.parseToolCalls(badBackslash);
        check(calls2 != null && calls2.size() == 1, "非法反斜杠修复");
    }

    private static void testFuzzyEndTag() {
        String xml = DeepSeekToolCallStream.TOOL_CALL_START
            + "[{\"name\": \"get_weather\", \"arguments\": {\"city\": \"北京\"}}]"
            + "<|tool_calls▁end｜>";
        List<DeepSeekToolCallStream.ToolCallData> calls = DeepSeekToolCallStream.parseToolCalls(xml);
        check(calls != null && calls.size() == 1, "模糊结束标记识别（｜↔| ▁↔_）");
    }

    private static void testDsmlParse() {
        // 网页模型泄漏的 DSML 工具标记（实测样本）
        String dsml = "<｜｜DSML｜｜ calls>\n<｜｜DSML｜｜ invoke name=\"LS\">\n"
            + "<｜｜DSML｜｜ parameter name=\"path\">/data/user/0/com.iqge/files/home</｜｜DSML｜｜ parameter>\n"
            + "</｜｜DSML｜｜ invoke>\n</｜｜DSML｜｜ calls>";
        List<DeepSeekToolCallStream.ToolCallData> calls = DeepSeekToolCallStream.parseDsmlCalls(dsml);
        check(calls != null && calls.size() == 1, "DSML 标记解析");
        check(calls != null && "LS".equals(calls.get(0).name), "DSML 工具名");
        check(calls != null && calls.get(0).arguments.contains("/data/user/0/com.iqge/files/home"), "DSML 参数值");
        check(calls != null && calls.get(0).arguments.startsWith("{"), "DSML 参数为 JSON");

        // 流式喂入：DSML 块被拦截，文本保留
        DeepSeekToolCallStream stream = new DeepSeekToolCallStream();
        List<DeepSeekToolCallStream.Out> outs = new java.util.ArrayList<>();
        outs.addAll(stream.feed("我先看下目录。\n"));
        String marker = dsml;
        for (int i = 0; i < marker.length(); i += 7) {
            outs.addAll(stream.feed(marker.substring(i, Math.min(marker.length(), i + 7))));
        }
        outs.addAll(stream.feed("好了。"));
        outs.addAll(stream.finish());
        StringBuilder text = new StringBuilder();
        int callCount = 0;
        for (DeepSeekToolCallStream.Out o : outs) {
            if (o.text != null) text.append(o.text);
            if (o.calls != null) callCount += o.calls.size();
        }
        check(callCount == 1, "DSML 流式解析为 1 个调用");
        check(text.toString().contains("我先看下目录") && text.toString().contains("好了"), "DSML 块外的文本保留");
        check(!text.toString().contains("DSML"), "DSML 标记不出现在正文");
    }

    private static void testDsmlBareParamsLeak() {
        // 实测样本：DSML calls 包装但直接跟普通 parameter（无 invoke/工具名），按 Bash 兜底
        String dsml = "<｜｜DSML｜｜ calls>\n"
            + "<parameter name=\"command\">cd /data/user/0/com.iqge/files/home && pwd && ls -la "
            + "&& echo \"===JAVAC===\" && command -v javac && javac -version 2>&1; echo \"===JAVA===\"; "
            + "command -v java && java -version 2>&1; echo \"===DONE===\"</parameter>\n"
            + "<parameter name=\"description\">Check home dir and Java toolchain</parameter>\n"
            + "</｜｜DSML｜｜ calls>";
        List<DeepSeekToolCallStream.ToolCallData> calls = DeepSeekToolCallStream.parseDsmlCalls(dsml);
        check(calls != null && calls.size() == 1, "DSML 无名块解析为 1 个调用");
        check(calls != null && "Bash".equals(calls.get(0).name), "DSML 无名块缺省 Bash");
        check(calls != null && calls.get(0).arguments.contains("===DONE==="), "DSML 无名块 command 保留");
        check(calls != null && calls.get(0).arguments.contains("Java toolchain"), "DSML 无名块 description 保留");

        // 流式喂入：整块转调用，标记不进正文
        DeepSeekToolCallStream stream = new DeepSeekToolCallStream();
        List<DeepSeekToolCallStream.Out> outs = new java.util.ArrayList<>();
        for (int i = 0; i < dsml.length(); i += 6) {
            outs.addAll(stream.feed(dsml.substring(i, Math.min(dsml.length(), i + 6))));
        }
        outs.addAll(stream.finish());
        StringBuilder text = new StringBuilder();
        int callCount = 0;
        String args = "";
        for (DeepSeekToolCallStream.Out o : outs) {
            if (o.text != null) text.append(o.text);
            if (o.calls != null) {
                callCount += o.calls.size();
                args = o.calls.get(0).arguments;
            }
        }
        check(callCount == 1, "DSML 无名块流式解析为 1 个调用");
        check(!text.toString().contains("DSML") && !text.toString().contains("<parameter"), "DSML 无名块标记不进正文");
        check(args.contains("javac -version"), "DSML 无名块流式参数完整");
    }

    private static void testBareInvokeLeak() {
        // 网页模型泄漏的 antml 风格裸块（实测样本：无任何工具标记包裹）
        String bare = "<Bash>\n"
            + "<parameter name=\"command\">cd /data/user/0/com.iqge/files/home && ls -la HelloWorld.java "
            + "2>/dev/null && echo \"=== JAVA ===\" && (which javac && javac -version && java -version) "
            + "2>&1 || echo \"NO_JDK\"</parameter>\n"
            + "<parameter name=\"description\">检查 HelloWorld.java 与 JDK 可用性</parameter>\n"
            + "</Bash>";
        List<DeepSeekToolCallStream.ToolCallData> calls = DeepSeekToolCallStream.parseBareToolCalls(bare);
        check(calls != null && calls.size() == 1, "裸 antml 块解析");
        check(calls != null && "Bash".equals(calls.get(0).name), "裸块工具名取自顶层标签");
        check(calls != null && calls.get(0).arguments.contains("NO_JDK"), "裸块 command 参数保留");
        check(calls != null && calls.get(0).arguments.contains("检查 HelloWorld"), "裸块多参数保留");
        check(calls != null && calls.get(0).arguments.startsWith("{"), "裸块参数为 JSON");

        // 裸 <invoke name="..."> 兜底格式
        List<DeepSeekToolCallStream.ToolCallData> invCalls = DeepSeekToolCallStream.parseInvokeCallsForTest(
            "<invoke name=\"LS\">\n<parameter name=\"path\">/data</parameter>\n</invoke>");
        check(invCalls != null && invCalls.size() == 1 && "LS".equals(invCalls.get(0).name), "裸 invoke 解析");

        // 流式喂入：块被拦截转成调用，块外文本保留，标记不出现在正文
        DeepSeekToolCallStream stream = new DeepSeekToolCallStream();
        List<DeepSeekToolCallStream.Out> outs = new java.util.ArrayList<>();
        outs.addAll(stream.feed("好的，我来检查。\n"));
        for (int i = 0; i < bare.length(); i += 5) {
            outs.addAll(stream.feed(bare.substring(i, Math.min(bare.length(), i + 5))));
        }
        outs.addAll(stream.feed("检查完毕。"));
        outs.addAll(stream.finish());
        StringBuilder text = new StringBuilder();
        int callCount = 0;
        String callArgs = "";
        for (DeepSeekToolCallStream.Out o : outs) {
            if (o.text != null) text.append(o.text);
            if (o.calls != null) {
                callCount += o.calls.size();
                callArgs = o.calls.get(0).arguments;
            }
        }
        check(callCount == 1, "裸块流式解析为 1 个调用");
        check(text.toString().contains("好的，我来检查") && text.toString().contains("检查完毕"), "裸块外的文本保留");
        check(!text.toString().contains("<Bash>") && !text.toString().contains("<parameter"), "裸块标记不出现在正文");
        check(callArgs.contains("which javac"), "流式路径参数完整");

        // 代码块内的裸块样例不解析（文档/示例不受影响）
        DeepSeekToolCallStream fenceStream = new DeepSeekToolCallStream();
        List<DeepSeekToolCallStream.Out> fenceOuts = new java.util.ArrayList<>();
        fenceOuts.addAll(fenceStream.feed("示例：\n```xml\n" + bare + "\n```\n完。"));
        fenceOuts.addAll(fenceStream.finish());
        StringBuilder fenceText = new StringBuilder();
        int fenceCalls = 0;
        for (DeepSeekToolCallStream.Out o : fenceOuts) {
            if (o.text != null) fenceText.append(o.text);
            if (o.calls != null) fenceCalls += o.calls.size();
        }
        check(fenceCalls == 0, "代码块内裸块不转调用");
        check(fenceText.toString().contains("<Bash>"), "代码块内裸块保留为文本");
    }

    private static void testCodeFenceSkip() {
        String xml = "示例：\n```json\n" + DeepSeekToolCallStream.TOOL_CALL_START
            + "[{\"name\": \"get_weather\", \"arguments\": {}}]"
            + DeepSeekToolCallStream.TOOL_CALL_END + "\n```";
        check(DeepSeekToolCallStream.parseToolCalls(xml) == null, "代码块内的标记不解析");
    }

    private static void testChatMlBuild() {
        List<DeepSeekChatPrompt.Msg> msgs = new java.util.ArrayList<>();
        msgs.add(DeepSeekChatPrompt.Msg.of("system", "你是助手。"));
        msgs.add(DeepSeekChatPrompt.Msg.of("user", "你好"));
        String prompt = DeepSeekChatPrompt.build(msgs, null, null, null);
        check(prompt.startsWith("<｜System｜>你是助手。"), "System 头标签");
        check(prompt.contains("<｜end▁of▁sentence｜><｜User｜>你好"), "user EOS 前缀");
        check(prompt.endsWith("<｜Assistant｜>\n"), "Assistant 锚点");

        // 连续同角色合并
        List<DeepSeekChatPrompt.Msg> merged = new java.util.ArrayList<>();
        merged.add(DeepSeekChatPrompt.Msg.of("user", "a"));
        merged.add(DeepSeekChatPrompt.Msg.of("user", "b"));
        List<DeepSeekChatPrompt.Msg> out = DeepSeekChatPrompt.mergeMessages(merged);
        check(out.size() == 1 && "a\nb".equals(out.get(0).content), "连续 user 消息合并");

        // assistant 工具调用回放
        List<DeepSeekChatPrompt.Msg> replay = new java.util.ArrayList<>();
        List<String[]> calls = new java.util.ArrayList<>();
        calls.add(new String[]{"Bash", "{\"command\": \"ls\"}"});
        replay.add(new DeepSeekChatPrompt.Msg("assistant", "", calls, null));
        String p2 = DeepSeekChatPrompt.build(replay, null, null, null);
        check(p2.contains(DeepSeekChatPrompt.TOOL_CALL_START + "\n[{\"name\": \"Bash\", \"arguments\": {\"command\":\"ls\"}}]\n"
            + DeepSeekChatPrompt.TOOL_CALL_END), "assistant 工具调用回放");
    }

    private static void testSplitHistory() {
        String prompt = "<｜System｜>sys\n<｜User｜>历史\n<｜Assistant｜>回答\n<｜User｜>最新";
        DeepSeekChatPrompt.SplitResult r = DeepSeekChatPrompt.splitHistoryPrompt(prompt);
        check("<｜Assistant｜>回答\n".equals(r.inlinePrompt), "inline 只含最后 assistant 块");
        check(r.historyContent.startsWith("[file content end]\n\n"), "history 以 file content end 开头");
        check(r.historyContent.endsWith("[file name]: IGNORE\n[file content begin]\n"), "history 包装尾");
        check(r.historyContent.contains("<｜User｜>历史"), "history 含早期块");

        check(DeepSeekChatPrompt.inputCharacterLimitFor("default") == 2_621_440, "default 字符上限");
        check(DeepSeekChatPrompt.oversizedThreshold("default") == 2_621_440 * 75 / 100, "default 超限阈值");
    }

    private static void testStructureAssertions() throws Exception {
        String provider = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/com/termux/app/iqcode/api/DeepSeekWebProvider.java")));
        check(provider.contains("deepseek-free") == false, "provider 不含协议选择");
        check(provider.contains("implements ModelProvider"), "provider 实现 ModelProvider");
        check(provider.contains("parseReadyMessageIds"), "provider 解析 ready 消息 id");
        check(provider.contains("uploadAndPoll"), "provider 超限回退历史上传");
        check(provider.contains("stopStream") && provider.contains("deleteSession"), "provider 会话清理");
        check(provider.contains("40003"), "provider 40003 重登");
        check(provider.contains("\"token\".equals(parts[0].trim())"), "provider 支持网页登录 token 凭据");

        String rest = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/com/termux/app/iqcode/api/deepseek/DeepSeekRestClient.java")));
        check(rest.contains("\"Content-Type\", \"application/json\""), "JSON POST 必须带 Content-Type（缺了会 422 loc=body）");

        String providers = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/com/termux/app/iqcode/api/ModelProviders.java")));
        check(providers.contains("\"deepseek-free\""), "ModelProviders 注册 deepseek-free");

        String resolver = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/com/termux/app/iqcode/api/ApiEndpointResolver.java")));
        check(resolver.contains("\"deepseek-free\".equals(protocol)) return \"\""), "deepseek-free 无模型目录端点");

        String catalog = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/com/termux/app/iqcode/api/ModelCatalogClient.java")));
        check(catalog.contains("\"deepseek-free\".equals(config.protocol)")
            && catalog.contains("deepseek-default"), "deepseek-free 内置固定模型目录");

        String engine = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/com/termux/app/iqcode/core/IQCodeEngine.java")));
        check(engine.contains("ModelProviders.forConfig(requestConfig, appContext)"), "engine 主请求传 appContext");
        check(engine.contains("ModelProviders.forConfig(summaryConfig, appContext)"), "engine 摘要请求传 appContext");

        String main = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/com/iqge/MainActivity.java")));
        check(main.contains("\"DeepSeek 免费网页版\"") && main.contains("\"deepseek-free\""), "MainActivity 协议选项");
        check(main.contains("panel.addView(labeled(\"协议\",protocol))"), "协议行字面量保持（ApiProfileStructureTest 依赖）");
        check(main.contains("showDeepSeekWebLogin") && main.contains("chat.deepseek.com/sign_in"), "内置网页登录入口");
        check(main.contains("X-Device-Id") && main.contains("Authorization"), "网页登录从请求头抓 token/device_id");

        java.nio.file.Path wasm = Paths.get("app/src/main/assets/ds_pow/sha3_wasm_bg.wasm");
        check(Files.size(wasm) > 1000, "PoW wasm 资产内置");
    }
}
