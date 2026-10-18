package com.termux.app.iqcode.local;

import android.content.Context;

import com.termux.app.iqcode.api.ModelProvider;
import com.termux.app.iqcode.model.AssistantTurn;
import com.termux.app.iqcode.model.SessionConfig;

import org.json.JSONArray;

/**
 * 本地/云端路由 Provider —— "红绿灯调度员"。
 *
 * 职责：按用户策略决定每轮对话走本地还是走云端；当一侧不可用/失败时，
 *       按策略自动切换，并把"走了哪条路/为什么切换"通过异常或状态暴露给上层。
 *
 * 策略（SessionConfig.localStrategy）：
 *   auto        省钱优先（默认）：云端可用→云端；断网或云端请求失败→本地兜底。
 *   local-first 离线优先：一律先本地；本地能力不足→明确抛错（不偷偷切云端）。
 *   cloud-first 质量优先：一律云端；本地仅用于补全预览（本类不处理预览）。
 *
 * 接入方式（ModelProviders.forConfig 内）：
 *   if (config.localFallback) return new RouterProvider(context, cloudProvider);
 *   引擎（IQCodeEngine）不需要任何改动。
 */
public final class RouterProvider implements ModelProvider {

    public static final String STRATEGY_AUTO = "auto";
    public static final String STRATEGY_LOCAL_FIRST = "local-first";
    public static final String STRATEGY_CLOUD_FIRST = "cloud-first";

    private final Context context;
    private final ModelProvider cloud;
    private final LocalModelProvider local;

    /**
     * @param cloud 云端 Provider（由 ModelProviders.forConfig 按原协议构造，必须非空）
     * @param local 本地 Provider（可替换为测试替身）
     */
    public RouterProvider(Context context, ModelProvider cloud, LocalModelProvider local) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.cloud = cloud;
        this.local = local;
    }

    public LocalModelProvider local() { return local; }

    /** 读策略；非法值回退 auto。 */
    private static String strategyOf(SessionConfig config) {
        if (config == null || config.localStrategy == null || config.localStrategy.isEmpty()) {
            return STRATEGY_AUTO;
        }
        return config.localStrategy;
    }

    @Override
    public AssistantTurn createMessage(SessionConfig config, String systemPrompt,
                                       JSONArray messages, JSONArray tools,
                                       StreamListener listener) throws Exception {
        String strategy = strategyOf(config);
        switch (strategy) {
            case STRATEGY_LOCAL_FIRST:
                return localFirst(config, systemPrompt, messages, tools, listener);
            case STRATEGY_CLOUD_FIRST:
                return cloud.createMessage(config, systemPrompt, messages, tools, listener);
            case STRATEGY_AUTO:
            default:
                return auto(config, systemPrompt, messages, tools, listener);
        }
    }

    // ==================== auto：省钱优先（默认） ====================

    private AssistantTurn auto(SessionConfig config, String systemPrompt, JSONArray messages,
                               JSONArray tools, StreamListener listener) throws Exception {
        // 1) 本地明显不可用（低配/发热/未下载）→ 直接走云端，不折腾。
        if (!local.isAvailable()) {
            return cloud.createMessage(config, systemPrompt, messages, tools, listener);
        }

        // 2) 断网 → 走本地（这是"兜底"的核心场景）。
        DeviceProfile dp = DeviceProfile.detect(context);
        if (!dp.networkOnline) {
            try {
                return local.createMessage(config, systemPrompt, messages, tools, listener);
            } catch (Exception e) {
                throw new StreamFailure("local_failed_no_network",
                    "当前无网络且本地模型不可用：" + message(e), e);
            }
        }

        // 3) 有网 → 先云端；云端失败（超时/5xx/网络抖动）→ 本地兜底一次。
        try {
            return cloud.createMessage(config, systemPrompt, messages, tools, listener);
        } catch (Exception cloudError) {
            if (!(cloudError instanceof StreamFailure)) {
                // 非协议错误（如网络 IO 异常）才值得兜底；协议错误说明云端在线但不认请求，兜底无意义
                try {
                    return local.createMessage(config, systemPrompt, messages, tools, listener);
                } catch (Exception localError) {
                    throw new StreamFailure("both_failed",
                        "云端失败（" + message(cloudError) + "），本地兜底也失败（" + message(localError) + "）",
                        cloudError);
                }
            }
            throw cloudError;
        }
    }

    // ==================== local-first：离线优先 ====================

    private AssistantTurn localFirst(SessionConfig config, String systemPrompt, JSONArray messages,
                                     JSONArray tools, StreamListener listener) throws Exception {
        if (!local.isAvailable()) {
            throw new StreamFailure("local_unavailable",
                "离线优先模式下本地模型不可用：" + local.unavailabilityReason());
        }
        try {
            return local.createMessage(config, systemPrompt, messages, tools, listener);
        } catch (Exception e) {
            throw new StreamFailure("local_failed",
                "本地模型执行失败（" + message(e) + "）。如需继续请切换到云端策略。", e);
        }
    }

    // ==================== 工具方法 ====================

    private static String message(Exception e) {
        if (e == null) return "未知错误";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    @Override
    public void cancelRequest(Thread worker) {
        // 云端请求和本地推理都尝试取消；各自内部实现幂等。
        try { cloud.cancelRequest(worker); } catch (Throwable ignored) { }
        try { local.cancelRequest(worker); } catch (Throwable ignored) { }
    }
}
