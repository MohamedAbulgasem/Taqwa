package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import world.taqwa.app.prayer.engine.day.DayComputer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.authorities.Muis
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Task 7c: a few of MUIS's own printed minutes (data.gov.sg, Singapore Open Data Licence v1.0;
 * `tools/timetables/official/open/SG-MUIS/off-muis-singapore-2026.txt`), so this commonTest can
 * commit them (openly licensed, spec §5's exception to "never commit a printed time"). Never early
 * on the starts, never a late end on the combined F+E column, and within sg.muis's own recorded
 * exceptions (Task 7c: 2 min for every event but Asr, 3 for Asr — see `Muis.kt` and
 * `docs/research/2026-09-prayer-times/proof/7c-sea.md`).
 */
class SgMuisProofTest {
    private val zone = TimeZone.of("Asia/Singapore")

    private class Row(val date: LocalDate, val fajrEnd: String, val sunrise: String, val dhuhr: String, val asr: String, val maghrib: String, val isha: String)

    // Four seasons plus a Ramadan day, read straight off the open 2026 table.
    private val rows = listOf(
        Row(LocalDate(2026, 1, 1), "05:44", "07:08", "13:10", "16:34", "19:11", "20:25"),
        Row(LocalDate(2026, 3, 2), "05:57", "07:14", "13:18", "16:30", "19:21", "20:29"), // Ramadan 1447
        Row(LocalDate(2026, 4, 1), "05:48", "07:05", "13:10", "16:16", "19:13", "20:22"),
        Row(LocalDate(2026, 7, 1), "05:40", "07:04", "13:10", "16:35", "19:15", "20:30"),
        Row(LocalDate(2026, 10, 1), "05:35", "06:52", "12:56", "16:03", "18:58", "20:06"),
    )

    private fun at(date: LocalDate, hhmm: String): Instant {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return date.atTime(h, m).toInstant(zone)
    }

    @Test
    fun `sg muis 2026 official minutes are never early and stay within the recorded exceptions`() {
        val ramadan = Registry.ramadanCalendarFor(Muis.entry)
        for (row in rows) {
            val day = DayComputer.compute(Muis.method, Muis.point, row.date, zone, AsrSchool.STANDARD, ramadan)
            val fajrEnd = at(row.date, row.fajrEnd)
            val sunrise = at(row.date, row.sunrise)
            val dhuhr = at(row.date, row.dhuhr)
            val asr = at(row.date, row.asr)
            val maghrib = at(row.date, row.maghrib)
            val isha = at(row.date, row.isha)

            assertTrue(day.fajr >= fajrEnd, "${row.date} fajr ${day.fajr} < $fajrEnd")
            assertTrue(day.endOfEating <= fajrEnd, "${row.date} endOfEating ${day.endOfEating} > $fajrEnd")
            assertTrue(day.sunrise <= sunrise, "${row.date} sunrise ${day.sunrise} > $sunrise")
            assertTrue(day.dhuhr >= dhuhr, "${row.date} dhuhr ${day.dhuhr} < $dhuhr")
            assertTrue(day.asr >= asr, "${row.date} asr ${day.asr} < $asr")
            assertTrue(day.maghrib >= maghrib, "${row.date} maghrib ${day.maghrib} < $maghrib")
            assertTrue(day.isha >= isha, "${row.date} isha ${day.isha} < $isha")

            // Task 7c's recorded exceptions: 2 min for every event but Asr, 3 for Asr (R41).
            assertTrue((day.fajr - fajrEnd).inWholeMinutes <= 2, "${row.date} fajr late")
            assertTrue((sunrise - day.sunrise).inWholeMinutes <= 2, "${row.date} sunrise early")
            assertTrue((day.dhuhr - dhuhr).inWholeMinutes <= 2, "${row.date} dhuhr late")
            assertTrue((day.asr - asr).inWholeMinutes <= 3, "${row.date} asr late")
            assertTrue((day.maghrib - maghrib).inWholeMinutes <= 2, "${row.date} maghrib late")
            assertTrue((day.isha - isha).inWholeMinutes <= 2, "${row.date} isha late")
        }
    }
}
