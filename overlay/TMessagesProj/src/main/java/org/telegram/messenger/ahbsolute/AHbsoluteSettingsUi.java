package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RadioButton;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * The single AHbsolute settings screen, built from Telegram's own list cells (HeaderCell, TextCheckCell, TextSettingsCell,
 * TextInfoPrivacyCell, ShadowSectionCell) so it looks exactly like any other Telegram settings page.
 * Used by Settings → AHbsolute (inside Telegram) and by the second launcher icon "AHbsolute ⚙".
 */
public class AHbsoluteSettingsUi {

    public static final int REQ_BANNER = 9301;

    public interface Host {
        /** start the system picture picker; the result must go to {@link #onPicked} */
        void pickBanner();
        /** null when the side-menu editor cannot be opened from this host */
        Runnable interfaceEditor();
        void rebuild();
    }

    private static int dp(float v) { return AndroidUtilities.dp(v); }
    private static int color(int key) { return Theme.getColor(key); }

    /** intent for the picture picker */
    public static Intent pickIntent() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        return Intent.createChooser(i, t("Выберите баннер", "Choose a banner"));
    }

    /** handles the picker result (both hosts call this from onActivityResult) */
    public static void onPicked(final Context c, int requestCode, int resultCode, Intent data, final Host host) {
        if (requestCode != REQ_BANNER || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Toast.makeText(c, t("Загружаю баннер…", "Loading banner…"), Toast.LENGTH_SHORT).show();
        AHbsoluteBanner.importFromUri(c, data.getData(), (id, err) -> {
            if (id == null) {
                Toast.makeText(c, t("Не удалось загрузить картинку: ", "Could not load the picture: ") + err, Toast.LENGTH_LONG).show();
                return;
            }
            AHbsoluteBanner.setMode(id);
            AHbsoluteBanner.invalidate();
            if (host != null) host.rebuild();
        });
    }

    /* ------------------------------------------------------------ building blocks ------------------------------------------------------------ */

    private static void header(Context c, LinearLayout list, String text) {
        HeaderCell h = new HeaderCell(c);
        h.setText(text);
        h.setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        list.addView(h, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void shadow(Context c, LinearLayout list) {
        list.addView(new ShadowSectionCell(c), new LinearLayout.LayoutParams(-1, -2));
    }

    /** grey hint under a section (ends the section, like in Telegram) */
    private static void info(Context c, LinearLayout list, String text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(c);
        cell.setText(text);
        cell.setBackground(Theme.getThemedDrawableByKey(c, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        list.addView(cell, new LinearLayout.LayoutParams(-1, -2));
    }

    private static View white(View v) {
        v.setBackground(Theme.getSelectorDrawable(true));
        return v;
    }

    private static void check(Context c, LinearLayout list, String text, boolean value, boolean divider, final Runnable onToggle) {
        final TextCheckCell cell = new TextCheckCell(c);
        cell.setTextAndCheck(text, value, divider);
        white(cell);
        cell.setOnClickListener(v -> {
            onToggle.run();
            cell.setChecked(!cell.isChecked());
        });
        list.addView(cell, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void uiCheck(final Context c, LinearLayout list, String text, final String key, boolean divider) {
        check(c, list, text, AHbsoluteUi.getBool(key), divider, () -> AHbsoluteUi.toggle(c, key));
    }

    /** "Title ........ value" row that opens a Telegram choice dialog */
    private static void choice(final Context c, LinearLayout list, final String title, final String key, final int[] values, final String[] names,
                               final int def, boolean divider, final Host host) {
        int cur = AHbsoluteUi.getInt(key, def);
        String value = null;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) value = names[i];
        if (value == null) value = cur + "%";
        TextSettingsCell cell = new TextSettingsCell(c);
        cell.setTextAndValue(title, value, divider);
        white(cell);
        cell.setOnClickListener(v -> {
            try {
                new AlertDialog.Builder(c)
                        .setTitle(title)
                        .setItems(names, (d, which) -> {
                            AHbsoluteUi.putInt(key, values[which]);
                            AHbsoluteBanner.invalidate();
                            host.rebuild();
                        })
                        .show();
            } catch (Throwable e) {
                // fallback: cycle through the values
                int idx = 0;
                for (int i = 0; i < values.length; i++) if (values[i] == AHbsoluteUi.getInt(key, def)) idx = i;
                AHbsoluteUi.putInt(key, values[(idx + 1) % values.length]);
                AHbsoluteBanner.invalidate();
                host.rebuild();
            }
        });
        list.addView(cell, new LinearLayout.LayoutParams(-1, -2));
    }

    /** row with a picture on the left, a title and Telegram's radio button on the right */
    private static LinearLayout radioRow(Context c, View image, String title, String subtitle, boolean checked, boolean divider) {
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
        white(row);
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

    /* ------------------------------------------------------------ profile preview ------------------------------------------------------------ */

    private static class Preview extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint av = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint status = new Paint(Paint.ANTI_ALIAS_FLAG);
        Preview(Context c) {
            super(c);
            name.setTextAlign(Paint.Align.CENTER);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            status.setTextAlign(Paint.Align.CENTER);
        }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(180), MeasureSpec.EXACTLY));
        }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            int head;
            try { head = Theme.getColor(Theme.key_avatar_backgroundActionBarBlue); } catch (Throwable e) { head = 0xFF5B95C2; }
            float a = 1f;
            try { a = AHbsoluteUi.profileHeaderAlpha(); } catch (Throwable ignored) { }
            bg.setColor(head);
            bg.setAlpha(Math.round(255 * a));
            canvas.drawRect(0, 0, w, h, bg);
            AHbsoluteBanner.drawPreview(getContext(), canvas, w, h);
            float r = dp(38), cx = w / 2f, cy = dp(66);
            av.setColor(0xFF8E7CC3);
            canvas.save();
            canvas.translate(cx - r, cy - r);
            canvas.drawPath(AHbsolute.cachedPath(AHbsolute.getMyShape(), r * 2, r * 2), av);
            canvas.restore();
            name.setColor(Color.WHITE);
            name.setTextSize(dp(18));
            canvas.drawText(t("Вы", "You"), cx, cy + r + dp(28), name);
            status.setColor(0xCCFFFFFF);
            status.setTextSize(dp(13));
            canvas.drawText(t("в сети", "online"), cx, cy + r + dp(48), status);
        }
    }

    /* ------------------------------------------------------------ the screen ------------------------------------------------------------ */

    public static void build(final Context c, final LinearLayout list, final Host host) {
        list.removeAllViews();
        try { Theme.createCommonResources(c); } catch (Throwable ignored) { }

        // ---------- banner ----------
        list.addView(new Preview(c), new LinearLayout.LayoutParams(-1, -2));
        header(c, list, t("Баннер профиля", "Profile banner"));
        String mode = AHbsoluteBanner.mode();

        LinearLayout none = radioRow(c, thumb(c, null, color(Theme.key_windowBackgroundGray)), t("Без баннера", "No banner"), null,
                AHbsoluteBanner.OFF.equals(mode), true);
        none.setOnClickListener(v -> { AHbsoluteBanner.setMode(AHbsoluteBanner.OFF); AHbsoluteBanner.invalidate(); host.rebuild(); });
        list.addView(none, new LinearLayout.LayoutParams(-1, dp(56)));

        ArrayList<String> mine = AHbsoluteBanner.userBanners(c);
        for (int i = 0; i < mine.size(); i++) {
            final String id = mine.get(i);
            LinearLayout row = radioRow(c, thumb(c, AHbsoluteBanner.load(c, id, 8), Color.GRAY),
                    t("Мой баннер ", "My banner ") + (i + 1), t("Нажмите и удерживайте, чтобы удалить", "Long press to delete"), id.equals(mode), true);
            row.setOnClickListener(v -> { AHbsoluteBanner.setMode(id); AHbsoluteBanner.invalidate(); host.rebuild(); });
            row.setOnLongClickListener(v -> {
                try {
                    new AlertDialog.Builder(c)
                            .setTitle(t("Удалить баннер?", "Delete banner?"))
                            .setMessage(t("Картинка будет удалена из AHbsolute.", "The picture will be removed from AHbsolute."))
                            .setPositiveButton(t("Удалить", "Delete"), (d, w) -> { AHbsoluteBanner.delete(c, id); host.rebuild(); })
                            .setNegativeButton(t("Отмена", "Cancel"), null)
                            .show();
                } catch (Throwable e) {
                    AHbsoluteBanner.delete(c, id);
                    host.rebuild();
                }
                return true;
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        }

        for (int i = 0; i < AHbsoluteBanner.BUILTIN.length; i++) {
            final String id = AHbsoluteBanner.BUILTIN[i];
            LinearLayout row = radioRow(c, thumb(c, AHbsoluteBanner.load(c, id, 16), Color.GRAY), AHbsoluteBanner.BUILTIN_NAMES[i], null, id.equals(mode), true);
            row.setOnClickListener(v -> { AHbsoluteBanner.setMode(id); AHbsoluteBanner.invalidate(); host.rebuild(); });
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        }
        LinearLayout byTheme = radioRow(c, null, t("По теме AHbsolute", "Match AHbsolute theme"), null, AHbsoluteBanner.THEME.equals(mode), true);
        byTheme.setOnClickListener(v -> { AHbsoluteBanner.setMode(AHbsoluteBanner.THEME); AHbsoluteBanner.invalidate(); host.rebuild(); });
        list.addView(byTheme, new LinearLayout.LayoutParams(-1, dp(56)));

        TextCell upload = new TextCell(c);
        upload.setTextAndIcon(t("Загрузить баннер из галереи", "Upload banner from gallery"), R.drawable.msg_addphoto, false);
        upload.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
        white(upload);
        upload.setOnClickListener(v -> host.pickBanner());
        list.addView(upload, new LinearLayout.LayoutParams(-1, -2));
        info(c, list, t("Баннер показывается за аватаркой и именем в профиле. Подойдёт любая картинка, лучше горизонтальная. Баннер видите только вы — в Telegram нет поля, через которое его можно передать другим.",
                "The banner is shown behind the avatar and the name in the profile. Any picture works, landscape is best. Only you see it: Telegram has no field to send it to others."));

        if (!AHbsoluteBanner.OFF.equals(mode)) {
            header(c, list, t("Настройка баннера", "Banner settings"));
            int[] p100 = {100, 85, 70, 55, 40};
            choice(c, list, t("Яркость", "Brightness"), "banner_op", p100, new String[]{"100%", "85%", "70%", "55%", "40%"}, 100, true, host);
            choice(c, list, t("Затемнение", "Darkening"), "banner_dim", new int[]{0, 15, 30, 45, 60},
                    new String[]{t("Нет", "None"), "15%", "30%", "45%", "60%"}, 15, true, host);
            choice(c, list, t("Затухание книзу", "Fade to bottom"), "banner_fade", new int[]{0, 20, 40, 70, 100},
                    new String[]{t("Нет", "None"), "20%", "40%", "70%", "100%"}, 40, true, host);
            choice(c, list, t("Положение картинки", "Picture position"), "banner_pos", new int[]{0, 50, 100},
                    new String[]{t("Сверху", "Top"), t("По центру", "Center"), t("Снизу", "Bottom")}, 50, true, host);
            choice(c, list, t("Прозрачность шапки", "Header transparency"), "prof_alpha", new int[]{100, 75, 50, 25, 0},
                    new String[]{t("Обычная", "Normal"), "75%", "50%", "25%", t("Прозрачная", "Transparent")}, 100, true, host);
            check(c, list, t("Во всех профилях", "On every profile"), AHbsoluteBanner.everywhere(), false,
                    () -> { AHbsoluteBanner.setEverywhere(!AHbsoluteBanner.everywhere()); host.rebuild(); });
            info(c, list, t("Выключено — баннер только в вашем профиле. Включено — за аватарками всех людей и чатов.",
                    "Off: only on your own profile. On: behind the avatars of all people and chats."));
        }

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
            LinearLayout row = radioRow(c, pv, names[i], null, my == shape, i < names.length - 1);
            row.setOnClickListener(v -> { AHbsolute.setMyShape(shape); host.rebuild(); });
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        }
        shadow(c, list);
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
            uiCheck(c, list, t("Компактная верхняя панель", "Compact rounded top bar"), "topbar", true);
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
            white(order);
            order.setOnClickListener(v -> editor.run());
            list.addView(order, new LinearLayout.LayoutParams(-1, -2));
        }
        info(c, list, t("Выключите «Функции оформления» — и Telegram будет выглядеть как обычно. Часть эффектов применяется после перезапуска приложения.",
                "Turn the main switch off and everything looks like regular Telegram. Some effects apply after restarting the app."));

        // ---------- themes ----------
        header(c, list, t("Темы AHbsolute", "AHbsolute themes"));
        String sel = AHbsoluteThemes.selectedId(c);
        LinearLayout std = radioRow(c, thumb(c, null, 0xFF5B95C2), t("Стандартная тема Telegram", "Default Telegram theme"), null, sel == null || sel.isEmpty(), true);
        std.setOnClickListener(v -> themeResult(c, AHbsoluteThemes.reset(c), host));
        list.addView(std, new LinearLayout.LayoutParams(-1, dp(56)));
        for (int i = 0; i < AHbsoluteBanner.BUILTIN.length; i++) {
            final int idx = i;
            LinearLayout row = radioRow(c, thumb(c, AHbsoluteBanner.load(c, AHbsoluteBanner.BUILTIN[i], 16), Color.GRAY), AHbsoluteBanner.BUILTIN_NAMES[i], null,
                    AHbsoluteBanner.BUILTIN[i].equals(sel), i < AHbsoluteBanner.BUILTIN.length - 1);
            row.setOnClickListener(v -> themeResult(c, AHbsoluteThemes.apply(c, idx), host));
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        }
        info(c, list, t("Тема заменяет светлую/тёмную тему Telegram. Автосмена ночной темы отключается.",
                "The theme replaces Telegram's light/dark theme. Automatic night mode is turned off."));

        // ---------- storage ----------
        header(c, list, t("Память", "Storage"));
        check(c, list, t("Очищать кэш старше 30 дней", "Clear cache older than 30 days"), AHbsolute.isCleanEnabled(), false,
                () -> AHbsolute.setCleanEnabled(!AHbsolute.isCleanEnabled()));
        info(c, list, t("Выполняется в фоне при запуске и касается только кэша приложения.", "Runs in the background on start and only touches the app cache."));

        // ---------- about ----------
        header(c, list, t("О приложении", "About"));
        TextSettingsCell diag = new TextSettingsCell(c);
        diag.setText(t("Диагностика", "Diagnostics"), false);
        white(diag);
        diag.setOnClickListener(v -> {
            try {
                new AlertDialog.Builder(c).setTitle(t("Диагностика", "Diagnostics")).setMessage(AHbsoluteUi.diagText(c))
                        .setPositiveButton("OK", null).show();
            } catch (Throwable e) {
                Toast.makeText(c, AHbsoluteUi.diagText(c), Toast.LENGTH_LONG).show();
            }
        });
        list.addView(diag, new LinearLayout.LayoutParams(-1, -2));
        TextInfoPrivacyCell about = new TextInfoPrivacyCell(c);
        about.setText(t("AHbsolute — неофициальный клиент. Он использует Telegram API, но не создан и не одобрен Telegram.",
                "AHbsolute is an unofficial client. It uses the Telegram API but is not made or endorsed by Telegram."));
        about.setTextGravity(Gravity.CENTER_HORIZONTAL);
        about.setBackground(Theme.getThemedDrawableByKey(c, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        list.addView(about, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void themeResult(Context c, String err, Host host) {
        if (err == null) {
            Toast.makeText(c, t("Готово. Перезапустите приложение, если цвета обновились не везде.",
                    "Done. Restart the app if not everything changed colour."), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(c, t("Не удалось применить тему: ", "Could not apply theme: ") + err, Toast.LENGTH_LONG).show();
        }
        host.rebuild();
    }
}
