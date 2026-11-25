package world.taqwa.app.prayer.engine.registry.authorities

import kotlin.math.ceil
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.distanceKm

/**
 * Bangladesh's 64 districts (rulings R220–R224, the Bangladesh lane of 13 Nov 2026). Since Ramadan 1446 (2025) the
 * Islamic Foundation prints its sehri and iftar calendar a district a page: the end of sehri is true dawn (18°) at the
 * district's east edge with the seconds dropped, the Fajr azan the same dawn at its west edge and iftar sunset at its
 * west edge, each made a full minute; the 1993 committee's three minutes of precaution are gone from it. Its year-round
 * Dhaka table still carries them, and until 2024 a district was Dhaka's day moved by its printed minutes.
 *
 * The app holds both (the owner decides whether the exact rule alone should stand: BUILD-LOG, the Bangladesh entry):
 * [SouthAsia.bangladeshMethod] at the user's own point, which is the 1993 rule at a district's town, and beside it
 * [held] for the rest of a district under either rule. A page is one table for a whole district, so against it the
 * user's own sun can be early by the district's width, and under the 1993 rule by the way to the district's town.
 * Each district's west and east edge is fitted from its page's odd days of Ramadan 1447 (the private archive's
 * `fit_edges.py`; only the fitted longitudes are here, rounded outward to 0.01°). The page's even days are the cells
 * the fit never saw, at the town and read again at both fitted edges; the rows past an edge, off the town's latitude
 * and of the 1993 rule away from the town were added while the hold was shaped on them and are counted with the fit
 * (R224). The archive holds a district's page for Ramadan only, and the app holds no district borders: the districts
 * are therefore the Foundation's published method, not a checked timetable, until the owner rules otherwise.
 */
object Bangladesh {

    /** A district: its town, where its unit sits, and the longitudes its page was printed for. */
    class District(val id: String, val name: String, val town: GeoPoint, val west: Double, val east: Double)

    private fun district(name: String, lat: Double, lon: Double, west: Double, east: Double) =
        District(name.lowercase().replace("'", "").replace(' ', '-'), name, GeoPoint(lat, lon), west, east)

    val districts: List<District> = listOf(
        district("Dhaka", 23.7104, 90.4074, west = 90.01, east = 90.52),
        district("Narayanganj", 23.6135, 90.5030, west = 90.42, east = 90.76),
        district("Kishoreganj", 24.4394, 90.7829, west = 90.58, east = 91.28),
        district("Gazipur", 23.9984, 90.4223, west = 90.15, east = 90.71),
        district("Gopalganj", 23.0051, 89.8266, west = 89.67, east = 90.15),
        district("Tangail", 24.2498, 89.9166, west = 89.74, east = 90.34),
        district("Narsingdi", 23.9230, 90.7177, west = 90.56, east = 90.99),
        district("Faridpur", 23.6061, 89.8406, west = 89.46, east = 90.26),
        district("Madaripur", 23.1710, 90.2094, west = 89.91, east = 90.37),
        district("Manikganj", 23.8577, 90.0096, west = 89.67, east = 90.27),
        district("Munshiganj", 23.5422, 90.5305, west = 90.17, east = 90.72),
        district("Rajbari", 23.7574, 89.6444, west = 89.31, east = 89.89),
        district("Shariatpur", 23.2182, 90.3508, west = 90.16, east = 90.62),
        district("Lakshmipur", 22.9443, 90.8300, west = 90.65, east = 91.03),
        district("Rangamati", 22.6432, 92.1919, west = 91.93, east = 92.62),
        district("Brahmanbaria", 23.9746, 91.1123, west = 90.70, east = 91.32),
        district("Bandarban", 22.1953, 92.2195, west = 92.04, east = 92.75),
        district("Feni", 23.0144, 91.3966, west = 91.25, east = 91.59),
        district("Noakhali", 22.8667, 91.1000, west = 90.91, east = 91.40),
        district("Chandpur", 23.2271, 90.6543, west = 90.53, east = 91.03),
        district("Chattogram", 22.3384, 91.8317, west = 91.43, east = 92.20),
        district("Khagrachhari", 23.1079, 91.9701, west = 91.73, east = 92.24),
        district("Cumilla", 23.4619, 91.1850, west = 90.61, east = 91.37),
        district("Cox's Bazar", 21.4397, 92.0096, west = 91.84, east = 92.42),
        district("Sirajganj", 24.4577, 89.7080, west = 89.22, east = 89.85),
        district("Rajshahi", 24.3740, 88.6011, west = 88.26, east = 88.98),
        district("Bogura", 24.8510, 89.3711, west = 88.98, east = 89.78),
        district("Pabna", 24.0064, 89.2372, west = 89.01, east = 89.74),
        district("Natore", 24.4111, 88.9867, west = 88.83, east = 89.34),
        district("Naogaon", 24.8042, 88.9488, west = 88.38, east = 89.14),
        district("Joypurhat", 25.1015, 89.0273, west = 88.92, east = 89.33),
        district("Chapai Nawabganj", 24.5903, 88.2744, west = 88.02, east = 88.53),
        district("Satkhira", 22.7082, 89.0719, west = 88.92, east = 89.39),
        district("Jashore", 23.1697, 89.2137, west = 88.84, east = 89.57),
        district("Meherpur", 23.7622, 88.6318, west = 88.56, east = 88.89),
        district("Magura", 23.4873, 89.4199, west = 89.21, east = 89.67),
        district("Bagerhat", 22.6566, 89.7912, west = 89.53, east = 89.96),
        district("Narail", 23.1551, 89.4952, west = 89.37, east = 89.82),
        district("Jhenaidah", 23.5450, 89.1726, west = 88.67, east = 89.42),
        district("Chuadanga", 23.6402, 88.8418, west = 88.62, east = 89.04),
        district("Khulna", 22.8098, 89.5644, west = 89.25, east = 89.76),
        district("Kushtia", 23.9028, 89.1194, west = 88.70, east = 89.41),
        district("Bhola", 22.6876, 90.6440, west = 90.52, east = 91.06),
        district("Barishal", 22.7050, 90.3701, west = 90.01, east = 90.75),
        district("Barguna", 22.1590, 90.1262, west = 89.88, east = 90.39),
        district("Pirojpur", 22.5797, 89.9752, west = 89.86, east = 90.21),
        district("Patuakhali", 22.3683, 90.3458, west = 90.09, east = 90.67),
        district("Jhalokathi", 22.6406, 90.1987, west = 90.03, east = 90.39),
        district("Sylhet", 24.8990, 91.8720, west = 91.62, east = 92.51),
        district("Moulvibazar", 24.4889, 91.7708, west = 91.57, east = 92.34),
        district("Habiganj", 24.3804, 91.4130, west = 91.14, east = 91.69),
        district("Sunamganj", 25.0689, 91.4024, west = 90.92, east = 91.76),
        district("Lalmonirhat", 25.9172, 89.4460, west = 88.94, east = 89.59),
        district("Rangpur", 25.7466, 89.2517, west = 88.93, east = 89.54),
        district("Panchagarh", 26.3334, 88.5578, west = 88.32, east = 88.79),
        district("Nilphamari", 25.9318, 88.8560, west = 88.71, east = 89.20),
        district("Dinajpur", 25.6275, 88.6378, west = 88.38, east = 89.30),
        district("Thakurgaon", 26.0310, 88.4699, west = 88.08, east = 88.65),
        district("Gaibandha", 25.3288, 89.5281, west = 89.15, east = 89.78),
        district("Kurigram", 25.8072, 89.6295, west = 89.46, east = 89.90),
        district("Sherpur", 25.0188, 90.0175, west = 89.88, east = 90.34),
        district("Mymensingh", 24.7564, 90.4065, west = 90.04, east = 90.84),
        district("Netrokona", 24.8835, 90.7290, west = 90.44, east = 91.28),
        district("Jamalpur", 24.9197, 89.9481, west = 89.65, east = 90.25),
    )

    /**
     * [method] at [user] holding the district tables (R220, and R224 of the fix round). The app holds no district
     * borders, so every district whose town lies within [PAGE_REACH_KM] and whose fitted longitudes hold the place may
     * be the user's own, and each of the Foundation's two rules is held for all of them:
     *
     * - the exact rule (the pages since Ramadan 1446): Fajr and Maghrib keep at least the seconds dawn and sunset take
     *   to reach the westernmost of their west edges, the end of eating as many before the dawn as the easternmost east
     *   edge's dawn comes sooner, each with [PAGE_SPARE] for the latitude between the place and the edge the page was
     *   computed at;
     * - the 1993 rule (a district's day is Dhaka's moved by its printed minutes, which is the day of its town): a place
     *   east of a district's town has its own dawn and sunset sooner than the town, so Fajr and Maghrib wait the seconds
     *   they take to reach the westernmost such town; a place west of a town has its dawn later, so the end of eating
     *   comes as many seconds sooner as the easternmost town's dawn does. [method]'s own minutes are kept on top: they
     *   are the rule at the town itself, and its district edge is the spare for the latitude between place and town.
     *
     * A district stays in the exact rule's hold [EAST_SLACK] past its fitted east edge: the fit places an edge only to
     * about a tenth of a degree, and just past it the hold would otherwise fall to nothing at once. The 1993 hold keeps
     * to the fitted edges and [BAND_SLACK]: carried as far, Tangail's town alone would cost Dhaka itself two minutes. With no
     * borders a neighbouring district's town is held as well as the user's own, which is the later side and costs up to
     * about two and three-quarter minutes (the widest way from a town to its page's edge, 0.66°); the owner decides
     * whether the 1993 rule should still be held across a district (BUILD-LOG, the Bangladesh entry).
     *
     * A time moves [SECONDS_PER_DEGREE] a degree of longitude. Only Fajr, Maghrib and the end of eating move: they are
     * the three times a district table prints.
     */
    fun held(method: TimetableMethod, user: GeoPoint): TimetableMethod {
        val within = districts.filter { user.lon >= it.west - BAND_SLACK && distanceKm(user, it.town) <= PAGE_REACH_KM }
        val pages = within.filter { user.lon <= it.east + EAST_SLACK }
        if (pages.isEmpty()) return method
        val towns = pages.filter { user.lon <= it.east + BAND_SLACK }
        val west = seconds(user.lon - pages.minOf { it.west }) + PAGE_SPARE
        val east = seconds(pages.maxOf { it.east } - user.lon) + PAGE_SPARE
        val townWest = towns.maxOfOrNull { seconds(user.lon - it.town.lon) } ?: 0
        val townEast = towns.maxOfOrNull { seconds(it.town.lon - user.lon) } ?: 0
        val fajr = maxOf(0, townWest, west - (method.authorityMinutes.fajr * 60 + method.margins.fajr))
        val maghrib = maxOf(0, townWest, west - (method.authorityMinutes.maghrib * 60 + method.margins.maghrib))
        val end = maxOf(0, townEast, east + method.endOfEatingMarginSeconds)
        if (fajr == 0 && maghrib == 0 && end == 0) return method
        return method.copy(
            margins = method.margins.copy(fajr = method.margins.fajr + fajr, maghrib = method.margins.maghrib + maghrib),
            endOfEatingMarginSeconds = method.endOfEatingMarginSeconds - end,
        )
    }

    private fun seconds(degrees: Double): Int = ceil(degrees * SECONDS_PER_DEGREE).toInt()

    /**
     * A district town's unit (R221): the town and [UNIT_KM] around it, as Egypt's and Oman's towns. It keeps the entry's
     * kind, the Foundation's published method (R224, the fix round's ruling 1): the archive holds a district's page for
     * Ramadan only, and the hold beside it is a rule the held tables do not prove day by day. Its own tables' days are
     * still checked at the town by the gate, so the unit is measured and its figure is a real one; the limits are the
     * entry's ([SouthAsia.bangladesh]).
     */
    private fun unit(d: District) = AuthorityUnit(
        id = d.id, name = d.name, point = d.town, radiusKm = UNIT_KM,
        // Dhaka's year-round table is kivabe.com's copy (ruling R276, [SouthAsia.bangladesh]).
        partlyRepublished = d.id == "dhaka",
    )

    /**
     * The units (R221). A unit carries no fixed point: a page is printed for its district's edges, not for its town,
     * and [held] reads those; the town's point would only move the times no page prints. Beyond every unit the same
     * method, the same hold and the same kind.
     */
    val units = UnitSet(
        "bd.ifb",
        districts.map(::unit),
        around = { user, _, placed -> held(placed.copy(fixedPoint = null), user) },
    ) { user -> held(SouthAsia.bangladeshMethod, user) }

    /** A unit's reach, as the Egyptian and Omani towns'. */
    private const val UNIT_KM = 20.0

    /** Past this far from a district's town no place is the district's (the largest reach about 100 km). */
    private const val PAGE_REACH_KM = 120.0

    /** Degrees of longitude a fitted edge may fall short of the real one. */
    private const val BAND_SLACK = 0.02

    /**
     * Degrees past its fitted east edge a district's page is still held under the exact rule: the two fits of a west
     * edge (by the Fajr azan and by iftar) part by up to 0.11°, and an east edge has one fit only.
     */
    private const val EAST_SLACK = 0.12

    /** Seconds a degree of longitude moves dawn and sunset. */
    private const val SECONDS_PER_DEGREE = 240.0

    /** Seconds for the latitude between a place and the edge its district's page was computed at. */
    private const val PAGE_SPARE = 30
}
