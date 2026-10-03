package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 7e's proof for the Levant, Iraq and Yemen (docs/research/2026-09-prayer-times/proof/7e-levant.md).
 * No printed time from any of these authorities' tables may be committed here (none is openly
 * licensed, and none is already public in the spec, unlike Diyanet İstanbul): every number the gate
 * proved lives instead in the git-ignored stamps and in proof.md. What this test can and does check,
 * without repeating a single official minute, is that the registry carries the class the gate found
 * for each entry, and that each method's own day is never out of order at the point the gate proved
 * it at.
 */
class LevantProofTest {

    private fun day(id: String, lat: Double, lon: Double, zone: String, date: LocalDate) =
        DayComputer.compute(
            requireNotNull(Registry.byId(id)?.method) { "$id has no single method" },
            GeoPoint(lat, lon), date, TimeZone.of(zone), AsrSchool.STANDARD, Registry.ramadanCalendar(),
        )

    /** No prayer's time is out of its natural order (a necessary condition for never-early). */
    private fun assertOrdered(label: String, d: world.taqwa.app.prayer.engine.day.PrayerDay) {
        assertTrue(d.fajr < d.sunrise, "$label: fajr before sunrise")
        assertTrue(d.sunrise < d.dhuhr, "$label: sunrise before dhuhr")
        assertTrue(d.dhuhr < d.asr, "$label: dhuhr before asr")
        assertTrue(d.asr < d.maghrib, "$label: asr before maghrib")
        assertTrue(d.maghrib < d.isha, "$label: maghrib before isha")
    }

    @Test
    fun `jordan is class A at amman and no stronger than B or D authority anywhere else`() {
        val entry = requireNotNull(Registry.byId("jo.awqaf"))
        // The entry's own class is B ("an unverified unit"): every one of its 80 fitted dates is
        // Amman's own (the calculator's <option selected> names it on every capture), so nothing
        // beyond Amman is measured.
        assertEquals(EntryClass.B, entry.entryClass)
        assertTrue(!entry.measured)
        val amman = Registry.resolve(Place(31.955, 35.945, "Asia/Amman", "JO"))
        assertEquals("jo.awqaf", amman.entry.id)
        assertEquals("Amman", amman.unitName)
        assertEquals(EntryClass.A, amman.entryClass)
        assertTrue(amman.measured)
        // Aqaba, far enough from Amman to fall outside its reach: no unit, so an A/B entry reads
        // D_AUTHORITY there (spec §6.2 a), never the entry's own claimed class.
        val aqaba = Registry.resolve(Place(29.5267, 35.0078, "Asia/Amman", "JO"))
        assertEquals("jo.awqaf", aqaba.entry.id)
        assertEquals(null, aqaba.unitName)
        assertEquals(EntryClass.D_AUTHORITY, aqaba.entryClass)
        assertTrue(!aqaba.measured)
        assertOrdered("jo.awqaf@amman", day("jo.awqaf", 31.955, 35.945, "Asia/Amman", LocalDate(2026, 6, 21)))
        assertOrdered("jo.awqaf@amman", day("jo.awqaf", 31.955, 35.945, "Asia/Amman", LocalDate(2026, 12, 21)))
    }

    @Test
    fun `the pa's al-aqsa table is class B over its own fitted point`() {
        val entry = requireNotNull(Registry.byId("ps.iftaa"))
        assertEquals(EntryClass.B, entry.entryClass)
        assertTrue(entry.measured)
        assertOrdered("ps.iftaa", day("ps.iftaa", 31.7767, 35.2345, "Asia/Hebron", LocalDate(2026, 6, 21)))
    }

    @Test
    fun `gaza's cautious entry ranks its ministry of awqaf over the pa`() {
        val gaza = requireNotNull(Registry.byId("ps.gaza.cautious"))
        assertEquals(EntryClass.C, gaza.entryClass)
        assertEquals(listOf("ps.gaza.awqaf", "ps.iftaa"), gaza.members.sortedBy { it.shareRank }.map { it.id })
    }

    @Test
    fun `lebanon syria and iraq stay class D authority with jordan and palestine's margins never applied to them`() {
        // Every D_AUTHORITY entry here is checked on a thin sample, not a whole area's tables: measured
        // is false throughout, consistently (none of them claims an "at most" figure).
        for (id in listOf("lb.fatwa", "sy.awqaf", "iq.sunni", "ps.gaza.awqaf")) {
            val entry = requireNotNull(Registry.byId(id)) { id }
            assertEquals(EntryClass.D_AUTHORITY, entry.entryClass, id)
            assertTrue(!entry.measured, id)
        }
        assertOrdered("lb.fatwa", day("lb.fatwa", 33.894, 35.502, "Asia/Beirut", LocalDate(2026, 3, 20)))
        assertOrdered("sy.awqaf", day("sy.awqaf", 33.5138, 36.2765, "Asia/Damascus", LocalDate(2026, 9, 26)))
        assertOrdered("iq.sunni", day("iq.sunni", 33.341, 44.401, "Asia/Baghdad", LocalDate(2026, 9, 26)))
    }

    @Test
    fun `lebanon's late-limit exceptions cover exactly fajr and the two evening events and the end of eating`() {
        val entry = requireNotNull(Registry.byId("lb.fatwa"))
        val covered = entry.lateLimits.flatMap { it.events }.toSet()
        assertEquals(setOf(TimedEvent.FAJR, TimedEvent.MAGHRIB, TimedEvent.ISHA, TimedEvent.END_OF_EATING), covered)
        for (limit in entry.lateLimits) assertTrue(limit.reason.isNotBlank(), limit.events.toString())
    }

    @Test
    fun `yemen has no authority proven and stays calculated`() {
        val entry = requireNotNull(Registry.byId("ye.default"))
        assertEquals(EntryClass.D_NONE, entry.entryClass)
        assertEquals(AboutTemplate.CALCULATED, entry.about)
    }
}
