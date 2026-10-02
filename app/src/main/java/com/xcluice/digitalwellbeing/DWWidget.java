package com.xcluice.digitalwellbeing;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
    private static final int[] NAME = {R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5};

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
        String[] names = new String[top.size()];
        String[] times = new String[top.size()];
        for (int i = 0; i < top.size(); i++) {
            try {
                names[i] = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(top.get(i).pkg, 0)));
            } catch (Exception e) {
                names[i] = top.get(i).pkg;
            }
            times[i] = Usage.fmtShort(top.get(i).ms);
        }

        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean portrait = c.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;

        for (int id : ids) {
            Bundle o = m.getAppWidgetOptions(id);
            int w = portrait ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250);
            int h = portrait ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 90)
                    : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90);
            int rows = Math.max(1, Math.min(5, (h - 20 + 3) / 23));
            rows = Math.min(rows, top.size());

            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.time, total < 0 ? "Allow access" : Usage.fmtShort(total));
            rv.setTextViewTextSize(R.id.time, TypedValue.COMPLEX_UNIT_SP,
                    total < 0 ? 18 : (w < 200 ? 26 : (h < 130 ? 34 : 40)));
            for (int i = 0; i < 5; i++) {
                if (i < rows) {
                    rv.setViewVisibility(ROW[i], View.VISIBLE);
                    rv.setTextViewText(PILL[i], times[i]);
                    rv.setTextViewText(NAME[i], names[i]);
                } else {
                    rv.setViewVisibility(ROW[i], View.GONE);
                }
            }
            rv.setOnClickPendingIntent(R.id.root, pi);
            m.updateAppWidget(id, rv);
        }
    }
}
