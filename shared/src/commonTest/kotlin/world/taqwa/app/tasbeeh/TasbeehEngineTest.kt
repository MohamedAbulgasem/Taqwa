package world.taqwa.app.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals

class TasbeehEngineTest {

    private val afterPrayer = TasbeehPresets.builtIn.first { it.id == "after_prayer" }
    private val subhanallah = TasbeehPresets.builtIn.first { it.id == "subhanallah" }

    /**
     * The 33 and the 34 of the post-prayer set's parts, each standing alone as a preset of its
     * own. They used to be the built-in `subhanallah` and `allahu_akbar` chips; those count to a
     * hundred now like every other single dhikr, so the counts themselves are held here as phrases
     * of the reader's own — a target is a target whoever set it, and these two are the ones the
     * post-prayer set is made of.
     */
    private val thirtyThree = TasbeehPresets.custom("custom_33", "dhikr", 33)
    private val thirtyFour = TasbeehPresets.custom("custom_34", "dhikr", 34)
    private val oneOff = TasbeehPresets.custom("custom_1", "dhikr", 1)

    private fun start(preset: TasbeehPreset) = TasbeehState(preset.id, 0, 1)

    /** Walks a whole round and returns the event each count produced, indexed by count - 1. */
    private fun walk(preset: TasbeehPreset): Pair<TasbeehState, List<TapEvent>> {
        var state = start(preset)
        val events = mutableListOf<TapEvent>()
        repeat(preset.total) {
            val (next, event) = TasbeehEngine.tap(state, preset)
            state = next
            events += event
        }
        return state to events
    }

    @Test
    fun thePostPrayerSetPartsAt33And66AndCompletesAt100() {
        val (state, events) = walk(afterPrayer)
        assertEquals(TasbeehState("after_prayer", 100, 1), state)
        assertEquals(TapEvent.PartComplete(0), events[32])
        assertEquals(TapEvent.PartComplete(1), events[65])
        assertEquals(TapEvent.SetComplete, events[99])
        val everywhereElse = events.filterIndexed { index, _ -> index !in setOf(32, 65, 99) }
        assertEquals(97, everywhereElse.size)
        assertEquals(emptyList(), everywhereElse.filterNot { it == TapEvent.Tick })
    }

    @Test
    fun currentPartFollowsTheNextCountNotTheLastOne() {
        for (count in 0..32) assertEquals(0, TasbeehEngine.currentPart(TasbeehState("after_prayer", count, 1), afterPrayer))
        for (count in 33..65) assertEquals(1, TasbeehEngine.currentPart(TasbeehState("after_prayer", count, 1), afterPrayer))
        // 100 included: a completed set stays on its last part until the next tap.
        for (count in 66..100) assertEquals(2, TasbeehEngine.currentPart(TasbeehState("after_prayer", count, 1), afterPrayer))
    }

    @Test
    fun theTapAfterACompletedSetOpensTheNextRound() {
        val (state, event) = TasbeehEngine.tap(TasbeehState("after_prayer", 100, 1), afterPrayer)
        assertEquals(TasbeehState("after_prayer", 1, 2), state)
        assertEquals(TapEvent.Tick, event)
        assertEquals(0, TasbeehEngine.currentPart(state, afterPrayer))
    }

    @Test
    fun aSinglePartPresetCompletesAtItsTotalAndNeverParts() {
        val (state, events) = walk(thirtyThree)
        assertEquals(TasbeehState("custom_33", 33, 1), state)
        assertEquals(TapEvent.SetComplete, events[32])
        assertEquals(emptyList(), events.filterIsInstance<TapEvent.PartComplete>())
    }

    /** The same machine on a built-in single, which is a hundred of one phrase rather than 33. */
    @Test
    fun aBuiltInSingleDhikrCompletesAtAHundred() {
        assertEquals(100, subhanallah.total)
        val (state, events) = walk(subhanallah)
        assertEquals(TasbeehState("subhanallah", 100, 1), state)
        assertEquals(TapEvent.SetComplete, events[99])
        assertEquals(emptyList(), events.filterIsInstance<TapEvent.PartComplete>())
        assertEquals(emptyList(), events.dropLast(1).filterNot { it == TapEvent.Tick })
    }

    /**
     * The 34 of the post-prayer set's third part, standing alone as its own preset: the one count
     * in the app that is neither 33 nor 100, and the only place a part length and a preset total
     * differ by a count — so it is where an off-by-one between "end of part" and "end of set"
     * would show first.
     */
    @Test
    fun theThirtyFourCountPresetCompletesAt34AndNeverParts() {
        val (state, events) = walk(thirtyFour)
        assertEquals(34, thirtyFour.total)
        assertEquals(TasbeehState("custom_34", 34, 1), state)
        assertEquals(TapEvent.SetComplete, events[33])
        assertEquals(emptyList(), events.filterIsInstance<TapEvent.PartComplete>())
        assertEquals(emptyList(), events.dropLast(1).filterNot { it == TapEvent.Tick })
    }

    @Test
    fun aOneCountCustomPresetCompletesOnTheFirstTap() {
        val (state, event) = TasbeehEngine.tap(start(oneOff), oneOff)
        assertEquals(TasbeehState("custom_1", 1, 1), state)
        assertEquals(TapEvent.SetComplete, event)
        val (rolled, rolledEvent) = TasbeehEngine.tap(state, oneOff)
        assertEquals(TasbeehState("custom_1", 1, 2), rolled)
        assertEquals(TapEvent.Tick, rolledEvent)
    }

    @Test
    fun resetKeepsThePresetAndDropsTheCountAndRound() {
        assertEquals(
            TasbeehState("after_prayer", 0, 1),
            TasbeehEngine.reset(TasbeehState("after_prayer", 67, 4)),
        )
    }

    @Test
    fun partEndsAreCumulative() {
        assertEquals(listOf(33, 66, 100), TasbeehEngine.partEnds(afterPrayer))
        assertEquals(listOf(33), TasbeehEngine.partEnds(thirtyThree))
        assertEquals(listOf(100), TasbeehEngine.partEnds(subhanallah))
    }

    /**
     * The set does not wait to be tapped off the hundred: a moment after it completes the screen
     * rolls it over on its own, and that is count 0 of the next round — not 1, since no dhikr was
     * said — with the preset unchanged.
     */
    @Test
    fun nextRoundOpensAtZeroWithTheRoundAdvanced() {
        assertEquals(TasbeehState("after_prayer", 0, 2), TasbeehEngine.nextRound(TasbeehState("after_prayer", 100, 1)))
        assertEquals(TasbeehState("custom_1", 0, 4), TasbeehEngine.nextRound(TasbeehState("custom_1", 1, 3)))
    }
}
