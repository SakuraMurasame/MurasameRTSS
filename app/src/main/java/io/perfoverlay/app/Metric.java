package io.perfoverlay.app;

public enum Metric {
    FPS("FPS", "应用 FPS（呈现帧率）", true),
    FRAME_TIME("帧时", "平均呈现帧间隔", false),
    CPU_FREQ("CPU", "CPU 核心组频率", true),
    GPU_FREQ("GPU", "GPU 频率", true),
    RAM("RAM", "可用内存", true),
    CPU_TEMP("CPU T", "CPU 温度", false),
    SOC_TEMP("SoC", "SoC 温度", true),
    GPU_TEMP("GPU T", "GPU 温度", false),
    BAT_TEMP("BAT T", "电池温度", true),
    DEVICE_POWER("整机", "整机功耗（电池供电时估算）", true),
    BAT_POWER("BAT", "电池侧功率", false),
    BAT_LEVEL("电量", "电量与充电状态", false),
    CPU_POWER("CPU W", "CPU 独立功耗", false),
    GPU_POWER("GPU W", "GPU 独立功耗", false),
    REFRESH("屏幕", "屏幕刷新率（不是游戏 FPS）", false);

    public final String label, title;
    public final boolean defaultOn;
    Metric(String label, String title, boolean defaultOn) {
        this.label = label; this.title = title; this.defaultOn = defaultOn;
    }
}
