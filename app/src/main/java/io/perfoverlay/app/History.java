package io.perfoverlay.app;

import android.content.Context;
import android.os.SystemClock;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** One disk queue shared by recording and history screens; no disk I/O on the UI thread. */
public final class History {
    private static History instance;
    public final ExecutorService io = Executors.newSingleThreadExecutor();
    private HistoryStore store;
    public volatile String active = "", status = "尚未开始记录";
    private History(Context c) {
        File folder = new File(c.getFilesDir(), "history");
        io.execute(() -> { try { store = new HistoryStore(folder); } catch (Exception e) { status = "记录存储不可用：" + e.getMessage(); } });
    }
    public static synchronized History get(Context c) {
        if (instance == null) instance = new History(c.getApplicationContext());
        return instance;
    }
    public HistoryStore store() {
        if (store == null) throw new IllegalStateException("记录存储不可用");
        return store;
    }
    public void begin() {
        io.execute(() -> {
            try { active = store().begin(System.currentTimeMillis(), SystemClock.elapsedRealtime()); status = "正在记录 · 最快每秒 1 个采样点"; }
            catch (Exception e) { status = "无法开始记录：" + e.getMessage(); }
        });
    }
    public void end() {
        io.execute(() -> { if (store != null) store.end(); if (!active.isEmpty()) status = "记录已保存"; active = ""; });
    }
    public void gap() { io.execute(() -> { if (store != null) store.gap(); }); }
    public void capture(Snapshot s, String expected) {
        if (expected.isEmpty() || s.timestamp <= 0) return;
        Map<String,String> values = new LinkedHashMap<>();
        for (Metric m : Metric.values()) {
            values.put(m.name(), s.values.get(m));
            String reason = s.reasons.get(m);
            if (reason != null) values.put("INFO_" + m.name(), reason.split("\n", 2)[0]);
        }
        org.json.JSONObject presentation = s.hardware.optJSONObject("presentation");
        if (presentation != null) values.put("TARGET_APP", presentation.optString("target", ""));
        long wall = System.currentTimeMillis();
        io.execute(() -> {
            try { store().append(expected, s.timestamp, wall, values); }
            catch (Exception e) { if (store != null) store.end(); active = ""; status = "记录已停止：" + e.getMessage(); }
        });
    }
}
