package io.perfoverlay.app;

import java.util.Locale;
import java.util.regex.*;

/** Conversion of the recorded display units; unavailable readings remain gaps. */
public final class HistoryMath {
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    public static double[] values(String raw, boolean cpu) {
        if (raw == null || raw.equals("--")) return new double[0];
        if (cpu) {
            String[] parts = raw.replace(" GHz", "").split("\\+", -1);
            double[] result = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try { result[i] = Double.parseDouble(parts[i]); } catch (NumberFormatException e) { result[i] = Double.NaN; }
            }
            return result;
        }
        Matcher m = NUMBER.matcher(raw);
        return m.find() ? new double[]{Double.parseDouble(m.group())} : new double[0];
    }
    public static String time(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    public static long parseTime(String text) {
        text = text.trim().replace('：', ':');
        if (text.matches("[0-9]{1,8}")) return Long.parseLong(text) * 1000;
        String[] parts = text.split(":", -1);
        if (parts.length != 3) throw new IllegalArgumentException("请输入 时:分:秒，例如 00:01:30");
        long h = Long.parseLong(parts[0]), m = Long.parseLong(parts[1]), s = Long.parseLong(parts[2]);
        if (h < 0 || h > 9999 || m < 0 || m > 59 || s < 0 || s > 59) throw new IllegalArgumentException("时间格式不正确");
        return (h * 3600 + m * 60 + s) * 1000;
    }
    public static boolean connects(long previousSequence, long sequence, long previousTime, long time) {
        return sequence == previousSequence + 1 && time > previousTime && time - previousTime <= 6000;
    }
}
