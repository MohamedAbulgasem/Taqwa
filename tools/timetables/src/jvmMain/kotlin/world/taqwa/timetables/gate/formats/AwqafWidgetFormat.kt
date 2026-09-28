package world.taqwa.timetables.gate.formats

import world.taqwa.timetables.gate.OfficialFormat

/**
 * Tripoli's table as the research assembled it from awqaf.gov.ly's home-page widget
 * (`round/data-maghreb-libya/off-libya-tripoli.txt`): the [DailyFormat] layout, one line per
 * Wayback Machine capture of the widget, dated by the capture, then api.ifta.ly's eight days.
 *
 * The widget shows "today" as the page was cached, so a capture can show an earlier day than its
 * own. A date-shift test puts all six printed times of each capture against the model the eight
 * API days fit (18.5° dawn, sunrise, transit, Asr, sunset, 18.3° dusk, each with the API days'
 * mean offset) at its own date and one and two days before. Four captures fit an earlier day far
 * better than their own (root-mean-square residual over the six times, own date → best):
 *
 * | capture    | taken (Libyan time) | best day | own → best |
 * |------------|---------------------|----------|------------|
 * | 2025-05-19 | 03:34               | −2       | 95 → 33 s  |
 * | 2026-02-02 | 05:25               | −1       | 64 → 28 s  |
 * | 2026-05-21 | 03:25               | −2       | 79 → 30 s  |
 * | 2026-09-03 | 08:44               | −1       | 65 → 26 s  |
 *
 * A shift takes each of these to 0.34–0.44 of its own-date residual; no other capture's best shift
 * goes below 0.66 (16 June 2026, 50 → 33 s, near the solstice, where a day moves the times least).
 * These four lines are skipped and counted as unreadable: checked at their capture date they would
 * measure the engine against another day (Maghrib and Isha a minute "early" on 3 September 2026,
 * Fajr on the two May captures), and re-dating them would be a guess.
 * Only this file is read with this format.
 */
object AwqafWidgetFormat : OfficialFormat {
    /** The captures that show an earlier day than their own (see above). */
    val STALE = setOf("2025-05-19", "2026-02-02", "2026-05-21", "2026-09-03")

    override fun read(lines: List<String>, columns: Int): OfficialFormat.Read {
        val kept = lines.filterNot { line -> line.trim().substringBefore(' ') in STALE }
        val read = DailyFormat.read(kept, columns)
        return OfficialFormat.Read(read.days, read.unreadable + (lines.size - kept.size))
    }
}
