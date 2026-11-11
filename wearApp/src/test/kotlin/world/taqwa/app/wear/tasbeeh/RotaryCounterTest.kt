package world.taqwa.app.wear.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bezel turned into counts (plan 3 Task 6): each whole detent is one count clockwise and one
 * undo the other way, whatever pieces it arrives in. A detent is 64 pixels here, as the plan sets
 * it; on the watch it is one `AXIS_SCROLL` unit, `ViewConfiguration.scaledVerticalScrollFactor`.
 */
class RotaryCounterTest {

    private fun counter() = RotaryCounter(detentPx = 64f)

    @Test
    fun oneDetentEachWay() {
        val counter = counter()
        assertEquals(1, counter.onScroll(64f, uptimeMillis = 0))
        assertEquals(-1, counter.onScroll(-64f, uptimeMillis = 100))
    }

    @Test
    fun halvesAddUp() {
        val counter = counter()
        assertEquals(0, counter.onScroll(32f, uptimeMillis = 0))
        assertEquals(1, counter.onScroll(32f, uptimeMillis = 50))
    }

    @Test
    fun aFastTurnCountsEveryDetent() {
        assertEquals(3, counter().onScroll(192f, uptimeMillis = 0))
    }

    @Test
    fun aPauseDropsTheRemainder() {
        val counter = counter()
        assertEquals(0, counter.onScroll(40f, uptimeMillis = 0))
        assertEquals(0, counter.onScroll(40f, uptimeMillis = 600), "the first 40 was dropped after 500 ms without a scroll")
    }

    @Test
    fun turningBackDropsTheOtherWaysRemainder() {
        val counter = counter()
        assertEquals(0, counter.onScroll(40f, uptimeMillis = 0))
        assertEquals(-1, counter.onScroll(-64f, uptimeMillis = 50), "the clockwise 40 is not taken off the turn back")
    }

    @Test
    fun aRemainderCarriesWhileTheTurnGoesOn() {
        val counter = counter()
        assertEquals(1, counter.onScroll(100f, uptimeMillis = 0))
        assertEquals(1, counter.onScroll(30f, uptimeMillis = 400), "36 left over and 30 more make the second detent")
        assertEquals(0, counter.onScroll(0f, uptimeMillis = 450))
    }

    @Test
    fun aDetentInPiecesThatDoNotAddUpExactlyStillCounts() {
        // Twelve twelfths of 64 add up to 63.999992 in floating point, a hair short of the detent.
        val counter = counter()
        repeat(11) { assertEquals(0, counter.onScroll(64f / 12, uptimeMillis = it * 10L)) }
        assertEquals(1, counter.onScroll(64f / 12, uptimeMillis = 110))
    }
}
