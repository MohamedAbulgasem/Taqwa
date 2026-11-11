"""Urdu's ۴ ۶ ۷ in the face's Noto Sans Arabic subsets (plan 2 Task 2, Steps 6 and 7), read with the
pinned fontTools (tools/watchface/subset-noto.sh's, ~/.cache/taqwa-watchface-tools/fonttools-4.55.0):

    <its python> -I fontcheck.py forms <upstream dir> [<subset dir>]
        each upstream weight's glyph for U+06F4, U+06F6 and U+06F7 in its cmap, the glyph the
        arab script's URD LangSys locl gives it, and in the Light that glyph's pnum form, by glyph
        name; then the same in the subsets of <subset dir> (noto_sans_arabic_subset_*.ttf), whose
        files carry no glyph names, so fontTools calls their glyphs glyphNNNNN

    <its python> -I fontcheck.py check <upstream dir> <old font dir> <new font dir>
        in each new weight, the cmap's glyphs for the three have the outlines and advances of
        upstream's locl_URD(...) glyphs (pnum(locl_URD(...)) in the Light); every other code point's
        glyph has the outline and advance it had in the old file of the same weight; and every
        table but the cmap holds the old file's bytes (head's checkSumAdjustment, the whole file's
        checksum, aside), so no glyph, layout feature or metric changed

Outlines are compared decomposed (components drawn in), point for point, with the advance width.
"""
import os
import sys

from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.ttLib import TTFont

URDU = (0x06F4, 0x06F6, 0x06F7)
WEIGHTS = ("Light", "SemiBold", "Bold")
OLD = "noto_sans_arabic_subset_%s.ttf"
NEW = "noto_sans_arabic_subset_v2_%s.ttf"


def single_subs(font, feature, script="arab", lang=None):
    """{glyph: glyph} of the single substitutions under feature, for one LangSys (lang) of script,
    or under every record of the feature when script is None."""
    gsub = font["GSUB"].table
    if script is None:
        indices = [i for i, r in enumerate(gsub.FeatureList.FeatureRecord) if r.FeatureTag == feature]
    else:
        rec = next(s for s in gsub.ScriptList.ScriptRecord if s.ScriptTag == script)
        ls = next(l.LangSys for l in rec.Script.LangSysRecord if l.LangSysTag == lang)
        indices = [i for i in ls.FeatureIndex if gsub.FeatureList.FeatureRecord[i].FeatureTag == feature]
    mapping = {}
    for i in indices:
        for lookup_index in gsub.FeatureList.FeatureRecord[i].Feature.LookupListIndex:
            lookup = gsub.LookupList.Lookup[lookup_index]
            for table in lookup.SubTable:
                if lookup.LookupType == 7:
                    table = table.ExtSubTable
                mapping.update(getattr(table, "mapping", None) or {})
    return mapping


def urdu_forms(font, light):
    """{code point: (cmap glyph, locl_URD glyph, pnum of it or None)} for the three."""
    cmap = font.getBestCmap()
    locl = single_subs(font, "locl", "arab", "URD ")
    pnum = single_subs(font, "pnum", None) if light else {}
    out = {}
    for cp in URDU:
        g = cmap[cp]
        u = locl.get(g)
        out[cp] = (g, u, pnum.get(u) if (light and u) else None)
    return out


def outline(font, glyph):
    pen = DecomposingRecordingPen(font.getGlyphSet())
    font.getGlyphSet()[glyph].draw(pen)
    return pen.value, font["hmtx"][glyph][0]


def forms(upstream, subsets=None):
    for where, name in ((upstream, "NotoSansArabic-%s.ttf"), (subsets, OLD)):
        if where is None:
            continue
        print(os.path.join(where, name % "<weight>"))
        for weight in WEIGHTS:
            font = TTFont(os.path.join(where, name % (weight if name != OLD else weight.lower())))
            for cp, (g, u, p) in urdu_forms(font, weight == "Light").items():
                print("  %-8s U+%04X %s  cmap %-10s  locl_URD %-13s%s" % (
                    weight, cp, chr(cp), g, u, ("  pnum %s" % p) if weight == "Light" else ""))


def check(upstream, old_dir, new_dir):
    bad = 0
    for weight in WEIGHTS:
        up = TTFont(os.path.join(upstream, "NotoSansArabic-%s.ttf" % weight))
        old = TTFont(os.path.join(old_dir, OLD % weight.lower()))
        new = TTFont(os.path.join(new_dir, NEW % weight.lower()))
        light = weight == "Light"
        ocmap, ncmap = old.getBestCmap(), new.getBestCmap()
        for cp, (g, u, p) in urdu_forms(up, light).items():
            want = p if light else u
            same = outline(new, ncmap[cp]) == outline(up, want)
            was = outline(old, ocmap[cp]) == outline(up, want)
            print("%-8s U+%04X: new %-11s = upstream %s(%s)%s: %s (the old file's %s: %s)" % (
                weight, cp, ncmap[cp], "pnum(locl_URD" if light else "locl_URD", g, ")" if light else "",
                "same outline and advance" if same else "DIFFERENT", ocmap[cp], "same" if was else "different"))
            bad += not same
        others = [cp for cp in ocmap if cp not in URDU]
        if set(ocmap) != set(ncmap):
            print("%-8s the cmaps cover different code points" % weight)
            bad += 1
        diff = [cp for cp in others if outline(new, ncmap[cp]) != outline(old, ocmap[cp])]
        print("%-8s the other %d code points: %d with another outline or advance than the old file's%s" % (
            weight, len(others), len(diff), (": " + ", ".join("U+%04X" % c for c in diff[:20])) if diff else ""))
        bad += len(diff)
        # the tables' bytes as the files hold them (the subsets carry no glyph names, so fontTools'
        # names for their glyphs follow the cmap and are not compared)
        raw_old = TTFont(os.path.join(old_dir, OLD % weight.lower())).reader
        raw_new = TTFont(os.path.join(new_dir, NEW % weight.lower())).reader
        tags = sorted(set(raw_old.keys()) | set(raw_new.keys()))

        def data(reader, tag):
            # head's checkSumAdjustment (bytes 8 to 11) is the whole file's checksum, which any
            # change to any table moves; the rest of head is compared
            raw = reader[tag]
            return raw[:8] + b"\0\0\0\0" + raw[12:] if tag == "head" else raw

        changed = [t for t in tags if t not in raw_old or t not in raw_new or data(raw_old, t) != data(raw_new, t)]
        print("%-8s tables whose bytes differ from the old file's (head's checkSumAdjustment aside): %s (%d glyphs, %d before)" % (
            weight, ", ".join(changed) or "none", len(new.getGlyphOrder()), len(old.getGlyphOrder())))
        bad += any(t != "cmap" for t in changed)
    print("PASS" if not bad else "FAIL: %d" % bad)
    return 1 if bad else 0


if __name__ == "__main__":
    if sys.argv[1:2] == ["forms"] and len(sys.argv) in (3, 4):
        forms(*sys.argv[2:])
    elif sys.argv[1:2] == ["check"] and len(sys.argv) == 5:
        sys.exit(check(*sys.argv[2:]))
    else:
        sys.exit(__doc__)
