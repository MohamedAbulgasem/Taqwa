package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The offline rule ([isOfflineVoice]) held to the voices Google's engine actually lists, as a
 * probe measured them on the emulator and the S23. A rule that counted the timeout and retry
 * settings as a network voice's mark once ruled every one of these out, and every language read
 * as Unsupported on both phones.
 */
class OfflineVoiceTest {

    /** On every voice Google lists, local and network alike. */
    private val googleSettings = setOf("networkRetriesCount", "networkTimeoutMs")

    @Test
    fun googlesLocalVoiceIsOffline() {
        // en-us-x-iob-local
        assertTrue(isOfflineVoice(networkRequired = false, features = googleSettings))
    }

    @Test
    fun googlesNetworkTwinIsNot() {
        // en-us-x-iob-network
        assertFalse(isOfflineVoice(networkRequired = true, features = googleSettings))
    }

    @Test
    fun theLanguageAliasSaysItIsOffline() {
        // en-US-language: kept, and left to VoicePick's -local tie-break to lose to its local twin.
        assertTrue(isOfflineVoice(networkRequired = false, features = googleSettings + "legacySetLanguageVoice"))
    }

    @Test
    fun aVoiceNotYetDownloadedIsStillAnOfflineVoice() {
        // Missing, not Unsupported: VoicePick.downloadable counts offline voices that are not installed.
        assertTrue(isOfflineVoice(networkRequired = false, features = googleSettings + "notInstalled"))
    }

    @Test
    fun networkSynthesisIsNeverOffline() {
        assertFalse(isOfflineVoice(networkRequired = false, features = setOf("networkTts")))
    }
}
