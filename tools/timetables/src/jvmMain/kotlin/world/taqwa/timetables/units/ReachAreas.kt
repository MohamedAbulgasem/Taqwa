package world.taqwa.timetables.units

import world.taqwa.app.prayer.FixCountry
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Box
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.data.JakimZoneCities
import world.taqwa.app.prayer.engine.registry.data.JakimZones
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
import kotlin.math.cos
import kotlin.math.PI

/**
 * Ruling R297 (16 Nov 2026, the proof-honesty entry's open item (6)): the reach rows of a figure claimed across an area
 * rather than a unit's circle. [ReachRows.SPECS] read each measured unit's own tables across its radius; a claim made over
 * a region rule's shape (London Unified over the M25, a unit of radius 0 that `choose` picks by the M25's polygon) or over
 * a whole country (MUIS over Singapore, the Supreme Council over Bahrain: entries with no units, their table at one fixed
 * point) has no radius, and no reach row read the ground the claim covers past the few points its own gate file names.
 * About's "at most N" (or the published method's "up to N") there was its own point's and the named points', measured on
 * part of the claim alone. So, as the dense radius sweep does for a circle, each [AreaClaim] is read on a grid over a box
 * that holds the whole claim, every point the app would say the figure at kept ([claimed]), and around each event's worst
 * points on a grid four times finer; each event's worst point and the claim's four extremes become rows of the claim's
 * `-reach.tsv` file, which the gate holds to the unit's (or the entry's) limits and counts in its stamp, so About reads the
 * whole claim's worst. `CityUnitsTest` checks the result on a grid offset from both.
 *
 * Ruling R300 (16 Nov 2026, the review of R297): a unit of radius over 0 that the set's `choose` hands to places beyond
 * its own circle claims its figure there too, and the radius sweep reads only the circle ([Chosen], [CHOSEN_BEYOND]).
 *
 * Ruling R307 (the owner's ruling of 17 Nov 2026): a country's figure is read across the app's whole nearest-city cell of
 * the country, sea included ([Cell]), not its drawn ground alone: everywhere the app says the figure to a phone.
 *
 * Ruling R310 (the owner's ruling 8 of 17 Nov 2026, the sweep zone by zone before the 1.7.0 cut): JAKIM's zones, each
 * read across all the ground the app hands it ([Handed], [JAKIM_ZONES]), into one file.
 */
object ReachAreas {

    /**
     * Where a claim's points are kept, each inside a box that must hold it whole (a claimed point on the box's outermost
     * grid line stops the sweep): [Shape], inside a region rule's own shape (London's M25, Regions.londonM25); [Chosen],
     * wherever a fix is placed in the country alone ([Cell]'s rule) and the unit set's `choose` hands the place, with its
     * nearest city's region, the claim's unit (Tayma's town; ruling R300); [Cell], wherever a GPS fix is placed in the
     * country alone (FixCountry under the country's own zone: the nearest app city is the country's, and no other country
     * is held beside it); [Handed], wherever a fix is placed in the country alone and the unit set hands the place the
     * claim's unit by the app's own rule (JAKIM's zones; ruling R310).
     *
     * A point whose nearest app city is another country's is not read by these three. There Automatic holds the day to
     * both countries ("Cautious times", no figure), and a user who chooses the claim's timetable has it resolved in the
     * claim's country through the second country (ruling R259): since ruling R318 (the owner's ruling of 18 Nov 2026, the
     * final audit's B1) it is not measured there, so About says "Not measured" and claims no figure where no row reads
     * it (`readInCountryAlone` in `Authority.kt`, held to these claims by `ChosenAcrossBorderPlacesTest`). Until R318 the
     * figure was claimed there unread (Batu Pahat on a Singapore network, Dammam on a Bahraini one). [Shape] reads every
     * point inside its shape, whatever the nearest city, so London Unified keeps its figure across the M25.
     *
     * The app's nearest-city rule hands a country more than its ground, out to sea (Singapore's cell reaches 35 km from a
     * Singaporean city into the Riau islands' waters, Bahrain's 120 km into the Gulf), and the app says the country's
     * figure there too. Since ruling R307 (17 Nov 2026) the figure is read across that whole cell, so About's "at most N"
     * (or the published method's "up to N") is measured everywhere the app says it. Until then (rulings R297 to R300) it
     * was read across the country's ground alone, drawn coarsely by hand (land and the water between its islands), and
     * a diagnostic of the cell was the owner's question; a bundled border lookup, which would place a fix at sea in no
     * country, would make the ground the right area again.
     */
    sealed interface Claim

    class Shape(val name: String, val area: world.taqwa.app.prayer.engine.registry.Area) : Claim

    /** Where the entry's unit set chooses the claim's unit ([UnitSet.choose]), called [name]. */
    class Chosen(val name: String) : Claim

    /** The app's nearest-city cell of the country, called [name]: every place a fix is placed in the country alone (ruling R307). */
    class Cell(val name: String) : Claim

    /**
     * Ruling R310: wherever a fix is placed in the country alone (as for [Cell]) and the entry's unit set hands the
     * place the claim's unit by the app's own rule ([UnitSet.unitFor]: `choose`, else the nearest circle that holds it),
     * called [name]: every point the app says the unit's figure at, its circle and whatever `choose` gives it beyond.
     */
    class Handed(val name: String) : Claim

    /**
     * One figure claimed across an area: [entry], its group file (the own rows at the table's point), the [unit] the area
     * resolves to (null for an entry with no units, read at its fixed point), the [country] and [zone] a point is placed in,
     * the [box] swept (one that must hold the claim whole: a claimed point on its outermost grid line stops the sweep), the
     * grid's spacing [stepKm], what the claim is ([claim], [what]) and the [file] its rows are written to (several claims
     * may share one: JAKIM's zones, ruling R310). [ridden]: other tables ride beside the claim's own across it (JAKIM's
     * neighbouring zones, `Jakim.withNeighbours`), so its reach limit keeps the reason that names them, not the area's
     * (which says no other table rides; `AREA_READ_UNITS` in `Authority.kt`).
     */
    class AreaClaim(
        val entry: String,
        val group: String,
        val unit: String?,
        val country: String,
        val zone: String,
        val box: Box,
        val stepKm: Double,
        val claim: Claim,
        val what: String,
        val file: String = "$group-reach.tsv",
        val ridden: Boolean = false,
    ) {

        /** The `entry` column of the claim's rows: `<entry>/<unit>`, or the bare entry where it has no units. */
        val tag: String get() = if (unit == null) entry else "$entry/$unit"
    }

    /**
     * Every figure claimed across an area that is read ([areaClaims] names the entries; a release is refused while one has
     * no line here, [ReachRows.unswept]): London Unified's M25, MUIS's Singapore and the Supreme Council's Bahrain, each
     * across the app's whole nearest-city cell of the country (ruling R307: Singapore's 0.5 km and Bahrain's 1 km grids,
     * the steps of the owner's diagnostic of 16 Nov 2026), (ruling R300) KACST's Tayma table within a minute's reach of
     * the town, and since ruling R310 every measured zone of JAKIM's ([JAKIM_ZONES]).
     */
    val AREAS: List<AreaClaim> by lazy { FIXED + JAKIM_ZONES }

    private val FIXED: List<AreaClaim> = listOf(
        AreaClaim(
            "gb.london.lupt", "gb-london-lupt", "gb.london.lupt", "GB", "Europe/London",
            Box(51.20, 51.77, -0.62, 0.37), 0.4, Shape("the M25", Regions.londonM25),
            "London Unified's one table, followed across the whole M25 (ruling R44)",
        ),
        AreaClaim(
            "sg.muis", "sg-muis", null, "SG", "Asia/Singapore",
            Box(0.90, 1.52, 103.45, 104.15), 0.5, Cell("Singapore's cell"),
            "MUIS's one table, followed at its fixed point wherever the app places a phone in Singapore (rulings R297 and R307)",
        ),
        AreaClaim(
            "bh.council", "bh-council", null, "BH", "Asia/Bahrain",
            Box(25.38, 27.22, 50.05, 51.75), 1.0, Cell("Bahrain's cell"),
            "the Supreme Council's one book, followed at its fixed points wherever the app places a phone in Bahrain (rulings R297 and R307)",
        ),
        AreaClaim(
            "sa.ummalqura", "sa-ummalqura", "tayma", "SA", "Asia/Riyadh",
            Box(27.35, 27.90, 38.22, 38.86), 0.25, Chosen("the town of Tayma"),
            "KACST's Tayma table, 111 km north of the town, followed within a minute's reach of the town (UmmAlQura.taymaTown, rulings R44 and R300)",
            file = "sa-ummalqura-tayma-reach.tsv",
        ),
    )

    /**
     * Ruling R310 (the owner's ruling 8 of 17 Nov 2026): every measured zone of JAKIM's, read across all the ground the
     * app hands it ([Handed]: a fix placed in Malaysia alone whose place `Jakim.units` gives the zone, by the nearest town
     * its list holds within [JakimZones.CITY_KM], the nearest place it names, a hill zone's within [JakimZones.HILL_KM],
     * else the nearest circle that holds it), each point against that zone's own tables in the archive (my-jakim.tsv's
     * rows at the zone), on a grid of [JAKIM_STEP_KM] and four times finer around each event's worst. The box of a zone
     * holds its circle and a [JakimZones.CITY_KM] disc round every town of its list ([JakimZoneCities]), every place it
     * names and its point, widened by [JAKIM_MARGIN_KM], so that no point the zone is handed lies on its outer grid line
     * (the sweep stops on one). All zones' rows go to one file, `my-jakim-zones-reach.tsv`. The neighbouring zones ride
     * beside a zone's own table there (`Jakim.withNeighbours`), and the gate reads each row as the app computes the place,
     * so a figure measured here is the place's, neighbours and all ([AreaClaim.ridden]).
     */
    val JAKIM_ZONES: List<AreaClaim> = run {
        val set = requireNotNull(Units.of("my.jakim"))
        set.units.filter { it.measured }.map { unit ->
            val z = JakimZones.zones.first { it.id == unit.id }
            val discs = (JakimZoneCities.byZone[z.id].orEmpty().map { GeoPoint(it.lat, it.lon) } + z.places + z.point)
                .map { it to JakimZones.CITY_KM } + (z.point to z.radiusKm)
            val km = discs.map { (p, r) -> Triple(p, r + JAKIM_MARGIN_KM, KM_PER_DEGREE * cos(p.lat * PI / 180)) }
            val box = Box(
                south = km.minOf { (p, r, _) -> p.lat - r / KM_PER_DEGREE },
                north = km.maxOf { (p, r, _) -> p.lat + r / KM_PER_DEGREE },
                west = km.minOf { (p, r, perLon) -> p.lon - r / perLon },
                east = km.maxOf { (p, r, perLon) -> p.lon + r / perLon },
            ).let { Box(round6(it.south), round6(it.north), round6(it.west), round6(it.east)) }
            AreaClaim(
                "my.jakim", "my-jakim", unit.id, "MY", if (z.point.lon >= JAKIM_BORNEO_WEST_LON) "Asia/Kuching" else "Asia/Kuala_Lumpur",
                box, JAKIM_STEP_KM, Handed("the ground the app hands ${unit.id}"),
                "e-solat's table for ${unit.id}, followed across all the ground the app hands the zone (rulings R226 and R310)",
                file = JAKIM_FILE, ridden = true,
            )
        }
    }

    /** The grid's spacing for JAKIM's zones (ruling R310): about 85,000 points over the ground the zones are handed. */
    const val JAKIM_STEP_KM = 1.0

    /** How far a JAKIM zone's box reaches past its discs and circle (ruling R310): three steps. */
    private const val JAKIM_MARGIN_KM = 3.0

    /** Where JAKIM's zones are in Borneo (`Jakim.BORNEO_WEST_LON`): a fix there is placed under Asia/Kuching. */
    private const val JAKIM_BORNEO_WEST_LON = 109.5

    /** The one file JAKIM's zones are written to (ruling R310). */
    const val JAKIM_FILE = "my-jakim-zones-reach.tsv"

    /**
     * Ruling R300: the entries whose unit set's `choose` hands a measured unit of radius over 0 to places beyond that
     * unit's own circle, so that the unit's figure is said across ground the radius sweep ([ReachRows.SPECS]) does not
     * read, each with where. [areaClaims] names them; `CityUnitsTest` looks for such places by [chosenBeyond] among the
     * app's cities, JAKIM's named places and a 0.05° grid over each set's countries, holds this list to exactly the entries
     * it finds, and every place found of an entry [AREAS] reads to a point one of its claims reads. None is owed: Tayma's
     * is read by its [Chosen] claim (ruling R300), and JAKIM's (46 of its 56 zones are chosen past their circle on the
     * app's cities and the grid) by [JAKIM_ZONES] since ruling R310 (17 Nov 2026). Brunei's stood here from R300 until
     * ruling R309 (17 Nov 2026): a district is now given to a place only within its own circle (`Muis.bruneiUnits`), so
     * none is chosen past it.
     */
    val CHOSEN_BEYOND: Map<String, String> = mapOf(
        "sa.ummalqura" to "Tayma's unit, at KACST's point 111 km north of the town, chosen within a minute's reach of the town",
        "my.jakim" to "a zone chosen within 10 km of every town its list names (5 km of a hill zone's named place), towns up to 128 km from the zone's point",
    )

    /**
     * Where [set]'s `choose` hands a place of [places] a measured unit of radius over 0 beyond that unit's own circle
     * (ruling R300): each such place with the unit. A unit of radius 0 is a shape's ([areaClaims] names it already).
     */
    fun chosenBeyond(set: UnitSet, places: List<Place>): List<Pair<Place, AuthorityUnit>> {
        val choose = set.choose ?: return emptyList()
        return places.mapNotNull { place ->
            choose(place)?.takeIf { it.measured && it.radiusKm > 0.0 && distanceKm(GeoPoint(place.lat, place.lon), it.point) > it.radiusKm + 1e-9 }
                ?.let { place to it }
        }
    }

    /** The app's cities, as FixCountry places a GPS fix, each with its region (which the place takes from it). */
    class Cities(file: File) {
        class City(val city: EnvelopeRows.City, val region: String?)

        private val cities = EnvelopeRows.cities(file).zip(
            file.readLines().drop(1).filter { it.isNotBlank() }.map { line -> line.split(",")[2].ifBlank { null } },
        ) { city, region -> City(city, region) }
        val index = FixCountry.Index(cities, { it.city.at.lat }, { it.city.at.lon }, { it.city.cc.uppercase() }, { it.city.zone })
    }

    /**
     * [at] as the app places a GPS fix under [a]'s zone, where it is placed in [a]'s country alone (FixCountry: the nearest
     * app city is the country's, and no other country is held beside it): the place, with the nearest city's region, or
     * null elsewhere.
     */
    private fun placed(a: AreaClaim, at: GeoPoint, cities: Cities): Place? =
        FixCountry.choice(at.lat, at.lon, a.zone, cities.index)
            ?.takeIf { it.city.city.cc.uppercase() == a.country && it.beside == null }
            ?.let { Place(at.lat, at.lon, a.zone, a.country, it.city.region) }

    /** Whether the app says [a]'s figure at [at]: the point is in the claim, resolves to its unit (or entry) and is measured there. */
    fun claimed(a: AreaClaim, at: GeoPoint, cities: Cities): Boolean {
        val place = when (val c = a.claim) {
            is Shape -> Place(at.lat, at.lon, a.zone, a.country).takeIf { c.area.contains(at) }
            // Where the fix is placed in the country, with its nearest city's region (Brunei's districts go by it), and the
            // set's `choose` hands it the claim's unit.
            is Chosen -> placed(a, at, cities)?.takeIf { Units.of(a.entry)?.choose?.invoke(it)?.id == a.unit }
            is Cell -> placed(a, at, cities)
            // Ruling R310: wherever the fix is placed in the country alone; the resolution below says whether the set hands
            // it the claim's unit, as the app does (UnitSet.unitFor).
            is Handed -> placed(a, at, cities)
        } ?: return false
        val r = Registry.resolveEntry(requireNotNull(Registry.byId(a.entry)), place)
        return r.measured && r.unitId == a.unit
    }

    /**
     * The own rows of [a]: its group file's rows of the entry (and unit) at the table's own point, tagged with the unit
     * there, or (a group file that names its units' points, as sa-ummalqura.tsv does) the entry's rows at the unit's
     * point, as [ReachRows.ownRows] reads them.
     */
    fun ownRows(manifest: GateManifest, a: AreaClaim): List<GateRow> {
        val rows = manifest.rows.filter { it.source == "${a.group}.tsv" && it.entry == a.entry && it.member == null && !it.promiseOnly }
        val tagged = rows.filter { it.unit == a.unit && it.lat == null && it.lon == null }
        if (tagged.isNotEmpty() || a.unit == null) return tagged
        val point = Units.of(a.entry)?.units?.firstOrNull { it.id == a.unit }?.point ?: return tagged
        return rows.filter { it.unit == null && it.lat == point.lat && it.lon == point.lon }
    }

    /** [own] read at [at], held out and tagged with the claim's unit. */
    fun rowsAt(own: List<GateRow>, a: AreaClaim, at: GeoPoint): List<GateRow> =
        own.map { it.copy(lat = at.lat, lon = at.lon, unit = a.unit, split = Split.TEST) }

    /** The grid of [a]'s box at [stepKm] (its own by default), offset by [shift] steps, as rounded points; row by row, south to north. */
    fun grid(a: AreaClaim, stepKm: Double = a.stepKm, shift: Double = 0.0): List<List<GeoPoint>> {
        val midLat = (a.box.south + a.box.north) / 2
        val dLat = stepKm / KM_PER_DEGREE
        val dLon = stepKm / (KM_PER_DEGREE * cos(midLat * PI / 180))
        val rows = ArrayList<List<GeoPoint>>()
        var lat = a.box.south + shift * dLat
        while (lat <= a.box.north + 1e-9) {
            val row = ArrayList<GeoPoint>()
            var lon = a.box.west + shift * dLon
            while (lon <= a.box.east + 1e-9) {
                row += GeoPoint(round6(lat), round6(lon))
                lon += dLon
            }
            rows += row
            lat += dLat
        }
        return rows
    }

    /** What one point of the sweep read: each event's worst minutes after (before, for an end), and its broken cells. */
    class Read(val at: GeoPoint, val worst: Map<Event, Int>, val broken: Int)

    fun read(a: AreaClaim, own: List<GateRow>, at: GeoPoint, roots: OfficialRoots): Read {
        val s = Gate(roots).evaluate(GateManifest(rowsAt(own, a, at))).entries[a.entry] ?: return Read(at, emptyMap(), 0)
        return Read(at, s.events.mapValues { it.value.worst }, s.events.values.sumOf { it.early + it.lateEnd })
    }

    class Swept(
        val claimed: List<GeoPoint>,
        val extremes: Map<String, GeoPoint>,
        val worst: Map<Event, Read>,
        val swept: Int,
        val broken: Int,
    )

    /**
     * The sweep: every grid point the app claims [a]'s figure at, then around each event's worst points (every point at
     * the event's worst, or [REFINED_AT] of them spread evenly through the grid where more tie) a grid four times finer
     * within a step of it. Each event's worst point is the first read at its largest figure.
     */
    fun sweep(a: AreaClaim, own: List<GateRow>, roots: OfficialRoots, cities: Cities, threads: Int): Swept {
        val rows = grid(a)
        val claimed = ArrayList<GeoPoint>()
        rows.forEachIndexed { i, row ->
            row.forEachIndexed { j, p ->
                if (claimed(a, p, cities)) {
                    require(i != 0 && i != rows.size - 1 && j != 0 && j != row.size - 1) {
                        "${a.entry}: the claim reaches its box's edge at $p: widen ReachAreas.AREAS' box so that it holds the whole claim"
                    }
                    claimed += p
                }
            }
        }
        require(claimed.isNotEmpty()) { "${a.entry}: no point of its box is claimed" }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            fun readAll(points: List<GeoPoint>): List<Read> = points.map { p -> pool.submit<Read> { read(a, own, p, roots) } }.map { it.get() }
            val reads = readAll(claimed).toMutableList()
            val best = LinkedHashMap<Event, Read>()
            fun note(r: Read) {
                for ((e, m) in r.worst) if (m > (best[e]?.worst?.get(e) ?: -1)) best[e] = r
            }
            reads.forEach(::note)
            val midLat = (a.box.south + a.box.north) / 2
            val dLat = a.stepKm / KM_PER_DEGREE
            val dLon = a.stepKm / (KM_PER_DEGREE * cos(midLat * PI / 180))
            val seen = claimed.toHashSet()
            val finer = ArrayList<GeoPoint>()
            for (e in best.keys.toList()) {
                val top = best.getValue(e).worst.getValue(e)
                val ties = reads.filter { it.worst[e] == top }
                val every = (ties.size + REFINED_AT - 1) / REFINED_AT
                for (r in ties.filterIndexed { i, _ -> i % every == 0 }) {
                    for (i in -3..3) for (j in -3..3) {
                        if (i == 0 && j == 0) continue
                        val p = GeoPoint(round6(r.at.lat + i * dLat / 4), round6(r.at.lon + j * dLon / 4))
                        if (seen.add(p) && claimed(a, p, cities)) finer += p
                    }
                }
            }
            val refined = readAll(finer)
            refined.forEach(::note)
            reads += refined
            val all = claimed + finer
            val extremes = linkedMapOf(
                "northernmost" to all.maxWith(compareBy<GeoPoint> { it.lat }.thenBy { it.lon }),
                "southernmost" to all.minWith(compareBy<GeoPoint> { it.lat }.thenBy { it.lon }),
                "easternmost" to all.maxWith(compareBy<GeoPoint> { it.lon }.thenBy { it.lat }),
                "westernmost" to all.minWith(compareBy<GeoPoint> { it.lon }.thenBy { it.lat }),
            )
            return Swept(all, extremes, best, claimed.size, reads.sumOf { it.broken })
        } finally {
            pool.shutdown()
        }
    }

    /**
     * One claim's rows: [file] is the whole file where the claim has a file of its own; [columns] and [body] (the column
     * header and the rows alone) are what [fileText] joins where several claims share one (JAKIM's zones, ruling R310).
     */
    class Rendered(
        val file: String,
        val points: Int,
        val claimed: Int,
        val swept: Int,
        val rows: Int,
        val broken: Int,
        val worst: Map<Event, Int>,
        val columns: String = "",
        val body: String = "",
    )

    /**
     * The text of the file [claims] share, from each claim's [Rendered] ([parts], in the same order): the claim's own file
     * where it is the only one, else one head saying what the file holds, the column header once, and every claim's rows.
     */
    fun fileText(claims: List<AreaClaim>, parts: List<Rendered>): String {
        require(claims.size == parts.size && claims.isNotEmpty() && claims.all { it.file == claims.first().file })
        if (claims.size == 1) return parts.single().file
        require(parts.all { it.columns == parts.first().columns }) { "${claims.first().file}: its claims' group files differ in their columns" }
        val out = StringBuilder()
        out.append("# GENERATED by `./gradlew -p tools/timetables generateReachRows` (units/ReachAreas.kt, rulings R297 and R310); prove\n")
        out.append("# extends these rows each new year through official/monitor/recipes.tsv. ").append(claims.size)
        out.append(" claims of ").append(claims.map { it.entry }.distinct().joinToString(", ")).append(", each its unit's own\n")
        out.append("# tables (").append(claims.map { it.group }.distinct().joinToString(", ") { "$it.tsv" })
        out.append("'s rows at the unit) replayed across all the ground the app hands the unit, on a grid of ")
        out.append(String.format(Locale.ROOT, "%.2f", claims.first().stepKm)).append(" km\n")
        out.append("# over its box and four times finer around each event's worst (").append(parts.sumOf { it.claimed })
        out.append(" points claimed in all): each claim's four extremes and each\n")
        out.append("# event's worst point, held out and tagged with the claim's unit. Coordinates and the tables' own paths only, never a time.\n")
        out.append(parts.first().columns).append('\n')
        for (part in parts) out.append(part.body)
        return out.toString()
    }

    fun render(a: AreaClaim, gate: File, roots: OfficialRoots, cities: Cities, threads: Int): Rendered {
        val manifest = GateManifest.load(gate).only(groups = setOf(a.group))
        val own = ownRows(manifest, a)
        require(own.isNotEmpty()) { "${a.entry}: no row of ${a.group}.tsv at the table's own point" }
        val source = gate.resolve("${a.group}.tsv").readLines()
        val header = source.first { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("@") }
        val names = header.split('\t').map { it.trim() }
        fun col(name: String) = names.indexOf(name).also { require(it >= 0) { "${a.group}.tsv has no $name column" } }
        val (iEntry, iLat, iLon, iSplit, iNote) = listOf(col("entry"), col("lat"), col("lon"), col("split"), col("note"))
        val swept = sweep(a, own, roots, cities, threads)
        val points = LinkedHashMap<GeoPoint, MutableList<String>>()
        for ((name, p) in swept.extremes) points.getOrPut(p) { mutableListOf() } += "at the claim's $name point"
        for ((e, r) in swept.worst) points.getOrPut(r.at) { mutableListOf() } += "at the sweep's worst for ${e.key}"
        val out = StringBuilder()
        out.append("# GENERATED by `./gradlew -p tools/timetables generateReachRows` (units/ReachAreas.kt, ruling R297); prove extends\n")
        out.append("# these rows each new year through official/monitor/recipes.tsv. ").append(a.what).append(": its own\n")
        out.append("# tables (").append(a.group).append(".tsv's rows at the table's point) replayed across the claim, on a grid of ")
        out.append(String.format(Locale.ROOT, "%.2f", a.stepKm)).append(" km over its box and four times finer\n")
        out.append("# around each event's worst (").append(swept.claimed.size).append(" points claimed): the claim's four extremes and each event's worst\n")
        out.append("# point, held out and tagged with the claim. Coordinates and the tables' own paths only, never a time.\n")
        out.append(header).append('\n')
        val head = out.length
        var rows = 0
        for ((p, whys) in points) {
            for (row in own) {
                val cells = source[row.line - 1].split('\t').toMutableList()
                cells[iEntry] = a.tag
                cells[iLat] = String.format(Locale.ROOT, "%.6f", p.lat)
                cells[iLon] = String.format(Locale.ROOT, "%.6f", p.lon)
                cells[iSplit] = "test"
                cells[iNote] = "${claimName(a)} ${whys.joinToString(" and ")}: ${cells[iNote]}"
                out.append(cells.joinToString("\t")).append('\n')
                rows++
            }
        }
        val worst = swept.worst.mapValues { (e, r) -> r.worst.getValue(e) }.toSortedMap()
        return Rendered(out.toString(), points.size, swept.claimed.size, swept.swept, rows, swept.broken, worst, header, out.substring(head))
    }

    private fun claimName(a: AreaClaim): String = when (val c = a.claim) {
        is Shape -> "the table read across ${c.name}"
        is Chosen -> "the table read across ${c.name}"
        is Cell -> "the table read across ${c.name}"
        is Handed -> "the table read across ${c.name}"
    }

    /**
     * Ruling R297's hold: the entries [entries] names that say a figure (class A or B's "at most N", or the published
     * method's "up to N", class D of an authority, where measured) across ground no unit's circle bounds, of four kinds:
     * an entry with no units, measured, its table at one point and its claim its countries; a measured unit of radius 0
     * (its ground a region's shape, chosen by `choose`); a measured entry whose unit set keeps it measured beyond every
     * unit (`measuredBeyond`, as the registry resolves such a place); and (ruling R300) a measured entry whose set's
     * `choose` hands a measured unit to places beyond its circle ([chosenBeyond]: the entries [beyond] names, which
     * `CityUnitsTest` holds to what the app's cities show). A cautious entry (class C) says no figure of minutes, and is
     * not named.
     */
    fun areaClaims(
        entries: List<RegistryEntry> = Registry.entries,
        units: (String) -> UnitSet? = Units::of,
        beyond: Set<String> = CHOSEN_BEYOND.keys,
    ): List<String> =
        entries.filter { e ->
            e.members.isEmpty() && e.entryClass in FIGURE_CLASSES && run {
                val set = units(e.id)
                if (set == null) {
                    e.measured
                } else {
                    // The fourth kind claims a measured unit's figure, whether or not the entry is measured beyond its units.
                    (e.measured && set.measuredBeyond) || set.units.any { it.measured && it.radiusKm == 0.0 } ||
                        (e.id in beyond && set.units.any { it.measured })
                }
            }
        }.map { it.id }.distinct().sorted()

    private val FIGURE_CLASSES = setOf(EntryClass.A, EntryClass.B, EntryClass.D_AUTHORITY)

    /** Around how many of an event's worst grid points the finer grid is read. */
    const val REFINED_AT = 24

    private const val KM_PER_DEGREE = 111.19

    private fun round6(x: Double): Double = String.format(Locale.ROOT, "%.6f", x).toDouble()
}
