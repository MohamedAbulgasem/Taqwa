package world.taqwa.app.notifications

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a prayer or adhkar notification's tap starts the app ([NOTIFICATION_TAP_FLAGS]): handed to
 * the `MainActivity` already running, never a fresh one in its place (B18).
 *
 * The launch flags are what decide it, and they are all this can check without a device: the
 * `PendingIntent` itself needs a `Context`, and there is no Robolectric here. They are compile-time
 * constants, so no Android class is loaded.
 */
class NotificationTapFlagsTest {

    private fun has(flag: Int) = (NOTIFICATION_TAP_FLAGS and flag) != 0

    @Test
    fun `a tap reaches the running activity instead of recreating it`() {
        // MainActivity is `standard`, and CLEAR_TOP without SINGLE_TOP finishes the running
        // instance and starts a new one: the reader's place lost, an adhkar tap on Prayer instead.
        assertTrue(has(Intent.FLAG_ACTIVITY_SINGLE_TOP), "no SINGLE_TOP: the tap recreates MainActivity")
        assertTrue(has(Intent.FLAG_ACTIVITY_CLEAR_TOP), "no CLEAR_TOP: whatever sits above MainActivity stays")
        assertTrue(has(Intent.FLAG_ACTIVITY_NEW_TASK), "no NEW_TASK: a tap with Taqwa closed has no task")
    }

    @Test
    fun `a tap starts the app with the ayah widget's three flags and nothing more`() {
        // TaqwaAyahGlanceWidget met the identical race first and settled on these three. A flag
        // beyond them, CLEAR_TASK say, would throw the running activity away all over again.
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            NOTIFICATION_TAP_FLAGS,
        )
    }
}
