package com.iqge;

import com.iqge.background.KeepAliveService;
import com.iqge.sandbox.IQSandboxEngine;
import com.iqge.sandbox.SandboxDashboardActivity;
import com.iqge.sandbox.SandboxDebugLog;
import com.termux.app.iqcode.local.DeviceProfile;
import com.termux.app.iqcode.local.LocalModelManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.app.iqcode.core.IQCodeEngine;
import com.termux.app.iqcode.api.ModelCatalogClient;
import com.termux.app.iqcode.api.ModelDescriptor;
import com.termux.app.iqcode.agents.AgentDefinition;
import com.termux.app.iqcode.agents.AgentTask;
import com.termux.app.iqcode.core.PermissionGate;
import com.termux.app.iqcode.core.PlanApprovalGate;
import com.termux.app.iqcode.core.QuestionGate;
import com.termux.app.iqcode.model.ApiProfile;
import com.termux.app.iqcode.model.PlanWorkflowState;
import com.termux.app.iqcode.model.SessionConfig;
import com.termux.app.iqcode.model.ToolCall;
import com.termux.app.iqcode.model.ToolExecutionResult;
import com.termux.app.iqcode.storage.ApiSettingsStore;
import com.termux.app.iqcode.storage.McpConfigStore;
import com.termux.app.iqcode.storage.SessionStore;
import com.termux.app.iqcode.tasks.TaskStore;
import com.termux.app.iqcode.termux.TermuxShellExecutor;
import com.termux.app.iqcode.tools.AndroidIntentBridge;
import com.termux.app.iqcode.tools.ToolRegistry;
import com.termux.app.iqcode.tools.WebSearchTool;
import com.termux.app.iqcode.tools.WebFetchTool;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 元境 Desktop-inspired Android workspace.
 * Agent/API logic is Java-native; shell/build execution uses the embedded Termux prefix.
 */
public final class MainActivity extends Activity {
    private static volatile java.lang.ref.WeakReference<MainActivity> overlayHost = new java.lang.ref.WeakReference<>(null);
    public static boolean submitOverlayPrompt(String text){MainActivity a=overlayHost.get();if(a==null||text==null||text.trim().isEmpty())return false;a.runOnUiThread(()->{if(a.prompt!=null){a.prompt.setText(text.trim());a.sendPrompt();}});return true;}
    private static final String UI_PREFS = "iqge_ui_preferences";
    private static final String UI_THEME_KEY = "ui_theme";
    private static final String THEME_CLASSIC = "classic";
    private static final String THEME_NEON = "neon-purple";
    private static final String THEME_DAY = "day";
    private static final String THEME_NIGHT = "night";
    private static final String PALETTE_BG_KEY = "palette_background";
    private static final String PALETTE_SURFACE_KEY = "palette_surface";
    private static final String PALETTE_TEXT_KEY = "palette_text";
    private static final String PALETTE_ACCENT_KEY = "palette_accent";
    private static final String PALETTE_GREEN_KEY = "palette_green";
    private static final String PALETTE_RED_KEY = "palette_red";

    // Runtime palette. The original warm dark theme remains the default; the optional purple-blue
    // theme is derived from the user's navy dashboard reference and can be changed without restart.
    private String uiTheme = THEME_NIGHT;
    private float uiFontScale = 1f;
    private float uiCornerScale = 1f;
    private boolean uiGradientAccent;
    private boolean neonTheme;
    private boolean lightTheme;
    private int BG = Color.rgb(8, 11, 17);
    private int TOP = Color.rgb(12, 16, 23);
    private int SIDEBAR = Color.rgb(6, 9, 14);
    private int SURFACE = Color.rgb(15, 19, 28);
    private int SURFACE_2 = Color.rgb(20, 26, 37);
    private int SURFACE_3 = Color.rgb(26, 33, 46);
    private int BORDER = Color.rgb(45, 55, 76);
    private int BORDER_SOFT = Color.rgb(36, 45, 62);
    private int TEXT = Color.rgb(228, 235, 248);
    private int MUTED = Color.rgb(148, 161, 182);
    private int MUTED_2 = Color.rgb(103, 116, 138);
    private int ACCENT = Color.rgb(56, 189, 248);
    private int GREEN = Color.rgb(80, 200, 120);
    private int RED = Color.rgb(248, 113, 113);
    private int USER_BG = Color.rgb(22, 30, 46);
    private int TERMINAL_BG = Color.rgb(5, 7, 11);
    private View canvasRoot;
    private WindowManager overlayWindowManager;
    private FrameLayout overlayShell;
    private FrameLayout overlayContent;
    private WindowManager.LayoutParams overlayLayout;
    private boolean overlayFramePosted;
    private boolean overlayScalePending;
    private long overlayLastCommitAt;
    private final Runnable overlayFrameUpdate=()->{
        overlayFramePosted=false;
        if(overlayShell==null||overlayLayout==null||overlayWindowManager==null)return;
        long now=android.os.SystemClock.uptimeMillis();
        if(now-overlayLastCommitAt<24L){deferOverlayFrame();return;}
        try{overlayWindowManager.updateViewLayout(overlayShell,overlayLayout);overlayLastCommitAt=now;if(overlayScalePending){overlayScalePending=false;scaleWorkspaceOverlay();}}catch(Exception ignored){}
    };
    private void deferOverlayFrame(){if(overlayShell!=null&&!overlayFramePosted){overlayFramePosted=true;overlayShell.postOnAnimation(overlayFrameUpdate);}}

    private static final int VIEW_CHAT = 0;
    private static final int VIEW_TERMINAL = 2;
    private static final int VIEW_FILES = 3;
    private static final int REQ_PICK_IMAGE = 4102;
    private static final int REQ_IMPORT_THEME = 4103;

    private static final class ChatItem {
        static final int USER = 1, ASSISTANT = 2, TOOL = 3, RESULT = 4, ERROR = 5;
        final int type;
        final String title;
        final StringBuilder body;
        final StringBuilder thinking = new StringBuilder();
        final List<String> processSteps = new ArrayList<>();
        String persistedBody = "";
        final List<Attachment> images = new ArrayList<>();
        final StringBuilder result = new StringBuilder();
        final StringBuilder diff = new StringBuilder();
        final StringBuilder liveOutput = new StringBuilder();
        boolean completed;
        boolean expanded;
        boolean resultError;
        boolean awaitingPermission;
        boolean progressRenderPosted;
        boolean restoredUnfinished;
        boolean liveLastWasStderr;
        int exitCode;
        int diffAddedLines;
        int diffDeletedLines;
        int liveStdoutChars;
        int liveStderrChars;
        long elapsedMs;
        TextView runningView;
        TextView liveOutputView;
        long contextTokens = -1;
        long inputTokens = -1;
        long outputTokens = -1;
        int contextWindowTokens;
        boolean contextApiMeasured;
        long startedAt = android.os.SystemClock.elapsedRealtime();
        View renderedView;
        /** Diff/output block of a tool card; kept so expanding and collapsing can animate it. */
        LinearLayout detailsView;
        LinearLayout thinkingHost;
        TextView thinkingLabel;
        TextView thinkingView;
        boolean thinkingExpanded;
        String toolId = "";
        String messageId = "";
        String messageContentHash = "";
        int legacyRowIndex = -1;
        boolean humanMessage;
        ChatItem(int type, String title, String body) {
            this.type = type; this.title = title; this.body = new StringBuilder(body == null ? "" : body);
            this.persistedBody = this.body.toString();
        }
    }

    private static final class CollapsedToolActivity {
        final long batchId;
        final ToolActivityGrouper.GroupPlan plan;
        final List<ChatItem> members = new ArrayList<>();
        boolean batchCompleted;
        boolean expanded;
        LinearLayout renderedView;
        LinearLayout childrenHost;

        CollapsedToolActivity(long batchId, ToolActivityGrouper.GroupPlan plan) {
            this.batchId = batchId;
            this.plan = plan;
        }
    }

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final List<ChatItem> transcript = new ArrayList<>();
    /** Cards whose details were just opened; they are built collapsed and then animated open. */
    private ChatItem pendingDetailsExpand;
    private CollapsedToolActivity pendingActivityExpand;
    private final Map<String, ChatItem> liveToolItems = new ConcurrentHashMap<>();
    private final List<AgentProgressView.Step> agentSteps = new ArrayList<>();
    private final Map<String, AgentProgressView.Step> agentStepsByCall = new java.util.HashMap<>();
    private final Map<String, CollapsedToolActivity> collapsedToolsById = new java.util.HashMap<>();
    private final Map<Long, List<CollapsedToolActivity>> collapsedToolsByBatch = new java.util.HashMap<>();
    private long nextRestoredToolBatchId = -1L;
    private final StringBuilder terminalBuffer = new StringBuilder();

    private ApiSettingsStore settingsStore;
    private McpConfigStore mcpStore;
    private SessionConfig config;
    private final ModelCatalogClient modelCatalogClient=new ModelCatalogClient();
    private final AtomicLong modelCatalogGeneration=new AtomicLong();
    private Future<?> modelCatalogFuture;
    private RuntimeInstaller runtime;
    private IQCodeEngine engine;
    private final Map<String, SessionRuntime> sessionRuntimes = new ConcurrentHashMap<>();
    private final Map<String, TextView> sessionRowLabels = new ConcurrentHashMap<>();
    private final List<SessionStore.SessionSummary> sidebarSessionSummaries = new ArrayList<>();
    private String sidebarSessionQuery = "";
    private boolean sidebarSessionsExpanded = false;
    private EditText sessionSearchBox;
    private LinearLayout sidebarSessionHost;
    private final Map<String, SessionStore.SessionSummary> sessionRowSummaries = new ConcurrentHashMap<>();
    private volatile SessionRuntime activeRuntime;
    private volatile long activeRuntimeGeneration;
    /** 会话打开/重载的令牌：后台加载完成时若令牌已过期（用户又切了会话），直接丢弃结果。 */
    private long sessionLoadToken;
    private long chatTreeGeneration;
    private int sidebarGeneration;
    private boolean composerBusyState;

    private final class SessionRuntime {
        final IQCodeEngine engine;
        final StringBuffer liveAssistant = new StringBuffer();
        final StringBuilder liveThinking = new StringBuilder();
        volatile File file;
        volatile String phase = "idle";
        volatile String detail = "";
        volatile boolean busy;
        volatile String boundProfileId="";
        volatile String pendingProfileId="";
        volatile SessionConfig nextConfig;
        volatile PlanWorkflowState planState = PlanWorkflowState.idle();
        volatile TaskStore.Snapshot taskSnapshot;
        volatile long lastEventAt = System.currentTimeMillis();
        boolean reasoningRenderPosted;
        long reasoningGeneration;
        SessionRuntime() {
            nextConfig=config==null?new SessionConfig():config.copy();
            boundProfileId=nextConfig.profileId;
            engine = new IQCodeEngine(MainActivity.this, new RuntimeListener(this));
        }
    }

    private final class RuntimeListener implements IQCodeEngine.Listener {
        final SessionRuntime rt;
        RuntimeListener(SessionRuntime rt) { this.rt = rt; }
        @Override public void onSessionStarted(SessionConfig c){ handleSessionStarted(rt,c); }
        @Override public void onTextDelta(String text){ handleTextDelta(rt,text); }
        @Override public void onThinkingDelta(String thinking){ handleThinkingDelta(rt,thinking); }
        @Override public void onToolBatchStarted(IQCodeEngine.ToolBatch batch){ handleToolBatchStarted(rt,batch); }
        @Override public void onToolUse(ToolCall call){ handleToolUse(rt,call); }
        @Override public void onToolBatchCompleted(IQCodeEngine.ToolBatch batch){ handleToolBatchCompleted(rt,batch); }
        @Override public void onToolProgress(ToolCall call,String chunk,boolean stderr,long elapsedMs){ handleToolProgress(rt,call,chunk,stderr,elapsedMs); }
        @Override public void onToolResult(ToolCall call,ToolExecutionResult result){ handleToolResult(rt,call,result); }
        @Override public void onPermissionRequest(PermissionGate.PermissionRequest request){ handlePermissionRequest(rt,request); }
        @Override public void onQuestionRequest(QuestionGate.QuestionRequest request){ handleQuestionRequest(rt,request); }
        @Override public void onPlanStateChanged(PlanWorkflowState state){ handlePlanStateChanged(rt,state); }
        @Override public void onPlanApprovalRequest(PlanApprovalGate.ApprovalRequest request){ handlePlanApprovalRequest(rt,request); }
        @Override public void onTasksChanged(TaskStore.Snapshot snapshot){ handleTasksChanged(rt,snapshot); }
        @Override public void onProjectDirectoryChanged(String projectDirectory){ handleProjectDirectoryChanged(rt,projectDirectory); }
        @Override public void onResponseInterruptedBySteering(){ handleResponseInterruptedBySteering(rt); }
        @Override public void onResponseRetry(){ handleResponseRetry(rt); }
        @Override public void onQueuedPromptApplied(){ handleQueuedPromptApplied(rt); }
        @Override public void onUsage(long inputTokens,long outputTokens){ handleUsage(rt,inputTokens,outputTokens); }
        @Override public void onStatus(String status){ handleStatus(rt,status); }
        @Override public void onTurnComplete(String stopReason){ handleTurnComplete(rt,stopReason); }
        @Override public void onError(String message,Throwable error){ handleError(rt,message,error); }
    }

    private boolean wide;
    /** 宽屏才有那条 228dp 侧栏；中等宽度只并排两块内容，把横向空间全留给它们。 */
    private boolean layoutSidebarVisible;
    private int currentView = VIEW_CHAT;
    /** 工作区交叉淡入期间临时关掉各面板自己的入场动画，避免两层动画叠加。 */
    private boolean suppressPaneAnimation;
    /** 切走工作区时暂存输入框草稿与光标位置（renderChat 会重建 composer，不带过去等于清空）。 */
    private String composerDraftCarry = "";
    private int composerCaretCarry;
    private boolean composerFocusCarry;
    /** 顶栏分段控件的滑块：在 对话 / 终端 之间滑过去，而不是直接跳。 */
    private View compactTabIndicator;
    private int compactTabIndex = -1;
    private FrameLayout primaryHost;
    private FrameLayout secondaryHost;
    private LinearLayout chatMessages;
    private LinearLayout chatPage;
    private LinearLayout currentConversationHost;
    private View chatImeSpacer;
    private ScrollView chatScroll;
    private EditText prompt;
    private TextView statusText;
    private TextView tokenText;
    private TextView deviceStatusText;
    private String lastDeviceStatusValue;
    private boolean deviceStatusReceiverRegistered;
    private final Runnable deviceClockTicker=new Runnable(){@Override public void run(){updateDeviceStatus();uiHandler.postDelayed(this,1000L);}};
    private final BroadcastReceiver deviceBatteryReceiver=new BroadcastReceiver(){@Override public void onReceive(android.content.Context context,Intent intent){updateDeviceStatus(intent);}};
    private TermuxTerminalPane terminalPane;
    private ListView filesList;
    private TextView filesPath;
    private EditText fileFilterBox;
    private LinearLayout fileFilterHost;
    private boolean fileFilterRefreshPosted;
    private TextView filesCount;
    private File browserDir;
    private ChatItem streamingItem;
    private TextView streamingView;
    private LinearLayout streamingBodyHost;
    private final List<TextView> viewTabs = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final UiCanvasStore.ChangeListener canvasChangeListener=(document,preview)->uiHandler.post(()->refreshUiCanvas(document,preview));
    private boolean toolElapsedTickerPosted;
    private final Runnable toolElapsedTicker = this::refreshToolElapsed;
    private final Runnable streamingFrameRunnable = this::renderStreamingFrame;
    private final Runnable streamingDeltaFlushRunnable = this::flushStreamingDeltas;
    private final Object streamingDeltaLock = new Object();
    private final StringBuilder pendingStreamingDelta = new StringBuilder();
    private SessionRuntime pendingStreamingRuntime;
    private long pendingStreamingGeneration;
    private boolean streamingDeltaFlushPosted;
    private boolean chatAutoFollow = true;
    private boolean chatUserTouching;
    private android.animation.ValueAnimator chatScrollAnimator;
    private boolean streamRenderPosted;
    private boolean chatRenderPosted;
    private final Runnable chatRenderRunnable = () -> {
        chatRenderPosted = false;
        if (primaryHost != null && chatMessages != null) renderChat(primaryHost, false);
    };
    private long streamingRenderTreeGeneration;
    private int streamingVisibleChars;
    private boolean streamingFinalizePending;
    private boolean autoFollowScrollPosted;
    private ScrollView autoFollowScrollTarget;
    private LinearLayout autoFollowMessagesTarget;
    private long autoFollowScrollTreeGeneration;
    private android.view.ViewTreeObserver.OnPreDrawListener autoFollowScrollListener;
    private int transcriptRenderLimit = 120;
    private Boolean lowRamDeviceCache;
    /** Per-tool-card diff rendering is capped so one huge edit cannot stall a frame. */
    private static final int TOOL_DIFF_MAX_CHARS = 60_000;
    /** Message bodies rendered as plain text (user bubble, live assistant stream) share the same cap. */
    private static final int MESSAGE_TEXT_MAX_CHARS = 60_000;
    /** 流式回复逐帧推进：帧间隔（短回复 ~50fps）与每帧吃掉积压的比例（指数缓出）。 */
    private static final long STREAM_FRAME_MS = 20L;
    private static final float STREAM_REVEAL_EASE = 0.34f;
    /**
     * Decoded history images are kept for the newest replies only. Each entry holds the full
     * decoded bitmap bytes for the lifetime of the transcript, so an unbounded count let a long
     * screenshot-heavy session exhaust the app heap just by being opened.
     */
    private static final int MAX_RESTORED_IMAGES = 24;
    private TextView contextChip;
    private TextView composerModeChip;
    private TextView composerEffortChip;
    private TextView composerModelChip;
    private HorizontalScrollView composerPickerScroll;
    private LinearLayout composerPickerRow;
    private int composerPickerKind;
    private static final int PICKER_NONE = 0;
    private static final int PICKER_PERMISSION = 1;
    private static final int PICKER_EFFORT = 2;
    private LinearLayout slashPalette;
    private ScrollView slashPaletteScroll;
    private LinearLayout attachmentStrip;
    private boolean suppressPromptWatcher;
    private Dialog mobileSidebarDialog;
    private final List<Attachment> pendingAttachments = new ArrayList<>();
    private boolean rebuildingTranscript;
    private TextView workingIndicator;
    private AgentProgressView agentProgressView;
    private LinearLayout workingBar;
    private TextView workingBarText;
    private TextView workingBarTask;
    private TextView workingBarChevron;
    private LinearLayout workingTaskList;
    private boolean workingTaskExpanded;
    private boolean workingTaskCollapsePosted;
    private final Runnable workingTaskCollapse=()->{
        workingTaskCollapsePosted=false;
        if(workingTaskExpanded&&workingTaskList!=null){
            workingTaskExpanded=false;
            UiMotion.collapseBar(workingTaskList,null);
        }
    };
    private RollingLogView rollingLog;
    private int workingPulseGeneration;
    private String workingStatus = "正在思考…";
    private TextView sendButton;
    private View themeToggleAnchor;
    private int themeRevealGeneration;
    private Animator themeRevealAnimator;
    private ViewGroup themeRevealHost;
    private View themeRevealUnderlay;
    private Bitmap themeRevealSnapshot;
    private TextView stopButton;
    private View composerHost;
    private LinearLayout chatBottomHost;
    private View keyboardRoot;
    private android.view.ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private boolean keyboardVisible;
    private int keyboardOffset;
    private int keyboardImeAnimationDepth;
    private int pendingKeyboardInset=-1;
    private FrameLayout filesWorkspaceHost;
    private EditText fileEditor;
    private File fileEditorFile;
    private TextView fileEditorStatus;
    private boolean fileEditorHighlighting;
    private Runnable fileHighlightRunnable;
    private boolean automaticRuntimeInstallRunning;
    private boolean waitingForAllFilesAccess;
    private boolean waitingForOverlayPermission;
    private static final int SHIZUKU_REQUEST_CODE = 4271;
    private TextView shizukuRow;
    private TextView shizukuDetailRow;
    private boolean waitingForShizukuPermission;
    /** Shizuku 授权结果：只更新设置页那一行，收到结果就停止“等待中”。 */
    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = (requestCode, grantResult) -> {
        if (requestCode != SHIZUKU_REQUEST_CODE) return;
        waitingForShizukuPermission = false;
        refreshShizukuRow();
        toast(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED ? "Shizuku 已授权（shell 身份）" : "Shizuku 授权被拒绝");
    };
    private boolean storagePermissionDialogVisible;
    private boolean androidIntegrationDialogVisible;
    private static final String SPONSOR_PREFS = "iqge_sponsor";
    private static final String SPONSOR_URL = "https://afdian.com/a/IQ_ge";
    /** GitHub 更新检查：改成你自己的公开仓库 owner/repo。发布 Release 时打 tag 如 v0.31.0。 */
    private static final String GITHUB_OWNER = "zihan8023";
    private static final String GITHUB_REPO = "yuanjing";
    private static final String GITHUB_API_LATEST = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    /** Contributors shown in the about page: {display name, GitHub user name}. */
    private static final String[][] CONTRIBUTORS = {{"蜘蛛", "zhizhu0002"}};
    private static boolean sponsorPromptTickDone;
    private boolean apiSetupPending;

    private static final String[] EFFORT_UI_VALUES = {"none", "auto", "low", "medium", "high", "max"};
    private static final String[] EFFORT_UI_LABELS = {"关闭", "自动", "低", "中等", "高", "最大"};

    private static final class Attachment {
        static final int TEXT = 1, IMAGE = 2;
        final int kind;
        final String label;
        final String body;
        final String mimeType;
        final byte[] bytes;
        Attachment(String label, String body) { this.kind = TEXT; this.label = label; this.body = body == null ? "" : body; this.mimeType = null; this.bytes = null; }
        Attachment(String label, String mimeType, byte[] bytes) { this.kind = IMAGE; this.label = label; this.body = ""; this.mimeType = mimeType == null ? "image/jpeg" : mimeType; this.bytes = bytes; }
    }

    private static final class SlashCommand {
        final String name, hint;
        SlashCommand(String name, String hint) { this.name = name; this.hint = hint; }
    }

    private static final SlashCommand[] SLASH_COMMANDS = new SlashCommand[]{
        new SlashCommand("/help", "查看全部 元境 Android 指令"),
        new SlashCommand("/compact", "模型语义压缩；可追加摘要侧重点"),
        new SlashCommand("/context", "查看或设置上下文窗口，例如 /context 1m"),
        new SlashCommand("/clear", "清空当前对话并开始新会话"),
        new SlashCommand("/new", "创建一个新的已保存会话"),
        new SlashCommand("/resume", "恢复本地已保存的历史会话"),
        new SlashCommand("/model", "切换当前模型"),
        new SlashCommand("/effort", "设置推理强度"),
        new SlashCommand("/permissions", "设置工具调用权限模式"),
        new SlashCommand("/root", "Agent Root：on / off / check"),
        new SlashCommand("/shizuku", "Agent Shizuku：on / off / status"),
        new SlashCommand("/keepalive", "强制后台保活：on / off / check"),
        new SlashCommand("/mcp", "配置和管理 MCP 服务器"),
        new SlashCommand("/web", "联网搜索设置；也可直接输入 /web 搜索词"),
        new SlashCommand("/terminal", "打开内置 Termux 终端"),
        new SlashCommand("/sandbox", "打开 IQ 沙箱；Agent 可安装、运行和调试虚拟 APK"),
        new SlashCommand("/opencode", "在内置 Termux 里运行官方 OpenCode Desktop，画面走 X11"),
        new SlashCommand("/files", "打开项目文件与代码编辑器"),
        new SlashCommand("/doctor", "检查 Termux 运行环境"),
        new SlashCommand("/repair", "修复中断的 apt/dpkg 状态"),
        new SlashCommand("/skills", "为下一条任务附加本地 Skill"),
        new SlashCommand("/status", "查看模型、项目、上下文、运行时和会话状态"),
        new SlashCommand("/stats", "查看当前会话与运行状态"),
        new SlashCommand("/usage", "查看当前上下文使用情况"),
        new SlashCommand("/copy", "复制最近一条 IQ 回复"),
        new SlashCommand("/plan", "进入计划模式；/plan off 退出"),
        new SlashCommand("/config", "打开 元境 设置"),
        new SlashCommand("/canvas", "打开运行时 UI 画布自定义"),
        new SlashCommand("/memory", "打开项目或用户 IQ.md 记忆"),
        new SlashCommand("/init", "让 IQ 初始化或完善项目 IQ.md"),
        new SlashCommand("/tasks", "查看 Android Agent 的任务与会话文件"),
        new SlashCommand("/agents", "管理内置、项目、用户和正在运行的子 Agent"),
        new SlashCommand("/cancel", "停止当前正在执行的 Agent 回合"),
        new SlashCommand("/runtime", "打开内置 Termux 运行环境控制")
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Shizuku 授权结果可能在系统弹窗关闭后才回来（此时 Activity 处于暂停态），所以在这里常驻注册。
        try { rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermissionListener); } catch (Throwable ignored) { }
        overlayHost=new java.lang.ref.WeakReference<>(this);
        UiCanvasStore.addChangeListener(canvasChangeListener);
        applyImmersiveFullscreen();
        applyUiPalette(getSharedPreferences(UI_PREFS,MODE_PRIVATE).getString(UI_THEME_KEY,THEME_CLASSIC));
        applyUiCanvasPalette();
        applySystemBarAppearance();

        File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        home.mkdirs(); new File(home, "projects").mkdirs();
        settingsStore = new ApiSettingsStore(this);
        mcpStore = new McpConfigStore();
        config = settingsStore.load();
        transcriptRenderLimit = config.transcriptWindowMessages;
        if (config.forcedKeepAliveEnabled) KeepAliveService.start(this, config.rootExecutionEnabled);
        if (config.projectDirectory == null || config.projectDirectory.trim().isEmpty()) config.projectDirectory = home.getAbsolutePath();
        if (!new File(config.projectDirectory).isDirectory()) config.projectDirectory = home.getAbsolutePath();
        runtime = new RuntimeInstaller(this);
        io.execute(()->{try{runtime.repairIfInstalled();}catch(Throwable ignored){}});
        SessionRuntime initial = createFreshRuntime();
        activateRuntime(initial, false);

        applyLayoutTier(computeLayoutTier());
        View app = buildApp();
        canvasRoot = app;
        setContentView(app);
        UiCanvasController.apply(app, UiCanvasStore.load(this));
        installKeyboardMotion();
        bindContentKeyboardInsets(app);
        UiMotion.appIn(app);
        renderChat(primaryHost);
        refreshChrome();
        // First-launch onboarding is deliberately posted after the first frame so the workspace
        // appears immediately. Missing Termux runtime is installed automatically and storage
        // access is requested with a Chinese explanation instead of leaving the terminal half-set-up.
        uiHandler.postDelayed(this::runFirstLaunchSetup, 450);
        uiHandler.postDelayed(this::restoreLastSessionAsync, 80);
        uiHandler.postDelayed(this::tickSponsorPrompt, 1600);
    }

    private void restoreLastSessionAsync() {
        final SessionRuntime placeholder=activeRuntime;
        final File lastSession=settingsStore.getLastSessionFile();
        if(lastSession==null)return;
        io.execute(()->{
            try{
                File migrated=SessionStore.migrateLegacySessions(lastSession);
                if(migrated==null)migrated=lastSession;
                if(!migrated.isFile())return;
                if(!migrated.getAbsolutePath().equals(lastSession.getAbsolutePath()))settingsStore.setLastSessionFile(migrated);
                final File file=migrated;
                final SessionRuntime restored=createRuntimeForExisting(file);
                // 长会话整文件解析留在后台线程：主线程只做 ChatItem 恢复，避免启动即 ANR。
                final JSONArray restoredRows=SessionStore.readRows(file);
                ui(()->{
                    if(activeRuntime!=placeholder||placeholder.engine.isBusy()||isFinishing()||isDestroyed()){
                        restored.engine.shutdown();
                        return;
                    }
                    activateRuntime(restored,restoredRows);
                    placeholder.engine.shutdown();
                    currentView=VIEW_CHAT;
                    rebuildWorkspaceForTheme();
                });
            }catch(Exception ignored){}
        });
    }

    private void configureFloatingWindow(){
        if(android.os.Build.VERSION.SDK_INT>=26)getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        WindowManager.LayoutParams p=getWindow().getAttributes();p.width=(int)(getResources().getDisplayMetrics().widthPixels*.88f);p.height=(int)(getResources().getDisplayMetrics().heightPixels*.78f);p.gravity=Gravity.RIGHT|Gravity.BOTTOM;p.x=18;p.y=90;getWindow().setAttributes(p);getWindow().setDimAmount(0f);
    }

    private void applyImmersiveFullscreen(){
        // Keep the workspace genuinely edge-to-edge: status/navigation bars are hidden so the
        // system clock cannot create a separate strip above the 元境 header.
        // Keep the window fixed; the IME inset path below moves only the composer and reserves its area.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        View decor=getWindow().getDecorView();
        int systemUi=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        decor.setSystemUiVisibility(systemUi);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller=decor.getWindowInsetsController();
            if(controller!=null)controller.hide(android.view.WindowInsets.Type.statusBars()|android.view.WindowInsets.Type.navigationBars());
        }
    }

    private void applySystemBarAppearance(){
        getWindow().setStatusBarColor(TOP);
        getWindow().setNavigationBarColor(BG);
        if(android.os.Build.VERSION.SDK_INT>=23){
            View decor=getWindow().getDecorView();
            int systemUi=decor.getSystemUiVisibility()&~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if(android.os.Build.VERSION.SDK_INT>=26)systemUi&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if(lightTheme)systemUi|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if(lightTheme&&android.os.Build.VERSION.SDK_INT>=26)systemUi|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(systemUi);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus){
            applyImmersiveFullscreen();
            getWindow().getDecorView().postDelayed(this::applyImmersiveFullscreen,180L);
        }
    }

    private void installKeyboardMotion(){
        if(keyboardRoot!=null)return;
        keyboardRoot=getWindow().getDecorView();
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            keyboardRoot.setOnApplyWindowInsetsListener((v,insets)->{
                pendingKeyboardInset=insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                if(keyboardImeAnimationDepth==0)applyKeyboardOffset(pendingKeyboardInset,false);
                return insets;
            });
            keyboardRoot.setWindowInsetsAnimationCallback(new android.view.WindowInsetsAnimation.Callback(android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE){
                @Override public void onPrepare(android.view.WindowInsetsAnimation animation){
                    super.onPrepare(animation);
                    if((animation.getTypeMask()&android.view.WindowInsets.Type.ime())!=0)keyboardImeAnimationDepth++;
                }
                @Override public android.view.WindowInsets onProgress(android.view.WindowInsets insets,List<android.view.WindowInsetsAnimation> runningAnimations){
                    boolean imeAnimating=false;
                    for(android.view.WindowInsetsAnimation animation:runningAnimations)if((animation.getTypeMask()&android.view.WindowInsets.Type.ime())!=0){imeAnimating=true;break;}
                    if(imeAnimating){
                        pendingKeyboardInset=insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                        applyKeyboardOffset(pendingKeyboardInset,false);
                    }
                    return insets;
                }
                @Override public void onEnd(android.view.WindowInsetsAnimation animation){
                    super.onEnd(animation);
                    if((animation.getTypeMask()&android.view.WindowInsets.Type.ime())!=0){
                        keyboardImeAnimationDepth=Math.max(0,keyboardImeAnimationDepth-1);
                        if(keyboardImeAnimationDepth==0&&pendingKeyboardInset>=0)applyKeyboardOffset(pendingKeyboardInset,false);
                    }
                }
            });
        }
        keyboardLayoutListener=()->{
            View target=composerHost;
            if(target==null||target.getParent()==null)return;
            // IME 动画期间由动画回调给实时高度：这里量到的值会先跳到终点，插进来就会先弹一下再退回去。
            if(android.os.Build.VERSION.SDK_INT>=30&&keyboardImeAnimationDepth>0)return;
            android.graphics.Rect frame=new android.graphics.Rect();
            keyboardRoot.getWindowVisibleDisplayFrame(frame);
            int obscured=Math.max(0,keyboardRoot.getRootView().getHeight()-frame.bottom);
            int detected=pendingKeyboardInset>=dp(120)?pendingKeyboardInset:obscured;
            if(android.os.Build.VERSION.SDK_INT<30||detected>=dp(120)||keyboardVisible)applyKeyboardOffset(detected,true);
        };
        keyboardRoot.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
        if(android.os.Build.VERSION.SDK_INT>=30)keyboardRoot.requestApplyInsets();
    }

    private void bindContentKeyboardInsets(View content){
        if(android.os.Build.VERSION.SDK_INT<30||content==null)return;
        content.setOnApplyWindowInsetsListener((v,insets)->{
            pendingKeyboardInset=insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
            if(keyboardImeAnimationDepth==0)applyKeyboardOffset(pendingKeyboardInset,false);
            return insets;
        });
        content.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            if(!content.isShown())return;
            if(android.os.Build.VERSION.SDK_INT>=30&&keyboardImeAnimationDepth>0)return;
            android.graphics.Rect frame=new android.graphics.Rect();
            content.getWindowVisibleDisplayFrame(frame);
            int[] location=new int[2];content.getLocationOnScreen(location);
            int contentBottom=location[1]+content.getHeight();
            int visibleObscured=Math.max(0,contentBottom-frame.bottom);
            int detected=pendingKeyboardInset>=dp(120)?pendingKeyboardInset:visibleObscured;
            if(detected>=dp(120)||keyboardVisible)applyKeyboardOffset(detected,true);
        });
        content.requestApplyInsets();
    }

    private void applyKeyboardOffset(int rawOffset,boolean animate){
        if(android.os.Build.VERSION.SDK_INT>=30&&keyboardImeAnimationDepth>0){
            // IME 正在弹出/收起：这一段只认动画给的实时高度，也不做 120dp 阈值判断。
            // 如果这里按"是否算弹出"去取值，开头会先跳到终点、动画过半又被拉回来，看起来就是抽一下再抖。
            int animated=Math.max(0,rawOffset);
            if(animated==keyboardOffset)return;
            keyboardVisible=animated>0;
            keyboardOffset=animated;
            applyChatKeyboardOffset(animated);
            moveKeyboardView(chatBottomHost,0,false);
            if(terminalPane!=null)terminalPane.setKeyboardOffset(animated,false);
            return;
        }
        boolean showing=rawOffset>=dp(120);
        int targetOffset=showing?rawOffset:0;
        int offsetDelta=Math.abs(targetOffset-keyboardOffset);
        // The window remains ADJUST_NOTHING so the manual composer translation and chat viewport
        // reservation stay in one coordinate system.
        if(showing==keyboardVisible&&offsetDelta<dp(8))return;
        if(showing==keyboardVisible&&targetOffset==keyboardOffset)return;
        keyboardVisible=showing;
        animateKeyboardOffsetTo(targetOffset,animate);
    }

    private android.animation.ValueAnimator keyboardAnimator;

    /**
     * 键盘避让补间：旧系统一次性给出全量高度时，把聊天视口预留/终端内边距一起
     * 滑到目标值，输入框和会话区跟着键盘的节奏走，而不是硬跳一下。
     */
    private void animateKeyboardOffsetTo(int target,boolean animate){
        if(keyboardAnimator!=null){keyboardAnimator.cancel();keyboardAnimator=null;}
        int from=keyboardOffset;
        long duration=animate&&from!=target?UiMotion.durationFor(this,190L):0L;
        if(duration==0L){
            keyboardOffset=target;
            applyChatKeyboardOffset(target);
            moveKeyboardView(chatBottomHost,0,false);
            if(terminalPane!=null)terminalPane.setKeyboardOffset(target,false);
            return;
        }
        android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofInt(from,target);
        animator.setDuration(duration).setInterpolator(UiMotion.standard());
        animator.addUpdateListener(a->{int value=(Integer)a.getAnimatedValue();keyboardOffset=value;applyChatKeyboardOffset(value);moveKeyboardView(chatBottomHost,0,false);if(terminalPane!=null)terminalPane.setKeyboardOffset(value,false);});
        animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(keyboardAnimator==a)keyboardAnimator=null;}});
        keyboardAnimator=animator;
        animator.start();
    }

    private void moveKeyboardView(View target,int offset,boolean animate){
        if(target==null||target.getParent()==null)return;
        if(animate)UiMotion.slideToY(target,-offset,180L);
        else{target.animate().cancel();target.setTranslationY(-offset);}
    }

    private void applyChatKeyboardOffset(int offset){
        if(chatScroll==null)return;
        if(chatScroll.getPaddingBottom()!=0||chatScroll.getClipToPadding()){
            chatScroll.setClipToPadding(false);
            chatScroll.setPadding(0,0,0,0);
        }
        if(chatPage!=null){
            int bottom=Math.max(0,offset);
            if(chatPage.getPaddingBottom()!=bottom)chatPage.setPadding(chatPage.getPaddingLeft(),chatPage.getPaddingTop(),chatPage.getPaddingRight(),bottom);
        }
        if(chatMessages!=null){
            // 状态行是浮在输入框上方的（不占布局），末尾留出它默认那一条的高度，浮出来时只盖空白不盖正文。
            int tail=dp(44);
            if(chatImeSpacer!=null){
                ViewGroup.LayoutParams spacerParams=chatImeSpacer.getLayoutParams();
                int spacerHeight=tail;
                if(spacerParams!=null&&spacerParams.height!=spacerHeight){spacerParams.height=spacerHeight;chatImeSpacer.setLayoutParams(spacerParams);}
            }else if(chatMessages.getPaddingBottom()!=tail){
                chatMessages.setPadding(chatMessages.getPaddingLeft(),chatMessages.getPaddingTop(),chatMessages.getPaddingRight(),tail);
            }
        }
        if(chatAutoFollow)scheduleAutoFollowScroll();
    }

    private static final int LAYOUT_NARROW = 0;
    private static final int LAYOUT_SPLIT = 1;
    private static final int LAYOUT_WIDE = 2;

    /**
     * 一套分档，全屏窗口和悬浮窗共用，只看“这块区域有多大”：
     * {@code 宽>=720dp 且 高>=600dp} 双栏+侧栏；{@code 宽>=560dp 且 高>=480dp} 对话 | 终端(文件) 并排；
     * 其余一律窄屏单栏。高度不够（小屏手机横屏只有 ~360-400dp 高）时即使很宽也退回单栏，
     * 否则分栏 + 侧栏会把竖向空间压得没法操作。平板横屏高度充足，仍走桌面布局。
     */
    private static int layoutTierForWidth(float widthDp) {
        return layoutTierForSize(widthDp, Float.MAX_VALUE);
    }

    private static int layoutTierForSize(float widthDp, float heightDp) {
        if (widthDp >= 720f && heightDp >= 600f) return LAYOUT_WIDE;
        if (widthDp >= 560f && heightDp >= 480f) return LAYOUT_SPLIT;
        return LAYOUT_NARROW;
    }

    private void applyLayoutTier(int tier) {
        wide = tier != LAYOUT_NARROW;
        layoutSidebarVisible = tier == LAYOUT_WIDE;
    }

    private int computeLayoutTier() {
        return layoutTierForSize(
            getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density,
            getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density);
    }

    /** 悬浮窗只有屏幕的一部分，所以按窗口自己的宽度分档，不看全屏宽度。 */
    private void applyOverlayLayoutTier(boolean portrait) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        float widthDp = (portrait ? screenW * .82f : screenW * .94f) / getResources().getDisplayMetrics().density;
        float heightDp = (portrait ? screenH * .94f : screenH * .82f) / getResources().getDisplayMetrics().density;
        applyLayoutTier(layoutTierForSize(widthDp, heightDp));
    }

    /** 悬浮窗入场姿态跟随当前屏幕方向：竖着拿手机却弹出一个横条，或者反过来，都很别扭。 */
    private boolean isLandscapeNow() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applyUiPalette(String requested) {
        // 非插件主题一律回到内置几何：圆角、字号、渐变由插件独占控制。
        uiFontScale=1f;uiCornerScale=1f;uiGradientAccent=false;
        lightTheme=THEME_DAY.equals(requested);
        neonTheme=false;
        uiTheme=lightTheme?THEME_DAY:THEME_NIGHT;
        if(lightTheme){
            // 冷色科技亮版：纸白微蓝底、墨蓝正文与电光蓝强调色，和夜间主题同一套视觉语言。
            BG=Color.rgb(246,248,253);TOP=Color.rgb(251,252,255);SIDEBAR=Color.rgb(238,241,249);
            SURFACE=Color.rgb(252,253,255);SURFACE_2=Color.rgb(242,245,252);SURFACE_3=Color.rgb(235,239,248);
            BORDER=Color.rgb(208,216,232);BORDER_SOFT=Color.rgb(222,228,240);
            TEXT=Color.rgb(28,34,48);MUTED=Color.rgb(96,107,128);MUTED_2=Color.rgb(116,127,147);
            ACCENT=Color.rgb(37,99,235);GREEN=Color.rgb(22,137,96);RED=Color.rgb(203,64,74);
            USER_BG=Color.rgb(231,237,250);TERMINAL_BG=Color.rgb(239,242,248);
        }else{
            // AI 科技风夜间主题：深蓝黑底 + 电光天蓝强调色
            BG = Color.rgb(8, 11, 17);TOP = Color.rgb(12, 16, 23);SIDEBAR = Color.rgb(6, 9, 14);
            SURFACE = Color.rgb(15, 19, 28);SURFACE_2 = Color.rgb(20, 26, 37);SURFACE_3 = Color.rgb(26, 33, 46);
            BORDER = Color.rgb(45, 55, 76);BORDER_SOFT = Color.rgb(36, 45, 62);TEXT = Color.rgb(228, 235, 248);
            MUTED = Color.rgb(148, 161, 182);MUTED_2 = Color.rgb(103, 116, 138);ACCENT = Color.rgb(56, 189, 248);
            GREEN = Color.rgb(80, 200, 120);RED = Color.rgb(248, 113, 113);USER_BG = Color.rgb(22, 30, 46);TERMINAL_BG = Color.rgb(5, 7, 11);
        }
        if(requested!=null&&requested.startsWith("plugin:"))applyPluginTheme(requested.substring(7).trim());
        else if("custom".equals(requested))applyStoredCustomPalette();
    }

    /** 主题插件：以夜间/白天为基线，JSON 里声明的颜色逐项覆盖，缺的按层级派生。 */
    private void applyPluginTheme(String id) {
        ThemePluginStore.Theme theme=ThemePluginStore.load(this,id);
        if(theme==null)return;
        lightTheme=!theme.dark;
        neonTheme=false;
        uiTheme="plugin:"+id;
        org.json.JSONObject c=theme.colors;
        boolean dark=theme.dark;
        // 基线：声明了哪些就覆盖哪些，未声明的按同样的派生规则补齐。
        BG=c.optInt("bg",dark?Color.rgb(18,17,15):Color.rgb(247,246,242));
        TEXT=c.optInt("text",dark?Color.rgb(240,236,229):Color.rgb(40,39,35));
        SURFACE=c.optInt("surface",mixColor(BG,TEXT,dark?.055f:.035f));
        ACCENT=c.optInt("accent",dark?Color.rgb(126,178,136):Color.rgb(55,105,75));
        GREEN=c.optInt("green",dark?Color.rgb(126,178,136):Color.rgb(28,137,91));
        RED=c.optInt("red",dark?Color.rgb(214,120,111):Color.rgb(194,65,75));
        TOP=c.optInt("top",mixColor(BG,Color.WHITE,dark?.02f:.03f));
        SIDEBAR=c.optInt("sidebar",mixColor(BG,Color.BLACK,dark?.18f:.03f));
        SURFACE_2=c.optInt("surface2",mixColor(SURFACE,TEXT,.06f));
        SURFACE_3=c.optInt("surface3",mixColor(SURFACE,TEXT,.12f));
        BORDER=c.optInt("border",mixColor(SURFACE,TEXT,.20f));
        BORDER_SOFT=c.optInt("borderSoft",mixColor(SURFACE,TEXT,.12f));
        MUTED=c.optInt("muted",mixColor(TEXT,BG,.38f));
        MUTED_2=c.optInt("muted2",mixColor(TEXT,BG,.58f));
        USER_BG=c.optInt("userBg",mixColor(SURFACE,ACCENT,.16f));
        TERMINAL_BG=c.optInt("terminalBg",mixColor(BG,Color.BLACK,.38f));
        // 全局 UI 旋钮：圆角缩放、字号缩放、强调色渐变——由插件的 style 段控制。
        org.json.JSONObject style=theme.style;
        uiFontScale=(float)style.optDouble("fontScale",1.0);
        uiCornerScale=(float)style.optDouble("cornerScale",1.0);
        uiGradientAccent=style.optBoolean("gradientAccent",false);
        if(Float.isNaN(uiFontScale)||uiFontScale<=0f)uiFontScale=1f;
        if(Float.isNaN(uiCornerScale)||uiCornerScale<=0f)uiCornerScale=1f;
    }

    private void applyStoredCustomPalette() {
        android.content.SharedPreferences prefs=getSharedPreferences(UI_PREFS,MODE_PRIVATE);
        if(!prefs.contains(PALETTE_BG_KEY))return;
        applyCustomPalette(
            prefs.getInt(PALETTE_BG_KEY,BG),prefs.getInt(PALETTE_SURFACE_KEY,SURFACE),
            prefs.getInt(PALETTE_TEXT_KEY,TEXT),prefs.getInt(PALETTE_ACCENT_KEY,ACCENT),
            prefs.getInt(PALETTE_GREEN_KEY,GREEN),prefs.getInt(PALETTE_RED_KEY,RED));
    }

    private void applyCustomPalette(int background,int surface,int textColor,int accent,int green,int red) {
        neonTheme=false;uiTheme="custom";
        BG=background;SURFACE=surface;TEXT=textColor;ACCENT=accent;GREEN=green;RED=red;
        TOP=mixColor(background,surface,.35f);SIDEBAR=mixColor(background,Color.BLACK,.18f);
        SURFACE_2=mixColor(surface,textColor,.06f);SURFACE_3=mixColor(surface,textColor,.12f);
        BORDER=mixColor(surface,textColor,.20f);BORDER_SOFT=mixColor(surface,textColor,.12f);
        MUTED=mixColor(textColor,background,.38f);MUTED_2=mixColor(textColor,background,.58f);
        USER_BG=mixColor(surface,accent,.16f);TERMINAL_BG=mixColor(background,Color.BLACK,.38f);
    }

    private int mixColor(int from,int to,float amount) {
        float t=Math.max(0f,Math.min(1f,amount));
        return Color.rgb(Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*t),Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*t),Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*t));
    }

    private int parsePaletteColor(EditText input) {
        String value=input.getText().toString().trim();
        if(!value.startsWith("#"))value="#"+value;
        if(value.length()!=7)throw new IllegalArgumentException("颜色必须使用 #RRGGBB 格式");
        return Color.parseColor(value);
    }

    private String paletteHex(int color) { return String.format(Locale.US,"#%06X",color&0xFFFFFF); }

    private void toggleDayNightTheme(){
        finishThemeRevealNow();
        boolean animated=UiMotion.enabled(this);
        int[] origin=animated?themeToggleCentre():null;
        Bitmap snapshot=animated?snapshotWorkspace():null;
        String next=lightTheme?THEME_NIGHT:THEME_DAY;saveAndApplyUiTheme(next,origin,snapshot);
        toast(lightTheme?"已切换为白天模式":"已切换为夜间模式");
    }

    /** Finishes an interrupted theme transition and releases its frozen underlay. */
    private void finishThemeRevealNow(){
        themeRevealGeneration++;
        Animator animator=themeRevealAnimator;themeRevealAnimator=null;
        if(animator!=null){
            animator.removeAllListeners();
            // Finish at full opacity before removing the previous workspace.
            try{animator.end();}catch(Throwable ignored){ }
        }
        ViewGroup host=themeRevealHost;themeRevealHost=null;
        View underlay=themeRevealUnderlay;themeRevealUnderlay=null;
        if(host!=null&&underlay!=null&&underlay.getParent()==host)host.removeView(underlay);
        if(canvasRoot!=null){canvasRoot.setAlpha(1f);canvasRoot.setVisibility(View.VISIBLE);}
        releaseThemeSnapshot();
    }

    /** Frees the frozen workspace once no sweep is drawing it; it is a full-screen ARGB bitmap. */
    private void releaseThemeSnapshot(){
        Bitmap shot=themeRevealSnapshot;themeRevealSnapshot=null;
        if(shot!=null&&!shot.isRecycled())shot.recycle();
    }

    /** Window-space centre of the theme toggle; the day/night transition grows out of it. */
    private int[] themeToggleCentre(){
        View anchor=themeToggleAnchor;
        if(anchor==null||anchor.getWidth()==0)return null;
        int[] location=new int[2];anchor.getLocationInWindow(location);
        location[0]+=anchor.getWidth()/2;location[1]+=anchor.getHeight()/2;
        return location;
    }

    private void saveAndApplyCustomPalette(int background,int surface,int textColor,int accent,int green,int red) {
        getSharedPreferences(UI_PREFS,MODE_PRIVATE).edit().putString(UI_THEME_KEY,"custom")
            .putInt(PALETTE_BG_KEY,background).putInt(PALETTE_SURFACE_KEY,surface)
            .putInt(PALETTE_TEXT_KEY,textColor).putInt(PALETTE_ACCENT_KEY,accent)
            .putInt(PALETTE_GREEN_KEY,green).putInt(PALETTE_RED_KEY,red).apply();
        applyCustomPalette(background,surface,textColor,accent,green,red);
        // The terminal pane picks up the new theme when renderTerminal re-parents it; re-theming the old
        // pane here made it change colour before the sweep had covered it.
        rebuildWorkspaceForTheme();
    }

    private void refreshUiCanvas(org.json.JSONObject document, boolean preview) {
        if (isFinishing() || canvasRoot == null) return;
        boolean paletteChanged=document.optJSONObject("palette")!=null&&!isDefaultUiCanvasPalette(document.optJSONObject("palette"));
        applyUiCanvasPalette(document);
        if (paletteChanged) rebuildWorkspaceForTheme();
        if (canvasRoot != null) UiCanvasController.apply(canvasRoot, document);
        if (canvasRoot != null) canvasRoot.invalidate();
        if (preview) toast("UI 画布预览已应用");
    }

    private boolean isDefaultUiCanvasPalette(org.json.JSONObject palette) {
        if(palette==null)return true;
        try {
            org.json.JSONObject defaults=UiCanvasStore.defaults().optJSONObject("palette");
            if(defaults==null)return false;
            for(String key:new String[]{"background","surface","text","muted","accent","green"})
                if(palette.optInt(key,Integer.MIN_VALUE)!=defaults.optInt(key,Integer.MIN_VALUE))return false;
            return true;
        } catch(Throwable ignored) { return false; }
    }

    private void applyUiCanvasPalette(org.json.JSONObject document) {
        try {
            org.json.JSONObject palette=document==null?null:document.optJSONObject("palette");
            if(palette==null||isDefaultUiCanvasPalette(palette))return;
            BG=palette.optInt("background",BG);SURFACE=palette.optInt("surface",SURFACE);TEXT=palette.optInt("text",TEXT);
            MUTED=palette.optInt("muted",MUTED);ACCENT=palette.optInt("accent",ACCENT);GREEN=palette.optInt("green",GREEN);
        } catch(Throwable ignored) {}
    }

    private void applyUiCanvasPalette() {
        try { applyUiCanvasPalette(UiCanvasStore.load(this)); } catch(Throwable ignored) {}
    }

    private void saveAndApplyUiTheme(String selectedTheme) { saveAndApplyUiTheme(selectedTheme,null,null); }

    private void saveAndApplyUiTheme(String selectedTheme,int[] revealOrigin,Bitmap revealSnapshot) {
        String normalized;
        if(selectedTheme!=null&&selectedTheme.startsWith("plugin:")&&ThemePluginStore.load(this,selectedTheme.substring(7).trim())!=null)
            normalized=selectedTheme;
        else normalized=THEME_DAY.equals(selectedTheme)?THEME_DAY:THEME_NIGHT;
        if(normalized.equals(uiTheme))return;
        getSharedPreferences(UI_PREFS,MODE_PRIVATE).edit().putString(UI_THEME_KEY,normalized).remove(PALETTE_BG_KEY).remove(PALETTE_SURFACE_KEY).remove(PALETTE_TEXT_KEY).remove(PALETTE_ACCENT_KEY).remove(PALETTE_GREEN_KEY).remove(PALETTE_RED_KEY).apply();
        applyUiPalette(normalized);
        // The terminal pane picks up the new theme when renderTerminal re-parents it; re-theming the old
        // pane here made it change colour before the sweep had covered it.
        rebuildWorkspaceForTheme(revealOrigin,revealSnapshot);
    }

    private void rebuildWorkspaceForTheme() { rebuildWorkspaceForTheme(null,null); }

    private void rebuildWorkspaceForTheme(int[] revealOrigin,Bitmap revealSnapshot) {
        finishThemeRevealNow();
        // The incoming freeze is only registered after the old sweep released its own bitmap.
        themeRevealSnapshot = revealSnapshot;
        int keepView = currentView;
        boolean keepFollow = chatAutoFollow;
        String draft = prompt == null ? "" : prompt.getText().toString();
        if (mobileSidebarDialog != null) { mobileSidebarDialog.dismiss(); mobileSidebarDialog = null; }
        applyLayoutTier(computeLayoutTier());
        viewTabs.clear();
        View previous = canvasRoot;
        View app = buildApp();
        canvasRoot = app;
        ViewGroup revealHost = revealOrigin != null && previous != null && previous.getParent() instanceof ViewGroup ? (ViewGroup) previous.getParent() : null;
        View revealUnderlay = null;
        if (revealHost != null) {
            // The old workspace stays underneath until the new theme has swept across it; a frozen copy is
            // used when we have one so nothing it owns (terminal pane, live updates) can change mid-sweep.
            // The new tree stays invisible until the reveal clip is in place, so nothing flashes first.
            if (revealSnapshot != null) {
                revealUnderlay = snapshotLayer(revealSnapshot);
                revealHost.addView(revealUnderlay, new FrameLayout.LayoutParams(-1, -1));
                revealHost.removeView(previous);
                previous = null;
            } else {
                revealUnderlay = previous;
            }
            revealHost.addView(app, new FrameLayout.LayoutParams(-1, -1));
            app.setVisibility(View.INVISIBLE);
        } else {
            releaseThemeSnapshot();
            applySystemBarAppearance();
            setContentView(app);
            UiMotion.themeChanged(app);
        }
        UiCanvasController.apply(app, UiCanvasStore.load(this));
        currentView = keepView;
        chatAutoFollow = keepFollow;
        renderChat(primaryHost, false);
        if (prompt != null && !draft.isEmpty()) { prompt.setText(draft); prompt.setSelection(prompt.length()); }
        if (wide) {
            if (keepView == VIEW_TERMINAL) renderTerminal(secondaryHost);
            else if (keepView == VIEW_FILES) renderFiles(secondaryHost);
        } else if (keepView != VIEW_CHAT) {
            focusWorkspace(keepView);
        }
        refreshChrome();
        if (revealHost != null) startThemeReveal(app, revealHost, revealUnderlay, revealOrigin);
    }

    /** Freezes the workspace about to be replaced so the day/night sweep runs over the old UI instead of a blank screen. */
    private Bitmap snapshotWorkspace(){
        View root=canvasRoot;
        if(root==null||root.getWidth()==0||root.getHeight()==0)return null;
        try{
            Bitmap shot=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(shot));
            return shot;
        }catch(Throwable ignored){ return null; }
    }

    private View snapshotLayer(Bitmap shot){
        ImageView layer=new ImageView(this);
        layer.setScaleType(ImageView.ScaleType.FIT_XY);
        layer.setImageBitmap(shot);
        return layer;
    }

    /** Crossfades the rebuilt theme over the frozen workspace without a moving reveal edge. */
    private void startThemeReveal(View app,ViewGroup host,View underlay,int[] origin){
        final int generation=themeRevealGeneration;
        themeRevealHost=host;themeRevealUnderlay=underlay;
        app.postOnAnimation(() -> {
            if(generation!=themeRevealGeneration){app.setVisibility(View.VISIBLE);endThemeReveal(generation,host,underlay);return;}
            if(isFinishing()||canvasRoot!=app||app.getWidth()==0){app.setVisibility(View.VISIBLE);endThemeReveal(generation,host,underlay);return;}
            applySystemBarAppearance();
            long duration=UiMotion.durationFor(this,240L);
            if(duration==0L){app.setVisibility(View.VISIBLE);endThemeReveal(generation,host,underlay);return;}
            ValueAnimator reveal=ValueAnimator.ofFloat(0f,1f);
            reveal.setDuration(duration);reveal.setInterpolator(UiMotion.standard());
            reveal.addUpdateListener(a->app.setAlpha((Float)a.getAnimatedValue()));
            reveal.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator animation){app.setAlpha(1f);endThemeReveal(generation,host,underlay);}
            });
            themeRevealAnimator=reveal;
            app.setAlpha(0f);app.setVisibility(View.VISIBLE);
            reveal.start();
        });
    }

    /** Drops the workspace the sweep has just covered. */
    private void endThemeReveal(int generation,ViewGroup host,View underlay){
        if(generation!=themeRevealGeneration)return;
        themeRevealAnimator=null;themeRevealHost=null;themeRevealUnderlay=null;
        if(underlay!=null&&host!=null&&underlay.getParent()==host)host.removeView(underlay);
        if(canvasRoot!=null){canvasRoot.setAlpha(1f);canvasRoot.setVisibility(View.VISIBLE);}
        releaseThemeSnapshot();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int keepView = currentView;
        boolean keepFollow = chatAutoFollow;
        String draft = prompt == null ? "" : prompt.getText().toString();
        applyLayoutTier(computeLayoutTier());
        viewTabs.clear();
        View app = buildApp();
        canvasRoot = app;
        setContentView(app);
        UiCanvasController.apply(app, UiCanvasStore.load(this));
        UiMotion.appIn(app);
        currentView = keepView;
        renderChat(primaryHost, false);
        if (prompt != null && !draft.isEmpty()) { prompt.setText(draft); prompt.setSelection(prompt.length()); }
        if (wide) {
            if (keepView == VIEW_TERMINAL) renderTerminal(secondaryHost);
            else if (keepView == VIEW_FILES) renderFiles(secondaryHost);
        } else if (keepView != VIEW_CHAT) {
            focusWorkspace(keepView);
        }
        refreshChrome();
    }

    private View buildApp() {
        LinearLayout root = vbox();
        root.setTag("workspace.root");
        root.setBackgroundColor(BG);
        View globalBar=buildGlobalBar(); globalBar.setTag("workspace.global_bar");
        root.addView(globalBar, lp(-1, dp(56)));
        root.addView(dividerHorizontal(), lp(-1, 1));

        LinearLayout body = hbox(); body.setTag("workspace.body");
        body.setBackgroundColor(BG);
        if (layoutSidebarVisible) {
            View sidebar=buildSidebar(); sidebar.setTag("workspace.sidebar");
            body.addView(sidebar, lp(dp(228), -1));
            body.addView(dividerVertical(), lp(1, -1));
        }
        body.addView(buildSessionArea(), new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        UiMotion.bindInteractive(root);
        return root;
    }

    private void updateDeviceStatus(){
        Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null)updateDeviceStatus(battery);
        else if(deviceStatusText!=null)deviceStatusText.setText(new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new java.util.Date()));
    }

    private void updateDeviceStatus(Intent battery){
        if(deviceStatusText==null)return;
        String clock=new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new java.util.Date());
        int level=battery==null?-1:battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1);
        int scale=battery==null?-1:battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,-1);
        int percent=level>=0&&scale>0?Math.round(level*100f/scale):-1;
        int plugged=battery==null?0:battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0);
        boolean charging=plugged!=0||battery!=null&&battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1)==android.os.BatteryManager.BATTERY_STATUS_CHARGING;
        String value=clock+(percent>=0?"  "+percent+"%":"")+(charging?"  充电":percent>=0&&percent<=20?"  低电":"");
        // The 1 Hz ticker usually re-renders the identical string; a setText per second
        // forces a measure/layout pass on the status bar for nothing.
        if(value.equals(lastDeviceStatusValue))return;
        lastDeviceStatusValue=value;
        deviceStatusText.setText(value);deviceStatusText.setTextColor(charging?GREEN:percent>=0&&percent<=20?RED:MUTED_2);
    }

    private void startDeviceStatus(){
        if(!deviceStatusReceiverRegistered){registerReceiver(deviceBatteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));deviceStatusReceiverRegistered=true;}
        uiHandler.removeCallbacks(deviceClockTicker);updateDeviceStatus();uiHandler.post(deviceClockTicker);
    }

    private void stopDeviceStatus(){uiHandler.removeCallbacks(deviceClockTicker);if(deviceStatusReceiverRegistered){unregisterReceiver(deviceBatteryReceiver);deviceStatusReceiverRegistered=false;}}

    private void toggleFloatingOverlay(){
        if(overlayShell!=null){restoreWorkspaceFromOverlay();return;}
        if(!android.provider.Settings.canDrawOverlays(this)){
            waitingForOverlayPermission=true;
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
            return;
        }
        showWorkspaceOverlay(!isLandscapeNow());
    }

    public static boolean reopenWorkspaceOverlay(){
        MainActivity a=overlayHost.get();
        if(a==null)return false;
        a.runOnUiThread(()->a.showWorkspaceOverlay(!a.isLandscapeNow()));
        return true;
    }

    private void showWorkspaceOverlay(boolean portrait){
        if(canvasRoot==null||overlayShell!=null)return;
        stopService(new Intent(this,FloatingOverlayService.class));
        ViewGroup parent=(ViewGroup)canvasRoot.getParent();if(parent!=null)parent.removeView(canvasRoot);
        FrameLayout placeholder=new FrameLayout(this);placeholder.setBackgroundColor(BG);setContentView(placeholder);
        overlayShell=new FrameLayout(this);overlayShell.setBackground(round(Color.rgb(12,12,16),20,BORDER,1));overlayShell.setClipChildren(true);overlayShell.setClipToOutline(true);overlayShell.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        // Build a compact workspace that measures against the actual overlay window bounds.
        applyOverlayLayoutTier(portrait);viewTabs.clear();canvasRoot=buildApp();bindContentKeyboardInsets(canvasRoot);overlayContent=new FrameLayout(this);overlayContent.setTag("floating.workspace.content");overlayContent.addView(canvasRoot,new FrameLayout.LayoutParams(-1,-1));overlayShell.addView(overlayContent,new FrameLayout.LayoutParams(-1,-1));
        renderChat(primaryHost,false);
        LinearLayout controls=hbox();controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(dp(6),0,dp(4),0);controls.setBackground(round(SURFACE_2,14,BORDER,1));
        TextView drag=text("元境",10.5f,TEXT);drag.setGravity(Gravity.CENTER_VERTICAL);controls.addView(drag,new LinearLayout.LayoutParams(0,dp(32),1));
        TextView vertical=iconButton(R.drawable.ic_overlay_portrait);vertical.setContentDescription("竖屏悬浮布局");vertical.setOnClickListener(v->resizeWorkspaceOverlay(true));controls.addView(vertical,lp(dp(34),dp(32)));
        TextView horizontal=iconButton(R.drawable.ic_overlay_landscape);horizontal.setContentDescription("横屏悬浮布局");horizontal.setOnClickListener(v->resizeWorkspaceOverlay(false));controls.addView(horizontal,lp(dp(34),dp(32)));
        TextView restore=iconButton(R.drawable.ic_overlay_restore);restore.setContentDescription("退出悬浮并返回完整界面");restore.setOnClickListener(v->restoreWorkspaceFromOverlay());controls.addView(restore,lp(dp(34),dp(32)));
        TextView minimize=iconButton(R.drawable.ic_minus);minimize.setContentDescription("收缩为悬浮球");minimize.setOnClickListener(v->minimizeWorkspaceOverlay());controls.addView(minimize,lp(dp(34),dp(32)));
        overlayShell.addView(controls,new FrameLayout.LayoutParams(-1,dp(34),Gravity.TOP));drag.setOnTouchListener(new OverlayDragTouch());
        TextView resize=iconButton(R.drawable.ic_resize);resize.setContentDescription("拖动调整悬浮窗大小");FrameLayout.LayoutParams resizeLp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.RIGHT|Gravity.BOTTOM);overlayShell.addView(resize,resizeLp);resize.setOnTouchListener(new OverlayResizeTouch());
        int screenW=getResources().getDisplayMetrics().widthPixels,screenH=getResources().getDisplayMetrics().heightPixels;
        overlayWindowManager=(WindowManager)getSystemService(WINDOW_SERVICE);int overlayFlags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;overlayLayout=new WindowManager.LayoutParams(portrait?(int)(screenW*.82f):(int)(screenW*.94f),portrait?(int)(screenH*.72f):(int)(screenH*.48f),android.os.Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,overlayFlags,PixelFormat.TRANSLUCENT);overlayLayout.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;overlayLayout.gravity=Gravity.TOP|Gravity.LEFT;overlayLayout.x=dp(12);overlayLayout.y=dp(56);overlayWindowManager.addView(overlayShell,overlayLayout);scaleWorkspaceOverlay();overlayShell.setFocusableInTouchMode(true);overlayShell.requestFocus();uiHandler.postDelayed(()->{if(overlayShell!=null){overlayShell.requestFocus();moveTaskToBack(true);}},250);
    }

    private void resizeWorkspaceOverlay(boolean portrait){
        if(overlayLayout==null)return;
        int screenW=getResources().getDisplayMetrics().widthPixels,screenH=getResources().getDisplayMetrics().heightPixels;
        int keepView=currentView;String draft=prompt==null?"":prompt.getText().toString();boolean keepFollow=chatAutoFollow;
        if(overlayContent!=null){overlayContent.removeView(canvasRoot);applyOverlayLayoutTier(portrait);viewTabs.clear();canvasRoot=buildApp();bindContentKeyboardInsets(canvasRoot);overlayContent.addView(canvasRoot,new FrameLayout.LayoutParams(-1,-1));UiCanvasController.apply(canvasRoot,UiCanvasStore.load(this));currentView=keepView;chatAutoFollow=keepFollow;renderChat(primaryHost,false);if(prompt!=null&&!draft.isEmpty()){prompt.setText(draft);prompt.setSelection(prompt.length());}if(wide){if(keepView==VIEW_TERMINAL)renderTerminal(secondaryHost);else if(keepView==VIEW_FILES)renderFiles(secondaryHost);}else if(keepView!=VIEW_CHAT)focusWorkspace(keepView);refreshChrome();}
        overlayLayout.width=portrait?(int)(screenW*.82f):(int)(screenW*.94f);overlayLayout.height=portrait?(int)(screenH*.72f):(int)(screenH*.48f);overlayWindowManager.updateViewLayout(overlayShell,overlayLayout);scaleWorkspaceOverlay();
    }
    private void scaleWorkspaceOverlay(){
        // The overlay owns a compact workspace tree; resizing is handled by normal measurement.
        if(canvasRoot==null)return;
        canvasRoot.setPivotX(0);canvasRoot.setPivotY(0);canvasRoot.setScaleX(1f);canvasRoot.setScaleY(1f);canvasRoot.setTranslationX(0);canvasRoot.setTranslationY(0);
    }
    private void minimizeWorkspaceOverlay(){cleanupOverlayResizeGhost();detachWorkspaceOverlay(false);startService(new Intent(this,FloatingOverlayService.class).putExtra("ball_only",true));}
    private void restoreWorkspaceFromOverlay(){cleanupOverlayResizeGhost();stopService(new Intent(this,FloatingOverlayService.class));detachWorkspaceOverlay(true);}
    private void cleanupOverlayResizeGhost(){
        if(overlayResizeGhost!=null){
            overlayResizeGhost.removeCallbacks(overlayResizeFrameUpdate);overlayResizeFramePosted=false;
            try{overlayWindowManager.removeView(overlayResizeGhost);}catch(Exception ignored){}
        }
        overlayResizeGhost=null;overlayResizeGhostLp=null;
    }
    private void detachWorkspaceOverlay(boolean restore){if(overlayShell==null)return;try{overlayWindowManager.removeView(overlayShell);}catch(Exception ignored){}ViewGroup p=(ViewGroup)canvasRoot.getParent();if(p!=null)p.removeView(canvasRoot);canvasRoot.setScaleX(1);canvasRoot.setScaleY(1);canvasRoot.setTranslationX(0);canvasRoot.setTranslationY(0);canvasRoot.setLayerType(View.LAYER_TYPE_NONE,null);if(overlayShell!=null)overlayShell.removeCallbacks(overlayFrameUpdate);overlayFramePosted=false;overlayScalePending=false;overlayShell=null;overlayContent=null;overlayLayout=null;if(restore){rebuildWorkspaceAfterOverlay();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}}

    /**
     * The overlay builds its own workspace against the floating window's width ({@link
     * #applyOverlayLayoutTier}), so that tree cannot be reused for the full-screen activity: bar
     * elements that only exist with a sidebar (the model name, the device status, the 92dp context
     * chip) were then laid out into a narrower window and the top bar came back squeezed. Rebuild
     * for the real window instead.
     */
    private void rebuildWorkspaceAfterOverlay(){
        int keepView=currentView;boolean keepFollow=chatAutoFollow;String draft=prompt==null?"":prompt.getText().toString();
        if(mobileSidebarDialog!=null){mobileSidebarDialog.dismiss();mobileSidebarDialog=null;}
        applyLayoutTier(computeLayoutTier());viewTabs.clear();
        View app=buildApp();canvasRoot=app;setContentView(app);
        bindContentKeyboardInsets(app);
        UiCanvasController.apply(app,UiCanvasStore.load(this));
        currentView=keepView;chatAutoFollow=keepFollow;
        renderChat(primaryHost,false);
        if(prompt!=null&&!draft.isEmpty()){prompt.setText(draft);prompt.setSelection(prompt.length());}
        if(wide){if(keepView==VIEW_TERMINAL)renderTerminal(secondaryHost);else if(keepView==VIEW_FILES)renderFiles(secondaryHost);}
        else if(keepView!=VIEW_CHAT)focusWorkspace(keepView);
        refreshChrome();
    }

    private void scheduleOverlayFrame(boolean scale){if(overlayShell==null)return;overlayScalePending|=scale;if(overlayFramePosted)return;overlayFramePosted=true;overlayShell.postOnAnimation(overlayFrameUpdate);}
    private void commitOverlayFrame(boolean scale){if(overlayShell==null)return;overlayShell.removeCallbacks(overlayFrameUpdate);overlayFramePosted=false;overlayScalePending|=scale;overlayLastCommitAt=0L;overlayFrameUpdate.run();}
    private final class OverlayDragTouch implements View.OnTouchListener{
        float sx,sy;int px,py,lastX,lastY;
        public boolean onTouch(View v,MotionEvent e){
            if(overlayLayout==null||overlayShell==null)return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                sx=e.getRawX();sy=e.getRawY();px=overlayLayout.x;py=overlayLayout.y;lastX=px;lastY=py;
                overlayShell.setTranslationX(0);overlayShell.setTranslationY(0);overlayLastCommitAt=0L;return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                int x=px+(int)(e.getRawX()-sx),y=py+(int)(e.getRawY()-sy);
                if(x!=lastX||y!=lastY){lastX=x;lastY=y;overlayLayout.x=x;overlayLayout.y=y;scheduleOverlayFrame(false);}
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
                overlayLayout.x=lastX;overlayLayout.y=lastY;commitOverlayFrame(false);return true;
            }
            return false;
        }
    }
    private View overlayResizeGhost;
    private WindowManager.LayoutParams overlayResizeGhostLp;
    private boolean overlayResizeFramePosted;
    private final Runnable overlayResizeFrameUpdate=()->{
        overlayResizeFramePosted=false;
        if(overlayResizeGhost==null||overlayResizeGhostLp==null||overlayWindowManager==null)return;
        try{overlayWindowManager.updateViewLayout(overlayResizeGhost,overlayResizeGhostLp);}catch(Exception ignored){}
    };
    private void scheduleOverlayResizeFrame(){
        if(overlayResizeGhost==null||overlayResizeFramePosted)return;
        overlayResizeFramePosted=true;overlayResizeGhost.postOnAnimation(overlayResizeFrameUpdate);
    }
    private final class OverlayResizeTouch implements View.OnTouchListener{
        float sx,sy;int pw,ph;
        public boolean onTouch(View v,MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                sx=e.getRawX();sy=e.getRawY();pw=overlayLayout.width;ph=overlayLayout.height;
                overlayResizeGhost=new FrameLayout(MainActivity.this);
                overlayResizeGhost.setBackground(round(Color.argb(46,255,255,255),20,ACCENT,3));
                int t=android.os.Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
                overlayResizeGhostLp=new WindowManager.LayoutParams(pw,ph,t,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
                overlayResizeGhostLp.gravity=Gravity.TOP|Gravity.LEFT;overlayResizeGhostLp.x=overlayLayout.x;overlayResizeGhostLp.y=overlayLayout.y;
                overlayWindowManager.addView(overlayResizeGhost,overlayResizeGhostLp);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                int w=Math.max(dp(300),pw+(int)(e.getRawX()-sx));int h=Math.max(dp(340),ph+(int)(e.getRawY()-sy));
                if(overlayResizeGhostLp!=null){overlayResizeGhostLp.width=w;overlayResizeGhostLp.height=h;scheduleOverlayResizeFrame();}
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
                int w=Math.max(dp(300),pw+(int)(e.getRawX()-sx));int h=Math.max(dp(340),ph+(int)(e.getRawY()-sy));
                cleanupOverlayResizeGhost();
                overlayLayout.width=w;overlayLayout.height=h;overlayWindowManager.updateViewLayout(overlayShell,overlayLayout);
                return true;
            }
            return false;
        }
    }




    private View buildGlobalBar() {
        LinearLayout bar = hbox();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), 0, dp(10), 0);
        bar.setBackground(themedTopBackground());

        if (!layoutSidebarVisible) {
            TextView menu = iconButton(R.drawable.ic_menu);
            menu.setContentDescription("打开项目与会话列表");
            menu.setOnClickListener(v -> showMobileSidebar());
            bar.addView(menu, lp(dp(38), dp(44)));
            if (!wide) {
                // 窄屏没有独立的工作区条：对话/终端 在这里占一小段分段控件，省下整条 44dp。
                bar.addView(buildCompactViewTabs(), lp(-2, dp(40)));
            }
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        } else {
            TextView brand = text("元境",14, TEXT); brand.setTypeface(Typeface.DEFAULT_BOLD);
            brand.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(brand, new LinearLayout.LayoutParams(0, dp(40), 1));
        }

        tokenText = text("", 11, MUTED_2); tokenText.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        if (layoutSidebarVisible) bar.addView(tokenText, lp(dp(170), dp(40)));
        contextChip = text("ctx",10.5f, MUTED);
        contextChip.setGravity(Gravity.CENTER);
        contextChip.setSingleLine(true);
        contextChip.setPadding(dp(6),0,dp(6),0);
        contextChip.setContentDescription("查看当前上下文占用");
        contextChip.setBackground(round(Color.TRANSPARENT,8, Color.TRANSPARENT, 0));
        contextChip.setOnClickListener(v -> showContextInfo());
        bar.addView(contextChip, lp(dp(wide ? 92 : 74), dp(36)));
        deviceStatusText=text("",10.5f,MUTED_2);deviceStatusText.setGravity(Gravity.CENTER);deviceStatusText.setSingleLine(true);deviceStatusText.setEllipsize(android.text.TextUtils.TruncateAt.END);lastDeviceStatusValue=null;
        deviceStatusText.setVisibility(layoutSidebarVisible ? View.VISIBLE : View.GONE);
        bar.addView(deviceStatusText,lp(layoutSidebarVisible?dp(118):0,dp(34)));
        TextView themeButton=iconButton(lightTheme?R.drawable.ic_moon:R.drawable.ic_sun);themeToggleAnchor=themeButton;themeButton.setContentDescription(lightTheme?"切换到夜间模式":"切换到白天模式");themeButton.setOnClickListener(v->toggleDayNightTheme());bar.addView(themeButton,lp(dp(wide?40:36),dp(40)));
        TextView floatButton = iconButton(R.drawable.ic_floating); floatButton.setContentDescription("开启系统悬浮球"); floatButton.setOnClickListener(v -> toggleFloatingOverlay()); floatButton.setVisibility(View.GONE); bar.addView(floatButton, lp(dp(40), dp(40)));
        TextView gear = iconButton(R.drawable.ic_settings); gear.setContentDescription("打开设置"); gear.setOnClickListener(v -> showSettings());
        bar.addView(gear, lp(dp(wide?40:36), dp(40)));
        return bar;
    }

    /** 侧边栏统一小图标按钮：同一圆角容器 + 同一图标色，与主按钮、导航芯片组成一套视觉语言。 */
    private TextView sidebarIconChip(int res,String desc,int sizeDp){
        TextView t=iconOnly(res,sizeDp,MUTED_2);t.setContentDescription(desc);
        t.setBackground(round(Color.TRANSPARENT,8,Color.TRANSPARENT,0));return t;
    }

    private LinearLayout.LayoutParams chipParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(32),dp(32));p.leftMargin=dp(4);return p;}

    private LinearLayout.LayoutParams miniChipParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(28),dp(24));p.leftMargin=dp(3);return p;}

    private LinearLayout.LayoutParams navChipParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1);p.leftMargin=dp(2);p.rightMargin=dp(2);return p;}

    /** 底部导航芯片：内容居中、统一底色的等宽三格。 */
    private TextView sidebarNavChip(String label){
        TextView t=sidebarAction(label,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(2),0,dp(2),0);
        t.setBackground(round(Color.TRANSPARENT,8,Color.TRANSPARENT,0));return t;
    }

    private View buildSidebar() {
        sessionRowLabels.clear();
        sessionRowSummaries.clear();
        sidebarSessionQuery = "";
        sidebarSessionsExpanded = false;
        LinearLayout side = vbox(); side.setBackground(sidebarBackground()); side.setClipToOutline(true); side.setPadding(dp(12), dp(12), dp(12), dp(8));
        // 顶栏一行：主按钮"新会话" + 三枚同规格小芯片（搜索/设置/关于），不再散排。
        LinearLayout topRow=hbox();topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView newSession = sidebarAction("＋  新会话", true);
        newSession.setOnClickListener(v -> newSession());
        topRow.addView(newSession,new LinearLayout.LayoutParams(0,dp(36),1));
        // 每枚芯片独立 LayoutParams（不共享实例，避免后续任何 margin 改动互相串扰）。
        TextView searchToggle=sidebarIconChip(R.drawable.ic_search,"搜索本项目会话",14);searchToggle.setOnClickListener(v->{
            boolean show=sessionSearchBox.getVisibility()!=View.VISIBLE;
            sessionSearchBox.setVisibility(show?View.VISIBLE:View.GONE);
            if(show)sessionSearchBox.requestFocus();else{sessionSearchBox.setText("");}
        });topRow.addView(searchToggle,chipParams());
        TextView settingsIcon=sidebarIconChip(R.drawable.ic_settings,"设置",14);settingsIcon.setOnClickListener(v->showSettings());topRow.addView(settingsIcon,chipParams());
        TextView aboutIcon=sidebarIconChip(R.drawable.ic_info,"查看版本、作者与 GitHub",14);aboutIcon.setOnClickListener(v->showAboutDialog());topRow.addView(aboutIcon,chipParams());
        side.addView(topRow,lp(-1,dp(36)));
        sessionSearchBox = input("", false);
        sessionSearchBox.setHint("搜索本项目会话");
        sessionSearchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) { sidebarSessionQuery = e == null ? "" : e.toString(); renderSidebarSessionRows(); }
            public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        });
        sessionSearchBox.setVisibility(View.GONE); // 搜索默认收起，点顶栏 🔍 就在下方展开
        side.addView(sessionSearchBox, lp(-1, dp(32)));
        // 历史标题行：↻ 换历史 / ⌂ 换项目，与标题同行、同芯片样式。
        String projectName=new File(config.projectDirectory).getName();if(projectName.isEmpty())projectName="主目录";
        LinearLayout historyHead=hbox();historyHead.setGravity(Gravity.CENTER_VERTICAL);historyHead.setPadding(dp(8),dp(2),0,0);
        TextView section = text("项目历史 · "+shorten(projectName,14),10.5f, MUTED_2); section.setTypeface(Typeface.DEFAULT_BOLD);
        historyHead.addView(section,new LinearLayout.LayoutParams(0,-2,1));
        TextView historyPick=sidebarIconChip(R.drawable.ic_refresh,"选择当前项目的上下文历史",13);historyPick.setOnClickListener(v->showResumePicker());historyHead.addView(historyPick,miniChipParams());
        TextView projectPick=sidebarIconChip(R.drawable.ic_home,"切换项目路径",13);projectPick.setOnClickListener(v->showProjectPathDialog());historyHead.addView(projectPick,miniChipParams());
        side.addView(historyHead,lp(-1,dp(24)));
        ScrollView sessionScroll = new ScrollView(this);
        sessionScroll.setVerticalScrollBarEnabled(false);
        // 列表短时把内容撑满可视高度：底部"快捷开始"卡片贴住导航行，侧栏不再是一整片空白。
        sessionScroll.setFillViewport(true);
        LinearLayout sessionList = vbox();
        UiMotion.enableLayoutChanges(sessionList);
        // Reading every session JSONL of the project can block for a while on a big
        // project, so the sidebar paints a loading row first and fills in from an
        // io task; the generation guard drops results for a sidebar already rebuilt.
        final int sidebarGen = ++sidebarGeneration;
        final LinearLayout pendingRows = sessionList;
        sidebarSessionHost = sessionList;
        TextView loading = text("正在读取会话…", 10.5f, MUTED_2);
        loading.setGravity(Gravity.CENTER_VERTICAL); loading.setPadding(dp(10),0,0,0);
        sessionList.addView(loading, lp(-1, dp(32)));
        UiMotion.staggerChildren(sessionList, 8);
        io.execute(() -> {
            final List<SessionStore.SessionSummary> saved = SessionStore.listSessions(config.projectDirectory);
            ui(() -> {
                if (sidebarGen != sidebarGeneration) return;
                sidebarSessionSummaries.clear();
                sidebarSessionSummaries.addAll(saved);
                renderSidebarSessionRows();
            });
        });
        sessionScroll.addView(sessionList, new ScrollView.LayoutParams(-1,-2));
        // 会话区吃掉全部剩余高度（宽屏不再固定 232dp）：列表短→卡片贴底，列表长→内部滚动。
        side.addView(sessionScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // 底部导航一行：文件 / 沙箱 / 环境 三枚等宽芯片贴底，内容居中、样式与顶部芯片一致。
        LinearLayout bottomNav=hbox();bottomNav.setGravity(Gravity.CENTER_VERTICAL);
        TextView filesRow=sidebarNavChip("⌘  文件");
        filesRow.setContentDescription("浏览、编辑项目文件，带内联代码编辑器");
        filesRow.setOnClickListener(v -> {
            if (mobileSidebarDialog != null) { mobileSidebarDialog.dismiss(); mobileSidebarDialog = null; }
            focusWorkspace(VIEW_FILES);
        });
        bottomNav.addView(filesRow,navChipParams());
        TextView sandboxRow=sidebarNavChip("▣  沙箱");
        sandboxRow.setContentDescription("打开集成的 IQ Sandbox 虚拟安卓环境");
        sandboxRow.setOnClickListener(v -> startActivity(new Intent(this, SandboxDashboardActivity.class)));
        bottomNav.addView(sandboxRow,navChipParams());
        TextView runtimeRow = sidebarNavChip(runtime.isInstalled() ? "●  环境" : "○  环境");
        runtimeRow.setTextColor(runtime.isInstalled() ? GREEN : MUTED);
        tintIcons(runtimeRow, runtime.isInstalled() ? GREEN : MUTED);
        runtimeRow.setOnClickListener(v -> runtimeDialog());
        bottomNav.addView(runtimeRow,navChipParams());
        side.addView(bottomNav,lp(-1,dp(38)));
        return side;
    }

    /** 列表底部留白处的"快捷开始"卡片：会话少时占住空档，会话多时随列表滚到下方。 */
    private View sidebarQuickStartCard() {
        LinearLayout card = vbox();
        card.setBackground(round(SURFACE_2, 12, Color.TRANSPARENT, 0));
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView caption = text("快捷开始", 10.5f, MUTED_2);
        caption.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(caption, lp(-1, dp(18)));
        LinearLayout actions = hbox();
        TextView terminal = sidebarNavChip("›_  终端");
        terminal.setContentDescription("打开终端工作区");
        terminal.setOnClickListener(v -> {
            if (mobileSidebarDialog != null) { mobileSidebarDialog.dismiss(); mobileSidebarDialog = null; }
            focusWorkspace(VIEW_TERMINAL);
        });
        actions.addView(terminal, new LinearLayout.LayoutParams(0, dp(32), 1));
        TextView project = sidebarNavChip("⌂  项目路径");
        project.setContentDescription("手动添加 / 切换项目路径");
        project.setOnClickListener(v -> showProjectPathDialog());
        LinearLayout.LayoutParams projectLp = new LinearLayout.LayoutParams(0, dp(32), 1);
        projectLp.leftMargin = dp(6);
        actions.addView(project, projectLp);
        card.addView(actions, lp(-1, dp(32)));
        TextView hint = text("长按管理会话 · 顶部搜索项目历史", 10.5f, MUTED_2);
        hint.setLineSpacing(0, 1.1f);
        LinearLayout.LayoutParams hintLp = lp(-1, -2); hintLp.setMargins(0, dp(6), 0, 0);
        card.addView(hint, hintLp);
        return card;
    }

    private View sessionRow(SessionStore.SessionSummary summary) {
        File current = engine == null ? null : engine.getSessionFile();
        boolean active = current != null && current.equals(summary.file);
        String key = runtimeKey(summary.file);
        sessionRowSummaries.put(key, summary);
        SessionRuntime rt = sessionRuntimes.get(key);
        LinearLayout row = hbox(); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(2), dp(2), dp(2));
        if (active) row.setBackground(round(mixColor(SURFACE_2,ACCENT,.08f),10, BORDER_SOFT, 1));
        row.setSelected(active);

        TextView label = text(sessionLabel(summary, active, rt), 12.5f, rt != null && rt.busy ? ACCENT : TEXT);
        sessionRowLabels.put(key, label);
        label.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        label.setGravity(Gravity.CENTER_VERTICAL);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(dp(4),0,dp(4),0);
        label.setOnClickListener(v -> resumeSession(summary));
        View.OnLongClickListener historyActions=v->{showSessionHistoryActions(summary,()->refreshVisibleSessionRowsFromDiskAsync());return true;};
        label.setOnLongClickListener(historyActions);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView when = text(relativeSessionTime(summary.activityModifiedAt), 10.5f, MUTED_2);
        when.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT); when.setSingleLine(true);
        row.addView(when, lp(-2, dp(38)));

        TextView delete = iconOnly(R.drawable.ic_close,18, MUTED_2);
        delete.setContentDescription("删除会话");
        delete.setOnClickListener(v -> confirmDeleteSession(summary));
        row.addView(delete, lp(dp(30), dp(38)));
        row.setOnLongClickListener(historyActions);
        return row;
    }

    /** Rebuilds the sidebar session list from cached summaries only - no disk I/O happens here.
     *  Rows are grouped into 今天/更早, filtered by the search box and capped by default so the
     *  column stays compact; a 展开其余/收起 toggle reveals the rest on demand. */
    private void renderSidebarSessionRows() {
        final LinearLayout host = sidebarSessionHost;
        if (host == null) return;
        host.removeAllViews();
        final String query = sidebarSessionQuery == null ? "" : sidebarSessionQuery.trim().toLowerCase();
        final List<SessionStore.SessionSummary> matched = new ArrayList<>();
        for (SessionStore.SessionSummary summary : sidebarSessionSummaries) {
            if (query.isEmpty()) { matched.add(summary); continue; }
            String note = summary.note == null ? "" : summary.note.toLowerCase();
            String title = summary.title == null ? "" : summary.title.toLowerCase();
            if (title.contains(query) || note.contains(query)) matched.add(summary);
        }
        final int cap = wide ? 12 : 10;
        final int limit = sidebarSessionsExpanded ? matched.size() : Math.min(matched.size(), cap);
        final long todayStart = sessionDayStart();
        boolean todayHeader = false, earlierHeader = false;
        for (int i = 0; i < limit; i++) {
            SessionStore.SessionSummary summary = matched.get(i);
            boolean today = summary.activityModifiedAt >= todayStart;
            if (today && !todayHeader) { host.addView(sidebarGroupCaption("今天"), lp(-1, dp(18))); todayHeader = true; }
            if (!today && !earlierHeader) { host.addView(sidebarGroupCaption("更早"), lp(-1, dp(18))); earlierHeader = true; }
            host.addView(sessionRow(summary), lp(-1, dp(40)));
        }
        if (limit == 0) {
            TextView empty = text(query.isEmpty() ? "暂无已保存会话" : "没有匹配的会话", 10.5f, MUTED_2);
            empty.setGravity(Gravity.CENTER_VERTICAL); empty.setPadding(dp(10), 0, 0, 0);
            host.addView(empty, lp(-1, dp(32)));
        } else if (matched.size() > cap) {
            boolean expanded = sidebarSessionsExpanded;
            TextView toggle = text(expanded ? "收起会话列表 ▲" : "展开其余 " + (matched.size() - cap) + " 个会话 ▼", 10.5f, expanded ? MUTED_2 : ACCENT);
            toggle.setGravity(Gravity.CENTER_VERTICAL); toggle.setPadding(dp(10), 0, 0, 0);
            toggle.setContentDescription(expanded ? "收起会话列表" : "展开全部会话");
            toggle.setOnClickListener(v -> { sidebarSessionsExpanded = !sidebarSessionsExpanded; renderSidebarSessionRows(); });
            host.addView(toggle, lp(-1, dp(28)));
        }
        // 撑开剩余高度：列表短时把"快捷开始"卡片推到列表区底部，空白变成有用的入口。
        View filler = new View(this);
        host.addView(filler, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout.LayoutParams cardLp = lp(-1, -2);
        cardLp.setMargins(0, dp(6), 0, dp(2));
        host.addView(sidebarQuickStartCard(), cardLp);
        UiMotion.staggerChildren(host, 8);
    }

    private TextView sidebarGroupCaption(String label) {
        TextView caption = text(label, 10.5f, MUTED_2);
        caption.setTypeface(Typeface.DEFAULT_BOLD); caption.setGravity(Gravity.CENTER_VERTICAL); caption.setPadding(dp(10), 0, 0, 0);
        return caption;
    }

    private long sessionDayStart() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void confirmDeleteSession(SessionStore.SessionSummary summary) {
        if (summary == null) return;
        final Dialog d = newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("删除这个会话？"),lp(-1,dp(48)));
        TextView body = text(summary.title + "\n" + relativeSessionTime(summary.activityModifiedAt) + " · " + summary.messageCount + " 条消息",12,MUTED); body.setLineSpacing(0,1.12f); body.setPadding(0,0,0,dp(12)); panel.addView(body,lp(-1,-2));
        LinearLayout actions = hbox(); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(0,dp(6),0,0);
        TextView cancel = dialogButton("取消",MUTED,false); cancel.setOnClickListener(v->d.dismiss()); actions.addView(cancel,dialogActionParams());
        TextView remove = dialogButton("删除",RED,true); remove.setOnClickListener(v->{
            String key=runtimeKey(summary.file);
            SessionRuntime doomed=sessionRuntimes.get(key);
            if(doomed!=null&&(doomed.busy||doomed.engine.isBusy()||doomed.planState.isAwaitingApproval())){toast("会话仍在执行，请先停止后再删除");return;}
            File active = activeRuntime == null ? null : activeRuntime.file;
            boolean deletingActive = active != null && runtimeKey(active).equals(key);
            if (!SessionStore.deleteSession(summary.file)) { toast("无法删除会话"); return; }
            d.dismiss();
            if(doomed!=null){sessionRuntimes.remove(key,doomed);doomed.engine.shutdown();}
            sessionRowLabels.remove(key);sessionRowSummaries.remove(key);
            if (deletingActive) { settingsStore.setLastSessionFile(null); newSession(); toast("会话已删除"); }
            else { toast("会话已删除"); refreshVisibleSessionRows(); if (mobileSidebarDialog != null) { mobileSidebarDialog.dismiss(); mobileSidebarDialog = null; showMobileSidebar(); } }
        }); actions.addView(remove,dialogActionParams());
        panel.addView(actions,lp(-1,dp(60)));
        d.setContentView(panel); d.show(); styleDialogWindow(d,dialogWidthPx(390),-2);
    }

    private void showSessionHistoryActions(SessionStore.SessionSummary summary,Runnable refreshed){
        if(summary==null)return;
        String[] options=summary.note.isEmpty()?new String[]{"编辑备注","恢复会话"}:new String[]{"编辑备注","清除备注","恢复会话"};
        showChoicePicker("会话操作",options,-1,which->{
            String selected=options[which];
            if("编辑备注".equals(selected))showSessionNoteEditor(summary,refreshed);
            else if("清除备注".equals(selected))updateSessionNote(summary,"",refreshed);
            else resumeSession(summary);
        });
    }

    private void showSessionNoteEditor(SessionStore.SessionSummary summary,Runnable refreshed){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("会话备注"),lp(-1,dp(48)));EditText note=input(summary.note,false);note.setHint("输入备注，最多 500 个字符");note.setGravity(Gravity.TOP|Gravity.START);note.setMinLines(3);note.setMaxLines(7);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(150));panel.addView(note,np);
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(8),0,0);TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->d.dismiss());actions.addView(cancel,dialogActionParams());TextView save=dialogButton("保存备注",TEXT,true);save.setOnClickListener(v->{d.dismiss();updateSessionNote(summary,note.getText().toString(),refreshed);});actions.addView(save,dialogActionParams());panel.addView(actions,lp(-1,dp(62)));d.setContentView(panel);d.show();styleFormDialog(d,dialogWidthPx(460));
    }

    private void updateSessionNote(SessionStore.SessionSummary summary,String note,Runnable refreshed){
        io.submit(()->{try{SessionStore.updateSessionMetadata(summary.file,note,summary.titleOverride);SessionStore.SessionSummary fresh=SessionStore.summarize(summary.file);ui(()->{sessionRowSummaries.put(runtimeKey(fresh.file),fresh);refreshVisibleSessionRows();if(refreshed!=null)refreshed.run();toast(note==null||note.trim().isEmpty()?"备注已清除":"备注已保存");});}catch(Exception error){ui(()->toast("备注保存失败："+(error.getMessage()==null?"":error.getMessage())));}});
    }

    private GradientDrawable sidebarBackground(){
        GradientDrawable background=neonTheme?navyGradient(0):round(SIDEBAR,0,Color.TRANSPARENT,0);
        float radius=dp(16);
        background.setCornerRadii(new float[]{0,0,radius,radius,radius,radius,0,0});
        return background;
    }

    private TextView sidebarAction(String label, boolean primary) {
        int iconRes = 0; String caption = label;
        int gap = label.indexOf("  ");
        if (gap > 0) { int r = glyphIconRes(label.substring(0, gap)); if (r != 0) { iconRes = r; caption = label.substring(gap + 2).trim(); } }
        TextView t = text(caption, 12, primary ? TEXT : MUTED);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(10), 0, dp(8), 0);
        if (iconRes != 0) applyLeftIcon(t, iconRes,16, primary ? TEXT : MUTED);
        if (primary) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setBackground(neonTheme ? accentGradient(12) : round(SURFACE_2,12, BORDER_SOFT, 1));
        }
        return t;
    }

    private View buildSessionArea() {
        LinearLayout area = vbox(); area.setTag("workspace.session_area"); area.setBackgroundColor(BG);
        if (wide) {
            LinearLayout panes = hbox();
            primaryHost = new FrameLayout(this); primaryHost.setTag("workspace.primary"); primaryHost.setBackgroundColor(BG);
            secondaryHost = new FrameLayout(this); secondaryHost.setTag("workspace.secondary"); secondaryHost.setBackgroundColor(SURFACE);
            panes.addView(primaryHost, new LinearLayout.LayoutParams(0, -1, 56f));
            panes.addView(dividerVertical(), lp(1, -1));
            LinearLayout right = vbox(); right.setBackgroundColor(SURFACE);
            right.addView(buildWorkspaceTabs(false), lp(-1, dp(44)));
            right.addView(dividerHorizontal(), lp(-1,1));
            right.addView(secondaryHost, new LinearLayout.LayoutParams(-1,0,1));
            panes.addView(right, new LinearLayout.LayoutParams(0, -1, 44f));
            area.addView(panes, new LinearLayout.LayoutParams(-1,0,1));
        } else {
            // 窄屏：对话/终端/文件 已收进顶栏的分段控件（buildCompactViewTabs），这里只留内容区。
            primaryHost = new FrameLayout(this); primaryHost.setTag("workspace.primary"); primaryHost.setBackgroundColor(BG);
            area.addView(primaryHost, new LinearLayout.LayoutParams(-1,0,1));
        }
        return area;
    }

    private View buildWorkspaceTabs(boolean includeChat) {
        LinearLayout tabs = hbox(); tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(10), neonTheme && !wide ? dp(6) : dp(4), dp(10), neonTheme && !wide ? dp(6) : dp(4));
        tabs.setBackground(neonTheme ? elevatedCard(0) : round(wide ? SURFACE : BG,0,Color.TRANSPARENT,0));
        viewTabs.clear();
        if (includeChat) addViewTab(tabs, neonTheme ? "⌂\n对话" : "对话", VIEW_CHAT);
        addViewTab(tabs, neonTheme && !wide ? ">_\n终端" : "终端", VIEW_TERMINAL);
        addViewTab(tabs, neonTheme && !wide ? "▣\n文件" : "文件", VIEW_FILES);
        return tabs;
    }

    /**
     * 窄屏顶栏里的分段控件：对话 / 终端 两段等分，图标 + 短标。
     * 仍然写进 viewTabs，所以 focusWorkspace/styleViewTabs 一套逻辑照用。
     * 文件工作区不在这里：它低频且是一个完整工作区，入口放在侧边栏「工作区」下。
     */
    private LinearLayout buildCompactViewTabs() {
        LinearLayout group = hbox(); group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(dp(3), dp(3), dp(3), dp(3));
        group.setBackground(round(SURFACE_2,14,BORDER_SOFT,1));
        viewTabs.clear();
        compactTabIndex = -1;
        // 滑块画在标签下面一层：切换时它从一段滑到另一段，标签本身不再自带底色。
        FrameLayout stack = new FrameLayout(this);
        compactTabIndicator = new View(this);
        compactTabIndicator.setBackground(neonTheme ? accentGradient(11) : round(mixColor(SURFACE_2,ACCENT,.12f),11,Color.TRANSPARENT,0));
        stack.addView(compactTabIndicator, new FrameLayout.LayoutParams(dp(50), dp(34)));
        LinearLayout row = hbox();
        addCompactViewTab(row, "⌂", "对话", VIEW_CHAT);
        addCompactViewTab(row, ">_", "终端", VIEW_TERMINAL);
        stack.addView(row, new FrameLayout.LayoutParams(-2,-2));
        group.addView(stack, new LinearLayout.LayoutParams(-2, dp(34)));
        styleViewTabs();
        return group;
    }

    /** 让滑块滑到当前视图所在的那一段；视图不在分段控件里（文件工作区）时淡出。 */
    private void slideCompactTabIndicator() {
        if (compactTabIndicator == null || viewTabs.isEmpty()) return;
        int slot = -1;
        for (int i = 0; i < viewTabs.size(); i++) {
            Object tag = viewTabs.get(i).getTag();
            if (tag instanceof Integer && ((Integer)tag).intValue() == currentView) { slot = i; break; }
        }
        if (slot < 0) {
            compactTabIndicator.animate().cancel();
            compactTabIndicator.setAlpha(0f);
            compactTabIndex = -1;
            return;
        }
        int width = viewTabs.get(0).getWidth(); if (width <= 0) width = dp(50);
        float x = (float)width * slot;
        boolean animate = compactTabIndex != -1;
        compactTabIndicator.setAlpha(1f);
        compactTabIndex = slot;
        if (!animate) { compactTabIndicator.animate().cancel(); compactTabIndicator.setTranslationX(x); return; }
        UiMotion.slideToX(compactTabIndicator, x, 200L);
    }

    private void addCompactViewTab(LinearLayout group, String glyph, String caption, int view) {
        int iconRes = tabIconRes(glyph);
        TextView t = text(caption, 10.5f, MUTED); t.setGravity(Gravity.CENTER); t.setTag(view); t.setSingleLine(true);
        if (iconRes != 0) { t.setCompoundDrawablePadding(dp(3)); t.setCompoundDrawables(vectorIcon(iconRes,12,MUTED),null,null,null); }
        t.setContentDescription("切换到" + caption + "工作区");
        t.setOnClickListener(v -> focusWorkspace((Integer)v.getTag()));
        viewTabs.add(t); group.addView(t, lp(dp(50), dp(34)));
    }

    private void addViewTab(LinearLayout tabs, String name, int view) {
        int tabIcon = 0; String caption = name;
        int nl = name.indexOf('\n');
        if (nl > 0) { int r = tabIconRes(name.substring(0, nl)); if (r != 0) { tabIcon = r; caption = name.substring(nl + 1); } }
        TextView t = text(caption, 11, MUTED); t.setGravity(Gravity.CENTER); t.setTag(view);
        if (tabIcon != 0) applyTopIcon(t, tabIcon,18, MUTED);
        if (neonTheme && !wide) { t.setTextSize(10.5f); t.setLineSpacing(0,1.08f); }
        t.setOnClickListener(v -> focusWorkspace((Integer)v.getTag()));
        LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(0,dp(neonTheme&&!wide?54:34),1);
        if(neonTheme&&!wide)tabParams.setMargins(dp(3),0,dp(3),0);
        viewTabs.add(t); tabs.addView(t,tabParams);
        styleViewTabs();
    }

    private void styleViewTabs() {
        for (TextView t : viewTabs) {
            int v = (Integer)t.getTag(); boolean active = currentView == v;
            int tint = active ? (neonTheme ? Color.WHITE : TEXT) : MUTED;
            t.setSelected(active);
            t.setTextColor(tint);
            tintIcons(t, tint);
            t.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            // 窄屏是顶栏里的分段控件：底色由滑块统一画，标签本身透明。
            if (!wide) t.setBackground(round(Color.TRANSPARENT,13,Color.TRANSPARENT,0));
            else t.setBackground(active && neonTheme ? accentGradient(15) : round(Color.TRANSPARENT,14,Color.TRANSPARENT,0));
            UiMotion.selection(t, active);
        }
        if (!wide) slideCompactTabIndicator();
    }

    private void focusWorkspace(int view) {
        int previous = currentView;
        currentView = view; styleViewTabs();
        if (wide) {
            if (view == VIEW_CHAT) { focusChat(); return; }
            else if (view == VIEW_TERMINAL) renderTerminal(secondaryHost);
            else if (view == VIEW_FILES) renderFiles(secondaryHost);
        } else if (previous == view) {
            if (view == VIEW_CHAT) renderChat(primaryHost);
            else if (view == VIEW_TERMINAL) renderTerminal(primaryHost);
            else if (view == VIEW_FILES) renderFiles(primaryHost);
        } else {
            switchWorkspacePane(previous, view);
        }
    }

    /**
     * 窄屏工作区切换：先把旧画面截一张图盖在新内容上淡出，新内容从切换方向滑入，
     * 对话↔终端↔文件 不再是一刀切。截图拿不到（无动画/未测量）时退回原来的直接渲染。
     */
    private void switchWorkspacePane(int previous, int view) {
        Bitmap ghost = snapshotPane(primaryHost);
        if (previous == VIEW_CHAT && prompt != null) {
            composerDraftCarry = prompt.getText().toString();
            composerCaretCarry = Math.max(0, prompt.getSelectionStart());
            composerFocusCarry = prompt.hasFocus();
        }
        suppressPaneAnimation = ghost != null;
        try {
            // 有截图过渡时关闭 render 内置的 pageIn：它从 alpha=0 起播，随后会被
            // crossFadePane 的 paneIn 取掉，页面就永远停在透明（“切回来界面消失”的元凶）。
            if (view == VIEW_CHAT) renderChat(primaryHost, false);
            else if (view == VIEW_TERMINAL) renderTerminal(primaryHost);
            else if (view == VIEW_FILES) renderFiles(primaryHost);
        } finally {
            suppressPaneAnimation = false;
        }
        crossFadePane(ghost, view > previous ? 1 : -1);
        if (view == VIEW_CHAT) restoreComposerCarry();
    }

    /** 切回对话：把切走前的草稿、光标位置和焦点一并放回去。 */
    private void restoreComposerCarry() {
        if (prompt == null) return;
        if (!composerDraftCarry.isEmpty()) {
            suppressPromptWatcher = true;
            try {
                prompt.setText(composerDraftCarry);
                prompt.setSelection(Math.min(composerCaretCarry, composerDraftCarry.length()));
            } finally {
                suppressPromptWatcher = false;
            }
        }
        if (composerFocusCarry) prompt.requestFocus();
    }

    private Bitmap snapshotPane(FrameLayout host) {
        if (host == null || host.getChildCount() == 0 || !UiMotion.enabled(this)) return null;
        if (host.getWidth() == 0 || host.getHeight() == 0) return null;
        try {
            Bitmap shot = Bitmap.createBitmap(host.getWidth(), host.getHeight(), Bitmap.Config.ARGB_8888);
            host.draw(new Canvas(shot));
            return shot;
        } catch (Throwable ignored) { return null; }
    }

    private void crossFadePane(Bitmap ghost, int direction) {
        if (ghost == null || primaryHost == null) return;
        if (ghost.isRecycled() || primaryHost.getChildCount() == 0) { if (!ghost.isRecycled()) ghost.recycle(); return; }
        final View fresh = primaryHost.getChildAt(0);
        // 取掉任何未完成进场动画（pageIn 停在 alpha=0），再从侧边滑入。
        fresh.animate().cancel();
        fresh.setAlpha(1f);
        float offset = dp(6) * direction;
        fresh.setTranslationX(offset);
        final View layer = snapshotLayer(ghost);
        primaryHost.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        UiMotion.paneIn(fresh, layer, offset, 200L, () -> { primaryHost.removeView(layer); if (!ghost.isRecycled()) ghost.recycle(); });
    }

    private void focusChat() {
        currentView = VIEW_CHAT; styleViewTabs(); renderChat(primaryHost);
    }

    /** Times main-thread work; only frame-dropping runs are recorded. */
    private static void reportStall(String label, long startedAt) {
        if (startedAt == 0L) return;
        SandboxDebugLog.stall(label, SystemClock.elapsedRealtime() - startedAt);
    }

    /** Context-token reads happen on the UI thread; report when one of them blocks a frame. */
    private static int estimateContextTokensTimed(IQCodeEngine engine) {
        if (engine == null) return 0;
        long startedAt = SystemClock.elapsedRealtime();
        int tokens = engine.estimateContextTokens();
        reportStall("estimateContextTokens", startedAt);
        return tokens;
    }

    private void scheduleChatRender() {
        if (chatRenderPosted || primaryHost == null || chatMessages == null) return;
        chatRenderPosted = true;
        primaryHost.postOnAnimation(chatRenderRunnable);
    }

    private void renderChat(FrameLayout host) { renderChat(host, true); }

    private void renderChat(FrameLayout host, boolean animate) {
        final long stallStartedAt = SystemClock.elapsedRealtime();
        final int oldY = chatScroll == null ? 0 : chatScroll.getScrollY();
        final boolean follow = chatAutoFollow;
        cancelAutoFollowScroll();
        cancelChatGlide();
        final long treeGeneration = ++chatTreeGeneration;
        if(agentProgressView!=null)agentProgressView.clear();
        host.removeAllViews();
        LinearLayout page = vbox(); page.setBackgroundColor(BG); chatPage=page;
        chatScroll = new ScrollView(this){
            private float touchDownY;
            private final int touchSlop=android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
            private void trackTouch(MotionEvent e){
                int action=e.getActionMasked();
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){chatUserTouching=true;cancelAutoFollowScroll();cancelChatGlide();}
                if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){chatAutoFollow=false;cancelAutoFollowScroll();}
                else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){chatUserTouching=false;updateChatFollowState();}
            }
            @Override public boolean onInterceptTouchEvent(MotionEvent e){
                trackTouch(e);
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN)touchDownY=e.getY();
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getY()-touchDownY)>touchSlop)return true;
                return super.onInterceptTouchEvent(e);
            }
            @Override public boolean onTouchEvent(MotionEvent e){trackTouch(e);return super.onTouchEvent(e);}
        };
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        chatScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        chatScroll.setOnScrollChangeListener((v, sx, sy, ox, oy) -> { if(chatUserTouching) updateChatFollowState(); });
        chatMessages = vbox(); chatMessages.setPadding(dp(wide?30:16), dp(18), dp(wide?30:16), 0);
        currentConversationHost=null;
        clearCollapsedToolActivityViews();
        streamingBodyHost = null; streamingView = null;
        streamingRenderTreeGeneration = treeGeneration;
        streamingVisibleChars = streamingItem == null ? 0 : streamingItem.body.length();
        agentProgressView=new AgentProgressView(this);agentProgressView.setPalette(TEXT,MUTED,ACCENT,GREEN,SURFACE_3);
        rebuildingTranscript = true;
        addTranscriptWindow();
        attachChatImeSpacer();
        rebuildingTranscript = false;
        SessionRuntime progressRuntime=activeRuntime;
        if(progressRuntime!=null)agentProgressView.update(runtimeDisplayStatus(progressRuntime),progressRuntime.taskSnapshot,progressRuntime.planState,progressRuntime.busy||progressRuntime.engine.isBusy());
        pushAgentSteps();
        chatScroll.addView(chatMessages); page.addView(chatScroll, new LinearLayout.LayoutParams(-1,0,1));
        chatBottomHost=vbox();
        // 状态/思考行（sphere + 状态 + 推理预览 + 任务行）高度一直在变。它一旦占布局，
        // 每变一次都会把会话区顶得忽高忽低——所以让它占 0 高度、向上浮在输入框上方：
        // 出现、变高、消失都不再改动会话区的尺寸。
        chatBottomHost.setClipChildren(false);
        page.setClipChildren(false);
        FrameLayout progressSlot=new FrameLayout(this);
        progressSlot.setClipChildren(false);
        FrameLayout.LayoutParams progressLp=new FrameLayout.LayoutParams(-1,-2);
        progressLp.gravity=Gravity.BOTTOM;
        progressLp.setMargins(dp(wide?24:12),0,dp(wide?24:12),0);
        progressSlot.addView(agentProgressView,progressLp);
        chatBottomHost.addView(progressSlot,new LinearLayout.LayoutParams(-1,0));
        // 回话时的滚木指示条：直接排在输入框上方，执行中常显、停止即收起。
        workingBar=new LinearLayout(this);workingBar.setOrientation(LinearLayout.HORIZONTAL);
        workingBar.setGravity(Gravity.CENTER_VERTICAL);
        workingBar.setBackground(round(SURFACE_2,16,BORDER_SOFT,1));
        workingBar.setPadding(dp(12),dp(6),dp(12),dp(6));
        rollingLog=new RollingLogView(this);rollingLog.setColor(ACCENT);
        workingBar.addView(rollingLog,lp(dp(20),dp(20)));
        workingBarText=text(workingStatus,12,TEXT);workingBarText.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        LinearLayout.LayoutParams workingTextLp=lp(-2,-2);workingTextLp.setMargins(dp(8),0,0,0);
        workingBar.addView(workingBarText,workingTextLp);
        // 左边是思考状态，右边是当前要做的任务。
        View workingSpacer=new View(this);
        workingBar.addView(workingSpacer,new LinearLayout.LayoutParams(0,1,1f));
        workingBarTask=text("",11,MUTED);workingBarTask.setSingleLine(true);
        workingBarTask.setEllipsize(android.text.TextUtils.TruncateAt.END);workingBarTask.setGravity(Gravity.END);
        LinearLayout.LayoutParams workingTaskLp=lp(-2,-2);
        workingBar.addView(workingBarTask,workingTaskLp);
        // 展开箭头：有任务时出现，▾ 已展开 / ▸ 已收起。
        workingBarChevron=text("▸",11,MUTED);
        LinearLayout.LayoutParams chevronLp=lp(-2,-2);chevronLp.setMargins(dp(6),0,0,0);
        workingBarChevron.setVisibility(View.GONE);
        workingBar.addView(workingBarChevron,chevronLp);
        workingBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams workingBarLp=lp(-2,-2);
        workingBarLp.setMargins(dp(wide?24:12),0,dp(wide?24:12),dp(6));
        chatBottomHost.addView(workingBar,workingBarLp);
        // 点滚木条展开/收起完整任务清单：一项一行，完成的划掉。
        workingTaskList=new LinearLayout(this);workingTaskList.setOrientation(LinearLayout.VERTICAL);
        workingTaskList.setBackground(round(SURFACE_2,12,BORDER_SOFT,1));
        workingTaskList.setPadding(dp(12),dp(6),dp(12),dp(6));
        LinearLayout.LayoutParams workingTaskListLp=lp(-2,-2);
        workingTaskListLp.setMargins(dp(wide?24:12),0,dp(wide?24:12),dp(4));
        workingTaskList.setVisibility(View.GONE);
        chatBottomHost.addView(workingTaskList,workingTaskListLp);
        workingBar.setOnClickListener(v->toggleWorkingTaskList());
        SessionRuntime barRuntime=activeRuntime;
        if(barRuntime!=null&&(barRuntime.busy||barRuntime.engine.isBusy())){
            workingBarText.setText(workingStatus);workingBar.setVisibility(View.VISIBLE);rollingLog.setRolling(true);
            refreshWorkingBarTask(barRuntime);
        }
        composerHost=buildComposer();
        LinearLayout.LayoutParams composerLp=lp(-1,-2);
        composerLp.gravity=Gravity.CENTER_HORIZONTAL;
        chatBottomHost.addView(composerHost,composerLp);
        // 横屏时输入框长度砍半（智能限幅：不超过可用宽度的一半、不窄于 300dp、不超过 620dp），
        // 居中摆放，拇指不用横跨整块屏幕；竖屏保持全宽，宽屏（平板）维持 620dp 上限。
        boolean landscapeNow = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(wide || landscapeNow){
            chatBottomHost.post(()->{
                if(composerHost==null||composerHost.getParent()==null||chatBottomHost.getWidth()<=0)return;
                int avail=chatBottomHost.getWidth();
                int capped=Math.min(avail,dp(620));
                if(landscapeNow){
                    int half=Math.max(dp(300),avail/2);
                    capped=Math.min(capped,half);
                }
                if(composerHost.getLayoutParams().width!=capped){
                    composerHost.getLayoutParams().width=capped;
                    composerHost.requestLayout();
                }
            });
        }
        page.addView(chatBottomHost,lp(-1,-2));
        moveKeyboardView(chatBottomHost,0,false);
        host.addView(page);
        applyChatKeyboardOffset(keyboardVisible?keyboardOffset:0);
        page.post(()->applyChatKeyboardOffset(keyboardVisible?keyboardOffset:0));
        syncComposerForActiveRuntime();
        if(animate)UiMotion.pageIn(page);
        UiMotion.bindInteractive(composerHost);
        final ScrollView targetScroll=chatScroll;
        targetScroll.post(() -> {
            if(treeGeneration!=chatTreeGeneration||targetScroll!=chatScroll)return;
            if(follow)scrollChatToEndWithoutFocus(targetScroll,chatMessages,false);else targetScroll.scrollTo(0,oldY);
        });
        reportStall("renderChat", stallStartedAt);
    }

    private void attachChatImeSpacer(){
        if(chatMessages==null)return;
        chatImeSpacer=new View(this);
        chatMessages.addView(chatImeSpacer,lp(-1,0));
    }

    private void addEmptyState(LinearLayout parent) {
        parent.addView(new View(this), lp(-1, dp(wide ? 92 : 56)));
        TextView caption = text("IQ CODE", 11, MUTED_2);
        caption.setLetterSpacing(.14f);caption.setGravity(Gravity.CENTER);
        caption.setPadding(0,0,0,dp(18));parent.addView(caption,lp(-1,-2));
        TextView title = text("从这里开始", wide ? 24 : 22, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); title.setGravity(Gravity.CENTER);
        title.setPadding(0,0,0,dp(12)); parent.addView(title, lp(-1,-2));
        TextView sub = text("描述任务，开始工作。\n代码、终端与验证，都在当前项目里。", 12.5f, MUTED);
        sub.setGravity(Gravity.CENTER); sub.setLineSpacing(dp(3),1.15f);
        sub.setPadding(dp(16),0,dp(16),dp(12)); parent.addView(sub, lp(-1,-2));
    }

    private View buildComposer() {
        LinearLayout wrap = vbox(); wrap.setPadding(dp(wide?24:12), dp(6), dp(wide?24:12), dp(12));
        slashPalette = vbox();
        slashPalette.setBackground(elevatedCard(16));
        slashPaletteScroll = new ScrollView(this);
        slashPaletteScroll.setVisibility(View.GONE);
        slashPaletteScroll.setFillViewport(false);
        slashPaletteScroll.setVerticalScrollBarEnabled(false);
        slashPaletteScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        slashPaletteScroll.addView(slashPalette, new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams slashLp = new LinearLayout.LayoutParams(-1,dp(210)); slashLp.setMargins(0,0,0,dp(6));
        wrap.addView(slashPaletteScroll, slashLp);

        LinearLayout card = vbox(); card.setPadding(dp(12), dp(12), dp(12), dp(8)); card.setBackground(round(SURFACE_2,18, BORDER, 1));
        attachmentStrip = vbox();
        UiMotion.enableLayoutChanges(attachmentStrip);
        rebuildAttachmentStrip();
        card.addView(attachmentStrip, lp(-1,-2));
        prompt = new EditText(this); prompt.setTextColor(TEXT); prompt.setHintTextColor(MUTED_2); prompt.setHint("描述任务或向 IQ 提问");
        prompt.setEnabled(true); prompt.setFocusable(true); prompt.setFocusableInTouchMode(true); prompt.setVisibility(View.VISIBLE);
        prompt.setTextSize(14 * uiFontScale); prompt.setGravity(Gravity.TOP); prompt.setMinLines(2); prompt.setMaxLines(5);
        prompt.setLineSpacing(dp(2),1.12f);
        prompt.setBackgroundColor(Color.TRANSPARENT); prompt.setPadding(dp(4), dp(2), dp(4), dp(10));
        prompt.setOnFocusChangeListener((v, focused) -> card.setBackground(round(SURFACE_2,18, focused ? mixColor(BORDER,ACCENT,.5f) : BORDER, 1)));
        prompt.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        prompt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { if(!suppressPromptWatcher) updateSlashPalette(s == null ? "" : s.toString()); }
            @Override public void afterTextChanged(Editable e) { }
        });
        card.addView(prompt, lp(-1,-2));

        // 内联选择条不走“长高”那套：它和底栏放在同一格、同一个高度上，展开只是盖住三个 chip
        // 那一小块，输入卡高度不变，上方会话区也就不会被顶得忽上忽下。
        composerPickerRow = hbox(); composerPickerRow.setGravity(Gravity.CENTER_VERTICAL);
        composerPickerScroll = new HorizontalScrollView(this);
        composerPickerScroll.setHorizontalScrollBarEnabled(false);
        composerPickerScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        composerPickerScroll.setVisibility(View.GONE);
        composerPickerScroll.addView(composerPickerRow, new HorizontalScrollView.LayoutParams(-2,-1));
        composerPickerScroll.setBackground(elevatedCard(12));
        composerPickerScroll.setPadding(dp(4),0,dp(4),0);
        composerPickerScroll.setOnClickListener(v -> hideComposerPicker());

        LinearLayout footer = hbox(); footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView plus = smallIcon(R.drawable.ic_plus); plus.setContentDescription("添加图片或文件"); plus.setOnClickListener(v -> showAttachMenu()); footer.addView(plus, lp(dp(36),dp(40)));
        composerModeChip = smallPill(permissionLabel(), MUTED); composerModeChip.setOnClickListener(v -> togglePermissionModeBar()); footer.addView(composerModeChip, new LinearLayout.LayoutParams(0,dp(40),1));
        composerEffortChip = smallPill("推理：" + effortLabel(config.effort), MUTED); composerEffortChip.setOnClickListener(v -> toggleEffortBar()); footer.addView(composerEffortChip, new LinearLayout.LayoutParams(0,dp(40),1));
        composerModelChip = smallPill(shorten(currentProfileName()+" · "+config.model, wide?26:16), MUTED); composerModelChip.setGravity(Gravity.CENTER); composerModelChip.setOnClickListener(v -> showModelPicker()); composerModelChip.setOnLongClickListener(v->{showApiProfileManager();return true;}); footer.addView(composerModelChip, new LinearLayout.LayoutParams(0,dp(40),1.25f));
        stopButton=iconOnly(R.drawable.ic_stop,14,Color.WHITE);stopButton.setGravity(Gravity.CENTER);stopButton.setContentDescription("立即停止当前任务");stopButton.setBackground(round(RED,12,RED,0));stopButton.setVisibility(View.GONE);stopButton.setOnClickListener(v->{if(engine!=null&&engine.isBusy()){engine.cancel();showWorkingIndicator("正在停止…");toast("正在停止当前任务");}});LinearLayout.LayoutParams stopLp=lp(dp(36),dp(36));stopLp.setMargins(0,0,dp(6),0);footer.addView(stopButton,stopLp);
        sendButton = iconOnly(R.drawable.ic_arrow_up,20,neonTheme ? Color.WHITE : contrastOn(ACCENT)); sendButton.setGravity(Gravity.CENTER); sendButton.setTypeface(Typeface.DEFAULT_BOLD); sendButton.setBackground(neonTheme ? accentGradient(13) : round(ACCENT,13, ACCENT, 0));
        sendButton.setContentDescription("发送消息；任务运行时会预输入并在当前步骤完成后加载。长按停止当前任务");
        sendButton.setOnClickListener(v -> sendPrompt());
        sendButton.setOnLongClickListener(v -> { if(engine!=null&&engine.isBusy()){engine.cancel();showWorkingIndicator("正在停止…");toast("正在停止当前任务");return true;} return false; });
        footer.addView(sendButton, lp(dp(40),dp(40)));
        FrameLayout actionSlot = new FrameLayout(this);
        actionSlot.addView(footer, new FrameLayout.LayoutParams(-1, dp(44), Gravity.CENTER_VERTICAL));
        // 左右各留出图标和发送键的位置，展开条正好压住三个 chip：附加、停止、发送始终可点。
        FrameLayout.LayoutParams pickerLp = new FrameLayout.LayoutParams(-1, dp(40), Gravity.CENTER_VERTICAL);
        pickerLp.setMargins(dp(36), 0, dp(84), 0);
        actionSlot.addView(composerPickerScroll, pickerLp);
        card.addView(actionSlot, lp(-1, dp(44))); wrap.addView(card, lp(-1, -2)); return wrap;
    }

    private void sendPrompt() {
        if (prompt == null) return;
        String p = prompt.getText().toString().trim(); if (p.isEmpty()) return;
        if (p.startsWith("/") && executeSlashCommand(p)) {
            suppressPromptWatcher = true; prompt.setText(""); suppressPromptWatcher = false;
            if (slashPaletteScroll != null) slashPaletteScroll.setVisibility(View.GONE);
            return;
        }
        boolean steering = engine != null && engine.isBusy();
        SessionRuntime promptRuntime=activeRuntime;
        if(!steering&&promptRuntime!=null&&promptRuntime.nextConfig!=null)config=promptRuntime.nextConfig.copy();
        if (!steering&&(config.apiKey == null || config.apiKey.isEmpty())) { toast("当前 API 配置缺少密钥"); showApiProfileManager(); return; }
        List<Attachment> sentImages = new ArrayList<>();
        for (Attachment a : pendingAttachments) if (a.kind == Attachment.IMAGE && a.bytes != null && a.bytes.length > 0) sentImages.add(a);
        String visible = p;
        JSONArray imageBlocks = buildImageBlocks();
        String enriched;
        if (p.startsWith("@")) {
            int cut=p.indexOf(' '); String agentName=cut>1?p.substring(1,cut).trim():p.substring(1).trim(); String delegated=cut>0?p.substring(cut+1).trim():"";
            boolean known=false; for(AgentDefinition def:engine.listAgentDefinitions())if(def.name.equalsIgnoreCase(agentName)){known=true;break;}
            if(known && !delegated.isEmpty()) enriched=buildPromptWithAttachments("Delegate this request to the `"+agentName+"` subagent using the Agent tool, wait for the result unless the agent is configured for background execution, then summarize the result for me. Delegated request:\n\n"+delegated);
            else enriched=buildPromptWithAttachments(p);
        } else enriched = buildPromptWithAttachments(p);
        pendingAttachments.clear();
        if(!steering)finalizeStreamingMessage();
        ChatItem userItem = new ChatItem(ChatItem.USER, "你", visible);
        userItem.messageId=java.util.UUID.randomUUID().toString();userItem.humanMessage=true;
        userItem.persistedBody=enriched;
        userItem.messageContentHash=persistedMessageHash(enriched,imageBlocks);
        userItem.images.addAll(sentImages);
        transcript.add(userItem);
        suppressPromptWatcher = true; prompt.setText(""); suppressPromptWatcher = false;
        rebuildAttachmentStrip(); hideSlashPalette(); hideComposerPicker();
        if(!steering){streamingItem = null; streamingView = null; streamingBodyHost = null; streamingVisibleChars = 0;}
        if (chatMessages != null) addChatView(userItem); scrollChat();
        showWorkingIndicator(steering ? "已预输入，等待当前回复完成…" : "正在思考…"); setComposerBusy(true);
        try {
            if (steering) {
                boolean wasTool = engine.isExecutingTool();
                boolean wasModel = engine.isExecutingModelRequest();
                if (!engine.steerPrompt(enriched, imageBlocks, userItem.messageId)) {
                    engine.configure(config);
                    engine.sendPrompt(enriched, imageBlocks, userItem.messageId);
                } else toast(wasTool ? "已预输入：当前工具完成后加载"
                    : wasModel ? "已预输入：当前回复完成后加载"
                    : "已预输入：当前步骤完成后加载");
            } else {
                engine.configure(config);
                if(promptRuntime!=null){promptRuntime.boundProfileId=config.profileId;promptRuntime.pendingProfileId="";promptRuntime.nextConfig=config.copy();}
                engine.sendPrompt(enriched, imageBlocks, userItem.messageId);
            }
        }
        catch (Exception e) { hideWorkingIndicator(); setComposerBusy(engine!=null&&engine.isBusy()); ChatItem err = new ChatItem(ChatItem.ERROR, "错误", String.valueOf(e.getMessage())); transcript.add(err); if (chatMessages != null) addChatView(err); }
    }

    private void updateSlashPalette(String raw) {
        if (slashPalette == null || slashPaletteScroll == null) return;
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("/") || value.contains("\n") || value.contains(" ")) {
            hideSlashPalette();
            return;
        }
        String q = value.toLowerCase(Locale.US);
        slashPalette.removeAllViews();
        int count = 0;
        for (SlashCommand cmd : SLASH_COMMANDS) {
            if (!cmd.name.startsWith(q)) continue;
            LinearLayout row = hbox(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(2), dp(8), dp(2));
            TextView name = text(cmd.name,12, TEXT); name.setTypeface(Typeface.DEFAULT_BOLD); row.addView(name, lp(dp(104),dp(38)));
            TextView hint = text(cmd.hint,10.5f, MUTED); hint.setSingleLine(true); hint.setEllipsize(android.text.TextUtils.TruncateAt.END); row.addView(hint, new LinearLayout.LayoutParams(0,dp(38),1));
            row.setOnClickListener(v -> {
                suppressPromptWatcher = true;
                prompt.setText(cmd.name + " "); prompt.setSelection(prompt.length());
                suppressPromptWatcher = false; hideSlashPalette();
            });
            slashPalette.addView(row, lp(-1, dp(42)));
            count++;
        }
        if (count == 0) { hideSlashPalette(); return; }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) slashPaletteScroll.getLayoutParams();
        if (lp != null) { lp.height = Math.min(dp(252), Math.max(dp(48), count * dp(42))); slashPaletteScroll.setLayoutParams(lp); }
        if (slashPaletteScroll.getVisibility() != View.VISIBLE) {
            hideComposerPicker();
            slashPaletteScroll.setVisibility(View.VISIBLE); slashPaletteScroll.setAlpha(0f); slashPaletteScroll.setTranslationY(dp(4));
            UiMotion.pageIn(slashPaletteScroll);
        }
        UiMotion.bindInteractive(slashPalette);
        slashPaletteScroll.post(() -> slashPaletteScroll.scrollTo(0,0));
    }

    private void hideSlashPalette() {
        if (slashPaletteScroll == null || slashPaletteScroll.getVisibility() != View.VISIBLE) return;
        UiMotion.fadeOut(slashPaletteScroll,3f,110,() -> {
            if (slashPaletteScroll != null) { slashPaletteScroll.setVisibility(View.GONE); slashPaletteScroll.setTranslationY(0f); }
        });
    }

    private boolean executeSlashCommand(String raw) {
        String line = raw == null ? "" : raw.trim();
        int sp = line.indexOf(' ');
        String cmd = (sp < 0 ? line : line.substring(0, sp)).toLowerCase(Locale.US);
        String arg = sp < 0 ? "" : line.substring(sp + 1).trim();
        switch (cmd) {
            case "/help": showCommandHelp(); return true;
            case "/compact": compactContextFromUi(arg); return true;
            case "/context": if (!arg.isEmpty()) { try { config.contextWindowTokens = parseTokenCount(arg); saveConfigQuiet(); refreshChrome(); toast("上下文窗口已设置为 " + formatTokenCount(config.contextWindowTokens)); } catch (Exception e) { toast("请输入 128k、200k、1m、1.5m 这类格式"); } } else showContextInfo(); return true;
            case "/clear":
            case "/new": newSession(); return true;
            case "/resume": showResumePicker(); return true;
            case "/model": if (!arg.isEmpty()) { config.model = arg; saveConfigQuiet(); updateComposerChips(); } else showModelPicker(); return true;
            case "/effort": if (!arg.isEmpty()) applyReasoningEffort(arg,true); else showEffortPicker(); return true;
            case "/permissions": showPermissionPicker(); return true;
            case "/root": handleRootSlash(arg); return true;
            case "/shizuku": handleShizukuSlash(arg); return true;
            case "/keepalive": handleKeepAliveSlash(arg); return true;
            case "/mcp": handleMcpSlash(arg); return true;
            case "/web": handleWebSlash(arg); return true;
            case "/terminal": focusWorkspace(VIEW_TERMINAL); return true;
            case "/sandbox": startActivity(new Intent(this, SandboxDashboardActivity.class)); return true;
            case "/opencode": startActivity(new Intent(this, com.iqge.opencode.OpenCodeActivity.class)); return true;
            case "/files": focusWorkspace(VIEW_FILES); return true;
            case "/doctor": runDiagnosticCommand("Termux 环境检查", "printf 'HOME=%s\\nPREFIX=%s\\nPATH=%s\\nTMPDIR=%s\\n' \"$HOME\" \"$PREFIX\" \"$PATH\" \"$TMPDIR\"; for x in bash pkg apt dpkg git python node bun java javac clang make cmake; do printf '%-8s ' \"$x\"; command -v \"$x\" || echo missing; done; echo; dpkg --audit 2>&1 || true", 60000); return true;
            case "/repair": runDiagnosticCommand("Termux 修复", "dpkg --configure -a; apt-get -f install -y", 600000); return true;
            case "/skills": showSkillsPicker(); return true;
            case "/status": showStatusInfo(); return true;
            case "/stats": showStatusInfo(); return true;
            case "/usage": showContextInfo(); return true;
            case "/copy": copyLatestAssistantReply(); return true;
            case "/plan": togglePlanMode(arg); return true;
            case "/config": showSettings(); return true;
            case "/canvas": showUiCanvasPanel(); return true;
            case "/memory": showMemoryPicker(); return true;
            case "/init": runInitPrompt(); return true;
            case "/tasks": showTaskFiles(); return true;
            case "/agents": showAgentsPanel(); return true;
            case "/cancel": engine.cancel(); toast("已停止当前任务"); return true;
            case "/runtime": runtimeDialog(); return true;
            default: return false;
        }
    }

    private void showCommandHelp() {
        StringBuilder b = new StringBuilder("可用的 / 指令：\n\n");
        for (SlashCommand c : SLASH_COMMANDS) b.append("` ").append(c.name).append(" ` — ").append(c.hint).append('\n');
        ChatItem item = new ChatItem(ChatItem.ASSISTANT, "元境 指令", b.toString()); transcript.add(item);
        if (chatMessages != null) { addChatView(item); scrollChatSoft(); }
    }

    private void showContextInfo() {
        int tokens = estimateContextTokensTimed(engine);
        int pct = engine == null ? 0 : engine.contextPercent();
        int threshold = engine == null ? 0 : engine.autoCompactThresholdTokens();
        int effective = engine == null ? 0 : engine.effectiveContextWindowTokens();
        int sessionHistory = engine == null ? 0 : engine.historyContextTokens();
        int fixedCost = engine == null ? 0 : engine.fixedContextTokens();
        String text = "预计上下文：**" + formatTokenCount(tokens) + " tokens** · 已使用 **" + pct + "%** / " + formatTokenCount(config.contextWindowTokens) + "。\n\n"
            + "**当前会话拆分：**本会话历史 **" + formatTokenCount(sessionHistory) + "** + 固定开销 **" + formatTokenCount(fixedCost) + "**（系统提示词 + 已启用工具的 schema）。\n固定开销每个会话都一样，所以短会话之间看着数值接近；API 返回过实际用量时优先显示该会话的真实值。\n\n"
            + "自动压缩：" + (config.autoCompact ? "已开启" : "已关闭") + " · 实际阈值 **" + formatTokenCount(threshold) + "** · 可用窗口 **" + formatTokenCount(effective) + "**。\n\n"
            + "阈值按模型输出预留（最多 20k）和 13k 安全缓冲计算；token 优先采用 API 实际用量。输入 `/compact [侧重点]` 可手动调用模型生成语义摘要。";
        ChatItem item = new ChatItem(ChatItem.ASSISTANT, "上下文", text); transcript.add(item);
        if (chatMessages != null) { addChatView(item); scrollChatSoft(); }
    }

    private void showStatusInfo() {
        File sf = engine == null ? null : engine.getSessionFile();
        String body = "**模型：** `" + config.model + "`\n\n" +
            "**协议：** `" + config.protocol + "` · **推理强度：** " + effortLabel(config.effort) + "\n\n" +
            "**项目：** `" + config.projectDirectory + "`\n\n" +
            "**上下文：** " + (engine == null ? 0 : engine.contextPercent()) + "% · 自动压缩 " + (config.autoCompact ? "开启" : "关闭") +
            "\n\n**联网搜索：** " + (config.webSearchEnabled ? "开启（" + config.webSearchProvider + "）" : "关闭") +
            "\n\n**运行环境：** " + (runtime.isInstalled() ? "已就绪" : "未安装") +
            "\n\n**IQ 沙箱：** " + IQSandboxEngine.status() +
            "\n\n**会话：** `" + (sf == null ? "未开始" : sf.getName()) + "`";
        ChatItem item = new ChatItem(ChatItem.ASSISTANT, "状态", body); transcript.add(item);
        if (chatMessages != null) { addChatView(item); scrollChatSoft(); }
    }

    private void copyLatestAssistantReply() {
        for (int i = transcript.size() - 1; i >= 0; i--) {
            ChatItem item = transcript.get(i);
            if (item.type == ChatItem.ASSISTANT && item.body.length() > 0) {
                copyTextToClipboard("元境 latest response", item.body.toString());
                return;
            }
        }
        toast("还没有可复制的 IQ 回复");
    }

    private void togglePlanMode(String argument) {
        String raw=argument==null?"":argument.trim();String arg=raw.toLowerCase(Locale.US);PlanWorkflowState state=engine.getPlanWorkflowState();
        if("open".equals(arg)||"打开".equals(arg)){if(state.planFile==null||state.planFile.isEmpty())toast("当前还没有计划文件");else openEditor(new File(state.planFile));return;}
        if("off".equals(arg)||"exit".equals(arg)||"关闭".equals(arg)){ToolExecutionResult result=engine.cancelPlanModeFromUi("用户通过 /plan off 退出");toast(result.isError?result.content:"已退出计划模式");return;}
        if(state.isPlanning()||state.isAwaitingApproval()){if(state.planText==null||state.planText.trim().isEmpty())toast("正在计划模式：继续调研并提交计划");else showPlanPreview(state);return;}
        ToolExecutionResult result=engine.enterPlanModeFromUi();updateComposerChips();toast(result.isError?result.content:"已进入计划模式：只读调研后提交计划审批");
    }

    private void showPlanPreview(PlanWorkflowState state){final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=dialogPanel();panel.addView(dialogTitle("当前计划 · revision "+state.revision),lp(-1,dp(48)));TextView path=text(state.planFile,11.5f,MUTED_2);path.setTypeface(Typeface.MONOSPACE);panel.addView(path,lp(-1,dp(34)));panel.addView(scrollBody(MarkdownRenderer.render(this,state.planText,12.5f,neonTheme,lightTheme),dialogWidthPx(560),dialogMaxBodyPx()));TextView close=dialogButton("关闭",TEXT,true);close.setOnClickListener(v->d.dismiss());panel.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(560),-2);}

    private void handleWebSlash(String arg) {
        String a = arg == null ? "" : arg.trim();
        if (a.isEmpty()) { showWebPanel(); return; }
        if ("on".equalsIgnoreCase(a) || "开启".equals(a)) { config.webSearchEnabled=true; saveConfigQuiet(); toast("联网搜索已开启"); return; }
        if ("off".equalsIgnoreCase(a) || "关闭".equals(a)) { config.webSearchEnabled=false; saveConfigQuiet(); toast("联网搜索已关闭"); return; }
        if (a.toLowerCase(Locale.US).startsWith("fetch ")) { runManualWebFetch(a.substring(6).trim()); return; }
        runManualWebSearch(a);
    }

    private void showWebPanel() {
        final Dialog d=newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(dialogTitle("联网搜索"),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView close=iconButton(R.drawable.ic_close); close.setOnClickListener(v->d.dismiss()); top.addView(close,lp(dp(44),dp(44))); panel.addView(top,lp(-1,dp(48)));
        TextView status=text(config.webSearchEnabled?"已开启 · IQ 可自动调用 WebSearch / WebFetch":"已关闭 · Agent 不会看到联网工具",12,config.webSearchEnabled?GREEN:MUTED); status.setPadding(dp(4),0,dp(4),dp(10)); panel.addView(status,lp(-1,-2));
        TextView toggle=dialogButton(config.webSearchEnabled?"关闭联网搜索":"开启联网搜索",TEXT,true); toggle.setOnClickListener(v->{config.webSearchEnabled=!config.webSearchEnabled; saveConfigQuiet(); d.dismiss(); showWebPanel();}); panel.addView(toggle,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView provider=text("搜索后端：" + webProviderLabel(config.webSearchProvider),12,MUTED); provider.setPadding(dp(4),dp(14),dp(4),dp(4)); panel.addView(provider,lp(-1,dp(40)));
        TextView hint=text("直接输入 `/web Android 16 新特性` 可手动搜索；`/web fetch https://...` 可抓取网页正文。Agent 在需要最新资料时也会自动调用。",11.5f,MUTED_2); hint.setLineSpacing(0,1.12f); hint.setPadding(dp(4),dp(10),dp(4),0); panel.addView(hint,lp(-1,-2));
        d.setContentView(panel); d.show(); styleDialogWindow(d,dialogWidthPx(440),-2);
    }

    private String webProviderLabel(String value) {
        if ("duckduckgo".equalsIgnoreCase(value)) return "DuckDuckGo";
        if ("bing".equalsIgnoreCase(value)) return "Bing RSS";
        return "自动（DuckDuckGo → Bing）";
    }

    private void runManualWebSearch(String query) {
        if (!config.webSearchEnabled) { toast("联网搜索已关闭，可输入 /web on 开启"); return; }
        if (query==null || query.trim().isEmpty()) { showWebPanel(); return; }
        final ChatItem wait=new ChatItem(ChatItem.ASSISTANT,"联网搜索","正在搜索：`"+query.trim()+"` …"); transcript.add(wait); if(chatMessages!=null){addChatView(wait);scrollChatSoft();}
        io.submit(()->{ ToolExecutionResult r; try { r=new WebSearchTool().execute(config.copy(),new JSONObject().put("query",query.trim())); } catch(Throwable e){ r=ToolExecutionResult.error(e.getMessage()==null?e.toString():e.getMessage()); }
            final ToolExecutionResult out=r; uiHandler.post(()->{ wait.body.setLength(0); wait.body.append(out.content); wait.completed=true; scheduleChatRender(); }); });
    }

    private void runManualWebFetch(String url) {
        if (!config.webSearchEnabled) { toast("联网搜索已关闭，可输入 /web on 开启"); return; }
        if (url==null || url.trim().isEmpty()) { toast("用法：/web fetch https://example.com"); return; }
        final ChatItem wait=new ChatItem(ChatItem.ASSISTANT,"网页读取","正在读取：`"+url.trim()+"` …"); transcript.add(wait); if(chatMessages!=null){addChatView(wait);scrollChatSoft();}
        io.submit(()->{ ToolExecutionResult r; try { r=new WebFetchTool().execute(config.copy(),new JSONObject().put("url",url.trim())); } catch(Throwable e){ r=ToolExecutionResult.error(e.getMessage()==null?e.toString():e.getMessage()); }
            final ToolExecutionResult out=r; uiHandler.post(()->{ wait.body.setLength(0); wait.body.append(out.content); wait.completed=true; scheduleChatRender(); }); });
    }

    private void showMemoryPicker() {
        List<File> files = new ArrayList<>();
        File project = new File(config.projectDirectory, "IQ.md"); if (project.isFile()) files.add(project);
        File user = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".iq/IQ.md"); if (user.isFile()) files.add(user);
        if (files.isEmpty()) { toast("未找到 IQ.md，可使用 /init 创建项目说明"); return; }
        String[] labels = new String[files.size()];
        for (int i=0;i<files.size();i++) labels[i] = files.get(i).equals(project) ? "项目 IQ.md" : "用户 IQ.md";
        showChoicePicker("记忆文件", labels, -1, which -> openEditor(files.get(which)));
    }

    private void runInitPrompt() {
        if (engine.isBusy()) { toast("IQ 正在执行任务，请先等待或停止当前任务"); return; }
        String visible = "/init";
        if(engine.getPlanWorkflowState().isPlanning())engine.cancelPlanModeFromUi("/init 使用快速直接模式");
        String instruction = "This is the built-in fast /init maintenance command. Do not enter plan mode, create tasks, launch subagents, or perform broad repository research. Read only the existing IQ.md/CLAUDE.md/README and primary build manifest or script when present, then directly create or improve IQ.md with concise build, test, architecture, conventions, and repository-specific instructions. Preserve useful existing instructions and run at most one small verification command.";
        transcript.add(new ChatItem(ChatItem.USER, "你", visible));
        if (wide || currentView == VIEW_CHAT) renderChat(primaryHost);
        try { engine.configure(config); engine.sendPrompt(instruction); } catch (Exception e) { toast(e.getMessage()); }
    }

    private void showAgentsPanel() {
        final Dialog d = newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(dialogTitle("Agents"),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView close=iconButton(R.drawable.ic_close); close.setOnClickListener(v->d.dismiss()); top.addView(close,lp(dp(44),dp(44))); panel.addView(top,lp(-1,dp(48)));
        TextView hint=text("内置 Agent 始终可用 · 点击 Agent 后用 @名称 调用",11.5f,MUTED); panel.addView(hint,lp(-1,dp(36)));
        ScrollView sv=new ScrollView(this); sv.setVerticalScrollBarEnabled(false); LinearLayout rows=vbox();
        List<AgentTask> running=engine.listAgentTasks();
        TextView rh=text("运行中",11.5f,MUTED_2); rh.setTypeface(Typeface.DEFAULT_BOLD); rh.setPadding(0,dp(10),0,dp(4)); rows.addView(rh,lp(-1,dp(38)));
        boolean any=false;
        for(AgentTask task:running){
            any=true; LinearLayout row=hbox(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8),dp(4),dp(4),dp(4));
            String state="running".equals(task.status)?"●":"completed".equals(task.status)?"✓":"×";
            int color="running".equals(task.status)?ACCENT:"completed".equals(task.status)?GREEN:RED;
            String stateLabel="running".equals(task.status)?"运行中":"completed".equals(task.status)?"已完成":"已失败";
            TextView info=text(state+"  "+task.agentType+"\n"+task.description+" · "+stateLabel,12,TEXT); info.setLineSpacing(0,1.08f); row.addView(info,new LinearLayout.LayoutParams(0,dp(56),1));
            if("running".equals(task.status)){TextView stop=dialogButton("停止",RED,false);stop.setOnClickListener(v->{engine.stopAgentTask(task.id);toast("已停止 "+task.id);d.dismiss();showAgentsPanel();});row.addView(stop,new LinearLayout.LayoutParams(dp(76),dp(44)));}
            else {TextView tag=text(task.id,11.5f,MUTED_2);tag.setGravity(Gravity.CENTER);row.addView(tag,new LinearLayout.LayoutParams(dp(76),dp(44)));}
            rows.addView(row,lp(-1,dp(68)));
        }
        if(!any){TextView empty=text("当前没有运行中的子 Agent",11.5f,MUTED_2);empty.setPadding(dp(8),0,0,0);empty.setGravity(Gravity.CENTER_VERTICAL);rows.addView(empty,lp(-1,dp(48)));}
        TextView lh=text("可用 Agents",11.5f,MUTED_2); lh.setTypeface(Typeface.DEFAULT_BOLD); lh.setPadding(0,dp(16),0,dp(4)); rows.addView(lh,lp(-1,dp(46)));
        List<AgentDefinition> defs=engine.listAgentDefinitions();String lastSource="";
        for(AgentDefinition def:defs){
            String sourceLabel=agentSourceLabel(def.source);if(!sourceLabel.equals(lastSource)){TextView group=text(sourceLabel,10.5f,MUTED_2);group.setTypeface(Typeface.DEFAULT_BOLD);group.setPadding(dp(4),dp(8),0,dp(4));rows.addView(group,lp(-1,dp(30)));lastSource=sourceLabel;}
            LinearLayout row=vbox(); row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));
            String meta=agentSourceLabel(def.source)+(def.model==null||def.model.isEmpty()||"inherit".equals(def.model)?"":" · "+def.model)+(def.background?" · 后台":"");
            TextView n=text(def.name,12.5f,TEXT); n.setTypeface(Typeface.DEFAULT_BOLD); row.addView(n,lp(-1,dp(26)));
            TextView metaView=text(meta,11,MUTED_2);row.addView(metaView,lp(-1,dp(22)));
            TextView desc=text(def.description==null||def.description.isEmpty()?"自定义子 Agent":def.description,11.5f,MUTED);desc.setMaxLines(2);desc.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(desc,lp(-1,dp(42)));
            row.setOnClickListener(v->{d.dismiss();suppressPromptWatcher=true;prompt.setText("@"+def.name+" ");prompt.setSelection(prompt.length());suppressPromptWatcher=false;prompt.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(prompt,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,dp(89));rowLp.setMargins(0,0,0,dp(6));rows.addView(row,rowLp);
        }
        TextView foot=text("自定义 Agent：.iq/agents/*.md 或 ~/.iq/agents/*.md",11.5f,MUTED_2);foot.setPadding(dp(8),dp(10),0,dp(8));rows.addView(foot,lp(-1,-2));
        panel.addView(scrollBody(rows,dialogWidthPx(480),dialogMaxBodyPx()));
        d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(480),-2);
    }

    private String agentSourceLabel(String source){
        if("built-in".equals(source))return "内置 · 始终可用";
        if("project".equals(source)||"projectSettings".equals(source))return "项目 Agent";
        if("user".equals(source)||"userSettings".equals(source))return "用户 Agent";
        if(source==null||source.isEmpty())return "自定义 Agent";
        return source;
    }

    private void showTaskFiles() {
        File root = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".iq");
        StringBuilder b = new StringBuilder("本地 IQ Android 状态：\n\n");
        appendTree(b, root, "", 0, 3);
        ChatItem item = new ChatItem(ChatItem.ASSISTANT, "任务文件", "```text\n" + b + "```"); transcript.add(item);
        if (chatMessages != null) { addChatView(item); scrollChatSoft(); }
    }

    private void appendTree(StringBuilder out, File file, String indent, int depth, int maxDepth) {
        if (file == null || depth > maxDepth || !file.exists()) return;
        if (depth > 0) out.append(indent).append(file.getName()).append(file.isDirectory() ? "/" : "").append('\n');
        if (!file.isDirectory() || depth == maxDepth) return;
        File[] children = file.listFiles(); if (children == null) return;
        Arrays.sort(children, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        int shown = 0;
        for (File child : children) { if (shown++ >= 40) { out.append(indent).append("  …\n"); break; } appendTree(out, child, indent + "  ", depth + 1, maxDepth); }
    }

    private void compactContextFromUi(String instructions) {
        if (engine == null) return;
        if (engine.isBusy()) { toast("请等待当前任务结束后再压缩上下文"); return; }
        setComposerBusy(true);
        showWorkingIndicator("正在调用模型整理上下文…");
        io.execute(() -> {
            try {
                String result = engine.compactContext(instructions);
                ui(() -> { ChatItem item = new ChatItem(ChatItem.ASSISTANT, "上下文压缩", "✓ " + result); stampCurrentContext(item, engine); transcript.add(item); if (chatMessages != null) { addChatView(item); scrollChatSoft(); } refreshChrome(); });
            } catch (Exception e) {
                ui(() -> { hideWorkingIndicator(); setComposerBusy(false); toast(e.getMessage() == null ? e.toString() : e.getMessage()); });
            }
        });
    }

    private void runDiagnosticCommand(String title, String command, int timeout) {
        ChatItem item = new ChatItem(ChatItem.TOOL, "Bash", new JSONObjectSafe().put("command", command).put("cwd", config.projectDirectory).put("timeout_ms", timeout).toString());
        transcript.add(item); if (chatMessages != null) addChatView(item); scrollChatSoft();
        io.execute(() -> {
            try {
                TermuxShellExecutor.Result r = new TermuxShellExecutor(this).execute(command, config.projectDirectory, timeout);
                ui(() -> { item.result.append(r.combined()); item.completed = true; item.exitCode = r.exitCode; item.resultError = r.exitCode != 0; refreshToolItem(item); });
            } catch (Exception e) {
                ui(() -> { item.result.append(e.toString()); item.completed = true; item.resultError = true; item.exitCode = -1; refreshToolItem(item); });
            }
        });
    }

    /** Tiny helper to avoid checked JSONException noise for UI-only tool cards. */
    private static final class JSONObjectSafe {
        private final JSONObject object = new JSONObject();
        JSONObjectSafe put(String k, Object v) { try { object.put(k, v); } catch (Exception ignored) { } return this; }
        @Override public String toString() { return object.toString(); }
    }

    private String buildPromptWithAttachments(String promptText) {
        if (pendingAttachments.isEmpty()) return promptText;
        boolean hasText=false; for(Attachment a:pendingAttachments) if(a.kind==Attachment.TEXT){hasText=true;break;}
        if(!hasText) return promptText;
        StringBuilder b = new StringBuilder(promptText).append("\n\n<attached_context>");
        for (Attachment a : pendingAttachments) {
            if (a.kind != Attachment.TEXT) continue;
            b.append("\n<attachment name=\"").append(a.label.replace("\"", "'")).append("\">\n");
            b.append(a.body.length() > 60000 ? a.body.substring(0,60000) + "\n[…attachment truncated…]" : a.body);
            b.append("\n</attachment>");
        }
        return b.append("\n</attached_context>").toString();
    }

    private JSONArray buildImageBlocks() {
        JSONArray blocks = new JSONArray();
        for (Attachment a : pendingAttachments) {
            if (a.kind != Attachment.IMAGE || a.bytes == null || a.bytes.length == 0) continue;
            try {
                String data = Base64.encodeToString(a.bytes, Base64.NO_WRAP);
                JSONObject source = new JSONObject().put("type", "base64").put("media_type", a.mimeType).put("data", data);
                blocks.put(new JSONObject().put("type", "image").put("source", source).put("name", a.label));
            } catch (Exception ignored) { }
        }
        return blocks;
    }

    private String persistedMessageHash(String text, JSONArray extraContent) {
        try {
            JSONArray content = new JSONArray();
            if (text != null && !text.isEmpty()) content.put(new JSONObject().put("type", "text").put("text", text));
            if (extraContent != null) for (int i = 0; i < extraContent.length(); i++) content.put(extraContent.get(i));
            return SessionStore.messageContentHash(content);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void rebuildAttachmentStrip() {
        if (attachmentStrip == null) return;
        attachmentStrip.removeAllViews();
        attachmentStrip.setVisibility(pendingAttachments.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < pendingAttachments.size(); i++) {
            final int index = i; Attachment a = pendingAttachments.get(i);
            LinearLayout chip = hbox(); chip.setGravity(Gravity.CENTER_VERTICAL); chip.setPadding(dp(6),dp(2),dp(6),dp(2)); chip.setBackground(round(SURFACE_3,10,Color.TRANSPARENT,0));
            if (a.kind == Attachment.IMAGE && a.bytes != null) {
                ImageView thumb = new ImageView(this);
                try { Bitmap bm = BitmapFactory.decodeByteArray(a.bytes,0,a.bytes.length); thumb.setImageBitmap(bm); } catch (Throwable ignored) { }
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP); chip.addView(thumb,lp(dp(34),dp(34)));
            } else {
                TextView glyph=iconOnly(R.drawable.ic_plus,14,ACCENT);chip.addView(glyph,lp(dp(28),dp(30)));
            }
            TextView name=text(shorten(a.label,36),10.5f,TEXT); name.setGravity(Gravity.CENTER_VERTICAL); chip.addView(name,new LinearLayout.LayoutParams(0,dp(34),1));
            TextView remove=iconOnly(R.drawable.ic_close,14,MUTED); remove.setOnClickListener(v -> { if (index < pendingAttachments.size()) pendingAttachments.remove(index); rebuildAttachmentStrip(); }); chip.addView(remove,lp(dp(32),dp(34)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,a.kind==Attachment.IMAGE?dp(44):dp(36)); cp.setMargins(0,0,0,dp(4)); attachmentStrip.addView(chip,cp);
        }
        UiMotion.bindInteractive(attachmentStrip);
        UiMotion.staggerChildren(attachmentStrip, 5);
    }

    private void showAttachMenu() {
        final Dialog d = newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(dialogTitle("添加上下文"),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView close=iconButton(R.drawable.ic_close); close.setOnClickListener(v->d.dismiss()); top.addView(close,lp(dp(44),dp(44))); panel.addView(top,lp(-1,dp(48)));
        panel.addView(contextAction(R.drawable.ic_files, "附加项目文件", "搜索项目文件，可多次附加到下一条消息", v->{d.dismiss();showProjectFilePicker();}));
        panel.addView(contextAction(R.drawable.ic_skill, "Skill 管理器", "查看、编辑、新建、删除并附加 SKILL.md", v->{d.dismiss();showSkillManager();}));
        panel.addView(contextAction(R.drawable.ic_code, "打开文件工作区", "浏览、增删改文件并使用内联代码编辑器", v->{d.dismiss();focusWorkspace(VIEW_FILES);}));
        panel.addView(contextAction(R.drawable.ic_photo, "上传照片", "从相册或文件中选择图片，作为视觉上下文发送", v->{d.dismiss();launchImagePicker();}));
        TextView tip=text("已附加内容只会加入下一条消息；发送后自动清空。",11.5f,MUTED_2); tip.setPadding(dp(8),dp(10),dp(8),0); panel.addView(tip,lp(-1,-2));
        d.setContentView(panel); d.show(); styleDialogWindow(d,dialogWidthPx(460),-2);
    }

    /** One entry of a sheet menu: a 60dp row so the whole line is a comfortable thumb target. */
    private View contextAction(int iconRes,String title,String subtitle,View.OnClickListener click){
        LinearLayout row=hbox(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8),dp(8),dp(8),dp(8));
        row.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.setMargins(0,0,0,dp(8));row.setLayoutParams(rowLp);
        TextView i=iconOnly(iconRes,22,ACCENT); row.addView(i,lp(dp(42),dp(54)));
        LinearLayout copy=vbox(); TextView t=text(title,12.5f,TEXT);t.setTypeface(Typeface.DEFAULT_BOLD);copy.addView(t,lp(-1,dp(26)));TextView sub=text(subtitle,11.5f,MUTED);sub.setMaxLines(2);copy.addView(sub,lp(-1,-2));row.addView(copy,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView arrow=iconOnly(R.drawable.ic_chevron_right,18,MUTED_2);row.addView(arrow,lp(dp(28),dp(54)));row.setOnClickListener(click);return row;
    }

    private void showProjectFilePicker(){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);head.addView(dialogTitle("附加项目文件"),new LinearLayout.LayoutParams(0,dp(48),1));TextView x=iconButton(R.drawable.ic_close);x.setOnClickListener(v->d.dismiss());head.addView(x,lp(dp(44),dp(44)));panel.addView(head,lp(-1,dp(48)));
        EditText search=input("",false);search.setHint("搜索文件名或路径");panel.addView(search,lp(-1,dp(52)));
        TextView root=text("项目："+config.projectDirectory,11.5f,MUTED_2);root.setSingleLine(true);root.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);root.setPadding(dp(4),dp(8),dp(4),dp(6));panel.addView(root,lp(-1,dp(34)));
        ListView list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        final List<File> all=listProjectFiles(new File(config.projectDirectory),700); final List<File> shown=new ArrayList<>();
        Runnable refresh=()->{String q=search.getText().toString().trim().toLowerCase(Locale.US);shown.clear();List<String> names=new ArrayList<>();for(File f:all){String rel=relativeProjectPath(f);if(q.isEmpty()||rel.toLowerCase(Locale.US).contains(q)){shown.add(f);names.add(fileGlyph(f)+"  "+rel);if(names.size()>=180)break;}}ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){@Override public View getView(int p,View c,ViewGroup parent){TextView t=(TextView)super.getView(p,c,parent);t.setTextColor(TEXT);t.setTextSize(13);t.setTypeface(Typeface.MONOSPACE);t.setGravity(Gravity.CENTER_VERTICAL);t.setMinHeight(dp(52));t.setPadding(dp(12),dp(6),dp(12),dp(6));return t;}};list.setAdapter(a);};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();
        list.setOnItemClickListener((p,v,pos,id)->{if(pos<shown.size()){attachFileDirect(shown.get(pos));toast("已附加："+shown.get(pos).getName());}});
        TextView done=dialogButton("完成",TEXT,true);done.setOnClickListener(v->d.dismiss());panel.addView(done,lp(-1,dp(48)));
        d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(580),Math.min(dp(700),(int)(getResources().getDisplayMetrics().heightPixels*.84f)));
    }

    private List<File> listProjectFiles(File root,int limit){List<File> out=new ArrayList<>();ArrayList<File> stack=new ArrayList<>();stack.add(root);while(!stack.isEmpty()&&out.size()<limit){File dir=stack.remove(stack.size()-1);File[] xs=dir.listFiles();if(xs==null)continue;Arrays.sort(xs,(a,b)->a.getName().compareToIgnoreCase(b.getName()));for(File f:xs){String n=f.getName();if(n.equals(".git")||n.equals("node_modules")||n.equals("build")||n.equals(".gradle"))continue;if(f.isDirectory())stack.add(f);else out.add(f);if(out.size()>=limit)break;}}return out;}

    private void showSkillsPicker(){ showSkillManager(); }

    private void showSkillManager(){
        if(wide){showSkillCenter();return;}
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout hcopy=vbox();hcopy.addView(dialogTitle("Skill 管理器"),lp(-1,dp(32)));TextView sub=text("项目级 .iq/skills 与用户级 ~/.iq/skills",11.5f,MUTED);hcopy.addView(sub,lp(-1,dp(24)));head.addView(hcopy,new LinearLayout.LayoutParams(0,dp(58),1));TextView add=dialogButton("＋ 新建",ACCENT,false);add.setOnClickListener(v->{d.dismiss();showNewSkillDialog();});head.addView(add,new LinearLayout.LayoutParams(dp(92),dp(44)));TextView x=iconButton(R.drawable.ic_close);x.setOnClickListener(v->d.dismiss());head.addView(x,lp(dp(44),dp(44)));panel.addView(head,lp(-1,dp(62)));
        ScrollView sv=new ScrollView(this);LinearLayout rows=vbox();List<File> skills=discoverSkills();if(skills.isEmpty()){TextView empty=text("还没有 Skill。点“＋ 新建”创建一个 SKILL.md。",12,MUTED);empty.setGravity(Gravity.CENTER);rows.addView(empty,lp(-1,dp(110)));}else for(File f:skills)rows.addView(skillRow(f,d));sv.addView(rows);panel.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(600),Math.min(dp(720),(int)(getResources().getDisplayMetrics().heightPixels*.86f)));
    }

    private View skillRow(File f,Dialog owner){return skillRow(f,owner,null);}

    /** Skill list entry; {@code onEdit} non-null routes 编辑 into the tablet detail pane instead of a sheet. */
    private View skillRow(File f,Dialog owner,Runnable onEdit){
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(6),dp(6),dp(6),dp(6));
        TextView glyph=iconOnly(R.drawable.ic_skill,18,ACCENT);row.addView(glyph,lp(dp(38),dp(52)));
        LinearLayout copy=vbox();String name=f.getParentFile().getName();TextView t=text(name,12.5f,TEXT);t.setTypeface(Typeface.DEFAULT_BOLD);copy.addView(t,lp(-1,dp(26)));boolean project=f.getAbsolutePath().startsWith(new File(config.projectDirectory).getAbsolutePath());TextView meta=text((project?"项目级":"用户级")+" · "+shorten(f.getAbsolutePath(),54),11.5f,MUTED);copy.addView(meta,lp(-1,dp(24)));row.addView(copy,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView attach=dialogButton("附加",GREEN,false);attach.setOnClickListener(v->{try{pendingAttachments.add(new Attachment("skill:"+name,new String(readFile(f),StandardCharsets.UTF_8)));rebuildAttachmentStrip();toast("已附加 Skill："+name);}catch(Exception e){toast("读取失败："+e.getMessage());}});row.addView(attach,new LinearLayout.LayoutParams(dp(72),dp(44)));
        TextView edit=dialogButton("编辑",TEXT,false);edit.setOnClickListener(v->{if(onEdit!=null)onEdit.run();else{owner.dismiss();showSkillEditor(f);}});row.addView(edit,new LinearLayout.LayoutParams(dp(72),dp(44)));return row;
    }

    private void showNewSkillDialog(){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout p=dialogPanel();p.addView(dialogTitle("新建 Skill"),lp(-1,dp(48)));EditText name=input("",false);name.setHint("skill-name");p.addView(labeled("名称",name));Spinner scope=darkSpinner(new String[]{"项目级","用户级"},new String[]{"project","user"},"project");p.addView(labeled("保存位置",scope));TextView create=dialogButton("创建并编辑",TEXT,true);create.setOnClickListener(v->{try{String n=name.getText().toString().trim();if(!n.matches("[A-Za-z0-9._-]{1,64}"))throw new IllegalArgumentException("名称只能包含字母、数字、._-");File root=scope.getSelectedItemPosition()==0?new File(config.projectDirectory,".iq/skills"):new File(TermuxConstants.TERMUX_HOME_DIR_PATH,".iq/skills");File dir=new File(root,n);if(!dir.mkdirs()&&!dir.isDirectory())throw new Exception("无法创建目录");File f=new File(dir,"SKILL.md");if(!f.exists()){String template="---\\nname: "+n+"\\ndescription: 请描述这个 Skill 的用途\\n---\\n\\n# "+n+"\\n\\n在这里编写给 IQ 的技能说明。\\n";writeFileBytes(f,template.getBytes(StandardCharsets.UTF_8));}d.dismiss();showSkillEditor(f);}catch(Exception e){toast(e.getMessage());}});p.addView(create,new LinearLayout.LayoutParams(-1,dp(48)));d.setContentView(p);d.show();styleFormDialog(d,dialogWidthPx(440));
    }

    private void showSkillEditor(File f){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=dialogPanel();LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);head.addView(dialogTitle("编辑 Skill · "+f.getParentFile().getName()),new LinearLayout.LayoutParams(0,dp(48),1));TextView x=iconButton(R.drawable.ic_close);x.setOnClickListener(v->d.dismiss());head.addView(x,lp(dp(44),dp(44)));panel.addView(head,lp(-1,dp(48)));
        EditText editor=skillEditorField(f);panel.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        panel.addView(skillFoot(f,d,editor),lp(-1,dp(60)));
        d.setContentView(panel);d.show();styleFormDialog(d,dialogWidthPx(720),Math.min(dp(820),(int)(getResources().getDisplayMetrics().heightPixels*.90f)));
    }

    /** Markdown editor with live re-highlighting, shared by the phone sheet and the tablet detail pane. */
    private EditText skillEditorField(File f){
        EditText editor=new EditText(this);editor.setTextColor(TEXT);editor.setHintTextColor(MUTED_2);editor.setTextSize(11);editor.setTypeface(Typeface.MONOSPACE);editor.setGravity(Gravity.TOP|Gravity.START);editor.setPadding(dp(12),dp(10),dp(12),dp(10));editor.setBackground(round(SURFACE,14,Color.TRANSPARENT,0));editor.setHorizontallyScrolling(true);editor.setHorizontalScrollBarEnabled(true);try{editor.setText(MarkdownRenderer.highlight(new String(readFile(f),StandardCharsets.UTF_8),"markdown",neonTheme,lightTheme));}catch(Exception e){editor.setText("");toast("读取失败："+e.getMessage());}
        final boolean[] styling={false};final Runnable[] styleTask={null};editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence c,int a,int b,int d){}public void onTextChanged(CharSequence c,int a,int b,int d){if(styling[0])return;if(styleTask[0]!=null)uiHandler.removeCallbacks(styleTask[0]);styleTask[0]=()->{int st=editor.getSelectionStart(),en=editor.getSelectionEnd();String raw=editor.getText().toString();styling[0]=true;editor.setText(MarkdownRenderer.highlight(raw,"markdown",neonTheme,lightTheme));int len=editor.length();editor.setSelection(Math.max(0,Math.min(st,len)),Math.max(0,Math.min(en,len)));styling[0]=false;};uiHandler.postDelayed(styleTask[0],220);}public void afterTextChanged(Editable e){}});        return editor;
    }

    private LinearLayout skillFoot(File f,Dialog d,EditText editor){
        LinearLayout foot=hbox();foot.setGravity(Gravity.CENTER_VERTICAL);TextView del=dialogButton("删除",RED,false);del.setOnClickListener(v->confirmDeleteSkill(f,d));foot.addView(del,new LinearLayout.LayoutParams(dp(88),dp(48)));View spacer=new View(this);foot.addView(spacer,new LinearLayout.LayoutParams(0,1,1));TextView attach=dialogButton("保存并附加",GREEN,false);attach.setOnClickListener(v->{try{String body=editor.getText().toString();writeFileBytes(f,body.getBytes(StandardCharsets.UTF_8));pendingAttachments.add(new Attachment("skill:"+f.getParentFile().getName(),body));rebuildAttachmentStrip();toast("已保存并附加");d.dismiss();}catch(Exception e){toast("保存失败："+e.getMessage());}});foot.addView(attach,new LinearLayout.LayoutParams(dp(120),dp(48)));TextView save=dialogButton("保存",TEXT,true);save.setOnClickListener(v->{try{writeFileBytes(f,editor.getText().toString().getBytes(StandardCharsets.UTF_8));toast("Skill 已保存");}catch(Exception e){toast("保存失败："+e.getMessage());}});foot.addView(save,new LinearLayout.LayoutParams(dp(88),dp(48)));        return foot;
    }

    /** Tablet skill manager: the skill list on the left, the editor for the selected skill on the right. */
    private void showSkillCenter(){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=vbox();root.setPadding(dp(16),dp(14),dp(16),dp(14));root.setBackground(round(SURFACE_2,20,Color.TRANSPARENT,0));
        LinearLayout columns=hbox();

        LinearLayout left=vbox();left.setPadding(dp(12),dp(10),dp(12),dp(10));left.setBackground(round(SURFACE,18,BORDER,1));
        left.addView(dialogTitle("Skill 管理器"),lp(-1,dp(44)));
        TextView add=dialogButton("＋ 新建",ACCENT,false);add.setOnClickListener(v->{d.dismiss();showNewSkillDialog();});left.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout rows=vbox();List<File> skills=discoverSkills();
        ScrollView listScroll=new ScrollView(this);listScroll.setVerticalScrollBarEnabled(true);listScroll.addView(rows,new ScrollView.LayoutParams(-1,-2));
        left.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=text("项目级 .iq/skills 与用户级 ~/.iq/skills",11.5f,MUTED_2);hint.setPadding(dp(2),dp(6),dp(2),0);left.addView(hint,lp(-1,dp(26)));

        LinearLayout detail=vbox();detail.setPadding(dp(14),dp(10),dp(4),dp(10));
        fillSkillEditorPane(detail,null,d);
        for(File f:skills)rows.addView(skillRow(f,d,()->fillSkillEditorPane(detail,f,d)));
        if(skills.isEmpty()){TextView empty=text("还没有 Skill。点“＋ 新建”创建一个 SKILL.md。",12,MUTED);empty.setGravity(Gravity.CENTER);rows.addView(empty,lp(-1,dp(110)));}

        columns.addView(left,new LinearLayout.LayoutParams(dp(300),-1));
        columns.addView(detail,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        d.setContentView(root);d.show();
        styleFormDialog(d,dialogWidthPx(760),Math.min(dp(760),(int)(getResources().getDisplayMetrics().heightPixels*.88f)));
    }

    private void fillSkillEditorPane(LinearLayout host,File f,Dialog d){
        host.removeAllViews();
        if(f==null){TextView empty=text("从左侧选择一个 Skill 开始编辑。",12,MUTED_2);empty.setGravity(Gravity.CENTER);host.addView(empty,new LinearLayout.LayoutParams(-1,0,1));return;}
        host.addView(dialogTitle("编辑 Skill · "+f.getParentFile().getName()),lp(-1,dp(48)));
        EditText editor=skillEditorField(f);host.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        host.addView(skillFoot(f,d,editor),lp(-1,dp(60)));
    }

    private void confirmDeleteSkill(File f,Dialog editorDialog){
        showAnimatedAlert(new AlertDialog.Builder(this).setTitle("删除 Skill？").setMessage(f.getParentFile().getName()+"\\n删除后无法撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(x,w)->{deleteRecursive(f.getParentFile());if(editorDialog!=null)editorDialog.dismiss();toast("Skill 已删除");showSkillManager();}).create());
    }

    private void launchImagePicker(){
        try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,REQ_PICK_IMAGE);}catch(Exception e){toast("无法打开图片选择器："+e.getMessage());}
    }

    private void launchThemeImport(){
        try{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
            startActivityForResult(i,REQ_IMPORT_THEME);
        }catch(Exception e){toast("无法打开文件选择器："+e.getMessage());}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_IMPORT_THEME){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            ui(()->{
                try{
                    String id=ThemePluginStore.importFrom(this,data.getData());
                    toast("已导入主题插件");
                    saveAndApplyUiTheme("plugin:"+id);
                }catch(Exception e){toast("导入失败："+e.getMessage());}
            });
            return;
        }
        if(requestCode!=REQ_PICK_IMAGE||resultCode!=RESULT_OK||data==null)return;
        ArrayList<Uri> uris=new ArrayList<>();
        if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++){Uri u=data.getClipData().getItemAt(i).getUri();if(u!=null)uris.add(u);}}
        else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;
        for(Uri uri:uris){try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}}
        io.execute(()->{
            final List<Attachment> added=new ArrayList<>();String firstError=null;
            for(Uri uri:uris){
                try{
                    String detected=getContentResolver().getType(uri);final String mime=(detected==null||!detected.startsWith("image/"))?"image/jpeg":detected;
                    final String name=queryDisplayName(uri);
                    byte[] bytes=readUriBytes(uri,12*1024*1024);
                    if(bytes.length>10*1024*1024)throw new IllegalArgumentException(name+" 超过 10 MB，请选择较小图片");
                    added.add(new Attachment(name,mime,bytes));
                }catch(Exception e){if(firstError==null)firstError=e.getMessage();}
            }
            final String err=firstError;
            ui(()->{
                pendingAttachments.addAll(added);rebuildAttachmentStrip();
                if(!added.isEmpty())toast(added.size()==1?"已添加图片："+added.get(0).label:"已添加 "+added.size()+" 张图片");
                if(err!=null)toast("部分图片读取失败："+err);
            });
        });
    }

    private String queryDisplayName(Uri uri){String name="image";Cursor c=null;try{c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}finally{if(c!=null)c.close();}return name==null?"image":name;}
    private byte[] readUriBytes(Uri uri,int hardLimit)throws Exception{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new Exception("无法读取图片");byte[] buf=new byte[32768];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>hardLimit)throw new IllegalArgumentException("图片过大");out.write(buf,0,n);}return out.toByteArray();}}

    private List<File> discoverSkills() {
        List<File> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        File[] roots = { new File(config.projectDirectory,".iq/skills"), new File(TermuxConstants.TERMUX_HOME_DIR_PATH,".iq/skills") };
        for (File root : roots) {
            File[] dirs = root.listFiles(); if (dirs == null) continue;
            for (File dir : dirs) {
                File skill = dir.isDirectory() ? new File(dir,"SKILL.md") : null;
                if (skill == null || !skill.isFile()) continue;
                try {
                    if (!seen.add(skill.getCanonicalPath())) continue;
                } catch (Exception ignored) {
                    if (!seen.add(skill.getAbsolutePath())) continue;
                }
                out.add(skill);
            }
        }
        out.sort((a,b)->a.getParentFile().getName().compareToIgnoreCase(b.getParentFile().getName()));
        return out;
    }

    private String relativeProjectPath(File f) {
        try {
            String root = new File(config.projectDirectory).getCanonicalPath(); String p = f.getCanonicalPath();
            if (p.startsWith(root + File.separator)) return p.substring(root.length()+1);
            return p;
        } catch (Exception e) { return f.getPath(); }
    }

    private void showModelPicker() {
        ApiProfile active=settingsStore.getProfile(config.profileId);if(active==null)active=settingsStore.getActiveProfile();
        if(active==null){toast("请先新增一条 API 配置");showApiProfileManager();return;}
        if(wide){showApiCenter();return;}
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        TextView h=dialogTitle("选择模型 · "+active.name);panel.addView(h,lp(-1,dp(48)));
        TextView status=text("正在从当前 API 获取模型…",11.5f,MUTED);panel.addView(status,lp(-1,dp(34)));
        LinearLayout quotaBox=vbox();panel.addView(quotaBox,lp(-1,-2));
        LinearLayout models=vbox();ScrollView scroll=new ScrollView(this);scroll.addView(models);scroll.setVisibility(View.GONE);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0));
        EditText manual=input(config.model,false);manual.setHint("手动输入模型名");panel.addView(manual,lp(-1,dp(52)));
        LinearLayout footer=hbox();footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(10),0,0);
        TextView manage=dialogButton("管理 API",MUTED,false);manage.setOnClickListener(v->{d.dismiss();showApiProfileManager();});footer.addView(manage,dialogActionParams());
        TextView use=dialogButton("使用模型",TEXT,true);use.setOnClickListener(v->{String m=manual.getText().toString().trim();if(!m.isEmpty()){applySelectedModel(m);d.dismiss();}});footer.addView(use,dialogActionParams());panel.addView(footer,lp(-1,dp(58)));
        d.setOnDismissListener(x->{modelCatalogGeneration.incrementAndGet();if(modelCatalogFuture!=null)modelCatalogFuture.cancel(true);});
        d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(500),-2);
        loadModelCatalog(d,models,scroll,status,manual);
        attachZcodeQuota(quotaBox,active);
    }

    /**
     * Catalog discovery for the active profile, drawn into whichever sheet owns {@code models}. All sheet
     * varieties share it so the picker, the tablet API centre and the manager's detail pane stay in step.
     */
    private void loadModelCatalog(Dialog d,LinearLayout models,ScrollView scroll,TextView status,EditText manual){
        ApiProfile active=settingsStore.getProfile(config.profileId);if(active==null)active=settingsStore.getActiveProfile();
        if(active==null)return;
        final long generation=modelCatalogGeneration.incrementAndGet();
        if(modelCatalogFuture!=null)modelCatalogFuture.cancel(true);
        SessionConfig catalogConfig=settingsStore.resolveProfile(active.id,config);
        modelCatalogFuture=io.submit(()->{
            try{
                List<ModelDescriptor> fetched=modelCatalogClient.fetch(catalogConfig,()->generation!=modelCatalogGeneration.get()||Thread.currentThread().isInterrupted());
                String entitlementNote="";
                if("zcode".equals(catalogConfig.protocol)){
                    // 套餐没开放的模型网关会直接拒绝，目录按 billing/balance 的 capabilities 过滤。
                    List<String> entitled=new com.termux.app.iqcode.api.ZcodePlanProvider(getApplicationContext())
                        .entitledModelIds(catalogConfig.apiKey==null?"":catalogConfig.apiKey.trim());
                    if(entitled!=null&&!entitled.isEmpty()){
                        List<ModelDescriptor> filtered=new java.util.ArrayList<>();
                        for(ModelDescriptor m:fetched) if(entitled.contains(m.id)) filtered.add(m);
                        if(!filtered.isEmpty()) fetched=filtered;
                    }
                    entitlementNote="（仅套餐可用模型）";
                }
                final List<ModelDescriptor> models_ =fetched;final String note=entitlementNote;
                ui(()->{if(generation!=modelCatalogGeneration.get()||!d.isShowing())return;models.removeAllViews();status.setText(models_.isEmpty()?"API 未返回可用模型，可手动输入":"已获取 "+models_.size()+" 个模型"+note);for(ModelDescriptor model:models_){TextView row=modelRow(model.displayName+(model.displayName.equals(model.id)?"":"\n"+model.id),model.id.equals(config.model));row.setOnClickListener(v->{manual.setText(model.id);manual.setSelection(manual.length());});models.addView(row);}sizeModelList(scroll,models);});
            }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            catch(Exception error){ui(()->{if(generation!=modelCatalogGeneration.get()||!d.isShowing())return;status.setText(friendlyCatalogError(error));});}
        });
    }

    /** Grows the model list to its rows and caps it at ~6 entries, so a short catalog leaves no blank block. */
    private void sizeModelList(ScrollView scroll,LinearLayout models){
        int height=Math.min(Math.min(dp(348),(int)(dialogMaxBodyPx()*.55f)),models.getChildCount()*dp(58));
        LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)scroll.getLayoutParams();
        if(p.height!=height){p.height=height;scroll.setLayoutParams(p);}
        scroll.setVisibility(height>0?View.VISIBLE:View.GONE);
    }

    /** Model entry: a name plus its id on a second line, 52dp tall so it is easy to hit with a thumb. */
    private TextView modelRow(String label,boolean selected){
        TextView row=text(label,12.5f,selected?ACCENT:TEXT);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setSingleLine(false);
        row.setLineSpacing(0,1.1f);
        row.setPadding(dp(14),0,dp(12),0);
        row.setBackground(round(selected?SURFACE_3:SURFACE,14,selected?ACCENT:BORDER,1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));
        p.setMargins(0,0,0,dp(6));
        row.setLayoutParams(p);
        return row;
    }

    private void applySelectedModel(String model){
        config.model=model;
        try{
            ApiProfile profile=settingsStore.getProfile(config.profileId);if(profile==null)profile=settingsStore.getActiveProfile();
            if(profile==null){toast("请先新增一条 API 配置");showApiProfileManager();return;}
            profile.defaultModel=model;settingsStore.saveProfile(profile,"",false);config=settingsStore.resolveProfile(profile.id,config);settingsStore.selectProfile(profile.id);
            SessionRuntime rt=activeRuntime;if(rt!=null){rt.nextConfig=config.copy();if(rt.busy||rt.engine.isBusy())rt.pendingProfileId=profile.id;else{rt.boundProfileId=profile.id;rt.pendingProfileId="";rt.engine.configure(config.copy());}}
        }catch(Exception e){toast("模型保存失败："+(e.getMessage()==null?"":e.getMessage()));return;}
        updateComposerChips();
    }

    private String friendlyCatalogError(Exception error){
        String message=error==null||error.getMessage()==null?"":error.getMessage();
        if(message.length()>120)message=message.substring(0,120)+"…";
        return message.isEmpty()?"无法获取模型，可手动输入":message+"；可手动输入或管理 API";
    }

    /**
     * ZCode start-plan 额度卡片：拉取 billing/balance 快照，逐桶显示 余量/总量、
     * 动画余量条与重置倒计时；仅 zcode 协议且有 JWT 时出现，点“刷新”重取。
     */
    private void attachZcodeQuota(LinearLayout box,ApiProfile profile){
        if(box==null)return;
        if(profile==null||!"zcode".equals(profile.protocol)){box.removeAllViews();return;}
        final String jwt;
        try{SessionConfig qc=settingsStore.resolveProfile(profile.id,config);jwt=qc.apiKey==null?"":qc.apiKey.trim();}
        catch(Exception e){box.removeAllViews();return;}
        if(jwt.isEmpty()){box.removeAllViews();return;}
        box.removeAllViews();
        LinearLayout card=vbox();card.setPadding(dp(12),dp(10),dp(12),dp(12));card.setBackground(round(SURFACE,14,BORDER,1));
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("ZCode 额度",12f,TEXT),new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=dialogButton("刷新",ACCENT,false);refresh.setOnClickListener(v->attachZcodeQuota(box,profile));head.addView(refresh,dialogActionParams());
        card.addView(head,lp(-1,dp(30)));
        TextView state=text("正在读取套餐余额…",11f,MUTED);state.setTag("zq-state");card.addView(state,lp(-1,-2));
        LinearLayout rows=vbox();rows.setTag("zq-rows");card.addView(rows,lp(-1,-2));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(6),0,dp(2));card.setLayoutParams(cp);
        box.addView(card);
        UiMotion.fadeSlideIn(card,0f,dp(6),240);
        io.submit(()->{
            try{
                org.json.JSONObject data=new com.termux.app.iqcode.api.ZcodePlanProvider(getApplicationContext()).fetchBalanceData(jwt);
                ui(()->{if(card.isAttachedToWindow())renderZcodeQuota(card,data);});
            }catch(Exception error){
                ui(()->{if(!card.isAttachedToWindow())return;TextView hint=(TextView)card.findViewWithTag("zq-state");if(hint!=null)hint.setText(shorten(error.getMessage()==null?"额度读取失败":error.getMessage(),80));});
            }
        });
    }

    /** 填充额度卡片：每个余量桶一行（名称 · 剩余/总量 · 动画条 · 重置倒计时）。 */
    private void renderZcodeQuota(LinearLayout card,JSONObject data){
        TextView state=(TextView)card.findViewWithTag("zq-state");if(state!=null)card.removeView(state);
        LinearLayout rows=(LinearLayout)card.findViewWithTag("zq-rows");if(rows==null)return;
        rows.removeAllViews();
        JSONArray balances=data.optJSONArray("balances");
        long server=data.optLong("server_time",System.currentTimeMillis()/1000L);
        if(balances==null||balances.length()==0){rows.addView(text("暂无套餐余额数据",11.5f,MUTED_2),lp(-1,-2));return;}
        for(int i=0;i<balances.length();i++){
            JSONObject bucket=balances.optJSONObject(i);if(bucket==null)continue;
            long total=bucket.optLong("total_units",0),remaining=bucket.optLong("remaining_units",0);
            long expiresAt=bucket.optLong("expires_at",0);
            LinearLayout row=vbox();
            LinearLayout line=hbox();
            line.addView(text(bucket.optString("show_name",""),12f,TEXT),new LinearLayout.LayoutParams(0,-2,1));
            line.addView(text(fmtTokens(remaining)+" / "+fmtTokens(total),11f,MUTED),lp(-2,-2));
            row.addView(line,lp(-1,-2));
            LinearLayout.LayoutParams barP=new LinearLayout.LayoutParams(-1,dp(4));barP.setMargins(0,dp(6),0,0);
            row.addView(new QuotaBar(total>0?remaining/(float)total:0f),barP);
            String sub;
            if(expiresAt<=0)sub="永久有效";
            else{
                long remainSec=expiresAt-server;
                if(remainSec<=0)sub="已过期";
                else if(remainSec<3600)sub="剩 "+(remainSec/60)+" 分钟重置";
                else if(remainSec<86400)sub=String.format(java.util.Locale.US,"剩 %d:%02d 重置",remainSec/3600,(remainSec%3600)/60);
                else sub="剩 "+(remainSec/86400)+" 天重置";
            }
            TextView subV=text(sub,10.5f,MUTED_2);LinearLayout.LayoutParams subP=lp(-1,-2);subP.setMargins(0,dp(3),0,0);row.addView(subV,subP);
            LinearLayout.LayoutParams rowP=lp(-1,-2);rowP.setMargins(0,i==0?dp(8):dp(12),0,0);
            rows.addView(row,rowP);
            UiMotion.listItemIn(row,i);
        }
    }

    /** token 数中文紧凑格式：1.0亿 / 495.6万 / 原值。 */
    private static String fmtTokens(long n){
        if(n>=100000000L)return String.format(java.util.Locale.US,"%.2f亿",n/100000000.0);
        if(n>=10000L)return String.format(java.util.Locale.US,"%.1f万",n/10000.0);
        return String.valueOf(n);
    }

    /** 余量条：槽底 + 圆角填充，挂上窗口后从 0 丝滑滑到目标比例；余量 <10% 转红。 */
    private final class QuotaBar extends View{
        private final float fraction;
        private float drawn;
        private android.animation.ValueAnimator animator;
        QuotaBar(float fraction){
            super(MainActivity.this);
            this.fraction=Math.max(0f,Math.min(1f,fraction));
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){
            float radius=getHeight()/2f;
            android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(SURFACE_3);
            canvas.drawRoundRect(0,0,getWidth(),getHeight(),radius,radius,paint);
            if(drawn>0f){
                paint.setColor(fraction<0.1f?RED:ACCENT);
                canvas.drawRoundRect(0,0,Math.max(getHeight(),getWidth()*drawn),getHeight(),radius,radius,paint);
            }
        }
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();
            long duration=UiMotion.durationFor(getContext(),700);
            if(duration==0L){drawn=fraction;invalidate();return;}
            animator=android.animation.ValueAnimator.ofFloat(0f,fraction);
            animator.setDuration(duration);
            animator.setInterpolator(UiMotion.emphasized());
            animator.addUpdateListener(a->{drawn=(Float)a.getAnimatedValue();invalidate();});
            animator.start();
        }
        @Override protected void onDetachedFromWindow(){
            if(animator!=null)animator.cancel();
            super.onDetachedFromWindow();
        }
    }

    private void showApiProfileManager(){
        if(wide){showApiCenter();return;}
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        List<ApiProfile> profiles=settingsStore.getProfiles();String activeId=config.profileId;
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);head.addView(dialogTitle("API 配置"),new LinearLayout.LayoutParams(0,dp(48),1));TextView add=pill("＋ 新增",TEXT);add.setGravity(Gravity.CENTER);add.setOnClickListener(v->{d.dismiss();showApiProfileEditor(null);});head.addView(add,lp(dp(96),dp(44)));panel.addView(head,lp(-1,dp(48)));
        if (config.apiKey == null || config.apiKey.trim().isEmpty()) {
            TextView hintTop=text(profiles.isEmpty()?"点“＋ 新增”，填写协议、Base URL、API Key 和默认模型。":"请选择一条 API 配置，或点“＋ 新增”添加新的兼容 API。",11.5f,MUTED_2);
            hintTop.setPadding(dp(4),dp(4),dp(4),dp(10));
            panel.addView(hintTop,lp(-1,-2));
        }
        LinearLayout rows=vbox();
        for(ApiProfile profile:profiles){
            LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));row.setBackground(round(profile.id.equals(activeId)?SURFACE_3:SURFACE,16,profile.id.equals(activeId)?ACCENT:BORDER,1));
            TextView info=text((profile.id.equals(activeId)?"● ":"")+profile.name+"\n"+profile.protocol+" · "+shorten(profile.defaultModel,28),12,profile.id.equals(activeId)?ACCENT:TEXT);info.setGravity(Gravity.CENTER_VERTICAL);info.setLineSpacing(0,1.12f);info.setOnClickListener(v->{applyApiProfile(profile.id);d.dismiss();});row.addView(info,new LinearLayout.LayoutParams(0,dp(56),1));
            TextView edit=dialogButton("编辑",MUTED,false);edit.setOnClickListener(v->{d.dismiss();showApiProfileEditor(profile);});row.addView(edit,new LinearLayout.LayoutParams(dp(88),dp(48)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(8));rows.addView(row,rp);
        }
        panel.addView(scrollBody(rows,dialogWidthPx(500),dialogMaxBodyPx()));
        TextView hint=text("点按配置用于当前会话；任务运行中切换将在下一完整轮生效。模型按钮会从当前 API 获取模型。",11.5f,MUTED_2);hint.setPadding(dp(4),dp(10),dp(4),0);panel.addView(hint,lp(-1,-2));
        d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(500),-2);
    }

    /**
     * Tablet API centre: the profile list on the left, the model catalog for the selected profile on the
     * right. Phones keep the two single-purpose sheets, since both fit one column there.
     */
    private void showApiCenter(){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ApiProfile active=settingsStore.getProfile(config.profileId);if(active==null)active=settingsStore.getActiveProfile();
        LinearLayout root=vbox();root.setPadding(dp(16),dp(14),dp(16),dp(14));root.setBackground(round(SURFACE_2,20,Color.TRANSPARENT,0));
        LinearLayout columns=hbox();

        LinearLayout left=vbox();left.setPadding(dp(12),dp(10),dp(12),dp(10));left.setBackground(round(SURFACE,18,BORDER,1));
        left.addView(dialogTitle("API 配置"),lp(-1,dp(44)));
        TextView add=dialogButton("＋ 新增",ACCENT,false);add.setOnClickListener(v->{d.dismiss();showApiProfileEditor(null);});left.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout profileList=vbox();
        List<ApiProfile> profiles=settingsStore.getProfiles();
        if(profiles.isEmpty()){TextView empty=text("点“＋ 新增”添加一条兼容 API 配置。",11.5f,MUTED_2);empty.setPadding(dp(6),dp(16),dp(6),dp(16));profileList.addView(empty,lp(-1,-2));}
        for(ApiProfile profile:profiles){
            boolean selected=profile.id.equals(config.profileId);
            LinearLayout row=vbox();row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setBackground(round(selected?SURFACE_3:SURFACE,14,selected?ACCENT:BORDER,1));
            row.addView(text((selected?"● ":"")+profile.name,12.5f,selected?ACCENT:TEXT),lp(-1,dp(26)));
            row.addView(text(profile.protocol+" · "+shorten(profile.defaultModel,22),11.5f,MUTED),lp(-1,dp(24)));
            row.setOnClickListener(v->{if(!profile.id.equals(config.profileId))applyApiProfile(profile.id);d.dismiss();showApiCenter();});
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,dp(8),0,0);profileList.addView(row,rp);
        }
        ScrollView profileScroll=new ScrollView(this);profileScroll.setVerticalScrollBarEnabled(true);profileScroll.addView(profileList,new ScrollView.LayoutParams(-1,-2));
        left.addView(profileScroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView edit=dialogButton("编辑配置",MUTED,false);edit.setOnClickListener(v->{ApiProfile target=settingsStore.getProfile(config.profileId);if(target==null)target=settingsStore.getActiveProfile();d.dismiss();showApiProfileEditor(target);});left.addView(edit,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout right=vbox();right.setPadding(dp(14),dp(10),dp(4),dp(10));
        right.addView(dialogTitle("选择模型 · "+(active==null?"未绑定 API":active.name)),lp(-1,dp(48)));
        TextView status=text("正在从当前 API 获取模型…",11.5f,MUTED);right.addView(status,lp(-1,dp(34)));
        LinearLayout quotaBox=vbox();right.addView(quotaBox,lp(-1,-2));
        LinearLayout models=vbox();ScrollView scroll=new ScrollView(this);scroll.addView(models);scroll.setVisibility(View.GONE);right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        EditText manual=input(config.model,false);manual.setHint("手动输入模型名");right.addView(manual,lp(-1,dp(52)));
        LinearLayout footer=hbox();footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(10),0,0);
        TextView profile=text("当前配置 · "+currentProfileName(),11.5f,MUTED);footer.addView(profile,new LinearLayout.LayoutParams(0,1,1));
        TextView use=dialogButton("保存并使用",TEXT,true);use.setOnClickListener(v->{String m=manual.getText().toString().trim();if(!m.isEmpty()){applySelectedModel(m);d.dismiss();}});footer.addView(use,new LinearLayout.LayoutParams(dp(136),dp(48)));
        right.addView(footer,lp(-1,dp(58)));

        columns.addView(left,new LinearLayout.LayoutParams(dp(300),-1));
        columns.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        d.setOnDismissListener(x->{modelCatalogGeneration.incrementAndGet();if(modelCatalogFuture!=null)modelCatalogFuture.cancel(true);});
        d.setContentView(root);d.show();
        styleFormDialog(d,dialogWidthPx(720),Math.min(dp(760),(int)(getResources().getDisplayMetrics().heightPixels*.88f)));
        loadModelCatalog(d,models,scroll,status,manual);
        attachZcodeQuota(quotaBox,active);
    }

    private void showApiProfileEditor(ApiProfile existing){
        final ApiProfile draft=existing==null?new ApiProfile():existing.copy();
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle(existing==null?"新增 API 配置":"编辑 API 配置"),lp(-1,dp(48)));
        EditText name=input(draft.name,false);name.setHint("配置名称");panel.addView(labeled("名称",name));
        String[] protocols={"Anthropic Messages","OpenAI Chat","OpenAI Responses","Codex Responses","DeepSeek 免费网页版","ZCode"};String[] values={"anthropic","openai-chat","openai-responses","codex-responses","deepseek-free","zcode"};Spinner protocol=darkSpinner(protocols,values,draft.protocol);panel.addView(labeled("协议",protocol));
        EditText endpoint=input(draft.baseUrl,false);endpoint.setHint("https://api.example.com");panel.addView(labeled("API Base URL",endpoint));
        EditText key=input("",true);key.setHint(existing==null?"请填写 API Key":"留空保持已保存密钥不变");panel.addView(labeled("API Key",key));
        EditText model=input(draft.defaultModel,false);model.setHint("默认模型（可稍后自动获取）");panel.addView(labeled("默认模型",model));
        TextView webLogin=dialogButton("网页登录 DeepSeek（自动获取登录态）",TEXT,false);
        webLogin.setOnClickListener(v->{if(!"deepseek-free".equals(values[Math.max(0,protocol.getSelectedItemPosition())])){toast("请先选择 DeepSeek 免费网页版协议");return;}showDeepSeekWebLogin(key);});
        panel.addView(webLogin,lp(-1,dp(44)));
        protocol.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                String picked=values[Math.max(0,pos)];
                boolean free="deepseek-free".equals(picked);
                boolean zcode="zcode".equals(picked);
                key.setHint(free?"邮箱|密码|设备ID 或点下方网页登录自动获取":zcode?"填写 ZCode 授权码":(existing==null?"请填写 API Key":"留空保持已保存密钥不变"));
                endpoint.setHint(free?"无需填写，直连 chat.deepseek.com":zcode?"无需填写，直连 ZCode 网关":"https://api.example.com");
                model.setHint(free?"deepseek-default（默认），可填 deepseek-expert":zcode?"GLM 系列，可点“获取模型”自动列出":"默认模型（可稍后自动获取）");
                webLogin.setVisibility(free?View.VISIBLE:View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){ }
        });
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(6),0,0);
        if(existing!=null){TextView delete=dialogButton("删除",RED,false);delete.setOnClickListener(v->{try{boolean current=existing.id.equals(config.profileId);settingsStore.deleteProfile(existing.id);if(current){String next=settingsStore.getActiveProfileId();if(next.isEmpty())unbindApiProfile();else applyApiProfile(next);}d.dismiss();showApiProfileManager();}catch(Exception e){toast((e.getMessage()==null?"":e.getMessage()));}});actions.addView(delete,new LinearLayout.LayoutParams(dp(88),dp(48)));}
        if(wide){View spacer=new View(this);actions.addView(spacer,new LinearLayout.LayoutParams(0,1,1));}
        TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->d.dismiss());actions.addView(cancel,dialogActionParams());
        TextView save=dialogButton("保存并使用",TEXT,true);save.setOnClickListener(v->{
            try{draft.name=name.getText().toString().trim();draft.protocol=values[Math.max(0,protocol.getSelectedItemPosition())];draft.baseUrl=endpoint.getText().toString().trim();draft.defaultModel=model.getText().toString().trim();String replacement=key.getText().toString();ApiProfile saved=settingsStore.saveProfile(draft,replacement,!replacement.isEmpty());applyApiProfile(saved.id);d.dismiss();}
            catch(Exception e){toast("保存失败："+(e.getMessage()==null?"":e.getMessage()));}
        });actions.addView(save,dialogActionParams());panel.addView(actions,lp(-1,dp(60)));
        int width=dialogWidthPx(500);
        d.setContentView(scrollBody(panel,width,dialogMaxBodyPx()));d.show();styleFormDialog(d,width);
    }

    /**
     * 内置 WebView 打开 DeepSeek 登录页，登录完成后自动从其 API 请求头里
     * 抓取 Authorization Bearer token 与 X-Device-Id（网页端每次调 /api/v0/ 都会带），
     * 组装成 `token|<token>|<deviceId>` 写回 API Key 输入框。
     */
    private void showDeepSeekWebLogin(EditText keyField){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("登录 DeepSeek 网页版"),lp(-1,dp(48)));
        android.widget.TextView status=new android.widget.TextView(this);
        status.setTextColor(MUTED);status.setTextSize(12);status.setPadding(dp(12),dp(4),dp(12),dp(8));
        status.setText("在下面完成登录；登录成功后会自动填回本页，无需手动复制。");
        panel.addView(status,lp(-1,android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        final android.webkit.WebView wv=new android.webkit.WebView(this);
        android.webkit.WebSettings ws=wv.getSettings();
        ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);
        android.webkit.CookieManager cm=android.webkit.CookieManager.getInstance();
        cm.setAcceptCookie(true);cm.setAcceptThirdPartyCookies(wv,true);
        final java.util.concurrent.atomic.AtomicReference<String> gotToken=new java.util.concurrent.atomic.AtomicReference<>(null);
        final java.util.concurrent.atomic.AtomicReference<String> gotDevice=new java.util.concurrent.atomic.AtomicReference<>("");
        wv.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest r){return false;}
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,String url){return false;}
            @Override public void onPageStarted(android.webkit.WebView v,String url,android.graphics.Bitmap f){
                if(url.contains("deepseek.com"))status.setText("页面："+url.replace("https://chat.deepseek.com",""));
            }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView v,android.webkit.WebResourceRequest r){
                String url=r.getUrl().toString();
                if(url.contains("deepseek.com")&&url.contains("/api/v0/")){
                    // HTTP/2 下请求头名是小写（authorization/x-device-id），必须忽略大小写地找
                    String auth=null,dev=null;
                    for(java.util.Map.Entry<String,String> e:r.getRequestHeaders().entrySet()){
                        String k=e.getKey().toLowerCase(java.util.Locale.US);
                        if("authorization".equals(k))auth=e.getValue();
                        else if("x-device-id".equals(k))dev=e.getValue();
                    }
                    if(dev!=null&&!dev.isEmpty())gotDevice.set(dev);
                    if(auth!=null){
                        String tok=auth.trim();
                        if(tok.regionMatches(true,0,"Bearer ",0,7))tok=tok.substring(7).trim();
                        if(!tok.isEmpty())gotToken.compareAndSet(null,tok);
                    }
                }
                return null;
            }
        });
        wv.loadUrl("https://chat.deepseek.com/sign_in");
        LinearLayout wvBox=new LinearLayout(this);wvBox.setOrientation(LinearLayout.VERTICAL);
        wvBox.addView(wv,new LinearLayout.LayoutParams(-1,dp(430)));
        panel.addView(wvBox,lp(-1,dp(430)));
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,0);
        TextView close=dialogButton("关闭",MUTED,false);close.setOnClickListener(v->d.dismiss());row.addView(close,dialogActionParams());
        panel.addView(row,lp(-1,dp(56)));
        final android.os.Handler h=new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] poll=new Runnable[1];
        poll[0]=new Runnable(){
            private int ticks=0;
            private void harvestLocal(){
                wv.evaluateJavascript("(function(){try{return JSON.stringify({t:(localStorage.getItem('userToken')||localStorage.getItem('token')||''),d:(localStorage.getItem('deviceId')||localStorage.getItem('device_id')||'')});}catch(e){return '{}';}})()",
                    v->{if(v==null||"null".equals(v)||v.length()<2)return;String s=v;
                        try{
                            if(s.charAt(0)=='"'&&s.charAt(s.length()-1)=='"')s=s.substring(1,s.length()-1);
                            org.json.JSONObject o=new org.json.JSONObject(s);
                            String tok=o.optString("t",""),dev=o.optString("d","");
                            if(dev!=null&&!dev.isEmpty())gotDevice.compareAndSet("",dev);
                            if(tok!=null&&!tok.isEmpty()&&!"null".equals(tok)){
                                if(tok.charAt(0)=='"'&&tok.length()>=2&&tok.charAt(tok.length()-1)=='"')tok=tok.substring(1,tok.length()-1);
                                try{
                                    if(tok.startsWith("{")){org.json.JSONObject to=new org.json.JSONObject(tok);
                                        Object val=to.opt("value");
                                        if(val instanceof String)tok=(String)val;
                                        else if(val instanceof org.json.JSONObject)tok=((org.json.JSONObject)val).optString("token","");
                                        else tok=to.optString("token",to.optString("value",""));
                                    }
                                }catch(Exception ignored){ }
                                if(tok.regionMatches(true,0,"Bearer ",0,7))tok=tok.substring(7).trim();
                                if(!tok.isEmpty()&&!tok.equals("null"))gotToken.compareAndSet(null,tok);
                            }
                        }catch(Exception ignored){ }});
            }
            @Override public void run(){
                String t=gotToken.get();
                if(t==null||t.isEmpty()){
                    harvestLocal();
                    t=gotToken.get();
                }
                if(t!=null&&!t.isEmpty()){
                    status.setText("已获取登录态"+(gotDevice.get().isEmpty()?"（未捕获设备ID，留空派生）":"（含设备ID）")+"，正在填回…");
                    keyField.setText("token|"+t+"|"+gotDevice.get());
                    toast("DeepSeek 登录态已填回，请点「保存并使用」");
                    d.dismiss();
                    return;
                }
                ticks++;
                if(ticks%6==0)status.setText("等待登录完成…（登录后进入对话页会自动填回；也可以直接看这行有没有跟着变）");
                h.postDelayed(this,600);
            }
        };
        h.postDelayed(poll[0],600);
        d.setOnDismissListener(x->{h.removeCallbacks(poll[0]);wv.stopLoading();wv.destroy();});
        int w=dialogWidthPx(430);
        d.setContentView(scrollBody(panel,w,dialogMaxBodyPx()));d.show();styleFormDialog(d,w);
    }

    private void applyApiProfile(String profileId){
        SessionRuntime rt=activeRuntime;SessionConfig base=rt!=null&&rt.nextConfig!=null?rt.nextConfig:config;
        SessionConfig resolved=settingsStore.resolveProfile(profileId,base);
        if(resolved==null){toast("API 配置不存在");return;}
        settingsStore.selectProfile(profileId);config=resolved.copy();modelCatalogGeneration.incrementAndGet();if(modelCatalogFuture!=null)modelCatalogFuture.cancel(true);
        if(rt!=null){rt.nextConfig=resolved.copy();if(rt.busy||rt.engine.isBusy())rt.pendingProfileId=profileId;else{rt.boundProfileId=profileId;rt.pendingProfileId="";rt.engine.configure(resolved.copy());}}
        updateComposerChips();toast(rt!=null&&(rt.busy||rt.engine.isBusy())?"API 配置将在下一完整轮生效":"已切换 API 配置");
        if (apiSetupPending && config.apiKey != null && !config.apiKey.trim().isEmpty()) {
            uiHandler.postDelayed(this::continueFirstLaunchSetup, 180L);
        }
    }

    /** The last API record was deleted: drop the binding instead of pointing at a missing profile. */
    private void unbindApiProfile(){
        config.profileId="";config.apiKey="";
        SessionRuntime rt=activeRuntime;
        if(rt!=null){rt.nextConfig=config.copy();rt.boundProfileId="";rt.pendingProfileId="";}
        updateComposerChips();toast("已删除最后一条 API 配置");
    }

    private String currentProfileName(){
        String id=config==null?"":config.profileId;
        for(ApiProfile profile:settingsStore.getProfiles())if(profile.id.equals(id))return profile.name;
        return "未绑定 API";
    }

    private LinearLayout conversationHost(){
        return currentConversationHost==null?chatMessages:currentConversationHost;
    }

    private void addChatRootView(View child,ViewGroup.LayoutParams params){
        if(chatMessages==null||child==null)return;
        int index=chatImeSpacer!=null&&chatImeSpacer.getParent()==chatMessages?chatMessages.indexOfChild(chatImeSpacer):chatMessages.getChildCount();
        chatMessages.addView(child,index,params);
    }

    private LinearLayout beginConversation(){
        // 回答不再套一整块圆角卡：正文直接铺在会话背景上，靠左侧竖条区分是谁在说话。
        LinearLayout group=vbox();
        group.setPadding(0,0,0,0);
        LinearLayout.LayoutParams groupParams=new LinearLayout.LayoutParams(-1,-2);
        groupParams.setMargins(0,dp(10),0,dp(14));
        int index=chatImeSpacer!=null&&chatImeSpacer.getParent()==chatMessages?chatMessages.indexOfChild(chatImeSpacer):chatMessages.getChildCount();
        chatMessages.addView(group,index,groupParams);
        currentConversationHost=vbox();
        group.addView(currentConversationHost,lp(-1,-2));
        return currentConversationHost;
    }

    private void addChatView(ChatItem item) {
        if (chatMessages == null) return;
        maybeTrimTranscriptViews();
        LinearLayout destination;
        if(item.type==ChatItem.USER){currentConversationHost=null;destination=chatMessages;}
        else destination=currentConversationHost==null?beginConversation():currentConversationHost;
        if (item.type == ChatItem.USER) {
            LinearLayout row = hbox(); row.setGravity(Gravity.RIGHT);
            LinearLayout bubble = vbox(); bubble.setPadding(dp(10),dp(8),dp(10),item.body.length()>0?dp(10):dp(8)); bubble.setBackground(round(USER_BG,16, BORDER_SOFT, 1));
            int bubbleMax = wide ? dp(520) : (int)(getResources().getDisplayMetrics().widthPixels * .82f);
            if (!item.images.isEmpty()) bubble.addView(buildSentImagePreview(item.images, bubbleMax));
            if (item.body.length() > 0) {
                TextView copy = text(shortenMultiline(item.body.toString(), MESSAGE_TEXT_MAX_CHARS), 14, TEXT); copy.setTextIsSelectable(true); copy.setLineSpacing(dp(2),1.15f); copy.setPadding(dp(4), item.images.isEmpty()?dp(2):dp(8), dp(4), dp(2)); copy.setMaxWidth(bubbleMax); bubble.addView(copy, lp(-1,-2));
            }
            LinearLayout userStack = vbox(); userStack.setGravity(Gravity.RIGHT);
            userStack.addView(bubble, lp(-2,-2));
            TextView actions = iconOnly(R.drawable.ic_more,18,MUTED_2);
            actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); actions.setPadding(dp(8),dp(2),dp(4),0);
            actions.setContentDescription("复制、编辑或删除这条消息");
            actions.setOnClickListener(v -> showMessageActions(item));
            bubble.setOnLongClickListener(v -> { showMessageActions(item); return true; });
            userStack.addView(actions, lp(dp(40),dp(32)));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.setMargins(dp(34),dp(5),0,dp(5)); row.addView(userStack,bp); addChatRootView(row,lp(-1,-2));
            UiMotion.bindInteractive(row);
            if (!rebuildingTranscript) UiMotion.messageIn(row, true);
            return;
        }
        if (item.type == ChatItem.TOOL) {
            CollapsedToolActivity activity=collapsedToolActivity(item);
            if(activity!=null){
                attachToolToCollapsedActivity(item);
                if(!activity.members.isEmpty()&&activity.members.get(0)==item&&activity.renderedView==null)addCollapsedToolActivity(activity,destination);
                return;
            }
            addToolCard(item,destination); return;
        }
        if (item.type == ChatItem.RESULT) { addToolCard(item,destination); return; }

        boolean grouped=destination!=chatMessages;
        LinearLayout block = vbox(); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,grouped?dp(2):dp(6),0,grouped?dp(4):dp(12)); block.setLayoutParams(p);
        if(item.type==ChatItem.ASSISTANT){
            block.setPadding(0,dp(1),dp(2),dp(3));
        }
        if(item.type!=ChatItem.ASSISTANT){
            LinearLayout heading = hbox(); heading.setGravity(Gravity.CENTER_VERTICAL);
            if (item.type == ChatItem.ERROR) {
                TextView dot = iconOnly(R.drawable.ic_warning,14, RED); heading.addView(dot, lp(dp(24),dp(26)));
            }
            TextView name = text(item.title, 12, item.type == ChatItem.ERROR ? RED : TEXT); name.setTypeface(Typeface.DEFAULT_BOLD); heading.addView(name, new LinearLayout.LayoutParams(0,dp(26),1));
            TextView more = iconOnly(R.drawable.ic_more,18,MUTED_2); more.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); more.setPadding(dp(8),0,dp(2),0); more.setOnClickListener(v -> showMessageActions(item)); heading.addView(more, lp(dp(36),dp(26)));
            block.addView(heading);
        }
        block.setOnLongClickListener(v -> { showMessageActions(item); return true; });
        LinearLayout bodyHost = vbox();
        bodyHost.setPadding(item.type == ChatItem.ERROR ? dp(24) : 0,dp(2),dp(4),dp(4));
        if (item == streamingItem) {
            String streamText = shortenMultiline(item.body.toString(), MESSAGE_TEXT_MAX_CHARS);
            TextView body = text(streamText, 14, TEXT);
            body.setText(MarkdownRenderer.inlineText(streamText,neonTheme,lightTheme), TextView.BufferType.SPANNABLE);
            body.setTextIsSelectable(true); body.setLineSpacing(dp(2),1.18f); body.setPadding(0,0,0,dp(4));
            bodyHost.addView(body, lp(-1,-2));
            streamingView = body; streamingBodyHost = bodyHost;
        } else {
            LinearLayout rich = MarkdownRenderer.render(this, item.body.toString(), 14f, neonTheme, lightTheme);
            bodyHost.addView(rich, lp(-1,-2));
        }
        if (item != streamingItem) addContextFooter(bodyHost, item, false);
        block.addView(bodyHost, lp(-1,-2));
        item.renderedView=block;destination.addView(block);
        UiMotion.bindInteractive(block);
        if (!rebuildingTranscript) UiMotion.messageIn(block, false);
    }

    private void addAssistantThinking(LinearLayout host,ChatItem item){
        LinearLayout box=vbox();box.setPadding(dp(10),dp(6),dp(10),dp(6));box.setBackground(round(SURFACE_3,10,Color.TRANSPARENT,0));
        TextView label=text("⌁  思考过程  ›",10.5f,ACCENT);label.setTypeface(Typeface.DEFAULT_BOLD);box.addView(label,lp(-1,dp(22)));
        TextView detail=text("正在组织回答…",10.5f,MUTED);detail.setMaxLines(5);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.setLineSpacing(0,1.08f);detail.setVisibility(View.GONE);box.addView(detail,lp(-1,-2));
        item.thinkingHost=box;item.thinkingLabel=label;item.thinkingView=detail;box.setOnClickListener(v->{item.thinkingExpanded=!item.thinkingExpanded;refreshAssistantThinking(item);});refreshAssistantThinking(item);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,0,dp(4),dp(8));host.addView(box,bp);
    }

    private void refreshAssistantThinking(ChatItem item){
        if(item==null||item.thinkingView==null||item.thinkingHost==null)return;
        String value=item.thinking.toString().trim();boolean available=!value.isEmpty()||!item.processSteps.isEmpty()||item==streamingItem;item.thinkingHost.setVisibility(available?View.VISIBLE:View.GONE);
        if(item.thinkingLabel!=null)item.thinkingLabel.setText((item.thinkingExpanded?"⌁  思考过程  ⌄":"⌁  思考过程  ›")+(item==streamingItem?" · 进行中":""));
        StringBuilder shown=new StringBuilder();for(String step:item.processSteps){if(shown.length()>0)shown.append('\n');shown.append("· ").append(step);}if(shown.length()==0)shown.append(item==streamingItem?"正在分析请求…":"本轮执行过程已完成");
        item.thinkingView.setText(shown.toString());
        // Process steps re-render on every step; only the open/close edges animate.
        boolean visible=item.thinkingView.getVisibility()==View.VISIBLE;
        if(item.thinkingExpanded&&!visible)UiMotion.expandBarToContent(item.thinkingView,null);
        else if(!item.thinkingExpanded&&visible)UiMotion.collapseBar(item.thinkingView,null);
    }

    private void recordAssistantProcessStep(ChatItem item,String step){
        if(item==null||step==null||step.trim().isEmpty())return;String value=step.trim();if(!item.processSteps.isEmpty()&&value.equals(item.processSteps.get(item.processSteps.size()-1)))return;if(item.processSteps.size()>=12)item.processSteps.remove(0);item.processSteps.add(value);refreshAssistantThinking(item);
    }

    /** Claude Code-style per-reply context indicator: current replay context, never cumulative billing. */
    private void addContextFooter(LinearLayout host, ChatItem item, boolean animate) {
        if (host == null || item == null || item.type != ChatItem.ASSISTANT || item.contextTokens < 0) return;
        int window = item.contextWindowTokens > 0 ? item.contextWindowTokens : Math.max(16_000, config.contextWindowTokens);
        int usedPct = Math.max(0, Math.min(999, (int)Math.round(item.contextTokens * 100.0 / window)));
        int remaining = Math.max(0, 100 - Math.min(100, usedPct));
        String label = (item.contextApiMeasured ? "上下文 " : "约上下文 ")
            + formatTokenCount(item.contextTokens) + " / " + formatTokenCount(window)
            + " · 剩余 " + remaining + "%";
        TextView meta = text(label,10.5f, usedPct >= 90 ? RED : (usedPct >= 72 ? ACCENT : MUTED_2));
        meta.setTypeface(Typeface.MONOSPACE);
        meta.setPadding(0, dp(4), dp(2), dp(0));
        meta.setContentDescription("当前会话上下文占用；不是累计 API 消费");
        host.addView(meta, lp(-1, dp(24)));
        if (animate && !rebuildingTranscript) UiMotion.contentUpdated(meta, false);
    }

    private void stampCurrentContext(ChatItem item, IQCodeEngine source) {
        if (item == null || source == null || item.type != ChatItem.ASSISTANT) return;
        item.contextTokens = estimateContextTokensTimed(source);
        item.contextWindowTokens = Math.max(16_000, config.contextWindowTokens);
        item.contextApiMeasured = source.hasMeasuredContextUsage();
    }

    private void showMessageActions(ChatItem item) {
        if (item == null) return;
        if (item.type == ChatItem.USER) {
            if(item.humanMessage){
                showChoicePicker("消息操作",new String[]{"复制","编辑此消息","删除此消息"},-1,which->{
                    if(which==0)copyMessageText(item);else if(which==1)showEditMessageDialog(item);else confirmDeleteMessage(item);
                });
            }else{
                showChoicePicker("消息操作",new String[]{"复制","再次发送","编辑后发送"},-1,which->{if(which==0)copyMessageText(item);else if(which==1)resendUserMessage(item);else stageMessageForEdit(item);});
            }
            return;
        }
        if (item.type == ChatItem.ASSISTANT || item.type == ChatItem.ERROR) {
            showChoicePicker("消息操作", new String[]{"复制", "重试上一问"}, -1, which -> {
                if (which == 0) copyMessageText(item);
                else {
                    ChatItem previous = previousUserMessage(item);
                    if (previous == null) toast("没有找到可重试的上一条提问");
                    else resendUserMessage(previous);
                }
            });
        }
    }

    private void copyMessageText(ChatItem item) {
        String value = item == null ? "" : item.body.toString();
        copyTextToClipboard("元境 message", value);
    }

    private ChatItem previousUserMessage(ChatItem from) {
        int index = transcript.indexOf(from);
        if (index < 0) index = transcript.size();
        for (int i = index - 1; i >= 0; i--) if (transcript.get(i).type == ChatItem.USER) return transcript.get(i);
        return null;
    }

    private void resendUserMessage(ChatItem source) {
        if (source == null || prompt == null) return;
        if (engine != null && engine.isBusy()) { toast("当前会话仍在执行，请先停止后再重试"); return; }
        pendingAttachments.clear(); pendingAttachments.addAll(source.images);
        String replayText=source.persistedBody==null||source.persistedBody.isEmpty()?source.body.toString():source.persistedBody;
        suppressPromptWatcher = true; prompt.setText(replayText); prompt.setSelection(prompt.length()); suppressPromptWatcher = false;
        rebuildAttachmentStrip(); sendPrompt();
    }

    private void stageMessageForEdit(ChatItem source) {
        if (source == null || prompt == null) return;
        pendingAttachments.clear(); pendingAttachments.addAll(source.images);
        String replayText=source.persistedBody==null||source.persistedBody.isEmpty()?source.body.toString():source.persistedBody;
        suppressPromptWatcher = true; prompt.setText(replayText); prompt.setSelection(prompt.length()); suppressPromptWatcher = false;
        rebuildAttachmentStrip(); hideSlashPalette(); hideComposerPicker(); prompt.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(prompt, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void showEditMessageDialog(ChatItem source){
        if(source==null||source.messageContentHash.trim().isEmpty()||!canMutateActiveHistory())return;
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("编辑此消息"),lp(-1,dp(48)));
        TextView warning=text("这会直接修改当前会话。后续回答会保留，但它们仍可能基于原文字；已执行的文件修改、命令和其他副作用不会回滚。",11.5f,ACCENT);warning.setLineSpacing(0,1.12f);warning.setPadding(0,dp(6),0,0);panel.addView(warning,lp(-1,-2));
        String persistedText=source.persistedBody==null||source.persistedBody.isEmpty()?source.body.toString():source.persistedBody;
        EditText edited=input(persistedText,false);edited.setGravity(Gravity.TOP|Gravity.START);edited.setMinLines(4);edited.setMaxLines(10);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(190));ep.setMargins(0,dp(10),0,dp(8));panel.addView(edited,ep);
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(8),0,0);TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->d.dismiss());actions.addView(cancel,dialogActionParams());
        TextView save=dialogButton("保存修改",TEXT,true);save.setOnClickListener(v->{String value=edited.getText().toString().trim();if(value.isEmpty()){toast("消息不能为空");return;}d.dismiss();mutateActiveMessage(source,value,false);});actions.addView(save,dialogActionParams());panel.addView(actions,lp(-1,dp(62)));
        d.setContentView(panel);d.show();styleFormDialog(d,dialogWidthPx(520));
    }

    private void confirmDeleteMessage(ChatItem source){
        if(source==null||source.messageContentHash.trim().isEmpty()||!canMutateActiveHistory())return;
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("删除此消息？"),lp(-1,dp(48)));TextView warning=text("只删除这条用户消息，前后回答都会保留，但后续回答可能失去原上下文；已执行的文件和命令副作用不会回滚。",11.5f,ACCENT);warning.setLineSpacing(0,1.12f);warning.setPadding(0,dp(6),0,0);panel.addView(warning,lp(-1,-2));
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(8),0,0);TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->d.dismiss());actions.addView(cancel,dialogActionParams());TextView remove=dialogButton("删除消息",RED,true);remove.setOnClickListener(v->{d.dismiss();mutateActiveMessage(source,"",true);});actions.addView(remove,dialogActionParams());panel.addView(actions,lp(-1,dp(62)));d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(460),-2);
    }

    private boolean canMutateActiveHistory(){
        SessionRuntime rt=activeRuntime;if(rt==null||rt.file==null){toast("当前会话尚未保存");return false;}
        if(rt.busy||rt.engine.isBusy()||rt.planState.isAwaitingApproval()){toast("当前会话仍在执行，请先停止后再修改历史");return false;}
        return true;
    }

    private void mutateActiveMessage(ChatItem source,String replacement,boolean delete){
        SessionRuntime rt=activeRuntime;File file=rt==null?null:rt.engine.getSessionFile();long generation=activeRuntimeGeneration;
        if(file==null){toast("当前会话尚未保存");return;}
        if(source==null||source.messageContentHash.trim().isEmpty()){
            toast("这条消息缺少有效历史校验，请重新打开会话后再试");return;
        }
        SessionStore.MessageReference reference=new SessionStore.MessageReference(source.messageId,source.legacyRowIndex,source.messageContentHash);
        io.submit(()->{try{
            if(delete)SessionStore.deleteHumanMessage(file,reference);else SessionStore.editHumanMessage(file,reference,replacement);
            ui(()->{
                if(activeRuntime!=rt||activeRuntimeGeneration!=generation||!runtimeKey(file).equals(runtimeKey(activeRuntime==null?null:activeRuntime.file))){
                    refreshVisibleSessionRowsFromDiskAsync();
                    toast("历史已修改；当前已切换到其他会话");
                    return;
                }
                reloadSessionRuntimeAfterMutation(file,delete?"消息已删除":"消息已修改");
            });
        }catch(Exception error){ui(()->toast("修改历史失败："+(error.getMessage()==null?"":error.getMessage())));}});
    }

    private void reloadSessionRuntimeAfterMutation(File file,String message){
        final String key=runtimeKey(file);final SessionRuntime old=sessionRuntimes.get(key);
        final long token=++sessionLoadToken;
        // 重建 + 解析同样放后台，主线程只做激活和渲染（与 resumeSession 同一套 ANR 修复）。
        io.submit(()->{
            SessionRuntime replacement=null;JSONArray rows=null;Exception error=null;
            try{replacement=buildRuntimeForExisting(file);rows=SessionStore.readRows(file);}
            catch(Exception e){error=e;}
            final SessionRuntime builtReplacement=replacement;final JSONArray builtRows=rows;final Exception failure=error;
            ui(()->{
                if(token!=sessionLoadToken||isFinishing()||isDestroyed())return;
                if(failure!=null){
                    if(old!=null)try{old.engine.resumeConversation(file);activateRuntime(old,true);currentView=VIEW_CHAT;styleViewTabs();renderChat(primaryHost,false);refreshVisibleSessionRows();refreshVisibleSessionRowsFromDiskAsync();toast(message);return;}
                    catch(Exception fallback){old.engine.shutdown();sessionRuntimes.remove(key,old);}
                    toast("重新加载会话失败："+(failure.getMessage()==null?"":failure.getMessage()));return;
                }
                sessionRuntimes.put(key,builtReplacement);activateRuntime(builtReplacement,builtRows);
                if(old!=null&&old!=builtReplacement)old.engine.shutdown();
                currentView=VIEW_CHAT;styleViewTabs();renderChat(primaryHost,false);refreshVisibleSessionRows();refreshVisibleSessionRowsFromDiskAsync();toast(message);
            });
        });
    }

    private View buildSentImagePreview(List<Attachment> images, int maxWidth) {
        if (images.size() == 1) {
            Attachment a = images.get(0);
            Bitmap bm = decodeSampledBitmap(a.bytes, maxWidth, dp(360));
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(round(SURFACE,14, Color.TRANSPARENT, 0)); image.setClipToOutline(true);
            if (bm != null) image.setImageBitmap(bm);
            int targetW=maxWidth;
            int targetH=dp(220);
            if(bm!=null && bm.getWidth()>0){targetH=Math.max(dp(120),Math.min(dp(360),(int)((long)targetW*bm.getHeight()/bm.getWidth())));}
            image.setOnClickListener(v -> showFullImage(a));
            image.setContentDescription(a.label);
            return wrapSized(image,targetW,targetH);
        }
        HorizontalScrollView scroll=new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout strip=hbox(); strip.setGravity(Gravity.CENTER_VERTICAL);
        int size=Math.min(dp(156),Math.max(dp(118),maxWidth/2));
        for(Attachment a:images){
            ImageView image=new ImageView(this); Bitmap bm=decodeSampledBitmap(a.bytes,size,size); if(bm!=null)image.setImageBitmap(bm);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackground(round(SURFACE,14,Color.TRANSPARENT,0)); image.setClipToOutline(true); image.setOnClickListener(v->showFullImage(a)); image.setContentDescription(a.label);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(size,size); ip.setMargins(0,0,dp(6),0); strip.addView(image,ip);
        }
        scroll.addView(strip,new HorizontalScrollView.LayoutParams(-2,size)); scroll.setLayoutParams(new LinearLayout.LayoutParams(maxWidth,size)); return scroll;
    }

    private View wrapSized(View child,int width,int height){
        FrameLayout frame=new FrameLayout(this); frame.addView(child,new FrameLayout.LayoutParams(-1,-1)); frame.setLayoutParams(new LinearLayout.LayoutParams(width,height)); return frame;
    }

    private Bitmap decodeSampledBitmap(byte[] bytes,int reqW,int reqH){
        if(bytes==null||bytes.length==0)return null;
        try{
            BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
            int sample=1; while(o.outWidth/sample>reqW*2 || o.outHeight/sample>reqH*2) sample*=2;
            BitmapFactory.Options d=new BitmapFactory.Options(); d.inSampleSize=Math.max(1,sample); d.inPreferredConfig=Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length,d);
        }catch(Throwable ignored){return null;}
    }

    private void showFullImage(Attachment a){
        if(a==null||a.bytes==null)return;
        final Dialog d=newOverlayAwareDialog(android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame=new FrameLayout(this); frame.setBackgroundColor(Color.BLACK);
        ImageView image=new ImageView(this); Bitmap bm=decodeSampledBitmap(a.bytes,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels); if(bm!=null)image.setImageBitmap(bm);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER); frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        TextView close=iconOnly(R.drawable.ic_close,22,Color.WHITE); close.setBackground(round(Color.argb(150,20,20,20),20,Color.TRANSPARENT,0)); close.setOnClickListener(v->d.dismiss());
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.RIGHT); cp.setMargins(0,dp(18),dp(14),0); frame.addView(close,cp);
        d.setContentView(frame); d.show();
        UiMotion.bindInteractive(frame);
        UiMotion.dialogIn(image);
    }

    private void addToolCard(ChatItem item) { addToolCard(item,chatMessages); }

    private void addToolCard(ChatItem item,LinearLayout host) {
        if(host==null)return;
        item.runningView=null;item.liveOutputView=null;
        // 元境's TUI keeps tool activity lightweight: one status row, a compact
        // summary, and output only when useful/expanded. Do the same here instead of
        // stacking large bordered cards for call + result.
        if (item.type == ChatItem.RESULT) {
            LinearLayout fallback = vbox();
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1,-2); fp.setMargins(dp(23),dp(1),0,dp(4)); fallback.setLayoutParams(fp);
            TextView result = text("  ⎿  " + shortenMultiline(item.body.toString(), 900), 10.5f, MUTED_2);
            result.setTypeface(Typeface.MONOSPACE); result.setTextIsSelectable(true);
            fallback.addView(result, lp(-1,-2));
            fallback.setOnLongClickListener(v -> { showToolActions(item); return true; });
            host.addView(fallback); item.renderedView = fallback;
            if (!rebuildingTranscript) UiMotion.toolIn(fallback);
            return;
        }

        LinearLayout row = vbox();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2);
        rp.setMargins(dp(20), dp(1), dp(2), dp(4));
        row.setLayoutParams(rp);

        LinearLayout top = hbox(); top.setGravity(Gravity.CENTER_VERTICAL);
        int statusColor = item.completed ? (item.resultError ? RED : GREEN) : ACCENT;
        int statusIcon = item.completed ? (item.resultError ? R.drawable.ic_close : R.drawable.ic_check) : (item.awaitingPermission ? R.drawable.ic_dot_ring : R.drawable.ic_dot_filled);
        TextView status = iconOnly(statusIcon,14, statusColor);
        top.addView(status, lp(dp(23), dp(25)));

        org.json.JSONObject input = null;
        try { input = new org.json.JSONObject(item.body.toString()); } catch (Exception ignored) {}
        String displayName = userFacingToolName(item.title, input);
        TextView name = text(displayName,12, TEXT); name.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(name, lp(-2, dp(25)));

        String summary = input == null ? "" : toolSummary(item.title, input);
        // Bash already renders the command on the next line; repeating it in the
        // title row makes the activity look noisy and causes the command to appear twice.
        if ("Bash".equalsIgnoreCase(item.title)) summary = "";
        if (!summary.isEmpty() && !summary.equals(displayName)) {
            TextView summaryView = text("  " + summary, 10.5f, MUTED);
            summaryView.setTypeface(Typeface.MONOSPACE); summaryView.setSingleLine(true);
            summaryView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(summaryView, new LinearLayout.LayoutParams(0, dp(25), 1));
        } else {
            top.addView(new View(this), new LinearLayout.LayoutParams(0, dp(25), 1));
        }

        int[] delta = item.completed && (item.diffAddedLines > 0 || item.diffDeletedLines > 0)
            ? new int[]{item.diffAddedLines,item.diffDeletedLines}
            : (input == null ? new int[]{0,0} : toolDelta(item.title, input));
        if (delta[0] > 0) top.addView(diffBadge("+" + delta[0], GREEN, item.completed && !rebuildingTranscript), lp(-2, dp(24)));
        if (delta[1] > 0) top.addView(diffBadge("−" + delta[1], RED, item.completed && !rebuildingTranscript), lp(-2, dp(24)));

        TextView toolMore = iconOnly(R.drawable.ic_more,18,MUTED_2); toolMore.setGravity(Gravity.CENTER);
        toolMore.setOnClickListener(v -> showToolActions(item));
        top.addView(toolMore, lp(dp(30), dp(25)));
        row.setOnLongClickListener(v -> { showToolActions(item); return true; });

        boolean hasDetails = item.result.length() > 0 || item.diff.length() > 0;
        if (item.completed && hasDetails) {
            TextView chevron = text(item.expanded ? "⌃" : "⌄", 11, MUTED_2); chevron.setGravity(Gravity.CENTER);
            top.addView(chevron, lp(dp(28), dp(25)));
            top.setOnClickListener(v -> toggleToolDetails(item));
        }
        row.addView(top, lp(-1,-2));

        // 运行中的 Bash 把命令行画在终端卡里（见下），这里只负责已完成的那行。
        if (item.completed && "Bash".equalsIgnoreCase(item.title) && input != null) {
            String command = input.optString("command", "").trim();
            if (!command.isEmpty()) {
                TextView cmd = text(item.expanded ? command : truncateCommand(command), 10.5f, TEXT);
                cmd.setTypeface(Typeface.MONOSPACE); cmd.setTextIsSelectable(true);
                cmd.setPadding(dp(22), 0, dp(4), dp(2));
                cmd.setLineSpacing(0, 1.06f);
                row.addView(cmd, lp(-1,-2));
            }
        }

        if (item.completed && hasDetails) {
            String resultText = item.result.toString();
            if (item.expanded) {
                // Diff and output share one container so opening and closing the card animates a
                // single height instead of swapping the transcript between two layouts.
                LinearLayout details = vbox();
                item.detailsView = details;
                if (item.diff.length() > 0) {
                    TextView diff = text("",10.5f, TEXT);
                    diff.setText(colorDiff(shortenMultiline(item.diff.toString(), TOOL_DIFF_MAX_CHARS)));
                    diff.setTypeface(Typeface.MONOSPACE); diff.setTextIsSelectable(true);
                    diff.setHorizontallyScrolling(true); diff.setHorizontalScrollBarEnabled(true);
                    diff.setPadding(dp(10), dp(8), dp(10), dp(8)); diff.setLineSpacing(0,1.04f);
                    diff.setBackground(round(TERMINAL_BG,10, Color.TRANSPARENT, 0));
                    HorizontalScrollView diffScroll = new HorizontalScrollView(this);
                    diffScroll.setHorizontalScrollBarEnabled(true); diffScroll.setFillViewport(false);
                    diffScroll.addView(diff, new HorizontalScrollView.LayoutParams(-2,-2));
                    LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1,-2); dpv.setMargins(dp(23),dp(3),0,dp(4));
                    details.addView(diffScroll,dpv);
                }
                if (!resultText.trim().isEmpty()) {
                    String output = shortenMultiline(resultText, 12000);
                    LinearLayout outputPanel = vbox();
                    outputPanel.setPadding(dp(22), dp(2), dp(4), dp(4));
                    outputPanel.setBackground(round(TERMINAL_BG, 10, Color.TRANSPARENT, 0));
                    if (output.contains("```") && !item.resultError) {
                        // Agent outputs frequently contain fenced source. Reuse the
                        // full Markdown renderer so expanded tool results get the
                        // same language labels, copy action, wrapping and syntax
                        // colors as normal assistant replies.
                        outputPanel.addView(MarkdownRenderer.render(this, output, 10.5f, neonTheme, lightTheme), lp(-1,-2));
                    } else {
                        TextView body = text("",10.5f, item.resultError ? RED : MUTED_2);
                        body.setText(item.resultError ? output : MarkdownRenderer.highlight(output, toolOutputLanguage(item), neonTheme, lightTheme));
                        body.setTypeface(Typeface.MONOSPACE); body.setTextIsSelectable(true);
                        body.setLineSpacing(0,1.08f);
                        body.setPadding(dp(8), dp(6), dp(8), dp(6));
                        outputPanel.addView(body, lp(-1,-2));
                    }
                    details.addView(outputPanel,lp(-1,-2));
                }
                boolean grow = pendingDetailsExpand == item;
                if (grow) pendingDetailsExpand = null;
                row.addView(details, grow ? new LinearLayout.LayoutParams(-1,0) : lp(-1,-2));
            } else {
                item.detailsView = null;
                int changed = item.diffAddedLines + item.diffDeletedLines;
                int lines = countLines(resultText);
                String compact = item.diff.length() > 0
                    ? changed + " 行已修改 · 点按展开"
                    : compactResult(item.title, resultText, lines, item.exitCode);
                TextView info = text("  ⎿  " + compact,10.5f, item.resultError ? RED : MUTED_2);
                info.setTypeface(Typeface.MONOSPACE); info.setPadding(dp(22),0,0,dp(2));
                row.addView(info,lp(-1,-2));
            }
        } else if (!item.completed) {
            final boolean bash = "Bash".equalsIgnoreCase(item.title);
            if (bash && input != null) {
                // 运行中的 Bash 收进一张终端卡：状态头 / 命令 / 分隔线 / 实时输出，
                // 而不是「一行状态 + 一坨黑块」各占一块地方。
                LinearLayout card = vbox();
                card.setPadding(dp(10),dp(7),dp(10),dp(8));
                card.setBackground(round(TERMINAL_BG,10,Color.TRANSPARENT,0));
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2);
                cardLp.setMargins(dp(23),dp(4),0,dp(4));
                card.setLayoutParams(cardLp);

                LinearLayout head = hbox(); head.setGravity(Gravity.CENTER_VERTICAL);
                View dot = new View(this);
                dot.setBackground(round(item.awaitingPermission ? MUTED_2 : ACCENT,4,Color.TRANSPARENT,0));
                head.addView(dot, lp(dp(7),dp(7)));
                TextView tag = text("bash",10.5f,MUTED_2); tag.setTypeface(Typeface.MONOSPACE); tag.setGravity(Gravity.CENTER_VERTICAL); tag.setPadding(dp(7),0,0,0);
                head.addView(tag, lp(-2,dp(20)));
                head.addView(new View(this), new LinearLayout.LayoutParams(0,1,1));
                TextView state = text(item.awaitingPermission ? "等待授权…" : runningToolLabel(item),10.5f, item.awaitingPermission ? ACCENT : MUTED_2);
                state.setTypeface(Typeface.MONOSPACE); state.setSingleLine(true); state.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
                item.runningView = state;
                head.addView(state, lp(-2,dp(20)));
                card.addView(head, lp(-1,dp(22)));

                String runningCommand = input.optString("command","").trim();
                if (!runningCommand.isEmpty()) {
                    TextView cmd = text(item.expanded ? runningCommand : truncateCommand(runningCommand),10.5f,TEXT);
                    cmd.setTypeface(Typeface.MONOSPACE); cmd.setTextIsSelectable(true); cmd.setLineSpacing(0,1.06f);
                    cmd.setPadding(0,dp(1),0,dp(7));
                    card.addView(cmd, lp(-1,-2));
                }
                card.addView(dividerHorizontal(), lp(-1,Math.max(1,dp(1))));

                String live = liveOutputPreview(item, 7000, wide ? 10 : 7);
                if (!live.isEmpty()) {
                    TextView body = text(live,10.5f, MUTED);
                    // Live output is re-setText'd every 300 ms; a selectable view would drop any
                    // in-progress selection on each refresh and pay for a MovementMethod each time.
                    body.setTypeface(Typeface.MONOSPACE);
                    body.setPadding(0,dp(6),0,0); body.setLineSpacing(0,1.08f);
                    item.liveOutputView = body;
                    card.addView(body,lp(-1,-2));
                } else if (!item.awaitingPermission) {
                    TextView waiting = text("等待程序输出…",10.5f,MUTED_2);
                    waiting.setTypeface(Typeface.MONOSPACE); waiting.setPadding(0,dp(6),0,0);
                    card.addView(waiting, lp(-1,-2));
                }
                row.addView(card);
            } else {
                TextView running = text(runningToolLabel(item),10.5f, item.awaitingPermission ? ACCENT : MUTED_2);
                running.setTypeface(Typeface.MONOSPACE); running.setPadding(dp(22),0,0,dp(2));
                item.runningView=running;
                row.addView(running,lp(-1,-2));
                String live = liveOutputTail(item, 5000);
                if (!live.isEmpty()) {
                    TextView body = text(live,10.5f, MUTED);
                    body.setTypeface(Typeface.MONOSPACE);
                    body.setPadding(dp(8),dp(6),dp(8),dp(6)); body.setBackground(round(TERMINAL_BG,10,Color.TRANSPARENT,0)); item.liveOutputView=body;
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1,-2); bp.setMargins(dp(23),dp(3),0,dp(3)); row.addView(body,bp);
                }
            }
        }

        host.addView(row); item.renderedView = row;
        if(!item.completed)scheduleToolElapsedTicker();
        UiMotion.bindInteractive(row);
        if (!rebuildingTranscript) UiMotion.toolIn(row);
    }

    private void addCollapsedToolActivity(CollapsedToolActivity activity,LinearLayout host){
        if(activity==null||host==null||activity.members.isEmpty())return;
        LinearLayout root=vbox();
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(dp(16),dp(2),dp(2),dp(5));root.setLayoutParams(rp);
        LinearLayout top=hbox();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(6),dp(2),dp(4),dp(2));
        int completed=collapsedCompletedCount(activity),failed=collapsedFailedCount(activity);
        boolean done=activity.batchCompleted&&completed>=activity.plan.toolIds.size();
        int color=failed>0?RED:(done?GREEN:ACCENT);
        TextView state=text(done?"✓":"◌",12,color);state.setTypeface(Typeface.MONOSPACE);state.setGravity(Gravity.CENTER);top.addView(state,lp(dp(24),dp(28)));
        TextView label=text(collapsedActivityLabel(activity),11,TEXT);label.setTypeface(Typeface.DEFAULT_BOLD);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(label,new LinearLayout.LayoutParams(0,dp(28),1));
        TextView chevron=text(activity.expanded?"⌃":"⌄",12,MUTED_2);chevron.setGravity(Gravity.CENTER);top.addView(chevron,lp(dp(28),dp(28)));
        View.OnClickListener toggle=v->toggleCollapsedActivity(activity);
        top.setOnClickListener(toggle);root.setOnClickListener(toggle);root.addView(top,lp(-1,-2));
        String hint=activity.plan.latestHint;
        String detail=hint.isEmpty()?"⎿  "+activity.plan.toolIds.size()+" 项工具 · 点按"+(activity.expanded?"收起":"展开"):"⎿  "+hint+" · 点按"+(activity.expanded?"收起":"展开");
        TextView sub=text(detail,10.5f,MUTED_2);sub.setTypeface(Typeface.MONOSPACE);sub.setPadding(dp(24),0,dp(8),dp(2));sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(sub,lp(-1,dp(24)));
        if(activity.expanded){
            LinearLayout children=vbox();children.setPadding(dp(4),dp(0),0,dp(2));
            activity.childrenHost=children;
            boolean wasRebuilding=rebuildingTranscript;rebuildingTranscript=true;
            for(ChatItem item:activity.members)addToolCard(item,children);
            rebuildingTranscript=wasRebuilding;
            boolean grow=pendingActivityExpand==activity;
            if(grow)pendingActivityExpand=null;
            if(children.getChildCount()>0)root.addView(children, grow ? new LinearLayout.LayoutParams(-1,0) : lp(-1,-2));
        }else{
            activity.childrenHost=null;
            for(ChatItem item:activity.members){item.renderedView=null;item.runningView=null;}
        }
        host.addView(root);activity.renderedView=root;
        root.setContentDescription((done?"已完成":"正在执行")+collapsedActivityLabel(activity)+"，点按"+(activity.expanded?"收起":"展开"));
        UiMotion.bindInteractive(root);
    }

    private String collapsedActivityLabel(CollapsedToolActivity activity){
        ArrayList<String> parts=new ArrayList<>();
        if(activity.plan.searchPatterns>0)parts.add("搜索 "+activity.plan.searchPatterns+" 个模式");
        if(activity.plan.readRequests>0)parts.add("读取 "+activity.plan.readRequests+" 个文件");
        if(activity.plan.locations>0)parts.add("查看 "+activity.plan.locations+" 个位置");
        StringBuilder label=new StringBuilder(activity.batchCompleted?"已":"正在");
        for(int i=0;i<parts.size();i++){if(i>0)label.append("、");label.append(parts.get(i));}
        int failed=collapsedFailedCount(activity);
        int missing=Math.max(0,activity.plan.toolIds.size()-collapsedCompletedCount(activity));
        if(failed>0)label.append(" · ").append(failed).append(" 项失败");
        else if(activity.batchCompleted&&missing>0)label.append(" · ").append(missing).append(" 项未完成");
        return label.toString();
    }

    private int collapsedCompletedCount(CollapsedToolActivity activity){
        int count=0;for(ChatItem item:activity.members)if(item.completed)count++;return count;
    }

    private int collapsedFailedCount(CollapsedToolActivity activity){
        int count=0;for(ChatItem item:activity.members)if(item.completed&&item.resultError)count++;return count;
    }

    private void refreshCollapsedToolActivity(CollapsedToolActivity activity){
        if(activity==null)return;
        View old=activity.renderedView;
        if(old==null){if(chatMessages!=null&&!activity.members.isEmpty())addCollapsedToolActivity(activity,conversationHost());return;}
        if(!(old.getParent() instanceof LinearLayout))return;
        LinearLayout host=(LinearLayout)old.getParent();int index=host.indexOfChild(old);if(index<0)return;
        int oldY=chatScroll==null?0:chatScroll.getScrollY();boolean follow=chatAutoFollow;
        host.removeViewAt(index);activity.renderedView=null;activity.childrenHost=null;
        for(ChatItem item:activity.members){item.renderedView=null;item.runningView=null;}
        boolean wasRebuilding=rebuildingTranscript;rebuildingTranscript=true;addCollapsedToolActivity(activity,host);rebuildingTranscript=wasRebuilding;
        View replacement=activity.renderedView;
        if(replacement!=null&&replacement.getParent()==host){host.removeView(replacement);host.addView(replacement,Math.min(index,host.getChildCount()));UiMotion.contentUpdated(replacement,activity.batchCompleted);}
        if(chatScroll!=null)chatScroll.post(()->{if(follow)scrollChatNow();else chatScroll.scrollTo(0,oldY);});
        pendingActivityExpand=null;
    }

    private void showToolActions(ChatItem item) {
        if (item == null) return;
        if (item.type == ChatItem.RESULT) {
            showChoicePicker("工具结果", new String[]{"复制输出"}, -1, which -> copyTextToClipboard("元境 tool output", item.body.toString()));
            return;
        }
        org.json.JSONObject input = null;
        try { input = new org.json.JSONObject(item.body.toString()); } catch (Exception ignored) { }
        String command = input == null ? "" : input.optString("command", "").trim();
        boolean hasCommand = "Bash".equalsIgnoreCase(item.title) && !command.isEmpty();
        boolean hasFinalOutput = item.result.length() > 0;
        boolean hasLiveOutput; synchronized (item) { hasLiveOutput = item.liveOutput.length() > 0; }
        boolean hasOutput = hasFinalOutput || hasLiveOutput;
        boolean hasDiff = item.diff.length() > 0;
        if (hasCommand && hasOutput) {
            final String cmd = command;
            if (!item.completed) {
                showChoicePicker("工具操作", new String[]{"复制命令", "复制实时输出"}, -1, which -> {
                    if (which == 0) copyTextToClipboard("元境 Bash command", cmd);
                    else copyTextToClipboard("元境 live tool output", liveOutputTail(item, 40000));
                });
            } else {
                showChoicePicker("工具操作", new String[]{"复制命令", "复制输出", item.expanded ? "折叠输出" : "展开输出"}, -1, which -> {
                    if (which == 0) copyTextToClipboard("元境 Bash command", cmd);
                    else if (which == 1) copyTextToClipboard("元境 tool output", item.result.toString());
                    else { item.expanded = !item.expanded; refreshToolItem(item); }
                });
            }
        } else if (hasCommand) {
            final String cmd = command;
            showChoicePicker("工具操作", new String[]{"复制命令"}, -1, which -> copyTextToClipboard("元境 Bash command", cmd));
        } else if (hasOutput && hasDiff) {
            showChoicePicker("工具操作", new String[]{"复制 Diff", "复制输出", item.expanded ? "折叠修改" : "展开修改"}, -1, which -> {
                if (which == 0) copyTextToClipboard("元境 unified diff", item.diff.toString());
                else if (which == 1) copyTextToClipboard("元境 tool output", item.result.toString());
                else { item.expanded = !item.expanded; refreshToolItem(item); }
            });
        } else if (hasOutput) {
            showChoicePicker("工具操作", new String[]{"复制输出", item.expanded ? "折叠输出" : "展开输出"}, -1, which -> {
                if (which == 0) copyTextToClipboard("元境 tool output", item.result.toString());
                else { item.expanded = !item.expanded; refreshToolItem(item); }
            });
        } else {
            showChoicePicker("工具操作", new String[]{"复制参数"}, -1, which -> copyTextToClipboard("元境 tool input", item.body.toString()));
        }
    }

    private void copyTextToClipboard(String label, String value) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText(label, value == null ? "" : value));
        toast("已复制");
    }

    private void refreshToolItem(ChatItem item) {
        final long stallStartedAt = SystemClock.elapsedRealtime();
        CollapsedToolActivity activity=collapsedToolActivity(item);
        if(activity!=null){refreshCollapsedToolActivity(activity);return;}
        if(item==null||item.renderedView==null||!(item.renderedView.getParent() instanceof LinearLayout))return;
        LinearLayout host=(LinearLayout)item.renderedView.getParent();
        int index=host.indexOfChild(item.renderedView);if(index<0)return;
        int oldY=chatScroll==null?0:chatScroll.getScrollY();boolean follow=chatAutoFollow;
        host.removeViewAt(index);rebuildingTranscript=true;addToolCard(item,host);rebuildingTranscript=false;
        View replacement=item.renderedView;
        if(replacement!=null&&replacement.getParent()==host){host.removeView(replacement);host.addView(replacement,Math.min(index,host.getChildCount()));UiMotion.contentUpdated(replacement,item.completed);}
        if(chatScroll!=null)chatScroll.post(()->{if(follow)scrollChatNow();else chatScroll.scrollTo(0,oldY);});
        pendingDetailsExpand=null;
        reportStall("refreshToolItem", stallStartedAt);
    }

    /** Opening a tool card grows its diff/output block; closing shrinks it before the row is rebuilt. */
    private void toggleToolDetails(ChatItem item) {
        if (item == null) return;
        boolean follow = chatAutoFollow;
        int y = chatScroll == null ? 0 : chatScroll.getScrollY();
        if (item.expanded) {
            item.expanded = false;
            View details = item.detailsView;
            if (details != null && details.getVisibility() == View.VISIBLE && !rebuildingTranscript) {
                UiMotion.collapseBar(details, () -> { refreshToolItem(item); restoreTranscriptScroll(follow, y); });
                return;
            }
        } else {
            item.expanded = true;
            pendingDetailsExpand = item;
        }
        refreshToolItem(item);
        restoreTranscriptScroll(follow, y);
        View details = item.detailsView;
        if (details != null && details.getLayoutParams() != null && details.getLayoutParams().height == 0)
            details.post(() -> UiMotion.expandBarToContent(details, () -> { if (chatAutoFollow) scrollChatNow(); }));
    }

    /** Same growth for a grouped tool batch; its children list is the animated block. */
    private void toggleCollapsedActivity(CollapsedToolActivity activity) {
        if (activity == null) return;
        boolean follow = chatAutoFollow;
        int y = chatScroll == null ? 0 : chatScroll.getScrollY();
        if (activity.expanded) {
            activity.expanded = false;
            View children = activity.childrenHost;
            if (children != null && children.getVisibility() == View.VISIBLE && !rebuildingTranscript) {
                UiMotion.collapseBar(children, () -> { refreshCollapsedToolActivity(activity); restoreTranscriptScroll(follow, y); });
                return;
            }
        } else {
            activity.expanded = true;
            pendingActivityExpand = activity;
        }
        refreshCollapsedToolActivity(activity);
        restoreTranscriptScroll(follow, y);
        View children = activity.childrenHost;
        if (children != null && children.getLayoutParams() != null && children.getLayoutParams().height == 0)
            children.post(() -> UiMotion.expandBarToContent(children, () -> { if (chatAutoFollow) scrollChatNow(); }));
    }

    private void restoreTranscriptScroll(boolean follow, int y) {
        if (chatScroll == null) return;
        chatScroll.post(() -> { if (follow) scrollChatNow(); else chatScroll.scrollTo(0, y); });
    }

    private ChatItem currentRunningBash() {
        for (int i = transcript.size() - 1; i >= 0; i--) {
            ChatItem item = transcript.get(i);
            if (item.type == ChatItem.TOOL && !item.completed && "Bash".equalsIgnoreCase(item.title)) return item;
        }
        return null;
    }

    private long currentToolElapsed(ChatItem item){
        if(item==null)return 0L;
        long local=Math.max(0L,android.os.SystemClock.elapsedRealtime()-item.startedAt);
        synchronized(item){return Math.max(item.elapsedMs,item.completed?item.elapsedMs:local);}
    }

    private String runningToolLabel(ChatItem item){
        if(item.awaitingPermission)return "  ⎿  等待授权…";
        if("Bash".equalsIgnoreCase(item.title)){
            int outChars,errChars;
            synchronized(item){outChars=item.liveStdoutChars;errChars=item.liveStderrChars;}
            // 终端卡头部只放耗时和输出量，不再拼一长串状态文字。
            return formatElapsed(currentToolElapsed(item))+" · 输出 "+formatCharCount(outChars)+(errChars>0?" · 错误 "+formatCharCount(errChars):"");
        }
        return "  ⎿  正在执行 "+formatElapsed(currentToolElapsed(item))+"…";
    }

    private void scheduleToolElapsedTicker(){
        if(toolElapsedTickerPosted)return;
        toolElapsedTickerPosted=true;
        uiHandler.postDelayed(toolElapsedTicker,500L);
    }

    private void refreshToolElapsed(){
        toolElapsedTickerPosted=false;
        boolean running=false;
        for(ChatItem item:transcript){
            if(item.type!=ChatItem.TOOL||item.completed||item.runningView==null||item.runningView.getParent()==null)continue;
            running=true;
            item.runningView.setText(runningToolLabel(item));
        }
        if(running)scheduleToolElapsedTicker();
    }

    private void appendLiveChunk(ChatItem item, String chunk, boolean stderr) {
        if (item == null || chunk == null || chunk.isEmpty()) return;
        String clean = chunk.replace("\r\n", "\n").replace('\r', '\n');
        synchronized (item) {
            if (stderr) item.liveStderrChars += clean.length(); else item.liveStdoutChars += clean.length();
            if (item.liveOutput.length() > 0 && item.liveLastWasStderr != stderr) {
                if (item.liveOutput.charAt(item.liveOutput.length() - 1) != '\n') item.liveOutput.append('\n');
                item.liveOutput.append(stderr ? "[stderr]\n" : "[stdout]\n");
            } else if (item.liveOutput.length() == 0 && stderr) {
                item.liveOutput.append("[stderr]\n");
            }
            item.liveLastWasStderr = stderr;
            item.liveOutput.append(clean);
            final int keep = 40000;
            if (item.liveOutput.length() > keep) item.liveOutput.delete(0, item.liveOutput.length() - keep);
        }
    }

    private String liveOutputTail(ChatItem item, int maxChars) {
        if (item == null) return "";
        synchronized (item) {
            int n = item.liveOutput.length();
            if (n == 0) return "";
            int start = Math.max(0, n - Math.max(1000, maxChars));
            String value = item.liveOutput.substring(start);
            if (start > 0) value = "… earlier output omitted …\n" + value;
            return value;
        }
    }

    /** Returns the newest live lines so the inline Bash row always shows current progress. */
    private String liveOutputPreview(ChatItem item, int maxChars, int maxLines) {
        String value = liveOutputTail(item, maxChars);
        if (value.isEmpty()) return value;
        String[] lines = value.split("\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].isEmpty()) end--;
        int start = Math.max(0, end - Math.max(1, maxLines));
        StringBuilder out = new StringBuilder();
        if (start > 0) out.append("… earlier live output omitted …\n");
        for (int i = start; i < end; i++) {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            out.append(lines[i]);
        }
        return out.toString();
    }

    private String formatElapsed(long elapsedMs) {
        long seconds = Math.max(0, elapsedMs) / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60L, seconds % 60L);
        return String.format(Locale.US, "%02d:%02d", minutes, seconds % 60L);
    }

    private String formatCharCount(int chars) {
        if (chars < 1000) return chars + " B";
        if (chars < 1000_000) return String.format(Locale.US, "%.1f KB", chars / 1000f);
        return String.format(Locale.US, "%.1f MB", chars / 1000_000f);
    }

    private TextView diffBadge(String label, int color, boolean animate) {
        TextView b = text(label,10.5f, color);
        b.setTypeface(Typeface.MONOSPACE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), 0, dp(4), 0);
        if (animate) UiMotion.contentUpdated(b,true);
        return b;
    }

    private String userFacingToolName(String name, org.json.JSONObject input) {
        if (name == null) return "Tool";
        if ("Root".equalsIgnoreCase(name)) return "Root 命令";
        if ("Shizuku".equalsIgnoreCase(name)) return "Shizuku 命令";
        if ("Edit".equalsIgnoreCase(name) || "MultiEdit".equalsIgnoreCase(name)) return "修改文件";
        if ("Write".equalsIgnoreCase(name)) return "写入文件";
        if ("Read".equalsIgnoreCase(name)) return "读取文件";
        if ("LS".equalsIgnoreCase(name) || "List".equalsIgnoreCase(name)) return "列出文件";
        if ("AndroidIntent".equalsIgnoreCase(name)) return "手机操作";
        return name;
    }

    private String truncateCommand(String command) {
        String[] lines = command.split("\\n", -1);
        String out = lines.length > 2 ? lines[0] + "\\n" + lines[1] + "…" : command;
        return shorten(out, 190);
    }

    private String compactResult(String tool, String result, int lines, int exitCode) {
        String clean = result == null ? "" : result.trim();
        if ("Bash".equalsIgnoreCase(tool)||"Root".equalsIgnoreCase(tool)||"Shizuku".equalsIgnoreCase(tool)) {
            if (exitCode != 0) {
                String why = firstUsefulErrorLine(clean);
                return "退出码 " + exitCode + (why.isEmpty() ? "" : " · " + shorten(why, 105)) + (lines > 1 ? " · 点按展开" : "");
            }
            return lines + (lines == 1 ? " 行" : " 行") + " · 点按展开";
        }
        if (clean.isEmpty()) return "已完成";
        if (lines == 1 && clean.length() <= 96) return clean;
        return lines + (lines == 1 ? " 行" : " 行") + " · 点按展开";
    }

    private String firstUsefulErrorLine(String text) {
        if (text == null) return "";
        String fallback = "";
        for (String line : text.split("\n")) {
            String x = line.trim(); if (x.isEmpty() || x.equals("[stderr]")) continue;
            if (fallback.isEmpty()) fallback = x;
            String l = x.toLowerCase(Locale.US);
            if (l.contains("error") || l.contains("failed") || l.contains("permission denied") || l.contains("unable to") || l.startsWith("e:") || l.contains("not found")) return x;
        }
        return fallback;
    }

    private boolean isPackageManagerTool(ChatItem item) {
        if (item == null) return false;
        try {
            JSONObject j = new JSONObject(item.body.toString());
            String c = j.optString("command", "").toLowerCase(Locale.US);
            return c.contains("pkg ") || c.contains("apt ") || c.contains("apt-get ") || c.contains("dpkg ");
        } catch (Exception e) { return false; }
    }

    private int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int n = 1; for (int i=0;i<s.length();i++) if (s.charAt(i)=='\n') n++;
        return n;
    }

    private int[] toolDelta(String name, org.json.JSONObject j) {
        int adds = 0, dels = 0;
        try {
            if ("Edit".equalsIgnoreCase(name)) {
                dels += countNonEmptyLines(j.optString("old_string", ""));
                adds += countNonEmptyLines(j.optString("new_string", ""));
            } else if ("Write".equalsIgnoreCase(name)) {
                adds += countNonEmptyLines(j.optString("content", ""));
            } else if ("MultiEdit".equalsIgnoreCase(name)) {
                org.json.JSONArray edits = j.optJSONArray("edits");
                if (edits != null) for (int i=0;i<edits.length();i++) {
                    org.json.JSONObject e = edits.optJSONObject(i); if (e == null) continue;
                    String oldText = e.optString("old_text", e.optString("old_string", ""));
                    String newText = e.optString("new_text", e.optString("new_string", ""));
                    dels += countNonEmptyLines(oldText);
                    adds += countNonEmptyLines(newText);
                }
            }
        } catch (Throwable ignored) {}
        return new int[]{adds,dels};
    }

    private int countNonEmptyLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int n = 0; for (String line : s.split("\\n",-1)) if (!line.isEmpty()) n++;
        return n;
    }

    private String toolOutputLanguage(ChatItem item) {
        if (item == null) return "text";
        try {
            JSONObject input = new JSONObject(item.body.toString());
            String path = input.optString("file_path", input.optString("path", ""));
            String command = input.optString("command", "");
            String source = (path + " " + command).toLowerCase(Locale.US);
            if (source.endsWith(".java") || source.contains(" javac")) return "java";
            if (source.endsWith(".kt")) return "kotlin";
            if (source.endsWith(".py") || source.contains("python")) return "python";
            if (source.endsWith(".json")) return "json";
            if (source.endsWith(".yml") || source.endsWith(".yaml")) return "yaml";
            if (source.endsWith(".js")) return "javascript";
            if (source.endsWith(".sh") || source.contains(" bash") || source.contains(" shell")) return "bash";
        } catch (Exception ignored) { }
        return "text";
    }

    private String toolSummary(String name, org.json.JSONObject j){
        if("Bash".equalsIgnoreCase(name)||"Shizuku".equalsIgnoreCase(name)) return truncateCommand(j.optString("command","").replace('\n',' '));
        if("Agent".equalsIgnoreCase(name)||"Task".equalsIgnoreCase(name)) return shorten(j.optString("subagent_type","general-purpose") + " · " + j.optString("description",""),82);
        if("TaskOutput".equalsIgnoreCase(name)||"TaskStop".equalsIgnoreCase(name)) return shorten(j.optString("task_id",j.optString("taskId","")),82);
        if("Read".equalsIgnoreCase(name)||"Write".equalsIgnoreCase(name)||"Edit".equalsIgnoreCase(name))
            return shorten(j.optString("file_path",j.optString("path","")),82);
        if("MultiEdit".equalsIgnoreCase(name)) {
            org.json.JSONArray edits = j.optJSONArray("edits");
            if (edits == null || edits.length() == 0) return "";
            org.json.JSONObject first = edits.optJSONObject(0);
            String path = first == null ? "" : first.optString("path", "");
            if (edits.length() == 1) return shorten(path, 82);
            return shorten(path, 58) + " · " + edits.length() + " edits";
        }
        if("Grep".equalsIgnoreCase(name)) return shorten(j.optString("pattern",""),64);
        if("Glob".equalsIgnoreCase(name)) return shorten(j.optString("pattern",""),64);
        if("Tree".equalsIgnoreCase(name)||"List".equalsIgnoreCase(name)||"LS".equalsIgnoreCase(name)) return shorten(j.optString("path","."),72);
        if("Move".equalsIgnoreCase(name)) return shorten(j.optString("source","")+" → "+j.optString("destination",""),82);
        if("Delete".equalsIgnoreCase(name)||"删除".equalsIgnoreCase(name)||"Mkdir".equalsIgnoreCase(name)||"Stat".equalsIgnoreCase(name)) return shorten(j.optString("path",""),82);
        return "";
    }

    private CharSequence colorDiff(String src) {
        SpannableString s = new SpannableString(src == null ? "" : src); int pos=0;
        for (String line : (src == null ? "" : src).split("\\n", -1)) {
            int color = TEXT, background = Color.TRANSPARENT;
            if (line.startsWith("+") && !line.startsWith("+++")) { color = GREEN; background = lightTheme ? Color.rgb(228,244,235) : neonTheme ? Color.rgb(9,48,42) : Color.rgb(25,45,31); }
            else if (line.startsWith("-") && !line.startsWith("---")) { color = RED; background = lightTheme ? Color.rgb(251,232,234) : neonTheme ? Color.rgb(54,20,40) : Color.rgb(52,29,29); }
            else if (line.startsWith("@@")) color = ACCENT;
            else if (line.startsWith("diff ") || line.startsWith("--- diff ---")) color = MUTED;
            int end = Math.min(s.length(), pos + line.length()); if (end > pos) { s.setSpan(new ForegroundColorSpan(color),pos,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); if(background!=Color.TRANSPARENT)s.setSpan(new BackgroundColorSpan(background),pos,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); } pos += line.length()+1;
        }
        return s;
    }

    private void renderTerminal(FrameLayout host) {
        host.removeAllViews();
        try {
            if (terminalPane == null) terminalPane = new TermuxTerminalPane(this, runtime, neonTheme, lightTheme);
            else terminalPane.applyTheme(neonTheme,lightTheme);
            if (terminalPane.getParent() != null) ((ViewGroup)terminalPane.getParent()).removeView(terminalPane);
            host.addView(terminalPane, new FrameLayout.LayoutParams(-1,-1));
            terminalPane.setKeyboardOffset(keyboardVisible?keyboardOffset:0,false);
            animatePane(host);
            // 不自动弹输入法：切到终端通常只是看输出，点终端区域或工具栏的键盘键才唤起。
        } catch (Throwable t) {
            LinearLayout error=vbox(); error.setGravity(Gravity.CENTER); error.setPadding(dp(22),dp(22),dp(22),dp(22));
            TextView title=text("终端启动失败",16,RED); title.setTypeface(Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER); error.addView(title,lp(-1,dp(34)));
            TextView msg=text(String.valueOf(t),11,MUTED); msg.setTypeface(Typeface.MONOSPACE); msg.setTextIsSelectable(true); msg.setGravity(Gravity.CENTER); error.addView(msg,lp(-1,-2));
            TextView repair=pill("修复运行环境",TEXT); repair.setOnClickListener(v->{runtime.repairIfInstalled();terminalPane=null;renderTerminal(host);});
            LinearLayout.LayoutParams pp=lp(dp(150),dp(40)); pp.setMargins(0,dp(16),0,0); error.addView(repair,pp); host.addView(error,new FrameLayout.LayoutParams(-1,-1));
        }
    }

    private void renderFiles(FrameLayout host) {
        filesWorkspaceHost = host;
        if (browserDir == null || !browserDir.isDirectory()) browserDir = new File(config.projectDirectory);
        showFileBrowser();
    }

    private void showFileBrowser() {
        if (filesWorkspaceHost == null) return;
        fileEditor = null; fileEditorFile = null; fileEditorStatus = null;
        filesWorkspaceHost.removeAllViews();
        LinearLayout page=vbox(); page.setBackgroundColor(SURFACE);
        // 顶栏：返回上一级 + 路径 + 筛选开关 + 新建 + 回项目根。
        LinearLayout header=hbox(); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(8),dp(4),dp(8),dp(4));
        TextView up=smallIcon(R.drawable.ic_back); up.setOnClickListener(v->{if(browserDir!=null&&browserDir.getParentFile()!=null){browserDir=browserDir.getParentFile();refreshFiles();}}); header.addView(up,lp(dp(44),dp(44)));
        filesPath=text("",12,MUTED); filesPath.setSingleLine(true); filesPath.setEllipsize(android.text.TextUtils.TruncateAt.START); header.addView(filesPath,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView search=smallIcon(R.drawable.ic_search); search.setContentDescription("筛选文件"); search.setOnClickListener(v->toggleFileFilter()); header.addView(search,lp(dp(44),dp(44)));
        TextView add=smallIcon(R.drawable.ic_plus); add.setContentDescription("新建文件或文件夹"); add.setOnClickListener(v->showFileCreateMenu()); header.addView(add,lp(dp(44),dp(44)));
        TextView root=dialogButton("项目",MUTED,false); root.setOnClickListener(v->{browserDir=new File(config.projectDirectory);refreshFiles();});header.addView(root,new LinearLayout.LayoutParams(dp(88),dp(44)));
        page.addView(header,lp(-1,dp(52)));
        View line=dividerHorizontal(); page.addView(line,lp(-1,1));
        // 筛选条（默认收起）：按关键字过滤当前目录，右侧实时显示条目数。
        LinearLayout filterRow=hbox();filterRow.setGravity(Gravity.CENTER_VERTICAL);filterRow.setPadding(dp(10),dp(2),dp(10),dp(2));
        fileFilterBox=input("",false);fileFilterBox.setHint("筛选当前目录…");fileFilterBox.setTextSize(12);
        fileFilterBox.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){if(fileFilterRefreshPosted)return;fileFilterRefreshPosted=true;uiHandler.postDelayed(()->{fileFilterRefreshPosted=false;refreshFiles();},140);}@Override public void afterTextChanged(Editable e){}});
        filterRow.addView(fileFilterBox,new LinearLayout.LayoutParams(0,dp(40),1));
        filesCount=text("",10.5f,MUTED_2);filesCount.setPadding(dp(8),0,0,0);filterRow.addView(filesCount,lp(-2,dp(40)));
        fileFilterHost=vbox();fileFilterHost.addView(filterRow,lp(-1,-2));fileFilterHost.setVisibility(View.GONE);
        page.addView(fileFilterHost,lp(-1,-2));
        filesList=new ListView(this); filesList.setBackgroundColor(SURFACE); filesList.setDivider(null); filesList.setSelector(android.R.color.transparent); filesList.setVerticalScrollBarEnabled(false);
        page.addView(filesList,new LinearLayout.LayoutParams(-1,0,1)); filesWorkspaceHost.addView(page,new FrameLayout.LayoutParams(-1,-1)); animatePane(page); refreshFiles();
    }

    /** 展开/收起筛选条；收起时清空关键字并刷新。 */
    private void toggleFileFilter(){
        if(fileFilterHost==null||fileFilterBox==null)return;
        boolean show=fileFilterHost.getVisibility()!=View.VISIBLE;
        fileFilterHost.setVisibility(show?View.VISIBLE:View.GONE);
        fileFilterBox.setText("");
        if(show)fileFilterBox.requestFocus();else refreshFiles();
    }

    /** 当前生效的筛选关键字（小写）；筛选条收起＝不过滤。 */
    private String activeFileFilter(){
        return fileFilterBox==null||fileFilterHost==null||fileFilterHost.getVisibility()!=View.VISIBLE?"":fileFilterBox.getText().toString().trim().toLowerCase(Locale.US);
    }

    private void refreshFiles() {
        if(browserDir==null||filesList==null)return;
        UiMotion.setTextCrossfade(filesPath,relativeProjectPath(browserDir));
        String filter=activeFileFilter();
        File[] xs=browserDir.listFiles(); List<File> list=new ArrayList<>(); if(xs!=null)list.addAll(Arrays.asList(xs));
        Collections.sort(list,(a,b)->{if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());});
        if(!filter.isEmpty()){
            List<File> matched=new ArrayList<>();
            for(File f:list){
                String name=f.getName().toLowerCase(Locale.US);
                if(name.contains(filter))matched.add(f);
                else if(f.isDirectory()){ // 目录名命中时不过滤其内容，保持可下钻
                    if(name.contains(filter))matched.add(f);
                }
            }
            list=matched;
        }
        if(filesCount!=null)filesCount.setText(list.isEmpty()?"0 项":list.size()+" 项");
        final List<File> shown=list;
        List<String> names=new ArrayList<>();for(File f:shown)names.add(f.getName());
        ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){
            @Override public View getView(int p,View c,ViewGroup parent){
                LinearLayout row=(LinearLayout)(c==null?fileCardRow():c);
                bindFileCardRow(row,shown.get(p),p);
                return row;
            }
        };
        filesList.setAdapter(a);
        filesList.setOnItemClickListener((parent,v,pos,id)->{File f=shown.get(pos);if(f.isDirectory()){browserDir=f;refreshFiles();}else openEditor(f);});
        filesList.setOnItemLongClickListener((parent,v,pos,id)->{showFileActions(shown.get(pos));return true;});
    }

    /** 文件卡片：类型图标 + 名称 + 元信息（大小/条目数 · 修改时间）；目录行带 chevron。 */
    private LinearLayout fileCardRow(){
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(6),dp(10),dp(6));
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.setMargins(dp(6),0,dp(6),dp(4));
        row.setLayoutParams(rowLp);
        row.setBackground(round(SURFACE_2,14,BORDER_SOFT,1));
        // IconTextView：空文本时把左侧矢量图水平居中（普通 TextView 会把图钉在 paddingLeft，导致图标偏左）。
        TextView icon=new IconTextView(this);icon.setTag("file-icon");icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp=lp(dp(38),dp(38));iconLp.setMargins(0,0,dp(10),0);icon.setLayoutParams(iconLp);
        row.addView(icon);
        LinearLayout mid=vbox();
        TextView name=text("",13,TEXT);name.setTag("file-name");name.setTypeface(Typeface.MONOSPACE);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        mid.addView(name,lp(-1,dp(22)));
        TextView meta=text("",10.5f,MUTED_2);meta.setTag("file-meta");meta.setSingleLine(true);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(meta,lp(-1,dp(18)));
        row.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        TextView chevron=new IconTextView(this);chevron.setTag("file-chevron");chevron.setGravity(Gravity.CENTER);
        row.addView(chevron,lp(dp(22),dp(38)));
        return row;
    }

    /** 把一条 File 数据绑到卡片上；命中筛选关键字时高亮文件名。 */
    private void bindFileCardRow(LinearLayout row,File f,int position){
        boolean dir=f.isDirectory();
        TextView icon=(TextView)row.findViewWithTag("file-icon");
        TextView name=(TextView)row.findViewWithTag("file-name");
        TextView meta=(TextView)row.findViewWithTag("file-meta");
        TextView chevron=(TextView)row.findViewWithTag("file-chevron");
        int tint=fileTintFor(f);
        icon.setText("");icon.setBackground(round(mixColor(tint,SURFACE_3,.72f),12,Color.TRANSPARENT,0));
        int glyphRes=fileIconRes(f);
        if(glyphRes!=0){icon.setCompoundDrawables(vectorIcon(glyphRes,17,tint),null,null,null);icon.setTextColor(tint);}
        else{icon.setCompoundDrawables(null,null,null,null);icon.setText(fileGlyph(f));icon.setTextColor(tint);icon.setTextSize(15);icon.setTypeface(Typeface.MONOSPACE);}
        String filter=activeFileFilter();
        String raw=f.getName();
        if(!filter.isEmpty()&&raw.toLowerCase(Locale.US).contains(filter)){
            name.setText(highlightFileName(raw,filter));
        }else name.setText(raw);
        meta.setText(fileMeta(f));
        meta.setTextColor(dir?MUTED:MUTED_2);
        if(dir){chevron.setText("");chevron.setCompoundDrawables(vectorIcon(R.drawable.ic_chevron_right,13,MUTED_2),null,null,null);chevron.setBackground(null);}
        else{chevron.setCompoundDrawables(null,null,null,null);chevron.setBackground(null);}
        UiMotion.listItemIn(row,position);
        UiMotion.bindInteractive(row);
    }

    /** 文件名里命中筛选片段的部分用 accent 前景色标出。 */
    private android.text.SpannableString highlightFileName(String raw,String filter){
        android.text.SpannableString span=new android.text.SpannableString(raw);
        int index=raw.toLowerCase(Locale.US).indexOf(filter);
        if(index>=0&&filter.length()>0)span.setSpan(new android.text.style.ForegroundColorSpan(ACCENT),index,index+filter.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    /** 行内小角标：目录显示子项数，文件显示人类可读大小 + 修改时间。 */
    private String fileMeta(File f){
        StringBuilder out=new StringBuilder();
        if(f.isDirectory()){
            File[] xs=f.listFiles();int n=xs==null?0:xs.length;
            out.append(n==0?"空文件夹":n+" 项");
        }else{
            out.append(formatFileSize(f.length()));
        }
        long modified=f.lastModified();
        if(modified>0L){
            String when;
            long age=System.currentTimeMillis()-modified;
            if(age<60_000L)when="刚刚";
            else if(age<3_600_000L)when=(age/60_000L)+" 分钟前";
            else if(age<86_400_000L)when=(age/3_600_000L)+" 小时前";
            else if(age<7L*86_400_000L)when=(age/86_400_000L)+" 天前";
            else when=new java.text.SimpleDateFormat("MM-dd",Locale.getDefault()).format(new java.util.Date(modified));
            out.append(" · ").append(when);
        }
        return out.toString();
    }

    /** 1.2 KB / 3.4 MB 这类紧凑大小。 */
    private static String formatFileSize(long bytes){
        if(bytes<1024L)return bytes+" B";
        double kb=bytes/1024.0;
        if(kb<1024.0)return String.format(Locale.US,kb<10?"%.1f KB":"%.0f KB",kb);
        double mb=kb/1024.0;
        if(mb<1024.0)return String.format(Locale.US,mb<10?"%.1f MB":"%.0f MB",mb);
        return String.format(Locale.US,"%.1f GB",mb/1024.0);
    }

    /** 按扩展名选矢量图标与配色：代码绿 / 配置橙 / 文档紫灰 / 其余中灰。 */
    private int fileTintFor(File f){
        if(f.isDirectory())return ACCENT;
        String n=f.getName().toLowerCase(Locale.US);
        if(n.endsWith(".java")||n.endsWith(".kt")||n.endsWith(".js")||n.endsWith(".ts")||n.endsWith(".py")||n.endsWith(".c")||n.endsWith(".cpp")||n.endsWith(".h")||n.endsWith(".sh"))return GREEN;
        if(n.endsWith(".json")||n.endsWith(".xml")||n.endsWith(".yml")||n.endsWith(".yaml")||n.endsWith(".gradle")||n.endsWith(".toml")||n.endsWith(".properties"))return ACCENT;
        if(n.endsWith(".md")||n.endsWith(".txt"))return MUTED;
        return MUTED_2;
    }

    private int fileIconRes(File f){
        if(f.isDirectory())return R.drawable.ic_files;
        String n=f.getName().toLowerCase(Locale.US);
        if(n.endsWith(".java")||n.endsWith(".kt")||n.endsWith(".js")||n.endsWith(".ts")||n.endsWith(".py")||n.endsWith(".c")||n.endsWith(".cpp")||n.endsWith(".h")||n.endsWith(".sh"))return R.drawable.ic_code;
        if(n.endsWith(".json")||n.endsWith(".xml")||n.endsWith(".yml")||n.endsWith(".yaml"))return R.drawable.ic_box;
        return R.drawable.ic_file;
    }

    private String fileGlyph(File f) {
        String n=f.getName().toLowerCase(Locale.US); if(n.endsWith(".java")||n.endsWith(".kt")||n.endsWith(".js")||n.endsWith(".ts")||n.endsWith(".py")||n.endsWith(".c")||n.endsWith(".cpp"))return "·"; if(n.endsWith(".json")||n.endsWith(".xml")||n.endsWith(".yml")||n.endsWith(".yaml"))return "◇"; return "·";
    }

    private void showFileCreateMenu() {
        showChoicePicker("新建",new String[]{"新建文件","新建文件夹"},-1,which->showCreatePathDialog(which==1));
    }

    private void showCreatePathDialog(boolean directory) {
        final Dialog d=newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle(directory?"新建文件夹":"新建文件"),lp(-1,dp(48)));
        EditText name=input("",false); name.setHint(directory?"文件夹名称":"Main.java"); panel.addView(name,lp(-1,dp(52)));
        LinearLayout actions=hbox(); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(0,dp(8),0,0);
        TextView cancel=dialogButton("取消",MUTED,false); cancel.setOnClickListener(v->d.dismiss()); actions.addView(cancel,dialogActionParams());
        TextView create=dialogButton("创建",TEXT,true); create.setOnClickListener(v->{try{String n=name.getText().toString().trim();if(n.isEmpty()||n.contains("/")||n.equals(".")||n.equals(".."))throw new IllegalArgumentException("名称无效");File target=new File(browserDir,n);if(target.exists())throw new IllegalArgumentException("目标已存在");if(directory){if(!target.mkdir())throw new Exception("创建文件夹失败");d.dismiss();refreshFiles();}else{if(!target.createNewFile())throw new Exception("创建文件失败");d.dismiss();refreshFiles();openEditor(target);}}catch(Exception e){toast(e.getMessage());}}); actions.addView(create,dialogActionParams()); panel.addView(actions,lp(-1,dp(62)));
        d.setContentView(panel); d.show(); styleFormDialog(d,dialogWidthPx(390)); name.requestFocus();
    }

    private void showFileActions(File file) {
        if(file==null)return;
        String[] items=file.isDirectory()?new String[]{"打开文件夹","重命名","删除","复制路径"}
            :new String[]{"打开编辑器","重命名","删除","复制路径","附加到下一条提问","用终端打开所在目录"};
        showChoicePicker(file.getName(),items,-1,which->{
            if(!file.isDirectory()&&which==0){openEditor(file);return;}
            int offset=file.isDirectory()?0:1;
            if(file.isDirectory()&&which==0){browserDir=file;refreshFiles();return;}
            if(which==offset)showRenamePathDialog(file);
            else if(which==offset+1)confirmDeletePath(file);
            else if(which==offset+2)copyPath(file);
            else if(!file.isDirectory()&&which==offset+3)attachFileDirect(file);
            else if(!file.isDirectory()&&which==offset+4)openTerminalAt(file.getParentFile());
        });
    }

    /** 切到终端工作区并 cd 到指定目录（目录不存在时回退项目根）。 */
    private void openTerminalAt(File dir){
        File target=dir!=null&&dir.isDirectory()?dir:new File(config.projectDirectory);
        focusWorkspace(VIEW_TERMINAL);
        if(terminalPane!=null)terminalPane.runCommand("cd '"+target.getAbsolutePath().replace("'","'\\''")+"' && ls");
        else toast("终端尚未就绪，稍后手动 cd："+relativeProjectPath(target));
    }

    private void showRenamePathDialog(File file) {
        final Dialog d=newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("重命名"),lp(-1,dp(48))); EditText name=input(file.getName(),false);panel.addView(name,lp(-1,dp(52)));
        TextView save=dialogButton("保存重命名",TEXT,true);save.setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()||n.contains("/")){toast("名称无效");return;}File target=new File(file.getParentFile(),n);if(target.exists()){toast("目标已存在");return;}if(file.renameTo(target)){d.dismiss();refreshFiles();}else toast("重命名失败");});panel.addView(save,new LinearLayout.LayoutParams(-1,dp(48)));d.setContentView(panel);d.show();styleFormDialog(d,dialogWidthPx(390));
    }

    private void confirmDeletePath(File file) {
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("删除“" + file.getName() + "”？"),lp(-1,dp(48)));TextView hint=text(file.isDirectory()?"该文件夹及其中所有内容都会被删除。":"该文件将被删除。",11.5f,MUTED);panel.addView(hint,lp(-1,dp(44)));
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(8),0,0);TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->d.dismiss());actions.addView(cancel,dialogActionParams());TextView del=dialogButton("删除",RED,true);del.setOnClickListener(v->{if(deletePathRecursive(file)){d.dismiss();refreshFiles();}else toast("删除失败");});actions.addView(del,dialogActionParams());panel.addView(actions,lp(-1,dp(62)));d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(400),-2);
    }

    private boolean deletePathRecursive(File f){try{if(f.isDirectory()){File[] xs=f.listFiles();if(xs!=null)for(File x:xs)if(!deletePathRecursive(x))return false;}return !f.exists()||f.delete();}catch(Throwable e){return false;}}
    private void copyPath(File f){android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("path",f.getAbsolutePath()));toast("路径已复制");}
    private void attachFileDirect(File f){try{if(f.length()>2_000_000)throw new IllegalArgumentException("文件过大");pendingAttachments.add(new Attachment(relativeProjectPath(f),new String(readFile(f),StandardCharsets.UTF_8)));rebuildAttachmentStrip();toast("已附加 " + f.getName());}catch(Exception e){toast(e.getMessage());}}

    private void openEditor(File file) {
        if(file.length()>2_000_000){toast("文件过大，无法使用内置编辑器");return;}
        if (filesWorkspaceHost == null || (!wide && currentView != VIEW_FILES)) focusWorkspace(VIEW_FILES);
        io.execute(()->{try{String source=new String(readFile(file),StandardCharsets.UTF_8);ui(()->showFileEditor(file,source));}catch(Exception e){ui(()->toast("打开失败："+e.getMessage()));}});
    }

    private void showFileEditor(File file,String source) {
        if(filesWorkspaceHost==null)return;
        fileEditorFile=file; filesWorkspaceHost.removeAllViews(); LinearLayout page=vbox(); page.setBackgroundColor(SURFACE);
        LinearLayout bar=hbox();bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(6),dp(4),dp(8),dp(4));TextView back=smallIcon(R.drawable.ic_back);back.setOnClickListener(v->showFileBrowser());bar.addView(back,lp(dp(44),dp(44)));TextView name=text(relativeProjectPath(file),12,TEXT);name.setTypeface(Typeface.MONOSPACE);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.START);bar.addView(name,new LinearLayout.LayoutParams(0,dp(44),1));TextView more=smallIcon(R.drawable.ic_more);more.setOnClickListener(v->showFileActions(file));bar.addView(more,lp(dp(44),dp(44)));TextView save=dialogButton("保存",ACCENT,true);save.setOnClickListener(v->saveFileEditor());bar.addView(save,new LinearLayout.LayoutParams(dp(84),dp(44)));page.addView(bar,lp(-1,dp(52)));page.addView(dividerHorizontal(),lp(-1,1));
        fileEditor=new EditText(this); fileEditor.setText(MarkdownRenderer.highlight(source,languageForFile(file),neonTheme,lightTheme)); fileEditor.setTextColor(TEXT); fileEditor.setHintTextColor(MUTED_2); fileEditor.setBackgroundColor(Color.TRANSPARENT); fileEditor.setTypeface(Typeface.MONOSPACE); fileEditor.setTextSize(11); fileEditor.setGravity(Gravity.TOP|Gravity.LEFT); fileEditor.setPadding(dp(14),dp(12),dp(14),dp(18)); fileEditor.setHorizontallyScrolling(true); fileEditor.setHorizontalScrollBarEnabled(false); fileEditor.setVerticalScrollBarEnabled(true); fileEditor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI); fileEditor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fileEditorStatus=text(languageForFile(file)+" · UTF-8 · 已保存",11.5f,MUTED_2); fileEditorStatus.setGravity(Gravity.CENTER_VERTICAL); fileEditorStatus.setPadding(dp(12),0,dp(12),0);
        fileEditor.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){if(fileEditorHighlighting)return;if(fileEditorStatus!=null)setAnimatedText(fileEditorStatus,languageForFile(file)+" · 已修改",false);scheduleFileHighlight();}@Override public void afterTextChanged(Editable e){}});
        page.addView(fileEditor,new LinearLayout.LayoutParams(-1,0,1));page.addView(fileEditorStatus,lp(-1,dp(32)));filesWorkspaceHost.addView(page,new FrameLayout.LayoutParams(-1,-1));animatePane(page);
    }

    private void scheduleFileHighlight(){if(fileEditor==null)return;if(fileHighlightRunnable!=null)uiHandler.removeCallbacks(fileHighlightRunnable);fileHighlightRunnable=()->{if(fileEditor==null||fileEditorFile==null)return;int start=fileEditor.getSelectionStart(),end=fileEditor.getSelectionEnd();String raw=fileEditor.getText().toString();fileEditorHighlighting=true;fileEditor.setText(MarkdownRenderer.highlight(raw,languageForFile(fileEditorFile),neonTheme,lightTheme));int len=fileEditor.length();fileEditor.setSelection(Math.max(0,Math.min(start,len)),Math.max(0,Math.min(end,len)));fileEditorHighlighting=false;};uiHandler.postDelayed(fileHighlightRunnable,180);}
    private void saveFileEditor(){if(fileEditor==null||fileEditorFile==null)return;String data=fileEditor.getText().toString();io.execute(()->{try{try(FileOutputStream out=new FileOutputStream(fileEditorFile)){out.write(data.getBytes(StandardCharsets.UTF_8));}ui(()->{if(fileEditorStatus!=null)setAnimatedText(fileEditorStatus,languageForFile(fileEditorFile)+" · UTF-8 · 已保存",true);toast("已保存 " + fileEditorFile.getName());});}catch(Exception e){ui(()->toast("保存失败："+e.getMessage()));}});}
    private String languageForFile(File file){String n=file==null?"":file.getName().toLowerCase(Locale.US);if(n.endsWith(".java"))return"java";if(n.endsWith(".kt")||n.endsWith(".kts"))return"kotlin";if(n.endsWith(".js"))return"javascript";if(n.endsWith(".ts")||n.endsWith(".tsx"))return"typescript";if(n.endsWith(".json"))return"json";if(n.endsWith(".py"))return"python";if(n.endsWith(".sh")||n.endsWith(".bash"))return"bash";if(n.endsWith(".yml")||n.endsWith(".yaml"))return"yaml";if(n.endsWith(".diff")||n.endsWith(".patch"))return"diff";if(n.endsWith(".xml"))return"xml";return"text";}

    private String runtimeKey(File file) {
        if (file == null) return "";
        try { return file.getCanonicalPath(); } catch (Exception e) { return file.getAbsolutePath(); }
    }

    private void registerRuntime(SessionRuntime rt) {
        if (rt == null || rt.file == null) return;
        sessionRuntimes.put(runtimeKey(rt.file), rt);
    }

    private SessionRuntime createFreshRuntime() {
        SessionRuntime rt = new SessionRuntime();
        rt.nextConfig.workflowId = java.util.UUID.randomUUID().toString();
        rt.engine.configure(rt.nextConfig.copy());
        rt.file = null;
        return rt;
    }

    private SessionRuntime createRuntimeForExisting(File file) throws Exception {
        String key = runtimeKey(file);
        SessionRuntime existing = sessionRuntimes.get(key);
        if (existing != null) return existing;
        SessionRuntime created=buildRuntimeForExisting(file);
        registerRuntime(created);
        return created;
    }

    private SessionRuntime buildRuntimeForExisting(File file) throws Exception {
        SessionStore.SessionSummary summary = SessionStore.summarize(file);
        SessionConfig rc = config.copy();
        SessionStore.ProfileBinding binding=SessionStore.loadProfileBinding(file);
        if(binding!=null){
            SessionConfig bound=settingsStore.resolveProfile(binding.profileId,rc);
            if(bound!=null){rc=bound;rc.model=binding.model.isEmpty()?rc.model:binding.model;}
            else{rc.profileId=binding.profileId;rc.apiKey="";rc.model=binding.model;}
        }
        if (summary.project != null && !summary.project.trim().isEmpty() && new File(summary.project).isDirectory()) rc.projectDirectory = summary.project;
        rc.renewTransportSession();
        SessionRuntime rt = new SessionRuntime();
        try {
            rt.nextConfig=rc.copy();rt.boundProfileId=rc.profileId;
            rt.engine.configure(rc);
            rt.engine.resumeConversation(file);
            rt.file = file;
            return rt;
        } catch (Exception error) {
            rt.engine.shutdown();
            throw error;
        }
    }

    private void activateRuntime(SessionRuntime rt, boolean restoreUi) {
        activateRuntime(rt, restoreUi, null);
    }

    /** 后台线程已解析好会话行的打开路径：主线程直接用解析结果恢复，不再整文件解析（长会话 ANR 修复）。 */
    private void activateRuntime(SessionRuntime rt, JSONArray preloadedRows) {
        activateRuntime(rt, true, preloadedRows);
    }

    private void activateRuntime(SessionRuntime rt, boolean restoreUi, JSONArray preloadedRows) {
        if (rt == null) return;
        activeRuntimeGeneration++;
        activeRuntime = rt;
        synchronized(streamingDeltaLock){pendingStreamingDelta.setLength(0);pendingStreamingRuntime=null;pendingStreamingGeneration=activeRuntimeGeneration;streamingDeltaFlushPosted=false;}
        uiHandler.removeCallbacks(streamingDeltaFlushRunnable);
        uiHandler.removeCallbacks(streamingFrameRunnable);
        liveToolItems.clear();
        clearCollapsedToolActivities();
        engine = rt.engine;
        File liveFile = rt.engine.getSessionFile();
        if (liveFile != null) rt.file = liveFile;
        registerRuntime(rt);
        if(rt.file!=null)settingsStore.setLastSessionFile(rt.file);
        try {
            config=rt.nextConfig==null?config:rt.nextConfig.copy();
            // The bound config usually already carries the project directory; only re-read the
            // (potentially very large) session file when it does not.
            if (config.projectDirectory == null || !new File(config.projectDirectory).isDirectory()) {
                if (rt.file != null && rt.file.isFile()) {
                    SessionStore.SessionSummary summary = SessionStore.summarize(rt.file);
                    if (summary.project != null && !summary.project.trim().isEmpty() && new File(summary.project).isDirectory()) config.projectDirectory = summary.project;
                }
            }
            if (!rt.engine.isBusy()) rt.engine.configure(config.copy());
            rt.planState=rt.engine.getPlanWorkflowState();rt.taskSnapshot=rt.engine.getTaskSnapshot();
        } catch (Exception ignored) { }
        if (restoreUi && rt.file != null) {
            try {
                if (preloadedRows != null) restoreTranscriptRows(preloadedRows);
                else restoreTranscript(rt.file);
            } catch (Exception e) { transcript.clear(); }
        }
        streamingItem = null; streamingView = null; streamingBodyHost = null; streamingVisibleChars = 0;
        if ((rt.busy || rt.engine.isBusy()) && rt.liveAssistant.length() > 0) {
            streamingItem = new ChatItem(ChatItem.ASSISTANT, "IQ", rt.liveAssistant.toString());
            streamingVisibleChars = Math.min(streamingItem.body.length(), MESSAGE_TEXT_MAX_CHARS);
            transcript.add(streamingItem);
        }
        if (rt.busy || rt.engine.isBusy()) showWorkingIndicator(runtimeDisplayStatus(rt));
        else hideWorkingIndicator();
        syncComposerForActiveRuntime();
        refreshChrome();
        refreshVisibleSessionRows();
    }

    private String runtimeDisplayStatus(SessionRuntime rt) {
        if (rt == null) return "";
        String d = rt.detail == null ? "" : rt.detail.trim();
        if (!d.isEmpty()) {
            String lower = d.toLowerCase(Locale.US);
            if (lower.contains("thinking")) return "正在思考…";
            if (lower.contains("continuing")) return "正在继续处理…";
            if (lower.contains("compact")) return "正在压缩上下文…";
            if (lower.contains("cancel")) return "正在停止…";
            return d;
        }
        if ("tool".equals(rt.phase)) return "正在执行工具…";
        if ("thinking".equals(rt.phase)) return "正在思考…";
        return rt.busy ? "正在处理…" : "";
    }

    private String sessionLabel(SessionStore.SessionSummary summary, boolean active, SessionRuntime rt) {
        return historyDisplayLabel(summary,34);
    }

    private String historyDisplayLabel(SessionStore.SessionSummary summary,int maxChars) {
        if(summary==null)return "";
        String note=summary.note==null?"":summary.note.replace('\n',' ').replace('\r',' ').trim();
        return shorten(note.isEmpty()?summary.title:note,maxChars);
    }

    private void refreshVisibleSessionRows() {
        for (Map.Entry<String,TextView> e : sessionRowLabels.entrySet()) {
            SessionStore.SessionSummary summary = sessionRowSummaries.get(e.getKey());
            if (summary == null) continue;
            SessionRuntime rt = sessionRuntimes.get(e.getKey());
            boolean active = activeRuntime != null && runtimeKey(activeRuntime.file).equals(e.getKey());
            TextView v = e.getValue();
            if (v != null) {
                setAnimatedText(v,sessionLabel(summary, active, rt),rt != null && rt.busy);
                v.setTextColor(rt != null && rt.busy ? ACCENT : TEXT);
                v.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                if (v.getParent() instanceof View) {
                    View row = (View)v.getParent();
                    if (row.isSelected() != active) {
                        row.setSelected(active);
                        row.setBackground(active ? round(mixColor(SURFACE_2,ACCENT,.08f),10,BORDER_SOFT,1) : null);
                    }
                }
            }
        }
    }

    /** Session JSONL parsing is disk I/O; refresh metadata off the main thread only at coarse boundaries. */
    private void refreshVisibleSessionRowsFromDiskAsync() {
        final List<String> keys = new ArrayList<>(sessionRowLabels.keySet());
        if (keys.isEmpty()) return;
        io.execute(() -> {
            final Map<String,SessionStore.SessionSummary> fresh = new java.util.HashMap<>();
            for (String key : keys) {
                try {
                    File f = new File(key);
                    if (f.isFile()) fresh.put(key, SessionStore.summarize(f));
                } catch (Exception ignored) { }
            }
            ui(() -> { sessionRowSummaries.putAll(fresh); refreshVisibleSessionRows(); });
        });
    }

    /**
     * Render-only clamp of the transcript window. {@link #transcriptRenderLimit} holds the
     * user-configured window (设置里的“聊天显示最近消息”); low-RAM devices (which report a smaller
     * per-app heap) rebuild a shorter window.
     */
    private int effectiveTranscriptLimit() {
        Boolean cached = lowRamDeviceCache;
        if (cached == null) {
            boolean low = false;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                low = am != null && am.isLowRamDevice();
            } catch (Exception ignored) { }
            cached = low; lowRamDeviceCache = cached;
        }
        return cached ? Math.min(transcriptRenderLimit, 60) : transcriptRenderLimit;
    }

    private void addTranscriptWindow() {
        if (transcript.isEmpty()) { addEmptyState(chatMessages); return; }
        int start = includeCollapsedActivityStart(Math.max(0, transcript.size() - Math.max(1, effectiveTranscriptLimit())));
        if (start > 0) {
            final int hidden = start;
            TextView older = text("↑ 加载更早消息 · " + hidden + " 条", 10.5f, MUTED);
            older.setGravity(Gravity.CENTER);
            older.setPadding(dp(8),dp(6),dp(8),dp(8));
            older.setOnClickListener(v -> loadEarlierTranscript());
            chatMessages.addView(older, lp(-1,dp(42)));
        }
        for (int i = start; i < transcript.size(); i++) addChatView(transcript.get(i));
    }

    /**
     * 超长会话的视图护栏：窗口只限制“一次铺多少条”，但会话连续追加时视图会一直堆积，
     * 上千个 View 的测量/布局会拖垮一帧甚至 OOM。超过窗口的 1.5 倍加 20 条后重建一次视图树。
     */
    private void maybeTrimTranscriptViews(){
        if(chatMessages==null||rebuildingTranscript)return;
        if(chatMessages.getChildCount()<=transcriptViewCap())return;
        chatMessages.post(this::trimTranscriptViewsNow);
    }

    private int transcriptViewCap(){
        int limit=Math.max(1,effectiveTranscriptLimit());
        return limit+limit/2+20;
    }

    private void trimTranscriptViewsNow(){
        if(chatMessages==null||rebuildingTranscript)return;
        // 流式输出或任务运行中重建会把正在渲染的消息连视图一起摘掉，等下一次追加再收。
        if(streamingItem!=null||streamingView!=null||(activeRuntime!=null&&activeRuntime.busy))return;
        // 用户正在往上翻历史时不动视图树，避免把滚动位置顶走。
        if(!chatAutoFollow)return;
        if(chatMessages.getChildCount()<=transcriptViewCap())return;
        rebuildTranscriptViews();
    }

    private int includeCollapsedActivityStart(int start){
        int adjusted=start;
        while(adjusted>0&&adjusted<transcript.size()){
            CollapsedToolActivity activity=collapsedToolActivity(transcript.get(adjusted));
            if(activity==null||activity.members.isEmpty())break;
            int first=transcript.indexOf(activity.members.get(0));
            if(first<0||first>=adjusted)break;
            adjusted=first;
        }
        return adjusted;
    }

    private void loadEarlierTranscript() {
        if (chatMessages == null || chatScroll == null) return;
        final int beforeHeight = chatMessages.getHeight();
        final int beforeY = chatScroll.getScrollY();
        chatAutoFollow = false;
        transcriptRenderLimit = Math.min(transcript.size(), transcriptRenderLimit + Math.max(10, effectiveTranscriptLimit()));
        rebuildTranscriptViews();
        final ScrollView targetScroll=chatScroll;final LinearLayout targetMessages=chatMessages;final long treeGeneration=chatTreeGeneration;
        targetMessages.post(() -> {
            if (treeGeneration!=chatTreeGeneration||targetScroll!=chatScroll||targetMessages!=chatMessages) return;
            int addedHeight = Math.max(0, targetMessages.getHeight() - beforeHeight);
            targetScroll.scrollTo(0, Math.max(0, beforeY + addedHeight));
        });
    }

    private void rebuildTranscriptViews() {
        if (chatMessages == null) return;
        clearCollapsedToolActivityViews();
        chatMessages.removeAllViews();
        currentConversationHost=null;
        workingIndicator = null;
        rebuildingTranscript = true;
        addTranscriptWindow();
        attachChatImeSpacer();
        rebuildingTranscript = false;
        if (activeRuntime != null && activeRuntime.busy) showWorkingIndicator(runtimeDisplayStatus(activeRuntime));
        UiMotion.bindInteractive(chatMessages);
        if (chatAutoFollow) scrollChatSoft();
    }

    private View paneHeader(String title, String action, View.OnClickListener click) {
        LinearLayout bar=hbox(); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(12),dp(4),dp(8),dp(4)); bar.setBackgroundColor(SURFACE);
        TextView t=text(title,12,TEXT);t.setTypeface(Typeface.DEFAULT_BOLD);bar.addView(t,new LinearLayout.LayoutParams(0,dp(36),1)); TextView b=smallPill(action,MUTED);b.setGravity(Gravity.CENTER);b.setOnClickListener(click);bar.addView(b,lp(dp(64),dp(32)));return bar;
    }

    private void newSession() {
        sessionLoadToken++;   // 作废仍在后台加载的旧会话，避免打开完成后反把新会话顶掉
        clearCollapsedToolActivities();
        transcript.clear(); transcriptRenderLimit=config.transcriptWindowMessages; streamingItem=null; streamingView=null; streamingBodyHost=null; streamingVisibleChars=0;
        pendingAttachments.clear();
        config.renewTransportSession();
        SessionRuntime rt = createFreshRuntime();
        activateRuntime(rt, false);
        focusChat();
        if (mobileSidebarDialog != null) { mobileSidebarDialog.dismiss(); mobileSidebarDialog = null; }
    }

    private void showMobileSidebar() {
        if (mobileSidebarDialog != null) mobileSidebarDialog.dismiss();
        final Dialog dialog=newOverlayAwareDialog(); mobileSidebarDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=vbox(); shell.setPadding(0,dp(8),0,dp(8)); shell.setBackground(round(SIDEBAR,20,Color.TRANSPARENT,0));
        LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(14),0,dp(8),0);
        TextView title=text("元境",14,TEXT); title.setTypeface(Typeface.DEFAULT_BOLD); top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView close=iconButton(R.drawable.ic_close); close.setOnClickListener(v->dialog.dismiss()); top.addView(close,lp(dp(44),dp(44)));
        shell.addView(top,lp(-1,dp(48)));
        ScrollView sidebarScroll=new ScrollView(this);
        sidebarScroll.setVerticalScrollBarEnabled(false);
        sidebarScroll.setFillViewport(true);
        sidebarScroll.addView(buildSidebar(),new ScrollView.LayoutParams(-1,-2));
        shell.addView(sidebarScroll,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(shell); dialog.setCanceledOnTouchOutside(true); dialog.setOnDismissListener(d -> { if (mobileSidebarDialog == dialog) mobileSidebarDialog = null; }); dialog.show();
        Window w=dialog.getWindow(); if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);android.view.WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.54f;w.setAttributes(a);w.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);w.setLayout(Math.min(dp(330),(int)(getResources().getDisplayMetrics().widthPixels*.88f)),-1);}
        UiMotion.bindInteractive(shell);
        // 侧栏从左侧滑入：与抽屉方向一致，代替通用的上浮淡入。
        shell.setAlpha(0f);shell.setTranslationX(-dp(28));
        shell.animate().alpha(1f).translationX(0f).setInterpolator(UiMotion.emphasized())
            .setDuration(UiMotion.durationFor(this,240L)).withLayer().start();
    }

    private void resumeSession(SessionStore.SessionSummary summary) {
        if (summary == null) return;
        final File file = summary.file;
        final String project = summary.project;
        if (mobileSidebarDialog != null) { mobileSidebarDialog.dismiss(); mobileSidebarDialog = null; }
        final long token = ++sessionLoadToken;
        toast("正在打开会话…");
        // 引擎恢复 + 整文件解析都放后台：超长会话在旧实现里会把主线程卡到输入无响应（ANR）。
        io.submit(() -> {
            SessionRuntime rt = null; JSONArray rows = null; Exception error = null;
            try {
                rt = createRuntimeForExisting(file);
                rows = SessionStore.readRows(file);
            } catch (Exception e) { error = e; }
            final SessionRuntime builtRt = rt; final JSONArray builtRows = rows; final Exception failure = error;
            ui(() -> {
                if (token != sessionLoadToken || isFinishing() || isDestroyed()) return;
                if (failure != null) { toast("恢复会话失败：" + failure.getMessage()); return; }
                try {
                    activateRuntime(builtRt, builtRows);
                    if (project != null && !project.trim().isEmpty() && new File(project).isDirectory()) config.projectDirectory = project;
                    builtRt.nextConfig = config.copy();
                    if (!builtRt.engine.isBusy()) builtRt.engine.configure(config.copy());
                    settingsStore.save(config);
                    currentView = VIEW_CHAT; styleViewTabs(); renderChat(primaryHost, false);
                } catch (Exception e) { toast("恢复会话失败：" + e.getMessage()); }
            });
        });
    }

    private void showProjectPathDialog() {
        final Dialog d=newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("手动添加 / 切换项目路径"),lp(-1,dp(48)));
        TextView hint=text("每个规范化项目路径都有独立上下文历史。切换后只显示该路径绑定的会话；原项目正在运行的任务不会被强制取消。",11.5f,MUTED); hint.setLineSpacing(0,1.12f); panel.addView(hint,lp(-1,-2));
        EditText path=input(config.projectDirectory,false); path.setHint("/storage/emulated/0/项目 或 /data/user/0/com.iqge/files/home/projects/项目名");
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(52)); pp.setMargins(0,dp(12),0,0); panel.addView(path,pp);
        LinearLayout actions=hbox(); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(0,dp(10),0,0);
        TextView cancel=dialogButton("取消",MUTED,false); cancel.setOnClickListener(v->d.dismiss()); actions.addView(cancel,dialogActionParams());
        TextView apply=dialogButton("打开项目",TEXT,true);
        apply.setOnClickListener(v->{try{String selected=path.getText().toString().trim();switchProjectPath(selected,true);d.dismiss();}catch(Exception e){toast("切换失败："+e.getMessage());}}); actions.addView(apply,dialogActionParams());
        panel.addView(actions,lp(-1,dp(62))); d.setContentView(panel); d.show(); styleFormDialog(d,dialogWidthPx(480));
        path.requestFocus(); path.setSelection(path.length());
    }

    private boolean switchProjectPath(String requestedProject, boolean openHistory) throws Exception {
        String raw=requestedProject==null?"":requestedProject.trim(); if(raw.isEmpty())throw new IllegalArgumentException("项目目录不能为空");
        File projectDir=new File(raw); if(!projectDir.isDirectory()&&!projectDir.mkdirs())throw new IllegalArgumentException("无法创建项目目录");
        String canonical=projectDir.getCanonicalPath(); String before=SessionStore.canonicalProject(config.projectDirectory);
        boolean changed=!before.equals(canonical); config.projectDirectory=canonical; settingsStore.save(config);
        if(changed) settingsStore.setLastSessionFile(null); else if(engine!=null&&!engine.isBusy()) engine.configure(config);
        browserDir=new File(canonical); if(terminalPane!=null)terminalPane.setNextSessionWorkingDirectory(canonical); refreshChrome(); updateComposerChips();
        if(changed&&openHistory){toast("项目已切换："+new File(canonical).getName());uiHandler.postDelayed(()->showProjectHistoryPicker(true),120);}
        else if(!changed)toast("已经在这个项目路径");
        return changed;
    }

    private void showResumePicker() { showProjectHistoryPicker(false); }

    private String historyPickerLabel(SessionStore.SessionSummary summary){
        return historyDisplayLabel(summary,46);
    }

    private void refreshHistoryPickerRow(TextView row,File file){
        io.submit(()->{try{SessionStore.SessionSummary fresh=SessionStore.summarize(file);ui(()->row.setText(historyPickerLabel(fresh)));}catch(Exception ignored){}});
    }

    /** 历史选择器的一行：点按恢复会话，长按可备注/删除。"展开全部"时复用同一构建。 */
    private TextView historyPickerRow(SessionStore.SessionSummary s,Dialog d,boolean[] resolved){
        TextView r=text(historyPickerLabel(s),14,TEXT);
        r.setPadding(dp(10),dp(6),dp(10),dp(6));r.setGravity(Gravity.CENTER_VERTICAL);r.setMaxLines(2);r.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.setOnClickListener(v->{resolved[0]=true;d.dismiss();resumeSession(s);});
        r.setOnLongClickListener(v->{showSessionHistoryActions(s,()->refreshHistoryPickerRow(r,s.file));return true;});
        return r;
    }

    private void showProjectHistoryPicker(boolean createOnCancel) {
        // The dialog opens immediately and the project's sessions are scanned on an io
        // task: parsing every session JSONL up front froze the UI on large projects.
        final Dialog d = newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();
        final boolean[] resolved={false};
        panel.addView(dialogTitle("选择项目上下文历史"),lp(-1,dp(48)));
        TextView path=text(shortenMiddle(SessionStore.canonicalProject(config.projectDirectory),62),11.5f,MUTED_2);path.setTypeface(Typeface.MONOSPACE);path.setPadding(0,0,0,dp(6));panel.addView(path,lp(-1,dp(34)));
        ScrollView sv = new ScrollView(this); LinearLayout rows = vbox();
        LinearLayout fresh=hbox();fresh.setGravity(Gravity.CENTER_VERTICAL);fresh.setPadding(dp(10),dp(6),dp(10),dp(6));fresh.setBackground(round(SURFACE_3,14,BORDER,1));
        fresh.addView(iconOnly(R.drawable.ic_plus,18,ACCENT),lp(dp(28),dp(48)));
        LinearLayout freshCopy=vbox();TextView freshTitle=text("新建空白上下文",12.5f,TEXT);freshCopy.addView(freshTitle,lp(-1,dp(26)));TextView freshSub=text("历史会保存到当前项目路径",11.5f,MUTED_2);freshCopy.addView(freshSub,lp(-1,dp(24)));fresh.addView(freshCopy,new LinearLayout.LayoutParams(0,dp(52),1));
        fresh.setOnClickListener(v->{resolved[0]=true;d.dismiss();newSession();});rows.addView(fresh,lp(-1,dp(72)));
        final TextView loading=text("正在读取会话…",12,MUTED_2);loading.setGravity(Gravity.CENTER_VERTICAL);loading.setPadding(dp(10),0,0,0);rows.addView(loading,lp(-1,dp(52)));
        sv.addView(rows,new ScrollView.LayoutParams(-1,-2)); panel.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        d.setOnCancelListener(x->{if(createOnCancel&&!resolved[0]){resolved[0]=true;newSession();}});
        d.setContentView(panel); d.show(); styleDialogWindow(d,dialogWidthPx(470),Math.min(dp(640),(int)(getResources().getDisplayMetrics().heightPixels*.82f)));
        io.execute(() -> {
            final List<SessionStore.SessionSummary> sessions = SessionStore.listSessions(config.projectDirectory);
            ui(() -> {
                if (!d.isShowing()) return;
                rows.removeView(loading);
                if (sessions.isEmpty()) { d.dismiss(); if(createOnCancel){newSession();toast("当前项目没有历史，已创建新上下文");}else toast("当前项目暂无已保存会话"); return; }
                final int cap = 30;
                int max = Math.min(cap, sessions.size());
                for (int i=0;i<max;i++) rows.addView(historyPickerRow(sessions.get(i),d,resolved),lp(-1,dp(76)));
                if (sessions.size() > cap) {
                    TextView more=text("展开其余 " + (sessions.size() - cap) + " 个会话 ▼",12,ACCENT);
                    more.setPadding(dp(10),dp(6),dp(10),dp(6));more.setGravity(Gravity.CENTER_VERTICAL);
                    more.setContentDescription("展开全部会话");
                    more.setOnClickListener(v->{rows.removeView(more);for(int i=cap;i<sessions.size();i++)rows.addView(historyPickerRow(sessions.get(i),d,resolved),lp(-1,dp(76)));});
                    rows.addView(more,lp(-1,dp(52)));
                }
            });
        });
    }

    private static int countImageBlocks(JSONArray rows) {
        int count = 0;
        if (rows == null) return 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !"message".equals(row.optString("type", ""))) continue;
            JSONArray content = row.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject block = content.optJSONObject(j);
                if (block != null && "image".equals(block.optString("type", ""))) count++;
            }
        }
        return count;
    }

    private void restoreTranscript(File file) throws Exception {
        restoreTranscriptRows(SessionStore.readRows(file));
    }

    /** 只做 UI 状态的恢复（纯数据，不建视图）：解析结果可由后台线程预先读好，主线程不再碰大文件。 */
    private void restoreTranscriptRows(JSONArray rows) {
        clearCollapsedToolActivities();
        transcript.clear();
        liveToolItems.clear();
        transcriptRenderLimit = config.transcriptWindowMessages;
        java.util.HashMap<String,ChatItem> tools = new java.util.HashMap<>();
        ChatItem pendingUsageAssistant = null;
        int imageRows = countImageBlocks(rows);
        int imagesToSkip = Math.max(0, imageRows - MAX_RESTORED_IMAGES);
        for (int i=0;i<rows.length();i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            String rowType = row.optString("type", "");
            if ("context_usage".equals(rowType)) {
                JSONObject usage = row.optJSONObject("payload");
                ChatItem assistant = pendingUsageAssistant;
                if (usage != null && assistant != null) {
                    assistant.contextTokens = usage.optLong("context_tokens", -1L);
                    assistant.contextWindowTokens = usage.optInt("context_window_tokens", config.contextWindowTokens);
                    assistant.inputTokens = usage.optLong("input_tokens", -1L);
                    assistant.outputTokens = usage.optLong("output_tokens", -1L);
                    assistant.contextApiMeasured = "api".equals(usage.optString("source", "estimate"));
                }
                pendingUsageAssistant = null;
                continue;
            }
            if ("tool_diff".equals(rowType)) {
                JSONObject payload = row.optJSONObject("payload");
                if (payload != null) {
                    ChatItem tool = tools.get(payload.optString("tool_use_id", ""));
                    if (tool != null) {
                        tool.diff.setLength(0); tool.diff.append(payload.optString("diff", ""));
                        tool.diffAddedLines = payload.optInt("additions", 0);
                        tool.diffDeletedLines = payload.optInt("deletions", 0);
                    }
                }
                continue;
            }
            if (!"message".equals(rowType)) continue;
            String role = row.optString("role", ""); JSONArray content = row.optJSONArray("content"); if (content == null) continue;
            if ("assistant".equals(role)) {
                pendingUsageAssistant = null;
                StringBuilder text = new StringBuilder();
                ArrayList<ToolActivityGrouper.Entry> activityEntries=new ArrayList<>();
                ArrayList<ChatItem> assistantTools=new ArrayList<>();
                boolean boundaryBeforeTool=false;
                for (int j=0;j<content.length();j++) {
                    JSONObject b = content.optJSONObject(j); if (b == null) continue;
                    String type = b.optString("type", "");
                    if ("text".equals(type)) {
                        String blockText=b.optString("text","");
                        if(!blockText.isEmpty()){text.append(blockText);boundaryBeforeTool=true;}
                    } else if ("tool_use".equals(type)) {
                        if (text.length()>0 && !text.toString().trim().isEmpty()) { ChatItem assistant=new ChatItem(ChatItem.ASSISTANT,"IQ",text.toString());SessionStore.MessageReference ref=SessionStore.messageReference(row,i);if(ref!=null){assistant.messageId=ref.messageId;assistant.messageContentHash=ref.contentHash;assistant.legacyRowIndex=ref.legacyRowIndex;assistant.humanMessage=true;}transcript.add(assistant);pendingUsageAssistant=assistant;text.setLength(0); }
                        JSONObject input=b.optJSONObject("input"); String toolName=b.optString("name","Tool"); String toolId=b.optString("id","");
                        activityEntries.add(toolActivityEntry(toolId,toolName,input,boundaryBeforeTool));
                        boundaryBeforeTool=false;
                        if(isWorkflowTool(toolName))continue;
                        ChatItem tool=new ChatItem(ChatItem.TOOL,toolName,input==null?"{}":input.toString());
                        tool.toolId=toolId; transcript.add(tool); assistantTools.add(tool); if(!tool.toolId.isEmpty())tools.put(tool.toolId,tool);
                    } else if(!type.isEmpty()) boundaryBeforeTool=true;
                }
                if (text.length()>0 && !text.toString().trim().isEmpty() && !text.toString().startsWith("Understood. Continuing from the compacted context")) { ChatItem assistant=new ChatItem(ChatItem.ASSISTANT,"IQ",text.toString());SessionStore.MessageReference ref=SessionStore.messageReference(row,i);if(ref!=null){assistant.messageId=ref.messageId;assistant.messageContentHash=ref.contentHash;assistant.legacyRowIndex=ref.legacyRowIndex;assistant.humanMessage=true;}transcript.add(assistant);pendingUsageAssistant=assistant; }
                registerCollapsedToolBatch(nextRestoredToolBatchId--,activityEntries);
                for(ChatItem tool:assistantTools)attachToolToCollapsedActivity(tool);
            } else if ("user".equals(role)) {
                StringBuilder user = new StringBuilder();
                List<Attachment> restoredImages = new ArrayList<>();
                for (int j=0;j<content.length();j++) {
                    JSONObject b=content.optJSONObject(j); if (b==null)continue; String type=b.optString("type","");
                    if ("text".equals(type)) { String t=b.optString("text",""); if(!t.startsWith("<context_summary>")&&!t.startsWith("<iq_internal_continue>"))user.append(t); }
                    else if ("image".equals(type)) {
                        if(imagesToSkip>0){ imagesToSkip--; }
                        else try {
                            JSONObject source=b.optJSONObject("source");
                            if(source!=null && "base64".equals(source.optString("type"))){
                                String data=source.optString("data","");
                                if(!data.isEmpty()){
                                    byte[] bytes=Base64.decode(data,Base64.DEFAULT);
                                    if(bytes.length<=10*1024*1024) restoredImages.add(new Attachment(b.optString("name","图片"),source.optString("media_type","image/jpeg"),bytes));
                                }
                            }
                        } catch(Throwable ignored) { }
                    } else if ("tool_result".equals(type)) {
                        ChatItem tool=tools.get(b.optString("tool_use_id","")); if(tool!=null){tool.completed=true;tool.resultError=b.optBoolean("is_error",false);tool.result.append(String.valueOf(b.opt("content")));}
                    }
                }
                String visible=user.toString().replaceFirst("\\n\\n📷 \\d+ 张图片\\s*$","");
                if(!visible.isEmpty() || !restoredImages.isEmpty()) {
                    ChatItem item=new ChatItem(ChatItem.USER,"你",visible);item.persistedBody=user.toString();item.images.addAll(restoredImages);
                    SessionStore.MessageReference reference=SessionStore.messageReference(row,i);
                    if(reference!=null){item.messageId=reference.messageId;item.messageContentHash=reference.contentHash;item.legacyRowIndex=reference.legacyRowIndex;item.humanMessage=true;}
                    transcript.add(item);
                }
            }
        }
        for(ChatItem tool:tools.values())if(!tool.completed){
            tool.completed=true;tool.resultError=true;tool.restoredUnfinished=true;
            tool.result.append("会话记录未包含该工具的结果。");
        }
        for(List<CollapsedToolActivity> activities:collapsedToolsByBatch.values())for(CollapsedToolActivity activity:activities)activity.batchCompleted=true;
        streamingItem=null; streamingView=null; streamingBodyHost=null; streamingVisibleChars=0;
    }

    private String relativeSessionTime(long when) {
        long diff=Math.max(0,System.currentTimeMillis()-when);
        if(diff<60_000)return "刚刚"; if(diff<3_600_000)return (diff/60_000)+" 分钟前"; if(diff<86_400_000)return (diff/3_600_000)+" 小时前";
        if(diff<604_800_000)return (diff/86_400_000)+" 天前";
        return new SimpleDateFormat("M月d日",Locale.CHINA).format(new Date(when));
    }

    private void runtimeDialog() {
        if (runtime.isInstalled()) {
            final Dialog d = newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout panel=dialogPanel();
            panel.addView(dialogTitle("Termux 运行环境"),lp(-1,dp(48)));
            TextView info=text("原生 Android/Bionic 环境 · 无 proot\n\nPREFIX\n"+TermuxConstants.TERMUX_PREFIX_DIR_PATH+"\n\nHOME\n"+TermuxConstants.TERMUX_HOME_DIR_PATH,12.5f,MUTED); info.setLineSpacing(0,1.15f); int width=dialogWidthPx(440); panel.addView(scrollBody(info,width,dialogMaxBodyPx()));
        LinearLayout actions=hbox(); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(0,dp(10),0,0);
        TextView storage=dialogButton("手机权限",MUTED,false); storage.setOnClickListener(v->{d.dismiss(); requestAndroidIntegrationSetup(true);}); actions.addView(storage,dialogActionParams());
        TextView reinstall=dialogButton("重新初始化",ACCENT,false); reinstall.setOnClickListener(v->{d.dismiss();installRuntime(false);}); actions.addView(reinstall,dialogActionParams());
        TextView close=dialogButton("关闭",TEXT,true); close.setOnClickListener(v->d.dismiss()); actions.addView(close,dialogActionParams());
        panel.addView(actions,lp(-1,dp(62))); d.setContentView(panel); d.show(); styleDialogWindow(d,width,-2);
        } else installRuntime(false);
    }

    private void runFirstLaunchSetup() {
        if (config.apiKey == null || config.apiKey.trim().isEmpty()) {
            apiSetupPending = true;
            showApiProfileManager();
            return;
        }
        continueFirstLaunchSetup();
    }

    private void continueFirstLaunchSetup() {
        apiSetupPending = false;
        if (!runtime.isInstalled()) installRuntime(true);
        else requestAndroidIntegrationSetup(false);
    }

    private void installRuntime() { installRuntime(false); }

    private void installRuntime(boolean automatic) {
        if (automaticRuntimeInstallRunning) return;
        automaticRuntimeInstallRunning = true;
        ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle(automatic ? "正在初始化内置 Termux 环境" : "重新初始化内置 Termux 环境");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); pd.setMax(100); pd.setCancelable(!automatic); pd.setMessage("正在准备…"); pd.show();
        if(pd.getWindow()!=null){UiMotion.bindInteractive(pd.getWindow().getDecorView());UiMotion.dialogIn(pd.getWindow().getDecorView());}
        io.execute(()->{try{
            runtime.install((msg,pct)->ui(()->{pd.setMessage(localizeRuntimeProgress(msg));pd.setProgress(pct);}));
            ui(()->{automaticRuntimeInstallRunning=false;pd.dismiss();refreshChrome();if(terminalPane!=null)terminalPane.onRuntimeReady();toast("内置 Termux 环境已就绪");requestAndroidIntegrationSetup(false);});
        }catch(Exception e){ui(()->{automaticRuntimeInstallRunning=false;pd.dismiss();showAnimatedAlert(new AlertDialog.Builder(this).setTitle("Termux 初始化失败").setMessage(e.toString()).setPositiveButton("知道了",null).create());});}});
    }

    private String localizeRuntimeProgress(String msg) {
        if (msg == null) return "正在处理…";
        return msg.replace("Reading bundled Termux bootstrap…","正在读取内置 Termux 基础环境…")
            .replace("Installing bundled Termux userspace…","正在安装内置 Termux 原生环境…")
            .replace("Extracted ","已解压 ")
            .replace(" files…"," 个文件…")
            .replace("Configuring com.iqge package compatibility…","正在配置 com.iqge 软件包兼容层…")
            .replace("Termux ready","Termux 已就绪");
    }

    private void requestAndroidIntegrationSetup(boolean force) {
        android.content.SharedPreferences prefs=getSharedPreferences("iqge_onboarding",MODE_PRIVATE);
        if (!force && prefs.getBoolean("android_integration_prompted_v0192",false)) {
            requestStorageAccessWithExplanation(false);
            return;
        }
        if (androidIntegrationDialogVisible) return;
        androidIntegrationDialogVisible=true;
        prefs.edit().putBoolean("android_integration_prompted_v0192",true).apply();

        final Dialog d=newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("手机功能与权限"),lp(-1,dp(48)));
        TextView body=text("元境 现在会通过 com.iqge 自己的 Android 进程打开浏览器、其他 App 和系统设置，不再让内置 Termux 直接执行 am start，因此不会再出现调用包名与 UID 不匹配。\n\nAndroid 没有一个统一的“允许调用其他 App”危险权限。每次 IQ 要跳转到外部 App 时，会先显示 元境 自己的操作确认弹窗；只有你点“允许”才会启动。\n\n下面只申请实际存在且 元境 会用到的系统权限：共享存储，以及 Android 13+ 的通知权限。",12,MUTED);
        body.setLineSpacing(dp(2),1.12f); int width=dialogWidthPx(480); panel.addView(scrollBody(body,width,dialogMaxBodyPx()));
        LinearLayout actions=hbox(); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(0,dp(12),0,0);
        TextView later=dialogButton("暂不",MUTED,false); later.setOnClickListener(v->{androidIntegrationDialogVisible=false;d.dismiss();}); actions.addView(later,dialogActionParams());
        TextView allow=dialogButton("设置权限",ACCENT,true); allow.setOnClickListener(v->{androidIntegrationDialogVisible=false;d.dismiss();requestAndroidSystemPermissionsNow();}); actions.addView(allow,dialogActionParams());
        panel.addView(actions,lp(-1,dp(62))); d.setOnDismissListener(x->androidIntegrationDialogVisible=false); d.setContentView(panel); d.show(); styleDialogWindow(d,width,-2);
    }

    private void requestAndroidSystemPermissionsNow() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},4243);
            return;
        }
        requestStorageAccessWithExplanation(true);
    }

    private boolean hasSharedStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 30) return isExternalStorageManagerCompat();
        if (android.os.Build.VERSION.SDK_INT >= 23)
            return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private boolean isExternalStorageManagerCompat() {
        if (android.os.Build.VERSION.SDK_INT < 30) return true;
        try {
            java.lang.reflect.Method m = android.os.Environment.class.getMethod("isExternalStorageManager");
            Object v = m.invoke(null);
            return v instanceof Boolean && ((Boolean) v);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void requestStorageAccessWithExplanation(boolean force) {
        if (hasSharedStorageAccess()) { createStorageLinks(); return; }
        android.content.SharedPreferences prefs=getSharedPreferences("iqge_onboarding",MODE_PRIVATE);
        if (!force && prefs.getBoolean("storage_permission_prompted",false)) return;
        if (storagePermissionDialogVisible) return;
        storagePermissionDialogVisible=true;
        prefs.edit().putBoolean("storage_permission_prompted",true).apply();

        final Dialog d=newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("允许访问手机存储"),lp(-1,dp(48)));
        TextView body=text("元境 和内置 Termux 需要访问 /storage/emulated/0，才能直接读取、编辑和运行手机共享存储里的项目文件。\n\n授权后会自动创建 ~/storage/shared、downloads、dcim、pictures、music、movies 等快捷入口。\n\n不会使用 root，也不会使用 proot。",12,MUTED); body.setLineSpacing(dp(2),1.12f); int width=dialogWidthPx(480); panel.addView(scrollBody(body,width,dialogMaxBodyPx()));
        LinearLayout actions=hbox(); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(0,dp(12),0,0);
        TextView later=dialogButton("暂不",MUTED,false); later.setOnClickListener(v->{storagePermissionDialogVisible=false;d.dismiss();}); actions.addView(later,dialogActionParams());
        TextView allow=dialogButton("去授权",ACCENT,true); allow.setOnClickListener(v->{storagePermissionDialogVisible=false;d.dismiss();requestStoragePermissionNow();}); actions.addView(allow,dialogActionParams());
        panel.addView(actions,lp(-1,dp(62))); d.setOnDismissListener(x->storagePermissionDialogVisible=false); d.setContentView(panel); d.show(); styleDialogWindow(d,width,-2);
    }

    private void requestStoragePermissionNow() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            waitingForAllFilesAccess=true;
            try {
                android.content.Intent i=new android.content.Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                    android.net.Uri.parse("package:"+getPackageName()));
                startActivity(i);
            } catch (Throwable first) {
                try { startActivity(new android.content.Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")); }
                catch (Throwable e) { waitingForAllFilesAccess=false; toast("无法打开存储权限设置："+e.getMessage()); }
            }
            return;
        }
        if(android.os.Build.VERSION.SDK_INT>=23){
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE},4242);return;
        }
        createStorageLinks();
    }

    private void setupStorage(){ requestStorageAccessWithExplanation(true); }

    @Override protected void onResume() {
        super.onResume();
        startDeviceStatus();
        ToolExecutionResult pendingInstall = AndroidIntentBridge.resumePendingApkInstall(this);
        if (pendingInstall != null) toast(pendingInstall.content);
        if (waitingForOverlayPermission) {
            if (android.provider.Settings.canDrawOverlays(this)) {
                waitingForOverlayPermission=false;
                showWorkspaceOverlay(!isLandscapeNow());
            }
        }
        if (waitingForAllFilesAccess && android.os.Build.VERSION.SDK_INT >= 30) {
            if (isExternalStorageManagerCompat()) {
                waitingForAllFilesAccess=false; createStorageLinks(); toast("存储权限已开启");
            }
        }
        if (waitingForShizukuPermission) {
            refreshShizukuRow();
            if (com.iqge.shizuku.ShizukuBridge.state(this) == com.iqge.shizuku.ShizukuBridge.GRANTED) {
                waitingForShizukuPermission=false; toast("Shizuku 已授权（shell 身份）");
            }
        }
    }

    @Override protected void onPause(){
        stopDeviceStatus();
        super.onPause();
    }

    private void createStorageLinks(){
        try{
            File dir=new File(TermuxConstants.TERMUX_HOME_DIR_PATH,"storage"); if(!dir.exists())dir.mkdirs();
            String root=android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
            linkStorage(new File(dir,"shared"),root); linkStorage(new File(dir,"downloads"),root+"/Download"); linkStorage(new File(dir,"dcim"),root+"/DCIM"); linkStorage(new File(dir,"pictures"),root+"/Pictures"); linkStorage(new File(dir,"music"),root+"/Music"); linkStorage(new File(dir,"movies"),root+"/Movies");
            toast("已创建 ~/storage，共享存储可直接访问");
        }catch(Throwable e){toast("创建存储快捷入口失败："+e.getMessage());}
    }

    private void handleMcpSlash(String arg) {
        String a = arg == null ? "" : arg.trim();
        if (a.isEmpty()) { showMcpPanel(); return; }
        String[] parts = a.split("\\s+", 2);
        String op = parts[0].toLowerCase(Locale.US);
        String name = parts.length > 1 ? parts[1].trim() : "";
        if ("list".equals(op) || "列表".equals(op)) { showMcpSummary(); return; }
        if (("enable".equals(op) || "disable".equals(op) || "remove".equals(op) || "delete".equals(op)) && name.isEmpty()) {
            toast("用法：/mcp " + op + " <服务器名称>"); return;
        }
        if ("enable".equals(op) || "disable".equals(op)) {
            List<McpConfigStore.Server> servers = mcpStore.load(); boolean found=false;
            for (McpConfigStore.Server server : servers) if (server.name.equalsIgnoreCase(name)) { server.enabled="enable".equals(op); found=true; }
            if (!found) { toast("未找到 MCP 服务器：" + name); return; }
            try { mcpStore.save(servers); toast(("enable".equals(op)?"已启用：":"已停用：") + name); }
            catch (Exception e) { toast("保存 MCP 配置失败：" + e.getMessage()); }
            return;
        }
        if ("remove".equals(op) || "delete".equals(op)) {
            List<McpConfigStore.Server> servers = mcpStore.load(); boolean removed=false;
            for (int i=servers.size()-1;i>=0;i--) if (servers.get(i).name.equalsIgnoreCase(name)) { servers.remove(i); removed=true; }
            if (!removed) { toast("未找到 MCP 服务器：" + name); return; }
            try { mcpStore.save(servers); toast("已删除 MCP 服务器：" + name); }
            catch (Exception e) { toast("保存 MCP 配置失败：" + e.getMessage()); }
            return;
        }
        if ("add".equals(op) || "添加".equals(op)) { showMcpEditor(null); return; }
        toast("/mcp 支持：list、add、enable <名称>、disable <名称>、remove <名称>");
    }

    private void showMcpSummary() {
        List<McpConfigStore.Server> servers = mcpStore.load();
        StringBuilder b = new StringBuilder("MCP 服务器：\n\n");
        if (servers.isEmpty()) b.append("当前没有配置 MCP 服务器。输入 `/mcp` 打开配置面板。\n");
        for (McpConfigStore.Server s : servers) {
            String endpoint = "stdio".equals(s.type) ? s.command : s.url;
            b.append(s.enabled ? "● " : "○ ").append("**").append(s.name).append("** · `").append(s.type).append("`")
                .append(" · ").append("project".equals(s.scope)?"项目":"用户").append("\n  `").append(endpoint).append("`\n");
        }
        ChatItem item = new ChatItem(ChatItem.ASSISTANT, "MCP", b.toString()); transcript.add(item);
        if (chatMessages != null) { addChatView(item); scrollChatSoft(); }
    }

    private void showMcpPanel() {
        final Dialog d = newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout titleRow=hbox(); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(dialogTitle("MCP 服务器"),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView add=dialogButton("添加",ACCENT,false); applyLeftIcon(add,R.drawable.ic_plus,14,ACCENT); add.setOnClickListener(v->{d.dismiss();showMcpEditor(null);}); titleRow.addView(add,new LinearLayout.LayoutParams(dp(88),dp(44)));
        TextView close=iconButton(R.drawable.ic_close); close.setOnClickListener(v->d.dismiss()); titleRow.addView(close,lp(dp(44),dp(44))); panel.addView(titleRow,lp(-1,dp(48)));
        TextView sub=text("配置文件："+mcpStore.getFile().getAbsolutePath(),11.5f,MUTED_2); sub.setPadding(dp(4),0,dp(4),dp(10)); panel.addView(sub,lp(-1,-2));
        LinearLayout rows=vbox(); List<McpConfigStore.Server> servers=mcpStore.load();
        if(servers.isEmpty()){TextView empty=text("还没有 MCP 服务器。点击右上角“添加”开始配置。",12,MUTED);empty.setPadding(dp(8),dp(22),dp(8),dp(22));rows.addView(empty,lp(-1,-2));}
        for(McpConfigStore.Server server:servers){
            LinearLayout row=vbox(); row.setPadding(dp(12),dp(10),dp(10),dp(10)); row.setBackground(round(SURFACE_3,16,Color.TRANSPARENT,0));
            LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL); TextView dot=text(server.enabled?"●":"○",12,server.enabled?GREEN:MUTED_2);top.addView(dot,lp(dp(24),dp(40)));
            TextView name=text(server.name,12.5f,TEXT);name.setTypeface(Typeface.DEFAULT_BOLD);top.addView(name,new LinearLayout.LayoutParams(0,dp(40),1));
            TextView type=text(server.type.toUpperCase(Locale.US),11,MUTED);type.setGravity(Gravity.CENTER);top.addView(type,lp(dp(64),dp(32)));row.addView(top,lp(-1,dp(40)));
            String endpoint="stdio".equals(server.type)?server.command:server.url; TextView ep=text(endpoint,11.5f,MUTED);ep.setSingleLine(true);ep.setPadding(dp(24),0,dp(6),dp(8));row.addView(ep,lp(-1,dp(30)));
            LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);
            actionCell(actions,server.enabled?"停用":"启用",server.enabled?MUTED:GREEN,false).setOnClickListener(v->{List<McpConfigStore.Server> all=mcpStore.load();for(McpConfigStore.Server x:all)if(x.name.equalsIgnoreCase(server.name))x.enabled=!server.enabled;try{mcpStore.save(all);d.dismiss();showMcpPanel();}catch(Exception e){toast("保存失败："+e.getMessage());}});
            actionCell(actions,"编辑",MUTED,false).setOnClickListener(v->{d.dismiss();showMcpEditor(server);});
            actionCell(actions,"删除",RED,false).setOnClickListener(v->showAnimatedAlert(new AlertDialog.Builder(this).setTitle("删除 MCP 服务器？").setMessage(server.name).setNegativeButton("取消",null).setPositiveButton("删除",(x,w)->{List<McpConfigStore.Server> all=mcpStore.load();for(int i=all.size()-1;i>=0;i--)if(all.get(i).name.equalsIgnoreCase(server.name))all.remove(i);try{mcpStore.save(all);d.dismiss();showMcpPanel();}catch(Exception e){toast("删除失败："+e.getMessage());}}).create()));
            row.addView(actions,lp(-1,dp(44)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(8));rows.addView(row,rp);
        }
        panel.addView(scrollBody(rows,dialogWidthPx(520),dialogMaxBodyPx()));
        TextView hint=text("支持 stdio / HTTP / SSE。命令行也可用：/mcp list、/mcp enable 名称、/mcp disable 名称、/mcp remove 名称。",11.5f,MUTED_2);hint.setPadding(dp(4),dp(10),dp(4),0);panel.addView(hint,lp(-1,-2));
        d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(520),-2);
    }

    private void showMcpEditor(McpConfigStore.Server existing) {
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);final boolean editing=existing!=null;McpConfigStore.Server base=editing?existing.copy():new McpConfigStore.Server();
        LinearLayout panel=dialogPanel();
        LinearLayout titleRow=hbox();titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.addView(dialogTitle(editing?"编辑 MCP 服务器":"添加 MCP 服务器"),new LinearLayout.LayoutParams(0,dp(48),1));TextView close=iconButton(R.drawable.ic_close);close.setOnClickListener(v->d.dismiss());titleRow.addView(close,lp(dp(44),dp(44)));panel.addView(titleRow,lp(-1,dp(48)));
        LinearLayout form=vbox();
        EditText name=input(base.name,false);name.setHint("例如 filesystem");form.addView(labeled("服务器名称",name));
        String[] typeLabels={"标准输入输出（stdio）","Streamable HTTP","旧版 SSE"};String[] typeValues={"stdio","http","sse"};Spinner type=darkSpinner(typeLabels,typeValues,base.type);form.addView(labeled("连接方式",type));
        EditText command=input(base.command,false);command.setHint("例如 npx");form.addView(labeled("启动命令（stdio）",command));
        EditText args=input(joinLines(base.args),false);args.setHint("参数用换行分隔，例如 -y\\n@modelcontextprotocol/server-filesystem");args.setSingleLine(false);args.setMinLines(2);args.setMaxLines(4);args.setGravity(Gravity.TOP);form.addView(labeledTall("命令参数（每行一个）",args,dp(82)));
        EditText url=input(base.url,false);url.setHint("例如 http://127.0.0.1:8787/mcp");form.addView(labeled("服务器 URL（HTTP/SSE）",url));
        EditText env=input(base.env==null||base.env.length()==0?"":base.env.toString(),false);env.setHint("{\"TOKEN\":\"...\"}");form.addView(labeled("环境变量 JSON（stdio，可选）",env));
        EditText headers=input(base.headers==null||base.headers.length()==0?"":base.headers.toString(),false);headers.setHint("{\"Authorization\":\"Bearer ...\"}");form.addView(labeled("请求头 JSON（HTTP/SSE，可选）",headers));
        String[] scopeLabels={"用户级（所有项目）","项目级（当前项目）"};String[] scopeValues={"user","project"};Spinner scope=darkSpinner(scopeLabels,scopeValues,base.scope);form.addView(labeled("作用范围",scope));
        String[] enabledLabels={"启用","停用"};String[] enabledValues={"true","false"};Spinner enabled=darkSpinner(enabledLabels,enabledValues,base.enabled?"true":"false");form.addView(labeled("状态",enabled));
        LinearLayout footer=hbox();footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(6),0,0);TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->d.dismiss());footer.addView(cancel,dialogActionParams());TextView save=dialogButton("保存",TEXT,true);save.setOnClickListener(v->{try{McpConfigStore.Server out=new McpConfigStore.Server();out.name=name.getText().toString().trim();if(out.name.isEmpty())throw new Exception("服务器名称不能为空");int ti=type.getSelectedItemPosition();out.type=typeValues[Math.max(0,ti)];out.command=command.getText().toString().trim();out.url=url.getText().toString().trim();if("stdio".equals(out.type)&&out.command.isEmpty())throw new Exception("stdio 服务器必须填写启动命令");if(!"stdio".equals(out.type)&&out.url.isEmpty())throw new Exception("HTTP/SSE 服务器必须填写 URL");for(String line:args.getText().toString().split("\\r?\\n")){String x=line.trim();if(!x.isEmpty())out.args.add(x);}String ev=env.getText().toString().trim();out.env=ev.isEmpty()?new JSONObject():new JSONObject(ev);String hv=headers.getText().toString().trim();out.headers=hv.isEmpty()?new JSONObject():new JSONObject(hv);out.scope=scopeValues[Math.max(0,scope.getSelectedItemPosition())];out.enabled=enabled.getSelectedItemPosition()==0;List<McpConfigStore.Server> all=mcpStore.load();for(McpConfigStore.Server x:all)if(!editing&&x.name.equalsIgnoreCase(out.name))throw new Exception("已存在同名 MCP 服务器");if(editing){boolean replaced=false;for(int i=0;i<all.size();i++)if(all.get(i).name.equalsIgnoreCase(existing.name)){all.set(i,out);replaced=true;break;}if(!replaced)all.add(out);}else all.add(out);mcpStore.save(all);d.dismiss();toast("MCP 配置已保存");showMcpPanel();}catch(Exception e){toast("保存失败："+e.getMessage());}});footer.addView(save,dialogActionParams());
        int width=dialogWidthPx(520);
        panel.addView(scrollBody(form,width,dialogMaxBodyPx()));
        panel.addView(footer,lp(-1,dp(60)));
        d.setContentView(panel);d.show();styleFormDialog(d,width);
    }

    private String joinLines(List<String> values){StringBuilder b=new StringBuilder();for(String x:values){if(b.length()>0)b.append('\n');b.append(x);}return b.toString();}
    private View labeledTall(String label,View field,int height){LinearLayout box=vbox();TextView l=text(label,11.5f,MUTED);l.setPadding(dp(2),dp(4),0,0);box.addView(l,lp(-1,dp(26)));box.addView(field,lp(-1,height));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,0,0,dp(8));box.setLayoutParams(bp);return box;}

    private void linkStorage(File link,String target)throws Exception{try{link.delete();}catch(Throwable ignored){} android.system.Os.symlink(target,link.getAbsolutePath());}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==4242){boolean ok=grantResults.length>0;for(int x:grantResults)ok&=x==android.content.pm.PackageManager.PERMISSION_GRANTED;if(ok){createStorageLinks();toast("存储权限已开启");}else toast("未授予存储权限，可在设置中稍后开启");return;}
        if(requestCode==4243){boolean ok=grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;toast(ok?"通知权限已开启":"通知权限未开启，可稍后在系统设置中修改");requestStorageAccessWithExplanation(true);return;}
    }

    private void showPermissionPicker() {
        if(isPermissionModeLocked()){
            toast("本轮任务已锁定为 "+permissionLabelFor(activePermissionMode())+"；完成后可切换");
            return;
        }
        String[] labels={"每次询问","自动允许文件编辑","自动判断","规划模式（只读）","不主动询问","跳过权限检查"};
        String[] values={"default","acceptEdits","auto","plan","dontAsk","bypassPermissions"};
        int checked=Math.max(0,Arrays.asList(values).indexOf(config.permissionMode));
        showChoicePicker("权限模式",labels,checked,which->{config.permissionMode=values[which];saveConfigQuiet();updateComposerChips();});
    }

    private void handleRootSlash(String arg){
        String value=arg==null?"":arg.trim().toLowerCase(Locale.US);
        if("on".equals(value)||"enable".equals(value)){config.rootExecutionEnabled=true;saveConfigQuiet();toast("Agent Root 已开启；Root 调用将请求 su 授权");return;}
        if("off".equals(value)||"disable".equals(value)){config.rootExecutionEnabled=false;saveConfigQuiet();if(config.forcedKeepAliveEnabled)KeepAliveService.start(this,false);toast("Agent Root 已关闭");return;}
        if("check".equals(value)||"status".equals(value)){checkRootAccess();return;}
        showChoicePicker("Agent Root 权限",new String[]{"关闭 Root 工具","开启 Root 工具","检测 Magisk/KernelSU Root"},config.rootExecutionEnabled?1:0,which->{if(which==2){checkRootAccess();return;}config.rootExecutionEnabled=which==1;saveConfigQuiet();toast(config.rootExecutionEnabled?"Agent Root 已开启":"Agent Root 已关闭");});
    }

    private void handleKeepAliveSlash(String arg){
        String value=arg==null?"":arg.trim().toLowerCase(Locale.US);
        if("on".equals(value)||"enable".equals(value)){config.forcedKeepAliveEnabled=true;saveConfigQuiet();KeepAliveService.start(this,config.rootExecutionEnabled);toast("强制后台保活已开启；Root 可用时会自动强化系统后台策略");return;}
        if("off".equals(value)||"disable".equals(value)||"revoke".equals(value)){config.forcedKeepAliveEnabled=false;saveConfigQuiet();KeepAliveService.stop(this);toast("强制后台保活已关闭");return;}
        toast(config.forcedKeepAliveEnabled?"强制后台保活：已开启":"强制后台保活：未开启；使用 /keepalive on 开启");
    }

    private void checkRootAccess(){
        toast("正在请求 Root 管理器验证…");
        io.execute(()->{try{
            TermuxShellExecutor.Result result=new TermuxShellExecutor(this).executeAsRoot("uid=$(id -u); printf 'uid=%s\\n' \"$uid\"; id; [ \"$uid\" = 0 ]",config.projectDirectory,60_000);
            String detail=result.combined();boolean ok=result.exitCode==0&&!result.timedOut&&detail.contains("uid=0");
            ui(()->showAnimatedAlert(new AlertDialog.Builder(this).setTitle(ok?"Root 可用":"Root 不可用").setMessage(ok?"元境 已通过 su 获得 Android uid 0。\\n\\n"+detail:"没有获得 uid 0。请确认设备已安装 Magisk/KernelSU，并在 Root 管理器中允许 com.iqge。\\n\\n"+detail).setPositiveButton("确定",null).create()));
        }catch(Exception e){ui(()->showAnimatedAlert(new AlertDialog.Builder(this).setTitle("Root 不可用").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("确定",null).create()));}});
    }

    /** Shizuku 设置模块的刷新：授权状态行 + 已授权时的服务详情。 */
    private void refreshShizukuRow(){
        if(shizukuRow==null&&shizukuDetailRow==null)return;
        int state=com.iqge.shizuku.ShizukuBridge.state(this);
        if(shizukuRow!=null){
            String label=com.iqge.shizuku.ShizukuBridge.stateLabel(this);
            if(waitingForShizukuPermission&&state==com.iqge.shizuku.ShizukuBridge.DENIED)label="等待授权结果…";
            shizukuRow.setText(label+"  ›");
            shizukuRow.setTextColor(state==com.iqge.shizuku.ShizukuBridge.GRANTED?GREEN:TEXT);
        }
        if(shizukuDetailRow!=null){
            String detail=com.iqge.shizuku.ShizukuBridge.detail();
            shizukuDetailRow.setText(detail!=null?detail:"未连接（授权后显示服务版本、uid 与 SELinux 上下文）");
        }
    }

    private void onShizukuEntryClicked(){
        int state=com.iqge.shizuku.ShizukuBridge.state(this);
        if(state==com.iqge.shizuku.ShizukuBridge.GRANTED){toast("Shizuku 已授权（shell 身份）");refreshShizukuRow();return;}
        if(state==com.iqge.shizuku.ShizukuBridge.UNAVAILABLE){showShizukuInstallDialog();return;}
        if(state==com.iqge.shizuku.ShizukuBridge.NOT_RUNNING){
            waitingForShizukuPermission=true;
            toast(com.iqge.shizuku.ShizukuBridge.openManager(this)?"请在 Shizuku 里启动服务，然后回来申请授权":"请先安装并启动 Shizuku 服务");
            return;
        }
        String error=com.iqge.shizuku.ShizukuBridge.requestPermission(SHIZUKU_REQUEST_CODE);
        if(error!=null){toast(error);return;}
        waitingForShizukuPermission=true;refreshShizukuRow();
    }

    private void onShizukuManagerClicked(){
        if(com.iqge.shizuku.ShizukuBridge.managerInstalled(this)){
            if(!com.iqge.shizuku.ShizukuBridge.openManager(this))toast("打开 Shizuku 管理端失败");
            waitingForShizukuPermission=true;
            return;
        }
        showShizukuInstallDialog();
    }

    private void showShizukuInstallDialog(){
        showAnimatedAlert(new AlertDialog.Builder(this).setTitle("未安装 Shizuku")
            .setMessage("先安装 Shizuku（或 Sui），用 adb 或 Root 启动它的服务，然后回到这里申请授权。\n\n"+com.iqge.shizuku.ShizukuBridge.PROJECT_URL)
            .setNegativeButton("打开项目页",(x,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(com.iqge.shizuku.ShizukuBridge.PROJECT_URL)));}catch(Throwable e){toast("打开失败："+(e.getMessage()==null?e.toString():e.getMessage()));}})
            .setPositiveButton("知道了",null).create());
    }

    private void handleShizukuSlash(String arg){
        String value=arg==null?"":arg.trim().toLowerCase(Locale.US);
        if("on".equals(value)||"enable".equals(value)){config.shizukuExecutionEnabled=true;saveConfigQuiet();toast(com.iqge.shizuku.ShizukuBridge.state(this)==com.iqge.shizuku.ShizukuBridge.GRANTED?"Agent Shizuku 已开启":"Agent Shizuku 已开启；还需要在 设置 → Shizuku 里授权");return;}
        if("off".equals(value)||"disable".equals(value)){config.shizukuExecutionEnabled=false;saveConfigQuiet();toast("Agent Shizuku 已关闭");return;}
        if("status".equals(value)||"check".equals(value)){toast("Shizuku "+com.iqge.shizuku.ShizukuBridge.stateLabel(this)+(config.shizukuExecutionEnabled?"；Agent 可用":"；Agent 不可用"));return;}
        showChoicePicker("Agent Shizuku 权限",new String[]{"关闭 Shizuku 工具","开启 Shizuku 工具","查看授权状态"},config.shizukuExecutionEnabled?1:0,which->{if(which==2){onShizukuEntryClicked();return;}config.shizukuExecutionEnabled=which==1;saveConfigQuiet();toast(config.shizukuExecutionEnabled?"Agent Shizuku 已开启":"Agent Shizuku 已关闭");});
    }

    private void showEffortPicker() {
        String[] values=EFFORT_UI_VALUES;
        String[] labels=EFFORT_UI_LABELS;
        int checked=Math.max(0,Arrays.asList(values).indexOf(uiEffortValue(config.effort)));
        showChoicePicker("推理强度",labels,checked,which->applyReasoningEffort(values[which],true));
    }

    /** Composer chip: the strip replaces the dialog and pops straight out of the input box. */
    private void togglePermissionModeBar() {
        if(isPermissionModeLocked()){
            toast("本轮任务已锁定为 "+permissionLabelFor(activePermissionMode())+"；完成后可切换");
            return;
        }
        if(composerPickerKind==PICKER_PERMISSION){hideComposerPicker();return;}
        String[] labels={"每次询问","自动允许文件编辑","自动判断","规划模式（只读）","不主动询问","跳过权限检查"};
        String[] values={"default","acceptEdits","auto","plan","dontAsk","bypassPermissions"};
        int checked=Math.max(0,Arrays.asList(values).indexOf(config.permissionMode));
        if(!showComposerPicker(PICKER_PERMISSION,labels,checked,which->{config.permissionMode=values[which];saveConfigQuiet();updateComposerChips();}))showPermissionPicker();
    }

    private void toggleEffortBar() {
        if(composerPickerKind==PICKER_EFFORT){hideComposerPicker();return;}
        int checked=Math.max(0,Arrays.asList(EFFORT_UI_VALUES).indexOf(uiEffortValue(config.effort)));
        if(!showComposerPicker(PICKER_EFFORT,EFFORT_UI_LABELS,checked,which->applyReasoningEffort(EFFORT_UI_VALUES[which],true)))showEffortPicker();
    }

    /** Fills the strip and shows it over the chip row; false when the chat page is not built. */
    private boolean showComposerPicker(int kind,String[] labels,int checked,java.util.function.IntConsumer choose) {
        if(composerPickerRow==null||composerPickerScroll==null)return false;
        composerPickerKind=kind;
        composerPickerRow.removeAllViews();
        TextView selectedPill=null;
        for(int i=0;i<labels.length;i++){
            final int idx=i;
            TextView pill=text(labels[i],12,i==checked?ACCENT:MUTED);
            pill.setGravity(Gravity.CENTER);pill.setSingleLine(true);pill.setPadding(dp(12),0,dp(12),0);
            if(i==checked){pill.setTypeface(Typeface.DEFAULT_BOLD);pill.setBackground(round(SURFACE_3,14,BORDER,1));selectedPill=pill;}
            else pill.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));
            pill.setContentDescription(i==checked?"当前："+labels[i]:labels[i]);
            pill.setOnClickListener(v->{choose.accept(idx);hideComposerPicker();});
            LinearLayout.LayoutParams pillLp=new LinearLayout.LayoutParams(-2,dp(32));
            pillLp.setMargins(0,0,dp(6),0);
            composerPickerRow.addView(pill,pillLp);
            UiMotion.bindInteractive(pill);
        }
        composerPickerScroll.scrollTo(0,0);
        setComposerPickerOpen(true);
        final TextView reveal=selectedPill;
        if(reveal!=null)composerPickerScroll.post(()->{if(reveal.getParent()!=null)composerPickerScroll.smoothScrollTo(Math.max(0,reveal.getLeft()-dp(10)),0);});
        return true;
    }

    /** 选择条开合只动可见性和透明度：它和三个 chip 同格同高，输入卡高度和上方会话区都不动。 */
    private void setComposerPickerOpen(boolean open) {
        if(composerPickerScroll==null)return;
        if(composerModeChip!=null)composerModeChip.setVisibility(open?View.INVISIBLE:View.VISIBLE);
        if(composerEffortChip!=null)composerEffortChip.setVisibility(open?View.INVISIBLE:View.VISIBLE);
        if(composerModelChip!=null)composerModelChip.setVisibility(open?View.INVISIBLE:View.VISIBLE);
        if(!open){composerPickerScroll.setVisibility(View.GONE);return;}
        UiMotion.fadeSlideIn(composerPickerScroll,0f,0f,140L);
    }

    private void hideComposerPicker() {
        composerPickerKind=PICKER_NONE;
        setComposerPickerOpen(false);
    }

    private static String uiEffortValue(String value) {
        if (value == null) return "auto";
        String v=value.trim().toLowerCase(Locale.US);
        if ("minimal".equals(v)) return "low";
        if ("adaptive".equals(v)) return "auto";
        if ("xhigh".equals(v)||"xxhigh".equals(v)||"ultra".equals(v)) return "max";
        return Arrays.asList(EFFORT_UI_VALUES).contains(v) ? v : "auto";
    }

    private static String effortLabel(String value) {
        String selected=uiEffortValue(value);
        int index=Arrays.asList(EFFORT_UI_VALUES).indexOf(selected);
        return index<0 ? EFFORT_UI_LABELS[1] : EFFORT_UI_LABELS[index];
    }

    private void applyReasoningEffort(String effort,boolean notify){
        String selected=effort==null?"auto":effort.trim();if(selected.isEmpty())selected="auto";
        config.effort=selected;
        SessionRuntime rt=activeRuntime;
        boolean busy=rt!=null&&rt.engine.isBusy();
        if(rt!=null){
            if(rt.nextConfig==null)rt.nextConfig=config.copy();else rt.nextConfig.effort=selected;
            if(busy)rt.engine.updateReasoningEffort(selected);else rt.engine.configure(rt.nextConfig.copy());
        }
        try{settingsStore.save(config);refreshChrome();updateComposerChips();if(notify)toast(busy?"推理强度已更新，将从下一次模型请求生效":"推理强度已更新");}
        catch(Exception error){toast("保存失败："+error.getMessage());}
    }


    private void showOtherSettings(){
        final Dialog dialog=newOverlayAwareDialog();dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout header=hbox();header.setGravity(Gravity.CENTER_VERTICAL);header.addView(dialogTitle("其他设置"),new LinearLayout.LayoutParams(0,dp(48),1));TextView close=iconButton(R.drawable.ic_close);close.setOnClickListener(v->dialog.dismiss());header.addView(close,lp(dp(44),dp(44)));panel.addView(header,lp(-1,dp(48)));
        TextView hint=text("自定义头部提示词会作为系统指令随模型请求发送。它只从下一完整任务生效，不能覆盖应用安全规则、权限模式、工具白名单或 Root 限制。请勿填写 API 密钥。",11.5f,MUTED);hint.setLineSpacing(0,1.12f);panel.addView(hint,lp(-1,-2));
        EditText editor=new EditText(this);editor.setText(config.customSystemPrompt==null?"":config.customSystemPrompt);editor.setTextColor(TEXT);editor.setHintTextColor(MUTED_2);editor.setHint("例如：始终先给出简短计划，再执行修改…");editor.setGravity(Gravity.TOP|Gravity.START);editor.setTextSize(12);editor.setPadding(dp(10),dp(10),dp(10),dp(10));editor.setMinLines(8);editor.setMaxLines(16);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);editor.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,0,1);ep.setMargins(0,dp(12),0,dp(6));panel.addView(editor,ep);
        TextView count=text("提示词长度不设上限（受模型上下文窗口限制）",11.5f,MUTED_2);count.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);panel.addView(count,lp(-1,dp(26)));
        LinearLayout footer=hbox();footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(6),0,0);TextView clear=dialogButton("清空",MUTED,false);clear.setOnClickListener(v->{editor.setText("");editor.requestFocus();});footer.addView(clear,dialogActionParams());TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->dialog.dismiss());footer.addView(cancel,dialogActionParams());TextView save=dialogButton("保存",TEXT,true);save.setOnClickListener(v->{try{String value=editor.getText().toString();applyCustomSystemPromptToRuntimes(value);dialog.dismiss();toast("自定义头部提示词已保存，将从下一完整任务生效");}catch(Exception error){toast("保存失败："+error.getMessage());}});footer.addView(save,dialogActionParams());panel.addView(footer,lp(-1,dp(60)));
        dialog.setContentView(panel);dialog.show();styleFormDialog(dialog,dialogWidthPx(wide?620:500),Math.min(dp(wide?720:660),(int)(getResources().getDisplayMetrics().heightPixels*.88f)));
    }

    private void applyCustomSystemPromptToRuntimes(String value)throws Exception{
        SessionConfig saved=config.copy();saved.customSystemPrompt=value;settingsStore.save(saved);config=saved;
        for(SessionRuntime rt:new ArrayList<>(sessionRuntimes.values())){if(rt==null)continue;if(rt.nextConfig==null)rt.nextConfig=config.copy();else rt.nextConfig.customSystemPrompt=config.customSystemPrompt;if(!rt.engine.isBusy())rt.engine.configure(rt.nextConfig.copy());}
        SessionRuntime rt=activeRuntime;if(rt!=null&&!sessionRuntimes.containsValue(rt)){if(rt.nextConfig==null)rt.nextConfig=config.copy();else rt.nextConfig.customSystemPrompt=config.customSystemPrompt;if(!rt.engine.isBusy())rt.engine.configure(rt.nextConfig.copy());}
        refreshChrome();updateComposerChips();
    }

    private void showUiCanvasPanel(){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout p=dialogPanel();
        LinearLayout h=hbox();h.setGravity(Gravity.CENTER_VERTICAL);h.addView(dialogTitle("运行时 UI 画布"),new LinearLayout.LayoutParams(0,dp(48),1));TextView x=iconButton(R.drawable.ic_close);x.setOnClickListener(v->d.dismiss());h.addView(x,lp(dp(44),dp(44)));p.addView(h,lp(-1,dp(48)));
        TextView info=text("仅修改当前运行中的允许节点：颜色、可见性、间距、文字大小和安全顺序。变更由 Agent 的 ui_canvas 工具或源代码完成；新行为仍应编辑源码。",11.5f,MUTED);info.setLineSpacing(0,1.12f);p.addView(info,lp(-1,-2));
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(10),0,0);TextView reset=dialogButton("恢复默认",MUTED,false);reset.setOnClickListener(v->{UiCanvasStore.reset(this);rebuildWorkspaceForTheme();d.dismiss();});actions.addView(reset,dialogActionParams());TextView export=dialogButton("复制 JSON",TEXT,false);export.setOnClickListener(v->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("ui_canvas",UiCanvasStore.export(UiCanvasStore.load(this))));toast("UI 画布 JSON 已复制");});actions.addView(export,dialogActionParams());TextView close=dialogButton("完成",TEXT,true);close.setOnClickListener(v->d.dismiss());actions.addView(close,dialogActionParams());p.addView(actions,lp(-1,dp(60)));d.setContentView(p);d.show();styleDialogWindow(d,dialogWidthPx(480),-2);
    }

    private void showSettings() {
        final Dialog dialog=newOverlayAwareDialog();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();

        LinearLayout titleRow=hbox(); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(dialogTitle("元境 设置"),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView x=iconButton(R.drawable.ic_close); x.setOnClickListener(v->dialog.dismiss()); titleRow.addView(x,lp(dp(44),dp(44))); panel.addView(titleRow,lp(-1,dp(48)));

        LinearLayout form=vbox(); form.setPadding(0,dp(4),0,dp(8));
        View appearanceSection=settingsSection("外观");form.addView(appearanceSection);
        java.util.List<ThemePluginStore.Theme> themePlugins=ThemePluginStore.list(this);
        String[] themeValues=new String[2+themePlugins.size()];
        String[] themeLabels=new String[themeValues.length];
        themeValues[0]=THEME_NIGHT;themeLabels[0]="夜间模式";
        themeValues[1]=THEME_DAY;themeLabels[1]="白天模式";
        String currentSelection=uiTheme!=null&&uiTheme.startsWith("plugin:")?uiTheme:(lightTheme?THEME_DAY:THEME_NIGHT);
        for(int i=0;i<themePlugins.size();i++){themeValues[2+i]="plugin:"+themePlugins.get(i).id;themeLabels[2+i]="插件 · "+themePlugins.get(i).name;}
        int themeSelectedIndex=0;
        for(int i=0;i<themeValues.length;i++)if(themeValues[i].equals(currentSelection))themeSelectedIndex=i;
        Spinner themeMode=darkSpinner(themeLabels,themeValues,themeValues[themeSelectedIndex]);form.addView(labeledHelp("主题模式",themeMode,"主题就是整个 App 的皮肤：背景、文字、按钮的颜色风格。夜间模式=深色底，适合晚上用；白天模式=浅色底。插件主题是别人做好的配色文件，导入后就能选。"));
        TextView themeModeHint=text("右上角 ☼/☾ 可快速切换；切换时会平滑重建界面。主题插件是 .json 调色板，放在 home/.iq/themes/ 或点下方导入。",10.5f,MUTED_2);themeModeHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(themeModeHint,lp(-1,-2));
        TextView themeImport=text("导入主题插件（.json）  ›",12,TEXT);themeImport.setGravity(Gravity.CENTER_VERTICAL);themeImport.setPadding(dp(12),0,dp(12),0);
        themeImport.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));
        themeImport.setOnClickListener(v->{dialog.dismiss();launchThemeImport();});
        form.addView(labeled("导入主题插件",themeImport));
        EditText paletteBackground=paletteInput(BG);form.addView(labeled("背景色",paletteBackground));
        EditText paletteSurface=paletteInput(SURFACE);form.addView(labeled("面板色",paletteSurface));
        EditText paletteText=paletteInput(TEXT);form.addView(labeled("文字色",paletteText));
        EditText paletteAccent=paletteInput(ACCENT);form.addView(labeled("强调色",paletteAccent));
        EditText paletteGreen=paletteInput(GREEN);form.addView(labeled("成功色",paletteGreen));
        EditText paletteRed=paletteInput(RED);form.addView(labeled("错误色",paletteRed));
        TextView themeHint=text("输入 #RRGGBB 颜色；边框、次级文字和层级表面会由调色板自动生成。",10.5f,MUTED_2);themeHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(themeHint,lp(-1,-2));
        View modelSection=settingsSection("模型与权限");form.addView(modelSection);
        TextView apiProfiles=text(currentProfileName()+" · "+config.protocol+" · "+shorten(config.model,30)+"  ›",12,TEXT);apiProfiles.setGravity(Gravity.CENTER_VERTICAL);apiProfiles.setPadding(dp(12),0,dp(12),0);apiProfiles.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));apiProfiles.setOnClickListener(v->{dialog.dismiss();showApiProfileManager();});form.addView(labeled("API 配置记录",apiProfiles));
        TextView apiHint=text("API 地址、协议、默认模型和密钥按配置记录独立保存；密钥不会在设置页回填。点模型按钮可自动获取当前 API 的模型。",10.5f,MUTED_2);apiHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(apiHint,lp(-1,-2));
        String[] visionValues={"on","off"}; String[] visionLabels={"开启（发送图片给模型）","关闭（仅保留本地预览）"};
        Spinner vision=darkSpinner(visionLabels,visionValues,config.visionEnabled?"on":"off"); form.addView(labeled("视觉图片输入（Vision）",vision));
        TextView visionHint=text("模型不支持 vision 时请选择关闭；当前图片和历史图片都不会发送，但聊天预览与会话记录仍会保留。",10.5f,MUTED_2);visionHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(visionHint,lp(-1,-2));

        String[] effortValues=EFFORT_UI_VALUES;
        String[] effortLabels=EFFORT_UI_LABELS;
        Spinner effort=darkSpinner(effortLabels,effortValues,uiEffortValue(config.effort));form.addView(labeledHelp("推理强度",effort,"推理强度=让 AI 想多久。强度高：想得更仔细、答案更准，但更慢更费算力；强度低：回答快、省钱，但可能想得浅。日常选标准即可。"));

        String[] permValues={"default","acceptEdits","auto","plan","dontAsk","bypassPermissions"};
        String[] permLabels={"每次询问","自动允许文件编辑","自动判断","规划模式（只读）","不主动询问","跳过权限检查"};
        boolean permissionLocked=isPermissionModeLocked();
        Spinner perm=darkSpinner(permLabels,permValues,permissionLocked?activePermissionMode():config.permissionMode);
        if(permissionLocked){perm.setEnabled(false);perm.setAlpha(.55f);}
        form.addView(labeledHelp("权限模式",perm,"权限模式决定 AI 助手在改动你手机文件或执行命令前，要不要先问你。每次询问=最安全；自动允许文件编辑=它改代码不用每次打扰你；跳过权限检查=完全放手（风险高，不建议）。"));
        if(permissionLocked){TextView permissionLockHint=text("当前任务已锁定为 "+permissionLabelFor(activePermissionMode())+"；任务结束后可修改。",10.5f,ACCENT);permissionLockHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(permissionLockHint,lp(-1,-2));}
        View localSection=settingsSection("本地模型");form.addView(localSection);
        String[] localFallbackValues={"on","off"};String[] localFallbackLabels={"开启（断网/云端失败自动兜底）","关闭（仅用云端）"};
        Spinner localFallbackSwitch=darkSpinner(localFallbackLabels,localFallbackValues,config.localFallback?"on":"off");form.addView(labeledHelp("本地小模型兜底",localFallbackSwitch,"云端模型断网或服务器出错时就用不了了。打开这个开关，App 会切到手机上下载好的小模型继续干活——相当于给 App 装了个备用发电机。需要先到「本地模型库」下载模型。"));
        String[] localStrategyValues={"auto","local-first","cloud-first"};String[] localStrategyLabels={"auto：云端优先，断网/失败才走本地（推荐）","local-first：本地优先，离线也能用","cloud-first：只用云端"};
        Spinner localStrategy=darkSpinner(localStrategyLabels,localStrategyValues,config.localStrategy==null||config.localStrategy.isEmpty()?"auto":config.localStrategy);form.addView(labeled("路由策略",localStrategy));
        TextView localModelEntry=text("模型下载与管理  ›",12,TEXT);localModelEntry.setGravity(Gravity.CENTER_VERTICAL);localModelEntry.setPadding(dp(12),0,dp(12),0);localModelEntry.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));localModelEntry.setOnClickListener(v->{dialog.dismiss();showLocalModelPanel();});form.addView(labeled("本地模型库",localModelEntry));
        TextView localHint=text("本地兜底让 App 在没有网络或云端故障时仍能继续编码。需先下载模型（3B 约 2GB，仅 Wi-Fi）并安装 Termux llama.cpp（pkg install llama.cpp）；设备配置不足会自动禁用本地模型。",10.5f,MUTED_2);localHint.setLineSpacing(0,1.12f);localHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(localHint,lp(-1,-2));
        View agentSection=settingsSection("Agent 与安全");form.addView(agentSection);
        String[] sandboxAccessValues={"on","off"};String[] sandboxAccessLabels={"开启（Agent 对 IQSandbox Guest 全权限，不逐次询问）","关闭（跟随普通权限模式）"};
        Spinner sandboxAccess=darkSpinner(sandboxAccessLabels,sandboxAccessValues,config.sandboxAgentFullAccess?"on":"off");form.addView(labeled("Agent 沙箱全权调试",sandboxAccess));
        TextView sandboxAccessHint=text("只绕过 IQSandbox/Sandbox Debug 的逐次确认：允许容器内安装、UI 操作、动态内存、Frida Hook/脚本。不会自动放开真机 host、Root、zygote/SystemUI 或其他无关 App。",10.5f,ACCENT);sandboxAccessHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(sandboxAccessHint,lp(-1,-2));
        String[] rootValues={"off","on"};String[] rootLabels={"关闭（不向 Agent 暴露 Root 工具）","开启（允许 Agent 请求 uid 0）"};
        Spinner rootAccess=darkSpinner(rootLabels,rootValues,config.rootExecutionEnabled?"on":"off");form.addView(labeled("Agent Root 权限",rootAccess));
        TextView rootHint=text("高风险功能：需要 Magisk/KernelSU 和 su 授权。开启后主 Agent 与通用子 Agent可请求 Root；普通权限模式仍会逐次确认。点此检测 Root。",10.5f,RED);rootHint.setPadding(dp(2),0,dp(2),dp(8));rootHint.setOnClickListener(v->checkRootAccess());form.addView(rootHint,lp(-1,-2));
        String[] keepAliveValues={"off","on"};String[] keepAliveLabels={"关闭","开启（前台服务 + WakeLock；Root 时强制系统后台策略）"};
        Spinner keepAlive=darkSpinner(keepAliveLabels,keepAliveValues,config.forcedKeepAliveEnabled?"on":"off");form.addView(labeled("强制后台保活",keepAlive));

        View shizukuSection=settingsSection("Shizuku");form.addView(shizukuSection);
        shizukuRow=text("",12,TEXT);shizukuRow.setGravity(Gravity.CENTER_VERTICAL);shizukuRow.setPadding(dp(12),0,dp(12),0);
        shizukuRow.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));shizukuRow.setOnClickListener(v->onShizukuEntryClicked());
        form.addView(labeled("授权状态",shizukuRow));
        String[] shizukuUseValues={"off","on"};String[] shizukuUseLabels={"关闭（不向 Agent 暴露 Shizuku 工具）","开启（允许 Agent 以 shell 身份执行系统命令）"};
        Spinner shizukuAccess=darkSpinner(shizukuUseLabels,shizukuUseValues,config.shizukuExecutionEnabled?"on":"off");form.addView(labeled("Agent Shizuku 权限",shizukuAccess));
        shizukuDetailRow=text("",11.5f,MUTED);shizukuDetailRow.setGravity(Gravity.CENTER_VERTICAL);shizukuDetailRow.setPadding(dp(12),0,dp(12),0);
        shizukuDetailRow.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));shizukuDetailRow.setSingleLine(true);shizukuDetailRow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        form.addView(labeled("服务详情",shizukuDetailRow));
        TextView shizukuManagerRow=text("打开 Shizuku 管理端  ›",12,TEXT);shizukuManagerRow.setGravity(Gravity.CENTER_VERTICAL);shizukuManagerRow.setPadding(dp(12),0,dp(12),0);
        shizukuManagerRow.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));shizukuManagerRow.setOnClickListener(v->onShizukuManagerClicked());
        form.addView(labeled("管理端",shizukuManagerRow));
        TextView shizukuHint=text("Shizuku 由用户用 adb 或 Root 启动一个 shell 身份的进程，再把它的 Binder 交给本应用，因此不需要设备 Root 就能拿到 shell 权限。打开「Agent Shizuku 权限」后，Agent 会多出一个 Shizuku 工具，以 shell 身份执行 pm / am / settings / appops / cmd / dumpsys 这类系统命令；它和 Root 一样每次都要确认，规划模式与「不主动询问」下不可用。注意 shell 身份读不到本应用的私有目录，项目里的文件操作仍应走 Bash。",10.5f,MUTED_2);shizukuHint.setLineSpacing(0,1.12f);shizukuHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(shizukuHint,lp(-1,-2));
        refreshShizukuRow();

        View networkSection=settingsSection("联网");form.addView(networkSection);
        String[] webEnabledLabels={"开启联网搜索","关闭联网搜索"}; String[] webEnabledValues={"on","off"};
        Spinner webEnabled=darkSpinner(webEnabledLabels,webEnabledValues,config.webSearchEnabled?"on":"off"); form.addView(labeledHelp("联网搜索",webEnabled,"打开后，AI 需要最新信息时（比如「今天有什么新闻」）会自动上网搜索；关闭后它只用你已知的资料回答，更省流量也更私密。"));
        String[] webProviderValues={"auto","duckduckgo","bing"}; String[] webProviderLabels={"自动（DuckDuckGo → Bing）","DuckDuckGo","Bing RSS"};
        Spinner webProvider=darkSpinner(webProviderLabels,webProviderValues,config.webSearchProvider); form.addView(labeled("搜索后端",webProvider));
        EditText webResults=input(Integer.toString(config.webSearchMaxResults),false); webResults.setInputType(InputType.TYPE_CLASS_NUMBER); form.addView(labeled("默认搜索结果数（1-10）",webResults));
        EditText webTimeout=input(Integer.toString(Math.max(1,config.webTimeoutMs/1000)),false); webTimeout.setInputType(InputType.TYPE_CLASS_NUMBER); form.addView(labeled("联网超时（秒）",webTimeout));

        View contextSection=settingsSection("上下文与项目");form.addView(contextSection);
        EditText contextWindow=input(formatTokenCount(config.contextWindowTokens),false); contextWindow.setHint("128k / 200k / 1m / 1.5m"); contextWindow.setInputType(InputType.TYPE_CLASS_TEXT); form.addView(labeledHelp("上下文窗口",contextWindow,"上下文窗口=AI 一次能记住多少对话内容。窗口越大记得越多、回答越连贯，但更慢更费钱；太小它会忘掉前面聊过的内容。128k 大约能装下十几万字。"));
        String[] compactLabels={"开启自动压缩","关闭自动压缩"}; String[] compactValues={"on","off"};
        Spinner autoCompact=darkSpinner(compactLabels,compactValues,config.autoCompact?"on":"off"); form.addView(labeled("上下文压缩",autoCompact));
        EditText compactAt=input(Integer.toString((int)Math.round(config.autoCompactRatio*100)),false); compactAt.setInputType(InputType.TYPE_CLASS_NUMBER); form.addView(labeled("自动压缩上限（50–100%，安全缓冲优先）",compactAt));
        String[] windowLabels={"10 条","20 条（默认）","50 条","120 条"};String[] windowValues={"10","20","50","120"};
        Spinner historyWindow=darkSpinner(windowLabels,windowValues,Integer.toString(config.transcriptWindowMessages));form.addView(labeled("聊天显示最近消息（超长会话防卡）",historyWindow));
        EditText project=input(config.projectDirectory,false);project.setHint("/storage/emulated/0/项目 或 /data/user/0/com.iqge/files/home/projects/项目名");form.addView(labeled("项目目录",project));
        TextView projectHint=text("项目路径与上下文历史一一绑定；保存新路径后会自动显示该项目可恢复的历史记录。",10.5f,MUTED_2);projectHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(projectHint,lp(-1,-2));

        View extensionsSection=settingsSection("扩展功能");form.addView(extensionsSection);
        TextView mcpEntry=text("MCP 服务器配置  ›",12,TEXT);mcpEntry.setGravity(Gravity.CENTER_VERTICAL);mcpEntry.setPadding(dp(12),0,dp(12),0);mcpEntry.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));mcpEntry.setOnClickListener(v->{dialog.dismiss();showMcpPanel();});form.addView(labeled("Model Context Protocol（MCP）",mcpEntry));
        TextView canvasEntry=text("运行时 UI 画布  ›",12,TEXT);canvasEntry.setGravity(Gravity.CENTER_VERTICAL);canvasEntry.setPadding(dp(12),0,dp(12),0);canvasEntry.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));canvasEntry.setOnClickListener(v->{dialog.dismiss();showUiCanvasPanel();});form.addView(labeled("无需重编译的界面调整",canvasEntry));
        TextView otherEntry=text("自定义头部提示词  ›",12,TEXT);otherEntry.setGravity(Gravity.CENTER_VERTICAL);otherEntry.setPadding(dp(12),0,dp(12),0);otherEntry.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));otherEntry.setOnClickListener(v->{dialog.dismiss();showOtherSettings();});form.addView(labeled("其他设置",otherEntry));
        View aboutSection=settingsSection("关于");form.addView(aboutSection);
        TextView aboutEntry=text("版本、作者与 GitHub  ›",12,TEXT);aboutEntry.setGravity(Gravity.CENTER_VERTICAL);aboutEntry.setPadding(dp(12),0,dp(12),0);aboutEntry.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));aboutEntry.setOnClickListener(v->{dialog.dismiss();showAboutDialog();});form.addView(labeled("关于 元境",aboutEntry));

        View pluginSection=settingsSection("插件");form.addView(pluginSection);
        TextView pluginHint=text("每个内置工具都可以单独开关，默认全部开启。顶部预设可一键切换：精简只保留核心编码工具（"+LITE_PLUGIN_TOOLS.size()+" 个，Root/沙箱/MCP/联网/画布等关闭），默认全部开启。关闭后该工具会从模型工具列表和系统提示词里移除，模型若仍然调用会被直接拒绝；权限模式、Root 与联网开关的约束保持不变。",10.5f,MUTED_2);pluginHint.setLineSpacing(0,1.12f);pluginHint.setPadding(dp(2),0,dp(2),dp(8));form.addView(pluginHint,lp(-1,-2));
        LinkedHashMap<String,Boolean> pluginSwitches=new LinkedHashMap<>();
        LinkedHashMap<String,PluginToggle> pluginRows=new LinkedHashMap<>();
        LinearLayout pluginPresets=hbox();pluginPresets.setGravity(Gravity.CENTER_VERTICAL);pluginPresets.setPadding(0,0,0,dp(10));
        TextView lite=dialogButton("精简",MUTED,false);lite.setOnClickListener(v->applyPluginPreset(pluginSwitches,pluginRows,true));pluginPresets.addView(lite,dialogActionParams());
        TextView all=dialogButton("默认（全开）",TEXT,false);all.setOnClickListener(v->applyPluginPreset(pluginSwitches,pluginRows,false));pluginPresets.addView(all,dialogActionParams());
        form.addView(pluginPresets,lp(-1,dp(56)));
        String[] pluginNames=ToolRegistry.pluginNames();
        for(int i=0;i<pluginNames.length;i++){form.addView(pluginToggleRow(pluginNames[i],pluginSwitches,pluginRows),lp(-1,dp(50)));}

        View[] sectionMarkers={appearanceSection,modelSection,localSection,agentSection,networkSection,contextSection,extensionsSection,aboutSection,pluginSection};
        String[] sectionLabels={"外观","模型与权限","本地模型","Agent 与安全","联网","上下文与项目","扩展功能","关于","插件"};
        ArrayList<LinearLayout> pageLayouts=new ArrayList<>();
        for(int i=0;i<sectionMarkers.length;i++){LinearLayout page=vbox();page.setPadding(dp(2),0,dp(2),dp(8));pageLayouts.add(page);}
        ArrayList<View> settingChildren=new ArrayList<>();for(int i=0;i<form.getChildCount();i++)settingChildren.add(form.getChildAt(i));
        int pageIndex=0;
        for(View child:settingChildren){for(int i=0;i<sectionMarkers.length;i++)if(child==sectionMarkers[i])pageIndex=i;form.removeView(child);pageLayouts.get(pageIndex).addView(child);}
        HorizontalScrollView categoryScroll=new HorizontalScrollView(this);categoryScroll.setHorizontalScrollBarEnabled(false);categoryScroll.setFillViewport(true);
        LinearLayout categories=hbox();categories.setGravity(Gravity.CENTER_VERTICAL);categories.setPadding(0,dp(2),0,dp(4));
        FrameLayout pageHost=new FrameLayout(this);ArrayList<ScrollView> pageScrolls=new ArrayList<>();
        for(int i=0;i<pageLayouts.size();i++){final int selected=i;ScrollView pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);pageScroll.setVerticalScrollBarEnabled(false);pageScroll.addView(pageLayouts.get(i));pageScroll.setVisibility(i==0?View.VISIBLE:View.GONE);pageHost.addView(pageScroll,new FrameLayout.LayoutParams(-1,-1));pageScrolls.add(pageScroll);
            TextView tab=settingsTab(sectionLabels[i],()->{for(int j=0;j<pageScrolls.size();j++)pageScrolls.get(j).setVisibility(j==selected?View.VISIBLE:View.GONE);categoryScroll.smoothScrollTo(Math.max(0,tabLeft(categories,selected)-dp(18)),0);});LinearLayout.LayoutParams tabLp=new LinearLayout.LayoutParams(-2,dp(40));tabLp.setMargins(0,0,dp(6),0);categories.addView(tab,tabLp);}
        categoryScroll.addView(categories,new android.widget.FrameLayout.LayoutParams(-1,dp(44)));panel.addView(categoryScroll,lp(-1,dp(48)));panel.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout footer=hbox(); footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(0,dp(8),0,0);
        TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->dialog.dismiss());footer.addView(cancel,dialogActionParams());
        TextView save=dialogButton("保存",TEXT,true);
        save.setOnClickListener(v->{try{
            int selectedBackground=parsePaletteColor(paletteBackground),selectedSurface=parsePaletteColor(paletteSurface),selectedText=parsePaletteColor(paletteText),selectedAccent=parsePaletteColor(paletteAccent),selectedGreen=parsePaletteColor(paletteGreen),selectedRed=parsePaletteColor(paletteRed);
            boolean paletteChanged=selectedBackground!=BG||selectedSurface!=SURFACE||selectedText!=TEXT||selectedAccent!=ACCENT||selectedGreen!=GREEN||selectedRed!=RED;
            String selectedTheme=themeValues[Math.max(0,themeMode.getSelectedItemPosition())];
            config.visionEnabled=vision.getSelectedItemPosition()==0;
            String selectedEffort=effortValues[Math.max(0,effort.getSelectedItemPosition())];
            config.effort=selectedEffort;SessionRuntime effortRuntime=activeRuntime;if(effortRuntime!=null){if(effortRuntime.nextConfig==null)effortRuntime.nextConfig=config.copy();else effortRuntime.nextConfig.effort=selectedEffort;if(effortRuntime.engine.isBusy())effortRuntime.engine.updateReasoningEffort(selectedEffort);}
            if(!isPermissionModeLocked())config.permissionMode=permValues[Math.max(0,perm.getSelectedItemPosition())];
            config.sandboxAgentFullAccess=sandboxAccess.getSelectedItemPosition()==0;
            config.rootExecutionEnabled=rootAccess.getSelectedItemPosition()==1;
            config.shizukuExecutionEnabled=shizukuAccess.getSelectedItemPosition()==1;
            config.forcedKeepAliveEnabled=keepAlive.getSelectedItemPosition()==1;
            config.localFallback=localFallbackSwitch.getSelectedItemPosition()==0;
            config.localStrategy=localStrategyValues[Math.max(0,localStrategy.getSelectedItemPosition())];
            config.webSearchEnabled=webEnabled.getSelectedItemPosition()==0;
            config.webSearchProvider=webProviderValues[Math.max(0,webProvider.getSelectedItemPosition())];
            config.webSearchMaxResults=Math.max(1,Math.min(10,Integer.parseInt(webResults.getText().toString().trim())));
            config.webTimeoutMs=Math.max(5000,Math.min(60000,Integer.parseInt(webTimeout.getText().toString().trim())*1000));
            config.contextWindowTokens=parseTokenCount(contextWindow.getText().toString());
            config.autoCompact=autoCompact.getSelectedItemPosition()==0;
            int compactPct=Math.max(50,Math.min(100,Integer.parseInt(compactAt.getText().toString().trim()))); config.autoCompactRatio=compactPct/100.0;
            int selectedWindow=Integer.parseInt(windowValues[Math.max(0,historyWindow.getSelectedItemPosition())]);
            boolean windowChanged=selectedWindow!=config.transcriptWindowMessages;
            config.transcriptWindowMessages=selectedWindow;
            String requestedProject=project.getText().toString().trim();
            boolean projectChanged=switchProjectPath(requestedProject,false);
            for(Map.Entry<String,Boolean> entry:pluginSwitches.entrySet())config.setToolEnabled(entry.getKey(),entry.getValue());
            settingsStore.save(config);
            applyToolSwitchesToRuntimes();
            if(config.forcedKeepAliveEnabled)KeepAliveService.start(this,config.rootExecutionEnabled);else KeepAliveService.stop(this);if(!projectChanged&&engine!=null&&!engine.isBusy())engine.configure(config);dialog.dismiss();if(paletteChanged)saveAndApplyCustomPalette(selectedBackground,selectedSurface,selectedText,selectedAccent,selectedGreen,selectedRed);else saveAndApplyUiTheme(selectedTheme);if(windowChanged){transcriptRenderLimit=config.transcriptWindowMessages;if(streamingItem==null&&streamingView==null)rebuildTranscriptViews();}updateComposerChips();toast(projectChanged?"项目已切换，请选择上下文历史":"设置已保存");if(projectChanged)uiHandler.postDelayed(()->showProjectHistoryPicker(true),180);
        }catch(Exception e){toast("保存失败："+e.getMessage());}});
        footer.addView(save,dialogActionParams());panel.addView(footer,lp(-1,dp(60)));

        dialog.setContentView(panel);dialog.show();
        styleDialogWindow(dialog,dialogWidthPx(860),Math.min(dp(720),(int)(getResources().getDisplayMetrics().heightPixels*.88f)));
    }

    /** 本地模型库：二进制状态 + 设备推荐 + 5 档模型下载/删除。 */
    private void showLocalModelPanel(){
        final Dialog dialog=newOverlayAwareDialog();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();

        LinearLayout titleRow=hbox(); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(dialogTitle("本地模型库"),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView x=iconButton(R.drawable.ic_close); x.setOnClickListener(v->dialog.dismiss()); titleRow.addView(x,lp(dp(44),dp(44))); panel.addView(titleRow,lp(-1,dp(48)));

        LinearLayout form=vbox(); form.setPadding(0,dp(4),0,dp(8));

        String binReason=LocalModelManager.missingBinaryReason();
        TextView binRow=text(binReason==null?"✓ llama-server 引擎已就绪":"⚠ "+binReason,12,binReason==null?GREEN:ACCENT);
        binRow.setPadding(dp(12),dp(8),dp(12),dp(8));binRow.setBackground(round(SURFACE_3,12,Color.TRANSPARENT,0));
        LinearLayout.LayoutParams binLp=new LinearLayout.LayoutParams(-1,-2);binLp.setMargins(0,0,0,dp(6));form.addView(binRow,binLp);

        String rec;
        try{
            DeviceProfile dp=DeviceProfile.detect(this);
            LocalModelManager.ModelDef recommended=LocalModelManager.recommend(dp);
            rec="设备档位 "+dp.tier()+" · 内存 "+dp.totalRamMb+"MB（可用 "+dp.availRamMb+"MB）"+
                (recommended==null?"；当前设备无法运行任何本地模型":"；本机推荐：\n"+recommended.id+"（"+recommended.sizeMb+"MB，需内存 "+recommended.minRamMb+"MB）");
        }catch(Throwable t){rec="设备信息读取失败";}
        TextView recRow=text(rec,11.5f,MUTED);recRow.setLineSpacing(0,1.15f);recRow.setPadding(dp(12),dp(8),dp(12),dp(8));recRow.setBackground(round(SURFACE_3,12,Color.TRANSPARENT,0));recRow.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);form.addView(recRow,lp(-1,-2));

        final LinearLayout list=vbox();form.addView(list,lp(-1,-2));
        refreshLocalModelList(list,dialog);

        panel.addView(form,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(panel);dialog.show();
        styleDialogWindow(dialog,dialogWidthPx(720),Math.min(dp(640),(int)(getResources().getDisplayMetrics().heightPixels*.88f)));
    }

    /** 重建模型列表（每行卡片：模型名 + 状态标签 chip + 详情 + 下载/删除按钮 + 下载进度）。 */
    private void refreshLocalModelList(final LinearLayout list,final Dialog dialog){
        list.removeAllViews();
        DeviceProfile profile=null;
        try{ profile=DeviceProfile.detect(this); }catch(Throwable ignored){}
        LocalModelManager.ModelDef[] defs=LocalModelManager.catalog();
        for(int i=0;i<defs.length;i++){
            final LocalModelManager.ModelDef def=defs[i];
            final boolean downloaded=LocalModelManager.isDownloaded(def);
            // 按设备可用内存给每个模型打标签：推荐 / 可运行 / 内存不足（chip 化）
            String badge="";int chipBg=0,chipFg=TEXT;
            if(profile!=null){
                if(def.minRamMb<=profile.availRamMb*0.7f){badge="推荐";chipBg=Color.argb(45,56,189,248);chipFg=ACCENT;}
                else if(def.minRamMb<=profile.availRamMb){badge="可运行";chipBg=Color.argb(45,80,200,120);chipFg=GREEN;}
                else{badge="内存不足";chipBg=Color.argb(45,248,113,113);chipFg=RED;}
            }
            // 卡片：纵向布局（顶部行=名称+标签+按钮；第二行=详情）
            LinearLayout row=vbox();row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackground(round(SURFACE_3,16,Color.TRANSPARENT,0));
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.setMargins(0,0,0,dp(8));list.addView(row,rowLp);
            LinearLayout top=hbox();top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=text(def.id,13,downloaded?TEXT:MUTED);name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            if(!badge.isEmpty()){
                TextView chip=text(badge,10,chipFg);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(8),dp(3),dp(8),dp(3));chip.setBackground(round(chipBg,11,Color.TRANSPARENT,0));
                LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-2,-2);cl.setMargins(0,0,dp(10),0);top.addView(chip,cl);
            }
            final TextView btn=text(downloaded?"删除":"下载",12,downloaded?RED:GREEN);
            btn.setGravity(Gravity.CENTER);btn.setPadding(dp(16),0,dp(16),0);btn.setBackground(round(SURFACE_3,16,downloaded?Color.argb(80,255,80,80):Color.argb(80,80,180,120),1));
            top.addView(btn,lp(dp(88),dp(40)));
            row.addView(top,lp(-1,-2));
            TextView detail=text(def.sizeMb+"MB · 需内存 "+def.minRamMb+"MB · 上下文 "+def.contextTokens+(def.supportsTools?" · 支持工具":" · 无工具"),11,MUTED);
            detail.setLineSpacing(0,1.1f);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.setMargins(0,dp(5),0,0);row.addView(detail,dl);
            if(downloaded){
                btn.setOnClickListener(v->{
                    java.io.File f=new java.io.File(LocalModelManager.modelsDir(),def.ggufFile);
                    if(f.delete()){toast("已删除 "+def.id);refreshLocalModelList(list,dialog);}
                    else toast("删除失败，请手动删除:\n"+f.getAbsolutePath());
                });
            }else{
                btn.setOnClickListener(v->{
                    btn.setEnabled(false);btn.setAlpha(.6f);btn.setText("下载中…");
                    new Thread(()->{
                        try{
                            LocalModelManager.download(MainActivity.this,def,false,(done,total)->{
                                final int pct=(int)(total<=0?0:done*100L/total);
                                uiHandler.post(()->{btn.setText("下载中 "+pct+"%");});
                            });
                            uiHandler.post(()->{toast("下载完成: "+def.id);refreshLocalModelList(list,dialog);});
                        }catch(Exception e){
                            uiHandler.post(()->{toast("下载失败: "+e.getMessage());refreshLocalModelList(list,dialog);});
                        }
                    }).start();
                });
            }
        }
    }

    private View buildThemePreview(Spinner themeSpinner){
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,0,0,dp(6));
        themeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){refreshThemePreview(row,themeSpinner);}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        refreshThemePreview(row,themeSpinner);
        return row;
    }

    private void refreshThemePreview(LinearLayout row,Spinner themeSpinner){
        row.removeAllViews();int selected=themeSpinner.getSelectedItemPosition();
        View classic=themePreviewCard("暖墨","奶油白 · 灰绿",false,selected==0,themeSpinner);
        View neon=themePreviewCard("暖纸","墨色 · 苔绿",true,selected==1,themeSpinner);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(68),1);left.setMargins(0,0,dp(4),0);row.addView(classic,left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(68),1);right.setMargins(dp(4),0,0,0);row.addView(neon,right);
        UiMotion.staggerChildren(row,2);UiMotion.bindInteractive(row);
    }

    private View themePreviewCard(String name,String detail,boolean day,boolean selected,Spinner themeSpinner){
        int fill=day?Color.rgb(247,246,242):Color.rgb(31,29,27);
        int stroke=selected?(day?Color.rgb(55,105,75):Color.rgb(126,178,136)):(day?Color.rgb(213,209,199):Color.rgb(55,51,46));
        LinearLayout card=vbox();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(6),dp(10),dp(6));card.setBackground(round(fill,12,stroke,1));
        TextView heading=text((selected?"●  ":"○  ")+name,11,day?Color.rgb(40,39,35):Color.rgb(240,236,229));heading.setTypeface(Typeface.DEFAULT_BOLD);card.addView(heading,lp(-1,dp(25)));
        TextView sub=text(detail,10.5f,day?Color.rgb(101,97,89):Color.rgb(164,157,147));card.addView(sub,lp(-1,dp(22)));
        card.setOnClickListener(v->{themeSpinner.setSelection(day?1:0);UiMotion.contentUpdated(card,true);});
        return card;
    }

    /**
     * One plugin row: tool label plus a themed sliding toggle. Enabled rows keep the label bright
     * and grow an accent-gradient track; disabled rows dim the label into a flat outlined track.
     */
    /** Live handle to one plugin switch so a preset can flip the row instead of rebuilding the page. */
    private static final class PluginToggle{
        final boolean[] on;final FrameLayout track;final View knob;final TextView label;
        PluginToggle(boolean[] on,FrameLayout track,View knob,TextView label){this.on=on;this.track=track;this.knob=knob;this.label=label;}
    }

    /**
     * 精简 preset: the tools a coding session actually needs. Root, the sandbox, MCP, web search, the UI
     * canvas, Worktree, Termux repair, Skill and Sleep stay off so the model gets a small, focused list.
     */
    private static final Set<String> LITE_PLUGIN_TOOLS=new LinkedHashSet<>(Arrays.asList(
        "Bash","Read","ReadMany","Stat","Tree","Write","Copy","Edit","MultiEdit","Mkdir","Move","Delete",
        "Glob","Grep","LS","TaskCreate","TaskGet","TaskList","TaskUpdate","TodoWrite","EnterPlanMode","ExitPlanMode"));

    private View pluginToggleRow(String name,LinkedHashMap<String,Boolean> states,Map<String,PluginToggle> handles){
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text(toolPluginLabel(name),12,TEXT);row.addView(label,new LinearLayout.LayoutParams(0,dp(44),1));
        FrameLayout track=new FrameLayout(this);
        View knob=new View(this);FrameLayout.LayoutParams knobLp=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.START|Gravity.CENTER_VERTICAL);knobLp.leftMargin=dp(4);track.addView(knob,knobLp);
        LinearLayout.LayoutParams trackLp=new LinearLayout.LayoutParams(dp(48),dp(30));trackLp.gravity=Gravity.CENTER_VERTICAL;row.addView(track,trackLp);
        final boolean[] on={!config.isToolDisabled(name)};
        states.put(name,on[0]);
        handles.put(name,new PluginToggle(on,track,knob,label));
        track.setContentDescription(name+" 插件开关");
        applyPluginToggle(track,knob,label,on[0],false);
        track.setOnClickListener(v->{on[0]=!on[0];states.put(name,on[0]);applyPluginToggle(track,knob,label,on[0],true);});
        return row;
    }

    /**
     * Tool preset applied straight from the plugin page: 精简 keeps {@link #LITE_PLUGIN_TOOLS}, 默认 turns
     * every tool back on. It saves and pushes the switches into live sessions immediately, because the
     * whole point is to skip hand-flipping 38 rows and then remembering to press 保存.
     */
    private void applyPluginPreset(LinkedHashMap<String,Boolean> states,Map<String,PluginToggle> handles,boolean lite){
        String[] names=ToolRegistry.pluginNames();int on=0;
        for(String name:names){
            boolean enabled=!lite||LITE_PLUGIN_TOOLS.contains(name);
            if(enabled)on++;
            states.put(name,enabled);
            config.setToolEnabled(name,enabled);
            PluginToggle handle=handles.get(name);
            if(handle!=null){handle.on[0]=enabled;applyPluginToggle(handle.track,handle.knob,handle.label,enabled,true);}
        }
        try{settingsStore.save(config);}catch(Exception e){toast("保存失败："+e.getMessage());return;}
        applyToolSwitchesToRuntimes();
        if(engine!=null&&!engine.isBusy())engine.configure(config.copy());
        refreshChrome();
        toast(lite?"已应用精简预设 · 开启 "+on+" 个核心工具，其余关闭":"已恢复默认 · "+on+" 个工具全部开启");
    }

    /** Pushes the current tool switches into every live session so they apply from the next request. */
    private void applyToolSwitchesToRuntimes(){
        for(SessionRuntime rt:new ArrayList<>(sessionRuntimes.values())){if(rt==null)continue;if(rt.nextConfig==null)rt.nextConfig=config.copy();else{rt.nextConfig.disabledTools.clear();rt.nextConfig.disabledTools.addAll(config.disabledTools);}if(!rt.engine.isBusy())rt.engine.configure(rt.nextConfig.copy());}
    }

    private void applyPluginToggle(FrameLayout track,View knob,TextView label,boolean on,boolean animate){
        track.setBackground(on?accentGradient(15):round(SURFACE_3,14,BORDER,1));
        knob.setBackground(round(on?Color.WHITE:MUTED_2,10,Color.TRANSPARENT,0));
        knob.setElevation(on?dp(2):0);
        knob.setAlpha(on?1f:.85f);
        label.setTextColor(on?TEXT:MUTED);
        float travel=on?dp(18):0f;
        if(animate){
            UiMotion.slideToX(knob,travel,180L);
            UiMotion.contentUpdated(track,on);
        }else{
            knob.animate().cancel();knob.setTranslationX(travel);
        }
    }

    private void showAboutDialog(){
        final Dialog dialog=newOverlayAwareDialog();dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);head.addView(dialogTitle("关于 元境"),new LinearLayout.LayoutParams(0,dp(48),1));TextView close=iconButton(R.drawable.ic_close);close.setOnClickListener(v->dialog.dismiss());head.addView(close,lp(dp(44),dp(44)));panel.addView(head,lp(-1,dp(48)));
        LinearLayout brand=hbox();brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(dp(2),dp(8),dp(2),dp(8));
        TextView badge=text("元",18,Color.WHITE);badge.setTypeface(Typeface.DEFAULT_BOLD);badge.setGravity(Gravity.CENTER);badge.setBackground(neonTheme?accentGradient(18):round(ACCENT,20,Color.TRANSPARENT,0));brand.addView(badge,lp(dp(54),dp(54)));
        LinearLayout brandCopy=vbox();brandCopy.setPadding(dp(12),0,0,0);TextView appName=text("元境",14,TEXT);appName.setTypeface(Typeface.DEFAULT_BOLD);brandCopy.addView(appName,lp(-1,dp(26)));TextView appDesc=text("运行在手机上的原生 AI 编程 Agent",10.5f,MUTED);brandCopy.addView(appDesc,lp(-1,dp(20)));brand.addView(brandCopy,new LinearLayout.LayoutParams(0,dp(54),1));panel.addView(brand,lp(-1,dp(70)));
        panel.addView(aboutRow(0,"版本",appVersionText(),null));
        panel.addView(aboutRow(0,"作者","zihan8023",null));
        panel.addView(aboutRow(0,"包名",getPackageName(),null));
        panel.addView(aboutRow(R.drawable.ic_globe,"GitHub","github.com/zihan8023",v->openExternal("https://github.com/zihan8023")));
        panel.addView(aboutRow(R.drawable.ic_refresh,"检查更新","点击检查",v->checkForUpdates()));
        TextView contributorsTitle=text("贡献者",10.5f,MUTED_2);contributorsTitle.setTypeface(Typeface.DEFAULT_BOLD);contributorsTitle.setPadding(dp(8),dp(8),0,dp(2));panel.addView(contributorsTitle,lp(-1,dp(26)));
        for(String[] contributor:CONTRIBUTORS){String handle=contributor[1];panel.addView(aboutRow(R.drawable.ic_globe,contributor[0],"github.com/"+handle,v->openExternal("https://github.com/"+handle)));}

        TextView note=text("模型 API 由你自行配置，密钥保存在 Android 安全存储中；终端命令、文件修改与沙箱能力请只用于可信项目。",11.5f,MUTED_2);note.setLineSpacing(0,1.14f);note.setPadding(dp(2),dp(10),dp(2),dp(6));panel.addView(note,lp(-1,-2));
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(6),0,0);
        TextView diagnostics=dialogButton("复制诊断信息",MUTED,false);diagnostics.setOnClickListener(v->copyDiagnostics());actions.addView(diagnostics,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView done=dialogButton("完成",TEXT,true);done.setOnClickListener(v->dialog.dismiss());LinearLayout.LayoutParams doneLp=new LinearLayout.LayoutParams(dp(96),dp(48));doneLp.setMargins(dp(8),0,0,0);actions.addView(done,doneLp);
        panel.addView(actions,lp(-1,dp(60)));
        int width=dialogWidthPx(430);
        dialog.setContentView(scrollBody(panel,width,dialogMaxBodyPx()));dialog.show();styleDialogWindow(dialog,width,-2);
    }

    /** A label/value row for the about page; clickable rows turn the value into an accent link. */
    private View aboutRow(int iconRes,String label,String value,View.OnClickListener click){
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));
        if(iconRes!=0)row.addView(iconOnly(iconRes,18,ACCENT),lp(dp(26),dp(48)));
        TextView name=text(label,12,MUTED);row.addView(name,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView data=text(value,12,click==null?TEXT:ACCENT);data.setTypeface(click==null?Typeface.DEFAULT:Typeface.DEFAULT_BOLD);data.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);data.setSingleLine(true);data.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);row.addView(data,lp(-2,dp(48)));
        if(click!=null){row.setOnClickListener(click);data.setOnClickListener(click);row.addView(iconOnly(R.drawable.ic_chevron_right,14,MUTED_2),lp(dp(18),dp(48)));}
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,dp(52));rowLp.setMargins(0,0,0,dp(6));row.setLayoutParams(rowLp);
        return row;
    }

    private String appVersionText(){
        try{
            android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
            return "v"+info.versionName+" ("+info.versionCode+")";
        }catch(Throwable ignored){ return ""; }
    }

    private void openExternal(String url){
        try{ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch(Throwable error){ toast("没有可用的应用打开这个链接"); }
    }

    /** 更新检查：后台请求 GitHub latest release，结果弹窗展示。 */
    private volatile String lastReleaseUrl;
    private void checkForUpdates(){
        toast("正在检查更新…");
        new Thread(()->{
            String result=fetchLatestRelease();
            runOnUiThread(()->showUpdateResult(result));
        }).start();
    }
    private String fetchLatestRelease(){
        try{
            java.net.HttpURLConnection conn=(java.net.HttpURLConnection)new java.net.URL(GITHUB_API_LATEST).openConnection();
            conn.setConnectTimeout(8000);conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept","application/vnd.github+json");
            conn.setRequestProperty("User-Agent","yuanjing-android");
            int code=conn.getResponseCode();
            if(code==404) return "你的仓库还没有发布任何 Release。\n\n发布方法：打开 GitHub 仓库 → Releases → Create a new release，Tag 填 v0.31.0 这样的版本号。";
            if(code!=200) return "检查失败（GitHub 返回 "+code+"），请稍后重试。";
            java.io.InputStream in=conn.getInputStream();
            java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(in,"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=reader.readLine())!=null) sb.append(line);
            reader.close();
            org.json.JSONObject json=new org.json.JSONObject(sb.toString());
            String tag=json.optString("tag_name","");
            lastReleaseUrl=json.optString("html_url","");
            String name=json.optString("name",tag);
            String published=json.optString("published_at","").replace("T"," ").replace("Z","");
            String current="";
            try{ current=getPackageManager().getPackageInfo(getPackageName(),0).versionName; }catch(Throwable ignored){}
            if(compareVersions(tag,current)>0)
                return "发现新版本："+(name.isEmpty()?tag:name)+"（"+tag+"）\n\n当前版本：v"+current+"\n发布时间："+published+"\n\n有新版本可以下载安装了。";
            else
                return "已是最新版本：v"+current+"\n\nGitHub 最新 Release："+(name.isEmpty()?tag:name)+"（"+tag+"）";
        }catch(Exception e){
            return "无法连接 GitHub："+e.getMessage()+"\n\n请检查网络后重试。";
        }
    }
    /** 版本号比较："v0.31.0" vs "0.30.4" → 1（a 更新） */
    private int compareVersions(String a,String b){
        String[] pa=a.replaceAll("[^0-9.]","").split("\\.");
        String[] pb=b.replaceAll("[^0-9.]","").split("\\.");
        for(int i=0;i<Math.max(pa.length,pb.length);i++){
            int x=i<pa.length&&!pa[i].isEmpty()?Integer.parseInt(pa[i]):0;
            int y=i<pb.length&&!pb[i].isEmpty()?Integer.parseInt(pb[i]):0;
            if(x!=y) return x>y?1:-1;
        }
        return 0;
    }
    private void showUpdateResult(String result){
        boolean hasUpdate=result.startsWith("发现新版本");
        final Dialog dialog=newOverlayAwareDialog();dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);head.addView(dialogTitle(hasUpdate?"发现新版本":"更新检查"),new LinearLayout.LayoutParams(0,dp(48),1));TextView close=iconButton(R.drawable.ic_close);close.setOnClickListener(v->dialog.dismiss());head.addView(close,lp(dp(44),dp(44)));panel.addView(head,lp(-1,dp(48)));
        TextView body=text(result,11.5f,TEXT);body.setLineSpacing(0,1.18f);body.setPadding(dp(4),dp(10),dp(4),dp(8));panel.addView(body,lp(-1,-2));
        if(hasUpdate){
            LinearLayout actions=hbox();actions.setPadding(0,dp(8),0,0);
            TextView open=dialogButton("打开下载页",ACCENT,true);open.setOnClickListener(v->{dialog.dismiss();openExternal(lastReleaseUrl);});actions.addView(open,new LinearLayout.LayoutParams(0,dp(48),1));
            panel.addView(actions,lp(-1,dp(64)));
        }else{
            TextView ok=dialogButton("知道了",TEXT,true);ok.setOnClickListener(v->dialog.dismiss());panel.addView(ok,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        int width=dialogWidthPx(400);
        dialog.setContentView(scrollBody(panel,width,dialogMaxBodyPx()));dialog.show();styleDialogWindow(dialog,width,-2);
    }

    private void copyDiagnostics(){
        String text="元境 "+appVersionText()+"\nAndroid "+android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")"
            +"\nABI "+(android.os.Build.SUPPORTED_ABIS.length>0?android.os.Build.SUPPORTED_ABIS[0]:"?")
            +"\n"+(android.os.Build.MANUFACTURER==null?"":android.os.Build.MANUFACTURER+" ")+(android.os.Build.MODEL==null?"":android.os.Build.MODEL)
            +"\n项目路径 "+config.projectDirectory;
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("iqcode_diagnostics",text));
        toast("诊断信息已复制，可直接粘贴到问题反馈里");
    }

    /** Sponsor prompt cadence: first ask on launch 10, then grow the interval by 10 (10 -> 30 -> 60 -> 100 -> ...). */
    private void tickSponsorPrompt(){
        if(sponsorPromptTickDone)return;
        sponsorPromptTickDone=true;
        android.content.SharedPreferences prefs=getSharedPreferences(SPONSOR_PREFS,MODE_PRIVATE);
        int launches=prefs.getInt("launches",0)+1;
        int interval=prefs.getInt("interval",10);
        int nextAt=prefs.getInt("next_at",10);
        prefs.edit().putInt("launches",launches).apply();
        if(launches<nextAt)return;
        if(isFinishing()||isDestroyed()||storagePermissionDialogVisible||androidIntegrationDialogVisible)return;
        prefs.edit().putInt("interval",interval+10).putInt("next_at",launches+interval+10).apply();
        showSponsorDialog();
    }

    private void showSponsorDialog(){
        final Dialog dialog=newOverlayAwareDialog();dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);TextView heart=iconOnly(R.drawable.ic_heart,22,ACCENT);heart.setGravity(Gravity.CENTER);head.addView(heart,lp(dp(30),dp(48)));head.addView(dialogTitle("支持 元境"),new LinearLayout.LayoutParams(0,dp(48),1));panel.addView(head,lp(-1,dp(48)));
        TextView body=text("元境 是一个人在业余时间写出来的：模型协议、工具链、内置 Termux、BlackBox 沙箱，还有你看到的每一个图标。\n\n如果它确实帮你省下了时间，可以在爱发电请我喝一杯咖啡。",12,MUTED);body.setLineSpacing(dp(2),1.14f);panel.addView(body,lp(-1,-2));
        panel.addView(aboutRow(R.drawable.ic_heart,"爱发电","afdian.com/a/IQ_ge",v->{dialog.dismiss();openExternal(SPONSOR_URL);}));
        TextView hint=text("拒绝也没关系，元境 的全部功能依旧免费。",11.5f,MUTED_2);hint.setPadding(dp(2),dp(10),dp(2),dp(4));panel.addView(hint,lp(-1,-2));
        panel.addView(sponsorActionRow("去发电",()->{dialog.dismiss();openExternal(SPONSOR_URL);},"拒绝赞助",()->{dialog.dismiss();showSadTearsDialog();}),lp(-1,dp(60)));
        dialog.setContentView(panel);dialog.show();styleDialogWindow(dialog,dialogWidthPx(430),-2);
    }

    /** Second, non-dismissable step: the author sheds a tear and the same two switches are the only way out. */
    private void showSadTearsDialog(){
        final Dialog dialog=newOverlayAwareDialog();dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);dialog.setCanceledOnTouchOutside(false);
        LinearLayout panel=dialogPanel();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);TextView tear=iconOnly(R.drawable.ic_tear,22,ACCENT);tear.setGravity(Gravity.CENTER);head.addView(tear,lp(dp(30),dp(48)));head.addView(dialogTitle("作者流下了伤心的泪水"),new LinearLayout.LayoutParams(0,dp(48),1));panel.addView(head,lp(-1,dp(48)));
        TextView body=text("眼泪擦一擦，代码继续写。\n\n元境 不会因为你不赞助而少掉任何一行功能，但你随时可以改变主意。",12,MUTED);body.setLineSpacing(dp(2),1.14f);panel.addView(body,lp(-1,-2));
        panel.addView(sponsorActionRow("去发电",()->{dialog.dismiss();openExternal(SPONSOR_URL);},"拒绝赞助",()->{dialog.dismiss();toast("好吧，祝你用得顺手 🥲");}),lp(-1,dp(60)));
        dialog.setContentView(panel);dialog.show();styleDialogWindow(dialog,dialogWidthPx(430),-2);
    }

    /** Primary/secondary button pair shared by the sponsor dialogs. */
    private LinearLayout sponsorActionRow(String primaryLabel,Runnable onPrimary,String secondaryLabel,Runnable onSecondary){
        LinearLayout actions=hbox();actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView secondary=dialogButton(secondaryLabel,MUTED,false);secondary.setOnClickListener(v->onSecondary.run());actions.addView(secondary,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView primary=dialogButton(primaryLabel,TEXT,true);primary.setOnClickListener(v->onPrimary.run());LinearLayout.LayoutParams primaryLp=new LinearLayout.LayoutParams(dp(96),dp(48));primaryLp.setMargins(dp(8),0,0,0);actions.addView(primary,primaryLp);
        return actions;
    }

    /** Display label for one built-in tool in the plugin settings list. */
    private String toolPluginLabel(String name){
        String[] entry=PLUGIN_LABELS.get(name);
        return entry==null?name:entry[0]+"  ·  "+entry[1];
    }

    private static final Map<String,String[]> PLUGIN_LABELS=buildPluginLabels();
    private static Map<String,String[]> buildPluginLabels(){
        Map<String,String[]> m=new LinkedHashMap<>();
        m.put("Bash",new String[]{"Bash","终端命令"});m.put("Root",new String[]{"Root","超级用户命令"});m.put("Shizuku",new String[]{"Shizuku","shell 身份系统命令"});
        m.put("AndroidIntent",new String[]{"AndroidIntent","启动应用/URL"});m.put("Sandbox",new String[]{"Sandbox","IQ 沙箱虚拟安卓"});
        m.put("Debug",new String[]{"Debug","调试与 Frida"});m.put("ui_canvas",new String[]{"ui_canvas","界面画布调整"});
        m.put("GitStatus",new String[]{"GitStatus","仓库状态"});m.put("WebSearch",new String[]{"WebSearch","联网搜索"});
        m.put("WebFetch",new String[]{"WebFetch","抓取网页"});m.put("TermuxDoctor",new String[]{"TermuxDoctor","Termux 环境诊断"});
        m.put("TermuxRepair",new String[]{"TermuxRepair","修复包管理状态"});m.put("EnterWorktree",new String[]{"EnterWorktree","进入独立工作树"});
        m.put("ExitWorktree",new String[]{"ExitWorktree","退出工作树"});m.put("Read",new String[]{"Read","读取文件"});
        m.put("ReadMany",new String[]{"ReadMany","批量读取"});m.put("Stat",new String[]{"Stat","文件信息"});
        m.put("Tree",new String[]{"Tree","目录树"});m.put("Write",new String[]{"Write","写入新文件"});
        m.put("Copy",new String[]{"Copy","复制文件"});m.put("Edit",new String[]{"Edit","精确编辑"});
        m.put("MultiEdit",new String[]{"MultiEdit","批量编辑"});m.put("Mkdir",new String[]{"Mkdir","新建目录"});
        m.put("Move",new String[]{"Move","移动/改名"});m.put("Delete",new String[]{"Delete","删除文件"});
        m.put("Glob",new String[]{"Glob","按文件名查找"});m.put("Grep",new String[]{"Grep","按内容搜索"});
        m.put("LS",new String[]{"LS","列目录"});m.put("TaskCreate",new String[]{"TaskCreate","新建任务"});
        m.put("TaskGet",new String[]{"TaskGet","查看任务"});m.put("TaskList",new String[]{"TaskList","列出任务"});
        m.put("TaskUpdate",new String[]{"TaskUpdate","更新任务"});m.put("Skill",new String[]{"Skill","加载技能"});
        m.put("Sleep",new String[]{"Sleep","短暂等待"});m.put("EnterPlanMode",new String[]{"EnterPlanMode","进入计划模式"});
        m.put("ExitPlanMode",new String[]{"ExitPlanMode","提交计划"});m.put("mcp_list",new String[]{"mcp_list","MCP 工具列表"});
        m.put("mcp_call",new String[]{"mcp_call","MCP 工具调用"});m.put("TodoWrite",new String[]{"TodoWrite","旧版待办"});
        return m;
    }

    private Spinner darkSpinner(String[] values,String selected){ return darkSpinner(values,values,selected); }

    private Spinner darkSpinner(String[] labels,String[] values,String selected){
        Spinner sp=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels){
            @Override public View getView(int pos,View convert,ViewGroup parent){TextView t=(TextView)super.getView(pos,convert,parent);t.setTextColor(TEXT);t.setTextSize(12);t.setPadding(dp(10),0,dp(10),0);t.setBackgroundColor(Color.TRANSPARENT);return t;}
            @Override public View getDropDownView(int pos,View convert,ViewGroup parent){TextView t=(TextView)super.getDropDownView(pos,convert,parent);t.setTextColor(TEXT);t.setTextSize(12);t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setBackgroundColor(SURFACE_3);return t;}
        };
        sp.setAdapter(a); int idx=Math.max(0,Arrays.asList(values).indexOf(selected)); sp.setSelection(idx); sp.setTag(values); sp.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0)); return sp;
    }

    private void showChoicePicker(String title,String[] labels,int checked,java.util.function.IntConsumer choose){
        final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle(title),lp(-1,dp(48)));
        LinearLayout options=vbox();options.setPadding(0,dp(4),0,0);
        for(int i=0;i<labels.length;i++){final int idx=i;TextView r=text((i==checked?"●  ":"○  ")+labels[i],12.5f,i==checked?TEXT:MUTED);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(14),0,dp(14),0);r.setBackground(round(i==checked?SURFACE_3:SURFACE,14,i==checked?ACCENT:BORDER,1));r.setOnClickListener(v->{choose.accept(idx);d.dismiss();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));rp.setMargins(0,0,0,dp(6));options.addView(r,rp);}
        panel.addView(scrollBody(options,dialogWidthPx(400),dialogMaxBodyPx()));
        d.setContentView(panel);d.show();styleDialogWindow(d,dialogWidthPx(400),-2);
    }

    private TextView settingsTab(String label,Runnable action){
        TextView tab=text(label,12,TEXT);tab.setGravity(Gravity.CENTER);tab.setPadding(dp(14),0,dp(14),0);tab.setSingleLine(true);tab.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));tab.setOnClickListener(v->{action.run();UiMotion.contentUpdated(tab,true);});return tab;
    }
    private int tabLeft(LinearLayout tabs,int index){return index>=0&&index<tabs.getChildCount()?tabs.getChildAt(index).getLeft():0;}
    private View settingsSection(String label){
        LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(14),0,dp(4));
        TextView title=text(label,12,ACCENT);title.setTypeface(Typeface.DEFAULT_BOLD);row.addView(title,lp(-2,dp(24)));
        View line=new View(this);line.setBackgroundColor(BORDER);LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(0,dp(1),1);lineLp.setMargins(dp(10),0,0,0);row.addView(line,lineLp);
        return row;
    }
    /** Labelled form field; the 52dp field height keeps it a full-size touch target on a phone. */
    private View labeled(String label,View field){LinearLayout box=vbox();TextView l=text(label,11.5f,MUTED);l.setPadding(dp(2),dp(4),0,0);box.addView(l,lp(-1,dp(26)));box.addView(field,lp(-1,dp(52)));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,0,0,dp(8));box.setLayoutParams(bp);return box;}

    /** 带"?"帮助的设置项：点问号弹大白话解释。 */
    private View labeledHelp(String label,View field,String explanation){
        LinearLayout box=vbox();
        LinearLayout labelRow=hbox();labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView l=text(label,11.5f,MUTED);l.setPadding(dp(2),dp(4),0,0);labelRow.addView(l,new LinearLayout.LayoutParams(0,dp(26),1));
        TextView tip=text("?",10f,ACCENT);tip.setTypeface(Typeface.DEFAULT_BOLD);tip.setGravity(Gravity.CENTER);tip.setPadding(dp(4),0,dp(4),0);tip.setContentDescription("帮助：大白话解释 "+label);tip.setOnClickListener(v->showHelpDialog(label,explanation));labelRow.addView(tip,lp(dp(28),dp(26)));
        box.addView(labelRow,lp(-1,dp(26)));
        box.addView(field,lp(-1,dp(52)));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,0,0,dp(8));box.setLayoutParams(bp);
        return box;
    }
    private void showHelpDialog(String title,String explanation){
        final Dialog dialog=newOverlayAwareDialog();dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        LinearLayout head=hbox();head.setGravity(Gravity.CENTER_VERTICAL);head.addView(dialogTitle(title+"  ·  大白话解释"),new LinearLayout.LayoutParams(0,dp(48),1));TextView close=iconButton(R.drawable.ic_close);close.setOnClickListener(v->dialog.dismiss());head.addView(close,lp(dp(44),dp(44)));panel.addView(head,lp(-1,dp(48)));
        TextView body=text(explanation,11.5f,TEXT);body.setLineSpacing(0,1.22f);body.setPadding(dp(4),dp(10),dp(4),dp(8));panel.addView(body,lp(-1,-2));
        TextView ok=dialogButton("知道了",TEXT,true);ok.setOnClickListener(v->dialog.dismiss());panel.addView(ok,new LinearLayout.LayoutParams(-1,dp(48)));
        int width=dialogWidthPx(380);
        dialog.setContentView(scrollBody(panel,width,dialogMaxBodyPx()));dialog.show();styleDialogWindow(dialog,width,-2);
    }
    private EditText input(String value,boolean secret){
        EditText e=new EditText(this);e.setText(value==null?"":value);e.setTextColor(TEXT);e.setHintTextColor(MUTED_2);
        e.setSingleLine(true);e.setTextSize(13*uiFontScale);e.setPadding(dp(12),0,dp(12),0);
        android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},round(SURFACE_2,12,mixColor(BORDER,ACCENT,.5f),1));
        background.addState(new int[]{},round(SURFACE_2,12,BORDER_SOFT,1));
        e.setBackground(background);
        if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }
    /** Manual #RRGGBB entry with a live swatch; tapping the swatch opens the full palette. */
    private EditText paletteInput(final int color){
        final EditText e=input(paletteHex(color),false);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        e.setCompoundDrawablePadding(dp(10));
        paintPaletteSwatch(e,color);
        e.setOnTouchListener((v,event)->{
            if(event.getX()>e.getPaddingLeft()+dp(38))return false;
            if(event.getAction()==MotionEvent.ACTION_DOWN)return true;
            if(event.getAction()==MotionEvent.ACTION_UP)openColorPalette(e,color);
            return true;
        });
        e.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){ }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){ }
            @Override public void afterTextChanged(Editable s){try{paintPaletteSwatch(e,parsePaletteColor(e));}catch(Exception ignored){ }}
        });
        return e;
    }

    /** 左侧预览：当前颜色作底 + 调色盘图标（图标随底色自动反差），点它直接打开调色盘。 */
    private void paintPaletteSwatch(EditText field,int color){
        GradientDrawable base=round(color,10,BORDER,1);base.setSize(dp(26),dp(26));
        Drawable icon=vectorIcon(R.drawable.ic_palette,17,contrastOn(color));
        LayerDrawable swatch=new LayerDrawable(new Drawable[]{base,icon});
        swatch.setLayerInset(1,dp(4),dp(4),dp(5),dp(5));
        swatch.setBounds(0,0,dp(26),dp(26));
        field.setCompoundDrawablesWithIntrinsicBounds(swatch,null,null,null);
        field.setContentDescription("当前颜色 "+paletteHex(color)+"，点击打开调色盘");
    }

    private void openColorPalette(EditText target,int fallback){
        int start;
        try{start=parsePaletteColor(target);}catch(Exception invalid){start=fallback;}
        final EditText field=target;
        showColorPalette(start,color->{field.setText(paletteHex(color));field.setSelection(field.length());paintPaletteSwatch(field,color);});
    }

    /**
     * Color palette: one live preview fed by a hue rail, saturation/brightness rails and a curated
     * swatch grid. Every control writes into the same HSV triple, so the preview always shows the
     * exact color that "应用" commits back to the field.
     */
    private void showColorPalette(int startColor,java.util.function.IntConsumer onPick){
        final Dialog dialog=newOverlayAwareDialog();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int width=dialogWidthPx(400);
        LinearLayout panel=dialogPanel();

        LinearLayout titleRow=hbox();titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(iconOnly(R.drawable.ic_palette,22,ACCENT),lp(dp(32),dp(48)));
        titleRow.addView(dialogTitle("调色盘"),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView close=iconButton(R.drawable.ic_close);close.setOnClickListener(v->dialog.dismiss());titleRow.addView(close,lp(dp(44),dp(44)));
        panel.addView(titleRow,lp(-1,dp(48)));

        final float[] hsv=new float[3];Color.colorToHSV(startColor,hsv);
        final boolean[] pushing={false};
        final Runnable[] refresh={null};

        // 预览条：左侧一个随背景色自动反差的大号调色盘矢量图，右侧是十六进制 / RGB。
        final LinearLayout previewBox=hbox();previewBox.setGravity(Gravity.CENTER_VERTICAL);
        previewBox.setBackground(round(startColor,14,BORDER,1));
        final TextView previewIcon=iconOnly(R.drawable.ic_palette,34,contrastOn(startColor));
        previewBox.addView(previewIcon,lp(dp(52),dp(64)));
        final TextView preview=text("",12,TEXT);preview.setGravity(Gravity.CENTER);preview.setTypeface(Typeface.MONOSPACE);
        previewBox.addView(preview,new LinearLayout.LayoutParams(0,dp(64),1));
        panel.addView(previewBox,lp(-1,dp(64)));

        final EditText hex=input(paletteHex(startColor),false);
        hex.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        panel.addView(labeled("十六进制",hex));

        panel.addView(paletteRailLabel("色相"));
        final GradientRail hue=new GradientRail(hueGradient(),hsv[0]/360f);panel.addView(hue,lp(-1,dp(46)));
        panel.addView(paletteRailLabel("饱和度"));
        final GradientRail sat=new GradientRail(new int[]{0,0},hsv[1]);panel.addView(sat,lp(-1,dp(46)));
        panel.addView(paletteRailLabel("明度"));
        final GradientRail val=new GradientRail(new int[]{0,0},hsv[2]);panel.addView(val,lp(-1,dp(46)));
        hue.setOnFraction(()->{hsv[0]=hue.fraction()*360f;refresh[0].run();});
        sat.setOnFraction(()->{hsv[1]=sat.fraction();refresh[0].run();});
        val.setOnFraction(()->{hsv[2]=val.fraction();refresh[0].run();});

        refresh[0]=()->{
            int color=Color.HSVToColor(hsv);
            int ink=contrastOn(color);
            previewBox.setBackground(round(color,14,BORDER,1));
            previewIcon.setCompoundDrawables(vectorIcon(R.drawable.ic_palette,34,ink),null,null,null);
            preview.setTextColor(ink);
            preview.setText(paletteHex(color)+"   R"+Color.red(color)+" G"+Color.green(color)+" B"+Color.blue(color));
            hue.setFraction(hsv[0]/360f);
            float railValue=Math.max(0.25f,hsv[2]);
            sat.setColors(new int[]{Color.HSVToColor(new float[]{hsv[0],0f,railValue}),Color.HSVToColor(new float[]{hsv[0],1f,railValue})});
            sat.setFraction(hsv[1]);
            val.setColors(new int[]{Color.HSVToColor(new float[]{hsv[0],hsv[1],0f}),Color.HSVToColor(new float[]{hsv[0],hsv[1],1f})});
            val.setFraction(hsv[2]);
            pushing[0]=true;hex.setText(paletteHex(color));hex.setSelection(hex.length());pushing[0]=false;
        };
        hex.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){ }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){ }
            @Override public void afterTextChanged(Editable s){
                if(pushing[0])return;
                try{Color.colorToHSV(parsePaletteColor(hex),hsv);refresh[0].run();}catch(Exception ignored){ }
            }
        });

        panel.addView(paletteRailLabel("预设"));
        final View[] ringed={null};
        final float[] gridSat={0.85f,0.45f,1.00f};
        final float[] gridVal={0.95f,1.00f,0.55f};
        for(int row=0;row<4;row++){
            LinearLayout line=hbox();
            for(int col=0;col<8;col++){
                final int swatchColor=row==3
                        ?Color.HSVToColor(new float[]{0f,0f,Math.min(1f,0.08f+col*0.128f)})
                        :Color.HSVToColor(new float[]{col*45f,gridSat[row],gridVal[row]});
                View swatch=new View(this);
                swatch.setTag(swatchColor);
                swatch.setBackground(round(swatchColor,10,BORDER,1));
                swatch.setContentDescription("预设颜色 "+paletteHex(swatchColor));
                swatch.setOnClickListener(v->{
                    if(ringed[0]!=null)setPaletteSwatchRing(ringed[0],false);
                    ringed[0]=v;setPaletteSwatchRing(v,true);
                    Color.colorToHSV(swatchColor,hsv);refresh[0].run();
                });
                LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(34),1);slp.setMargins(dp(3),dp(3),dp(3),dp(3));
                line.addView(swatch,slp);
            }
            panel.addView(line,lp(-1,-2));
        }
        refresh[0].run();

        LinearLayout footer=hbox();footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(10),0,0);
        TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->dialog.dismiss());footer.addView(cancel,dialogActionParams());
        TextView apply=dialogButton("应用",TEXT,true);
        apply.setOnClickListener(v->{onPick.accept(Color.HSVToColor(hsv));dialog.dismiss();});
        footer.addView(apply,dialogActionParams());panel.addView(footer,lp(-1,dp(60)));

        dialog.setContentView(scrollBody(panel,width,dialogMaxBodyPx()));dialog.show();
        styleDialogWindow(dialog,width,-2);
    }

    private TextView paletteRailLabel(String label){TextView t=text(label,11.5f,MUTED_2);t.setPadding(dp(2),dp(10),0,dp(4));return t;}

    private void setPaletteSwatchRing(View swatch,boolean on){
        Object tag=swatch.getTag();
        int color=tag instanceof Integer?(Integer)tag:Color.GRAY;
        swatch.setBackground(round(color,10,on?ACCENT:BORDER,on?2:1));
    }

    private static int[] hueGradient(){
        int[] stops=new int[7];
        for(int i=0;i<stops.length;i++)stops[i]=Color.HSVToColor(new float[]{i==stops.length-1?359.99f:i*60f,1f,1f});
        return stops;
    }

    private static float clamp01(float value){return value<0f?0f:(value>1f?1f:value);}

    private static int contrastOn(int color){
        double luma=(0.299*Color.red(color)+0.587*Color.green(color)+0.114*Color.blue(color))/255d;
        return luma>0.62?Color.rgb(16,16,20):Color.rgb(255,255,255);
    }

    /** Horizontal gradient rail with a draggable thumb; the callback fires only on user input. */
    private final class GradientRail extends View {
        private final Paint railPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF railRect=new RectF();
        private int[] colors;
        private float fraction;
        private Runnable onFraction;
        GradientRail(int[] colors,float fraction){
            super(MainActivity.this);
            this.colors=colors;
            this.fraction=clamp01(fraction);
        }
        void setColors(int[] next){colors=next;invalidate();}
        void setFraction(float next){fraction=clamp01(next);invalidate();}
        float fraction(){return fraction;}
        void setOnFraction(Runnable action){onFraction=action;}
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                fraction=clamp01(event.getX()/Math.max(1f,getWidth()));
                invalidate();
                if(onFraction!=null)onFraction.run();
                return true;
            }
            return action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||super.onTouchEvent(event);
        }
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight();
            if(w<=0f||h<=0f||colors==null||colors.length<2)return;
            railRect.set(0,0,w,h);
            railPaint.setShader(new LinearGradient(0,0,w,0,colors,null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(railRect,h/2f,h/2f,railPaint);
            railPaint.setShader(null);
            float x=Math.max(h/2f,Math.min(w-h/2f,fraction*w));
            float radius=Math.max(dp(7),h*0.40f);
            thumbPaint.setStyle(Paint.Style.FILL);
            thumbPaint.setColor(0x66000000);canvas.drawCircle(x,h/2f,radius+dp(1),thumbPaint);
            thumbPaint.setColor(Color.WHITE);canvas.drawCircle(x,h/2f,radius,thumbPaint);
        }
    }

    private void saveConfigQuiet(){
        try{
            settingsStore.save(config);
            SessionRuntime rt=activeRuntime;
            if(rt!=null){
                if(rt.nextConfig==null)rt.nextConfig=config.copy();
                else{
                    String stagedPrompt=rt.nextConfig.customSystemPrompt;
                    rt.nextConfig=config.copy();
                    if(stagedPrompt!=null)rt.nextConfig.customSystemPrompt=stagedPrompt;
                }
                if(!rt.engine.isBusy())rt.engine.configure(rt.nextConfig.copy());
            }else if(engine!=null&&!engine.isBusy())engine.configure(config.copy());
            refreshChrome();
        }catch(Exception e){toast("保存失败："+e.getMessage());}
    }

    private void updateComposerChips() {
        boolean locked=isPermissionModeLocked();
        if (composerModeChip != null) {
            setAnimatedText(composerModeChip,permissionLabel(),false);
            composerModeChip.setEnabled(!locked);composerModeChip.setAlpha(locked?.58f:1f);
            composerModeChip.setContentDescription(locked?"权限模式本轮已锁定："+permissionLabelFor(activePermissionMode()):"选择权限模式");
        }
        if (composerEffortChip != null) {
            setAnimatedText(composerEffortChip,"推理：" + effortLabel(config.effort),false);
            composerEffortChip.setEnabled(true);composerEffortChip.setAlpha(1f);
            composerEffortChip.setContentDescription(isPermissionModeLocked()?"调整推理强度；从下一次模型请求生效":"调整推理强度");
        }
        if (composerModelChip != null) setAnimatedText(composerModelChip,shorten(currentProfileName()+" · "+config.model, wide ? 26 : 16),false);
    }

    private static int parseTokenCount(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.US).replace(",", "").replace("_", "");
        if (s.isEmpty()) throw new IllegalArgumentException("Empty token count");
        double multiplier = 1d;
        if (s.endsWith("k")) { multiplier = 1_000d; s = s.substring(0,s.length()-1); }
        else if (s.endsWith("m")) { multiplier = 1_000_000d; s = s.substring(0,s.length()-1); }
        double value = Double.parseDouble(s) * multiplier;
        if (!Double.isFinite(value) || value < 16_000d || value > 2_000_000d) throw new IllegalArgumentException("Context must be 16k–2m");
        return (int)Math.round(value);
    }

    private static String formatTokenCount(long value) {
        if (value >= 1_000_000L) { double m=value/1_000_000d; return Math.abs(m-Math.rint(m))<0.0001 ? String.format(Locale.US,"%.0fm",m) : String.format(Locale.US,"%.1fm",m); }
        if (value >= 1_000L) { double k=value/1_000d; return Math.abs(k-Math.rint(k))<0.0001 ? String.format(Locale.US,"%.0fk",k) : String.format(Locale.US,"%.1fk",k); }
        return Long.toString(value);
    }

    private boolean isPermissionModeLocked(){
        SessionRuntime rt=activeRuntime;
        return rt!=null&&(rt.busy||rt.engine.isBusy());
    }

    private String userPermissionMode(){
        SessionRuntime rt=activeRuntime;
        return rt==null?config.permissionMode:rt.engine.getUserPermissionMode();
    }

    private String activePermissionMode(){
        SessionRuntime rt=activeRuntime;
        return rt==null?config.permissionMode:rt.engine.getEffectivePermissionMode();
    }

    private static String permissionLabelFor(String mode){
        if("acceptEdits".equals(mode))return "自动编辑"; if("plan".equals(mode))return "规划"; if("auto".equals(mode))return "自动"; if("dontAsk".equals(mode))return "不询问"; if("bypassPermissions".equals(mode))return "跳过权限"; return "每次询问";
    }

    private String permissionLabel(){
        String effective=activePermissionMode(),base=userPermissionMode();
        String label=permissionLabelFor(effective);
        if("plan".equals(effective)&&!"plan".equals(base))label+=" · 基础："+permissionLabelFor(base);
        return label+(isPermissionModeLocked()?" · 锁定":"");
    }

    private void refreshChrome() {
        if (statusText != null) setAnimatedText(statusText,runtime.isInstalled()?"已就绪":"运行环境未安装",false);
        if (tokenText != null) setAnimatedText(tokenText,(runtime.isInstalled()?"● ":"○ ")+shorten(config.model,22),false);
        updateContextChip(estimateContextTokensTimed(engine), false);
    }

    private void updateContextChip(long currentTokens, boolean animate) {
        if (contextChip == null) return;
        int window = Math.max(16_000, config.contextWindowTokens);
        int pct = Math.max(0, Math.min(999, (int)Math.round(currentTokens * 100.0 / window)));
        setAnimatedText(contextChip,formatTokenCount(currentTokens) + " / " + formatTokenCount(window),animate);
        contextChip.setTextColor(pct >= 90 ? RED : (pct >= 72 ? ACCENT : MUTED));
        contextChip.setBackground(round(pct >= 90 ? mixColor(SURFACE_2,RED,.12f) : Color.TRANSPARENT,8,Color.TRANSPARENT,0));
    }

    private boolean isActiveRuntime(SessionRuntime rt) { return rt != null && rt == activeRuntime; }

    private void touchRuntime(SessionRuntime rt, String phase, String detail, boolean busy) {
        if (rt == null) return;
        String nextPhase = phase == null ? rt.phase : phase;
        String nextDetail = detail == null ? "" : detail;
        boolean visibleStateChanged = !nextPhase.equals(rt.phase) || !nextDetail.equals(rt.detail) || busy != rt.busy;
        rt.phase = nextPhase;
        rt.detail = nextDetail;
        rt.busy = busy;
        rt.lastEventAt = System.currentTimeMillis();
        File f = rt.engine.getSessionFile(); if (f != null) { rt.file = f; registerRuntime(rt); }
        // Streaming text can arrive dozens of times per second. Refreshing the sidebar used to
        // reread and parse every JSONL session on every delta, blocking the Android main thread.
        if (visibleStateChanged) ui(this::refreshVisibleSessionRows);
    }

    private void handleSessionStarted(SessionRuntime rt, SessionConfig c){
        rt.boundProfileId=c.profileId;
        if(rt.pendingProfileId.isEmpty()){
            String selectedEffort=rt.nextConfig==null?null:rt.nextConfig.effort;
            String selectedPrompt=rt.nextConfig==null?null:rt.nextConfig.customSystemPrompt;
            rt.nextConfig=c.copy();
            if(selectedEffort!=null&&!selectedEffort.trim().isEmpty())rt.nextConfig.effort=selectedEffort;
            if(selectedPrompt!=null)rt.nextConfig.customSystemPrompt=selectedPrompt;
        }
        File sessionFile=rt.engine.getSessionFile();
        if(sessionFile!=null){rt.file=sessionFile;registerRuntime(rt);if(isActiveRuntime(rt))settingsStore.setLastSessionFile(sessionFile);}
        rt.liveAssistant.setLength(0);
        touchRuntime(rt,"thinking","正在思考…",true);
        if(!isActiveRuntime(rt))return;
        postRuntimeUi(rt,()->{if(streamingItem==null||streamingItem.body.length()>0){streamingItem=null;streamingView=null;streamingBodyHost=null;streamingVisibleChars=0;}agentSteps.clear();agentStepsByCall.clear();pushAgentSteps();showWorkingIndicator("正在思考…");ensureStreamingAssistantOnUi(rt);if(streamingItem!=null)recordAssistantProcessStep(streamingItem,"开始分析请求");setComposerBusy(true);updateComposerChips();if(tokenText!=null)setAnimatedText(tokenText,"● 工作中 · "+shorten(c.model,18),true);});
    }

    private void pushAgentSteps(){
        if(agentProgressView!=null)agentProgressView.setSteps(agentSteps);
    }

    /** 输入框上那条滚木的右半边：正在做的任务（activeForm），没有就显示待办数量。 */
    private void refreshWorkingBarTask(SessionRuntime rt){
        if(workingBarTask==null)return;
        TaskStore.Snapshot snapshot=rt==null?null:rt.taskSnapshot;
        String label="";int open=0;int any=0;String fallback="";
        if(snapshot!=null)for(org.json.JSONObject task:snapshot.getTasks()){
            String status=task.optString("status");
            if("deleted".equals(status))continue;
            any++;
            if("completed".equals(status))continue;
            open++;
            String form=task.optString("activeForm").trim();
            String subject=task.optString("subject").trim();
            if("in_progress".equals(status)){label=form.isEmpty()?subject:form;break;}
            if(fallback.isEmpty()&&!subject.isEmpty())fallback=subject;
        }
        if(label.isEmpty()&&open>0)label=fallback.isEmpty()?("待办 "+open+" 项"):fallback;
        if(label.length()>12)label=label.substring(0,11)+"…";
        if(label.isEmpty()){workingBarTask.setVisibility(View.GONE);workingBarTask.setText("");}
        else{workingBarTask.setVisibility(View.VISIBLE);workingBarTask.setText(label);}
        // 展开状态开关：有任务才显示箭头，让用户知道清单可以点开/收起。
        if(workingBarChevron!=null){
            if(any>0){workingBarChevron.setVisibility(View.VISIBLE);workingBarChevron.setText(workingTaskExpanded?"▾":"▸");}
            else workingBarChevron.setVisibility(View.GONE);
        }
    }

    /** 任务清单展开/收起：完整列出所有任务（最多 12 行），完成的加删除线。 */
    private void toggleWorkingTaskList(){
        if(workingTaskList==null)return;
        if(workingTaskExpanded){
            workingTaskExpanded=false;
            UiMotion.collapseBar(workingTaskList,null);
        }else{
            workingTaskExpanded=true;
            rebuildWorkingTaskList();
            UiMotion.expandBarToContent(workingTaskList,null);
        }
        if(workingBarChevron!=null&&workingBarChevron.getVisibility()==View.VISIBLE){
            workingBarChevron.setText(workingTaskExpanded?"▾":"▸");
            UiMotion.contentUpdated(workingBarChevron,false);
        }
    }

    /** 展开的清单里划掉的任务只停留几秒，到点自动重渲染移除。 */
    private final java.util.Map<String,Long> workingTaskDoneAt=new java.util.HashMap<>();
    private final Runnable workingTaskTtlTick=()->rebuildWorkingTaskList();

    private void rebuildWorkingTaskList(){
        if(workingTaskList==null)return;
        workingTaskList.removeAllViews();
        uiHandler.removeCallbacks(workingTaskTtlTick);
        TaskStore.Snapshot snapshot=activeRuntime==null?null:activeRuntime.taskSnapshot;
        long now=System.currentTimeMillis();long nextExpiry=0L;
        java.util.List<org.json.JSONObject> visible=new ArrayList<>();
        if(snapshot!=null){
            java.util.Set<String> stillOpen=new java.util.HashSet<>();
            for(org.json.JSONObject task:snapshot.getTasks()){
                String status=task.optString("status");
                if("deleted".equals(status))continue;
                String id=task.optString("id");
                if("completed".equals(status)){
                    stillOpen.add(id);workingTaskDoneAt.putIfAbsent(id,now);
                }else{stillOpen.add(id);workingTaskDoneAt.remove(id);}
            }
            workingTaskDoneAt.keySet().retainAll(stillOpen);
            for(org.json.JSONObject task:snapshot.getTasks()){
                String status=task.optString("status");
                if("deleted".equals(status))continue;
                if("completed".equals(status)){
                    Long at=workingTaskDoneAt.get(task.optString("id"));long doneAt=at==null?now:at;
                    if(now-doneAt>4000L)continue; // 划掉超时的自动消失
                    long expiry=doneAt+4000L;
                    if(expiry>now&&(nextExpiry==0L||expiry<nextExpiry))nextExpiry=expiry;
                }
                if("completed".equals(status)&&visible.size()>=8)continue; // 完成的太多时只带最近几条
                visible.add(task);
            }
        }
        if(visible.isEmpty()){workingTaskList.addView(text("暂无任务",11,MUTED),lp(-1,dp(24)));return;}
        int render=Math.min(visible.size(),12);
        for(int i=0;i<render;i++)workingTaskList.addView(workingTaskRow(visible.get(i),i==render-1),lp(-1,dp(24)));
        if(visible.size()>render){
            TextView more=text("…  另有 "+(visible.size()-render)+" 项",11,MUTED);
            workingTaskList.addView(more,lp(-1,dp(22)));
        }
        if(nextExpiry>0L)uiHandler.postDelayed(workingTaskTtlTick,Math.max(1L,nextExpiry-now));
    }

    private View workingTaskRow(org.json.JSONObject task,boolean last){
        String status=task.optString("status","pending");
        String form=task.optString("activeForm").trim();
        String subject=task.optString("subject").trim();
        String label="in_progress".equals(status)&&!form.isEmpty()?form:(subject.isEmpty()?"任务":subject);
        int color="completed".equals(status)?GREEN:"in_progress".equals(status)?ACCENT:TEXT;
        TextView row=text((last?"└─ ":"├─ ")+label,11,color);
        if("completed".equals(status)){
            android.text.SpannableString span=new android.text.SpannableString(row.getText());
            span.setSpan(new android.text.style.StrikethroughSpan(),0,span.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.setText(span);row.setAlpha(.72f);
        }
        return row;
    }

    private static boolean snapshotHasOpenTasks(TaskStore.Snapshot snapshot){
        if(snapshot==null)return false;
        for(org.json.JSONObject task:snapshot.getTasks()){
            String status=task.optString("status");
            if(!"completed".equals(status)&&!"deleted".equals(status))return true;
        }
        return false;
    }

    private void scheduleWorkingTaskListCollapse(){
        if(!workingTaskExpanded||workingTaskList==null||workingTaskCollapsePosted)return;
        if(activeRuntime!=null&&snapshotHasOpenTasks(activeRuntime.taskSnapshot))return;
        workingTaskCollapsePosted=true;
        uiHandler.postDelayed(workingTaskCollapse,4000L);
    }

    private void completeAgentStep(ToolCall call){
        if(call!=null&&call.id!=null&&!call.id.isEmpty()){
            AgentProgressView.Step step=agentStepsByCall.remove(call.id);
            if(step!=null)step.done=true;
        }
        pushAgentSteps();
    }

    /** 从工具入参里抽一个简短说明，让步骤列表可读：Bash · gradlew assembleRelease 之类。 */
    private static String stepLabel(ToolCall call){
        String name=call==null||call.name==null||call.name.isEmpty()?"工具":call.name;
        if(call!=null&&call.input!=null){
            org.json.JSONObject in=call.input;
            String detail="";
            if(in.has("command"))detail=in.optString("command");
            else if(in.has("file_path"))detail=in.optString("file_path");
            else if(in.has("notebook_path"))detail=in.optString("notebook_path");
            else if(in.has("subject"))detail=in.optString("subject");
            else if(in.has("query"))detail=in.optString("query");
            else if(in.has("url"))detail=in.optString("url");
            else if(in.has("pattern"))detail=in.optString("pattern");
            detail=detail==null?"":detail.trim().replace('\n',' ');
            if(!detail.isEmpty()){
                if(detail.contains("/"))detail=detail.substring(detail.lastIndexOf('/')+1);
                if(detail.length()>22)detail=detail.substring(0,21)+"…";
                return name+" · "+detail;
            }
        }
        return name;
    }

    private void handleTextDelta(SessionRuntime rt,String delta){
        if(delta==null||delta.isEmpty())return;
        rt.liveAssistant.append(delta);
        touchRuntime(rt,"thinking",rt.detail,true);
        if(!isActiveRuntime(rt))return;
        queueStreamingDelta(rt,delta);
    }

    /** Coalesce provider deltas at a short frame-friendly cadence so text follows the API without UI bursts. */
    private void queueStreamingDelta(SessionRuntime rt,String delta){
        synchronized(streamingDeltaLock){
            if(pendingStreamingRuntime!=rt||pendingStreamingGeneration!=activeRuntimeGeneration){
                pendingStreamingDelta.setLength(0);
                pendingStreamingRuntime=rt;
                pendingStreamingGeneration=activeRuntimeGeneration;
            }
            pendingStreamingDelta.append(delta);
            if(streamingDeltaFlushPosted)return;
            streamingDeltaFlushPosted=true;
        }
        uiHandler.postDelayed(streamingDeltaFlushRunnable,32L);
    }

    private void flushStreamingDeltas(){
        SessionRuntime rt;
        String chunk;
        long generation;
        synchronized(streamingDeltaLock){
            streamingDeltaFlushPosted=false;
            rt=pendingStreamingRuntime;
            generation=pendingStreamingGeneration;
            chunk=pendingStreamingDelta.toString();
            pendingStreamingDelta.setLength(0);
        }
        if(rt==null||chunk.isEmpty()||!isActiveRuntime(rt)||generation!=activeRuntimeGeneration)return;
        appendStreamingChunkOnUi(chunk);
    }

    /** Must run on the UI thread. Used before tool/completion events so delayed deltas never reorder. */
    private void flushStreamingDeltasNow(SessionRuntime rt){
        String chunk="";
        synchronized(streamingDeltaLock){
            if(pendingStreamingRuntime==rt&&pendingStreamingGeneration==activeRuntimeGeneration&&pendingStreamingDelta.length()>0){
                chunk=pendingStreamingDelta.toString();
                pendingStreamingDelta.setLength(0);
            }
            streamingDeltaFlushPosted=false;
        }
        uiHandler.removeCallbacks(streamingDeltaFlushRunnable);
        if(!chunk.isEmpty()&&isActiveRuntime(rt))appendStreamingChunkOnUi(chunk);
    }

    private void discardStreamingDeltas(SessionRuntime rt){
        synchronized(streamingDeltaLock){
            if(pendingStreamingRuntime==rt){
                pendingStreamingDelta.setLength(0);
                pendingStreamingRuntime=null;
                pendingStreamingGeneration=0L;
                streamingDeltaFlushPosted=false;
            }
        }
        uiHandler.removeCallbacks(streamingDeltaFlushRunnable);
    }

    private void ensureStreamingAssistantOnUi(SessionRuntime rt){
        if(streamingItem!=null)return;
        if(agentProgressView==null){agentProgressView=new AgentProgressView(this);agentProgressView.setPalette(TEXT,MUTED,ACCENT,GREEN,SURFACE_3);}
        streamingItem=new ChatItem(ChatItem.ASSISTANT,"IQ","");streamingVisibleChars=0;transcript.add(streamingItem);
        if(chatMessages!=null)addChatView(streamingItem);
    }

    private void appendStreamingChunkOnUi(String chunk){
        showWorkingIndicator("正在回复…");
        ensureStreamingAssistantOnUi(activeRuntime);
        streamingItem.body.append(chunk);
        scheduleStreamingRender();
    }

    private void handleToolBatchStarted(SessionRuntime rt,IQCodeEngine.ToolBatch batch){
        if(batch==null||!isActiveRuntime(rt))return;
        postRuntimeUi(rt,()->registerCollapsedToolBatch(batch.batchId,toolActivityEntries(batch)));
    }

    private void handleToolBatchCompleted(SessionRuntime rt,IQCodeEngine.ToolBatch batch){
        if(batch==null||!isActiveRuntime(rt))return;
        postRuntimeUi(rt,()->{
            List<CollapsedToolActivity> activities=collapsedToolsByBatch.get(batch.batchId);
            if(activities==null)return;
            for(CollapsedToolActivity activity:activities){
                for(ChatItem item:activity.members)if(!item.completed){
                    item.completed=true;item.resultError=true;item.runningView=null;
                    item.result.append("工具在返回结果前结束。");
                }
                activity.batchCompleted=true;refreshCollapsedToolActivity(activity);
            }
        });
    }

    private List<ToolActivityGrouper.Entry> toolActivityEntries(IQCodeEngine.ToolBatch batch){
        ArrayList<ToolActivityGrouper.Entry> entries=new ArrayList<>();
        for(ToolCall call:batch.toolCalls){
            if(call!=null)entries.add(toolActivityEntry(call.id,call.name,call.input,batch.breakBeforeToolIds.contains(call.id)));
        }
        return entries;
    }

    private ToolActivityGrouper.Entry toolActivityEntry(String toolId,String name,JSONObject input,boolean boundaryBefore){
        String toolName=name==null?"":name;
        int readRequests="Read".equals(toolName)?1:("ReadMany".equals(toolName)?readManyCount(input):0);
        return new ToolActivityGrouper.Entry(toolId,toolName,readRequests,toolActivityHint(toolName,input),boundaryBefore);
    }

    private static int readManyCount(JSONObject input){
        if(input==null)return 0;
        JSONArray paths=input.optJSONArray("paths");
        return paths==null?0:paths.length();
    }

    private String toolActivityHint(String name,JSONObject input){
        if(input==null)return "";
        String value="";
        if("Read".equals(name))value=input.optString("file_path","");
        else if("ReadMany".equals(name)){
            JSONArray paths=input.optJSONArray("paths");
            if(paths!=null&&paths.length()>0)value=paths.optString(0,"")+(paths.length()>1?" · +"+(paths.length()-1):"");
        } else if("Grep".equals(name)||"Glob".equals(name)){
            value=input.optString("pattern","");
            String path=input.optString("path","");
            if(!path.isEmpty())value=value.isEmpty()?path:value+" · "+path;
        } else if("LS".equals(name)||"Tree".equals(name)||"Stat".equals(name))value=input.optString("path",input.optString("file_path",""));
        return shortenMiddle(value,72);
    }

    private void registerCollapsedToolBatch(long batchId,List<ToolActivityGrouper.Entry> entries){
        List<ToolActivityGrouper.GroupPlan> plans=ToolActivityGrouper.group(entries);
        if(plans.isEmpty())return;
        ArrayList<CollapsedToolActivity> activities=new ArrayList<>();
        for(ToolActivityGrouper.GroupPlan plan:plans){
            CollapsedToolActivity activity=new CollapsedToolActivity(batchId,plan);
            activities.add(activity);
            for(String toolId:plan.toolIds)collapsedToolsById.put(toolId,activity);
        }
        collapsedToolsByBatch.put(batchId,activities);
    }

    private ChatItem newToolItem(ToolCall call){
        ChatItem tool=new ChatItem(ChatItem.TOOL,call==null?"工具":call.name,call==null||call.input==null?"{}":call.input.toString());
        tool.toolId=call==null||call.id==null?"":call.id;
        if(!tool.toolId.isEmpty())liveToolItems.put(tool.toolId,tool);
        return tool;
    }

    private CollapsedToolActivity collapsedToolActivity(ChatItem item){
        return item==null||item.toolId==null||item.toolId.isEmpty()?null:collapsedToolsById.get(item.toolId);
    }

    private void attachToolToCollapsedActivity(ChatItem item){
        CollapsedToolActivity activity=collapsedToolActivity(item);
        if(activity!=null&&!activity.members.contains(item))activity.members.add(item);
    }

    private void clearCollapsedToolActivities(){
        clearCollapsedToolActivityViews();
        collapsedToolsById.clear();
        collapsedToolsByBatch.clear();
        nextRestoredToolBatchId=-1L;
    }

    private void clearCollapsedToolActivityViews(){
        for(List<CollapsedToolActivity> activities:collapsedToolsByBatch.values())for(CollapsedToolActivity activity:activities){
            activity.renderedView=null;activity.childrenHost=null;
            for(ChatItem item:activity.members){item.renderedView=null;item.runningView=null;}
        }
    }

    private void handleToolUse(SessionRuntime rt,ToolCall call){
        // The assistant turn has been persisted before tool execution begins, so a
        // background switch can reload it from JSONL without duplicating live text.
        rt.liveAssistant.setLength(0);
        String name=call==null?"工具":call.name;
        touchRuntime(rt,"tool","正在执行 "+name+"…",true);
        if(!isActiveRuntime(rt))return;
        if(isWorkflowTool(name)){postRuntimeUi(rt,()->{flushStreamingDeltasNow(rt);finalizeStreamingMessage();showWorkingIndicator("正在执行 "+name+"…");ensureStreamingAssistantOnUi(rt);if(streamingItem!=null)recordAssistantProcessStep(streamingItem,"调用工具："+name);});return;}
        final ChatItem tool=newToolItem(call);
        postRuntimeUi(rt,()->{
            flushStreamingDeltasNow(rt);
            finalizeStreamingMessage();
            hideWorkingIndicator();
            boolean follow=chatAutoFollow;
            if(!transcript.contains(tool)) transcript.add(tool);
            attachToolToCollapsedActivity(tool);
            if(wide||currentView==VIEW_CHAT){
                CollapsedToolActivity activity=collapsedToolActivity(tool);
                if(activity!=null){
                    if(activity.renderedView==null)addCollapsedToolActivity(activity,conversationHost());
                    else refreshCollapsedToolActivity(activity);
                }else if(chatMessages!=null)addChatView(tool);
                if(follow)scrollChatSoft();
            }
            showWorkingIndicator("正在执行 "+name+"…");
            AgentProgressView.Step step=null;
            if(call!=null&&call.id!=null&&!call.id.isEmpty()){
                step=agentStepsByCall.get(call.id);
                if(step==null){step=new AgentProgressView.Step(stepLabel(call));agentStepsByCall.put(call.id,step);agentSteps.add(step);}
            }else{step=new AgentProgressView.Step(stepLabel(call));agentSteps.add(step);}
            pushAgentSteps();
            ensureStreamingAssistantOnUi(rt);if(streamingItem!=null)recordAssistantProcessStep(streamingItem,"调用工具："+name);
        });
    }

    private void handleToolProgress(SessionRuntime rt, ToolCall call, String chunk, boolean stderr, long elapsedMs) {
        if (call == null) return;
        ChatItem tool = call.id == null ? null : liveToolItems.get(call.id);
        if (tool == null) return;
        synchronized (tool) {
            tool.elapsedMs = Math.max(tool.elapsedMs, elapsedMs);
            if (chunk != null && !chunk.isEmpty()) appendLiveChunk(tool, chunk, stderr);
            if (tool.progressRenderPosted) return;
            tool.progressRenderPosted = true;
        }
        final long generation=activeRuntimeGeneration;
        uiHandler.postDelayed(() -> {
            synchronized (tool) { tool.progressRenderPosted = false; }
            if (tool.completed || !isActiveRuntime(rt) || generation!=activeRuntimeGeneration) return;
            String live=liveOutputPreview(tool,7000,wide?10:7);
            if(tool.liveOutputView!=null&&tool.liveOutputView.getParent()!=null)tool.liveOutputView.setText(live);
            else refreshToolItem(tool);
        }, 300);
    }

    private void handleToolResult(SessionRuntime rt,ToolCall call,ToolExecutionResult result){
        touchRuntime(rt,"thinking","正在继续处理…",true);
        if(!isActiveRuntime(rt)){ if(call!=null&&call.id!=null) liveToolItems.remove(call.id); return; }
        if(call!=null&&isWorkflowTool(call.name)){if(call.id!=null)liveToolItems.remove(call.id);postRuntimeUi(rt,()->{ChatItem stale=findPendingTool(call);if(stale!=null){stale.elapsedMs=Math.max(stale.elapsedMs,currentToolElapsed(stale));stale.completed=true;stale.resultError=false;stale.runningView=null;refreshToolItem(stale);}showWorkingIndicator("正在继续处理…");});return;}
        postRuntimeUi(rt,()->{
            boolean follow=chatAutoFollow;
            ChatItem tool=call!=null&&call.id!=null&&!call.id.isEmpty()?liveToolItems.get(call.id):findPendingTool(call);
            if(tool==null&&call!=null&&call.id!=null&&!call.id.isEmpty())tool=findPendingTool(call);
            if(call!=null&&call.id!=null) liveToolItems.remove(call.id);
            if(tool==null&&call!=null&&call.id!=null&&!call.id.isEmpty()&&collapsedToolsById.containsKey(call.id)){
                tool=newToolItem(call);
                liveToolItems.remove(call.id);
                transcript.add(tool);
                attachToolToCollapsedActivity(tool);
            }
            if(tool==null){ChatItem legacy=new ChatItem(ChatItem.RESULT,call==null?"工具":call.name,result.content);transcript.add(legacy);if(chatMessages!=null)addChatView(legacy);}
            else{
                tool.result.setLength(0); tool.result.append(result.content==null?"":result.content);
                tool.diff.setLength(0); tool.diff.append(result.diff==null?"":result.diff);
                tool.diffAddedLines=result.addedLines; tool.diffDeletedLines=result.deletedLines;
                tool.elapsedMs=Math.max(tool.elapsedMs,currentToolElapsed(tool));
                tool.completed=true; tool.runningView=null; tool.resultError=result.isError; tool.exitCode=result.exitCode;
                if (result.isError && "Bash".equalsIgnoreCase(tool.title) && isPackageManagerTool(tool)) tool.expanded = true;
                if(!transcript.contains(tool)){transcript.add(tool);attachToolToCollapsedActivity(tool);if(chatMessages!=null)addChatView(tool);}
                refreshToolItem(tool);
            }
            showWorkingIndicator("正在继续处理…");
            completeAgentStep(call);
            ensureStreamingAssistantOnUi(rt);if(streamingItem!=null)recordAssistantProcessStep(streamingItem,"工具完成："+(call==null?"工具":call.name));
            if(follow)scrollChatSoft();
        });
    }

    private static boolean isWorkflowTool(String name){return "TaskCreate".equals(name)||"TaskGet".equals(name)||"TaskList".equals(name)||"TaskUpdate".equals(name)||"TodoWrite".equals(name)||"EnterPlanMode".equals(name)||"ExitPlanMode".equals(name);}

    private ChatItem findPendingTool(ToolCall call){
        if (call != null && call.id != null && !call.id.isEmpty()) {
            for(int i=transcript.size()-1;i>=0;i--){
                ChatItem x=transcript.get(i);
                if(x.type==ChatItem.TOOL&&!x.completed&&call.id.equals(x.toolId)) return x;
            }
            return null;
        }
        String name = call == null ? null : call.name;
        for(int i=transcript.size()-1;i>=0;i--){
            ChatItem x=transcript.get(i);
            if(x.type==ChatItem.TOOL&&!x.completed&&(name==null||name.equalsIgnoreCase(x.title))) return x;
        }
        return null;
    }

    private void handleQueuedPromptApplied(SessionRuntime rt){if(!isActiveRuntime(rt))return;postRuntimeUi(rt,()->{flushStreamingDeltasNow(rt);finalizeStreamingMessage();showWorkingIndicator("预输入已加载，正在继续…");});}

    private void handleResponseRetry(SessionRuntime rt){
        rt.liveAssistant.setLength(0);
        if(!isActiveRuntime(rt))return;
        postRuntimeUi(rt,()->{
            discardStreamingDeltas(rt);
            if(streamingItem!=null)transcript.remove(streamingItem);
            streamingItem=null;streamingView=null;streamingBodyHost=null;streamingVisibleChars=0;
            if(chatMessages!=null)rebuildTranscriptViews();
            showWorkingIndicator("连接中断，正在重试模型请求…");
            setComposerBusy(true);
        });
    }

    private void handleResponseInterruptedBySteering(SessionRuntime rt) {
        rt.liveAssistant.setLength(0);
        touchRuntime(rt,"thinking","旧响应已打断，正在按最新纠正重新规划…",true);
        if(!isActiveRuntime(rt))return;
        postRuntimeUi(rt,() -> {
            discardStreamingDeltas(rt);
            finalizeStreamingMessage();
            streamingItem=null; streamingView=null; streamingBodyHost=null; streamingVisibleChars=0;
            showWorkingIndicator("旧响应已打断，正在按最新纠正重新规划…");
            setComposerBusy(true);
        });
    }

    private void handleProjectDirectoryChanged(SessionRuntime rt,String projectDirectory) {
        if(!isActiveRuntime(rt))return;
        postRuntimeUi(rt,() -> {
            if(projectDirectory==null||projectDirectory.trim().isEmpty())return;
            config.projectDirectory=projectDirectory; try{settingsStore.save(config);}catch(Exception ignored){} browserDir=new File(projectDirectory);
            refreshChrome(); if(filesWorkspaceHost!=null&&currentView==VIEW_FILES)showFileBrowser();
            if(terminalPane!=null) terminalPane.setNextSessionWorkingDirectory(projectDirectory);
        });
    }

    private void handlePlanStateChanged(SessionRuntime rt,PlanWorkflowState state){
        rt.planState=state==null?PlanWorkflowState.idle():state.copy();
        if(!isActiveRuntime(rt))return;
        postRuntimeUi(rt,()->{updateComposerChips();if(agentProgressView!=null){agentProgressView.update(runtimeDisplayStatus(rt),rt.taskSnapshot,rt.planState,rt.busy);ensureStreamingAssistantOnUi(rt);if(chatAutoFollow)scrollChatSoft();}});
    }

    private void handleTasksChanged(SessionRuntime rt,TaskStore.Snapshot snapshot){
        if(snapshot==null)return;TaskStore.Snapshot previous=rt.taskSnapshot;if(previous!=null&&snapshot.version<previous.version)return;rt.taskSnapshot=snapshot;
        if(!isActiveRuntime(rt))return;postRuntimeUi(rt,()->{if(agentProgressView!=null)agentProgressView.update(runtimeDisplayStatus(rt),snapshot,rt.planState,rt.busy);ensureStreamingAssistantOnUi(rt);if(agentProgressView!=null&&chatAutoFollow)scrollChatSoft();refreshWorkingBarTask(rt);
            if(!snapshotHasOpenTasks(snapshot)&&!(rt.busy||rt.engine.isBusy())){
                // 任务全划掉且回合已结束：兜底收走滚木（补齐个别丢失的结束事件）。
                hideWorkingIndicator();
            }else if(workingTaskExpanded){
                // 任务全划掉后，展开的清单不能一直卡着：停几秒自动收起；来了新任务则取消收起。
                if(snapshotHasOpenTasks(snapshot)){uiHandler.removeCallbacks(workingTaskCollapse);workingTaskCollapsePosted=false;}
                else scheduleWorkingTaskListCollapse();
                rebuildWorkingTaskList();
            }});
    }

    private void handlePlanApprovalRequest(SessionRuntime rt,PlanApprovalGate.ApprovalRequest request){
        ui(()->showPlanApprovalDialog(rt,request));
    }

    private void showPlanApprovalDialog(SessionRuntime rt,PlanApprovalGate.ApprovalRequest request){
        if(request==null)return;final boolean[] resolved={false};final Dialog d=newOverlayAwareDialog();d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setCanceledOnTouchOutside(false);
        LinearLayout panel=dialogPanel();
        panel.addView(dialogTitle("实现计划 · revision "+request.plan.revision),lp(-1,dp(48)));
        TextView path=text(request.plan.planFile,11.5f,MUTED_2);path.setTypeface(Typeface.MONOSPACE);path.setPadding(0,0,0,dp(6));panel.addView(path,lp(-1,-2));
        ScrollView planScroll=new ScrollView(this);LinearLayout rendered=MarkdownRenderer.render(this,request.plan.planText,12.5f,neonTheme,lightTheme);planScroll.addView(rendered,new ScrollView.LayoutParams(-1,-2));panel.addView(planScroll,new LinearLayout.LayoutParams(-1,0,1));
        EditText feedback=input("",false);feedback.setHint("继续规划时填写反馈（可选）");LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52));fp.setMargins(0,dp(8),0,0);panel.addView(feedback,fp);
        TextView policy=text("批准只确认计划；后续操作仍按你的权限模式："+permissionLabelFor(userPermissionMode()),11.5f,ACCENT);policy.setPadding(dp(2),dp(8),dp(2),dp(2));panel.addView(policy,lp(-1,-2));
        LinearLayout footer=hbox();footer.setGravity(Gravity.CENTER_VERTICAL);TextView revise=dialogButton("继续规划",MUTED,false);revise.setOnClickListener(v->{resolved[0]=true;d.dismiss();rt.engine.respondPlanApproval(request.requestId,PlanApprovalGate.Decision.KEEP_PLANNING,feedback.getText().toString().trim());});footer.addView(revise,dialogActionParams());
        TextView approve=dialogButton("批准并开始",TEXT,true);approve.setOnClickListener(v->{resolved[0]=true;d.dismiss();rt.engine.respondPlanApproval(request.requestId,PlanApprovalGate.Decision.APPROVE,"");});footer.addView(approve,dialogActionParams());panel.addView(footer,lp(-1,dp(62)));
        d.setOnCancelListener(x->{if(!resolved[0]){resolved[0]=true;rt.engine.respondPlanApproval(request.requestId,PlanApprovalGate.Decision.KEEP_PLANNING,feedback.getText().toString().trim());}});d.setContentView(panel);d.show();styleFormDialog(d,dialogWidthPx(560),Math.min(dp(720),(int)(getResources().getDisplayMetrics().heightPixels*.88f)));
    }

    private void handleQuestionRequest(SessionRuntime rt,QuestionGate.QuestionRequest request) {
        ui(() -> showQuestionStep(rt.engine, request, 0, new JSONObject()));
    }

    private void showQuestionStep(IQCodeEngine targetEngine, QuestionGate.QuestionRequest request, int index, JSONObject answers) {
        if (request == null) return;
        if (index >= request.questions.length()) { targetEngine.respondQuestion(request.requestId, answers); return; }
        JSONObject q=request.questions.optJSONObject(index); if(q==null){showQuestionStep(targetEngine,request,index+1,answers);return;}
        final Dialog d=newOverlayAwareDialog(); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=dialogPanel();
        int width=dialogWidthPx(520); LinearLayout content=vbox();
        String header=q.optString("header","问题"); TextView h=text(header,12.5f,ACCENT); h.setTypeface(Typeface.DEFAULT_BOLD); content.addView(h,lp(-1,dp(34)));
        String question=q.optString("question","IQ 应该怎么做？"); TextView qt=text(question,14.5f,TEXT); qt.setLineSpacing(0,1.12f); content.addView(qt,lp(-1,-2));
        JSONArray options=q.optJSONArray("options"); boolean multi=q.optBoolean("multiSelect",false); java.util.LinkedHashSet<String> selected=new java.util.LinkedHashSet<>();
        LinearLayout optionHost=vbox(); LinearLayout.LayoutParams oh=new LinearLayout.LayoutParams(-1,-2);oh.setMargins(0,dp(10),0,0);content.addView(optionHost,oh);
        if(options!=null)for(int i=0;i<options.length();i++){
            JSONObject o=options.optJSONObject(i); if(o==null)continue; String label=o.optString("label",""); String desc=o.optString("description","");
            TextView row=text((multi?"○  ":"")+label+(desc.isEmpty()?"":"\n    "+desc),12.5f,TEXT);row.setMinHeight(dp(52));row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackground(round(SURFACE_3,14,Color.TRANSPARENT,0));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,dp(8));optionHost.addView(row,rp);
            row.setOnClickListener(v->{
                if(multi){if(selected.contains(label)){selected.remove(label);setAnimatedText(row,"○  "+label+(desc.isEmpty()?"":"\n    "+desc),true);}else{selected.add(label);setAnimatedText(row,"●  "+label+(desc.isEmpty()?"":"\n    "+desc),true);}}
                else {try{answers.put(question,label);}catch(Exception ignored){}d.dismiss();showQuestionStep(targetEngine,request,index+1,answers);}
            });
        }
        EditText other=input("",false); other.setHint("其他回答…"); LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(52));op.setMargins(0,dp(8),0,0);content.addView(other,op);
        panel.addView(scrollBody(content,width,dialogMaxBodyPx()));
        LinearLayout footer=hbox();footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(10),0,0);
        TextView cancel=dialogButton("取消",MUTED,false);cancel.setOnClickListener(v->{d.dismiss();targetEngine.respondQuestion(request.requestId,new JSONObject());});footer.addView(cancel,dialogActionParams());
        TextView next=dialogButton(index+1>=request.questions.length()?"提交":"下一步",TEXT,true);next.setOnClickListener(v->{
            try{String custom=other.getText().toString().trim();if(!custom.isEmpty())answers.put(question,custom);else if(multi)answers.put(question,new JSONArray(selected));else if(options==null||options.length()==0){toast("请输入回答");return;}else{toast("请选择一个选项");return;}}catch(Exception ignored){}
            d.dismiss();showQuestionStep(targetEngine,request,index+1,answers);
        });footer.addView(next,dialogActionParams());panel.addView(footer,lp(-1,dp(62)));
        d.setOnCancelListener(x->targetEngine.respondQuestion(request.requestId,new JSONObject()));d.setCanceledOnTouchOutside(false);d.setContentView(panel);d.show();styleFormDialog(d,width);
    }

    private void handlePermissionRequest(SessionRuntime rt,PermissionGate.PermissionRequest r){
        ui(() -> {
            ChatItem pending = findPendingTool(r.call);
            if (pending != null) {
                pending.awaitingPermission = true;
                refreshToolItem(pending);
            }
            showPermissionDialog(rt, r, pending);
        });
    }

    /**
     * Custom 元境 permission surface.
     *
     * Do not use AlertDialog here: the stock Material dialog is visually disconnected
     * from the rest of the workspace and dumps raw JSON at the user.  This panel keeps
     * the command readable, exposes the important metadata, and keeps the tool row in
     * an explicit "等待授权" state until the user decides.
     */
    private void showPermissionDialog(SessionRuntime rt, PermissionGate.PermissionRequest r, ChatItem pending) {
        final Dialog d = newOverlayAwareDialog();
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final boolean[] resolved = { false };

        final JSONObject input = r.call == null || r.call.input == null ? new JSONObject() : r.call.input;
        final String tool = r.call == null || r.call.name == null ? "操作" : r.call.name;
        final boolean rootCommand="Root".equalsIgnoreCase(tool);
        final boolean shizukuCommand="Shizuku".equalsIgnoreCase(tool);
        final boolean bash = "Bash".equalsIgnoreCase(tool)||rootCommand||shizukuCommand;
        final String command = bash ? input.optString("command", "").trim() : "";
        final String cwd = input.optString("cwd", input.optString("working_directory", config.projectDirectory == null ? "" : config.projectDirectory));
        final long timeout = input.optLong("timeout_ms", 10000L);

        LinearLayout panel = vbox();
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(round(SURFACE_2,20, BORDER, 1));

        // Header
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView glyph = text(permissionGlyph(tool),16, rootCommand?RED:ACCENT);
        glyph.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(round(SURFACE_3,14, rootCommand?RED:ACCENT, 1));
        head.addView(glyph, lp(dp(50), dp(50)));

        LinearLayout titles = vbox();
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.setMargins(dp(12), 0, 0, 0);
        TextView title = text("允许执行 " + userFacingToolName(tool, input) + "？",17, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title, lp(-1, dp(30)));
        TextView subtitle = text(permissionSubtitle(tool), 11.5f, MUTED);
        subtitle.setLineSpacing(0, 1.08f);
        titles.addView(subtitle, lp(-1, -2));
        head.addView(titles, tp);
        panel.addView(head, lp(-1, -2));

        LinearLayout details = vbox();
        ScrollView detailsScroll = new ScrollView(this);
        detailsScroll.setFillViewport(false);
        detailsScroll.setVerticalScrollBarEnabled(true);
        detailsScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        detailsScroll.addView(details, new ScrollView.LayoutParams(-1, -2));
        panel.addView(detailsScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!isActiveRuntime(rt)) {
            TextView background = text("后台会话正在请求权限",10.5f, ACCENT);
            background.setPadding(dp(8), 0, dp(8), 0);
            background.setGravity(Gravity.CENTER_VERTICAL);
            background.setBackground(round(SURFACE_3,10, Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(26));
            bp.setMargins(dp(62), dp(7), 0, 0);
            details.addView(background, bp);
        }

        // Main operation content. Bash gets a real code block instead of raw JSON.
        if (bash) {
            LinearLayout codeBox = vbox();
            codeBox.setPadding(dp(12), dp(10), dp(12), dp(10));
            codeBox.setBackground(round(TERMINAL_BG,14, BORDER, 1));

            LinearLayout codeHead = hbox();
            codeHead.setGravity(Gravity.CENTER_VERTICAL);
            TextView lang = text(rootCommand?"ROOT · UID 0":shizukuCommand?"SHIZUKU · SHELL":"BASH",11.5f, rootCommand?RED:MUTED_2);
            lang.setTypeface(Typeface.DEFAULT_BOLD);
            codeHead.addView(lang, new LinearLayout.LayoutParams(0, dp(36), 1));
            TextView copy = dialogButton("复制命令",MUTED,false);
            copy.setGravity(Gravity.CENTER);
            copy.setOnClickListener(v -> {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText(rootCommand?"元境 Root":"元境 Bash", command));
                    toast("命令已复制");
                } catch (Exception ignored) {}
            });
            codeHead.addView(copy, new LinearLayout.LayoutParams(dp(96), dp(36)));
            codeBox.addView(codeHead, lp(-1, dp(36)));

            TextView code = text(command.isEmpty() ? "(empty command)" : command,12.5f, TEXT);
            code.setTypeface(Typeface.MONOSPACE);
            code.setTextIsSelectable(true);
            code.setLineSpacing(dp(1), 1.08f);
            code.setMaxLines(9);
            codeBox.addView(code, lp(-1, -2));

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(0, dp(14), 0, 0);
            details.addView(codeBox, cp);

            // Metadata row: no raw JSON.
            LinearLayout meta = hbox();
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.setMargins(0, dp(12), 0, 0);

            LinearLayout cwdCol = vbox();
            TextView cwdLabel = text("工作目录",11.5f, MUTED_2);
            cwdCol.addView(cwdLabel, lp(-1, dp(20)));
            TextView cwdValue = text(cwd == null || cwd.trim().isEmpty() ? "当前项目目录" : shortenMiddle(cwd, 38),11.5f, TEXT);
            cwdValue.setTypeface(Typeface.MONOSPACE);
            cwdCol.addView(cwdValue, lp(-1, dp(24)));
            meta.addView(cwdCol, new LinearLayout.LayoutParams(0, dp(50), 1));

            View sep = dividerVertical();
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(1, dp(38));
            sp.setMargins(dp(10), dp(4), dp(12), 0);
            meta.addView(sep, sp);

            LinearLayout timeCol = vbox();
            TextView timeLabel = text("超时时间",11.5f, MUTED_2);
            timeCol.addView(timeLabel, lp(-1, dp(20)));
            TextView timeValue = text(formatTimeout(timeout),11.5f, TEXT);
            timeValue.setTypeface(Typeface.MONOSPACE);
            timeCol.addView(timeValue, lp(-1, dp(24)));
            meta.addView(timeCol, lp(dp(120), dp(50)));
            details.addView(meta, mp);
        } else {
            TextView inputText = text(prettyPermissionInput(tool, input),12, TEXT);
            inputText.setTypeface(Typeface.MONOSPACE);
            inputText.setTextIsSelectable(true);
            inputText.setLineSpacing(dp(1), 1.08f);
            inputText.setPadding(dp(12), dp(10), dp(12), dp(10));
            inputText.setBackground(round(TERMINAL_BG,14, BORDER, 1));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
            ip.setMargins(0, dp(14), 0, 0);
            details.addView(inputText, ip);
        }

        TextView mode = text("权限模式 · " + permissionModeLabel(r.mode),11.5f, MUTED_2);
        LinearLayout.LayoutParams modep = new LinearLayout.LayoutParams(-1, dp(34));
        modep.setMargins(0, dp(7), 0, 0);
        details.addView(mode, modep);

        // Actions.
        LinearLayout actions = hbox();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(48));
        ap.setMargins(0, dp(8), 0, 0);

        TextView deny = text("拒绝",13, TEXT);
        deny.setTypeface(Typeface.DEFAULT_BOLD);
        deny.setGravity(Gravity.CENTER);
        deny.setBackground(round(SURFACE_3,14, BORDER, 1));
        LinearLayout.LayoutParams denyP = new LinearLayout.LayoutParams(0, dp(48), 1);
        denyP.setMargins(0, 0, dp(7), 0);
        actions.addView(deny, denyP);

        TextView allow = text("允许",13, Color.WHITE);
        allow.setTypeface(Typeface.DEFAULT_BOLD);
        allow.setGravity(Gravity.CENTER);
        allow.setBackground(round(ACCENT,14, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams allowP = new LinearLayout.LayoutParams(0, dp(48), 1);
        allowP.setMargins(dp(7), 0, 0, 0);
        actions.addView(allow, allowP);
        panel.addView(actions, ap);

        View.OnClickListener resolveDeny = v -> {
            if (resolved[0]) return;
            resolved[0] = true;
            finishPermissionUi(pending);
            d.dismiss();
            rt.engine.respondPermission(r.requestId, false);
        };
        View.OnClickListener resolveAllow = v -> {
            if (resolved[0]) return;
            resolved[0] = true;
            finishPermissionUi(pending);
            d.dismiss();
            rt.engine.respondPermission(r.requestId, true);
        };
        deny.setOnClickListener(resolveDeny);
        allow.setOnClickListener(resolveAllow);
        d.setOnCancelListener(x -> {
            if (resolved[0]) return;
            resolved[0] = true;
            finishPermissionUi(pending);
            rt.engine.respondPermission(r.requestId, false);
        });

        d.setCanceledOnTouchOutside(false);
        d.setContentView(panel);
        d.show();
        stylePermissionDialogWindow(d, dialogWidthPx(560));
    }

    private void finishPermissionUi(ChatItem pending) {
        if (pending == null) return;
        pending.awaitingPermission = false;
        refreshToolItem(pending);
    }

    private String permissionGlyph(String tool) {
        if ("Root".equalsIgnoreCase(tool)) return "#";
        if ("Bash".equalsIgnoreCase(tool)) return ">_";
        if ("Shizuku".equalsIgnoreCase(tool)) return "$_";
        if ("AndroidIntent".equalsIgnoreCase(tool)) return "↗";
        if ("Write".equalsIgnoreCase(tool) || "Edit".equalsIgnoreCase(tool) || "MultiEdit".equalsIgnoreCase(tool)) return "✎";
        if ("Delete".equalsIgnoreCase(tool)) return "×";
        if ("Move".equalsIgnoreCase(tool)) return "→";
        return "!";
    }

    private String permissionSubtitle(String tool) {
        if ("Root".equalsIgnoreCase(tool)) return "高风险：元境 请求通过 Magisk/KernelSU 以 Android uid 0 执行系统命令";
        if ("Bash".equalsIgnoreCase(tool)) return "元境 请求在本地 Termux 环境中执行以下命令";
        if ("Shizuku".equalsIgnoreCase(tool)) return "元境 请求通过 Shizuku 以 shell 身份执行以下系统命令（不是 Root）";
        if ("AndroidIntent".equalsIgnoreCase(tool)) return "元境 请求通过 Android 应用进程打开手机 App、网页或系统页面";
        if ("Write".equalsIgnoreCase(tool) || "Edit".equalsIgnoreCase(tool) || "MultiEdit".equalsIgnoreCase(tool))
            return "元境 请求修改当前项目中的文件";
        if ("Delete".equalsIgnoreCase(tool)) return "元境 请求删除项目中的文件或目录";
        return "元境 请求执行一个需要确认的操作";
    }

    private String permissionModeLabel(String mode) {
        if ("acceptEdits".equals(mode)) return "自动允许编辑";
        if ("plan".equals(mode)) return "规划模式";
        if ("auto".equals(mode)) return "自动判断";
        if ("dontAsk".equals(mode)) return "不主动询问";
        if ("bypassPermissions".equals(mode)) return "跳过权限检查";
        return "每次询问";
    }

    private String prettyPermissionInput(String tool, JSONObject input) {
        if (input == null) return "";
        if ("AndroidIntent".equalsIgnoreCase(tool)) {
            StringBuilder out=new StringBuilder();
            String op=input.optString("operation","intent"); out.append("操作: ").append(op);
            String uri=input.optString("uri",input.optString("data","")); if(!uri.isEmpty())out.append("\n地址: ").append(uri);
            String pkg=input.optString("package",""); if(!pkg.isEmpty())out.append("\n应用包名: ").append(pkg); String app=input.optString("app_name",""); if(!app.isEmpty())out.append("\n应用名称: ").append(app);
            String action=input.optString("action",""); if(!action.isEmpty())out.append("\nAction: ").append(action);
            String component=input.optString("component",""); if(!component.isEmpty())out.append("\n组件: ").append(component);
            return out.toString();
        }
        String path = input.optString("file_path", input.optString("path", ""));
        if (!path.isEmpty()) {
            if ("Move".equalsIgnoreCase(tool)) {
                return input.optString("source", path) + "\n→ " + input.optString("destination", "");
            }
            return path;
        }
        try { return input.toString(2); } catch (Exception ignored) { return input.toString(); }
    }

    private String formatTimeout(long millis) {
        if (millis <= 0) return "默认";
        if (millis >= 60000 && millis % 60000 == 0) return (millis / 60000) + " 分钟";
        if (millis >= 1000 && millis % 1000 == 0) return (millis / 1000) + " 秒";
        return String.format(Locale.US, "%,d ms", millis);
    }

    private static String shortenMiddle(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        int left = Math.max(6, (max - 1) / 2);
        int right = Math.max(6, max - left - 1);
        return value.substring(0, left) + "…" + value.substring(value.length() - right);
    }

    private void stylePermissionDialogWindow(Dialog d, int width) {
        Window w = d == null ? null : d.getWindow();
        if (w == null) return;
        if(overlayShell!=null&&android.os.Build.VERSION.SDK_INT>=26)w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams a = w.getAttributes();
        a.dimAmount = .66f;
        w.setAttributes(a);
        w.setGravity(Gravity.CENTER);
        int height=Math.min(dp(720),(int)(getResources().getDisplayMetrics().heightPixels*.88f));
        w.setLayout(width, height);
        View decor = w.getDecorView();
        UiMotion.bindInteractive(decor);
        UiMotion.dialogIn(decor);
    }

    private void handleUsage(SessionRuntime rt,long in,long out){
        if(!isActiveRuntime(rt))return;
        final long current=Math.max(0L,in)+Math.max(0L,out);
        postRuntimeUi(rt,()->{
            long shown=current>0?current:estimateContextTokensTimed(rt.engine);
            int window=Math.max(16_000,config.contextWindowTokens);
            int used=Math.max(0,Math.min(100,(int)Math.round(shown*100.0/window)));
            if(tokenText!=null)setAnimatedText(tokenText,"上下文 "+formatTokenCount(shown)+" · 剩余 "+Math.max(0,100-used)+"%",true);
            updateContextChip(shown,true);
        });
    }

    private void handleStatus(SessionRuntime rt,String status){
        touchRuntime(rt, "tool".equals(rt.phase)?"tool":"thinking", status==null?"正在处理…":status, true);
        if(!isActiveRuntime(rt))return;
        postRuntimeUi(rt,()->{workingStatus=status==null?"正在处理…":status;showWorkingIndicator(runtimeDisplayStatus(rt));ensureStreamingAssistantOnUi(rt);if(streamingItem!=null)recordAssistantProcessStep(streamingItem,runtimeDisplayStatus(rt));if(tokenText!=null)setAnimatedText(tokenText,shorten(runtimeDisplayStatus(rt),22),false);});
    }

    private void handleThinkingDelta(SessionRuntime rt,String delta){
        if(delta==null||delta.isEmpty())return;
        boolean schedule;long generation;
        synchronized(rt.liveThinking){
            appendCollapsedWhitespace(rt.liveThinking,delta);
            if(rt.liveThinking.length()>1200)rt.liveThinking.delete(0,rt.liveThinking.length()-1200);
            schedule=!rt.reasoningRenderPosted;
            rt.reasoningRenderPosted=true;generation=rt.reasoningGeneration;
        }
        if(schedule){
            final long renderGeneration=generation;
            uiHandler.postDelayed(()->{
                String preview;
                synchronized(rt.liveThinking){
                    if(renderGeneration!=rt.reasoningGeneration)return;
                    preview=rt.liveThinking.toString();rt.reasoningRenderPosted=false;
                }
                if(!isActiveRuntime(rt)||!rt.busy)return;
                ensureStreamingAssistantOnUi(rt);if(streamingItem!=null){streamingItem.thinking.setLength(0);streamingItem.thinking.append(preview);refreshAssistantThinking(streamingItem);}if(agentProgressView!=null)agentProgressView.setReasoningPreview(preview);if(chatAutoFollow)scrollChatSoft();
            },64L);
        }
    }

    private static void appendCollapsedWhitespace(StringBuilder out,String value){
        boolean previousWhitespace=out.length()>0&&Character.isWhitespace(out.charAt(out.length()-1));
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            boolean whitespace=Character.isWhitespace(c);
            if(!whitespace)out.append(c);
            else if(!previousWhitespace)out.append(' ');
            previousWhitespace=whitespace;
        }
    }

    private void clearThinkingPreview(SessionRuntime rt){
        synchronized(rt.liveThinking){rt.liveThinking.setLength(0);rt.reasoningRenderPosted=false;rt.reasoningGeneration++;}
        postRuntimeUi(rt,()->{if(agentProgressView!=null)agentProgressView.setReasoningPreview("");});
    }

    private void handleTurnComplete(SessionRuntime rt,String reason){
        clearThinkingPreview(rt);
        touchRuntime(rt,"done","已完成",false);
        if(!isActiveRuntime(rt)){rt.liveAssistant.setLength(0);refreshVisibleSessionRowsFromDiskAsync();return;}
        postRuntimeUi(rt,()->{if(rt.busy)return;flushStreamingDeltasNow(rt);if(streamingItem!=null)recordAssistantProcessStep(streamingItem,"完成回复");hideWorkingIndicator();setComposerBusy(false);updateComposerChips();stampCurrentContext(streamingItem,rt.engine);finishStreamingWhenRevealed();rt.liveAssistant.setLength(0);refreshChrome();refreshVisibleSessionRows();});
        refreshVisibleSessionRowsFromDiskAsync();
    }

    private void handleError(SessionRuntime rt,String message,Throwable e){
        clearThinkingPreview(rt);
        touchRuntime(rt,"error",message==null?"出错":message,false);
        if(!isActiveRuntime(rt)){rt.liveAssistant.setLength(0);refreshVisibleSessionRowsFromDiskAsync();return;}
        postRuntimeUi(rt,()->{if(rt.busy)return;flushStreamingDeltasNow(rt);if(streamingItem!=null)recordAssistantProcessStep(streamingItem,"发生错误");hideWorkingIndicator();setComposerBusy(false);updateComposerChips();stampCurrentContext(streamingItem,rt.engine);finishStreamingWhenRevealed();rt.liveAssistant.setLength(0);ChatItem err=new ChatItem(ChatItem.ERROR,"错误",friendlyNetworkError(message,e));transcript.add(err);if(chatMessages!=null)addChatView(err);if(chatAutoFollow)scrollChatSoft();refreshChrome();refreshVisibleSessionRows();});
        refreshVisibleSessionRowsFromDiskAsync();
    }

    private void setComposerBusy(boolean busy) {
        composerBusyState=busy;
        if (sendButton == null) return;
        // Keep the send affordance stable while the Agent is working. A separate stop button
        // is shown beside it; tapping send queues a steering message for the current task.
        sendButton.setContentDescription(busy ? "发送消息；当前任务完成后处理" : "发送消息");
        if(stopButton!=null){stopButton.setVisibility(busy?View.VISIBLE:View.GONE);UiMotion.state(stopButton,busy?1f:.9f,busy?1f:.9f,150);}
        UiMotion.state(sendButton,busy ? .96f : 1f,busy ? .98f : 1f,150);
    }

    private void syncComposerForActiveRuntime(){
        SessionRuntime rt=activeRuntime;
        setComposerBusy(rt!=null&&(rt.busy||rt.engine.isBusy()));
        updateComposerChips();
    }

    private void postRuntimeUi(SessionRuntime rt,Runnable action){
        if(rt==null||action==null||!isActiveRuntime(rt))return;
        final long generation=activeRuntimeGeneration;
        ui(()->{if(!isActiveRuntime(rt)||generation!=activeRuntimeGeneration)return;action.run();});
    }

    private void showWorkingIndicator(String status) {
        workingStatus = status == null || status.trim().isEmpty() ? "工作中…" : status.trim();
        ui(()->{SessionRuntime rt=activeRuntime;if(rt!=null){ensureStreamingAssistantOnUi(rt);if(agentProgressView!=null){agentProgressView.update(workingStatus,rt.taskSnapshot,rt.planState,true);if(chatAutoFollow)scrollChatSoft();}}
            if(workingBar!=null){
                workingBarText.setText(workingStatus);
                refreshWorkingBarTask(rt);
                if(rollingLog!=null)rollingLog.setRolling(true);
                // 出现先走动画（高度+淡入），输入框被平滑顶上去，而不是硬跳。
                if(workingBar.getVisibility()!=View.VISIBLE)UiMotion.expandBarToContent(workingBar,null);
            }});
    }

    private void hideWorkingIndicator() {
        workingPulseGeneration++;
        workingIndicator=null;
        ui(()->{SessionRuntime rt=activeRuntime;if(agentProgressView!=null&&rt!=null){agentProgressView.update(runtimeDisplayStatus(rt),rt.taskSnapshot,rt.planState,false);if(chatAutoFollow)scrollChatSoft();}
            if(workingBar!=null&&workingBar.getVisibility()==View.VISIBLE){
                if(rollingLog!=null)rollingLog.setRolling(false);
                UiMotion.collapseBar(workingBar,null);
            }
            // 回合结束：展开的任务清单立刻跟着滚木一起收走，别卡在输入框上。
            uiHandler.removeCallbacks(workingTaskCollapse);workingTaskCollapsePosted=false;
            if(workingTaskExpanded){workingTaskExpanded=false;UiMotion.collapseBar(workingTaskList,null);}});
    }

    private String friendlyNetworkError(String message, Throwable e) {
        String m=(message==null?"":message)+(e==null?"":"\n"+e.toString());
        String lower=m.toLowerCase(Locale.US);
        if(lower.contains("model_capability_not_supported")&&lower.contains("vision")) return "当前模型不支持视觉图片输入。请打开 API 设置，将“视觉图片输入（Vision）”切换为关闭，或改用支持视觉的模型。";
        if(lower.contains("cleartext")) return "系统或网络组件仍阻止了该 HTTP 地址；请检查设备网络策略，或改用 HTTPS。";
        return message==null?String.valueOf(e):message;
    }

    private void scheduleStreamingRender(){
        if(streamingView==null)return;
        if(streamRenderPosted)return;
        streamRenderPosted=true;
        // TextView layout and markdown-sized transcripts are expensive on the UI thread, so the
        // cadence widens as the rendered text grows: short replies advance at ~50fps, long ones
        // slow down instead of taking the UI thread away from the IME and user input.
        long delay=streamingVisibleChars>8000?48L:streamingVisibleChars>3000?32L:STREAM_FRAME_MS;
        streamingView.postDelayed(streamingFrameRunnable, delay);
    }

    private void cancelStreamingRender(){
        if(streamingView!=null)streamingView.removeCallbacks(streamingFrameRunnable);
        uiHandler.removeCallbacks(streamingFrameRunnable);
        streamRenderPosted=false;
    }

    private void renderStreamingFrame(){
        if(streamingRenderTreeGeneration!=chatTreeGeneration||!streamRenderPosted||streamingItem==null||streamingView==null){streamRenderPosted=false;return;}
        StringBuilder target=streamingItem.body;
        int targetLength=target.length();
        if(!streamingView.isAttachedToWindow()){
            streamingVisibleChars=targetLength;streamRenderPosted=false;
            if(streamingFinalizePending)finalizeStreamingMessage();
            return;
        }
        int revealCap=Math.min(targetLength,MESSAGE_TEXT_MAX_CHARS);
        // 每帧只吃掉积压的一部分：一次性吐到最新会让文字一段一段地蹦出来，越接近越慢才连续。
        if(revealCap>streamingVisibleChars){
            int step=Math.max(1,(int)Math.ceil((revealCap-streamingVisibleChars)*STREAM_REVEAL_EASE));
            revealCap=Math.min(revealCap,streamingVisibleChars+step);
        }
        android.text.Editable current=streamingView.getEditableText();
        if(current!=null&&current.length()>=2&&current.subSequence(current.length()-2,current.length()).toString().equals(" ▍"))
            current.delete(current.length()-2,current.length());
        if(streamingVisibleChars>revealCap||streamingView.length()!=streamingVisibleChars){
            streamingVisibleChars=revealCap;
            String visible=target.substring(0,streamingVisibleChars);
            android.text.SpannableStringBuilder animated=new android.text.SpannableStringBuilder(visible);
            int cursorStart=animated.length();
            animated.append(" ▍");
            animated.setSpan(new ForegroundColorSpan(ACCENT),cursorStart,animated.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            streamingView.setText(animated,TextView.BufferType.SPANNABLE);
        }else if(revealCap>streamingVisibleChars){
            String visible=target.substring(streamingVisibleChars,revealCap);
            android.text.SpannableStringBuilder animated=new android.text.SpannableStringBuilder(visible);
            int cursorStart=animated.length();
            animated.append(" ▍");
            animated.setSpan(new ForegroundColorSpan(ACCENT),cursorStart,animated.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            streamingView.append(animated);
        }
        streamingVisibleChars=revealCap;
        if(agentProgressView!=null)agentProgressView.setOutputBacklog(0);
        if(chatAutoFollow)scheduleAutoFollowScroll();
        streamRenderPosted=false;
        // 还有没吐完的正文就继续排下一帧，这一帧只负责把动画往前推一点。
        if(streamingVisibleChars<Math.min(targetLength,MESSAGE_TEXT_MAX_CHARS))scheduleStreamingRender();
        else if(streamingFinalizePending)streamingView.post(this::finalizeStreamingMessage);
    }

    private void finishStreamingWhenRevealed(){
        if(streamingItem==null||streamingView==null){finalizeStreamingMessage();return;}
        if(streamingVisibleChars>=Math.min(streamingItem.body.length(),MESSAGE_TEXT_MAX_CHARS)){finalizeStreamingMessage();return;}
        streamingFinalizePending=true;
        scheduleStreamingRender();
    }

    private void scheduleAutoFollowScroll(){
        if(autoFollowScrollPosted||!chatAutoFollow||chatScroll==null||chatMessages==null||chatUserTouching)return;
        final ScrollView targetScroll=chatScroll;final LinearLayout targetMessages=chatMessages;final long treeGeneration=chatTreeGeneration;
        android.view.ViewTreeObserver observer=targetScroll.getViewTreeObserver();
        if(!observer.isAlive())return;
        autoFollowScrollPosted=true;autoFollowScrollTarget=targetScroll;autoFollowMessagesTarget=targetMessages;autoFollowScrollTreeGeneration=treeGeneration;
        autoFollowScrollListener=()->{
            long scheduledGeneration=autoFollowScrollTreeGeneration;
            cancelAutoFollowScroll();
            if(scheduledGeneration!=treeGeneration||treeGeneration!=chatTreeGeneration||targetScroll!=chatScroll||targetMessages!=chatMessages)return true;
            if(chatAutoFollow&&!chatUserTouching)scrollChatToEndWithoutFocus(targetScroll,targetMessages,false);
            return true;
        };
        observer.addOnPreDrawListener(autoFollowScrollListener);
    }

    private void cancelAutoFollowScroll(){
        ScrollView target=autoFollowScrollTarget;
        android.view.ViewTreeObserver.OnPreDrawListener listener=autoFollowScrollListener;
        if(target!=null&&listener!=null){
            android.view.ViewTreeObserver observer=target.getViewTreeObserver();
            if(observer.isAlive())observer.removeOnPreDrawListener(listener);
        }
        autoFollowScrollPosted=false;autoFollowScrollTarget=null;autoFollowMessagesTarget=null;autoFollowScrollTreeGeneration=0L;autoFollowScrollListener=null;
    }
    private boolean discardEmptyStreamingMessage(){
        if(streamingItem==null||streamingItem.body.length()>0&&!streamingItem.body.toString().trim().isEmpty())return false;
        if(streamingItem.renderedView!=null&&streamingItem.renderedView.getParent() instanceof ViewGroup)
            ((ViewGroup)streamingItem.renderedView.getParent()).removeView(streamingItem.renderedView);
        else if(streamingBodyHost!=null&&streamingBodyHost.getParent() instanceof ViewGroup)
            ((ViewGroup)streamingBodyHost.getParent()).removeView(streamingBodyHost);
        transcript.remove(streamingItem);
        streamingItem=null;streamingView=null;streamingBodyHost=null;streamingVisibleChars=0;
        return true;
    }

    private void finalizeStreamingMessage(){
        streamingFinalizePending=false;
        cancelAutoFollowScroll();
        cancelStreamingRender();
        if(agentProgressView!=null)agentProgressView.setOutputBacklog(0);
        final long stallStartedAt = SystemClock.elapsedRealtime();
        if(discardEmptyStreamingMessage())return;
        boolean follow=chatAutoFollow;int oldY=chatScroll==null?0:chatScroll.getScrollY();
        if(streamingItem!=null&&streamingItem.thinking.length()==0&&streamingItem.thinkingHost!=null)streamingItem.thinkingHost.setVisibility(View.GONE);
        if(streamingBodyHost!=null){
            streamingBodyHost.removeAllViews();
            LinearLayout rich=MarkdownRenderer.render(this,streamingItem.body.toString(),14f,neonTheme,lightTheme);
            streamingBodyHost.addView(rich,lp(-1,-2));
            UiMotion.contentUpdated(streamingBodyHost,true);
            UiMotion.staggerChildren(rich,8);
            UiMotion.bindInteractive(rich);
            addContextFooter(streamingBodyHost,streamingItem,true);
        }
        streamingItem=null;streamingView=null;streamingBodyHost=null;streamingVisibleChars=0;
        final ScrollView targetScroll=chatScroll;final LinearLayout targetMessages=chatMessages;final long treeGeneration=chatTreeGeneration;
        if(targetScroll!=null)targetScroll.post(()->{if(treeGeneration!=chatTreeGeneration||targetScroll!=chatScroll||targetMessages!=chatMessages)return;if(follow)scrollChatToEndWithoutFocus(targetScroll,targetMessages,true);else targetScroll.scrollTo(0,oldY);});
        reportStall("finalizeStreamingMessage", stallStartedAt);
    }
    private int chatBottomScrollY(ScrollView targetScroll,LinearLayout targetMessages){
        if(targetScroll==null||targetMessages==null)return 0;
        int viewport=Math.max(0,targetScroll.getHeight()-targetScroll.getPaddingTop()-targetScroll.getPaddingBottom());
        return Math.max(0,targetMessages.getHeight()-viewport);
    }
    private void updateChatFollowState(){
        if(chatScroll==null||chatMessages==null)return;
        chatAutoFollow=chatBottomScrollY(chatScroll,chatMessages)-chatScroll.getScrollY()<=dp(72);
        if(!chatAutoFollow)cancelAutoFollowScroll();
    }
    private void scrollChatNow(){
        final ScrollView targetScroll=chatScroll;final LinearLayout targetMessages=chatMessages;final long treeGeneration=chatTreeGeneration;
        if(targetScroll!=null&&targetMessages!=null)targetScroll.post(()->{if(treeGeneration==chatTreeGeneration&&targetScroll==chatScroll&&targetMessages==chatMessages)scrollChatToEndWithoutFocus(targetScroll,targetMessages,false);});
    }
    private void scrollChatToEndWithoutFocus(ScrollView targetScroll,LinearLayout targetMessages,boolean smooth){
        if(targetScroll==null||targetMessages==null)return;
        int y=chatBottomScrollY(targetScroll,targetMessages);
        if(smooth)targetScroll.smoothScrollTo(0,y);else glideChatScroll(targetScroll,y);
    }
    private void scrollChatSoft(){
        final ScrollView targetScroll=chatScroll;final LinearLayout targetMessages=chatMessages;final long treeGeneration=chatTreeGeneration;
        if(targetScroll!=null&&targetMessages!=null)targetScroll.post(()->{if(treeGeneration!=chatTreeGeneration||targetScroll!=chatScroll||targetMessages!=chatMessages||!chatAutoFollow||chatUserTouching)return;glideChatScroll(targetScroll,chatBottomScrollY(targetScroll,targetMessages));});
    }
    private void scrollChat(){chatAutoFollow=true;cancelAutoFollowScroll();scrollChatSoft();}

    /** 取消进行中的滚动滑翔（换树/用户接管时调用，绝不带到新 ScrollView 上）。 */
    private void cancelChatGlide(){
        android.animation.ValueAnimator animator=chatScrollAnimator;
        chatScrollAnimator=null;
        if(animator!=null)animator.cancel();
    }

    /**
     * 会话滚动的短滑翔：跟随流式输出/新消息时不再是逐帧瞬跳，而是一段 ~160ms 的
     * 缓动；每帧重定目标都从当前位置续走，所以连续调用拼成一条连续的滑动轨迹。
     */
    private void glideChatScroll(ScrollView target,int y){
        if(target==null)return;
        y=Math.max(0,y);
        int from=target.getScrollY();
        if(from==y)return;
        cancelChatGlide();
        long duration=UiMotion.durationFor(this,160L);
        if(duration==0L){target.scrollTo(0,y);return;}
        android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofInt(from,y);
        animator.setDuration(duration).setInterpolator(UiMotion.standard());
        animator.addUpdateListener(a->{if(target!=chatScroll)return;target.scrollTo(0,(Integer)a.getAnimatedValue());});
        chatScrollAnimator=animator;
        animator.start();
    }

    @Override protected void onDestroy(){
        try { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener); } catch (Throwable ignored) { }
        stopDeviceStatus();
        UiCanvasStore.removeChangeListener(canvasChangeListener);
        if(keyboardRoot!=null&&keyboardLayoutListener!=null)keyboardRoot.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
        cancelAutoFollowScroll();
        cancelChatGlide();
        if(keyboardAnimator!=null)keyboardAnimator.cancel();
        finishThemeRevealNow();
        streamRenderPosted=false;chatRenderPosted=false;uiHandler.removeCallbacks(streamingFrameRunnable);uiHandler.removeCallbacks(streamingDeltaFlushRunnable);uiHandler.removeCallbacks(toolElapsedTicker);if(primaryHost!=null)primaryHost.removeCallbacks(chatRenderRunnable);if(agentProgressView!=null)agentProgressView.clear();if(terminalPane!=null)terminalPane.closeAll();releaseThemeSnapshot();for(SessionRuntime rt:sessionRuntimes.values())try{rt.engine.shutdown();}catch(Exception ignored){}sessionRuntimes.clear();super.onDestroy();io.shutdownNow();
    }

    private void animatePane(View v){if(!suppressPaneAnimation)UiMotion.pageIn(v);UiMotion.bindInteractive(v);}
    private void animateMessage(View v){UiMotion.messageIn(v,false);UiMotion.bindInteractive(v);}

    private Dialog newOverlayAwareDialog(){return newOverlayAwareDialog(0);}
    private Dialog newOverlayAwareDialog(int theme){
        Dialog d=theme==0?new Dialog(this):new Dialog(this,theme);
        Window w=d.getWindow();
        if(w!=null&&overlayShell!=null&&android.os.Build.VERSION.SDK_INT>=26){
            w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return d;
    }

    private void styleDialogWindow(Dialog d, int width, int height) {
        Window w = d == null ? null : d.getWindow(); if (w == null) return;
        if(overlayShell!=null&&android.os.Build.VERSION.SDK_INT>=26)w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams a = w.getAttributes(); a.dimAmount = .56f; w.setAttributes(a);
        w.setGravity(Gravity.CENTER); w.setLayout(width, height);
        View decor = w.getDecorView();
        UiMotion.bindInteractive(decor);
        UiMotion.dialogIn(decor);
    }

    /** Standard dialog surface: 16/14 padding on the card colour, matching every settings sheet. */
    private LinearLayout dialogPanel(){
        LinearLayout panel=vbox();
        int corner=(int)(20*uiCornerScale);
        panel.setPadding(dp(16),dp(16),dp(16),dp(16));
        panel.setBackground(round(SURFACE,corner,BORDER_SOFT,1));
        return panel;
    }

    /** Bold sheet title; 48dp tall so it also reads as a touch target when a close action sits beside it. */
    private TextView dialogTitle(String label){
        TextView t=text(label,17,TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    /**
     * Wraps dialog content in a scroll view sized to the content, capped at {@code maxHeightPx}, so a long
     * sheet scrolls instead of pushing its footer off a phone screen and a short one leaves no blank block.
     */
    private ScrollView scrollBody(View content,int dialogWidthPx,int maxHeightPx){
        int inner=Math.max(dp(200),dialogWidthPx-dp(32));
        content.measure(View.MeasureSpec.makeMeasureSpec(inner,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        ScrollView body=new ScrollView(this);
        body.setVerticalScrollBarEnabled(true);
        body.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        body.addView(content,new ScrollView.LayoutParams(-1,-2));
        body.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.min(Math.max(content.getMeasuredHeight(),0),maxHeightPx)));
        return body;
    }

    /** Sheet width: tablets get a 720dp working area, phones stay edge-to-edge minus a small margin. */
    private int dialogWidthPx(int phoneMaxDp){
        float screenW=getResources().getDisplayMetrics().widthPixels;
        return wide?Math.min(dp(720),(int)(screenW*.94f)):Math.min(dp(phoneMaxDp),(int)(screenW*.96f));
    }

    private int dialogMaxBodyPx(){
        float screenH=getResources().getDisplayMetrics().heightPixels;
        return (int)(screenH*(wide?.86f:.80f));
    }

    /** Form sheets resize around the keyboard, so the save button stays reachable while typing. */
    private void styleFormDialog(Dialog d,int widthPx){styleFormDialog(d,widthPx,-2);}
    private void styleFormDialog(Dialog d,int widthPx,int heightPx){
        styleDialogWindow(d,widthPx,heightPx);
        Window w=d==null?null:d.getWindow();
        if(w!=null)w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /** Equal-width action button filling a row; every cell keeps a 44dp touch target on a phone. */
    private TextView actionCell(LinearLayout row,String label,int color,boolean primary){
        TextView b=dialogButton(label,color,primary);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);
        p.setMargins(dp(3),0,dp(3),0);
        row.addView(b,p);
        return b;
    }

    private TextView dialogButton(String label,int color,boolean primary){
        TextView b=text(label,13,color);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        int emphasis = color == RED ? RED : ACCENT;
        int corner=(int)(12*uiCornerScale);
        b.setBackground(round(primary?mixColor(SURFACE_2,emphasis,.10f):SURFACE_2,corner,primary?mixColor(BORDER,emphasis,.35f):BORDER_SOFT,1));
        b.setPadding(dp(12),0,dp(12),0);
        return b;
    }

    /** Footer action: 48dp tall everywhere; full width on a phone, right-sized on a tablet. */
    private LinearLayout.LayoutParams dialogActionParams(){
        LinearLayout.LayoutParams p=wide?new LinearLayout.LayoutParams(dp(148),dp(48)):new LinearLayout.LayoutParams(0,dp(48),1);
        p.setMargins(dp(5),0,dp(5),0);
        return p;
    }

    private void showAnimatedAlert(AlertDialog dialog){
        if(dialog==null)return;
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            if(overlayShell!=null&&android.os.Build.VERSION.SDK_INT>=26){
                w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setLayout(Math.min(dp(460),(int)(getResources().getDisplayMetrics().widthPixels*.92f)),-2);
            UiMotion.bindInteractive(w.getDecorView());
            UiMotion.dialogIn(w.getDecorView());
        }
    }

    // --- Styling helpers ---
    /** Builds a tinted vector icon sized in dp; every icon in the UI is drawn this way. */
    private Drawable vectorIcon(int res,int sizeDp,int color){Drawable d=getResources().getDrawable(res,getTheme());d.setBounds(0,0,dp(sizeDp),dp(sizeDp));d.setTint(color);return d;}
    /** TextView that keeps a lone vector icon centered in its box; TextView.onDraw would pin it to paddingLeft. */
    private static final class IconTextView extends TextView {
        IconTextView(android.content.Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){
            Drawable[] ds=getCompoundDrawables();
            if(ds!=null&&ds[0]!=null&&ds[1]==null&&ds[2]==null&&ds[3]==null&&getText().length()==0){
                canvas.translate((getWidth()-getPaddingLeft()-getPaddingRight()-ds[0].getBounds().width())/2f,0);
            }
            super.onDraw(canvas);
        }
    }
    /** A TextView whose only content is a tinted vector icon, centered. */
    private TextView iconOnly(int res,int sizeDp,int color){TextView t=new IconTextView(this);t.setText("");t.setTextSize(14);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setCompoundDrawables(vectorIcon(res,sizeDp,color),null,null,null);return t;}
    /** Places a vector icon left of (or above) existing label text. */
    private void applyLeftIcon(TextView t,int res,int sizeDp,int color){t.setCompoundDrawablePadding(dp(8));t.setCompoundDrawables(vectorIcon(res,sizeDp,color),null,null,null);}
    private void applyTopIcon(TextView t,int res,int sizeDp,int color){t.setCompoundDrawablePadding(dp(3));t.setCompoundDrawables(null,vectorIcon(res,sizeDp,color),null,null);}
    /** Re-tints icons already attached to a row when its state color changes. */
    private void tintIcons(TextView t,int color){Drawable[] ds=t.getCompoundDrawables();for(int i=0;i<ds.length;i++)if(ds[i]!=null)ds[i].setTint(color);}
    /** Maps a leading glyph token (kept in literals) to its vector drawable. */
    private int glyphIconRes(String token){switch(token.trim()){
        case "＋": return R.drawable.ic_plus;
        case "↻": return R.drawable.ic_refresh;
        case "⌂": return R.drawable.ic_home;
        case "›_": return R.drawable.ic_terminal;
        case "⌘": return R.drawable.ic_file;
        case "▣": return R.drawable.ic_box;
        case "ⓘ": return R.drawable.ic_info;
        case "⚙": return R.drawable.ic_settings;
        case "●": return R.drawable.ic_dot_filled;
        case "○": return R.drawable.ic_dot_ring;
        default: return 0;}}
    /** Maps a workspace tab token to its vector drawable. */
    private int tabIconRes(String token){switch(token.trim()){
        case "⌂": return R.drawable.ic_chat;
        case ">_": return R.drawable.ic_terminal;
        case "▣": return R.drawable.ic_files;
        default: return 0;}}
    private TextView iconButton(int res){TextView t=iconOnly(res,22,MUTED);t.setBackground(round(Color.TRANSPARENT,10,Color.TRANSPARENT,0));return t;}
    private TextView smallIcon(int res){return iconOnly(res,18,MUTED);}
    private TextView pill(String s,int color){TextView t=text(s,11,color);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),0,dp(8),0);t.setBackground(round(SURFACE_2,14,Color.TRANSPARENT,0));applyLeadingIconToken(t,color);return t;}
    private TextView smallPill(String s,int color){TextView t=text(s,11,color);t.setPadding(dp(4),0,dp(4),0);t.setGravity(Gravity.CENTER_VERTICAL);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);applyLeadingIconToken(t,color);return t;}
    /** Rewrites a leading "glyph space" token into its vector icon, so labels like "＋ 新增" draw an icon. */
    private void applyLeadingIconToken(TextView t,int color){
        String label=String.valueOf(t.getText());
        int gap=label.indexOf(' ');
        if(gap<=0)return;
        int res=glyphIconRes(label.substring(0,gap));
        if(res==0)return;
        t.setText(label.substring(gap+1).trim());
        applyLeftIcon(t,res,14,color);
    }
    private View dividerVertical(){View v=new View(this);v.setBackgroundColor(BORDER_SOFT);return v;}
    private View dividerHorizontal(){View v=new View(this);v.setBackgroundColor(BORDER_SOFT);return v;}
    private LinearLayout vbox(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
    private LinearLayout hbox(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);return x;}
    private TextView text(String s,float sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp*uiFontScale);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    private void setAnimatedText(TextView view,CharSequence value,boolean emphasized){if(view==null)return;CharSequence next=value==null?"":value;if(String.valueOf(view.getText()).contentEquals(next))return;view.setText(next);UiMotion.contentUpdated(view,emphasized);}
    private GradientDrawable round(int fill,float radiusDp,int stroke,int strokeDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(radiusDp*uiCornerScale*getResources().getDisplayMetrics().density);if(strokeDp>0)g.setStroke(dp(strokeDp),stroke);return g;}
    private GradientDrawable gradient(int start,int end,float radiusDp,int stroke,int strokeDp){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});g.setCornerRadius(radiusDp*uiCornerScale*getResources().getDisplayMetrics().density);if(strokeDp>0)g.setStroke(dp(strokeDp),stroke);return g;}
    private GradientDrawable themedTopBackground(){return neonTheme?gradient(Color.rgb(8,16,34),Color.rgb(18,22,52),0,BORDER_SOFT,0):round(TOP,0,Color.TRANSPARENT,0);}
    private GradientDrawable navyGradient(float radiusDp){return gradient(Color.rgb(8,16,34),Color.rgb(13,22,43),radiusDp,BORDER,0);}
    private GradientDrawable elevatedCard(float radiusDp){return neonTheme?gradient(Color.rgb(28,36,65),Color.rgb(15,27,48),radiusDp,BORDER,1):round(SURFACE_2,radiusDp,Color.TRANSPARENT,0);}
    private GradientDrawable accentGradient(float radiusDp){
        if(neonTheme)return gradient(Color.rgb(91,103,255),Color.rgb(142,78,255),radiusDp,Color.rgb(151,129,255),1);
        if(uiGradientAccent)return gradient(mixColor(ACCENT,Color.WHITE,.24f),ACCENT,radiusDp,Color.TRANSPARENT,0);
        return round(ACCENT,radiusDp,Color.TRANSPARENT,0);
    }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void ui(Runnable r){runOnUiThread(r);}
    private static String shorten(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,Math.max(0,n-1))+"…";}
    private static String shortenMultiline(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n)+"\n…";}
    private static String cleanBaseUrl(String s){return s==null?"":s.trim();}
    private static byte[] readFile(File f)throws Exception{try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[(int)f.length()];int o=0,n;while(o<b.length&&(n=in.read(b,o,b.length-o))>0)o+=n;return b;}}
    private static void writeFileBytes(File f,byte[] data)throws Exception{File parent=f.getParentFile();if(parent!=null)parent.mkdirs();try(FileOutputStream out=new FileOutputStream(f)){out.write(data);}}
    private static boolean deleteRecursive(File f){if(f==null||!f.exists())return true;if(f.isDirectory()){File[] xs=f.listFiles();if(xs!=null)for(File x:xs)if(!deleteRecursive(x))return false;}return f.delete();}
}
