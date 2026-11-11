package world.taqwa.app.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/**
 * `internal` with a locale parameter so the host test can build it for a given locale; the app
 * only ever reaches it through [createPlatformFormat], with the device's default.
 */
internal class AndroidPlatformFormat(
    private val locale: Locale = Locale.getDefault(),
    private val clockFormat: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR,
) : PlatformFormat {

    // NumberFormat is not thread-safe, so no instance of this class is shared across threads. The
    // app's screens call theirs on the UI thread alone, and one instance avoids re-resolving the
    // locale's numbering system on every tick of the countdown. The watch feed builds a fresh
    // createPlatformFormat() for each ask (feedFor, plan 2 Task 4) and calls it, clockFigures
    // included, only on the one worker that serves that ask.
    private val plainFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    private val twoDigitFormat = NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = 2
        isGroupingUsed = false
    }

    // CLDR's abbreviated day periods for this locale — "AM"/"PM" in American English, "am"/"pm"
    // in British, "ص"/"م" in Arabic, "ÖÖ"/"ÖS" in Turkish. Never our own strings (spec §1.2).
    private val amPm: Array<String> = DateFormatSymbols.getInstance(locale).amPmStrings
    private val markerFirst = TwelveHourClock.markerFirst(locale.toLanguageTag())

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

    override fun clockTime(hour: Int, minute: Int): String {
        if (clockFormat == ClockFormat.TWENTY_FOUR_HOUR) {
            return "${plainFormat.format(hour)}:${twoDigitFormat.format(minute)}"
        }
        val (h, pm) = twelveHourParts(hour)
        return TwelveHourClock.render(
            plainFormat.format(h), twoDigitFormat.format(minute), amPm[if (pm) 1 else 0], markerFirst,
        )
    }

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

    // Same lookup as languageName, without that function's titlecasing: mid-sentence ("Nécessite
    // une voix gratuite en anglais.") French and Indonesian keep CLDR's own lower case, and only the
    // list row above wants the capital.
    override fun languageNameInSentence(code: String): String {
        val name = Locale.forLanguageTag(code).getDisplayLanguage(locale)
        if (name.isBlank() || name.equals(code, ignoreCase = true)) {
            return EnglishPlatformFormat.languageName(code)
        }
        return name
    }

    // The watch feed's words (plan 2, Task 1). clockTime above and longDate stay the app's own, in
    // this format's locale; these are in the words locale, which follows the interface language.

    override fun clockFigures(hour: Int, minute: Int): String {
        val h = if (clockFormat == ClockFormat.TWELVE_HOUR) twelveHourParts(hour).first else hour
        return "${plainFormat.format(h)}:${twoDigitFormat.format(minute)}"
    }

    override fun dayPeriods(language: UiLanguage): Pair<String, String> {
        val markers = DateFormatSymbols.getInstance(wordsLocale(language)).amPmStrings
        return markers[0] to markers[1]
    }

    override fun shortWeekday(date: LocalDate, language: UiLanguage): String = words("EEE", date, language)

    override fun shortMonth(date: LocalDate, language: UiLanguage): String = words("MMM", date, language)

    override fun longWeekday(date: LocalDate, language: UiLanguage): String = words("EEEE", date, language)

    override fun longMonth(date: LocalDate, language: UiLanguage): String = words("MMMM", date, language)

    private fun wordsLocale(language: UiLanguage): Locale =
        Locale.forLanguageTag(wordsLocaleTag(languageTag(), language))

    // The format forms of CLDR's Gregorian names (`MMM`, not the stand-alone `LLL`), the forms
    // longDate's own pattern uses. A pattern of letters alone holds no digit, and the DecimalStyle
    // is there all the same, as for longDate, should a pattern ever hold one.
    private fun words(pattern: String, date: LocalDate, language: UiLanguage): String {
        val wordsLocale = wordsLocale(language)
        return DateTimeFormatter.ofPattern(pattern, wordsLocale)
            .withChronology(IsoChronology.INSTANCE)
            .withDecimalStyle(DecimalStyle.of(wordsLocale))
            .format(date.toJavaLocalDate())
    }
}

actual fun createPlatformFormat(clockFormat: ClockFormat): PlatformFormat = AndroidPlatformFormat(clockFormat = clockFormat)
