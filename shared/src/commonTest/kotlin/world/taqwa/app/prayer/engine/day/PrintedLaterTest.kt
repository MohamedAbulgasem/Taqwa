package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.PrintedLater
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.app.prayer.engine.registry.authorities.Jakim
import world.taqwa.app.prayer.engine.registry.data.JakimZones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Days an authority's own table prints after the days around them are held as printed ([PrintedLater]; ruling R228 as
 * the review round of 13 Nov 2026 left it): on those days each start is later by the seconds held, the sunrise, the end
 * of eating and the imsak stay where they are (an end is never moved later to meet a print), and no other day moves.
 * The figures here are invented: the rule, not a table.
 */
class PrintedLaterTest {
    private val zone = TimeZone.of("Asia/Kuching")
    private val unit = Units.of("my.jakim")!!.units.first { it.id == "SBH07" }
    private val plain: TimetableMethod = assertNotNull(unit.method).copy(printedLater = emptyList())
    private val first = LocalDate(2031, 5, 10)
    private val last = LocalDate(2031, 5, 12)
    private val later = EventOffsets(fajr = 600, sunrise = 0, dhuhr = 300, asr = 900, maghrib = 120, isha = 60)
    private val held = plain.copy(printedLater = listOf(PrintedLater(first, last, later)))

    private fun day(method: TimetableMethod, date: LocalDate): PrayerDay =
        DayComputer.compute(method, unit.point, date, zone, AsrSchool.STANDARD, Registry.ramadanCalendarFor(Jakim.entry), Registry.lagDates)

    @Test
    fun `on a held day each start is later by its seconds and no end moves`() {
        var date = first
        while (date <= last) {
            val a = day(plain, date)
            val b = day(held, date)
            assertEquals(a.fajr + 600.seconds, b.fajr, "$date fajr")
            assertEquals(a.dhuhr + 300.seconds, b.dhuhr, "$date dhuhr")
            assertEquals(a.asr + 900.seconds, b.asr, "$date asr")
            assertEquals(a.maghrib + 120.seconds, b.maghrib, "$date maghrib")
            assertEquals(a.isha + 60.seconds, b.isha, "$date isha")
            assertEquals(a.sunrise, b.sunrise, "$date sunrise")
            assertEquals(a.endOfEating, b.endOfEating, "$date end of eating")
            assertEquals(assertNotNull(a.imsak), b.imsak, "$date imsak")
            date = date.plus(DatePeriod(days = 1))
        }
    }

    @Test
    fun `the days either side of a held run are the plain days`() {
        for (date in listOf(first.minus(DatePeriod(days = 1)), last.plus(DatePeriod(days = 1)), LocalDate(2032, 5, 11))) {
            assertEquals(day(plain, date), day(held, date), "$date")
        }
    }

    @Test
    fun `a hold is only ever later and never on the sunrise`() {
        assertFailsWith<IllegalArgumentException> { PrintedLater(first, last, EventOffsets(sunrise = 60)) }
        assertFailsWith<IllegalArgumentException> { PrintedLater(first, last, EventOffsets(asr = -60)) }
        assertFailsWith<IllegalArgumentException> { PrintedLater(last, first, later) }
        // Only where the margins alone set the starts: not beside a fixed table year.
        assertFailsWith<IllegalArgumentException> { held.copy(fixedTableYear = 2026) }
    }

    @Test
    fun `every held run of a zone reaches the zone's unit and names whole minutes`() {
        val units = Units.of("my.jakim")!!.units.associateBy { it.id }
        for (z in JakimZones.zones) {
            assertEquals(z.printedLater, assertNotNull(units.getValue(z.id).method).printedLater, z.id)
            for (run in z.printedLater) {
                val s = run.starts
                assertTrue(listOf(s.fajr, s.dhuhr, s.asr, s.maghrib, s.isha).all { it % 60 == 0 }, "${z.id}: a print is in whole minutes")
            }
        }
    }
}
