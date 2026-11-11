package world.taqwa.app.tasbeeh

/**
 * The counting rules of spec §3. Pure: no clock, no storage, no platform — the screen owns the
 * haptics and the writes, this owns only what a tap means.
 */
object TasbeehEngine {

    /**
     * How long a completed set holds its closed ring before the counter rolls it over to
     * [nextRound]. Long enough for the set haptic and the full ring to register as "done", short
     * enough that a reader going straight on does not wait on it: a tap in the hold opens the next
     * round at 1 immediately. The phone's counter and the watches' hold for the same time.
     */
    const val SET_COMPLETE_HOLD_MS = 1_000L

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

    /**
     * A turn back on a watch's bezel or crown: the last count taken away, in the same round, or
     * null when there is nothing to take away. So it never reopens the round before: at count 0
     * (a fresh preset, or the 0 a finished set rolled over to) it answers null, and from a
     * completed set it answers `total - 1` of that same round. Back over a part's end,
     * [currentPart] shows the part before again, and the next [tap] completes that part again.
     *
     * It takes one from the count as the counter shows it, which is the count held to the
     * preset's [TasbeehPreset.total], the phone's rule: a watch keeps its own counts, so a
     * phrase whose target was lowered on the phone can arrive with a count above it. A preset
     * with nothing to count answers null, as [tap] changes nothing on it.
     */
    fun undo(state: TasbeehState, preset: TasbeehPreset): TasbeehState? {
        val shown = state.count.coerceAtMost(preset.total)
        return if (shown <= 0) null else state.copy(count = shown - 1)
    }

    /** Back to the start of this preset: count 0, round 1, the preset unchanged. */
    fun reset(state: TasbeehState): TasbeehState = state.copy(count = 0, round = 1)

    /**
     * The round after this one, before anything has been said in it: count 0, `round + 1`. The
     * screen applies it on its own a moment after a set completes, so a finished set does not sit
     * on its hundred waiting for a tap. [tap] from a completed set still opens the next round at 1
     * — that tap *is* the first dhikr of the new round; this is the case where no tap came.
     */
    fun nextRound(state: TasbeehState): TasbeehState = state.copy(count = 0, round = state.round + 1)

    /**
     * The cumulative count each part ends on — 33, 66, 100 for the post-prayer set. The ring
     * draws its tick marks at these, and [tap] reads them to decide a part is done.
     */
    fun partEnds(preset: TasbeehPreset): List<Int> {
        var running = 0
        return preset.parts.map { running += it.count; running }
    }
}
