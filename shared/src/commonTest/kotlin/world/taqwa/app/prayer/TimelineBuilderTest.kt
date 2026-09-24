package world.taqwa.app.prayer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private val yesterday = day(LocalDate(2026, 9, 5), 1_000_000 - 86_400)
    private val today = day(LocalDate(2026, 9, 6), 1_000_000)
    private val tomorrow = day(LocalDate(2026, 9, 7), 1_086_400)

    @Test
    fun sunriseIsHiddenUnlessRequested() {
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_000_100), false, TimeZone.UTC)
        assertEquals(5, s.rows.size)
        assertTrue(s.rows.none { it.prayer == Prayer.SUNRISE })
    }

    @Test
    fun sunriseAppearsWhenRequested() {
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_000_100), true, TimeZone.UTC)
        assertEquals(6, s.rows.size)
    }

    @Test
    fun partitionsIntoPassedCurrentAndUpcoming() {
        // 30 minutes after Asr
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_012_600), false, TimeZone.UTC)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.FAJR }.status)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.DHUHR }.status)
        assertEquals(PrayerStatus.CURRENT, s.rows.first { it.prayer == Prayer.ASR }.status)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.MAGHRIB }.status)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.ISHA }.status)
    }

    @Test
    fun exactlyOneRowIsCurrent() {
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_012_600), false, TimeZone.UTC)
        assertEquals(1, s.rows.count { it.status == PrayerStatus.CURRENT })
    }

    @Test
    fun nextIsTheFollowingObligatoryPrayerNotSunrise() {
        // just after Fajr — the next notified prayer is Dhuhr, not Sunrise
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_000_100), false, TimeZone.UTC)
        assertEquals(Prayer.DHUHR, s.next.prayer)
    }

    @Test
    fun countdownCountsDownToTheNextPrayer() {
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_007_200 - 600), false, TimeZone.UTC)
        assertEquals(600, s.countdown.inWholeSeconds)
    }

    @Test
    fun afterIshaTheNextPrayerIsTomorrowsFajr() {
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_018_100), false, TimeZone.UTC)
        assertEquals(Prayer.FAJR, s.next.prayer)
        assertEquals(tomorrow.time(Prayer.FAJR), s.next.instant)
    }

    @Test
    fun beforeFajrNothingHasPassedYet() {
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(999_000), false, TimeZone.UTC)
        assertTrue(s.rows.none { it.status == PrayerStatus.PASSED })
        assertEquals(Prayer.FAJR, s.next.prayer)
    }

    @Test
    fun ringProgressRunsFromZeroToOneAcrossTheInterval() {
        // Progress is measured across the Fajr(1_000_000)->Dhuhr(1_007_200) window (7200s).
        // 1_003_600 is 3600s in, i.e. exactly the midpoint (0.5), not a quarter as originally
        // asserted (0.2f..0.4f) — see task-5-report.md for the corrected arithmetic.
        val midway = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_003_600), false, TimeZone.UTC)
        assertTrue(midway.ringProgress in 0.4f..0.6f, "was ${midway.ringProgress}")
        val almost = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(1_007_100), false, TimeZone.UTC)
        assertTrue(almost.ringProgress > 0.9f, "was ${almost.ringProgress}")
    }

    @Test
    fun ringProgressAdvancesBetweenMidnightAndFajr() {
        // Yesterday's Isha is at 1_000_000 - 86_400 + 18_000 = 931_600; today's Fajr is at
        // 1_000_000. 999_000 sits deep inside that interval, so the ring must be part-filled and
        // moving rather than sitting at 0f for the whole night.
        val s = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(999_000), false, TimeZone.UTC)
        assertTrue(s.ringProgress > 0f, "was ${s.ringProgress}")
        assertTrue(s.ringProgress < 1f, "was ${s.ringProgress}")
        val later = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(999_600), false, TimeZone.UTC)
        assertTrue(later.ringProgress > s.ringProgress, "ring did not advance")
    }

    @Test
    fun ringProgressIsAlwaysWithinBounds() {
        listOf(999_000L, 1_000_000L, 1_012_600L, 1_018_100L, 1_085_000L).forEach { t ->
            val p = TimelineBuilder.build(yesterday, today, tomorrow, Instant.fromEpochSeconds(t), false, TimeZone.UTC).ringProgress
            assertTrue(p in 0f..1f, "progress $p out of bounds at $t")
        }
    }

    // Jumuʿah. 25 September 2026 is a Friday. Tripoli keeps UTC+2 all year, Jakarta UTC+7, and
    // New York is on UTC−4 in September.
    private val tripoli = TimeZone.of("Africa/Tripoli")
    private val jakarta = TimeZone.of("Asia/Jakarta")

    @Test
    fun `Dhuhr on a Friday is Jumuah`() {
        // 12:55 on Friday in Tripoli.
        assertTrue(TimelineBuilder.isJumuah(Prayer.DHUHR, Instant.parse("2026-09-25T10:55:00Z"), tripoli))
    }

    @Test
    fun `Asr on a Friday is not Jumuah`() {
        // 16:20 on Friday in Tripoli.
        assertFalse(TimelineBuilder.isJumuah(Prayer.ASR, Instant.parse("2026-09-25T14:20:00Z"), tripoli))
    }

    @Test
    fun `Dhuhr on a Thursday is not Jumuah`() {
        // 12:55 on Thursday in Tripoli.
        assertFalse(TimelineBuilder.isJumuah(Prayer.DHUHR, Instant.parse("2026-09-24T10:55:00Z"), tripoli))
    }

    @Test
    fun `Friday is the location's own while UTC is still on Thursday`() {
        // 18:30 UTC on Thursday is 01:30 on Friday in Jakarta. No Dhuhr falls at that hour: the
        // instant is picked because the two clocks disagree about the day there, and the rule
        // reads the day.
        assertTrue(TimelineBuilder.isJumuah(Prayer.DHUHR, Instant.parse("2026-09-24T18:30:00Z"), jakarta))
    }

    @Test
    fun `Thursday is the location's own once UTC has reached Friday`() {
        // 03:00 UTC on Friday is 23:00 on Thursday in New York. With the Jakarta case above, a
        // rule that read the device's zone instead fails one of the two on any machine.
        assertFalse(
            TimelineBuilder.isJumuah(Prayer.DHUHR, Instant.parse("2026-09-25T03:00:00Z"), TimeZone.of("America/New_York")),
        )
    }

    @Test
    fun `only the Dhuhr row of a Friday timeline carries the Jumuah flag`() {
        // An hour apart like the fixtures above, from 04:00 on Friday in Jakarta: Dhuhr at 06:00
        // there is still 23:00 on Thursday in UTC, so the flag has to come from the zone passed in.
        val fajr = Instant.parse("2026-09-24T21:00:00Z").epochSeconds
        val s = TimelineBuilder.build(
            yesterday = day(LocalDate(2026, 9, 24), fajr - 86_400),
            today = day(LocalDate(2026, 9, 25), fajr),
            tomorrow = day(LocalDate(2026, 9, 26), fajr + 86_400),
            now = Instant.fromEpochSeconds(fajr + 100),
            showSunrise = true,
            zone = jakarta,
        )
        assertEquals(6, s.rows.size)
        assertEquals(listOf(Prayer.DHUHR), s.rows.filter { it.isJumuah }.map { it.prayer })
    }
}
