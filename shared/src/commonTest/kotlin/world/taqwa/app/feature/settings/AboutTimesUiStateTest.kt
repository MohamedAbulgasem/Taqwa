package world.taqwa.app.feature.settings

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.data.ProofStamp
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_not_followed_irn_tromso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Task 11's template selection (spec §2.3, §6.1's class table): [aboutTimesUiState] must read each
 * class into the right [AboutTimesUiState] branch — A and B both into [AboutTimesUiState.AuthorityChecked],
 * a cautious place into [AboutTimesUiState.Cautious] with every member's own day alongside it, an
 * authority-with-safety-minutes place into [AboutTimesUiState.AuthorityUnchecked], and a place with no
 * authority at all into [AboutTimesUiState.Calculated] — the same four mockups the previews use.
 */
class AboutTimesUiStateTest {

    private val date = LocalDate(2026, 9, 26)

    private fun stateFor(place: Place): AboutTimesUiState {
        val engineDay = PrayerEngine.dayTimes(place, date, EngineSettings())
        return aboutTimesUiState(engineDay, place, date)
    }

    @Test
    fun `class A resolves to AuthorityChecked`() {
        val istanbul = Place(41.0082, 28.9784, "Europe/Istanbul", "TR")
        val state = stateFor(istanbul)
        assertIs<AboutTimesUiState.AuthorityChecked>(state)
        assertEquals("tr.diyanet", state.resolution.entry.id)
    }

    @Test
    fun `class B resolves to AuthorityChecked`() {
        val singapore = Place(1.3521, 103.8198, "Asia/Singapore", "SG")
        val state = stateFor(singapore)
        assertIs<AboutTimesUiState.AuthorityChecked>(state)
        assertEquals("sg.muis", state.resolution.entry.id)
    }

    @Test
    fun `class D authority resolves to AuthorityUnchecked`() {
        val tripoli = Place(32.88743, 13.18733, "Africa/Tripoli", "LY")
        val state = stateFor(tripoli)
        assertIs<AboutTimesUiState.AuthorityUnchecked>(state)
        assertEquals("ly.awqaf", state.resolution.entry.id)
    }

    @Test
    fun `class D none resolves to Calculated`() {
        val zahedan = Place(29.4963, 60.8629, "Asia/Tehran", "IR", admin1 = "Sistan and Baluchestan")
        val state = stateFor(zahedan)
        assertIs<AboutTimesUiState.Calculated>(state)
        assertEquals("ir.hanafi", state.resolution.entry.id)
    }

    @Test
    fun `class C resolves to Cautious with each member's own day`() {
        val toronto = Place(43.6532, -79.3832, "America/Toronto", "CA")
        val state = stateFor(toronto)
        assertIs<AboutTimesUiState.Cautious>(state)
        assertEquals("ca.toronto", state.resolution.entry.id)
        assertEquals(state.resolution.members.size, state.members.size)
        // The most-followed member (share rank 1) is first, as ui-common.md's "Resolution.members"
        // note and the Toronto mockup both expect.
        assertEquals(1, state.resolution.members.first().shareRank)
        assertTrue(state.members.all { (member, _) -> member in state.resolution.members })
        // A cautious state carries the entry's proof stamp like the two authority states (ruling R100).
        assertEquals("ca.toronto", state.stamp?.entryId)
    }

    /**
     * Rulings R100, R105, R111 and R112 (city-pages spec §10): a measured cautious place shows two
     * proof tiles — the stamp's place-days at its places, and the 0 starts before the timetable that
     * decides each of them — and a sentence with no date; the same figures the site's cautious page
     * shows from the same stamp. Toronto's are the committed `ca.toronto` stamp's: 1,400 days at 6
     * places.
     */
    @Test
    fun `a cautious place's proof is its stamp's place-days and places`() {
        val toronto = Place(43.6532, -79.3832, "America/Toronto", "CA")
        val state = stateFor(toronto)
        assertIs<AboutTimesUiState.Cautious>(state)
        assertTrue(state.resolution.measured)
        val proof = requireNotNull(cautiousProof(state.resolution, state.stamp)) { "Toronto is measured and stamped" }
        assertEquals(CautiousProof(placeDays = 1400, places = 6), proof)
    }

    /**
     * No figure is claimed where the proof does not reach (spec §10, as the checked template does):
     * without a stamp, or at a place the members' proof does not measure — Vancouver lies beyond the
     * places `ca.cautious`'s stamp was gated at, though the stamp itself exists.
     */
    @Test
    fun `a cautious place claims no proof without a stamp or where it is not measured`() {
        val toronto = stateFor(Place(43.6532, -79.3832, "America/Toronto", "CA"))
        assertIs<AboutTimesUiState.Cautious>(toronto)
        assertNull(cautiousProof(toronto.resolution, null))

        val vancouver = stateFor(Place(49.2827, -123.1207, "America/Vancouver", "CA"))
        assertIs<AboutTimesUiState.Cautious>(vancouver)
        assertEquals("ca.cautious", vancouver.resolution.entry.id)
        assertFalse(vancouver.resolution.measured)
        assertTrue(vancouver.stamp != null, "ca.cautious carries a committed stamp")
        assertNull(cautiousProof(vancouver.resolution, vancouver.stamp))
    }

    /** Oslo is measured (the city pages publish it) and shows its `no.cautious` stamp's own figures. */
    @Test
    fun `oslo is measured and shows its stamp's figures`() {
        val oslo = stateFor(Place(59.91273, 10.74609, "Europe/Oslo", "NO"))
        assertIs<AboutTimesUiState.Cautious>(oslo)
        assertEquals("no.cautious", oslo.resolution.entry.id)
        assertTrue(oslo.resolution.measured)
        val stamp = requireNotNull(oslo.stamp)
        assertTrue(stamp.worstLateByUnit.isEmpty(), "no cautious stamp has unit rows today")
        assertEquals(CautiousProof(stamp.placeDays, stamp.places), cautiousProof(oslo.resolution, stamp))
    }

    /**
     * Review M2 (the plan's units gate): a cautious stamp with unit rows proves only the units in
     * them. A cautious resolution carries no unit today, so such a stamp shows nothing; a resolution
     * placed in one of its units shows the stamp's figures; one placed outside them, or in a unit
     * whose row lacks a start the entry is checked on elsewhere, shows nothing — as the checked
     * template's [measuredStartsWorst] decides. The rows are lateness counts only, never a time.
     */
    @Test
    fun `a cautious stamp with unit rows proves only the place's own complete unit row`() {
        val toronto = stateFor(Place(43.6532, -79.3832, "America/Toronto", "CA"))
        assertIs<AboutTimesUiState.Cautious>(toronto)
        assertNull(toronto.resolution.unitId)
        val byUnit = requireNotNull(toronto.stamp).copy(
            worstLateByUnit = mapOf(
                "toronto" to mapOf("fajr" to 6, "sunrise" to 3, "dhuhr" to 5, "asrStandard" to 6, "maghrib" to 5, "isha" to 7),
                "milton" to mapOf("sunrise" to 3, "dhuhr" to 5, "asrStandard" to 6, "maghrib" to 5, "isha" to 7),
            ),
        )
        assertNull(cautiousProof(toronto.resolution, byUnit), "no unit: nothing, as the site holds the page")
        assertEquals(CautiousProof(byUnit.placeDays, byUnit.places), cautiousProof(toronto.resolution.copy(unitId = "toronto"), byUnit))
        assertNull(cautiousProof(toronto.resolution.copy(unitId = "milton"), byUnit), "Milton's row lacks Fajr")
        assertNull(cautiousProof(toronto.resolution.copy(unitId = "ottawa"), byUnit), "no row for Ottawa")
    }

    private fun instantFor(day: PrayerDay, prayer: Prayer): Instant = when (prayer) {
        Prayer.FAJR -> day.fajr
        Prayer.SUNRISE -> day.sunrise
        Prayer.DHUHR -> day.dhuhr
        Prayer.ASR -> day.asr
        Prayer.MAGHRIB -> day.maghrib
        Prayer.ISHA -> day.isha
    }

    /**
     * Review fix: the cautious "which timetable decides" table used to compare each member's raw
     * day against [world.taqwa.app.prayer.engine.EngineDay.day] — the *adjusted* one — so an active
     * manual adjustment left a prayer with no matching member at all (a blank "decided by", and a
     * stray leading "; " in the second line). [AboutTimesUiState.Cautious.combined] must stay the
     * members combined before the adjustment, so every prayer always matches at least one member.
     */
    @Test
    fun `an active adjustment still leaves every prayer matching a member's own day`() {
        val toronto = Place(43.6532, -79.3832, "America/Toronto", "CA")
        val settings = EngineSettings(adjustmentsMinutes = mapOf(Prayer.ISHA to 5))
        val engineDay = PrayerEngine.dayTimes(toronto, date, settings)
        val state = aboutTimesUiState(engineDay, toronto, date)
        assertIs<AboutTimesUiState.Cautious>(state)

        // The adjustment must actually have moved Isha away from the unadjusted combined day,
        // or this test would not exercise the bug at all.
        assertNotEquals(engineDay.day.isha, state.combined.isha)

        for (prayer in Prayer.entries) {
            val shown = instantFor(state.combined, prayer)
            val agrees = state.members.any { (_, memberDay) -> instantFor(memberDay, prayer) == shown }
            assertTrue(agrees, "no member's own $prayer time matches the unadjusted combined day")
        }
    }

    /**
     * Review fix: the checked template's "at most N minutes after" tile must read the stamp's own
     * measured worst (spec §2.3's İstanbul mockup: "figures from the release's proof stamp"), not
     * the class's late-limit ceiling — the same source the unchecked template's "up to {worst}
     * minutes after" sentence already reads.
     */
    @Test
    fun `the checked tile's at-most figure is the worst start measured at the user's own unit`() {
        // Review I3. The committed my.jakim stamp's entry-wide worst is 4, the end of eating (each
        // zone's Subuh is printed for its latest point; the end-of-eating audit of 3 Oct 2026), and
        // before it was 3, Perlis's PLS01 sunrise: ends either way. Kuala Lumpur's own zone (WLY01)
        // measured every start within a minute, and that is the figure its tile claims.
        val kualaLumpur = Place(3.1412, 101.68653, "Asia/Kuala_Lumpur", "MY")
        val state = stateFor(kualaLumpur)
        assertIs<AboutTimesUiState.AuthorityChecked>(state)
        assertEquals("my.jakim", state.resolution.entry.id)
        assertEquals("WLY01", state.resolution.unitId)
        val stamp = requireNotNull(state.stamp) { "my.jakim should carry a committed stamp" }
        assertEquals(4, stamp.worstLateMinutes.values.max())
        assertEquals(4, stamp.worstLateMinutes.getValue("endOfEating"))
        assertEquals(1, checkedAtMostMinutes(state.resolution, kualaLumpur, stamp))
    }

    @Test
    fun `kazan's at-most figure is its own unit's starts not another town's end of sahur`() {
        // Review I3: the ru.dumrt stamp's entry-wide worst is Zelenodolsk's end of sahur (over two
        // hours, on the white nights) and its Fajr; Kazan's own starts are within DUM RT's minutes.
        val kazan = Place(55.7887, 49.1221, "Europe/Moscow", "RU", admin1 = "Tatarstan")
        val state = stateFor(kazan)
        assertIs<AboutTimesUiState.AuthorityChecked>(state)
        assertEquals("ru.dumrt", state.resolution.entry.id)
        assertEquals("kazan", state.resolution.unitId)
        val stamp = requireNotNull(state.stamp)
        assertTrue(stamp.worstLateMinutes.values.max() > 60)
        val kazanStarts = stamp.worstLateByUnit.getValue("kazan")
            .filterKeys { it != "sunrise" && it != "endOfEating" && it != "imsak" }.values.max()
        assertEquals(kazanStarts, checkedAtMostMinutes(state.resolution, kazan, stamp))
        assertTrue(kazanStarts <= 2)
    }

    @Test
    fun `an entry checked without units claims its own worst start`() {
        // Umm al-Qura is gated at twelve places with no units: its figure is the entry's own, over
        // the starts only.
        val riyadh = Place(24.68773, 46.72185, "Asia/Riyadh", "SA")
        val state = stateFor(riyadh)
        assertIs<AboutTimesUiState.AuthorityChecked>(state)
        val stamp = requireNotNull(state.stamp)
        assertTrue(stamp.worstLateByUnit.isEmpty())
        val starts = listOf("fajr", "dhuhr", "asrStandard", "maghrib", "isha").mapNotNull { stamp.worstLateMinutes[it] }.max()
        assertEquals(starts, checkedAtMostMinutes(state.resolution, riyadh, stamp))
    }

    @Test
    fun `the unchecked template claims no figure for an unmeasured unit`() {
        // Review I2 (spec §3.5): Ipoh lies beyond JAKIM's measured zones, so JAKIM is class D there
        // and unmeasured; its stamp holds figures for other zones, none of them Ipoh's.
        val ipoh = Place(4.5975, 101.0901, "Asia/Kuala_Lumpur", "MY")
        val state = stateFor(ipoh)
        assertIs<AboutTimesUiState.AuthorityUnchecked>(state)
        assertEquals("my.jakim", state.resolution.entry.id)
        assertFalse(state.resolution.measured)
        assertTrue(state.stamp != null)
        assertNull(uncheckedWorstMinutes(state.resolution, state.stamp))
    }

    @Test
    fun `at tromso about says what of irn's makkah-time calendar is not followed and nowhere else`() {
        // Ruling R82: "never before IRN's" holds on every cell but the declared ones, which the note names.
        val tromso = stateFor(Place(69.6492, 18.9553, "Europe/Oslo", "NO"))
        assertIs<AboutTimesUiState.AuthorityUnchecked>(tromso)
        assertEquals("no.irn", tromso.resolution.entry.id)
        assertEquals(Res.string.about_not_followed_irn_tromso, notFollowedNoteRes(tromso.resolution))
        val trondheim = Registry.resolveEntry(Registry.byId("no.irn")!!, Place(63.43049, 10.39506, "Europe/Oslo", "NO"))
        assertNull(notFollowedNoteRes(trondheim))
        assertNull(notFollowedNoteRes(stateFor(Place(59.91273, 10.74609, "Europe/Oslo", "NO")).resolution)) // Oslo, cautious
    }

    @Test
    fun `the maghrib cap is named where it decided today's maghrib and not otherwise`() {
        // Ruling R91 (spec §3.6): About says so where the shown Maghrib keeps the most-followed
        // member's own minutes while another member's is later, naming the later one; nowhere else.
        // Brussels: EMB's own Maghrib under the cap, Diyanet's later, every day (ruling R88).
        val brussels = Place(50.8503, 4.3517, "Europe/Brussels", "BE")
        val state = stateFor(brussels)
        assertIs<AboutTimesUiState.Cautious>(state)
        assertEquals("be.cautious", state.resolution.entry.id)
        val cap = requireNotNull(maghribCapToday(state)) { "Belgium's members spread past the agreement every day" }
        assertEquals("be.emb", cap.followed.id)
        assertEquals(listOf("tr.diyanet.europe"), cap.later.map { it.id })
        val emb = state.members.single { (member, _) -> member.id == "be.emb" }.second
        val diyanet = state.members.single { (member, _) -> member.id == "tr.diyanet.europe" }.second
        assertEquals(emb.maghrib, state.combined.maghrib, "the shown Maghrib is EMB's own minutes")
        assertTrue(diyanet.maghrib > state.combined.maghrib)

        // Where every member has begun Maghrib by the minute shown there is nothing to say.
        val agreed = state.copy(members = state.members.map { (member, day) -> member to day.copy(maghrib = state.combined.maghrib) })
        assertNull(maghribCapToday(agreed))

        // The member followed is the one giving the shown minute, whichever it is; the later ones are named.
        val shown = state.combined.maghrib
        val swapped = state.copy(
            members = state.members.map { (member, day) ->
                member to day.copy(maghrib = if (member.id == "be.emb") shown + 3.minutes else shown)
            },
        )
        val swappedCap = requireNotNull(maghribCapToday(swapped))
        assertEquals("tr.diyanet.europe", swappedCap.followed.id)
        assertEquals(listOf("be.emb"), swappedCap.later.map { it.id })

        // Toronto's members are within the agreement today: the latest is shown, and nothing is said.
        val toronto = stateFor(Place(43.6532, -79.3832, "America/Toronto", "CA"))
        assertIs<AboutTimesUiState.Cautious>(toronto)
        assertEquals(toronto.members.none { (_, day) -> day.maghrib > toronto.combined.maghrib }, maghribCapToday(toronto) == null)
    }

    @Test
    fun `the unchecked template claims its own unit's worst start where it is measured`() {
        val tripoli = Place(32.88743, 13.18733, "Africa/Tripoli", "LY")
        val state = stateFor(tripoli)
        assertIs<AboutTimesUiState.AuthorityUnchecked>(state)
        assertTrue(state.resolution.measured)
        val stamp = requireNotNull(state.stamp)
        val unit = requireNotNull(state.resolution.unitId)
        val starts = stamp.worstLateByUnit.getValue(unit)
            .filterKeys { it != "sunrise" && it != "endOfEating" && it != "imsak" }.values.max()
        assertEquals(starts, uncheckedWorstMinutes(state.resolution, stamp))
    }

    /**
     * Ruling R73's shape of the ly.awqaf stamp (a fixture: lateness counts only, never a time). The
     * entry is checked on every start somewhere; Tobruk's row, like the eleven other eastern and
     * southern cities with no adhan of their own, holds sunrise, Dhuhr, Asr and Isha but not Fajr
     * or Maghrib; Benghazi's holds all five starts, Fajr and Maghrib from the owner's adhan.
     */
    private val libyaAfterR73 = ProofStamp(
        entryId = "ly.awqaf",
        places = 22,
        placeDays = 67,
        ramadanDays = 3,
        provenThrough = "2026-10-02",
        worstLateMinutes = mapOf(
            "fajr" to 2, "sunrise" to 3, "dhuhr" to 2, "asrStandard" to 3, "maghrib" to 2, "isha" to 3,
            "endOfEating" to 1, "imsak" to 2,
        ),
        worstLateByUnit = mapOf(
            "tobruk" to mapOf("sunrise" to 2, "dhuhr" to 1, "asrStandard" to 1, "isha" to 1),
            "benghazi" to mapOf("fajr" to 1, "sunrise" to 2, "dhuhr" to 1, "asrStandard" to 2, "maghrib" to 1, "isha" to 1),
        ),
        lateLimits = emptyList(),
    )

    @Test
    fun `a unit whose row lacks a start its entry is checked on elsewhere claims no figure`() {
        // Review of R73: Tobruk's Fajr and Maghrib are measured nowhere, so its worst over the other
        // starts must not read as "up to 1 minute after" for the whole day.
        val tobruk = Place(32.08963, 23.95385, "Africa/Tripoli", "LY")
        val state = stateFor(tobruk)
        assertIs<AboutTimesUiState.AuthorityUnchecked>(state)
        assertEquals("tobruk", state.resolution.unitId)
        assertTrue(state.resolution.measured)
        assertNull(measuredStartsWorst(state.resolution, libyaAfterR73))
        assertNull(uncheckedWorstMinutes(state.resolution, libyaAfterR73))
        // The checked template falls back to the class's own limit (D, 3 min), never the row's 1.
        assertEquals(3, checkedAtMostMinutes(state.resolution, tobruk, libyaAfterR73))
    }

    @Test
    fun `a unit whose row holds every start its entry is checked on still claims its own worst`() {
        val benghazi = Place(32.11486, 20.06859, "Africa/Tripoli", "LY")
        val state = stateFor(benghazi)
        assertEquals("benghazi", state.resolution.unitId)
        assertEquals(2, uncheckedWorstMinutes(state.resolution, libyaAfterR73))
    }

    @Test
    fun `a unit checked on the hanafi asr where others are checked on the standard one is not missing asr`() {
        // The gate writes the Standard and Hanafi Asr apart (London, Norway, South Africa): either
        // counts as the unit's Asr.
        val tobruk = Place(32.08963, 23.95385, "Africa/Tripoli", "LY")
        val state = stateFor(tobruk)
        val stamp = libyaAfterR73.copy(
            worstLateByUnit = mapOf(
                "tobruk" to mapOf("fajr" to 1, "dhuhr" to 1, "asrHanafi" to 2, "maghrib" to 1, "isha" to 1),
            ),
        )
        assertEquals(2, measuredStartsWorst(state.resolution, stamp))
    }
}
