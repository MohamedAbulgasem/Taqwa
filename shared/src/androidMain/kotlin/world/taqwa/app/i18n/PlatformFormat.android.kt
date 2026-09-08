package world.taqwa.app.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.text.NumberFormat
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

private class AndroidPlatformFormat : PlatformFormat {
    private val locale: Locale = Locale.getDefault()

    // NumberFormat is not thread-safe, but every call here is on the UI thread and one instance
    // avoids re-resolving the locale's numbering system on every tick of the countdown.
    private val plainFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    private val twoDigitFormat = NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = 2
        isGroupingUsed = false
    }

    // CLDR's LONG date is "full month, day, year, no weekday" in the locale's own field order
    // ("6 September 2026" for en-GB, "September 6, 2026" for en-US). Pure java.time rather than
    // android.text.format.DateFormat, which is a stub under JVM unit tests and would throw from
    // this constructor in every test that reaches createPlatformFormat(). A few locales (en-ZA,
    // for one) pad the day in their long format — "06 September" — which is a sibling of the
    // Hijri "23" here, so the pad is dropped; the skeleton form CLDR offers for this exact
    // combination is unpadded in those locales anyway. The DecimalStyle makes the digits follow
    // the same CLDR numbering rule as `clockTime`; DateTimeFormatter defaults to ASCII otherwise.
    private val longDateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern(
            DateTimeFormatterBuilder
                .getLocalizedDateTimePattern(FormatStyle.LONG, null, IsoChronology.INSTANCE, locale)
                .replace("dd", "d"),
            locale,
        )
        .withDecimalStyle(DecimalStyle.of(locale))

    override fun languageTag(): String = locale.toLanguageTag()

    override fun longDate(date: LocalDate): String = longDateFormatter.format(date.toJavaLocalDate())

    override fun localizedDigits(number: Int): String = plainFormat.format(number)

    override fun clockTime(hour: Int, minute: Int): String =
        "${plainFormat.format(hour)}:${twoDigitFormat.format(minute)}"

    // CLDR returns these lowercase in several locales (French "anglais", Indonesian "inggris"),
    // and this name is a list label, so the first character is titlecased in the display locale's
    // own rules rather than with uppercase(), which mis-cases Turkish "i". An unresolvable code
    // comes back as the code itself, which the English map turns into a real name where it can.
    override fun languageName(code: String): String {
        val name = Locale.forLanguageTag(code).getDisplayLanguage(locale)
        if (name.isBlank() || name.equals(code, ignoreCase = true)) {
            return EnglishPlatformFormat.languageName(code)
        }
        return name.replaceFirstChar { it.titlecase(locale) }
    }
}

actual fun createPlatformFormat(): PlatformFormat = AndroidPlatformFormat()
