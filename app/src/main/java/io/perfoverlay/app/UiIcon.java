package io.perfoverlay.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small line icons drawn locally; no image decoding or extra runtime dependency. */
public final class UiIcon extends View {
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    private int color;
    public UiIcon(Context context, int kind, int color) {
        super(context); this.kind = kind; this.color = color;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void color(int value) { color=value; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); int save = canvas.save();
        float size = Math.min(getWidth(), getHeight());
        canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2); canvas.scale(size / 24, size / 24);
        pen.setColor(color); pen.setStrokeWidth(1.6f); pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case 0:
                Path home = new Path(); home.moveTo(3, 10); home.lineTo(12, 3); home.lineTo(21, 10);
                home.moveTo(5, 9); home.lineTo(5, 21); home.lineTo(10, 21); home.lineTo(10, 14); home.lineTo(14, 14); home.lineTo(14, 21); home.lineTo(19, 21); home.lineTo(19, 9); canvas.drawPath(home, pen); break;
            case 1:
                canvas.drawCircle(12, 12, 9, pen);
                canvas.drawCircle(8, 8, 1, pen); canvas.drawCircle(14, 6, 1, pen);
                canvas.drawCircle(18, 11, 1, pen); canvas.drawCircle(8, 15, 1, pen); break;
            case 2:
                canvas.drawLine(5, 20, 5, 12, pen); canvas.drawLine(12, 20, 12, 4, pen); canvas.drawLine(19, 20, 19, 8, pen); break;
            case 3:
                canvas.drawRoundRect(5, 3, 19, 21, 2, 2, pen);
                canvas.drawLine(9, 8, 15, 8, pen); canvas.drawLine(9, 12, 15, 12, pen); canvas.drawLine(9, 16, 13, 16, pen); break;
            case 4:
                canvas.drawCircle(12, 12, 9, pen); canvas.drawLine(12, 11, 12, 17, pen); canvas.drawPoint(12, 7, pen); break;
            case 5:
                canvas.drawRoundRect(6, 6, 18, 18, 2, 2, pen); canvas.drawRect(9, 9, 15, 15, pen);
                for (int i = 8; i <= 16; i += 4) { canvas.drawLine(i, 3, i, 6, pen); canvas.drawLine(i, 18, i, 21, pen); canvas.drawLine(3, i, 6, i, pen); canvas.drawLine(18, i, 21, i, pen); } break;
            case 6:
                canvas.drawRoundRect(3, 6, 21, 17, 2, 2, pen);
                for (int i = 6; i <= 18; i += 4) { canvas.drawLine(i, 9, i, 13, pen); canvas.drawLine(i, 17, i, 20, pen); } break;
            case 7:
                canvas.drawRoundRect(9, 3, 15, 16, 3, 3, pen); canvas.drawCircle(12, 17, 4, pen); canvas.drawLine(12, 8, 12, 17, pen); break;
            default:
                canvas.drawRoundRect(6, 4, 18, 21, 2, 2, pen); canvas.drawLine(10, 2, 14, 2, pen); canvas.drawLine(9, 15, 15, 15, pen); break;
        }
        canvas.restoreToCount(save);
    }
}
