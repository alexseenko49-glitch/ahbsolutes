#!/usr/bin/env python3
"""Applies AHbsolute patches to a checkout of Telegram-Android.
Usage: apply_patches.py <upstream_dir>
Required patches fail loudly (exit 1) so CI never silently builds something half-patched."""
import os, re, sys, json

SAFE = "--safe" in sys.argv
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
for g in list(walk(suffix="build.gradle")) + list(walk(suffix="build.gradle.kts")):
    s = read(g)
    s2, n = re.subn(r'(applicationId\s*=?\s*)["\']org\.telegram\.messenger[\w.]*["\']', r'\g<1>"' + PKG + '"', s)
    if n: write(g, s2); cnt += n
if cnt == 0: fail("applicationId not found in any build.gradle (app keeps the upstream package id)", required=False)
else: print("ok: applicationId ->", PKG, f"({cnt} places)")

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

# ---------- 5. google-services.json: retarget to our package ----------
gj = os.environ.get("GOOGLE_SERVICES_JSON_PATH")
for g in walk(pattern_name="google-services.json"):
    if gj and os.path.exists(gj):
        write(g, read(gj)); print("ok: own google-services.json used")
    else:
        s = read(g)
        s2 = re.sub(r'("package_name"\s*:\s*)"[^"]*"', r'\g<1>"' + PKG + '"', s)
        write(g, s2); print("info: dummy google-services.json retargeted (no FCM push; set GOOGLE_SERVICES_JSON secret to enable)")

if SAFE:
    if failures:
        print("\nrequired identity patch failed:", failures); sys.exit(1)
    print("\nSAFE MODE: only identity patches applied (api_id, name, package). UI features skipped."); sys.exit(0)

# ---------- 6. DrawerLayoutAdapter: AHbsolute item + custom order ----------
da = one("DrawerLayoutAdapter.java")
if not da: fail("DrawerLayoutAdapter.java not found")
else:
    s = read(da)
    m = re.search(r'void\s+resetItems\s*\(\s*\)\s*\{', s)
    if not m: fail("resetItems() not found in DrawerLayoutAdapter")
    else:
        end = brace_end(s, m.end() - 1)
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
        for (int id : order) for (Item it : real) if (it.id == id && !sorted.contains(it)) sorted.add(it);
        for (Item it : real) if (!sorted.contains(it)) sorted.add(it);
        for (int i = 0; i < slots.size(); i++) items.set(slots.get(i), sorted.get(i));
    }
'''
        s = s[:end] + add + s[end:end + 1] + method + s[end + 1:]
        if "import java.util.ArrayList;" not in s:
            s = re.sub(r'(package [\w.]+;)', r'\1\nimport java.util.ArrayList;', s, count=1)
        write(da, s); print("ok: DrawerLayoutAdapter patched")

# ---------- 7. LaunchActivity: handle click on AHbsolute entry ----------
la = one("LaunchActivity.java")
if not la: fail("LaunchActivity.java not found")
else:
    s = read(la)
    pat = re.compile(r'((?:final\s+)?int\s+(\w+)\s*=\s*drawerLayoutAdapter\.getId\(position\);)')
    m = None
    for cand in pat.finditer(s):
        k = s.rfind("setOnItem", 0, cand.start())
        if k != -1 and s.startswith("setOnItemClickListener", k):
            m = cand; break
    if not m: fail("drawerLayoutAdapter.getId(position) inside setOnItemClickListener not found in LaunchActivity")
    else:
        v = m.group(2)
        hook = (m.group(1) + f'\n                if (org.telegram.messenger.ahbsolute.AHbsolute.handleDrawerClick(LaunchActivity.this, {v})) {{ drawerLayoutContainer.closeDrawer(false); return; }}')
        s = s[:m.start()] + hook + s[m.end():]
        write(la, s); print("ok: LaunchActivity patched")

# ---------- 8. ImageReceiver: shaped avatars ----------
ir = one("ImageReceiver.java")
if not ir: fail("ImageReceiver.java not found")
else:
    s = read(ir)
    zero = re.search(r'private\s+int\[\]\s+roundRadius\b', s) is not None
    if zero:
        body = '''int a0 = roundRadius[0], a1 = roundRadius[1], a2 = roundRadius[2], a3 = roundRadius[3];
        roundRadius[0] = roundRadius[1] = roundRadius[2] = roundRadius[3] = 0;
        try { return ahOrigDraw(canvas); } finally {
            roundRadius[0] = a0; roundRadius[1] = a1; roundRadius[2] = a2; roundRadius[3] = a3;
            canvas.restoreToCount(ahSave);
        }'''
    else:
        print("warn: roundRadius field not found, shapes will be clipped inside the circle")
        body = 'try { return ahOrigDraw(canvas); } finally { canvas.restoreToCount(ahSave); }'
    hook = '''public int ahShape = 0;
    public boolean draw(Canvas canvas) {
        if (ahShape == 0) return ahOrigDraw(canvas);
        float ahW = getImageWidth(), ahH = getImageHeight();
        if (ahW <= 0 || ahH <= 0) return ahOrigDraw(canvas);
        int ahSave = canvas.save();
        canvas.translate(getImageX(), getImageY());
        canvas.clipPath(org.telegram.messenger.ahbsolute.AHbsolute.cachedPath(ahShape, ahW, ahH));
        canvas.translate(-getImageX(), -getImageY());
        ''' + body + '''
    }
    private boolean ahOrigDraw(Canvas canvas) {'''
    s, n = re.subn(r'public\s+boolean\s+draw\s*\(\s*Canvas\s+canvas\s*\)\s*\{', lambda _m: hook, s, count=1)
    if n != 1: fail("ImageReceiver.draw(Canvas) not found")
    # set shape whenever an avatar is assigned
    def inject(m):
        return m.group(0) + f'\n        ahShape = org.telegram.messenger.ahbsolute.AHbsolute.shapeFor({m.group(1)});'
    s, n2 = re.subn(r'public\s+void\s+setForUserOrChat\s*\(\s*TLObject\s+(\w+)\s*,[^)]*\)\s*\{', inject, s)
    if n2 == 0: fail("ImageReceiver.setForUserOrChat not found")
    write(ir, s); print(f"ok: ImageReceiver patched (setForUserOrChat overloads: {n2}, zero-radius: {zero})")

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
