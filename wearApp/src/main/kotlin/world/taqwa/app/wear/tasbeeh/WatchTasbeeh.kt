package world.taqwa.app.wear.tasbeeh

import android.os.SystemClock
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import world.taqwa.app.tasbeeh.TapEvent
import world.taqwa.app.tasbeeh.TasbeehEngine
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.tasbeeh.TasbeehState
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.Feel
import world.taqwa.app.wear.app.HapticPatterns
import world.taqwa.app.wear.app.Line

/**
 * Everything the counter draws and says: the phrase and where it stands, the part on screen (the
 * one the next count belongs to, [TasbeehEngine.currentPart]), the ring's notches at the parts'
 * ends as fractions of the whole ([ticks], none for a single phrase), and the flash the last change
 * crossed: a part's end or the set's, or null.
 */
internal data class TasbeehUi(
    val preset: TasbeehPreset,
    val state: TasbeehState,
    val currentPart: Int,
    val ticks: List<Float>,
    val flash: TapEvent?,
) {
    /** The count as the counter shows it, held to the phrase's target, as the phone's screen holds it. */
    val count: Int get() = state.count.coerceIn(0, preset.total.coerceAtLeast(0))

    /** How far round the ring is: the count over the target. */
    val progress: Float get() = if (preset.total <= 0) 0f else count.toFloat() / preset.total

    companion object {
        fun of(preset: TasbeehPreset, state: TasbeehState, flash: TapEvent? = null): TasbeehUi = TasbeehUi(
            preset = preset,
            state = state,
            currentPart = TasbeehEngine.currentPart(state, preset),
            ticks = if (preset.parts.size < 2 || preset.total <= 0) {
                emptyList()
            } else {
                TasbeehEngine.partEnds(preset).dropLast(1).map { it.toFloat() / preset.total }
            },
            flash = flash,
        )
    }
}

/**
 * The counter's and the phrase list's lines (call 15), in the drawings' units (`gTasbeeh`,
 * `gTasbeehList`): each one's size, how far the wearer's font size may grow it, and how far it may
 * shrink to fit.
 */
internal object TasbeehType {
    /**
     * The dhikr at the top. A built-in with its transliteration beneath it is one line. A phrase with
     * none (his own, or any under an interface not in Latin script) may take the transliteration's
     * place as a second line, and shrinks to fit down to 11 units before an ellipsis.
     *
     * Its two lines stand as its font sets them, but never farther apart than [DHIKR_LINE_HEIGHT] of
     * its size: the watch's Naskh sets its own 1.70 apart, which at the largest font size left no
     * room for two lines above "ROUND n" even at 11 ([dhikrBottom]). Roboto, for a phrase in Latin
     * script, and the emoji font set theirs nearer, and keep them.
     */
    fun dhikr(transliterated: Boolean): Line =
        if (transliterated) {
            Line(16.5f, 1.15f, maxLines = 1)
        } else {
            Line(16.5f, 1.15f, maxLines = 2, floor = 11f, maxLineHeight = DHIKR_LINE_HEIGHT)
        }

    /**
     * The farthest apart a dhikr's two lines stand, as a share of its size: far enough that a
     * vowelled phrase's marks stay apart across its lines, which at 1.2 they did not (review round
     * 2). In the watch's Naskh the lowest ink of a first line and the highest of a second, where they
     * overlap across, come to at most 1.45 of the size over 3,888 vowelled phrases of up to 60 code
     * points from the app's adhkar and the Quran, each wrapped as the counter wraps it: a final
     * yaa's dots over the hamza and fatha of أَ. At 1.55 a tenth of the size stays between them, and
     * two lines at 11 still end above "ROUND n" at the largest font size once Android has rounded
     * them to whole pixels, on every round screen from 384 to 480 pixels across. The phone's own
     * dhikr stands its lines 1.6 apart (30 sp on 48 sp, `TasbeehScreen`), which on the watch would
     * give up the second line at 1.3 on the emulator's 454 pixels.
     */
    private const val DHIKR_LINE_HEIGHT = 1.55f

    val transliteration = Line(10f, 1.3f)
    val round = Line(10f, 1.3f)
    val count = Line(46f, 1f)
    val of = Line(11f, 1.3f)
    val hint = Line(10f, 1f)

    /**
     * How far the hint keeps from the round screen's edge, in units: none. `gTasbeeh` sets it
     * across the ring, out to the screen's own edge (96.9 units wide at 10), where the frame's
     * usual 6-unit inset would cut it with an ellipsis.
     */
    const val HINT_INSET = 0f

    /** The counter's baselines, in units (`gTasbeeh`), and the parts' dots' middle. */
    const val DHIKR_Y = 56f
    const val TRANSLITERATION_Y = 72f
    const val ROUND_Y = 92f
    const val COUNT_Y = 134f
    const val OF_Y = 151f
    const val DOTS_Y = 168f
    const val HINT_Y = 184f

    /**
     * The lowest height, in units, the dhikr's lines may reach under [fontScale]: [DHIKR_GAP] above
     * the letters of "ROUND n" beneath it, which rise as the font size grows it. A dhikr that would
     * reach lower is drawn smaller, down to its floor, and at its floor in as many of its lines as
     * end above it (`LineText`'s `bottom`): two lines in a script with a deep descent, as the watch's
     * Naskh's is, ran into the round (review round 1). A built-in above its transliteration
     * ([transliterated]) has none, and keeps the drawing's size.
     */
    fun dhikrBottom(transliterated: Boolean, fontScale: Float): Float? =
        if (transliterated) null else round.top(ROUND_Y, fontScale) - DHIKR_GAP

    private const val DHIKR_GAP = 1f

    /** The phrase list's title, a row's name and its target. */
    val title = Line(12.5f, 1.3f)
    val rowName = Line(11f, 1.3f)
    val rowTarget = Line(10f, 1.3f)

    /** A phrase row's height in units, which grows with its name as drawn ([drawn], in units): 25 at the drawing's 11. */
    fun rowHeight(drawn: Float): Float = ROW_HEIGHT * drawn / rowName.units

    private const val ROW_HEIGHT = 25f
}

/**
 * What the counter writes (`gTasbeeh`), in the phone's words, digits and casing: the dhikr as the
 * phone shows it (the Arabic of a built-in, a phrase of his own as typed) on its [dhikrLine], its
 * transliteration only under a Latin-script interface, "ROUND n", the count, "of N", and the hint
 * at 0 of round 1 alone.
 */
internal data class CounterLines(
    val dhikr: String,
    val dhikrLine: Line,
    val transliteration: String?,
    val round: String,
    val count: String,
    val of: String,
    val hint: String?,
)

/**
 * [ui] as [feed]'s phone writes it. Numbers are in the feed's digits ([WatchRules.digits]); the
 * round is in capitals only under a Latin-script interface, cased in the phone's language, as
 * Today's label and the face's are.
 */
internal fun counterLines(feed: WatchFeed, ui: TasbeehUi): CounterLines {
    val words = feed.words()
    val dhikr = ui.preset.parts.getOrNull(ui.currentPart)?.dhikr
    val transliteration = dhikr?.transliteration?.takeIf { feed.ui.latinScript }
    val round = WatchRules.filled(words.round, WatchRules.digits(feed, ui.state.round.toLong()))
    return CounterLines(
        dhikr = dhikr?.arabic.orEmpty(),
        dhikrLine = TasbeehType.dhikr(transliterated = transliteration != null),
        transliteration = transliteration,
        round = if (feed.ui.latinScript) round.uppercase(Locale.forLanguageTag(feed.ui.language)) else round,
        count = WatchRules.digits(feed, ui.count.toLong()),
        of = WatchRules.filled(words.of, WatchRules.digits(feed, ui.preset.total.toLong())),
        hint = words.bezelHint.takeIf { ui.state.count == 0 && ui.state.round == 1 },
    )
}

/**
 * The watch's tasbeeh counter. Its rules are the phone's (`TasbeehViewModel`), restated over the
 * phone's own engine, which `:watchcore` holds:
 * - a set's end holds its closed ring for [TasbeehEngine.SET_COMPLETE_HOLD_MS], then rolls over to
 *   0 of the next round, which is written at once. A count, a turn back, a choice of phrase or a
 *   reset cancels the hold: a count inside it opens the next round at 1, and a turn back stays in
 *   the round;
 * - the count is written [WRITE_DEBOUNCE_MS] after the last change, once for a whole burst;
 * - [flush] settles a pending hold first, so what is written on the way out is the round about to
 *   open, never the hundred; it writes at once, on the caller's thread;
 * - a choice of phrase writes the one left first, so each phrase keeps its own count;
 * - [setPhrases] falls back to [TasbeehPresets.DEFAULT_ID] when the phrase chosen has gone from
 *   the phone.
 *
 * Beside them, the bezel: [turn] applies one count for each detent clockwise and one [undo] for
 * each the other way, in order, so every detent is counted. An undo is the engine's
 * ([TasbeehEngine.undo]): it never goes below 0 or into the round before.
 *
 * **The haptics, the watch's own rule (call 14).** A new vibration cancels the one still playing,
 * so a tick sent just after a part's buzz would cut it short. Each [count], [undo] or [turn] sends
 * at most one haptic, the strongest it crossed: the set's end, else a part's end, else a tick. A
 * part's or the set's is always sent, and starts a quiet window as long as its waveform
 * ([HapticPatterns.PART_MS], [HapticPatterns.SET_MS]) from [clock]; no tick is sent inside it,
 * though the count itself is counted. A change that changes nothing (an undo at 0) is silent.
 *
 * It lives above the pager, in the activity, on a [scope] that outlives the page: the pager
 * composes only the page in front, so a page's own scope would end, with the last 300 ms of counts
 * and the hold, at the first swipe away. The page flushes it whenever the counter stops being the
 * page in front and active, and the activity on pause (erratum p3 #8). Every call comes on the
 * main thread, as the [scope]'s own work does.
 */
internal class WatchTasbeeh(
    private val memory: TasbeehMemory,
    private val feel: Feel,
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private var phrases: List<TasbeehPreset> = TasbeehPresets.builtIn

    private val _ui = MutableStateFlow(
        (phrases.firstOrNull { it.id == memory.selected() } ?: TasbeehPresets.default).let { preset ->
            TasbeehUi.of(preset, read(preset))
        },
    )
    val ui: StateFlow<TasbeehUi> = _ui.asStateFlow()

    /** The pending debounced write, or null. */
    private var writeJob: Job? = null

    /** The pending rollover of a finished set, or null. */
    private var holdJob: Job? = null

    /** Until when, on [clock], a part's or the set's buzz still plays, and no tick may cut it. */
    private var quietUntil = Long.MIN_VALUE

    init {
        holdIfComplete()
    }

    /** One count: a tap, or a click of the bezel clockwise. */
    fun count() = turn(1)

    /** One count taken away: the bezel's click back, or the screen reader's "Take one away". */
    fun undo() = turn(-1)

    /** [detents] clicks of the bezel: each positive one counts, each negative one takes one away. */
    fun turn(detents: Int) {
        if (detents == 0) return
        val current = _ui.value
        var state = current.state
        // The strongest end the turn crossed, a part's or the set's, and the latest of its kind.
        var crossed: TapEvent? = null
        repeat(abs(detents)) {
            state = if (detents > 0) {
                val (next, event) = TasbeehEngine.tap(state, current.preset)
                if (event != TapEvent.Tick && strength(event) >= strength(crossed)) crossed = event
                next
            } else {
                TasbeehEngine.undo(state, current.preset) ?: return@repeat
            }
        }
        if (state == current.state) return
        holdJob?.cancel()
        holdJob = null
        _ui.value = TasbeehUi.of(current.preset, state, crossed)
        buzz(crossed)
        scheduleWrite()
        holdIfComplete()
    }

    /** Chooses the phrase [id], if it is one of the list's; the one left keeps its count. */
    fun select(id: String) {
        val current = _ui.value
        if (id == current.preset.id) return
        val preset = phrases.firstOrNull { it.id == id } ?: return
        leave(current)
        memory.select(id)
        show(preset, read(preset))
    }

    /** Back to 0 of round 1 for the phrase on screen ("Reset the count for this dhikr?"), written at once. */
    fun reset() {
        holdJob?.cancel()
        holdJob = null
        val current = _ui.value
        _ui.value = TasbeehUi.of(current.preset, TasbeehEngine.reset(current.state))
        flush()
    }

    /**
     * The phrases the phone has sent ([PhraseList.of]). The phrase chosen is found in them again: if
     * it has gone, the counter falls back to After prayer, and that becomes the choice; if the phone
     * has changed its words or its target, the counter shows the new ones, its count held to the
     * new target.
     */
    fun setPhrases(list: List<TasbeehPreset>) {
        phrases = list
        val wanted = memory.selected() ?: TasbeehPresets.DEFAULT_ID
        val found = list.firstOrNull { it.id == wanted }
        if (found == null && wanted != TasbeehPresets.DEFAULT_ID) memory.select(TasbeehPresets.DEFAULT_ID)
        val target = found ?: list.firstOrNull { it.id == TasbeehPresets.DEFAULT_ID } ?: TasbeehPresets.default
        val current = _ui.value
        if (target.id == current.preset.id) {
            if (target != current.preset) {
                show(target, TasbeehCodec.decode(target.id, TasbeehCodec.encode(current.state), target.total), flash = current.flash)
            }
            return
        }
        leave(current)
        show(target, read(target))
    }

    /** Writes the count now, the hold settled first. The activity calls it when the counter is left, and on pause. */
    fun flush() {
        writeJob?.cancel()
        writeJob = null
        settle()
        save(_ui.value)
    }

    /** Settles the phrase on screen and writes it, before another takes its place. */
    private fun leave(current: TasbeehUi) {
        writeJob?.cancel()
        writeJob = null
        settle()
        save(_ui.value.takeIf { it.preset.id == current.preset.id } ?: current)
    }

    private fun show(preset: TasbeehPreset, state: TasbeehState, flash: TapEvent? = null) {
        holdJob?.cancel()
        holdJob = null
        _ui.value = TasbeehUi.of(preset, state, flash)
        holdIfComplete()
    }

    private fun read(preset: TasbeehPreset): TasbeehState = TasbeehCodec.decode(preset.id, memory.state(preset.id), preset.total)

    private fun save(ui: TasbeehUi) {
        memory.save(ui.preset.id, TasbeehCodec.encode(ui.state))
    }

    /** The one haptic for a change: the set's or a part's end it crossed, always, or else a tick, unless a buzz still plays. */
    private fun buzz(crossed: TapEvent?) {
        when (crossed) {
            TapEvent.SetComplete -> {
                feel.setComplete()
                quietUntil = clock() + HapticPatterns.SET_MS
            }
            is TapEvent.PartComplete -> {
                feel.partComplete()
                quietUntil = clock() + HapticPatterns.PART_MS
            }
            TapEvent.Tick, null -> if (clock() >= quietUntil) feel.count()
        }
    }

    private fun scheduleWrite() {
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            save(_ui.value)
        }
    }

    /**
     * Holds a finished set for [TasbeehEngine.SET_COMPLETE_HOLD_MS], then rolls it over. A set read
     * back finished (a process ended inside the hold, after the debounce wrote the hundred) rolls
     * over as a fresh one does.
     */
    private fun holdIfComplete() {
        val ui = _ui.value
        if (ui.preset.total <= 0 || ui.state.count < ui.preset.total) return
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(TasbeehEngine.SET_COMPLETE_HOLD_MS)
            holdJob = null
            rollOver()
        }
    }

    /** The pending rollover applied now, if there is one. */
    private fun settle() {
        val pending = holdJob ?: return
        holdJob = null
        pending.cancel()
        rollOver()
    }

    /** A finished set becomes 0 of the next round, written at once; anything else is left as it is. */
    private fun rollOver() {
        val ui = _ui.value
        if (ui.preset.total <= 0 || ui.state.count < ui.preset.total) return
        val next = TasbeehUi.of(ui.preset, TasbeehEngine.nextRound(ui.state))
        _ui.value = next
        save(next)
    }

    /** How strong an end's haptic is: none, a part's, the set's. */
    private fun strength(event: TapEvent?): Int = when (event) {
        null, TapEvent.Tick -> 0
        is TapEvent.PartComplete -> 1
        TapEvent.SetComplete -> 2
    }

    companion object {
        /** One write this long after the last change, not one for each: the phone's (spec §6). */
        const val WRITE_DEBOUNCE_MS = 300L
    }
}
