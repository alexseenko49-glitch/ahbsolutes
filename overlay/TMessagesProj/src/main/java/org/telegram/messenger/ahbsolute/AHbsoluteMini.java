package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * Mini profile: 📎 → «Мини-профиль» sends a message "[Профиль]". The word is a link to the AHbsolute project that also
 * carries your profile customization. Without AHbsolute people simply see "[Профиль]" (a link to the project); in AHbsolute
 * a tap opens a scrollable mini profile with the banner, wallpaper, glass, avatar layout and shape, theme / design,
 * shared reposts — and a button to apply the same look to yourself.
 */
public class AHbsoluteMini {

    public static final int ATTACH_TAG = 9130;
    static final String URL = "https://github.com/alexseenko49-glitch/ahbsolutes#ahbp=";

    public static boolean enabled() { return AHbsoluteUi.getBool("miniprofile"); }

    public static String label() { return t("Мини-профиль", "Mini profile"); }

    static JSONObject payload() throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", 1);
        long me = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        o.put("u", me);
        try {
            TLRPC.User u = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            o.put("n", u == null ? "" : AHbsolute.stripMarker(UserObject.getUserName(u)));
        } catch (Throwable ignored) { }
        o.put("b", AHbsoluteBanner.mode());
        o.put("w", AHbsoluteProfile.wallpaper());
        o.put("g", AHbsoluteProfile.glassOn() ? 1 : 0);
        o.put("go", AHbsoluteProfile.glassOpacity());
        o.put("wb", AHbsoluteProfile.wallpaperBlur());
        o.put("ap", AHbsoluteProfile.avatarPos());
        o.put("as", AHbsoluteProfile.avatarSize());
        o.put("na", AHbsoluteProfile.nameAlign());
        o.put("ns", AHbsoluteProfile.nameSize());
        o.put("sh", AHbsolute.getMyShape());
        o.put("th", AHbsoluteThemes.selectedId(AHbsoluteUi.ctx()));
        o.put("d", AHbsoluteDesign.variant(AHbsoluteUi.ctx()));
        o.put("sp", AHbsoluteShare.enabled() ? 1 : 0);
        return o;
    }

    /** 📎 → Мини-профиль (ChatAttachAlert) */
    public static void sendFromAttach(Object fragment) {
        try {
            if (!(fragment instanceof org.telegram.ui.ChatActivity)) return;
            org.telegram.ui.ChatActivity chat = (org.telegram.ui.ChatActivity) fragment;
            long dialog = chat.getDialogId();
            String text = t("[Профиль]", "[Profile]");
            TLRPC.TL_messageEntityTextUrl e = new TLRPC.TL_messageEntityTextUrl();
            e.offset = 0;
            e.length = text.length();
            e.url = URL + AHbsoluteShare.pack(payload().toString());
            ArrayList<TLRPC.MessageEntity> ents = new ArrayList<>();
            ents.add(e);
            SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(text, dialog);
            p.entities = ents;
            p.searchLinks = false;
            p.notify = true;
            SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(p);
        } catch (Throwable ex) {
            try { Toast.makeText(AHbsoluteUi.ctx(), String.valueOf(ex), Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
        }
    }

    /** Browser / open-link alert: our mini profile link opens the AHbsolute viewer instead of the browser */
    public static boolean intercept(Context ctx, String url) {
        try {
            if (url == null || !url.startsWith(URL)) return false;
            final JSONObject o = new JSONObject(AHbsoluteShare.unpack(url.substring(URL.length())));
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    org.telegram.ui.ActionBar.BaseFragment last = org.telegram.ui.LaunchActivity.getLastFragment();
                    if (last != null) last.presentFragment(new AHbsoluteMiniActivity(o));
                } catch (Throwable ignored) { }
            });
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** "Применить себе": copies the look of the mini profile (built-in pictures only; somebody else's gallery pictures stay theirs) */
    public static void applyToMe(Context c, JSONObject o) {
        try {
            String b = o.optString("b", AHbsoluteBanner.OFF);
            if (!AHbsoluteBanner.isUser(b)) AHbsoluteBanner.setMode(b);
            String w = o.optString("w", AHbsoluteBanner.OFF);
            if (!AHbsoluteBanner.isUser(w)) AHbsoluteProfile.setWallpaper(w);
            AHbsoluteProfile.setGlass(o.optInt("g", 0) == 1);
            AHbsoluteUi.putInt("prof_glass_op", o.optInt("go", 62));
            AHbsoluteUi.putInt("prof_wp_blur", o.optInt("wb", 1));
            AHbsoluteUi.putInt("prof_av_pos", o.optInt("ap", 0));
            AHbsoluteUi.putInt("prof_av_size", o.optInt("as", 100));
            AHbsoluteUi.putInt("prof_name_align", o.optInt("na", 3));
            AHbsoluteUi.putInt("prof_name_size", o.optInt("ns", 100));
            AHbsolute.setMyShape(o.optInt("sh", 0));
            int d = o.optInt("d", 0);
            String err = null;
            if (d != AHbsoluteDesign.variant(c)) err = AHbsoluteDesign.setVariant(c, d);
            if (d == 0) {
                int idx = AHbsoluteThemes.indexOf(o.optString("th", ""));
                if (idx >= 0) err = AHbsoluteThemes.apply(c, idx);
            }
            AHbsoluteBanner.invalidate();
            AHbsoluteProfile.invalidate();
            Toast.makeText(c, err == null ? t("Готово: теперь у вас такой же профиль", "Done: your profile now looks the same") : err, Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            Toast.makeText(c, String.valueOf(e), Toast.LENGTH_LONG).show();
        }
    }
}
