package world.taqwa.app.prayer.engine.registry.authorities

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.FixedPointMode
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.RamadanRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.MethodVariant
import world.taqwa.app.prayer.engine.registry.Regions
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.SAFE_END
import world.taqwa.app.prayer.engine.registry.SAFE_START
import world.taqwa.app.prayer.engine.registry.SAFE_SUNRISE
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.data.AlgeriaWilayas
import world.taqwa.app.prayer.engine.registry.data.HabousCities
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Libya, Tunisia, Algeria, Morocco, Mauritania and Sudan (profiles-tested.md data-maghreb-libya;
 * authorities-arab-world-turkiye.md). Every margin here is fitted on this core (the gate's Fitter,
 * 5 s safety) against the official tables in `tools/timetables/official/gate/` (ly-awqaf, tn-inm,
 * dz-marw, ma-habous, mr-ministry, sd-ministry); the proof is
 * `docs/research/2026-09-prayer-times/proof/7d-maghreb-libya.md`. Only derived parameters are
 * written here: angles, minutes, margins in seconds and points, never a printed time.
 */
object Maghreb {

    // Libya.

    /**
     * Libya: the General Authority of Awqaf and Dar al-Ifta (Tripoli; the 1992–94 observation
     * committee's table, statement of 8 Feb 2022). The national method at each of the 22 cities
     * awqaf.gov.ly prints (the owner's answer, spec §6.2 a and §10.3): Fajr 18.5°, Isha 18.3°. The 21
     * other cities' tables are held for 25–26 Sep 2026 only, and Tripoli's alone spans the seasons
     * (23 checked days, March 2025 to October 2026), drifting against this model through the year
     * (Fajr about a minute later in spring and summer than in late September, Dhuhr later in winter,
     * sunrise earlier in summer). So each margin is the city's late-September offset from Tripoli
     * (the most cautious of three readings of two days) plus Tripoli's worst over its 23 days, plus
     * 5 s: never early at any city that drifts as Tripoli does. That is a projection outside late
     * September, not a measurement; Tripoli's own days are measured. The end of eating is that 18.5°
     * dawn + 7 s (ruling R59, the strictest reading: each western city's earliest late-September day
     * against Tripoli's API mean, plus Tripoli's earliest over its 23 days, 5 s; Tripoli the tightest);
     * imsak is Fajr − 10 or − 11 in print, so the dawn − 11. Jalu and Awjila print their Asr 7.5 min
     * after the others' rule, their own offset. East of 18.5° E and south of 29.5° N, Fajr and
     * Maghrib follow the local adhan and not this national method, and the end of eating is the
     * 19.5° dawn the mosques there call ([variants], ruling R73). Class D: at most 3 min late on
     * every day held (the east and south's Fajr and Maghrib measured only at the owner's two
     * observations there; their sunrise, Dhuhr, Asr and Isha still at all 14 of their cities).
     */
    val libyaMethod = TimetableMethod(
        id = "ly.awqaf",
        fajrAngle = 18.5,
        isha = IshaRule.Angle(18.3),
        margins = margins(start = 0, sunrise = -98, fajr = 63, dhuhr = 246, asr = 29, maghrib = 261, isha = 42),
        endOfEatingMarginSeconds = 7,
        imsakMinutesBeforeFajr = 11,
    )

    /** Jalu and Awjila print their Asr 7.5 min after the others' rule: the authority's own offset, every month. */
    private val libyaLateAsr = libyaMethod.copy(
        id = "ly.awqaf.late-asr",
        monthlyOffsets = mapOf(Prayer.ASR to IntArray(12) { LATE_ASR_SECONDS }),
    )

    private fun libyan(name: String, lat: Double, lon: Double, method: TimetableMethod? = null) =
        AuthorityUnit(name.lowercase().replace(' ', '-'), name, GeoPoint(lat, lon), 30.0, method)

    val libyaUnits = UnitSet(
        "ly.awqaf",
        listOf(
            libyan("Tripoli", 32.8872, 13.1913), libyan("Benghazi", 32.1167, 20.0667), libyan("Sabha", 27.0377, 14.4283),
            libyan("Tobruk", 32.0836, 23.9764), libyan("Misrata", 32.3754, 15.0925), libyan("Ghadames", 30.1333, 9.5),
            libyan("Kufra", 24.1833, 23.2833), libyan("Derna", 32.767, 22.6367), libyan("Sirte", 31.2089, 16.5887),
            libyan("Bayda", 32.7627, 21.7551), libyan("Zawiya", 32.7571, 12.7276), libyan("Hun", 29.1268, 15.9477),
            libyan("Jalu", 29.0331, 21.5482, libyaLateAsr), libyan("Emsaed", 31.5889, 25.052), libyan("Ajdabiya", 30.7554, 20.2263),
            libyan("Khoms", 32.6486, 14.2619), libyan("Marj", 32.4925, 20.8286), libyan("Bani Walid", 31.7566, 13.9942),
            libyan("Ras Lanuf", 30.4833, 18.55), libyan("Zliten", 32.4674, 14.5687), libyan("Brega", 30.4061, 19.5739),
            libyan("Awjila", 29.1081, 21.2869, libyaLateAsr),
        ),
    ) { libyaMethod.atEdge("ly.awqaf.edge", libyaMethod.margins.widened(60)) }

    val libya: RegistryEntry = single(
        id = "ly.awqaf", nameKey = "authority_awqaf_libya", entryClass = EntryClass.D_AUTHORITY, method = libyaMethod,
        school = AsrSchool.STANDARD, countries = setOf("LY"),
    )

    // Tunisia.

    /**
     * Tunisia: the Institut National de la Météorologie, per delegation at its own coordinates (258;
     * the six checked are at INM's own reference points). Fajr 18°, Isha 18°, Dhuhr = transit + 7 and
     * Maghrib = sunset + 2, sunrise floored; Asr first-principles plus a monthly table (its definition
     * is none of those tested); no imsak: INM prints none in the tables held, so none is shown and the end of eating
     * is the fast's one time here (ruling R188, the owner, 12 Nov 2026; it was Fajr − 10, unprinted). Margins fitted on
     * the four low delegations' 2026 tables (Tunis and Sfax every day, Tabarka and Ben Guerdane every
     * third), the end of eating from the dawn − 41 s; held out, Tunis 2019–2025 and the two elevated
     * delegations, where nothing was early. Elevated delegations apply a horizon dip to Fajr, sunrise,
     * Maghrib and Isha (Tataouine ~0.45°, Tala ~1.0°, found on their own tables in the research);
     * their dipped Fajr takes 60 s more. Beyond the six (class D), the deepest dip seen (1°) for
     * sunrise, Maghrib and Isha, the end of eating at the dipped dawn, and the Fajr start undipped, so
     * it is never before a low delegation's. Class B: at most 1 min late at the low delegations, 2 at
     * the dipped ones.
     */
    val tunisiaMethod = TimetableMethod(
        id = "tn.inm",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(dhuhr = 7, maghrib = 2),
        margins = margins(start = 0, sunrise = -27, fajr = -51, dhuhr = -15, asr = 14, maghrib = 26, isha = 43),
        monthlyOffsets = mapOf(Prayer.ASR to intArrayOf(-57, -58, -45, -38, -35, -35, -15, 7, 20, 10, 9, -20)),
        endOfEatingMarginSeconds = -41,
    )

    private fun dipped(id: String, dip: Double) = tunisiaMethod.copy(
        id = "tn.inm.$id", horizonDeg = -0.8333 - dip, twilightDipDeg = dip,
        margins = tunisiaMethod.margins.copy(fajr = tunisiaMethod.margins.fajr + 60),
    )

    val tunisiaUnits = UnitSet(
        "tn.inm",
        listOf(
            AuthorityUnit("tunis", "Tunis", GeoPoint(36.8, 10.183), 25.0),
            AuthorityUnit("sfax", "Sfax", GeoPoint(34.733, 10.75), 25.0),
            AuthorityUnit("tataouine", "Tataouine", GeoPoint(32.933, 10.45), 25.0, dipped("tataouine", 0.45)),
            AuthorityUnit("tala", "Tala", GeoPoint(35.572, 8.67), 25.0, dipped("tala", 1.0)),
            AuthorityUnit("tabarka", "Tabarka", GeoPoint(36.952, 8.758), 25.0),
            AuthorityUnit("ben-guerdane", "Ben Guerdane", GeoPoint(33.139, 11.215), 25.0),
        ),
    ) {
        tunisiaMethod.atEdge("tn.inm.edge", tunisiaMethod.margins.widened(60)).copy(
            horizonDeg = -1.8333, isha = IshaRule.Angle(19.0), endOfEating = EndOfEating.DawnAngle(19.0),
        )
    }

    val tunisia: RegistryEntry = single(
        id = "tn.inm", nameKey = "authority_inm", entryClass = EntryClass.B, method = tunisiaMethod,
        school = AsrSchool.STANDARD, countries = setOf("TN"),
    )

    // Algeria.

    /**
     * Algeria: the Ministry of Religious Affairs and Waqfs, 1448 calendars: Fajr 18°, Isha 17°,
     * Maghrib + 3, every column rounded up, sunrise included; the Asr of a one-shot formula with the
     * sun at 12h UT; no elevation, no imsak. The three base cities' margins are fitted on Algiers's
     * 1448 table and held out on Djelfa's, Adrar's and eight days of Algiers's 1447 calendar (nothing
     * early, at most 1 min late at all three). Class B there: MARW's own reference points are not
     * published.
     *
     * Every other wilaya is computed at its seat (the owner's answer, spec §10.4). The ministry prints
     * those as a base city plus a difference per month (Adrar's group per half month); Oran's and
     * Tamanrasset's derived tables fit their seats' own margins, class D there (Oran at most 3 min
     * late, Tamanrasset 3 and Asr 6, a recorded exception), and every other seat takes the later of
     * the two (not measured).
     *
     * Rulings R230–R232 (13 Nov 2026, the owner's ruling of that day): within 20 km of its seat, each wilaya of
     * Algiers's group (and Dellys, which the calendar gives a column) is this method at the seat with margins of its
     * own, fitted so that it is never before the table the Ministry prints for the place ([fittedSeats]), class D
     * ("Published method") with the figure the fit measured, and no start before its sign over the user's own
     * point. None of the Ministry's printed difference minutes is in this code: the tables they make are held
     * privately, where the gate reads them. The fit is of one calendar's days: after its last day ([lastHeldDay])
     * these places, and Oran's and Tamanrasset's bands with them, are a whole minute to the safe side and claim no
     * figure until the next calendar is read ([beyondHeldDays], R231 as revised).
     */
    val algeriaMethod = TimetableMethod(
        id = "dz.marw",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(17.0),
        asrModel = AsrModel.UTC12_ONE_SHOT,
        authorityMinutes = EventOffsets(maghrib = 3),
        margins = margins(start = 0, sunrise = 49, fajr = 14, dhuhr = 7, asr = 10, maghrib = 11, isha = 14),
        endOfEatingMarginSeconds = 46,
    )

    /** Tamanrasset's seat, fitted on the ministry's Tamanrasset table (Adrar plus its half-monthly differences). */
    private val tamanrassetMethod = algeriaMethod.copy(
        id = "dz.marw.tamanrasset",
        margins = margins(start = 0, sunrise = 67, fajr = 188, dhuhr = 19, asr = 195, maghrib = 178, isha = 194),
        endOfEatingMarginSeconds = 62,
    )

    /**
     * Every other seat: the later of the two derived tables' starts, the earlier sunrise (no table of its own).
     *
     * Ruling R218: and a further minute at Dhuhr and at sunrise. Against the 24 printed tables of Algiers's group
     * (355 days each), the seat's computation on the two fitted seats' margins alone was a minute before the printed
     * Dhuhr at 16 seats (on up to 212 of the days) and a minute after the printed sunrise at 3; with the minute it is
     * before none of them at any start, nor after any sunrise. The minute applies wherever this method computes:
     * every computed place of Algeria is a minute later at Dhuhr and its sunrise a minute earlier than before. The
     * evidence is Algiers's group alone; the 33 wilayas of Djelfa's and Adrar's calendars, whose difference pages
     * were not read, take the minute as caution and are not measured.
     */
    private val wilayaMethod = algeriaMethod.atEdge(
        "dz.marw.wilaya",
        margins(start = 0, sunrise = 2, fajr = 188, dhuhr = 81, asr = 195, maghrib = 178, isha = 194),
    )

    private val tamanrassetAsr = LateLimit(
        6,
        "The ministry prints Tamanrasset as Adrar's table plus one difference each half month, which does not " +
            "follow Tamanrasset's own Asr through the half month; so that the Asr computed at its seat is never " +
            "before that printed time, it is up to 6 min after it on some days.",
        setOf(TimedEvent.ASR),
    )

    /** Ruling R265, measured at Tamanrasset and Relizane (the gate, 14 Nov 2026). */
    private val algeriaRealSunrise = LateLimit(
        4,
        "The sunrise shown is never after the real sunrise at the place itself (ruling R265), and the ministry's printed " +
            "sunrise stands after the sun has risen there on some days: the sunrise comes up to 4 min before the print",
        setOf(TimedEvent.SUNRISE),
    )

    /** Algiers's point, the base table's (the research's). */
    private val algiersPoint = GeoPoint(36.7538, 3.0588)

    /** A seat's own margins over [algeriaMethod], in seconds: the five starts, sunrise and the end of eating. */
    private class Fitted(val fajr: Int, val sunrise: Int, val dhuhr: Int, val asr: Int, val maghrib: Int, val isha: Int, val eating: Int)

    /**
     * Ruling R230 (the owner's ruling of 13 Nov 2026): the margins of the 24 wilaya seats of Algiers's calendar and of
     * Dellys, by the wilaya's official number. The Ministry prints each of these places as Algiers's daily table plus
     * a whole number of minutes per time, one figure for each Hijri month; those figures, and the tables they make,
     * are held privately and are no part of this code (its calendar forbids copying; ruling R69). What is here is
     * Taqwa's own: one margin per time and seat, constant through the year, over this method computed at the seat,
     * fitted (the gate's fit, 5 s safety) so that no start is before, and no sunrise or end of eating after, the
     * place's table on any of its 355 held days (16 June 2026 to 5 June 2027).
     *
     * Ruling R231 (as revised in the review round of 13 Nov 2026), the days the calendar does not hold. A monthly
     * whole minute on the held days is all the fit sees, so the held days alone do not bound a later day: the Hijri
     * months fall eleven days earlier each year, and a month's one figure then sits over other days of the seat's
     * own curve. Two things answer that.
     *
     * The spare. Each margin is also past a bound on what the next calendars can print for the seat on any day to
     * 30 June 2028, wherever their month pages fall. The bound is worked out privately, beside the held tables (the
     * archive's `marw_fit.py`, which says how), and is past the held days' own need at every seat and time: the spare
     * is what it adds, 9 to 77 s (29 s at the middle one). It costs lateness: on the held days every time is at most
     * 3 min after the printed one at every seat (the class's limit, the owner's bound), 2 min at most seats, and
     * Dhuhr 1 min at all but two; any wider a bound would make one seat's Fajr 4 min late on some days.
     *
     * The last held day. That bound is a reading of one calendar, made on the same days the margins were fitted on
     * (no day of these 25 places is held out), with about a second between it and the widest case seen. It is spare,
     * not proof. So the margins stand as they are only through [lastHeldDay]; after it every start is a whole minute
     * later and sunrise and the end of eating a whole minute earlier than they give, and the place claims no figure
     * ([beyondHeldDays]), until the next calendar is read, the margins are fitted again and the day is moved on.
     */
    private val fittedSeats: Map<String, Fitted> = mapOf(
        "2" to Fitted(fajr = 137, sunrise = 37, dhuhr = 19, asr = 118, maghrib = 119, isha = 140, eating = 33),
        "4" to Fitted(fajr = 169, sunrise = 37, dhuhr = 26, asr = 130, maghrib = 146, isha = 170, eating = 34),
        "6" to Fitted(fajr = 86, sunrise = 50, dhuhr = 19, asr = 22, maghrib = 23, isha = 26, eating = 46),
        "9" to Fitted(fajr = 114, sunrise = 38, dhuhr = 17, asr = 94, maghrib = 102, isha = 111, eating = 36),
        "10" to Fitted(fajr = 122, sunrise = 37, dhuhr = 35, asr = 102, maghrib = 108, isha = 117, eating = 38),
        "15" to Fitted(fajr = 80, sunrise = 37, dhuhr = 69, asr = 77, maghrib = 80, isha = 90, eating = 33),
        "18" to Fitted(fajr = 92, sunrise = 38, dhuhr = 62, asr = 73, maghrib = 76, isha = 73, eating = 37),
        "19" to Fitted(fajr = 138, sunrise = 37, dhuhr = 38, asr = 113, maghrib = 124, isha = 139, eating = 39),
        "21" to Fitted(fajr = 86, sunrise = 50, dhuhr = 36, asr = 87, maghrib = 91, isha = 98, eating = 35),
        "23" to Fitted(fajr = 94, sunrise = 39, dhuhr = 62, asr = 84, maghrib = 89, isha = 101, eating = 33),
        "24" to Fitted(fajr = 111, sunrise = 37, dhuhr = 41, asr = 96, maghrib = 99, isha = 117, eating = 33),
        "25" to Fitted(fajr = 117, sunrise = 39, dhuhr = 26, asr = 104, maghrib = 105, isha = 125, eating = 39),
        "26" to Fitted(fajr = 126, sunrise = 42, dhuhr = 59, asr = 111, maghrib = 114, isha = 130, eating = 34),
        "27" to Fitted(fajr = 160, sunrise = 36, dhuhr = 20, asr = 130, maghrib = 135, isha = 163, eating = 38),
        "28" to Fitted(fajr = 181, sunrise = 36, dhuhr = 67, asr = 149, maghrib = 157, isha = 184, eating = 35),
        "31" to Fitted(fajr = 179, sunrise = 41, dhuhr = 26, asr = 139, maghrib = 156, isha = 185, eating = 36),
        "34" to Fitted(fajr = 143, sunrise = 40, dhuhr = 61, asr = 125, maghrib = 124, isha = 144, eating = 36),
        "35" to Fitted(fajr = 64, sunrise = 83, dhuhr = 53, asr = 57, maghrib = 58, isha = 60, eating = 81),
        "36" to Fitted(fajr = 86, sunrise = 44, dhuhr = 14, asr = 78, maghrib = 80, isha = 82, eating = 41),
        "41" to Fitted(fajr = 124, sunrise = 36, dhuhr = 47, asr = 109, maghrib = 109, isha = 123, eating = 33),
        "42" to Fitted(fajr = 97, sunrise = 42, dhuhr = 46, asr = 90, maghrib = 88, isha = 98, eating = 34),
        "43" to Fitted(fajr = 107, sunrise = 41, dhuhr = 62, asr = 95, maghrib = 96, isha = 109, eating = 40),
        "44" to Fitted(fajr = 129, sunrise = 37, dhuhr = 51, asr = 103, maghrib = 113, isha = 132, eating = 35),
        "48" to Fitted(fajr = 177, sunrise = 39, dhuhr = 72, asr = 139, maghrib = 147, isha = 182, eating = 33),
        "dellys" to Fitted(fajr = 100, sunrise = 36, dhuhr = 37, asr = 89, maghrib = 94, isha = 96, eating = 37),
    )

    /**
     * Ruling R230: the place [key] of Algiers's group as its seat's own fitted day: this method at [point] alone
     * ([FixedPointMode.TABLE]: the seat's day, as the Ministry's one table for the wilaya is the seat's) with the
     * seat's margins ([fittedSeats]). Null for a place with none (the three base cities, and the wilayas of Djelfa's
     * and Adrar's calendars, whose difference pages are not held).
     */
    private fun fittedSeat(key: String, point: GeoPoint): TimetableMethod? = fittedSeats[key]?.let { f ->
        algeriaMethod.fitted("dz.marw.seat.$key", f).copy(fixedPoint = point, fixedPointMode = FixedPointMode.TABLE)
    }

    private fun TimetableMethod.fitted(id: String, f: Fitted) = copy(
        id = id,
        margins = margins(start = 0, sunrise = f.sunrise, fajr = f.fajr, dhuhr = f.dhuhr, asr = f.asr, maghrib = f.maghrib, isha = f.isha),
        endOfEatingMarginSeconds = f.eating,
    )

    /**
     * Oran's band: the rest of its wilaya within the old seat reach, and its seat where the place's region is not
     * known (ruling R217). The same margins as Oran's fitted seat (ruling R231: the fit on the held days alone, which
     * this band carried before, has no spare for a day the calendar does not hold), computed at the seat and at the
     * place as every seat's method is, and the same last held day ([beyondHeldDays]).
     */
    private val oranMethod = algeriaMethod.fitted("dz.marw.oran", fittedSeats.getValue("31"))

    /**
     * The last day of the calendar the fitted margins were read against (the Ministry's 1448 calendar: 16 June 2026
     * to 5 June 2027). To be moved on only when the next calendar's tables are held, the margins fitted on them and
     * the gate has proved them.
     */
    internal val lastHeldDay = LocalDate(2027, 6, 5)

    /**
     * Ruling R231 as revised: a fitted place after [lastHeldDay]. Beside the place's own day rides the same method
     * ([method], as placed for [user]) with every start margin a minute larger and the sunrise and end-of-eating
     * margins a minute smaller: each start is then a whole minute later, and sunrise and the end of eating a whole
     * minute earlier, than the fitted margins give. A figure of the next calendar a whole minute past what the
     * margins allow for is so still not before the time shown. The day rides on no date up to [lastHeldDay], where
     * the gate holds the place's own table, and the unit claims no figure after it
     * ([AuthorityUnit.measuredThrough]).
     */
    private fun beyondHeldDays(user: GeoPoint, method: TimetableMethod): Alongside {
        val m = method.margins
        val wider = method.copy(
            id = "${method.id}.beyond",
            margins = m.copy(
                fajr = m.fajr + MINUTE, dhuhr = m.dhuhr + MINUTE, asr = m.asr + MINUTE, maghrib = m.maghrib + MINUTE,
                isha = m.isha + MINUTE, sunrise = m.sunrise - MINUTE,
            ),
            endOfEatingMarginSeconds = method.endOfEatingMarginSeconds - MINUTE,
            alongside = emptyList(),
        )
        return Alongside(user, wider, after = lastHeldDay)
    }

    /**
     * Ruling R232 (R219's floor, kept as it was built): beside a seat's fitted day ([seat], [fittedSeat]), the user's
     * own sky. The seat's table is its centre's, and a place up to [SEAT_REACH_KM] to its west or south sees each
     * sign later; the calendar prints the Standard Asr alone. So no start is before its sign over the user's own
     * point with Algiers's own margin for it and none of the authority's minutes: Fajr the user's own dawn, Dhuhr the
     * transit, Asr of either school the user's own (the method's model, and the exact moment, which a one-shot model
     * can sit a few seconds before), Maghrib the sunset, Isha the dusk. And no sunrise or end of eating is after the
     * seat's own margin over the user's own point (a place east of the seat sees them earlier). Two days alongside
     * ([Alongside]) say it: every start the later, every end the earlier. At the seat itself they change nothing,
     * a seat's start margins being no smaller than Algiers's (held by a test).
     */
    private fun ownSky(user: GeoPoint, seat: TimetableMethod): List<Alongside> {
        val base = algeriaMethod.margins
        val own = seat.copy(
            id = "${seat.id}.own", fixedPoint = null, fixedPointMode = FixedPointMode.BOTH, authorityMinutes = EventOffsets(),
            margins = seat.margins.copy(fajr = base.fajr, dhuhr = base.dhuhr, asr = base.asr, maghrib = base.maghrib, isha = base.isha),
        )
        return listOf(Alongside(user, own), Alongside(user, own.copy(id = "${seat.id}.own.asr", asrModel = AsrModel.EXACT_MOMENT)))
    }

    /** Dellys, a town of Boumerdès (wilaya 35) the Algiers calendar gives a column of its own: the app's city point. */
    private val dellysPoint = GeoPoint(36.91716, 3.91311)

    /** A wilaya whose table is held: its base city's point (the research's) or its seat, its method and class. */
    private class Measured(val point: GeoPoint?, val method: TimetableMethod?, val entryClass: EntryClass, val lateLimits: List<LateLimit> = emptyList())

    private val measuredWilayas = mapOf(
        16 to Measured(algiersPoint, null, EntryClass.B),
        17 to Measured(GeoPoint(34.6704, 3.2630), null, EntryClass.B),
        1 to Measured(GeoPoint(27.8742, -0.2939), null, EntryClass.B),
        31 to Measured(null, oranMethod, EntryClass.D_AUTHORITY),
        11 to Measured(null, tamanrassetMethod, EntryClass.D_AUTHORITY, listOf(tamanrassetAsr, algeriaRealSunrise)),
    )

    /**
     * A place's wilaya is its own region when known (ruling R32); the nearest seat only otherwise,
     * which in the Sahara can be another wilaya's (Aoulef, in Adrar, is nearer In Salah). The whole
     * wilaya follows its seat's table, so the seat rides as the fixed point everywhere in it; a
     * wilaya whose table is held is measured only within its class's reach of the seat (ruling R40:
     * about 45 km for B, 70 for D), beyond which its own sun can be later than the seat's by more than
     * the class allows and the rest of the wilaya claims no figure.
     *
     * Rulings R217 and R230: a place of Algiers's group is first a unit of [SEAT_REACH_KM] around its seat on the
     * seat's own fitted day ([fittedSeat]: Egypt's and Oman's printed cities, ruling R163, are units of the same
     * 20 km), class D with the fit's figure, with the user's own sky beside it ([ownSky], ruling R232); a wilaya's
     * places beyond it are computed as they were. Oran's seat keeps the band it had around that unit (`31-seat`,
     * class D out to its reach, on the margins fitted to Oran's table before), and Dellys is such a unit inside
     * Boumerdès's wilaya. The band's table is replayed at the seat, a point the band itself no longer serves: its
     * "measured" is the fit there, carried out to the band's reach as it was before the seat's unit.
     *
     * Ruling R219: a seat's fitted day is a place's only by its region. Without one (a location stored without a
     * region, or one whose name is none of the 58), the nearest such seat can be a neighbouring wilaya's, whose table
     * is not the place's own (Rouiba, in Algiers, is nearer Boumerdès's seat): such a place is computed at the nearest
     * seat as before these units ([computed]), never handed a seat's fitted day by distance alone.
     */
    private fun fittedUnit(id: String, name: String, point: GeoPoint, method: TimetableMethod) =
        AuthorityUnit(
            id, name, point, SEAT_REACH_KM, method, entryClass = EntryClass.D_AUTHORITY, measuredThrough = lastHeldDay,
            named = false, lateLimits = if (id in realSunriseSeats) listOf(algeriaRealSunrise) else emptyList(),
        )

    /** Ruling R265: the seats where the real sun's cap puts the sunrise past class D's 3 min before the print (Relizane). */
    private val realSunriseSeats = setOf("48")

    /** Whether a unit is a seat's fitted day ([fittedSeat]). */
    private val AuthorityUnit.isFittedSeat: Boolean get() = method?.id?.startsWith("dz.marw.seat.") == true

    val algeriaUnits: UnitSet = run {
        val cores = mutableMapOf<String, List<AuthorityUnit>>()
        val rests = mutableMapOf<String, AuthorityUnit>()
        for (seat in AlgeriaWilayas.seats) {
            val id = seat.code.toString()
            val held = measuredWilayas[seat.code]
            val point = held?.point ?: seat.point
            val seatMethod = if (held == null) wilayaMethod else held.method
            val fitted = fittedSeat(id, point)?.let { fittedUnit(id, seat.name, point, it) }
            val band = held?.let {
                AuthorityUnit(
                    if (fitted == null) id else "$id-seat", seat.name, point, lateReachKm(point.lat, held.entryClass), held.method,
                    entryClass = held.entryClass.takeIf { it != EntryClass.B }, lateLimits = held.lateLimits,
                    // A band fitted on a derived table (Oran's, Tamanrasset's) is of the calendar's days, as the seats are.
                    measuredThrough = lastHeldDay.takeIf { held.method != null },
                    // Named by the app in the reader's language, never the seat's Latin name (the second audit's G12).
                    named = false,
                )
            }
            val towns = if (seat.code == BOUMERDES) {
                listOfNotNull(fittedSeat("dellys", dellysPoint)?.let { fittedUnit("$id-dellys", "Dellys", dellysPoint, it) })
            } else {
                emptyList()
            }
            // The seat's fitted day first, then Oran's band around it: the first whose reach holds a place is its unit.
            cores[id] = listOfNotNull(fitted, band) + towns
            rests[id] = AuthorityUnit(
                if (cores.getValue(id).isEmpty()) id else "$id-rest", seat.name, point, WILAYA_REACH_KM, seatMethod, measured = false,
                named = false,
            )
        }
        // The units a place without a region can be (ruling R219): the held bands, then every wilaya's rest.
        val computed = UnitSet(
            "dz.marw",
            cores.values.flatten().filter { !it.isFittedSeat } + rests.values.toList(),
        ) { wilayaMethod }
        UnitSet(
            "dz.marw",
            // Cores first, a seat's fitted day before its band: at the same distance the nearest-unit search keeps the first.
            cores.values.flatMap { it.take(1) } + cores.values.flatMap { it.drop(1) } + rests.values.toList(),
            // Ruling R232: the user's own sky beside a seat's fitted day. Ruling R231 as revised: beside every unit
            // fitted on the calendar's days (the seats, Oran's band, Tamanrasset's), the wider day after the last of them.
            around = { user, unit, placed ->
                val own = if (unit.isFittedSeat) ownSky(user, placed) else emptyList()
                val beyond = if (unit.measuredThrough != null) listOf(beyondHeldDays(user, placed)) else emptyList()
                if (own.isEmpty() && beyond.isEmpty()) placed else placed.copy(alongside = placed.alongside + own + beyond)
            },
            choose = { place ->
                val user = GeoPoint(place.lat, place.lon)
                val seat = place.admin1?.let(AlgeriaWilayas::seatFor)
                if (seat == null) {
                    computed.nearest(user)
                } else {
                    val id = seat.code.toString()
                    cores.getValue(id).firstOrNull { distanceKm(user, it.point) <= it.radiusKm } ?: rests.getValue(id)
                }
            },
        ) { wilayaMethod }
    }

    val algeria: RegistryEntry = single(
        id = "dz.marw", nameKey = "authority_marw", entryClass = EntryClass.B, method = algeriaMethod,
        school = AsrSchool.STANDARD, countries = setOf("DZ"),
    )

    // Morocco.

    /**
     * Morocco: the Ministry of Habous and Islamic Affairs, a table for each of about 190 places. Fajr 19° (rounded
     * down), Isha 17°. Every place Habous prints for is a unit of its own ([moroccoUnits], rulings R195–R199), read
     * at Habous's own point for it with its own horizon; this method carries what they share: the national margins
     * for Fajr, Dhuhr, Asr and Isha and the end of eating (the dawn − 2 s), each the largest need of any held day at
     * any of the places (their November and October 2026 month files, the days of 13–30 September, and the nine
     * older cities' pages of 2015–2025) and 5 s more (ruling R197).
     *
     * Sunrise and Maghrib are corrected per place for elevation, so each unit has its own horizon and margins for
     * them ([habous]). This method's own, sunrise −650 s and Maghrib +650 s on the plain horizon, are for a place
     * beyond every unit, whose elevation is not known ([habousEdge]). Class B at the units; the edge is held by no
     * table, only by being no earlier than any unit's own day (a test holds that).
     */
    val moroccoMethod = TimetableMethod(
        id = "ma.habous",
        fajrAngle = 19.0,
        isha = IshaRule.Angle(17.0),
        margins = margins(start = 0, sunrise = -HABOUS_EDGE_SECONDS, fajr = -39, dhuhr = 290, asr = 0, maghrib = HABOUS_EDGE_SECONDS, isha = -10),
        endOfEatingMarginSeconds = -2,
    )

    /**
     * Beyond every Habous table (ruling R199), where neither the place's elevation nor Habous's point for it is
     * known. Sunrise and Maghrib: [HABOUS_EDGE_SECONDS] on the plain horizon, past what the highest place Habous
     * prints for needs by its own unit's horizon and margins on any day of the year (a test holds that at every
     * unit; the −510 s and +510 s this carried until November 2026 were short of it at Habous's mountain places,
     * so those builds could show Maghrib a minute before the printed time there). Fajr, Dhuhr, Asr and Isha: a minute
     * more than the later of two fits, the one at Habous's own points ([moroccoMethod]) and the one at the ten city
     * centres this entry carried before (Fajr −29, Dhuhr 293, Asr 12, Isha −7), so no start here is earlier than it
     * was. The end of eating a minute earlier than the fitted one.
     */
    private val habousEdge = margins(
        start = 0, sunrise = -HABOUS_EDGE_SECONDS, fajr = -29 + 60, dhuhr = 293 + 60, asr = 12 + 60,
        maghrib = HABOUS_EDGE_SECONDS, isha = -7 + 60,
    )

    /** Spec §6.3 (rulings R41, R199): beyond every Habous table, sunrise and Maghrib take a margin for any elevation. */
    private val edgeSunrise = LateLimit(
        EDGE_SUNRISE_MINUTES,
        "Beyond the places Habous prints a table for, the place's elevation is not known, so sunrise takes a national " +
            "margin that covers the highest place Habous prints for: at a low town it is up to " +
            "$EDGE_SUNRISE_MINUTES min before Habous's sunrise.",
        setOf(TimedEvent.SUNRISE),
    )

    private val edgeMaghrib = LateLimit(
        EDGE_MAGHRIB_MINUTES,
        "Beyond the places Habous prints a table for, the place's elevation is not known, so Maghrib takes a national " +
            "margin that covers the highest place Habous prints for: at a low town it is up to " +
            "$EDGE_MAGHRIB_MINUTES min after Habous's Maghrib.",
        setOf(TimedEvent.MAGHRIB),
    )

    /** A place with its own table is held to its class's own limit, not the national margin's. */
    private val fittedCityLimit = LateLimit(
        2,
        "This place's own Habous table gives its sunrise and Maghrib (its elevation), so the national margin for " +
            "other places does not apply here.",
        setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB),
    )

    /**
     * A unit's sunrise and Maghrib where another Habous place's table rides beside its own from a higher horizon
     * (rulings R198, R199): further from its own table than the class's two minutes, at its own point or at an app
     * city inside it.
     */
    private fun riderLimit(minutes: Int) = LateLimit(
        minutes,
        "Another place Habous prints a table for lies within 25 km, and its table is kept beside this place's own so " +
            "that neither is ever shown early: sunrise up to $minutes min before this place's own table and Maghrib up " +
            "to $minutes min after it.",
        setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB),
    )

    /** And its other times, where a rider's table lies to the west of the place's own. */
    private fun riderStarts(events: Set<TimedEvent>) = LateLimit(
        RIDER_STARTS_MINUTES,
        "Another place Habous prints a table for lies within 25 km, and its table is kept beside this place's own so " +
            "that neither is ever shown early: up to $RIDER_STARTS_MINUTES min after this place's own table, the end " +
            "of eating as much before it.",
        events,
    )

    /**
     * One Habous place as a unit (ruling R195): Habous's own point for it, 25 km, the horizon Taqwa reads from its
     * table (the plain one less [HabousCities.City.dip]) and, on that horizon, the sunrise and Maghrib margins that keep
     * every printed minute of its November 2026 table, then a safety: [HABOUS_SAFETY] for the nine cities whose held
     * pages cover other seasons and years, [HABOUS_SEASON_SAFETY] for a place held for one autumn alone. Such a
     * place also takes [HABOUS_SEASON_EXTRA] more on Fajr, Dhuhr, Asr and Isha and as much less on the end of eating,
     * and its table is read at its point and [HABOUS_SEASON_LATITUDE_DEG] north and south of it besides, every start
     * the latest of the three and every end the earliest (ruling R196): its point is fitted from one November, which
     * leaves its latitude open by some hundredths of a degree, and 0.01° of latitude that moves Fajr half a second in
     * November moves it three in June. The band costs little in the months the fit was made in and widens by itself
     * in the seasons the place is not yet held in. Where Habous's point lies off the town
     * ([HabousCities.City.town]) the unit sits at the town and reads its table at Habous's point besides: every start
     * the later of the two, every end the earlier.
     */
    private fun habous(city: HabousCities.City): AuthorityUnit {
        val safety = if (city.seasons) HABOUS_SAFETY else HABOUS_SEASON_SAFETY
        val extra = if (city.seasons) 0 else HABOUS_SEASON_EXTRA
        val band = if (city.seasons) emptyList() else listOf(HABOUS_SEASON_LATITUDE_DEG, -HABOUS_SEASON_LATITUDE_DEG)
        // No name of its own for About and the site (named false, the second audit's G12, as Egypt's F34): Habous's
        // Latin spelling would stand in every language ("في Casablanca"), so the place is called by the app's own name.
        return AuthorityUnit(
            id = city.id, name = city.name, point = city.town ?: city.point, radiusKm = HABOUS_REACH_KM, named = false,
            method = moroccoMethod.copy(
                id = "ma.habous.${city.id}",
                horizonDeg = PLAIN_HORIZON_DEG - city.dip,
                margins = moroccoMethod.margins.copy(
                    fajr = moroccoMethod.margins.fajr + extra, dhuhr = moroccoMethod.margins.dhuhr + extra,
                    asr = moroccoMethod.margins.asr + extra, isha = moroccoMethod.margins.isha + extra,
                    sunrise = city.sunrise - safety, maghrib = city.maghrib + safety,
                ),
                endOfEatingMarginSeconds = moroccoMethod.endOfEatingMarginSeconds - extra,
                fixedPoint = city.town?.let { city.point },
                envelopePoints = band.map { GeoPoint(city.point.lat + it, city.point.lon) },
            ),
            lateLimits = habousLimits(city.id),
        )
    }

    /**
     * The units whose shown sunrise and Maghrib run further from their own table than the class's two minutes
     * (ruling R199), by minutes: the gate's worst at the unit's own point and at every app city inside it, on every
     * held day (ma-habous.tsv). Each has a higher place's table riding beside it (R198); Agoudal has two tables of
     * its own.
     */
    private val habousRiderMinutes: Map<String, Int> = mapOf(
        3 to listOf(
            "rabat", "bouznika", "sidi-yahia-el-gharb", "souk-el-arbaa", "ksar-sghir", "beni-drar", "fezouane",
            "ahfir", "el-gara", "khouribga", "sefrou", "el-menzel", "boured", "tissa", "oued-amlil", "agadir",
            "tine-biougra", "oulad-ayad-tahla", "imi-n-tanout",
        ),
        4 to listOf(
            "sidi-kacem", "sidi-slimane", "martil", "mdiq", "berkane", "selouane", "mohammedia", "kasba-tadla",
            "tahla", "meknes", "agaiouar", "asguine", "ait-ourir",
        ),
        5 to listOf(
            "bouskour", "fes", "matmata", "tifentane", "zaouiat-moulay-brahim",
        ),
        6 to listOf(
            "moulay-yacoub",
        ),
    ).flatMap { (minutes, ids) -> ids.map { it to minutes } }.toMap()

    /** The units where a rider's table also moves another time past two minutes, at an app city inside them. */
    private val habousRiderStarts: Map<String, Set<TimedEvent>> = mapOf(
        "rabat" to setOf(TimedEvent.ASR),
        "ain-el-aouda" to setOf(TimedEvent.ASR),
        "souk-el-arbaa" to setOf(TimedEvent.END_OF_EATING),
    )

    private val agoudalLimit = LateLimit(
        4,
        "Habous prints Agoudal twice, for two horizons. Taqwa follows the table with the earlier sunrise and the " +
            "later Maghrib, so it is never early by either: up to 4 min before the other table's sunrise and after " +
            "its Maghrib.",
        setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB),
    )

    private fun habousLimits(id: String): List<LateLimit> = listOfNotNull(
        if (id == "agoudal") agoudalLimit else habousRiderMinutes[id]?.let(::riderLimit) ?: fittedCityLimit,
        habousRiderStarts[id]?.let(::riderStarts),
    )

    private val habousUnits: List<AuthorityUnit> = HabousCities.cities.map(::habous)

    /**
     * Habous's units. The nearest unit's table holds a place inside its 25 km, and every other unit whose own 25 km
     * holds the place rides as its own day at its own point besides ([Alongside], ruling R198, as Egypt's towns do,
     * ruling R165): every start the later, every end the earlier. Habous's points are fitted to 0.01° and the app's
     * town points lie off them, some by more than the distance between two of Habous's places (Ait Ourir and Kers
     * are 3 km apart), so which table a place follows is not known to the kilometre, and two places 20 km apart can
     * differ by minutes at sunrise and Maghrib (a mountain town's horizon beside a valley's). No table whose reach
     * holds the place is ever shown early; the cost is a sunrise up to some minutes earlier and a Maghrib as much
     * later than the place's own table where a higher neighbour rides ([habousLimits]).
     */
    val moroccoUnits = UnitSet(
        "ma.habous",
        habousUnits,
        around = { user, unit, placed ->
            val riders = habousUnits.filter { it.id != unit.id && distanceKm(user, it.point) <= it.radiusKm }
            if (riders.isEmpty()) {
                placed
            } else {
                placed.copy(
                    alongside = placed.alongside + riders.mapNotNull { other ->
                        other.method?.let { Alongside(it.fixedPoint ?: other.point, it.copy(fixedPoint = null)) }
                    },
                )
            }
        },
    ) {
        moroccoMethod.atEdge("ma.habous.edge", habousEdge)
            .copy(endOfEatingMarginSeconds = moroccoMethod.endOfEatingMarginSeconds - 60)
    }

    /**
     * Ruling R261 (the second trust audit of 14 Nov 2026, finding G6; the Lead's decision under the owner's standard,
     * pending the owner's confirmation, and built as the never-early reading meanwhile): Habous prints a table for Sebta
     * and for Melilla ([HabousCities.spain]), and the app places Ceuta and Melilla in Spain, whose safe default
     * ([Generic.europe]) ended the fast 2 to 3 min after the Fajr Habous prints for them on every held day (its 18° dawn
     * less 2 min, beside Habous's 19°). The cities' Muslims may well pray by Habous's tables, so within
     * [SPAIN_HABOUS_KM] of either table's point its own day (read as a Habous place's, [habous]) rides beside Spain's
     * ([Alongside]): sunrise and the end of eating never after it, and a start later only where Spain's would come
     * before Habous's print (on the days held, none does). The place keeps Spain's entry and its class (D).
     *
     * Held for October and November 2026 alone, November the month the points were fitted on: the monitor does not
     * read villes 24 and 40, and `safe-default-europe-promise.tsv` is kept by hand, so December onward, Ramadan 1448
     * included, rests on that autumn fit until a hand read checks it against Habous (the fit's own day, never later
     * than Spain's alone, is what is shown). The monitor's manual source `ma-habous-spain` (ruling R288) goes due at
     * the start of January 2027 for that read, December 2026 to March 2027 (the owner's choice of 16 Nov 2026: December
     * rests on the autumn fit until then, which he accepted), so the sentence below stays true through Ramadan. The labels follow it since ruling R285 (the re-audit's C3, 15 Nov 2026): such a
     * place names Habous as the body whose table its calculated day is on the safe side of ([spainUnits]' safeSideOf,
     * [world.taqwa.app.prayer.engine.registry.Resolution.safeSideOfTableKey]), on the card and in About, and About says
     * the end of eating is the earlier of Spain's dawn and Habous's own for the city, sunrise never after Habous's.
     */
    val spainHabousTables: List<AuthorityUnit> by lazy { HabousCities.spain.map(::habous) }

    /** Spain's safe default at [user], with Habous's Sebta or Melilla table beside it near either city (ruling R261). */
    internal fun spainAt(user: GeoPoint): TimetableMethod {
        val base = Generic.europe.method
        val near = spainHabousTables.filter { distanceKm(user, it.point) <= SPAIN_HABOUS_KM }
        if (near.isEmpty()) return base
        return base.copy(alongside = near.map { t -> requireNotNull(t.method).let { Alongside(it.fixedPoint ?: t.point, it.copy(fixedPoint = null)) } })
    }

    /**
     * Spain's and its neighbours' safe default ([Generic.europe]), with Habous's tables beside it at Ceuta and Melilla; no
     * unit of its own. Where a table rides (R261), Habous is the body the day is on the safe side of (ruling R285).
     */
    val spainUnits: UnitSet by lazy {
        UnitSet(
            Generic.europe.id, emptyList(), measuredBeyond = true,
            safeSideOf = { user -> HABOUS_NAME.takeIf { spainHabousTables.any { distanceKm(user, it.point) <= SPAIN_HABOUS_KM } } },
        ) { user -> spainAt(user) }
    }

    val morocco: RegistryEntry = single(
        id = "ma.habous", nameKey = HABOUS_NAME, entryClass = EntryClass.B, method = moroccoMethod,
        school = AsrSchool.STANDARD, countries = setOf("MA", "EH"), lateLimits = listOf(edgeSunrise, edgeMaghrib),
    )

    // Mauritania and Sudan.

    /**
     * Mauritania: the ministry's Ramadan imsakiyas only (HIGH for what they show, LOW that a year-round
     * table is followed): Fajr 19.5°, Isha 17.5°, Maghrib = sunset + 2, imsak = Fajr − 5. Fitted on
     * Nouadhibou's 1444 page and widened by Nouakchott's held-out 1445 page (Fajr, Dhuhr and Asr were
     * a minute early there with the first fit). Every start keeps at least the plain safe 30 s after
     * the method itself (two Ramadans are one season): Dhuhr never before transit. Sunrise −124 s, the
     * tables' own (earlier than a −0.83° sunrise). Class D, not measured beyond the two cities' Ramadans.
     */
    val mauritaniaMethod = TimetableMethod(
        id = "mr.ministry",
        fajrAngle = 19.5,
        isha = IshaRule.Angle(17.5),
        authorityMinutes = EventOffsets(maghrib = 2),
        margins = margins(start = SAFE_START, sunrise = -124),
        endOfEatingMarginSeconds = SAFE_END,
        imsakMinutesBeforeFajr = 5,
    )

    val mauritania: RegistryEntry = single(
        id = "mr.ministry", nameKey = "authority_mauritania", entryClass = EntryClass.D_AUTHORITY, method = mauritaniaMethod,
        school = AsrSchool.STANDARD, countries = setOf("MR"),
    )

    private val sudanEndOfEating = LateLimit(
        7,
        "News reports of Sudan's 2026 imsakiya, not the table itself, put its dawn near 19.4°, earlier than the 18.3° " +
            "of the one table held (Khartoum, Ramadan 1443); until a current table is held the fast begins at the 19.5° " +
            "dawn, up to 7 min before that table's Fajr.",
        setOf(TimedEvent.END_OF_EATING),
    )

    /**
     * Ruling R74: a 2026 Ramadan imsakiya circulating under the Fiqh Academy's name (not the table
     * itself, which is Ramadan 1443/2022) puts Isha at Maghrib + 90, 13–19 min after the verified
     * table's 18.0° angle on the research's projected 2024–2030 dates; the gate's own worst day
     * against the held 2022 table (a different season) is 20 min. A floor, never earlier than that
     * table, of the two on the Academy's own dates only: on the verified table's own 30 Ramadan
     * days, the floor is the honest exception.
     */
    private val sudanRamadanIsha = LateLimit(
        20,
        "A 2026 Ramadan imsakiya circulating under the Fiqh Academy's name (not the verified 2022 table) puts Isha " +
            "at Maghrib + 90; on the entry's Ramadan dates only, Isha never earlier than that, up to 20 min after the " +
            "2022 table's 18.0° angle.",
        setOf(TimedEvent.ISHA),
    )

    /**
     * Sudan: the Fiqh Academy's 2022 Khartoum imsakiya implies Fajr ≈ 18.3°, Isha ≈ 18.0°, Dhuhr ≈ +
     * 2 (fitted: +131 s); news reports of 2026, and nothing more, imply ≈ 19.4° (LOW). The Fajr start
     * at 18.3°, the end of eating at 19.5° (the earlier, a recorded exception). Every start keeps at
     * least the plain safe 30 s after the method (one Ramadan's table): Maghrib, which the imsakiya
     * prints about half a minute before the standard sunset, never before it. The Academy calls an
     * imsak before Fajr unfounded, so none. On the entry's Ramadan dates, Isha is also never earlier
     * than Maghrib + 90 min (ruling R74: a 2026 imsakiya circulating under the Fiqh Academy's name,
     * unverified but corroborated by a second, independent republication; a floor, not a replacement,
     * so the 18.0° angle still wins where it falls later — never asserted for any other month). Class
     * D.
     */
    val sudanMethod = TimetableMethod(
        id = "sd.ministry",
        fajrAngle = 18.3,
        isha = IshaRule.Angle(18.0),
        margins = margins(start = SAFE_START, sunrise = SAFE_SUNRISE, dhuhr = 131),
        ramadan = RamadanRule(ishaFloorMinutesAfterMaghrib = 90),
        endOfEating = EndOfEating.DawnAngle(19.5),
        endOfEatingMarginSeconds = SAFE_END,
    )

    val sudan: RegistryEntry = single(
        id = "sd.ministry", nameKey = "authority_sudan_fiqh", entryClass = EntryClass.D_AUTHORITY, method = sudanMethod,
        school = AsrSchool.STANDARD, countries = setOf("SD"), lateLimits = listOf(sudanEndOfEating, sudanRamadanIsha),
    )

    /**
     * Eastern and southern Libya follow the local adhan for Fajr and Maghrib, not the national
     * method computed at each city (ruling R73). The owner heard it on 27 Sep 2026, 700 km apart,
     * at Benghazi and at Sabha: each exactly the 19.5° dawn and sunset + 1 minute, floored, and
     * 7 (Fajr) and 4 (Maghrib) minutes earlier than the national method there — safe (never early)
     * but not what the mosques call. So there Fajr is the 19.5° dawn with the plain safe margin
     * (like Mauritania and Sudan), never before it; Maghrib is sunset + 1 minute with the same
     * margin, never before it either; the end of eating stays that 19.5° dawn as before, its own
     * margin unchanged; imsak, still "dawn − 11", follows along since the dawn it reads is now the
     * 19.5° one. Beyond 30 km of every city (the edge, rulings R44 and R45) Fajr and Maghrib keep
     * the edge's minute on top of that safe margin, as every other start there does. Dhuhr, Asr,
     * Isha and sunrise keep the national method. Fajr and Maghrib are measured only at the owner's
     * two observations (`open/LY-AWQAF-OWNER`); the gate no longer holds the national table's Fajr
     * and Maghrib columns there against these cities (their other columns still checked). The
     * west's fitted margin, unaffected, is for the 18.5° dawn at its tables.
     */
    val variants = listOf(
        MethodVariant("ly.awqaf", Regions.libyaEastSouth) {
            it.copy(
                fajrAngle = 19.5,
                authorityMinutes = it.authorityMinutes.copy(maghrib = 1),
                margins = it.margins.copy(
                    fajr = SAFE_START + beyondNational(it.margins.fajr, libyaMethod.margins.fajr),
                    maghrib = SAFE_START + beyondNational(it.margins.maghrib, libyaMethod.margins.maghrib),
                ),
                endOfEating = EndOfEating.DawnAngle(19.5),
                endOfEatingMarginSeconds = minOf(it.endOfEatingMarginSeconds, SAFE_END),
            )
        },
    )

    /**
     * What a place's start margin carries beyond the national one: nothing at a city (every unit's
     * method shares [libyaMethod]'s margins), the edge's minute beyond them. Never less than nothing,
     * so the variant's safe margin is never narrowed.
     */
    private fun beyondNational(margin: Int, national: Int): Int = maxOf(0, margin - national)

    val entries = listOf(libya, tunisia, algeria, morocco, mauritania, sudan)

    /** Habous's nine cities held over several seasons and years: 20 s more than the fit on sunrise and Maghrib. */
    private const val HABOUS_SAFETY = 20

    /** A Habous place held for one autumn alone (October and November 2026): 40 s more on sunrise and Maghrib. */
    private const val HABOUS_SEASON_SAFETY = 40

    /**
     * How far from Habous's point for Sebta or Melilla its table rides beside Spain's day (ruling R261): each city and
     * the ground around it (Ceuta's Benzú is about 7 km from the point), short of the Spanish mainland across the strait
     * (Tarifa about 28 km off) and of Gibraltar.
     */
    private const val SPAIN_HABOUS_KM = 10.0

    /** Habous's short name's key: its entry's, and the body a Ceuta or Melilla day is on the safe side of (ruling R285). */
    private const val HABOUS_NAME = "authority_habous"

    /** And 10 s more than the national margins on its Fajr, Dhuhr, Asr and Isha, as much less on its end of eating. */
    private const val HABOUS_SEASON_EXTRA = 10

    /**
     * And its table read this far north and south of its fitted point besides, in degrees of latitude (5.5 km): four
     * times the furthest a November fit sat from the year's at the nine cities held in every season (0.02°, read
     * from Fajr's 8 s of summer drift there).
     */
    private const val HABOUS_SEASON_LATITUDE_DEG = 0.05

    /** Sunrise and Maghrib beyond every Habous table: past the highest printed place's need on any day of the year. */
    private const val HABOUS_EDGE_SECONDS = 650
    private const val EDGE_SUNRISE_MINUTES = 9
    private const val EDGE_MAGHRIB_MINUTES = 8
    private const val HABOUS_REACH_KM = 25.0
    private const val RIDER_STARTS_MINUTES = 3
    private const val PLAIN_HORIZON_DEG = -0.8333
    private const val LATE_ASR_SECONDS = 450
    private const val WILAYA_REACH_KM = 700.0

    /** Ruling R217: how far from its seat a wilaya's fitted day is the place's unit (the 20 km of R163's cities; ring-checked, ruling R232). */
    private const val SEAT_REACH_KM = 20.0

    /** A whole minute of margin, in seconds ([beyondHeldDays]). */
    private const val MINUTE = 60

    /** Boumerdès's official number: the wilaya Dellys is a town of. */
    private const val BOUMERDES = 35
}
