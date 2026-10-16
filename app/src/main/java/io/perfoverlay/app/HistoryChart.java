package io.perfoverlay.app;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;

/** Compact graph with shared time bounds and a synchronized cursor. */
public final class HistoryChart extends View {
    private final UiTheme theme;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors;
    private HistorySeries data;
    private double low, high = 1;
    private HistoryStore.Point selected;
    private Path[] traces = new Path[0];
    public interface Selection { void selected(HistoryStore.Point point); }
    private Selection listener;
    private float downX, downY;
    public HistoryChart(Context c) { super(c); theme = UiTheme.get(c); colors = new int[]{theme.accent, theme.violet, theme.amber, theme.dark ? 0xFF79CAFF : 0xFF24699B, theme.dark ? 0xFFF199C7 : 0xFF9B3978, theme.dark ? 0xFFE9B58D : 0xFF885624}; setContentDescription(I18n.t("历史曲线，点击或横向拖动同步选择采样点")); }
    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
    public void selection(Selection callback) { listener = callback; }
    public void bind(HistorySeries series) {
        data = series; low = series.valid == 0 ? 0 : series.min; high = series.valid == 0 ? 1 : series.max;
        if (high - low < .01) { double padding = Math.max(.5, Math.abs(high) * .02); low = low >= 0 ? Math.max(0, low - padding) : low - padding; high += padding; }
        rebuildTraces(); setContentDescription(I18n.t(series.metric.title) + I18n.t("曲线；") + series.statistics()); invalidate();
    }
    public void selected(HistoryStore.Point p) { selected = p; invalidate(); }
    private float left() { return dp(42); }
    private float right() { return getWidth() - dp(10); }
    private float top() { return dp(16); }
    private float bottom() { return getHeight() - dp(43); }
    private float x(long time) { return left() + (time - data.from) * (right() - left()) / (data.to - data.from); }
    private float y(double v) { return bottom() - (float)((v - low) / (high - low)) * (bottom() - top()); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); rebuildTraces(); }
    private void rebuildTraces() {
        if (data == null || getWidth() == 0) return;
        traces = new Path[data.groups];
        for (int group = 0; group < data.groups; group++) {
            Path path = new Path(); traces[group] = path;
            HistoryStore.Point previous = null;
            for (int i = 0; i < data.points.size(); i++) {
                HistoryStore.Point p = data.points.get(i); double[] row = data.values.get(i);
                double value = group < row.length ? row[group] : Double.NaN;
                if (!Double.isFinite(value)) { previous = null; continue; }
                float px = x(p.elapsed), py = y(value);
                if (previous != null && HistoryMath.connects(previous.sequence, p.sequence, previous.elapsed, p.elapsed)) path.lineTo(px, py);
                else { path.moveTo(px, py); path.lineTo(px + dp(.25f), py); }
                previous = p;
            }
        }
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.drawColor(theme.card); if (data == null) return;
        pen.setTypeface(Typeface.MONOSPACE); pen.setTextSize(dp(9)); pen.setStrokeWidth(dp(.5f));
        pen.setColor(theme.grid);
        for (float gx = left(); gx <= right(); gx += dp(10)) c.drawLine(gx, top(), gx, bottom(), pen);
        for (float gy = top(); gy <= bottom(); gy += dp(10)) c.drawLine(left(), gy, right(), gy, pen);
        pen.setColor(theme.muted);
        c.drawText(String.format(Locale.ROOT, "%.1f", high), 0, top() + dp(6), pen);
        c.drawText(String.format(Locale.ROOT, "%.1f", low), 0, bottom(), pen);
        c.drawText(HistoryMath.time(data.from), left(), getHeight() - dp(5), pen);
        String end = HistoryMath.time(data.to); c.drawText(end, right() - pen.measureText(end), getHeight() - dp(5), pen);
        drawClockAxis(c);
        if (data.valid == 0) {
            String empty = I18n.t("暂无有效读数");
            c.drawText(empty, left() + (right() - left() - pen.measureText(empty)) / 2, (top() + bottom()) / 2, pen);
        }
        int save = c.save(); c.clipRect(left() - dp(1), 0, right() + dp(1), bottom() + dp(2));
        for (int group = 0; group < data.groups; group++) {
            pen.setColor(data.groups > 1 ? colors[group % colors.length] : data.metric == Metric.CPU_TEMP || data.metric == Metric.GPU_TEMP || data.metric == Metric.SOC_TEMP || data.metric == Metric.BAT_TEMP ? theme.amber : theme.accent); pen.setStrokeWidth(dp(.8f));
            if (data.groups > 1) c.drawText(I18n.t("组") + (group + 1), left() + group * dp(34), dp(10), pen);
            pen.setStyle(Paint.Style.STROKE);
            if (group < traces.length) c.drawPath(traces[group], pen);
            pen.setStyle(Paint.Style.FILL);
        }
        if (selected != null && selected.elapsed >= data.from && selected.elapsed <= data.to) {
            pen.setColor(theme.accent); pen.setStrokeWidth(dp(1));
            pen.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(3)}, 0));
            c.drawLine(x(selected.elapsed), top(), x(selected.elapsed), bottom(), pen); pen.setPathEffect(null);
        }
        c.restoreToCount(save);
        if (selected != null && selected.elapsed >= data.from && selected.elapsed <= data.to) {
            String stamp = HistoryMath.time(selected.elapsed);
            float labelX = Math.max(left(), Math.min(right() - pen.measureText(stamp), x(selected.elapsed) - pen.measureText(stamp) / 2));
            pen.setColor(theme.card); c.drawRect(labelX - dp(2), getHeight() - dp(18), labelX + pen.measureText(stamp) + dp(2), getHeight(), pen);
            pen.setColor(theme.accent); c.drawText(stamp, labelX, getHeight() - dp(5), pen);
        }
    }
    private void drawClockAxis(Canvas c) {
        if (data.points.isEmpty()) return;
        HistoryStore.Point first=data.points.get(0), last=data.points.get(data.points.size()-1);
        boolean dated=HistoryMath.differentDate(first.wall,last.wall);
        HistoryStore.Point focus=selected!=null && selected.elapsed>=data.from && selected.elapsed<=data.to?selected:null;
        String focused=focus==null?"":HistoryMath.clock(focus.wall,dated);
        float focusLeft=focus==null?Float.MAX_VALUE:labelX(focused,focus.elapsed);
        float focusRight=focusLeft+pen.measureText(focused);
        float priorRight=-1;
        // Use actual sample timestamps: clock adjustments must not be hidden by interpolation.
        HistoryStore.Point[] ticks={first,HistoryMath.nearest(data.points,data.from+(data.to-data.from)/2),last};
        long previous=-1;
        for(HistoryStore.Point point:ticks) {
            if(point==null || point.sequence==previous)continue; previous=point.sequence;
            String label=HistoryMath.clock(point.wall,dated); float start=labelX(label,point.elapsed), end=start+pen.measureText(label);
            if(start<priorRight+dp(6) || (focus!=null && end+dp(6)>focusLeft && start-dp(6)<focusRight))continue;
            pen.setColor(theme.muted); c.drawText(label,start,getHeight()-dp(24),pen);
            c.drawLine(x(point.elapsed),bottom(),x(point.elapsed),bottom()+dp(3),pen); priorRight=end;
        }
        if(focus!=null) { pen.setColor(theme.accent); c.drawText(focused,focusLeft,getHeight()-dp(24),pen); }
    }
    private float labelX(String label,long elapsed) {
        return Math.max(left(),Math.min(right()-pen.measureText(label),x(elapsed)-pen.measureText(label)/2));
    }
    private void select(float px) {
        if (data == null) return;
        HistoryStore.Point nearest = null; float distance = Float.MAX_VALUE;
        for (HistoryStore.Point p : data.points) { float d = Math.abs(x(p.elapsed) - px); if (d < distance) { distance = d; nearest = p; } }
        if (listener != null && nearest != null) listener.selected(nearest);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX=e.getX(); downY=e.getY(); select(downX); return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)) { getParent().requestDisallowInterceptTouchEvent(true); select(e.getX()); }
                return true;
            case MotionEvent.ACTION_UP: getParent().requestDisallowInterceptTouchEvent(false); performClick(); select(e.getX()); return true;
            case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
