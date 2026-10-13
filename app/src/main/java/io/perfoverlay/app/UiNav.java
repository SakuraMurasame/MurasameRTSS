package io.perfoverlay.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import java.util.function.IntConsumer;

public final class UiNav {
    public static LinearLayout create(Context context, UiTheme theme, int selected, IntConsumer navigate) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout bar = new LinearLayout(context); bar.setGravity(Gravity.CENTER);
        bar.setPadding((int)(8 * density), (int)(7 * density), (int)(8 * density), (int)(7 * density));
        bar.setBackground(theme.shape(context, theme.card, 18, true));
        String[] labels = {"监控", "样式", "指标", "记录", "关于"};
        for (int i = 0; i < labels.length; i++) {
            int index = i, color = i == selected ? theme.accent : theme.muted;
            LinearLayout tab = new LinearLayout(context); tab.setOrientation(LinearLayout.VERTICAL); tab.setGravity(Gravity.CENTER);
            tab.setBackground(UiMotion.surface(context,theme,i==selected?theme.softAccent:0,12,false));
            UiIcon icon = new UiIcon(context, i, color); tab.addView(icon, new LinearLayout.LayoutParams((int)(22 * density), (int)(22 * density)));
            TextView text = new TextView(context); text.setText(I18n.t(labels[i])); text.setTextColor(color); text.setTextSize(11);
            text.setGravity(Gravity.CENTER); text.setMaxLines(1); text.setTypeface(null, i == selected ? Typeface.BOLD : Typeface.NORMAL);
            text.setAutoSizeTextTypeUniformWithConfiguration(9, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            tab.addView(text, new LinearLayout.LayoutParams(-1, (int)(21 * density)));
            tab.setContentDescription(I18n.t(labels[i])); tab.setFocusable(true); tab.setSelected(i == selected);
            tab.setOnClickListener(v -> navigate.accept(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int)(52 * density), 1); lp.setMargins((int)(2 * density), 0, (int)(2 * density), 0);
            bar.addView(tab, lp);
        }
        return bar;
    }
    public static void select(LinearLayout bar,UiTheme theme,int selected) {
        String[] labels={"监控","样式","指标","记录","关于"};
        for (int i=0;i<bar.getChildCount();i++) {
            LinearLayout tab=(LinearLayout)bar.getChildAt(i); boolean active=i==selected; int color=active?theme.accent:theme.muted;
            tab.setSelected(active); tab.setBackground(UiMotion.surface(bar.getContext(),theme,active?theme.softAccent:0,12,false));
            ((UiIcon)tab.getChildAt(0)).color(color); TextView label=(TextView)tab.getChildAt(1);
            label.setText(I18n.t(labels[i])); label.setTextColor(color); label.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
            tab.setContentDescription(I18n.t(labels[i]));
        }
    }
    private UiNav() { }
}
