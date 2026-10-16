package io.perfoverlay.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class AppBatteryActivity extends Activity {
    private UiTheme theme;
    private UiNav.Shell nav;
    private AppBatteryRecords records;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LinearLayout content, rows, graphBox;
    private TextView state, total, period, graphCaption;
    private Button start, session, backList;
    private EditText from, to;
    private String id="", restoredFrom, restoredTo, language;
    private int uid=-1, generation, shown=50;
    private long origin, lastModified=-1;
    private boolean loading, follow=true, watts=true;
    private List<AppBatteryData.Sample> samples=Collections.emptyList();
    private List<AppBatteryData.Interval> intervals=Collections.emptyList();
    private AppBatteryChart chart;
    private FrameLayout sceneHost;
    private ScrollView listScroll, detailScroll;
    private long selectedEnd=-1;
    private int savedListOffset, savedDetailOffset;
    private boolean restoreOffsets;
    private final Map<Integer,Long> cursors=new HashMap<>();
    private final Map<String,String> labels=new HashMap<>();
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            state.setText(I18n.t(AppBatteryService.running?"正在记录应用耗电":"已停止")+" · "+I18n.raw(AppBatteryService.running?AppBatteryService.status:records.status));
            start.setText(I18n.t(AppBatteryService.running?"停止并保存耗电记录":"开始耗电监控"));
            if (!from.hasFocus() && !to.hasFocus()) load(false);
            ui.postDelayed(this,5000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        UiTheme.beforeCreate(this); super.onCreate(saved); records=AppBatteryRecords.get(this);
        if (saved!=null) {
            id=saved.getString("id",""); uid=saved.getInt("uid",-1); follow=saved.getBoolean("follow",true); watts=saved.getBoolean("watts",true);
            restoredFrom=saved.getString("from"); restoredTo=saved.getString("to");
            selectedEnd=saved.getLong("selectedEnd",-1);
            savedListOffset=saved.getInt("listOffset",0); savedDetailOffset=saved.getInt("detailOffset",0); restoreOffsets=true;
        }
        build();
        UiMotion.backHandler(this,this::backAction);
    }
    @Override protected void onResume() {
        super.onResume(); if (theme.dark!=UiTheme.get(this).dark || !language.equals(((MurasameApp)getApplication()).selection())) { recreate(); return; }
        ui.removeCallbacks(refresh); ui.post(refresh);
    }
    @Override protected void onPause() { ui.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { generation++; ui.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle b) {
        b.putString("id",id); b.putInt("uid",uid); b.putBoolean("follow",follow); b.putBoolean("watts",watts);
        b.putString("from",from.getText().toString()); b.putString("to",to.getText().toString()); super.onSaveInstanceState(b);
        b.putLong("selectedEnd",selectedEnd);
        b.putInt("listOffset",listScroll.getHeight()>0?listScroll.getScrollY():savedListOffset);
        b.putInt("detailOffset",detailScroll.getHeight()>0?detailScroll.getScrollY():savedDetailOffset);
    }
    @Override public void onBackPressed() { backAction(); }
    private void backAction() { if(nav!=null && nav.collapse())return; if (uid>=0) closeDetails(); else UiMotion.back(this); }
    private void closeDetails() {
        int offset=listScroll.getHeight()>0?listScroll.getScrollY():savedListOffset; cursors.put(uid,selectedEnd); uid=-1; draw();
        UiMotion.swap(sceneHost,listScroll,-1,true); UiMotion.restoreScroll(listScroll,offset);
    }
    private void openDetails(int selected) {
        from.clearFocus(); to.clearFocus();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(from.getWindowToken(),0);
        uid=selected; selectedEnd=cursors.getOrDefault(selected,-1L); draw();
        UiMotion.swap(sceneHost,detailScroll,1,true);
        UiMotion.restoreScroll(detailScroll,0);
    }
    private void restoreOffsets() {
        if (!restoreOffsets) return; restoreOffsets=false;
        UiMotion.restoreScroll(listScroll,savedListOffset); UiMotion.restoreScroll(detailScroll,savedDetailOffset);
    }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); return c; }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0,dp(5),0,dp(5)); return t;
    }
    private LinearLayout card(String title) {
        LinearLayout c=column(); c.setPadding(dp(16),dp(12),dp(16),dp(12)); c.setBackground(theme.shape(this,theme.card,16,true));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(12); content.addView(c,p);
        if (!title.isEmpty()) { TextView t=text(title,18,theme.ink); t.setTypeface(null,Typeface.BOLD); c.addView(t); } return c;
    }
    private Button button(LinearLayout parent,String label,Runnable action,boolean primary) {
        Button b=new Button(this); b.setText(label); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(primary?theme.primaryInk:theme.accent);
        b.setBackground(UiMotion.surface(this,theme,primary?theme.accent:theme.surface,12,false)); b.setPadding(dp(12),dp(8),dp(12),dp(8)); b.setMinWidth(0); b.setMinHeight(dp(44));
        b.setOnClickListener(v->action.run()); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(6); p.bottomMargin=dp(6); parent.addView(b,p); return b;
    }
    private void build() {
        theme=UiTheme.get(this); theme.window(this); language=((MurasameApp)getApplication()).selection();
        LinearLayout root=column(); root.setBackgroundColor(theme.bg);
        root.addView(UiNav.header(this,theme,I18n.t("应用耗电"),()->nav.toggle(),this::backAction));
        sceneHost=new UiMotion.Host(this); root.addView(sceneHost,new LinearLayout.LayoutParams(-1,0,1));
        listScroll=new ScrollView(this); content=column(); content.setPadding(dp(16),dp(6),dp(16),dp(20)); listScroll.addView(content);
        detailScroll=new ScrollView(this); LinearLayout detailBody=column(); detailBody.setPadding(dp(16),dp(6),dp(16),dp(20)); detailScroll.addView(detailBody);
        UiMotion.swap(sceneHost,uid>=0?detailScroll:listScroll,1,false); nav=UiNav.create(this,theme,root,5,index->{
            if(index==5)return;
            Intent target=index==3?new Intent(this,HistoryActivity.class):new Intent(this,MainActivity.class).putExtra("page",index);
            target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            UiMotion.open(this,target,UiNav.direction(5,index)); finish();
        }); setContentView(nav);
        LinearLayout control=card(""); state=text("",12,theme.muted); control.addView(state);
        start=button(control,I18n.t("开始耗电监控"),this::toggle,true);
        button(control,I18n.t("采样间隔")+" · "+getSharedPreferences("overlay",MODE_PRIVATE).getInt("battery_interval",60)+" s",this::chooseInterval,false);
        TextView help=text(I18n.t("耗电说明")+"  ⓘ",12,theme.accent); help.setMinHeight(dp(44)); help.setGravity(Gravity.CENTER_VERTICAL);
        help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(I18n.t("耗电说明")).setMessage(I18n.t("应用耗电来自 Android 系统归因估算，mAh 不是逐程序硬件实测。百分比是所选区间内各程序占已统计耗电的比例，不是电池电量下降百分比。")
            +"\n\n"+I18n.t("每隔至少 60 秒尝试采样，熄屏后继续监控，深度休眠时延后采样。功耗为区间平均值；系统统计可能延迟，微小变化可能因精度显示为零。")
            +"\n\n"+I18n.t("前台服务和缓存进程计入后台；系统未归类的耗电用斜纹表示。共享 UID 的程序合并统计，不能单独拆分。")
            +"\n\n"+I18n.t("断线、系统统计重置和程序身份变化处不推算耗电。首次采样仅建立基线，下一次采样后才有区间数据。"))
            .setNegativeButton(I18n.t("取消"),null).show()); control.addView(help);
        LinearLayout range=card(I18n.t("时间范围")); session=button(range,I18n.t("选择耗电记录"),this::chooseSession,false);
        period=text("",12,theme.muted); range.addView(period);
        LinearLayout fields=new LinearLayout(this); from=field(I18n.t("开始时间"),restoredFrom==null?"00:00:00":restoredFrom); to=field(I18n.t("结束时间"),restoredTo==null?"":restoredTo);
        LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1); cell.rightMargin=dp(8); fields.addView(from,cell); fields.addView(to,new LinearLayout.LayoutParams(0,-2,1)); range.addView(fields);
        button(range,I18n.t("查看此时间段"),()->{ follow=false; from.clearFocus(); to.clearFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(from.getWindowToken(),0); draw(); },false);
        button(range,I18n.t("显示全部 / 刷新数据"),()->{ follow=true; from.clearFocus(); to.clearFocus(); from.setText("00:00:00"); to.setText(""); load(true); },false);
        range.addView(text(I18n.t("按完整采样区间统计；边界区间不拆分。"),11,theme.muted));
        LinearLayout overview=card(I18n.t("耗电占比与消耗")); total=text("",14,theme.ink); overview.addView(total);
        overview.addView(text(I18n.t("点击程序查看对应时间段的功耗。"),12,theme.muted));
        rows=column(); overview.addView(rows);
        graphBox=column(); graphBox.setPadding(dp(16),dp(12),dp(16),dp(12)); graphBox.setBackground(theme.shape(this,theme.card,16,true)); detailBody.addView(graphBox);
        button(content,I18n.t("删除整次耗电记录"),this::deleteRecord,false); load(true);
    }
    private EditText field(String hint,String value) {
        EditText e=new EditText(this); e.setTextColor(theme.ink); e.setHintTextColor(theme.muted); e.setTextSize(13);
        e.setHint(hint+" 00:00:00"); e.setText(value); e.setSingleLine(); e.setSelectAllOnFocus(true); e.setContentDescription(hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_TIME); return e;
    }
    private void toggle() {
        if (AppBatteryService.running) { stopService(new Intent(this,AppBatteryService.class)); ui.postDelayed(()->{ load(true); state.setText(I18n.t("记录已保存")); },500); return; }
        if (!ShizukuBridge.authorized()) {
            new AlertDialog.Builder(this).setMessage(I18n.t("请先启动并授权 Shizuku")).setPositiveButton(I18n.t("连接 / 授权 Shizuku"),(d,w)->{
                startActivity(new Intent(this,MainActivity.class).putExtra("page",0).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
            }).setNegativeButton(I18n.t("取消"),null).show(); return;
        }
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42); return;
        }
        generation++; loading=false; id=""; uid=-1; cursors.clear(); selectedEnd=-1; follow=true; from.setText("00:00:00"); to.setText(""); lastModified=-1;
        startForegroundService(new Intent(this,AppBatteryService.class).setAction(AppBatteryService.START)); ui.postDelayed(()->load(true),1500);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request==42 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) toggle();
    }
    private void chooseInterval() {
        if (AppBatteryService.running) { toast(I18n.t("请先停止耗电监控再修改采样间隔")); return; }
        int[] values={60,120,300}; String[] options={"60 s","120 s","300 s"};
        new AlertDialog.Builder(this).setTitle(I18n.t("采样间隔")).setItems(options,(d,w)->{
            getSharedPreferences("overlay",MODE_PRIVATE).edit().putInt("battery_interval",values[w]).apply(); recreate();
        }).show();
    }
    private void chooseSession() {
        records.io.execute(()->{
            try { List<String> ids=records.store().list(); ui.post(()->{
                if (isDestroyed()) return;
                if (ids.isEmpty()) { toast(I18n.t("还没有耗电记录")); return; }
                String[] titles=new String[ids.size()]; for (int i=0;i<ids.size();i++) titles[i]=date(started(ids.get(i)))+(ids.get(i).equals(records.active)?" · "+I18n.t("正在记录应用耗电"):"");
                new AlertDialog.Builder(this).setTitle(I18n.t("选择耗电记录")).setItems(titles,(d,w)->{
                    generation++; loading=false; id=ids.get(w); uid=-1; cursors.clear(); selectedEnd=-1; follow=true; from.setText("00:00:00"); to.setText(""); lastModified=-1; load(true);
                }).show();
            }); } catch (Exception e) { fail(); }
        });
    }
    private void load(boolean force) {
        if (loading) return; loading=true; int token=++generation; String requested=id;
        records.io.execute(()->{
            try {
                String chosen=requested; List<String> ids=records.store().list();
                if (chosen.isEmpty()) chosen=records.active.isEmpty()?(ids.isEmpty()?"":ids.get(0)):records.active;
                if (chosen.isEmpty()) { ui.post(()->{ if (token==generation && !isDestroyed()) {
                    loading=false; id=""; samples=Collections.emptyList(); intervals=Collections.emptyList(); rows.removeAllViews(); rows.setVisibility(View.VISIBLE);
                    graphBox.setVisibility(View.GONE); period.setText(""); session.setText(I18n.t("选择耗电记录")); total.setText(I18n.t("还没有耗电记录"));
                } }); return; }
                long modified=new java.io.File(getFilesDir(),"app-battery/"+chosen).lastModified();
                if (!force && chosen.equals(requested) && modified==lastModified) { ui.post(()->{ if (token==generation) loading=false; }); return; }
                List<AppBatteryData.Sample> data=records.store().read(chosen); List<AppBatteryData.Interval> spans=new ArrayList<>();
                for (int i=1;i<data.size();i++) spans.add(AppBatteryData.between(data.get(i-1),data.get(i)));
                String selected=chosen;
                ui.post(()->{
                    if (token!=generation || isDestroyed()) return; loading=false; id=selected; lastModified=modified; samples=data; intervals=spans;
                    session.setText(date(started(id))+"  ›");
                    if (!data.isEmpty()) {
                        origin=data.get(0).elapsed; AppBatteryData.Sample last=data.get(data.size()-1);
                        period.setText(date(data.get(0).wall)+" → "+date(last.wall));
                        if ((follow || to.getText().length()==0) && !from.hasFocus() && !to.hasFocus()) to.setText(HistoryMath.time(Math.max(0,last.elapsed-origin)));
                    }
                    draw();
                });
            } catch (Exception e) { ui.post(()->{ if (token==generation && !isDestroyed()) { loading=false; total.setText(I18n.t("耗电记录读取失败")); } }); }
        });
    }
    private long[] range() {
        long a=HistoryMath.parseTime(from.getText().toString())+origin, b=HistoryMath.parseTime(to.getText().toString())+origin+999;
        if (b<a) throw new IllegalArgumentException(I18n.t("结束时间不能早于开始时间")); return new long[]{a,b};
    }
    private void draw() {
        if (samples.size()<2) {
            rows.removeAllViews(); total.setText(I18n.t("基线已建立，等待下一次采样"));
            if (uid>=0) {
                graphBox.setVisibility(View.VISIBLE); graphBox.removeAllViews(); button(graphBox,I18n.t("返回耗电列表"),this::closeDetails,false);
                graphBox.addView(text(I18n.t("基线已建立，等待下一次采样"),14,theme.muted));
            }
            return;
        }
        try {
            long[] range=range(); Map<Integer,AppBatteryData.Summary> summary=AppBatteryData.summarize(intervals,range[0],range[1]);
            double all=0; for (AppBatteryData.Summary s:summary.values()) all+=s.total;
            total.setText(I18n.t("已统计耗电")+(summary.isEmpty()?" --":" ≈"+MetricMath.fixed(all,3))+" mAh\n"+I18n.t("耗电占比：占所列程序合计"));
            if (uid>=0) {
                AppBatteryData.Summary s=summary.get(uid);
                if (s==null) {
                    graphBox.setVisibility(View.VISIBLE); graphBox.removeAllViews(); button(graphBox,I18n.t("返回耗电列表"),this::closeDetails,false);
                    AppBatteryData.Usage last=samples.get(samples.size()-1).apps.get(uid);
                    graphBox.addView(text(appName(uid,last==null?Collections.emptyList():last.packages),18,theme.ink));
                    graphBox.addView(text(I18n.t("暂无有效读数"),14,theme.muted));
                } else drawApp(s,all,range);
                restoreOffsets();
                return;
            }
            rows.removeAllViews(); rows.setVisibility(View.VISIBLE); List<AppBatteryData.Summary> sorted=new ArrayList<>(summary.values()); sorted.sort((a,b)->Double.compare(b.total,a.total));
            if (sorted.isEmpty()) rows.addView(text(I18n.t("所选范围没有完整有效的采样区间"),13,theme.muted));
            for (int i=0;i<Math.min(shown,sorted.size());i++) {
                AppBatteryData.Summary s=sorted.get(i); LinearLayout row=column(); row.setPadding(dp(12),dp(8),dp(12),dp(8)); row.setBackground(UiMotion.surface(this,theme,theme.surface,12,false));
                LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
                TextView label=text(appName(s.uid,s.packages),14,theme.ink); label.setTypeface(null,Typeface.BOLD); heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                TextView chevron=text("›",22,theme.accent); chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); heading.addView(chevron); row.addView(heading);
                row.addView(text("≈"+MetricMath.fixed(s.total,3)+" mAh  ·  "+MetricMath.fixed(AppBatteryData.percent(s.total,all),1)+"%",15,theme.accent));
                row.addView(text(split(s.foreground,s.background,s.unknown),11,theme.muted));
                row.setContentDescription(appName(s.uid,s.packages)); row.setFocusable(true); row.setOnClickListener(v->openDetails(s.uid));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(8); rows.addView(row,p);
            }
            if (sorted.size()>shown) button(rows,I18n.t("显示更多程序"),()->{ shown+=50; draw(); },false);
            restoreOffsets();
        } catch (Exception e) { toast(I18n.t("请输入有效时间范围")); }
    }
    private void drawApp(AppBatteryData.Summary s,double all,long[] range) {
        graphBox.setVisibility(View.VISIBLE); graphBox.removeAllViews(); backList=button(graphBox,I18n.t("返回耗电列表"),this::closeDetails,false);
        TextView name=text(appName(s.uid,s.packages),18,theme.ink); name.setTypeface(null,Typeface.BOLD); graphBox.addView(name);
        graphBox.addView(text("≈"+MetricMath.fixed(s.total,3)+" mAh · "+MetricMath.fixed(AppBatteryData.percent(s.total,all),1)+"%",17,theme.accent));
        graphBox.addView(text(split(s.foreground,s.background,s.unknown),12,theme.muted));
        graphBox.addView(text(I18n.t("前台：不透明 · 后台：半透明 · 未归类：斜纹"),12,theme.ink));
        button(graphBox,I18n.t(watts?"区间平均功耗":"每区间消耗")+" · "+(watts?"W":"mAh"),()->{ watts=!watts; draw(); },false);
        graphBox.addView(text(I18n.t("上轴：手机时间 · 下轴：记录时长"),11,theme.muted));
        chart=new AppBatteryChart(this); graphBox.addView(chart,new LinearLayout.LayoutParams(-1,dp(240)));
        graphCaption=text(I18n.t("点选区间查看应用功耗"),12,theme.muted); graphBox.addView(graphCaption);
        chart.listener(i->{
            AppBatteryData.Drain d=i.apps.get(uid);
            if (d==null) return;
            selectedEnd=i.to.elapsed;
            graphCaption.setText(date(i.from.wall)+" → "+date(i.to.wall)+"\n"+I18n.t("区间平均功耗")+" ≈"+MetricMath.fixed(i.watts(d.total),3)+" W\n"
                +I18n.t("每区间消耗")+" ≈"+MetricMath.fixed(d.total,3)+" mAh\n"+split(d.foreground,d.background,d.unknown));
        });
        chart.bind(intervals,uid,range[0],range[1],origin,watts);
        chart.restore(selectedEnd);
        graphBox.addView(text(String.join("\n",s.packages)+"\nUID "+s.uid,11,theme.muted));
    }
    private String split(double fg,double bg,double unknown) { return I18n.t("前台")+" "+MetricMath.fixed(fg,3)+" · "+I18n.t("后台")+" "+MetricMath.fixed(bg,3)+" · "+I18n.t("未归类")+" "+MetricMath.fixed(unknown,3)+" mAh"; }
    private String appName(int uid,List<String> packages) {
        if (packages.isEmpty()) return I18n.t(uid % 100000 >= 10000 ? "未识别程序" : "系统进程")+" · UID "+uid;
        String pkg=packages.get(0); String label=labels.get(pkg);
        if (label==null) { try { label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString(); } catch (Exception e) { label=pkg; } labels.put(pkg,label); }
        return label+(packages.size()>1?" +"+(packages.size()-1)+" · "+I18n.t("共享统计"):"");
    }
    private void deleteRecord() {
        if (id.isEmpty()) return;
        if (id.equals(records.active)) { toast(I18n.t("请先停止耗电监控")); return; }
        String selected=id;
        new AlertDialog.Builder(this).setTitle(I18n.t("删除整次耗电记录"))
            .setMessage(I18n.t("删除后不能恢复，是否删除这次耗电记录？")).setPositiveButton(I18n.t("删除"),(d,w)->records.io.execute(()->{
                try { records.store().delete(selected); ui.post(()->{ if (isDestroyed()) return; generation++; loading=false; id=""; uid=-1; lastModified=-1; from.setText("00:00:00"); to.setText(""); load(true); }); }
                catch (Exception e) { fail(); }
            })).setNegativeButton(I18n.t("取消"),null).show();
    }
    private void fail() { ui.post(()->{ if (!isDestroyed()) toast(I18n.t("耗电记录读取失败")); }); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private static long started(String id) { return Long.parseLong(id.substring(0,id.indexOf('-'))); }
    private String date(long wall) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date(wall)); }
}
