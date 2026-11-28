package org.telegram.messenger.ahbsolute;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Chat / channel opening animation: the chat grows from a small dot (under the finger) to full size in 0.2 s.
 * "Peek": two quick taps on a chat open it and, as soon as the opening animation has finished, it closes again by itself.
 * Called from patched ActionBarLayout (see apply_patches.py, group "anim") and from AHbsoluteUi (touch tracking).
 */
public class AHbsoluteAnim {

    private static final Handler H = new Handler(Looper.getMainLooper());

    private static float tapX = -1, tapY = -1;       // last finger position (screen)
    private static float firstDownX, firstDownY;
    private static long lastUpTime;
    private static boolean opening;                   // a chat is being opened right now
    private static boolean peekPending;
    private static boolean consuming;                 // swallow the rest of the second tap
    private static boolean pivotSet;

    private static boolean isChat(Object f) { return f != null && "ChatActivity".equals(f.getClass().getSimpleName()); }

    /* ---------------- touch tracking (called from AHbsoluteUi) ---------------- */

    /** true when this DOWN is the second quick tap on a chat that is opening (peek) */
    static boolean onDown(float rx, float ry, long time, float density) {
        tapX = rx; tapY = ry;
        try {
            if (opening && AHbsoluteUi.peekOn() && time - lastUpTime < 500) {
                float dx = rx - firstDownX, dy = ry - firstDownY, lim = 56 * density;
                if (dx * dx + dy * dy < lim * lim) {
                    peekPending = true;
                    consuming = true;
                    return true;
                }
            }
        } catch (Throwable ignored) { }
        firstDownX = rx; firstDownY = ry;
        return false;
    }

    static void onUp(long time) { lastUpTime = time; }

    /** swallow the whole second tap so it does not press something inside the opening chat */
    public static boolean consume(MotionEvent ev) {
        if (!consuming) return false;
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) consuming = false;
        return true;
    }

    /* ---------------- hooks called from the patched ActionBarLayout ---------------- */

    public static void onPresent(Object fragment) {
        try {
            opening = isChat(fragment);
            pivotSet = false;
            peekPending = false;
        } catch (Throwable ignored) { }
    }

    public static float duration(Object newFragment, boolean open, float def) {
        try { if (open && AHbsoluteUi.animOn() && isChat(newFragment)) return 200f; } catch (Throwable ignored) { }
        return def;
    }

    /** returns true when it handled the frame (the caller then skips Telegram's slide) */
    public static boolean dotOpen(View container, Object newFragment, float t) {
        try {
            if (!AHbsoluteUi.animOn() || !isChat(newFragment)) return false;
            if (!pivotSet) {
                int[] loc = new int[2];
                container.getLocationOnScreen(loc);
                float px = tapX >= 0 ? tapX - loc[0] : container.getWidth() / 2f;
                float py = tapY >= 0 ? tapY - loc[1] : container.getHeight() / 2f;
                container.setPivotX(px);
                container.setPivotY(py);
                pivotSet = true;
            }
            float s = 0.02f + 0.98f * Math.max(0f, Math.min(1f, t));
            container.setScaleX(s);
            container.setScaleY(s);
            container.setTranslationX(0);
            container.setAlpha(Math.min(1f, t * 3f));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void onOpened(final ActionBarLayout layout, final BaseFragment fragment) {
        try {
            opening = false;
            if (peekPending && isChat(fragment)) {
                peekPending = false;
                // a short look, then the opening animation plays backwards: the chat shrinks into the dot it came from
                H.postDelayed(new Runnable() {
                    @Override public void run() { reverseClose(layout, fragment); }
                }, 380);
            }
        } catch (Throwable ignored) { }
    }

    private static void reverseClose(final ActionBarLayout layout, final BaseFragment fragment) {
        final View v = fragment.getFragmentView();
        final View target = v != null && v.getParent() instanceof View ? (View) v.getParent() : v;
        if (target == null) {
            try { layout.closeLastFragment(true); } catch (Throwable ignored) { }
            return;
        }
        try {
            int[] loc = new int[2];
            target.getLocationOnScreen(loc);
            target.setPivotX(firstDownX > 0 ? firstDownX - loc[0] : target.getWidth() / 2f);
            target.setPivotY(firstDownY > 0 ? firstDownY - loc[1] : target.getHeight() / 2f);
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(1f, 0f);
            va.setDuration(240);
            va.setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f));
            va.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float sc = 0.02f + 0.98f * t;
                target.setScaleX(sc);
                target.setScaleY(sc);
                target.setAlpha(Math.min(1f, t * 3f));
            });
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    try { layout.closeLastFragment(false); } catch (Throwable ignored) { }
                    target.setScaleX(1f);
                    target.setScaleY(1f);
                    target.setAlpha(1f);
                }
            });
            va.start();
        } catch (Throwable e) {
            try { layout.closeLastFragment(true); } catch (Throwable ignored) { }
        }
    }
}
