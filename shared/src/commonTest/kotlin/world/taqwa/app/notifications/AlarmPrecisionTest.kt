package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Which entries the Android scheduler asks an exact alarm for (Duha and Iqama spec §3.4). An
 * inexact one can arrive up to an hour late, so it is kept for what has a window to wait in.
 */
class AlarmPrecisionTest {

    private val at = Instant.parse("2026-10-25T08:06:00Z")

    private fun entry(kind: NotificationKind, windowLeft: Duration? = null) = ScheduledNotification(
        id = "TEST",
        prayer = Prayer.FAJR,
        kind = kind,
        instant = at,
        sound = PrayerSound.NOTIFICATION,
        title = "",
        body = "",
        windowEnd = windowLeft?.let { at + it },
    )

    private val exact = listOf(NotificationKind.PRAYER, NotificationKind.REMINDER, NotificationKind.TAHAJJUD, NotificationKind.IQAMA)
    private val windowed = listOf(NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING, NotificationKind.DUHA_BEST, NotificationKind.DUHA_START)

    @Test
    fun `every kind is in one of the two lists`() {
        assertEquals(NotificationKind.entries.toSet(), (exact + windowed).toSet())
    }

    @Test
    fun `the prayers and their reminders and Tahajjud and the iqama ask for an exact alarm`() {
        for (kind in exact) assertTrue(AlarmPrecision.wantsExact(entry(kind)), "$kind")
        // The iqama's own ten minutes are no reason to let it arrive late.
        assertTrue(AlarmPrecision.wantsExact(entry(NotificationKind.IQAMA, 10.minutes)))
    }

    @Test
    fun `the adhkar reminder is always inexact`() {
        for (kind in listOf(NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING)) {
            assertFalse(AlarmPrecision.wantsExact(entry(kind, 5.minutes)), "$kind")
            assertFalse(AlarmPrecision.wantsExact(entry(kind, 2.hours)), "$kind")
        }
    }

    @Test
    fun `Duha is inexact while an hour or more of its window is left`() {
        for (kind in listOf(NotificationKind.DUHA_BEST, NotificationKind.DUHA_START)) {
            assertFalse(AlarmPrecision.wantsExact(entry(kind, 60.minutes)), "$kind")
            assertFalse(AlarmPrecision.wantsExact(entry(kind, 4.hours)), "$kind")
        }
    }

    @Test
    fun `Duha with less than an hour of its window left is exact so it is never dropped for lateness`() {
        for (kind in listOf(NotificationKind.DUHA_BEST, NotificationKind.DUHA_START)) {
            assertTrue(AlarmPrecision.wantsExact(entry(kind, 59.minutes)), "$kind")
            assertTrue(AlarmPrecision.wantsExact(entry(kind, 1.minutes)), "$kind")
        }
    }
}
