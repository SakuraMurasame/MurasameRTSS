package io.perfoverlay.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class MonitorTile extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile(); if (tile == null) return;
        tile.setLabel(I18n.t("性能浮窗"));
        tile.setState(MonitorService.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(MonitorService.running ? I18n.t("点击停止") : I18n.t("点击打开并开始")); tile.updateTile();
    }
    @Override public void onClick() {
        if (MonitorService.running) { stopService(new Intent(this, MonitorService.class)); onStartListening(); return; }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("startFromTile", true);
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 12, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        else startActivityAndCollapse(open);
    }
}
