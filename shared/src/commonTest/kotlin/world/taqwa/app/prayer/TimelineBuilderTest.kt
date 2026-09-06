package world.taqwa.app.prayer

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineBuilderTest {

    /** 2026-09-06 London-ish times expressed as UTC instants, one hour apart for clarity. */
    private fun day(date: LocalDate, startEpoch: Long) = DayPrayerTimes(
        date = date,
        times = listOf(
            PrayerTime(Prayer.FAJR, Instant.fromEpochSeconds(startEpoch)),
            PrayerTime(Prayer.SUNRISE, Instant.fromEpochSeconds(startEpoch + 3600)),
            PrayerTime(Prayer.DHUHR, Instant.fromEpochSeconds(startEpoch + 7200)),
            PrayerTime(Prayer.ASR, Instant.fromEpochSeconds(startEpoch + 10800)),
            PrayerTime(Prayer.MAGHRIB, Instant.fromEpochSeconds(startEpoch + 14400)),
            PrayerTime(Prayer.ISHA, Instant.fromEpochSeconds(startEpoch + 18000)),
        ),
        highLatitudeRuleApplied = null,
    )

    private val today = day(LocalDate(2026, 9, 6), 1_000_000)
    private val tomorrow = day(LocalDate(2026, 9, 7), 1_086_400)

    @Test
    fun sunriseIsHiddenUnlessRequested() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_000_100), false)
        assertEquals(5, s.rows.size)
        assertTrue(s.rows.none { it.prayer == Prayer.SUNRISE })
    }

    @Test
    fun sunriseAppearsWhenRequested() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_000_100), true)
        assertEquals(6, s.rows.size)
    }

    @Test
    fun partitionsIntoPassedCurrentAndUpcoming() {
        // 30 minutes after Asr
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_012_600), false)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.FAJR }.status)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.DHUHR }.status)
        assertEquals(PrayerStatus.CURRENT, s.rows.first { it.prayer == Prayer.ASR }.status)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.MAGHRIB }.status)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.ISHA }.status)
    }

    @Test
    fun exactlyOneRowIsCurrent() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_012_600), false)
        assertEquals(1, s.rows.count { it.status == PrayerStatus.CURRENT })
    }

    @Test
    fun nextIsTheFollowingObligatoryPrayerNotSunrise() {
        // just after Fajr — the next notified prayer is Dhuhr, not Sunrise
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_000_100), false)
        assertEquals(Prayer.DHUHR, s.next.prayer)
    }

    @Test
    fun countdownCountsDownToTheNextPrayer() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_007_200 - 600), false)
        assertEquals(600, s.countdown.inWholeSeconds)
    }

    @Test
    fun afterIshaTheNextPrayerIsTomorrowsFajr() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_018_100), false)
        assertEquals(Prayer.FAJR, s.next.prayer)
        assertEquals(tomorrow.time(Prayer.FAJR), s.next.instant)
    }

    @Test
    fun beforeFajrNothingHasPassedYet() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(999_000), false)
        assertTrue(s.rows.none { it.status == PrayerStatus.PASSED })
        assertEquals(Prayer.FAJR, s.next.prayer)
    }

    @Test
    fun ringProgressRunsFromZeroToOneAcrossTheInterval() {
        // Progress is measured across the Fajr(1_000_000)->Dhuhr(1_007_200) window (7200s).
        // 1_003_600 is 3600s in, i.e. exactly the midpoint (0.5), not a quarter as originally
        // asserted (0.2f..0.4f) — see task-5-report.md for the corrected arithmetic.
        val midway = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_003_600), false)
        assertTrue(midway.ringProgress in 0.4f..0.6f, "was ${midway.ringProgress}")
        val almost = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_007_100), false)
        assertTrue(almost.ringProgress > 0.9f, "was ${almost.ringProgress}")
    }

    @Test
    fun ringProgressIsAlwaysWithinBounds() {
        listOf(999_000L, 1_000_000L, 1_012_600L, 1_018_100L, 1_085_000L).forEach { t ->
            val p = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(t), false).ringProgress
            assertTrue(p in 0f..1f, "progress $p out of bounds at $t")
        }
    }
}
