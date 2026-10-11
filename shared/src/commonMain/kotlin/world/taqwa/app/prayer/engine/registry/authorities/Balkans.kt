package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.domain.Prayer
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
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import world.taqwa.app.prayer.engine.registry.data.BalkanCurves
import world.taqwa.app.prayer.engine.registry.margins
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened

/**
 * Bosnia, Kosovo, Albania and Montenegro (research Balkans sections). All print the first-shadow
 * Asr, round to the nearest minute, and print one morning time that is both the Fajr (or, in Kosovo,
 * the imsak it is counted from) and the end of eating. Fitted and proven by Task 7b
 * (proof/7b-turkiye-balkans-russia.md).
 *
 * Where a table is printed for a point, the app follows it there by curves and monthly offsets
 * derived from it (rulings R39, R42): its dawn, end of eating and Isha as depression curves
 * ([BalkanCurves]), and its sunrise, Dhuhr, Asr and Maghrib as the sun's plus a per-month offset in
 * seconds (the table's lead or lag over the sun, never early and never late on any of its days, 5 s
 * to spare). The start curves carry −29 s and the end curve + 30 s, since each is derived 25 s before
 * the printed minute: the start then shows that minute and the end never passes the Fajr at either of
 * two points (ruling R15).
 */
object Balkans {

    /** Per-month offsets in seconds for sunrise, Dhuhr, Asr and Maghrib (index month − 1). */
    private fun monthly(sunrise: IntArray, dhuhr: IntArray, asr: IntArray, maghrib: IntArray, fajr: IntArray? = null, isha: IntArray? = null) =
        buildMap {
            put(Prayer.SUNRISE, sunrise)
            put(Prayer.DHUHR, dhuhr)
            put(Prayer.ASR, asr)
            put(Prayer.MAGHRIB, maghrib)
            fajr?.let { put(Prayer.FAJR, it) }
            isha?.let { put(Prayer.ISHA, it) }
        }

    /**
     * [base] following a table printed for one point: its curves, its monthly offsets, and the curves'
     * margins; [slack] seconds later on every start and earlier on the end of eating, [sunriseSlack]
     * earlier on sunrise, for places whose own table is that one plus whole-minute offsets.
     */
    private fun printedAt(
        base: TimetableMethod,
        id: String,
        curves: BalkanCurves.Curves,
        offsets: Map<Prayer, IntArray>,
        slack: Int = 0,
        sunriseSlack: Int = slack,
    ) = base.copy(
        id = id,
        fajrAngleByDayOfYear = curves.fajr,
        ishaAngleByDayOfYear = curves.isha,
        endOfEating = EndOfEating.DawnAngle(base.fajrAngle, curves.end),
        monthlyOffsets = offsets.mapValues { (prayer, v) -> IntArray(12) { v[it] + if (prayer == Prayer.SUNRISE) -sunriseSlack else slack } },
        margins = margins(start = slack, sunrise = 0, fajr = CURVE_START + slack, isha = CURVE_START + slack),
        endOfEatingMarginSeconds = CURVE_END - slack,
    )

    /**
     * Bosnia and Herzegovina: the IZ BiH vaktija, which "has the force of a fatwa" (HIGH; Takvim 2025):
     * zora (Fajr and the start of the fast) 18°, jacija 16°, sunrise − 6, akšam + 6 "for a relative
     * elevation of 920 m", podne + 1–2, first-shadow Asr, nearest rounding; 19°/17° until 1 Jan 2025.
     * Each town is Sarajevo plus whole-minute monthly offsets, which drift from the sun: Banja Luka's
     * and Bihać's zora runs 1–7 min after the real 18° dawn, their Isha up to 7 min before 16°.
     *
     * Units: Sarajevo, Banja Luka and Bihać, each following its own 2026 table ([printedAt]): class B,
     * not A, since each town's parameters come from its own table and only two live days are held out
     * (Banja Luka's and Bihać's drift within the month is a recorded exception of 3 min). Elsewhere (spec §6.2 a, class D, no figure claimed) the plain angles with per-month offsets
     * a minute beyond the latest of the three towns' for the starts and the earliest for the ends, the
     * fast at the 18° dawn 73 s early. Towns south of Sarajevo carry a sign error in IZ's offsets,
     * printing a dawn before the real one; Taqwa keeps the real dawn (spec §10.8), a recorded
     * exception on the units for the southern towns in the app's list (no table held for them).
     * Sandžak uses it (Regions).
     */
    val bosniaMethod = TimetableMethod(
        id = "ba.iz",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(16.2),
        authorityMinutes = EventOffsets(sunrise = -6, dhuhr = 2, maghrib = 6),
        endOfEating = EndOfEating.DawnAngle(18.0),
        endOfEatingMarginSeconds = -73,
        monthlyOffsets = monthly(
            fajr = intArrayOf(180, 178, 169, 230, 369, 460, 436, 307, 226, 173, 208, 215),
            sunrise = intArrayOf(-17, -6, -51, -65, -64, -135, -192, -150, -154, -125, -94, -62),
            dhuhr = intArrayOf(-10, -6, -3, -11, -26, -32, -29, -20, -15, -12, -18, -6),
            asr = intArrayOf(35, 46, 154, 139, 177, 178, 233, 162, 205, 125, 46, 45),
            maghrib = intArrayOf(19, 34, 42, 70, 68, 57, 147, 171, 190, 112, 69, 38),
            isha = intArrayOf(-69, -80, -82, -104, -128, -85, -12, 17, 19, 6, -15, -43),
        ),
    )

    private val sarajevoMethod = printedAt(
        bosniaMethod, "ba.iz.sarajevo", BalkanCurves.sarajevo,
        monthly(
            sunrise = intArrayOf(58, 54, 48, 27, -2, -64, -79, -65, -79, -46, -34, -2),
            dhuhr = intArrayOf(-93, -90, -87, -95, -110, -116, -113, -104, -99, -96, -102, -90),
            asr = intArrayOf(-58, -52, 6, -15, 0, -31, 0, -8, 8, 28, -15, 4),
            maghrib = intArrayOf(-58, -49, -58, -62, -55, -53, 13, 48, 28, 17, 9, -22),
        ),
    )

    private val banjaLukaMethod = printedAt(
        bosniaMethod, "ba.iz.banja-luka", BalkanCurves.banjaLuka,
        monthly(
            sunrise = intArrayOf(72, 94, 31, -5, 10, -63, -118, -72, -94, -39, 35, 61),
            dhuhr = intArrayOf(-70, -66, -63, -71, -86, -92, -89, -80, -75, -72, -78, -66),
            asr = intArrayOf(-25, -14, 94, 40, 117, 118, 173, 102, 145, 65, -14, -15),
            maghrib = intArrayOf(-41, -26, -18, -27, 8, -3, 87, 111, 130, 52, -5, -50),
        ),
    )

    private val bihacMethod = printedAt(
        bosniaMethod, "ba.iz.bihac", BalkanCurves.bihac,
        monthly(
            sunrise = intArrayOf(43, 69, 9, 37, -4, -75, -132, -90, -55, -65, 7, 32),
            dhuhr = intArrayOf(-90, -87, -84, -92, -106, -113, -110, -100, -95, -92, -98, -87),
            asr = intArrayOf(-38, -29, 76, 79, 93, 93, 149, 81, 127, 49, -28, -27),
            maghrib = intArrayOf(-53, -42, -39, 10, -18, -32, 58, 87, 111, 36, -18, -63),
        ),
    )

    private val bosniaEdge = bosniaMethod.copy(id = "ba.iz.edge")

    /** A town south of Sarajevo in the app's list: IZ's own offsets there are not held (spec §10.8). */
    private fun southern(id: String, name: String, lat: Double, lon: Double) = AuthorityUnit(
        id, name, GeoPoint(lat, lon), 20.0, method = bosniaEdge, measured = false,
        lateLimits = listOf(LateLimit(24, SOUTHERN_DAWN, setOf(TimedEvent.FAJR))),
    )

    /** Banja Luka's and Bihać's monthly offsets from Sarajevo drift from the sun within a month. */
    private val townDrift: LateLimit
        get() = LateLimit(3, TOWN_DRIFT, setOf(TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.END_OF_EATING))

    private const val TOWN_DRIFT =
        "IZ BiH prints this town as Sarajevo plus a whole-minute offset for each month, which drifts from the sun " +
            "within the month; the app follows the month's offset on the safe side, up to 3 min from the printed time."

    private const val SOUTHERN_DAWN =
        "IZ BiH's town offsets carry the latitude term with the wrong sign south of Sarajevo, so its printed zora " +
            "can come before the real dawn, by about 16 min at Trebinje in June; Taqwa keeps the real dawn and, as " +
            "everywhere beyond IZ's three held towns, the month's margin over their drift (3–8 min), about 24 min " +
            "after the printed zora there in June."

    val bosniaUnits = UnitSet(
        "ba.iz",
        listOf(
            AuthorityUnit("sarajevo", "Sarajevo", GeoPoint(43.84864, 18.35644), 25.0, sarajevoMethod),
            AuthorityUnit("banja-luka", "Banja Luka", GeoPoint(44.77879, 17.20629), 20.0, banjaLukaMethod, lateLimits = listOf(townDrift)),
            AuthorityUnit("bihac", "Bihać", GeoPoint(44.81694, 15.87083), 20.0, bihacMethod, lateLimits = listOf(townDrift)),
            southern("mostar", "Mostar", 43.34333, 17.80806),
            southern("konjic", "Konjic", 43.65126, 17.96082),
            southern("gorazde", "Goražde", 43.66795, 18.97564),
            southern("trebinje", "Trebinje", 42.71197, 18.34362),
            southern("novi-pazar", "Novi Pazar", 43.13667, 20.51222),
        ),
    ) { bosniaEdge }

    val bosnia: RegistryEntry = single(
        id = "ba.iz", nameKey = "authority_iz_bih", entryClass = EntryClass.B, method = bosniaMethod,
        school = AsrSchool.STANDARD, countries = setOf("BA"),
    )

    /**
     * Kosovo: the BIK Takvimi, an official document for Kosovo and the Preševo valley (MEDIUM): one
     * table for 42.5° N 21° E with town offsets of −2..+2 min; sunrise − 6 and sunset + 6 ("1.5° –
     * 6 min"); imsak ends the fast and the Sabah prayer is imsak + 20 min; an irregular old perpetual
     * table (its imsak between 18.5° and 19.7°, its Asr 2–11 min after the first shadow). The app
     * follows the table at any point in Kosovo ([printedAt] at its base point) with 30 s more either
     * way for the towns' whole-minute offsets (a minute on sunrise). Class D: never early nor late on
     * its base table and Prishtina's held out, but its uneven steps put Dhuhr, sunrise, Maghrib and Asr
     * up to 4–6 min from it on some days (recorded exceptions).
     */
    private val kosovoBase = TimetableMethod(
        id = "xk.bik",
        fajrAngle = 19.0,
        isha = IshaRule.Angle(18.5),
        fajrAfterDawnMinutes = 20,
        authorityMinutes = EventOffsets(sunrise = -6, dhuhr = 5, maghrib = 6),
    )

    val kosovoMethod = printedAt(
        kosovoBase, "xk.bik", BalkanCurves.kosovo,
        monthly(
            sunrise = intArrayOf(121, -31, -77, -117, -149, -214, -261, -157, -155, -134, 31, 176),
            dhuhr = intArrayOf(-38, -53, -63, -138, -167, -107, -167, -96, -57, -71, -48, 5),
            asr = intArrayOf(520, 389, 257, 138, 176, 194, 190, 256, 336, 512, 497, 646),
            maghrib = intArrayOf(34, 78, 84, 69, 21, 100, 55, 124, 139, 141, 60, 103),
        ),
        slack = TOWN_OFFSET,
        sunriseSlack = TOWN_SUNRISE,
    )

    val kosovo: RegistryEntry = single(
        id = "xk.bik", nameKey = "authority_bik", entryClass = EntryClass.D_AUTHORITY, method = kosovoMethod,
        school = AsrSchool.STANDARD, countries = setOf("XK"), measured = true,
        lateLimits = listOf(
            LateLimit(4, PERPETUAL_STEPS, setOf(TimedEvent.DHUHR)),
            LateLimit(5, PERPETUAL_STEPS, setOf(TimedEvent.SUNRISE, TimedEvent.MAGHRIB)),
            LateLimit(6, PERPETUAL_STEPS, setOf(TimedEvent.ASR)),
        ),
    )

    /** The Takvimi's base point. */
    private val BIK_BASE = GeoPoint(42.5, 21.0)

    private val DRAGASH = GeoPoint(42.0265, 20.65333)
    private val SHTERPCE = GeoPoint(42.2394, 21.0272)

    /** One place of BIK's town list: [minutes] after the Takvim (its "T"), at [point], the app's city of that name. */
    private class BikTown(val name: String, val point: GeoPoint, val minutes: Int)

    /** BIK's towns printed earlier than the Takvim (the Takvimi's "Diferenca e vendeve"). */
    private val earlierTowns = listOf(
        BikTown("Ferizaj", GeoPoint(42.37056, 21.15528), -1),
        BikTown("Gjilan", GeoPoint(42.46045, 21.46986), -1),
        BikTown("Prishtina", GeoPoint(42.67272, 21.16688), -1),
        BikTown("Podujeva", GeoPoint(42.91056, 21.19306), -1),
        BikTown("Vushtrri", GeoPoint(42.82306, 20.9675), -1),
        BikTown("Presheva", GeoPoint(42.3092, 21.6497), -2),
    )

    /**
     * The Takvimi's base table is printed for its own point, 42.5° N 21° E (xk-bik.tsv; rulings R30, R40 and R44): within
     * class D's reach of it (about 61 km, most of Kosovo) that point rides beside the user's, so the app is never before
     * the base table there. The city-point audit of 10–11 Oct 2026 found the app's own Shtime, 8 km from the base point, an
     * Isha a minute before it on 16 days, and Kosovo Polje's and Glogovac's Fajr before it on more.
     *
     * BIK's own town list (the Takvimi's "Diferenca e vendeve", p. 5 of its 2024 and 2026 prints, held at
     * archive/raw/manual/xk-bik/2026-10-11/): the Takvim applies to the whole of Kosovo, with small differences in a few
     * places, "T" plus or minus whole minutes: Sharri T + 2; Ferizaj, Gjilani, Prishtina, Podujeva and Vushtrria T − 1;
     * Presheva T − 2. Every other place prints the Takvim itself (the west's Peja, Gjakova and Prizren included, whose own
     * sun is later). So, like the PA's printed town figures (rulings R118, R120), a town's table is the base table plus
     * its figure, and that table rides beside the user's own point (the owner's rule, 11 Oct 2026: rather late, never
     * early, and never earlier than before): anywhere in Kosovo the base point rides for every start and end; in Sharri a
     * start point at the base's latitude [SHARRI_WEST_DEG] west of it gives the Takvim plus two minutes (with a few
     * seconds to spare for the rounding); within [BIK_TOWN_KM] of a town BIK prints earlier, an end point east of it gives
     * the Takvim less that town's minutes for sunrise and the end of eating. "Sharri" is read both ways it is used, the
     * Sharr (Dragash) municipality, Kosovo's south-west tip, and Shtërpcë under the Sharr mountains: either way its starts
     * are never before T + 2. The figure stays the base unit's, within class D's reach; beyond it, out to [KOSOVO_KM]
     * (every place in Kosovo), the same construction with no figure, where the first round had the user's own point alone
     * (Leposaviq, Istok and Dragash's south a minute or more before the Takvim on some days, the review's probe).
     */
    val kosovoUnits: UnitSet = run {
        val unit = AuthorityUnit(
            "base", "the Takvimi's base point", BIK_BASE, lateReachKm(BIK_BASE.lat, EntryClass.D_AUTHORITY),
            named = false, endsBoundAtUserPoint = true,
            lateLimits = listOf(
                LateLimit(7, BASE_REACH, setOf(TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ISHA)),
                LateLimit(8, BASE_REACH, setOf(TimedEvent.SUNRISE, TimedEvent.ASR, TimedEvent.MAGHRIB)),
                LateLimit(9, BASE_REACH, setOf(TimedEvent.END_OF_EATING)),
            ),
        )
        UnitSet(kosovo.id, listOf(unit), around = { user, _, placed -> withTownFigures(placed, user) }) { user ->
            if (distanceKm(user, BIK_BASE) > KOSOVO_KM) {
                kosovoMethod
            } else {
                withTownFigures(kosovoMethod.copy(fixedPoint = BIK_BASE, endsBoundAtUserPoint = true), user)
            }
        }
    }

    /** Whether [user] is in Sharri, which BIK prints at the Takvim plus two minutes: the Sharr (Dragash) municipality or Shtërpcë. */
    private fun inSharri(user: GeoPoint): Boolean =
        (distanceKm(user, DRAGASH) <= SHARRI_KM && user.lat <= SHARRI_NORTH_LAT) || distanceKm(user, SHTERPCE) <= SHTERPCE_KM

    /**
     * [placed] (the base point riding beside [user]) with BIK's town figures: in Sharri a start point giving the Takvim
     * plus two minutes; near a town BIK prints earlier, an end point giving the Takvim less its minutes.
     */
    private fun withTownFigures(placed: TimetableMethod, user: GeoPoint): TimetableMethod {
        val starts = if (inSharri(user)) listOf(GeoPoint(BIK_BASE.lat, BIK_BASE.lon - SHARRI_WEST_DEG)) else emptyList()
        val earliest = earlierTowns.filter { distanceKm(user, it.point) <= BIK_TOWN_KM }.minOfOrNull { it.minutes }
        val ends = earliest?.let { listOf(GeoPoint(BIK_BASE.lat, BIK_BASE.lon + (-it + TOWN_FIGURE_SPARE_MINUTES) * DEG_PER_MINUTE)) }
            ?: emptyList()
        if (starts.isEmpty() && ends.isEmpty()) return placed
        return placed.copy(startPoints = placed.startPoints + starts, endPoints = placed.endPoints + ends)
    }

    /** The Sharr (Dragash) municipality: Kosovo's south-west tip, within this many km of Dragash and south of [SHARRI_NORTH_LAT]. */
    private const val SHARRI_KM = 25.0
    private const val SHARRI_NORTH_LAT = 42.15

    /** Shtërpcë's municipality, under the Sharr mountains, within this many km of the town. */
    private const val SHTERPCE_KM = 12.0

    /** How near a town BIK prints earlier its earlier ends apply (a municipality's extent, generously: an earlier end is safe). */
    private const val BIK_TOWN_KM = 20.0

    /** Every place in Kosovo is within this many km of the base point (its farthest corners about 90). */
    private const val KOSOVO_KM = 100.0

    /** A minute of time is a quarter degree of longitude at one latitude. */
    private const val DEG_PER_MINUTE = 0.25

    /** A town figure's point lies this many minutes farther, so that its whole minutes survive the rounding. */
    private const val TOWN_FIGURE_SPARE_MINUTES = 0.04

    /** Sharri's two minutes, with [TOWN_FIGURE_SPARE_MINUTES] to spare, as degrees west of the base point. */
    private const val SHARRI_WEST_DEG = (2 + TOWN_FIGURE_SPARE_MINUTES) * DEG_PER_MINUTE

    private const val BASE_REACH =
        "The base table's point rides across Kosovo beside the user's (rulings R30 and R44), with BIK's own town figures " +
            "(the Takvimi's \"Diferenca e vendeve\": Sharri T + 2, Ferizaj, Gjilani, Prishtina, Podujeva and Vushtrria T - 1, " +
            "Presheva T - 2): west of it, where BIK prints the Takvim itself, the user's own sun is later, in Sharri every " +
            "start is at least T + 2, and north or south of it the night's length moves the dawn and the dusk, so against " +
            "the base table itself the times run up to this many minutes from it, on top of its own uneven steps."

    private const val PERPETUAL_STEPS =
        "The Takvimi is an old perpetual table whose minutes step unevenly from day to day; the app follows each " +
            "month of it on the safe side, with half a minute more for the towns' whole-minute offsets, and so runs " +
            "up to this many minutes from its printed time on some days."

    /**
     * Albania: KMSH's Kalendari 2026, identical to Diyanet's Tirana table (HIGH): Diyanet's algorithm
     * with 18°/17° and its temkin, first-shadow Asr, Tirana plus constant town offsets. At the Tirana
     * unit, margins fitted on KMSH's 2026 table (5 s safety) and held out on Diyanet's own for Sep
     * 2026 – Dec 2027, worst 1: class A, Diyanet's method rebuilt exactly, within R40's reach of a
     * minute (about 21 km, KMSH's other towns printing their own). Elsewhere 60 s more for the town
     * offsets (class D).
     */
    val albaniaMethod = Diyanet.method.copy(
        id = "al.kmsh",
        margins = margins(start = -24, sunrise = 21, fajr = -22, asr = -23, maghrib = -23, isha = -21),
        endOfEatingMarginSeconds = 20,
    )

    private val TIRANA = GeoPoint(41.32744, 19.81866)

    val albaniaUnits = UnitSet(
        "al.kmsh",
        listOf(AuthorityUnit("tirana", "Tirana", TIRANA, lateReachKm(TIRANA.lat, EntryClass.A))),
    ) { albaniaMethod.atEdge("al.kmsh.edge", albaniaMethod.margins.widened(60)) }

    val albania: RegistryEntry = single(
        id = "al.kmsh", nameKey = "authority_kmsh", entryClass = EntryClass.A, method = albaniaMethod,
        school = AsrSchool.STANDARD, countries = setOf("AL"),
    )

    /**
     * Montenegro: the Islamic Community's own table (agent-reported, LOW), about 19°/18°: one month of
     * it held (Podgorica, September 2026), which puts its dawn about 2 min before 19°, its sunrise
     * 7.5 min before the sun's, its Dhuhr a minute after the transit, its Asr at the first shadow and
     * its Maghrib 7.5 min after sunset. Margins fitted on that month, widened for the seasons not
     * held: 2 min more at the dawn, the end of eating and Isha, whose angles are not known, 30 s more
     * elsewhere. Class D, no figure claimed.
     */
    val montenegroMethod = TimetableMethod(
        id = "me.izcg",
        fajrAngle = 19.0,
        isha = IshaRule.Angle(18.0),
        authorityMinutes = EventOffsets(maghrib = 8),
        margins = margins(start = 0, sunrise = -489, fajr = -17, dhuhr = 63, asr = 6, maghrib = 8, isha = 49),
        endOfEatingMarginSeconds = -223,
    )

    val montenegro: RegistryEntry = single(
        id = "me.izcg", nameKey = "authority_izcg", entryClass = EntryClass.D_AUTHORITY, method = montenegroMethod,
        school = AsrSchool.STANDARD, countries = setOf("ME"),
    )

    val entries = listOf(bosnia, kosovo, albania, montenegro)

    /** The start margin on a start curve and the end margin on an end curve (each derived 25 s before the minute). */
    private const val CURVE_START = -29
    private const val CURVE_END = 30

    /**
     * Half a minute either way for a Kosovan town's whole-minute offset from the base table; a minute
     * on sunrise, where Prishtina's table held out ran 24 s earlier than the half minute gives in
     * winter (the excess and 5 s).
     */
    private const val TOWN_OFFSET = 30
    private const val TOWN_SUNRISE = 59
}
