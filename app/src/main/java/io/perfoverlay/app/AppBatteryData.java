package io.perfoverlay.app;

import java.util.*;

/** Cumulative Android attribution estimates, and differences between complete samples. */
public final class AppBatteryData {
    private AppBatteryData() { }
    public static final class Usage {
        public final double total, foreground, background;
        public final List<String> packages;
        public Usage(double total, double foreground, double background, List<String> packages) {
            this.total = total; this.foreground = foreground; this.background = background;
            List<String> sorted = new ArrayList<>(packages); Collections.sort(sorted);
            this.packages = Collections.unmodifiableList(sorted);
        }
    }
    public static final class Sample {
        public final long wall, elapsed;
        public final String epoch;
        public final double capacity, computed, volts;
        public final boolean valid;
        public final Map<Integer,Usage> apps;
        public Sample(long wall, long elapsed, String epoch, double capacity, double computed,
                double volts, boolean valid, Map<Integer,Usage> apps) {
            this.wall = wall; this.elapsed = elapsed; this.epoch = epoch;
            this.capacity = capacity; this.computed = computed; this.volts = volts; this.valid = valid;
            this.apps = Collections.unmodifiableMap(new TreeMap<>(apps));
        }
        public static Sample gap(long wall, long elapsed) {
            return new Sample(wall, elapsed, "", Double.NaN, Double.NaN, Double.NaN, false, Collections.emptyMap());
        }
    }
    public static final class Drain {
        public final double total, foreground, background, unknown;
        public final List<String> packages;
        Drain(double total, double foreground, double background, List<String> packages) {
            this.total = total; this.foreground = foreground; this.background = background;
            this.unknown = Math.max(0, total - foreground - background); this.packages = packages;
        }
    }
    public static final class Interval {
        public final Sample from, to;
        public final boolean valid;
        public final Map<Integer,Drain> apps;
        Interval(Sample from, Sample to, boolean valid, Map<Integer,Drain> apps) {
            this.from = from; this.to = to; this.valid = valid; this.apps = Collections.unmodifiableMap(apps);
        }
        public double watts(double mah) {
            if (!valid || !Double.isFinite(mah) || mah < 0 || from.volts < 2 || to.volts < 2
                    || from.volts > 30 || to.volts > 30 || !Double.isFinite(from.volts + to.volts)) return Double.NaN;
            return mah * (from.volts + to.volts) / 2 * 3600 / (to.elapsed - from.elapsed);
        }
    }
    public static Interval between(Sample from, Sample to) {
        Map<Integer,Drain> result = new TreeMap<>();
        if (!from.valid || !to.valid || to.elapsed <= from.elapsed || from.epoch.isEmpty()
                || !from.epoch.equals(to.epoch) || to.computed + 1e-8 < from.computed)
            return new Interval(from, to, false, result);
        for (Map.Entry<Integer,Usage> entry : to.apps.entrySet()) {
            Usage b = entry.getValue(), a = from.apps.get(entry.getKey());
            // A newly appearing or reassigned UID needs its own baseline. Never import earlier drain.
            if (a == null || !a.packages.equals(b.packages)) continue;
            double total = b.total - a.total;
            if (!Double.isFinite(total) || total < -1e-8) continue;
            total = Math.max(0, total);
            double fg = b.foreground - a.foreground, bg = b.background - a.background;
            if (!Double.isFinite(fg + bg) || fg < -1e-8 || bg < -1e-8 || fg + bg > total + 1e-8) fg = bg = 0;
            fg = Math.max(0, fg); bg = Math.max(0, Math.min(bg, total - fg));
            result.put(entry.getKey(), new Drain(total, fg, bg, b.packages));
        }
        return new Interval(from, to, true, result);
    }
    public static final class Summary {
        public final int uid;
        public double total, foreground, background, unknown;
        public long duration;
        public List<String> packages = Collections.emptyList();
        Summary(int uid) { this.uid = uid; }
    }
    public static Map<Integer,Summary> summarize(List<Interval> intervals, long from, long to) {
        if (from < 0 || to < from) throw new IllegalArgumentException("Invalid time range");
        Map<Integer,Summary> result = new TreeMap<>();
        for (Interval interval : intervals) {
            if (!interval.valid || interval.from.elapsed < from || interval.to.elapsed > to) continue;
            for (Map.Entry<Integer,Drain> entry : interval.apps.entrySet()) {
                Drain d = entry.getValue(); Summary s = result.computeIfAbsent(entry.getKey(), Summary::new);
                s.total += d.total; s.foreground += d.foreground; s.background += d.background; s.unknown += d.unknown;
                s.duration += interval.to.elapsed - interval.from.elapsed; s.packages = d.packages;
            }
        }
        return result;
    }
    public static double percent(double mah, double all) {
        return all > 0 && Double.isFinite(mah + all) && mah >= 0 ? 100 * mah / all : Double.NaN;
    }
}
