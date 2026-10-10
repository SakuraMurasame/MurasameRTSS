package io.perfoverlay.app;

import android.app.Application;
import android.content.*;
import android.content.res.Configuration;
import java.util.*;

public final class MurasameApp extends Application {
    public static final String LANGUAGE_CHANGED = "io.perfoverlay.app.LANGUAGE_CHANGED";
    @Override public void onCreate() { super.onCreate(); refreshLanguage(); }
    public String selection() { return getSharedPreferences("overlay", MODE_PRIVATE).getString("language", "system"); }
    public void selectLanguage(String code) {
        if (!Arrays.asList("system", "zh", "ja", "en", "fr", "de").contains(code)) code = "system";
        getSharedPreferences("overlay", MODE_PRIVATE).edit().putString("language", code).apply();
        refreshLanguage();
        sendBroadcast(new Intent(LANGUAGE_CHANGED).setPackage(getPackageName()));
    }
    private void refreshLanguage() {
        String selected = selection();
        Configuration config = new Configuration(getResources().getConfiguration());
        String language = "system".equals(selected) ? config.getLocales().get(0).getLanguage() : selected;
        config.setLocale(Locale.forLanguageTag(I18n.supported(language)));
        Context localized = createConfigurationContext(config);
        I18n.install(TranslationResources.load(localized));
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config); refreshLanguage();
        sendBroadcast(new Intent(LANGUAGE_CHANGED).setPackage(getPackageName()));
    }
}
