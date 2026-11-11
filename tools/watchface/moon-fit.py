"""Checks that the Moon style's curved lines fit, for every form the phone can send: the prayer and
its time over the top (slot 0), the countdown under it (slot 6) and the Hijri date under the moon
(slot 2), on its circle with the wearer's slots empty and on its tighter one with either filled
(option (d)). Each curve's circle, size, fonts, tracking and path are read from tools/face-moon.py,
which writes them into the face. Each form is measured in the face's fonts as Android shapes them
(Pillow with Raqm), turned into degrees along its curve as the runtime lays it out (the calibration
below), and the run fails if one does not fit:

- the Hijri date: every day (1 to 30) of every month in the seven languages, in each digit set the
  phone draws, as day and month (Ruling 24's form). It must stay inside its path (104 degrees with
  the slots empty, 120 with one filled) by min= px at each end: the runtime cuts a curved text at
  its path's ends, and this one has no ellipsis;
- the countdown: each of its four phrases, filled with every hour (1 to 99) and minute (1 to 59) in
  each phone's digits, inside its 124-degree path by min= px at each end;
- the prayer line: each prayer's name, Jumuʿah's too, in the seven languages, beside the widest time
  of each clock in each digit set, centred on twelve as the runtime centres it (one TextCircular of
  runs), min= px clear of the date window at three (nine is a plain index since option (d));
- with a slot filled, the Hijri date's ink stays out of the wearer's slots' boxes (face-centre.py
  SLOT_BOX), where a tap opens a slot, to within a pixel: its deepest descender is counted as if it
  stood at six, the curve's lowest point, so the bound is the most it could reach;
- every curved run of these lines, in each direction, holds no more glyphs past its characters
  than its PAD has joiners (face-moon.py PADDED, Ruling 37), and a run without PAD none: each
  prayer's name and each clock time, each Hijri date (right to left its month and its day), each
  countdown, as the watch's fonts shape them (HarfBuzz's hb-shape, Homebrew's harfbuzz): Noto Sans
  Arabic, Manrope, and Noto Sans Bengali UI, whose o-kar is two signs (যোহর, 4 characters and 5
  glyphs). The run fails without hb-shape, as it does without the Bengali font. The wearer's
  curved values are the watch's own text in any language, which no list holds, so they always end
  with PAD (face-moon.py moon_slot()).

    python3 -I tools/watchface/moon-fit.py                       # every form; fails if one does not fit
    python3 -I tools/watchface/moon-fit.py min=4                 # another margin (px, 3 by default)
    python3 -I tools/watchface/moon-fit.py forms=<file>          # the countdowns of another phone-forms.json
    python3 -I tools/watchface/moon-fit.py fonts=<dir>           # the watch's Bengali font from <dir>
    python3 -I tools/watchface/moon-fit.py show='29 Jumada al-Akhirah'   # one Hijri form, in degrees

Where the words come from. The Hijri months are the phone's own (shared HijriMonthNames.kt), and
the prayers' names are the phone's strings (shared composeResources, prayer_*), in capitals with
the language's own rules where Latin has them, as the watch makes its label. The countdown's
phrases are the feed's: tools/watchface/phone-forms.json (shared's FaceFormsTest writes it from the
phone's own code) holds every language's, and each tag's digit sets; its "countdown" and
"digitSets" per tag are read by default, another file's with forms=, and without the file the
committed samples' (watchcore/vectors: English and Arabic, in Western and Saudi Arabia's digits).

Fonts: Latin in the face's Manrope (SemiBold for the countdown and the Hijri date, ExtraBold for the
prayer line), Arabic and Urdu in its Noto Sans Arabic (SemiBold, Bold), and Bengali, which Manrope
lacks and the watch draws in its own Noto Sans Bengali UI, from emulator-5558's /system/fonts when
no fonts= is given. Without that font the run fails, as hijri-fit.py's does.

The calibration. A TextCircular lays its letters out along the circle it is given, and the ink's
width along that circle is what Pillow measures, so a form's angle is its ink width over a radius
that is the curve's own: R_LAYOUT below, measured on emulator-5558 from the forms in CALIBRATION,
whose angles the emulator drew (tools/watchface/hijri.sh, shoot.sh and the samples, measured on the
screenshots). The model is never narrower than the emulator on any of them, and the run fails if
it becomes so. A change to a curve's size, font, tracking or runs in face-moon.py makes the radii
stale: shoot CALIBRATION's forms again and write the radii they give.
"""
import importlib.util
import json
import math
import os
import subprocess
import sys
import tempfile
import unicodedata
import xml.etree.ElementTree as ET

import numpy as np
from PIL import ImageFont, features

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT_DIR = os.path.join(REPO, "watchface", "src", "main", "res", "font")
STRINGS = os.path.join(REPO, "shared", "src", "commonMain", "composeResources")
VECTORS = os.path.join(REPO, "watchcore", "vectors")
FORMS = os.path.join(REPO, "tools", "watchface", "phone-forms.json")
SERIAL = "emulator-5558"
SS = 8

_spec = importlib.util.spec_from_file_location("face_moon", os.path.join(REPO, "tools", "face-moon.py"))
M = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(M)
_spec = importlib.util.spec_from_file_location("hijri_fit", os.path.join(REPO, "tools", "watchface", "hijri-fit.py"))
H = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(H)

K = M.K
# The paths, as tools/face-moon.py writes them: (low, high) degrees; the Hijri date's on its circle with
# the slots empty ("hijri") and with one filled ("hijri_d"), each with its baseline, px.
HIJRI_PATHS = {"hijri": (tuple(sorted(M.HIJRI_ARC)), M.R_HIJRI), "hijri_d": (tuple(sorted(M.HIJRI_ARC_D)), M.R_HIJRI_D)}
HIJRI_PATH = HIJRI_PATHS["hijri"][0]
COUNT_PATH = tuple(sorted(M.COUNT_ARC))
# The radius each curve's letters are laid out along against Pillow's ink widths, px, by the script
# of its text: a tenth under the least that any CALIBRATION form gives, so that the model is never
# narrower than what the emulator drew. A script with no form of its own on a curve (Bengali's
# countdown and prayer line, which no sample holds yet) takes the curve's least, the widest reading.
# The forms the emulator drew follow, each with the angle its ink took there, degrees: the
# countdown and the prayer line from the samples at 02:30:30, 08:30:30, 14:30:30, 17:30:30 and
# 19:30:30 (the hands clear of the top), the Hijri dates from tools/watchface/hijri.sh at
# 14:15:15 (clear of the bottom), shot on emulator-5558 in the final review's fix round 2; and on
# the tighter circle (hijri_d) with the battery and the steps filled, at 10:15:15 (the hour hand
# clear of the bottom), shot in Task 11.
R_LAYOUT = {
    ('count', 'arab'): 130.8,  # 10 forms, 130.9 to 131.2
    ('count', 'latn'): 133.0,  # 5 forms, 133.1 to 134.9
    ('hijri', 'arab'): 150.6,  # 6 forms, 150.7 to 157.5
    ('hijri', 'beng'): 161.0,  # 1 form, 161.1 to 161.1
    ('hijri', 'latn'): 155.5,  # 5 forms, 155.6 to 157.4
    ('prayer', 'arab'): 156.9,  # 10 forms, 157.0 to 168.6
    ('prayer', 'latn'): 164.4,  # 5 forms, 164.5 to 167.5
    ('hijri_d', 'arab'): 123.3,  # 6 forms, 123.4 to 128.6
    ('hijri_d', 'beng'): 130.6,  # 1 form, 130.8 to 130.8
    ('hijri_d', 'latn'): 126.6,  # 5 forms, 126.7 to 128.9
}
CALIBRATION = [
    ('count', 'en', 'in 2 h 2 min', 61.09),
    ('count', 'en', 'in 4 h 2 min', 61.09),
    ('count', 'en', 'in 1 h 39 min', 66.88),
    ('count', 'en', 'in 1 h 33 min', 66.25),
    ('count', 'en', 'in 53 min', 48.35),
    ('count', 'ar', 'بعد 2 س 2 د', 63.67),
    ('count', 'ar', 'بعد 4 س 2 د', 63.67),
    ('count', 'ar', 'بعد 1 س 39 د', 70.43),
    ('count', 'ar', 'بعد 1 س 33 د', 70.43),
    ('count', 'ar', 'بعد 53 د', 41.99),
    ('count', 'ar', 'بعد ٢ س ٢ د', 63.67),
    ('count', 'ar', 'بعد ٤ س ٢ د', 63.67),
    ('count', 'ar', 'بعد ١ س ٣٩ د', 70.43),
    ('count', 'ar', 'بعد ١ س ٣٣ د', 70.43),
    ('count', 'ar', 'بعد ٥٣ د', 41.99),
    ('prayer', 'en', ('FAJR', '4:33'), 58.1),
    ('prayer', 'en', ('DHUHR', '12:33'), 76.54),
    ('prayer', 'en', ('ASR', '16:10'), 58.09),
    ('prayer', 'en', ('MAGHRIB', '19:04'), 91.09),
    ('prayer', 'en', ('ISHA', '20:24'), 65.93),
    ('prayer', 'ar', ('الفجر', '4:33'), 52.61),
    ('prayer', 'ar', ('الظهر', '12:33'), 59.61),
    ('prayer', 'ar', ('العصر', '4:10'), 56.38),
    ('prayer', 'ar', ('المغرب', '7:04'), 64.47),
    ('prayer', 'ar', ('العشاء', '8:24'), 61.93),
    ('prayer', 'ar', ('الفجر', '٤:٣٣'), 50.4),
    ('prayer', 'ar', ('الظهر', '١٢:٣٣'), 56.65),
    ('prayer', 'ar', ('العصر', '١٦:١٠'), 59.23),
    ('prayer', 'ar', ('المغرب', '١٩:٠٤'), 66.54),
    ('prayer', 'ar', ('العشاء', '٢٠:٢٤'), 64.62),
    ('hijri', 'en', '6 Jumada al-Ula', 66.76),
    ('hijri', 'en', '29 Jumada al-Akhirah', 90.02),
    ('hijri', 'en', '30 Joumada al-Akhira', 90.27),
    ('hijri', 'en', '30 Cemaziyelevvel', 77.63),
    ('hijri', 'bn', '৩০ জমাদিউল আউয়াল', 83.75),
    ('hijri', 'en', '2 Rajab', 30.65),
    ('hijri', 'ar', '٣٠ جمادى الأولى', 69.18),
    ('hijri', 'ar', '29 جمادى الآخرة', 69.88),
    ('hijri', 'ar', '30 جمادی الثانی', 69.24),
    ('hijri', 'ar', '6 جمادى الأولى', 65.02),
    ('hijri', 'ar', '١٢ جمادى الثانية', 70.79),
    ('hijri', 'ar', '١٠ ذو الحجّة', 49.24),
    ('hijri_d', 'en', '6 Jumada al-Ula', 80.9),
    ('hijri_d', 'en', '29 Jumada al-Akhirah', 110.1),
    ('hijri_d', 'en', '30 Joumada al-Akhira', 110.2),
    ('hijri_d', 'en', '30 Cemaziyelevvel', 94.3),
    ('hijri_d', 'bn', '৩০ জমাদিউল আউয়াল', 102.8),
    ('hijri_d', 'en', '2 Rajab', 37.3),
    ('hijri_d', 'ar', '٣٠ جمادى الأولى', 84.3),
    ('hijri_d', 'ar', '29 جمادى الآخرة', 85.4),
    ('hijri_d', 'ar', '30 جمادی الثانی', 84.6),
    ('hijri_d', 'ar', '6 جمادى الأولى', 79.3),
    ('hijri_d', 'ar', '١٢ جمادى الثانية', 86.6),
    ('hijri_d', 'ar', '١٠ ذو الحجّة', 60.3),
]

# faces.js's date window at three (units, about the face's centre).
WINDOW_BOX = {False: (126 - 100, 91 - 100, 44, 18), True: (120 - 100, 91 - 100, 52, 18)}

LANGS = {"ENGLISH": "values", "FRENCH": "values-fr", "TURKISH": "values-tr", "INDONESIAN": "values-id",
         "ARABIC": "values-ar", "URDU": "values-ur", "BENGALI": "values-bn"}
RTL_LANGS = ("ARABIC", "URDU")
TAG = {"ENGLISH": "en", "FRENCH": "fr", "TURKISH": "tr", "INDONESIAN": "id", "ARABIC": "ar", "URDU": "ur", "BENGALI": "bn"}


def font_files(sysdir):
    return {
        "manrope_semibold": (os.path.join(FONT_DIR, "manrope_semibold.ttf"), None),
        "manrope_extrabold": (os.path.join(FONT_DIR, "manrope_extrabold.ttf"), None),
        "noto_semibold": (os.path.join(FONT_DIR, "noto_sans_arabic_subset_v2_semibold.ttf"), None),
        "noto_bold": (os.path.join(FONT_DIR, "noto_sans_arabic_subset_v2_bold.ttf"), None),
        "bengali_semibold": (os.path.join(sysdir, "NotoSansBengaliUI-VF.ttf"), 600),
        "bengali_extrabold": (os.path.join(sysdir, "NotoSansBengaliUI-VF.ttf"), 800),
    }


_fonts = {}


def pil_font(path, weight, size):
    key = (path, weight, size)
    if key not in _fonts:
        f = ImageFont.truetype(path, round(size * SS), layout_engine=ImageFont.Layout.RAQM)
        if weight:
            f.set_variation_by_axes([weight])
        _fonts[key] = f
    return _fonts[key]


def clusters(text):
    """The letters a letterSpacing is added between: characters that are not marks on another."""
    return sum(1 for ch in text if unicodedata.category(ch) not in ("Mn", "Mc", "Me"))


def ink(text, face, size, spacing, rtl):
    """(ink width, advance width, ink top above the baseline, ink bottom below it), px, of text
    in face at size, with spacing em added between its letters."""
    path, weight = face
    f = pil_font(path, weight, size)
    kw = {"direction": "rtl"} if rtl else {}
    left, top, right, bottom = f.getbbox(text, anchor="ls", **kw)
    advance = f.getlength(text, **kw)
    extra = spacing * size * max(clusters(text) - 1, 0) * SS
    return (right - left + extra) / SS, (advance + extra + spacing * size * SS) / SS, -top / SS, bottom / SS


def degrees(px, curve):
    return math.degrees(px / R_LAYOUT[curve])


# ---- The three curves, as the face draws them

def is_bengali(text):
    return any("\u0980" <= ch <= "\u09ff" for ch in text)


def script(text, rtl):
    return "arab" if rtl else ("beng" if is_bengali(text) else "latn")


def radius(curve, scr):
    return R_LAYOUT.get((curve, scr)) or min(r for (c, _), r in R_LAYOUT.items() if c == curve)


def hijri_face(text, rtl, fonts, curve="hijri"):
    """The Hijri curve's font for text: (face, size, letterSpacing), as face-moon.py's slot 2."""
    if rtl:
        return fonts["noto_semibold"], 12 * K, 0.0
    return ((fonts["bengali_semibold"] if is_bengali(text) else fonts["manrope_semibold"]), 11 * K,
            M.ccw_spacing(HIJRI_PATHS[curve][1], 11 * K))


def count_face(text, rtl, fonts):
    """The countdown's font for text, as face-moon.py's slot 6."""
    if rtl:
        return fonts["noto_semibold"], 12 * K, 0.0
    return ((fonts["bengali_semibold"] if is_bengali(text) else fonts["manrope_semibold"]), 11 * K,
            M.track(M.R_COUNT, 11 * K, 0, M.TRACK_COUNT))


def along(text, curve, rtl, fonts):
    """(ink degrees, advance degrees) of a Hijri or countdown text along its curve."""
    if curve.startswith("hijri"):
        face, size, spacing = hijri_face(text, rtl, fonts, curve)
    else:
        face, size, spacing = count_face(text, rtl, fonts)
    w, adv, _, _ = ink(text, face, size, spacing, rtl)
    r = radius(curve, script(text, rtl))
    return math.degrees(w / r), math.degrees(adv / r)


def prayer_line(label, time, rtl, arab, fonts):
    """The prayer line's ink along its circle: (ink width, the ink's offset from the line's middle,
    ink top above the baseline, ink bottom below it), px, the line laid out as the face's runs are
    (tools/face-moon.py's slot 0): name, gap and time, or time, gap and name right to left, each run
    with its letterSpacing half before and half after each letter."""
    if rtl:
        lab = (fonts["noto_bold"], 13 * K, 0.0)
    else:
        lab = (fonts["bengali_extrabold"] if is_bengali(label) else fonts["manrope_extrabold"], 11.5 * K,
               M.track(M.R_PRAYER, 11.5 * K, 1.4 / 11.5, M.TRACK_LABEL))
    tim = (fonts["noto_bold"] if arab else (fonts["bengali_extrabold"] if is_bengali(time) else fonts["manrope_extrabold"]),
           13 * K, M.track(M.R_PRAYER, 13 * K, 0, M.TRACK_TIME))
    runs = [(time, tim, False), None, (label, lab, True)] if rtl else [(label, lab, False), None, (time, tim, False)]
    x, first, last, top, bottom = 0.0, None, None, 0.0, 0.0
    for r in runs:
        if r is None:
            x += M.PRAYER_GAP[(rtl, arab)]
            continue
        text, (face, size, spacing), shaped_rtl = r
        f = pil_font(face[0], face[1], size)
        kw = {"direction": "rtl"} if shaped_rtl else {}
        left, t, right, b = f.getbbox(text, anchor="ls", **kw)
        n = clusters(text)
        half = spacing * size / 2
        ink_l = x + half + left / SS
        ink_r = x + half + right / SS + spacing * size * max(n - 1, 0)
        first = ink_l if first is None else first
        last = ink_r
        top, bottom = max(top, -t / SS), max(bottom, b / SS)
        x += f.getlength(text, **kw) / SS + spacing * size * n
    return last - first, (first + last) / 2 - x / 2, top, bottom


def prayer_span(label, time, rtl, arab, fonts):
    """(first degree, last degree, inner radius, outer radius) of the prayer line's ink, px."""
    w, off, top, bottom = prayer_line(label, time, rtl, arab, fonts)
    r = radius("prayer", "arab" if rtl else ("beng" if is_bengali(label + time) else "latn"))
    mid = M.PRAYER_TURN[rtl] + math.degrees(off / r)
    half = math.degrees(w / 2 / r)
    return mid - half, mid + half, M.R_PRAYER - bottom, M.R_PRAYER + top


def clearance(span, rtl):
    """The least distance, px, from the prayer line's ink to the date window."""
    a0, a1, r0, r1 = span
    pts = []
    for a in np.linspace(a0, a1, 400):
        for r in (r0, r1):
            pts.append((r, a))
    for a in (a0, a1):
        for r in np.linspace(r0, r1, 40):
            pts.append((r, a))
    p = np.array([(r * math.sin(math.radians(a)), -r * math.cos(math.radians(a))) for r, a in pts])
    x, y, w, h = (v * K for v in WINDOW_BOX[rtl])
    ddx = np.maximum(np.maximum(x - p[:, 0], p[:, 0] - (x + w)), 0)
    ddy = np.maximum(np.maximum(y - p[:, 1], p[:, 1] - (y + h)), 0)
    return np.hypot(ddx, ddy).min(), "the date window"


# ---- The forms the phone sends

def strings(lang):
    path = os.path.join(STRINGS, LANGS[lang], "strings.xml")
    return {e.get("name"): e.text for e in ET.parse(path).getroot().iter("string")}


def label_of(name, lang):
    """The watch's label: the name in capitals with its language's own rules (ComplicationFields)."""
    if lang == "TURKISH":
        name = name.replace("i", "\u0130").replace("\u0131", "I")
    return name.upper()


def names(lang):
    s = strings(lang)
    return [label_of(s["prayer_" + p], lang) for p in ("fajr", "dhuhr", "asr", "maghrib", "isha", "jumuah")]


def digits(ds):
    return lambda v: "".join(H.DIGITS[ds][int(c)] for c in str(v))


def clock_times(ds, twelve):
    """Every clock time the phone writes, in a digit set: H:MM, on 24 or 12 hours."""
    dg = digits(ds)
    hours = range(1, 13) if twelve else range(0, 24)
    return [dg(h) + ":" + dg("%02d" % m) for h in hours for m in range(60)]


def countdown_locales(path):
    """[(tag, rtl, digit sets, the four phrases)] from a phone-forms.json, or from the committed
    samples when there is none."""
    if path and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            forms = json.load(f)["locales"]
        out = []
        for tag, loc in sorted(forms.items()):
            sets = [next(k for k, v in H.DIGITS.items() if v == ds) for ds in loc["digitSets"]]
            out.append((tag, loc["rtl"], sets, loc["countdown"]))
        return out, os.path.relpath(path, REPO)
    out = []
    for name in sorted(os.listdir(VECTORS)):
        if name.startswith("sample-feed-") and name.endswith(".json"):
            with open(os.path.join(VECTORS, name), encoding="utf-8") as f:
                ui = json.load(f)["ui"]
            ds = next(k for k, v in H.DIGITS.items() if v[0] == ui.get("digitZero", "0"))
            entry = (ui["language"], ui["rtl"], [ds], ui["countdown"])
            if entry not in out:
                out.append(entry)
    return out, "the committed samples (watchcore/vectors)"


def countdown_forms(phrases, ds):
    dg = digits(ds)
    yield phrases["underAMinute"]
    for m in range(1, 60):
        yield phrases["minutes"].replace("%1$s", dg(m))
    for h in range(1, 100):
        yield phrases["hours"].replace("%1$s", dg(h))
        for m in range(1, 60):
            yield phrases["hoursMinutes"].replace("%1$s", dg(h)).replace("%2$s", dg(m))


# ---- Every curved run's glyphs (Ruling 37)
#
# The runtime draws a curved run with drawTextOnPath, which places each glyph by the advance of the
# character with the glyph's index, so the glyphs a run holds past its characters are placed from
# past the advances (face-moon.py PAD). They are its visually last glyphs: a right-to-left run's
# logically first, a left-to-right run's logically last. So every run a Moon curve draws from the
# phone's words is counted, in the font the watch draws it in: the face's Noto Sans Arabic for its
# Arabic-script runs and for a time in Arabic-Indic digits, Manrope for Latin, and the watch's Noto
# Sans Bengali UI for Bengali, which a Manrope run falls back to (the o-kar of যোহর is two signs, 4
# characters and 5 glyphs). A run that carries PAD at the end those glyphs are drawn at
# (face-moon.py PADDED) may hold as many past its characters as PAD has joiners; a run without it,
# none. A run in two fonts (a Bengali month after a Western day) is shaped in the Bengali one, which
# draws what it lacks one glyph a character, as the fallback does.

def shaped_glyphs(face, texts):
    """How many glyphs face, a (path, variable weight) of font_files(), shapes each of texts into, by
    hb-shape (one call, one text a line)."""
    path, weight = face
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".txt", delete=False) as fh:
        fh.write("".join(t + "\n" for t in texts))
    try:
        args = ["hb-shape", "--no-glyph-names", "--no-positions", "--no-clusters", "--text-file=" + fh.name, path]
        if weight:
            args[1:1] = ["--variations=wght=%d" % weight]
        out = subprocess.run(args, check=True, capture_output=True, text=True).stdout.splitlines()
    finally:
        os.unlink(fh.name)
    if len(out) != len(texts):
        raise RuntimeError("hb-shape answered %d lines for %d texts" % (len(out), len(texts)))
    return [line.strip().strip("[]").count("|") + 1 for line in out]


def curved_runs(fonts, locales):
    """Every run of the phone's words a Moon curve draws, as face-moon.py writes it:
    {(the run, its direction, its face's key in font_files()): its texts}. The prayer's name in each
    language (slot 0) and its clock's every time in each digit set; the Hijri date (slot 2), left to
    right each day of each month as one run, right to left the month and the day as two; and each
    countdown (slot 6) of locales, every number in each digit set."""
    runs = {}

    def add(run, rtl, face, text):
        runs.setdefault((run, rtl, face), set()).add(text)

    def ltr_face(text, weight):
        return ("bengali_" if is_bengali(text) else "manrope_") + weight

    for lang in LANGS:
        rtl = lang in RTL_LANGS
        for label in names(lang):
            add("label", rtl, "noto_bold" if rtl else ltr_face(label, "extrabold"), label)
        for ds in H.LANG_DIGITS[lang]:
            arab = ds in ("arab", "arabext")
            for twelve in (False, True):
                for time in clock_times(ds, twelve):
                    add("time", rtl, "noto_bold" if arab else ltr_face(time, "extrabold"), time)
            for name in H.months()[lang]:
                if rtl:
                    add("hijri_month", True, "noto_semibold", name)
                for day in range(1, 31):
                    if rtl:
                        add("hijri_day", True, "noto_semibold", digits(ds)(day))
                    else:
                        text = digits(ds)(day) + " " + name
                        add("hijri", False, ltr_face(text, "semibold"), text)
    for tag, rtl, sets, phrases in locales:
        for ds in sets:
            for text in countdown_forms(phrases, ds):
                add("countdown", rtl, "noto_semibold" if rtl else ltr_face(text, "semibold"), text)
    return runs


def glyph_excess(fonts, locales):
    """([(run, text, glyphs past its characters, how many it may hold)] that hold too many, forms
    counted, {(run, direction): (most past, its text)}) for every curved run of curved_runs(); (None,
    0, None) without hb-shape."""
    try:
        subprocess.run(["hb-shape", "--version"], check=True, capture_output=True)
    except (OSError, subprocess.SubprocessError):
        return None, 0, None
    bad, count, worst = [], 0, {}
    for (run, rtl, face), texts in sorted(curved_runs(fonts, locales).items()):
        texts = sorted(texts)
        room = len(M.PAD) if (run, rtl) in M.PADDED else 0
        for text, n in zip(texts, shaped_glyphs(fonts[face], texts)):
            e = n - len(text)
            count += 1
            worst[(run, rtl)] = max(worst.get((run, rtl), (-99, "")), (e, text))
            if e > room:
                bad.append((run, rtl, text, e, room))
    return bad, count, worst


# ---- The run

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
    opts = dict(a.split("=", 1) for a in argv if "=" in a)
    if not features.check("raqm"):
        sys.exit("Pillow has no Raqm here, so Arabic and Bengali cannot be shaped")
    with tempfile.TemporaryDirectory(prefix="taqwa-moon-fit.") as scratch:
        sysdir = system_fonts(opts.get("fonts"), scratch)
        if not os.path.exists(os.path.join(sysdir, "NotoSansBengaliUI-VF.ttf")):
            print("no Bengali font (fonts=<dir> holding NotoSansBengaliUI-VF.ttf, or start emulator-5558):"
                  " Bengali's are among the widest forms, so the run cannot pass without it")
            return 1
        fonts = font_files(sysdir)
        if "show" in opts:
            text = opts["show"]
            rtl = any("\u0600" <= ch <= "\u06ff" for ch in text)
            a, adv = along(text, "hijri", rtl, fonts)
            print("%s: %.1f deg of ink, %.1f deg of advance, on the Hijri curve's %.0f-degree path" % (
                text, a, adv, HIJRI_PATH[1] - HIJRI_PATH[0]))
            return 0
        return run(opts, fonts, float(opts.get("min", 3)))


def run(opts, fonts, least):
    failures = []
    print("calibration (emulator-5558's angles, the model's, degrees):")
    worst_cal = 0.0
    for curve, lang, text, emu in CALIBRATION:
        rtl = lang in ("ar", "ur")
        if curve == "prayer":
            label, time = text
            a0, a1, _, _ = prayer_span(label, time, rtl, any("\u0660" <= ch <= "\u0669" for ch in time), fonts)
            model, shown = a1 - a0, "%s %s" % (label, time)
        else:
            model, shown = along(text, curve, rtl, fonts)[0], text
        worst_cal = max(worst_cal, emu - model)
        print("  %-9s %-24s emulator %6.2f  model %6.2f" % (curve, shown, emu, model))
    if worst_cal > 0.05:
        failures.append("the model draws a calibration form %.2f deg narrower than the emulator did" % worst_cal)

    # the Hijri date, on each of its circles
    for curve, label in (("hijri", "with the slots empty"), ("hijri_d", "with a slot filled")):
        (low, high), base = HIJRI_PATHS[curve]
        path = high - low
        room = path - 2 * math.degrees(least / base)
        worst, count, deepest = None, 0, (0.0, None)
        for lang, months in H.months().items():
            if lang not in LANGS:
                continue
            rtl = lang in RTL_LANGS
            for ds in H.LANG_DIGITS[lang]:
                dg = digits(ds)
                for name in months:
                    for day in range(1, 31):
                        text = dg(day) + " " + name
                        _, adv = along(text, curve, rtl, fonts)
                        face, size, spacing = hijri_face(text, rtl, fonts, curve)
                        deepest = max(deepest, (ink(text, face, size, spacing, rtl)[3], text))
                        count += 1
                        if worst is None or adv > worst[0]:
                            worst = (adv, text, lang)
                        if adv > room:
                            failures.append("Hijri %s (%s, %s): %.1f deg of a %.1f-degree room" % (text, lang, label, adv, room))
        print("the Hijri date %s: %d forms; the widest %s (%s) takes %.1f deg of the %.0f-degree path, %.1f px to spare at each end" % (
            label, count, worst[1], worst[2], worst[0], path, (path - worst[0]) / 2 * math.pi / 180 * base))
        if curve == "hijri_d":
            top = min(box[1] for box in M.face_centre.SLOT_BOX.values()) - M.C
            clear = top - (base + deepest[0])
            print("  its deepest descender (%s), if it stood at six, %.1f px above the slots' boxes" % (deepest[1], clear))
            if clear < -1:
                failures.append("Hijri %s with a slot filled: its ink reaches %.1f px into the slots' boxes" % (deepest[1], -clear))

    # the countdown
    path = COUNT_PATH[1] - COUNT_PATH[0]
    room = path - 2 * math.degrees(least / M.R_COUNT)
    locales, source = countdown_locales(opts.get("forms", FORMS))
    worst, count = None, 0
    for tag, rtl, sets, phrases in locales:
        for ds in sets:
            for text in countdown_forms(phrases, ds):
                _, adv = along(text, "count", rtl, fonts)
                count += 1
                if worst is None or adv > worst[0]:
                    worst = (adv, text, tag)
                if adv > room:
                    failures.append("countdown %s (%s): %.1f deg of a %.1f-degree room" % (text, tag, adv, room))
    print("the countdown, from %s (%s): %d forms; the widest %s (%s) takes %.1f deg of the %.0f-degree path, %.1f px to spare at each end" % (
        source, ", ".join(t for t, _, _, _ in locales), count, worst[1], worst[2], worst[0], path,
        (path - worst[0]) / 2 * math.pi / 180 * M.R_COUNT))

    # the prayer line
    worst, count = None, 0
    for lang in LANGS:
        rtl = lang in RTL_LANGS
        for ds in H.LANG_DIGITS[lang]:
            arab = ds in ("arab", "arabext")
            for twelve in (False, True):
                times = clock_times(ds, twelve)
                face = (fonts["noto_bold"] if arab else (fonts["bengali_extrabold"] if ds == "beng" else fonts["manrope_extrabold"]))
                f = pil_font(face[0], face[1], 13 * K)
                time = max(times, key=lambda t: f.getlength(t))
                for label in names(lang):
                    span = prayer_span(label, time, rtl, arab, fonts)
                    gap, what = clearance(span, rtl)
                    count += 1
                    if worst is None or gap < worst[0]:
                        worst = (gap, "%s %s" % (label, time), lang, what, span)
                    if gap < least:
                        failures.append("prayer %s %s (%s): %.1f px from %s" % (label, time, lang, gap, what))
    print("the prayer line: %d forms (each name beside its clock's widest time); the nearest %s (%s), %.1f..%.1f deg, %.1f px from %s" % (
        count, worst[1], worst[2], worst[4][0], worst[4][1], worst[0], worst[3]))

    # every curved run's glyphs past its characters, against its PAD (Ruling 37)
    excess, count, worst = glyph_excess(fonts, locales)
    if excess is None:
        failures.append("hb-shape (Homebrew's harfbuzz) is not here, so the curved runs' glyphs cannot be counted")
    else:
        print("the curved runs' glyphs: %d forms; the most past the characters, against the joiners each run's PAD has:" % count)
        for (run, rtl), (e, text) in sorted(worst.items()):
            print("  %-11s %s  %d (%s), against %d" % (run, "right to left" if rtl else "left to right", e, text,
                                                     len(M.PAD) if (run, rtl) in M.PADDED else 0))
        for run, rtl, text, e, room in excess:
            failures.append("%s (%s, %s) holds %d glyphs past its characters, and its run may hold %d: PAD at the run's"
                            " logical %s (face-moon.py PADDED)" % (
                                text, run, "right to left" if rtl else "left to right", e, room, "start" if rtl else "end"))

    if failures:
        print("%d do not fit (min=%g px):" % (len(failures), least))
        for line in failures[:40]:
            print("  " + line)
        return 1
    print("every form fits, %g px or more to spare" % least)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
