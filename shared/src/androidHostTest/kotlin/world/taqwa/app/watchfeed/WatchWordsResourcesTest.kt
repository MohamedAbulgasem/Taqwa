package world.taqwa.app.watchfeed

import org.w3c.dom.Element
import world.taqwa.app.i18n.UiLanguage
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The watch's words against the phone's own strings, read straight from the Compose resources:
 * `WatchWords` is plain Kotlin, for a boot receiver with no composition, so it is kept in step with
 * the files here, as `PrayerNaming` is with the prayer names.
 */
class WatchWordsResourcesTest {

    /** Each `values*` directory of the Compose resources, with the interface language it holds. */
    private val directories: List<Pair<String, UiLanguage>> =
        listOf("", "-ar", "-fr", "-tr", "-id", "-in", "-ur", "-bn").map { suffix ->
            "values$suffix" to UiLanguage.of(suffix.removePrefix("-").ifEmpty { "en" })
        }

    private fun resources(): File {
        val moduleRelative = File("src/commonMain/composeResources")
        return if (moduleRelative.exists()) moduleRelative else File("shared/src/commonMain/composeResources")
    }

    private fun strings(directory: String): Element {
        val file = File(resources(), "$directory/strings.xml")
        assertTrue(file.exists(), "no $file")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.string(name: String): String? {
        val nodes = getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("name") == name) return e.textContent
        }
        return null
    }

    private fun Element.pluralItems(name: String): Map<String, String>? {
        val nodes = getElementsByTagName("plurals")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("name") != name) continue
            val items = e.getElementsByTagName("item")
            return (0 until items.length).associate {
                val item = items.item(it) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
        return null
    }

    @Test
    fun jumuahIsThePrayerScreensWord() {
        for ((directory, language) in directories) {
            assertEquals(strings(directory).string("prayer_jumuah"), WatchWords.jumuah(language), directory)
        }
    }

    /**
     * Ruling 60 item 4, as the native review settled it (plan 5 Task 11): the two watches' hints
     * name the tap with the phone's own word, the one its tasbeeh hint (`tasbeeh_hint`) uses.
     * `WatchWordsTest.theTwoWatchesHintsNameTheTapAlike` holds the two hints alike; this holds
     * their word to the phone's, in each language.
     */
    @Test
    fun theWatchesTapIsThePhonesOwnWord() {
        val or = mapOf(
            UiLanguage.ENGLISH to " or ", UiLanguage.ARABIC to " أو ", UiLanguage.FRENCH to " ou ",
            UiLanguage.TURKISH to " veya ", UiLanguage.INDONESIAN to " atau ", UiLanguage.URDU to " یا ",
            UiLanguage.BENGALI to " বা ",
        )
        for ((directory, language) in directories) {
            val words = WatchWords.screens(language)
            val hint = assertNotNull(strings(directory).string("tasbeeh_hint"), "$directory tasbeeh_hint")
            for (watchHint in listOf(words.bezelHint, words.crownHint)) {
                val tap = watchHint.substringAfterLast(or.getValue(language), missingDelimiterValue = "")
                assertTrue(
                    tap.isNotEmpty() && hint.contains(tap, ignoreCase = true),
                    "$directory: '$watchHint' names the tap apart from the phone's '$hint'",
                )
            }
        }
    }

    @Test
    fun everyLanguageHasTheSpokenWords() {
        for ((directory, _) in directories) {
            val xml = strings(directory)
            val countdown = assertNotNull(xml.string("prayer_a11y_countdown"), "$directory prayer_a11y_countdown")
            for (place in listOf("%1\$s", "%2\$s", "%3\$s")) {
                assertTrue(countdown.contains(place), "$directory prayer_a11y_countdown: $countdown")
            }
            for (name in listOf("prayer_a11y_countdown_soon", "duration_hours_minutes")) {
                val text = assertNotNull(xml.string(name), "$directory $name")
                assertTrue(text.contains("%1\$s") && text.contains("%2\$s"), "$directory $name: $text")
            }
            for (name in listOf("a11y_hours", "a11y_minutes")) {
                val items = assertNotNull(xml.pluralItems(name), "$directory $name")
                val other = assertNotNull(items["other"], "$directory $name has no other item")
                assertTrue(other.contains("%1\$s"), "$directory $name other: $other")
            }
        }
    }

    /**
     * Compose Multiplatform picks `values-<language>` for a phone in that language and `values`
     * when it has none, so a phone on [tag], whatever its digits' extension, shows the app in the
     * language `UiLanguage.of(tag)` names: the language Task 2's forms and Task 3's size test build
     * each tag in. The language is read both as the tag spells it and as Java's `Locale` does,
     * which still spells Indonesian `in` on some runtimes (hence the twins `values-id` and
     * `values-in`).
     */
    @Test
    fun everyNamedTagResolvesAsUiLanguageOfIt() {
        for (tag in PhoneTags.TAGS + PhoneTags.FALLBACK_TAGS) {
            val expected = UiLanguage.of(tag).code
            for (t in PhoneTags.numberingTags(tag)) {
                val spellings = setOf(
                    t.substringBefore('-').substringBefore('_').lowercase(),
                    Locale.forLanguageTag(t).language,
                )
                for (language in spellings) {
                    val directory = "values-$language".takeIf { File(resources(), it).isDirectory } ?: "values"
                    assertEquals(expected, strings(directory).string("ui_language"), "$t as $language: $directory")
                }
            }
        }
    }
}
