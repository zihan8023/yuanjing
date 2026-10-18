import com.termux.app.iqcode.storage.SessionStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 回归测试：超长对话（单行几 MB 的消息）下，会话汇总/元数据扫描必须内存有界。
 *
 * 历史崩溃（侧边栏刷新历史）：
 *   java.lang.OutOfMemoryError at JSONTokener.nextString
 *   SessionStore.readRows -> summarize -> listSessions
 * 原因：readRows 把整个 JSONL 解析成 JSONArray；一条超长消息行就是几 MB 的字符串。
 *
 * 运行（需要 android.jar 与 org.json jar）：
 *   javac -d "$HOME/.cache/iqtest" -sourcepath app/src/main/java \
 *     -cp "$HOME/android-sdk/platforms/android-35/android.jar:<json.jar>" \
 *     app/tests/SessionStoreStreamingTest.java
 *   java -Xmx64m -cp "$HOME/.cache/iqtest:<json.jar>" SessionStoreStreamingTest
 * 64MB 堆能通过，即证明扫描内存有界（旧实现会在此 OOM）。
 */
public final class SessionStoreStreamingTest {
    private static final int GIANT_CHARS = 8 * 1024 * 1024;
    /** 5 条 8MB 的长行：旧实现整文件累积解析在 64MB 堆上必 OOM，新实现逐行有界扫描。 */
    private static final int GIANT_LINES = 5;

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("iq-session-stream").toFile();
        File smallFirst = new File(dir, "small-user-first.jsonl");
        File giantFirst = new File(dir, "giant-user-first.jsonl");
        try {
            writeSession(smallFirst, false);
            writeSession(giantFirst, true);

            SessionStore.SessionSummary a = SessionStore.summarize(smallFirst);
            check("small.title", "第一条用户消息标题", a.title);
            check("small.count", String.valueOf(1 + GIANT_LINES), String.valueOf(a.messageCount));
            check("small.project", dir.getCanonicalPath(), a.project);
            check("small.tokens", "123456", String.valueOf(SessionStore.lastContextUsageTokens(smallFirst)));
            check("small.workflow", "wf-demo", SessionStore.loadWorkflowId(smallFirst));
            check("small.binding", "p1", SessionStore.loadProfileBinding(smallFirst).profileId);
            check("small.plan", "PLANNING", SessionStore.loadPlanState(smallFirst).status.name());

            SessionStore.SessionSummary b = SessionStore.summarize(giantFirst);
            check("giant.count", String.valueOf(GIANT_LINES), String.valueOf(b.messageCount));
            if (!b.title.startsWith("BIGUSER")) fail("giant.title 应从截断前缀恢复，实际: " + b.title);

            System.out.println("giant.title=" + b.title);
            System.out.println("PASS SessionStoreStreamingTest (maxHeap=" + (Runtime.getRuntime().maxMemory() >> 20) + "MB)");
        } finally {
            smallFirst.delete();
            giantFirst.delete();
            dir.delete();
        }
    }

    /** 一行 8MB 的消息 + 各种小行；userIsGiant=true 时首条（唯一）用户消息就是超长行。 */
    private static void writeSession(File file, boolean userIsGiant) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            write(out, new JSONObject()
                .put("type", "session_start")
                .put("project", file.getParentFile().getCanonicalPath())
                .put("project_key", "k")
                .put("workflow_id", "wf-demo")
                .put("created_at", 1000L));
            if (!userIsGiant) write(out, messageRow("user", "第一条用户消息标题"));
            // 超长行逐块写出：测试自身也不整段持有几 MB 的字符串。
            for (int i = 0; i < GIANT_LINES; i++)
                writeGiantMessage(out, userIsGiant ? "user" : "assistant",
                    userIsGiant ? "BIGUSER 超长粘贴开头 " : "BIGASSISTANT 超长输出 ");
            write(out, new JSONObject().put("type", "context_usage")
                .put("payload", new JSONObject().put("context_tokens", 123456)).put("timestamp", 2000L));
            write(out, new JSONObject().put("type", "profile_binding")
                .put("payload", new JSONObject().put("profile_id", "p1")).put("timestamp", 2000L));
            write(out, new JSONObject().put("type", "plan_entered")
                .put("payload", new JSONObject().put("workflow_id", "wf-demo")).put("timestamp", 2000L));
        }
    }

    private static void writeGiantMessage(FileOutputStream out, String role, String prefix) throws Exception {
        out.write(("{\"type\":\"message\",\"role\":\"" + role + "\",\"content\":[{\"type\":\"text\",\"text\":\"" + prefix)
            .getBytes(StandardCharsets.UTF_8));
        int remaining = GIANT_CHARS - prefix.length();
        byte[] chunk = new byte[64 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        while (remaining > 0) {
            int n = Math.min(remaining, chunk.length);
            out.write(chunk, 0, n);
            remaining -= n;
        }
        out.write(("\"}],\"message_id\":\"m-" + role + "\",\"timestamp\":1500}\n").getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject messageRow(String role, String text) throws Exception {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", text));
        return new JSONObject().put("type", "message").put("role", role).put("content", content)
            .put("message_id", "m-" + role).put("timestamp", 1500L);
    }

    private static void write(FileOutputStream out, JSONObject row) throws Exception {
        out.write(row.toString().getBytes(StandardCharsets.UTF_8));
        out.write('\n');
    }

    private static void check(String name, String expected, String actual) {
        if (!expected.equals(actual)) fail(name + " 期望 [" + expected + "] 实际 [" + actual + "]");
    }

    private static void fail(String message) {
        System.out.println("FAIL " + message);
        System.exit(1);
    }
}
