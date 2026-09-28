package world.taqwa.timetables.gate.formats

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.gate.OfficialDay
import world.taqwa.timetables.gate.OfficialFormat

/**
 * Comma-separated tables with a header, as the research saved the Balkan calendars
 * (`date,fajr,sunrise,…` then a made-up `2000-01-01,1:23,4:56,…`): the first cell a `yyyy-mm-dd` date, then
 * the time columns as 24-hour `H:MM` or `HH:MM` (a `-` or an empty cell where the table has none).
 * The header line (its first cell `date`), `#` lines and blank lines are skipped; cells after the
 * expected columns are ignored. A line whose date or any expected column does not read is counted
 * as unreadable, never guessed.
 */
object CsvFormat : OfficialFormat {
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")

    override fun read(lines: List<String>, columns: Int): OfficialFormat.Read {
        val days = mutableListOf<OfficialDay>()
        var unreadable = 0
        for (line in lines) {
            val text = line.trim()
            if (text.isEmpty() || text.startsWith("#")) continue
            val cells = text.split(',').map { it.trim() }
            if (cells[0].equals("date", ignoreCase = true)) continue
            val day = day(cells, columns)
            if (day == null) unreadable++ else days += day
        }
        return OfficialFormat.Read(days, unreadable)
    }

    private fun day(cells: List<String>, columns: Int): OfficialDay? {
        if (cells.size < columns + 1 || !DATE.matches(cells[0])) return null
        val date = runCatching { LocalDate.parse(cells[0]) }.getOrNull() ?: return null
        val minutes = ArrayList<Int?>(columns)
        for (cell in cells.subList(1, columns + 1)) {
            if (cell == "-" || cell.isEmpty()) {
                minutes += null
                continue
            }
            val match = TIME.matchEntire(cell) ?: return null
            val (h, m) = match.destructured
            val hours = h.toInt()
            val mins = m.toInt()
            if (hours > 30 || mins > 59) return null
            minutes += hours * 60 + mins
        }
        return OfficialDay(date, minutes)
    }
}
