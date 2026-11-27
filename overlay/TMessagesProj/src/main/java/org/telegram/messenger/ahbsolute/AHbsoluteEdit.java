package org.telegram.messenger.ahbsolute;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * "Edit (only for me)": changes the text of a friend's message on this phone only. The message gets the usual "edited" mark
 * as if the friend had edited it. Nothing is sent to Telegram. The change lasts while the chat stays open
 * (reopening the chat reloads the real message).
 */
public class AHbsoluteEdit {

    public static final int OPTION = 9100;

    public static String label() { return t("Изменить (только у меня)", "Edit (only for me)"); }

    /** used by the patched ChatActivity menu: show the item for friends' text messages */
    public static boolean canEdit(MessageObject m) {
        try {
            if (m == null || m.messageOwner == null || !AHbsoluteUi.localEditOn()) return false;
            if (m.isOut()) return false;
            if (m.type != 0) return false;
            String s = m.messageOwner.message;
            return s != null && s.length() > 0;
        } catch (Throwable e) { return false; }
    }

    private static void set(Object o, String name, Object v) {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); f.set(o, v); return; } catch (Throwable ignored) { }
        }
    }

    public static void start(final Activity act, final MessageObject msg) {
        try {
            if (act == null || msg == null || msg.messageOwner == null) return;
            final EditText et = new EditText(act);
            et.setText(msg.messageOwner.message);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setSelection(et.getText().length());
            FrameLayout box = new FrameLayout(act);
            int p = Math.round(20 * act.getResources().getDisplayMetrics().density);
            box.setPadding(p, p / 2, p, 0);
            box.addView(et);
            new AlertDialog.Builder(act)
                    .setTitle(label())
                    .setMessage(t("Изменение увидите только вы.", "Only you will see the change."))
                    .setView(box)
                    .setPositiveButton(t("Сохранить", "Save"), new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { apply(act, msg, et.getText().toString()); }
                    })
                    .setNegativeButton(t("Отмена", "Cancel"), null)
                    .show();
        } catch (Throwable e) {
            try { Toast.makeText(act, "AHbsolute: " + e, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
        }
    }

    private static void apply(Activity act, MessageObject msg, String text) {
        try {
            msg.messageOwner.message = text;
            set(msg.messageOwner, "entities", new ArrayList<Object>());
            set(msg.messageOwner, "edit_date", (int) (System.currentTimeMillis() / 1000L));
            try {
                Field fl = msg.messageOwner.getClass().getField("flags");
                fl.setInt(msg.messageOwner, fl.getInt(msg.messageOwner) | 32768);
            } catch (Throwable ignored) { }
            set(msg.messageOwner, "edit_hide", Boolean.FALSE);
            msg.applyNewText();
            msg.forceUpdate = true;
            ArrayList<MessageObject> list = new ArrayList<>();
            list.add(msg);
            int acc = UserConfig.selectedAccount;
            try { Field fa = msg.getClass().getField("currentAccount"); acc = fa.getInt(msg); } catch (Throwable ignored) { }
            NotificationCenter.getInstance(acc).postNotificationName(NotificationCenter.replaceMessagesObjects, msg.getDialogId(), list);
        } catch (Throwable e) {
            try { Toast.makeText(act, "AHbsolute: " + e, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
        }
    }
}
