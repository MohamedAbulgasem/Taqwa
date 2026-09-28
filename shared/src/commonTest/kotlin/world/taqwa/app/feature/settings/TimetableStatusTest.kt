package world.taqwa.app.feature.settings

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimetableStatusTest {

    private val date = LocalDate(2026, 10, 1)
    private val london = Place(51.5074, -0.1278, "Europe/London", "GB")
    private val riyadh = Place(24.68773, 46.72185, "Asia/Riyadh", "SA")
    private val toronto = Place(43.70643, -79.39864, "America/Toronto", "CA")
    private val jakarta = Place(-6.21462, 106.84513, "Asia/Jakarta", "ID")

    private fun status(place: Place, settings: PrayerSettings = PrayerSettings()): TimetableStatus =
        TimetableStatus.of(settings, PrayerEngine.dayTimes(place, date, PrayerTimesEngine.engineSettingsOf(settings)))

    @Test
    fun automaticNamesWhatItFollowsHere() {
        val s = status(london)
        assertEquals(TimetableName("authority_london_unified", SourceKind.AUTHORITY), s.automatic)
        assertEquals(AUTOMATIC_TIMETABLE, s.selectedId)
        assertNull(s.chosenId)
        assertFalse(s.outOfScope)
        assertFalse(s.paused)
        assertFalse(s.highLatitude)
    }

    @Test
    fun aTimetableThatDoesNotApplyHereLeavesAutomaticWithItsLine() {
        val s = status(london, PrayerSettings(timetable = "sa.ummalqura", timetableConfirmed = true))
        assertTrue(s.outOfScope)
        assertEquals(AUTOMATIC_TIMETABLE, s.selectedId)
        assertEquals("authority_umm_al_qura", s.chosenNameKey)
        assertEquals("gb.london.lupt", s.effectiveId)
    }

    @Test
    fun anUnknownTimetableIsOutOfScopeToo() {
        val s = status(london, PrayerSettings(timetable = "xx.gone"))
        assertTrue(s.outOfScope)
        assertNull(s.chosenNameKey)
    }

    @Test
    fun anUnconfirmedTimetableIsPausedUntilConfirmed() {
        val paused = status(london, PrayerSettings(timetable = "other.mwl"))
        assertTrue(paused.paused)
        assertEquals("other.mwl", paused.selectedId)
        assertTrue(paused.highLatitude, "an Other method has the high-latitude row")
        val confirmed = status(london, PrayerSettings(timetable = "other.mwl", timetableConfirmed = true))
        assertFalse(confirmed.paused)
        assertEquals(TimetableName("method_muslim_world_league", SourceKind.AUTHORITY), confirmed.effective)
    }

    @Test
    fun theSaudiRowIsForUmmAlQuraInSaudiArabiaOnly() {
        assertTrue(status(riyadh).saudiFajr)
        assertFalse(status(riyadh, PrayerSettings(timetable = "other.ummalqura", timetableConfirmed = true)).saudiFajr)
        assertFalse(status(london).saudiFajr)
    }

    @Test
    fun whereTimetablesDifferIsForCautiousTimesOnly() {
        val toronto = status(toronto)
        assertTrue(toronto.whereDiffer)
        assertEquals(SourceKind.CAUTIOUS, toronto.automatic.source)
        assertFalse(status(london).whereDiffer)
    }

    @Test
    fun theAsrNoteKnowsWhetherTheMajoritySchoolIsKnown() {
        val london = status(london)
        assertEquals(AsrSchool.HANAFI, london.school)
        assertFalse(london.schoolKnown)
        val riyadh = status(riyadh)
        assertEquals(AsrSchool.STANDARD, riyadh.school)
        assertTrue(riyadh.schoolKnown)
    }

    @Test
    fun aStoredTimetableThatAppliesHereHasItsOwnRowAndIsSelected() {
        // Review M8: Wifaqul is a British timetable, in scope in London by its country, but London
        // Unified lists nothing nearby; chosen, it still has a row, and that row is the selected one.
        val automatic = Registry.automaticEntry(london)
        assertEquals(emptyList(), nearbyEntries(automatic, london).map { it.id })
        assertTrue(Registry.inScope(Registry.byId("gb.wifaqul")!!, london))
        val rows = timetableRowIds(automatic, london, "gb.wifaqul")
        assertEquals(listOf("gb.wifaqul"), rows)
        val chosen = PrayerSettings(timetable = "gb.wifaqul", timetableConfirmed = true)
        assertEquals("gb.wifaqul", status(london, chosen).selectedId)
        // Listed once where it is nearby already; never for Automatic, an Other method or a
        // timetable that does not apply here.
        val torontoAutomatic = Registry.automaticEntry(toronto)
        val nearby = nearbyEntries(torontoAutomatic, toronto).map { it.id }
        assertEquals(nearby, timetableRowIds(torontoAutomatic, toronto, nearby.first()))
        assertEquals(emptyList(), timetableRowIds(automatic, london, AUTOMATIC_TIMETABLE))
        assertEquals(emptyList(), timetableRowIds(automatic, london, "other.mwl"))
        assertEquals(emptyList(), timetableRowIds(automatic, london, "tr.diyanet"))
        assertEquals(emptyList(), timetableRowIds(automatic, london, "no.such.entry"))
    }

    @Test
    fun nearbyListsOnlyTimetablesThatApplyHere() {
        val automatic = Registry.automaticEntry(jakarta)
        assertEquals(listOf("id.muhammadiyah"), nearbyEntries(automatic, jakarta).map { it.id })
        // London lists none; Toronto lists its three named members.
        assertEquals(emptyList(), nearbyEntries(Registry.automaticEntry(london), london).map { it.id })
        assertEquals(3, nearbyEntries(Registry.automaticEntry(toronto), toronto).size)
    }

    @Test
    fun aClassAMarginIsItsMinuteAndCalculatedTimesClaimNone() {
        // Umm al-Qura is class A (London Unified is class B since Task 7g).
        val makkah = PrayerEngine.dayTimes(riyadh, date, PrayerTimesEngine.engineSettingsOf(PrayerSettings())).effective
        assertEquals(EntryClass.A, makkah.entryClass)
        assertTrue(marginMinutes(makkah, Prayer.MAGHRIB)!! >= 1)
        val apia = Place(-13.83333, -171.76666, "Pacific/Apia", "WS")
        val calculated = PrayerEngine.dayTimes(apia, date, PrayerTimesEngine.engineSettingsOf(PrayerSettings())).effective
        assertEquals(EntryClass.D_NONE, calculated.entryClass)
        assertNull(marginMinutes(calculated, Prayer.MAGHRIB))
    }

    @Test
    fun londonUnifiedIsTheTimetableMostMosquesFollowAndUmmAlQuraAnAuthoritys() {
        // "London Unified, followed by most mosques here", not "the timetable it publishes" (mockup section 3).
        assertEquals(TimetableKind.MAJORITY, status(london).automaticKind)
        assertEquals(TimetableKind.AUTHORITY, status(riyadh).automaticKind)
    }

    @Test
    fun everyMajorityTimetableIsAnEntryWithAMethodOfItsOwn() {
        for (id in MAJORITY_TIMETABLES) {
            val entry = assertNotNull(Registry.byId(id), "$id is not in the registry")
            assertTrue(
                entry.method != null && entry.entryClass != EntryClass.C && entry.entryClass != EntryClass.D_NONE,
                "$id is not a single timetable any more (${entry.entryClass}): take it off MAJORITY_TIMETABLES",
            )
            assertEquals(TimetableKind.MAJORITY, TimetableKind.of(entry), id)
        }
    }

    @Test
    fun chicagoIsCautiousNotAMajority() {
        // Task 7g: its mosques differ on Fajr (15° and an 18° block), so no single timetable is "most mosques'".
        val chicago = Registry.byId("us.chicago")!!
        assertEquals(EntryClass.C, chicago.entryClass)
        assertTrue(TimetableKind.of(chicago) != TimetableKind.MAJORITY)
    }

    @Test
    fun anOtherMethodIsAMethodEvenWhereItsNameIsAMajoritys() {
        // ISNA is what most US mosques follow (us.isna), and a method anyone can choose (other.isna).
        assertEquals(TimetableKind.MAJORITY, TimetableKind.of(Registry.byId("us.isna")!!))
        assertEquals(TimetableKind.METHOD, TimetableKind.of(Registry.byId("other.isna")!!))
        assertEquals(TimetableKind.AUTHORITY, TimetableKind.of(Registry.byId("ly.awqaf")!!))
    }
}
