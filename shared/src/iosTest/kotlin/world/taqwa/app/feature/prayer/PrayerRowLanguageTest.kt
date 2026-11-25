package world.taqwa.app.feature.prayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import platform.Foundation.NSLocale
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TimelineRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The prayer row's join through its call site, `PrayerTimeline.kt`'s `uiLanguage()` (Ruling 75 item
 * 3): `CountdownSpeechTest` holds `prayerRowDescription` with a language handed to it, and only
 * here does a row composed under Arabic hand it the language its strings resolved in, so a row told
 * `UiLanguage.ENGLISH` fails. The time is the test's own, so only the name and the comma come from
 * the language.
 *
 * Each case swaps `AppleLanguages` in this process's argument domain ([inLanguage], shared with
 * the watch card's suites), where `-AppleLanguages (ar)` on a command line lands.
 * `NSLocale.preferredLanguages`, and so the resources' language, follows it on iOS (the name in
 * each script shows it took), and it is never written down, so it ends with
 * the case or at worst with the process. Not `NSUserDefaults.setObject`: that goes to the
 * preferences file named for this test binary (`test.kexe.plist`, the same for all three modules
 * on the simulator), its removal can miss the disk even with `synchronize()`, and the next test
 * process then starts in that language. Not `registerDefaults`: the simulator's global domain says
 * en-US and outranks it.
 */
@OptIn(ExperimentalTestApi::class)
class PrayerRowLanguageTest {

    private val asr = TimelineRow(Prayer.ASR, Instant.parse("2026-10-16T13:45:00Z"), PrayerStatus.UPCOMING)

    // Not TaqwaTheme: its system-bar effects crash the simulator test process.
    @Composable
    private fun Timeline() {
        CompositionLocalProvider(LocalTaqwaColors provides LightColors) {
            PrayerTimeline(rows = listOf(asr), formatTime = { "16:10" })
        }
    }

    private fun theRowSays(tag: String, description: String) = inLanguage(tag) {
        runComposeUiTest {
            setContent { Timeline() }
            waitForIdle()
            onAllNodesWithContentDescription(description).assertCountEquals(1)
        }
    }

    @Test
    fun anArabicRowIsJoinedByTheArabicComma() = theRowSays("ar", "العصر، 16:10")

    @Test
    fun anUrduRowIsJoinedByTheArabicComma() = theRowSays("ur", "عصر، 16:10")

    @Test
    fun anEnglishRowKeepsTheLatinComma() = theRowSays("en", "Asr, 16:10")

    /** In letters of its own but written left to right: the comma follows the direction. */
    @Test
    fun aBengaliRowKeepsTheLatinComma() = theRowSays("bn", "আসর, 16:10")

    /**
     * The swap stays in this process: the old one, with `setObject`, is red here on the first read.
     * Red with this file as it is means an earlier run left the key behind, and this binary's other
     * suites may be running in that language: look in `test.kexe.plist`.
     */
    @Test
    fun theSwapWritesNothingToThePreferences() = inLanguage("ar") {
        val preferences = NSUserDefaults.standardUserDefaults
            .persistentDomainForName(NSProcessInfo.processInfo.processName)
        assertNull(preferences?.get(APPLE_LANGUAGES), "$APPLE_LANGUAGES is in this process's preferences")
    }

    /**
     * Inside its case the swap holds, and after it the process is back on the languages it had. Two
     * languages, so a swap left behind by the case before cannot pass for the languages it had.
     */
    @Test
    fun theSwapEndsWithItsCase() {
        val before = NSLocale.preferredLanguages
        for (tag in listOf("ar", "ur")) {
            inLanguage(tag) { assertEquals(tag, NSLocale.preferredLanguages.first()) }
            assertEquals(before, NSLocale.preferredLanguages)
        }
    }
}
