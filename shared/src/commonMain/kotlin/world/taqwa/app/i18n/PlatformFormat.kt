package world.taqwa.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

interface PlatformFormat {
    fun languageTag(): String

    /** Locale-correct digits for a plain non-negative integer, e.g. "42" or "٤٢". */
    fun localizedDigits(number: Int): String

    /**
     * A clock time using the platform's own locale formatting and digit set: 24-hour, or the
     * 12-hour form ("6:32 pm", marker from the platform's own symbols) when the formatter was
     * created with [ClockFormat.TWELVE_HOUR].
     *
     * The hour is *not* zero-padded and the minute always is — "3:42", "٣:٤٢", "15:07". That is
     * the shape the spec quotes for a Libyan user and the shape the Prayer screen already showed
     * before this task, so migrating to CLDR digits does not silently reformat every English
     * screen. (The brief's sketch padded both fields; padding the hour would have turned every
     * existing "3:42" into "03:42".)
     */
    fun clockTime(hour: Int, minute: Int): String

    /**
     * A Gregorian calendar date with the month spelled out and no weekday, in the locale's own
     * order and digits: "6 September 2026" in British English, "September 6, 2026" in American,
     * "٦ سبتمبر ٢٠٢٦" in Egyptian Arabic. It sits beside the Hijri date on the Prayer screen,
     * which is why it carries the full month name: the two dates read as a pair, and the Hijri
     * month is never abbreviated.
     *
     * Defaulted so the test fakes, which never show a date, need not implement it.
     */
    fun longDate(date: LocalDate): String = EnglishPlatformFormat.longDate(date)

    /**
     * The name of the language with ISO 639-1 code [code] ("ar", "ur"...), in the device's UI
     * language and capitalised the way that language capitalises names.
     *
     * The Quran's translation picker labels each row with the language it is in, and the reader is
     * choosing among languages they may not read — "Bengali" has to be legible to someone whose
     * phone is in English, so the platform's own CLDR name is the only correct source. Defaulted
     * to the English map for the fakes, as [longDate] is.
     */
    fun languageName(code: String): String = EnglishPlatformFormat.languageName(code)

    /**
     * The language's name as it reads inside a sentence: French and other languages that write it
     * in lower case keep it so, unlike [languageName], which is titlecased for a list row.
     * Defaulted to [languageName] itself, which already is sentence case everywhere but Android.
     */
    fun languageNameInSentence(code: String): String = languageName(code)

    // The watch feed's words (plan 2, Task 1). Each date word and day marker below is CLDR's in
    // the *words locale*: this format's own locale when the tag's own language is the interface
    // [language] (`isTagsOwnLanguage`), otherwise the bare interface language. The format's tag
    // only answers "can the phone's locale give these words?", never which language they are in,
    // so a Hebrew phone on the English interface gets English words, in a script the watch face
    // can draw. Each is defaulted to English so the test fakes compile.

    /**
     * The clock time without its day period: "4:10" in 12-hour and "16:10" in 24-hour, in this
     * format's digits. The watch draws the marker itself, from [dayPeriods], where its language
     * puts it. Defaulted to [clockTime], which is what a 24-hour format writes.
     */
    fun clockFigures(hour: Int, minute: Int): String = clockTime(hour, minute)

    /** The platform's own day-period markers, the ones [clockTime] uses, in the words locale: "AM" and "PM", ص and م. */
    fun dayPeriods(language: UiLanguage): Pair<String, String> = "AM" to "PM"

    /**
     * CLDR's abbreviated weekday, pattern `EEE`, Gregorian, in the words locale: "Sun", "dim.",
     * "الأحد" in the JVM's CLDR, or "أحد" in Foundation's, which drops the article.
     */
    fun shortWeekday(date: LocalDate, language: UiLanguage): String = ENGLISH_WEEKDAYS[date.dayOfWeek.isoDayNumber - 1].take(3)

    /** CLDR's abbreviated month, pattern `MMM`, Gregorian, in the words locale: "Oct", "oct.", "أكتوبر". */
    fun shortMonth(date: LocalDate, language: UiLanguage): String = GREGORIAN_MONTHS[date.month.number - 1].take(3)

    /** The weekday in full, pattern `EEEE`, for the screen reader's date: "Sunday", "dimanche". */
    fun longWeekday(date: LocalDate, language: UiLanguage): String = ENGLISH_WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]

    /** The month in full, pattern `MMMM`, for the screen reader's date: "October", "octobre". */
    fun longMonth(date: LocalDate, language: UiLanguage): String = GREGORIAN_MONTHS[date.month.number - 1]
}

/**
 * Whether [languageTag]'s own language is the interface [language]: the tag's primary subtag
 * (before the first `-` or `_`), lowercased, with Java's old spelling `in` read as `id`. Never
 * `UiLanguage.of(tag) == language`: that answers English for every language outside the seven, so
 * he-IL, hi-IN, fa-IR, mr-IN or zh-CN on the English interface would pass it, and their phones'
 * words, in scripts the watch face's fonts lack, would reach the feed.
 */
internal fun isTagsOwnLanguage(languageTag: String, language: UiLanguage): Boolean {
    val primary = languageTag.substringBefore('-').substringBefore('_').lowercase()
    return (if (primary == "in") "id" else primary) == language.code
}

/**
 * The words locale's tag: [languageTag] itself, written with `-`, when its own language is the
 * interface [language]; otherwise the interface language's bare code. It is the locale the date
 * words and the day markers come from, and the feed's `ui.language`, from which the watch takes
 * letter case alone: an az-AZ phone on the English interface sends "en", so the watch writes
 * "MAGHRIB", as the app does, and not Azerbaijani's "MAGHRİB".
 */
internal fun wordsLocaleTag(languageTag: String, language: UiLanguage): String =
    if (isTagsOwnLanguage(languageTag, language)) languageTag.replace('_', '-') else language.code

/**
 * The device's formatter, writing clock times in [clockFormat]. The default is declared here and
 * only here — Kotlin forbids a default on an `actual` — so every existing `createPlatformFormat()`
 * call keeps its 24-hour behaviour.
 */
expect fun createPlatformFormat(clockFormat: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR): PlatformFormat

/**
 * Built once per process rather than per composable: constructing a locale-aware number
 * formatter is not free, and every screen that shows a time needs one. Provided explicitly in
 * `App`, but the default keeps previews and any stray call site correct.
 */
val LocalPlatformFormat = staticCompositionLocalOf { createPlatformFormat() }

/**
 * A locale-free [PlatformFormat]: British English wording, Western digits. It is the default for
 * the pure view models so their tests read the same on any machine, and the fallback wherever a
 * device format is genuinely unavailable. The app always passes the real one.
 */
object EnglishPlatformFormat : PlatformFormat {
    override fun languageTag(): String = "en"
    override fun localizedDigits(number: Int): String = number.toString()
    override fun clockTime(hour: Int, minute: Int): String =
        "$hour:${minute.toString().padStart(2, '0')}"

    override fun longDate(date: LocalDate): String =
        "${date.day} ${GREGORIAN_MONTHS[date.month.number - 1]} ${date.year}"

    /** Only the languages the app actually bundles a translation in; anything else falls back to
     * the bare code, which at least names the row rather than leaving it blank. */
    override fun languageName(code: String): String = LANGUAGE_NAMES[code.lowercase()] ?: code
}

private val LANGUAGE_NAMES = mapOf(
    "en" to "English",
    "ar" to "Arabic",
    "id" to "Indonesian",
    "ur" to "Urdu",
    "bn" to "Bengali",
    "tr" to "Turkish",
    "fr" to "French",
)

private val GREGORIAN_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** Monday first, as ISO numbers the days. */
private val ENGLISH_WEEKDAYS = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)
