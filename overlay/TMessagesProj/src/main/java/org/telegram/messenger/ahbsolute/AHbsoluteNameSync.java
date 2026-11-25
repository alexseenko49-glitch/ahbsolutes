package org.telegram.messenger.ahbsolute;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * Writes the invisible shape marker into the user's own last name.
 * Kept in its own file: if it does not fit a new Telegram version, CI swaps in the no-op stub
 * (overlay_stub/) and the rest of AHbsolute still builds.
 */
public class AHbsoluteNameSync {

    private static void setField(Object o, String name, Object v) {
        try {
            Field f = o.getClass().getField(name);
            f.set(o, v);
        } catch (Throwable t) {
            try {
                Field f = o.getClass().getDeclaredField(name);
                f.setAccessible(true);
                f.set(o, v);
            } catch (Throwable ignored) { }
        }
    }

    /** The request class was renamed between Telegram versions, so it is looked up by name at runtime. */
    private static TLObject buildUpdateProfile(String first, String last) {
        String[] names = {
                "org.telegram.tgnet.TLRPC$TL_account_updateProfile",
                "org.telegram.tgnet.tl.TL_account$updateProfile",
                "org.telegram.tgnet.tl.TL_account_updateProfile",
        };
        for (String n : names) {
            try {
                Class<?> c = Class.forName(n);
                Object o = c.getDeclaredConstructor().newInstance();
                setField(o, "flags", 3);
                setField(o, "first_name", first);
                setField(o, "last_name", last);
                return (TLObject) o;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    public static void push() {
        final int acc = UserConfig.selectedAccount;
        final TLRPC.User cur = UserConfig.getInstance(acc).getCurrentUser();
        if (cur == null) return;
        String base = AHbsolute.stripMarker(cur.last_name);
        String wanted = base + (AHbsolute.isShareEnabled() ? AHbsolute.markerForShape(AHbsolute.getMyShape()) : "");
        String have = cur.last_name == null ? "" : cur.last_name;
        if (wanted.equals(have)) return;

        TLObject req = buildUpdateProfile(cur.first_name == null ? "" : cur.first_name, wanted);
        if (req == null) return;
        ConnectionsManager.getInstance(acc).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.User) {
                final TLRPC.User nu = (TLRPC.User) response;
                AndroidUtilities.runOnUIThread(() -> {
                    ArrayList<TLRPC.User> l = new ArrayList<>();
                    l.add(nu);
                    MessagesController.getInstance(acc).putUser(nu, false);
                    UserConfig.getInstance(acc).setCurrentUser(nu);
                    UserConfig.getInstance(acc).saveConfig(true);
                    MessagesStorage.getInstance(acc).putUsersAndChats(l, null, false, true);
                    NotificationCenter.getInstance(acc).postNotificationName(
                            NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                });
            }
        });
    }
}
