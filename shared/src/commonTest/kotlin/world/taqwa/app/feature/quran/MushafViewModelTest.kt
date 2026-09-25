package world.taqwa.app.feature.quran

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.AyahShareText
import world.taqwa.app.quran.LineType
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Mushaf mode's view model and the two pure layout rules its page view leans on (spec §2.4).
 *
 * The pages are [MUSHAF_PAGE_1], [MUSHAF_PAGE_2] and [MUSHAF_PAGE_3] — dumped out of the bundled
 * database, never typed — fed through [FakeQuranSource], which counts its own [FakeQuranSource.pageLoads]
 * so the view model's page cache is testable without exposing the cache itself.
 *
 * Like [ReaderViewModelTest], every test hands [MushafViewModel.start] `backgroundScope` (the
 * settings collector runs for as long as its scope lives) and waits for `Ready` through a
 * suspending [first] rather than reading `.value`, because [SettingsRepository] is real,
 * disk-backed DataStore even here.
 */
class MushafViewModelTest {

    // The store's writes run on the test's own scheduler (`scope = backgroundScope`), as Android's
    // DataStore testing guidance has it. On its default IO scope a write raced the virtual clock,
    // and a test waiting for the stored value could hang until runTest gave up (seen in CI).
    /**
     * A fresh store per run, not just per test: `runTest` drains the remaining virtual time when
     * the body ends, so the debounce this file deliberately does *not* wait out still fires
     * afterwards and writes. A fixed path would carry that write into the next run and fail the
     * "nothing written yet" assertion for reasons that have nothing to do with the code.
     */
    private fun TestScope.settingsRepo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
            "/tmp/taqwa-mushaf-test-$name-${Random.nextULong()}.preferences_pb".toPath()
        },
    )

    /** A fresh file per store, for [ReaderViewModelTest]'s reason: [BookmarkStore.toggle] is a
     * toggle, so a store left behind by an earlier run would start with the bookmark already set
     * and the first toggle would remove it instead of adding it. */
    private fun TestScope.bookmarkStore(name: String) = BookmarkStore(
        PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
            "/tmp/taqwa-mushaf-bm-$name-${Random.nextULong()}.preferences_pb".toPath()
        },
    ) { 1L }

    private val surahs = listOf(
        Surah(1, "الفاتحة", "Al-Faatiha", "The Opening", Revelation.MAKKI, 7, 1, 1),
        Surah(2, "البقرة", "Al-Baqarah", "The Cow", Revelation.MADANI, 286, 2, 1),
        Surah(112, "الإخلاص", "Al-Ikhlaas", "Sincerity", Revelation.MAKKI, 4, 604, 30),
    )

    private val sahih = TranslationInfo("en.sahih", "en", "Saheeh International", "Saheeh International", "licence", "url", TextKind.TRANSLATION)

    /** Al-Faatiha's own ayahs, the only ones this screen reads: ayah 1 is the basmala and ayah 2
     * the reading sheet's size preview (spec §2.5), both taken from the database, never a literal. */
    private val alFatiha = MUSHAF_PAGE_1.lines
        .filter { it.type == LineType.TEXT }
        .flatMap { it.words }
        .groupBy { it.ayah }
        .map { (number, words) -> Ayah(1, number, words.joinToString(" ") { it.text }, 1, 1, 1, 0) }

    /** Al-Baqarah's ayahs as *page 3* carries them — partial, since the surah runs far past that
     * page, which is all the share text's own per-surah lookup needs; the point of having a second
     * surah here is that nothing loads it at startup, unlike Al-Faatiha. */
    private val alBaqarah = MUSHAF_PAGE_3.lines
        .filter { it.type == LineType.TEXT }
        .flatMap { it.words }
        .filter { it.surah == 2 }
        .groupBy { it.ayah }
        .map { (number, words) -> Ayah(2, number, words.joinToString(" ") { it.text }, 3, MUSHAF_PAGE_3.juz, 1, 0) }

    private fun source() = FakeQuranSource(
        surahList = surahs,
        ayahsBySurah = mapOf(1 to alFatiha, 2 to alBaqarah),
        translationsList = listOf(sahih),
        translationTextsById = mapOf("en.sahih" to mapOf(1 to alFatiha.associate { it.number to "en 1:${it.number}" })),
        pagesByNumber = mapOf(1 to MUSHAF_PAGE_1, 2 to MUSHAF_PAGE_2, 3 to MUSHAF_PAGE_3),
    )

    private suspend fun MushafViewModel.awaitReady(): MushafUiState.Ready =
        state.first { it is MushafUiState.Ready } as MushafUiState.Ready

    /** [BookmarkStore] is real, disk-backed DataStore even here, so its emissions arrive on a
     * dispatcher virtual time cannot advance: every bookmark assertion waits for the state to say
     * so rather than reading `.value` and racing the write. */
    private suspend fun MushafViewModel.awaitBookmarked(
        predicate: (Set<Pair<Int, Int>>) -> Boolean,
    ): Set<Pair<Int, Int>> =
        (state.first { it is MushafUiState.Ready && predicate(it.bookmarked) } as MushafUiState.Ready).bookmarked

    @Test
    fun aPageLoadsItsLinesFromTheSource() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("page-load"), bookmarkStore("page-load"), "en", startPage = 3)
        vm.start(backgroundScope)
        vm.awaitReady()

        val page = vm.page(3)
        assertEquals(15, page.lines.size)
        assertEquals(MUSHAF_PAGE_3.lines.first().text, page.lines.first().text)
        assertEquals(2 to 6, page.firstSurah to page.firstAyah)
    }

    @Test
    fun aPageAlreadyLoadedIsServedFromTheCache() = runTest {
        val src = source()
        val vm = MushafViewModel(src, settingsRepo("page-cache"), bookmarkStore("page-cache"), "en", startPage = 3)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.page(3)
        vm.page(3)
        vm.page(3)
        // The pager re-composes a page on every settle; only the first of those may hit the source.
        assertEquals(listOf(3), src.pageLoads)
    }

    @Test
    fun theCacheKeepsOnlyTheFiveMostRecentPages() = runTest {
        // Six numbered copies of one real page: this test is about eviction, not about content.
        val pages = (1..6).associateWith { MUSHAF_PAGE_3.copy(number = it) }
        val src = FakeQuranSource(
            surahList = surahs,
            ayahsBySurah = mapOf(1 to alFatiha),
            translationsList = listOf(sahih),
            pagesByNumber = pages,
        )
        val vm = MushafViewModel(src, settingsRepo("page-evict"), bookmarkStore("page-evict"), "en", startPage = 1)
        vm.start(backgroundScope)
        vm.awaitReady()

        (1..6).forEach { vm.page(it) }
        src.pageLoads.clear()
        vm.page(6)
        vm.page(1)
        // 6 is still cached; 1 fell out when 6 arrived, so only 1 goes back to the source.
        assertEquals(listOf(1), src.pageLoads)
    }

    @Test
    fun theHeaderNamesTheSurahAndJuzOfThePageShown() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("header"), bookmarkStore("header"), "en", startPage = 3)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()

        assertEquals(3, ready.currentPage)
        assertEquals("Al-Baqarah", ready.surah.nameLatin)
        assertEquals(MUSHAF_PAGE_3.juz, ready.juz)
    }

    @Test
    fun turningThePageRenamesTheHeader() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("header-turn"), bookmarkStore("header-turn"), "en", startPage = 3)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onPageShown(1)
        val ready = vm.state.first { it is MushafUiState.Ready && it.currentPage == 1 } as MushafUiState.Ready
        assertEquals("Al-Faatiha", ready.surah.nameLatin)
    }

    @Test
    fun thePageShownIsNotWrittenBeforeTheDebounceElapses() = runTest {
        val repo = settingsRepo("position-early")
        val vm = MushafViewModel(source(), repo, bookmarkStore("position-early"), "en", startPage = 1)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onPageShown(3)
        advanceTimeBy(300)
        // Still parked in the debounce's own delay at 300 ms of virtual time.
        assertEquals(null, repo.readingPosition.first())
    }

    @Test
    fun thePageShownIsWrittenAsTheFirstTextLinesAyahAfterTheDebounce() = runTest {
        val repo = settingsRepo("position-late")
        val vm = MushafViewModel(source(), repo, bookmarkStore("position-late"), "en", startPage = 1)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onPageShown(3)
        advanceTimeBy(500)
        // Page 3's first text line starts at 2:6 (spec §2.4) — not the page table's own row.
        assertEquals(ReadingPosition(2, 6, 3), repo.readingPosition.first { it != null })
    }

    @Test
    fun rapidPageTurnsCollapseIntoASingleWriteForTheLastPage() = runTest {
        val repo = settingsRepo("position-collapse")
        val vm = MushafViewModel(source(), repo, bookmarkStore("position-collapse"), "en", startPage = 1)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onPageShown(2)
        advanceTimeBy(200)
        vm.onPageShown(3)
        advanceTimeBy(200)
        vm.onPageShown(1)
        advanceTimeBy(500)
        assertEquals(ReadingPosition(1, 1, 1), repo.readingPosition.first { it != null })
    }

    @Test
    fun switchingToTheReaderReturnsThePagesFirstAyahAndStoresTranslationMode() = runTest {
        val repo = settingsRepo("switch")
        repo.setReadingSettings(ReadingSettings(mode = ReadingMode.MUSHAF))
        val vm = MushafViewModel(source(), repo, bookmarkStore("switch"), "en", startPage = 3)
        vm.start(backgroundScope)
        vm.awaitReady()

        assertEquals(2 to 6, vm.switchToReader())
        assertEquals(ReadingMode.TRANSLATION, repo.readingSettings("en").first().mode)
    }

    @Test
    fun theBasmalaAndSizePreviewComeFromTheDatabase() = runTest {
        val src = source()
        val vm = MushafViewModel(src, settingsRepo("basmala"), bookmarkStore("basmala"), "en", startPage = 2)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()

        assertEquals(src.ayahs(1).first().text, ready.basmala)
        assertEquals(QuranText.withMarker(src.ayahs(1)[1].text, 2), ready.previewAyah)
    }

    @Test
    fun everySurahOnThePageIsAvailableForItsBand() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("bands"), bookmarkStore("bands"), "en", startPage = 1)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()

        // Page 1's first line is Al-Faatiha's surah band, which needs the full row to draw.
        assertEquals("الفاتحة", ready.surahsByNumber[1]?.nameArabic)
        assertEquals(7, ready.surahsByNumber[1]?.ayahCount)
    }

    @Test
    fun bookmarksArriveInStateAsSurahAyahPairsAndToggleFlips() = runTest {
        val store = bookmarkStore("mushaf-bm")
        store.toggle(2, 6)
        val vm = MushafViewModel(source(), settingsRepo("mushaf-bm"), store, "en", startPage = 3)
        vm.start(backgroundScope)
        // All of them, not only the current page's: a page can hold the end of one surah and the
        // start of the next, so the pill's own ayah is looked up by the full pair (spec 2b §2.5).
        assertEquals(setOf(2 to 6), vm.awaitBookmarked { it.isNotEmpty() })

        vm.toggleBookmark(1, 1)
        assertEquals(setOf(2 to 6, 1 to 1), vm.awaitBookmarked { it.size == 2 })

        vm.toggleBookmark(2, 6)
        assertEquals(setOf(1 to 1), vm.awaitBookmarked { it.size == 1 })
    }

    @Test
    fun theShareTextNeverCarriesATranslation() = runTest {
        val src = source()
        val vm = MushafViewModel(src, settingsRepo("mushaf-share"), bookmarkStore("mushaf-share"), "en", startPage = 1)
        vm.start(backgroundScope)
        vm.awaitReady()

        // 1:2's text comes out of the fixture rather than being retyped, and the expectation is
        // the formatter's own no-translation output: the Mushaf shows no translation, so the
        // shared text may never carry one (spec 2b §2.5) whatever the reading settings say.
        // The name is the one this file's own surah table gives surah 1, so a share text and the
        // header can never be shown to disagree by the test spelling it a third way.
        val name = surahs.first { it.number == 1 }.nameLatin
        val expected = AyahShareText.format(src.ayahs(1)[1].text, 2, null, name, 1, 2) { it.toString() }
        assertEquals(expected, vm.shareTextFor(1, 2, name) { it.toString() })
    }

    @Test
    fun theShareTextIsNullForAnAyahTheSurahDoesNotHave() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("mushaf-share-miss"), bookmarkStore("mushaf-share-miss"), "en", startPage = 1)
        vm.start(backgroundScope)
        vm.awaitReady()

        // Al-Faatiha has seven ayahs, so the lookup inside shareTextFor misses and the pill's copy
        // and share get nothing to put out rather than a reference to an ayah that does not exist.
        assertNull(vm.shareTextFor(1, 99, "Al-Faatiha") { it.toString() })
    }

    @Test
    fun twoSharesOfTheSameSurahLoadItsAyahsOnce() = runTest {
        val src = source()
        val vm = MushafViewModel(src, settingsRepo("mushaf-share-cache"), bookmarkStore("mushaf-share-cache"), "en", startPage = 3)
        vm.start(backgroundScope)
        vm.awaitReady()
        // Al-Faatiha is loaded at startup for the basmala; Al-Baqarah is not, so the counter below
        // starts empty and every entry in it belongs to a share.
        src.ayahLoads.clear()

        assertNotNull(vm.shareTextFor(2, 6, "Al-Baqarah") { it.toString() })
        assertNotNull(vm.shareTextFor(2, 7, "Al-Baqarah") { it.toString() })
        // A surah's whole text is expensive to fetch and the reader taps several ayahs of the page
        // they are on, so the second share must come out of the cache.
        assertEquals(listOf(2), src.ayahLoads)
    }
}
