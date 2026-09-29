package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.curveSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Diyanet's European city tables after the monitor round (brief D): the seven units it added, their
 * reach against their neighbours', and Trondheim's winter rule. No printed time here (ruling R69):
 * the figures are the engine's own at run time, compared with each other.
 */
class DiyanetEuropeUnitsTest {
    private val units = assertNotNull(Units.of("tr.diyanet.europe"))

    private fun unitAt(lat: Double, lon: Double, zone: String, cc: String) = units.unitFor(Place(lat, lon, zone, cc))

    @Test
    fun `the seven cities the monitor round added are units at their own points`() {
        for ((key, point) in listOf(
            "lyon" to GeoPoint(45.764, 4.8357), "lille" to GeoPoint(50.6292, 3.0573), "gent" to GeoPoint(51.0543, 3.7174),
            "antwerpen" to GeoPoint(51.2194, 4.4025), "copenhagen" to GeoPoint(55.6761, 12.5683),
            "helsinki" to GeoPoint(60.1699, 24.9384), "trondheim" to GeoPoint(63.43049, 10.39506),
        )) {
            val unit = units.unit("tr.diyanet.europe.$key")
            assertEquals(point, unit.point, key)
            assertEquals(unit, units.nearest(point), "$key resolves to its own unit at its point")
            val method = assertNotNull(unit.method, key)
            assertNotNull(method.fajrAngleByDayOfYear, "$key has its own curves")
        }
    }

    @Test
    fun `antwerpen and gent stand inside brussels' reach and the nearer table wins between them`() {
        val brussels = units.unit("tr.diyanet.europe.brussels")
        val antwerpen = units.unit("tr.diyanet.europe.antwerpen")
        val gent = units.unit("tr.diyanet.europe.gent")
        assertTrue(distanceKm(brussels.point, antwerpen.point) < brussels.radiusKm, "Antwerpen is within Brussels' reach")
        assertTrue(distanceKm(brussels.point, gent.point) < brussels.radiusKm, "Gent is within Brussels' reach")
        // Sint-Niklaas, 19 km from Antwerpen and 36 from Brussels; Mechelen, 21 km from Brussels and 22 from
        // Antwerpen; Aalst, 24 km from Brussels and 26 from Gent.
        assertEquals(antwerpen, unitAt(51.1667, 4.1433, "Europe/Brussels", "BE"))
        assertEquals(brussels, unitAt(51.0259, 4.4776, "Europe/Brussels", "BE"))
        assertEquals(brussels, unitAt(50.9378, 4.0405, "Europe/Brussels", "BE"))
        // Kortrijk, west of Gent: Lille's reach (its own point 30 km away) before Gent's (40 km).
        assertEquals(units.unit("tr.diyanet.europe.lille"), unitAt(50.8279, 3.2649, "Europe/Brussels", "BE"))
    }

    @Test
    fun `beyond every table the edge keeps the generic method`() {
        // Ålesund, 230 km from Bergen and Trondheim; Hamburg, between Berlin and Copenhagen.
        assertNull(unitAt(62.47225, 6.15492, "Europe/Oslo", "NO"))
        assertNull(unitAt(53.55073, 9.99302, "Europe/Berlin", "DE"))
        val alesund = Registry.resolveEntry(Registry.byId("tr.diyanet.europe")!!, Place(62.47225, 6.15492, "Europe/Oslo", "NO"))
        assertEquals("tr.diyanet.europe.edge", assertNotNull(alesund.method).id)
    }

    @Test
    fun `trondheim holds its december day to five hours around dhuhr and its summer to the sun`() {
        val entry = Registry.byId("tr.diyanet.europe")!!
        val trondheim = Place(63.43049, 10.39506, "Europe/Oslo", "NO")
        val winter = DayPipeline.day(entry, trondheim, LocalDate(2027, 12, 21))
        assertTrue(winter.maghrib - winter.dhuhr >= 150.minutes && winter.maghrib - winter.dhuhr <= 152.minutes, "afternoon ${winter.maghrib - winter.dhuhr}")
        assertTrue(winter.dhuhr - winter.sunrise >= 150.minutes && winter.dhuhr - winter.sunrise <= 152.minutes, "morning ${winter.dhuhr - winter.sunrise}")
        assertTrue(winter.maghrib >= winter.sunset)
        // In June the sun's own day is far longer than five hours and the rule is silent: Maghrib is the sun's + 7.
        val summer = DayPipeline.day(entry, trondheim, LocalDate(2027, 6, 21))
        assertTrue(summer.maghrib - summer.sunset >= 7.minutes && summer.maghrib - summer.sunset <= 8.minutes, "Maghrib ${summer.maghrib - summer.sunset} after sunset")
        assertTrue(summer.fajr < summer.sunrise && summer.maghrib < summer.isha)
        // Its June Fajr follows its own table's curve, hours after 18°, which the sun never reaches there.
        val june = assertNotNull(units.unit("tr.diyanet.europe.trondheim").method).fajrAngleByDayOfYear!![curveSlot(LocalDate(2027, 6, 21))]
        assertTrue(june < 3.0, "Trondheim's solstice Fajr depression $june")
    }

    @Test
    fun `no unit's own point falls inside another unit's nearer reach`() {
        for (unit in units.units) {
            assertEquals(unit, units.nearest(unit.point), unit.id)
        }
        assertEquals(33, units.units.size)
        assertEquals(TimeZone.of("Europe/Oslo").id, "Europe/Oslo")
    }
}
