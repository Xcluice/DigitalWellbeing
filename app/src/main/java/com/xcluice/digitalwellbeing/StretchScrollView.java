package com.xcluice.digitalwellbeing;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ScrollView;

/**
 * ScrollView with the Android 12 "stretch" overscroll: dragging past the top or bottom stretches the
 * content like rubber, and it springs back on release. Also gives a small stretch when a fling hits an edge.
 */
public class StretchScrollView extends ScrollView {
    private final float d;
    private float lastY, raw, curS;
    private int dir = 1; // +1 stretching from the top edge, -1 from the bottom edge
    private boolean touching;
    private ValueAnimator anim;

    public StretchScrollView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private int maxScroll() {
        View c = getChildAt(0);
        if (c == null) return 0;
        return Math.max(0, c.getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom()));
    }

    private void apply(float s, int dirNow) {
        View c = getChildAt(0);
        if (c == null) return;
        curS = s;
        dir = dirNow;
        c.setPivotX(c.getWidth() / 2f);
        c.setPivotY(dirNow > 0 ? 0f : c.getHeight());
        c.setScaleY(1f + s);
    }

    /** Rubber-band curve: more pull gives less and less extra stretch. */
    private float stretchFor(float pull) {
        float x = Math.abs(pull) / Math.max(1, getHeight());
        return 0.25f * x / (x + 0.5f);
    }

    private float pullFor(float s, int dirNow) {
        if (s <= 0f || s >= 0.24f) return 0f;
        float x = 0.5f * s / (0.25f - s);
        return dirNow * x * getHeight();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = super.onTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                lastY = ev.getY();
                if (anim != null) anim.cancel();
                raw = pullFor(curS, dir); // grabbing it mid spring-back keeps the current stretch
                break;
            case MotionEvent.ACTION_MOVE: {
                float y = ev.getY();
                float dy = y - lastY;
                lastY = y;
                if (raw == 0f) {
                    if (dy > 0 && getScrollY() <= 0) raw += dy;
                    else if (dy < 0 && getScrollY() >= maxScroll()) raw += dy;
                } else {
                    raw += dy;
                    if ((dir > 0 && raw < 0) || (dir < 0 && raw > 0)) raw = 0f;
                }
                if (raw != 0f) apply(stretchFor(raw), raw > 0 ? 1 : -1);
                else if (curS != 0f) apply(0f, dir);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                raw = 0f;
                if (curS != 0f) release();
                break;
            default:
                break;
        }
        return handled;
    }

    private void release() {
        if (anim != null) anim.cancel();
        final float from = curS;
        final int dd = dir;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(560);
        anim.setInterpolator(new OvershootInterpolator(2.4f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                apply(from * (1f - (Float) a.getAnimatedValue()), dd);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { apply(0f, dd); }
        });
        anim.start();
    }

    /** Quick stretch-and-settle, used when a fling runs into an edge. */
    private void kick(final float amp, final int dd) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(480);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                apply(amp * (float) Math.sin(Math.PI * t) * (1f - 0.35f * t), dd);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { apply(0f, dd); }
        });
        anim.start();
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (touching || getChildCount() == 0) return;
        int delta = Math.abs(t - ot);
        if (delta <= 4 * d) return;
        int max = maxScroll();
        float amp = Math.min(0.08f, 0.012f + delta / (60f * d) * 0.04f);
        if (t <= 0 && ot > 0) kick(amp, 1);
        else if (max > 0 && t >= max && ot < max) kick(amp, -1);
    }

    @Override
    public void fling(int velocityY) {
        super.fling(velocityY);
        if (getChildCount() == 0) return;
        float amp = Math.min(0.08f, 0.01f + Math.abs(velocityY) / (d * 25000f));
        if (velocityY < 0 && getScrollY() <= 0) kick(amp, 1);
        else if (velocityY > 0 && getScrollY() >= maxScroll()) kick(amp, -1);
    }
}
