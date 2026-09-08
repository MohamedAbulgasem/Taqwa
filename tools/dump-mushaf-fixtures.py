#!/usr/bin/env python3
"""Regenerate shared/src/commonTest/.../MushafPageFixtures.kt from the bundled quran.db: pages
1, 2 and 3 copied verbatim, never retyped. Run after every pipeline change that touches
page_line or line_word."""
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "shared/src/commonMain/composeResources/files/quran.db"
OUT = ROOT / "shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/MushafPageFixtures.kt"

HEADER = '''package world.taqwa.app.feature.quran

import world.taqwa.app.quran.LineType
import world.taqwa.app.quran.LineWord
import world.taqwa.app.quran.MushafLine
import world.taqwa.app.quran.MushafPage

/**
 * Pages 1, 2 and 3 of the Madinah Mushaf, copied verbatim out of the bundled
 * `quran.db` (`page`, `page_line` and `line_word`) by `tools/dump-mushaf-fixtures.py` rather than
 * typed: the global rule is that Quran text is never retyped, and that holds for test fixtures too.
 * Regenerate, never edit.
 *
 * Between them these three pages carry every shape [MushafViewModel] and [MushafPageView] have to
 * handle: page 1 is Al-Faatiha's short, vertically centred page with a `SURAH` band and a line
 * that ends a surah; page 2 adds a `BASMALA` line; page 3 is an ordinary 15-line text page whose
 * first line starts at 2:6 - which is what the reader has to return to when the mode toggle goes
 * back to translation.
 */
'''


def kstr(s):
    return "null" if s is None else '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\u00a0", "\\u00a0") + '"'


def kint(v):
    return "null" if v is None else str(v)


def main():
    conn = sqlite3.connect(DB)
    out = [HEADER]
    for page in (1, 2, 3):
        number, first_surah, first_ayah = conn.execute("SELECT number, first_surah, first_ayah FROM page WHERE number=?", (page,)).fetchone()
        juz = conn.execute("SELECT juz FROM ayah WHERE surah=? AND number=?", (first_surah, first_ayah)).fetchone()[0]
        out.append(f"\ninternal val MUSHAF_PAGE_{page} = MushafPage({number}, {first_surah}, {first_ayah}, {juz}, listOf(\n")
        for line, ltype, surah, text, fs, fa, ls, la, ends in conn.execute(
            "SELECT line, type, surah, text, first_surah, first_ayah, last_surah, last_ayah, ends_surah FROM page_line WHERE page=? ORDER BY line", (page,)
        ):
            out.append("    MushafLine(\n")
            out.append(f"        line = {line}, type = LineType.{ltype.upper()}, surah = {kint(surah)},\n")
            out.append(f"        text = {kstr(text)},\n")
            out.append(f"        firstSurah = {kint(fs)}, firstAyah = {kint(fa)},\n")
            out.append(f"        lastSurah = {kint(ls)}, lastAyah = {kint(la)},\n")
            out.append(f"        endsSurah = {'true' if ends else 'false'},\n")
            words = conn.execute("SELECT position, surah, ayah, word, text FROM line_word WHERE page=? AND line=? ORDER BY position", (page, line)).fetchall()
            if not words:
                out.append("        words = emptyList(),\n")
            else:
                out.append("        words = listOf(\n")
                for pos, s, a, w, t in words:
                    out.append(f"            LineWord({pos}, {s}, {a}, {w}, {kstr(t)}),\n")
                out.append("        ),\n")
            out.append("    ),\n")
        out.append("))\n")
    OUT.write_text("".join(out), encoding="utf-8")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
