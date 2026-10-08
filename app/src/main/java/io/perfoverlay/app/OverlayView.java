package io.perfoverlay.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public final class OverlayView extends View {
    private final Prefs prefs;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint background = new Paint(), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Metric> metrics = new ArrayList<>();
    private final List<String> labels = new ArrayList<>(), values = new ArrayList<>();
    private Snapshot snapshot = new Snapshot();
    private FlowLayout layout;
    private float padding, rowHeight, baseline, maxWidth = 1000;
    public OverlayView(Context c, Prefs prefs) {
        super(c); this.prefs = prefs;
        text.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false); setFocusable(false);
    }
    public static float safeWindowAlpha(Context c) {
        float max = 0.8f;
        if (Build.VERSION.SDK_INT >= 31) {
            try { max = c.getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch(); }
            catch (Exception ignored) { }
        }
        return MetricMath.touchAlpha(Build.VERSION.SDK_INT, max);
    }
    public void update(Snapshot snapshot) { this.snapshot = snapshot; requestLayout(); invalidate(); }
    public void maxWidth(float width) { maxWidth = Math.max(1, width); requestLayout(); }
    public int rowCount() { return layout == null ? 0 : layout.rows; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float sp = getResources().getDisplayMetrics().scaledDensity;
        text.setTextSize(prefs.font() * sp);
        Paint.FontMetrics fm = text.getFontMetrics();
        padding = text.getTextSize() * 0.5f;
        rowHeight = fm.bottom - fm.top + text.getTextSize() * 0.45f;
        baseline = -fm.top + text.getTextSize() * 0.225f;
        metrics.clear(); labels.clear(); values.clear();
        for (Metric m : prefs.order()) if (prefs.enabled(m)) {
            metrics.add(m); labels.add(m.label + " "); values.add(snapshot.values.get(m));
        }
        if (metrics.isEmpty()) { metrics.add(Metric.RAM); labels.add(""); values.add("请在应用中选择显示项目"); }
        float available = maxWidth;
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) available = Math.min(available, MeasureSpec.getSize(widthSpec));
        float[] widths = new float[metrics.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = text.measureText(labels.get(i) + values.get(i));
        layout = FlowLayout.compute(widths, available, text.getTextSize() * 1.25f, padding, prefs.vertical());
        int width = Math.max(1, (int) Math.ceil(layout.width)), height = Math.max(1, (int) Math.ceil(layout.rows * rowHeight));
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float windowAlpha = safeWindowAlpha(getContext());
        float desired = Math.min(windowAlpha, prefs.opacity() / 100f);
        background.setColor(Color.argb(MetricMath.backgroundPaintAlpha(desired, windowAlpha), 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        if (layout == null) return;
        text.setShadowLayer(1.5f, 0, 1, Color.BLACK);
        for (FlowLayout.Item item : layout.items) {
            int i = item.index;
            float y = item.row * rowHeight + baseline;
            String label = labels.get(i), value = values.get(i);
            int clip = canvas.save();
            canvas.clipRect(item.x, item.row * rowHeight, item.x + item.width, (item.row + 1) * rowHeight);
            String full = label + value;
            if (text.measureText(full) > item.width) {
                text.setColor(Color.WHITE);
                canvas.drawText(TextUtils.ellipsize(full, text, item.width, TextUtils.TruncateAt.END).toString(), item.x, y, text);
            } else {
                text.setColor(0xFFF1F5F8); canvas.drawText(label, item.x, y, text);
                Metric m = metrics.get(i);
                text.setColor(m == Metric.CPU_TEMP || m == Metric.GPU_TEMP || m == Metric.SOC_TEMP || m == Metric.BAT_TEMP ? 0xFFFFC75F : 0xFF67E9E3);
                if ("--".equals(value)) text.setColor(0xFFA6B0BA);
                canvas.drawText(value, item.x + text.measureText(label), y, text);
            }
            canvas.restoreToCount(clip);
            if (prefs.separators() && item.x > padding + 1) {
                float x = item.x - text.getTextSize() * 0.625f;
                line.setColor(0xFF889399); line.setStrokeWidth(Math.max(1, text.getTextSize() / 20));
                canvas.drawLine(x, item.row * rowHeight + rowHeight * .28f, x, item.row * rowHeight + rowHeight * .72f, line);
            }
        }
    }
}
