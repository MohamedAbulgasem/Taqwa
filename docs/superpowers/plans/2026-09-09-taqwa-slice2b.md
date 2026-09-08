# Taqwa slice 2b (search, bookmarks, share) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Full-text search of the Quran from the Quran tab, bookmarks on ayahs with a list of them, and copy/share of an ayah from the card reader and the Mushaf, per `docs/superpowers/specs/2026-09-09-taqwa-quran-search-bookmarks-share-design.md`.

**Architecture:** Two new SQLDelight queries and two `QuranSource` methods expose Arabic and translation search, both matched in Kotlin. **Correction (applied during 2b):** Task 1 below was written against an FTS5 table (`ayah_fts`) in the bundled `quran.db`. That table is gone: Android's framework SQLite is built without the FTS5 module, so `ayah_fts MATCH` crashed on every phone with `no such module: fts5` (it passed on desktop and iOS SQLite, which do have it), and bundling a SQLite build with FTS5 would add megabytes to the APK. Arabic search now scans the pre-normalised `ayah.text_search` column in Kotlin — `SearchQuery.arabicTokens(raw): List<String>` plus a `contains` check per token — and the database was rebuilt at `user_version`/`QuranDb.VERSION` 4. Every `SearchQuery.fts`, `searchArabic:` SQL and `ayah_fts` snippet in Task 1 below is superseded; read the shipped `SearchQuery.kt`, `QuranRepository.kt` and `Quran.sq` instead. Bookmarks live in the existing DataStore as a string set behind a small `BookmarkStore`. Copy/share text is one pure formatter; the share sheet is one `expect fun`. The root, reader and Mushaf view models grow state; the screens grow a search section, a Bookmarks tab, an ayah action row and pill actions.

**Tech Stack:** Kotlin 2.4 Multiplatform, Compose Multiplatform 1.12, SQLDelight 2.2.1 (sqlite-3-38 dialect, no FTS5), DataStore preferences 1.1.7, kotlinx-coroutines-test. Tests via `./scripts/test.sh` (all targets) or `./gradlew :shared:testDebugUnitTest --tests '<pattern>'` (JVM only, faster). Android APK: `./gradlew :androidApp:assembleDebug`. Always use absolute paths in shell commands; the shell cwd resets between calls. Repo: `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa`, branch `slice2b`.

## Global Constraints

- Quran text is never retyped: every Arabic Quran string in code or tests comes from the database or from the existing fixtures (`MUSHAF_PAGE_1..3`, `FakeQuranSource`). UI copy is never a literal in Kotlin: every user-facing string is a resource in both `shared/src/commonMain/composeResources/values/strings.xml` and `values-ar/strings.xml`.
- Quran text is drawn only with `mushafFamily()` (`TaqwaText.quran(size)` / `fontFamily = mushafFamily()`), RTL. UI text uses the existing `TaqwaText` styles and `LocalTaqwaColors`; no Material colours, no new colour literals.
- Hand-drawn chrome: hairline cards (`TaqwaCard`, `CardDivider`, `TaqwaRow`), hand-drawn glyphs at `glyphStroke()` weight in a 16-unit box, 44 dp minimum tap targets, no ripples on selectable rows (`TaqwaRow(selectable = true)` / `indication = null`).
- Search: debounce 250 ms, minimum 2 letters, cap 100 results, results ordered by (surah, ayah). Arabic queries → FTS with `QuranText.normaliseForSearch`, tokens quoted and suffixed `*`; other queries → case-insensitive substring over the current translation (`ReadingSettings.translationId`; `none` or unbundled → `en.sahih`).
- Bookmarks: DataStore key `quran_bookmarks`, string set, entry format `"<surah>:<ayah>:<epochMillis>"`, newest first, malformed entries ignored.
- Share text format (exact, blank lines included; the translation block only when a translation is shown):
  ```
  <arabic> ﴿<arabic-indic number>﴾

  <translation> (<translation name>)

  <surah name> <surah>:<ayah>
  ```
  Ornate brackets U+FD3F before and U+FD3E after the number (`"﴿"`, `"﴾"`). Surah name by the UI rule (`Surah.displayName(rtl)`); reference digits via `PlatformFormat.localizedDigits`.
- View models never import `nav`; navigation stays in `App.kt`. View models are plain classes over `MutableStateFlow`, testable under `runTest`, in the shape of `QuranRootViewModel`.
- Every task ends with the covering tests green and a commit on `slice2b`. Comments say why, not what.

---

### Task 1: Search in the data layer

**Files:**
- Modify: `shared/src/commonMain/sqldelight/world/taqwa/app/quran/db/Quran.sq` (append two queries)
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/quran/SearchQuery.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranModels.kt` (add `SearchHit`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranRepository.kt` (interface + implementation)
- Modify: `shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/FakeQuranSource.kt` (implement the two new methods)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/quran/SearchQueryTest.kt`
- Test: `shared/src/androidUnitTest/kotlin/world/taqwa/app/quran/QuranRepositoryDbTest.kt` (append)

**Interfaces:**
- Produces:
  ```kotlin
  data class SearchHit(val surah: Int, val ayah: Int, val arabic: String, val translation: String?)
  object SearchQuery {
      fun isArabic(raw: String): Boolean
      fun arabicTokens(raw: String): List<String>  // empty when nothing searchable survives folding
      fun isLongEnough(raw: String): Boolean // at least 2 letters or digits after trim
  }
  interface QuranSource {  // two new members
      suspend fun searchArabic(query: String, limit: Int): List<SearchHit>
      suspend fun searchTranslation(translationId: String, query: String, limit: Int): List<SearchHit>
  }
  ```

- [ ] **Step 1: Append the queries to Quran.sq**

```sql
searchArabic:
SELECT surah, number, text_uthmani FROM ayah
WHERE rowid IN (SELECT rowid FROM ayah_fts WHERE ayah_fts MATCH ?)
ORDER BY surah, number LIMIT ?;

translationTextsAll:
SELECT surah, number, text FROM ayah_translation WHERE translation_id = ? ORDER BY surah, number;
```

- [ ] **Step 2: Write the failing SearchQuery tests**

`shared/src/commonTest/kotlin/world/taqwa/app/quran/SearchQueryTest.kt`:

```kotlin
package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchQueryTest {
    @Test
    fun aQueryWithAnyArabicLetterIsArabic() {
        assertTrue(SearchQuery.isArabic("الرحمن"))
        assertTrue(SearchQuery.isArabic("rabb العالمين"))
        assertFalse(SearchQuery.isArabic("mercy"))
        assertFalse(SearchQuery.isArabic("2:255"))
    }

    @Test
    fun ftsFoldsQuotesAndPrefixesEveryToken() {
        // Harakat and the alef wasla fold away (QuranText.normaliseForSearch); each token is a
        // quoted prefix term; tokens are joined by a space, which FTS5 reads as AND.
        assertEquals("\"الرحمن\"*", SearchQuery.fts("ٱلرَّحْمَٰنِ"))
        assertEquals("\"رب\"* \"العالمين\"*", SearchQuery.fts("  رَبِّ   ٱلْعَالَمِينَ "))
    }

    @Test
    fun ftsStripsCharactersThatWouldBreakTheMatchSyntax() {
        // Double quotes, asterisks, parentheses and colons in the raw text never reach FTS5.
        assertEquals("\"رب\"*", SearchQuery.fts("\"رب\"*():"))
    }

    @Test
    fun ftsIsNullWhenNothingSearchableRemains() {
        assertNull(SearchQuery.fts("   "))
        assertNull(SearchQuery.fts("\"\"*"))
    }

    @Test
    fun twoLettersIsTheMinimum() {
        assertFalse(SearchQuery.isLongEnough("a"))
        assertFalse(SearchQuery.isLongEnough(" ر "))
        assertTrue(SearchQuery.isLongEnough("ab"))
        assertTrue(SearchQuery.isLongEnough("رب"))
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.quran.SearchQueryTest' -q 2>&1 | tail -5`
Expected: compilation error, `SearchQuery` unresolved.

- [ ] **Step 4: Implement SearchQuery**

`shared/src/commonMain/kotlin/world/taqwa/app/quran/SearchQuery.kt`:

```kotlin
package world.taqwa.app.quran

/**
 * Turns what the reader typed into what the two searches need (spec 2b §2.1). Pure, so the
 * quoting rule and the script decision are tested without a database.
 */
object SearchQuery {
    private val ARABIC_LETTER = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]")
    // Anything FTS5 gives syntax meaning to inside or around a term. A term is wrapped in double
    // quotes below, so a quote inside it is the one character that must not survive.
    private val FTS_SYNTAX = Regex("[\"*():^{}\\-+]")

    fun isArabic(raw: String): Boolean = ARABIC_LETTER.containsMatchIn(raw)

    fun isLongEnough(raw: String): Boolean = raw.count { it.isLetterOrDigit() } >= 2

    /** The FTS5 MATCH expression: every folded token as a quoted prefix term, AND-ed by
     * adjacency. Null when no token survives, so the caller runs no query at all. */
    fun fts(raw: String): String? {
        val tokens = QuranText.normaliseForSearch(FTS_SYNTAX.replace(raw, " "))
            .split(' ')
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "\"$it\"*" }
    }
}
```

- [ ] **Step 5: Add SearchHit and the source methods**

In `QuranModels.kt`, after `data class Ayah(...)`:

```kotlin
/** One search result (spec 2b §2.1): the ayah's own Arabic and, for a translation hit, the
 * translation text the match was found in. */
data class SearchHit(val surah: Int, val ayah: Int, val arabic: String, val translation: String?)
```

In `QuranRepository.kt`, add to `interface QuranSource`:

```kotlin
    /** Arabic FTS search; [query] is raw user text, folded and quoted by [SearchQuery.fts]. */
    suspend fun searchArabic(query: String, limit: Int): List<SearchHit>
    /** Case-insensitive substring search over one translation, folded in Kotlin so non-ASCII
     * case (Turkish, French) folds correctly, which SQLite's LIKE and lower() cannot do. */
    suspend fun searchTranslation(translationId: String, query: String, limit: Int): List<SearchHit>
```

and to `class QuranRepository`:

```kotlin
    override suspend fun searchArabic(query: String, limit: Int): List<SearchHit> = withContext(io) {
        val match = SearchQuery.fts(query) ?: return@withContext emptyList()
        q.searchArabic(match, limit.toLong()).executeAsList().map {
            SearchHit(it.surah.toInt(), it.number.toInt(), it.text_uthmani, translation = null)
        }
    }

    // The whole translation is read once per search (6,236 short rows, a few milliseconds) and
    // the ayah rows for the hits are fetched by surah, so a phrase found in many surahs costs one
    // query per surah touched, not one per hit.
    override suspend fun searchTranslation(translationId: String, query: String, limit: Int): List<SearchHit> = withContext(io) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return@withContext emptyList()
        val hits = q.translationTextsAll(translationId).executeAsList()
            .asSequence()
            .filter { it.text.lowercase().contains(needle) }
            .take(limit)
            .toList()
        val arabicBySurah = hits.map { it.surah.toInt() }.distinct().associateWith { surah ->
            q.ayahsOfSurah(surah.toLong()).executeAsList().associate { it.number.toInt() to it.text_uthmani }
        }
        hits.map { SearchHit(it.surah.toInt(), it.number.toInt(), arabicBySurah.getValue(it.surah.toInt()).getValue(it.number.toInt()), it.text) }
    }
```

- [ ] **Step 6: Give FakeQuranSource the two methods**

Add to `FakeQuranSource`, next to `translationTexts`:

```kotlin
    /** Arabic search over the configured ayahs' text, folded the same way the real FTS is (a
     * whole-word prefix match on the normalised text is close enough for the view-model tests). */
    override suspend fun searchArabic(query: String, limit: Int): List<SearchHit> {
        val tokens = SearchQuery.fts(query)?.split(' ')?.map { it.trim('"', '*') } ?: return emptyList()
        return ayahsBySurah.values.flatten()
            .filter { ayah ->
                val words = QuranText.normaliseForSearch(ayah.text).split(' ')
                tokens.all { token -> words.any { it.startsWith(token) } }
            }
            .sortedWith(compareBy({ it.surah }, { it.number }))
            .take(limit)
            .map { SearchHit(it.surah, it.number, it.text, null) }
    }

    override suspend fun searchTranslation(translationId: String, query: String, limit: Int): List<SearchHit> {
        val needle = query.trim().lowercase()
        val texts = translationTextsById[translationId] ?: return emptyList()
        return texts.flatMap { (surah, byAyah) -> byAyah.map { (ayah, text) -> Triple(surah, ayah, text) } }
            .filter { (_, _, text) -> text.lowercase().contains(needle) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .take(limit)
            .map { (surah, ayah, text) ->
                val arabic = ayahsBySurah[surah]?.firstOrNull { it.number == ayah }?.text ?: ""
                SearchHit(surah, ayah, arabic, text)
            }
    }
```

(`SearchQuery` and `SearchHit` need imports in the fake: `world.taqwa.app.quran.SearchHit`, `world.taqwa.app.quran.SearchQuery`, `world.taqwa.app.quran.QuranText`.)

- [ ] **Step 7: Append database tests**

In `QuranRepositoryDbTest.kt`:

```kotlin
    @Test fun arabicSearchFindsPrefixesAcrossHarakat() = runTest {
        val hits = repo.searchArabic("الرحمن", limit = 100)
        assertTrue(hits.any { it.surah == 1 && it.ayah == 1 }, "1:1 has ٱلرَّحْمَٰنِ")
        assertTrue(hits.any { it.surah == 1 && it.ayah == 3 })
        assertTrue(hits.all { it.translation == null })
        assertEquals(hits, hits.sortedWith(compareBy({ it.surah }, { it.ayah })))
    }

    @Test fun arabicSearchWithTwoTokensNeedsBoth() = runTest {
        val hits = repo.searchArabic("رب العالمين", limit = 100)
        assertTrue(hits.any { it.surah == 1 && it.ayah == 2 })
        assertTrue(hits.none { it.surah == 1 && it.ayah == 1 })
    }

    @Test fun arabicSearchNonsenseFindsNothingAndTheCapHolds() = runTest {
        assertEquals(emptyList(), repo.searchArabic("ذذذذ", limit = 100))
        assertEquals(5, repo.searchArabic("الله", limit = 5).size)
    }

    @Test fun translationSearchIsCaseInsensitiveAndCarriesBothTexts() = runTest {
        val hits = repo.searchTranslation("en.sahih", "MERCIFUL", limit = 100)
        assertTrue(hits.any { it.surah == 1 && it.ayah == 1 })
        val first = hits.first()
        assertTrue(first.translation!!.contains("Merciful"))
        assertTrue(first.arabic.isNotBlank())
    }

    @Test fun translationSearchFoldsNonAsciiCase() = runTest {
        // Turkish "İ" lower-cases to "i̇" in Kotlin; a plain lower-case query must still match.
        val upper = repo.searchTranslation("tr.diyanet", "ALLAH", limit = 100)
        val lower = repo.searchTranslation("tr.diyanet", "allah", limit = 100)
        assertEquals(upper.map { it.surah to it.ayah }, lower.map { it.surah to it.ayah })
        assertTrue(upper.isNotEmpty())
    }
```

- [ ] **Step 8: Run the tests**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.quran.*' --tests 'world.taqwa.app.feature.quran.*' -q 2>&1 | tail -5`
Expected: all green (the `--tests 'world.taqwa.app.quran.*'` pattern covers `SearchQueryTest` and `QuranRepositoryDbTest`).

- [ ] **Step 9: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "Quran search in the data layer: FTS for Arabic, folded substring for translations"
```

---

### Task 2: BookmarkStore

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt` (add key)
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/settings/BookmarkStore.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt` (expose `bookmarkStore`)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/settings/BookmarkStoreTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Bookmark(val surah: Int, val ayah: Int, val createdAt: Long)
  class BookmarkStore(store: DataStore<Preferences>, private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
      val bookmarks: Flow<List<Bookmark>>              // newest first
      suspend fun toggle(surah: Int, ayah: Int): Boolean   // returns the new state: true = now bookmarked
      suspend fun remove(surah: Int, ayah: Int)
  }
  ```
  `AppContainer.bookmarkStore: BookmarkStore` built over the same DataStore as `settingsRepository` (see how `SettingsRepository(createDataStore())` is built; keep one `createDataStore()` call and pass the instance to both).

- [ ] **Step 1: Write the failing tests**

`shared/src/commonTest/kotlin/world/taqwa/app/settings/BookmarkStoreTest.kt`:

```kotlin
package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookmarkStoreTest {
    private fun dataStore() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-bookmarks-${Random.nextULong()}.preferences_pb".toPath()
    }

    @Test
    fun toggleAddsThenRemovesAndReportsTheNewState() = runTest {
        var clock = 1_000L
        val store = BookmarkStore(dataStore()) { clock }
        assertEquals(emptyList(), store.bookmarks.first())
        assertTrue(store.toggle(2, 255))
        assertEquals(listOf(Bookmark(2, 255, 1_000L)), store.bookmarks.first())
        assertFalse(store.toggle(2, 255))
        assertEquals(emptyList(), store.bookmarks.first())
    }

    @Test
    fun newestFirst() = runTest {
        var clock = 1L
        val store = BookmarkStore(dataStore()) { clock++ }
        store.toggle(1, 1); store.toggle(2, 255); store.toggle(18, 10)
        assertEquals(listOf(18 to 10, 2 to 255, 1 to 1), store.bookmarks.first().map { it.surah to it.ayah })
    }

    @Test
    fun removeIsIdempotent() = runTest {
        val store = BookmarkStore(dataStore()) { 5L }
        store.toggle(1, 1)
        store.remove(1, 1)
        store.remove(1, 1)
        assertEquals(emptyList(), store.bookmarks.first())
    }

    @Test
    fun malformedEntriesAreIgnoredNotThrown() = runTest {
        val ds = dataStore()
        ds.edit { it[stringSetPreferencesKey("quran_bookmarks")] = setOf("2:255:10", "garbage", "1:x:3", "3:4") }
        val store = BookmarkStore(ds) { 0L }
        assertEquals(listOf(Bookmark(2, 255, 10L)), store.bookmarks.first())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.settings.BookmarkStoreTest' -q 2>&1 | tail -5`
Expected: compilation error, `BookmarkStore` unresolved.

- [ ] **Step 3: Implement**

`SettingsKeys.kt`, next to the other `QURAN_*` keys:

```kotlin
    /** Bookmarked ayahs as "<surah>:<ayah>:<epochMillis>" entries (spec 2b §2.2). */
    val QURAN_BOOKMARKS = stringSetPreferencesKey("quran_bookmarks")
```

`BookmarkStore.kt`:

```kotlin
package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/** An ayah the reader kept (spec 2b §2.2). */
data class Bookmark(val surah: Int, val ayah: Int, val createdAt: Long)

/**
 * Bookmarks in the app's DataStore as a string set (spec 2b §2.2): hundreds at most, so no
 * database; and separate from the bundled Quran database, which is replaced wholesale on upgrade.
 * [now] is injectable so tests control the ordering clock.
 */
class BookmarkStore(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val bookmarks: Flow<List<Bookmark>> = store.data.map { prefs -> parse(prefs[SettingsKeys.QURAN_BOOKMARKS].orEmpty()) }

    /** Adds the ayah if absent, removes it if present; returns true when it is now bookmarked. */
    suspend fun toggle(surah: Int, ayah: Int): Boolean {
        var added = false
        store.edit { prefs ->
            val current = parse(prefs[SettingsKeys.QURAN_BOOKMARKS].orEmpty())
            val existing = current.firstOrNull { it.surah == surah && it.ayah == ayah }
            val next = if (existing == null) {
                added = true
                current + Bookmark(surah, ayah, now())
            } else {
                current - existing
            }
            prefs[SettingsKeys.QURAN_BOOKMARKS] = next.map { encode(it) }.toSet()
        }
        return added
    }

    suspend fun remove(surah: Int, ayah: Int) {
        store.edit { prefs ->
            val current = parse(prefs[SettingsKeys.QURAN_BOOKMARKS].orEmpty())
            prefs[SettingsKeys.QURAN_BOOKMARKS] = current.filterNot { it.surah == surah && it.ayah == ayah }.map { encode(it) }.toSet()
        }
    }

    private fun encode(b: Bookmark) = "${b.surah}:${b.ayah}:${b.createdAt}"

    /** Newest first; an entry that does not parse is dropped rather than failing every reader of
     * the flow — a preference file is user data and must never be able to crash the app. */
    private fun parse(raw: Set<String>): List<Bookmark> = raw.mapNotNull { entry ->
        val parts = entry.split(':')
        if (parts.size != 3) return@mapNotNull null
        val surah = parts[0].toIntOrNull() ?: return@mapNotNull null
        val ayah = parts[1].toIntOrNull() ?: return@mapNotNull null
        val at = parts[2].toLongOrNull() ?: return@mapNotNull null
        Bookmark(surah, ayah, at)
    }.sortedByDescending { it.createdAt }
}
```

`AppContainer.kt`: replace `val settingsRepository = SettingsRepository(createDataStore())` with

```kotlin
    private val dataStore = createDataStore()
    val settingsRepository = SettingsRepository(dataStore)
    val bookmarkStore = BookmarkStore(dataStore)
```

(Check `createDataStore()`'s return type is `DataStore<Preferences>`; it is what `SettingsRepository` takes.)

- [ ] **Step 4: Run the tests**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.settings.*' -q 2>&1 | tail -5`
Expected: green. `kotlinx.datetime.Clock` is already a dependency (the app uses kotlinx-datetime); if `Clock.System` is unresolved, use `kotlin.time.Clock` per the version in `gradle/libs.versions.toml`.

- [ ] **Step 5: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "BookmarkStore: bookmarked ayahs in DataStore, newest first"
```

---

### Task 3: Share text, the share sheet, the glyphs and the strings

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/quran/AyahShareText.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/share/ShareText.kt` (`expect fun shareText(text: String)`)
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/share/ShareText.android.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/share/ShareText.ios.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/Glyphs.kt` (add `drawBookmark`, `drawCopy`, `drawShare`)
- Modify: `shared/src/commonMain/composeResources/values/strings.xml` and `values-ar/strings.xml`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/quran/AyahShareTextTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object AyahShareText {
      fun format(arabic: String, ayahNumber: Int, translation: Pair<String, String>?, surahName: String, surah: Int, ayah: Int, digits: (Int) -> String): String
  }
  expect fun shareText(text: String)
  internal fun DrawScope.drawBookmark(tint: Color, filled: Boolean)
  internal fun DrawScope.drawCopy(tint: Color)
  internal fun DrawScope.drawShare(tint: Color)
  ```
  String ids (en / ar): `quran_search_hint` ("Search the Quran" / «ابحث في القرآن»), `quran_section_surahs` ("Surahs" / «السور»), `quran_section_ayahs` ("Ayahs · %1$s" / «الآيات · %1$s»), `quran_search_empty` ("No ayahs match" / «لا توجد آيات مطابقة»), `quran_tab_bookmarks` ("Bookmarks" / «المحفوظات»), `quran_bookmarks_empty` ("No bookmarks yet. Open an ayah and tap the bookmark." / «لا توجد محفوظات بعد. افتح آية واضغط على علامة الحفظ.»), `quran_ayah_n` ("Ayah %1$s" / «الآية %1$s»), `quran_action_bookmark` ("Bookmark" / «حفظ»), `quran_action_bookmarked` ("Bookmarked" / «محفوظة»), `quran_action_copy` ("Copy" / «نسخ»), `quran_action_copied` ("Copied" / «تم النسخ»), `quran_action_share` ("Share" / «مشاركة»). Change the existing `quran_search_hint` value if it exists, else add it (the root currently uses `quran_search_hint` = "Search surah"; update its value in both files).

- [ ] **Step 1: Write the failing share-text test**

`shared/src/commonTest/kotlin/world/taqwa/app/quran/AyahShareTextTest.kt`:

```kotlin
package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class AyahShareTextTest {
    // Not Quran text: a stand-in string is fine here because the formatter never inspects it.
    private val arabic = "نص"

    @Test
    fun withTranslationThreeBlocksSeparatedByBlankLines() {
        val text = AyahShareText.format(
            arabic = arabic, ayahNumber = 255,
            translation = "Allah - there is no deity except Him" to "Saheeh International",
            surahName = "Al-Baqarah", surah = 2, ayah = 255, digits = { it.toString() },
        )
        assertEquals(
            "نص ﴿٢٥٥﴾\n\nAllah - there is no deity except Him (Saheeh International)\n\nAl-Baqarah 2:255",
            text,
        )
    }

    @Test
    fun withoutTranslationTwoBlocks() {
        val text = AyahShareText.format(arabic, 1, null, "Al-Fatihah", 1, 1) { it.toString() }
        assertEquals("نص ﴿١﴾\n\nAl-Fatihah 1:1", text)
    }

    @Test
    fun referenceDigitsFollowTheUiButTheOrnateNumberIsAlwaysArabicIndic() {
        val text = AyahShareText.format(arabic, 7, null, "الفاتحة", 1, 7) { QuranText.arabicIndic(it) }
        assertEquals("نص ﴿٧﴾\n\nالفاتحة ١:٧", text)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.quran.AyahShareTextTest' -q 2>&1 | tail -5`
Expected: compilation error.

- [ ] **Step 3: Implement AyahShareText**

```kotlin
package world.taqwa.app.quran

/**
 * The one text copy and share both produce (spec 2b §2.3). The ayah number goes in ornate
 * brackets rather than as bare digits: the roundel is a font feature of ours, and bare digits
 * after the text read as the next ayah's start wherever the text is pasted.
 */
object AyahShareText {
    private const val OPEN = "﴿"
    private const val CLOSE = "﴾"

    fun format(
        arabic: String,
        ayahNumber: Int,
        /** Translation text to translation name, or null when the reader shows no translation. */
        translation: Pair<String, String>?,
        surahName: String,
        surah: Int,
        ayah: Int,
        digits: (Int) -> String,
    ): String = buildString {
        append(arabic).append(' ').append(OPEN).append(QuranText.arabicIndic(ayahNumber)).append(CLOSE)
        if (translation != null) {
            append("\n\n").append(translation.first).append(" (").append(translation.second).append(')')
        }
        append("\n\n").append(surahName).append(' ').append(digits(surah)).append(':').append(digits(ayah))
    }
}
```

- [ ] **Step 4: Run the share-text test**

Run the same command. Expected: PASS.

- [ ] **Step 5: The share sheet, expect/actual**

`shared/src/commonMain/kotlin/world/taqwa/app/share/ShareText.kt`:

```kotlin
package world.taqwa.app.share

/** Hands [text] to the platform's share sheet (spec 2b §2.3). Fire and forget; never throws. */
expect fun shareText(text: String)
```

`shared/src/androidMain/kotlin/world/taqwa/app/share/ShareText.android.kt`:

```kotlin
package world.taqwa.app.share

import android.content.Intent
import world.taqwa.app.settings.appContext

actual fun shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    // Started from the application context, which has no task of its own to start on.
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { appContext.startActivity(chooser) }
}
```

(`appContext` lives in `world.taqwa.app.settings` in `:widgetcore`; check the import the DataStore factory uses and match it.)

`shared/src/iosMain/kotlin/world/taqwa/app/share/ShareText.ios.kt`:

```kotlin
package world.taqwa.app.share

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

actual fun shareText(text: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    // Present from whatever is on top, or UIKit refuses with "already presenting".
    var top: UIViewController = root
    while (top.presentedViewController != null) top = top.presentedViewController!!
    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    top.presentViewController(sheet, animated = true, completion = null)
}
```

(`keyWindow` is deprecated but still works and is what the app's other UIKit code uses; if the compiler rejects it, use `UIApplication.sharedApplication.windows.firstOrNull { (it as UIWindow).isKeyWindow() } as? UIWindow`.)

- [ ] **Step 6: The glyphs**

Append to `Glyphs.kt` (16-unit box, `glyphStroke()` weight, same conventions as `drawBook`):

```kotlin
/** A bookmark: a pennant with a notch at the foot. Filled when the ayah is kept. */
internal fun DrawScope.drawBookmark(tint: Color, filled: Boolean) {
    val u = size.width / 16f
    val path = Path().apply {
        moveTo(3.5f * u, 2f * u)
        lineTo(12.5f * u, 2f * u)
        lineTo(12.5f * u, 14f * u)
        lineTo(8f * u, 10.6f * u)
        lineTo(3.5f * u, 14f * u)
        close()
    }
    drawPath(path, tint, style = if (filled) Fill else glyphStroke())
}

/** Copy: two overlapping rounded rectangles, the back one peeking out top-start. */
internal fun DrawScope.drawCopy(tint: Color) {
    val u = size.width / 16f
    val corner = CornerRadius(1.2f * u)
    drawRoundRect(tint, topLeft = Offset(5.5f * u, 5.5f * u), size = Size(8f * u, 8f * u), cornerRadius = corner, style = glyphStroke())
    val back = Path().apply {
        moveTo(3.2f * u, 10.5f * u)
        lineTo(3.2f * u, 3.4f * u)
        quadraticTo(3.2f * u, 2.5f * u, 4.1f * u, 2.5f * u)
        lineTo(10.5f * u, 2.5f * u)
    }
    drawPath(back, tint, style = glyphStroke())
}

/** Share: a tray with an arrow rising out of it. */
internal fun DrawScope.drawShare(tint: Color) {
    val u = size.width / 16f
    val tray = Path().apply {
        moveTo(5f * u, 7.5f * u); lineTo(3.2f * u, 7.5f * u); lineTo(3.2f * u, 14f * u)
        lineTo(12.8f * u, 14f * u); lineTo(12.8f * u, 7.5f * u); lineTo(11f * u, 7.5f * u)
    }
    drawPath(tray, tint, style = glyphStroke())
    drawLine(tint, Offset(8f * u, 10.5f * u), Offset(8f * u, 2.2f * u), strokeWidth = size.width * 0.0875f, cap = StrokeCap.Round)
    val head = Path().apply { moveTo(5.4f * u, 4.8f * u); lineTo(8f * u, 2.2f * u); lineTo(10.6f * u, 4.8f * u) }
    drawPath(head, tint, style = glyphStroke())
}
```

Add the imports `Glyphs.kt` lacks (`androidx.compose.ui.geometry.CornerRadius`, `Size`, `androidx.compose.ui.graphics.drawscope.Fill`). Compile check: `./gradlew :shared:compileDebugKotlinAndroid -q`.

- [ ] **Step 7: The strings**

Add every id from the Interfaces block to both `strings.xml` files (keep the files' existing grouping: put them beside the other `quran_*` strings). Update `quran_search_hint` to "Search the Quran" / «ابحث في القرآن».

- [ ] **Step 8: Build both platforms' shared code**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64 -q 2>&1 | grep -E "^e:|error:" | head; echo done`
Expected: no errors printed.

- [ ] **Step 9: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "Share text formatter, platform share sheet, action glyphs and the 2b strings"
```

---

### Task 4: Quran root view model: search state and the Bookmarks tab

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/QuranRootViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt` (pass `container.bookmarkStore`, call `start`)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/QuranRootViewModelTest.kt` (append)

**Interfaces:**
- Consumes: `QuranSource.searchArabic/searchTranslation`, `SearchQuery`, `BookmarkStore.bookmarks/remove`, `ReadingSettings.NO_TRANSLATION`.
- Produces:
  ```kotlin
  enum class RootTab { SURAH, JUZ, BOOKMARKS }
  sealed interface SearchState {
      data object Idle : SearchState            // query too short: surah filter only
      data object Searching : SearchState       // debouncing or querying
      data class Results(val hits: List<SearchHit>, val capped: Boolean, val query: String) : SearchState
  }
  data class BookmarkRow(val bookmark: Bookmark, val surah: Surah, val arabic: String, val juz: Int, val page: Int)
  // QuranRootUiState.Ready gains: val search: SearchState, val bookmarks: List<BookmarkRow>, val translationId: String
  class QuranRootViewModel(source, settings, bookmarks: BookmarkStore, languageTag) {
      suspend fun load()
      fun start(scope: CoroutineScope)     // collects bookmarks into state; must be called from a LaunchedEffect
      fun setFilter(text: String)          // existing; now also drives the debounced search
      fun setTab(tab: RootTab)             // existing name may differ: keep the existing one
      fun removeBookmark(surah: Int, ayah: Int)
  }
  ```
  `filteredSurahs` stays as is (the screen caps it to 5 while searching).

- [ ] **Step 1: Read the existing view model and test file fully**, note the existing method names for setting the filter and the tab (`setFilter`, and whatever the tab setter is called) and the `ReaderViewModel.start(scope)` pattern (`scope.launch { flow.collect { … } }`).

- [ ] **Step 2: Write the failing tests** (append to `QuranRootViewModelTest`; add a `FakeQuranSource` with ayahs and an `en.sahih` translation for surahs 1 and 2 like `ReaderViewModelTest` builds — copy its `source()` helper's data shape, do not invent Quran text: use the fixture ayah texts from `MUSHAF_PAGE_1` words if you need Arabic, e.g. `MUSHAF_PAGE_1.lines[1].text`):

```kotlin
    private fun bookmarkStore(name: String) = BookmarkStore(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-quran-root-bm-$name.preferences_pb".toPath() },
    ) { 1L }

    @Test
    fun aShortQueryOnlyFiltersSurahs() = runTest {
        val vm = QuranRootViewModel(searchSource(), settings("search-short"), bookmarkStore("search-short"), "en")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter("a")
        advanceTimeBy(1_000)
        assertEquals(SearchState.Idle, (vm.state.value as QuranRootUiState.Ready).search)
    }

    @Test
    fun aLatinQuerySearchesTheCurrentTranslationAfterTheDebounce() = runTest {
        val vm = QuranRootViewModel(searchSource(), settings("search-latin"), bookmarkStore("search-latin"), "en")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter("merciful")
        assertEquals(SearchState.Searching, (vm.state.value as QuranRootUiState.Ready).search)
        advanceTimeBy(300)
        val results = (vm.state.value as QuranRootUiState.Ready).search as SearchState.Results
        assertTrue(results.hits.isNotEmpty())
        assertTrue(results.hits.all { it.translation != null })
        assertEquals("merciful", results.query)
    }

    @Test
    fun anArabicQuerySearchesTheArabicText() = runTest {
        val vm = QuranRootViewModel(searchSource(), settings("search-arabic"), bookmarkStore("search-arabic"), "en")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter("الحمد")
        advanceTimeBy(300)
        val results = (vm.state.value as QuranRootUiState.Ready).search as SearchState.Results
        assertEquals(listOf(1 to 2), results.hits.map { it.surah to it.ayah })
        assertTrue(results.hits.all { it.translation == null })
    }

    @Test
    fun clearingTheQueryReturnsToIdleAndOnlyTheLastQueryLands() = runTest {
        val vm = QuranRootViewModel(searchSource(), settings("search-clear"), bookmarkStore("search-clear"), "en")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter("mer"); advanceTimeBy(100)
        vm.setFilter("merciful"); advanceTimeBy(300)
        assertEquals("merciful", ((vm.state.value as QuranRootUiState.Ready).search as SearchState.Results).query)
        vm.setFilter(""); advanceTimeBy(300)
        assertEquals(SearchState.Idle, (vm.state.value as QuranRootUiState.Ready).search)
    }

    @Test
    fun bookmarksArriveAsRowsNewestFirstAndRemoveDropsOne() = runTest {
        val store = bookmarkStore("rows")
        store.toggle(1, 1)
        val vm = QuranRootViewModel(searchSource(), settings("rows"), store, "en")
        vm.load(); vm.start(backgroundScope)
        advanceUntilIdle()
        val rows = (vm.state.value as QuranRootUiState.Ready).bookmarks
        assertEquals(listOf(1 to 1), rows.map { it.surah.number to it.bookmark.ayah })
        assertTrue(rows.first().arabic.isNotBlank())
        vm.removeBookmark(1, 1)
        advanceUntilIdle()
        assertEquals(emptyList(), (vm.state.value as QuranRootUiState.Ready).bookmarks)
    }
```

`searchSource()` builds a `FakeQuranSource` whose `ayahsBySurah[1]` are Al-Faatiha's seven ayahs (texts from `MUSHAF_PAGE_1`'s text lines split per ayah are awkward; instead reuse `ReaderViewModelTest`'s data helper if it exposes one, or construct `Ayah(1, n, <text from fixture words joined>, 1, 1, 1, 0)` for n = 1..2 using `MUSHAF_PAGE_1.lines[1].words` (ayah 1) and `lines[2].words` (ayah 2) joined with spaces, digits removed via `substringBefore(' ')`), plus `translationTextsById["en.sahih"][1] = mapOf(1 to "In the name of Allah, the Entirely Merciful", 2 to "All praise is due to Allah")` and `translationsList` with the `en.sahih` `TranslationInfo`.

- [ ] **Step 3: Run to verify failure**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.feature.quran.QuranRootViewModelTest' -q 2>&1 | tail -5`
Expected: compilation errors (`SearchState`, constructor arity).

- [ ] **Step 4: Implement**

In `QuranRootViewModel.kt`:
- `enum class RootTab { SURAH, JUZ, BOOKMARKS }`.
- Add `SearchState` and `BookmarkRow` as in Interfaces.
- `Ready` gains `search: SearchState = SearchState.Idle`, `bookmarks: List<BookmarkRow> = emptyList()`, `translationId: String`.
- Constructor gains `private val bookmarks: BookmarkStore` (third parameter, before `languageTag`).
- `load()` also reads `translationId` from the same `readingSettings(languageTag).first()` and resolves it: `ReadingSettings.NO_TRANSLATION` or an id not in `source.translations()` → `"en.sahih"`.
- `start(scope)`: `scope.launch { bookmarks.bookmarks.collect { list -> setBookmarkRows(list) } }`, where `setBookmarkRows` maps each bookmark to a `BookmarkRow` using the loaded surah map and `source.ayahs(surah)` (cache the per-surah ayah lists in a `mutableMapOf<Int, List<Ayah>>` so 50 bookmarks in one surah cost one query), skipping bookmarks whose surah or ayah does not exist.
- `setFilter(text)`: keeps the existing behaviour, then: cancel the previous search job; if `!SearchQuery.isLongEnough(text)` set `search = Idle`; else set `search = Searching` and launch on the scope saved by `start`: `delay(250)`, then `val hits = if (SearchQuery.isArabic(text)) source.searchArabic(text, LIMIT + 1) else source.searchTranslation(translationId, text, LIMIT + 1)`, then set `Results(hits.take(LIMIT), capped = hits.size > LIMIT, query = text)`. `LIMIT = 100`. Guard against a stale result: only publish if the state's filter still equals `text`.
- `removeBookmark(surah, ayah)`: `scope.launch { bookmarks.remove(surah, ayah) }` (the flow collection updates the rows).

In `App.kt`, where `QuranRootViewModel(` is constructed, pass `bookmarks = container.bookmarkStore`; find the existing `LaunchedEffect` that calls `load()` and add `viewModel.start(this)` after it (the reader and Mushaf screens already do `LaunchedEffect(viewModel) { viewModel.start(this) }`; mirror that).

- [ ] **Step 5: Run the tests**

Run: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.feature.quran.*' -q 2>&1 | tail -5`
Expected: green, including the older root tests (their `viewModel(name)` helper must now pass a bookmark store; update the helper, not the tests).

- [ ] **Step 6: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "Quran root view model: debounced search state and bookmark rows"
```

---

### Task 5: Quran root screen: search sections and the Bookmarks tab

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/QuranRootScreen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt` (pass `onRemoveBookmark`)

**Interfaces:**
- Consumes: `SearchState`, `BookmarkRow`, `RootTab.BOOKMARKS`, strings from Task 3, glyph `drawBookmark`.
- Produces: `QuranRootScreen` gains `onRemoveBookmark: (surah: Int, ayah: Int) -> Unit`.

- [ ] **Step 1: Search sections.** In `QuranRootScreen`'s `LazyColumn`, when `ready.search !is SearchState.Idle`:
  - Do not emit the `tabs` item or the continue card; emit instead:
  - If `ready.filteredSurahs` is non-empty: a section label item (`Text(stringResource(Res.string.quran_section_surahs).uppercase(), style = TaqwaText.sectionLabel, color = colors.textTertiary)` with the `SettingsGutter + 4.dp` start padding the settings screens use for labels), then `surahListItems(ready.filteredSurahs.take(5), ...)` as today.
  - The ayah section: label `stringResource(Res.string.quran_section_ayahs, countText)` where `countText` is `format.localizedDigits(hits.size)` or `"${format.localizedDigits(100)}+"` when `capped`, shown only in the `Results` state; then a card list built with the same `CardTopCap` / `CardRow` / `CardBottomCap` items as the surah list, one `SearchHitRow` per hit (`key = "hit-${surah}-${ayah}"`), or a single row with `quran_search_empty` in `TaqwaText.caption` / `textSecondary` when there are no hits. While `Searching`, emit nothing for the ayah section (the surah section still shows).
- [ ] **Step 2: `SearchHitRow(hit, surah, query, onOpen)`**: a `Column` with 14 dp horizontal and 10 dp vertical padding, clickable without indication:
  1. `Text("${surah.displayName(arabic)} · ${format.localizedDigits(hit.surah)}:${format.localizedDigits(hit.ayah)}", style = TaqwaText.caption, color = colors.textSecondary)`.
  2. `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Text(hit.arabic, fontFamily = mushafFamily(), fontSize = 18.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()) }`.
  3. If `hit.translation != null`: the translation as an `AnnotatedString` where every case-insensitive occurrence of `query.trim()` is styled `SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)`, base style `TaqwaText.caption`, `color = colors.textSecondary`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, wrapped in the translation's own direction (reuse the `RTL_TRANSLATION_LANGUAGES` rule: make that set `internal` in `AyahCard.kt` and use the root state's translation language — add `translationLanguage: String` to `Ready` in the view model if it is not there: it is the language of `translationId`'s `TranslationInfo`, default `"en"`).
  Extract the highlighting into `internal fun highlightMatches(text: String, query: String, style: SpanStyle): AnnotatedString` in a new file `SearchHighlight.kt` and unit test it in `SearchHighlightTest.kt` (two occurrences, case-insensitive, empty query returns plain text).
- [ ] **Step 3: Bookmarks tab.** `TaqwaSegmented` gets three options (`quran_tab_surah`, `quran_tab_juz`, `quran_tab_bookmarks`) and `selectedIndex` 0/1/2. Add `bookmarkListItems(rows, onOpen, onRemove)` in the shape of `juzListItems`: `BookmarkRowView` shows `surah.displayName(arabic)` (rowLabel) and `stringResource(Res.string.quran_ayah_n, digits(ayah))` beside it in caption, a second line `"${stringResource(Res.string.quran_juz_n, digits(juz))} · ${stringResource(Res.string.quran_page_n, digits(page))}"` (add `quran_page_n` "Page %1$s" / «صفحة %1$s» if no such string exists; check `quran_juz_page` first and reuse it if its arguments fit), then the Arabic one line at 18 sp in the Mushaf font RTL, ellipsised; trailing a 44 dp `Box` with a 20 dp `Canvas` drawing `drawBookmark(colors.accent, filled = true)`, clickable without indication → `onRemove(surah, ayah)`. Empty list → one `CardRow` with `quran_bookmarks_empty` in caption/secondary with 14 dp padding.
- [ ] **Step 4: Opening.** Search hits and bookmark rows open through the existing `open(surah, ayah)` (reader in Translation mode, `pageFor` + Mushaf otherwise).
- [ ] **Step 5: Wire `onRemoveBookmark = viewModel::removeBookmark` in `App.kt`.**
- [ ] **Step 6: Build and look.** `./gradlew :androidApp:assembleDebug -q`; install on the emulator (`~/platform-tools/adb -s emulator-5554 install -r <apk>`), open the Quran tab, type "merciful" and «الرحمن», open a hit, go back (query kept), switch to Bookmarks (empty state). Save screenshots to `build/review-shots/2b/`. Run `./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.feature.quran.*' -q`.
- [ ] **Step 7: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "Quran root: search results beneath the field, Bookmarks tab"
```

---

### Task 6: Reader: bookmark, copy, share on the ayah card

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/ReaderViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/AyahCard.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/ReaderScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/AyahActions.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/ReaderViewModelTest.kt` (append)

**Interfaces:**
- Consumes: `BookmarkStore`, `AyahShareText`, `shareText`, glyphs.
- Produces:
  ```kotlin
  // ReaderUiState.Ready gains: val bookmarked: Set<Int>, val translationName: String?
  class ReaderViewModel(source, settings, bookmarks: BookmarkStore, languageTag, surah) {
      fun toggleBookmark(ayah: Int)
      fun shareTextFor(ayah: Int, surahName: String, digits: (Int) -> String): String?   // null before Ready
  }
  @Composable fun AyahActions(bookmarked: Boolean, onBookmark: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit)
  // AyahCard gains: bookmarked: Boolean, actions: (@Composable () -> Unit)?  (drawn below the content when selected)
  ```

- [ ] **Step 1: Failing view-model tests** (append; use the existing `source()`/`settingsRepo()` helpers and a `BookmarkStore` over a temp DataStore as in Task 4):

```kotlin
    @Test
    fun bookmarkedAyahsOfThisSurahArriveInStateAndToggleFlips() = runTest {
        val store = bookmarks("reader-bm")
        store.toggle(2, 3); store.toggle(1, 1)
        val vm = ReaderViewModel(source(), settingsRepo("reader-bm"), store, "en", surah = 2)
        vm.start(backgroundScope)
        assertEquals(setOf(3), vm.awaitReady().bookmarked)
        vm.toggleBookmark(3); advanceUntilIdle()
        assertEquals(emptySet(), (vm.state.value as ReaderUiState.Ready).bookmarked)
        vm.toggleBookmark(5); advanceUntilIdle()
        assertEquals(setOf(5), (vm.state.value as ReaderUiState.Ready).bookmarked)
    }

    @Test
    fun shareTextCarriesTheTranslationOnlyWhenOneIsShown() = runTest {
        val repo = settingsRepo("share-with")
        val vm = ReaderViewModel(source(), repo, bookmarks("share-with"), "en", surah = 2)
        vm.start(backgroundScope); vm.awaitReady()
        val with = vm.shareTextFor(1, "Al-Baqarah") { it.toString() }!!
        assertTrue(with.contains("(Saheeh International)"))
        assertTrue(with.endsWith("Al-Baqarah 2:1"))
        repo.setReadingSettings(ReadingSettings(translationId = ReadingSettings.NO_TRANSLATION)); advanceUntilIdle()
        val without = vm.shareTextFor(1, "Al-Baqarah") { it.toString() }!!
        assertFalse(without.contains("Saheeh"))
    }
```

- [ ] **Step 2: Implement in the view model**: constructor gains `bookmarks: BookmarkStore` (third parameter); `start` also collects `bookmarks.bookmarks` and stores `bookmarked = list.filter { it.surah == surah }.map { it.ayah }.toSet()` into `Ready` (keep a private field so `applySettings` re-emits it); `translationName` = the `TranslationInfo.name` of the effective translation id, null when `NO_TRANSLATION`; `toggleBookmark(ayah)` launches `bookmarks.toggle(surah, ayah)`; `shareTextFor` builds `AyahShareText.format(arabic = ayah.text, ayahNumber = ayah.number, translation = translation[ayah.number]?.let { it to translationName!! }, surahName, surah, ayah, digits)`.

- [ ] **Step 3: `AyahActions.kt`**: a `Row` of three `ActionButton(glyph, label, onClick)`: each a `Row` 44 dp tall, clickable without indication, a 18 dp `Canvas` drawing the glyph in `colors.accent` and the label in `TaqwaText.caption` `colors.textSecondary`, 6 dp apart, 20 dp between buttons. Copy shows `quran_action_copied` for 1.5 s after a tap (`LaunchedEffect` keyed on a counter). Labels: `quran_action_bookmark`/`quran_action_bookmarked`, `quran_action_copy`, `quran_action_share`.

- [ ] **Step 4: `AyahCard`**: add `bookmarked: Boolean` (draws a 14 dp filled bookmark in `colors.accent` at the top-start of the content column, before the Arabic, only when true) and `actions: (@Composable () -> Unit)?`; when `selected && actions != null`, draw `Spacer(8.dp)`, `CardDivider()`, then `actions()` at the bottom of the content column.

- [ ] **Step 5: `ReaderScreen`**: pass `bookmarked = ayah.number in ready.bookmarked` and, for the selected card, `actions = { AyahActions(bookmarked = …, onBookmark = { onToggleBookmark(ayah.number) }, onCopy = { shareTextFor(ayah.number)?.let { clipboard.setText(AnnotatedString(it)) } }, onShare = { shareTextFor(ayah.number)?.let(::shareText) }) }` where `clipboard = LocalClipboardManager.current` and `shareTextFor` is a new screen parameter `(Int) -> String?` bound in `App.kt` to `{ ayah -> viewModel.shareTextFor(ayah, surahName, format::localizedDigits) }` with `surahName = ready.surah.displayName(arabic)`. New `ReaderScreen` parameters: `onToggleBookmark: (Int) -> Unit`, `shareTextFor: (Int) -> String?`.

- [ ] **Step 6: App.kt**: pass `bookmarks = container.bookmarkStore` to `ReaderViewModel`; wire the two new callbacks.

- [ ] **Step 7: Tests + build**: `./gradlew :shared:testDebugUnitTest --tests 'world.taqwa.app.feature.quran.*' -q` green; `./gradlew :androidApp:assembleDebug -q`; on the emulator tap a card, bookmark it (glyph fills, badge appears), copy (label flips to Copied), share (chooser opens; dismiss). Screenshots to `build/review-shots/2b/`.

- [ ] **Step 8: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "Reader: bookmark, copy and share on the selected ayah card"
```

---

### Task 7: Mushaf: actions on the reference pill

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/MushafViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/MushafScreen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/MushafPageView.kt` (`ReferencePill`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/MushafViewModelTest.kt` (append)

**Interfaces:**
- Produces:
  ```kotlin
  // MushafUiState.Ready gains: val bookmarked: Set<Pair<Int, Int>>
  class MushafViewModel(source, settings, bookmarks: BookmarkStore, languageTag, startPage) {
      fun toggleBookmark(surah: Int, ayah: Int)
      suspend fun shareTextFor(surah: Int, ayah: Int, surahName: String, digits: (Int) -> String): String
  }
  // MushafPageView gains: bookmarked: Boolean, onBookmark: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit
  ```

- [ ] **Step 1: Failing tests**: bookmarks arrive as `(surah, ayah)` pairs in `Ready`; `toggleBookmark` flips; `shareTextFor(1, 2, "Al-Fatihah") { it.toString() }` equals `AyahShareText.format(<1:2 text from the fake>, 2, null, "Al-Fatihah", 1, 2) { it.toString() }` (no translation ever, spec §2.5).
- [ ] **Step 2: Implement**: constructor gains `bookmarks: BookmarkStore` (third parameter); `start` collects the store into `Ready.bookmarked` (all bookmarks, as pairs, since a page can hold several surahs); `shareTextFor` reads `source.ayahs(surah)` (cache per surah) and formats without translation.
- [ ] **Step 3: `ReferencePill`** becomes a `Row` inside the same hairline surface: the reference `Text` (tap → clear, as now), a 1 dp × 16 dp hairline separator, then three 44 dp `Box` buttons each with an 18 dp `Canvas` (`drawBookmark(accent, filled)`, `drawCopy(accent)`, `drawShare(accent)`), clickable without indication. Keep the 12 dp corner radius and the bottom-centre placement.
- [ ] **Step 4: `MushafScreen`** passes `bookmarked = highlighted in ready.bookmarked`, and copy/share through a new `shareTextFor: suspend (Int, Int) -> String` parameter (launch in `rememberCoroutineScope()`), plus `onToggleBookmark: (Int, Int) -> Unit`. `App.kt` wires them like the reader's.
- [ ] **Step 5: Tests + build + emulator check** (tap a line, bookmark it from the pill, then confirm it appears in the root's Bookmarks tab). Screenshots to `build/review-shots/2b/`.
- [ ] **Step 6: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "Mushaf: bookmark, copy and share from the reference pill"
```

---

### Task 8: Docs and the whole-suite run

**Files:**
- Modify: `docs/superpowers/specs/2026-09-07-taqwa-quran-reader-design.md` (a one-line pointer at the top of §2.1, §2.3, §2.4, §2.5 to the 2b spec)
- Modify: `docs/BUILD-LOG.md` (a "Slice 2b" section: what was built, decisions taken without Mohamed, test totals)
- Modify: `README.md` (feature list mentions search, bookmarks, share, if it lists features)

- [ ] **Step 1: Run everything**: `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && ./scripts/test.sh 2>&1 | tail -3` (all targets) and `./scripts/ios-build.sh 2>&1 | grep -E "BUILD SUCCEEDED|BUILD FAILED"`.
- [ ] **Step 2: Write the docs.**
- [ ] **Step 3: Commit**

```bash
cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa && git add -A && git commit -m "Slice 2b: docs and build log"
```

The device round on the S23 and the iPhone 12 is the controller's, after the final whole-branch review.
