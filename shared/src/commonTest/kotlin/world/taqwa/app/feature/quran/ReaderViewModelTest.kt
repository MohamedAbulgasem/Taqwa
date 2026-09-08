package world.taqwa.app.feature.quran

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.Juz
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [FakeQuranSource] (shared with [QuranRootViewModelTest]) fed a small surah list — 1, 2, 3, 9 and
 * 114 — that is just enough to exercise every rule [ReaderViewModel] applies: the basmala's two
 * exceptions (1 has none of its own, since it is ayah 1; 9 has none at all), a "next surah" that
 * exists (2 -> 3) and one that does not (114 -> null), and a translation/transliteration id that
 * may or may not be bundled.
 *
 * [ReaderViewModel.start] launches a settings-observing collector that runs for as long as its
 * scope lives — exactly like [world.taqwa.app.feature.qibla.QiblaViewModel.start] — so every test
 * here hands it `backgroundScope` (auto-cancelled when the test body finishes) rather than `this`,
 * and waits for the first `Ready` state through a genuine suspending [kotlinx.coroutines.flow.first]
 * rather than reading `.value` immediately: [SettingsRepository] is real, disk-backed DataStore
 * even in tests, so its first read is real (non-virtual) I/O that a bare `.value` check would race.
 */
class ReaderViewModelTest {

    private fun settingsRepo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-reader-test-$name.preferences_pb".toPath() },
    )

    private fun ayahsFor(surah: Int, count: Int, page: Int = 2, juz: Int = 1): List<Ayah> =
        (1..count).map { n -> Ayah(surah, n, "$surah:$n uthmani text", page, juz, 1, 0) }

    private val surahs = listOf(
        Surah(1, "الفاتحة", "Al-Faatiha", "The Opening", Revelation.MAKKI, 7, 1, 1),
        Surah(2, "البقرة", "Al-Baqara", "The Cow", Revelation.MADANI, 286, 2, 1),
        Surah(3, "آل عمران", "Aal-i-Imraan", "The Family of Imraan", Revelation.MADANI, 200, 50, 3),
        Surah(9, "التوبة", "At-Tawbah", "The Repentance", Revelation.MADANI, 129, 187, 10),
        // Surah 9's own next-surah lookup needs this to exist even though no test asserts on it.
        Surah(10, "يونس", "Yunus", "Jonah", Revelation.MAKKI, 109, 208, 11),
        Surah(114, "الناس", "An-Naas", "Mankind", Revelation.MAKKI, 6, 604, 30),
    )

    private val sahih = TranslationInfo("en.sahih", "en", "Saheeh International", "Saheeh International", "licence", "url", TextKind.TRANSLATION)
    private val tafsir = TranslationInfo("ar.muyassar", "ar", "Tafsir al-Muyassar", "King Fahd Complex", "licence", "url", TextKind.TAFSIR)
    private val french = TranslationInfo("fr.hamidullah", "fr", "Hamidullah", "Hamidullah", "licence", "url", TextKind.TRANSLATION)
    private val transliterationInfo = TranslationInfo("en.transliteration", "en", "Transliteration", "-", "licence", "url", TextKind.TRANSLITERATION)

    private fun source(
        ayahsBySurah: Map<Int, List<Ayah>> = mapOf(
            1 to ayahsFor(1, 7),
            2 to ayahsFor(2, 286),
            3 to ayahsFor(3, 200),
            9 to ayahsFor(9, 129),
            114 to ayahsFor(114, 6),
        ),
        translationsList: List<TranslationInfo> = listOf(sahih),
        translationTextsById: Map<String, Map<Int, Map<Int, String>>> = mapOf(
            "en.sahih" to ayahsBySurah.mapValues { (_, ayahs) -> ayahs.associate { it.number to "en ${it.surah}:${it.number}" } },
            "en.transliteration" to ayahsBySurah.mapValues { (_, ayahs) -> ayahs.associate { it.number to "tl ${it.surah}:${it.number}" } },
        ),
    ) = FakeQuranSource(
        surahList = surahs,
        juzList = listOf(Juz(1, 1, 1)),
        ayahsBySurah = ayahsBySurah,
        translationsList = translationsList,
        translationTextsById = translationTextsById,
    )

    private suspend fun ReaderViewModel.awaitReady(): ReaderUiState.Ready =
        state.first { it is ReaderUiState.Ready } as ReaderUiState.Ready

    @Test
    fun basmalaIsAbsentForAlFaatihaWhichIsAyahOneItself() = runTest {
        val vm = ReaderViewModel(source(), settingsRepo("basmala-1"), "en", surah = 1)
        vm.start(backgroundScope)
        assertNull(vm.awaitReady().basmala)
    }

    @Test
    fun basmalaIsShownForOrdinarySurahsReadFromAyahOneOfSurahOne() = runTest {
        val src = source()
        val vm = ReaderViewModel(src, settingsRepo("basmala-2"), "en", surah = 2)
        vm.start(backgroundScope)
        // Never a literal: it must be exactly the database's own 1:1 text.
        assertEquals(src.ayahs(1).first().text, vm.awaitReady().basmala)
    }

    @Test
    fun basmalaIsAbsentForAtTawbahWhichHasNoneAtAll() = runTest {
        val vm = ReaderViewModel(source(), settingsRepo("basmala-9"), "en", surah = 9)
        vm.start(backgroundScope)
        assertNull(vm.awaitReady().basmala)
    }

    @Test
    fun translationLoadsForTheStoredId() = runTest {
        val repo = settingsRepo("translation-ok")
        repo.setReadingSettings(ReadingSettings(translationId = "en.sahih"))
        val vm = ReaderViewModel(source(), repo, "en", surah = 2)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()
        assertEquals("en 2:1", ready.translation[1])
        // The exposed translations list carries the same id's own name (spec §2.5's sheet reads
        // this rather than a literal), not just whatever text was fetched for the cards.
        assertEquals("Saheeh International", ready.translations.first { it.id == ready.settings.translationId }.name)
    }

    @Test
    fun anUnknownTranslationIdFallsBackToSaheehInternational() = runTest {
        val repo = settingsRepo("translation-fallback")
        repo.setReadingSettings(ReadingSettings(translationId = "xx.unbundled"))
        val vm = ReaderViewModel(source(), repo, "en", surah = 2)
        vm.start(backgroundScope)
        // The bundled en.sahih text, not an empty map from a translation id nothing provides.
        assertEquals("en 2:1", vm.awaitReady().translation[1])
    }

    @Test
    fun transliterationIsNullWhenTheSettingIsOff() = runTest {
        val repo = settingsRepo("translit-off")
        repo.setReadingSettings(ReadingSettings(transliteration = false))
        val vm = ReaderViewModel(source(), repo, "en", surah = 2)
        vm.start(backgroundScope)
        assertNull(vm.awaitReady().transliteration)
    }

    @Test
    fun transliterationLoadsWhenTheSettingIsOn() = runTest {
        val repo = settingsRepo("translit-on")
        repo.setReadingSettings(ReadingSettings(transliteration = true))
        val vm = ReaderViewModel(source(), repo, "en", surah = 2)
        vm.start(backgroundScope)
        assertEquals("tl 2:1", vm.awaitReady().transliteration?.get(1))
    }

    @Test
    fun nextSurahIsTheFollowingSurahWhenOneExists() = runTest {
        val vm = ReaderViewModel(source(), settingsRepo("next-exists"), "en", surah = 2)
        vm.start(backgroundScope)
        assertEquals(3, vm.awaitReady().nextSurah?.number)
    }

    @Test
    fun nextSurahIsNullAfterAnNas() = runTest {
        val vm = ReaderViewModel(source(), settingsRepo("next-114"), "en", surah = 114)
        vm.start(backgroundScope)
        assertNull(vm.awaitReady().nextSurah)
    }

    @Test
    fun theFirstVisibleAyahIsNotWrittenBeforeTheDebounceElapses() = runTest {
        val repo = settingsRepo("debounce-early")
        val vm = ReaderViewModel(source(), repo, "en", surah = 2)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onFirstVisibleAyah(5)
        advanceTimeBy(300)
        // The debounce job is still parked in its `delay(500)` at 300 ms of virtual time — this
        // has nothing to do with real I/O timing, so a plain (non-waiting) read is deterministic.
        assertNull(repo.readingPosition.first())
    }

    @Test
    fun theFirstVisibleAyahIsWrittenOnceAfterTheDebounceElapses() = runTest {
        val repo = settingsRepo("debounce-late")
        val src = source()
        val vm = ReaderViewModel(src, repo, "en", surah = 2)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onFirstVisibleAyah(5)
        advanceTimeBy(600)
        val expectedPage = src.ayahs(2).first { it.number == 5 }.page
        // A predicate wait, not `.first()`: advancing virtual time only unparks the debounce
        // job's `delay`; the write after it is real disk I/O the test must still wait out.
        assertEquals(ReadingPosition(2, 5, expectedPage), repo.readingPosition.first { it != null })
    }

    @Test
    fun rapidScrollingCollapsesIntoASingleWriteForTheLatestAyah() = runTest {
        val repo = settingsRepo("debounce-collapse")
        val vm = ReaderViewModel(source(), repo, "en", surah = 2)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onFirstVisibleAyah(5)
        advanceTimeBy(200)
        vm.onFirstVisibleAyah(6)
        advanceTimeBy(200)
        vm.onFirstVisibleAyah(7)
        advanceTimeBy(600)
        assertEquals(7, repo.readingPosition.first { it != null }?.ayah)
    }

    @Test
    fun theCaptionFollowsTheFirstVisibleAyahAfterTheDebounce() = runTest {
        val repo = settingsRepo("caption")
        val src = source()
        val vm = ReaderViewModel(src, repo, "en", surah = 2)
        vm.start(backgroundScope)
        vm.awaitReady()

        vm.onFirstVisibleAyah(5)
        advanceTimeBy(600)
        val ready = vm.state.first { (it as? ReaderUiState.Ready)?.currentAyah == 5 } as ReaderUiState.Ready
        val ayah5 = src.ayahs(2).first { it.number == 5 }
        assertEquals(ayah5.juz to ayah5.page, ready.caption)
    }

    @Test
    fun orderForSheetPutsTafsirFirstThenSortsTheRestByLanguage() {
        // The pure function directly, per an already-filtered list (no transliteration) — the
        // shape [ReaderViewModel] itself feeds it, and the easiest place to pin the grouping rule.
        val ordered = orderForSheet(listOf(french, sahih, tafsir))
        assertEquals(listOf(tafsir, sahih, french), ordered)
    }

    @Test
    fun previewAyahIsAlFatihaAyahTwoWithTheRoundel() = runTest {
        val src = source()
        val vm = ReaderViewModel(src, settingsRepo("preview-ayah"), "en", surah = 2)
        vm.start(backgroundScope)
        val expected = QuranText.withMarker(src.ayahs(1)[1].text, 2)
        assertEquals(expected, vm.awaitReady().previewAyah)
    }

    @Test
    fun previewAyahIsTheSameRegardlessOfWhichSurahIsOpen() = runTest {
        val src = source()
        val vm = ReaderViewModel(src, settingsRepo("preview-ayah-surah1"), "en", surah = 1)
        vm.start(backgroundScope)
        val expected = QuranText.withMarker(src.ayahs(1)[1].text, 2)
        assertEquals(expected, vm.awaitReady().previewAyah)
    }

    @Test
    fun translationsListExcludesTransliterationAndPutsTafsirFirstThenByLanguage() = runTest {
        val src = source(translationsList = listOf(french, transliterationInfo, sahih, tafsir))
        val vm = ReaderViewModel(src, settingsRepo("translations-order"), "en", surah = 2)
        vm.start(backgroundScope)
        val ids = vm.awaitReady().translations.map { it.id }
        assertEquals(listOf("ar.muyassar", "en.sahih", "fr.hamidullah"), ids)
    }

    @Test
    fun writingTheReadingPositionDirectlyDoesNotReloadTheTranslation() = runTest {
        // Regression for the distinctUntilChanged fix in ReaderViewModel.start: readingSettings
        // and readingPosition are both read from the same underlying DataStore file, so a plain
        // position write still makes that file's own Flow re-emit. Without distinctUntilChanged
        // dropping the resulting (unchanged) ReadingSettings object, applySettings re-ran and
        // re-fetched the translation on every scroll-stop, not just on an actual settings change.
        val repo = settingsRepo("translation-loads-position")
        val src = source()
        val vm = ReaderViewModel(src, repo, "en", surah = 2)
        vm.start(backgroundScope)
        vm.awaitReady()

        val loadsBefore = src.translationLoads.size
        repo.setReadingPosition(ReadingPosition(2, 5, 2))
        // No event to await here — asserting an absence of a reload, so a short real-time wait
        // covers the flow's own chance to (wrongly) re-emit before the assertion runs.
        advanceTimeBy(50)
        assertEquals(loadsBefore, src.translationLoads.size)
    }

    @Test
    fun updateSettingsPersistsThroughTheRepositoryAndReEmits() = runTest {
        val repo = settingsRepo("update-settings")
        val vm = ReaderViewModel(source(), repo, "en", surah = 2)
        vm.start(backgroundScope)
        val ready = vm.awaitReady()

        vm.updateSettings(ready.settings.copy(arabicSizeSp = 34))
        val updated = vm.state.first { (it as? ReaderUiState.Ready)?.settings?.arabicSizeSp == 34 } as ReaderUiState.Ready
        assertEquals(34, updated.settings.arabicSizeSp)
        assertEquals(34, repo.readingSettings("en").first().arabicSizeSp)
    }
}
