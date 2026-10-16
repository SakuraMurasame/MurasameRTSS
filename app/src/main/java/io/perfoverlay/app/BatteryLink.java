package io.perfoverlay.app;

/** Main-thread ownership of a battery recording; independent sessions have no owner. */
public final class BatteryLink {
    private String owner = "";
    public boolean acceptStart(String requested, String current, boolean monitoring, boolean recording) {
        if (!monitoring || requested == null || requested.isEmpty() || !requested.equals(current)) return false;
        // Reuse independent recordings without taking ownership. A new monitor session
        // may adopt an older linked recording before its delayed stop arrives.
        if (!recording || !owner.isEmpty()) owner = requested;
        return true;
    }
    public boolean shouldStop(String requested) { return !owner.isEmpty() && owner.equals(requested); }
    public void independent() { owner = ""; }
    public String owner() { return owner; }
}
