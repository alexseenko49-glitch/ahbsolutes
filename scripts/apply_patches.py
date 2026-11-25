#!/usr/bin/env python3
"""Applies AHbsolute patches to a checkout of Telegram-Android.
Usage: apply_patches.py <upstream_dir>
Required patches fail loudly (exit 1) so CI never silently builds something half-patched."""
import os, re, sys, json

SAFE = "--safe" in sys.argv
FEATS = {"menu", "avatar", "settings"}
for _a in sys.argv[1:]:
    if _a.startswith("--features="):
        FEATS = {x.strip() for x in _a.split("=", 1)[1].split(",") if x.strip()}
print("requested features:", sorted(FEATS))
UP = [a for a in sys.argv[1:] if not a.startswith("--")][0]
APP_ID = os.environ.get("TG_API_ID", "").strip()
APP_HASH = os.environ.get("TG_API_HASH", "").strip()
PKG = os.environ.get("AH_PACKAGE", "com.ahbsolute.app").strip()
failures = []

def walk(pattern_name=None, suffix=None):
    for d, _, files in os.walk(UP):
        if "/.git" in d or "/build/" in d + "/":
            continue
        for f in files:
            if (pattern_name and f == pattern_name) or (suffix and f.endswith(suffix)):
                yield os.path.join(d, f)

def read(p):
    with open(p, encoding="utf-8") as fh: return fh.read()

def write(p, s):
    with open(p, "w", encoding="utf-8") as fh: fh.write(s)

def one(name):
    hits = list(walk(pattern_name=name))
    return hits[0] if hits else None

def fail(msg, required=True):
    print(("FAIL: " if required else "WARN: ") + msg)
    if required: failures.append(msg)

def brace_end(src, open_idx):
    depth = 0
    for i in range(open_idx, len(src)):
        c = src[i]
        if c == "{": depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0: return i
    return -1

# ---------- 1. BuildVars: own api_id / api_hash (Telegram API ToS 2.1) ----------
bv = one("BuildVars.java")
if not bv: fail("BuildVars.java not found")
else:
    s = read(bv)
    if not APP_ID or not APP_HASH or not APP_ID.isdigit():
        fail("TG_API_ID / TG_API_HASH secrets are missing or invalid (get your own at https://my.telegram.org)")
    else:
        s, n1 = re.subn(r'(APP_ID\s*=\s*)\d+', r'\g<1>' + APP_ID, s, count=1)
        s, n2 = re.subn(r'(APP_HASH\s*=\s*)"[^"]*"', r'\g<1>"' + APP_HASH + '"', s, count=1)
        if n1 != 1 or n2 != 1: fail("could not set APP_ID/APP_HASH in BuildVars.java")
        else: write(bv, s); print("ok: api_id/api_hash set")

# ---------- 2. applicationId ----------
cnt = 0
diag = []
for g in list(walk(suffix=".gradle")) + list(walk(suffix=".kts")):
    s = read(g)
    for i, line in enumerate(s.splitlines(), 1):
        if "applicationId" in line:
            diag.append(f"{os.path.relpath(g, UP)}:{i}: {line.strip()}")
    s2, n = re.subn(r'(applicationId\s*=?\s*)["\']org\.telegram\.messenger[\w.]*["\']', r'\g<1>"' + PKG + '"', s)
    if n: write(g, s2); cnt += n
print("info: applicationId lines found in gradle files:")
for d_ in diag: print("   ", d_)
marker = os.path.join(UP, ".ahb_force_pkg")
if cnt == 0:
    fail("applicationId not found in gradle files; will force it with an init script", required=False)
    write(marker, PKG)
else:
    print("ok: applicationId ->", PKG, f"({cnt} places)")
    if os.path.exists(marker): os.remove(marker)

# ---------- 3. manifests: avoid DUPLICATE_PERMISSION clash with official Telegram ----------
pm = 0
for m in walk(pattern_name="AndroidManifest.xml"):
    s = read(m)
    s2, n = re.subn(r'org\.telegram\.messenger(?:\.web|\.beta)?\.permission\.', '${applicationId}.permission.', s)
    if n: write(m, s2); pm += n
print("info: manifest permission names rewritten:", pm)

# ---------- 4. app name (no "Telegram" in the title, ToS 2.3) ----------
nm = 0
for x in walk(pattern_name="strings.xml"):
    s = read(x)
    s2, n = re.subn(r'(<string\s+name="AppName(?:Beta)?"[^>]*>)[^<]*(</string>)', r'\g<1>AHbsolute\g<2>', s)
    if n: write(x, s2); nm += n
if nm == 0: fail("AppName string not found")
else: print("ok: AppName ->", "AHbsolute", f"({nm})")

# ---------- 5. google-services.json: keep upstream clients, ADD a client for our package ----------
import copy
gj = os.environ.get("GOOGLE_SERVICES_JSON_PATH")
for g in walk(pattern_name="google-services.json"):
    if gj and os.path.exists(gj):
        write(g, read(gj)); print("ok: own google-services.json used"); continue
    try:
        data = json.loads(read(g))
        clients = data.get("client", [])
        have = set()
        for c in clients:
            have.add(c.get("client_info", {}).get("android_client_info", {}).get("package_name"))
        if clients and PKG not in have:
            c2 = copy.deepcopy(clients[0])
            c2["client_info"]["android_client_info"]["package_name"] = PKG
            clients.append(c2)
        write(g, json.dumps(data, indent=2))
        print("info: google-services.json now has clients for:", sorted(x for x in have if x) + [PKG])
    except Exception as e:
        fail("could not edit " + g + ": " + str(e), required=False)

if SAFE:
    if failures:
        print("\nrequired identity patch failed:", failures); sys.exit(1)
    print("\nSAFE MODE: only identity patches applied (api_id, name, package). UI features skipped."); sys.exit(0)

# ---------- UI features: each group is applied atomically; a group that does not fit is skipped ----------
def show(path, pats, maxn=40):
    if not path: return
    n = 0
    for i, l in enumerate(read(path).splitlines(), 1):
        if any(re.search(x, l) for x in pats):
            print(f"      {os.path.relpath(path, UP)}:{i}: {l.strip()[:170]}")
            n += 1
            if n >= maxn: break

features = {}

# ---- group "menu": DrawerLayoutAdapter + LaunchActivity ----
def patch_drawer(path):
    s = read(path)
    m = re.search(r'void\s+resetItems\s*\(\s*\)\s*\{', s)
    if not m: return None, "resetItems() not found in DrawerLayoutAdapter"
    end = brace_end(s, m.end() - 1)
    if end < 0: return None, "resetItems() braces do not match"
    add = ('\n        items.add(new Item(org.telegram.messenger.ahbsolute.AHbsolute.DRAWER_ID, "AHbsolute", R.drawable.ahbsolute_menu_icon));\n'
           '        ahbsoluteOrderItems();\n    ')
    method = '''
    private void ahbsoluteOrderItems() {
        ArrayList<Integer> slots = new ArrayList<>();
        ArrayList<Item> real = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != null) { slots.add(i); real.add(items.get(i)); }
        }
        int[] ids = new int[real.size()];
        String[] labels = new String[real.size()];
        for (int i = 0; i < real.size(); i++) { ids[i] = real.get(i).id; labels[i] = String.valueOf(real.get(i).text); }
        org.telegram.messenger.ahbsolute.AHbsolute.publishDrawer(ids, labels);
        int[] order = org.telegram.messenger.ahbsolute.AHbsolute.getDrawerOrder();
        if (order.length == 0) return;
        ArrayList<Item> sorted = new ArrayList<>();
        for (int id : order) { for (Item it : real) { if (it.id != id) continue; if (sorted.contains(it)) continue; sorted.add(it); } }
        for (Item it : real) { if (!sorted.contains(it)) sorted.add(it); }
        for (int i = 0; i < slots.size(); i++) items.set(slots.get(i), sorted.get(i));
    }
'''
    s = s[:end] + add + s[end:end + 1] + method + s[end + 1:]
    if "import java.util.ArrayList;" not in s:
        s = re.sub(r'(package [\w.]+;)', r'\1\nimport java.util.ArrayList;', s, count=1)
    return s, None

def patch_launch(path):
    s = read(path)
    pat = re.compile(r'((?:final\s+)?int\s+(\w+)\s*=\s*drawerLayoutAdapter\.getId\(position\);)')
    m = None
    for cand in pat.finditer(s):
        k = s.rfind("setOnItem", 0, cand.start())
        if k != -1 and s.startswith("setOnItemClickListener", k):
            m = cand; break
    if not m: return None, "drawerLayoutAdapter.getId(position) inside setOnItemClickListener not found in LaunchActivity"
    v = m.group(2)
    hook = (m.group(1) + f'\n                if (org.telegram.messenger.ahbsolute.AHbsolute.handleDrawerClick(LaunchActivity.this, {v})) {{ drawerLayoutContainer.closeDrawer(false); return; }}')
    return s[:m.start()] + hook + s[m.end():], None

da = one("DrawerLayoutAdapter.java") if "menu" in FEATS else None
la = one("LaunchActivity.java") if "menu" in FEATS else None
ir = one("ImageReceiver.java") if "avatar" in FEATS else None
da_new = la_new = None
errs = []
if da:
    da_new, e = patch_drawer(da)
    if e: errs.append(e)
else: errs.append("DrawerLayoutAdapter.java not found (or menu not requested)")
if la:
    la_new, e = patch_launch(la)
    if e: errs.append(e)
else: errs.append("LaunchActivity.java not found (or menu not requested)")
if da_new and la_new:
    write(da, da_new); write(la, la_new); features["menu"] = True
    print("ok: menu patches applied (DrawerLayoutAdapter + LaunchActivity)")
else:
    for e in errs: print("WARN: menu feature skipped:", e)
    print("   context for the author:")
    show(da, [r'items\.add', r'class\s+Item', r'resetItems', r'getId', r'public\s+\w+\s+\w+;'])
    show(la, [r'drawerLayoutAdapter', r'setOnItemClickListener', r'setOnItemLongClickListener'])

# ---- group "avatar": ImageReceiver ----
def patch_image(path):
    s = read(path)
    zero = re.search(r'private\s+int\[\]\s+roundRadius\b', s) is not None
    if zero:
        body = '''int a0 = roundRadius[0], a1 = roundRadius[1], a2 = roundRadius[2], a3 = roundRadius[3];
        roundRadius[0] = roundRadius[1] = roundRadius[2] = roundRadius[3] = 0;
        try { return ahOrigDraw(canvas); } finally {
            roundRadius[0] = a0; roundRadius[1] = a1; roundRadius[2] = a2; roundRadius[3] = a3;
            canvas.restoreToCount(ahSave);
        }'''
    else:
        print("info: roundRadius field not found, shapes will be clipped inside the circle")
        body = 'try { return ahOrigDraw(canvas); } finally { canvas.restoreToCount(ahSave); }'
    hook = '''public int ahShape = 0;
    public boolean draw(Canvas canvas) {
        if (ahShape == 0) return ahOrigDraw(canvas);
        float ahW = getImageWidth(), ahH = getImageHeight();
        if (Math.min(ahW, ahH) <= 0) return ahOrigDraw(canvas);
        int ahSave = canvas.save();
        canvas.translate(getImageX(), getImageY());
        canvas.clipPath(org.telegram.messenger.ahbsolute.AHbsolute.cachedPath(ahShape, ahW, ahH));
        canvas.translate(-getImageX(), -getImageY());
        ''' + body + '''
    }
    private boolean ahOrigDraw(Canvas canvas) {'''
    s, n = re.subn(r'public\s+boolean\s+draw\s*\(\s*Canvas\s+canvas\s*\)\s*\{', lambda _m: hook, s, count=1)
    if n != 1: return None, "ImageReceiver.draw(Canvas) not found"
    def inject(m):
        return m.group(0) + f'\n        ahShape = org.telegram.messenger.ahbsolute.AHbsolute.shapeFor({m.group(1)});'
    s, n2 = re.subn(r'public\s+void\s+setForUserOrChat\s*\(\s*TLObject\s+(\w+)\s*,[^)]*\)\s*\{', inject, s)
    if n2 == 0: return None, "ImageReceiver.setForUserOrChat(TLObject, ...) not found"
    print(f"info: ImageReceiver: setForUserOrChat overloads patched: {n2}, zero-radius: {zero}")
    return s, None

if ir:
    ir_new, e = patch_image(ir)
else:
    ir_new, e = None, "ImageReceiver.java not found (or avatar not requested)"
if ir_new:
    write(ir, ir_new); features["avatar"] = True
    print("ok: avatar patches applied (ImageReceiver)")
else:
    print("WARN: avatar feature skipped:", e)
    print("   context for the author:")
    show(ir, [r'boolean\s+draw\s*\(', r'setForUserOrChat', r'private\s+int\[\]\s+roundRadius', r'void\s+setImageCoords'])

# ---- group "settings": row "Кастомизация Пользователя" in Telegram's Settings screen (SettingsActivity) ----
def patch_settings(path):
    s = read(path)
    m = re.search(r'class\s+SettingsActivity\s+extends[^{]*\{', s)
    if not m: return None, "class SettingsActivity not found"
    s = s[:m.end()] + "\n    private int ahbRow = -1;" + s[m.end():]
    # row position: right after the Language row
    s, n = re.subn(r'(languageRow\s*=\s*rowCount\+\+;)', r'\1\n        ahbRow = rowCount++;', s, count=1)
    if n != 1: return None, "languageRow = rowCount++ not found in SettingsActivity"
    # click
    s, n = re.subn(r'if\s*\(position\s*==\s*languageRow\)\s*\{(\s*presentFragment\(new\s+LanguageSelectActivity)',
                   lambda mm: 'if (position == ahbRow) {\n                presentFragment(new org.telegram.messenger.ahbsolute.AHbsoluteCustomizationActivity());\n            } else if (position == languageRow) {' + mm.group(1), s, count=1)
    if n != 1: return None, "click handler for languageRow not found"
    # bind (text + icon)
    s, n = re.subn(r'if\s*\(position\s*==\s*languageRow\)\s*\{(\s*(\w+)\.setTextAndIcon\()',
                   lambda mm: 'if (position == ahbRow) {\n                ' + mm.group(2) + '.setTextAndIcon(org.telegram.messenger.ahbsolute.AHbsolute.t("Кастомизация Пользователя", "User Customization"), R.drawable.ahbsolute_menu_icon, false);\n            } else if (position == languageRow) {' + mm.group(1), s, count=1)
    if n != 1: return None, "bind (setTextAndIcon) for languageRow not found"
    # view type + enabled: only inside getItemViewType / isEnabled
    done = 0
    for name in ("getItemViewType", "isEnabled"):
        for mm in re.finditer(r'\b' + name + r'\s*\([^)]*\)\s*\{', s):
            end = brace_end(s, mm.end() - 1)
            if end < 0: continue
            body = s[mm.end():end]
            if "position == languageRow" in body or "position==languageRow" in body:
                body2 = re.sub(r'position\s*==\s*languageRow', '(position == languageRow || position == ahbRow)', body)
                s = s[:mm.end()] + body2 + s[end:]
                done += 1
                break
    if done < 2: return None, "getItemViewType/isEnabled do not reference languageRow (found %d of 2)" % done
    return s, None

sa = one("SettingsActivity.java") if "settings" in FEATS else None
if sa:
    sa_new, e = patch_settings(sa)
else:
    sa_new, e = None, "SettingsActivity.java not found (or settings not requested)"
if sa_new:
    write(sa, sa_new); features["settings"] = True
    print("ok: settings patch applied (row 'Кастомизация Пользователя')")
else:
    print("WARN: settings feature skipped:", e)
    print("   context for the author:")
    show(sa, [r'languageRow', r'class\s+SettingsActivity'])

if not (features.get("menu") or features.get("avatar")):
    fail("neither menu editor nor avatar shapes could be applied - refusing to produce a SAFE build")

write(os.path.join(UP, ".ahb_features"), ",".join(sorted(features)) or "none")
print("FEATURES APPLIED:", sorted(features) or "none")

# ---------- 9. ApplicationLoader: startup hook ----------
al = one("ApplicationLoader.java")
if not al: fail("ApplicationLoader.java not found", required=False)
else:
    s = read(al)
    m = re.search(r'public\s+void\s+onCreate\s*\(\s*\)\s*\{\s*super\.onCreate\(\);', s)
    if not m: fail("ApplicationLoader.onCreate not found", required=False)
    else:
        s = s[:m.end()] + '\n        try { org.telegram.messenger.ahbsolute.AHbsolute.onAppStart(this); } catch (Throwable ignored) {}' + s[m.end():]
        write(al, s); print("ok: ApplicationLoader patched")

if failures:
    print("\n%d required patch(es) failed. Upstream code layout probably changed; see README, section 'If the build fails'." % len(failures))
    sys.exit(1)
print("\nAll required patches applied.")
