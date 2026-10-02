package com.xcluice.digitalwellbeing;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.RemoteViews;

public class DWWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(final Context c, final AppWidgetManager m, final int[] ids) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try { update(c, m, ids); } finally { pr.finish(); }
            }
        }).start();
    }

    /** Call from a background thread. */
    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, DWWidget.class));
        if (ids.length > 0) update(c, m, ids);
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        long total = -1;
        try { if (Usage.hasAccess(c)) total = Usage.todayTotal(c); } catch (Exception ignored) {}
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
        rv.setImageViewBitmap(R.id.ring, ring(total));
        rv.setTextViewText(R.id.time, total < 0 ? "Allow access" : Usage.fmtTotal(total));
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.root, pi);
        for (int id : ids) m.updateAppWidget(id, rv);
    }

    private static Bitmap ring(long total) {
        int s = 220;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(24);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF r = new RectF(20, 20, s - 20, s - 20);
        p.setColor(0xFF3C4043);
        cv.drawArc(r, 0, 360, false, p);
        if (total > 0) {
            float f = Math.min(1f, total / (6f * 3600000f));
            p.setColor(0xFFAECBFA);
            cv.drawArc(r, -90, Math.max(f * 360f, 6f), false, p);
        }
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFFFFFFFF);
        t.setTextSize(36);
        t.setTextAlign(Paint.Align.CENTER);
        cv.drawText("Today", s / 2f, s / 2f + 13, t);
        return b;
    }
}
