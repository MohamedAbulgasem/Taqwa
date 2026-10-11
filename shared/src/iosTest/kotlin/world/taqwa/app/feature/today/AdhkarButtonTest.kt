package world.taqwa.app.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.today_open_adhkar
import world.taqwa.app.resources.today_open_tasbeeh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The Prayer screen's header ends with two glyph buttons: the horizon, which opens the adhkar, and
 * the misbaha, which opens the tasbeeh and keeps the corner. Each is found by its content
 * description (the glyphs have no words), taps report only to its own callback, and both are the
 * 44 dp target. Run upright and sideways, and in both layout directions, because the landscape body
 * shares the header row and "the misbaha keeps the corner" must hold under Arabic too.
 *
 * Expected descriptions come from the resources, not from literals: the simulator's language
 * decides every word on screen.
 */
@OptIn(ExperimentalTestApi::class)
class AdhkarButtonTest {

    private class Taps {
        var adhkar = 0
        var tasbeeh = 0
    }

    /** A ready state with fixed instants: nothing here depends on the engine or on today's date. */
    private fun ready(): TodayUiState.Ready {
        val fajr = Instant.parse("2026-10-11T02:45:00Z")
        val times = listOf(
            Prayer.FAJR to fajr,
            Prayer.DHUHR to fajr + 480.minutes,
            Prayer.ASR to fajr + 660.minutes,
            Prayer.MAGHRIB to fajr + 900.minutes,
            Prayer.ISHA to fajr + 990.minutes,
        )
        val rows = times.map { (prayer, instant) ->
            TimelineRow(prayer, instant, if (prayer == Prayer.DHUHR) PrayerStatus.CURRENT else PrayerStatus.UPCOMING)
        }
        val next = PrayerTime(Prayer.ASR, fajr + 660.minutes)
        return TodayUiState.Ready(
            location = GeoLocation(41.0082, 28.9784, "Europe/Istanbul", "Istanbul", "TR"),
            cityDisplayName = "Istanbul",
            hijri = "29 Rabi’ al-Awwal 1448",
            gregorian = "11 October 2026",
            today = TodayState(rows, next, 180.minutes, ringProgress = 0.4f),
            whose = WhoseTimes("authority_diyanet", EntryClass.A),
            qiblaBearingDegrees = 152.0,
            qiblaDistanceKm = 2406.0,
        )
    }

    @Composable
    private fun Screen(taps: Taps, direction: LayoutDirection, width: Dp, height: Dp) {
        // Not TaqwaTheme: it also installs the system-bar and keep-screen-on effects, which crash
        // the simulator test process. The colours are all the screen reads from it.
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalLayoutDirection provides direction,
        ) {
            Box(Modifier.size(width, height)) {
                TodayScreen(
                    state = ready(),
                    onChooseCity = {},
                    onOpenLocationSettings = {},
                    onOpenQibla = {},
                    onOpenAdhkar = { taps.adhkar++ },
                    onOpenTasbeeh = { taps.tasbeeh++ },
                )
            }
        }
    }

    // Inside the test window (1024 x 768 at density 1), so the sizes are what the screen is given.
    private val upright = 411.dp to 760.dp
    private val sideways = 891.dp to 411.dp

    private suspend fun ComposeUiTest.bothButtonsReportToTheirOwnCallbackOnly(direction: LayoutDirection, size: Pair<Dp, Dp>) {
        val taps = Taps()
        setContent { Screen(taps, direction, size.first, size.second) }
        val adhkar = getString(Res.string.today_open_adhkar)
        val tasbeeh = getString(Res.string.today_open_tasbeeh)

        // One of each: the header row carries both, the sideways pane included.
        onAllNodesWithContentDescription(adhkar).assertCountEquals(1)
        onAllNodesWithContentDescription(tasbeeh).assertCountEquals(1)

        onNodeWithContentDescription(adhkar).performClick()
        assertEquals(1, taps.adhkar)
        assertEquals(0, taps.tasbeeh)

        onNodeWithContentDescription(tasbeeh).performClick()
        assertEquals(1, taps.adhkar)
        assertEquals(1, taps.tasbeeh)
    }

    @Test
    fun theHorizonOpensTheAdhkarAndTheMisbahaStillOpensTheTasbeehUpright() = runComposeUiTest {
        bothButtonsReportToTheirOwnCallbackOnly(LayoutDirection.Ltr, upright)
    }

    @Test
    fun theHorizonOpensTheAdhkarAndTheMisbahaStillOpensTheTasbeehSideways() = runComposeUiTest {
        bothButtonsReportToTheirOwnCallbackOnly(LayoutDirection.Ltr, sideways)
    }

    @Test
    fun bothButtonsAreTheFortyFourDpTarget() = runComposeUiTest {
        setContent { Screen(Taps(), LayoutDirection.Ltr, upright.first, upright.second) }
        val adhkar = getString(Res.string.today_open_adhkar)
        val tasbeeh = getString(Res.string.today_open_tasbeeh)
        for (description in listOf(adhkar, tasbeeh)) {
            onNodeWithContentDescription(description).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        }
    }

    /** The horizon comes first in reading order, so the misbaha keeps the end corner. */
    private suspend fun ComposeUiTest.horizonSitsBeforeTheMisbaha(direction: LayoutDirection, size: Pair<Dp, Dp>) {
        setContent { Screen(Taps(), direction, size.first, size.second) }
        val adhkar = getString(Res.string.today_open_adhkar)
        val tasbeeh = getString(Res.string.today_open_tasbeeh)
        val horizon = onNodeWithContentDescription(adhkar).fetchSemanticsNode().boundsInRoot
        val misbaha = onNodeWithContentDescription(tasbeeh).fetchSemanticsNode().boundsInRoot
        // Side by side on one line, not stacked.
        assertEquals(horizon.top, misbaha.top)
        when (direction) {
            LayoutDirection.Ltr -> assertTrue(horizon.right <= misbaha.left, "LTR: $horizon should end before $misbaha starts")
            LayoutDirection.Rtl -> assertTrue(misbaha.right <= horizon.left, "RTL: $misbaha should end before $horizon starts")
        }
    }

    @Test
    fun theHorizonSitsBeforeTheMisbahaLeftToRight() = runComposeUiTest {
        horizonSitsBeforeTheMisbaha(LayoutDirection.Ltr, upright)
    }

    @Test
    fun theHorizonSitsBeforeTheMisbahaRightToLeft() = runComposeUiTest {
        horizonSitsBeforeTheMisbaha(LayoutDirection.Rtl, upright)
    }

    @Test
    fun theHorizonSitsBeforeTheMisbahaSideways() = runComposeUiTest {
        horizonSitsBeforeTheMisbaha(LayoutDirection.Ltr, sideways)
    }
}
