package io.perfoverlay.app;

import android.content.Context;
import android.animation.*;
import android.view.animation.PathInterpolator;
import android.graphics.Typeface;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Avatar-operated drawer. Hidden navigation reserves no page space. */
public final class UiNav {
    private static final String[] LABELS={"监控","样式","指标","记录","关于","应用耗电"};
    private static final int[] ORDER={0,3,5,1,2,4};
    public static Shell create(Context context,UiTheme theme,View body,int selected,IntConsumer navigate) {
        return new Shell(context,theme,body,selected,navigate);
    }
    public static void select(Shell shell,UiTheme theme,int selected) { shell.select(selected); }
    private static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    private static ImageButton avatar(Context c,UiTheme theme,String description,Runnable click) {
        ImageButton image=new ImageButton(c); image.setImageResource(R.drawable.murasame_character);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setPadding(0,0,0,0);
        image.setContentDescription(I18n.t(description)); image.setTooltipText(I18n.t(description));
        image.setBackground(UiMotion.surface(c,theme,0,12,false)); image.setOnClickListener(v->click.run());
        return image;
    }
    public static LinearLayout header(Context c,UiTheme theme,String title,Runnable menu,Runnable back) {
        LinearLayout header=new LinearLayout(c); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        header.addView(avatar(c,theme,"展开导航",menu),new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));
        if(back!=null) {
            Button button=new Button(c); button.setText("‹"); button.setTextSize(24); button.setTextColor(theme.accent);
            button.setMinWidth(0); button.setPadding(0,0,0,0); button.setContentDescription(I18n.t("返回"));
            button.setBackground(UiMotion.surface(c,theme,theme.surface,12,false)); button.setOnClickListener(v->back.run());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(c,48),dp(c,48)); lp.leftMargin=dp(c,8); header.addView(button,lp);
        }
        TextView label=new TextView(c); label.setText(title); label.setTextColor(theme.ink); label.setTypeface(null,Typeface.BOLD);
        label.setTextSize(23); label.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); label.setPadding(dp(c,8),0,0,0); label.setMaxLines(1);
        label.setAutoSizeTextTypeUniformWithConfiguration(12,23,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        header.addView(label,new LinearLayout.LayoutParams(0,dp(c,48),1)); return header;
    }
    public static int direction(int from,int to) {
        int a=0,b=0; for(int i=0;i<ORDER.length;i++) { if(ORDER[i]==from)a=i; if(ORDER[i]==to)b=i; }
        return b>=a?1:-1;
    }
    public static final class Shell extends FrameLayout {
        private final UiTheme theme;
        private final View body, shade;
        private final LinearLayout rail;
        private final LinearLayout items;
        private final ImageButton toggle;
        private final LinearLayout[] tabs=new LinearLayout[6];
        private final TextView[] labels=new TextView[6];
        private boolean expanded;
        private float openness;
        private ValueAnimator drawerAnimator;
        private Runnable afterClose;
        Shell(Context context,UiTheme theme,View body,int selected,IntConsumer navigate) {
            super(context); this.theme=theme; this.body=body; setBackgroundColor(theme.bg);
            addView(body,new LayoutParams(-1,-1));
            shade=new View(context); shade.setBackgroundColor(0x44000000); shade.setVisibility(GONE);
            shade.setContentDescription(I18n.t("收起导航")); shade.setOnClickListener(v->collapse()); addView(shade,new LayoutParams(-1,-1));
            rail=new LinearLayout(context); rail.setOrientation(LinearLayout.VERTICAL); rail.setClickable(true);
            rail.setBackgroundColor(theme.card);
            FrameLayout drawerHeader=new FrameLayout(context);
            toggle=avatar(context,theme,"收起导航",this::toggle);
            LayoutParams portrait=new LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT); portrait.leftMargin=dp(16); portrait.topMargin=dp(12);
            drawerHeader.addView(toggle,portrait); rail.addView(drawerHeader,new LinearLayout.LayoutParams(-1,dp(72)));
            ScrollView list=new ScrollView(context); list.setVerticalScrollBarEnabled(false);
            items=new LinearLayout(context); items.setOrientation(LinearLayout.VERTICAL); items.setPadding(dp(2),0,dp(2),dp(8));
            list.addView(items); rail.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            for(int position=0;position<ORDER.length;position++) {
                int index=ORDER[position];
                LinearLayout tab=new LinearLayout(context); tab.setGravity(Gravity.CENTER_VERTICAL); tabs[index]=tab;
                UiIcon icon=new UiIcon(context,index==5?8:index,theme.muted);
                FrameLayout iconBox=new FrameLayout(context); iconBox.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
                tab.addView(iconBox,new LinearLayout.LayoutParams(dp(48),dp(48)));
                TextView label=new TextView(context); labels[index]=label; label.setTextSize(12); label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(0,dp(8),dp(4),dp(8)); tab.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                tab.setMinimumHeight(dp(48)); tab.setFocusable(true); tab.setOnClickListener(v->{ afterClose=()->navigate.accept(index); setExpanded(false); });
                LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,-2); item.topMargin=dp(position==3?20:6); items.addView(tab,item);
            }
            addView(rail,new LayoutParams(1,-1,Gravity.LEFT));
            setOnApplyWindowInsetsListener((v,insets)->{
                if(Build.VERSION.SDK_INT>=30) {
                    android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                    setPadding(safe.left,safe.top,safe.right,safe.bottom);
                } else setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            });
            select(selected); setExpanded(false);
        }
        private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
        @Override protected void onMeasure(int w,int h) {
            int available=MeasureSpec.getSize(w)-getPaddingLeft()-getPaddingRight();
            rail.getLayoutParams().width=Math.max(1,available/3);
            super.onMeasure(w,h);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            super.onLayout(changed,l,t,r,b); applyPosition();
        }
        private void applyPosition() {
            // Use the measured safe width on first open and after rotation, never an old screen width.
            float width=Math.max(1,rail.getLayoutParams().width);
            rail.setTranslationX(-(1-openness)*width); shade.setAlpha(openness);
        }
        public void toggle() { afterClose=null; setExpanded(!expanded); }
        public boolean collapse() {
            if(!expanded && drawerAnimator==null && rail.getVisibility()!=VISIBLE)return false;
            afterClose=null; setExpanded(false); return true;
        }
        private void cancelAnimator() {
            if(drawerAnimator!=null) {
                drawerAnimator.removeAllListeners(); drawerAnimator.removeAllUpdateListeners(); drawerAnimator.cancel(); drawerAnimator=null;
            }
        }
        private void finishDrawer(boolean open) {
            openness=open?1:0; applyPosition();
            rail.setVisibility(open?VISIBLE:GONE); shade.setVisibility(open?VISIBLE:GONE);
            body.setImportantForAccessibility(open?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            if(open)toggle.requestFocus();
            else { Runnable next=afterClose; afterClose=null; if(next!=null && isAttachedToWindow())next.run(); }
        }
        private void setExpanded(boolean value) {
            if(expanded==value && drawerAnimator!=null)return; // New selection replaces the pending destination without restarting the close.
            expanded=value; cancelAnimator();
            float end=value?1:0;
            if(!UiMotion.enabled() || !isLaidOut() || Math.abs(end-openness)<.001f) { finishDrawer(value); return; }
            rail.setVisibility(VISIBLE); shade.setVisibility(VISIBLE);
            body.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            applyPosition();
            ValueAnimator animator=ValueAnimator.ofFloat(openness,end); drawerAnimator=animator;
            animator.setDuration(Math.max(80,Math.round((value?280:220)*Math.abs(end-openness))));
            animator.setInterpolator(new PathInterpolator(.2f,0,0,1));
            animator.addUpdateListener(a->{ openness=(float)a.getAnimatedValue(); applyPosition(); });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if(drawerAnimator!=animation)return; drawerAnimator=null; finishDrawer(value);
                }
            });
            animator.start();
        }
        private void resetDrawer() {
            afterClose=null; cancelAnimator(); expanded=false; finishDrawer(false);
        }
        @Override protected void onDetachedFromWindow() { resetDrawer(); super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility); if(visibility!=VISIBLE && rail!=null)resetDrawer();
        }
        private void select(int selected) {
            for(int i=0;i<tabs.length;i++) {
                boolean active=i==selected; int color=active?theme.accent:theme.muted;
                tabs[i].setSelected(active); tabs[i].setBackground(UiMotion.surface(getContext(),theme,active?theme.softAccent:0,12,false));
                ((UiIcon)((FrameLayout)tabs[i].getChildAt(0)).getChildAt(0)).color(color);
                labels[i].setText(I18n.t(LABELS[i])); labels[i].setTextColor(color); labels[i].setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
                tabs[i].setContentDescription(I18n.t(LABELS[i])); tabs[i].setTooltipText(I18n.t(LABELS[i]));
            }
            toggle.setContentDescription(I18n.t("收起导航"));
        }
    }
    private UiNav() { }
}
