package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which states answer a second request for the same surah. iOS starts no second task for a surah
 * on its way, as Android's unique work with `KEEP` starts no second worker: on a slow connection
 * two tasks shared the bandwidth and the bar jumped between their counts (18 Oct 2026, the owner's
 * iPhone 12 on a slow Wi-Fi).
 */
class DownloadStateTest {

    @Test
    fun aSurahAcceptedTransferringOrVerifyingIsOnItsWay() {
        assertTrue(DownloadState.Queued.isOnItsWay)
        assertTrue(DownloadState.Downloading(bytes = 1_000L, total = 100_000L).isOnItsWay)
        assertTrue(DownloadState.Verifying.isOnItsWay)
    }

    @Test
    fun aFailedOrUnaskedSurahIsNotOnItsWaySoAskingAgainStartsIt() {
        DownloadFailure.entries.forEach { assertFalse(DownloadState.Failed(it).isOnItsWay, "Failed($it)") }
        val none: DownloadState? = null
        assertFalse(none.isOnItsWay)
    }
}
