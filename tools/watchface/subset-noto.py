"""Subsets the three Noto Sans Arabic weights the face bundles to the characters the face can be sent,
and writes them to watchface/src/main/res/font (noto_sans_arabic_subset_v2_light.ttf and so on). Run it
through subset-noto.sh, which gives it fontTools at the version pinned there:

    tools/watchface/subset-noto.sh <directory of get-noto.sh's files>

The face draws Noto Sans Arabic for the right-to-left phones' texts (Arabic and Urdu) and for
Arabic-Indic digits, so it keeps Basic Latin and Latin-1 (a Latin word or marker inside an Arabic
text), the Arabic block and Arabic Supplement (Urdu's letters, both digit sets, the Arabic comma and
percent sign), General Punctuation (the joiners, the direction marks, quotes and dashes) and the
dotted circle a lone mark is shaped on. The contextual forms, ligatures and marks are glyphs the
font's own layout tables reach from those characters, and fontTools keeps all of them with every
layout feature; what goes is the presentation-form code points (no phone writes them), Arabic
Extended-A and the glyphs only they reach, about two thirds of each file.

Every weight draws Urdu's own ۴ ۶ ۷ (plan 2 Task 2, the Lead's call 2). Noto's default forms of
U+06F4, U+06F6 and U+06F7 are the Persian ones, and its Urdu forms are reached only through the
locl feature of the arab script's URD language system, which the runtime applies only when the
watch itself is set to Urdu: the face cannot name a language. So an Urdu phone's digits would come
out Persian on every other watch, beside the phone's Urdu ones. The cmap maps the three straight
to the glyphs that locl gives them (the Urdu ۴ and ۷, and for ۶ the glyph of Arabic-Indic ٦),
read from the upstream font's own lookup, never by glyph name, and the script stops, naming the
code point and the weight, if one has none. The glyphs they mapped to before are kept, so the
subset holds the same glyphs and the same layout tables as before; only the cmap changes. The cost:
a Persian phone's ۴ ۶ ۷ would be drawn in Urdu forms too, but no phone sends Persian digits (the
feed writes them in Western digits, the Lead's call 3).

The Light weight is the clock's alone, and it gets one more change: its Arabic-Indic and extended
Arabic-Indic digits map to the font's own proportional figures (its pnum forms), which faces.js's
clock asks for (its hour is proportional), as Manrope's figures are by default; Urdu's three to the
pnum forms of their Urdu glyphs, and the script stops if one has none. The runtime takes no font
features, so the clock could not ask for them.

The output is the same bytes for the same input (no timestamp is written anew). The files carry a
name of their own, not the upstream fonts' noto_sans_arabic_*: the watch's runtime keeps a font it
has loaded by its resource name, through a new APK and even an uninstall (found on the Wear OS 6
emulator: the same new file under the old name drew the old glyphs, under a new name the new ones),
so a font whose file changes takes a new name. The _v2_ files are the first with Urdu's forms; the
next change to them takes _v3_.
"""
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "watchface", "src", "main", "res", "font")
WEIGHTS = {"Light": "noto_sans_arabic_subset_v2_light.ttf", "SemiBold": "noto_sans_arabic_subset_v2_semibold.ttf", "Bold": "noto_sans_arabic_subset_v2_bold.ttf"}
RANGES = [(0x20, 0x7E), (0xA0, 0xFF), (0x600, 0x6FF), (0x750, 0x77F), (0x2000, 0x206F), (0x25CC, 0x25CC)]
URDU_DIGITS = (0x06F4, 0x06F6, 0x06F7)   # ۴ ۶ ۷, whose Urdu forms differ from the default (Persian) ones


def single_substitutions(gsub, indices):
    """{glyph: glyph} of the single substitutions in the features at indices of the GSUB table."""
    forms = {}
    for i in indices:
        for index in gsub.FeatureList.FeatureRecord[i].Feature.LookupListIndex:
            lookup = gsub.LookupList.Lookup[index]
            for table in lookup.SubTable:
                if lookup.LookupType == 7:
                    table = table.ExtSubTable
                forms.update(getattr(table, "mapping", None) or {})
    return forms


def urdu_digits(font, weight):
    """Maps ۴ ۶ ۷ to the glyphs the arab script's URD locl gives them, in every Unicode cmap of the
    font, and returns the glyphs they mapped to before (which the subset keeps). Stops, naming the
    code point and the weight, if one has no URD locl form."""
    gsub = font["GSUB"].table
    arab = next((r.Script for r in gsub.ScriptList.ScriptRecord if r.ScriptTag == "arab"), None)
    urd = next((r.LangSys for r in (arab.LangSysRecord if arab else []) if r.LangSysTag == "URD "), None)
    if urd is None:
        sys.exit("%s: the font has no URD language system in its arab script" % weight)
    forms = single_substitutions(gsub, [i for i in urd.FeatureIndex if gsub.FeatureList.FeatureRecord[i].FeatureTag == "locl"])
    before = dict(font.getBestCmap())   # a copy: the best cmap is one of the tables changed below
    for code in URDU_DIGITS:
        if forms.get(before.get(code)) is None:
            sys.exit("%s: U+%04X has no Urdu form (the arab script's URD locl gives its glyph %s nothing)" % (weight, code, before.get(code)))
    for table in font["cmap"].tables:
        if table.isUnicode():
            for code in URDU_DIGITS:
                if code in table.cmap:
                    table.cmap[code] = forms[before[code]]
    return [before[code] for code in URDU_DIGITS]


def proportional_digits(font):
    """Maps each digit that has a pnum form to it, in every Unicode cmap of the font, and Urdu's
    three to the pnum forms of the Urdu glyphs urdu_digits() gave them, stopping if one has none."""
    gsub = font["GSUB"].table
    forms = single_substitutions(gsub, [i for i, r in enumerate(gsub.FeatureList.FeatureRecord) if r.FeatureTag == "pnum"])
    cmap = font.getBestCmap()
    for code in URDU_DIGITS:
        if cmap[code] not in forms:
            sys.exit("Light: U+%04X's Urdu form %s has no pnum form" % (code, cmap[code]))
    changed = 0
    for table in font["cmap"].tables:
        if not table.isUnicode():
            continue
        for code, glyph in list(table.cmap.items()):
            if (0x660 <= code <= 0x669 or 0x6F0 <= code <= 0x6F9) and glyph in forms:
                table.cmap[code] = forms[glyph]
                changed += 1
    return changed


def main(argv):
    if len(argv) != 1 or not os.path.isdir(argv[0]):
        sys.exit(__doc__)
    unicodes = [c for lo, hi in RANGES for c in range(lo, hi + 1)]
    for weight, name in WEIGHTS.items():
        source = os.path.join(argv[0], "NotoSansArabic-%s.ttf" % weight)
        font = TTFont(source, recalcTimestamp=False)
        # before the subset, so that the forms are reached from the cmap and kept
        kept = urdu_digits(font, weight)
        note = ", ۴ ۶ ۷ in Urdu's forms"
        if weight == "Light":
            note += ", %d digits proportional" % proportional_digits(font)
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.notdef_outline = True
        options.recalc_timestamp = False
        subsetter = subset.Subsetter(options)
        # the glyphs Urdu's three mapped to before stay, so that the glyphs and the layout tables
        # are those of a subset without the change
        subsetter.populate(unicodes=unicodes, glyphs=kept)
        subsetter.subset(font)
        target = os.path.join(OUT, name)
        font.save(target)
        print("%s: %d -> %d bytes, %d glyphs%s" % (name, os.path.getsize(source), os.path.getsize(target), len(font.getGlyphOrder()), note))


if __name__ == "__main__":
    main(sys.argv[1:])
