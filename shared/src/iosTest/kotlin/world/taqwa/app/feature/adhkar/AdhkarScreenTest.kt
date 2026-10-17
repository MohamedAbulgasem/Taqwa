package world.taqwa.app.feature.adhkar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.adhkar.AdhkarItem
import world.taqwa.app.adhkar.AdhkarKind
import world.taqwa.app.adhkar.AdhkarPhase
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.adhkar.QuranPrefix
import world.taqwa.app.adhkar.QuranRef
import world.taqwa.app.adhkar.Source
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.isolatedClock
import world.taqwa.app.i18n.uppercaseIn
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhkar_a11y_count
import world.taqwa.app.resources.adhkar_a11y_next
import world.taqwa.app.resources.adhkar_a11y_previous
import world.taqwa.app.resources.adhkar_back
import world.taqwa.app.resources.adhkar_done_evening
import world.taqwa.app.resources.adhkar_done_morning
import world.taqwa.app.resources.adhkar_next_evening
import world.taqwa.app.resources.adhkar_next_morning
import world.taqwa.app.resources.adhkar_opens_at
import world.taqwa.app.resources.adhkar_progress
import world.taqwa.app.resources.adhkar_reading
import world.taqwa.app.resources.adhkar_times
import world.taqwa.app.resources.adhkar_translation
import world.taqwa.app.resources.adhkar_until_sunrise
import world.taqwa.app.resources.adhkar_window_evening
import world.taqwa.app.resources.quran_reader_aa
import world.taqwa.app.resources.quran_sheet_size
import world.taqwa.app.resources.quran_sheet_transliteration
import world.taqwa.app.resources.recitation_a11y_pause
import world.taqwa.app.resources.recitation_play
import world.taqwa.app.resources.source_quran
import world.taqwa.app.resources.tasbeeh_hint
import world.taqwa.app.resources.ui_language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The session screen on a two-page fixture: page one said three times and counted once, page two
 * said once. Expected words come from the resources, never from literals — the simulator's
 * language decides them — and digits from the English format the scene is given, so "1" is "1".
 */
@OptIn(ExperimentalTestApi::class)
class AdhkarScreenTest {

    private class Calls {
        var taps = 0
        var backs = 0
        val goTo = mutableListOf<Int>()
    }

    private fun item(id: String, count: Int) = AdhkarItem(
        id = id,
        hisn = 86,
        kind = AdhkarKind.DUA,
        sets = setOf(AdhkarSet.MORNING, AdhkarSet.EVENING),
        count = count,
        countDaily = false,
        quran = null,
        arabic = mapOf("both" to "بِسْمِ اللَّهِ"),
        transliteration = mapOf("both" to "Bismillah"),
        sources = listOf(Source("abu_dawud", "5088")),
    )

    private val state = AdhkarUiState(
        set = AdhkarSet.MORNING,
        phase = AdhkarPhase.BEST,
        until = "6:12",
        pages = listOf(
            AdhkarPageUi(item("hisn-086", 3), "بِسْمِ اللَّهِ", "Bismillah", "In the name of Allah", null, count = 1, target = 3, done = false),
            AdhkarPageUi(item("hisn-088", 1), "يَا حَيُّ يَا قَيُّومُ", "Ya Hayyu ya Qayyum", "O Ever-Living", null, count = 0, target = 1, done = false),
        ),
        currentPage = 0,
        completed = 0,
        showTranslation = true,
        arabicSizeSp = 28,
        transliteration = true,
        loaded = true,
    )

    /** Ayat al-Kursi as the view model hands it over: no du'a text, the passage from `quran.db`. */
    private val kursiPage = AdhkarPageUi(
        item = AdhkarItem(
            id = "hisn-075",
            hisn = 75,
            kind = AdhkarKind.QURAN,
            sets = setOf(AdhkarSet.MORNING, AdhkarSet.EVENING),
            count = 1,
            countDaily = false,
            quran = QuranRef(2, 255, QuranPrefix.ISTIADHAH),
            arabic = mapOf("both" to ISTIADHAH),
            transliteration = emptyMap(),
            sources = listOf(Source("quran", "2:255")),
        ),
        arabic = null,
        transliteration = null,
        translation = null,
        quran = QuranPageText(ISTIADHAH, listOf(KURSI to 255), "Allah - there is no deity except Him", "en"),
        count = 0,
        target = 1,
        done = false,
    )

    /** [state] with every page said: the session opens on the done page after them. */
    private fun finished(set: AdhkarSet) = state.copy(
        set = set,
        pages = state.pages.map { it.copy(count = it.target, done = true) },
        currentPage = state.pages.size,
        completed = state.pages.size,
    )

    /**
     * Not TaqwaTheme: it also installs the system-bar and keep-screen-on effects, which crash the
     * simulator test process. The colours are all the screen reads from it.
     */
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides EnglishPlatformFormat,
            content = content,
        )
    }

    @Composable
    private fun Screen(calls: Calls, shown: AdhkarUiState = state) {
        Themed {
            AdhkarScreen(
                state = shown,
                onBack = { calls.backs++ },
                onSelectSet = {},
                onGoTo = { calls.goTo += it },
                onTap = { calls.taps++ },
                onPlay = null,
                playing = false,
                onOpenReading = {},
            )
        }
    }

    private suspend fun label(count: Int): String =
        getPluralString(Res.plurals.adhkar_times, count, count.toString())
            .uppercaseIn(UiLanguage.of(getString(Res.string.ui_language)))

    @Test
    fun thePageShowsItsLabelItsCountAndTheSetsProgress() = runComposeUiTest {
        setContent { Screen(Calls()) }
        onNodeWithText(label(3), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("1").assertIsDisplayed()
        onNodeWithText(getString(Res.string.adhkar_progress, "0", "2")).assertIsDisplayed()
        onNodeWithText(getString(Res.string.adhkar_until_sunrise, "6:12")).assertIsDisplayed()
        // The count is said as "1 of 3".
        onNodeWithContentDescription(getString(Res.string.adhkar_progress, "1", "3")).assertIsDisplayed()
    }

    @Test
    fun aTapOnThePageCounts() = runComposeUiTest {
        val calls = Calls()
        setContent { Screen(calls) }
        val page = onNode(hasText(label(3)) and hasClickAction())
        // The tap is named for what it does, not left as a bare "double-tap to activate".
        assertEquals(getString(Res.string.adhkar_a11y_count), page.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        page.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, calls.taps)
    }

    @Test
    fun thePageOffersCountAsACustomAction() = runComposeUiTest {
        val calls = Calls()
        setContent { Screen(calls) }
        val count = getString(Res.string.adhkar_a11y_count)
        val page = onNode(hasText(label(3)) and hasCustomAction(count))
        page.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == count }.action()
        assertEquals(1, calls.taps)
    }

    @Test
    fun theChevronsAreLabelledAndPreviousRestsOnTheFirstPage() = runComposeUiTest {
        val calls = Calls()
        setContent { Screen(calls) }
        onNodeWithContentDescription(getString(Res.string.adhkar_a11y_previous)).assertIsNotEnabled()
        onNodeWithContentDescription(getString(Res.string.adhkar_a11y_next)).assertIsEnabled().performClick()
        // The pager reports the page it settled on (0) when it appears; the chevron asks for 1.
        assertEquals(listOf(1), calls.goTo.filter { it != 0 })
    }

    @Test
    fun theHintIsGoneOnceTheSessionHasACount() = runComposeUiTest {
        setContent { Screen(Calls()) }
        // Page one is already at 1, so "Tap anywhere to count" has nothing left to teach.
        onAllNodesWithText(getString(Res.string.tasbeeh_hint)).assertCountEquals(0)
    }

    /**
     * The owner's hard rule: the Quran is never voiced by a phone voice. A Quran page's block is
     * one node that says its reference line; not a word of the ayah is in the semantics tree, merged
     * or not, so no screen reader can reach it.
     */
    @Test
    fun aQuranPageSaysItsReferenceAndNoneOfTheQuran() = runComposeUiTest {
        val quranState = state.copy(pages = listOf(kursiPage))
        setContent {
            CompositionLocalProvider(
                LocalTaqwaColors provides LightColors,
                LocalPlatformFormat provides EnglishPlatformFormat,
            ) {
                AdhkarScreen(quranState, {}, {}, {}, {}, null, false, {})
            }
        }
        val reference = "${getString(Res.string.source_quran)} 2:255"
        onNodeWithContentDescription(reference).assertExists()
        // The merged tree, which is the one a screen reader is given: `clearAndSetSemantics` takes the
        // block's text out of it. (The test-only unmerged tree keeps replaced nodes for inspection,
        // so it would find the text whatever the screen did.)
        for (word in listOf("ٱلْقَيُّومُ", "ٱلشَّيْطَانِ")) {
            onAllNodesWithText(word, substring = true).assertCountEquals(0)
            onAllNodesWithContentDescription(word, substring = true).assertCountEquals(0)
        }
    }

    /**
     * The speaker belongs to the Quran pages (spec §3.5): on Ayat al-Kursi it is there, named for
     * what a tap does, and the tap reaches the route; while the passage plays it is the pause. On
     * a du'a page there is none, though recitation is wired in all the same — a du'a has no audio.
     */
    @Test
    fun aQuranPageShowsTheSpeakerAndADuaPageDoesNot() = runComposeUiTest {
        var plays = 0
        var shown by mutableStateOf(state.copy(pages = listOf(kursiPage) + state.pages))
        var playing by mutableStateOf(false)
        setContent {
            Themed {
                AdhkarScreen(shown, {}, {}, {}, {}, onPlay = { plays++ }, playing = playing, onOpenReading = {})
            }
        }
        val play = getString(Res.string.recitation_play)
        val pause = getString(Res.string.recitation_a11y_pause)
        onNodeWithContentDescription(play).assertIsDisplayed().performClick()
        assertEquals(1, plays)
        playing = true
        onNodeWithContentDescription(pause).assertIsDisplayed()
        onAllNodesWithContentDescription(play).assertCountEquals(0)

        // The next page is a du'a.
        playing = false
        shown = shown.copy(currentPage = 1)
        waitForIdle()
        onAllNodesWithContentDescription(play).assertCountEquals(0)
        onAllNodesWithContentDescription(pause).assertCountEquals(0)
        // "Aa" is still there: only the speaker is the Quran's.
        onNodeWithContentDescription(getString(Res.string.quran_reader_aa)).assertIsDisplayed()
    }

    /**
     * Two page changes from the session in quick succession — a page completing and a chevron, say
     * — must leave the pager at rest on the session's page, not stopped between two pages. The
     * session is wired as the view model is: whatever page the pager reports becomes its page.
     */
    @Test
    fun twoQuickPageChangesLeaveThePagerAtRestOnTheSessionsPage() = runComposeUiTest {
        var page by mutableStateOf(0)
        var pager: PagerState? = null
        setContent {
            val pagerState = rememberPagerState { 8 }
            pager = pagerState
            SessionPagerSync(pagerState, page) { page = it }
            HorizontalPager(pagerState, Modifier.size(400.dp, 300.dp)) { index ->
                Box(Modifier.fillMaxSize()) { Text("page $index") }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        runOnUiThread { page = 3 }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(120)
        runOnUiThread { page = 5 }
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()
        val state = pager!!
        assertEquals(5, page)
        assertEquals(5, state.currentPage)
        assertEquals(0f, state.currentPageOffsetFraction)
    }

    /**
     * A finger taking the pager while the session is moving it: nothing is reported while the
     * finger is down — the page the animation set out from is not where the reader is, and the
     * session would move back to it — and once the swipe settles, the page it settled on is.
     */
    @Test
    fun aFingerTakingThePagerMidAnimationIsReportedOnlyOnceItSettles() = runComposeUiTest {
        var page by mutableStateOf(0)
        val reported = mutableListOf<Int>()
        var pager: PagerState? = null
        setContent {
            val pagerState = rememberPagerState { 8 }
            pager = pagerState
            SessionPagerSync(pagerState, page) { reported += it; page = it }
            HorizontalPager(pagerState, Modifier.size(400.dp, 300.dp).testTag(PAGER)) { index ->
                Box(Modifier.fillMaxSize()) { Text("page $index") }
            }
        }
        waitForIdle()
        reported.clear()
        mainClock.autoAdvance = false
        runOnUiThread { page = 3 }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(120)
        // The finger lands mid-animation, drags on, and stays down for a while.
        onNodeWithTag(PAGER).performTouchInput {
            down(center)
            moveBy(Offset(-40f, 0f))
            moveBy(Offset(-40f, 0f))
        }
        mainClock.advanceTimeBy(200)
        assertEquals(emptyList(), reported)
        assertEquals(3, page)

        onNodeWithTag(PAGER).performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        val state = pager!!
        assertEquals(0f, state.currentPageOffsetFraction)
        assertEquals(state.currentPage, page)
        assertTrue(reported.isNotEmpty())
        assertEquals(listOf(state.currentPage), reported.distinct())
    }

    @Test
    fun theDonePageNamesTheSetAndItsLinkGoesBack() = runComposeUiTest {
        val calls = Calls()
        setContent { Screen(calls, finished(AdhkarSet.MORNING)) }
        onNodeWithText(getString(Res.string.adhkar_done_morning)).assertIsDisplayed()
        // The morning done, the caption says when the evening opens.
        onNodeWithText(getString(Res.string.adhkar_next_evening)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.adhkar_back)).assertIsDisplayed().performClick()
        assertEquals(1, calls.backs)
        // Nothing to count on the done page.
        assertEquals(0, calls.taps)
    }

    @Test
    fun theEveningDonePageSaysWhenTheMorningOpens() = runComposeUiTest {
        setContent { Screen(Calls(), finished(AdhkarSet.EVENING)) }
        onNodeWithText(getString(Res.string.adhkar_done_evening)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.adhkar_next_morning)).assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.adhkar_done_morning)).assertCountEquals(0)
    }

    /**
     * Before Asr the evening set is a free read (spec §3.3): the caption under the title says when
     * it opens — Asr's time, isolated as every spliced clock time is, so a 12-hour marker keeps its
     * place — rather than the plain window it shows at other hours.
     */
    @Test
    fun beforeAsrTheCaptionSaysWhenTheEveningOpens() = runComposeUiTest {
        val early = state.copy(set = AdhkarSet.EVENING, phase = AdhkarPhase.NONE, until = null, opensAt = "4:55 PM")
        setContent { Screen(Calls(), early) }
        onNodeWithText(getString(Res.string.adhkar_opens_at, isolatedClock("4:55 PM"))).assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.adhkar_window_evening)).assertCountEquals(0)
    }

    /**
     * An evening read through before Asr ends on the done page — the reader did read it — but the
     * page does not call it the day's evening: in place of "Evening adhkar · done" it says, as the
     * header does, when the evening opens.
     */
    @Test
    fun anEveningFinishedBeforeAsrSaysWhenItOpensAndNotThatItIsDone() = runComposeUiTest {
        val calls = Calls()
        val early = finished(AdhkarSet.EVENING).copy(phase = AdhkarPhase.NONE, until = null, opensAt = "16:55")
        setContent { Screen(calls, early) }
        onAllNodesWithText(getString(Res.string.adhkar_done_evening)).assertCountEquals(0)
        // Once under the title and once on the done page.
        onAllNodesWithText(getString(Res.string.adhkar_opens_at, "16:55")).assertCountEquals(2)
        onNodeWithText(getString(Res.string.adhkar_back)).assertIsDisplayed().performClick()
        assertEquals(1, calls.backs)
    }

    /**
     * The sheet's rows follow the interface (spec §3.6): transliteration under a Latin interface
     * only, the translation switch under every interface but Arabic. The simulator's language
     * decides which branch runs; the expectations are read from the same resources.
     */
    @Test
    fun theReadingSheetShowsTheSizeThePreviewAndTheInterfacesToggles() = runComposeUiTest {
        val switched = mutableListOf<Boolean>()
        setContent {
            Themed {
                AdhkarReadingContent(
                    settings = ReadingSettings(arabicSizeSp = 28, transliteration = true),
                    showTranslation = true,
                    preview = "بِسْمِ اللَّهِ",
                    onChange = {},
                    onTranslation = { switched += it },
                )
            }
        }
        val language = UiLanguage.of(getString(Res.string.ui_language))
        onNodeWithText(getString(Res.string.adhkar_reading)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.quran_sheet_size)).assertIsDisplayed()
        onNodeWithText("28").assertIsDisplayed()
        // The preview is a picture of the size, not words for a screen reader to say.
        onAllNodesWithText("بِسْمِ اللَّهِ", substring = true).assertCountEquals(0)

        val transliteration = onAllNodesWithText(getString(Res.string.quran_sheet_transliteration))
        transliteration.assertCountEquals(if (language.latinScript) 1 else 0)
        val translation = onAllNodesWithText(getString(Res.string.adhkar_translation))
        if (language == UiLanguage.ARABIC) {
            translation.assertCountEquals(0)
        } else {
            translation.assertCountEquals(1)
            // The translation row is the switch, activated as a screen reader's double-tap does:
            // only the drawn switch at its end takes a touch.
            onNode(isToggleable() and hasText(getString(Res.string.adhkar_translation)))
                .assertIsOn()
                .performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(listOf(false), switched)
        }
    }

    /**
     * The sheet writes the reader's settings back whole, so each change must carry everything else
     * as it was: a size commit or a transliteration switch that dropped the mode or the translation
     * would change the reader's Quran under it. The mode here is the Mushaf, which the adhkar never
     * shows, so a default in its place could not pass for it.
     */
    @Test
    fun theSheetsSizeAndTransliterationEachWriteTheWholeSettings() = runComposeUiTest {
        val reading = ReadingSettings(
            mode = ReadingMode.MUSHAF,
            arabicSizeSp = 28,
            transliteration = false,
            translationId = "fr.hamidullah",
        )
        val changes = mutableListOf<ReadingSettings>()
        setContent {
            Themed {
                AdhkarReadingContent(
                    settings = reading,
                    showTranslation = true,
                    preview = "بِسْمِ اللَّهِ",
                    onChange = { changes += it },
                    onTranslation = {},
                )
            }
        }
        onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(32f) }
        assertEquals(listOf(reading.copy(arabicSizeSp = 32)), changes)

        if (UiLanguage.of(getString(Res.string.ui_language)).latinScript) {
            // The transliteration row is the switch, activated as a screen reader's double-tap does.
            onNode(isToggleable() and hasText(getString(Res.string.quran_sheet_transliteration)))
                .assertIsOff()
                .performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(reading.copy(transliteration = true), changes.last())
            assertEquals(2, changes.size)
        }
    }

    /**
     * The scene at a larger text size — 2 is the device pass's 200 % font, and iOS's largest
     * accessibility sizes go past 3 — in a column [width] wide. The density is the scene's own with
     * only the font scale changed.
     */
    @Composable
    private fun Scaled(shown: AdhkarUiState, width: Dp, fontScale: Float = 2f) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.width(width)) { Screen(Calls(), shown) }
        }
    }

    private fun ComposeUiTest.layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    /**
     * At a large font a count of 100 stays whole in the ring. The device pass saw "10" at 200 %:
     * the count shrank only as far as a 12 sp floor, which grows with the font — 24 dp there — and
     * three digits at that size do not fit the ring's 44 dp, so the last one broke onto a line that
     * one line cannot show. The floor is 8 dp now, at every size.
     */
    @Test
    fun aCountOfAHundredStaysWholeAtLargeTextSizes() = runComposeUiTest {
        val hundred = state.copy(pages = listOf(state.pages[0].copy(count = 100, target = 100)))
        var scale by mutableStateOf(2f)
        setContent { Scaled(hundred, width = 400.dp, fontScale = scale) }
        for (each in listOf(2f, 3f)) {
            scale = each
            waitForIdle()
            val count = layoutOf(onNodeWithText("100", useUnmergedTree = true))
            assertEquals(3, count.getLineEnd(0), "at font scale $each")
            assertFalse(count.didOverflowHeight, "at font scale $each")
        }
    }

    /**
     * At a 200 % font in a narrow column the phase caption wraps onto a second line rather than
     * losing its time to an ellipsis ("After sunrise · until 12:5…" on the device).
     */
    @Test
    fun thePhaseCaptionWrapsRatherThanLosingItsTimeAtTwiceTheTextSize() = runComposeUiTest {
        val makeup = state.copy(phase = AdhkarPhase.MAKEUP, until = "12:53")
        setContent { Scaled(makeup, width = 320.dp) }
        val caption = layoutOf(onNodeWithText("12:53", substring = true, useUnmergedTree = true))
        assertFalse(caption.isLineEllipsized(caption.lineCount - 1))
        assertTrue(caption.lineCount > 1)
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }
}

private const val PAGER = "pager"

/** The opening of 2:255 as `quran.db` spells it, and the istiʿadhah Hisn prints before it. */
private const val KURSI = "ٱللَّهُ لَآ إِلَـٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ"
private const val ISTIADHAH = "أَعُوذُ بِاللَّهِ مِنَ ٱلشَّيْطَانِ الرَّجِيمِ"
