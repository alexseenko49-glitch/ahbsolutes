package org.telegram.messenger.ahbsolute;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * AHbsolute custom interface. Everything here can be switched off in the AHbsolute settings screen
 * (master switch + one switch per feature), and everything talks to Telegram only through reflection / public Android API:
 *  - glass bottom panel: turns on Telegram's own "liquid glass" (LiteMode.FLAG_LIQUID_GLASS) that the tab bar already supports
 *  - chat wallpaper = picture of the selected AHbsolute theme (see AHbsoluteThemes)
 *  - a chat row trembles while it is held (long press) and returns to its place when released
 */
public class AHbsoluteUi {

    private static boolean inited;
    private static final Handler H = new Handler(Looper.getMainLooper());

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("ahb_ui", Context.MODE_PRIVATE); }
    public static boolean master(Context c) { return sp(c).getBoolean("master", true); }
    public static boolean glass(Context c) { return master(c) && sp(c).getBoolean("glass", true); }
    public static boolean wallpaper(Context c) { return master(c) && sp(c).getBoolean("wp", true); }
    public static boolean shake(Context c) { return master(c) && sp(c).getBoolean("shake", true); }
    private static boolean raw(Context c, String k) { return sp(c).getBoolean(k, true); }

    /* ------------------------------------------------------------------ init ------------------------------------------------------------------ */

    public static synchronized void init(Context ctx) {
        if (inited || ctx == null) return;
        try {
            final Application app = (Application) ctx.getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityResumed(Activity a) { onResumed(a); }
                @Override public void onActivityPaused(Activity a) { stopShake(); }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) { }
            });
            inited = true;
            if (ctx instanceof Activity) onResumed((Activity) ctx);
        } catch (Throwable ignored) { }
    }

    private static void onResumed(final Activity a) {
        try { installTouch(a); } catch (Throwable ignored) { }
        try { applyGlass(a.getApplicationContext()); } catch (Throwable ignored) { }
    }

    /* ------------------------------------------------------------------ glass panel ------------------------------------------------------------------ */

    public static void applyGlass(Context c) {
        try {
            Class<?> lm = Class.forName("org.telegram.messenger.LiteMode");
            int flag = lm.getField("FLAG_LIQUID_GLASS").getInt(null);
            Method isSet = null, toggle = null;
            for (Method m : lm.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("isEnabledSetting") && p.length == 1 && p[0] == int.class) isSet = m;
                if (m.getName().equals("toggleFlag") && p.length == 2 && p[0] == int.class && p[1] == boolean.class) toggle = m;
            }
            if (toggle == null) return;
            boolean on = isSet != null && (Boolean) isSet.invoke(null, flag);
            SharedPreferences s = sp(c);
            if (glass(c)) {
                if (!on) { toggle.invoke(null, flag, true); s.edit().putBoolean("glass_forced", true).apply(); }
            } else if (s.getBoolean("glass_forced", false)) {
                if (on) toggle.invoke(null, flag, false);
                s.edit().putBoolean("glass_forced", false).apply();
            }
        } catch (Throwable ignored) { }
    }

    /* ------------------------------------------------------------------ chat row shake ------------------------------------------------------------------ */

    private static View target;
    private static boolean shaking;
    private static float downX, downY;
    private static long shakeStart;
    private static final Runnable startRunnable = new Runnable() {
        @Override public void run() { startShake(); }
    };

    private static void installTouch(final Activity a) {
        Window w = a.getWindow();
        if (w == null) return;
        final Window.Callback cur = w.getCallback();
        if (cur == null) return;
        if (Proxy.isProxyClass(cur.getClass()) && Proxy.getInvocationHandler(cur) instanceof TouchHandler) return;
        Window.Callback proxy = (Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class}, new TouchHandler(a, cur));
        w.setCallback(proxy);
    }

    private static class TouchHandler implements InvocationHandler {
        final Activity act; final Window.Callback orig;
        TouchHandler(Activity a, Window.Callback o) { act = a; orig = o; }
        @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            try {
                if (args != null && args.length == 1 && args[0] instanceof MotionEvent && m.getName().equals("dispatchTouchEvent")) {
                    onTouch(act, (MotionEvent) args[0]);
                }
            } catch (Throwable ignored) { }
            try {
                return m.invoke(orig, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    private static void onTouch(Activity a, MotionEvent ev) {
        Context c = a.getApplicationContext();
        int act = ev.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            stopShake();
            if (!shake(c)) return;
            View hit = deepest(a.getWindow().getDecorView(), ev.getRawX(), ev.getRawY());
            View cell = hit;
            while (cell != null && !"DialogCell".equals(cell.getClass().getSimpleName())) {
                cell = cell.getParent() instanceof View ? (View) cell.getParent() : null;
            }
            if (cell != null) {
                target = cell; downX = ev.getRawX(); downY = ev.getRawY();
                H.postDelayed(startRunnable, ViewConfiguration.getLongPressTimeout());
            }
        } else if (act == MotionEvent.ACTION_MOVE) {
            if (target != null) {
                float slop = ViewConfiguration.get(a).getScaledTouchSlop() * (shaking ? 3f : 1.2f);
                float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                if (dx * dx + dy * dy > slop * slop) stopShake();
            }
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            stopShake();
        }
    }

    private static View deepest(View v, float rx, float ry) {
        if (v == null || v.getVisibility() != View.VISIBLE) return null;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (rx < loc[0] || ry < loc[1] || rx > loc[0] + v.getWidth() || ry > loc[1] + v.getHeight()) return null;
        if ("DialogCell".equals(v.getClass().getSimpleName())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = deepest(g.getChildAt(i), rx, ry);
                if (r != null) return r;
            }
        }
        return v;
    }

    private static void startShake() {
        final View v = target;
        if (v == null || !v.isAttachedToWindow()) return;
        shaking = true;
        shakeStart = SystemClock.uptimeMillis();
        final float d = v.getResources().getDisplayMetrics().density;
        v.postOnAnimation(new Runnable() {
            @Override public void run() {
                if (!shaking || target != v || !v.isAttachedToWindow()) { reset(v); return; }
                long t = SystemClock.uptimeMillis() - shakeStart;
                if (t > 20000) { stopShake(); return; }
                double ph = t / 1000.0 * 2 * Math.PI * 9.0;
                v.setRotation((float) (1.1 * Math.sin(ph)));
                v.setTranslationX((float) (0.9 * d * Math.sin(ph + 1.3)));
                v.postOnAnimation(this);
            }
        });
    }

    private static void reset(View v) {
        try {
            v.animate().cancel();
            v.animate().rotation(0f).translationX(0f).setDuration(90).start();
        } catch (Throwable ignored) { }
    }

    private static void stopShake() {
        H.removeCallbacks(startRunnable);
        View v = target;
        shaking = false;
        target = null;
        if (v != null) reset(v);
    }

    /* ------------------------------------------------------------------ settings UI ------------------------------------------------------------------ */

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static void row(final Context c, LinearLayout content, int textColor, String label, final String key, final Runnable refresh) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        TextView tv = new TextView(c);
        tv.setText(label + "   " + (raw(c, key) ? t("ВКЛ ✓", "ON ✓") : t("ВЫКЛ", "OFF")));
        tv.setTextSize(16);
        tv.setTextColor(textColor);
        row.addView(tv);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp(c).edit().putBoolean(key, !raw(c, key)).apply();
                try { applyGlass(c); } catch (Throwable ignored) { }
                if (key.equals("wp") || key.equals("master")) {
                    try { AHbsoluteThemes.reapply(c); } catch (Throwable ignored) { }
                }
                if (refresh != null) refresh.run();
            }
        });
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    public static void addSection(Context c, LinearLayout content, int headerColor, int textColor, int hintColor, Runnable refresh) {
        TextView head = new TextView(c);
        head.setText(t("КАСТОМНЫЙ ИНТЕРФЕЙС", "CUSTOM INTERFACE"));
        head.setTextSize(13);
        head.setTextColor(headerColor);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 6));
        content.addView(head);

        row(c, content, textColor, t("Кастомный интерфейс (всё сразу)", "Custom interface (everything)"), "master", refresh);
        row(c, content, textColor, t("Стеклянная нижняя панель", "Glass bottom panel"), "glass", refresh);
        row(c, content, textColor, t("Обои чата по теме", "Chat wallpaper from theme"), "wp", refresh);
        row(c, content, textColor, t("Дрожь чата при удержании", "Chat shakes while held"), "shake", refresh);

        TextView note = new TextView(c);
        note.setText(t("Если что-то глючит, выключите «Кастомный интерфейс» — всё вернётся к обычному виду. Стеклянная панель использует встроенное «жидкое стекло» Telegram (нужен Android 13+ для полного эффекта).",
                "If anything misbehaves, turn the master switch off to get the regular interface back. The glass panel uses Telegram's built-in liquid glass (Android 13+ for the full effect)."));
        note.setTextSize(12);
        note.setTextColor(hintColor);
        note.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
        content.addView(note);
    }
}
