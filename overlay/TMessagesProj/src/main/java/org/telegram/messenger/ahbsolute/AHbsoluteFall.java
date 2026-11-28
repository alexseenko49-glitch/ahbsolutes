package org.telegram.messenger.ahbsolute;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;
import java.util.Random;

/**
 * Text deletion animation (patch group "fall", ChatActivityEnterView): letters you erase in the message field jump out,
 * fall down, land on the messages below their path (or on the chat right above the input field), bounce a little and
 * fade away within 2 seconds. Switch: AHbsolute → Сообщения → «Падающие буквы».
 */
public class AHbsoluteFall {

    public static boolean on() {
        try { return AHbsoluteUi.ctx() != null && AHbsoluteUi.master(AHbsoluteUi.ctx()) && AHbsoluteUi.getBool("textfall"); } catch (Throwable e) { return false; }
    }

    /** called once for the message field */
    public static void attach(final EditText et) {
        if (et == null) return;
        try { AHbsoluteCounter.attach(et); } catch (Throwable ignored) { }
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                try {
                    // only real erasing: nothing typed instead, not the whole field cleared at once (sending / clearing)
                    if (after != 0 || count <= 0 || count > 40 || !on()) return;
                    if (count == s.length() && count > 2) return;
                    spawn(et, s, start, count);
                } catch (Throwable ignored) { }
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    /* ------------------------------------------------------------ particles ------------------------------------------------------------ */

    private static class Letter {
        String ch;
        float x, y, vx, vy, rot, vr, w;
        boolean resting;
        long restAt;
        float startX, startY;
    }

    private static class Surface { float l, t, r; }

    private static final Random R = new Random();

    private static Activity activity(Context c) {
        for (int i = 0; i < 10 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            c = c instanceof ContextWrapper ? ((ContextWrapper) c).getBaseContext() : null;
        }
        return null;
    }

    private static void spawn(EditText et, CharSequence s, int start, int count) {
        Layout layout = et.getLayout();
        Activity a = activity(et.getContext());
        if (layout == null || a == null) return;
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        int[] loc = new int[2];
        et.getLocationOnScreen(loc);
        float baseX = loc[0] + et.getTotalPaddingLeft() - et.getScrollX();
        float baseY = loc[1] + et.getTotalPaddingTop() - et.getScrollY();
        TextPaint paint = new TextPaint(et.getPaint());
        FallDrawable fd = FallDrawable.of(decor, paint, loc[1] - AndroidUtilities.dp(4));
        int end = Math.min(s.length(), start + count);
        int i = start;
        while (i < end) {
            int cp = Character.codePointAt(s, i);
            int n = Character.charCount(cp);
            String ch = new String(Character.toChars(cp));
            if (!Character.isWhitespace(cp)) {
                int line = layout.getLineForOffset(i);
                Letter l = new Letter();
                l.ch = ch;
                l.w = paint.measureText(ch);
                l.x = baseX + layout.getPrimaryHorizontal(i) + l.w / 2f;
                l.y = baseY + layout.getLineBaseline(line);
                l.startX = l.x; l.startY = l.y;
                // jump up and a little sideways, the later letters a bit stronger
                l.vy = -AndroidUtilities.dp(620 + R.nextInt(520));
                l.vx = AndroidUtilities.dp(R.nextInt(320) - 160);
                l.vr = R.nextInt(720) - 360;
                fd.add(l);
            }
            i += n;
        }
    }

    /** one overlay per window: draws and moves all falling letters */
    private static class FallDrawable extends Drawable implements Choreographer.FrameCallback {
        private static final ArrayList<FallDrawable> live = new ArrayList<>();
        private final ViewGroup decor;
        private final TextPaint paint;
        private final ArrayList<Letter> letters = new ArrayList<>();
        private final ArrayList<Surface> surfaces = new ArrayList<>();
        private float floorY;
        private long lastFrame;
        private boolean running;
        private final int[] tmp = new int[2];

        private FallDrawable(ViewGroup decor, TextPaint paint) { this.decor = decor; this.paint = paint; }

        static FallDrawable of(ViewGroup decor, TextPaint paint, float floor) {
            FallDrawable f = null;
            for (FallDrawable x : live) if (x.decor == decor) f = x;
            if (f == null) {
                f = new FallDrawable(decor, paint);
                f.setBounds(0, 0, decor.getWidth(), decor.getHeight());
                decor.getOverlay().add(f);
                live.add(f);
            }
            f.setBounds(0, 0, decor.getWidth(), decor.getHeight());
            f.paint.set(paint);
            f.floorY = floor;
            f.collectSurfaces();
            return f;
        }

        void add(Letter l) {
            letters.add(l);
            if (!running) {
                running = true;
                lastFrame = 0;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        /** tops of the message bubbles on screen */
        private void collectSurfaces() {
            surfaces.clear();
            try { scan(decor); } catch (Throwable ignored) { }
        }

        private void scan(View v) {
            if (v.getVisibility() != View.VISIBLE) return;
            if (v instanceof ChatMessageCell) {
                ChatMessageCell c = (ChatMessageCell) v;
                c.getLocationOnScreen(tmp);
                Surface s = new Surface();
                s.l = tmp[0] + c.getBackgroundDrawableLeft();
                s.r = tmp[0] + c.getBackgroundDrawableRight();
                s.t = tmp[1] + c.getBackgroundDrawableTop() + AndroidUtilities.dp(2);
                if (s.r > s.l) surfaces.add(s);
                return;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) scan(g.getChildAt(i));
            }
        }

        @Override public void doFrame(long frameTimeNanos) {
            long now = frameTimeNanos / 1000000L;
            float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float g = AndroidUtilities.dp(2600);
            for (int i = letters.size() - 1; i >= 0; i--) {
                Letter l = letters.get(i);
                if (!l.resting) {
                    float py = l.y;
                    l.vy += g * dt;
                    l.x += l.vx * dt;
                    l.y += l.vy * dt;
                    l.rot += l.vr * dt;
                    if (l.vy > 0) {
                        float land = Float.NaN;
                        for (Surface s : surfaces) {
                            // only bubbles above the field the letter came from
                            if (s.t < l.startY - AndroidUtilities.dp(20) && l.x >= s.l && l.x <= s.r && py <= s.t && l.y >= s.t) {
                                if (Float.isNaN(land) || s.t < land) land = s.t;
                            }
                        }
                        if (Float.isNaN(land) && l.y >= floorY && py <= floorY) land = floorY;
                        if (Float.isNaN(land) && l.y > floorY + AndroidUtilities.dp(400)) land = l.y; // safety
                        if (!Float.isNaN(land)) {
                            l.y = land;
                            l.vy = -l.vy * 0.28f;
                            l.vx *= 0.5f;
                            l.vr *= 0.4f;
                            if (Math.abs(l.vy) < AndroidUtilities.dp(130)) {
                                l.resting = true;
                                l.restAt = now;
                                l.vy = 0;
                                l.vx = 0;
                            }
                        }
                    }
                } else if (now - l.restAt > 2000) {
                    letters.remove(i);
                }
            }
            invalidateSelf();
            if (letters.isEmpty()) {
                running = false;
                try { decor.getOverlay().remove(this); } catch (Throwable ignored) { }
                live.remove(this);
            } else {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        @Override public void draw(Canvas canvas) {
            decor.getLocationOnScreen(tmp);
            long now = lastFrame;
            int baseAlpha = paint.getAlpha();
            for (Letter l : letters) {
                float a = l.resting ? Math.max(0f, 1f - (now - l.restAt) / 2000f) : 1f;
                paint.setAlpha(Math.round(baseAlpha * a));
                canvas.save();
                canvas.translate(l.x - tmp[0], l.y - tmp[1]);
                canvas.rotate(l.rot % 360);
                canvas.drawText(l.ch, -l.w / 2f, 0, paint);
                canvas.restore();
            }
            paint.setAlpha(baseAlpha);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
