package world.taqwa.timetables.curves

import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.Prayer
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
 * The two held years are only 0.24 day apart in the sun's position on a month and day; the leap
 * cycle's worst is about 0.76 day (2027 to 2028 after 29 February, 2031 to 2032). So each direction
 * is also read a slot off (review D round 1, M6): the 2027 curves' slot for the day before on a 2026
 * date, and the 2026 curves' slot for the day after on a 2027 date, each 0.76 day between the sun
 * the slot was fitted on and the sun it is read on, the cycle's worst, on the real autumn takdir.
 * That is what ruling R28's envelope (each slot the safe side of the day before, the day and the day
 * after) promises to cover. The real 2028 rows become gate rows the moment the monitor holds them.
 *
 * A cell the day declares not followed (point 8's Fajr after the sun's sunrise, point 9's Isha
 * before the Maghrib shown) is counted, never checked, as in the gate (rulings R82, R90); none falls
 * in the autumn dates. Reads the restricted archive at the official root the build hands it;
 * skipped, and says so, where the archive is not held (CI).
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
        var declared = 0
        for (city in DIYANET_EUROPE_CITIES) {
            val zone = TimeZone.of(city.zone)
            val rows = DiyanetEuropeCurveGenerator.readRows(official, city.paths)
            val byYear = rows.groupBy { it.date.year }
            for ((fitYear, testYear) in listOf(2027 to 2026, 2026 to 2027)) {
                val fitRows = byYear.getValue(fitYear)
                val testDates = byYear.getValue(fitYear).map { it.date.month to it.date.day }.toSet()
                val testRows = byYear.getValue(testYear).filter { (it.date.month to it.date.day) in testDates }
                val curves = DiyanetEuropeCurveGenerator.derive(fitRows, city.point, zone)
                // Slot i holds slot i − shift's value: +1 reads the day before's slot on a 2026 date (the
                // 2027 sun it was fitted on is 0.76 day earlier than the 2026 sun it meets), −1 the day
                // after's on a 2027 date (0.76 day later).
                for (shift in listOf(0, if (fitYear == 2027) 1 else -1)) {
                    fun rotated(a: DoubleArray) = DoubleArray(a.size) { i -> a[Math.floorMod(i - shift, a.size)] }
                    val method = Diyanet.curveMethod(
                        "holdout.${city.key}.$fitYear.$shift", rotated(curves.fajr), rotated(curves.isha), rotated(curves.end),
                    )
                    val late = LongArray(3)
                    val worst = LongArray(3)
                    for (row in testRows) {
                        val day = DayComputer.compute(method, city.point, row.date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
                        val fajr = Gate.officialInstant(row.date, row.fajrMinutes, Event.FAJR, zone)
                        val isha = Gate.officialInstant(row.date, row.ishaMinutes, Event.ISHA, zone)
                        val deltas = longArrayOf(
                            (day.fajr - fajr).inWholeSeconds, (day.isha - isha).inWholeSeconds, (fajr - day.endOfEating).inWholeSeconds,
                        )
                        val names = listOf("Fajr", "Isha", "end of eating")
                        val skipped = listOf(Prayer.FAJR in day.notFollowed, Prayer.ISHA in day.notFollowed, false)
                        for (i in 0..2) {
                            if (skipped[i]) {
                                declared++
                                continue
                            }
                            if (deltas[i] < 0) {
                                broken += "${city.key} fit $fitYear test $testYear shift $shift ${row.date}: ${names[i]} on the wrong side by ${-deltas[i]} s"
                            }
                            late[i] += deltas[i]
                            worst[i] = maxOf(worst[i], deltas[i])
                        }
                    }
                    val n = testRows.size
                    report.append(
                        String.format(
                            Locale.ROOT, "%-10s fit %d test %d shift %2d (%3d days): mean late Fajr %3d s Isha %3d s, end early %3d s; worst %d/%d/%d min\n",
                            city.key, fitYear, testYear, shift, n, late[0] / n, late[1] / n, late[2] / n, worst[0] / 60, worst[1] / 60, worst[2] / 60,
                        ),
                    )
                }
            }
        }
        println(report)
        println("cross-year holdout: $declared cells declared")
        assertTrue(broken.isEmpty(), "${broken.size} on the wrong side:\n" + broken.take(60).joinToString("\n"))
    }
}
