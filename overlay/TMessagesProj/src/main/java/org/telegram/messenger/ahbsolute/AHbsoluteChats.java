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

    /* ------------------------------------------------------------ top bar title ------------------------------------------------------------ */

    /** gradient text, the AHbsolute signature (violet → pink) */
    private static class GradientSpan extends CharacterStyle implements UpdateAppearance {
        @Override public void updateDrawState(TextPaint tp) {
            float w = tp.measureText("AHbsolute");
            tp.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xFF8B5CFF, 0xFFC86BFA, 0xFFFF6FA8}, null, Shader.TileMode.CLAMP));
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
            boolean design = false;
            try { design = AHbsoluteDesign.isOn(AHbsoluteUi.ctx()); } catch (Throwable ignored) { }
            if (design) ssb.setSpan(new GradientSpan(), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            else ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignored) { }
    }
}
