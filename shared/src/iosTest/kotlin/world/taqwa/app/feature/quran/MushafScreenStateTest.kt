package world.taqwa.app.feature.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.feature.recitation.QuranRecitation
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.recitation_a11y_recitation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Mushaf's own state across what happens after it opens: the app-wide "show me the recited
 * ayah" token, and a tapped ayah the reader has since paged away from. Pages 1 to 3 are the
 * fixtures copied from the bundled database; page 3 opens at 2:6.
 */
@OptIn(ExperimentalTestApi::class)
class MushafScreenStateTest {

    private val fatiha = Surah(1, "الفاتحة", "Al-Faatiha", "The Opening", Revelation.MAKKI, ayahCount = 7, startPage = 1, startJuz = 1)
    private val baqarah = Surah(2, "البقرة", "Al-Baqarah", "The Cow", Revelation.MADANI, ayahCount = 286, startPage = 2, startJuz = 1)
    private val pages = mapOf(1 to MUSHAF_PAGE_1, 2 to MUSHAF_PAGE_2, 3 to MUSHAF_PAGE_3)

    private fun ready(page: Int) = MushafUiState.Ready(
        currentPage = page,
        surah = if (page == 1) fatiha else baqarah,
        juz = 1,
        settings = ReadingSettings(mode = ReadingMode.MUSHAF),
        translations = emptyList(),
        previewAyah = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ",
        basmala = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
        surahsByNumber = mapOf(1 to fatiha, 2 to baqarah),
        bookmarked = emptySet(),
    )

    /** Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test process. */
    @Composable
    private fun Mushaf(
        startPage: Int,
        recitation: QuranRecitation,
        initialHighlight: Pair<Int, Int>? = null,
        onPageShown: (Int) -> Unit = {},
    ) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides EnglishPlatformFormat,
        ) {
            MushafScreen(
                state = ready(startPage),
                startPage = startPage,
                initialHighlight = initialHighlight,
                pageLoader = { number -> pages[number] ?: MUSHAF_PAGE_3.copy(number = number) },
                onBack = {},
                onToggleMode = {},
                onChangeSettings = {},
                onPageShown = onPageShown,
                onToggleBookmark = { _, _ -> },
                shareTextFor = { _, _ -> null },
                recitation = recitation,
                pageOfAyah = { surah, ayah -> if (surah == 2 && ayah >= 6) 3 else if (surah == 2) 2 else 1 },
            )
        }
    }

    /**
     * A Mushaf opened at Al-Faatiha while Al-Baqarah 2:6 is recited two pages on stays on its own
     * page: the jump token was answered before it opened (a bookmark, a search hit, the Juz list,
     * Continue, a widget tap all opened Mushafs that turned to the recited page at once). A token
     * bumped while it is open turns to the recited page.
     */
    @Test
    fun aJumpAskedForBeforeTheMushafOpenedIsNotAnsweredByIt() = runComposeUiTest {
        var token by mutableStateOf(3)
        var shown = 0
        setContent {
            Mushaf(
                startPage = 1,
                recitation = QuranRecitation(playing = 2 to 6, live = true, jumpToken = token),
                onPageShown = { shown = it },
            )
        }
        waitForIdle()
        assertEquals(1, shown, "the Mushaf turned to a jump asked for before it opened")

        token = 4
        waitForIdle()
        assertEquals(3, shown, "the Mushaf did not answer a jump asked for while it was open")
    }

    /** The header's Play starts at the tapped ayah while that ayah is on the page being read. */
    @Test
    fun headerPlayStartsAtTheTappedAyahOnThePageBeingRead() = runComposeUiTest {
        val taps = mutableListOf<Pair<Int, Int>>()
        setContent { Mushaf(startPage = 1, recitation = QuranRecitation(onHeader = { s, a -> taps += s to a }), initialHighlight = 1 to 3) }
        waitForIdle()

        onNodeWithContentDescription(getString(Res.string.recitation_a11y_recitation)).performClick()

        assertEquals(listOf(1 to 3), taps)
    }

    /**
     * Once the tapped ayah is no longer on the page being read, the header's Play starts at the
     * first ayah printed on that page, as it does with nothing tapped: it used to start at the
     * tapped ayah pages back, off screen, or pause another surah's recitation.
     */
    @Test
    fun headerPlayStartsOnThePageBeingReadOnceTheTappedAyahIsOffIt() = runComposeUiTest {
        val taps = mutableListOf<Pair<Int, Int>>()
        setContent { Mushaf(startPage = 3, recitation = QuranRecitation(onHeader = { s, a -> taps += s to a }), initialHighlight = 1 to 3) }
        waitForIdle()

        onNodeWithContentDescription(getString(Res.string.recitation_a11y_recitation)).performClick()

        assertEquals(listOf(2 to 6), taps)
    }
}
