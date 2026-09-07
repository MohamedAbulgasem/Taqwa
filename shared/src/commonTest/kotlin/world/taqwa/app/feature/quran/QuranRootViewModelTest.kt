package world.taqwa.app.feature.quran

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.Juz
import world.taqwa.app.quran.MushafPage
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A hand-built stand-in for [QuranSource] with four surahs (1, 2, 18, 114 — matching the real
 * database's numbers, meanings and ayah counts) and just enough juz starts to exercise the
 * boundary maths without needing the eleven surahs in between. Surah 18's Arabic name carries
 * invented tashkeel deliberately (the real database stores bare consonants) so the harakat-
 * insensitive search test actually exercises [world.taqwa.app.quran.QuranText.normaliseForSearch]
 * rather than trivially matching on already-bare text.
 */
private class FakeQuranSource(
    private val surahList: List<Surah> = listOf(
        Surah(1, "الفاتحة", "Al-Faatiha", "The Opening", Revelation.MAKKI, 7, 1, 1),
        Surah(2, "البقرة", "Al-Baqara", "The Cow", Revelation.MADANI, 286, 2, 1),
        Surah(18, "ٱلۡكَهۡفِ", "Al-Kahf", "The Cave", Revelation.MAKKI, 110, 293, 15),
        Surah(114, "الناس", "An-Naas", "Mankind", Revelation.MAKKI, 6, 604, 30),
    ),
    private val juzList: List<Juz> = listOf(
        Juz(1, 1, 1),
        Juz(2, 2, 142),
        Juz(3, 18, 5),
        Juz(4, 114, 3),
    ),
) : QuranSource {
    override suspend fun surahs(): List<Surah> = surahList
    override suspend fun surah(number: Int): Surah = surahList.first { it.number == number }
    override suspend fun juzs(): List<Juz> = juzList
    override suspend fun ayahs(surah: Int): List<Ayah> = error("not needed by QuranRootViewModelTest")
    override suspend fun translations(): List<TranslationInfo> = error("not needed by QuranRootViewModelTest")
    override suspend fun translationTexts(translationId: String, surah: Int): Map<Int, String> =
        error("not needed by QuranRootViewModelTest")
    override suspend fun pageOf(surah: Int, ayah: Int): Int = when (surah) {
        1 -> 1
        2 -> 2
        18 -> 293
        114 -> 604
        else -> error("unknown surah $surah")
    }
    override suspend fun page(number: Int): MushafPage = error("not needed by QuranRootViewModelTest")
    override suspend fun surahOfPage(number: Int): Surah = error("not needed by QuranRootViewModelTest")
}

class QuranRootViewModelTest {

    private fun settings(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-quran-root-test-$name.preferences_pb".toPath() },
    )

    private fun viewModel(name: String, source: QuranSource = FakeQuranSource(), languageTag: String = "en") =
        QuranRootViewModel(source, settings(name), languageTag)

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
        val vm = QuranRootViewModel(FakeQuranSource(), repo, "en")
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
        val vm = QuranRootViewModel(FakeQuranSource(), repo, "en")
        vm.load()
        assertEquals(ReadingMode.MUSHAF, (vm.state.value as QuranRootUiState.Ready).mode)
    }

    @Test
    fun pageForDelegatesToTheSource() = runTest {
        val vm = viewModel("page-for")
        assertEquals(293, vm.pageFor(18, 1))
    }
}
