package world.taqwa.app.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.onboarding_widget_cta_add
import world.taqwa.app.resources.onboarding_widget_cta_done
import world.taqwa.app.resources.onboarding_widget_steps_android
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetAddPath
import world.taqwa.app.widget.WidgetPinRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The widget screen's tap, end to end, with a launcher that answers as told. The rule: the steps
 * by hand appear only when the launcher refused; a request it took ends onboarding as before.
 * Driven as Android ([WidgetAddPath.ANDROID_PIN]), the only platform that can be asked.
 */
@OptIn(ExperimentalTestApi::class)
class OnboardingWidgetPageTest {

    private class Launcher(private val takes: Boolean, private val canPin: Boolean = true) : WidgetPinRequester {
        val asked = mutableListOf<PinnableWidget>()
        override fun isSupported(widget: PinnableWidget) = canPin
        override fun requestPin(widget: PinnableWidget): Boolean {
            asked += widget
            return takes
        }
    }

    /** The whole page on a phone of [SHORT_PHONE]'s size, inside the test window (1024 x 768 at
     * density 1), at the text size given. Not TaqwaTheme: its system-bar effects crash the
     * simulator test process, and the colours are all the page reads from it. */
    @Composable
    private fun Screen(launcher: Launcher, fontScale: Float = 1f, onComplete: () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            Box(Modifier.size(SHORT_PHONE.first, SHORT_PHONE.second).testTag(SCREEN)) {
                OnboardingWidgetPage(launcher, WidgetAddPath.ANDROID_PIN, onComplete)
            }
        }
    }

    @Test
    fun aRequestTheLauncherTookFinishesOnboardingAndShowsNoSteps() = runComposeUiTest {
        val launcher = Launcher(takes = true)
        var completed = false
        setContent { Screen(launcher) { completed = true } }
        // The resource, not a literal: the simulator's language decides the words.
        val add = getString(Res.string.onboarding_widget_cta_add)
        onNodeWithText(add).performClick()
        waitForIdle()
        assertEquals(listOf(PinnableWidget.PRAYER), launcher.asked)
        assertTrue(completed)
        onNodeWithText(getString(Res.string.onboarding_widget_steps_android)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.onboarding_widget_cta_done)).assertDoesNotExist()
        onNodeWithText(add).assertExists()
    }

    @Test
    fun aRequestTheLauncherRefusedStaysAndPutsTheStepsAndDoneWhereTheButtonWas() = runComposeUiTest {
        val launcher = Launcher(takes = false)
        var completed = false
        setContent { Screen(launcher) { completed = true } }
        val add = getString(Res.string.onboarding_widget_cta_add)
        onNodeWithText(add).performClick()
        waitForIdle()
        assertEquals(listOf(PinnableWidget.PRAYER), launcher.asked)
        assertFalse(completed)
        onNodeWithText(add).assertDoesNotExist()
        // A polite live region, so TalkBack reads what replaced the button it was on.
        onNodeWithText(getString(Res.string.onboarding_widget_steps_android))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        onNodeWithText(getString(Res.string.onboarding_widget_cta_done))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .performClick()
        assertTrue(completed)
    }

    /** A launcher that cannot pin at all shows the steps from the first frame. Nothing replaced
     * anything, so they are read in their place like the rest of the screen, not announced. */
    @Test
    fun onAPhoneThatCannotPinTheStepsAreThereFromTheStartAndNotLive() = runComposeUiTest {
        val launcher = Launcher(takes = false, canPin = false)
        setContent { Screen(launcher) {} }
        onNodeWithText(getString(Res.string.onboarding_widget_cta_add)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.onboarding_widget_steps_android))
            .assertExists()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        assertEquals(emptyList(), launcher.asked)
    }

    /** At the largest text on a short phone the steps are taller than the room left for them.
     * Done must still be whole and on screen, and the steps scrolled into view, not left below. */
    @Test
    fun atTheLargestTextOnAShortPhoneDoneStaysOnScreenUnderTheSteps() = runComposeUiTest {
        val launcher = Launcher(takes = false)
        var completed = false
        setContent { Screen(launcher, fontScale = 2f) { completed = true } }
        onNodeWithText(getString(Res.string.onboarding_widget_cta_add)).performClick()
        waitForIdle()

        val screen = onNodeWithTag(SCREEN).getUnclippedBoundsInRoot()
        val done = onNodeWithText(getString(Res.string.onboarding_widget_cta_done))
        val bounds = done.getUnclippedBoundsInRoot()
        assertTrue(bounds.top >= screen.top && bounds.bottom <= screen.bottom, "Done at $bounds, screen $screen")
        assertTrue(bounds.bottom - bounds.top >= 48.dp, "Done squeezed to $bounds")
        onNodeWithText(getString(Res.string.onboarding_widget_steps_android)).assertIsDisplayed()
        done.assertIsDisplayed().performClick()
        assertTrue(completed)
    }

    private companion object {
        const val SCREEN = "screen"

        /** The project's short three-button phone (spec §19): 1224 x 2280 at 480 dpi. */
        val SHORT_PHONE = 408.dp to 760.dp
    }
}
