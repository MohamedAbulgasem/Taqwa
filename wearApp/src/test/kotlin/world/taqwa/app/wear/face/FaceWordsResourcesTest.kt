package world.taqwa.app.wear.face

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element
import world.taqwa.app.watch.WatchFaceWords
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.complication.TestXml

/**
 * The watch's own face phrases (`face_*`, before any feed) are the phrases the phone sends in the
 * feed (`WatchWords.face`), so the face screen says the same before and after its first feed. The
 * committed feeds hold the phone's words in English (the sample), Arabic (the Saudi sample) and
 * Urdu (the phone's Cape Town golden feed).
 */
class FaceWordsResourcesTest {

    private val res = File(System.getProperty("taqwa.wearRes") ?: "src/main/res")

    @Test
    fun theWatchsOwnFacePhrasesAreThePhones() {
        val feeds = mapOf(
            "values" to TestFeeds.sample,
            "values-ar" to TestFeeds.other("ar-sa"),
            "values-ur" to TestFeeds.phone("capetown-ur24"),
        )
        for ((folder, feed) in feeds) {
            assertEquals(feed.ui.face, own(folder), "$folder's face_* are the feed's ui.face")
        }
    }

    private fun own(folder: String): WatchFaceWords {
        val root = TestXml.parse(File(res, "$folder/strings.xml"))
        val list = root.getElementsByTagName("string")
        val strings = (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
        return WatchFaceWords(
            add = strings.getValue("face_add"),
            about = strings.getValue("face_about"),
            active = strings.getValue("face_active"),
            chooseIt = strings.getValue("face_choose_it"),
            failed = strings.getValue("face_failed"),
        )
    }
}
