package io.perfoverlay.app;

import java.util.EnumMap;
import org.json.JSONObject;

public final class Snapshot {
    public final EnumMap<Metric, String> values = new EnumMap<>(Metric.class);
    public final EnumMap<Metric, String> reasons = new EnumMap<>(Metric.class);
    public JSONObject hardware = new JSONObject();
    public long timestamp;
    public String backend = "普通模式";
    public Snapshot() {
        for (Metric m : Metric.values()) { values.put(m, "--"); reasons.put(m, "尚无数据"); }
    }
    public void set(Metric m, String value, String source) { values.put(m, value); reasons.put(m, source); }
    public static Snapshot demo() {
        Snapshot s = new Snapshot();
        s.values.put(Metric.FPS, "59.8"); s.values.put(Metric.FRAME_TIME, "16.7 ms");
        s.values.put(Metric.DEVICE_POWER, "≈5.2 W");
        s.values.put(Metric.CPU_FREQ, "3.2+2.8+2.1+1.4 GHz");
        s.values.put(Metric.GPU_FREQ, "680 MHz"); s.values.put(Metric.RAM, "5.6 GiB");
        s.values.put(Metric.CPU_TEMP, "60°C"); s.values.put(Metric.SOC_TEMP, "62°C");
        s.values.put(Metric.GPU_TEMP, "58°C"); s.values.put(Metric.BAT_TEMP, "38.5°C");
        s.values.put(Metric.BAT_POWER, "输出 ≈5.2 W"); s.values.put(Metric.BAT_LEVEL, "78%");
        s.values.put(Metric.REFRESH, "120 Hz");
        return s;
    }
}
