package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.Americas
import world.taqwa.app.prayer.engine.registry.authorities.Balkans
import world.taqwa.app.prayer.engine.registry.authorities.CentralAsia
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.app.prayer.engine.registry.authorities.Egypt
import world.taqwa.app.prayer.engine.registry.authorities.Europe
import world.taqwa.app.prayer.engine.registry.authorities.Gulf
import world.taqwa.app.prayer.engine.registry.authorities.Jakim
import world.taqwa.app.prayer.engine.registry.authorities.Kemenag
import world.taqwa.app.prayer.engine.registry.authorities.Levant
import world.taqwa.app.prayer.engine.registry.authorities.Maghreb
import world.taqwa.app.prayer.engine.registry.authorities.Muis
import world.taqwa.app.prayer.engine.registry.authorities.Russia
import world.taqwa.app.prayer.engine.registry.authorities.SouthAfrica
import world.taqwa.app.prayer.engine.registry.authorities.UmmAlQura
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One of an authority's units (a JAKIM zone, a Kemenag kab/kota, a Diyanet ilçe, a Habous city, an
 * Algerian wilaya seat …): its published or fitted reference [point], the [radiusKm] within which a
 * place belongs to it, and its own [method] where its margins, horizon or points differ from the
 * authority's (controller ruling R4). [measured] is false for a unit with no official days in the
 * proof, which then claims no "at most" figure. [entryClass], when set, is the class at this unit
 * whatever [measured] says (a unit with official days whose method is not yet rebuilt is still
 * class D). [lateLimits] are the unit's own exceptions to the late limit, each for its own events,
 * before its entry's ([lateLimitFor]). [named] is false for a city's own table (Umm al-Qura's and
 * QMDB's places, ruling R44), whose unit is the user's city itself: About and the site then call the
 * place by the app's own name for it in the reader's language ([Resolution.unitLabel]), not by the
 * authority's spelling ("Mecca", "Алматы қаласы"), as they do outside every unit. [cityId], when set, is
 * the app's own city (its id in the app's city list) whose table the unit is: About then calls the place
 * by that city's name in the reader's language, even where the user's own nearest city is another (a
 * suburb's fix inside the city's reach, the owner's decision of 9 Oct 2026 for QMDB's cities).
 * (Not named `Unit`: that would shadow kotlin.Unit.)
 */
data class AuthorityUnit(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val radiusKm: Double,
    val method: TimetableMethod? = null,
    val measured: Boolean = true,
    val entryClass: EntryClass? = null,
    val lateLimits: List<LateLimit> = emptyList(),
    val named: Boolean = true,
    val cityId: Int? = null,
) {
    init {
        requireEachEventOnce(lateLimits, id)
    }
}

/**
 * An authority's units, and the method for a place beyond all of them: the authority's method at
 * the user's point with the unit's safe-edge margin and the deepest plausible horizon (spec §3.5).
 * [choose], when given, picks the unit instead of the nearest point (districts whose minutes differ,
 * where the nearest seat can be the wrong district).
 */
class UnitSet(
    val entryId: String,
    val units: List<AuthorityUnit>,
    val choose: ((Place) -> AuthorityUnit?)? = null,
    val outside: (GeoPoint) -> TimetableMethod,
) {
    init {
        require(units.map { it.id }.toSet().size == units.size) { "$entryId: duplicate unit ids" }
    }

    /** The unit [place] belongs to, or null beyond every unit. */
    fun unitFor(place: Place): AuthorityUnit? = choose?.invoke(place) ?: nearest(GeoPoint(place.lat, place.lon))

    /** The unit with id [id]. */
    fun unit(id: String): AuthorityUnit = units.first { it.id == id }

    /** The nearest unit whose radius holds [point], or null; ties go to the unit listed first. */
    fun nearest(point: GeoPoint): AuthorityUnit? {
        var best: AuthorityUnit? = null
        var bestKm = Double.MAX_VALUE
        for (unit in index?.near(point) ?: units) {
            val km = distanceKm(point, unit.point)
            if (km <= unit.radiusKm && (km < bestKm || (km == bestKm && order.getValue(unit.id) < order.getValue(best!!.id)))) {
                best = unit
                bestKm = km
            }
        }
        return best
    }

    private val order: Map<String, Int> by lazy { units.withIndex().associate { (i, u) -> u.id to i } }

    /** For a set of many units (QMDB's 5,676), a grid of cells at least as wide as the largest radius. */
    private val index: Grid? by lazy { if (units.size > GRID_FROM) Grid(units) else null }

    /**
     * Units by cell of [cellDeg] degrees, latitude and longitude alike, wide enough that every unit whose radius
     * holds a point lies in the point's cell or one of its eight neighbours.
     */
    private class Grid(units: List<AuthorityUnit>) {
        private val cellDeg: Double
        private val cells: Map<Long, List<AuthorityUnit>>

        init {
            val maxRadius = units.maxOf { it.radiusKm }
            val maxLat = units.maxOf { abs(it.point.lat) } + maxRadius / KM_PER_DEGREE
            // A degree of longitude is shortest at the highest latitude a radius reaches.
            cellDeg = GRID_SAFETY * maxRadius / (KM_PER_DEGREE * cos(rad(min(maxLat, 89.0))))
            cells = units.groupBy { key(cell(it.point.lat), cell(it.point.lon)) }
        }

        private fun cell(deg: Double) = floor(deg / cellDeg).toLong()

        private fun key(lat: Long, lon: Long) = lat * 1_000_000L + lon

        fun near(point: GeoPoint): List<AuthorityUnit> {
            val la = cell(point.lat)
            val lo = cell(point.lon)
            val out = ArrayList<AuthorityUnit>()
            for (dLat in -1L..1L) for (dLon in -1L..1L) cells[key(la + dLat, lo + dLon)]?.let { out += it }
            return out
        }
    }

    private companion object {
        /** Above this many units a set is searched through its grid. */
        const val GRID_FROM = 256

        /** A cell a little wider than it must be, for the haversine's curvature over a cell. */
        const val GRID_SAFETY = 1.01

        /** Kilometres in a degree of latitude, on the mean earth radius [distanceKm] uses (a little more than a degree spans). */
        const val KM_PER_DEGREE = 111.19
    }
}

/** Every authority's units, by registry entry id. */
object Units {
    private val sets: Map<String, UnitSet> by lazy {
        listOf(
            Jakim.units, Kemenag.units, Kemenag.muhammadiyahUnits, Diyanet.units, Egypt.units, Gulf.dubaiUnits,
            Gulf.awqafUnits, Gulf.qatarUnits, Gulf.omanUnits, Muis.bruneiUnits, Maghreb.libyaUnits, Maghreb.tunisiaUnits,
            Maghreb.algeriaUnits, Maghreb.moroccoUnits, SouthAfrica.units, Russia.dumRtUnits, Russia.dumRfUnits,
            Balkans.bosniaUnits, Balkans.albaniaUnits, Europe.austriaUnits, Europe.switzerlandUnits, Americas.fianzUnits,
            UmmAlQura.units,
        ).plus(Americas.torontoUnits).plus(Americas.lakembaUnits).plus(Europe.dublinTables).plus(SouthAfrica.capeTownTables)
            .plus(listOf(Europe.londonUnits, Europe.gmpUnits, Europe.embUnits, Europe.irnUnits, Diyanet.europeUnits))
            .plus(Levant.jordanUnits).plus(Levant.paUnits)
            .associateBy { it.entryId }
    }

    /**
     * Sets built only when their entry is first resolved: QMDB's 5,676 units (decoded from QmdbPlaceList, about 20 ms
     * on a cold JVM) wait for the first place in Kazakhstan.
     */
    private val built: Map<String, Lazy<UnitSet>> = mapOf("kz.qmdb" to lazy { CentralAsia.kazakhstanUnits })

    /** The units of entry [entryId], or null when it has none. */
    fun of(entryId: String): UnitSet? = sets[entryId] ?: built[entryId]?.value
}

/**
 * Ruling R40: how far a unit whose neighbours publish their own tables reaches. Its point rides as
 * the fixed point (R15), so east of it the start shown is its point's, later than the user's own sun
 * by 4 min a degree of longitude; the reach is where that lateness meets the class's limit (spec §1:
 * 1 min for A, 2 for B, 3 otherwise): about 21 km at 41° N for a minute.
 */
internal fun lateReachKm(latitude: Double, entryClass: EntryClass): Double {
    val minutes = when (entryClass) {
        EntryClass.A -> 1
        EntryClass.B -> 2
        else -> 3
    }
    return minutes * DEGREES_PER_MINUTE * rad(180.0) / 180.0 * EARTH_RADIUS_KM * cos(rad(latitude))
}

/** One table an authority prints for one point: [key] names its unit, [method] where it differs from the entry's. */
internal class PrintedTable(val key: String, val name: String, val point: GeoPoint, val method: TimetableMethod? = null)

/**
 * Rulings R30, R44, R45: an authority's tables printed for one point each.
 * - Within its class's [lateReachKm] of a table's point, the point rides as the method's fixed point
 *   beside the user's (starts the later, ends the earlier), for a member of a cautious entry and for
 *   someone who follows it alone; the table's fitted end-of-eating margin applies there only.
 * - Beyond, [beyondTable] of the nearest table.
 */
internal fun pointTables(entry: RegistryEntry, tables: List<PrintedTable>): UnitSet {
    val method = requireNotNull(entry.method) { "${entry.id} has no single method" }
    val units = tables.map { t ->
        val id = if (tables.size == 1) entry.id else "${entry.id}.${t.key}"
        AuthorityUnit(id, t.name, t.point, lateReachKm(t.point.lat, entry.entryClass), t.method)
    }
    return UnitSet(entry.id, units) { user ->
        val nearest = units.minBy { distanceKm(user, it.point) }
        (nearest.method ?: method).beyondTable("${entry.id}.edge", nearest.point, user, nearest.radiusKm)
    }
}

/** A table printed for one [point] ([pointTables]). */
internal fun pointTable(entry: RegistryEntry, name: String, point: GeoPoint): UnitSet =
    pointTables(entry, listOf(PrintedTable(entry.id, name, point)))

/**
 * Rulings R44 and R45: [this] beyond its table's reach ([reachKm] from [tablePoint]): starts at the
 * user's point a minute later (the edge), the end of eating with no fitted margin (atEdge: SAFE_END or
 * lower), and, within [ENDS_REACH_FACTOR] reaches, the table's point still bounding sunrise, the end
 * of eating and imsak (an ends-only fixed point). Farther, the user's point alone: there the table's
 * sunrise would be more than three times the class's minutes before the user's own.
 */
internal fun TimetableMethod.beyondTable(id: String, tablePoint: GeoPoint, user: GeoPoint, reachKm: Double): TimetableMethod {
    val edge = atEdge(id, margins.widened(BEYOND_REACH_SECONDS))
    return if (distanceKm(user, tablePoint) <= reachKm * ENDS_REACH_FACTOR) {
        edge.copy(fixedPoint = tablePoint, fixedPointMode = FixedPointMode.ENDS_ONLY)
    } else {
        edge.copy(fixedPoint = null)
    }
}

/** How many reaches a point table's point still bounds the ends beyond its reach (ruling R45). */
internal const val ENDS_REACH_FACTOR = 3.0

/** The edge's minute beyond a point table's reach. */
private const val BEYOND_REACH_SECONDS = 60

/** A minute of time is a quarter degree of longitude. */
private const val DEGREES_PER_MINUTE = 0.25

/** Great-circle distance in kilometres (mean earth radius). */
fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val la1 = rad(a.lat)
    val la2 = rad(b.lat)
    val dLat = la2 - la1
    val dLon = rad(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
}

private fun rad(deg: Double) = deg * PI / 180.0

private const val EARTH_RADIUS_KM = 6371.0
