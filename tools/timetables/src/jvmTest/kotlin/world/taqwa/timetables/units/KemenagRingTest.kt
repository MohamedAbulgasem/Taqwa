package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.authorities.Kemenag
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.GateRow
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Split
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ground beyond Kemenag's kab/kota circles (ruling R211, the audit of 12 Nov 2026, finding F6: beyond Kab. Puncak
 * Jaya's and Kab. Lanny Jaya's circles the edge showed Isya up to 4 min before their own tables and ended eating up to
 * 4 min after; the reviews of 13 Nov 2026: so it did again past a flat 55 km, and other units' Asar came a minute early
 * from 50 km). A circle stops short of the nearest other kab/kota (R103), so the ground beyond it is, on every other
 * side, still the unit's own, and its people follow the unit's table.
 *
 * With the archive, on grids offset from the gate rows', the edge is never before the unit's own table at any start,
 * nor any end after it:
 * - on a ring just outside every circle;
 * - around every unit out to the carry's end (`Kemenag.carryKm`): 55 km, and a degree around the units whose print
 *   departs from the method (`Kemenag.departing`);
 * - on every listed far ground (`Kemenag.farGrounds`).
 *
 * And the gate holds it: the committed envelope rows are every point the generator writes ([KemenagRings.gateRings],
 * [KemenagRings.farPoints]).
 *
 * That is all this claims: beyond a unit's carry no table is read against the edge, which is the plain published
 * method there. What is checked against Kemenag's tables is restricted data: this holds the rule alone, never a
 * printed minute.
 */
class KemenagRingTest {
    private val gateDir = TestPaths.repoRoot.resolve("tools/timetables/official/gate")
    private val manifest = GateManifest.load(gateDir).only(groups = setOf("id-kemenag"))
    private val spec = ReachRows.SPECS.single { it.entry == KemenagRings.ENTRY }

    private fun roots(): OfficialRoots? {
        val official = System.getProperty("taqwa.official")?.takeIf { it.isNotBlank() } ?: return null
        return OfficialRoots(File(official), TestPaths.repoRoot.resolve("tools/timetables/official")).takeIf { it.held }
    }

    /** Between the gate rows' bearings (every 45° from north): every 45° from 22.5°. */
    private val between: List<Double> = (0 until 8).map { 22.5 + 45.0 * it }

    /** Every 22.5° from 11.25°: off the gate rows' bearings and off [between]. */
    private val sixteen: List<Double> = (0 until 16).map { 11.25 + 22.5 * it }

    /** [unit]'s own tables replayed at [points] as the edge shows them: what was early or ended late, by point. */
    private fun broken(roots: OfficialRoots, unit: AuthorityUnit, own: List<GateRow>, points: List<KemenagRings.Point>): List<String> =
        points.mapNotNull { p ->
            val rows = own.map {
                it.copy(source = "id-kemenag${GateRow.PROMISE_ONLY_SUFFIX}", lat = p.at.lat, lon = p.at.lon, unit = null, split = Split.TEST)
            }
            val stats = requireNotNull(Gate(roots).evaluate(GateManifest(rows)).entries[KemenagRings.ENTRY]) { "${unit.id}: nothing was checked" }
            val bad = stats.events.filter { it.value.early + it.value.lateEnd > 0 }
            if (bad.isEmpty()) return@mapNotNull null
            "${unit.name} ${"%.1f".format(p.km)} km at ${p.bearing}: " +
                bad.entries.joinToString { "${it.key.key} early ${it.value.early}, late end ${it.value.lateEnd}, worst ${it.value.worstEarly} min" }
        }

    @Test
    fun `just outside every circle the edge is never before the unit's own table nor any end after it`() {
        val roots = roots() ?: return
        val units = KemenagRings.units()
        val read = java.util.concurrent.atomic.AtomicInteger()
        val failures = units.parallelStream().map { unit ->
            val own = ReachRows.ownRows(manifest, spec, unit)
            val points = KemenagRings.points(unit, own.first().zone, bearings = between)
            read.addAndGet(points.size)
            broken(roots, unit, own, points)
        }.toList().flatten()
        assertTrue(read.get() > units.size * 6, "the rings were read: ${read.get()} points around ${units.size} circles")
        assertEquals(emptyList(), failures, "the edge just outside a circle against the unit's own table")
    }

    @Test
    fun `around every unit the edge holds its table to the carry's end`() {
        val roots = roots() ?: return
        val units = KemenagRings.units()
        val read = java.util.concurrent.atomic.AtomicInteger()
        val failures = units.parallelStream().map { unit ->
            val own = ReachRows.ownRows(manifest, spec, unit)
            val end = Kemenag.carryKm(unit.id) - 0.2
            val points = KemenagRings.pointsAt(unit, own.first().zone, listOf(end), sixteen) +
                KemenagRings.pointsAt(unit, own.first().zone, listOf((unit.radiusKm + end) / 2, end * 0.85), between)
            read.addAndGet(points.size)
            broken(roots, unit, own, points)
        }.toList().flatten()
        assertTrue(read.get() > units.size * 16, "the rings were read: ${read.get()} points around ${units.size} units")
        assertEquals(emptyList(), failures, "the edge within a unit's carry against the unit's own table")
    }

    @Test
    fun `around a unit whose print departs from the method the edge holds its table for a degree`() {
        val roots = roots() ?: return
        val departing = KemenagRings.departing()
        assertTrue(departing.isNotEmpty(), "no unit departs")
        val failures = departing.parallelStream().map { unit ->
            val own = ReachRows.ownRows(manifest, spec, unit)
            val end = Kemenag.carryKm(unit.id) - 0.2
            assertTrue(end > Kemenag.CARRY_KM, "${unit.name}: carried no further than any unit")
            val km = listOf(unit.radiusKm + 2.0, end * 0.25, end * 0.4, end * 0.6, end * 0.75, end * 0.9, end)
            val points = KemenagRings.pointsAt(unit, own.first().zone, km, (0 until 24).map { 7.5 + 15.0 * it })
            assertTrue(points.size > 120, "${unit.name}: ${points.size} points")
            broken(roots, unit, own, points)
        }.toList().flatten()
        assertEquals(emptyList(), failures, "the edge around a departing unit against the unit's own table")
    }

    @Test
    fun `on a unit's far ground the edge holds its table`() {
        val roots = roots() ?: return
        assertTrue(Kemenag.farGrounds.isNotEmpty(), "no far ground is listed")
        val failures = Kemenag.farGrounds.flatMap { ground ->
            val unit = KemenagRings.units().single { it.id == ground.unit }
            val own = ReachRows.ownRows(manifest, spec, unit)
            val zone = own.first().zone
            val here = AuthorityUnit(unit.id, ground.name, ground.point, 0.0)
            val points = KemenagRings.pointsAt(here, zone, listOf(0.0), listOf(0.0)) +
                KemenagRings.pointsAt(here, zone, listOf(ground.radiusKm / 2, ground.radiusKm - 0.2), sixteen)
            assertTrue(points.size > 16, "${ground.name}: ${points.size} points")
            broken(roots, unit, own, points)
        }
        assertEquals(emptyList(), failures, "the edge on a far ground against its unit's own table")
    }

    @Test
    fun `the gate holds every unit's carry and every far ground`() {
        val committed = gateDir.resolve("id-kemenag${GateRow.PROMISE_ONLY_SUFFIX}")
        assertTrue(committed.isFile, "${committed.name} is not committed: run generateEnvelopeRows")
        val rows = GateManifest.parse(committed.name, committed.readText()).rows
        val held = rows.filter { it.entry == KemenagRings.ENTRY && it.unit == null }.groupBy { it.lat to it.lon }
        var points = 0
        fun holds(own: List<GateRow>, p: KemenagRings.Point, label: String) {
            assertEquals(own.map { it.path }.toSet(), held[p.at.lat to p.at.lon].orEmpty().map { it.path }.toSet(), label)
            points++
        }
        var ringed = 0
        for (unit in KemenagRings.units()) {
            val own = ReachRows.ownRows(manifest, spec, unit)
            val ring = KemenagRings.gateRings(unit, own.first().zone)
            if (unit.id in Kemenag.departing) assertTrue(ring.size >= 32, "${unit.name}: ${ring.size} ring points")
            if (ring.isNotEmpty()) ringed++
            for (p in ring) holds(own, p, "${unit.name} at ${p.km} km, bearing ${p.bearing}")
        }
        // A unit hemmed in by other circles on every diagonal has no point of its own; nearly every unit has one.
        assertTrue(ringed > KemenagRings.units().size * 9 / 10, "$ringed units have a ring point")
        for (ground in Kemenag.farGrounds) {
            val unit = KemenagRings.units().single { it.id == ground.unit }
            val own = ReachRows.ownRows(manifest, spec, unit)
            val far = KemenagRings.farPoints(ground, own.first().zone)
            assertTrue(far.size >= 5, "${ground.name}: ${far.size} points")
            for (p in far) holds(own, p, "${ground.name}, bearing ${p.bearing}")
        }
        assertEquals(points, held.size, "rows at a point the generator no longer writes: run generateEnvelopeRows")
    }
}
