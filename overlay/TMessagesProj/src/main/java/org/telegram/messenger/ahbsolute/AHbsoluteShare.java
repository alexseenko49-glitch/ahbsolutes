package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * Your AHbsolute profile for other AHbsolute users, through your PERSONAL CHANNEL (Telegram: profile → personal channel).
 *
 * Publishing: a post "✨ Мой профиль в AHbsolute #ahbsolute" whose word "AHbsolute" is a link to the project; the link
 * carries the data (built-in banner / wallpaper choice, glass, reposts of channel posts). Pictures from your gallery are
 * posted as photos with #ahbsolute_banner / #ahbsolute_wall. Old AHbsolute posts are deleted when a new one is made.
 * Regular Telegram users simply see these posts in your channel.
 *
 * Reading: when an AHbsolute user opens your profile, it finds the newest #ahbsolute post in your personal channel and shows
 * your banner, wallpaper, glass and reposts. Reposts of ordinary messages are never published.
 */
public class AHbsoluteShare {

    private static final String TAG = "#ahbsolute", TAG_BANNER = "#ahbsolute_banner", TAG_WALL = "#ahbsolute_wall";
    private static final String URL = "https://github.com/alexseenko49-glitch/ahbsolutes#ahb=";
    private static final Handler H = new Handler(Looper.getMainLooper());

    public static boolean enabled() { return "1".equals(AHbsoluteUi.getStr("share_profile", "0")); }
    public static void setEnabled(boolean v) { AHbsoluteUi.putStr("share_profile", v ? "1" : "0"); }

    private static int acc() { return UserConfig.selectedAccount; }
    private static long me() { try { return UserConfig.getInstance(acc()).getClientUserId(); } catch (Throwable e) { return 0; } }

    public interface Result { void done(String errorOrNull); }

    /* ------------------------------------------------------------ data helpers ------------------------------------------------------------ */

    static String pack(String json) throws Exception {
        byte[] in = json.getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(in);
        d.finish();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) bo.write(buf, 0, d.deflate(buf));
        d.end();
        return Base64.encodeToString(bo.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static String unpack(String s) throws Exception {
        byte[] in = Base64.decode(s, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        Inflater f = new Inflater();
        f.setInput(in);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int guard = 0;
        while (!f.finished() && guard++ < 4096) {
            int n = f.inflate(buf);
            if (n == 0 && (f.needsInput() || f.needsDictionary())) break;
            bo.write(buf, 0, n);
        }
        f.end();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String publicId(String id) {
        if (id == null || AHbsoluteBanner.OFF.equals(id)) return "";
        if (AHbsoluteBanner.isUser(id)) return "u";
        if (AHbsoluteBanner.THEME.equals(id)) {
            String sel = AHbsoluteThemes.selectedId(AHbsoluteUi.ctx());
            return sel == null || sel.isEmpty() ? AHbsoluteBanner.BUILTIN[0] : sel;
        }
        return id;
    }

    private static String cut(String s, int n) { return s == null ? "" : s.length() > n ? s.substring(0, n) + "…" : s; }

    private static JSONObject payload() throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", 1);
        String banner = AHbsoluteBanner.mode(), wall = AHbsoluteProfile.wallpaper();
        o.put("b", publicId(banner));
        o.put("w", banner != null && banner.equals(wall) && AHbsoluteBanner.isUser(wall) ? "b" : publicId(wall));
        o.put("g", AHbsoluteProfile.glassOn() ? 1 : 0);
        o.put("go", AHbsoluteProfile.glassOpacity());
        o.put("wb", AHbsoluteProfile.wallpaperBlur());
        o.put("wd", AHbsoluteProfile.wallpaperDim());
        JSONArray r = new JSONArray();
        int n = 0;
        for (AHbsoluteReposts.Item it : AHbsoluteReposts.list()) {
            if (it.priv) continue;                       // reposts of ordinary messages are never published
            JSONArray x = new JSONArray();
            x.put(it.dialogId); x.put(it.messageId); x.put(cut(it.title, 32)); x.put(cut(it.text, 60)); x.put(it.date);
            r.put(x);
            if (++n >= 15) break;
        }
        o.put("r", r);
        return o;
    }

    /* ------------------------------------------------------------ publish ------------------------------------------------------------ */

    private static final Runnable publishLater = () -> publish(null);

    /** called after anything that is shared changed (banner, wallpaper, glass, reposts) */
    public static void publishSoon() {
        if (!enabled()) return;
        H.removeCallbacks(publishLater);
        H.postDelayed(publishLater, 5000);
    }

    private static void withUserFull(long uid, int tries, Utilities1<TLRPC.UserFull> cb) {
        MessagesController mc = MessagesController.getInstance(acc());
        TLRPC.UserFull uf = mc.getUserFull(uid);
        if (uf != null || tries <= 0) { cb.run(uf); return; }
        if (tries == 4) {
            try {
                TLRPC.User u = mc.getUser(uid);
                if (u != null) mc.loadFullUser(u, 0, true);
            } catch (Throwable ignored) { }
        }
        H.postDelayed(() -> withUserFull(uid, tries - 1, cb), 1200);
    }

    interface Utilities1<T> { void run(T v); }

    private static void search(long channelId, String q, int limit, Utilities1<ArrayList<TLRPC.Message>> cb) {
        try {
            MessagesController mc = MessagesController.getInstance(acc());
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = mc.getInputPeer(-channelId);
            req.q = q;
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.limit = limit;
            ConnectionsManager.getInstance(acc()).sendRequest(req, (response, error) -> {
                ArrayList<TLRPC.Message> out = new ArrayList<>();
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    out.addAll(res.messages);
                }
                H.post(() -> cb.run(out));
            });
        } catch (Throwable e) {
            H.post(() -> cb.run(new ArrayList<>()));
        }
    }

    private static boolean hasTag(TLRPC.Message m, String tag) {
        if (m == null || m.message == null) return false;
        String s = m.message;
        int i = s.indexOf(tag);
        while (i >= 0) {
            int end = i + tag.length();
            if (end >= s.length() || !(Character.isLetterOrDigit(s.charAt(end)) || s.charAt(end) == '_')) return true;
            i = s.indexOf(tag, end);
        }
        return false;
    }

    /** posts the profile card (and changed pictures) to the personal channel */
    public static void publish(final Result result) {
        final Context c = AHbsoluteUi.ctx();
        final long uid = me();
        if (c == null || uid == 0) { if (result != null) result.done(t("Нет аккаунта", "No account")); return; }
        withUserFull(uid, 4, uf -> {
            try {
                long ch = uf == null ? 0 : uf.personal_channel_id;
                if (ch == 0) {
                    if (result != null) result.done(t("У вас нет личного канала. Добавьте его в Telegram: свой профиль → «Изменить» → «Личный канал».",
                            "You have no personal channel. Add one in Telegram: your profile → Edit → Personal channel."));
                    return;
                }
                long dialog = -ch;
                // pictures from the gallery: upload once per change
                String banner = AHbsoluteBanner.mode(), wall = AHbsoluteProfile.wallpaper();
                uploadIfChanged(c, dialog, banner, "up_banner", TAG_BANNER);
                if (!(banner != null && banner.equals(wall))) uploadIfChanged(c, dialog, wall, "up_wall", TAG_WALL);
                // old cards are removed, a fresh one is posted
                search(ch, TAG, 20, old -> {
                    ArrayList<Integer> ids = new ArrayList<>();
                    for (TLRPC.Message m : old) if (hasTag(m, TAG)) ids.add(m.id);
                    if (!ids.isEmpty()) {
                        try { MessagesController.getInstance(acc()).deleteMessages(ids, null, null, dialog, 0, true, 0); } catch (Throwable ignored) { }
                    }
                    try {
                        String data = pack(payload().toString());
                        String text = t("✨ Мой профиль в AHbsolute\n", "✨ My profile in AHbsolute\n") + TAG;
                        TLRPC.TL_messageEntityTextUrl e = new TLRPC.TL_messageEntityTextUrl();
                        e.offset = text.indexOf("AHbsolute");
                        e.length = "AHbsolute".length();
                        e.url = URL + data;
                        ArrayList<TLRPC.MessageEntity> ents = new ArrayList<>();
                        ents.add(e);
                        SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(text, dialog);
                        p.entities = ents;
                        p.searchLinks = false;
                        p.notify = false;
                        SendMessagesHelper.getInstance(acc()).sendMessage(p);
                        AHbsoluteUi.diag("d_share", "published, " + data.length() + " chars");
                        if (result != null) result.done(null);
                    } catch (Throwable ex) {
                        if (result != null) result.done(String.valueOf(ex));
                    }
                });
            } catch (Throwable ex) {
                if (result != null) result.done(String.valueOf(ex));
            }
        });
    }

    private static void uploadIfChanged(Context c, long dialog, String id, String key, String tag) {
        try {
            if (!AHbsoluteBanner.isUser(id)) return;
            File f = new File(new File(c.getFilesDir(), "ahb_banners"), id + ".jpg");
            if (!f.exists()) return;
            String stamp = id + "@" + f.lastModified() + "@" + dialog;
            if (stamp.equals(AHbsoluteUi.getStr(key, ""))) return;
            SendMessagesHelper.prepareSendingPhoto(AccountInstance.getInstance(acc()), f.getAbsolutePath(), null, dialog, null, null, null,
                    tag, null, null, null, 0, null, false, 0, 0, SendMessageChatArguments.EMPTY);
            AHbsoluteUi.putStr(key, stamp);
        } catch (Throwable e) {
            AHbsoluteUi.diag("d_share", "upload error: " + e);
        }
    }

    /* ------------------------------------------------------------ read other people's profiles ------------------------------------------------------------ */

    public static class Remote {
        long loadedAt;
        boolean has;
        String banner = "", wall = "";
        boolean glass;
        int glassOp = 62, blur = 1, dim = 0;
        Bitmap bannerBmp, wallBmp;
        final ArrayList<AHbsoluteReposts.Item> reposts = new ArrayList<>();
    }

    private static final HashMap<Long, Remote> cache = new HashMap<>();
    private static final HashMap<Long, Boolean> loading = new HashMap<>();

    static Remote get(long userId) {
        if (userId == 0 || userId == me()) return null;
        Remote r = cache.get(userId);
        return r != null && r.has ? r : null;
    }

    /** starts loading the AHbsolute card of a user (from ProfileActivity); onLoaded runs on the main thread when something was found */
    public static void request(final long userId, final Runnable onLoaded) {
        try {
            if (userId == 0 || userId == me()) return;
            Remote old = cache.get(userId);
            if (old != null && System.currentTimeMillis() - old.loadedAt < 10 * 60 * 1000L) {
                if (old.has && onLoaded != null) onLoaded.run();
                return;
            }
            if (loading.containsKey(userId)) return;
            loading.put(userId, Boolean.TRUE);
            withUserFull(userId, 5, uf -> {
                long ch = uf == null ? 0 : uf.personal_channel_id;
                if (ch == 0) { finish(userId, new Remote(), null); return; }
                search(ch, TAG, 10, msgs -> {
                    Remote r = new Remote();
                    for (TLRPC.Message m : msgs) {
                        if (!hasTag(m, TAG) || m.entities == null) continue;
                        for (TLRPC.MessageEntity e : m.entities) {
                            if (e.url != null && e.url.startsWith(URL)) {
                                try { parse(r, unpack(e.url.substring(URL.length()))); r.has = true; } catch (Throwable ignored) { }
                                break;
                            }
                        }
                        if (r.has) break;
                    }
                    if (!r.has) { finish(userId, r, null); return; }
                    final Context c = AHbsoluteUi.ctx();
                    loadPictures(c, ch, r, () -> finish(userId, r, onLoaded));
                    // built-in pictures are available at once
                    finish(userId, r, onLoaded);
                });
            });
        } catch (Throwable e) {
            loading.remove(userId);
        }
    }

    private static void finish(long userId, Remote r, Runnable onLoaded) {
        r.loadedAt = System.currentTimeMillis();
        cache.put(userId, r);
        loading.remove(userId);
        if (r.has && onLoaded != null) { try { onLoaded.run(); } catch (Throwable ignored) { } }
    }

    private static void parse(Remote r, String json) throws Exception {
        JSONObject o = new JSONObject(json);
        r.banner = o.optString("b", "");
        r.wall = o.optString("w", "");
        r.glass = o.optInt("g", 0) == 1;
        r.glassOp = o.optInt("go", 62);
        r.blur = o.optInt("wb", 1);
        r.dim = o.optInt("wd", 0);
        JSONArray a = o.optJSONArray("r");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONArray x = a.getJSONArray(i);
            AHbsoluteReposts.Item it = new AHbsoluteReposts.Item();
            it.dialogId = x.optLong(0); it.messageId = x.optInt(1); it.title = x.optString(2); it.text = x.optString(3); it.date = x.optInt(4);
            r.reposts.add(it);
        }
    }

    private static void loadPictures(final Context c, long ch, final Remote r, final Runnable done) {
        if (c == null) return;
        if (isBuiltin(r.banner)) r.bannerBmp = AHbsoluteBanner.load(c, r.banner, 2);
        if (isBuiltin(r.wall)) r.wallBmp = AHbsoluteProfile.blurred(AHbsoluteBanner.load(c, r.wall, 2), r.blur);
        boolean needBanner = "u".equals(r.banner), needWall = "u".equals(r.wall) || ("b".equals(r.wall) && needBanner);
        if (needBanner) photo(c, ch, TAG_BANNER, bmp -> {
            r.bannerBmp = bmp;
            if ("b".equals(r.wall)) r.wallBmp = AHbsoluteProfile.blurred(bmp, r.blur);
            done.run();
        });
        if ("u".equals(r.wall)) photo(c, ch, TAG_WALL, bmp -> { r.wallBmp = AHbsoluteProfile.blurred(bmp, r.blur); done.run(); });
        if (!needBanner && !needWall) done.run();
    }

    private static boolean isBuiltin(String id) {
        for (String b : AHbsoluteBanner.BUILTIN) if (b.equals(id)) return true;
        return false;
    }

    private static void photo(final Context c, long ch, String tag, final Utilities1<Bitmap> cb) {
        search(ch, tag, 5, msgs -> {
            for (TLRPC.Message m : msgs) {
                if (!hasTag(m, tag) || !(m.media instanceof TLRPC.TL_messageMediaPhoto) || m.media.photo == null) continue;
                try {
                    TLRPC.Photo photo = m.media.photo;
                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 1600);
                    if (size == null) return;
                    MessageObject parent = new MessageObject(acc(), m, false, false);
                    FileLoader.getInstance(acc()).loadFile(ImageLocation.getForPhoto(size, photo), parent, "jpg", FileLoader.PRIORITY_NORMAL, 1);
                    final File f = FileLoader.getInstance(acc()).getPathToAttach(size, true);
                    waitFile(f, 0, cb);
                } catch (Throwable ignored) { }
                return;
            }
        });
    }

    private static void waitFile(final File f, final int tries, final Utilities1<Bitmap> cb) {
        if (f != null && f.exists() && f.length() > 0) {
            new Thread(() -> {
                Bitmap b = null;
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                    int s = 1;
                    while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1200) s *= 2;
                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = s;
                    b = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
                } catch (Throwable ignored) { }
                final Bitmap fb = b;
                H.post(() -> { if (fb != null) cb.run(fb); });
            }, "ahb-share-decode").start();
            return;
        }
        if (tries > 60) return; // ~30 s
        H.postDelayed(() -> waitFile(f, tries + 1, cb), 500);
    }

    /* ------------------------------------------------------------ getters used by banner / profile / reposts ------------------------------------------------------------ */

    public static Bitmap bannerBitmap(long userId) { Remote r = get(userId); return r == null ? null : r.bannerBmp; }
    public static Bitmap wallBitmap(long userId) { Remote r = get(userId); return r == null ? null : r.wallBmp; }
    public static boolean glass(long userId) { Remote r = get(userId); return r != null && r.glass && r.wallBmp != null; }
    public static int glassOpacity(long userId) { Remote r = get(userId); return r == null ? 62 : r.glassOp; }
    public static ArrayList<AHbsoluteReposts.Item> reposts(long userId) {
        Remote r = get(userId);
        return r == null ? new ArrayList<>() : new ArrayList<>(r.reposts);
    }
}
