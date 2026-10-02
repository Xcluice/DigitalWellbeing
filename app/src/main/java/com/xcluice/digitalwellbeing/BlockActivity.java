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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF1F1F1F);
        root.setPadding((int) (32 * d), (int) (110 * d), (int) (32 * d), (int) (32 * d));

        root.addView(ic, new LinearLayout.LayoutParams((int) (72 * d), (int) (72 * d)));

        TextView t = new TextView(this);
        t.setText(label + " timer is up");
        t.setTextColor(Color.WHITE);
        t.setTextSize(24);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = (int) (28 * d);
        root.addView(t, tp);

        TextView s = new TextView(this);
        s.setText("You've reached your daily limit of " + Usage.fmtTotal(lim * 60000L)
                + ". The timer resets at midnight.");
        s.setTextColor(0xFFBDC1C6);
        s.setTextSize(15);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = (int) (14 * d);
        root.addView(s, sp);

        TextView close = new TextView(this);
        close.setText("Close app");
        close.setTextColor(0xFF1F1F1F);
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF8AB4F8);
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
        ign.setText("Ignore timer for today");
        ign.setTextColor(0xFF8AB4F8);
        ign.setTextSize(14);
        ign.setPadding((int) (20 * d), (int) (16 * d), (int) (20 * d), (int) (16 * d));
        ign.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Usage.ignoreToday(BlockActivity.this, pkg);
                Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                finish();
                if (li != null) startActivity(li);
            }
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
        ip.topMargin = (int) (8 * d);
        root.addView(ign, ip);

        setContentView(root);
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
