package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.RectF;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

/** AHbsolute: central config + helpers. Unofficial client, not affiliated with Telegram. */
public class AHbsolute {

    public static final int DRAWER_ID = 9001;

    public static final int SHAPE_CIRCLE = 0, SHAPE_SQUARE = 1, SHAPE_STAR = 2, SHAPE_TRIANGLE = 3;

    // Invisible marker appended to the last name: ZWJ ZWNJ ZWJ + 2 chars (ZWNJ = 0, ZWJ = 1) = shape id.
    private static final String MARK = "\u200D\u200C\u200D";
    private static final char B0 = '\u200C', B1 = '\u200D';

    private static int[] drawerIds = new int[0];
    private static String[] drawerLabels = new String[0];

    /* ---------- text ---------- */

    public static String t(String ru, String en) {
        String l = Locale.getDefault().getLanguage();
        return (l.equals("ru") || l.equals("uk") || l.equals("be")) ? ru : en;
    }

    /* ---------- prefs ---------- */

    private static SharedPreferences p() {
        return ApplicationLoader.applicationContext.getSharedPreferences("ahbsolute", Context.MODE_PRIVATE);
    }

    public static int getMyShape() { return p().getInt("shape", SHAPE_CIRCLE); }
    public static boolean isShareEnabled() { return p().getBoolean("share_shape", false); }
    public static boolean isCleanEnabled() { return p().getBoolean("clean_cache", false); }
    public static void setCleanEnabled(boolean v) { p().edit().putBoolean("clean_cache", v).apply(); }

    public static void setMyShape(int shape) {
        p().edit().putInt("shape", shape).apply();
        if (isShareEnabled()) pushNameMarker();
        notifyUi();
    }

    public static void setShareEnabled(boolean v) {
        p().edit().putBoolean("share_shape", v).apply();
        pushNameMarker();
    }

    private static void notifyUi() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
    }

    /* ---------- drawer order (main screen buttons) ---------- */

    public static void publishDrawer(int[] ids, String[] labels) {
        drawerIds = ids;
        drawerLabels = labels;
    }

    public static int[] getDrawerIds() { return drawerIds; }
    public static String[] getDrawerLabels() { return drawerLabels; }

    public static int[] getDrawerOrder() {
        String s = p().getString("drawer_order", "");
        if (s.isEmpty()) return new int[0];
        String[] parts = s.split(",");
        int[] r = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            try { r[n++] = Integer.parseInt(part.trim()); } catch (Exception ignored) { }
        }
        int[] out = new int[n];
        System.arraycopy(r, 0, out, 0, n);
        return out;
    }

    public static void setDrawerOrder(int[] order) {
        StringBuilder sb = new StringBuilder();
        if (order != null) for (int i = 0; i < order.length; i++) { if (i > 0) sb.append(','); sb.append(order[i]); }
        p().edit().putString("drawer_order", sb.toString()).apply();
        notifyUi();
    }

    public static boolean handleDrawerClick(LaunchActivity activity, int id) {
        if (id != DRAWER_ID) return false;
        activity.presentFragment(new AHbsoluteSettingsActivity());
        return true;
    }

    /* ---------- avatar shapes ---------- */

    public static int shapeFor(TLObject obj) {
        if (!(obj instanceof TLRPC.User)) return SHAPE_CIRCLE;
        TLRPC.User u = (TLRPC.User) obj;
        if (UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId() == u.id) return getMyShape();
        return parseMarker(u.last_name);
    }

    private static int parseMarker(String s) {
        if (s == null) return SHAPE_CIRCLE;
        int n = s.length();
        if (n < 5 || !s.startsWith(MARK, n - 5)) return SHAPE_CIRCLE;
        char hi = s.charAt(n - 2), lo = s.charAt(n - 1);
        if ((hi != B0 && hi != B1) || (lo != B0 && lo != B1)) return SHAPE_CIRCLE;
        return (hi == B1 ? 2 : 0) + (lo == B1 ? 1 : 0);
    }

    public static String stripMarker(String s) {
        if (s == null) return "";
        int n = s.length();
        if (n >= 5 && s.startsWith(MARK, n - 5)) return s.substring(0, n - 5);
        return s;
    }

    private static String markerFor(int shape) {
        if (shape <= 0 || shape > 3) return "";
        return MARK + ((shape & 2) != 0 ? B1 : B0) + ((shape & 1) != 0 ? B1 : B0);
    }

    /** Writes/removes the invisible marker in the user's own last name (opt-in only). */
    public static void pushNameMarker() {
        AHbsoluteNameSync.push();
    }

    public static String markerForShape(int shape) { return markerFor(shape); }

    private static final HashMap<Long, Path> PATHS = new HashMap<>();

    /** Path for a shape in (0,0,w,h); cached to avoid per-frame allocations. */
    public static synchronized Path cachedPath(int shape, float w, float h) {
        int iw = Math.max(1, Math.round(w)), ih = Math.max(1, Math.round(h));
        long key = ((long) shape << 48) | ((long) iw << 24) | ih;
        Path path = PATHS.get(key);
        if (path == null) {
            if (PATHS.size() > 96) PATHS.clear();
            path = buildPath(shape, iw, ih);
            PATHS.put(key, path);
        }
        return path;
    }

    public static Path buildPath(int shape, float w, float h) {
        Path path = new Path();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        switch (shape) {
            case SHAPE_SQUARE: {
                float rad = r * 0.18f;
                path.addRoundRect(new RectF(0, 0, w, h), rad, rad, Path.Direction.CW);
                break;
            }
            case SHAPE_STAR: {
                float inner = r * 0.45f;
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0) ? r : inner;
                    float px = (float) (cx + Math.cos(a) * rr), py = (float) (cy + Math.sin(a) * rr);
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                break;
            }
            case SHAPE_TRIANGLE: {
                path.moveTo(cx, h * 0.05f);
                path.lineTo(w, h * 0.95f);
                path.lineTo(0, h * 0.95f);
                path.close();
                break;
            }
            default:
                path.addCircle(cx, cy, r, Path.Direction.CW);
        }
        return path;
    }

    /* ---------- startup optimisation (opt-in) ---------- */

    public static void onAppStart(Context ctx) {
        try { AHbsoluteStreaks.init(ctx); } catch (Throwable ignored) { }
        try {
            if (!isCleanEnabled()) return;
            final File dir = ctx.getCacheDir();
            new Thread(() -> {
                try { deleteOld(dir, System.currentTimeMillis() - 30L * 86400000L, false); } catch (Throwable ignored) { }
            }, "ahb-clean").start();
        } catch (Throwable ignored) { }
    }

    private static void deleteOld(File f, long limit, boolean deleteSelf) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) for (File c : list) deleteOld(c, limit, true);
            if (deleteSelf) { File[] left = f.listFiles(); if (left == null || left.length == 0) f.delete(); }
        } else if (f.lastModified() < limit) {
            f.delete();
        }
    }
}
