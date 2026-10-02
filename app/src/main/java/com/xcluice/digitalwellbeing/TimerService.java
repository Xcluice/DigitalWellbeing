package com.xcluice.digitalwellbeing;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Watches which app is in front, enforces app timers and keeps the widget fresh. */
public class TimerService extends AccessibilityService {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private volatile int gen;
    private String current = "";
    private long lastWidget;

    private final BroadcastReceiver unlock = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { widget(true); }
    };

    @Override
    protected void onServiceConnected() {
        IntentFilter f = new IntentFilter(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlock, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(unlock, f);
        widget(true);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(unlock); } catch (Exception ignored) {}
        ex.shutdownNow();
        super.onDestroy();
    }

    @Override public void onInterrupt() {}

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cs = e.getPackageName();
        if (cs == null) return;
        String p = cs.toString();
        if (p.equals(current)) return;
        if (p.equals(getPackageName())) { current = p; gen++; return; }
        if (getPackageManager().getLaunchIntentForPackage(p) == null) return; // keyboards, system UI...
        current = p;
        gen++;
        widget(false);
        check(p, gen);
    }

    private void widget(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastWidget < 30000) return;
        lastWidget = now;
        try {
            ex.execute(new Runnable() {
                @Override public void run() { DWWidget.refreshAll(TimerService.this); }
            });
        } catch (Exception ignored) {}
    }

    private void check(final String p, final int g) {
        try {
            ex.execute(new Runnable() {
                @Override public void run() {
                    if (g != gen) return;
                    int lim = Usage.limit(TimerService.this, p);
                    if (lim <= 0 || Usage.ignored(TimerService.this, p)) return;
                    long used = 0;
                    Map<String, long[]> m = Usage.range(TimerService.this,
                            Usage.midnight(Calendar.getInstance()).getTimeInMillis(), 1);
                    long[] a = m.get(p);
                    if (a != null) used = a[0];
                    long left = lim * 60000L - used;
                    if (left <= 0) {
                        h.post(new Runnable() {
                            @Override public void run() { if (g == gen) block(p); }
                        });
                        return;
                    }
                    h.postDelayed(new Runnable() {
                        @Override public void run() { if (g == gen) check(p, g); }
                    }, Math.min(left, 30000) + 200);
                }
            });
        } catch (Exception ignored) {}
    }

    private void block(String p) {
        gen++;
        current = "";
        performGlobalAction(GLOBAL_ACTION_HOME);
        Intent i = new Intent(this, BlockActivity.class)
                .putExtra("pkg", p)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }
}
