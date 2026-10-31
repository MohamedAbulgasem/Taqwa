package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_language_label
import world.taqwa.app.resources.language_name
import world.taqwa.app.resources.settings_appearance_value
import world.taqwa.app.resources.settings_language
import world.taqwa.app.resources.theme_system
import kotlin.test.Test

/**
 * The language is set once, if ever (Taqwa follows the phone's), so it lives inside Appearance
 * with the theme, and the root's Appearance row names both: a reader who picked a language they
 * cannot read still finds their own language's name on the first screen.
 */
@OptIn(ExperimentalTestApi::class)
class LanguageInAppearanceTest {

    /** Not TaqwaTheme: its system-bar effects crash the simulator test process. */
    @Composable
    private fun Colours(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalTaqwaColors provides LightColors, content = content)

    @Composable
    private fun Root(themeName: String) = Colours {
        SettingsRootScreen(
            cityName = "Cape Town",
            methodName = "Cautious",
            themeName = themeName,
            notificationSettings = NotificationSettings(),
            onOpenLocation = {},
            onOpenPrayerTimes = {},
            onOpenNotifications = {},
            onOpenAppearance = {},
            widgetsValue = null,
            onOpenWidgets = {},
            onOpenAbout = {},
            reciterName = "Alafasy",
            onOpenRecitation = {},
        )
    }

    @Test
    fun theRootHasNoLanguageRowAndItsAppearanceRowNamesTheThemeAndTheLanguage() = runComposeUiTest {
        val system = getString(Res.string.theme_system)
        setContent { Root(themeName = system) }
        waitForIdle()
        onAllNodesWithText(getString(Res.string.settings_language)).assertCountEquals(0)
        val value = getString(Res.string.settings_appearance_value, system, getString(Res.string.language_name))
        onNodeWithText(value).assertIsDisplayed()
    }

    @Test
    fun appearanceHoldsTheLanguageUnderTheTheme() = runComposeUiTest {
        setContent { Colours { AppearanceSettingsScreen(current = ThemeMode.SYSTEM, onPick = {}, onBack = {}) } }
        waitForIdle()
        onNodeWithText(getString(Res.string.appearance_language_label)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.language_name)).assertIsDisplayed()
    }
}
