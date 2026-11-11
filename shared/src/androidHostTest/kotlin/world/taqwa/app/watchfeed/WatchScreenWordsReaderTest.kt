package world.taqwa.app.watchfeed

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchScreenWords
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.reflect.KProperty1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The words of the watch's screens as the phone reads them (plan 3 Task 10): each language's, read
 * through the host's stand-in for the resource environment ([BundledScreenStrings]), against the
 * phone's own strings, the Wear app's own two notices, and the worst case the size tests send.
 */
class WatchScreenWordsReaderTest {

    private fun words(language: UiLanguage): WatchScreenWords = BundledScreenStrings.words(language)

    @Test
    fun everyLanguageHasEveryWord() {
        val english = texts(WatchScreenWords.ENGLISH)
        for (language in UiLanguage.entries) {
            val words = words(language)
            val label = language.code
            assertEquals(4, words.cardinals.size, "$label cardinals")
            val mine = texts(words)
            assertEquals(english.keys, mine.keys, "$label fields")
            for ((field, list) in mine) {
                val want = english.getValue(field)
                assertTrue(list.isNotEmpty(), "$label $field is empty")
                for (text in list) assertTrue(text.isNotBlank(), "$label $field holds a blank word")
                // Only the set of places, never their order: a language may rightly put %2$s first.
                val places = list.map(::places).toSet()
                assertEquals(want.map(::places).toSet(), places, "$label $field: $list against English's $want")
            }
            assertEquals(WatchScreenWords.ENGLISH.presets.keys.toList(), words.presets.keys.toList(), "$label presets")
            for (turns in listOf(words.turnRight, words.turnLeft)) {
                assertEquals(turns.sortedBy { it.upTo }, turns, "$label turns in order")
                assertEquals(LAST_TURN, turns.last().upTo, "$label: the last run takes the largest turn asked")
            }
        }
    }

    @Test
    fun aPlaceMayComeFirst() {
        assertEquals("১০০-এর মধ্যে ১৩", WatchRules.filled(words(UiLanguage.BENGALI).countSpoken, "১৩", "১০০"))
        assertEquals("100 میں سے 13", WatchRules.filled(words(UiLanguage.URDU).countSpoken, "13", "100"))
    }

    @Test
    fun theWordsAreThePhonesOwn() {
        for (language in UiLanguage.entries) {
            val words = words(language)
            for ((field, key) in FROM_THE_PHONE) {
                assertEquals(phoneString(language, key), field.get(words), "${language.code} ${field.name} from $key")
            }
            for ((id, name) in words.presets) {
                assertEquals(phoneString(language, "tasbeeh_preset_$id"), name, "${language.code} preset $id")
            }
            assertEquals(
                listOf("north", "east", "south", "west").map { phoneString(language, "qibla_cardinal_$it") },
                words.cardinals,
                "${language.code} cardinals",
            )
        }

        val arabic = words(UiLanguage.ARABIC)
        // A new phrase, the watch's own (WatchWords).
        assertEquals("أدِر الإطار أو اضغط", arabic.bezelHint)
        // Accessibility round 2's table, until its keys are on the branch (WatchWords).
        assertEquals("%1\$s من %2\$s", arabic.countSpoken)
        assertEquals("عُدّ", arabic.countAction)
        // Its plural asked at 15, 30, … 180 degrees, as runs: درجات at 105 alone, CLDR's "few".
        assertEquals(
            listOf(
                WatchCount(90, "استدر يمينًا حوالي %1\$s درجة"),
                WatchCount(105, "استدر يمينًا حوالي %1\$s درجات"),
                WatchCount(180, "استدر يمينًا حوالي %1\$s درجة"),
            ),
            arabic.turnRight,
        )
        assertEquals(listOf(WatchCount(180, "Yaklaşık %1\$s derece sola dönün")), words(UiLanguage.TURKISH).turnLeft)
        assertEquals("%1\$s, toplam %2\$s", words(UiLanguage.TURKISH).countSpoken)
    }

    /**
     * The Apple Watch's two words (plan 4 Task 3): the app's name is the phone's own `app_name` in
     * the interface language, read through the same strings as every other word, and the crown's
     * hint is the watch's new phrase, whose English the UI tests listen for.
     */
    @Test
    fun theAppleWordsAreThePhonesOwn() {
        val names = mapOf(
            UiLanguage.ENGLISH to "Taqwa",
            UiLanguage.ARABIC to "تقوى",
            UiLanguage.BENGALI to "তাকওয়া",
            UiLanguage.URDU to "تقویٰ",
        )
        for ((language, name) in names) {
            assertEquals(name, runBlocking { BundledScreenStrings.of(language).text("app_name") }, language.code)
            assertEquals(name, words(language).appName, language.code)
        }
        assertEquals("Turn the crown or tap", words(UiLanguage.ENGLISH).crownHint)
    }

    /** Seven phrases for the crown, none empty, and Indonesian the same whichever way a tag spells it. */
    @Test
    fun theCrownHintIsInEveryLanguage() {
        val hints = UiLanguage.entries.associateWith { words(it).crownHint }
        assertEquals(7, hints.size)
        for ((language, hint) in hints) assertTrue(hint.isNotBlank(), "${language.code} has no crown hint")
        assertEquals(7, hints.values.toSet().size, "each language its own phrase: $hints")
        assertEquals(words(UiLanguage.of("id")).crownHint, words(UiLanguage.of("in")).crownHint)
        assertEquals("Putar Digital Crown atau ketuk", words(UiLanguage.of("in")).crownHint)
        assertEquals("أدِر التاج أو اضغط", hints.getValue(UiLanguage.ARABIC))
        // Compose's rule, which tools/i18n-check.py holds for the resources: the typographic
        // apostrophe, never a backslash before a plain one.
        assertEquals("Digital Crown’u çevirin veya dokunun", hints.getValue(UiLanguage.TURKISH))
    }

    @Test
    fun theWatchAndThePhoneSayTheNoticesAlike() {
        for (language in UiLanguage.entries) {
            val words = words(language)
            assertEquals(wearString(language, "open_taqwa_on_phone"), words.openOnPhone, language.code)
            assertEquals(wearString(language, "update_taqwa_on_watch"), words.updateOnWatch, language.code)
        }
    }

    @Test
    fun bundledEnglishIsTheEnglishDefault() {
        assertEquals(WatchScreenWords.ENGLISH, words(UiLanguage.ENGLISH))
    }

    @Test
    fun aReadThatThrowsIsNotGuessed() {
        // Every key the reader asks for, then each of them failing in turn: no read is guarded.
        val asked = mutableListOf<String>()
        val recording = object : ScreenStrings {
            private val bundled = BundledScreenStrings.of(UiLanguage.FRENCH)
            override suspend fun text(key: String): String = bundled.text(key).also { asked += key }
            override suspend fun counted(key: String, n: Int): String = bundled.counted(key, n).also { asked += key }
        }
        runBlocking { watchScreenWords(UiLanguage.FRENCH, recording) }
        assertTrue(asked.size >= FROM_THE_PHONE.size + WatchScreenWords.ENGLISH.presets.size + 4, "asked $asked")
        for (key in asked.toSet()) {
            val thrown = assertFailsWith<IllegalStateException>(key) {
                runBlocking { watchScreenWords(UiLanguage.FRENCH, throwingOn(key)) }
            }
            assertEquals("no $key", thrown.message)
        }
    }

    @Test
    fun theWorstWordsOutweighEveryLanguage() {
        val worst = sizeOf(WorstFeeds.SCREENS)
        var largest = 0 to ""
        for (language in UiLanguage.entries) {
            val size = sizeOf(words(language))
            assertTrue(size <= worst, "${language.code}'s words take $size bytes, the worst $worst")
            if (size > largest.first) largest = size to language.code
        }
        println("[measure] screens' words: largest ${largest.second}, ${largest.first} UTF-8 bytes; WorstFeeds.SCREENS $worst")
    }

    // ---- Helpers

    /** A stand-in that reads French as the phone would, but throws for [failing]. */
    private fun throwingOn(failing: String): ScreenStrings = object : ScreenStrings {
        private val bundled = BundledScreenStrings.of(UiLanguage.FRENCH)
        override suspend fun text(key: String): String =
            if (key == failing) throw IllegalStateException("no $key") else bundled.text(key)
        override suspend fun counted(key: String, n: Int): String =
            if (key == failing) throw IllegalStateException("no $key") else bundled.counted(key, n)
    }

    private fun sizeOf(words: WatchScreenWords): Int =
        Json.encodeToString(WatchScreenWords.serializer(), words).encodeToByteArray().size

    /**
     * Every word in [words], by its field: a string as itself, a map's values, a list's strings or
     * its runs' phrases. Defaults are encoded, so a field left blank is seen.
     */
    private fun texts(words: WatchScreenWords): Map<String, List<String>> {
        val tree = ENCODE_DEFAULTS.encodeToJsonElement(WatchScreenWords.serializer(), words) as JsonObject
        return tree.mapValues { (_, value) -> leaves(value) }
    }

    private fun leaves(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> listOf(element.content)
        is JsonArray -> element.flatMap { item ->
            if (item is JsonObject) listOf(item.getValue("phrase").jsonPrimitive.content) else leaves(item)
        }
        is JsonObject -> element.values.flatMap(::leaves)
    }

    private fun places(text: String): Set<String> = PLACE.findAll(text).map { it.value }.toSet()

    /** [key] in [language]'s Compose `strings.xml`, or in `values/` where that folder lacks it, as Compose reads it. */
    private fun phoneString(language: UiLanguage, key: String): String {
        val own = if (language == UiLanguage.ENGLISH) "values" else "values-${language.code}"
        return xmlString(composeResources(), own, key) ?: xmlString(composeResources(), "values", key) ?: error("no $key")
    }

    /** [key] in the Wear app's own resources for [language]: Android spells Indonesian `in`. */
    private fun wearString(language: UiLanguage, key: String): String {
        val directory = when (language) {
            UiLanguage.ENGLISH -> "values"
            UiLanguage.INDONESIAN -> "values-in"
            else -> "values-${language.code}"
        }
        val res = File("../wearApp/src/main/res").takeIf { it.isDirectory } ?: File("wearApp/src/main/res")
        return xmlString(res, directory, key) ?: error("no $key in $directory")
    }

    private fun composeResources(): File =
        File("src/commonMain/composeResources").takeIf { it.isDirectory } ?: File("shared/src/commonMain/composeResources")

    private fun xmlString(resources: File, directory: String, key: String): String? {
        val file = File(resources, "$directory/strings.xml")
        check(file.exists()) { "no $file" }
        val root: Element = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val nodes = root.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("name") == key) return e.textContent
        }
        return null
    }

    private companion object {
        val ENCODE_DEFAULTS = Json { encodeDefaults = true }

        val PLACE = Regex("""%\d+\$[sd]""")

        /** The largest turn the phone asks its plural for: 180 degrees. */
        const val LAST_TURN = 180

        /** The fields read from the phone's own strings, and the key each is read from (plan 3 Task 10's table). */
        val FROM_THE_PHONE: List<Pair<KProperty1<WatchScreenWords, String>, String>> = listOf(
            WatchScreenWords::today to "qibla_back",
            WatchScreenWords::tasbeeh to "tasbeeh_title",
            WatchScreenWords::qibla to "qibla_title",
            WatchScreenWords::appName to "app_name",
            WatchScreenWords::rowCurrent to "prayer_a11y_prayer_current",
            WatchScreenWords::rowPassed to "prayer_a11y_prayer_passed",
            WatchScreenWords::rowNext to "prayer_a11y_prayer_next",
            WatchScreenWords::round to "tasbeeh_round",
            WatchScreenWords::of to "tasbeeh_of",
            WatchScreenWords::reset to "tasbeeh_reset",
            WatchScreenWords::resetConfirm to "tasbeeh_reset_confirm",
            WatchScreenWords::facingQibla to "qibla_facing_qibla",
            WatchScreenWords::distance to "qibla_distance",
            WatchScreenWords::bearing to "qibla_bearing",
            WatchScreenWords::qiblaDetail to "prayer_qibla_detail",
            WatchScreenWords::needsCalibrating to "qibla_needs_calibrating",
            WatchScreenWords::interference to "qibla_interference_title",
            WatchScreenWords::bestEffort to "qibla_best_effort_title",
            WatchScreenWords::noSensor to "qibla_no_sensor_title",
        )
    }
}
