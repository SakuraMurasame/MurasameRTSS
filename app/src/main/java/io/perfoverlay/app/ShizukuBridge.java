package io.perfoverlay.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import rikka.shizuku.Shizuku;

public final class ShizukuBridge implements AutoCloseable {
    private final Shizuku.UserServiceArgs args;
    private volatile IHardwareService remote;
    private boolean bound;
    private long bindingStarted;
    private volatile String state = "尚未连接";
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!bound) return;
            remote = IHardwareService.Stub.asInterface(binder); state = "Shizuku 已连接";
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            remote = null; state = "Shizuku 连接已断开"; bound = false;
        }
    };
    public ShizukuBridge(Context context) {
        this(context, false);
    }
    public ShizukuBridge(Context context, boolean battery) {
        args = new Shizuku.UserServiceArgs(new ComponentName(context, HardwareUserService.class))
                .tag(battery ? "perfoverlay.battery" : "perfoverlay.hardware")
                .processNameSuffix(battery ? "battery" : "hardware").daemon(false).version(4);
    }
    public static boolean available() {
        try { return Shizuku.pingBinder() && !Shizuku.isPreV11(); }
        catch (Exception e) { return false; }
    }
    public static boolean authorized() {
        try { return available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Exception e) { return false; }
    }
    public void connect() {
        if (!authorized()) { if (bound) close(); state = available() ? "Shizuku 待授权" : "请先启动 Shizuku"; return; }
        if (bound && remote == null && android.os.SystemClock.elapsedRealtime() - bindingStarted > 10000) close();
        if (bound) return;
        try {
            bound = true; bindingStarted = android.os.SystemClock.elapsedRealtime(); state = "Shizuku 正在连接";
            Shizuku.bindUserService(args, connection);
        } catch (Exception e) { bound = false; state = "Shizuku 连接失败"; }
    }
    public String sample(int mask) {
        IHardwareService service = remote;
        if (service == null || !authorized()) return null;
        try { return service.sample(mask); }
        catch (Exception e) { remote = null; state = "Shizuku 读取中断"; return null; }
    }
    public String status() { return state; }
    public String battery() {
        IHardwareService service = remote;
        if (service == null || !authorized()) return null;
        try { return service.battery(); }
        catch (Exception e) { remote = null; state = "Shizuku 读取中断"; return null; }
    }
    @Override public void close() {
        remote = null;
        if (bound) {
            try { Shizuku.unbindUserService(args, connection, true); }
            catch (Exception ignored) { }
        }
        bound = false; state = "尚未连接";
    }
}
