package world.taqwa.app.tasbeeh

/**
 * The counting rules of spec §3. Pure: no clock, no storage, no platform — the screen owns the
 * haptics and the writes, this owns only what a tap means.
 */
object TasbeehEngine {

    /**
     * Counting is continuous within a round, so a tap adds one to the running count rather than
     * to a per-part count: from `count < total` the new count is `count + 1`, and the event is
     * [TapEvent.SetComplete] when that reaches the preset's total, [TapEvent.PartComplete] when
     * it lands on the cumulative end of a part that is not the last one, and [TapEvent.Tick]
     * otherwise. A tap on a finished set (`count == total`) opens the next round at count 1 with
     * `round + 1`, and that is a plain [TapEvent.Tick] — the set was already celebrated when it
     * completed.
     */
    fun tap(state: TasbeehState, preset: TasbeehPreset): Pair<TasbeehState, TapEvent> {
        val total = preset.total
        if (total <= 0) return state to TapEvent.Tick
        if (state.count >= total) {
            return state.copy(count = 1, round = state.round + 1) to TapEvent.Tick
        }
        val next = state.count + 1
        val event = when {
            next >= total -> TapEvent.SetComplete
            else -> {
                // `dropLast(1)`: the last part's end is the total, which is SetComplete above, so
                // PartComplete can never fire for a single-part preset.
                val part = partEnds(preset).dropLast(1).indexOf(next)
                if (part >= 0) TapEvent.PartComplete(part) else TapEvent.Tick
            }
        }
        return state.copy(count = next) to event
    }

    /**
     * The index of the part the *next* count belongs to, which is what the screen displays: the
     * dhikr switches the moment a part's end is reached, not one tap later. For `count == total`
     * it is the last part, so a completed set is shown whole until the next tap starts a round.
     */
    fun currentPart(state: TasbeehState, preset: TasbeehPreset): Int {
        if (preset.parts.isEmpty()) return 0
        val ends = partEnds(preset)
        val index = ends.indexOfFirst { it > state.count }
        return if (index >= 0) index else preset.parts.lastIndex
    }

    /** Back to the start of this preset: count 0, round 1, the preset unchanged. */
    fun reset(state: TasbeehState): TasbeehState = state.copy(count = 0, round = 1)

    /**
     * The cumulative count each part ends on — 33, 66, 100 for the post-prayer set. The ring
     * draws its tick marks at these, and [tap] reads them to decide a part is done.
     */
    fun partEnds(preset: TasbeehPreset): List<Int> {
        var running = 0
        return preset.parts.map { running += it.count; running }
    }
}
