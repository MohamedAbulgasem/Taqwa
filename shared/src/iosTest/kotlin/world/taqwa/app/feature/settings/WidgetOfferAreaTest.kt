package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_widget_add
import world.taqwa.app.resources.widgets_how_label
import world.taqwa.app.resources.widgets_refused_body
import world.taqwa.app.resources.widgets_refused_title
import world.taqwa.app.resources.widgets_show_how
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetAddPath
import world.taqwa.app.widget.WidgetOffer
import world.taqwa.app.widget.WidgetPinRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Widgets screen's "Add to home screen" buttons, with a launcher that answers as told: only a
 * refusal reaches the screen, which then turns that card's button into the steps
 * ([world.taqwa.app.widget.widgetOffer]).
 */
@OptIn(ExperimentalTestApi::class)
class WidgetOfferAreaTest {

    private class Launcher(private val takes: Boolean) : WidgetPinRequester {
        val asked = mutableListOf<PinnableWidget>()
        override fun isSupported(widget: PinnableWidget) = true
        override fun requestPin(widget: PinnableWidget): Boolean {
            asked += widget
            return takes
        }
    }

    /** Both buttons, prayer first, as the screen has them. */
    @Composable
    private fun BothButtons(launcher: Launcher, refused: MutableList<PinnableWidget>) {
        Column {
            for (widget in PinnableWidget.entries) {
                WidgetOfferArea(
                    name = widget.name,
                    offer = WidgetOffer.Pin(widget),
                    stepsOpen = false,
                    refused = false,
                    pinRequester = launcher,
                    onRefused = { refused += it },
                    onShowSteps = {},
                )
            }
        }
    }

    @Test
    fun aButtonTheLauncherTookIsNotReportedAsRefused() = runComposeUiTest {
        val launcher = Launcher(takes = true)
        val refused = mutableListOf<PinnableWidget>()
        setContent { BothButtons(launcher, refused) }
        val buttons = onAllNodesWithText(getString(Res.string.appearance_widget_add))
        PinnableWidget.entries.indices.forEach { buttons[it].performClick() }
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        assertEquals(emptyList<PinnableWidget>(), refused)
    }

    @Test
    fun aButtonTheLauncherRefusedIsReportedForThatWidget() = runComposeUiTest {
        val launcher = Launcher(takes = false)
        val refused = mutableListOf<PinnableWidget>()
        setContent { BothButtons(launcher, refused) }
        val buttons = onAllNodesWithText(getString(Res.string.appearance_widget_add))
        PinnableWidget.entries.indices.forEach { buttons[it].performClick() }
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        assertEquals(PinnableWidget.entries.toList(), refused)
    }

    /**
     * Review round 2: the row's hairline sat right under the card's description, and in Arabic
     * the descenders touched it. It keeps the room HowToPanel keeps above its own hairline, so
     * the line sits in the same place whether the steps are folded or open.
     */
    @Test
    fun theShowMeHowRowLeavesRoomBetweenItsHairlineAndTheTextAbove() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
                Column {
                    Box(Modifier.fillMaxWidth().height(20.dp).testTag(ABOVE))
                    WidgetOfferArea(
                        name = "Ayah of the day",
                        offer = WidgetOffer.Instructions(WidgetAddPath.IOS_HOLD_ICON),
                        stepsOpen = false,
                        refused = false,
                        pinRequester = Launcher(takes = false),
                        onRefused = {},
                        onShowSteps = {},
                    )
                }
            }
        }
        val above = onNodeWithTag(ABOVE).getUnclippedBoundsInRoot()
        val row = onNodeWithText(getString(Res.string.widgets_show_how)).getUnclippedBoundsInRoot()
        // The row starts under its one-dp hairline, so the room above that line is the gap less it.
        val room = row.top - above.bottom - 1.dp
        assertTrue(room >= 12.dp, "only $room between the text above and the row's hairline")
    }

    /** One card's foot with the steps written out, at the text size [fontScale], under Material's
     * typography as the app's theme hands it down (its bodyLarge line height is what the step
     * number inherits). */
    @Composable
    private fun OpenSteps(
        path: WidgetAddPath,
        fontScale: Float = 1f,
        refused: Boolean = false,
        screenReader: Boolean = false,
        openedByTap: Boolean = false,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            MaterialTheme {
                WidgetOfferArea(
                    name = PRAYER_TIMES,
                    offer = WidgetOffer.Instructions(path),
                    stepsOpen = true,
                    refused = refused,
                    pinRequester = Launcher(takes = false),
                    onRefused = {},
                    onShowSteps = {},
                    screenReader = screenReader,
                    openedByTap = openedByTap,
                )
            }
        }
    }

    /**
     * Review round 2: the step number sat in a fixed 22 dp disc, so with larger text its line no
     * longer fitted and the digit was cut off at the disc's edge (from about 1.5 times the size on
     * Android). The disc grows with the text: at twice the size the digit's whole line is drawn.
     */
    @Test
    fun theStepNumbersWholeLineFitsItsDiscAtTwiceTheTextSize() = runComposeUiTest {
        setContent { OpenSteps(WidgetAddPath.ANDROID_PIN, fontScale = 2f) }
        val number = layoutOf("1")
        // The height alone: a paragraph is laid out across the whole width it may take, so it
        // always "overflows" sideways however narrow its digit.
        assertFalse(number.didOverflowHeight, "the digit's ${number.multiParagraph.height} px line is cut to ${number.size.height} px")
    }

    /** Review round 2: "Show me how" is a disclosure, so it says so: a screen reader hears it as
     * folded and can open it by its expand action, which opens the steps as a tap does. */
    @Test
    fun theShowMeHowRowOpensItsStepsByTheExpandAction() = runComposeUiTest {
        var opened = 0
        setContent {
            CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
                WidgetOfferArea(
                    name = PRAYER_TIMES,
                    offer = WidgetOffer.Instructions(WidgetAddPath.IOS_HOLD_ICON),
                    stepsOpen = false,
                    refused = false,
                    pinRequester = Launcher(takes = false),
                    onRefused = {},
                    onShowSteps = { opened++ },
                )
            }
        }
        onNodeWithText(getString(Res.string.widgets_show_how))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Collapse))
            .performSemanticsAction(SemanticsActions.Expand)
        waitForIdle()
        assertEquals(1, opened)
    }

    /**
     * Review round 2: the Android refused path had no test of its own, only screenshots. Android's
     * steps open in the warm panel, which says first why the button did nothing, then the
     * launcher's three steps with the widget's name; never the iPhone's "How to add it" heading.
     * Read out, since they replace the button the reader just tapped.
     */
    @Test
    fun androidsByHandPanelSaysWhyThenGivesTheLaunchersThreeSteps() = runComposeUiTest {
        setContent { OpenSteps(WidgetAddPath.ANDROID_PIN, refused = true) }
        onNodeWithText(getString(Res.string.widgets_refused_title)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.widgets_refused_body)).assertIsDisplayed()
        for (step in widgetSteps(WidgetAddPath.ANDROID_PIN).steps) {
            onNodeWithText(markedText(getString(step), PRAYER_TIMES).text).assertIsDisplayed()
        }
        onAllNodesWithText(getString(Res.string.widgets_how_label)).assertCountEquals(0)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(1)
    }

    /**
     * VoiceOver says the panel as it replaces a refused button. CMP 1.12.1's iOS mediator
     * announces a live region only from that node's own label, and a node that merges nothing has
     * none (as 8aea9361 found for the watch rows): so the live region is one node that merges the
     * words that say why, and not a box around them. The steps stay nodes of their own, each
     * reached by itself, rather than being swallowed into the announcement.
     */
    @Test
    fun aRefusedPanelIsAnnouncedFromOneNodeThatHoldsItsWordsAndLeavesTheStepsApart() = runComposeUiTest {
        setContent { OpenSteps(WidgetAddPath.ANDROID_PIN, refused = true) }
        val title = getString(Res.string.widgets_refused_title)
        val body = getString(Res.string.widgets_refused_body)
        val politeWhy = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
            hasText(title) and hasText(body)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(1)
        onAllNodes(politeWhy).assertCountEquals(1)
        for (step in widgetSteps(WidgetAddPath.ANDROID_PIN).steps) {
            val words = markedText(getString(step), PRAYER_TIMES).text
            onAllNodesWithText(words).assertCountEquals(1)
            onNodeWithText(words).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        }
    }

    /** The same on the iPhone's panel, whose words that hold the live region are its heading. */
    @Test
    fun aRefusedHowToPanelIsAnnouncedFromANodeThatHoldsItsHeading() = runComposeUiTest {
        setContent { OpenSteps(WidgetAddPath.IOS_HOLD_ICON, refused = true) }
        val politeHeading = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
            hasText(getString(Res.string.widgets_how_label))
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(1)
        onAllNodes(politeHeading).assertCountEquals(1)
    }

    /**
     * Found on the emulator's TalkBack (review round 2): the refused button goes, and TalkBack put
     * its focus on another card's title. Under a screen reader focus moves to the panel's first
     * words instead, which say why the button did nothing, and the panel is read from there rather
     * than announced as well.
     */
    @Test
    fun underAScreenReaderARefusedButtonsPanelTakesFocusAtWhyItIsThere() = runComposeUiTest {
        setContent { OpenSteps(WidgetAddPath.ANDROID_PIN, refused = true, screenReader = true, openedByTap = true) }
        // Focus moves a moment after the panel appears, once Android's bridge can have seen it.
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onNodeWithText(getString(Res.string.widgets_refused_title)).assertIsFocused()
        onAllNodes(isFocused()).assertCountEquals(1)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(0)
    }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}

private const val PRAYER_TIMES = "Prayer times"

/** Longer than the moment the screen waits before it moves focus into steps just opened. */
internal const val FOCUS_SETTLES_MS = 1_000L

private const val ABOVE = "above"
