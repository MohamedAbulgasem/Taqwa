package world.taqwa.app.wear.link

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.wear.complication.TestXml

/**
 * What Play services reads to wake the watch's link, held to the contract (plan 2 Task 6, as the
 * phone's `PhoneWatchListenerServiceTest` holds the phone's, Ruling 54 item 2): a path or a
 * capability renamed on one side only would leave the link silent, and neither file is code that
 * a build would fail on.
 */
class LinkManifestTest {

    private val manifest: Element by lazy { TestXml.parse(TestXml.file("taqwa.wearManifest", "src/main/AndroidManifest.xml")) }

    @Test
    fun theManifestWakesTheListenerForTheFeedAndThePhonesCapability() {
        val service = manifest.getElementsByTagName("service").elements()
            .single { it.getAttributeNS(ANDROID, "name") == ".link.FeedListenerService" }
        assertEquals("true", service.getAttributeNS(ANDROID, "exported"), "Play services binds to it")
        assertEquals("", service.getAttributeNS(ANDROID, "permission"))

        val wakes = service.children("intent-filter").map { filter ->
            val action = filter.children("action").single().getAttributeNS(ANDROID, "name")
            val data = filter.children("data").single()
            assertEquals("wear", data.getAttributeNS(ANDROID, "scheme"), action)
            assertEquals("*", data.getAttributeNS(ANDROID, "host"), action)
            val attributes = (0 until data.attributes.length).map { data.attributes.item(it) }
                .filter { it.namespaceURI == ANDROID }.map { it.localName }.toSet()
            Triple(action, attributes - setOf("scheme", "host"), data.getAttributeNS(ANDROID, "path") + data.getAttributeNS(ANDROID, "pathPrefix"))
        }
        assertEquals(
            listOf(
                // A prefix, because the delivery rides after it: /taqwa/feed/<origin>/<seq>/<fingerprint>.
                Triple(MessageClient.ACTION_MESSAGE_RECEIVED, setOf("pathPrefix"), WatchLinkContract.FEED_PATH),
                Triple(CapabilityClient.ACTION_CAPABILITY_CHANGED, setOf("path"), "/${WatchLinkContract.PHONE_CAPABILITY}"),
            ),
            wakes,
        )
    }

    @Test
    fun theDailyNudgeIsTheAppsAlone() {
        val receiver = manifest.getElementsByTagName("receiver").elements()
            .single { it.getAttributeNS(ANDROID, "name") == ".link.DailyNudgeReceiver" }
        assertEquals("false", receiver.getAttributeNS(ANDROID, "exported"), "only the app's own alarm reaches it")
        assertEquals(emptyList(), receiver.children("intent-filter"), "an explicit PendingIntent needs no filter")
    }

    @Test
    fun theWatchsCapabilityIsTheContractsAndKept() {
        val wear = TestXml.parse(TestXml.file("taqwa.wearCapabilities", "src/main/res/values/wear.xml"))
        val capabilities = wear.getElementsByTagName("string-array").elements()
            .single { it.getAttribute("name") == "android_wear_capabilities" }
        assertEquals(listOf(WatchLinkContract.WATCH_CAPABILITY), capabilities.children("item").map { it.textContent.trim() })
        assertEquals("@array/android_wear_capabilities", wear.getAttributeNS(TOOLS, "keep"))
    }

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) }.filterIsInstance<Element>()

    private fun Element.children(tag: String): List<Element> = childNodes.elements().filter { it.tagName == tag }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val TOOLS = "http://schemas.android.com/tools"
    }
}
