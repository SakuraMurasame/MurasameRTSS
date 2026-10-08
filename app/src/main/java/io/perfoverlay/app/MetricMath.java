package io.perfoverlay.app;

import java.util.Locale;

/** Unit conversions and sentinel validation, independent of Android for host testing. */
public final class MetricMath {
    private MetricMath() {}
    public static double number(String value) {
        try {
            double d = Double.parseDouble(value.trim());
            return Double.isFinite(d) ? d : Double.NaN;
        } catch (Exception e) { return Double.NaN; }
    }
    public static double frequencyGHz(String khz) {
        double d = number(khz);
        return d > 0 && d < 20_000_000 ? d / 1_000_000d : Double.NaN;
    }
    public static double thermalC(String milliC) {
        double raw = number(milliC);
        // Values such as 45 or 450 may use an OEM-specific scale; do not guess units.
        if (raw != 0 && Math.abs(raw) < 1000) return Double.NaN;
        double d = raw / 1000d;
        return d >= -30 && d <= 180 ? d : Double.NaN;
    }
    public static double batteryWatts(int microA, int milliV) {
        if (microA == Integer.MIN_VALUE || microA == Integer.MAX_VALUE || milliV <= 0
                || milliV > 30000 || Math.abs((long) microA) > 100_000_000L) return Double.NaN;
        return Math.abs((double) microA) * milliV / 1_000_000_000d;
    }
    public static double deviceWatts(int microA, int milliV, boolean externalPower, boolean discharging) {
        // Some vendor battery HALs invert current. An unplugged, discharging battery has
        // physically unambiguous direction; use magnitude without pretending the raw sign is standard.
        if (externalPower || !discharging) return Double.NaN;
        return batteryWatts(microA, milliV);
    }
    public static boolean isTemperatureSensor(String name) {
        String type = name.toLowerCase(Locale.ROOT);
        return !(type.contains("bcl") || type.contains("ibat") || type.equals("vbat") || type.equals("socd"));
    }
    public static float touchAlpha(int api, float systemLimit) {
        if (api < 31) return 1f;
        if (!Float.isFinite(systemLimit) || systemLimit <= 0 || systemLimit > 1) systemLimit = 0.8f;
        return Math.max(0, systemLimit - 0.01f);
    }
    public static int backgroundPaintAlpha(float requested, float windowAlpha) {
        if (windowAlpha <= 0) return 0;
        return Math.round(Math.max(0, Math.min(requested / windowAlpha, 1)) * 255);
    }
    public static String fixed(double d, int places) {
        return Double.isFinite(d) ? String.format(Locale.US, "%." + places + "f", d) : "--";
    }
}
