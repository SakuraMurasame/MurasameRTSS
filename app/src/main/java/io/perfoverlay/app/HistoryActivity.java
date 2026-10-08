package io.perfoverlay.app;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class HistoryActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private History history;
    private Prefs prefs;
    private LinearLayout content, records;
    private ScrollView scroll;
    private LinearLayout edits;
    private TextView status, summary, selection;
    private Button recordButton, pointButton;
    private EditText from, to;
    private final EnumMap<Metric, HistoryChart> charts = new EnumMap<>(Metric.class);
    private final EnumMap<Metric, TextView> captions = new EnumMap<>(Metric.class);
    private final EnumMap<Metric, HistorySeries> series = new EnumMap<>(Metric.class);
    private static final Metric[] PANELS = {Metric.CPU_TEMP, Metric.GPU_TEMP, Metric.CPU_FREQ, Metric.GPU_FREQ,
        Metric.RAM, Metric.DEVICE_POWER, Metric.BAT_POWER, Metric.BAT_TEMP, Metric.FPS, Metric.FRAME_TIME,
        Metric.BAT_LEVEL, Metric.SOC_TEMP, Metric.REFRESH, Metric.CPU_POWER, Metric.GPU_POWER};
    private String id = "";
    private String restoredFrom, restoredTo;
    private List<HistoryStore.Point> points = new ArrayList<>();
    private HistoryStore.Point selected;
    private int generation, listLimit = 20;
    private int drawGeneration;
    private boolean loading;
    private boolean followLatest = true;
    private String lastActive = "";
    private long lastRefresh;
    private final Runnable tick = new Runnable() {
        public void run() {
            status.setText(history.status + (MonitorService.paused ? " · 采样已暂停" : ""));
            recordButton.setText(history.active.isEmpty() ? "开始记录" : "结束并保存记录");
            if (!lastActive.equals(history.active)) {
                lastActive = history.active;
                if (id.isEmpty()) loadList();
                else pointButton.setEnabled(selected != null && !id.equals(history.active));
            }
            if (!id.isEmpty() && id.equals(history.active) && SystemClock.elapsedRealtime() - lastRefresh > 3000 && !loading) loadDetail(false);
            ui.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); history = History.get(this); prefs = new Prefs(this);
        if (state != null) {
            id = state.getString("id", ""); followLatest = state.getBoolean("follow", true);
            restoredFrom = state.getString("from"); restoredTo = state.getString("to");
        } else id = history.active;
        build();
    }
    @Override protected void onResume() { super.onResume(); ui.post(tick); }
    @Override protected void onPause() { ui.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { generation++; ui.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle b) {
        b.putString("id", id); b.putBoolean("follow", followLatest);
        if (!id.isEmpty() && from != null) { b.putString("from", from.getText().toString()); b.putString("to", to.getText().toString()); }
        super.onSaveInstanceState(b);
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(0xFFE8EEF3); v.setPadding(0, dp(6), 0, dp(6)); return v; }
    private LinearLayout col() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); return c; }
    private Button button(LinearLayout parent, String s, Runnable action) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(0xFF62E0CA); b.setOnClickListener(v -> action.run());
        b.setTextSize(13); b.setPadding(dp(12), dp(10), dp(12), dp(10)); b.setMinHeight(dp(44));
        GradientDrawable background = new GradientDrawable(); background.setColor(0xFF22333E); background.setCornerRadius(dp(12)); b.setBackground(background);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(6), 0, dp(6)); parent.addView(b, p); return b;
    }
    private LinearLayout card(String title) {
        LinearLayout c = col(); c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFF151F29); bg.setCornerRadius(dp(16)); c.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); content.addView(c, p);
        TextView t = text(title, 18); t.setTypeface(null, Typeface.BOLD); c.addView(t); return c;
    }
    private void build() {
        generation++; drawGeneration++; loading = false; selected = null;
        charts.clear(); captions.clear(); series.clear(); points = new ArrayList<>();
        LinearLayout root = col(); root.setBackgroundColor(0xFF0C1218);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        button(root, id.isEmpty() ? "‹ 返回 MurasameRTSS" : "‹ 全部记录", () -> { if (id.isEmpty()) finish(); else { id = ""; build(); } });
        scroll = new ScrollView(this); content = col(); content.setPadding(dp(16), dp(8), dp(16), dp(24)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        LinearLayout control = card("记录与曲线"); status = text(history.status, 13); control.addView(status);
        Switch auto = new Switch(this); auto.setText("监控时自动记录"); auto.setTextColor(0xFFE8EEF3); auto.setTextSize(14);
        auto.setPadding(0, dp(8), 0, dp(8)); auto.setChecked(prefs.autoRecord());
        auto.setOnCheckedChangeListener((v, enabled) -> prefs.put("auto_record", enabled)); control.addView(auto);
        recordButton = button(control, history.active.isEmpty() ? "开始记录" : "结束并保存记录", () -> {
            if (!MonitorService.running && history.active.isEmpty()) { toast("请先返回监控页，开启监控后再开始记录"); return; }
            startService(new Intent(this, MonitorService.class).setAction(history.active.isEmpty() ? MonitorService.RECORD_START : MonitorService.RECORD_STOP));
            ui.postDelayed(() -> { if (!isDestroyed()) {
                if (id.isEmpty() && !history.active.isEmpty()) { id = history.active; build(); }
                else if (id.isEmpty()) loadList(); else loadDetail(false);
            } }, 500);
        });
        control.addView(text("开关默认开启：开始监控即记录，停止监控即保存。记录所有可采集指标，与浮窗开关无关。手动结束后本次不再自动续录；关闭开关也会结束当前记录。", 12));
        if (id.isEmpty()) {
            button(control, "查看当前曲线", () -> { if (history.active.isEmpty()) toast("当前未记录，开启监控或手动开始记录"); else { id = history.active; build(); } });
            records = card("历史记录"); button(records, "刷新列表", this::loadList); loadList();
        } else detail();
    }
    private void loadList() {
        final int token = ++generation;
        history.io.execute(() -> {
            try { List<String> list = history.store().list(); ui.post(() -> {
                if (isDestroyed() || token != generation) return;
                records.removeAllViews(); records.addView(text("历史记录 · " + list.size() + " 次", 18)); button(records, "刷新列表", this::loadList);
                if (list.isEmpty()) records.addView(text("还没有记录。开启自动记录后，开始监控即可生成记录。", 14));
                for (int i = 0; i < Math.min(listLimit, list.size()); i++) {
                    String entry = list.get(i);
                    button(records, date(HistoryStore.started(entry)) + (entry.equals(history.active) ? " · 记录中" : ""), () -> { id = entry; build(); });
                }
                if (list.size() > listLimit) button(records, "加载更早的记录", () -> { listLimit += 20; loadList(); });
            }); } catch (Exception e) { error(e); }
        });
    }
    private void detail() {
        LinearLayout c = card(date(HistoryStore.started(id)));
        LinearLayout graphCard = c;
        button(c, "时间范围 / 删除", () -> scroll.smoothScrollTo(0, edits.getTop()));
        summary = text("正在加载…", 12); c.addView(summary);
        selection = text("点选任意曲线同步查看同一时刻的全部指标。", 12); c.addView(selection);
        for (Metric m : PANELS) {
            LinearLayout panel = col(); panel.setPadding(dp(7), dp(5), dp(7), dp(5)); panel.setBackgroundColor(0xFF1B1D20);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(7); c.addView(panel, lp);
            TextView title = text(m.title + " · " + unit(m), 13); title.setTextColor(0xFFBFD9EF); title.setTypeface(null, Typeface.BOLD); panel.addView(title);
            TextView caption = text("等待数据", 11); caption.setTypeface(Typeface.MONOSPACE);
            caption.setMinLines(3); caption.setMaxLines(3); caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            panel.addView(caption); captions.put(m, caption);
            HistoryChart chart = new HistoryChart(this); chart.selection(this::selectPoint);
            panel.addView(chart, new LinearLayout.LayoutParams(-1, dp(124))); charts.put(m, chart);
        }
        edits = card("时间范围与删除");
        c = edits;
        button(c, "返回曲线", () -> scroll.smoothScrollTo(0, graphCard.getTop()));
        c.addView(text("查看 / 删除范围（从记录开始，填时:分:秒或秒数）", 13));
        from = field(c, "开始时间", restoredFrom == null ? "00:00:00" : restoredFrom);
        to = field(c, "结束时间", restoredTo == null ? "" : restoredTo);
        button(c, "查看此时间段", () -> { followLatest = false; draw(); });
        button(c, "显示全部 / 刷新数据", () -> { followLatest = true; loadDetail(true); });
        c.addView(text("删除会同时移除此采样点或时间段内的所有指标；正在记录的会话需先结束。删除后不能撤销。", 12));
        pointButton = button(c, "删除选中采样点", () -> {
            if (selected == null) { toast("请先点击曲线选择一个采样点"); return; }
            long sequence = selected.sequence; String entry = id;
            confirm("删除选中采样点？", "将删除 " + HistoryMath.time(selected.elapsed) + " 的所有指标数据。", () -> history.store().deletePoints(entry, 0, Long.MAX_VALUE, sequence), false);
        }); pointButton.setEnabled(false);
        button(c, "删除此时间段", () -> {
            try { long[] range = range(); String entry = id; confirm("删除此时间段？", "将删除 " + from.getText() + " 至 " + to.getText() + "（含边界秒）内的所有指标数据。", () -> history.store().deletePoints(entry, range[0], range[1], null), false); }
            catch (Exception e) { toast(e.getMessage()); }
        });
        button(c, "删除整次记录", () -> { String entry = id; confirm("删除整次记录？", "将永久删除 " + date(HistoryStore.started(entry)) + " 的全部数据。", () -> { history.store().delete(entry); return -1; }, true); });
        boolean resetRange = restoredTo == null;
        restoredFrom = null; restoredTo = null;
        loadDetail(resetRange);
    }
    private EditText field(LinearLayout c, String hint, String value) {
        EditText e = new EditText(this); e.setTextColor(0xFFE8EEF3); e.setHintTextColor(0xFF93A6B7); e.setHint(hint + " 00:00:00 或秒数");
        e.setSingleLine(); e.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI); e.setSelectAllOnFocus(true);
        e.setText(value); e.setContentDescription(hint); c.addView(e); return e;
    }
    private void loadDetail(boolean resetRange) {
        loading = true; lastRefresh = SystemClock.elapsedRealtime(); final String entry = id; final int token = ++generation;
        history.io.execute(() -> {
            try { List<HistoryStore.Point> result = history.store().read(entry); ui.post(() -> {
                if (isDestroyed() || token != generation) return; loading = false; points = result;
                if (resetRange || to.getText().length() == 0) {
                    from.setText("00:00:00"); to.setText(HistoryMath.time(points.isEmpty() ? 0 : points.get(points.size() - 1).elapsed));
                } else if (followLatest && entry.equals(history.active) && !to.hasFocus() && !from.hasFocus()) {
                    to.setText(HistoryMath.time(points.isEmpty() ? 0 : points.get(points.size() - 1).elapsed));
                }
                draw();
            }); } catch (Exception e) { ui.post(() -> { if (token == generation) loading = false; }); error(e); }
        });
    }
    private long[] range() {
        long start = HistoryMath.parseTime(from.getText().toString()), end = HistoryMath.parseTime(to.getText().toString()) + 999;
        if (end < start) throw new IllegalArgumentException("结束时间不能早于开始时间"); return new long[]{start, end};
    }
    private void draw() {
        if (charts.isEmpty() || from == null || to.getText().length() == 0) return;
        try {
            long[] range = range(); final int token = ++drawGeneration; final int pageToken = generation;
            List<HistoryStore.Point> captured = points;
            history.io.execute(() -> {
                EnumMap<Metric, HistorySeries> prepared = new EnumMap<>(Metric.class);
                for (Metric m : PANELS) prepared.put(m, new HistorySeries(captured, m, range[0], range[1]));
                ui.post(() -> {
                    if (isDestroyed() || token != drawGeneration || pageToken != generation) return;
                    series.clear(); series.putAll(prepared);
                    if (selected != null && prepared.get(PANELS[0]).points.stream().noneMatch(p -> p.sequence == selected.sequence)) selected = null;
                    summary.setText(prepared.get(PANELS[0]).points.size() + " / " + points.size() + " 个采样点 · " + PANELS.length + " 项指标 · 共用时间轴\n"
                            + "停止监控后保留全部曲线。无效读数、暂停与删除处不连线。");
                    for (Metric m : PANELS) charts.get(m).bind(prepared.get(m));
                    selectPoint(selected);
                });
            });
        } catch (Exception e) { toast(e.getMessage()); }
    }
    private void selectPoint(HistoryStore.Point point) {
        selected = point;
        selection.setText(point == null ? "显示范围末点读数；点选或横向拖动任意曲线，同步查看全部指标。"
                : "已选 " + HistoryMath.time(point.elapsed) + String.format(Locale.ROOT, ".%03d", point.elapsed % 1000)
                + " · " + date(point.wall) + "\n目标应用：" + point.values.getOrDefault("TARGET_APP", "旧记录未保存"));
        if (pointButton != null) pointButton.setEnabled(point != null && !id.equals(history.active));
        for (Metric m : PANELS) {
            HistorySeries s = series.get(m); if (s == null) continue;
            String value = s.value(point);
            captions.get(m).setText((point == null ? (id.equals(history.active) && followLatest ? "当前 " : "末点 ") : "选中 ") + value + "\n" + s.statistics()
                    + ("--".equals(value) ? "\n" + s.note(point) : ""));
            charts.get(m).selected(point);
        }
    }
    private interface Mutation { int run() throws Exception; }
    private void confirm(String title, String message, Mutation action, boolean whole) {
        if (id.equals(history.active)) { toast("请先结束并保存记录，再删除数据"); return; }
        final int token = generation;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
            history.io.execute(() -> {
                try { int removed = action.run(); ui.post(() -> {
                    if (isDestroyed() || token != generation) return;
                    toast(whole ? "整次记录已删除" : "已删除 " + removed + " 个采样点");
                    if (whole) { id = ""; build(); } else loadDetail(false);
                }); } catch (Exception e) { error(e); }
            });
        }).show();
    }
    private static String unit(Metric m) {
        switch (m) {
            case CPU_FREQ: return "GHz"; case GPU_FREQ: return "MHz"; case RAM: return "GiB";
            case FPS: return "FPS"; case FRAME_TIME: return "ms"; case BAT_LEVEL: return "%"; case REFRESH: return "Hz";
            case DEVICE_POWER: case CPU_POWER: case GPU_POWER: return "W";
            case BAT_POWER: return "W（充入 / 输出的绝对值）";
            default: return "°C";
        }
    }
    private String date(long wall) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(wall)); }
    private void error(Exception e) { ui.post(() -> { if (!isDestroyed()) toast("操作失败：" + e.getMessage()); }); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
