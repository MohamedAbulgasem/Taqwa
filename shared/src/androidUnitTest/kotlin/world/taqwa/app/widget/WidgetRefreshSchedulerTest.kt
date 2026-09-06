package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers the one part of [WidgetRefreshScheduler] that can be wrong in a way a device test would
 * not catch quickly: the boundary arithmetic that decides *when* the next redraw is armed. The
 * `AlarmManager` calls themselves are thin enough to verify on the phone (`dumpsys alarm`).
 *
 * This lives in `androidUnitTest` rather than `commonTest` because the scheduler is an Android
 * concern; only the pure function is touched, so no Android class is ever loaded.
 */
class WidgetRefreshSchedulerTest {

    private val fiveMinutes = 5L * 60L * 1000L

    @Test
    fun `mid-interval instant rounds up to the next boundary`() {
        // 12:02:37.123 -> 12:05:00.000
        val now = boundary(12, 0) + 2 * 60_000L + 37_123L
        assertEquals(boundary(12, 5), WidgetRefreshScheduler.nextBoundaryAfter(now))
    }

    @Test
    fun `an instant exactly on a boundary advances a whole interval`() {
        // The receiver re-arms from its own delivery time, which lands on (or just after) a
        // boundary. Returning that same instant would arm an already-due alarm and spin.
        val now = boundary(12, 5)
        assertEquals(boundary(12, 10), WidgetRefreshScheduler.nextBoundaryAfter(now))
    }

    @Test
    fun `one millisecond before a boundary still returns that boundary`() {
        val now = boundary(12, 5) - 1L
        assertEquals(boundary(12, 5), WidgetRefreshScheduler.nextBoundaryAfter(now))
    }

    @Test
    fun `boundaries land on the wall-clock five-minute grid`() {
        // Epoch alignment is only useful if it coincides with what a reader sees on the clock.
        // True for every whole-minute UTC offset, which is every real zone.
        val next = WidgetRefreshScheduler.nextBoundaryAfter(boundary(9, 13) + 41_000L)
        assertEquals(0L, next % fiveMinutes)
        assertEquals(boundary(9, 15), next)
    }

    @Test
    fun `the result is always strictly ahead and never more than one interval away`() {
        var now = boundary(6, 0)
        repeat(600) {
            val next = WidgetRefreshScheduler.nextBoundaryAfter(now)
            assertTrue(next > now, "boundary $next was not ahead of $now")
            assertTrue(next - now <= fiveMinutes, "boundary $next was more than one interval past $now")
            now += 997L // a prime step, so every offset within the interval gets exercised
        }
    }

    @Test
    fun `a pre-epoch clock still moves forwards`() {
        // Kotlin's `%` keeps the dividend's sign, so the naive form would return a *smaller*
        // number here — an alarm permanently in the past on a device whose date was dragged back.
        val now = -3L * 60L * 1000L // 23:57 on 31 Dec 1969
        val next = WidgetRefreshScheduler.nextBoundaryAfter(now)
        assertEquals(0L, next)
        assertTrue(next > now)
    }

    @Test
    fun `the interval is overridable and honoured`() {
        val oneMinute = 60_000L
        assertEquals(
            boundary(12, 3),
            WidgetRefreshScheduler.nextBoundaryAfter(boundary(12, 2) + 30_000L, oneMinute),
        )
    }

    @Test
    fun `a non-positive interval is rejected rather than dividing by zero`() {
        assertFailsWith<IllegalArgumentException> {
            WidgetRefreshScheduler.nextBoundaryAfter(boundary(12, 0), 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            WidgetRefreshScheduler.nextBoundaryAfter(boundary(12, 0), -fiveMinutes)
        }
    }

    @Test
    fun `the shipped interval is the five minutes the staleness budget assumes`() {
        assertEquals(fiveMinutes, WidgetRefreshScheduler.INTERVAL_MILLIS)
    }

    /** Epoch millis for [hour]:[minute] UTC on an arbitrary day, exact to the second. */
    private fun boundary(hour: Int, minute: Int): Long =
        (1_788_000_000L / 86_400L) * 86_400L * 1000L + (hour * 60L + minute) * 60L * 1000L
}
