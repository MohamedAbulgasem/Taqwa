package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class InvariantsTest {
    private val date = LocalDate(2026, 10, 1)
    private val midnight = date.atStartOfDayIn(TimeZone.UTC)

    private fun at(hhmm: String): Instant {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return midnight + (h * 60 + m).minutes
    }

    /** A day from end of eating, Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha and sunset. */
    private fun day(times: String, imsak: String? = null): PrayerDay {
        val t = times.split(" ").map(::at)
        return PrayerDay(
            date = date,
            endOfEating = t[0], fajr = t[1], sunrise = t[2], dhuhr = t[3], asr = t[4], asrOther = t[4] + 45.minutes,
            maghrib = t[5], isha = t[6], sunset = t[7], imsak = imsak?.let(::at),
            methodId = "test",
        )
    }

    private val ordered = "05:10 05:10 06:40 12:50 16:10 18:55 20:10 18:52"

    @Test
    fun `an ordered day is left as it is`() {
        val day = day(ordered, imsak = "05:00")
        assertTrue(Invariants.holds(day))
        assertSame(day, Invariants.repair(day))
        assertFalse(Invariants.repair(day).repaired)
    }

    @Test
    fun `an isha before maghrib comes out ordered and flagged`() {
        val broken = day("05:10 05:10 06:40 12:50 16:10 18:55 18:50 18:52")
        assertFalse(Invariants.holds(broken))
        val fixed = Invariants.repair(broken)
        assertTrue(Invariants.holds(fixed))
        assertTrue(fixed.repaired)
        assertEquals(at("18:56"), fixed.isha)
        assertEquals(broken.copy(isha = at("18:56"), repaired = true), fixed)
    }

    @Test
    fun `an end of eating after fajr moves back to fajr`() {
        val fixed = Invariants.repair(day("05:20 05:10 06:40 12:50 16:10 18:55 20:10 18:52"))
        assertEquals(at("05:10"), fixed.endOfEating)
        assertTrue(fixed.repaired && Invariants.holds(fixed))
    }

    @Test
    fun `a maghrib before sunset moves to the minute after the rounded down sunset`() {
        // The sunset shown is rounded down: the raw sunset lies within the minute after it, so the
        // earliest Maghrib that is surely not before it is that minute's end.
        val fixed = Invariants.repair(day("05:10 05:10 06:40 12:50 16:10 18:50 20:10 18:52"))
        assertEquals(at("18:53"), fixed.maghrib)
        assertEquals(at("20:10"), fixed.isha)
        assertTrue(fixed.repaired && Invariants.holds(fixed))
        // A Maghrib already at the rounded-down sunset is in order and left alone.
        val atSunset = day("05:10 05:10 06:40 12:50 16:10 18:52 20:10 18:52")
        assertSame(atSunset, Invariants.repair(atSunset))
    }

    @Test
    fun `a repaired day keeps its ends after their starts and maghrib's no later than isha`() {
        fun withEnds(day: PrayerDay, maghribEnd: String) = day.copy(
            ends = mapOf(
                Prayer.FAJR to day.sunrise, Prayer.DHUHR to day.asr, Prayer.ASR to day.sunset,
                Prayer.MAGHRIB to at(maghribEnd), Prayer.ISHA to at("29:10"),
            ),
        )
        // Isha before Maghrib; Maghrib's end still at the old Isha, before the Maghrib start: dropped.
        val early = Invariants.repair(withEnds(day("05:10 05:10 06:40 12:50 16:10 18:55 18:50 18:52"), "18:50"))
        assertEquals(at("18:56"), early.isha)
        assertEquals(setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.ISHA), early.ends.keys)
        assertTrue(early.repaired && Invariants.holds(early))

        // Maghrib's end past the repaired Isha: held to it.
        val late = Invariants.repair(withEnds(day("05:10 05:10 06:40 12:50 16:10 18:55 18:50 18:52"), "20:30"))
        assertEquals(at("18:56"), late.ends[Prayer.MAGHRIB])
        assertTrue(late.repaired && Invariants.holds(late))

        // A Dhuhr after Asr pushes Asr past sunset: Dhuhr's end (the old Asr) and Asr's (sunset) are
        // no longer after their starts, and go.
        val pushed = Invariants.repair(withEnds(day("05:10 05:10 06:40 19:00 16:10 19:05 20:10 18:52"), "20:00"))
        assertEquals(at("19:01"), pushed.asr)
        assertEquals(setOf(Prayer.FAJR, Prayer.MAGHRIB, Prayer.ISHA), pushed.ends.keys)
        assertTrue(pushed.repaired && Invariants.holds(pushed))

        // An ordered day with an end not after its start is repaired by dropping that end only.
        val ordered = day(ordered)
        val stray = withEnds(ordered, "20:00").let { it.copy(ends = it.ends + (Prayer.DHUHR to ordered.dhuhr)) }
        assertFalse(Invariants.holds(stray))
        val dropped = Invariants.repair(stray)
        assertEquals(stray.copy(ends = stray.ends - Prayer.DHUHR, repaired = true), dropped)
    }

    @Test
    fun `a fajr at or after sunrise moves to the minute before sunrise`() {
        val fixed = Invariants.repair(day("06:45 06:45 06:40 12:50 16:10 18:55 20:10 18:52", imsak = "06:44"))
        assertEquals(at("06:39"), fixed.fajr)
        assertEquals(at("06:39"), fixed.endOfEating)
        assertEquals(at("06:39"), fixed.imsak)
        assertEquals(at("06:40"), fixed.sunrise, "sunrise never moves")
        assertTrue(fixed.repaired && Invariants.holds(fixed))
    }

    @Test
    fun `starts that collide are pushed later a minute apart`() {
        val fixed = Invariants.repair(day("05:10 05:10 06:40 06:40 06:40 06:40 06:40 06:30"))
        assertEquals(listOf("06:41", "06:42", "06:43", "06:44").map(::at), listOf(fixed.dhuhr, fixed.asr, fixed.maghrib, fixed.isha))
        assertTrue(fixed.repaired && Invariants.holds(fixed))
    }

    @Test
    fun `a scrambled day never throws and always comes out ordered on the safe side`() {
        fun hhmm(minutes: Int) = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
        val random = Random(27)
        repeat(2_000) {
            val times = List(8) { random.nextInt(0, 36 * 60) }.joinToString(" ", transform = ::hhmm)
            val broken = day(times, imsak = if (random.nextBoolean()) null else hhmm(random.nextInt(0, 24 * 60)))
            val fixed = Invariants.repair(broken)
            assertTrue(Invariants.holds(fixed), "$times → $fixed")
            assertEquals(!Invariants.holds(broken), fixed.repaired, times)
            // Ends never later; sunrise and sunset never move; starts never earlier, except a Fajr
            // that was not before sunrise.
            assertEquals(broken.sunrise, fixed.sunrise)
            assertEquals(broken.sunset, fixed.sunset)
            assertTrue(fixed.endOfEating <= broken.endOfEating, times)
            if (broken.fajr < broken.sunrise) assertEquals(broken.fajr, fixed.fajr, times)
            for ((before, after) in listOf(broken.dhuhr to fixed.dhuhr, broken.asr to fixed.asr, broken.maghrib to fixed.maghrib, broken.isha to fixed.isha)) {
                assertTrue(after >= before, times)
            }
            listOfNotNull(fixed.fajr, fixed.dhuhr, fixed.asr, fixed.maghrib, fixed.isha, fixed.endOfEating, fixed.imsak)
                .forEach { assertEquals(0L, it.epochSeconds % 60, times) }
        }
    }

    @Test
    fun `computed days hold the invariants from the equator to the polar circle`() {
        val method = TimetableMethod(id = "test", fajrAngle = 18.0, isha = IshaRule.Angle(17.0))
        val points = listOf(GeoPoint(0.0, 32.0), GeoPoint(21.42, 39.83), GeoPoint(51.5, -0.13), GeoPoint(59.9, 10.75), GeoPoint(-53.16, -70.9), GeoPoint(68.97, 33.07))
        val zones = listOf("Africa/Kampala", "Asia/Riyadh", "Europe/London", "Europe/Oslo", "America/Punta_Arenas", "Europe/Moscow")
        val dates = listOf(LocalDate(2026, 3, 20), LocalDate(2026, 6, 21), LocalDate(2026, 9, 23), LocalDate(2026, 12, 21))
        points.zip(zones).forEach { (point, zone) ->
            for (d in dates) {
                val day = DayComputer.compute(method, point, d, TimeZone.of(zone), AsrSchool.HANAFI, { false })
                assertTrue(Invariants.holds(day), "$point $d: $day")
            }
        }
    }
}
