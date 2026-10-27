package com.makereadyos.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import androidx.core.view.WindowCompat;

/** Only a presentation preference is retained; no workspace or account data. */
final class WorkspaceTheme {
    static boolean valid(String mode) {
        return "light".equals(mode) || "dark".equals(mode) || "default".equals(mode);
    }
    static String saved(Context context) {
        String mode = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("mode", "light");
        return valid(mode) ? mode : "light";
    }
    static void save(Context context, String mode) {
        if (valid(mode)) context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", mode).apply();
    }
    static int background(String mode) { return Color.parseColor("light".equals(mode) ? "#f1f5f9" : "dark".equals(mode) ? "#000000" : "#091018"); }
    static int surface(String mode) { return Color.parseColor("light".equals(mode) ? "#ffffff" : "dark".equals(mode) ? "#080808" : "#151d28"); }
    static int text(String mode) { return Color.parseColor("light".equals(mode) ? "#182432" : "#f2f5fb"); }
    static int accent(String mode) { return Color.parseColor("light".equals(mode) ? "#086b62" : "#65d9c8"); }
    static void style(Activity activity, String mode) {
        activity.setTheme("light".equals(mode) ? R.style.AppTheme : R.style.AppThemeDark);
    }
    static void bars(Activity activity, View root, String mode) {
        root.setBackgroundColor(background(mode));
        var controller = WindowCompat.getInsetsController(activity.getWindow(), root);
        controller.setAppearanceLightStatusBars("light".equals(mode));
        controller.setAppearanceLightNavigationBars("light".equals(mode));
        activity.getWindow().setStatusBarColor(background(mode));
        activity.getWindow().setNavigationBarColor(background(mode));
    }
}
