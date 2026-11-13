package com.xcluice.digitalwellbeing;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Watches which app is in front, enforces app timers and app packs, counts YouTube Shorts
 * and keeps the widget fresh.
 */
public class TimerService extends AccessibilityService {
    private static final String YT = "com.google.android.youtube";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private volatile int gen;
    private String current = "";
    private long lastWidget;

    // YouTube Shorts state
    private boolean inShorts;
    private long lastScan, lastSwipe, lastKick;

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
        CharSequence cs = e.getPackageName();
        if (cs == null) return;
        String p = cs.toString();
        int t = e.getEventType();

        // Scroll / content events are only interesting inside YouTube (Shorts counter).
        if (p.equals(YT)) shorts(e, t);
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        if (!p.equals(YT)) inShorts = false;
        if (p.equals(current)) return;
        if (p.equals(getPackageName())) { current = p; gen++; return; }
        if (getPackageManager().getLaunchIntentForPackage(p) == null) return; // keyboards, system UI...
        current = p;
        gen++;
        widget(false);
        check(p, gen);
    }

    // ---- YouTube Shorts counter

    private void shorts(AccessibilityEvent e, int t) {
        if (!Usage.shortsOn(this)) return;
        long now = SystemClock.uptimeMillis();
        int limit = Usage.shortsLimit(this);

        // A swipe inside the Shorts pager moves to the next Short.
        if (t == AccessibilityEvent.TYPE_VIEW_SCROLLED && inShorts && now - lastSwipe > 700 && reelScroll(e)) {
            lastSwipe = now;
            if (Usage.shortsCount(this) >= limit) kick();
            else Usage.shortsAdd(this);
            return;
        }

        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastScan < 300) return;
        lastScan = now;
        boolean open = shortsOpen();
        if (open && !inShorts) {
            // Just entered Shorts: the first Short counts too.
            inShorts = true;
            if (Usage.shortsCount(this) >= limit) { kick(); return; }
            lastSwipe = now;
            Usage.shortsAdd(this);
        } else if (!open) {
            inShorts = false;
        }
    }

    private boolean reelScroll(AccessibilityEvent e) {
        AccessibilityNodeInfo s = e.getSource();
        if (s == null) return false;
        try {
            String id = s.getViewIdResourceName();
            return id != null && (id.contains("reel") || id.contains("shorts"));
        } finally {
            s.recycle();
        }
    }

    private boolean shortsOpen() {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        if (r == null) return false;
        try {
            return has(r, "reel_recycler") || has(r, "reel_player_page_container")
                    || has(r, "reel_watch_fragment_root");
        } finally {
            r.recycle();
        }
    }

    private boolean has(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(YT + ":id/" + id);
        if (l == null || l.isEmpty()) return false;
        for (AccessibilityNodeInfo n : l) n.recycle();
        return true;
    }

    /** Leave Shorts. Long videos keep working because only the Shorts screen is closed. */
    private void kick() {
        long now = SystemClock.uptimeMillis();
        if (now - lastKick < 1500) return;
        lastKick = now;
        inShorts = false;
        performGlobalAction(GLOBAL_ACTION_BACK);
        h.postDelayed(new Runnable() {
            @Override public void run() {
                startActivity(new Intent(TimerService.this, ShortsBlockActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        }, 150);
    }

    // ---- Widget

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

    // ---- App timers and app packs

    private void check(final String p, final int g) {
        try {
            ex.execute(new Runnable() {
                @Override public void run() {
                    if (g != gen) return;
                    Context c = TimerService.this;
                    int lim = Usage.limit(c, p);
                    boolean appOn = lim > 0 && !Usage.ignored(c, p);
                    List<Usage.Pack> hit = new ArrayList<>();
                    for (Usage.Pack k : Usage.packs(c)) {
                        if (k.limit > 0 && k.pkgs.contains(p) && !Usage.packIgnored(c, k.id)) hit.add(k);
                    }
                    if (!appOn && hit.isEmpty()) return;

                    Map<String, long[]> m = Usage.range(c,
                            Usage.midnight(Calendar.getInstance()).getTimeInMillis(), 1);

                    long appLeft = Long.MAX_VALUE;
                    if (appOn) {
                        long[] a = m.get(p);
                        appLeft = lim * 60000L - (a != null ? a[0] : 0);
                    }
                    long packLeft = Long.MAX_VALUE;
                    String packId = null;
                    for (Usage.Pack k : hit) {
                        long left = k.limit * 60000L - Usage.packUsed(k, m);
                        if (left < packLeft) { packLeft = left; packId = k.id; }
                    }

                    if (appLeft <= 0) { post(p, g, null); return; }
                    if (packLeft <= 0) { post(p, g, packId); return; }

                    long next = Math.min(Math.min(appLeft, packLeft), 30000) + 200;
                    h.postDelayed(new Runnable() {
                        @Override public void run() { if (g == gen) check(p, g); }
                    }, next);
                }
            });
        } catch (Exception ignored) {}
    }

    private void post(final String p, final int g, final String packId) {
        h.post(new Runnable() {
            @Override public void run() { if (g == gen) block(p, packId); }
        });
    }

    private void block(String p, String packId) {
        gen++;
        current = "";
        performGlobalAction(GLOBAL_ACTION_HOME);
        Intent i = new Intent(this, BlockActivity.class)
                .putExtra("pkg", p)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (packId != null) i.putExtra("pack", packId);
        startActivity(i);
    }
}
