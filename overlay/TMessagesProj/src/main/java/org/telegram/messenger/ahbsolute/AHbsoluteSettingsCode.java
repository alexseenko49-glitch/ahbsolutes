package org.telegram.messenger.ahbsolute;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

/**
 * "Скопировать настройки" / "Ввод настроек": all AHbsolute settings packed into one long NUMBER (compressed, so it can be
 * pasted anywhere and typed back). Entering the number restores the settings exactly as they were.
 * Pictures from your gallery are not inside the number (they stay on the phone); a setting that points to a missing
 * picture falls back to "none".
 */
public class AHbsoluteSettingsCode {

    private static final String[] FILES = {"ahb_ui", "ahbsolute", "ahb_theme_ui", "ahb_blur", "ahb_design"};
    private static final String SKIP_PREFIX = "up_"; // upload stamps of the personal channel are device-specific

    public static String export(Context c) throws Exception {
        JSONObject root = new JSONObject();
        root.put("v", 2);
        for (String f : FILES) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences(f, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object v = e.getValue();
                if (e.getKey().startsWith(SKIP_PREFIX)) continue;
                JSONArray x = new JSONArray();
                if (v instanceof Boolean) { x.put("b"); x.put(v); }
                else if (v instanceof Integer) { x.put("i"); x.put(v); }
                else if (v instanceof Long) { x.put("l"); x.put(v); }
                else if (v instanceof Float) { x.put("f"); x.put(((Float) v).doubleValue()); }
                else if (v instanceof String) { x.put("s"); x.put(v); }
                else continue;
                o.put(e.getKey(), x);
            }
            root.put(f, o);
        }
        // base64 → number: a leading 1 keeps leading zero bytes
        String b64 = AHbsoluteShare.pack(root.toString());
        byte[] raw = b64.getBytes("US-ASCII");
        byte[] withMark = new byte[raw.length + 1];
        withMark[0] = 1;
        System.arraycopy(raw, 0, withMark, 1, raw.length);
        return new BigInteger(1, withMark).toString();
    }

    public static void copy(Context c) throws Exception {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("AHbsolute", export(c)));
    }

    /** returns null on success, otherwise an error text */
    public static String importCode(Context c, String number) {
        try {
            String digits = number == null ? "" : number.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return AHbsolute.t("Пустой код", "Empty code");
            byte[] withMark = new BigInteger(digits).toByteArray();
            int start = 0;
            while (start < withMark.length && withMark[start] == 0) start++;
            if (start >= withMark.length || withMark[start] != 1) return AHbsolute.t("Это не код настроек AHbsolute", "Not an AHbsolute settings code");
            String b64 = new String(withMark, start + 1, withMark.length - start - 1, "US-ASCII");
            JSONObject root = new JSONObject(AHbsoluteShare.unpack(b64));
            for (String f : FILES) {
                JSONObject o = root.optJSONObject(f);
                if (o == null) continue;
                SharedPreferences.Editor ed = c.getSharedPreferences(f, Context.MODE_PRIVATE).edit();
                ed.clear();
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray x = o.getJSONArray(k);
                    String type = x.getString(0);
                    switch (type) {
                        case "b": ed.putBoolean(k, x.getBoolean(1)); break;
                        case "i": ed.putInt(k, x.getInt(1)); break;
                        case "l": ed.putLong(k, x.getLong(1)); break;
                        case "f": ed.putFloat(k, (float) x.getDouble(1)); break;
                        default: ed.putString(k, x.getString(1));
                    }
                }
                ed.commit();
            }
            // pictures that are not on this phone fall back to "none"
            java.util.ArrayList<String> mine = AHbsoluteBanner.userBanners(c);
            if (AHbsoluteBanner.isUser(AHbsoluteBanner.mode()) && !mine.contains(AHbsoluteBanner.mode())) AHbsoluteUi.putStr("banner", AHbsoluteBanner.OFF);
            String wp = AHbsoluteProfile.wallpaper();
            if (AHbsoluteBanner.isUser(wp) && !mine.contains(wp)) AHbsoluteUi.putStr("prof_wp", AHbsoluteBanner.OFF);
            AHbsoluteBanner.invalidate();
            AHbsoluteProfile.invalidate();
            String sel = AHbsoluteThemes.selectedId(c);
            int idx = AHbsoluteThemes.indexOf(sel);
            String err = idx >= 0 ? AHbsoluteThemes.apply(c, idx) : null;
            try { AHbsoluteUi.applyGlass(c); } catch (Throwable ignored) { }
            AHbsoluteShare.publishSoon();
            return err;
        } catch (Throwable e) {
            return AHbsolute.t("Код повреждён: ", "Broken code: ") + e.getMessage();
        }
    }
}
