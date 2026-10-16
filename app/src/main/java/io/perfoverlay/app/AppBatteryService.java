package io.perfoverlay.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** User-started battery attribution recording, independent of overlay and screen-on state. */
public final class AppBatteryService extends Service {
    public static final String START = "battery_start", STOP = "battery_stop";
    public static final String START_LINKED = "battery_start_linked", STOP_LINKED = "battery_stop_linked", SESSION = "monitor_session";
    private final BatteryLink link = new BatteryLink();
    private boolean recordingStarted;
    public static volatile boolean running;
    public static volatile String status = "已停止";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AppBatteryRecords records;
    private ShizukuBridge bridge;
    private volatile boolean destroyed;
    private boolean hasBaseline, gapped;
    private long interval;
    private final BroadcastReceiver language = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { notifyState(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            bridge.connect(); String id = records.active;
            worker.execute(() -> {
                AppBatteryData.Sample sample = null; String error = "";
                try {
                    String response = bridge.battery();
                    if (response == null) error = bridge.status();
                    else {
                        JSONObject json = new JSONObject(response);
                        if (json.has("error")) error = json.getString("error");
                        else {
                            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                            double volts = battery == null ? Double.NaN : battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000d;
                            Map<Integer,AppBatteryData.Usage> apps = new TreeMap<>(); JSONArray rows = json.getJSONArray("apps");
                            for (int i = 0; i < rows.length(); i++) {
                                JSONObject row = rows.getJSONObject(i); List<String> names = new ArrayList<>(); JSONArray packages = row.getJSONArray("packages");
                                for (int j = 0; j < packages.length(); j++) names.add(packages.getString(j));
                                apps.put(row.getInt("uid"), new AppBatteryData.Usage(row.getDouble("total"), row.optDouble("fg", Double.NaN), row.optDouble("bg", Double.NaN), names));
                            }
                            sample = new AppBatteryData.Sample(json.getLong("wall"), json.getLong("elapsed"), json.getString("epoch"),
                                    json.getDouble("capacity"), json.getDouble("computed"), volts, true, apps);
                        }
                    }
                } catch (Exception e) { error = "应用耗电读取失败"; }
                AppBatteryData.Sample completed = sample; String failure = error;
                main.post(() -> {
                    if (destroyed) return;
                    if (completed != null) {
                        records.append(id, completed, () -> main.post(() -> { status = records.status; stopSelf(); }));
                        status = hasBaseline && !gapped ? "正在记录应用耗电" : "基线已建立，等待下一次采样";
                        hasBaseline = true; gapped = false;
                    } else {
                        status = failure;
                        if (hasBaseline && !gapped) records.append(id, AppBatteryData.Sample.gap(System.currentTimeMillis(), SystemClock.elapsedRealtime()), () -> main.post(AppBatteryService.this::stopSelf));
                        gapped = true;
                    }
                    records.status = status; notifyState();
                    main.postDelayed(tick, completed == null && !hasBaseline ? 5000 : interval);
                });
            });
        }
    };
    @Override public void onCreate() {
        super.onCreate(); records = AppBatteryRecords.get(this); bridge = new ShizukuBridge(this, true);
        interval = Math.max(60, Math.min(300, getSharedPreferences("overlay", MODE_PRIVATE).getInt("battery_interval", 60))) * 1000L;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("app_battery", I18n.t("应用耗电"), NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(language, new IntentFilter(MurasameApp.LANGUAGE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(language, new IntentFilter(MurasameApp.LANGUAGE_CHANGED));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction(), token = intent.getStringExtra(SESSION);
        if (STOP_LINKED.equals(action)) {
            if (link.shouldStop(token) || !running) stopSelf();
            return START_NOT_STICKY;
        }
        if (START_LINKED.equals(action)) {
            if (!link.acceptStart(token, MonitorService.sessionToken, MonitorService.running, running)) {
                if (!running) stopSelf();
                return START_NOT_STICKY;
            }
        } else if (START.equals(action)) {
            if (!running) link.independent();
        } else { if (!running) stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        if (!ShizukuBridge.authorized()) { status = "请先启动并授权 Shizuku"; stopSelf(); return START_NOT_STICKY; }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(11, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(11, notification());
        } catch (RuntimeException e) { status = "系统拒绝启动前台服务"; stopSelf(); return START_NOT_STICKY; }
        running = true; recordingStarted = true; status = "等待首个耗电基线";
        records.begin(() -> main.post(() -> { if (destroyed) return; if (records.active.isEmpty()) { status = records.status; stopSelf(); } else main.post(tick); }));
        return START_NOT_STICKY;
    }
    private Notification notification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 11, new Intent(this, AppBatteryActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 12, new Intent(this, AppBatteryService.class).setAction(STOP), flags);
        return new Notification.Builder(this, "app_battery").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("MurasameRTSS · " + I18n.t("应用耗电")).setContentText(I18n.raw(status))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, I18n.t("停止监控"), stop).build()).build();
    }
    private void notifyState() { if (!destroyed && running) getSystemService(NotificationManager.class).notify(11, notification()); }
    @Override public void onDestroy() {
        destroyed = true; running = false; main.removeCallbacksAndMessages(null);
        if (records != null && recordingStarted) {
            records.end();
            records.status = hasBaseline && (status.equals("正在记录应用耗电") || status.equals("基线已建立，等待下一次采样")) ? "记录已保存" : status;
        }
        // Close IPC off the UI thread; the overlay uses a different Shizuku service tag.
        worker.execute(() -> bridge.close()); worker.shutdown();
        unregisterReceiver(language); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter out, String[] args) {
        out.println("AppBattery running=" + running + " interval=" + interval + " status=" + status);
        out.println("linkedMonitor=" + link.owner());
        out.println("active=" + records.active + " samples=" + records.samples + " baseline=" + hasBaseline);
    }
}
