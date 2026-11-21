package world.taqwa.app.feature.prayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.jetbrains.compose.resources.getString
import world.taqwa.app.i18n.isolated
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_watch_about
import world.taqwa.app.resources.prayer_watch_dismiss
import world.taqwa.app.resources.prayer_watch_dismissed
import world.taqwa.app.resources.prayer_watch_install_on
import world.taqwa.app.resources.prayer_watch_title_face
import world.taqwa.app.resources.prayer_watch_title_install
import world.taqwa.app.resources.prayer_watch_try_again
import world.taqwa.app.resources.prayer_watch_undo
import world.taqwa.app.resources.settings_watch_continue
import world.taqwa.app.resources.settings_watch_face
import world.taqwa.app.resources.settings_watch_install
import world.taqwa.app.resources.settings_watch_unreachable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Home's watch card (Option B) as it is drawn and read: in the Qibla card's metrics, what this is
 * (17 sp) over the one action (14 sp, amber at 4.5:1, with the external-link glyph), one button for
 * a screen reader whose label says the whole card, a 48 dp x of its own with nothing under it, the
 * watch's answers said as they come, and after the x a quiet line with Undo in the card's place.
 * Every line is whole, wrapped and never cut, in every language on the owner's 360 dp phone and at
 * twice the text size; under Arabic the row mirrors and the watch drawing does not.
 *
 * Expected words come from the resources, never from literals: the simulator's language decides
 * them, and [inLanguage] each language in turn.
 */
@OptIn(ExperimentalTestApi::class)
class WatchCardTest {

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val anyLiveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    /** The install card's label: what this is, the named action, and the watch face's own sentence. */
    private suspend fun installLabel(name: String = galaxy.name): String = listOf(
        getString(Res.string.prayer_watch_title_install),
        getString(Res.string.prayer_watch_install_on, isolated(name)),
        getString(Res.string.prayer_watch_about),
    ).joinToString(". ")

    private fun ComposeUiTest.clickLabelOf(label: String): String? =
        onNodeWithContentDescription(label).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    private fun ComposeUiTest.drawn(text: String): Int =
        onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun overlaps(a: DpRect, b: DpRect): Boolean =
        a.left + 0.5.dp < b.right && b.left + 0.5.dp < a.right && a.top + 0.5.dp < b.bottom && b.top + 0.5.dp < a.bottom

    @Test
    fun theInstallCardIsOneButtonNamingTheWatch() = runComposeUiTest {
        setContent { Slot(cardOf(), width = 412.dp) }
        val named = getString(Res.string.prayer_watch_install_on, isolated(galaxy.name))
        onAllNodesWithContentDescription(installLabel()).assertCountEquals(1)
        onNodeWithContentDescription(installLabel()).assert(button).assertHasClickAction()
        assertEquals(named, clickLabelOf(installLabel()))
        assertEquals(1, drawn(named), "the named line is drawn")
        onAllNodes(anyLiveRegion).assertCountEquals(0)
    }

    @Test
    fun theInstallCardIsTheQiblaCardsHeightAtTheDefaultTextSize() = runComposeUiTest {
        setContent { Slot(cardOf(), width = 412.dp) }
        near(76.dp, onNodeWithContentDescription(installLabel()).getUnclippedBoundsInRoot().height, "the card's height")
    }

    @Test
    fun aNameTooLongForOneLineFallsBackToInstallOnYourWatch() = runComposeUiTest {
        val long = "Galaxy Watch8 Classic (5F2A)"
        setContent { Slot(cardOf(name = long), width = 360.dp) }
        assertEquals(1, drawn(getString(Res.string.settings_watch_install)), "the generic line is drawn")
        assertEquals(0, drawn(getString(Res.string.prayer_watch_install_on, isolated(long))), "the named line is not drawn")
        onAllNodesWithContentDescription(installLabel(long)).assertCountEquals(1)
    }

    /**
     * Option B's name rule: the named line falls back only where it would not fit one line at the
     * default text size; a name that fits there stays at a larger size and wraps (the artboard's
     * 1.3x card), so a reader who needs large text still sees which watch.
     */
    @Test
    fun aNameThatFitsAtTheDefaultSizeStaysAndWrapsAtLargerText() = runComposeUiTest {
        setContent { Slot(cardOf(), width = 412.dp, fontScale = 2f) }
        val named = getString(Res.string.prayer_watch_install_on, isolated(galaxy.name))
        assertEquals(1, drawn(named), "the named line gave way to the generic one")
        assertTrue(layoutOf(named).lineCount > 1, "the named line did not wrap")
        assertWhole(named)
    }

    @Test
    fun aBlankNameUsesTheGenericLine() = runComposeUiTest {
        var card by mutableStateOf<WatchCardUi>(cardOf(name = ""))
        setContent { Slot(card, width = 412.dp) }
        val generic = getString(Res.string.settings_watch_install)
        assertEquals(1, drawn(generic))
        val label = listOf(getString(Res.string.prayer_watch_title_install), generic, getString(Res.string.prayer_watch_about)).joinToString(". ")
        val node = onNodeWithContentDescription(getString(Res.string.prayer_watch_title_install), substring = true).fetchSemanticsNode()
        val said = node.config[SemanticsProperties.ContentDescription].single()
        assertEquals(label, said)
        assertFalse('\u2068' in said || '\u2069' in said, "an empty isolate in \"$said\"")
        card = cardOf(name = "", answer = WatchCardAnswer.CONTINUE)
        waitForIdle()
        onAllNodesWithContentDescription(getString(Res.string.settings_watch_continue)).assertCountEquals(1)
    }

    @Test
    fun anArabicNameIsIsolatedInTheLine() = runComposeUiTest {
        val arabic = "ساعة محمد"
        setContent { Slot(cardOf(name = arabic), width = 412.dp) }
        val line = getString(Res.string.prayer_watch_install_on, isolated(arabic))
        val text = onNodeWithText(arabic, substring = true, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        assertTrue(text.startsWith(line), "drawn \"$text\"")
        assertTrue("\u2068$arabic\u2069" in text, "the name is not isolated in \"$text\"")
    }

    @Test
    fun theFaceCardSaysTaqwaIsOnYourWatch() = runComposeUiTest {
        setContent { Slot(cardOf(step = WatchCardStep.ADD_FACE), width = 412.dp) }
        onAllNodesWithText(getString(Res.string.prayer_watch_title_face), useUnmergedTree = true).assertCountEquals(1)
        assertEquals(1, drawn(getString(Res.string.settings_watch_face)))
    }

    @Test
    fun theXIsItsOwnButtonAndNothingSitsUnderIt() = runComposeUiTest {
        val taps = CardTaps()
        setContent { Slot(cardOf(), width = 360.dp, taps = taps) }
        val x = onNodeWithContentDescription(getString(Res.string.prayer_watch_dismiss))
        x.assert(button).assertHasClickAction()
        val xb = x.getUnclippedBoundsInRoot()
        near(48.dp, xb.width, "the x's width")
        near(48.dp, xb.height, "the x's height")
        val card = onNodeWithContentDescription(installLabel()).getUnclippedBoundsInRoot()
        near(card.right, xb.right, "the x's end")
        near(card.top, xb.top, "the x's top")
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(texts.isNotEmpty())
        for (node in texts) {
            val r = dpRect(node.boundsInRoot)
            assertFalse(overlaps(xb, r), "\"${node.config[SemanticsProperties.Text]}\" sits under the x ($r against $xb)")
        }
        x.performClick()
        assertEquals(1, taps.dismiss)
        assertEquals(0, taps.open)
    }

    @Test
    fun answersArePoliteAndTheCardIsQuietBeforeThem() = runComposeUiTest {
        var card by mutableStateOf<WatchCardUi>(cardOf(answer = WatchCardAnswer.CONTINUE))
        setContent { Slot(card, width = 412.dp) }
        val going = "${getString(Res.string.settings_watch_continue)}. ${isolated(galaxy.name)}"
        onAllNodes(polite and hasContentDescription(going)).assertCountEquals(1)
        card = cardOf(answer = WatchCardAnswer.UNREACHABLE)
        waitForIdle()
        val tryAgain = getString(Res.string.prayer_watch_try_again)
        val unreachable = "${getString(Res.string.settings_watch_unreachable)}. $tryAgain"
        onAllNodes(polite and hasContentDescription(unreachable)).assertCountEquals(1)
        assertEquals(tryAgain, clickLabelOf(unreachable))
        for (quiet in listOf(cardOf(answer = WatchCardAnswer.ASKING), cardOf())) {
            card = quiet
            waitForIdle()
            onAllNodes(anyLiveRegion).assertCountEquals(0)
        }
    }

    @Test
    fun theDismissedLineIsPoliteWithItsOwnUndoButton() = runComposeUiTest {
        val taps = CardTaps()
        setContent { Slot(WatchCardUi.Dismissed, width = 360.dp, taps = taps) }
        onAllNodes(polite and hasText(getString(Res.string.prayer_watch_dismissed))).assertCountEquals(1)
        val undo = onNode(button and hasText(getString(Res.string.prayer_watch_undo)))
        val b = undo.getUnclippedBoundsInRoot()
        assertTrue(b.width >= 55.5.dp && b.height >= 43.5.dp, "Undo's target is ${b.width} by ${b.height}")
        undo.performClick()
        assertEquals(1, taps.undo)
    }

    /** The undo line's words and its Undo button, where they are drawn. */
    private suspend fun ComposeUiTest.undoLine(): Pair<DpRect, DpRect> {
        val words = onNodeWithText(getString(Res.string.prayer_watch_dismissed), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val undo = onNode(button and hasText(getString(Res.string.prayer_watch_undo))).getUnclippedBoundsInRoot()
        return words to undo
    }

    /**
     * Undo never squeezes the line it sits on: beside it while it is short (English on the owner's
     * 360 dp phone), under it at its end once it would take more than 40 % of the width (Bengali's
     * Undo at twice the text size on a 300 dp phone, which left the words no room at all).
     */
    @Test
    fun undoGoesUnderTheLineRatherThanSqueezeIt() {
        runComposeUiTest {
            setContent { Slot(WatchCardUi.Dismissed, width = 360.dp) }
            val (words, undo) = undoLine()
            assertTrue(undo.top < words.bottom && words.top < undo.bottom, "Undo ($undo) is not beside the words ($words)")
        }
        inLanguage("bn") {
            runComposeUiTest {
                setContent { Slot(WatchCardUi.Dismissed, width = 300.dp, fontScale = 2f) }
                val (words, undo) = undoLine()
                assertTrue(undo.top >= words.bottom - 0.5.dp, "Undo ($undo) is not under the words ($words)")
                assertTrue(words.width > (300.dp - 48.dp) / 2, "the words have ${words.width} of the line")
            }
        }
    }

    /**
     * Every drawn text of the card inside the card and clear of its x (the review of 20 November:
     * checked in every language, both directions and both text sizes, not only in English).
     */
    private suspend fun ComposeUiTest.textsAreInsideTheCardAndClearOfTheX() {
        val dismiss = getString(Res.string.prayer_watch_dismiss)
        val x = onNodeWithContentDescription(dismiss).getUnclippedBoundsInRoot()
        val body = onNode(button and hasClickAction() and !hasContentDescription(dismiss)).getUnclippedBoundsInRoot()
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        val count = texts.fetchSemanticsNodes().size
        assertTrue(count >= 2, "$count texts on the card")
        for (i in 0 until count) {
            val r = texts[i].getUnclippedBoundsInRoot()
            val what = texts[i].fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
            val inside = r.left >= body.left - 0.5.dp && r.right <= body.right + 0.5.dp &&
                r.top >= body.top - 0.5.dp && r.bottom <= body.bottom + 0.5.dp
            assertTrue(inside, "\"$what\" ($r) is not inside the card ($body)")
            assertFalse(overlaps(x, r), "\"$what\" ($r) sits under the x ($x)")
        }
    }

    /** Each state's drawn lines, in the simulator's language now, at [width] and [fontScale]. */
    private suspend fun ComposeUiTest.eachStateIsWhole(width: Dp, fontScale: Float, direction: LayoutDirection, titleWraps: Boolean = false) {
        var card by mutableStateOf<WatchCardUi>(cardOf())
        setContent { Slot(card, width = width, fontScale = fontScale, direction = direction) }
        waitForIdle()
        val named = getString(Res.string.prayer_watch_install_on, isolated(galaxy.name))
        val generic = getString(Res.string.settings_watch_install)
        val title = getString(Res.string.prayer_watch_title_install)
        assertWhole(title)
        if (titleWraps) assertTrue(layoutOf(title).lineCount > 1, "the title did not wrap")
        assertWhole(if (drawn(named) == 1) named else generic)
        textsAreInsideTheCardAndClearOfTheX()
        card = cardOf(step = WatchCardStep.ADD_FACE)
        waitForIdle()
        assertWhole(getString(Res.string.prayer_watch_title_face))
        assertWhole(getString(Res.string.settings_watch_face))
        textsAreInsideTheCardAndClearOfTheX()
        card = cardOf(answer = WatchCardAnswer.CONTINUE)
        waitForIdle()
        assertWhole(getString(Res.string.settings_watch_continue))
        assertWhole(galaxy.name)
        textsAreInsideTheCardAndClearOfTheX()
        card = cardOf(step = WatchCardStep.ADD_FACE, answer = WatchCardAnswer.UNREACHABLE)
        waitForIdle()
        assertWhole(getString(Res.string.settings_watch_unreachable))
        assertWhole(getString(Res.string.prayer_watch_try_again))
        textsAreInsideTheCardAndClearOfTheX()
        card = WatchCardUi.Dismissed
        waitForIdle()
        assertWhole(getString(Res.string.prayer_watch_dismissed))
        assertWhole(getString(Res.string.prayer_watch_undo))
    }

    /** The fixture's own check: a line drawn on one line where it needs more width is cut at the side. */
    @Test
    fun aLineCutAtTheSideIsNotWhole() {
        val line = "A line far too long for the narrow column it is drawn in"
        runComposeUiTest {
            setContent { Box(Modifier.width(120.dp)) { Text(line, softWrap = false) } }
            assertFails { assertWhole(line) }
        }
        runComposeUiTest {
            setContent { Box(Modifier.width(120.dp)) { Text(line) } }
            assertWhole(line)
        }
    }

    private val languages = listOf("en", "ar", "bn", "fr", "id", "tr", "ur")

    private fun directionOf(tag: String) = if (tag == "ar" || tag == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr

    @Test
    fun everyStateIsWholeInEveryLanguageOnA360DpPhone() {
        for (tag in languages) inLanguage(tag) {
            runComposeUiTest { eachStateIsWhole(360.dp, 1f, directionOf(tag)) }
        }
    }

    @Test
    fun everyStateWrapsAndIsWholeAtTwiceTheTextSize() {
        for (tag in languages) inLanguage(tag) {
            runComposeUiTest { eachStateIsWhole(300.dp, 2f, directionOf(tag), titleWraps = true) }
        }
    }

    @Test
    fun underArabicTheXIsOnTheLeftAndTheWatchOnTheRight() = runComposeUiTest {
        setContent { Slot(cardOf(), width = 412.dp, direction = LayoutDirection.Rtl) }
        val card = onNodeWithContentDescription(installLabel()).getUnclippedBoundsInRoot()
        val x = onNodeWithContentDescription(getString(Res.string.prayer_watch_dismiss)).getUnclippedBoundsInRoot()
        near(card.left, x.left, "the x's left edge")
        val title = onNodeWithText(getString(Res.string.prayer_watch_title_install), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(title.right <= card.right - 80.dp + 0.5.dp, "the title (${title.right}) is not clear of the watch (${card.right - 80.dp})")
    }

    @Test
    fun withReduceMotionTheCardIsWholeOnItsFirstFrame() = runComposeUiTest {
        var card by mutableStateOf<WatchCardUi>(cardOf())
        setContent { Slot(card, width = 412.dp) }
        waitForIdle()
        val settled = onNodeWithContentDescription(installLabel()).getUnclippedBoundsInRoot().height
        card = WatchCardUi.Hidden
        waitForIdle()
        onAllNodesWithContentDescription(installLabel()).assertCountEquals(0)
        mainClock.autoAdvance = false
        card = cardOf()
        mainClock.advanceTimeByFrame()
        near(settled, onNodeWithContentDescription(installLabel()).getUnclippedBoundsInRoot().height, "the card's height on its first frame")
    }
    // -- The review of 20 November -------------------------------------------------------------

    /**
     * The name gives way only to a generic line that fits where it does not: where both wrap (a
     * narrow column, Bengali on the owner's phone), the name stays.
     */
    @Test
    fun aNameStaysWhereTheGenericLineWouldWrapToo() = runComposeUiTest {
        setContent { Slot(cardOf(), width = 260.dp) }
        val named = getString(Res.string.prayer_watch_install_on, isolated(galaxy.name))
        assertEquals(1, drawn(named), "the name gave way to a generic line that wraps as well")
        assertEquals(0, drawn(getString(Res.string.settings_watch_install)))
        assertWhole(named)
    }

    /** A card tapped since Home came to the front says its new step as it comes. */
    @Test
    fun aCardTappedThisVisitSaysItsNewStepAsItComes() = runComposeUiTest {
        var card by mutableStateOf<WatchCardUi>(cardOf(step = WatchCardStep.ADD_FACE, tapped = true))
        setContent { Slot(card, width = 412.dp) }
        val faceLabel = listOf(
            getString(Res.string.prayer_watch_title_face),
            getString(Res.string.settings_watch_face),
            getString(Res.string.prayer_watch_about),
        ).joinToString(". ")
        onAllNodes(polite and hasContentDescription(faceLabel)).assertCountEquals(1)
        card = cardOf(step = WatchCardStep.ADD_FACE)
        waitForIdle()
        onAllNodes(anyLiveRegion).assertCountEquals(0)
    }

    /** The x asks how long the line stays: the reader's own time to act, six seconds at least. */
    @Test
    fun theXAsksThePlatformHowLongTheUndoLineStays() {
        runComposeUiTest {
            val taps = CardTaps()
            setContent { Slot(cardOf(), width = 412.dp, taps = taps, accessibility = TimeToAct(60_000)) }
            onNodeWithContentDescription(getString(Res.string.prayer_watch_dismiss)).performClick()
            assertEquals(60.seconds, taps.window)
        }
        runComposeUiTest {
            val taps = CardTaps()
            setContent { Slot(cardOf(), width = 412.dp, taps = taps, accessibility = null) }
            onNodeWithContentDescription(getString(Res.string.prayer_watch_dismiss)).performClick()
            assertEquals(6.seconds, taps.window)
        }
    }

    private fun ComposeUiTest.focused(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher and isFocused()).fetchSemanticsNodes().isNotEmpty()

    /**
     * Under a screen reader the x's line takes the focus to Undo once the bridge has heard of it
     * (the app's FocusOnceHeard), so a reader whose card left the screen is one tap from Undo.
     */
    @Test
    fun underAScreenReaderUndoTakesTheFocusOnceTheLineIsHeard() = runComposeUiTest {
        var card by mutableStateOf<WatchCardUi>(cardOf())
        setContent { Slot(card, width = 360.dp, screenReader = true) }
        val undo = button and hasText(getString(Res.string.prayer_watch_undo))
        card = WatchCardUi.Dismissed
        waitUntil("Undo focused", timeoutMillis = 5_000) { focused(undo) }
    }

    /** After Undo the reader's focus goes back to the card that came back, where it was. */
    @Test
    fun underAScreenReaderUndoPutsTheFocusBackOnTheCard() = runComposeUiTest {
        var card by mutableStateOf<WatchCardUi>(WatchCardUi.Dismissed)
        val taps = CardTaps()
        setContent { Slot(card, width = 412.dp, taps = taps, screenReader = true) }
        val label = installLabel()
        onNode(button and hasText(getString(Res.string.prayer_watch_undo))).performClick()
        assertEquals(1, taps.undo)
        card = cardOf()
        waitUntil("the card focused", timeoutMillis = 5_000) { focused(hasContentDescription(label)) }
    }

    /**
     * Focus moves only for a screen reader, and only after the reader's own x or Undo: a sighted
     * reader's page never scrolls to a focus, and a card that arrives takes none.
     */
    @Test
    fun nothingTakesTheFocusWithoutAScreenReaderOrOnArrival() {
        runComposeUiTest {
            var card by mutableStateOf<WatchCardUi>(cardOf())
            setContent { Slot(card, width = 412.dp) }
            card = WatchCardUi.Dismissed
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertFalse(focused(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)), "focus moved without a screen reader")
            onNode(button and hasText(getString(Res.string.prayer_watch_undo))).performClick()
            card = cardOf()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertFalse(focused(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)), "focus moved to the card without a screen reader")
        }
        runComposeUiTest {
            var card by mutableStateOf<WatchCardUi>(WatchCardUi.Hidden)
            setContent { Slot(card, width = 412.dp, screenReader = true) }
            card = cardOf()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertFalse(focused(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)), "the arriving card took the focus")
        }
    }
}
