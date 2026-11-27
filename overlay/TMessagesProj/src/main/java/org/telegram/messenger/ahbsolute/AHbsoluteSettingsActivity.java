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

/** AHbsolute settings. Reached from the side menu entry "AHbsolute". */
public class AHbsoluteSettingsActivity extends BaseFragment {

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
        actionBar.setTitle("AHbsolute");
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

    private TextView text(Context c, String s, boolean title, boolean clickable) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextColor(Theme.getColor(title ? Theme.key_windowBackgroundWhiteBlueHeader : Theme.key_windowBackgroundWhiteBlackText));
        tv.setTextSize(title ? 14 : 16);
        if (title) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(title ? 18 : 14), AndroidUtilities.dp(20), AndroidUtilities.dp(title ? 6 : 14));
        if (clickable) tv.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        return tv;
    }

    private void rebuild(Context c) {
        content.removeAllViews();

        TextView info = text(c, t(
                "AHbsolute — неофициальный клиент. Он использует Telegram API, но не создан и не одобрен Telegram.",
                "AHbsolute is an unofficial client. It uses the Telegram API but is not made or endorsed by Telegram."), false, false);
        info.setTextSize(13);
        info.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        content.addView(info);

        TextView editor = text(c, t("Изменение интерфейса", "Interface editor") + "  ›", false, true);
        editor.setOnClickListener(v -> presentFragment(new AHbsoluteInterfaceActivity()));
        content.addView(editor);

        content.addView(text(c, t("ФОРМА АВАТАРА", "AVATAR SHAPE"), true, false));
        String[] names = {t("Круг", "Circle"), t("Квадрат", "Square"), t("Звезда", "Star"), t("Треугольник", "Triangle")};
        int[] shapes = {AHbsolute.SHAPE_CIRCLE, AHbsolute.SHAPE_SQUARE, AHbsolute.SHAPE_STAR, AHbsolute.SHAPE_TRIANGLE};
        for (int i = 0; i < shapes.length; i++) {
            final int shape = shapes[i];
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
            ShapePreview pv = new ShapePreview(c, shape);
            row.addView(pv, new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(36)));
            TextView label = new TextView(c);
            label.setText(names[i] + (AHbsolute.getMyShape() == shape ? "   ✓" : ""));
            label.setTextSize(16);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            label.setPadding(AndroidUtilities.dp(16), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(-2, -2));
            row.setOnClickListener(v -> { AHbsolute.setMyShape(shape); rebuild(c); });
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        boolean share = AHbsolute.isShareEnabled();
        TextView shareRow = text(c, t("Показывать мою форму другим пользователям AHbsolute: ", "Show my shape to other AHbsolute users: ")
                + (share ? t("ВКЛ", "ON") : t("ВЫКЛ", "OFF")), false, true);
        shareRow.setOnClickListener(v -> { AHbsolute.setShareEnabled(!AHbsolute.isShareEnabled()); rebuild(c); });
        content.addView(shareRow);
        TextView shareNote = text(c, t(
                "Если включено, к вашей фамилии добавляется невидимый маркер (нулевой ширины). Пользователи обычного Telegram видят круглый аватар и фамилию без изменений, но маркер технически присутствует в вашем имени. Выключите — и он будет удалён.",
                "When on, an invisible zero-width marker is appended to your last name. Regular Telegram users see a normal round avatar and the same name, but the marker is technically part of your name. Turn it off to remove it."), false, false);
        shareNote.setTextSize(12);
        shareNote.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        content.addView(shareNote);

        content.addView(text(c, t("ОПТИМИЗАЦИЯ", "OPTIMIZATION"), true, false));
        TextView clean = text(c, t("Очищать кэш старше 30 дней при запуске: ", "Clean cache older than 30 days on start: ")
                + (AHbsolute.isCleanEnabled() ? t("ВКЛ", "ON") : t("ВЫКЛ", "OFF")), false, true);
        clean.setOnClickListener(v -> { AHbsolute.setCleanEnabled(!AHbsolute.isCleanEnabled()); rebuild(c); });
        content.addView(clean);
        TextView cleanNote = text(c, t(
                "Работает в фоне, затрагивает только папку кэша приложения. Выключено по умолчанию, чтобы не конфликтовать с настройкой «Хранить медиа».",
                "Runs in the background and only touches the app cache folder. Off by default so it does not conflict with Telegram's \"Keep media\" setting."), false, false);
        cleanNote.setTextSize(12);
        cleanNote.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        content.addView(cleanNote);
    }
}
