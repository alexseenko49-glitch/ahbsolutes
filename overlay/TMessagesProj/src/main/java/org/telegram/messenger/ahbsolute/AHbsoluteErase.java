package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.widget.Toast;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * "Экстремальное удаление" for your own messages: one tap, no questions — the message is deleted on Telegram's server for
 * everybody (all clients, all devices) and from this device's cache. Nothing is left in the chat, not even a trace.
 * (What nobody can undo: screenshots, notifications already shown, or apps that copy messages on their own.)
 */
public class AHbsoluteErase {

    public static final int OPTION = 9120;

    public static boolean enabled() { return AHbsoluteUi.getBool("erase"); }

    public static String label() { return t("Экстремальное удаление", "Extreme delete"); }

    public static boolean canErase(MessageObject m) {
        try {
            return enabled() && m != null && m.isOut() && m.getId() > 0 && !m.isSponsored()
                    && m.messageOwner != null && m.messageOwner.action == null;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void erase(Context ctx, int account, MessageObject m, MessageObject.GroupedMessages group, long dialogId, TLRPC.EncryptedChat encrypted) {
        try {
            ArrayList<Integer> ids = new ArrayList<>();
            ArrayList<Long> randoms = encrypted != null ? new ArrayList<>() : null;
            ArrayList<MessageObject> all = new ArrayList<>();
            if (group != null && group.messages != null && !group.messages.isEmpty()) all.addAll(group.messages); else all.add(m);
            for (MessageObject x : all) {
                if (x == null || x.getId() == 0) continue;
                ids.add(x.getId());
                if (randoms != null && x.messageOwner != null) randoms.add(x.messageOwner.random_id);
            }
            if (ids.isEmpty()) return;
            MessagesController.getInstance(account).deleteMessages(ids, randoms, encrypted, dialogId, 0, true, 0);
            if (ctx != null) Toast.makeText(ctx, t("Сообщение пропало без следа", "The message vanished without a trace"), Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            if (ctx != null) Toast.makeText(ctx, t("Не получилось: ", "Failed: ") + e, Toast.LENGTH_LONG).show();
        }
    }
}
