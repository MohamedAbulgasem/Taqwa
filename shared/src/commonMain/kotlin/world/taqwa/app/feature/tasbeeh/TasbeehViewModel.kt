package world.taqwa.app.feature.tasbeeh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.taqwa.app.qibla.Haptics
import world.taqwa.app.settings.TasbeehStore
import world.taqwa.app.tasbeeh.TapEvent
import world.taqwa.app.tasbeeh.TasbeehEngine
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.tasbeeh.TasbeehState

/**
 * Everything the tasbeeh screen draws, derived rather than stored: the screen asks for
 * [currentPart], [progress] and [partEnds] instead of keeping three more fields in step with the
 * count by hand.
 */
data class TasbeehUiState(
    val preset: TasbeehPreset = TasbeehPresets.default,
    val state: TasbeehState = TasbeehState(TasbeehPresets.DEFAULT_ID, 0, 1),
    val custom: List<TasbeehPreset> = emptyList(),
) {
    /** Chip order: the seven built-ins of spec §2, then the reader's own in creation order. */
    val presets: List<TasbeehPreset> get() = TasbeehPresets.builtIn + custom

    /** The part the *next* count belongs to — the dhikr on screen, and the reminder row's dot. */
    val currentPart: Int get() = TasbeehEngine.currentPart(state, preset)

    /**
     * The count as the screen shows it, which is [TasbeehState.count] clamped to the target.
     *
     * A stored count above its preset's target should not happen — the store clamps an edited
     * custom on the way in, and the built-ins' targets have only ever grown (SubhanAllah's 33
     * became 100, so someone left at 20 of 33 opens at 20 of 100 with the same twenty taps behind
     * them). But a target that shrinks is one released build away, and a ring drawn at 120 % or a
     * count reading past its own "of" is a worse answer than a set that reads as finished. The
     * engine needs no such guard: a tap from `count >= total` already opens the next round.
     */
    val count: Int get() = if (preset.total <= 0) state.count else state.count.coerceAtMost(preset.total)

    val progress: Float
        get() = if (preset.total <= 0) 0f else count.toFloat() / preset.total

    /**
     * Where the ring's tick marks go: the part boundaries as fractions of the whole, without the
     * last one, which is the ring closing rather than a boundary. Empty for a single-part preset.
     */
    val partEnds: List<Float>
        get() = if (preset.parts.size < 2 || preset.total <= 0) {
            emptyList()
        } else {
            TasbeehEngine.partEnds(preset).dropLast(1).map { it.toFloat() / preset.total }
        }

    /**
     * This state with a completed set rolled over to 0 of the next round, or this very state if
     * the set is not complete. The guard is the point: the hold's coroutine cannot be cancelled
     * once its delay has elapsed, so a rollover that lands just after a tap or a chip switch has
     * moved the count must find nothing to do — a 1 of round 2 stays a 1.
     */
    fun rolledOver(): TasbeehUiState =
        if (preset.total <= 0 || state.count < preset.total) this
        else copy(state = TasbeehEngine.nextRound(state))
}

/**
 * The screen's state and the three things a tap sets off: the engine's rules, one haptic, and —
 * eventually — one write.
 *
 * **Why the write is debounced.** A hundred taps is a hundred state changes but only one thing
 * worth remembering: where you got to. [WRITE_DEBOUNCE_MS] after the last tap the count goes to
 * disk once; [flush] puts it there immediately, and the screen calls that on the way out so a
 * quick exit mid-burst loses nothing (spec §6).
 *
 * **Why a completed set rolls over by itself.** The hundredth tap closes the ring and fires the
 * set haptic; the ring then stays closed for [SET_COMPLETE_HOLD_MS] and the counter rolls over to
 * 0 of the next round on its own — the reader should not have to tap a finished set off its
 * hundred, and 100 followed by 1 read as if the first dhikr of the new round had been skipped.
 * A tap inside the hold still opens the next round at 1, exactly as before, and cancels the
 * rollover; anything that leaves the screen or the preset mid-hold ([flush], [select], the custom
 * edits) settles the rollover first, so what reaches disk is the round about to be opened, never
 * the hundred.
 *
 * **Why only the debounce and the hold are cancellable.** Those two are the pieces of work that
 * are meant to be thrown away — every tap replaces the one and pre-empts the other. Switching a
 * chip, adding a phrase and deleting one are not: each is a save followed by a read that must both happen, so they run on jobs of their own
 * that nothing cancels. Sharing one job slot between the two kinds once meant a page tap 300 ms
 * after a chip tap could cut the switch in half and leave the chip silently doing nothing.
 *
 * [scope] is the seam the tests use: a `TestScope` runs the debounce on virtual time, so
 * `advanceTimeBy(300)` is the clock without a clock parameter to thread through every call. Its
 * default is the view model's own scope rather than the screen's, because the screen's is
 * cancelled as it leaves composition — which is exactly when [flush] has work to do.
 */
class TasbeehViewModel(
    private val store: TasbeehStore,
    private val haptics: Haptics,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(TasbeehUiState())
    val state: StateFlow<TasbeehUiState> = _state.asStateFlow()

    /**
     * The pending debounced write, and nothing else: only [scheduleWrite] puts a job here, and
     * only [scheduleWrite] and [flush] cancel it.
     */
    private var debounceJob: Job? = null

    /**
     * The pending rollover of a completed set, or null. Set by [holdThenRollOver] alone; cancelled
     * by a tap (which rolls the set over itself), by [reset] and by [settle]. Cancelling is best
     * effort: past its delay the coroutine has no suspension point left and runs to the end
     * whatever is done to it, which is why [TasbeehUiState.rolledOver] checks before it moves.
     */
    private var holdJob: Job? = null

    /**
     * Whether a tap has landed yet. A disk read is not instant, and a thumb on the screen half a
     * second after it opens is not unusual: without this the stored count would arrive and wipe
     * out the taps already made. The reader's own counting wins; only the chip row still takes
     * what the disk says, since that cannot be counted over.
     *
     * Read inside the [MutableStateFlow.update] that would overwrite the taps, so that the check
     * and the write are one step: the flag is set on the main thread and the read runs on
     * [Dispatchers.Default], and a check made outside the update could pass just before a tap and
     * be acted on just after it.
     */
    private var counted = false

    init {
        // Read once when the screen opens (spec §6), not collected: the count on screen is the
        // truth from here on, and a flow feeding it back would fight the taps.
        scope.launch {
            val custom = store.customPresets.first()
            val selected = store.selectedId.first()
            // A selection naming a phrase that has since been deleted falls back rather than
            // throwing — the store makes the same fallback, so the two cannot disagree.
            val preset = TasbeehPresets.byId(selected, custom) ?: TasbeehPresets.default
            val stored = store.stateOf(preset.id).first()
            var opened = false
            _state.update { current ->
                opened = !counted
                if (counted) current.copy(custom = custom) else TasbeehUiState(preset, stored, custom)
            }
            if (opened) holdIfComplete(preset, stored)
        }
    }

    /**
     * One tap: the engine decides what it meant, the event picks exactly one haptic — a tap that
     * rolls a finished set into the next round is a plain [TapEvent.Tick], so it gets the light
     * one — and the write is pushed out another [WRITE_DEBOUNCE_MS].
     */
    fun tap() {
        counted = true
        // A tap on a held hundred is the reader carrying on: the engine opens the next round at 1,
        // and the rollover that would have opened it at 0 must not land on top of that.
        holdJob?.cancel()
        holdJob = null
        var fired: TapEvent = TapEvent.Tick
        _state.update { current ->
            val (next, event) = TasbeehEngine.tap(current.state, current.preset)
            fired = event
            current.copy(state = next)
        }
        when (fired) {
            TapEvent.Tick -> haptics.count()
            is TapEvent.PartComplete -> haptics.partComplete()
            TapEvent.SetComplete -> haptics.setComplete()
        }
        scheduleWrite()
        if (fired == TapEvent.SetComplete) holdThenRollOver()
    }

    /** Back to count 0, round 1 for this preset, and that is worth writing straight away. */
    fun reset() {
        // Reset goes to round 1 whatever the hold would have opened; the hold is simply dropped.
        holdJob?.cancel()
        holdJob = null
        _state.update { it.copy(state = TasbeehEngine.reset(it.state)) }
        flush()
    }

    /**
     * Switches the chip. The preset being left keeps its own count, so it is written before the
     * new one's is read — each preset remembers where it was (spec §4).
     */
    fun select(id: String) {
        val current = _state.value
        if (id == current.preset.id) return
        val preset = TasbeehPresets.byId(id, current.custom) ?: return
        val leaving = settle()
        // The debounce would only write what is being written here, a moment later.
        debounceJob?.cancel()
        scope.launch {
            store.save(leaving)
            store.select(id)
            val restored = store.stateOf(id).first()
            _state.update { it.copy(preset = preset, state = restored) }
            holdIfComplete(preset, restored)
        }
    }

    /** Adds a phrase of the reader's own and selects it, which is why they typed it. */
    fun addCustom(phrase: String, target: Int) {
        val leaving = settle()
        debounceJob?.cancel()
        scope.launch {
            store.save(leaving)
            val added = store.addCustom(phrase, target)
            store.select(added.id)
            val restored = store.stateOf(added.id).first()
            val custom = store.customPresets.first()
            _state.update { it.copy(preset = added, state = restored, custom = custom) }
            holdIfComplete(added, restored)
        }
    }

    /**
     * Rewrites a phrase of the reader's own from its own sheet: a new wording, a new target, or
     * both. The id and the chip's place in the row are the same afterwards, and so is the count —
     * unless the new target is below it, which the store clamps.
     *
     * The count on screen goes to disk first, exactly as [select] and [removeCustom] do it. That
     * matters most when the phrase being edited *is* the one being counted: the store clamps what
     * is on disk, so what is on disk has to be the count actually reached, not the one from 300 ms
     * ago. Editing some other phrase is the same reason [removeCustom] flushes — a pending count
     * is not something an unrelated edit may drop.
     *
     * Everything on screen is then re-derived from the store rather than patched: the preset comes
     * back out of the freshly read list, so the dhikr block, the ring's target and the chip all
     * show the new phrase without three separate assignments that could disagree.
     */
    fun updateCustom(id: String, phrase: String, target: Int) {
        val leaving = settle()
        debounceJob?.cancel()
        scope.launch {
            store.save(leaving)
            store.updateCustom(id, phrase, target)
            val custom = store.customPresets.first()
            if (_state.value.preset.id == id) {
                val edited = TasbeehPresets.byId(id, custom) ?: TasbeehPresets.default
                val restored = store.stateOf(edited.id).first()
                _state.update { it.copy(preset = edited, state = restored, custom = custom) }
                holdIfComplete(edited, restored)
            } else {
                _state.update { it.copy(custom = custom) }
            }
        }
    }

    /**
     * Forgets a phrase and its count. Deleting the one on screen falls back to the default preset
     * — the store makes the same fallback for the stored selection, so the two cannot disagree.
     *
     * The count on screen goes to disk first, exactly as [select] writes the preset it leaves: the
     * phrase deleted is often *not* the one being counted, and a delete is no reason to drop the
     * taps of the one that is.
     */
    fun removeCustom(id: String) {
        val leaving = settle()
        debounceJob?.cancel()
        scope.launch {
            store.save(leaving)
            store.removeCustom(id)
            val custom = store.customPresets.first()
            if (_state.value.preset.id == id) {
                val fallback = TasbeehPresets.default
                val restored = store.stateOf(fallback.id).first()
                _state.update { TasbeehUiState(fallback, restored, custom) }
                holdIfComplete(fallback, restored)
            } else {
                _state.update { it.copy(custom = custom) }
            }
        }
    }

    /** Writes the count now rather than in 300 ms. The screen's `DisposableEffect` calls this. */
    fun flush() {
        debounceJob?.cancel()
        debounceJob = null
        val state = settle()
        // Not held in [debounceJob]: a write asked for by name is never the one to throw away.
        scope.launch { store.save(state) }
    }

    /** Holds the closed ring for [SET_COMPLETE_HOLD_MS], then [rollOver]. */
    private fun holdThenRollOver() {
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(SET_COMPLETE_HOLD_MS)
            rollOver()
        }
    }

    /**
     * Schedules the hold for a state read from disk that is already a completed set. A hundred
     * can be there: the debounce writes it 300 ms after the last tap, so a process that dies
     * inside the hold leaves one, and so does an install from before the rollover existed. It is
     * shown as the hundred it was and rolls over as a fresh one does.
     */
    private fun holdIfComplete(preset: TasbeehPreset, state: TasbeehState) {
        if (preset.total > 0 && state.count >= preset.total) holdThenRollOver()
    }

    /**
     * Applies a pending rollover now, if there is one, and returns the state to write for the
     * preset on screen. Every path that writes the current preset on its way somewhere else goes
     * through this, so a hundred mid-hold is stored as 0 of the next round rather than as itself.
     * The coroutine may still land afterwards if it was already past its delay; it then finds the
     * set rolled over and does nothing.
     */
    private fun settle(): TasbeehState {
        val pending = holdJob
        holdJob = null
        if (pending != null && pending.isActive) {
            pending.cancel()
            _state.update { it.rolledOver() }
        }
        return _state.value.state
    }

    /**
     * The hold's end: the completed set becomes 0 of the next round, and that goes to disk at once
     * — a state the reader did not tap into is not left to a debounce a later tap could push back.
     * Written directly rather than through [flush]: this runs on [scope], and [flush] and [settle]
     * touch the two job fields that otherwise only the main thread writes. No debounce needs
     * cancelling either: one pending here would mean a tap inside the hold, and that tap cancelled
     * the hold. If the set is no longer complete (a tap or a switch beat this by an instant) there
     * is nothing to write.
     */
    private fun rollOver() {
        var rolled: TasbeehState? = null
        _state.update { current ->
            val next = current.rolledOver()
            rolled = if (next === current) null else next.state
            next
        }
        val state = rolled ?: return
        scope.launch { store.save(state) }
    }

    private fun scheduleWrite() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            store.save(_state.value.state)
        }
    }

    companion object {
        /** Spec §6: one write 300 ms after the last tap, not one per tap. */
        const val WRITE_DEBOUNCE_MS = 300L

        /**
         * How long a completed set holds its closed ring before rolling over to 0 of the next
         * round. Long enough for the set haptic and the full ring to register as "done", short
         * enough that a reader going straight on does not wait on it — a tap in the hold opens
         * the next round at 1 immediately.
         */
        const val SET_COMPLETE_HOLD_MS = 1_000L
    }
}
