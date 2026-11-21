package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.TableEnvelope
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Americas
import world.taqwa.app.prayer.engine.registry.authorities.Egypt
import world.taqwa.app.prayer.engine.registry.authorities.Europe
import world.taqwa.app.prayer.engine.registry.authorities.Gulf
import world.taqwa.app.prayer.engine.registry.authorities.Kemenag
import world.taqwa.app.prayer.engine.registry.data.JakimZoneCities
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.edgeRideKm
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.GateRow
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Writes the envelope's promise-only rows, `official/gate/<group>-envelope.tsv` (the city-point round 2 review of 11 Oct
 * 2026, "nothing in the gate guards the round's main claim"). Under the owner's rule "rather late, never early" a place is
 * never before any table printed for its own point whose bound reach holds it, nor any end after one, not only the table
 * it follows (`TableEnvelope`). So every such table's own rows (its group file's rows at the table's point) are replayed
 * at every app city (`cities.csv`) of its entry's countries within its bound reach ([EnvelopeRows.SETS]: R40's class D
 * reach, three minutes of longitude), whichever unit the city follows, or none; and past every bound reach, the nearest
 * table's rows at every app city within its ride (`TableEnvelope.rideKm`, five minutes of longitude: the owner's decision
 * of 17 Oct 2026; Wifaqul's where it is chosen on its own), as Rabita's Helsinki calendar's at the app's Finnish cities
 * and on rings around Helsinki within its ride (`Europe.rabitaRide`), and ISNA's Montréal and Ottawa mosques' rows under
 * Canada's cautious times wherever those are followed and a mosque rides beside them ([EnvelopeRows.CROSS],
 * `Units.crossRide`). And Habous's places ([EnvelopeRows.HABOUS], ruling R198): every unit's own rows wherever the engine
 * rides its table beside another unit's, at the app's cities of Morocco and Western Sahara and at Habous's other points.
 * And by name ([EnvelopeRows.NAMED]): the Takvim at the app's
 * Kosovo cities, Kénitra's Habous table at Sidi Taibi, Lakemba's tables at Sydney's west, London's Unified Timetable where
 * it is chosen outside the M25 and still applies (within its area, about 104 km), IACAD's tables where they are chosen
 * outside its zones, and Diyanet's Sydney table where Diyanet's preset is chosen around Sydney (the owner's decision of
 * 17 Oct 2026). And the Kyrgyz Muftiate's localities where their reaches overlap ([EnvelopeRows.kyrgyzOverlaps], ruling
 * R205): each locality's own table at the places inside its reach that follow another locality.
 * And JAKIM's zones ([EnvelopeRows.JAKIM_PLACES], the review round of 13 Nov 2026): the zones are districts
 * whose borders are not held, so at every Malaysian city of the app's list that is no listed town of a zone, and at the
 * places the zones' own lists name, the table of the zone the place takes and of every zone riding beside it there is
 * replayed at the place itself.
 * And Kemenag's kab/kota
 * beyond their circles, as far as the edge carries each one's allowance ([KemenagRings.gateRings] and
 * [KemenagRings.farPoints], ruling R211): each one's own table short of its carry's end, and on rings out to it for the
 * units whose print departs from the method.
 * And ESA's Egyptian towns in the belt past their reaches ([EnvelopeRows.egyptBelt], ruling R213): each town's own table
 * wherever it rides beside the edge.
 * And Oman's 86 localities past their own circles on the edge ([EnvelopeRows.omanRings], ruling R210): each locality's
 * own table just outside its circle, midway through its ride and just inside the ride's end, wherever no other
 * locality's circle holds the place, and wherever else it rides at the app's cities and the audit's points.
 * And QMDB's places the same way ([EnvelopeRows.kazakhOverlaps], ruling R249): each place's own tables wherever a
 * place inside its reach follows another of QMDB's places, and at the ground it shares with its neighbours.
 * And past every Kyrgyz reach, Egyptian belt and Omani ride ([EnvelopeRows.edgeRings], ruling R260, the second audit of
 * 14 Nov 2026): each unit's own tables wherever the engine reads them beside the edge's day, on rings out to the end of
 * the edge's ride and at the app's cities past every inner line.
 *
 * The gate holds these rows to the promise alone ([GateRow.promiseOnly]): an early start or a late end breaks it as
 * anywhere, but their lateness, against a table the city may not follow, is no unit's figure and no limit's. A row the
 * group files already hold at that city (the app's own point rows) is not written again.
 *
 *     ./gradlew -p tools/timetables generateEnvelopeRows
 *     ./gradlew -p tools/timetables checkEnvelopeRows
 *
 * Re-run after a table printed for its own point, its rows or its reach change, or after `prove` adds a year to a table's
 * rows, then run the gate and commit. `EnvelopeCoverageTest` fails while the committed files miss a place the generator
 * would write now: where the generator asks the engine where a table rides ([EnvelopeRows.Named.rides]), a ride changed
 * in the engine cannot leave the files behind it unnoticed. Coordinates are the app's own, as `cities.csv` gives them;
 * no printed time is written.
 *
 * `--check` writes nothing: it names every file a run would write, change or remove and exits 1 when there is one (the
 * audit of 13 Nov 2026: the generator is a pure function of the gate's group files, the engine's reaches and the app's
 * cities, so a run on a tree whose files are current changes nothing; the committed files had fallen behind because
 * `prove` appends a new capture's rows to the group file and nothing replayed them). It is the release cut's check and
 * not a test of the monitor's: `prove` runs the tests right after it appends rows, and those rows' replays are a later
 * run's.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) ?: fail("$name needs a value") }
    val repo = File(option("--repo") ?: fail("--repo is required"))
    val gate = repo.resolve("tools/timetables/official/gate")
    val cities = EnvelopeRows.cities(repo.resolve("shared/src/commonMain/composeResources/files/cities.csv"))
    val written = EnvelopeRows.render(gate, cities)
    val stale = gate.listFiles { f -> f.isFile && f.name.endsWith(GateRow.PROMISE_ONLY_SUFFIX) }.orEmpty().map { it.name }.toSet() - written.keys
    if ("--check" in args) {
        val behind = EnvelopeRows.behind(gate, written)
        if (behind.isEmpty()) {
            println("checkEnvelopeRows: ${written.size} files, ${written.values.sumOf { it.rows }} rows — every committed file is what a run would write.")
            return
        }
        for (line in behind) System.err.println("checkEnvelopeRows: $line")
        System.err.println("checkEnvelopeRows: ${behind.size} file(s) behind the gate's group files: run generateEnvelopeRows, then the gate, and commit.")
        exitProcess(1)
    }
    for (name in stale) gate.resolve(name).delete()
    for ((name, file) in written) {
        gate.resolve(name).writeText(file.text)
        println("$name: ${file.rows} rows at ${file.cities} cities (${file.skipped} the group file already holds)")
    }
}

private fun fail(problem: String): Nothing {
    System.err.println("generateEnvelopeRows: $problem")
    exitProcess(2)
}

/** The envelope's rows and the tables and places they are read at. */
object EnvelopeRows {
    /** One app city: its name, country, point and zone; or a point the app does not list ([listed] false). */
    class City(val name: String, val cc: String, val at: GeoPoint, val zone: String, val listed: Boolean = true)

    /**
     * The sets of tables printed for their own points (`tablesAtTheirPoints`), by the entry or cautious member whose units
     * they are: each table's bound reach is R40's class D reach, the envelope's own (`TableEnvelope`).
     */
    val SETS = listOf(
        "us.isna", "ca.isna", "ca.isha90", "ca.fifteen", "ca.eighteen", "us.chicago.eighteen", "gb.wifaqul", "in.karachi",
        "pk.karachi", "uz.board", "ps.gaza.awqaf", "lb.fatwa", "sy.awqaf", "iq.sunni",
    )

    /**
     * The group files of tables each printed for one city (Dawat-e-Islami's city cards, rulings R190 and R194): their rows at
     * a unit's own point name that unit (`pk.karachi/<unit>`), and are replayed only as far as the unit's table is bound
     * (`TableEnvelope.holding`: a card's own unit; the table's own card out to the table's bound), never in a ride past it.
     */
    val CITY_CARDS = setOf("pk-dawateislami.tsv", "in-dawateislami.tsv")

    /**
     * Points the app does not list, checked beside its cities (the review of 13 Nov 2026, ruling R194): Karachi's own suburbs
     * east of the city, past Karachi's unit and within its table's bound, where the app lists no city and a user there by
     * GPS follows the city's card.
     */
    val POINTS = mapOf(
        "pk.karachi" to listOf(
            City("Bahria Town Karachi (a point the app does not list)", "PK", GeoPoint(25.03, 67.31), "Asia/Karachi", listed = false),
            City("Gulshan-e-Hadeed (a point the app does not list)", "PK", GeoPoint(24.865, 67.36), "Asia/Karachi", listed = false),
            City("a point east of Karachi near Dhabeji (the app lists none there)", "PK", GeoPoint(24.80, 67.45), "Asia/Karachi", listed = false),
        ),
    )

    /**
     * A named check: [source]'s rows matched by [rows], replayed at the app's [cities] of [cc] (by name, or the point given),
     * and at every app city of [cc] where [rides] holds. [rides] asks the engine where it rides the table ([ridesAny]: the
     * app's own resolution), never a reach of the generator's own, so a ride widened or narrowed in the engine moves these
     * rows with it the next time they are written, and `EnvelopeCoverageTest` fails until they are (the Q4 review of 17 Oct
     * 2026). [chosen], for a table checked where a user chooses it, is its entry: every city must be one where that choice
     * applies (`Registry.inScope`), or the row would test a choice the app does not honour there. [why] ends each row's
     * note: the claim it stands for.
     */
    class Named(
        val what: String,
        val source: String,
        val rows: (GateRow) -> Boolean,
        val cc: String,
        val cities: List<String>,
        val chosen: String? = null,
        val rides: ((City) -> Boolean)? = null,
        val why: String = "the round's named claim (round 2's envelope)",
    )

    /**
     * Whether entry [entryId], chosen at [city], rides any of [points] there: the app's own resolution of the entry at the
     * city ([Registry.resolveEntry]) carries one of them as its fixed point, as a start, end or envelope point, or as a
     * day alongside (`TimetableMethod.alongside`, the nearest table's ride past its bound, the owner's decision of 17 Oct
     * 2026, question 1).
     */
    fun ridesAny(entryId: String, city: City, points: List<GeoPoint>): Boolean {
        val entry = requireNotNull(Registry.byId(entryId)) { "no entry $entryId" }
        val m = Registry.resolveEntry(entry, Place(city.at.lat, city.at.lon, city.zone, city.cc)).method ?: return false
        val ridden = listOfNotNull(m.fixedPoint) + m.startPoints + m.endPoints + m.envelopePoints + m.alongside.map { it.point }
        return ridden.any { it in points }
    }

    private fun at(row: GateRow, lat: Double, lon: Double) = row.lat != null && row.lon != null && abs(row.lat - lat) < 1e-6 && abs(row.lon - lon) < 1e-6

    /**
     * The round's named claims (the round 2 review): BIK's Takvim (its base point's rows) at every app city in Kosovo; Kénitra's
     * Habous table at Sidi Taibi, Kénitra's own unit since every Habous place is one (R195); Lakemba's tables (LMA's and Diyanet's Sydney table) at
     * Sydney's west; LUPT where it is chosen outside the M25, within its area (R45's three reaches, about 104 km: beyond, the
     * owner's decision of 17 Oct 2026 put it out of scope, so Ipswich, Margate, Dover, Norwich, Peterborough, Southampton,
     * Birmingham, Bristol, Manchester, Leeds, Glasgow and Edinburgh, which round 2's fix held, are held no more); IACAD's
     * Dubai table where it is chosen in the other emirates, Hatta's at Fujairah and Dubai Rural's at Al Ain. And the owner's
     * decision of 17 Oct 2026 (question 4): Diyanet's Sydney table where Diyanet's preset is chosen, at every app city where
     * the chosen preset rides one of the table's plausible points (`Americas.sydneyDiyanetPoints`; today class D's reach of
     * them, 69 km).
     */
    val NAMED = listOf(
        Named("BIK's Takvim (the base point's table)", "xk-bik.tsv", { it.entry == "xk.bik" && it.unit == null && at(it, 42.5, 21.0) }, "XK", listOf("*")),
        Named("Kénitra's Habous table", "ma-habous.tsv", { it.entry == "ma.habous" && it.unit == null && it.path.endsWith("/kenitra.txt") }, "MA", listOf("@34.18698,-6.68176")),
        Named(
            "Lakemba's tables", "au-cautious.tsv", { it.entry == "au.cautious" && at(it, -33.92, 151.0756) }, "AU",
            listOf("Fairfield", "Liverpool", "Blacktown", "Parramatta"),
        ),
        Named(
            "London's Unified Timetable, chosen", "gb-london-lupt.tsv", { it.entry == "gb.london.lupt" && it.unit == "gb.london.lupt" }, "GB",
            listOf("Basildon", "Sevenoaks", "St Albans", "Luton", "Reading", "Folkestone"),
            chosen = "gb.london.lupt",
        ),
        Named(
            "IACAD's Dubai table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "dubai" }, "AE",
            listOf("Sharjah", "Ajman", "Fujairah", "Al Ain City"), chosen = "ae.iacad.dubai",
        ),
        Named(
            "IACAD's Hatta table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "hatta" }, "AE", listOf("Fujairah"),
            chosen = "ae.iacad.dubai",
        ),
        Named(
            "IACAD's Dubai Rural table, chosen", "ae-iacad.tsv", { it.entry == "ae.iacad.dubai" && it.unit == "dubai-rural" }, "AE",
            listOf("Al Ain City"), chosen = "ae.iacad.dubai",
        ),
        Named(
            "Diyanet's Sydney table, its preset chosen", "au-diyanet.tsv", { it.entry == "other.turkey" && at(it, -33.92, 151.0756) }, "AU",
            emptyList(), chosen = "other.turkey", rides = { c -> ridesAny("other.turkey", c, Americas.sydneyDiyanetPoints.map { it.point }) },
            why = "the owner's decision of 17 Oct 2026 (question 4)",
        ),
    )

    /**
     * A place JAKIM's own zone list names that is no town of the app's list (the review round of 13 Nov 2026), and [zone],
     * the zone the list names it under: the zone's table must be the place's own there or ride beside it, or the generator
     * stops. The points are general geography (as `JakimZones.Zone.places`), which is why every zone around rides.
     */
    class JakimPlace(val name: String, val at: GeoPoint, val zone: String)

    /**
     * The border towns the review found before their own zone's print on a neighbour's table (Pulau Sebang of Melaka by
     * Tampin, Hulu Bernam of Selangor by Tanjong Malim, the Kedah bank at Selama, Mahang), the hill places of PHG06, and
     * the point and the named places of each of the ten zones that hold no city of the app's list.
     */
    val JAKIM_PLACES = listOf(
        JakimPlace("Pulau Sebang", GeoPoint(2.455, 102.235), "MLK01"),
        JakimPlace("Hulu Bernam", GeoPoint(3.672, 101.523), "SGR01"),
        JakimPlace("Selama's Kedah bank", GeoPoint(5.2245, 100.684), "KDH05"),
        JakimPlace("Mahang", GeoPoint(5.33, 100.75), "KDH05"),
        JakimPlace("Genting Highlands", GeoPoint(3.4236, 101.7932), "PHG06"),
        JakimPlace("Bukit Fraser", GeoPoint(3.712, 101.737), "PHG06"),
        JakimPlace("Gunung Jerai's summit", GeoPoint(5.787, 100.435), "KDH07"),
        JakimPlace("Bukit Tinggi", GeoPoint(3.35, 101.82), "PHG05"),
        JakimPlace("Genting Sempah", GeoPoint(3.353, 101.77), "PHG05"),
        JakimPlace("Janda Baik", GeoPoint(3.33, 101.86), "PHG05"),
        JakimPlace("Kuala Rompin", GeoPoint(2.805, 103.489), "PHG07"),
        JakimPlace("Tanjung Gemok", GeoPoint(2.667, 103.617), "PHG07"),
        JakimPlace("Pulau Banding", GeoPoint(5.5506, 101.3489), "PRK04"),
        JakimPlace("the Temengor dam", GeoPoint(5.4069, 101.3003), "PRK04"),
        JakimPlace("Bukit Larut", GeoPoint(4.862, 100.793), "PRK07"),
        JakimPlace("Beluran", GeoPoint(5.895, 117.555), "SBH02"),
        JakimPlace("Telupid", GeoPoint(5.628, 117.126), "SBH02"),
        JakimPlace("Gunung Kinabalu's summit", GeoPoint(6.075, 116.558), "SBH06"),
        JakimPlace("Kampung Patarikan", GeoPoint(4.96, 115.505), "SWK09"),
        JakimPlace("Jerteh", GeoPoint(5.7336, 102.4897), "TRG02"),
        JakimPlace("Kuala Besut", GeoPoint(5.83, 102.557), "TRG02"),
        JakimPlace("Bandar Permaisuri", GeoPoint(5.52, 102.75), "TRG02"),
        JakimPlace("Kuala Berang", GeoPoint(5.0725, 103.0117), "TRG03"),
    )

    /** The zone a place takes under JAKIM's entry (null at the edge) and the zones riding beside it there, as the app resolves it. */
    class JakimAt(val taken: AuthorityUnit?, val riding: List<AuthorityUnit>)

    fun jakimAt(at: GeoPoint, zone: String): JakimAt {
        val set = requireNotNull(Units.of(JAKIM)) { "no units for $JAKIM" }
        val resolved = Registry.resolveEntry(requireNotNull(Registry.byId(JAKIM)), Place(at.lat, at.lon, zone, "MY"))
        val beside = resolved.method?.alongside.orEmpty().map { it.point }
        return JakimAt(set.units.firstOrNull { it.id == resolved.unitId }, set.units.filter { it.point in beside })
    }

    /** Whether [at] is a town of the app's list a zone's own list holds: there the zone is on the list, and nothing rides. */
    fun jakimListedTown(at: GeoPoint): Boolean = JakimZoneCities.byZone.values.any { towns -> towns.any { it.lat == at.lat && it.lon == at.lon } }

    private const val JAKIM = "my.jakim"

    private const val JAKIM_SOURCE = "my-jakim.tsv"

    /** Where a place's own zone is read with its limits (G28, ruling R274). */
    private const val JAKIM_CITIES = "my-jakim-cities.tsv"

    /** The words a group's file opens with beyond the common ones. */
    private val ABOUT_ALSO = mapOf(
        "my-jakim" to listOf(
            "JAKIM's zones (the review round of 13 Nov 2026): the zones are districts whose borders are not held, so a place",
            "near a border, in a zone with no town of the app's list or under no zone of the list takes the nearest listed",
            "town's zone with every zone that could hold it riding beside. Here each such place (the app's Malaysian cities that",
            "are no listed town, the border and hill places the review named, the ten townless zones' named places) is read",
            "against the table of every zone riding beside it: never before any of them. The table of the zone it takes is",
            "read in my-jakim-cities.tsv, its limits checked (the second trust audit's G28, ruling R274).",
        ),
    )

    fun cities(file: File): List<City> = file.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
        .map { City(it[1], it[4], GeoPoint(it[5].toDouble(), it[6].toDouble()), it[7]) }

    /** One written file: its text, rows, cities and the rows left out because the group file holds them. */
    class Written(val text: String, val rows: Int, val cities: Int, val skipped: Int)

    /** A row to write: its source row, the city and why, and the entry and member it is checked under where not its own. */
    private class Out(val row: GateRow, val city: City, val note: String, val entry: String = row.entry, val member: String? = row.member)

    /**
     * A cautious [entry] that rides another entry's [tables] (Units.crossRide), checked under [member], its most-followed
     * member: a table riding beside the place is shown on the safe side whatever the far-north guard says (DayComputer.safeSide),
     * and the most-followed member's Maghrib is the one the guard always reads as printed (ruling R156), so its Maghrib is
     * checked as printed on every day: ISNA's Montréal and Ottawa mosques beside Canada's cautious times.
     */
    class Cross(val entry: String, val tables: String, val member: String)

    val CROSS = listOf(Cross("ca.cautious", "ca.isna", "ca.eighteen"))

    /**
     * Morocco's entry, whose units ride each other (ruling R198): every unit whose own 25 km holds a place rides beside the
     * nearest one's table there, as its own day at its own point. Its rows are the units' own (`ma.habous/<unit>`).
     */
    const val HABOUS = "ma.habous"

    /** Rabita's ring around Helsinki: every [RABITA_RING_BEARINGS] degrees, at each of [RABITA_RINGS_KM], inside its ride. */
    fun rabitaRing(): List<City> = RABITA_RINGS_KM.flatMap { km ->
        (0 until 360 step RABITA_RING_BEARINGS).map { bearing ->
            City("a point ${fmt1(km)} km from Helsinki at $bearing°", "FI", destination(Europe.RABITA_HELSINKI, km, bearing.toDouble()), "Europe/Helsinki")
        }
    }.also { ring -> require(ring.all { Europe.rabitaRides(it.at) }) { "a ring point outside Rabita's ride" } }

    /**
     * The rings' distances from Helsinki: just past a minute's reach (13.8 km), across Diyanet's Helsinki unit (41.5 km) and
     * the region's 45 km edge, out to the ride's end.
     */
    private val RABITA_RINGS_KM = listOf(14.0, 20.0, 30.0, 38.0, 41.0, 42.0, 44.9, 45.1, 49.0, 55.0, 62.0, 69.0)
    private const val RABITA_RING_BEARINGS = 15

    /** The point [km] from [from] at [bearing] degrees (great circle, the earth radius `distanceKm` uses). */
    private fun destination(from: GeoPoint, km: Double, bearing: Double): GeoPoint {
        val d = km / 6371.0
        val b = Math.toRadians(bearing)
        val la1 = Math.toRadians(from.lat)
        val lo1 = Math.toRadians(from.lon)
        val la2 = Math.asin(Math.sin(la1) * Math.cos(d) + Math.cos(la1) * Math.sin(d) * Math.cos(b))
        val lo2 = lo1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(la1), Math.cos(d) - Math.sin(la1) * Math.sin(la2))
        return GeoPoint(Math.toDegrees(la2), Math.toDegrees(lo2))
    }

    /**
     * The Kyrgyz Muftiate's entry: its 115 localities and Aravan town are units a minute of longitude wide (about 21 km),
     * printed with no boundaries, so their reaches overlap and a place inside two of them may follow either table
     * (ruling R205, the audit of 12 Nov 2026: `CentralAsia.kyrgyzstanUnits` rides every other locality whose reach holds
     * the place).
     */
    const val KYRGYZ = "kg.default"

    /** The ring just inside a locality's edge, where another table is farthest from the place: 24 bearings at this share. */
    private const val KYRGYZ_EDGE_SHARE = 0.995
    private val KYRGYZ_EDGE_BEARINGS: List<Double> = (0 until 24).map { 7.5 + 15.0 * it }

    /**
     * The audit's own probe points (12 Nov 2026, findings F1 and F8): between Karakol and Kyzyl-Suu, between Bishkek,
     * Sokuluk and Manas airport, and between Sokuluk and Manas airport. Read against every table whose reach holds them,
     * the table they follow too.
     */
    val KYRGYZ_PROBES: List<GeoPoint> = listOf(GeoPoint(42.4079, 78.2035), GeoPoint(42.87, 74.44), GeoPoint(42.9463, 74.3809))

    /** A Kyrgyz place inside [table]'s reach: where, and whether [table]'s own unit is the one it follows. */
    class Overlap(val table: AuthorityUnit, val place: City, val follows: AuthorityUnit, val probe: Boolean)

    /**
     * Where each Kyrgyz locality's own table is read beside the table the place follows (ruling R205): inside the
     * locality's reach, every place that follows another locality (the reach rows, `kg-default-reach.tsv`, leave exactly
     * those out) among the reach rows' grid ([ReachRows.BEARINGS] at [ReachRows.SHARES] of its radius), a ring just inside
     * its edge ([KYRGYZ_EDGE_BEARINGS] at [KYRGYZ_EDGE_SHARE]), and the app's Kyrgyz [cities]; and, against the table
     * whichever locality the place follows, the middle of the ground each pair of reaches shares ([OverlapSweep.middle]:
     * a pair whose shared ground is too thin for the grid and the ring is still held both ways) and the audit's probe
     * points ([KYRGYZ_PROBES]) against every table whose reach holds them. Asked of the engine's own units and
     * resolution, never a list of pairs kept here.
     */
    fun kyrgyzOverlaps(cities: List<City>): List<Overlap> {
        val units = requireNotNull(Units.of(KYRGYZ)) { "no units for $KYRGYZ" }.units
        val entry = requireNotNull(Registry.byId(KYRGYZ))
        val zone = "Asia/Bishkek"
        val byId = units.associateBy { it.id }
        fun follows(at: GeoPoint): AuthorityUnit? = Registry.resolveEntry(entry, Place(at.lat, at.lon, zone, "KG")).unitId?.let(byId::get)
        val out = ArrayList<Overlap>()
        for (unit in units) {
            val radius = fmt1(unit.radiusKm)
            val grid = ReachRows.SHARES.flatMap { share -> ReachRows.BEARINGS.map { share to it } } + KYRGYZ_EDGE_BEARINGS.map { KYRGYZ_EDGE_SHARE to it }
            val around = grid.map { (share, bearing) ->
                val at = QmdbReach.moved(unit.point, unit.radiusKm * share, bearing).let { GeoPoint(fmt6(it.lat).toDouble(), fmt6(it.lon).toDouble()) }
                City("a point at ${fmt1(share * 100)} % of its $radius km reach, bearing ${fmt1(bearing)}", "KG", at, zone)
            }
            val app = cities.filter { it.cc == "KG" }.map { City("the app's ${it.name}", it.cc, it.at, it.zone) }
            for (c in around + app) {
                if (distanceKm(c.at, unit.point) > unit.radiusKm) continue
                val other = follows(c.at) ?: error("${c.name} of ${unit.id} follows no unit inside its reach")
                if (other.id != unit.id) out += Overlap(unit, c, other, probe = false)
            }
            for (other in units) {
                if (other.id == unit.id) continue
                // One point a pair, whichever side asks: both tables are read at the same place.
                val middle = (if (unit.id < other.id) OverlapSweep.middle(unit, other) else OverlapSweep.middle(other, unit)) ?: continue
                val held = follows(middle) ?: error("the middle of ${unit.id} and ${other.id} follows no unit")
                out += Overlap(unit, City("the middle of the ground its reach shares with ${other.name}'s", "KG", middle, zone), held, probe = true)
            }
            for (probe in KYRGYZ_PROBES) {
                if (distanceKm(probe, unit.point) > unit.radiusKm) continue
                val other = follows(probe) ?: error("the probe ${probe.lat}, ${probe.lon} follows no unit")
                out += Overlap(unit, City("the audit's probe point", "KG", probe, zone), other, probe = true)
            }
        }
        return out
    }

    /** ESA's entry: 78 towns of 20 km (Cairo 30), and beyond every one of them the edge (`Egypt.units`). */
    const val EGYPT = "eg.esa"

    /**
     * Where a town's table is read in the belt past its reach: just past the reach, the middle of the belt and just
     * inside its end (`Egypt.BESIDE_REACHES`), as shares of the town's own reach, at the reach rows' bearings.
     */
    val EGYPT_BELT_SHARES: List<Double> = listOf(1.025, 1.5, 1.975)

    /** A place past every Egyptian town's reach where [table] rides beside the edge. */
    class Beside(val table: AuthorityUnit, val place: City)

    /**
     * Where each Egyptian town's own table is read beside the edge (ruling R213, the audit of 12 Nov 2026, finding F13):
     * past every town's reach, wherever the engine itself says the town rides (`Egypt.beside`), among
     * [ReachRows.BEARINGS] at [EGYPT_BELT_SHARES] of its reach and the app's Egyptian [cities] (Banhā and Girga are two
     * of those that stand there). A place a town's reach holds is left out: the reach rows hold it
     * (`eg-esa-reach.tsv`). Asked of the engine's own units and resolution, never a list kept here.
     */
    fun egyptBelt(cities: List<City>): List<Beside> {
        val units = requireNotNull(Units.of(EGYPT)) { "no units for $EGYPT" }.units
        val entry = requireNotNull(Registry.byId(EGYPT))
        val zone = "Africa/Cairo"
        val app = cities.filter { it.cc == "EG" }.map { City("the app's ${it.name}", it.cc, it.at, it.zone) }
        val out = ArrayList<Beside>()
        for (unit in units) {
            val radius = fmt1(unit.radiusKm)
            val around = EGYPT_BELT_SHARES.flatMap { share ->
                ReachRows.BEARINGS.map { bearing ->
                    val at = QmdbReach.moved(unit.point, unit.radiusKm * share, bearing).let { GeoPoint(fmt6(it.lat).toDouble(), fmt6(it.lon).toDouble()) }
                    City("a point at ${fmt1(share * 100)} % of its $radius km reach, bearing ${fmt1(bearing)}", "EG", at, zone)
                }
            }
            for (c in around + app) {
                if (Registry.resolveEntry(entry, Place(c.at.lat, c.at.lon, zone, "EG")).unitId != null) continue
                // Past every belt a town rides by ruling R260 instead: those are edgeRings' ([EDGE_LINES]).
                if (units.none { distanceKm(c.at, it.point) <= it.radiusKm * Egypt.BESIDE_REACHES }) continue
                if (Egypt.beside(c.at).none { it.id == unit.id }) continue
                out += Beside(unit, c)
            }
        }
        return out
    }

    /**
     * QMDB's entry: 5,676 places, each a unit as wide as its own table was measured to carry alone, printed with no
     * boundaries, so their reaches overlap (up to 183 hold one point) and a place inside several waits for every such
     * place's table (ruling R249, the owner's ruling of 13 Nov 2026: `CentralAsia.kazakhstanUnits` rides each of them).
     */
    const val KAZAKH = "kz.qmdb"

    /** Of a place's neighbours with no table held, the ones whose shared ground is read: this many, spread around it. */
    private const val KAZAKH_NEIGHBOURS = 16

    /** QMDB's densest ground (Maktaaral and Zhetysay's villages): read against every held table whose reach holds it. */
    val KAZAKH_PROBES: List<GeoPoint> = listOf(GeoPoint(40.686322, 68.285832), GeoPoint(40.901015, 68.453861))

    /**
     * Where each of QMDB's places with a table held ([tables]: the units with rows at their own point in `kz-qmdb.tsv`)
     * is read beside the table the place follows (ruling R249). As for the Kyrgyz localities ([kyrgyzOverlaps]): inside
     * its reach, every place that follows another unit (the reach rows, `kz-qmdb-reach.tsv`, leave exactly those out)
     * among the reach rows' grid, a ring just inside its edge and the app's Kazakh [cities]; and, whichever unit the
     * place follows, the middle of the ground its reach shares ([OverlapSweep.middle]) with every neighbour that has a
     * table held too (both tables read at the one point, so every such pair is held both ways), with every neighbour
     * of another band (QMDB's offsets change at 46° N and 48° N), and with [KAZAKH_NEIGHBOURS] of its other neighbours
     * spread around it by bearing (a wide sample: Shymkent's reach alone shares ground with 248 others); and QMDB's
     * densest ground ([KAZAKH_PROBES]). Asked of the engine's own units and resolution.
     */
    fun kazakhOverlaps(cities: List<City>, tables: List<AuthorityUnit>): List<Overlap> {
        val units = requireNotNull(Units.of(KAZAKH)) { "no units for $KAZAKH" }.units
        val entry = requireNotNull(Registry.byId(KAZAKH))
        val zone = "Asia/Almaty"
        val byId = units.associateBy { it.id }
        val held = tables.map { it.id }.toSet()
        fun follows(at: GeoPoint): AuthorityUnit? = Registry.resolveEntry(entry, Place(at.lat, at.lon, zone, "KZ")).unitId?.let(byId::get)
        fun band(u: AuthorityUnit) = if (u.point.lat >= 48) 2 else if (u.point.lat >= 46) 1 else 0
        fun bearing(from: GeoPoint, to: GeoPoint) =
            Math.toDegrees(Math.atan2((to.lon - from.lon) * Math.cos(Math.toRadians(from.lat)), to.lat - from.lat))
        val app = cities.filter { it.cc == "KZ" }.map { City("the app's ${it.name}", it.cc, it.at, it.zone) }
        val out = ArrayList<Overlap>()
        for (unit in tables) {
            val radius = fmt1(unit.radiusKm)
            val grid = ReachRows.SHARES.flatMap { share -> ReachRows.BEARINGS.map { share to it } } + KYRGYZ_EDGE_BEARINGS.map { KYRGYZ_EDGE_SHARE to it }
            val around = grid.map { (share, bearing) ->
                val at = QmdbReach.moved(unit.point, unit.radiusKm * share, bearing).let { GeoPoint(fmt6(it.lat).toDouble(), fmt6(it.lon).toDouble()) }
                City("a point at ${fmt1(share * 100)} % of its $radius km reach, bearing ${fmt1(bearing)}", "KZ", at, zone)
            }
            for (c in around + app) {
                if (distanceKm(c.at, unit.point) > unit.radiusKm) continue
                val other = follows(c.at) ?: error("${c.name} of ${unit.id} follows no unit inside its reach")
                if (other.id != unit.id) out += Overlap(unit, c, other, probe = false)
            }
            val neighbours = units.filter { it.id != unit.id && distanceKm(unit.point, it.point) < unit.radiusKm + it.radiusKm }
            val rest = neighbours.filter { it.id !in held && band(it) == band(unit) }.sortedBy { bearing(unit.point, it.point) }
            val sample = if (rest.size <= KAZAKH_NEIGHBOURS) rest else (0 until KAZAKH_NEIGHBOURS).map { rest[it * rest.size / KAZAKH_NEIGHBOURS] }
            for (other in neighbours.filter { it.id in held || band(it) != band(unit) } + sample) {
                // One point a pair, whichever side asks: where both have tables, both are read at the same place.
                val middle = (if (unit.id < other.id) OverlapSweep.middle(unit, other) else OverlapSweep.middle(other, unit)) ?: continue
                val place = follows(middle) ?: error("the middle of ${unit.id} and ${other.id} follows no unit")
                out += Overlap(unit, City("the middle of the ground its reach shares with ${other.name}'s", "KZ", middle, zone), place, probe = true)
            }
            for (probe in KAZAKH_PROBES) {
                if (distanceKm(probe, unit.point) > unit.radiusKm) continue
                val other = follows(probe) ?: error("the probe ${probe.lat}, ${probe.lon} follows no unit")
                out += Overlap(unit, City("QMDB's densest ground", "KZ", probe, zone), other, probe = true)
            }
        }
        return out
    }

    /**
     * An entry whose units print by locality with no boundaries, and the line past which ruling R260 rides the nearest
     * unit's table beside the edge: the Kyrgyz Muftiate's reach, ESA's belt (`Egypt.BESIDE_REACHES`, ruling R213), the
     * Omani ministry's ride (`Gulf.omanRideKm`, ruling R210). [group] is the group file whose unit rows are replayed.
     */
    class EdgeLine(val entry: String, val group: String, val cc: String, val zone: String, val line: String, val innerKm: (AuthorityUnit) -> Double)

    val EDGE_LINES = listOf(
        EdgeLine(KYRGYZ, "kg-default.tsv", "KG", "Asia/Bishkek", "reach") { it.radiusKm },
        EdgeLine(EGYPT, "eg-esa.tsv", "EG", "Africa/Cairo", "belt") { it.radiusKm * Egypt.BESIDE_REACHES },
        EdgeLine(OMAN, "om-mara.tsv", "OM", "Asia/Muscat", "ride") { Gulf.omanRideKm(it) },
    )

    /** How far past its inner line, and how far inside the end of R260's ride, the rings stand. */
    private const val EDGE_RING_KM = 0.5

    /**
     * Where on a bearing each unit's table is sampled past [line]'s inner line ([EdgeLine.innerKm]): [EDGE_RING_KM] past
     * it, midway to the end of R260's ride (`edgeRideKm`, five minutes of longitude) and [EDGE_RING_KM] inside that end.
     */
    fun edgeRingKm(line: EdgeLine, unit: AuthorityUnit): List<Pair<String, Double>> {
        val inner = line.innerKm(unit)
        val ride = edgeRideKm(unit)
        return listOf(
            "a point ${fmt1(EDGE_RING_KM)} km past its ${fmt1(inner)} km ${line.line}" to inner + EDGE_RING_KM,
            "a point midway from its ${line.line} to the end of its ${fmt1(ride)} km edge ride" to (inner + ride) / 2,
            "a point ${fmt1(EDGE_RING_KM)} km inside the end of its ${fmt1(ride)} km edge ride" to ride - EDGE_RING_KM,
        )
    }

    /**
     * Where each unit's own table is read past every inner line (ruling R260, the second audit of 14 Nov 2026, findings
     * G1, G2 and G3): on the 8 [ReachRows.BEARINGS], at each of [edgeRingKm]'s three distances, wherever no unit's reach
     * holds the place and the engine reads the unit's table beside the edge's day there (the nearest unit, or one no more
     * than a quarter farther), whether or not another unit's bigger belt or ride holds the place (ruling R262, the edges
     * lane's second review of 15 Nov 2026: there the nearest rides beside that unit's table); and at the app's cities past
     * every inner line (Egypt's Kom Ombo, Mallawi, Dayrut, al-Qusiyah, Dayr Mawas, Zaafarana and an-Najaylah), every
     * unit read there (a city inside some inner line is [egyptBelt]'s or [omanRings]', which read every table the
     * engine rides there). Asked of the engine's own resolution, never a list kept here: a ride shortened or narrowed in
     * the engine moves these rows with it.
     */
    fun edgeRings(line: EdgeLine, cities: List<City>): List<Beside> {
        val units = requireNotNull(Units.of(line.entry)) { "no units for ${line.entry}" }.units
        val entry = requireNotNull(Registry.byId(line.entry))
        fun resolved(at: GeoPoint) = Registry.resolveEntry(entry, Place(at.lat, at.lon, line.zone, line.cc))
        fun past(at: GeoPoint) = units.none { distanceKm(at, it.point) <= line.innerKm(it) }
        fun read(at: GeoPoint): Set<GeoPoint> {
            val m = requireNotNull(resolved(at).method) { "${line.entry} has no method at ${at.lat}, ${at.lon}" }
            return (m.envelopePoints + m.alongside.map { it.point }).toSet()
        }
        val out = ArrayList<Beside>()
        for (unit in units) {
            for (bearing in ReachRows.BEARINGS) {
                for ((what, km) in edgeRingKm(line, unit)) {
                    val at = QmdbReach.moved(unit.point, km, bearing).let { GeoPoint(fmt6(it.lat).toDouble(), fmt6(it.lon).toDouble()) }
                    if (resolved(at).unitId != null) continue
                    if (unit.point !in read(at)) continue
                    out += Beside(unit, City("$what, bearing ${fmt1(bearing)}", line.cc, at, line.zone, listed = false))
                }
            }
        }
        for (c in cities.filter { it.cc == line.cc }) {
            if (!past(c.at) || resolved(c.at).unitId != null) continue
            val ridden = read(c.at)
            for (unit in units.filter { it.point in ridden }) out += Beside(unit, City("the app's ${c.name}", c.cc, c.at, c.zone))
        }
        return out
    }

    /**
     * Oman's entry: Muscat and the ministry's 85 other localities, each a circle around its own point (30 km, 20 km, Al
     * Hamra 12 km), printed with no boundaries, so a place just past a circle, where no other locality's circle holds it,
     * still reads that locality's table (ruling R210, the audit of 12 Nov 2026: beyond every circle `Gulf.omanUnits` rides
     * a locality's table half again its circle).
     */
    const val OMAN = "om.mara"
    private const val OMAN_ZONE = "Asia/Muscat"

    /** How far past a circle, and how far inside the end of a ride, the rings stand. */
    private const val OMAN_RING_KM = 0.5

    /**
     * The audit's own probe points (12 Nov 2026, findings F7 and F10): Jebel Shams in Al Hamra's north, 13.9 km from the
     * town, and As Sifah east of Muscat, 41.2 km from Muscat's point. Read against every table that rides there.
     */
    val OMAN_PROBES: List<Pair<String, GeoPoint>> = listOf("Jebel Shams" to GeoPoint(23.24, 57.26), "As Sifah" to GeoPoint(23.44, 58.78))

    /** A place where the engine reads [table] beside the place's own day: the locality the place [follows], or none on the edge. */
    class Ring(val table: AuthorityUnit, val place: City, val follows: AuthorityUnit? = null)

    /**
     * Where on a bearing each of Oman's localities' tables is sampled past its circle: [OMAN_RING_KM] past the circle,
     * midway through the ride, and [OMAN_RING_KM] inside the ride's end (`Gulf.omanRideKm`).
     */
    fun omanRingKm(unit: AuthorityUnit): List<Pair<String, Double>> {
        val ride = Gulf.omanRideKm(unit)
        return listOf(
            "a point ${fmt1(OMAN_RING_KM)} km past its ${fmt1(unit.radiusKm)} km circle" to unit.radiusKm + OMAN_RING_KM,
            "a point midway through its ${fmt1(ride)} km ride" to (unit.radiusKm + ride) / 2,
            "a point ${fmt1(OMAN_RING_KM)} km inside the end of its ${fmt1(ride)} km ride" to ride - OMAN_RING_KM,
        )
    }

    /**
     * Where each of Oman's localities' own table is read past its own circle (ruling R210). On the 8 [ReachRows.BEARINGS],
     * at each of [omanRingKm]'s three distances, wherever the place lies beyond every circle (the edge): there the engine
     * must read the locality's table beside the place's own day, and the row holds it to the promise (no start before the
     * table, no sunrise or end of eating after it). A sample inside another locality's circle is left out: that locality's
     * own printed table holds the place (its reach rows), and this one's is not read there (R167 rides a table only inside
     * its own circle). And wherever else the engine reads a table beside the place's own day: the app's Omani [cities],
     * on the edge (R210) or where two circles hold the city (R167), and the audit's points ([OMAN_PROBES]). Asked of the engine's own units, ride and resolution, never a list kept here; so
     * these rows guard the rule as built (a ride shortened in the engine fails `require` here, a table read wrongly fails
     * the gate) and say nothing of the places beyond a ride, which no row here reaches. A sample may fall at sea or across
     * the border, where the app would not resolve Oman's entry: it is replayed all the same, as a point of the rule.
     */
    fun omanRings(cities: List<City>): List<Ring> {
        val units = requireNotNull(Units.of(OMAN)) { "no units for $OMAN" }.units
        val entry = requireNotNull(Registry.byId(OMAN))
        val byId = units.associateBy { it.id }
        fun resolved(at: GeoPoint) = Registry.resolveEntry(entry, Place(at.lat, at.lon, OMAN_ZONE, "OM"))
        // The localities whose table the engine reads at [at] beside the place's own day (not the one it follows).
        fun riders(at: GeoPoint): List<AuthorityUnit> {
            val r = resolved(at)
            val m = requireNotNull(r.method) { "$OMAN has no method at ${at.lat}, ${at.lon}" }
            val read = (m.envelopePoints + m.alongside.map { it.point }).toSet()
            return units.filter { it.id != r.unitId && it.point in read }
        }
        fun rounded(at: GeoPoint) = GeoPoint(fmt6(at.lat).toDouble(), fmt6(at.lon).toDouble())
        val out = ArrayList<Ring>()
        for (unit in units) {
            for (bearing in ReachRows.BEARINGS) {
                for ((what, km) in omanRingKm(unit)) {
                    val at = rounded(QmdbReach.moved(unit.point, km, bearing))
                    if (resolved(at).unitId != null) continue
                    require(unit in riders(at)) { "$OMAN/${unit.id}: its table does not ride at $what, bearing ${fmt1(bearing)}, on the edge" }
                    out += Ring(unit, City("$what, bearing ${fmt1(bearing)}", "OM", at, OMAN_ZONE))
                }
            }
        }
        for (c in cities.filter { it.cc == "OM" }) {
            val follows = resolved(c.at).unitId?.let(byId::get)
            for (unit in riders(c.at)) out += Ring(unit, City("the app's ${c.name}", c.cc, c.at, c.zone), follows)
        }
        for ((name, probe) in OMAN_PROBES) {
            require(resolved(probe).unitId == null) { "the audit's $name is no longer on the edge" }
            val riding = riders(probe).also { require(it.isNotEmpty()) { "no table rides at the audit's $name" } }
            for (unit in riding) out += Ring(unit, City("the audit's probe point, $name", "OM", probe, OMAN_ZONE))
        }
        return out
    }

    /** What a written file's header says its rows are, where not the tables printed for their own points ([SETS]). */
    private val ABOUT: Map<String, List<String>> = mapOf(
        "eg-esa" to listOf(
            "# (ruling R213, the audit of 12 Nov 2026, finding F13). eg-esa.tsv's tables, each town's own (the rows of its unit),",
            "# replayed past its reach wherever the town rides beside the edge (Egypt.beside: out to twice its reach, 40 km and",
            "# Cairo's 60, and past every other town's reach): at the reach rows' bearings just past its reach, in the middle of",
            "# the belt and just inside its end, and at the app's Egyptian cities that stand there. ESA prints by town, with no",
            "# boundaries: this near a town, where no town's reach holds the place (a place inside a reach follows that town's",
            "# table alone, ruling R165, and has no row here), a place is never before its table, nor any end after it. The",
            "# gate checks the promise alone here, on these sampled points and the days the tables hold; the lateness, against",
            "# a table the place does not follow, is no unit's figure and no limit's.",
            "# And past every belt (ruling R260, the second audit's G2, 14 Nov 2026): the nearest town's table, and any town no",
            "# more than a quarter farther, wherever it rides beside the edge out to five minutes of longitude from its point:",
            "# on the same bearings just past the belt, midway to that ride's end and just inside it, and at the app's cities past",
            "# every belt (Kom Ombo, Mallawi, Dayrut, al-Qusiyah, Dayr Mawas, Zaafarana, an-Najaylah); and past the nearest",
            "# town's own belt where a farther town's bigger belt holds the place (ruling R262, 15 Nov 2026), the nearest riding",
            "# beside that town's.",
            "# Coordinates and the tables' own paths only, never a time.",
        ),
        "kz-qmdb" to listOf(
            "# (ruling R249, the owner's ruling of 13 Nov 2026). kz-qmdb.tsv's tables, each of QMDB's places' own (the rows at its",
            "# point), replayed inside its reach wherever the place follows another of QMDB's places (the reach rows,",
            "# kz-qmdb-reach.tsv, leave those out): on the reach rows' grid, on a ring just inside its edge and at the app's cities;",
            "# and, whichever place is followed, at the middle of the ground its reach shares with every neighbour that has a",
            "# table held too (both tables there, so every such pair is held both ways), with every neighbour of another band",
            "# (46 and 48 N) and with 16 of its other neighbours spread around it, and at QMDB's densest ground against every",
            "# held table whose reach holds it. QMDB prints by place, with no boundaries, so a place inside several reaches",
            "# waits for every such place's table: never before any of them, nor any end after one. The gate checks the promise",
            "# alone here; the lateness is no unit's figure and no limit's. Coordinates and the tables' own paths only, never a time.",
        ),
        "kg-default" to listOf(
            "# (ruling R205, the audit of 12 Nov 2026). kg-default.tsv's tables, each locality's own (the rows at its point), replayed",
            "# inside its reach wherever the place follows another locality (the reach rows, kg-default-reach.tsv, leave those",
            "# out): on the reach rows' grid, on a ring just inside its edge and at the app's cities; and, whichever locality",
            "# the place follows, at the middle of the ground each pair of reaches shares (both tables there, so every pair is",
            "# held both ways) and at the audit's probe points against every table whose reach holds them. The Muftiate prints",
            "# by locality, with no boundaries, so a place inside two reaches may follow either table: never before any of them,",
            "# nor any end after one. The gate checks the promise alone here; the lateness is no unit's figure and no limit's.",
            "# And past every reach (ruling R260, the second audit's G1, 14 Nov 2026): the nearest locality's table, and any",
            "# locality no more than a quarter farther, wherever it rides beside the edge out to five minutes of longitude from",
            "# its point: on 8 bearings just past the reach, midway to that ride's end and just inside it.",
            "# Coordinates and the tables' own paths only, never a time.",
        ),
        "id-kemenag" to listOf(
            "# (ruling R211, the audit of 12 Nov 2026, finding F6, and the reviews of 13 Nov 2026). id-kemenag.tsv's tables, each",
            "# kab/kota's own, replayed beyond its circle where the edge carries that unit's allowance with what the distance",
            "# from its point needs (Kemenag.outsideAt), wherever no other circle holds the point: every unit's at the four",
            "# diagonals half a kilometre short of the carry's end (54.5 km from its point); the units whose own allowance is",
            "# more than the whole plain edge's (Kab. Puncak Jaya and Kab. Lanny Jaya: their Isya, and the end of eating before",
            "# their Subuh), carried a degree, on rings just outside the circle, at 35, 54.5 and 82.5 km and half a kilometre",
            "# short of that end, at the reach rows' bearings; and a unit's listed far ground (Bawean, Kab. Gresik's) at its",
            "# own point and on a ring short of its edge. A circle stops short of the nearest other kab/kota, so the ground",
            "# beyond it is still the unit's own on every other side: never before its table there, nor any end after it.",
            "# Beyond the carry the edge is the plain published method and no table is read against it. The gate checks the",
            "# promise alone here; the lateness is no unit's figure and no limit's. Coordinates and the tables' own paths",
            "# only, never a time.",
        ),
        "om-mara" to listOf(
            "# (ruling R210, the audit of 12 Nov 2026, findings F7, F10 and F12). om-mara.tsv's tables, each locality's own,",
            "# replayed past its own circle on the edge, where no other locality's circle holds the place and the engine reads",
            "# the table beside the place's own day (half again the circle): on 8 bearings 0.5 km outside the circle, midway",
            "# through the ride and 0.5 km inside its end, and wherever else a table is read beside the place's own: at the",
            "# app's cities (on the edge, or where two circles hold the city, ruling R167) and the audit's probe points (Jebel",
            "# Shams against Al Hamra's table, As Sifah against Muscat's). Never before the table there, nor sunrise or the",
            "# end of eating after it. And past every ride (ruling R260, the second audit's G3, 14 Nov 2026): the nearest",
            "# locality's table, and any locality no more than a quarter farther, wherever it rides beside the edge out to five",
            "# minutes of longitude from its point: on the same bearings just past the ride, midway to that ride's end and just",
            "# inside it, also where a farther locality's bigger ride holds the place (ruling R262, 15 Nov 2026: the nearest",
            "# rides beside it there). No ring sample inside another locality's circle is written: that locality's own table",
            "# holds the place.",
            "# Some samples fall at sea or across the border; they are points of the rule all the same. The gate checks",
            "# the promise alone here; the lateness is no unit's figure and no limit's. Coordinates and the tables' own",
            "# paths only, never a time.",
        ),
    )

    /**
     * What a run would change in [gate], one line a file and none when the committed files are what [written] holds:
     * a file it would write anew, one whose rows it would change (with the count of rows added and dropped), and a
     * promise-only file it would remove.
     */
    fun behind(gate: File, written: Map<String, Written>): List<String> {
        fun rows(text: String) = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        val lines = ArrayList<String>()
        for ((name, file) in written) {
            val committed = gate.resolve(name)
            if (!committed.isFile) { lines += "$name is not written yet (${file.rows} rows)"; continue }
            val now = committed.readText()
            if (now == file.text) continue
            val (old, new) = rows(now) to rows(file.text)
            val oldSet = old.toSet()
            val newSet = new.toSet()
            lines += "$name would change: ${new.count { it !in oldSet }} rows to add, ${old.count { it !in newSet }} to drop" +
                if (oldSet == newSet) " (the same rows: their order or the file's header)" else ""
        }
        val stale = gate.listFiles { f -> f.isFile && f.name.endsWith(GateRow.PROMISE_ONLY_SUFFIX) }.orEmpty().map { it.name }.toSet() - written.keys
        for (name in stale.sorted()) lines += "$name would be removed: no table writes it now"
        return lines
    }

    fun render(gate: File, cities: List<City>): Map<String, Written> {
        val manifest = GateManifest.load(gate)
        val base = manifest.rows.filter { !it.promiseOnly }
        val own = base.filter { !it.source.endsWith("-reach.tsv") }
        val held = base.map { key(it.source, it.path, it.entry, it.member, it.lat, it.lon) }.toSet()
        val out = LinkedHashMap<String, MutableList<Out>>()

        for (setId in SETS) {
            val set = Units.of(setId) ?: error("no units for $setId")
            val rowsOf = set.units.associateWith { unit ->
                own.filter {
                    (it.entry == setId && it.member == null || it.member == setId) && at(it, unit.point.lat, unit.point.lon) &&
                        (it.unit == null || it.unit == unit.id && it.source in CITY_CARDS)
                }
                    .also { require(it.isNotEmpty()) { "$setId/${unit.id}: no gate row at the table's own point" } }
            }
            val envelope = requireNotNull(set.envelope) { "$setId rides no envelope" }
            for (c in cities + POINTS[setId].orEmpty()) {
                val held = envelope.holding(c.at)
                for ((unit, why) in riders(envelope, c.at)) {
                    // A city's card holds a place only within its bound, never in a ride past it (CITY_CARDS).
                    val rows = rowsOf.getValue(unit).filter { it.unit == null || unit in held }
                    val countries = rows.flatMap { requireNotNull(Registry.byId(it.entry)).countries }.toSet()
                    if (c.cc !in countries) continue
                    val place = if (c.listed) "the app's ${c.name}" else c.name
                    val note = "${unit.name}'s own table at $place, ${fmt1(distanceKm(c.at, unit.point))} km, $why"
                    for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
                }
            }
        }
        // The Kyrgyz Muftiate's localities where their reaches overlap (ruling R205): each locality's own rows, written
        // under the bare entry (the place resolves to another unit), wherever a place inside its reach follows another.
        val kyrgyz = kyrgyzOverlaps(cities)
        val kyrgyzRows = kyrgyz.map { it.table }.distinct().associateWith { unit ->
            own.filter { it.source == "kg-default.tsv" && it.entry == KYRGYZ && it.unit == unit.id && at(it, unit.point.lat, unit.point.lon) }
                .also { require(it.isNotEmpty()) { "$KYRGYZ/${unit.id}: no gate row at the unit's own point" } }
        }
        for (o in kyrgyz) {
            val km = fmt1(distanceKm(o.place.at, o.table.point))
            val note = if (o.table.id == o.follows.id) {
                "${o.table.name}'s own table at ${o.place.name}, $km km, which follows it, beside the other tables whose reach holds it (ruling R205)"
            } else {
                "${o.table.name}'s own table at ${o.place.name}, $km km, within its ${fmt1(o.table.radiusKm)} km reach where the place follows ${o.follows.name} (ruling R205)"
            }
            for (r in kyrgyzRows.getValue(o.table)) out.getOrPut(r.source) { mutableListOf() } += Out(r, o.place, note)
        }
        // Kemenag's kab/kota beyond their circles (ruling R211): each one's own rows where the edge carries its allowance,
        // under the bare entry (no unit holds the point).
        val kemenag = KemenagRings.units().associateWith { unit ->
            own.filter { it.source == "id-kemenag.tsv" && it.entry == KemenagRings.ENTRY && it.unit == unit.id }
                .also { require(it.isNotEmpty()) { "${KemenagRings.ENTRY}/${unit.id}: no gate row of the unit's own" } }
        }
        for ((unit, rows) in kemenag) {
            for (p in KemenagRings.gateRings(unit, rows.first().zone)) {
                val place = City("a point ${fmt1(p.km)} km from ${unit.name}'s, bearing ${fmt1(p.bearing)}", "ID", p.at, rows.first().zone, listed = false)
                val note = "${unit.name}'s own table at ${place.name}, beyond its ${fmt1(unit.radiusKm)} km circle where the edge carries its allowance (ruling R211)"
                for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, place, note)
            }
        }
        for (ground in Kemenag.farGrounds) {
            val (unit, rows) = kemenag.entries.single { it.key.id == ground.unit }
            for (p in KemenagRings.farPoints(ground, rows.first().zone)) {
                val where = if (p.bearing.isNaN()) "${ground.name}'s own point" else "a point of ${ground.name}'s ring, bearing ${fmt1(p.bearing)}"
                val place = City("$where, ${fmt1(p.km)} km from ${unit.name}'s", "ID", p.at, rows.first().zone, listed = false)
                val note = "${unit.name}'s own table at ${place.name}, its far ground where the edge carries its allowance (ruling R211)"
                for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, place, note)
            }
        }
        // ESA's towns in the belt past their reaches (ruling R213): each town's own rows, written under the bare entry (the
        // place resolves to the edge), wherever the town rides beside it.
        val belt = egyptBelt(cities)
        val beltRows = belt.map { it.table }.distinct().associateWith { unit ->
            own.filter { it.source == "eg-esa.tsv" && it.entry == EGYPT && it.unit == unit.id && it.lat == null && it.lon == null }
                .also { require(it.isNotEmpty()) { "$EGYPT/${unit.id}: no gate row of the town's own table" } }
        }
        for (b in belt) {
            val note = "${b.table.name}'s own table at ${b.place.name}, ${fmt1(distanceKm(b.place.at, b.table.point))} km, past every " +
                "reach where it rides beside the edge (ruling R213)"
            for (r in beltRows.getValue(b.table)) out.getOrPut(r.source) { mutableListOf() } += Out(r, b.place, note)
        }
        // Oman's localities past their own circles on the edge (ruling R210): each locality's own rows, written under the bare
        // entry (the place resolves to no locality, or to another), on its rings and wherever else its table is read.
        val oman = omanRings(cities)
        val omanRows = oman.map { it.table }.distinct().associateWith { unit ->
            own.filter { it.source == "om-mara.tsv" && it.entry == OMAN && it.unit == unit.id }
                .also { require(it.isNotEmpty()) { "$OMAN/${unit.id}: no gate row of the unit's own" } }
        }
        for (o in oman) {
            val where = o.follows?.let { "where the place follows ${it.name} (ruling R167)" } ?: "beyond every circle (ruling R210)"
            val note = "${o.table.name}'s own table at ${o.place.name}, ${fmt1(distanceKm(o.place.at, o.table.point))} km, $where"
            for (r in omanRows.getValue(o.table)) out.getOrPut(r.source) { mutableListOf() } += Out(r, o.place, note)
        }
        // Past every reach, belt and ride (ruling R260): each unit's own rows, written under the bare entry (the place
        // resolves to the edge), wherever the engine reads its table beside the edge's day.
        for (line in EDGE_LINES) {
            val rings = edgeRings(line, cities)
            val ringRows = rings.map { it.table }.distinct().associateWith { unit ->
                own.filter { it.source == line.group && it.entry == line.entry && it.unit == unit.id && (it.lat == null || at(it, unit.point.lat, unit.point.lon)) }
                    .also { require(it.isNotEmpty()) { "${line.entry}/${unit.id}: no gate row of the unit's own" } }
            }
            val lineUnits = requireNotNull(Units.of(line.entry)).units
            for (b in rings) {
                val inside = lineUnits.any { distanceKm(b.place.at, it.point) <= line.innerKm(it) }
                val note = "${b.table.name}'s own table at ${b.place.name}, ${fmt1(distanceKm(b.place.at, b.table.point))} km, " +
                    if (inside) "past its own ${line.line} but inside another's, where it rides beside that one's table (ruling R262)"
                    else "past every ${line.line} where it rides beside the edge (ruling R260)"
                for (r in ringRows.getValue(b.table)) out.getOrPut(r.source) { mutableListOf() } += Out(r, b.place, note)
            }
        }
        // QMDB's places where their reaches overlap (ruling R249), the same way: each held table's own rows.
        val kazakhUnits = requireNotNull(Units.of(KAZAKH)) { "no units for $KAZAKH" }.units
        val kazakhOwn = own.filter { it.source == "kz-qmdb.tsv" && it.entry == KAZAKH && it.unit == null && it.member == null && it.lat != null }
        val kazakhRows = kazakhUnits.associateWith { unit -> kazakhOwn.filter { at(it, unit.point.lat, unit.point.lon) } }.filterValues { it.isNotEmpty() }
        for (o in kazakhOverlaps(cities, kazakhRows.keys.toList())) {
            val km = fmt1(distanceKm(o.place.at, o.table.point))
            val note = if (o.table.id == o.follows.id) {
                "${o.table.name}'s own table at ${o.place.name}, $km km, which follows it, beside the other tables whose reach holds it (ruling R249)"
            } else {
                "${o.table.name}'s own table at ${o.place.name}, $km km, within its ${fmt1(o.table.radiusKm)} km reach where the place follows ${o.follows.name} (ruling R249)"
            }
            for (r in kazakhRows.getValue(o.table)) out.getOrPut(r.source) { mutableListOf() } += Out(r, o.place, note)
        }
        // Rabita's Helsinki calendar from a minute's reach of Helsinki out to its ride (Europe.rabitaRide, the owner's decision
        // of 17 Oct 2026): at the app's Finnish cities there, and at points on rings around Helsinki (rabitaRing), where the
        // app's own cities had all been clean while points between them were not (the reviews of 17 Oct 2026).
        val rabita = own.filter { it.entry == "se.cautious" && it.member == "se.rabita" && at(it, Europe.RABITA_HELSINKI.lat, Europe.RABITA_HELSINKI.lon) }
        require(rabita.isNotEmpty()) { "no row of Rabita's calendar at Helsinki in se-cautious.tsv" }
        val ride = "within its ${fmt1(Europe.RABITA_RIDE_FROM_KM)} to ${fmt1(Europe.RABITA_RIDE_KM)} km ride (the owner's decision of 17 Oct 2026)"
        for (c in cities) {
            if (c.cc != "FI" || !Europe.rabitaRides(c.at)) continue
            val note = "Rabita's Helsinki calendar at the app's ${c.name}, ${fmt1(distanceKm(c.at, Europe.RABITA_HELSINKI))} km, $ride"
            for (r in rabita) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
        }
        for (c in rabitaRing()) {
            val note = "Rabita's Helsinki calendar at ${c.name}, $ride"
            for (r in rabita) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
        }
        // Another entry's tables riding beside a cautious place (Units.crossRide, the owner's decision of 17 Oct 2026): ISNA's
        // Montréal and Ottawa mosques' own rows at every app city of Canada where Canada's cautious times are followed and a
        // mosque's bound or ride holds it, checked under Automatic (CROSS).
        for (cross in CROSS) {
            val set = Units.of(cross.tables) ?: error("no units for ${cross.tables}")
            val envelope = requireNotNull(set.envelope) { "${cross.tables} rides no envelope" }
            val entry = requireNotNull(Registry.byId(cross.entry)) { "no entry ${cross.entry}" }
            require(Units.crossRide(cross.entry)?.tablesEntryId == cross.tables) { "${cross.entry} rides no ${cross.tables} tables" }
            require(entry.members.minBy { it.shareRank }.id == cross.member) { "${cross.member} is not ${cross.entry}'s most-followed member, whose Maghrib the gate always reads as printed (ruling R156)" }

            for (c in cities) {
                if (c.cc !in entry.countries) continue
                if (Registry.automaticEntry(Place(c.at.lat, c.at.lon, c.zone, c.cc)).id != cross.entry) continue
                for ((unit, why) in riders(envelope, c.at)) {
                    val rows = own.filter { it.entry == cross.tables && it.member == null && it.unit == null && at(it, unit.point.lat, unit.point.lon) }
                    require(rows.isNotEmpty()) { "${cross.tables}/${unit.id}: no gate row at the table's own point" }
                    val note = "${unit.name}'s own table beside ${cross.entry} at the app's ${c.name}, ${fmt1(distanceKm(c.at, unit.point))} km, " +
                        "$why, ridden by Automatic there (the owner's decision of 17 Oct 2026)"
                    for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note, cross.entry, cross.member)
                }
            }
        }
        // Habous's places (ruling R198): every unit's own rows wherever the engine rides its table beside the place's own unit's
        // (the app's own resolution, never a reach of the generator's), at every app city of Morocco and Western Sahara and at
        // every other unit's point. The place's own unit's rows are the group file's (its lateness is that unit's figure).
        run {
            val set = Units.of(HABOUS) ?: error("no units for $HABOUS")
            val entry = requireNotNull(Registry.byId(HABOUS)) { "no entry $HABOUS" }
            val tableAt = set.units.associateBy { it.method?.fixedPoint ?: it.point }
            val rowsOf = set.units.associateWith { unit -> own.filter { it.entry == HABOUS && it.unit == unit.id } }
            val places = cities.filter { it.cc in entry.countries }.map { it to "the app's ${it.name}" } +
                set.units.map { City(it.name, "MA", it.point, "Africa/Casablanca").let { c -> c to "Habous's point for ${c.name}" } }
            for ((c, where) in places) {
                val place = Place(c.at.lat, c.at.lon, c.zone, c.cc)
                val followed = set.unitFor(place) ?: continue
                val method = Registry.resolveEntry(entry, place).method ?: continue
                for (rider in method.alongside.mapNotNull { tableAt[it.point] }.distinct()) {
                    if (rider.id == followed.id) continue
                    val rows = rowsOf.getValue(rider)
                    require(rows.isNotEmpty()) { "$HABOUS/${rider.id}: no gate row of the unit's own" }
                    val note = "${rider.name}'s own table at $where, ${fmt1(distanceKm(c.at, rider.point))} km, riding beside ${followed.name}'s (ruling R198)"
                    for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
                }
            }
        }
        // JAKIM's zones beside a place (the review round of 13 Nov 2026): the engine says which zone a place takes and which
        // ride beside it; each one's unit rows are replayed at the place under the entry itself, so the place's own day
        // is what is read. A named place's list zone must be among them.
        val zoneRows = own.filter { it.source == JAKIM_SOURCE && it.entry == JAKIM && it.unit != null && it.lat == null }.groupBy { it.unit!! }
        val named = JakimZones.zones.flatMap { z -> z.places + listOfNotNull(z.point.takeIf { z in JakimZones.named }) }
        require(named.all { p -> JAKIM_PLACES.any { it.at == p } }) { "a named place of JakimZones is not among JAKIM_PLACES" }
        val jakimPlaces = cities.filter { it.cc == "MY" && !jakimListedTown(it.at) }.map { it to null } +
            JAKIM_PLACES.map { City(it.name, "MY", it.at, if (it.at.lon >= 109.5) "Asia/Kuching" else "Asia/Kuala_Lumpur") to it.zone }
        for ((c, listZone) in jakimPlaces) {
            val here = jakimAt(c.at, c.zone)
            val all = listOfNotNull(here.taken) + here.riding
            require(listZone == null || all.any { it.id == listZone }) { "${c.name}: $listZone, its zone by JAKIM's list, neither holds it nor rides beside it" }
            val what = if (listZone == null) "the app's ${c.name}" else "${c.name} ($listZone by JAKIM's list)"
            for (unit in all) {
                // The zone's own point under its own unit is my-jakim.tsv's row already.
                if (unit == here.taken && unit.point == c.at) continue
                // The zone a place takes is its own table: my-jakim-cities.tsv reads it there with its limits checked (the
                // second trust audit's G28, ruling R274), never a promise-only row here.
                if (unit == here.taken) {
                    require(own.any { it.source == JAKIM_CITIES && it.unit == unit.id && it.lat == c.at.lat && it.lon == c.at.lon }) {
                        "${c.name}: takes $JAKIM/${unit.id}, and $JAKIM_CITIES reads no table of it there (add the zone's rows at the place's point)"
                    }
                    continue
                }
                val rows = requireNotNull(zoneRows[unit.id]) { "$JAKIM/${unit.id}: no unit row in $JAKIM_SOURCE" }
                val note = "${unit.id}'s own table at $what, riding beside ${here.taken?.id ?: "the edge"}" +
                    " (JAKIM's borders are not held, the review round of 13 Nov 2026)"
                for (r in rows) out.getOrPut(r.source) { mutableListOf() } += Out(r, c, note)
            }
        }
        for (n in NAMED) {
            val rows = own.filter { it.source == n.source && n.rows(it) }
            require(rows.isNotEmpty()) { "${n.what}: no row in ${n.source}" }
            val named = n.cities.flatMap { name ->
                when {
                    name == "*" -> cities.filter { it.cc == n.cc }
                    name.startsWith("@") -> {
                        val (lat, lon) = name.drop(1).split(",").map { it.toDouble() }
                        listOf(cities.filter { it.cc == n.cc }.minBy { distanceKm(it.at, GeoPoint(lat, lon)) })
                    }
                    else -> listOf(cities.firstOrNull { it.name == name && it.cc == n.cc } ?: error("${n.what}: no app city $name, ${n.cc}"))
                }
            }
            val near = n.rides?.let { rides -> cities.filter { c -> c.cc == n.cc && rides(c) } }.orEmpty()
            require(n.rides == null || near.isNotEmpty()) { "${n.what}: the engine rides the table at no app city of ${n.cc}" }
            val places = (named + near).distinct()
            n.chosen?.let { id ->
                val entry = requireNotNull(Registry.byId(id)) { "${n.what}: no entry $id" }
                for (c in places) {
                    require(Registry.inScope(entry, Place(c.at.lat, c.at.lon, c.zone, c.cc))) { "${n.what}: $id does not apply at the app's ${c.name}" }
                }
            }
            for (c in places) for (r in rows) {
                val from = r.lat?.let { lat -> GeoPoint(lat, r.lon!!) } ?: r.unit?.let { u -> Units.of(r.entry)?.units?.firstOrNull { it.id == u }?.point }
                val km = from?.let { " ${fmt1(distanceKm(c.at, it))} km," }.orEmpty()
                out.getOrPut(r.source) { mutableListOf() } += Out(r, c, "${n.what} at the app's ${c.name},$km ${n.why}")
            }
        }

        val written = LinkedHashMap<String, Written>()
        for ((source, list) in out.toSortedMap()) {
            val lines = gate.resolve(source).readLines()
            val header = lines.first { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }
            val names = header.split('\t').map { it.trim() }
            fun col(name: String) = names.indexOf(name).also { require(it >= 0) { "$source has no $name column" } }
            val iEntry = col("entry")
            val iLat = col("lat")
            val iLon = col("lon")
            val iSplit = col("split")
            val iNote = col("note")
            // A row checked under another entry's member (CROSS) needs the member column, which a single entry's file lacks.
            val addMember = "member" !in names && list.any { it.member != null }
            val iMember = if (addMember) names.size else names.indexOf("member")
            val sb = StringBuilder()
            val group = source.removeSuffix(".tsv")
            sb.append("# GENERATED by `./gradlew -p tools/timetables generateEnvelopeRows` (units/GenerateEnvelopeRows.kt): promise-only rows\n")
            val about = ABOUT[group]
            if (about != null) {
                for (line in about) sb.append(line).append('\n')
            } else {
                sb.append("# (the city-point round 2 review, 11 Oct 2026). ").append(group).append(".tsv's tables printed for their own points, each\n")
                sb.append("# replayed at every app city of its entry's countries within its bound reach (R40's class D reach), whichever table\n")
                sb.append("# the city follows, past every bound the nearest table within its ride (five reaches, the owner's decision of\n")
                sb.append("# 17 Oct 2026), and the round's named claims: never before any of them, nor any end after one. The gate checks\n")
                sb.append("# the promise alone here; the lateness is no unit's figure and no limit's. Coordinates and the tables' own paths only,\n")
                sb.append("# never a time.\n")
            }
            for (line in ABOUT_ALSO[group].orEmpty()) sb.append("# ").append(line).append('\n')
            if (list.any { it.row.entry == HABOUS && it.row.unit != null }) {
                sb.append("# And every Habous unit's own rows wherever the engine rides its table beside another unit's (ruling R198): at\n")
                sb.append("# every app city of Morocco and Western Sahara and at Habous's other points inside its 25 km.\n")
            }
            sb.append(header).append(if (addMember) "\tmember" else "").append('\n')
            var rows = 0
            var skipped = 0
            val places = HashSet<String>()
            val sorted = list.sortedWith(compareBy<Out>({ it.entry }, { it.city.at.lat }, { it.city.at.lon }, { it.row.line }))
            val seen = HashSet<String>()
            for (o in sorted) {
                val lat = fmt6(o.city.at.lat)
                val lon = fmt6(o.city.at.lon)
                val k = key(o.row.source, o.row.path, o.entry, o.member, lat.toDouble(), lon.toDouble())
                if (k in held) {
                    skipped++
                    continue
                }
                if (!seen.add(k + "|" + o.row.line)) continue
                val cells = lines[o.row.line - 1].split('\t').toMutableList()
                if (addMember) cells += ""
                cells[iEntry] = o.entry
                cells[iLat] = lat
                cells[iLon] = lon
                cells[iSplit] = "test"
                cells[iNote] = "${o.note}: ${cells[iNote]}"
                if (iMember >= 0 && iMember < cells.size) cells[iMember] = o.member.orEmpty()
                sb.append(cells.joinToString("\t")).append('\n')
                rows++
                places += "${o.entry}@$lat,$lon"
            }
            written["$group${GateRow.PROMISE_ONLY_SUFFIX}"] = Written(sb.toString(), rows, places.size, skipped)
        }
        return written
    }

    /**
     * The tables that ride beside [at] ([TableEnvelope.riding]), each with why: every one whose bound reach (R40's class D
     * reach, a city-bound table's own radius: [TableEnvelope.boundKm]) holds it, and the nearest within its ride ([TableEnvelope.rideKm], the owner's decision of 17 Oct 2026) where
     * no bound holds it.
     */
    fun riders(envelope: TableEnvelope, at: GeoPoint): List<Pair<AuthorityUnit, String>> {
        val held = envelope.holding(at)
        return envelope.riding(at).map { unit ->
            unit to if (unit in held) {
                "within its ${fmt1(envelope.boundKm(unit))} km bound reach (round 2's envelope)"
            } else {
                "the nearest table, within its ${fmt1(envelope.rideKm(unit))} km ride past its bound (the owner's decision of 17 Oct 2026)"
            }
        }
    }

    private fun key(source: String, path: String, entry: String, member: String?, lat: Double?, lon: Double?) =
        listOf(source, path, entry, member ?: "", lat?.let(::fmt6) ?: "", lon?.let(::fmt6) ?: "").joinToString("|")

    private fun fmt6(x: Double) = String.format(Locale.ROOT, "%.6f", x)

    private fun fmt1(x: Double) = String.format(Locale.ROOT, "%.1f", x)
}
