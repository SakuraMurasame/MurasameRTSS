package io.perfoverlay.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private UiTheme theme;
    private int BG, CARD, INK, MUTED, ACCENT;
    private final java.util.EnumMap<Metric, TextView> overview = new java.util.EnumMap<>(Metric.class);
    private TextView samplingLabel, overviewNote;
    private boolean permissionExpanded;
    private Prefs prefs;
    private LinearLayout content;
    private TextView runtime, previewTitle, permissions, diagnostics;
    private Button startButton, hideButton, pauseButton;
    private OverlayView preview;
    private int page;
    private boolean pendingStart;
    private boolean notificationRequestInFlight;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshState(); ui.postDelayed(this, 1000); }
    };
    private final Shizuku.OnRequestPermissionResultListener shizukuResult = (code, grant) -> runOnUiThread(() -> {
        if (grant == PackageManager.PERMISSION_GRANTED) {
            prefs.put("shizuku", true);
            if (MonitorService.running) startService(new Intent(this, MonitorService.class).setAction(MonitorService.REFRESH));
            toast(I18n.t("Shizuku 已授权"));
        } else toast(I18n.t("未授权，仍可使用基础监控"));
        refreshState();
    });
    @Override public void onCreate(Bundle state) {
        UiTheme.beforeCreate(this); super.onCreate(state); prefs = new Prefs(this);
        if (state != null) page = state.getInt("page", 0);
        if (getIntent().hasExtra("page")) page = getIntent().getIntExtra("page", 0);
        getIntent().removeExtra("page");
        Shizuku.addRequestPermissionResultListener(shizukuResult);
        buildPage();
        if (getIntent().getBooleanExtra("startFromTile", false)) {
            getIntent().removeExtra("startFromTile"); ui.post(this::startMonitoring);
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (theme != null && theme.dark != UiTheme.get(this).dark) { recreate(); return; }
        ui.removeCallbacks(refresh); ui.post(refresh);
        if (pendingStart && !notificationRequestInFlight && Settings.canDrawOverlays(this)) { pendingStart = false; startMonitoring(); }
    }
    @Override protected void onPause() { ui.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null); Shizuku.removeRequestPermissionResultListener(shizukuResult); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle b) { b.putInt("page", page); super.onSaveInstanceState(b); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(dp(3), 1); return v;
    }
    private GradientDrawable box(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void addGap(LinearLayout l, int size) { View gap = new View(this); l.addView(gap, new LinearLayout.LayoutParams(1, dp(size))); }
    private LinearLayout card(String title, String detail) {
        LinearLayout c = column(); c.setPadding(dp(18), dp(18), dp(18), dp(18)); c.setBackground(theme.shape(this, CARD, 16, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(14); content.addView(c, lp);
        if (!title.isEmpty()) { TextView t = text(title, 18, INK); t.setTypeface(null, Typeface.BOLD); c.addView(t); }
        if (!detail.isEmpty()) { addGap(c, 6); c.addView(text(detail, 12, MUTED)); }
        if (!title.isEmpty() || !detail.isEmpty()) addGap(c, 12); return c;
    }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextSize(13); b.setAllCaps(false);
        b.setTextColor(primary ? theme.primaryInk : ACCENT); b.setBackground(box(primary ? ACCENT : theme.surface, 12));
        b.setPadding(dp(12), dp(10), dp(12), dp(10)); b.setMinHeight(dp(44)); b.setMinWidth(0);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void fullButton(LinearLayout l, String label, Runnable action, boolean primary) {
        Button b = button(label, action, primary); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(9); l.addView(b, lp);
    }
    private void buildPage() {
        theme = UiTheme.get(this); theme.window(this);
        BG = theme.bg; CARD = theme.card; INK = theme.ink; MUTED = theme.muted; ACCENT = theme.accent;
        overview.clear(); samplingLabel = null; overviewNote = null;
        runtime = null; preview = null; previewTitle = null; permissions = null; diagnostics = null;
        startButton = null; hideButton = null; pauseButton = null;
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom;
            } else { left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop(); right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom(); }
            v.setPadding(left, top, right, bottom); return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(16));
        TextView brand = text("MurasameRTSS", 23, INK); brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView mascot = new ImageView(this); mascot.setImageResource(R.drawable.murasame_character);
        mascot.setContentDescription(I18n.t("丛雨程序图标")); header.addView(mascot, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = column(); content.setPadding(dp(16), 0, dp(16), dp(12)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = UiNav.create(this, theme, page, this::navigate);
        LinearLayout.LayoutParams navLayout = new LinearLayout.LayoutParams(-1, -2); navLayout.setMargins(dp(12), 0, dp(12), dp(6));
        root.addView(nav, navLayout);
        setContentView(root);
        if (page == 0) monitorPage(); else if (page == 1) stylePage(); else if (page == 2) metricsPage(); else diagnosticsPage();
        refreshState();
    }
    private void previewCard() {
        LinearLayout c = card(I18n.t("浮窗预览"), "");
        previewTitle = text(I18n.t("布局示例 · 非实测"), 11, MUTED); c.addView(previewTitle); addGap(c, 8);
        FrameLayout frame = new FrameLayout(this); frame.setBackground(theme.shape(this, theme.surface, 12, true));
        frame.setPadding(dp(8), dp(8), dp(8), dp(8)); frame.setMinimumHeight(dp(58));
        preview = new OverlayView(this, prefs); preview.setAlpha(OverlayView.safeWindowAlpha(this));
        preview.maxWidth(getResources().getDisplayMetrics().widthPixels - dp(100));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addView(preview, lp); c.addView(frame, new LinearLayout.LayoutParams(-1, -2));
        if (page == 0) {
            LinearLayout controls = new LinearLayout(this);
            LinearLayout font = column(), opacity = column();
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -2, 1); cell.rightMargin = dp(12);
            controls.addView(font, cell); controls.addView(opacity, new LinearLayout.LayoutParams(0, -2, 1)); c.addView(controls);
            slider(font, I18n.t("字号"), 8, 30, prefs.font(), " sp", value -> prefs.put("font", value));
            int max = (int)Math.floor(OverlayView.safeWindowAlpha(this) * 100 + .001f);
            slider(opacity, I18n.t("黑底不透明度"), 0, max, Math.min(max, prefs.opacity()), "%", value -> prefs.put("opacity", value));
        }
    }
    private void monitorPage() {
        LinearLayout control = card("", "");
        LinearLayout state = new LinearLayout(this); state.setGravity(Gravity.CENTER_VERTICAL);
        runtime = text("", 12, ACCENT); runtime.setPadding(dp(10), dp(5), dp(10), dp(5));
        runtime.setBackground(theme.shape(this, theme.softAccent, 16, false));
        state.addView(runtime, new LinearLayout.LayoutParams(-2, -2));
        samplingLabel = text("", 11, MUTED); samplingLabel.setGravity(Gravity.END);
        state.addView(samplingLabel, new LinearLayout.LayoutParams(0, -2, 1)); control.addView(state); addGap(control, 12);
        startButton = button(I18n.t("开始监控"), () -> {
            if (MonitorService.running) { stopService(new Intent(this, MonitorService.class)); ui.postDelayed(this::refreshState, 250); }
            else startMonitoring();
        }, true); control.addView(startButton, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this); addGap(control, 8);
        hideButton = button(I18n.t("隐藏浮窗"), () -> send(MonitorService.HIDE), false);
        pauseButton = button(I18n.t("暂停采样"), () -> send(MonitorService.PAUSE), false);
        LinearLayout.LayoutParams h = new LinearLayout.LayoutParams(0, -2, 1); h.rightMargin = dp(8);
        controls.addView(hideButton, h); controls.addView(pauseButton, new LinearLayout.LayoutParams(0, -2, 1)); control.addView(controls);
        LinearLayout metrics = card(I18n.t("性能概览"), "");
        overviewNote = text("", 11, MUTED); metrics.addView(overviewNote); addGap(metrics, 8);
        Metric[] shown = {Metric.CPU_FREQ, Metric.RAM, Metric.CPU_TEMP, Metric.BAT_TEMP};
        String[] names = {"CPU 频率", "可用内存", "CPU 温度", "电池温度"};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                LinearLayout tile = column(); tile.setPadding(dp(12), dp(10), dp(12), dp(10));
                tile.setBackground(theme.shape(this, theme.surface, 12, false));
                LinearLayout label = new LinearLayout(this); label.setGravity(Gravity.CENTER_VERTICAL);
                label.addView(new UiIcon(this, 5 + index, index >= 2 ? theme.amber : ACCENT), new LinearLayout.LayoutParams(dp(21), dp(21)));
                TextView name = text(I18n.t(names[index]), 11, MUTED); name.setPadding(dp(7), 0, 0, 0);
                label.addView(name, new LinearLayout.LayoutParams(0, -2, 1)); tile.addView(label);
                TextView value = text("--", 21, INK); value.setTypeface(Typeface.MONOSPACE); value.setPadding(0, dp(5), 0, 0);
                value.setMaxLines(2); value.setAutoSizeTextTypeUniformWithConfiguration(14, 21, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
                tile.addView(value, new LinearLayout.LayoutParams(-1, dp(48))); overview.put(shown[index], value);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); if (col == 0) lp.rightMargin = dp(8);
                line.addView(tile, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); if (row > 0) lp.topMargin = dp(8); metrics.addView(line, lp);
        }
        previewCard();
        LinearLayout p = card("", "");
        TextView permissionTitle = text(I18n.t("权限与连接") + "  ›", 15, INK); permissionTitle.setTypeface(null, Typeface.BOLD);
        permissionTitle.setMinHeight(dp(44)); permissionTitle.setGravity(Gravity.CENTER_VERTICAL); p.addView(permissionTitle);
        permissions = text("", 11, MUTED); p.addView(permissions);
        LinearLayout details = column(); p.addView(details);
        boolean expanded = permissionExpanded || !Settings.canDrawOverlays(this);
        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        permissionTitle.setOnClickListener(v -> { permissionExpanded = details.getVisibility() != View.VISIBLE; details.setVisibility(permissionExpanded ? View.VISIBLE : View.GONE); });
        details.addView(text(I18n.t("已安装 Shizuku 时，先在 Shizuku 中启动服务，再返回这里授权。"), 12, MUTED));
        fullButton(details, I18n.t("允许悬浮窗"), this::overlayPermission, false);
        fullButton(details, I18n.t("允许通知"), this::notificationPermission, false);
        fullButton(details, I18n.t("连接 / 授权 Shizuku"), this::authorizeShizuku, false);
        toggle(details, I18n.t("使用 Shizuku 增强采集"), prefs.shizuku(), value -> prefs.put("shizuku", value));
        fullButton(details, I18n.t("打开触摸测试页"), () -> startActivity(new Intent(this, TouchTestActivity.class)), false);
    }
    private void navigate(int index) {
        if (index == 3) startActivity(new Intent(this, HistoryActivity.class));
        else if (index != page) { page = index; buildPage(); }
    }
    private interface IntChange { void set(int value); }
    private interface BoolChange { void set(boolean value); }
    private void slider(LinearLayout c, String name, int min, int max, int value, String suffix, IntChange callback) {
        addGap(c, 9); TextView label = text(name + "  " + value + suffix, 14, INK); c.addView(label);
        SeekBar seek = new SeekBar(this); seek.setMax(max - min); seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
        seek.setContentDescription(name); c.addView(seek, new LinearLayout.LayoutParams(-1, dp(42)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (user) { int next = min + progress; label.setText(name + "  " + next + suffix); callback.set(next); refreshState(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }
    private void toggle(LinearLayout c, String name, boolean value, BoolChange callback) {
        Switch s = new Switch(this); s.setText(name); s.setTextColor(INK); s.setTextSize(14); s.setPadding(0, dp(10), 0, dp(10));
        s.setChecked(value); s.setOnCheckedChangeListener((v, checked) -> { callback.set(checked); refreshState(); }); c.addView(s);
    }
    private void stylePage() {
        LinearLayout appearance = card(I18n.t("外观主题"), I18n.t("界面主题与浮窗黑底透明度分别设置。"));
        fullButton(appearance, themeName() + "  ›", this::chooseTheme, false);
        previewCard();
        LinearLayout c = card(I18n.t("文字与黑底"), I18n.t("黑底紧贴文字测量尺寸，不使用固定宽高。"));
        slider(c, I18n.t("字号"), 8, 30, prefs.font(), " sp", value -> prefs.put("font", value));
        int max = (int) Math.floor(OverlayView.safeWindowAlpha(this) * 100 + .001f);
        slider(c, I18n.t("黑底不透明度"), 0, max, Math.min(max, prefs.opacity()), "%", value -> prefs.put("opacity", value));
        c.addView(text(I18n.t("0% 为全透明。此设备上限 ") + max + I18n.t("%：按系统触摸穿透规则限制整窗遮挡，文字也会略微透底。"), 12, MUTED));
        slider(c, I18n.t("距顶部安全区域"), 0, 120, prefs.offset(), " dp", value -> prefs.put("offset", value));
        toggle(c, I18n.t("显示指标分隔线"), prefs.separators(), value -> prefs.put("separators", value));
        toggle(c, I18n.t("改为纵向排列"), prefs.vertical(), value -> prefs.put("vertical", value));
        LinearLayout rate = card(I18n.t("刷新速度"), I18n.t("默认每秒刷新一次。高频采样会增加监控本身的开销。"));
        String[] options = {"250 ms", "500 ms", I18n.t("1000 ms · 推荐"), "2000 ms"}; int[] intervals = {250, 500, 1000, 2000};
        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < options.length; i++) { RadioButton b = new RadioButton(this); b.setText(options[i]); b.setTextColor(INK); b.setId(100 + i); group.addView(b); if (prefs.interval() == intervals[i]) group.check(b.getId()); }
        group.setOnCheckedChangeListener((g, checked) -> prefs.put("interval", intervals[checked - 100])); rate.addView(group);
        LinearLayout presets = card(I18n.t("快速预设"), I18n.t("切换预设会替换显示项目；字号和透明度保持当前设置。"));
        fullButton(presets, I18n.t("顶部常用：频率 / 内存 / 温度 / 功率"), () -> preset(false), false);
        fullButton(presets, I18n.t("极简：CPU / 内存 / 电池温度"), () -> preset(true), false);
    }
    private void preset(boolean minimal) {
        for (Metric m : Metric.values()) prefs.put("metric_" + m.name(), minimal ? m == Metric.CPU_FREQ || m == Metric.RAM || m == Metric.BAT_TEMP : m.defaultOn);
        prefs.put("vertical", false); refreshState(); toast(I18n.t("已应用预设"));
    }
    private void metricsPage() {
        LinearLayout hint = card(I18n.t("显示项目"), I18n.t("开关决定是否显示；箭头调整左右顺序。不可用项显示 --，不会用示例数值替代。"));
        hint.addView(text(I18n.t("CPU 按实际调频域排列，支持任意组数。屏幕 Hz 和游戏 FPS 为不同指标。"), 12, MUTED));
        for (Metric m : prefs.order()) {
            LinearLayout c = card("", "");
            Switch choice = new Switch(this); choice.setText(I18n.t(m.title)); choice.setTextSize(15); choice.setTextColor(INK); choice.setChecked(prefs.enabled(m));
            choice.setOnCheckedChangeListener((v, on) -> prefs.put("metric_" + m.name(), on)); c.addView(choice);
            Snapshot s = MonitorService.latest;
            String reason = s == null ? m == Metric.FPS ? I18n.t("开启 Shizuku 并切换到目标应用后读取") : I18n.t("开始监控后查看可用数据") : I18n.raw(s.values.get(m)) + " · " + I18n.raw(s.reasons.get(m));
            c.addView(text(reason, 12, MUTED)); addGap(c, 7);
            LinearLayout row = new LinearLayout(this);
            Button up = button(I18n.t("前移 ↑"), () -> { prefs.move(m, -1); buildPage(); }, false);
            Button down = button(I18n.t("后移 ↓"), () -> { prefs.move(m, 1); buildPage(); }, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.rightMargin = dp(8);
            row.addView(up, lp); row.addView(down, new LinearLayout.LayoutParams(0, -2, 1)); c.addView(row);
            if (m == Metric.CPU_TEMP || m == Metric.SOC_TEMP || m == Metric.GPU_TEMP) fullButton(c, I18n.t("选择温度传感器"), () -> chooseSensor(m), false);
        }
    }
    private void chooseSensor(Metric metric) {
        List<String> labels = new ArrayList<>(), keys = new ArrayList<>();
        labels.add(I18n.t("自动：只匹配同类明确名称，取最高温")); keys.add("auto");
        Snapshot s = MonitorService.latest;
        JSONArray array = s == null ? null : s.hardware.optJSONArray("sensors");
        if (array != null) for (int i = 0; i < array.length(); i++) {
            JSONObject sensor = array.optJSONObject(i); if (sensor == null || !sensor.has("c")) continue;
            labels.add(sensor.optString("name") + " · " + MetricMath.fixed(sensor.optDouble("c", Double.NaN), 1) + "°C · " + sensor.optString("id"));
            keys.add(sensor.optString("id") + ":" + sensor.optString("name"));
        }
        if (keys.size() == 1) { toast(I18n.t("先开启温度指标并开始监控；不可读的传感器不会出现")); return; }
        new AlertDialog.Builder(this).setTitle(I18n.t(metric.title) + I18n.t("：确认传感器含义后选择"))
                .setSingleChoiceItems(labels.toArray(new String[0]), Math.max(0, keys.indexOf(prefs.sensor(metric))), (dialog, which) -> {
                    prefs.put("sensor_" + metric.name(), keys.get(which)); dialog.dismiss(); toast(I18n.t("传感器已更新"));
                }).setNegativeButton(I18n.t("取消"), null).show();
    }
    private void diagnosticsPage() {
        LinearLayout language = card(I18n.t("语言"), I18n.t("语言切换立即生效，监控和记录不会中断。"));
        fullButton(language, I18n.t("应用语言") + " · " + languageName(), this::chooseLanguage, false);
        LinearLayout about = card(I18n.t("关于程序"), I18n.t("MurasameRTSS · 1.1.0\nAndroid 游戏性能浮窗与本地曲线记录"));
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.murasame_character);
        icon.setContentDescription(I18n.t("丛雨程序图标")); about.addView(icon, new LinearLayout.LayoutParams(dp(88), dp(88)));
        LinearLayout updates = card(I18n.t("历史更新"), I18n.t("日期 / 版本 / 更新内容"));
        updates.addView(text(I18n.t("2026-10-10 · 1.1.0\n更新底部导航、性能概览、浮窗预览与曲线卡片；新增暗色、亮色和跟随系统主题，设置持久保存。\n\n2026-10-10 · 1.0.0\n新增日语、英语、法语和德语，支持跟随系统或手动选择语言。移除首页副标题和关于页图标制作说明。\n\n2026-10-08 · 0.4.0\n新增默认开启的“监控时自动记录”开关；开始监控自动记录，停止监控保存。记录采集独立于浮窗显示，新增 15 项指标纵向曲线面板、共用时间轴、同步游标、末点 / 选中读数及最低 / 最高 / 均值。保留单点、时间段和整次删除。\n\n2026-10-07 · 0.3.0\n应用更名 MurasameRTSS，更换丛雨图标；新增手动曲线记录、指标切换、时间范围查看、单点 / 时间段 / 整次记录删除；新增关于程序和历史更新。\n\n2026-10-07 · 0.2.1\n修复横屏空间充足时仍显示两行的问题；按可用宽度平铺，旋转屏幕自动重新测量。\n\n2026-10-07 · 0.2.0\n加入 Shizuku 应用呈现 FPS 和帧间隔检测、整机功耗估算；明确 GPU 节点读取限制，改进诊断信息。\n\n2026-10-07 · 0.1.0\n首个版本：手动启停、常驻通知、顶部浮窗、CPU 核心组频率及温度 / 内存 / 电池监控；可选指标、字号与黑底透明度，支持触摸穿透。"), 13, INK));
        LinearLayout c = card(I18n.t("实时诊断"), I18n.t("数据保留在本机。-- 表示无有效数据；不代表温度或功耗为零。"));
        diagnostics = text("", 12, MUTED); diagnostics.setTypeface(Typeface.MONOSPACE); diagnostics.setTextIsSelectable(true); c.addView(diagnostics);
        fullButton(c, I18n.t("复制诊断报告"), () -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(I18n.t("MurasameRTSS 诊断"), diagnosticText())); toast(I18n.t("已复制"));
        }, false);
        LinearLayout limits = card(I18n.t("当前版本"), "MurasameRTSS 1.1.0 · Android 10+");
        limits.addView(text(I18n.t("新增应用呈现 FPS / 帧间隔：由 Shizuku 读取前台应用图层的 SurfaceFlinger 时间戳；与游戏内部渲染帧数、屏幕 Hz 不同。\n\n新增整机功耗估算：仅未接外部电源且正在放电时提供。充电时请查看电池侧功率，不能将其当成整机功耗。\n\nCPU/GPU 独立功耗仍未适配。GPU 权限被 ROM 拒绝时会明确说明；Shizuku 不等于 Root。"), 13, MUTED));
    }
    private String themeName() {
        String mode = prefs.store.getString("theme", "dark");
        return I18n.t("light".equals(mode) ? "亮色" : "system".equals(mode) ? "跟随系统" : "暗色");
    }
    private void chooseTheme() {
        String[] codes = {"dark", "light", "system"};
        String[] names = {I18n.t("暗色"), I18n.t("亮色"), I18n.t("跟随系统")};
        new AlertDialog.Builder(this).setTitle(I18n.t("外观主题"))
            .setSingleChoiceItems(names, java.util.Arrays.asList(codes).indexOf(prefs.store.getString("theme", "dark")), (dialog, which) -> {
                prefs.put("theme", codes[which]); dialog.dismiss(); recreate();
            }).setNegativeButton(I18n.t("取消"), null).show();
    }
    private String languageName() {
        String[] codes = {"system", "zh", "ja", "en", "fr", "de"};
        String[] names = {I18n.t("跟随系统"), "简体中文", "日本語", "English", "Français", "Deutsch"};
        String selected = ((MurasameApp) getApplication()).selection();
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(selected)) return names[i];
        return names[0];
    }
    private void chooseLanguage() {
        String[] codes = {"system", "zh", "ja", "en", "fr", "de"};
        String[] names = {I18n.t("跟随系统"), "简体中文", "日本語", "English", "Français", "Deutsch"};
        String selected = ((MurasameApp) getApplication()).selection();
        int checked = java.util.Arrays.asList(codes).indexOf(selected);
        new AlertDialog.Builder(this).setTitle(I18n.t("应用语言"))
            .setSingleChoiceItems(names, Math.max(0, checked), (dialog, which) -> {
                ((MurasameApp) getApplication()).selectLanguage(codes[which]);
                dialog.dismiss(); buildPage();
            }).setNegativeButton(I18n.t("取消"), null).show();
    }
    private String diagnosticText() {
        StringBuilder b = new StringBuilder(I18n.t("设备：")).append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nAndroid ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT)
                .append(I18n.t("\n穿透整窗 alpha：")).append(MetricMath.fixed(OverlayView.safeWindowAlpha(this), 2)).append('\n');
        Snapshot s = MonitorService.latest;
        if (s == null) return b.append(I18n.t("\n开始监控后显示真实数据和传感器。\n")).toString();
        b.append(I18n.raw(s.backend)).append('\n');
        for (Metric m : Metric.values()) b.append('\n').append(I18n.t(m.title)).append("：").append(I18n.raw(s.values.get(m))).append('\n').append(I18n.raw(s.reasons.get(m))).append('\n');
        try { b.append(I18n.t("\n硬件节点（调频域 / 传感器）：\n")).append(s.hardware.toString(2)); } catch (Exception ignored) { }
        return b.toString();
    }
    private void refreshState() {
        if (runtime != null) runtime.setText("● " + I18n.t(MonitorService.running ? MonitorService.paused ? "已暂停" : "监控中" : "已停止"));
        if (samplingLabel != null) samplingLabel.setText(I18n.t("采样间隔") + " " + prefs.interval() + " ms");
        Snapshot sample = MonitorService.running ? MonitorService.latest : null;
        if (overviewNote != null) overviewNote.setText(sample == null ? I18n.t("开始监控后显示实时数据") : I18n.raw(sample.backend) + (MonitorService.paused ? I18n.t(" · 已暂停") : ""));
        for (java.util.Map.Entry<Metric, TextView> cell : overview.entrySet()) {
            String value = sample == null ? "--" : I18n.raw(sample.values.get(cell.getKey()));
            cell.getValue().setText(value); cell.getValue().setTextColor("--".equals(value) ? MUTED : cell.getKey() == Metric.CPU_TEMP || cell.getKey() == Metric.BAT_TEMP ? theme.amber : INK);
        }
        if (startButton != null) startButton.setText(MonitorService.running ? I18n.t("停止监控") : I18n.t("开始监控"));
        if (hideButton != null) { hideButton.setEnabled(MonitorService.running); hideButton.setText(MonitorService.hidden ? I18n.t("显示浮窗") : I18n.t("隐藏浮窗")); }
        if (pauseButton != null) { pauseButton.setEnabled(MonitorService.running); pauseButton.setText(MonitorService.paused ? I18n.t("继续采样") : I18n.t("暂停采样")); }
        if (preview != null) {
            boolean live = MonitorService.running && MonitorService.latest != null;
            previewTitle.setText(live ? I18n.t("实时数据") + (MonitorService.paused ? I18n.t(" · 已暂停") : "") : I18n.t("布局示例 · 非实测"));
            preview.update(live ? MonitorService.latest : Snapshot.demo());
        }
        if (permissions != null) permissions.setText(I18n.t("悬浮窗") + (Settings.canDrawOverlays(this) ? " ✓" : " !")
                + "  ·  " + I18n.t("通知") + (getSystemService(NotificationManager.class).areNotificationsEnabled() ? " ✓" : " !")
                + "  ·  Shizuku " + (ShizukuBridge.authorized() ? "✓" : "!"));
        if (diagnostics != null) diagnostics.setText(diagnosticText());
    }
    private void overlayPermission() {
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        catch (ActivityNotFoundException e) { toast(I18n.t("请在系统设置中允许显示在其他应用上层")); }
    }
    private void notificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    private void authorizeShizuku() {
        if (!ShizukuBridge.available()) {
            new AlertDialog.Builder(this).setTitle(I18n.t("先启动 Shizuku"))
                    .setMessage(I18n.t("在 Shizuku 中通过无线调试启动服务，再返回这里点击授权。手机重启后通常需要重新启动 Shizuku。"))
                    .setPositiveButton(I18n.t("打开 Shizuku"), (d, w) -> {
                        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (launch != null) startActivity(launch); else toast(I18n.t("未找到 Shizuku 应用"));
                    }).setNegativeButton(I18n.t("稍后"), null).show(); return;
        }
        try {
            if (ShizukuBridge.authorized()) {
                prefs.put("shizuku", true); if (MonitorService.running) send(MonitorService.REFRESH); toast(I18n.t("Shizuku 已授权"));
            } else if (Shizuku.shouldShowRequestPermissionRationale()) toast(I18n.t("请在 Shizuku 的已授权应用管理中允许 MurasameRTSS"));
            else Shizuku.requestPermission(40);
        } catch (Exception e) { toast(I18n.t("Shizuku 连接已断开，请重新启动")); }
    }
    private void startMonitoring() {
        if (!Settings.canDrawOverlays(this)) { pendingStart = true; overlayPermission(); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true; notificationRequestInFlight = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10); return;
        }
        launchMonitor();
    }
    private void launchMonitor() {
        pendingStart = false;
        try { startForegroundService(new Intent(this, MonitorService.class).setAction(MonitorService.START)); ui.postDelayed(this::refreshState, 300); }
        catch (RuntimeException e) { toast(I18n.t("启动失败，请保持本应用在前台重试")); }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 10) {
            notificationRequestInFlight = false;
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) launchMonitor();
            else {
                pendingStart = false;
                new AlertDialog.Builder(this).setTitle(I18n.t("通知未允许"))
                        .setMessage(I18n.t("浮窗可以运行，但通知栏控制可能不可见。你可以回到应用停止监控。"))
                        .setPositiveButton(I18n.t("仍然开始"), (d, w) -> launchMonitor()).setNegativeButton(I18n.t("取消"), null).show();
            }
        }
    }
    private void send(String action) {
        if (MonitorService.running) startService(new Intent(this, MonitorService.class).setAction(action));
        ui.postDelayed(this::refreshState, 200);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
