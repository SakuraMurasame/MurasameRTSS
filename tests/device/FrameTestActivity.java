package io.perfoverlay.frametest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;

/** Controlled independent-UID rendering target, no permissions or user data. */
public final class FrameTestActivity extends Activity implements SurfaceHolder.Callback, Choreographer.FrameCallback {
    private SurfaceView surface;
    private boolean ready;
    private int target = 30, taps;
    private long frames, previous;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        configure(getIntent());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        surface = new SurfaceView(this); surface.getHolder().addCallback(this);
        surface.setOnTouchListener((v, e) -> { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) taps++; return true; });
        setContentView(surface);
    }
    private void configure(Intent intent) {
        target = intent.getIntExtra("fps", 30);
        if (intent.hasExtra("orientation")) setRequestedOrientation(intent.getIntExtra("orientation", -1));
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); configure(intent); }
    @Override public void surfaceCreated(SurfaceHolder holder) { ready = true; Choreographer.getInstance().postFrameCallback(this); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { ready = false; Choreographer.getInstance().removeFrameCallback(this); }
    @Override public void doFrame(long time) {
        if (!ready) return;
        if (time - previous >= 1_000_000_000L / Math.max(1, target) - 1_000_000L) {
            previous = time;
            Canvas c = surface.getHolder().lockCanvas();
            if (c != null) {
                c.drawColor(0xFF152D3B); float density = getResources().getDisplayMetrics().density;
                paint.setColor(0xFF63E5CA); paint.setTextSize(24 * density);
                c.drawText("FPS / TOUCH TEST", 28 * density, 210 * density, paint);
                paint.setColor(Color.WHITE); paint.setTextSize(18 * density);
                c.drawText("Target: " + target + " fps", 28 * density, 250 * density, paint);
                c.drawText("Taps received: " + taps, 28 * density, 285 * density, paint);
                c.drawText("Frames drawn: " + (++frames), 28 * density, 320 * density, paint);
                float x = c.getWidth() / 2f + (float) Math.sin(time / 1e9 * 2) * c.getWidth() * .35f;
                paint.setColor(0xFF63E5CA); c.drawCircle(x, c.getHeight() * .7f, 24 * density, paint);
                surface.getHolder().unlockCanvasAndPost(c);
            }
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
    @Override public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println("FRAME_TEST target=" + target + " frames=" + frames + " taps=" + taps + " ready=" + ready);
    }
}
