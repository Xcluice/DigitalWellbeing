package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

        // ---- Header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(C_BAR);
        head.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(56)));
        ImageView back = icon(R.drawable.ic_back, 56, 16);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(back);
        TextView title = tv("Dashboard", 20, Color.WHITE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = dp(2);
        title.setLayoutParams(tp);
        head.addView(title);
        final ImageView more = icon(R.drawable.ic_more, 56, 16);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(more); }
        });
        head.addView(more);
        root.addView(head);

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
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Refresh");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem it) {
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
        act.setColorFilter(Color.WHITE);
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
