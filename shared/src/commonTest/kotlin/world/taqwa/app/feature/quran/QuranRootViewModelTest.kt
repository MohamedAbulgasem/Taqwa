package world.taqwa.app.feature.quran

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.Juz
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.BookmarkStore
import world.taqwa.app.settings.SettingsRepository
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// FakeQuranSource lives in its own file now (FakeQuranSource.kt, same package), shared with
// ReaderViewModelTest.

class QuranRootViewModelTest {

    private fun settings(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-quran-root-test-$name.preferences_pb".toPath() },
    )

    /** A fresh file per store: [BookmarkStore.toggle] is a toggle, so a store that outlived an
     * earlier run would start with the bookmark already in it and the toggle would remove it. */
    private fun bookmarkStore(name: String) = BookmarkStore(
        PreferenceDataStoreFactory.createWithPath {
            "/tmp/taqwa-quran-root-bm-$name-${Random.nextULong()}.preferences_pb".toPath()
        },
    ) { 1L }

    /**
     * The ayah text the search tests search: Al-Faatiha 1 and 2 built out of [MUSHAF_PAGE_1]'s
     * words (line index 1 is ayah 1, index 2 is ayah 2), never retyped, with the trailing roundel
     * dropped so each ayah is its text alone.
     */
    private fun fixtureAyah(
        number: Int,
        lineIndex: Int,
        surah: Int = 1,
        juz: Int = 1,
        page: Int = 1,
    ) = Ayah(
        surah = surah,
        number = number,
        text = MUSHAF_PAGE_1.lines[lineIndex].words.joinToString(" ") {
            it.text.substringBefore(QuranText.MARKER_SEPARATOR)
        },
        // Al-Faatiha 1's own juz and page by default; the bookmark-row test borrows this text for
        // another surah so its row's juz and page are two different numbers and can be told apart.
        page = page, juz = juz, hizbQuarter = 1, sajdah = 0,
    )

    /**
     * The default source for these tests: the fake's four surahs and its bundled Saheeh
     * International catalogue, plus enough of Al-Faatiha — Arabic text and that translation's
     * words — for the two ayah searches to have something to find.
     */
    private fun searchSource() = FakeQuranSource(
        ayahsBySurah = mapOf(1 to listOf(fixtureAyah(1, 1), fixtureAyah(2, 2))),
        translationTextsById = mapOf(
            "en.sahih" to mapOf(
                1 to mapOf(
                    1 to "In the name of Allah, the Entirely Merciful",
                    2 to "All praise is due to Allah",
                ),
            ),
        ),
    )

    /**
     * [BookmarkStore] is real, disk-backed DataStore even in tests, so its emissions come back on
     * a real dispatcher that virtual time cannot advance to: the rows are awaited through the
     * state flow, the way [ReaderViewModelTest] awaits its first `Ready`, rather than by draining
     * the test scheduler.
     */
    private suspend fun QuranRootViewModel.awaitBookmarks(predicate: (List<BookmarkRow>) -> Boolean): List<BookmarkRow> =
        (state.first { it is QuranRootUiState.Ready && predicate(it.bookmarks) } as QuranRootUiState.Ready).bookmarks

    private fun viewModel(name: String, source: QuranSource = searchSource(), languageTag: String = "en") =
        QuranRootViewModel(source, settings(name), bookmarkStore(name), languageTag)

    @Test
    fun loadingIsTheStateBeforeAnythingHasBeenFetched() = runTest {
        assertEquals(QuranRootUiState.Loading, viewModel("initial").state.value)
    }

    @Test
    fun readyCarriesAllSurahsAndTheComputedJuzRowsWithNoFilterAndTheSurahTabByDefault() = runTest {
        val vm = viewModel("ready")
        vm.load()
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals(listOf(1, 2, 18, 114), ready.surahs.map { it.number })
        assertEquals("", ready.filter)
        assertEquals(RootTab.SURAH, ready.tab)
        assertEquals(listOf(1, 2, 18, 114), ready.filteredSurahs.map { it.number })
    }

    @Test
    fun juzOneSpansSurahOneAyahOneToSurahTwoAyahOneFortyOneWhenJuzTwoStartsAtTwoOneFortyTwo() = runTest {
        val vm = viewModel("juz-boundary")
        vm.load()
        val ready = vm.state.value as QuranRootUiState.Ready
        val juzOne = ready.juzs.first { it.number == 1 }
        assertEquals(1, juzOne.startSurah.number)
        assertEquals(1, juzOne.startAyah)
        assertEquals(2, juzOne.endSurah.number)
        assertEquals(141, juzOne.endAyah)
    }

    @Test
    fun theLastJuzInTheListEndsAtTheLastAyahOfTheLastSurah() = runTest {
        val source = FakeQuranSource(
            surahList = listOf(Surah(114, "الناس", "An-Naas", "Mankind", Revelation.MAKKI, 6, 604, 30)),
            juzList = listOf(Juz(30, 114, 1)),
        )
        val vm = viewModel("juz-last", source)
        vm.load()
        val ready = vm.state.value as QuranRootUiState.Ready
        val juzThirty = ready.juzs.single()
        assertEquals(114, juzThirty.endSurah.number)
        assertEquals(6, juzThirty.endAyah)
    }

    // A quarter of the real juz boundaries fall on the first ayah of a surah (15:1, 17:1, 21:1 …):
    // the juz before must end on the last ayah of the previous surah, not on "ayah 0".
    @Test
    fun aJuzEndingWhereTheNextSurahBeginsEndsOnThePreviousSurahsLastAyah() = runTest {
        val source = FakeQuranSource(
            surahList = listOf(
                Surah(14, "إبراهيم", "Ibrahim", "Abraham", Revelation.MAKKI, 52, 255, 13),
                Surah(15, "الحجر", "Al-Hijr", "The Rock", Revelation.MAKKI, 99, 262, 14),
            ),
            juzList = listOf(Juz(13, 14, 1), Juz(14, 15, 1)),
        )
        val vm = viewModel("juz-surah-boundary", source)
        vm.load()
        val ready = vm.state.value as QuranRootUiState.Ready
        val juzThirteen = ready.juzs.first { it.number == 13 }
        assertEquals(14, juzThirteen.endSurah.number)
        assertEquals(52, juzThirteen.endAyah)
        val juzFourteen = ready.juzs.first { it.number == 14 }
        assertEquals(15, juzFourteen.startSurah.number)
        assertEquals(99, juzFourteen.endAyah)
    }

    @Test
    fun filteringByLatinNameIsCaseInsensitive() = runTest {
        val vm = viewModel("filter-latin")
        vm.load()
        vm.setFilter("kahf")
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals(listOf(18), ready.filteredSurahs.map { it.number })
    }

    @Test
    fun filteringByArabicNameIgnoresHarakat() = runTest {
        val vm = viewModel("filter-arabic")
        vm.load()
        // Bare consonants, no tashkeel — matches surah 18's harakat-laden name in the fake.
        vm.setFilter("الكهف")
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals(listOf(18), ready.filteredSurahs.map { it.number })
    }

    @Test
    fun filteringByNumberMatches() = runTest {
        val vm = viewModel("filter-number")
        vm.load()
        vm.setFilter("114")
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals(listOf(114), ready.filteredSurahs.map { it.number })
    }

    @Test
    fun filteringByMeaningMatches() = runTest {
        val vm = viewModel("filter-meaning")
        vm.load()
        vm.setFilter("cave")
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals(listOf(18), ready.filteredSurahs.map { it.number })
    }

    @Test
    fun filteringIgnoresHyphensAndApostrophesInTheLatinName() = runTest {
        val vm = viewModel("filter-hyphen")
        vm.load()
        // "An-Naas" with its hyphen removed is "annaas" — the query a user who does not know
        // the exact spelling is likely to type.
        vm.setFilter("annaas")
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals(listOf(114), ready.filteredSurahs.map { it.number })
    }

    @Test
    fun filteringMatchesEveryCommonSpellingThroughTheAliasesAndTheFold() = runTest {
        val vm = viewModel("filter-spellings")
        vm.load()
        // Tanzil's doubled vowel, the bare name, the curated name with and without its final h.
        listOf("faatiha", "fatiha", "Fatihah", "Al-Fatihah", "baqara", "Baqarah").forEach { query ->
            vm.setFilter(query)
            val ready = vm.state.value as QuranRootUiState.Ready
            assertEquals(1, ready.filteredSurahs.size, "query '$query'")
        }
        vm.setFilter("annaas")
        assertEquals(listOf(114), (vm.state.value as QuranRootUiState.Ready).filteredSurahs.map { it.number })
    }

    @Test
    fun collapseLatinFoldsSpellingDifferencesThatAreNotDifferentNames() {
        assertEquals("almursalat", "Al-Mursalaat".collapseLatin())
        assertEquals("almursalat", "Al-Mursalat".collapseLatin())
        assertEquals("mursalat", "Mursalat".collapseLatin())
        assertEquals("alimran", "Ali 'Imran".collapseLatin())
        assertEquals("yasin", "Ya-Sin".collapseLatin())
        // Only doubled vowels fold; doubled consonants are kept ("Muzzammil").
        assertEquals("almuzzammil", "Al-Muzzammil".collapseLatin())
    }

    @Test
    fun switchingTabsIsReflectedInState() = runTest {
        val vm = viewModel("tabs")
        vm.load()
        vm.setTab(RootTab.JUZ)
        assertEquals(RootTab.JUZ, (vm.state.value as QuranRootUiState.Ready).tab)
    }

    @Test
    fun thereIsNoContinueCardBeforeAnyPositionHasBeenStored() = runTest {
        val vm = viewModel("continue-absent")
        vm.load()
        assertNull((vm.state.value as QuranRootUiState.Ready).continueCard)
    }

    @Test
    fun theContinueCardAppearsOnceAPositionIsStoredAndNamesItsJuz() = runTest {
        val name = "continue-present"
        val repo = settings(name)
        repo.setReadingPosition(ReadingPosition(surah = 18, ayah = 28, page = 293))
        val vm = QuranRootViewModel(searchSource(), repo, bookmarkStore(name), "en")
        vm.load()
        val card = (vm.state.value as QuranRootUiState.Ready).continueCard
        assertTrue(card != null)
        assertEquals(18, card.surah.number)
        assertEquals(28, card.ayah)
        // 18:28 falls after juz 3's start (18:5) and before juz 4's start (114:3) in the fake.
        assertEquals(3, card.juz)
    }

    @Test
    fun modeComesFromReadingSettings() = runTest {
        val name = "mode"
        val repo = settings(name)
        repo.setReadingSettings(ReadingSettings(mode = ReadingMode.MUSHAF))
        val vm = QuranRootViewModel(searchSource(), repo, bookmarkStore(name), "en")
        vm.load()
        assertEquals(ReadingMode.MUSHAF, (vm.state.value as QuranRootUiState.Ready).mode)
    }

    @Test
    fun theTranslationsOwnLanguageTravelsWithTheStateForTheSnippetsDirection() = runTest {
        val name = "translation-language"
        val repo = settings(name)
        repo.setReadingSettings(ReadingSettings(translationId = "ur.junagarhi"))
        val source = FakeQuranSource(
            translationsList = listOf(
                TranslationInfo("en.sahih", "en", "Saheeh International", "Saheeh International", "licence", "url", TextKind.TRANSLATION),
                TranslationInfo("ur.junagarhi", "ur", "\u062A\u0631\u062C\u0645\u06C1", "Junagarhi", "licence", "url", TextKind.TRANSLATION),
            ),
        )
        val vm = QuranRootViewModel(source, repo, bookmarkStore(name), "en")
        vm.load()
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals("ur.junagarhi", ready.translationId)
        assertEquals("ur", ready.translationLanguage)
    }

    @Test
    fun anUnbundledTranslationFallsBackToSaheehAndItsLanguage() = runTest {
        val name = "translation-language-fallback"
        val repo = settings(name)
        repo.setReadingSettings(ReadingSettings(translationId = "xx.gone"))
        val vm = QuranRootViewModel(searchSource(), repo, bookmarkStore(name), "en")
        vm.load()
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals("en.sahih", ready.translationId)
        assertEquals("en", ready.translationLanguage)
    }

    @Test
    fun theTranslationOfAReaderWhoTurnedTranslationsOffIsStillSearchable() = runTest {
        // "None" is a reading choice, not a search one: Arabic-only cards must not leave the Latin
        // search with no translation to run against (resolveTranslationId).
        val name = "translation-none"
        val repo = settings(name)
        repo.setReadingSettings(ReadingSettings(translationId = ReadingSettings.NO_TRANSLATION))
        val vm = QuranRootViewModel(searchSource(), repo, bookmarkStore(name), "en")
        vm.load()
        val ready = vm.state.value as QuranRootUiState.Ready
        assertEquals("en.sahih", ready.translationId)
        assertEquals("en", ready.translationLanguage)
    }

    @Test
    fun pageForDelegatesToTheSource() = runTest {
        val vm = viewModel("page-for")
        assertEquals(293, vm.pageFor(18, 1))
    }

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
    }

    @Test
    fun anArabicHitCarriesTheReadersTranslation() = runTest {
        val vm = viewModel("search-arabic-translated")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter("الحمد")
        advanceTimeBy(300)
        val results = (vm.state.value as QuranRootUiState.Ready).search as SearchState.Results
        assertEquals(listOf("All praise is due to Allah"), results.hits.map { it.translation })
    }

    @Test
    fun anArabicInterfaceShowsAnArabicHitWithoutATranslation() = runTest {
        val vm = viewModel("search-arabic-ui", languageTag = "ar")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter("الحمد")
        advanceTimeBy(300)
        val results = (vm.state.value as QuranRootUiState.Ready).search as SearchState.Results
        assertEquals(listOf(1 to 2), results.hits.map { it.surah to it.ayah })
        assertTrue(results.hits.all { it.translation == null })
    }

    @Test
    fun clearingTheQueryReturnsToIdleAndOnlyTheLastQueryLands() = runTest {
        val source = searchSource()
        val vm = QuranRootViewModel(source, settings("search-clear"), bookmarkStore("search-clear"), "en")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter("mer"); advanceTimeBy(100)
        vm.setFilter("merciful"); advanceTimeBy(300)
        assertEquals("merciful", ((vm.state.value as QuranRootUiState.Ready).search as SearchState.Results).query)
        // The point of the debounce: the keystroke that was superseded inside 250 ms never reached
        // the database at all, rather than reaching it and losing the race on the way back.
        assertEquals(listOf("merciful"), source.searchLoads)
        vm.setFilter(""); advanceTimeBy(300)
        assertEquals(SearchState.Idle, (vm.state.value as QuranRootUiState.Ready).search)
        assertEquals(listOf("merciful"), source.searchLoads)
    }

    @Test
    fun aSearchWithMoreHitsThanTheCapIsCutAtTheCapAndSaysSo() = runTest {
        // The view model asks for one more than the cap so "capped" is known rather than guessed:
        // 120 identical ayahs (Al-Faatiha 1's text, numbered 1..120) all match the query.
        val text = fixtureAyah(1, 1).text
        val source = FakeQuranSource(ayahsBySurah = mapOf(1 to (1..120).map { fixtureAyah(it, 1) }))
        val vm = QuranRootViewModel(source, settings("search-capped"), bookmarkStore("search-capped"), "en")
        vm.load(); vm.start(backgroundScope)
        vm.setFilter(text.split(' ').last())
        advanceTimeBy(300)
        val results = (vm.state.value as QuranRootUiState.Ready).search as SearchState.Results
        assertTrue(results.capped)
        assertEquals(100, results.hits.size)
    }

    @Test
    fun bookmarksArriveAsRowsNewestFirstAndRemoveDropsOne() = runTest {
        val store = bookmarkStore("rows")
        store.toggle(18, 1)
        // Al-Kahf's own juz and page (15, 293) on the fixture's text, so the two numbers the row
        // carries are different from each other and from the surah's — a row that had them the
        // wrong way round, or took them from anywhere but the ayah, could not pass.
        val source = FakeQuranSource(
            ayahsBySurah = mapOf(18 to listOf(fixtureAyah(1, 1, surah = 18, juz = 15, page = 293))),
        )
        val vm = QuranRootViewModel(source, settings("rows"), store, "en")
        vm.load(); vm.start(backgroundScope)
        val rows = vm.awaitBookmarks { it.isNotEmpty() }
        assertEquals(listOf(18 to 1), rows.map { it.surah.number to it.bookmark.ayah })
        assertTrue(rows.first().arabic.isNotBlank())
        assertEquals(15, rows.first().juz)
        assertEquals(293, rows.first().page)
        vm.removeBookmark(18, 1)
        assertEquals(emptyList(), vm.awaitBookmarks { it.isEmpty() })
    }
}
