package world.taqwa.app.nav

import kotlinx.datetime.LocalDate
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.notifications.adhkarNotificationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource
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

    @Test fun openPlayingIsPendingUntilConsumed() {
        LaunchRequests.consumePlaying()
        assertEquals(false, LaunchRequests.pendingPlaying.value)
        LaunchRequests.openPlaying()
        assertEquals(true, LaunchRequests.pendingPlaying.value)
        LaunchRequests.consumePlaying()
        assertEquals(false, LaunchRequests.pendingPlaying.value)
    }

    @Test
    fun `an adhkar reminder tap is pending until consumed`() {
        LaunchRequests.consumeAdhkar()
        assertNull(LaunchRequests.pendingAdhkar.value)
        LaunchRequests.openAdhkar(AdhkarSet.EVENING)
        assertEquals(AdhkarSet.EVENING, LaunchRequests.pendingAdhkar.value)
        LaunchRequests.consumeAdhkar()
        assertNull(LaunchRequests.pendingAdhkar.value)
    }

    @Test
    fun `the android extra names the set and nothing else gets through`() {
        LaunchRequests.consumeAdhkar()
        assertEquals("morning", LaunchRequests.adhkarExtra(AdhkarSet.MORNING))
        assertEquals("evening", LaunchRequests.adhkarExtra(AdhkarSet.EVENING))
        // The launcher activity is exported: any other value is ignored, as an out-of-range ayah is.
        for (value in listOf(null, "", "MORNING", "night", "morning ")) {
            LaunchRequests.openAdhkarFromExtra(value)
            assertNull(LaunchRequests.pendingAdhkar.value, "$value")
        }
        for (set in AdhkarSet.entries) {
            LaunchRequests.openAdhkarFromExtra(LaunchRequests.adhkarExtra(set))
            assertEquals(set, LaunchRequests.pendingAdhkar.value)
        }
        LaunchRequests.consumeAdhkar()
    }

    @Test
    fun `a notification id opens its adhkar set and any other id opens nothing`() {
        LaunchRequests.consumeAdhkar()
        val date = LocalDate(2026, 10, 11)
        for (set in AdhkarSet.entries) {
            LaunchRequests.openAdhkarForNotification(adhkarNotificationId(set, date))
            assertEquals(set, LaunchRequests.pendingAdhkar.value)
            LaunchRequests.consumeAdhkar()
        }
        for (id in listOf("FAJR-PRAYER-$date", "ASR-REMINDER-$date", "TAHAJJUD-$date", "ADHKAR-", "")) {
            LaunchRequests.openAdhkarForNotification(id)
            assertNull(LaunchRequests.pendingAdhkar.value, id)
        }
    }

    /**
     * Every request that moves the screen is counted, consumed or not, so the app coming back to
     * the front can tell whether one arrived since it left: the iOS return to a playing recitation
     * yields to a widget tap or an adhkar reminder that brought the app back.
     */
    @Test
    fun `every request that moves the screen is counted`() {
        val before = LaunchRequests.count
        LaunchRequests.openAyah(18, 46)
        LaunchRequests.consume()
        LaunchRequests.openAdhkar(AdhkarSet.MORNING)
        LaunchRequests.consumeAdhkar()
        LaunchRequests.openPlaying()
        LaunchRequests.consumePlaying()
        LaunchRequests.openScreen("prayer")
        LaunchRequests.consumeScreen()
        assertEquals(before + 4, LaunchRequests.count)
    }

    /** A request dropped as not one of the app's own moves nothing, so it is not counted either. */
    @Test
    fun `a request that is dropped is not counted`() {
        val before = LaunchRequests.count
        LaunchRequests.openAyah(115, 1)
        LaunchRequests.openAdhkarFromExtra("night")
        LaunchRequests.openAdhkarForNotification("FAJR-PRAYER-2026-10-17")
        assertEquals(before, LaunchRequests.count)
    }

    /**
     * The app sending the person elsewhere itself (a link, a mail, iOS Settings) is the reason it
     * leaves the front if it leaves a moment later, and only for that leave.
     */
    @Test
    fun `a link the app opened is the reason for the next leave and only that one`() {
        LaunchRequests.openingLink()
        assertTrue(LaunchRequests.leavingForOwnLink())
        assertFalse(LaunchRequests.leavingForOwnLink())
    }

    @Test
    fun `a link that did not open is no reason to leave`() {
        LaunchRequests.openingLink()
        LaunchRequests.linkDidNotOpen()
        assertFalse(LaunchRequests.leavingForOwnLink())
    }

    /** A link that opened nothing without saying so is forgotten: the next real leave is not its. */
    @Test
    fun `a leave long after the link was opened is not for it`() {
        val clock = TestTimeSource()
        LaunchRequests.clock = clock
        try {
            LaunchRequests.openingLink()
            clock += 6.seconds
            assertFalse(LaunchRequests.leavingForOwnLink())
        } finally {
            LaunchRequests.clock = TimeSource.Monotonic
        }
    }
}
