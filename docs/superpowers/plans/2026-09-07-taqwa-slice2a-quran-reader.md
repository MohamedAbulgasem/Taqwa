# Taqwa Slice 2a: Quran Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Quran tab: an offline Quran with a translation-mode reader (ayah cards) and a Mushaf-mode reader (the 604 printed pages), a tab root with continue-reading, surah and juz lists, a reading-settings sheet, and the tab-bar fix.

**Architecture:** A Python pipeline turns Tanzil text, Tanzil translations and the Madinah page layout into one SQLite file bundled as a compose resource. SQLDelight reads it through a `QuranRepository` in `commonMain`; three screens under `feature/quran` render it with the bundled KFGQPC Hafs font. Navigation gains a third tab root and two pushed screens. Preferences live in the existing DataStore.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.12.0, SQLDelight 2.2.1 (`sqlite-3-38` dialect; `android-driver`, `native-driver`, `sqlite-driver`), DataStore preferences 1.1.7, Python 3.11+ (standard library) for the pipeline.

**Spec:** `docs/superpowers/specs/2026-09-07-taqwa-quran-reader-design.md`. Read it first; section numbers below refer to it.

## Global Constraints

- **No network in the app.** Everything the app shows is in the bundled database and resources. The pipeline downloads at build time on the developer's machine only.
- **Quran text is never retyped.** All Arabic comes from the Tanzil files and the layout JSON through the pipeline. Test fixtures quote the database or the pipeline's cached files, never a hand-typed string, except single words used to test the normaliser.
- **Text rules (spec §3.2):** U+06DF → U+0652 everywhere; roundel = `text + " " + Arabic-Indic digits` (spec §5.2), never U+06DD or U+2060; BOM stripped; transliteration tags stripped.
- **Font:** Quran text only in `uthmanic_hafs.ttf` via `MushafFont`; UI text unchanged. Line height 2.0 × size in translation mode.
- **Direction:** Quran text composables wrap in `LocalLayoutDirection = Rtl`; the Mushaf pager runs right-to-left in every UI language.
- **Palette:** no new colour literals; everything from `LocalTaqwaColors`. No ripples (`indication = null`, as the rest of the app). 44 dp minimum tap targets.
- **Strings:** every user-visible string in both `values/strings.xml` and `values-ar/strings.xml`. No em or en dashes in copy; plain punctuation.
- **Tab bar (spec §2.6):** glyphs 22 dp, labels 12 sp, unselected colour `textSecondary`.
- **Tests:** `./scripts/test.sh` must pass (JVM + iOS) at the end of every task; Android JVM tests over the real database go in `shared/src/androidUnitTest`.
- **Commits:** one per task, message in the repo's style, `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` trailer. Never push; the owner pushes.
- **Shell:** cwd resets between commands; always `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && …` or absolute paths. Use `~/platform-tools/adb`. iOS test/build scripts pin Xcode 26 themselves.

---

## File structure

| Path | Responsibility |
|---|---|
| `tools/build-quran-db.py` | Download (cached in `tools/cache/`), normalise, verify, write `quran.db` |
| `tools/cache/` | Git-ignored download cache |
| `shared/src/commonMain/composeResources/files/quran.db` | The bundled database (committed; ~30 MB) |
| `shared/src/commonMain/composeResources/font/uthmanic_hafs.ttf` | KFGQPC Hafs font |
| `shared/src/commonMain/sqldelight/world/taqwa/app/quran/db/Quran.sq` | SQLDelight table declarations and queries |
| `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranModels.kt` | Domain types |
| `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranText.kt` | Roundel rule, Arabic-Indic digits, search normaliser |
| `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranDb.kt` | `QuranDb.VERSION`, `expect fun createQuranDriver()` |
| `shared/src/androidMain/.../quran/QuranDb.android.kt`, `shared/src/iosMain/.../quran/QuranDb.ios.kt` | Copy-on-first-use and driver creation |
| `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranRepository.kt` | Typed access over the queries |
| `shared/src/commonMain/kotlin/world/taqwa/app/quran/ReadingSettings.kt` | `ReadingMode`, `ReadingSettings`, `ReadingPosition`, locale defaults |
| `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsRepository.kt` | New flows and setters for the reading preferences |
| `shared/src/commonMain/kotlin/world/taqwa/app/design/MushafFont.kt` | `mushafFamily()`, `TaqwaText.quran(sizeSp)` |
| `shared/src/commonMain/kotlin/world/taqwa/app/design/components/TaqwaTabBar.kt` | Third tab, 22 dp glyphs, unselected colour |
| `shared/src/commonMain/kotlin/world/taqwa/app/nav/Tab.kt`, `Screen.kt` | `QURAN`, `Screen.Quran`, `Screen.Reader`, `Screen.Mushaf` |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/QuranRootScreen.kt`, `QuranRootViewModel.kt` | Tab root |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/ReaderHeader.kt` | Shared header |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/ReaderScreen.kt`, `ReaderViewModel.kt`, `AyahCard.kt` | Translation mode |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/MushafScreen.kt`, `MushafViewModel.kt`, `MushafPageView.kt` | Mushaf mode |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/ReadingSheet.kt` | Settings sheet |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AttributionScreen.kt` | New credits |
| `shared/src/commonTest/kotlin/world/taqwa/app/quran/*Test.kt` | Pure-logic tests |
| `shared/src/androidUnitTest/kotlin/world/taqwa/app/quran/QuranRepositoryDbTest.kt` | Real-database tests on the JVM |

---

### Task 1: The data pipeline and the bundled database

**Files:**
- Create: `tools/build-quran-db.py`
- Create: `shared/src/commonMain/composeResources/files/quran.db` (generated)
- Modify: `.gitignore` (add `tools/cache/`)
- Copy: `/private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/bbf8ad7a-0b95-43b1-89c1-af0f595af640/scratchpad/fonts/UthmanicHafs_V22.ttf` → `shared/src/commonMain/composeResources/font/uthmanic_hafs.ttf` (if the scratchpad copy is gone, download `https://static-cdn.tarteel.ai/qul/fonts/UthmanicHafs_V22.ttf`)

**Interfaces:**
- Produces: the schema in spec §3.3, exactly, with `PRAGMA user_version = 1`.

- [ ] **Step 1: Write the pipeline**

```python
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
TEXT_LICENCE = "Tanzil Quran Text v1.1, CC BY-ND 3.0, tanzil.net"

SILENT_ALEF_SIGN = "۟"   # the Hafs font draws this as an inline ring; see spike §12
SUKUN = "ْ"
SIGN_TOKENS = {"۞", "۩"}          # ۞ rub el hizb, ۩ sajdah, standalone tokens in Tanzil
PAUSE_MARKS = set("ۖۗۘۙۚۛۜ")
ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"


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
    tokens = [t for t in text.split() if t not in SIGN_TOKENS and not all(c in PAUSE_MARKS for c in t)]
    return " ".join(tokens)


def load_layout() -> list[dict]:
    pages = []
    for n in range(1, 605):
        pages.append(json.loads(fetch(LAYOUT.format(n=n), f"page-{n:03d}.json").decode("utf-8")))
    return pages


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

    print("Pages, lines, words")
    rebuilt: dict[tuple[int, int], list[str]] = {}
    for p in layout:
        pn = p["page"]
        first_ayah = None
        for L in p["lines"]:
            t = L["type"]
            if t == "surah-header":
                conn.execute("INSERT INTO page_line (page, line, type, surah) VALUES (?,?,?,?)",
                             (pn, L["line"], "surah", int(L["surah"])))
            elif t == "basmala":
                conn.execute("INSERT INTO page_line (page, line, type) VALUES (?,?,?)", (pn, L["line"], "basmala"))
            else:
                words = L["words"]
                firsts = words[0]["location"].split(":")
                lasts = words[-1]["location"].split(":")
                fs, fa = int(firsts[0]), int(firsts[1])
                ls, la = int(lasts[0]), int(lasts[1])
                if first_ayah is None:
                    first_ayah = (fs, fa)
                last_word_text = clean(words[-1]["word"])
                ends_ayah = last_word_text and last_word_text[-1] in ARABIC_INDIC
                ends_surah = 1 if ends_ayah and la == int(next(x for x in suras if int(x["index"]) == ls)["ayas"]) else 0
                line_text = " ".join(clean(w["word"]) for w in words)
                conn.execute(
                    "INSERT INTO page_line VALUES (?,?,?,?,?,?,?,?,?,?)",
                    (pn, L["line"], "text", None, line_text, fs, fa, ls, la, ends_surah),
                )
                for pos, w in enumerate(words, start=1):
                    s, a, wi = (int(x) for x in w["location"].split(":"))
                    text = clean(w["word"])
                    conn.execute("INSERT INTO line_word VALUES (?,?,?,?,?,?,?)", (pn, L["line"], pos, s, a, wi, text))
                    rebuilt.setdefault((s, a), []).append(text.rstrip(ARABIC_INDIC).rstrip())
        if first_ayah is None:
            raise SystemExit(f"page {pn} has no text line")
        conn.execute("INSERT INTO page VALUES (?,?,?)", (pn, first_ayah[0], first_ayah[1]))

    print("Search index")
    conn.execute("INSERT INTO ayah_fts(ayah_fts) VALUES ('rebuild')")
    conn.execute(f"PRAGMA user_version = {USER_VERSION}")
    conn.commit()

    print("Cross-checking layout words against Tanzil")
    bad = 0
    for key in order:
        expect = strip_for_compare(uthmani[key])
        got = strip_for_compare(" ".join(w for w in rebuilt.get(key, []) if w))
        if expect != got:
            bad += 1
            if bad <= 10:
                print(f"  MISMATCH {key}:\n    tanzil: {expect}\n    layout: {got}")
    if bad:
        raise SystemExit(f"{bad} ayahs differ between Tanzil and the layout")


def verify(conn: sqlite3.Connection):
    def one(sql, *args):
        return conn.execute(sql, args).fetchone()[0]
    assert one("SELECT count(*) FROM surah") == 114
    assert one("SELECT count(*) FROM ayah") == 6236
    assert one("SELECT count(*) FROM juz") == 30
    assert one("SELECT count(*) FROM page") == 604
    assert one("SELECT count(*) FROM translation") == len(TRANSLATIONS)
    assert one("SELECT count(*) FROM ayah_translation") == 6236 * len(TRANSLATIONS)
    assert one("SELECT count(*) FROM page_line WHERE page > 2 GROUP BY page HAVING count(*) <> 15") is None or False
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
```

Also create `tools/quran-schema.sql` containing the spec §3.3 schema verbatim (so the pipeline and `Quran.sq` are compared by eye against one text). The `page_line` INSERT with ten values relies on column order `page, line, type, surah, text, first_surah, first_ayah, last_surah, last_ayah, ends_surah`.

Note on `verify`: the "15 lines" assertion above is written defensively; replace it with an explicit query: `SELECT count(*) FROM (SELECT page FROM page_line WHERE page > 2 GROUP BY page HAVING count(*) <> 15)` must equal 0. Page 2:255's page is 42 in the Madinah Mushaf; if the assertion fails, print the value and check against `quran-data.xml` before changing the test.

- [ ] **Step 2: Run it**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && printf '\n# Quran pipeline downloads\ntools/cache/\n' >> .gitignore && python3 tools/build-quran-db.py
```

Expected: downloads listed once, "verify: ok", `wrote … quran.db (NN.N MB)` with NN under 40. If the cross-check reports mismatches, inspect them: the known legitimate differences are standalone sign tokens; extend `SIGN_TOKENS` or `strip_for_compare` only for tokens that are demonstrably signs, never by loosening the comparison to a prefix.

- [ ] **Step 3: Copy the font**

```bash
cp /private/tmp/claude-501/-Users-mohamedabulgasem-Desktop-Workspace-apps/bbf8ad7a-0b95-43b1-89c1-af0f595af640/scratchpad/fonts/UthmanicHafs_V22.ttf /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/shared/src/commonMain/composeResources/font/uthmanic_hafs.ttf
```

- [ ] **Step 4: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add tools/build-quran-db.py tools/quran-schema.sql .gitignore shared/src/commonMain/composeResources/files/quran.db shared/src/commonMain/composeResources/font/uthmanic_hafs.ttf && git commit -m "Quran data pipeline and bundled database

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: SQLDelight, the driver seam, and the repository

**Files:**
- Modify: `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `build.gradle.kts` (root plugin declaration if plugins are declared there)
- Create: `shared/src/commonMain/sqldelight/world/taqwa/app/quran/db/Quran.sq`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranModels.kt`, `QuranDb.kt`, `QuranRepository.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/quran/QuranDb.android.kt`, `shared/src/iosMain/kotlin/world/taqwa/app/quran/QuranDb.ios.kt`
- Create: `shared/src/androidUnitTest/kotlin/world/taqwa/app/quran/QuranRepositoryDbTest.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt`

**Interfaces:**
- Produces: `QuranRepository` with `suspend fun surahs(): List<Surah>`, `suspend fun surah(number: Int): Surah`, `suspend fun juzs(): List<Juz>`, `suspend fun ayahs(surah: Int): List<Ayah>`, `suspend fun translations(): List<TranslationInfo>`, `suspend fun translationTexts(translationId: String, surah: Int): Map<Int, String>` (ayah number → text), `suspend fun pageOf(surah: Int, ayah: Int): Int`, `suspend fun page(number: Int): MushafPage`, `suspend fun surahOfPage(number: Int): Surah`.

- [ ] **Step 1: Gradle**

`gradle/libs.versions.toml`:

```toml
[versions]
sqldelight = "2.2.1"

[libraries]
sqldelight-android = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-sqlite = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }

[plugins]
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

`shared/build.gradle.kts`: apply `alias(libs.plugins.sqldelight)`; add `implementation(libs.sqldelight.coroutines)` to commonMain, `libs.sqldelight.android` to androidMain, `libs.sqldelight.native` to iosMain, `libs.sqldelight.sqlite` to a new `androidUnitTest` dependencies block (JVM unit tests already run as `testDebugUnitTest`); and:

```kotlin
sqldelight {
    databases {
        create("QuranDatabase") {
            packageName.set("world.taqwa.app.quran.db")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.2.1")
            // The database ships prebuilt; the schema below exists for code generation only.
            deriveSchemaFromMigrations.set(false)
        }
    }
}
```

If the root `build.gradle.kts` lists plugins with `apply false`, add the sqldelight alias there too. Run `./gradlew :shared:generateCommonMainQuranDatabaseInterface -q` to confirm codegen before writing Kotlin.

- [ ] **Step 2: Quran.sq**

Declare the tables exactly as in spec §3.3 (SQLDelight needs them to type the queries; the driver never executes the CREATE statements because the file already exists), then the queries:

```sql
surahs:
SELECT * FROM surah ORDER BY number;

surahByNumber:
SELECT * FROM surah WHERE number = ?;

juzs:
SELECT juz.number, juz.start_surah, juz.start_ayah FROM juz ORDER BY number;

ayahsOfSurah:
SELECT surah, number, text_uthmani, page, juz, hizb_quarter, sajdah FROM ayah WHERE surah = ? ORDER BY number;

translations:
SELECT * FROM translation ORDER BY kind, language, name;

translationOfSurah:
SELECT number, text FROM ayah_translation WHERE translation_id = ? AND surah = ? ORDER BY number;

pageOfAyah:
SELECT page FROM ayah WHERE surah = ? AND number = ?;

pageLines:
SELECT * FROM page_line WHERE page = ? ORDER BY line;

pageWords:
SELECT * FROM line_word WHERE page = ? ORDER BY line, position;

pageHeader:
SELECT page.number, page.first_surah, page.first_ayah FROM page WHERE number = ?;
```

- [ ] **Step 3: Domain types**

```kotlin
package world.taqwa.app.quran

enum class Revelation { MAKKI, MADANI }

data class Surah(
    val number: Int, val nameArabic: String, val nameLatin: String, val meaning: String,
    val revelation: Revelation, val ayahCount: Int, val startPage: Int, val startJuz: Int,
)

data class Ayah(val surah: Int, val number: Int, val text: String, val page: Int, val juz: Int, val hizbQuarter: Int, val sajdah: Int)

data class Juz(val number: Int, val startSurah: Int, val startAyah: Int)

enum class TextKind { TRANSLATION, TAFSIR, TRANSLITERATION }

data class TranslationInfo(val id: String, val language: String, val name: String, val translator: String, val licence: String, val sourceUrl: String, val kind: TextKind)

enum class LineType { SURAH, BASMALA, TEXT }

data class LineWord(val position: Int, val surah: Int, val ayah: Int, val word: Int, val text: String)

data class MushafLine(
    val line: Int, val type: LineType, val surah: Int?, val text: String?,
    val firstSurah: Int?, val firstAyah: Int?, val lastSurah: Int?, val lastAyah: Int?,
    val endsSurah: Boolean, val words: List<LineWord>,
)

data class MushafPage(val number: Int, val firstSurah: Int, val firstAyah: Int, val lines: List<MushafLine>) {
    val juz: Int get() = TODO("filled by repository from the first ayah")  // remove: see repository, juz is a field
}
```

Make `MushafPage` carry `val juz: Int` as a plain constructor field set by the repository from `ayah.juz` of the first ayah; delete the `TODO` body above when transcribing.

- [ ] **Step 4: Driver seam**

`QuranDb.kt` (commonMain):

```kotlin
package world.taqwa.app.quran

import app.cash.sqldelight.db.SqlDriver

object QuranDb {
    /** Must equal the pipeline's USER_VERSION; a mismatch on disk triggers a fresh copy. */
    const val VERSION = 1
    const val FILE = "quran.db"
    const val RESOURCE = "files/quran.db"
}

/**
 * Opens the bundled database, copying it out of the app's resources into the platform's database
 * directory first if it is missing or carries a different `user_version`. Blocking; call it off
 * the main thread once, from [QuranRepository].
 */
expect fun createQuranDriver(): SqlDriver
```

Android actual: needs a `Context`; the app already exposes an application context for widgets (see how `androidWidgetPinHook` / `TaqwaApplication` provides it) — follow the same hook pattern (`quranContextProvider` set in `TaqwaApplication`), or reuse the existing context holder if one exists. Implementation: `val file = context.getDatabasePath(QuranDb.FILE)`; if `!file.exists() || userVersion(file) != QuranDb.VERSION` then `file.parentFile.mkdirs()` and write `runBlocking { Res.readBytes(QuranDb.RESOURCE) }` to a temp file then rename; return `AndroidSqliteDriver(QuranDatabase.Schema, context, QuranDb.FILE, callback = object : AndroidSqliteDriver.Callback(QuranDatabase.Schema) { override fun onCreate(db: SupportSQLiteDatabase) = Unit; override fun onUpgrade(db, old, new) = Unit })` so the framework never creates or migrates. `userVersion(file)` opens the file read-only with `SQLiteDatabase.openDatabase(path, null, OPEN_READONLY)` and reads `PRAGMA user_version`.

iOS actual: destination `NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first()/databases/quran.db`; copy with `NSFileManager` from bytes of `Res.readBytes`; check version by opening once with `NativeSqliteDriver` and `executeQuery("PRAGMA user_version")` (or read bytes 60..63 of the file header, big-endian, which is the SQLite user_version and needs no driver); return `NativeSqliteDriver(DatabaseConfiguration(name = QuranDb.FILE, version = QuranDb.VERSION, create = {}, upgrade = { _, _, _ -> }, extendedConfig = DatabaseConfiguration.Extended(basePath = directoryPath)))`.

`Res.readBytes` is suspend; use `runBlocking` inside the actuals (they are called from `Dispatchers.IO`/`Default` by the repository).

- [ ] **Step 5: Repository**

```kotlin
package world.taqwa.app.quran

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import world.taqwa.app.quran.db.QuranDatabase

class QuranRepository(
    driverFactory: () -> app.cash.sqldelight.db.SqlDriver = ::createQuranDriver,
    private val io: CoroutineDispatcher = ioDispatcher(),
) {
    private val db: QuranDatabase by lazy { QuranDatabase(driverFactory()) }
    private val q get() = db.quranQueries

    suspend fun surahs(): List<Surah> = withContext(io) { q.surahs().executeAsList().map { it.toSurah() } }
    suspend fun surah(number: Int): Surah = withContext(io) { q.surahByNumber(number.toLong()).executeAsOne().toSurah() }
    suspend fun juzs(): List<Juz> = withContext(io) { q.juzs().executeAsList().map { Juz(it.number.toInt(), it.start_surah.toInt(), it.start_ayah.toInt()) } }
    suspend fun ayahs(surah: Int): List<Ayah> = withContext(io) {
        q.ayahsOfSurah(surah.toLong()).executeAsList().map { Ayah(it.surah.toInt(), it.number.toInt(), it.text_uthmani, it.page.toInt(), it.juz.toInt(), it.hizb_quarter.toInt(), it.sajdah.toInt()) }
    }
    suspend fun translations(): List<TranslationInfo> = withContext(io) { q.translations().executeAsList().map { it.toInfo() } }
    suspend fun translationTexts(translationId: String, surah: Int): Map<Int, String> = withContext(io) {
        q.translationOfSurah(translationId, surah.toLong()).executeAsList().associate { it.number.toInt() to it.text }
    }
    suspend fun pageOf(surah: Int, ayah: Int): Int = withContext(io) { q.pageOfAyah(surah.toLong(), ayah.toLong()).executeAsOne().toInt() }
    suspend fun page(number: Int): MushafPage = withContext(io) {
        val header = q.pageHeader(number.toLong()).executeAsOne()
        val words = q.pageWords(number.toLong()).executeAsList().groupBy { it.line.toInt() }
        val lines = q.pageLines(number.toLong()).executeAsList().map { l ->
            MushafLine(
                line = l.line.toInt(),
                type = when (l.type) { "surah" -> LineType.SURAH; "basmala" -> LineType.BASMALA; else -> LineType.TEXT },
                surah = l.surah?.toInt(), text = l.text,
                firstSurah = l.first_surah?.toInt(), firstAyah = l.first_ayah?.toInt(),
                lastSurah = l.last_surah?.toInt(), lastAyah = l.last_ayah?.toInt(),
                endsSurah = l.ends_surah == 1L,
                words = words[l.line.toInt()].orEmpty().map { LineWord(it.position.toInt(), it.surah.toInt(), it.ayah.toInt(), it.word.toInt(), it.text) },
            )
        }
        val juz = q.ayahsOfSurah(header.first_surah).executeAsList().first { it.number == header.first_ayah }.juz.toInt()
        MushafPage(number, header.first_surah.toInt(), header.first_ayah.toInt(), juz, lines)
    }
    suspend fun surahOfPage(number: Int): Surah = withContext(io) { surah(q.pageHeader(number.toLong()).executeAsOne().first_surah.toInt()) }
}

internal expect fun ioDispatcher(): CoroutineDispatcher   // Dispatchers.IO on Android, Dispatchers.Default on iOS
```

Add a dedicated `juzOfAyah` query instead of scanning `ayahsOfSurah` in `page()`: `SELECT juz FROM ayah WHERE surah = ? AND number = ?;` and use it. Mapping helpers `toSurah()` / `toInfo()` are private extension functions in the same file (`revelation` "Makki" → `MAKKI`; `kind` string → `TextKind`).

- [ ] **Step 6: Real-database test (JVM)**

`shared/src/androidUnitTest/kotlin/world/taqwa/app/quran/QuranRepositoryDbTest.kt` using `JdbcSqliteDriver("jdbc:sqlite:" + File("src/commonMain/composeResources/files/quran.db").absolutePath)` (the Gradle test working directory is the module directory; assert the file exists first with a clear message):

```kotlin
class QuranRepositoryDbTest {
    private val repo = QuranRepository(driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + dbFile().absolutePath) }, io = Dispatchers.Unconfined)

    @Test fun hasEverySurahInOrder() = runTest {
        val s = repo.surahs(); assertEquals(114, s.size); assertEquals("Al-Faatiha", s.first().nameLatin); assertEquals(6, s.last().ayahCount)
    }
    @Test fun alBaqarahHas286AyahsAndKursiIsOnPage42() = runTest {
        val a = repo.ayahs(2); assertEquals(286, a.size); assertEquals(42, a.first { it.number == 255 }.page)
    }
    @Test fun noSilentAlefSignSurvivesInText() = runTest {
        assertFalse(repo.ayahs(2).any { '۟' in it.text })
    }
    @Test fun eightBundledTextsWithLicences() = runTest {
        val t = repo.translations(); assertEquals(8, t.size); assertTrue(t.all { it.licence.isNotBlank() })
        assertEquals(TextKind.TRANSLITERATION, t.first { it.id == "en.transliteration" }.kind)
    }
    @Test fun saheehFatihaHasSevenLines() = runTest { assertEquals(7, repo.translationTexts("en.sahih", 1).size) }
    @Test fun pageOneIsFatihaWithHeaderAndSevenTextLines() = runTest {
        val p = repo.page(1); assertEquals(LineType.SURAH, p.lines.first().type); assertEquals(1, p.firstSurah)
        assertEquals(1, p.juz)
    }
    @Test fun pageThreeHasFifteenLinesStartingAtBaqarah6() = runTest {
        val p = repo.page(3); assertEquals(15, p.lines.size); assertEquals(2 to 6, p.firstSurah to p.firstAyah)
        assertTrue(p.lines.all { it.type != LineType.TEXT || it.words.isNotEmpty() })
    }
    @Test fun lastPageEndsAnNas() = runTest { val p = repo.page(604); assertTrue(p.lines.last { it.type == LineType.TEXT }.endsSurah) }
    @Test fun pageOfMapsBothWays() = runTest { assertEquals(2, repo.pageOf(2, 1)); assertEquals(604, repo.pageOf(114, 6)) }
}
```

Run: `./gradlew :shared:testDebugUnitTest --tests '*QuranRepositoryDbTest*' -q` → all pass.

- [ ] **Step 7: Container and full test run**

`AppContainer`: `val quranRepository by lazy { QuranRepository() }`. Run `./scripts/test.sh` (iOS compile proves the native actual builds). Commit: "SQLDelight access to the bundled Quran database".

---

### Task 3: Quran text rules, font, and reading settings

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranText.kt`, `ReadingSettings.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/MushafFont.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt`, `SettingsRepository.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/quran/QuranTextTest.kt`, `ReadingSettingsTest.kt`

- [ ] **Step 1: Failing tests**

```kotlin
class QuranTextTest {
    @Test fun roundelIsArabicIndicDigitsAfterANonBreakingSpace() {
        assertEquals("نص ٢٥٥", QuranText.withMarker("نص", 255))
        assertEquals("نص ٧", QuranText.withMarker("نص", 7))
    }
    @Test fun roundelNeverUsesTheOrnamentOrWordJoiner() {
        val s = QuranText.withMarker("نص", 12); assertFalse('۝' in s); assertFalse('⁠' in s)
    }
    @Test fun searchNormaliserDropsHarakatAndFoldsAlef() {
        assertEquals("الحمد لله", QuranText.normaliseForSearch("ٱلْحَمْدُ لِلَّهِ"))
        assertEquals("ايمان", QuranText.normaliseForSearch("إيمَان"))
    }
}

class ReadingSettingsTest {
    @Test fun englishDefaultsToSaheehAndTranslationMode() {
        val d = ReadingSettings.defaultsFor("en-GB"); assertEquals("en.sahih", d.translationId); assertEquals(ReadingMode.TRANSLATION, d.mode)
    }
    @Test fun arabicDefaultsToMuyassarAndMushaf() {
        val d = ReadingSettings.defaultsFor("ar-LY"); assertEquals("ar.muyassar", d.translationId); assertEquals(ReadingMode.MUSHAF, d.mode)
    }
    @Test fun bundledLanguagesGetTheirOwnTranslationOthersFallBackToEnglish() {
        assertEquals("tr.diyanet", ReadingSettings.defaultsFor("tr-TR").translationId)
        assertEquals("en.sahih", ReadingSettings.defaultsFor("de-DE").translationId)
    }
    @Test fun sizeIsClampedToTheSheetRange() { assertEquals(22, ReadingSettings(arabicSizeSp = 10).clamped().arabicSizeSp); assertEquals(40, ReadingSettings(arabicSizeSp = 99).clamped().arabicSizeSp) }
}
```

- [ ] **Step 2: Implementation**

```kotlin
object QuranText {
    private const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
    private const val NBSP = ' '
    fun arabicIndic(n: Int): String = n.toString().map { ARABIC_INDIC[it - '0'] }.joinToString("")
    /** Spec §5.2: the Hafs font draws bare Arabic-Indic digits as the Mushaf roundel. */
    fun withMarker(text: String, ayah: Int): String = text + NBSP + arabicIndic(ayah)
    private val HARAKAT = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    fun normaliseForSearch(s: String): String = HARAKAT.replace(s, "")
        .replace('ٱ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        .replace(Regex("\\s+"), " ").trim()
}

enum class ReadingMode { TRANSLATION, MUSHAF }

data class ReadingSettings(
    val mode: ReadingMode = ReadingMode.TRANSLATION,
    val arabicSizeSp: Int = 28,
    val transliteration: Boolean = false,
    val translationId: String = "en.sahih",
) {
    fun clamped() = copy(arabicSizeSp = arabicSizeSp.coerceIn(MIN_SIZE, MAX_SIZE))
    companion object {
        const val MIN_SIZE = 22; const val MAX_SIZE = 40; const val SIZE_STEP = 2
        private val byLanguage = mapOf("en" to "en.sahih", "ar" to "ar.muyassar", "id" to "id.indonesian", "ur" to "ur.junagarhi", "bn" to "bn.bengali", "tr" to "tr.diyanet", "fr" to "fr.hamidullah")
        fun defaultsFor(languageTag: String): ReadingSettings {
            val lang = languageTag.substringBefore('-').lowercase()
            return ReadingSettings(
                mode = if (lang == "ar") ReadingMode.MUSHAF else ReadingMode.TRANSLATION,
                translationId = byLanguage[lang] ?: "en.sahih",
            )
        }
    }
}

data class ReadingPosition(val surah: Int, val ayah: Int, val page: Int)
```

`MushafFont.kt`: `@Composable fun mushafFamily() = FontFamily(Font(Res.font.uthmanic_hafs))` and `fun TaqwaText.quran(sizeSp: Int): TextStyle` returning `TextStyle(fontSize = sizeSp.sp, lineHeight = (sizeSp * 2).sp, textAlign = TextAlign.Right, lineHeightStyle = LineHeightStyle(Alignment.Center, Trim.None))` (family applied at the call site since `mushafFamily()` is composable).

`SettingsKeys`: `QURAN_MODE`, `QURAN_SIZE`, `QURAN_TRANSLITERATION`, `QURAN_TRANSLATION`, `QURAN_LAST_SURAH`, `QURAN_LAST_AYAH`, `QURAN_LAST_PAGE`. `SettingsRepository`: `fun readingSettings(defaultLanguageTag: String): Flow<ReadingSettings>` (defaults from `ReadingSettings.defaultsFor` when keys are absent), `suspend fun setReadingSettings(s: ReadingSettings)`, `val readingPosition: Flow<ReadingPosition?>`, `suspend fun setReadingPosition(p: ReadingPosition)`.

- [ ] **Step 3: Run tests, commit** "Quran text rules, Mushaf font, reading settings".

---

### Task 4: Navigation and the tab bar

**Files:**
- Modify: `nav/Tab.kt`, `nav/Screen.kt`, `design/components/TaqwaTabBar.kt`, `App.kt`, `commonTest/.../nav/NavigatorTest.kt`, strings

- [ ] **Step 1: Test first** — in `NavigatorTest`, roots become `listOf(Screen.Today, Screen.Quran, Screen.Settings)`; add `Screen.Reader(2, 255)` and `Screen.Mushaf(42)` to the non-root list; add a test that `Navigator(Screen.Today).apply { selectTab(Tab.QURAN); push(Screen.Reader(2, 1)); push(Screen.Mushaf(2)) }.currentTab == Tab.QURAN`.

- [ ] **Step 2: Screens and tabs**

```kotlin
data object Quran : Screen
data class Reader(val surah: Int, val ayah: Int) : Screen
data class Mushaf(val page: Int) : Screen
```

`Tab`: `PRAYER(Screen.Today), QURAN(Screen.Quran), SETTINGS(Screen.Settings)`; update the KDoc (three tabs now, the Quran one arrived with slice 2).

- [ ] **Step 3: Tab bar** — `IconSize = 22.dp`, label `12.sp`, spacer `5.dp`, `BarHeight = 60.dp`, `tint = if (selected) colors.accent else colors.textSecondary`; add `drawBook(tint)` from the 16-unit path used in the design mockups:

```kotlin
private fun DrawScope.drawBook(tint: Color) {
    val u = size.width / 16f
    val pages = Path().apply {
        moveTo(8f * u, 3.2f * u)
        cubicTo(6.6f * u, 2.1f * u, 4.4f * u, 1.9f * u, 1.8f * u, 2.4f * u)
        lineTo(1.8f * u, 12.8f * u)
        cubicTo(4.4f * u, 12.3f * u, 6.6f * u, 12.5f * u, 8f * u, 13.7f * u)
        cubicTo(9.4f * u, 12.5f * u, 11.6f * u, 12.3f * u, 14.2f * u, 12.8f * u)
        lineTo(14.2f * u, 2.4f * u)
        cubicTo(11.6f * u, 1.9f * u, 9.4f * u, 2.1f * u, 8f * u, 3.2f * u)
        close()
    }
    drawPath(pages, tint, style = glyphStroke())
    drawLine(tint, Offset(8f * u, 3.2f * u), Offset(8f * u, 13.7f * u), strokeWidth = size.width * 0.0875f, cap = StrokeCap.Round)
}
```

Strings: `tab_quran` = "Quran" / "القرآن".

- [ ] **Step 4: App.kt** — route `Screen.Quran`, `Screen.Reader`, `Screen.Mushaf` to placeholder composables that show the screen name (replaced in Tasks 5, 6, 8) so the app builds; back handling unchanged (non-Prayer roots fall back to Prayer). Run tests, build the Android app, screenshot the bar on the emulator in light mode to confirm 22 dp glyphs and the unselected colour, commit "Quran tab and bigger tab glyphs".

---

### Task 5: The tab root

**Files:**
- Create: `feature/quran/QuranRootScreen.kt`, `QuranRootViewModel.kt`
- Modify: `App.kt`, strings
- Test: `commonTest/.../feature/quran/QuranRootViewModelTest.kt` (with a fake `QuranRepository` — make the repository an interface `QuranSource` implemented by `QuranRepository`, so tests can fake it without a database)

**Behaviour (spec §2.1):** state = `Loading | Ready(surahs, juzs, continueCard: ContinueCard?, filter: String, tab: SurahOrJuz)`; `ContinueCard(surah: Surah, ayah: Int, mode: ReadingMode)` from `readingPosition`; `filter` matches `nameLatin`, `nameArabic`, `meaning`, or `number.toString()`, case-insensitive, ignoring harakat on the Arabic via `QuranText.normaliseForSearch`. Tapping a surah → `Reader(surah, 1)` or `Mushaf(pageOf(surah,1))` by current `mode`; tapping a juz → the juz start likewise; tapping continue → last position in stored mode.

**Layout:** `SettingsScaffold(title = "Quran", onBack = null)` for the title treatment, then: filter field (a `TaqwaCard`-styled row with the magnifier drawn in `Canvas`, `BasicTextField` inside, 44 dp tall), continue card (`TaqwaCard`, `SectionLabel`-style label inside, progress hairline 3 dp with `colors.accent` fill), segmented control (reuse the pill style from `SoundSheet`/onboarding if one exists, else a small `TaqwaSegmented(options, selected, onSelect)` component added to `design/components`), then the list card with rows. Surah row: 30 dp roundel (`border 1.dp hairline, CircleShape`, number in `caption` 12 sp semibold `textSecondary`), name `rowLabel`, subtitle 12 sp `textSecondary` "The Cow · 286 ayahs · Madani" (format string `quran_surah_subtitle` = "%1$s · %2$s ayahs · %3$s"), Arabic name in `mushafFamily()` 20 sp. Row height 56 dp minimum, `CardDivider` between rows, whole row clickable with `indication = null`.

Strings (en / ar): `quran_title` "Quran"/"القرآن", `quran_search_hint` "Search surah"/"ابحث عن سورة", `quran_continue` "CONTINUE READING"/"متابعة القراءة", `quran_continue_detail` "Ayah %1$s of %2$s · Juz %3$s"/"الآية %1$s من %2$s · الجزء %3$s", `quran_tab_surah` "Surah"/"السور", `quran_tab_juz` "Juz"/"الأجزاء", `quran_surah_subtitle`, `quran_makki` "Makki"/"مكية", `quran_madani` "Madani"/"مدنية", `quran_juz_n` "Juz %1$s"/"الجزء %1$s", `quran_juz_range` "%1$s %2$s to %3$s %4$s"/"من %1$s %2$s إلى %3$s %4$s".

Commit "Quran tab root: continue card, surah and juz lists".

---

### Task 6: Translation mode reader

**Files:**
- Create: `feature/quran/ReaderHeader.kt`, `ReaderScreen.kt`, `ReaderViewModel.kt`, `AyahCard.kt`
- Modify: `App.kt`, strings
- Test: `ReaderViewModelTest.kt` (fake source): loads ayahs + translation + transliteration for a surah; `onFirstVisibleAyah(n)` updates position after debounce; `nextSurah` is null after 114; basmala shown for surah 2, not for 1 or 9.

**Header (spec §2.2):** `ReaderHeader(title, caption, mushafSelected, onBack, onToggleMode, onOpenSheet)`: `BackChevron` (already internal in `feature.settings`), title `rowLabel` weight ExtraBold, caption 12 sp secondary, two 36 dp round buttons with `indication = null`; the book button gets `background(colors.accent.copy(alpha = .18f), CircleShape)` and accent tint when `mushafSelected`.

**Body (spec §2.3):** `LazyColumn` with `contentPadding` 24 dp horizontal; item 0 basmala (`Text(basmala, fontFamily = mushafFamily(), fontSize = 24.sp, textAlign = Center)` — the basmala string is `ayahs(1).first().text` read from the database at load time, never a literal); then `AyahCard(ayah, transliteration?, translation, sizeSp)`; last item the "next surah" card. `AyahCard`:

```kotlin
@Composable
fun AyahCard(text: String, ayahNumber: Int, transliteration: String?, translation: String?, sizeSp: Int) {
    val colors = LocalTaqwaColors.current
    TaqwaCard {
        Column(Modifier.padding(14.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    buildAnnotatedString {
                        append(QuranText.withMarker(text, ayahNumber).dropLast(QuranText.arabicIndic(ayahNumber).length))
                        withStyle(SpanStyle(color = colors.accent)) { append(QuranText.arabicIndic(ayahNumber)) }
                    },
                    style = TaqwaText.quran(sizeSp), fontFamily = mushafFamily(), color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (transliteration != null) { Spacer(Modifier.height(6.dp)); Text(transliteration, style = TaqwaText.caption.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic), color = colors.textTertiary) }
            if (translation != null) { Spacer(Modifier.height(8.dp)); Text(translation, style = TaqwaText.caption, color = colors.textSecondary, lineHeight = 21.sp) }
        }
    }
}
```

(Build the annotated string more simply: `append(text); append(' '); withStyle(accent) { append(digits) }`; the `dropLast` form above is only to show the intent. Transcribe the simple form.)

**Position:** `rememberLazyListState`; `snapshotFlow { listState.firstVisibleItemIndex }` → `viewModel.onFirstVisibleAyah`, debounced 500 ms in the view model → `settings.setReadingPosition(ReadingPosition(surah, ayah, pageOf(surah, ayah)))` and the caption "Juz n · Page p" from that ayah. Opening at `ayah > 1`: `listState.scrollToItem(index = ayah /* item 0 is the basmala */)` once after load. Mode toggle → `onToggleMode` navigates `replace` (pop then push) to `Screen.Mushaf(pageOf(surah, currentAyah))` and stores `mode = MUSHAF`. Under Arabic UI the title is `surah.nameArabic` in `mushafFamily()` 20 sp.

Strings: `quran_next_surah` "Next: %1$s"/"التالية: %1$s", `quran_juz_page` "Juz %1$s · Page %2$s"/"الجزء %1$s · صفحة %2$s".

Commit "Quran reader: translation mode".

---

### Task 7: Reading settings sheet

**Files:**
- Create: `feature/quran/ReadingSheet.kt`
- Modify: `ReaderScreen.kt`, strings; later `MushafScreen.kt` (Task 8) opens the same sheet

Use `ModalBottomSheet` from material3 with `containerColor = colors.surface`, `dragHandle` drawn as the 36×4 dp pill in `colors.hairline`, `shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`. Contents (spec §2.5): size row (`TaqwaRow`-like label "Arabic size" and value), `Slider(value, onValueChange, valueRange = 22f..40f, steps = 8, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = hairline))`; preview `Text(previewAyah, mushafFamily(), size)` where `previewAyah` = ayah 1:2 text with marker, from the database; `TaqwaToggle` row for transliteration; translation row that expands (state) into the `translations()` list minus transliteration, each a `TaqwaRow(name, subtitle = translator, selectable = true, trailing = CheckMark if selected)`; mode row with a two-option segmented control. Every change writes through `settings.setReadingSettings` immediately. In Mushaf mode the slider is disabled (alpha .4) with the note `quran_size_mushaf_note` "Page size follows the width in Mushaf mode."/"حجم الصفحة يتبع عرض الشاشة في وضع المصحف."

Strings: `quran_sheet_size` "Arabic size"/"حجم النص العربي", `quran_sheet_transliteration` "Transliteration"/"النطق بالحروف اللاتينية", `quran_sheet_translation` "Translation"/"الترجمة", `quran_sheet_mode` "Reading mode"/"وضع القراءة", `quran_mode_translation` "Translation"/"ترجمة", `quran_mode_mushaf` "Mushaf"/"مصحف".

Commit "Reading settings sheet".

---

### Task 8: Mushaf mode

**Files:**
- Create: `feature/quran/MushafScreen.kt`, `MushafViewModel.kt`, `MushafPageView.kt`
- Modify: `App.kt`
- Test: `MushafViewModelTest.kt` (fake source): page N loads lines; `onPageShown(n)` stores position (first text line's ayah, page n); `toggleToReader()` yields `Reader(firstSurah, firstAyah)`; the title for page 3 is Al-Baqarah.

**Pager:** `HorizontalPager(state = rememberPagerState(initialPage = startPage - 1) { 604 }, beyondViewportPageCount = 1)` wrapped in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` so page N+1 is to the left in both UI directions. Each page composes `MushafPageView(page)`, loading from `viewModel.page(n)` (cached in a `LruCache`-like map of the last 5 pages).

**MushafPageView (spec §2.4):**

```kotlin
@Composable
fun MushafPageView(page: MushafPage, surahOf: (Int) -> Surah?, highlighted: Pair<Int, Int>?, onTapAyah: (Int, Int) -> Unit) {
    val colors = LocalTaqwaColors.current
    val family = mushafFamily()
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val frameWidth = maxWidth - 2 * 14.dp
        val baseSp = (28f * (frameWidth.value / 347f)).coerceIn(20f, 34f)   // 375 dp screen - 28 dp = 347 dp frame
        Column(Modifier.fillMaxSize()) {
            PageHeader(page)   // juz and surah name, 12 sp secondary, RTL row
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
                    .padding(4.dp).border(1.dp, colors.hairline, RoundedCornerShape(15.dp))
                    .background(colors.surface, RoundedCornerShape(15.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = if (page.number <= 2) Arrangement.Center else Arrangement.SpaceEvenly,
            ) {
                page.lines.forEach { line ->
                    when (line.type) {
                        LineType.SURAH -> SurahBand(surahOf(line.surah!!))
                        LineType.BASMALA -> BasicText(basmalaText, style = TextStyle(fontFamily = family, fontSize = baseSp.sp, textAlign = TextAlign.Center, color = colors.textPrimary), modifier = Modifier.fillMaxWidth())
                        LineType.TEXT -> MushafTextLine(line, family, baseSp, justify = !line.endsSurah && page.number > 2, highlighted, onTapAyah)
                    }
                }
            }
            Text(QuranText.arabicIndic(page.number), style = TaqwaText.caption, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        }
    }
}
```

`MushafTextLine`: one `BasicText` per line with `autoSize = TextAutoSize.StepBased(minFontSize = (baseSp * 0.7f).sp, maxFontSize = baseSp.sp, stepSize = 0.5.sp)`, `maxLines = 1`, `softWrap = false`, `TextStyle(fontFamily, textAlign = if (justify) TextAlign.Justify else TextAlign.Start, textDirection = TextDirection.Rtl, color = textPrimary)`; the text is `line.text` with the ayah digits coloured accent through `buildAnnotatedString` (split each word: digits at the end of a word get `SpanStyle(color = accent)`), and the highlighted ayah's words get `SpanStyle(background = accent.copy(alpha = .16f))`. Tap: `Modifier.pointerInput(line) { detectTapGestures { offset -> layout?.getOffsetForPosition(offset)?.let { idx -> word at that character index → onTapAyah(word.surah, word.ayah) } } }` using `onTextLayout` to keep the `TextLayoutResult`. If `TextAutoSize` is not resolvable in CMP 1.12 on iOS (check by compiling), fall back to measuring with `rememberTextMeasurer()` and shrinking `fontSize` in 0.5 sp steps until `size.width <= constraints.maxWidth` — write it once as `fitOneLine()` and keep the same call shape.

`SurahBand(surah)`: a 40 dp hairline-framed row (`RoundedCornerShape(12.dp)`), Arabic name centred in `family` at `baseSp * 0.85`, ayah count in Arabic-Indic on one side and Makki/Madani on the other in 11 sp `textSecondary`.

`PageHeader`: `Row(SpaceBetween)`: "الجزء ١" style juz label on the start side, surah name (Arabic in `family` 15 sp under Arabic UI; Latin `caption` otherwise) on the end side.

**Header and position:** the same `ReaderHeader` with `mushafSelected = true`; title = surah of the current page's first text line; caption "Juz n · Page p". `onToggleMode` → `Reader(firstSurah, firstAyah)` and `mode = TRANSLATION`. `snapshotFlow { pagerState.settledPage }` → `onPageShown(page + 1)` → position saved. `Aa` opens `ReadingSheet` with the slider disabled.

**Tap pill:** when `highlighted != null`, show a small pill at the bottom of the frame ("2:255" in `caption`, accent border) that clears on tap; actions come in 2b.

Run `./scripts/test.sh`, build Android, open page 1, 2, 3, 42, 604 on the emulator in both themes and check: no line wraps, roundels inline, header and footer right, band on page 1 and at surah starts, RTL page order. Commit "Quran reader: Mushaf mode".

---

### Task 9: Attribution, docs, strings audit

**Files:**
- Modify: `feature/settings/AttributionScreen.kt`, `docs/ATTRIBUTION.md`, `README.md` (features list gains the Quran reader; roadmap row for slice 2 → "In progress" / "Done"), `docs/BUILD-LOG.md` (new section "Slice 2a — Quran reader" with what was built, the pipeline, the spike carry-overs), strings

Attribution rows (spec §6): Quran text (Tanzil, CC BY-ND 3.0, source `tanzil.net`), Translations (one row per `translation` table entry read via `quranRepository.translations()`: name, translator, "via Tanzil, non-commercial"), Quran typeface (King Fahd Glorious Quran Printing Complex, "KFGQPC Uthmanic Script Hafs, free to use and distribute unmodified"), Page layout (Quranic Universal Library data via `github.com/zonetecde/mushaf-layout`). Strings for each `credit_*` in both languages. Grep both string files for `—` and `–` and remove any.

Commit "Attribution and docs for the Quran reader".

---

### Task 10: Device round

- Build Android (`./gradlew :androidApp:assembleDebug -q`) and install on every attached Android device (`~/platform-tools/adb devices`); build iOS for the device with the documented `xcodebuild` command and install on the iPhone if attached; otherwise the simulator via `./scripts/ios-build.sh`.
- Capture, in English and Arabic, light and dark: tab root (with a continue card after opening a surah), reader at 2:255 with transliteration on, the sheet, Mushaf pages 1, 3 and 42, the tab bar. Check the iOS resource copy on first launch (time it; must be under 2 s on the iPhone 12) and that a second launch does not copy again.
- Record findings and screenshots in `docs/BUILD-LOG.md`; fix anything visibly wrong in small follow-up commits on the same task.

---

## Self-review notes

- Spec coverage: §2.1 Task 5; §2.2 Task 6 (header) and 8; §2.3 Task 6; §2.4 Task 8; §2.5 Task 7; §2.6 Task 4; §3 Tasks 1–2; §3.5 Task 3; §4 Tasks 2–8; §5 Tasks 3, 6, 8; §6 Task 9; §7 Tasks 1, 2, 3, 5, 6, 8, 10.
- Names used across tasks: `QuranRepository` (2, 5–8), `QuranText.withMarker` / `arabicIndic` (3, 6, 8), `ReadingSettings` / `ReadingMode` / `ReadingPosition` (3, 5–8), `mushafFamily()` and `TaqwaText.quran` (3, 5–8), `ReaderHeader` (6, 8), `Screen.Reader` / `Screen.Mushaf` (4–8), `BackChevron` (existing, 6).
- Known open point: the `QuranSource` interface for fakes is introduced in Task 5; Task 2's tests use the real class. If the implementer of Task 2 prefers, introduce the interface there.
