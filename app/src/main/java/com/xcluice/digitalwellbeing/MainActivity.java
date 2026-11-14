package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
    static final int C_LINK = 0xFF8AB4F8;

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
    private TextView more;
    private Themes.T th;
    private LinearLayout checking;
    private ProgressBar spinner;
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.8f);
    private ValueAnimator totalAnim;
    private long shownTotal, lastKey = -1, lastWeekKey = -1;
    private String lastTotalStr = "";
    private int slideDir;
    private volatile long lastLoad, lastWidget;
    private AlertDialog dlDialog;
    private ProgressBar dlBar;
    private TextView dlText;
    private volatile boolean cancelDl;
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
        i.setColorFilter(th.text);
        i.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return i;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        th = Themes.get(this);
        Themes.applyWindow(this);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- Top row: update banner + menu (no title bar)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));
        checking = new LinearLayout(this);
        checking.setOrientation(LinearLayout.HORIZONTAL);
        checking.setGravity(Gravity.CENTER_VERTICAL);
        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Usage.accent(this)));
        checking.addView(spinner, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView ckt = tv("Checking for updates\u2026", 13, th.sub);
        LinearLayout.LayoutParams ckp = new LinearLayout.LayoutParams(-2, -2);
        ckp.leftMargin = dp(10);
        checking.addView(ckt, ckp);
        checking.setVisibility(View.GONE);
        LinearLayout.LayoutParams cklp = new LinearLayout.LayoutParams(-2, -2);
        cklp.leftMargin = dp(18);
        top.addView(checking, cklp);
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
        more = tv("Settings", 14, Usage.accent(this));
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable mg = new GradientDrawable();
        mg.setCornerRadius(dp(18));
        mg.setStroke(dp(1), th.div);
        more.setBackground(mg);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.rightMargin = dp(16);
        top.addView(more, mlp);
        root.addView(top);

        ScrollView sv = new StretchScrollView(this);
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
        TextView pt = tv("Allow usage access", 22, th.text);
        pt.setGravity(Gravity.CENTER);
        permView.addView(pt);
        TextView pd = tv("To show your screen time, allow Digital Wellbeing in Usage access settings.", 14, th.sub);
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
        totalTv = tv("0 min", 26, th.text);
        totalTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(24);
        contentView.addView(totalTv, lp);

        subTv = tv("Today", 12, th.sub);
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
        dateTv = tv("", 15, th.text);
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
            @Override public void onClick(View v) { moveWeek(-1); }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveWeek(1); }
        });
        contentView.addView(dateRow, dr);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(list, new LinearLayout.LayoutParams(-1, -2));

        contentView.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(40)));
        holder.addView(contentView, new FrameLayout.LayoutParams(-1, -2));

        setContentView(root);

        new Thread(new Runnable() {
            @Override public void run() { Notifs.scheduleAll(MainActivity.this); }
        }).start();
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
        if (getIntent().getBooleanExtra("update", false)) {
            getIntent().removeExtra("update");
            checkUpdate(true);
        } else if (System.currentTimeMillis() - lastCheck > 30 * 60 * 1000L) {
            checkUpdate(false);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
    }

    private int dlgStyle() {
        return th.light ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Dialog_Alert;
    }

    /** A rounded panel that slides up from the bottom. maxHeight 0 = wrap content. */
    private Dialog sheet(View content, int maxHeight) {
        final Dialog dg = new Dialog(this);
        dg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.sheet);
        float r = dp(24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        View handle = new View(this);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(th.div);
        hg.setCornerRadius(dp(2));
        handle.setBackground(hg);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(4));
        hp.topMargin = dp(10);
        wrap.addView(handle, hp);
        wrap.addView(content, new LinearLayout.LayoutParams(-1, maxHeight > 0 ? maxHeight : -2));
        dg.setContentView(wrap);
        Window w = dg.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.getDecorView().setPadding(0, 0, 0, 0);
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setWindowAnimations(R.style.SheetAnim);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.55f);
        return dg;
    }

    private void showSettings() {
        final Dialog[] dg = new Dialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(14), dp(24), dp(28));
        box.addView(tv("Settings", 20, th.text));
        int np = Usage.packs(this).size();
        String[] titles = {"Themes", "Colour options", "Notifications", "App packs", "YouTube Shorts",
                "Check for updates", "Refresh data"};
        String[] subs = {Themes.get(this).name, "Accent and chart colours", "Update alerts and daily summary",
                np == 0 ? "Shared limit for a group of apps" : np == 1 ? "1 pack" : np + " packs",
                Usage.shortsOn(this) ? Usage.shortsCount(this) + " of " + Usage.shortsLimit(this) + " Shorts today" : "Off",
                "Installed: build " + Updater.installed(this), "Reload your usage"};
        for (int i = 0; i < titles.length; i++) {
            final int id = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(14), 0, dp(14));
            row.addView(tv(titles[i], 16, th.text));
            LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-2, -2);
            sp2.topMargin = dp(4);
            row.addView(tv(subs[i], 13, th.sub), sp2);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dg[0].dismiss();
                    if (id == 0) showThemes();
                    else if (id == 1) showColors();
                    else if (id == 2) showNotifSettings();
                    else if (id == 3) openPacks();
                    else if (id == 4) showShorts();
                    else if (id == 5) checkUpdate(true);
                    else { loadedWeek = -1; load(); }
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            if (i == 0) rp.topMargin = dp(10);
            box.addView(row, rp);
        }
        ScrollView ssv = new ScrollView(this);
        ssv.setVerticalScrollBarEnabled(false);
        ssv.addView(box);
        dg[0] = sheet(ssv, 0);
        dg[0].show();
    }

    private void showThemes() {
        final Dialog[] dg = new Dialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(14), dp(24), dp(20));
        box.addView(tv("Themes", 20, th.text));
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(-2, -2);
        subp.topMargin = dp(4);
        box.addView(tv("Pick a look for the whole app", 13, th.sub), subp);
        int cols = 3, total = Themes.ALL.length, cur = Themes.index(this);
        int cell = (getResources().getDisplayMetrics().widthPixels - dp(48) - dp(12) * (cols - 1)) / cols;
        int acc = Usage.accent(this);
        for (int i = 0; i < total; i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < cols; j++) {
                final int idx = i + j;
                LinearLayout.LayoutParams cp2 = new LinearLayout.LayoutParams(cell, -2);
                if (j > 0) cp2.leftMargin = dp(12);
                if (idx >= total) { row.addView(new View(this), cp2); continue; }
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.addView(new ThemePreview(this, Themes.ALL[idx], idx == cur, acc),
                        new LinearLayout.LayoutParams(cell, (int) (cell * 1.3f)));
                TextView nm = tv(Themes.ALL[idx].name, 12, th.text);
                nm.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams np2 = new LinearLayout.LayoutParams(-1, -2);
                np2.topMargin = dp(8);
                item.addView(nm, np2);
                item.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Themes.set(MainActivity.this, idx);
                        dg[0].dismiss();
                        new Thread(new Runnable() {
                            @Override public void run() { DWWidget.refreshAll(MainActivity.this); }
                        }).start();
                        recreate();
                    }
                });
                row.addView(item, cp2);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(16);
            box.addView(row, rp);
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(box);
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.78f);
        dg[0] = sheet(sv, maxH);
        dg[0].show();
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

    /** One screen = one week: the arrows jump a whole week, keeping the same weekday. */
    private void moveWeek(int delta) {
        Calendar n = (Calendar) sel.clone();
        n.add(Calendar.DAY_OF_YEAR, 7 * delta);
        Calendar t = Usage.midnight(Calendar.getInstance());
        if (n.getTimeInMillis() > t.getTimeInMillis()) {
            if (delta > 0 && Usage.weekStart(sel).getTimeInMillis() >= Usage.weekStart(t).getTimeInMillis()) return;
            n = t;
        }
        slideDir = delta;
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
        final boolean sameWeek = ws == loadedWeek;
        final boolean recent = sameWeek && System.currentTimeMillis() - lastLoad < 20000L;
        if ((sameWeek && !isTodayWeek(ws)) || recent) { render(); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                final Map<String, long[]> data = Usage.week(MainActivity.this, ws);
                PackageManager pm = getPackageManager();
                for (String p : data.keySet()) ensureMeta(pm, p);
                ui.post(new Runnable() {
                    @Override public void run() {
                        weekData = data;
                        loadedWeek = ws;
                        lastLoad = System.currentTimeMillis();
                        render();
                    }
                });
                if (System.currentTimeMillis() - lastWidget > 60000L) {
                    lastWidget = System.currentTimeMillis();
                    DWWidget.refreshAll(MainActivity.this);
                }
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
        long wkey = ws.getTimeInMillis();
        long key = wkey * 10 + idx;
        boolean anim = key != lastKey;
        boolean newWeek = wkey != lastWeekKey;
        lastKey = key;
        lastWeekKey = wkey;
        chart.setThresholds(Usage.thLow(this), Usage.thHigh(this));
        chart.set(totals, fut, idx, new ChartView.Listener() {
            @Override public void onDay(int i) {
                Calendar c = Usage.weekStart(sel);
                c.add(Calendar.DAY_OF_YEAR, i);
                sel = c;
                render();
            }
        });

        if (newWeek) {
            chart.grow();
            if (slideDir != 0) {
                chart.setTranslationX(slideDir * dp(36));
                chart.setAlpha(0f);
                chart.animate().translationX(0f).alpha(1f).setDuration(260).setInterpolator(decel).withLayer().start();
            }
        }
        animateTotal(totals[idx], anim);
        boolean today = isToday();
        Calendar y = Usage.midnight(Calendar.getInstance());
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (today) subTv.setText("Today");
        else if (sel.getTimeInMillis() == y.getTimeInMillis()) subTv.setText("Yesterday");
        else subTv.setText(new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(sel.getTime()));
        Calendar we = (Calendar) ws.clone();
        we.add(Calendar.DAY_OF_YEAR, 6);
        SimpleDateFormat rf = new SimpleDateFormat("MMM d", Locale.getDefault());
        dateTv.setText(rf.format(ws.getTime()) + " \u2013 " + rf.format(we.getTime()));
        nextBtn.setVisibility(ws.getTimeInMillis() < Usage.weekStart(Calendar.getInstance()).getTimeInMillis()
                ? View.VISIBLE : View.INVISIBLE);

        list.removeAllViews();
        PackageManager pm = getPackageManager();
        long maxMs = items.isEmpty() ? 1 : items.get(0).ms;
        int rowNo = 0;
        for (Item it : items) {
            View rv = row(it, ensureMeta(pm, it.pkg), maxMs);
            list.addView(rv);
            if (anim && rowNo < 8) animateRow(rv, rowNo);
            rowNo++;
        }
        if (anim) slideDir = 0;
    }

    private void animateTotal(final long to, boolean anim) {
        if (totalAnim != null) totalAnim.cancel();
        if (!anim || Math.abs(to - shownTotal) < 60000L) {
            totalTv.setText(Usage.fmtTotal(to));
            shownTotal = to;
            lastTotalStr = "";
            return;
        }
        final long from = shownTotal;
        totalAnim = ValueAnimator.ofFloat(0f, 1f);
        totalAnim.setDuration(500);
        totalAnim.setInterpolator(decel);
        totalAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                long v = from + (long) ((to - from) * (Float) a.getAnimatedValue());
                shownTotal = v;
                String t = Usage.fmtTotal(v);
                if (!t.equals(lastTotalStr)) { lastTotalStr = t; totalTv.setText(t); }
            }
        });
        totalAnim.start();
    }

    private void animateRow(View v, int i) {
        v.setAlpha(0f);
        if (slideDir != 0) v.setTranslationX(slideDir * dp(28)); else v.setTranslationY(dp(18));
        long delay = i * 40L;
        v.animate().alpha(1f).translationX(0f).translationY(0f)
                .setStartDelay(delay).setDuration(280).setInterpolator(decel).withLayer().start();
        Object f = v.getTag();
        if (f instanceof View) {
            View fl = (View) f;
            fl.setPivotX(0f);
            fl.setScaleX(0f);
            fl.animate().scaleX(1f).setStartDelay(delay + 140).setDuration(420).setInterpolator(decel).start();
        }
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
        TextView name = tv(m.label, 16, th.text);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        int lim = Usage.limit(this, it.pkg);
        TextView time = tv(Usage.fmtRow(it.ms) + (lim > 0 && !m.sys ? "  \u00b7  " + Usage.fmtTotal(lim * 60000L) + " limit" : ""), 13, th.sub);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(5);
        col.addView(time, tp);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        float frac = lim > 0 && !m.sys
                ? Math.max(0.03f, Math.min(1f, it.ms / (lim * 60000f)))
                : Math.max(0.05f, Math.min(1f, (float) it.ms / maxMs));
        View fill = new View(this);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(lim > 0 && !m.sys && it.ms >= lim * 60000L ? th.text : m.color);
        fg.setCornerRadius(dp(2));
        fill.setBackground(fg);
        bar.addView(fill, new LinearLayout.LayoutParams(0, dp(4), frac));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(4), 1f - frac));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(4));
        bp.topMargin = dp(7);
        bp.rightMargin = dp(8);
        col.addView(bar, bp);
        r.addView(col, cp);
        r.setTag(fill);

        View div = new View(this);
        div.setBackgroundColor(th.div);
        r.addView(div, new LinearLayout.LayoutParams(dp(1), dp(38)));

        ImageView act = new ImageView(this);
        act.setImageResource(m.sys ? R.drawable.ic_info : R.drawable.ic_hourglass);
        act.setColorFilter(Usage.accent(this));
        act.setPadding(dp(20), dp(20), dp(20), dp(20));
        r.addView(act, new LinearLayout.LayoutParams(dp(64), dp(64)));

        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDetail(it.pkg); }
        });
        act.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (m.sys) appInfo(it.pkg);
                else showTimer(it.pkg, m);
            }
        });
        return r;
    }

    private void openDetail(String pkg) {
        startActivity(new Intent(this, AppDetailActivity.class)
                .putExtra("pkg", pkg).putExtra("day", sel.getTimeInMillis()));
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }

    private void appInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (Exception ignored) {}
    }

    private void applyAccent() {
        int a = Usage.accent(this);
        more.setTextColor(a);
        prevBtn.setColorFilter(a);
        nextBtn.setColorFilter(a);
        totalTv.setTextColor(a);
        banner.setTextColor(a);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(a));
    }

    private void showNotifSettings() {
        final boolean[] on = {Usage.notifUpd(this), Usage.notifSum(this)};
        new AlertDialog.Builder(this, dlgStyle())
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
        l.addView(tv("Accent colour", 14, th.sub));
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

        TextView ct = tv("Chart colours", 14, th.sub);
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
        TextView e0 = tv("0h", 11, th.sub);
        ends.addView(e0, new LinearLayout.LayoutParams(0, -2, 1f));
        ends.addView(tv("12h", 11, th.sub));
        LinearLayout.LayoutParams enp = new LinearLayout.LayoutParams(-1, -2);
        enp.topMargin = dp(4);
        l.addView(ends, enp);

        final TextView lowTv = tv("", 14, th.text);
        final TextView highTv = tv("", 14, th.text);
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
        new AlertDialog.Builder(this, dlgStyle())
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
        final long t0 = lastCheck;
        if (manual) checking.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Updater.Info info = Updater.fetch();
                    if (info == null) throw new Exception("no release");
                    final int cur = Updater.installed(MainActivity.this);
                    after(t0, manual, new Runnable() {
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
                    if (manual) after(t0, true, new Runnable() {
                        @Override public void run() {
                            Toast.makeText(MainActivity.this, "Couldn't check for updates", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ---- In-app download + install
    private void installUpdate() {
        final String url = updUrl;
        if (url == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this, dlgStyle())
                    .setTitle("Allow installing updates")
                    .setMessage("Android needs your OK once. Turn on \"Allow from this source\", then come back and tap Update now again.")
                    .setPositiveButton("Open settings", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d2, int w) {
                            try {
                                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception ignored) {}
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(8));
        dlText = tv("Downloading update\u2026", 14, th.text);
        box.addView(dlText);
        dlBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        dlBar.setMax(100);
        dlBar.setIndeterminate(true);
        dlBar.setProgressTintList(ColorStateList.valueOf(Usage.accent(this)));
        dlBar.setIndeterminateTintList(ColorStateList.valueOf(Usage.accent(this)));
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(-1, -2);
        bp2.topMargin = dp(16);
        box.addView(dlBar, bp2);
        cancelDl = false;
        dlDialog = new AlertDialog.Builder(this, dlgStyle())
                .setTitle("Updating")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Cancel", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d2, int w) { cancelDl = true; }
                })
                .show();
        new Thread(new Runnable() {
            @Override public void run() {
                PackageInstaller.Session session = null;
                try {
                    PackageInstaller inst = getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    sp.setAppPackageName(getPackageName());
                    int id = inst.createSession(sp);
                    session = inst.openSession(id);
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(true);
                    final long len = c.getContentLengthLong();
                    InputStream in = c.getInputStream();
                    OutputStream out = session.openWrite("update.apk", 0, len > 0 ? len : -1);
                    byte[] buf = new byte[32768];
                    long done = 0;
                    int n, last = -1;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelDl) throw new Exception("cancelled");
                        out.write(buf, 0, n);
                        done += n;
                        if (len > 0) {
                            final int pct = (int) (done * 100 / len);
                            if (pct != last) {
                                last = pct;
                                ui.post(new Runnable() {
                                    @Override public void run() {
                                        dlBar.setIndeterminate(false);
                                        dlBar.setProgress(pct);
                                        dlText.setText("Downloading update\u2026 " + pct + "%");
                                    }
                                });
                            }
                        }
                    }
                    session.fsync(out);
                    out.close();
                    in.close();
                    ui.post(new Runnable() {
                        @Override public void run() { dlText.setText("Installing\u2026"); }
                    });
                    PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, id,
                            new Intent(MainActivity.this, InstallReceiver.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    session.commit(pi.getIntentSender());
                    session.close();
                    ui.post(new Runnable() {
                        @Override public void run() { if (dlDialog != null) dlDialog.dismiss(); }
                    });
                } catch (final Exception e) {
                    try { if (session != null) session.abandon(); } catch (Exception ignored) {}
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (dlDialog != null) dlDialog.dismiss();
                            if (!cancelDl) Toast.makeText(MainActivity.this,
                                    "Update failed. Check your internet and try again.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** Keeps the spinner visible for a moment so a fast check doesn't just blink. */
    private void after(long t0, boolean manual, final Runnable r) {
        long wait = manual ? Math.max(0, 900 - (System.currentTimeMillis() - t0)) : 0;
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                checking.setVisibility(View.GONE);
                r.run();
            }
        }, wait);
    }

    private void showUpdateDialog() {
        if (updUrl == null) return;
        new AlertDialog.Builder(this, dlgStyle())
                .setTitle("Update available")
                .setMessage("A new version of Digital Wellbeing (build " + updBuild
                        + ") is ready. It downloads right here and installs over this one.")
                .setPositiveButton("Update now", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d2, int w) {
                        installUpdate();
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    // ---- App packs
    private void openPacks() {
        startActivity(new Intent(this, PacksActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }

    // ---- YouTube Shorts counter
    private TextView stepBtn(String s) {
        TextView t = tv(s, 18, th.text);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.bg);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), th.div);
        t.setBackground(g);
        Fx.press(t);
        return t;
    }

    private void showShorts() {
        final boolean[] on = {Usage.shortsOn(this)};
        final int[] lim = {Usage.shortsLimit(this)};
        final int count = Usage.shortsCount(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(14), dp(24), dp(28));
        box.addView(tv("YouTube Shorts", 20, th.text));
        TextView sub = tv("Counts the Shorts you swipe through. At the limit, Shorts close and only regular videos "
                + "play until midnight.", 13, th.sub);
        sub.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(-2, -2);
        subp.topMargin = dp(6);
        box.addView(sub, subp);

        final TextView big = tv("0", 46, th.text);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = dp(22);
        box.addView(big, bp);
        final TextView of = tv("", 14, th.sub);
        LinearLayout.LayoutParams ofp = new LinearLayout.LayoutParams(-2, -2);
        ofp.topMargin = dp(6);
        box.addView(of, ofp);
        of.setText("Shorts today, of " + lim[0]);
        ValueAnimator va = ValueAnimator.ofInt(0, count);
        va.setDuration(count == 0 ? 1 : 800);
        va.setInterpolator(decel);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                big.setText(String.valueOf((int) a.getAnimatedValue()));
            }
        });
        va.start();

        // On / off
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        r1.addView(tv("Limit Shorts", 16, th.text), new LinearLayout.LayoutParams(0, -2, 1f));
        final android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(on[0]);
        sw.setThumbTintList(ColorStateList.valueOf(th.text));
        sw.setTrackTintList(ColorStateList.valueOf(th.grid));
        r1.addView(sw);
        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(-1, -2);
        r1p.topMargin = dp(26);
        box.addView(r1, r1p);

        // Limit stepper
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.addView(tv("Daily limit", 16, th.text), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView minus = stepBtn("−");
        final TextView val = tv(String.valueOf(lim[0]), 17, th.text);
        val.setGravity(Gravity.CENTER);
        TextView plus = stepBtn("+");
        r2.addView(minus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        r2.addView(val, new LinearLayout.LayoutParams(dp(56), -2));
        r2.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.topMargin = dp(20);
        box.addView(r2, r2p);

        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean c) {
                on[0] = c;
                Usage.setShorts(MainActivity.this, on[0], lim[0]);
            }
        });
        View.OnClickListener stepper = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int delta = ((TextView) v).getText().toString().equals("+") ? 5 : -5;
                lim[0] = Math.max(5, Math.min(500, lim[0] + delta));
                val.setText(String.valueOf(lim[0]));
                of.setText("Shorts today, of " + lim[0]);
                Usage.setShorts(MainActivity.this, on[0], lim[0]);
                val.setScaleX(0.85f);
                val.setScaleY(0.85f);
                val.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            }
        };
        minus.setOnClickListener(stepper);
        plus.setOnClickListener(stepper);

        TextView reset = tv("Reset today's count", 14, th.sub);
        reset.setPadding(0, dp(14), dp(20), dp(14));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Usage.shortsReset(MainActivity.this);
                big.setText("0");
                big.setAlpha(0f);
                big.animate().alpha(1f).setDuration(300).start();
            }
        });
        LinearLayout.LayoutParams rsp = new LinearLayout.LayoutParams(-2, -2);
        rsp.topMargin = dp(12);
        box.addView(reset, rsp);

        if (!serviceOn()) {
            TextView warn = tv("Turn on \"App timers\" in Accessibility settings so Shorts can be counted. Tap to open.",
                    13, th.text);
            warn.setLineSpacing(0, 1.15f);
            warn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { askService(); }
            });
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
            wp.topMargin = dp(6);
            box.addView(warn, wp);
        }
        sheet(box, 0).show();
    }

    // ---- App timers
    private boolean serviceOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private TextView timerChip(String label, final int minutes, final int[] cur, final Runnable changed) {
        TextView t = tv(label, 13, th.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.bg);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), th.div);
        t.setBackground(g);
        Fx.press(t);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cur[0] = minutes > 0 ? minutes : cur[0];
                changed.run();
            }
        });
        return t;
    }

    private void showTimer(final String pkg, final Meta m) {
        final int existing = Usage.limit(this, pkg);
        final int[] cur = {existing > 0 ? existing : 30};
        final long[] used = {0};
        final Dialog[] dg = new Dialog[1];

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(24));

        // Header: icon + name
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (m.icon != null) {
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(m.icon);
            head.addView(ic, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
        TextView ttl = tv(m.label + " timer", 20, th.text);
        ttl.setSingleLine(true);
        ttl.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(0, -2, 1f);
        ttp.leftMargin = dp(12);
        head.addView(ttl, ttp);
        box.addView(head);

        TextView hint = tv("Set how long you can use this app each day. At the limit it locks until midnight.", 13, th.sub);
        hint.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams hintp = new LinearLayout.LayoutParams(-2, -2);
        hintp.topMargin = dp(10);
        box.addView(hint, hintp);

        // Big value
        final TextView big = tv("", 44, th.text);
        LinearLayout.LayoutParams bigp = new LinearLayout.LayoutParams(-2, -2);
        bigp.topMargin = dp(22);
        box.addView(big, bigp);

        // Fill bar: how much of the chosen limit is already used today
        final FrameLayout track = new FrameLayout(this);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(th.div);
        tg.setCornerRadius(dp(5));
        track.setBackground(tg);
        final View fill = new View(this);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(th.text);
        fg.setCornerRadius(dp(5));
        fill.setBackground(fg);
        fill.setPivotX(0f);
        track.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, dp(10));
        trp.topMargin = dp(14);
        box.addView(track, trp);
        final TextView status = tv("", 13, th.sub);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = dp(10);
        box.addView(status, stp);

        final float[] shown = {0f};
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                big.setText(Usage.fmtTotal(cur[0] * 60000L));
                big.setScaleX(0.94f);
                big.setScaleY(0.94f);
                big.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                long lim = cur[0] * 60000L;
                float f = Math.min(1f, used[0] / (float) lim);
                ValueAnimator va = ValueAnimator.ofFloat(shown[0], f);
                va.setDuration(520);
                va.setInterpolator(decel);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator an) {
                        shown[0] = (float) an.getAnimatedValue();
                        fill.setScaleX(shown[0]);
                    }
                });
                va.start();
                status.setText(used[0] >= lim
                        ? "Already used " + Usage.fmtRow(used[0]) + " today. It will lock as soon as you open it."
                        : Usage.fmtRow(used[0]) + " used today \u00b7 " + Usage.fmtShort(lim - used[0]) + " left");
            }
        };
        refresh.run();

        // Presets
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        int[] pm = {15, 30, 60, 120, 180};
        String[] pl = {"15m", "30m", "1h", "2h", "3h"};
        for (int i = 0; i < pm.length; i++) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) cp.leftMargin = dp(8);
            chips.addView(timerChip(pl[i], pm[i], cur, refresh), cp);
        }
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, -2);
        chp.topMargin = dp(20);
        box.addView(chips, chp);

        // Fine steppers
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.HORIZONTAL);
        final int[] dl = {-60, -5, 5, 60};
        String[] sl = {"\u2212 1 hr", "\u2212 5 min", "+ 5 min", "+ 1 hr"};
        for (int i = 0; i < dl.length; i++) {
            final int delta = dl[i];
            TextView st = tv(sl[i], 13, th.text);
            st.setGravity(Gravity.CENTER);
            st.setPadding(dp(8), dp(10), dp(8), dp(10));
            GradientDrawable sg = new GradientDrawable();
            sg.setColor(th.sheet);
            sg.setCornerRadius(dp(18));
            sg.setStroke(dp(1), th.div);
            st.setBackground(sg);
            Fx.press(st);
            st.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cur[0] = Math.max(5, Math.min(24 * 60 - 1, cur[0] + delta));
                    refresh.run();
                }
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) sp.leftMargin = dp(8);
            steps.addView(st, sp);
        }
        LinearLayout.LayoutParams stpp = new LinearLayout.LayoutParams(-1, -2);
        stpp.topMargin = dp(10);
        box.addView(steps, stpp);

        if (!serviceOn()) {
            TextView warn = tv("The \"App timers\" accessibility service is off, so timers can't lock apps yet. Tap here to turn it on.",
                    13, th.text);
            warn.setLineSpacing(0, 1.15f);
            warn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { askService(); }
            });
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
            wp.topMargin = dp(18);
            box.addView(warn, wp);
        }

        // Save
        final TextView save = tv(existing > 0 ? "Update timer" : "Set timer", 16, th.bg);
        save.setGravity(Gravity.CENTER);
        GradientDrawable sv = new GradientDrawable();
        sv.setColor(th.text);
        sv.setCornerRadius(dp(26));
        save.setBackground(sv);
        Fx.press(save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                save.setOnClickListener(null);
                Usage.setLimit(MainActivity.this, pkg, cur[0]);
                save.setText("Timer set \u2713");
                save.setScaleX(0.92f);
                save.setScaleY(0.92f);
                save.animate().scaleX(1f).scaleY(1f).setDuration(320)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(4f)).start();
                ui.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (dg[0] != null) dg[0].dismiss();
                        render();
                        if (!serviceOn()) askService();
                    }
                }, 520);
            }
        });
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(-1, dp(52));
        svp.topMargin = dp(22);
        box.addView(save, svp);

        if (existing > 0) {
            TextView rm = tv("Remove timer", 14, th.sub);
            rm.setGravity(Gravity.CENTER);
            rm.setPadding(0, dp(16), 0, dp(4));
            rm.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Usage.setLimit(MainActivity.this, pkg, 0);
                    if (dg[0] != null) dg[0].dismiss();
                    render();
                }
            });
            box.addView(rm, new LinearLayout.LayoutParams(-1, -2));
        }

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.addView(box);
        dg[0] = sheet(sc, 0);
        dg[0].show();

        // Entrance: rows rise in one after another.
        for (int i = 0; i < box.getChildCount(); i++) {
            View c = box.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(dp(16));
            c.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 45L).setDuration(340)
                    .setInterpolator(decel).start();
        }

        // Today's real usage, so the bar fills to where you actually are.
        new Thread(new Runnable() {
            @Override public void run() {
                long u = 0;
                try {
                    if (Usage.hasAccess(MainActivity.this)) {
                        long[] a = Usage.range(MainActivity.this,
                                Usage.midnight(Calendar.getInstance()).getTimeInMillis(), 1).get(pkg);
                        if (a != null && a.length > 0) u = a[0];
                    }
                } catch (Exception ignored) {}
                final long uu = u;
                ui.post(new Runnable() {
                    @Override public void run() { used[0] = uu; refresh.run(); }
                });
            }
        }).start();
    }

    private void askService() {
        new AlertDialog.Builder(this, dlgStyle())
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
            int s = 24;
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
        new AlertDialog.Builder(this, dlgStyle())
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
