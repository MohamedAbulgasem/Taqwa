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
        for (kind in NotificationKind.entries.filterNot { it.isAdhkar }) {
            assertNull(AdhkarReminderDelivery.tagFor(kind, "FAJR-PRAYER-2026-10-11"), "$kind")
        }
    }

    // Duha and Iqama spec §3.4: the receiver reads windowEnd for every kind, so the same rules drop
    // a Duha delivered after its end (ten minutes before noon) and an iqama delivered more than
    // ten minutes after it was due, and time out one that arrives in time.
    @Test
    fun `a late Duha or iqama reminder is dropped at its window end and times out there`() {
        val duhaEnd = Instant.parse("2026-10-25T10:41:00Z")
        assertEquals(35.minutes, AdhkarReminderDelivery.timeout(duhaEnd - 35.minutes, duhaEnd))
        assertFalse(AdhkarReminderDelivery.shouldPost(duhaEnd, duhaEnd))
        assertFalse(AdhkarReminderDelivery.shouldPost(duhaEnd + 20.minutes, duhaEnd))
        assertNull(AdhkarReminderDelivery.timeout(duhaEnd + 20.minutes, duhaEnd))

        val iqamaAt = Instant.parse("2026-10-25T15:25:00Z")
        val iqamaEnd = iqamaAt + 10.minutes
        assertEquals(10.minutes, AdhkarReminderDelivery.timeout(iqamaAt, iqamaEnd))
        assertEquals(1.minutes, AdhkarReminderDelivery.timeout(iqamaAt + 9.minutes, iqamaEnd))
        assertFalse(AdhkarReminderDelivery.shouldPost(iqamaAt + 10.minutes, iqamaEnd))
        assertNull(AdhkarReminderDelivery.timeout(iqamaAt + 40.minutes, iqamaEnd))
    }
}
