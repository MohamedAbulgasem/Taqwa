#!/usr/bin/env python3
"""Build shared/src/commonMain/composeResources/files/quran.db from Tanzil, Tanzil translations
and the Madinah Mushaf page layout. Standard library only. Run from anywhere:

    python3 tools/build-quran-db.py            # download (cached), build, verify
    python3 tools/build-quran-db.py --verify   # verify an existing quran.db only

Sources and licences are listed in docs/superpowers/specs/2026-09-07-taqwa-quran-reader-design.md §3.1.
"""
from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / "cache"
OUT = ROOT / "shared" / "src" / "commonMain" / "composeResources" / "files" / "quran.db"
USER_VERSION = 1

TANZIL_TEXT = "https://tanzil.net/pub/download/index.php?quranType={kind}&marks=true&sajdah=true&rub=true&tatweel=false&outType=txt-2&agree=true"
TANZIL_TRANS = "https://tanzil.net/trans/?transID={id}&type=txt"
TANZIL_META = "https://tanzil.net/res/text/metadata/quran-data.xml"
LAYOUT = "https://raw.githubusercontent.com/zonetecde/mushaf-layout/refs/heads/main/mushaf/page-{n:03d}.json"

# id, language, name, translator, kind, licence
TRANSLATIONS = [
    ("en.sahih", "en", "Saheeh International", "Saheeh International", "translation"),
    ("ar.muyassar", "ar", "التفسير الميسر", "مجمع الملك فهد لطباعة المصحف الشريف", "tafsir"),
    ("id.indonesian", "id", "Terjemahan Kementerian Agama", "Kementerian Agama Republik Indonesia", "translation"),
    ("ur.junagarhi", "ur", "ترجمہ محمد جوناگڑھی", "محمد جوناگڑھی", "translation"),
    ("bn.bengali", "bn", "মুহিউদ্দীন খান", "মুহিউদ্দীন খান", "translation"),
    ("tr.diyanet", "tr", "Diyanet İşleri", "Diyanet İşleri Başkanlığı", "translation"),
    ("fr.hamidullah", "fr", "Muhammad Hamidullah", "Muhammad Hamidullah", "translation"),
    ("en.transliteration", "en", "Transliteration", "Tanzil Project", "transliteration"),
]
TANZIL_LICENCE = "Tanzil Project, non-commercial use, verbatim, credit the translator. tanzil.net/trans"

SILENT_ALEF_SIGN = "۟"   # the Hafs font draws this as an inline ring; see spike §12
SUKUN = "ْ"
SIGN_TOKENS = {"۞", "۩"}          # ۞ rub el hizb, ۩ sajdah, standalone tokens in Tanzil
PAUSE_MARKS = set("ۖۗۘۙۚۛۜ")
ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
# A layout word can carry a trailing ayah-ending digit ("الٓمٓ ١") or a trailing pause mark
# ("افْتَرَاهُ ۖ") glued on with a space. When a layout word's letters are corrected (see the
# LAYOUT CORRECTION rule below), this suffix is not part of the correction and must be kept as-is.
TRAILING_SUFFIX = re.compile(r"(\s*[" + ARABIC_INDIC + "".join(PAUSE_MARKS) + r"]+)$")

# U+06E2 (small high meem isolated form, marks iqlab) and U+06ED (small low meem, marks
# ghunnah/idgham) are tajweed annotation marks that Tanzil's plain Uthmani text and the Madinah
# Mushaf layout include at very different densities for the same underlying letters: across the
# corpus the layout carries 2,445 / 4,807 occurrences of U+06E2 / U+06ED respectively, against only
# 510 / 99 in Tanzil. Both are legitimate readings of the same text, not content errors, so they are
# stripped for the cross-check comparison only -- `ayah.text_uthmani`, `line_word.text` and
# `page_line.text` all keep exactly what their own source provided. U+200E/U+200F (directional
# marks) are stripped for the same reason: the layout attaches a stray U+200F to some standalone
# sign tokens (e.g. the 27:26 sajdah mark), which would otherwise fail the SIGN_TOKENS match.
COMPARE_STRIP_CHARS = "ۭۢ‎‏"


def fetch(url: str, name: str) -> bytes:
    CACHE.mkdir(parents=True, exist_ok=True)
    p = CACHE / name
    if not p.exists():
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Taqwa build)"})
        with urllib.request.urlopen(req, timeout=120) as r:
            p.write_bytes(r.read())
        print(f"  fetched {name} ({p.stat().st_size:,} bytes)")
    return p.read_bytes()


def clean(s: str) -> str:
    return s.replace("﻿", "").strip().replace(SILENT_ALEF_SIGN, SUKUN)


def parse_tanzil_text(raw: bytes) -> dict[tuple[int, int], str]:
    out = {}
    for line in raw.decode("utf-8").splitlines():
        line = line.replace("﻿", "")
        if not line or not line[0].isdigit():
            continue
        s, a, text = line.split("|", 2)
        out[(int(s), int(a))] = clean(text)
    assert len(out) == 6236, f"expected 6236 ayahs, got {len(out)}"
    return out


def parse_translation(raw: bytes, order: list[tuple[int, int]]) -> dict[tuple[int, int], str]:
    lines = [l for l in raw.decode("utf-8").replace("﻿", "").splitlines()]
    # Tanzil translation files: one ayah per line, then a blank line and a comment block.
    body = []
    for l in lines:
        if l.startswith("#"):
            continue
        body.append(l)
    while body and not body[-1].strip():
        body.pop()
    assert len(body) == 6236, f"translation has {len(body)} lines"
    tag = re.compile(r"</?[ub]>", re.IGNORECASE)
    return {k: re.sub(r"  +", " ", tag.sub("", v)).strip() for k, v in zip(order, body)}


def parse_meta(raw: bytes):
    root = ET.fromstring(raw)
    suras = [dict(e.attrib) for e in root.find("suras")]
    juzs = [(int(e.get("index")), int(e.get("sura")), int(e.get("aya"))) for e in root.find("juzs")]
    quarters = [(int(e.get("index")), int(e.get("sura")), int(e.get("aya"))) for e in root.find("hizbs")]
    pages = [(int(e.get("index")), int(e.get("sura")), int(e.get("aya"))) for e in root.find("pages")]
    sajdas = {(int(e.get("sura")), int(e.get("aya"))): (2 if e.get("type") == "obligatory" else 1) for e in root.find("sajdas")}
    assert len(suras) == 114 and len(juzs) == 30 and len(quarters) == 240 and len(pages) == 604
    return suras, juzs, quarters, pages, sajdas


def boundary_lookup(order, boundaries):
    """Map every (surah, ayah) to the index of the last boundary at or before it."""
    result, current, bi = {}, 0, 0
    starts = [(s, a) for _, s, a in boundaries]
    for key in order:
        while bi < len(starts) and starts[bi] <= key:
            current = boundaries[bi][0]
            bi += 1
        result[key] = current
    return result


def strip_for_compare(text: str) -> str:
    """Normalize text for the Tanzil/layout cross-check only. Never applied to stored text.

    Pause marks (U+06D6-U+06DC) are also stripped char-wise rather than only when they form a
    whole token: Tanzil sometimes emits a pause mark as its own token (space-separated) where the
    layout fuses it onto the end of the preceding word with no space (e.g. 18:1's closing
    "...عِوَجَا" + "ۖ" in Tanzil vs "...عِوَجَاۜ" in the layout) -- same letters, same recitation
    annotation, different tokenisation.

    Text is also put through Unicode canonical (NFC) normalization first: a handful of layout
    words order two combining diacritics (e.g. damma then shadda) differently from Tanzil's Uthmani
    text for the same reading (e.g. 52:1's "وَٱلطُّورِ"); NFC's canonical combining-class ordering
    makes such byte-level differences compare equal without touching which characters are present.
    """
    text = unicodedata.normalize("NFC", text)
    tokens = []
    for t in text.split():
        t = "".join(c for c in t if c not in COMPARE_STRIP_CHARS and c not in PAUSE_MARKS)
        if not t or t in SIGN_TOKENS:
            continue
        tokens.append(t)
    return " ".join(tokens)


def load_layout() -> list[dict]:
    pages = []
    for n in range(1, 605):
        pages.append(json.loads(fetch(LAYOUT.format(n=n), f"page-{n:03d}.json").decode("utf-8")))
    return pages


def derive_surah_headers(pages_out: list[tuple[int, list[dict]]]):
    """The upstream layout JSON is unreliable about chapter headers: 17 surahs have no
    surah-header line anywhere, 13+ surahs have a second surah-header line sitting where the
    *next* surah begins but mislabelled with the current surah's number, and at least one surah
    (9, at page 207 line 1) has a spurious header stuck mid-surah that belongs nowhere. Two
    surahs (81, 85) are also missing their basmala line.

    Rather than trust the layout's `surah` field, headers are derived positionally: for every
    surah S in 2..114 (S=1's header is already correct on page 1 line 1), the line carrying
    S:1:1 must be immediately preceded by a basmala (except S==9, which has none) and that, in
    turn, immediately preceded by a surah-header -- "immediately before" may cross a page
    boundary. A header found there is relabelled to S regardless of its json value; a missing
    header/basmala is synthesised; any surah-header line never claimed by this process is
    spurious and dropped. Mutates `pages_out` in place (insertions, deletions, and a final
    renumbering of `line` 1..n per page) and returns the logs.
    """
    def flatten():
        flat = []
        for pi, (pn, lines_out) in enumerate(pages_out):
            for li, L in enumerate(lines_out):
                flat.append((pi, li, L))
        return flat

    flat0 = flatten()
    first = flat0[0][2]
    assert first["type"] == "surah" and first["surah"] == 1, "page 1 line 1 must be the Al-Fatiha header"
    first["_claimed"] = True

    relabelled: list[tuple[int, int, int, int]] = []
    synth_headers: list[tuple[int, int]] = []
    synth_basmalas: list[tuple[int, int]] = []

    for s in range(2, 115):
        flat = flatten()
        idx = next(i for i, (_, _, L) in enumerate(flat)
                   if L["type"] == "text" and L["words"][0]["s"] == s
                   and L["words"][0]["a"] == 1 and L["words"][0]["wi"] == 1)
        pi_text, li_text, _ = flat[idx]
        pn_text = pages_out[pi_text][0]

        basmala_entry = None
        if s != 9 and idx - 1 >= 0 and flat[idx - 1][2]["type"] == "basmala":
            basmala_entry = flat[idx - 1]

        header_search_i = idx - 2 if basmala_entry else idx - 1
        header_entry = None
        if header_search_i >= 0 and flat[header_search_i][2]["type"] == "surah":
            header_entry = flat[header_search_i]

        insert_pos = li_text
        if header_entry is not None:
            hpi, hli, hdict = header_entry
            if hdict["surah"] != s:
                relabelled.append((pages_out[hpi][0], hdict.get("line"), hdict["surah"], s))
                hdict["surah"] = s
            hdict["_claimed"] = True
        else:
            new_header = {"type": "surah", "surah": s, "_claimed": True}
            if basmala_entry is not None:
                b_pi, b_li, _ = basmala_entry
                pages_out[b_pi][1].insert(b_li, new_header)
                if b_pi == pi_text:
                    insert_pos += 1
            else:
                pages_out[pi_text][1].insert(insert_pos, new_header)
                insert_pos += 1
            synth_headers.append((s, pn_text))

        if s != 9 and basmala_entry is None:
            pages_out[pi_text][1].insert(insert_pos, {"type": "basmala"})
            synth_basmalas.append((s, pn_text))

    dropped: list[tuple[int, int, int]] = []
    for pi, (pn, lines_out) in enumerate(pages_out):
        keep = []
        for L in lines_out:
            if L["type"] == "surah" and not L.get("_claimed"):
                dropped.append((pn, L.get("line"), L["surah"]))
                continue
            L.pop("_claimed", None)
            keep.append(L)
        pages_out[pi] = (pn, keep)

    for pn, lines_out in pages_out:
        for li, L in enumerate(lines_out, start=1):
            L["line"] = li

    for pn, line, json_surah, actual in relabelled:
        print(f"RELABELLED HEADER page {pn} line {line} json={json_surah} actual={actual}")
    for s, pn in synth_headers:
        print(f"SYNTHESISED HEADER surah {s} on page {pn}")
    for s, pn in synth_basmalas:
        print(f"SYNTHESISED BASMALA surah {s} on page {pn}")
    for pn, line, json_surah in dropped:
        print(f"DROPPED HEADER page {pn} line {line} (json surah={json_surah})")

    return relabelled, synth_headers, synth_basmalas, dropped


def build(conn: sqlite3.Connection):
    print("Downloading and parsing")
    uthmani = parse_tanzil_text(fetch(TANZIL_TEXT.format(kind="uthmani"), "uthmani.txt"))
    simple = parse_tanzil_text(fetch(TANZIL_TEXT.format(kind="simple-clean"), "simple-clean.txt"))
    order = sorted(uthmani)
    suras, juzs, quarters, pages, sajdas = parse_meta(fetch(TANZIL_META, "quran-data.xml"))
    juz_of = boundary_lookup(order, juzs)
    quarter_of = boundary_lookup(order, quarters)
    page_of = boundary_lookup(order, pages)
    layout = load_layout()

    print("Writing schema")
    conn.executescript(open(ROOT / "tools" / "quran-schema.sql", encoding="utf-8").read())

    print("Surahs and ayahs")
    for s in suras:
        n = int(s["index"])
        first = (n, 1)
        conn.execute(
            "INSERT INTO surah VALUES (?,?,?,?,?,?,?,?)",
            (n, s["name"], s["tname"], s["ename"], "Makki" if s["type"] == "Meccan" else "Madani",
             int(s["ayas"]), page_of[first], juz_of[first]),
        )
    for key in order:
        s, a = key
        conn.execute(
            "INSERT INTO ayah VALUES (?,?,?,?,?,?,?,?)",
            (s, a, uthmani[key], simple[key], page_of[key], juz_of[key], quarter_of[key], sajdas.get(key, 0)),
        )
    for idx, s, a in juzs:
        conn.execute("INSERT INTO juz VALUES (?,?,?)", (idx, s, a))

    print("Translations")
    for tid, lang, name, translator, kind in TRANSLATIONS:
        licence = TANZIL_LICENCE
        conn.execute("INSERT INTO translation VALUES (?,?,?,?,?,?,?)",
                     (tid, lang, name, translator, licence, f"https://tanzil.net/trans/{tid}", kind))
        text = parse_translation(fetch(TANZIL_TRANS.format(id=tid), f"{tid}.txt"), order)
        conn.executemany("INSERT INTO ayah_translation VALUES (?,?,?,?)",
                         [(tid, s, a, text[(s, a)]) for (s, a) in order])

    print("Pages, lines, words: reading layout")
    # Materialize every text line's words as mutable entries (not yet written to the DB) so the
    # cross-check below can apply a rule-based correction to a word's text before anything is
    # inserted. `rebuilt` collects each ayah's word entries in reading order for the comparison.
    rebuilt: dict[tuple[int, int], list[dict]] = {}
    pages_out = []
    for p in layout:
        lines_out = []
        for L in p["lines"]:
            t = L["type"]
            if t == "surah-header":
                lines_out.append({"type": "surah", "line": L["line"], "surah": int(L["surah"])})
            elif t == "basmala":
                lines_out.append({"type": "basmala", "line": L["line"]})
            else:
                entries = []
                for pos, w in enumerate(L["words"], start=1):
                    s, a, wi = (int(x) for x in w["location"].split(":"))
                    entry = {"pos": pos, "s": s, "a": a, "wi": wi, "text": clean(w["word"])}
                    entries.append(entry)
                    rebuilt.setdefault((s, a), []).append(entry)
                lines_out.append({"type": "text", "line": L["line"], "words": entries})
        pages_out.append((p["page"], lines_out))

    print("Deriving surah headers positionally (layout's surah-header placement is unreliable)")
    relabelled, synth_headers, synth_basmalas, dropped_headers = derive_surah_headers(pages_out)
    print(f"  {len(relabelled)} header(s) relabelled, {len(synth_headers)} header(s) synthesised, "
          f"{len(synth_basmalas)} basmala(s) synthesised, {len(dropped_headers)} spurious header(s) dropped")

    print("Cross-checking layout words against Tanzil, applying rule-based corrections")
    # Every surah's ayah 1 except Al-Fatiha (1, itself the basmala) and At-Tawbah (9, which has
    # none) carries the basmala inline in Tanzil's ayah text, while the layout stores it as its own
    # `basmala` line with no words (spec §3.2). The basmala is always exactly 4 words, so that known
    # prefix is stripped by count, not by matching it against a reference spelling: Tanzil writes it
    # with an extra shadda ("بِّسْمِ") for at-Tin (95) and al-Qadr (97), a legitimate recitation
    # variant, not a different-length prefix.
    BASMALA_WORD_COUNT = len(uthmani[(1, 1)].split())
    bad = 0
    second_tier: list[tuple[int, int]] = []
    corrections = 0
    for key in order:
        s, a = key
        tanzil_words = uthmani[key].split()
        if a == 1 and s not in (1, 9) and len(tanzil_words) > BASMALA_WORD_COUNT:
            tanzil_words = tanzil_words[BASMALA_WORD_COUNT:]

        entries = rebuilt.get(key, [])
        layout_words = [e["text"].rstrip(ARABIC_INDIC).rstrip() for e in entries]
        expect = strip_for_compare(" ".join(tanzil_words))
        got = strip_for_compare(" ".join(layout_words))
        if expect == got:
            continue
        if expect.replace(" ", "") == got.replace(" ", ""):
            second_tier.append(key)
            continue

        # Word-align on the *comparable* words only: a token that strip_for_compare reduces to
        # nothing (a standalone pause mark or sign token present on one side only) is not a "word"
        # for the purposes of "the word lists have the same length" -- keep the raw word alongside
        # its normalized form so a real content difference can still be found and corrected by
        # position, without off-by-one drift from those dropped tokens.
        tanzil_aligned = [(w, n) for w in tanzil_words if (n := strip_for_compare(w))]
        layout_aligned = [(e, n) for e, w in zip(entries, layout_words) if (n := strip_for_compare(w))]
        diffs = [] if len(tanzil_aligned) != len(layout_aligned) else [
            i for i in range(len(tanzil_aligned)) if tanzil_aligned[i][1] != layout_aligned[i][1]
        ]
        if len(diffs) == 1:
            i = diffs[0]
            tanzil_word = tanzil_aligned[i][0]
            entry = layout_aligned[i][0]
            m = TRAILING_SUFFIX.search(entry["text"])
            digits = m.group(1) if m else ""
            print(f"LAYOUT CORRECTION {entry['s']}:{entry['a']}:{entry['wi']}  "
                  f"layout={entry['text']}  tanzil={tanzil_word}")
            entry["text"] = tanzil_word + digits
            corrections += 1
            continue

        bad += 1
        if bad <= 10:
            print(f"  MISMATCH {key}:\n    tanzil: {expect}\n    layout: {got}")
    if second_tier:
        print(f"  {len(second_tier)} ayah(s) needed the second-tier (spacing-only) comparison: {second_tier}")
    if corrections:
        print(f"  {corrections} layout word(s) corrected to match Tanzil")
    if corrections > 5:
        raise SystemExit(f"{corrections} layout corrections needed -- expected at most 5, investigate before proceeding")
    if bad:
        raise SystemExit(f"{bad} ayahs differ between Tanzil and the layout")

    print("Writing pages, lines, words")
    for pn, lines_out in pages_out:
        first_ayah = None
        for L in lines_out:
            if L["type"] == "surah":
                conn.execute("INSERT INTO page_line (page, line, type, surah) VALUES (?,?,?,?)",
                             (pn, L["line"], "surah", L["surah"]))
            elif L["type"] == "basmala":
                conn.execute("INSERT INTO page_line (page, line, type) VALUES (?,?,?)", (pn, L["line"], "basmala"))
            else:
                entries = L["words"]
                fs, fa = entries[0]["s"], entries[0]["a"]
                ls, la = entries[-1]["s"], entries[-1]["a"]
                if first_ayah is None:
                    first_ayah = (fs, fa)
                last_word_text = entries[-1]["text"]
                ends_ayah = last_word_text and last_word_text[-1] in ARABIC_INDIC
                ends_surah = 1 if ends_ayah and la == int(next(x for x in suras if int(x["index"]) == ls)["ayas"]) else 0
                line_text = " ".join(e["text"] for e in entries)
                conn.execute(
                    "INSERT INTO page_line VALUES (?,?,?,?,?,?,?,?,?,?)",
                    (pn, L["line"], "text", None, line_text, fs, fa, ls, la, ends_surah),
                )
                for e in entries:
                    conn.execute("INSERT INTO line_word VALUES (?,?,?,?,?,?,?)",
                                 (pn, L["line"], e["pos"], e["s"], e["a"], e["wi"], e["text"]))
        if first_ayah is None:
            raise SystemExit(f"page {pn} has no text line")
        conn.execute("INSERT INTO page VALUES (?,?,?)", (pn, first_ayah[0], first_ayah[1]))

    print("Search index")
    conn.execute("INSERT INTO ayah_fts(ayah_fts) VALUES ('rebuild')")
    conn.execute(f"PRAGMA user_version = {USER_VERSION}")
    conn.commit()


def verify(conn: sqlite3.Connection):
    def one(sql, *args):
        return conn.execute(sql, args).fetchone()[0]
    assert one("SELECT count(*) FROM surah") == 114
    assert one("SELECT count(*) FROM ayah") == 6236
    assert one("SELECT count(*) FROM juz") == 30
    assert one("SELECT count(*) FROM page") == 604
    assert one("SELECT count(*) FROM translation") == len(TRANSLATIONS)
    assert one("SELECT count(*) FROM ayah_translation") == 6236 * len(TRANSLATIONS)

    # Every page from 3 to 604 must have between 13 and 16 lines: the header-derivation pass
    # (see derive_surah_headers) inserts up to 2 synthetic lines (header + basmala) or drops a
    # spurious one, so the old fixed "15 lines, with two documented exceptions" assertion no
    # longer holds. Print the distribution so a real regression (e.g. a page collapsing to 1
    # line) is easy to spot even though the bound is loose.
    rows = conn.execute(
        "SELECT page, count(*) FROM page_line WHERE page BETWEEN 3 AND 604 GROUP BY page"
    ).fetchall()
    assert len(rows) == 602, f"expected 602 pages (3..604), got {len(rows)}"
    dist = {}
    for _, c in rows:
        dist[c] = dist.get(c, 0) + 1
    print(f"  page_line line-count distribution (pages 3-604): {dict(sorted(dist.items()))}")
    bad_pages = [(p, c) for p, c in rows if not (13 <= c <= 16)]
    assert not bad_pages, f"page(s) with line count outside [13,16]: {bad_pages}"

    # Exactly one surah-header line per surah, 1..114.
    surah_rows = conn.execute(
        "SELECT surah, count(*) FROM page_line WHERE type='surah' GROUP BY surah ORDER BY surah"
    ).fetchall()
    assert [r[0] for r in surah_rows] == list(range(1, 115)), \
        f"surah header numbers wrong: {[r[0] for r in surah_rows]}"
    assert all(r[1] == 1 for r in surah_rows), f"surah(s) with != 1 header: {surah_rows}"

    # Exactly 112 basmala lines: every surah except Al-Fatiha (1) and At-Tawbah (9).
    assert one("SELECT count(*) FROM page_line WHERE type='basmala'") == 112

    # For every surah 2..114, the basmala (or, for surah 9, the header itself) is immediately
    # followed -- in page/line order -- by a text line whose first word is S:1:1.
    flat = conn.execute("SELECT page, line, type, surah FROM page_line ORDER BY page, line").fetchall()
    header_pos = {r[3]: i for i, r in enumerate(flat) if r[2] == "surah"}
    for s in range(2, 115):
        i = header_pos[s] + 1
        if s != 9:
            assert flat[i][2] == "basmala", f"surah {s}: header not immediately followed by basmala"
            i += 1
        tpage, tline, ttype, _ = flat[i]
        assert ttype == "text", f"surah {s}: expected a text line at {(tpage, tline)}, got {ttype}"
        w = conn.execute(
            "SELECT surah, ayah, word FROM line_word WHERE page=? AND line=? AND position=1", (tpage, tline)
        ).fetchone()
        assert w == (s, 1, 1), f"surah {s}: first word after header/basmala is {w}, expected ({s}, 1, 1)"

    assert one("SELECT count(*) FROM ayah WHERE instr(text_uthmani, char(1759)) > 0") == 0, "U+06DF survived"
    assert one("SELECT count(*) FROM line_word WHERE instr(text, char(1759)) > 0") == 0
    assert one("SELECT text_uthmani FROM ayah WHERE surah=1 AND number=1").startswith("بِسْمِ")
    assert one("SELECT page FROM ayah WHERE surah=2 AND number=255") == 42
    assert one("SELECT juz FROM ayah WHERE surah=114 AND number=6") == 30
    assert one("SELECT count(*) FROM ayah_fts WHERE ayah_fts MATCH 'الحمد'") >= 20
    assert one("PRAGMA user_version") == USER_VERSION
    print("verify: ok")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verify", action="store_true")
    args = ap.parse_args()
    if args.verify:
        with sqlite3.connect(OUT) as conn:
            verify(conn)
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    conn = sqlite3.connect(OUT)
    conn.execute("PRAGMA journal_mode = OFF")
    build(conn)
    verify(conn)
    conn.execute("VACUUM")
    conn.close()
    print(f"wrote {OUT} ({OUT.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
