#!/usr/bin/env python3
"""Hold the Quran as the app draws it against an outside text, word by word, and print every
difference grouped by class. Standard library only.

    python3 tools/quran-drawn-check.py --kf PATH/hafsData_v2-0.json [--dump] [--out DIR]

The drawn text comes from the app's own Kotlin code, never from a re-implementation here:
`DrawnQuranDumpTest` (:shared androidHostTest) writes shared/build/drawn-quran/ayahs.tsv and
words.tsv through QuranRepository, the Mushaf's line and word context and HafsSpelling.forDrawing.
`--dump` runs that test first (./gradlew :shared:testAndroidHostTest --tests '*DrawnQuranDumpTest*').

The outside text is the King Fahd Complex's "KFGQPC Hafs Uthmanic Data" v2.0 (hafsData_v2-0.json,
2022), given by path. It is NOT in this repository and must not be committed or bundled: permission
to use it is pending. It is only ever read from where it is given.

Words are compared the way the print-match study did (docs/superpowers/specs, 2026-11-07, on the
quran-print-match branch): a pause sign joined straight to its word as the Complex writes it, the hizb
(۞) and sajdah (۩) signs left out on both sides (where they sit is tokenisation; the app draws them
apart either way), and words paired one to one, or by letter skeleton where the two texts split
words differently.

A differing word is put in the smallest set of known classes that explains it, each class a named
equivalence tried on both words:

  pause sign      the 1405 print's pause sign (Tanzil, kept) against the current edition's
  yeh             ى where the Complex writes ي (R3, not applied: invisible as the app lays words out)
  lam-alef        lam + ـَٔا against lam + أٓ (R6)
  sajdah overline the Complex's U+06E4 on the words of a sajdah (HafsSpelling's table)
  hamza seat      ئ / ؤ against ي / و with a hamza below (U+0655) (HafsSpelling's table)
  item            one of the single words the 1405 print decided: 2:72, 12:11, 17:7, 52:37, 2:97

Since the marks hotfix the app draws R6 and the table's words as the Complex writes them, so the only
differences left are the 1405 print's own pause signs and yeh, the three places the two texts divide
words differently (15:7, 27:20, 36:22), and 17:7, which the face draws with the print's signs from
Tanzil's spelling. A word in any other class, or another single word, is printed as "not expected"
and fails the run; anything no class explains is printed in full as "unexplained" and fails it too:
the conversion would then have turned a word wrongly, or missed one.
"""
from __future__ import annotations

import argparse
import collections
import difflib
import itertools
import json
import os
import re
import subprocess
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DRAWN = ROOT / "shared" / "build" / "drawn-quran"

SIGNS = "۞۩"
PAUSE = set(chr(c) for c in range(0x06D6, 0x06DD))
ITEMS = {(2, 72), (12, 11), (17, 7), (52, 37), (2, 97)}
# What may still differ once the drawing is the 1405 print's: its pause signs and yeh, and 17:7.
ALLOWED_CLASSES = {"pause sign", "yeh"}
ALLOWED_ITEMS = {(17, 7)}
TATWEEL, ALEF = "ـ", "ٰ"


def cps(w: str) -> str:
    return " ".join(f"{ord(ch):04X}" for ch in w)


def is_mark(ch: str) -> bool:
    o = ord(ch)
    return ch == ALEF or 0x064B <= o <= 0x065F or 0x06D6 <= o <= 0x06ED


def marks_only(tok: str) -> bool:
    return all(is_mark(ch) or unicodedata.category(ch) in ("So", "Mn") for ch in tok)


def words(text: str) -> list[str]:
    """Words with a free-standing pause sign joined to the word before it; ۞ and ۩ left out."""
    out: list[str] = []
    for tok in text.replace(" ", " ").split(" "):
        tok = tok.strip(SIGNS)
        if not tok:
            continue
        if out and marks_only(tok):
            out[-1] += tok
        else:
            out.append(tok)
    return out


def skeleton(w: str) -> str:
    s = "".join(ch for ch in w if unicodedata.category(ch) == "Lo" and ch != TATWEEL)
    return s.translate(str.maketrans("ٱأإآىۥۦئؤ", "اااايوييو"))


def align(a: list[str], b: list[str]):
    if len(a) == len(b):
        return list(zip(range(len(a)), range(len(b))))
    sm = difflib.SequenceMatcher(None, [skeleton(x) for x in a], [skeleton(x) for x in b], autojunk=False)
    pairs = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal" or (tag == "replace" and i2 - i1 == j2 - j1):
            pairs += list(zip(range(i1, i2), range(j1, j2)))
        else:
            pairs += [(i, None) for i in range(i1, i2)] + [(None, j) for j in range(j1, j2)]
    return pairs


def load_drawn(path: Path) -> dict[tuple[int, int], tuple[str, str]]:
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        s, a, stored, drawn = line.split("\t")
        out[(int(s), int(a))] = (stored, drawn)
    assert len(out) == 6236, f"{path}: {len(out)} ayahs"
    return out


def load_kf(path: str) -> dict[tuple[int, int], str]:
    """hafsData_v2-0.json: the ayah number is one presentation-form code point after a space."""
    data = json.load(open(path, encoding="utf-8"))
    out = {}
    for v in data:
        out[(v["sura_no"], v["aya_no"])] = re.sub(r"[ \u00a0]+[\ufb50-\ufdff\u0660-\u0669]+$", "", v["aya_text"]).strip()
    assert len(out) == 6236, f"{path}: {len(out)} ayahs"
    return out


# --- the named equivalences, each applied to (drawn, kf) -------------------------------------

def eq_pause(d: str, k: str):
    return "".join(c for c in d if c not in PAUSE), "".join(c for c in k if c not in PAUSE)


def eq_yeh(d: str, k: str):
    return d.replace("ى", "ي"), k.replace("ى", "ي")


def eq_lam_alef(d: str, k: str):
    out, i = "", 0
    while i < len(d):
        for seq in ("\u0640\u064e\u0654\u0627", "\u0640\u0654\u064e\u0627"):
            if d.startswith(seq, i):
                j = len(out) - 1
                while j >= 0 and is_mark(out[j]):
                    j -= 1
                if j >= 0 and out[j] == "ل":
                    out += "أٓ"
                    i += len(seq)
                    break
        else:
            out += d[i]
            i += 1
    return out, k


def eq_sajdah(d: str, k: str):
    return d, k.replace("ۤ", "")


def eq_hamza_seat(d: str, k: str):
    return d, k.replace("يٕ", "ئ").replace("وٕ", "ؤ")


CLASSES = {
    "pause sign": eq_pause,
    "yeh": eq_yeh,
    "lam-alef": eq_lam_alef,
    "sajdah overline": eq_sajdah,
    "hamza seat": eq_hamza_seat,
}


def explain(d: str, k: str) -> tuple[str, ...] | None:
    names = list(CLASSES)
    for size in range(1, len(names) + 1):
        for combo in itertools.combinations(names, size):
            x, y = d, k
            for n in combo:
                x, y = CLASSES[n](x, y)
            if unicodedata.normalize("NFC", x) == unicodedata.normalize("NFC", y):
                return combo
    return None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--kf", required=True, help="the Complex's hafsData_v2-0.json, read in place, never copied")
    ap.add_argument("--drawn", default=str(DRAWN), help="the directory DrawnQuranDumpTest writes")
    ap.add_argument("--dump", action="store_true", help="run DrawnQuranDumpTest first")
    ap.add_argument("--out", help="write each class's words to DIR/<class>.tsv")
    args = ap.parse_args()
    if args.dump:
        subprocess.run(["./gradlew", ":shared:testAndroidHostTest", "--tests", "*DrawnQuranDumpTest*", "--rerun"],
                       cwd=ROOT, check=True)
    drawn = load_drawn(Path(args.drawn) / "ayahs.tsv")
    kf = load_kf(args.kf)

    pairs = 0
    identical = 0
    signs = 0
    joins = []
    by_class: dict[tuple[str, ...], list[tuple[str, str, str]]] = collections.defaultdict(list)
    items: list[tuple[str, str, str]] = []
    unexplained: list[tuple[str, str, str]] = []
    for key in sorted(drawn):
        stored, text = drawn[key]
        signs += sum(text.count(s) for s in SIGNS)
        dw, kw = words(text), words(kf[key])
        for i, j in align(dw, kw):
            ref = f"{key[0]}:{key[1]}"
            if i is None or j is None:
                joins.append((ref, dw[i] if i is not None else "", kw[j] if j is not None else ""))
                continue
            pairs += 1
            d, k = dw[i], kw[j]
            if d == k:
                identical += 1
                continue
            why = explain(d, k)
            if why is not None:
                by_class[why].append((f"{ref}:{i + 1}", d, k))
            elif key in ITEMS:
                items.append((f"{ref}:{i + 1}", d, k))
            else:
                unexplained.append((f"{ref}:{i + 1}", d, k))

    print(f"aligned words {pairs}: byte-identical {identical}, differ {pairs - identical}")
    print(f"hizb and sajdah signs left out of the comparison: {signs}")
    print(f"words split differently (unaligned tokens): {len(joins)}")
    for ref, d, k in joins:
        print(f"  {ref}  drawn={d!r}  kf={k!r}")
    for combo, rows in sorted(by_class.items(), key=lambda kv: (-len(kv[1]), kv[0])):
        print(f"{' + '.join(combo)}: {len(rows)} words")
        refs = " ".join(r for r, _, _ in rows)
        print(f"  {refs[:600]}{' …' if len(refs) > 600 else ''}")
    print(f"single words the print decides: {len(items)}")
    for ref, d, k in items:
        print(f"  {ref}  drawn {cps(d)}\n  {' ' * len(ref)}  kf    {cps(k)}")
    not_expected = [(" + ".join(c), r) for c, rows in by_class.items() if not set(c) <= ALLOWED_CLASSES for r, _, _ in rows]
    not_expected += [("item", r) for r, _, _ in items if tuple(int(x) for x in r.split(":")[:2]) not in ALLOWED_ITEMS]
    print(f"not expected (a class the drawing converts, or another single word): {len(not_expected)}")
    for why, ref in not_expected:
        print(f"  {ref}  {why}")
    print(f"unexplained: {len(unexplained)}")
    for ref, d, k in unexplained:
        print(f"  {ref}  {d}  |  {k}\n    drawn {cps(d)}\n    kf    {cps(k)}")
    if args.out:
        os.makedirs(args.out, exist_ok=True)
        for combo, rows in by_class.items():
            with open(os.path.join(args.out, "_".join(c.replace(" ", "-") for c in combo) + ".tsv"), "w", encoding="utf-8") as f:
                for ref, d, k in rows:
                    f.write(f"{ref}\t{d}\t{k}\t{cps(d)}\t{cps(k)}\n")
    sys.exit(1 if unexplained or not_expected else 0)


if __name__ == "__main__":
    main()
