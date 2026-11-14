package com.xcluice.digitalwellbeing;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "Timer is up" / "Pack is locked" screen. Drawn by the accessibility service as an overlay right on top
 * of the blocked app (instant), or hosted inside BlockActivity as a fallback. Grey / white / black only.
 */
public class AppLockView extends FrameLayout {
    private final Runnable onClose, onIgnore;
    private final float d;
    private final ImageView icon;
    private final TextView title, sub, close, ignore;

    public AppLockView(Context c, String pkg, String packId, Runnable onClose, Runnable onIgnore) {
        super(c);
        this.onClose = onClose;
        this.onIgnore = onIgnore;
        d = c.getResources().getDisplayMetrics().density;
        Themes.T th = Themes.get(c);

        PackageManager pm = c.getPackageManager();
        String label = pkg;
        icon = new ImageView(c);
        try {
            label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            icon.setImageDrawable(pm.getApplicationIcon(pkg));
        } catch (Exception ignored) {}
        int lim = Usage.limit(c, pkg);
        Usage.Pack pack = Usage.pack(c, packId);

        setBackgroundColor(th.bg);
        setClickable(true); // swallow touches so nothing leaks through to the blocked app
        setFocusable(true);
        setFocusableInTouchMode(true);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding((int) (32 * d), (int) (90 * d), (int) (32 * d), (int) (32 * d));

        final FrameLayout holder = new FrameLayout(c);
        holder.addView(new Fx.Rings(c, th.grid), new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams icp = new FrameLayout.LayoutParams((int) (72 * d), (int) (72 * d));
        icp.gravity = Gravity.CENTER;
        holder.addView(icon, icp);
        root.addView(holder, new LinearLayout.LayoutParams((int) (190 * d), (int) (190 * d)));
        holder.setTag("holder");

        title = new TextView(c);
        title.setText(pack != null ? pack.name + " pack is locked" : label + " timer is up");
        title.setTextColor(th.text);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = (int) (28 * d);
        root.addView(title, tp);

        sub = new TextView(c);
        sub.setText(pack != null
                ? "Your " + pack.pkgs.size() + " apps in this pack have used their combined limit of "
                        + Usage.fmtTotal(pack.limit * 60000L) + " today. The timer resets at midnight."
                : "You've reached your daily limit of " + Usage.fmtTotal(lim * 60000L)
                        + ". The timer resets at midnight.");
        sub.setTextColor(th.sub);
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = (int) (14 * d);
        root.addView(sub, sp);

        close = new TextView(c);
        close.setText("Close app");
        close.setTextColor(th.bg);
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.text);
        g.setCornerRadius(24 * d);
        close.setBackground(g);
        close.setPadding((int) (40 * d), (int) (13 * d), (int) (40 * d), (int) (13 * d));
        close.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { closeIt(); }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = (int) (44 * d);
        root.addView(close, cp);

        ignore = new TextView(c);
        ignore.setText(pack != null ? "Ignore pack for today" : "Ignore timer for today");
        ignore.setTextColor(th.sub);
        ignore.setTextSize(14);
        ignore.setPadding((int) (20 * d), (int) (16 * d), (int) (20 * d), (int) (16 * d));
        ignore.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { if (AppLockView.this.onIgnore != null) AppLockView.this.onIgnore.run(); }
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
        ip.topMargin = (int) (8 * d);
        root.addView(ignore, ip);

        addView(new Fx.Particles(c, th.text, 22), new LayoutParams(-1, -1));
        addView(root, new LayoutParams(-1, -1));
        Fx.press(close);
        Fx.press(ignore);
    }

    private void closeIt() {
        if (onClose != null) onClose.run();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) closeIt();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();
        View holder = findViewWithTag("holder");
        if (holder != null) Fx.loop(holder, "translationY", -5 * d, 5 * d, 2000);

        // Entrance motion: icon pops in, then the text and buttons rise one after another.
        icon.setAlpha(0f);
        icon.setScaleX(0.5f);
        icon.setScaleY(0.5f);
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        View[] rise = {title, sub, close, ignore};
        for (int i = 0; i < rise.length; i++) {
            rise[i].setAlpha(0f);
            rise[i].setTranslationY(22 * d);
            rise[i].animate().alpha(1f).translationY(0f).setStartDelay(200 + i * 100L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        }
    }
}
