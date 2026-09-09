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
}
