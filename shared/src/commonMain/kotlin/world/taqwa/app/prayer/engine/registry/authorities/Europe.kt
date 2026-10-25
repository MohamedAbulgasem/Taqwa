package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.Cautious
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.ADHAN2_ASR_ALLOWANCE
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Circle
import world.taqwa.app.prayer.engine.registry.ENDS_REACH_FACTOR
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.MethodVariant
import world.taqwa.app.prayer.engine.registry.OwnTable
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.RIDE_REACHES
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.beyondTable
import world.taqwa.app.prayer.engine.registry.followedBeyond
import world.taqwa.app.prayer.engine.registry.cautious
import world.taqwa.app.prayer.engine.registry.convention
import world.taqwa.app.prayer.engine.registry.data.DeLateDawnCurve
import world.taqwa.app.prayer.engine.registry.data.IfiCurve
import world.taqwa.app.prayer.engine.registry.data.IrnCurves
import world.taqwa.app.prayer.engine.registry.data.LondonUnifiedCurve
import world.taqwa.app.prayer.engine.registry.data.RabitaCurves
import world.taqwa.app.prayer.engine.registry.data.UkLateDawnCurve
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.pointTable
import world.taqwa.app.prayer.engine.registry.pointTables
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.tablesAtTheirPoints
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Europe (research Europe sections; spec §6.1: London class A, the UK outside London, Ireland,
 * France, Belgium, the Netherlands and the Nordics cautious). Members are kept short, most-followed
 * first, each with its evidence; their margins are the first-guess ±30 s until Task 7g fits them.
 * Named members are also Other methods (global).
 */
object Europe {

    private val safe = margins(start = SAFE_START, sunrise = SAFE_SUNRISE)

    // United Kingdom.

    /**
     * London Unified Prayer Timetable (inside the M25; 37 of 55 sampled London mosques, a clear
     * majority, spec §3.6 and §10.2): Fajr and Isha from Shaukat's model, whole-minute gaps before its
     * sunrise and after its Maghrib ([LondonUnifiedCurve]); sunrise = HMNAO − 3 "for safety", Zuhr =
     * noon + 5, Maghrib = sunset + 3, rounded to the nearest minute; both Asr printed; the Fajr also
     * begins the fast (no imsak), here its own earliest curve. Margins fitted on its 2026 table at its
     * point (Task 7g). Class B: at the point every time is within a minute of the table but Fajr, Isha
     * and the end of eating, which take a day's gap either side from 1 March for the leap years and so
     * run 2 min on some days. Its school is not known, so the later Asr leads (spec §3.7).
     */
    val londonMethod = TimetableMethod(
        id = "gb.london.lupt",
        fajrAngle = 12.0,
        isha = IshaRule.Angle(15.0),
        authorityMinutes = EventOffsets(sunrise = -3, dhuhr = 5, maghrib = 3),
        margins = margins(start = -12, sunrise = 30, fajr = -17, dhuhr = -14, asr = 36, isha = -15),
        endOfEating = EndOfEating.DawnAngle(LondonUnifiedCurve.endOfEating.max(), LondonUnifiedCurve.endOfEating),
        endOfEatingMarginSeconds = 34,
        fajrAngleByDayOfYear = LondonUnifiedCurve.fajr,
        ishaAngleByDayOfYear = LondonUnifiedCurve.isha,
    )

    /** London Unified's table's point (the M25's one table, ruling R44). */
    private val londonPoint = GeoPoint(LondonUnifiedCurve.LAT, LondonUnifiedCurve.LON)

    /**
     * Where London Unified applies when chosen (the owner's decision of 17 Oct 2026, the round 2 fix's open question):
     * R45's three reaches of its table's point (class B's two minutes of longitude, about 35 km, three times: about 104 km,
     * Basildon, Sevenoaks, St Albans, Luton, Reading and Folkestone), where its point still rides as the fixed point
     * ([londonUnits]). Beyond, it is out of scope, as any timetable is outside its area (spec §2.2, ruling R50): Automatic
     * applies, and the Timetable screen says so in its one line until the user returns. LUPT is London's table, the M25's;
     * far from it London's own times (in Glasgow its winter sunrise, about an hour before Glasgow's) are simply wrong for
     * the place, even where they were on the safe side.
     */
    val londonArea = Circle(londonPoint, lateReachKm(londonPoint.lat, EntryClass.B) * ENDS_REACH_FACTOR)

    val london: RegistryEntry = single(
        id = "gb.london.lupt", nameKey = "authority_london_unified", entryClass = EntryClass.B, method = londonMethod,
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.GLOBAL, countries = setOf("GB"), measured = true,
        area = londonArea,
    )

    /**
     * Ruling R44: the whole M25 follows the one LUPT table, computed at its point, so inside the M25
     * that point rides as the fixed point with no R40 cut (like Brunei's districts and Algeria's
     * wilayas): starts are the later of the point's and the user's own, ends the earlier. Measured on
     * the 2026 table at the M25's edges and corners (Task 7g, rulings R49): the recorded exceptions
     * below. Chosen beyond the M25, within its [londonArea], the table's point rides for every start and end
     * ([followedBeyond]).
     */
    val londonUnits: UnitSet = run {
        val point = londonPoint
        val twilight = LateLimit(
            5,
            "The M25 follows one table computed at its point, and Fajr and Isha shown are the later of the " +
                "point's and the user's own: at the M25's southern and western edges the user's Fajr, and at its " +
                "northern and north-western edges the user's Isha, at the table's depression run up to 3 min " +
                "later than the point's in summer.",
            setOf(TimedEvent.FAJR, TimedEvent.ISHA),
        )
        val west = LateLimit(
            3,
            "The M25 follows one table computed at its point; at the M25's western edge the user's own sun is " +
                "1.6 min later, and Asr and Maghrib shown follow it.",
            setOf(TimedEvent.ASR, TimedEvent.MAGHRIB),
        )
        val sunrise = LateLimit(
            3,
            "The M25 follows one table computed at its point; at the M25's eastern edge the user's own " +
                "sunrise is 1.7 min earlier, and the sunrise shown follows it.",
            setOf(TimedEvent.SUNRISE),
        )
        val eating = LateLimit(
            4,
            "The M25 follows one table computed at its point; at its eastern edge the user's own dawn is " +
                "1.7 min earlier, and at its northern edge up to 3 min earlier in summer, and the end of " +
                "eating shown follows it.",
            setOf(TimedEvent.END_OF_EATING),
        )
        val m25 = AuthorityUnit(
            "gb.london.lupt", "London (M25)", point, radiusKm = 0.0, lateLimits = listOf(twilight, west, sunrise, eating),
        )
        UnitSet(
            "gb.london.lupt",
            listOf(m25),
            choose = { place -> m25.takeIf { Regions.londonM25.contains(GeoPoint(place.lat, place.lon)) } },
        ) { user ->
            // Chosen outside the M25 (the city-point round of 11 Oct 2026, the owner's rule: a user who chooses a table
            // follows it): within its area, R45's three reaches (about 104 km: Basildon, Sevenoaks, St Albans, Luton,
            // Reading, Folkestone), the table's point rides as the fixed point for every start and end. Beyond, London
            // Unified is out of scope (londonArea, Registry.inScope; the owner's decision of 17 Oct 2026), so the app
            // never computes it there: Automatic applies. Resolved there directly (a tool), it is R45's edge at the
            // user's own point alone, its own sun and no figure, as beyond any point table's three reaches.
            val reach = lateReachKm(point.lat, london.entryClass)
            if (londonArea.contains(user)) {
                londonMethod.followedBeyond("gb.london.lupt.edge", point)
            } else {
                londonMethod.beyondTable("gb.london.lupt.edge", point, user, reach)
            }
        }
    }

    /**
     * Wifaqul Ulama (Deobandi, the largest group of UK mosques): Fajr 18° (the 1983 Bradford
     * agreement), Isha 15° at or above 48° (Preston, 11 Nov 2018), Zuhr = istiwa + 4, Maghrib = sunset
     * + 5, both Asr printed; rejects the one-seventh rule. Manchester Central Mosque prints 18°/15° with
     * Hanafi Asr, rounded to the nearest minute (its September 2026 table, seven rows: the margins are
     * nearest rounding's, not the seven rows' tighter fit), but its Zuhr at the meridian and its
     * Maghrib at sunset themselves: the documented minutes stay, a recorded exception against it.
     */
    val wifaqul: RegistryEntry = single(
        id = "gb.wifaqul", nameKey = "authority_wifaqul_ulama", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "gb.wifaqul", fajrAngle = 18.0, isha = IshaRule.Angle(15.0),
            authorityMinutes = EventOffsets(dhuhr = 4, maghrib = 5), margins = margins(start = -20, sunrise = 20, asr = -5),
            endOfEatingMarginSeconds = 20,
        ),
        school = AsrSchool.HANAFI, scope = Scope.GLOBAL, countries = setOf("GB"),
        lateLimits = listOf(
            LateLimit(
                6,
                "Wifaqul Ulama documents Zuhr at the meridian + 4 and Maghrib at sunset + 5; Manchester Central " +
                    "Mosque, the only table of theirs held, prints the meridian and sunset themselves, so against " +
                    "it Dhuhr runs up to 5 min and Maghrib up to 6 min late.",
                setOf(TimedEvent.DHUHR, TimedEvent.MAGHRIB),
            ),
        ),
    )

    /**
     * Manchester Central Mosque's table is printed for the mosque (gb-wifaqul.tsv; rulings R30, R40 and R44): within a
     * minute's reach of it (about 16 km) its point rides beside the user's, so the app is never before it there, nor
     * any end after it (tablesAtTheirPoints). On main, at the app's own towns around Manchester, a start came a minute
     * before it or an end after it on some days (the city-point audit of 10–11 Oct 2026: Ashton-under-Lyne, Stockport,
     * Sale, Altrincham). No figure is claimed, there or elsewhere.
     *
     * Past its bound (about 50 km) the table's own day still rides beside the user's, a day alongside, out to five of its
     * reaches (83 km: Leeds, Sheffield, Liverpool, Stoke, Derby, Blackpool), where Wifaqul is chosen on its own (the
     * owner's decision of 17 Oct 2026, question 1): there, on main, a user who chose it saw Fajr, Hanafi Asr and Isha
     * before Manchester Central's table on about 60 % of its printed days (Leeds' Fajr 5 min before it on all seven, Derby's
     * Isha 6) and the end of eating up to 4 min after it (Liverpool). As the UK's cautious times' most-followed member it
     * keeps its bound ([tablesAtTheirPoints]' memberRide of zero): its reach is the owner's question 2 of that day, accepted
     * at the bound for now until a spot-check of the boards there says whether the table is followed even that far
     * (Preston, Bradford, Huddersfield), and at those 86 towns the cautious times are already never before its table nor
     * any end after it on its printed days (the reviews of 17 Oct 2026: 602 place-days). Riding there too would make
     * Automatic's Maghrib up to 5 to 6 min later on most days of the year at Leeds, Wakefield and Castleford and carry the
     * table's knife-edge summer nights into it (Isha up to 85 min later on five to seven nights): the owner's call.
     */
    val wifaqulUnits: UnitSet = tablesAtTheirPoints(
        wifaqul.id, EntryClass.A, wifaqul.method!!,
        listOf(OwnTable("manchester-central", "Manchester Central Mosque", GeoPoint(53.4631, -2.2285))),
        measured = false, memberRide = 0.0,
    )

    /**
     * The UK's 15° family (Task 7g's survey of 59 Mawaqit tables outside London: 26 print a Fajr of
     * 14.6–15.4° in winter, in Birmingham, Manchester, Bradford, Halifax, Leicester and Glasgow), at the
     * family's latest starts and earliest sunrise: Fajr 14.6°, or later where the MWL Fiqh Council's
     * proportion from 45° gives a later one (their summer rule, applied while the sign still exists:
     * PlaceCurves); Isha 15°; Zuhr + 5 and Maghrib + 5 (Manchester and Glasgow), Asr + 3 and sunrise
     * − 7 (Glasgow); Birmingham's print about + 1 and 0.
     */
    private val fifteen = convention(
        "gb.fifteen", "timetable_fifteen_degrees",
        TimetableMethod(
            id = "gb.fifteen", fajrAngle = 14.6, isha = IshaRule.Angle(15.0),
            authorityMinutes = EventOffsets(sunrise = -7, dhuhr = 5, asr = 4, maghrib = 5),
            margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, dhuhr = 60 + SAFE_START),
        ),
        2,
    )

    /**
     * The Karachi 18°/18° of the Faizan-e-Madina (Barelvi) network and its like (5 of the 59 surveyed
     * tables: Faizan-e-Madina Bradford, Halifax and Stechford, Jamia Masjid Madni Halifax, Masjid Imam
     * al-Shafi'i Birmingham), at their latest: Zuhr + 1, Asr + 5 and Maghrib + 4 (Faizan-e-Madina
     * Bradford's).
     *
     * Its fast begins at the 18.6° dawn, and on a night where the sun does not get 18.6° low at the
     * middle of the night ([EndOfEating.DawnOrMiddle]; research-uk, ruling R75). A quarter of the 62
     * calendars research-uk surveyed (14 to 16: Faizan-e-Madina Bradford and Halifax, Faizan-e-Makkah,
     * Jamia Masjid Madni, Zia-ul-Quran, Sultan Bahu, Markaz Quba, Masjid Aisha, Mu'adh ibn Jabal and
     * five in Glasgow) print their Fajr, which is also when their fast begins, at the middle of the
     * night on the summer nights without 18°, where the earlier end of eating was up to 147 min after
     * it; 18.6° covers Faizan-e-Makkah's 18.44–18.58° dawn. Its Fajr start and every other time are
     * unchanged, so no start moves.
     */
    private val karachi = convention(
        "gb.karachi", "method_karachi",
        TimetableMethod(
            id = "gb.karachi", fajrAngle = 18.0, isha = IshaRule.Angle(18.0),
            authorityMinutes = EventOffsets(dhuhr = 1, asr = 5, maghrib = 4),
            margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, isha = 60 + SAFE_START),
            endOfEating = EndOfEating.DawnOrMiddle(UK_EATING_DAWN),
            endOfEatingMarginSeconds = SAFE_END,
        ),
        4,
    )

    /**
     * The UK's late-dawn family (research-uk, ruling R75): Fajr about 12.4° below the horizon in winter
     * and about 8° in summer ([UkLateDawnCurve]), printed by 9 of the 62 calendars surveyed outside
     * London (15 %): South Birmingham Central Masjid, Wythenshawe Masjid, Masjid Abdul Raheem, MahmudSabir
     * Al-Furqan Masjid, Zaytuna Masjid, Marwa Masjid, Leeds Grand Mosque, Ashton Jam'e Mosque and Muslim
     * Youth Foundation. Leeds Grand Mosque's summer Fajr, about 8°, is in it: left out it would be early
     * on 136 days by up to 15 min, and in it the curve costs about 3–10 min more in April to August.
     * Where the sun does not reach the curve (Orkney and Shetland in June and July), its Fajr is the
     * family's own June share of the night before sunrise, [LATE_DAWN_SHARE] (Leeds 0.185, Wythenshawe
     * 0.187), not the MWL proportion, which put it about 28 min before sunrise there. It leads on Fajr
     * alone: its Isha 15°, Zuhr + 5, Asr, Maghrib and sunrise are never the ones shown (UkCautiousTest).
     */
    private val lateDawn = convention(
        "gb.latedawn", "timetable_late_dawn",
        TimetableMethod(
            id = "gb.latedawn", fajrAngle = 12.0, isha = IshaRule.Angle(15.0),
            authorityMinutes = EventOffsets(dhuhr = 5),
            margins = safe,
            fajrAngleByDayOfYear = UkLateDawnCurve.fajr,
            highLatitude = HighLatRule.NightFraction(LATE_DAWN_SHARE, LATE_DAWN_SHARE),
        ),
        5,
    )

    /**
     * The UK outside London: no majority (Task 7g's Mawaqit survey; research-uk's survey of 71 Mawaqit
     * calendars, 62 of them used). Members, most-followed first: Wifaqul Ulama's 18°/15° (Deobandi,
     * Manchester Central); the 15° family (with Glasgow's Asr in Scotland, [variants]); MWL 18°/17°; the
     * Karachi 18°/18° family, whose fast begins at the middle of the night where 18.6° is not reached;
     * the late-dawn family. Each family printed by about a tenth of the calendars or more is a member
     * (spec §3.6, ruling R75). The school is not known (31 Hanafi calendars, 30 Standard, 1 alternating):
     * the later Asr (spec §3.7).
     *
     * Against every calendar at its own point, faults left out (UkMawaqitSurveyTest in tools/timetables):
     * no Fajr, Isha, Zuhr or Asr in the default school before it, and no sunrise or end of eating after
     * it, but for nine calendars whose cases no member absorbs at a fair cost for everyone, and the
     * calendars printing a later Maghrib than every member (Glasgow's, recorded under the 26 September
     * cap; Maghrib is now the latest member's, ruling R155). Those, and the threshold night where the
     * latest Isha meets the next end of eating (the night a member's Isha angle, 15°, 17° or 18°, is last
     * or first reached: up to two nights a year at a point, by up to 3 min, at some latitudes only;
     * UkCautiousTest), are
     * recorded in the proof (7g-europe-americas-africa-oceania.md, "The UK outside London") and the
     * survey's outliers, not as late limits: a late limit covers lateness, never an early start or a late
     * end.
     */
    val uk = cautious(
        id = "gb.cautious",
        members = listOf(wifaqul.asMember(1), fifteen, Generic.mwl.asMember(3), karachi, lateDawn),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.UNIT, countries = setOf("GB"), measured = false,
        named = { it != "gb.fifteen" && it != "gb.karachi" && it != "gb.latedawn" },
    )

    /** Glasgow's Asr after the exact time, in seconds, January to December ([variants]). */
    private val GLASGOW_ASR_SECONDS = intArrayOf(196, 391, 597, 237, 250, 280, 388, 473, 509, 531, 491, 368)

    /** Glasgow's Zuhr beyond the 15° family's, in seconds, January to December ([variants]). */
    private val GLASGOW_DHUHR_SECONDS = intArrayOf(0, 0, 0, 0, 0, 20, 0, 0, 0, 0, 0, 0)

    /**
     * Scotland ([Regions.scotland]; research-uk): the 15° family's Asr is Glasgow's. Its 11 calendars
     * print Asr 2–11 min after the exact time by season, in both schools (+ 9..11 in late autumn), where
     * the family's + 4 was up to 5 min early on six of them; so there its Asr minutes are monthly
     * ([GLASGOW_ASR_SECONDS]: each month's latest lateness over those calendars, less the printed
     * minute, plus 15 s, less our 30 s start margin). Its June Zuhr takes 20 s more (Masjid Bilal's
     * + 7.6 on one day). England, Wales and Northern Ireland keep the family's own minutes.
     */
    val variants = listOf(
        // Rabita Helsinki's calendar across the Helsinki region (the city-point round 2 review, the owner's rule: rather
        // late, never early): within 45 km of Helsinki its Isha a minute later and its end of eating two minutes earlier,
        // so no town is before the calendar's Isha (Sipoo, Kerava, Järvenpää) or after its end of eating (Lauttasaari,
        // Kaarela, Matinkylä, Olari, Espoonlahti, Nurmijärvi). From a minute's reach of Helsinki the calendar's own day at
        // Helsinki also rides beside the user's (rabitaRide, the owner's decision of 17 Oct 2026).
        MethodVariant("se.rabita", Circle(GeoPoint(60.1699, 24.9384), RABITA_REGION_KM)) { method ->
            method.copy(
                margins = method.margins.copy(isha = method.margins.isha + RABITA_REGION_ISHA_SECONDS),
                endOfEatingMarginSeconds = method.endOfEatingMarginSeconds - RABITA_REGION_EATING_SECONDS,
            )
        },
        MethodVariant("gb.fifteen", Regions.scotland) { method ->
            method.copy(
                authorityMinutes = method.authorityMinutes.copy(asr = 0),
                monthlyOffsets = mapOf(Prayer.ASR to GLASGOW_ASR_SECONDS, Prayer.DHUHR to GLASGOW_DHUHR_SECONDS),
            )
        },
    )

    // Ireland.

    /**
     * The Islamic Foundation of Ireland (Dublin Mosque; also Lucan, Finglas, Al-Khidmah): a fixed
     * table for 28 towns, Fajr about 11.3–16.4° and Isha 9.2–15.7° ([IfiCurve], its own table's curve),
     * sunrise 1–5 min early, Maghrib 0–6 min late, Standard Asr, up to 6 min after MWL's; Dhuhr about
     * ± 3. Its sunrise, Dhuhr, Asr and Maghrib move with the season: per-month offsets fitted on its 2026
     * Dublin table (Task 7g, seconds after the authority's minutes).
     *
     * The table is fixed: it prints the same month-day times every year, so every year is computed as
     * 2026, the year it was fitted on ([TimetableMethod.fixedTableYear]; track T4, 11 Oct 2026). Against
     * its own sun 2027 was a minute early on 52 starts (Maghrib 37, Asr 14, Dhuhr 1) and a minute late
     * on 29 ends against the table mapped onto 2027, and 2028, a leap year, by up to 70 s more; each
     * year now shows the table's own times, held to the date's own sun (review of track T4): where the
     * leap cycle moves the sun past the table, the table's sunrise would come after the real one (19 days
     * of March 2028) or its Maghrib on the minute the sun sets (41 days of March and April 2028, 24 of
     * February to April 2029), and the day shows the sun's minute instead, a minute from the table on the
     * safe side. The table mapped onto 2027–2029 checks that (gate rows, fit: the table re-dated, not
     * held-out years).
     */
    val ifi: RegistryEntry = single(
        id = "ie.ifi", nameKey = "authority_ifi", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ie.ifi", fajrAngle = 12.0, isha = IshaRule.Angle(15.0),
            authorityMinutes = EventOffsets(sunrise = -5, dhuhr = 3, asr = 6, maghrib = 6),
            margins = margins(start = 0, sunrise = 0, asr = 12),
            monthlyOffsets = mapOf(
                Prayer.SUNRISE to intArrayOf(156, 218, 263, 217, 181, 87, 33, 24, 55, 13, 19, 75),
                Prayer.DHUHR to intArrayOf(-113, -76, -54, -70, -90, -97, -110, -62, -52, -77, -93, -91),
                Prayer.ASR to intArrayOf(-321, -373, -347, -349, -310, -274, -235, -149, -121, -129, -165, -219),
                Prayer.MAGHRIB to intArrayOf(-262, -352, -333, -354, -305, -159, -69, 9, 16, -24, -69, -109),
            ),
            endOfEatingMarginSeconds = -54,
            fajrAngleByDayOfYear = IfiCurve.fajr, ishaAngleByDayOfYear = IfiCurve.isha,
            fixedTableYear = 2026,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("IE"),
    )

    /**
     * The Islamic Cultural Centre of Ireland (Clonskeagh): its site's code is adhan-js MWL with the
     * TwilightAngle high-latitude rule and Shafi'i Asr at 53.3498, −6.2603 (HIGH); Dhuhr + 1. The rule
     * keeps Fajr no earlier than 18/60 of the night before sunrise and Isha no later than 17/60 after
     * sunset whenever the sign occurs (curves, PlaceCurves). It prints no imsak, so the fast begins at
     * its Fajr: ruling R80, that rule's own dawn read as an end (PlaceCurves' ICCI end,
     * TwilightCurves.fajrAsEnd), in place of R39's 18° dawn, which came up to 86 min before its printed
     * Fajr where the sun only just reaches 18° (May and August). Margins fitted on its 2026 table (Task 7g:
     * January to June, widened by July to December's excess to the whole year's); the end of eating's
     * (+21 s) on January to June, with 0 late ends on July to December and none against adhan2's run of
     * its code (to the minute on every day of that table) in any year from 2025 to 2048, which allows up
     * to +27 s in every one of those years.
     */
    val icci: RegistryEntry = single(
        id = "ie.icci", nameKey = "authority_icci", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ie.icci", fajrAngle = 18.0, isha = IshaRule.Angle(17.0), authorityMinutes = EventOffsets(dhuhr = 1),
            margins = margins(start = -22, sunrise = 23, fajr = 21, asr = 59, maghrib = -23, isha = -17),
            highLatitude = HighLatRule.Legacy("angle"),
            endOfEatingMarginSeconds = 21,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("IE"),
        lateLimits = listOf(
            LateLimit(7, ICCI_RULE + " Fajr up to 7 min after its table.", setOf(TimedEvent.FAJR)),
            LateLimit(5, ICCI_RULE + " Isha up to 5 min after its table.", setOf(TimedEvent.ISHA)),
        ),
    )

    /** IFI's and the ICCI's tables are Dublin's (rulings R30, R44; the ICCI's code runs at 53.3498, −6.2603). */
    val dublinTables: List<UnitSet> = GeoPoint(53.3498, -6.2603).let { dublin ->
        listOf(pointTable(ifi, "Dublin", dublin), pointTable(icci, "Dublin", dublin))
    }

    /** Ireland: no majority; IFI's table and the ICCI's, both Standard Asr. */
    val ireland = cautious(
        id = "ie.cautious", members = listOf(ifi.asMember(1), icci.asMember(2)),
        school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.COUNTRY, countries = setOf("IE"), measured = true,
        named = { true },
        lateLimits = listOf(
            LateLimit(
                2,
                "Each member keeps its own margins (IFI's times at its table's minute, the ICCI's rounding): starts up to " +
                    "2 min after the later table's, sunrise up to 2 before the earlier's.",
                setOf(TimedEvent.FAJR, TimedEvent.SUNRISE, TimedEvent.ASR),
            ),
            LateLimit(
                3,
                "Maghrib is the later member's like every start (ruling R155), most days IFI's; each member keeps its own " +
                    "margins: up to 2 min after the later table's at the tables' point, and at the app's own Dublin, 2 km " +
                    "south-east of it, where the tables' point rides beside the user's (rulings R30, R44), up to 3.",
                setOf(TimedEvent.MAGHRIB),
            ),
            LateLimit(
                5,
                "Isha is the later member's; the ICCI's rule on the curve's slots (ruling R28) runs up to 5 min after its " +
                    "table in May.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                3,
                "The end of eating is the earlier member's: the ICCI's own rule read as an end (ruling R80), but for " +
                    "most of June, when IFI's own dawn is the earlier; the ICCI's, taken by month and day over the leap " +
                    "cycle, runs up to 3 min before the earlier of the two tables' Fajr in July and August.",
                setOf(TimedEvent.END_OF_EATING),
            ),
        ),
    )

    // France.

    /**
     * The Grande Mosquée de Paris. From January 2026 its table is the Moonsighting Committee's seasonal
     * Fajr (a curve, PlaceCurves; at 18° alone it was up to 110 min early) with Zuhr + 5, Maghrib + 3
     * and Isha = sunset + 90 (fitted, HIGH). From October its Mawaqit page still carries its earlier
     * method (Fajr about 18° tapering, Isha about 15.3–16°, Zuhr + 1, the same as its October 2025 rows),
     * so from October Isha is the later of the two, never before either. It prints no imsak, so the fast
     * begins at its Fajr: ruling R80, its own dawns read as an end (EndOfEatingDawns.gmpParis, the earliest
     * of its page's, its own September page's and its October 2025 rows' by month and day, so from October
     * its earlier method's), in place of R39's 18° dawn, which came up to 114 min before its printed Fajr in
     * June; not the seasonal curve, whose slots are widened late. Carried to another latitude by the
     * fraction of the night, those dawns can come after its rule there (southern Corsica in January), so
     * the end is also no later than the Moonsighting Committee's Fajr itself read as an end at the user's
     * latitude, and at the mosque across its table's 55 km reach (PlaceCurves' floor, R80's fix round):
     * within 4 min of its printed Fajr at the mosque rather than 2, and never after its rule within the reach.
     * Margins fitted by Task 7g on January to September 2026, widened by the held-out rows' excess. The
     * end's, +13 s, is its fit on January to September (+54) held to what adhan2's run of the Moonsighting
     * Committee's code (to the minute on 271 of those 273 rows) allowed on its own dawns in every year from
     * 2025 to 2048, +19 s, less the Fitter's 5 s safety and a second: a table's depressions carry its minute
     * rounding, and the leap cycle moves it. The floor allows more at the mosque, but +13 s is also the
     * end's across the reach, where it keeps the end at most 9 s after the rule's exact moment.
     */
    val gmp: RegistryEntry = single(
        id = "fr.gmp", nameKey = "authority_grande_mosquee_paris", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "fr.gmp", fajrAngle = 18.0, isha = IshaRule.AfterMaghrib(90),
            authorityMinutes = EventOffsets(dhuhr = 5, maghrib = 3),
            margins = margins(start = -23, sunrise = 22, fajr = -11, asr = 36, maghrib = 29, isha = 12),
            endOfEatingMarginSeconds = 13,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("FR"),
        lateLimits = listOf(
            LateLimit(
                16,
                "The GMP prints no imsak, so the fast begins at its own Fajr (ruling R80), within 4 min of it (the end " +
                    "holds for everyone within its table's reach, where its rule's dawn to the south comes up to 2 min " +
                    "earlier in June) but where its page changes method (the Moonsighting Committee's from January to " +
                    "September, its earlier method from October): each change is carried over the two days either " +
                    "side, so the end comes up to 16 min before its printed Fajr on 1 and 2 January and 8 before it on " +
                    "29 and 30 September.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                5,
                "From October the Grande Mosquée's page still carries its earlier method, whose Isha moves from " +
                    "about 15.3° to 16° over October; Isha is taken at the later of its 2026 method and 16°, never " +
                    "before either, and so runs up to 5 min after the earlier method's in early October.",
                setOf(TimedEvent.ISHA),
            ),
        ),
    )

    /** The Grande Mosquée de Paris's table is printed for the mosque (ruling R44; class D: about 55 km). */
    val gmpUnits: UnitSet = pointTable(gmp, "Paris", GeoPoint(48.8418, 2.3556))

    /**
     * France: no majority (224 Île-de-France Mawaqit mosques: the 12–13° family 40 %, the old GMP 14 %,
     * a flat 15° 12 %; Maghrib + 3/+4 at 78 %; Standard Asr at 96 %; Lyon's 14.5°/13.5° and
     * Marseille's 13°/13° lie inside the range). Members: the 12°/12° family (UOIF's 12°) with Zuhr +
     * 5 and Maghrib + 4, the GMP, the flat 15° family with its Asr up to 3 min after the exact one
     * (Al-Amel's, Task 7g), and Diyanet's European method (ruling R87: DITIB's mosques, as in Belgium,
     * the Netherlands and Germany; the Lyon DITIB calendar research-mawaqit surveyed prints it to the
     * angle, and against the three families alone its Isha was up to 11 min early, its Asr 2, its
     * imsak 22 and its sunrise, the sun's less 7, up to 7 min before the one shown). Checked at Paris
     * against one table of each of the three families (fr-cautious.tsv; Diyanet's Paris table is its
     * own entry's) and against 12 Lyon and Marseille calendars (ContinentalMawaqitSurveyTest). Maghrib is
     * the latest member's (ruling R155), Diyanet's + 7 (+ 5.5 to + 12.3 on the exact sun) where it is
     * later than the 12–13° family's + 4, as the Lyon DITIB calendar prints it.
     */
    val france = cautious(
        id = "fr.cautious",
        members = listOf(
            convention(
                "fr.twelve", "timetable_twelve_degrees",
                TimetableMethod(
                    id = "fr.twelve", fajrAngle = 12.0, isha = IshaRule.Angle(12.0),
                    authorityMinutes = EventOffsets(dhuhr = 5, maghrib = 4), margins = safe,
                ),
                1,
                measuredAt = listOf(GeoPoint(48.8418, 2.3556)),
            ),
            gmp.asMember(2),
            convention(
                "fr.fifteen", "timetable_fifteen_degrees",
                TimetableMethod(
                    id = "fr.fifteen", fajrAngle = 15.0, isha = IshaRule.Angle(15.0),
                    authorityMinutes = EventOffsets(dhuhr = 1, asr = 3), margins = safe,
                    endOfEatingMarginSeconds = FIFTEEN_END_MARGIN,
                ),
                3,
                measuredAt = listOf(GeoPoint(48.8418, 2.3556)),
            ),
            Diyanet.europe.asMember(4),
        ),
        school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.COUNTRY, countries = setOf("FR"), measured = true,
        named = { it == "fr.gmp" || it == "tr.diyanet.europe" },
        lateLimits = listOf(
            LateLimit(
                FRANCE_END,
                "The end of eating is the earliest member's: from late April to August the flat 15° family's 15° dawn, " +
                    "which Al-Amel's table (the family's one held) leaves for a later summer Fajr of its own, up to 29 min " +
                    "before the earliest printed Fajr of the three tables in June at Paris and up to " + FRANCE_END + " min " +
                    "before Diyanet's own Lille table's imsak in late May, when its takdir has moved its dawn later; the " +
                    "rest of the year Diyanet's table's dawn (its 18° and takdir, a member since ruling R87), up to " +
                    FRANCE_DIYANET_END + " min before the Grande Mosquée's own Fajr in April and August and about 12 in " +
                    "winter, and where the Grande Mosquée's page changes method its own dawn (ruling R80: 16 min on 1 and " +
                    "2 January, 8 on 29 and 30 September).",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                FRANCE_SUNRISE,
                "Sunrise is the earliest member's, Diyanet's (the sun's less 7): up to " + FRANCE_SUNRISE + " min " +
                    "before the three families' tables, which print the sun's own.",
                setOf(TimedEvent.SUNRISE),
            ),
            LateLimit(
                FRANCE_FAJR,
                "The 12–13° family prints Fajr from about 12° to 13° (Drancy's at 12.7°); its member takes 12°, the " +
                    "family's latest, never before any of them, so against Drancy's table Fajr runs up to 7 min late; " +
                    "from late May to June Diyanet's Paris table (its takdir, a member since ruling R87) is the latest, " +
                    "up to 16 min after Drancy's. Against Diyanet's own Lyon and Lille tables (their own units since the " +
                    "monitor round) the 12° family's Fajr is the later on most days, up to " + FRANCE_FAJR + " min after " +
                    "Diyanet's 18° at Lyon in autumn and winter.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                FRANCE_ISHA,
                "The flat 15° family prints Isha at 15° (in a Paris June after midnight), while the Grande Mosquée's " +
                    "is sunset + 90 and Drancy's and Al-Amel's summer Isha follow earlier rules of their own; Isha is " +
                    "the latest member's, so in summer it runs up to 38 min after the latest of those three tables at " +
                    "Paris, and up to " + FRANCE_ISHA + " min after Diyanet's own Lille table in June, whose takdir Isha " +
                    "is earlier still.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                FRANCE_ASR,
                FRANCE_SPREAD + " Asr (Diyanet's + 4 the latest) runs up to " + FRANCE_ASR + " min after the latest of " +
                    "the three tables held.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(
                6,
                "Maghrib is the latest member's like every start (ruling R155), Diyanet's sunset + 7 on its own Paris " +
                    "table's sun: at Paris up to 6 min after the latest of the three families' tables (the 12–13° " +
                    "family's and the Grande Mosquée's sunset + 3..4), in summer and early autumn; up to 1 after " +
                    "Diyanet's own Lyon and Lille tables.",
                setOf(TimedEvent.MAGHRIB),
            ),
            LateLimit(
                2,
                FRANCE_SPREAD + " Dhuhr runs up to 2 min after the latest of them.",
                setOf(TimedEvent.DHUHR),
            ),
        ),
    )

    // Belgium, the Netherlands, Germany.

    /**
     * The Executive of Muslims of Belgium: one yearly table for Brussels, Antwerp and Charleroi with
     * fixed town offsets (fitted, HIGH): 18°/18°, sunrise − 2, Dhuhr 0, Asr + 0..1, Maghrib + 2. From
     * May to July above 45° the MWL's proportion from 45°, with clock times at Brussels: Fajr the later
     * of 18° and the earlier of a clock-time floor and the proportion, Isha (to August) the earlier of 18°
     * and the later of a clock-time cap and its own proportion (PlaceCurves). The fast begins at its own earliest
     * dawns (ruling R39: EndOfEatingDawns). Margins fitted by Task 7g on January to June 2026, widened
     * by July to December's excess.
     */
    val emb: RegistryEntry = single(
        id = "be.emb", nameKey = "authority_emb", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "be.emb", fajrAngle = 18.0, isha = IshaRule.Angle(18.0),
            authorityMinutes = EventOffsets(sunrise = -2, maghrib = 2),
            margins = margins(start = -22, sunrise = 5, fajr = -1, asr = 40, maghrib = -24, isha = -7),
            endOfEatingMarginSeconds = 54,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("BE"),
        lateLimits = listOf(
            LateLimit(
                7,
                "From mid-May to 1 August EMB holds Fajr at one clock time, and its fast begins then; " +
                    "the curves carry a clock time as the sun's depression, which the day-either-side envelope " +
                    "(ruling R28) moves by a day's change: Fajr up to 7 min after EMB's, the end of eating up to " +
                    "7 min before it, in May.",
                setOf(TimedEvent.FAJR, TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                9,
                "From May to August EMB's Isha stops at a clock time (a few minutes later from July) or follows its own " +
                    "proportion from 45° (about 0.26 of the night in June); the curve takes the later of the " +
                    "clock time and the 45° proportion of 17.5°, never before EMB's, and runs up to 9 min after it in May.",
                setOf(TimedEvent.ISHA),
            ),
        ),
    )

    /** EMB's yearly table is printed for Brussels (ruling R44; class D: about 53 km). */
    val embUnits: UnitSet = pointTable(emb, "Brussels", GeoPoint(50.8467, 4.3525))

    /**
     * Belgium: EMB's times at 42 of 64 Brussels Mawaqit mosques; about 70 Diyanet mosques follow Diyanet.
     * Against 11 Mawaqit calendars across Antwerp, Ghent, Charleroi, Liège and Brussels at their own points
     * (ruling R87, ContinentalMawaqitSurveyTest): no start before them and no sunrise or end of eating
     * after them, but for what the survey leaves out (four calendars' Fajr column holds a congregation
     * time from February to October, after sunrise in summer) and records (Masjid Bilal Liège's Fajr,
     * Mawaqit's own 90 minutes before sunrise; Diyanet's Antwerp and Ghent tables, not held, whose summer
     * Fajr runs up to 5 min after the Brussels curve and the generic edge; two calendars' Isha at 18° or
     * the middle of the night in summer; and Maghrib, below).
     *
     * Maghrib (ruling R155, superseding R88's cap): the later member's, Diyanet's, sunset + 7 on its own
     * sun taken at 0h UT (+ 6 to + 9 on the exact sun), as the five Diyanet calendars of the survey print
     * it (Mawaqit's exact-sun rendering runs a minute after it on a few days), 2 to 9 min after EMB's own
     * sunset + 2. R88's other half stays: the EMB member is EMB's own entry (track L had given it
     * sunset + 7, through an edge hook, so that Diyanet's followers were never ahead; Diyanet's own member
     * now does that, and the hook stays gone).
     */
    val belgium = cautious(
        id = "be.cautious", members = listOf(emb.asMember(1), Diyanet.europe.asMember(2)),
        school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.COUNTRY, countries = setOf("BE"), measured = true,
        named = { true },
        lateLimits = listOf(
            LateLimit(
                4,
                "Fajr is the later member's; against Diyanet's Antwerpen and Gent tables (their own units since the " +
                    "monitor round) up to 4 min on the days its spring takdir moves Fajr in steps, and 2 on plain days " +
                    "where EMB's 18° dawn is the later.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                53,
                "Isha is the later member's, EMB's (18°, in summer its clock cap and proportion from 45°): up to 53 min " +
                    "after Diyanet's Antwerpen and Gent tables in July, whose takdir Isha is earlier.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                65,
                "The end of eating is the earlier member's, EMB's own dawns (ruling R39): up to 65 min before Diyanet's " +
                    "Antwerpen and Gent imsak in late April and May, when Diyanet's takdir has moved its dawn later.",
                setOf(TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /**
     * The Netherlands: no authority, at least four families (HIGH). Members: the shared Moroccan
     * calendar (22 of 34 Amsterdam Mawaqit mosques; Fajr 16.3° in winter to 14° in spring and
     * autumn, Isha 15.4° to 14.4°, sunrise − 2, Dhuhr + 1, Maghrib + 1..4), taken at its latest
     * angles; Diyanet (ISN); plain 18°/17°. The Awqati observation tables (Fajr about 40 min after
     * 18°) have no known share and are left to Task 7g.
     *
     * Maghrib (ruling R155): the latest member's, like every other start. The Moroccan calendar prints
     * sunset + 1..4 (its member takes the latest, [NL_MAGHRIB]), but 7 of the 12 Mawaqit calendars
     * research-mawaqit surveyed across Rotterdam, Den Haag, Utrecht and Amsterdam (Turkish and Arab
     * mosques alike) print Diyanet's Maghrib, sunset + 7 on Diyanet's own sun taken at 0h UT: sunset + 6
     * in spring to + 9 in autumn on the exact sun. Diyanet's member, at the later of its Amsterdam table's
     * point and the user's (ruling R15), gives that Maghrib on Diyanet's own daily sun and is the latest:
     * never before Diyanet's own Amsterdam table, and a minute before Mawaqit's exact-sun rendering of the
     * same rule on a few days (Mevlana Moskee Amsterdam, 8 days from January to April, recorded in the
     * survey as in Belgium and France). R87 had given the Moroccan member their Maghrib as a monthly floor
     * on the exact sun (+ 7 to + 9) while spec §3.6's cap followed the most-followed member; with the cap
     * gone the floor is gone too, and the Moroccan member is the calendar's own again.
     */
    val netherlands = cautious(
        id = "nl.cautious",
        members = listOf(
            convention(
                "nl.moroccan", "timetable_moroccan_calendar",
                TimetableMethod(
                    id = "nl.moroccan", fajrAngle = 14.0, isha = IshaRule.Angle(15.4),
                    authorityMinutes = EventOffsets(sunrise = -2, dhuhr = 1, maghrib = NL_MAGHRIB), margins = safe,
                ),
                1,
                measuredAt = listOf(GeoPoint(52.37403, 4.88969)),
            ),
            Diyanet.europe.asMember(2),
            Generic.mwl.asMember(3),
        ),
        school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.COUNTRY, countries = setOf("NL"), measured = true,
        named = { it != "nl.moroccan" },
        lateLimits = listOf(
            LateLimit(
                16,
                "The Moroccan calendar's Fajr moves from about 16° in winter to 14° in May and August; its member " +
                    "takes 14°, the calendar's latest, never before it, and so runs up to 16 min after it in autumn " +
                    "(Task 7g checked it at Amsterdam from 25 September to 25 October).",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                6,
                "Isha is the latest member's, MWL's 17°, whose own table is not held here: up to 6 min after the " +
                    "Moroccan calendar's and Diyanet's.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                2,
                "The members' own margins: the end of eating (Diyanet's earliest dawns) up to 2 min before the " +
                    "tables held.",
                setOf(TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /**
     * VIKZ's Fazilet Takvimi (about 300 of 2,342 German mosques): Diyanet's times with Isha 10 min
     * later, and separate imsak and sabah columns. No table of its own is held, and it is computed at
     * the user's point, so Diyanet's end-of-eating margin, fitted at Diyanet's own cities, is not
     * carried: the first-guess SAFE_END (ruling R44, R49 item 2).
     */
    val vikz: RegistryEntry = single(
        id = "de.vikz", nameKey = "authority_fazilet", entryClass = EntryClass.D_AUTHORITY,
        method = Diyanet.europeMethod.copy(
            id = "de.vikz", authorityMinutes = Diyanet.europeMethod.authorityMinutes.copy(isha = 10),
            endOfEatingMarginSeconds = minOf(Diyanet.europeMethod.endOfEatingMarginSeconds, SAFE_END),
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("DE"),
    )

    /**
     * Germany's late-dawn family (research-mawaqit, ruling R87): Fajr about 13° below the horizon from
     * September to April and about 10.3° in June ([DeLateDawnCurve]), printed by 2 of the 11 German
     * calendars surveyed, Hilaly Köln and Rahma Moschee München, Moroccan mosques whose winter Fajr was
     * up to 26 and 36 min after Diyanet's. Where the sun does not reach the curve (nowhere in Germany in
     * a common year; a spare), its Fajr is the family's own June share of the night before sunrise,
     * [DE_LATE_DAWN_SHARE]. It leads on Fajr alone: its Isha (Diyanet's generic 16°, else 22 % of the night,
     * on Diyanet's sun taken at 0h UT like the other members', so that on the night 16° is barely reached
     * all four agree it is not, where the exact sun put a 15° or 16° Isha at the sun's lowest point, an
     * hour or two after VIKZ's; VIKZ's + 10 then always passes it), Zuhr + 4, Asr, Maghrib + 4 and sunrise
     * − 2 are never the ones shown (ContinentalCautiousTest). The curve was derived on the exact sun; at
     * dawn, three hours from 0h UT, the two models differ by seconds. Germany's Maghrib stays Diyanet's
     * + 7, the latest member's (ruling R155).
     */
    private val deLateDawn = convention(
        "de.latedawn", "timetable_late_dawn",
        TimetableMethod(
            id = "de.latedawn", fajrAngle = 13.0, isha = Diyanet.europeMethod.isha, sunModel = Diyanet.europeMethod.sunModel,
            authorityMinutes = EventOffsets(sunrise = -2, dhuhr = 4, maghrib = 4),
            margins = safe,
            fajrAngleByDayOfYear = DeLateDawnCurve.fajr,
            highLatitude = HighLatRule.NightFraction(DE_LATE_DAWN_SHARE, Diyanet.europeMethod.ishaFraction()),
        ),
        3,
    )

    /**
     * Germany's 18° family (research-mawaqit, ruling R87): Pakistani and Afghan mosques whose fast
     * begins at the 18° dawn and, on the nights the sun does not get 18° low, at the middle of the night
     * (3 of the 11 calendars surveyed: Darul Aman and Pak Muhammad Jamia Masjid in Berlin print their
     * Fajr, which is also when their fast begins, at the middle of the night from May to July; Pak
     * Islami Majlis Hamburg at three tenths of the night), where Diyanet's takdir imsak was up to 140 min
     * after it. Its fast begins at the [DE_EATING_DAWN] dawn (Hamburg prints up to 18.35° in April and
     * August, Berlin's two up to 18.08°) or, where the sun does not get that low, at the middle of the
     * night ([EndOfEating.DawnOrMiddle], as the UK's Karachi family). Its times are Diyanet's European
     * method as VIKZ carries it, so that its starts tie with the members already there and never lead; on
     * those nights its own Fajr is the middle of the night, earlier than every other member's. It leads on
     * the end of eating alone (ContinentalCautiousTest).
     */
    private val deEighteen = convention(
        "de.eighteen", "timetable_eighteen_degrees",
        Diyanet.europeMethod.copy(
            id = "de.eighteen",
            highLatitude = HighLatRule.NightFraction(fajrFraction = 0.5, ishaFraction = Diyanet.europeMethod.ishaFraction()),
            endOfEating = EndOfEating.DawnOrMiddle(DE_EATING_DAWN),
            endOfEatingMarginSeconds = SAFE_END,
        ),
        4,
    )

    private fun TimetableMethod.ishaFraction(): Double = (highLatitude as HighLatRule.NightFraction).ishaFraction

    /**
     * Germany: Diyanet through DITIB (896 mosques) and IGMG (304, byte-identical in Berlin), about
     * 51 % before copies (17 of 21 Berlin Mawaqit mosques); VIKZ's Fazilet the next; the late-dawn and
     * 18° families of ruling R87 (each about a fifth of the 11 calendars surveyed outside the copies,
     * spec §3.6). Against those calendars at their own points (ContinentalMawaqitSurveyTest): no start
     * before them and no sunrise or end of eating after them, but for the cases the survey's outliers
     * record (the three DITIB and Pakistani calendars whose Fajr is Mawaqit's own minutes-before-sunrise
     * setting, Pak Muhammad Jamia's midnight Isha, Hamburg's Maghrib + 8).
     */
    val germany = cautious(
        id = "de.cautious", members = listOf(Diyanet.europe.asMember(1), vikz.asMember(2), deLateDawn, deEighteen),
        school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.COUNTRY, countries = setOf("DE"), measured = true,
        named = { it != "de.latedawn" && it != "de.eighteen" },
        lateLimits = listOf(
            LateLimit(
                DE_FAJR,
                "Fajr is the latest member's: the late-dawn family's 13° from September to April (ruling R87), up to " +
                    DE_FAJR + " min after Diyanet's Berlin table in early September, as its takdir ends, and about 30 " +
                    "in October; in summer VIKZ's, Diyanet's generic European method (VIKZ has no city curves of its " +
                    "own), no earlier than 19 % of the night, up to 12 min after the table.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                2,
                "Diyanet's European method's own margins (subtask 7b): sunrise up to 2 min before Diyanet's Berlin table.",
                setOf(TimedEvent.SUNRISE),
            ),
            LateLimit(
                DE_END,
                "The end of eating is the earliest member's, the 18° family's " + DE_EATING_DAWN + "° dawn (Hamburg's " +
                    "18.35°, ruling R87), up to " + DE_END + " min before Diyanet's Berlin imsak.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                11,
                "Isha is the later member's, VIKZ's: Diyanet's Isha + 10 min by VIKZ's own rule (no VIKZ table is " +
                    "held), up to 11 min after Diyanet's Berlin table.",
                setOf(TimedEvent.ISHA),
            ),
        ),
    )

    // Austria and Switzerland: Diyanet's European method as the national calendar.

    /**
     * Austria: IGGÖ's national calendar since 1 Jan 2023 is Diyanet's European method, printed city by
     * city (its PDFs are per location); Wien's equals Diyanet Wien (121 of 122 days). Wien's table is the
     * one held: a point table (ruling R44) on Diyanet's own Wien curves (subtask 7b: its takdir as its
     * table prints it), the other columns' margins fitted by Task 7g on IGGÖ's own Wien table (March,
     * May and June; July, September and December held out). Fajr, Isha and the end of eating keep
     * Diyanet's curve margins: since the monitor round a curve slot is a bound on Diyanet's own moment
     * in any year (Diyanet.CITY_CURVE_START), and IGGÖ's 2026 table, the year before Diyanet's held
     * rows, is that construction's cross-year check, with the same step lateness as Diyanet's own
     * cities. Other Austrian cities' tables are not held, so beyond Wien's reach the edge applies:
     * Diyanet's generic European method, a minute more.
     */
    val austria: RegistryEntry = single(
        id = "at.iggo", nameKey = "authority_iggo", entryClass = EntryClass.D_AUTHORITY,
        method = Diyanet.europeMethod.copy(
            id = "at.iggo", margins = margins(start = -17, sunrise = 31, fajr = -21, maghrib = -18, isha = -15),
            endOfEatingMarginSeconds = IGGO_END,
        ),
        school = AsrSchool.STANDARD, countries = setOf("AT"),
    )

    val austriaUnits: UnitSet = cityTable(
        austria, "Wien", GeoPoint(48.20849, 16.37208),
        diyanetCity("wien", "at.iggo.wien").copy(
            margins = margins(start = -17, sunrise = 31, fajr = Diyanet.CITY_CURVE_START, maghrib = -18, isha = Diyanet.CITY_CURVE_START),
        ),
        lateLimits = Diyanet.steps(4, 4, 4),
    )

    /**
     * Switzerland: FIDS's times equal Diyanet Zürich (31 of 31 days); majority not established. Zürich's
     * table is the one held: a point table (ruling R44) on Diyanet's own Zürich method, its curves (subtask
     * 7b) and, since the review of track T4 (11 Oct 2026), its margins too ([switzerlandUnits]). Beyond
     * Zürich's reach the edge applies: Diyanet's generic European method with the margins Task 7g fitted on
     * FIDS's own Zürich month, a minute more.
     */
    val switzerland: RegistryEntry = single(
        id = "ch.fids", nameKey = "authority_fids", entryClass = EntryClass.D_AUTHORITY,
        method = Diyanet.europeMethod.copy(
            id = "ch.fids", margins = margins(start = -20, sunrise = 33, asr = -23, maghrib = -23),
            endOfEatingMarginSeconds = FIDS_END,
        ),
        school = AsrSchool.STANDARD, countries = setOf("CH"),
    )

    /**
     * Zürich's unit is Diyanet Zürich's own method, margins and all (review of track T4, 11 Oct 2026): FIDS prints
     * Diyanet Zürich's table, and the margins Task 7g fitted on FIDS's one autumn month were, against Diyanet's
     * Zürich table for 2027 (FIDS's to come, ch-fids.tsv), a minute early at Maghrib on five days and a minute
     * late at sunrise on nine. Diyanet's own are green on both, as on Diyanet's own rows at the same point.
     */
    val switzerlandUnits: UnitSet = cityTable(switzerland, "Zürich", GeoPoint(47.36667, 8.55), diyanetCity("zurich", "ch.fids.zurich"))

    /**
     * A point table (rulings R30, R44) on its own [method] at [point]; beyond its reach the edge takes the
     * entry's own (Diyanet's generic European) method, as Diyanet's own edge does (ruling R45).
     */
    private fun cityTable(
        entry: RegistryEntry,
        name: String,
        point: GeoPoint,
        method: TimetableMethod,
        lateLimits: List<LateLimit> = emptyList(),
    ): UnitSet {
        val reach = lateReachKm(point.lat, entry.entryClass)
        return UnitSet(entry.id, listOf(AuthorityUnit(entry.id, name, point, reach, method, lateLimits = lateLimits))) { user ->
            requireNotNull(entry.method).beyondTable(entry.id + ".edge", point, user, reach)
        }
    }

    /** Diyanet's own city method at [key] (its table's curves, subtask 7b), under [id] so PlaceCurves keeps them. */
    private fun diyanetCity(key: String, id: String): TimetableMethod =
        requireNotNull(Diyanet.europeUnits.unit("tr.diyanet.europe.$key").method) { "no Diyanet city method $key" }.copy(id = id)

    // The Nordics.

    /**
     * Islamsk Råd Norge's joint calendar (bonnetid.info; Oslo's largest mosques, MEDIUM-HIGH): Fajr
     * 16°, Isha 15°, Maghrib + 3..6, Dhuhr + 4..10 (the latest here), both Asr printed; in summer Fajr at
     * sunrise − 60 then frozen at fixed times, and Isha moving to Maghrib + 40 by a ramp, city by city.
     * The fast begins at its own earliest dawns (ruling R39: EndOfEatingDawns). Beyond its calendars'
     * reach the edge keeps Fajr no earlier than an hour before sunrise from April to September.
     */
    val irn: RegistryEntry = single(
        id = "no.irn", nameKey = "authority_irn", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "no.irn", fajrAngle = 16.0, isha = IshaRule.Angle(15.0),
            authorityMinutes = EventOffsets(dhuhr = 10, maghrib = 6), margins = margins(start = SAFE_START, sunrise = -60 + SAFE_SUNRISE),
            endOfEatingMarginSeconds = 55,
        ),
        school = AsrSchool.HANAFI, scope = Scope.GLOBAL, countries = setOf("NO"),
        lateLimits = listOf(
            LateLimit(
                10,
                "IRN prints no imsak, so the fast begins at its Fajr; the end reads IRN's own earliest dawns (ruling " +
                    "R39), each the earliest of the day before, the day and the day after: where its Fajr moves " +
                    "fastest, in April as the summer rule begins, up to 10 min before its printed Fajr.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                6,
                "IRN's Fajr is each calendar's own curve (ruling R48), widened over the day before and after " +
                    "(ruling R28): when it moves fastest, in spring and autumn, up to 6 min after the calendar's.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                4,
                "IRN's Isha is each calendar's own curve (ruling R48), widened over the day before and after " +
                    "(ruling R28): up to 4 min after the calendar's.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                5,
                "IRN's Zuhr is + 4 in winter and + 9 from spring to autumn, changing within March and September; " +
                    "those two months take the later, up to 5 min after the calendar's before the change.",
                setOf(TimedEvent.DHUHR),
            ),
        ),
    )

    /**
     * The Tromsø unit's own late limits under IRN's rule (ruling R82), measured on its 2026 calendar.
     * The cells it declares not followed (a Fajr after the sun has risen, a sunrise that is the sun's
     * lowest point) are neither early nor late and are not in these figures. The window dates taken a
     * day early put the autumn blend's first anchor on 30 October, which in a year whose clocks go
     * back on 31 October (2027, 2032, 2038) is still summer time, so that blend's line on the wall
     * clock starts an hour later ([IrnArctic.windowShiftDays]): Fajr, Asr, Maghrib and Isha also carry
     * that November, measured as the widened rule's lead over the rule on its own dates through the
     * unit (up to 55, 33, 67 and 63 min) plus the unit's own lateness in the 2026 autumn blend (1, 3,
     * 4 and 2); no calendar of those years is held to check it against.
     */
    private val irnTromsoLimits = listOf(
        LateLimit(
            56,
            "IRN's window dates at Tromsø are read from its 2026 calendar alone, so until its 2027 calendar is held each " +
                "is taken a day early and a day late and the later start kept: on the Fajr blends' first and last days " +
                "(22 April, 21 August) Fajr runs up to 11 min after IRN's, and up to 5 in the winter blends. In a year " +
                "whose clocks go back on 31 October (2027, 2032, 2038) the day-early autumn blend starts on summer time: " +
                "up to 56 min after IRN's in early November, less than 10 from about 23 November.",
            setOf(TimedEvent.FAJR),
        ),
        LateLimit(
            42,
            "IRN prints noon as Tromsø's sunrise in the polar night and keeps it, or its own later sunrise, for some days " +
                "after the sun returns: there the sunrise shown (IRN's calculator's) is up to 42 min before IRN's in " +
                "January and 17 in November; elsewhere up to 4.",
            setOf(TimedEvent.SUNRISE),
        ),
        LateLimit(
            6,
            "IRN's Zuhr at Tromsø is noon + 5, + 10 from 21 March to 20 September; until its 2027 calendar is held the " +
                "change is taken a day early and a day late: up to 6 min after IRN's on 20 March and 21 September.",
            setOf(TimedEvent.DHUHR),
        ),
        LateLimit(
            36,
            "IRN's Asr at Tromsø is the later of the shadow and midway between noon and its own Maghrib less 5; with its " +
                "window dates taken a day either way until its 2027 calendar is held, up to 10 min after IRN's on the " +
                "Maghrib blends' first and last days (16 April, 28 August) and 7 in the winter blends. In a year whose " +
                "clocks go back on 31 October (2027, 2032, 2038) the day-early autumn blend starts on summer time: up to " +
                "36 in early November.",
            setOf(TimedEvent.ASR),
        ),
        LateLimit(
            313,
            "From mid-April to late August IRN's Maghrib at Tromsø is Makkah's sunset + 5, while the sun there sets hours " +
                "later: Maghrib never comes before the real sunset, up to 313 min after IRN's (mid-May and late July); in " +
                "the winter blends, with the window dates taken a day either way, up to 12, and in early November of a " +
                "year whose clocks go back on 31 October (2027, 2032, 2038), when the day-early blend starts on summer " +
                "time, up to 71.",
            setOf(TimedEvent.MAGHRIB),
        ),
        LateLimit(
            237,
            "Where IRN's Makkah-time Isha comes before the real sunset at Tromsø (late April to mid-August), Isha follows " +
                "the Maghrib shown, up to 237 min after IRN's; otherwise, with the window dates taken a day either way, up " +
                "to 12 on the Isha blends' edge days (23 April, 19-20 August) and 5 in the winter blends, and up to 65 in " +
                "early November of a year whose clocks go back on 31 October (2027, 2032, 2038), when the day-early blend " +
                "starts on summer time.",
            setOf(TimedEvent.ISHA),
        ),
        LateLimit(
            235,
            "IRN prints no imsak, so the fast begins at its Fajr. Where its Makkah-time Fajr at Tromsø falls after the sun " +
                "has risen (late April to mid-May and late July to mid-August; declared not followed) the fast begins at the " +
                "unit's own dawn before sunrise, up to 235 min before IRN's Fajr; elsewhere up to 5.",
            setOf(TimedEvent.END_OF_EATING),
        ),
    )

    /**
     * IRN's calendars for Oslo, Trondheim and Tromsø, each printed for its city (ruling R44), each with
     * its own end curve and end margin (fitted on its 2026 calendar). Ruling R48: Oslo's and Trondheim's
     * Fajr and Isha are their own calendars' curves ([IrnCurves]); their Zuhr (+ 4 in winter, + 9 from
     * March to September), Maghrib (+ 1 to + 6 by season) and sunrise (− 2 to + 2) follow the month
     * (monthly offsets fitted by Task 7g on each calendar, seconds after the authority's + 10 and + 6). IRN
     * rounds up (its Zuhr runs 0–60 s after the minute), so Fajr and Isha at their own calendar's minute
     * take no margin (a curve read from the same table would fit to − 54 s, exact on 2026 alone but not
     * across the rounding of other years).
     *
     * Tromsø's calendar follows IRN's Makkah-time rule for the far north ([IrnArctic], ruling R82): its
     * unit follows it wherever the sun allows (a Fajr it puts after the sun has risen, and its
     * midnight-sun sunrise, the sun's lowest point, are declared not followed), with the window dates
     * widened a day either way until IRN's 2027 calendar is held, its Maghrib base at + 5 and margins
     * fitted on its 2026 calendar. The rule is Tromsø's alone: beyond the unit the edge keeps the
     * calendar's plain method (16°/15° with the hour before sunrise from April to September), since
     * IRN's other Arctic calendars are not held.
     */
    val irnUnits: UnitSet = irn.method!!.let { m ->
        val tromsoPlain = m.copy(endOfEatingMarginSeconds = IRN_TROMSO_END, fajrAngleByDayOfYear = irnSummerFajr(69.6489))
        val tables = listOf(
            AuthorityUnit(
                "no.irn.oslo", "Oslo", GeoPoint(59.91273, 10.74609), lateReachKm(59.91273, irn.entryClass),
                m.copy(
                    margins = margins(start = 0, sunrise = 0, asr = -30),
                    monthlyOffsets = mapOf(
                        Prayer.DHUHR to intArrayOf(-361, -362, -63, -61, -61, -64, -61, -60, -65, -362, -362, -362),
                        Prayer.MAGHRIB to intArrayOf(-169, -169, -161, -164, -139, -74, -42, -44, -64, -91, -114, -145),
                        Prayer.SUNRISE to intArrayOf(-10, -31, -58, -72, -79, -65, -26, 12, 14, 18, 31, 22),
                    ),
                    endOfEatingMarginSeconds = IRN_OSLO_END,
                    fajrAngleByDayOfYear = IrnCurves.osloFajr, ishaAngleByDayOfYear = IrnCurves.osloIsha,
                ),
            ),
            AuthorityUnit(
                "no.irn.trondheim", "Trondheim", GeoPoint(63.43049, 10.39506), lateReachKm(63.43049, irn.entryClass),
                m.copy(
                    margins = margins(start = 0, sunrise = 0, asr = -18),
                    monthlyOffsets = mapOf(
                        Prayer.DHUHR to intArrayOf(-373, -373, -75, -73, -72, -76, -73, -72, -72, -374, -374, -377),
                        Prayer.MAGHRIB to intArrayOf(-85, -137, -171, -211, -250, -230, -137, -79, -69, -68, -64, -50),
                        Prayer.SUNRISE to intArrayOf(-98, -81, -72, -58, -41, 18, 106, 39, 8, -13, -63, -106),
                    ),
                    endOfEatingMarginSeconds = IRN_TRONDHEIM_END,
                    fajrAngleByDayOfYear = IrnCurves.trondheimFajr, ishaAngleByDayOfYear = IrnCurves.trondheimIsha,
                ),
            ),
            AuthorityUnit(
                "no.irn.tromso", "Tromsø", GeoPoint(69.6489, 18.95508), lateReachKm(69.6489, irn.entryClass),
                tromsoPlain.copy(
                    clockRule = IrnArctic(windowShiftDays = listOf(-1, 0, 1)),
                    authorityMinutes = m.authorityMinutes.copy(maghrib = IRN_TROMSO_MAGHRIB),
                    margins = margins(
                        start = IRN_TROMSO_START, sunrise = IRN_TROMSO_SUNRISE, dhuhr = IRN_TROMSO_DHUHR, asr = IRN_TROMSO_ASR,
                        maghrib = IRN_TROMSO_MAGHRIB_MARGIN, isha = IRN_TROMSO_ISHA,
                    ),
                    endOfEatingMarginSeconds = IRN_TROMSO_RULE_END,
                ),
                measured = true,
                lateLimits = irnTromsoLimits,
            ),
        )
        UnitSet("no.irn", tables) { user ->
            val nearest = tables.minBy { distanceKm(user, it.point) }
            // Beyond Tromsø's reach its calendar's rule and fitted margins stay behind (ruling R82).
            val own = if (nearest.id == "no.irn.tromso") tromsoPlain else nearest.method ?: m
            val method = own.copy(fajrAngleByDayOfYear = irnSummerFajr(user.lat), ishaAngleByDayOfYear = null, clockRule = null)
            method.beyondTable("no.irn.edge", nearest.point, user, nearest.radiusKm)
        }
    }

    /** 16°, or an hour before sunrise from April to September where that is later (IRN's summer rule). */
    private fun irnSummerFajr(latitude: Double): DoubleArray =
        TwilightCurves.fajr(latitude, 16.0) { d -> if (TwilightCurves.month(d) in 4..9) 60.0 / d.morningNight else null }

    /**
     * Rabita's Maghrib past its sunset + 3 and the 30 s start margin, in seconds, January to December (ruling R155; the
     * gate of 25 Oct 2026 and the Lead's review of the same day). Its Helsinki calendar's Maghrib is the sun at about
     * -1.22 degrees rounded to the minute (fitted, as ruling R42 records a derived parameter: the sun at -1.2183 degrees
     * plus 7 s, rounded, gives 354 of 2026's 365 printed minutes, the other 11 a minute either side), 2:40 to 6:00 min
     * after the exact sunset, the most near the solstices. The member's sunset + 3 alone ran up to 2 min before it in June
     * and July; the 26 September cap had hidden that (it followed Diyanet's later Maghrib), and it showed past Diyanet's
     * Helsinki unit, where the calendar's day rides beside the user's ([rabitaRide]): 5,286 early cells at the
     * envelope's points 42 to 69 km out. Each month takes the least half minute that keeps the member at or after the
     * calendar on every day of a leap cycle, 2026 to 2033, whether Mawaqit keeps the 2026 calendar as it is (it is
     * year-agnostic: the same month-day's minute every year) or regenerates it by its rule; a table fitted to 2026 alone
     * ran a minute before the calendar kept as it is on September and October days of 2028 and 2032. On its own at
     * Helsinki in 2026 the member is on the calendar's minute on 42 days, a minute after it on 195 (53 %) and two on 128
     * (35 %). A table by month rather than the calendar's own depression: Rabita's member reaches Lapland, where the sun
     * that sets at -0.83 degrees does not reach -1.22 near midsummer from about 65.3 to 65.7 degrees north, a band that
     * would have no Maghrib. Declared before [rabita], which reads it (an object initialises in order).
     */
    private val RABITA_MAGHRIB_SECONDS = intArrayOf(120, 60, 0, 30, 90, 120, 120, 90, 60, 60, 90, 90)

    /**
     * Rabita Helsinki's family (Mawaqit): 18°/17°, Zuhr + 2, Maghrib + 3..6 (its calendar's Maghrib month by month,
     * [RABITA_MAGHRIB_SECONDS], ruling R155). From April to August its Fajr
     * is near the middle of the night: its own calendar's Fajr is its curve ([RabitaCurves], as IRN's under
     * ruling R48), and, as it prints no imsak, the fast begins at that Fajr (ruling R39:
     * EndOfEatingDawns.rabitaHelsinki). While 17° does not occur its Isha is Maghrib + 88 (PlaceCurves; the
     * MWL member it replaces took the MWL Fiqh Council's estimate, a minute before Rabita's on 49 May days,
     * Task 7g); where the curve asks for 17° on a day the sun no longer reaches it, a quarter of the night
     * after sunset.
     */
    private val rabita = convention(
        "se.rabita", "timetable_rabita",
        TimetableMethod(
            id = "se.rabita", fajrAngle = 18.0, isha = IshaRule.Angle(17.0),
            authorityMinutes = EventOffsets(dhuhr = 2, maghrib = 3), margins = safe,
            monthlyOffsets = mapOf(Prayer.MAGHRIB to RABITA_MAGHRIB_SECONDS),
            highLatitude = HighLatRule.NightFraction(fajrFraction = 1.0 / 7.0, ishaFraction = 0.25),
            fajrAngleByDayOfYear = RabitaCurves.fajr,
            endOfEatingMarginSeconds = RABITA_END,
        ),
        2,
        measuredAt = listOf(GeoPoint(60.1699, 24.9384)),
    )

    /**
     * Rabita's Helsinki calendar beyond Helsinki's own neighbourhood (the owner's decision of 17 Oct 2026, "rather late,
     * never early"): from a minute's reach of Helsinki (13.8 km) out to [RIDE_REACHES] of them (69 km), the calendar's own
     * day rides beside the user's, a day alongside ([world.taqwa.app.prayer.engine.method.Alongside]): the member's own day
     * at the user's point exactly as before (inside the [variants]' 45 km region with its wider margins), and its day at
     * Helsinki itself, read with Helsinki's own curves and the region's margins, as the calendar's own point shows it; the
     * day shown is their safe side. Past Diyanet's Helsinki unit (class D's reach, 41.5 km) that day's Asr is also
     * [RABITA_RIDE_ASR_SECONDS] later: at Helsinki the member's own Standard Asr comes up to a minute before the calendar's
     * on some winter days, where Diyanet's later Asr, its Helsinki unit's, holds the cautious day; past that unit Diyanet's
     * is the user's own, and the member alone must hold the calendar.
     *
     * On main, every one of the app's towns in the region was clean against the calendar, but points between them were not
     * (rings of 24 points, every day of 2026): Isha up to 1 to 9 min before it from 14 km out (on land to the east from
     * 30 km), and Fajr, Asr and the end of eating from 38 to 42 km; past the region, with Helsinki's point beside the
     * user's (the first build of this decision, which read the user's curves and the member's own end-of-eating margin at
     * Helsinki), 4 km north of Lohja the end of eating up to 4 min after the calendar's (the reviews of 17 Oct 2026). Within
     * a minute's reach the region's margins hold every point on land (main's Isha a minute or two before the calendar at 8
     * to 13.7 km lies on the sea to the south). Helsinki's own Isha runs up to 156 min after the calendar on the days its
     * rule changes (22–23 April, 19–20 August), and the ride carries that to the towns whose own rule has already changed,
     * up to 97 min later than main at Järvenpää. No figure is claimed past class C's reach of Helsinki (measured as
     * before). Within a minute's reach, and beyond the ride, Rabita's method at the user's own point, as before.
     */
    val rabitaRide: UnitSet = UnitSet(rabita.id, emptyList(), measuredBeyond = true) { user ->
        if (!rabitaRides(user)) {
            rabita.method
        } else {
            val atHelsinki = if (distanceKm(user, RABITA_HELSINKI) > diyanetHelsinkiKm) rabitaHeldAlone else rabita.method
            rabita.method.copy(alongside = listOf(Alongside(RABITA_HELSINKI, atHelsinki)))
        }
    }

    /** Rabita's own day at Helsinki past Diyanet's Helsinki unit ([rabitaRide]): its Asr [RABITA_RIDE_ASR_SECONDS] later. */
    private val rabitaHeldAlone: TimetableMethod =
        rabita.method.copy(margins = rabita.method.margins.copy(asr = rabita.method.margins.asr + RABITA_RIDE_ASR_SECONDS))

    /** How far Diyanet's Helsinki unit reaches, within which its Asr holds Rabita's calendar's in the cautious day. */
    private val diyanetHelsinkiKm: Double by lazy { Diyanet.europeUnits.unit("tr.diyanet.europe.helsinki").radiusKm }

    /** Rabita's Helsinki calendar's point (se-cautious.tsv's Helsinki rows). */
    internal val RABITA_HELSINKI = GeoPoint(60.1699, 24.9384)

    /** Where Rabita's calendar starts to ride ([rabitaRide]): a minute's reach of Helsinki. */
    internal val RABITA_RIDE_FROM_KM: Double = lateReachKm(RABITA_HELSINKI.lat, EntryClass.A)

    /** How far from Helsinki Rabita's calendar rides ([rabitaRide]): [RIDE_REACHES] of a minute's reach there. */
    internal val RABITA_RIDE_KM: Double = RIDE_REACHES * RABITA_RIDE_FROM_KM

    /** Whether Rabita's calendar rides beside [user] ([rabitaRide]): past a minute's reach of Helsinki, within [RABITA_RIDE_KM]. */
    internal fun rabitaRides(user: GeoPoint): Boolean =
        distanceKm(user, RABITA_HELSINKI).let { it > RABITA_RIDE_FROM_KM && it <= RABITA_RIDE_KM }

    /**
     * Norway (ruling R64): no national authority. IRN's joint calendar (Oslo's largest mosques, the most
     * followed) and Diyanet's European method (the Turkish and Bosnian mosques). The school is not known:
     * the later Asr.
     */
    val norway = cautious(
        id = "no.cautious", members = listOf(irn.asMember(1), Diyanet.europe.asMember(2)),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.COUNTRY, countries = setOf("NO", "SJ"),
        measured = true, named = { true },
        lateLimits = listOf(
            LateLimit(
                101,
                "Fajr is the later member's. At IRN's own calendars Diyanet's European takdir on its own city curves: up " +
                    "to 54 min after IRN's Trondheim calendar in June and 48 after its Oslo calendar. Beyond them, at " +
                    "Diyanet's Kristiansand, Stavanger and Bergen tables (their own units since the monitor round), IRN's " +
                    "edge (its hour before sunrise from April to September, else 16°) is the later: up to 101 min after " +
                    "Diyanet's 18° in spring and autumn.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                135,
                "Isha is the later member's. At IRN's own calendars Diyanet's European takdir on its own city curves: up " +
                    "to 34 min after IRN's calendars (Trondheim's in spring, Oslo's 29). Beyond them, at Diyanet's " +
                    "Kristiansand, Stavanger and Bergen tables, IRN's edge (15°, else the proportion from 45°) is the " +
                    "later: up to 135 min after Diyanet's takdir Isha in spring and summer.",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                57,
                "The end of eating is the earlier member's: IRN's own earliest dawns (ruling R39) at Oslo, up to 55 min " +
                    "before Diyanet's imsak and 26 before IRN's Fajr; at Trondheim Diyanet's own table's dawn, up to 55 " +
                    "before IRN's printed Fajr in spring; at Bergen up to 57 before Diyanet's imsak in August.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                58,
                "Sunrise is the earlier member's, the sun's own less Diyanet's 7 (IRN's is up to 2 min before the sun's): " +
                    "up to 10 min before IRN's Trondheim sunrise, and from late May to late July up to 58 min before " +
                    "Diyanet's own Trondheim table, which holds the day to about nineteen hours around Dhuhr and prints " +
                    "its sunrise up to 53 min after the sun's (the app keeps the sun's, the safe side).",
                setOf(TimedEvent.SUNRISE),
            ),
            LateLimit(
                8,
                "Asr is the later member's, in the later school (the school is not known): up to 8 min after IRN's " +
                    "Trondheim Asr.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(
                6,
                "Zuhr is the later member's (IRN's + 4 to + 9, Diyanet's + 5): up to 5 min after the tables', and beyond " +
                    "IRN's calendars, where its edge keeps + 10, up to 6 after Diyanet's Kristiansand table.",
                setOf(TimedEvent.DHUHR),
            ),
            LateLimit(
                50,
                "Maghrib is the later member's from the sun like every start (ruling R155), most days Diyanet's sunset + 7: " +
                    "up to 5 min after IRN's Oslo calendar and 6 after its Trondheim calendar, and up to 4 after Diyanet's " +
                    "own Bergen table and 1 after its Oslo, Kristiansand and Stavanger tables. From late May to late July " +
                    "Diyanet's own Trondheim table holds the day to about nineteen hours around Dhuhr and prints its Maghrib " +
                    "before the sun sets; Maghrib never comes before the sun's, up to 50 min after that table (the safe " +
                    "side). On the winter days Diyanet's five-hour day holds its Trondheim Maghrib later than the sun, its " +
                    "Maghrib from the sun is the one checked (ruling R156).",
                setOf(TimedEvent.MAGHRIB),
            ),
        ),
    )

    /**
     * Sweden, Denmark, Finland and Iceland (ruling R64): no national authority. Diyanet's European method
     * (Islamiska Förbundet in Stockholm, HBKCC in Copenhagen) and Rabita Helsinki's 18°/17° with its summer
     * Isha. The school is not known: the later Asr.
     */
    val nordics = cautious(
        id = "se.cautious", members = listOf(Diyanet.europe.asMember(1), rabita),
        school = AsrSchool.HANAFI, schoolKnown = false, scope = Scope.COUNTRY, countries = setOf("SE", "DK", "FI", "IS", "FO", "AX", "GL"),
        measured = true, named = { it != "se.rabita" },
        lateLimits = listOf(
            LateLimit(
                111,
                "Fajr is the later member's, Diyanet's European takdir on its own city curves (Helsinki's and " +
                    "Copenhagen's since the monitor round): up to 109 min after Rabita's Helsinki calendar in summer, " +
                    "and 111 at the towns of the Helsinki region its calendar is held at; at Stockholm 6. Rabita's own " +
                    "Fajr follows its calendar.",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                170,
                "Isha is the later member's, Rabita's own model: 17° while the sun reaches it, near midnight in late " +
                    "spring and late summer, up to 155 min after Rabita's calendar at Helsinki and 136 after Diyanet's " +
                    "Stockholm table, 170 after Diyanet's Luleå table in April and September (its takdir Isha comes " +
                    "earlier the further north).",
                setOf(TimedEvent.ISHA),
            ),
            LateLimit(
                154,
                "The end of eating is the earlier member's: Rabita's own earliest dawns, near the middle of the night " +
                    "from April to August (ruling R39), applied at Copenhagen and Stockholm, up to 137 and 136 min before " +
                    "Diyanet's imsak there, and up to 154 before its Malmö and Aarhus tables in August; at Helsinki up to " +
                    "25 min before Rabita's own Fajr.",
                setOf(TimedEvent.END_OF_EATING),
            ),
            LateLimit(
                7,
                "Asr is the later member's, in the later school (the school is not known): Diyanet's, up to 7 min after " +
                    "Rabita's at Helsinki.",
                setOf(TimedEvent.ASR),
            ),
            LateLimit(
                129,
                "Sunrise is the earlier member's, Diyanet's (the sun's less 7): up to 6 min before Rabita's at Helsinki, " +
                    "and from late May to late July, where Diyanet holds its tables' day to nineteen hours around Dhuhr " +
                    "and prints its sunrise after the sun's, up to 11 min before its Helsinki table and 129 before its " +
                    "Luleå table (the app keeps the sun's, the safe side).",
                setOf(TimedEvent.SUNRISE),
            ),
            LateLimit(
                5,
                "Zuhr is the later member's, Diyanet's (+ 5): up to 5 min after Rabita's at Helsinki.",
                setOf(TimedEvent.DHUHR),
            ),
            LateLimit(
                126,
                "Maghrib is the later member's from the sun like every start (ruling R155), most days Diyanet's sunset + 7: " +
                    "up to 7 min after Rabita's Helsinki calendar (sunset + 3 to + 6) at Helsinki and 8 at its towns, and up to " +
                    "1 after Diyanet's own tables. From late May to late July, where Diyanet holds its tables' day to " +
                    "nineteen hours around Dhuhr and prints its Maghrib before the sun sets, Maghrib never comes before the " +
                    "sun's: up to 4 min after its Turku table, 17 after Tampere's, 32 after Sundsvall's, 59 after Umeå's, " +
                    "94 after Oulu's and 126 after Luleå's.",
                setOf(TimedEvent.MAGHRIB),
            ),
        ),
    )

    val entries = listOf(
        london, wifaqul, uk, ifi, icci, ireland, gmp, france, emb, belgium, netherlands, vikz, germany,
        austria, switzerland, irn, norway, nordics,
    )

    /**
     * IGGÖ's and FIDS's end-of-eating margins on Diyanet's generic European method (their entries' own
     * methods; at Wien and Zürich their units take Diyanet's city curves with their own margins).
     */
    private const val IGGO_END = 5

    private const val FIDS_END = 10

    /**
     * Rabita's end margin (Task 7g): where its printed Fajr lies within a quarter degree of the sun's lowest
     * point (late April to August), the end curve stops that quarter degree short so that it is always
     * reached (TwilightCurves.endOfEating), which falls up to 4 min after the printed Fajr; the margin keeps
     * every end before it.
     */
    private const val RABITA_END = -300

    /**
     * Rabita's Helsinki region ([variants], the city-point round 2 review): how far from Helsinki its calendar is held by
     * the wider margins, and by how much. Before them (as on main) the app's Sipoo had Isha a minute before the calendar on
     * 84 days, Kerava 14 and Järvenpää 18, and Espoonlahti, Matinkylä, Lauttasaari, Kaarela, Olari and Nurmijärvi the end of
     * eating 1 to 2 min after it on 3 to 31 days.
     */
    private const val RABITA_REGION_KM = 45.0
    private const val RABITA_REGION_ISHA_SECONDS = 60
    private const val RABITA_REGION_EATING_SECONDS = 120

    /** Rabita's own day at Helsinki past Diyanet's Helsinki unit ([rabitaRide]): its Asr this much later, so the member alone holds the calendar. */
    private const val RABITA_RIDE_ASR_SECONDS = 60

    /** Why the ICCI's Fajr and Isha run late (Task 7g's gate, proof.md). */
    private const val ICCI_RULE = "The ICCI's code keeps Fajr no earlier than 18/60 of the night before sunrise and Isha no " +
        "later than 17/60 after sunset (adhan-js's TwilightAngle rule); on the curve's slots each is widened over the day " +
        "before and after (ruling R28), most when the rule takes over, in late April and May:"

    /** Why France's cautious Dhuhr and Asr run late (Task 7g's gate). */
    private const val FRANCE_SPREAD = "Each family's tables differ among themselves (Drancy's and Al-Amel's Asr swing by " +
        "3-5 min over the year; the 12-13° family prints Zuhr + 5 and Maghrib + 3..4); each member takes its family's latest."

    /**
     * The flat 15° family's end margin (ruling R80): with the Grande Mosquée's end at its own dawn, this
     * family's 15° dawn is France's earliest from spring to late summer; fitted with the end formula (5 s
     * safety) on Al-Amel's 2026 table as the gate reads it, at the Grande Mosquée's point, 13 km west of the
     * mosque, where its dawn comes up to 40 s later than the mosque's own.
     */
    private const val FIFTEEN_END_MARGIN = -34

    /** The UK Karachi family's end-of-eating dawn: Faizan-e-Makkah prints 18.44–18.58° (research-uk). */
    private const val UK_EATING_DAWN = 18.6

    /** The Dutch Moroccan member's Maghrib minutes: the calendar's own latest (it prints + 1..4; ruling R155). */
    private const val NL_MAGHRIB = 4

    /**
     * France's late limits with Diyanet as a member (ruling R87; measured by the gate at Paris, and since
     * the monitor round against Diyanet's own Lyon and Lille tables, where the families' later Fajr and
     * Isha and the earliest end stand against Diyanet's 18° and takdir).
     */
    private const val FRANCE_SUNRISE = 8
    private const val FRANCE_ASR = 4
    private const val FRANCE_FAJR = 56
    private const val FRANCE_ISHA = 88
    private const val FRANCE_END = 77
    private const val FRANCE_DIYANET_END = 26

    /** Germany's late-dawn family's June share of the night before sunrise (Hilaly 0.206, Rahma 0.213). */
    private const val DE_LATE_DAWN_SHARE = 0.206

    /** Germany's Fajr late limit: the late-dawn family's 13° against Diyanet's Berlin table as its takdir ends. */
    private const val DE_FAJR = 44

    /** Germany's end-of-eating limit: the 18° family's 18.4° dawn against Diyanet's Berlin imsak. */
    private const val DE_END = 5

    /** Germany's 18° family's end-of-eating dawn: Pak Islami Majlis Hamburg prints up to 18.35° (research-mawaqit). */
    private const val DE_EATING_DAWN = 18.4

    /**
     * The UK late-dawn family's Fajr as a share of the night before sunrise where the sun does not reach
     * its curve: its own June share (research-uk).
     */
    private const val LATE_DAWN_SHARE = 0.185

    /** IRN's end margins, fitted on each calendar (the end formula). */
    private const val IRN_OSLO_END = 55
    private const val IRN_TRONDHEIM_END = 55
    private const val IRN_TROMSO_END = 56

    /**
     * IRN's Tromsø calendar under its rule (ruling R82): its Maghrib base (sunset + 5, in minutes) and
     * our margins in seconds, fitted on its 2026 calendar under the rule's own 2026 dates by the plan's
     * formula (the Fitter's, safety 5 s): a start's is the least that is never early + 1 + 5 s, an
     * end's the least that is never late − 1 − 5 s (IRN's noon runs 22 s after the engine's transit at
     * the unit's point, which every start margin carries). Asr's is the Hanafi column's, the later of
     * the two; the end of eating's reads IRN's Fajr as an end. Fajr keeps 35 s, more than the fit's
     * 17, on the safe side. Sunrise takes none: its margin also moves the sun's own sunrise, which the
     * sunrise shown never passes (the fit would allow + 77 s against IRN's calculator).
     */
    private const val IRN_TROMSO_MAGHRIB = 5
    private const val IRN_TROMSO_START = 35
    private const val IRN_TROMSO_SUNRISE = 0
    private const val IRN_TROMSO_DHUHR = 29
    private const val IRN_TROMSO_ASR = 75
    private const val IRN_TROMSO_MAGHRIB_MARGIN = 42
    private const val IRN_TROMSO_ISHA = 83
    private const val IRN_TROMSO_RULE_END = 17
}
