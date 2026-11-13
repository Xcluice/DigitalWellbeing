package com.xcluice.digitalwellbeing;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lists app packs: groups of apps that share one daily time limit. */
public class PacksActivity extends Activity {
    private Themes.T th;
    private float d;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.8f);
    private LinearLayout box;
    private int gen;

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(th.bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(th.text);
        back.setPadding(dp(14), dp(14), dp(14), dp(14));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView title = tv("App packs", 20, th.text);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView add = tv("New pack", 14, th.bg);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable ag = new GradientDrawable();
        ag.setColor(th.text);
        ag.setCornerRadius(dp(20));
        add.setBackground(ag);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openEdit(null); }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.rightMargin = dp(20);
        bar.addView(add, alp);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));

        ScrollView sv = new StretchScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(40));
        sv.addView(box, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }

    private void openEdit(String id) {
        Intent i = new Intent(this, PackEditActivity.class);
        if (id != null) i.putExtra("id", id);
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }

    private void load() {
        final int g = ++gen;
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Usage.Pack> l = Usage.packs(PacksActivity.this);
                Map<String, long[]> m = new HashMap<>();
                if (Usage.hasAccess(PacksActivity.this)) {
                    m = Usage.range(PacksActivity.this,
                            Usage.midnight(Calendar.getInstance()).getTimeInMillis(), 1);
                }
                final Map<String, long[]> mm = m;
                ui.post(new Runnable() {
                    @Override public void run() { if (g == gen) render(l, mm); }
                });
            }
        }).start();
    }

    private void render(List<Usage.Pack> packs, Map<String, long[]> today) {
        box.removeAllViews();
        TextView intro = tv("Group apps and give them one shared daily limit. When the combined time of all of them "
                + "reaches it, every app in the pack locks until midnight.", 13, th.sub);
        intro.setLineSpacing(0, 1.2f);
        box.addView(intro, new LinearLayout.LayoutParams(-2, -2));
        fadeIn(intro, 0);

        if (packs.isEmpty()) {
            TextView e = tv("No packs yet", 18, th.text);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
            ep.topMargin = dp(48);
            ep.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(e, ep);
            TextView e2 = tv("Tap \"New pack\" to create one, for example Social media with a 1 hour limit.", 14, th.sub);
            e2.setGravity(Gravity.CENTER);
            e2.setLineSpacing(0, 1.2f);
            LinearLayout.LayoutParams e2p = new LinearLayout.LayoutParams(-2, -2);
            e2p.topMargin = dp(10);
            e2p.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(e2, e2p);
            fadeIn(e, 1);
            fadeIn(e2, 2);
            return;
        }
        int idx = 1;
        for (Usage.Pack k : packs) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(14);
            box.addView(card(k, Usage.packUsed(k, today), idx), cp);
            idx++;
        }
    }

    private void fadeIn(View v, int idx) {
        v.setAlpha(0f);
        v.setTranslationY(dp(14));
        v.animate().alpha(1f).translationY(0f).setStartDelay(idx * 60L).setDuration(320).setInterpolator(decel).start();
    }

    private View card(final Usage.Pack k, long used, int idx) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.sheet);
        bg.setCornerRadius(dp(18));
        c.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView nm = tv(k.name, 17, th.text);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(tv(Usage.fmtShort(k.limit * 60000L), 14, th.sub));
        c.addView(top);

        TextView apps = tv(k.pkgs.size() == 1 ? "1 app" : k.pkgs.size() + " apps", 13, th.sub);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.topMargin = dp(4);
        c.addView(apps, ap);

        final long limMs = k.limit * 60000L;
        final float frac = limMs <= 0 ? 0f : Math.min(1f, used / (float) limMs);
        final FrameLayout track = new FrameLayout(this);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(th.div);
        tg.setCornerRadius(dp(4));
        track.setBackground(tg);
        final View fill = new View(this);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(th.text);
        fg.setCornerRadius(dp(4));
        fill.setBackground(fg);
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, dp(8));
        trp.topMargin = dp(14);
        c.addView(track, trp);

        final int delay = idx * 60 + 150;
        track.post(new Runnable() {
            @Override public void run() {
                final int w = track.getWidth();
                ValueAnimator a = ValueAnimator.ofFloat(0f, frac);
                a.setDuration(700);
                a.setStartDelay(delay);
                a.setInterpolator(decel);
                a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator an) {
                        float f = (float) an.getAnimatedValue();
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                        lp.width = f <= 0f ? 0 : Math.max(dp(8), (int) (w * f));
                        fill.setLayoutParams(lp);
                    }
                });
                a.start();
            }
        });

        boolean locked = limMs > 0 && used >= limMs && !Usage.packIgnored(this, k.id);
        String st = locked ? "Locked until midnight"
                : Usage.fmtRow(used) + " used · " + Usage.fmtShort(Math.max(0, limMs - used)) + " left";
        TextView stv = tv(st, 13, locked ? th.text : th.sub);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = dp(10);
        c.addView(stv, stp);

        LinearLayout icons = new LinearLayout(this);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        icons.setGravity(Gravity.CENTER_VERTICAL);
        PackageManager pm = getPackageManager();
        int shown = 0;
        for (String p : k.pkgs) {
            if (shown == 7) break;
            try {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(pm.getApplicationIcon(p));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(30), dp(30));
                if (shown > 0) ip.leftMargin = dp(8);
                icons.addView(iv, ip);
                shown++;
            } catch (Exception ignored) {}
        }
        if (k.pkgs.size() > shown) {
            TextView more = tv("+" + (k.pkgs.size() - shown), 13, th.sub);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
            mp.leftMargin = dp(10);
            icons.addView(more, mp);
        }
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(-2, -2);
        icp.topMargin = dp(14);
        c.addView(icons, icp);

        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openEdit(k.id); }
        });
        fadeIn(c, idx);
        return c;
    }
}
