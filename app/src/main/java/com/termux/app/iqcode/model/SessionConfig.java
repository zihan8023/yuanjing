package com.termux.app.iqcode.model;

import com.termux.shared.termux.TermuxConstants;

import java.util.UUID;

/** Runtime configuration for a Java-native IQ Code session. */
public final class SessionConfig {
    public String protocol = "openai-responses";
    /** User-supplied API endpoint. Deliberately blank until configured in Settings. */
    public String baseUrl = "";
    public String apiKey = "";
    public String profileId = "";
    public int profileRevision = 1;
    public int credentialRevision = 1;
    public String model = "gpt-5.6-terra";
    /** 是否启用"本地小模型兜底"路由（RouterProvider）。 */
    public boolean localFallback = false;
    /** 本地/云端路由策略：auto / local-first / cloud-first，见 RouterProvider。 */
    public String localStrategy = "auto";
    /** 指定本地模型 id（LocalModelManager.CATALOG 中的 id）；空 = 按设备自动推荐。 */
    public String localModelId = "";
    /** Whether image blocks are included in provider requests. */
    public boolean visionEnabled = true;
    public String effort = "high";
    /** User-authored high-priority instructions, still below code-enforced safety and permissions. */
    public String customSystemPrompt = "";
    public String reasoningSummary = "auto";
    public boolean preserveReasoningState = true;
    public String toolMode = "auto";
    /** Built-in tools ("plugins") the user switched off. Empty means every tool is enabled. */
    public final java.util.Set<String> disabledTools = new java.util.LinkedHashSet<>();
    public String sessionId = UUID.randomUUID().toString();
    public String threadId = sessionId;
    /** Stable task namespace for the lifetime of this coding workflow. */
    public String workflowId = UUID.randomUUID().toString();
    public String permissionMode = "default";
    public String permissionModeBeforePlan = "default";
    /** Atomically replaced immutable snapshot of the current plan workflow. */
    public volatile PlanWorkflowState planWorkflowState = PlanWorkflowState.idle();
    /** User-authorized full control inside IQSandbox only. Host/system processes are not included. */
    public boolean sandboxAgentFullAccess = true;
    /** Exposes the opt-in Root tool. Commands still require a real Magisk/KernelSU su grant. */
    public boolean rootExecutionEnabled = false;
    /** Exposes the opt-in Shizuku tool: shell-identity commands through the Shizuku service, no root needed. */
    public boolean shizukuExecutionEnabled = false;
    /** Keeps the app in an explicit foreground service until the user turns it off. */
    public boolean forcedKeepAliveEnabled = false;
    public String projectDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;
    public String worktreeOriginalDirectory = "";
    public String worktreePath = "";
    public int maxTokens = 32768;
    public int maxAgentTurns = 100;
    public boolean streamThinking = true;
    public int contextWindowTokens = 128000;
    /** 聊天区渲染窗口：只铺最近多少条消息（不影响发给模型的上下文，也不删历史）。 */
    public int transcriptWindowMessages = 20;
    public boolean autoCompact = true;
    /** Optional user ceiling; the Claude-style output reserve and 13k safety buffer remain authoritative. */
    public double autoCompactRatio = 1.0;
    public boolean webSearchEnabled = true;
    public String webSearchProvider = "auto";
    public int webSearchMaxResults = 6;
    public int webFetchMaxChars = 30000;
    public int webTimeoutMs = 15000;

    /** True only when the user explicitly switched this tool off in the plugin settings. */
    public boolean isToolDisabled(String name) { return name != null && disabledTools.contains(name); }

    /** Enables or disables one built-in tool; the set stays minimal so the default is "all on". */
    public void setToolEnabled(String name, boolean enabled) {
        if (name == null || name.isEmpty()) return;
        if (enabled) disabledTools.remove(name); else disabledTools.add(name);
    }

    public void renewTransportSession() {
        sessionId = UUID.randomUUID().toString();
        threadId = sessionId;
    }

    public SessionConfig copy() {
        SessionConfig c = new SessionConfig();
        c.protocol = protocol;
        c.baseUrl = baseUrl;
        c.apiKey = apiKey;
        c.profileId = profileId;
        c.profileRevision = profileRevision;
        c.credentialRevision = credentialRevision;
        c.model = model;
        c.localFallback = localFallback;
        c.localStrategy = localStrategy;
        c.localModelId = localModelId;
        c.visionEnabled = visionEnabled;
        c.effort = effort;
        c.customSystemPrompt = customSystemPrompt;
        c.reasoningSummary = reasoningSummary;
        c.preserveReasoningState = preserveReasoningState;
        c.toolMode = toolMode;
        c.disabledTools.addAll(disabledTools);
        c.sessionId = sessionId;
        c.threadId = threadId;
        c.workflowId = workflowId;
        c.permissionMode = permissionMode;
        c.permissionModeBeforePlan = permissionModeBeforePlan;
        PlanWorkflowState planSnapshot = planWorkflowState;
        c.planWorkflowState = planSnapshot == null ? PlanWorkflowState.idle() : planSnapshot.copy();
        c.sandboxAgentFullAccess = sandboxAgentFullAccess;
        c.rootExecutionEnabled = rootExecutionEnabled;
        c.shizukuExecutionEnabled = shizukuExecutionEnabled;
        c.forcedKeepAliveEnabled = forcedKeepAliveEnabled;
        c.projectDirectory = projectDirectory;
        c.worktreeOriginalDirectory = worktreeOriginalDirectory;
        c.worktreePath = worktreePath;
        c.maxTokens = maxTokens;
        c.maxAgentTurns = maxAgentTurns;
        c.streamThinking = streamThinking;
        c.contextWindowTokens = contextWindowTokens;
        c.transcriptWindowMessages = transcriptWindowMessages;
        c.autoCompact = autoCompact;
        c.autoCompactRatio = autoCompactRatio;
        c.webSearchEnabled = webSearchEnabled;
        c.webSearchProvider = webSearchProvider;
        c.webSearchMaxResults = webSearchMaxResults;
        c.webFetchMaxChars = webFetchMaxChars;
        c.webTimeoutMs = webTimeoutMs;
        return c;
    }
}
