package world.taqwa.timetables.units

import world.taqwa.timetables.gate.GateManifest
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Egypt
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.timetables.TestPaths
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The envelope's promise-only rows past the bound (the owner's decision of 17 Oct 2026): the nearest table printed for its
 * own point is replayed at every app city within its ride, as `TableEnvelope` rides it (Wifaqul's where it is chosen),
 * Rabita's Helsinki calendar at the app's Finnish cities and on rings around Helsinki within its ride, and ISNA's Montréal
 * and Ottawa mosques under Canada's cautious times outside the two cities. Read from the generator, not the committed files, so a year `prove` adds to a
 * table's rows does not fail it.
 */
class EnvelopeRowsTest {
    private val gate = TestPaths.repoRoot.resolve("tools/timetables/official/gate")
    private val cities = EnvelopeRows.cities(TestPaths.appFiles.resolve("cities.csv"))
    private val written by lazy { EnvelopeRows.render(gate, cities) }

    private fun linesAt(file: String, city: String): List<String> =
        written.getValue(file).text.lines().filter { "at the app's $city," in it }

    @Test
    fun `the riders are every table whose bound holds a place and the nearest within its ride past them`() {
        val envelope = Units.of("us.isna")!!.envelope!!
        // Winchester, Virginia: past every bound, ADAMS's ride holds it.
        val winchester = EnvelopeRows.riders(envelope, GeoPoint(39.18566, -78.16333))
        assertEquals(listOf("adams"), winchester.map { it.first.id })
        assertTrue("ride past its bound" in winchester.single().second)
        // Sterling, inside ADAMS's unit: the tables whose bound holds it, all of them bound rows.
        val sterling = EnvelopeRows.riders(envelope, GeoPoint(39.0062, -77.4286))
        assertTrue(sterling.size > 1 && sterling.all { "bound reach" in it.second }, "$sterling")
    }

    @Test
    fun `past every bound the nearest table's rows are replayed at the app's cities within its ride`() {
        val winchester = linesAt("us-isna-envelope.tsv", "Winchester")
        assertTrue(winchester.isNotEmpty() && winchester.all { "ADAMS Center, Sterling's own table" in it && "ride past its bound" in it }, "$winchester")
        assertTrue(linesAt("uz-board-envelope.tsv", "Angren").isNotEmpty())
        // Harrisonburg, Virginia, is past every ride: no row.
        assertTrue(linesAt("us-isna-envelope.tsv", "Harrisonburg").isEmpty())
        // Rabita's calendar at Porvoo, within its ride; not at Sipoo (se-cautious.tsv's own row) nor at Lahti past its ride.
        val porvoo = linesAt("se-cautious-envelope.tsv", "Porvoo")
        assertTrue(porvoo.isNotEmpty() && porvoo.all { "Rabita's Helsinki calendar" in it }, "$porvoo")
        assertTrue(linesAt("se-cautious-envelope.tsv", "Sibbo").isEmpty() && linesAt("se-cautious-envelope.tsv", "Lahti").isEmpty())
        // Wifaqul chosen at Leeds, past Manchester Central's bound, within its ride.
        val leeds = linesAt("gb-wifaqul-envelope.tsv", "Leeds")
        assertTrue(leeds.isNotEmpty() && leeds.all { "ride past its bound" in it && it.split('\t')[1] == "gb.wifaqul" }, "$leeds")
    }

    @Test
    fun `rabita's calendar is replayed on rings around helsinki across its ride`() {
        val ring = EnvelopeRows.rabitaRing()
        assertEquals(12 * 24, ring.size)
        val lines = written.getValue("se-cautious-envelope.tsv").text.lines().filter { "km from Helsinki at " in it }
        assertEquals(ring.size, lines.size, "one row of Rabita's calendar at every ring point")
        assertTrue(lines.all { it.split('\t')[1] == "se.cautious" && it.trimEnd().endsWith("se.rabita") }, lines.first())
    }

    @Test
    fun `isna's montreal and ottawa mosques are replayed under canada's cautious times outside the two cities`() {
        val file = written.getValue("ca-isna-envelope.tsv").text.lines()
        val header = file.first { it.startsWith("path\t") }.split('\t')
        assertEquals("member", header.last())
        // Drummondville, 87 km from CCML, outside Montreal's box: CCML's rows under ca.cautious, named by its most-followed member.
        val drummondville = file.filter { "at the app's Drummondville," in it && "beside ca.cautious" in it }
        assertTrue(drummondville.isNotEmpty(), "no ca.cautious row at Drummondville")
        for (line in drummondville) {
            val cells = line.split('\t')
            assertEquals("ca.cautious", cells[1])
            assertEquals("ca.eighteen", cells.last())
            assertTrue("CCML, Longueuil's own table" in line, line)
        }
        // Inside Montréal's box Automatic is ISNA itself: no ca.cautious row at the app's Laval.
        assertTrue(file.none { "at the app's Laval," in it && "beside ca.cautious" in it })
    }

    @Test
    fun `a place by a border of jakim's zones is read against every zone that could own it`() {
        // The review round of 13 Nov 2026: the districts' borders are not held, so the zone a place's own list gives it
        // is the zone it takes or rides beside it, and its table is replayed at the place. The engine, not this file,
        // says which zones those are.
        fun zonesAt(lat: Double, lon: Double): Set<String> {
            val here = EnvelopeRows.jakimAt(GeoPoint(lat, lon), if (lon >= 109.5) "Asia/Kuching" else "Asia/Kuala_Lumpur")
            return (listOfNotNull(here.taken) + here.riding).map { it.id }.toSet()
        }
        assertTrue(zonesAt(2.455, 102.235).containsAll(setOf("MLK01", "NGS01")), "Pulau Sebang, across the border from Tampin")
        assertTrue(zonesAt(3.672, 101.523).containsAll(setOf("SGR01", "PRK01")), "Hulu Bernam, across the river from Tanjong Malim")
        assertTrue(zonesAt(5.2245, 100.684).containsAll(setOf("KDH05", "PRK06")), "Selama's Kedah bank")
        assertTrue("KDH05" in zonesAt(5.33, 100.75), "Mahang")
        assertTrue("PHG06" in zonesAt(3.4236, 101.7932) && "PHG06" in zonesAt(3.712, 101.737), "Genting Highlands and Bukit Fraser")
        assertTrue(zonesAt(4.862, 100.793).containsAll(setOf("PRK07", "PRK06")), "Bukit Larut above Taiping")
        assertTrue("KDH07" in zonesAt(5.787, 100.435), "Gunung Jerai's summit")
        for (place in EnvelopeRows.JAKIM_PLACES) {
            assertTrue(place.zone in zonesAt(place.at.lat, place.at.lon), "${place.name}: ${place.zone}, its zone by JAKIM's list, neither holds it nor rides")
        }
        val lines = written.getValue("my-jakim-envelope.tsv").text.lines().filter { !it.startsWith("#") && it.isNotBlank() }
        // The zone a place takes is read in my-jakim-cities.tsv with its limits (the second trust audit's G28, ruling R274);
        // a zone riding beside it here, held to the promise.
        val cityRows = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")).only(setOf("my-jakim-cities")).rows
        for (place in EnvelopeRows.JAKIM_PLACES) {
            val own = lines.filter { "${place.zone}'s own table at ${place.name} (${place.zone} by JAKIM's list)" in it }
            // A zone's fitted point under its own unit is my-jakim.tsv's row already; anywhere else its own table is replayed.
            val atOwnPoint = Units.of("my.jakim")!!.units.any { it.id == place.zone && it.point == place.at }
            val takes = EnvelopeRows.jakimAt(place.at, if (place.at.lon >= 109.5) "Asia/Kuching" else "Asia/Kuala_Lumpur").taken?.id == place.zone
            val inCities = cityRows.any { it.unit == place.zone && it.lat == place.at.lat && it.lon == place.at.lon }
            assertTrue(atOwnPoint || (if (takes) inCities else own.isNotEmpty()), "${place.name}: no row of ${place.zone}'s own table")
            assertTrue(!takes || own.isEmpty(), "${place.name}: the zone it takes is no promise-only row")
            assertTrue(own.all { it.split('\t')[1] == "my.jakim" }, "${place.name}: read as the place resolves, not at a unit")
        }
        // A town of the app's list a zone's list holds is its zone's alone (my-jakim-cities.tsv reads it): no row here.
        val tampin = cities.single { it.cc == "MY" && it.name == "Tampin" }
        assertTrue(EnvelopeRows.jakimListedTown(tampin.at), "Tampin is a town of NGS01's list")
        assertTrue(lines.none { " at Tampin " in it || "the app's Tampin" in it }, "a listed town has no row here")
    }

    @Test
    fun `each egyptian town's table is replayed past its reach wherever it rides beside the edge`() {
        // Ruling R213 (the audit of 12 Nov 2026, finding F13): the belt out to twice a town's reach.
        val units = Units.of(EnvelopeRows.EGYPT)!!.units
        val entry = Registry.byId(EnvelopeRows.EGYPT)!!
        val belt = EnvelopeRows.egyptBelt(cities)
        for (b in belt) {
            val where = "${b.table.id} at ${b.place.name}"
            assertEquals(null, Registry.resolveEntry(entry, Place(b.place.at.lat, b.place.at.lon, "Africa/Cairo", "EG")).unitId, where)
            assertTrue(Egypt.beside(b.place.at).any { it.id == b.table.id }, where)
            val km = distanceKm(b.place.at, b.table.point)
            assertTrue(km > b.table.radiusKm && km <= b.table.radiusKm * Egypt.BESIDE_REACHES, "$where: $km km")
        }
        // Every town is read on its belt's rings, just past the reach and just inside the belt's end among them, and at
        // the app's cities that stand in a belt: Banhā, past Cairo's and Zagazig's reaches and within twice of each.
        val rings = belt.filter { "bearing" in it.place.name }
        assertEquals(units.map { it.id }.toSet(), rings.map { it.table.id }.toSet())
        for (share in listOf(EnvelopeRows.EGYPT_BELT_SHARES.first(), EnvelopeRows.EGYPT_BELT_SHARES.last())) {
            assertTrue(share > 1.0 && share < Egypt.BESIDE_REACHES)
            val read = rings.filter { abs(distanceKm(it.place.at, it.table.point) / it.table.radiusKm - share) < 0.001 }.map { it.table.id }.toSet()
            assertTrue(read.size > units.size / 2, "at $share of the reach: ${units.map { it.id } - read}")
        }
        val banha = linesAt("eg-esa-envelope.tsv", "Banhā")
        assertTrue(banha.any { "Cairo's own table" in it } && banha.any { "Zagazig's own table" in it }, banha.firstOrNull())
        // A place a town's reach holds is the reach rows', never the belt's: Tanta's own point.
        assertTrue(belt.none { it.place.at == units.single { u -> u.id == "tanta" }.point })
        val lines = written.getValue("eg-esa-envelope.tsv").text.lines().filter { "(ruling R213)" in it && !it.startsWith("#") }
        assertTrue(lines.size >= belt.size && lines.all { it.split('\t')[1] == "eg.esa" }, "written under the bare entry, which resolves to the edge")
    }

    @Test
    fun `past every kyrgyz reach egyptian belt and omani ride the tables the edge reads there are replayed`() {
        // Ruling R260 (the second audit of 14 Nov 2026, findings G1, G2 and G3): past each inner line the nearest unit's
        // table, and any no more than a quarter farther, rides beside the edge's day; its rows are replayed on rings out
        // to the end of that ride and at the app's cities past every inner line, wherever the engine reads it. And past
        // the table's own line inside a farther unit's (ruling R262, the edges lane's second review of 15 Nov 2026), where
        // the nearest rides beside that unit's table: those rings are written too, noted as R262's.
        val insideAnother = HashMap<String, Int>()
        for (line in EnvelopeRows.EDGE_LINES) {
            val units = Units.of(line.entry)!!.units
            val entry = Registry.byId(line.entry)!!
            val rings = EnvelopeRows.edgeRings(line, cities)
            assertTrue(rings.count { "bearing" in it.place.name } > units.size, "${line.entry}: ${rings.size} ring rows")
            for (b in rings) {
                val where = "${line.entry}/${b.table.id} at ${b.place.name}"
                assertTrue(distanceKm(b.place.at, b.table.point) > line.innerKm(b.table), "$where: inside its own inner line")
                if (units.any { distanceKm(b.place.at, it.point) <= line.innerKm(it) }) {
                    assertTrue("bearing" in b.place.name, "$where: an app city inside an inner line")
                    insideAnother.merge(line.entry, 1, Int::plus)
                }
                val r = Registry.resolveEntry(entry, Place(b.place.at.lat, b.place.at.lon, line.zone, line.cc))
                assertEquals(null, r.unitId, where)
                assertTrue(r.method!!.alongside.any { it.point == b.table.point }, "$where: the edge does not read the table")
                assertTrue(distanceKm(b.place.at, b.table.point) <= world.taqwa.app.prayer.engine.registry.edgeRideKm(b.table), where)
            }
            val lines = written.getValue("${line.group.removeSuffix(".tsv")}-envelope.tsv").text.lines().filter { ("(ruling R260)" in it || "(ruling R262)" in it) && !it.startsWith("#") }
            assertTrue(lines.size >= rings.size && lines.all { it.split('\t')[1] == line.entry }, "${line.entry}: written under the bare entry")
        }
        // Egypt and Oman have ground past a town's or locality's own line inside a farther one's; Kyrgyzstan cannot (every
        // reach is the same minute of longitude).
        assertTrue((insideAnother[EnvelopeRows.EGYPT] ?: 0) > 0 && (insideAnother[EnvelopeRows.OMAN] ?: 0) > 0, "$insideAnother")
        // Egypt's seven app cities past every belt, each against its nearest town at least (an-Najaylah against both).
        val egypt = EnvelopeRows.edgeRings(EnvelopeRows.EDGE_LINES.single { it.entry == EnvelopeRows.EGYPT }, cities)
        val read = egypt.filter { it.place.name.startsWith("the app's ") }.groupBy({ it.place.name.removePrefix("the app's ") }, { it.table.id })
        assertEquals(7, read.size, "$read")
        assertEquals(setOf("marsa-matruh", "sidi-barrani"), read.getValue("An-Najaylah").toSet())
    }
}
