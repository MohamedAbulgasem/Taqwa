package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
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
import world.taqwa.app.prayer.engine.registry.cautious
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single

/**
 * Jordan, Palestine, Lebanon, Syria, Iraq and Yemen: class D with an authority (Palestine measured to
 * class B everywhere; Jordan class A at Amman, its only measured point, B elsewhere as the entry's
 * own class, D_AUTHORITY as any place beyond a checked unit reads; the rest still thin, spec §6.2 a).
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
     * The PA's Dar al-Iftaa: a perpetual al-Aqsa table reprinted yearly (HIGH): ≈ 18°/18°, Maghrib +
     * 4.9..7.3, sunrise 1.6–4.3 min early, with printed city offsets. Its Maghrib and sunrise follow
     * a depression angle like Jordan's, so the same −2.25° (which gives 6.7–7.5 min at 31.8° N).
     * A named timetable: also a member of Gaza's cautious times. Margins fitted on the 2012 printing
     * (72 dates, archive/tables/normalized-7e/ps-iftaa-aqsa-2012.txt, read on its printed perpetual
     * winter clock, UTC+02:00 fixed all year -- the gate's `clock` column, not this method), held out
     * on six dates from its Ramadan 1447 (2026) reprint, 14 years later: never early, at most 2 min
     * late on every held-out event (class B; proves the "reused verbatim" claim forward in time).
     */
    val paMethod = TimetableMethod(
        id = "ps.iftaa",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        horizonDeg = -2.25,
        margins = margins(start = -16, sunrise = 216, dhuhr = -40, asr = -26, maghrib = -25, isha = 23),
    )

    val pa = single(
        id = "ps.iftaa", nameKey = "authority_pa_iftaa", entryClass = EntryClass.B, method = paMethod,
        school = AsrSchool.STANDARD, scope = Scope.GLOBAL, countries = setOf("PS"), measured = true,
    )

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
