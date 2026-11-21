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
        // KACST's places, each table counted once where it is printed for (ruling R214, 13 Nov 2026): the app's own
        // points for the Saudi cities and each place's tables replayed across its reach (sa-ummalqura-reach.tsv) are
        // checks, and no place or place-day of the figure shown (they made it 3,242 places before). The rule, not the
        // day's figure (the review of 14 Nov 2026): a place is one that has its own row of worsts, and the places only
        // grow from the 173 of 13 November 2026 as the monitor holds more of KACST's.
        assertEquals(stamp.worstLateByUnit.size, stamp.places, "sa.ummalqura counts a place that has no table of its own")
        assertTrue(stamp.places >= 173, "sa.ummalqura places ${stamp.places}")
        // At least the tables' own place-days of 13 November 2026: the weekly monitor's prove adds held-out rows as new
        // captures arrive (docs/MONITOR.md, "Prove"), so the proof only grows; a regeneration that drops rows shows here.
        assertTrue(stamp.placeDays >= 132866, "sa.ummalqura placeDays ${stamp.placeDays}")
        assertTrue(stamp.lastChecked!! >= "2030-12-31", "sa.ummalqura lastChecked ${stamp.lastChecked}")
        // Dhuhr's worst, 2 on 9 October 2026 (the reach's minute, ruling R40), may grow to Tayma's own limit of 3 as prove
        // holds Tayma's next tables, never past it; a regeneration that loses it shows here.
        assertTrue(stamp.worstLateMinutes.getValue("dhuhr") in 2..3, "sa.ummalqura Dhuhr ${stamp.worstLateMinutes["dhuhr"]}")
        assertEquals(stamp.worstLateMinutes["dhuhr"], stamp.worstLateByUnit.values.mapNotNull { it["dhuhr"] }.max())
        // One row per KACST place, each its own: Makkah's Fajr is not Tayma's (a table computed a degree north of the
        // town, read across the town's disc as well since ruling R300, where the user's own dawn comes later than it).
        assertEquals(2, stamp.worstLateByUnit.getValue("makkah")["fajr"])
        assertTrue(stamp.worstLateByUnit.getValue("tayma").getValue("fajr") > stamp.worstLateByUnit.getValue("makkah").getValue("fajr"))
        assertEquals(stamp.worstLateMinutes["fajr"], stamp.worstLateByUnit.values.mapNotNull { it["fajr"] }.max())
        val lagLimit = stamp.lateLimits.first { "fajr" in it.events && it.reason?.contains("repeats the previous day") == true }
        assertEquals(2, lagLimit.minutes)
        assertEquals(listOf("fajr"), lagLimit.events)
        // The entry's own ends limit (the lag dates' earlier sunrise and end of eating), not a unit's (Tayma's own 3, whose
        // sunrise a measured reach limit overrides since ruling R300, now lists the end of eating without it).
        val ends = stamp.lateLimits.first { "endOfEating" in it.events && it.reason?.contains("on the lag dates") == true }
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
        // Since ruling R270 every town's reach meets a white night, so the towns' figures stand close; still each is its own.
        assertTrue(stamp.worstLateByUnit.values.any { it.getValue("endOfEating") < stamp.worstLateMinutes.getValue("endOfEating") })
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
