# Taqwa — Slice 2: Quran reader. Design spec

Status: **agreed 7 September 2026** after the investigation (`2026-09-07-quran-text-audio-investigation.md`, §11 decisions, §12 spike) and the design round (https://claude.ai/code/artifact/5748bf9a-e0c0-492f-8205-93604f6efb68). This document is the contract the implementation plan is written against. Slice 3 (audio) has its own spec later; slice 2b (search, bookmarks, share) extends this one.

## 1. What ships

A third tab, **Quran** (Arabic: القرآن), holding an offline Quran with two reading modes:

- **Translation mode**: one surah at a time, each ayah a card with the Uthmani Arabic, an optional transliteration line, and the chosen translation.
- **Mushaf mode**: the 604 pages of the Madinah Mushaf, one page per screen, lines breaking exactly where the printed page breaks, ordered like an Arabic book: the next page sits to the left, so a swipe from left to right turns forward.

Plus: a tab root with a continue-reading card, the surah and juz lists, and a name filter; a reading-settings sheet; last-read position; attribution for every bundled text; the tab bar fix (bigger glyphs, a non-disabled unselected colour). Everything bundled in the app; no network.

Explicitly not in slice 2a: full-text search, bookmarks, sharing and copying, audio, word-by-word, tajweed colouring, footnotes. Search, bookmarks and share are slice 2b.

## 2. Screens

### 2.1 Tab root ("Root A")

Top to bottom, inside the standard tab scaffold with a large "Quran" title:

1. A **filter field** styled like a search field (hairline card, magnifier glyph, placeholder "Search surah"). In 2a it filters the surah list as you type, matching the Latin name, the Arabic name, the English meaning and the surah number. In 2b it becomes the entry to full search.
2. A **Continue reading** card, only when a last-read position exists: section label CONTINUE READING, surah name (Latin), "Ayah 28 of 110 · Juz 15", the Arabic name in the Mushaf font on the trailing side, and a 3 dp progress hairline (ayah / ayah count). Tapping opens the reader in the last-used mode at that ayah.
3. A **Surah | Juz** segmented control in the app's pill style.
4. **Surah list**: one card, 114 rows. Row: number in a 30 dp hairline roundel, Latin name (17 sp semibold), subtitle "The Cow · 286 ayahs · Madani" (12 sp secondary), Arabic name in the Mushaf font (20 sp) on the trailing side. Tap opens the reader at ayah 1 in the last-used mode.
5. **Juz list** (when Juz is selected): 30 rows, "Juz 1", subtitle "Al-Fatiha 1 to Al-Baqarah 141", Arabic "الجزء ١" trailing. Tap opens the reader at the juz start.

### 2.2 Reader header (shared by both modes)

A compact header, 56 dp: back chevron (the settings one), title (17 sp extra-bold) = surah Latin name in translation mode or the surah of the current page's first line in Mushaf mode, caption (12 sp secondary) "Juz 1 · Page 2", then two 36 dp round icon buttons: **book** (toggles mode; drawn filled with the accent halo when in Mushaf mode) and **Aa** (opens the reading sheet). Under Arabic the title is the Arabic surah name in the Mushaf font.

The mode toggle preserves position: translation → Mushaf opens the page containing the first fully visible ayah; Mushaf → translation opens the surah and ayah of the page's first text line. The mode chosen becomes the default for the next open.

### 2.3 Translation mode ("Reader B")

- **Basmala** centred in the Mushaf font (24 sp) above ayah 1 for every surah except Al-Fatiha (where it is ayah 1) and At-Tawbah (which has none).
- **Ayah cards**: a `TaqwaCard` per ayah, 12 dp vertical gap, inner padding 14 dp. Inside, top to bottom: Arabic (Mushaf font, size from settings, default 28 sp, line height 2.0×, right-aligned, RTL) with the ayah roundel inline (see §5.2); transliteration (13 sp italic tertiary) when enabled; translation (14 sp secondary, line height 1.5). Cards are the whole list; nothing else between them.
- **End of surah**: a card "Next: Aal-i-Imraan" that opens the next surah; none after An-Nas.
- **Position**: the first ayah whose card top is on screen is the current ayah; it is saved (debounced 500 ms) as last-read, together with the surah and mode.
- **Jump-to-ayah**: opening at a given ayah scrolls that card to the top with 8 dp offset.

### 2.4 Mushaf mode ("Mushaf B")

- **Pager** over 604 pages; page N+1 is to the left of page N (a right-to-left book), regardless of UI language.
- **Page frame**: a hairline rounded rectangle (18 dp) on the surface colour, 16 dp inside the gutter, with a double hairline inset like the printed frame, containing exactly the page's lines. Above the frame, a 12 sp secondary line with the juz on one side and the surah name on the other (mirrored for RTL as the header text direction dictates). Below, the page number in Arabic-Indic digits, centred.
- **Lines**: 15 per page (pages 1 and 2 have fewer and are vertically centred). Line types: `surah` (a band: hairline-framed row with the surah name in the Mushaf font centred, ayah count and Makki/Madani in 11 sp on the sides), `basmala` (centred), `text` (the words of that printed line). Text lines are set **justified** to the frame width; a line that ends a surah and the lines of pages 1 and 2 are not justified but aligned to the start. **One font size per page**: the page's base size is derived from the frame width (28 sp at 375 dp, scaling linearly) and the whole page is set at the largest half-step at or below that base at which its widest line fits the frame, so every line of a page reads at the same size (a line-by-line auto-shrink was tried first and read as a page of mixed sizes). Justification is by word spacing: each text line's words are measured individually and the line's slack is divided evenly between the gaps, the way a printed line is filled; the natural gap of an unjustified line is what the font itself puts between the words. No line ever wraps.
- **Ayah roundel**: the layout data ends each ayah's last word with bare Arabic-Indic digits; rendered by the Hafs font as the roundel. A non-breaking space precedes them (§5.2).
- **Tap** on a line highlights the ayah that word belongs to (soft accent field behind all its words on that page) and shows a small pill with the reference "2:255"; tapping again clears. Actions on the pill arrive in 2b.
- **Position**: the page shown is saved as last-read (its first text line's ayah).
- **Size**: the reading-sheet slider does not apply in Mushaf mode; the page size is fixed by width so lines keep their breaks. The sheet shows the slider disabled with a one-line note in Mushaf mode.

### 2.5 Reading settings sheet

A modal bottom sheet (`TaqwaBottomSheet`: the app's background colour, no tonal tint, the hairline grab handle; the same component hosts every sheet in the app), opened by Aa:

1. **Arabic size**: slider 22 to 40 sp in 2 sp steps, value shown, live preview line (Al-Fatiha ayah 2 in the Mushaf font at that size).
2. **Transliteration**: `TaqwaToggle`. Off by default.
3. **Translation**: a row with the current name (or "Off"); tapping expands a list whose first entry is **Off** (Arabic only, every ayah still on its own card; stored as the sentinel id `none`, never fallen back from) followed by the bundled translations (name, translator, language in the UI language) with a check mark on the current one.
4. **Reading mode**: Translation | Mushaf segmented control; switching applies immediately and closes the sheet.

Defaults: English UI → Saheeh International, Translation mode, transliteration off. Arabic UI → Tafsir al-Muyassar, Mushaf mode, transliteration off. Under other languages the bundled translation for that language if there is one (Indonesian, Urdu, Bengali, Turkish, French), else Saheeh International.

### 2.6 Tab bar

Three tabs: Prayer (mihrab), Quran (open book), Settings (sliders; the gear never sat with the other two). Glyphs 22 dp, label 12 sp semibold, 5 dp between them, bar content height 60 dp plus the navigation inset. Selected: accent. Unselected: **secondary text colour** (`textSecondary`), not tertiary. Back from Quran or Settings root goes to Prayer, as today.

## 3. Data

### 3.1 Sources

| Data | Source | Licence | Notes |
|---|---|---|---|
| Uthmani text | Tanzil v1.1, `quranType=uthmani`, pause marks and sajdah and rub signs on, no tatweel | CC BY-ND 3.0 | 6,236 ayahs, `s|a|text` format |
| Search text | Tanzil `simple-clean` | CC BY-ND 3.0 | For 2b's search; stored now |
| Metadata | Tanzil `quran-data.xml` | same | suras (name, tname, ename, type, ayas, start), juzs, hizb quarters, pages, sajdas |
| Translations | Tanzil: `en.sahih`, `ar.muyassar`, `id.indonesian`, `ur.junagarhi`, `bn.bengali`, `tr.diyanet`, `fr.hamidullah` | non-commercial, credit translator, link to tanzil.net/trans | one ayah per line, 6,236 lines |
| Transliteration | Tanzil `en.transliteration` | same | carries `<u>` and `<b>` emphasis tags, which are stripped |
| Page layout | `zonetecde/mushaf-layout`, `mushaf/page-001.json` to `page-604.json` (Madinah Mushaf 1421H layout as published by QUL) | layout facts of the printed Mushaf; word text is Tanzil's | 15 lines per page; line types `surah-header`, `basmala`, `text`; words with `location` "s:a:w" and Unicode `word` |
| Font | KFGQPC Uthmanic Script Hafs v22 (`UthmanicHafs_V22.ttf`) | KFGQPC: free to use and distribute unmodified | bundled as `uthmanic_hafs.ttf` |

### 3.2 Text rules (applied once, in the pipeline)

1. Strip the UTF-8 BOM and trailing whitespace from every source line.
2. Map **U+06DF → U+0652** in the Uthmani text and in every layout word (the Hafs font draws U+06DF as an inline ring that splits the word; spike §12).
3. Verify per ayah that the layout's words, joined with spaces and with the trailing ayah digits removed from the last word, equal the Tanzil Uthmani text with the standalone sign tokens (rub-el-hizb ۞ U+06DE and sajdah ۩ U+06E9, and pause marks U+06D6–U+06DC when they are separate tokens) accounted for. Any mismatch fails the build and prints the ayah.
4. Transliteration: remove `<u>`, `</u>`, `<b>`, `</b>` (case-insensitive) and collapse double spaces.
5. Tanzil prefixes the basmala to ayah 1 of every surah except Al-Fatiha and At-Tawbah; the pipeline strips that leading basmala (matched on bare letters, since surahs 95 and 97 spell it with a different shadda placement) from `text_uthmani` and `text_search`, because the app renders the basmala as its own line. 27:30, where the basmala sits mid-ayah, is untouched.
6. Ayah roundels are **not** stored in the ayah text. They are added at render time (§5.2). The layout words keep their trailing digits, attached with a non-breaking space (the same separator `QuranText.withMarker` uses), because a Mushaf word is drawn as one unit.
7. **Every Mushaf word's text is Tanzil's, not the layout's.** The layout is trusted only for segmentation (which word sits on which line); its word text encodes the sequential-tanween forms with extra small-meem signs (U+06E2/U+06ED, 6,642 words) that the Hafs font draws as a literal small meem the printed page does not show. After rule 3 passes, each layout word is re-texted from Tanzil's tokens, aligned by bare letters (a layout word may span two tokens: "بَعْدَ مَا", "إِلْ يَاسِينَ"); Tanzil's standalone pause marks and the sajdah sign are fused onto the word before them and the rub-el-hizb star onto the word after, space kept, so the stored string stays character-for-character Tanzil's. The build then verifies, for all 6,236 ayahs, that the Mushaf words joined with spaces equal `ayah.text_uthmani` exactly, that every character is in the Quranic code-point set, and (with `uharfbuzz` installed) that every stored line, word and ayah shapes with the bundled Hafs font without a missing glyph or a dotted circle.

8. **Latin surah names are curated, not Tanzil's.** `surah.name_en` carries the spellings most readers meet ("Al-Fatihah", "Ali 'Imran", "Al-Mursalat"; the list is `LATIN_NAMES` in the pipeline), and `surah.aliases_en` keeps Tanzil's own spelling plus common other names ("Yaseen", "Tabarak") for the filter, which folds doubled vowels and a final "h" on both sides so "Mursalat", "Al-Mursalaat", "Baqara" and "Baqarah" all match. Names are metadata, not Quran text; the Arabic names stay exactly as Tanzil gives them.
### 3.3 Database

One SQLite file, `shared/src/commonMain/composeResources/files/quran.db`, built by `tools/build-quran-db.py`, `PRAGMA user_version = 3` (1 shipped the layout's own word text; 2 re-texts every Mushaf word from Tanzil, rule 7; 3 curates the Latin surah names, see below), vacuumed, journal off. Schema:

```sql
CREATE TABLE surah (
  number INTEGER PRIMARY KEY, name_ar TEXT NOT NULL, name_en TEXT NOT NULL,
  aliases_en TEXT NOT NULL,                             -- other Latin spellings, '|'-separated, for the filter only
  meaning_en TEXT NOT NULL, revelation TEXT NOT NULL,   -- 'Makki' | 'Madani'
  ayah_count INTEGER NOT NULL, start_page INTEGER NOT NULL, start_juz INTEGER NOT NULL
);
CREATE TABLE ayah (
  surah INTEGER NOT NULL, number INTEGER NOT NULL,
  text_uthmani TEXT NOT NULL, text_search TEXT NOT NULL,
  page INTEGER NOT NULL, juz INTEGER NOT NULL, hizb_quarter INTEGER NOT NULL,
  sajdah INTEGER NOT NULL DEFAULT 0,                    -- 0 none, 1 recommended, 2 obligatory
  PRIMARY KEY (surah, number)
);
CREATE TABLE juz (number INTEGER PRIMARY KEY, start_surah INTEGER NOT NULL, start_ayah INTEGER NOT NULL);
CREATE TABLE translation (
  id TEXT PRIMARY KEY, language TEXT NOT NULL, name TEXT NOT NULL, translator TEXT NOT NULL,
  licence TEXT NOT NULL, source_url TEXT NOT NULL, kind TEXT NOT NULL      -- 'translation' | 'tafsir' | 'transliteration'
);
CREATE TABLE ayah_translation (
  translation_id TEXT NOT NULL, surah INTEGER NOT NULL, number INTEGER NOT NULL, text TEXT NOT NULL,
  PRIMARY KEY (translation_id, surah, number)
);
CREATE TABLE page (number INTEGER PRIMARY KEY, first_surah INTEGER NOT NULL, first_ayah INTEGER NOT NULL);
CREATE TABLE page_line (
  page INTEGER NOT NULL, line INTEGER NOT NULL,
  type TEXT NOT NULL,                                   -- 'surah' | 'basmala' | 'text'
  surah INTEGER,                                        -- for 'surah' lines
  text TEXT,                                            -- the full line for 'text' lines
  first_surah INTEGER, first_ayah INTEGER,              -- first word's ayah, for 'text' lines
  last_surah INTEGER, last_ayah INTEGER,
  ends_surah INTEGER NOT NULL DEFAULT 0,                -- 1 when the line's last word ends a surah
  PRIMARY KEY (page, line)
);
CREATE TABLE line_word (
  page INTEGER NOT NULL, line INTEGER NOT NULL, position INTEGER NOT NULL,   -- position within the line, 1-based
  surah INTEGER NOT NULL, ayah INTEGER NOT NULL, word INTEGER NOT NULL,      -- word index within the ayah
  text TEXT NOT NULL,
  PRIMARY KEY (page, line, position)
);
CREATE VIRTUAL TABLE ayah_fts USING fts5(text_search, content='ayah', content_rowid='rowid');
```

Size target: under 40 MB uncompressed. The FTS table is filled now so 2b needs no rebuild.

### 3.4 Access in the app

SQLDelight 2.2.1 with the `sqlite-3-38` dialect, drivers `android-driver`, `native-driver`, `sqlite-driver` (JVM tests). The bundled file is copied from compose resources to the app's database directory on first launch or when the stored `user_version` differs from the app's `QuranDb.VERSION`; the schema is never created by the driver. `QuranRepository` exposes suspend functions and returns plain domain types; all queries run on `Dispatchers.IO` (Android) / `Dispatchers.Default` (iOS).

### 3.5 Preferences (DataStore, beside the existing keys)

`quran_mode` (`TRANSLATION` | `MUSHAF`), `quran_size` (Int, 28), `quran_transliteration` (Boolean, false), `quran_translation` (String, locale default), `quran_last_surah`, `quran_last_ayah`, `quran_last_page` (Int, absent until first read). Snake case, matching every other key in `SettingsKeys`.

## 4. Architecture

- `shared/src/commonMain/kotlin/world/taqwa/app/quran/` — domain: `Surah`, `Ayah`, `Juz`, `TranslationInfo`, `MushafPage`, `MushafLine`, `LineWord`, `ReadingSettings`, `ReadingMode`, `ReadingPosition`, `QuranText` (roundel rule, digit conversion), `QuranRepository`, `QuranDb` (version constant, `QuranDatabaseProvider` expect/actual).
- `shared/src/commonMain/sqldelight/world/taqwa/app/quran/db/Quran.sq` — the queries.
- `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/` — `QuranRootScreen` + `QuranRootViewModel`, `ReaderScreen` + `ReaderViewModel`, `MushafScreen` + `MushafViewModel`, `ReadingSheet`, `AyahCard`, `MushafPageView`, `SurahBand`, `ReaderHeader`.
- `nav`: `Tab.QURAN(Screen.Quran)` between PRAYER and SETTINGS; `Screen.Quran`, `Screen.Reader(surah: Int, ayah: Int)`, `Screen.Mushaf(page: Int)` as data classes.
- `design`: `MushafFont` (font family from `Res.font.uthmanic_hafs`), `TaqwaText.quran(sizeSp)`; tab bar changes in `TaqwaTabBar`.
- `AppContainer` gains `quranRepository` (lazy: the copy happens on first use, off the main thread).
- `tools/build-quran-db.py` (Python 3.11+, standard library only) with `tools/cache/` git-ignored.

## 5. Typography rules

### 5.1 Fonts and sizes

Quran text only ever uses the Hafs font. UI text stays Manrope / OS Arabic. Sizes: translation mode 22–40 sp (default 28), line height 2.0×; Mushaf page base size 28 sp at 375 dp width, scaled by width, then one fitted size per page (see §2.4; on a 6.7-inch phone most pages land between 18 and 21 sp, pages 1 and 2 at the base); basmala 24 sp; surah names in lists 20 sp; header title in Arabic UI 20 sp.

### 5.2 Ayah roundel

`QuranText.withMarker(text, ayahNumber)` = `text + " " + arabicIndic(ayahNumber)`. Always Arabic-Indic digits, in every locale: the Mushaf's numbers are part of the Mushaf. Never U+06DD, never U+2060.

### 5.3 Direction

Every Quran text composable is wrapped in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` with `TextAlign.Right` (translation cards) or `TextAlign.Justify` (Mushaf lines), so the layout is identical under English and Arabic UI. The pager's page order is right-to-left in both UIs.

## 6. Attribution

The Attribution screen gains: Tanzil Project (text and translations, with the required link), King Fahd Glorious Quran Printing Complex (font), each translation by name and translator with its licence line, the Quranic Universal Library and `zonetecde/mushaf-layout` for the page layout. `docs/ATTRIBUTION.md` is updated to match. The translation list on that screen is read from the `translation` table so it never drifts from what is bundled.

## 7. Testing

- Pipeline: `tools/build-quran-db.py --verify` checks counts (114 / 6,236 / 30 / 604 / 15 lines on pages 3–604), the word-equality rule per ayah, U+06DF absence, and that every ayah maps to exactly one page and every page's first line to an ayah.
- JVM (`androidUnitTest`, JDBC driver over the built file): repository queries return the right shapes for known references (1:1, 2:255, 114:6, page 1, page 604, juz 30).
- commonTest: roundel rule, digit conversion, locale defaults for translation and mode, position mapping ayah ↔ page from a small in-memory fake.
- Device round on S23, LoopPhone, iPhone 12 and the emulator in English and Arabic, light and dark, with screenshots in `docs/BUILD-LOG.md`.

## 8. Out of scope, recorded

Search and bookmarks and share (2b). Audio (3). Word-by-word, tajweed colours, footnotes, notes, khatm plans (later slices or never). Tafsir beyond Muyassar. Downloadable translation packs.
