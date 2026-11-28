package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.ArrayList;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * Quick actions on the app icon (long press the AHbsolute icon): «Настройки AHbsolute», «Сменить дизайн»,
 * «Скопировать настройки». Android 7.1+.
 */
public class AHbsoluteShortcuts {

    public static final String EXTRA = "ahb_action";

    public static void update(Context c) {
        if (Build.VERSION.SDK_INT < 25 || c == null) return;
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            if (!AHbsoluteUi.getBool("shortcuts")) { sm.removeAllDynamicShortcuts(); return; }
            ArrayList<ShortcutInfo> list = new ArrayList<>();
            list.add(make(c, "ahb_settings", t("Настройки AHbsolute", "AHbsolute settings"), "settings", android.R.drawable.ic_menu_preferences));
            list.add(make(c, "ahb_design", t("Сменить дизайн", "Switch design"), "design", android.R.drawable.ic_menu_gallery));
            list.add(make(c, "ahb_copy", t("Скопировать настройки", "Copy settings"), "copy", android.R.drawable.ic_menu_save));
            sm.setDynamicShortcuts(list);
        } catch (Throwable e) {
            AHbsoluteUi.diag("d_shortcuts", "error: " + e);
        }
    }

    private static ShortcutInfo make(Context c, String id, String label, String action, int icon) {
        Intent i = new Intent(c, AHbsoluteStandaloneActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(EXTRA, action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return new ShortcutInfo.Builder(c, id).setShortLabel(label).setLongLabel(label)
                .setIcon(Icon.createWithResource(c, icon)).setIntent(i).build();
    }
}
