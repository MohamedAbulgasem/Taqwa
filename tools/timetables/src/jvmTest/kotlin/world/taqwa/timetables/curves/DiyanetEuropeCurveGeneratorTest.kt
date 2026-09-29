package world.taqwa.timetables.curves

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.authorities.Diyanet
import world.taqwa.timetables.gate.Event
import world.taqwa.timetables.gate.Gate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The curve derivation on an invented table (ruling R69: no printed time from a restricted table
 * here): an invented authority at an invented point prints, rounded to the nearest minute, the plain
 * 18°/16° with a model offset of its own outside its summer takdir and a night-fraction takdir
 * inside it. Its 2027 table alone derives the curves; the curves are then held against the same
 * authority's tables for every year of two leap cycles, which is what the monitor found the first
 * curves could not do: a slot that reproduces one year's rounding to the second shows the minute
 * before the authority's in the next.
 */
class DiyanetEuropeCurveGeneratorTest {
    private val point = GeoPoint(51.9, 4.7)
    private val zone = TimeZone.of("Europe/Amsterdam")
    private val method = Diyanet.europeMethod

    /** The invented authority's own moments on [date] (epoch seconds), Fajr and Isha. */
    private fun own(date: LocalDate): Pair<Double, Double> {
        val offset = zone.offsetAt(LocalDateTime(date, LocalTime(12, 0)).toInstant(zone)).totalSeconds
        val sun = SunClock(point.lat, point.lon, date, offset, SunModel.DAILY_0H_UT)
        val next = SunClock(point.lat, point.lon, date.plus(1, DateTimeUnit.DAY), offset, SunModel.DAILY_0H_UT)
        val previous = SunClock(point.lat, point.lon, date.plus(-1, DateTimeUnit.DAY), offset, SunModel.DAILY_0H_UT)
        val sunrise = sun.altitudeTime(-0.8333, morning = true)!!
        val sunset = sun.altitudeTime(-0.8333, morning = false)!!
        val dawn = sun.altitudeTime(-18.0, morning = true)
        val dusk = sun.altitudeTime(-16.0, morning = false)
        val takdir = date.month.ordinal + 1 in 5..8 || (date.month.ordinal + 1 == 4 && date.day >= 25)
        val fajr = if (takdir) {
            val night = sunrise - previous.altitudeTime(-0.8333, morning = false)!!
            max(dawn?.let { it - 12 } ?: Double.NEGATIVE_INFINITY, sunrise - 0.206 * night)
        } else {
            dawn!! - 12
        }
        val isha = if (takdir) {
            val night = next.altitudeTime(-0.8333, morning = true)!! - sunset
            min(dusk?.let { it + 8 } ?: Double.POSITIVE_INFINITY, sunset + 0.19 * night)
        } else {
            dusk!! + 8
        }
        return fajr to isha
    }

    /** [epochSeconds] printed as the invented table prints it: minutes after local midnight, nearest minute. */
    private fun printed(date: LocalDate, epochSeconds: Double): Int {
        val minute = round(epochSeconds / 60.0) * 60
        val local = kotlin.time.Instant.fromEpochSeconds(minute.toLong()).toLocalDateTime(zone)
        val days = (local.date.toEpochDays() - date.toEpochDays()).toInt()
        return days * 24 * 60 + local.hour * 60 + local.minute
    }

    private fun rows(year: Int): List<DiyanetEuropeCurveGenerator.Row> {
        val out = mutableListOf<DiyanetEuropeCurveGenerator.Row>()
        var d = LocalDate(year, 1, 1)
        while (d.year == year) {
            val (fajr, isha) = own(d)
            out += DiyanetEuropeCurveGenerator.Row(d, printed(d, fajr), printed(d, isha))
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return out
    }

    private val curves by lazy { DiyanetEuropeCurveGenerator.derive(rows(2027), point, zone, method) }
    private val curveMethod by lazy { Diyanet.curveMethod("test.curves", curves.fajr, curves.isha, curves.end) }

    private fun shown(date: LocalDate) =
        DayComputer.compute(curveMethod, point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())

    @Test
    fun `curves from one year are never early and never end late in any year of two leap cycles`() {
        var checked = 0
        var lateSeconds = 0L
        var worst = 0L
        for (year in 2025..2032) {
            for (row in rows(year)) {
                val day = shown(row.date)
                val fajr = Gate.officialInstant(row.date, row.fajrMinutes, Event.FAJR, zone)
                val isha = Gate.officialInstant(row.date, row.ishaMinutes, Event.ISHA, zone)
                assertTrue(day.fajr >= fajr, "${row.date}: Fajr shown before the printed minute")
                assertTrue(day.isha >= isha, "${row.date}: Isha shown before the printed minute")
                assertTrue(day.endOfEating <= fajr, "${row.date}: the end of eating shown after the printed Fajr")
                val late = maxOf((day.fajr - fajr).inWholeSeconds, (day.isha - isha).inWholeSeconds)
                lateSeconds += late
                // The envelope over the neighbouring days costs up to a day's step: the invented takdir
                // switches on and off in cliffs of many minutes where Diyanet's own ramps, so the worst is
                // measured where the authority's own moments move less than 150 s from both neighbours.
                val (fajrOwn, ishaOwn) = own(row.date)
                val steady = listOf(-1, 1).all { k ->
                    val (f, i) = own(row.date.plus(k, DateTimeUnit.DAY))
                    kotlin.math.abs(f - fajrOwn) < 150 && kotlin.math.abs(i - ishaOwn) < 150
                }
                if (steady) worst = max(worst, late)
                checked++
            }
        }
        assertEquals(8 * 365 + 2, checked)
        // Lateness stays a cost of about a minute: the takdir's rounding bound, the plain days' own
        // rounding, and the envelope's step where the takdir moves.
        assertTrue(lateSeconds / checked < 120, "mean lateness ${lateSeconds / checked} s")
        assertTrue(worst <= 4 * 60, "worst lateness on steady days $worst s")
    }

    /**
     * Outside the takdir a slot is the plain method's own moment, so the curves show its minute, or
     * the minute on the safe side of it where rounding the depression to a hundredth of a degree and
     * the envelope over the neighbouring days move the moment by a few seconds.
     */
    @Test
    fun `on plain days the curves show the plain method's own minute or the one on its safe side`() {
        var same = 0
        var d = LocalDate(2027, 10, 1)
        while (d < LocalDate(2028, 4, 1)) {
            val plain = DayComputer.compute(method, point, d, zone, AsrSchool.STANDARD, Registry.ramadanCalendar())
            val curve = shown(d)
            assertTrue(curve.fajr >= plain.fajr && curve.fajr <= plain.fajr + 1.minutes, "$d Fajr ${curve.fajr} vs ${plain.fajr}")
            assertTrue(curve.isha >= plain.isha && curve.isha <= plain.isha + 1.minutes, "$d Isha ${curve.isha} vs ${plain.isha}")
            assertTrue(
                curve.endOfEating <= plain.endOfEating && curve.endOfEating >= plain.endOfEating - 1.minutes,
                "$d end ${curve.endOfEating} vs ${plain.endOfEating}",
            )
            if (curve.fajr == plain.fajr && curve.isha == plain.isha && curve.endOfEating == plain.endOfEating) same++
            d = d.plus(1, DateTimeUnit.DAY)
        }
        assertTrue(same > 120, "only $same of 183 winter days show the plain method's minutes exactly")
    }

    @Test
    fun `a slot the derivation year does not print takes the safe side of its neighbours`() {
        // 2027 has no 29 February: its slot (59) is filled from 28 February and 1 March.
        assertEquals(min(curves.fajr[58], curves.fajr[60]), curves.fajr[59], 0.011)
        assertEquals(max(curves.isha[58], curves.isha[60]), curves.isha[59], 0.011)
        assertEquals(max(curves.end[58], curves.end[60]), curves.end[59], 0.011)
    }

    @Test
    fun `the rendered file decodes to the same curves`() {
        val city = DiyanetEuropeCurveGenerator.City("test", "Test", point, zone.id, emptyList())
        val text = DiyanetEuropeCurveGenerator.render(listOf(city), listOf(curves), listOf(365), LocalDate(2027, 1, 1), LocalDate(2027, 12, 31))
        val encoded = Regex("\"test\" to Curves\\(\\n((?:.*\\n){3,60}?)            \\),").find(text)!!.groupValues[1]
        val arrays = encoded.split("\",\n").filter { it.isNotBlank() }.map { block ->
            Regex("\\d+").findAll(block).map { it.value.toInt() / 100.0 }.toList()
        }
        assertEquals(3, arrays.size)
        for ((array, expected) in arrays.zip(listOf(curves.fajr, curves.isha, curves.end))) {
            assertEquals(366, array.size)
            for (i in 0 until 366) assertEquals(expected[i], array[i], 0.0001, "slot $i")
        }
        assertTrue(ceil(curves.isha[0] * 100) / 100 == curves.isha[0], "a slot is a hundredth of a degree")
    }
}
