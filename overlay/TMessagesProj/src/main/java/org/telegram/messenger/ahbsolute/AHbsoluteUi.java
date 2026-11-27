package org.telegram.messenger.ahbsolute;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.WeakHashMap;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * AHbsolute custom interface (master switch + one switch per feature, plus a diagnostics block that shows what actually happened):
 *  - glass bottom panel: turns on Telegram's own "liquid glass" (LiteMode.FLAG_LIQUID_GLASS); where Telegram's glass is not
 *    available on the phone, a frosted translucent look is drawn on the tab bar instead
 *  - chat wallpaper = picture of the selected AHbsolute theme (see AHbsoluteThemes)
 *  - a chat row trembles while it is held and returns to its place when released
 * Talks to Telegram only through reflection / public Android API, so it can never break the build.
 */
public class AHbsoluteUi {

    private static boolean inited;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, Boolean> installed = new WeakHashMap<>();

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("ahb_ui", Context.MODE_PRIVATE); }
    private static SharedPreferences dg(Context c) { return c.getApplicationContext().getSharedPreferences("ahb_diag", Context.MODE_PRIVATE); }
    public static boolean master(Context c) { return sp(c).getBoolean("master", true); }
    public static boolean glass(Context c) { return master(c) && sp(c).getBoolean("glass", true); }
    public static boolean wallpaper(Context c) { return master(c) && sp(c).getBoolean("wp", true); }
    public static boolean shake(Context c) { return master(c) && sp(c).getBoolean("shake", true); }
    public static boolean topbar(Context c) { return master(c) && sp(c).getBoolean("topbar", false); }
    private static boolean raw(Context c, String k) { return sp(c).getBoolean(k, !k.equals("topbar")); }

    private static Context appCtx;
    public static Context ctx() { return appCtx; }
    public static boolean animOn() { return appCtx != null && master(appCtx) && raw(appCtx, "anim"); }
    public static boolean peekOn() { return appCtx != null && master(appCtx) && raw(appCtx, "peek"); }
    public static boolean localEditOn() { return appCtx != null && master(appCtx) && raw(appCtx, "localedit"); }
    public static String bannerMode() { return appCtx != null && master(appCtx) ? sp(appCtx).getString("banner", "theme") : "off"; }
    private static float pct(String key, String def) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(sp(appCtx).getString(key, def)))) / 100f; } catch (Throwable e) { return 1f; }
    }
    public static float bannerOpacity() { return appCtx == null ? 0.8f : pct("banner_op", "80"); }
    public static float profileHeaderAlpha() { return appCtx != null && master(appCtx) ? pct("prof_alpha", "100") : 1f; }
    private static int cntDown, cntCell, cntShake;

    /* ---- public access to the settings (used by AHbsoluteSettingsUi / AHbsoluteBanner) ---- */
    private static Context anyCtx() {
        if (appCtx == null) {
            try { appCtx = org.telegram.messenger.ApplicationLoader.applicationContext; } catch (Throwable ignored) { }
        }
        return appCtx;
    }
    public static boolean getBool(String key) { Context c = anyCtx(); return c != null && raw(c, key); }
    /** toggles a switch and applies the side effects the old settings rows had */
    public static void toggle(Context c, String key) {
        if (appCtx == null) appCtx = c.getApplicationContext();
        sp(c).edit().putBoolean(key, !raw(c, key)).apply();
        try { applyGlass(c); } catch (Throwable ignored) { }
        if (key.equals("wp") || key.equals("master")) {
            try { AHbsoluteThemes.reapply(c); } catch (Throwable ignored) { }
        }
    }
    public static String getStr(String key, String def) {
        Context c = anyCtx();
        if (c == null) return def;
        try { return sp(c).getString(key, def); } catch (Throwable e) { return def; }
    }
    public static void putStr(String key, String v) {
        Context c = anyCtx();
        if (c != null) sp(c).edit().putString(key, v).apply();
    }
    public static int getInt(String key, int def) {
        try { return Integer.parseInt(getStr(key, String.valueOf(def)).trim()); } catch (Throwable e) { return def; }
    }
    public static void putInt(String key, int v) { putStr(key, String.valueOf(v)); }
    /** text of the diagnostics block */
    public static String diagText(Context c) {
        SharedPreferences d = dg(c);
        String none = t("— (ещё не запускалось)", "— (has not run yet)");
        return "init: " + d.getString("d_init", none)
                + "\ntouch: " + d.getString("d_touch", none)
                + "\ntaps: " + d.getInt("n_down", 0) + ", on chat rows: " + d.getInt("n_cell", 0) + ", shakes: " + d.getInt("n_shake", 0)
                + "\nglass: " + d.getString("d_glass", none)
                + "\nengine: " + d.getString("d_engine", none)
                + "\nrefresh: " + d.getString("d_refresh", none)
                + "\nfrost: " + d.getString("d_frost", none)
                + "\ntopbar: " + d.getString("d_topbar", none)
                + "\ntheme: " + d.getString("d_theme", none);
    }

    public static void diag(String k, String v) {
        try { if (appCtx != null) dg(appCtx).edit().putString(k, v).apply(); } catch (Throwable ignored) { }
    }

    private static void flush() {
        try {
            if (appCtx != null) dg(appCtx).edit().putInt("n_down", cntDown).putInt("n_cell", cntCell).putInt("n_shake", cntShake).apply();
        } catch (Throwable ignored) { }
    }

    /* ------------------------------------------------------------------ init ------------------------------------------------------------------ */

    private static Activity findActivity(Context c) {
        for (int i = 0; i < 10 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (c instanceof ContextWrapper) c = ((ContextWrapper) c).getBaseContext(); else break;
        }
        return null;
    }

    /** called from several places (app start, every RecyclerListView); cheap and idempotent */
    public static void init(Context ctx) {
        if (ctx == null) return;
        try {
            if (!inited) {
                synchronized (AHbsoluteUi.class) {
                    if (!inited) {
                        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
                        appCtx = app;
                        ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                            @Override public void onActivityCreated(Activity a, Bundle b) { }
                            @Override public void onActivityStarted(Activity a) { }
                            @Override public void onActivityResumed(Activity a) { onResumed(a); }
                            @Override public void onActivityPaused(Activity a) { stopShake(); flush(); }
                            @Override public void onActivityStopped(Activity a) { }
                            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                            @Override public void onActivityDestroyed(Activity a) { }
                        });
                        inited = true;
                        diag("d_init", "ok, API " + Build.VERSION.SDK_INT + ", " + Build.MANUFACTURER + " " + Build.MODEL);
                    }
                }
            }
            Activity a = findActivity(ctx);
            if (a != null && !installed.containsKey(a)) onResumed(a);
        } catch (Throwable e) {
            diag("d_init", "error: " + e);
        }
    }

    private static void onResumed(final Activity a) {
        installed.put(a, true);
        try { installTouch(a); } catch (Throwable e) { diag("d_touch", "error: " + e); }
        final Context c = a.getApplicationContext();
        // Telegram builds its tab bar / glass state a bit after onResume, so the pass is repeated a few times
        int[] delays = {0, 350, 1000, 2200};
        for (int dl : delays) {
            H.postDelayed(new Runnable() {
                @Override public void run() {
                    try { applyGlass(c); } catch (Throwable e) { diag("d_glass", "error: " + e); }
                    try { refreshGlass(a); } catch (Throwable e) { diag("d_engine", "refresh error: " + e); }
                    try { frost(a); } catch (Throwable e) { diag("d_frost", "error: " + e); }
                    try { topBar(a); } catch (Throwable e) { diag("d_topbar", "error: " + e); }
                }
            }, dl);
        }
    }

    /* ------------------------------------------------------------------ glass panel ------------------------------------------------------------------ */

    private static Method findMethod(Class<?> cl, String name, Class<?>... p) {
        for (Class<?> k = cl; k != null; k = k.getSuperclass()) {
            try { Method m = k.getDeclaredMethod(name, p); m.setAccessible(true); return m; } catch (Throwable ignored) { }
        }
        return null;
    }

    private static Field findStatic(Class<?> cl, String name) {
        for (Class<?> k = cl; k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (Throwable ignored) { }
        }
        return null;
    }

    /** true / false, or null when it cannot be read */
    private static Boolean nativeLiquidGlass() {
        try {
            Class<?> ge = Class.forName("org.telegram.utils.glass.GlassEngine");
            Field f = findStatic(ge, "glassSettings");
            if (f == null) return null;
            Object gs = f.get(null);
            if (gs == null) return null;
            Field l = findStatic(gs.getClass(), "isLiquidGlassEnabled");
            Field b = findStatic(gs.getClass(), "isBlurEnabled");
            Boolean lg = l != null ? (Boolean) l.get(gs) : null;
            Boolean bl = b != null ? (Boolean) b.get(gs) : null;
            diag("d_engine", "liquidGlass=" + lg + ", blur=" + bl);
            return lg;
        } catch (Throwable e) {
            diag("d_engine", "cannot read: " + e);
            return null;
        }
    }

    public static void applyGlass(Context c) {
        String info;
        try {
            Class<?> lm = Class.forName("org.telegram.messenger.LiteMode");
            Field ff = findStatic(lm, "FLAG_LIQUID_GLASS");
            if (ff == null) { diag("d_glass", "LiteMode.FLAG_LIQUID_GLASS not found"); return; }
            int flag = ff.getInt(null);
            Method isSet = findMethod(lm, "isEnabledSetting", int.class);
            Method toggle = findMethod(lm, "toggleFlag", int.class, boolean.class);
            if (toggle == null) { diag("d_glass", "LiteMode.toggleFlag(int,boolean) not found"); return; }
            boolean on = isSet != null && (Boolean) isSet.invoke(null, flag);
            SharedPreferences s = sp(c);
            if (glass(c)) {
                if (!on) { toggle.invoke(null, flag, true); s.edit().putBoolean("glass_forced", true).apply(); info = "flag was OFF -> turned ON"; }
                else info = "flag already ON";
            } else if (s.getBoolean("glass_forced", false)) {
                if (on) toggle.invoke(null, flag, false);
                s.edit().putBoolean("glass_forced", false).apply();
                info = "switch OFF -> flag restored";
            } else info = "switch OFF (nothing changed)";
            boolean now = isSet != null && (Boolean) isSet.invoke(null, flag);
            diag("d_glass", info + "; flag now " + (now ? "ON" : "OFF") + "; API " + Build.VERSION.SDK_INT);
            nativeLiquidGlass();
        } catch (Throwable e) {
            Throwable r = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            diag("d_glass", "error: " + r);
        }
    }

    /** asks Telegram's glass engine to re-read the settings (it only did that after you entered a chat) and repaints the glass views */
    private static void refreshGlass(Activity a) {
        try {
            Class<?> ge = Class.forName("org.telegram.utils.glass.GlassEngine");
            for (Class<?> k = ge; k != null; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (m.getName().equals("checkGlassSettings") && m.getParameterTypes().length == 0) {
                        m.setAccessible(true);
                        m.invoke(null);
                        diag("d_refresh", "checkGlassSettings() called");
                    }
                }
            }
        } catch (Throwable e) {
            diag("d_refresh", "checkGlassSettings failed: " + (e.getCause() != null ? e.getCause() : e));
        }
        invalidateGlass(a.getWindow().getDecorView());
    }

    private static void invalidateGlass(View v) {
        if (v == null) return;
        String n = v.getClass().getSimpleName();
        if (n.contains("Glass") || n.equals("MainTabsLayout")) { v.invalidate(); }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateGlass(g.getChildAt(i));
        }
    }

    /** compact top bar with rounded corners (visual only: clips the action bar of the chat list) */
    private static void topBar(Activity a) {
        Context c = a.getApplicationContext();
        View bar = findByClass(a.getWindow().getDecorView(), "ActionBar");
        if (bar == null) { diag("d_topbar", "ActionBar not found"); return; }
        if (topbar(c)) {
            final float d = c.getResources().getDisplayMetrics().density;
            bar.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    int w = v.getWidth(), h = v.getHeight();
                    if (w <= 0 || h <= 0) return;
                    o.setRoundRect(Math.round(10 * d), Math.round(5 * d), w - Math.round(10 * d), h - Math.round(5 * d), 22 * d);
                }
            });
            bar.setClipToOutline(true);
            bar.invalidateOutline();
            bar.setTag("ahb_topbar");
            diag("d_topbar", "compact rounded bar applied " + bar.getWidth() + "x" + bar.getHeight());
        } else if ("ahb_topbar".equals(bar.getTag())) {
            bar.setClipToOutline(false);
            bar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            bar.setTag(null);
            diag("d_topbar", "removed");
        }
    }

    private static View findByClass(View v, String simple) {
        if (v == null) return null;
        if (simple.equals(v.getClass().getSimpleName())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findByClass(g.getChildAt(i), simple);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void frost(Activity a) {
        Context c = a.getApplicationContext();
        View tabs = findByClass(a.getWindow().getDecorView(), "MainTabsLayout");
        if (tabs == null) { diag("d_frost", "tab bar (MainTabsLayout) not found on screen"); return; }
        boolean wantFrost = glass(c);
        Boolean nat = nativeLiquidGlass();
        if (wantFrost && Boolean.TRUE.equals(nat)) {
            if (tabs.getTag() != null && "ahb_frost".equals(tabs.getTag())) { tabs.setBackground(null); tabs.setTag(null); }
            diag("d_frost", "native liquid glass is active, own frost not needed");
            return;
        }
        if (wantFrost) {
            float d = c.getResources().getDisplayMetrics().density;
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x38FFFFFF);
            g.setStroke(Math.max(1, Math.round(d)), 0x80FFFFFF);
            g.setCornerRadius(Math.max(tabs.getHeight(), Math.round(48 * d)) / 2f);
            tabs.setBackground(g);
            tabs.setTag("ahb_frost");
            diag("d_frost", "frosted look applied to tab bar " + tabs.getWidth() + "x" + tabs.getHeight());
        } else if ("ahb_frost".equals(tabs.getTag())) {
            tabs.setBackground(null); tabs.setTag(null);
            diag("d_frost", "removed");
        }
    }

    /* ------------------------------------------------------------------ chat row shake ------------------------------------------------------------------ */

    private static View target;
    private static boolean shaking;
    private static float downX, downY;
    private static long shakeStart;
    private static final Runnable startRunnable = new Runnable() { @Override public void run() { startShake(); } };

    private static void installTouch(final Activity a) {
        Window w = a.getWindow();
        if (w == null) { diag("d_touch", "no window"); return; }
        final Window.Callback cur = w.getCallback();
        if (cur == null) { diag("d_touch", "window callback is null"); return; }
        if (Proxy.isProxyClass(cur.getClass()) && Proxy.getInvocationHandler(cur) instanceof TouchHandler) {
            diag("d_touch", "already installed on " + a.getClass().getSimpleName());
            return;
        }
        Window.Callback proxy = (Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class}, new TouchHandler(a, cur));
        w.setCallback(proxy);
        diag("d_touch", "installed on " + a.getClass().getSimpleName());
    }

    private static class TouchHandler implements InvocationHandler {
        final Activity act; final Window.Callback orig;
        TouchHandler(Activity a, Window.Callback o) { act = a; orig = o; }
        @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            try {
                if (args != null && args.length == 1 && args[0] instanceof MotionEvent && m.getName().equals("dispatchTouchEvent")) {
                    onTouch(act, (MotionEvent) args[0]);
                    if (AHbsoluteAnim.consume((MotionEvent) args[0])) return Boolean.TRUE;
                }
            } catch (Throwable ignored) { }
            try {
                return m.invoke(orig, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    private static long lastEvKey = -1;

    private static void onTouch(Activity a, MotionEvent ev) {
        int act = ev.getActionMasked();
        View cand = null;
        if (act == MotionEvent.ACTION_DOWN && shake(a.getApplicationContext())) cand = deepest(a.getWindow().getDecorView(), ev.getRawX(), ev.getRawY());
        handle(a, act, ev.getEventTime(), cand, ev.getRawX(), ev.getRawY());
    }

    /** list-level entry (RecyclerView item touch listener, see AHbsoluteBlur): child = view under the finger on ACTION_DOWN */
    public static void listTouch(Context ctx, int act, long time, View child, float rx, float ry) {
        try { handle(ctx, act, time, child, rx, ry); } catch (Throwable ignored) { }
    }

    private static void handle(Context ctx, int act, long time, View cand, float rx, float ry) {
        long key = time * 8 + act;
        if (key == lastEvKey) return; // same event seen by both entry points
        lastEvKey = key;
        Context c = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        if (appCtx == null) appCtx = c;
        if (act == MotionEvent.ACTION_DOWN) {
            try { AHbsoluteAnim.onDown(rx, ry, time, c.getResources().getDisplayMetrics().density); } catch (Throwable ignored) { }
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            try { AHbsoluteAnim.onUp(time); } catch (Throwable ignored) { }
        }
        if (act == MotionEvent.ACTION_DOWN) {
            stopShake();
            cntDown++;
            if (!shake(c)) return;
            View cell = cand;
            while (cell != null && !"DialogCell".equals(cell.getClass().getSimpleName())) {
                cell = cell.getParent() instanceof View ? (View) cell.getParent() : null;
            }
            if (cell != null) {
                cntCell++;
                target = cell; downX = rx; downY = ry;
                H.postDelayed(startRunnable, ViewConfiguration.getLongPressTimeout());
            }
        } else if (act == MotionEvent.ACTION_MOVE) {
            if (target != null) {
                float slop = ViewConfiguration.get(c).getScaledTouchSlop() * (shaking ? 3f : 1.2f);
                float dx = rx - downX, dy = ry - downY;
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
        cntShake++;
        flush();
        shakeStart = SystemClock.uptimeMillis();
        final float d = v.getResources().getDisplayMetrics().density;
        v.postOnAnimation(new Runnable() {
            @Override public void run() {
                if (!shaking || target != v || !v.isAttachedToWindow()) { reset(v); return; }
                long t = SystemClock.uptimeMillis() - shakeStart;
                if (t > 20000) { stopShake(); return; }
                double ph = t / 1000.0 * 2 * Math.PI * 12.0;
                v.setRotation((float) (1.8 * Math.sin(ph)));
                v.setTranslationX((float) (2.2 * d * Math.sin(ph + 1.3)));
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
                if (appCtx == null) appCtx = c.getApplicationContext();
                try { applyGlass(c); } catch (Throwable ignored) { }
                if (key.equals("wp") || key.equals("master")) {
                    try { AHbsoluteThemes.reapply(c); } catch (Throwable ignored) { }
                }
                if (refresh != null) refresh.run();
            }
        });
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void cycle(final Context c, LinearLayout content, int textColor, String label, final String key,
                              final String[] values, final String[] names, final String def, final Runnable refresh) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        String cur = sp(c).getString(key, def);
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) idx = i;
        TextView tv = new TextView(c);
        tv.setText(label + ":   " + names[idx] + "  ›");
        tv.setTextSize(16);
        tv.setTextColor(textColor);
        row.addView(tv);
        final int fidx = idx;
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp(c).edit().putString(key, values[(fidx + 1) % values.length]).apply();
                if (appCtx == null) appCtx = c.getApplicationContext();
                if (refresh != null) refresh.run();
            }
        });
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void line(Context c, LinearLayout content, int color, String s) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(12);
        tv.setTextColor(color);
        tv.setPadding(dp(c, 20), dp(c, 2), dp(c, 20), dp(c, 2));
        content.addView(tv);
    }

    public static void addSection(Context c, LinearLayout content, int headerColor, int textColor, int hintColor, Runnable refresh) {
        if (appCtx == null) appCtx = c.getApplicationContext();
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
        row(c, content, textColor, t("Верхняя панель: меньше и скруглённая", "Top bar: smaller and rounded"), "topbar", refresh);
        row(c, content, textColor, t("Анимация открытия чата (из точки)", "Chat opening animation (from a dot)"), "anim", refresh);
        row(c, content, textColor, t("Два быстрых нажатия: открыть и сразу закрыть", "Double tap: open and auto-close"), "peek", refresh);
        row(c, content, textColor, t("Изменять сообщения друга (только у меня)", "Edit friend's messages (only for me)"), "localedit", refresh);

        TextView ph = new TextView(c);
        ph.setText(t("ПРОФИЛЬ", "PROFILE"));
        ph.setTextSize(13);
        ph.setTextColor(headerColor);
        ph.setTypeface(Typeface.DEFAULT_BOLD);
        ph.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 4));
        content.addView(ph);
        cycle(c, content, textColor, t("Баннер за аватаркой", "Banner behind avatar"), "banner",
                new String[]{"off", "theme", "vice", "chess", "absolut", "teg"},
                new String[]{t("выкл", "off"), t("по теме", "by theme"), "Vice", "Chess", "Absolut", "Teg"}, "theme", refresh);
        cycle(c, content, textColor, t("Яркость баннера", "Banner opacity"), "banner_op",
                new String[]{"100", "80", "60", "40"}, new String[]{"100%", "80%", "60%", "40%"}, "80", refresh);
        cycle(c, content, textColor, t("Прозрачность шапки профиля", "Profile header transparency"), "prof_alpha",
                new String[]{"100", "75", "50", "25", "0"}, new String[]{t("обычная", "normal"), "75%", "50%", "25%", t("прозрачная", "transparent")}, "100", refresh);

        TextView note = new TextView(c);
        note.setText(t("Если что-то глючит, выключите «Кастомный интерфейс» — всё вернётся к обычному виду. Эффекты применяются после полного перезапуска Telegram.",
                "If anything misbehaves, turn the master switch off to get the regular interface back. Effects apply after a full restart of Telegram."));
        note.setTextSize(12);
        note.setTextColor(hintColor);
        note.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 4));
        content.addView(note);

        // ---- diagnostics (what really happened during the last Telegram session) ----
        SharedPreferences d = dg(c);
        TextView dh = new TextView(c);
        dh.setText(t("ДИАГНОСТИКА (пришлите скриншот, если не работает)", "DIAGNOSTICS (send a screenshot if it does not work)"));
        dh.setTextSize(12);
        dh.setTextColor(headerColor);
        dh.setTypeface(Typeface.DEFAULT_BOLD);
        dh.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 4));
        content.addView(dh);
        String none = t("— (Telegram ещё не запускался)", "— (Telegram has not run yet)");
        line(c, content, hintColor, "init: " + d.getString("d_init", none));
        line(c, content, hintColor, "touch: " + d.getString("d_touch", none));
        line(c, content, hintColor, "taps: " + d.getInt("n_down", 0) + ", on chat rows: " + d.getInt("n_cell", 0) + ", shakes: " + d.getInt("n_shake", 0));
        line(c, content, hintColor, "glass: " + d.getString("d_glass", none));
        line(c, content, hintColor, "engine: " + d.getString("d_engine", none));
        line(c, content, hintColor, "refresh: " + d.getString("d_refresh", none));
        line(c, content, hintColor, "frost: " + d.getString("d_frost", none));
        line(c, content, hintColor, "topbar: " + d.getString("d_topbar", none));
        line(c, content, hintColor, "theme: " + d.getString("d_theme", none));

        LinearLayout row = new LinearLayout(c);
        row.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        TextView rb = new TextView(c);
        rb.setText(t("Обновить диагностику", "Refresh diagnostics"));
        rb.setTextSize(15);
        rb.setTextColor(headerColor);
        row.addView(rb);
        final Runnable rf = refresh;
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (rf != null) rf.run(); } });
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
}
