package io.perfoverlay.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public final class Prefs {
    public final SharedPreferences store;
    public Prefs(Context c) {
        store = c.getSharedPreferences("overlay", Context.MODE_PRIVATE);
        if (!store.getBoolean("features_v2", false)) store.edit().putBoolean("features_v2", true)
                .putBoolean("metric_FPS", true).putBoolean("metric_DEVICE_POWER", true).apply();
    }
    public int font() { return store.getInt("font", 12); }
    public int opacity() { return store.getInt("opacity", 55); }
    public int offset() { return store.getInt("offset", 0); }
    public int interval() { return store.getInt("interval", 1000); }
    public boolean shizuku() { return store.getBoolean("shizuku", true); }
    public boolean vertical() { return store.getBoolean("vertical", false); }
    public boolean autoRecord() { return store.getBoolean("auto_record", true); }
    public boolean separators() { return store.getBoolean("separators", true); }
    public int dischargeSign() {
        return android.os.Build.FINGERPRINT.equals(store.getString("current_sign_build", "")) ? store.getInt("discharge_sign", 0) : 0;
    }
    public void calibrateCurrent(int sign) {
        if (sign == dischargeSign()) return;
        store.edit().putInt("discharge_sign", sign).putString("current_sign_build", android.os.Build.FINGERPRINT).apply();
    }
    public boolean enabled(Metric m) { return store.getBoolean("metric_" + m.name(), m.defaultOn); }
    public String sensor(Metric m) { return store.getString("sensor_" + m.name(), "auto"); }
    public void put(String key, int value) { store.edit().putInt(key, value).apply(); }
    public void put(String key, boolean value) { store.edit().putBoolean(key, value).apply(); }
    public void put(String key, String value) { store.edit().putString(key, value).apply(); }
    public List<Metric> order() {
        List<Metric> items = new ArrayList<>();
        for (String key : store.getString("order", "").split(",")) {
            try { Metric m = Metric.valueOf(key); if (!items.contains(m)) items.add(m); }
            catch (IllegalArgumentException ignored) { }
        }
        for (Metric m : Metric.values()) if (!items.contains(m)) items.add(m);
        return items;
    }
    public void move(Metric m, int delta) {
        List<Metric> items = order(); int i = items.indexOf(m), j = i + delta;
        if (j < 0 || j >= items.size()) return;
        items.remove(i); items.add(j, m);
        List<String> keys = new ArrayList<>(); for (Metric item : items) keys.add(item.name());
        put("order", String.join(",", keys));
    }
}
