package world.taqwa.app.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals

class TasbeehEngineTest {

    private val afterPrayer = TasbeehPresets.builtIn.first { it.id == "after_prayer" }
    private val subhanallah = TasbeehPresets.builtIn.first { it.id == "subhanallah" }
    private val allahuAkbar = TasbeehPresets.builtIn.first { it.id == "allahu_akbar" }
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
        val (state, events) = walk(subhanallah)
        assertEquals(TasbeehState("subhanallah", 33, 1), state)
        assertEquals(TapEvent.SetComplete, events[32])
        assertEquals(emptyList(), events.filterIsInstance<TapEvent.PartComplete>())
    }

    /**
     * The 34 of the post-prayer set's third part, standing alone as its own preset. It is the one
     * built-in whose total is not 33 or 100, and the only place a part length and a preset total
     * differ by a count — so it is where an off-by-one between "end of part" and "end of set"
     * would show first.
     */
    @Test
    fun theThirtyFourCountPresetCompletesAt34AndNeverParts() {
        val (state, events) = walk(allahuAkbar)
        assertEquals(34, allahuAkbar.total)
        assertEquals(TasbeehState("allahu_akbar", 34, 1), state)
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
        assertEquals(listOf(33), TasbeehEngine.partEnds(subhanallah))
    }
}
