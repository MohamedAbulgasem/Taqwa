package world.taqwa.app.prayer.engine.registry.data

import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 11's generated proof-stamp table ([ProofStamps]), checked against one entry's committed
 * stamp (`tools/timetables/official/stamps/sa.ummalqura.json`) so a bad regeneration — a field
 * dropped, a unit mis-parsed — fails here rather than only showing up as a wrong number on the
 * About screen.
 */
class ProofStampsTest {

    @Test
    fun `sa ummalqura carries the committed stamp's headline numbers`() {
        val stamp = ProofStamps.of("sa.ummalqura")
        assertTrue(stamp != null, "sa.ummalqura should have a generated row")
        assertEquals("sa.ummalqura", stamp.entryId)
        // KACST's 173 places, the app's own points for 94 Saudi cities (the city-points round of 9 Oct 2026) and
        // each place's own tables across its reach (sa-ummalqura-reach.tsv, the review of 9 Oct 2026).
        assertEquals(2313, stamp.places)
        // At least the place-days of 9 October 2026: the weekly monitor's prove adds held-out rows as new captures
        // arrive (docs/MONITOR.md, "Prove"), so the proof only grows; a regeneration that drops rows shows here.
        assertTrue(stamp.placeDays >= 1778731, "sa.ummalqura placeDays ${stamp.placeDays}")
        assertEquals("2030-12-31", stamp.provenThrough)
        assertEquals(2, stamp.worstLateMinutes["dhuhr"])
        // One row per KACST place, each its own: Makkah's Fajr is not Tayma's (a table computed a degree north of it).
        assertEquals(173, stamp.worstLateByUnit.size)
        assertEquals(2, stamp.worstLateByUnit.getValue("makkah")["fajr"])
        assertEquals(5, stamp.worstLateByUnit.getValue("tayma")["fajr"])
        assertEquals(stamp.worstLateMinutes["fajr"], stamp.worstLateByUnit.values.mapNotNull { it["fajr"] }.max())
        val lagLimit = stamp.lateLimits.first { "fajr" in it.events && it.reason?.contains("repeats the previous day") == true }
        assertEquals(2, lagLimit.minutes)
        assertEquals(listOf("fajr"), lagLimit.events)
        val ends = stamp.lateLimits.first { "endOfEating" in it.events }
        assertEquals(3, ends.minutes)
        assertTrue("sunrise" in ends.events)
    }

    @Test
    fun `each unit carries its own worst per event and the class limit is listed without a reason`() {
        // ru.dumrt is gated at 19 towns, one unit each: the entry-wide worst is the worst of theirs,
        // and one town's white-night end of sahur is not another's.
        val stamp = ProofStamps.of("ru.dumrt")
        assertTrue(stamp != null)
        assertEquals(19, stamp.worstLateByUnit.size)
        for ((event, worst) in stamp.worstLateMinutes) {
            assertEquals(worst, stamp.worstLateByUnit.values.mapNotNull { it[event] }.max(), event)
        }
        assertTrue(stamp.worstLateByUnit.getValue("kazan").getValue("endOfEating") < stamp.worstLateMinutes.getValue("endOfEating"))
        // Review M7: the class's own limit (B, 2 min) is a row with a null reason.
        assertTrue(stamp.lateLimits.any { it.reason == null && it.minutes == 2 })
        // An entry gated without units has none.
        assertTrue(ProofStamps.of("sg.muis")!!.worstLateByUnit.isEmpty())
    }

    @Test
    fun `every measured unit of an entry checked by unit has a row of its own`() {
        // About's checked template falls back to the class's minutes where a measured unit has no row
        // (checkedAtMostMinutes): a figure the gate never measured there. The city-points round's 265 units
        // (Umm al-Qura's and QMDB's) each have one.
        val missing = mutableListOf<String>()
        for (entry in Registry.entries) {
            val units = Units.of(entry.id) ?: continue
            val stamp = ProofStamps.of(entry.id) ?: continue
            if (stamp.worstLateByUnit.isEmpty()) continue
            for (unit in units.units) if (unit.measured && unit.id !in stamp.worstLateByUnit) missing += "${entry.id}/${unit.id}"
        }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun `an entry with no committed stamp has no row`() {
        assertNull(ProofStamps.of("no.such.entry"))
    }

    @Test
    fun `every row's places and place-days are at least one`() {
        for (stamp in ProofStamps.byEntry.values) {
            assertTrue(stamp.places > 0, "${stamp.entryId}: places")
            assertTrue(stamp.placeDays > 0, "${stamp.entryId}: placeDays")
        }
    }
}
