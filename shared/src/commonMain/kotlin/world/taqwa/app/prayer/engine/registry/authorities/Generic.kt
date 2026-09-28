package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.ADHAN2_ASR_ALLOWANCE
import world.taqwa.app.prayer.engine.registry.CALCULATED_NAME
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single

/**
 * The old picker's methods as global Other methods (controller ruling R12; Tehran has none), and the
 * class D safe default where nothing is known (spec §6.2 c).
 *
 * Other methods follow the adhan2 presets the old picker used, on this core, with the first-guess
 * safe rounding (starts +30 s, sunrise and the end of eating −30 s; ruling R31, Task 7h), never an
 * authority's fitted margins. Their [RegistryEntry.school] is neutral: the
 * school that leads comes from the place's Automatic entry or the user (Task 8).
 *
 * Asr carries the extra [ADHAN2_ASR_ALLOWANCE]: adhan2 0.0.7 always takes the day's declination
 * once (at its `SolarCoordinates(julianDay)`, not iterated to the Asr moment), which the proof
 * test (`OtherMethodsProofTest`) measured up to about 34 s later than this core's iterated,
 * exact-moment Asr (the Maghreb-Libya research: adhan2 minus first-principles Asr is −37..+34 s).
 * The plain 30 s safe start would then not always reach adhan2's own Asr; only `other.turkey`
 * (`Diyanet.other`) is exempt, since it reuses Diyanet's own `AsrModel.DAILY_0H_UT`, the same
 * fixed-declination approach adhan2 uses there.
 */
object Generic {

    private val safeRounding = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE)

    private fun preset(id: String, nameKey: String, fajr: Double, isha: Double, dhuhrMinutes: Int = 1) = single(
        id = id, nameKey = nameKey, entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = id, fajrAngle = fajr, isha = IshaRule.Angle(isha),
            authorityMinutes = EventOffsets(dhuhr = dhuhrMinutes), margins = safeRounding, endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    /** Muslim World League: 18°/17°, Dhuhr + 1. */
    val mwl = preset("other.mwl", "method_muslim_world_league", 18.0, 17.0)

    /** ISNA (FCNA): 15°/15°, Dhuhr + 1. */
    val isna = preset("other.isna", "method_isna", 15.0, 15.0)

    /** University of Islamic Sciences, Karachi: 18°/18°, Dhuhr + 1. */
    val karachi = preset("other.karachi", "method_karachi", 18.0, 18.0)

    /**
     * Moonsighting Committee Worldwide (moonsighting.com, how-we.html; adhan2's preset): Zuhr + 5,
     * Maghrib + 3, Fajr the later of 18° and the seasonal morning function, Isha the earlier of 18°
     * and the seasonal evening function (shafaq general); from 55° N a seventh of the night takes
     * 18°'s place. The functions are minutes from sunrise or sunset, so they are expressed here
     * as day-of-year angle curves for the place's latitude ([PlaceCurves]); the entry's own method
     * carries the equator's curves.
     */
    private val moonsightingBase = TimetableMethod(
        id = "other.moonsighting",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(dhuhr = 5, maghrib = 3),
        margins = safeRounding,
        endOfEatingMarginSeconds = SAFE_END,
    )

    val moonsighting = single(
        id = "other.moonsighting", nameKey = "method_moonsighting", entryClass = EntryClass.D_AUTHORITY,
        method = PlaceCurves.at(moonsightingBase, GeoPoint(0.0, 0.0)), school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    // The class D safe default (spec §6.2 c).

    /**
     * A region's conventions for the safe default: the Fajr prayer at the latest dawn convention in
     * use there (18° where none is known), the end of eating 2 min before the earliest, Isha at the
     * latest convention + 2, sunrise − 2, Dhuhr, Asr and Maghrib + 2 (MWL Fiqh Council, 1986), and
     * larger minutes where a published table nearby prints them (§6.2 d: every published data point
     * is a floor). All with the first-guess 30 s.
     *
     * Fajr, sunrise, Dhuhr and Maghrib carry [EXTRA_SAFETY_SECONDS] on top of that 30 s: the gate
     * (Task 7h §3, `safe-default.tsv`) found `default.safe` up to a minute early or a minute-late
     * sunrise against JAKIM's own printed Subuh (Kuala Lumpur) and Kemenag's own Dhuhr/Maghrib/sunrise
     * (Jakarta), even though both authorities' own conventions are the same nominal figures this
     * region assumes (JAKIM's 18°; Kemenag's own +2 min) — a few tens of seconds of point/ephemeris
     * difference the plain 30 s did not cover. Not spec-mandated minutes (unlike the policy figures
     * below): a plain safety cushion, widened here per the observed excess (task-7-common's "widen by
     * the excess plus 5 s"), the same way [ADHAN2_ASR_ALLOWANCE] widens Asr's above.
     */
    class SafeRegion(
        val id: String,
        val latestFajr: Double,
        val earliestFajr: Double,
        val latestIsha: Double,
        val fajrAfterDawnMinutes: Int = 0,
        val horizonDeg: Double = -0.8333,
        val sunriseMinutes: Int = -2,
        val dhuhrMinutes: Int = 2,
        val asrMinutes: Int = 2,
        val maghribMinutes: Int = 2,
        val ishaMinutes: Int = 2,
    ) {
        val method = TimetableMethod(
            id = id,
            fajrAngle = latestFajr,
            isha = IshaRule.Angle(latestIsha),
            horizonDeg = horizonDeg,
            fajrAfterDawnMinutes = fajrAfterDawnMinutes,
            margins = margins(
                start = SAFE_START, sunrise = sunriseMinutes * 60 + SAFE_SUNRISE - EXTRA_SAFETY_SECONDS,
                fajr = SAFE_START + EXTRA_SAFETY_SECONDS,
                dhuhr = dhuhrMinutes * 60 + SAFE_START + EXTRA_SAFETY_SECONDS, asr = asrMinutes * 60 + SAFE_START,
                maghrib = maghribMinutes * 60 + SAFE_START + EXTRA_SAFETY_SECONDS, isha = ishaMinutes * 60 + SAFE_START,
            ),
            endOfEating = EndOfEating.DawnAngle(earliestFajr),
            endOfEatingMarginSeconds = -120,
        )
    }

    /** Nothing known: 18° throughout. */
    val world = SafeRegion("default.safe", 18.0, 18.0, 18.0)

    /** Latin America and the Caribbean: FCNA's 15° is the latest dawn in use in the Americas, MWL's 18° the earliest. */
    val americas = SafeRegion("default.safe.americas", 15.0, 18.0, 18.0)

    /**
     * Europe without an authority or a mosque survey (Spain, Portugal, Italy …): the Paris 12–13°
     * family is the latest dawn in wide European use, 18° the earliest; its Zuhr + 5 and Maghrib + 4
     * (78 % of Île-de-France mosques) as floors.
     */
    val europe = SafeRegion("default.safe.europe", 12.0, 18.0, 18.0, dhuhrMinutes = 5, maghribMinutes = 4)

    /**
     * The Balkans and their neighbours without a checked authority (North Macedonia, Serbia outside
     * Sandžak, Croatia, Slovenia, Bulgaria, Greece, Romania, Cyprus, Moldova): the latest Fajr in the
     * region is Kosovo's Sabah (imsak ≈ 19° + 20 min), the earliest dawn 19°, the latest Isha 18.5°;
     * Diyanet's temkin (sunrise − 7, Dhuhr + 5, Asr + 4) and Montenegro's Maghrib + 8.5 as floors.
     */
    val balkans = SafeRegion(
        "default.safe.balkans", 19.0, 19.0, 18.5, fajrAfterDawnMinutes = 20,
        sunriseMinutes = -7, dhuhrMinutes = 5, asrMinutes = 4, maghribMinutes = 9,
    )

    /**
     * Africa south of the Sahara without an authority: 15° Fajr is in use (Abuja, Dakar), 19.5° the
     * earliest dawn (Egypt, Mauritania); Jamiat's and Darul Ihsan's perpetual tables, printed for
     * towns across the continent, add Dhuhr + 5 and Maghrib + 3.
     */
    val africa = SafeRegion("default.safe.africa", 15.0, 19.5, 18.0, dhuhrMinutes = 5, maghribMinutes = 3)

    /** South-east Asia without a checked authority: JAKIM's 18° is the latest Subuh, the 20° of MUIS, Kemenag and MORA the earliest. */
    val southeastAsia = SafeRegion("default.safe.seasia", 18.0, 20.0, 18.0)

    /** Israel: the Levant's conventions (Jordan's and the PA's −2.25° horizon, Lebanon's 20° dawn, Jordan's 18.2° Isha). */
    val levant = SafeRegion("default.safe.levant", 18.0, 20.0, 18.2, horizonDeg = -2.25)

    /** South Asia beyond Pakistan, India and Bangladesh: Karachi's 18°/18° with IFB's Dhuhr and Maghrib + 3. */
    val southAsia = SafeRegion("default.safe.southasia", 18.0, 18.0, 18.0, dhuhrMinutes = 3, maghribMinutes = 3)

    /**
     * Central Asia without a table (Tajikistan, Turkmenistan): QMDB's 15° is the latest Fajr, the
     * Kyrgyz Muftiate's 18° the earliest, its 16° the latest Isha; the Uzbek Board's former Dhuhr + 5
     * and the Kyrgyz Maghrib + 7 as floors.
     */
    val centralAsia = SafeRegion("default.safe.centralasia", 15.0, 18.0, 16.0, dhuhrMinutes = 5, maghribMinutes = 7)

    private fun calculated(
        region: SafeRegion,
        id: String = region.id,
        school: AsrSchool,
        schoolKnown: Boolean,
        scope: Scope = Scope.COUNTRY,
        countries: Set<String> = emptySet(),
        lateLimits: List<LateLimit> = emptyList(),
    ) = single(
        id = id, nameKey = CALCULATED_NAME, entryClass = EntryClass.D_NONE,
        method = if (id == region.id) region.method else region.method.copy(id = id),
        school = school, schoolKnown = schoolKnown, scope = scope, countries = countries, lateLimits = lateLimits,
    )

    /**
     * `default.safe`'s exception (rulings R37/R41): the gate (Task 7h §3, `safe-default.tsv`) checked
     * it against a broad, unrelated sample of real authorities across regions (none of them actually
     * served by this fallback — they each have their own registered entry; this is a stress test of
     * how cautious the fallback is, not a proof of any of them). Never early anywhere sampled, but
     * several apply their own safety minutes deeper than the MWL Fiqh Council's 1986 baseline this
     * region follows (spec §6.2 c): Dar al-Ifta Cairo's own 19.5° dawn runs this region's flat 18°
     * Fajr up to about 12 min late (the worst measured); Diyanet's İstanbul temkin (sunrise − 7,
     * Dhuhr + 5, Asr + 4, Maghrib + 7) runs 4–8 min past this region's flat − 2/+ 2. 20 min covers
     * every measured case with room to spare.
     *
     * Isha gets its own, smaller exception: Dar al-Ifta Cairo's own 17.5° is shallower than this
     * region's 18°, so its real Isha comes up to about 8 min before this region's angle-based
     * estimate reaches 18° — a real, physical angle difference (never early: 18° is always later
     * than 17.5°), not a minutes-policy gap like the events above.
     */
    private val worldLateLimits = listOf(
        LateLimit(20, "no local convention is known here; nearby authorities elsewhere run deeper safety minutes than this region's flat 1986 baseline (Task 7h, safe-default.tsv)", setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB)),
        LateLimit(15, "this region's 18° Isha is deeper (later) than some real authorities' own shallower Isha angle (Dar al-Ifta Cairo's 17.5°, Task 7h, safe-default.tsv) — never early, only later by more than the class default", setOf(TimedEvent.ISHA)),
    )

    /** The safe default where nothing is known; the later Asr leads. */
    fun safeDefault(region: SafeRegion): RegistryEntry = safeDefaults.first { it.id == region.id }

    private val safeDefaults: List<RegistryEntry> = listOf(
        calculated(world, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.GLOBAL, lateLimits = worldLateLimits),
        calculated(americas, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT),
        calculated(europe, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT),
        // Every Balkan authority prints the first-shadow Asr (research, Bosnia to Albania).
        calculated(balkans, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT),
        // "S (Maliki / Shafi'i)" for Nigeria, Senegal, Somalia, Ethiopia (research summary table).
        calculated(africa, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT),
        // "Standard in … South-east Asia" (spec §3.7).
        calculated(southeastAsia, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT),
        // "Standard in the Arab world" (spec §3.7).
        calculated(levant, school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT),
        calculated(southAsia, school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT),
    )

    /**
     * Iran: Sunni times calculated by Taqwa (no Sunni authority found; spec §6.2 c) and the
     * Sunni-times card everywhere. The school per spec §3.7: Hanafi in Sistan-Baluchestan, Golestan
     * and Khorasan, Shafi'i (Standard) in Kurdistan and Hormozgan, otherwise not known, so the later.
     */
    val iran = calculated(world, id = "ir.default", school = AsrSchool.HANAFI, schoolKnown = false, countries = setOf("IR"))
    val iranHanafi = calculated(world, id = "ir.hanafi", school = AsrSchool.HANAFI, schoolKnown = true, scope = Scope.UNIT, countries = setOf("IR"))
    val iranShafii = calculated(world, id = "ir.shafii", school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.UNIT, countries = setOf("IR"))

    /** Afghanistan: Hanafi (spec §3.7), South Asia's conventions. */
    val afghanistan = calculated(southAsia, id = "af.default", school = AsrSchool.HANAFI, schoolKnown = true, countries = setOf("AF"))

    /** Tajikistan and Turkmenistan: Hanafi (Central Asia, spec §3.7), no table found. */
    val tajikistan = calculated(centralAsia, id = "tj.default", school = AsrSchool.HANAFI, schoolKnown = true, countries = setOf("TJ"))
    val turkmenistan = calculated(centralAsia, id = "tm.default", school = AsrSchool.HANAFI, schoolKnown = true, countries = setOf("TM"))

    val others = listOf(mwl, isna, karachi, moonsighting)
    val entries = safeDefaults + listOf(iran, iranHanafi, iranShafii, afghanistan, tajikistan, turkmenistan)

    /** See the KDoc on [SafeRegion]: the extra safety the gate showed the safe default needs. */
    private const val EXTRA_SAFETY_SECONDS = 150
}
