package io.perfoverlay.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.view.Display;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Collector {
    private final Context context;
    private final Prefs prefs;
    private final ShizukuBridge bridge;
    private final HardwareProbe local = new HardwareProbe();
    public Collector(Context c, Prefs prefs, ShizukuBridge bridge) { context = c; this.prefs = prefs; this.bridge = bridge; }
    public Snapshot sample(boolean recordAll) {
        Snapshot s = new Snapshot(); s.timestamp = SystemClock.elapsedRealtime();
        JSONObject batteryInfo = new JSONObject();
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(mem);
        s.set(Metric.RAM, MetricMath.fixed(mem.availMem / 1073741824d, 1) + " GiB", "系统可用内存；总计 " + MetricMath.fixed(mem.totalMem / 1073741824d, 1) + " GiB");
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            if (battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                double temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) / 10d;
                if (temp >= -30 && temp <= 100) s.set(Metric.BAT_TEMP, MetricMath.fixed(temp, 1) + "°C", "Android 电池状态接口");
            }
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING;
            if (level >= 0 && scale > 0) s.set(Metric.BAT_LEVEL, Math.round(level * 100f / scale) + "%" + (charging ? " 充电" : ""), "Android 电池状态接口");
            int mv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int ua = context.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            double watts = MetricMath.batteryWatts(ua, mv);
            boolean plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            double deviceWatts = MetricMath.deviceWatts(ua, mv, plugged, status == BatteryManager.BATTERY_STATUS_DISCHARGING);
            if (Double.isFinite(deviceWatts) && Math.abs((long) ua) > 50000) prefs.calibrateCurrent(ua < 0 ? -1 : 1);
            if (Double.isFinite(deviceWatts)) s.set(Metric.DEVICE_POWER, "≈" + MetricMath.fixed(deviceWatts, 1) + " W", "未接外部电源：电池输出功率近似整机功耗，包含供电损耗；不是独立硬件功率计");
            else s.reasons.put(Metric.DEVICE_POWER, plugged ? "已接外部电源，电池净功率不能代表整机功耗；拔掉充电线后显示" : "电池电流、电压或放电状态无效，无法估算整机功耗");
            try {
                batteryInfo.put("currentMicroA", ua).put("voltageMilliV", mv).put("plugged", plugged).put("status", status)
                        .put("calibratedDischargeSign", prefs.dischargeSign());
            } catch (Exception ignored) { }
            if (Double.isFinite(watts)) {
                int sign = prefs.dischargeSign();
                String direction = sign == 0 ? "" : ua == 0 ? "净值 " : Integer.signum(ua) == sign ? "输出 " : "充入 ";
                s.set(Metric.BAT_POWER, direction + "≈" + MetricMath.fixed(watts, 1) + " W", "电池侧电流 × 电压估算；"
                        + (sign == 0 ? "电流方向尚未校准，未显示充入/输出；断开外部电源放电后自动校准" : "方向已通过未接电源的放电状态校准")
                        + "；不是 CPU/GPU 或充电器功率");
            } else s.reasons.put(Metric.BAT_POWER, "设备未提供有效电流或电压");
        }
        Display display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null) s.set(Metric.REFRESH, Math.round(display.getRefreshRate()) + " Hz", "屏幕刷新率，不是游戏 FPS");
        s.reasons.put(Metric.FPS, "需要 Shizuku 已授权并开启 FPS，随后切换到目标应用");
        s.reasons.put(Metric.FRAME_TIME, "与应用 FPS 使用同一图层的呈现时间戳");
        s.reasons.put(Metric.CPU_POWER, "设备独立 CPU 功率接口尚未适配");
        s.reasons.put(Metric.GPU_POWER, "设备独立 GPU 功率接口尚未适配");
        int mask = 0;
        if (recordAll || prefs.enabled(Metric.CPU_FREQ)) mask |= HardwareProbe.CPU;
        if (recordAll || prefs.enabled(Metric.GPU_FREQ)) mask |= HardwareProbe.GPU;
        if (recordAll || prefs.enabled(Metric.CPU_TEMP) || prefs.enabled(Metric.SOC_TEMP) || prefs.enabled(Metric.GPU_TEMP)) mask |= HardwareProbe.THERMAL;
        if (recordAll || prefs.enabled(Metric.FPS) || prefs.enabled(Metric.FRAME_TIME)) mask |= HardwareProbe.FPS;
        String json = prefs.shizuku() ? bridge.sample(mask) : null;
        boolean elevated = json != null;
        if (json == null) json = local.sample(mask);
        s.backend = elevated ? "Shizuku 采集" : prefs.shizuku() ? "普通模式 · " + bridge.status() : "普通模式";
        try {
            s.hardware = new JSONObject(json);
            s.hardware.put("battery", batteryInfo);
            JSONArray cpus = s.hardware.optJSONArray("cpus");
            List<String> values = new ArrayList<>();
            boolean valid = false, requested = false;
            if (cpus != null) for (int i = 0; i < cpus.length(); i++) {
                JSONObject cpu = cpus.getJSONObject(i);
                double freq = MetricMath.frequencyGHz(cpu.optString("khz"));
                values.add(MetricMath.fixed(freq, 1)); valid |= Double.isFinite(freq);
                requested |= "scaling_cur_freq".equals(cpu.optString("source"));
            }
            if (valid) s.set(Metric.CPU_FREQ, String.join("+", values) + " GHz", "按调频域最高规格频率降序；" + (requested ? "含驱动报告频率" : "硬件报告频率"));
            else s.reasons.put(Metric.CPU_FREQ, "CPU 调频域节点不可读、未提供或未启用");
            double gpu = s.hardware.optDouble("gpuMHz", Double.NaN);
            if (Double.isFinite(gpu)) s.set(Metric.GPU_FREQ, MetricMath.fixed(gpu, 0) + " MHz", s.hardware.optString("gpuSource"));
            else s.reasons.put(Metric.GPU_FREQ, "permission_denied".equals(s.hardware.optString("gpuStatus"))
                    ? (elevated ? "ROM 拒绝 Shizuku/shell 读取 GPU 节点，授权无法解除此限制" : "GPU 节点权限被拒绝；可连接 Shizuku 再检查")
                    : "disabled".equals(s.hardware.optString("gpuStatus")) ? "GPU 频率未启用" : "没有找到有效且已适配的 GPU 频率节点");
            JSONObject presentation = s.hardware.optJSONObject("presentation");
            if (presentation != null) {
                String source = presentation.optString("status") + "\n目标：" + presentation.optString("target") + "\n图层：" + presentation.optString("layer");
                double fps = presentation.optDouble("fps", Double.NaN), frameMs = presentation.optDouble("frameMs", Double.NaN);
                if (Double.isFinite(fps)) s.set(Metric.FPS, MetricMath.fixed(fps, 1), source);
                else s.reasons.put(Metric.FPS, source);
                if (Double.isFinite(frameMs)) s.set(Metric.FRAME_TIME, MetricMath.fixed(frameMs, 1) + " ms", source);
                else s.reasons.put(Metric.FRAME_TIME, source);
            }
            readTemperature(s, Metric.CPU_TEMP, "cpu");
            readTemperature(s, Metric.SOC_TEMP, "soc");
            readTemperature(s, Metric.GPU_TEMP, "gpu");
        } catch (Exception e) { s.backend += " · 硬件数据解析失败"; }
        return s;
    }
    private void readTemperature(Snapshot s, Metric metric, String prefix) throws Exception {
        JSONArray sensors = s.hardware.optJSONArray("sensors");
        String selected = prefs.sensor(metric), name = "";
        double best = Double.NaN;
        if (sensors != null) for (int i = 0; i < sensors.length(); i++) {
            JSONObject sensor = sensors.getJSONObject(i);
            String type = sensor.optString("name"), key = sensor.optString("id") + ":" + type;
            String lower = type.toLowerCase(Locale.ROOT);
            boolean match = "auto".equals(selected) ? lower.matches(prefix + "(?:[-_].*)?") : selected.equals(key);
            double c = sensor.optDouble("c", Double.NaN);
            if (match && Double.isFinite(c) && (!Double.isFinite(best) || c > best)) { best = c; name = type; }
        }
        if (Double.isFinite(best)) s.set(metric, MetricMath.fixed(best, 0) + "°C", ("auto".equals(selected) ? "同类最高温：" : "自选传感器：") + name);
        else s.reasons.put(metric, "尚未找到匹配且可读的传感器，可在设置中选择");
    }
}
