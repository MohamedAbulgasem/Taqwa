package world.taqwa.app.notifications

import world.taqwa.app.recitation.RecitationService
import world.taqwa.app.recitation.SurahDownloadWorker
import world.taqwa.app.widget.WidgetRefreshScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every fixed number the Android side gives a `PendingIntent` or a notification: apart from each
 * other, and the ones the prayer alarms could reach beyond their reach (B22).
 *
 * [AndroidNotificationScheduler] numbers the alarms 1, 2, 3, … and never starts again, and each
 * number is also that prayer's notification id and the request code of its tap, which is the
 * launcher intent. A fixed number that shares either with them, the launcher intent or the app's
 * untagged notifications, collides on the day the plan reaches it: recitation's tap at 31 and
 * Media3's default notification id, 1001, both did. The sequential numbers are never fixed, so
 * they are left out of the comparison by construction, and what keeps the fixed ones clear of
 * them is [SEQUENTIAL_REACH].
 *
 * Every number here is a `const val`, inlined at compile time, so no Android class is loaded.
 */
class RequestCodesAndNotificationIdsTest {

    @Test
    fun `recitation's tap and media notification are beyond the prayer alarms' reach`() {
        assertTrue(
            RecitationService.REQUEST_OPEN_PLAYING >= SEQUENTIAL_REACH,
            "REQUEST_OPEN_PLAYING ${RecitationService.REQUEST_OPEN_PLAYING} is a prayer tap's request code one day",
        )
        assertTrue(
            RecitationService.MEDIA_NOTIFICATION_ID >= SEQUENTIAL_REACH,
            "MEDIA_NOTIFICATION_ID ${RecitationService.MEDIA_NOTIFICATION_ID} is a prayer notification's id one day",
        )
    }

    @Test
    fun `the download notification is beyond the prayer alarms' reach`() {
        assertTrue(
            SurahDownloadWorker.NOTIFICATION_ID >= SEQUENTIAL_REACH,
            "NOTIFICATION_ID ${SurahDownloadWorker.NOTIFICATION_ID} is a prayer notification's id one day",
        )
    }

    @Test
    fun `every fixed request code and notification id is used once`() {
        val fixed = mapOf(
            "RecitationService.REQUEST_OPEN_PLAYING" to RecitationService.REQUEST_OPEN_PLAYING,
            "RecitationService.MEDIA_NOTIFICATION_ID" to RecitationService.MEDIA_NOTIFICATION_ID,
            "SurahDownloadWorker.NOTIFICATION_ID" to SurahDownloadWorker.NOTIFICATION_ID,
            // Inside the prayer alarms' reach, and safe there: a broadcast to the widget refresh
            // receiver under its own action, which `Intent.filterEquals` never takes for a prayer
            // alarm's. Kept apart from the other fixed numbers all the same.
            "WidgetRefreshScheduler.REQUEST_CODE" to WidgetRefreshScheduler.REQUEST_CODE,
        )
        // androidApp's AyahWidgetScheduler (0x7A9B) is the one fixed number out of this source
        // set's sight; it is a broadcast to the ayah widget's own receiver, as the one above is.

        val usedTwice = fixed.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }

        assertEquals(emptyMap(), usedTwice)
    }

    private companion object {
        /**
         * How far the sequential numbers can get on a phone: they grow by one per new notification
         * id, 5 to 13 a day, and by at most a plan's 64 when a switch turned off is turned back
         * on. At 13 a day 700,000 is almost 150 years away, and still about 25 with a whole plan
         * re-armed every day on top.
         */
        const val SEQUENTIAL_REACH = 700_000
    }
}
