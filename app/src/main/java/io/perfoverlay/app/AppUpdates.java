package io.perfoverlay.app;

import android.content.*;
import android.os.*;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Public GitHub metadata only. No device measurements, tokens, or automatic APK downloads. */
public final class AppUpdates {
    public interface Listener { void changed(); }
    public static final class Release {
        public final String tag,notes,apk;
        Release(String tag,String notes,String apk) { this.tag=tag; this.notes=notes; this.apk=apk; }
    }
    private static AppUpdates instance;
    public static synchronized AppUpdates get(Context c) {
        if(instance==null)instance=new AppUpdates(c.getApplicationContext()); return instance;
    }
    public final SharedPreferences prefs;
    public final String installed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    public boolean checking;
    public Release release;
    public String status="尚未检查更新";
    private AppUpdates(Context context) {
        prefs=context.getSharedPreferences("updates",Context.MODE_PRIVATE);
        String local;
        try { local=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName; }
        catch(Exception e) { local="?"; }
        installed=local;
        try {
            String cached=prefs.getString("release_json","");
            if(!cached.isEmpty()) { release=parse(cached); status=resultStatus(release); }
        } catch(Exception ignored) { release=null; }
    }
    public boolean automatic() { return prefs.getBoolean("automatic",true); }
    public void automatic(boolean enabled) { prefs.edit().putBoolean("automatic",enabled).apply(); if(enabled)check(false); }
    public void listen(Listener listener) { listeners.add(listener); listener.changed(); }
    public void unlisten(Listener listener) { listeners.remove(listener); }
    private void notifyChanged() { for(Listener listener:new ArrayList<>(listeners))listener.changed(); }
    public boolean available() { try { return release!=null && UpdatePolicy.newer(release.tag,installed); } catch(Exception e) { return false; } }
    private String resultStatus(Release r) {
        if(r==null)return "GitHub 暂无正式发布版本";
        return UpdatePolicy.newer(r.tag,installed)?"发现新版本":"当前版本无需更新";
    }
    static Release parse(String raw) throws Exception {
        JSONObject json=new JSONObject(raw);
        if(json.getBoolean("draft") || json.getBoolean("prerelease"))return null;
        String tag=json.getString("tag_name"); UpdatePolicy.version(tag);
        String notes=json.optString("body",""); if(notes.length()>12000)notes=notes.substring(0,12000)+"…";
        String apk=""; JSONArray assets=json.optJSONArray("assets");
        if(assets!=null)for(int i=0;i<assets.length();i++) {
            JSONObject a=assets.optJSONObject(i); if(a==null || !"uploaded".equals(a.optString("state")) || a.optLong("size",0)<=0)continue;
            if(UpdatePolicy.apk(tag,a.optString("name"),a.optString("browser_download_url"))) { apk=a.getString("browser_download_url"); break; }
        }
        return new Release(tag,notes,apk);
    }
    public void check(boolean manual) {
        if(checking)return;
        long now=System.currentTimeMillis();
        if(!manual && (!automatic() || !UpdatePolicy.due(now,prefs.getLong("last_attempt",0),prefs.getBoolean("last_success",false))))return;
        if(now>=prefs.getLong("last_attempt",0) && now<prefs.getLong("retry_at",0) && prefs.getLong("retry_at",0)-now<=86400000L) { status="GitHub 请求受限，请稍后重试"; notifyChanged(); return; }
        checking=true; status="正在检查更新…";
        prefs.edit().putLong("last_attempt",now).putBoolean("last_success",false).apply(); notifyChanged();
        worker.execute(()->{
            String response=null,message="无法连接 GitHub，请稍后重试"; Release found=null; boolean success=false; long retry=0;
            HttpsURLConnection connection=null;
            try {
                connection=(HttpsURLConnection)new URL(UpdatePolicy.API).openConnection();
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept","application/vnd.github+json");
                connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
                connection.setRequestProperty("User-Agent","MurasameRTSS/"+installed); connection.setUseCaches(false);
                int code=connection.getResponseCode();
                if(code==200) {
                    try(InputStream in=connection.getInputStream(); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                        byte[] block=new byte[8192]; int count; long started=SystemClock.elapsedRealtime();
                        while((count=in.read(block))!=-1) { if(SystemClock.elapsedRealtime()-started>20000 || bytes.size()+count>262144)throw new IOException("Response too large"); bytes.write(block,0,count); }
                        response=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
                    }
                    try { found=parse(response); message=resultStatus(found); success=true; }
                    catch(Exception e) { response=null; message="更新信息格式不受支持，请查看发布页"; }
                } else if(code==404) { message="GitHub 暂无正式发布版本"; success=true; }
                else if(code==403 || code==429) {
                    message="GitHub 请求受限，请稍后重试";
                    retry=UpdatePolicy.retryAt(System.currentTimeMillis(),connection.getHeaderField("Retry-After"),connection.getHeaderField("X-RateLimit-Reset"));
                }
            } catch(Exception ignored) { }
            finally { if(connection!=null)connection.disconnect(); }
            final String raw=response, result=message; final Release next=found; final boolean ok=success; final long retryTime=retry;
            main.post(()->{
                checking=false; status=result;
                if(ok)release=next;
                SharedPreferences.Editor edit=prefs.edit().putBoolean("last_success",ok).putLong("retry_at",retryTime);
                if(ok) { if(raw==null)edit.remove("release_json"); else edit.putString("release_json",raw); }
                edit.apply(); notifyChanged();
            });
        });
    }
}
