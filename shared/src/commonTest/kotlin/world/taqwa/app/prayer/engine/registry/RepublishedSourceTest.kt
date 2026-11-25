package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import world.taqwa.app.feature.prayer.CheckedClaim
import world.taqwa.app.feature.prayer.WhoseTimes
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.registry.authorities.Jakim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A timetable read from a republisher's copy says whose copy (the audit of 12 Nov 2026, finding F26). Every held
 * Kemenag kab/kota table came from the open myQuran API, none from Kemenag's own pages, so "checked against its
 * published timetable" would claim more than was done; the entry names the republisher and every sentence of the
 * Prayer screen's card and of About these times that names the comparison is chosen through [CheckedClaim], which
 * gives a republished timetable the twin that says whose copy.
 */
class RepublishedSourceTest {

    @Test
    fun `Kemenag's tables are a republisher's copy and its entry names whose`() {
        assertEquals("myQuran", assertNotNull(Registry.byId("id.kemenag")).republisher)
    }

    @Test
    fun `a republisher is named only where a checked timetable is claimed`() {
        for (entry in Registry.entries) {
            val republisher = entry.republisher ?: continue
            assertTrue(entry.entryClass == EntryClass.A || entry.entryClass == EntryClass.B, "${entry.id}: $republisher")
        }
    }

    @Test
    fun `every sentence that names the comparison has a twin that says whose copy and a republisher always gets it`() {
        for (claim in CheckedClaim.entries) {
            assertNotEquals(claim.own, claim.copy, claim.name)
            assertEquals(claim.own, claim.sentence(null), claim.name)
            for (entry in Registry.entries) {
                val republisher = entry.republisher ?: continue
                assertEquals(claim.copy, claim.sentence(republisher), "${claim.name} for ${entry.id}")
            }
        }
        // Each twin is its own sentence: no two claims share one.
        assertEquals(CheckedClaim.entries.size * 3, CheckedClaim.entries.flatMap { listOf(it.own, it.copy, it.part) }.toSet().size)
    }

    @Test
    fun `a timetable only some of whose tables are a copy takes the part twin and names whose`() {
        // Ruling R276 (the second audit's G7): JAKIM's mirrored years at Kuala Lumpur, its own pages at Ipoh.
        for (claim in CheckedClaim.entries) {
            assertEquals(claim.part, claim.sentence(null, "waktusolat.app"), claim.name)
            assertEquals(claim.copy, claim.sentence("myQuran", null), claim.name)
        }
        assertEquals("waktusolat.app", CheckedClaim.copyName(null, "waktusolat.app"))
        assertNull(CheckedClaim.copyName(null, null))
        val date = LocalDate(2026, 11, 13)
        val kualaLumpur = PrayerEngine.dayTimes(Place(3.139, 101.6869, "Asia/Kuala_Lumpur", "MY"), date, EngineSettings())
        assertEquals("WLY01", kualaLumpur.effective.unitId)
        assertEquals("waktusolat.app", kualaLumpur.effective.partlyRepublisher)
        val ipoh = PrayerEngine.dayTimes(Place(4.5975, 101.0901, "Asia/Kuala_Lumpur", "MY"), date, EngineSettings())
        assertEquals("my.jakim", ipoh.effectiveEntry.id)
        assertNull(ipoh.effective.partlyRepublisher, "${ipoh.effective.unitId}: e-solat's own tables only")
        assertEquals("waktusolat.app", ipoh.effectiveEntry.partlyRepublisher, "the entry's whole days still name it")
    }

    @Test
    fun `the card names the copy on a day whose table was only the copy and the own pages on a day whose was its own`() {
        // Ruling R278 (the review of the second audit's G7): Kuala Lumpur's 2026 table is e-solat's own, its 2025 and 2027
        // the mirror's; the card speaks of one day, so it names the one that day was compared with.
        val kualaLumpur = Place(3.139, 101.6869, "Asia/Kuala_Lumpur", "MY")
        val own = WhoseTimes.of(PrayerEngine.dayTimes(kualaLumpur, LocalDate(2026, 11, 13), EngineSettings()))
        assertNull(own.republisher)
        assertNull(own.partlyRepublisher)
        val mirrored = WhoseTimes.of(PrayerEngine.dayTimes(kualaLumpur, LocalDate(2027, 3, 1), EngineSettings()))
        assertEquals(EntryClass.B, mirrored.entryClass)
        assertEquals("waktusolat.app", mirrored.republisher)
        assertNull(mirrored.partlyRepublisher)
        // Labuan's 2025 alone is the mirror's: its 2026 days are e-solat's own.
        val labuan = WhoseTimes.of(PrayerEngine.dayTimes(Place(5.2767, 115.2417, "Asia/Kuching", "MY"), LocalDate(2026, 11, 13), EngineSettings()))
        assertNull(labuan.republisher)
        assertNull(labuan.partlyRepublisher)
        // Where the copy's years are not known apart (Dhaka's year-round table beside the Foundation's Ramadan pages),
        // every day says "partly".
        val dhaka = PrayerEngine.dayTimes(Place(23.8103, 90.4125, "Asia/Dhaka", "BD"), LocalDate(2026, 11, 13), EngineSettings())
        assertEquals("dhaka", dhaka.effective.unitId)
        assertEquals("kivabe.com", WhoseTimes.of(dhaka).partlyRepublisher)
        assertNull(WhoseTimes.of(dhaka).republisher)
        for ((zone, years) in Jakim.mirroredYears) {
            val unit = assertNotNull(Units.of("my.jakim")).units.single { it.id == zone }
            assertEquals(years, unit.republishedYears, zone)
        }
    }

    @Test
    fun `a whole copy and a part copy are never claimed of the same timetable`() {
        for (entry in Registry.entries) assertTrue(entry.republisher == null || entry.partlyRepublisher == null, entry.id)
    }

    @Test
    fun `beyond its checked places a republished timetable still carries the republisher`() {
        // Open sea south of Java, outside every kab/kota circle: Kemenag's method at the edge, not a checked table,
        // and what About says of the days compared so far is still said of myQuran's copy.
        val edge = Place(-9.5, 108.0, "Asia/Jakarta", "ID")
        val day = PrayerEngine.dayTimes(edge, LocalDate(2026, 11, 13), EngineSettings())
        assertEquals(EntryClass.D_AUTHORITY, day.effective.entryClass)
        assertEquals("myQuran", day.effective.entry.republisher)
    }

    @Test
    fun `the Prayer screen's card carries the republisher of the timetable the day follows`() {
        val jakarta = Place(-6.21462, 106.84513, "Asia/Jakarta", "ID")
        val day = PrayerEngine.dayTimes(jakarta, LocalDate(2026, 11, 13), EngineSettings())
        assertEquals("id.kemenag", day.effectiveEntry.id)
        assertEquals(WhoseTimes("authority_kemenag", EntryClass.B, "myQuran"), WhoseTimes.of(day))
        val istanbul = Place(41.01384, 28.94966, "Europe/Istanbul", "TR", "Istanbul")
        assertNull(WhoseTimes.of(PrayerEngine.dayTimes(istanbul, LocalDate(2026, 11, 13), EngineSettings())).republisher)
    }
}
