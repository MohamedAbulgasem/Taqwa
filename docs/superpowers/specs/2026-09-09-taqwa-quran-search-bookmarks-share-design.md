# Taqwa slice 2b: Quran search, bookmarks, share

Status: **decided 9 September 2026, overnight, by Claude on Mohamed's standing instruction** ("You build, I review"). Extends `2026-09-07-taqwa-quran-reader-design.md` (slice 2a), whose §2.1, §2.3, §2.4 and §2.5 this document amends. Mohamed's scope statement: "Slice 2b, the agreed scope" = full-text search, bookmarks, share and copy. Everything else in 2a's §8 stays out.

## 1. Goals

- Find an ayah by a word or phrase, in Arabic or in the translation the reader is set to, from the Quran tab's existing field, and land on it.
- Keep ayahs. A bookmark is one tap from any ayah, and the list of them is one tap from the Quran root.
- Take an ayah out of the app: copy or share its text with its reference, from the card reader and from the Mushaf.

Non-goals: search history or suggestions, searching the transliteration, notes or highlights on ayahs, bookmark folders, sharing as an image, syncing.

## 2. Behaviour

### 2.1 Search (amends 2a §2.1)

The root's field keeps its placeholder ("Search surah" becomes **"Search the Quran"** / «ابحث في القرآن») and keeps filtering the surah list as it does now. What is new is beneath it:

- While the trimmed query has **fewer than 2 letters**, only the surah filter runs (as today).
- From 2 letters, the root shows two sections in the same lazy list:
  1. **Surahs** — the filtered surah rows, at most 5, in a card with a section label ("SURAHS" / «السور»). Omitted when nothing matches.
  2. **Ayahs** — a section label with the count ("AYAHS · 37" / «الآيات · ٣٧»; "AYAHS · 100+" when capped) and a card of result rows. Omitted while the query is still being debounced; shows an empty row ("No ayahs match" / «لا توجد آيات مطابقة») when the search returned nothing.
- The Surah | Juz | Bookmarks switch hides while a query is active, so the list is only search results.
- Debounce: 250 ms after the last keystroke. Cap: 100 results, in Mushaf order (surah, ayah).

**Which text is searched** is decided by the query's script:

- A query containing any Arabic letter searches the **Arabic text** in the pre-normalised `ayah.text_search` column (Tanzil simple-clean). The query is folded with `QuranText.normaliseForSearch` and split on spaces; an ayah is a hit when its search text contains *every* token as a substring. So «الرحمن» finds every ayah containing الرحمن; «رحمن» finds them too, because the match is not anchored at a word start; «رب العالمين» finds ayahs containing both. This started out as an FTS5 `MATCH` over an `ayah_fts` table, which had to go: Android's framework SQLite is built without the FTS5 module, so every Arabic query crashed on a phone with `no such module: fts5` (desktop and iOS SQLite have it, which is why the JVM test passed). Bundling a SQLite build with FTS5 would add megabytes to the APK, so the matching moved into Kotlin, exactly like the translation search below.
- Any other query searches the **current translation** (`ReadingSettings.translationId`; with translation off, or an id no longer bundled, Saheeh International): a case-insensitive substring match, done in Kotlin over the translation's 6,236 rows so that Turkish, French and Indonesian case folding is correct (SQLite's `LIKE` and `lower()` are ASCII-only). Under 20 ms on the phones we have; measured once in the repository test.

**Result row**: surah name (Latin or Arabic per the UI rule) and "2:255" in the caption style on the first line; then the Arabic text in the Mushaf font at 18 sp, one line, ellipsised at the end (RTL); then, for translation hits only, the translation text in the caption style, two lines, ellipsised, with the matched substring in **the primary text colour and semibold** against secondary for the rest. Tapping a row opens the ayah the way the root already opens surahs: the card reader positioned on that ayah in Translation mode, the Mushaf page holding it in Mushaf mode. The query stays in the field on return.

### 2.2 Bookmarks

A bookmark is an ayah (surah, number) with the time it was set.

- **Setting one**: from the reader's ayah action row (§2.4) and from the Mushaf reference pill (§2.5). The action toggles: bookmarked ayahs show a filled bookmark glyph, others an outline. No confirmation.
- **Seeing them in the reader**: a bookmarked ayah's card carries a small filled bookmark glyph (14 dp, accent) in its top-start corner, inside the padding, above the Arabic. Nothing else on the card changes.
- **The list**: the root's switch becomes **Surah | Juz | Bookmarks** («السور | الأجزاء | المحفوظات»). The Bookmarks tab lists bookmarks newest first in the same card-list shape as the surah list: each row shows the surah name and "Ayah 255" / «الآية ٢٥٥» on the first line (caption: juz and page), the Arabic text one line in the Mushaf font at 18 sp, and a trailing filled bookmark glyph in a 44 dp target that **removes** the bookmark on tap (the row disappears; re-adding is one tap from the ayah, so no undo). Tapping the row opens the ayah like a search hit. Empty state, in the card: "No bookmarks yet. Open an ayah and tap the bookmark." / «لا توجد محفوظات بعد. افتح آية واضغط على علامة الحفظ.»
- **Storage**: DataStore, key `quran_bookmarks`, a string set of `"<surah>:<ayah>:<epochMillis>"` entries (`stringSetPreferencesKey`). Hundreds of entries at most; no new database, nothing to migrate, survives the bundled Quran database being replaced. Exposed by a `BookmarkStore` (`bookmarks: Flow<List<Bookmark>>` newest first, `suspend fun toggle(surah, ayah)`, `suspend fun isBookmarked(surah, ayah)`), with malformed entries ignored, never thrown on.

### 2.3 Copy and share

Both produce the same **text**, built by one pure function (`AyahShareText.format`) so it is unit-tested once:

```
<Arabic text> ﴿<Arabic-Indic number>﴾

<translation text> (<translation name>)

<Surah name> <surah>:<ayah>
```

- The Arabic line is `ayah.text_uthmani` with the number in ornate brackets U+FD3F/U+FD3E — the roundel is a font feature, and plain digits after the text would be mistaken for the next ayah's start in other apps.
- The translation block is present only when the reader is showing one (not in Mushaf mode, not with translation off); its name is the translation's own name from the database.
- The surah name follows the UI rule (Latin under Latin UIs, Arabic under Arabic); the reference digits are Western under Latin UIs and Arabic-Indic under Arabic, via `PlatformFormat.localizedDigits`.
- No app name, no link, no hashtag.

**Copy** puts that text on the clipboard through Compose's `LocalClipboardManager` and shows **"Copied"** / «تم النسخ» in place of the action's label for 1.5 s. **Share** hands it to the platform share sheet: Android `Intent.ACTION_SEND` (`text/plain`) wrapped in a chooser, iOS `UIActivityViewController` presented from the root view controller, through an `expect fun shareText(text: String)`.

### 2.4 Reader ayah action row (amends 2a §2.3)

Tapping an ayah card selects it (one at a time, as now) and reveals, inside the card below the translation, a row of three text-and-glyph actions in the caption style, 44 dp tall targets, start-aligned: **Bookmark** (glyph filled when set; label "Bookmark" / "Bookmarked", «حفظ» / «محفوظة»), **Copy** («نسخ»), **Share** («مشاركة»). The row is separated from the content above by 8 dp and a `CardDivider`. Tapping the card again, or another card, hides it. The row's glyphs are hand-drawn in `Glyphs.kt` at the tab bar's stroke: a bookmark (a pennant with a notch), two stacked rectangles (copy), a box with an arrow leaving it (share).

### 2.5 Mushaf reference pill (amends 2a §2.4)

The pill that shows the tapped ayah's reference grows into a bar: the reference on the start side, then the same three actions as glyph-only 44 dp buttons. Same width rules as today (centred, straddling the frame's bottom hairline). The share text from the Mushaf never includes a translation. Tapping the reference clears the highlight as before.

## 3. Data and code

- **Quran.sq** gains `ayahSearchRows: SELECT surah, number, text_uthmani, text_search FROM ayah ORDER BY surah, number;` and `translationTextsAll: SELECT surah, number, text FROM ayah_translation WHERE translation_id = ? ORDER BY surah, number;`. Dropping FTS5 removed the `ayah_fts` virtual table from both the pipeline and `Quran.sq`, so the database was rebuilt and `user_version` (and `QuranDb.VERSION`) went to **4**; that bump makes every installed copy re-copy the file on next launch.
- **QuranSource** gains `suspend fun searchArabic(query: String, limit: Int): List<SearchHit>` and `suspend fun searchTranslation(translationId: String, query: String, limit: Int): List<SearchHit>`, `data class SearchHit(val surah: Int, val ayah: Int, val arabic: String, val translation: String?)`. The query's tokens come from a pure `SearchQuery.arabicTokens(raw: String): List<String>` (empty when no token survives normalisation, so no search runs) and are testable without a database; `SearchQuery.isArabic(raw)` decides the branch.
- **BookmarkStore** in `settings/`, over the app's existing DataStore (`SettingsRepository` owns the store; the bookmark store takes the same `DataStore<Preferences>`). `data class Bookmark(val surah: Int, val ayah: Int, val createdAt: Long)`.
- **AyahShareText** in `quran/`: `format(arabic, ayahNumber, translation: Pair<String, String>?, surahName, surah, ayah, digits: (Int) -> String)`.
- **shareText** expect/actual in `share/`: Android needs the application context (`appContext`) and `FLAG_ACTIVITY_NEW_TASK`; iOS presents on `UIApplication.sharedApplication.keyWindow?.rootViewController` (top-most presented controller).
- **Navigation**: `Screen.Reader(surah, ayah)` and `Screen.Mushaf(page)` already carry what a hit needs; no new screens. `RootTab` gains `BOOKMARKS`.
- **State**: `QuranRootUiState.Ready` gains `search: SearchState` (`Idle`, `Searching`, `Results(hits, capped)`) and `bookmarks: List<BookmarkRow>` (bookmark + surah + Arabic text); `ReaderUiState.Ready` gains `bookmarked: Set<Int>` (ayah numbers of this surah) and `translationName: String?`. Share and copy are actions on the view models that return the text; the screen does the clipboard and share-sheet calls.

## 4. Strings

All new copy exists in `values/strings.xml` and `values-ar/strings.xml` from the start: `quran_search_hint`, `quran_section_surahs`, `quran_section_ayahs` (with a count argument), `quran_search_empty`, `quran_tab_bookmarks`, `quran_bookmarks_empty`, `quran_ayah_n`, `quran_action_bookmark`, `quran_action_bookmarked`, `quran_action_copy`, `quran_action_copied`, `quran_action_share`. Arabic strings are written by the implementer in the same register as the existing ones (formal, short, no transliteration); the final review checks them.

## 5. Tests

Pure and view-model tests in `commonTest`, the repository search against the real bundled database in `androidUnitTest` (as `QuranRepositoryDbTest` already does): FTS query building (folding, quoting, prefix, empty), Arabic search hits (`الرحمن` includes 1:1 and 1:3; a nonsense query returns nothing), translation search (case-insensitive, non-ASCII, cap), bookmark store (toggle, order, malformed entries ignored), share text (with and without translation, Arabic UI digits), root search state transitions with the debounce, reader bookmark set. The device round covers Android and iOS in English and Arabic.

## 6. Out of scope, recorded

Search history, transliteration search, notes and highlights, folders, image sharing, sync, tafsir search beyond the selected text. Word-level result highlighting in Arabic.
