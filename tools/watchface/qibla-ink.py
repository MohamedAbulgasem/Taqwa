#!/usr/bin/env python3
"""The Qibla page's glyph ink in the watch's fallback fonts, for the Wear app's host tests.

    python3 -I tools/watchface/qibla-ink.py            # rewrite the model
    python3 -I tools/watchface/qibla-ink.py --check    # fail if the committed model differs

Ruling 66 item 4: the Qibla detail is sized against the turning letters' glyph ink, measured as
glyph outlines. On the host, Java2D reads the app's own Manrope (`QiblaFitTest`); the letters and
figures Manrope lacks are drawn by the watch's system fonts, Noto Naskh Arabic (Bold: the compact
"UI" variant, and the elegant one emulator-5558 draws its Arabic in) and Noto Sans Bengali (its
UI and its elegant variant, at the weights the page asks: 600 for the figures and the letters
`WatchFonts.weight(700)` gives, 800 for north). Java2D cannot set a variable font's weight, so
those runs are shaped here once, by HarfBuzz (`hb-shape --show-extents`), in layoutlib's copies of
the system fonts, and written to the model the tests read:

    wearApp/src/test/resources/world/taqwa/app/wear/qibla/fallback-ink.json

Each run is its glyphs' ink boxes, [left, right, top, bottom] in ems about its pen's start and its
baseline (y down), in the order they are drawn from the left, and its advance. Digits and marks are
shaped one at a time (the tests set a number from them); the words and letters that join as
wholes: the dial's unit in Arabic script or Bengali (كم), from `WatchWords.kt`'s short forms, and
the dial's letters (ش ق ج غ, Urdu's مش and مغ, উ পূ দ প), from the phone's `qibla_cardinal_*`
strings. The check below holds that a number set from its digits is the number shaped whole, to a
thousandth of an em.

The captions the page draws over a detail in Arabic, Urdu and Bengali (Ruling 67 item 2: T6's gap
between the caption and the detail, held in all seven languages) are read from the phone's strings
and `WatchWords.kt`, as the feed carries them: "Facing qibla", the two titles a given-up compass
shows over its bearing and distance, and the tilt notice. They are set in ExtraBold, so in the Naskh
fonts and Bengali's 800, and each of their words is shaped whole. On the watch the space between
two of those words is Manrope's: U+0020 is not one of minikin's sticky marks, and minikin gives a
character to the first family that has it, Manrope. So a Naskh or Bengali run ends at every space,
each word is shaped alone, and the tests' HostInk sets a caption from the words this model holds
and Manrope's space (0.200 em) between them. A no-break space is otherwise: U+00A0 is one of
minikin's sticky marks and both fonts' cmaps have it, so after these fonts' digits or letters in
one run of one direction the watch draws the font's own (emulator-5558, 31 October), and the model
holds it beside the digits; the check below holds that a number grouped by one, and each dial word
on both sides of one, set from its parts is it shaped whole. The check below holds, besides, that each caption
set from its words and the font's own space (0.221 em in the Naskh fonts, 0.26 in Bengali's, which
the model keeps for this check alone) is the caption shaped whole in that font: no shaping
crosses a space, so the words would draw alike were the space the font's. When one of those words
changes, or a short form's unit or a dial letter (a native reviewer's form, say), run this again:
`--check` fails until it is. A format character a reviewer writes (a direction mark, the Arabic
letter mark) is left out of the words shaped, since Android draws it with no ink and no advance,
and `WatchWords.kt`'s escapes (\\u200F) are read as the characters they write.
"""
import json
import os
import re
import subprocess
import sys
import unicodedata

LAYOUTLIB = "/Applications/Android Studio.app/Contents/plugins/design-tools/resources/layoutlib/data/fonts"
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "wearApp", "src", "test", "resources", "world", "taqwa", "app",
                   "wear", "qibla", "fallback-ink.json")

ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
EXTENDED = "۰۱۲۳۴۵۶۷۸۹"
BENGALI = "০১২৩৪৫৬৭৮৯"

# Each font: its file, its variation, and the characters shaped one at a time; the runs shaped
# whole are its script's dial words (dial_words). The marks are the ones the phone groups with
# beside these digits (DigitGrouping: ٬ with Arabic-Indic and Extended digits, a comma with
# Bengali); the font's own space, which only the captions' check below reads (the watch draws
# Manrope's space between these words: see above); and the font's own no-break space, which the
# watch does draw: U+00A0 is one of minikin's sticky marks and both fonts' cmaps have it, so after
# these digits or letters in one run it stays in their font (0.221 em in the Naskh fonts, 0.26 in
# Bengali's; emulator-5558, 31 October). The narrow no-break space (U+202F) is not here: neither
# font's cmap has it (HarfBuzz would make one from the space, 0.110 and 0.130 em, which Android
# never draws), and the watch draws it in its next fallback (0.2485 em, Roboto's). build() holds
# every single to the font's cmap.
NBSP = "\u00a0"
# Minikin's sticky marks (HostInk.STICKY): after a font's letters or digits in one run, the watch draws
# one in that font when the font's cmap has it. Each font's model names those its cmap has
# ("sticky"), so the host's model errors on one it holds no run for rather than drawing Manrope's
# (Ruling 67's left gap).
STICKY = "!,-.:;?" + NBSP + "\u2010\u2011\u202f"
NASKH_SINGLES = ARABIC_INDIC + EXTENDED + "٬ " + NBSP
BENGALI_SINGLES = BENGALI + ", " + NBSP
FONTS = {
    "naskh-ui": ("NotoNaskhArabicUI-Bold.ttf", None, NASKH_SINGLES),
    "naskh-elegant": ("NotoNaskhArabic-Bold.ttf", None, NASKH_SINGLES),
    "bengali-ui-600": ("NotoSansBengaliUI-VF.ttf", "wght=600", BENGALI_SINGLES),
    "bengali-ui-800": ("NotoSansBengaliUI-VF.ttf", "wght=800", BENGALI_SINGLES),
    "bengali-elegant-600": ("NotoSansBengali-VF.ttf", "wght=600", BENGALI_SINGLES),
    "bengali-elegant-800": ("NotoSansBengali-VF.ttf", "wght=800", BENGALI_SINGLES),
}
# Numbers shaped whole and set from their digits, which must agree; a reviewer may group one with a
# no-break space, which the bidirectional algorithm keeps in the digits' run (a common separator
# between two numbers of one type).
CHECKS = {"naskh": ["٢٠٬٠٠٠", "٦٬٦٠٠", "۱۹٬۰۰۰", "۷٬۹۰۰", "٣٥٩", "٦" + NBSP + "٦٠٠", "۷" + NBSP + "۹۰۰"],
          "bengali": ["২০,০০০", "৬,৬০০", "৮,৬০০", "৩৫৯", "৬" + NBSP + "৬০০"]}

# Each script's languages, as the phone's string folders and WatchWords.SCREENS name them; the
# dial's letters by key; and the captions drawn over a detail (T6), as the feed carries them: the
# phone's strings by key, and the tilt notice from WatchWords.SCREENS. A script's captions go to its
# ExtraBold fonts.
ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
STRINGS = os.path.join(ROOT, "shared", "src", "commonMain", "composeResources")
WATCH_WORDS = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "world", "taqwa", "app", "watchfeed", "WatchWords.kt")
CAPTION_KEYS = ["qibla_facing_qibla", "qibla_best_effort_title", "qibla_no_sensor_title"]
LANGUAGES = {"naskh": [("values-ar", "ARABIC"), ("values-ur", "URDU")], "bengali": [("values-bn", "BENGALI")]}
CARDINAL_KEYS = ["qibla_cardinal_north", "qibla_cardinal_east", "qibla_cardinal_south", "qibla_cardinal_west"]
SHORT_FORMS = ["distanceOnDial", "qiblaDetailOnDial"]
PLACE = re.compile(r"%\d+\\?\$[sd]")
CAPTION_FONTS = ["naskh-ui", "naskh-elegant", "bengali-ui-800", "bengali-elegant-800"]


def phone_string(folder, key):
    with open(os.path.join(STRINGS, folder, "strings.xml"), encoding="utf-8") as f:
        found = re.search(r'<string name="%s">(.*?)</string>' % key, f.read())
    if not found:
        sys.exit("qibla-ink.py: %s has no %s" % (folder, key))
    return found.group(1).replace("\\'", "'")


def watch_word(language, field):
    with open(WATCH_WORDS, encoding="utf-8") as f:
        text = f.read()
    screens = text[text.index("private val SCREENS"):]
    start = screens.index("UiLanguage.%s to WatchScreenWords(" % language)
    end = screens.find("UiLanguage.", start + 1)
    found = re.search(r'%s = "([^"]*)"' % field, screens[start:end if end > 0 else len(screens)])
    if not found:
        sys.exit("qibla-ink.py: WatchWords.kt has no %s for %s" % (field, language))
    return re.sub(r"\\u([0-9A-Fa-f]{4})", lambda m: chr(int(m.group(1), 16)), found.group(1))


def inked(text):
    """[text] without its format characters (a direction mark, the Arabic letter mark), which Android draws with no ink and no advance."""
    return "".join(c for c in text if unicodedata.category(c) != "Cf")


def captions(script):
    """The script's captions drawn over a detail, in the order of its languages and keys."""
    out = []
    for folder, language in LANGUAGES[script]:
        out += [inked(phone_string(folder, key)) for key in CAPTION_KEYS] + [inked(watch_word(language, "holdLevel"))]
    return out


def in_script(word, script):
    if script == "naskh":
        return any(is_arabic_letter(c) for c in word)
    return any(0x0980 <= ord(c) <= 0x09FF for c in word)


def dial_words(script):
    """The script's words the dial draws whole: each short form's unit in that script, then the dial's letters."""
    out = []
    for folder, language in LANGUAGES[script]:
        units = [inked(w) for field in SHORT_FORMS for w in PLACE.sub(" ", watch_word(language, field)).split() if in_script(w, script)]
        for word in units + [phone_string(folder, key) for key in CARDINAL_KEYS]:
            if word not in out:
                out.append(word)
    return out


def script_of(name):
    return "naskh" if name.startswith("naskh") else "bengali"


def set_from(runs, phrase, space=" "):
    """[phrase] set from its words and the font's own [space] as the model holds them, drawn from the left.

    With U+0020 the check's alone: the watch, and HostInk, draw Manrope's space between the words.
    With the no-break space it is how HostInk sets a run that holds one (its Modelled.shape)."""
    pieces = []
    for i, word in enumerate(phrase.split(space)):
        pieces += ([space] if i else []) + [word]
    if any(is_arabic_letter(c) for c in phrase):
        pieces.reverse()
    pen, glyphs = 0.0, []
    for piece in pieces:
        run = runs[piece]
        glyphs += [[g[0] + pen, g[1] + pen, g[2], g[3]] for g in run["glyphs"]]
        pen += run["advance"]
    return pen, glyphs


def is_arabic_letter(c):
    o = ord(c)
    return (0x0600 <= o <= 0x06FF) and not (0x0660 <= o <= 0x066C or 0x06F0 <= o <= 0x06F9)


def cmap(path):
    """The characters [path]'s cmap maps, which is what minikin reads a font's coverage from."""
    out = subprocess.check_output(["hb-info", "--list-unicodes", path], text=True)
    return {int(line.split()[0][2:], 16) for line in out.splitlines() if line.startswith("U+")}


def shape(path, variation, text):
    rtl = any(is_arabic_letter(c) for c in text)
    args = ["hb-shape", path, "--font-size=1000", "--output-format=json", "--show-extents",
            "--direction=" + ("rtl" if rtl else "ltr"), "--unicodes=" + ",".join("U+%04X" % ord(c) for c in text)]
    if variation:
        args.append("--variations=" + variation)
    pen, glyphs = 0, []
    for g in json.loads(subprocess.check_output(args)):
        if g["g"] in (".notdef", "gid0") or g["g"] == 0:
            sys.exit("qibla-ink.py: %s has no glyph for %r" % (os.path.basename(path), text))
        if g["w"] and g["h"]:
            left = pen + g["dx"] + g["xb"]
            top = g["dy"] + g["yb"]
            glyphs.append([round(left / 1000, 4), round((left + g["w"]) / 1000, 4),
                           round(-top / 1000, 4), round(-(top + g["h"]) / 1000, 4)])
        pen += g["ax"]
    return {"advance": round(pen / 1000, 4), "glyphs": glyphs}


def build():
    model = {"source": "tools/watchface/qibla-ink.py: hb-shape on layoutlib's system fonts, ems about the pen and the baseline, y down",
             "fonts": {}}
    worst = 0.0
    for name, (file, variation, singles) in FONTS.items():
        wholes = dial_words(script_of(name))
        path = os.path.join(LAYOUTLIB, file)
        if not os.path.exists(path):
            sys.exit("qibla-ink.py: no %s (Android Studio's layoutlib)" % path)
        missing = [c for c in singles if ord(c) not in cmap(path)]
        if missing:
            sys.exit("qibla-ink.py: %s's cmap has no %s, so the watch never draws it in that font" % (file, ", ".join("U+%04X" % ord(c) for c in missing)))
        runs = {c: shape(path, variation, c) for c in singles}
        runs.update({w: shape(path, variation, w) for w in wholes})
        phrases = captions(script_of(name)) if name in CAPTION_FONTS else []
        runs.update({w: shape(path, variation, w) for phrase in phrases for w in phrase.split(" ")})
        model["fonts"][name] = {"file": file, "variation": variation, "runs": runs,
                                "sticky": "".join(c for c in STICKY if ord(c) in cmap(path))}
        # The captions, by U+0020; and each dial word on both sides of a no-break space, by it.
        for phrase, space in [(p, " ") for p in phrases] + [(w + NBSP + w, NBSP) for w in wholes]:
            whole = shape(path, variation, phrase)
            pen, glyphs = set_from(runs, phrase, space)
            if len(glyphs) != len(whole["glyphs"]):
                sys.exit("qibla-ink.py: %s sets %s in %d glyphs, shaped whole %d" % (name, phrase, len(glyphs), len(whole["glyphs"])))
            worst = max(worst, abs(pen - whole["advance"]))
            for a, b in zip(glyphs, whole["glyphs"]):
                worst = max(worst, max(abs(x - y) for x, y in zip(a, b)))
        for number in CHECKS[script_of(name)]:
            whole = shape(path, variation, number)
            pen, glyphs = 0.0, []
            for c in number:
                run = runs[c]
                glyphs += [[g[0] + pen, g[1] + pen, g[2], g[3]] for g in run["glyphs"]]
                pen += run["advance"]
            worst = max(worst, abs(pen - whole["advance"]))
            for a, b in zip(glyphs, whole["glyphs"]):
                worst = max(worst, max(abs(x - y) for x, y in zip(a, b)))
            if len(glyphs) != len(whole["glyphs"]):
                sys.exit("qibla-ink.py: %s sets %s in %d glyphs, shaped whole %d" % (name, number, len(glyphs), len(whole["glyphs"])))
    if worst > 0.001:
        sys.exit("qibla-ink.py: a number set from its digits, or a caption or a dial word from its words and spaces, is %.4f em from it shaped whole" % worst)
    return model, worst


def main():
    model, worst = build()
    text = json.dumps(model, ensure_ascii=False, indent=1, sort_keys=True) + "\n"
    if "--check" in sys.argv[1:]:
        with open(OUT, encoding="utf-8") as f:
            if f.read() != text:
                sys.exit("qibla-ink.py: %s is not what the fonts give; run it without --check" % OUT)
        print("qibla-ink.py: the model is the fonts' (digits, captions' words and no-break spaces set one at a time within %.4f em)" % worst)
        return
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(text)
    print("qibla-ink.py: wrote %s (digits, captions' words and no-break spaces set one at a time within %.4f em)" % (os.path.relpath(OUT), worst))


if __name__ == "__main__":
    main()
