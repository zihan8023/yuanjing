package com.iqge.opencode;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * 触摸/文字 → 真实 X11 输入的桥。
 *
 * 只做两件事：把一行命令写进 input.fifo（由常驻输入泵 eval），把文本/剪贴板落到文件再让
 * xdotool/xclip 从文件读。动态内容一律不进命令行，从根上掐掉引号与命令注入。
 */
public final class OpenCodeInputBridge implements AutoCloseable {
    private final Context context;
    private Writer fifo;

    public OpenCodeInputBridge(Context c) { this.context = c.getApplicationContext(); }

    private synchronized Writer writer() throws IOException {
        if (fifo != null) return fifo;
        File f = OpenCodeRuntime.inputFifo(context);
        if (!f.exists()) throw new IOException("输入管道不存在，请先启动 OpenCode 运行时");
        fifo = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8);
        return fifo;
    }

    private synchronized void send(String command) {
        try {
            Writer w = writer();
            w.write(command);
            w.write('\n');
            w.flush();
        } catch (IOException e) {
            close();
        }
    }

    public void pointerMove(int x, int y) { send("xdotool mousemove " + x + " " + y); }
    public void pointerDown(int x, int y, int button) { send("xdotool mousemove " + x + " " + y + " mousedown " + button); }
    public void pointerUp(int button) { send("xdotool mouseup " + button); }
    public void click(int x, int y, int button) { send("xdotool mousemove " + x + " " + y + " click " + button); }

    public void scroll(int x, int y, boolean up) {
        send("xdotool mousemove " + x + " " + y + " click " + (up ? 4 : 5));
    }

    /** 拖动：按下 → 中途多次 move → 抬起。调用方按手势节奏发 move。 */
    public void dragStart(int x, int y, int button) { pointerDown(x, y, button); }
    public void dragMove(int x, int y) { pointerMove(x, y); }
    public void dragEnd(int button) { pointerUp(button); }

    public void key(String xdotoolKeyName) { send("xdotool key --clearmodifiers " + xdotoolKeyName); }

    /** 文本经文件投递，xdotool type --file 逐字符注入当前焦点窗口。 */
    public void typeText(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            File f = OpenCodeRuntime.typeFile(context);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            send("xdotool type --clearmodifiers --delay 12 --file '" + f.getAbsolutePath() + "'");
        } catch (IOException e) {
            close();
        }
    }

    public void setClipboard(String text) {
        if (text == null) return;
        try {
            File f = OpenCodeRuntime.typeFile(context);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            send("xclip -selection clipboard -i < '" + f.getAbsolutePath() + "'");
        } catch (IOException e) {
            close();
        }
    }

    /** 剪贴板读取是同步的（要拿返回值），单独走一次短命令。 */
    public String getClipboard() {
        try {
            OpenCodeRuntime.writeScripts(context);
            com.termux.app.iqcode.termux.TermuxShellExecutor exec =
                new com.termux.app.iqcode.termux.TermuxShellExecutor(context);
            com.termux.app.iqcode.termux.TermuxShellExecutor.Result r = exec.execute(
                "bash '" + OpenCodeRuntime.script(context, "clipget.sh").getAbsolutePath() + "'",
                com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH, 8_000);
            return r.stdout;
        } catch (Exception e) {
            return "";
        }
    }

    @Override public synchronized void close() {
        if (fifo != null) {
            try { fifo.close(); } catch (IOException ignored) { }
            fifo = null;
        }
    }
}
