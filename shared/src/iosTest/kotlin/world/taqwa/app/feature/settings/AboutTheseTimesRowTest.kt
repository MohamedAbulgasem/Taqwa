package world.taqwa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_times_about_row
import world.taqwa.app.resources.prayer_times_manual
import world.taqwa.app.resources.prayer_times_show_sunrise
import world.taqwa.app.resources.show_both_asr
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Settings › Prayer times: About these times is the Timetable card's last row, right below Manual
 * adjustments, so a reader who wonders where the times come from sees it on arrival, before Asr.
 */
@OptIn(ExperimentalTestApi::class)
class AboutTheseTimesRowTest {

    // Not TaqwaTheme: its system-bar effects crash the simulator test process.
    @Composable
    private fun Screen() {
        CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
            PrayerTimesSettingsScreen(
                settings = PrayerSettings(), status = null, adjustmentsPaused = false, placeName = null,
                today = LocalDate(2026, 11, 1), clockFormat = ClockFormat.TWENTY_FOUR_HOUR, onClockFormat = {},
                onSchool = {}, onHijriOffset = {}, onShowSunrise = {}, onShowBothAsr = {}, onShowWhereDiffer = {},
                onSaudiFajrLater = {}, onBack = {}, onOpenTimetable = {}, onOpenHighLatitudePicker = {},
                onOpenManualAdjustments = {}, onOpenAboutTimes = {},
            )
        }
    }

    @Test
    fun aboutTheseTimesSitsRightBelowManualAdjustmentsAndAboveAsr() = runComposeUiTest {
        setContent { Screen() }
        waitForIdle()
        val manual = onNode(hasText(getString(Res.string.prayer_times_manual))).fetchSemanticsNode().boundsInRoot
        // Seen on arrival, with no scroll.
        val about = onNodeWithText(getString(Res.string.prayer_times_about_row)).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val asr = onNode(hasText(getString(Res.string.show_both_asr))).fetchSemanticsNode().boundsInRoot
        assertTrue(about.top >= manual.bottom - 0.5f, "About these times ($about) is not below Manual adjustments ($manual)")
        // Only the card's divider between them: nothing else fits in under a row's height.
        assertTrue(about.top - manual.bottom < manual.height / 2, "something sits between Manual adjustments and About these times")
        assertTrue(about.bottom <= asr.top, "About these times ($about) is not above Asr ($asr)")
    }

    /** Show sunrise and Time format, how the list is shown, sit right under the timetable's card and above Asr
     * (the owner's call, 4 Nov 2026). */
    @Test
    fun theDisplayCardSitsBetweenTheTimetableCardAndAsr() = runComposeUiTest {
        setContent { Screen() }
        waitForIdle()
        val about = onNodeWithText(getString(Res.string.prayer_times_about_row)).fetchSemanticsNode().boundsInRoot
        val sunrise = onNode(hasText(getString(Res.string.prayer_times_show_sunrise))).fetchSemanticsNode().boundsInRoot
        val asr = onNode(hasText(getString(Res.string.show_both_asr))).fetchSemanticsNode().boundsInRoot
        assertTrue(sunrise.top >= about.bottom, "Show sunrise ($sunrise) is not below the timetable card ($about)")
        assertTrue(sunrise.bottom <= asr.top, "Show sunrise ($sunrise) is not above Asr ($asr)")
    }
}
