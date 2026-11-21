package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Kemenag
import java.util.Locale

/**
 * The ground beyond Kemenag's kab/kota circles where the edge carries a unit's own table (ruling R211, the audit of
 * 12 Nov 2026, finding F6, and the reviews of 13 Nov 2026). A circle stops half a kilometre short of the nearest other
 * kab/kota's boundary (R103), so the place beyond it is, on every other side, still the unit's own kab/kota, and its
 * people follow the unit's table. `Kemenag.outsideAt` carries each unit's allowance, with what the distance needs, as
 * far as `Kemenag.carryKm` from its point and on its listed far grounds; what is read against the unit's own tables is
 * exactly that reach, no further: beyond it the edge is the plain published method and claims no table.
 *
 * [points] and [pointsAt] give where a table is read; [gateRings] and [farPoints], the points the gate holds
 * (gate/id-kemenag-envelope.tsv, written by `generateEnvelopeRows`). Which units depart from the method and how far
 * each is carried are the engine's own (`Kemenag.departing`, `Kemenag.carryKm`, `Kemenag.farGrounds`): nothing here
 * repeats the rule.
 */
object KemenagRings {
    const val ENTRY = "id.kemenag"

    /** Just outside a circle: this far beyond its radius. */
    const val BEYOND_KM = 0.3

    /** Short of a carry's end by this much, so that a point rounded to six decimals is still inside it. */
    const val SHORT_KM = 0.5

    /** A point read against [unit]'s table: where, how far from the unit's point, and at what bearing from it. */
    class Point(val at: GeoPoint, val km: Double, val bearing: Double)

    /**
     * The points [beyondKm] beyond [unit]'s circle at [bearings] that no unit takes once rounded to six decimals (a
     * point inside another circle follows that kab/kota's table, and is its reach rows' to hold).
     */
    fun points(unit: AuthorityUnit, zone: String, beyondKm: List<Double> = listOf(BEYOND_KM), bearings: List<Double> = ReachRows.BEARINGS): List<Point> =
        pointsAt(unit, zone, beyondKm.map { unit.radiusKm + it }, bearings)

    /** The points [km] from [unit]'s own point at [bearings] that no unit takes once rounded to six decimals. */
    fun pointsAt(unit: AuthorityUnit, zone: String, km: List<Double>, bearings: List<Double> = ReachRows.BEARINGS): List<Point> =
        km.flatMap { d ->
            bearings.map { b ->
                val at = QmdbReach.moved(unit.point, d, b)
                Point(GeoPoint(round6(at.lat), round6(at.lon)), d, b)
            }
        }.filter { onTheEdge(it.at, zone) }

    private fun onTheEdge(at: GeoPoint, zone: String): Boolean =
        Registry.resolveEntry(requireNotNull(Registry.byId(ENTRY)), Place(at.lat, at.lon, zone, "ID")).unitId == null

    fun units(): List<AuthorityUnit> = requireNotNull(Units.of(ENTRY)) { "no units for $ENTRY" }.units

    /** The units whose own allowance is more than the whole plain edge's somewhere: the engine's own list. */
    fun departing(): List<AuthorityUnit> = units().filter { it.id in Kemenag.departing }

    /** The diagonals, where both the longitude and the latitude tell. */
    val DIAGONALS: List<Double> = listOf(45.0, 135.0, 225.0, 315.0)

    /**
     * Where the gate reads [unit]'s table beyond its circle. Every unit: at the four diagonals half a kilometre short of
     * its carry's end. A departing unit, at the reach rows' eight bearings: just outside the circle, at 35 km, short of
     * the common carry's end, midway from there to its own carry's end, and short of that.
     */
    fun gateRings(unit: AuthorityUnit, zone: String): List<Point> {
        val end = Kemenag.carryKm(unit.id) - SHORT_KM
        if (unit.id !in Kemenag.departing) return pointsAt(unit, zone, listOf(end), DIAGONALS)
        val common = Kemenag.CARRY_KM - SHORT_KM
        return pointsAt(unit, zone, listOf(unit.radiusKm + BEYOND_KM, 35.0, common, (common + end) / 2, end))
    }

    /**
     * Where the gate reads a unit's table on one of its far grounds: at the ground's own point and on a ring short of
     * its edge, wherever no circle holds the point.
     */
    fun farPoints(ground: Kemenag.FarGround, zone: String): List<Point> {
        val unit = units().single { it.id == ground.unit }
        val ring = ReachRows.BEARINGS.map { b -> b to QmdbReach.moved(ground.point, ground.radiusKm - SHORT_KM, b) }
        return (listOf(Double.NaN to ground.point) + ring)
            .map { (b, at) -> Point(GeoPoint(round6(at.lat), round6(at.lon)), distanceKm(unit.point, at), b) }
            .filter { onTheEdge(it.at, zone) }
    }

    private fun distanceKm(a: GeoPoint, b: GeoPoint) = world.taqwa.app.prayer.engine.registry.distanceKm(a, b)

    private fun round6(x: Double): Double = String.format(Locale.ROOT, "%.6f", x).toDouble()
}
