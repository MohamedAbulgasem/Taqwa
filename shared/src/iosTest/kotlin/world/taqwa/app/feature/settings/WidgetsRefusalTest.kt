package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalScreenReaderOverride
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_widget_add
import world.taqwa.app.resources.widgets_on_home_ios
import world.taqwa.app.resources.widgets_prayer_name
import world.taqwa.app.resources.widgets_show_how
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetContentBuilder
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.WidgetPinRequester
import world.taqwa.app.widget.WidgetPlacement
import world.taqwa.app.widget.WidgetPlacementSource
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.widgetAddPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The whole Widgets screen with neither widget placed. The owner's rule: the steps by hand appear
 * only once the launcher has refused, and a phone whose launcher takes the request keeps its "Add
 * to home screen" buttons exactly as before. [WidgetOfferAreaTest] proves one button reports a
 * refusal; this proves the screen acts on it, for each widget, and keeps it across a restore.
 *
 * It runs on the iOS simulator, where the steps written out are the iPhone's; the launcher here is
 * a fake that can be asked, so the same screen logic an Android launcher drives is what is tested.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetsRefusalTest {

    private class Launcher(private val takes: Boolean, private val canBeAsked: Boolean = true) : WidgetPinRequester {
        val asked = mutableListOf<PinnableWidget>()
        override fun isSupported(widget: PinnableWidget) = canBeAsked
        override fun requestPin(widget: PinnableWidget): Boolean {
            asked += widget
            return takes
        }
    }

    private object NeitherPlaced : WidgetPlacementSource {
        override suspend fun current() = WidgetPlacement(prayer = false, ayah = false)
    }

    /** A source that never answers: the screen as it is before the first read returns. */
    private object NeverAnswers : WidgetPlacementSource {
        override suspend fun current(): WidgetPlacement = awaitCancellation()
    }

    /** Whatever the test last set, as the launcher would answer after the reader went to it. */
    private class SetPlacement(var now: WidgetPlacement) : WidgetPlacementSource {
        override suspend fun current() = now
    }

    /** In front, so the screen's ON_RESUME read of placement runs; and sent away and brought back,
     * as leaving Taqwa for the home screen and returning does, for the read that follows. */
    private class InFront : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        init { registry.currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry

        fun leaveAndComeBack() {
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    /** Not TaqwaTheme: its system-bar effects crash the simulator test process, and the colours
     * are all the screen reads from it. */
    @Composable
    private fun Screen(
        launcher: Launcher,
        placement: WidgetPlacementSource = NeitherPlaced,
        screenReader: Boolean = false,
        inFront: InFront = remember { InFront() },
    ) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalLifecycleOwner provides inFront,
            LocalScreenReaderOverride provides screenReader,
        ) {
            WidgetsSettingsScreen(
                widgetBackground = WidgetBackground.FOLLOW_THEME,
                onPickWidgetBackground = {},
                widgetPinRequester = launcher,
                widgetPlacementSource = placement,
                onBack = {},
            )
        }
    }

    /** The first step of this platform's way, which names no widget, as shown (its marked words
     * without their stars): one per card showing its steps open. */
    private suspend fun firstStep(): String = markedText(getString(widgetSteps(widgetAddPath).steps.first()), name = "").text

    /** Placement is read off the main thread, so the buttons arrive a moment after the screen. */
    private fun ComposeUiTest.bothButtonsArrive(add: String) {
        waitUntil("both Add to home screen buttons", timeoutMillis = 5_000) {
            onAllNodesWithText(add).fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun beforePlacementIsReadNeitherCardOffersAnything() = runComposeUiTest {
        setContent { Screen(Launcher(takes = true), placement = NeverAnswers) }
        waitForIdle()
        onAllNodesWithText(getString(Res.string.appearance_widget_add)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.widgets_on_home_ios)).assertCountEquals(0)
        onAllNodesWithText(firstStep()).assertCountEquals(0)
    }

    @Test
    fun aLauncherThatTakesTheRequestKeepsBothButtonsAndNeverShowsTheSteps() = runComposeUiTest {
        val launcher = Launcher(takes = true)
        setContent { Screen(launcher) }
        // The resources, not literals: the simulator's language decides the words.
        val add = getString(Res.string.appearance_widget_add)
        val steps = firstStep()
        bothButtonsArrive(add)
        onAllNodesWithText(steps).assertCountEquals(0)

        repeat(2) { onAllNodesWithText(add)[it].performScrollTo().performClick() }
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        onAllNodesWithText(add).assertCountEquals(2)
        onAllNodesWithText(steps).assertCountEquals(0)
    }

    @Test
    fun aLauncherThatRefusesTurnsEachButtonItRefusedIntoTheSteps() = runComposeUiTest {
        val launcher = Launcher(takes = false)
        setContent { Screen(launcher) }
        val add = getString(Res.string.appearance_widget_add)
        val steps = firstStep()
        bothButtonsArrive(add)
        onAllNodesWithText(steps).assertCountEquals(0)

        // The prayer button comes first: refused, it becomes the steps and the ayah button stays.
        onAllNodesWithText(add)[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(PinnableWidget.PRAYER), launcher.asked)
        onAllNodesWithText(add).assertCountEquals(1)
        onNodeWithText(steps).performScrollTo().assertIsDisplayed()

        // Then the ayah button: refused too, its own card answers its own tap with the steps,
        // and the prayer card's stay open above it.
        onNodeWithText(add).performScrollTo().performClick()
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        onAllNodesWithText(add).assertCountEquals(0)
        onAllNodesWithText(steps).assertCountEquals(2)
    }

    /** A rotation or a process death rebuilds the screen. A button the launcher refused stays the
     * steps, rather than going back to an "Add to home screen" that does nothing. */
    @Test
    fun aRefusedButtonStaysTheStepsAfterTheScreenIsRestored() = runComposeUiTest {
        val launcher = Launcher(takes = false)
        // What StateRestorationTester does on Android (its iOS build is a stub): save the
        // screen's state, take the screen away, and bring it back at the same place in the tree
        // from what was saved.
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var shown by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (shown) Screen(launcher)
            }
        }
        val add = getString(Res.string.appearance_widget_add)
        val steps = firstStep()
        bothButtonsArrive(add)
        onAllNodesWithText(add)[0].performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText(add).assertCountEquals(1)

        val saved = registry.performSave()
        shown = false
        waitForIdle()
        registry = SaveableStateRegistry(saved) { true }
        shown = true
        waitForIdle()
        // Placement is read again off the main thread: one offer per widget once it is back.
        waitUntil("both offers", timeoutMillis = 5_000) {
            onAllNodesWithText(add).fetchSemanticsNodes().size +
                onAllNodesWithText(steps).fetchSemanticsNodes().size == 2
        }
        onAllNodesWithText(add).assertCountEquals(1)
        onNodeWithText(steps).performScrollTo().assertIsDisplayed()
    }

    /** iPhone as it is: nothing can be asked, so both cards need the steps. They are written out
     * once, on the first card; the second has "Show me how", which opens the same steps there. */
    @Test
    fun withBothWidgetsMissingOnlyTheFirstCardShowsTheStepsOpen() = runComposeUiTest {
        setContent { Screen(Launcher(takes = false, canBeAsked = false)) }
        val steps = firstStep()
        val showHow = getString(Res.string.widgets_show_how)
        waitUntil("the first card's steps", timeoutMillis = 5_000) {
            onAllNodesWithText(steps).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText(steps).assertCountEquals(1)
        // The ayah card's row, and on iPhone the Lock Screen card's, which never opens by itself.
        onAllNodesWithText(showHow).assertCountEquals(2)

        onAllNodesWithText(showHow)[0].performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText(steps).assertCountEquals(2)
        onAllNodesWithText(showHow).assertCountEquals(1)
    }

    /** The owner's decision (31 Oct): the iPhone's gallery lists the prayer widget as "Prayer
     * times", the card's own name, so the steps open on the prayer card name it by that name. */
    @Test
    fun theIPhoneStepsNameThePrayerWidgetByItsCardsName() = runComposeUiTest {
        setContent { Screen(Launcher(takes = false, canBeAsked = false)) }
        val named = widgetSteps(widgetAddPath).steps.map { getString(it) }.single { "%1\$s" in it }
        val shown = markedText(named, name = getString(Res.string.widgets_prayer_name)).text
        val steps = firstStep()
        waitUntil("the prayer card's steps", timeoutMillis = 5_000) {
            onAllNodesWithText(steps).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(shown).performScrollTo().assertIsDisplayed()
    }

    /** Review round 2: "Show me how" goes away as its steps open, which left a screen reader's
     * focus nowhere. It moves to the first of the steps just opened, not to the ones open above. */
    @Test
    fun underAScreenReaderShowMeHowMovesFocusToTheFirstOfTheStepsItOpened() = runComposeUiTest {
        setContent { Screen(Launcher(takes = false, canBeAsked = false), screenReader = true) }
        val steps = firstStep()
        val showHow = getString(Res.string.widgets_show_how)
        waitUntil("the first card's steps", timeoutMillis = 5_000) {
            onAllNodesWithText(steps).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodes(isFocused()).assertCountEquals(0)

        onAllNodesWithText(showHow)[0].performScrollTo().performClick()
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onAllNodes(isFocused()).assertCountEquals(1)
        onAllNodesWithText(steps)[1].assertIsFocused()
    }

    /** The refused button goes too, so under a screen reader focus moves into the steps that
     * replace it (here the iPhone's, whose first stop is their first step). */
    @Test
    fun underAScreenReaderARefusedButtonMovesFocusIntoItsSteps() = runComposeUiTest {
        setContent { Screen(Launcher(takes = false), screenReader = true) }
        val add = getString(Res.string.appearance_widget_add)
        bothButtonsArrive(add)
        onAllNodesWithText(add)[1].performScrollTo().performClick()
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onAllNodes(isFocused()).assertCountEquals(1)
        onNodeWithText(firstStep()).assertIsFocused()
    }

    /** The owner's rule: accessibility work leaves the screen as it was for a sighted reader.
     * Moving focus scrolls the step into view, so without a screen reader nothing takes focus. */
    @Test
    fun withoutAScreenReaderShowMeHowMovesNoFocus() = runComposeUiTest {
        setContent { Screen(Launcher(takes = false, canBeAsked = false)) }
        val steps = firstStep()
        val showHow = getString(Res.string.widgets_show_how)
        waitUntil("the first card's steps", timeoutMillis = 5_000) {
            onAllNodesWithText(steps).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText(showHow)[0].performScrollTo().performClick()
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onAllNodesWithText(steps).assertCountEquals(2)
        onAllNodes(isFocused()).assertCountEquals(0)
    }

    /**
     * The Lead's follow-up 1 (31 Oct): under a screen reader steps are made known by moving focus
     * into them, never by a live region. The prayer card's refused steps took focus with no live
     * region; when the ayah card was refused next, the focus went there and the prayer card's
     * steps became a live region, which TalkBack may read out over the steps it was taken to.
     */
    @Test
    fun underAScreenReaderTwoRefusalsInARowLeaveNoLiveRegion() = runComposeUiTest {
        setContent { Screen(Launcher(takes = false), screenReader = true) }
        val add = getString(Res.string.appearance_widget_add)
        bothButtonsArrive(add)
        repeat(2) {
            onAllNodesWithText(add)[0].performScrollTo().performClick()
            mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
            waitForIdle()
        }
        onAllNodesWithText(add).assertCountEquals(0)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(0)
    }

    /** The same after a refusal and then another card's "Show me how" (on iPhone, the Lock
     * Screen card's), which takes the focus to its own first step. */
    @Test
    fun underAScreenReaderShowMeHowAfterARefusalLeavesNoLiveRegion() = runComposeUiTest {
        setContent { Screen(Launcher(takes = false), screenReader = true) }
        val add = getString(Res.string.appearance_widget_add)
        bothButtonsArrive(add)
        onAllNodesWithText(add)[0].performScrollTo().performClick()
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onNodeWithText(getString(Res.string.widgets_show_how)).performScrollTo().performClick()
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onAllNodes(isFocused()).assertCountEquals(1)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(0)
    }

    /**
     * Focus moves into steps once, as the tap that opened them is answered. Steps that go and come
     * back later (the widget added by hand, then removed, each seen on returning to Taqwa) did not
     * come from a tap just made, so they take no focus, which would also scroll the screen to them.
     */
    @Test
    fun underAScreenReaderStepsThatComeBackLaterTakeNoFocus() = runComposeUiTest {
        val placement = SetPlacement(WidgetPlacement(prayer = false, ayah = false))
        val inFront = InFront()
        setContent { Screen(Launcher(takes = false), placement = placement, screenReader = true, inFront = inFront) }
        val add = getString(Res.string.appearance_widget_add)
        val onHome = getString(Res.string.widgets_on_home_ios)
        val steps = firstStep()
        bothButtonsArrive(add)
        onAllNodesWithText(add)[0].performScrollTo().performClick()
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onNodeWithText(steps).assertIsFocused()

        placement.now = WidgetPlacement(prayer = true, ayah = false)
        runOnIdle { inFront.leaveAndComeBack() }
        waitUntil("the prayer card's check", timeoutMillis = 5_000) {
            onAllNodesWithText(onHome).fetchSemanticsNodes().size == 1
        }
        placement.now = WidgetPlacement(prayer = false, ayah = false)
        runOnIdle { inFront.leaveAndComeBack() }
        waitUntil("the prayer card's steps again", timeoutMillis = 5_000) {
            onAllNodesWithText(steps).fetchSemanticsNodes().size == 1
        }
        mainClock.advanceTimeBy(FOCUS_SETTLES_MS)
        waitForIdle()
        onAllNodes(isFocused()).assertCountEquals(0)
    }

    /** Review round 2: the prayer card's picture is sample names, times and a countdown, which a
     * screen reader read out before the card's own name. It is a picture, as the Lock Screen's is. */
    @Test
    fun thePrayerPreviewIsHiddenFromScreenReaders() = runComposeUiTest {
        var shown: WidgetContent? = null
        setContent {
            // What the screen's preview shows: the mirror when the Prayer screen has written it,
            // otherwise the sample.
            val mirror = remember { WidgetMirrorWriter.read(createWidgetKeyValueStore())?.let(WidgetContentBuilder::build) }
            shown = mirror ?: sampleWidgetContent()
            Screen(Launcher(takes = true))
        }
        waitForIdle()
        val content = checkNotNull(shown)
        // The tree a screen reader reads: the merged one, which leaves out what a picture clears.
        onAllNodesWithText(content.countdownLabel, substring = true).assertCountEquals(0)
        for (row in content.rows) {
            onAllNodesWithText(row.clockTime, substring = true).assertCountEquals(0)
        }
    }
}
