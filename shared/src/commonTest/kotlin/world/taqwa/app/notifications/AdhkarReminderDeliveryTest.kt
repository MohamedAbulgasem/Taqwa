package world.taqwa.app.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What the Android receiver does with an adhkar reminder its inexact alarm delivered late (adhkar
 * spec §5): nothing once the window has closed, and otherwise a notification that leaves the shade
 * when it does. The receiver has no host test, so the decision lives here.
 */
class AdhkarReminderDeliveryTest {

    private val sunrise = Instant.parse("2026-10-11T05:41:00Z")

    @Test
    fun `a reminder inside its window is posted until the window closes`() {
        val now = sunrise - 25.minutes
        assertTrue(AdhkarReminderDelivery.shouldPost(now, sunrise))
        assertEquals(25.minutes, AdhkarReminderDelivery.timeout(now, sunrise))
    }

    @Test
    fun `a reminder delivered at or after the end of its window is dropped`() {
        assertFalse(AdhkarReminderDelivery.shouldPost(sunrise, sunrise))
        assertFalse(AdhkarReminderDelivery.shouldPost(sunrise + 1.seconds, sunrise))
        assertFalse(AdhkarReminderDelivery.shouldPost(sunrise + 40.minutes, sunrise))
        assertNull(AdhkarReminderDelivery.timeout(sunrise, sunrise))
        assertNull(AdhkarReminderDelivery.timeout(sunrise + 40.minutes, sunrise))
    }

    @Test
    fun `a second before the end still posts for that second`() {
        val now = sunrise - 1.seconds
        assertTrue(AdhkarReminderDelivery.shouldPost(now, sunrise))
        assertEquals(1.seconds, AdhkarReminderDelivery.timeout(now, sunrise))
    }

    @Test
    fun `only the adhkar kinds are posted under their id as the tag`() {
        assertEquals("ADHKAR-MORNING-2026-10-11", AdhkarReminderDelivery.tagFor(NotificationKind.ADHKAR_MORNING, "ADHKAR-MORNING-2026-10-11"))
        assertEquals("ADHKAR-EVENING-2026-10-11", AdhkarReminderDelivery.tagFor(NotificationKind.ADHKAR_EVENING, "ADHKAR-EVENING-2026-10-11"))
        // Every other kind posts exactly as it always has, untagged.
        for (kind in listOf(NotificationKind.PRAYER, NotificationKind.REMINDER, NotificationKind.TAHAJJUD)) {
            assertNull(AdhkarReminderDelivery.tagFor(kind, "FAJR-PRAYER-2026-10-11"), "$kind")
        }
    }
}
