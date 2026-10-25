package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.notifications_tahajjud
import world.taqwa.app.resources.notifications_tahajjud_sound
import world.taqwa.app.resources.notifications_tahajjud_subtitle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settings › Notifications, the Tahajjud card (spec §17.6), built as the Duha and Iqama cards are:
 * off, one quiet row, its title and its switch; on, its subtitle and its sound row appear.
 * Expected words come from the resources: the simulator's language decides them.
 */
@OptIn(ExperimentalTestApi::class)
class TahajjudCardTest {

    @Composable
    private fun Screen(settings: NotificationSettings, onToggleTahajjud: (Boolean) -> Unit = {}) {
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
                onToggleTahajjud = onToggleTahajjud,
            )
        }
    }

    @Test
    fun offTheTahajjudCardIsOnlyItsRow() = runComposeUiTest {
        val toggled = mutableListOf<Boolean>()
        setContent { Screen(NotificationSettings(), onToggleTahajjud = { toggled += it }) }
        val tahajjud = onNode(isToggleable() and hasText(getString(Res.string.notifications_tahajjud)))
        tahajjud.performScrollTo().assertIsDisplayed().assertIsOff()
        onAllNodesWithText(getString(Res.string.notifications_tahajjud_subtitle)).assertCountEquals(0)
        // Off on a fresh install, Duha and adhkar too: no sound row anywhere.
        onAllNodesWithText(getString(Res.string.notifications_tahajjud_sound)).assertCountEquals(0)

        // Activated as a screen reader's double-tap does: only the drawn switch takes a touch.
        tahajjud.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(true), toggled)
    }

    @Test
    fun onTheTahajjudCardShowsItsSubtitleAndSound() = runComposeUiTest {
        var settings by mutableStateOf(NotificationSettings())
        setContent { Screen(settings) }
        val subtitle = getString(Res.string.notifications_tahajjud_subtitle)
        onAllNodesWithText(subtitle).assertCountEquals(0)

        settings = settings.copy(tahajjud = true)
        waitForIdle()
        onNode(isToggleable() and hasText(getString(Res.string.notifications_tahajjud))).performScrollTo().assertIsOn()
        onNodeWithText(subtitle).performScrollTo().assertIsDisplayed()
        onNodeWithText(getString(Res.string.notifications_tahajjud_sound)).performScrollTo().assertIsDisplayed()
    }
}
