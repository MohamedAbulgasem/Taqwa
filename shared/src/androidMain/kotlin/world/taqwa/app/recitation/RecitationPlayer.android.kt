package world.taqwa.app.recitation

import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import world.taqwa.app.settings.appContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * The app's side of [RecitationService]: a `MediaController` bound lazily, and a [PlaybackState]
 * derived from it.
 *
 * Nothing here happens until [load]. Building this class does not bind the service, does not
 * claim audio focus and does not post a notification — `AppContainer` holds one for the life of
 * the process and a session that never opens the Quran never costs a service.
 */
@UnstableApi
actual class RecitationPlayer actual constructor(
    private val library: RecitationLibrary,
) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(PlaybackState.EMPTY)
    actual val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** A lock-screen previous/next, relayed by the service as a custom command (spec §15.1). */
    private val _skips = MutableSharedFlow<SurahSkip>(extraBufferCapacity = 4)
    actual val skips: SharedFlow<SurahSkip> = _skips.asSharedFlow()

    /** The surah read itself out (spec §16.1); the controller answers with `load` or `stop`. */
    private val _surahEnds = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    actual val surahEnds: SharedFlow<Int> = _surahEnds.asSharedFlow()

    /** The hold's own deadline: [stop], when the controller has answered with neither. */
    private var holdJob: Job? = null

    private var connecting: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var queue: RecitationQueue? = null

    /**
     * The surah's clock (spec §14.1), read off the container at [load] and sent to the service
     * with the queue's text, so both ends are on one clock. Everything the session then reports
     * — position, duration, buffered position — is on it (see [AyahPlayer]), and the ayah-level
     * numbers the bar's previous button needs are derived back from it here.
     */
    private var timeline: SurahTimeline? = null
    private var reciterId: String? = null
    private var ticker: Job? = null

    /**
     * Read-aloud (spec §5.3): what the loaded queue reads, and what [setSpeech] needs to build the
     * same surah again with another. [generation] names each queue's speech items, so the service
     * never plays a WAV it made for a queue that has since been replaced.
     */
    private var speech: SpokenTranslation? = null
    private var generation = 0L
    private var nowText: NowPlayingText? = null
    private var gapMs = 0L
    private var ayahDurations: Map<Int, Long> = emptyMap()

    /**
     * The last position and duration read while an *ayah* was playing. During the reciter's gap
     * the platform's clock is the gap's, and a progress line that jumped back to zero for 300 ms
     * between every two ayahs is exactly the flicker `RecitationQueue` exists to prevent.
     */
    private var ayahPositionMs = 0L
    private var ayahDurationMs = 0L

    /**
     * True from the moment the last ayah has played out until a `load` takes the session on or the
     * hold lapses into [stop] (spec §16.1). The end arrives inside a player callback, and is
     * reported from there several times over as the events that follow it land; this is what
     * makes it one hold and one report.
     */
    private var ending = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    /**
     * The service released its session and went — it does that when the app is swiped out of
     * Recents with nothing playing. There is no player any more, so the bar must not go on
     * showing a paused surah, and the dead controller must not be handed out again: the next
     * [load] builds a new one, which starts a new service.
     */
    private val connectionListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (controller !== this@RecitationPlayer.controller) return
            stopTicker()
            leaveHold()
            queue = null
            reciterId = null
            speech = null
            releaseController()
            _state.value = PlaybackState.EMPTY
        }

        /** The service's phase changed (read-aloud spec §5.5): the voice began or stopped reading. */
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            publish()
        }

        /** The service saying a lock screen, headset or car pressed previous or next. */
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                RecitationService.COMMAND_SURAH_NEXT -> _skips.tryEmit(SurahSkip.NEXT)
                RecitationService.COMMAND_SURAH_PREVIOUS -> _skips.tryEmit(SurahSkip.PREVIOUS)
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** The controller, but only while it is any use. */
    private val live: MediaController? get() = controller?.takeIf { it.isConnected }

    actual suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText, speech: SpokenTranslation?) {
        // First of all, before the container is even read: a load is the answer the hold was
        // waiting for, and the hold must not lapse into a stop while the answer is under way.
        leaveHold()
        val opened = withContext(Dispatchers.IO) {
            runCatching {
                val taqa = TaqaFile(library.fileFor(reciter.id, surah), FileSystem.SYSTEM)
                taqa.index() to taqa.ayahDurationsMs()
            }.getOrNull()
        } ?: return
        val (index, durations) = opened
        val built = RecitationQueue.of(index, reciter.gapMs.toLong(), speech?.spoken.orEmpty())
        // The clock counts what is heard (read-aloud spec §2): the breath before each
        // translation, and the translation's own slot as estimated from its length.
        val clock = SurahTimeline.of(built, { durations[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }
        val startIndex = built.indexOfAyah(startAyah) ?: 0
        val bound = runCatching { connect() }.getOrNull() ?: return

        queue = built
        timeline = clock
        reciterId = reciter.id
        this.speech = speech
        generation++
        nowText = text
        gapMs = reciter.gapMs.toLong()
        ayahDurations = durations
        ayahPositionMs = 0L
        ayahDurationMs = 0L

        sendNowPlaying(bound, text, clock)
        bound.setMediaItems(built.items.map { item(reciter.id, surah, it) }, startIndex, 0L)
        bound.prepare()
        bound.play()
        publish()
    }

    /**
     * The notification's text, the clock and read-aloud's script, sent ahead of the queue they
     * describe. Commands from one controller reach the session in order, so the service has the
     * script before the first speech item can ask it for anything.
     */
    private fun sendNowPlaying(bound: MediaController, text: NowPlayingText, clock: SurahTimeline) {
        val spoken = speech
        bound.sendCustomCommand(
            SessionCommand(RecitationService.COMMAND_NOW_PLAYING, Bundle.EMPTY),
            Bundle().apply {
                putString(RecitationService.ARG_TITLE, text.title)
                putString(RecitationService.ARG_SUBTITLE, text.subtitle)
                putString(RecitationService.ARG_PREVIOUS_AYAH, text.previousAyahLabel)
                putString(RecitationService.ARG_NEXT_AYAH, text.nextAyahLabel)
                putLongArray(RecitationService.ARG_TIMELINE, clock.itemsMs.toLongArray())
                if (spoken != null) {
                    val entries = spoken.texts.entries.sortedBy { it.key }
                    putLong(RecitationService.ARG_SPEECH_GENERATION, generation)
                    putString(RecitationService.ARG_SPEECH_ENGINE, spoken.voice.engine)
                    putString(RecitationService.ARG_SPEECH_VOICE, spoken.voice.id)
                    putString(RecitationService.ARG_SPEECH_LANGUAGE, spoken.language)
                    putIntArray(RecitationService.ARG_SPEECH_AYAHS, entries.map { it.key }.toIntArray())
                    putStringArray(RecitationService.ARG_SPEECH_TEXTS, entries.map { it.value }.toTypedArray())
                }
            },
        )
    }

    /**
     * The same surah built again around the ayah being heard, with [speech] or without (read-aloud
     * spec §2). Inside an ayah, that ayah carries on from where it is; in a translation or a
     * silence, the next ayah starts — or, after the last one, the last ayah's end, from where the
     * new queue plays whatever it still has after that ayah and then ends as it would have. One
     * `setMediaItems` at that point: a fraction of a second of re-buffering, and nothing more.
     */
    actual fun setSpeech(speech: SpokenTranslation?) {
        val bound = live ?: return
        val old = queue ?: return
        val reciter = reciterId ?: return
        val text = nowText ?: return
        val built = RecitationQueue(old.surah, old.ayahs, gapMs, speech?.spoken.orEmpty())
        val clock = SurahTimeline.of(built, { ayahDurations[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }
        val ayah = _state.value.ayah ?: old.ayahs.first()
        val ayahIndex = built.indexOfAyah(ayah) ?: 0
        val (startIndex, startMs) = when {
            phase() == RecitationService.PHASE_AYAH -> ayahIndex to ayahPositionMs
            else -> built.next(ayahIndex)?.let { it to 0L } ?: (ayahIndex to (ayahDurations[ayah] ?: 0L))
        }
        this.speech = speech
        generation++
        queue = built
        timeline = clock
        sendNowPlaying(bound, text, clock)
        bound.setMediaItems(built.items.map { item(reciter, built.surah, it) }, startIndex, startMs)
        bound.prepare()
        publish()
    }

    /** The real current item's kind, as the service publishes it (read-aloud spec §5.5). */
    private fun phase(): Int =
        live?.sessionExtras?.getInt(RecitationService.EXTRA_PHASE, RecitationService.PHASE_AYAH)
            ?: RecitationService.PHASE_AYAH

    actual fun play() {
        val bound = live ?: return
        bound.play()
    }

    actual fun pause() {
        live?.pause()
    }

    actual fun toggle() {
        if (_state.value.playing) pause() else play()
    }

    actual fun seekToAyah(n: Int) {
        val bound = live ?: return
        val target = queue?.indexOfAyah(n) ?: return
        bound.seekTo(target, 0L)
    }

    // The controller's seek arrives at the session's player, which is `AyahPlayer`: the snap to
    // the ayah's start happens there, in the one place the lock screen's scrub already uses.
    actual fun seekToSurahTime(positionMs: Long) {
        val bound = live ?: return
        if (_state.value.surahDurationMs <= 0L) return
        bound.seekTo(positionMs.coerceIn(0L, _state.value.surahDurationMs))
    }

    actual fun next() {
        val bound = live ?: return
        val target = queue?.next(bound.currentMediaItemIndex) ?: return
        bound.seekTo(target, 0L)
    }

    actual fun previous() {
        val bound = live ?: return
        val built = queue ?: return
        val at = bound.currentMediaItemIndex
        // The session reports the ayah a silence or a translation follows (see [AyahPlayer]), so
        // the service's phase is what says that ayah has already been read: a press then
        // restarts it, however short it was, as the lock screen's own previous does (read-aloud
        // spec §2). Otherwise the queue's rule, on the position inside the ayah.
        val finished = !built.isAyah(at) || phase() != RecitationService.PHASE_AYAH
        val target = if (finished) built.ayahIndexAt(at) else built.previous(at, withinAyah(bound, built, at))
        bound.seekTo(target, 0L)
    }

    /**
     * True when the session is reporting the surah's clock rather than the item's — which it
     * does whenever [AyahPlayer] holds a clock that matches its queue. Asked rather than assumed:
     * a duration that is the whole surah's is the one thing the two cannot be confused on.
     */
    private fun onSurahClock(bound: MediaController, clock: SurahTimeline): Boolean =
        bound.duration != C.TIME_UNSET && abs(bound.duration - clock.totalMs) <= CLOCK_TOLERANCE_MS

    /** The position inside the ayah at [at], whichever clock the session is on. */
    private fun withinAyah(bound: MediaController, built: RecitationQueue, at: Int): Long {
        val position = bound.currentPosition.coerceAtLeast(0L)
        val clock = timeline?.takeIf { it.size == built.size } ?: return position
        return if (onSurahClock(bound, clock)) (position - clock.startOf(at)).coerceAtLeast(0L) else position
    }

    actual fun stop() {
        ticker?.cancel()
        ticker = null
        leaveHold()
        queue = null
        timeline = null
        reciterId = null
        speech = null
        live?.let { bound ->
            bound.stop()
            bound.clearMediaItems()
        }
        // An empty, stopped player is what takes the notification down; letting go of the
        // controller then leaves the service unbound, so it is destroyed with the process rather
        // than holding it up. The service object itself outlives this — it is the app's one
        // session, and the next `load` finds it warm — but it costs nothing while it is idle.
        releaseController()
        _state.value = PlaybackState.EMPTY
    }

    actual fun release() {
        ticker?.cancel()
        holdJob?.cancel()
        releaseController()
        scope.cancel()
        _state.value = PlaybackState.EMPTY
    }

    private fun releaseController() {
        controller?.removeListener(listener)
        controller = null
        connecting?.let { MediaController.releaseFuture(it) }
        connecting = null
    }

    private suspend fun connect(): MediaController {
        live?.let { return it }
        // A controller left over from a service that has gone still holds a released future.
        releaseController()
        val token = SessionToken(appContext, ComponentName(appContext, RecitationService::class.java))
        val future = MediaController.Builder(appContext, token)
            .setListener(connectionListener)
            .buildAsync()
        connecting = future
        val bound = suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    if (!continuation.isActive) return@addListener
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(appContext),
            )
        }
        bound.addListener(listener)
        controller = bound
        return bound
    }

    /**
     * Id only: the URI is stripped crossing the binder anyway and the service puts it back, along
     * with the title, the subtitle and the artwork. The session reads the queue back off these ids
     * ([queueItemsOf]).
     */
    private fun item(reciterId: String, surah: Int, entry: QueueItem): MediaItem =
        MediaItem.Builder().setMediaId(mediaIdOf(entry, reciterId, surah, generation)).build()

    private fun publish() {
        val bound = live
        val built = queue
        if (bound == null || built == null || bound.mediaItemCount == 0) return
        if (bound.playbackState == Player.STATE_ENDED) {
            hold(built)
            return
        }
        // A seek back into the surah during the hold — the lock screen's previous, a tap on an
        // ayah — has the surah playing again; the hold must not lapse into a stop under it.
        if (ending) leaveHold()
        // Never a gap or a translation: the session's `AyahPlayer` reports the ayah either follows
        // as the current item, so the app never sees their index. Meanwhile the clock sits inside
        // their slot, which the arithmetic below reads as the ayah's end — the line of an ayah
        // that has just been read stays full, as it always did — and the service's phase is what
        // says the voice is reading.
        val at = bound.currentMediaItemIndex
        val clock = timeline?.takeIf { it.size == built.size }
        var surahPositionMs = 0L
        var surahDurationMs = 0L
        if (clock != null) {
            // The session is on the surah's clock (spec §14.1): the ayah-level pair the bar's
            // previous button still needs is read back off it.
            val reported = bound.currentPosition.coerceAtLeast(0L)
            surahPositionMs = if (onSurahClock(bound, clock)) {
                reported.coerceAtMost(clock.totalMs)
            } else {
                clock.elapsed(at, reported)
            }
            surahDurationMs = clock.totalMs
            ayahDurationMs = clock.durationOf(at)
            ayahPositionMs = (surahPositionMs - clock.startOf(at)).coerceIn(0L, ayahDurationMs)
        } else {
            ayahPositionMs = bound.currentPosition.coerceAtLeast(0L)
            ayahDurationMs = bound.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L)
                ?: ayahDurationMs
        }
        _state.value = PlaybackState(
            reciterId = reciterId,
            surah = built.surah,
            ayah = built.ayahAt(at),
            ayahCount = built.ayahCount,
            positionMs = ayahPositionMs,
            durationMs = ayahDurationMs,
            playing = bound.playWhenReady &&
                bound.playbackState != Player.STATE_IDLE &&
                bound.playbackState != Player.STATE_ENDED,
            buffering = bound.playbackState == Player.STATE_BUFFERING,
            surahPositionMs = surahPositionMs,
            surahDurationMs = surahDurationMs,
            speaking = phase() == RecitationService.PHASE_SPEECH,
        )
        if (bound.isPlaying) startTicker() else stopTicker()
    }

    /**
     * The surah has read itself out (spec §16.1). Nothing is torn down yet: the bar shows the
     * last ayah with its line full and its play button up, the notification and the service
     * stand, and the controller is told — its answer is a plain [load] into this same session
     * (the next surah, with nothing flickering and audio focus never given up and taken back) or
     * a [stop]. Should neither arrive within [SURAH_END_HOLD_MS] the hold lapses into [stop] on
     * its own: a surah that simply ended once left a `NO_CLEAR` notification and a foreground
     * service standing indefinitely for a player with nothing to play, and that must not come
     * back by way of a controller that never answered.
     *
     * The lapse is posted rather than run here. This is reached from a `Player.Listener`
     * callback, and stopping the controller and dropping it from inside one is exactly the
     * re-entrancy Media3's listener set is not there to survive; `Dispatchers.Main` (not the
     * scope's `immediate`) is what makes it the next thing the main thread does instead of a
     * nested one.
     */
    private fun hold(built: RecitationQueue) {
        if (ending) return
        ending = true
        stopTicker()
        val clock = timeline?.takeIf { it.size == built.size }
        val last = built.size - 1
        _state.value = PlaybackState(
            reciterId = reciterId,
            surah = built.surah,
            ayah = built.ayahAt(last),
            ayahCount = built.ayahCount,
            positionMs = ayahDurationMs,
            durationMs = ayahDurationMs,
            playing = false,
            buffering = false,
            surahPositionMs = clock?.totalMs ?: _state.value.surahPositionMs,
            surahDurationMs = clock?.totalMs ?: _state.value.surahDurationMs,
        )
        _surahEnds.tryEmit(built.surah)
        holdJob = scope.launch(Dispatchers.Main) {
            delay(SURAH_END_HOLD_MS)
            if (ending) stop()
        }
    }

    private fun leaveHold() {
        ending = false
        holdJob?.cancel()
        holdJob = null
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(POLL_MS)
                val bound = live ?: break
                if (!bound.isPlaying) break
                publish()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        /** Fine enough for a progress line at 60 Hz-ish and cheap enough to run in the app. */
        const val POLL_MS = 250L

        /** How far the session's duration may sit from the clock's total and still be the clock. */
        const val CLOCK_TOLERANCE_MS = 1_000L
    }
}
