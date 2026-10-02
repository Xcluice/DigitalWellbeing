package com.xcluice.digitalwellbeing;

import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.net.Uri;

public class UpdateJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters p) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (!Usage.notifUpd(UpdateJob.this)) return;
                    Updater.Info i = Updater.fetch();
                    if (i == null || i.build <= Updater.installed(UpdateJob.this)) return;
                    android.content.SharedPreferences sp = getSharedPreferences("dw_prefs", MODE_PRIVATE);
                    if (sp.getInt("upd_notified", 0) >= i.build) return;
                    PendingIntent pi = PendingIntent.getActivity(UpdateJob.this, 3,
                            new Intent(Intent.ACTION_VIEW, Uri.parse(i.url)),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    Notifs.post(UpdateJob.this, 11, Notifs.CH_UPD, "Digital Wellbeing update",
                            "Build " + i.build + " is ready. Tap to download, then open the file to install.", pi);
                    sp.edit().putInt("upd_notified", i.build).apply();
                } catch (Exception ignored) {
                } finally {
                    jobFinished(p, false);
                }
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return true; }
}
