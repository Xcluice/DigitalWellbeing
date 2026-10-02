package com.xcluice.digitalwellbeing;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SummaryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, Intent i) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (!Usage.notifSum(c) || !Usage.hasAccess(c)) return;
                    Map<String, Long> m = Usage.today(c);
                    long total = 0;
                    List<Map.Entry<String, Long>> l = new ArrayList<>(m.entrySet());
                    for (Map.Entry<String, Long> e : l) total += e.getValue();
                    Collections.sort(l, new Comparator<Map.Entry<String, Long>>() {
                        @Override public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                            return Long.compare(b.getValue(), a.getValue());
                        }
                    });
                    PackageManager pm = c.getPackageManager();
                    StringBuilder sb = new StringBuilder();
                    int shown = 0;
                    for (Map.Entry<String, Long> e : l) {
                        if (shown >= 3 || e.getValue() < 60000) break;
                        String name = e.getKey();
                        try { name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(e.getKey(), 0))); }
                        catch (Exception ignored) {}
                        if (shown > 0) sb.append(", ");
                        sb.append(name).append(" (").append(Usage.fmtShort(e.getValue())).append(")");
                        shown++;
                    }
                    String text = shown == 0 ? "Hardly any phone time today. Nice." : "Most used: " + sb;
                    PendingIntent pi = PendingIntent.getActivity(c, 4, new Intent(c, MainActivity.class),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    Notifs.post(c, 12, Notifs.CH_SUM, "Today's screen time: " + Usage.fmtShort(total), text, pi);
                } catch (Exception ignored) {
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }
}
