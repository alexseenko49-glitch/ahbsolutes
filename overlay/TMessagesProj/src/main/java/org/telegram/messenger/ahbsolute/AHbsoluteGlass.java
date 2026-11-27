package org.telegram.messenger.ahbsolute;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;

/**
 * "Liquid Glass" message bubbles (patch group "msgglass", Theme's MessageDrawable):
 * the bubble body becomes translucent so the chat wallpaper shows through, then a bright specular highlight on the
 * upper part, a light rim and a soft darker lower edge give it the look of a drop of glass.
 */
public class AHbsoluteGlass {

    private static boolean cached;
    private static long cachedAt;
    private static final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint low = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        rim.setStyle(Paint.Style.STROKE);
        low.setStyle(Paint.Style.STROKE);
    }

    /** read at most twice a second: this runs for every bubble on every frame */
    public static boolean on() {
        long now = SystemClock.uptimeMillis();
        if (now - cachedAt > 500) {
            cachedAt = now;
            try {
                cached = AHbsoluteUi.ctx() != null && AHbsoluteUi.master(AHbsoluteUi.ctx()) && AHbsoluteUi.getBool("msgglass");
            } catch (Throwable e) {
                cached = false;
            }
        }
        return cached;
    }

    /** share of the bubble colour that stays */
    public static float bodyAlpha() {
        return Math.max(25, Math.min(95, AHbsoluteUi.getInt("msgglass_op", 62))) / 100f;
    }

    public static void drawBubble(Canvas canvas, Path path, Rect b, boolean out) {
        try {
            if (b.height() <= 0) return;
            float top = b.top, h = b.height();
            // specular highlight: strong at the very top, gone by ~45% of the height
            shine.setShader(new LinearGradient(0, top, 0, top + Math.min(h, AndroidUtilities.dp(64)) * 0.9f,
                    new int[]{out ? 0x66FFFFFF : 0x80FFFFFF, 0x14FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawPath(path, shine);
            // light rim
            rim.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 1.1f));
            rim.setShader(new LinearGradient(0, top, 0, top + h, new int[]{0x99FFFFFF, 0x33FFFFFF, 0x1AFFFFFF}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawPath(path, rim);
            // soft darker lower edge (refraction)
            low.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 2f));
            low.setShader(new LinearGradient(0, top + h * 0.6f, 0, top + h, 0x00000000, 0x1F000000, Shader.TileMode.CLAMP));
            canvas.save();
            canvas.clipPath(path);
            canvas.drawPath(path, low);
            canvas.restore();
        } catch (Throwable ignored) { }
    }
}
