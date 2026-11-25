package world.taqwa.app.prayer.engine.registry.authorities

import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.Alongside
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.ImsakFrom
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.atEdge
import world.taqwa.app.prayer.engine.registry.data.JakimZoneCities
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.single
import world.taqwa.app.prayer.engine.registry.widened
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Malaysia: the state muftis' takwim, published through JAKIM e-solat (research Malaysia section,
 * HIGH; profiles-tested.md data-wide-band): Subuh 18° since the MKI decision of 20–21 Nov 2019,
 * Isyak 18°, Shafi'i Asr, the classic method (events at the noon declination). Each zone is
 * computed at its latest reference point and rounded up, syuruk at its eastern point rounded down,
 * so there is no national profile: each fitted zone is a unit with its own margins (Task 7c:
 * `Fitter.fit("my.jakim/<zone>")` against the zone's own e-solat 2026 table and its 2025 mirror,
 * safety 5 s, margins fitted over the union of both years — see `JakimZones.kt` and
 * `docs/research/2026-09-prayer-times/proof/7c-sea.md`). Imsak Subuh − 10 (none in Perlis), as e-solat prints it:
 * the zone's Subuh as printed, for its latest point and rounded up, less 10 ([ImsakFrom.PrintedFajr], each zone's own
 * margin in `JakimZones.kt`, the JAKIM imsak round of 12 Nov 2026), not the dawn the end of eating hangs off, the
 * reference point's own, which put it up to 4 min before e-solat's (3–4 at Kuching on every day of Ramadan 1448).
 * Beyond the fitted zones (the edges) it is counted back from that dawn ([atEdge]).
 *
 * All sixty zones of e-solat's list are units (rulings R225–R229): the first thirteen, since the zone round of
 * 13 Nov 2026 every other zone that holds a city of the app's list, and since its review round the ten that hold
 * none, each on its own e-solat tables. JAKIM's zones are districts, not circles, and their borders are not held,
 * so a place takes the zone of the nearest town the zone's own list holds ([JakimZoneCities], within
 * [JakimZones.CITY_KM]) or of the nearest place the list names, with every zone that could own the place beside it
 * ([withNeighbours]): the day shown is the safe side of them all, so a place on a border is never before its own
 * zone's print, and may be minutes after it. `my-jakim-cities.tsv` reads every listed town at its own point, and
 * `my-jakim-envelope.tsv` the border places, the hills and the unattached cities against every table beside them.
 *
 * Outside the fitted zones and away from every listed town (class D there) the zone's reference point is unknown,
 * and computing at the user's point is 2–8 min early in the east of wide zones. The
 * safe edge takes, for each prayer, the largest margin of any fitted zone (Kuching's reach about
 * 3 min), and in Borneo, where zones span up to 15 min, 5 min more. That is later than the brief's
 * +90 s on purpose: +90 s is already early at Kuching's own point. Verified in an earlier task at
 * Kapit, Sibu, Mukah, Gua Musang, Jeli, Kuala Krai and Dabong, which are zone towns now and read against their own
 * zones' tables. No zone is left at the edge; what is still there is the land away from every listed town and
 * named place, in whichever zone it lies, and it is kept as is: the edge's margins are the largest of every zone of
 * the plain, the ten new ones included.
 */
object Jakim {
    val method = TimetableMethod(
        id = "my.jakim",
        fajrAngle = 18.0,
        isha = IshaRule.Angle(18.0),
        sunModel = SunModel.CLASSIC_NOON,
        imsakMinutesBeforeFajr = IMSAK_MINUTES,
    )

    /**
     * The zones some of whose checked tables are the mirror's, and the years those are (ruling R276; my-jakim.tsv and
     * my-jakim-cities.tsv, held by a test): every zone's 2025, and 2027 at nine of them. Their other checked year, 2026,
     * is e-solat's own, so the Prayer screen's card names the copy on a mirrored year's day and e-solat's own pages on
     * the others (ruling R278). Declared before [zones], which reads it as the object is built.
     */
    val mirroredYears: Map<String, Set<Int>> = mapOf(
        "JHR01" to setOf(2025, 2027), "JHR02" to setOf(2025, 2027), "KTN01" to setOf(2025, 2027), "PHG01" to setOf(2025, 2027),
        "PHG06" to setOf(2025, 2027), "PLS01" to setOf(2025, 2027), "PNG01" to setOf(2025, 2027), "SBH01" to setOf(2025),
        "SBH07" to setOf(2025), "SGR01" to setOf(2025), "SWK08" to setOf(2025, 2027), "WLY01" to setOf(2025, 2027),
        "WLY02" to setOf(2025),
    )

    /** The zones some of whose checked tables are the mirror's ([mirroredYears]). */
    val partlyMirrored: Set<String> = mirroredYears.keys

    private val zones: List<AuthorityUnit> = JakimZones.zones.map { z ->
        // One year's table held: every margin further to its safe side until the second is (JakimZones.FORWARD_SECONDS).
        val forward = if (z.oneYear) JakimZones.FORWARD_SECONDS else 0
        AuthorityUnit(
            id = z.id,
            name = z.id,
            // A zone code is no place's name ("used in SBH01"): About and the site call the place by the app's own name
            // for it in the reader's language (the second audit's G12, as Egypt's F34).
            named = false,
            partlyRepublished = z.id in partlyMirrored,
            republishedYears = mirroredYears[z.id].orEmpty(),
            point = z.point,
            radiusKm = z.radiusKm,
            method = method.copy(
                id = "my.jakim.${z.id.lowercase()}",
                isha = IshaRule.Angle(z.ishaAngleDeg),
                margins = z.margins.widened(forward),
                horizonDeg = z.horizonDeg,
                startPoints = z.startPoints,
                imsakMinutesBeforeFajr = z.imsakMarginSeconds?.let { IMSAK_MINUTES },
                imsakFrom = z.imsakMarginSeconds?.let { ImsakFrom.PrintedFajr(it - forward) } ?: ImsakFrom.Dawn,
                endOfEatingMarginSeconds = z.endOfEatingMarginSeconds - if (z.endOfEatingMarginSeconds != 0) forward else 0,
                printedLater = z.printedLater,
            ),
            lateLimits = z.late.map { LateLimit(it.minutes, reason(it), it.events.toSet()) },
        )
    }

    /**
     * The words of a zone's own late limit ([JakimZones.Why]; ruling R227 for the stepped tables and the towns). An
     * end here is the sunrise, the end of eating or the imsak, and for an end "late" means BEFORE the print: the app
     * never shows one after it. The sunrise is the zone's fitted one, not always the place's own sun: most zones'
     * sunrise margin is negative, and in a few it is positive (KTN02, PHG03, TRG04 and Kuching, where the print's
     * sunrise stands after the point's own), still never after the print on any day held. The imsak hangs off the
     * printed Subuh less 10, so where a start is held later for a stepped table the imsak is fitted earlier.
     */
    private fun reason(late: JakimZones.Late): String {
        val ends = late.events.all { it == TimedEvent.SUNRISE || it == TimedEvent.END_OF_EATING || it == TimedEvent.IMSAK }
        return when (late.why) {
            JakimZones.Why.YEAR_SHIFT -> if (late.events.all { it == TimedEvent.IMSAK }) WLY01_IMSAK_REASON else WLY01_FAJR_REASON
            JakimZones.Why.PRINT_SPREAD -> PLS01_SUNRISE_REASON
            JakimZones.Why.STEPPED_TABLE ->
                "e-solat's table for this Kedah zone shifts for stretches of days (Asar by about 4 min, the others by a " +
                    "minute or two), so no one point fits it: the margins hold its latest stretch, and on its other days " +
                    (if (ends) "the sunrise comes up to ${late.minutes} min before the print" else "a start comes up to ${late.minutes} min after the print and the imsak up to ${late.minutes} min before it")
            JakimZones.Why.HEIGHT ->
                "e-solat prints this zone for a height, and the border between it and the zone of the plain around it is " +
                    "not held, so the plain's table rides beside the hill's: " +
                    (if (ends) "an end comes up to ${late.minutes} min before the hill's print" else "a start the hill's table prints before the plain's comes up to ${late.minutes} min after the hill's print")
            JakimZones.Why.BESIDE ->
                "this zone holds no city of the app's list and its border with the zones around it is not held, so their " +
                    "tables ride beside its own at the places its list names: a start a neighbouring zone prints later " +
                    "comes up to ${late.minutes} min after this zone's print"
            JakimZones.Why.BROKEN_RUN ->
                "e-solat's table for this summit breaks its own run (days printed minutes after the days around them, a " +
                    "Maghrib that steps back 8 min from one day to the next) and is held as printed, with the zone around " +
                    "the summit beside it: the margins hold its latest days, and on the others " +
                    (if (ends) "an end comes up to ${late.minutes} min before the print" else "a start comes up to ${late.minutes} min after the print")
            JakimZones.Why.FORWARD ->
                "only one year's table of this zone is held, so its margins carry ${JakimZones.FORWARD_SECONDS} s more to " +
                    "the safe side until the next year's is: at one of its towns the sunrise comes up to ${late.minutes} min before the print"
            JakimZones.Why.PRINTED_LATER ->
                "e-solat's table for this zone prints a few days minutes after the days around them (28 to 31 March " +
                    "2026), and they are held to the print: on those days no start is before it, and the sunrise, " +
                    "the end of eating and the imsak, which are never moved later, come up to ${late.minutes} min before the print"
            JakimZones.Why.NEIGHBOUR_PRINTED_LATER ->
                "the zone beside this one (SBH09) prints 28 to 31 March 2026 minutes after the days around them and is " +
                    "held to its print, never before it; the border between the two is not held, so its table rides beside " +
                    "this zone's own: on those four days a start came up to ${late.minutes} min after this zone's print, on any other day at most 4"
            JakimZones.Why.REAL_SUN ->
                "the sunrise shown is never after the real sunrise at the place itself (ruling R265), and e-solat's print " +
                    "for this zone, one time for all of it, can stand after the sun has risen, at the zone's point or at " +
                    "its towns far from the point: the sunrise comes up to ${late.minutes} min before the print"
            JakimZones.Why.TOWNS ->
                "e-solat prints one time for the whole zone, and the app counts the place's own sun beside the zone's " +
                    "point: at the zone's towns far from the point its print fits, " +
                    (if (ends) "the sun rises and the dawn breaks up to ${late.minutes} min before the print" else "a start comes up to ${late.minutes} min after the print")
        }
    }

    private val hills: Set<String> = JakimZones.zones.filter { it.hill }.map { it.id }.toSet()

    /**
     * The largest margin any fitted zone of the plain needs, prayer by prayer (sunrise: the most negative). A hill
     * zone's ([JakimZones.Zone.hill]) are for its height alone and stay out: its named places are taken by the zone
     * itself, never by the edge.
     */
    private val widest: EventOffsets = zones.filter { it.id !in hills }.mapNotNull { it.method }
        .map { it.margins }
        .reduce { a, b ->
            EventOffsets(
                fajr = maxOf(a.fajr, b.fajr), sunrise = minOf(a.sunrise, b.sunrise), dhuhr = maxOf(a.dhuhr, b.dhuhr),
                asr = maxOf(a.asr, b.asr), maghrib = maxOf(a.maghrib, b.maghrib), isha = maxOf(a.isha, b.isha),
            )
        }

    /** Peninsular Malaysia beyond the fitted zones. */
    val peninsularEdge = method.atEdge("my.jakim.edge", widest)

    /** Sabah, Sarawak and Labuan beyond the fitted zones, where zones span up to 15 minutes. */
    val borneoEdge = method.atEdge("my.jakim.edge.borneo", widest.widened(BORNEO_EXTRA_SECONDS))

    /**
     * A point a zone is known by, and its zone. [listed]: a town of the app's list the zone's own list of districts
     * holds ([JakimZoneCities]), or the point a zone of the first thirteen and the 37 was fitted at; else a place the zone's list
     * names whose point is general geography ([JakimZones.Zone.places], and the point of a zone that holds no city of
     * the app's list). [peak]: such a place of a hill zone, which reaches [JakimZones.HILL_KM] and no further.
     */
    private class Anchor(val point: GeoPoint, val unit: AuthorityUnit, val listed: Boolean, val peak: Boolean) {
        val takenWithinKm get() = if (peak) JakimZones.HILL_KM else JakimZones.CITY_KM
    }

    private val anchors: List<Anchor> = JakimZones.zones.flatMap { z ->
        val unit = zones.first { it.id == z.id }
        val towns = JakimZoneCities.byZone[z.id].orEmpty().map { GeoPoint(it.lat, it.lon) }
        val fitted = z !in JakimZones.named
        val listed = (towns + listOfNotNull(z.point.takeIf { fitted })).distinct()
        val named = (z.places + listOfNotNull(z.point.takeIf { !fitted })).distinct()
        listed.map { Anchor(it, unit, listed = true, peak = false) } + named.map { Anchor(it, unit, listed = false, peak = z.hill) }
    }

    /** The anchors that can lie within [km] of [p], by their degrees alone, before any distance is worked out. */
    private fun near(p: GeoPoint, km: Double): List<Anchor> {
        val band = km / KM_PER_DEGREE_AT_LEAST
        return anchors.filter { abs(it.point.lat - p.lat) <= band && abs(it.point.lon - p.lon) <= band }
    }

    /**
     * The zone of the anchor nearest [place], within [JakimZones.CITY_KM] of it (ruling R226; a hill zone's named
     * place within [JakimZones.HILL_KM]): JAKIM's zones are districts, not circles, and the towns of two zones lie
     * within a kilometre of each other (Bandar Baharu in KDH05, Parit Buntar in PRK06), so a place takes its zone from
     * the nearest town a zone's list holds; beyond every town, the circle of one of the thirteen first zones
     * ([UnitSet.nearest]), else the edge. The choice is never the proof that a place is not early: [beside] is.
     */
    private fun zoneOfNearestTown(place: Place): AuthorityUnit? {
        val here = GeoPoint(place.lat, place.lon)
        return near(here, JakimZones.CITY_KM).map { it to distanceKm(here, it.point) }
            .filter { it.second <= it.first.takenWithinKm }.minByOrNull { it.second }?.first?.unit
    }

    /**
     * The zones that could hold [user] beside [own] (the user's unit; null at the edge), each of which rides beside
     * the user's day as its own day at its own point ([Alongside]: each start the later, each end the earlier of the
     * zones'). The district borders are not held, so (ruling R226 as the review round of 13 Nov 2026 left it):
     * - every zone of the plain with an anchor within [BESIDE_FLOOR_KM] of the user, however near the user's own
     *   town is: two zones' towns face each other across a state border (Tampin and Pulau Sebang, Tanjong Malim and
     *   Hulu Bernam, Selama on both banks of the Krian), and the nearest town of the place's true zone was up to 21 km
     *   off where the nearer town's zone printed earlier on 39 to 579 days of 730;
     * - and every zone with an anchor less than [BESIDE_FACTOR] times as far as the nearest anchor of the user's own
     *   zone (at the edge, the nearest of any), at most [BESIDE_MAX_KM];
     * - a hill zone only by the second rule from its towns, and within [JakimZones.HILL_KM] of a named place of its:
     *   its table is minutes later at Maghrib than the plain's, which a plain town 20 km off must not be shown.
     */
    private fun beside(user: GeoPoint, own: AuthorityUnit?): List<AuthorityUnit> {
        val around = near(user, BESIDE_MAX_KM).map { it to distanceKm(user, it.point) }.filter { it.second <= BESIDE_MAX_KM }
        val ownKm = if (own != null) {
            anchors.filter { it.unit.id == own.id }.minOf { distanceKm(user, it.point) }
        } else {
            around.filter { !it.first.peak }.minOfOrNull { it.second } ?: return emptyList()
        }
        val byRatio = min(BESIDE_FACTOR * ownKm, BESIDE_MAX_KM)
        return around.filter { (anchor, km) ->
            anchor.unit.id != own?.id && when {
                anchor.peak -> km <= JakimZones.HILL_KM
                anchor.unit.id in hills -> km <= byRatio
                else -> km <= max(BESIDE_FLOOR_KM, byRatio)
            }
        }.map { it.first.unit }.distinct()
    }

    private fun TimetableMethod.with(beside: List<AuthorityUnit>): TimetableMethod =
        if (beside.isEmpty()) this else copy(alongside = alongside + beside.map { Alongside(it.point, it.method ?: method) })

    /**
     * The neighbouring zones beside a user in [unit] ([beside]). At a town of the app's list itself nothing rides: its
     * zone is on the list. At a named place whose point is general geography the neighbours ride at the point too.
     */
    private fun withNeighbours(user: GeoPoint, unit: AuthorityUnit, placed: TimetableMethod): TimetableMethod {
        if (anchors.any { it.listed && it.unit.id == unit.id && it.point.lat == user.lat && it.point.lon == user.lon }) return placed
        return placed.with(beside(user, unit))
    }

    /**
     * Beyond every town and circle (class D): the safe edge, and beside it every zone that could hold the place
     * ([beside]; the review round of 13 Nov 2026: Mahang, in KDH05 by the list and 21 km from Kulim, was before
     * KDH05's Asar on 51 days of 730 on the edge alone).
     */
    val units = UnitSet("my.jakim", zones, choose = ::zoneOfNearestTown, around = ::withNeighbours) { p: GeoPoint ->
        (if (p.lon >= BORNEO_WEST_LON) borneoEdge else peninsularEdge).with(beside(p, null))
    }

    /**
     * The end of eating against e-solat's Subuh (end-of-eating audit, 3 Oct 2026; my-jakim.tsv now
     * checks it, F+E: the fast begins at Subuh, imsak is its precaution): never after it on any of
     * the 9,489 zone-days held, but the Subuh printed is each zone's latest reference point rounded
     * up, and the end the dawn at the place itself rounded down, so it comes up to 4 min before it.
     * The fitter would move it later (+78 to +93 s at Johor and Kuala Lumpur); an end never moves
     * later to close a gap, so the minutes are recorded instead.
     */
    private val endOfEatingLimit = LateLimit(
        4,
        "e-solat prints each zone's Subuh for its latest point, rounded up, and the fast here begins at the dawn of the " +
            "place itself: up to 4 min before the printed Subuh (end-of-eating audit, 3 Oct 2026)",
        setOf(TimedEvent.END_OF_EATING),
    )

    /**
     * Ruling R276 (the second audit's G7): the 2025 tables of 13 zones and the 2027 tables of nine of them were read
     * from api.waktusolat.app's public mirror of e-solat, not from e-solat itself (every other table, 2026 and the zone
     * round's 2027, is e-solat's own). So the entry and those zones say "partly in waktusolat.app's copy" wherever they
     * name the comparison, until e-solat's own tables replace the mirror's in the gate.
     */
    val entry: RegistryEntry = single(
        id = "my.jakim", nameKey = "authority_jakim", entryClass = EntryClass.B, method = method,
        school = AsrSchool.STANDARD, countries = setOf("MY"), lateLimits = listOf(endOfEatingLimit),
        partlyRepublisher = MIRROR,
    )

    /** Whose copy of e-solat the mirrored tables are, as the app and the site name it. */
    private const val MIRROR = "waktusolat.app"

    val entries = listOf(entry)

    /** e-solat's imsak: its printed Subuh less these minutes, in every zone but Perlis. */
    private const val IMSAK_MINUTES = 10

    private const val BORNEO_WEST_LON = 109.5
    private const val BORNEO_EXTRA_SECONDS = 300

    private const val BESIDE_FACTOR = 3.0
    private const val BESIDE_MAX_KM = 50.0
    private const val BESIDE_FLOOR_KM = 25.0

    /** Less than a degree spans anywhere in Malaysia, of latitude or of longitude (110.6 km and, at 7.4° N, 110.3). */
    private const val KM_PER_DEGREE_AT_LEAST = 108.0

    private const val WLY01_FAJR_REASON =
        "Kuala Lumpur's 2027 Subuh prints up to 3 min before its 2025 and 2026 Subuh at the same point; an earlier Fajr would be early in those years"

    /** Ruling R187 (the owner, 12 Nov 2026): the same cause as the Fajr's, seen from the imsak, an end. */
    private const val WLY01_IMSAK_REASON =
        "Kuala Lumpur's 2027 Subuh, and its imsak with it, prints up to 3 min before 2025's and 2026's at the same point; " +
            "the imsak is never after any year's, so it is up to 3 min before the earlier years'"

    private const val PLS01_SUNRISE_REASON =
        "Kangar's printed sunrise spreads a further minute beyond a plain classic-noon model across the seasons, and at Arau, " +
            "east of Kangar, the sun rises before it does there: up to 4 min before the print"
}
