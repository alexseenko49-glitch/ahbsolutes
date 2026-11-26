package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TimeZone;

/**
 * "Fire" streaks. A day counts when BOTH people wrote to each other that (local) day.
 * 2+ days in a row -> a number appears next to the friend's name in the chat list:
 *  orange = already refreshed today, gray = not yet refreshed today, gone = a whole day was missed.
 * Data is local (SharedPreferences) and counts from the moment this version is installed.
 * All access to Telegram's internals is via reflection, so it cannot break the build.
 */
public class AHbsoluteStreaks {

    private static final String PREF = "ahb_streaks";
    private static final int[] EMPTY = {-1000000, 0, -1000000, 0, 0}; // doneDay, streak, curDay, mine, theirs
    private static final HashMap<String, int[]> cache = new HashMap<>();
    private static SharedPreferences sp;
    private static boolean inited;
    private static Object observer;
    private static int msgEventId = Integer.MIN_VALUE;

    // reflection cache
    private static Method mGetDialogId, mIsOut;
    private static Field fOwner, fDate;
    private static Class<?> msgClass;

    private static float density() { return Resources.getSystem().getDisplayMetrics().density; }

    private static synchronized SharedPreferences sp(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp;
    }

    private static int localDay(long sec) {
        long ms = sec * 1000L;
        return (int) ((ms + TimeZone.getDefault().getOffset(ms)) / 86400000L);
    }

    private static int[] get(Context c, int acc, long uid) {
        String k = acc + ":" + uid;
        int[] s = cache.get(k);
        if (s != null) return s;
        s = EMPTY.clone();
        try {
            String v = sp(c).getString(k, null);
            if (v != null) {
                String[] p = v.split(",");
                for (int i = 0; i < 5 && i < p.length; i++) s[i] = Integer.parseInt(p[i]);
            }
        } catch (Throwable ignored) { }
        cache.put(k, s);
        return s;
    }

    private static void save(Context c, int acc, long uid, int[] s) {
        try {
            sp(c).edit().putString(acc + ":" + uid, s[0] + "," + s[1] + "," + s[2] + "," + s[3] + "," + s[4]).apply();
        } catch (Throwable ignored) { }
    }

    /* ---------------- tracking ---------------- */

    static synchronized void onMessage(Context c, int acc, long uid, long dateSec, boolean mine) {
        int d = localDay(dateSec);
        if (d > localDay(System.currentTimeMillis() / 1000L) + 1) return; // clock skew
        int[] s = get(c, acc, uid);
        if (d < s[2]) return;
        if (d > s[2]) { s[2] = d; s[3] = 0; s[4] = 0; }
        if (mine) s[3] = 1; else s[4] = 1;
        if (s[3] == 1 && s[4] == 1 && s[0] != d) {
            s[1] = (s[0] == d - 1) ? s[1] + 1 : 1;
            s[0] = d;
        }
        save(c, acc, uid, s);
    }

    /** {number, active(1)/pending(0)} or null when nothing should be shown */
    static int[] state(Context c, int acc, long uid) {
        int[] s = get(c, acc, uid);
        if (s[1] < 2) return null;
        int gap = localDay(System.currentTimeMillis() / 1000L) - s[0];
        if (gap >= 2) return null;
        return new int[]{s[1], gap <= 0 ? 1 : 0};
    }

    private static Object defaultFor(Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == float.class) return 0f;
        if (r == double.class) return 0d;
        if (r == short.class) return (short) 0;
        if (r == byte.class) return (byte) 0;
        if (r == char.class) return (char) 0;
        return null;
    }

    public static synchronized void init(final Context ctx) {
        if (inited) return;
        try {
            final Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            Class<?> nc = Class.forName("org.telegram.messenger.NotificationCenter");
            Class<?> del = Class.forName("org.telegram.messenger.NotificationCenter$NotificationCenterDelegate");
            msgEventId = nc.getField("didReceiveNewMessages").getInt(null);
            final int evId = msgEventId;
            observer = Proxy.newProxyInstance(del.getClassLoader(), new Class<?>[]{del}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method m, Object[] a) {
                    try {
                        String n = m.getName();
                        if (n.equals("didReceivedNotification") && a != null && a.length >= 3) {
                            if ((Integer) a[0] == evId) handle(app, (Integer) a[1], (Object[]) a[2]);
                            return null;
                        }
                        if (n.equals("hashCode")) return System.identityHashCode(proxy);
                        if (n.equals("equals")) return proxy == a[0];
                        if (n.equals("toString")) return "AHbStreakObserver";
                    } catch (Throwable ignored) { }
                    return defaultFor(m.getReturnType());
                }
            });
            Method getInst = nc.getMethod("getInstance", int.class);
            Method add = null;
            for (Method m : nc.getMethods()) {
                if (m.getName().equals("addObserver") && m.getParameterTypes().length == 2) add = m;
            }
            int max = 3;
            try { max = Class.forName("org.telegram.messenger.UserConfig").getField("MAX_ACCOUNT_COUNT").getInt(null); } catch (Throwable ignored) { }
            if (add != null) {
                for (int i = 0; i < max; i++) {
                    try { add.invoke(getInst.invoke(null, i), observer, evId); } catch (Throwable ignored) { }
                }
            }
            inited = true;
        } catch (Throwable ignored) { }
    }

    private static void handle(Context c, int acc, Object[] a) {
        if (a == null) return;
        ArrayList<?> list = null;
        for (Object o : a) { if (o instanceof ArrayList) { list = (ArrayList<?>) o; break; } }
        if (list == null) return;
        if (a.length >= 3 && a[2] instanceof Boolean && (Boolean) a[2]) return; // scheduled
        long me = 0;
        try {
            Object uc = Class.forName("org.telegram.messenger.UserConfig").getMethod("getInstance", int.class).invoke(null, acc);
            me = (Long) uc.getClass().getMethod("getClientUserId").invoke(uc);
        } catch (Throwable ignored) { }
        for (Object m : list) {
            try { process(c, acc, m, me); } catch (Throwable ignored) { }
        }
    }

    private static boolean isBot(int acc, long uid) {
        try {
            Object mc = Class.forName("org.telegram.messenger.MessagesController").getMethod("getInstance", int.class).invoke(null, acc);
            Object u = null;
            for (Method m : mc.getClass().getMethods()) {
                if (m.getName().equals("getUser") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Long.class) {
                    u = m.invoke(mc, Long.valueOf(uid)); break;
                }
            }
            if (u == null) return false;
            return u.getClass().getField("bot").getBoolean(u);
        } catch (Throwable e) { return false; }
    }

    private static void process(Context c, int acc, Object m, long me) throws Exception {
        if (msgClass != m.getClass()) {
            msgClass = m.getClass();
            mGetDialogId = msgClass.getMethod("getDialogId");
            mIsOut = msgClass.getMethod("isOutOwner");
            fOwner = msgClass.getField("messageOwner");
            fDate = null;
        }
        Object owner = fOwner.get(m);
        if (owner == null) return;
        if (owner.getClass().getSimpleName().contains("Service")) return;
        if (fDate == null || fDate.getDeclaringClass() != owner.getClass() && !fDate.getDeclaringClass().isInstance(owner)) fDate = owner.getClass().getField("date");
        long uid = (Long) mGetDialogId.invoke(m);
        if (uid <= 0 || uid == me) return;
        if (isBot(acc, uid)) return;
        boolean mine = (Boolean) mIsOut.invoke(m);
        long date = ((Number) fDate.get(owner)).longValue();
        onMessage(c, acc, uid, date, mine);
    }

    /* ---------------- drawing (called from DialogCell.onDraw, canvas is translated to the name layout origin) ---------------- */

    private static Paint numPaint, firePaint;
    private static Field fDlg, fAcc;
    private static Class<?> cellClass;

    public static void drawBadge(Canvas canvas, View cell, Layout nameLayout) {
        try {
            Context c = cell.getContext();
            if (!inited) init(c);
            if (cellClass != cell.getClass()) {
                cellClass = cell.getClass();
                fDlg = findField(cellClass, "currentDialogId");
                fAcc = findField(cellClass, "currentAccount");
            }
            if (fDlg == null) return;
            long uid = fDlg.getLong(cell);
            int acc = fAcc != null ? fAcc.getInt(cell) : 0;
            if (uid <= 0) return;
            int[] st = state(c, acc, uid);
            if (st == null) return;
            float d = density();
            if (numPaint == null) {
                numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                numPaint.setTypeface(Typeface.DEFAULT_BOLD);
                firePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            numPaint.setTextSize(14 * d);
            firePaint.setTextSize(13 * d);
            boolean active = st[1] == 1;
            numPaint.setColor(active ? 0xFFFF8A1F : 0xFF9AA0A6);
            if (active) firePaint.setColorFilter(null);
            else { ColorMatrix cm = new ColorMatrix(); cm.setSaturation(0f); firePaint.setColorFilter(new ColorMatrixColorFilter(cm)); }
            String fire = "\uD83D\uDD25";
            String num = String.valueOf(st[0]);
            float fw = firePaint.measureText(fire), nw = numPaint.measureText(num);
            float total = fw + 1 * d + nw;
            float x = nameLayout.getLineWidth(0) + 6 * d;
            float maxX = nameLayout.getWidth() - total;
            if (x > maxX && maxX > 0) x = maxX;
            float y = nameLayout.getLineBaseline(0);
            canvas.drawText(fire, x, y, firePaint);
            canvas.drawText(num, x + fw + 1 * d, y, numPaint);
        } catch (Throwable ignored) { }
    }

    private static Field findField(Class<?> cl, String name) {
        for (Class<?> k = cl; k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (Throwable ignored) { }
        }
        return null;
    }
}
