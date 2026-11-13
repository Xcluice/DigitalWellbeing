package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Create or edit one app pack: name, shared daily limit and the apps in it. */
public class PackEditActivity extends Activity {
    private Themes.T th;
    private float d;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.8f);

    private Usage.Pack pack;
    private boolean isNew;
    private int limit = 60;
    private final Set<String> sel = new LinkedHashSet<>();
    private EditText nameEt;
    private TextView limitTv, appsLabel;
    private LinearLayout appList;

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

        Usage.Pack existing = Usage.pack(this, getIntent().getStringExtra("id"));
        isNew = existing == null;
        pack = existing != null ? existing : new Usage.Pack();
        limit = pack.limit > 0 ? pack.limit : 60;
        sel.addAll(pack.pkgs);

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
        bar.addView(tv(isNew ? "New pack" : "Edit pack", 20, th.text), new LinearLayout.LayoutParams(0, -2, 1f));
        if (!isNew) {
            TextView del = tv("Delete", 14, th.sub);
            del.setPadding(dp(20), dp(14), dp(20), dp(14));
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirmDelete(); }
            });
            bar.addView(del);
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));

        ScrollView sv = new StretchScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setFillViewport(false);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(24));
        sv.addView(col, new LinearLayout.LayoutParams(-1, -2));

        // Name
        col.addView(tv("Name", 14, th.sub));
        nameEt = new EditText(this);
        nameEt.setText(pack.name);
        nameEt.setHint("e.g. Social media");
        nameEt.setTextColor(th.text);
        nameEt.setHintTextColor(th.grid);
        nameEt.setHighlightColor(th.div);
        nameEt.setTextSize(17);
        nameEt.setSingleLine(true);
        nameEt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        GradientDrawable ng = new GradientDrawable();
        ng.setColor(th.sheet);
        ng.setCornerRadius(dp(14));
        nameEt.setBackground(ng);
        nameEt.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(10);
        col.addView(nameEt, nlp);

        // Shared limit
        TextView lt = tv("Combined daily limit", 14, th.sub);
        LinearLayout.LayoutParams ltp = new LinearLayout.LayoutParams(-2, -2);
        ltp.topMargin = dp(28);
        col.addView(lt, ltp);
        limitTv = tv("", 34, th.text);
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(-2, -2);
        lvp.topMargin = dp(10);
        col.addView(limitTv, lvp);
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.HORIZONTAL);
        steps.addView(step("− 1 hr", -60));
        steps.addView(step("+ 1 hr", 60), stepLp());
        steps.addView(step("− 5 min", -5), stepLp());
        steps.addView(step("+ 5 min", 5), stepLp());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(14);
        col.addView(steps, sp);
        updateLimit(false);

        // Apps
        appsLabel = tv("Apps", 14, th.sub);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.topMargin = dp(30);
        col.addView(appsLabel, alp);
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams apl = new LinearLayout.LayoutParams(-1, -2);
        apl.topMargin = dp(6);
        col.addView(appList, apl);
        updateCount();

        // Save
        TextView save = tv("Save pack", 16, th.bg);
        save.setGravity(Gravity.CENTER);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(th.text);
        sg.setCornerRadius(dp(26));
        save.setBackground(sg);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(-1, dp(52));
        svp.setMargins(dp(20), dp(8), dp(20), dp(20));
        root.addView(save, svp);

        setContentView(root);
        loadApps();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }

    private LinearLayout.LayoutParams stepLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = dp(8);
        return p;
    }

    private TextView step(String s, final int delta) {
        TextView t = tv(s, 13, th.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.sheet);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), th.div);
        t.setBackground(g);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                limit = Math.max(5, Math.min(24 * 60, limit + delta));
                updateLimit(true);
            }
        });
        return t;
    }

    private void updateLimit(boolean pop) {
        limitTv.setText(Usage.fmtTotal(limit * 60000L));
        if (pop) {
            limitTv.setScaleX(0.94f);
            limitTv.setScaleY(0.94f);
            limitTv.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new OvershootInterpolator(3f)).start();
        }
    }

    private void updateCount() {
        appsLabel.setText(sel.isEmpty() ? "Apps" : "Apps · " + sel.size() + " selected");
    }

    private void loadApps() {
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = getPackageManager();
                final List<Object[]> apps = new ArrayList<>();
                for (String p : Usage.launchable(PackEditActivity.this)) {
                    try {
                        String label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)));
                        apps.add(new Object[]{label, p, pm.getApplicationIcon(p)});
                    } catch (Exception ignored) {}
                }
                Collections.sort(apps, new Comparator<Object[]>() {
                    @Override public int compare(Object[] a, Object[] b) {
                        boolean sa = sel.contains((String) a[1]), sb = sel.contains((String) b[1]);
                        if (sa != sb) return sa ? -1 : 1;
                        return ((String) a[0]).compareToIgnoreCase((String) b[0]);
                    }
                });
                ui.post(new Runnable() {
                    @Override public void run() { showApps(apps); }
                });
            }
        }).start();
    }

    private void showApps(List<Object[]> apps) {
        appList.removeAllViews();
        int i = 0;
        for (Object[] a : apps) {
            final String pkg = (String) a[1];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            ImageView ic = new ImageView(this);
            ic.setImageDrawable((Drawable) a[2]);
            row.addView(ic, new LinearLayout.LayoutParams(dp(38), dp(38)));

            TextView nm = tv((String) a[0], 16, th.text);
            nm.setSingleLine(true);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
            np.leftMargin = dp(14);
            np.rightMargin = dp(12);
            row.addView(nm, np);

            final FrameLayout mark = new FrameLayout(this);
            final TextView tick = tv("✓", 14, th.bg);
            tick.setGravity(Gravity.CENTER);
            mark.addView(tick, new FrameLayout.LayoutParams(-1, -1));
            paint(mark, tick, sel.contains(pkg));
            row.addView(mark, new LinearLayout.LayoutParams(dp(26), dp(26)));

            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean on = !sel.contains(pkg);
                    if (on) sel.add(pkg); else sel.remove(pkg);
                    paint(mark, tick, on);
                    mark.setScaleX(0.7f);
                    mark.setScaleY(0.7f);
                    mark.animate().scaleX(1f).scaleY(1f).setDuration(240)
                            .setInterpolator(new OvershootInterpolator(3f)).start();
                    updateCount();
                }
            });
            appList.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (i < 12) {
                row.setAlpha(0f);
                row.setTranslationY(dp(10));
                row.animate().alpha(1f).translationY(0f).setStartDelay(i * 25L).setDuration(260)
                        .setInterpolator(decel).start();
            }
            i++;
        }
    }

    private void paint(View mark, TextView tick, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (on) {
            g.setColor(th.text);
            g.setStroke(dp(2), th.text);
        } else {
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(2), th.grid);
        }
        mark.setBackground(g);
        tick.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private void save() {
        if (sel.isEmpty()) {
            Toast.makeText(this, "Pick at least one app", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Usage.Pack> all = Usage.packs(this);
        String nm = nameEt.getText().toString().trim();
        if (nm.isEmpty()) nm = "Pack " + (isNew ? all.size() + 1 : 1);
        pack.name = nm;
        pack.limit = limit;
        pack.pkgs = new LinkedHashSet<>(sel);
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(pack.id)) { all.set(i, pack); found = true; }
        }
        if (!found) all.add(pack);
        Usage.savePacks(this, all);
        if (!Usage.serviceOn(this)) askService(); else finish();
    }

    private int dlgStyle() {
        return th.light ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Dialog_Alert;
    }

    private void askService() {
        new AlertDialog.Builder(this, dlgStyle())
                .setTitle("Turn on app timers")
                .setMessage("Pack limits are enforced by the \"App timers\" accessibility service. Turn it on in Accessibility settings (under Installed apps or Downloaded apps).")
                .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) {
                        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) {}
                        finish();
                    }
                })
                .setNegativeButton("Later", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) { finish(); }
                })
                .setCancelable(false)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this, dlgStyle())
                .setTitle("Delete pack?")
                .setMessage("The apps stay as they are. Only the shared limit is removed.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) {
                        List<Usage.Pack> all = Usage.packs(PackEditActivity.this);
                        for (int i = all.size() - 1; i >= 0; i--) {
                            if (all.get(i).id.equals(pack.id)) all.remove(i);
                        }
                        Usage.savePacks(PackEditActivity.this, all);
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
