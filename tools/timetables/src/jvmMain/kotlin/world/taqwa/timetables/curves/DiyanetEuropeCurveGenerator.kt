package world.taqwa.timetables.curves

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import world.taqwa.app.prayer.engine.astro.SolarMath
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.CURVE_REFERENCE_YEAR
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.method.curveSlot
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import java.io.File
import java.util.Locale
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Writes `DiyanetEuropeCurves.kt` (rulings R39, R42, R44): for each of Diyanet's European city
 * tables north of 44.5°, the per-slot depressions of its Fajr, Isha and end of eating, derived from
 * every held row of that city (the research captures and the monitor's) under the method's own
 * daily sun at the city's point.
 *
 *     ./gradlew -p tools/timetables generateDiyanetEuropeCurves [-Pofficial=<dir>] [-Pout=<file>]
 *
 * **Why a slot is a bound, not the printed minute** (monitor round, brief D). Diyanet prints each
 * time rounded to the nearest minute, so a printed minute says only that its own moment lies
 * within 30 s of it; which side depends on the year, because the sun's position on a month and day
 * shifts through the leap cycle. A slot derived from the printed minute itself (the first curves
 * took the depression 25 s before it) reproduced one year's rounding to the second and, on the
 * other side of the phase, showed the minute before Diyanet's: Isha a minute early at five cities
 * on 26–28 October 2026, the first winter-time days no held table had covered. So each row now
 * gives the latest moment Diyanet's own could be, the printed minute + 30 s + [SAFETY], for a
 * start, and the earliest, − 30 s − [SAFETY], for the end; with the curve margins
 * ([Diyanet.CITY_CURVE_START], [Diyanet.CITY_CURVE_END]) a start then shows the printed minute or
 * the one after and the end never passes it, in every year.
 *
 * On the days Diyanet prints the plain 18° dawn or 16° dusk (outside its takdir) the bound is
 * tighter than the rounding: the plain method's own moment ([Diyanet.europeMethod]'s fitted
 * margins, never early on the held rows with the Fitter's safety), taken wherever the plain method
 * holds the row (its start no earlier than the printed minute, its end no later), which is exactly
 * the physical 18°/16° moment shifted so that the curve shows the same minute as the plain method.
 * On takdir days the plain moment is hours off and the rounding bound wins; on the days between,
 * whichever is tighter. Across the rows of one slot (the same month and day in several years) a
 * start takes the tightest bound (the earliest moment: Fajr's largest depression, Isha's smallest)
 * and the end likewise (the latest moment: the largest depression); a slot without a row (29
 * February) takes the safe side of its neighbours. Then ruling R28's envelope, each slot the
 * latest of the day before, the day and the day after for a start and the earliest for the end,
 * so the leap cycle's drift of the sun cannot make a start early or the end late; and rounding to
 * a hundredth of a degree on the safe side.
 *
 * A takdir moment must lie on the sun's own side of its lowest point (a Fajr after it, an Isha
 * before it) for a depression to name it; every held row does, and a row that did not would fail
 * the generator rather than write a slot the engine would read on the wrong side.
 *
 * Derived parameters only (ruling R42): depressions, never the printed times; the tables stay in
 * the restricted archive.
 */
object DiyanetEuropeCurveGenerator {

    /** The Fitter's safety, beyond the half minute of Diyanet's rounding. */
    const val SAFETY = 5

    /** One city's derived curves, depressions in degrees by ruling R28's slot. */
    class Curves(val fajr: DoubleArray, val isha: DoubleArray, val end: DoubleArray)

    /** A printed row: the minutes after local midnight of the Fajr (also the end of eating) and the Isha columns. */
    class Row(val date: LocalDate, val fajrMinutes: Int, val ishaMinutes: Int)

    /**
     * The curves for the table printed for [point] in [zone] from its [rows], for the plain
     * [method] (its angles and its fitted margins) and the curve margins [startMargin] / [endMargin].
     */
    fun derive(
        rows: Collection<Row>,
        point: GeoPoint,
        zone: TimeZone,
        method: TimetableMethod = Diyanet.europeMethod,
        startMargin: Int = Diyanet.CITY_CURVE_START,
        endMargin: Int = Diyanet.CITY_CURVE_END,
    ): Curves {
        val fajrAngle = method.fajrAngle
        val ishaAngle = (method.isha as IshaRule.Angle).degrees
        val fajrBySlot = arrayOfNulls<Double>(SLOTS)
        val ishaBySlot = arrayOfNulls<Double>(SLOTS)
        val endBySlot = arrayOfNulls<Double>(SLOTS)
        for (row in rows) {
            val slot = curveSlot(row.date)
            val sky = Sky(point, row.date, zone)
            val printedFajr = Gate.officialInstant(row.date, row.fajrMinutes, Event.FAJR, zone).epochSeconds.toDouble()
            val printedIsha = Gate.officialInstant(row.date, row.ishaMinutes, Event.ISHA, zone).epochSeconds.toDouble()
            val dawn = sky.clock.altitudeTime(-fajrAngle, morning = true)
            val dusk = sky.clock.altitudeTime(-ishaAngle, morning = false)

            // A start: the earliest moment that is still no earlier than Diyanet's own.
            var fajrBound = printedFajr + 30 + SAFETY
            if (dawn != null && startOf(dawn + method.margins.fajr) >= printedFajr) {
                fajrBound = min(fajrBound, dawn + method.margins.fajr - startMargin)
            }
            var ishaBound = printedIsha + 30 + SAFETY
            if (dusk != null && startOf(dusk + method.margins.isha) >= printedIsha) {
                ishaBound = min(ishaBound, dusk + method.margins.isha - startMargin)
            }
            // The end: the latest moment that is still no later than Diyanet's own.
            var endBound = printedFajr - 30 - SAFETY
            if (dawn != null && endOf(dawn + method.endOfEatingMarginSeconds) <= printedFajr) {
                endBound = max(endBound, dawn + method.endOfEatingMarginSeconds - endMargin)
            }
            require(fajrBound > sky.lowestBefore && endBound > sky.lowestBefore) {
                "${row.date}: Diyanet's Fajr is not after the sun's lowest point at $point; no depression names it"
            }
            require(ishaBound < sky.lowestAfter) {
                "${row.date}: Diyanet's Isha is not before the sun's lowest point at $point; no depression names it"
            }
            fajrBySlot[slot] = maxOrNew(fajrBySlot[slot], sky.depressionAt(fajrBound))
            ishaBySlot[slot] = minOrNew(ishaBySlot[slot], sky.depressionAt(ishaBound))
            endBySlot[slot] = maxOrNew(endBySlot[slot], sky.depressionAt(endBound))
        }
        val fajr = filled(fajrBySlot, later = ::min)
        val isha = filled(ishaBySlot, later = ::max)
        val end = filled(endBySlot, later = ::max)
        return Curves(
            fajr = DoubleArray(SLOTS) { i -> floor(minOf(fajr[before(i)], fajr[i], fajr[after(i)]) * 100) / 100 },
            isha = DoubleArray(SLOTS) { i -> ceil(maxOf(isha[before(i)], isha[i], isha[after(i)]) * 100) / 100 },
            end = DoubleArray(SLOTS) { i -> ceil(maxOf(end[before(i)], end[i], end[after(i)]) * 100) / 100 },
        )
    }

    /** Slots without a row take the safe side ([later]) of their nearest filled neighbours. */
    private fun filled(bySlot: Array<Double?>, later: (Double, Double) -> Double): DoubleArray {
        require(bySlot.count { it != null } >= 2) { "too few rows to derive a curve" }
        return DoubleArray(SLOTS) { i ->
            bySlot[i] ?: run {
                var b = i
                while (bySlot[b] == null) b = before(b)
                var a = i
                while (bySlot[a] == null) a = after(a)
                later(bySlot[b]!!, bySlot[a]!!)
            }
        }
    }

    private fun before(i: Int) = (i + SLOTS - 1) % SLOTS
    private fun after(i: Int) = (i + 1) % SLOTS
    private fun maxOrNew(a: Double?, b: Double) = if (a == null) b else max(a, b)
    private fun minOrNew(a: Double?, b: Double) = if (a == null) b else min(a, b)
    private fun startOf(epochSeconds: Double): Double = ceil(epochSeconds / 60.0) * 60
    private fun endOf(epochSeconds: Double): Double = floor(epochSeconds / 60.0) * 60

    /**
     * The method's own daily sun over [point] on [date]: declination and equation of time once at
     * 0h UT ([SunModel.DAILY_0H_UT], as [Diyanet.europeMethod] computes), the noon it gives and the
     * sun's depression at any moment of that day by the same hour-angle formula.
     */
    private class Sky(point: GeoPoint, date: LocalDate, zone: TimeZone) {
        private val offset = zone.offsetAt(LocalDateTime(date, LocalTime(12, 0)).toInstant(zone)).totalSeconds
        val clock = SunClock(point.lat, point.lon, date, offset, SunModel.DAILY_0H_UT)
        private val noon = clock.transit()
        private val phi = SolarMath.rad(point.lat)
        private val delta = SolarMath.rad(SolarMath.sun(SolarMath.julianDay(date.toEpochDays() * SolarMath.SECONDS_PER_DAY)).declinationDeg)
        val lowestBefore = noon - SolarMath.SECONDS_PER_DAY / 2
        val lowestAfter = noon + SolarMath.SECONDS_PER_DAY / 2

        fun depressionAt(epochSeconds: Double): Double {
            val h = SolarMath.rad((epochSeconds - noon) / SECONDS_PER_DEGREE)
            return -SolarMath.deg(asin(sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(h)))
        }
    }

    /** One city table: its unit key, name, point, zone and the archive tables it is held in. */
    class City(val key: String, val name: String, val point: GeoPoint, val zone: String, val paths: List<String>)

    /** The rows of [paths] under [archiveRoot], the F+E and I columns of the daily layout. */
    fun readRows(archiveRoot: File, paths: List<String>): List<Row> {
        val byDate = LinkedHashMap<LocalDate, Row>()
        for (path in paths) {
            val file = archiveRoot.resolve(path)
            require(file.isFile) { "$path is not held under $archiveRoot" }
            for (line in file.readLines()) {
                val text = line.trim()
                if (text.isEmpty() || text.startsWith("#")) continue
                val t = text.split(Regex("\\s+"))
                if (t.size < 7) continue
                val date = runCatching { LocalDate.parse(t[0]) }.getOrNull() ?: continue
                fun minutes(s: String): Int? = Regex("""(\d{1,2}):(\d{2})""").matchEntire(s)?.let { m ->
                    m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                }
                val fajr = minutes(t[1]) ?: continue
                val isha = minutes(t[6]) ?: continue
                // A date two captures both hold prints the same minutes; the later capture wins if not.
                byDate[date] = Row(date, fajr, isha)
            }
        }
        return byDate.values.toList()
    }

    /** `DiyanetEuropeCurves.kt`'s exact source text for [cities] (south to north) and their [curves]. */
    fun render(cities: List<City>, curves: List<Curves>, rows: List<Int>, first: LocalDate, last: LocalDate): String = buildString {
        append(
            """
            |package world.taqwa.app.prayer.engine.registry.data
            |
            |/**
            | * Diyanet's European method at its own city tables (rulings R39, R42, R44): for each held city north
            | * of 44.5° (its takdir), the sun's depression at each slot of ruling R28 (the month and day of
            | * 2028-01-01 + i) at which its Fajr, Isha and end of eating are read, so that its point table starts
            | * each prayer where Diyanet prints it, takdir included, rather than at a generic night fraction (up to
            | * 39 min late at Fajr and 143 min at Isha before).
            | *
            | * Generated by tools/timetables (`./gradlew -p tools/timetables generateDiyanetEuropeCurves`; the
            | * generator's own KDoc says how): derived parameters, not times. Each slot is a bound on Diyanet's own
            | * moment under the method's own daily sun at the city's point: for a start the earliest moment that is
            | * still no earlier than Diyanet's in any year (its printed minute + 35 s under its nearest-minute
            | * rounding, or, where it prints the plain 18°/16° that day, the plain method's own moment), for the
            | * end the latest that is still no later; across the years a slot is held in, the tightest; then each
            | * slot the safe side of the day before, the day and the day after (ruling R28), rounded to a hundredth
            | * of a degree on the safe side. Source: Diyanet İşleri Başkanlığı's tables for these cities
            | * (namazvakitleri.diyanet.gov.tr; restricted, "all rights reserved"), ${rows.sum()} rows over
            | * ${cities.size} cities, $first – $last: only these depressions are kept; the tables themselves are not
            | * committed.
            | */
            |internal object DiyanetEuropeCurves {
            |    /** One city's Fajr, Isha and end-of-eating depressions (degrees, by slot) at its point. */
            |    class Curves(val fajrEncoded: String, val ishaEncoded: String, val endEncoded: String) {
            |        val fajr: DoubleArray by lazy { decode(fajrEncoded) }
            |        val isha: DoubleArray by lazy { decode(ishaEncoded) }
            |        val end: DoubleArray by lazy { decode(endEncoded) }
            |
            |        private fun decode(s: String) =
            |            s.trim().split(' ').map { it.toInt() / 100.0 }.toDoubleArray().also { require(it.size == 366) }
            |    }
            |
            |    /** By the city key of Diyanet's point tables (Diyanet.europeUnits). */
            |    val byCity: Map<String, Curves> by lazy {
            |        mapOf(
            |
            """.trimMargin(),
        )
        for ((city, c) in cities.zip(curves)) {
            append("            // ${city.name} (${city.point.lat}° N)\n")
            append("            \"${city.key}\" to Curves(\n")
            for (array in listOf(c.fajr, c.isha, c.end)) {
                val values = array.map { (it * 100).let { v -> Math.round(v) } }
                values.chunked(20).forEachIndexed { i, chunk ->
                    val text = chunk.joinToString(" ")
                    val last = i == values.size / 20 && values.size % 20 != 0 || (values.size % 20 == 0 && i == values.size / 20 - 1)
                    append("                \"$text" + (if (last) "\",\n" else " \" +\n"))
                }
            }
            append("            ),\n")
        }
        append("        )\n    }\n}\n")
    }

    private const val SLOTS = 366
    private const val SECONDS_PER_DEGREE = 240.0

    /** The reference year's slots, for a caller that wants each slot's date. */
    fun slotDate(i: Int): LocalDate = LocalDate(CURVE_REFERENCE_YEAR, 1, 1).plus(i, DateTimeUnit.DAY)
}

/**
 * Diyanet's European city tables north of 44.5°, south to north, with the archive tables each is
 * held in (the research captures of September 2026 and the monitor's, restricted). Sarajevo, south
 * of 44.5°, follows the plain method and has no curve.
 */
val DIYANET_EUROPE_CITIES: List<DiyanetEuropeCurveGenerator.City> = listOf(
    DiyanetEuropeCurveGenerator.City(
        "zurich", "Zürich", GeoPoint(47.36667, 8.55), "Europe/Zurich",
        listOf("archive/tables/moral-auth-world/europe/verify/dy-zurich.txt", "archive/tables/monitor/tr-diyanet-europe/zurich.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "freiburg", "Freiburg", GeoPoint(47.9959, 7.85222), "Europe/Berlin",
        listOf("archive/tables/moral-auth-world/europe/dy-freiburg.txt", "archive/tables/monitor/tr-diyanet-europe/freiburg.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "munich", "München", GeoPoint(48.13743, 11.57549), "Europe/Berlin",
        listOf("archive/tables/moral-auth-world/europe/dy-munich.txt", "archive/tables/monitor/tr-diyanet-europe/munich.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "wien", "Wien", GeoPoint(48.20849, 16.37208), "Europe/Vienna",
        listOf("archive/tables/moral-auth-world/europe/verify/dy-wien.txt", "archive/tables/monitor/tr-diyanet-europe/wien.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "paris", "Paris", GeoPoint(48.85341, 2.3488), "Europe/Paris",
        listOf("archive/tables/moral-auth-world/europe/dy-paris.txt", "archive/tables/monitor/tr-diyanet-europe/paris.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "brussels", "Brussels", GeoPoint(50.85045, 4.34878), "Europe/Brussels",
        listOf("archive/tables/moral-auth-world/europe/dy-brussels.txt", "archive/tables/monitor/tr-diyanet-europe/brussels.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "london", "London", GeoPoint(51.5074, -0.1278), "Europe/London",
        listOf("archive/tables/moral-auth-world/europe/dy-london.txt", "archive/tables/monitor/tr-diyanet-europe/london.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "amsterdam", "Amsterdam", GeoPoint(52.37403, 4.88969), "Europe/Amsterdam",
        listOf("archive/tables/moral-auth-world/europe/dy-amsterdam.txt", "archive/tables/monitor/tr-diyanet-europe/amsterdam.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "berlin", "Berlin", GeoPoint(52.52437, 13.41053), "Europe/Berlin",
        listOf("archive/tables/moral-auth-world/europe/dy-berlin.txt", "archive/tables/monitor/tr-diyanet-europe/berlin.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "stockholm", "Stockholm", GeoPoint(59.32938, 18.06871), "Europe/Stockholm",
        listOf("archive/tables/moral-auth-world/europe/verify/dy-stockholm.txt", "archive/tables/monitor/tr-diyanet-europe/stockholm.txt"),
    ),
    DiyanetEuropeCurveGenerator.City(
        "oslo", "Oslo", GeoPoint(59.91273, 10.74609), "Europe/Oslo",
        listOf("archive/tables/moral-auth-world/europe/verify/dy-oslo.txt", "archive/tables/monitor/tr-diyanet-europe/oslo.txt"),
    ),
)

/** `--repo <repository root> --official <official root> [--out <file>]`. */
fun main(args: Array<String>) {
    val options = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        options[key] = args.getOrNull(i + 1) ?: fail("$key needs a value")
        i += 2
    }
    val repo = File(options["--repo"] ?: fail("--repo is required"))
    val official = File(options["--official"] ?: fail("--official is required"))
    val out = File(
        options["--out"] ?: repo.resolve("shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/DiyanetEuropeCurves.kt").path,
    )
    val cities = DIYANET_EUROPE_CITIES.sortedBy { it.point.lat }
    val curves = mutableListOf<DiyanetEuropeCurveGenerator.Curves>()
    val counts = mutableListOf<Int>()
    var first: LocalDate? = null
    var last: LocalDate? = null
    for (city in cities) {
        val rows = DiyanetEuropeCurveGenerator.readRows(official, city.paths)
        require(rows.isNotEmpty()) { "${city.key}: no rows" }
        counts += rows.size
        first = listOfNotNull(first, rows.minOf { it.date }).min()
        last = listOfNotNull(last, rows.maxOf { it.date }).max()
        curves += DiyanetEuropeCurveGenerator.derive(rows, city.point, TimeZone.of(city.zone))
        println(String.format(Locale.ROOT, "%-10s %4d rows %s..%s", city.key, rows.size, rows.minOf { it.date }, rows.maxOf { it.date }))
    }
    out.writeText(DiyanetEuropeCurveGenerator.render(cities, curves, counts, first!!, last!!))
    println("Wrote ${cities.size} cities' curves to ${out.relativeTo(repo).invariantSeparatorsPath}")
}

private fun fail(problem: String): Nothing {
    System.err.println("generateDiyanetEuropeCurves: $problem")
    exitProcess(2)
}
