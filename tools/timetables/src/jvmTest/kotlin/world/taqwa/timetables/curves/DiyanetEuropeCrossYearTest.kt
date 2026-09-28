package world.taqwa.timetables.curves

import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The cross-year holdout the monitor round asked for (brief D): each Diyanet city's curves derived
 * from one year's rows alone and held against the other year's rows on the same calendar dates, in
 * both directions, for every city. The captures hold 2026 from 25 September to 29 October and all
 * of 2027, so the 2027 curves are tested on the 35 autumn days of 2026 and the 2026 curves on the
 * same 35 dates of 2027 (a slot without a 2026 row takes its neighbours', never a 2027 row). Never
 * early and never a late end in either direction; the lateness each direction costs is printed.
 *
 * Reads the restricted archive at the official root the build hands it; skipped, and says so,
 * where the archive is not held (CI).
 */
class DiyanetEuropeCrossYearTest {

    private val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))

    @Test
    fun `curves from one year's rows are never early and never end late on the other year's same dates`() {
        if (!official.resolve("archive").isDirectory) {
            println("cross-year holdout skipped: no archive under $official")
            return
        }
        val broken = mutableListOf<String>()
        val report = StringBuilder()
        for (city in DIYANET_EUROPE_CITIES) {
            val zone = TimeZone.of(city.zone)
            val rows = DiyanetEuropeCurveGenerator.readRows(official, city.paths)
            val byYear = rows.groupBy { it.date.year }
            for ((fitYear, testYear) in listOf(2027 to 2026, 2026 to 2027)) {
                val fitRows = byYear.getValue(fitYear)
                val testDates = byYear.getValue(fitYear).map { it.date.month to it.date.day }.toSet()
                val testRows = byYear.getValue(testYear).filter { (it.date.month to it.date.day) in testDates }
                val curves = DiyanetEuropeCurveGenerator.derive(fitRows, city.point, zone)
                val method = Diyanet.curveMethod("holdout.${city.key}.$fitYear", curves.fajr, curves.isha, curves.end)
                var late = LongArray(3)
                var worst = LongArray(3)
                for (row in testRows) {
                    val day = DayComputer.compute(method, city.point, row.date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
                    val fajr = Gate.officialInstant(row.date, row.fajrMinutes, Event.FAJR, zone)
                    val isha = Gate.officialInstant(row.date, row.ishaMinutes, Event.ISHA, zone)
                    val deltas = longArrayOf(
                        (day.fajr - fajr).inWholeSeconds, (day.isha - isha).inWholeSeconds, (fajr - day.endOfEating).inWholeSeconds,
                    )
                    val names = listOf("Fajr", "Isha", "end of eating")
                    for (i in 0..2) {
                        if (deltas[i] < 0) broken += "${city.key} fit $fitYear test $testYear ${row.date}: ${names[i]} on the wrong side by ${-deltas[i] / 60} min"
                        late[i] += deltas[i]
                        worst[i] = maxOf(worst[i], deltas[i])
                    }
                }
                val n = testRows.size
                report.append(
                    String.format(
                        Locale.ROOT, "%-10s fit %d test %d (%3d days): mean late Fajr %3d s Isha %3d s, end early %3d s; worst %d/%d/%d min\n",
                        city.key, fitYear, testYear, n, late[0] / n, late[1] / n, late[2] / n, worst[0] / 60, worst[1] / 60, worst[2] / 60,
                    ),
                )
            }
        }
        println(report)
        assertTrue(broken.isEmpty(), "${broken.size} on the wrong side:\n" + broken.joinToString("\n"))
    }
}
