package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.authorities.Europe

/** A region of the earth, in degrees (north and east positive). */
sealed interface Area {
    fun contains(p: GeoPoint): Boolean
}

/** Latitudes [south]..[north] and longitudes [west]..[east], inclusive. */
data class Box(val south: Double, val north: Double, val west: Double, val east: Double) : Area {
    override fun contains(p: GeoPoint) = p.lat in south..north && p.lon in west..east
}

/** Within [radiusKm] of [center]. */
data class Circle(val center: GeoPoint, val radiusKm: Double) : Area {
    override fun contains(p: GeoPoint) = distanceKm(p, center) <= radiusKm
}

/** Inside the ring of [vertices] (lat/lon treated as a plane, fine at these sizes). */
class Polygon(private val vertices: List<GeoPoint>) : Area {
    override fun contains(p: GeoPoint): Boolean {
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) &&
                p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

/** Any of [areas]. */
class AnyOf(private val areas: List<Area>) : Area {
    constructor(vararg areas: Area) : this(areas.toList())

    override fun contains(p: GeoPoint) = areas.any { it.contains(p) }
}

/** A rule on the coordinates themselves ("east of 18.5° E or south of 29.5° N"). */
class Where(private val rule: (GeoPoint) -> Boolean) : Area {
    override fun contains(p: GeoPoint) = rule(p)
}

/**
 * Inside [area] of [countryCode], or in one of its first-level [regions] (the city list's names,
 * matched by [regionKey]), Automatic follows [entryId] rather than the country's entry. A rule by
 * [regions] alone ([area] null) never holds where the place's region is not known.
 */
data class RegionRule(
    val name: String,
    val countryCode: String,
    val area: Area?,
    val entryId: String,
    val regions: Set<String> = emptySet(),
) {
    private val keys = regions.map(::regionKey).toSet()

    /** Whether the rule holds at [point], whose first-level region is [admin1] (null: not known). */
    fun holds(point: GeoPoint, admin1: String?): Boolean =
        area?.contains(point) == true || (admin1 != null && regionKey(admin1) in keys)
}

/** Inside [area], entry [entryId]'s method is changed by [change] (after its unit's own method). */
class MethodVariant(val entryId: String, val area: Area, val change: (TimetableMethod) -> TimetableMethod)

/**
 * The named regions the registry resolves by (spec §2.1, §3.5–§3.7, §10.3). Shapes are coarse and
 * checked against the admin-1 region of every city in the app's list (RegistryCitiesTest); where a
 * shape takes in a neighbouring town, the test names it and the town's Muslims follow the same
 * school, so the difference is one of authority, never of Asr school or of being early.
 */
object Regions {

    private fun p(lat: Double, lon: Double) = GeoPoint(lat, lon)

    /**
     * Greater London inside the M25, the area London Unified is valid for ("Valid inside the M25
     * only", research UK section). Its 32 junctions, clockwise from the Dartford Crossing, joined not by
     * chords, which cut strips of London off to the UK's cautious entry, but every 1.5 km or so along the
     * ring: each point interpolated in bearing and distance about the ring's centre (51.49 N, 0.12 W)
     * between its two junctions, then 300 m further out (ruling R49).
     */
    val londonM25 = Polygon(
        listOf(
            p(51.465, 0.263), p(51.452, 0.260), p(51.439, 0.255), p(51.426, 0.249), p(51.414, 0.227),
            p(51.403, 0.204), p(51.394, 0.180), p(51.383, 0.166), p(51.373, 0.152), p(51.364, 0.136),
            p(51.355, 0.120), p(51.336, 0.127), p(51.315, 0.131), p(51.294, 0.133), p(51.286, 0.115),
            p(51.279, 0.097), p(51.272, 0.078), p(51.266, 0.059), p(51.261, 0.039), p(51.256, 0.018),
            p(51.252, -0.002), p(51.249, -0.023), p(51.246, -0.044), p(51.244, -0.065), p(51.250, -0.092),
            p(51.256, -0.118), p(51.263, -0.142), p(51.262, -0.162), p(51.261, -0.182), p(51.261, -0.202),
            p(51.261, -0.222), p(51.267, -0.242), p(51.273, -0.261), p(51.280, -0.280), p(51.287, -0.298),
            p(51.295, -0.314), p(51.304, -0.330), p(51.305, -0.353), p(51.307, -0.375), p(51.310, -0.398),
            p(51.313, -0.421), p(51.318, -0.444), p(51.328, -0.459), p(51.339, -0.473), p(51.350, -0.486),
            p(51.361, -0.498), p(51.373, -0.509), p(51.385, -0.519), p(51.394, -0.527), p(51.404, -0.534),
            p(51.420, -0.534), p(51.436, -0.532), p(51.448, -0.528), p(51.460, -0.522), p(51.477, -0.518),
            p(51.494, -0.512), p(51.507, -0.511), p(51.521, -0.509), p(51.534, -0.506), p(51.547, -0.502),
            p(51.560, -0.496), p(51.574, -0.500), p(51.588, -0.502), p(51.602, -0.502), p(51.617, -0.502),
            p(51.632, -0.503), p(51.646, -0.504), p(51.659, -0.492), p(51.672, -0.480), p(51.684, -0.466),
            p(51.696, -0.451), p(51.710, -0.442), p(51.715, -0.417), p(51.719, -0.392), p(51.721, -0.362),
            p(51.722, -0.333), p(51.721, -0.305), p(51.716, -0.278), p(51.710, -0.252), p(51.703, -0.228),
            p(51.695, -0.198), p(51.687, -0.171), p(51.689, -0.151), p(51.691, -0.131), p(51.692, -0.111),
            p(51.692, -0.090), p(51.691, -0.070), p(51.690, -0.049), p(51.688, -0.029), p(51.684, -0.009),
            p(51.680, 0.011), p(51.675, 0.030), p(51.673, 0.049), p(51.670, 0.069), p(51.667, 0.088),
            p(51.662, 0.108), p(51.657, 0.129), p(51.652, 0.150), p(51.645, 0.171), p(51.637, 0.191),
            p(51.628, 0.211), p(51.619, 0.230), p(51.608, 0.249), p(51.595, 0.261), p(51.582, 0.272),
            p(51.570, 0.278), p(51.557, 0.283), p(51.545, 0.286), p(51.532, 0.289), p(51.519, 0.291),
            p(51.507, 0.292), p(51.494, 0.291), p(51.482, 0.284),
        ),
    )

    /**
     * Scotland with its islands (research-uk: the UK 15° family's Asr there is Glasgow's). Along the
     * Anglo-Scottish border every few km, east to west: the coast at Marshall Meadows Bay, the Tweed to
     * Carham, the Cheviots, Carter Bar, Kielder, Kershope Burn, the Liddel, Scots' Dike and the Sark;
     * then down the middle of the Solway Firth, through the North Channel between Galloway and Kintyre
     * and Northern Ireland and between Islay and Ireland, out into the Atlantic past St Kilda, north of
     * Shetland and back down the North Sea. Checked against every British city in the app's list
     * (RegistryCitiesTest): Berwick-upon-Tweed, Carlisle and Northern Ireland stay out.
     */
    val scotland = Polygon(
        listOf(
            p(55.8115, -2.0351), p(55.7900, -2.0700), p(55.7700, -2.0950), p(55.7535, -2.1025), p(55.7380, -2.1350),
            p(55.7220, -2.1650), p(55.7050, -2.1780), p(55.6750, -2.2050), p(55.6600, -2.2300), p(55.6488, -2.2455),
            p(55.6440, -2.2700), p(55.6440, -2.2900), p(55.6355, -2.3350), p(55.6150, -2.3300), p(55.5900, -2.3000),
            p(55.5600, -2.2550), p(55.5300, -2.2450), p(55.4938, -2.2127), p(55.4700, -2.1900), p(55.4450, -2.2290),
            p(55.4145, -2.2320), p(55.3950, -2.2650), p(55.3700, -2.3300), p(55.3525, -2.3600), p(55.3450, -2.4100),
            p(55.3540, -2.4790), p(55.3300, -2.5200), p(55.3045, -2.5540), p(55.2840, -2.5870), p(55.2650, -2.6100),
            p(55.2450, -2.6500), p(55.2150, -2.7000), p(55.1900, -2.7500), p(55.1600, -2.7900), p(55.1375, -2.8260),
            p(55.1100, -2.8700), p(55.0850, -2.9050), p(55.0600, -2.9450), p(55.0410, -2.9650), p(55.0340, -3.0360),
            p(55.0200, -3.0450), p(55.0030, -3.0550), p(54.9750, -3.0600),
            // The Solway Firth.
            p(54.9720, -3.1500), p(54.9680, -3.2400), p(54.9500, -3.3300), p(54.8800, -3.4800), p(54.8000, -3.6500),
            p(54.7200, -3.8000), p(54.6000, -4.1000), p(54.5500, -4.4500),
            // The North Channel, then between Islay and Ireland.
            p(54.5500, -4.8500), p(54.6000, -5.2000), p(54.7500, -5.3500), p(54.9000, -5.4500), p(55.1000, -5.6200),
            p(55.2500, -5.9300), p(55.3000, -6.0000), p(55.4000, -6.3000), p(55.4500, -6.8000), p(55.5000, -7.4000),
            p(55.5500, -8.0000), p(55.6000, -9.5000),
            // The Atlantic, the north beyond Shetland, the North Sea.
            p(61.0000, -9.5000), p(61.0000, 0.0000), p(55.8115, -1.0000),
        ),
    )

    /** The Chicago metro: Cook, DuPage, Lake, Will, Kane and north-west Indiana (brief). */
    val chicago = Box(41.2, 42.5, -88.6, -87.2)

    /** Toronto and the GTA (brief). */
    val toronto = Box(43.4, 44.1, -80.0, -78.9)

    /** Montreal (brief). */
    val montreal = Box(45.3, 45.8, -74.1, -73.3)

    /** Ottawa and Gatineau (brief). */
    val ottawa = Box(45.2, 45.6, -76.0, -75.4)

    /** The Cape Town metro with Paarl and Stellenbosch, where no timetable has a majority. */
    val capeTown = Box(-34.40, -33.45, 18.30, 19.10)

    /** The Gaza Strip, where the PA's table and Gaza's Ministry of Awqaf differ. */
    val gaza = Box(31.20, 31.60, 34.20, 34.57)

    /** The Dubai emirate (IACAD's Dubai and Dubai Rural zones) and the Hatta exclave. */
    val dubai = AnyOf(
        Polygon(
            listOf(
                p(25.33, 55.32), p(25.25, 55.45), p(25.10, 55.60), p(24.90, 55.85), p(24.62, 55.70),
                p(24.62, 55.25), p(24.93, 54.88), p(25.10, 55.05), p(25.30, 55.18), p(25.36, 55.28),
            ),
        ),
        Circle(p(24.80073, 56.12726), 12.0),
    )

    /**
     * Tatarstan: 30 km around each of DUM RT's 44 localities (its 2026 table). The 19 in the app's
     * city list are at those points; the 25 villages are placed to about 10 km, enough for a
     * region edge but not a reference point.
     */
    val tatarstan = AnyOf(
        listOf(
            p(55.78874, 49.12214), p(55.73718, 52.41961), p(55.63794, 51.81502), p(54.90395, 52.31793),
            p(55.84376, 48.51784), p(54.5378, 52.7985), p(55.76232, 52.04425), p(54.5971, 52.45124),
            p(55.36612, 50.64399), p(55.3195, 52.06942), p(54.85821, 53.08006), p(54.42903, 50.80598),
            p(54.39759, 53.25116), p(55.89692, 52.31119), p(54.97422, 48.29088), p(56.5234, 52.99431),
            p(56.09254, 49.87819), p(55.72792, 53.1022), p(56.18648, 50.89404),
            p(54.85, 50.82), p(55.72, 54.07), p(55.30, 50.10), p(55.20, 48.51), p(54.90, 49.93), p(56.35, 50.20),
            p(56.00, 50.45), p(54.97, 49.05), p(56.07, 49.40), p(55.40, 48.19), p(55.77, 48.98), p(55.90, 49.30),
            p(55.20, 49.27), p(55.40, 49.55), p(55.71, 51.41), p(55.30, 53.19), p(55.07, 51.23), p(55.75, 49.65),
            p(55.46, 50.14), p(55.25, 52.58), p(54.72, 47.56), p(54.94, 48.83), p(55.89, 50.24), p(54.60, 53.46),
            p(54.66, 51.50),
        ).map { Circle(it, 30.0) },
    )

    /** Dagestan, east of Chechnya and south of Kalmykia. */
    val dagestan = AnyOf(Box(41.10, 43.45, 46.40, 48.70), Box(43.45, 44.00, 46.45, 48.00), Box(44.00, 45.00, 45.20, 47.80))

    /** Bashkortostan (a Hanafi region, spec §3.7); tested after Tatarstan. */
    val bashkortostan = Box(52.00, 56.55, 53.15, 59.60)

    /**
     * Iran's Sistan and Baluchestan, Khorasan (Razavi, North, South) and Golestan provinces: Hanafi
     * Sunnis (spec §3.7). By province, not by shape (ruling R32).
     */
    val iranHanafi = setOf(
        "Sistan and Baluchestan", "Sistan-Baluchestan", "Golestan", "Razavi Khorasan", "North Khorasan", "South Khorasan",
        "South Khorasan Province",
    )

    /**
     * Iran's Kurdistan and Hormozgan provinces: Shafi'i Sunnis (spec §3.7). Shafi'i towns elsewhere
     * (Larestan) are not told apart: there the school is not known and the later Asr leads.
     */
    val iranShafii = setOf("Kurdistan", "Kurdistan Province", "Hormozgan")

    /** Sandžak, whose towns use the Bosnian vaktija (research Montenegro/Sandžak note). */
    val sandzak = Box(42.85, 43.55, 19.40, 20.65)

    /** The Preševo valley, covered by Kosovo's Takvimi ("for Kosovo and the Preševo valley"). */
    val presevo = Box(42.25, 42.90, 21.40, 21.85)

    /** Saudi Arabia's Eastern Province, east of 48° E (brief). */
    val saudiEast = Where { it.lon > 48.0 }

    /** Zaydi northern Yemen: Sa'dah, Hajjah, Amran, Sana'a, Dhamar, al-Jawf, al-Mahwit. */
    val yemenNorth = Box(14.35, 17.60, 43.40, 45.00)

    val gilgitBaltistan = AnyOf(Box(35.00, 37.10, 72.80, 77.90), Box(34.60, 35.00, 74.50, 77.90))

    /** Kurram (Parachinar). */
    val kurram = Box(33.30, 34.10, 69.80, 70.80)

    /** Hazarajat: Bamyan, Daykundi and the Hazara districts around them. */
    val hazarajat = Box(33.00, 35.30, 65.80, 68.30)

    /** Eastern and southern Libya (spec §10.3). */
    val libyaEastSouth = Where { it.lon > 18.5 || it.lat < 29.5 }

    /** Kazakhstan at and above 48° N, where QMDB's offsets are ±5 rather than ±3. */
    val kazakhNorth = Where { it.lat >= 48.0 }

    /**
     * Within the reach of IRN's Tromsø calendar (ruling R82), whose Makkah-time rule Taqwa follows
     * there: a sun-based takdir cannot be combined with it in order, and it is the only calendar
     * printed for Tromsø, so Automatic follows IRN alone. Read from the unit itself, so the two agree.
     * `no.cautious` resolved here by id still places its IRN member in this unit, rule and all, and
     * cannot keep its order (153 days of 2026 repaired at the unit's point, review M3). No one
     * reaches it here: it is Automatic only elsewhere in Norway, and never a timetable row or a
     * mosque-board candidate. A golden vector or benchmark that resolves entries by id (rulings R84,
     * R85) leaves it out within this reach.
     */
    val tromso = Where { p -> Europe.irnUnits.unit(IRN_TROMSO_UNIT).let { distanceKm(p, it.point) <= it.radiusKm } }

    private const val IRN_TROMSO_UNIT = "no.irn.tromso"

    /** Region overrides, in order; the first that holds wins over the country's own entry. */
    val rules: List<RegionRule> = listOf(
        RegionRule("London inside the M25", "GB", londonM25, "gb.london.lupt"),
        RegionRule("Chicago metro", "US", chicago, "us.chicago"),
        RegionRule("Toronto and the GTA", "CA", toronto, "ca.toronto"),
        RegionRule("Montreal", "CA", montreal, "ca.isna"),
        RegionRule("Ottawa", "CA", ottawa, "ca.isna"),
        RegionRule("Cape Town", "ZA", capeTown, "za.cape"),
        RegionRule("Gaza", "PS", gaza, "ps.gaza.cautious"),
        RegionRule("Iran's Hanafi provinces", "IR", null, "ir.hanafi", iranHanafi),
        RegionRule("Iran's Shafi'i provinces", "IR", null, "ir.shafii", iranShafii),
        RegionRule("Tatarstan", "RU", tatarstan, "ru.dumrt"),
        RegionRule("Dagestan", "RU", dagestan, "ru.dumrd"),
        RegionRule("Bashkortostan", "RU", bashkortostan, "ru.bashkortostan"),
        RegionRule("Dubai", "AE", dubai, "ae.iacad.dubai"),
        RegionRule("Sandžak", "RS", sandzak, "ba.iz"),
        RegionRule("Preševo valley", "RS", presevo, "xk.bik"),
        RegionRule("Tromsø, IRN's calendar", "NO", tromso, "no.irn"),
    )

    /** The Shia-regions list of spec §2.1, besides its whole countries. */
    private val shiaCountries = setOf("IR", "IQ", "BH", "AZ", "LB", "KW")
    private val shiaAreas: List<Pair<String, Area>> = listOf(
        "SA" to saudiEast, "YE" to yemenNorth, "PK" to gilgitBaltistan, "PK" to kurram, "AF" to hazarajat,
    )

    /** Whether [point] in [countryCode] is in the Shia-regions list (the Sunni-times card). */
    fun shia(countryCode: String, point: GeoPoint): Boolean =
        countryCode in shiaCountries || shiaAreas.any { (cc, area) -> cc == countryCode && area.contains(point) }

    /** The region override for [point] in [countryCode], whose first-level region is [admin1], or null. */
    fun ruleFor(countryCode: String, point: GeoPoint, admin1: String? = null): RegionRule? =
        rules.firstOrNull { it.countryCode == countryCode && it.holds(point, admin1) }
}
