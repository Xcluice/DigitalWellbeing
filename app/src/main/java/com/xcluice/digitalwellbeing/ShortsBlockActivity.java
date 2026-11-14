package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.os.Bundle;

/** Fallback host for the Shorts-limit screen; the accessibility service normally draws it as an overlay. */
public class ShortsBlockActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.applyWindow(this);
        setContentView(new ShortsLockView(this, new Runnable() {
            @Override public void run() { finish(); }
        }));
    }
}
