package com.iqge.opencode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * OpenCode Desktop 的显示位。
 *
 * 现状（第一阶段）：画面由 Termux:X11 的 Surface 后端承接，本 View 负责
 *   1) 给出 X 桌面的逻辑分辨率与坐标映射（触摸 → 桌面坐标，含 scale/offset）；
 *   2) 没有可用显示后端时显示状态/诊断文字，不假装成功。
 * 下一阶段：把 Termux:X11 的 LorieView 渲染循环并进本进程（GPL-3.0，需先确认许可策略），
 * 届时本 View 直接消费其共享缓冲，不用任何截图轮询。
 */
public class OpenCodeDisplayView extends SurfaceView implements SurfaceHolder.Callback {
    public interface Listener {
        void onPointerDown(int x, int y);
        void onPointerMove(int x, int y);
        void onPointerUp(int x, int y);
        void onRightClick(int x, int y);
        void onScroll(int x, int y, boolean up);
        void onRemoteResize(int desktopW, int desktopH);
    }

    private Listener listener;
    private String status = "等待 OpenCode Desktop 画面…";
    private int desktopW = 1280, desktopH = 800;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private float downX, downY, lastX, lastY;
    private long downAt;

    public OpenCodeDisplayView(Context c) { this(c, null); }

    public OpenCodeDisplayView(Context c, AttributeSet attrs) {
        super(c, attrs);
        getHolder().addCallback(this);
        paint.setColor(Color.WHITE);
        paint.setTextSize(28f);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setDesktopSize(int w, int h) {
        if (w > 0 && h > 0) { desktopW = w; desktopH = h; }
    }

    public void setStatus(String s) {
        status = s == null ? "" : s;
        invalidate();
    }

    /** Android 视图坐标 → 桌面坐标：先减去留黑边，再按比例放大。 */
    public void mapPoint(float viewX, float viewY, float[] out) {
        int vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) { out[0] = 0; out[1] = 0; return; }
        float scale = Math.min(vw / (float) desktopW, vh / (float) desktopH);
        float drawW = desktopW * scale, drawH = desktopH * scale;
        float offsetX = (vw - drawW) / 2f, offsetY = (vh - drawH) / 2f;
        out[0] = Math.max(0f, Math.min(desktopW, (viewX - offsetX) / scale));
        out[1] = Math.max(0f, Math.min(desktopH, (viewY - offsetY) / scale));
    }

    /** 桌面坐标 → 视图坐标（贴浮动光标、反向诊断用）。 */
    public void unmapPoint(float desktopX, float desktopY, float[] out) {
        int vw = getWidth(), vh = getHeight();
        float scale = Math.min(vw / (float) desktopW, vh / (float) desktopH);
        float drawW = desktopW * scale, drawH = desktopH * scale;
        out[0] = (vw - drawW) / 2f + desktopX * scale;
        out[1] = (vh - drawH) / 2f + desktopY * scale;
    }

    /** 两指手势：第二指落下后转成滚轮，避免误触发右键/拖动。 */
    private boolean twoFinger;
    private float pinchAnchorY;

    @Override public boolean onTouchEvent(MotionEvent e) {
        int[] d = new int[2];
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                twoFinger = false;
                downX = lastX = e.getX(); downY = lastY = e.getY();
                downAt = System.currentTimeMillis();
                toDesktop(e.getX(), e.getY(), d);
                if (listener != null) listener.onPointerDown(d[0], d[1]);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                twoFinger = true;
                pinchAnchorY = e.getY(0);
                if (listener != null) listener.onPointerUp(0, 0);
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (twoFinger) {
                    float y = e.getY(0);
                    float dy = pinchAnchorY - y;
                    if (Math.abs(dy) > 40f) {
                        toDesktop(e.getX(0), y, d);
                        if (listener != null) listener.onScroll(d[0], d[1], dy > 0);
                        pinchAnchorY = y;
                    }
                    return true;
                }
                float x = e.getX(), y = e.getY();
                if (Math.abs(x - lastX) + Math.abs(y - lastY) > 1.5f) {
                    toDesktop(x, y, d);
                    if (listener != null) listener.onPointerMove(d[0], d[1]);
                    lastX = x; lastY = y;
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (twoFinger) { twoFinger = false; return true; }
                toDesktop(e.getX(), e.getY(), d);
                if (listener == null) return true;
                if (System.currentTimeMillis() - downAt > 550
                        && Math.abs(e.getX() - downX) < 14 && Math.abs(e.getY() - downY) < 14) {
                    listener.onRightClick(d[0], d[1]);
                } else {
                    listener.onPointerUp(d[0], d[1]);
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                if (!twoFinger && listener != null) listener.onPointerUp(0, 0);
                twoFinger = false;
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    private void toDesktop(float viewX, float viewY, int[] out) {
        float[] p = new float[2];
        mapPoint(viewX, viewY, p);
        out[0] = Math.round(p[0]);
        out[1] = Math.round(p[1]);
    }

    /** 双指纵向滑动换算成滚轮刻度（外部手势接管时用）。 */
    public boolean scrollFromPinch(float dy, float x, float y) {
        int[] d = new int[2];
        toDesktop(x, y, d);
        int steps = Math.max(1, (int) (Math.abs(dy) / 48f));
        for (int i = 0; i < steps; i++) if (listener != null) listener.onScroll(d[0], d[1], dy < 0);
        return true;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) { canvas.drawColor(Color.rgb(18, 17, 15)); }
        } finally {
            if (canvas != null) try { holder.unlockCanvasAndPost(canvas); } catch (Exception ignored) { }
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (listener != null) listener.onRemoteResize(desktopW, desktopH);
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) { }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (status.isEmpty()) return;
        paint.getTextBounds(status, 0, status.length(), textBounds);
        float x = (getWidth() - textBounds.width()) / 2f;
        float y = getHeight() / 2f;
        canvas.drawText(status, Math.max(0, x), y, paint);
    }
}
