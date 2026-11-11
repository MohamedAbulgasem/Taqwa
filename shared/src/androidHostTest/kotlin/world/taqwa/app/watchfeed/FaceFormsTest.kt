package world.taqwa.app.watchfeed

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import world.taqwa.app.i18n.AndroidPlatformFormat
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.UiLanguage
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * The phone's forms that the face's fit tools measure (`tools/watchface/phone-forms.json`: the
 * Gregorian date words, the countdown's phrases and the digit sets, for every tag the face is
 * measured for), held to what the feed writes.
 *
 * The file is [PhoneForms.json] as the JVM's CLDR gives it. It fails when the file is missing or
 * differs. After a change that is meant, write it again with
 * `./gradlew :shared:testAndroidHostTest --tests '*FaceFormsTest*' -Ptaqwa.updateVectors`, read the
 * diff, run the fit tools on it (`tools/watchface/README.md`), and commit both.
 *
 * [formsComposeAsTheFeedWritesThem] is why the tools may compose a line from the file's words: for
 * every date of a year, in every digit set, the feed's line is the file's weekday, the day and the
 * file's month, and nothing else.
 */
class FaceFormsTest {

    private fun formsFile(): File =
        if (File("src/androidHostTest").isDirectory) File("../tools/watchface/phone-forms.json") else File("tools/watchface/phone-forms.json")

    private val locales: JsonObject by lazy {
        val file = formsFile()
        if (!file.exists()) fail("${file.path} is missing. ${HOW_TO_WRITE}")
        Json.parseToJsonElement(file.readText()).jsonObject.getValue("locales").jsonObject
    }

    private fun format(tag: String) = AndroidPlatformFormat(Locale.forLanguageTag(tag), ClockFormat.TWENTY_FOUR_HOUR)

    private fun strings(array: JsonArray): List<String> = array.map { it.jsonPrimitive.content }

    /** [set]'s ten digits, each a string, so that a digit outside the BMP would be whole. */
    private fun digitsOf(set: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < set.length) {
            val n = Character.charCount(set.codePointAt(i))
            out += set.substring(i, i + n)
            i += n
        }
        return out
    }

    @Test
    fun phoneFormsAreUpToDate() {
        val file = formsFile()
        val expected = PhoneForms.json()
        if (System.getProperty("taqwa.updateVectors") != null) {
            file.absoluteFile.parentFile?.mkdirs()
            file.writeText(expected)
            return
        }
        if (!file.exists()) fail("${file.path} is missing. $HOW_TO_WRITE")
        val actual = file.readText()
        if (actual != expected) {
            val column = actual.commonPrefixWith(expected).length
            fun excerpt(text: String) = text.substring((column - 40).coerceAtLeast(0), minOf(text.length, column + 80))
            fail(
                "${file.path} is not the forms the phone writes. First difference at character $column:\n" +
                    "    file:  ${excerpt(actual)}\n    phone: ${excerpt(expected)}\n$HOW_TO_WRITE",
            )
        }
    }

    @Test
    fun formsComposeAsTheFeedWritesThem() {
        val year = (0 until 365).map { LocalDate(2027, 1, 1).plus(DatePeriod(days = it)) }
        for (tag in PhoneTags.TAGS + PhoneTags.FALLBACK_TAGS) {
            val language = UiLanguage.of(tag)
            val entry = assertNotNull(locales[tag]?.jsonObject, "$tag is not in the forms file")
            val weekdays = strings(entry.getValue("weekdays").jsonArray)
            val months = strings(entry.getValue("months").jsonArray)
            val sets = PhoneTags.digitSets(tag) { format(it) }
            assertEquals(sets.map { it.digits }, strings(entry.getValue("digitSets").jsonArray), "$tag's digit sets")
            for (set in sets) {
                val format = format(set.formatTag)
                val digits = digitsOf(set.digits)
                for (date in year) {
                    val written = WatchDigits.of(format, language).write(WatchDateLine.of(date, format, language).gregorian)
                    val day = date.day.toString().map { digits[it - '0'] }.joinToString("")
                    // the file's weekdays are Sunday first; ISO numbers Monday 1 to Sunday 7
                    val composed = "${weekdays[date.dayOfWeek.ordinal.plus(1) % 7]} $day ${months[date.month.ordinal]}"
                    assertEquals(composed, written, "$tag in ${set.formatTag}'s digits on $date")
                }
            }
        }
    }

    @Test
    fun theFallbackLocalesAreMeasuredAsTheFeedWritesThem() {
        val english = locales.getValue("en").jsonObject
        for (tag in listOf("hi-IN", "he-IL", "fa-IR", "mr-IN", "zh-CN")) {
            val entry = assertNotNull(locales[tag]?.jsonObject, "$tag is not in the forms file")
            assertEquals("en", entry.getValue("language").jsonPrimitive.content, tag)
            assertEquals(false, entry.getValue("rtl").jsonPrimitive.boolean, tag)
            for (key in listOf("weekdays", "months", "countdown")) assertEquals(english.getValue(key), entry.getValue(key), "$tag's $key")
            assertEquals(listOf("0123456789"), strings(entry.getValue("digitSets").jsonArray), "$tag's digit sets")
        }
    }

    private companion object {
        const val HOW_TO_WRITE = "To write it again: " +
            "./gradlew :shared:testAndroidHostTest --tests '*FaceFormsTest*' -Ptaqwa.updateVectors. " +
            "Read the diff, and run the face's fit tools on it, before you commit it."
    }
}
