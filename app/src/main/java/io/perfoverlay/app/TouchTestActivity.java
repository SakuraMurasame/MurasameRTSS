package io.perfoverlay.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;

public final class TouchTestActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(new TouchCanvas());
    }
    private final class TouchCanvas extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path trail = new Path();
        private int taps, events, fingers;
        private float x = -1, y = -1;
        TouchCanvas() { super(TouchTestActivity.this); setBackgroundColor(0xFF172B36); }
        @Override protected void onDraw(Canvas c) {
            float density = getResources().getDisplayMetrics().density;
            p.setColor(0xFF34505E); p.setStrokeWidth(1); p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < getWidth(); i += 64) c.drawLine(i, 0, i, getHeight(), p);
            for (int i = 0; i < getHeight(); i += 64) c.drawLine(0, i, getWidth(), i, p);
            p.setColor(0xFF62E0CA); p.setStrokeWidth(4); c.drawPath(trail, p);
            if (x >= 0) c.drawCircle(x, y, 25 * density, p);
            p.setStyle(Paint.Style.FILL); p.setTextSize(18 * density); p.setColor(Color.WHITE);
            int top = Math.max((int) (150 * density), getHeight() / 3);
            String message = I18n.t("触摸测试 · 点击顶部浮窗覆盖区域") + "\n\n"
                    + I18n.t("点击 ") + taps + I18n.t("    事件 ") + events + I18n.t("    手指 ") + fingers
                    + "\n\n" + I18n.t("滑动会留下轨迹；系统返回手势退出。")
                    + "\n" + I18n.t("本页验证触摸标志，跨应用穿透还需在游戏中确认。");
            android.text.TextPaint label = new android.text.TextPaint(p); label.setTextSize(14 * density);
            android.text.StaticLayout layout = android.text.StaticLayout.Builder.obtain(message, 0, message.length(), label,
                    Math.max(1, getWidth() - (int) (40 * density))).setIncludePad(false).build();
            int save = c.save(); c.translate(20 * density, top); layout.draw(c); c.restoreToCount(save);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            events++; fingers = e.getPointerCount(); x = e.getX(); y = e.getY();
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { taps++; trail.reset(); trail.moveTo(x, y); }
            else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) trail.lineTo(x, y);
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) fingers = 0;
            invalidate(); return true;
        }
    }
}
