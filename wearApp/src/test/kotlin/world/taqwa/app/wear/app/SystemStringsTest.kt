package world.taqwa.app.wear.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Comment
import org.w3c.dom.Element
import world.taqwa.app.wear.complication.TestXml

/**
 * The words the watch system shows from the app's own resources, in the watch's language and often
 * before any feed exists (spec 1.3): the app's name, the pickers' names and previews, and the two
 * lines a watch shows before it holds a feed. They ship in the phone's seven languages, in the
 * folders the phone's own resources use (Android spells Indonesian `in`).
 *
 * A phrase the phone already has is the phone's own text, and every other one has had its native
 * review (plan 5 Task 11): the five languages by native readers (Ruling 71), and the Arabic by
 * Mohamed at M8 (6 November 2026). Until then each draft's nearest comment above it marked it, and
 * now no comment marks a draft.
 */
class SystemStringsTest {

    private val res = File(System.getProperty("taqwa.wearRes") ?: "src/main/res")
    private val phoneRes = File(System.getProperty("taqwa.phoneRes") ?: "../androidApp/src/main/res")

    @Test
    fun everyStringIsInEveryLanguageWithItsPlaces() {
        val english = strings(File(res, "values/strings.xml"))
        for (key in listOf("app_name", "open_taqwa_on_phone", "update_taqwa_on_watch", "tile_label", "tile_description")) {
            assertTrue(key in english, "values/strings.xml has $key")
        }
        for (language in LANGUAGES) {
            val other = strings(File(res, "values-$language/strings.xml"))
            assertEquals(english.keys, other.keys, "values-$language has the same keys as values")
            for ((key, value) in english) {
                assertEquals(places(value), places(other.getValue(key)), "values-$language $key keeps the places of '$value'")
            }
        }
    }

    @Test
    fun indonesianIsValuesInAlone() {
        assertFalse(File(res, "values-id").exists(), "no values-id: Android resources spell Indonesian 'in'")
        assertTrue(File(res, "values-in/strings.xml").isFile)
        assertTrue(File(phoneRes, "values-in/strings.xml").isFile, "as the phone's own resources do")
        assertFalse(File(phoneRes, "values-id").exists())
    }

    @Test
    fun noValueHoldsAStraightApostrophe() {
        for (folder in listOf("values") + LANGUAGES.map { "values-$it" }) {
            for ((key, value) in strings(File(res, "$folder/strings.xml"))) {
                assertFalse('\'' in value, "$folder $key: write ’, not a straight apostrophe")
            }
        }
    }

    @Test
    fun theWordsThePhoneHasAreThePhones() {
        for (folder in listOf("values") + LANGUAGES.map { "values-$it" }) {
            val watch = strings(File(res, "$folder/strings.xml"))
            val phone = strings(File(phoneRes, "$folder/strings.xml"))
            for ((key, phoneKey) in FROM_THE_PHONE) {
                assertEquals(phone.getValue(phoneKey), watch.getValue(key), "$folder $key is the phone's $phoneKey")
            }
        }
    }

    /**
     * Since M8 (6 November 2026) every phrase has had its native review, so no comment in the six
     * other languages marks one as a draft: a draft added later is held back here until it is read.
     */
    @Test
    fun noPhraseIsMarkedADraftSinceTheNativeReview() {
        for (folder in LANGUAGES.map { "values-$it" }) {
            val root = TestXml.parse(File(res, "$folder/strings.xml"))
            val nodes = (0 until root.childNodes.length).map { root.childNodes.item(it) }
            assertTrue(nodes.any { it is Element && it.tagName == "string" }, "$folder has its strings")
            for (node in nodes.filterIsInstance<Comment>()) {
                assertFalse(DRAFT_MARK.containsMatchIn(node.data), "$folder: a comment marks a draft: ${node.data.trim()}")
            }
        }
    }

    /** Each `<string>` of [file], by its name, with aapt's `\'` and `\"` read as the characters. */
    private fun strings(file: File): Map<String, String> {
        val root = TestXml.parse(file)
        val list = root.getElementsByTagName("string")
        return (0 until list.length).map { list.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun places(value: String): List<String> = PLACE.findAll(value).map { it.value }.sorted().toList()

    private companion object {
        val LANGUAGES = listOf("ar", "fr", "tr", "in", "ur", "bn")
        val PLACE = Regex("""%\d+\$[sd]""")
        /** What marked a draft until M8, in the six languages' comments, in any of its forms. */
        val DRAFT_MARK = Regex("draft|native review", RegexOption.IGNORE_CASE)

        /** The watch's strings that are the phone's own words, by the phone's key in `androidApp`. */
        val FROM_THE_PHONE = mapOf(
            "app_name" to "app_name",
            "complication_mark" to "app_name",
            "complication_mark_description" to "app_name",
            "complication_next_prayer" to "widget_small_label",
            "tile_label" to "widget_medium_label",
        )
    }
}
