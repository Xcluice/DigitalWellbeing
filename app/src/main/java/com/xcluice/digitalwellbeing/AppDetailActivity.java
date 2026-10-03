package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AppDetailActivity extends Activity {
    private Themes.T th;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String pkg = "", label = "";
    private int color = 0xFF8AB4F8, accent;
    private Calendar day;
    private float d;
    private TextView totalTv, opensTv, peakTv, dateTv;
    private ImageView prev, next;
    private HourChart chart;
    private TextView bgTv, bgSub, fgTv, lastTv, mobNote;

    private int dp(float v) { return (int) (v * d + 0.5f); }

    private TextView tv(String s, float sp, int col) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(col);
        t.setIncludeFontPadding(false);
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        th = Themes.get(this);
        Themes.applyWindow(this);
        d = getResources().getDisplayMetrics().density;
        accent = Usage.accent(this);
        String p = getIntent().getStringExtra("pkg");
        pkg = p == null ? "" : p;
        day = Calendar.getInstance();
        day.setTimeInMillis(getIntent().getLongExtra("day", System.currentTimeMillis()));
        day = Usage.midnight(day);

        PackageManager pm = getPackageManager();
        Drawable icon = null;
        label = pkg;
        try {
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            icon = pm.getApplicationIcon(pkg);
            color = MainActivity.dominant(pm.getApplicationIcon(pkg));
        } catch (Exception ignored) {}

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(th.bg);

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(accent);
        back.setPadding(dp(14), dp(14), dp(14), dp(14));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        ScrollView sv = new StretchScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(4), dp(20), dp(40));
        sv.addView(col, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        if (icon != null) ic.setImageDrawable(icon);
        head.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView name = tv(label, 22, th.text);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = dp(16);
        head.addView(name, np);
        col.addView(head);

        totalTv = tv("", 30, accent);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(24);
        col.addView(totalTv, tp);
        opensTv = tv("", 14, th.sub);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2);
        op.topMargin = dp(8);
        col.addView(opensTv, op);
        peakTv = tv("", 14, th.sub);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.topMargin = dp(4);
        col.addView(peakTv, pp);

        TextView ht = tv("Hourly usage", 14, th.sub);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = dp(26);
        col.addView(ht, hp);
        chart = new HourChart(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        col.addView(chart, cp);

        FrameLayout dr = new FrameLayout(this);
        dateTv = tv("", 15, th.text);
        dr.addView(dateTv, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        prev = arrow(R.drawable.ic_prev);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START | Gravity.CENTER_VERTICAL);
        pl.leftMargin = dp(24);
        dr.addView(prev, pl);
        next = arrow(R.drawable.ic_next);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END | Gravity.CENTER_VERTICAL);
        nl.rightMargin = dp(24);
        dr.addView(next, nl);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveDay(-1); }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveDay(1); }
        });
        LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(-1, dp(48));
        drp.topMargin = dp(14);
        col.addView(dr, drp);

        TextView bt = tv("Background activity", 14, th.sub);
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(-2, -2);
        btp.topMargin = dp(28);
        col.addView(bt, btp);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(th.sheet);
        cg.setCornerRadius(dp(16));
        card.setBackground(cg);
        bgTv = tv("\u2026", 18, th.text);
        card.addView(bgTv);
        bgSub = tv("", 13, th.sub);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(-2, -2);
        l1.topMargin = dp(4);
        card.addView(bgSub, l1);
        fgTv = tv("", 14, th.text);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-2, -2);
        l2.topMargin = dp(14);
        card.addView(fgTv, l2);
        lastTv = tv("", 14, th.text);
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(-2, -2);
        l3.topMargin = dp(8);
        card.addView(lastTv, l3);
        mobNote = tv("Tap to include mobile data", 13, accent);
        mobNote.setVisibility(View.GONE);
        mobNote.setPadding(0, dp(8), 0, dp(4));
        mobNote.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (android.os.Build.VERSION.SDK_INT < 29) {
                    requestPermissions(new String[]{"android.permission.READ_PHONE_STATE"}, 9);
                }
            }
        });
        LinearLayout.LayoutParams l4 = new LinearLayout.LayoutParams(-2, -2);
        l4.topMargin = dp(6);
        card.addView(mobNote, l4);
        LinearLayout.LayoutParams cdp = new LinearLayout.LayoutParams(-1, -2);
        cdp.topMargin = dp(10);
        col.addView(card, cdp);
        TextView foot = tv("Data the app used while it was not on screen. Android does not share other apps' battery or CPU use.", 12, th.sub);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, -2);
        fp.topMargin = dp(8);
        col.addView(foot, fp);

        TextView info = tv("App info", 14, accent);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(24), dp(11), dp(24), dp(11));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1), 0xFF3F5A7A);
        info.setBackground(g);
        info.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                } catch (Exception ignored) {}
            }
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        ip.topMargin = dp(30);
        col.addView(info, ip);

        setContentView(root);
        load();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        load();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }

    private ImageView arrow(int res) {
        ImageView i = new ImageView(this);
        i.setImageResource(res);
        i.setColorFilter(accent);
        i.setPadding(dp(8), dp(8), dp(8), dp(8));
        return i;
    }

    private boolean isToday() {
        return day.getTimeInMillis() >= Usage.midnight(Calendar.getInstance()).getTimeInMillis();
    }

    private void moveDay(int delta) {
        Calendar n = (Calendar) day.clone();
        n.add(Calendar.DAY_OF_YEAR, delta);
        if (n.getTimeInMillis() > Usage.midnight(Calendar.getInstance()).getTimeInMillis()) return;
        day = n;
        load();
    }

    private static String hourName(int h) {
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (h < 12 ? " AM" : " PM");
    }

    private void load() {
        final long start = day.getTimeInMillis();
        dateTv.setText(isToday() ? "Today" : new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(day.getTime()));
        next.setVisibility(isToday() ? View.INVISIBLE : View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                final Usage.Detail det = Usage.detail(AppDetailActivity.this, pkg, start);
                final NetUsage.R net = NetUsage.query(AppDetailActivity.this, pkg, start, start + 86400000L);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (start != day.getTimeInMillis()) return;
                        long total = 0;
                        int peak = -1;
                        long best = 0;
                        for (int i = 0; i < 24; i++) {
                            total += det.hours[i];
                            if (det.hours[i] > best) { best = det.hours[i]; peak = i; }
                        }
                        totalTv.setText(Usage.fmtTotal(total));
                        opensTv.setText(det.opens == 1 ? "Opened 1 time" : "Opened " + det.opens + " times");
                        peakTv.setText(peak < 0 ? "No usage this day"
                                : "Busiest hour: " + hourName(peak) + " \u00b7 " + Usage.fmtTotal(best));
                        chart.set(det.hours, color);
                        bgTv.setText(NetUsage.fmt(net.bg()) + " in the background");
                        bgSub.setText("Wi\u2011Fi " + NetUsage.fmt(net.wifiBg) + "  \u00b7  Mobile "
                                + (net.mobileDenied ? "not allowed" : NetUsage.fmt(net.mobBg)));
                        fgTv.setText("While open: " + NetUsage.fmt(net.fg()));
                        lastTv.setText("Last used: " + (det.lastEnd > 0
                                ? new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new java.util.Date(det.lastEnd))
                                : "\u2014"));
                        mobNote.setVisibility(net.mobileDenied ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }).start();
    }
}
