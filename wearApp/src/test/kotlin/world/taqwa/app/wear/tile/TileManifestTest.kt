package world.taqwa.app.wear.tile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import world.taqwa.app.wear.complication.TestXml

/**
 * The Tile as the system knows it, read from the watch app's manifest (spec 2.4): a service only
 * the system binds, offered in the Tiles picker under the app's own names and its preview, and a
 * tap that brings the app's one activity forward rather than a second copy of it.
 */
class TileManifestTest {

    private val manifest: Element by lazy { TestXml.parse(TestXml.file("taqwa.wearManifest", "src/main/AndroidManifest.xml")) }

    private val tile: Element by lazy {
        manifest.elements("service").single { it.getAttributeNS(ANDROID, "name") == ".tile.TodayTileService" }
    }

    @Test
    fun onlyTheSystemBindsTheTile() {
        assertEquals("true", tile.getAttributeNS(ANDROID, "exported"))
        assertEquals("com.google.android.wearable.permission.BIND_TILE_PROVIDER", tile.getAttributeNS(ANDROID, "permission"))
        val actions = tile.elements("intent-filter").flatMap { it.elements("action") }.map { it.getAttributeNS(ANDROID, "name") }
        assertEquals(listOf("androidx.wear.tiles.action.BIND_TILE_PROVIDER"), actions)
    }

    @Test
    fun thePickerShowsTheTilesOwnNamesAndPreview() {
        assertEquals("@string/tile_label", tile.getAttributeNS(ANDROID, "label"))
        assertEquals("@string/tile_description", tile.getAttributeNS(ANDROID, "description"))
        val preview = tile.elements("meta-data").single { it.getAttributeNS(ANDROID, "name") == "androidx.wear.tiles.PREVIEW" }
        assertEquals("@drawable/tile_preview", preview.getAttributeNS(ANDROID, "resource"))
        val res = File(System.getProperty("taqwa.wearRes") ?: "src/main/res")
        assertTrue(File(res, "drawable-nodpi/tile_preview.png").isFile, "the preview is in the app's density-less drawables")
    }

    @Test
    fun aTapBringsTheOneActivityForward() {
        // The Tile opens the app on Today. With its empty task affinity every launch from outside
        // would be a task of its own, and a second WearMainActivity would hold its own tasbeeh
        // counts, which the first could write over when it came back (P3.6's carried minor).
        val activity = manifest.elements("activity").single { it.getAttributeNS(ANDROID, "name") == ".app.WearMainActivity" }

        assertEquals("singleTask", activity.getAttributeNS(ANDROID, "launchMode"))
    }

    private fun Element.elements(tag: String): List<Element> = getElementsByTagName(tag).elements()

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) }.filterIsInstance<Element>()

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
