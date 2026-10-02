package com.xcluice.digitalwellbeing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 24 bars, one per hour, for a single app on a single day. */
public class HourChart extends View {
    private final float d;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lab = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private long[] hours = new long[24];
    private int color = 0xFF8AB4F8;
    private int peak = -1;

    public HourChart(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setColor(0xFF5F6368);
        line.setStrokeWidth(d);
        txt.setColor(0xFF9AA0A6);
        txt.setTextSize(11 * d);
        lab.setColor(0xFFBDC1C6);
        lab.setTextSize(11 * d);
        lab.setTextAlign(Paint.Align.CENTER);
    }

    void set(long[] h, int col) {
        hours = h;
        color = col;
        peak = -1;
        long best = 0;
        for (int i = 0; i < 24; i++) if (h[i] > best) { best = h[i]; peak = i; }
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (170 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float left = 16 * d, right = getWidth() - 40 * d, top = 8 * d, grid = 130 * d, base = top + grid;
        long max = 0;
        for (long v : hours) max = Math.max(max, v);
        int topMin = max <= 15 * 60000L ? 15 : (max <= 30 * 60000L ? 30 : 60);
        for (int k = 0; k <= 3; k++) {
            float y = base - grid * k / 3f;
            c.drawLine(left, y, right, y, line);
            c.drawText((topMin * k / 3) + "m", right + 8 * d, y + 4 * d, txt);
        }
        float slot = (right - left) / 24f, bw = slot * 0.62f, r = 3 * d;
        String[] names = {"12a", "6a", "12p", "6p"};
        for (int i = 0; i < 4; i++) {
            float cx = left + slot * (i * 6 + 0.5f);
            c.drawText(names[i], cx, base + 20 * d, lab);
        }
        for (int i = 0; i < 24; i++) {
            if (hours[i] <= 0) continue;
            float cx = left + slot * (i + 0.5f);
            float h = (float) (hours[i] / 60000.0 / topMin) * grid;
            if (h > grid) h = grid;
            if (h < 3 * d) h = 3 * d;
            bar.setColor(color);
            bar.setAlpha(i == peak ? 255 : 170);
            c.save();
            c.clipRect(0, 0, getWidth(), base);
            rf.set(cx - bw / 2, base - h, cx + bw / 2, base + r);
            c.drawRoundRect(rf, r, r, bar);
            c.restore();
        }
    }
}
