package io.perfoverlay.app;
import java.util.*;

/** One metric on a shared time axis. Build on the history queue. */
public final class HistorySeries {
    public final Metric metric;
    public final long from, to;
    public final List<HistoryStore.Point> points = new ArrayList<>();
    public final List<double[]> values = new ArrayList<>();
    public final double min, max, mean;
    public final int valid, groups;
    public HistorySeries(List<HistoryStore.Point> all, Metric metric, long from, long to) {
        this.metric = metric; this.from = from; this.to = Math.max(from + 1, to);
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY, sum = 0;
        int count = 0, width = 0;
        for (HistoryStore.Point point : all) if (point.elapsed >= from && point.elapsed <= to) {
            double[] row = HistoryMath.values(point.values.get(metric.name()), metric == Metric.CPU_FREQ);
            points.add(point); values.add(row); width = Math.max(width, row.length);
            for (double v : row) if (Double.isFinite(v)) { low = Math.min(low, v); high = Math.max(high, v); sum += v; count++; }
        }
        valid = count; groups = width; min = count == 0 ? Double.NaN : low; max = count == 0 ? Double.NaN : high;
        mean = count == 0 ? Double.NaN : sum / count;
    }
    public String value(HistoryStore.Point point) {
        HistoryStore.Point p = point != null ? point : points.isEmpty() ? null : points.get(points.size() - 1);
        return p == null ? "--" : p.values.getOrDefault(metric.name(), "--");
    }
    public String note(HistoryStore.Point point) {
        HistoryStore.Point p = point != null ? point : points.isEmpty() ? null : points.get(points.size() - 1);
        return p == null ? I18n.t("此范围暂无采样") : p.values.getOrDefault("INFO_" + metric.name(), I18n.t("旧记录可能未采集该指标"));
    }
    public String statistics() {
        if (valid == 0) return I18n.t("最低 --   最高 --   均值 --");
        return String.format(Locale.ROOT, I18n.t("最低 %.2f   最高 %.2f   均值 %.2f%s"), min, max, mean,
                metric == Metric.CPU_FREQ ? I18n.t("（全部组）") : "");
    }
}
