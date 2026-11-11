package world.taqwa.app.wear.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import world.taqwa.app.watch.WatchDelivery

/**
 * What the watch's release log lines say of a delivery (Ruling 52 item 4, and Ruling 39): its
 * number and fingerprint alone, never its origin, which is the phone install's one random number
 * and the same in every feed of that phone. No line names the place or the bearing either: the
 * lines are built from deliveries, results and reasons alone.
 */
class FeedLogTest {

    private val d = WatchDelivery("0123456789abcdef", 5, "fedcba9876543210")

    @Test
    fun aLogLineNamesADeliveryByItsNumberAndFingerprintAlone() {
        assertEquals("5/fedcba9876543210", logged(d))
        assertFalse(d.origin in logged(d))
        assertEquals("none", logged(null))

        assertEquals("5/fedcba9876543210", loggedEcho(d.text), "an echo, as the acknowledgement carries it")
        assertEquals("none", loggedEcho(""), "an echo of nothing")
        for (text in listOf("garbage", d.origin, "${d.text}/more")) {
            assertEquals("unreadable", loggedEcho(text), text)
        }
    }
}
