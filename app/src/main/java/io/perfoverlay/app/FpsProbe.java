package io.perfoverlay.app;

import org.json.JSONObject;
import java.util.List;

/** Measures actual presentation of one foreground-app layer, never display Hz or our overlay. */
public final class FpsProbe implements AutoCloseable {
    private final ServiceDump dump = new ServiceDump();
    private String target = "", layer = "";
    private long lastDiscovery;
    private JSONObject cached;
    private long lastRead;
    public synchronized JSONObject sample() {
        long now = System.nanoTime();
        if (cached != null && now - lastRead < 400_000_000L) return cached;
        JSONObject out = new JSONObject();
        try {
            String current = FrameMath.foregroundPackage(dump.read("activity", "activities"));
            if (!current.equals(target)) { target = current; layer = ""; lastDiscovery = 0; }
            out.put("target", target);
            if (target.isEmpty() || target.equals("io.perfoverlay.app")) {
                out.put("status", "请切换到要监控的游戏或应用");
                return save(out, now);
            }
            FrameMath.Result result = null;
            if (!layer.isEmpty()) result = FrameMath.analyze(dump.read("SurfaceFlinger", "--latency", layer), System.nanoTime());
            if ((result == null || !result.valid || now - result.latest > FrameMath.WINDOW_NS)
                    && (lastDiscovery == 0 || now - lastDiscovery > 2_000_000_000L)) {
                lastDiscovery = now;
                List<String> candidates = FrameMath.candidateLayers(dump.read("SurfaceFlinger", "--list"), target);
                FrameMath.Result best = null; String bestName = "";
                for (int i = 0; i < Math.min(10, candidates.size()); i++) {
                    String name = candidates.get(i);
                    FrameMath.Result r = FrameMath.analyze(dump.read("SurfaceFlinger", "--latency", name), System.nanoTime());
                    if (!r.valid) continue;
                    if (best == null || r.latest > best.latest) { best = r; bestName = name; }
                    if (r.fps > 0 && name.contains("SurfaceView")) { best = r; bestName = name; break; }
                }
                if (best != null) { result = best; layer = bestName; }
                else { result = null; layer = ""; }
            }
            out.put("layer", layer);
            if (result != null && result.valid) {
                out.put("fps", result.fps);
                if (Double.isFinite(result.frameMs)) out.put("frameMs", result.frameMs);
                out.put("frames", result.frames);
                out.put("status", result.fps == 0 ? "最近 1 秒没有新呈现帧" : "SurfaceFlinger 实际呈现帧率");
            } else out.put("status", "正在等待目标图层的有效帧时间");
        } catch (Exception e) {
            out = new JSONObject();
            try { out.put("status", "帧数据读取失败：" + e.getClass().getSimpleName()); } catch (Exception ignored) { }
        }
        return save(out, now);
    }
    private JSONObject save(JSONObject value, long now) { lastRead = now; cached = value; return value; }
    public void reset() { target = ""; layer = ""; lastDiscovery = 0; cached = null; }
    @Override public void close() { dump.close(); }
}
