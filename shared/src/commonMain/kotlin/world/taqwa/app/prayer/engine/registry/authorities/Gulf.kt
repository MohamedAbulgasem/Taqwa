package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.ADHAN2_ASR_ALLOWANCE
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/** The UAE, Qatar, Kuwait, Bahrain and Oman (research: authorities-arab-world-turkiye.md, profiles-tested.md data-gulf-egypt). */
object Gulf {

    // UAE.

    /**
     * Dubai: IACAD's perpetual zone tables for Dubai, Dubai Rural and Hatta (2022–2025 identical by
     * date; HIGH), about 18.2°/18.2°, Shafi'i Asr; no end of eating is printed, so the fast begins at
     * its Fajr.
     *
     * Margins (Task 7a, ae-iacad.tsv), each zone's own, fitted on its captured spreadsheets (Dubai
     * and Dubai Rural 2022 to June 2025, a whole leap cycle; Hatta 2023 to June 2025) with the
     * Conventions' formulas and 5 s, and held out on the same perpetual table mapped onto 2026–2029,
     * the years the app shows: 0 early and 0 late ends, at most 2 min at each zone's point. Dubai:
     * Fajr −10, sunrise −209 (shown about 3.5 min before raw), Dhuhr +162, Asr +60, Maghrib +216,
     * Isha +24, end of eating −14. Class B.
     */
    val dubaiMethod = TimetableMethod(
        id = "ae.iacad.dubai",
        fajrAngle = 18.2,
        isha = IshaRule.Angle(18.2),
        margins = margins(start = 0, sunrise = -209, fajr = -10, dhuhr = 162, asr = 60, maghrib = 216, isha = 24),
        endOfEatingMarginSeconds = -14,
    )

    private val dubaiZoneEdge = dubaiMethod.margins.widened(60)

    /** Hatta's own table (later than Dubai's method at Hatta at Fajr, earlier at Isha), at Hatta. */
    private val hattaMethod = dubaiMethod.copy(
        id = "ae.iacad.hatta",
        margins = margins(start = 0, sunrise = -227, fajr = 45, dhuhr = 118, asr = 18, maghrib = 229, isha = -24),
        endOfEatingMarginSeconds = 39,
    )

    /**
     * Dubai Rural's own table. Its point is not published: 24.66° N is the latitude whose sun fits
     * it best (every event's band 107–122 s over 2022–2025 there, against 138–153 s at 24.90° N; the
     * longitude only shifts every time alike, which the margins absorb), carried as the fixed point.
     */
    private val ruralMethod = dubaiMethod.copy(
        id = "ae.iacad.dubai.rural",
        margins = margins(start = 0, sunrise = -241, fajr = 4, dhuhr = 111, asr = 8, maghrib = 209, isha = -35),
        endOfEatingMarginSeconds = 12,
        fixedPoint = GeoPoint(24.66, 55.60),
    )

    /**
     * Each zone's table is the one its whole zone follows, and the engine also keeps the user's own
     * sun (spec §3.5), so across a zone the times run later than at its point: checked at eight
     * towns from Jebel Ali to Al Mizhar, Dubai stays within 2 min but for the end of eating in the
     * city's north-east in June (3 min early on 8 of 1,461 days at Al Mizhar, where the dawn is
     * about 50 s before the Dubai point's). Dubai Rural, whose table fits no point to the minute,
     * is class D: within 2 min at its point; at Lahbab, its north, 3 min at Isha on 33 summer days
     * of its captured and mapped years and at the end of eating on 1.
     */
    val dubaiUnits = UnitSet(
        "ae.iacad.dubai",
        listOf(
            AuthorityUnit(
                "dubai", "Dubai", GeoPoint(25.07725, 55.30927), 30.0,
                lateLimits = listOf(
                    LateLimit(
                        3,
                        "Dubai's one table serves the whole city, and Taqwa also keeps each place's own dawn, " +
                            "about a minute earlier in the city's north-east in June",
                        setOf(TimedEvent.END_OF_EATING),
                    ),
                ),
            ),
            AuthorityUnit("dubai-rural", "Dubai Rural", GeoPoint(24.80, 55.60), 30.0, method = ruralMethod, entryClass = EntryClass.D_AUTHORITY),
            AuthorityUnit("hatta", "Hatta", GeoPoint(24.80073, 56.12726), 12.0, method = hattaMethod),
        ),
    ) { dubaiMethod.atEdge("ae.iacad.dubai.edge", dubaiZoneEdge) }

    val dubai: RegistryEntry = single(
        id = "ae.iacad.dubai", nameKey = "authority_iacad", entryClass = EntryClass.B, method = dubaiMethod,
        school = AsrSchool.STANDARD, scope = Scope.CITY, countries = setOf("AE"),
    )

    /**
     * The other emirates: the federal Awqaf (GAIAE), about 60 zones with elevation for mountain tops,
     * IACAD's 18.2°/18.2° as far as one day shows. Jebel Jais and Jebel Hafeet are computed at a −2°
     * horizon (Jebel Jais' sunrise 5 min earlier and Maghrib 4 min later than Ras Al Khaimah city that
     * day; not measured).
     *
     * Margins (Task 7a, ae-awqaf.tsv): its one day read (25 Sep 2026, ten areas at the app's points)
     * prints Asr, Maghrib and Isha about a minute before IACAD's method there. So: that day's
     * never-early fit with the Conventions' 5 s (Fajr −14, sunrise −222, Dhuhr +144, Asr +6, Maghrib
     * +159, Isha −38, end of eating +22), plus how much more IACAD's own tables need over the year
     * than on its 23–27 September days (Fajr +15, sunrise −22, Dhuhr +14, Asr +19, Maghrib +1, Isha 0,
     * end of eating −25), plus 60 s for its unknown zone points. That day: 0 early, at most 3 min.
     * Class D (one day of data). Abu Dhabi's 2013–2014 tables are an older method and prove nothing.
     */
    val awqafMethod = dubaiMethod.copy(
        id = "ae.awqaf",
        margins = margins(start = 0, sunrise = -304, fajr = 61, dhuhr = 218, asr = 85, maghrib = 220, isha = 22),
        endOfEatingMarginSeconds = -63,
    )

    val awqafUnits = UnitSet(
        "ae.awqaf",
        listOf(
            AuthorityUnit("jebel-jais", "Jebel Jais", GeoPoint(25.95, 56.15), 6.0, awqafMethod.copy(id = "ae.awqaf.jebel-jais", horizonDeg = -2.0), measured = false),
            AuthorityUnit("jebel-hafeet", "Jebel Hafeet", GeoPoint(24.06, 55.77), 5.0, awqafMethod.copy(id = "ae.awqaf.jebel-hafeet", horizonDeg = -2.0), measured = false),
        ),
    ) { awqafMethod }

    val awqaf: RegistryEntry = single(
        id = "ae.awqaf", nameKey = "authority_awqaf_uae", entryClass = EntryClass.D_AUTHORITY, method = awqafMethod,
        school = AsrSchool.STANDARD, countries = setOf("AE"), scope = Scope.UNIT, nearby = listOf("ae.iacad.dubai"),
    )

    // Qatar.

    /**
     * Qatar Calendar House, "the official calendar of the State of Qatar" (HIGH that Isha = Maghrib +
     * 90 on all 365 days, Ramadan included): Fajr 18° (printed up to about 1.6 min before exact 18°),
     * Dhuhr about +1, Asr +1, and Maghrib + 3 all year (spec §6.1: the calendar's Ramadan 1447 Maghrib;
     * the rest of the year it prints about sunset + 2). Taqwa follows the later of the calendar and
     * the Ministry of Awqaf's API, which prints Dhuhr and Asr a minute earlier (all 119 days both
     * print). The fast begins at its Fajr: the end of eating is the 18° dawn rounded down less 54 s.
     *
     * Margins (Task 7a, qa-calendarhouse.tsv): fitted on the printed Doha calendar (16 Jun–11 Sep 2026)
     * and the Ramadan 1447 imsakiya, the Conventions' formulas with 5 s: Dhuhr +37, Asr +40, Maghrib
     * and Isha −28 (so Maghrib is sunset + 152 s: Ramadan 1447 needs + 147 s). On the held-out website
     * captures, 21 May 2024's Fajr was a minute early with the fitted −73 and 8 Aug 2024's end of
     * eating a minute late with −45, so they are widened by the excess plus 5 s (proof
     * 7a-gulf-egypt.md): Fajr −63, end of eating −54; and sunrise, not late there but within its
     * 5 s, +15 (the fit said +18).
     * Held out, 0 early and 0 late ends on 2,845 place-days (the API's 2026, the website's 2023–2026
     * and Doha's table at seven towns of the east coast and the metro). Class B.
     */
    val qatarMethod = TimetableMethod(
        id = "qa.calendarhouse",
        fajrAngle = 18.0,
        isha = IshaRule.AfterMaghrib(90),
        authorityMinutes = EventOffsets(maghrib = 3),
        margins = margins(start = -28, sunrise = 15, fajr = -63, dhuhr = 37, asr = 40),
        endOfEatingMarginSeconds = -54,
    )

    /**
     * Qatar beyond Doha's table: the calendar's other zones (Al Shamal, Dukhan, Abu Samra, Mesaieed,
     * Halul) print their own rows, whose reference points are not known. The only zone data, the
     * website's own header, names its zones inconsistently (its "Mesaieed" runs 4–6 min before Doha,
     * which no point near Mesaieed can; its 2023 captures sit up to 16 min from its 2026 ones), so
     * nothing is measured there; where it looks geographical (Al Shamal, Dukhan, Abu Samra, Halul in
     * 2025–2026) it wants Fajr up to about 75 s later and sunrise 65 s earlier than Doha's margins
     * give at the zone towns. Hence 90 s either way, not the usual 60.
     */
    private val qatarZoneEdge = qatarMethod.margins.widened(QATAR_ZONE_EDGE_SECONDS)

    private fun qatarZone(id: String, name: String, lat: Double, lon: Double, radiusKm: Double) = AuthorityUnit(
        id, name, GeoPoint(lat, lon), radiusKm, method = qatarMethod.atEdge("qa.calendarhouse.$id", qatarZoneEdge), measured = false,
    )

    /** Doha's point: its calendar serves the east coast from Al Khor to Al Wakrah. */
    private val dohaPoint = GeoPoint(25.28545, 51.53096)

    /**
     * Doha, and Al Khor at the north of the coast that follows Doha's table: there the engine also
     * keeps Al Khor's own sun (spec §3.5), whose summer sunset is about 50 s after Doha's, so its
     * Maghrib and Isha run a minute past Doha's other towns on a few summer days (3 min on 6 days of
     * 2026 against Doha's table; Az Za'ayin, 12 km south, stays within 2).
     */
    val qatarUnits = UnitSet(
        "qa.calendarhouse",
        listOf(
            AuthorityUnit("doha", "Doha", dohaPoint, 50.0),
            AuthorityUnit(
                "al-khor", "Al Khor", GeoPoint(25.68389, 51.50583), 10.0,
                method = qatarMethod.copy(id = "qa.calendarhouse.al-khor", fixedPoint = dohaPoint),
                lateLimits = listOf(
                    LateLimit(
                        3,
                        "Al Khor follows Doha's calendar, and Taqwa also keeps Al Khor's own later summer sunset " +
                            "with the calendar's Maghrib + 3 (its Ramadan Maghrib, kept all year)",
                        setOf(TimedEvent.MAGHRIB, TimedEvent.ISHA),
                    ),
                ),
            ),
            qatarZone("shamal", "Al Shamal", 26.12, 51.20, 20.0),
            qatarZone("dukhan", "Dukhan", 25.43, 50.79, 25.0),
            qatarZone("abu-samra", "Abu Samra", 24.75, 50.83, 20.0),
            qatarZone("mesaieed", "Mesaieed", 24.99, 51.55, 15.0),
            qatarZone("halul", "Halul", 25.67, 52.41, 10.0),
        ),
    ) { qatarMethod.atEdge("qa.calendarhouse.edge", qatarZoneEdge) }

    val qatar: RegistryEntry = single(
        id = "qa.calendarhouse", nameKey = "authority_qatar_calendar", entryClass = EntryClass.B, method = qatarMethod,
        school = AsrSchool.STANDARD, countries = setOf("QA"),
    )

    // Kuwait, Bahrain, Oman.

    /**
     * Kuwait: the Ministry of Awqaf with the Al-Ojairi Scientific Centre, 18°/17.5°, no minutes,
     * mostly nearest rounding (MEDIUM). No official table is publicly reachable: the data is the
     * ministry's times for one day (25 Sep 2026) and 24 copies of Al-Anba's prayer box (2019–2026),
     * a newspaper that names no source, about half of whose pages print the day before the date
     * they state (dated here as the research corrected them).
     *
     * Margins (Task 7a, kw-awqaf.tsv): the never-early fit on those 25 days at Kuwait City with the
     * Conventions' formulas and 5 s (Fajr +6, sunrise +11, Dhuhr −28, Asr +17, Maghrib −11, Isha −9,
     * end of eating +6), and 60 s more on every time for its unknown reference point and thin,
     * second-hand data. Nothing is held out. Class D.
     */
    val kuwaitMethod = TimetableMethod(
        id = "kw.awqaf",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(17.5),
        margins = margins(start = 0, sunrise = 11, fajr = 6, dhuhr = -28, asr = 17, maghrib = -11, isha = -9),
        endOfEatingMarginSeconds = 6,
    ).let { it.atEdge(it.id, it.margins.widened(ZONE_EDGE_SECONDS)) }

    val kuwait: RegistryEntry = single(
        id = "kw.awqaf", nameKey = "authority_awqaf_kuwait", entryClass = EntryClass.D_AUTHORITY, method = kuwaitMethod,
        school = AsrSchool.STANDARD, countries = setOf("KW"),
    )

    /**
     * Bahrain: the Supreme Council for Islamic Affairs, one table for the kingdom (HIGH, the calendar
     * book): Isha 18° (replacing Maghrib + 90), Fajr and sunrise for the easternmost point, Dhuhr to
     * Isha for the westernmost, Dhuhr when the whole disc has crossed (+1). The 1448 book fits its
     * sun to the rounding (bands of 64–68 s) at two latitudes, as it says: Fajr and sunrise at about
     * 26.22° N, Dhuhr to Isha at about 26.15° N (Umm an Nasan's; the Hawar islands, at 25.65° N, give
     * 210–230 s). So sunrise and the end of eating are at the earlier of the main island's east coast
     * at 26.22° N and the user's point, and every start the latest of those and Umm an Nasan's west
     * coast. The fast begins at the printed Fajr.
     *
     * Margins (Task 7a, bh-council.tsv): fitted on the 1448 book (16 Jun 2026 to 5 Jun 2027) at the
     * fixed point and the app's eight Bahraini towns, since the whole kingdom follows the one table,
     * with the Conventions' formulas and 5 s: sunrise −9, Dhuhr −59 (with the book's +1 min), Asr −62,
     * Maghrib −36, Isha −118, end of eating −13 (the book rounds down). Its Fajr is the east point's
     * dawn rounded down, which is before a western town's own; the engine's start is the latest of
     * its points (spec §3.5: never before the method at the user's own point), so Fajr keeps the
     * book's own rounding at its east point, −56 (−123 would fit the book at every town but put Fajr
     * up to a minute before the west's own dawn), and runs 1–2 min after the book. The rest within
     * 1 min at every town; one year, nothing held out: class D.
     */
    val bahrainMethod = TimetableMethod(
        id = "bh.council",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(dhuhr = 1),
        margins = margins(start = 0, sunrise = -9, fajr = -56, dhuhr = -59, asr = -62, maghrib = -36, isha = -118),
        fixedPoint = GeoPoint(26.22, 50.67),
        endOfEatingMarginSeconds = -13,
        startPoints = listOf(GeoPoint(26.15, 50.38)),
    )

    val bahrain: RegistryEntry = single(
        id = "bh.council", nameKey = "authority_bahrain_council", entryClass = EntryClass.D_AUTHORITY, method = bahrainMethod,
        school = AsrSchool.STANDARD, countries = setOf("BH"), measured = true,
    )

    /**
     * Oman: the Ministry of Awqaf and Religious Affairs, 18°/18°, Dhuhr, Asr and Maghrib + 5, rounded
     * up, the sun once a day at about 0h UT, 86 localities at their own coordinates (HIGH as
     * inferred). The fast begins at the printed Fajr.
     *
     * Margins (Task 7a, om-mara.tsv): fitted on Muscat's 2026 year with the Conventions' formulas and
     * 5 s: Fajr +8, sunrise +54, Dhuhr, Asr and Maghrib +9 (with the ministry's 5 min), Isha +7, end
     * of eating +52. Held out, Muscat's January and July 2021 and March and September 2031: 0 early,
     * 0 late ends, at most 1 min (81–89 % to the minute). Only Muscat is measured: class B there, and
     * beyond it every locality's own table is the same method at a point not known here, so +60 s /
     * −60 s (class D).
     */
    val omanMethod = TimetableMethod(
        id = "om.mara",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        sunModel = SunModel.DAILY_0H_UT,
        asrModel = AsrModel.DAILY_0H_UT,
        authorityMinutes = EventOffsets(dhuhr = 5, asr = 5, maghrib = 5),
        margins = margins(start = 9, sunrise = 54, fajr = 8, isha = 7),
        endOfEatingMarginSeconds = 52,
    )

    val omanUnits = UnitSet(
        "om.mara",
        listOf(AuthorityUnit("muscat", "Muscat", GeoPoint(23.58413, 58.40778), 30.0)),
    ) { omanMethod.atEdge("om.mara.edge", omanMethod.margins.widened(60)) }

    val oman: RegistryEntry = single(
        id = "om.mara", nameKey = "authority_oman_awqaf", entryClass = EntryClass.B, method = omanMethod,
        school = AsrSchool.STANDARD, countries = setOf("OM"),
    )

    // The old picker's Gulf methods, anywhere.

    /**
     * The Other methods' plain safe rounding (ruling R31), with Asr [ADHAN2_ASR_ALLOWANCE] later:
     * adhan2 0.0.7, whose presets these reproduce, takes one declination for the whole day, so its
     * Asr runs up to about 34 s after this core's iterated one (Task 7h found other.singapore a
     * minute early from it: docs/research/2026-09-prayer-times/proof/7h-other-and-default.md).
     * Measured for these three and other.egyptian against the old adhan2 engine at twelve places
     * (Cairo to Sydney, London and New York among them), every day of 2026–2027 in both schools:
     * the Standard Asr a minute early on 19 of 8,760 days each (London 17, New York 2), 0 with the
     * allowance. other.ummalqura was never early there and keeps the plain rounding.
     */
    private val otherRounding = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, asr = SAFE_START + ADHAN2_ASR_ALLOWANCE)


    val otherKuwait: RegistryEntry = single(
        id = "other.kuwait", nameKey = "method_kuwait", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "other.kuwait", fajrAngle = 18.0, isha = IshaRule.Angle(17.5),
            margins = otherRounding, endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    /** The old picker's Qatar anywhere: Calendar House's 18° and Maghrib + 90 (+ 3), [otherRounding]. */
    val otherQatar: RegistryEntry = single(
        id = "other.qatar", nameKey = "method_qatar", entryClass = EntryClass.D_AUTHORITY,
        method = qatarMethod.copy(
            id = "other.qatar", margins = otherRounding, endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    /**
     * The old picker's Dubai anywhere: 18.2°/18.2° with adhan2's preset minutes (sunrise − 3, Dhuhr,
     * Asr and Maghrib + 3; IACAD's own are about the same), [otherRounding].
     */
    val otherDubai: RegistryEntry = single(
        id = "other.dubai", nameKey = "method_dubai", entryClass = EntryClass.D_AUTHORITY,
        method = dubaiMethod.copy(
            id = "other.dubai", authorityMinutes = EventOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
            margins = otherRounding, endOfEatingMarginSeconds = SAFE_END,
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL,
    )

    val entries = listOf(dubai, awqaf, qatar, kuwait, bahrain, oman)
    val others = listOf(otherKuwait, otherQatar, otherDubai)

    /** Where an authority's zone points are not known: a minute of offset either way. */
    private const val ZONE_EDGE_SECONDS = 60

    /** Qatar's zones and the rest of the country (see [qatarUnits]). */
    private const val QATAR_ZONE_EDGE_SECONDS = 90
}
