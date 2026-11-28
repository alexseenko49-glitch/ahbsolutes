package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RadioButton;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * The single AHbsolute settings screen, built from Telegram's own list cells inside rounded sections
 * (the same look as Telegram's current settings pages). Used by Settings → AHbsolute and by "AHbsolute ⚙".
 */
public class AHbsoluteSettingsUi {

    public static final int REQ_BANNER = 9301;
    private static final String TARGET_BANNER = "banner", TARGET_WALL = "wall";
    private static String pickTarget = TARGET_BANNER;

    public interface Host {
        /** start the system picture picker; the result must go to {@link #onPicked} */
        void pickBanner();
        /** null when the side-menu editor cannot be opened from this host */
        Runnable interfaceEditor();
        /** null when the reposts screen cannot be opened from this host */
        Runnable reposts();
        /** the version easter egg */
        void openVersion();
        void rebuild();
    }

    private static int dp(float v) { return AndroidUtilities.dp(v); }
    private static int color(int key) { return Theme.getColor(key); }

    public static Intent pickIntent() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        return Intent.createChooser(i, t("Выберите картинку", "Choose a picture"));
    }

    public static void onPicked(final Context c, int requestCode, int resultCode, Intent data, final Host host) {
        if (requestCode != REQ_BANNER || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Toast.makeText(c, t("Загружаю картинку…", "Loading picture…"), Toast.LENGTH_SHORT).show();
        final String target = pickTarget;
        AHbsoluteBanner.importFromUri(c, data.getData(), (id, err) -> {
            if (id == null) {
                Toast.makeText(c, t("Не удалось загрузить картинку: ", "Could not load the picture: ") + err, Toast.LENGTH_LONG).show();
                return;
            }
            if (TARGET_WALL.equals(target)) AHbsoluteProfile.setWallpaper(id); else AHbsoluteBanner.setMode(id);
            AHbsoluteBanner.invalidate();
            AHbsoluteProfile.invalidate();
            if (host != null) host.rebuild();
        });
    }

    /* ------------------------------------------------------------ building blocks ------------------------------------------------------------ */

    private static void header(Context c, AHbsoluteCardList list, String text) {
        HeaderCell h = new HeaderCell(c);
        h.setText(text);
        list.addCard(h, -2);
    }

    /** grey hint under a section; ends the card */
    private static void info(Context c, AHbsoluteCardList list, String text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(c);
        cell.setText(text);
        list.addPlain(cell);
    }

    private static View sel(View v) {
        v.setBackground(Theme.getSelectorDrawable(false));
        return v;
    }

    private static void check(Context c, AHbsoluteCardList list, String text, boolean value, boolean divider, final Runnable onToggle) {
        final TextCheckCell cell = new TextCheckCell(c);
        cell.setTextAndCheck(text, value, divider);
        sel(cell);
        cell.setOnClickListener(v -> {
            onToggle.run();
            cell.setChecked(!cell.isChecked());
        });
        list.addCard(cell, -2);
    }

    private static void uiCheck(final Context c, AHbsoluteCardList list, String text, final String key, boolean divider) {
        check(c, list, text, AHbsoluteUi.getBool(key), divider, () -> AHbsoluteUi.toggle(c, key));
    }

    private interface Pick { void accept(int which); }

    private static void dialog(Context c, String title, CharSequence[] names, final Pick onPick) {
        try {
            new AlertDialog.Builder(c).setTitle(title).setItems(names, (d, which) -> onPick.accept(which)).show();
        } catch (Throwable e) {
            onPick.accept(0);
        }
    }

    /** "Title ........ value" row that opens a Telegram choice dialog; values are stored as strings */
    private static void choice(final Context c, AHbsoluteCardList list, final String title, final String key, final String[] values, final String[] names,
                               final String def, boolean divider, final Host host) {
        String cur = AHbsoluteUi.getStr(key, def);
        String value = cur;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) value = names[i];
        TextSettingsCell cell = new TextSettingsCell(c);
        cell.setTextAndValue(title, value, divider);
        sel(cell);
        cell.setOnClickListener(v -> dialog(c, title, names, which -> {
            AHbsoluteUi.putStr(key, values[which]);
            AHbsoluteBanner.invalidate();
            AHbsoluteProfile.invalidate();
            host.rebuild();
        }));
        list.addCard(cell, -2);
    }

    private static String[] pct(int... v) {
        String[] r = new String[v.length];
        for (int i = 0; i < v.length; i++) r[i] = String.valueOf(v[i]);
        return r;
    }

    private static String[] pctNames(String zero, int... v) {
        String[] r = new String[v.length];
        for (int i = 0; i < v.length; i++) r[i] = v[i] == 0 && zero != null ? zero : v[i] + "%";
        return r;
    }

    /** row with a picture on the left, a title and Telegram's radio button on the right */
    private static LinearLayout radioRow(Context c, final View image, String title, String subtitle, boolean checked, boolean divider) {
        final boolean needDivider = divider;
        LinearLayout row = new LinearLayout(c) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (needDivider && Theme.dividerPaint != null) {
                    canvas.drawLine(dp(image != null ? 76 : 21), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            }
        };
        row.setWillNotDraw(false);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(image != null ? 16 : 21), 0, dp(21), 0);
        sel(row);
        if (image != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(32));
            lp.rightMargin = dp(16);
            row.addView(image, lp);
        }
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(c);
        tv.setText(title);
        tv.setTextSize(16);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        texts.addView(tv);
        if (subtitle != null) {
            TextView st = new TextView(c);
            st.setText(subtitle);
            st.setTextSize(13);
            st.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));
            texts.addView(st);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        RadioButton rb = new RadioButton(c);
        rb.setSize(dp(20));
        rb.setColor(color(Theme.key_radioBackground), color(Theme.key_radioBackgroundChecked));
        rb.setChecked(checked, false);
        row.addView(rb, new LinearLayout.LayoutParams(dp(22), dp(22)));
        return row;
    }

    private static ImageView thumb(Context c, Bitmap b, int fallback) {
        ImageView iv = new ImageView(c) {
            private final Path clip = new Path();
            @Override protected void onDraw(Canvas canvas) {
                clip.reset();
                clip.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), dp(6), dp(6), Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                super.onDraw(canvas);
                canvas.restore();
            }
        };
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (b != null) {
            iv.setImageBitmap(b);
        } else {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(6));
            g.setColor(fallback);
            iv.setImageDrawable(g);
        }
        return iv;
    }

    /** human name of a picture id (banner / wallpaper) */
    private static String pictureName(Context c, String id) {
        if (id == null || AHbsoluteBanner.OFF.equals(id)) return t("Нет", "None");
        if (AHbsoluteBanner.THEME.equals(id)) return t("По теме", "From theme");
        for (int i = 0; i < AHbsoluteBanner.BUILTIN.length; i++) if (AHbsoluteBanner.BUILTIN[i].equals(id)) return AHbsoluteBanner.BUILTIN_NAMES[i];
        ArrayList<String> mine = AHbsoluteBanner.userBanners(c);
        int k = mine.indexOf(id);
        return k >= 0 ? t("Моя картинка ", "My picture ") + (k + 1) : t("Своя", "Custom");
    }

    /* ------------------------------------------------------------ live preview ------------------------------------------------------------ */

    /** a miniature of the profile: wallpaper, glass cards, banner, avatar position/size/shape, name alignment */
    private static class Preview extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint av = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint status = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        Preview(Context c) {
            super(c);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(300), MeasureSpec.EXACTLY));
        }
        @Override protected void onDraw(Canvas canvas) {
            Context c = getContext();
            int w = getWidth(), h = getHeight(), head = AHbsoluteProfile.besideActive() ? dp(132) : dp(176);
            boolean wall = !AHbsoluteBanner.OFF.equals(AHbsoluteProfile.wallpaper());
            boolean glass = AHbsoluteProfile.glassOn();
            // page background
            bg.setShader(null);
            bg.setColor(color(Theme.key_windowBackgroundGray));
            canvas.drawRect(0, 0, w, h, bg);
            if (wall) {
                Bitmap b = AHbsoluteProfile.bitmap(c);
                if (b != null) AHbsoluteProfile.drawCover(canvas, b, w, h);
            }
            // header
            int headColor;
            try { headColor = Theme.getColor(Theme.key_avatar_backgroundActionBarBlue); } catch (Throwable e) { headColor = 0xFF5B95C2; }
            float ha = 1f;
            try { ha = wall ? 0f : AHbsoluteUi.profileHeaderAlpha(); } catch (Throwable ignored) { }
            bg.setColor(headColor);
            bg.setAlpha(Math.round(255 * ha));
            canvas.drawRect(0, 0, w, head, bg);
            if (!AHbsoluteBanner.OFF.equals(AHbsoluteBanner.mode())) AHbsoluteBanner.drawPreview(c, canvas, w, head);
            if (wall) {
                bg.setShader(new LinearGradient(0, 0, 0, head, new int[]{0x59000000, 0x26000000, 0}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, head, bg);
                bg.setShader(null);
            }
            // avatar
            float size = dp(72) * Math.max(60, Math.min(140, AHbsoluteProfile.avatarSize())) / 100f;
            int pos = AHbsoluteProfile.effectiveAvatarPos();
            float ax = pos == AHbsoluteProfile.POS_LEFT ? dp(24) : pos == AHbsoluteProfile.POS_RIGHT ? w - size - dp(24) : (w - size) / 2f;
            float ay = dp(22);
            av.setShader(new LinearGradient(ax, ay, ax + size, ay + size, 0xFFB79CFF, 0xFF6D4AFF, Shader.TileMode.CLAMP));
            canvas.save();
            canvas.translate(ax, ay);
            canvas.drawPath(AHbsolute.cachedPath(AHbsolute.getMyShape(), size, size), av);
            canvas.restore();
            // name + status: with the avatar / next to it / fixed side
            boolean beside = AHbsoluteProfile.besideActive();
            int al = beside ? (pos == AHbsoluteProfile.POS_RIGHT ? AHbsoluteProfile.POS_RIGHT : AHbsoluteProfile.POS_LEFT) : AHbsoluteProfile.effectiveNameAlign();
            Paint.Align align = al == AHbsoluteProfile.POS_LEFT ? Paint.Align.LEFT : al == AHbsoluteProfile.POS_RIGHT ? Paint.Align.RIGHT : Paint.Align.CENTER;
            float tx;
            if (beside) tx = pos == AHbsoluteProfile.POS_RIGHT ? ax - dp(16) : ax + size + dp(16);
            else tx = al == AHbsoluteProfile.POS_LEFT ? dp(24) : al == AHbsoluteProfile.POS_RIGHT ? w - dp(24) : w / 2f;
            name.setTextAlign(align);
            status.setTextAlign(align);
            name.setColor(Color.WHITE);
            name.setTextSize(dp(19) * Math.max(80, Math.min(130, AHbsoluteProfile.nameSize())) / 100f);
            float ny = beside ? ay + size / 2f - dp(2) : ay + size + dp(30);
            canvas.drawText(t("Вы", "You"), tx, ny, name);
            status.setColor(0xCCFFFFFF);
            status.setTextSize(dp(13));
            canvas.drawText(t("в сети", "online"), tx, ny + dp(20), status);
            // two section cards
            int card = color(Theme.key_windowBackgroundWhite);
            float top = head + dp(12);
            for (int i = 0; i < 2; i++) {
                float ch = i == 0 ? dp(58) : dp(36);
                r.set(dp(12), top, w - dp(12), top + ch);
                if (glass) {
                    AHbsoluteProfile.drawGlassSection(canvas, r, dp(16), dp(16), 1f, card);
                } else {
                    bg.setColor(card);
                    canvas.drawRoundRect(r, dp(16), dp(16), bg);
                }
                line.setColor(color(Theme.key_windowBackgroundWhiteBlackText));
                line.setAlpha(150);
                canvas.drawRoundRect(dp(28), top + dp(13), dp(28) + w * 0.38f, top + dp(22), dp(4), dp(4), line);
                if (i == 0) {
                    line.setAlpha(80);
                    canvas.drawRoundRect(dp(28), top + dp(33), dp(28) + w * 0.24f, top + dp(41), dp(4), dp(4), line);
                }
                top += ch + dp(10);
            }
        }
    }

    /** fixed screen background: grey, or the design wallpaper behind glass cards when Absolute Design 1 is on */
    public static android.widget.FrameLayout backdrop(Context c) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c) {
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(color(Theme.key_windowBackgroundGray));
                if (AHbsoluteDesign.isOn(getContext())) {
                    Bitmap b = AHbsoluteProfile.bitmap(getContext());
                    if (b != null) AHbsoluteProfile.drawCover(canvas, b, getWidth(), getHeight());
                }
            }
        };
        f.setWillNotDraw(false);
        return f;
    }

    /* ------------------------------------------------------------ the screen ------------------------------------------------------------ */

    public static void build(final Context c, final AHbsoluteCardList list, final Host host) {
        list.removeAllViews();
        try { Theme.createCommonResources(c); } catch (Throwable ignored) { }
        list.glass = AHbsoluteDesign.isOn(c);
        if (list.getParent() instanceof View && ((View) list.getParent()).getParent() instanceof View) ((View) ((View) list.getParent()).getParent()).invalidate();
        list.gap(12);

        // ---------- preview ----------
        list.addCard(new Preview(c), -2);
        list.gap(16);

        // ---------- Absolute Design 1 ----------
        final TextCheckCell design = new TextCheckCell(c);
        design.setTextAndValueAndCheck(AHbsoluteDesign.title(), t("Новый интерфейс AHbsolute", "The new AHbsolute interface"),
                AHbsoluteDesign.isOn(c), false, false);
        sel(design);
        design.setOnClickListener(v -> {
            boolean on = !AHbsoluteDesign.isOn(c);
            String err = AHbsoluteDesign.setOn(c, on);
            if (err != null) Toast.makeText(c, t("Тема не применилась: ", "Theme was not applied: ") + err, Toast.LENGTH_LONG).show();
            else if (on) Toast.makeText(c, t("Absolute Design 1 включён ✨ Откройте свой профиль.", "Absolute Design 1 is on ✨ Open your profile."), Toast.LENGTH_LONG).show();
            host.rebuild();
        });
        list.addCard(design, -2);
        info(c, list, AHbsoluteDesign.subtitle());

        // ---------- profile ----------
        header(c, list, t("Профиль", "Profile"));
        String wp = AHbsoluteProfile.wallpaper();
        TextSettingsCell wall = new TextSettingsCell(c);
        wall.setTextAndValue(t("Обои профиля", "Profile wallpaper"), pictureName(c, wp), true);
        sel(wall);
        wall.setOnClickListener(v -> {
            final ArrayList<String> ids = new ArrayList<>();
            final ArrayList<String> names = new ArrayList<>();
            ids.add(AHbsoluteBanner.OFF); names.add(t("Нет", "None"));
            ids.add("upload"); names.add(t("Загрузить из галереи…", "Upload from gallery…"));
            ids.add(AHbsoluteBanner.THEME); names.add(t("По теме", "From theme"));
            for (String id : AHbsoluteBanner.userBanners(c)) { ids.add(id); names.add(pictureName(c, id)); }
            for (int i = 0; i < AHbsoluteBanner.BUILTIN.length; i++) { ids.add(AHbsoluteBanner.BUILTIN[i]); names.add(AHbsoluteBanner.BUILTIN_NAMES[i]); }
            dialog(c, t("Обои профиля", "Profile wallpaper"), names.toArray(new CharSequence[0]), which -> {
                String id = ids.get(which);
                if ("upload".equals(id)) { pickTarget = TARGET_WALL; host.pickBanner(); return; }
                AHbsoluteProfile.setWallpaper(id);
                host.rebuild();
            });
        });
        list.addCard(wall, -2);
        check(c, list, t("Стеклянный профиль", "Glass profile"), AHbsoluteProfile.glassOn(), true,
                () -> { AHbsoluteProfile.setGlass(!AHbsoluteProfile.glassOn()); host.rebuild(); });
        if (AHbsoluteProfile.glassOn()) {
            choice(c, list, t("Плотность стекла", "Glass density"), "prof_glass_op", pct(35, 50, 62, 75, 88),
                    pctNames(null, 35, 50, 62, 75, 88), "62", true, host);
        }
        if (!AHbsoluteBanner.OFF.equals(wp)) {
            choice(c, list, t("Размытие обоев", "Wallpaper blur"), "prof_wp_blur", pct(0, 1, 2, 3),
                    new String[]{t("Нет", "None"), t("Лёгкое", "Light"), t("Среднее", "Medium"), t("Сильное", "Strong")}, "1", true, host);
            choice(c, list, t("Затемнение обоев", "Wallpaper dimming"), "prof_wp_dim", pct(0, 15, 30, 45),
                    pctNames(t("Нет", "None"), 0, 15, 30, 45), "0", true, host);
        }
        String[] posNames = {t("По центру", "Center"), t("Слева", "Left"), t("Справа", "Right")};
        choice(c, list, t("Аватар", "Avatar"), "prof_av_pos", pct(0, 1, 2), posNames, "0", true, host);
        choice(c, list, t("Размер аватара", "Avatar size"), "prof_av_size", pct(80, 90, 100, 115, 130), pctNames(null, 80, 90, 100, 115, 130), "100", true, host);
        choice(c, list, t("Имя и «в сети»", "Name and status"), "prof_name_align", pct(3, 4, 0, 1, 2),
                new String[]{t("Вместе с аватаром", "With the avatar"), t("Рядом с аватаром", "Next to the avatar"), t("По центру", "Center"), t("Слева", "Left"), t("Справа", "Right")}, "3", true, host);
        choice(c, list, t("Размер имени", "Name size"), "prof_name_size", pct(90, 100, 115, 125), pctNames(null, 90, 100, 115, 125), "100", true, host);
        choice(c, list, t("Прозрачность шапки", "Header transparency"), "prof_alpha", pct(100, 75, 50, 25, 0),
                new String[]{t("Обычная", "Normal"), "75%", "50%", "25%", t("Прозрачная", "Transparent")}, "100", true, host);
        check(c, list, t("Во всех профилях", "On every profile"), AHbsoluteProfile.everywhere(), false,
                () -> { AHbsoluteProfile.setEverywhere(!AHbsoluteProfile.everywhere()); host.rebuild(); });
        info(c, list, t("Обои, стекло и расположение аватара видите вы в AHbsolute. Выключено «Во всех профилях» — меняется только ваш профиль.",
                "Wallpaper, glass and avatar layout are shown in your AHbsolute. With \"On every profile\" off only your own profile changes."));

        // ---------- customization: top bar ----------
        header(c, list, t("Кастомизация", "Customization"));
        check(c, list, t("Компактная верхняя панель", "Compact top bar"), AHbsoluteUi.getBool("topbar"), AHbsoluteUi.getBool("topbar"),
                () -> { AHbsoluteUi.toggle(c, "topbar"); host.rebuild(); });
        if (AHbsoluteUi.getBool("topbar")) {
            choice(c, list, t("Размер панели", "Bar size"), "bar_size", pct(100, 96, 92, 88, 84),
                    new String[]{t("Обычный", "Normal"), "96%", "92%", "88%", "84%"}, "92", true, host);
            choice(c, list, t("Скругление краёв", "Corner rounding"), "bar_radius", pct(0, 12, 18, 24, 99),
                    new String[]{t("Без скругления", "Square"), "12 dp", "18 dp", "24 dp", t("Капсула", "Capsule")}, "24", true, host);
            choice(c, list, t("Отступы по краям", "Side margins"), "bar_margin", pct(0, 6, 10, 16, 24),
                    new String[]{t("Нет", "None"), "6 dp", "10 dp", "16 dp", "24 dp"}, "10", false, host);
        }
        info(c, list, t("Верхняя панель главного экрана (надпись AHbsolute, поиск, меню) становится меньше и стоит в скруглённой капсуле. С обоями за чатами капсула стеклянная.",
                "The top bar of the main screen (AHbsolute title, search, menu) gets smaller and sits in a rounded capsule. With a wallpaper behind the chats the capsule is glass."));

        // ---------- main screen ----------
        header(c, list, t("Главный экран", "Main screen"));
        choice(c, list, t("Расстояние между чатами", "Space between chats"), "chat_gap", pct(0, 2, 4, 6, 8, 12, 16),
                new String[]{t("Как в Telegram", "Like Telegram"), "2 dp", "4 dp", "6 dp", "8 dp", "12 dp", "16 dp"}, "0", true, host);
        check(c, list, t("Чаты карточками", "Chats as cards"), AHbsoluteUi.getBool("chatcards"), true,
                () -> { AHbsoluteUi.toggle(c, "chatcards"); host.rebuild(); });
        if (AHbsoluteUi.getBool("chatcards")) {
            choice(c, list, t("Скругление карточек", "Card corners"), "chat_radius", pct(10, 14, 18, 20, 24),
                    new String[]{"10 dp", "14 dp", "18 dp", "20 dp", "24 dp"}, "18", true, host);
        }
        check(c, list, t("Обои за чатами (стекло)", "Wallpaper behind chats (glass)"), AHbsoluteUi.getBool("mainwall"), true,
                () -> { AHbsoluteUi.toggle(c, "mainwall"); host.rebuild(); });
        uiCheck(c, list, t("Перемещать любые чаты", "Move any chat"), "chatmove", AHbsoluteOrder.count() > 0);
        if (AHbsoluteOrder.count() > 0) {
            TextSettingsCell resetOrder = new TextSettingsCell(c);
            resetOrder.setTextAndValue(t("Сбросить порядок чатов", "Reset chat order"), String.valueOf(AHbsoluteOrder.count()), false);
            sel(resetOrder);
            resetOrder.setOnClickListener(v -> { AHbsoluteOrder.reset(); host.rebuild(); });
            list.addCard(resetOrder, -2);
        }
        info(c, list, t("Перемещение: зажмите чат (он выделится), потом зажмите ещё раз и тяните вверх или вниз — он останется там. Обои за чатами берутся из «Обоев профиля». С ними карточки чатов становятся стеклянными. Изменения видны при следующем открытии списка чатов.",
                "The wallpaper behind the chats is the \"Profile wallpaper\". With it the chat cards turn into glass. Changes show the next time the chat list is opened."));

        // ---------- messages ----------
        header(c, list, t("Сообщения", "Messages"));
        check(c, list, "Liquid Glass", AHbsoluteUi.getBool("msgglass"), true,
                () -> { AHbsoluteUi.toggle(c, "msgglass"); host.rebuild(); });
        if (AHbsoluteUi.getBool("msgglass")) {
            choice(c, list, t("Плотность стекла", "Glass density"), "msgglass_op", pct(40, 52, 62, 72, 85), pctNames(null, 40, 52, 62, 72, 85), "62", true, host);
        }
        uiCheck(c, list, t("Падающие буквы при удалении", "Falling letters when erasing"), "textfall", true);
        uiCheck(c, list, t("Экстремальное удаление", "Extreme delete"), "erase", false);
        info(c, list, t("Liquid Glass: сообщения становятся каплями стекла, сквозь них видны обои. Падающие буквы: стёртые буквы выпрыгивают из поля ввода, падают на сообщения или на чат и исчезают за 2 секунды.",
                "Liquid Glass: messages become drops of glass with the wallpaper showing through. Falling letters: erased letters jump out of the input field, land on the messages or the chat and fade within 2 seconds."));
        info(c, list, t("Экстремальное удаление: нажмите на своё сообщение → «Экстремальное удаление». Без вопросов оно удаляется у всех на сервере и из памяти телефона — в чате не остаётся даже следа. Скриншоты, уже показанные уведомления и приложения, которые сами копируют сообщения, стереть невозможно.",
                "Extreme delete: tap your message → \"Extreme delete\". Without questions it is deleted for everyone on the server and from this phone — not even a trace stays in the chat. Screenshots, notifications already shown and apps that copy messages themselves cannot be erased."));

        // ---------- banner ----------
        header(c, list, t("Баннер профиля", "Profile banner"));
        String mode = AHbsoluteBanner.mode();
        LinearLayout none = radioRow(c, thumb(c, null, color(Theme.key_windowBackgroundGray)), t("Без баннера", "No banner"), null,
                AHbsoluteBanner.OFF.equals(mode), true);
        none.setOnClickListener(v -> { AHbsoluteBanner.setMode(AHbsoluteBanner.OFF); AHbsoluteBanner.invalidate(); host.rebuild(); });
        list.addCard(none, dp(56));
        ArrayList<String> mine = AHbsoluteBanner.userBanners(c);
        for (int i = 0; i < mine.size(); i++) {
            final String id = mine.get(i);
            LinearLayout row = radioRow(c, thumb(c, AHbsoluteBanner.load(c, id, 8), Color.GRAY),
                    t("Моя картинка ", "My picture ") + (i + 1), t("Удерживайте, чтобы удалить", "Hold to delete"), id.equals(mode), true);
            row.setOnClickListener(v -> { AHbsoluteBanner.setMode(id); AHbsoluteBanner.invalidate(); host.rebuild(); });
            row.setOnLongClickListener(v -> {
                try {
                    new AlertDialog.Builder(c)
                            .setTitle(t("Удалить картинку?", "Delete picture?"))
                            .setMessage(t("Она пропадёт из баннеров и обоев.", "It will be removed from banners and wallpapers."))
                            .setPositiveButton(t("Удалить", "Delete"), (d, w) -> {
                                AHbsoluteBanner.delete(c, id);
                                if (id.equals(AHbsoluteProfile.wallpaper())) AHbsoluteProfile.setWallpaper(AHbsoluteBanner.OFF);
                                host.rebuild();
                            })
                            .setNegativeButton(t("Отмена", "Cancel"), null)
                            .show();
                } catch (Throwable e) {
                    AHbsoluteBanner.delete(c, id);
                    host.rebuild();
                }
                return true;
            });
            list.addCard(row, dp(56));
        }
        for (int i = 0; i < AHbsoluteBanner.BUILTIN.length; i++) {
            final String id = AHbsoluteBanner.BUILTIN[i];
            LinearLayout row = radioRow(c, thumb(c, AHbsoluteBanner.load(c, id, 16), Color.GRAY), AHbsoluteBanner.BUILTIN_NAMES[i], null, id.equals(mode), true);
            row.setOnClickListener(v -> { AHbsoluteBanner.setMode(id); AHbsoluteBanner.invalidate(); host.rebuild(); });
            list.addCard(row, dp(56));
        }
        LinearLayout byTheme = radioRow(c, null, t("По теме AHbsolute", "Match AHbsolute theme"), null, AHbsoluteBanner.THEME.equals(mode), true);
        byTheme.setOnClickListener(v -> { AHbsoluteBanner.setMode(AHbsoluteBanner.THEME); AHbsoluteBanner.invalidate(); host.rebuild(); });
        list.addCard(byTheme, dp(56));
        TextCell upload = new TextCell(c);
        upload.setTextAndIcon(t("Загрузить баннер из галереи", "Upload banner from gallery"), R.drawable.msg_addphoto, false);
        upload.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
        sel(upload);
        upload.setOnClickListener(v -> { pickTarget = TARGET_BANNER; host.pickBanner(); });
        list.addCard(upload, -2);
        info(c, list, t("Баннер показывается за аватаркой и именем. Подойдёт любая картинка, лучше горизонтальная. Загруженные картинки можно ставить и на обои профиля.",
                "The banner is shown behind the avatar and the name. Any picture works, landscape is best. Uploaded pictures can also be used as the profile wallpaper."));

        if (!AHbsoluteBanner.OFF.equals(mode)) {
            header(c, list, t("Настройка баннера", "Banner settings"));
            choice(c, list, t("Яркость", "Brightness"), "banner_op", pct(100, 85, 70, 55, 40), pctNames(null, 100, 85, 70, 55, 40), "100", true, host);
            choice(c, list, t("Затемнение", "Darkening"), "banner_dim", pct(0, 15, 30, 45, 60), pctNames(t("Нет", "None"), 0, 15, 30, 45, 60), "15", true, host);
            choice(c, list, t("Затухание книзу", "Fade to bottom"), "banner_fade", pct(0, 20, 40, 70, 100), pctNames(t("Нет", "None"), 0, 20, 40, 70, 100), "40", true, host);
            choice(c, list, t("Положение картинки", "Picture position"), "banner_pos", pct(0, 50, 100),
                    new String[]{t("Сверху", "Top"), t("По центру", "Center"), t("Снизу", "Bottom")}, "50", true, host);
            check(c, list, t("Во всех профилях", "On every profile"), AHbsoluteBanner.everywhere(), false,
                    () -> { AHbsoluteBanner.setEverywhere(!AHbsoluteBanner.everywhere()); host.rebuild(); });
            info(c, list, t("Выключено — баннер только в вашем профиле. Включено — за аватарками всех людей и чатов.",
                    "Off: only on your own profile. On: behind the avatars of all people and chats."));
        }

        // ---------- reposts ----------
        header(c, list, t("Репосты", "Reposts"));
        uiCheck(c, list, t("Кнопка «Репост» в каналах", "\"Repost\" button in channels"), "repost", true);
        final Runnable reposts = host.reposts();
        TextSettingsCell rp = new TextSettingsCell(c);
        int n = AHbsoluteReposts.count();
        rp.setTextAndValue(t("Мои репосты", "My reposts"), String.valueOf(n), false);
        sel(rp);
        rp.setOnClickListener(v -> {
            if (reposts != null) reposts.run();
            else Toast.makeText(c, t("Откройте их в своём профиле в AHbsolute.", "Open them on your profile in AHbsolute."), Toast.LENGTH_SHORT).show();
        });
        list.addCard(rp, -2);
        info(c, list, t("Зажмите пост в канале → «Репост». Он появится в разделе «Репосты» вашего профиля, оттуда можно перейти к посту.",
                "Hold a post in a channel → \"Repost\". It appears in the \"Reposts\" section of your profile, and opens the original post from there."));

        // ---------- avatar shape ----------
        header(c, list, t("Форма аватара", "Avatar shape"));
        String[] names = {t("Круг", "Circle"), t("Квадрат", "Square"), t("Звезда", "Star"), t("Треугольник", "Triangle")};
        int my = AHbsolute.getMyShape();
        for (int i = 0; i < names.length; i++) {
            final int shape = i;
            View pv = new View(c) {
                private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas) {
                    p.setColor(color(Theme.key_windowBackgroundWhiteBlueHeader));
                    float s = Math.min(getWidth(), getHeight());
                    canvas.save();
                    canvas.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
                    canvas.drawPath(AHbsolute.cachedPath(shape, s, s), p);
                    canvas.restore();
                }
            };
            LinearLayout row = radioRow(c, pv, names[i], null, my == shape, true);
            row.setOnClickListener(v -> { AHbsolute.setMyShape(shape); host.rebuild(); });
            list.addCard(row, dp(56));
        }
        check(c, list, t("Показывать форму пользователям AHbsolute", "Show shape to AHbsolute users"), AHbsolute.isShareEnabled(), false,
                () -> AHbsolute.setShareEnabled(!AHbsolute.isShareEnabled()));
        info(c, list, t("К вашей фамилии добавляется невидимый маркер. В обычном Telegram аватар остаётся круглым, имя выглядит так же. Выключите — маркер удалится.",
                "An invisible marker is added to your last name. Regular Telegram still shows a round avatar and the same name. Turn off to remove it."));

        // ---------- interface ----------
        header(c, list, t("Интерфейс", "Interface"));
        check(c, list, t("Функции оформления AHbsolute", "AHbsolute interface features"), AHbsoluteUi.getBool("master"), true,
                () -> { AHbsoluteUi.toggle(c, "master"); host.rebuild(); });
        if (AHbsoluteUi.getBool("master")) {
            uiCheck(c, list, t("Стеклянная нижняя панель", "Glass bottom panel"), "glass", true);
            uiCheck(c, list, t("Обои чата по теме", "Chat wallpaper from theme"), "wp", true);
            uiCheck(c, list, t("Дрожь чата при удержании", "Chat shakes while held"), "shake", true);
            uiCheck(c, list, t("Анимация открытия чата", "Chat opening animation"), "anim", true);
            uiCheck(c, list, t("Двойное нажатие — быстрый просмотр", "Double tap to peek"), "peek", true);
            uiCheck(c, list, t("Изменять сообщения (только у меня)", "Edit messages (only for me)"), "localedit", true);
            check(c, list, t("Размытие при быстрой прокрутке", "Blur on fast scrolling"), AHbsoluteBlur.enabled(c), false,
                    () -> AHbsoluteBlur.setEnabled(c, !AHbsoluteBlur.enabled(c)));
        }
        final Runnable editor = host.interfaceEditor();
        if (editor != null) {
            TextSettingsCell order = new TextSettingsCell(c);
            order.setText(t("Порядок пунктов бокового меню", "Side menu order"), false);
            sel(order);
            order.setOnClickListener(v -> editor.run());
            list.addCard(order, -2);
        }
        info(c, list, t("Выключите «Функции оформления» — и Telegram будет выглядеть как обычно. Часть эффектов применяется после перезапуска приложения.",
                "Turn the main switch off and everything looks like regular Telegram. Some effects apply after restarting the app."));

        // ---------- themes ----------
        header(c, list, t("Темы AHbsolute", "AHbsolute themes"));
        String selTheme = AHbsoluteThemes.selectedId(c);
        LinearLayout std = radioRow(c, thumb(c, null, 0xFF5B95C2), t("Стандартная тема Telegram", "Default Telegram theme"), null, selTheme == null || selTheme.isEmpty(), true);
        std.setOnClickListener(v -> themeResult(c, AHbsoluteThemes.reset(c), host));
        list.addCard(std, dp(56));
        for (int i = 0; i < AHbsoluteBanner.BUILTIN.length; i++) {
            final String id = AHbsoluteBanner.BUILTIN[i];
            LinearLayout row = radioRow(c, thumb(c, AHbsoluteBanner.load(c, id, 16), Color.GRAY), AHbsoluteBanner.BUILTIN_NAMES[i], null,
                    id.equals(selTheme), i < AHbsoluteBanner.BUILTIN.length - 1);
            row.setOnClickListener(v -> themeResult(c, AHbsoluteThemes.apply(c, AHbsoluteThemes.indexOf(id)), host));
            list.addCard(row, dp(56));
        }
        info(c, list, t("Тема заменяет светлую/тёмную тему Telegram. Автосмена ночной темы отключается.",
                "The theme replaces Telegram's light/dark theme. Automatic night mode is turned off."));

        // ---------- visible to AHbsolute users ----------
        header(c, list, t("Видно пользователям AHbsolute", "Visible to AHbsolute users"));
        check(c, list, t("Показывать мой профиль пользователям AHbsolute", "Show my profile to AHbsolute users"), AHbsoluteShare.enabled(), AHbsoluteShare.enabled(),
                () -> {
                    boolean on = !AHbsoluteShare.enabled();
                    AHbsoluteShare.setEnabled(on);
                    if (on) AHbsoluteShare.publish(err -> Toast.makeText(c, err == null ? t("Профиль опубликован в личном канале", "Profile published to your personal channel") : err, Toast.LENGTH_LONG).show());
                    host.rebuild();
                });
        if (AHbsoluteShare.enabled()) {
            TextSettingsCell pub = new TextSettingsCell(c);
            pub.setText(t("Обновить сейчас", "Update now"), false);
            sel(pub);
            pub.setOnClickListener(v -> AHbsoluteShare.publish(err -> Toast.makeText(c, err == null ? t("Профиль обновлён", "Profile updated") : err, Toast.LENGTH_LONG).show()));
            list.addCard(pub, -2);
        }
        info(c, list, t("Ваш баннер, обои, стекло и репосты постов увидят пользователи AHbsolute, открыв ваш профиль. Нужен личный канал (свой профиль → «Изменить» → «Личный канал»): AHbsolute публикует туда пост «Мой профиль в AHbsolute» и картинки. В обычном Telegram это просто посты вашего канала. Репосты обычных сообщений не публикуются никогда.",
                "AHbsolute users who open your profile will see your banner, wallpaper, glass and reposts of posts. A personal channel is needed (your profile → Edit → Personal channel): AHbsolute posts \"My profile in AHbsolute\" and the pictures there. In regular Telegram these are just posts of your channel. Reposts of ordinary messages are never published."));

        // ---------- settings transfer ----------
        header(c, list, t("Перенос настроек", "Settings transfer"));
        TextSettingsCell copyCode = new TextSettingsCell(c);
        copyCode.setText(t("Скопировать настройки", "Copy settings"), true);
        sel(copyCode);
        copyCode.setOnClickListener(v -> {
            try {
                AHbsoluteSettingsCode.copy(c);
                Toast.makeText(c, t("Код настроек скопирован. Вставьте его в «Ввод настроек», чтобы вернуть всё как сейчас.", "Settings code copied. Paste it into \"Enter settings\" to get everything back."), Toast.LENGTH_LONG).show();
            } catch (Throwable e) {
                Toast.makeText(c, String.valueOf(e), Toast.LENGTH_LONG).show();
            }
        });
        list.addCard(copyCode, -2);
        TextSettingsCell enterCode = new TextSettingsCell(c);
        enterCode.setText(t("Ввод настроек", "Enter settings"), false);
        sel(enterCode);
        enterCode.setOnClickListener(v -> {
            final android.widget.EditText et = new android.widget.EditText(c);
            et.setHint(t("Вставьте код настроек", "Paste the settings code"));
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setMaxLines(4);
            et.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
            et.setHintTextColor(color(Theme.key_windowBackgroundWhiteGrayText));
            android.widget.FrameLayout box = new android.widget.FrameLayout(c);
            box.setPadding(dp(24), dp(8), dp(24), 0);
            box.addView(et);
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                CharSequence clip = cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0 ? cm.getPrimaryClip().getItemAt(0).getText() : null;
                if (clip != null && clip.toString().matches("\\d{20,}")) et.setText(clip);
            } catch (Throwable ignored) { }
            Runnable apply = () -> {
                String err = AHbsoluteSettingsCode.importCode(c, et.getText().toString());
                Toast.makeText(c, err == null ? t("Настройки восстановлены", "Settings restored") : err, Toast.LENGTH_LONG).show();
                host.rebuild();
            };
            try {
                new AlertDialog.Builder(c).setTitle(t("Ввод настроек", "Enter settings")).setView(box)
                        .setPositiveButton(t("Применить", "Apply"), (d, w) -> apply.run())
                        .setNegativeButton(t("Отмена", "Cancel"), null).show();
            } catch (Throwable e) {
                new android.app.AlertDialog.Builder(c).setTitle(t("Ввод настроек", "Enter settings")).setView(box)
                        .setPositiveButton(t("Применить", "Apply"), (d, w) -> apply.run())
                        .setNegativeButton(t("Отмена", "Cancel"), null).show();
            }
        });
        list.addCard(enterCode, -2);
        info(c, list, t("«Скопировать настройки» кладёт в буфер обмена число — это все настройки AHbsolute. Введите его в «Ввод настроек» (на этом или другом телефоне) — и всё станет как было. Свои картинки из галереи в число не входят.",
                "\"Copy settings\" puts a number into the clipboard — that is all AHbsolute settings. Enter it in \"Enter settings\" (on this or another phone) and everything is back as it was. Your own gallery pictures are not part of the number."));

        // ---------- storage ----------
        header(c, list, t("Память", "Storage"));
        check(c, list, t("Очищать кэш старше 30 дней", "Clear cache older than 30 days"), AHbsolute.isCleanEnabled(), false,
                () -> AHbsolute.setCleanEnabled(!AHbsolute.isCleanEnabled()));
        info(c, list, t("Выполняется в фоне при запуске и касается только кэша приложения.", "Runs in the background on start and only touches the app cache."));

        // ---------- about ----------
        TextSettingsCell diag = new TextSettingsCell(c);
        diag.setText(t("Диагностика", "Diagnostics"), false);
        sel(diag);
        // one tap: diagnostics; two quick taps: the version easter egg
        final Runnable showDiag = () -> {
            try {
                new AlertDialog.Builder(c).setTitle(t("Диагностика", "Diagnostics")).setMessage(AHbsoluteUi.diagText(c))
                        .setPositiveButton("OK", null).show();
            } catch (Throwable e) {
                Toast.makeText(c, AHbsoluteUi.diagText(c), Toast.LENGTH_LONG).show();
            }
        };
        final long[] lastTap = {0};
        diag.setOnClickListener(v -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastTap[0] < 350) {
                lastTap[0] = 0;
                v.removeCallbacks(showDiag);
                host.openVersion();
            } else {
                lastTap[0] = now;
                v.postDelayed(showDiag, 360);
            }
        });
        list.addCard(diag, -2);
        TextInfoPrivacyCell about = new TextInfoPrivacyCell(c);
        about.setText(t("AHbsolute — неофициальный клиент. Он использует Telegram API, но не создан и не одобрен Telegram.",
                "AHbsolute is an unofficial client. It uses the Telegram API but is not made or endorsed by Telegram."));
        about.setTextGravity(Gravity.CENTER_HORIZONTAL);
        list.addPlain(about);
    }

    private static void themeResult(Context c, String err, Host host) {
        if (err == null) {
            Toast.makeText(c, t("Готово. Перезапустите приложение, если цвета обновились не везде.",
                    "Done. Restart the app if not everything changed colour."), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(c, t("Не удалось применить тему: ", "Could not apply theme: ") + err, Toast.LENGTH_LONG).show();
        }
        AHbsoluteBanner.invalidate();
        AHbsoluteProfile.invalidate();
        host.rebuild();
    }
}
