package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.cautious
import world.taqwa.app.prayer.engine.registry.convention
import world.taqwa.app.prayer.engine.registry.data.JamiatTowns
import world.taqwa.app.prayer.engine.registry.pointTable
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/** South Africa (research South Africa sections; profiles-tested.md data-wide-band). */
object SouthAfrica {

    /** Jamiat's own Cape Town point (33°56′ S, 18°28′ E, as its table prints it). */
    private val jamiatCapeTown = GeoPoint(-(33 + 56 / 60.0), 18 + 28 / 60.0)

    /**
     * Jamiatul Ulama's perpetual salaah times (Jamiat SA, Darul Ihsan and Jamiat KZN publish the same
     * system; HIGH): the sun's declination and equation of time once per date at 0h UT in a year of
     * 2026's solar phase, Fajr 18°, Isha 18° (Hanafi) or 15° (Shafi'i), Zuhr = noon + 5 and Maghrib =
     * sunset + 3 (documented), no elevation, nearest rounding, Suhoor ends = Fajr − 5 (a printed
     * column), counted back from the dawn. The Hanafi Isha is taken: the later, and Hanafi leads in
     * Gauteng and KwaZulu-Natal (spec §3.7). Margins fitted by Task 7g on Johannesburg, Cape Town and
     * Durban over 2025–28 (their printed Suhoor column included) and held out on the other five towns,
     * 2029, Darul Ihsan's Pretoria and Jamiat KZN's relay: 0 early over 14,973 place-days, every time
     * within 1 min but Maghrib and Isha on 3 days (2). Units are Jamiat's 379 South African towns at its
     * own points; the eight analysed and Pretoria (Darul Ihsan's) are measured. Class B there.
     */
    val method = TimetableMethod(
        id = "za.jamiat",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        sunModel = SunModel.DAILY_0H_UT,
        phaseYear = 2026,
        asrModel = AsrModel.DAILY_0H_UT,
        authorityMinutes = EventOffsets(dhuhr = 5, maghrib = 3),
        margins = margins(start = -19, sunrise = 20, fajr = -18, isha = -17),
        endOfEating = EndOfEating.MinutesBeforeFajr(5),
        endOfEatingMarginSeconds = 20,
    )

    private val measuredTowns = setOf(
        "Johannesburg", "Pietersburg/Polokwane", "Durban", "Cape Town", "Springbok Ncape", "Port Elizabeth",
        "Bloemfontein", "Upington", "Pretoria",
    )

    val units = UnitSet(
        "za.jamiat",
        JamiatTowns.towns.map { t ->
            AuthorityUnit(
                id = t.name.lowercase().replace(' ', '-'), name = t.name, point = t.point, radiusKm = 30.0,
                measured = t.name in measuredTowns,
            )
        }.distinctBy { it.id },
    ) { method.atEdge("za.jamiat.edge", method.margins.widened(60 + 22, sunrise = -60 - 23)) }

    val entry: RegistryEntry = single(
        id = "za.jamiat", nameKey = "authority_jamiat", entryClass = EntryClass.B, method = method,
        school = AsrSchool.HANAFI, scope = Scope.GLOBAL, countries = setOf("ZA"),
    )

    /**
     * The Muslim Judicial Council's website times: no method stated, behaving like MWL 18°/17° with no
     * Dhuhr + 1 (Radio 786 documents MWL with an 18° Fajr, Shafi'i Asr, Fajr the end of sehri), rounded
     * to the nearest minute, its Asr about a minute before the exact one. One month (September 2026) is
     * held, so the margins are nearest rounding's bounds rather than that month's tighter fit (Task 7g).
     */
    val mjc: RegistryEntry = single(
        id = "za.mjc", nameKey = "authority_mjc", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "za.mjc", fajrAngle = 18.0, isha = IshaRule.Angle(17.0),
            margins = margins(start = -10, sunrise = 10, asr = -60),
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("ZA"),
    )

    /**
     * The community calendar (VOC, the Wembley calendar and masjids.co.za carry the same numbers but for
     * a minute here and there; fitted): Fajr ≈ 16.5°, Isha ≈ 15.3–15.4°, Dhuhr = noon + 3 (documented
     * column), Maghrib = sunset + 2 to 3, fixed town offsets; its Fajr up to 45 s before the dawn, hence
     * the end of eating 50 s before it. Margins fitted by Task 7g on Wembley's 2026 rows and widened by
     * its 2027 rows' excess; three Asr faults in Wembley's table (3–4 min after its neighbours and the
     * relay) are left out, not fitted.
     */
    val voc: RegistryEntry = single(
        id = "za.voc", nameKey = "authority_cape_calendar", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "za.voc", fajrAngle = 16.5, isha = IshaRule.Angle(15.4), asrModel = AsrModel.EXACT_MOMENT,
            authorityMinutes = EventOffsets(dhuhr = 3, maghrib = 3),
            margins = margins(start = -16, sunrise = -35, fajr = -23, asr = -7, maghrib = 5, isha = 37),
            endOfEatingMarginSeconds = -50,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("ZA"),
    )

    /** The MJC's website times and the community calendar are Cape Town's tables (ruling R30). */
    val capeTownTables: List<UnitSet> = GeoPoint(-33.92584, 18.42322).let { capeTown ->
        listOf(pointTable(mjc, "Cape Town", capeTown), pointTable(voc, "Cape Town", capeTown))
    }

    /**
     * The MJC as Cape Town's cautious member: its own method with its Maghrib at sunset + 2 (Task 7g).
     * The three tables print Maghrib from sunset to sunset + 3, and their printed Maghribs agree within
     * 2 min on most days (ruling R38: then Maghrib is the latest of them); with each member's own
     * never-early margin the engine's members spread past 2 min on those days and capped Maghrib at the
     * MJC's, 2 min before Jamiat's and the calendar's (20 of 30 September days). So the MJC member's
     * Maghrib is taken at the others' earliest, which keeps the engine's members within 2 min and its
     * Maghrib the latest of the three, never before any.
     */
    private val mjcMember = mjc.asMember(1).let { it.copy(method = it.method.copy(margins = it.method.margins.copy(maghrib = MJC_MEMBER_MAGHRIB))) }

    /**
     * Cape Town: no majority, three tables (research): the MJC's, the community calendar, and
     * Jamiat's Cape Town table with its Shafi'i Isha (15°). Standard Asr (the MJC, spec §3.7).
     * Jamiat's member is its Cape Town table, so that table's point rides as its fixed point
     * (ruling R30): across the metro its starts are never before the table's, its ends never after.
     */
    val cape = cautious(
        id = "za.cape",
        members = listOf(
            mjcMember,
            voc.asMember(2),
            convention(
                "za.jamiat.shafii", "authority_jamiat",
                method.copy(id = "za.jamiat.shafii", isha = IshaRule.Angle(15.0), fixedPoint = jamiatCapeTown),
                3,
                measuredAt = listOf(GeoPoint(-33.92584, 18.42322)),
            ),
        ),
        school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.CITY, countries = setOf("ZA"), measured = true,
        named = { it != "za.jamiat.shafii" },
        lateLimits = listOf(
            LateLimit(
                4,
                "The three tables print Maghrib from sunset to sunset + 3; Maghrib is the latest member's, never " +
                    "before any, so on the days the MJC prints it 3 min before the others it runs up to 4 min after the MJC's.",
                setOf(TimedEvent.MAGHRIB),
            ),
            LateLimit(
                2,
                "Sunrise is the earliest of the three members', each a little before its own table's: up to 2 min " +
                    "before the MJC's printed sunrise.",
                setOf(TimedEvent.SUNRISE),
            ),
        ),
    )

    val entries = listOf(entry, mjc, voc, cape)

    /** The MJC member's Maghrib margin in za.cape, seconds after sunset (see [mjcMember]). */
    private const val MJC_MEMBER_MAGHRIB = 110
}
