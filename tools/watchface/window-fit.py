"""Checks the Moon's date window: that each weekday the phone writes, beside each day of the month,
keeps clear of its day number. It takes the window's rule from tools/face-moon.py, which writes it
into the face: the weekday at faces.js's size from the window's inner end, scaled down about its
start and its baseline when it would come nearer its day than WD_GAP, by estimates of both widths
made from their characters (the Latin letters' classes, the Arabic-script names' own advances, the
figures'). It works out the scale as the face would, draws both texts in the face's fonts as Android
shapes them (Pillow with Raqm), and fails if the ink of a weekday comes nearer its day's than min=
px (3 by default), or if a weekday is cut by its box's ellipsis:

    python3 -I tools/watchface/window-fit.py                 # every weekday and day; fails if one does not fit
    python3 -I tools/watchface/window-fit.py min=4           # another margin
    python3 -I tools/watchface/window-fit.py show=الخميس,28  # how one weekday and day come out
    python3 -I tools/watchface/window-fit.py fonts=<dir>     # the watch's Bengali font from <dir>
    python3 -I tools/watchface/window-fit.py forms=<file>    # another phone-forms.json (a device's)

The weekdays are those the phone writes for the window, from tools/watchface/phone-forms.json
(forms=; shared's FaceFormsTest writes it from the phone's own code): for each tag the face is
measured for, CLDR's short weekdays in the words locale, without their dot, in the interface
language's capitals, as the feed's weekday is. The days are 1 to 31 in each digit set the file
gives the tag: Western, Arabic-Indic, extended Arabic-Indic and Bengali, the two Arabic-Indic
sets drawn in Noto Sans Arabic as the face draws them (ComplicationFields' NOTO_ZEROS). The tags that send the
same window are measured once and named together. Latin is in
the face's Manrope ExtraBold with the window's tracking, Arabic and Urdu in its Noto Sans Arabic
Bold, and Bengali, which Manrope does not have, in the watch's own Noto Sans Bengali UI at weight
800, from emulator-5558's /system/fonts when no fonts= is given (without it the run fails, as
hijri-fit.py's does: Bengali's are the binding weekdays). The emulator agrees with the widths to
about a pixel (tools/watchface/weekday.sh shows any weekday and day on it). It also checks that the
estimates are what the fonts say: each Arabic-script name's advance, and each Latin class no
narrower than any letter in it.
"""
import importlib.util
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont, features

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT_DIR = os.path.join(REPO, "watchface", "src", "main", "res", "font")
MANROPE = os.path.join(FONT_DIR, "manrope_extrabold.ttf")
NOTO = os.path.join(FONT_DIR, "noto_sans_arabic_subset_v2_bold.ttf")
FORMS = os.path.join(REPO, "tools", "watchface", "phone-forms.json")
SERIAL = "emulator-5558"
SS = 8

_spec = importlib.util.spec_from_file_location("face_moon", os.path.join(REPO, "tools", "face-moon.py"))
face_moon = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(face_moon)
M = face_moon

WESTERN, INDIC, EXTENDED, BENGALI = "0123456789", "٠١٢٣٤٥٦٧٨٩", "۰۱۲۳۴۵۶۷۸۹", "০১২৩৪৫৬৭৮৯"
# The zeros of the digit sets the face draws in Noto Sans Arabic (ComplicationFields' NOTO_ZEROS).
NOTO_ZEROS = (INDIC[0], EXTENDED[0])


def phones(path):
    """The windows the phones send, from a phone-forms.json: [(the tags that send it, right to left,
    the weekdays, the digit set, its numbers in Noto)], one for each (direction, weekdays, digit
    set), in the file's order; and the file, as named to the reader."""
    with open(path, encoding="utf-8") as f:
        locales = json.load(f)["locales"]
    rows = {}
    for tag, loc in locales.items():
        for zero_set in loc["digitSets"]:
            rows.setdefault((loc["rtl"], tuple(loc["weekdays"]), zero_set), []).append(tag)
    return ([(tags, rtl, list(weekdays), zero_set, zero_set[0] in NOTO_ZEROS)
             for (rtl, weekdays, zero_set), tags in rows.items()], os.path.relpath(path, REPO))


def digits(n, zero_set):
    return "".join(zero_set[int(c)] for c in str(n))


# ---- The face's estimates, worked out here (face_moon.weekday_em and day_em)

def classes_em(text, classes, rest, chars):
    total = 0.0
    for i, c in enumerate(text):
        if i >= chars:
            total += rest
            continue
        total += next((em for members, em in classes if c in members), rest)
    return total


def weekday_em(text, rtl):
    if not rtl:
        return classes_em(text, M.WD_LATIN, M.WD_LATIN_REST, M.WD_LATIN_CHARS) + len(text) * M.WD_TRACK
    return dict(M.WD_ARABIC).get(text, len(text) * M.WD_ARABIC_REST)


def day_em(text, arab):
    lead = next((em for members, em in (M.DAY_LEAD_NOTO if arab else M.DAY_LEAD_MANROPE) if text[:1] in members), 0)
    return (len(text) * M.DAY_NOTO if arab else classes_em(text, M.DAY_MANROPE, M.DAY_MANROPE_REST, 2)) - lead


def scale_of(weekday, day, rtl, arab):
    start, day_end = M.window_edges(rtl)
    room = day_end - start - M.WD_GAP - M.DAY_SIZE * day_em(day, arab)
    return min(max(room / (M.WD_SIZE[rtl] * weekday_em(weekday, rtl) + 0.01), M.WD_MIN), 1.0)


# ---- The fonts, as Android draws them

_fonts = {}


def font(path, size, weight=None):
    key = (path, size, weight)
    if key not in _fonts:
        f = ImageFont.truetype(path, round(size * SS), layout_engine=ImageFont.Layout.RAQM)
        if weight:
            f.set_variation_by_axes([weight])
        _fonts[key] = f
    return _fonts[key]


def ink(text, path, size, rtl=False, spacing=0.0, weight=None):
    """(ink left, ink right, advance), px from the pen's start, at size. With spacing (em), each
    letter's advance is spacing more and the letter sits half of it in, as Android spaces letters."""
    f = font(path, size, weight)
    w, h = int(size * SS * (len(text) + 4)), int(size * SS * 3)
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    o = int(size * SS)
    kw = {"direction": "rtl"} if rtl else {}
    if spacing:
        sp = spacing * size * SS
        for i, ch in enumerate(text):
            d.text((o + f.getlength(text[:i]) + i * sp + sp / 2, o), ch, font=f, fill=255)
        adv = f.getlength(text) + len(text) * sp
    else:
        d.text((o, o), text, font=f, fill=255, **kw)
        adv = f.getlength(text, **kw)
    cols = np.nonzero((np.asarray(img) > 96).any(axis=0))[0]
    return (cols[0] - o) / SS, (cols[-1] + 1 - o) / SS, adv / SS


def weekday_font(weekday, rtl, bengali):
    if rtl:
        return NOTO, None
    if any("\u0980" <= c <= "\u09ff" for c in weekday):
        return bengali, 800
    return MANROPE, None


def day_font(day, arab, bengali):
    if arab:
        return NOTO, None
    if any("\u0980" <= c <= "\u09ff" for c in day):
        return bengali, 800
    return MANROPE, None


def place(weekday, day, rtl, arab, bengali):
    """How the window comes out: (scale, the ink gap px, the weekday's full advance px)."""
    s = scale_of(weekday, day, rtl, arab)
    start, day_end = M.window_edges(rtl)
    wpath, wweight = weekday_font(weekday, rtl, bengali)
    # Android spaces the letters of Latin, not of the scripts whose letters join (Minikin leaves
    # Arabic and Bengali unspaced)
    spaced = not rtl and wweight is None
    wl, wr, wadv = ink(weekday, wpath, M.WD_SIZE[rtl], rtl=rtl, spacing=M.WD_TRACK if spaced else 0, weight=wweight)
    dpath, dweight = day_font(day, arab, bengali)
    dl, dr, dadv = ink(day, dpath, M.DAY_SIZE, weight=dweight)
    return s, (day_end - dadv + dl) - (start + s * wr), wadv


# ---- The estimates against the fonts

def check_estimates():
    bad = []
    for name, em in M.WD_ARABIC:
        real = font(NOTO, 1000).getlength(name, direction="rtl") / 1000 / SS
        if abs(real - em) > 0.002:
            bad.append("%s is %.3f em in the font, the face says %.3f" % (name, real, em))
    for members, em in M.WD_LATIN:
        for c in members:
            if "\u0980" <= c <= "\u09ff":
                continue
            real = font(MANROPE, 1000).getlength(c) / 1000 / SS
            if real > em + 0.0005:
                bad.append("%r is %.3f em in Manrope ExtraBold, wider than its class's %.3f" % (c, real, em))
    for members, em in M.DAY_MANROPE:
        for c in members:
            real = font(MANROPE, 1000).getlength(c) / 1000 / SS
            if abs(real - em) > 0.0015:
                bad.append("figure %r is %.3f em in Manrope ExtraBold, the face says %.3f" % (c, real, em))
    for path, classes in ((MANROPE, M.DAY_LEAD_MANROPE), (NOTO, M.DAY_LEAD_NOTO)):
        for members, em in classes:
            for c in members:
                l, r, adv = ink(c, path, 100)
                if l / 100 < em - 0.0015 or l / 100 > em + 0.012:
                    bad.append("figure %r's left side bearing is %.3f em, the face takes %.3f" % (c, l / 100, em))
    for c in WESTERN + INDIC + EXTENDED:
        real = font(NOTO, 1000).getlength(c) / 1000 / SS
        if abs(real - M.DAY_NOTO) > 0.0015:
            bad.append("figure %r is %.3f em in Noto Sans Arabic Bold, the face says %.3f" % (c, real, M.DAY_NOTO))
    return bad


def system_fonts(arg, out):
    if arg:
        return arg
    try:
        subprocess.run(["adb", "-s", SERIAL, "pull", "/system/fonts/NotoSansBengaliUI-VF.ttf", out],
                       check=True, capture_output=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        pass
    return out


def main(argv):
    opts = dict(a.split("=", 1) for a in argv if "=" in a and not a.startswith("show="))
    shows = [a[5:] for a in argv if a.startswith("show=")]
    least = float(opts.get("min", 3))
    if not features.check("raqm"):
        sys.exit("Pillow has no Raqm here, so the weekdays cannot be shaped as Android shapes them")
    with tempfile.TemporaryDirectory() as tmp:
        bengali = os.path.join(system_fonts(opts.get("fonts"), tmp), "NotoSansBengaliUI-VF.ttf")
        has_bengali = os.path.exists(bengali)
        if has_bengali:
            font(bengali, M.WD_SIZE[False], 800)    # loaded while the copy is there
            font(bengali, M.DAY_SIZE, 800)
        if shows:
            for show in shows:
                weekday, day = show.split(",")
                rtl = any("\u0600" <= c <= "\u06ff" for c in weekday)
                arab = any(c in INDIC + EXTENDED for c in day)
                s, g, wadv = place(weekday, day, rtl, arab, bengali)
                print("%s %s (%s): scale %.3f, the weekday %.1f px at %.2f px, %.1f px from the day"
                      % (weekday, day, "Arabic-Indic" if arab else "right to left" if rtl else "left to right",
                         s, wadv * s, M.WD_SIZE[rtl] * s, g))
            return
        bad = check_estimates()
        for b in bad:
            print("  ESTIMATE: " + b)
        windows, source = phones(opts.get("forms", FORMS))
        print("The Moon's date window (tools/face-moon.py WD_GAP %s px, WD_MIN %s), the ink at least %s px from the day,"
              " for the phones' weekdays in %s:" % (M.f(M.WD_GAP), M.f(M.WD_MIN), M.f(least), source))
        fails = 0
        unchecked = []
        for tags, rtl, names, zero_set, arab in windows:
            lang = ",".join(tags) if len(tags) <= 4 else "%s and %d more" % (",".join(tags[:3]), len(tags) - 3)
            if any("\u0980" <= c <= "\u09ff" for name in names for c in name) and not has_bengali:
                print("  %s NOT CHECKED: no Bengali font (fonts=<dir> holding NotoSansBengaliUI-VF.ttf, or start %s);"
                      " Bengali's are the binding weekdays, so the run cannot pass without it" % (lang, SERIAL))
                unchecked.append(lang)
                continue
            rows = []
            for name in names:
                for n in range(1, 32):
                    day = digits(n, zero_set)
                    s, g, wadv = place(name, day, rtl, arab, bengali)
                    rows.append((g, s, name, day, wadv))
                    cut = wadv > M.WD_BOX
                    if g < least or cut:
                        fails += 1
                        if fails <= 20:
                            print("  DOES NOT FIT: %s %s, %.1f px from the day%s" % (name, day, g, ", cut by the ellipsis" if cut else ""))
            g, s, name, day, _ = min(rows)
            small = min(rows, key=lambda r: r[1])
            full = sum(1 for r in rows if r[1] >= 1.0)
            print("  %-30s %-13s %4d dates, %3d%% at full size, least scale %.2f (%s %s), nearest %.1f px (%s %s)"
                  % (lang, "Arabic-Indic" if arab else zero_set[1:4] + "…", len(rows), round(100.0 * full / len(rows)),
                     small[1], small[2], small[3], g, name, day))
        if bad or fails or unchecked:
            sys.exit("tools/watchface/window-fit.py: %d dates do not fit, %d estimates are off, %d windows not checked"
                     % (fails, len(bad), len(unchecked)))
        print("tools/watchface/window-fit.py: every weekday keeps clear of its day")


if __name__ == "__main__":
    main(sys.argv[1:])
