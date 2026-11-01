package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_widget_add
import world.taqwa.app.resources.widgets_on_home_ios
import world.taqwa.app.resources.widgets_on_lock_ios
import world.taqwa.app.resources.widgets_show_how
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetPinRequester
import world.taqwa.app.widget.WidgetPlacement
import world.taqwa.app.widget.WidgetPlacementSource
import world.taqwa.app.widget.widgetAddPath
import kotlin.test.Test

/**
 * What the Widgets screen says about each widget's place, on the iPhone as it is (nothing can be
 * asked of the launcher): a check only for a widget the platform said is there, on the card of the
 * widget it said, and nothing at all while the platform cannot tell.
 *
 * Every case here was a false "On your Home Screen" before review round 2: a Lock Screen widget
 * put the check on the prayer card, and a read that could not tell put it on every card.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetsPlacementStatusTest {

    private object IPhone : WidgetPinRequester {
        override fun isSupported(widget: PinnableWidget) = false
        override fun requestPin(widget: PinnableWidget) = false
    }

    /** Gives [answers] in turn, the last one for every read after, and counts the reads. */
    private class Answers(private vararg val answers: WidgetPlacement?) : WidgetPlacementSource {
        var reads = 0
            private set

        override suspend fun current(): WidgetPlacement? = answers[minOf(reads++, answers.lastIndex)]
    }

    /** A lifecycle the test can take out of RESUMED and back, as a pull of Control Center does. */
    private class Front : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        init { registry.currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    /** Not TaqwaTheme: its system-bar effects crash the simulator test process. */
    @Composable
    private fun Screen(placement: WidgetPlacementSource, front: Front = Front()) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalLifecycleOwner provides front,
        ) {
            WidgetsSettingsScreen(
                widgetBackground = WidgetBackground.FOLLOW_THEME,
                onPickWidgetBackground = {},
                widgetPinRequester = IPhone,
                widgetPlacementSource = placement,
                onBack = {},
            )
        }
    }

    /** Placement is read off the main thread: wait for the read, then for the screen to settle. */
    private fun ComposeUiTest.afterRead(source: Answers, reads: Int) {
        waitUntil("read $reads of placement", timeoutMillis = 5_000) { source.reads >= reads }
        waitForIdle()
    }

    private suspend fun firstStep(): String = markedText(getString(widgetSteps(widgetAddPath).steps.first()), name = "").text

    @Test
    fun aSourceThatCannotTellShowsNoStatusAndNoStepsOnAnyCard() = runComposeUiTest {
        val source = Answers(null)
        setContent { Screen(source) }
        afterRead(source, reads = 1)
        onAllNodesWithText(getString(Res.string.widgets_on_home_ios)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.widgets_on_lock_ios)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.widgets_show_how)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.appearance_widget_add)).assertCountEquals(0)
        onAllNodesWithText(firstStep()).assertCountEquals(0)
    }

    @Test
    fun aLockScreenWidgetAlonePutsTheCheckOnTheLockScreenCardOnly() = runComposeUiTest {
        val source = Answers(WidgetPlacement(prayer = false, ayah = false, lockScreen = true))
        setContent { Screen(source) }
        afterRead(source, reads = 1)
        onAllNodesWithText(getString(Res.string.widgets_on_home_ios)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.widgets_on_lock_ios)).assertCountEquals(1)
        // The prayer card writes its steps out; the ayah card has "Show me how"; the Lock Screen
        // card, its widget placed, has the check instead of its own.
        onAllNodesWithText(firstStep()).assertCountEquals(1)
        onAllNodesWithText(getString(Res.string.widgets_show_how)).assertCountEquals(1)
    }

    @Test
    fun theHomeScreenWidgetsAloneLeaveTheLockScreenCardItsSteps() = runComposeUiTest {
        val source = Answers(WidgetPlacement(prayer = true, ayah = true, lockScreen = false))
        setContent { Screen(source) }
        afterRead(source, reads = 1)
        onAllNodesWithText(getString(Res.string.widgets_on_home_ios)).assertCountEquals(2)
        onAllNodesWithText(getString(Res.string.widgets_on_lock_ios)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.widgets_show_how)).assertCountEquals(1)
    }

    /** Leaving RESUMED and coming back reads placement again; a read that cannot tell then must
     * not take away the checks the last answer gave. */
    @Test
    fun aReadThatCannotTellAfterAnAnswerKeepsEveryCheck() = runComposeUiTest {
        val source = Answers(WidgetPlacement(prayer = true, ayah = true, lockScreen = true), null)
        val front = Front()
        setContent { Screen(source, front) }
        afterRead(source, reads = 1)
        onAllNodesWithText(getString(Res.string.widgets_on_home_ios)).assertCountEquals(2)
        onAllNodesWithText(getString(Res.string.widgets_on_lock_ios)).assertCountEquals(1)

        runOnIdle { front.registry.currentState = Lifecycle.State.STARTED }
        runOnIdle { front.registry.currentState = Lifecycle.State.RESUMED }
        afterRead(source, reads = 2)
        onAllNodesWithText(getString(Res.string.widgets_on_home_ios)).assertCountEquals(2)
        onAllNodesWithText(getString(Res.string.widgets_on_lock_ios)).assertCountEquals(1)
        onAllNodesWithText(getString(Res.string.widgets_show_how)).assertCountEquals(0)
    }
}
