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
    private UiTheme theme;
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
    private long restoredSelection = -1;
    private int listPosition;
    private boolean listNeedsRestore;
    private int generation, listLimit = 20;
    private int drawGeneration;
    private boolean loading;
    private boolean followLatest = true;
    private String lastActive = "";
    private long lastRefresh;
    private final Runnable tick = new Runnable() {
        public void run() {
            status.setText(I18n.raw(history.status) + (MonitorService.paused ? I18n.t(" · 采样已暂停") : ""));
            recordButton.setText(history.active.isEmpty() ? I18n.t("开始记录") : I18n.t("结束并保存记录"));
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
        UiTheme.beforeCreate(this); super.onCreate(state); history = History.get(this); prefs = new Prefs(this);
        if (state != null) {
            id = state.getString("id", ""); followLatest = state.getBoolean("follow", true);
            restoredFrom = state.getString("from"); restoredTo = state.getString("to");
            restoredSelection = state.getLong("selection", -1);
            listPosition=state.getInt("listPosition",0);
        } else id = history.active;
        build();
        UiMotion.backHandler(this,this::backAction);
    }
    @Override protected void onResume() { super.onResume(); if (theme != null && theme.dark != UiTheme.get(this).dark) { recreate(); return; } ui.removeCallbacks(tick); ui.post(tick); }
    @Override protected void onPause() { ui.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { generation++; ui.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle b) {
        b.putString("id", id); b.putBoolean("follow", followLatest);
        b.putLong("selection", selected == null ? restoredSelection : selected.sequence);
        b.putInt("listPosition",id.isEmpty() && scroll!=null?scroll.getScrollY():listPosition);
        if (!id.isEmpty() && from != null) { b.putString("from", from.getText().toString()); b.putString("to", to.getText().toString()); }
        super.onSaveInstanceState(b);
    }
    @Override public void onBackPressed() { backAction(); }
    private void backAction() {
        if (!id.isEmpty()) { id=""; build(); UiMotion.reveal(content,-1); }
        else UiMotion.back(this);
    }
    private void openRecord(String entry) {
        if (id.isEmpty() && scroll!=null) listPosition=scroll.getScrollY();
        id=entry; restoredFrom=null; restoredTo=null; restoredSelection=-1; followLatest=true;
        build(); UiMotion.reveal(content,1);
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(theme.ink); v.setPadding(0, dp(6), 0, dp(6)); return v; }
    private LinearLayout col() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); return c; }
    private Button button(LinearLayout parent, String s, Runnable action) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(theme.accent); b.setOnClickListener(v -> action.run());
        b.setTextSize(13); b.setPadding(dp(12), dp(10), dp(12), dp(10)); b.setMinHeight(dp(44)); b.setMinWidth(0);
        b.setBackground(UiMotion.surface(this,theme,theme.surface,12,false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(6), 0, dp(6)); parent.addView(b, p); return b;
    }
    private LinearLayout card(String title) {
        LinearLayout c = col(); c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(theme.card); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1), theme.border); c.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); content.addView(c, p);
        if (!title.isEmpty()) { TextView t = text(title, 18); t.setTypeface(null, Typeface.BOLD); c.addView(t); } return c;
    }
    private void build() {
        theme = UiTheme.get(this); theme.window(this);
        generation++; drawGeneration++; loading = false; selected = null;
        charts.clear(); captions.clear(); series.clear(); points = new ArrayList<>();
        LinearLayout root = col(); root.setBackgroundColor(theme.bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20), dp(12), dp(20), dp(8));
        if (!id.isEmpty()) {
            Button back = new Button(this); back.setText("‹"); back.setTextSize(25); back.setTextColor(theme.accent);
            back.setContentDescription(I18n.t("‹ 全部记录")); back.setMinWidth(0); back.setPadding(0, 0, 0, 0);
            back.setBackground(UiMotion.surface(this,theme,theme.surface,12,false)); back.setOnClickListener(v -> backAction());
            LinearLayout.LayoutParams backLayout = new LinearLayout.LayoutParams(dp(44), dp(44)); backLayout.rightMargin = dp(10);
            header.addView(back, backLayout);
        }
        TextView heading = text(I18n.t("记录与曲线"), 23); heading.setTypeface(null, Typeface.BOLD); header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView mascot = new ImageView(this); mascot.setImageResource(R.drawable.murasame_character); mascot.setContentDescription(I18n.t("丛雨程序图标")); header.addView(mascot, new LinearLayout.LayoutParams(dp(44), dp(44))); root.addView(header);
        scroll = new ScrollView(this); content = col(); content.setPadding(dp(16), dp(8), dp(16), dp(24)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); LinearLayout nav = UiNav.create(this, theme, 3, index -> {
            if (index == 3) return;
            UiMotion.open(this,new Intent(this,MainActivity.class).putExtra("page",index)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),index>3?1:-1); finish();
        });
        LinearLayout.LayoutParams navLayout = new LinearLayout.LayoutParams(-1, -2); navLayout.setMargins(dp(12), 0, dp(12), dp(6)); root.addView(nav, navLayout);
        setContentView(root);
        LinearLayout control = card(""); status = text(I18n.raw(history.status), 13); control.addView(status);
        Switch auto = new Switch(this); auto.setText(I18n.t("监控时自动记录")); auto.setTextColor(theme.ink); auto.setTextSize(14);
        auto.setPadding(0, dp(8), 0, dp(8)); auto.setChecked(prefs.autoRecord());
        auto.setOnCheckedChangeListener((v, enabled) -> prefs.put("auto_record", enabled));
        LinearLayout recordOptions = new LinearLayout(this); recordOptions.setGravity(Gravity.CENTER_VERTICAL);
        recordOptions.addView(auto, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout help = new FrameLayout(this); help.setContentDescription(I18n.t("记录说明")); help.setFocusable(true);
        help.setBackground(theme.shape(this, theme.surface, 12, false));
        help.addView(new UiIcon(this, 4, theme.accent), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(I18n.t("记录说明"))
                .setMessage(I18n.t("开关默认开启：开始监控即记录，停止监控即保存。记录所有可采集指标，与浮窗开关无关。手动结束后本次不再自动续录；关闭开关也会结束当前记录。")
                    + "\n\n" + I18n.t("停止监控后保留全部曲线。无效读数、暂停与删除处不连线。"))
                .setNegativeButton(I18n.t("取消"), null).show());
        LinearLayout.LayoutParams helpLayout = new LinearLayout.LayoutParams(dp(44), dp(44)); helpLayout.leftMargin = dp(10);
        recordOptions.addView(help, helpLayout); control.addView(recordOptions);
        recordButton = button(control, history.active.isEmpty() ? I18n.t("开始记录") : I18n.t("结束并保存记录"), () -> {
            if (!MonitorService.running && history.active.isEmpty()) { toast(I18n.t("请先返回监控页，开启监控后再开始记录")); return; }
            startService(new Intent(this, MonitorService.class).setAction(history.active.isEmpty() ? MonitorService.RECORD_START : MonitorService.RECORD_STOP));
            ui.postDelayed(() -> { if (!isDestroyed()) {
                if (id.isEmpty() && !history.active.isEmpty()) { id = history.active; build(); }
                else if (id.isEmpty()) loadList(); else loadDetail(false);
            } }, 500);
        });
        if (id.isEmpty()) {
            button(control, I18n.t("应用耗电记录"), () -> UiMotion.open(this,new Intent(this,AppBatteryActivity.class),1));
            button(control, I18n.t("查看当前曲线"), () -> { if (history.active.isEmpty()) toast(I18n.t("当前未记录，开启监控或手动开始记录")); else openRecord(history.active); });
            records = card(I18n.t("历史记录")); button(records, I18n.t("刷新列表"), this::loadList); listNeedsRestore=true; loadList();
        } else detail();
    }
    private void loadList() {
        final int wanted=listNeedsRestore?listPosition:scroll.getScrollY();
        final int token = ++generation;
        history.io.execute(() -> {
            try { List<String> list = history.store().list(); ui.post(() -> {
                if (isDestroyed() || token != generation) return;
                records.removeAllViews(); records.addView(text(I18n.t("历史记录 · ") + list.size() + I18n.t(" 次"), 18)); button(records, I18n.t("刷新列表"), this::loadList);
                if (list.isEmpty()) records.addView(text(I18n.t("还没有记录。开启自动记录后，开始监控即可生成记录。"), 14));
                for (int i = 0; i < Math.min(listLimit, list.size()); i++) {
                    String entry = list.get(i);
                    button(records, date(HistoryStore.started(entry)) + (entry.equals(history.active) ? I18n.t(" · 记录中") : ""), () -> openRecord(entry));
                }
                if (list.size() > listLimit) button(records, I18n.t("加载更早的记录"), () -> { listLimit += 20; loadList(); });
                listNeedsRestore=false; UiMotion.restoreScroll(scroll,wanted);
            }); } catch (Exception e) { error(e); }
        });
    }
    private void detail() {
        LinearLayout c = card(date(HistoryStore.started(id)));
        LinearLayout graphCard = c;
        button(c, I18n.t("时间范围 / 删除"), () -> scroll.smoothScrollTo(0, edits.getTop()));
        summary = text(I18n.t("正在加载…"), 12); c.addView(summary);
        selection = text(I18n.t("点选任意曲线同步查看同一时刻的全部指标。"), 12); c.addView(selection);
        for (Metric m : PANELS) {
            LinearLayout panel = card("");
            TextView title = text(I18n.t(m.title) + " · " + unit(m), 13); title.setTextColor(theme.ink); title.setTypeface(null, Typeface.BOLD); panel.addView(title);
            TextView caption = text(I18n.t("等待数据"), 11); caption.setTypeface(Typeface.MONOSPACE);
            caption.setMinLines(2);
            panel.addView(caption); captions.put(m, caption);
            HistoryChart chart = new HistoryChart(this); chart.selection(this::selectPoint);
            panel.addView(chart, new LinearLayout.LayoutParams(-1, dp(124))); charts.put(m, chart);
        }
        edits = card(I18n.t("时间范围与删除"));
        c = edits;
        button(c, I18n.t("返回曲线"), () -> scroll.smoothScrollTo(0, graphCard.getTop()));
        c.addView(text(I18n.t("查看 / 删除范围（从记录开始，填时:分:秒或秒数）"), 13));
        from = field(c, I18n.t("开始时间"), restoredFrom == null ? "00:00:00" : restoredFrom);
        to = field(c, I18n.t("结束时间"), restoredTo == null ? "" : restoredTo);
        button(c, I18n.t("查看此时间段"), () -> { followLatest = false; draw(); });
        button(c, I18n.t("显示全部 / 刷新数据"), () -> { followLatest = true; loadDetail(true); });
        c.addView(text(I18n.t("删除会同时移除此采样点或时间段内的所有指标；正在记录的会话需先结束。删除后不能撤销。"), 12));
        pointButton = button(c, I18n.t("删除选中采样点"), () -> {
            if (selected == null) { toast(I18n.t("请先点击曲线选择一个采样点")); return; }
            long sequence = selected.sequence; String entry = id;
            confirm(I18n.t("删除选中采样点？"), I18n.t("将删除 ") + HistoryMath.time(selected.elapsed) + I18n.t(" 的所有指标数据。"), () -> history.store().deletePoints(entry, 0, Long.MAX_VALUE, sequence), false);
        }); pointButton.setEnabled(false);
        button(c, I18n.t("删除此时间段"), () -> {
            try { long[] range = range(); String entry = id; confirm(I18n.t("删除此时间段？"), I18n.t("将删除 ") + from.getText() + I18n.t(" 至 ") + to.getText() + I18n.t("（含边界秒）内的所有指标数据。"), () -> history.store().deletePoints(entry, range[0], range[1], null), false); }
            catch (Exception e) { toast(e.getMessage()); }
        });
        button(c, I18n.t("删除整次记录"), () -> { String entry = id; confirm(I18n.t("删除整次记录？"), I18n.t("将永久删除 ") + date(HistoryStore.started(entry)) + I18n.t(" 的全部数据。"), () -> { history.store().delete(entry); return -1; }, true); });
        boolean resetRange = restoredTo == null;
        restoredFrom = null; restoredTo = null;
        loadDetail(resetRange);
    }
    private EditText field(LinearLayout c, String hint, String value) {
        EditText e = new EditText(this); e.setTextColor(theme.ink); e.setHintTextColor(theme.muted); e.setHint(hint + I18n.t(" 00:00:00 或秒数"));
        e.setSingleLine(); e.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI); e.setSelectAllOnFocus(true);
        e.setText(value); e.setContentDescription(hint); c.addView(e); return e;
    }
    private void loadDetail(boolean resetRange) {
        loading = true; lastRefresh = SystemClock.elapsedRealtime(); final String entry = id; final int token = ++generation;
        history.io.execute(() -> {
            try { List<HistoryStore.Point> result = history.store().read(entry); ui.post(() -> {
                if (isDestroyed() || token != generation) return; loading = false; points = result;
                if (restoredSelection >= 0) {
                    for (HistoryStore.Point point : points) if (point.sequence == restoredSelection) { selected = point; break; }
                    restoredSelection = -1;
                }
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
        if (end < start) throw new IllegalArgumentException(I18n.t("结束时间不能早于开始时间")); return new long[]{start, end};
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
                    summary.setText(prepared.get(PANELS[0]).points.size() + " / " + points.size() + I18n.t(" 个采样点 · ") + PANELS.length + I18n.t(" 项指标 · 共用时间轴\n").trim());
                    for (Metric m : PANELS) charts.get(m).bind(prepared.get(m));
                    selectPoint(selected);
                });
            });
        } catch (Exception e) { toast(e.getMessage()); }
    }
    private void selectPoint(HistoryStore.Point point) {
        selected = point;
        selection.setText(point == null ? I18n.t("显示范围末点读数；点选或横向拖动任意曲线，同步查看全部指标。")
                : I18n.t("已选 ") + HistoryMath.time(point.elapsed) + String.format(Locale.ROOT, ".%03d", point.elapsed % 1000)
                + " · " + date(point.wall) + I18n.t("\n目标应用：") + point.values.getOrDefault("TARGET_APP", I18n.t("旧记录未保存")));
        if (pointButton != null) pointButton.setEnabled(point != null && !id.equals(history.active));
        for (Metric m : PANELS) {
            HistorySeries s = series.get(m); if (s == null) continue;
            String value = I18n.raw(s.value(point));
            captions.get(m).setText((point == null ? (id.equals(history.active) && followLatest ? I18n.t("当前 ") : I18n.t("末点 ")) : I18n.t("选中 ")) + value + "\n" + s.statistics()
                    + ("--".equals(value) ? "\n" + I18n.raw(s.note(point)) : ""));
            charts.get(m).selected(point);
        }
    }
    private interface Mutation { int run() throws Exception; }
    private void confirm(String title, String message, Mutation action, boolean whole) {
        if (id.equals(history.active)) { toast(I18n.t("请先结束并保存记录，再删除数据")); return; }
        final int token = generation;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton(I18n.t("取消"), null).setPositiveButton(I18n.t("删除"), (d, w) -> {
            history.io.execute(() -> {
                try { int removed = action.run(); ui.post(() -> {
                    if (isDestroyed() || token != generation) return;
                    toast(whole ? I18n.t("整次记录已删除") : I18n.t("已删除 ") + removed + I18n.t(" 个采样点"));
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
            case BAT_POWER: return I18n.t("W（充入 / 输出的绝对值）");
            default: return "°C";
        }
    }
    private String date(long wall) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(wall)); }
    private void error(Exception e) { ui.post(() -> { if (!isDestroyed()) toast(I18n.t("操作失败：") + e.getMessage()); }); }
    private void toast(String s) { Toast.makeText(this, I18n.raw(s), Toast.LENGTH_LONG).show(); }
}
