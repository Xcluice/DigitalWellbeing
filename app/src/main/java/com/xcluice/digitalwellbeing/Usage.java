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
        return range(c, weekStartMs, 7);
    }

    /** package -> foreground ms for each of n consecutive days starting at startMs (a midnight). */
    static Map<String, long[]> range(Context c, long weekStartMs, int n) {
        Map<String, long[]> out = new HashMap<>();
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        long[] b = new long[n + 1];
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(weekStartMs);
        for (int i = 0; i <= n; i++) {
            b[i] = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long end = Math.min(now, b[n]);
        if (end <= b[0]) return out;

        Set<String> ok = launchable(c);
        UsageEvents ev = usm.queryEvents(b[0], end);
        UsageEvents.Event e = new UsageEvents.Event();
        // Only one app can be in front at a time: this keeps totals sane even when
        // the device (e.g. older Samsung/Oreo) drops or reorders some events.
        String cur = null;
        long curStart = 0, lastTs = b[0];

        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            int t = e.getEventType();
            long ts = e.getTimeStamp();
            String pkg = e.getPackageName();
            lastTs = ts;
            if (t == 1) { // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
                if (cur != null && !cur.equals(pkg)) {
                    add(out, ok, cur, curStart, ts, b, n);
                    cur = null;
                }
                if (cur == null) { cur = pkg; curStart = ts; }
            } else if (t == 2) { // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
                if (cur != null && cur.equals(pkg)) {
                    add(out, ok, cur, curStart, ts, b, n);
                    cur = null;
                }
            } else if (t == 16 || t == 17 || t == 26) { // screen off / keyguard / shutdown
                if (cur != null) {
                    add(out, ok, cur, curStart, ts, b, n);
                    cur = null;
                }
            }
        }
        if (cur != null) {
            android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
            long stop = pm.isInteractive() ? end : Math.min(end, lastTs + 60000L);
            add(out, ok, cur, curStart, stop, b, n);
        }
        return out;
    }

    private static void add(Map<String, long[]> out, Set<String> ok, String pkg, long s, long e, long[] b, int n) {
        if (!ok.contains(pkg) || e <= s) return;
        if (e - s > 6 * 3600000L) e = s + 6 * 3600000L; // safety cap for a single session
        long[] arr = out.get(pkg);
        if (arr == null) { arr = new long[n]; out.put(pkg, arr); }
        for (int i = 0; i < n; i++) {
            long lo = Math.max(s, b[i]);
            long hi = Math.min(e, b[i + 1]);
            if (hi > lo) arr[i] += hi - lo;
        }
    }

    /** package -> foreground ms today, excluding hidden apps. */
    static Map<String, Long> today(Context c) {
        Calendar now = Calendar.getInstance();
        int idx = now.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        Map<String, long[]> m = week(c, weekStart(now).getTimeInMillis());
        Set<String> h = hidden(c);
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, long[]> en : m.entrySet()) {
            if (!h.contains(en.getKey()) && en.getValue()[idx] > 0) out.put(en.getKey(), en.getValue()[idx]);
        }
        return out;
    }

    static String fmtShort(long ms) {
        long m = ms / 60000, h = m / 60;
        m %= 60;
        if (h > 0 && m > 0) return h + "h " + m + "m";
        if (h > 0) return h + "h";
        return m + "m";
    }

    /** Diagnostic dump: event type counts + the longest sessions and why each one ended. */
    static String debug(Context c) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        long from = now - 3L * 86400000L;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEE HH:mm:ss", java.util.Locale.US);
        UsageEvents ev = usm.queryEvents(from, now);
        UsageEvents.Event e = new UsageEvents.Event();
        Map<Integer, Integer> hist = new java.util.TreeMap<>();
        List<Object[]> sessions = new java.util.ArrayList<>();
        String cur = null, cls = "";
        long curStart = 0;
        int total = 0;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            total++;
            int t = e.getEventType();
            long ts = e.getTimeStamp();
            String pkg = e.getPackageName();
            Integer h = hist.get(t);
            hist.put(t, h == null ? 1 : h + 1);
            String why = null;
            if (t == 1) {
                if (cur != null && !cur.equals(pkg)) { why = "next app " + pkg; }
                if (why != null) { sessions.add(new Object[]{cur, curStart, ts, why}); cur = null; }
                if (cur == null) { cur = pkg; curStart = ts; }
            } else if (t == 2) {
                if (cur != null && cur.equals(pkg)) { sessions.add(new Object[]{cur, curStart, ts, "pause"}); cur = null; }
            } else if (t == 16 || t == 17 || t == 26) {
                if (cur != null) { sessions.add(new Object[]{cur, curStart, ts, "screen/type " + t}); cur = null; }
            }
        }
        if (cur != null) sessions.add(new Object[]{cur, curStart, now, "STILL OPEN"});
        java.util.Collections.sort(sessions, new java.util.Comparator<Object[]>() {
            @Override public int compare(Object[] a, Object[] b) {
                return Long.compare((Long) b[2] - (Long) b[1], (Long) a[2] - (Long) a[1]);
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("SDK ").append(android.os.Build.VERSION.SDK_INT).append("  events(3d): ").append(total).append("\n");
        sb.append("types: ").append(hist.toString()).append("\n\nLongest sessions:\n");
        for (int i = 0; i < Math.min(8, sessions.size()); i++) {
            Object[] o = sessions.get(i);
            long mins = ((Long) o[2] - (Long) o[1]) / 60000;
            String p = (String) o[0];
            sb.append(mins).append("m  ").append(p.substring(Math.max(0, p.length() - 22))).append("\n  ")
                    .append(f.format(new java.util.Date((Long) o[1]))).append(" -> ")
                    .append(f.format(new java.util.Date((Long) o[2]))).append("\n  end: ").append(o[3]).append("\n");
        }
        return sb.toString();
    }

    // ---- App timers
    static int limit(Context c, String pkg) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("lim_" + pkg, 0);
    }

    static void setLimit(Context c, String pkg, int minutes) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (minutes <= 0) e.remove("lim_" + pkg); else e.putInt("lim_" + pkg, minutes);
        e.apply();
    }

    static void ignoreToday(Context c, String pkg) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("ign_" + pkg, midnight(Calendar.getInstance()).getTimeInMillis()).apply();
    }

    static boolean ignored(Context c, String pkg) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("ign_" + pkg, 0)
                == midnight(Calendar.getInstance()).getTimeInMillis();
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
