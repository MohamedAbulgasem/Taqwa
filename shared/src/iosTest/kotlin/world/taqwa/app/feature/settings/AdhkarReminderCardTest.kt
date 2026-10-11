package world.taqwa.app.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.notifications_adhkar
import world.taqwa.app.resources.notifications_adhkar_subtitle
import world.taqwa.app.resources.notifications_tahajjud_sound
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settings › Notifications, the adhkar card (adhkar spec §5): the switch with its subtitle, off on
 * a fresh install, and the sound row only while it is on, as Tahajjud's card does. Expected words
 * come from the resources: the simulator's language decides them.
 */
@OptIn(ExperimentalTestApi::class)
class AdhkarReminderCardTest {

    @Test
    fun theAdhkarCardOffersItsSoundOnlyWhileItIsOn() = runComposeUiTest {
        var settings by mutableStateOf(NotificationSettings())
        val toggled = mutableListOf<Boolean>()
        setContent {
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
                    onToggleAdhkar = { toggled += it },
                )
            }
        }
        val sound = getString(Res.string.notifications_tahajjud_sound)
        onNodeWithText(getString(Res.string.notifications_adhkar)).performScrollTo().assertIsDisplayed()
        onNodeWithText(getString(Res.string.notifications_adhkar_subtitle)).performScrollTo().assertIsDisplayed()
        // Off on a fresh install, Tahajjud too: no sound row anywhere.
        onAllNodesWithText(sound).assertCountEquals(0)

        // The card is the last on the screen, and its switch the last thing on it to press.
        onAllNodes(hasClickAction()).onLast().performScrollTo().performClick()
        assertEquals(listOf(true), toggled)

        settings = settings.copy(adhkar = true)
        waitForIdle()
        onNodeWithText(sound).performScrollTo().assertIsDisplayed()
    }
}
