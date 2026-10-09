package world.taqwa.timetables.units

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.time.Instant

/**
 * How far a QMDB place's unit reaches (the owner's decision of 9 Oct 2026: every place of QMDB's own list a unit, a
 * user taking the nearest within its reach). A unit reaches as far as the time a user is shown stays within
 * [LIMIT_MINUTES] of both the unit's own day (the table the user follows) and the user's own point alone (what the
 * app showed before units), for every event, measured with the engine's own rule: the unit's point riding beside the
 * user's (ruling R15), QMDB's AngleBased curves read on the 0.1° grid and at both latitudes, its bands either side of
 * 46° N and 48° N, so latitude counts as well as R40's longitude slope.
 *
 * - [extraMinutes]: at one point, each event's most minutes later (a start) or earlier (an end) over [dates] than the
 *   unit's own day and than the user's own point alone. A start before either, or an end after, is a broken
 *   promise ([Extra.broken]); by construction there is none.
 * - [reachKm]: the largest radius, by bisection between 0 and class D's R40 reach (three minutes of longitude), at
 *   which every one of eight bearings stays within the limit, then [SAFETY] of it, floored to 0.1 km and never below
 *   0.1 km (the unit's own point is always its own).
 *
 * The sample days are every fourth day of 2026 and the solstices and equinoxes; `CityUnitsTest` checks every day of
 * the year at the reach's edge for a large sample of units.
 */
object QmdbReach {
    const val LIMIT_MINUTES = 3

    /** The bisection's answer, kept to this share of it (the bearings and days between those sampled). */
    const val SAFETY = 0.9

    /** QMDB's places are all on UTC+5 (its city list); one zone serves every civil date. */
    val zone: TimeZone = TimeZone.of("Asia/Almaty")

    val dates: List<LocalDate> = (
        generateSequence(LocalDate(2026, 1, 1)) { it.plus(4, DateTimeUnit.DAY) }.takeWhile { it.year == 2026 }.toList() +
            listOf(LocalDate(2026, 3, 20), LocalDate(2026, 6, 21), LocalDate(2026, 9, 23), LocalDate(2026, 12, 21))
        ).distinct().sorted()

    /** The events a day shows, as the gate names them: a start or an end. */
    val events: List<Pair<String, Boolean>> = listOf(
        "fajr" to true, "sunrise" to false, "dhuhr" to true, "asr" to true, "asrOther" to true, "maghrib" to true,
        "isha" to true, "endOfEating" to false,
    )

    private fun PrayerDay.at(i: Int): Instant = when (i) {
        0 -> fajr
        1 -> sunrise
        2 -> dhuhr
        3 -> asr
        4 -> asrOther
        5 -> maghrib
        6 -> isha
        else -> endOfEating
    }

    /** [extra] per event (minutes, in [events]' order), and the cells that broke the promise there. */
    class Extra(val extra: IntArray, val broken: List<String>)

    private val entry: RegistryEntry by lazy { requireNotNull(Registry.byId("kz.qmdb")) }

    private fun day(method: world.taqwa.app.prayer.engine.method.TimetableMethod, point: GeoPoint, date: LocalDate): PrayerDay =
        DayPipeline.unended(
            Resolution(entry, point, null, shiaRegion = false, saudi = false, measured = true, method = method),
            date, zone,
        )

    /** The unit's own day on each of [days]: a user at its own point. */
    fun unitDays(unit: AuthorityUnit, days: List<LocalDate> = dates): Map<LocalDate, PrayerDay> {
        val method = Registry.methodInUnit(entry.id, entry.method!!, unit, unit.point)
        return days.associateWith { day(method, unit.point, it) }
    }

    /** Each event's most minutes beyond [unit]'s own day and the user's own point alone, at [at], over [days]. */
    fun extraMinutes(
        unit: AuthorityUnit,
        at: GeoPoint,
        own: Map<LocalDate, PrayerDay> = unitDays(unit),
        days: List<LocalDate> = dates,
    ): Extra {
        val inUnit = Registry.methodInUnit(entry.id, entry.method!!, unit, at)
        val alone = Registry.methodAlone(entry.id, entry.method!!, at)
        val extra = IntArray(events.size)
        val broken = mutableListOf<String>()
        for (date in days) {
            val shown = day(inUnit, at, date)
            val unitDay = own.getValue(date)
            val aloneDay = day(alone, at, date)
            for ((i, e) in events.withIndex()) {
                val (name, start) = e
                val s = shown.at(i)
                val vsUnit = ((if (start) s - unitDay.at(i) else unitDay.at(i) - s).inWholeMinutes).toInt()
                val vsAlone = ((if (start) s - aloneDay.at(i) else aloneDay.at(i) - s).inWholeMinutes).toInt()
                if (vsUnit < 0 || vsAlone < 0) broken += "$date $name at ${at.lat},${at.lon}: ${minOf(vsUnit, vsAlone)} min"
                extra[i] = max(extra[i], max(vsUnit, vsAlone))
            }
        }
        return Extra(extra, broken)
    }

    /** [from] moved [km] along [bearingDeg] on the sphere the registry measures with. */
    fun moved(from: GeoPoint, km: Double, bearingDeg: Double): GeoPoint {
        val d = km / 6371.0
        val b = bearingDeg * PI / 180
        val la1 = from.lat * PI / 180
        val lo1 = from.lon * PI / 180
        val la2 = asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
        val lo2 = lo1 + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
        return GeoPoint(la2 * 180 / PI, lo2 * 180 / PI)
    }

    val bearings: List<Double> = (0 until 8).map { it * 45.0 }

    /** Whether every bearing at [km] from [unit]'s point stays within the limit, every event, every sample day. */
    private fun holds(unit: AuthorityUnit, km: Double, own: Map<LocalDate, PrayerDay>): Boolean = bearings.all { b ->
        val e = extraMinutes(unit, moved(unit.point, km, b), own)
        require(e.broken.isEmpty()) { "${unit.id}: a promise broken inside its own reach: ${e.broken.take(3)}" }
        e.extra.all { it <= LIMIT_MINUTES }
    }

    /** [unit]'s reach in km ([QmdbReach]'s KDoc). */
    fun reachKm(unit: AuthorityUnit): Double {
        val own = unitDays(unit)
        val cap = lateReachKm(unit.point.lat, EntryClass.D_AUTHORITY)
        var lo = 0.0
        var hi = cap
        if (holds(unit, hi, own)) {
            lo = hi
        } else {
            repeat(BISECTIONS) {
                val mid = (lo + hi) / 2
                if (holds(unit, mid, own)) lo = mid else hi = mid
            }
        }
        return max(0.1, floor(SAFETY * lo * 10.0) / 10.0)
    }

    private const val BISECTIONS = 7

    /** The worst extra per event at the four edges ([edgeBearings]) of [unit]'s reach, every day of 2026. */
    fun edges(unit: AuthorityUnit, share: Double = 0.98, edgeBearings: List<Double> = bearings): Extra {
        val year = generateSequence(LocalDate(2026, 1, 1)) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it.year == 2026 }.toList()
        val own = unitDays(unit, year)
        val worst = IntArray(events.size)
        val broken = mutableListOf<String>()
        for (b in edgeBearings) {
            val at = moved(unit.point, unit.radiusKm * share, b)
            check(distanceKm(at, unit.point) <= unit.radiusKm)
            val e = extraMinutes(unit, at, own, year)
            for (i in worst.indices) worst[i] = max(worst[i], e.extra[i])
            broken += e.broken
        }
        return Extra(worst, broken)
    }
}
