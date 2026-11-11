package world.taqwa.app.wear.tile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ruling 22's safety net from the Tile: each request of the Tile asks the complications to be
 * requested again, at most once in six hours (`complications.asked`).
 */
class ThrottleTest {

    private val now = 1_792_330_680L

    @Test
    fun neverAskedIsDue() {
        assertTrue(Throttle.due(lastAt = null, now = now, gapSeconds = 21_600L))
    }

    @Test
    fun aGapShortOfSixHoursIsNotDue() {
        assertFalse(Throttle.due(lastAt = now - 21_599L, now = now, gapSeconds = 21_600L))
        assertFalse(Throttle.due(lastAt = now, now = now, gapSeconds = 21_600L))
    }

    @Test
    fun sixHoursIsDue() {
        assertTrue(Throttle.due(lastAt = now - 21_600L, now = now, gapSeconds = 21_600L))
    }

    @Test
    fun aStampFromTheFutureIsDue() {
        // The watch's clock was set back (a time zone, a manual time): a stamp ahead of now would
        // otherwise hold the net shut for as long as the clock was moved.
        assertTrue(Throttle.due(lastAt = now + 60L, now = now, gapSeconds = 21_600L))
    }
}
