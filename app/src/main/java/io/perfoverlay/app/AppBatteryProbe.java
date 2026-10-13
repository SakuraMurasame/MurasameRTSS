package io.perfoverlay.app;

import android.os.IBinder;
import android.os.SystemClock;
import org.json.*;
import java.util.*;

/** Shizuku-only read path. Never resets batterystats, writes settings, or spawns commands. */
public final class AppBatteryProbe implements AutoCloseable {
    private final ServiceDump dump = new ServiceDump();
    public synchronized String sample() {
        JSONObject result = new JSONObject();
        try {
            String raw = dump.readLimited(8 * 1024 * 1024, "batterystats", "--charged");
            if (raw.contains("Permission Denial") || raw.contains("Permission denied")) {
                result.put("error", "系统拒绝读取应用耗电统计"); return result.toString();
            }
            AppBatteryData.Sample sample = AppBatteryParser.parse(raw, System.currentTimeMillis(), SystemClock.elapsedRealtime(), Double.NaN);
            Object pm = null; java.lang.reflect.Method packages = null;
            try {
                IBinder binder = (IBinder)Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package");
                pm = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
                packages = Class.forName("android.content.pm.IPackageManager").getMethod("getPackagesForUid", int.class);
            } catch (Exception ignored) { /* Preserve UID attribution when package lookup is unavailable. */ }
            result.put("wall", sample.wall).put("elapsed", sample.elapsed).put("epoch", sample.epoch)
                    .put("capacity", sample.capacity).put("computed", sample.computed);
            JSONArray rows = new JSONArray();
            for (Map.Entry<Integer,AppBatteryData.Usage> entry : sample.apps.entrySet()) {
                AppBatteryData.Usage usage = entry.getValue();
                JSONObject row = new JSONObject().put("uid", entry.getKey()).put("total", usage.total);
                if (Double.isFinite(usage.foreground + usage.background)) row.put("fg", usage.foreground).put("bg", usage.background);
                JSONArray names = new JSONArray();
                try {
                    String[] found = packages == null ? null : (String[])packages.invoke(pm, entry.getKey());
                    if (found != null) for (int i = 0; i < Math.min(found.length, 128); i++) names.put(found[i]);
                } catch (Exception ignored) { }
                row.put("packages", names); rows.put(row);
            }
            result.put("apps", rows);
            if (result.toString().length() > 400000) return "{\"error\":\"应用耗电统计过大\"}";
        } catch (IllegalArgumentException e) {
            try { result = new JSONObject().put("error", "系统未提供可识别的应用耗电统计"); } catch (Exception ignored) { }
        } catch (Exception e) {
            try { result = new JSONObject().put("error", "应用耗电读取失败"); } catch (Exception ignored) { }
        }
        return result.toString();
    }
    @Override public void close() { dump.close(); }
}
