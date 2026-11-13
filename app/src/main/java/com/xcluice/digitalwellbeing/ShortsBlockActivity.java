package com.xcluice.digitalwellbeing;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

/** Full screen shown when the daily Shorts limit is reached. Grey / white / black only. */
public class ShortsBlockActivity extends Activity {
    private ValueAnimator pulse;

    /** A vertical clip with a play triangle, pulsing rings and a slash that draws itself in. */
    private static class LockIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tri = new Path();
        private final RectF r = new RectF();
        private final int fg, bg, ring;
        private final float d;
        float pulseT, slash;

        LockIcon(Context c, int fg, int bg, int ring) {
            super(c);
            this.fg = fg;
            this.bg = bg;
            this.ring = ring;
            this.d = c.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f, s = Math.min(w, h);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            for (int i = 0; i < 2; i++) {
                float ph = (pulseT + i * 0.5f) % 1f;
                p.setColor(ring);
                p.setAlpha((int) ((1f - ph) * 150));
                cv.drawCircle(cx, cy, s * 0.30f + ph * s * 0.20f, p);
            }
            p.setAlpha(255);

            float rw = s * 0.30f, rh = s * 0.50f;
            r.set(cx - rw / 2f, cy - rh / 2f, cx + rw / 2f, cy + rh / 2f);
            p.setColor(fg);
            p.setStrokeWidth(3.5f * d);
            cv.drawRoundRect(r, s * 0.07f, s * 0.07f, p);

            float ts = s * 0.075f;
            tri.reset();
            tri.moveTo(cx - ts * 0.7f, cy - ts);
            tri.lineTo(cx - ts * 0.7f, cy + ts);
            tri.lineTo(cx + ts * 1.1f, cy);
            tri.close();
            p.setStyle(Paint.Style.FILL);
            cv.drawPath(tri, p);

            if (slash > 0f) {
                float x0 = cx - s * 0.26f, y0 = cy - s * 0.30f;
                float x1 = cx + s * 0.26f, y1 = cy + s * 0.30f;
                float ex = x0 + (x1 - x0) * slash, ey = y0 + (y1 - y0) * slash;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(bg);
                p.setStrokeWidth(11f * d);
                cv.drawLine(x0, y0, ex, ey, p);
                p.setColor(fg);
                p.setStrokeWidth(4f * d);
                cv.drawLine(x0, y0, ex, ey, p);
                p.setStrokeCap(Paint.Cap.BUTT);
            }
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final Themes.T th = Themes.get(this);
        Themes.applyWindow(this);
        final float d = getResources().getDisplayMetrics().density;
        int count = Usage.shortsCount(this);
        int limit = Usage.shortsLimit(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding((int) (32 * d), (int) (80 * d), (int) (32 * d), (int) (32 * d));

        final LockIcon icon = new LockIcon(this, th.text, th.bg, th.grid);
        root.addView(icon, new LinearLayout.LayoutParams((int) (170 * d), (int) (170 * d)));

        final TextView big = new TextView(this);
        big.setText("0");
        big.setTextColor(th.text);
        big.setTextSize(52);
        big.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bgp = new LinearLayout.LayoutParams(-2, -2);
        bgp.topMargin = (int) (6 * d);
        root.addView(big, bgp);
        TextView cap = new TextView(this);
        cap.setText("Shorts watched today");
        cap.setTextColor(th.sub);
        cap.setTextSize(13);
        root.addView(cap, new LinearLayout.LayoutParams(-2, -2));

        TextView t = new TextView(this);
        t.setText("Shorts are done for today");
        t.setTextColor(th.text);
        t.setTextSize(26);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = (int) (24 * d);
        root.addView(t, tp);

        TextView s = new TextView(this);
        s.setText("That's your limit for today. Time for something with a beginning, a middle and an end.\n\n"
                + "Regular videos still work. Shorts unlock again at midnight.");
        s.setTextColor(th.sub);
        s.setTextSize(15);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = (int) (14 * d);
        root.addView(s, sp);

        TextView ok = new TextView(this);
        ok.setText("Got it");
        ok.setTextColor(th.bg);
        ok.setTextSize(16);
        ok.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.text);
        g.setCornerRadius(26 * d);
        ok.setBackground(g);
        ok.setPadding((int) (56 * d), (int) (14 * d), (int) (56 * d), (int) (14 * d));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2);
        op.topMargin = (int) (44 * d);
        root.addView(ok, op);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(th.bg);
        frame.addView(new Fx.Particles(this, th.text, 26), new android.widget.FrameLayout.LayoutParams(-1, -1));
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        Fx.press(ok);
        Fx.countUp(big, 0, Math.max(count, limit), 1200, "");

        // Motion: icon pops in, rings pulse, slash draws across, text rises in one by one.
        DecelerateInterpolator decel = new DecelerateInterpolator(1.8f);
        icon.setAlpha(0f);
        icon.setScaleX(0.5f);
        icon.setScaleY(0.5f);
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();

        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2200);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                icon.pulseT = (float) a.getAnimatedValue();
                icon.invalidate();
            }
        });
        pulse.start();

        ValueAnimator sl = ValueAnimator.ofFloat(0f, 1f);
        sl.setStartDelay(500);
        sl.setDuration(450);
        sl.setInterpolator(decel);
        sl.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                icon.slash = (float) a.getAnimatedValue();
                icon.invalidate();
            }
        });
        sl.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) {
                // The slash lands: a quick shake, then the icon settles into a gentle float.
                android.animation.ObjectAnimator sh = android.animation.ObjectAnimator.ofFloat(icon, "translationX",
                        0f, -14 * d, 14 * d, -9 * d, 9 * d, -4 * d, 0f);
                sh.setDuration(420);
                sh.start();
                Fx.loop(icon, "translationY", -6 * d, 6 * d, 1900);
                Fx.loop(icon, "rotation", -3f, 3f, 2700);
            }
        });
        sl.start();

        View[] rise = {big, cap, t, s, ok};
        for (int i = 0; i < rise.length; i++) {
            rise[i].setAlpha(0f);
            rise[i].setTranslationY(22 * d);
            rise[i].animate().alpha(1f).translationY(0f).setStartDelay(300 + i * 110L)
                    .setDuration(460).setInterpolator(i == rise.length - 1 ? new OvershootInterpolator(1.6f) : decel).start();
        }
    }

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        super.onDestroy();
    }
}
