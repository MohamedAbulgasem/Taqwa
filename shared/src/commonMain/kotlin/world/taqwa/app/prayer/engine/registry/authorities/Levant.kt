package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.CALCULATED_NAME
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.Scope
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.beyondTable
import world.taqwa.app.prayer.engine.registry.cautious
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single

/**
 * Jordan, Palestine, Lebanon, Syria, Iraq and Yemen: class D with an authority (Palestine measured at
 * every town its offset list prints, class D since ruling R118; Jordan class A at Amman, its only
 * measured point, B elsewhere as the entry's own class, D_AUTHORITY as any place beyond a checked unit
 * reads; the rest still thin, spec §6.2 a).
 * Task 7e normalised the Levant and Iraq captures under `archive/tables/normalized-7e/` (git-ignored)
 * and gated each authority; the margins below are `Fitter.fit`'s never-early margins over the fit
 * rows, widened where a held-out row was early (never silently, see proof.md), never a first guess.
 * See docs/research/2026-09-prayer-times/proof/7e-levant.md for every number and its gate table.
 */
object Levant {

    /**
     * Jordan's Ministry of Awqaf: Fajr 18°, Isha ≈ 18.2°; sunrise 5.6–7.5 min before true sunrise and
     * Maghrib + 5.9..7.6, behaving like depression angles of about −2.0° and −2.25° rather than fixed
     * minutes (80 Wayback dates, HIGH); Dhuhr, Asr, Isha + 0..1, rounded up. Here both at −2.25°, the
     * deeper (sunrise earlier, Maghrib later). Margins fitted on 50 dates 2021-2024 (the awqaf.gov.jo
     * calculator's own "recent calculations" log, archive/tables/normalized-7e/jo-awqaf-amman-2021-2024.txt),
     * held out on 30 dates from 2025: never early, at most 1 min late on every one of the 80 measured
     * place-days, fit and held-out alike.
     *
     * Every one of the 80 dates is Amman's: the calculator's `<option selected>` names Amman on every
     * one of the 11 captures (the same dropdown also lists Irbid, Karak, Tafileh, Ma'an, Aqaba, the
     * Jordan Valley, Jerash/Ajloun and Mafraq, but none of those is ever the selected city, and no
     * other place's results table was captured). So Amman is a point-table unit (rulings R30, R15:
     * its point rides as the fixed point), class A within its reach (ruling R40); elsewhere in Jordan
     * the fitted margins are unproven, so the edge is a plain safety margin (Diyanet's own pattern,
     * +60 s starts, −60 s sunrise) rather than the fitted ones, and the entry's own class is B ("an
     * unverified unit or season") — though a place that resolves to no unit reads D_AUTHORITY there
     * regardless (outside a checked unit an A/B entry is D_AUTHORITY, spec §6.2 a); About shows that,
     * honestly, for everywhere in Jordan but Amman.
     */
    val jordanMethod = TimetableMethod(
        id = "jo.awqaf",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.2),
        horizonDeg = -2.25,
        margins = margins(start = -35, sunrise = 60, dhuhr = 3, asr = 11, maghrib = -47, isha = -52),
    )

    /** Beyond Amman's reach the point is unknown: a plain safety edge, not the fitted margins. */
    val jordanEdge = jordanMethod.atEdge("jo.awqaf.edge", margins(start = 60, sunrise = -60))

    val jordanUnits = UnitSet(
        "jo.awqaf",
        listOf(
            AuthorityUnit(
                "jo.awqaf.amman", "Amman", GeoPoint(31.955, 35.945), lateReachKm(31.955, EntryClass.A),
                entryClass = EntryClass.A, measured = true,
            ),
        ),
    ) { jordanEdge }

    val jordan = single(
        id = "jo.awqaf", nameKey = "authority_awqaf_jordan", entryClass = EntryClass.B, method = jordanMethod,
        school = AsrSchool.STANDARD, countries = setOf("JO"), measured = false,
    )

    /**
     * The PA's Dar al-Iftaa: its perpetual prayer table for al-Aqsa Mosque, printed on the perpetual
     * winter clock (UTC+02:00 all year: the gate's `clock` column, not this method) and reprinted
     * unchanged (the 2012 printing, the 2026 yearly calendar and the Ramadan 1447 imsakiya agree on
     * every cell held), with a printed list of town offsets, one figure a town for every time alike,
     * sunrise included ([paTowns]). Fajr ≈ 18°, Isha ≈ 18°, sunrise and Maghrib behaving like a
     * −2.25° horizon (6.7–7.5 min at 31.8° N), every event at the noon declination
     * (SunModel.CLASSIC_NOON: over the whole table it narrows the spread against the sun, Fajr's from
     * 3.1 to 2.4 min, Isha's from 2.9 to 2.6). A named timetable: also a member of Gaza's cautious
     * times.
     *
     * The PA prints no imsak: its Fajr is when the fast begins, so the end of eating is held to the
     * printed Fajr (the gate's `F+E` column), never after it. One dawn cannot be both never before the
     * printed Fajr (a start) and never after it (an end), the printed minutes wandering about 2.4 min
     * against any smooth rule; so the end of eating has its own fitted margin
     * ([TimetableMethod.endOfEatingMarginSeconds], ruling R39's "an end of its own").
     *
     * Margins fitted by the gate's fitter at al-Aqsa on the whole table, 366 days, mapped onto its
     * printing year 2012 and onto 2026 (the 2026 calendar's print), held out on 2027 and on the
     * imsakiya's six dates (archive/tables/manual/ps-iftaa/2026-10-02/); where 2027 was early (by 1
     * min, on 1-11 days of Fajr, sunrise, Maghrib and Isha: the leap cycle's drift of a perpetual
     * table) the margin is widened to the three years' bound, never silently.
     *
     * Class D_AUTHORITY, not B (ruling R57): never early on any measured place-day, but the table's
     * own minutes wander about 2.4 min against any one smooth rule (Fajr in April and August a minute
     * nearer the sun than in October), so one never-early margin per event runs 3 min late on some
     * days of Fajr, sunrise, Maghrib and Isha even at al-Aqsa: four of six events would need an
     * exception to stay B, and the class the data supports is D (at most 3 min late), like Libya's.
     * Earlier (Task 7e) the entry was class B on 78 dates; the whole table disproves that.
     */
    val paMethod = TimetableMethod(
        id = "ps.iftaa",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        sunModel = SunModel.CLASSIC_NOON,
        horizonDeg = -2.25,
        margins = margins(start = 0, sunrise = 191, fajr = 1, dhuhr = -37, asr = -21, maghrib = -2, isha = 59),
        endOfEatingMarginSeconds = PA_END_OF_EATING_MARGIN,
    )

    val pa = single(
        id = "ps.iftaa", nameKey = "authority_pa_iftaa", entryClass = EntryClass.D_AUTHORITY, method = paMethod,
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("PS"), measured = true,
    )

    /**
     * The end of eating's own margin (seconds), fitted by the gate's fitter at al-Aqsa on the printed
     * Fajr as an end (the gate's `F+E`): − 96 s on 2012 and 2026; 2027 held out was 1 min late on 7
     * days of April with it, so it is widened to the three years' bound, − 116 s, never silently.
     */
    private const val PA_END_OF_EATING_MARGIN = -116

    /** al-Aqsa Mosque, the point the perpetual table is printed for. */
    val aqsa = GeoPoint(31.7767, 35.2345)

    /**
     * One town of the PA's printed offset list: its unit [key], [name] and [point] (the app's city
     * point where the app lists the town), and its printed offset in minutes after Jerusalem as
     * whole minutes for the starts ([startMinutes]) and for the ends ([endMinutes]): the same figure,
     * except the minute and a half of Qalqilya, Lydd and Ramla, which a whole-minute clock shows as + 2
     * for a start (never before the half-minute instant) and + 1 for an end (never after it). [country]
     * is where the town lies today: PS, or IL for the list's towns in Israel.
     */
    class PaTown(
        val key: String,
        val name: String,
        val point: GeoPoint,
        val startMinutes: Int,
        val endMinutes: Int = startMinutes,
        val country: String = "PS",
    )

    /**
     * Ruling R118 (owner, 2 Oct 2026): the authority's own printed town offsets apply to its al-Aqsa
     * table, exactly as printed, for every town whose offset it prints (the earlier refusal for Gaza
     * stands only where it prints none). The print (ssalah2012.pdf p. 2): "This timing is set for the
     * blessed al-Aqsa Mosque (by the winter clock); those living outside it observe the time
     * differences as follows". Every Palestinian town of the list, as printed: Jerusalem, Ramallah,
     * Bethlehem, Jenin and Nablus + 0; Jericho − 1; Hebron, Idhna, Dura, Beit Awwa and Tulkarm + 1;
     * Qalqilya + 1.5; Gaza + 3; Rafah, Khan Yunis and Deir al-Balah + 4. Beit Awwa is not among the
     * app's cities: its point is approximate (from public maps), among neighbours (Dura, Idhna,
     * Hebron) that all print + 1.
     *
     * The list's towns in Israel, as printed: Nazareth and Umm al-Fahm + 0; Tiberias, Safed and Beisan
     * − 1; Haifa, Acre, Kafr Qasim and Tayibe + 1; Lydd and Ramla + 1.5; Beersheba and Jaffa + 2
     * (points approximate, from public maps). The entry's scope stays Palestine (choosing it in Israel
     * falls back to Automatic): these towns are units only so that a place in Palestine nearer one of
     * them than any Palestinian town of the list (Ni'lin and Qibya by Lydd, Barta'a by Umm al-Fahm,
     * Bardala by Beisan) takes that nearest printed town's own figure, never a farther town's (review
     * r2 of R118).
     */
    val paTowns: List<PaTown> = listOf(
        PaTown("jerusalem", "Jerusalem", aqsa, 0),
        PaTown("ramallah", "Ramallah", GeoPoint(31.89964, 35.20422), 0),
        PaTown("bethlehem", "Bethlehem", GeoPoint(31.70487, 35.20376), 0),
        PaTown("jenin", "Jenin", GeoPoint(32.45943, 35.30086), 0),
        PaTown("nablus", "Nablus", GeoPoint(32.22111, 35.25444), 0),
        PaTown("jericho", "Jericho", GeoPoint(31.86667, 35.45), -1),
        PaTown("hebron", "Hebron", GeoPoint(31.52935, 35.0938), 1),
        PaTown("idhna", "Idhna", GeoPoint(31.55874, 34.97436), 1),
        PaTown("dura", "Dura", GeoPoint(31.50777, 35.02929), 1),
        PaTown("beit-awwa", "Beit Awwa", GeoPoint(31.5075, 34.9497), 1),
        PaTown("tulkarm", "Tulkarm", GeoPoint(32.31156, 35.0269), 1),
        PaTown("qalqilya", "Qalqilya", GeoPoint(32.18966, 34.97063), startMinutes = 2, endMinutes = 1),
        PaTown("gaza", "Gaza", GeoPoint(31.50161, 34.46672), 3),
        PaTown("rafah", "Rafah", GeoPoint(31.29722, 34.24357), 4),
        PaTown("khan-yunis", "Khan Yunis", GeoPoint(31.34018, 34.30627), 4),
        PaTown("deir-al-balah", "Deir al-Balah", GeoPoint(31.41834, 34.34933), 4),
        PaTown("nazareth", "Nazareth", GeoPoint(32.7019, 35.3033), 0, country = "IL"),
        PaTown("umm-al-fahm", "Umm al-Fahm", GeoPoint(32.5194, 35.1536), 0, country = "IL"),
        PaTown("tiberias", "Tiberias", GeoPoint(32.7922, 35.5312), -1, country = "IL"),
        PaTown("safed", "Safed", GeoPoint(32.9646, 35.496), -1, country = "IL"),
        PaTown("beisan", "Beisan", GeoPoint(32.4973, 35.4973), -1, country = "IL"),
        PaTown("haifa", "Haifa", GeoPoint(32.794, 34.9896), 1, country = "IL"),
        PaTown("acre", "Acre", GeoPoint(32.9281, 35.082), 1, country = "IL"),
        PaTown("kafr-qasim", "Kafr Qasim", GeoPoint(32.1146, 34.9762), 1, country = "IL"),
        PaTown("tayibe", "Tayibe", GeoPoint(32.2662, 35.0089), 1, country = "IL"),
        PaTown("lydd", "Lydd", GeoPoint(31.951, 34.8881), startMinutes = 2, endMinutes = 1, country = "IL"),
        PaTown("ramla", "Ramla", GeoPoint(31.9293, 34.873), startMinutes = 2, endMinutes = 1, country = "IL"),
        PaTown("beersheba", "Beersheba", GeoPoint(31.2518, 34.7913), 2, country = "IL"),
        PaTown("jaffa", "Jaffa", GeoPoint(32.0504, 34.7522), 2, country = "IL"),
    )

    /**
     * A town's table, as the authority constructs it: al-Aqsa's own times plus the town's printed
     * minutes, computed at al-Aqsa alone ([FixedPointMode.TABLE]) with al-Aqsa's fitted margins. So
     * every town carries al-Aqsa's proven bound, and is never early against its own printed times by
     * construction: whole minutes added to a time already rounded never cross a minute. The end of
     * eating follows the end minutes (the authority's Fajr minute moves it too, so a half minute's + 2
     * for its Fajr start is taken back a minute for its end of eating). The ends the authority never
     * prints (Asr's at the sunset, Maghrib's at the red twilight) stay bounded by the user's own sun
     * too ([FixedPointMode.TABLE]).
     */
    private fun paTable(town: PaTown) = paMethod.copy(
        id = "ps.iftaa.${town.key}",
        authorityMinutes = EventOffsets(
            fajr = town.startMinutes, sunrise = town.endMinutes, dhuhr = town.startMinutes,
            asr = town.startMinutes, maghrib = town.startMinutes, isha = town.startMinutes,
        ),
        endOfEatingMarginSeconds = paMethod.endOfEatingMarginSeconds + (town.endMinutes - town.startMinutes) * 60,
        fixedPoint = aqsa,
        fixedPointMode = FixedPointMode.TABLE,
    )

    /**
     * Every printed town a unit at its own point (rulings R30, R118). A place takes the nearest
     * printed town's own table, never a neighbour's offset (the discipline of rulings R103 and R113):
     * every unit has the same reach (class D's at al-Aqsa's latitude, about 71 km), so the nearest
     * unit holding a place is the nearest town, and no town's reach takes a place nearer another
     * printed town, on either side of the Green Line. Every place in Palestine is within about 20 km
     * of a printed town. Beyond every reach (only a place outside Palestine whose Automatic lists the
     * entry) the edge: the al-Aqsa method at the user's own point a minute later, the nearest town's
     * point still bounding the ends (rulings R44, R45), no town's offset carried beyond its town.
     */
    val paUnits: UnitSet = run {
        val reachKm = lateReachKm(aqsa.lat, EntryClass.D_AUTHORITY)
        val units = paTowns.map { AuthorityUnit("ps.iftaa.${it.key}", it.name, it.point, reachKm, paTable(it)) }
        UnitSet(pa.id, units) { user ->
            val nearest = units.minBy { distanceKm(user, it.point) }
            paMethod.beyondTable("ps.iftaa.edge", nearest.point, user, nearest.radiusKm)
        }
    }

    /**
     * Gaza's Ministry of Awqaf: Fajr ≈ 19.5° (the second adhan; the first is 30 min earlier), Isha ≈
     * 17.5°, Maghrib + 2..3 (HIGH for what it shows). It conflicts with the PA's table for Gaza (the
     * PA's Fajr about 7 min later, its Maghrib 3 min later). Margins fitted on the one real capture
     * held (six full dated rows from a Jun-Jul 2020 imsakiya PDF,
     * archive/tables/normalized-7e/ps-gaza-awqaf-2020.txt); nothing held out (too thin), so class
     * stays D_AUTHORITY (spec §6.2 a) though never early on what is measured.
     */
    val gazaAwqaf = single(
        id = "ps.gaza.awqaf", nameKey = "authority_gaza_awqaf", entryClass = EntryClass.D_AUTHORITY,
        method = TimetableMethod(
            id = "ps.gaza.awqaf",
            fajrAngle = 19.5,
            isha = IshaRule.Angle(17.5),
            authorityMinutes = EventOffsets(maghrib = 3),
            margins = margins(start = 12, sunrise = 59, dhuhr = -8, asr = -54, maghrib = -45, isha = -34),
        ),
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("PS"),
    )

    /** Gaza: cautious over its Ministry of Awqaf (most followed there) and the PA's table (spec §6.1, class C). */
    val gaza = cautious(
        id = "ps.gaza.cautious",
        members = listOf(gazaAwqaf.asMember(1), pa.asMember(2)),
        school = AsrSchool.STANDARD, schoolKnown = true, scope = Scope.CITY, countries = setOf("PS"), measured = false,
        named = { true },
    )

    /**
     * Lebanon's Dar al-Fatwa: its mosque-screen feed's perpetual table (HIGH that it is identified):
     * Fajr 19.1–20.0° (6–13 min before 18°), Isha 17.2–18.1°, Maghrib + 3.1..6.8. Margins fitted on
     * all 365 printable rows of the perpetual table (Masjidi TV id 26, Ras Beirut; found printed on a
     * fixed winter clock all year, UTC+02:00, never Beirut's real summer DST -- see proof.md), widened
     * where two of 19 held-out dated captures (2019-2026, a different source) were early by exactly
     * 1 min (fajr, dhuhr): +65 s over the fit's own worst day, never silently. A single worst-case
     * margin over 384 days across two sources and seven years still lets other days run later: Fajr up
     * to 9 min, Maghrib and Isha up to 7 -- two recorded exceptions, never early on any of them. The
     * end of eating keeps the spec's dawn angle, 20°, not measured here (no printed end-of-eating
     * column).
     *
     * Two more captures were checked and stayed excluded (see proof.md): 25 Jan 2023 is a genuine
     * widget bug (every printed time ~60 min off its own displayed date, nowhere near a real
     * daylight-saving change). 28 Mar 2024 reads correctly on the DST clock on every OTHER event
     * (1-14 min of the fitted margins above) but its sunrise would be 10 min late under that same
     * reading -- and a sunrise may never be late by any amount, no exception can cover it, so
     * including this one date would cost every other day 11 more minutes of earlier sunrise. One
     * capture (1 Apr 2023) reads correctly and is included with its own fixed clock, below.
     */
    val lebanonMethod = TimetableMethod(
        id = "lb.fatwa",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        margins = margins(start = -304, sunrise = -61, dhuhr = 53, asr = 47, maghrib = 392, isha = -4),
        endOfEating = EndOfEating.DawnAngle(20.0),
        endOfEatingMarginSeconds = LEBANON_END_OF_EATING,
    )

    val lebanon = single(
        id = "lb.fatwa", nameKey = "authority_dar_al_fatwa", entryClass = EntryClass.D_AUTHORITY, method = lebanonMethod,
        school = AsrSchool.STANDARD, countries = setOf("LB"),
        lateLimits = listOf(
            LateLimit(
                9, "the perpetual table's single never-early margin, over 384 measured days across two " +
                    "sources and seven years, runs to 9 min late on Fajr on its worst day",
                setOf(TimedEvent.FAJR),
            ),
            LateLimit(
                7, "the same margin runs to 7 min late on Maghrib and Isha on their worst held-out day",
                setOf(TimedEvent.MAGHRIB, TimedEvent.ISHA),
            ),
            LateLimit(
                8, "the fast begins at the 20° dawn, never after Dar al-Fatwa's printed Fajr, whose own dawn runs 19.1° to " +
                    "20.0°: up to 8 min before it on the shallowest days (end-of-eating audit, 3 Oct 2026)",
                setOf(TimedEvent.END_OF_EATING),
            ),
        ),
    )

    /**
     * The 20° end of eating's margin (end-of-eating audit, 3 Oct 2026): Dar al-Fatwa prints no
     * end-of-eating column, and the fast begins at its printed Fajr (lb-fatwa.tsv now checks the end
     * against it, F+E). Never after it on any of the 384 days held; the fitter's bound on the
     * perpetual table, with its 5 s safety, is −5 s (decided by its 9 September row), so the end moves
     * 5 s earlier.
     */
    private const val LEBANON_END_OF_EATING = -5

    /**
     * Syria's Ministry of Awqaf: the MWL method plus a 2-minute tamkin, officially since 28 Feb 2025
     * (SANA; the + 2 sits on Maghrib and Dhuhr here, MEDIUM, 2 dates). Margins fitted on the earlier of
     * the two dated samples the research found (a Ramadan 1447 imsakiya reading), widened on Dhuhr by
     * +125 s and Asr/Maghrib by +65 s: the later sample (the Umayyad Mosque, a different point), held
     * out, was early by 2 min on Dhuhr and 1 min on Asr and Maghrib, never silently. Too thin for a
     * class above D_AUTHORITY (spec §6.2 a).
     */
    val syriaMethod = TimetableMethod(
        id = "sy.awqaf",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(17.0),
        authorityMinutes = EventOffsets(dhuhr = 1, maghrib = 2),
        margins = margins(start = -47, sunrise = -7, dhuhr = 28, asr = 49, maghrib = 19, isha = -10),
    )

    val syria = single(
        id = "sy.awqaf", nameKey = "authority_awqaf_syria", entryClass = EntryClass.D_AUTHORITY, method = syriaMethod,
        school = AsrSchool.STANDARD, countries = setOf("SY"),
    )

    /**
     * Iraq's Sunni Endowment: 18°/17°, Dhuhr ≈ + 5, Asr + 3..4.5, Maghrib ≈ + 3 (MEDIUM, one official
     * sample). Margins fitted on that one dated sample (4 Dec 2025,
     * archive/tables/normalized-7e/iq-sunni-baghdad-official.txt); a third-party site's month was
     * normalised too but excluded from the gate (its Dhuhr/Asr/Maghrib/Isha disagree with this date by
     * 6-27 min, one-sided, far beyond ordinary variation -- not established reliable, see proof.md).
     * One date proves nothing beyond itself: D_AUTHORITY.
     */
    val iraqMethod = TimetableMethod(
        id = "iq.sunni",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(17.0),
        margins = margins(start = -26, sunrise = 18, dhuhr = 268, asr = 125, maghrib = 154, isha = -29),
    )

    val iraq = single(
        id = "iq.sunni", nameKey = "authority_sunni_endowment", entryClass = EntryClass.D_AUTHORITY, method = iraqMethod,
        school = AsrSchool.STANDARD, countries = setOf("IQ"),
    )

    /**
     * Yemen: no Sunni authority's table found (Aden side not found); the Sana'a Awqaf's 31 points on one
     * date (LOW) are Fajr 18°, Isha ≈ 16°, Dhuhr ≈ + 10, Maghrib + 6 in the lowlands and ≈ + 9.5 in the
     * highlands. Calculated by Taqwa (spec §6.2 c) with those published minutes as floors (§6.2 d):
     * Isha 18° + 2, Dhuhr and Maghrib + 10, Asr + 2, sunrise − 2, the end of eating 2 min before 18°.
     */
    val yemenMethod = TimetableMethod(
        id = "ye.default",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        margins = margins(start = 120 + SAFE_START, sunrise = -120 + SAFE_SUNRISE, fajr = SAFE_START, dhuhr = 600 + SAFE_START, maghrib = 600 + SAFE_START),
        endOfEating = EndOfEating.DawnAngle(18.0),
        endOfEatingMarginSeconds = -120,
    )

    val yemen = single(
        id = "ye.default", nameKey = CALCULATED_NAME, entryClass = EntryClass.D_NONE, method = yemenMethod,
        school = AsrSchool.STANDARD, countries = setOf("YE"),
    )

    val entries = listOf(jordan, pa, gazaAwqaf, gaza, lebanon, syria, iraq, yemen)
}
