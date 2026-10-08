package io.perfoverlay.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Read-only fixed sysfs paths. Used both in the app and in Shizuku's shell process. */
public final class HardwareProbe {
    public static final int CPU = 1, GPU = 2, THERMAL = 4, FPS = 8;
    private final List<File> policies = new ArrayList<>(), thermal = new ArrayList<>();
    private final List<String> gpuPaths = new ArrayList<>();
    private long discovered;
    private static final class ReadResult {
        String value = "", error = "";
    }
    private static ReadResult readDetailed(String path) {
        ReadResult result = new ReadResult();
        try (FileInputStream stream = new FileInputStream(path)) {
            byte[] data = new byte[4096]; int n = stream.read(data);
            result.value = n > 0 ? new String(data, 0, n, StandardCharsets.UTF_8).trim() : "";
        } catch (Exception e) {
            String message = String.valueOf(e.getMessage());
            result.error = message.contains("Permission denied") || message.contains("EACCES") ? "permission_denied"
                    : message.contains("No such file") || message.contains("ENOENT") ? "not_found" : "read_failed";
        }
        return result;
    }

    static String read(String path) {
        try (FileInputStream stream = new FileInputStream(path)) {
            byte[] data = new byte[4096];
            int n = stream.read(data);
            return n > 0 ? new String(data, 0, n, StandardCharsets.UTF_8).trim() : "";
        } catch (Exception e) { return ""; }
    }
    private void discover() {
        policies.clear(); thermal.clear(); gpuPaths.clear();
        File[] dirs = new File("/sys/devices/system/cpu/cpufreq").listFiles();
        if (dirs != null) for (File f : dirs) if (f.getName().matches("policy\\d+")) policies.add(f);
        // Fixed order across samples: current frequency must not reorder the groups.
        policies.sort(Comparator.<File>comparingDouble(f -> {
            double n = MetricMath.number(read(f + "/cpuinfo_max_freq"));
            return Double.isFinite(n) ? n : 0;
        }).reversed().thenComparing(File::getName));
        dirs = new File("/sys/class/thermal").listFiles();
        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(File::getName));
            for (File f : dirs) if (f.getName().matches("thermal_zone\\d+") && thermal.size() < 128) thermal.add(f);
        }
        gpuPaths.add("/sys/class/kgsl/kgsl-3d0/gpuclk");
        gpuPaths.add("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
        dirs = new File("/sys/class/devfreq").listFiles();
        if (dirs != null) for (File f : dirs) {
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("gpu") || name.contains("mali") || name.contains("kgsl")) gpuPaths.add(f + "/cur_freq");
        }
        discovered = android.os.SystemClock.elapsedRealtime();
    }
    public synchronized String sample(int mask) {
        try {
            if (discovered == 0 || android.os.SystemClock.elapsedRealtime() - discovered > 60000) discover();
            JSONObject root = new JSONObject();
            root.put("uid", android.os.Process.myUid());
            JSONArray cpus = new JSONArray();
            if ((mask & CPU) != 0) for (File dir : policies) {
                JSONObject cpu = new JSONObject();
                cpu.put("policy", dir.getName());
                cpu.put("cores", read(dir + "/related_cpus"));
                cpu.put("maxKhz", read(dir + "/cpuinfo_max_freq"));
                String value = read(dir + "/cpuinfo_cur_freq"), source = "cpuinfo_cur_freq";
                if (!Double.isFinite(MetricMath.frequencyGHz(value))) { value = read(dir + "/scaling_cur_freq"); source = "scaling_cur_freq"; }
                cpu.put("khz", value); cpu.put("source", source);
                cpus.put(cpu);
            }
            root.put("cpus", cpus);
            if ((mask & GPU) != 0) {
                JSONArray errors = new JSONArray(); boolean denied = false;
                root.put("gpuStatus", "unavailable");
                for (String path : gpuPaths) {
                    ReadResult read = readDetailed(path);
                    double hz = MetricMath.number(read.value);
                    if (hz >= 1_000_000 && hz <= 10_000_000_000d) {
                        root.put("gpuMHz", hz / 1_000_000d); root.put("gpuSource", path);
                        root.put("gpuStatus", "ok"); break;
                    }
                    denied |= "permission_denied".equals(read.error);
                    errors.put(new JSONObject().put("path", path).put("error", read.error.isEmpty() ? "invalid_value" : read.error));
                }
                if (!root.has("gpuMHz") && denied) root.put("gpuStatus", "permission_denied");
                root.put("gpuErrors", errors);
            } else root.put("gpuStatus", "disabled");
            JSONArray sensors = new JSONArray();
            if ((mask & THERMAL) != 0) for (File dir : thermal) {
                String type = read(dir + "/type"), raw = read(dir + "/temp");
                if (type.isEmpty()) continue;
                JSONObject sensor = new JSONObject();
                sensor.put("id", dir.getName()); sensor.put("name", type); sensor.put("raw", raw);
                double c = MetricMath.thermalC(raw);
                if (Double.isFinite(c) && MetricMath.isTemperatureSensor(type)) sensor.put("c", c);
                sensors.put(sensor);
            }
            root.put("sensors", sensors);
            return root.toString();
        } catch (Exception e) { return "{\"error\":\"硬件读取失败\"}"; }
    }
}
