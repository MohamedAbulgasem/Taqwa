package world.taqwa.app.feature.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.feature.recitation.QuranRecitation
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_reader_aa
import world.taqwa.app.resources.quran_sheet_size
import world.taqwa.app.resources.recitation_a11y_recitation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader's own state across the things that happen to it after it opens: the view model
 * issuing a new state (a bookmark, a settings change), and the app-wide "show me the recited ayah"
 * token, which outlives every reader it has ever been answered by.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderScreenStateTest {

    private val surah = Surah(2, "البقرة", "Al-Baqarah", "The Cow", Revelation.MADANI, ayahCount = 60, startPage = 2, startJuz = 1)
    private val ayahs = (1..60).map { Ayah(2, it, "آية $it", page = 2 + it / 10, juz = 1, hizbQuarter = 1, sajdah = 0) }

    private fun ready(bookmarked: Set<Int> = emptySet()) = ReaderUiState.Ready(
        surah = surah,
        basmala = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
        ayahs = ayahs,
        translation = ayahs.associate { it.number to "The meaning of ayah ${it.number}." },
        transliteration = null,
        settings = ReadingSettings(),
        nextSurah = null,
        currentAyah = 1,
        caption = 1 to 2,
        translationLanguage = "en",
        previewAyah = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ",
        translations = emptyList(),
        bookmarked = bookmarked,
        translationName = "Saheeh International",
    )

    /** Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test process. */
    @Composable
    private fun Reader(
        state: ReaderUiState,
        recitation: QuranRecitation,
        onFirstVisibleAyah: (Int) -> Unit = {},
        sheetOpenState: MutableState<Boolean> = remember { mutableStateOf(false) },
        initialAyah: Int = 1,
        selectInitialAyah: Boolean = false,
        followVoice: Boolean = false,
    ) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides EnglishPlatformFormat,
        ) {
            ReaderScreen(
                state = state,
                initialAyah = initialAyah,
                selectInitialAyah = selectInitialAyah,
                followVoice = followVoice,
                onBack = {},
                onToggleMode = {},
                onChangeSettings = {},
                onFirstVisibleAyah = onFirstVisibleAyah,
                onOpenNextSurah = {},
                onToggleBookmark = {},
                shareTextFor = { null },
                recitation = recitation,
                sheetOpenState = sheetOpenState,
            )
        }
    }

    /**
     * The header's Play starts at the ayah scrolled to (spec §5.1), and still does after the view
     * model issues a new state: a bookmark set here made a fresh header ayah back at the one the
     * reader opened on, while the list kept reporting its scrolling to the old one.
     */
    @Test
    fun headerPlayStartsAtTheAyahScrolledToAfterANewStateArrives() = runComposeUiTest {
        var state by mutableStateOf<ReaderUiState>(ready())
        val taps = mutableListOf<Pair<Int, Int>>()
        setContent { Reader(state, QuranRecitation(onHeader = { s, a -> taps += s to a })) }
        waitForIdle()

        state = ready(bookmarked = setOf(5))
        waitForIdle()
        // Item 0 is the basmala, so item 31 is ayah 31.
        onNode(hasScrollToIndexAction()).performScrollToIndex(31)
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.recitation_a11y_recitation)).performClick()

        assertEquals(listOf(2 to 31), taps)
    }

    /**
     * A reader opened while a recitation plays far down its surah stays where it opened: the
     * jump token was answered before this reader existed (a tap on the bar's title, or the media
     * notification, any time earlier) and is not a request to it. A token bumped while it is open
     * is, and takes it to the recited ayah.
     */
    @Test
    fun aJumpAskedForBeforeTheReaderOpenedIsNotAnsweredByIt() = runComposeUiTest {
        var token by mutableStateOf(3)
        var firstVisible = 0
        setContent {
            Reader(
                state = ready(),
                recitation = QuranRecitation(playing = 2 to 55, live = true, jumpToken = token),
                onFirstVisibleAyah = { firstVisible = it },
            )
        }
        waitForIdle()
        assertEquals(1, firstVisible, "the reader moved to a jump asked for before it opened")

        token = 4
        waitForIdle()
        assertTrue(firstVisible > 40, "the reader did not answer a jump asked for while it was open: $firstVisible")
    }

    /**
     * The reading sheet is the app's to hold, so it stays open when the reader under it is
     * replaced by the voice moving on to the next surah: the reader's finger is on it, and the
     * Mushaf, which no surah change replaces, keeps its sheet the same way.
     */
    @Test
    fun aReadingSheetHeldByTheAppStaysOpenWhenTheReaderIsReplaced() = runComposeUiTest {
        val sheet = mutableStateOf(false)
        var screen by mutableStateOf(1)
        setContent { key(screen) { Reader(ready(), QuranRecitation(), sheetOpenState = sheet) } }
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.quran_reader_aa)).performClick()
        waitForIdle()
        onNodeWithText(getString(Res.string.quran_sheet_size)).assertIsDisplayed()

        screen = 2
        waitForIdle()

        onNodeWithText(getString(Res.string.quran_sheet_size)).assertIsDisplayed()
    }

    /**
     * A reader opened by a widget tap at 2:30, with the voice at 2:34 a few cards down, stays where
     * it opened: the selected card the widget asked for stays in view rather than being scrolled
     * off the top as the screen opens.
     */
    @Test
    fun aReaderOpenedNearTheVoiceStaysWhereItOpened() = runComposeUiTest {
        var firstVisible = 0
        setContent {
            Reader(
                state = ready(),
                recitation = QuranRecitation(playing = 2 to 34, live = true),
                onFirstVisibleAyah = { firstVisible = it },
                initialAyah = 30,
                selectInitialAyah = true,
            )
        }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        assertTrue(firstVisible in 29..30, "the reader moved off the ayah it was opened at: $firstVisible")
    }

    /** The same reader opened at the voice follows it at once, to the resting line. */
    @Test
    fun aReaderOpenedAtTheVoiceFollowsItAtOnce() = runComposeUiTest {
        var firstVisible = 0
        setContent {
            Reader(
                state = ready(),
                recitation = QuranRecitation(playing = 2 to 34, live = true),
                onFirstVisibleAyah = { firstVisible = it },
                initialAyah = 30,
                followVoice = true,
            )
        }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        assertTrue(firstVisible > 30, "the reader opened at the voice did not follow it: $firstVisible")
    }
}
