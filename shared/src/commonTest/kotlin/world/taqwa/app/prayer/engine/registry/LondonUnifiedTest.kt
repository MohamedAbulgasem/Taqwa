package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `outside the m25 the table's point still bounds the ends nearby`() {
        val luton = Registry.resolveEntry(Registry.byId("gb.london.lupt")!!, Place(51.87967, -0.41748, "Europe/London", "GB"))
        assertNull(luton.unitName)
        val method = luton.method!!
        assertEquals(point, method.fixedPoint)
        assertEquals(FixedPointMode.ENDS_ONLY, method.fixedPointMode)
        assertTrue(method.endOfEatingMarginSeconds <= SAFE_END)
        // Far from London (Manchester) the user's own point alone.
        val manchester = Registry.resolveEntry(Registry.byId("gb.london.lupt")!!, Place(53.48095, -2.23743, "Europe/London", "GB"))
        assertNull(manchester.method!!.fixedPoint)
    }
}
