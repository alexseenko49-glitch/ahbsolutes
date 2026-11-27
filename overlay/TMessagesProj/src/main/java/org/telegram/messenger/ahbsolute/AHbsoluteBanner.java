package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.UserConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Profile banners.
 *  - built-in pictures (assets/ahb_themes/*.jpg) and pictures the user uploads from the gallery (files/ahb_banners/*.jpg)
 *  - drawn behind the avatar and the name in the profile header (patched ProfileActivity.TopView, group "profile")
 *  - everything is configured in "AHbsolute ⚙" / Settings → AHbsolute: brightness, darkening, fade, position, where to show.
 * The banner is local: Telegram has no field for it, so only this client shows it.
 */
public class AHbsoluteBanner {

    public static final String[] BUILTIN = {"vice", "chess", "absolut", "teg"};
    public static final String[] BUILTIN_NAMES = {"Vice", "Chess", "Absolut", "Teg"};
    public static final String OFF = "off", THEME = "theme";
    private static final String USER_PREFIX = "u_";
    private static final int MAX_SIDE = 1600;

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint dim = new Paint();
    private static final Rect src = new Rect();
    private static final RectF dst = new RectF();
    private static final Handler H = new Handler(Looper.getMainLooper());

    private static String cachedKey;
    private static Bitmap cachedBitmap;
    private static int maskH = -1;
    private static long maskKey = -1;

    static {
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    /* ------------------------------------------------------------ settings ------------------------------------------------------------ */

    public static String mode() { return AHbsoluteUi.getStr("banner", OFF); }
    public static void setMode(String m) { AHbsoluteUi.putStr("banner", m); }
    public static int brightness() { return AHbsoluteUi.getInt("banner_op", 100); }
    public static int darken() { return AHbsoluteUi.getInt("banner_dim", 15); }
    public static int fade() { return AHbsoluteUi.getInt("banner_fade", 40); }
    /** 0 = top, 50 = centre, 100 = bottom */
    public static int position() { return AHbsoluteUi.getInt("banner_pos", 50); }
    public static boolean everywhere() { return "all".equals(AHbsoluteUi.getStr("banner_scope", "me")); }
    public static void setEverywhere(boolean v) { AHbsoluteUi.putStr("banner_scope", v ? "all" : "me"); }

    /* ------------------------------------------------------------ files ------------------------------------------------------------ */

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "ahb_banners");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static boolean isUser(String id) { return id != null && id.startsWith(USER_PREFIX); }

    /** ids of uploaded banners, oldest first */
    public static ArrayList<String> userBanners(Context c) {
        ArrayList<String> out = new ArrayList<>();
        try {
            File[] fs = dir(c).listFiles();
            if (fs == null) return out;
            Arrays.sort(fs, (a, b) -> a.getName().compareTo(b.getName()));
            for (File f : fs) {
                String n = f.getName();
                if (n.startsWith(USER_PREFIX) && n.endsWith(".jpg")) out.add(n.substring(0, n.length() - 4));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static void delete(Context c, String id) {
        if (!isUser(id)) return;
        try { new File(dir(c), id + ".jpg").delete(); } catch (Throwable ignored) { }
        if (id.equals(mode())) setMode(OFF);
        cachedKey = null;
        cachedBitmap = null;
    }

    public interface ImportCallback { void done(String idOrNull, String error); }

    /** copies a picture chosen in the gallery into the app (downscaled, EXIF rotation applied); callback on the main thread */
    public static void importFromUri(final Context ctx, final Uri uri, final ImportCallback cb) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            String id = null, err = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
                if (o.outWidth <= 0 || o.outHeight <= 0) throw new IllegalStateException("not an image");
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap b;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, o2); }
                if (b == null) throw new IllegalStateException("decode failed");
                int rot = 0;
                if (android.os.Build.VERSION.SDK_INT >= 24) try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    int or = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    rot = or == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : or == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : or == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
                } catch (Throwable ignored) { }
                float scale = Math.min(1f, (float) MAX_SIDE / Math.max(b.getWidth(), b.getHeight()));
                if (rot != 0 || scale < 1f) {
                    Matrix m = new Matrix();
                    m.postScale(scale, scale);
                    m.postRotate(rot);
                    Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                    if (r != b) b.recycle();
                    b = r;
                }
                String newId = USER_PREFIX + System.currentTimeMillis();
                File f = new File(dir(c), newId + ".jpg");
                try (FileOutputStream out = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 90, out); }
                b.recycle();
                id = newId;
            } catch (Throwable e) {
                err = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
            }
            final String fid = id, ferr = err;
            H.post(() -> { if (cb != null) cb.done(fid, ferr); });
        }, "ahb-banner-import").start();
    }

    /* ------------------------------------------------------------ bitmaps ------------------------------------------------------------ */

    /** resolves "theme" to the selected AHbsolute theme picture; returns null for "off" */
    private static String resolve(String m) {
        if (m == null || OFF.equals(m)) return null;
        if (THEME.equals(m)) {
            String sel = null;
            try { sel = AHbsoluteThemes.selectedId(AHbsoluteUi.ctx()); } catch (Throwable ignored) { }
            return sel == null || sel.isEmpty() ? BUILTIN[0] : sel;
        }
        return m;
    }

    public static Bitmap load(Context c, String id, int sample) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            if (isUser(id)) {
                File f = new File(dir(c), id + ".jpg");
                return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath(), o) : null;
            }
            try (InputStream in = c.getAssets().open("ahb_themes/" + id + ".jpg")) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Throwable e) {
            return null;
        }
    }

    private static Bitmap current(Context c) {
        String id = resolve(mode());
        if (id == null) return null;
        long stamp = 0;
        if (isUser(id)) { try { stamp = new File(dir(c), id + ".jpg").lastModified(); } catch (Throwable ignored) { } }
        String key = id + "@" + stamp;
        if (key.equals(cachedKey) && cachedBitmap != null) return cachedBitmap;
        cachedBitmap = load(c, id, isUser(id) ? 1 : 2);
        cachedKey = key;
        return cachedBitmap;
    }

    /* ------------------------------------------------------------ drawing ------------------------------------------------------------ */

    private static boolean visibleFor(long userId, long chatId) {
        if (everywhere()) return true;
        if (chatId != 0) return false;
        try { return userId != 0 && userId == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId(); } catch (Throwable e) { return false; }
    }

    /** share of the header colour that stays visible: 1 = normal, 0 = fully transparent (only where a banner is shown) */
    public static float headerAlpha(long userId, long chatId) {
        try {
            if (resolve(mode()) == null || !visibleFor(userId, chatId)) return 1f;
            return AHbsoluteUi.profileHeaderAlpha();
        } catch (Throwable e) { return 1f; }
    }

    public static float headerAlpha() { return headerAlpha(0, 0); }

    /** called by the patched ProfileActivity.TopView: draws the banner into the header area 0..h */
    public static void draw(Canvas canvas, int w, int h, long userId, long chatId) {
        try {
            Context c = AHbsoluteUi.ctx();
            if (c == null || w <= 0 || h <= 0 || !AHbsoluteUi.master(c) || !visibleFor(userId, chatId)) return;
            Bitmap b = current(c);
            if (b == null) return;
            drawBitmap(canvas, b, w, h);
        } catch (Throwable ignored) { }
    }

    /** old hook signature (shows the banner on every profile) */
    public static void draw(Canvas canvas, int w, int h) {
        try {
            Context c = AHbsoluteUi.ctx();
            if (c == null || w <= 0 || h <= 0 || !AHbsoluteUi.master(c)) return;
            Bitmap b = current(c);
            if (b != null) drawBitmap(canvas, b, w, h);
        } catch (Throwable ignored) { }
    }

    /** preview in the settings screen (ignores the "where to show" option) */
    public static boolean drawPreview(Context c, Canvas canvas, int w, int h) {
        try {
            Bitmap b = current(c);
            if (b == null) return false;
            drawBitmap(canvas, b, w, h);
            return true;
        } catch (Throwable e) { return false; }
    }

    private static void drawBitmap(Canvas canvas, Bitmap b, int w, int h) {
        int top = Math.round(255 * Math.max(0, Math.min(100, brightness())) / 100f);
        int bottom = Math.round(top * (1f - Math.max(0, Math.min(100, fade())) / 100f));
        // centre-crop of the picture to w:h, vertical position chosen by the user
        float sr = (float) w / h, br = (float) b.getWidth() / b.getHeight();
        if (br > sr) {
            int sw = Math.round(b.getHeight() * sr);
            src.set((b.getWidth() - sw) / 2, 0, (b.getWidth() - sw) / 2 + sw, b.getHeight());
        } else {
            int sh = Math.round(b.getWidth() / sr);
            int y = Math.round((b.getHeight() - sh) * Math.max(0, Math.min(100, position())) / 100f);
            src.set(0, y, b.getWidth(), y + sh);
        }
        dst.set(0, 0, w, h);
        int save = canvas.saveLayer(0, 0, w, h, null);
        canvas.drawBitmap(b, src, dst, paint);
        int d = Math.max(0, Math.min(90, darken()));
        if (d > 0) {
            dim.setColor(Math.round(255 * d / 100f) << 24);
            canvas.drawRect(0, 0, w, h, dim);
        }
        long key = top * 1000L + bottom;
        if (maskH != h || maskKey != key) {
            mask.setShader(new LinearGradient(0, 0, 0, h, top << 24, bottom << 24, Shader.TileMode.CLAMP));
            maskH = h;
            maskKey = key;
        }
        canvas.drawRect(0, 0, w, h, mask);
        canvas.restoreToCount(save);
    }

    /** forget the cached picture (after the settings changed) */
    public static void invalidate() {
        cachedKey = null;
        maskKey = -1;
    }
}
