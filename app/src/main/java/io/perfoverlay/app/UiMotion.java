package io.perfoverlay.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.os.Build;
import android.transition.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/** Short, interruptible transitions. Honors Android's disabled-animation setting. */
public final class UiMotion {
    public static final class Host extends FrameLayout {
        private boolean changing;
        public Host(Context context) { super(context); }
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) { return changing || super.onInterceptTouchEvent(event); }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) { return changing || super.onTouchEvent(event); }
    }
    private UiMotion() { }
    public static boolean enabled() { return ValueAnimator.areAnimatorsEnabled(); }
    public static Drawable surface(Context c, UiTheme theme, int color, int radius, boolean border) {
        return new RippleDrawable(ColorStateList.valueOf((theme.accent & 0xFFFFFF) | 0x30000000),
                theme.shape(c,color,radius,border),theme.shape(c,0xFFFFFFFF,radius,false));
    }
    public static void swap(FrameLayout host, View next, int direction, boolean animate) {
        if (host instanceof Host) ((Host)host).changing=false;
        Object token = new Object(); host.setTag(token);
        View previous = host.getChildCount() == 0 ? null : host.getChildAt(host.getChildCount()-1);
        for (int i=host.getChildCount()-1;i>=0;i--) {
            View child=host.getChildAt(i); child.animate().withEndAction(null).setListener(null).cancel();
            child.setAlpha(1); child.setTranslationX(0);
            child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            if (child!=previous && child!=next) host.removeView(child);
        }
        if (previous==next) {
            for (int i=host.getChildCount()-1;i>=0;i--) if (host.getChildAt(i)!=next) host.removeViewAt(i);
            return;
        }
        if (next.getParent()==host) host.removeView(next);
        host.addView(next,new FrameLayout.LayoutParams(-1,-1));
        next.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (!animate || !enabled() || previous==null) {
            if (previous!=null) host.removeView(previous); next.setAlpha(1); next.setTranslationX(0); return;
        }
        float distance=18*host.getResources().getDisplayMetrics().density*(direction<0?-1:1);
        previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (host instanceof Host) ((Host)host).changing=true;
        next.setAlpha(.65f); next.setTranslationX(distance);
        previous.animate().alpha(0).translationX(-distance).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        View outgoing=previous;
        next.animate().alpha(1).translationX(0).setDuration(180).setInterpolator(new DecelerateInterpolator())
                .withEndAction(()->{
                    if (host.getTag()!=token) return;
                    host.removeView(outgoing); outgoing.setAlpha(1); outgoing.setTranslationX(0);
                    outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    if (host instanceof Host) ((Host)host).changing=false;
                }).start();
    }
    public static void expand(ViewGroup group) {
        if (enabled()) TransitionManager.beginDelayedTransition(group,new AutoTransition().setDuration(160));
    }
    public static void reveal(View view,int direction) {
        if (!enabled()) return;
        view.animate().withEndAction(null).cancel(); view.setAlpha(.7f);
        view.setTranslationX((direction<0?-1:1)*14*view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1).translationX(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
    }
    public static void restoreScroll(ScrollView view,int offset) {
        Object token=new Object(); view.setTag(token);
        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
                if (view.getTag()==token) view.scrollTo(0,offset);
                return true;
            }
        });
        view.invalidate();
    }
    public static void open(Activity activity,Intent intent,int direction) {
        activity.startActivity(intent);
        if (enabled()) activity.overridePendingTransition(direction<0?R.anim.ui_back_enter:R.anim.ui_forward_enter,
                direction<0?R.anim.ui_back_exit:R.anim.ui_forward_exit);
        else activity.overridePendingTransition(0,0);
    }
    public static void back(Activity activity) {
        activity.finish();
        if (enabled()) activity.overridePendingTransition(R.anim.ui_back_enter,R.anim.ui_back_exit);
        else activity.overridePendingTransition(0,0);
    }
    public static void backHandler(Activity activity,Runnable action) {
        if (Build.VERSION.SDK_INT>=33) activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,action::run);
    }
}
