package com.xcluice.digitalwellbeing;

import android.Manifest;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.telephony.TelephonyManager;

/** Data an app used in the background vs. while on screen: the background activity Android lets us see. */
final class NetUsage {
    private NetUsage() {}

    static class R {
        long wifiFg, wifiBg, mobFg, mobBg;
        boolean mobileDenied;
        long fg() { return wifiFg + mobFg; }
        long bg() { return wifiBg + mobBg; }
    }

    static R query(Context c, String pkg, long start, long end) {
        R r = new R();
        int uid;
        try { uid = c.getPackageManager().getApplicationInfo(pkg, 0).uid; } catch (Exception e) { return r; }
        NetworkStatsManager nsm = (NetworkStatsManager) c.getSystemService(Context.NETWORK_STATS_SERVICE);
        try {
            sum(nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, start, end), uid, r, false);
        } catch (Exception ignored) {}
        try {
            String sub = null;
            if (Build.VERSION.SDK_INT < 29) {
                if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    r.mobileDenied = true;
                    return r;
                }
                TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
                sub = tm.getSubscriberId();
            }
            sum(nsm.querySummary(ConnectivityManager.TYPE_MOBILE, sub, start, end), uid, r, true);
        } catch (Exception ignored) {}
        return r;
    }

    private static void sum(NetworkStats ns, int uid, R r, boolean mobile) {
        NetworkStats.Bucket b = new NetworkStats.Bucket();
        while (ns.hasNextBucket()) {
            ns.getNextBucket(b);
            if (b.getUid() != uid) continue;
            long bytes = b.getRxBytes() + b.getTxBytes();
            boolean fg = b.getState() == NetworkStats.Bucket.STATE_FOREGROUND;
            if (mobile) { if (fg) r.mobFg += bytes; else r.mobBg += bytes; }
            else { if (fg) r.wifiFg += bytes; else r.wifiBg += bytes; }
        }
        ns.close();
    }

    static String fmt(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        if (b < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(java.util.Locale.US, "%.2f GB", b / 1073741824.0);
    }
}
