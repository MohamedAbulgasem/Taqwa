package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Jakim
import world.taqwa.timetables.TestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ruling R276 (the second audit's G7): where some of a checked unit's tables were read from a republisher's copy, the
 * rest from the authority's own pages, the unit says so, and only such a unit. Read off the gate files' own paths and
 * notes, so a zone whose mirror tables give way to e-solat's own in the gate stops saying it as soon as a test asks.
 */
class RepublisherCopyTest {
    private val gate = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))

    private fun mirrored(row: GateRow) = "mirror" in row.path.lowercase() || "mirror" in row.note.lowercase()

    @Test
    fun `a jakim zone says partly in the mirror's copy exactly where a gate row of its own is the mirror's`() {
        val zoneRows = gate.rows.filter { it.source in setOf("my-jakim.tsv", "my-jakim-cities.tsv") && it.unit != null }
        assertTrue(zoneRows.isNotEmpty())
        val fromMirror = zoneRows.filter(::mirrored).mapNotNull { it.unit }.toSet()
        assertEquals(fromMirror, Jakim.partlyMirrored)
        for (unit in Units.of("my.jakim")!!.units) {
            assertEquals(unit.id in fromMirror, unit.partlyRepublished, unit.id)
        }
        // And the entry names the copy, for the sentences of its whole days (About's tiles, "so far", the checks page).
        assertEquals("waktusolat.app", Registry.byId("my.jakim")?.partlyRepublisher)
        assertNull(Registry.byId("my.jakim")?.republisher)
    }

    @Test
    fun `a jakim zone's mirrored years are the years of its mirror rows and none of its own rows`() {
        // Ruling R278: the card names the copy on a mirrored year's day and e-solat's own pages on the others, so the years
        // are read off the gate files' names ("off-jakim-WLY01-2025-mirror.txt", "pinned/my-jakim/2026-11-11/WLY01-2027.txt").
        val year = Regex("-(20\\d\\d)")
        fun yearOf(row: GateRow) = year.findAll(row.path.substringAfterLast('/')).lastOrNull()?.groupValues?.get(1)?.toInt()
        val zoneRows = gate.rows.filter { it.source in setOf("my-jakim.tsv", "my-jakim-cities.tsv") && it.unit != null }
        val fromMirror = zoneRows.filter(::mirrored).groupBy { it.unit!! }
            .mapValues { (_, rows) -> rows.map { assertNotNull(yearOf(it), it.path) }.toSet() }
        assertEquals(fromMirror, Jakim.mirroredYears)
        for (row in zoneRows.filterNot(::mirrored)) {
            val y = yearOf(row) ?: continue
            assertTrue(y !in Jakim.mirroredYears[row.unit].orEmpty(), "${row.path}: an own table in a mirrored year")
        }
    }

    @Test
    fun `the islamic foundation's dhaka table is kivabe com's copy and its ramadan pages are its own`() {
        val rows = gate.rows.filter { it.source == "bd-ifb.tsv" }
        val dhaka = rows.filter { it.unit == "dhaka" }
        assertTrue(dhaka.any { "off-ifb" in it.path }, "Dhaka's year-round table is in the gate")
        assertTrue(rows.filter { it.unit != "dhaka" }.none { "off-ifb" in it.path }, "and no other district's")
        assertEquals("kivabe.com", Registry.byId("bd.ifb")?.partlyRepublisher)
        for (unit in Units.of("bd.ifb")!!.units) assertEquals(unit.id == "dhaka", unit.partlyRepublished, unit.id)
    }
}
