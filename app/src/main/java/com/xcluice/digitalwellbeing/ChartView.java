package com.xcluice.digitalwellbeing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class ChartView extends View {
    interface Listener { void onDay(int i); }

    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private final float d;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private long[] vals = new long[7];
    private boolean[] future = new boolean[7];
    private int selected;
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

    void set(long[] v, boolean[] f, int sel, Listener l) {
        vals = v; future = f; selected = sel; listener = l;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (124 * d));
    }

    private float[] geo() {
        float left = 15 * d, right = getWidth() - 34 * d, top = 8 * d, grid = 88 * d;
        return new float[]{left, right, top, grid, top + grid};
    }

    @Override
    protected void onDraw(Canvas c) {
        float[] g = geo();
        float left = g[0], right = g[1], grid = g[3], base = g[4];
        double max = 0;
        for (int i = 0; i < 7; i++) if (!future[i]) max = Math.max(max, vals[i] / 3600000.0);
        int topH = max <= 6 ? 6 : (int) (Math.ceil(max / 6) * 6);
        for (int k = 0; k <= 3; k++) {
            float y = base - grid * k / 3f;
            c.drawLine(left, y, right, y, line);
            c.drawText((topH * k / 3) + "h", right + 8 * d, y + 4 * d, txt);
        }
        float slot = (right - left) / 7f, bw = slot * 0.66f, r = 4 * d;
        for (int i = 0; i < 7; i++) {
            float cx = left + slot * (i + 0.5f);
            c.drawText(DAYS[i], cx, base + 20 * d, day);
            if (future[i] || vals[i] <= 0) continue;
            float h = (float) (vals[i] / 3600000.0 / topH) * grid;
            if (h < 3 * d) h = 3 * d;
            bar.setColor(i == selected ? 0xFFAECBFA : 0xFFF1F3F4);
            c.save();
            c.clipRect(0, 0, getWidth(), base);
            rf.set(cx - bw / 2, base - h, cx + bw / 2, base + r);
            c.drawRoundRect(rf, r, r, bar);
            c.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            float[] g = geo();
            float slot = (g[1] - g[0]) / 7f;
            int i = (int) ((ev.getX() - g[0]) / slot);
            if (i >= 0 && i < 7 && !future[i] && listener != null) listener.onDay(i);
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
