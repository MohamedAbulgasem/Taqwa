package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RescheduleDeciderTest {

    private val now = Instant.parse("2026-09-06T09:00:00Z")

    private fun entryAt(instant: Instant) = ScheduledNotification(
        id = "x", prayer = Prayer.FAJR, kind = NotificationKind.PRAYER, instant = instant,
        timeZoneId = "Europe/London", sound = PrayerSound.TAKBIR, title = "Fajr", body = "…",
    )

    @Test
    fun anEmptyPlanAlwaysNeedsTopUp() {
        assertTrue(RescheduleDecider.needsTopUp(emptyList(), now, 3.days))
    }

    @Test
    fun aDrainingWindowNeedsTopUp() {
        val plan = listOf(entryAt(now + 2.days))
        assertTrue(RescheduleDecider.needsTopUp(plan, now, 3.days))
    }

    @Test
    fun aFullWindowDoesNot() {
        val plan = listOf(entryAt(now + 2.days), entryAt(now + 11.days))
        assertFalse(RescheduleDecider.needsTopUp(plan, now, 3.days))
    }
}
