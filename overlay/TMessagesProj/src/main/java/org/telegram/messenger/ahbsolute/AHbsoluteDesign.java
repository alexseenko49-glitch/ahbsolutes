package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * "Absolute Design 1": one switch that turns the whole client into the AHbsolute design while keeping every
 * Telegram and AHbsolute function where it is:
 *  - Absolute Design 1 theme (light or night, follows the current Telegram theme) with its own wallpaper
 *  - glass profile: the design wallpaper behind the profile, frosted translucent cards, banner on top
 *  - glass bottom panel, compact rounded top bar, chat opening animation
 * Everything it changes is remembered and restored when the switch is turned off.
 */
public class AHbsoluteDesign {

    private static final String[] UI_KEYS = {"master", "glass", "topbar", "anim", "wp", "msgglass", "chatcards", "mainwall"};
    private static final String[][] STR_KEYS = {
            {"banner", AHbsoluteBanner.OFF}, {"banner_pos", "50"}, {"banner_fade", "40"}, {"banner_dim", "15"}, {"banner_op", "100"},
            {"prof_wp", AHbsoluteBanner.OFF}, {"prof_glass", "0"}, {"prof_glass_op", "62"}, {"prof_wp_blur", "1"}, {"prof_wp_dim", "0"},
            {"chat_gap", "0"}, {"chat_radius", "18"}, {"msgglass_op", "62"},
    };

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("ahb_design", Context.MODE_PRIVATE); }

    public static boolean isOn(Context c) { return c != null && sp(c).getBoolean("d1", false); }

    public static String title() { return "Absolute Design 1"; }

    public static String subtitle() {
        return t("Новый интерфейс AHbsolute: чаты-карточки из стекла поверх фирменных обоев, градиентный заголовок, сообщения Liquid Glass, стеклянный профиль и плавные анимации. Все функции Telegram и AHbsolute на месте.",
                "The new AHbsolute interface: glass chat cards over the signature wallpaper, gradient title, Liquid Glass messages, glass profile and smooth animations. Every Telegram and AHbsolute feature stays.");
    }

    /** returns null on success, otherwise an error text */
    public static String setOn(Context c, boolean on) {
        if (on == isOn(c)) return null;
        String err = null;
        try {
            if (on) {
                // remember everything we are about to change
                JSONObject b = new JSONObject();
                for (String k : UI_KEYS) b.put("b_" + k, AHbsoluteUi.getBool(k));
                for (String[] kv : STR_KEYS) b.put("s_" + kv[0], AHbsoluteUi.getStr(kv[0], kv[1]));
                b.put("theme", AHbsoluteThemes.selectedId(c));
                sp(c).edit().putString("backup", b.toString()).putBoolean("d1", true).apply();

                for (String k : UI_KEYS) if (!AHbsoluteUi.getBool(k)) AHbsoluteUi.toggle(c, k);
                boolean night = false;
                try { night = org.telegram.ui.ActionBar.Theme.isCurrentThemeDark(); } catch (Throwable ignored) { }
                int idx = AHbsoluteThemes.indexOf(night ? "design1n" : "design1");
                if (idx >= 0) err = AHbsoluteThemes.apply(c, idx);
                // glass profile over the design wallpaper; the banner shows the top of the same picture,
                // so header and page read as one continuous image
                AHbsoluteUi.putStr("prof_wp", AHbsoluteBanner.THEME);
                AHbsoluteUi.putStr("prof_glass", "1");
                AHbsoluteUi.putStr("prof_glass_op", night ? "55" : "62");
                AHbsoluteUi.putStr("prof_wp_blur", "1");
                AHbsoluteUi.putStr("banner", AHbsoluteBanner.THEME);
                AHbsoluteUi.putStr("banner_pos", "0");
                AHbsoluteUi.putStr("banner_fade", "0");
                AHbsoluteUi.putStr("banner_dim", night ? "10" : "0");
                AHbsoluteUi.putStr("banner_op", "100");
                // main screen: floating glass chat cards over the design wallpaper, a little air between them
                AHbsoluteUi.putStr("chat_gap", "6");
                AHbsoluteUi.putStr("chat_radius", "20");
                AHbsoluteUi.putStr("msgglass_op", night ? "58" : "66");
            } else {
                JSONObject b = new JSONObject(sp(c).getString("backup", "{}"));
                Iterator<String> it = b.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (k.startsWith("b_")) {
                        String key = k.substring(2);
                        if (AHbsoluteUi.getBool(key) != b.getBoolean(k)) AHbsoluteUi.toggle(c, key);
                    } else if (k.startsWith("s_")) {
                        AHbsoluteUi.putStr(k.substring(2), b.getString(k));
                    }
                }
                String prev = b.optString("theme", "");
                int idx = AHbsoluteThemes.indexOf(prev);
                err = idx >= 0 ? AHbsoluteThemes.apply(c, idx) : AHbsoluteThemes.reset(c);
                sp(c).edit().putBoolean("d1", false).remove("backup").apply();
            }
        } catch (Throwable e) {
            err = String.valueOf(e);
        }
        AHbsoluteBanner.invalidate();
        AHbsoluteProfile.invalidate();
        AHbsoluteUi.diag("d_design", (on ? "on" : "off") + (err == null ? ", ok" : ", " + err));
        return err;
    }
}
