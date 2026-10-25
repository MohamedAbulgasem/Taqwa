package world.taqwa.app.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_prayer_times_settings
import world.taqwa.app.resources.timetable_match_mosque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Prayer › About these times, the two actions at the foot of its first section: "Match my
 * mosque ›" and "Prayer times settings ›" are both links, so both read in the accent and both
 * answer a tap (and a screen reader's double-tap). Toronto is a cautious place, the one template
 * that shows both. Expected words come from the resources: the simulator's language decides them.
 */
@OptIn(ExperimentalTestApi::class)
class AboutTimesLinksTest {

    private val toronto = Place(43.6532, -79.3832, "America/Toronto", "CA")
    private val date = LocalDate(2026, 9, 26)

    /** The colour the link's words are drawn in, read from the text node's own layout. */
    private fun SemanticsNodeInteraction.textColor(): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull(action).invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    @Test
    fun bothLinksReadInTheAccentAndAnswerATap() = runComposeUiTest {
        val engineDay = PrayerEngine.dayTimes(toronto, date, EngineSettings())
        val state = aboutTimesUiState(engineDay, toronto, date)
        var matched = 0
        var opened = 0
        setContent {
            // Not TaqwaTheme: its system-bar and keep-screen-on effects crash the simulator test
            // process. The colours are all the screen reads from it.
            CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
                AboutTimesScreen(
                    state = state,
                    engineDay = engineDay,
                    place = toronto,
                    cityLabel = "Toronto",
                    zone = TimeZone.of(toronto.zoneId),
                    onBack = {},
                    onMatchMyMosque = { matched++ },
                    onOpenPrayerTimesSettings = { opened++ },
                )
            }
        }
        val match = getString(Res.string.timetable_match_mosque)
        val settings = getString(Res.string.about_prayer_times_settings)

        assertEquals(LightColors.accent, onNodeWithText(match, useUnmergedTree = true).textColor())
        assertEquals(LightColors.accent, onNodeWithText(settings, useUnmergedTree = true).textColor())

        onNodeWithText(match).performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        onNodeWithText(settings).performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(1, matched)
        assertEquals(1, opened)
    }
}
