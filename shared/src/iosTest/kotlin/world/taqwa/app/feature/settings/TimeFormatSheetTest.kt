package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_times_time_format
import world.taqwa.app.resources.time_format_12
import world.taqwa.app.resources.time_format_24
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Settings › Prayer times › Time format: the row says which format is in use, a tap opens a sheet
 * of the two, each shown with the same sample time the way it writes it, and a pick reports the
 * choice and closes the sheet. To a screen reader the options also say which one is chosen.
 * Expected texts come from the resources and the platform format, not from literals: the
 * simulator's language decides every word and digit on screen.
 */
@OptIn(ExperimentalTestApi::class)
class TimeFormatSheetTest {

    @Composable
    private fun Screen(clockFormat: ClockFormat, onClockFormat: (ClockFormat) -> Unit) {
        // Not TaqwaTheme: it also installs the system-bar and keep-screen-on effects, which crash
        // the simulator test process. The colours are all the screen reads from it.
        CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
            PrayerTimesSettingsScreen(
                settings = PrayerSettings(),
                status = null,
                adjustmentsPaused = false,
                placeName = null,
                today = LocalDate(2026, 10, 11),
                clockFormat = clockFormat,
                onClockFormat = onClockFormat,
                onSchool = {},
                onHijriOffset = {},
                onShowSunrise = {},
                onShowBothAsr = {},
                onShowWhereDiffer = {},
                onSaudiFajrLater = {},
                onBack = {},
                onOpenTimetable = {},
                onOpenHighLatitudePicker = {},
                onOpenManualAdjustments = {},
                onOpenAboutTimes = {},
            )
        }
    }

    @Test
    fun theRowNamesTheFormatInUse() = runComposeUiTest {
        setContent { Screen(ClockFormat.TWENTY_FOUR_HOUR) {} }
        val label = getString(Res.string.prayer_times_time_format)
        val h24 = getString(Res.string.time_format_24)
        onNodeWithText(label).performScrollTo().assertIsDisplayed()
        // One row carries both its label and its value; nothing else says "24-hour" yet.
        onAllNodesWithText(h24).assertCountEquals(1)
        onNode(hasText(label) and hasText(h24)).assertIsDisplayed()
    }

    @Test
    fun theRowFollowsTheStoredFormat() = runComposeUiTest {
        setContent { Screen(ClockFormat.TWELVE_HOUR) {} }
        val label = getString(Res.string.prayer_times_time_format)
        val h12 = getString(Res.string.time_format_12)
        onNodeWithText(label).performScrollTo()
        onNode(hasText(label) and hasText(h12)).assertIsDisplayed()
    }

    @Test
    fun theSheetOffersBothFormatsWithTheirSampleAndAPickReportsAndCloses() = runComposeUiTest {
        var picked: ClockFormat? = null
        setContent { Screen(ClockFormat.TWENTY_FOUR_HOUR) { picked = it } }
        val label = getString(Res.string.prayer_times_time_format)
        val h24 = getString(Res.string.time_format_24)
        val h12 = getString(Res.string.time_format_12)
        val sample24 = createPlatformFormat(ClockFormat.TWENTY_FOUR_HOUR).clockTime(18, 32)
        val sample12 = createPlatformFormat(ClockFormat.TWELVE_HOUR).clockTime(18, 32)

        // Closed: no sheet, so no 12-hour option anywhere and no sample.
        onAllNodesWithText(h12).assertCountEquals(0)
        onNodeWithText(label).performScrollTo().performClick()

        // Open: the row's own value and the sheet's option both say "24-hour"; the title repeats
        // the row's label. Each option carries the sample time the way that format writes it.
        onAllNodesWithText(h24).assertCountEquals(2)
        onAllNodesWithText(label).assertCountEquals(2)
        onNode(hasText(h24) and hasText(sample24)).assertIsDisplayed()
        onNode(hasText(h12) and hasText(sample12)).assertIsDisplayed()
        assertNull(picked)

        onNodeWithText(h12).performClick()
        assertEquals(ClockFormat.TWELVE_HOUR, picked)
        // The pick closes the sheet: the 12-hour option is gone again.
        waitForIdle()
        onAllNodesWithText(h12).assertCountEquals(0)
    }

    /** The sheet's option for [format]: its name and the sample time it writes, one row. */
    private suspend fun option(format: ClockFormat): SemanticsMatcher {
        val name = when (format) {
            ClockFormat.TWENTY_FOUR_HOUR -> getString(Res.string.time_format_24)
            ClockFormat.TWELVE_HOUR -> getString(Res.string.time_format_12)
        }
        return hasText(name) and hasText(createPlatformFormat(format).clockTime(18, 32))
    }

    /**
     * A screen reader hears which clock is in use, not only a sighted reader seeing the check mark:
     * the stored option is selected and the other is not, the two are the only options of one
     * selectable group (the divider between them is a node of its own, and no option), and a pick
     * of the other still reaches the callback.
     */
    private suspend fun ComposeUiTest.theSheetSaysWhichClockIsChosen(stored: ClockFormat, other: ClockFormat) {
        var picked: ClockFormat? = null
        setContent { Screen(stored) { picked = it } }
        onNodeWithText(getString(Res.string.prayer_times_time_format)).performScrollTo().performClick()

        val chosen = option(stored)
        val offered = option(other)
        onNode(chosen).assertIsSelected()
        onNode(offered).assertIsNotSelected()
        val group = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
        onNode(group and hasAnyChild(chosen) and hasAnyChild(offered))
            .onChildren()
            .filter(isSelectable())
            .assertCountEquals(2)

        onNode(offered).performClick()
        assertEquals(other, picked)
    }

    @Test
    fun theSheetSaysTheTwentyFourHourClockIsChosen() = runComposeUiTest {
        theSheetSaysWhichClockIsChosen(ClockFormat.TWENTY_FOUR_HOUR, ClockFormat.TWELVE_HOUR)
    }

    @Test
    fun theSheetSaysTheTwelveHourClockIsChosen() = runComposeUiTest {
        theSheetSaysWhichClockIsChosen(ClockFormat.TWELVE_HOUR, ClockFormat.TWENTY_FOUR_HOUR)
    }
}
