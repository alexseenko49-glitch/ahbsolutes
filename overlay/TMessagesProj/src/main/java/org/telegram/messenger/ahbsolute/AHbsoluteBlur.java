package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * Motion blur while scrolling FAST (vertical blur on RecyclerListView, Android 12+ only, uses RenderEffect).
 * Slow scrolling is never blurred; the blur fades with speed and disappears as soon as the list slows down or stops.
 */
public class AHbsoluteBlur {

    private static final float FAST_DP_PER_MS = 3.2f;   // threshold: ~3200 dp/s
    private static final float MAX_RADIUS_DP = 9f;

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("ahb_blur", Context.MODE_PRIVATE); }
    public static boolean enabled(Context c) { return sp(c).getBoolean("on", true); }
    public static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("on", v).apply(); }

    /** called from RecyclerListView constructors (patch "blur") */
    public static void attach(final RecyclerView rv) {
        try { if (rv != null) AHbsoluteUi.init(rv.getContext()); } catch (Throwable ignored) { }
        try {
            if (rv != null) rv.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                @Override public boolean onInterceptTouchEvent(RecyclerView v, android.view.MotionEvent e) {
                    try {
                        View child = e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN ? v.findChildViewUnder(e.getX(), e.getY()) : null;
                        AHbsoluteUi.listTouch(v.getContext(), e.getActionMasked(), e.getEventTime(), child, e.getRawX(), e.getRawY());
                    } catch (Throwable ignored) { }
                    return false;
                }
                @Override public void onTouchEvent(RecyclerView v, android.view.MotionEvent e) {
                    try {
                        AHbsoluteUi.listTouch(v.getContext(), e.getActionMasked(), e.getEventTime(), null, e.getRawX(), e.getRawY());
                    } catch (Throwable ignored) { }
                }
                @Override public void onRequestDisallowInterceptTouchEvent(boolean disallow) { }
            });
        } catch (Throwable ignored) { }
        if (Build.VERSION.SDK_INT < 31 || rv == null) return;
        try {
            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                long t0 = 0, lastEvent = 0;
                int acc = 0;
                int lastRadius = 0;

                private void clear(RecyclerView v) {
                    if (lastRadius != 0) { v.setRenderEffect(null); lastRadius = 0; }
                    t0 = 0; acc = 0;
                }

                @Override public void onScrolled(RecyclerView v, int dx, int dy) {
                    try {
                        if (!enabled(v.getContext())) { clear(v); return; }
                        long now = SystemClock.uptimeMillis();
                        if (t0 == 0 || now - lastEvent > 120) { t0 = now; acc = 0; }
                        lastEvent = now;
                        acc += Math.abs(dy);
                        long dt = now - t0;
                        if (dt < 24) return;
                        float density = v.getResources().getDisplayMetrics().density;
                        float speed = acc / (float) dt / density; // dp per ms
                        t0 = now; acc = 0;
                        if (speed <= FAST_DP_PER_MS) { clear(v); return; }
                        float r = Math.min(MAX_RADIUS_DP, (speed - FAST_DP_PER_MS) * 2.5f + 2f) * density;
                        int q = Math.round(r);
                        if (q != lastRadius) {
                            v.setRenderEffect(RenderEffect.createBlurEffect(0.1f, Math.max(0.5f, q), Shader.TileMode.CLAMP));
                            lastRadius = q;
                        }
                    } catch (Throwable ignored) { }
                }

                @Override public void onScrollStateChanged(RecyclerView v, int state) {
                    try { if (state == RecyclerView.SCROLL_STATE_IDLE) clear(v); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** "EFFECTS" block (one switch row) for the settings screens */
    public static void addSection(final Context c, LinearLayout content, int headerColor, int textColor, int hintColor, final Runnable refresh) {
        TextView head = new TextView(c);
        head.setText(t("ЭФФЕКТЫ", "EFFECTS"));
        head.setTextSize(13);
        head.setTextColor(headerColor);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 6));
        content.addView(head);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        TextView label = new TextView(c);
        label.setText(t("Размытие при быстрой прокрутке", "Blur on fast scrolling") + "   " + (enabled(c) ? t("ВКЛ ✓", "ON ✓") : t("ВЫКЛ", "OFF")));
        label.setTextSize(16);
        label.setTextColor(textColor);
        row.addView(label);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setEnabled(c, !enabled(c)); if (refresh != null) refresh.run(); }
        });
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(c);
        note.setText(Build.VERSION.SDK_INT >= 31
                ? t("Размывается только очень быстрая прокрутка списков. Медленная — без эффекта.", "Only very fast list scrolling is blurred. Slow scrolling has no effect.")
                : t("Работает на Android 12 и новее. На этом телефоне эффект недоступен.", "Works on Android 12+. Not available on this phone."));
        note.setTextSize(12);
        note.setTextColor(hintColor);
        note.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
        content.addView(note);
    }
}
