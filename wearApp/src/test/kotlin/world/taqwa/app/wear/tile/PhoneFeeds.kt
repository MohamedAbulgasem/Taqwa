package world.taqwa.app.wear.tile

import java.io.File
import java.text.DateFormatSymbols
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchCountdownPhrases
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watch.WatchSpoken

/**
 * A feed's days and place in each of the phone's seven languages and on both its clocks, in the
 * words the phone writes them in, for the tests that weigh the Tile in every language (review
 * round 5): its prayer names, spoken sentences, plurals and the rows' states from `:shared`'s
 * composeResources, the Wear app's own two lines from its `values-*` (which the phone sends as
 * the screens' words), its countdown forms and digit sets from `tools/watchface/phone-forms.json`
 * and the Tile's compact forms from `WatchWords.kt` (`PhoneWords.compact`), and its day periods from `DateFormatSymbols`, the phone's own call (`PlatformFormat.android`).
 *
 * Where a language writes numbers in more than one set, the feed takes the heaviest (Urdu's
 * extended Arabic-Indic digits, Saudi Arabic's Arabic-Indic), and a 12-hour time is the phone's
 * `TwelveHourClock.render` (a no-break space, the day period first in Turkish). Each plural is the
 * phone's run of phrases for 0 to 99 (`WatchCount`), by its language's CLDR rule for whole numbers.
 * `PhoneFeedsTest` holds these to the phone's golden feeds where it has them.
 */
internal object PhoneFeeds {

    /** One of the phone's languages ([tag], its words locale) on one of its clocks. */
    data class Phone(val tag: String, val twelveHour: Boolean) {
        override fun toString(): String = if (twelveHour) "$tag 12-hour" else tag
    }

    /** Every language on both clocks: fourteen phones. */
    val ALL: List<Phone> = PhoneWords.SEVEN.flatMap { (_, tag) -> listOf(Phone(tag, twelveHour = false), Phone(tag, twelveHour = true)) }

    private val phoneStrings = File(System.getProperty("taqwa.phoneStrings") ?: "../shared/src/commonMain/composeResources")
    private val wearStrings = File(System.getProperty("taqwa.wearRes") ?: "src/main/res")
    private val forms = File(System.getProperty("taqwa.phoneForms") ?: "../tools/watchface/phone-forms.json")

    private val locales: JsonObject by lazy {
        check(forms.isFile) { "the phone's forms are not at ${forms.absolutePath}" }
        Json.parseToJsonElement(forms.readText()).jsonObject.getValue("locales").jsonObject
    }

    /**
     * [feed], an English 24-hour feed, as [phone] writes it, with [city] for its place. Only what
     * the phone writes in its language changes: the names, the clock strings, the countdown, the
     * spoken words and the five screens' words the Tile and Today read.
     */
    fun localised(feed: WatchFeed, phone: Phone, city: String = feed.place.city): WatchFeed {
        check(!feed.ui.twelveHour && feed.ui.language == "en") { "localised starts from an English 24-hour feed" }
        val folder = PhoneWords.SEVEN.first { it.second == phone.tag }.first
        val strings = strings(File(phoneStrings, "$folder/strings.xml"))
        val wear = strings(File(wearStrings, "$folder/strings.xml"))
        val form = locales.getValue(phone.tag).jsonObject
        val digits = form.getValue("digitSets").jsonArray.map { it.jsonPrimitive.content }.maxBy { it.encodeToByteArray().size }
        fun written(n: Int) = n.toString().map { digits[it - '0'] }.joinToString("")
        val (am, pm) = DateFormatSymbols.getInstance(Locale.forLanguageTag(phone.tag)).amPmStrings.let { it[0] to it[1] }
        val markerFirst = phone.tag.substringBefore('-') == "tr"
        val language = form.getValue("language").jsonPrimitive.content
        fun clock(text: String): Pair<String, String?> {
            val (h, m) = text.split(':').map { it.toInt() }
            if (!phone.twelveHour) return "${written(h)}:${written(m).padStart(2, digits[0])}" to null
            val hour = written(if (h % 12 == 0) 12 else h % 12)
            val minute = written(m).padStart(2, digits[0])
            val marker = if (h < 12) am else pm
            val full = if (markerFirst) "$marker$NBSP$hour:$minute" else "$hour:$minute$NBSP$marker"
            return "$hour:$minute" to full
        }
        val countdown = form.getValue("countdown").jsonObject
        fun phrase(key: String) = countdown.getValue(key).jsonPrimitive.content
        val names = listOf("prayer_fajr", "prayer_sunrise", "prayer_dhuhr", "prayer_jumuah", "prayer_asr", "prayer_maghrib", "prayer_isha").associateWith { strings.getValue(it) }
        val latin = names.values.all { name -> name.all { it.code < 0x0590 } }
        return feed.copy(
            place = feed.place.copy(city = city),
            ui = feed.ui.copy(
                language = phone.tag,
                rtl = form.getValue("rtl").jsonPrimitive.content.toBoolean(),
                digitZero = digits[0],
                twelveHour = phone.twelveHour,
                am = am,
                pm = pm,
                countdown = PhoneWords.compact(phone.tag).let { compact ->
                    WatchCountdownPhrases(
                        phrase("hoursMinutes"), phrase("hours"), phrase("minutes"), phrase("underAMinute"),
                        compactHoursMinutes = compact.hoursMinutes, compactHours = compact.hours, compactMinutes = compact.minutes,
                    )
                },
                nextIn = strings.getValue("prayer_next_in"),
                spoken = WatchSpoken(
                    countdown = strings.getValue("prayer_a11y_countdown"),
                    countdownSoon = strings.getValue("prayer_a11y_countdown_soon"),
                    hoursMinutes = strings.getValue("duration_hours_minutes"),
                    hours = runs(plurals(File(phoneStrings, "$folder/strings.xml"), "a11y_hours"), language),
                    minutes = runs(plurals(File(phoneStrings, "$folder/strings.xml"), "a11y_minutes"), language),
                ),
                screens = feed.ui.screens.copy(
                    openOnPhone = wear.getValue("open_taqwa_on_phone"),
                    updateOnWatch = wear.getValue("update_taqwa_on_watch"),
                    rowCurrent = strings.getValue("prayer_a11y_prayer_current"),
                    rowPassed = strings.getValue("prayer_a11y_prayer_passed"),
                    rowNext = strings.getValue("prayer_a11y_prayer_next"),
                ),
                latinScript = latin,
            ),
            days = feed.days.map { day ->
                val c = day.clock
                val six = listOf(c.fajr, c.sunrise, c.dhuhr, c.asr, c.maghrib, c.isha).map(::clock)
                val friday = day.names.dhuhr != "Dhuhr"
                day.copy(
                    names = WatchSix(
                        fajr = names.getValue("prayer_fajr"),
                        sunrise = names.getValue("prayer_sunrise"),
                        dhuhr = names.getValue(if (friday) "prayer_jumuah" else "prayer_dhuhr"),
                        asr = names.getValue("prayer_asr"),
                        maghrib = names.getValue("prayer_maghrib"),
                        isha = names.getValue("prayer_isha"),
                    ),
                    clock = WatchSix(six[0].first, six[1].first, six[2].first, six[3].first, six[4].first, six[5].first),
                    clockTime = if (phone.twelveHour) WatchSix(six[0].second!!, six[1].second!!, six[2].second!!, six[3].second!!, six[4].second!!, six[5].second!!) else null,
                )
            },
        )
    }

    /** The `<string>`s of [file], unescaped as Android reads them. */
    private fun strings(file: File): Map<String, String> {
        check(file.isFile) { "no strings at ${file.absolutePath}" }
        return Regex("<string name=\"([^\"]+)\">(.*?)</string>").findAll(file.readText()).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
    }

    /** The items of the plural [name] in [file], by quantity. */
    private fun plurals(file: File, name: String): Map<String, String> {
        val body = checkNotNull(Regex("<plurals name=\"$name\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL).find(file.readText())) { "$name in $file" }.groupValues[1]
        return Regex("<item quantity=\"([a-z]+)\">(.*?)</item>").findAll(body).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
    }

    private fun unescape(text: String): String =
        text.replace("\\'", "'").replace("\\\"", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    /**
     * The phone's runs of [items] for 0 to 99 (`WatchCount`): each number's phrase by its
     * [language]'s CLDR plural rule for whole numbers, falling back to `other` as Android does,
     * one entry for each run of numbers that share a phrase.
     */
    private fun runs(items: Map<String, String>, language: String): List<WatchCount> {
        val runs = mutableListOf<WatchCount>()
        for (n in 0..99) {
            val phrase = items[category(language, n)] ?: items.getValue("other")
            if (runs.isNotEmpty() && runs.last().phrase == phrase) runs[runs.lastIndex] = WatchCount(n, phrase) else runs += WatchCount(n, phrase)
        }
        return runs
    }

    /** CLDR's plural category of the whole number [n] in [language], for the seven. */
    private fun category(language: String, n: Int): String = when (language) {
        "ar" -> when {
            n == 0 -> "zero"
            n == 1 -> "one"
            n == 2 -> "two"
            n % 100 in 3..10 -> "few"
            n % 100 in 11..99 -> "many"
            else -> "other"
        }
        "fr", "bn" -> if (n == 0 || n == 1) "one" else "other"
        "id" -> "other"
        else -> if (n == 1) "one" else "other"
    }

    private val NBSP = Char(0xA0)
}
