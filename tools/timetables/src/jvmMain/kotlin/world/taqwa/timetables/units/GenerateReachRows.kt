package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.TimedEvent.FAJR
import world.taqwa.app.prayer.engine.registry.TimedEvent.SUNRISE
import world.taqwa.app.prayer.engine.registry.TimedEvent.DHUHR
import world.taqwa.app.prayer.engine.registry.TimedEvent.ASR
import world.taqwa.app.prayer.engine.registry.TimedEvent.MAGHRIB
import world.taqwa.app.prayer.engine.registry.TimedEvent.ISHA
import world.taqwa.app.prayer.engine.registry.TimedEvent.END_OF_EATING
import world.taqwa.app.prayer.engine.registry.TimedEvent.IMSAK
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.ReachLimits
import world.taqwa.app.prayer.engine.registry.data.AlgeriaWilayas
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.GateRow
import world.taqwa.timetables.gate.OfficialRoots
import world.taqwa.timetables.gate.Split
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.system.exitProcess

/**
 * Writes the reach rows of the units that claim a figure ([ReachRows.SPECS]): Umm al-Qura's (KACST's 173 places) and
 * QMDB's (its 86 measured places), `official/gate/sa-ummalqura-reach.tsv` and `kz-qmdb-reach.tsv` (the reviews of
 * 9 and 10 Oct 2026, "prove it across the unit", London's precedent); and since the city-point audit of 10–11 Oct
 * 2026 ISNA's mosques in the United States and Canada and BIK's base table (`us-isna-reach.tsv`, `ca-isna-reach.tsv`,
 * `xk-bik-reach.tsv`), since the review of 12 Nov 2026 Kemenag's kab/kota (`id-kemenag-reach.tsv`), and since 13 Nov
 * 2026 ESA's 78 Egyptian towns (`eg-esa-reach.tsv`, ruling R181) and the Omani ministry's localities
 * (`om-mara-reach.tsv`, the audit of 12 Nov 2026, finding F14), and since 14 Nov 2026 Diyanet's 84 il centres
 * (`tr-diyanet-reach.tsv`, ruling R255). A unit's figure was proven at its own point and
 * at the app's point for its city only; inside the unit a user can be up to its reach from both. So each measured
 * unit's own tables (the rows its group file reads at the unit's point) are replayed, held out (split=test) and tagged
 * with the unit (`<entry>/<unit>`, so the gate checks the point resolves there):
 *
 * - at [BEARINGS] bearings and [SHARES] of its radius, a fixed grid the monitor's recipes extend each new year;
 * - and at each event's worst point of a dense sweep ([sweep]: 392 points a unit, and a finer sweep of 1,560 more
 *   where fewer than [MIN_SWEPT] of them fall inside the unit, against the unit's own tables in the archive): the
 *   lateness that decides the unit's figure and limits sits near the edge and between bearings, and in a unit that
 *   other units hem in (Fayfa: 15 of a 72-point grid inside it), where a sparse grid misses it by a minute. Where a
 *   place waits for every table whose reach holds it ([ReachRows.Spec.waits]: QMDB's, ruling R249) the sweep also
 *   takes a point of the ground the unit shares with each neighbour ([ReachRows.neighbours]): the lateness there
 *   steps at each neighbour's edge, and bearings alone can pass a neighbour by.
 *
 * A point another unit takes (a nearer place, or a city whose reach holds it) is left out: its user follows that
 * unit's table. The unit's stamp row then holds the lateness across its area, which About reads. `CityUnitsTest`
 * checks the result on a third grid, offset from both.
 *
 *     ./gradlew -p tools/timetables generateReachRows [-Pthreads=N] [--args="--only <entry>"]
 *
 * `--only` writes that entry's reach file alone, and is the only way to write a pending entry's ([ReachRows.PENDING]:
 * such a file is not committed, since the gate does not pass on it yet). Needs the archive. Re-run after a unit, a reach,
 * a unit's table or the rule that picks a unit changes, then run the gate and commit. Coordinates are rounded to six
 * decimals, the point then resolved again; no printed time is written.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) ?: fail("$name needs a value") }
    val repo = File(option("--repo") ?: fail("--repo is required"))
    val official = File(option("--official") ?: fail("--official is required"))
    val threads = option("--threads")?.toInt() ?: Runtime.getRuntime().availableProcessors()
    val only = option("--only")?.also { e ->
        if ((ReachRows.SPECS + ReachRows.PENDING).none { it.entry == e } && ReachAreas.AREAS.none { it.entry == e }) fail("--only $e: no such reach entry")
    }
    val roots = OfficialRoots(official, repo.resolve("tools/timetables/official"))
    if (!roots.held) fail("no archive at $official: the sweep reads each unit's own tables")
    val gate = repo.resolve("tools/timetables/official/gate")
    // Ruling R297: the figures claimed across an area, read on a grid over the whole claim (ReachAreas.AREAS); claims that
    // share a file (JAKIM's zones, ruling R310) are written to it together.
    val cities = ReachAreas.Cities(repo.resolve("shared/src/commonMain/composeResources/files/cities.csv"))
    for ((file, claims) in ReachAreas.AREAS.filter { only == null || it.entry == only }.groupBy { it.file }) {
        val fileStarted = System.nanoTime()
        val parts = claims.map { area ->
            val started = System.nanoTime()
            val text = ReachAreas.render(area, gate, roots, cities, threads)
            println(
                "${if (claims.size > 1) area.tag else area.entry}: ${text.swept} points of its claim on the grid and ${text.claimed - text.swept} on the finer one, ${text.points} points kept, " +
                    "${text.rows} rows, ${text.broken} cells early or late in the sweep; worst " +
                    text.worst.entries.joinToString(", ") { "${it.key.key} ${it.value}" } +
                    ", ${"%.0f".format((System.nanoTime() - started) / 1e9)} s",
            )
            if (text.broken > 0) fail("${if (claims.size > 1) area.tag else area.entry}: the sweep found ${text.broken} cells early or late")
            text
        }
        gate.resolve(file).writeText(ReachAreas.fileText(claims, parts))
        if (claims.size > 1) {
            println(
                "$file: ${claims.size} claims, ${parts.sumOf { it.swept }} points on the grid and ${parts.sumOf { it.claimed - it.swept }} on the finer one, " +
                    "${parts.sumOf { it.points }} points kept, ${parts.sumOf { it.rows }} rows, ${parts.sumOf { it.broken }} cells early or late in the sweep, " +
                    "${"%.0f".format((System.nanoTime() - fileStarted) / 1e9)} s",
            )
        }
    }
    for (spec in if (only == null) ReachRows.SPECS else (ReachRows.SPECS + ReachRows.PENDING).filter { it.entry == only }) {
        val started = System.nanoTime()
        val text = ReachRows.render(spec, gate, roots, threads)
        repo.resolve("tools/timetables/official/gate/${spec.file}").writeText(text.file)
        println(
            "${spec.entry}: ${text.units} units, ${text.points} points kept of ${text.tried} on the grid and ${text.worst} " +
                "worst points of the sweep (${text.swept} swept, ${text.fine} units finely), ${text.rows} rows, ${text.broken} cells early or late in the " +
                "sweep, ${"%.0f".format((System.nanoTime() - started) / 1e9)} s",
        )
        if (text.broken > 0) fail("${spec.entry}: the sweep found ${text.broken} cells early or late")
        if (spec in ReachRows.PENDING) println("${spec.entry} is pending: ${spec.file} is not to be committed until the gate passes on it")
    }
}

private fun fail(problem: String): Nothing {
    System.err.println("generateReachRows: $problem")
    exitProcess(2)
}

/** The reach rows' layout and the points they are read at. */
object ReachRows {
    /**
     * One authority: its entry, its group file (the unit's own rows) and its country. [ownTagged]: the group file names
     * the unit on its own rows (`<entry>/<unit>` at the unit's point, as kg-default.tsv does), not the bare entry.
     * [leftOut]: measured units whose reach rows are not written yet, because the unit's own limit does not hold across
     * its reach and the limit is not this file's to settle (each named where the spec is; never a unit that would be
     * early: lateness alone).
     * [waits]: a place inside several of the entry's reaches waits for every such unit's table (QMDB's, ruling R249),
     * so the sweep reads the ground each neighbour shares with the unit ([neighbours]).
     * [watched]: points of a unit's reach written as rows whatever the sweep finds, by unit: where a probe finer than the
     * sweep read a unit's worst (ruling R321: the Tromsø probe's worst points on the app's own grid, so the proofs keep
     * reading them), each with why.
     */
    class Spec(
        val entry: String,
        val group: String,
        val country: String? = null,
        val ownTagged: Boolean = false,
        val waits: Boolean = false,
        val leftOut: Set<String> = emptySet(),
        val region: ((AuthorityUnit) -> String?)? = null,
        val file: String = "$group-reach.tsv",
        val dense: Boolean = true,
        val watched: Map<String, List<Watched>> = emptyMap(),
    ) {
        /** The country a point of the reach is resolved in: [country], else the entry's first, as the gate resolves a row. */
        val countryCode: String get() = country ?: Registry.byId(entry)?.countries?.firstOrNull().orEmpty()
    }

    /** A point [Spec.watched] keeps in a unit's rows, and why (written into the rows' note). */
    class Watched(val at: GeoPoint, val why: String)

    /** A point around [unit] as the app places a user there: [Spec.region]'s region, where the entry picks a unit by region. */
    fun place(spec: Spec, unit: AuthorityUnit, at: GeoPoint, zone: String): Place =
        Place(at.lat, at.lon, zone, spec.countryCode, spec.region?.invoke(unit))

    /** The units [spec]'s reach rows cover: the entry's measured units, less [Spec.leftOut]. */
    fun units(spec: Spec): List<AuthorityUnit> = requireNotNull(Units.of(spec.entry)).units.filter { it.measured && it.id !in spec.leftOut }

    /**
     * Ruling R321: the Tromsø probe's worst points on the app's own grid (a thousandth of a degree, as the app keeps a
     * location), read before and after R321's fix: the over-limit Isha point of 16 May 2026 (a sunset the sun did not
     * make), and each event's worst since.
     */
    val TROMSO_WATCHED: List<Watched> = listOf(
        Watched(GeoPoint(69.895, 18.852), "the Tromsø probe's Isha over its limit before ruling R321"),
        Watched(GeoPoint(69.682, 18.604), "the Tromsø probe's worst Isha on the app's grid after ruling R321"),
        Watched(GeoPoint(69.671, 18.783), "the Tromsø probe's worst Maghrib on the app's grid before ruling R321"),
        Watched(GeoPoint(69.671, 18.856), "the Tromsø probe's worst Maghrib on the app's grid in May after ruling R321"),
        Watched(GeoPoint(69.904, 19.088), "the Tromsø probe's worst Maghrib on the app's grid in July"),
        Watched(GeoPoint(69.389, 18.893), "the Tromsø probe's worst sunrise on the app's grid"),
        Watched(GeoPoint(69.831, 19.414), "the Tromsø probe's worst end of eating on the app's grid"),
    )

    /**
     * Umm al-Qura's and QMDB's places; and, since the city-point audit of 10–11 Oct 2026, the entries whose tables printed
     * for their own points became units (`tablesAtTheirPoints`) and claim a figure there: ISNA's mosques in the United
     * States and Canada, and BIK's base table. (Dawat-e-Islami's Delhi and the Uzbek Board's Tashkent claim none, and the
     * cautious members' tables show none.) And since 12 Nov 2026 the Kyrgyz Muftiate's 115 localities and Aravan town
     * (`kg-default-reach.tsv`, the Kyrgyz round's review: each unit's figure was proven at its own point alone), and
     * Kemenag's kab/kota (the review of 12 Nov 2026: 127 circles out to 24.9 km, where a point's stamp of a minute read 2
     * near a circle's west edge). And since 13 Nov 2026 ESA's 78 towns (ruling R181: a town's stamp of a minute read 2
     * toward its reach's west edge and 3 where another town's table rides beside it, Cairo's among them). And the Omani
     * ministry's localities (the audit of 12 Nov 2026, finding F14: 85 units of 20 km and Al Hamra made on 12 Nov with
     * no row away from their own points). Bahla was left out until 14 Nov 2026: across its reach its tables read 3 min
     * after on 230 cells of May to July 2027 and June and July 2029 (Asr 48, Isha 104, the end of eating 78), over the
     * entry's 2, never early; Gulf.kt now holds that as Bahla's own limit (ruling R254) and its rows are in. And since
     * 14 Nov 2026 Türkiye's 84 il centres (finding F14, ruling R255; pending until then): across their reaches no start
     * is early and no end late (1,760 points), but 71 units read 2 to 4 min after their own table, toward the edge of
     * the reach where the place's own sun rides and where a neighbouring district's table does (rulings R201 and R204),
     * over the limits their own-point rows and R171's hand-placed rows held (102,308 cells). Each unit's limits are now
     * the worst the gate measures on these rows too (the archive's limits.py reads the stamp, DiyanetBesideDistricts.late).
     * And since the second trust audit of 14 Nov 2026 (finding G26, ruling R270) every other entry the gate checks whose
     * measured units reach past their own points: Habous's 187 places, JAKIM's 60 zones, the Foundation's 64 districts,
     * Algeria's seats and bands, DUM RT's 19 towns, Tunisia's 6 and Jamiat's 9, and the tables printed for their own
     * points (R40's reaches: Brunei, Dubai, Qatar, Amman, the PA's towns, Libya, Cape Town's two, Moscow, Bosnia,
     * Tirana, Dublin's two, Paris, Brussels, Vienna, Zürich, Toronto's three, Sydney, Auckland). Each unit's limit, where
     * its reach read later, is the measured worst (ReachLimits.kt, `generateReachLimits`); a figure over [CEILING] waits
     * for the owner's ruling ([overCeiling]). The [region] of an Algerian seat is its wilaya's: the app hands a place a
     * seat's fitted day by its region alone (ruling R219). A Brunei district's [region] is its own name: the app gives a
     * place the district its nearest city's region names, anywhere in that district's circle (rulings R32 and R309), so
     * Brunei-Muara is read across its whole circle (the review of 17 Nov 2026: by longitude alone the sweep gave Tutong
     * the ground west of 114.85° E, Sengkurong's, which the app gives Brunei-Muara). And since 16 Nov 2026 Diyanet's 33 European cities, pending
     * from 14 Nov until their far-north days north of Luleå were put in order (ruling R293). And since 17 Nov 2026 Norway's
     * IRN (Oslo, Trondheim, Tromsø), pending from 14 Nov until the owner ruled on the nights too short for any day in
     * order at the edge of Tromsø's reach: such a night declares Isha's end not shown (ruling R302, the owner's (a′)).
     */
    val SPECS = listOf(
        Spec("sa.ummalqura", "sa-ummalqura", "SA", dense = false), Spec("kz.qmdb", "kz-qmdb", "KZ", waits = true, dense = false),
        Spec("us.isna", "us-isna", "US", dense = false), Spec("ca.isna", "ca-isna", "CA", dense = false),
        Spec("xk.bik", "xk-bik", "XK", dense = false),
        Spec("kg.default", "kg-default", "KG", ownTagged = true, dense = false),
        Spec("id.kemenag", "id-kemenag", "ID", dense = false),
        Spec("eg.esa", "eg-esa", "EG", dense = false),
        Spec("om.mara", "om-mara", "OM", dense = false),
        Spec("tr.diyanet", "tr-diyanet", "TR", dense = false),
        Spec("ma.habous", "ma-habous", "MA"),
        Spec("my.jakim", "my-jakim", "MY"),
        Spec("bd.ifb", "bd-ifb", "BD"),
        Spec("dz.marw", "dz-marw", "DZ", region = { unit -> AlgeriaWilayas.seats.first { it.code == unit.id.substringBefore('-').toInt() }.name }),
        Spec("ru.dumrt", "ru-dumrt", "RU"),
        Spec("tn.inm", "tn-inm", "TN"),
        Spec("za.jamiat", "za-jamiat", "ZA"),
        Spec("bn.mora", "bn-mora", region = { unit -> unit.name }), Spec("ae.iacad.dubai", "ae-iacad"),
        Spec("qa.calendarhouse", "qa-calendarhouse"), Spec("jo.awqaf", "jo-awqaf"), Spec("ps.iftaa", "ps-iftaa", ownTagged = true),
        Spec("ly.awqaf", "ly-awqaf"), Spec("za.mjc", "za-cape", file = "za-mjc-reach.tsv"), Spec("za.voc", "za-cape", file = "za-voc-reach.tsv"), Spec("ru.dumrf", "ru-dumrf"),
        Spec("ba.iz", "ba-iz"), Spec("al.kmsh", "al-kmsh"), Spec("ie.ifi", "ie-ifi"), Spec("ie.icci", "ie-icci"),
        Spec("fr.gmp", "fr-gmp"), Spec("be.emb", "be-emb"), Spec("at.iggo", "at-iggo"), Spec("ch.fids", "ch-fids"),
        Spec("ca.ift", "ca-toronto", file = "ca-ift-reach.tsv"), Spec("ca.iit", "ca-toronto", file = "ca-iit-reach.tsv"),
        Spec("ca.mac", "ca-toronto", file = "ca-mac-reach.tsv"),
        Spec("au.lma", "au-lma"), Spec("nz.fianz", "nz-fianz"),
        Spec("tr.diyanet.europe", "tr-diyanet-europe"),
        Spec("no.irn", "no-irn", watched = mapOf("no.irn.tromso" to TROMSO_WATCHED)),
    )

    /**
     * Entries whose reach rows the gate does not pass yet, written by `--only` alone and not committed, and a release
     * waits ([outstanding]). Türkiye's 84 il centres stood here from 13 Nov until ruling R255, Diyanet's European cities
     * from 14 Nov (G26) until ruling R293 of 16 Nov 2026, and Norway's IRN from 14 Nov until ruling R302 of 17 Nov 2026:
     * its Tromsø unit's reach meets nights the sun is down for minutes, the week before and after the midnight sun.
     * Ruling R292 puts in order every such night that can hold Maghrib and Isha before the next dawn; a night shorter
     * than any day in order needs (the minute after the real sunset and the minute after it are not before the next
     * dawn the real sun allows) declares Isha's end not shown, which the gate recomputes from the real sun and counts
     * declared, and its Maghrib is the minute after the sun has set wherever that is before the next Fajr (R302). Empty
     * now.
     */
    val PENDING: List<Spec> = emptyList()

    /**
     * The reach proofs still owed, one line each: every unit a spec leaves out ([Spec.leftOut]), every pending entry
     * ([PENDING]), every entry the gate checks whose measured units reach past their own points and that has no spec at
     * all ([unspecified], the second trust audit's G26: "no reach proof is owed" had read only this file's own list, and
     * seven authorities' units claimed a figure no reach row had measured), every claim across an area no reach row reads
     * ([unswept]), every figure over [CEILING] read beyond a drawn area and not ruled on ([beyondArea]), and every reach
     * limit over [CEILING] the owner has not ruled on ([overCeiling], ruling R270). At each of them About's figure is either the unit's own point's, and
     * a place across its reach was measured later (never early), or a measured figure past what a ruling has accepted.
     * `checkStamps -Prelease=true`, which the release scripts run, refuses a build while this is not empty
     * ([world.taqwa.timetables.gate.CheckStamps.reachHold]): the limit is settled or the reach cut, and the rows join the
     * gate, before a release says the figure.
     */
    fun outstanding(
        specs: List<Spec> = SPECS,
        pending: List<Spec> = PENDING,
        unspecified: List<String> = unspecified(specs + pending),
        overCeiling: List<String> = overCeiling(),
        unswept: List<String> = unswept(),
        beyondArea: List<String> = beyondArea(),
    ): List<String> =
        specs.filter { it.leftOut.isNotEmpty() }.map { spec ->
            "${spec.entry}: ${spec.file} leaves out ${spec.leftOut.sorted().joinToString(", ")}"
        } + pending.map { spec -> "${spec.entry}: ${spec.file} is pending, none of its units' reaches is in the gate" } +
            unspecified.map { "$it: its measured units reach past their own points and no reach spec measures them (ReachRows.SPECS)" } +
            unswept.map { "$it: it claims a figure across an area (a region's shape, its countries, a unit chosen beyond its circle) that no reach row reads (ReachAreas.AREAS, R297, R300)" } +
            beyondArea + overCeiling

    /**
     * Where the app says an entry's figure beyond the area [ReachAreas.AREAS] draws for it and a reading there (not part
     * of the proof) measured figures over [CEILING] that no ruling accepts: entry to what was read and where. Each refuses a
     * release, as a figure over the ceiling inside the drawn area does ([overCeiling]), until the owner rules (accepting
     * the figures, or reading the area the app actually hands the figure to) and the line goes.
     *
     * Empty since 17 Nov 2026: Bahrain stood here from the review of 16 Nov 2026 (its drawn ground left out the sea the
     * app's nearest-city rule hands it, where a diagnostic read Fajr 5, sunrise 6 and the end of eating 7) until the
     * owner ruled that Bahrain's and Singapore's figures are read across the app's whole nearest-city cell (ruling R307,
     * [ReachAreas.Cell]). The cell is every point a fix on the country's own zone is placed in the country alone; a
     * point beyond it, whose nearest city is a neighbour's, is shown the figure only where the user chooses the
     * timetable at a border place (ruling R259), and since ruling R318 (18 Nov 2026) the choice claims no figure there
     * (`Registry.resolveChosen`: "Not measured"). So nothing the app says these figures at is beyond what is read.
     */
    val BEYOND_AREA: Map<String, String> = emptyMap()

    /** [beyond]'s lines for the entries of [gated] (the entries with gate rows; all when null), as [outstanding] says them. */
    fun beyondArea(gated: Set<String>? = null, beyond: Map<String, String> = BEYOND_AREA): List<String> =
        beyond.filterKeys { gated == null || it in gated }.toSortedMap().map { (entry, what) -> "$entry: $what" }

    /**
     * Ruling R297: every entry of [gated] (the entries with gate rows; all of the registry's when null) that claims a figure
     * across an area rather than a unit's circle ([ReachAreas.areaClaims]) and that [areas] does not read: there About's
     * figure was measured at the points the gate file names, on part of the claim alone.
     */
    fun unswept(
        gated: Set<String>? = null,
        areas: List<ReachAreas.AreaClaim> = ReachAreas.AREAS,
        claims: List<String> = ReachAreas.areaClaims(),
    ): List<String> {
        val read = areas.map { it.entry }.toSet()
        return claims.filter { (gated == null || it in gated) && it !in read }.sorted()
    }

    /**
     * Every registry entry of [gated] (the entries with gate rows; all of the registry's when null) whose units include a
     * measured one with a reach (radius over 0) and that [specs] do not name: each claims "at most N min" across ground
     * no reach row has read.
     */
    fun unspecified(specs: List<Spec>, gated: Set<String>? = null): List<String> {
        val named = specs.map { it.entry }.toSet()
        return Registry.entries.map { it.id }.filter { id ->
            id !in named && (gated == null || id in gated) &&
                Units.of(id)?.units?.any { it.measured && it.radiusKm > 0.0 } == true
        }.sorted()
    }

    /**
     * Ruling R270: a reach limit is the measured worst, and a figure over [CEILING] needs the owner's ruling (R255: "a
     * fifth minute needs a ruling"). One line per entry naming its units' figures over it, less what [RULED] accepts.
     */
    fun overCeiling(
        minutes: Map<String, Map<String, Map<TimedEvent, Int>>> = ReachLimits.MINUTES,
        ruled: Map<String, Map<TimedEvent, Int>> = RULED,
    ): List<String> =
        minutes.toSortedMap().mapNotNull { (entry, units) ->
            val over = units.toSortedMap().flatMap { (unit, _) ->
                unruled(entry, unit, minutes, ruled).map { (event, m) -> "$unit ${event.name.lowercase()} $m" }
            }
            over.takeIf { it.isNotEmpty() }?.let {
                "$entry: ${it.size} reach limit${if (it.size == 1) "" else "s"} over $CEILING min await the owner's ruling (R270): " +
                    it.take(OVER_NAMED).joinToString(", ") + if (it.size > OVER_NAMED) " and ${it.size - OVER_NAMED} more" else ""
            }
        }

    /**
     * [unit]'s reach limits over [CEILING] that [ruled] does not accept, by event. The site holds a city whose unit has
     * any (`Proven.verdict`), as a release is held ([overCeiling]): a page says no figure the owner has not accepted.
     */
    fun unruled(
        entry: String,
        unit: String,
        minutes: Map<String, Map<String, Map<TimedEvent, Int>>> = ReachLimits.MINUTES,
        ruled: Map<String, Map<TimedEvent, Int>> = RULED,
    ): Map<TimedEvent, Int> =
        minutes[entry]?.get(unit).orEmpty().filter { (e, m) -> m > CEILING && m > (ruled["$entry/$unit"]?.get(e) ?: 0) }.toSortedMap()

    /** The most minutes a reach limit may be without a ruling (R255's ceiling, Ankara's 4). */
    const val CEILING = 4

    /** How many figures over [CEILING] a line of [overCeiling] names before it counts the rest. */
    private const val OVER_NAMED = 12

    /**
     * The owner's rulings on reach limits over [CEILING], by `entry/unit` and event: the figure he has accepted for that
     * event there. On 16 Nov 2026 (ruling R291, verbatim "Accept all 246") the owner accepted, as measured, all 246
     * figures over 4 min that [overCeiling] named on that day's committed stamps (`ReachLimits.MINUTES`): 97 units of 20
     * entries, the 233 of 14 Nov (ruling R270) and the 13 new over 4 of 15 Nov. Each value is that event's measured
     * figure, derived from `ReachLimits.kt` by a script and not by hand. The ruling is per event, as he accepted it: a later
     * raise of a ruled event past its value, any other event rising over [CEILING] (in a ruled unit or not), or a new unit
     * over it, again refuses a release and holds the unit's cities until he rules.
     *
     * On 17 Nov 2026 (verbatim "Yes I accept all 9 with your recommendations") he accepted, as measured and per event:
     * Diyanet Europe's 174 figures over 4 min that `checkStamps -Prelease=true` named on d1eaa4689, and IRN's 19 once its
     * reach is in the gate (ruling R304); London Unified's end of eating 5 across the M25 (R305); the Supreme Council's
     * Fajr 5, sunrise 6 and end of eating 7 across Bahrain's whole nearest-city cell (R307); and Umm al-Qura's Tayma table
     * read across the town, Fajr 6, Maghrib 5 and Isha 5 (R308). Each value is the figure he accepted, as the BUILD-LOG
     * and the refusal named it. [unruled] accepts a measured figure up to the ruled one and refuses one above it: a
     * regenerated measurement lower than the accepted figure stays accepted, a higher one refuses a release again.
     *
     * On the evening of 17 Nov 2026 (verbatim "Okay cool, yes I accept both recommendations") he accepted, as measured and
     * per event, the 32 figures over 4 min that JAKIM's zones read across all the ground the app hands them measured
     * (rulings R310 and R313; the BUILD-LOG's "JAKIM's zones read across their ground", For the owner): each value is the
     * figure `ReachLimits.kt` gives after the merge of that lane and its regenerated rows, checked by a script against
     * the lane's table (all 32 equal), merged into the zone's earlier line where it had one.
     */
    val RULED: Map<String, Map<TimedEvent, Int>> = mapOf(
        "at.iggo/at.iggo" to mapOf(FAJR to 8, ISHA to 8, END_OF_EATING to 9),
        "au.lma/au.lma" to mapOf(FAJR to 5, DHUHR to 6, ASR to 18, END_OF_EATING to 5),
        "be.emb/be.emb" to mapOf(FAJR to 13, SUNRISE to 5, ASR to 5, MAGHRIB to 5, ISHA to 16, END_OF_EATING to 16),
        "bh.council/bh.council" to mapOf(FAJR to 5, SUNRISE to 6, END_OF_EATING to 7),
        "ca.ift/ca.ift" to mapOf(FAJR to 6, SUNRISE to 6, ASR to 5, MAGHRIB to 6, ISHA to 7, END_OF_EATING to 8),
        "ca.iit/ca.iit" to mapOf(FAJR to 7, SUNRISE to 7, ASR to 6, MAGHRIB to 7, ISHA to 7, END_OF_EATING to 7),
        "ca.mac/ca.mac" to mapOf(FAJR to 6, SUNRISE to 5, ASR to 6, MAGHRIB to 5, ISHA to 6, END_OF_EATING to 6),
        "ch.fids/ch.fids" to mapOf(FAJR to 8, SUNRISE to 5, ISHA to 8, END_OF_EATING to 9),
        "dz.marw/11" to mapOf(FAJR to 7, SUNRISE to 7, ASR to 9, MAGHRIB to 7, ISHA to 7, END_OF_EATING to 7),
        "dz.marw/31-seat" to mapOf(FAJR to 8, SUNRISE to 6, ASR to 6, MAGHRIB to 6, ISHA to 7, END_OF_EATING to 7),
        "fr.gmp/fr.gmp" to mapOf(FAJR to 7, SUNRISE to 5, ASR to 6, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 19),
        "gb.london.lupt/gb.london.lupt" to mapOf(END_OF_EATING to 5),
        "ie.icci/ie.icci" to mapOf(FAJR to 13, SUNRISE to 5, ASR to 7, MAGHRIB to 5, ISHA to 12, END_OF_EATING to 11),
        "ie.ifi/ie.ifi" to mapOf(FAJR to 10, SUNRISE to 6, ASR to 6, MAGHRIB to 6, ISHA to 9, END_OF_EATING to 11),
        "ly.awqaf/tripoli" to mapOf(ISHA to 5),
        "ma.habous/agoudal" to mapOf(SUNRISE to 6),
        "ma.habous/al-fqih-ben-calah" to mapOf(SUNRISE to 5),
        "ma.habous/arbaoua" to mapOf(SUNRISE to 5),
        "ma.habous/azilal" to mapOf(SUNRISE to 5),
        "ma.habous/berrechid" to mapOf(SUNRISE to 5),
        "ma.habous/chichaoua" to mapOf(MAGHRIB to 5),
        "ma.habous/driouch" to mapOf(MAGHRIB to 5),
        "ma.habous/el-aioun" to mapOf(SUNRISE to 5),
        "ma.habous/el-jebha" to mapOf(SUNRISE to 6, MAGHRIB to 6),
        "ma.habous/el-kelaa-des-srarhna" to mapOf(SUNRISE to 6),
        "ma.habous/essaouira" to mapOf(SUNRISE to 6),
        "ma.habous/guercif" to mapOf(SUNRISE to 5),
        "ma.habous/ifrane" to mapOf(SUNRISE to 5),
        "ma.habous/imi-n-tlat" to mapOf(SUNRISE to 6),
        "ma.habous/karia-ba-mohamed" to mapOf(MAGHRIB to 5),
        "ma.habous/khemisset" to mapOf(SUNRISE to 5),
        "ma.habous/ksar-sghir" to mapOf(SUNRISE to 5),
        "ma.habous/matmata" to mapOf(SUNRISE to 7),
        "ma.habous/oued-laou" to mapOf(SUNRISE to 5, MAGHRIB to 6),
        "ma.habous/ouezzane" to mapOf(ASR to 5),
        "ma.habous/oulad-ayad-tahla" to mapOf(SUNRISE to 6, MAGHRIB to 5),
        "ma.habous/rabat" to mapOf(SUNRISE to 5),
        "ma.habous/ras-el-ma" to mapOf(SUNRISE to 6, MAGHRIB to 5),
        "ma.habous/rommani" to mapOf(SUNRISE to 5),
        "ma.habous/saidia" to mapOf(SUNRISE to 5),
        "ma.habous/sidi-slimane" to mapOf(MAGHRIB to 5),
        "ma.habous/souk-el-arbaa" to mapOf(SUNRISE to 6),
        "ma.habous/tassoultante" to mapOf(SUNRISE to 7, MAGHRIB to 5),
        "ma.habous/tata" to mapOf(MAGHRIB to 5),
        "ma.habous/tifentane" to mapOf(MAGHRIB to 6),
        "my.jakim/JHR02" to mapOf(FAJR to 6, DHUHR to 5, ASR to 6, MAGHRIB to 6, ISHA to 6, END_OF_EATING to 5),
        "my.jakim/JHR03" to mapOf(FAJR to 5, ASR to 5),
        "my.jakim/JHR04" to mapOf(END_OF_EATING to 5),
        "my.jakim/KDH02" to mapOf(SUNRISE to 5, MAGHRIB to 6, ISHA to 6, END_OF_EATING to 7, IMSAK to 7),
        "my.jakim/KDH04" to mapOf(FAJR to 5, MAGHRIB to 5, ISHA to 5, END_OF_EATING to 5),
        "my.jakim/KDH05" to mapOf(MAGHRIB to 5, ISHA to 5),
        "my.jakim/KTN01" to mapOf(FAJR to 5, ISHA to 5, END_OF_EATING to 5),
        "my.jakim/KTN02" to mapOf(SUNRISE to 5, END_OF_EATING to 7, IMSAK to 5),
        "my.jakim/NGS03" to mapOf(END_OF_EATING to 5),
        "my.jakim/PHG02" to mapOf(ASR to 5),
        "my.jakim/PHG03" to mapOf(SUNRISE to 5, END_OF_EATING to 7),
        "my.jakim/PHG04" to mapOf(ASR to 5),
        "my.jakim/PHG05" to mapOf(ASR to 5, MAGHRIB to 8),
        "my.jakim/PHG06" to mapOf(FAJR to 5, ASR to 5, ISHA to 5, END_OF_EATING to 5),
        "my.jakim/PHG07" to mapOf(SUNRISE to 5, END_OF_EATING to 6, IMSAK to 5),
        "my.jakim/PLS01" to mapOf(FAJR to 5, SUNRISE to 6),
        "my.jakim/PNG01" to mapOf(SUNRISE to 5, END_OF_EATING to 5, IMSAK to 5),
        "my.jakim/PRK01" to mapOf(SUNRISE to 7, MAGHRIB to 7, END_OF_EATING to 6),
        "my.jakim/PRK02" to mapOf(SUNRISE to 7, MAGHRIB to 6, END_OF_EATING to 6),
        "my.jakim/PRK03" to mapOf(ASR to 5),
        "my.jakim/PRK05" to mapOf(END_OF_EATING to 6),
        "my.jakim/PRK06" to mapOf(SUNRISE to 6, ASR to 5, MAGHRIB to 5, END_OF_EATING to 5),
        "my.jakim/SBH01" to mapOf(FAJR to 5, DHUHR to 5, ASR to 5, MAGHRIB to 5, ISHA to 5, END_OF_EATING to 5),
        "my.jakim/SBH07" to mapOf(FAJR to 13, DHUHR to 9, ASR to 21, MAGHRIB to 5),
        "my.jakim/SGR01" to mapOf(END_OF_EATING to 6),
        "my.jakim/SGR02" to mapOf(END_OF_EATING to 6, IMSAK to 5),
        "my.jakim/SGR03" to mapOf(END_OF_EATING to 5),
        "my.jakim/SWK01" to mapOf(FAJR to 9, DHUHR to 5, ASR to 18, END_OF_EATING to 5, IMSAK to 5),
        "my.jakim/SWK02" to mapOf(END_OF_EATING to 5),
        "my.jakim/SWK03" to mapOf(END_OF_EATING to 6),
        "my.jakim/SWK04" to mapOf(SUNRISE to 8),
        "my.jakim/SWK08" to mapOf(SUNRISE to 5, END_OF_EATING to 6),
        "my.jakim/TRG02" to mapOf(END_OF_EATING to 5),
        "my.jakim/TRG03" to mapOf(END_OF_EATING to 5),
        "my.jakim/TRG04" to mapOf(END_OF_EATING to 5),
        "my.jakim/WLY01" to mapOf(FAJR to 6, ASR to 5, ISHA to 5, END_OF_EATING to 5),
        "my.jakim/WLY02" to mapOf(FAJR to 10, DHUHR to 6, ASR to 18),
        "no.irn/no.irn.oslo" to mapOf(FAJR to 11, SUNRISE to 6, DHUHR to 8, ASR to 6, MAGHRIB to 6, ISHA to 9, END_OF_EATING to 17),
        "no.irn/no.irn.tromso" to mapOf(SUNRISE to 57, DHUHR to 9, MAGHRIB to 325, ISHA to 250, END_OF_EATING to 249),
        "no.irn/no.irn.trondheim" to mapOf(FAJR to 16, SUNRISE to 10, DHUHR to 8, ASR to 6, MAGHRIB to 9, ISHA to 16, END_OF_EATING to 29),
        "nz.fianz/auckland" to mapOf(FAJR to 5, SUNRISE to 5, ISHA to 6, END_OF_EATING to 5),
        "ps.iftaa/ps.iftaa.acre" to mapOf(ASR to 7),
        "ps.iftaa/ps.iftaa.beersheba" to mapOf(ASR to 5),
        "ps.iftaa/ps.iftaa.haifa" to mapOf(DHUHR to 5, ASR to 7),
        "ps.iftaa/ps.iftaa.jaffa" to mapOf(ASR to 5),
        "ps.iftaa/ps.iftaa.rafah" to mapOf(ASR to 6),
        "ps.iftaa/ps.iftaa.safed" to mapOf(SUNRISE to 5, ASR to 7),
        "ps.iftaa/ps.iftaa.tulkarm" to mapOf(ASR to 5),
        "ps.iftaa/ps.iftaa.umm-al-fahm" to mapOf(ASR to 5),
        "ru.dumrf/moscow" to mapOf(FAJR to 11, SUNRISE to 6, ASR to 5, MAGHRIB to 5, ISHA to 13, END_OF_EATING to 13),
        "ru.dumrt/agryz" to mapOf(FAJR to 135, ISHA to 65, END_OF_EATING to 133),
        "ru.dumrt/almetyevsk" to mapOf(FAJR to 133, ISHA to 63, END_OF_EATING to 127),
        "ru.dumrt/arsk" to mapOf(FAJR to 139, ISHA to 64, END_OF_EATING to 132),
        "ru.dumrt/aznakayevo" to mapOf(FAJR to 128, ISHA to 62, END_OF_EATING to 129),
        "ru.dumrt/bavly" to mapOf(FAJR to 135, ISHA to 62, END_OF_EATING to 127),
        "ru.dumrt/bugulma" to mapOf(FAJR to 120, ISHA to 62, END_OF_EATING to 128),
        "ru.dumrt/buinsk" to mapOf(FAJR to 134, ISHA to 63, END_OF_EATING to 131),
        "ru.dumrt/chistopol" to mapOf(FAJR to 127, ISHA to 63, END_OF_EATING to 130),
        "ru.dumrt/kazan" to mapOf(FAJR to 135, ISHA to 64, END_OF_EATING to 134),
        "ru.dumrt/kukmor" to mapOf(FAJR to 129, ISHA to 64, END_OF_EATING to 133),
        "ru.dumrt/leninogorsk" to mapOf(FAJR to 127, ISHA to 62, END_OF_EATING to 128),
        "ru.dumrt/mendeleyevsk" to mapOf(FAJR to 127, ISHA to 63, END_OF_EATING to 132),
        "ru.dumrt/menzelinsk" to mapOf(FAJR to 137, ISHA to 64, END_OF_EATING to 130),
        "ru.dumrt/naberezhnye-chelny" to mapOf(FAJR to 138, ISHA to 64, END_OF_EATING to 132),
        "ru.dumrt/nizhnekamsk" to mapOf(FAJR to 128, ISHA to 64, END_OF_EATING to 132),
        "ru.dumrt/nurlat" to mapOf(FAJR to 130, ISHA to 62, END_OF_EATING to 129),
        "ru.dumrt/yelabuga" to mapOf(FAJR to 132, END_OF_EATING to 16),
        "ru.dumrt/zainsk" to mapOf(FAJR to 122, ISHA to 63, END_OF_EATING to 132),
        "ru.dumrt/zelenodolsk" to mapOf(FAJR to 123, ISHA to 66, END_OF_EATING to 134),
        "sa.ummalqura/tayma" to mapOf(FAJR to 6, MAGHRIB to 5, ISHA to 5),
        "tr.diyanet.europe/tr.diyanet.europe.aalborg" to mapOf(FAJR to 9, SUNRISE to 5, ASR to 5, MAGHRIB to 5, ISHA to 9, END_OF_EATING to 10),
        "tr.diyanet.europe/tr.diyanet.europe.aarhus" to mapOf(FAJR to 9, SUNRISE to 6, MAGHRIB to 5, ISHA to 9, END_OF_EATING to 9),
        "tr.diyanet.europe/tr.diyanet.europe.amsterdam" to mapOf(FAJR to 9, SUNRISE to 5, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 9),
        "tr.diyanet.europe/tr.diyanet.europe.antwerpen" to mapOf(FAJR to 6, SUNRISE to 5, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 9),
        "tr.diyanet.europe/tr.diyanet.europe.bergen" to mapOf(FAJR to 11, SUNRISE to 19, ASR to 5, MAGHRIB to 9, ISHA to 11, END_OF_EATING to 12),
        "tr.diyanet.europe/tr.diyanet.europe.berlin" to mapOf(FAJR to 8, SUNRISE to 5, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 9),
        "tr.diyanet.europe/tr.diyanet.europe.brussels" to mapOf(FAJR to 8, MAGHRIB to 5, ISHA to 6, END_OF_EATING to 8),
        "tr.diyanet.europe/tr.diyanet.europe.copenhagen" to mapOf(FAJR to 9, SUNRISE to 5, ASR to 5, MAGHRIB to 5, ISHA to 9, END_OF_EATING to 10),
        "tr.diyanet.europe/tr.diyanet.europe.freiburg" to mapOf(FAJR to 8, SUNRISE to 5, ISHA to 8, END_OF_EATING to 8),
        "tr.diyanet.europe/tr.diyanet.europe.gent" to mapOf(FAJR to 7, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 8),
        "tr.diyanet.europe/tr.diyanet.europe.goteborg" to mapOf(FAJR to 10, SUNRISE to 5, ASR to 5, MAGHRIB to 5, ISHA to 10, END_OF_EATING to 11),
        "tr.diyanet.europe/tr.diyanet.europe.helsinki" to mapOf(FAJR to 11, SUNRISE to 16, ASR to 5, MAGHRIB to 6, ISHA to 11, END_OF_EATING to 12),
        "tr.diyanet.europe/tr.diyanet.europe.kristiansand" to mapOf(FAJR to 10, SUNRISE to 6, ASR to 5, MAGHRIB to 6, ISHA to 10, END_OF_EATING to 11),
        "tr.diyanet.europe/tr.diyanet.europe.lille" to mapOf(FAJR to 8, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 7),
        "tr.diyanet.europe/tr.diyanet.europe.london" to mapOf(FAJR to 8, SUNRISE to 5, ASR to 5, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 9),
        "tr.diyanet.europe/tr.diyanet.europe.lulea" to mapOf(FAJR to 14, SUNRISE to 158, ASR to 5, MAGHRIB to 155, ISHA to 17, END_OF_EATING to 98),
        "tr.diyanet.europe/tr.diyanet.europe.lyon" to mapOf(FAJR to 9, SUNRISE to 5, ISHA to 8, END_OF_EATING to 8),
        "tr.diyanet.europe/tr.diyanet.europe.malmo" to mapOf(FAJR to 9, SUNRISE to 5, MAGHRIB to 5, ISHA to 8, END_OF_EATING to 10),
        "tr.diyanet.europe/tr.diyanet.europe.munich" to mapOf(FAJR to 8, SUNRISE to 5, ISHA to 8, END_OF_EATING to 8),
        "tr.diyanet.europe/tr.diyanet.europe.oslo" to mapOf(FAJR to 11, SUNRISE to 13, ASR to 5, MAGHRIB to 6, ISHA to 11, END_OF_EATING to 12),
        "tr.diyanet.europe/tr.diyanet.europe.oulu" to mapOf(FAJR to 14, SUNRISE to 112, ASR to 5, MAGHRIB to 109, ISHA to 17, END_OF_EATING to 50),
        "tr.diyanet.europe/tr.diyanet.europe.paris" to mapOf(FAJR to 8, SUNRISE to 5, ISHA to 8, END_OF_EATING to 8),
        "tr.diyanet.europe/tr.diyanet.europe.sarajevo" to mapOf(FAJR to 7, ASR to 5, MAGHRIB to 5, ISHA to 7, END_OF_EATING to 7),
        "tr.diyanet.europe/tr.diyanet.europe.stavanger" to mapOf(FAJR to 10, SUNRISE to 6, ASR to 5, MAGHRIB to 6, ISHA to 10, END_OF_EATING to 12),
        "tr.diyanet.europe/tr.diyanet.europe.stockholm" to mapOf(FAJR to 12, SUNRISE to 7, ASR to 5, MAGHRIB to 5, ISHA to 9, END_OF_EATING to 12),
        "tr.diyanet.europe/tr.diyanet.europe.sundsvall" to mapOf(FAJR to 13, SUNRISE to 48, ASR to 5, MAGHRIB to 38, ISHA to 15, END_OF_EATING to 15),
        "tr.diyanet.europe/tr.diyanet.europe.tampere" to mapOf(FAJR to 12, SUNRISE to 34, ASR to 5, MAGHRIB to 23, ISHA to 13, END_OF_EATING to 13),
        "tr.diyanet.europe/tr.diyanet.europe.trondheim" to mapOf(FAJR to 13, SUNRISE to 61, ASR to 5, MAGHRIB to 58, ISHA to 15, END_OF_EATING to 15),
        "tr.diyanet.europe/tr.diyanet.europe.turku" to mapOf(FAJR to 11, SUNRISE to 20, ASR to 5, MAGHRIB to 9, ISHA to 11, END_OF_EATING to 12),
        "tr.diyanet.europe/tr.diyanet.europe.umea" to mapOf(FAJR to 13, SUNRISE to 71, ASR to 5, MAGHRIB to 68, ISHA to 15, END_OF_EATING to 15),
        "tr.diyanet.europe/tr.diyanet.europe.uppsala" to mapOf(FAJR to 11, SUNRISE to 12, ASR to 5, MAGHRIB to 6, ISHA to 11, END_OF_EATING to 12),
        "tr.diyanet.europe/tr.diyanet.europe.wien" to mapOf(FAJR to 8, SUNRISE to 5, ISHA to 8, END_OF_EATING to 8),
        "tr.diyanet.europe/tr.diyanet.europe.zurich" to mapOf(FAJR to 8, SUNRISE to 5, ISHA to 7, END_OF_EATING to 9),
        "za.mjc/za.mjc" to mapOf(FAJR to 5, ASR to 5, ISHA to 5, END_OF_EATING to 5),
        "za.voc/za.voc" to mapOf(FAJR to 5, SUNRISE to 5, ASR to 5, MAGHRIB to 6, ISHA to 6, END_OF_EATING to 6),
    )

    /** The fixed grid. */
    val BEARINGS: List<Double> = (0 until 8).map { it * 45.0 }
    val SHARES: List<Double> = listOf(0.5, 0.95)

    /**
     * The sweep's grids, as (bearings, shares): 40 bearings from 4.5° by 9° at eight shares out to the edge itself
     * (a reach holds its edge; the review found Isha's worst at 99.9 %, 10 Oct 2026),
     * and the review's fourth grid, 24 bearings from 7.5° by 15° at 40, 85 and 99.5 % (10 Oct 2026). `CityUnitsTest`
     * checks the rows on that grid and on another, offset from all of these.
     */
    val SWEEP: List<Pair<List<Double>, List<Double>>> = listOf(
        (0 until 40).map { 4.5 + 9.0 * it } to listOf(0.35, 0.6, 0.8, 0.93, 0.97, 0.995, 0.999, 1.0),
        (0 until 24).map { 7.5 + 15.0 * it } to listOf(0.4, 0.85, 0.995),
    )

    /** The finer sweep where fewer than [MIN_SWEPT] points of [SWEEP] fall inside the unit: 120 bearings from 1.5° by 3°. */
    val FINE: Pair<List<Double>, List<Double>> =
        (0 until 120).map { 1.5 + 3.0 * it } to listOf(0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.92, 0.97, 0.995, 0.999, 1.0)

    /**
     * Where a spec is [Spec.dense] (every spec since ruling R270), the finer sweep everywhere and this one besides: 72
     * bearings from 2.5° by 5° at eleven shares. The entries proven since the second trust audit hold tables a unit's
     * neighbours ride beside (Habous, JAKIM) or reaches far wider than their tables' own geography (R40's), where the
     * worst stands between the coarse sweep's bearings: `CityUnitsTest`'s offset grids read a minute past it at
     * Chefchaouen and Ouezzane (14 Nov 2026).
     */
    val DENSE: Pair<List<Double>, List<Double>> =
        (0 until 72).map { 2.5 + 5.0 * it } to listOf(0.2, 0.3, 0.5, 0.7, 0.8, 0.9, 0.95, 0.98, 0.99, 0.999, 1.0)

    /** Half of [SWEEP]'s 392 points. */
    const val MIN_SWEPT = 196

    /**
     * The points of the sweep around [unit] inside it, the finer sweep's too where [SWEEP]'s are few, and the ground
     * it shares with each neighbour where the entry's places wait for one another ([neighbours]).
     */
    fun sweep(spec: Spec, unit: AuthorityUnit, zone: String): Pair<List<Point>, Boolean> {
        val coarse = SWEEP.flatMap { (bearings, shares) -> points(spec, unit, zone, shares, bearings) }
        val fine = coarse.size < MIN_SWEPT || spec.dense
        val all = (if (fine) coarse + points(spec, unit, zone, FINE.second, FINE.first) else coarse) +
            (if (spec.dense) points(spec, unit, zone, DENSE.second, DENSE.first) else emptyList()) +
            (if (spec.waits) neighbours(spec, unit, zone) else emptyList())
        return all.distinctBy { it.at } to fine
    }

    /**
     * Where a place waits for every table whose reach holds it (ruling R249), the time shown at a point that follows
     * [unit] is the latest of the unit's day, the point's own and each neighbour's whose reach holds the point and
     * whose day rides there (the earliest, for an end). A neighbour's day is read at its own point wherever it rides,
     * so across the unit the lateness against its own table is decided neighbour by neighbour and steps at each
     * neighbour's edge, and a grid of bearings can pass a neighbour by: `CityUnitsTest`'s offset grid read Saryagash's
     * Dhuhr and Maghrib a minute past the sweep's worst at the very edge, bearing 307.5, where the reach of a place
     * 68 km to the west-north-west holds a sliver under 200 m deep of the ground that follows Saryagash (14 Nov 2026).
     *
     * So for every neighbour whose reach shares ground with the unit's, nearest ground first, one point of
     * [GROUND] in the neighbour's reach that follows the unit and where the neighbour's day rides, unless it rides
     * at a point already taken: with these the sweep has read every neighbour that rides anywhere [GROUND] finds the
     * unit followed, so each event's worst across the unit is among its points. A neighbour with no such point
     * (another unit takes the ground they share, or its day is bounded there on every side, ruling R251) has none.
     */
    fun neighbours(spec: Spec, unit: AuthorityUnit, zone: String): List<Point> {
        val entry = requireNotNull(Registry.byId(spec.entry))
        fun nearestShared(other: AuthorityUnit) = max(0.0, distanceKm(unit.point, other.point) - other.radiusKm)
        val others = requireNotNull(Units.of(spec.entry)).units
            .filter { it.id != unit.id && distanceKm(unit.point, it.point) < unit.radiusKm + it.radiusKm }
            .sortedBy(::nearestShared)
        if (others.isEmpty()) return emptyList()
        val ground = GROUND.flatMap { (bearings, shares) ->
            shares.flatMap { share ->
                bearings.map { b ->
                    val at = QmdbReach.moved(unit.point, unit.radiusKm * share, b)
                    Point(GeoPoint(round6(at.lat), round6(at.lon)), share, b)
                }
            }
        }.sortedBy { it.share }
        // Each point of the ground is resolved once at most: the points of the days riding there, or null where another unit is followed.
        val riding = HashMap<GeoPoint, Set<GeoPoint>?>()
        fun ridingAt(p: Point): Set<GeoPoint>? = riding.getOrPut(p.at) {
            val r = Registry.resolveEntry(entry, place(spec, unit, p.at, zone))
            if (r.unitId == unit.id) r.method?.alongside.orEmpty().mapTo(HashSet()) { it.point } else null
        }
        val out = ArrayList<Point>()
        val read = HashSet<GeoPoint>()
        for (other in others) {
            if (other.point in read) continue
            val at = ground.firstOrNull { distanceKm(it.at, other.point) <= other.radiusKm && ridingAt(it)?.contains(other.point) == true } ?: continue
            out += at
            read += ridingAt(at).orEmpty()
        }
        return out
    }

    /**
     * The ground [neighbours] searches, as (bearings, shares): every degree of bearing at 23 shares of the radius,
     * and every half degree (300 m of a 35 km reach's edge) at the edge itself and 99.9, 99.5 and 99 %.
     */
    val GROUND: List<Pair<List<Double>, List<Double>>> = listOf(
        (0 until 720).map { it * 0.5 } to listOf(1.0, 0.999, 0.995, 0.99),
        (0 until 360).map { it * 1.0 } to listOf(
            0.98, 0.965, 0.95, 0.93, 0.9, 0.86, 0.82, 0.78, 0.74, 0.7, 0.65, 0.6, 0.55, 0.5, 0.45, 0.4, 0.35, 0.3, 0.25, 0.2, 0.15, 0.1, 0.05,
        ),
    )

    /**
     * A unit's own rows: where the group file names the unit and no point (Kemenag's, `id.kemenag/<kab/kota>`, read at
     * the unit's point), those alone; else its group file's rows of the entry itself at the unit's own point, no unit
     * named (or the unit itself where [Spec.ownTagged]). Those alone: a row that places another table at the unit's point
     * (Yeşilyurt's district table at Malatya's, tr-diyanet.tsv's rows of R204) is that table's proof where it rides, not
     * the unit's table, and replayed across the unit it would be read where it does not ride.
     */
    fun ownRows(manifest: GateManifest, spec: Spec, unit: AuthorityUnit): List<GateRow> {
        val rows = manifest.rows.filter { it.source == "${spec.group}.tsv" && it.entry == spec.entry && it.member == null }
        return rows.filter { it.unit == unit.id && it.lat == null && it.lon == null }.ifEmpty {
            rows.filter { it.unit == (if (spec.ownTagged) unit.id else null) && it.lat == unit.point.lat && it.lon == unit.point.lon }
        }
    }

    /** A point of a grid around a unit: where, at what share of its radius and bearing. */
    class Point(val at: GeoPoint, val share: Double, val bearing: Double)

    /** The points of [shares] × [bearings] around [unit] that resolve to [unit] once rounded. */
    fun points(spec: Spec, unit: AuthorityUnit, zone: String, shares: List<Double> = SHARES, bearings: List<Double> = BEARINGS): List<Point> {
        val entry = requireNotNull(Registry.byId(spec.entry))
        return shares.flatMap { share -> bearings.map { b -> Triple(share, b, QmdbReach.moved(unit.point, unit.radiusKm * share, b)) } }
            .map { (share, b, at) -> Point(GeoPoint(round6(at.lat), round6(at.lon)), share, b) }
            .filter { Registry.resolveEntry(entry, place(spec, unit, it.at, zone)).unitId == unit.id }
    }

    private fun round6(x: Double): Double = String.format(Locale.ROOT, "%.6f", x).toDouble()

    /** The initial great-circle bearing from [from] to [to], in degrees from north, 0 to 360. */
    private fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val f1 = Math.toRadians(from.lat)
        val f2 = Math.toRadians(to.lat)
        val dl = Math.toRadians(to.lon - from.lon)
        val y = Math.sin(dl) * Math.cos(f2)
        val x = Math.cos(f1) * Math.sin(f2) - Math.sin(f1) * Math.cos(f2) * Math.cos(dl)
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
    }

    /** [own] read at [at] for [unit], held out and tagged. */
    fun rowsAt(own: List<GateRow>, unit: AuthorityUnit, at: GeoPoint): List<GateRow> =
        own.map { it.copy(lat = at.lat, lon = at.lon, unit = unit.id, split = Split.TEST) }

    /** Each event's worst point of the sweep around [unit] (the first reached where several tie), and the sweep's broken cells. */
    class Worst(val points: Map<Point, List<Event>>, val swept: Int, val broken: Int, val fine: Boolean)

    fun worst(spec: Spec, unit: AuthorityUnit, own: List<GateRow>, roots: OfficialRoots): Worst {
        val (swept, fine) = sweep(spec, unit, own.first().zone)
        val best = LinkedHashMap<Event, Pair<Point, Int>>()
        var broken = 0
        for (p in swept) {
            val s = Gate(roots).evaluate(GateManifest(rowsAt(own, unit, p.at))).entries[spec.entry] ?: continue
            for ((event, e) in s.events) {
                broken += e.early + e.lateEnd
                if (e.worst > (best[event]?.second ?: -1)) best[event] = p to e.worst
            }
        }
        val points = LinkedHashMap<Point, MutableList<Event>>()
        for ((event, pw) in best) points.getOrPut(pw.first) { mutableListOf() } += event
        return Worst(points, swept.size, broken, fine)
    }

    class Rendered(
        val file: String,
        val units: Int,
        val points: Int,
        val tried: Int,
        val worst: Int,
        val swept: Int,
        val rows: Int,
        val broken: Int,
        val fine: Int,
    )

    fun render(spec: Spec, gate: File, roots: OfficialRoots, threads: Int): Rendered {
        val manifest = GateManifest.load(gate).only(groups = setOf(spec.group))
        val source = gate.resolve("${spec.group}.tsv").readLines()
        val header = source.first { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }
        val names = header.split('\t').map { it.trim() }
        fun col(name: String) = names.indexOf(name).also { require(it >= 0) { "${spec.group}.tsv has no $name column" } }
        val (iEntry, iLat, iLon, iSplit, iNote) = listOf(col("entry"), col("lat"), col("lon"), col("split"), col("note"))
        val units = units(spec)
        val pool = Executors.newFixedThreadPool(threads)
        val worsts = try {
            units.map { unit ->
                pool.submit<Worst> {
                    val own = ownRows(manifest, spec, unit)
                    require(own.isNotEmpty()) { "${spec.entry}/${unit.id}: no row of ${spec.group}.tsv at the unit's own point" }
                    worst(spec, unit, own, roots)
                }
            }.map { it.get() }
        } finally {
            pool.shutdown()
        }
        val out = StringBuilder()
        out.append("# GENERATED by `./gradlew -p tools/timetables generateReachRows` (units/GenerateReachRows.kt); prove extends the\n")
        out.append("# grid's rows each new year through official/monitor/recipes.tsv. ").append(spec.entry).append("'s measured units, each\n")
        out.append("# unit's own tables (").append(spec.group).append(".tsv's rows at its point) replayed across its area: at ")
        out.append(BEARINGS.size).append(" bearings and ").append(SHARES.joinToString(" and ") { "${(it * 100).toInt()} %" })
        out.append(" of its radius, and at\n# each event's worst point of a sweep of ").append(SWEEP.sumOf { it.first.size * it.second.size })
        out.append(" points a unit (and a finer one where few fall inside it); held out and tagged with the unit, a point another\n")
        out.append("# unit takes left out")
        if (spec.leftOut.isNotEmpty()) out.append("; not yet ").append(spec.leftOut.sorted().joinToString(", ")).append(" (ReachRows.SPECS says why)")
        out.append("\n")
        if (spec.watched.isNotEmpty()) {
            out.append("# (and at the points ReachRows.SPECS watches for a unit, where a finer probe read its worst: ruling R321)\n")
        }
        if (spec.waits) {
            out.append("# (a place inside several reaches waits for every such unit's table, ruling R249: the sweep also reads a point\n")
            out.append("# of the ground the unit shares with each neighbour)\n")
        }
        out.append("# (the reviews of 9 and 10 Oct 2026, \"prove it across the unit\"). Coordinates and the tables' own paths only,\n")
        out.append("# never a time.\n")
        out.append(header).append('\n')
        var points = 0
        var tried = 0
        var worstPoints = 0
        var swept = 0
        var rows = 0
        var withRows = 0
        var broken = 0
        for ((unit, worst) in units.zip(worsts)) {
            val own = ownRows(manifest, spec, unit)
            val grid = points(spec, unit, own.first().zone)
            tried += SHARES.size * BEARINGS.size
            points += grid.size
            swept += worst.swept
            broken += worst.broken
            val onGrid = grid.map { it.at }.toSet()
            val extra = worst.points.filterKeys { it.at !in onGrid }
            val watched = spec.watched[unit.id].orEmpty().filter { w -> w.at !in onGrid && extra.keys.none { it.at == w.at } }
            for (w in spec.watched[unit.id].orEmpty()) {
                require(Registry.resolveEntry(requireNotNull(Registry.byId(spec.entry)), place(spec, unit, w.at, own.first().zone)).unitId == unit.id) {
                    "${spec.entry}/${unit.id}: the watched point ${w.at} does not follow the unit"
                }
            }
            worstPoints += extra.size
            if (grid.isNotEmpty() || extra.isNotEmpty() || watched.isNotEmpty()) withRows++
            val radius = String.format(Locale.ROOT, "%.1f", unit.radiusKm)
            fun emit(p: Point, what: String) {
                for (row in own) {
                    val cells = source[row.line - 1].split('\t').toMutableList()
                    cells[iEntry] = "${spec.entry}/${unit.id}"
                    cells[iLat] = String.format(Locale.ROOT, "%.6f", p.at.lat)
                    cells[iLon] = String.format(Locale.ROOT, "%.6f", p.at.lon)
                    cells[iSplit] = "test"
                    cells[iNote] = "${unit.name}'s own table $what at ${String.format(Locale.ROOT, "%.1f", p.share * 100)} % of its " +
                        "$radius km reach, bearing ${String.format(Locale.ROOT, "%.1f", p.bearing)}: ${cells[iNote]}"
                    out.append(cells.joinToString("\t")).append('\n')
                    rows++
                }
            }
            for (p in grid) emit(p, "on the grid")
            for ((p, events) in extra) emit(p, "at the sweep's worst for ${events.joinToString("/") { it.key }}")
            for (w in watched) emit(Point(w.at, distanceKm(unit.point, w.at) / unit.radiusKm, bearing(unit.point, w.at)), "at ${w.why}")
        }
        return Rendered(out.toString(), withRows, points, tried, worstPoints, swept, rows, broken, worsts.count { it.fine })
    }
}
