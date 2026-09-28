package world.taqwa.timetables.gate.formats

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.gate.OfficialFormat

/**
 * The [DailyFormat] layout, keeping only the days from 1 January 2025 on: for a file that holds
 * captures of an authority's older method version beside the current one. Egypt's ESA changed its
 * method between its 2 December 2024 and 8 January 2025 captures (profiles-tested.md,
 * data-gulf-egypt: the 2024 rows sit 20–40 s off at Fajr, sunrise, Maghrib and Isha), so the town
 * files that mix both are read from 2025 on. The days before are left out, not counted as unreadable.
 */
object DailyFrom2025Format : OfficialFormat {
    private val FROM = LocalDate(2025, 1, 1)

    override fun read(lines: List<String>, columns: Int): OfficialFormat.Read {
        val all = DailyFormat.read(lines, columns)
        return OfficialFormat.Read(all.days.filter { it.date >= FROM }, all.unreadable)
    }
}
