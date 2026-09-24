package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import world.taqwa.app.hijri.HijriMonthNames
import world.taqwa.app.i18n.UiLanguage
import java.text.NumberFormat
import java.time.YearMonth
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Every number and date on a city page, formatted the way the app's `AndroidPlatformFormat`
 * formats them for a reader whose phone is set to [language] in [country]: CLDR digits, a 24-hour
 * clock with the hour unpadded and the minute padded, and CLDR's long date with the day unpadded.
 * That is why a Libyan Arabic page reads 5:35 and an Egyptian one ٥:٣٥, as the app does.
 *
 * English is the one exception to "the country's own locale": where CLDR has no English for the
 * country (en-LY does not exist, and Java would quietly fall back to American), the page uses
 * British English, as the rest of the site and the app's `EnglishPlatformFormat` do.
 */
class Formats(val language: String, country: String) {

    val locale: Locale = localeFor(language, country)

    private val plain = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    private val twoDigits = NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = 2
        isGroupingUsed = false
    }
    private val grouped = NumberFormat.getIntegerInstance(locale)
    private val decimalStyle = DecimalStyle.of(locale)

    private val longDateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern(
            DateTimeFormatterBuilder
                .getLocalizedDateTimePattern(FormatStyle.LONG, null, IsoChronology.INSTANCE, locale)
                .replace("dd", "d"),
            locale,
        )
        .withDecimalStyle(decimalStyle)
    private val monthYearFormatter = DateTimeFormatter.ofLocalizedPattern("yMMMM")
        .withLocale(locale)
        .withDecimalStyle(decimalStyle)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", locale)
    private val weekdayShortFormatter = DateTimeFormatter.ofPattern("EEE", locale)

    private val uiLanguage = UiLanguage.of(language)

    fun digits(number: Int): String = plain.format(number)

    fun clock(hour: Int, minute: Int): String = "${plain.format(hour)}:${twoDigits.format(minute)}"

    fun longDate(date: LocalDate): String = longDateFormatter.format(date.toJavaLocalDate())

    fun monthYear(year: Int, month: Int): String = monthYearFormatter.format(YearMonth.of(year, month))

    fun weekday(date: LocalDate): String = weekdayFormatter.format(date.toJavaLocalDate())

    fun weekdayShort(date: LocalDate): String = weekdayShortFormatter.format(date.toJavaLocalDate())

    fun distance(km: Int): String = grouped.format(km)

    fun degrees(value: Int): String = "${plain.format(value)}°"

    fun countryName(code: String): String = Locale.of("", code).getDisplayCountry(locale)

    /** The day, the app's own month name, the year: "12 Rabi’ al-Thani 1448". */
    fun hijri(year: Int, month: Int, day: Int): String =
        "${plain.format(day)} ${HijriMonthNames.of(month, uiLanguage)} ${plain.format(year)}"

    fun hijriDayMonth(month: Int, day: Int): String = "${plain.format(day)} ${HijriMonthNames.of(month, uiLanguage)}"

    fun hijriMonth(month: Int): String = HijriMonthNames.of(month, uiLanguage)

    /** "UTC+2", "UTC+5:30", "UTC−4" (a true minus sign), or just "UTC", in the page's digits. */
    fun utcOffset(totalSeconds: Int): String {
        if (totalSeconds == 0) return "UTC"
        val sign = if (totalSeconds > 0) "+" else "−"
        val hours = abs(totalSeconds) / 3600
        val minutes = abs(totalSeconds) % 3600 / 60
        val tail = if (minutes == 0) "" else ":${twoDigits.format(minutes)}"
        return "UTC$sign${plain.format(hours)}$tail"
    }

    private companion object {
        val englishWithCountry: Set<Locale> =
            Locale.getAvailableLocales().filter { it.language == "en" && it.country.isNotEmpty() }.toSet()

        fun localeFor(language: String, country: String): Locale {
            val candidate = Locale.Builder().setLanguage(language).setRegion(country).build()
            if (language == "en" && candidate !in englishWithCountry) return Locale.UK
            return candidate
        }
    }
}
