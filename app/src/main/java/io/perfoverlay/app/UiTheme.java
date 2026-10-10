package io.perfoverlay.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** The settings UI palette. Overlay opacity and recorded values are independent of it. */
public final class UiTheme {
    public final boolean dark;
    public final int bg, card, surface, ink, muted, accent, softAccent, primaryInk, border, grid, amber, violet;
    private UiTheme(boolean dark) {
        this.dark = dark;
        bg = dark ? 0xFF101619 : 0xFFF2F6F5;
        card = dark ? 0xFF182125 : 0xFFFFFFFF;
        surface = dark ? 0xFF222F34 : 0xFFEAF1EF;
        ink = dark ? 0xFFECF2F4 : 0xFF172E2B;
        muted = dark ? 0xFF9CAFB7 : 0xFF506B66;
        accent = dark ? 0xFF8CDBC3 : 0xFF146B56;
        softAccent = dark ? 0xFF203D36 : 0xFFDCEFE6;
        primaryInk = dark ? 0xFF10261F : 0xFFFFFFFF;
        border = dark ? 0xFF34464C : 0xFFD8E4E0;
        grid = dark ? 0xFF2A383E : 0xFFDEE7E4;
        amber = dark ? 0xFFFFBF62 : 0xFF925000;
        violet = dark ? 0xFFB9A5E1 : 0xFF7653A8;
    }
    public static UiTheme get(Context context) {
        String selected = context.getSharedPreferences("overlay", Context.MODE_PRIVATE).getString("theme", "dark");
        boolean night = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return new UiTheme("system".equals(selected) ? night : !"light".equals(selected));
    }
    public static void beforeCreate(Activity activity) {
        activity.setTheme(get(activity).dark ? R.style.AppTheme : R.style.AppThemeLight);
    }
    public void window(Activity activity) {
        activity.getWindow().setStatusBarColor(bg); activity.getWindow().setNavigationBarColor(bg);
        int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
        flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    public GradientDrawable shape(Context context, int color, int radius, boolean stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color);
        drawable.setCornerRadius(radius * context.getResources().getDisplayMetrics().density);
        if (stroke) drawable.setStroke(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)), border);
        return drawable;
    }
}
