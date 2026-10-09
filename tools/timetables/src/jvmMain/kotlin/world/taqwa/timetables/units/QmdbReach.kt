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
import world.taqwa.app.prayer.engine.registry.authorities.CentralAsia
import world.taqwa.app.prayer.engine.registry.data.QmdbPlaceCodec
import world.taqwa.app.prayer.engine.registry.distanceKm
import world.taqwa.app.prayer.engine.registry.lateReachKm
import java.security.MessageDigest
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

    /**
     * Each event's most minutes beyond [unit]'s own day and the user's own point alone, at [at], over [days], and
     * beyond [also] where given (another place's own day: a village's, inside a city's reach).
     */
    fun extraMinutes(
        unit: AuthorityUnit,
        at: GeoPoint,
        own: Map<LocalDate, PrayerDay> = unitDays(unit),
        days: List<LocalDate> = dates,
        also: Map<LocalDate, PrayerDay>? = null,
    ): Extra {
        val inUnit = Registry.methodInUnit(entry.id, entry.method!!, unit, at)
        val alone = Registry.methodAlone(entry.id, entry.method!!, at)
        val extra = IntArray(events.size)
        val broken = mutableListOf<String>()
        for (date in days) {
            val shown = day(inUnit, at, date)
            val references = listOfNotNull(own.getValue(date), day(alone, at, date), also?.getValue(date))
            for ((i, e) in events.withIndex()) {
                val (name, start) = e
                val s = shown.at(i)
                val beyond = references.map { ((if (start) s - it.at(i) else it.at(i) - s).inWholeMinutes).toInt() }
                if (beyond.any { it < 0 }) broken += "$date $name at ${at.lat},${at.lon}: ${beyond.min()} min"
                extra[i] = max(extra[i], beyond.max())
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

    /**
     * What every reach was measured with, as one hash: the generator writes it into QmdbPlaceList.kt
     * ([QmdbPlaceList.FINGERPRINT][world.taqwa.app.prayer.engine.registry.data.QmdbPlaceList.FINGERPRINT]) and
     * `CityUnitsTest` fails when it no longer matches. It covers this object's own rule (the limit, the share kept,
     * the bisection, the bearings, the days, the events, the class D cap), the codec, and kz.qmdb's method, bands
     * and curves through what they give: each event's time, in whole seconds, on [PROBE_DAYS] at [PROBE_POINTS]
     * (straddling 46° N and 48° N and every zone), for a unit there, for a user beside it and for that user alone.
     * Any change to the method, its margins, its bands, the AngleBased curves or the engine's way of riding a
     * unit's point beside the user's moves some of them.
     *
     * The same on every machine: no double is printed (the cap in metres, the coordinates as literal sums, the
     * times as whole epoch seconds), so neither `Double.toString` nor a maths routine's last bit can move it.
     */
    fun fingerprint(): String {
        val text = StringBuilder()
        text.append("rule ").append(LIMIT_MINUTES).append(' ').append(Math.round(SAFETY * 1000)).append(' ').append(BISECTIONS)
        text.append(" bearings ").append(bearings.joinToString(",") { Math.round(it * 1000).toString() })
        text.append(" days ").append(dates.joinToString(",")).append(" zone ").append(zone.id)
        text.append(" events ").append(events.joinToString(",") { "${it.first}:${it.second}" })
        text.append(" cap ").append((40..56).joinToString(",") { Math.round(lateReachKm(it.toDouble(), EntryClass.D_AUTHORITY) * 1000).toString() })
        text.append("\ncodec ").append(QmdbPlaceCodec.WIDTH).append(' ').append(Math.round(QmdbPlaceCodec.LAT_BASE * 1000))
            .append(' ').append(Math.round(QmdbPlaceCodec.LON_BASE * 1000)).append(' ').append(QmdbPlaceCodec.SCALE)
            .append(' ').append(QmdbPlaceCodec.DIGITS)
        for ((lat, lon) in PROBE_POINTS) {
            val point = GeoPoint(lat, lon)
            val unit = AuthorityUnit("probe", "probe", point, lateReachKm(lat, EntryClass.D_AUTHORITY), method = CentralAsia.kazakhstanAt(point), named = false)
            val user = GeoPoint(lat + PROBE_STEP_LAT, lon + PROBE_STEP_LON)
            val ownMethod = Registry.methodInUnit(entry.id, entry.method!!, unit, point)
            val inUnit = Registry.methodInUnit(entry.id, entry.method!!, unit, user)
            val alone = Registry.methodAlone(entry.id, entry.method!!, user)
            text.append("\nprobe ").append(Math.round(lat * 1000)).append(',').append(Math.round(lon * 1000))
            for (date in PROBE_DAYS) {
                for ((method, at) in listOf(ownMethod to point, inUnit to user, alone to user)) {
                    val day = day(method, at, date)
                    text.append(' ').append(events.indices.joinToString(",") { day.at(it).epochSeconds.toString() })
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    /** The probe's places: either side of 46° N and 48° N, the north to Isakovka, the west's zones, the far south. */
    private val PROBE_POINTS: List<Pair<Double, Double>> = listOf(
        40.6 to 68.5, 41.3 to 69.0, 42.3 to 69.6, 42.9 to 71.4, 43.24 to 76.95, 43.6 to 51.2, 44.8 to 65.5,
        45.0 to 78.4, 45.95 to 63.3, 46.0 to 61.7, 46.05 to 74.98, 46.5 to 54.0, 47.12 to 51.88, 47.5 to 84.9,
        47.95 to 80.43, 47.99 to 51.62, 48.0 to 67.5, 48.02 to 67.7, 48.8 to 58.1, 49.8 to 73.1, 50.3 to 57.2,
        50.4 to 80.2, 51.13 to 71.43, 51.2 to 51.4, 52.3 to 76.96, 53.2 to 63.6, 54.0 to 69.0, 54.86 to 69.14,
        55.4 to 68.9,
    )

    /** The probe's user, beside each place: north-east of it, across 46° N and 48° N from the places just below. */
    private const val PROBE_STEP_LAT = 0.09
    private const val PROBE_STEP_LON = 0.11

    /** The 1st and the 15th of each month of 2026, and the solstices. */
    private val PROBE_DAYS: List<LocalDate> =
        ((1..12).flatMap { listOf(LocalDate(2026, it, 1), LocalDate(2026, it, 15)) } + listOf(LocalDate(2026, 6, 21), LocalDate(2026, 12, 21))).sorted()

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
