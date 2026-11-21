package world.taqwa.app.prayer

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.distanceKm
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Which countries a raw position fix may be in, and the bundled city it is named after (rulings R256 and R259, the
 * second trust audit's G4 and G5, 14–15 Nov 2026). A fix carries neither a country nor a region, and the engine picks
 * the timetable by the country (`Registry.automaticEntry`), Algeria's and Iran's units by the region too, so what is
 * chosen here decides whose table a GPS user is held to.
 *
 * Until R256 it was simply the nearest bundled city, and a border place whose nearest city stands across the border
 * was handed the neighbour's entry alone: 22 of the Kyrgyz Muftiate's 116 localities went to Kazakhstan, Uzbekistan or
 * Tajikistan (eating ending up to 30 min after the Muftiate's printed Fajr, Maghrib and Isha early), Karkamış to Syria
 * (Maghrib 6 min before Diyanet's table), Madha and Dibba Al-Baya to the UAE, Temburong to Sarawak, Tel al-Sultan to
 * Egypt (Fajr and Maghrib 8 min before the Iftaa's Rafah table), Farkhana to Spain.
 *
 * The device's time zone is the one other fact about the place a phone gives: iOS, and Android with location time
 * zone detection, set it from the position against zone boundaries, which follow borders. But it is not always the
 * place's: Android without that detection takes it from the network it is registered on, a phone near a border is
 * often on the neighbour's, and a zone can be set by hand. R256 trusted the zone over a nearer city, and so moved a
 * Johor Bahru phone on a Singapore network to Singapore's timetable (Fajr minutes before JAKIM's on every day). Neither
 * fact settles the country alone, so where they disagree the place is held to both (R259):
 *
 * 1. The city is always the nearest bundled city: its name is the header's, and its country and region the place's
 *    own ([Choice.city]).
 * 2. Nothing besides when the zone does not say otherwise: a zone the list does not name (UTC, an old alias), or one
 *    whose countries include the nearest city's (a country counts in a zone with [MIN_ZONE_CITIES] cities there, or
 *    as the zone's main country, so that a stray row, Georgia's one city in Europe/Moscow, does not make the zone
 *    Georgia's too), or follow the one entry they all follow there (Western Sahara's and Morocco's, both Habous).
 * 3. Otherwise also the nearest city of the zone's countries ([Choice.beside]), whose country and region the place
 *    may be in as well, when it stands within [PLAUSIBLE_KM], or within [PLAUSIBLE_RATIO] times the nearest city's
 *    distance where that is further (a desert: the Rub' al Khali's Saudi places 650 km from a Saudi city and 300 km
 *    from an Omani one); or wherever it stands when the fix is inside the reach of one of that entry's units
 *    (Habous's Lagouira, 12 km from Nouadhibou and 330 km from Dakhla, where Mauritania's entry starts Dhuhr up to 5
 *    min before Habous's held table). The engine then holds the day to both countries' entries (`Registry.border`:
 *    each start the later, each end the earlier, "Cautious times"), so it is never before the table of the side the
 *    place is on, whichever fact was wrong.
 * 4. Nothing besides when the zone's countries have no city that near and no unit there: a zone set by hand far
 *    away, or not yet moved after a journey.
 *
 * What is left, said plainly: where the zone agrees with the nearest city but the place is across the border from
 * both (Madha on a UAE network, Karkamış on a Syrian one), the neighbour's entry alone is kept, exactly as before
 * R256; so it is under a zone two countries share (a Kosovo town nearest a Serbian city under Europe/Belgrade). Only a
 * bundled border lookup settles those, and none is bundled. A zone that is now a link's target (Asia/Riyadh for
 * Asia/Kuwait, Asia/Kuching for Asia/Brunei) is not merged with its link's country on purpose: a Saudi town nearest a
 * Kuwaiti city under Asia/Riyadh must keep Saudi Arabia beside it, so a Kuwaiti phone reporting Asia/Riyadh is held to
 * both (later, never early). Within a country the region is still the nearest city's (an Algerian wilaya, an Iranian
 * province), except that a beside city's is dropped where the units go by the point ([REGION_BY_POINT]).
 *
 * Plain data and arithmetic: tools/timetables compiles it, and its `GpsCountryTest` resolves every gate point this way,
 * under its own zone and under every neighbouring country's.
 */
object FixCountry {

    /** How far the zone's nearest city may stand from the fix for the place to be held to the zone's country too (step 3). */
    const val PLAUSIBLE_KM = 200.0

    /** ... or this many times the nearest city's own distance, where that is further (step 3). */
    const val PLAUSIBLE_RATIO = 3.0

    /** How many of its cities a country needs in a zone for the zone to count as its (step 2), unless it is the zone's main country. */
    const val MIN_ZONE_CITIES = 3

    /**
     * The countries whose units are chosen by the point where no region is known (Brunei's districts, Algeria's
     * wilayas, ruling R219): a beside city's region is not carried there, since that city can stand in another
     * district than the fix (Bangar, in Temburong, beside Bandar Seri Begawan in Brunei-Muara).
     */
    val REGION_BY_POINT: Set<String> = setOf("BN", "DZ")

    /** The region a beside city's [region] in [countryCode] is carried as: null where the units go by the point. */
    fun besideRegion(countryCode: String, region: String?): String? =
        region?.ifEmpty { null }?.takeIf { countryCode.uppercase() !in REGION_BY_POINT }

    /** Why [choice] chose what it chose. */
    enum class Reason {
        /** The device zone is not one the list names: the nearest city alone. */
        ZONE_UNKNOWN,

        /** The nearest city's country is one of the zone's countries, or follows the one entry they all follow here. */
        NEAREST,

        /** The zone's countries disagree with the nearest city's: the nearest city, and beside it the zone's countries' nearest. */
        ZONE,

        /**
         * The same, beyond the plausible distance, because the fix stands inside the reach of a unit of the entry the
         * zone's countries follow there.
         */
        ZONE_UNIT,

        /** The zone's countries disagree, but none of their cities is within the plausible distance: the nearest city alone. */
        ZONE_TOO_FAR,
    }

    /**
     * [city]: the nearest bundled city, [km] from the fix, whose name, country and region the place takes. [beside]:
     * where the zone says the place may be in another country ([Reason.ZONE], [Reason.ZONE_UNIT]), that country's
     * nearest city, [besideKm] away, whose country and region the place may be in as well; null otherwise.
     */
    class Choice<T>(val city: T, val reason: Reason, val km: Double, val beside: T? = null, val besideKm: Double? = null)

    /**
     * The cities, sorted by latitude once, so that a nearest-city search stops as soon as the latitude alone puts every
     * city left further than the best found (a great circle is never shorter than its difference of latitude). Exact:
     * the city found is the one a scan of the whole list finds, the first in the list's order on a tie.
     */
    class Index<T>(
        items: List<T>,
        private val latOf: (T) -> Double,
        private val lonOf: (T) -> Double,
        val countryOf: (T) -> String,
        zoneOf: (T) -> String,
    ) {
        private val order: IntArray = items.indices.sortedBy { latOf(items[it]) }.toIntArray()
        private val sorted: List<T> = order.map { items[it] }
        private val lats = DoubleArray(sorted.size) { latOf(sorted[it]) }
        private val lons = DoubleArray(sorted.size) { lonOf(sorted[it]) }
        private val zoneCountries: Map<String, Set<String>> = run {
            val counts = HashMap<String, HashMap<String, Int>>()
            for (item in items) {
                val byCountry = counts.getOrPut(zoneOf(item)) { HashMap() }
                val country = countryOf(item)
                byCountry[country] = (byCountry[country] ?: 0) + 1
            }
            counts.mapValues { (_, byCountry) ->
                val main = byCountry.maxOf { it.value }
                byCountry.filter { (_, n) -> n >= MIN_ZONE_CITIES || n == main }.keys
            }
        }

        /**
         * The countries whose cities the list puts in zone [zoneId], each with [MIN_ZONE_CITIES] cities there or the
         * zone's main one; empty for a zone it does not name.
         */
        fun countriesOf(zoneId: String): Set<String> = zoneCountries[zoneId].orEmpty()

        /** The nearest city to [lat], [lon] that [accept] takes, and its distance, within [withinKm]; null when none is. */
        fun nearest(lat: Double, lon: Double, withinKm: Double = Double.MAX_VALUE, accept: ((T) -> Boolean)? = null): Pair<T, Double>? {
            if (sorted.isEmpty()) return null
            var hi = lowerBound(lat)
            var lo = hi - 1
            var best = -1
            var bestKm = withinKm
            while (lo >= 0 || hi < sorted.size) {
                val below = if (lo >= 0) lat - lats[lo] else Double.MAX_VALUE
                val above = if (hi < sorted.size) lats[hi] - lat else Double.MAX_VALUE
                val up = above <= below
                if ((if (up) above else below) * KM_PER_DEGREE > bestKm) break
                val i = if (up) hi++ else lo--
                if (accept != null && !accept(sorted[i])) continue
                val km = haversineKm(lat, lon, lats[i], lons[i])
                if (km < bestKm || (km == bestKm && (best < 0 || order[i] < order[best]))) {
                    best = i
                    bestKm = km
                }
            }
            return if (best < 0) null else sorted[best] to bestKm
        }

        private fun lowerBound(lat: Double): Int {
            var a = 0
            var b = lats.size
            while (a < b) {
                val m = (a + b) ushr 1
                if (lats[m] < lat) a = m + 1 else b = m
            }
            return a
        }
    }

    /** The city a fix at [lat], [lon] under device zone [deviceZone] is named after: the nearest bundled city. */
    fun <T> choose(lat: Double, lon: Double, deviceZone: String?, index: Index<T>): T? = choice(lat, lon, deviceZone, index)?.city

    /** The nearest city, the reason, and the zone's countries' nearest city where the place may be in one of them too; null only for an empty list. */
    fun <T> choice(lat: Double, lon: Double, deviceZone: String?, index: Index<T>): Choice<T>? {
        val (nearest, nearestKm) = index.nearest(lat, lon) ?: return null
        val zone = deviceZone ?: return Choice(nearest, Reason.ZONE_UNKNOWN, nearestKm)
        val zoneCountries = index.countriesOf(zone)
        if (zoneCountries.isEmpty()) return Choice(nearest, Reason.ZONE_UNKNOWN, nearestKm)
        if (index.countryOf(nearest) in zoneCountries) return Choice(nearest, Reason.NEAREST, nearestKm)
        // The entries the zone's countries follow here. Where they all follow one, a country that follows it too is the
        // zone's for this purpose: Western Sahara's cities are in Africa/El_Aaiun and Morocco's in Africa/Casablanca,
        // both follow Habous, so a Moroccan phone in Western Sahara keeps its nearest city, and Habous. Where they follow
        // several (Europe/Belgrade: Serbia's and Kosovo's), the zone's own countries alone: North Macedonia follows
        // Serbia's entry, and a Kosovo town nearer a Macedonian city must still be held to Kosovo's.
        val follows = HashMap<String, String>()
        fun entryOf(country: String) = follows.getOrPut(country) { Registry.automaticEntry(Place(lat, lon, zone, country)).id }
        val zoneEntries = zoneCountries.mapTo(HashSet()) { entryOf(it) }
        val oneEntry = zoneEntries.singleOrNull()
        fun zones(country: String) = country in zoneCountries || (oneEntry != null && entryOf(country) == oneEntry)
        if (zones(index.countryOf(nearest))) return Choice(nearest, Reason.NEAREST, nearestKm)
        val zoneCity: (T) -> Boolean = { city -> zones(index.countryOf(city)) }
        val limit = max(PLAUSIBLE_KM, PLAUSIBLE_RATIO * nearestKm)
        index.nearest(lat, lon, withinKm = limit, accept = zoneCity)?.let { (city, km) ->
            return Choice(nearest, Reason.ZONE, nearestKm, beside = city, besideKm = km)
        }
        // No city of the zone's near enough, but the fix stands inside the reach of one of the units those entries hold
        // a table for (Habous's Lagouira, 12 km from Nouadhibou and 330 from Dakhla): the place may be theirs.
        val point = GeoPoint(lat, lon)
        val inUnit = zoneEntries.any { id -> Units.of(id)?.units?.any { distanceKm(point, it.point) <= it.radiusKm } == true }
        if (inUnit) {
            index.nearest(lat, lon, accept = zoneCity)?.let { (city, km) ->
                return Choice(nearest, Reason.ZONE_UNIT, nearestKm, beside = city, besideKm = km)
            }
        }
        return Choice(nearest, Reason.ZONE_TOO_FAR, nearestKm)
    }

    private const val EARTH_RADIUS_KM = 6371.0
    private const val KM_PER_DEGREE = EARTH_RADIUS_KM * PI / 180.0

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val la1 = lat1 * PI / 180.0
        val la2 = lat2 * PI / 180.0
        val dLat = la2 - la1
        val dLon = (lon2 - lon1) * PI / 180.0
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }
}
