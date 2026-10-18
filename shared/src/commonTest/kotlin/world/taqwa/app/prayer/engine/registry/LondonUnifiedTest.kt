package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Europe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ruling R44: the whole M25 follows the one LUPT table, computed at its point. */
class LondonUnifiedTest {
    private val point = GeoPoint(51.5074, -0.1278)
    private val zone = TimeZone.of("Europe/London")
    private val dates = (1..12).flatMap { m -> listOf(LocalDate(2026, m, 1), LocalDate(2026, m, 15)) }

    private fun resolve(lat: Double, lon: Double) = Registry.resolve(Place(lat, lon, "Europe/London", "GB"))

    private fun day(r: Resolution, date: LocalDate): PrayerDay =
        DayComputer.compute(r.method!!, r.point, date, zone, AsrSchool.HANAFI, Registry.ramadanCalendarFor(r.entry))

    private fun PrayerDay.starts() = listOf(fajr, dhuhr, asr, maghrib, isha)

    @Test
    fun `east london is never before the table and hounslow never ends after it`() {
        val table = resolve(point.lat, point.lon)
        val romford = resolve(51.57515, 0.18582)
        val ilford = resolve(51.55765, 0.07278)
        val hounslow = resolve(51.46839, -0.36092)
        for (r in listOf(table, romford, ilford, hounslow)) {
            assertEquals("gb.london.lupt", r.entry.id)
            assertEquals("London (M25)", r.unitName)
            assertEquals(point, r.method!!.fixedPoint)
            assertEquals(FixedPointMode.BOTH, r.method!!.fixedPointMode)
        }
        for (date in dates) {
            val atTable = day(table, date)
            for (east in listOf(romford, ilford)) {
                day(east, date).starts().zip(atTable.starts()).forEach { (shown, printed) -> assertTrue(shown >= printed, "$date") }
            }
            val west = day(hounslow, date)
            assertTrue(west.sunrise <= atTable.sunrise && west.endOfEating <= atTable.endOfEating, "$date")
        }
    }

    @Test
    fun `the lateness either side of the point is a recorded exception for its own events`() {
        val entry = Registry.byId("gb.london.lupt")!!
        val m25 = Units.of("gb.london.lupt")!!.units.single()
        // Task 7g measured them on the 2026 table at the M25's edges and corners.
        val expected = mapOf(
            TimedEvent.FAJR to 5, TimedEvent.ISHA to 5, TimedEvent.ASR to 3, TimedEvent.MAGHRIB to 3,
            TimedEvent.SUNRISE to 3, TimedEvent.END_OF_EATING to 4,
        )
        for ((event, minutes) in expected) assertEquals(minutes, assertNotNull(lateLimitFor(event, m25, entry), "$event").minutes)
        // Dhuhr stays within class B's 2 min everywhere in the M25; there is no imsak.
        assertNull(lateLimitFor(TimedEvent.DHUHR, m25, entry))
        assertNull(lateLimitFor(TimedEvent.IMSAK, m25, entry))
    }

    @Test
    fun `outside the m25 a user who chooses the table follows it nearby for every start and end`() {
        // The city-point round of 11 Oct 2026: within R45's three reaches the table's point rides both ways.
        val luton = Registry.resolveEntry(Registry.byId("gb.london.lupt")!!, Place(51.87967, -0.41748, "Europe/London", "GB"))
        assertNull(luton.unitName)
        val method = luton.method!!
        assertEquals(point, method.fixedPoint)
        assertEquals(FixedPointMode.BOTH, method.fixedPointMode)
        assertTrue(method.endOfEatingMarginSeconds <= SAFE_END)
    }

    @Test
    fun `chosen it applies within R45's three reaches of its point and nowhere beyond`() {
        // The owner's decision of 17 Oct 2026: beyond about 104 km London Unified is out of scope, as any timetable is
        // outside its area, so Automatic applies there (round 2's fix had ridden London's point as far as Scotland).
        val entry = Registry.byId("gb.london.lupt")!!
        assertEquals(point, Europe.londonArea.center)
        assertEquals(lateReachKm(point.lat, entry.entryClass) * ENDS_REACH_FACTOR, Europe.londonArea.radiusKm)
        assertEquals(Europe.londonArea, entry.area)
        fun gb(lat: Double, lon: Double) = Place(lat, lon, "Europe/London", "GB")
        // The app's Basildon, Sevenoaks, St Albans, Luton, Reading and Folkestone (101.7 km), and 102.6 km due north.
        val within = listOf(
            gb(51.56844, 0.45782), gb(51.27266, 0.18883), gb(51.75, -0.33333), gb(51.87967, -0.41748), gb(51.45625, -0.97113),
            gb(51.08169, 1.16734), gb(52.43, -0.1278),
        )
        for (place in within) {
            assertTrue(Registry.inScope(entry, place), "${place.lat}, ${place.lon}")
            val method = Registry.resolveEntry(entry, place).method!!
            assertEquals(point, method.fixedPoint)
            assertEquals(FixedPointMode.BOTH, method.fixedPointMode)
        }
        // 105.9 km due north; the app's Margate, Ipswich, Dover, Southampton, Birmingham, Manchester, Glasgow, Edinburgh,
        // Belfast and Penzance.
        val beyond = listOf(
            gb(52.46, -0.1278), gb(51.38132, 1.38617), gb(52.05917, 1.15545), gb(51.12598, 1.31257), gb(50.90395, -1.40428),
            gb(52.48142, -1.89983), gb(53.48095, -2.23743), gb(55.86515, -4.25763), gb(55.95206, -3.19648),
            gb(54.59682, -5.92541), gb(50.11861, -5.53715),
        )
        for (place in beyond) assertFalse(Registry.inScope(entry, place), "${place.lat}, ${place.lon}")
        // Inside the M25 it is Automatic itself.
        assertTrue(Registry.inScope(entry, gb(point.lat, point.lon)))
    }

    @Test
    fun `resolved beyond its area it is its own rule at the user's own sun`() {
        // Not what the app shows there (Automatic applies, Registry.inScope), but what a direct resolution gives: R45's edge
        // at the user's own point alone, with no table's point beside it.
        val glasgow = Registry.resolveEntry(Registry.byId("gb.london.lupt")!!, Place(55.86515, -4.25763, "Europe/London", "GB"))
        val method = glasgow.method!!
        assertNull(method.fixedPoint)
        assertTrue(method.startPoints.isEmpty() && method.endPoints.isEmpty() && method.envelopePoints.isEmpty())
        assertFalse(glasgow.measured)
    }
}
