package world.taqwa.app.wear.tile

import java.io.File
import java.text.DateFormatSymbols
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The words the Tile's countdown line can say in each of the phone's seven languages, as the phone
 * writes them: its prayer names (`prayer_*` in :shared's composeResources, the strings the feed's
 * `names` come from) and its countdown forms in every digit set it writes them in
 * (`tools/watchface/phone-forms.json`, the phone's own forms, which the face's fits read too), with
 * the Tile's compact forms from `WatchWords.kt` ([compact]).
 */
internal object PhoneWords {

    /**
     * One language: its [labels] as the line writes them (the five prayers and Jumuʿah, in capitals
     * under a Latin script, cased in the language) and its [countdowns], the longest it can say
     * (99 h 59 min) and under a minute, in each of its digit sets, in the full forms and in the
     * Tile's compact ones ([compactCountdowns]); its [clock], the widest start's time a 12-hour
     * phone writes in its first digit set, with its day period ("12:59 PM", the day period first
     * in Turkish); and whether it is written right to left ([rtl]).
     */
    data class Language(
        val tag: String,
        val labels: List<String>,
        val countdowns: List<String>,
        val compactCountdowns: List<String>,
        val clock: String,
        val rtl: Boolean,
    )

    private val strings = File(System.getProperty("taqwa.phoneStrings") ?: "../shared/src/commonMain/composeResources")
    private val forms = File(System.getProperty("taqwa.phoneForms") ?: "../tools/watchface/phone-forms.json")
    private val watchWords = File(System.getProperty("taqwa.watchWords") ?: "../shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchWords.kt")

    /** The Tile's compact countdown forms, which the face never draws and so `phone-forms.json` does not hold. */
    data class Compact(val hoursMinutes: String, val hours: String, val minutes: String)

    /**
     * [tag]'s compact countdown forms (the Tile's line), as `WatchWords.COUNTDOWN` writes them for
     * the feed, each empty where the language keeps its full form: a `\$` is a dollar, as Kotlin
     * reads it.
     */
    fun compact(tag: String): Compact {
        val name = mapOf("en" to "ENGLISH", "ar" to "ARABIC", "fr" to "FRENCH", "tr" to "TURKISH", "id" to "INDONESIAN", "ur" to "URDU", "bn" to "BENGALI").getValue(tag.substringBefore('-'))
        check(watchWords.isFile) { "the phone's watch words are not at ${watchWords.absolutePath}" }
        val text = watchWords.readText()
        val table = text.substring(text.indexOf("private val COUNTDOWN"), text.indexOf("private val FACE"))
        val start = table.indexOf("UiLanguage.$name to WatchCountdownPhrases(")
        check(start >= 0) { "no $name in WatchWords.COUNTDOWN" }
        val end = table.indexOf("UiLanguage.", start + 1).let { if (it < 0) table.length else it }
        val block = table.substring(start, end)
        fun field(key: String) = Regex("""$key = "([^"]*)"""").find(block)?.groupValues?.get(1)?.replace("\\$", "$").orEmpty()
        return Compact(field("compactHoursMinutes"), field("compactHours"), field("compactMinutes"))
    }

    /** The seven: the folder of each one's strings, and its locale in the phone's forms. */
    val SEVEN: List<Pair<String, String>> = listOf(
        "values" to "en",
        "values-ar" to "ar-SA",
        "values-fr" to "fr-FR",
        "values-tr" to "tr-TR",
        "values-in" to "id-ID",
        "values-ur" to "ur-PK",
        "values-bn" to "bn-BD",
    )

    private val PRAYERS = listOf("prayer_fajr", "prayer_dhuhr", "prayer_asr", "prayer_maghrib", "prayer_isha", "prayer_jumuah")

    val languages: List<Language> by lazy {
        check(forms.isFile) { "the phone's forms are not at ${forms.absolutePath}" }
        val locales = Json.parseToJsonElement(forms.readText()).jsonObject.getValue("locales").jsonObject
        SEVEN.map { (folder, tag) ->
            val file = File(strings, "$folder/strings.xml")
            check(file.isFile) { "the phone's strings are not at ${file.absolutePath}" }
            val xml = file.readText()
            val names = PRAYERS.map { key ->
                checkNotNull(Regex("<string name=\"$key\">(.*?)</string>").find(xml)) { "$key in $folder" }.groupValues[1].replace("\\'", "'")
            }
            val locale = locales.getValue(tag).jsonObject
            val language = locale.getValue("language").jsonPrimitive.content
            val latin = names.all { name -> name.all { it.code < 0x0590 } }
            val labels = if (latin) names.map { it.uppercase(Locale.forLanguageTag(language)) } else names
            val countdown = locale.getValue("countdown") as JsonObject
            val digitSets = locale.getValue("digitSets").jsonArray.map { it.jsonPrimitive.content }
            val compact = compact(tag).hoursMinutes.ifEmpty { countdown.getValue("hoursMinutes").jsonPrimitive.content }
            fun longest(hoursMinutes: String) = digitSets.flatMap { digits ->
                fun number(n: Int) = n.toString().map { digits[it - '0'] }.joinToString("")
                listOf(
                    hoursMinutes.replace("%1\$s", number(99)).replace("%2\$s", number(59)),
                    countdown.getValue("underAMinute").jsonPrimitive.content,
                )
            }.distinct()
            val digits = digitSets.first()
            val (_, pm) = DateFormatSymbols.getInstance(Locale.forLanguageTag(tag)).amPmStrings.let { it[0] to it[1] }
            val time = "12:59".map { if (it.isDigit()) digits[it - '0'] else it }.joinToString("")
            val clock = if (language == "tr") "$pm${Char(0xA0)}$time" else "$time${Char(0xA0)}$pm"
            val rtl = locale.getValue("rtl").jsonPrimitive.content.toBoolean()
            Language(tag, labels, longest(countdown.getValue("hoursMinutes").jsonPrimitive.content), longest(compact), clock, rtl)
        }
    }
}
