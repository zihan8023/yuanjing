package com.iqge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

/** 细线弧加载指示；保留原类名和滚/停接口供现有调用使用。 */
public final class RollingLogView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect visibleBounds = new Rect();
    private long lastFrameAt;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            framePosted = false;
            if (!canAnimate()) { stopFrames(); return; }
            long now = android.os.SystemClock.uptimeMillis();
            long dt = lastFrameAt == 0L ? 16L : Math.min(64L, now - lastFrameAt);
            lastFrameAt = now;
            angle = (angle + dt * .066f) % 360f;
            invalidate();
            syncFrames();
        }
    };
    private boolean rolling, framePosted;
    private float angle;
    private int color = 0xff8fa58f;

    public RollingLogView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setColor(int value) { color = value; invalidate(); }

    public void setRolling(boolean value) {
        rolling = value;
        syncFrames();
        invalidate();
    }

    private boolean canAnimate() {
        if (!rolling || !isAttachedToWindow() || getWindowVisibility() != VISIBLE
                || !isShown() || !UiMotion.enabled(getContext())) return false;
        float alpha = getAlpha();
        for (ViewParent parent = getParent(); parent instanceof View; parent = parent.getParent())
            alpha *= ((View) parent).getAlpha();
        return alpha > .05f && getGlobalVisibleRect(visibleBounds);
    }

    private void syncFrames() {
        if (frame == null) return;
        if (!canAnimate()) { stopFrames(); return; }
        if (!framePosted) { framePosted = true; postOnAnimation(frame); }
    }

    private void stopFrames() {
        removeCallbacks(frame);
        framePosted = false;
        lastFrameAt = 0L;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * .34f;
        paint.setColor(color);
        paint.setAlpha(android.graphics.Color.alpha(color) * 28 / 255);
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f, false, paint);
        paint.setAlpha(android.graphics.Color.alpha(color));
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, angle - 90f, 84f, false, paint);
        syncFrames();
    }

    // 父级淡入或滚回可见区域时恢复；监听绘制，不为隐藏视图轮询。
    private final android.view.ViewTreeObserver.OnPreDrawListener visibilityCheck = () -> {
        syncFrames();
        return true;
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(visibilityCheck);
        syncFrames();
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(visibilityCheck);
        stopFrames();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncFrames();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        syncFrames();
    }
}
