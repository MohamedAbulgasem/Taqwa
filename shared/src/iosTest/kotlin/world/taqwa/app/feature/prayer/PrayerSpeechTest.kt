package world.taqwa.app.feature.prayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PrayerNaming
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.i18n.isolatedClock
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.a11y_hours
import world.taqwa.app.resources.a11y_minutes
import world.taqwa.app.resources.duration_hours_minutes
import world.taqwa.app.resources.prayer_a11y_countdown
import world.taqwa.app.resources.prayer_a11y_countdown_soon
import world.taqwa.app.resources.prayer_a11y_prayer_current
import world.taqwa.app.resources.prayer_a11y_prayer_next
import world.taqwa.app.resources.prayer_a11y_prayer_passed
import world.taqwa.app.resources.prayer_asr
import world.taqwa.app.resources.prayer_dhuhr
import world.taqwa.app.resources.prayer_fajr
import world.taqwa.app.resources.prayer_isha
import world.taqwa.app.resources.prayer_jumuah
import world.taqwa.app.resources.prayer_maghrib
import world.taqwa.app.resources.prayer_set_by_rule
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What a screen reader hears on the Prayer screen: each prayer is one node, its name, any pill and
 * its time, with whether it has passed, is current or is next as its state; and the countdown ring
 * is one sentence instead of its three fragments. The screen is composed under the 12-hour clock,
 * the format whose times differ most from a fresh 24-hour one, so every time expected here is the
 * one the screen draws, from the same formatter.
 *
 * Expected words come from the resources and the platform format, not from literals: the
 * simulator's language decides them.
 */
@OptIn(ExperimentalTestApi::class)
class PrayerSpeechTest {

    private val zone = TimeZone.of("Europe/Istanbul")
    private val format = createPlatformFormat(ClockFormat.TWELVE_HOUR)

    private val fajr = Instant.parse("2026-10-16T02:45:00Z")
    private val dhuhr = fajr + 480.minutes
    private val asr = fajr + 660.minutes
    private val maghrib = fajr + 900.minutes
    private val isha = fajr + 990.minutes

    /** Fajr has passed, Friday's Dhuhr is current, Asr is next, and a rule set Isha. */
    private fun ready(countdown: Duration): PrayerUiState.Ready {
        val rows = listOf(
            TimelineRow(Prayer.FAJR, fajr, PrayerStatus.PASSED),
            TimelineRow(Prayer.DHUHR, dhuhr, PrayerStatus.CURRENT, isJumuah = true),
            TimelineRow(Prayer.ASR, asr, PrayerStatus.UPCOMING),
            TimelineRow(Prayer.MAGHRIB, maghrib, PrayerStatus.UPCOMING),
            TimelineRow(Prayer.ISHA, isha, PrayerStatus.UPCOMING),
        )
        return PrayerUiState.Ready(
            location = GeoLocation(41.0082, 28.9784, "Europe/Istanbul", "Istanbul", "TR"),
            cityDisplayName = "Istanbul",
            hijri = "4 Rabi’ al-Akhir 1448",
            gregorian = "16 October 2026",
            today = TodayState(rows, PrayerTime(Prayer.ASR, asr), countdown, ringProgress = 0.4f),
            whose = WhoseTimes("authority_diyanet", EntryClass.A),
            setByRule = setOf(Prayer.ISHA),
            qiblaBearingDegrees = 152.0,
            qiblaDistanceKm = 2406.0,
        )
    }

    @Composable
    private fun Screen(state: PrayerUiState.Ready) {
        // Not TaqwaTheme: it also installs the system-bar and keep-screen-on effects, which crash
        // the simulator test process. The colours are all the screen reads from it.
        CompositionLocalProvider(
            LocalTaqwaColors provides LightColors,
            LocalPlatformFormat provides format,
        ) {
            Box(Modifier.size(411.dp, 760.dp)) {
                PrayerScreen(
                    state = state,
                    onChooseCity = {},
                    onOpenLocationSettings = {},
                    onOpenQibla = {},
                    onOpenAdhkar = {},
                    onOpenTasbeeh = {},
                )
            }
        }
    }

    private fun clock(instant: Instant) = formatClock(instant, zone, format)

    private fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private val noState = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    @Test
    fun eachPrayerIsOneNodeThatSaysItsNamePillsTimeAndState() = runComposeUiTest {
        setContent { Screen(ready(3.hours)) }
        val passed = getString(Res.string.prayer_a11y_prayer_passed)
        val current = getString(Res.string.prayer_a11y_prayer_current)
        val next = getString(Res.string.prayer_a11y_prayer_next)
        val jumuah = getString(Res.string.prayer_jumuah)
        val setByRule = getString(Res.string.prayer_set_by_rule)

        val expected = listOf(
            "${getString(Res.string.prayer_fajr)}, ${clock(fajr)}" to state(passed),
            "${getString(Res.string.prayer_dhuhr)}, $jumuah, ${clock(dhuhr)}" to state(current),
            "${getString(Res.string.prayer_asr)}, ${clock(asr)}" to state(next),
            "${getString(Res.string.prayer_maghrib)}, ${clock(maghrib)}" to noState,
            "${getString(Res.string.prayer_isha)}, $setByRule, ${clock(isha)}" to noState,
        )
        for ((description, stateMatcher) in expected) {
            onAllNodesWithContentDescription(description).assertCountEquals(1)
            onNodeWithContentDescription(description).assert(stateMatcher)
        }
        // The name, the Arabic name, the pills and the time are no longer stops of their own. Read
        // from the merged tree, the one a screen reader walks: the unmerged tree keeps the texts a
        // clearAndSetSemantics replaced.
        for (prayer in listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)) {
            onAllNodesWithText(PrayerNaming.arabicName(prayer), substring = true).assertCountEquals(0)
        }
        for (time in listOf(fajr, dhuhr, asr, maghrib, isha)) {
            onAllNodesWithText(clock(time), substring = true).assertCountEquals(0)
        }
        onAllNodesWithText(jumuah, substring = true).assertCountEquals(0)
        onAllNodesWithText(setByRule, substring = true).assertCountEquals(0)
    }

    /** Only the countdown's own prayer is next: after Isha it counts to tomorrow's Fajr, which has passed today. */
    @Test
    fun aPassedRowIsNeverNextEvenWhenItsPrayerIsTheCountdowns() = runComposeUiTest {
        val afterIsha = ready(5.hours).let { s ->
            s.copy(today = s.today.copy(next = PrayerTime(Prayer.FAJR, fajr + 24.hours)))
        }
        setContent { Screen(afterIsha) }
        val fajrRow = "${getString(Res.string.prayer_fajr)}, ${clock(fajr)}"
        onNodeWithContentDescription(fajrRow).assert(state(getString(Res.string.prayer_a11y_prayer_passed)))
        onNodeWithContentDescription("${getString(Res.string.prayer_asr)}, ${clock(asr)}").assert(noState)
    }

    /**
     * In a high-latitude summer yesterday's Isha can start after midnight and before today's Fajr
     * (TimelineBuilder.build, the Tromsø nights): the ring counts to it, but it is no row of
     * today's, and today's own Isha row, still upcoming, is a different start. No row is next.
     */
    @Test
    fun yesterdaysIshaAfterMidnightMakesNoRowNext() = runComposeUiTest {
        val yesterdaysIsha = fajr - 40.minutes
        val rows = listOf(
            Prayer.FAJR to fajr,
            Prayer.DHUHR to dhuhr,
            Prayer.ASR to asr,
            Prayer.MAGHRIB to maghrib,
            Prayer.ISHA to isha,
        ).map { (prayer, instant) -> TimelineRow(prayer, instant, PrayerStatus.UPCOMING) }
        val beforeFajr = ready(10.minutes).let { s ->
            s.copy(
                today = s.today.copy(rows = rows, next = PrayerTime(Prayer.ISHA, yesterdaysIsha)),
                setByRule = emptySet(),
            )
        }
        setContent { Screen(beforeFajr) }
        onNodeWithContentDescription("${getString(Res.string.prayer_isha)}, ${clock(isha)}").assert(noState)
        onAllNodes(state(getString(Res.string.prayer_a11y_prayer_next))).assertCountEquals(0)
    }

    private suspend fun hours(n: Int) = getPluralString(Res.plurals.a11y_hours, n, format.localizedDigits(n))

    private suspend fun minutes(n: Int) = getPluralString(Res.plurals.a11y_minutes, n, format.localizedDigits(n))

    private fun ComposeUiTest.ringSays(countdown: Duration, expected: String) {
        setContent { Screen(ready(countdown)) }
        onAllNodesWithContentDescription(expected).assertCountEquals(1)
    }

    @Test
    fun theRingIsOneSentenceInHoursAndMinutesAtTheDrawnClockTime() = runComposeUiTest {
        val name = getString(Res.string.prayer_asr)
        // The 12-hour time the ring draws under the countdown, never a 24-hour one.
        assertNotEquals(createPlatformFormat(ClockFormat.TWENTY_FOUR_HOUR).clockTime(16, 45), clock(asr))
        val duration = getString(Res.string.duration_hours_minutes, hours(4), minutes(10))
        ringSays(
            4.hours + 10.minutes + 2.seconds,
            getString(Res.string.prayer_a11y_countdown, name, duration, isolatedClock(clock(asr))),
        )
    }

    @Test
    fun theRingSaysWholeHoursAloneAndMinutesAlone() = runComposeUiTest {
        val name = getString(Res.string.prayer_asr)
        val at = isolatedClock(clock(asr))
        var countdown by mutableStateOf(1.hours + 30.seconds)
        setContent { Screen(ready(countdown)) }
        onAllNodesWithContentDescription(getString(Res.string.prayer_a11y_countdown, name, hours(1), at))
            .assertCountEquals(1)
        countdown = 5.minutes + 59.seconds
        waitForIdle()
        onAllNodesWithContentDescription(getString(Res.string.prayer_a11y_countdown, name, minutes(5), at))
            .assertCountEquals(1)
    }

    @Test
    fun underAMinuteTheRingSaysSo() = runComposeUiTest {
        val name = getString(Res.string.prayer_asr)
        ringSays(30.seconds, getString(Res.string.prayer_a11y_countdown_soon, name, isolatedClock(clock(asr))))
    }
}
