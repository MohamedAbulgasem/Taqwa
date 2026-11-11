package world.taqwa.app.wear.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import world.taqwa.app.wear.complication.TestXml

/**
 * What the watch app asks of the watch, read from its manifest (spec 2.1 and plan 3's Global
 * Constraints): exactly what wearApp/permissions.txt lists as the manifest's, the one list of them
 * (plan 5 Task 4), and nothing that file may never hold. It never asks for the network or for where
 * it is; the magnetometer needs no permission. And the app is opened
 * on its own screens, not plan 1's status screen, starting on the splash: the launcher icon itself,
 * a 48 dp disc on black (Google's Wear app quality item WO-V15 and its branded launch guide, plan 5
 * Task 3), which androidx.core's splash screen draws on Wear OS 3, before the system has one of its
 * own.
 */
class WearManifestTest {

    private val manifest: Element by lazy { TestXml.parse(TestXml.file("taqwa.wearManifest", "src/main/AndroidManifest.xml")) }

    /**
     * wearApp/permissions.txt is the one list of the watch app's permissions (plan 5 Task 3): its
     * `manifest` lines are exactly what the source manifest declares, each once, and its `library:`
     * lines what the merge adds (tools/wear-release-check.sh holds the merged manifest to the whole
     * file). Neither the manifest nor the file may ever hold spec 2.1's NEVER.
     */
    @Test
    fun theWatchAsksOnlyForThePermissionsListed() {
        val asked = (manifest.elements("uses-permission") + manifest.elements("uses-permission-sdk-23"))
            .map { it.getAttributeNS(ANDROID, "name") }
        val listed = permissionLines()

        assertEquals(asked.distinct(), asked, "each permission is declared once")
        assertEquals(
            listed.filter { it.origin == "manifest" }.map { it.name }.sorted(), asked.sorted(),
            "the manifest declares exactly the `manifest` lines of ${permissionsFile.path}",
        )
        for (never in NEVER) {
            assertFalse(never in asked, "$never in the manifest")
            assertFalse(never in listed.map { it.name }, "$never in ${permissionsFile.path}")
        }
        val launcher = manifest.elements("activity").filter { activity ->
            activity.elements("category").any { it.getAttributeNS(ANDROID, "name") == "android.intent.category.LAUNCHER" }
        }
        assertEquals(listOf(".app.WearMainActivity"), launcher.map { it.getAttributeNS(ANDROID, "name") })
    }

    /** Every line of the file that is not a comment is `<name> <origin> # <reason>`, and names a permission once. */
    @Test
    fun everyListedPermissionSaysWhereItComesFromAndWhy() {
        val lines = permissionsFile.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        assertTrue(lines.isNotEmpty(), "${permissionsFile.path} lists nothing")
        for (line in lines) {
            assertTrue(LINE.matches(line), "not '<name> <manifest|library:group:artifact> # <reason>': $line")
        }
        val names = permissionLines().map { it.name }
        assertEquals(names.distinct(), names, "each permission is listed once")
    }

    private data class Listed(val name: String, val origin: String)

    private val permissionsFile = File(System.getProperty("taqwa.wearPermissions") ?: "permissions.txt")

    private fun permissionLines(): List<Listed> {
        assertTrue(permissionsFile.isFile, "no file at ${permissionsFile.absolutePath}")
        return permissionsFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { line -> line.split(Regex("\\s+")).let { Listed(it[0], it.getOrElse(1) { "" }) } }
    }

    @Test
    fun theLauncherIsTheAppsOwnScreens() {
        val activities = manifest.elements("activity")
        val launchers = activities.filter { activity ->
            activity.elements("intent-filter").any { filter ->
                filter.elements("action").any { it.getAttributeNS(ANDROID, "name") == "android.intent.action.MAIN" } &&
                    filter.elements("category").any { it.getAttributeNS(ANDROID, "name") == "android.intent.category.LAUNCHER" }
            }
        }

        assertEquals(listOf(".app.WearMainActivity"), launchers.map { it.getAttributeNS(ANDROID, "name") })
        assertEquals("", launchers.single().getAttributeNS(ANDROID, "taskAffinity"), "one entry in the recents list")
        // Beside it, only plan 5's face screen, which no launcher shows (FaceManifestTest): no StatusActivity.
        assertEquals(listOf(".face.FaceActivity", ".app.WearMainActivity"), activities.map { it.getAttributeNS(ANDROID, "name") }, "no StatusActivity")
    }

    /**
     * The splash theme alone is not enough: the activity has to install the splash screen before
     * `super.onCreate`, or Wear OS 3, which has no system splash, never draws it, and on API 31+ the
     * splash never hands to the post-splash theme (erratum p5, the wave 7 minors).
     */
    @Test
    fun theLauncherInstallsTheSplashBeforeItsFirstFrame() {
        val source = File(System.getProperty("taqwa.wearMainActivity") ?: "src/main/kotlin/world/taqwa/app/wear/app/WearMainActivity.kt").readText()
        val onCreate = source.substringAfter("override fun onCreate(", missingDelimiterValue = "")
        assertTrue(onCreate.isNotEmpty(), "WearMainActivity has an onCreate")
        val install = onCreate.indexOf("installSplashScreen()")
        val superCall = onCreate.indexOf("super.onCreate(")
        assertTrue(install >= 0, "onCreate calls installSplashScreen()")
        assertTrue(install < superCall, "installSplashScreen() comes before super.onCreate")
    }

    @Test
    fun theLauncherStartsOnTheSplashTheme() {
        val launcher = manifest.elements("activity").single { it.getAttributeNS(ANDROID, "name") == ".app.WearMainActivity" }
        assertEquals("@style/Theme.Taqwa.Starting", launcher.getAttributeNS(ANDROID, "theme"))

        val themes = File(res, "values/themes.xml")
        assertTrue(themes.isFile, "no file at ${themes.absolutePath}")
        val styles = TestXml.parse(themes).elements("style")
        val starting = styles.single { it.getAttribute("name") == "Theme.Taqwa.Starting" }
        val items = starting.elements("item").associate { it.getAttribute("name") to it.textContent.trim() }

        // Google's theme for a round icon: Theme.SplashScreen.IconBackground and an icon background
        // colour are for an icon that is not round, and would put a second disc behind this one.
        assertEquals("Theme.SplashScreen", starting.getAttribute("parent"), "the round icon's theme")
        assertFalse("windowSplashScreenIconBackgroundColor" in items, "no disc behind the launcher icon's own")
        assertEquals("#000000", items["windowSplashScreenBackground"], "the icon on black")
        assertEquals("@drawable/splash_icon", items["windowSplashScreenAnimatedIcon"])
        val application = manifest.elements("application").single()
        assertEquals(
            application.getAttributeNS(ANDROID, "theme"), items["postSplashScreenTheme"],
            "once the splash is gone, the activity is in the theme it had before, the application's",
        )
    }

    /**
     * The splash's icon is the launcher icon itself (WO-V15: "The splash screen icon must match the app
     * launcher icon"; the branded launch guide: "a 48x48dp circular icon"), drawn as Google's Wear
     * splash page draws it: a layer-list of the launcher's mipmap at 48 dp. So it is the same white
     * disc, dark arch and amber lamp the watch's app list shows, masked round alike, and it cannot
     * drift from it.
     */
    @Test
    fun theSplashIsTheLauncherIconAt48dp() {
        val splash = TestXml.parse(File(res, "drawable/splash_icon.xml"))
        assertEquals("layer-list", splash.tagName, "the launcher icon wrapped at a size, not a drawing of its own")
        assertEquals(0, splash.getElementsByTagName("path").length, "no copy of the mark in other colours")
        val item = splash.elements("item").single()
        val application = manifest.elements("application").single()
        assertEquals(application.getAttributeNS(ANDROID, "icon"), item.getAttributeNS(ANDROID, "drawable"), "the launcher icon")
        assertEquals("48dp", item.getAttributeNS(ANDROID, "width"))
        assertEquals("48dp", item.getAttributeNS(ANDROID, "height"))
        assertEquals("center", item.getAttributeNS(ANDROID, "gravity"))

        // A round watch's launcher shows android:roundIcon, so that has to be the same drawing.
        assertEquals(
            File(res, "mipmap-anydpi/ic_launcher.xml").readText(), File(res, "mipmap-anydpi/ic_launcher_round.xml").readText(),
            "the round icon is the icon",
        )
    }

    /**
     * The launcher icon is the phone's own (plan 5 Task 3): its adaptive icon, layers and colour copied
     * byte for byte, which the watch's launcher masks to its round screen. So the splash, which is
     * this icon, is a white disc (a full-bleed colour layer, which the mask cuts round) with the dark
     * arch and its amber lamp on it. A change to these colours is a design change, the phone's too.
     */
    @Test
    fun theLauncherIconIsThePhones() {
        val application = manifest.elements("application").single()
        assertEquals("@mipmap/ic_launcher", application.getAttributeNS(ANDROID, "icon"))
        assertEquals("@mipmap/ic_launcher_round", application.getAttributeNS(ANDROID, "roundIcon"))

        val phone = File(System.getProperty("taqwa.phoneRes") ?: "../androidApp/src/main/res")
        val copies = mapOf(
            "mipmap-anydpi/ic_launcher.xml" to "mipmap-anydpi-v26/ic_launcher.xml",
            "mipmap-anydpi/ic_launcher_round.xml" to "mipmap-anydpi-v26/ic_launcher_round.xml",
            "drawable/ic_launcher_foreground.xml" to "drawable/ic_launcher_foreground.xml",
            "drawable/ic_launcher_monochrome.xml" to "drawable/ic_launcher_monochrome.xml",
            "values/ic_launcher_background.xml" to "values/ic_launcher_background.xml",
        )
        for ((watchPath, phonePath) in copies) {
            val watch = File(res, watchPath)
            assertTrue(watch.isFile, "no $watchPath")
            assertEquals(File(phone, phonePath).readText(), watch.readText(), "$watchPath is the phone's $phonePath")
        }

        val icon = TestXml.parse(File(res, "mipmap-anydpi/ic_launcher.xml"))
        assertEquals("adaptive-icon", icon.tagName, "an icon the watch's mask cuts round")
        assertEquals("@color/ic_launcher_background", icon.elements("background").single().getAttributeNS(ANDROID, "drawable"))
        assertEquals("@drawable/ic_launcher_foreground", icon.elements("foreground").single().getAttributeNS(ANDROID, "drawable"))
        val disc = TestXml.parse(File(res, "values/ic_launcher_background.xml")).elements("color")
            .single { it.getAttribute("name") == "ic_launcher_background" }.textContent.trim()
        assertEquals("#FFFFFF", disc, "the white disc")

        val mark = TestXml.parse(File(res, "drawable/ic_launcher_foreground.xml")).elements("path")
        assertEquals(2, mark.size, "the arch and its lamp")
        assertEquals("#16160F", mark[0].getAttributeNS(ANDROID, "strokeColor"), "the dark arch")
        assertEquals("#E3A21C", mark[1].getAttributeNS(ANDROID, "fillColor"), "the amber lamp")
    }

    private fun Element.elements(tag: String): List<Element> = getElementsByTagName(tag).elements()

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) }.filterIsInstance<Element>()

    private val res = File(System.getProperty("taqwa.wearRes") ?: "src/main/res")

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val LINE = Regex("""^[A-Za-z0-9_.]+ (manifest|library:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+) # \S.*$""")
        val NEVER = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.BODY_SENSORS",
        )
    }
}
