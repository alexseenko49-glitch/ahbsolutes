package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.InputStream;

/**
 * Profile banner: a picture behind the avatar and the name, fully visible on top and slightly more transparent at the bottom.
 * Plus the transparency of the profile header (called from the patched ProfileActivity.TopView, group "profile").
 */
public class AHbsoluteBanner {

    public static final String[] IDS = {"vice", "chess", "absolut", "teg"};

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Rect src = new Rect();
    private static final RectF dst = new RectF();
    private static final Bitmap[] cache = new Bitmap[IDS.length];
    private static int maskH = -1, maskA = -1;

    static {
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    /** share of the header colour that stays visible: 1 = normal, 0 = fully transparent */
    public static float headerAlpha() {
        try { return AHbsoluteUi.profileHeaderAlpha(); } catch (Throwable e) { return 1f; }
    }

    private static Bitmap bitmap(Context c, int i) {
        if (cache[i] != null) return cache[i];
        try (InputStream in = c.getAssets().open("ahb_themes/" + IDS[i] + ".jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            cache[i] = BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable ignored) { }
        return cache[i];
    }

    private static int indexFor(String mode) {
        if (mode == null || mode.equals("off")) return -1;
        if (mode.equals("theme")) {
            String sel = AHbsoluteThemes.selectedId(AHbsoluteUi.ctx());
            if (sel == null || sel.isEmpty()) return 0;
            mode = sel;
        }
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(mode)) return i;
        return 0;
    }

    /** draws the banner into the header area 0..h; w = header width */
    public static void draw(Canvas canvas, int w, int h) {
        try {
            Context c = AHbsoluteUi.ctx();
            if (c == null || w <= 0 || h <= 0) return;
            int idx = indexFor(AHbsoluteUi.bannerMode());
            if (idx < 0) return;
            Bitmap b = bitmap(c, idx);
            if (b == null) return;
            int top = Math.round(255 * AHbsoluteUi.bannerOpacity());
            int bottom = Math.round(top * 0.6f);   // "a bit more transparent" at the bottom
            // centre-crop of the picture to w:h
            float sr = (float) w / h, br = (float) b.getWidth() / b.getHeight();
            if (br > sr) { int sw = Math.round(b.getHeight() * sr); src.set((b.getWidth() - sw) / 2, 0, (b.getWidth() - sw) / 2 + sw, b.getHeight()); }
            else { int sh = Math.round(b.getWidth() / sr); src.set(0, 0, b.getWidth(), sh); }
            dst.set(0, 0, w, h);
            int save = canvas.saveLayer(0, 0, w, h, null);
            canvas.drawBitmap(b, src, dst, paint);
            if (maskH != h || maskA != top * 1000 + bottom) {
                mask.setShader(new LinearGradient(0, 0, 0, h, top << 24, bottom << 24, Shader.TileMode.CLAMP));
                maskH = h; maskA = top * 1000 + bottom;
            }
            canvas.drawRect(0, 0, w, h, mask);
            canvas.restoreToCount(save);
        } catch (Throwable ignored) { }
    }
}
