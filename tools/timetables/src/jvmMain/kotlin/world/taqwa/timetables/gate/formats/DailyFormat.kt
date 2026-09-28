package world.taqwa.timetables.gate.formats

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.gate.OfficialDay
import world.taqwa.timetables.gate.OfficialFormat

/**
 * The layout of `official/README.md`, which almost every held table follows (MUIS and DUM RT's
 * open sets among them): one line per day, `yyyy-mm-dd` then the time columns as 24-hour `HH:MM`
 * (a `-` where the table has none), separated by blanks. `#` lines and blank lines are comments;
 * anything after the expected columns (a source tag like `# file1`, `p2r1` or `tz=3`) is ignored.
 * A line whose date or any expected column does not read is counted as unreadable, never guessed.
 */
object DailyFormat : OfficialFormat {
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")

    override fun read(lines: List<String>, columns: Int): OfficialFormat.Read {
        val days = mutableListOf<OfficialDay>()
        var unreadable = 0
        for (line in lines) {
            val text = line.trim()
            if (text.isEmpty() || text.startsWith("#")) continue
            val day = day(text.split(Regex("\\s+")), columns)
            if (day == null) unreadable++ else days += day
        }
        return OfficialFormat.Read(days, unreadable)
    }

    private fun day(tokens: List<String>, columns: Int): OfficialDay? {
        if (tokens.size < columns + 1 || !DATE.matches(tokens[0])) return null
        val date = runCatching { LocalDate.parse(tokens[0]) }.getOrNull() ?: return null
        val minutes = ArrayList<Int?>(columns)
        for (token in tokens.subList(1, columns + 1)) {
            if (token == "-") {
                minutes += null
                continue
            }
            val match = TIME.matchEntire(token) ?: return null
            val (h, m) = match.destructured
            val hours = h.toInt()
            val mins = m.toInt()
            if (hours > 30 || mins > 59) return null
            minutes += hours * 60 + mins
        }
        return OfficialDay(date, minutes)
    }
}
