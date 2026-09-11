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

    val progress: Float
        get() = if (preset.total <= 0) 0f else state.count.toFloat() / preset.total

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

    /** The pending debounced write, cancelled and replaced by every tap. */
    private var writeJob: Job? = null

    /**
     * Whether a tap has landed yet. A disk read is not instant, and a thumb on the screen half a
     * second after it opens is not unusual: without this the stored count would arrive and wipe
     * out the taps already made. The reader's own counting wins; only the chip row still takes
     * what the disk says, since that cannot be counted over.
     */
    private var counted = false

    init {
        // Read once when the screen opens (spec §6), not collected: the count on screen is the
        // truth from here on, and a flow feeding it back would fight the taps.
        scope.launch {
            val custom = store.customPresets.first()
            val selected = store.selectedId.first()
            val preset = TasbeehPresets.byId(selected, custom) ?: TasbeehPresets.default
            val stored = store.stateOf(preset.id).first()
            _state.value = if (counted) {
                _state.value.copy(custom = custom)
            } else {
                TasbeehUiState(preset, stored, custom)
            }
        }
    }

    /**
     * One tap: the engine decides what it meant, the event picks exactly one haptic — a tap that
     * rolls a finished set into the next round is a plain [TapEvent.Tick], so it gets the light
     * one — and the write is pushed out another [WRITE_DEBOUNCE_MS].
     */
    fun tap() {
        counted = true
        val current = _state.value
        val (next, event) = TasbeehEngine.tap(current.state, current.preset)
        _state.value = current.copy(state = next)
        when (event) {
            TapEvent.Tick -> haptics.count()
            is TapEvent.PartComplete -> haptics.partComplete()
            TapEvent.SetComplete -> haptics.setComplete()
        }
        scheduleWrite()
    }

    /** Back to count 0, round 1 for this preset, and that is worth writing straight away. */
    fun reset() {
        _state.value = _state.value.let { it.copy(state = TasbeehEngine.reset(it.state)) }
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
        writeJob?.cancel()
        writeJob = scope.launch {
            store.save(current.state)
            store.select(id)
            _state.value = _state.value.copy(preset = preset, state = store.stateOf(id).first())
        }
    }

    /** Adds a phrase of the reader's own and selects it, which is why they typed it. */
    fun addCustom(phrase: String, target: Int) {
        val leaving = _state.value.state
        writeJob?.cancel()
        writeJob = scope.launch {
            store.save(leaving)
            val added = store.addCustom(phrase, target)
            store.select(added.id)
            _state.value = _state.value.copy(
                preset = added,
                state = store.stateOf(added.id).first(),
                custom = store.customPresets.first(),
            )
        }
    }

    /**
     * Forgets a phrase and its count. Deleting the one on screen falls back to the default preset
     * — the store makes the same fallback for the stored selection, so the two cannot disagree.
     */
    fun removeCustom(id: String) {
        writeJob?.cancel()
        writeJob = scope.launch {
            store.removeCustom(id)
            val custom = store.customPresets.first()
            val current = _state.value
            if (current.preset.id == id) {
                val fallback = TasbeehPresets.default
                _state.value = TasbeehUiState(fallback, store.stateOf(fallback.id).first(), custom)
            } else {
                _state.value = current.copy(custom = custom)
            }
        }
    }

    /** Writes the count now rather than in 300 ms. The screen's `DisposableEffect` calls this. */
    fun flush() {
        writeJob?.cancel()
        val state = _state.value.state
        writeJob = scope.launch { store.save(state) }
    }

    private fun scheduleWrite() {
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            store.save(_state.value.state)
        }
    }

    companion object {
        /** Spec §6: one write 300 ms after the last tap, not one per tap. */
        const val WRITE_DEBOUNCE_MS = 300L
    }
}
