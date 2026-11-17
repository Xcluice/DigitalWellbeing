package com.xcluice.digitalwellbeing;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Force-update gate. When a newer build exists the app shows only this screen until the update is
 * installed. Grey / white / black only, animated, back button does not get past it.
 */
public class UpdateActivity extends Activity {
    private static final int IDLE = 0, DOWNLOADING = 1, INSTALLING = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Themes.T th;
    private float d;
    private Glyph glyph;
    private TextView title, sub, status, button;
    private int state = IDLE;
    private boolean sawPause;
    private String url;
    private int build;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        th = Themes.get(this);
        Themes.applyWindow(this);
        d = getResources().getDisplayMetrics().density;

        build = getIntent().getIntExtra("build", Updater.forceBuild(this));
        url = getIntent().getStringExtra("url");
        if (url == null) url = Updater.forceUrl(this);
        if (build <= Updater.installed(this) && build > 0) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(th.bg);
        frame.addView(new Fx.Particles(this, th.text, 24), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding((int) (32 * d), (int) (84 * d), (int) (32 * d), (int) (32 * d));

        glyph = new Glyph(this, th.text, th.grid);
        col.addView(glyph, new LinearLayout.LayoutParams((int) (210 * d), (int) (210 * d)));

        title = text("Update required", 26, th.text);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = (int) (26 * d);
        col.addView(title, tp);

        sub = text("Digital Wellbeing won't work until you update. "
                + (build > 0 ? "Build " + build + " is ready, you're on build " + Updater.installed(this) + "."
                : "A newer build is ready."), 15, th.sub);
        sub.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = (int) (14 * d);
        col.addView(sub, sp);

        button = text("Update now", 16, th.bg);
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.text);
        g.setCornerRadius(26 * d);
        button.setBackground(g);
        button.setPadding((int) (48 * d), (int) (14 * d), (int) (48 * d), (int) (14 * d));
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = (int) (40 * d);
        col.addView(button, bp);

        status = text("Your timers and settings stay as they are.", 13, th.sub);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = (int) (18 * d);
        col.addView(status, stp);

        frame.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        Fx.press(button);

        // Entrance: the glyph pops in, then everything rises one after another.
        glyph.setAlpha(0f);
        glyph.setScaleX(0.5f);
        glyph.setScaleY(0.5f);
        glyph.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        View[] rise = {title, sub, button, status};
        for (int i = 0; i < rise.length; i++) Fx.rise(rise[i], 22 * d, 220 + i * 110L, 400);

        if (url == null) fetchInfo();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void fetchInfo() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Updater.Info i = Updater.fetch();
                    if (i == null) return;
                    ui.post(new Runnable() {
                        @Override public void run() { url = i.url; build = i.build; }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state == INSTALLING && sawPause) {
            // Came back from the system installer without updating (cancelled).
            sawPause = false;
            setState(IDLE);
            status.setText("The install was cancelled. Tap Update now to try again.");
        }
        if (Updater.installed(this) >= Updater.forceBuild(this)) {
            // Updated while this screen was open, or the pending build was already installed.
            Updater.clearForce(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (state == INSTALLING) sawPause = true;
    }

    private void setState(int s) {
        state = s;
        glyph.setMode(s);
        button.setEnabled(s == IDLE);
        button.animate().alpha(s == IDLE ? 1f : 0.35f).setDuration(200).start();
        button.setText(s == IDLE ? "Update now" : s == DOWNLOADING ? "Downloading\u2026" : "Installing\u2026");
    }

    private void start() {
        if (state != IDLE) return;
        if (url == null) {
            status.setText("Checking for the update\u2026 try again in a moment.");
            fetchInfo();
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            status.setText("Turn on \"Allow from this source\", then come back and tap Update now.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
            return;
        }
        setState(DOWNLOADING);
        status.setText("Starting download\u2026");
        final String u = url;
        new Thread(new Runnable() {
            @Override public void run() { download(u); }
        }).start();
    }

    private void download(String u) {
        PackageInstaller.Session session = null;
        try {
            PackageInstaller inst = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(getPackageName());
            int id = inst.createSession(sp);
            session = inst.openSession(id);
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
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
                out.write(buf, 0, n);
                done += n;
                if (len > 0) {
                    final int pct = (int) (done * 100 / len);
                    if (pct != last) {
                        last = pct;
                        ui.post(new Runnable() {
                            @Override public void run() {
                                glyph.setProgress(pct / 100f);
                                status.setText(pct + "%");
                            }
                        });
                    }
                }
            }
            session.fsync(out);
            out.close();
            in.close();
            ui.post(new Runnable() {
                @Override public void run() {
                    setState(INSTALLING);
                    status.setText("Almost there\u2026");
                }
            });
            PendingIntent pi = PendingIntent.getBroadcast(this, id,
                    new Intent(this, InstallReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pi.getIntentSender());
            session.close();
        } catch (Exception e) {
            try { if (session != null) session.abandon(); } catch (Exception ignored) {}
            ui.post(new Runnable() {
                @Override public void run() {
                    setState(IDLE);
                    button.setText("Try again");
                    status.setText("Couldn't download. Check your internet and try again.");
                }
            });
        }
    }

    /** Back never gets past the gate; it just sends the app to the background. */
    @Override
    public void onBackPressed() { moveTaskToBack(true); }

    /**
     * Animated mark: pulsing rings, a download arrow that bobs, and a ring that sweeps while idle,
     * fills with the download progress, then spins fast while installing. Greys only.
     */
    static class Glyph extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Path arrow = new Path();
        private final int fg, track;
        private final float d;
        private float t, shown, target, bobAmp = 1f;
        private int mode = IDLE;
        private ValueAnimator a;

        Glyph(Context c, int fg, int track) {
            super(c);
            this.fg = fg;
            this.track = track;
            d = c.getResources().getDisplayMetrics().density;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setMode(int m) {
            mode = m;
            if (m == IDLE) { target = 0f; shown = 0f; }
        }

        void setProgress(float f) { target = Math.max(0f, Math.min(1f, f)); }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(2600);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new LinearInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator an) {
                    t = (float) an.getAnimatedValue();
                    shown += (target - shown) * 0.12f;
                    invalidate();
                }
            });
            a.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (a != null) a.cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, s = Math.min(w, h);
            p.setStyle(Paint.Style.STROKE);

            // Soft pulse rings behind everything.
            p.setStrokeWidth(2f * d);
            p.setColor(fg);
            for (int i = 0; i < 3; i++) {
                float ph = (t + i / 3f) % 1f;
                p.setAlpha((int) ((1f - ph) * 90));
                cv.drawCircle(cx, cy, s * 0.30f + ph * s * 0.20f, p);
            }

            // Track + progress / sweep ring.
            float r = s * 0.30f;
            rf.set(cx - r, cy - r, cx + r, cy + r);
            p.setStrokeWidth(5f * d);
            p.setColor(track);
            p.setAlpha(255);
            cv.drawCircle(cx, cy, r, p);
            p.setColor(fg);
            if (mode == DOWNLOADING && target > 0f) {
                cv.drawArc(rf, -90f, 360f * shown, false, p);
            } else {
                float speed = mode == INSTALLING ? 3f : 1f;
                float start = (t * 360f * speed) % 360f;
                cv.drawArc(rf, start, 90f, false, p);
            }

            // Arrow with a tray, bobbing down and settling.
            float bob = (float) Math.sin(t * Math.PI * 2) * 4f * d * bobAmp;
            p.setStrokeWidth(4.5f * d);
            arrow.reset();
            arrow.moveTo(cx, cy - s * 0.11f + bob);
            arrow.lineTo(cx, cy + s * 0.07f + bob);
            arrow.moveTo(cx - s * 0.075f, cy + s * 0.00f + bob);
            arrow.lineTo(cx, cy + s * 0.075f + bob);
            arrow.lineTo(cx + s * 0.075f, cy + s * 0.00f + bob);
            cv.drawPath(arrow, p);
            cv.drawLine(cx - s * 0.085f, cy + s * 0.125f, cx + s * 0.085f, cy + s * 0.125f, p);
        }
    }
}
