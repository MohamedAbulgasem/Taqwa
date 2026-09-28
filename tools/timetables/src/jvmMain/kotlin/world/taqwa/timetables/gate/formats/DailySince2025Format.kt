package world.taqwa.timetables.gate.formats

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.gate.OfficialFormat

/**
 * The [DailyFormat] layout for a table that holds rows of an authority's earlier rule beside its
 * current one: DUM RF's Moscow captures, whose 2024 rows follow its old summer rule (Fajr and Isha a
 * fixed 1 h 55 min from sunrise and Maghrib) and whose 2025–26 rows its current one (fractions of the
 * night). The rows dated before 2025 are left out and counted as unreadable, so the report shows
 * them ("lines: N unreadable (skipped)"); the rest are read as [DailyFormat] reads them.
 */
object DailySince2025Format : OfficialFormat {
    private val FROM = LocalDate(2025, 1, 1)

    override fun read(lines: List<String>, columns: Int): OfficialFormat.Read {
        val all = DailyFormat.read(lines, columns)
        val kept = all.days.filter { it.date >= FROM }
        return OfficialFormat.Read(kept, all.unreadable + (all.days.size - kept.size))
    }
}
