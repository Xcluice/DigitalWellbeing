package com.xcluice.digitalwellbeing;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DWWidget extends AppWidgetProvider {
    private static final int[] ROW = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5};
    private static final int[] PILL = {R.id.pill1, R.id.pill2, R.id.pill3, R.id.pill4, R.id.pill5};
    private static final int[] PILLBG = {R.id.pillbg1, R.id.pillbg2, R.id.pillbg3, R.id.pillbg4, R.id.pillbg5};
    private static final int[] ICON = {R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5};

    static final String ACTION_REFRESH = "com.xcluice.digitalwellbeing.REFRESH";

    @Override
    public void onReceive(final Context c, Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) {
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                @Override public void run() {
                    try { refreshAll(c); } finally { pr.finish(); }
                }
            }).start();
        } else {
            super.onReceive(c, i);
        }
    }

    @Override
    public void onUpdate(final Context c, final AppWidgetManager m, final int[] ids) {
        run(c, m, ids);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        run(c, m, new int[]{id});
    }

    private void run(final Context c, final AppWidgetManager m, final int[] ids) {
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

    /** Readable text colour for a given background. */
    private static int on(int bg) {
        double l = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return l > 0.6 ? 0xFF1B1C1A : 0xFFFFFFFF;
    }

    private static Bitmap roundIcon(Drawable icon) {
        int s = 96;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Path clip = new Path();
        clip.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW);
        cv.clipPath(clip);
        icon.setBounds(0, 0, s, s);
        icon.draw(cv);
        return b;
    }

    static class Row { String pkg; long ms; }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        long total = -1;
        List<Row> top = new ArrayList<>();
        try {
            if (Usage.hasAccess(c)) {
                total = 0;
                for (Map.Entry<String, Long> en : Usage.today(c).entrySet()) {
                    total += en.getValue();
                    if (en.getValue() >= 60000) {
                        Row r = new Row();
                        r.pkg = en.getKey();
                        r.ms = en.getValue();
                        top.add(r);
                    }
                }
                Collections.sort(top, new Comparator<Row>() {
                    @Override public int compare(Row a, Row b) { return Long.compare(b.ms, a.ms); }
                });
            }
        } catch (Exception ignored) {}
        if (top.size() > 5) top = new ArrayList<>(top.subList(0, 5));

        PackageManager pm = c.getPackageManager();
        Bitmap[] icons = new Bitmap[top.size()];
        String[] times = new String[top.size()];
        for (int i = 0; i < top.size(); i++) {
            try {
                icons[i] = roundIcon(pm.getApplicationIcon(top.get(i).pkg));
            } catch (Exception e) {
                icons[i] = null;
            }
            times[i] = Usage.fmtShort(top.get(i).ms);
        }

        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent refresh = PendingIntent.getBroadcast(c, 1,
                new Intent(c, DWWidget.class).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean portrait = c.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;

        // colours follow the theme and accent picked in the app
        Themes.T th = Themes.get(c);
        int acc = Usage.accent(c);
        int[] pbg = {th.text, th.grid, acc, acc, acc};
        int[] pfg = new int[5];
        for (int i = 0; i < 5; i++) pfg[i] = on(pbg[i]);

        for (int id : ids) {
            Bundle o = m.getAppWidgetOptions(id);
            int w = portrait ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250);
            int h = portrait ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 90)
                    : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90);
            int rows = Math.max(1, Math.min(5, (h - 20 + 3) / 23));
            rows = Math.min(rows, top.size());

            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setInt(R.id.wbg, "setColorFilter", th.bg);
            rv.setTextColor(R.id.title, th.text);
            rv.setTextColor(R.id.time, th.text);
            rv.setInt(R.id.chartic, "setColorFilter", th.sub);
            for (int i = 0; i < 5; i++) {
                rv.setInt(PILLBG[i], "setColorFilter", pbg[i]);
                rv.setTextColor(PILL[i], pfg[i]);
            }
            rv.setTextViewText(R.id.time, total < 0 ? "Allow access" : Usage.fmtShort(total));
            rv.setTextViewTextSize(R.id.time, TypedValue.COMPLEX_UNIT_SP,
                    total < 0 ? 18 : (w < 200 ? 26 : (h < 130 ? 34 : 40)));
            for (int i = 0; i < 5; i++) {
                if (i < rows) {
                    rv.setViewVisibility(ROW[i], View.VISIBLE);
                    rv.setTextViewText(PILL[i], times[i]);
                    if (icons[i] != null) rv.setImageViewBitmap(ICON[i], icons[i]);
                } else {
                    rv.setViewVisibility(ROW[i], View.GONE);
                }
            }
            rv.setOnClickPendingIntent(R.id.root, pi);
            rv.setOnClickPendingIntent(R.id.left, refresh);
            m.updateAppWidget(id, rv);
        }
    }
}
