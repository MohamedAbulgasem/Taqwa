package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The city-point round of 11 Oct 2026, under the owner's rule "rather late, never early": a place is never before any
 * table printed for its own point whose reach holds it, nor any end after one (the envelope), BIK's printed town figures,
 * the nearest Habous table's ends, Diyanet's Sydney table at every plausible point, and a named table followed wherever
 * it is chosen.
 */
class CityPointsEnvelopeTest {
    private val dates = (1..12).flatMap { m -> listOf(LocalDate(2026, m, 3), LocalDate(2026, m, 18)) }

    private fun at(entryId: String, lat: Double, lon: Double, zone: String, cc: String) =
        Registry.resolveEntry(Registry.byId(entryId)!!, Place(lat, lon, zone, cc))

    private fun day(method: TimetableMethod, point: GeoPoint, date: LocalDate, zone: String): PrayerDay =
        DayComputer.compute(method, point, date, TimeZone.of(zone), AsrSchool.STANDARD, Registry.ramadanCalendar())

    /** [shown] never before [table] and no end after it. */
    private fun assertBounded(shown: PrayerDay, table: PrayerDay, what: String) {
        assertTrue(shown.fajr >= table.fajr && shown.dhuhr >= table.dhuhr && shown.asr >= table.asr, "$what: a start before")
        assertTrue(shown.maghrib >= table.maghrib && shown.isha >= table.isha, "$what: a start before")
        assertTrue(shown.sunrise <= table.sunrise && shown.endOfEating <= table.endOfEating, "$what: an end after")
    }

    @Test
    fun `ottawa rides every mosque table whose reach holds it and is never before Abrar's or Kanata's`() {
        val zone = "America/Toronto"
        val ottawa = GeoPoint(45.41117, -75.69812)
        val r = at("ca.isna", ottawa.lat, ottawa.lon, zone, "CA")
        assertEquals("ottawa-south", r.unitId)
        val method = assertNotNull(r.method)
        val abrar = GeoPoint(45.3516, -75.7955)
        val kanata = GeoPoint(45.3561, -75.9284)
        assertTrue(abrar in method.envelopePoints && kanata in method.envelopePoints, "${method.envelopePoints}")
        for (table in listOf(abrar, kanata)) {
            val alone = at("ca.isna", table.lat, table.lon, zone, "CA").method!!.copy(envelopePoints = emptyList())
            for (date in dates) assertBounded(day(method, ottawa, date, zone), day(alone.copy(fixedPoint = null), table, date, zone), "$table $date")
        }
    }

    @Test
    fun `just beyond a mosque's minute the nearest table still rides and no figure is claimed`() {
        // North-east Calgary (Saddle Ridge), 19 km from Calgary Islamic Centre SW, beyond its minute's reach.
        val units = assertNotNull(Units.of("ca.isha90"))
        val saddleRidge = Place(51.13, -113.95, "America/Edmonton", "CA")
        assertNull(units.unitFor(saddleRidge))
        val edge = units.outside(GeoPoint(saddleRidge.lat, saddleRidge.lon))
        assertEquals(GeoPoint(51.0395, -114.1685), edge.fixedPoint)
        assertEquals(FixedPointMode.BOTH, edge.fixedPointMode)
        assertTrue(edge.endsBoundAtUserPoint)
        // Beyond every table's bound reach, the method alone, as before.
        assertNull(units.outside(GeoPoint(52.27, -113.81)).fixedPoint)
    }

    @Test
    fun `kosovo carries BIK's own town figures and the Takvim rides everywhere in Kosovo`() {
        val zone = "Europe/Belgrade"
        val dragash = at("xk.bik", 42.0265, 20.65333, zone, "XK").method!!
        assertEquals(listOf(GeoPoint(42.5, 21.0 - 2.04 * 0.25)), dragash.startPoints)
        val prishtina = at("xk.bik", 42.67272, 21.16688, zone, "XK").method!!
        assertEquals(listOf(GeoPoint(42.5, 21.0 + 1.04 * 0.25)), prishtina.endPoints)
        assertTrue(prishtina.startPoints.isEmpty())
        val peja = at("xk.bik", 42.65914, 20.28828, zone, "XK").method!!
        assertTrue(peja.startPoints.isEmpty() && peja.endPoints.isEmpty())
        // Leposaviq, beyond the base unit: the base still rides, with no figure.
        val leposaviq = at("xk.bik", 43.10389, 20.80278, zone, "XK")
        assertNull(leposaviq.unitId)
        assertEquals(GeoPoint(42.5, 21.0), leposaviq.method!!.fixedPoint)
        // Sharri's table is the Takvim plus two minutes: never before it at Dragash.
        val base = at("xk.bik", 42.5, 21.0, zone, "XK").method!!.copy(startPoints = emptyList(), endPoints = emptyList())
        for (date in dates) {
            val shown = day(dragash, GeoPoint(42.0265, 20.65333), date, zone)
            val takvim = day(base, GeoPoint(42.5, 21.0), date, zone)
            assertTrue(shown.fajr >= takvim.fajr.plus(2.minutes), "$date fajr")
            assertTrue(shown.isha >= takvim.isha.plus(2.minutes), "$date isha")
        }
    }

    @Test
    fun `sidi taibi in Rabat's unit ends no later than Kenitra's table too`() {
        val r = at("ma.habous", 34.18698, -6.68176, "Africa/Casablanca", "MA")
        assertEquals("rabat", r.unitId)
        assertEquals(listOf(GeoPoint(34.26101, -6.5802)), r.method!!.endPoints)
        assertTrue(r.method!!.startPoints.isEmpty())
    }

    @Test
    fun `a named table chosen beyond its area rides its point for every start and end`() {
        val basildon = at("gb.london.lupt", 51.56844, 0.45782, "Europe/London", "GB")
        assertNull(basildon.unitName)
        assertEquals(FixedPointMode.BOTH, basildon.method!!.fixedPointMode)
        assertNotNull(basildon.method!!.fixedPoint)
        val sharjah = at("ae.iacad.dubai", 25.3463, 55.4209, "Asia/Dubai", "AE")
        assertNull(sharjah.unitName)
        assertEquals(GeoPoint(25.07725, 55.30927), sharjah.method!!.fixedPoint)
        assertEquals(FixedPointMode.BOTH, sharjah.method!!.fixedPointMode)
    }

    @Test
    fun `diyanet's sydney table rides every plausible point of its near Sydney only`() {
        val fairfield = Registry.resolve(Place(-33.8723, 150.9561, "Australia/Sydney", "AU"))
        val turkey = fairfield.members.first { it.id == "other.turkey" }.method
        assertNotNull(turkey.fixedPoint)
        assertEquals(2, turkey.envelopePoints.size)
        val melbourne = Registry.resolve(Place(-37.8136, 144.9631, "Australia/Melbourne", "AU"))
        assertNull(melbourne.members.first { it.id == "other.turkey" }.method.fixedPoint)
    }
}
