package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.RamadanRule
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
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.followedBeyond
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.withNearestBeyond
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
        margins = margins(start = 0, sunrise = -241, fajr = 4, dhuhr = 111, asr = 8, maghrib = 209, isha = -35 + RURAL_REACH_SECONDS),
        endOfEatingMarginSeconds = 12 - RURAL_REACH_SECONDS,
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
    ) { user ->
        // A user who chooses IACAD beyond its zones (Sharjah, Ajman) follows its nearest table (the city-point round of
        // 11 Oct 2026): the zone edge as before, with that table's point riding for every start and end (followedBeyond),
        // so never before Dubai's own table, nor any end after it. Nearer Dubai Rural's or Hatta's point, the same edge
        // rides there (their own margins differ from the edge's; the edge's is never earlier than before). Dubai's own
        // point also rides beside it wherever another table is the nearest, for every start and end (round 2's review:
        // Fujairah, nearest Hatta, and Al Ain, nearest Dubai Rural, had been up to 3 and 2 min before Dubai's own table),
        // so a user who chooses IACAD is never before Dubai's main table either, nor any end after it.
        val nearest = DUBAI_TABLES.minBy { distanceKm(user, it) }
        val followed = dubaiMethod.followedBeyond("ae.iacad.dubai.edge", nearest, dubaiZoneEdge)
        val dubai = DUBAI_TABLES.first()
        if (nearest == dubai) {
            followed
        } else {
            followed.copy(startPoints = (followed.startPoints + dubai).distinct(), endPoints = (followed.endPoints + dubai).distinct())
        }
    }

    /** IACAD's three tables' points: Dubai's, Dubai Rural's (the latitude its sun fits best) and Hatta's. */
    private val DUBAI_TABLES = listOf(GeoPoint(25.07725, 55.30927), GeoPoint(24.66, 55.60), GeoPoint(24.80073, 56.12726))

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
     * and Isha −28 (so Maghrib is sunset + 152 s: Ramadan 1447 needs + 147 s). On two held-out website
     * captures, Fajr was a minute early with the fitted −73 on one and the end of eating a minute late
     * with −45 on the other, so both are widened by the excess plus 5 s (proof 7a-gulf-egypt.md): Fajr
     * −63, end of eating −54; and sunrise, not late there but within its 5 s, +15 (the fit said +18).
     * Held out, 0 early and 0 late ends on 2,845 place-days (the API's 2026, the website's 2023–2026
     * and Doha's table at seven towns of the east coast and the metro). Class B.
     * 2027 (track T4, 11 Oct 2026): the API answers every date of 2027, and there Maghrib and Isha were a
     * minute early on one day of Ramadan 1448 at Doha and four of the towns. The calendar's Ramadan Maghrib
     * is its latest (above), and that day asks a few seconds more, so Ramadan's Maghrib, and the Isha counted
     * from it, take 8 s more ([RamadanRule], the fits' 5 s included), the rest of the year keeping −28 (raised
     * all year, 13 summer days at Doha reached 3 min, over class B's 2). Dhuhr +42. Both were set on 2027's
     * rows, so those rows are fit, not held out.
     */
    val qatarMethod = TimetableMethod(
        id = "qa.calendarhouse",
        fajrAngle = 18.0,
        isha = IshaRule.AfterMaghrib(90),
        authorityMinutes = EventOffsets(maghrib = 3),
        margins = margins(start = -28, sunrise = 15, fajr = -63, dhuhr = 42, asr = 40),
        ramadan = RamadanRule(maghribExtraSeconds = 8),
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
     * Oman: the Ministry of Awqaf and Religious Affairs, 18°/18°, Dhuhr, Asr and Maghrib + 5, the sun once a day at
     * about 0h UT, 86 localities at their own coordinates (HIGH as inferred). Muscat's times are rounded up, every
     * other locality's to the nearest minute (below). The fast begins at the printed Fajr.
     *
     * Margins (Task 7a, om-mara.tsv): fitted on Muscat's 2026 year with the Conventions' formulas and
     * 5 s: Fajr +8, sunrise +54, Dhuhr, Asr and Maghrib +9 (with the ministry's 5 min), Isha +7, end
     * of eating +52. Held out, Muscat's January and July 2021 and March and September 2031: 0 early,
     * 0 late ends, at most 1 min (81–89 % to the minute).
     *
     * The ministry's 85 other localities (rulings R166 and R169, the round of 12 November 2026): its all-localities day
     * page, read for every day from 1 November 2026 to 31 December 2027 (Ramadan 1448 among it) and the 1st and 15th
     * of each month of 2029, prints each one's table at its own point. Dhuhr follows longitude alone: at the app's
     * towns noon + 5 gives the printed Dhuhr rounded to the nearest minute on 390–421 of 426 days and rounded up on
     * about 200, where Muscat's is rounded up (417), so the ministry rounds Muscat up and every other locality to the
     * nearest minute (R169; R166 had read every locality as rounded up, which put each point 0.125° east of its town,
     * the same half minute). Each is a unit of 20 km at its own point, fitted from its 2026–2027 days
     * (archive/code/manual/om-mara/fit_points.py) and rounded to 0.01° as fitted rounded up, then 0.125° west, with
     * [omanNearest]'s margins: the same day at that point as R166's checked. Where the app lists the town, the point
     * lies 0.3–4.5 km from the app's (Quriyat 11.5). Held out on the 2029 days, which no point was fitted on: see
     * om-mara.tsv. Where two localities' reaches overlap (ruling R167, as Egypt's R165), the nearer holds the place and
     * every other whose reach holds it rides beside, its point in the envelope where the two share their margins, its
     * own day at its own point where they do not (Muscat beside another), so no start there is before either table.
     *
     * Al Hamra (ruling R170): the ministry's "الحمرة" (its English page's "Al-Hamra", read 12 Nov 2026), the wilayat
     * otherwise missing from its list, is printed for 23.78 N 57.285 E: the town's longitude and about 0.66° north of
     * it (23°47′ for 23°07′). Its unit sits at the town, with that point riding beside, so every start is the later
     * and every end the earlier of the town's own sun and the table's, as at an off-point ESA town. Its reach is 12 km,
     * short of Bahla's point (16 km), and its dawn runs up to 3 min after the table in early summer (an exception).
     * Bahla's own exception is the other side of the same table (ruling R254, 14 Nov 2026): Al Hamra's circle holds the
     * north of Bahla's, from about 4 km north of Bahla's point, and there the 23.78 N table rides beside Bahla's (R167), so
     * from May to July Asr and Isha run up to 3 min after Bahla's table and the end of eating 3 min before it, where
     * the entry's limit is 2 (measured across Bahla's reach in om-mara-reach.tsv; no start early, no end late). The
     * ministry's Rakhyut is printed about half a degree east of the town, beside Raysut's: its unit stays where it is
     * printed, and the town itself lies in Dhalkut's reach, whose table runs 2–3 min after Rakhyut's (late, never early).
     *
     * Past a circle on the edge (ruling R210, the audit of 12 Nov 2026): the ministry prints by locality, with no
     * boundaries, so a circle is this engine's line, not the ministry's, and a place just past one, where no other
     * locality's circle holds it, still reads that locality's table. Left alone, the edge there was a minute before the
     * table on up to a third of days (As Sifah, east of Muscat's circle) or showed sunrise and the end of eating a minute
     * after it (the edge beside most circles, Al Hamra's north). So beyond every circle a locality's table rides half
     * again its circle ([omanRideKm]: 30 km; Muscat 45 km, which holds As Sifah; Al Hamra 18 km, which holds Jebel
     * Shams), as its own day beside the user's own: the later start and the earlier end of the two. Nothing gets earlier
     * anywhere; within a ride, starts are at most the riding table's minute later and sunrise and the end of eating its
     * minute earlier. Inside another locality's circle nothing changes: that locality's own printed table holds the
     * place, and another's rides beside it only where its own circle also holds the place (R167).
     *
     * The ride moved the line; it did not end it. Half again the circle is this engine's judgement, as the circle is,
     * and the ministry gives nothing to fit it to: just past the end of a ride the place was the plain edge again, and
     * there the edge stood a minute before the nearest locality's table on some days or showed its sunrise and end of
     * eating a minute after it (measured in docs/BUILD-LOG.md, 13 Nov 2026; the second audit's G3, 14 Nov 2026). So past
     * every ride (ruling R260) the nearest locality's own day still rides beside the user's own, and with it any other
     * locality no more than a quarter farther ([ridingBeyond]), out to five minutes of longitude from its point (about
     * 127 km; farther, the Rub' al Khali and the far desert, the edge alone). Where a farther locality's ride holds the
     * place but the nearest locality's does not, the nearest and any about as near ride beside it too (ruling R262, 15 Nov
     * 2026, [withNearestBeyond]: 3 km past Quriyat's ride, inside Muscat's, the end of eating was a minute after Quriyat's
     * printed Fajr on 24 days of 2027). `om-mara-envelope.tsv` holds each table to the promise on the edge inside its
     * ride and on rings past it.
     *
     * Beyond every circle, the same method at a point not known here (class D). Its starts are a minute after Muscat's
     * rounding up, so a minute and a half after the other 85 tables' rounding to the nearest minute (R169). Its sunrise
     * and end of eating are a minute before the nearest rounding's, a minute and a half before Muscat's (ruling R240,
     * the audit's F12): a minute before Muscat's alone left them half a minute, and the end of eating 52 s, before the
     * 85 tables the edge lies beside, and the edge showed their sunrise late on about one day in twenty-five just past
     * a circle.
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

    /**
     * Ruling R169: the ministry's localities but Muscat, rounded to the nearest minute rather than up: the national
     * margins 30 s earlier at every event, the end of eating too. At its own point each is the same day the round of 12
     * November 2026 checked 0.125° east of it with the national margins (noon moves 30 s in 0.125°).
     */
    private val omanNearest = omanMethod.copy(
        id = "om.mara.nearest",
        margins = margins(start = 9 - NEAREST_SECONDS, sunrise = 54 - NEAREST_SECONDS, fajr = 8 - NEAREST_SECONDS, isha = 7 - NEAREST_SECONDS),
        endOfEatingMarginSeconds = 52 - NEAREST_SECONDS,
    )

    private fun locality(name: String, lat: Double, lon: Double, id: String = name.lowercase().replace(' ', '-')) =
        AuthorityUnit(id = id, name = name, point = GeoPoint(lat, lon), radiusKm = 20.0, method = omanNearest, named = false)

    /** Al Hamra's table, printed for 23.78 N at the town's longitude (ruling R170). */
    private val alHamraTable = GeoPoint(23.78, 57.285)

    /** Ruling R265: the sunrise shown is never after the real sunrise at the place, measured at Muscat and Al Hamra. */
    private val omanRealSunrise = LateLimit(
        3,
        "The sunrise shown is never after the real sunrise at the place itself (ruling R265), and the ministry's printed " +
            "sunrise stands after the sun has risen there on some days: the sunrise comes up to 3 min before the print",
        setOf(TimedEvent.SUNRISE),
    )

    private val omanLocalities = listOf(
        // Every locality is named by the app in the reader's language, never the ministry's Latin name (the second
        // audit's G12: "في Muscat"), as Egypt's towns are (F34).
        AuthorityUnit(
            "muscat", "Muscat", GeoPoint(23.58413, 58.40778), 30.0, named = false, lateLimits = listOf(omanRealSunrise),
        ),
        // The ministry's other localities (rulings R166, R169), each at its own point fitted from its printed days.
        locality("Al Hamra", 23.11667, 57.28333).copy(
            radiusKm = AL_HAMRA_KM,
            method = omanNearest.copy(id = "om.mara.al-hamra", envelopePoints = listOf(alHamraTable)),
            lateLimits = listOf(
                LateLimit(
                    3,
                    "The ministry computes Al Hamra's table about two thirds of a degree north of the town, and Taqwa " +
                        "keeps the town's own later dawn in early summer",
                    setOf(TimedEvent.FAJR),
                ),
                omanRealSunrise,
            ),
        ),
        locality("Ibra", 22.70, 58.545),
        locality("Adam", 22.37, 57.535),
        locality("Izki", 22.93, 57.785),
        locality("Al Ashkharah", 21.85, 59.565),
        locality("Al Buraimi", 24.24, 55.785, id = "buraimi"),
        locality("Al Jazer", 18.67, 56.545),
        locality("Al Jammah", 23.56, 57.545),
        locality("Al Hashman", 18.45, 53.075),
        locality("Al Hallaniyat", 17.50, 55.995),
        locality("Al Khaburah", 24.00, 57.095),
        locality("Al Khadrafi", 16.70, 53.145),
        locality("Al Khuwair", 22.86, 55.395),
        locality("Duqm", 19.63, 57.635),
        locality("Rustaq", 23.43, 57.435),
        locality("Al Awabi", 23.31, 57.545),
        locality("Al Qabil (Ash Sharqiyah)", 22.56, 58.685, id = "al-qabil-sharqiyah"),
        locality("Al Qabil (Adh Dhahirah)", 23.95, 55.795, id = "al-qabil-dhahirah"),
        locality("Al Kamil Wal Wafi", 22.20, 59.195),
        locality("Al Mudaybi", 22.56, 58.115),
        locality("Al Mamurah", 17.06, 54.225),
        locality("Al Huwaisah", 21.88, 55.895),
        locality("Al Wasit", 24.20, 56.265),
        locality("Al Wasil", 22.50, 58.745),
        locality("Bukha", 26.13, 56.165),
        locality("Bidbid", 23.40, 58.135),
        locality("Bidiyah", 22.43, 58.815),
        locality("Barka", 23.68, 57.895),
        // Ruling R254: Al Hamra's table rides beside Bahla's in the north of Bahla's circle, measured across the reach.
        locality("Bahla", 22.97, 57.295).copy(
            lateLimits = listOf(
                LateLimit(
                    3,
                    "Al Hamra's table, which the ministry computes about two thirds of a degree north of that town, " +
                        "also holds the north of Bahla's area, and there Taqwa keeps the later start and the earlier " +
                        "end of the two tables in summer",
                    setOf(TimedEvent.ASR, TimedEvent.ISHA, TimedEvent.END_OF_EATING),
                ),
            ),
        ),
        locality("Thumrait", 17.61, 54.045),
        locality("Jiddat al Harasis", 19.59, 56.245),
        locality("Jaalan Bani Bu Hassan", 22.08, 59.285),
        locality("Jaalan Bani Bu Ali", 21.98, 59.345),
        locality("Habrut", 17.32, 52.745),
        locality("Hij", 20.76, 58.295),
        locality("Khazzan", 22.03, 56.695),
        locality("Dibba", 25.63, 56.245),
        locality("Ras al Hadd", 22.53, 59.785),
        locality("Ras Madrakah", 19.00, 57.825),
        locality("Rakhyut", 16.75, 53.935),
        locality("Wahiba Sands", 22.23, 58.785),
        locality("Raysut", 16.95, 53.995),
        locality("Sadah", 17.05, 55.045),
        locality("Samail", 23.30, 57.965),
        locality("Samad al Shan", 22.73, 58.145),
        locality("Sinaw", 22.50, 58.035),
        locality("Suwayq", 23.85, 57.435),
        locality("Saih al Rawl", 21.26, 56.665),
        locality("Saiq", 23.08, 57.645),
        locality("Shalim", 18.09, 55.645),
        locality("Shinas", 24.73, 56.465),
        locality("Sohar", 24.34, 56.725),
        locality("Saham", 24.16, 56.885),
        locality("Sarab", 20.17, 57.815),
        locality("Sarfait", 16.70, 53.095),
        locality("Salalah", 17.03, 54.075),
        locality("Sur", 22.57, 59.535),
        locality("Dank", 23.54, 56.245),
        locality("Taqah", 17.04, 54.395),
        locality("Dhalkut", 16.71, 53.245),
        locality("Ibri", 23.23, 56.515),
        locality("Fahud", 22.34, 56.475),
        locality("Qarn Alam", 21.10, 57.685),
        locality("Quriyat", 23.16, 58.915),
        locality("Kanhat", 23.55, 56.785),
        locality("Liwa", 24.51, 56.565),
        locality("Mahdah", 24.40, 55.965),
        locality("Mahut", 20.57, 58.165),
        locality("Madha", 25.26, 56.295),
        locality("Mirbat", 16.98, 54.685),
        locality("Marmur", 17.97, 54.475),
        locality("Marmul", 18.13, 55.185),
        locality("Musandam", 26.18, 56.245),
        locality("Al Musanaah", 23.78, 57.645),
        locality("Masirah", 20.67, 58.895),
        locality("Muqshin", 19.55, 54.885),
        locality("Manah", 22.78, 57.595),
        locality("Nakhal", 23.40, 57.825),
        locality("Nizwa", 22.93, 57.535),
        locality("Nimr", 18.55, 55.845),
        locality("Harweel", 18.03, 54.635),
        locality("Haima", 20.07, 56.295),
        locality("Wadi Bani Khalid", 22.48, 59.115),
        locality("Wadi Hibi", 23.92, 56.545),
        locality("Yanqul", 23.58, 56.545),
    )

    /**
     * How far [unit]'s table rides beyond every circle, where no table holds the place (ruling R210): [OMAN_RIDE] of its
     * own circle (30 km for a locality, 45 km for Muscat, 18 km for Al Hamra).
     */
    internal fun omanRideKm(unit: AuthorityUnit): Double = OMAN_RIDE * unit.radiusKm

    /**
     * [placed] with [others] riding beside it: each one's point(s) in the envelope where it shares [mine]'s margins,
     * its own day at its own point(s) where it does not (Muscat beside another locality, every locality beside the edge).
     */
    private fun omanRide(placed: TimetableMethod, mine: TimetableMethod, others: List<AuthorityUnit>): TimetableMethod {
        if (others.isEmpty()) return placed
        val (shared, own) = others.partition { (it.method ?: omanMethod).margins == mine.margins }
        val ownDays = own.flatMap { u ->
            val m = u.method ?: omanMethod
            (listOf(u.point) + m.envelopePoints).map { Alongside(it, m.copy(envelopePoints = emptyList())) }
        }
        return placed.copy(
            envelopePoints = (placed.envelopePoints + shared.flatMap { listOf(it.point) + (it.method?.envelopePoints ?: emptyList()) }).distinct(),
            alongside = placed.alongside + ownDays,
        )
    }

    /**
     * Beyond every circle: the national method at a point not known here (class D). Starts a minute after Muscat's
     * rounding up; sunrise and the end of eating a minute before the localities' rounding to the nearest (ruling R240).
     */
    private val omanEdge = omanMethod.atEdge("om.mara.edge", omanMethod.margins.widened(60, sunrise = -(60 + NEAREST_SECONDS)))

    val omanUnits = UnitSet(
        "om.mara",
        omanLocalities,
        around = { user, unit, placed ->
            // Ruling R167: every other locality whose circle holds the user rides beside the nearest.
            omanRide(placed, unit.method ?: omanMethod, omanLocalities.filter { it.id != unit.id && distanceKm(user, it.point) <= it.radiusKm })
        },
    ) { user ->
        // Ruling R210: beyond every circle, every locality whose ride holds the user rides beside the user's own day; past
        // every ride, or past the nearest locality's own ride, the nearest and any about as near (rulings R260, R262).
        omanRide(omanEdge, omanEdge, withNearestBeyond(omanLocalities.filter { distanceKm(user, it.point) <= omanRideKm(it) }, omanLocalities, user))
    }

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

    /**
     * Ruling R271 (the reach proof of 14 Nov 2026, finding G26): Dubai Rural's table read across its reach was a minute
     * early at Isha on one day of 2024 and its end of eating a minute late on two days of 2023, at every point of the
     * reach that does not see the sun later than its fixed point (its own point, 24.80° N, did, which hid it): its
     * Isha margin and its end of eating each move this much to the safe side.
     */
    private const val RURAL_REACH_SECONDS = 30

    /** Where an authority's zone points are not known: a minute of offset either way. */
    private const val ZONE_EDGE_SECONDS = 60

    /** Qatar's zones and the rest of the country (see [qatarUnits]). */
    private const val QATAR_ZONE_EDGE_SECONDS = 90

    /** Half a minute: the ministry's nearest rounding beside Muscat's rounding up (ruling R169). */
    private const val NEAREST_SECONDS = 30

    /** Al Hamra's reach: the wilayat's town and villages, short of Bahla's point 16 km away, which keeps its own (R170). */
    private const val AL_HAMRA_KM = 12.0

    /** Beyond every circle a locality's table rides half again its circle (ruling R210). */
    private const val OMAN_RIDE = 1.5
}
