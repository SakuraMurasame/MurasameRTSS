package io.perfoverlay.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** Interval-average stacked power: foreground opaque, background translucent, unknown hatched. */
public final class AppBatteryChart extends View {
    private final UiTheme theme;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<AppBatteryData.Interval> intervals = Collections.emptyList();
    private int uid;
    private long from, to, origin;
    private double high = 1;
    private boolean watts;
    private AppBatteryData.Interval selected;
    private Consumer<AppBatteryData.Interval> listener;
    private float downX, downY;
    public AppBatteryChart(Context context) { super(context); theme = UiTheme.get(context); setContentDescription(I18n.t("点选区间查看应用功耗")); }
    public void listener(Consumer<AppBatteryData.Interval> listener) { this.listener = listener; }
    public void restore(long elapsed) {
        for (AppBatteryData.Interval i:intervals) if (included(i) && i.to.elapsed==elapsed) {
            selected=i; invalidate(); if (listener!=null) listener.accept(i); return;
        }
    }
    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
    public void bind(List<AppBatteryData.Interval> data, int uid, long from, long to, long origin, boolean watts) {
        this.intervals = data; this.uid = uid; this.from = from; this.to = Math.max(from+1,to); this.origin = origin; this.watts = watts;
        high = 0;
        for (AppBatteryData.Interval i : data) if (included(i)) {
            double n = value(i, i.apps.get(uid).total); if (Double.isFinite(n)) high = Math.max(high,n);
        }
        high = high <= 0 ? (watts ? .1 : 1) : high * 1.12;
        selected = null; invalidate();
    }
    private boolean included(AppBatteryData.Interval i) { return i.valid && i.from.elapsed >= from && i.to.elapsed <= to && i.apps.containsKey(uid); }
    private double value(AppBatteryData.Interval i, double n) { return watts ? i.watts(n) : n; }
    private float x(long time) { return dp(44) + (float)((time-from)/(double)(to-from)) * (getWidth()-dp(54)); }
    private float y(double value) { return getHeight()-dp(46) - (float)(value/high)*(getHeight()-dp(62)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(theme.card); pen.setStyle(Paint.Style.FILL); pen.setAlpha(255);
        pen.setColor(theme.grid); pen.setStrokeWidth(dp(.5f));
        for (float gx=dp(44); gx<=getWidth()-dp(10); gx+=dp(14)) canvas.drawLine(gx,dp(16),gx,getHeight()-dp(46),pen);
        for (float gy=dp(16); gy<=getHeight()-dp(46); gy+=dp(14)) canvas.drawLine(dp(44),gy,getWidth()-dp(10),gy,pen);
        pen.setTextSize(dp(10)); pen.setColor(theme.muted);
        canvas.drawText(MetricMath.fixed(high,2),0,dp(22),pen); canvas.drawText("0",0,getHeight()-dp(46),pen);
        canvas.drawText(HistoryMath.time(Math.max(0,from-origin)),dp(44),getHeight()-dp(6),pen);
        String end=HistoryMath.time(Math.max(0,to-origin)); canvas.drawText(end,getWidth()-dp(10)-pen.measureText(end),getHeight()-dp(6),pen);
        drawClockAxis(canvas);
        boolean any = false;
        int save=canvas.save(); canvas.clipRect(dp(44),dp(16),getWidth()-dp(10),getHeight()-dp(46));
        for (AppBatteryData.Interval interval : intervals) {
            if (!included(interval)) continue;
            AppBatteryData.Drain d=interval.apps.get(uid); double total=value(interval,d.total);
            if (!Double.isFinite(total)) continue; any=true;
            double fg=value(interval,d.foreground), bg=value(interval,d.background), unknown=value(interval,d.unknown);
            float left=x(interval.from.elapsed), right=Math.max(left+dp(.6f),x(interval.to.elapsed)-dp(.5f));
            pen.setColor(theme.accent); pen.setAlpha(255); canvas.drawRect(left,y(fg),right,y(0),pen);
            pen.setAlpha(96); canvas.drawRect(left,y(fg+bg),right,y(fg),pen);
            if (unknown>0) {
                pen.setColor(theme.muted); pen.setAlpha(32); canvas.drawRect(left,y(total),right,y(fg+bg),pen);
                int clip=canvas.save(); canvas.clipRect(left,y(total),right,y(fg+bg)); pen.setAlpha(170); pen.setStrokeWidth(dp(.8f));
                float height=y(fg+bg)-y(total);
                for (float stripe=left-height; stripe<=right; stripe+=dp(7)) canvas.drawLine(stripe,y(fg+bg),stripe+height,y(total),pen);
                canvas.restoreToCount(clip);
            }
        }
        pen.setAlpha(255);
        if (selected!=null) {
            pen.setColor(theme.violet); pen.setStyle(Paint.Style.STROKE); pen.setStrokeWidth(dp(1.5f));
            canvas.drawRect(x(selected.from.elapsed),dp(16),x(selected.to.elapsed),getHeight()-dp(46),pen); pen.setStyle(Paint.Style.FILL);
        }
        canvas.restoreToCount(save);
        if (!any) { pen.setColor(theme.muted); String empty=I18n.t("暂无有效读数"); canvas.drawText(empty,dp(50),getHeight()/2f,pen); }
    }
    private void drawClockAxis(Canvas canvas) {
        AppBatteryData.Sample first=null,last=null;
        for(AppBatteryData.Interval interval:intervals) if(included(interval)) {
            if(first==null)first=interval.from;
            last=interval.to;
        }
        if(first==null)return;
        boolean dated=HistoryMath.differentDate(first.wall,last.wall);
        String start=HistoryMath.clock(first.wall,dated), end=HistoryMath.clock(last.wall,dated);
        pen.setTextSize(dp(9));
        float left=dp(44), right=getWidth()-dp(10);
        float a=Math.max(left,Math.min(right-pen.measureText(start),x(first.elapsed)-pen.measureText(start)/2));
        float b=Math.max(left,Math.min(right-pen.measureText(end),x(last.elapsed)-pen.measureText(end)/2));
        pen.setColor(theme.muted); pen.setTextSize(dp(9));
        canvas.drawText(start,a,getHeight()-dp(26),pen);
        if(b>a+pen.measureText(start)+dp(6))canvas.drawText(end,b,getHeight()-dp(26),pen);
    }
    private void select(float px) {
        long time=from+(long)((px-dp(44))/(getWidth()-dp(54))*(to-from)); AppBatteryData.Interval nearest=null; long distance=Long.MAX_VALUE;
        for (AppBatteryData.Interval i:intervals) if (included(i)) {
            long center=i.from.elapsed+(i.to.elapsed-i.from.elapsed)/2; long d=Math.abs(center-time);
            if (d<distance) { distance=d; nearest=i; }
        }
        selected=nearest; invalidate(); if (nearest!=null && listener!=null) listener.accept(nearest);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX=event.getX(); downY=event.getY(); select(downX); return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX()-downX)>Math.abs(event.getY()-downY)) { getParent().requestDisallowInterceptTouchEvent(true); select(event.getX()); }
                return true;
            case MotionEvent.ACTION_UP: getParent().requestDisallowInterceptTouchEvent(false); performClick(); select(event.getX()); return true;
            case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
