"""Checks that the Arch style's Hijri line fits the arch's crown for every date the phone can send:
each day (1 to 30) of each month in each of the seven languages, in each digit set the phone
draws, as day and month (Ruling 24's form). It takes the line's steps from tools/face-centre.py,
which writes them into the face (ARCH_HIJRI_STEPS: the size by the text's length, each step's box),
draws each date as the face would, in the step its length picks, at each of the line's places (its
own, and the one LAYOUT_COMPS gives it when the wearer's slots are filled, if that is another), and
measures it against
the lit stroke's inner edge (faces.js ARCH2 at x 2.25, the lit stroke 3.8 units wide, through
tools/face-arch.py):

    python3 -I tools/watchface/hijri-fit.py                  # every form; fails if one does not fit
    python3 -I tools/watchface/hijri-fit.py min=7 year=1448  # another margin; the forms with a year
    python3 -I tools/watchface/hijri-fit.py fonts=<dir>      # the watch's Bengali font from <dir>

A form fits when it is drawn whole in its box (its width under the box's, so the box's ellipsis,
a guard, never cuts it) and its ink keeps min= px (7 by default) from the lit stroke, which lies
over the outline where it is lit. Latin is drawn in the face's Manrope SemiBold. Arabic and Urdu
are the right-to-left line, in the face's Noto Sans Arabic at 12.5/12 of the size (faces.js's
Arabic Hijri size). Bengali is drawn in the watch's Noto Sans Bengali UI at weight 600 (the device
draws it lighter, so narrower), which comes from emulator-5558's /system/fonts when no fonts= is
given; without it the run fails, because Bengali's are the binding forms.

The baseline of each step's box is the Manrope rule from the face's header: the box's centre plus
0.38 of the size. Widths come from Pillow with Raqm (HarfBuzz), as Android shapes them; the
emulator agrees to about a pixel (tools/watchface/hijri.sh shows any one form on it).
"""
import importlib.util
import os
import re
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont, features

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT_DIR = os.path.join(REPO, "watchface", "src", "main", "res", "font")
MONTHS_KT = os.path.join(REPO, "shared", "src", "commonMain", "kotlin", "world", "taqwa", "app", "hijri", "HijriMonthNames.kt")
SERIAL = "emulator-5558"

K = 2.25
LIT_HALF = 3.8 * K / 2      # the awake lit stroke's half width, face px
SS = 4                      # supersampling of each drawn date

spec = importlib.util.spec_from_file_location("face_arch", os.path.join(REPO, "tools", "face-arch.py"))
face_arch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(face_arch)

# The arch's centre line in face px, both sides: the crown, then the foot down to the screen's edge.
_right = [face_arch.bez(i / 4000) for i in range(4001)] + [(180, 118 + 42 * i / 400) for i in range(401)]
_r = np.array([(x * K, y * K) for x, y in _right])
_l = _r.copy()
_l[:, 0] = 450 - _l[:, 0]
CURVE = np.vstack([_r, _l])


def clearance(xs, ys):
    """The least distance from the points to the lit stroke's edge, face px (negative: on it)."""
    d = np.sqrt((CURVE[None, :, 0] - xs[:, None]) ** 2 + (CURVE[None, :, 1] - ys[:, None]) ** 2)
    return d.min() - LIT_HALF


_spec = importlib.util.spec_from_file_location("face_centre", os.path.join(REPO, "tools", "face-centre.py"))
face_centre = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(face_centre)


def steps():
    """Arch's Hijri steps as face-centre.py writes them, in order: (most characters or None, size,
    box, [baselines]), the box as (x, y, width, height) in face px and a baseline for each of the
    line's places, its own and the one LAYOUT_COMPS lifts it to."""
    lift = (face_centre.LAYOUT_COMPS["arch"]["h"] - face_centre.LAYOUT["arch"]["h"]) * K
    x, width = face_centre.ARCH_HIJRI_BOX
    out = []
    for most, size in face_centre.ARCH_HIJRI_STEPS:
        top, height = face_centre.ARCH_HIJRI_TOPS[size]
        base = top + height / 2 + 0.38 * size
        out.append((most, size, (x, top, width, height), [base, base + lift] if lift else [base]))
    return out


def months():
    src = open(MONTHS_KT, encoding="utf-8").read()
    return {lang: re.findall(r'"([^"]+)"', body) for lang, body in re.findall(r"UiLanguage\.(\w+) to listOf\((.*?)\)", src, re.S)}


DIGITS = {"latn": "0123456789", "arab": "٠١٢٣٤٥٦٧٨٩", "arabext": "۰۱۲۳۴۵۶۷۸۹", "beng": "০১২৩৪৫৬৭৮৯"}
# The digit sets each language's phone may draw (the app follows the region's CLDR digits).
LANG_DIGITS = {"ENGLISH": ["latn"], "FRENCH": ["latn"], "TURKISH": ["latn"], "INDONESIAN": ["latn"],
               "ARABIC": ["latn", "arab"], "URDU": ["latn", "arabext"], "BENGALI": ["beng", "latn"]}


def system_fonts(arg, out):
    """The directory holding the watch's Bengali font: fonts=, or a copy from the emulator into out."""
    names = ("NotoSansBengaliUI-VF.ttf",)
    if arg:
        return arg
    for name in names:
        try:
            subprocess.run(["adb", "-s", SERIAL, "pull", "/system/fonts/" + name, out],
                           check=True, capture_output=True, timeout=30)
        except (OSError, subprocess.SubprocessError):
            pass
    return out


_fonts = {}


def font(path, size, weight=None):
    key = (path, size, weight)
    if key not in _fonts:
        f = ImageFont.truetype(path, round(size * SS), layout_engine=ImageFont.Layout.RAQM)
        if weight:
            f.set_variation_by_axes([weight])
        _fonts[key] = f
    return _fonts[key]


def measure(text, path, size, baseline, rtl, weight=None):
    """(advance width, least clearance to the lit stroke) of text centred on the face's axis."""
    f = font(path, size, weight)
    top = 40
    img = Image.new("L", (450 * SS, 200 * SS), 0)
    d = ImageDraw.Draw(img)
    kw = {"direction": "rtl"} if rtl else {}
    w = d.textlength(text, font=f, **kw)
    d.text((225 * SS - w / 2, (baseline - top) * SS), text, font=f, fill=255, anchor="ls", **kw)
    ink = np.asarray(img) > 96
    xs, ys = [], []
    for r in np.nonzero(ink.any(axis=1))[0]:
        c = np.nonzero(ink[r])[0]
        xs += [c[0] / SS, (c[-1] + 1) / SS]
        ys += [r / SS + top] * 2
    return w / SS, clearance(np.array(xs), np.array(ys))


def main(argv):
    opts = dict(a.split("=", 1) for a in argv if "=" in a)
    least = float(opts.get("min", 7))
    year = int(opts["year"]) if "year" in opts else None
    if not features.check("raqm"):
        sys.exit("Pillow has no Raqm here, so Arabic and Bengali cannot be shaped")
    with tempfile.TemporaryDirectory(prefix="taqwa-hijri-fit.") as scratch:
        return run(opts, least, year, system_fonts(opts.get("fonts"), scratch))


def run(opts, least, year, sysdir):
    manrope = os.path.join(FONT_DIR, "manrope_semibold.ttf")
    noto_ar = os.path.join(FONT_DIR, "noto_sans_arabic_subset_v2_semibold.ttf")
    bengali = os.path.join(sysdir, "NotoSansBengaliUI-VF.ttf")
    if not os.path.exists(bengali):
        print("no Bengali font (fonts=<dir> holding NotoSansBengaliUI-VF.ttf, or start emulator-5558):"
              " Bengali's are the binding forms, so the run cannot pass without it")
        return 1
    # per language: [(font, size factor, rtl, weight)]
    drawn = {lang: [(manrope, 1.0, False, None)] for lang in ("ENGLISH", "FRENCH", "TURKISH", "INDONESIAN")}
    for lang in ("ARABIC", "URDU"):
        drawn[lang] = [(noto_ar, face_centre.ARABIC_HIJRI, True, None)]
    drawn["BENGALI"] = [(bengali, 1.0, False, 600)]

    table = steps()
    print("Arch's Hijri steps (tools/face-centre.py ARCH_HIJRI_STEPS):")
    for most, size, box, bases in table:
        print("  %-15s %5.2f px  box x %d..%d, y %d..%d  baselines %s" % (
            "up to %d chars" % most if most else "longer", size, box[0], box[0] + box[2], box[1], box[1] + box[3],
            " and ".join("%.2f" % b for b in bases)))

    def step_for(n):
        return next(s for s in table if s[0] is None or n <= s[0])

    worst = {}
    failures = []
    count = 0
    for lang, names in months().items():
        if lang not in drawn:
            continue
        for ds in LANG_DIGITS[lang]:
            dg = lambda v: "".join(DIGITS[ds][int(c)] for c in str(v))
            for name in names:
                for day in range(1, 31):
                    text = dg(day) + " " + name + (" " + dg(year) if year else "")
                    most, size, box, bases = step_for(len(text))
                    for (path, factor, rtl, weight), base in ((d, b) for d in drawn[lang] for b in bases):
                        w, c = measure(text, path, size * factor, base, rtl, weight)
                        count += 1
                        key = (most, size)
                        if key not in worst or c < worst[key][0]:
                            worst[key] = (c, text, os.path.basename(path), w, box[2])
                        if c < least or w > box[2] - 2:
                            failures.append("%s (%s, %d chars, %.1f px): %.1f px wide in a %d px box, %.1f px from the lit stroke" % (
                                text, os.path.basename(path), len(text), size * factor, w, box[2], c))
    print("%d dates drawn; the least room to the lit stroke in each step:" % count)
    for (most, size), (c, text, path, w, bw) in sorted(worst.items(), key=lambda kv: -kv[0][1]):
        print("  %5.2f px: %.1f px, %s (%s, %.1f px wide of %d)" % (size, c, text, path, w, bw))
    if failures:
        print("%d do not fit (min=%g px):" % (len(failures), least))
        for f in failures[:40]:
            print("  " + f)
        return 1
    print("every date fits: drawn whole, and %g px or more from the lit stroke" % least)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
