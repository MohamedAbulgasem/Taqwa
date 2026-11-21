package world.taqwa.app.prayer.engine.registry

import world.taqwa.app.prayer.engine.registry.ownLateLimitFor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.ImsakFrom
import world.taqwa.app.prayer.engine.registry.authorities.Jakim
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * JAKIM's imsak follows e-solat's own rule, its printed Subuh less 10 (the JAKIM imsak round of 12 Nov 2026): counted
 * back from the zone's Subuh as printed for its latest point ([ImsakFrom.PrintedFajr]), not from the reference point's
 * dawn the end of eating keeps, which put it 3–4 min before e-solat's at Kuching on every day of Ramadan 1448.
 */
class JakimImsakTest {
    private val kuchingZone = TimeZone.of("Asia/Kuching")
    private val kuching = Place(1.55, 110.33333, "Asia/Kuching", "MY")

    @Test
    fun `every fitted zone but perlis counts its imsak back from its printed subuh`() {
        for (unit in Units.of("my.jakim")!!.units) {
            val method = assertNotNull(unit.method, unit.id)
            if (unit.id == "PLS01") {
                // Perlis prints no precaution: no imsak, its end of eating alone.
                assertNull(method.imsakMinutesBeforeFajr, unit.id)
                assertEquals(ImsakFrom.Dawn, method.imsakFrom, unit.id)
            } else {
                assertEquals(10, method.imsakMinutesBeforeFajr, unit.id)
                val from = assertIs<ImsakFrom.PrintedFajr>(method.imsakFrom, unit.id)
                // Fitted as an end: never later than the Fajr's own start margin would put it.
                assertTrue(from.marginSeconds < method.margins.fajr, "${unit.id}: ${from.marginSeconds} against ${method.margins.fajr}")
            }
        }
    }

    @Test
    fun `kuala lumpur's imsak exception is its fajr's own three minutes and no zone's imsak carries one it does not record`() {
        // Ruling R187 (the owner, 12 Nov 2026): the 2027 Subuh prints earlier than 2025's and 2026's at the same point,
        // so the imsak, never after any year's, comes up to 3 min before the earlier years'. One zone, one cause.
        for (unit in Units.of("my.jakim")!!.units) {
            // The recorded exceptions; a reach's measured figure (ruling R270, ReachLimits) is not a cause recorded here.
            val imsak = ownLateLimitFor(TimedEvent.IMSAK, unit, Jakim.entry)
            if (unit.id == "WLY01") {
                val fajr = assertNotNull(ownLateLimitFor(TimedEvent.FAJR, unit, Jakim.entry), unit.id)
                assertEquals(fajr.minutes, assertNotNull(imsak, unit.id).minutes, unit.id)
                assertEquals(setOf(TimedEvent.IMSAK), imsak.events, unit.id)
            } else {
                // The zone round (ruling R227): a zone's imsak carries a limit only where the zone records one with
                // its cause (Kedah's stepped tables, SBH09's later-printed days, a broken run), else the class limit.
                val recorded = JakimZones.zones.first { it.id == unit.id }.late.firstOrNull { TimedEvent.IMSAK in it.events }
                assertEquals(recorded?.minutes, imsak?.minutes, "${unit.id}: its imsak keeps its zone's recorded limit, else the class limit")
            }
        }
    }

    @Test
    fun `beyond the fitted zones the imsak is counted back from the dawn`() {
        assertEquals(ImsakFrom.Dawn, Jakim.peninsularEdge.imsakFrom)
        assertEquals(ImsakFrom.Dawn, Jakim.borneoEdge.imsakFrom)
        // Selangau, Sarawak: on no zone's list and beyond every listed town's reach (JakimZoneCities.unattached).
        val selangau = Registry.resolve(Place(2.52477, 112.32543, "Asia/Kuching", "MY"))
        assertNull(selangau.unitId)
        assertEquals(ImsakFrom.Dawn, assertNotNull(selangau.method).imsakFrom)
    }

    @Test
    fun `kuching's ramadan 1448 imsak is counted from the printed subuh and its end of eating from the dawn`() {
        // e-solat's printed imsak and Subuh are held against these days by the gate (my-jakim.tsv's SWK08 imsak and
        // Subuh rows), never here (ruling R69: no printed time in the tree). What this test holds is the rule: the
        // imsak counted back from the zone's printed Subuh comes after the one the reference point's dawn would give,
        // which stood 3 min or more before e-solat's, and still before the end of eating, which stays the dawn's.
        for (date in listOf(LocalDate(2027, 2, 7), LocalDate(2027, 2, 20), LocalDate(2027, 3, 10))) {
            val day = DayPipeline.day(Jakim.entry, kuching, date)
            val shown = assertNotNull(day.imsak, "$date")
            assertTrue(shown < day.endOfEating, "$date: the imsak $shown is a precaution before the end of eating")
            assertTrue(shown - (day.endOfEating - 10.minutes) >= 1.minutes, "$date: $shown is the dawn's count, not the printed Subuh's")
        }
    }

    @Test
    fun `a place west of kuching's reference point keeps the zone's own imsak`() {
        val date = LocalDate(2027, 2, 20)
        val atPoint = DayPipeline.day(Jakim.entry, kuching, date)
        // 40 km west, inside SWK08's 45 km: its own Fajr is later, but the imsak is e-solat's for the zone.
        val west = Place(1.55, 109.97, "Asia/Kuching", "MY")
        assertEquals("SWK08", Registry.resolve(west).unitName)
        val there = DayPipeline.day(Jakim.entry, west, date)
        assertTrue(there.fajr > atPoint.fajr, "the place's own later Fajr is the start shown")
        assertEquals(assertNotNull(atPoint.imsak), there.imsak, "the zone's own imsak, e-solat's")
        // The table's point riding beside the user's (DayComputer's point 7) does the work: without it the imsak
        // counted back from the place's own later Subuh would be after the zone's, and so after e-solat's.
        val resolved = assertNotNull(Registry.resolve(west).method)
        assertNotNull(resolved.fixedPoint, "SWK08's table point rides beside the user's")
        fun computed(method: TimetableMethod) = DayComputer.compute(
            method, GeoPoint(west.lat, west.lon), date, kuchingZone, Jakim.entry.school,
            Registry.ramadanCalendarFor(Jakim.entry), Registry.lagDates,
        )
        val bound = assertNotNull(computed(resolved).imsak)
        val alone = assertNotNull(computed(resolved.copy(fixedPoint = null)).imsak)
        assertEquals(assertNotNull(atPoint.imsak), bound, "the bound imsak is the zone's")
        assertTrue(alone > bound, "$alone against $bound")
    }
}
