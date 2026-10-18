package com.iqge;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * One motion language for the native IQ Code workspace.
 *
 * The helpers deliberately animate only compositor-friendly properties (alpha,
 * translation and scale).  High-frequency surfaces such as TerminalView are
 * animated only when the pane is attached; terminal frames themselves are never
 * animated.  Weak maps prevent view bookkeeping from extending a view's lifetime.
 */
public final class UiMotion {
    private static final TimeInterpolator STANDARD = new PathInterpolator(.20f, 0f, 0f, 1f);
    private static final TimeInterpolator EMPHASIZED = new PathInterpolator(.16f, .84f, .22f, 1f);
    private static final Map<View, Boolean> BOUND = new WeakHashMap<>();
    private static final Map<View, Boolean> SELECTED = new WeakHashMap<>();
    private static final Map<View, Integer> LIST_POSITIONS = new WeakHashMap<>();
    private static final Map<View, ValueAnimator> BAR_ANIMATORS = new WeakHashMap<>();
    private static final Map<View, ViewTreeObserver.OnGlobalLayoutListener> BAR_LAYOUT_LISTENERS = new WeakHashMap<>();
    private static volatile long motionStateCheckedAt;
    private static volatile boolean cachedAnimationsEnabled = true;
    private static volatile boolean cachedPowerSave;

    private UiMotion() {}

    public static boolean enabled(Context context) {
        refreshMotionState(context);
        return cachedAnimationsEnabled;
    }

    private static void refreshMotionState(Context context) {
        if (context == null) return;
        long now=android.os.SystemClock.uptimeMillis();
        if(now-motionStateCheckedAt<1_000L)return;
        synchronized(UiMotion.class){
            if(now-motionStateCheckedAt<1_000L)return;
            try {
                cachedAnimationsEnabled=(Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled())
                    && Settings.Global.getFloat(context.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;
                PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
                cachedPowerSave=power!=null&&power.isPowerSaveMode();
            } catch(Throwable ignored){cachedAnimationsEnabled=true;cachedPowerSave=false;}
            motionStateCheckedAt=now;
        }
    }

    private static long duration(View view, long normal) {
        Context context=view==null?null:view.getContext();
        refreshMotionState(context);
        if(!cachedAnimationsEnabled)return 0L;
        // Keep transitions visible but short enough that frequent workspace updates do
        // not queue behind one another or make the UI feel sluggish.
        long tuned = Math.max(70L, (long)(normal * .78f));
        return cachedPowerSave ? Math.max(70L, (long)(tuned * .72f)) : tuned;
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    private static void identity(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    public static void appIn(View view) {
        if (view == null) return;
        long duration = duration(view, 260);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate().alpha(1f)
                .setInterpolator(STANDARD).setDuration(duration).withLayer().start();
    }

    /** Slightly brighter cross-fade used when the whole runtime palette changes. */
    public static void themeChanged(View view) {
        if (view == null) return;
        long duration = duration(view, 330);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(.7f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate().alpha(1f)
                .setInterpolator(EMPHASIZED).setDuration(duration).withLayer().start();
    }

    public static void pageIn(View view) {
        if (view == null) return;
        long duration = duration(view, 150);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 3f));
        view.animate().alpha(1f).translationY(0f)
                .setInterpolator(STANDARD).setDuration(duration).start();
    }

    public static void messageIn(View view, boolean fromUser) {
        if (view == null) return;
        long duration = duration(view, 155);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 4f));
        view.animate().alpha(1f).translationY(0f)
                .setInterpolator(STANDARD).setDuration(duration).start();
    }

    public static void toolIn(View view) {
        if (view == null) return;
        long duration = duration(view, 240);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(0f);
        view.setTranslationX(0f);
        view.setTranslationY(dp(view, 3f));
        view.animate().alpha(1f).translationX(0f).translationY(0f)
                .setInterpolator(STANDARD).setDuration(duration).withLayer().start();
    }

    public static void contentUpdated(View view, boolean emphasized) {
        if (view == null) return;
        long duration = duration(view, emphasized ? 210 : 120);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(emphasized ? .88f : .96f);
        view.setTranslationY(0f);
        view.animate().alpha(1f)
                .setInterpolator(STANDARD).setDuration(duration).start();
    }

    public static void state(View view, float alpha, float scale, long normalDuration) {
        if (view == null) return;
        long duration = duration(view, normalDuration);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        if (duration == 0L) { view.setAlpha(alpha); view.setScaleX(scale); view.setScaleY(scale); return; }
        view.animate().alpha(alpha).scaleX(scale).scaleY(scale)
                .setInterpolator(EMPHASIZED).setDuration(duration).start();
    }

    public static void breathe(View view, float alpha, long normalDuration) {
        if (view == null) return;
        long duration = duration(view, normalDuration);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        if (duration == 0L) { view.setAlpha(1f); return; }
        view.animate().alpha(alpha).setInterpolator(STANDARD).setDuration(duration).start();
    }

    public static void fadeOut(View view, float downDp, long normalDuration, Runnable end) {
        if (view == null) { if (end != null) end.run(); return; }
        long duration = duration(view, normalDuration);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        if (duration == 0L) { view.setAlpha(0f); if (end != null) end.run(); return; }
        view.animate().alpha(0f).translationY(dp(view, downDp))
                .setInterpolator(STANDARD).setDuration(duration).withLayer().withEndAction(end).start();
    }

    public static void dialogIn(View view) {
        if (view == null) return;
        long duration = duration(view, 270);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationY(dp(view, 6f));
        view.animate().alpha(1f).translationY(0f)
                .setInterpolator(EMPHASIZED).setDuration(duration).withLayer().start();
    }

    public static void drawerIn(View scrim, View drawer, float widthDp) {
        if (scrim == null || drawer == null) return;
        long duration = duration(drawer, 260);
        scrim.animate().cancel();
        scrim.animate().setStartDelay(0L);
        drawer.animate().cancel();
        drawer.animate().setStartDelay(0L);
        scrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        if (duration == 0L) { scrim.setAlpha(1f); identity(drawer); return; }
        scrim.setAlpha(0f);
        drawer.setAlpha(.82f);
        drawer.setTranslationX(dp(drawer, -Math.abs(widthDp)));
        drawer.animate().alpha(1f).translationX(0f).setInterpolator(EMPHASIZED)
                .setDuration(duration).withLayer().start();
        scrim.animate().alpha(1f).setInterpolator(STANDARD).setDuration(duration - 35L).start();
    }

    public static void drawerOut(View scrim, View drawer, float widthDp, Runnable end) {
        if (scrim == null || drawer == null) { if (end != null) end.run(); return; }
        long duration = duration(drawer, 205);
        scrim.animate().cancel();
        scrim.animate().setStartDelay(0L);
        drawer.animate().cancel();
        drawer.animate().setStartDelay(0L);
        if (duration == 0L) {
            scrim.setVisibility(View.GONE);
            drawer.setVisibility(View.GONE);
            if (end != null) end.run();
            return;
        }
        scrim.animate().alpha(0f).setInterpolator(STANDARD).setDuration(duration - 25L).start();
        drawer.animate().alpha(.88f).translationX(dp(drawer, -Math.abs(widthDp)))
                .setInterpolator(STANDARD).setDuration(duration).withLayer().withEndAction(() -> {
                    scrim.setVisibility(View.GONE);
                    drawer.setVisibility(View.GONE);
                    drawer.setAlpha(1f);
                    if (end != null) end.run();
                }).start();
    }

    public static void selection(View view, boolean active) {
        if (view == null) return;
        Boolean previous = SELECTED.put(view, active);
        if (previous != null && previous == active) return;
        long duration = duration(view, 140);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setScaleX(1f);view.setScaleY(1f);
        float targetAlpha=active?1f:.92f;
        if(duration==0L){view.setAlpha(targetAlpha);return;}
        view.animate().alpha(targetAlpha).setInterpolator(STANDARD).setDuration(duration).start();
    }

    public static void staggerChildren(ViewGroup parent, int maxChildren) {
        if (parent == null) return;
        int count = parent.getChildCount();
        int start = Math.max(0, count - Math.max(1, maxChildren));
        if (!enabled(parent.getContext())) {
            for (int i = start; i < count; i++) identity(parent.getChildAt(i));
            return;
        }
        for (int i = start; i < count; i++) {
            View child = parent.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(dp(child, 3f));
            child.setScaleX(1f);
            child.setScaleY(1f);
            long delay = Math.min(48L, (long)(i - start) * 8L);
            child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(delay).setDuration(duration(child, 220))
                    .setInterpolator(STANDARD).withLayer().start();
        }
    }

    public static void listItemIn(View view, int position) {
        if (view == null) return;
        Integer previous = LIST_POSITIONS.put(view, position);
        if (previous != null && previous == position) return;
        long duration = duration(view, 190);
        if (duration == 0L) { identity(view); return; }
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(0f);
        view.setTranslationX(0f);
        view.animate().alpha(1f).setStartDelay(Math.min(40L, Math.max(0,position) * 6L))
                .setInterpolator(STANDARD).setDuration(duration).start();
    }

    /**
     * Pops a strip out of the composer (permission mode / reasoning effort) by animating its
     * layout height together with its opacity, so the chat above it follows instead of jumping.
     */
    public static void expandBar(View view, int expandedHeight, Runnable end) {
        if (view == null) { if (end != null) end.run(); return; }
        cancelBarAnimator(view);
        view.setVisibility(View.VISIBLE);
        long duration = duration(view, 215);
        if (duration == 0L) { setBarHeight(view, expandedHeight); view.setAlpha(1f); if (end != null) end.run(); return; }
        view.setAlpha(0f);
        setBarHeight(view, 0);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration).setInterpolator(EMPHASIZED);
        animator.addUpdateListener(a -> {
            float progress = (Float) a.getAnimatedValue();
            setBarHeight(view, Math.round(expandedHeight * progress));
            view.setAlpha(Math.min(1f, progress * 1.7f));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (BAR_ANIMATORS.get(view) != a) return; // cancelled or superseded
                BAR_ANIMATORS.remove(view);
                setBarHeight(view, expandedHeight);
                view.setAlpha(1f);
                if (end != null) end.run();
            }
        });
        BAR_ANIMATORS.put(view, animator);
        animator.start();
    }

    /**
     * Reveals a block that is already part of the hierarchy (tool output, thinking details) by
     * growing it to its own content height; safe to call before the view has been laid out.
     */
    public static void expandBarToContent(View view, Runnable end) {
        expandBarToContent(view, end, true);
    }

    private static void expandBarToContent(View view, Runnable end, boolean retry) {
        if (view == null) { if (end != null) end.run(); return; }
        cancelBarAnimator(view);
        view.setVisibility(View.VISIBLE);
        int width = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
        if (width <= 0) {
            // Not laid out yet: keep it invisible for this frame, then measure once it has a width.
            view.setAlpha(0f);
            setBarHeight(view, 0);
            if (!retry) { setBarHeight(view, ViewGroup.LayoutParams.WRAP_CONTENT); view.setAlpha(1f); if (end != null) end.run(); return; }
            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if (BAR_LAYOUT_LISTENERS.get(view) != this || view.getWidth() <= 0) return;
                    BAR_LAYOUT_LISTENERS.remove(view);
                    ViewTreeObserver observer = view.getViewTreeObserver();
                    if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
                    expandBarToContent(view, end, false);
                }
            };
            BAR_LAYOUT_LISTENERS.put(view, listener);
            view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            return;
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        expandBar(view, view.getMeasuredHeight(), end);
    }

    /** Reverses {@link #expandBar(View, int, Runnable)}; end runs once the strip is gone. */
    public static void collapseBar(View view, Runnable end) {
        if (view == null) { if (end != null) end.run(); return; }
        cancelBarAnimator(view);
        int from = view.getVisibility() == View.VISIBLE ? Math.max(view.getHeight(), 1) : 0;
        long duration = from == 0 ? 0L : duration(view, 150);
        if (duration == 0L) { setBarHeight(view, 0); view.setVisibility(View.GONE); view.setAlpha(1f); if (end != null) end.run(); return; }
        ValueAnimator animator = ValueAnimator.ofInt(from, 0);
        animator.setDuration(duration).setInterpolator(STANDARD);
        animator.addUpdateListener(a -> {
            int height = (Integer) a.getAnimatedValue();
            setBarHeight(view, height);
            view.setAlpha(Math.max(.3f, height / (float) from));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (BAR_ANIMATORS.get(view) != a) return; // cancelled or superseded
                BAR_ANIMATORS.remove(view);
                setBarHeight(view, 0);
                view.setVisibility(View.GONE);
                view.setAlpha(1f);
                if (end != null) end.run();
            }
        });
        BAR_ANIMATORS.put(view, animator);
        animator.start();
    }

    private static void cancelBarAnimator(View view) {
        ViewTreeObserver.OnGlobalLayoutListener listener = BAR_LAYOUT_LISTENERS.remove(view);
        ViewTreeObserver observer = view.getViewTreeObserver();
        if (listener != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        ValueAnimator animator = BAR_ANIMATORS.remove(view);
        if (animator != null) animator.cancel();
    }

    private static void setBarHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height == height) return;
        params.height = height;
        view.setLayoutParams(params);
    }

    public static void setTextCrossfade(TextView view, CharSequence value) {
        if (view == null) return;
        CharSequence next = value == null ? "" : value;
        if (String.valueOf(view.getText()).contentEquals(next)) return;
        long duration = duration(view, 170);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setTranslationY(0f);
        if (duration == 0L) { view.setAlpha(1f); view.setText(next); return; }
        view.animate().alpha(.72f).setDuration(duration / 2L)
                .setInterpolator(STANDARD).withEndAction(() -> {
                    view.setText(next);
                    view.animate().alpha(1f).setDuration(duration / 2L)
                            .setInterpolator(STANDARD).start();
                }).start();
    }

    /** Adds uniform press feedback without replacing click listeners. */
    public static void bindInteractive(View root) {
        if (root == null) return;
        if (root.hasOnClickListeners() && !BOUND.containsKey(root)) {
            BOUND.put(root, Boolean.TRUE);
            final float[] pressedAlpha = {-1f};
            root.setOnTouchListener((view, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    pressedAlpha[0] = -1f;
                    if (!view.isEnabled() || view.getTranslationX() != 0f
                            || view.getTranslationY() != 0f || view.getAlpha() < .9f) return false;
                    long d = duration(view, 65);
                    if (d > 0L) {
                        pressedAlpha[0] = view.getAlpha();
                        view.animate().cancel();
                        view.animate().alpha(pressedAlpha[0] * .9f)
                                .setInterpolator(STANDARD).setStartDelay(0L).setDuration(d).start();
                    }
                } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                        && pressedAlpha[0] >= 0f) {
                    float alpha = pressedAlpha[0];
                    pressedAlpha[0] = -1f;
                    long d = duration(view, 120);
                    view.animate().cancel();
                    if (d == 0L) view.setAlpha(alpha);
                    else view.animate().alpha(alpha)
                            .setInterpolator(EMPHASIZED).setStartDelay(0L).setDuration(d).start();
                }
                return false;
            });
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) bindInteractive(group.getChildAt(i));
        }
    }

    /** Low-frequency add/remove transitions; CHANGE_* stays disabled to avoid layout thrash. */
    public static void enableLayoutChanges(ViewGroup group) {
        if (group == null || !enabled(group.getContext())) return;
        LayoutTransition transition = new LayoutTransition();
        transition.disableTransitionType(LayoutTransition.CHANGING);
        transition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        transition.setDuration(LayoutTransition.APPEARING, 145L);
        transition.setDuration(LayoutTransition.DISAPPEARING, 105L);
        transition.setStartDelay(LayoutTransition.APPEARING, 0L);
        transition.setStartDelay(LayoutTransition.DISAPPEARING, 0L);
        transition.setInterpolator(LayoutTransition.APPEARING, STANDARD);
        transition.setInterpolator(LayoutTransition.DISAPPEARING, STANDARD);
        group.setLayoutTransition(transition);
    }

    // ---- Shared primitives: every screen-level animation routes through these ----

    public static TimeInterpolator standard() { return STANDARD; }

    public static TimeInterpolator emphasized() { return EMPHASIZED; }

    /** Tuned duration for call sites that hold no view yet (decor, indicators). */
    public static long durationFor(Context context, long normal) {
        refreshMotionState(context);
        if (!cachedAnimationsEnabled) return 0L;
        long tuned = Math.max(70L, (long) (normal * .78f));
        return cachedPowerSave ? Math.max(70L, (long) (tuned * .72f)) : tuned;
    }

    /** Moves a view to an absolute translationX (tab sliders, toggle knobs). */
    public static void slideToX(View view, float targetX, long normal) {
        if (view == null) return;
        long duration = duration(view, normal);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        if (duration == 0L) { view.setTranslationX(targetX); return; }
        view.animate().translationX(targetX).setInterpolator(EMPHASIZED).setDuration(duration).start();
    }

    /** Moves a view to an absolute translationY (keyboard insets, panel shifts). */
    public static void slideToY(View view, float targetY, long normal) {
        if (view == null) return;
        long duration = duration(view, normal);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        if (duration == 0L) { view.setTranslationY(targetY); return; }
        view.animate().translationY(targetY).setInterpolator(STANDARD).setDuration(duration).start();
    }

    /** Cross-pane slides: incoming pane from +dx, outgoing drifts to -dx while fading. */
    public static void paneIn(View incoming, View outgoing, float dxPx, long normal, Runnable outEnd) {
        if (incoming != null) {
            long duration = duration(incoming, normal);
            incoming.animate().cancel();
            incoming.animate().setStartDelay(0L);
            if (duration == 0L) identity(incoming);
            else incoming.animate().translationX(0f).setInterpolator(EMPHASIZED).setDuration(duration).start();
        }
        if (outgoing != null) {
            long duration = duration(outgoing, normal);
            outgoing.animate().cancel();
            outgoing.animate().setStartDelay(0L);
            if (duration == 0L) { outgoing.setAlpha(0f); if (outEnd != null) outEnd.run(); return; }
            outgoing.animate().alpha(0f).translationX(-dxPx * .55f)
                    .setInterpolator(STANDARD).setDuration(duration).withLayer()
                    .withEndAction(() -> { if (outEnd != null) outEnd.run(); }).start();
        }
    }

    /** Shows a hidden strip with a quick fade+slide (floating controllers, action rows). */
    public static void fadeSlideIn(View view, float fromDx, float fromDy, long normal) {
        if (view == null) return;
        long duration = duration(view, normal);
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setVisibility(View.VISIBLE);
        if (duration == 0L) { identity(view); return; }
        view.setAlpha(0f);
        view.setTranslationX(fromDx);
        view.setTranslationY(fromDy);
        view.animate().alpha(1f).translationX(0f).translationY(0f)
                .setInterpolator(STANDARD).setDuration(duration).withLayer().start();
    }
}
