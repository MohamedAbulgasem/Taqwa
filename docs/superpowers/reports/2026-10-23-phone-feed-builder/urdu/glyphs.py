"""glyphs.py: Urdu's ۴ ۶ ۷ on the face against the fonts' own glyphs (P2.3b Step 6's evidence, committed
beside the report; no build or test runs it).

    python3 -I glyphs.py verdict <fonts dir> <shot.png> <x0,y0,x1,y1> <weight> <size units> <text>
    python3 -I glyphs.py strip <fonts dir> <out.png> <weight>:<size units>[:<label>] ...
    python3 -I glyphs.py sheet <fonts dir> <out.png> <title> <spec.json>

<fonts dir> holds v2_<weight>.ttf (the face's fonts as committed: U+06F4, U+06F6 and U+06F7 mapped to
Noto's own Urdu forms, URD locl) and old_<weight>.ttf (the fonts before plan 2 Task 2, ff28cd5a: the
same characters mapped to Noto's default, Persian, forms; in the Light their pnum forms). Both are
drawn with no language, so each draws what its cmap names: v2 the Urdu forms, old the Persian ones.

verdict: for each of ۴ ۶ ۷ in <text>, that glyph drawn in each font at the face's size (units of
the 450-unit face, at the emulator's 454 px; and every 2% to 6% either side) and matched anywhere in
the shot's box (its luminance), turned as well for the Moon's curved lines ("angles" in the sheet's
spec); prints each font's best normalized cross-correlation and which wins.
Only the font differs between the two glyphs, so the winner names the form the face drew.
strip: ۴ ۶ ۷ in each font at each size, at 3x, Urdu forms above and Persian below.
sheet: the shots' boxes at 3x, each beside the two fonts' drawings of its text at its size, and the
verdict under it.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

PX = 454 / 450
DIGITS = "۴ ۶ ۷"
NAMES = str.maketrans({"۴": "U+06F4", "۶": "U+06F6", "۷": "U+06F7"})   # the labels' font has no Arabic


def label_font():
    try:
        return ImageFont.load_default(size=16)
    except TypeError:
        return ImageFont.load_default()


def font(fonts, which, weight, px):
    return ImageFont.truetype(os.path.join(fonts, "%s_%s.ttf" % (which, weight)), size=px,
                              layout_engine=ImageFont.Layout.RAQM)


def draw(fonts, which, weight, px, text):
    """The text, white on black, its ink's box only (grey levels 0-1)."""
    f = font(fonts, which, weight, px)
    rtl = any("؀" <= c <= "ۿ" and not ("۰" <= c <= "۹") and not ("٠" <= c <= "٩") for c in text)
    kw = {"direction": "rtl"} if rtl else {"direction": "ltr"}
    box = f.getbbox(text, **kw)
    w, h = box[2] - box[0] + 8, box[3] - box[1] + 8
    img = Image.new("L", (w, h), 0)
    ImageDraw.Draw(img).text((4 - box[0], 4 - box[1]), text, fill=255, font=f, **kw)
    a = np.asarray(img).astype(float) / 255
    ys, xs = np.nonzero(a > 0.05)
    return a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def luminance(img, box):
    a = np.asarray(img.convert("RGB").crop(box)).astype(float) / 255
    return a.max(axis=2)


def ncc(region, tmpl):
    """The best normalized cross-correlation of tmpl over every offset inside region."""
    H, W = region.shape
    h, w = tmpl.shape
    if h > H or w > W:
        return -1.0
    t = tmpl - tmpl.mean()
    tn = np.sqrt((t * t).sum())
    win = np.lib.stride_tricks.sliding_window_view(region, (h, w))
    r = win - win.mean(axis=(2, 3), keepdims=True)
    rn = np.sqrt((r * r).sum(axis=(2, 3)))
    num = (r * t).sum(axis=(2, 3))
    with np.errstate(invalid="ignore", divide="ignore"):
        score = np.where(rn > 0, num / (rn * tn), -1.0)
    return float(score.max())


def turned(a, angle):
    """The glyph turned by angle degrees (counter-clockwise), its ink's box only."""
    if not angle:
        return a
    img = Image.fromarray((np.pad(a, 6) * 255).astype("uint8")).rotate(angle, resample=Image.BICUBIC, expand=True)
    b = np.asarray(img).astype(float) / 255
    ys, xs = np.nonzero(b > 0.05)
    return b[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def verdict(fonts, shot, box, weight, units, text, angles=(0,)):
    """For each of ۴ ۶ ۷ that the text holds: that one glyph in each font, at the face's size and
    within 6% of it, matched anywhere in the box; the font whose glyph matches best names the form
    the face drew."""
    region = luminance(Image.open(shot), box)
    out = {}
    for digit in [d for d in "۴۶۷" if d in text]:
        res = {}
        for which in ("v2", "old"):
            best = (-1.0, None)
            for k in range(-6, 7, 2):
                px = units * PX * (1 + k / 100)
                glyph = draw(fonts, which, weight, px, digit)
                for angle in angles:
                    score = ncc(region, turned(glyph, angle))
                    if score > best[0]:
                        best = (score, round(px, 2), angle)
            res[which] = best
        res["win"] = "Urdu" if res["v2"][0] > res["old"][0] else "PERSIAN"
        out[digit] = res
    return out


def said(out):
    return "; ".join("%s: Urdu %.3f, Persian %.3f -> %s" % (d, r["v2"][0], r["old"][0], r["win"]) for d, r in out.items())


def strip(fonts, specs):
    rows = []
    for spec in specs:
        weight, units = spec[0], float(spec[1])
        label = spec[2] if len(spec) > 2 else "%s %s u" % (weight, units)
        px = units * PX * 3
        cells = [(which, Image.fromarray((draw(fonts, which, weight, px, DIGITS) * 255).astype("uint8"))) for which in ("v2", "old")]
        rows.append((label, cells))
    lab = label_font()
    W = 20 + max(sum(c.size[0] + 160 for _, c in cells) for _, cells in rows)
    H = 10 + sum(max(c.size[1] for _, c in cells) + 36 for _, cells in rows)
    out = Image.new("RGB", (max(W, 700), H), (40, 40, 40))
    d = ImageDraw.Draw(out)
    y = 10
    for label, cells in rows:
        d.text((10, y), label + ", 3x: Urdu forms (the face's v2 fonts) | Persian forms (the fonts before)", fill=(230, 230, 230), font=lab)
        x = 10
        for which, c in cells:
            out.paste(Image.merge("RGB", (c, c, c)), (x, y + 22))
            x += c.size[0] + 160
        y += max(c.size[1] for _, c in cells) + 36
    return out


def sheet(fonts, out, title, spec):
    lab = label_font()
    blocks = []
    for item in spec:
        shot = Image.open(item["shot"]).convert("RGB")
        box = tuple(item["box"])
        crop = shot.crop(box).resize(((box[2] - box[0]) * 3, (box[3] - box[1]) * 3), Image.NEAREST)
        res = verdict(fonts, item["shot"], box, item["weight"], item["units"], item["text"],
                      tuple(item.get("angles", (0,))))
        draws = []
        for which in ("v2", "old"):
            a = draw(fonts, which, item["weight"], item["units"] * PX * 3, item["text"])
            g = Image.fromarray((a * 255).astype("uint8"))
            draws.append(Image.merge("RGB", (g, g, g)))
        line = "%s | %s %s u | %s" % (item["label"], item["weight"], item["units"], said(res))
        item["verdict"] = res
        print(line)
        blocks.append((line, [("the shot, 3x", crop), ("Urdu forms (v2)", draws[0]), ("Persian forms (old)", draws[1])]))
    W = 20 + max(sum(im.size[0] + 20 for _, im in cells) for _, cells in blocks)
    H = 30 + sum(max(im.size[1] for _, im in cells) + 60 for _, cells in blocks)
    img = Image.new("RGB", (W, H), (40, 40, 40))
    d = ImageDraw.Draw(img)
    d.text((10, 8), title, fill=(230, 230, 230), font=lab)
    y = 30
    for line, cells in blocks:
        d.text((10, y), line.translate(NAMES), fill=(230, 230, 230), font=lab)
        x = 10
        for name, im in cells:
            d.text((x, y + 20), name, fill=(170, 170, 170), font=lab)
            img.paste(im, (x, y + 40))
            x += im.size[0] + 20
        y += max(im.size[1] for _, im in cells) + 60
    img.save(out)
    print(out)


if __name__ == "__main__":
    cmd, fonts = sys.argv[1], sys.argv[2]
    if cmd == "verdict":
        shot, box, weight, units, text = sys.argv[3], tuple(int(v) for v in sys.argv[4].split(",")), sys.argv[5], float(sys.argv[6]), sys.argv[7]
        print(said(verdict(fonts, shot, box, weight, units, text)))
    elif cmd == "strip":
        strip(fonts, [s.split(":") for s in sys.argv[4:]]).save(sys.argv[3])
        print(sys.argv[3])
    elif cmd == "sheet":
        sheet(fonts, sys.argv[3], sys.argv[4], json.load(open(sys.argv[5], encoding="utf-8")))
    else:
        sys.exit(__doc__)
