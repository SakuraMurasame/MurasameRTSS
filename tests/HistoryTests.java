import io.perfoverlay.app.HistoryStore;
import io.perfoverlay.app.HistoryMath;
import io.perfoverlay.app.HistorySeries;
import io.perfoverlay.app.Metric;
import java.nio.file.*;
import java.util.*;

public final class HistoryTests {
    static int assertions;
    static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }
    interface Action { void run() throws Exception; }
    static void rejects(Action a, String reason) throws Exception {
        boolean rejected = false; try { a.run(); } catch (IllegalArgumentException | IllegalStateException e) { rejected = true; } check(rejected, reason);
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Paths.get(args[0]), "history-");
        HistoryStore store = new HistoryStore(dir.toFile());
        long start = 1791331200000L;
        String id = store.begin(start, 10000);
        check(store.begin(start + 1, 10001).equals(id), "Duplicate start must not replace recording");
        rejects(() -> store.delete(id), "Cannot delete active session");
        rejects(() -> store.deletePoints(id, 0, 100, null), "Cannot mutate active points");
        Map<String,String> values = new LinkedHashMap<>();
        values.put("CPU_FREQ", "4.3+3.5 GHz"); values.put("GPU_FREQ", "--"); values.put("BAT_POWER", "充入 ≈5.2 W");
        values.put("INFO_GPU_FREQ", "ROM 拒绝读取"); values.put("TARGET_APP", "test.game");
        for (int i = 0; i < 20; i++) {
            check(store.append(id, 10000 + i * 1000, start + i * 1000, values), "Append real sample");
            check(!store.append(id, 10000 + i * 1000 + 250, start, values), "Throttle high-frequency samples");
        }
        store.gap();
        check(store.append(id, 30000, start + 20000, values), "Append after pause");
        store.end();
        check(!store.append(id, 31000, start + 21000, values), "Stopped recordings never grow");
        List<HistoryStore.Point> read = store.read(id);
        check(read.size() == 21, "Saved sample count");
        check(read.get(0).values.equals(values), "Unicode and all metric values round trip");
        check(!HistoryMath.connects(read.get(19).sequence, read.get(20).sequence, read.get(19).elapsed, read.get(20).elapsed), "Pause creates a visible break");
        check(store.deletePoints(id, 5000, 9999, null) == 5, "Delete inclusive time range");
        read = store.read(id);
        check(read.size() == 16 && read.get(5).elapsed == 10000, "Delete retains timestamps and surviving values");
        check(!HistoryMath.connects(read.get(4).sequence, read.get(5).sequence, read.get(4).elapsed, read.get(5).elapsed), "Deleted range never connected");
        check(store.deletePoints(id, 0, Long.MAX_VALUE, 10L) == 1, "Delete one exact sample");
        check(store.deletePoints(id, 0, Long.MAX_VALUE, 10L) == 0, "Repeated point delete changes nothing");
        rejects(() -> store.deletePoints(id, 10, 1, null), "Reject reversed range");
        check(store.read(id).size() == 15, "Invalid deletion preserves original");
        HistoryStore reopened = new HistoryStore(dir.toFile());
        check(reopened.active.isEmpty(), "Restart does not auto-record");
        check(reopened.read(id).size() == 15, "Deletion survives process restart");
        Files.write(dir.resolve(id), "999\t30000\t123\tCPU_FREQ=broken".getBytes(java.nio.charset.StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        check(reopened.read(id).size() == 15, "Incomplete trailing write ignored");
        String other = reopened.begin(start + 30000, 50000);
        check(!reopened.append(id, 51000, start, values), "Stale sample cannot cross sessions");
        reopened.end();
        check(reopened.deletePoints(id, 0, Long.MAX_VALUE, null) == 15, "Delete whole data range");
        check(reopened.read(id).isEmpty(), "Empty recording is readable");
        reopened.delete(id);
        check(reopened.list().size() == 1 && reopened.list().get(0).equals(other), "Session delete leaves other sessions intact");
        rejects(() -> reopened.read("../secret"), "Reject traversal");
        check(HistoryMath.values("--", false).length == 0, "Unknown is gap, not zero");
        check(HistoryMath.values("0.0", false)[0] == 0, "Actual zero remains zero");
        double[] cpu = HistoryMath.values("4.3+--+2.0 GHz", true);
        check(cpu.length == 3 && Double.isNaN(cpu[1]) && cpu[2] == 2, "Missing CPU group never shifts the other groups");
        check(HistoryMath.values("充入 ≈5.2 W", false)[0] == 5.2, "Battery direction text is preserved, curve uses magnitude");
        check(HistoryMath.parseTime("01:02:03") == 3723000, "Range parser");
        check(HistoryMath.parseTime("01：02：03") == 3723000, "Chinese IME colon accepted");
        check(HistoryMath.parseTime("10") == 10000, "Seconds input accepted");
        rejects(() -> HistoryMath.parseTime("00:60:01"), "Invalid minute rejected");
        check(HistoryMath.time(3723999).equals("01:02:03"), "Elapsed time formatting");
        check(!HistoryMath.connects(1, 3, 1000, 3000), "One deleted point leaves a break");
        check(!HistoryMath.connects(1, 2, 1000, 8000), "Long sampling gap leaves a break");
        check(HistoryMath.connects(1, 2, 1000, 3000), "Normal 2 second sampling connects");
        testPanels();
        System.out.println("PASS: " + assertions + " history assertions (persistence, range/point/session deletion, gaps, restart, corruption, units)");
    }
    private static void testPanels() {
        List<HistoryStore.Point> samples = Arrays.asList(
            new HistoryStore.Point(0, 1000, 100000, Map.of("FPS", "60.0", "CPU_FREQ", "4.3+2.0 GHz", "RAM", "5.2 GiB")),
            new HistoryStore.Point(1, 2000, 101000, Map.of("FPS", "0.0", "CPU_FREQ", "3.0+-- GHz", "RAM", "5.0 GiB")),
            new HistoryStore.Point(3, 4000, 103000, Map.of("FPS", "--", "INFO_FPS", "图层不可用", "CPU_FREQ", "--", "RAM", "5.1 GiB")));
        HistorySeries fps = new HistorySeries(samples, Metric.FPS, 0, 5000);
        check(fps.valid == 2 && fps.min == 0 && fps.max == 60 && fps.mean == 30, "Zero FPS counted; missing FPS excluded from statistics");
        check(fps.value(null).equals("--"), "Last unavailable sample never replaced with stale last valid value");
        check(fps.note(null).equals("图层不可用"), "Recorded unavailable reason");
        HistorySeries cpu = new HistorySeries(samples, Metric.CPU_FREQ, 0, 5000);
        check(cpu.groups == 2 && cpu.valid == 3 && cpu.min == 2 && cpu.max == 4.3, "All CPU groups included without filling missing group with zero");
        for (Metric metric : Metric.values()) {
            HistorySeries s = new HistorySeries(samples, metric, 0, 5000);
            check(s.from == fps.from && s.to == fps.to, "Every panel shares time axis");
            check(s.points.get(1).sequence == fps.points.get(1).sequence, "Every panel shares cursor sample");
            check(s.value(samples.get(1)).equals(samples.get(1).values.getOrDefault(metric.name(), "--")), "Synchronized cursor uses the chosen metric");
        }
        HistorySeries ram = new HistorySeries(samples, Metric.RAM, 1500, 2500);
        check(ram.points.size() == 1 && ram.min == 5.0 && ram.max == 5.0, "Time filtering applies to statistics and plotted samples");
        HistorySeries gpu = new HistorySeries(samples, Metric.GPU_FREQ, 0, 5000);
        check(gpu.valid == 0 && Double.isNaN(gpu.min) && Double.isNaN(gpu.max), "Unsupported sensor has no fabricated extrema");
        check(gpu.note(null).contains("旧记录"), "Legacy records without reason remain readable");
        HistorySeries empty = new HistorySeries(samples, Metric.RAM, 10000, 11000);
        check(empty.points.isEmpty() && empty.value(null).equals("--"), "Empty selected range");
        check(cpu.points.get(2).sequence == 3, "Deleted sample gap retained in every panel");
    }
}
