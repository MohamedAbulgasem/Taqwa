package world.taqwa.app.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import world.taqwa.app.resources.today_about_these_times
import world.taqwa.app.resources.today_open_adhkar
import world.taqwa.app.resources.today_open_tasbeeh
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * They sit on the city's line and nowhere else: the dates below run the block's whole width, the
 * glyphs are not part of the ⓘ's target, and a tap on one never opens the ⓘ's card.
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
    private fun ready(
        city: String = "Istanbul",
        hijri: String = "29 Rabi’ al-Awwal 1448",
        gregorian: String = "11 October 2026",
    ): TodayUiState.Ready {
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
            cityDisplayName = city,
            hijri = hijri,
            gregorian = gregorian,
            today = TodayState(rows, next, 180.minutes, ringProgress = 0.4f),
            whose = WhoseTimes("authority_diyanet", EntryClass.A),
            qiblaBearingDegrees = 152.0,
            qiblaDistanceKm = 2406.0,
        )
    }

    @Composable
    private fun Screen(
        taps: Taps,
        direction: LayoutDirection,
        width: Dp,
        height: Dp,
        state: TodayUiState.Ready = ready(),
    ) {
        // Not TaqwaTheme: it also installs the system-bar and keep-screen-on effects, which crash
        // the simulator test process. The colours are all the screen reads from it.
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalLayoutDirection provides direction,
        ) {
            Box(Modifier.size(width, height)) {
                TodayScreen(
                    state = state,
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

    // ---- The glyphs share the city's line only; the dates keep the block's whole width ----------

    private val city = "Istanbul"

    /** Nothing but the ⓘ's own card has this text, so its presence says the card is open. */
    private suspend fun ComposeUiTest.cardIsOpen(): Boolean =
        onAllNodesWithText(getString(Res.string.today_about_these_times)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aTapOnTheHorizonOpensTheAdhkarAndNotTheWhoseTimesCard() = runComposeUiTest {
        val taps = Taps()
        setContent { Screen(taps, LayoutDirection.Ltr, upright.first, upright.second) }
        assertFalse(cardIsOpen(), "the card is shut before any tap")

        onNodeWithContentDescription(getString(Res.string.today_open_adhkar)).performClick()
        waitForIdle()

        assertEquals(1, taps.adhkar)
        assertEquals(0, taps.tasbeeh)
        assertFalse(cardIsOpen(), "a tap on the horizon must not open the whose-times card")
    }

    @Test
    fun aTapOnTheMisbahaOpensTheTasbeehAndNotTheWhoseTimesCard() = runComposeUiTest {
        val taps = Taps()
        setContent { Screen(taps, LayoutDirection.Ltr, upright.first, upright.second) }

        onNodeWithContentDescription(getString(Res.string.today_open_tasbeeh)).performClick()
        waitForIdle()

        assertEquals(0, taps.adhkar)
        assertEquals(1, taps.tasbeeh)
        assertFalse(cardIsOpen(), "a tap on the misbaha must not open the whose-times card")
    }

    @Test
    fun aTapOnTheCityOpensTheWhoseTimesCardAndNeitherGlyph() = runComposeUiTest {
        val taps = Taps()
        setContent { Screen(taps, LayoutDirection.Ltr, upright.first, upright.second) }

        onNodeWithText(city).performClick()
        waitForIdle()

        assertTrue(cardIsOpen(), "a tap on the block opens the whose-times card")
        assertEquals(0, taps.adhkar)
        assertEquals(0, taps.tasbeeh)
    }

    @Test
    fun theGlyphsAreTheirOwnTargetsAndNotPartOfTheInfoBlock() = runComposeUiTest {
        setContent { Screen(Taps(), LayoutDirection.Ltr, upright.first, upright.second) }
        val adhkar = getString(Res.string.today_open_adhkar)
        val tasbeeh = getString(Res.string.today_open_tasbeeh)

        onNodeWithContentDescription(adhkar).assertHasClickAction()
        onNodeWithContentDescription(tasbeeh).assertHasClickAction()
        // The block is one merged node (city, dates, the ⓘ's label); neither glyph's description
        // may have been merged into it.
        val spoken = onNodeWithText(city).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertFalse(adhkar in spoken, "the horizon joined the block's label: $spoken")
        assertFalse(tasbeeh in spoken, "the misbaha joined the block's label: $spoken")
    }

    /**
     * Long enough to wrap whatever the font: the city and the dates fill every line they are given,
     * so the width each one ends up with says how much room it was offered. At 360 dp the block is
     * 312 wide: the city may have 312 less the two 44 dp glyphs, the dates all 312.
     */
    private suspend fun ComposeUiTest.datesRunUnderTheGlyphsAndTheCityStopsShort(direction: LayoutDirection) {
        val longCity = "Saint Petersburg Saint Petersburg Saint Petersburg Saint Petersburg"
        val hijri = "29 Rabi’ al-Awwal 1448 29 Rabi’ al-Awwal 1448 29 Rabi’ al-Awwal 1448"
        setContent {
            Screen(Taps(), direction, 360.dp, 760.dp, ready(city = longCity, hijri = hijri))
        }
        val block = onNodeWithText(longCity).getUnclippedBoundsInRoot()
        val title = onNode(hasText(longCity), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dates = onNode(hasText(hijri, substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val horizon = onNodeWithContentDescription(getString(Res.string.today_open_adhkar)).getUnclippedBoundsInRoot()
        val misbaha = onNodeWithContentDescription(getString(Res.string.today_open_tasbeeh)).getUnclippedBoundsInRoot()

        // The whole block is the 312 dp column (360 less the two 24 dp gutters).
        assertEquals(312f, block.width.value, 1f, "block $block")
        // The city leaves the two glyphs' 88 dp; the dates do not.
        assertTrue(title.width.value <= block.width.value - 88f + 1f, "city $title in block $block")
        assertTrue(dates.width.value > block.width.value - 88f + 1f, "dates $dates should use the room under the glyphs, block $block")
        // The dates sit straight under the city's own lines, not under a 44 dp row of glyphs.
        assertEquals(title.bottom.value, dates.top.value, 0.5f, "dates $dates under city $title")
        // The glyphs are the block's end corner, the horizon beside the misbaha.
        when (direction) {
            LayoutDirection.Ltr -> {
                assertEquals(block.right.value, misbaha.right.value, 0.5f)
                assertEquals(misbaha.left.value, horizon.right.value, 0.5f)
                assertTrue(title.right.value <= horizon.left.value + 0.5f)
            }
            LayoutDirection.Rtl -> {
                assertEquals(block.left.value, misbaha.left.value, 0.5f)
                assertEquals(misbaha.right.value, horizon.left.value, 0.5f)
                assertTrue(title.left.value >= horizon.right.value - 0.5f)
            }
        }
    }

    @Test
    fun theDatesKeepTheBlocksWholeWidthAndTheCityStopsShortOfTheGlyphsLeftToRight() = runComposeUiTest {
        datesRunUnderTheGlyphsAndTheCityStopsShort(LayoutDirection.Ltr)
    }

    @Test
    fun theDatesKeepTheBlocksWholeWidthAndTheCityStopsShortOfTheGlyphsRightToLeft() = runComposeUiTest {
        datesRunUnderTheGlyphsAndTheCityStopsShort(LayoutDirection.Rtl)
    }

    @Test
    fun theGlyphsDoNotSetTheBlocksHeight() = runComposeUiTest {
        // The glyphs are 44 dp tall against a city line of about 33; the text sets the height.
        setContent { Screen(Taps(), LayoutDirection.Ltr, upright.first, upright.second) }
        val title = onNode(hasText(city), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dates = onNode(hasText("29 Rabi’ al-Awwal 1448", substring = true), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val block = onNodeWithText(city).getUnclippedBoundsInRoot()
        assertEquals(title.bottom.value, dates.top.value, 0.5f)
        // The two lines of text, or the block's 48 dp target if they are shorter; never the glyphs' 44 dp
        // on top of the dates.
        val text = dates.bottom.value - title.top.value
        assertTrue(abs(block.height.value - maxOf(48f, text)) < 1f, "block $block title $title dates $dates")
    }
}
