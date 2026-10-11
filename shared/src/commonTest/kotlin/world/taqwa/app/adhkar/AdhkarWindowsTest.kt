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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AdhkarWindowsTest {

    private val tripoli = TimeZone.of("Africa/Tripoli")
    private val date = LocalDate(2026, 10, 11)
    private val nextDay = LocalDate(2026, 10, 12)

    private fun local(hour: Int, minute: Int, zone: TimeZone = tripoli, on: LocalDate = date): Instant =
        on.atTime(hour, minute).toInstant(zone)

    private fun day(
        on: LocalDate = date,
        fajr: Instant = local(5, 12, on = on),
        sunrise: Instant = local(6, 30, on = on),
        dhuhr: Instant = local(12, 45, on = on),
        asr: Instant = local(16, 0, on = on),
        maghrib: Instant = local(18, 30, on = on),
        isha: Instant = local(19, 45, on = on),
    ) = DayPrayerTimes(
        date = on,
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

    // dayFor: which day's times a given instant is read against.

    @Test fun `after fajr the day is the local date's own and yesterday is never asked for`() {
        val asked = mutableListOf<LocalDate>()
        val times: (LocalDate) -> DayPrayerTimes = { asked += it; day(on = it) }
        val morning = AdhkarWindows.dayFor(local(5, 30), tripoli, times)
        assertEquals(date, morning.date)
        assertEquals(listOf(date), asked)
        // Fajr itself is the first instant of the day, and the evening is the same day's too.
        assertEquals(date, AdhkarWindows.dayFor(local(5, 12), tripoli, times).date)
        assertEquals(date, AdhkarWindows.dayFor(local(22, 0), tripoli, times).date)
    }

    @Test fun `between midnight and fajr the day is yesterday's and the evening is keyed to it`() {
        val asked = mutableListOf<LocalDate>()
        val times: (LocalDate) -> DayPrayerTimes = { asked += it; day(on = it) }
        val now = local(0, 30, on = nextDay)
        val yesterday = AdhkarWindows.dayFor(now, tripoli, times)
        assertEquals(date, yesterday.date)
        assertEquals(listOf(nextDay, date), asked)
        // All three functions agree on yesterday's evening: it is the evening set, dated by
        // yesterday's Asr, and (Isha being at 19:45) its window is over.
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(now, yesterday))
        assertEquals(date, AdhkarWindows.dateKey(AdhkarSet.EVENING, yesterday, tripoli))
        assertEquals(AdhkarPhase.NONE, window(AdhkarSet.EVENING, now, yesterday).phase)
        // Fed today's times instead, the key would be an Asr that has not happened yet.
        assertEquals(nextDay, AdhkarWindows.dateKey(AdhkarSet.EVENING, day(on = nextDay), tripoli))
    }

    @Test fun `the local date decides the day and not the date at Greenwich`() {
        // 23:30 UTC on the 10th is 01:30 on the 11th in Tripoli: before Fajr, so the 10th's times.
        val now = Instant.parse("2026-10-10T23:30:00Z")
        val asked = mutableListOf<LocalDate>()
        val result = AdhkarWindows.dayFor(now, tripoli) { asked += it; day(on = it) }
        assertEquals(LocalDate(2026, 10, 10), result.date)
        assertEquals(listOf(date, LocalDate(2026, 10, 10)), asked)
    }

    @Test fun `an isha after midnight is reached through dayFor`() {
        // Tromso, as above: the 11th's Isha is 00:41 on the 12th, after midnight and before the
        // 12th's own Fajr (01:09). At 00:20 the local date is the 12th, but the evening that is
        // open is the 11th's, and only dayFor gets there.
        val oslo = TimeZone.of("Europe/Oslo")
        val eleventh = day(
            on = date,
            fajr = local(1, 7, oslo),
            sunrise = local(1, 8, oslo),
            dhuhr = local(12, 52, oslo),
            asr = local(18, 55, oslo),
            maghrib = local(0, 13, oslo, nextDay),
            isha = local(0, 41, oslo, nextDay),
        )
        val twelfth = day(
            on = nextDay,
            fajr = local(1, 9, oslo, nextDay),
            sunrise = local(1, 10, oslo, nextDay),
            dhuhr = local(12, 52, oslo, nextDay),
            asr = local(18, 58, oslo, nextDay),
            maghrib = local(0, 40, oslo, LocalDate(2026, 10, 13)),
            isha = local(1, 5, oslo, LocalDate(2026, 10, 13)),
        )
        val now = local(0, 20, oslo, nextDay)
        val chosen = AdhkarWindows.dayFor(now, oslo) { if (it == date) eleventh else twelfth }
        assertEquals(date, chosen.date)
        assertEquals(AdhkarSet.EVENING, AdhkarWindows.setFor(now, chosen))
        assertEquals(
            AdhkarWindow(AdhkarSet.EVENING, AdhkarPhase.MAKEUP, local(0, 41, oslo, nextDay)),
            window(AdhkarSet.EVENING, now, chosen),
        )
        assertEquals(date, AdhkarWindows.dateKey(AdhkarSet.EVENING, chosen, oslo))
    }

    // isOpen: a set counts only from its opening prayer (spec §3.3).

    @Test fun `the evening counts only from asr and on through its make up and after`() {
        // The device pass's case: an evening set read at 07:12 is not that day's evening.
        assertFalse(AdhkarWindows.isOpen(AdhkarSet.EVENING, local(5, 12) + 1.hours, today))
        assertFalse(AdhkarWindows.isOpen(AdhkarSet.EVENING, local(16, 0) - 1.minutes, today))
        // Asr is the first instant it counts; the closing is unchanged, so the make-up after
        // Maghrib counts as it always has, and so does the rest of the night.
        assertTrue(AdhkarWindows.isOpen(AdhkarSet.EVENING, local(16, 0), today))
        assertTrue(AdhkarWindows.isOpen(AdhkarSet.EVENING, local(18, 30) + 1.hours, today))
        assertTrue(AdhkarWindows.isOpen(AdhkarSet.EVENING, local(19, 45) + 1.minutes, today))
        assertEquals(local(16, 0), AdhkarWindows.opensAt(AdhkarSet.EVENING, today))
    }

    @Test fun `the morning counts from fajr and until the next fajr is still the previous day's`() {
        assertTrue(AdhkarWindows.isOpen(AdhkarSet.MORNING, local(5, 12), today))
        assertTrue(AdhkarWindows.isOpen(AdhkarSet.MORNING, local(12, 45) + 1.hours, today))
        assertEquals(local(5, 12), AdhkarWindows.opensAt(AdhkarSet.MORNING, today))
        // At 03:00 the day is yesterday's, and both of yesterday's sets have long opened.
        val threeAm = local(3, 0, on = nextDay)
        val previous = AdhkarWindows.dayFor(threeAm, tripoli) { day(on = it) }
        assertEquals(date, previous.date)
        assertTrue(AdhkarWindows.isOpen(AdhkarSet.MORNING, threeAm, previous))
        assertTrue(AdhkarWindows.isOpen(AdhkarSet.EVENING, threeAm, previous))
    }

    @Test fun `dayFor never hands back a day whose fajr is still ahead so a morning is never early`() {
        // The proof that the morning needs no rule of its own: whatever the instant, the day dayFor
        // picks has its Fajr at or before it. Every seven minutes over three days in Tripoli, and
        // over the Tromso days whose Fajr is at one in the morning.
        val oslo = TimeZone.of("Europe/Oslo")
        val tromso: (LocalDate) -> DayPrayerTimes = { on ->
            day(
                on = on,
                fajr = local(1, 7, oslo, on),
                sunrise = local(1, 8, oslo, on),
                dhuhr = local(12, 52, oslo, on),
                asr = local(18, 55, oslo, on),
                maghrib = local(23, 50, oslo, on),
                isha = local(23, 58, oslo, on),
            )
        }
        val cases = listOf(Triple(tripoli, local(0, 0), { on: LocalDate -> day(on = on) }), Triple(oslo, local(0, 0, oslo), tromso))
        for ((zone, start, timesFor) in cases) {
            var now = start
            while (now < start + 72.hours) {
                val chosen = AdhkarWindows.dayFor(now, zone, timesFor)
                assertTrue(AdhkarWindows.isOpen(AdhkarSet.MORNING, now, chosen), "morning not open at " + now)
                now += 7.minutes
            }
        }
    }
}
