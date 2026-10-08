import io.perfoverlay.app.MetricMath;
import io.perfoverlay.app.FlowLayout;
import io.perfoverlay.app.FrameMath;
import java.util.Random;

public final class CoreTests {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++; if (!ok) throw new AssertionError(message);
    }
    private static void close(double actual, double expected, String message) { check(Math.abs(actual - expected) < 0.0001, message); }
    public static void main(String[] args) {
        close(MetricMath.frequencyGHz("3200000"), 3.2, "CPU kHz conversion");
        check(Double.isNaN(MetricMath.frequencyGHz("0")), "Offline must not appear as 0 GHz");
        check(Double.isNaN(MetricMath.frequencyGHz("permission denied")), "Unreadable frequency");
        check(Double.isNaN(MetricMath.number("Infinity")), "Infinite input");
        check(Double.isNaN(MetricMath.number("NaN")), "NaN input");
        close(MetricMath.thermalC("62500"), 62.5, "Thermal millidegrees");
        check(Double.isNaN(MetricMath.thermalC("2147483647")), "Temperature sentinel");
        check(Double.isNaN(MetricMath.thermalC("45")), "Do not guess OEM temperature unit");
        close(MetricMath.batteryWatts(-1300000, 4000), 5.2, "Discharge watts");
        close(MetricMath.batteryWatts(1300000, 4000), 5.2, "Charge magnitude");
        check(Double.isNaN(MetricMath.batteryWatts(Integer.MIN_VALUE, 4000)), "Unsupported current sentinel");
        check(Double.isNaN(MetricMath.batteryWatts(Integer.MAX_VALUE, 4000)), "Invalid maximum sentinel");
        check(Double.isNaN(MetricMath.batteryWatts(1300000, 0)), "Missing voltage");
        close(MetricMath.deviceWatts(-1300000, 4000, false, true), 5.2, "Unplugged whole-device estimate");
        check(Double.isNaN(MetricMath.deviceWatts(-1300000, 4000, true, true)), "Plugged battery drain is not total device power");
        close(MetricMath.deviceWatts(1300000, 4000, false, true), 5.2, "Vendor-inverted current during unambiguous unplugged discharge");
        check(Double.isNaN(MetricMath.deviceWatts(-1300000, 4000, false, false)), "Unknown power state is not discharge");
        check(!MetricMath.isTemperatureSensor("vbat"), "Battery voltage thermal zone is not temperature");
        check(!MetricMath.isTemperatureSensor("socd"), "State of charge is not SoC temperature");
        check(MetricMath.isTemperatureSensor("gpuss-0"), "Known GPU thermal sensor");
        testFrames();
        close(MetricMath.touchAlpha(30, .8f), 1, "Pre Android 12 alpha");
        check(MetricMath.touchAlpha(31, .8f) < .8f, "Touch limit margin");
        check(MetricMath.touchAlpha(35, .5f) < .5f, "Use device touch limit, not hardcoded .8");
        for (int opacity = 0; opacity <= 100; opacity++) {
            float window = MetricMath.touchAlpha(35, .8f);
            float requested = Math.min(window, opacity / 100f);
            int paint = MetricMath.backgroundPaintAlpha(requested, window);
            check(paint >= 0 && paint <= 255, "Legal background alpha");
            check(Math.abs(window * paint / 255f - requested) < .004f, "Composed opacity matches slider");
        }
        FlowLayout line = FlowLayout.compute(new float[]{100, 80, 90}, 400, 10, 5, false);
        check(line.rows == 1, "Wide landscape fits one row"); close(line.width, 300, "Background fits text width");
        FlowLayout wrap = FlowLayout.compute(new float[]{100, 80, 90}, 220, 10, 5, false);
        check(wrap.rows == 2, "Narrow viewport wraps complete metrics");
        FlowLayout vertical = FlowLayout.compute(new float[]{100, 80, 90}, 400, 10, 5, true);
        check(vertical.rows == 3, "Vertical layout"); close(vertical.width, 110, "Vertical black background follows longest value");
        Random random = new Random(71);
        for (int t = 0; t < 500; t++) {
            float max = 100 + random.nextInt(1000);
            float[] widths = new float[1 + random.nextInt(15)];
            for (int i = 0; i < widths.length; i++) widths[i] = 10 + random.nextInt(2000);
            FlowLayout f = FlowLayout.compute(widths, max, 12, 5, false);
            check(f.width <= max + .001f, "Black background stays within viewport");
            check(f.items.size() == widths.length, "No metric silently dropped");
            FlowLayout.Item previous = null;
            for (FlowLayout.Item item : f.items) {
                check(item.x >= 0 && item.x + item.width <= max + .001f, "Item bounds");
                if (previous != null && previous.row == item.row) check(item.x >= previous.x + previous.width, "No overlapping metrics");
                previous = item;
            }
        }
        System.out.println("PASS: " + assertions + " assertions (units, sentinels, touch opacity, layout invariants)");
    }
    private static void testFrames() {
        long now = 100_000_000_000L;
        for (int fps : new int[]{1, 2, 30, 60, 90, 120, 144, 240}) {
            StringBuilder dump = new StringBuilder("16666666\n");
            int count = Math.min(127, fps + 1);
            for (int i = count - 1; i >= 0; i--) {
                long frame = now - Math.round(i * 1e9 / fps);
                dump.append(frame).append(' ').append(frame).append(' ').append(frame).append('\n');
            }
            // Identical rows and invalid pending fences must not inflate FPS.
            dump.append(now).append(' ').append(now).append(' ').append(now).append('\n');
            dump.append("0 0 0\n1 9223372036854775807 1\n1 -1 1\ninvalid\n");
            FrameMath.Result result = FrameMath.analyze(dump.toString(), now);
            check(result.valid, "Valid frame timestamp buffer");
            close(result.fps, fps, "Presentation FPS, independent of screen refresh header");
            close(result.frameMs, 1000d / fps, "Mean presentation interval");
            FrameMath.Result stopped = FrameMath.analyze(dump.toString(), now + 2_000_000_000L);
            close(stopped.fps, 0, "Old timestamps cannot freeze old FPS");
        }
        check(!FrameMath.analyze("16666666\n", now).valid, "Refresh period alone is not FPS");
        check(!FrameMath.analyze("Permission denied", now).valid, "Permission denial is not zero FPS");
        String target = FrameMath.foregroundPackage("topResumedActivity=ActivityRecord{123 u0 com.example.game/.GameActivity t3}");
        check(target.equals("com.example.game"), "Foreground app parsing");
        String list = "RequestedLayerState{SurfaceView[com.example.game/.GameActivity](BLAST)#145 parentId=11}\n"
                + "RequestedLayerState{ActivityRecord{123 u0 com.example.game/.GameActivity t3}#14 parentId=10}\n"
                + "f00 com.example.game/.GameActivity#15\n"
                + "RequestedLayerState{PerfOverlay 只读性能监控#20 parentId=10}\n";
        java.util.List<String> candidates = FrameMath.candidateLayers(list, target);
        check(candidates.size() == 2, "Exclude metadata layers and our overlay");
        check(candidates.get(0).equals("SurfaceView[com.example.game/.GameActivity](BLAST)#145"), "Android 16 layer-name normalization");
        check(FrameMath.candidateLayers(list, "io.perfoverlay.app").isEmpty(), "Never report own overlay redraw as app FPS");
    }
}
