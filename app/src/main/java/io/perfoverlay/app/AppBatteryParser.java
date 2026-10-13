package io.perfoverlay.app;

import java.util.*;
import java.util.regex.*;

/** Parses only the estimated-charge section of a read-only batterystats --charged dump. */
public final class AppBatteryParser {
    private static final String NUMBER = "([0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)";
    private static final Pattern UID = Pattern.compile("^\\s*UID\\s+(\\S+):\\s*" + NUMBER + "(?:\\s|$)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAPACITY = Pattern.compile("Capacity:\\s*" + NUMBER + ",\\s*Computed drain:\\s*" + NUMBER);
    private static final Pattern STATE = Pattern.compile("(?:^|\\s)(fg|bg|fgs|cached):\\s*" + NUMBER + "(?=\\s|$)");
    private static final Pattern APP_UID = Pattern.compile("u([0-9]+)([ais])([0-9]+)");
    public static int uid(String value) {
        try {
            if (value.matches("[0-9]+")) return Integer.parseInt(value);
            Matcher m = APP_UID.matcher(value); if (!m.matches()) return -1;
            long local = Long.parseLong(m.group(3)) + (m.group(2).equals("a") ? 10000 : m.group(2).equals("i") ? 99000 : 0);
            long result = Long.parseLong(m.group(1)) * 100000 + local;
            return local < 100000 && result <= Integer.MAX_VALUE ? (int)result : -1;
        } catch (Exception e) { return -1; }
    }
    public static AppBatteryData.Sample parse(String raw, long wall, long elapsed, double volts) {
        String epoch = ""; double capacity = Double.NaN, computed = Double.NaN;
        Map<Integer,AppBatteryData.Usage> apps = new TreeMap<>(); boolean section = false;
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Start clock time:")) epoch = trimmed.substring(17).trim();
            if (trimmed.contains("Estimated power use (mAh):")) { section = true; continue; }
            if (!section) continue;
            Matcher c = CAPACITY.matcher(trimmed);
            if (c.find()) { capacity = MetricMath.number(c.group(1)); computed = MetricMath.number(c.group(2)); continue; }
            Matcher m = UID.matcher(line); if (!m.matches()) continue;
            int uid = uid(m.group(1)); double total = MetricMath.number(m.group(2));
            if (uid < 0 || !Double.isFinite(total) || apps.containsKey(uid)) continue;
            Matcher state = STATE.matcher(m.group(3)); boolean known = false; double fg = 0, bg = 0;
            Set<String> seen = new HashSet<>();
            while (state.find()) {
                if (!seen.add(state.group(1))) continue;
                double n = MetricMath.number(state.group(2)); if (!Double.isFinite(n)) continue;
                known = true; if (state.group(1).equals("fg")) fg += n; else bg += n;
            }
            apps.put(uid, new AppBatteryData.Usage(total, known ? fg : Double.NaN,
                    known ? bg : Double.NaN, Collections.emptyList()));
            if (apps.size() > 2048) throw new IllegalArgumentException("Too many UIDs");
        }
        if (!section || epoch.isEmpty() || !Double.isFinite(capacity + computed) || capacity <= 0 || apps.isEmpty())
            throw new IllegalArgumentException("Battery attribution unavailable");
        return new AppBatteryData.Sample(wall, elapsed, epoch, capacity, computed, volts, true, apps);
    }
    private AppBatteryParser() { }
}
