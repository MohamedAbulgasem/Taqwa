package world.taqwa.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchRequestsTest {

    @Test fun openAyahSetsThePendingReferenceAndConsumeClearsIt() {
        assertNull(LaunchRequests.pendingAyah.value)

        LaunchRequests.openAyah(18, 46)
        assertEquals(18 to 46, LaunchRequests.pendingAyah.value)

        LaunchRequests.consume()
        assertNull(LaunchRequests.pendingAyah.value)
    }

    @Test fun openAyahAgainOverwritesAPendingRequest() {
        LaunchRequests.openAyah(2, 255)
        LaunchRequests.openAyah(36, 1)
        assertEquals(36 to 1, LaunchRequests.pendingAyah.value)
        LaunchRequests.consume()
    }


    /**
     * The Android launcher activity is exported, so any app on the phone can start it with
     * `open_surah=999`; a surah the database does not have used to reach the reader and throw
     * inside its coroutine. The bounds are the Quran's own: 114 surahs, and no surah longer than
     * al-Baqarah's 286 ayahs. Enforced here rather than on each platform so iOS gets the same
     * rule (it only checked `ayah >= 1`).
     */
    @Test fun openAyahIgnoresAReferenceOutsideTheQuran() {
        LaunchRequests.consume()
        LaunchRequests.openAyah(0, 1)
        LaunchRequests.openAyah(115, 1)
        LaunchRequests.openAyah(2, 0)
        LaunchRequests.openAyah(2, 287)
        assertNull(LaunchRequests.pendingAyah.value)

        LaunchRequests.openAyah(114, 6)
        assertEquals(114 to 6, LaunchRequests.pendingAyah.value)
        LaunchRequests.openAyah(2, 286)
        assertEquals(2 to 286, LaunchRequests.pendingAyah.value)
        LaunchRequests.consume()
    }
}
