package com.termux.app.iqcode.api;

import android.content.Context;

import com.termux.app.iqcode.local.LocalModelProvider;
import com.termux.app.iqcode.local.RouterProvider;
import com.termux.app.iqcode.model.SessionConfig;

/** Selects a Java-native wire provider. No CLI subprocess is involved. */
public final class ModelProviders {
    private ModelProviders() {}

    public static ModelProvider forConfig(SessionConfig config) {
        return forConfig(config, null);
    }

    public static ModelProvider forConfig(SessionConfig config, Context context) {
        String protocol = config == null || config.protocol == null ? "anthropic" : config.protocol;
        if ("openai-responses".equals(protocol) || "codex-responses".equals(protocol)) {
            return new OpenAIResponsesProvider();
        }
        if ("openai-compatible".equals(protocol) || "openai-chat".equals(protocol)) {
            return new OpenAIChatCompletionsProvider();
        }
        if ("anthropic".equals(protocol)) return new AnthropicMessagesProvider();
        if ("zcode".equals(protocol)) return new ZcodePlanProvider(context);
        if ("deepseek-free".equals(protocol)) {
            if (context == null) {
                throw new IllegalArgumentException("DeepSeek 免费网页版协议需要应用上下文");
            }
            return new DeepSeekWebProvider(context);
        }
        // 本地小模型：当 LocalModelProvider 完整实现后，可直接选 "local" 协议使用。
        if ("local".equals(protocol)) {
            if (context == null) {
                throw new IllegalArgumentException("本地模型协议需要应用上下文");
            }
            return new LocalModelProvider(context);
        }
        // 本地兜底路由：在用户选定的云端协议外面包一层"红绿灯调度员"，
        // 引擎（IQCodeEngine）无需任何改动即可获得断网/失败时的本地兜底能力。
        if (config != null && config.localFallback) {
            if (context == null) {
                throw new IllegalArgumentException("本地兜底路由需要应用上下文");
            }
            ModelProvider cloud = forConfig(configWithoutFallback(config), context);
            return new RouterProvider(context, cloud, new LocalModelProvider(context));
        }
        throw new IllegalArgumentException("Protocol is not Java-ported yet: " + protocol);
    }

    /** 构造云端 Provider 时临时关闭 fallback，避免路由无限嵌套。 */
    private static SessionConfig configWithoutFallback(SessionConfig config) {
        SessionConfig c = config.copy();
        c.localFallback = false;
        return c;
    }
}
