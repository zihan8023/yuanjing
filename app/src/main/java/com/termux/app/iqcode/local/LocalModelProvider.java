package com.termux.app.iqcode.local;

import android.content.Context;

import com.termux.app.iqcode.api.ModelProvider;
import com.termux.app.iqcode.model.AssistantTurn;
import com.termux.app.iqcode.model.SessionConfig;
import com.termux.app.iqcode.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地小模型 Provider（完整版）。
 *
 * 流程：设备/模型裁决 → 组装 Qwen chat prompt → 启动/复用 llama-server →
 *       SSE 流式推理 → 解析 <tool_call> 工具调用 → AssistantTurn。
 *
 * 能力边界（诚实降级）：
 * - 本地模型上下文 = min(配置, 档位上限)，超长由引擎 ContextCompactor 先压缩；
 * - 无视觉输入（SessionConfig.visionEnabled 由路由层负责关）；
 * - 不支持工具调用的档位（0.5B）→ tools 传空，模型只做纯文本回合。
 */
public final class LocalModelProvider implements ModelProvider {

    private static final Pattern TOOL_CALL_PATTERN =
        Pattern.compile("<tool_call>\\s*\\{([\\s\\S]*?)\\}\\s*</tool_call>");
    private static final int DEFAULT_MAX_TOKENS = 2048;

    private final Context context;

    public LocalModelProvider(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    // ==================== 可用性裁决 ====================

    /** 本地是否可用：二进制存在 + 模型已下载 + 设备够配 + 不发热/不低电。 */
    public boolean isAvailable() {
        try {
            if (context == null) return false;
            if (LocalModelManager.missingBinaryReason() != null) return false;
            DeviceProfile dp = DeviceProfile.detect(context);
            if (dp.tier() == DeviceProfile.Tier.LOW) return false;
            if (!dp.thermallyFit()) return false;
            return !LocalModelManager.installed().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 不可用原因（给 UI/路由层提示）。 */
    public String unavailabilityReason() {
        try {
            if (context == null) return "应用上下文不可用";
            String bin = LocalModelManager.missingBinaryReason();
            if (bin != null) return bin;
            DeviceProfile dp = DeviceProfile.detect(context);
            if (dp.tier() == DeviceProfile.Tier.LOW) {
                return "设备内存过低（" + dp.totalRamMb + "MB），无法运行本地模型";
            }
            if (!dp.thermallyFit()) {
                return dp.batteryPercent < 15 && !dp.charging
                    ? "电量过低，为省电已禁用本地模型"
                    : "设备温度较高（" + dp.batteryTempC + "℃），已禁用本地模型";
            }
            if (LocalModelManager.installed().isEmpty()) {
                return "还没有下载本地模型（设置 → 本地模型 → 下载）";
            }
            return "本地模型引擎未就绪";
        } catch (Throwable t) {
            return "设备信息读取失败";
        }
    }

    // ==================== 主入口 ====================

    @Override
    public AssistantTurn createMessage(SessionConfig config, String systemPrompt,
                                       JSONArray messages, JSONArray tools,
                                       StreamListener listener) throws Exception {
        if (context == null) {
            throw new StreamFailure("local_no_context", "本地模型需要应用上下文");
        }
        String bin = LocalModelManager.missingBinaryReason();
        if (bin != null) throw new StreamFailure("local_no_binary", bin);

        DeviceProfile dp = DeviceProfile.detect(context);
        if (dp.tier() == DeviceProfile.Tier.LOW) {
            throw new StreamFailure("local_low_end", "设备内存过低，无法运行本地模型");
        }
        if (!dp.thermallyFit()) {
            throw new StreamFailure("local_thermal", "设备发热或电量过低，已禁用本地模型");
        }

        // 1) 选模型：配置指定优先，否则按设备推荐
        LocalModelManager.ModelDef def =
            config != null && config.localModelId != null && !config.localModelId.isEmpty()
                ? LocalModelManager.def(config.localModelId)
                : LocalModelManager.recommend(dp);
        if (def == null) {
            throw new StreamFailure("local_no_model", "当前设备无法运行任何本地模型（内存或存储不足）");
        }
        if (!LocalModelManager.isDownloaded(def)) {
            throw new StreamFailure("local_not_downloaded", "模型未下载: " + def.ggufFile
                + "（设置 → 本地模型 → 下载）");
        }

        // 2) 不支持工具调用的档位 → 关闭工具（诚实降级）
        JSONArray effectiveTools = def.supportsTools ? tools : new JSONArray();

        // 3) 组装 prompt
        int maxTokens = config != null && config.maxTokens > 0
            ? Math.min(config.maxTokens, DEFAULT_MAX_TOKENS * 4) : DEFAULT_MAX_TOKENS;
        String prompt = LocalModelManager.buildPrompt(
            systemPrompt == null ? "" : systemPrompt, messages, effectiveTools, def, maxTokens);

        // 4) 启动/复用 server
        LocalModelManager.ServerHandle handle = LocalModelManager.acquire(context, def);

        // 5) 流式推理
        LocalModelManager.CompletionResult result;
        try {
            result = LocalModelManager.completion(handle, prompt, maxTokens, listener);
        } finally {
            LocalModelManager.reclaimIdle();
        }

        // 6) 解析工具调用
        List<ToolCall> toolCalls = def.supportsTools ? parseToolCalls(result.text) : new ArrayList<>();

        AssistantTurn turn = new AssistantTurn();
        String text = cleanText(result.text);
        if (!text.isEmpty()) {
            turn.content.put(new JSONObject().put("type", "text").put("text", text));
        }
        turn.toolCalls.addAll(toolCalls);
        turn.stopReason = toolCalls.isEmpty() ? "end_turn" : "tool_use";
        turn.inputTokens = result.inputTokens;
        turn.outputTokens = result.outputTokens;
        if (listener != null) listener.onUsage(turn.inputTokens, turn.outputTokens);
        return turn;
    }

    @Override
    public void cancelRequest(Thread worker) {
        // 中断正在读取 SSE 的线程；completion 内部检测 isInterrupted 并抛 InterruptedException
        if (worker != null) worker.interrupt();
    }

    // ==================== 工具调用解析 ====================

    /** 从模型输出里提取所有 <tool_call>{...}</tool_call>。 */
    static List<ToolCall> parseToolCalls(String text) {
        List<ToolCall> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        Matcher m = TOOL_CALL_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group(1);
            try {
                JSONObject call = new JSONObject(raw);
                String name = call.optString("name", "");
                if (name.isEmpty()) continue;
                Object args = call.opt("arguments");
                JSONObject input;
                if (args instanceof JSONObject) {
                    input = (JSONObject) args;
                } else if (args != null && !args.toString().isEmpty()) {
                    try { input = new JSONObject(args.toString()); }
                    catch (Exception e) { input = new JSONObject().put("_raw", args.toString()); }
                } else {
                    input = new JSONObject();
                }
                out.add(new ToolCall(UUID.randomUUID().toString(), name, input));
            } catch (Exception ignored) {
                // 模型输出不规范的 tool_call 块直接跳过，不阻断整轮
            }
        }
        return out;
    }

    /** 去掉模型输出里的特殊标记，只留给人看的正文。 */
    static String cleanText(String text) {
        if (text == null) return "";
        String t = text.replace("<|im_end|>", "").replace("<|im_start|>", "");
        t = t.replaceAll("</?tool_call>", "");
        return t.trim();
    }
}
