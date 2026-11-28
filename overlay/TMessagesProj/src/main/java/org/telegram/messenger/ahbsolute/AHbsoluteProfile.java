package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;

/**
 * Profile customization (groups "pwall" and "playout" of apply_patches.py):
 *  - profile wallpaper: your own picture (or a built-in one) behind the whole profile screen
 *  - glass profile: the rounded sections become translucent frosted cards over the wallpaper
 *  - layout: avatar left / centre / right, avatar size, name alignment, name size
 * Every hook is wrapped in try/catch and falls back to Telegram's own value.
 */
public class AHbsoluteProfile {

    public static final int POS_CENTER = 0, POS_LEFT = 1, POS_RIGHT = 2, POS_FOLLOW = 3, POS_BESIDE = 4;

    /* ------------------------------------------------------------ settings ------------------------------------------------------------ */

    public static String wallpaper() { return AHbsoluteUi.getStr("prof_wp", AHbsoluteBanner.OFF); }
    public static void setWallpaper(String id) { AHbsoluteUi.putStr("prof_wp", id); invalidate(); }
    public static boolean glassOn() { return "1".equals(AHbsoluteUi.getStr("prof_glass", "0")); }
    public static void setGlass(boolean v) { AHbsoluteUi.putStr("prof_glass", v ? "1" : "0"); }
    /** opacity of the glass cards, % */
    public static int glassOpacity() { return AHbsoluteUi.getInt("prof_glass_op", 62); }
    /** 0 = sharp, 1..3 = blur strength */
    public static int wallpaperBlur() { return AHbsoluteUi.getInt("prof_wp_blur", 1); }
    public static int wallpaperDim() { return AHbsoluteUi.getInt("prof_wp_dim", 0); }
    public static int avatarPos() { return AHbsoluteUi.getInt("prof_av_pos", POS_CENTER); }
    /** stored value; the default follows the avatar */
    public static int nameAlign() { return AHbsoluteUi.getInt("prof_name_align", POS_FOLLOW); }
    /** where the name really goes: FOLLOW = same side as the avatar */
    static int effectiveNameAlign() {
        int al = nameAlign();
        return al == POS_FOLLOW ? avatarPos() : al;
    }
    /** avatar side; "name beside the avatar" with a centred avatar puts the avatar on the left */
    static int effectiveAvatarPos() {
        int p = avatarPos();
        return p == POS_CENTER && nameAlign() == POS_BESIDE ? POS_LEFT : p;
    }

    /**
     * name + status stand next to the avatar: explicitly, or "with the avatar" while the avatar is on a side
     * (below a side avatar the other half of the header looked empty)
     */
    static boolean besideActive() {
        int al = nameAlign();
        return al == POS_BESIDE || (al == POS_FOLLOW && avatarPos() != POS_CENTER);
    }

    /** ProfileActivity.getHeaderOnlyExtraHeight(): with the name next to the avatar the rows below it move up */
    public static int headerExtra(int def, long userId, long chatId) {
        try {
            if (!besideActive() || !appliesTo(userId, chatId)) return def;
            float k = sizeK(userId, chatId);
            int freed = AndroidUtilities.dp(54) - Math.max(0, Math.round(AndroidUtilities.dp(96) * (k - 1f)));
            return def - Math.max(0, freed);
        } catch (Throwable e) { return def; }
    }
    public static int avatarSize() { return AHbsoluteUi.getInt("prof_av_size", 100); }
    public static int nameSize() { return AHbsoluteUi.getInt("prof_name_size", 100); }
    public static boolean everywhere() { return "all".equals(AHbsoluteUi.getStr("prof_scope", "me")); }
    public static void setEverywhere(boolean v) { AHbsoluteUi.putStr("prof_scope", v ? "all" : "me"); }

    private static boolean on() {
        try { Context c = AHbsoluteUi.ctx(); return c == null || AHbsoluteUi.master(c); } catch (Throwable e) { return true; }
    }

    static boolean appliesTo(long userId, long chatId) {
        if (!on()) return false;
        if (everywhere()) return true;
        if (chatId != 0) return false;
        try { return userId != 0 && userId == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId(); } catch (Throwable e) { return false; }
    }

    /* ------------------------------------------------------------ layout hooks ------------------------------------------------------------ */

    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    /** ProfileActivity.fixAvatarImageInCenter(): x of the avatar (its scale pivot is the left edge) */
    public static float avatarX(float width, float avatarW, long userId, long chatId) {
        float centered = width / 2f - avatarW / 2f;
        try {
            int pos = effectiveAvatarPos();
            if (pos == POS_CENTER || width <= 0 || !appliesTo(userId, chatId)) return centered;
            // the avatar fills the screen when the profile is pulled down: then it must stay centred
            float t = avatarW <= width * 0.45f ? 1f : clamp01((width * 0.75f - avatarW) / (width * 0.30f));
            float target = pos == POS_LEFT ? AndroidUtilities.dp(24) : width - avatarW - AndroidUtilities.dp(24);
            return centered + (target - centered) * t;
        } catch (Throwable e) { return centered; }
    }

    /** name / status x: centred value from Telegram, moved to the chosen side / next to the avatar by the header progress */
    public static float textX(float centered, float viewportWidth, float leftMargin, float textW, float diff, long userId, long chatId) {
        return textX(centered, viewportWidth, leftMargin, textW, diff, Float.NaN, 0, userId, chatId);
    }

    public static float textX(float centered, float viewportWidth, float leftMargin, float textW, float diff, float avX, float avW, long userId, long chatId) {
        try {
            if (viewportWidth <= 0 || !appliesTo(userId, chatId)) return centered;
            int al = nameAlign();
            float target;
            if (besideActive() && !Float.isNaN(avX)) {
                boolean right = effectiveAvatarPos() == POS_RIGHT;
                target = right ? avX - AndroidUtilities.dp(16) - leftMargin - textW : avX + avW + AndroidUtilities.dp(16) - leftMargin;
            } else {
                al = effectiveNameAlign();
                if (al == POS_BESIDE) al = effectiveAvatarPos();
                if (al == POS_CENTER) return centered;
                target = al == POS_LEFT ? AndroidUtilities.dp(24) - leftMargin : viewportWidth - AndroidUtilities.dp(24) - leftMargin - textW;
            }
            return centered + (target - centered) * clamp01(diff);
        } catch (Throwable e) { return centered; }
    }

    /** vertical shift of name and status for "beside the avatar": centred on the avatar instead of below it */
    public static float besideShift(float avatarTop, float avatarH, float nameY, float diff, long userId, long chatId) {
        try {
            if (!besideActive() || !appliesTo(userId, chatId)) return 0;
            float target = avatarTop + avatarH / 2f - AndroidUtilities.dp(22);
            return (target - nameY) * clamp01(diff);
        } catch (Throwable e) { return 0; }
    }

    /** multiplier for the avatar size in the normal (not pulled down) header */
    public static float sizeK(long userId, long chatId) {
        try {
            if (!appliesTo(userId, chatId)) return 1f;
            return Math.max(60, Math.min(140, avatarSize())) / 100f;
        } catch (Throwable e) { return 1f; }
    }

    public static float nameK(long userId, long chatId) {
        try {
            if (!appliesTo(userId, chatId)) return 1f;
            return Math.max(80, Math.min(130, nameSize())) / 100f;
        } catch (Throwable e) { return 1f; }
    }

    /* ------------------------------------------------------------ wallpaper + glass ------------------------------------------------------------ */

    private static String cachedKey;
    private static Bitmap cached;
    private static final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint dimPaint = new Paint();
    private static final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Rect src = new Rect();
    private static final RectF dst = new RectF();
    private static final Path path = new Path();
    private static final float[] radii = new float[8];

    public static void invalidate() { cachedKey = null; cached = null; }

    private static String resolve(String m) {
        if (m == null || AHbsoluteBanner.OFF.equals(m)) return null;
        if (AHbsoluteBanner.THEME.equals(m)) {
            String sel = null;
            try { sel = AHbsoluteThemes.selectedId(AHbsoluteUi.ctx()); } catch (Throwable ignored) { }
            return sel == null || sel.isEmpty() ? AHbsoluteBanner.BUILTIN[0] : sel;
        }
        return m;
    }

    /** wallpaper bitmap, blurred by downscaling (cheap and smooth) */
    static Bitmap bitmap(Context c) {
        String id = resolve(wallpaper());
        if (id == null || c == null) return null;
        int blur = Math.max(0, Math.min(3, wallpaperBlur()));
        String key = id + "/" + blur;
        if (key.equals(cachedKey) && cached != null) return cached;
        Bitmap b = AHbsoluteBanner.load(c, id, AHbsoluteBanner.isUser(id) ? 1 : 2);
        if (b != null && blur > 0) {
            int f = blur == 1 ? 6 : blur == 2 ? 12 : 22;
            int w = Math.max(8, b.getWidth() / f), h = Math.max(8, b.getHeight() / f);
            Bitmap small = Bitmap.createScaledBitmap(b, w, h, true);
            Bitmap smooth = Bitmap.createScaledBitmap(small, Math.max(16, b.getWidth() / 3), Math.max(16, b.getHeight() / 3), true);
            if (small != smooth) small.recycle();
            b.recycle();
            b = smooth;
        }
        cached = b;
        cachedKey = key;
        return b;
    }

    public static boolean glassFor(long userId, long chatId) {
        try { return glassOn() && appliesTo(userId, chatId); } catch (Throwable e) { return false; }
    }

    public static boolean wallpaperFor(long userId, long chatId) {
        try { return resolve(wallpaper()) != null && appliesTo(userId, chatId); } catch (Throwable e) { return false; }
    }

    private static final java.util.WeakHashMap<View, Boolean> wallLists = new java.util.WeakHashMap<>();

    /** SharedMediaLayout.MediaPage: no grey page background inside a profile that shows a wallpaper (Posts, Archive, Media…) */
    public static boolean transparentPage(View page) {
        try {
            if (wallLists.isEmpty()) return false;
            Object p = page.getParent();
            for (int i = 0; i < 14 && p instanceof View; i++) {
                if (wallLists.containsKey(p)) return true;
                p = ((View) p).getParent();
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** ProfileActivity.createView(): turns the list into glass sections when enabled */
    public static void setupList(Object listView, long userId, long chatId) {
        try {
            if (!(listView instanceof View)) return;
            boolean glass = glassFor(userId, chatId), wp = wallpaperFor(userId, chatId);
            if (!glass && !wp) return;
            View v = (View) listView;
            v.setBackgroundColor(Color.TRANSPARENT);
            if (wp) wallLists.put(v, Boolean.TRUE);
            if (glass) {
                java.lang.reflect.Method m = listView.getClass().getMethod("setLegacySections", boolean.class);
                m.invoke(listView, true);
                java.lang.reflect.Field f = findField(listView.getClass(), "ahbGlass");
                if (f != null) f.setBoolean(listView, true);
            }
        } catch (Throwable e) {
            AHbsoluteUi.diag("d_profile", "setupList: " + e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> k, String n) {
        for (; k != null; k = k.getSuperclass()) {
            try { java.lang.reflect.Field f = k.getDeclaredField(n); f.setAccessible(true); return f; } catch (Throwable ignored) { }
        }
        return null;
    }

    /** ProfileActivity content view: wallpaper behind the list (fixed while the cards scroll over it) */
    public static void drawWallpaper(Canvas canvas, View view, long userId, long chatId) {
        try {
            if (!wallpaperFor(userId, chatId)) return;
            Bitmap b = bitmap(view.getContext());
            if (b == null) return;
            drawCover(canvas, b, view.getWidth(), view.getHeight());
        } catch (Throwable ignored) { }
    }

    /** width-fit from the top (matches the banner with position "top"), otherwise centre crop */
    static void drawCover(Canvas canvas, Bitmap b, int w, int h) {
        if (w <= 0 || h <= 0) return;
        float sr = (float) w / h, br = (float) b.getWidth() / b.getHeight();
        if (br > sr) {
            int sw = Math.round(b.getHeight() * sr);
            src.set((b.getWidth() - sw) / 2, 0, (b.getWidth() - sw) / 2 + sw, b.getHeight());
        } else {
            int sh = Math.round(b.getWidth() / sr);
            src.set(0, 0, b.getWidth(), sh);
        }
        dst.set(0, 0, w, h);
        canvas.drawBitmap(b, src, dst, bmpPaint);
        int d = Math.max(0, Math.min(80, wallpaperDim()));
        if (d > 0) {
            dimPaint.setColor(Math.round(255 * d / 100f) << 24);
            canvas.drawRect(0, 0, w, h, dimPaint);
        }
    }

    /** RecyclerListView.drawBackgroundRect() when ahbGlass is set: a frosted translucent card */
    public static void drawGlassSection(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha, int baseColor) {
        float op = Math.max(10, Math.min(100, glassOpacity())) / 100f;
        int a = Math.round(255 * op * Math.max(0f, Math.min(1f, alpha)));
        glassPaint.setColor((baseColor & 0x00FFFFFF) | (a << 24));
        path.rewind();
        radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
        radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
        path.addRoundRect(rect, radii, Path.Direction.CW);
        canvas.drawPath(path, glassPaint);
        // soft light from the top + a hairline edge = the "glass" look
        boolean dark = (Color.red(baseColor) * 299 + Color.green(baseColor) * 587 + Color.blue(baseColor) * 114) / 1000 < 110;
        shinePaint.setShader(new LinearGradient(0, rect.top, 0, rect.top + Math.min(rect.height(), AndroidUtilities.dp(120)),
                Math.round(a * (dark ? 0.10f : 0.35f)) << 24 | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(path, shinePaint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1f, AndroidUtilities.density));
        strokePaint.setColor(dark ? 0x26FFFFFF : 0x59FFFFFF);
        canvas.drawPath(path, strokePaint);
    }
}
