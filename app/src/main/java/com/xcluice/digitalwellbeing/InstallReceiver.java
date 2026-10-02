package com.xcluice.digitalwellbeing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

/** Receives the result of the in-app update install session. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = confirmIntent(i);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Exception ignored) {}
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "Update failed" + (msg == null ? "" : ": " + msg), Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private static Intent confirmIntent(Intent i) {
        if (Build.VERSION.SDK_INT >= 33) return i.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        return i.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}
