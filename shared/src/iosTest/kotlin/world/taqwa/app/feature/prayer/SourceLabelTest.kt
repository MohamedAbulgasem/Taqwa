package world.taqwa.app.feature.prayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import world.taqwa.app.i18n.isolated
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_about_a11y
import world.taqwa.app.resources.prayer_fajr
import world.taqwa.app.resources.prayer_isha
import world.taqwa.app.resources.qibla_title
import world.taqwa.app.resources.prayer_source_published_method
import world.taqwa.app.resources.timetable_calculated
import world.taqwa.app.resources.timetable_cautious
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the times are not an authority's own checked timetable, a label set into the times card's
 * top hairline says where they come from: "Cautious times" (several timetables combined), "Published
 * method" (an authority's method not yet proven against its timetable), "Calculated by Taqwa"
 * (nothing official known). It takes no line of its own, so the Qibla card keeps its place: it sits
 * lower than for any other city only by the air the label is given above Fajr. The label's words
 * start where the prayer names start, in either direction; it is one button that opens About these
 * times. When it cannot fit on that edge (a large text size on a narrow phone), it becomes a line
 * under the list, as the cautious line always was.
 *
 * Expected words come from the resources, not from literals: the simulator's language decides
 * every word on screen.
 */
@OptIn(ExperimentalTestApi::class)
class SourceLabelTest {

    private class Taps {
        var about = 0
    }

    private val city = "Cape Town"

    /** A ready state with fixed instants: nothing here depends on the engine or on today's date. */
    private fun ready(entryClass: EntryClass): PrayerUiState.Ready {
        val fajr = Instant.parse("2026-10-22T02:36:00Z")
        val times = listOf(
            Prayer.FAJR to fajr,
            Prayer.DHUHR to fajr + 480.minutes,
            Prayer.ASR to fajr + 695.minutes,
            Prayer.MAGHRIB to fajr + 873.minutes,
            Prayer.ISHA to fajr + 953.minutes,
        )
        val rows = times.map { (prayer, instant) ->
            TimelineRow(prayer, instant, if (prayer == Prayer.FAJR) PrayerStatus.PASSED else PrayerStatus.UPCOMING)
        }
        val next = PrayerTime(Prayer.DHUHR, fajr + 480.minutes)
        val whose = when (entryClass) {
            EntryClass.A, EntryClass.B -> WhoseTimes("authority_diyanet", entryClass)
            EntryClass.C -> WhoseTimes("timetable_cautious", entryClass)
            EntryClass.D_AUTHORITY -> WhoseTimes("authority_isna", entryClass)
            EntryClass.D_NONE -> WhoseTimes("timetable_calculated", entryClass)
        }
        return PrayerUiState.Ready(
            location = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", city, "ZA"),
            cityDisplayName = city,
            hijri = "10 Jumada al-Ula 1448",
            gregorian = "22 October 2026",
            today = TodayState(rows, next, 150.minutes, ringProgress = 0.69f),
            whose = whose,
            qiblaBearingDegrees = 23.0,
            qiblaDistanceKm = 6559.0,
        )
    }

    @Composable
    private fun Screen(
        taps: Taps,
        entryClass: EntryClass,
        direction: LayoutDirection = LayoutDirection.Ltr,
        width: Dp = 384.dp,
        height: Dp = 760.dp,
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
                    state = ready(entryClass),
                    onChooseCity = {},
                    onOpenLocationSettings = {},
                    onOpenQibla = {},
                    onOpenAdhkar = {},
                    onOpenTasbeeh = {},
                    onOpenAboutTimes = { taps.about++ },
                )
            }
        }
    }

    private val sources = listOf(
        Res.string.timetable_cautious,
        Res.string.prayer_source_published_method,
        Res.string.timetable_calculated,
    )

    /** What the label (or its line) is read as: "Cautious times, Cape Town. Opens About these times". */
    private suspend fun spoken(words: StringResource): String =
        getString(Res.string.prayer_about_a11y, getString(words), isolated(city))

    /**
     * A prayer's row: one node whose label opens with the prayer's name (the row is spoken as one
     * stop), as wide as the timeline inside the card's 16 dp, so its names start
     * [TimelineNameInset] in from its start edge.
     */
    private suspend fun ComposeUiTest.row(prayer: StringResource): DpRect =
        onAllNodesWithContentDescription(getString(prayer), substring = true).fetchSemanticsNodes().first().let { node ->
            val r: Rect = node.boundsInRoot
            with(density) { DpRect(r.left.toDp(), r.top.toDp(), r.right.toDp(), r.bottom.toDp()) }
        }

    /** The Qibla card is one clickable row that fills the card, so its top is the card's. */
    private suspend fun ComposeUiTest.qiblaTop(): Dp =
        onNodeWithText(getString(Res.string.qibla_title)).getUnclippedBoundsInRoot().top

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((expected - actual).value) <= 0.5f, "$what: expected $expected, was $actual")

    @Test
    fun theQiblaCardSitsLowerOnlyByTheLabelsAir() = runComposeUiTest {
        var entryClass by mutableStateOf(EntryClass.A)
        setContent { Screen(Taps(), entryClass) }
        val plain = qiblaTop()
        entryClass = EntryClass.C
        waitForIdle()
        near(plain + SourceLabelAir, qiblaTop(), "the Qibla card's top where the times are cautious")
    }

    /** One label, its own words, on the card's top edge above Fajr and not as a line under Isha. */
    private suspend fun ComposeUiTest.labelSitsOnTheEdge(entryClass: EntryClass, words: StringResource) {
        setContent { Screen(Taps(), entryClass) }
        for (other in sources - words) onAllNodesWithContentDescription(spoken(other)).assertCountEquals(0)
        onAllNodesWithContentDescription(spoken(words)).assertCountEquals(1)
        val label = onNodeWithContentDescription(spoken(words)).getUnclippedBoundsInRoot()
        val fajr = row(Res.string.prayer_fajr)
        val isha = row(Res.string.prayer_isha)
        val centre = (label.top + label.bottom) / 2
        assertTrue(centre < fajr.top, "the label's centre ($centre) is above Fajr (${fajr.top})")
        assertTrue(label.bottom < isha.top, "the label is on the edge, not a line under Isha")
    }

    @Test
    fun cautiousTimesAreLabelledOnTheEdge() = runComposeUiTest {
        labelSitsOnTheEdge(EntryClass.C, Res.string.timetable_cautious)
    }

    @Test
    fun anAuthoritysMethodNotYetProvenIsLabelledPublishedMethod() = runComposeUiTest {
        labelSitsOnTheEdge(EntryClass.D_AUTHORITY, Res.string.prayer_source_published_method)
    }

    @Test
    fun timesWithNothingOfficialKnownAreLabelledCalculatedByTaqwa() = runComposeUiTest {
        labelSitsOnTheEdge(EntryClass.D_NONE, Res.string.timetable_calculated)
    }

    private suspend fun ComposeUiTest.noLabel(entryClass: EntryClass) {
        setContent { Screen(Taps(), entryClass) }
        for (words in sources) onAllNodesWithContentDescription(spoken(words)).assertCountEquals(0)
    }

    @Test
    fun anAuthoritysCheckedTimetableHasNoLabel() = runComposeUiTest { noLabel(EntryClass.A) }

    @Test
    fun anAuthoritysCheckedMethodHasNoLabel() = runComposeUiTest { noLabel(EntryClass.B) }

    @Test
    fun theLabelsWordsStartWhereThePrayerNamesStartLeftToRight() = runComposeUiTest {
        setContent { Screen(Taps(), EntryClass.D_NONE, direction = LayoutDirection.Ltr) }
        val label = onNodeWithContentDescription(spoken(Res.string.timetable_calculated)).getUnclippedBoundsInRoot()
        val fajr = row(Res.string.prayer_fajr)
        near(fajr.left + TimelineNameInset - SourceLabelPad, label.left, "the label's start")
    }

    @Test
    fun theLabelsWordsStartWhereThePrayerNamesStartRightToLeft() = runComposeUiTest {
        setContent { Screen(Taps(), EntryClass.C, direction = LayoutDirection.Rtl) }
        val label = onNodeWithContentDescription(spoken(Res.string.timetable_cautious)).getUnclippedBoundsInRoot()
        val fajr = row(Res.string.prayer_fajr)
        near(fajr.right - TimelineNameInset + SourceLabelPad, label.right, "the label's start under right-to-left")
    }

    @Test
    fun theLabelIsAButtonThatOpensAboutTheseTimes() = runComposeUiTest {
        val taps = Taps()
        setContent { Screen(taps, EntryClass.D_AUTHORITY) }
        onNodeWithContentDescription(spoken(Res.string.prayer_source_published_method))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .performClick()
        assertEquals(1, taps.about)
    }

    @Test
    fun whenTheLabelCannotFitItBecomesALineUnderTheList() = runComposeUiTest {
        // Double text on a 300 dp phone: no language's label fits the edge of a 252 dp card.
        var entryClass by mutableStateOf(EntryClass.A)
        setContent { Screen(Taps(), entryClass, width = 300.dp, fontScale = 2f) }
        val plain = qiblaTop()
        entryClass = EntryClass.C
        waitForIdle()
        onAllNodesWithContentDescription(spoken(Res.string.timetable_cautious)).assertCountEquals(1)
        val line = onNodeWithContentDescription(spoken(Res.string.timetable_cautious)).getUnclippedBoundsInRoot()
        val isha = row(Res.string.prayer_isha)
        assertTrue(line.top > isha.bottom, "the fallback is the line under the list")
        // The line's own 48 dp target and 2 dp either side, in place of the 14 dp gap.
        near(plain + 38.dp, qiblaTop(), "the Qibla card's top with the line back")
    }
}
