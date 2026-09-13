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
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
     * The last position and duration read while an *ayah* was playing. During the reciter's gap
     * the platform's clock is the gap's, and a progress line that jumped back to zero for 300 ms
     * between every two ayahs is exactly the flicker `RecitationQueue` exists to prevent.
     */
    private var ayahPositionMs = 0L
    private var ayahDurationMs = 0L

    /**
     * True from the moment the last ayah has played out until the teardown it starts has finished.
     * The end arrives inside a player callback, and the teardown it wants — stop the player, drop
     * the queue, let go of the controller — cannot be run from inside one; it is posted instead,
     * and this is what stops the several events that follow the end posting it again.
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
            queue = null
            reciterId = null
            releaseController()
            _state.value = PlaybackState.EMPTY
        }
    }

    /** The controller, but only while it is any use. */
    private val live: MediaController? get() = controller?.takeIf { it.isConnected }

    actual suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText) {
        val opened = withContext(Dispatchers.IO) {
            runCatching {
                val taqa = TaqaFile(library.fileFor(reciter.id, surah), FileSystem.SYSTEM)
                taqa.index() to taqa.ayahDurationsMs()
            }.getOrNull()
        } ?: return
        val (index, durations) = opened
        val built = RecitationQueue.of(index, reciter.gapMs.toLong())
        val clock = SurahTimeline.of(built) { durations[it] ?: 0L }
        val startIndex = built.indexOfAyah(startAyah) ?: 0
        val bound = runCatching { connect() }.getOrNull() ?: return

        queue = built
        timeline = clock
        reciterId = reciter.id
        ayahPositionMs = 0L
        ayahDurationMs = 0L
        ending = false

        bound.sendCustomCommand(
            SessionCommand(RecitationService.COMMAND_NOW_PLAYING, Bundle.EMPTY),
            Bundle().apply {
                putString(RecitationService.ARG_TITLE, text.title)
                putString(RecitationService.ARG_SUBTITLE, text.subtitle)
                putLongArray(RecitationService.ARG_TIMELINE, clock.itemsMs.toLongArray())
            },
        )
        bound.setMediaItems(built.items.map { item(reciter.id, surah, it) }, startIndex, 0L)
        bound.prepare()
        bound.play()
        publish()
    }

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

    actual fun next() {
        val bound = live ?: return
        val target = queue?.next(bound.currentMediaItemIndex) ?: return
        bound.seekTo(target, 0L)
    }

    actual fun previous() {
        val bound = live ?: return
        val built = queue ?: return
        val at = bound.currentMediaItemIndex
        bound.seekTo(built.previous(at, if (built.isGap(at)) 0L else withinAyah(bound, built, at)), 0L)
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
        ending = false
        queue = null
        timeline = null
        reciterId = null
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

    private fun item(reciterId: String, surah: Int, entry: QueueItem): MediaItem {
        val id = when (entry) {
            is QueueItem.Ayah -> ayahUri(reciterId, surah, entry.n)
            is QueueItem.Gap -> gapUri(entry.durationMs)
        }
        // Id only: the URI is stripped crossing the binder anyway and the service puts it back,
        // along with the title, the subtitle and the artwork.
        return MediaItem.Builder().setMediaId(id).build()
    }

    private fun publish() {
        val bound = live
        val built = queue
        if (bound == null || built == null || bound.mediaItemCount == 0) return
        if (bound.playbackState == Player.STATE_ENDED) {
            end()
            return
        }
        // Never a gap: the session's `AyahPlayer` reports the ayah a gap follows as the current
        // item, so the app never sees a gap index. During the gap the clock sits inside the gap's
        // slot, which the arithmetic below reads as the ayah's end — the line of an ayah that has
        // just been read stays full, as it always did.
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
        )
        if (bound.isPlaying) startTicker() else stopTicker()
    }

    /**
     * The surah has read itself out. There is nothing left to play, so the bar, the media session
     * and the foreground service all go — the same teardown the bar's × performs, because a
     * player with an empty queue is what takes the notification down and lets the service be
     * destroyed. A surah that simply ended used to leave all three standing: a `NO_CLEAR`
     * notification and a foreground service, indefinitely, for a player with nothing to play.
     *
     * Posted rather than run here. This is reached from a `Player.Listener` callback, and stopping
     * the controller and dropping it from inside one is exactly the re-entrancy Media3's listener
     * set is not there to survive; `Dispatchers.Main` (not the scope's `immediate`) is what makes
     * it the next thing the main thread does instead of a nested one.
     */
    private fun end() {
        if (ending) return
        ending = true
        stopTicker()
        scope.launch(Dispatchers.Main) { stop() }
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
