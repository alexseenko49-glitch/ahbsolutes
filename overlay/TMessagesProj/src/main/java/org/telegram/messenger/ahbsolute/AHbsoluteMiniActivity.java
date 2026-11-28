package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/** Scrollable mini profile opened from a "[Профиль]" message. */
public class AHbsoluteMiniActivity extends BaseFragment {

    private final JSONObject o;
    private final long userId;
    private AHbsoluteCardList list;
    private FrameLayout frame;
    private Context ctx;

    public AHbsoluteMiniActivity(JSONObject o) {
        this.o = o;
        this.userId = o.optLong("u", 0);
    }

    private static int dp(float v) { return AndroidUtilities.dp(v); }

    private String themeOf() {
        int d = o.optInt("d", 0);
        if (d == 1) return Theme.isCurrentThemeDark() ? "design1n" : "design1";
        if (d == 2) return Theme.isCurrentThemeDark() ? "design2n" : "design2";
        String th = o.optString("th", "");
        return th.isEmpty() ? AHbsoluteBanner.BUILTIN[0] : th;
    }

    /** picture of the mini profile: built-in, "theme", or the sender's gallery picture from their personal channel */
    private Bitmap picture(String id, boolean wall) {
        if (id == null || id.isEmpty() || AHbsoluteBanner.OFF.equals(id)) return null;
        if (AHbsoluteBanner.isUser(id)) return wall ? AHbsoluteShare.wallBitmap(userId) : AHbsoluteShare.bannerBitmap(userId);
        if (AHbsoluteBanner.THEME.equals(id)) id = themeOf();
        Bitmap b = AHbsoluteBanner.load(ctx, id, 2);
        return wall ? AHbsoluteProfile.blurred(b, o.optInt("wb", 1)) : b;
    }

    private class Preview extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        Preview(Context c) { super(c); txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
        @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(340), MeasureSpec.EXACTLY)); }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            int ap = o.optInt("ap", 0), na = o.optInt("na", 3);
            boolean beside = na == AHbsoluteProfile.POS_BESIDE || (na == AHbsoluteProfile.POS_FOLLOW && ap != 0);
            int head = beside ? dp(150) : dp(196);
            Bitmap wall = picture(o.optString("w", ""), true);
            p.setShader(null);
            p.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            canvas.drawRect(0, 0, w, h, p);
            if (wall != null) AHbsoluteProfile.drawCover(canvas, wall, w, h);
            else { p.setColor(0xFF5B95C2); canvas.drawRect(0, 0, w, head, p); }
            Bitmap banner = picture(o.optString("b", ""), false);
            if (banner != null) {
                int save = canvas.saveLayer(0, 0, w, head, null);
                AHbsoluteProfile.drawCover(canvas, banner, w, head);
                Paint mask = new Paint();
                mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
                mask.setShader(new LinearGradient(0, 0, 0, head, new int[]{0xFF000000, 0xFF000000, wall != null ? 0x00000000 : 0x66000000}, new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, head, mask);
                canvas.restoreToCount(save);
            }
            p.setShader(new LinearGradient(0, 0, 0, head, new int[]{0x59000000, 0x26000000, 0}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, head, p);
            p.setShader(null);
            // avatar with the sender's shape, position and size
            float size = dp(80) * Math.max(60, Math.min(140, o.optInt("as", 100))) / 100f;
            int pos = ap == 0 && na == AHbsoluteProfile.POS_BESIDE ? AHbsoluteProfile.POS_LEFT : ap;
            float ax = pos == AHbsoluteProfile.POS_LEFT ? dp(24) : pos == AHbsoluteProfile.POS_RIGHT ? w - size - dp(24) : (w - size) / 2f;
            float ay = dp(26);
            p.setShader(new LinearGradient(ax, ay, ax + size, ay + size, 0xFFB79CFF, 0xFF6D4AFF, Shader.TileMode.CLAMP));
            canvas.save();
            canvas.translate(ax, ay);
            canvas.drawPath(AHbsolute.buildPath(o.optInt("sh", 0), size, size), p);
            canvas.restore();
            p.setShader(null);
            String name = o.optString("n", "");
            if (name.isEmpty()) name = t("Пользователь AHbsolute", "AHbsolute user");
            txt.setColor(Color.WHITE);
            txt.setTextSize(dp(20) * Math.max(80, Math.min(130, o.optInt("ns", 100))) / 100f);
            float tx, ty;
            if (beside) {
                txt.setTextAlign(pos == AHbsoluteProfile.POS_RIGHT ? Paint.Align.RIGHT : Paint.Align.LEFT);
                tx = pos == AHbsoluteProfile.POS_RIGHT ? ax - dp(16) : ax + size + dp(16);
                ty = ay + size / 2f;
            } else {
                int al = na == AHbsoluteProfile.POS_FOLLOW ? pos : na;
                txt.setTextAlign(al == AHbsoluteProfile.POS_LEFT ? Paint.Align.LEFT : al == AHbsoluteProfile.POS_RIGHT ? Paint.Align.RIGHT : Paint.Align.CENTER);
                tx = al == AHbsoluteProfile.POS_LEFT ? dp(24) : al == AHbsoluteProfile.POS_RIGHT ? w - dp(24) : w / 2f;
                ty = ay + size + dp(30);
            }
            canvas.drawText(name, tx, ty, txt);
            txt.setTextSize(dp(13));
            txt.setColor(0xCCFFFFFF);
            canvas.drawText(t("мини-профиль AHbsolute", "AHbsolute mini profile"), tx, ty + dp(20), txt);
            // glass cards like in the real profile
            int card = Theme.getColor(Theme.key_windowBackgroundWhite);
            float top = head + dp(14);
            for (int i = 0; i < 3; i++) {
                float ch = i == 0 ? dp(56) : dp(40);
                r.set(dp(12), top, w - dp(12), top + ch);
                if (o.optInt("g", 0) == 1 && wall != null) AHbsoluteProfile.drawGlassSection(canvas, r, dp(16), dp(16), 1f, card);
                else { p.setColor(card); canvas.drawRoundRect(r, dp(16), dp(16), p); }
                top += ch + dp(10);
            }
        }
    }

    @Override
    public View createView(Context context) {
        ctx = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(t("Мини-профиль", "Mini profile"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;
        ScrollView sv = new ScrollView(context);
        list = new AHbsoluteCardList(context);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        rebuild();
        // the sender's own gallery pictures and reposts come from their personal channel (if they share it)
        if (userId != 0 && o.optInt("sp", 0) == 1) AHbsoluteShare.request(userId, this::rebuild);
        return fragmentView;
    }

    private String picName(String id) {
        if (id == null || id.isEmpty() || AHbsoluteBanner.OFF.equals(id)) return t("Нет", "None");
        if (AHbsoluteBanner.isUser(id)) return t("Своя картинка", "Own picture");
        if (AHbsoluteBanner.THEME.equals(id)) return t("По теме", "From theme");
        for (int i = 0; i < AHbsoluteBanner.BUILTIN.length; i++) if (AHbsoluteBanner.BUILTIN[i].equals(id)) return AHbsoluteBanner.BUILTIN_NAMES[i];
        return id;
    }

    private void row(String title, String value, boolean divider) {
        TextSettingsCell c = new TextSettingsCell(ctx);
        c.setTextAndValue(title, value, divider);
        list.addCard(c, -2);
    }

    private void rebuild() {
        if (list == null) return;
        list.removeAllViews();
        list.gap(12);
        list.addCard(new Preview(ctx), -2);
        list.gap(16);
        HeaderCell h = new HeaderCell(ctx);
        h.setText(t("Кастомизация", "Customization"));
        list.addCard(h, -2);
        int d = o.optInt("d", 0);
        row(t("Дизайн", "Design"), d == 1 ? "Absolute Design 1" : d == 2 ? "Absolute Design 2" : t("Обычный", "Regular"), true);
        String th = o.optString("th", "");
        int ti = AHbsoluteThemes.indexOf(th);
        row(t("Тема", "Theme"), ti >= 0 ? AHbsoluteBanner.BUILTIN_NAMES[ti] : t("Стандартная", "Default"), true);
        String[] shapes = {t("Круг", "Circle"), t("Квадрат", "Square"), t("Звезда", "Star"), t("Треугольник", "Triangle")};
        row(t("Форма аватара", "Avatar shape"), shapes[Math.max(0, Math.min(3, o.optInt("sh", 0)))], true);
        String[] pos = {t("По центру", "Center"), t("Слева", "Left"), t("Справа", "Right")};
        row(t("Аватар", "Avatar"), pos[Math.max(0, Math.min(2, o.optInt("ap", 0)))] + " · " + o.optInt("as", 100) + "%", true);
        row(t("Баннер", "Banner"), picName(o.optString("b", "")), true);
        row(t("Обои профиля", "Profile wallpaper"), picName(o.optString("w", "")), true);
        row(t("Стеклянный профиль", "Glass profile"), o.optInt("g", 0) == 1 ? t("Вкл", "On") : t("Выкл", "Off"), false);

        ArrayList<AHbsoluteReposts.Item> reposts = AHbsoluteShare.reposts(userId);
        if (!reposts.isEmpty()) {
            list.gap(16);
            HeaderCell rh = new HeaderCell(ctx);
            rh.setText(t("Репосты", "Reposts") + " · " + reposts.size());
            list.addCard(rh, -2);
            TextCell open = new TextCell(ctx);
            open.setTextAndIcon(t("Открыть репосты", "Open reposts"), R.drawable.msg_share, false);
            open.setBackground(Theme.getSelectorDrawable(false));
            open.setOnClickListener(v -> presentFragment(new AHbsoluteRepostsActivity(userId)));
            list.addCard(open, -2);
        }

        list.gap(16);
        TextCell apply = new TextCell(ctx);
        apply.setTextAndIcon(t("Применить себе", "Apply to me"), R.drawable.msg_customize, true);
        apply.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
        apply.setBackground(Theme.getSelectorDrawable(false));
        apply.setOnClickListener(v -> { AHbsoluteMini.applyToMe(ctx, o); rebuild(); });
        list.addCard(apply, -2);
        if (userId != 0) {
            TextCell prof = new TextCell(ctx);
            prof.setTextAndIcon(t("Открыть профиль", "Open profile"), R.drawable.msg_openprofile, false);
            prof.setBackground(Theme.getSelectorDrawable(false));
            prof.setOnClickListener(v -> {
                Bundle b = new Bundle();
                b.putLong("user_id", userId);
                presentFragment(new org.telegram.ui.ProfileActivity(b));
            });
            list.addCard(prof, -2);
        }
        TextInfoPrivacyCell info = new TextInfoPrivacyCell(ctx);
        info.setText(t("«Применить себе» копирует оформление: дизайн, тему, форму и расположение аватара, встроенные баннер и обои. Чужие картинки из галереи остаются у владельца.",
                "\"Apply to me\" copies the look: design, theme, avatar shape and layout, built-in banner and wallpaper. Other people's gallery pictures stay theirs."));
        list.addPlain(info);
    }
}
