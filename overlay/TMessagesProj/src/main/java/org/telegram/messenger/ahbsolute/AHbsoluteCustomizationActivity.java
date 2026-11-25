package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/** Settings -> "Кастомизация Пользователя": avatar shape picker (circle / square / star / triangle). */
public class AHbsoluteCustomizationActivity extends BaseFragment {

    private LinearLayout content;

    private static class ShapePreview extends View {
        private final int shape;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ShapePreview(Context c, int shape) { super(c); this.shape = shape; paint.setColor(0xFF9B4F96); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawPath(AHbsolute.cachedPath(shape, getWidth(), getHeight()), paint);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(t("Кастомизация Пользователя", "User Customization"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frame;

        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        rebuild(context);
        return fragmentView;
    }

    private TextView text(Context c, String s, boolean title) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextColor(Theme.getColor(title ? Theme.key_windowBackgroundWhiteBlueHeader : Theme.key_windowBackgroundWhiteGrayText));
        tv.setTextSize(title ? 14 : 12);
        if (title) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(title ? 18 : 10), AndroidUtilities.dp(20), AndroidUtilities.dp(title ? 6 : 10));
        return tv;
    }

    private void rebuild(Context c) {
        content.removeAllViews();
        content.addView(text(c, t("ФОРМА АВАТАРА", "AVATAR SHAPE"), true));

        String[] names = {t("Круг", "Circle"), t("Квадрат", "Square"), t("Звезда", "Star"), t("Треугольник", "Triangle")};
        int[] shapes = {AHbsolute.SHAPE_CIRCLE, AHbsolute.SHAPE_SQUARE, AHbsolute.SHAPE_STAR, AHbsolute.SHAPE_TRIANGLE};
        for (int i = 0; i < shapes.length; i++) {
            final int shape = shapes[i];
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
            row.addView(new ShapePreview(c, shape), new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(36)));
            TextView label = new TextView(c);
            label.setText(names[i] + (AHbsolute.getMyShape() == shape ? "   ✓" : ""));
            label.setTextSize(16);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            label.setPadding(AndroidUtilities.dp(16), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(-2, -2));
            row.setOnClickListener(v -> { AHbsolute.setMyShape(shape); rebuild(c); });
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        content.addView(text(c, t(
                "Форма применяется к вашему аватару в этом клиенте. Показ формы другим пользователям AHbsolute включается в меню «AHbsolute».",
                "The shape is applied to your avatar in this client. Sharing it with other AHbsolute users is in the \"AHbsolute\" menu."), false));
    }
}
