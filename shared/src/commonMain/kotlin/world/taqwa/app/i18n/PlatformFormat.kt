package world.taqwa.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

interface PlatformFormat {
    fun languageTag(): String

    /** Locale-correct digits for a plain non-negative integer, e.g. "42" or "٤٢". */
    fun localizedDigits(number: Int): String

    /**
     * A 24-hour clock time using the platform's own locale formatting and digit set.
     *
     * The hour is *not* zero-padded and the minute always is — "3:42", "٣:٤٢", "15:07". That is
     * the shape the spec quotes for a Libyan user and the shape Today already showed before this
     * task, so migrating to CLDR digits does not silently reformat every English screen. (The
     * brief's sketch padded both fields; padding the hour would have turned every existing
     * "3:42" into "03:42".)
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
}

expect fun createPlatformFormat(): PlatformFormat

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
