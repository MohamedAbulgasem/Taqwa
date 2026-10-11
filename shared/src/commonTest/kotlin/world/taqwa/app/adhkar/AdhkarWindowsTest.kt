package world.taqwa.app.adhkar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.prayer.engine.registry.EntryClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AdhkarWindowsTest {

    private val tripoli = TimeZone.of("Africa/Tripoli")
    private val date = LocalDate(2026, 10, 11)
    private val nextDay = LocalDate(2026, 10, 12)

    private fun local(hour: Int, minute: Int, zone: TimeZone = tripoli, on: LocalDate = date): Instant =
        on.atTime(hour, minute).toInstant(zone)

    private fun day(
        fajr: Instant = local(5, 12),
        sunrise: Instant = local(6, 30),
        dhuhr: Instant = local(12, 45),
        asr: Instant = local(16, 0),
        maghrib: Instant = local(18, 30),
        isha: Instant = local(19, 45),
    ) = DayPrayerTimes(
        date = date,
        times = listOf(
            PrayerTime(Prayer.FAJR, fajr),
            PrayerTime(Prayer.SUNRISE, sunrise),
            PrayerTime(Prayer.DHUHR, dhuhr),
            PrayerTime(Prayer.ASR, asr),
            PrayerTime(Prayer.MAGHRIB, maghrib),
            PrayerTime(Prayer.ISHA, isha),
        ),
        highLatitudeRuleApplied = null,
        asrOther = asr + 30.minutes,
        sunset = maghrib - 1.minutes,
        endOfEating = fajr,
        sourceEntryId = "test",
        entryClass = EntryClass.D_NONE,
    )

    private val today = day()

    private fun window(set: AdhkarSet, now: Instant, times: DayPrayerTimes = today) =
        AdhkarWindows.windowFor(set, now, times)

    @Test fun `before fajr it is still the evening and nothing is open`() {
        val now = local(4, 0)
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(now, today))
        assertEquals(AdhkarWindow(AdhkarSet.EVENING, AdhkarPhase.NONE, null), window(AdhkarSet.EVENING, now))
        assertEquals(AdhkarWindow(AdhkarSet.MORNING, AdhkarPhase.NONE, null), window(AdhkarSet.MORNING, now))
    }

    @Test fun `ten minutes after fajr is the best time of the morning until sunrise`() {
        val now = local(5, 12) + 10.minutes
        assertEquals(AdhkarSet.MORNING, AdhkarWindows.setFor(now, today))
        assertEquals(
            AdhkarWindow(AdhkarSet.MORNING, AdhkarPhase.BEST, local(6, 30)),
            window(AdhkarSet.MORNING, now),
        )
    }

    @Test fun `an hour after sunrise the morning can still be made up until dhuhr`() {
        val now = local(6, 30) + 60.minutes
        assertEquals(AdhkarSet.MORNING, AdhkarWindows.setFor(now, today))
        assertEquals(
            AdhkarWindow(AdhkarSet.MORNING, AdhkarPhase.MAKEUP, local(12, 45)),
            window(AdhkarSet.MORNING, now),
        )
    }

    @Test fun `a minute after dhuhr the morning is over but the half day is still the morning`() {
        val now = local(12, 45) + 1.minutes
        assertEquals(AdhkarSet.MORNING, AdhkarWindows.setFor(now, today))
        assertEquals(AdhkarWindow(AdhkarSet.MORNING, AdhkarPhase.NONE, null), window(AdhkarSet.MORNING, now))
    }

    @Test fun `a minute after asr the evening is at its best until maghrib`() {
        val now = local(16, 0) + 1.minutes
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(now, today))
        assertEquals(
            AdhkarWindow(AdhkarSet.EVENING, AdhkarPhase.BEST, local(18, 30)),
            window(AdhkarSet.EVENING, now),
        )
    }

    @Test fun `a minute after maghrib the evening can be made up until isha`() {
        val now = local(18, 30) + 1.minutes
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(now, today))
        assertEquals(
            AdhkarWindow(AdhkarSet.EVENING, AdhkarPhase.MAKEUP, local(19, 45)),
            window(AdhkarSet.EVENING, now),
        )
    }

    @Test fun `a minute after isha the evening is over`() {
        val now = local(19, 45) + 1.minutes
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(now, today))
        assertEquals(AdhkarWindow(AdhkarSet.EVENING, AdhkarPhase.NONE, null), window(AdhkarSet.EVENING, now))
    }

    @Test fun `each boundary belongs to the phase that begins there`() {
        // Fajr, sunrise, Dhuhr, Asr, Maghrib and Isha are the first instants of their phases.
        assertEquals(AdhkarPhase.BEST, window(AdhkarSet.MORNING, local(5, 12)).phase)
        assertEquals(AdhkarPhase.MAKEUP, window(AdhkarSet.MORNING, local(6, 30)).phase)
        assertEquals(AdhkarPhase.NONE, window(AdhkarSet.MORNING, local(12, 45)).phase)
        assertEquals(AdhkarPhase.BEST, window(AdhkarSet.EVENING, local(16, 0)).phase)
        assertEquals(AdhkarPhase.MAKEUP, window(AdhkarSet.EVENING, local(18, 30)).phase)
        assertEquals(AdhkarPhase.NONE, window(AdhkarSet.EVENING, local(19, 45)).phase)

        assertEquals(AdhkarSet.MORNING, AdhkarWindows.setFor(local(5, 12), today))
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(local(16, 0), today))
        assertEquals(AdhkarSet.MORNING, AdhkarWindows.setFor(local(16, 0) - 1.minutes, today))
    }

    @Test fun `the other set is never open while one is`() {
        assertEquals(AdhkarPhase.NONE, window(AdhkarSet.EVENING, local(7, 0)).phase)
        assertEquals(AdhkarPhase.NONE, window(AdhkarSet.MORNING, local(17, 0)).phase)
    }

    @Test fun `a sunrise that comes with fajr leaves no best time and the morning is all make up`() {
        // High latitudes: the twilight can last to the sunrise itself (Tromso in May has Fajr at
        // 01:07 and sunrise at 01:08). The comparisons are the whole rule, so the best phase is
        // simply empty rather than an error; a DayPrayerTimes always carries all six times.
        val polar = day(sunrise = local(5, 12))
        assertEquals(
            AdhkarWindow(AdhkarSet.MORNING, AdhkarPhase.MAKEUP, local(12, 45)),
            window(AdhkarSet.MORNING, local(5, 12), polar),
        )
        assertEquals(
            AdhkarWindow(AdhkarSet.MORNING, AdhkarPhase.MAKEUP, local(12, 45)),
            window(AdhkarSet.MORNING, local(5, 13), polar),
        )
    }

    @Test fun `an isha after midnight keeps the evening make up open past midnight`() {
        // Tromso: Maghrib 00:13 and Isha 00:41 on the next morning. Instants are compared, not
        // clock times, so the window simply runs across the date line.
        val oslo = TimeZone.of("Europe/Oslo")
        val late = day(
            fajr = local(1, 7, oslo),
            sunrise = local(1, 8, oslo),
            dhuhr = local(12, 52, oslo),
            asr = local(18, 55, oslo),
            maghrib = local(0, 13, oslo, nextDay),
            isha = local(0, 41, oslo, nextDay),
        )
        val afterMidnight = local(0, 20, oslo, nextDay)
        assertEquals(
            AdhkarWindow(AdhkarSet.EVENING, AdhkarPhase.MAKEUP, local(0, 41, oslo, nextDay)),
            window(AdhkarSet.EVENING, afterMidnight, late),
        )
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(afterMidnight, late))
    }

    @Test fun `a set is dated by the local date of its own prayer`() {
        assertEquals(date, AdhkarWindows.dateKey(AdhkarSet.MORNING, today, tripoli))
        assertEquals(date, AdhkarWindows.dateKey(AdhkarSet.EVENING, today, tripoli))
    }

    @Test fun `the date is the local one and not the one at Greenwich`() {
        // Auckland is UTC+13 in October: Fajr at 05:12 local is 16:12 the day before in UTC, while
        // Asr at 16:00 local is the same date in both.
        val auckland = TimeZone.of("Pacific/Auckland")
        val nz = day(fajr = local(5, 12, auckland), asr = local(16, 0, auckland))
        assertEquals(date, AdhkarWindows.dateKey(AdhkarSet.MORNING, nz, auckland))
        assertEquals(date, AdhkarWindows.dateKey(AdhkarSet.EVENING, nz, auckland))
        assertEquals(LocalDate(2026, 10, 10), AdhkarWindows.dateKey(AdhkarSet.MORNING, nz, TimeZone.UTC))
        assertEquals(date, AdhkarWindows.dateKey(AdhkarSet.EVENING, nz, TimeZone.UTC))
    }
}
