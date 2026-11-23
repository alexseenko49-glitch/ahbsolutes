#!/usr/bin/env python3
"""Replaces every launcher icon with the AHbsolute icon (assets/icon.png). Usage: make_icons.py <upstream_dir>"""
import os, sys
from PIL import Image

UP = sys.argv[1]
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "icon.png")
src = Image.open(SRC).convert("RGBA")
side = min(src.size)
src = src.crop(((src.width - side) // 2, (src.height - side) // 2, (src.width + side) // 2, (src.height + side) // 2))

replaced, res_roots = 0, set()
for d, _, files in os.walk(UP):
    if "/build/" in d + "/" or "/.git" in d:
        continue
    base = os.path.basename(d)
    if not base.startswith("mipmap"):
        continue
    for f in files:
        if not f.startswith("ic_launcher"):
            continue
        p = os.path.join(d, f)
        if f.endswith(".xml"):
            if "anydpi" in base:
                with open(p, "w", encoding="utf-8") as fh:
                    fh.write('<?xml version="1.0" encoding="utf-8"?>\n'
                             '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                             '    <background android:drawable="@drawable/ahbsolute_icon_bg"/>\n'
                             '    <foreground android:drawable="@drawable/ahbsolute_icon_fg"/>\n'
                             '</adaptive-icon>\n')
                res_roots.add(os.path.dirname(d)); replaced += 1
            continue
        if not f.lower().endswith((".png", ".webp")):
            continue
        try:
            size = Image.open(p).size
        except Exception:
            size = (192, 192)
        img = src.resize(size, Image.LANCZOS)
        if "round" in f:
            mask = Image.new("L", size, 0)
            from PIL import ImageDraw
            ImageDraw.Draw(mask).ellipse((0, 0, size[0] - 1, size[1] - 1), fill=255)
            img.putalpha(mask)
        img.save(p, "WEBP" if f.lower().endswith(".webp") else "PNG")
        res_roots.add(os.path.dirname(d)); replaced += 1

for root in res_roots:
    dn = os.path.join(root, "drawable-nodpi")
    os.makedirs(dn, exist_ok=True)
    src.resize((432, 432), Image.LANCZOS).convert("RGB").save(os.path.join(dn, "ahbsolute_icon_bg.png"))
    Image.new("RGBA", (432, 432), (0, 0, 0, 0)).save(os.path.join(dn, "ahbsolute_icon_fg.png"))

print(f"launcher icons replaced: {replaced}")
# Trademark check: list in-app images that look like the Telegram logo
sus = []
for d, _, files in os.walk(UP):
    if "/build/" in d + "/" or "/.git" in d:
        continue
    for f in files:
        l = f.lower()
        if any(k in l for k in ("intro_tg_plane", "telegram_logo", "tg_logo", "ic_telegram", "telegram_plane")):
            sus.append(os.path.join(d, f))
if sus:
    print("WARN: possible Telegram logo assets still in the app (replace them before publishing):")
    for s_ in sus: print("   ", s_)
if replaced == 0:
    print("ERROR: no launcher icons found"); sys.exit(1)
