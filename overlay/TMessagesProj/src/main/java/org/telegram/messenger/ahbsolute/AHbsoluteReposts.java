package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * Reposts: hold a post in a channel → "Репост" → it appears in the "Репосты" section of your profile,
 * from where you can jump back to the original post. Stored on this device (Telegram has no such field).
 */
public class AHbsoluteReposts {

    /** option id in the ChatActivity message menu (must not clash with Telegram's OPTION_* values) */
    public static final int OPTION = 9110;
    private static final int MAX = 500;

    public static class Item {
        public long dialogId;
        public int messageId;
        public String title;
        public String text;
        public int date;
        public long added;
        /** a repost of an ordinary message: never shown to anybody else */
        public boolean priv;
    }

    private static SharedPreferences sp() {
        return ApplicationLoader.applicationContext.getSharedPreferences("ahb_reposts", Context.MODE_PRIVATE);
    }

    public static boolean enabled() { return AHbsoluteUi.getBool("repost"); }

    public static String label() { return t("Репост", "Repost"); }

    /** called while the message menu is being built: channel posts and ordinary messages */
    public static boolean canRepost(MessageObject m, TLRPC.Chat chat) {
        try {
            if (!enabled() || m == null || m.getId() <= 0 || m.isSponsored()) return false;
            return m.messageOwner != null && m.messageOwner.action == null;
        } catch (Throwable e) {
            return false;
        }
    }

    static boolean isChannelPost(TLRPC.Chat chat) {
        try { return chat != null && ChatObject.isChannelAndNotMegaGroup(chat); } catch (Throwable e) { return false; }
    }

    public static ArrayList<Item> list() {
        ArrayList<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp().getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.dialogId = o.optLong("d");
                it.messageId = o.optInt("m");
                it.title = o.optString("t", "");
                it.text = o.optString("x", "");
                it.date = o.optInt("dt");
                it.added = o.optLong("a");
                it.priv = o.optBoolean("p", false);
                out.add(it);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static int count() { return list().size(); }

    private static void save(ArrayList<Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("d", it.dialogId); o.put("m", it.messageId); o.put("t", it.title);
                o.put("x", it.text); o.put("dt", it.date); o.put("a", it.added); o.put("p", it.priv);
                a.put(o);
            }
            sp().edit().putString("list", a.toString()).apply();
        } catch (Throwable ignored) { }
    }

    public static boolean contains(long dialogId, int messageId) {
        for (Item it : list()) if (it.dialogId == dialogId && it.messageId == messageId) return true;
        return false;
    }

    /** ChatActivity: the "Репост" item was tapped */
    public static void add(Context ctx, MessageObject m, TLRPC.Chat chat) {
        try {
            if (m == null) return;
            long did = m.getDialogId();
            int mid = m.getId();
            ArrayList<Item> items = list();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).dialogId == did && items.get(i).messageId == mid) {
                    toast(ctx, t("Этот пост уже в ваших репостах", "This post is already in your reposts"));
                    return;
                }
            }
            Item it = new Item();
            it.dialogId = did;
            it.messageId = mid;
            it.priv = !isChannelPost(chat);
            if (chat != null && chat.title != null) it.title = chat.title;
            else {
                try {
                    org.telegram.tgnet.TLRPC.User u = org.telegram.messenger.MessagesController.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getUser(did);
                    it.title = u == null ? "" : org.telegram.messenger.UserObject.getUserName(u);
                } catch (Throwable e) { it.title = ""; }
            }
            CharSequence txt = m.messageText;
            if ((txt == null || txt.length() == 0) && m.caption != null) txt = m.caption;
            String s = txt == null ? "" : txt.toString().replace('\n', ' ').trim();
            if (s.isEmpty()) s = t("Медиа", "Media");
            it.text = s.length() > 300 ? s.substring(0, 300) + "…" : s;
            it.date = m.messageOwner != null ? m.messageOwner.date : 0;
            it.added = System.currentTimeMillis();
            items.add(0, it);
            while (items.size() > MAX) items.remove(items.size() - 1);
            save(items);
            if (it.priv) {
                toast(ctx, t("Репост сохранён. Репосты сообщений не видит никто, кроме вас", "Saved. Reposts of messages are seen by nobody but you"));
            } else {
                toast(ctx, t("Репост добавлен в ваш профиль", "Reposted to your profile"));
                AHbsoluteShare.publishSoon();
            }
        } catch (Throwable e) {
            toast(ctx, t("Не удалось сделать репост: ", "Could not repost: ") + e);
        }
    }

    public static void remove(long dialogId, int messageId) {
        ArrayList<Item> items = list();
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).dialogId == dialogId && items.get(i).messageId == messageId) items.remove(i);
        }
        save(items);
        AHbsoluteShare.publishSoon();
    }

    private static void toast(Context c, String s) {
        try { Toast.makeText(c != null ? c : ApplicationLoader.applicationContext, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
    }

    /** ProfileActivity row text */
    public static String rowTitle() { return t("Репосты", "Reposts"); }
    public static String rowValue() { int n = count(); return n == 0 ? "" : String.valueOf(n); }

    /** ProfileActivity: the row is shown on your own profile and on profiles of AHbsolute users that shared reposts */
    public static boolean showRow(long userId) {
        try {
            long me = org.telegram.messenger.UserConfig.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getClientUserId();
            if (userId == me) return enabled();
            return !AHbsoluteShare.reposts(userId).isEmpty();
        } catch (Throwable e) { return false; }
    }

    public static String rowValue(long userId) {
        try {
            long me = org.telegram.messenger.UserConfig.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getClientUserId();
            int n = userId == me ? count() : AHbsoluteShare.reposts(userId).size();
            return n == 0 ? "" : String.valueOf(n);
        } catch (Throwable e) { return ""; }
    }
}
