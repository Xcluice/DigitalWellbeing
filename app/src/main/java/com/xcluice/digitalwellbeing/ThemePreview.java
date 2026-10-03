package com.xcluice.digitalwellbeing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A tiny mock of the dashboard drawn in a theme's colours. */
public class ThemePreview extends View {
    private final Themes.T t;
    private final boolean selected;
    private final int accent;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private static final float[] HS = {0.35f, 0.6f, 0.45f, 0.95f, 0.7f, 0.3f, 0.55f};

    public ThemePreview(Context c, Themes.T t, boolean selected, int accent) {
        super(c);
        this.t = t;
        this.selected = selected;
        this.accent = accent;
        d = c.getResources().getDisplayMetrics().density;
    }

    private void pill(Canvas c, float l, float tp, float r, float b, float rad, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        rf.set(l, tp, r, b);
        c.drawRoundRect(rf, rad, rad, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = 14 * d;
        pill(c, 0, 0, w, h, rad, t.bg);
        float inset = selected ? 1.5f * d : 0.5f * d;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(selected ? 3 * d : d);
        p.setColor(selected ? accent : t.div);
        rf.set(inset, inset, w - inset, h - inset);
        c.drawRoundRect(rf, rad, rad, p);

        float pad = w * 0.12f;
        pill(c, w / 2 - w * 0.22f, h * 0.10f, w / 2 + w * 0.22f, h * 0.10f + 6 * d, 3 * d, accent);
        pill(c, w / 2 - w * 0.12f, h * 0.10f + 10 * d, w / 2 + w * 0.12f, h * 0.10f + 13 * d, 2 * d, t.sub);

        float baseY = h * 0.58f, maxH = h * 0.26f, slot = (w - 2 * pad) / 7f, bw = slot * 0.62f;
        pill(c, pad, baseY, w - pad, baseY + d, 0, t.grid);
        for (int i = 0; i < 7; i++) {
            float cx = pad + slot * (i + 0.5f);
            pill(c, cx - bw / 2, baseY - HS[i] * maxH, cx + bw / 2, baseY, 2 * d,
                    ChartView.barColor(HS[i] * 6f, 1, 6, 255));
        }
        for (int k = 0; k < 2; k++) {
            float y = h * 0.69f + k * h * 0.13f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(t.sub);
            c.drawCircle(pad + 5 * d, y + 5 * d, 5 * d, p);
            pill(c, pad + 16 * d, y + d, pad + 16 * d + w * 0.34f, y + 5 * d, 2 * d, t.text);
            pill(c, pad + 16 * d, y + 8 * d, pad + 16 * d + w * 0.2f, y + 11 * d, 2 * d, t.sub);
        }
        if (selected) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(accent);
            c.drawCircle(w - 15 * d, 15 * d, 9 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFF1F1F1F);
            c.drawLine(w - 19 * d, 15 * d, w - 16 * d, 18 * d, p);
            c.drawLine(w - 16 * d, 18 * d, w - 11 * d, 12 * d, p);
            p.setStrokeCap(Paint.Cap.BUTT);
        }
    }
}
