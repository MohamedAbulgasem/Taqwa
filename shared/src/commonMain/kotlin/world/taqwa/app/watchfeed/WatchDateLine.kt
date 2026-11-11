package world.taqwa.app.watchfeed

import kotlinx.datetime.LocalDate
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.uppercaseIn

/**
 * A day's Gregorian date in the watch feed's forms (`WatchDay.gregorian`, `weekday`, `dayNumber`
 * and `gregorianSpoken`): the face's short line, `SUN 18 OCT`, and the screen reader's date in
 * full words, `Sunday 18 October`.
 *
 * The words are CLDR's in the words locale ([PlatformFormat.shortWeekday] and the rest), and the
 * day is the phone's own digits. Both lines are in logical order, the weekday, the day and the
 * month, in every language, as the samples are; a right-to-left line is laid out by the watch.
 */
internal data class WatchDateLine(
    /** `"$weekday $dayNumber $month"`: `SUN 18 OCT`, `DIM 18 OCT`, `الأحد ١٨ أكتوبر`. */
    val gregorian: String,
    /** The abbreviated weekday without its trailing dot, in the interface language's capitals. */
    val weekday: String,
    /** The day of the month in the phone's digits. */
    val dayNumber: String,
    /** The weekday and the month in full, with no dot taken off and no change of case. */
    val spoken: String,
) {
    companion object {
        fun of(date: LocalDate, format: PlatformFormat, language: UiLanguage): WatchDateLine {
            val weekday = format.shortWeekday(date, language).removeSuffix(".").uppercaseIn(language)
            val month = format.shortMonth(date, language).removeSuffix(".").uppercaseIn(language)
            val dayNumber = format.localizedDigits(date.day)
            return WatchDateLine(
                gregorian = "$weekday $dayNumber $month",
                weekday = weekday,
                dayNumber = dayNumber,
                spoken = "${format.longWeekday(date, language)} $dayNumber ${format.longMonth(date, language)}",
            )
        }
    }
}
