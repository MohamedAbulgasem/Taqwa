package world.taqwa.app.feature.quran

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.quran.Juz
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.settings.SettingsRepository
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
