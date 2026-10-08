package io.perfoverlay.app;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser for SurfaceFlinger's desired/actual/ready presentation timestamps. */
public final class FrameMath {
    public static final long WINDOW_NS = 1_000_000_000L;
    public static final class Result {
        public final boolean valid;
        public final double fps, frameMs;
        public final long latest;
        public final int frames;
        Result(boolean valid, double fps, double frameMs, long latest, int frames) {
            this.valid = valid; this.fps = fps; this.frameMs = frameMs; this.latest = latest; this.frames = frames;
        }
    }
    public static Result analyze(String dump, long now) {
        TreeSet<Long> presented = new TreeSet<>();
        for (String row : dump.split("\\r?\\n")) {
            String[] columns = row.trim().split("\\s+");
            if (columns.length != 3) continue;
            try {
                long actual = Long.parseLong(columns[1]);
                if (actual > 0 && actual != Long.MAX_VALUE && actual <= now) presented.add(actual);
            } catch (NumberFormatException ignored) { }
        }
        if (presented.size() < 2) return new Result(false, Double.NaN, Double.NaN, 0, 0);
        long latest = presented.last();
        if (now - latest > WINDOW_NS) return new Result(true, 0, Double.NaN, latest, 0);
        long start = Math.max(now - WINDOW_NS, presented.first());
        List<Long> inWindow = new ArrayList<>(presented.tailSet(start, false));
        double seconds = (now - start) / 1e9;
        if (seconds < .05) return new Result(false, Double.NaN, Double.NaN, latest, inWindow.size());
        double fps = inWindow.size() / seconds;
        double frameMs;
        if (inWindow.size() >= 2) {
            long first = inWindow.get(0), last = inWindow.get(inWindow.size() - 1);
            frameMs = (last - first) / 1e6 / (inWindow.size() - 1);
        } else frameMs = (latest - presented.lower(latest)) / 1e6;
        return new Result(fps <= 1000 && fps >= 0, fps, frameMs, latest, inWindow.size());
    }
    public static String foregroundPackage(String activityDump) {
        for (String key : new String[]{"topResumedActivity", "mResumedActivity"}) {
            Matcher match = Pattern.compile(key + "=.*?\\bu\\d+\\s+([A-Za-z0-9_.]+)\\/").matcher(activityDump);
            if (match.find()) return match.group(1);
        }
        return "";
    }
    public static List<String> candidateLayers(String list, String pkg) {
        List<String> names = new ArrayList<>();
        if (pkg.isEmpty() || pkg.equals("io.perfoverlay.app")) return names;
        for (String row : list.split("\\r?\\n")) {
            String name = row.trim();
            if (name.startsWith("RequestedLayerState{")) {
                Matcher m = Pattern.compile("^RequestedLayerState\\{(.+?#\\d+)(?:\\s|\\})").matcher(name);
                if (!m.find()) continue;
                name = m.group(1);
            }
            if (!name.contains(pkg + "/")) continue;
            if (name.contains("ActivityRecord{") || name.contains("InputSink") || name.contains("Background for")
                    || name.contains("- animation-leash") || name.contains("transition-leash") || name.startsWith("Surface(name=")) continue;
            if (!names.contains(name)) names.add(name);
        }
        names.sort((a, b) -> Integer.compare(score(b), score(a)));
        return names;
    }
    private static int score(String name) {
        return (name.contains("SurfaceView") ? 100 : 0) + (name.contains("BLAST") ? 10 : 0);
    }
}
