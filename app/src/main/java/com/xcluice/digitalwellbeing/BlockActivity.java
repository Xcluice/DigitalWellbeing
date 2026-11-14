package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Fallback host for the lock screen; the accessibility service normally draws it as an overlay. */
public class BlockActivity extends Activity {
    private String pkg = "";
    private String packId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.applyWindow(this);
        pkg = getIntent().getStringExtra("pkg");
        if (pkg == null) pkg = "";
        packId = getIntent().getStringExtra("pack");
        if (Usage.pack(this, packId) == null) packId = null;

        setContentView(new AppLockView(this, pkg, packId,
                new Runnable() {
                    @Override public void run() { goHome(); }
                },
                new Runnable() {
                    @Override public void run() {
                        if (packId != null) Usage.ignorePackToday(BlockActivity.this, packId);
                        else Usage.ignoreToday(BlockActivity.this, pkg);
                        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                        finish();
                        if (li != null) startActivity(li);
                    }
                }));
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
