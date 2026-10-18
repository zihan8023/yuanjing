package com.iqge.opencode;

import android.content.Context;

import com.termux.app.iqcode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * OpenCode Desktop 运行时的落地实现（实验性）。
 *
 * 目标：在 IQ Code 自带的 Termux/Bionic ARM64 环境里跑官方 OpenCode Desktop 的 Electron 本体，
 * 通过 X11 显示层把真实画面送到 Android，输入/IME/剪贴板走桥接。不重写 OpenCode UI，不用 WebView。
 *
 * 已验证事实（本机实测）：
 * - 官方 v1.18.34 提供 linux-arm64 的 AppImage/deb/rpm；CLI arm64 二进制可用 glibc-runner 直接跑通（--version=1.18.34）。
 * - 该 Electron 主程序在 glibc-runner 下加载器可用，第一个缺的库是 libglib-2.0.so.0 → 需要补一整套 Debian arm64 运行库（GTK3/NSS/X11…）。
 * - glibc-runner 自带的 glibc 是 2.44，足够跑 Debian trixie（2.41/2.42）的库。
 *   AppImage 运行时不兼容（ELF ABI version invalid）→ 这里改用官方 deb 包用 dpkg-deb -x 解包，绕开 AppImage。
 *
 * 因此"魔改 glibc"= 不重建 glibc，只补库：把 Debian arm64 的 .so 解到 app 私有目录，用 LD_LIBRARY_PATH 喂给 glibc-runner。
 */
public final class OpenCodeRuntime {
    public static final String DESKTOP_VERSION = "1.18.34";
    public static final String DEB_URL =
        "https://github.com/anomalyco/opencode/releases/download/v" + DESKTOP_VERSION
            + "/opencode-desktop-linux-arm64.deb";

    /** Electron 的 DT_NEEDED 已经实测出来，这里按库名反查 Debian 包补齐（glibc 侧）。 */
    public static final String[] LIB_DEBS = {
        "libglib2.0-0t64", "libgtk-3-0t64", "libnss3", "libnspr4", "libatk1.0-0t64",
        "libatk-bridge2.0-0t64", "libatspi2.0-0t64", "libcups2t64", "libdbus-1-3",
        "libcairo2", "libpango-1.0-0", "libx11-6", "libxcb1", "libxcomposite1",
        "libxdamage1", "libxext6", "libxfixes3", "libxrandr2", "libgbm1",
        "libexpat1", "libxkbcommon0", "libxkbcommon-x11-0", "libudev1", "libasound2t64",
        "libpixman-1-0", "libxrender1", "libxi6", "libxtst6", "libepoxy0",
        "libfontconfig1", "libfreetype6", "libharfbuzz0b", "libfribidi0", "libthai0",
        "libdatrie1", "libgraphite2-3", "libpng16-16t64", "libjpeg62-turbo",
        "libwayland-client0", "libxshmfence1",
        "fonts-dejavu-core", "fonts-noto-core"
    };

    /** Termux 侧需要装的包（Bionic 原生）：X 服务器、输入注入、剪贴板、glibc 运行器。 */
    public static final String[] TERMUX_PACKAGES = {
        "x11-repo", "glibc-runner", "termux-x11-nightly", "xdotool", "xclip", "xz-utils", "tar", "curl", "tigervnc"
    };

    public interface Progress { void onStep(String text); }

    private OpenCodeRuntime() { }

    public static File root(Context c) { return new File(c.getFilesDir(), "opencode"); }
    public static File appDir(Context c) { return new File(root(c), "app"); }
    public static File libDir(Context c) { return new File(root(c), "libs"); }
    public static File logDir(Context c) { return new File(root(c), "log"); }
    public static File runDir(Context c) { return new File(root(c), "run"); }

    public static File script(Context c, String name) { return new File(root(c), name); }

    /** 输入泵的命名管道：Java 侧写入一行 = 一条 xdotool 命令。 */
    public static File inputFifo(Context c) { return new File(runDir(c), "input.fifo"); }

    /** 交给 xdotool type --file 的文本落地文件，避免任何 shell 引号/注入问题。 */
    public static File typeFile(Context c) { return new File(runDir(c), "type.txt"); }

    public static boolean isProvisioned(Context c) {
        File marker = new File(root(c), ".provisioned");
        return marker.isFile() && new File(root(c), "desktop.bin").isFile();
    }

    /** 生成运行期脚本（幂等）。所有脚本都放在 app 私有目录，umask 077。 */
    public static void writeScripts(Context c) throws Exception {
        File r = root(c);
        for (File d : new File[]{r, appDir(c), libDir(c), logDir(c), runDir(c)}) {
            if (!d.isDirectory() && !d.mkdirs() && !d.isDirectory())
                throw new IllegalStateException("无法创建目录: " + d);
        }
        write(c, "bootstrap.sh", bootstrapScript(c));
        write(c, "xserver.sh", xserverScript(c));
        write(c, "desktop.sh", desktopScript(c));
        write(c, "inputloop.sh", inputLoopScript(c));
        write(c, "inputdaemon.sh", inputDaemonScript(c));
        write(c, "clipget.sh", clipGetScript(c));
        write(c, "clipset.sh", clipSetScript(c));
        write(c, "stop.sh", stopScript(c));
    }

    private static void write(Context c, String name, String body) throws Exception {
        File f = script(c, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        //noinspection ResultOfMethodCallIgnored
        f.setExecutable(true, true);
    }

    private static String prefix() { return TermuxConstants.TERMUX_PREFIX_DIR_PATH; }

    /** 一次装齐：Termux 侧包 + Debian arm64 运行库 + OpenCode Desktop deb 解包。 */
    static String bootstrapScript(Context c) {
        File r = root(c);
        StringBuilder debs = new StringBuilder();
        for (String pkg : LIB_DEBS) debs.append(pkg).append(' ');
        String pkgs = String.join(" ", TERMUX_PACKAGES);
        return "set -e\n" +
            "export PREFIX='" + prefix() + "'\n" +
            "export PATH=\"$PREFIX/bin:$PATH\"\n" +
            "R='" + r.getAbsolutePath() + "'\n" +
            "mkdir -p \"$R/app\" \"$R/libs\" \"$R/log\" \"$R/run\" \"$R/debs\"\n" +
            "step(){ echo \"[IQGE] $*\"; }\n" +
            "step '更新 Termux 包索引'\n" +
            "yes | pkg update >/dev/null 2>&1 || true\n" +
            "step '安装 X 服务器 / 输入注入 / 剪贴板 / glibc 运行器'\n" +
            "yes | pkg install -y " + pkgs + " >\"" + logDir(c).getAbsolutePath() + "/apt.log\" 2>&1 || true\n" +
            "step '下载 Debian arm64 运行库'\n" +
            "cd \"$R/debs\"\n" +
            "[ -f Packages ] || { curl -fsSL -o Packages.xz 'https://deb.debian.org/debian/dists/stable/main/binary-arm64/Packages.xz' && xz -dc Packages.xz > Packages; }\n" +
            "for p in " + debs + "; do\n" +
            "  [ -f \"$p.deb\" ] && continue\n" +
            "  FN=$(awk -v p=\"$p\" '$1==" + "\"Package:\"" + " && $2==p {f=1; next} f && $1==" + "\"Filename:\"" + " {print $2; exit}' Packages)\n" +
            "  [ -n \"$FN\" ] && curl -fsSL -o \"$p.deb\" \"https://deb.debian.org/debian/$FN\" || true\n" +
            "done\n" +
            "step '解开运行库'\n" +
            "for f in *.deb; do [ -f \"$f\" ] && dpkg-deb -x \"$f\" \"$R/libs\" >/dev/null 2>&1 || true; done\n" +
            "step '下载 OpenCode Desktop (" + DESKTOP_VERSION + ")'\n" +
            "[ -f \"$R/debs/opencode-desktop.deb\" ] || curl -fL --retry 3 -o \"$R/debs/opencode-desktop.deb\" '" + DEB_URL + "'\n" +
            "step '解包 OpenCode Desktop'\n" +
            "rm -rf \"$R/app\"; mkdir -p \"$R/app\"\n" +
            "dpkg-deb -x \"$R/debs/opencode-desktop.deb\" \"$R/app\"\n" +
            "BIN=$(find \"$R/app\" -type f -name 'ai.opencode.desktop' | head -1)\n" +
            "[ -n \"$BIN\" ] || { echo '[IQGE] 找不到 ai.opencode.desktop'; exit 3; }\n" +
            "printf '%s' \"$BIN\" > \"$R/desktop.bin\"\n" +
            "chmod 0755 \"$BIN\" \"$R/app/opt/OpenCode/chrome-sandbox\" 2>/dev/null || true\n" +
            "[ -p \"$R/run/input.fifo\" ] || { rm -f \"$R/run/input.fifo\"; mkfifo \"$R/run/input.fifo\" || true; }\n" +
            "touch \"$R/.provisioned\"\n" +
            "step '运行时准备完成'\n";
    }

    /** X 服务器：termux-x11 写的 Xvfb，画面由 Termux:X11 的 Surface 后端承接。 */
    static String xserverScript(Context c) {
        File r = runDir(c);
        return "export PREFIX='" + prefix() + "'\n" +
            "export PATH=\"$PREFIX/bin:$PATH\"\n" +
            "R='" + r.getAbsolutePath() + "'\n" +
            "if [ -f \"$R/x.pid\" ] && kill -0 \"$(cat \"$R/x.pid\")\" 2>/dev/null; then echo '[IQGE] X 已在运行'; exit 0; fi\n" +
            "termux-x11 :0 -ac -nolisten tcp >'" + logDir(c).getAbsolutePath() + "/xserver.log' 2>&1 &\n" +
            "echo $! > \"$R/x.pid\"\n" +
            "for i in $(seq 1 60); do [ -e /tmp/.X11-unix/X0 ] && { echo '[IQGE] X 已就绪'; exit 0; }; sleep 0.5; done\n" +
            "echo '[IQGE] X 启动超时（确认 Termux:X11 已安装）'; exit 4\n";
    }

    /** 启动 Electron 本体：glibc-runner 载入官方二进制，库搜索路径指向补好的 Debian 库。 */
    static String desktopScript(Context c) {
        File r = root(c);
        return "export PREFIX='" + prefix() + "'\n" +
            "export PATH=\"$PREFIX/bin:$PATH\"\n" +
            "R='" + r.getAbsolutePath() + "'\n" +
            "L=\"$R/libs/usr/lib/aarch64-linux-gnu:$R/libs/usr/lib\"\n" +
            "export LD_LIBRARY_PATH=\"$L:$LD_LIBRARY_PATH\"\n" +
            "export DISPLAY=:0\n" +
            "export XDG_RUNTIME_DIR=\"$R/run\"\n" +
            "export HOME=\"$R/home\"\n" +
            "mkdir -p \"$HOME\"\n" +
            "BIN=$(cat \"$R/desktop.bin\")\n" +
            "if [ -f \"$R/run/desktop.pid\" ] && kill -0 \"$(cat \"$R/run/desktop.pid\")\" 2>/dev/null; then echo '[IQGE] Desktop 已在运行'; exit 0; fi\n" +
            "setsid glibc-runner \"$BIN\" --no-sandbox --ozone-platform=x11 --disable-gpu-sandbox " +
            ">>'" + logDir(c).getAbsolutePath() + "/desktop.log' 2>&1 &\n" +
            "echo $! > \"$R/run/desktop.pid\"\n" +
            "echo '[IQGE] Desktop 启动中'\n";
    }

    /**
     * 常驻输入泵：从命名管道读行，每行一条完整 shell 命令。
     * 命令由 Java 侧自建并 shell-quote，动态内容（文字/剪贴板）一律走文件，不走命令行。
     */
    static String inputLoopScript(Context c) {
        File r = root(c);
        return "export PATH='" + prefix() + "/bin:$PATH'\n" +
            "export DISPLAY=:0\n" +
            "R='" + r.getAbsolutePath() + "'\n" +
            "FIFO=\"$R/run/input.fifo\"\n" +
            "[ -p \"$FIFO\" ] || { rm -f \"$FIFO\"; mkfifo \"$FIFO\" || exit 5; }\n" +
            "while IFS= read -r line; do\n" +
            "  [ -n \"$line\" ] && eval \"$line\" >/dev/null 2>&1\n" +
            "done < \"$FIFO\"\n";
    }

    /** 输入泵由自身脚本后台化，execute() 立刻返回，不长时间占住一个执行线程。 */
    static String inputDaemonScript(Context c) {
        File r = runDir(c);
        return "export PREFIX='" + prefix() + "'\n" +
            "export PATH=\"$PREFIX/bin:$PATH\"\n" +
            "R='" + r.getAbsolutePath() + "'\n" +
            "if [ -f \"$R/input.pid\" ] && kill -0 \"$(cat \"$R/input.pid\")\" 2>/dev/null; then echo '[IQGE] 输入泵已在运行'; exit 0; fi\n" +
            "setsid bash '" + script(c, "inputloop.sh").getAbsolutePath() + "' " +
            ">>'" + logDir(c).getAbsolutePath() + "/input.log' 2>&1 < /dev/null &\n" +
            "echo $! > \"$R/input.pid\"\n" +
            "echo '[IQGE] 输入泵已就绪'\n";
    }

    static String clipGetScript(Context c) {
        return "export PATH='" + prefix() + "/bin:$PATH'\nexport DISPLAY=:0\nxclip -selection clipboard -o 2>/dev/null || true\n";
    }

    static String clipSetScript(Context c) {
        return "export PATH='" + prefix() + "/bin:$PATH'\nexport DISPLAY=:0\nxclip -selection clipboard -i 2>/dev/null || true\n";
    }

    static String stopScript(Context c) {
        File r = root(c);
        return "export PREFIX='" + prefix() + "'\n" +
            "export PATH=\"$PREFIX/bin:$PATH\"\n" +
            "R='" + r.getAbsolutePath() + "'\n" +
            "for f in \"$R/run/desktop.pid\" \"$R/run/input.pid\" \"$R/run/x.pid\"; do\n" +
            "  [ -f \"$f\" ] || continue\n" +
            "  P=$(cat \"$f\")\n" +
            "  kill -TERM \"-$P\" 2>/dev/null || kill -TERM \"$P\" 2>/dev/null || true\n" +
            "  rm -f \"$f\"\n" +
            "done\n" +
            "pkill -f 'ai.opencode.desktop' 2>/dev/null || true\n" +
            "echo '[IQGE] 已清理'\n";
    }

    /** 首装或补齐：跑 bootstrap.sh，最长 30 分钟（下载 ~200MB）。 */
    public static TermuxShellExecutor.Result provision(Context c, OpenCodeRuntime.Progress cb) throws Exception {
        writeScripts(c);
        return runScript(c, "bootstrap.sh", 30 * 60 * 1000, cb);
    }

    public static TermuxShellExecutor.Result startXServer(Context c, Progress cb) throws Exception {
        writeScripts(c);
        return runScript(c, "xserver.sh", 60_000, cb);
    }

    public static TermuxShellExecutor.Result startDesktop(Context c, Progress cb) throws Exception {
        writeScripts(c);
        return runScript(c, "desktop.sh", 60_000, cb);
    }

    public static TermuxShellExecutor.Result startInputPump(Context c, Progress cb) throws Exception {
        writeScripts(c);
        return runScript(c, "inputdaemon.sh", 30_000, cb);
    }

    public static TermuxShellExecutor.Result stopAll(Context c) throws Exception {
        writeScripts(c);
        return runScript(c, "stop.sh", 30_000, null);
    }

    /** 依次拉起 X 服务器 → 输入泵 → Desktop 本体。任一步失败即返回该步结果。 */
    public static TermuxShellExecutor.Result startAll(Context c, Progress cb) throws Exception {
        TermuxShellExecutor.Result r = startXServer(c, cb);
        if (r.exitCode != 0) return r;
        r = startInputPump(c, cb);
        if (r.exitCode != 0) return r;
        return startDesktop(c, cb);
    }

    private static TermuxShellExecutor.Result runScript(Context c, String name, int timeoutMs, Progress cb)
            throws Exception {
        TermuxShellExecutor exec = new TermuxShellExecutor(c);
        return exec.execute("bash '" + script(c, name).getAbsolutePath() + "'",
            TermuxConstants.TERMUX_HOME_DIR_PATH, timeoutMs,
            (chunk, stderr, elapsedMs) -> {
                if (cb == null || chunk == null || chunk.isEmpty()) return;
                for (String line : chunk.split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty()) cb.onStep(t);
                }
            });
    }
}
