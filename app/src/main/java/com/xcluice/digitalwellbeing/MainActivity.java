package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    static final int C_BG = 0xFF1F1F1F, C_BAR = 0xFF424242, C_SUB = 0xFFBDC1C6,
            C_DIV = 0xFF3C4043, C_LINK = 0xFF8AB4F8;

    static class Meta {
        String label;
        Drawable icon;
        boolean sys;
        int color = 0xFF9AA0A6;
    }

    static class Item {
        String pkg;
        long ms;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Meta> metaCache = new HashMap<>();
    private Map<String, long[]> weekData = new HashMap<>();
    private long loadedWeek = -1;
    private Calendar sel = Usage.midnight(Calendar.getInstance());

    private float d;
    private LinearLayout contentView, permView, list;
    private TextView totalTv, subTv, dateTv;
    private ImageView prevBtn, nextBtn;
    private ChartView chart;
    private ImageView more;
    private TextView banner;
    private String updUrl;
    private int updBuild;
    private long lastCheck;
    private int taps;
    private long lastTap;

    private int dp(float v) { return (int) (v * d + 0.5f); }

    private TextView tv(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private ImageView icon(int res, int sizeDp, int padDp) {
        ImageView i = new ImageView(this);
        i.setImageResource(res);
        i.setColorFilter(Color.WHITE);
        i.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return i;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // ---- Top row: update banner + menu (no title bar)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));
        banner = tv("Update available  \u00b7  tap to get it", 13, Usage.accent(this));
        banner.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setCornerRadius(dp(18));
        bbg.setStroke(dp(1), 0xFF3F5A7A);
        banner.setBackground(bbg);
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showUpdateDialog(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.leftMargin = dp(16);
        top.addView(banner, blp);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        more = icon(R.drawable.ic_more, 52, 14);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(more); }
        });
        top.addView(more);
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout holder = new FrameLayout(this);
        sv.addView(holder, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sv);

        // ---- Permission view
        permView = new LinearLayout(this);
        permView.setOrientation(LinearLayout.VERTICAL);
        permView.setGravity(Gravity.CENTER_HORIZONTAL);
        permView.setPadding(dp(32), dp(120), dp(32), dp(32));
        TextView pt = tv("Allow usage access", 22, Color.WHITE);
        pt.setGravity(Gravity.CENTER);
        permView.addView(pt);
        TextView pd = tv("To show your screen time, allow Digital Wellbeing in Usage access settings.", 14, C_SUB);
        pd.setGravity(Gravity.CENTER);
        pd.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams pdp = new LinearLayout.LayoutParams(-2, -2);
        pdp.topMargin = dp(16);
        permView.addView(pd, pdp);
        TextView pb = pill("Open settings");
        pb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAccessSettings(); }
        });
        LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(-2, -2);
        pbp.topMargin = dp(32);
        permView.addView(pb, pbp);
        holder.addView(permView, new FrameLayout.LayoutParams(-1, -2));

        // ---- Content view
        contentView = new LinearLayout(this);
        contentView.setOrientation(LinearLayout.VERTICAL);
        totalTv = tv("0 min", 26, Color.WHITE);
        totalTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(24);
        contentView.addView(totalTv, lp);

        subTv = tv("Today", 12, C_SUB);
        subTv.setGravity(Gravity.CENTER);
        subTv.setPadding(dp(40), dp(10), dp(40), dp(10));
        subTv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { secretTap(v); }
        });
        contentView.addView(subTv, new LinearLayout.LayoutParams(-1, -2));

        chart = new ChartView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(14);
        contentView.addView(chart, cp);

        FrameLayout dateRow = new FrameLayout(this);
        LinearLayout.LayoutParams dr = new LinearLayout.LayoutParams(-1, dp(48));
        dr.topMargin = dp(20);
        dateTv = tv("", 15, Color.WHITE);
        dateRow.addView(dateTv, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        prevBtn = icon(R.drawable.ic_prev, 40, 8);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START | Gravity.CENTER_VERTICAL);
        pl.leftMargin = dp(40);
        dateRow.addView(prevBtn, pl);
        nextBtn = icon(R.drawable.ic_next, 40, 8);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END | Gravity.CENTER_VERTICAL);
        nl.rightMargin = dp(40);
        dateRow.addView(nextBtn, nl);
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveDay(-1); }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveDay(1); }
        });
        contentView.addView(dateRow, dr);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(list, new LinearLayout.LayoutParams(-1, -2));

        contentView.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(40)));
        holder.addView(contentView, new FrameLayout.LayoutParams(-1, -2));

        setContentView(root);

        Notifs.scheduleAll(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    private TextView pill(String s) {
        TextView t = tv(s, 14, C_LINK);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(22), dp(11), dp(22), dp(11));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1), 0xFF3F5A7A);
        t.setBackground(g);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        if (System.currentTimeMillis() - lastCheck > 30 * 60 * 1000L) checkUpdate(false);
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Refresh");
        m.getMenu().add(0, 2, 1, "Colour options");
        m.getMenu().add(0, 4, 2, "Notifications");
        m.getMenu().add(0, 3, 3, "Check for updates");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem it) {
                if (it.getItemId() == 2) { showColors(); return true; }
                if (it.getItemId() == 3) { checkUpdate(true); return true; }
                if (it.getItemId() == 4) { showNotifSettings(); return true; }
                loadedWeek = -1;
                load();
                return true;
            }
        });
        m.show();
    }

    private void openAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Open Settings > Apps > Special access > Usage access", Toast.LENGTH_LONG).show();
        }
    }

    private void secretTap(View v) {
        long n = System.currentTimeMillis();
        if (n - lastTap > 1200) taps = 0;
        lastTap = n;
        taps++;
        if (taps >= 7) {
            taps = 0;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            openManager();
        }
    }

    private int dayIdx() { return sel.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY; }

    private boolean isToday() {
        return sel.getTimeInMillis() == Usage.midnight(Calendar.getInstance()).getTimeInMillis();
    }

    private void moveDay(int delta) {
        Calendar n = (Calendar) sel.clone();
        n.add(Calendar.DAY_OF_YEAR, delta);
        if (n.getTimeInMillis() > Usage.midnight(Calendar.getInstance()).getTimeInMillis()) return;
        sel = n;
        load();
    }

    private void load() {
        if (!Usage.hasAccess(this)) {
            permView.setVisibility(View.VISIBLE);
            contentView.setVisibility(View.GONE);
            return;
        }
        permView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        final long ws = Usage.weekStart(sel).getTimeInMillis();
        final boolean fresh = ws == loadedWeek && !isTodayWeek(ws);
        if (fresh) { render(); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                final Map<String, long[]> data = Usage.week(MainActivity.this, ws);
                PackageManager pm = getPackageManager();
                for (String p : data.keySet()) ensureMeta(pm, p);
                ui.post(new Runnable() {
                    @Override public void run() {
                        weekData = data;
                        loadedWeek = ws;
                        render();
                    }
                });
                DWWidget.refreshAll(MainActivity.this);
            }
        }).start();
    }

    private boolean isTodayWeek(long ws) {
        return ws == Usage.weekStart(Calendar.getInstance()).getTimeInMillis();
    }

    private synchronized Meta ensureMeta(PackageManager pm, String p) {
        Meta m = metaCache.get(p);
        if (m != null) return m;
        m = new Meta();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(p, 0);
            m.label = String.valueOf(pm.getApplicationLabel(ai));
            m.icon = pm.getApplicationIcon(ai);
            m.color = dominant(pm.getApplicationIcon(ai));
            m.sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (Exception e) {
            m.label = p;
            m.icon = pm.getDefaultActivityIcon();
        }
        metaCache.put(p, m);
        return m;
    }

    private void render() {
        applyAccent();
        Set<String> hid = Usage.hidden(this);
        long[] totals = new long[7];
        List<Item> items = new ArrayList<>();
        int idx = dayIdx();
        for (Map.Entry<String, long[]> en : weekData.entrySet()) {
            if (hid.contains(en.getKey())) continue;
            long[] a = en.getValue();
            for (int i = 0; i < 7; i++) totals[i] += a[i];
            if (a[idx] >= 60000) {
                Item it = new Item();
                it.pkg = en.getKey();
                it.ms = a[idx];
                items.add(it);
            }
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Long.compare(b.ms, a.ms); }
        });

        Calendar ws = Usage.weekStart(sel);
        long todayStart = Usage.midnight(Calendar.getInstance()).getTimeInMillis();
        boolean[] fut = new boolean[7];
        for (int i = 0; i < 7; i++) {
            Calendar c = (Calendar) ws.clone();
            c.add(Calendar.DAY_OF_YEAR, i);
            fut[i] = c.getTimeInMillis() > todayStart;
        }
        long[][] segV = new long[7][];
        int[][] segC = new int[7][];
        PackageManager pmc = getPackageManager();
        for (int i = 0; i < 7; i++) {
            List<Item> di = new ArrayList<>();
            for (Map.Entry<String, long[]> en : weekData.entrySet()) {
                if (hid.contains(en.getKey()) || en.getValue()[i] <= 0) continue;
                Item x = new Item();
                x.pkg = en.getKey();
                x.ms = en.getValue()[i];
                di.add(x);
            }
            Collections.sort(di, new Comparator<Item>() {
                @Override public int compare(Item a, Item b) { return Long.compare(b.ms, a.ms); }
            });
            int n = Math.min(di.size(), 6);
            long rest = 0;
            for (int k = 6; k < di.size(); k++) rest += di.get(k).ms;
            int len = n + (rest > 0 ? 1 : 0);
            segV[i] = new long[len];
            segC[i] = new int[len];
            for (int k = 0; k < n; k++) {
                segV[i][k] = di.get(k).ms;
                segC[i][k] = ensureMeta(pmc, di.get(k).pkg).color;
            }
            if (rest > 0) { segV[i][n] = rest; segC[i][n] = 0xFF5F6368; }
        }
        chart.setThresholds(Usage.thLow(this), Usage.thHigh(this));
        chart.set(totals, segV, segC, fut, idx, new ChartView.Listener() {
            @Override public void onDay(int i) {
                Calendar c = Usage.weekStart(sel);
                c.add(Calendar.DAY_OF_YEAR, i);
                sel = c;
                render();
            }
        });

        totalTv.setText(Usage.fmtTotal(totals[idx]));
        boolean today = isToday();
        Calendar y = Usage.midnight(Calendar.getInstance());
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (today) subTv.setText("Today");
        else if (sel.getTimeInMillis() == y.getTimeInMillis()) subTv.setText("Yesterday");
        else subTv.setText(new SimpleDateFormat("EEEE", Locale.getDefault()).format(sel.getTime()));
        dateTv.setText(new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(sel.getTime()));
        nextBtn.setVisibility(today ? View.INVISIBLE : View.VISIBLE);

        list.removeAllViews();
        PackageManager pm = getPackageManager();
        long maxMs = items.isEmpty() ? 1 : items.get(0).ms;
        for (Item it : items) list.addView(row(it, ensureMeta(pm, it.pkg), maxMs));
    }

    private View row(final Item it, final Meta m, long maxMs) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(70)));

        ImageView ic = new ImageView(this);
        ic.setImageDrawable(m.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
        ip.leftMargin = dp(22);
        r.addView(ic, ip);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = dp(14);
        TextView name = tv(m.label, 16, Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        int lim = Usage.limit(this, it.pkg);
        TextView time = tv(Usage.fmtRow(it.ms) + (lim > 0 && !m.sys ? "  \u00b7  " + Usage.fmtTotal(lim * 60000L) + " limit" : ""), 13, C_SUB);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(5);
        col.addView(time, tp);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        float frac = Math.max(0.05f, Math.min(1f, (float) it.ms / maxMs));
        View fill = new View(this);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(m.color);
        fg.setCornerRadius(dp(2));
        fill.setBackground(fg);
        bar.addView(fill, new LinearLayout.LayoutParams(0, dp(4), frac));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(4), 1f - frac));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(4));
        bp.topMargin = dp(7);
        bp.rightMargin = dp(8);
        col.addView(bar, bp);
        r.addView(col, cp);

        View div = new View(this);
        div.setBackgroundColor(C_DIV);
        r.addView(div, new LinearLayout.LayoutParams(dp(1), dp(38)));

        ImageView act = new ImageView(this);
        act.setImageResource(m.sys ? R.drawable.ic_info : R.drawable.ic_hourglass);
        act.setColorFilter(Usage.accent(this));
        act.setPadding(dp(20), dp(20), dp(20), dp(20));
        r.addView(act, new LinearLayout.LayoutParams(dp(64), dp(64)));

        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { appInfo(it.pkg); }
        });
        act.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (m.sys) appInfo(it.pkg);
                else showTimer(it.pkg, m);
            }
        });
        return r;
    }

    private void appInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (Exception ignored) {}
    }

    private void applyAccent() {
        int a = Usage.accent(this);
        more.setColorFilter(a);
        prevBtn.setColorFilter(a);
        nextBtn.setColorFilter(a);
        totalTv.setTextColor(a);
        banner.setTextColor(a);
    }

    private void showNotifSettings() {
        final boolean[] on = {Usage.notifUpd(this), Usage.notifSum(this)};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Notifications")
                .setMultiChoiceItems(new String[]{"Update alerts", "Daily summary (around 9 PM)"}, on,
                        new android.content.DialogInterface.OnMultiChoiceClickListener() {
                            @Override public void onClick(android.content.DialogInterface d2, int which, boolean checked) {
                                on[which] = checked;
                            }
                        })
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d2, int w) {
                        Usage.setNotifs(MainActivity.this, on[0], on[1]);
                        Notifs.scheduleAll(MainActivity.this);
                        if (Build.VERSION.SDK_INT >= 33 && (on[0] || on[1])
                                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Colour options
    private void showColors() {
        final int[] acc = {Usage.accent(this)};
        final int[] low = {Usage.thLow(this)};
        final int[] high = {Usage.thHigh(this)};
        final int[] cols = {0xFF8AB4F8, 0xFF81C995, 0xFF78D9EC, 0xFFFDD663, 0xFFFFAD70,
                0xFFF28B82, 0xFFFF8BCB, 0xFFC58AF9, 0xFF3DDBC1, 0xFFFFFFFF};
        final List<View> sw = new ArrayList<>();
        final Runnable[] restyle = new Runnable[1];
        restyle[0] = new Runnable() {
            @Override public void run() {
                for (int i = 0; i < sw.size(); i++) {
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(cols[i]);
                    if (cols[i] == acc[0]) g.setStroke(dp(3), 0xFF202124 == 0 ? 0 : 0xFFFFFFFF);
                    else g.setStroke(dp(1), 0x55FFFFFF);
                    sw.get(i).setBackground(g);
                }
            }
        };

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(22), dp(12), dp(22), dp(8));
        l.addView(tv("Accent colour", 14, C_SUB));
        for (int row = 0; row < 2; row++) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
            rp.topMargin = dp(10);
            for (int k = 0; k < 5; k++) {
                final int ci = row * 5 + k;
                View v = new View(this);
                LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(38), dp(38));
                vp.rightMargin = dp(10);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View x) { acc[0] = cols[ci]; restyle[0].run(); }
                });
                sw.add(v);
                r.addView(v, vp);
            }
            l.addView(r, rp);
        }
        restyle[0].run();

        TextView ct = tv("Chart colours", 14, C_SUB);
        LinearLayout.LayoutParams ctp = new LinearLayout.LayoutParams(-2, -2);
        ctp.topMargin = dp(26);
        l.addView(ct, ctp);

        final View strip = new View(this) {
            final Paint p = new Paint();
            @Override protected void onDraw(Canvas c) {
                int w = getWidth();
                for (int x = 0; x < w; x += 3) {
                    p.setColor(ChartView.barColor(12f * x / w, low[0], high[0], 255));
                    c.drawRect(x, 0, Math.min(x + 3, w), getHeight(), p);
                }
            }
        };
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, dp(14));
        stp.topMargin = dp(12);
        l.addView(strip, stp);
        LinearLayout ends = new LinearLayout(this);
        ends.setOrientation(LinearLayout.HORIZONTAL);
        TextView e0 = tv("0h", 11, C_SUB);
        ends.addView(e0, new LinearLayout.LayoutParams(0, -2, 1f));
        ends.addView(tv("12h", 11, C_SUB));
        LinearLayout.LayoutParams enp = new LinearLayout.LayoutParams(-1, -2);
        enp.topMargin = dp(4);
        l.addView(ends, enp);

        final TextView lowTv = tv("", 14, Color.WHITE);
        final TextView highTv = tv("", 14, Color.WHITE);
        final SeekBar lowBar = new SeekBar(this);
        lowBar.setMax(11);
        lowBar.setProgress(low[0]);
        final SeekBar highBar = new SeekBar(this);
        highBar.setMax(11);
        highBar.setProgress(high[0] - 1);
        final boolean[] busy = {false};
        final Runnable labels = new Runnable() {
            @Override public void run() {
                lowTv.setText("Green up to " + low[0] + " h");
                highTv.setText("Red from " + high[0] + " h");
                strip.invalidate();
            }
        };
        labels.run();
        lowBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (busy[0]) return;
                busy[0] = true;
                low[0] = p;
                if (high[0] <= low[0]) { high[0] = low[0] + 1; highBar.setProgress(high[0] - 1); }
                busy[0] = false;
                labels.run();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        highBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (busy[0]) return;
                busy[0] = true;
                high[0] = p + 1;
                if (low[0] >= high[0]) { low[0] = high[0] - 1; lowBar.setProgress(low[0]); }
                busy[0] = false;
                labels.run();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(-2, -2);
        t1.topMargin = dp(18);
        l.addView(lowTv, t1);
        l.addView(lowBar, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams t2 = new LinearLayout.LayoutParams(-2, -2);
        t2.topMargin = dp(10);
        l.addView(highTv, t2);
        l.addView(highBar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv2 = new ScrollView(this);
        sv2.addView(l);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Colour options")
                .setView(sv2)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d2, int w) {
                        Usage.setColors(MainActivity.this, acc[0], low[0], high[0]);
                        render();
                        new Thread(new Runnable() {
                            @Override public void run() { DWWidget.refreshAll(MainActivity.this); }
                        }).start();
                    }
                })
                .setNeutralButton("Reset", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d2, int w) {
                        Usage.setColors(MainActivity.this, 0xFF8AB4F8, 1, 6);
                        render();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Update check (looks at the GitHub releases of this app)
    private void checkUpdate(final boolean manual) {
        lastCheck = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Updater.Info info = Updater.fetch();
                    if (info == null) throw new Exception("no release");
                    final int cur = Updater.installed(MainActivity.this);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (info.build > cur) {
                                updUrl = info.url;
                                updBuild = info.build;
                                banner.setVisibility(View.VISIBLE);
                                android.content.SharedPreferences sp = getSharedPreferences("dw_prefs", MODE_PRIVATE);
                                if (manual || sp.getInt("upd_seen", 0) != info.build) {
                                    sp.edit().putInt("upd_seen", info.build).apply();
                                    showUpdateDialog();
                                }
                            } else {
                                banner.setVisibility(View.GONE);
                                if (manual) Toast.makeText(MainActivity.this,
                                        "You're up to date (build " + cur + ")", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    if (manual) ui.post(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(MainActivity.this, "Couldn't check for updates", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showUpdateDialog() {
        if (updUrl == null) return;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Update available")
                .setMessage("A new version of Digital Wellbeing (build " + updBuild
                        + ") is ready. Download it and tap the file to install over this one.")
                .setPositiveButton("Download", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d2, int w) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(updUrl))); } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    // ---- App timers
    private boolean serviceOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private void showTimer(final String pkg, final Meta m) {
        int cur = Usage.limit(this, pkg);
        if (cur <= 0) cur = 30;
        final NumberPicker hp = new NumberPicker(this);
        hp.setMinValue(0);
        hp.setMaxValue(23);
        hp.setValue(cur / 60);
        final NumberPicker mp = new NumberPicker(this);
        mp.setMinValue(0);
        mp.setMaxValue(59);
        mp.setValue(cur % 60);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(16), dp(16), dp(16), dp(8));
        l.addView(hp);
        TextView hl = tv("hr", 15, Color.WHITE);
        hl.setPadding(dp(8), 0, dp(24), 0);
        l.addView(hl);
        l.addView(mp);
        TextView ml = tv("min", 15, Color.WHITE);
        ml.setPadding(dp(8), 0, 0, 0);
        l.addView(ml);
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(m.label + " timer")
                .setView(l)
                .setPositiveButton("Set timer", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dlg, int w) {
                        int total = hp.getValue() * 60 + mp.getValue();
                        Usage.setLimit(MainActivity.this, pkg, total);
                        render();
                        if (total > 0 && !serviceOn()) askService();
                    }
                })
                .setNegativeButton("Cancel", null);
        if (Usage.limit(this, pkg) > 0) {
            b.setNeutralButton("Remove", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface dlg, int w) {
                    Usage.setLimit(MainActivity.this, pkg, 0);
                    render();
                }
            });
        }
        b.show();
    }

    private void askService() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Turn on app timers")
                .setMessage("To enforce timers, turn on \"App timers\" in Accessibility settings (under Installed apps or Downloaded apps). It only sees which app is open.")
                .setPositiveButton("Open settings", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dlg, int w) {
                        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    // ---- Colour accent from an app icon
    static int dominant(Drawable d) {
        try {
            int s = 40;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            float[] w = new float[24];
            float[] r = new float[24], g = new float[24], bl = new float[24];
            float[] hsv = new float[3];
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 200) continue;
                    Color.colorToHSV(px, hsv);
                    if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue;
                    int k = ((int) (hsv[0] / 15f)) % 24;
                    float wt = hsv[1] * hsv[2];
                    w[k] += wt;
                    r[k] += Color.red(px) * wt;
                    g[k] += Color.green(px) * wt;
                    bl[k] += Color.blue(px) * wt;
                }
            }
            int best = 0;
            for (int k = 1; k < 24; k++) if (w[k] > w[best]) best = k;
            if (w[best] <= 0) return 0xFF9AA0A6;
            int col = Color.rgb((int) (r[best] / w[best]), (int) (g[best] / w[best]), (int) (bl[best] / w[best]));
            Color.colorToHSV(col, hsv);
            hsv[1] = Math.max(hsv[1], 0.5f);
            hsv[2] = Math.max(hsv[2], 0.75f);
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return 0xFF9AA0A6;
        }
    }

    // ---- Secret: choose which apps are excluded from the stats
    private void openManager() {
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = getPackageManager();
                final List<String[]> apps = new ArrayList<>();
                for (String p : Usage.launchable(MainActivity.this)) {
                    apps.add(new String[]{String.valueOf(ensureMeta(pm, p).label), p});
                }
                Collections.sort(apps, new Comparator<String[]>() {
                    @Override public int compare(String[] a, String[] b) { return a[0].compareToIgnoreCase(b[0]); }
                });
                ui.post(new Runnable() {
                    @Override public void run() { showManager(apps); }
                });
            }
        }).start();
    }

    private void showManager(final List<String[]> apps) {
        final Set<String> hid = Usage.hidden(this);
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i)[0];
            checked[i] = hid.contains(apps.get(i)[1]);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hide from stats")
                .setMultiChoiceItems(labels, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(android.content.DialogInterface dlg, int which, boolean on) {
                        String p = apps.get(which)[1];
                        if (on) hid.add(p); else hid.remove(p);
                    }
                })
                .setPositiveButton("Done", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dlg, int w) {
                        Usage.saveHidden(MainActivity.this, hid);
                        render();
                        new Thread(new Runnable() {
                            @Override public void run() { DWWidget.refreshAll(MainActivity.this); }
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
