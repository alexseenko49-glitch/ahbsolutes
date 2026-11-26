package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * AHbsolute custom Telegram themes (they replace the light / dark theme).
 * A theme = generated .attheme file (colors + wallpaper from assets/ahb_themes/*.jpg) applied through
 * Telegram's own Theme class. Every call into Telegram's Theme class goes through reflection, so a change in
 * Telegram's internal API can never break the build: it only makes the apply step show an error message.
 */
public class AHbsoluteThemes {

    private static final String[] IDS = {"vice", "chess", "absolut", "teg"};
    private static final String[] NAMES = {"Vice", "Chess", "Absolut", "Teg"};
    // dark, bg, bgGray, text, hint, accent, bar, bubbleIn, bubbleOut, textOut, link
    private static final long[][] P = {
        // vice: deep blue -> magenta
        {1, 0xFF0B1252L, 0xFF070C3DL, 0xFFFFFFFFL, 0xFFB6A9E6L, 0xFFFF1FA6L, 0xFF0F1566L, 0xFF1D2478L, 0xFFB5128AL, 0xFFFFFFFFL, 0xFFFF7AD0L},
        // chess: green / cream
        {0, 0xFFF3F3E3L, 0xFFE4E7CFL, 0xFF1F2A14L, 0xFF6B7A57L, 0xFF6E9150L, 0xFF6E9150L, 0xFFF6F6E6L, 0xFFC9DDAFL, 0xFF1F2A14L, 0xFF3F6B22L},
        // absolut: lilac
        {0, 0xFFF5E6F3L, 0xFFE8CCE3L, 0xFF2B1029L, 0xFF7A5575L, 0xFF8E4B88L, 0xFFA8689FL, 0xFFF7E9F5L, 0xFFD9A5D2L, 0xFF2B1029L, 0xFF6A2F65L},
        // teg: dark slate
        {1, 0xFF1B242EL, 0xFF121A22L, 0xFFE6EDF3L, 0xFF7F8E9CL, 0xFF3A86C8L, 0xFF121A22L, 0xFF263340L, 0xFF2E5C86L, 0xFFFFFFFFL, 0xFF6DB3F2L},
    };

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("ahb_theme_ui", Context.MODE_PRIVATE); }

    private static void put(StringBuilder sb, String key, long color) {
        sb.append(key).append("=#").append(String.format("%08X", color & 0xFFFFFFFFL)).append('\n');
    }

    private static byte[] readAsset(Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open("ahb_themes/" + name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    private static File buildFile(Context c, int i) throws Exception {
        long[] p = P[i];
        long bg = p[1], gray = p[2], text = p[3], hint = p[4], acc = p[5], bar = p[6], bIn = p[7], bOut = p[8], tOut = p[9], link = p[10];
        long onBar = 0xFFFFFFFFL;
        StringBuilder s = new StringBuilder();
        put(s, "windowBackgroundWhite", bg); put(s, "windowBackgroundGray", gray);
        put(s, "windowBackgroundWhiteBlackText", text);
        put(s, "windowBackgroundWhiteGrayText", hint); put(s, "windowBackgroundWhiteGrayText2", hint);
        put(s, "windowBackgroundWhiteGrayText3", hint); put(s, "windowBackgroundWhiteGrayText4", hint);
        put(s, "windowBackgroundWhiteGrayText5", hint); put(s, "windowBackgroundWhiteGrayText6", hint);
        put(s, "windowBackgroundWhiteGrayText7", hint); put(s, "windowBackgroundWhiteGrayText8", hint);
        put(s, "windowBackgroundWhiteBlueText", acc); put(s, "windowBackgroundWhiteBlueText2", acc);
        put(s, "windowBackgroundWhiteBlueText3", acc); put(s, "windowBackgroundWhiteBlueText4", acc);
        put(s, "windowBackgroundWhiteBlueText5", acc); put(s, "windowBackgroundWhiteBlueText6", acc);
        put(s, "windowBackgroundWhiteBlueText7", acc); put(s, "windowBackgroundWhiteBlueHeader", acc);
        put(s, "windowBackgroundWhiteBlueIcon", acc); put(s, "windowBackgroundWhiteGrayIcon", hint);
        put(s, "windowBackgroundWhiteLinkText", link); put(s, "windowBackgroundWhiteValueText", acc);
        put(s, "windowBackgroundWhiteInputField", hint); put(s, "windowBackgroundWhiteInputFieldActivated", acc);
        put(s, "windowBackgroundChecked", gray); put(s, "windowBackgroundUnchecked", bg);
        put(s, "actionBarDefault", bar); put(s, "actionBarDefaultIcon", onBar); put(s, "actionBarDefaultTitle", onBar);
        put(s, "actionBarDefaultSubtitle", onBar); put(s, "actionBarDefaultSelector", 0x33FFFFFFL);
        put(s, "actionBarDefaultSearch", onBar); put(s, "actionBarDefaultSearchPlaceholder", 0x99FFFFFFL);
        put(s, "actionBarDefaultSubmenuBackground", bg); put(s, "actionBarDefaultSubmenuItem", text);
        put(s, "actionBarDefaultSubmenuItemIcon", hint);
        put(s, "actionBarActionModeDefault", bg); put(s, "actionBarActionModeDefaultTop", gray);
        put(s, "actionBarActionModeDefaultIcon", text); put(s, "actionBarActionModeDefaultSelector", 0x22888888L);
        put(s, "dialogBackground", bg); put(s, "dialogBackgroundGray", gray);
        put(s, "dialogTextBlack", text); put(s, "dialogTextGray", hint); put(s, "dialogTextGray2", hint);
        put(s, "dialogTextGray3", hint); put(s, "dialogTextGray4", hint); put(s, "dialogTextBlue", acc);
        put(s, "dialogTextBlue2", acc); put(s, "dialogTextBlue3", acc); put(s, "dialogTextBlue4", acc);
        put(s, "dialogTextLink", link); put(s, "dialogLinkSelection", 0x33888888L);
        put(s, "dialogButton", acc); put(s, "dialogButtonSelector", 0x22888888L);
        put(s, "dialogInputField", hint); put(s, "dialogInputFieldActivated", acc);
        put(s, "divider", 0x22888888L); put(s, "listSelector", 0x22888888L);
        put(s, "key_graySection", gray); put(s, "graySection", gray); put(s, "graySectionText", acc);
        put(s, "chats_name", text); put(s, "chats_message", hint); put(s, "chats_message_threeLines", hint);
        put(s, "chats_nameMessage", acc); put(s, "chats_nameMessage_threeLines", acc);
        put(s, "chats_attachMessage", acc); put(s, "chats_draft", 0xFFE05252L);
        put(s, "chats_date", hint); put(s, "chats_pinnedIcon", hint); put(s, "chats_pinnedOverlay", 0x0A888888L);
        put(s, "chats_unreadCounter", acc); put(s, "chats_unreadCounterText", onBar); put(s, "chats_unreadCounterMuted", hint);
        put(s, "chats_sentCheck", acc); put(s, "chats_sentReadCheck", acc); put(s, "chats_sentClock", hint);
        put(s, "chats_menuBackground", bg); put(s, "chats_menuItemText", text); put(s, "chats_menuItemIcon", hint);
        put(s, "chats_menuName", onBar); put(s, "chats_menuPhone", onBar); put(s, "chats_menuPhoneCats", onBar);
        put(s, "chats_menuTopBackground", bar); put(s, "chats_menuTopShadow", 0x00000000L);
        put(s, "chats_actionIcon", onBar); put(s, "chats_actionBackground", acc); put(s, "chats_actionPressedBackground", acc);
        put(s, "chat_wallpaper", gray);
        put(s, "chat_inBubble", bIn); put(s, "chat_inBubbleSelected", bIn); put(s, "chat_inBubbleShadow", 0x00000000L);
        put(s, "chat_outBubble", bOut); put(s, "chat_outBubbleSelected", bOut); put(s, "chat_outBubbleShadow", 0x00000000L);
        put(s, "chat_messageTextIn", text); put(s, "chat_messageTextOut", tOut);
        put(s, "chat_messageLinkIn", link); put(s, "chat_messageLinkOut", link);
        put(s, "chat_inTimeText", hint); put(s, "chat_outTimeText", tOut);
        put(s, "chat_outSentCheck", tOut); put(s, "chat_outSentCheckSelected", tOut);
        put(s, "chat_outSentCheckRead", tOut); put(s, "chat_outSentCheckReadSelected", tOut);
        put(s, "chat_outSentClock", tOut); put(s, "chat_inSentClock", hint);
        put(s, "chat_inReplyLine", acc); put(s, "chat_outReplyLine", tOut);
        put(s, "chat_inReplyNameText", acc); put(s, "chat_outReplyNameText", tOut);
        put(s, "chat_inReplyMessageText", text); put(s, "chat_outReplyMessageText", tOut);
        put(s, "chat_inReplyMediaMessageText", hint); put(s, "chat_outReplyMediaMessageText", tOut);
        put(s, "chat_inForwardedNameText", acc); put(s, "chat_outForwardedNameText", tOut);
        put(s, "chat_inViaBotNameText", acc); put(s, "chat_outViaBotNameText", tOut);
        put(s, "chat_inSiteNameText", acc); put(s, "chat_outSiteNameText", tOut);
        put(s, "chat_inLoader", acc); put(s, "chat_outLoader", tOut);
        put(s, "chat_messagePanelBackground", bg); put(s, "chat_messagePanelText", text);
        put(s, "chat_messagePanelHint", hint); put(s, "chat_messagePanelIcons", hint);
        put(s, "chat_messagePanelSend", acc); put(s, "chat_messagePanelCursor", acc);
        put(s, "chat_messagePanelShadow", 0x22000000L);
        put(s, "chat_serviceBackground", 0x66000000L); put(s, "chat_serviceBackgroundSelected", 0x88000000L);
        put(s, "chat_serviceText", 0xFFFFFFFFL); put(s, "chat_serviceLink", 0xFFFFFFFFL); put(s, "chat_serviceIcon", 0xFFFFFFFFL);
        put(s, "chat_topPanelBackground", bg); put(s, "chat_topPanelTitle", acc); put(s, "chat_topPanelMessage", hint);
        put(s, "chat_emojiPanelBackground", bg); put(s, "chat_emojiPanelBackspace", hint);
        put(s, "chat_emojiPanelIcon", hint); put(s, "chat_emojiPanelIconSelected", acc);
        put(s, "chat_unreadMessagesStartBackground", bg); put(s, "chat_unreadMessagesStartText", acc);
        put(s, "profile_title", onBar); put(s, "profile_status", 0xCCFFFFFFL);
        put(s, "profile_actionIcon", onBar); put(s, "profile_actionBackground", acc);
        put(s, "switchTrack", hint); put(s, "switchTrackChecked", acc);
        put(s, "switchTrackBlue", hint); put(s, "switchTrackBlueChecked", acc);
        put(s, "switchTrackBlueThumb", onBar); put(s, "switchTrackBlueThumbChecked", onBar);
        put(s, "switch2Track", hint); put(s, "switch2TrackChecked", acc);
        put(s, "checkbox", acc); put(s, "checkboxCheck", onBar); put(s, "checkboxDisabled", hint);
        put(s, "fastScrollActive", acc); put(s, "fastScrollInactive", hint); put(s, "fastScrollText", onBar);
        put(s, "inappPlayerBackground", bg); put(s, "inappPlayerTitle", acc); put(s, "inappPlayerPerformer", hint);
        put(s, "inappPlayerClose", hint); put(s, "emptyListPlaceholder", hint);
        put(s, "progressCircle", acc); put(s, "featuredStickers_addButton", acc);
        put(s, "player_actionBar", bg); put(s, "player_button", hint); put(s, "player_progress", acc);

        byte[] head = s.toString().getBytes(StandardCharsets.UTF_8);
        byte[] img = readAsset(c, IDS[i] + ".jpg");
        File f = new File(c.getCacheDir(), "AHbsolute-" + NAMES[i] + ".attheme");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(head);
            o.write("WPS\n".getBytes(StandardCharsets.UTF_8));
            o.write(img);
            o.write("\nWPE\n".getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private static Method find(Class<?> cl, String name, int params) {
        for (Method m : cl.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == params) return m;
        }
        return null;
    }

    private static void noAutoNight(Class<?> theme) {
        try { theme.getField("selectedAutoNightType").setInt(null, 0); } catch (Throwable ignore) { }
        try { Method m = find(theme, "saveAutoNightThemeConfig", 0); if (m != null) m.invoke(null); } catch (Throwable ignore) { }
    }

    private static void applyInfo(Class<?> theme, Object info) throws Exception {
        for (Method m : theme.getMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (m.getName().equals("applyTheme") && pt.length == 2 && pt[0].isInstance(info) && pt[1] == boolean.class) {
                m.invoke(null, info, false);
                return;
            }
        }
        throw new NoSuchMethodException("Theme.applyTheme(ThemeInfo, boolean)");
    }

    /** returns null on success, otherwise an error text */
    public static String apply(Context c, int i) {
        try {
            File f = buildFile(c, i);
            Class<?> theme = Class.forName("org.telegram.ui.ActionBar.Theme");
            noAutoNight(theme);
            Method m = null;
            for (Method x : theme.getMethods()) {
                if (x.getName().equals("applyThemeFile") && x.getParameterTypes().length == 4 && x.getParameterTypes()[0] == File.class) m = x;
            }
            if (m == null) return "Theme.applyThemeFile not found";
            Object info = m.invoke(null, f, "AHbsolute " + NAMES[i], null, false);
            if (info == null) return "applyThemeFile returned null";
            applyInfo(theme, info);
            prefs(c).edit().putString("sel", IDS[i]).apply();
            return null;
        } catch (Throwable e) {
            Throwable r = e.getCause() != null ? e.getCause() : e;
            return r.getClass().getSimpleName() + ": " + r.getMessage();
        }
    }

    /** back to Telegram's regular day theme */
    public static String reset(Context c) {
        try {
            Class<?> theme = Class.forName("org.telegram.ui.ActionBar.Theme");
            Method g = null;
            for (Method x : theme.getMethods()) {
                if (x.getName().equals("getTheme") && x.getParameterTypes().length == 1 && x.getParameterTypes()[0] == String.class) g = x;
            }
            if (g == null) return "Theme.getTheme not found";
            Object info = g.invoke(null, "Blue");
            if (info == null) return "default theme not found";
            applyInfo(theme, info);
            prefs(c).edit().remove("sel").apply();
            return null;
        } catch (Throwable e) {
            Throwable r = e.getCause() != null ? e.getCause() : e;
            return r.getClass().getSimpleName() + ": " + r.getMessage();
        }
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** Adds the "THEMES" block to a vertical LinearLayout. Colors are passed in so it fits both Telegram's and plain Android styling. */
    public static void addSection(Context c, LinearLayout content, int headerColor, int textColor, int hintColor, Runnable refresh) {
        TextView head = new TextView(c);
        head.setText(t("ТЕМЫ (вместо светлой/тёмной)", "THEMES (replace light/dark)"));
        head.setTextSize(13);
        head.setTextColor(headerColor);
        head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 6));
        content.addView(head);

        String sel = prefs(c).getString("sel", "");
        for (int i = 0; i <= IDS.length; i++) {
            final int idx = i;
            boolean isReset = i == IDS.length;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 8));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (!isReset) {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 8;
                    byte[] d = readAsset(c, IDS[i] + ".jpg");
                    Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, o);
                    iv.setImageBitmap(b);
                } catch (Throwable e) { iv.setBackgroundColor(Color.GRAY); }
            } else {
                iv.setBackgroundColor(0xFFDDDDDD);
            }
            row.addView(iv, new LinearLayout.LayoutParams(dp(c, 36), dp(c, 36)));
            TextView label = new TextView(c);
            String name = isReset ? t("Стандартная тема Telegram", "Default Telegram theme") : NAMES[i];
            label.setText(name + (!isReset && IDS[i].equals(sel) ? "   ✓" : ""));
            label.setTextSize(16);
            label.setTextColor(textColor);
            label.setPadding(dp(c, 16), 0, 0, 0);
            row.addView(label);
            row.setOnClickListener(v -> {
                String err = isReset ? reset(c) : apply(c, idx);
                if (err == null) {
                    Toast.makeText(c, t("Готово. Полностью закройте Telegram (смахните из недавних) и откройте снова.",
                            "Done. Fully close Telegram (swipe it away from recents) and open it again."), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(c, t("Не удалось применить тему: ", "Could not apply theme: ") + err, Toast.LENGTH_LONG).show();
                }
                if (refresh != null) refresh.run();
            });
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView note = new TextView(c);
        note.setText(t("Тема применяется ко всему приложению вместо светлой/тёмной. Автосмена дневной/ночной темы отключается.",
                "The theme is applied app-wide instead of light/dark. Automatic day/night switching is turned off."));
        note.setTextSize(12);
        note.setTextColor(hintColor);
        note.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
        content.addView(note);
    }
}
