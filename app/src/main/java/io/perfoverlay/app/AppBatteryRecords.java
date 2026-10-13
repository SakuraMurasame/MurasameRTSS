package io.perfoverlay.app;

import android.content.Context;
import java.io.File;
import java.util.concurrent.*;

/** One disk queue for battery sessions and the battery screen. */
public final class AppBatteryRecords {
    private static AppBatteryRecords instance;
    public final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppBatteryStore store;
    public volatile String active = "", last = "", status = "尚未开始记录";
    public volatile int samples;
    private AppBatteryRecords(Context context) {
        File folder = new File(context.getFilesDir(), "app-battery");
        io.execute(() -> { try { store = new AppBatteryStore(folder); } catch (Exception e) { status = "耗电记录存储不可用"; } });
    }
    public static synchronized AppBatteryRecords get(Context context) {
        if (instance == null) instance = new AppBatteryRecords(context.getApplicationContext()); return instance;
    }
    public AppBatteryStore store() {
        if (store == null) throw new IllegalStateException("Storage unavailable"); return store;
    }
    public void begin(Runnable ready) {
        io.execute(() -> {
            try { active = store().begin(System.currentTimeMillis()); last = active; samples = 0; status = "等待首个耗电基线"; }
            catch (Exception e) { active = ""; status = "耗电记录存储不可用"; }
            ready.run();
        });
    }
    public void append(String expected, AppBatteryData.Sample sample, Runnable limit) {
        io.execute(() -> {
            try {
                if (expected.isEmpty() || !expected.equals(active)) return;
                store().append(expected, sample); if (sample.valid) samples++;
            } catch (Exception e) { status = "耗电记录已达容量上限或写入失败"; limit.run(); }
        });
    }
    public void end() {
        io.execute(() -> { if (store != null) store.end(); active = ""; });
    }
}
