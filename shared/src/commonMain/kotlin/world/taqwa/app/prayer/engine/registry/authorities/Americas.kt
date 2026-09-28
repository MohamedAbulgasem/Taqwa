package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.method.AsrSchool
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
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.convention
import world.taqwa.app.prayer.engine.registry.pointTable
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single

/**
 * The United States, Canada, Australia and New Zealand (research North America and Oceania
 * sections; spec §6.1–§6.2).
 */
object Americas {

    private val safe = margins(start = SAFE_START, sunrise = SAFE_SUNRISE)

    // United States (spec §6.2 b: a majority convention with the MWL 1986 minutes).

    /**
     * The United States: FCNA/ISNA 15°/15° (36 of 42 named mosques, 39 of 43 Mawaqit tables, 73 of 93
     * Madina tables; FCNA's current advice of 27–29 Oct 2017, reaffirmed 18 Sep 2024), no binding
     * authority. Standard Asr (spec §3.7). The mosque tables that follow 15°/15° are its floors (Task 7g,
     * 35 tables from New York to California): many add their own minutes (Zuhr up to + 5 at Irving and
     * MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS ± 2 in the safe
     * direction), and the app is never before any of them. Margins fitted on 14 of the tables and widened
     * by the other 21's excess (proof.md); the end of eating before every table's Fajr (none prints an
     * imsak).
     */
    val usa: RegistryEntry = single(
        id = "us.isna", nameKey = "authority_isna", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "us.isna", fajrAngle = 15.0, isha = IshaRule.Angle(15.0),
            margins = margins(start = 154, sunrise = -98, fajr = 71, dhuhr = 314, maghrib = 266, isha = 150),
            endOfEatingMarginSeconds = -99,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("US", "PR", "VI", "GU", "AS", "MP", "UM"), measured = true,
        lateLimits = listOf(
            LateLimit(7, US_FLOORS + " Zuhr up to 7 min after the tables that add nothing.", setOf(TimedEvent.DHUHR)),
            LateLimit(6, US_FLOORS + " Maghrib up to 6 min after the tables that add nothing.", setOf(TimedEvent.MAGHRIB)),
            LateLimit(
                5,
                US_FLOORS + " Asr and Isha, a minute or two after the angle at some tables (ADAMS's Isha 2 min later by its " +
                    "rule), up to 5 min after the others'.",
                setOf(TimedEvent.ASR, TimedEvent.ISHA),
            ),
            LateLimit(
                4,
                US_FLOORS + " ADAMS moves Fajr and sunrise 2 min earlier (its safe direction): Fajr up to 4 min after " +
                    "its, and sunrise and the end of eating, never after its, up to 4 min before the others'.",
                setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /**
     * The Chicago metro: a block of 18°/15° with Hanafi Asr (Islamic Foundation Villa Park prints
     * "Fajr 18 degrees Isha 15 degrees Asr Hanfi"; Makki Masjid and Masjid DarusSalam Lombard the same),
     * with the US floors' minutes (its own tables' Zuhr up to + 5 and Maghrib up to + 3.5 lie within them).
     */
    private val chicagoBlock = convention(
        "us.chicago.eighteen", "timetable_chicago_eighteen",
        usa.method!!.copy(id = "us.chicago.eighteen", fajrAngle = 18.0),
        2,
        measuredAt = listOf(GeoPoint(41.8898, -87.989), GeoPoint(41.9704, -87.7144), GeoPoint(41.9028, -88.047)),
    )

    /**
     * The Chicago metro: its mosques differ on Fajr (Task 7g, seven tables held): the 18° block (Villa
     * Park, Makki, DarusSalam Lombard) and 15°/15° (MCC Chicago, MEC Morton Grove, Mosque Foundation
     * Bridgeview, the Prayer Center of Orland Park), all with Hanafi Asr, Arab mosques included (spec
     * §3.7). Cautious: Fajr no earlier than the 15° mosques', the end of eating no later than the 18°
     * block's dawn.
     */
    val chicago = cautious(
        id = "us.chicago", members = listOf(usa.asMember(1), chicagoBlock),
        school = AsrSchool.HANAFI, schoolKnown = true, scope = Scope.CITY, countries = setOf("US"), measured = true,
        named = { it == "us.isna" },
        lateLimits = listOf(
            LateLimit(
                31,
                "Chicago's mosques differ on Fajr: the 15° mosques' Fajr, never before it, runs up to 31 min after the " +
                    "18° block's (Villa Park, Makki, DarusSalam).",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                32,
                "Chicago's mosques differ on Fajr: the end of eating is the 18° block's dawn, never after it, up to 32 min " +
                    "before the 15° mosques' Fajr (MCC, MEC, Mosque Foundation, Orland Park).",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(6, CHICAGO_FLOORS + " Zuhr and Maghrib up to 6 min.", setOf(TimedEvent.DHUHR, TimedEvent.MAGHRIB)),
            LateLimit(5, CHICAGO_FLOORS + " Asr and Isha up to 5 min.", setOf(TimedEvent.ASR, TimedEvent.ISHA)),
            LateLimit(3, CHICAGO_FLOORS + " Sunrise up to 3 min before theirs.", setOf(TimedEvent.SUNRISE)),
        ),
    )

    // Canada.

    /**
     * Montreal (12 of 16 mosques) and Ottawa (6 of 6) follow ISNA 15°/15°, Standard Asr (spec §3.6). As in
     * the US, their 15°/15° tables are its floors (Task 7g, 18 Mawaqit tables): margins fitted on nine and
     * held out on the other nine, which showed none early (proof.md).
     */
    val canadaIsna: RegistryEntry = single(
        id = "ca.isna", nameKey = "authority_isna", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.isna", fajrAngle = 15.0, isha = IshaRule.Angle(15.0),
            margins = margins(start = 71, sunrise = -160, fajr = 110, dhuhr = 10, maghrib = 131, isha = 83),
            endOfEatingMarginSeconds = -103,
        ),
        school = AsrSchool.STANDARD, scope = Scope.CITY, countries = setOf("CA"), measured = true,
        lateLimits = listOf(
            LateLimit(5, CANADA_FLOORS + " Fajr, Maghrib and sunrise and the end of eating up to 5 min from the others'.",
                setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.MAGHRIB, TimedEvent.END_OF_EATING)),
            LateLimit(4, CANADA_FLOORS + " Asr and Isha up to 4 min after the others'.", setOf(TimedEvent.ASR, TimedEvent.ISHA)),
        ),
    )

    /**
     * The Islamic Foundation of Toronto: "Fajr Dawn 15 dg / Isha Night 13.5° dg", Hanafi; Dhuhr and
     * Maghrib about a minute after transit and sunset. Margins fitted on its 2026 table (Task 7g:
     * January to June, widened by July to December's excess), its faults left out (proof.md).
     */
    val ift: RegistryEntry = single(
        id = "ca.ift", nameKey = "authority_ift", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.ift", fajrAngle = 15.0, isha = IshaRule.Angle(13.5), asrModel = AsrModel.EXACT_MOMENT,
            authorityMinutes = EventOffsets(dhuhr = 1, maghrib = 1),
            margins = margins(start = 33, sunrise = 27, fajr = 82, dhuhr = -44, asr = 51, isha = 82),
        ),
        school = AsrSchool.HANAFI, scope = Scope.GLOBAL, countries = setOf("CA"),
    )

    /**
     * The Islamic Institute of Toronto: 15°, Maghrib about sunset + 2, Isha = Maghrib + 90, Standard Asr
     * about 2 min after the exact one. Margins fitted on its 2026 table as IFT's.
     */
    val iit: RegistryEntry = single(
        id = "ca.iit", nameKey = "authority_iit", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.iit", fajrAngle = 15.0, isha = IshaRule.AfterMaghrib(90), asrModel = AsrModel.EXACT_MOMENT,
            authorityMinutes = EventOffsets(maghrib = 2),
            margins = margins(start = 18, sunrise = -54, fajr = 78, dhuhr = 49, asr = 107),
        ),
        school = AsrSchool.HANAFI, scope = Scope.GLOBAL, countries = setOf("CA"),
        lateLimits = listOf(
            LateLimit(
                4,
                "IIT's own table drifts over its year: its sunrise from 2 min before to 2 min after the sun's, its " +
                    "Maghrib from sunset to sunset + 2.5 (and Isha, 90 min after it, with it); the margins hold it " +
                    "never early all year, so in spring they run up to 4 min.",
                setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB, TimedEvent.ISHA),
            ),
        ),
    )

    /**
     * MAC Masjid Toronto: plain 15°/15°, nearest, no Dhuhr + 1, Asr 1–2 min after the exact one. Margins
     * fitted on its 2026 table as IFT's.
     */
    val mac: RegistryEntry = single(
        id = "ca.mac", nameKey = "authority_mac_toronto", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ca.mac", fajrAngle = 15.0, isha = IshaRule.Angle(15.0), asrModel = AsrModel.DAILY_0H_UT,
            margins = margins(start = -22, sunrise = 20, fajr = -19, dhuhr = -23, asr = 24, isha = -12),
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("CA"),
    )

    /**
     * Toronto and the GTA: no majority (spec §3.6): the Islamic Foundation of Toronto (most followed
     * in the mockup), the Islamic Institute of Toronto and MAC Masjid Toronto. School not known:
     * the later Asr.
     */
    val toronto = cautious(
        id = "ca.toronto", members = listOf(ift.asMember(1), iit.asMember(2), mac.asMember(3)),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.CITY, countries = setOf("CA"), measured = true,
        named = { true },
        lateLimits = listOf(
            LateLimit(
                4,
                "None of the three tables prints an imsak, so each Fajr is where its fast begins; the end is the " +
                    "earliest member's own dawn less its margin, up to 4 min before the tables' Fajr.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                7,
                TORONTO_SPREAD + " West of the mosques the user's own sun is later than the tables' points (at Milton, " +
                    "the reach's western end, by 2.5 min), and Fajr and Isha shown follow it: up to 6 and 7 min.",
                setOf(TimedEvent.FAJR, TimedEvent.ISHA),
            ),
            LateLimit(
                6,
                TORONTO_SPREAD + " West of the mosques the user's own sun is later (at Milton by 2.5 min), and Dhuhr, " +
                    "Asr and Maghrib shown follow it: up to 5, 6 and 5 min.",
                setOf(TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB),
            ),
            LateLimit(
                3,
                TORONTO_SPREAD + " Sunrise is the earliest of the three, each a little before its own table's.",
                setOf(TimedEvent.SUNRISE),
            ),
        ),
    )

    /**
     * Each Toronto table is its own mosque's (ruling R30), so each is a unit at the mosque, reaching
     * as far as class D's three minutes east to west (ruling R40: about 60 km, the GTA from Oshawa to
     * Oakville): there the table's point rides as the method's fixed point beside the user's
     * (starts the later, ends the earlier), for the cautious members and for someone who follows one
     * of them alone. Mosque points from the research's fits (IFT Scarborough, IIT Scarborough, MAC
     * Masjid Toronto downtown).
     */
    val torontoUnits: List<UnitSet> = listOf(
        Triple(ift, "Islamic Foundation of Toronto", GeoPoint(43.7980, -79.2417)),
        Triple(iit, "Islamic Institute of Toronto", GeoPoint(43.8187, -79.2298)),
        Triple(mac, "MAC Masjid Toronto", GeoPoint(43.6555, -79.3858)),
    ).map { (entry, name, point) -> pointTable(entry, name, point) }

    /**
     * The Edmonton mosques' 18° family (Al Omari, Al Ansar, Al Farooq; Surrey Jamea in BC): Fajr 18°, no
     * earlier than a quarter of the night before sunrise (or, where the sun misses 18°, the MWL proportion
     * from 45°), Isha the earlier of 18° (Al Farooq's; Al Omari and Al Ansar 17°) and a sixth of the night
     * (PlaceCurves); Zuhr up to + 5 and Maghrib up to + 6 (Al Omari from August). Margins fitted on their
     * 2026 tables (Task 7g).
     */
    private val eighteenFamily = convention(
        "ca.eighteen", "timetable_eighteen_degrees",
        TimetableMethod(
            id = "ca.eighteen", fajrAngle = 18.0, isha = IshaRule.Angle(18.0),
            margins = margins(start = 169, sunrise = -105, fajr = 171, dhuhr = 264, maghrib = 316, isha = 95),
            endOfEatingMarginSeconds = -300,
        ),
        1,
        measuredAt = listOf(GeoPoint(53.6, -113.441), GeoPoint(53.5606, -113.53), GeoPoint(53.4675, -113.4302), GeoPoint(49.1342, -122.8787)),
    )

    /**
     * Edmonton's 15° family (Markaz Al Imam Malik): Fajr 15°, no earlier than a quarter of the night before
     * sunrise, Isha 16.5°, no later than 28 % of the night after sunset (PlaceCurves). Margins fitted on its
     * 2026 table (Task 7g).
     */
    private val fifteenFamily = convention(
        "ca.fifteen", "timetable_fifteen_degrees",
        TimetableMethod(
            id = "ca.fifteen", fajrAngle = 15.0, isha = IshaRule.Angle(16.5),
            margins = margins(start = -23, sunrise = 18, fajr = -15, asr = 9, maghrib = -17, isha = -30),
            endOfEatingMarginSeconds = -204,
        ),
        2,
        measuredAt = listOf(GeoPoint(53.608, -113.526)),
    )

    /**
     * Calgary Islamic Centre SW's family: Fajr 15°, no earlier than 23.5 % of the night before sunrise
     * (PlaceCurves), Isha = Maghrib + 90. Margins fitted on its 2026 table (Task 7g).
     */
    private val ishaNinety = convention(
        "ca.isha90", "timetable_isha_ninety",
        TimetableMethod(
            id = "ca.isha90", fajrAngle = 15.0, isha = IshaRule.AfterMaghrib(90),
            margins = margins(start = 21, sunrise = -98, fajr = 124, dhuhr = 6, asr = 86),
            endOfEatingMarginSeconds = -336,
        ),
        3,
        measuredAt = listOf(GeoPoint(51.0395, -114.1685)),
    )

    /**
     * Canada outside Toronto, Montreal and Ottawa: no majority (3 of 10 Madina tables 15°/15°, 2 MWL in
     * Alberta, 5 all different; each Prairie and BC mosque its own summer rule). Members (Task 7g), each
     * with its own summer rule fitted on its tables: the Edmonton mosques' 18° family (the most followed of
     * the tables held), Edmonton's 15° family, and Calgary Islamic Centre SW's 15° with Isha = Maghrib + 90. The plain ISNA and MWL presets are not members: where the
     * sun only just reaches their angles, in May and August on the Prairies, their Fajr falls near midnight
     * and would end the fast hours before any table's. School not known: the later Asr.
     */
    val canada = cautious(
        id = "ca.cautious",
        members = listOf(eighteenFamily, fifteenFamily, ishaNinety),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT, countries = setOf("CA"), measured = true,
        named = { false },
        lateLimits = listOf(
            LateLimit(
                36,
                CANADA_FAMILIES + " Fajr, the 15° families', never before theirs, runs up to 36 min after the 18° family's.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                38,
                CANADA_FAMILIES + " The end of eating, the 18° family's dawn, never after it, runs up to 38 min before the " +
                    "15° families' Fajr.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                77,
                CANADA_FAMILIES + " Isha is the latest family's: Imam Malik's 16.5° (up to 28 % of the night) in spring and " +
                    "autumn, the 18° family's in winter, up to 77 min after Calgary Islamic Centre SW's Maghrib + 90 and " +
                    "the 18° family's spring Isha.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                5,
                CANADA_FAMILIES + " Each keeps its own tables' minutes (Al Farooq's Zuhr + 5, Al Omari's Maghrib + 6 from " +
                    "August, Asr up to 3 min after the exact one): Zuhr, Asr and Maghrib up to 5 min after the others'.",
                setOf(TimedEvent.DHUHR, TimedEvent.ASR, TimedEvent.MAGHRIB),
            ),
            LateLimit(4, CANADA_FAMILIES + " Sunrise, the earliest family's, up to 4 min before the others'.", setOf(TimedEvent.SUNRISE)),
        ),
    )

    // Australia and New Zealand.

    /**
     * Lakemba Mosque (LMA), 1448 PDF, 349 days fitted, no method stated: Fajr 18° rounded down, sunrise
     * rounded down, Maghrib sunset rounded up, Isha = Maghrib + 90, Dhuhr noon + 2..7 and Asr from 12 min
     * before to 22 min after the Shafi'i Asr, both moving month by month: per-month offsets (seconds after
     * its latest minutes, + 7 and + 22) and margins fitted on the whole table (Task 7g).
     */
    val lakemba: RegistryEntry = single(
        id = "au.lma", nameKey = "authority_lakemba", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "au.lma", fajrAngle = 18.0, isha = IshaRule.AfterMaghrib(90),
            authorityMinutes = EventOffsets(dhuhr = 7, asr = 22),
            margins = margins(start = 0, sunrise = -19, fajr = -25, asr = 7, maghrib = 5, isha = 14), endOfEatingMarginSeconds = -17,
            monthlyOffsets = mapOf(
                Prayer.DHUHR to intArrayOf(-226, -208, -157, -111, -136, -201, -214, -142, -184, -70, -66, -87),
                Prayer.ASR to intArrayOf(-861, -1772, -1783, -1200, -789, -804, -1142, -1431, -995, -356, -80, -76),
            ),
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("AU"),
        lateLimits = listOf(
            LateLimit(
                15,
                "LMA's Asr runs from 12 min before to 22 min after the Shafi'i Asr on a smooth seasonal curve no rule here " +
                    "reproduces; per-month offsets keep it never early, up to 15 min after it within its fastest-moving " +
                    "months (October, December and January).",
                setOf(TimedEvent.ASR),
            ),
        ),
    )

    /** Lakemba Mosque's table is printed for Lakemba (ruling R30). */
    val lakembaUnits: UnitSet = pointTable(lakemba, "Lakemba Mosque", GeoPoint(-33.92, 151.0756))

    /**
     * Australia: no dominant table (HIGH): Lakemba's, and Diyanet's per-suburb tables (the TURKEY preset ± 1).
     * Maghrib is capped at LMA's (the most followed) where the two spread, 5–6 min before Diyanet's
     * (its sunset + 7), as the spec's cautious Maghrib (Task 7g's gate, proof.md).
     */
    val australia = cautious(
        id = "au.cautious", members = listOf(lakemba.asMember(1), Diyanet.other.asMember(2)),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.COUNTRY, countries = setOf("AU"), measured = true,
        named = { true },
        lateLimits = listOf(
            LateLimit(
                18,
                "Asr is the later member's: LMA's, on its per-month offsets up to 15 min after its own table, and the " +
                    "TURKEY preset's with its allowance for Diyanet's daily model (subtask 7b): up to 18 min after the " +
                    "earlier table's.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(
                10,
                "Isha is the later member's, the TURKEY preset's 17° or LMA's Maghrib + 90: up to 10 min after the other's.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                8,
                "Sunrise is the earlier member's, the TURKEY preset's (the sun's less 7 min): up to 8 min before LMA's.",
                setOf(TimedEvent.SUNRISE),
            ),
            LateLimit(
                4,
                "Zuhr is the later member's: the TURKEY preset's (+ 5) and LMA's (+ 2 to + 7 by month), up to 4 min after " +
                    "the earlier table's.",
                setOf(TimedEvent.DHUHR),
            ),
            LateLimit(
                2,
                "Fajr and the end of eating keep each member's own margins: up to 2 min from the tables'.",
                setOf(TimedEvent.FAJR, TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /** New Zealand: FIANZ, 20 cities (fitted, MEDIUM-HIGH): 18°/18°, Shafi'i Asr, no offsets, nearest. Auckland is held. */
    val fianzMethod = TimetableMethod(id = "nz.fianz", fajrAngle = 18.0, isha = IshaRule.Angle(18.0), margins = safe)

    /**
     * FIANZ's Auckland table, printed for Auckland (ruling R30), its reach by class D (ruling R40). Margins fitted
     * on its March and June 2026 pages, widened by September's Fajr excess to the whole table's (Task 7g).
     */
    val fianzUnits = UnitSet(
        "nz.fianz",
        listOf(
            AuthorityUnit(
                "auckland", "Auckland", GeoPoint(-36.84853, 174.76349), lateReachKm(-36.84853, EntryClass.D_AUTHORITY),
                fianzMethod.copy(
                    margins = margins(start = -13, sunrise = 18, fajr = -2, asr = -16, maghrib = -1, isha = -4),
                    endOfEatingMarginSeconds = 18,
                ),
            ),
        ),
    ) { fianzMethod.atEdge("nz.fianz.edge", margins(start = 60 + SAFE_START, sunrise = -60 + SAFE_SUNRISE)) }

    val newZealand: RegistryEntry = single(
        id = "nz.fianz", nameKey = "authority_fianz", entryClass = EntryClass.D_AUTHORITY, method = fianzMethod,
        school = AsrSchool.STANDARD, countries = setOf("NZ"),
    )

    val entries = listOf(usa, chicago, canadaIsna, ift, iit, mac, toronto, canada, lakemba, australia, newZealand)

    /** Why the US times run later than many of its 15°/15° tables (Task 7g's gate, proof.md). */
    private const val US_FLOORS = "The US mosques that follow 15°/15° add their own minutes (Zuhr up to + 5 at Irving and " +
        "MCC Silver Spring, Maghrib up to + 5 at King Fahad and Dar Al Noor, ADAMS 2 min in the safe direction); the app " +
        "is never before any of them:"

    /** Why Montreal's and Ottawa's times run later than many of their tables (Task 7g's gate, proof.md). */
    private const val CANADA_FLOORS = "Montreal's and Ottawa's 15°/15° tables differ among themselves: MAC's two (Al-Rawdah, " +
        "Abrar) wander around the rule (Fajr from 14.6° to 15.3°, Maghrib from 2 min before sunset to 3 min after, sunrise up " +
        "to 3.6 min before the sun's), and Asr runs from a minute before the exact one (Dar Al Arkam, CCML) to 2 min after it " +
        "(Ottawa South). The app is never before any of them, and sunrise and the end of eating never after any:"

    /** Why Canada's cautious times run later than its tables (Task 7g's gate, proof.md). */
    private const val CANADA_FAMILIES = "Canada's mosques outside Toronto, Montreal and Ottawa follow three families, each " +
        "with its own summer rule (the Edmonton mosques' 18°, Imam Malik's 15° with Isha 16.5°, Calgary Islamic Centre " +
        "SW's 15° with Isha = Maghrib + 90); the app is never before any of them."

    /** Why Chicago's times run later than its tables (Task 7g's gate, proof.md). */
    private const val CHICAGO_FLOORS = "Both members keep the US floors (Zuhr up to + 5, Maghrib up to + 5 at some US " +
        "mosques, Makki's Zuhr + 5 among them), after the Chicago tables that add less:"

    /** Why Toronto's cautious times run later than its three tables (Task 7g's gate, proof.md). */
    private const val TORONTO_SPREAD = "Toronto's three tables round and offset their times irregularly against their own " +
        "rules (IFT's Fajr and Isha up to 80 s after them, IIT's Asr up to 107 s, IIT's own Maghrib drifting over the " +
        "year), so each member keeps its table's widest margin and the latest of them runs up to 4 min after the " +
        "latest printed time at the mosques."

}
