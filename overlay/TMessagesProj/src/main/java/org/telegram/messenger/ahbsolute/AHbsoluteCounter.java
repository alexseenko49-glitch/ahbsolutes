package org.telegram.messenger.ahbsolute;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Character counter in the message field: a small pill in the top-right corner shows how many characters you typed
 * (Telegram's limit for one message is 4096); it turns red past the limit. Switch: AHbsolute → Сообщения.
 */
public class AHbsoluteCounter extends Drawable {

    private static final int LIMIT = 4096;
    private final EditText et;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int count;

    private AHbsoluteCounter(EditText et) {
        this.et = et;
        text.setTextSize(AndroidUtilities.dp(10.5f));
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    public static boolean on() {
        try { return AHbsoluteUi.ctx() != null && AHbsoluteUi.master(AHbsoluteUi.ctx()) && AHbsoluteUi.getBool("charcount"); } catch (Throwable e) { return false; }
    }

    public static void attach(final EditText et) {
        if (et == null) return;
        final AHbsoluteCounter d = new AHbsoluteCounter(et);
        et.getOverlay().add(d);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                d.count = s == null ? 0 : s.length();
                d.setBounds(0, 0, et.getWidth(), et.getHeight());
                d.invalidateSelf();
            }
        });
    }

    @Override public void draw(Canvas canvas) {
        if (count <= 0 || !on()) return;
        String s = count > LIMIT ? count + " / " + LIMIT : String.valueOf(count);
        float w = text.measureText(s) + AndroidUtilities.dp(10);
        float right = et.getWidth() - AndroidUtilities.dp(2) + et.getScrollX();
        float top = AndroidUtilities.dp(1) + et.getScrollY();
        r.set(right - w, top, right, top + AndroidUtilities.dp(16));
        boolean over = count > LIMIT;
        int accent = over ? 0xFFE53935 : Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        bg.setColor(accent);
        bg.setAlpha(over ? 230 : 38);
        canvas.drawRoundRect(r, AndroidUtilities.dp(8), AndroidUtilities.dp(8), bg);
        text.setColor(over ? 0xFFFFFFFF : accent);
        canvas.drawText(s, r.centerX(), r.centerY() + AndroidUtilities.dp(3.7f), text);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
