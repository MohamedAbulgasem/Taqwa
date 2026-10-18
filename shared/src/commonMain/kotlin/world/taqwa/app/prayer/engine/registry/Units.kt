package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.EventOffsets
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
import world.taqwa.app.prayer.engine.registry.authorities.SouthAsia
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
 * [endsBoundAtUserPoint] is true for a table printed for its own point beside a method computed at the user's own
 * point ([tablesAtTheirPoints]): its method then carries [TimetableMethod.endsBoundAtUserPoint], so no end shown is
 * after the method at the user's point alone, as it was before the unit.
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
    val endsBoundAtUserPoint: Boolean = false,
) {
    init {
        requireEachEventOnce(lateLimits, id)
    }
}

/**
 * An authority's units, and the method for a place beyond all of them: the authority's method at
 * the user's point with the unit's safe-edge margin and the deepest plausible horizon (spec §3.5).
 * [choose], when given, picks the unit instead of the nearest point (districts whose minutes differ,
 * where the nearest seat can be the wrong district). [measuredBeyond]: beyond every unit a place keeps
 * whether it was measured before the units (a cautious member's own reach, [Member.measuredAt]); else it
 * is not measured there. [around], when given, adds what else bounds a user inside a unit, after the
 * unit's point is set as the fixed point: the other tables whose reach holds the user
 * ([tablesAtTheirPoints]' envelope), or an authority's printed town figures (BIK's, Balkans.kosovoUnits).
 * [envelope], when set, is the [TableEnvelope] [around] and [outside] ride (the tools' envelope rows read from it which
 * tables ride where). [outsideAsMember], when set, is [outside] for the entry placed as a cautious entry's member, where
 * that differs from the entry chosen on its own (Manchester Central's Wifaqul table, whose ride past its bound holds the
 * entry chosen but not the UK's cautious member: Europe.wifaqulUnits).
 */
class UnitSet(
    val entryId: String,
    val units: List<AuthorityUnit>,
    val choose: ((Place) -> AuthorityUnit?)? = null,
    val measuredBeyond: Boolean = false,
    val around: ((user: GeoPoint, unit: AuthorityUnit, method: TimetableMethod) -> TimetableMethod)? = null,
    val envelope: TableEnvelope? = null,
    val outsideAsMember: ((GeoPoint) -> TimetableMethod)? = null,
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
            // The city-point audit of 10–11 Oct 2026: tables printed for their own points, of entries and cautious members
            // computed at the user's point (tablesAtTheirPoints).
            .plus(listOf(Americas.usaUnits, Americas.canadaIsnaUnits, Americas.chicagoBlockUnits))
            .plus(Americas.canadaFamilyUnits)
            .plus(listOf(SouthAsia.indiaUnits, CentralAsia.uzbekistanUnits, Balkans.kosovoUnits))
            .plus(listOf(SouthAsia.pakistanUnits, CentralAsia.kyrgyzstanUnits, Europe.wifaqulUnits))
            .plus(listOf(Levant.gazaAwqafUnits, Levant.lebanonUnits, Levant.syriaUnits, Levant.iraqUnits))
            // The city-point round of 11 Oct 2026: Diyanet's Sydney table at every plausible point of its, near Sydney only.
            .plus(Americas.sydneyDiyanetUnits)
            // The owner's decision of 17 Oct 2026: Rabita's Helsinki calendar rides past its region, as a nearest table past its bound.
            .plus(Europe.rabitaRide)
            .associateBy { it.entryId }
    }

    /**
     * Sets built only when their entry is first resolved: QMDB's 5,676 units (decoded from QmdbPlaceList and built,
     * about 10 ms on a cold JVM) wait for the first place in Kazakhstan.
     */
    private val built: Map<String, Lazy<UnitSet>> = mapOf("kz.qmdb" to lazy { CentralAsia.kazakhstanUnits })

    /** The units of entry [entryId], or null when it has none. */
    fun of(entryId: String): UnitSet? = sets[entryId] ?: built[entryId]?.value

    /**
     * Another entry's tables riding beside cautious entry [entryId]'s day ([CrossRide]), or null: ISNA's Montréal and
     * Ottawa mosques beside Canada's cautious times outside those cities (the owner's decision of 17 Oct 2026).
     */
    fun crossRide(entryId: String): CrossRide? = crossRides[entryId]

    private val crossRides: Map<String, CrossRide> by lazy { listOf(Americas.canadaIsnaRide).associateBy { it.entryId } }
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
 * One table printed for its own [point] ([tablesAtTheirPoints]): [key] names its unit, [lateLimits] its own exceptions (R41).
 * [method], when set, is the unit's own method in place of the set's (two samples of one authority's table at two points:
 * the Syrian Awqaf's Damascus, whose units read each other's point for the starts too, [TimetableMethod.startPoints]).
 */
internal class OwnTable(
    val key: String,
    val name: String,
    val point: GeoPoint,
    val lateLimits: List<LateLimit> = emptyList(),
    val method: TimetableMethod? = null,
)

/**
 * Rulings R30, R40 and R44 for an entry or a cautious member that is computed at the user's own point and checked against
 * tables printed each for its own point (the city-point audit of 10–11 Oct 2026): a mosque's own (ISNA's in the United
 * States and Canada, Canada's three families, Chicago's 18° block, Manchester Central's Wifaqul table), a city's
 * (Dawat-e-Islami's Delhi, the Karachi tables, the Muslim Board's Tashkent, the Kyrgyz Muftiate's Bishkek, Gaza's
 * Awqaf, Dar al-Fatwa's Beirut, the Syrian Awqaf's two Damascus tables, the Sunni Endowment's Baghdad), an authority's
 * base point (BIK's Takvimi). Each table is a unit at its point, reaching as far as [reach]'s [lateReachKm]; the nearest
 * whose reach holds the user takes them, and its point rides as the method's fixed point beside the user's own (ruling
 * R15: starts the later, sunrise and the ends the earlier). So the time shown is never before the nearest table whose
 * reach holds the user, nor any end after it, and nowhere is a start earlier, or an end later, than the user's own point
 * alone gave it. Every other table whose [bound] reach holds the user rides beside the nearest ([TableEnvelope], round 2
 * of 11 Oct 2026, the owner's rule: the app's Ottawa, in the Ottawa South Mosque's unit, had been early against Abrar
 * Masjid and Kanata's tables); two samples of one table (Damascus) also read each other's point for the starts
 * ([OwnTable.method]). On main the app's own Houston, Sterling, Calgary, Delhi or Shtime, a few kilometres from
 * such a table, had a time a minute on the wrong side of it on some days.
 *
 * [reach]: a mosque's or a city's table serves its own neighbourhood, a minute of longitude (class A's, about 17 to 24
 * km), where the user's own sun is within a minute of the table's; farther out a user's own mosque or town prints for
 * its own point, as the method computes. That narrows ruling R40, which takes a unit's reach from its entry's class
 * limit (class D's and C's three minutes, about 61 km, for ISNA, the city tables and Canada's families): a proposed
 * ruling for the owner, whose cost is the cities just beyond the minute (Calgary's north-east, Vancouver's west, the
 * Wifaqul towns 17 to 20 km from Manchester Central). An authority that applies one table to a whole area by
 * whole-minute offsets (BIK) reaches as far as its entry's class.
 *
 * Beyond every minute's reach, the tables whose [bound] reach holds the user ride as there ([TableEnvelope.beyond]);
 * beyond every bound reach the nearest table's own day still rides beside the user's own, a day alongside, out to
 * [RIDE_REACHES] of its reaches (the owner's decision of 17 Oct 2026), and beyond that the method at the user's own
 * point exactly as before ([method]): unlike one point table's fit ([pointTables], whose edge [beyondTable] widens), these
 * margins hold every table at its own point and are meant for any point, so ruling R44's edge minute and SAFE_END do not
 * apply there (the same proposed ruling). As beyond every other unit, no figure is claimed there, except that a cautious
 * member keeps its own measured reach ([measuredBeyond], [Member.measuredAt]). [measured] is false for an entry that
 * claims no figure anywhere (every city table's above). The units carry no name of their own ([AuthorityUnit.named]):
 * About calls the place by the app's own name for it, never a mosque's. [ride]: how many of its reaches the nearest
 * table rides past every bound ([TableEnvelope]'s rideReaches; zero keeps the bound alone); [memberRide], where it
 * differs, the same for the entry placed as a cautious entry's member (Manchester Central's Wifaqul table rides the
 * entry chosen but not the UK's cautious member, whose reach is the owner's question 2 of 17 Oct 2026).
 */
internal fun tablesAtTheirPoints(
    entryId: String,
    reach: EntryClass,
    method: TimetableMethod,
    tables: List<OwnTable>,
    measured: Boolean = true,
    measuredBeyond: Boolean = false,
    bound: EntryClass = EntryClass.D_AUTHORITY,
    ride: Double = RIDE_REACHES,
    memberRide: Double = ride,
): UnitSet {
    val units = tables.map { t ->
        AuthorityUnit(
            t.key, t.name, t.point, lateReachKm(t.point.lat, reach), method = t.method, measured = measured,
            lateLimits = t.lateLimits, named = false, endsBoundAtUserPoint = true,
        )
    }
    val envelope = TableEnvelope(units, bound, ride)
    val asMember = if (memberRide == ride) null else TableEnvelope(units, bound, memberRide)
    return UnitSet(
        entryId, units, measuredBeyond = measuredBeyond,
        around = { user, unit, placed -> envelope.around(user, unit, placed, method) }, envelope = envelope,
        outsideAsMember = asMember?.let { e -> { user -> e.beyond(user, method) } },
    ) { user -> envelope.beyond(user, method) }
}

/**
 * The owner's rule for the city-point round of 11 Oct 2026, "rather late, never early": a place is never before any
 * table printed for its own point whose [bound] reach holds it, nor any end after it, not only its nearest. That reach is
 * ruling R40's own, the entry's class limit (three minutes of longitude, about 61 km at 41° N, for the class D entries and
 * class C members of [tablesAtTheirPoints]), where a user may still follow that table, and also the three reaches R45
 * bounds a point table's ends to. The unit (its figure, About's name) stays the nearest within a minute's reach; the
 * other tables ride beside it ([TimetableMethod.envelopePoints]): every start the latest and every end the earliest over
 * all of them and the user's own point, each end never after any of them alone. Beyond every unit but within a table's
 * bound reach, the nearest such table rides as the fixed point and the rest beside it, with no figure (the review's probe
 * found the times just beyond the minute's reach before the nearest table on some days: Calgary's north-east, Delhi's
 * edge, Faridabad).
 *
 * [rideReaches] (the owner's decision of 17 Oct 2026, "rather late, never early"): beyond every bound reach the nearest
 * table still rides, out to that many of its own reaches ([rideKm]: [RIDE_REACHES] minutes of longitude at the table's
 * latitude, 83 km at Edmonton to 126 km at Karachi, where the bound is three). Just past the bound the times had come
 * back to the user's own point and ran up to 4 to 5 min before the nearest table on some days, where a user may still
 * pray by that table (the app's Winchester and Martinsburg ended sunrise and the fast after ADAMS's table, Angren began
 * every prayer before Tashkent's, Thatta before Karachi's). There its own day rides beside the user's own as a day
 * alongside ([TimetableMethod.alongside]): the user's own day exactly as before, the table's own day at its own point,
 * each read with its own curves, regional variants and margins, and the day shown their safe side, every start the later
 * of the two and every end the earlier. So nothing is earlier than before, no start is before the nearest table nor any
 * end after it, and no time is later than the later of the two days asks: a fixed point, or the table's point beside the
 * user's (round 2's envelope points, which read the method's one set of curves at both points), would read one point's
 * curves at the other, and the Canadian families' night fractions and Rabita's summer Isha, read at both latitudes, had
 * come up to 10 and 23 min later than either day (the reviews of 17 Oct 2026). No figure is claimed there
 * ([measuredBeyond] as before). A [rideReaches] of zero keeps the bound alone: Diyanet's Sydney points (Diyanet prints a
 * table for every city, which its preset computes at the user's own point) and Manchester Central's Wifaqul table placed as
 * the UK's cautious member (Europe.wifaqulUnits: its reach is the owner's question 2, accepted at the bound for now).
 * Beyond the ride, the method at the user's own point alone, as before.
 */
class TableEnvelope internal constructor(
    private val units: List<AuthorityUnit>,
    private val bound: EntryClass,
    private val rideReaches: Double = 0.0,
) {
    private val reachKm: Map<String, Double> = units.associate { it.id to lateReachKm(it.point.lat, bound) }

    /** How far [unit]'s table rides as the nearest beyond its bound reach: [rideReaches] of its own reach. */
    fun rideKm(unit: AuthorityUnit): Double = rideReaches * unit.radiusKm

    /** The tables whose bound reach holds [user], nearest first. */
    fun holding(user: GeoPoint): List<AuthorityUnit> =
        units.map { it to distanceKm(user, it.point) }.filter { (u, km) -> km <= reachKm.getValue(u.id) }
            .sortedBy { it.second }.map { it.first }

    /** The nearest table whose ride ([rideKm]) holds [user], or null. */
    fun nearestRiding(user: GeoPoint): AuthorityUnit? =
        units.map { it to distanceKm(user, it.point) }.filter { (u, km) -> km <= rideKm(u) }.minByOrNull { it.second }?.first

    /**
     * The tables that ride beside [user]: every one whose bound reach holds it, nearest first, and the nearest within its
     * ride ([nearestRiding]) after them where no bound holds it (a nearer table at a higher latitude, whose bound is the
     * shorter, can be beyond its bound while a farther one holds the user).
     */
    fun riding(user: GeoPoint): List<AuthorityUnit> = riding(user, holding(user))

    private fun riding(user: GeoPoint, held: List<AuthorityUnit>): List<AuthorityUnit> {
        val nearest = nearestRiding(user) ?: return held
        return if (nearest in held) held else held + nearest
    }

    /** The nearest table whose ride holds [user] where no bound in [held] holds it as well, or null. */
    private fun ridingAlone(user: GeoPoint, held: List<AuthorityUnit>): AuthorityUnit? = nearestRiding(user)?.takeIf { it !in held }

    /** [unit]'s own day at its own point, beside a user's ([Alongside]): its own method, else the set's [method]. */
    private fun alongside(unit: AuthorityUnit, method: TimetableMethod) = Alongside(unit.point, unit.method ?: method)

    /**
     * [placed] (the unit's method at its point) with every other table holding [user] beside it, and the nearest within its
     * ride, where no bound holds it, as a day alongside ([method] its own where it has none).
     */
    fun around(user: GeoPoint, unit: AuthorityUnit, placed: TimetableMethod, method: TimetableMethod): TimetableMethod {
        val held = holding(user)
        val others = held.filter { it.id != unit.id }.map { it.point }
        val withOthers = if (others.isEmpty()) placed else placed.copy(envelopePoints = (placed.envelopePoints + others).distinct())
        val ride = ridingAlone(user, held)?.takeIf { it.id != unit.id } ?: return withOthers
        return withOthers.copy(alongside = withOthers.alongside + alongside(ride, method))
    }

    /**
     * [method] beyond every unit: the nearest table holding [user] as its fixed point and the other tables holding it
     * beside it (round 2's envelope); beyond every bound reach, [method] at the user's own point exactly as before, with
     * the nearest table within its ride as a day alongside ([TimetableMethod.alongside]: its own day at its own point,
     * read with its own curves); else [method] alone.
     */
    fun beyond(user: GeoPoint, method: TimetableMethod): TimetableMethod {
        val held = holding(user)
        val ride = ridingAlone(user, held)
        val first = held.firstOrNull() ?: return ride?.let { method.copy(alongside = method.alongside + alongside(it, method)) } ?: method
        val own = first.method ?: method
        val bounded = own.copy(
            fixedPoint = first.point, endsBoundAtUserPoint = true,
            envelopePoints = (own.envelopePoints + held.drop(1).map { it.point }).distinct(),
        )
        return ride?.let { bounded.copy(alongside = bounded.alongside + alongside(it, method)) } ?: bounded
    }

    /**
     * Every table riding beside [user] ([riding]: each whose bound reach holds it, and the nearest within its ride) as a
     * day alongside, each its own method's at its own point ([method] where it has none): another entry's tables held
     * beside a cautious place ([CrossRide]).
     */
    fun alongside(user: GeoPoint, method: TimetableMethod): List<Alongside> = riding(user).map { alongside(it, method) }
}

/**
 * Another entry's tables printed for their own points riding beside a cautious entry's day, wherever that entry is resolved
 * (the owner's decision of 17 Oct 2026, question 1, its own example: "someone 60 km from Montréal may well pray by CCML's
 * board"). [ride] gives the tables at a user's point, as days alongside (`Resolution.alongside`), each read as its own
 * entry [tablesEntryId] reads it at its own point: the place's day is the safe side of its own cautious day and theirs.
 */
class CrossRide(val entryId: String, val tablesEntryId: String, val ride: (GeoPoint) -> List<Alongside>)

/**
 * How many of its own reaches (a minute of longitude each, class A's) a table printed for its own point rides as the
 * nearest beyond its bound reach ([TableEnvelope]): the owner's decision of 17 Oct 2026, "about five reaches", where the
 * bound is three.
 */
internal const val RIDE_REACHES = 5.0

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

/**
 * A named city timetable a user chooses beyond its own area (London Unified outside the M25, IACAD's Dubai tables in
 * the other emirates): the city-point round of 11 Oct 2026, the owner's rule, a user who chooses a table follows it, so
 * the time shown is never before that table, nor any end after it, wherever it is chosen. [beyondTable]'s edge (the
 * user's own point a minute later, the end of eating with no fitted margin), with the table's point riding as the fixed
 * point for every start and end ([FixedPointMode.BOTH]), where R45 bounded only the ends (St Albans, Sevenoaks and
 * Basildon had a start before LUPT's on some days; Sharjah and Ajman before Dubai's). London rides it within R45's three
 * reaches; IACAD at any distance.
 */
internal fun TimetableMethod.followedBeyond(id: String, tablePoint: GeoPoint, edge: EventOffsets = margins.widened(BEYOND_REACH_SECONDS)): TimetableMethod =
    atEdge(id, edge).copy(fixedPoint = tablePoint, fixedPointMode = FixedPointMode.BOTH)

/**
 * [this] beyond every unit of an authority whose every place is a unit, Umm al-Qura's and QMDB's (rulings R44 and
 * R45, the reviews of 10 Oct 2026): [beyondTable]'s edge, the user's point a minute later and the end of eating with
 * no fitted margin, but no table's point bounding an end. R45 keeps that bound within one reach only there, where a
 * unit's own bound covers its cost (QMDB's reach is where every time stays within 3 min of the user's own point,
 * Umm al-Qura's a minute of longitude), and a user beyond every unit is beyond every reach; within three it had cost
 * up to 12 min.
 */
internal fun TimetableMethod.beyondEveryUnit(id: String): TimetableMethod =
    atEdge(id, margins.widened(BEYOND_REACH_SECONDS)).copy(fixedPoint = null)

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
