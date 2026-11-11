package world.taqwa.app.watchfeed

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import world.taqwa.app.i18n.AndroidPlatformFormat
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.UiLanguage
import java.util.Locale

/**
 * The forms the feed writes that the watch face draws unmeasured otherwise, for every tag the face
 * is measured for ([PhoneTags.TAGS] and [PhoneTags.FALLBACK_TAGS]): the Gregorian date line's
 * words, the countdown's phrases and the digit sets. The face's fit tools read them
 * (`tools/watchface/phone-forms.json`: `date-fit.py`, `window-fit.py` and `moon-fit.py`), so that
 * every line they measure is one the phone can send.
 *
 * Each tag is built as the feed builds it: through [AndroidPlatformFormat] on the tag, with
 * `UiLanguage.of(tag)` as its interface language (the app's language on such a phone, as
 * `everyNamedTagResolvesAsUiLanguageOfIt` holds), through [WatchDateLine], [WatchWords] and
 * [WatchDigits]. It declares no tag list and no digit rule of its own ([PhoneTags]).
 *
 * It runs on whatever runtime runs it: the JVM's CLDR in `FaceFormsTest`, which writes the committed
 * file, and the phone's ICU in plan 2 Task 7's harness, whose file is compared with it. Release code
 * never calls it, so R8 drops it.
 */
internal object PhoneForms {

    /** The forms as the file holds them: `{"locales": {tag: {…}}}`, the tags in [PhoneTags]'s order. */
    fun json(): String = buildString {
        append("{\n    \"locales\": {\n")
        val tags = PhoneTags.TAGS + PhoneTags.FALLBACK_TAGS
        tags.forEachIndexed { i, tag ->
            append("        ").append(quoted(tag)).append(": ").append(locale(tag))
            append(if (i < tags.lastIndex) ",\n" else "\n")
        }
        append("    }\n}\n")
    }

    private fun locale(tag: String): String {
        val language = UiLanguage.of(tag)
        val format = format(tag)
        val digitSets = PhoneTags.digitSets(tag) { format(it) }.map { it.digits }
        // a week from a Sunday, and the first of each month, in the year the face's tools draw
        val weekdays = (0 until 7).map { WatchDateLine.of(LocalDate(2027, 1, 3 + it), format, language).weekday }
        val months = (1..12).map { month ->
            val line = WatchDateLine.of(LocalDate(2027, month, 1), format, language)
            line.gregorian.removePrefix("${line.weekday} ${line.dayNumber} ")
        }
        val countdown = WatchWords.countdown(language)
        val phrases = listOf(
            "hoursMinutes" to countdown.hoursMinutes,
            "hours" to countdown.hours,
            "minutes" to countdown.minutes,
            "underAMinute" to countdown.underAMinute,
        )
        val inner = "            "
        return "{\n" +
            "$inner\"language\": ${quoted(language.code)},\n" +
            "$inner\"rtl\": ${language.rtl},\n" +
            "$inner\"digitSets\": ${list(digitSets)},\n" +
            "$inner\"weekdays\": ${list(weekdays)},\n" +
            "$inner\"months\": ${list(months)},\n" +
            "$inner\"countdown\": {${phrases.joinToString(", ") { (key, phrase) -> "${quoted(key)}: ${quoted(phrase)}" }}}\n" +
            "        }"
    }

    private fun format(tag: String) = AndroidPlatformFormat(Locale.forLanguageTag(tag), ClockFormat.TWENTY_FOUR_HOUR)

    private fun list(items: List<String>): String = items.joinToString(", ", "[", "]") { quoted(it) }

    /** A JSON string, escaped as kotlinx writes one: only quotes, backslashes and control characters. */
    private fun quoted(text: String): String = JsonPrimitive(text).toString()
}
