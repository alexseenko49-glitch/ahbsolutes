package org.telegram.messenger.ahbsolute;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Absolute Design 2 «Aurora» — the look that makes the design new, not just recoloured:
 *  - living wallpaper: the northern lights slowly drift and breathe behind chats, profile and settings
 *  - aurora edges: every glass card gets a thin teal → cyan → indigo rim that slowly flows around it
 *  - unread chats glow: a brighter, thicker rim and a soft halo
 *  - aurora rim on your own Liquid Glass messages
 */
public class AHbsoluteAurora {

    private static final int[] AURORA = {0xFF14B8A6, 0xFF22D3EE, 0xFF818CF8, 0xFF14B8A6};
    private static final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final float[] radii = new float[8];
    private static final Matrix m = new Matrix();
    private static boolean cachedOn;
    private static long cachedAt;

    static {
        edge.setStyle(Paint.Style.STROKE);
        halo.setStyle(Paint.Style.STROKE);
    }

    /** Design 2 is active (read at most twice a second — called while drawing) */
    public static boolean on() {
        long now = SystemClock.uptimeMillis();
        if (now - cachedAt > 500) {
            cachedAt = now;
            try { cachedOn = AHbsoluteUi.ctx() != null && AHbsoluteUi.master(AHbsoluteUi.ctx()) && AHbsoluteDesign.variant(AHbsoluteUi.ctx()) == 2; }
            catch (Throwable e) { cachedOn = false; }
        }
        return cachedOn;
    }

    public static boolean living() { return on() && AHbsoluteUi.getBool("livewall"); }

    private static float phase(long period) { return (SystemClock.uptimeMillis() % period) / (float) period; }

    /** the flowing gradient rim of a card */
    public static void drawEdge(Canvas canvas, RectF r, float topRadius, float bottomRadius, float alpha, boolean strong) {
        if (r.width() <= 0 || r.height() <= 0) return;
        float p = phase(9000);
        float shift = r.width() * 2f * p;
        edge.setShader(new LinearGradient(r.left - r.width() + shift, r.top, r.left + shift, r.bottom, AURORA, null, Shader.TileMode.MIRROR));
        edge.setStrokeWidth(AndroidUtilities.dp(strong ? 2f : 1.1f));
        edge.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha)) * (strong ? 1f : 0.75f)));
        path.rewind();
        radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
        radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
        path.addRoundRect(r, radii, Path.Direction.CW);
        if (strong) {
            halo.setShader(edge.getShader());
            halo.setStrokeWidth(AndroidUtilities.dp(7));
            halo.setAlpha(Math.round(60 * alpha * (0.7f + 0.3f * (float) Math.sin(phase(2400) * Math.PI * 2))));
            canvas.drawPath(path, halo);
        }
        canvas.drawPath(path, edge);
    }

    /** the living wallpaper: slow drift and breathing scale, same geometry everywhere so screens line up */
    public static void drawLiving(Canvas canvas, Bitmap b, int w, int h) {
        float t = phase(26000) * (float) Math.PI * 2f;
        float s = 1.10f + 0.03f * (float) Math.sin(t * 2f);
        float dx = (float) Math.sin(t) * w * 0.035f, dy = (float) Math.cos(t * 0.75f) * h * 0.02f;
        canvas.save();
        canvas.translate(w / 2f + dx, h / 2f + dy);
        canvas.scale(s, s);
        canvas.translate(-w / 2f, -h / 2f);
        AHbsoluteProfile.drawCover(canvas, b, w, h);
        canvas.restore();
    }

    /** keeps a living wallpaper moving (~25 fps, only while Design 2 + living wallpaper are on) */
    public static void tick(View v) {
        if (v != null && living()) v.postInvalidateDelayed(40);
    }
}
