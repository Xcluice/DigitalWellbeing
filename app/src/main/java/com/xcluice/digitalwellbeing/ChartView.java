package com.xcluice.digitalwellbeing;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** Weekly bar chart. Allocation-free while drawing so the grow animation stays smooth on slow phones. */
public class ChartView extends View {
    interface Listener { void onDay(int i); }

    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private final float d;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.6f);
    private final String[] yLabels = {"0h", "2h", "4h", "6h"};
    private final int[] barCol = new int[7];
    private final float[] heightFrac = new float[7];
    private long[] totals = new long[7];
    private boolean[] future = new boolean[7];
    private int selected, topH = 6;
    private int lowH = 1, highH = 6;
    private float left, right, top, grid, base;
    private float prog = 1f;
    private ValueAnimator va;
    private Listener listener;

    public ChartView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setColor(0xFF5F6368);
        line.setStrokeWidth(d);
        txt.setColor(0xFF9AA0A6);
        txt.setTextSize(11 * d);
        day.setColor(0xFFBDC1C6);
        day.setTextSize(12 * d);
        day.setTextAlign(Paint.Align.CENTER);
    }

    void setThresholds(int low, int high) {
        lowH = low;
        highH = high;
        recolor();
    }

    /** green up to lowH hours, red from highH hours, blends through yellow in between. */
    static int barColor(float hrs, int low, int high, int alpha) {
        float t = hrs <= low ? 0f : (hrs >= high ? 1f : (hrs - low) / (float) (high - low));
        return android.graphics.Color.HSVToColor(alpha, new float[]{120f * (1f - t), 0.72f, 0.96f});
    }

    private void recolor() {
        for (int i = 0; i < 7; i++) barCol[i] = barColor(totals[i] / 3600000f, lowH, highH, 255);
    }

    void set(long[] t, boolean[] f, int sel, Listener l) {
        totals = t;
        future = f;
        selected = sel;
        listener = l;
        double max = 0;
        for (int i = 0; i < 7; i++) if (!f[i]) max = Math.max(max, t[i] / 3600000.0);
        topH = max <= 6 ? 6 : (int) (Math.ceil(max / 6) * 6);
        for (int k = 0; k <= 3; k++) yLabels[k] = (topH * k / 3) + "h";
        for (int i = 0; i < 7; i++) heightFrac[i] = (float) (t[i] / 3600000.0 / topH);
        recolor();
        invalidate();
    }

    /** Bars rise one after another. */
    void grow() {
        if (va != null) va.cancel();
        prog = 0f;
        va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(700);
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                prog = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        va.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (124 * d));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        left = 15 * d;
        right = w - 34 * d;
        top = 8 * d;
        grid = 88 * d;
        base = top + grid;
    }

    @Override
    protected void onDraw(Canvas c) {
        for (int k = 0; k <= 3; k++) {
            float y = base - grid * k / 3f;
            c.drawLine(left, y, right, y, line);
            c.drawText(yLabels[k], right + 8 * d, y + 4 * d, txt);
        }
        float slot = (right - left) / 7f, bw = slot * 0.66f, r = 4 * d;
        c.save();
        c.clipRect(0, 0, getWidth(), base + 1);
        for (int i = 0; i < 7; i++) {
            float cx = left + slot * (i + 0.5f);
            if (future[i] || totals[i] <= 0) continue;
            float t = prog * 1.5f - i * 0.08f;
            t = t < 0f ? 0f : (t > 1f ? 1f : t);
            if (t <= 0f) continue;
            float h = heightFrac[i] * grid;
            if (h < 3 * d) h = 3 * d;
            h *= decel.getInterpolation(t);
            bar.setColor(barCol[i]);
            bar.setAlpha(i == selected ? 255 : 150);
            rf.set(cx - bw / 2, base - h, cx + bw / 2, base + r);
            c.drawRoundRect(rf, r, r, bar);
        }
        c.restore();
        for (int i = 0; i < 7; i++) {
            c.drawText(DAYS[i], left + slot * (i + 0.5f), base + 20 * d, day);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            float slot = (right - left) / 7f;
            int i = (int) ((ev.getX() - left) / slot);
            if (i >= 0 && i < 7 && !future[i] && listener != null) listener.onDay(i);
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
