package world.taqwa.app.feature.prayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
import world.taqwa.app.resources.prayer_about_these_times
import world.taqwa.app.resources.prayer_open_adhkar
import world.taqwa.app.resources.prayer_open_tasbeeh
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
 * They sit on the city's line and nowhere else: the dates below run the block's whole width and
 * start 2 dp below the glyphs' targets, the glyphs are not part of the ⓘ's target, and a tap on
 * one never opens the ⓘ's card.
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
    ): PrayerUiState.Ready {
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
        return PrayerUiState.Ready(
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
        state: PrayerUiState.Ready = ready(),
        fontScale: Float = 1f,
    ) {
        // Not TaqwaTheme: it also installs the system-bar and keep-screen-on effects, which crash
        // the simulator test process. The colours are all the screen reads from it.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalLayoutDirection provides direction,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            Box(Modifier.size(width, height)) {
                PrayerScreen(
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
        val adhkar = getString(Res.string.prayer_open_adhkar)
        val tasbeeh = getString(Res.string.prayer_open_tasbeeh)

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
        val adhkar = getString(Res.string.prayer_open_adhkar)
        val tasbeeh = getString(Res.string.prayer_open_tasbeeh)
        for (description in listOf(adhkar, tasbeeh)) {
            onNodeWithContentDescription(description).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        }
    }

    /** The horizon comes first in reading order, so the misbaha keeps the end corner. */
    private suspend fun ComposeUiTest.horizonSitsBeforeTheMisbaha(direction: LayoutDirection, size: Pair<Dp, Dp>) {
        setContent { Screen(Taps(), direction, size.first, size.second) }
        val adhkar = getString(Res.string.prayer_open_adhkar)
        val tasbeeh = getString(Res.string.prayer_open_tasbeeh)
        val horizon = onNodeWithContentDescription(adhkar).fetchSemanticsNode().boundsInRoot
        val misbaha = onNodeWithContentDescription(tasbeeh).fetchSemanticsNode().boundsInRoot
        // Side by side on one line, not stacked.
        assertEquals(horizon.top, misbaha.top)
        // On the city's line, lifted by the nudge above its top, upright and sideways alike.
        val cityTop = onNode(hasText(city), useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        val glyphTop = onNodeWithContentDescription(adhkar).getUnclippedBoundsInRoot().top.value
        assertEquals(cityTop - GLYPH_NUDGE, glyphTop, 0.5f, "the glyphs should sit $GLYPH_NUDGE dp above the city's top")
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
        onAllNodesWithText(getString(Res.string.prayer_about_these_times)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aTapOnTheHorizonOpensTheAdhkarAndNotTheWhoseTimesCard() = runComposeUiTest {
        val taps = Taps()
        setContent { Screen(taps, LayoutDirection.Ltr, upright.first, upright.second) }
        assertFalse(cardIsOpen(), "the card is shut before any tap")

        onNodeWithContentDescription(getString(Res.string.prayer_open_adhkar)).performClick()
        waitForIdle()

        assertEquals(1, taps.adhkar)
        assertEquals(0, taps.tasbeeh)
        assertFalse(cardIsOpen(), "a tap on the horizon must not open the whose-times card")
    }

    @Test
    fun aTapOnTheMisbahaOpensTheTasbeehAndNotTheWhoseTimesCard() = runComposeUiTest {
        val taps = Taps()
        setContent { Screen(taps, LayoutDirection.Ltr, upright.first, upright.second) }

        onNodeWithContentDescription(getString(Res.string.prayer_open_tasbeeh)).performClick()
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
        val adhkar = getString(Res.string.prayer_open_adhkar)
        val tasbeeh = getString(Res.string.prayer_open_tasbeeh)

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
        val horizon = onNodeWithContentDescription(getString(Res.string.prayer_open_adhkar)).getUnclippedBoundsInRoot()
        val misbaha = onNodeWithContentDescription(getString(Res.string.prayer_open_tasbeeh)).getUnclippedBoundsInRoot()

        // The whole block is the 312 dp column (360 less the two 24 dp gutters).
        assertEquals(312f, block.width.value, 1f, "block $block")
        // The city leaves the two glyphs' 88 dp; the dates do not.
        assertTrue(title.width.value <= block.width.value - 88f + 1f, "city $title in block $block")
        assertTrue(dates.width.value > block.width.value - 88f + 1f, "dates $dates should use the room under the glyphs, block $block")
        // The dates sit straight under the city's own lines, which wrap here to well below the
        // glyphs' targets, not under a 44 dp row of glyphs.
        val header = header(longCity, hijri)
        assertEquals(header.datesBelong, dates.top.value, 0.5f, "dates $dates under city $header")
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
    fun theGlyphsHoldTheDatesOnlyBelowTheirOwnReach() = runComposeUiTest {
        // The glyphs' targets reach 40 dp down the block (44 dp, lifted 4) against a one-line city
        // of about 29: they hold the dates 2 dp below that reach and add nothing else.
        setContent { Screen(Taps(), LayoutDirection.Ltr, upright.first, upright.second) }
        val title = onNode(hasText(city), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dates = onNode(hasText("29 Rabi’ al-Awwal 1448", substring = true), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val block = onNodeWithText(city).getUnclippedBoundsInRoot()
        val header = header(city, "29 Rabi’ al-Awwal 1448")
        assertEquals(header.datesBelong, dates.top.value, 0.5f, "$header")
        // The city's line and the dates, or the block's 48 dp target if they are shorter; never the
        // glyphs' 44 dp stacked on top of the dates.
        val text = dates.bottom.value - title.top.value
        assertTrue(abs(block.height.value - maxOf(48f, text)) < 1f, "block $block title $title dates $dates")
    }

    // ---- The dates start below the glyphs' targets, and no lower than that needs ---------------

    /**
     * The block's own words as the app shows them under Arabic: the city and both dates in Arabic,
     * Eastern Arabic digits. The rest of the screen keeps the simulator's language.
     */
    private fun arabic(city: String = "إسطنبول") =
        ready(city = city, hijri = "٢٩ ربيع الأول ١٤٤٨", gregorian = "١١ أكتوبر ٢٠٢٦")

    /** Where the header's lines and targets fall, down the root, in dp. */
    private data class Header(
        val cityTop: Float,
        /** The bottom of the city's own lines, above whatever room the line's minimum adds. */
        val cityTextBottom: Float,
        val cityLines: Int,
        val datesTop: Float,
        /** The horizon's and the misbaha's tops. */
        val targetsTops: List<Float>,
        /** The lower of the two glyph targets' bottoms. */
        val targetsBottom: Float,
    ) {
        /** Under the city's own lines, or [DATES_GAP] under the targets where those reach lower. */
        val datesBelong: Float get() = maxOf(cityTextBottom, targetsBottom + DATES_GAP)
    }

    private suspend fun ComposeUiTest.header(city: String, hijri: String): Header {
        // The city's own node is its text and nothing more; the line's minimum lies outside it.
        val cityNode = onNode(hasText(city), useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        cityNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val title = cityNode.getUnclippedBoundsInRoot()
        val dates = onNode(hasText(hijri, substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val horizon = onNodeWithContentDescription(getString(Res.string.prayer_open_adhkar)).getUnclippedBoundsInRoot()
        val misbaha = onNodeWithContentDescription(getString(Res.string.prayer_open_tasbeeh)).getUnclippedBoundsInRoot()
        return Header(
            cityTop = title.top.value,
            cityTextBottom = title.bottom.value,
            cityLines = layouts.single().lineCount,
            datesTop = dates.top.value,
            targetsTops = listOf(horizon.top.value, misbaha.top.value),
            targetsBottom = maxOf(horizon.bottom, misbaha.bottom).value,
        )
    }

    /**
     * The glyphs' targets reach 40 dp down the block (44 dp, lifted 4), deeper than a one-line city
     * (29 dp in English, 30 in Arabic), so dates straight under the city would start inside them,
     * and a tap on the ⓘ that ends their first line, which can fall under either glyph, could open
     * the adhkar or the tasbeeh instead of its card. The dates start 2 dp below both targets, and no
     * lower than that or the city's own lines need: at the default size a one-line city puts them
     * exactly 2 dp below, and at twice the text size the city line clears the targets by itself and
     * gets nothing added. The glyphs keep their place on the city's line throughout.
     *
     * One composition walks every size, so each case is the same screen re-laid out.
     */
    private suspend fun ComposeUiTest.datesStartBelowTheGlyphTargets(direction: LayoutDirection, state: PrayerUiState.Ready) {
        var width by mutableStateOf(360.dp)
        var fontScale by mutableFloatStateOf(1f)
        setContent { Screen(Taps(), direction, width, 760.dp, state, fontScale) }
        val city = state.cityDisplayName!!
        for (scale in listOf(1f, 2f)) {
            for (each in listOf(360.dp, 411.dp)) {
                fontScale = scale
                width = each
                waitForIdle()
                val header = header(city, state.hijri)
                val case = "$direction at $each and font scale $scale: $header"
                assertTrue(
                    header.datesTop >= header.targetsBottom + DATES_GAP - 0.5f,
                    "the dates start inside a glyph's target or within $DATES_GAP dp of it, $case",
                )
                assertEquals(header.datesBelong, header.datesTop, 0.5f, "the dates are not where they belong, $case")
                // Read from the targets and not from the city's bounds, so it holds wherever the
                // line's minimum sits: a one-line city leaves the dates just the gap below them.
                if (scale == 1f) {
                    assertEquals(header.targetsBottom + DATES_GAP, header.datesTop, 0.5f, "the dates sit lower than the gap needs, $case")
                }
                for (top in header.targetsTops) {
                    assertEquals(header.cityTop - GLYPH_NUDGE, top, 0.5f, "a glyph left the city's line, $case")
                }
            }
        }
    }

    @Test
    fun theDatesStartBelowBothGlyphTargetsInEnglish() = runComposeUiTest {
        datesStartBelowTheGlyphTargets(LayoutDirection.Ltr, ready())
    }

    @Test
    fun theDatesStartBelowBothGlyphTargetsInArabic() = runComposeUiTest {
        datesStartBelowTheGlyphTargets(LayoutDirection.Rtl, arabic())
    }

    /** A city long enough to wrap already reaches below the targets: the dates follow it with no gap. */
    private suspend fun ComposeUiTest.aCityThatWrapsIsFollowedByTheDatesDirectly(direction: LayoutDirection, state: PrayerUiState.Ready) {
        setContent { Screen(Taps(), direction, 360.dp, 760.dp, state) }
        val header = header(state.cityDisplayName!!, state.hijri)
        assertTrue(header.cityLines >= 2, "the city should wrap at 360 dp: $header")
        assertTrue(header.cityTextBottom > header.targetsBottom + DATES_GAP, "the wrapped city should reach below the targets: $header")
        assertEquals(header.cityTextBottom, header.datesTop, 0.5f, "the dates should follow the city's lines directly: $header")
    }

    @Test
    fun aCityThatWrapsIsFollowedByTheDatesDirectlyInEnglish() = runComposeUiTest {
        aCityThatWrapsIsFollowedByTheDatesDirectly(
            LayoutDirection.Ltr,
            ready(city = "Saint Petersburg Saint Petersburg Saint Petersburg"),
        )
    }

    @Test
    fun aCityThatWrapsIsFollowedByTheDatesDirectlyInArabic() = runComposeUiTest {
        aCityThatWrapsIsFollowedByTheDatesDirectly(
            LayoutDirection.Rtl,
            arabic(city = "سانت بطرسبرغ سانت بطرسبرغ سانت بطرسبرغ"),
        )
    }

    private companion object {
        /** The air the dates keep below the glyphs' targets. */
        const val DATES_GAP = 2f

        /** How far the glyphs' targets are lifted above the city's top. */
        const val GLYPH_NUDGE = 4f
    }
}
