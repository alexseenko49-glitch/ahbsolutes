package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Free chat order: hold any chat (selection mode) and drag it up or down. Telegram only lets pinned chats move, so
 * AHbsolute remembers, for every moved chat, which chat it should stand under ("anchor"; 0 = at the very top, right after
 * the pinned chats) and puts it there every time the chat list is shown. Stored on this device only.
 */
public class AHbsoluteOrder {

    private static long[][] placements;   // {dialogId, anchorId}
    private static long version = -1;

    public static boolean on() {
        try { return AHbsoluteUi.ctx() != null && AHbsoluteUi.master(AHbsoluteUi.ctx()) && AHbsoluteUi.getBool("chatmove"); } catch (Throwable e) { return false; }
    }

    private static SharedPreferences sp() {
        return ApplicationLoader.applicationContext.getSharedPreferences("ahb_order", Context.MODE_PRIVATE);
    }

    private static long[][] load() {
        long v = sp().getLong("ver", 0);
        if (placements != null && v == version) return placements;
        ArrayList<long[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp().getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray x = a.getJSONArray(i);
                out.add(new long[]{x.getLong(0), x.getLong(1)});
            }
        } catch (Throwable ignored) { }
        placements = out.toArray(new long[0][]);
        version = v;
        return placements;
    }

    private static void save(long[][] list) {
        JSONArray a = new JSONArray();
        for (long[] p : list) { JSONArray x = new JSONArray(); x.put(p[0]); x.put(p[1]); a.put(x); }
        placements = list;
        version = System.currentTimeMillis();
        sp().edit().putString("list", a.toString()).putLong("ver", version).apply();
    }

    public static int count() { return load().length; }

    public static void reset() { save(new long[0][]); }

    /** DialogsActivity.getDialogsArray(): puts the moved chats where the user left them (changes the list in place) */
    public static ArrayList<TLRPC.Dialog> apply(ArrayList<TLRPC.Dialog> list, int folderId) {
        try {
            if (list == null || list.size() < 2 || !on()) return list;
            long[][] ps = load();
            if (ps.length == 0) return list;
            for (long[] p : ps) {
                int from = indexOf(list, p[0]);
                if (from < 0) continue;
                TLRPC.Dialog d = list.get(from);
                if (d.pinned) continue;
                int to;
                if (p[1] == 0) {
                    to = firstUnpinned(list, from);
                } else {
                    int a = indexOf(list, p[1]);
                    if (a < 0 || a == from) continue;
                    to = a < from ? a + 1 : a;   // index after removal
                }
                if (to == from) continue;
                list.remove(from);
                list.add(Math.max(0, Math.min(list.size(), to)), d);
            }
        } catch (Throwable ignored) { }
        return list;
    }

    private static int indexOf(ArrayList<TLRPC.Dialog> list, long id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) != null && list.get(i).id == id) return i;
        return -1;
    }

    private static int firstUnpinned(ArrayList<TLRPC.Dialog> list, int skip) {
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            if (i == skip) continue;
            TLRPC.Dialog d = list.get(i);
            if (d != null && (d.pinned || org.telegram.messenger.DialogObject.isFolderDialogId(d.id))) n++; else break;
        }
        return n;
    }

    /** DialogsActivity SwipeController.onMove (unpinned chats): remember the new place of the dragged chat */
    public static void onMoved(ArrayList<TLRPC.Dialog> list, long dialogId) {
        try {
            int i = indexOf(list, dialogId);
            if (i < 0) return;
            long anchor = 0;
            if (i > 0) {
                TLRPC.Dialog above = list.get(i - 1);
                if (above != null && !above.pinned && !org.telegram.messenger.DialogObject.isFolderDialogId(above.id)) anchor = above.id;
            }
            ArrayList<long[]> out = new ArrayList<>();
            for (long[] p : load()) if (p[0] != dialogId) out.add(p);
            out.add(new long[]{dialogId, anchor});
            save(out.toArray(new long[0][]));
        } catch (Throwable ignored) { }
    }
}
