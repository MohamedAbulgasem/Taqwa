package world.taqwa.timetables.gate.formats

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.gate.OfficialDay
import world.taqwa.timetables.gate.OfficialFormat
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Wayback captures in Morocco's Habous tables as the research assembled them
 * (`round/data-maghreb-libya/off-habous-*.txt`): the [DailyFormat] layout, read into UTC, so a row
 * using it names `clock` UTC. The same files' live month is read by [HabousLiveFormat].
 *
 * Each file holds the live month from habous.gov.ma, printed in UTC+0 (its first comment line gives
 * its dates: "(2026-09-13..2026-10-12, current Hijri month, times in UTC+0)"), and captures of
 * earlier month pages printed in Morocco's legal time. A page is one Hijri month in one clock, which
 * need not be the clock tzdata gives each of its days: the Sha'ban 1441 page keeps UTC+1 after
 * tzdata's switch of 19 April 2020 and the Shawwal 1441 page UTC+0 after tzdata's return of 31 May,
 * the Sha'ban 1445 page UTC+1 on 10–11 March 2024 (every line's clock checked against its printed
 * Dhuhr, transit + 5 min at the city). So a run of consecutive dates starts in tzdata's
 * `Africa/Casablanca` offset at local noon and keeps its clock until its printed Dhuhr itself jumps
 * by half an hour or more from the day before, by the whole hours of that jump.
 *
 * Three captures were misdated when the research read the Gregorian month off a page that spans two:
 * their lines' Dhuhr sits minutes from the day's transit + 5 (another day's time). They are
 * skipped and counted as unreadable:
 *
 * - Laayoune (page 156): 2020-10-19..2020-10-30, from the capture of 19 Sep 2020 of the Safar 1442
 *   page, whose September days were dated October: at 19 October its Dhuhr jumps by about 9 min
 *   (7–10 min off the day's transit + 5).
 * - The site's default page (Rabat): 2020-01-28..2020-01-31 (Dhuhr about 11 min off, December's
 *   days) and 2021-10-01..2021-10-06 (4–6 min off, November's days).
 */
object HabousArchiveFormat : OfficialFormat {
    override fun read(lines: List<String>, columns: Int): OfficialFormat.Read {
        val file = HabousFile.of(lines)
        val read = DailyFormat.read(lines, columns)
        val days = mutableListOf<OfficialDay>()
        var skipped = 0
        var previous: OfficialDay? = null
        var offsetHours = 0
        for (day in read.days) {
            if (file.isLive(day.date)) {
                previous = null
                continue
            }
            if (file.misdated.any { day.date in it }) {
                skipped++
                previous = null
                continue
            }
            val before = previous
            val dhuhr = day.minutes.getOrNull(DHUHR)
            val beforeDhuhr = before?.minutes?.getOrNull(DHUHR)
            offsetHours = when {
                before == null || before.date.toEpochDays() + 1 != day.date.toEpochDays() -> legalHours(day.date)
                dhuhr != null && beforeDhuhr != null && abs(dhuhr - beforeDhuhr) >= 30 ->
                    offsetHours + ((dhuhr - beforeDhuhr) / 60.0).roundToInt()
                else -> offsetHours
            }
            previous = day
            days += OfficialDay(day.date, day.minutes.map { it?.minus(offsetHours * 60) })
        }
        return OfficialFormat.Read(days, read.unreadable + skipped)
    }

    /** Lines misdated by the research's reading of two-month pages, by page ("default" for the site's own). */
    val MISDATED: Map<String, List<ClosedRange<LocalDate>>> = mapOf(
        "156" to listOf(LocalDate(2020, 10, 19)..LocalDate(2020, 10, 30)),
        "default" to listOf(LocalDate(2020, 1, 28)..LocalDate(2020, 1, 31), LocalDate(2021, 10, 1)..LocalDate(2021, 10, 6)),
    )

    private const val DHUHR = 2
    private val LEGAL = ZoneId.of("Africa/Casablanca")

    /** Africa/Casablanca's offset at local noon on [date], in whole hours. */
    private fun legalHours(date: LocalDate): Int =
        LEGAL.rules.getOffset(LocalDateTime.of(date.year, date.month.ordinal + 1, date.day, 12, 0)).totalSeconds / 3600
}

/** What a Habous file's first comment line says: its live month's dates and its page. */
internal class HabousFile(private val live: ClosedRange<LocalDate>?, page: String) {
    val misdated: List<ClosedRange<LocalDate>> = HabousArchiveFormat.MISDATED[page].orEmpty()

    fun isLive(date: LocalDate): Boolean = live != null && date in live

    companion object {
        private val LIVE = Regex("""\((\d{4}-\d{2}-\d{2})\.\.(\d{4}-\d{2}-\d{2}), current Hijri month, times in UTC\+0\)""")
        private val PAGE = Regex("""ville=(\d+)""")

        fun of(lines: List<String>): HabousFile {
            val first = lines.firstOrNull { it.startsWith("#") }.orEmpty().substringBefore(" -- ")
            val live = LIVE.find(first)?.destructured?.let { (from, to) -> LocalDate.parse(from)..LocalDate.parse(to) }
            return HabousFile(live, PAGE.find(first)?.groupValues?.get(1) ?: "default")
        }
    }
}
