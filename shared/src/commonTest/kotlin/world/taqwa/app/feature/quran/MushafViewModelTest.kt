package world.taqwa.app.feature.quran

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.LineType
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.SettingsRepository
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals

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

    /**
     * A fresh store per run, not just per test: `runTest` drains the remaining virtual time when
     * the body ends, so the debounce this file deliberately does *not* wait out still fires
     * afterwards and writes. A fixed path would carry that write into the next run and fail the
     * "nothing written yet" assertion for reasons that have nothing to do with the code.
     */
    private fun settingsRepo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath {
            "/tmp/taqwa-mushaf-test-$name-${Random.nextULong()}.preferences_pb".toPath()
        },
    )

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

    private fun source() = FakeQuranSource(
        surahList = surahs,
        ayahsBySurah = mapOf(1 to alFatiha),
        translationsList = listOf(sahih),
        translationTextsById = mapOf("en.sahih" to mapOf(1 to alFatiha.associate { it.number to "en 1:${it.number}" })),
        pagesByNumber = mapOf(1 to MUSHAF_PAGE_1, 2 to MUSHAF_PAGE_2, 3 to MUSHAF_PAGE_3),
    )

    private suspend fun MushafViewModel.awaitReady(): MushafUiState.Ready =
        state.first { it is MushafUiState.Ready } as MushafUiState.Ready

    @Test
    fun aPageLoadsItsLinesFromTheSource() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("page-load"), "en", startPage = 3)
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
        val vm = MushafViewModel(src, settingsRepo("page-cache"), "en", startPage = 3)
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
        val vm = MushafViewModel(src, settingsRepo("page-evict"), "en", startPage = 1)
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
        val vm = MushafViewModel(source(), settingsRepo("header"), "en", startPage = 3)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()

        assertEquals(3, ready.currentPage)
        assertEquals("Al-Baqarah", ready.surah.nameLatin)
        assertEquals(MUSHAF_PAGE_3.juz, ready.juz)
    }

    @Test
    fun turningThePageRenamesTheHeader() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("header-turn"), "en", startPage = 3)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onPageShown(1)
        val ready = vm.state.first { it is MushafUiState.Ready && it.currentPage == 1 } as MushafUiState.Ready
        assertEquals("Al-Faatiha", ready.surah.nameLatin)
    }

    @Test
    fun thePageShownIsNotWrittenBeforeTheDebounceElapses() = runTest {
        val repo = settingsRepo("position-early")
        val vm = MushafViewModel(source(), repo, "en", startPage = 1)
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
        val vm = MushafViewModel(source(), repo, "en", startPage = 1)
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
        val vm = MushafViewModel(source(), repo, "en", startPage = 1)
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
        val vm = MushafViewModel(source(), repo, "en", startPage = 3)
        vm.start(backgroundScope)
        vm.awaitReady()

        assertEquals(2 to 6, vm.switchToReader())
        assertEquals(ReadingMode.TRANSLATION, repo.readingSettings("en").first().mode)
    }

    @Test
    fun theBasmalaAndSizePreviewComeFromTheDatabase() = runTest {
        val src = source()
        val vm = MushafViewModel(src, settingsRepo("basmala"), "en", startPage = 2)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()

        assertEquals(src.ayahs(1).first().text, ready.basmala)
        assertEquals(QuranText.withMarker(src.ayahs(1)[1].text, 2), ready.previewAyah)
    }

    @Test
    fun everySurahOnThePageIsAvailableForItsBand() = runTest {
        val vm = MushafViewModel(source(), settingsRepo("bands"), "en", startPage = 1)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()

        // Page 1's first line is Al-Faatiha's surah band, which needs the full row to draw.
        assertEquals("الفاتحة", ready.surahsByNumber[1]?.nameArabic)
        assertEquals(7, ready.surahsByNumber[1]?.ayahCount)
    }
}
