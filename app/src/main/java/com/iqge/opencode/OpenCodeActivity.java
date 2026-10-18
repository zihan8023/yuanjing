package com.iqge.opencode;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * OpenCode Desktop 宿主界面：画面位 + 输入桥 + 运行状态。
 *
 * 画面来源是 Termux:X11 的真实 X 渲染路径（点“显示桌面”打开 Termux:X11 本体，它显示 :0），
 * 本 Activity 负责：启动/停止 OpenCode 运行时、把触摸/文字/快捷键送进 X、
 * 以及把 X 桌面坐标映射好。真实 OpenCode UI 不在这里仿制。
 */
public final class OpenCodeActivity extends Activity
        implements OpenCodeDisplayView.Listener, OpenCodeSession.Observer {

    private static final String X11_PACKAGE = "com.termux.x11";
    private static final String X11_ACTIVITY = "com.termux.x11.MainActivity";

    private OpenCodeDisplayView display;
    private TextView statusView;
    private ScrollView logScroll;
    private TextView logView;
    private EditText inputField;
    private OpenCodeSession session;
    private int activeButton = 1;
    private boolean pointerDown;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        session = new OpenCodeSession(this, this);
        announce();
    }

    private void announce() {
        if (isX11Installed()) {
            display.setStatus("点“显示桌面”查看 OpenCode Desktop 画面");
        } else {
            display.setStatus("未检测到 Termux:X11，画面无法显示（运行时仍可启动）");
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);

        display = new OpenCodeDisplayView(this);
        display.setListener(this);
        root.addView(display, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xE6100F0D);
        int pad = dp(10);
        panel.setPadding(pad, pad, pad, pad);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFE8E3D9);
        statusView.setTextSize(13f);
        statusView.setText("未启动");
        panel.addView(statusView);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(button("启动", v -> session.prepareAndStart()));
        actions.addView(button("停止", v -> session.stop()));
        actions.addView(button("显示桌面", v -> showDesktop()));
        actions.addView(button("键盘", v -> toggleIme()));
        panel.addView(actions);

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.addView(button("Esc", v -> key("Escape")));
        keys.addView(button("Tab", v -> key("Tab")));
        keys.addView(button("Enter", v -> key("Return")));
        keys.addView(button("Ctrl+C", v -> key("ctrl+c")));
        keys.addView(button("Ctrl+V", v -> pasteClipboard()));
        panel.addView(keys);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputField = new EditText(this);
        inputField.setHint("向 OpenCode 输入文字…");
        inputField.setTextColor(0xFFE8E3D9);
        inputField.setHintTextColor(0xFF8A8578);
        inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputRow.addView(inputField, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(button("发送", v -> sendText()));
        panel.addView(inputRow);

        logScroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(0xFF9FD3A5);
        logView.setTextSize(11f);
        logScroll.addView(logView);
        panel.addView(logScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        root.addView(panel, lp);
        return root;
    }

    private Button button(String label, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setOnClickListener(l);
        return btn;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private OpenCodeInputBridge bridge() { return session == null ? null : session.input(); }

    // ---- 触摸 → X11 ----

    @Override public void onPointerDown(int x, int y) {
        OpenCodeInputBridge in = bridge();
        if (in == null) return;
        activeButton = 1;
        pointerDown = true;
        in.pointerDown(x, y, 1);
    }

    @Override public void onPointerMove(int x, int y) {
        OpenCodeInputBridge in = bridge();
        if (in == null) return;
        in.pointerMove(x, y);
    }

    @Override public void onPointerUp(int x, int y) {
        OpenCodeInputBridge in = bridge();
        if (in == null) return;
        if (y > 0) in.pointerMove(x, y);
        if (pointerDown) { in.pointerUp(activeButton); pointerDown = false; }
        else in.click(x, y, 1);
    }

    @Override public void onRightClick(int x, int y) {
        OpenCodeInputBridge in = bridge();
        if (in == null) return;
        if (pointerDown) { in.pointerUp(activeButton); pointerDown = false; }
        in.click(x, y, 3);
    }

    @Override public void onScroll(int x, int y, boolean up) {
        OpenCodeInputBridge in = bridge();
        if (in != null) in.scroll(x, y, up);
    }

    @Override public void onRemoteResize(int desktopW, int desktopH) {
        // 桌面分辨率固定，视图只做坐标映射，不改 X 模式。
    }

    // ---- 会话回调 ----

    @Override public void onStatus(String text) { statusView.setText(text); }

    @Override public void onLog(String line) {
        if (line == null || line.isEmpty()) return;
        logView.append(line + "\n");
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override public void onReady() { Toast.makeText(this, "OpenCode Desktop 已就绪", Toast.LENGTH_SHORT).show(); }

    @Override public void onFailed(String reason) { Toast.makeText(this, reason, Toast.LENGTH_LONG).show(); }

    // ---- 快捷动作 ----

    private void key(String name) {
        OpenCodeInputBridge in = bridge();
        if (in != null) in.key(name);
    }

    private void sendText() {
        OpenCodeInputBridge in = bridge();
        if (in == null) { Toast.makeText(this, "先启动运行时", Toast.LENGTH_SHORT).show(); return; }
        String text = inputField.getText().toString();
        if (text.isEmpty()) return;
        in.typeText(text);
        inputField.setText("");
    }

    private void pasteClipboard() {
        OpenCodeInputBridge in = bridge();
        if (in != null) in.key("ctrl+v");
    }

    private void toggleIme() {
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (inputField.hasFocus()) {
            imm.hideSoftInputFromWindow(inputField.getWindowToken(), 0);
            inputField.clearFocus();
        } else {
            inputField.requestFocus();
            imm.showSoftInput(inputField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private boolean isX11Installed() {
        try {
            getPackageManager().getPackageInfo(X11_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** 打开 Termux:X11 本体，它渲染 :0 上的真实 OpenCode Desktop 画面。 */
    private void showDesktop() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(X11_PACKAGE, X11_ACTIVITY));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "未安装 Termux:X11，无法显示画面", Toast.LENGTH_LONG).show();
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            session.closeInput();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.closeInput();
            session = null;
        }
    }
}
