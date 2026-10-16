package io.perfoverlay.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;

/** Activity-owned presentation; the shared checker never retains an inactive Activity. */
public final class UpdateUi implements AppUpdates.Listener {
    private final Activity activity;
    private final AppUpdates updates;
    private TextView status;
    private Button check,details;
    private boolean active,manual;
    private AlertDialog dialog;
    public UpdateUi(Activity activity) { this.activity=activity; updates=AppUpdates.get(activity); }
    public void resume() { active=true; updates.listen(this); updates.check(false); }
    public void pause() {
        active=false; manual=false; updates.unlisten(this);
        if(dialog!=null) { dialog.dismiss(); dialog=null; }
    }
    private int dp(int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
    private Button button(LinearLayout parent,UiTheme theme,String title,Runnable action) {
        Button button=new Button(activity); button.setText(I18n.t(title)); button.setAllCaps(false); button.setTextSize(13);
        button.setTextColor(theme.accent); button.setMinHeight(dp(48)); button.setPadding(dp(10),dp(8),dp(10),dp(8));
        button.setBackground(UiMotion.surface(activity,theme,theme.surface,12,false)); button.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(8); parent.addView(button,lp); return button;
    }
    public void populate(LinearLayout card,UiTheme theme) {
        Switch auto=new Switch(activity); auto.setText(I18n.t("自动检查更新")); auto.setTextColor(theme.ink); auto.setTextSize(14);
        auto.setMinHeight(dp(48)); auto.setChecked(updates.automatic()); auto.setOnCheckedChangeListener((v,checked)->updates.automatic(checked)); card.addView(auto);
        TextView hint=new TextView(activity); hint.setText(I18n.t("打开主界面时检查，每 24 小时一次；下载前由你确认。")); hint.setTextColor(theme.muted); hint.setTextSize(12); card.addView(hint);
        status=new TextView(activity); status.setTextColor(theme.ink); status.setTextSize(13); status.setPadding(0,dp(10),0,dp(4)); card.addView(status);
        check=button(card,theme,"立即检查更新",()->{ manual=true; updates.check(true); });
        details=button(card,theme,"查看新版详情",this::showRelease);
        button(card,theme,"GitHub 发布页面",()->open(UpdatePolicy.REPO+"/releases")); changed();
    }
    @Override public void changed() {
        if(status!=null)status.setText(I18n.t(updates.status)+(updates.available()?" · "+updates.release.tag:""));
        if(check!=null) { check.setEnabled(!updates.checking); check.setAlpha(updates.checking?.4f:1f); }
        if(details!=null)details.setVisibility(updates.available()?android.view.View.VISIBLE:android.view.View.GONE);
        if(!active || updates.checking || activity.isFinishing() || activity.isDestroyed())return;
        boolean requested=manual; manual=false;
        if(updates.available() && "发现新版本".equals(updates.status) && (requested || (updates.automatic()
                && !updates.release.tag.equals(updates.prefs.getString("notified_tag","")))))showRelease();
        else if(requested)Toast.makeText(activity,I18n.t(updates.status),Toast.LENGTH_LONG).show();
    }
    private void showRelease() {
        AppUpdates.Release release=updates.release;
        if(release==null || !active || activity.isFinishing() || activity.isDestroyed() || (dialog!=null && dialog.isShowing()))return;
        String message=I18n.t("当前版本")+" "+updates.installed+"\n"+I18n.t("最新版本")+" "+release.tag+"\n\n"
            +(release.notes.isEmpty()?I18n.t("此版本未填写更新说明"):release.notes);
        if(release.apk.isEmpty())message+="\n\n"+I18n.t("此版本暂无可下载 APK，请查看发布页面。");
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(I18n.t("发现新版本")).setMessage(message)
            .setNegativeButton(I18n.t("稍后"),null).setNeutralButton(I18n.t("发布页面"),(d,w)->open(UpdatePolicy.page(release.tag)));
        if(!release.apk.isEmpty())builder.setPositiveButton(I18n.t("下载 APK"),(d,w)->open(release.apk));
        dialog=builder.create(); dialog.setOnDismissListener(d->dialog=null); dialog.show();
        updates.prefs.edit().putString("notified_tag",release.tag).apply();
    }
    private void open(String url) {
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch(ActivityNotFoundException e) { Toast.makeText(activity,I18n.t("未找到可以打开链接的浏览器"),Toast.LENGTH_LONG).show(); }
    }
}
