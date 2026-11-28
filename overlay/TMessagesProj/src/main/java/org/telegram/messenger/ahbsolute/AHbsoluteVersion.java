package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Easter egg: double tap «Диагностика» → a quiet screen with the AHbsolute icon and the version. No settings,
 * only the back arrow in the top left corner.
 */
public class AHbsoluteVersion {

    public static final String VERSION = "v1.03";

    /** the screen; {@code back} closes it */
    public static View build(Context c, final Runnable back) {
        FrameLayout root = new FrameLayout(c) {
            private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            { setWillNotDraw(false); }
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                // slowly breathing violet glow behind the icon
                float t = (SystemClock.uptimeMillis() % 4000) / 4000f;
                float k = 0.85f + 0.15f * (float) Math.sin(t * Math.PI * 2);
                float cx = getWidth() / 2f, cy = getHeight() * 0.40f, r = AndroidUtilities.dp(170) * k;
                glow.setShader(new android.graphics.RadialGradient(cx, cy, r, new int[]{0x668B5CFF, 0x00C86BFA}, null, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, r, glow);
                postInvalidateOnAnimation();
            }
        };

        LinearLayout center = new LinearLayout(c);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(c) {
            private final Path clip = new Path();
            @Override protected void onDraw(Canvas canvas) {
                clip.rewind();
                clip.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), getWidth() * 0.28f, getWidth() * 0.28f, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                super.onDraw(canvas);
                canvas.restore();
            }
        };
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(c.getPackageName());
            icon.setImageDrawable(d);
        } catch (Throwable e) { icon.setBackgroundColor(0xFF8B5CFF); }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setElevation(AndroidUtilities.dp(10));
        center.addView(icon, new LinearLayout.LayoutParams(AndroidUtilities.dp(132), AndroidUtilities.dp(132)));

        TextView name = new TextView(c) {
            @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                super.onSizeChanged(w, h, ow, oh);
                getPaint().setShader(new LinearGradient(0, 0, w, 0, new int[]{0xFF8B5CFF, 0xFFC86BFA, 0xFFFF6FA8}, null, Shader.TileMode.CLAMP));
            }
        };
        name.setText("AHbsolute");
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = AndroidUtilities.dp(26);
        center.addView(name, nlp);

        TextView ver = new TextView(c);
        ver.setText(VERSION);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        ver.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = AndroidUtilities.dp(6);
        center.addView(ver, vlp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        clp.bottomMargin = AndroidUtilities.dp(60);
        root.addView(center, clp);

        ImageView arrow = new ImageView(c);
        arrow.setImageResource(R.drawable.ic_ab_back);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        arrow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        try { arrow.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1)); } catch (Throwable ignored) { }
        arrow.setOnClickListener(v -> { if (back != null) back.run(); });
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(AndroidUtilities.dp(54), AndroidUtilities.dp(54), Gravity.TOP | Gravity.LEFT);
        alp.topMargin = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(2);
        alp.leftMargin = AndroidUtilities.dp(2);
        root.addView(arrow, alp);
        return root;
    }
}
