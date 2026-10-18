package world.taqwa.timetables.units

import world.taqwa.app.prayer.engine.registry.authorities.Americas
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.GateManifest
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The envelope's promise-only rows hold every place the engine rides a table at (the Q4 review of 17 Oct 2026: the
 * generator had copied class D's reach for Diyanet's Sydney table, so a ride widened in the engine would have left the
 * new area ungated, and nothing would have said so).
 * - Every place (entry and point) the generator would write now is a place of the committed files. A year `prove` adds
 *   to a table's rows adds rows at the same places, so it does not fail this; a ride changed in the engine with the files
 *   not regenerated does.
 * - Diyanet's Sydney table, its preset chosen: read from the app's own resolution, the Australian cities where the chosen
 *   preset rides one of the table's plausible points are exactly the places its rows are read at. Wollongong (59 km from
 *   the nearest point) is one; Newcastle (117 km) and Canberra (about 250 km) are not. Riding Sydney's table there would be
 *   a decision of its own (the owner's question 1 met question 4 here), so this fails until it is taken on purpose.
 */
class EnvelopeCoverageTest {
    private val gate = TestPaths.repoRoot.resolve("tools/timetables/official/gate")
    private val cities = EnvelopeRows.cities(TestPaths.appFiles.resolve("cities.csv"))

    private fun place(entry: String, lat: Double?, lon: Double?) = String.format(Locale.ROOT, "%s@%.6f,%.6f", entry, lat, lon)

    /** The places of a gate file's rows: entry and point, to the micro-degree. */
    private fun places(name: String, text: String): Set<String> =
        GateManifest.parse(name, text).rows.map { place(it.entry, it.lat, it.lon) }.toSet()

    @Test
    fun `every place the envelope rows would be written at is held by the committed files`() {
        val written = EnvelopeRows.render(gate, cities)
        assertTrue(written.isNotEmpty())
        for ((name, file) in written) {
            val committed = gate.resolve(name)
            assertTrue(committed.isFile, "$name is not committed: run generateEnvelopeRows")
            val missing = places(name, file.text) - places(name, committed.readText())
            assertTrue(missing.isEmpty(), "$name misses ${missing.size} places the engine rides now, ${missing.take(5)}: run generateEnvelopeRows")
        }
    }

    @Test
    fun `diyanet's sydney table is read wherever its preset chosen rides the table and nowhere else`() {
        val points = Americas.sydneyDiyanetPoints.map { it.point }
        val ridden = cities.filter { it.cc == "AU" && EnvelopeRows.ridesAny("other.turkey", it, points) }
        val read = places("au-diyanet.tsv", gate.resolve("au-diyanet.tsv").readText()) +
            places("au-diyanet-envelope.tsv", gate.resolve("au-diyanet-envelope.tsv").readText())
        val key = { c: EnvelopeRows.City -> place("other.turkey", c.at.lat, c.at.lon) }
        assertEquals(emptyList(), ridden.filter { key(it) !in read }.map { it.name }, "ridden but not read: run generateEnvelopeRows")
        val au = cities.filter { it.cc == "AU" }
        val readCities = au.filter { key(it) in read }.map { it.name }.toSet()
        assertEquals(ridden.map { it.name }.toSet(), readCities, "read where the preset does not ride the table")
        assertTrue("Wollongong" in readCities && "Sydney" in readCities, "$readCities")
        for (beyond in listOf("Newcastle", "Canberra", "Melbourne")) {
            assertTrue(beyond !in readCities, "$beyond now rides Diyanet's Sydney table: decide it on purpose (Q1 and Q4), then change this test")
        }
    }
}
