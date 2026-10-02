package com.xcluice.digitalwellbeing;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Usage {
    private Usage() {}

    private static final String PREFS = "dw_prefs";
    private static final String KEY = "h";

    static boolean hasAccess(Context c) {
        AppOpsManager a = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int m = a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return m == AppOpsManager.MODE_ALLOWED;
    }

    static Set<String> hidden(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(p.getStringSet(KEY, new HashSet<String>()));
    }

    static void saveHidden(Context c, Set<String> s) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, new HashSet<>(s)).apply();
    }

    static Set<String> launchable(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> l = c.getPackageManager().queryIntentActivities(i, 0);
        Set<String> s = new HashSet<>();
        for (ResolveInfo r : l) s.add(r.activityInfo.packageName);
        s.remove(c.getPackageName());
        return s;
    }

    static Calendar midnight(Calendar in) {
        Calendar c = (Calendar) in.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Sunday 00:00 of the week containing the given date. */
    static Calendar weekStart(Calendar in) {
        Calendar c = midnight(in);
        c.add(Calendar.DAY_OF_YEAR, -(c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY));
        return c;
    }

    /** package -> foreground ms for each of the 7 days (Sun..Sat) of the week starting at weekStartMs. */
    static Map<String, long[]> week(Context c, long weekStartMs) {
        Map<String, long[]> out = new HashMap<>();
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        long[] b = new long[8];
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(weekStartMs);
        for (int i = 0; i < 8; i++) {
            b[i] = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long end = Math.min(now, b[7]);
        if (end <= b[0]) return out;

        Set<String> ok = launchable(c);
        UsageEvents ev = usm.queryEvents(b[0], end);
        UsageEvents.Event e = new UsageEvents.Event();
        Map<String, Set<String>> open = new HashMap<>();
        Map<String, Long> start = new HashMap<>();

        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            int t = e.getEventType();
            long ts = e.getTimeStamp();
            String pkg = e.getPackageName();
            if (t == 1) { // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
                Set<String> s = open.get(pkg);
                if (s == null) { s = new HashSet<>(); open.put(pkg, s); }
                if (s.isEmpty()) start.put(pkg, ts);
                String cls = e.getClassName();
                s.add(cls == null ? "" : cls);
            } else if (t == 2) { // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
                Set<String> s = open.get(pkg);
                if (s == null) continue;
                String cls = e.getClassName();
                s.remove(cls == null ? "" : cls);
                if (s.isEmpty()) {
                    Long st = start.remove(pkg);
                    if (st != null) add(out, ok, pkg, st, ts, b);
                }
            } else if (t == 16 || t == 17) { // SCREEN_NON_INTERACTIVE / KEYGUARD_SHOWN
                for (Map.Entry<String, Long> en : new HashMap<>(start).entrySet()) {
                    add(out, ok, en.getKey(), en.getValue(), ts, b);
                }
                start.clear();
                for (Set<String> s : open.values()) s.clear();
            }
        }
        for (Map.Entry<String, Long> en : start.entrySet()) {
            add(out, ok, en.getKey(), en.getValue(), end, b);
        }
        return out;
    }

    private static void add(Map<String, long[]> out, Set<String> ok, String pkg, long s, long e, long[] b) {
        if (!ok.contains(pkg) || e <= s) return;
        long[] arr = out.get(pkg);
        if (arr == null) { arr = new long[7]; out.put(pkg, arr); }
        for (int i = 0; i < 7; i++) {
            long lo = Math.max(s, b[i]);
            long hi = Math.min(e, b[i + 1]);
            if (hi > lo) arr[i] += hi - lo;
        }
    }

    static long todayTotal(Context c) {
        Calendar now = Calendar.getInstance();
        int idx = now.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        Map<String, long[]> m = week(c, weekStart(now).getTimeInMillis());
        Set<String> h = hidden(c);
        long t = 0;
        for (Map.Entry<String, long[]> en : m.entrySet()) {
            if (!h.contains(en.getKey())) t += en.getValue()[idx];
        }
        return t;
    }

    static String fmtTotal(long ms) {
        long m = ms / 60000, h = m / 60;
        m %= 60;
        if (h > 0 && m > 0) return h + " hr, " + m + " min";
        if (h > 0) return h + " hr";
        return m + " min";
    }

    static String fmtRow(long ms) {
        long m = ms / 60000, h = m / 60;
        m %= 60;
        if (h > 0) return m > 0 ? h + " hr, " + m + " min" : h + " hr";
        return m == 1 ? "1 minute" : m + " minutes";
    }
}
