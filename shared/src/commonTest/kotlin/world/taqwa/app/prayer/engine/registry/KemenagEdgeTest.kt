package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.ImsakFrom
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.Kemenag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kemenag's edge beside its kab/kota circles (ruling R211, the audit of 12 Nov 2026, finding F6, and the reviews of
 * 13 Nov 2026). A circle stops short of the nearest other kab/kota (R103), so the ground beyond it is still the unit's
 * own kab/kota on every other side, and its people follow the unit's table, computed at the unit's one point: within a
 * unit's carry the edge may never allow less than the unit does with the sun's own time for the distance on top.
 * Beyond every carry it is the plain published method and claims no table. What the gate proves against Kemenag's
 * tables is restricted data; this holds only the allowances, never a printed minute.
 */
class KemenagEdgeTest {

    private val entries = listOf("id.kemenag", "id.muhammadiyah")

    private fun resolve(entryId: String, at: GeoPoint) =
        Registry.resolveEntry(assertNotNull(Registry.byId(entryId)), Place(at.lat, at.lon, "Asia/Jayapura", "ID"))

    /** [from] moved [km] north and east, near enough for a ring (the distance itself is asserted where it matters). */
    private fun moved(from: GeoPoint, north: Double, east: Double): GeoPoint {
        val perDegree = 111.195
        return GeoPoint(from.lat + north / perDegree, from.lon + east / (perDegree * cos(from.lat * PI / 180)))
    }

    private val compass = listOf(1.0 to 0.0, 0.0 to 1.0, -1.0 to 0.0, 0.0 to -1.0)

    /** The edge's method at each compass point [km] from [unit]'s point that no circle holds. */
    private fun edgesAt(entryId: String, unit: AuthorityUnit, km: Double): List<TimetableMethod> = compass.mapNotNull { (n, e) ->
        val r = resolve(entryId, moved(unit.point, n * km, e * km))
        if (r.unitId != null) return@mapNotNull null
        assertFalse(r.measured, "${unit.id}: the edge claims nothing")
        assertEquals(EntryClass.D_AUTHORITY, r.entryClass, unit.id)
        assertNotNull(r.method)
    }

    /** [edge] allows every start at least [more] seconds beyond [unit]'s own allowance, and each end as much earlier. */
    private fun assertNeverTighter(edge: TimetableMethod, unit: TimetableMethod, more: Int, label: String) {
        assertTrue(edge.margins.dhuhr >= unit.margins.dhuhr + more, "$label dhuhr")
        assertTrue(edge.margins.asr >= unit.margins.asr + more, "$label asr")
        assertTrue(edge.margins.maghrib >= unit.margins.maghrib + more, "$label maghrib")
        assertTrue(edge.margins.isha >= unit.margins.isha + more, "$label isha ${edge.margins.isha} < ${unit.margins.isha} + $more")
        assertTrue(edge.margins.fajr >= unit.margins.fajr + more, "$label fajr")
        assertTrue(edge.margins.sunrise <= unit.margins.sunrise - more, "$label sunrise")
        assertTrue(
            edge.endOfEatingMarginSeconds <= unit.endOfEatingMarginSeconds - more,
            "$label end of eating ${edge.endOfEatingMarginSeconds} > ${unit.endOfEatingMarginSeconds} - $more",
        )
    }

    @Test
    fun `just beyond a circle the edge never allows less than the unit it borders`() {
        for (entryId in entries) {
            var read = 0
            for (unit in assertNotNull(Units.of(entryId)).units) {
                val own = assertNotNull(unit.method)
                for (edge in edgesAt(entryId, unit, unit.radiusKm + 0.3)) {
                    assertNeverTighter(edge, own, 0, "$entryId ${unit.name}")
                    read++
                }
            }
            assertTrue(read > 900, "$entryId: the rings were read ($read points)")
        }
    }

    /** The plain edge of [entryId]: Kemenag's, and Muhammadiyah's with its own Subuh angle. */
    private fun plain(entryId: String) =
        if (entryId == "id.kemenag") Kemenag.outside else Kemenag.outside.copy(id = "id.muhammadiyah.edge", fajrAngle = 18.0)

    /** The units whose own allowance is more than the whole plain edge's somewhere: by rule, not by name. */
    private fun departing(entryId: String): List<AuthorityUnit> {
        val e = plain(entryId)
        return assertNotNull(Units.of(entryId)).units.filter { unit ->
            val m = assertNotNull(unit.method)
            m.margins.fajr > e.margins.fajr || m.margins.dhuhr > e.margins.dhuhr || m.margins.asr > e.margins.asr ||
                m.margins.maghrib > e.margins.maghrib || m.margins.isha > e.margins.isha ||
                m.margins.sunrise < e.margins.sunrise || m.endOfEatingMarginSeconds < e.endOfEatingMarginSeconds
        }
    }

    /** A degree of longitude is four minutes of the sun. */
    private val secondsADegree = 240.0

    /** [unit]'s carry: the distances read, to half a kilometre short of its end. */
    private fun reads(unit: AuthorityUnit) = listOf(unit.radiusKm + 0.3, 30.0, Kemenag.CARRY_KM - 0.5, Kemenag.carryKm(unit.id) - 0.5).distinct()

    @Test
    fun `within its carry the edge allows a unit's table the sun's own time for the distance`() {
        for (entryId in entries) {
            var east = 0
            var west = 0
            for (unit in assertNotNull(Units.of(entryId)).units) {
                val own = assertNotNull(unit.method)
                for (km in reads(unit)) {
                    for ((n, e) in compass) {
                        val at = moved(unit.point, n * km, e * km)
                        val r = resolve(entryId, at)
                        if (r.unitId != null) continue
                        val edge = assertNotNull(r.method)
                        val label = "$entryId ${unit.name} at $km km ($n, $e)"
                        // Later starts and earlier ends only, against the unit and against the plain edge.
                        assertNeverTighter(edge, own, 0, label)
                        assertNeverTighter(edge, plain(entryId), 0, "$label against the plain edge")
                        assertEquals(plain(entryId).id, edge.id)
                        val sun = ((at.lon - unit.point.lon) * secondsADegree).toInt()
                        if (e > 0) {
                            // East of the point the sun comes earlier: every start needs the longitude's time on top.
                            assertTrue(edge.margins.dhuhr >= own.margins.dhuhr + sun, "$label dhuhr")
                            assertTrue(edge.margins.asr >= own.margins.asr + sun, "$label asr")
                            assertTrue(edge.margins.isha >= own.margins.isha + sun, "$label isha ${edge.margins.isha} < ${own.margins.isha} + $sun")
                            if (own.horizonDeg == edge.horizonDeg) assertTrue(edge.margins.maghrib >= own.margins.maghrib + sun, "$label maghrib")
                            east++
                        }
                        if (e < 0) {
                            // West of it the sun comes later: every end needs it.
                            assertTrue(edge.endOfEatingMarginSeconds <= own.endOfEatingMarginSeconds + sun, "$label end of eating")
                            if (own.horizonDeg == edge.horizonDeg) assertTrue(edge.margins.sunrise <= own.margins.sunrise + sun, "$label sunrise")
                            west++
                        }
                    }
                }
            }
            assertTrue(east > 400 && west > 400, "$entryId: the carries were read ($east east, $west west)")
        }
    }

    @Test
    fun `a unit that allows more than the whole edge is carried a degree and never with its earlier Subuh`() {
        for (entryId in entries) {
            val departing = departing(entryId)
            // The audit's two (F6): their Isya and their end of eating depart from the method itself.
            assertTrue(departing.map { it.name }.containsAll(listOf("Kab. Puncak Jaya", "Kab. Lanny Jaya")), "$entryId: ${departing.map { it.name }}")
            assertEquals(departing.map { it.id }.toSet(), Kemenag.departing.toSet(), "$entryId: the engine's own list")
            for (unit in departing) {
                assertTrue(Kemenag.carryKm(unit.id) >= 2 * Kemenag.CARRY_KM, "${unit.name}: carried ${Kemenag.carryKm(unit.id)} km")
                val own = assertNotNull(unit.method)
                for (km in reads(unit)) {
                    val edges = edgesAt(entryId, unit, km)
                    assertTrue(edges.isNotEmpty(), "${unit.name} at $km km")
                    for (edge in edges) {
                        assertTrue(edge.margins.isha >= own.margins.isha, "${unit.name} at $km km: isha")
                        assertTrue(edge.endOfEatingMarginSeconds <= own.endOfEatingMarginSeconds, "${unit.name} at $km km: end of eating")
                        assertEquals(plain(entryId).margins.fajr, edge.margins.fajr, "${unit.name}: the edge's own Fajr stays")
                    }
                }
            }
            for (unit in assertNotNull(Units.of(entryId)).units - departing.toSet()) {
                assertEquals(Kemenag.CARRY_KM, Kemenag.carryKm(unit.id), unit.name)
            }
        }
    }

    @Test
    fun `on a unit's far ground the edge carries its table`() {
        assertTrue(Kemenag.farGrounds.isNotEmpty())
        for (entryId in entries) {
            val units = assertNotNull(Units.of(entryId))
            for (ground in Kemenag.farGrounds) {
                val unit = units.unit(ground.unit)
                val own = assertNotNull(unit.method)
                assertTrue(distanceKm(ground.point, unit.point) > Kemenag.carryKm(unit.id), "${ground.name} lies within ${unit.name}'s carry already")
                val north = kotlin.math.abs(ground.point.lat - unit.point.lat)
                for ((n, e) in compass + (0.0 to 0.0)) {
                    val at = moved(ground.point, n * (ground.radiusKm - 0.5), e * (ground.radiusKm - 0.5))
                    val r = resolve(entryId, at)
                    assertNull(r.unitId, "${ground.name}: a circle holds it")
                    val edge = assertNotNull(r.method)
                    assertNeverTighter(edge, own, 0, "$entryId ${ground.name}")
                    assertNeverTighter(edge, plain(entryId), 0, "$entryId ${ground.name} against the plain edge")
                    // A ground this far north or south of the point: the latitude moves Asar and the twilights.
                    if (north > 1.0) {
                        assertTrue(edge.margins.asr > plain(entryId).margins.asr, "${ground.name}: asr")
                        assertTrue(edge.margins.isha > plain(entryId).margins.isha, "${ground.name}: isha")
                        assertTrue(edge.endOfEatingMarginSeconds < plain(entryId).endOfEatingMarginSeconds, "${ground.name}: end of eating")
                    }
                }
            }
        }
    }

    @Test
    fun `beyond every carry and every far ground the edge is the plain one`() {
        for (entryId in entries) {
            val units = assertNotNull(Units.of(entryId)).units
            fun beyondEveryCarry(at: GeoPoint) = units.all { distanceKm(at, it.point) > Kemenag.carryKm(it.id) } &&
                Kemenag.farGrounds.all { distanceKm(at, it.point) > it.radiusKm }
            var past = 0
            for (unit in units) {
                val km = Kemenag.carryKm(unit.id) + 1.0
                for ((n, e) in compass) {
                    val at = moved(unit.point, n * km, e * km)
                    val r = resolve(entryId, at)
                    if (r.unitId != null || !beyondEveryCarry(at)) continue
                    assertEquals(plain(entryId), r.method, "${unit.name}: $km km out")
                    past++
                }
            }
            assertTrue(past > 100, "$entryId: points just past a carry were read ($past)")
        }
    }

    @Test
    fun `the edge is the user's own sky at the deepest horizon everywhere`() {
        for (entryId in entries) {
            for (unit in assertNotNull(Units.of(entryId)).units) {
                for (edge in edgesAt(entryId, unit, unit.radiusKm + 0.3)) {
                    assertNull(edge.fixedPoint, unit.name)
                    assertEquals(-2.0, edge.horizonDeg, unit.name)
                    assertEquals(ImsakFrom.Dawn, edge.imsakFrom, unit.name)
                }
            }
        }
    }
}
