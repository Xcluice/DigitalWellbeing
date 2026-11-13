package com.xcluice.digitalwellbeing;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.util.Random;

/** Small motion toolkit shared by the screens. Everything here is drawn with the theme's greys. */
final class Fx {
    private Fx() {}

    /** Squash on touch, springy bounce back on release. Does not swallow clicks. */
    static void press(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View x, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        x.animate().scaleX(1f).scaleY(1f).setDuration(380)
                                .setInterpolator(new OvershootInterpolator(4f)).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** Endless back-and-forth on one property (float, bob, sway, breathe). Stops when the view detaches. */
    static ObjectAnimator loop(final View v, String prop, float a, float b, long ms) {
        final ObjectAnimator o = ObjectAnimator.ofFloat(v, prop, a, b);
        o.setDuration(ms);
        o.setRepeatCount(ValueAnimator.INFINITE);
        o.setRepeatMode(ValueAnimator.REVERSE);
        o.setInterpolator(new AccelerateDecelerateInterpolator());
        o.start();
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) {}
            @Override public void onViewDetachedFromWindow(View x) { o.cancel(); }
        });
        return o;
    }

    /** Rises in from below while fading in. */
    static void rise(View v, float distPx, long delay, long dur) {
        v.setAlpha(0f);
        v.setTranslationY(distPx);
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(dur)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    static void countUp(final TextView t, int from, int to, long ms, final String suffix) {
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(Math.max(1, ms));
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                t.setText(an.getAnimatedValue() + suffix);
            }
        });
        a.start();
    }

    /** Expanding fading rings, like a calm sonar pulse. */
    static class Rings extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final float d;
        private float t;
        private ValueAnimator a;

        Rings(Context c, int color) {
            super(c);
            this.color = color;
            this.d = c.getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(2600);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new LinearInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator an) {
                    t = (float) an.getAnimatedValue();
                    invalidate();
                }
            });
            a.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (a != null) a.cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight());
            for (int i = 0; i < 3; i++) {
                float ph = (t + i / 3f) % 1f;
                p.setColor(color);
                p.setAlpha((int) ((1f - ph) * 130));
                cv.drawCircle(getWidth() / 2f, getHeight() / 2f, s * 0.22f + ph * s * 0.28f, p);
            }
        }
    }

    /** Slowly drifting dots and tiny clips that float up the screen and fade at the edges. */
    static class Particles extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final int color, n;
        private final float d;
        private float[] x, y, r, v, ph;
        private long last;
        private float clock;
        private ValueAnimator a;

        Particles(Context c, int color, int n) {
            super(c);
            this.color = color;
            this.n = n;
            this.d = c.getResources().getDisplayMetrics().density;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            Random rnd = new Random(7);
            x = new float[n];
            y = new float[n];
            r = new float[n];
            v = new float[n];
            ph = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = rnd.nextFloat() * w;
                y[i] = rnd.nextFloat() * h;
                r[i] = (2f + rnd.nextFloat() * 4f) * d;
                v[i] = (8f + rnd.nextFloat() * 22f) * d;
                ph[i] = rnd.nextFloat() * 6.28f;
            }
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(1000);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new LinearInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator an) { step(); }
            });
            last = 0;
            a.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (a != null) a.cancel();
            super.onDetachedFromWindow();
        }

        private void step() {
            if (y == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(50, now - last) / 1000f;
            last = now;
            clock += dt;
            int h = getHeight(), w = getWidth();
            for (int i = 0; i < n; i++) {
                y[i] -= v[i] * dt;
                if (y[i] < -r[i] * 3) {
                    y[i] = h + r[i] * 3;
                    x[i] = (float) Math.random() * w;
                }
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            if (y == null) return;
            float h = getHeight();
            p.setColor(color);
            for (int i = 0; i < n; i++) {
                float f = Math.max(0f, Math.min(1f, y[i] / h));
                p.setAlpha((int) (Math.sin(Math.PI * f) * 90));
                float px = x[i] + (float) Math.sin(clock * 0.8f + ph[i]) * 12f * d;
                if (i % 3 == 0) {
                    rf.set(px - r[i] * 0.8f, y[i] - r[i] * 1.4f, px + r[i] * 0.8f, y[i] + r[i] * 1.4f);
                    cv.drawRoundRect(rf, r[i] * 0.4f, r[i] * 0.4f, p);
                } else {
                    cv.drawCircle(px, y[i], r[i], p);
                }
            }
        }
    }
}
