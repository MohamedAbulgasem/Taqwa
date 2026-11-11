package world.taqwa.app.wear.face

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import world.taqwa.app.wear.complication.TestXml

/**
 * The Taqwa face rides inside the watch app as Google's default watch face (plan 5 Task 4, the
 * Lead's call 1): the app may install and update a face through Watch Face Push, and its manifest
 * names the validation token of the face it bundles, so that the system installs that face into
 * Taqwa's one slot when the app is installed. The names are Google's ("Watch Face Push: Wear OS app
 * configuration"). The token itself is made at build time from the very face APK bundled
 * (FaceBundleTask, wearApp/build.gradle.kts), so the manifest names it by its resource, never by value.
 * Neither line lets the watch reach the network or learn where it is.
 */
class FaceManifestTest {

    private val manifest: Element by lazy { TestXml.parse(TestXml.file("taqwa.wearManifest", "src/main/AndroidManifest.xml")) }

    @Test
    fun theAppMayPushTheTaqwaFace() {
        assertTrue(PUSH in asked(), "the manifest asks for $PUSH")
    }

    @Test
    fun theDefaultFacesTokenIsTheBundledOnes() {
        val application = manifest.elements("application").single()
        val tokens = application.elements("meta-data").filter { it.getAttributeNS(ANDROID, "name") == TOKEN }

        assertEquals(1, tokens.size, "one $TOKEN meta-data on the application")
        assertEquals("@string/default_wf_token", tokens.single().getAttributeNS(ANDROID, "value"))
    }

    @Test
    fun pushingTheFaceNeedsNoNetworkAndNoLocation() {
        val asked = asked()
        for (never in listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        )) {
            assertFalse(never in asked, never)
        }
    }

    @Test
    fun theFaceMayBeMadeActiveOnTheWearersTap() {
        assertTrue(SET_ACTIVE in asked(), "the manifest asks for $SET_ACTIVE")
    }

    /**
     * Erratum p5: the Push library's own manifest requires the system library wear-sdk, which no
     * watch before Wear OS 6 has. Required, the merged manifest would keep Wear OS 3 to 5 watches
     * from installing the app at all, and Play would hide it from them. So the app's manifest
     * replaces it with not required, and AndroidFacePushPort asks for the library only where the
     * system has it. The library's minSdk 33 is overridden for the same watches.
     */
    @Test
    fun watchesBeforeWearOsSixStillInstallTheApp() {
        val libraries = manifest.elements("application").single().elements("uses-library")
            .filter { it.getAttributeNS(ANDROID, "name") == "wear-sdk" }
        assertEquals(1, libraries.size, "one uses-library for wear-sdk")
        assertEquals("false", libraries.single().getAttributeNS(ANDROID, "required"))
        assertEquals("android:required", libraries.single().getAttributeNS(TOOLS, "replace"))

        val overridden = manifest.elements("uses-sdk").singleOrNull()?.getAttributeNS(TOOLS, "overrideLibrary").orEmpty()
        assertTrue("androidx.wear.watchfacepush" in overridden.split(',').map { it.trim() }, "uses-sdk overrides the library's minSdk: '$overridden'")
    }

    @Test
    fun theFaceScreenOpensFromItsLink() {
        val activity = component("activity", ".face.FaceActivity")
        assertEquals("true", activity.getAttributeNS(ANDROID, "exported"), "the phone's row opens it")
        val filters = activity.elements("intent-filter")
        val link = filters.singleOrNull { filter ->
            filter.elements("data").any { it.getAttributeNS(ANDROID, "scheme") == "taqwa" && it.getAttributeNS(ANDROID, "host") == "face" }
        }
        checkNotNull(link) { "a filter for taqwa://face" }
        assertEquals(listOf("android.intent.action.VIEW"), link.elements("action").map { it.getAttributeNS(ANDROID, "name") })
        assertEquals(
            setOf("android.intent.category.BROWSABLE", "android.intent.category.DEFAULT"),
            link.elements("category").map { it.getAttributeNS(ANDROID, "name") }.toSet(),
        )
    }

    @Test
    fun theFaceIsUpdatedAfterEveryAppUpdate() {
        // Google: a bundled face is not updated with the app; the app listens for MY_PACKAGE_REPLACED.
        val receiver = component("receiver", ".face.FaceUpdateReceiver")
        assertEquals("false", receiver.getAttributeNS(ANDROID, "exported"), "only the system sends it")
        val actions = receiver.elements("intent-filter").flatMap { it.elements("action") }.map { it.getAttributeNS(ANDROID, "name") }
        assertEquals(listOf("android.intent.action.MY_PACKAGE_REPLACED"), actions)
    }

    private fun component(tag: String, name: String): Element =
        manifest.elements("application").single().elements(tag).singleOrNull { it.getAttributeNS(ANDROID, "name") == name }
            ?: error("no $tag $name in the manifest")

    private fun asked(): List<String> = (manifest.elements("uses-permission") + manifest.elements("uses-permission-sdk-23"))
        .map { it.getAttributeNS(ANDROID, "name") }

    private fun Element.elements(tag: String): List<Element> = getElementsByTagName(tag).elements()

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) }.filterIsInstance<Element>()

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val TOOLS = "http://schemas.android.com/tools"
        const val PUSH = "com.google.wear.permission.PUSH_WATCH_FACES"
        const val SET_ACTIVE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"
        const val TOKEN = "com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN"
    }
}
