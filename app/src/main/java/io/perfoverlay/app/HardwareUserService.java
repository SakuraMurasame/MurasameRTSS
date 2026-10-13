package io.perfoverlay.app;

/** Instantiated by Shizuku, not an Android Service; no shell commands or writes. */
public final class HardwareUserService extends IHardwareService.Stub {
    private final HardwareProbe probe = new HardwareProbe();
    private final FpsProbe fps = new FpsProbe();
    private final AppBatteryProbe battery = new AppBatteryProbe();
    public HardwareUserService() { }
    @Override public String sample(int mask) {
        try {
            org.json.JSONObject result = new org.json.JSONObject(probe.sample(mask & 7));
            if ((mask & HardwareProbe.FPS) != 0) result.put("presentation", fps.sample());
            else fps.reset();
            return result.toString();
        } catch (Exception e) { return "{\"error\":\"硬件采样失败\"}"; }
    }
    @Override public String battery() { return battery.sample(); }
    @Override public void destroy() { fps.close(); battery.close(); System.exit(0); }
}
