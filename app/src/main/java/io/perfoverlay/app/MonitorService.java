package io.perfoverlay.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.view.*;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MonitorService extends Service implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String START = "start", STOP = "stop", HIDE = "hide", PAUSE = "pause", REFRESH = "refresh";
    public static final String RECORD_START = "record_start", RECORD_STOP = "record_stop";
    public static volatile boolean running, hidden, paused;
    public static volatile Snapshot latest;
    public static volatile String message = "已停止";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Prefs prefs;
    private ShizukuBridge bridge;
    private Collector collector;
    private History history;
    private String recordingNotice = "";
    private WindowManager windows;
    private Context windowContext;
    private OverlayView overlay;
    private WindowManager.LayoutParams params;
    private boolean attached, collecting, suspended, destroyed;
    private long lastConnect;
    private int availableWidth;
    private final Runnable reflow = () -> { if (!destroyed) resize(); };
    private final ComponentCallbacks windowConfiguration = new ComponentCallbacks() {
        @Override public void onConfigurationChanged(Configuration config) {
            main.removeCallbacks(reflow); main.post(reflow);
        }
        @Override public void onLowMemory() { }
    };
    private final Runnable expired = () -> {
        if (destroyed || paused || suspended || !collecting) return;
        Snapshot unavailable = new Snapshot();
        unavailable.backend = "采集超时，等待恢复；可停止后重试";
        latest = unavailable; message = unavailable.backend;
        history.gap();
        resize();
    };
    private final Runnable sampleTick = new Runnable() {
        @Override public void run() {
            if (destroyed || paused || suspended) return;
            if (!Settings.canDrawOverlays(MonitorService.this)) { message = "悬浮窗权限已关闭"; stopSelf(); return; }
            if (prefs.shizuku() && SystemClock.elapsedRealtime() - lastConnect > 5000) {
                bridge.connect(); lastConnect = SystemClock.elapsedRealtime();
            }
            if (collecting) return;
            collecting = true;
            String recording = history.active;
            main.removeCallbacks(expired);
            main.postDelayed(expired, Math.max(5000, prefs.interval() * 3L));
            worker.execute(() -> {
                Snapshot result;
                try { result = collector.sample(!recording.isEmpty()); }
                catch (Exception e) { result = new Snapshot(); result.backend = "采样暂时失败"; }
                Snapshot completed = result;
                main.post(() -> {
                    collecting = false;
                    main.removeCallbacks(expired);
                    if (destroyed) return;
                    if (!paused && !suspended) {
                        latest = completed; message = completed.backend;
                        history.capture(completed, recording);
                        updateRecordingNotice();
                        resize(); showIfNeeded();
                        main.removeCallbacks(sampleTick); main.postDelayed(sampleTick, prefs.interval());
                    }
                });
            });
        }
    };
    private final BroadcastReceiver languageChange = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            resize();
            getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("monitor", I18n.t("性能监控"), NotificationManager.IMPORTANCE_LOW));
            if (running) getSystemService(NotificationManager.class).notify(10, notification());
            updateTile();
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            suspended = !getSystemService(PowerManager.class).isInteractive() || getSystemService(KeyguardManager.class).isKeyguardLocked();
            if (suspended) { history.gap(); removeOverlay(); main.removeCallbacks(sampleTick); }
            else { main.removeCallbacks(sampleTick); main.post(sampleTick); }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this); bridge = new ShizukuBridge(this); collector = new Collector(this, prefs, bridge);
        history = History.get(this);
        Context visual = this;
        if (Build.VERSION.SDK_INT >= 30) {
            Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) visual = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        windows = visual.getSystemService(WindowManager.class);
        windowContext = visual;
        overlay = new OverlayView(visual, prefs);
        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.alpha = OverlayView.safeWindowAlpha(this);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        params.setTitle(I18n.t("MurasameRTSS 只读性能监控"));
        if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        prefs.store.registerOnSharedPreferenceChangeListener(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("monitor", I18n.t("性能监控"), NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(languageChange, new IntentFilter(MurasameApp.LANGUAGE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(languageChange, new IntentFilter(MurasameApp.LANGUAGE_CHANGED));
        windowContext.registerComponentCallbacks(windowConfiguration);
        resize();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? STOP : intent.getAction();
        if (STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        // Stale notification actions must never resurrect monitoring after it was stopped.
        if (!running && !START.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, I18n.t("请先允许显示在其他应用上层"), Toast.LENGTH_LONG).show(); stopSelf(); return START_NOT_STICKY; }
        boolean newMonitoring = !running;
        if (newMonitoring) { running = true; hidden = false; paused = false; latest = null; }
        if (HIDE.equals(action)) hidden = !hidden;
        if (PAUSE.equals(action)) { paused = !paused; history.gap(); }
        if (RECORD_START.equals(action)) history.begin();
        if (RECORD_STOP.equals(action)) history.end();
        if (RECORD_START.equals(action) || RECORD_STOP.equals(action)) main.postDelayed(this::updateRecordingNotice, 500);
        if (REFRESH.equals(action)) { bridge.close(); if (prefs.shizuku()) bridge.connect(); }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(10, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(10, notification());
        } catch (RuntimeException e) {
            message = "系统拒绝启动前台服务";
            Toast.makeText(this, I18n.raw(message), Toast.LENGTH_LONG).show(); stopSelf(); return START_NOT_STICKY;
        }
        // Only a new monitoring session auto-starts. Manual end must stay ended
        // through hide/pause/refresh and repeated START intents.
        if (newMonitoring && prefs.autoRecord()) history.begin();
        suspended = !getSystemService(PowerManager.class).isInteractive() || getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (prefs.shizuku()) bridge.connect();
        if (hidden || paused || suspended) removeOverlay(); else showIfNeeded();
        main.removeCallbacks(sampleTick);
        if (!paused && !suspended) main.post(sampleTick);
        updateTile();
        return START_NOT_STICKY;
    }
    private Notification notification() {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "monitor")
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("MurasameRTSS · " + (paused ? I18n.t("已暂停") : hidden ? I18n.t("浮窗已隐藏") : I18n.t("监控中")))
                .setContentText(history.active.isEmpty() ? I18n.t("点击打开设置 · 浮窗允许触摸穿透") : I18n.t("正在记录曲线 · 在应用的记录页结束并保存"))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .addAction(new Notification.Action.Builder(null, hidden ? I18n.t("显示") : I18n.t("隐藏"), action(HIDE, 1)).build())
                .addAction(new Notification.Action.Builder(null, paused ? I18n.t("继续") : I18n.t("暂停"), action(PAUSE, 2)).build())
                .addAction(new Notification.Action.Builder(null, I18n.t("停止"), action(STOP, 3)).build()).build();
    }
    private PendingIntent action(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, MonitorService.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private void resize() {
        int width;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowMetrics wm = windows.getCurrentWindowMetrics();
            android.graphics.Insets insets = wm.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            width = wm.getBounds().width() - insets.left - insets.right;
        } else {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            windows.getDefaultDisplay().getMetrics(dm); width = dm.widthPixels;
        }
        availableWidth = Math.max(1, width);
        int oldWidth = params.width, oldY = params.y;
        float oldAlpha = params.alpha;
        overlay.maxWidth(availableWidth);
        overlay.update(latest == null ? new Snapshot() : latest);
        // Root WRAP_CONTENT can use Android's preferred dialog width before trying
        // the screen width. Measure against the full safe area, then size the window
        // explicitly so an otherwise fitting row is never wrapped by that heuristic.
        overlay.measure(View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        params.width = overlay.getMeasuredWidth();
        params.y = Math.round(prefs.offset() * overlay.getResources().getDisplayMetrics().density);
        params.alpha = OverlayView.safeWindowAlpha(this);
        if (attached && (oldWidth != params.width || oldY != params.y || oldAlpha != params.alpha)) {
            try { windows.updateViewLayout(overlay, params); }
            catch (RuntimeException e) { removeOverlay(); }
        }
    }
    private void showIfNeeded() {
        if (attached || hidden || paused || suspended || destroyed) return;
        try { resize(); windows.addView(overlay, params); attached = true; }
        catch (RuntimeException e) { message = "浮窗创建失败，请检查权限"; stopSelf(); }
    }
    private void removeOverlay() {
        if (attached) { try { windows.removeViewImmediate(overlay); } catch (RuntimeException ignored) { } }
        attached = false;
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config); main.removeCallbacks(reflow); main.post(reflow);
    }
    @Override public void onSharedPreferenceChanged(android.content.SharedPreferences p, String key) {
        if ("auto_record".equals(key) && running) {
            if (prefs.autoRecord()) history.begin(); else history.end();
            main.postDelayed(this::updateRecordingNotice, 500);
        }
        if ("shizuku".equals(key)) { bridge.close(); if (prefs.shizuku()) bridge.connect(); }
        resize();
        main.removeCallbacks(sampleTick);
        if (!paused && !suspended) main.post(sampleTick);
    }
    private void updateTile() { TileService.requestListeningState(this, new ComponentName(this, MonitorTile.class)); }
    private void updateRecordingNotice() {
        if (!destroyed && running && !recordingNotice.equals(history.active)) {
            recordingNotice = history.active;
            getSystemService(NotificationManager.class).notify(10, notification());
        }
    }
    @Override public void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null); removeOverlay(); worker.shutdownNow();
        history.end();
        bridge.close(); prefs.store.unregisterOnSharedPreferenceChangeListener(this);
        windowContext.unregisterComponentCallbacks(windowConfiguration);
        try { unregisterReceiver(screen); unregisterReceiver(languageChange); } catch (RuntimeException ignored) { }
        running = false; hidden = false; paused = false; latest = null; message = "已停止";
        stopForeground(STOP_FOREGROUND_REMOVE); updateTile(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        writer.println("MurasameRTSS 1.2.1 running=" + running + " hidden=" + hidden + " paused=" + paused);
        writer.println("font=" + prefs.font() + " backgroundOpacity=" + prefs.opacity() + " windowAlpha=" + params.alpha);
        int[] location = new int[2]; overlay.getLocationOnScreen(location);
        writer.println("overlay attached=" + attached + " x=" + location[0] + " y=" + location[1]
                + " width=" + overlay.getWidth() + " height=" + overlay.getHeight());
        writer.println("layout availableWidth=" + availableWidth + " requestedWidth=" + params.width + " rows=" + overlay.rowCount());
        writer.println("history active=" + history.active + " status=" + history.status);
        writer.println("autoRecord=" + prefs.autoRecord());
        Snapshot s = latest;
        if (s != null) {
            writer.println(s.backend);
            for (Metric m : Metric.values()) writer.println(m.name() + "=" + s.values.get(m) + " | " + s.reasons.get(m));
            writer.println(s.hardware.toString());
        }
    }
}
