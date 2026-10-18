package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.TestPaths
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
}
