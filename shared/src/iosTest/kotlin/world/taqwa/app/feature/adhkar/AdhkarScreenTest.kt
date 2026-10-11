package world.taqwa.app.feature.adhkar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
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
import world.taqwa.app.i18n.uppercaseIn
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
import world.taqwa.app.resources.adhkar_progress
import world.taqwa.app.resources.adhkar_reading
import world.taqwa.app.resources.adhkar_times
import world.taqwa.app.resources.adhkar_translation
import world.taqwa.app.resources.adhkar_until_sunrise
import world.taqwa.app.resources.quran_sheet_size
import world.taqwa.app.resources.quran_sheet_transliteration
import world.taqwa.app.resources.source_quran
import world.taqwa.app.resources.tasbeeh_hint
import world.taqwa.app.resources.ui_language
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val kursi = AdhkarItem(
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
        )
        val quranState = state.copy(
            pages = listOf(
                AdhkarPageUi(
                    item = kursi,
                    arabic = null,
                    transliteration = null,
                    translation = null,
                    quran = QuranPageText(ISTIADHAH, listOf(KURSI to 255), "Allah - there is no deity except Him", "en"),
                    count = 0,
                    target = 1,
                    done = false,
                ),
            ),
        )
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
            // The translation switch is the last control on the sheet.
            onAllNodes(hasClickAction()).onLast().performClick()
            assertEquals(listOf(false), switched)
        }
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }
}

private const val PAGER = "pager"

/** The opening of 2:255 as `quran.db` spells it, and the istiʿadhah Hisn prints before it. */
private const val KURSI = "ٱللَّهُ لَآ إِلَـٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ"
private const val ISTIADHAH = "أَعُوذُ بِاللَّهِ مِنَ ٱلشَّيْطَانِ الرَّجِيمِ"
