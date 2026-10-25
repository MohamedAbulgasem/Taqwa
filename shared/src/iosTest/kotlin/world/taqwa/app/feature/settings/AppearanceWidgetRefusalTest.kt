package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_widget_add
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetPinRequester
import world.taqwa.app.widget.WidgetPlacement
import world.taqwa.app.widget.WidgetPlacementSource
import world.taqwa.app.widget.widgetAddInstructionsKey
import world.taqwa.app.widget.widgetAddPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The whole Appearance screen with neither widget placed and a launcher that can be asked. The
 * owner's rule: the steps by hand appear only once the launcher has refused, and a phone whose
 * launcher takes the request keeps its "Add widget" rows exactly as before. [WidgetAddOfferTest]
 * proves one row reports a refusal; this proves the screen acts on it, for each widget.
 */
@OptIn(ExperimentalTestApi::class)
class AppearanceWidgetRefusalTest {

    private class Launcher(private val takes: Boolean) : WidgetPinRequester {
        val asked = mutableListOf<PinnableWidget>()
        override fun isSupported(widget: PinnableWidget) = true
        override fun requestPin(widget: PinnableWidget): Boolean {
            asked += widget
            return takes
        }
    }

    private object NeitherPlaced : WidgetPlacementSource {
        override suspend fun current() = WidgetPlacement(prayer = false, ayah = false)
    }

    /** In front, so the screen's ON_START read of placement runs. */
    private class InFront : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        init { registry.currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    /** Not TaqwaTheme: its system-bar effects crash the simulator test process, and the colours
     * are all the screen reads from it. */
    @Composable
    private fun Screen(launcher: Launcher) {
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalLifecycleOwner provides InFront(),
        ) {
            AppearanceSettingsScreen(
                current = ThemeMode.SYSTEM,
                onPick = {},
                widgetBackground = WidgetBackground.FOLLOW_THEME,
                onPickWidgetBackground = {},
                widgetPinRequester = launcher,
                widgetPlacementSource = NeitherPlaced,
                onBack = {},
            )
        }
    }

    /** Placement is read off the main thread, so the rows arrive a moment after the screen. */
    private fun ComposeUiTest.bothRowsArrive(add: String) {
        waitUntil("both Add widget rows", timeoutMillis = 5_000) {
            onAllNodesWithText(add).fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun aLauncherThatTakesTheRequestKeepsBothRowsAndNeverShowsTheSteps() = runComposeUiTest {
        val launcher = Launcher(takes = true)
        setContent { Screen(launcher) }
        // The resources, not literals: the simulator's language decides the words.
        val add = getString(Res.string.appearance_widget_add)
        val steps = getString(widgetAddInstructionsKey(widgetAddPath))
        bothRowsArrive(add)
        onAllNodesWithText(steps).assertCountEquals(0)

        repeat(2) { onAllNodesWithText(add)[it].performScrollTo().performClick() }
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        onAllNodesWithText(add).assertCountEquals(2)
        onAllNodesWithText(steps).assertCountEquals(0)
    }

    @Test
    fun aLauncherThatRefusesTurnsEachRowItRefusedIntoTheSteps() = runComposeUiTest {
        val launcher = Launcher(takes = false)
        setContent { Screen(launcher) }
        val add = getString(Res.string.appearance_widget_add)
        val steps = getString(widgetAddInstructionsKey(widgetAddPath))
        bothRowsArrive(add)
        onAllNodesWithText(steps).assertCountEquals(0)

        // The prayer row comes first: refused, it becomes the steps and the ayah row stays.
        onAllNodesWithText(add)[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(PinnableWidget.PRAYER), launcher.asked)
        onAllNodesWithText(add).assertCountEquals(1)
        onNodeWithText(steps).performScrollTo().assertIsDisplayed()

        // Then the ayah row: both refused, and the steps are said once for the two.
        onNodeWithText(add).performScrollTo().performClick()
        waitForIdle()
        assertEquals(PinnableWidget.entries.toList(), launcher.asked)
        onAllNodesWithText(add).assertCountEquals(0)
        onAllNodesWithText(steps).assertCountEquals(1)
    }

    /** A rotation or a process death rebuilds the screen. A row the launcher refused stays the
     * steps, rather than going back to an "Add widget" that does nothing. */
    @Test
    fun aRefusedRowStaysTheStepsAfterTheScreenIsRestored() = runComposeUiTest {
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
        val steps = getString(widgetAddInstructionsKey(widgetAddPath))
        bothRowsArrive(add)
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
}
