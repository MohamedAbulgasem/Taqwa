package world.taqwa.app.prayer.engine.registry.data

import world.taqwa.app.prayer.engine.method.EventOffsets
import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.PrintedLater
import world.taqwa.app.prayer.engine.registry.TimedEvent

/**
 * JAKIM's fitted zones, every zone of e-solat's list, sixty: the first thirteen ([first]), the 37 of the zone round
 * of 13 Nov 2026 ([towns]) and the ten of its review round, which hold no city of the app's list ([named]). Derived
 * parameters, not official times. The first thirteen (Task 7c: refitted with
 * `Fitter.fit("my.jakim/<zone>")` against each zone's own 2025 mirror table and its 2026 e-solat
 * table, safety 5 s, over the union of both years since a single year's fit does not always cover
 * the other — see `docs/research/2026-09-prayer-times/proof/7c-sea.md`; SBH06 was left out then for its 2026
 * breaks and is a unit since the review round, held as printed, [Why.BROKEN_RUN]). Margins are our convention:
 * starts ⌈raw + m⌉, sunrise ⌊raw + m⌋.
 *
 * - WLY01 and SGR01 share Selangor's starts: the later of a south point (2.62, 101.69) and a
 *   north-west point (3.73, 101.45), fitted (JAKIM's Lampiran B names Tg Rhu Sepang and Kg Gedangsa).
 *   Their sunrise takes SGR01's margin, the earlier, since the two zones' syuruk points differ and a
 *   Selangor suburb may sit nearer KL's point.
 * - KTN01's Isha is a genuine 17° ([ishaAngleDeg]), not the 18° base with a negative margin: fitting
 *   the real angle needs a small, ordinary margin instead of one that tracks the declination's own
 *   drift only by luck.
 * - PHG06 (Cameron Highlands) is computed at a −2.5° horizon (research, MEDIUM: elevation depresses
 *   the visible horizon, so sunrise is earlier and sunset later there than at sea level, matching the
 *   in-sample residual). Its Maghrib keeps a small positive margin rather than the fitted floor
 *   (which needs about −67 s over 2025–2026): the invariant `maghrib ≥ sunset` compares against the
 *   unmargined, floor-rounded sunset at the same horizon, and a margin that negative ceils below it
 *   on some days (out of order, repaired later at runtime). A positive margin stays on the safe,
 *   never-early side (only later, which class B allows) without ever repairing.
 * - 2027 refit (4 Nov 2026, the monitor's first 2027 tables): WLY01, PLS01 and PNG01 refitted on
 *   2025–2027 together, each margin only ever later for a start and earlier for an end. WLY01's 2027
 *   Subuh prints up to 3 min before 2025–2026's at the same point, so its Fajr carries a 3-min late
 *   exception ([Why.YEAR_SHIFT]) rather than an earlier margin, which would be early in 2025–26.
 * - PLS01's sunrise carries its own exception ([Why.PRINT_SPREAD], 4 min): Perlis's printed sunrise
 *   spreads up to 4 min from a plain classic-noon sunrise across the seasons (3 min, a minute past class B,
 *   on close to 30 % of the days checked, 2025–2026); every other zone of the first thirteen stays inside 2 min.
 * - The imsak (the JAKIM imsak round of 12 Nov 2026): e-solat prints Subuh − 10, so each zone's
 *   [imsakMarginSeconds] is fitted as an end on every Subuh held, 2025–2027, read as Subuh − 10, and
 *   on the printed imsak of 2026–2027 (`Fitter.fit("my.jakim/<zone>")` over its fit and its test rows,
 *   the smaller), safety 5 s: never after e-solat's imsak, and 0–2 min before it on every gated day
 *   (2 on 104 of 6,935). Kuala Lumpur's 2027 Subuh prints 1–3 min before 2025's and 2026's at the
 *   same point (the 2027 table decides its −125 s), so its 2026 imsak is 2–3 min before e-solat's:
 *   a 3-min imsak exception at Kuala Lumpur ([Why.YEAR_SHIFT] on the imsak), approved by the owner on 12 Nov 2026
 *   (ruling R187), an earlier end and never a later one, and that row is back in the gate (my-jakim.tsv). No imsak day is a holdout: every one, its `test` split included, sets the
 *   margin, so the stamp's held-out imsak is in-sample (a fit on 2025 alone would be after e-solat's 2027 imsak).
 * - The zone round of 13 Nov 2026 (ruling R225): every other zone that holds a city of the app's list ([towns], 37
 *   zones), each on its own e-solat tables of 2026 and 2027 (seven have no 2027 table on e-solat yet: SBH03, SBH04,
 *   SBH05, SBH08, SBH09, SGR02 and SGR03, their 2026 table split January to September and October to December). Each
 *   zone's point is its first town in the app's list. Its [Zone.startPoints] is a fitted effective point, not
 *   JAKIM's reference point (which is not held): the point of a search around the zone at which the printed starts
 *   fit with the smallest margins, two points in SGR03. Several lie outside the zone (KDH02 to KDH06, SGR02 and
 *   NGS03: at sea or in a neighbouring zone), since a table that is the latest of several points, or stepped, fits
 *   best from beyond its own edge. The margins are over the union of everything held, as the first thirteen's: a
 *   fit on the 2026 rows alone was clean on the days it did not see in 32 of the 37 zones, and in KDH02, KDH04, KDH05,
 *   NGS02 and PRK02 it was not (a start before the print on one day each in KDH04 and NGS02, an end after it on one
 *   to three days in the others), so every day held sets the margins: NO day of a zone's own table is held out,
 *   and the stamp's "held-out" days are in-sample for them. At the zones' other towns in the app's list
 *   (`my-jakim-cities.tsv`) no margin was fitted, but the print they are read against is the zone's own, the one
 *   the margins were fitted on: what those rows give is the LATENESS at each town, measured; that no town is early
 *   follows from the unit's fit and the place's own sun, and the rows confirm it, they are not an independent test.
 * - The review round of 13 Nov 2026: the ten zones that hold no city ([named]) became units too, on their 2026
 *   tables alone (plain GETs), so that no place e-solat lists is served on a neighbouring zone's table while its
 *   own goes unread; every zone that could own a place rides beside the zone taken (`Jakim.withNeighbours`), the
 *   named border places are proven in `my-jakim-envelope.tsv`. A zone read on one year alone ([Zone.oneYear]:
 *   those ten and the seven above, seventeen) carries [FORWARD_SECONDS] more on every margin, to its safe side.
 * - SBH09 (Sipitang) prints 28 to 31 March 2026 LATER than the days around them (Subuh and Syuruk 10–11 min, Zohor
 *   6–7, Asar 19–20, Maghrib 3 min later, Isyak in line). They are held as printed ([Zone.printedLater]; ruling
 *   R228 as revised), never left out: on those four days no start is before the print, and the sunrise, the end of
 *   eating and the imsak, which are never moved later, come up to 13 min before the print
 *   ([Why.PRINTED_LATER]). No other day of the year moves.
 * - The zones' own late limits (ruling R227): [Why.STEPPED_TABLE] for Kedah's KDH02, KDH04 and KDH05, whose tables
 *   shift for stretches of days, and [Why.TOWNS] where a zone is wide and a town far from the point its print fits
 *   (the sunrise and the dawn before the print in the east, a start after it in the west). Each is a measured figure
 *   of the gate, recorded, never a start moved earlier.
 */
object JakimZones {
    class Zone(
        val id: String,
        val city: String,
        val point: GeoPoint,
        val radiusKm: Double,
        val margins: EventOffsets,
        val horizonDeg: Double = -0.8333,
        val startPoints: List<GeoPoint> = emptyList(),
        /**
         * The imsak's own margin in seconds ([world.taqwa.app.prayer.engine.method.ImsakFrom.PrintedFajr]: the
         * zone's Subuh as printed, less 10), fitted as an end; null where the zone prints no precaution (PLS01).
         */
        val imsakMarginSeconds: Int?,
        val ishaAngleDeg: Double = 18.0,
        /** The zone's own late limits, each event at most once; any other event keeps the entry's or class B's. */
        val late: List<Late> = emptyList(),
        /**
         * Places the zone's own list names that the app's city list does not hold (the review round of 13 Nov 2026):
         * their points are general geography, not a list of JAKIM's or the app's, so a place takes the zone near
         * them only with every neighbouring zone beside it (`Jakim.withNeighbours`), at the point itself too.
         */
        val places: List<GeoPoint> = emptyList(),
        /**
         * The zone's table is printed for a height (Cameron Highlands, Bukit Larut, the summits of Gunung Jerai and
         * Kinabalu, the Genting Sempah hills): its sun rises minutes before, and sets minutes after, the plain's
         * around it. Such a zone is taken, and rides beside its neighbours, only within [HILL_KM] of a place its
         * list names (a town of the app's list in it as any other town), and its margins never widen the edge.
         */
        val hill: Boolean = false,
        /**
         * Only one year's table of the zone is held (e-solat has not loaded its 2027 yet): its margins carry
         * [FORWARD_SECONDS] more, each to its safe side, until the second year is held and the zone refitted.
         */
        val oneYear: Boolean = false,
        /**
         * The end of eating's own margin in seconds, where the zone prints its Subuh before the dawn of its point (the
         * summit of Gunung Jerai, whose table carries the height at the dawn too); 0 elsewhere: the dawn of the place.
         */
        val endOfEatingMarginSeconds: Int = 0,
        /**
         * Days the zone's own table prints minutes after the days around them, held as printed ([PrintedLater]; ruling
         * R228 as the review round of 13 Nov 2026 left it): each start those days is never before the print, whatever
         * the run gives. The monitor's fetcher names the same days (`HELD_AS_PRINTED`), and raises any other such day
         * for a decision.
         */
        val printedLater: List<PrintedLater> = emptyList(),
    )

    /** Why a zone records a late limit of its own (`Jakim` words each). */
    enum class Why {
        /** One year's table prints Subuh earlier than the others at the same point (Kuala Lumpur's 2027). */
        YEAR_SHIFT,

        /** The printed sunrise spreads past a plain classic-noon model across the seasons (Perlis). */
        PRINT_SPREAD,

        /** The table shifts for stretches of days, so no one point fits it (Kedah's KDH02, KDH04 and KDH05). */
        STEPPED_TABLE,

        /**
         * e-solat prints one time for the whole zone and the app counts the place's own sun beside the zone's point:
         * at the zone's towns far from the point the print fits, an end comes before the print and a start after it.
         */
        TOWNS,

        /**
         * A hill zone stands inside a zone of the plain whose border is not held, so the plain's table rides beside
         * the hill's: a start the hill's table prints earlier than the plain's comes after the hill's print.
         */
        HEIGHT,

        /**
         * The zone holds no city of the app's list and its border is not held, so the neighbouring zones' tables ride
         * beside its own at the places its list names: a start a neighbour prints later comes after this zone's print.
         */
        BESIDE,

        /**
         * The table breaks its own run and is held as printed (the summit of Kinabalu's 2026: three days of March
         * printed minutes after the days around them, a Maghrib that steps back 8 min on 1 December): the margins
         * hold its latest days, so on the others a start comes after the print and an end before it.
         */
        BROKEN_RUN,

        /** Only one year's table is held, and the margin it carries for the next ([FORWARD_SECONDS]) passes a minute. */
        FORWARD,

        /**
         * The table prints a few days minutes after the days around them and they are held as printed
         * ([Zone.printedLater]): those days no start is before the print, and the sunrise, the end of eating and the
         * imsak, which are never moved later, come before the print by about as much.
         */
        PRINTED_LATER,

        /**
         * The zone beside this one prints a few days minutes after the days around them and is held to its print, never
         * before it ([PRINTED_LATER]); the border between the two is not held, so its table rides beside this zone's own,
         * and on those days a start here comes after this zone's own print by about as much (SWK09 beside SBH09).
         */
        NEIGHBOUR_PRINTED_LATER,

        /**
         * The real sun at the user's own point caps the sunrise shown (ruling R265, the second trust audit's G32): where
         * the zone's print, and the margin fitted to reproduce it, stand after the sun has risen at a place, the sunrise
         * shown is the minute the sun rose in there, before the print by up to the minutes recorded (Maran: e-solat's
         * PHG03 prints the sunrise up to 2 min 19 s after the real one at the zone's own point).
         */
        REAL_SUN,
    }

    /** [minutes] a zone may be late at [events], as the gate measured it at the zone's point and at its listed towns. */
    class Late(val minutes: Int, val why: Why, vararg val events: TimedEvent)

    private val STARTS = arrayOf(TimedEvent.FAJR, TimedEvent.ASR, TimedEvent.MAGHRIB, TimedEvent.ISHA)

    private val selangor = listOf(GeoPoint(2.62, 101.69), GeoPoint(3.73, 101.45))

    private fun m(fajr: Int, sunrise: Int, dhuhr: Int, asr: Int, maghrib: Int, isha: Int) =
        EventOffsets(fajr = fajr, sunrise = sunrise, dhuhr = dhuhr, asr = asr, maghrib = maghrib, isha = isha)

    /** The thirteen zones fitted before the zone round of 13 Nov 2026, each with a circle of its own. */
    val first: List<Zone> = listOf(
        Zone("WLY01", "Kuala Lumpur", GeoPoint(3.1412, 101.68653), 12.0, m(25, -44, 88, 39, 35, 38), imsakMarginSeconds = -125, startPoints = selangor,
            late = listOf(Late(3, Why.YEAR_SHIFT, TimedEvent.FAJR), Late(3, Why.YEAR_SHIFT, TimedEvent.IMSAK))),
        Zone("SGR01", "Shah Alam", GeoPoint(3.08507, 101.53281), 50.0, m(24, -111, 88, 26, 23, 26), imsakMarginSeconds = -6, startPoints = selangor,
            late = listOf(Late(5, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("JHR02", "Johor Bahru", GeoPoint(1.4655, 103.7578), 40.0, m(81, 19, 143, 105, 86, 91), imsakMarginSeconds = 15,
            late = listOf(Late(3, Why.TOWNS, TimedEvent.SUNRISE), Late(3, Why.TOWNS, TimedEvent.ASR))),
        Zone("KTN01", "Kota Bharu", GeoPoint(6.12361, 102.24333), 45.0, m(82, 46, 135, 81, 79, 77), imsakMarginSeconds = 44, ishaAngleDeg = 17.0,
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE))),
        Zone("SBH07", "Kota Kinabalu", GeoPoint(5.9749, 116.0724), 40.0, m(70, 2, 110, 62, 68, 68), imsakMarginSeconds = 2,
            late = listOf(Late(4, Why.TOWNS, TimedEvent.SUNRISE), Late(5, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("SWK08", "Kuching", GeoPoint(1.55, 110.33333), 45.0, m(169, 104, 208, 175, 168, 172), imsakMarginSeconds = 100,
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE), Late(3, Why.TOWNS, *STARTS, TimedEvent.DHUHR))),
        Zone("SBH01", "Sandakan", GeoPoint(5.8402, 118.1179), 50.0, m(123, 38, 148, 116, 118, 114), imsakMarginSeconds = 38),
        Zone(
            "PLS01", "Kangar", GeoPoint(6.4414, 100.1986), 30.0, m(30, -117, 106, 38, 30, 32), imsakMarginSeconds = null,
            late = listOf(Late(4, Why.PRINT_SPREAD, TimedEvent.SUNRISE)),
        ),
        Zone("PNG01", "George Town", GeoPoint(5.4141, 100.3288), 25.0, m(81, -93, 122, 78, 64, 63), imsakMarginSeconds = 26),
        Zone("WLY02", "Labuan", GeoPoint(5.2831, 115.2308), 20.0, m(33, -35, 93, 32, 32, 33), imsakMarginSeconds = 12),
        Zone("PHG06", "Tanah Rata", GeoPoint(4.4721, 101.3801), 20.0, m(9, 57, 86, 15, 30, 7), imsakMarginSeconds = -18, horizonDeg = -2.5,
            // Genting Highlands and Bukit Fraser, which the zone's list names beside Cameron Highlands.
            hill = true, places = listOf(GeoPoint(3.4236, 101.7932), GeoPoint(3.712, 101.737))),
        Zone("JHR01", "Pulau Aur", GeoPoint(2.45, 104.52), 10.0, m(67, 27, 138, 81, 69, 72), imsakMarginSeconds = 23),
        Zone("PHG01", "Pulau Tioman", GeoPoint(2.79, 104.17), 10.0, m(18, -7, 95, 25, 14, 16), imsakMarginSeconds = -11),
    )

    /** How far from one of its listed towns a place still takes a zone ([JakimZoneCities]; ruling R226). */
    const val CITY_KM = 10.0

    /** How far from a named place of a hill zone ([Zone.hill]) a place takes the hill's table, and how far it rides. */
    const val HILL_KM = 5.0

    /**
     * What a zone with one year's table carries until its second is held ([Zone.oneYear]; the review round of
     * 13 Nov 2026): across the thirty zones of the round with both years, a fit on 2026 alone, safety and all, fell
     * short of 2027's need by at most 12 s (KDH04's Isyak; 9 s at an imsak, 7 s at an Asar, in seven zone-events of
     * 210 by more than 0), so 15.
     */
    const val FORWARD_SECONDS = 15

    /**
     * The zones of the zone round of 13 Nov 2026 (ruling R225): each one's point is its first town in the app's list,
     * its [Zone.startPoints] the point its printed starts fit (see the header).
     */
    val towns: List<Zone> = listOf(
        Zone("JHR03", "Kluang", GeoPoint(2.03046, 103.31689), CITY_KM, m(10, -24, 88, 17, 8, 9), imsakMarginSeconds = -25,
            startPoints = listOf(GeoPoint(1.62, 103.195))),
        Zone("JHR04", "Muar", GeoPoint(2.0442, 102.5689), CITY_KM, m(9, -22, 87, 18, 8, 5), imsakMarginSeconds = -26,
            startPoints = listOf(GeoPoint(2.28, 102.522)),
            late = listOf(Late(4, Why.TOWNS, TimedEvent.SUNRISE))),
        Zone("KDH01", "Alor Setar", GeoPoint(6.12104, 100.36014), CITY_KM, m(18, -102, 79, 12, 13, 10), imsakMarginSeconds = -44,
            startPoints = listOf(GeoPoint(6.2, 100.127))),
        Zone("KDH02", "Kota Kuala Muda", GeoPoint(5.58822, 100.37085), CITY_KM, m(9, -155, -24, 25, 32, 19), imsakMarginSeconds = -140,
            startPoints = listOf(GeoPoint(6.22, 99.948)),
            late = listOf(Late(3, Why.STEPPED_TABLE, *STARTS, TimedEvent.IMSAK), Late(3, Why.STEPPED_TABLE, TimedEvent.SUNRISE))),
        Zone("KDH03", "Sik", GeoPoint(5.83333, 100.75), CITY_KM, m(18, -158, 79, 12, 13, 10), imsakMarginSeconds = -44,
            startPoints = listOf(GeoPoint(6.2, 100.377))),
        Zone("KDH04", "Kuala Ketil", GeoPoint(5.6, 100.65), CITY_KM, m(9, -195, -8, 64, 25, 23), imsakMarginSeconds = -148,
            startPoints = listOf(GeoPoint(6.0, 100.265)),
            late = listOf(Late(3, Why.STEPPED_TABLE, *STARTS, TimedEvent.IMSAK), Late(4, Why.STEPPED_TABLE, TimedEvent.SUNRISE))),
        Zone("KDH05", "Kulim", GeoPoint(5.36499, 100.56177), CITY_KM, m(9, -146, -4, 67, 32, 30), imsakMarginSeconds = -184,
            startPoints = listOf(GeoPoint(6.02, 100.034)),
            late = listOf(Late(4, Why.STEPPED_TABLE, *STARTS, TimedEvent.IMSAK))),
        Zone("KDH06", "Kuah", GeoPoint(6.32649, 99.8432), CITY_KM, m(33, -46, 43, 34, 12, 9), imsakMarginSeconds = -78,
            startPoints = listOf(GeoPoint(6.3, 99.479))),
        Zone("KTN02", "Gua Musang", GeoPoint(4.88441, 101.96857), CITY_KM, m(10, 100, 74, 17, 10, 11), imsakMarginSeconds = -6,
            startPoints = listOf(GeoPoint(4.96, 101.509)), ishaAngleDeg = 17.0,
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE))),
        Zone("MLK01", "Malacca", GeoPoint(2.196, 102.2405), CITY_KM, m(14, 37, 77, 13, 9, 13), imsakMarginSeconds = -8,
            startPoints = listOf(GeoPoint(2.38, 101.984)),
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE))),
        Zone("NGS01", "Rompin", GeoPoint(2.7083, 102.5047), CITY_KM, m(9, -67, 50, 22, 16, 15), imsakMarginSeconds = -27,
            startPoints = listOf(GeoPoint(2.74, 102.112))),
        Zone("NGS02", "Rembau", GeoPoint(2.5897, 102.0913), CITY_KM, m(11, -111, 44, 6, 14, 16), imsakMarginSeconds = -37,
            startPoints = listOf(GeoPoint(3.0, 101.804))),
        Zone("NGS03", "Seremban", GeoPoint(2.7297, 101.9381), CITY_KM, m(9, -65, 61, 29, 13, 16), imsakMarginSeconds = -24,
            startPoints = listOf(GeoPoint(2.74, 101.66))),
        Zone("PHG02", "Kuantan", GeoPoint(3.8077, 103.326), CITY_KM, m(13, 43, 89, 19, 10, 12), imsakMarginSeconds = -15,
            startPoints = listOf(GeoPoint(3.08, 102.781)),
            late = listOf(Late(4, Why.REAL_SUN, TimedEvent.SUNRISE), Late(5, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("PHG03", "Maran", GeoPoint(3.586, 102.773), CITY_KM, m(12, 133, 88, 21, 9, 10), imsakMarginSeconds = -14,
            startPoints = listOf(GeoPoint(3.46, 102.113)),
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE))),
        Zone("PHG04", "Bentong Town", GeoPoint(3.52229, 101.90866), CITY_KM, m(14, -14, 89, 23, 10, 12), imsakMarginSeconds = -21,
            startPoints = listOf(GeoPoint(3.96, 101.732))),
        Zone("PRK01", "Tanjong Malim", GeoPoint(3.681, 101.5198), CITY_KM, m(10, -64, 63, 27, 10, 13), imsakMarginSeconds = -23,
            startPoints = listOf(GeoPoint(4.24, 101.034))),
        Zone("PRK02", "Ipoh", GeoPoint(4.5841, 101.0829), CITY_KM, m(10, -127, 65, 32, 13, 11), imsakMarginSeconds = -28,
            startPoints = listOf(GeoPoint(4.58, 100.681))),
        Zone("PRK03", "Gerik", GeoPoint(5.41667, 101.13333), CITY_KM, m(9, -132, 36, 14, 13, 15), imsakMarginSeconds = -37,
            startPoints = listOf(GeoPoint(5.32, 100.731))),
        Zone("PRK05", "Teluk Intan", GeoPoint(4.02219, 101.02083), CITY_KM, m(10, -110, 62, 29, 12, 13), imsakMarginSeconds = -26,
            startPoints = listOf(GeoPoint(4.3, 100.491))),
        Zone("PRK06", "Taiping", GeoPoint(4.85, 100.73333), CITY_KM, m(10, -101, 63, 30, 11, 11), imsakMarginSeconds = -28,
            startPoints = listOf(GeoPoint(5.14, 100.341))),
        Zone("SBH03", "Lahad Datu", GeoPoint(5.02298, 118.32897), CITY_KM, m(10, -1, 72, 13, 10, 11), imsakMarginSeconds = -8, oneYear = true,
            startPoints = listOf(GeoPoint(5.02, 118.307)),
            late = listOf(Late(3, Why.TOWNS, TimedEvent.SUNRISE))),
        Zone("SBH04", "Tawau", GeoPoint(4.24482, 117.89115), CITY_KM, m(9, 69, 71, 9, 10, 10), imsakMarginSeconds = -8, oneYear = true,
            startPoints = listOf(GeoPoint(4.44, 117.502))),
        Zone("SBH05", "Kudat", GeoPoint(6.88732, 116.82364), CITY_KM, m(7, -12, 73, 6, 8, 10), imsakMarginSeconds = -15, oneYear = true,
            startPoints = listOf(GeoPoint(6.84, 116.822))),
        Zone("SBH08", "Keningau", GeoPoint(5.3378, 116.1602), CITY_KM, m(8, -10, 70, 6, 9, 8), imsakMarginSeconds = -13, oneYear = true,
            startPoints = listOf(GeoPoint(5.28, 116.149)),
            late = listOf(Late(3, Why.FORWARD, TimedEvent.SUNRISE))),
        Zone("SBH09", "Sipitang", GeoPoint(5.07903, 115.54964), CITY_KM, m(12, -9, 68, 10, 8, 12), imsakMarginSeconds = -31, oneYear = true,
            startPoints = listOf(GeoPoint(5.04, 115.54)),
            // e-solat's 2026 table prints 28 to 31 March after the days either side: Subuh and Syuruk 10 to 11 min,
            // Zohor 6 to 7, Asar 19 to 20, Maghrib 3, Isyak in line. Held to the print, never before it, and never left out.
            printedLater = listOf(PrintedLater(LocalDate(2026, 3, 28), LocalDate(2026, 3, 31), m(660, 0, 420, 1200, 180, 0))),
            late = listOf(Late(13, Why.PRINTED_LATER, TimedEvent.SUNRISE, TimedEvent.END_OF_EATING, TimedEvent.IMSAK))),
        Zone("SGR02", "Ijok", GeoPoint(3.31667, 101.4), CITY_KM, m(12, -38, 71, 13, 9, 10), imsakMarginSeconds = -8, oneYear = true,
            startPoints = listOf(GeoPoint(3.8, 100.807)),
            late = listOf(Late(5, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("SGR03", "Kapar", GeoPoint(3.13333, 101.38333), CITY_KM, m(10, -104, 70, 12, 10, 11), imsakMarginSeconds = -9, oneYear = true,
            startPoints = listOf(GeoPoint(3.06, 101.245), GeoPoint(3.66, 101.365))),
        Zone("SWK01", "Limbang", GeoPoint(4.75477, 115.01011), CITY_KM, m(11, 35, 74, 14, 10, 10), imsakMarginSeconds = -6,
            startPoints = listOf(GeoPoint(4.78, 114.845)),
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE))),
        Zone("SWK02", "Miri", GeoPoint(4.39928, 113.99163), CITY_KM, m(10, 5, 76, 15, 11, 11), imsakMarginSeconds = -5,
            startPoints = listOf(GeoPoint(3.88, 113.73)),
            late = listOf(Late(3, Why.TOWNS, TimedEvent.SUNRISE))),
        Zone("SWK03", "Bintulu", GeoPoint(3.16667, 113.03333), CITY_KM, m(10, 6, 74, 17, 10, 9), imsakMarginSeconds = -5,
            startPoints = listOf(GeoPoint(2.88, 112.879)),
            late = listOf(Late(5, Why.REAL_SUN, TimedEvent.SUNRISE), Late(5, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("SWK04", "Sibu", GeoPoint(2.3, 111.81667), CITY_KM, m(9, -32, 75, 16, 9, 10), imsakMarginSeconds = -38,
            startPoints = listOf(GeoPoint(2.84, 111.711)),
            late = listOf(Late(7, Why.TOWNS, TimedEvent.SUNRISE), Late(8, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("SWK05", "Sarikei", GeoPoint(2.11667, 111.51667), CITY_KM, m(11, 56, 76, 15, 9, 11), imsakMarginSeconds = -5,
            startPoints = listOf(GeoPoint(2.22, 111.229)),
            late = listOf(Late(4, Why.REAL_SUN, TimedEvent.SUNRISE), Late(5, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("SWK06", "Sri Aman", GeoPoint(1.24722, 111.45278), CITY_KM, m(9, 21, 74, 19, 11, 11), imsakMarginSeconds = -4,
            startPoints = listOf(GeoPoint(1.78, 111.129)),
            late = listOf(Late(4, Why.REAL_SUN, TimedEvent.SUNRISE), Late(5, Why.TOWNS, TimedEvent.END_OF_EATING))),
        Zone("SWK07", "Serian", GeoPoint(1.16667, 110.56667), CITY_KM, m(11, -15, 75, 15, 9, 9), imsakMarginSeconds = -19,
            startPoints = listOf(GeoPoint(1.46, 110.494))),
        Zone("TRG01", "Kuala Terengganu", GeoPoint(5.3302, 103.1408), CITY_KM, m(11, 27, 74, 12, 10, 12), imsakMarginSeconds = -10,
            startPoints = listOf(GeoPoint(5.24, 102.969))),
        Zone("TRG04", "Cukai", GeoPoint(4.25, 103.41667), CITY_KM, m(12, 99, 73, 11, 10, 12), imsakMarginSeconds = -7,
            startPoints = listOf(GeoPoint(4.5, 102.874)),
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE))),
    )

    /**
     * The ten zones that hold no city of the app's list (the review round of 13 Nov 2026), each on its own e-solat
     * 2026 table (ten plain GETs of 13 Nov 2026; no 2027 yet, so each is [Zone.oneYear]): a place in one of them was
     * on a neighbouring zone's table or at the edge, and before the print where its own zone prints later (Bukit
     * Larut's Maghrib 4 min after Taiping's; Besut's Isyak at 18 degrees, Kelantan's at 17). The list names each by
     * places and districts, and their points here are general geography ([Zone.places]), which is why every
     * neighbouring zone rides beside them. SWK09's list names one village (Kampung Patarikan); its point is the one
     * its own table fits, by the Lawas-Sipitang border.
     */
    val named: List<Zone> = listOf(
        // Puncak Gunung Jerai.
        Zone("KDH07", "Gunung Jerai", GeoPoint(5.787, 100.435), HILL_KM, m(-219, -321, 76, 188, 303, 301), imsakMarginSeconds = -284, endOfEatingMarginSeconds = -242, hill = true, oneYear = true,
            late = listOf(Late(8, Why.HEIGHT, TimedEvent.FAJR), Late(4, Why.HEIGHT, TimedEvent.ASR))),
        // Genting Sempah, Janda Baik, Bukit Tinggi.
        Zone("PHG05", "Bukit Tinggi", GeoPoint(3.35, 101.82), HILL_KM, m(10, -254, 85, 23, 244, 7), imsakMarginSeconds = -15, hill = true, oneYear = true,
            places = listOf(GeoPoint(3.353, 101.77), GeoPoint(3.33, 101.86)),
            late = listOf(Late(3, Why.HEIGHT, TimedEvent.FAJR, TimedEvent.DHUHR, TimedEvent.ISHA), Late(4, Why.HEIGHT, TimedEvent.ASR))),
        // Mukim Rompin, Endau and Pontian of Rompin district: Kuala Rompin, Tanjung Gemok.
        Zone("PHG07", "Kuala Rompin", GeoPoint(2.805, 103.489), CITY_KM, m(29, 47, 103, 33, 23, 27), imsakMarginSeconds = 2, oneYear = true,
            startPoints = listOf(GeoPoint(2.88, 103.29)), places = listOf(GeoPoint(2.667, 103.617))),
        // Temengor and Belum: Pulau Banding, the Temengor dam.
        Zone("PRK04", "Pulau Banding", GeoPoint(5.5506, 101.3489), CITY_KM, m(64, -122, 100, 72, 65, 58), imsakMarginSeconds = 21, oneYear = true,
            startPoints = listOf(GeoPoint(5.59, 101.334)), places = listOf(GeoPoint(5.4069, 101.3003))),
        // Bukit Larut.
        Zone("PRK07", "Bukit Larut", GeoPoint(4.862, 100.793), HILL_KM, m(13, -337, 68, 35, 332, 8), imsakMarginSeconds = -14, hill = true, oneYear = true,
            late = listOf(Late(3, Why.HEIGHT, TimedEvent.FAJR, TimedEvent.ISHA), Late(4, Why.HEIGHT, TimedEvent.ASR))),
        // Beluran, Telupid, Pinangah, Terusan, Kuamut, the west of Sandakan division: Beluran, Telupid.
        Zone("SBH02", "Beluran", GeoPoint(5.895, 117.555), CITY_KM, m(14, 108, 77, 16, 14, 17), imsakMarginSeconds = -3, oneYear = true,
            startPoints = listOf(GeoPoint(5.2, 116.842)), places = listOf(GeoPoint(5.628, 117.126)),
            late = listOf(Late(5, Why.REAL_SUN, TimedEvent.SUNRISE), Late(6, Why.TOWNS, TimedEvent.END_OF_EATING))),
        // Gunung Kinabalu.
        Zone("SBH06", "Gunung Kinabalu", GeoPoint(6.075, 116.558), HILL_KM, m(216, -491, 213, 350, 501, 20), imsakMarginSeconds = -3, hill = true, oneYear = true,
            late = listOf(
                Late(4, Why.BROKEN_RUN, TimedEvent.FAJR, TimedEvent.ISHA), Late(3, Why.BROKEN_RUN, TimedEvent.DHUHR),
                Late(7, Why.BROKEN_RUN, TimedEvent.ASR), Late(9, Why.BROKEN_RUN, TimedEvent.MAGHRIB),
                Late(5, Why.BROKEN_RUN, TimedEvent.SUNRISE, TimedEvent.END_OF_EATING), Late(4, Why.BROKEN_RUN, TimedEvent.IMSAK),
            )),
        // Zon Khas (Kampung Patarikan).
        Zone("SWK09", "Kampung Patarikan", GeoPoint(4.96, 115.505), CITY_KM, m(10, -2, 75, 18, 11, 11), imsakMarginSeconds = -3, oneYear = true,
            // SBH09, across the border, rides beside it, with its four days of March 2026 held to its print, never before it.
            late = listOf(
                Late(11, Why.NEIGHBOUR_PRINTED_LATER, TimedEvent.FAJR), Late(8, Why.NEIGHBOUR_PRINTED_LATER, TimedEvent.DHUHR),
                Late(20, Why.NEIGHBOUR_PRINTED_LATER, TimedEvent.ASR), Late(4, Why.BESIDE, TimedEvent.MAGHRIB, TimedEvent.ISHA),
            )),
        // Besut and Setiu: Jerteh, Kuala Besut, Bandar Permaisuri.
        Zone("TRG02", "Jerteh", GeoPoint(5.7336, 102.4897), CITY_KM, m(17, -17, 77, 17, 14, 16), imsakMarginSeconds = -17, oneYear = true,
            startPoints = listOf(GeoPoint(5.42, 102.447)), places = listOf(GeoPoint(5.83, 102.557), GeoPoint(5.52, 102.75)),
            late = listOf(Late(4, Why.BESIDE, TimedEvent.FAJR, TimedEvent.ASR, TimedEvent.MAGHRIB))),
        // Hulu Terengganu: Kuala Berang.
        Zone("TRG03", "Kuala Berang", GeoPoint(5.0725, 103.0117), CITY_KM, m(16, 103, 76, 15, 14, 15), imsakMarginSeconds = -3, oneYear = true,
            startPoints = listOf(GeoPoint(5.01, 102.56)),
            late = listOf(Late(3, Why.REAL_SUN, TimedEvent.SUNRISE))),
    )

    val zones: List<Zone> = first + towns + named
}
