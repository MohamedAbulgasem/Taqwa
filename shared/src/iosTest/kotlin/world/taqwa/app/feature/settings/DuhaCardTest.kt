package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.DuhaTiming
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.prayer.DuhaTimes
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.notifications_duha
import world.taqwa.app.resources.notifications_duha_best
import world.taqwa.app.resources.notifications_duha_best_detail
import world.taqwa.app.resources.notifications_duha_start
import world.taqwa.app.resources.notifications_duha_start_detail
import world.taqwa.app.resources.notifications_duha_when
import world.taqwa.app.resources.notifications_section_when
import world.taqwa.app.resources.notifications_tahajjud_sound
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settings › Notifications, the Duha card (Duha and Iqama spec §4.2): off it is one row and its
 * switch; on, the timing's detail, When and Sound appear; When opens a sheet of the two timings
 * as radio rows. Expected words come from the resources: the simulator's language decides them.
 */
@OptIn(ExperimentalTestApi::class)
class DuhaCardTest {

    private val radioRow = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Composable
    private fun Screen(
        settings: NotificationSettings,
        onToggleDuha: (Boolean) -> Unit = {},
        onPickDuhaTiming: (DuhaTiming) -> Unit = {},
    ) {
        // Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test
        // process. The colours are all the screen reads from it.
        CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
            NotificationSettingsScreen(
                settings = settings,
                onBack = {},
                onToggleEnabled = {},
                onPickLead = {},
                onPickSound = { _, _ -> },
                onPreviewSound = {},
                onToggleDuha = onToggleDuha,
                onPickDuhaTiming = onPickDuhaTiming,
            )
        }
    }

    @Test
    fun offTheDuhaCardIsOnlyItsRow() = runComposeUiTest {
        val toggled = mutableListOf<Boolean>()
        setContent { Screen(NotificationSettings(), onToggleDuha = { toggled += it }) }
        val duha = onNode(isToggleable() and hasText(getString(Res.string.notifications_duha)))
        duha.performScrollTo().assertIsDisplayed().assertIsOff()
        onAllNodesWithText(getString(Res.string.notifications_duha_best_detail)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.notifications_duha_when)).assertCountEquals(0)
        // Tahajjud and adhkar are off on a fresh install too: no sound row anywhere.
        onAllNodesWithText(getString(Res.string.notifications_tahajjud_sound)).assertCountEquals(0)

        // Activated as a screen reader's double-tap does: only the drawn switch takes a touch.
        duha.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(true), toggled)
    }

    @Test
    fun onTheDuhaCardShowsItsTimingWhenAndSound() = runComposeUiTest {
        var settings by mutableStateOf(NotificationSettings(duha = true))
        setContent { Screen(settings) }
        val best = getString(Res.string.notifications_duha_best)
        val whenLabel = getString(Res.string.notifications_duha_when)
        onNode(isToggleable() and hasText(getString(Res.string.notifications_duha))).performScrollTo().assertIsOn()
        onNodeWithText(getString(Res.string.notifications_duha_best_detail)).performScrollTo().assertIsDisplayed()
        onNode(hasText(whenLabel) and hasText(best)).performScrollTo().assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.notifications_tahajjud_sound)).assertCountEquals(1)

        // The other timing: its own detail, with the minutes in the locale's digits.
        settings = settings.copy(duhaTiming = DuhaTiming.START)
        waitForIdle()
        val fifty = createPlatformFormat().localizedDigits(DuhaTimes.AFTER_SUNRISE.inWholeMinutes.toInt())
        onNodeWithText(getString(Res.string.notifications_duha_start_detail, fifty)).performScrollTo().assertIsDisplayed()
        onNode(hasText(whenLabel) and hasText(getString(Res.string.notifications_duha_start))).assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.notifications_duha_best_detail)).assertCountEquals(0)
    }

    @Test
    fun theWhenSheetOffersTwoRadioRowsAndAPickReportsAndCloses() = runComposeUiTest {
        var picked: DuhaTiming? = null
        setContent { Screen(NotificationSettings(duha = true), onPickDuhaTiming = { picked = it }) }
        val best = getString(Res.string.notifications_duha_best)
        val start = getString(Res.string.notifications_duha_start)
        onAllNodesWithText(getString(Res.string.notifications_section_when)).assertCountEquals(0)
        onNodeWithText(getString(Res.string.notifications_duha_when)).performScrollTo().performClick()

        onNodeWithText(getString(Res.string.notifications_section_when)).assertIsDisplayed()
        onAllNodes(radioRow).assertCountEquals(2)
        onNode(radioRow and hasText(best) and isSelected()).assertIsDisplayed()
        onNode(radioRow and hasText(start) and isNotSelected()).assertIsDisplayed()

        onNode(radioRow and hasText(start)).performClick()
        assertEquals(DuhaTiming.START, picked)
        waitForIdle()
        onAllNodes(radioRow).assertCountEquals(0)
    }
}
