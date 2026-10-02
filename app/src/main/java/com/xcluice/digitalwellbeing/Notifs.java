package com.xcluice.digitalwellbeing;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;

final class Notifs {
    private Notifs() {}

    static final String CH_UPD = "updates";
    static final String CH_SUM = "summary";
    private static final int JOB_ID = 1001;

    static void channels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_UPD, "App updates", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_SUM, "Daily summary", NotificationManager.IMPORTANCE_LOW));
    }

    static boolean canPost(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static void post(Context c, int id, String ch, String title, String text, PendingIntent pi) {
        if (!canPost(c)) return;
        channels(c);
        Notification n = new Notification.Builder(c, ch)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, n);
    }

    static void scheduleAll(Context c) {
        channels(c);
        // update check every ~6 hours, survives reboot
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js.getPendingJob(JOB_ID) == null) {
                js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(6L * 60 * 60 * 1000)
                        .setPersisted(true)
                        .build());
            }
        } catch (Exception ignored) {}
        // daily summary around 9 PM
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 2, new Intent(c, SummaryReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (Usage.notifSum(c)) {
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, 21);
            t.set(Calendar.MINUTE, 0);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() < System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1);
            am.setInexactRepeating(AlarmManager.RTC, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        } else {
            am.cancel(pi);
        }
    }
}
