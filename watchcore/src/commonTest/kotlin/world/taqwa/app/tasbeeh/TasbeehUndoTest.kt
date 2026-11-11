package world.taqwa.app.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A turn back on the watch's bezel or crown takes the last count away: [TasbeehEngine.undo]
 * answers one count less in the same round, and nothing at 0, so it never reopens the round
 * before. On the post-prayer set, 33 · 33 · 34, unless a case names its own preset.
 */
class TasbeehUndoTest {

    private val set = TasbeehPresets.default

    private fun at(count: Int, round: Int) = TasbeehState(TasbeehPresets.DEFAULT_ID, count, round)

    @Test
    fun undoTakesTheLastCountAway() {
        assertEquals(at(4, 1), TasbeehEngine.undo(at(5, 1), set))
    }

    @Test
    fun undoAtZeroDoesNothing() {
        assertNull(TasbeehEngine.undo(at(0, 1), set))
        // Round 3's 0 is where round 2 rolled over; a turn back does not reopen round 2's 100.
        assertNull(TasbeehEngine.undo(at(0, 3), set))
    }

    /**
     * The lower boundary: the first count of a round goes back to that round's 0, in round 1 and
     * in a later one, and a one-count phrase's whole set, completed at 1, is taken back to 0.
     */
    @Test
    fun undoFromOneGoesBackToZeroInTheSameRound() {
        assertEquals(at(0, 1), TasbeehEngine.undo(at(1, 1), set))
        assertEquals(at(0, 2), TasbeehEngine.undo(at(1, 2), set))

        val once = TasbeehPresets.custom("custom_1", "x", 1)
        assertEquals(
            TasbeehState("custom_1", 0, 1),
            TasbeehEngine.undo(TasbeehState("custom_1", 1, 1), once),
        )
    }

    @Test
    fun undoBackOverAPartsEndShowsThePartBefore() {
        val fromThirtyThree = assertNotNull(TasbeehEngine.undo(at(33, 1), set))
        assertEquals(at(32, 1), fromThirtyThree)
        assertEquals(0, TasbeehEngine.currentPart(fromThirtyThree, set))

        val fromThirtyFour = assertNotNull(TasbeehEngine.undo(at(34, 1), set))
        assertEquals(at(33, 1), fromThirtyFour)
        assertEquals(1, TasbeehEngine.currentPart(fromThirtyFour, set))
    }

    @Test
    fun undoOnACompletedSetStaysInItsRound() {
        assertEquals(at(99, 1), TasbeehEngine.undo(at(100, 1), set))
    }

    /** The 33rd count taken away and said again is the end of the first part again, buzzed again. */
    @Test
    fun countingAgainAfterUndoBuzzesThePartAgain() {
        val back = assertNotNull(TasbeehEngine.undo(at(33, 1), set))
        assertEquals(at(33, 1) to TapEvent.PartComplete(0), TasbeehEngine.tap(back, set))
    }

    /**
     * The watch keeps its own counts, so a phrase whose target was lowered on the phone can arrive
     * with a count above it: 500 kept against a target now 100. The counter shows such a count as
     * its target, the phone's rule (`TasbeehUiState.count`), and a turn back takes one from the
     * count shown, so the first turn is seen to work, and the round stays.
     */
    @Test
    fun undoFromAboveATargetThatShrankTakesOneFromTheTarget() {
        val shrunk = TasbeehPresets.custom("custom_7", "dhikr", 100)
        assertEquals(
            TasbeehState("custom_7", 99, 2),
            TasbeehEngine.undo(TasbeehState("custom_7", 500, 2), shrunk),
        )
    }

    /** A preset with nothing to count: [TasbeehEngine.tap] changes nothing on it, and nor does a turn back. */
    @Test
    fun undoOnAPresetWithNothingToCountDoesNothing() {
        val empty = TasbeehPreset("custom_0", parts = emptyList(), custom = true)
        assertNull(TasbeehEngine.undo(TasbeehState("custom_0", 3, 1), empty))
    }
}
