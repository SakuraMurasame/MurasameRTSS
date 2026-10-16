import io.perfoverlay.app.BatteryLink;

public final class BatteryLinkTests {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("case " + assertions); }
    public static void main(String[] args) {
        BatteryLink link = new BatteryLink();
        check(!link.acceptStart(null, "a", true, false));
        check(!link.acceptStart("", "", true, false));
        check(!link.acceptStart("a", "a", false, false));
        check(!link.acceptStart("a", "b", true, false));
        check(!link.shouldStop("a"));
        check(link.acceptStart("a", "a", true, false));
        check(link.shouldStop("a"));
        check(!link.shouldStop("b"));
        check(!link.shouldStop(null));
        // Repeated start preserves ownership and does not request a fresh recording.
        check(link.acceptStart("a", "a", true, true));
        check(link.shouldStop("a"));
        // Rapid stop/start: the current session adopts a linked recorder; stale stop is harmless.
        check(link.acceptStart("b", "b", true, true));
        check(!link.shouldStop("a"));
        check(link.shouldStop("b"));
        check(!link.acceptStart("a", "b", true, true));
        check(link.shouldStop("b"));
        // A manually started recording remains independent of monitor stop.
        link.independent();
        check(link.acceptStart("c", "c", true, true));
        check(link.owner().isEmpty());
        check(!link.shouldStop("c"));
        check(!link.shouldStop("b"));
        // Pending start after monitor destruction must not resurrect battery recording.
        link = new BatteryLink();
        check(!link.acceptStart("c", "", false, false));
        check(link.owner().isEmpty());
        check(link.acceptStart("d", "d", true, false));
        check(link.shouldStop("d"));
        System.out.println("PASS: " + assertions + " battery link lifecycle assertions");
    }
}
