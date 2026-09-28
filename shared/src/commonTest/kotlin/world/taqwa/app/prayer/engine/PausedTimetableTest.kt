package world.taqwa.app.prayer.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.day.PrayerDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A chosen timetable held to Automatic (ruling R52), on invented days. */
class PausedTimetableTest {
    private val date = LocalDate(2026, 6, 21)
    private val midnight = date.atStartOfDayIn(TimeZone.UTC)

    private fun at(hhmm: String): Instant {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return midnight + (h * 60 + m).minutes
    }

    /** A day from end of eating, Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha and sunset, with its ends. */
    private fun day(times: String, id: String): PrayerDay {
        val t = times.split(" ").map(::at)
        return PrayerDay(
            date = date,
            endOfEating = t[0], fajr = t[1], sunrise = t[2], dhuhr = t[3], asr = t[4], asrOther = t[4] + 45.minutes,
            maghrib = t[5], isha = t[6], sunset = t[7], imsak = null,
            ends = Ends.of(sunrise = t[2], standardAsr = t[4], sunset = t[7], earliestIsha = t[6], maghribEndCap = null, nextEndOfEating = null),
            methodId = id,
        )
    }

    @Test
    fun `each start is the later and each end the earlier where the two days keep their order`() {
        val chosen = day("04:40 04:50 06:10 12:05 15:40 19:05 20:40 19:02", "chosen")
        val automatic = day("04:30 04:55 06:12 12:10 15:35 19:08 20:30 19:03", "automatic")
        val paused = PausedTimetable.combine(chosen, automatic)
        assertEquals(at("04:55"), paused.fajr)
        assertEquals(at("06:10"), paused.sunrise)
        assertEquals(at("12:10"), paused.dhuhr)
        assertEquals(at("15:40"), paused.asr)
        assertEquals(at("19:08"), paused.maghrib)
        assertEquals(at("20:40"), paused.isha)
        assertEquals(at("04:30"), paused.endOfEating)
        assertEquals(at("06:10"), paused.ends[Prayer.FAJR])
        assertEquals(at("15:35"), paused.ends[Prayer.DHUHR])
        assertEquals(at("19:02"), paused.ends[Prayer.ASR])
        assertEquals(at("20:30"), paused.ends[Prayer.MAGHRIB])
        assertFalse(paused.repaired)
    }

    @Test
    fun `a later fajr at or after the other day's sunrise keeps its own day's sunrise`() {
        // A sun-based day at a nearest latitude against an authority's Makkah time (ruling R82).
        val chosen = day("00:50 01:00 01:40 12:00 17:00 23:50 23:55 23:45", "chosen")
        val automatic = day("02:50 03:00 04:00 12:10 15:00 19:35 21:30 19:30", "automatic")
            .copy(notFollowed = setOf(Prayer.SUNRISE), setByRule = setOf(Prayer.FAJR, Prayer.SUNRISE))
        val paused = PausedTimetable.combine(chosen, automatic)
        // Fajr is never earlier than either; the sunrise is Automatic's own, after the chosen's.
        assertEquals(at("03:00"), paused.fajr)
        assertEquals(at("04:00"), paused.sunrise)
        assertEquals(at("04:00"), paused.ends[Prayer.FAJR])
        assertEquals(at("00:50"), paused.endOfEating)
        assertEquals(setOf(Prayer.FAJR, Prayer.SUNRISE), paused.setByRule)
        assertEquals(setOf(Prayer.SUNRISE), paused.notFollowed)
        assertTrue(Invariants.holds(paused))
        assertFalse(paused.repaired)
    }

    @Test
    fun `an end at or before the later start is the end of the day whose start is shown`() {
        val chosen = day("00:50 01:00 01:40 12:00 17:00 23:50 23:55 23:45", "chosen")
        val automatic = day("00:40 00:55 01:50 12:10 15:00 16:35 18:00 16:30", "automatic")
        val paused = PausedTimetable.combine(chosen, automatic)
        assertEquals(at("17:00"), paused.asr)
        assertEquals(at("23:45"), paused.ends[Prayer.ASR], "the chosen's own sunset: Automatic's is before its Asr")
        assertEquals(at("23:50"), paused.maghrib)
        assertEquals(at("23:55"), paused.ends[Prayer.MAGHRIB], "the chosen's own Isha: Automatic's is before its Maghrib")
        assertEquals(at("15:00"), paused.ends[Prayer.DHUHR], "an end after the start shown stays the earlier")
        assertEquals(at("16:30"), paused.sunset)
        assertTrue(Invariants.holds(paused))
        assertFalse(paused.repaired)
    }

    @Test
    fun `an end the later start's day does not have is left out`() {
        val chosen = day("00:50 01:00 01:40 12:00 13:00 13:40 15:00 13:35", "chosen")
        val automatic = day("00:40 00:55 01:50 12:10 14:00 15:05 17:00 15:00", "automatic").let { it.copy(ends = it.ends - Prayer.ASR) }
        val paused = PausedTimetable.combine(chosen, automatic)
        assertEquals(at("14:00"), paused.asr)
        assertFalse(Prayer.ASR in paused.ends, "Automatic shows no end for its Asr, and the chosen's is before it")
        assertTrue(Invariants.holds(paused))
        assertFalse(paused.repaired)
    }
}
