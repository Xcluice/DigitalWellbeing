package com.xcluice.digitalwellbeing;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
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
    private View lockView;
    private long lockAt;
    private boolean inShortsLock;
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
        h.removeCallbacks(watchdog);
        h.postDelayed(watchdog, 1500);
    }

    /**
     * Safety net: window events can be dropped or arrive before the service is ready (right after the timer
     * is set, after an update, when the service reconnects). Every few seconds, look at what is actually in
     * front and enforce the limits for it.
     */
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            try {
                if (lockView == null) {
                    AccessibilityNodeInfo r = getRootInActiveWindow();
                    if (r != null) {
                        CharSequence cs = r.getPackageName();
                        r.recycle();
                        if (cs != null) {
                            String p = cs.toString();
                            if (!p.equals(current) && !p.equals(getPackageName())
                                    && getPackageManager().getLaunchIntentForPackage(p) != null) {
                                current = p;
                                gen++;
                                check(p, gen);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            h.postDelayed(this, 3000);
        }
    };

    @Override
    public void onDestroy() {
        try { unregisterReceiver(unlock); } catch (Exception ignored) {}
        h.removeCallbacks(watchdog);
        removeLock();
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
        // The user moved on (another app, home): the lock screen has done its job. A short grace period
        // lets the "go home" we trigger ourselves settle first.
        if (lockView != null && !p.equals(getPackageName()) && !(inShortsLock && p.equals(YT))
                && SystemClock.uptimeMillis() - lockAt > 1200) {
            removeLock();
        }
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

    /**
     * Shorts limit reached. The lock screen is drawn first, as a window straight over YouTube, so there is no
     * gap; only then is Shorts closed behind it. Long videos keep working.
     */
    private void kick() {
        long now = SystemClock.uptimeMillis();
        if (now - lastKick < 1500) return;
        lastKick = now;
        inShorts = false;
        showShortsLock();
        performGlobalAction(GLOBAL_ACTION_BACK);
        // If Shorts is somehow still on screen behind the overlay, close it again.
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (lockView != null && shortsOpen()) performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }, 700);
    }

    private void showShortsLock() {
        if (lockView != null) return;
        inShortsLock = true;
        View v = new ShortsLockView(this, new Runnable() {
            @Override public void run() { removeLock(); }
        });
        if (!showLock(v)) {
            startActivity(new Intent(this, ShortsBlockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
    }

    /** Adds a full-screen window over whatever is on screen. Returns false if Android refuses it. */
    private boolean showLock(View v) {
        removeLock();
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.OPAQUE);
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            wm.addView(v, lp);
            lockView = v;
            lockAt = SystemClock.uptimeMillis();
            return true;
        } catch (Exception err) {
            lockView = null;
            return false;
        }
    }

    private void removeLock() {
        View v = lockView;
        lockView = null;
        if (v == null) return;
        try {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(v);
        } catch (Exception ignored) {}
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

    private void block(final String p, final String packId) {
        gen++;
        current = "";
        inShortsLock = false;
        View v = new AppLockView(this, p, packId,
                new Runnable() {
                    @Override public void run() { removeLock(); }
                },
                new Runnable() {
                    @Override public void run() {
                        if (packId != null) Usage.ignorePackToday(TimerService.this, packId);
                        else Usage.ignoreToday(TimerService.this, p);
                        removeLock();
                        Intent li = getPackageManager().getLaunchIntentForPackage(p);
                        if (li != null) startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                });
        if (showLock(v)) {
            // The overlay already covers the app; send the app away behind it.
            performGlobalAction(GLOBAL_ACTION_HOME);
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME);
            Intent i = new Intent(this, BlockActivity.class)
                    .putExtra("pkg", p)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (packId != null) i.putExtra("pack", packId);
            startActivity(i);
        }
    }
}
