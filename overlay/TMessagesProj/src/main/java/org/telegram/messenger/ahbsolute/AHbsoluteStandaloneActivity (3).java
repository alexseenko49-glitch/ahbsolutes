package org.telegram.messenger.ahbsolute;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/** Second launcher icon "AHbsolute ⚙": opens AHbsolute settings without touching Telegram's own screens. */
public class AHbsoluteStandaloneActivity extends Activity {

    private LinearLayout content;

    private static class ShapePreview extends View {
        private final int shape;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ShapePreview(Context c, int shape) { super(c); this.shape = shape; paint.setColor(0xFF9B4F96); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawPath(AHbsolute.cachedPath(shape, getWidth(), getHeight()), paint);
        }
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("AHbsolute");
        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        setContentView(sv);
        rebuild();
    }

    private TextView text(String s, boolean header, boolean clickable) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(header ? 13 : 16);
        if (header) tv.setTextColor(0xFF9B4F96);
        tv.setPadding(dp(20), dp(header ? 18 : 14), dp(20), dp(header ? 6 : 14));
        if (clickable) tv.setBackgroundResource(android.R.drawable.list_selector_background);
        return tv;
    }

    private void rebuild() {
        content.removeAllViews();
        TextView info = text(t(
                "AHbsolute — неофициальный клиент. Он использует Telegram API, но не создан и не одобрен Telegram.",
                "AHbsolute is an unofficial client. It uses the Telegram API but is not made or endorsed by Telegram."), false, false);
        info.setTextSize(13);
        content.addView(info);

        content.addView(text(t("ФОРМА АВАТАРА", "AVATAR SHAPE"), true, false));
        String[] names = {t("Круг", "Circle"), t("Квадрат", "Square"), t("Звезда", "Star"), t("Треугольник", "Triangle")};
        for (int i = 0; i < names.length; i++) {
            final int shape = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(8), dp(20), dp(8));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.addView(new ShapePreview(this, shape), new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView label = new TextView(this);
            label.setText(names[i] + (AHbsolute.getMyShape() == shape ? "   ✓" : ""));
            label.setTextSize(16);
            label.setPadding(dp(16), 0, 0, 0);
            row.addView(label);
            row.setOnClickListener(v -> { AHbsolute.setMyShape(shape); rebuild(); });
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView share = text(t("Показывать мою форму другим пользователям AHbsolute: ", "Show my shape to other AHbsolute users: ")
                + (AHbsolute.isShareEnabled() ? t("ВКЛ", "ON") : t("ВЫКЛ", "OFF")), false, true);
        share.setOnClickListener(v -> { AHbsolute.setShareEnabled(!AHbsolute.isShareEnabled()); rebuild(); });
        content.addView(share);
        TextView note = text(t(
                "Если включено, к вашей фамилии добавляется невидимый маркер. Обычные клиенты видят круглый аватар и ту же фамилию. Откройте AHbsolute и войдите в аккаунт, чтобы маркер отправился в профиль.",
                "When on, an invisible marker is appended to your last name. Regular clients see a round avatar and the same name. Open AHbsolute and sign in so the marker can be sent to your profile."), false, false);
        note.setTextSize(12);
        content.addView(note);

        AHbsoluteUi.addSection(this, content, 0xFF9B4F96, 0xFF202020, 0xFF777777, this::rebuild);
        AHbsoluteBlur.addSection(this, content, 0xFF9B4F96, 0xFF202020, 0xFF777777, this::rebuild);
        AHbsoluteThemes.addSection(this, content, 0xFF9B4F96, 0xFF202020, 0xFF777777, this::rebuild);

        content.addView(text(t("ОПТИМИЗАЦИЯ", "OPTIMIZATION"), true, false));
        TextView clean = text(t("Очищать кэш старше 30 дней при запуске: ", "Clean cache older than 30 days on start: ")
                + (AHbsolute.isCleanEnabled() ? t("ВКЛ", "ON") : t("ВЫКЛ", "OFF")), false, true);
        clean.setOnClickListener(v -> { AHbsolute.setCleanEnabled(!AHbsolute.isCleanEnabled()); rebuild(); });
        content.addView(clean);
    }
}
