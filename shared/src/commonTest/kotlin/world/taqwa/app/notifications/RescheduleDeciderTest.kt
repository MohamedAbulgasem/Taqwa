package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
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

    // The overload Android's alarm receiver asks: it wakes in a process with no plan in memory
    // and knows only how far the armed one reaches, read back from the scheduler's own prefs.

    @Test
    fun nothingScheduledAlwaysNeedsTopUp() {
        assertTrue(RescheduleDecider.needsTopUp(null, now, 3.days))
    }

    @Test
    fun aHorizonInsideTheMinimumNeedsTopUp() {
        assertTrue(RescheduleDecider.needsTopUp(now + 2.days, now, 3.days))
    }

    @Test
    fun aHorizonBeyondTheMinimumDoesNot() {
        assertFalse(RescheduleDecider.needsTopUp(now + 11.days, now, 3.days))
    }

    @Test
    fun aHorizonAlreadyPastNeedsTopUp() {
        // What a plan that ran out entirely looks like: the last alarm fired days ago and every
        // id has been swept, but the stored horizon is still whatever it reached.
        assertTrue(RescheduleDecider.needsTopUp(now - 1.days, now, 3.days))
    }

    @Test
    fun bothOverloadsAgreeOnTheSamePlan() {
        val plan = listOf(entryAt(now + 2.days), entryAt(now + 11.days))
        assertEquals(
            RescheduleDecider.needsTopUp(plan, now, 3.days),
            RescheduleDecider.needsTopUp(plan.maxOf { it.instant }, now, 3.days),
        )
    }
}
