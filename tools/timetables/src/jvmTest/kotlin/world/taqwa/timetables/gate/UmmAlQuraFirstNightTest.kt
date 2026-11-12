package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.data.UmmAlQuraDates
import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first night of Ramadan under Umm al-Qura (engine survey of 11 Nov 2026, "what the survey missed"): the first
 * tarawih is prayed on the evening before 1 Ramadan, yet the engine applies Isha's Ramadan minutes (Maghrib + 120) by
 * civil date, on Umm al-Qura's own Ramadan dates with no day's tolerance ([Registry.ramadanCalendarFor]), so on that
 * evening Isha is Maghrib + 90. That is what Umm al-Qura prints: on every held table (`sa-ummalqura*.tsv`'s files, each
 * read once), Isha is Maghrib + 120 on exactly the dates the API labels Ramadan and + 90 on every other date, the eve of
 * 1 Ramadan and 1 Shawwal included. No change to the engine; this holds the evidence. Without the archive (CI) the
 * table half reads nothing and passes. Failures name dates and gaps in minutes only (R69).
 */
class UmmAlQuraFirstNightTest {

    @Test
    fun `Umm al-Qura's tables print Isha at Maghrib + 120 on its Ramadan dates alone, the eve of the first fast at + 90`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        if (!roots.held) return
        val rows = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")).rows
            .filter { it.entry.substringBefore('/') == UQ && it.member == null }
            .distinctBy { it.path }
        val gaps = sortedMapOf<String, Int>()
        val wrong = mutableListOf<String>()
        var eves = 0
        for (row in rows) {
            val maghrib = row.columns.indexOfFirst { Event.MAGHRIB in it }
            val isha = row.columns.indexOfFirst { Event.ISHA in it }
            if (maghrib < 0 || isha < 0) continue
            val table = OfficialTable.read(roots.file(row.path), row.format, row.columns.size)
            for (day in table.days) {
                val m = day.minutes[maghrib] ?: continue
                val i = day.minutes[isha] ?: continue
                val ramadan = day.date in UmmAlQuraDates.ramadanDates
                val gap = i - m
                gaps.merge("${if (ramadan) "Ramadan" else "other"} +$gap", 1, Int::plus)
                if (gap != if (ramadan) 120 else 90) wrong += "${row.path.substringAfterLast('/')} ${day.date}: +$gap"
                if (!ramadan && day.date.plus(1, DateTimeUnit.DAY) in UmmAlQuraDates.ramadanDates) eves++
            }
        }
        println("Umm al-Qura's printed Isha − Maghrib over ${rows.size} held tables: $gaps; eves of 1 Ramadan read: $eves")
        assertTrue(wrong.isEmpty(), "Isha − Maghrib off the rule: ${wrong.take(20)}")
        assertTrue(eves > 0, "no eve of 1 Ramadan held")
    }

    @Test
    fun `the engine's Ramadan for Umm al-Qura begins on 1 Ramadan, not its eve, and its Isha follows`() {
        val entry = Registry.byId(UQ)!!
        val calendar = Registry.ramadanCalendarFor(entry)
        val makkah = Place(21.4225, 39.8262, "Asia/Riyadh", "SA")
        val zone = TimeZone.of(makkah.zoneId)
        val firsts = UmmAlQuraDates.ramadanDates.filter { it.minus(1, DateTimeUnit.DAY) !in UmmAlQuraDates.ramadanDates }.sorted()
        for (first in firsts.filter { it.year in 2026..2029 }) {
            val eve = first.minus(1, DateTimeUnit.DAY)
            assertFalse(calendar.isRamadan(eve), "$eve")
            assertTrue(calendar.isRamadan(first), "$first")
            val gap = { date: LocalDate ->
                DayPipeline.day(entry, makkah, date).let { (it.isha - it.maghrib).inWholeMinutes }
            }
            println("Makkah: eve $eve Isha − Maghrib ${gap(eve)} min; 1 Ramadan $first ${gap(first)} min")
            assertEquals(90, gap(eve), "the eve $eve")
            assertEquals(120, gap(first), "1 Ramadan $first")
        }
    }

    private companion object {
        const val UQ = "sa.ummalqura"
    }
}
