package world.taqwa.app.wear.face

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Comment
import org.w3c.dom.Element
import world.taqwa.app.wear.complication.TestXml

/**
 * The Taqwa face's own words (plan 5 Task 8): what the watch system shows of the face from the
 * face's resources, in the watch's language and before any feed (spec 1.3). That is the face's name
 * in the face picker, and the face editor's style, colour and slot names. They ship in the phone's
 * seven languages, in the folders the phone's own resources use (Android spells Indonesian `in`).
 *
 * The face's name and "Next prayer" are the phone's own words. The fixed slots the pickers also
 * list are named as the watch app names their sources there. Every other phrase has had its native
 * review (plan 5 Task 11), and spec 1.3's Arabic style and colour names were read and approved by
 * Mohamed at M8 (6 November 2026), so no comment marks a draft any more.
 */
class FaceStringsTest {

    private val faceRes = File(System.getProperty("taqwa.faceRes") ?: "../watchface/src/main/res")
    private val wearRes = File(System.getProperty("taqwa.wearRes") ?: "src/main/res")
    private val phoneRes = File(System.getProperty("taqwa.phoneRes") ?: "../androidApp/src/main/res")

    @Test
    fun everyNameTheFaceShowsIsInEveryLanguage() {
        val english = strings(faceRes, "values")
        val shown = namesTheFaceShows()
        assertTrue("watch_face_name" in shown && "style_moon" in shown && "slot_left" in shown, "the face names $shown")
        for (name in shown) assertTrue(name in english, "values/strings.xml has $name, which the face shows")
        for (folder in FOLDERS) {
            assertEquals(english.keys, strings(faceRes, folder).keys, "$folder has the same keys as values")
        }
    }

    @Test
    fun indonesianIsValuesInAlone() {
        assertFalse(File(faceRes, "values-id").exists(), "no values-id: Android resources spell Indonesian 'in'")
        assertTrue(File(faceRes, "values-in/strings.xml").isFile)
    }

    @Test
    fun theWordsThePhoneHasAreThePhones() {
        for (folder in listOf("values") + FOLDERS) {
            val face = strings(faceRes, folder)
            val phone = strings(phoneRes, folder)
            for ((key, phoneKey) in FROM_THE_PHONE) {
                assertEquals(phone.getValue(phoneKey), face.getValue(key), "$folder $key is the phone's $phoneKey")
            }
        }
    }

    @Test
    fun theFixedSlotsAreNamedAsThePickersNameTheirSources() {
        for (folder in listOf("values") + FOLDERS) {
            val face = strings(faceRes, folder)
            val watch = strings(wearRes, folder)
            for ((key, watchKey) in AS_THE_PICKERS) {
                assertEquals(watch.getValue(watchKey), face.getValue(key), "$folder $key is the watch app's $watchKey")
            }
        }
    }

    @Test
    fun theArabicStylesAndColoursAreSpecOnePointThreesTable() {
        val arabic = strings(faceRes, "values-ar")
        for ((key, name) in SPEC_ARABIC) assertEquals(name, arabic.getValue(key), "values-ar $key")
    }

    /**
     * Since M8 (6 November 2026) every phrase has had its native review, so no comment in the six
     * other languages marks one as a draft: a draft added later is held back here until it is read.
     */
    @Test
    fun noPhraseIsMarkedADraftSinceTheNativeReview() {
        for (folder in FOLDERS) {
            val root = TestXml.parse(File(faceRes, "$folder/strings.xml"))
            val nodes = (0 until root.childNodes.length).map { root.childNodes.item(it) }
            assertTrue(nodes.any { it is Element && it.tagName == "string" }, "$folder has its strings")
            for (node in nodes.filterIsInstance<Comment>()) {
                assertFalse(DRAFT_MARK.containsMatchIn(node.data), "$folder: a comment marks a draft: ${node.data.trim()}")
            }
        }
    }

    @Test
    fun noValueHoldsAStraightApostrophe() {
        for (folder in listOf("values") + FOLDERS) {
            for ((key, value) in strings(faceRes, folder)) {
                assertFalse('\'' in value, "$folder $key: write ’, not a straight apostrophe")
            }
        }
    }

    /**
     * The string names the face shows: its label in the face picker (its manifest), and the
     * `displayName` and `screenReaderText` of watchface.xml, which name a string resource bare.
     */
    private fun namesTheFaceShows(): Set<String> {
        val manifest = TestXml.parse(File(faceRes.parentFile, "AndroidManifest.xml"))
        val label = (manifest.getElementsByTagName("application").item(0) as Element)
            .getAttributeNS(ANDROID, "label").removePrefix("@string/")
        val face = TestXml.parse(File(faceRes, "raw/watchface.xml"))
        val all = face.getElementsByTagName("*")
        val named = (0 until all.length).map { all.item(it) as Element }
            .flatMap { listOf(it.getAttribute("displayName"), it.getAttribute("screenReaderText")) }
            .filter { RESOURCE_NAME.matches(it) }
        return (named + label).toSet()
    }

    private fun strings(res: File, folder: String): Map<String, String> {
        val root = TestXml.parse(File(res, "$folder/strings.xml"))
        val list = root.getElementsByTagName("string")
        return (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private companion object {
        val FOLDERS = listOf("ar", "fr", "tr", "in", "ur", "bn").map { "values-$it" }
        /** What marked a draft until M8, in the six languages' comments, in any of its forms. */
        val DRAFT_MARK = Regex("draft|native review", RegexOption.IGNORE_CASE)
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val RESOURCE_NAME = Regex("[a-z][a-z0-9_]*")

        /** The face's strings that are the phone's own words, by the phone's key in `androidApp`. */
        val FROM_THE_PHONE = mapOf("watch_face_name" to "app_name", "slot_next_prayer" to "widget_small_label")

        /** The fixed slots whose sources the pickers list, by the watch app's key for that source. */
        val AS_THE_PICKERS = mapOf(
            "slot_next_prayer" to "complication_next_prayer",
            "slot_day" to "complication_day",
            "slot_dates" to "complication_dates",
        )

        /** Spec 1.3's table of the Arabic names, which Mohamed approved at M8 (6 November 2026). */
        val SPEC_ARABIC = mapOf(
            "style_arch" to "المحراب",
            "style_ring" to "الحلقة",
            "style_sun" to "الشمس",
            "style_moon" to "القمر",
            "accent_amber" to "كهرماني",
            "accent_pearl" to "لؤلؤي",
            "accent_sage" to "ميرمية",
            "accent_night" to "ليلي",
        )
    }
}
