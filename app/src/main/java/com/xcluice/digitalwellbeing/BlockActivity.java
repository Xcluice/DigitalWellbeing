package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BlockActivity extends Activity {
    private String pkg = "";
    private String packId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final Themes.T th = Themes.get(this);
        Themes.applyWindow(this);
        pkg = getIntent().getStringExtra("pkg");
        if (pkg == null) pkg = "";
        final float d = getResources().getDisplayMetrics().density;
        PackageManager pm = getPackageManager();
        String label = pkg;
        ImageView ic = new ImageView(this);
        try {
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            ic.setImageDrawable(pm.getApplicationIcon(pkg));
        } catch (Exception ignored) {}
        int lim = Usage.limit(this, pkg);
        packId = getIntent().getStringExtra("pack");
        final Usage.Pack pack = Usage.pack(this, packId);
        if (pack == null) packId = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding((int) (32 * d), (int) (90 * d), (int) (32 * d), (int) (32 * d));

        final android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        holder.addView(new Fx.Rings(this, th.grid), new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.FrameLayout.LayoutParams icp = new android.widget.FrameLayout.LayoutParams((int) (72 * d), (int) (72 * d));
        icp.gravity = Gravity.CENTER;
        holder.addView(ic, icp);
        root.addView(holder, new LinearLayout.LayoutParams((int) (190 * d), (int) (190 * d)));

        TextView t = new TextView(this);
        t.setText(pack != null ? pack.name + " pack is locked" : label + " timer is up");
        t.setTextColor(th.text);
        t.setTextSize(24);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = (int) (28 * d);
        root.addView(t, tp);

        TextView s = new TextView(this);
        s.setText(pack != null
                ? "Your " + pack.pkgs.size() + " apps in this pack have used their combined limit of "
                        + Usage.fmtTotal(pack.limit * 60000L) + " today. The timer resets at midnight."
                : "You've reached your daily limit of " + Usage.fmtTotal(lim * 60000L)
                        + ". The timer resets at midnight.");
        s.setTextColor(th.sub);
        s.setTextSize(15);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = (int) (14 * d);
        root.addView(s, sp);

        TextView close = new TextView(this);
        close.setText("Close app");
        close.setTextColor(th.bg);
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.text);
        g.setCornerRadius(24 * d);
        close.setBackground(g);
        close.setPadding((int) (40 * d), (int) (13 * d), (int) (40 * d), (int) (13 * d));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goHome(); }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = (int) (44 * d);
        root.addView(close, cp);

        TextView ign = new TextView(this);
        ign.setText(pack != null ? "Ignore pack for today" : "Ignore timer for today");
        ign.setTextColor(th.sub);
        ign.setTextSize(14);
        ign.setPadding((int) (20 * d), (int) (16 * d), (int) (20 * d), (int) (16 * d));
        ign.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (packId != null) Usage.ignorePackToday(BlockActivity.this, packId);
                else Usage.ignoreToday(BlockActivity.this, pkg);
                Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                finish();
                if (li != null) startActivity(li);
            }
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
        ip.topMargin = (int) (8 * d);
        root.addView(ign, ip);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(th.bg);
        frame.addView(new Fx.Particles(this, th.text, 22), new android.widget.FrameLayout.LayoutParams(-1, -1));
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        Fx.press(close);
        Fx.press(ign);
        Fx.loop(holder, "translationY", -5 * d, 5 * d, 2000);

        // Entrance motion: icon pops in, then the text and buttons rise one after another.
        ic.setAlpha(0f);
        ic.setScaleX(0.5f);
        ic.setScaleY(0.5f);
        ic.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
        View[] rise = {t, s, close, ign};
        for (int i = 0; i < rise.length; i++) {
            rise[i].setAlpha(0f);
            rise[i].setTranslationY(22 * d);
            rise[i].animate().alpha(1f).translationY(0f).setStartDelay(300 + i * 120L).setDuration(420)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }
    }

    private void goHome() {
        Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
        finish();
    }

    @Override
    public void onBackPressed() { goHome(); }
}
