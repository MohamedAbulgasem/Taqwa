package world.taqwa.app.adhkar

import kotlinx.datetime.LocalDate

/**
 * How far one set has been counted on one date (spec §3.3): the count of each page, in page order.
 * It is counted, never visited — a page is done when its count reaches its target, not when it was
 * swiped past — and only today's is kept: a new date overwrites, there is no history.
 *
 * Stored as one string, `"<date>|c1,c2,…"`, by [AdhkarStore]. The daily istighfar is the same shape
 * with one page (`"<date>|n"`), read with `pages = 1`.
 */
data class AdhkarProgress(val date: LocalDate, val counts: List<Int>) {
    fun encode(): String = "$date$DATE_SEPARATOR${counts.joinToString(COUNT_SEPARATOR.toString())}"

    companion object {
        private const val DATE_SEPARATOR = '|'
        private const val COUNT_SEPARATOR = ','

        /**
         * The progress [raw] holds, or null if it is absent, does not read, or was saved for a set
         * of a different length than [pages] — a set whose items changed under an update is a
         * different set, and counts kept for the old pages would land on the wrong ones.
         */
        fun decode(raw: String?, pages: Int): AdhkarProgress? {
            if (raw == null) return null
            val parts = raw.split(DATE_SEPARATOR)
            if (parts.size != 2) return null
            val date = try {
                LocalDate.parse(parts[0])
            } catch (notADate: IllegalArgumentException) {
                return null
            }
            val counts = if (parts[1].isEmpty()) {
                emptyList()
            } else {
                parts[1].split(COUNT_SEPARATOR).map { it.toIntOrNull()?.takeIf { count -> count >= 0 } ?: return null }
            }
            return if (counts.size == pages) AdhkarProgress(date, counts) else null
        }

        /**
         * What to show for [date]: the stored counts if they are for [date] and for these [pages],
         * otherwise a fresh set of zeros. This is the one place a stale day is discarded — the
         * store hands back whatever it holds, and yesterday's counts are simply not today's.
         */
        fun forDate(raw: String?, date: LocalDate, pages: Int): AdhkarProgress =
            decode(raw, pages)?.takeIf { it.date == date } ?: AdhkarProgress(date, List(pages) { 0 })
    }
}
