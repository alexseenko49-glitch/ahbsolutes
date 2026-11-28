package org.telegram.messenger.ahbsolute;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;

/**
 * Main screen (chat list):
 *  - spacing between chats (any style)
 *  - Absolute Design 1 look: every chat is a floating rounded card, glass cards over the design wallpaper
 *  - the "AHbsolute" title of the top bar (instead of the Telegram logo), with the AHbsolute gradient in Design 1
 * Attached to every RecyclerListView (it only acts on lists that contain DialogCell rows).
 */
public class AHbsoluteChats extends RecyclerView.ItemDecoration {

    private static final java.util.WeakHashMap<View, Boolean> clipped = new java.util.WeakHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /* ------------------------------------------------------------ settings ------------------------------------------------------------ */

    /** extra space between chats, dp */
    public static int spacing() { return AHbsoluteUi.getInt("chat_gap", 0); }
    public static boolean cards() { return AHbsoluteUi.getBool("chatcards"); }
    public static boolean mainWallpaper() { return AHbsoluteUi.getBool("mainwall"); }
    /** card radius, dp */
    public static int radius() { return AHbsoluteUi.getInt("chat_radius", 18); }

    private static boolean on() {
        try { return AHbsoluteUi.ctx() == null || AHbsoluteUi.master(AHbsoluteUi.ctx()); } catch (Throwable e) { return true; }
    }

    /** called from the RecyclerListView constructors (through AHbsoluteBlur.attach) */
    public static void attach(RecyclerView rv) {
        try { if (rv != null) rv.addItemDecoration(new AHbsoluteChats()); } catch (Throwable ignored) { }
    }

    /* ------------------------------------------------------------ decoration ------------------------------------------------------------ */

    @Override
    public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!(view instanceof DialogCell)) return;
        try {
            boolean on = on();
            boolean cards = on && cards();
            int gap = on ? AndroidUtilities.dp(Math.max(0, Math.min(24, spacing()))) : 0;
            if (cards) gap = Math.max(gap, AndroidUtilities.dp(6));
            out.top = gap / 2;
            out.bottom = gap - gap / 2;
            if (cards) out.left = out.right = AndroidUtilities.dp(10);
            DialogCell cell = (DialogCell) view;
            if (cards) cell.useSeparator = false;
            clip(view, cards);
        } catch (Throwable ignored) { }
    }

    /** rounded clipping of the row itself, so pinned / swipe backgrounds follow the card shape */
    private static void clip(View v, boolean on) {
        if (on) {
            if (!clipped.containsKey(v)) {
                v.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(radius()));
                    }
                });
                clipped.put(v, Boolean.TRUE);
            }
            v.setClipToOutline(true);
        } else if (clipped.containsKey(v)) {
            v.setClipToOutline(false);
            v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            clipped.remove(v);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        try {
            if (!on()) return;
            boolean cards = cards(), wall = mainWallpaper();
            if (!cards && !wall) return;
            boolean hasDialogs = false;
            for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChildAt(i) instanceof DialogCell) { hasDialogs = true; break; }
            if (!hasDialogs) return;
            // page behind the chats: the design wallpaper (fixed, the cards slide over it) or the grey page colour
            Bitmap b = wall ? AHbsoluteProfile.bitmap(parent.getContext()) : null;
            if (b != null) {
                parent.getLocationOnScreen(tmpLoc);
                wallRect[0] = tmpLoc[0]; wallRect[1] = tmpLoc[1]; wallRect[2] = parent.getWidth(); wallRect[3] = parent.getHeight();
                wallKnown = true;
                AHbsoluteProfile.drawCover(canvas, b, parent.getWidth(), parent.getHeight());
            } else if (cards) {
                canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
            }
            if (!cards) return;
            int color = Theme.getColor(Theme.key_windowBackgroundWhite);
            float r = AndroidUtilities.dp(radius());
            for (int i = 0; i < parent.getChildCount(); i++) {
                View v = parent.getChildAt(i);
                if (!(v instanceof DialogCell) || v.getVisibility() != View.VISIBLE) continue;
                rect.set(v.getLeft() + v.getTranslationX(), v.getTop() + v.getTranslationY(), v.getRight() + v.getTranslationX(), v.getBottom() + v.getTranslationY());
                if (b != null) {
                    AHbsoluteProfile.drawGlassSection(canvas, rect, r, r, v.getAlpha(), color);
                } else {
                    paint.setColor(color);
                    paint.setAlpha(Math.round(255 * v.getAlpha()));
                    canvas.drawRoundRect(rect, r, r, paint);
                }
            }
        } catch (Throwable ignored) { }
    }

    /* ------------------------------------------------------------ top bar (DialogsActivity header) ------------------------------------------------------------ */

    private static final int[] wallRect = new int[4];
    private static boolean wallKnown;
    private final int[] tmpLoc = new int[2];
    private static final int[] hostLoc = new int[2];
    private static final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint pillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF pillRect = new RectF();

    public static boolean compactBar() { return AHbsoluteUi.getBool("topbar"); }
    /** size of the bar, % */
    public static int barSize() { return AHbsoluteUi.getInt("bar_size", 92); }
    /** corner radius, dp */
    public static int barRadius() { return AHbsoluteUi.getInt("bar_radius", 24); }
    /** side margin, dp */
    public static int barMargin() { return AHbsoluteUi.getInt("bar_margin", 10); }

    private static final Paint fadeMask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static float fadeMaskStart = -1, fadeMaskH = -1;

    static {
        fadeMask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
    }

    /** the wallpaper exactly where the chat list draws it (fixed to the screen) */
    private static void drawWallAligned(Canvas canvas, View host, Bitmap b) {
        host.getLocationOnScreen(hostLoc);
        int lx = wallKnown ? wallRect[0] : hostLoc[0], ly = wallKnown ? wallRect[1] : hostLoc[1];
        int lw = wallKnown ? wallRect[2] : host.getWidth(), lh = wallKnown ? wallRect[3] : host.getHeight();
        canvas.save();
        canvas.translate(lx - hostLoc[0], ly - hostLoc[1]);
        AHbsoluteProfile.drawCover(canvas, b, lw, lh);
        canvas.restore();
    }

    /**
     * DialogsActivityTopBubblesFadeView: Telegram fills the top of the chat list with the theme colour as soon as the list
     * is scrolled (that made the header dark). With "wallpaper behind chats" the very same wallpaper is drawn there
     * instead, so the header matches the wallpaper at any scroll position. Returns false = Telegram draws its fade.
     */
    public static boolean drawFade(Canvas canvas, View fade, float fadeStart, float fadeHeight) {
        try {
            if (!on() || !mainWallpaper()) return false;
            Bitmap b = AHbsoluteProfile.bitmap(fade.getContext());
            if (b == null) return false;
            float bottom = fadeStart + Math.max(0, fadeHeight);
            if (bottom <= 0) return true;
            int save = canvas.saveLayer(0, 0, fade.getWidth(), bottom, null);
            drawWallAligned(canvas, fade, b);
            if (fadeHeight > 0) {
                if (fadeMaskStart != fadeStart || fadeMaskH != fadeHeight) {
                    fadeMask.setShader(new LinearGradient(0, fadeStart, 0, bottom, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
                    fadeMaskStart = fadeStart;
                    fadeMaskH = fadeHeight;
                }
                canvas.drawRect(0, fadeStart, fade.getWidth(), bottom, fadeMask);
            }
            canvas.restoreToCount(save);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * DialogsActivity content view, right before the action bar (title, search, menu) is drawn: with the compact bar the row
     * gets smaller around its centre and sits in a rounded capsule (glass over the wallpaper). Size, corners and side
     * margins are set in AHbsolute → Кастомизация.
     */
    public static void drawBar(Canvas canvas, View host, View actionBar) {
        try {
            if (actionBar == null) return;
            if (!on() || !compactBar() || actionBar.getVisibility() != View.VISIBLE || actionBar.getHeight() <= 0) { resetBar(actionBar); return; }
            int sb = actionBar instanceof org.telegram.ui.ActionBar.ActionBar && ((org.telegram.ui.ActionBar.ActionBar) actionBar).getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
            float k = Math.max(70, Math.min(100, barSize())) / 100f;
            float pivotY = sb + (actionBar.getHeight() - sb) / 2f;
            if (actionBar.getPivotY() != pivotY) actionBar.setPivotY(pivotY);
            if (actionBar.getPivotX() != actionBar.getWidth() / 2f) actionBar.setPivotX(actionBar.getWidth() / 2f);
            if (actionBar.getScaleX() != k) { actionBar.setScaleX(k); actionBar.setScaleY(k); }
            float rowH = (actionBar.getHeight() - sb) * k;
            float cy = actionBar.getY() + pivotY;
            float m = AndroidUtilities.dp(Math.max(0, Math.min(40, barMargin())));
            pillRect.set(m, cy - rowH / 2f + AndroidUtilities.dp(3), host.getWidth() - m, cy + rowH / 2f - AndroidUtilities.dp(3));
            float r = Math.min(pillRect.height() / 2f, AndroidUtilities.dp(Math.max(0, barRadius())));
            int base = Theme.getColor(Theme.key_windowBackgroundWhite);
            float alpha = actionBar.getAlpha();
            if (mainWallpaper() && AHbsoluteProfile.bitmap(host.getContext()) != null) {
                AHbsoluteProfile.drawGlassSection(canvas, pillRect, r, r, alpha, base);
            } else {
                pill.setColor(base);
                pill.setAlpha(Math.round(255 * alpha));
                pill.setShadowLayer(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(2), 0x22000000);
                canvas.drawRoundRect(pillRect, r, r, pill);
                pillStroke.setStyle(Paint.Style.STROKE);
                pillStroke.setStrokeWidth(Math.max(1f, AndroidUtilities.density * 0.8f));
                pillStroke.setColor(0x14000000);
                canvas.drawRoundRect(pillRect, r, r, pillStroke);
            }
        } catch (Throwable ignored) { }
    }

    private static void resetBar(View actionBar) {
        if (actionBar != null && actionBar.getScaleX() != 1f) { actionBar.setScaleX(1f); actionBar.setScaleY(1f); }
    }

    /* ------------------------------------------------------------ top bar title ------------------------------------------------------------ */

    /** gradient text, the AHbsolute signature (violet → pink) */
    private static class GradientSpan extends CharacterStyle implements UpdateAppearance {
        private final int variant;
        GradientSpan(int variant) { this.variant = variant; }
        @Override public void updateDrawState(TextPaint tp) {
            float w = tp.measureText("AHbsolute");
            int[] cols = variant == 2 ? new int[]{0xFF14B8A6, 0xFF22D3EE, 0xFF818CF8} : new int[]{0xFF8B5CFF, 0xFFC86BFA, 0xFFFF6FA8};
            tp.setShader(new LinearGradient(0, 0, w, 0, cols, null, Shader.TileMode.CLAMP));
            tp.setFakeBoldText(true);
        }
    }

    /**
     * DialogsActivity: the title that Telegram covers with its logo picture. Instead of the logo AHbsolute shows the text
     * "AHbsolute" (plain in the regular look, gradient in Absolute Design 1).
     */
    public static void styleTitle(SpannableStringBuilder ssb) {
        try {
            ssb.clear();
            ssb.append("AHbsolute");
            int design = 0;
            try { design = AHbsoluteDesign.variant(AHbsoluteUi.ctx()); } catch (Throwable ignored) { }
            if (design != 0) ssb.setSpan(new GradientSpan(design), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            else ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignored) { }
    }
}
