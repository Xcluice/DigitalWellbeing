package com.xcluice.digitalwellbeing;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;

final class Themes {
    private Themes() {}

    static class T {
        final String name;
        final int bg, text, sub, div, grid, sheet;
        final boolean light;

        T(String name, int bg, int text, int sub, int div, int grid, int sheet, boolean light) {
            this.name = name;
            this.bg = bg;
            this.text = text;
            this.sub = sub;
            this.div = div;
            this.grid = grid;
            this.sheet = sheet;
            this.light = light;
        }
    }

    static final T[] ALL = {
            new T("Dark Grey", 0xFF1F1F1F, 0xFFFFFFFF, 0xFFBDC1C6, 0xFF3C4043, 0xFF5F6368, 0xFF2B2B2B, false),
            new T("Pure Black", 0xFF000000, 0xFFFFFFFF, 0xFFB0B3B8, 0xFF2A2A2A, 0xFF3A3A3A, 0xFF121212, false),
            new T("Graphite", 0xFF2D2F33, 0xFFF1F3F4, 0xFFBDC1C6, 0xFF45484D, 0xFF5F6368, 0xFF383B40, false),
            new T("Grey", 0xFF4A4D52, 0xFFFFFFFF, 0xFFD2D5D9, 0xFF62666C, 0xFF7B7F86, 0xFF565A60, false),
            new T("Silver", 0xFFC9CCD1, 0xFF202124, 0xFF4A4D52, 0xFFADB1B8, 0xFF8E9299, 0xFFD5D8DC, true),
            new T("White", 0xFFFFFFFF, 0xFF202124, 0xFF5F6368, 0xFFE0E0E0, 0xFFBDBDBD, 0xFFF4F4F4, true),
            new T("White Grey", 0xFFF1F3F4, 0xFF202124, 0xFF5F6368, 0xFFDADCE0, 0xFFBDC1C6, 0xFFFFFFFF, true),
            new T("Cream", 0xFFF5EBDD, 0xFF3B2F2F, 0xFF6E5E52, 0xFFDCCBB5, 0xFFB9A58B, 0xFFFBF4E9, true),
            new T("Mint", 0xFFE6F4EA, 0xFF1E3A2B, 0xFF4C6B5A, 0xFFC4DECB, 0xFF9DBFA9, 0xFFF2FAF4, true),
            new T("Midnight Blue", 0xFF0D1B2A, 0xFFE0E6F0, 0xFFA5B4C8, 0xFF22364D, 0xFF3A5470, 0xFF16283B, false),
            new T("Forest", 0xFF10241B, 0xFFE3F1E8, 0xFFA9C4B3, 0xFF21402F, 0xFF3C6650, 0xFF193426, false),
            new T("Deep Purple", 0xFF1E1530, 0xFFEDE7F6, 0xFFBCAFD6, 0xFF372A52, 0xFF5B4A80, 0xFF2A1E42, false),
            new T("Ocean", 0xFF0B2A30, 0xFFDDF1F4, 0xFF9CC5CC, 0xFF1B454D, 0xFF33707B, 0xFF12383F, false),
            new T("Maroon", 0xFF2A1418, 0xFFF8E6E8, 0xFFD1A9AE, 0xFF4A252B, 0xFF7A4048, 0xFF381C21, false)
    };

    static int index(Context c) {
        int i = c.getSharedPreferences("dw_prefs", Context.MODE_PRIVATE).getInt("theme", 0);
        return i < 0 || i >= ALL.length ? 0 : i;
    }

    static T get(Context c) { return ALL[index(c)]; }

    static void set(Context c, int i) {
        c.getSharedPreferences("dw_prefs", Context.MODE_PRIVATE).edit().putInt("theme", i).apply();
    }

    /** Window background, status/navigation bar colours and icon contrast for the chosen theme. */
    static void applyWindow(Activity a) {
        T t = get(a);
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(t.bg));
        w.setStatusBarColor(t.bg);
        w.setNavigationBarColor(t.bg);
        View dv = w.getDecorView();
        int f = dv.getSystemUiVisibility();
        int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        dv.setSystemUiVisibility(t.light ? (f | mask) : (f & ~mask));
    }
}
