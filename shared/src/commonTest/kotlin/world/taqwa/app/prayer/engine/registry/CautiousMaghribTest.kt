package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.Invariants
import world.taqwa.app.prayer.engine.method.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Ruling R155 (the owner's decision of 24 Oct 2026, option B with the far-north guard) on the registry's own
 * cautious entries: "Cautious Maghrib is the latest time any of the place's timetables gives from the sun, never
 * before the most-followed timetable's own Maghrib, and never before the real sunset."
 */
class CautiousMaghribTest {
    private val oslo = TimeZone.of("Europe/Oslo")

    private fun dates(first: LocalDate, last: LocalDate, step: Int = 1): Sequence<LocalDate> =
        generateSequence(first) { it.plus(step, DateTimeUnit.DAY) }.takeWhile { it <= last }

    /** The first whole minute the sun has set by: [sunset] rounded down, plus a minute. */
    private fun minuteAfter(sunset: Instant): Instant = Instant.fromEpochSeconds(sunset.epochSeconds.floorDiv(60L) * 60) + 1.minutes

    private fun local(date: LocalDate, hour: Int, minute: Int, zone: TimeZone): Instant =
        date.atTime(LocalTime(hour, minute)).toInstant(zone)

    /**
     * South of the far north every member's Maghrib follows the sun, so the cautious Maghrib is the latest
     * member's, like every other start, at any spread: the 26 September cap (the most-followed member's own
     * past a 2-minute agreement) is gone.
     */
    @Test
    fun `south of the far north a cautious maghrib is the latest member's`() {
        val places = listOf(
            Place(-33.86785, 151.20732, "Australia/Sydney", "AU"),
            Place(50.85045, 4.34878, "Europe/Brussels", "BE"),
            Place(51.2194, 4.4025, "Europe/Brussels", "BE"), // Antwerp
            Place(48.85341, 2.3488, "Europe/Paris", "FR"),
            Place(52.37403, 4.88969, "Europe/Amsterdam", "NL"),
            Place(52.52437, 13.41053, "Europe/Berlin", "DE"),
            Place(52.4862, -1.8904, "Europe/London", "GB"),
            Place(53.34399, -6.26719, "Europe/Dublin", "IE"),
            Place(59.91273, 10.74609, "Europe/Oslo", "NO"),
            Place(59.33258, 18.0649, "Europe/Stockholm", "SE"),
            Place(43.70011, -79.4163, "America/Toronto", "CA"),
            Place(41.85003, -87.65005, "America/Chicago", "US"),
            Place(-33.92584, 18.42322, "Africa/Johannesburg", "ZA"),
            Place(31.50161, 34.46672, "Asia/Gaza", "PS"),
        )
        for (place in places) {
            val r = Registry.resolve(place)
            assertTrue(r.members.size >= 2, "${r.entry.id} at ${place.lat} is cautious")
            val zone = TimeZone.of(place.zoneId)
            for (date in dates(LocalDate(2026, 10, 1), LocalDate(2027, 9, 30), step = 3)) {
                val members = DayPipeline.members(r, date, zone)
                val label = "${r.entry.id} ${place.lat} $date"
                assertTrue(members.none { it.maghribHeld || Prayer.MAGHRIB in it.setByRule }, "$label: every member follows the sun")
                val day = DayPipeline.unended(r, date, zone)
                if (r.alongside.isEmpty()) {
                    assertEquals(members.maxOf { it.maghrib }, day.maghrib, label)
                } else {
                    assertTrue(day.maghrib >= members.maxOf { it.maghrib }, label)
                }
                assertFalse(Prayer.MAGHRIB in day.setByRule || day.polar || day.maghribHeld, label)
            }
        }
    }

    /**
     * The far-north guard: from about 62.6° north Diyanet's tables hold the winter day to five hours around Dhuhr
     * (TimetableMethod.dayAroundDhuhrMinutes). Where IRN is the most followed (Trondheim, Bodø), Diyanet's member
     * counts at its Maghrib from the sun, its sunset + 7, never at that held minute: the latest of the two from the
     * sun. The day keeps its own flags (item A of the adversarial check): that minute is no member's own Maghrib
     * shown, and it is real. (Bodø's polar-edge days are put in order for IRN's own times, as before; Trondheim's
     * never are.)
     */
    @Test
    fun `where irn is the most followed diyanet's winter hold counts at its own sun time`() {
        val trondheim = Place(63.43049, 10.39506, "Europe/Oslo", "NO")
        for (place in listOf(trondheim, Place(67.28267, 14.37513, "Europe/Oslo", "NO"))) {
            val r = Registry.resolve(place)
            assertEquals("no.cautious", r.entry.id)
            val irn = r.members.indexOfFirst { it.id == "no.irn" }
            val diyanet = r.members.indexOfFirst { it.id == "tr.diyanet.europe" }
            assertEquals(1, r.members[irn].shareRank, "IRN is the most followed")
            var held = 0
            for (date in dates(LocalDate(2026, 11, 15), LocalDate(2027, 1, 31))) {
                val members = DayPipeline.members(r, date, oslo)
                val day = DayPipeline.unended(r, date, oslo)
                val label = "${place.lat} $date"
                if (!members[diyanet].maghribHeld) continue
                held++
                val sun = assertNotNull(members[diyanet].maghribBeforeHold, label)
                assertTrue(sun < members[diyanet].maghrib, "$label: held later than the sun")
                assertEquals(sun, members[diyanet].maghribBySun, label)
                val irnBySun = assertNotNull(members[irn].maghribBySun, "$label: IRN's own from the sun")
                assertEquals(maxOf(irnBySun, sun), day.maghrib, "$label: the latest from the sun, never Diyanet's held minute")
                assertTrue(day.maghrib < members[diyanet].maghrib, label)
                assertFalse(Prayer.MAGHRIB in day.setByRule, "$label: not set by rule")
                assertFalse(day.polar, "$label: not polar")
                if (place == trondheim) assertFalse(day.repaired, "$label: in order")
                assertFalse(day.maghribHeld, label)
                assertTrue(Invariants.holds(day), label)
            }
            assertTrue(held >= 20, "${place.lat}: Diyanet's winter hold binds on $held days")
        }
    }

    /**
     * Where Diyanet is itself the most followed (Luleå, Kiruna), its own held Maghrib counts, as it does today:
     * never before the most-followed timetable's own Maghrib. The day says it is held.
     */
    @Test
    fun `where diyanet is the most followed its winter hold counts`() {
        for (place in listOf(Place(65.58415, 22.15465, "Europe/Stockholm", "SE"), Place(67.85572, 20.22513, "Europe/Stockholm", "SE"))) {
            val r = Registry.resolve(place)
            assertEquals("se.cautious", r.entry.id)
            val zone = TimeZone.of(place.zoneId)
            val diyanet = r.members.indexOfFirst { it.id == "tr.diyanet.europe" }
            assertEquals(1, r.members[diyanet].shareRank, "Diyanet is the most followed")
            var held = 0
            for (date in dates(LocalDate(2026, 11, 15), LocalDate(2027, 1, 31))) {
                val members = DayPipeline.members(r, date, zone)
                if (!members[diyanet].maghribHeld || DayPipeline.realSunset(r.point, date, zone) == null) continue
                held++
                val day = DayPipeline.unended(r, date, zone)
                val label = "${place.lat} $date"
                assertEquals(members[diyanet].maghrib, day.maghrib, "$label: Diyanet's own held Maghrib")
                assertTrue(day.maghribHeld, label)
                assertFalse(Prayer.MAGHRIB in day.setByRule, label)
            }
            assertTrue(held >= 20, "${place.lat}: Diyanet's winter hold binds on $held days")
        }
    }

    /**
     * The Lapland fix: Maghrib is never before the minute after the real sunset at the user's point, wherever the
     * sun sets that day. At Kemi on 23 June 2027 the sun sets at 01:16 the next morning; today's cap showed
     * Diyanet's Maghrib, set by its polar rule, 33 minutes before it.
     */
    @Test
    fun `maghrib is never before the real sunset in lapland`() {
        val kemi = Place(65.73641, 24.56371, "Europe/Helsinki", "FI")
        val zone = TimeZone.of(kemi.zoneId)
        val r = Registry.resolve(kemi)
        assertEquals("se.cautious", r.entry.id)
        val date = LocalDate(2027, 6, 23)
        val sunset = assertNotNull(DayPipeline.realSunset(r.point, date, zone))
        val next = date.plus(1, DateTimeUnit.DAY)
        assertTrue(sunset >= local(next, 1, 16, zone) && sunset < local(next, 1, 17, zone), "Kemi's sunset $sunset")
        val day = DayPipeline.day(r, date, zone)
        assertTrue(day.maghrib >= local(next, 1, 17, zone), "Maghrib ${day.maghrib}")
        assertTrue(day.maghrib < day.isha, "Isha after it")

        val lapland = listOf(
            kemi,
            Place(65.84811, 24.14662, "Europe/Helsinki", "FI"), // Tornio
            Place(66.50394, 25.72939, "Europe/Helsinki", "FI"), // Rovaniemi
            Place(67.85572, 20.22513, "Europe/Stockholm", "SE"), // Kiruna
            Place(65.82522, 21.68856, "Europe/Stockholm", "SE"), // Boden
        )
        for (place in lapland) {
            val r2 = Registry.resolve(place)
            val zone2 = TimeZone.of(place.zoneId)
            for (d in dates(LocalDate(2027, 5, 1), LocalDate(2027, 8, 15))) {
                val real = DayPipeline.realSunset(r2.point, d, zone2) ?: continue
                val shown = DayPipeline.unended(r2, d, zone2)
                assertTrue(shown.maghrib >= minuteAfter(real), "${place.lat} $d: Maghrib ${shown.maghrib} before the minute after the sunset $real")
            }
        }
    }

    /**
     * Item E of the adversarial check: where the real sun does not set at the user's point, no member's Maghrib
     * counts as from the sun, though Diyanet's own sun model still sets it (Finnsnes, 69.2° N, on 20 May 2027):
     * the most-followed member's own, IRN's.
     */
    @Test
    fun `where the real sun does not set only the most followed member's maghrib counts`() {
        val finnsnes = Place(69.23, 17.981, "Europe/Oslo", "NO")
        val r = Registry.resolve(finnsnes)
        assertEquals("no.cautious", r.entry.id)
        val date = LocalDate(2027, 5, 20)
        assertNull(DayPipeline.realSunset(r.point, date, oslo), "the sun does not set")
        val members = DayPipeline.members(r, date, oslo)
        val irn = r.members.indexOfFirst { it.id == "no.irn" }
        val diyanet = r.members.indexOfFirst { it.id == "tr.diyanet.europe" }
        assertNotNull(members[diyanet].maghribBySun, "Diyanet's own sun sets")
        assertTrue(members[diyanet].maghrib > members[irn].maghrib)
        val day = DayPipeline.unended(r, date, oslo)
        assertEquals(members[irn].maghrib, day.maghrib, "IRN's, the most followed")
    }

    /** The real sunset at a point: the exact sun's upper limb on the horizon, null where the sun does not set that day. */
    @Test
    fun `the real sunset is the exact sun's and null where the sun does not set`() {
        val tromso = GeoPoint(69.6489, 18.95508)
        assertNull(DayPipeline.realSunset(tromso, LocalDate(2026, 12, 15), oslo), "polar night")
        assertNull(DayPipeline.realSunset(tromso, LocalDate(2027, 6, 21), oslo), "midnight sun")
        val osloPoint = GeoPoint(59.91273, 10.74609)
        val date = LocalDate(2026, 12, 21)
        val sunset = assertNotNull(DayPipeline.realSunset(osloPoint, date, oslo))
        assertTrue(sunset >= local(date, 15, 11, oslo) && sunset < local(date, 15, 13, oslo), "Oslo's shortest day: $sunset")
    }
}
