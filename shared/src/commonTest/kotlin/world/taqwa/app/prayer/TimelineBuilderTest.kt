package world.taqwa.app.prayer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TodayState
import world.taqwa.app.prayer.engine.registry.EntryClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TimelineBuilderTest {

    private val engine = PrayerTimesEngine()

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
        asrOther = Instant.fromEpochSeconds(startEpoch + 12600),
        sunset = Instant.fromEpochSeconds(startEpoch + 14340),
        endOfEating = Instant.fromEpochSeconds(startEpoch),
        sourceEntryId = "test",
        entryClass = EntryClass.D_NONE,
    )

    private val yesterday = day(LocalDate(2026, 9, 5), 1_000_000 - 86_400)
    private val today = day(LocalDate(2026, 9, 6), 1_000_000)
    private val tomorrow = day(LocalDate(2026, 9, 7), 1_086_400)

    /** A timeline builder: the Prayer screen's, or the widgets' (ruling R8). */
    private fun interface Builder {
        fun build(
            yesterday: DayPrayerTimes,
            today: DayPrayerTimes,
            tomorrow: DayPrayerTimes,
            now: Instant,
            showSunrise: Boolean,
            zone: TimeZone,
        ): TodayState
    }

    /**
     * The cases below hold for both builders: on a day without ends they agree, so these pin the
     * widgets' rule (ruling R8) as well as the screen's. Each assertion names the builder it failed on.
     */
    private val builders = listOf(
        "screen" to Builder(TimelineBuilder::build),
        "widgets" to Builder(TimelineBuilder::buildForWidgets),
    )

    private fun forBoth(case: (name: String, Builder) -> Unit) = builders.forEach { (name, b) -> case(name, b) }

    private fun Builder.at(t: Long, showSunrise: Boolean = false) =
        build(yesterday, today, tomorrow, Instant.fromEpochSeconds(t), showSunrise, TimeZone.UTC)

    @Test
    fun sunriseIsHiddenUnlessRequested() = forBoth { name, b ->
        val s = b.at(1_000_100)
        assertEquals(5, s.rows.size, name)
        assertTrue(s.rows.none { it.prayer == Prayer.SUNRISE }, name)
    }

    @Test
    fun sunriseAppearsWhenRequested() = forBoth { name, b ->
        assertEquals(6, b.at(1_000_100, showSunrise = true).rows.size, name)
    }

    @Test
    fun partitionsIntoPassedCurrentAndUpcoming() = forBoth { name, b ->
        // 30 minutes after Asr
        val s = b.at(1_012_600)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.FAJR }.status, name)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.DHUHR }.status, name)
        assertEquals(PrayerStatus.CURRENT, s.rows.first { it.prayer == Prayer.ASR }.status, name)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.MAGHRIB }.status, name)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.ISHA }.status, name)
    }

    @Test
    fun exactlyOneRowIsCurrent() = forBoth { name, b ->
        assertEquals(1, b.at(1_012_600).rows.count { it.status == PrayerStatus.CURRENT }, name)
    }

    @Test
    fun nextIsTheFollowingObligatoryPrayerNotSunrise() = forBoth { name, b ->
        // just after Fajr — the next notified prayer is Dhuhr, not Sunrise
        assertEquals(Prayer.DHUHR, b.at(1_000_100).next.prayer, name)
    }

    @Test
    fun countdownCountsDownToTheNextPrayer() = forBoth { name, b ->
        assertEquals(600, b.at(1_007_200 - 600).countdown.inWholeSeconds, name)
    }

    @Test
    fun afterIshaTheNextPrayerIsTomorrowsFajr() = forBoth { name, b ->
        // An ordinary night: the R72 fallback below still lands on tomorrow's Fajr here, since it
        // is already the earliest of tomorrow's five that is still ahead of `now`.
        val s = b.at(1_018_100)
        assertEquals(Prayer.FAJR, s.next.prayer, name)
        assertEquals(tomorrow.time(Prayer.FAJR), s.next.instant, name)
        assertTrue(s.countdown.isPositive(), name)
    }

    // -- Never a passed prayer as next (follow-up to R83) --------------------------------------
    //
    // Track C's report found that once `now` is past a night's Maghrib and Isha, the naive
    // fallback to "tomorrow's Fajr" can itself already be behind `now` on the nights where a
    // combined polar Maghrib/Isha reaches or passes the next day's Fajr (R72): a passed prayer
    // shown as next, with a negative countdown.

    @Test
    fun onAnR72NightNextIsNeverAPassedInstant() = forBoth { name, b ->
        // The shape: tonight's Maghrib and Isha at or past tomorrow's Fajr, so that two minutes
        // after Maghrib that Fajr is already behind `now`. The engine gave it at Longyearbyen on
        // 2026-05-19 until ruling R90 (the repair had pulled the polar Fajr before the borrowed
        // sunrise); no real night has it since, so it is built from the real Longyearbyen days
        // with tomorrow's Fajr moved to the minute before `now`. The builder must still never hand
        // back a passed instant as next.
        val longyearbyen = GeoLocation(78.2232, 15.6267, "Europe/Oslo", "Longyearbyen", "NO")
        val settings = PrayerSettings()
        val zone = TimeZone.of(longyearbyen.timeZoneId)
        val yesterdayTimes = engine.timesFor(longyearbyen, LocalDate(2026, 5, 18), settings)
        val todayTimes = engine.timesFor(longyearbyen, LocalDate(2026, 5, 19), settings)
        val realTomorrow = engine.timesFor(longyearbyen, LocalDate(2026, 5, 20), settings)
        val now = todayTimes.time(Prayer.MAGHRIB) + 2.minutes
        assertTrue(realTomorrow.time(Prayer.FAJR) > now, "$name: the real engine gives this shape again; use its own day")
        val tomorrowTimes = realTomorrow.copy(
            times = realTomorrow.times.map { if (it.prayer == Prayer.FAJR) PrayerTime(Prayer.FAJR, now - 1.minutes) else it },
        )
        // Precondition: without this, the R72 shape this test targets is not actually present.
        assertTrue(
            tomorrowTimes.time(Prayer.FAJR) <= now,
            "$name: tomorrow's Fajr ${tomorrowTimes.time(Prayer.FAJR)} is not already behind now $now",
        )

        val s = b.build(yesterdayTimes, todayTimes, tomorrowTimes, now, false, zone)
        assertTrue(s.next.instant > now, "$name: next ${s.next.instant} is not after now $now")
        assertTrue(s.countdown.isPositive(), "$name: countdown ${s.countdown} is not positive")
    }

    @Test
    fun yesterdaysIshaAfterMidnightButBeforeTodaysFajrIsStillShownAsNext() = forBoth { name, b ->
        // A fabricated night, not the class's shared fixtures: yesterday's Isha falls only ten
        // minutes before today's Fajr — the shape of a high-latitude night where even Isha, not
        // just Maghrib, is carried past local midnight and still counts as next while it is ahead
        // (spec §3.3). Must keep working unchanged now that the R72 fallback exists alongside it.
        val fajr = 5_000_000L
        val customYesterday = day(LocalDate(2026, 1, 1), fajr - 5 * 3_600 - 600)
        val customToday = day(LocalDate(2026, 1, 2), fajr)
        val customTomorrow = day(LocalDate(2026, 1, 3), fajr + 86_400)
        val now = Instant.fromEpochSeconds(fajr - 700)

        val s = b.build(customYesterday, customToday, customTomorrow, now, false, TimeZone.UTC)
        assertEquals(Prayer.ISHA, s.next.prayer, name)
        assertEquals(customYesterday.time(Prayer.ISHA), s.next.instant, name)
        assertTrue(s.countdown.isPositive(), name)
    }

    @Test
    fun beforeFajrNothingHasPassedYet() = forBoth { name, b ->
        val s = b.at(999_000)
        assertTrue(s.rows.none { it.status == PrayerStatus.PASSED }, name)
        assertEquals(Prayer.FAJR, s.next.prayer, name)
    }

    @Test
    fun ringProgressRunsFromZeroToOneAcrossTheInterval() = forBoth { name, b ->
        // Progress is measured across the Fajr(1_000_000)->Dhuhr(1_007_200) window (7200s).
        // 1_003_600 is 3600s in, i.e. exactly the midpoint (0.5), not a quarter as originally
        // asserted (0.2f..0.4f) — see task-5-report.md for the corrected arithmetic.
        val midway = b.at(1_003_600)
        assertTrue(midway.ringProgress in 0.4f..0.6f, "$name: was ${midway.ringProgress}")
        val almost = b.at(1_007_100)
        assertTrue(almost.ringProgress > 0.9f, "$name: was ${almost.ringProgress}")
    }

    @Test
    fun ringProgressAdvancesBetweenMidnightAndFajr() = forBoth { name, b ->
        // Yesterday's Isha is at 1_000_000 - 86_400 + 18_000 = 931_600; today's Fajr is at
        // 1_000_000. 999_000 sits deep inside that interval, so the ring must be part-filled and
        // moving rather than sitting at 0f for the whole night.
        val s = b.at(999_000)
        assertTrue(s.ringProgress > 0f, "$name: was ${s.ringProgress}")
        assertTrue(s.ringProgress < 1f, "$name: was ${s.ringProgress}")
        val later = b.at(999_600)
        assertTrue(later.ringProgress > s.ringProgress, "$name: ring did not advance")
    }

    @Test
    fun ringProgressIsAlwaysWithinBounds() = forBoth { name, b ->
        listOf(999_000L, 1_000_000L, 1_012_600L, 1_018_100L, 1_085_000L).forEach { t ->
            val p = b.at(t).ringProgress
            assertTrue(p in 0f..1f, "$name: progress $p out of bounds at $t")
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
    fun `only the Dhuhr row of a Friday timeline carries the Jumuah flag`() = forBoth { name, b ->
        // An hour apart like the fixtures above, from 04:00 on Friday in Jakarta: Dhuhr at 06:00
        // there is still 23:00 on Thursday in UTC, so the flag has to come from the zone passed in.
        val fajr = Instant.parse("2026-09-24T21:00:00Z").epochSeconds
        val s = b.build(
            yesterday = day(LocalDate(2026, 9, 24), fajr - 86_400),
            today = day(LocalDate(2026, 9, 25), fajr),
            tomorrow = day(LocalDate(2026, 9, 26), fajr + 86_400),
            now = Instant.fromEpochSeconds(fajr + 100),
            showSunrise = true,
            zone = jakarta,
        )
        assertEquals(6, s.rows.size, name)
        assertEquals(listOf(Prayer.DHUHR), s.rows.filter { it.isJumuah }.map { it.prayer }, name)
    }

    // -- The Prayer screen's highlight (spec §2.1, decision §10.7) ------------------------------
    //
    // The same hour-apart day, with its ends: Fajr at sunrise (1_003_600), Asr at sunset
    // (1_014_340, a minute before Maghrib).

    private fun ended(d: DayPrayerTimes) = d.copy(
        ends = mapOf(
            Prayer.FAJR to d.time(Prayer.SUNRISE),
            Prayer.DHUHR to d.asrOther,
            Prayer.ASR to d.sunset,
            Prayer.MAGHRIB to d.time(Prayer.ISHA),
        ),
    )

    private val endedToday = ended(today)

    private fun screen(now: Long) =
        TimelineBuilder.build(ended(yesterday), endedToday, ended(tomorrow), Instant.fromEpochSeconds(now), false, TimeZone.UTC)

    private fun TodayState.statusOf(p: Prayer) = rows.first { it.prayer == p }.status

    @Test
    fun fajrIsCurrentUntilSunrise() {
        assertEquals(PrayerStatus.CURRENT, screen(1_003_540).statusOf(Prayer.FAJR))
    }

    @Test
    fun fajrIsNotCurrentFromSunrise() {
        val s = screen(1_003_600)
        assertEquals(PrayerStatus.PASSED, s.statusOf(Prayer.FAJR))
        assertTrue(s.rows.none { it.status == PrayerStatus.CURRENT })
        assertEquals(Prayer.DHUHR, s.next.prayer)
    }

    @Test
    fun theRingStillCountsFromFajrToDhuhrAfterSunrise() {
        // Three quarters of the way from Fajr (1_000_000) to Dhuhr (1_007_200), after sunrise.
        val s = screen(1_005_400)
        assertEquals(0.75f, s.ringProgress, 0.001f)
        assertEquals(1_800L, s.countdown.inWholeSeconds)
    }

    @Test
    fun asrIsCurrentUntilSunset() {
        assertEquals(PrayerStatus.CURRENT, screen(1_014_300).statusOf(Prayer.ASR))
    }

    @Test
    fun asrIsNotCurrentFromSunset() {
        val s = screen(1_014_340)
        assertEquals(PrayerStatus.PASSED, s.statusOf(Prayer.ASR))
        assertTrue(s.rows.none { it.status == PrayerStatus.CURRENT })
        assertEquals(Prayer.MAGHRIB, s.next.prayer)
    }

    @Test
    fun dhuhrMaghribAndIshaKeepTheirHighlightPastTheirEnds() {
        // Their ends mark a preferred time, not a lapse the screen shows.
        assertEquals(PrayerStatus.CURRENT, screen(1_010_000).statusOf(Prayer.DHUHR))
        assertEquals(PrayerStatus.CURRENT, screen(1_018_100).statusOf(Prayer.ISHA))
    }

    @Test
    fun theWidgetsKeepTodaysRule() {
        // Ruling R8: the widgets change in the Ramadan release, not now.
        val s = TimelineBuilder.buildForWidgets(
            ended(yesterday), endedToday, ended(tomorrow), Instant.fromEpochSeconds(1_003_600), false, TimeZone.UTC,
        )
        assertEquals(PrayerStatus.CURRENT, s.statusOf(Prayer.FAJR))
        val afterSunset = TimelineBuilder.buildForWidgets(
            ended(yesterday), endedToday, ended(tomorrow), Instant.fromEpochSeconds(1_014_340), false, TimeZone.UTC,
        )
        assertEquals(PrayerStatus.CURRENT, afterSunset.statusOf(Prayer.ASR))
    }

    // -- Isha after local midnight -------------------------------------------------------------
    //
    // Tromsø on 17 and 18 May 2026 as the engine gave them when these were written, before ruling R82
    // moved Tromsø to IRN's Makkah time (hand-made fixtures; under the rule the night is much the same,
    // Maghrib waiting for the real sunset after midnight) (Europe/Oslo, UTC+2): the 17th's
    // Maghrib is at 00:13 and its Isha at 00:41 on the 18th, after the 18th's own Fajr (00:38) and
    // sunrise (00:39). The 17th's next end of eating (00:38) is not after its Isha, so ruling R26
    // leaves that Isha with no end. A prayer of yesterday's counts only while it starts before
    // today's Fajr, so the 17th's Maghrib counts and its Isha does not.

    private val oslo = TimeZone.of("Europe/Oslo")

    private fun at(text: String) = Instant.parse(text)

    private fun tromso(
        date: LocalDate,
        fajr: String, sunrise: String, dhuhr: String, asr: String, maghrib: String, isha: String, sunset: String,
        ends: Map<Prayer, String>,
    ) = DayPrayerTimes(
        date = date,
        times = listOf(
            PrayerTime(Prayer.FAJR, at(fajr)),
            PrayerTime(Prayer.SUNRISE, at(sunrise)),
            PrayerTime(Prayer.DHUHR, at(dhuhr)),
            PrayerTime(Prayer.ASR, at(asr)),
            PrayerTime(Prayer.MAGHRIB, at(maghrib)),
            PrayerTime(Prayer.ISHA, at(isha)),
        ),
        highLatitudeRuleApplied = null,
        asrOther = at(asr),
        sunset = at(sunset),
        endOfEating = at(fajr),
        ends = ends.mapValues { at(it.value) },
        sourceEntryId = "se.cautious",
        entryClass = EntryClass.C,
    )

    private val may17 = tromso(
        LocalDate(2026, 5, 17),
        fajr = "2026-05-16T23:07:00Z", sunrise = "2026-05-16T23:08:00Z", dhuhr = "2026-05-17T10:52:00Z",
        asr = "2026-05-17T16:55:00Z", maghrib = "2026-05-17T22:13:00Z", isha = "2026-05-17T22:41:00Z",
        sunset = "2026-05-17T22:04:00Z",
        // No Isha end: the next end of eating (22:38Z) is not after Isha (22:41Z).
        ends = mapOf(Prayer.FAJR to "2026-05-16T23:08:00Z", Prayer.ASR to "2026-05-17T22:04:00Z"),
    )
    private val may18 = tromso(
        LocalDate(2026, 5, 18),
        fajr = "2026-05-17T22:38:00Z", sunrise = "2026-05-17T22:39:00Z", dhuhr = "2026-05-18T10:52:00Z",
        asr = "2026-05-18T16:57:00Z", maghrib = "2026-05-18T22:42:00Z", isha = "2026-05-18T22:43:00Z",
        sunset = "2026-05-18T21:59:00Z",
        ends = mapOf(
            Prayer.FAJR to "2026-05-17T22:39:00Z", Prayer.ASR to "2026-05-18T21:59:00Z",
            Prayer.ISHA to "2026-05-18T22:58:00Z",
        ),
    )
    private val may19 = tromso(
        LocalDate(2026, 5, 19),
        fajr = "2026-05-18T23:05:00Z", sunrise = "2026-05-18T23:12:00Z", dhuhr = "2026-05-19T10:52:00Z",
        asr = "2026-05-19T16:59:00Z", maghrib = "2026-05-19T22:10:00Z", isha = "2026-05-19T22:33:00Z",
        sunset = "2026-05-19T22:00:00Z",
        ends = mapOf(Prayer.FAJR to "2026-05-18T23:12:00Z", Prayer.ASR to "2026-05-19T22:00:00Z"),
    )

    /** [utc] on the night of the 17th to the 18th; Oslo's summer clock is UTC+2. */
    private fun tromsoAt(utc: String) = TimelineBuilder.build(may17, may18, may19, at("2026-05-17T${utc}Z"), false, oslo)

    @Test
    fun yesterdaysMaghribAfterMidnightIsTheNextPrayerWhileItIsAhead() {
        // 00:05 local on the 18th: the 17th's Maghrib (00:13) comes first.
        val s = tromsoAt("22:05:00")
        assertEquals(Prayer.MAGHRIB, s.next.prayer)
        assertEquals(may17.time(Prayer.MAGHRIB), s.next.instant)
        assertEquals(8L, s.countdown.inWholeMinutes)
    }

    @Test
    fun beforeTodaysFajrTheRingCountsFromYesterdaysMaghrib() {
        // 00:20 local: the 17th's Maghrib has begun, the 18th's Fajr (00:38) is next.
        val s = tromsoAt("22:20:00")
        assertEquals(Prayer.FAJR, s.next.prayer)
        assertEquals(may18.time(Prayer.FAJR), s.next.instant)
        // 7 of the 25 minutes from 00:13 to 00:38.
        assertEquals(7f / 25f, s.ringProgress, 0.001f)
        assertTrue(s.rows.none { it.status == PrayerStatus.CURRENT })
    }

    @Test
    fun yesterdaysIshaAfterTodaysFajrDoesNotCount() {
        // 00:39:30 local: the 18th's Fajr (00:38) is in and ended at sunrise (00:39). The 17th's
        // Isha (00:41, no end under R26) starts after that Fajr, so it is not counted: Dhuhr is next.
        val s = tromsoAt("22:39:30")
        assertEquals(Prayer.DHUHR, s.next.prayer)
        assertEquals(may18.time(Prayer.DHUHR), s.next.instant)
        assertEquals(PrayerStatus.PASSED, s.statusOf(Prayer.FAJR))
        assertTrue(s.rows.none { it.status == PrayerStatus.CURRENT })
        // Today's own Isha row is the 18th's, still ahead.
        assertEquals(PrayerStatus.UPCOMING, s.statusOf(Prayer.ISHA))
    }

    /** The widgets' timeline at [utc] on the night of the 17th to the 18th. */
    private fun tromsoWidgetsAt(utc: String) =
        TimelineBuilder.buildForWidgets(may17, may18, may19, at("2026-05-17T${utc}Z"), false, oslo)

    @Test
    fun theWidgetsNextPrayerLooksAcrossYesterday() {
        // Ruling R8 amended: the widgets' next and countdown look across yesterday as the screen's
        // do; only their highlight keeps today's rule.
        val ahead = tromsoWidgetsAt("22:05:00")
        assertEquals(may17.time(Prayer.MAGHRIB), ahead.next.instant)
        assertEquals(8L, ahead.countdown.inWholeMinutes)
        val begun = tromsoWidgetsAt("22:20:00")
        assertEquals(may18.time(Prayer.FAJR), begun.next.instant)
        assertEquals(7f / 25f, begun.ringProgress, 0.001f)
        assertTrue(begun.rows.none { it.status == PrayerStatus.CURRENT })
        // Yesterday's Isha after today's Fajr does not count for the widgets either.
        assertEquals(may18.time(Prayer.DHUHR), tromsoWidgetsAt("22:39:30").next.instant)
    }

    @Test
    fun afterTodaysFajrTheRingCountsFromItToDhuhr() {
        // 00:45 local: the ring runs from the 18th's Fajr (00:38), not from the 17th's Isha (00:41).
        val s = tromsoAt("22:45:00")
        assertEquals(Prayer.DHUHR, s.next.prayer)
        val total = (may18.time(Prayer.DHUHR) - may18.time(Prayer.FAJR)).inWholeSeconds.toFloat()
        assertEquals(420f / total, s.ringProgress, 0.0001f)
    }
}
