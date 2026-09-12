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
    private var reciterId: String? = null
    private var ticker: Job? = null

    /**
     * The last position and duration read while an *ayah* was playing. During the reciter's gap
     * the platform's clock is the gap's, and a progress line that jumped back to zero for 300 ms
     * between every two ayahs is exactly the flicker `RecitationQueue` exists to prevent.
     */
    private var ayahPositionMs = 0L
    private var ayahDurationMs = 0L

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
        val index = withContext(Dispatchers.IO) {
            runCatching { TaqaFile(library.fileFor(reciter.id, surah), FileSystem.SYSTEM).index() }
                .getOrNull()
        } ?: return
        val built = RecitationQueue.of(index, reciter.gapMs.toLong())
        val startIndex = built.indexOfAyah(startAyah) ?: 0
        val bound = runCatching { connect() }.getOrNull() ?: return

        queue = built
        reciterId = reciter.id
        ayahPositionMs = 0L
        ayahDurationMs = 0L

        bound.sendCustomCommand(
            SessionCommand(RecitationService.COMMAND_NOW_PLAYING, Bundle.EMPTY),
            Bundle().apply {
                putString(RecitationService.ARG_TITLE, text.title)
                putString(RecitationService.ARG_SUBTITLE, text.subtitle)
            },
        )
        bound.setMediaItems(built.items.map { item(reciter.id, surah, it) }, startIndex, 0L)
        bound.prepare()
        bound.play()
        publish()
    }

    actual fun play() {
        val bound = live ?: return
        if (bound.playbackState == Player.STATE_ENDED) bound.seekTo(0, 0L)
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
        bound.seekTo(built.previous(at, if (built.isGap(at)) 0L else bound.currentPosition), 0L)
    }

    actual fun stop() {
        ticker?.cancel()
        ticker = null
        queue = null
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
        val at = bound.currentMediaItemIndex
        val gap = built.isGap(at)
        if (!gap) {
            ayahPositionMs = bound.currentPosition.coerceAtLeast(0L)
            ayahDurationMs = bound.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L)
                ?: ayahDurationMs
        }
        _state.value = PlaybackState(
            reciterId = reciterId,
            surah = built.surah,
            ayah = built.ayahAt(at),
            ayahCount = built.ayahCount,
            // While the gap plays the ayah is over: its progress line stays full rather than
            // rewinding, which is what the eye expects of an ayah that has just been read.
            positionMs = if (gap) ayahDurationMs else ayahPositionMs,
            durationMs = ayahDurationMs,
            playing = bound.playWhenReady &&
                bound.playbackState != Player.STATE_IDLE &&
                bound.playbackState != Player.STATE_ENDED,
            buffering = bound.playbackState == Player.STATE_BUFFERING,
        )
        if (bound.isPlaying) startTicker() else stopTicker()
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
    }
}
