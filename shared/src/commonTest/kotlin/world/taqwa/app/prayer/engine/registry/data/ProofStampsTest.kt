package world.taqwa.app.prayer.engine.registry.data

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
        assertEquals(12, stamp.places)
        assertEquals(13146, stamp.placeDays)
        assertEquals("2030-12-31", stamp.provenThrough)
        assertEquals(1, stamp.worstLateMinutes["dhuhr"])
        assertEquals(2, stamp.worstLateMinutes["fajr"])
        assertTrue(stamp.lateLimits.isNotEmpty(), "sa.ummalqura has a recorded late-limit exception (the lag dates)")
        val lagLimit = stamp.lateLimits.first { "fajr" in it.events }
        assertEquals(2, lagLimit.minutes)
        assertTrue("endOfEating" in lagLimit.events)
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
        assertTrue(ProofStamps.of("sa.ummalqura")!!.worstLateByUnit.isEmpty())
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
