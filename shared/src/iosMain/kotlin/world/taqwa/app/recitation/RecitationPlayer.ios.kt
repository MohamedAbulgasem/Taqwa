package world.taqwa.app.recitation

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
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
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.CoreGraphics.CGSize
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.NSObjectProtocol
import kotlin.time.TimeSource

/**
 * Recitation on iOS (spec §6): one `AVPlayer` driven through a queue this class keeps itself,
 * `AVAudioSession` in the playback category so it survives the screen locking, and
 * `MPNowPlayingInfoCenter`/`MPRemoteCommandCenter` for the lock screen.
 *
 * **Why not `AVQueuePlayer`.** A queue player advances between items on its own, at a moment this
 * app does not get told about precisely and cannot put a gap into. The reciter's inter-ayah
 * silence (spec §2) and the exact ayah boundary the reader highlights on are the two things this
 * feature is built around, so the queue is walked by hand: play one ayah, be told it ended, wait
 * the gap, put the next one in. One `AVPlayer` and one item at a time.
 *
 * **Why per-ayah files.** The ayahs live inside the surah's `.taqa` as a byte range each, and
 * AVFoundation will not read a range of a file without an `AVAssetResourceLoaderDelegate` and a
 * custom URL scheme. Splitting the container into `Library/Caches/recitation/...` on load is a
 * few lines instead, and Caches is the right place for it: the files can be rebuilt from the
 * container at any time and iOS may take them back when the disk is tight.
 *
 * Everything that touches AVFoundation or MediaPlayer runs on the main thread.
 */
@OptIn(ExperimentalForeignApi::class)
actual class RecitationPlayer actual constructor(
    private val library: RecitationLibrary,
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(PlaybackState.EMPTY)
    actual val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: AVPlayer? = null
    private var queue: RecitationQueue? = null

    /** The surah's clock (spec §14.1), read off the container at [load]; see [SurahTimeline]. */
    private var timeline: SurahTimeline? = null
    private var files: Map<Int, String> = emptyMap()
    private var reciterId: String? = null
    private var text: NowPlayingText? = null

    private var at: Int = 0
    private var wantsPlay: Boolean = false
    private var gapJob: Job? = null
    private var ticker: Job? = null
    private var observers: MutableList<NSObjectProtocol> = mutableListOf()

    /**
     * The did-play-to-end observer of the ayah currently in the player, registered against that
     * one `AVPlayerItem` rather than against every item in the process.
     *
     * This started as one observer for the whole player, taking any did-play-to-end notification
     * and asking `notification.object === player.currentItem` whether it was ours. On a simulator
     * that comparison was never true: the notification's `object` arrives as `Any?`, and the
     * Kotlin object it is wrapped in is not the one `currentItem` hands back, so every ayah ended
     * and nothing followed it. Naming the item at registration asks no such question.
     */
    private var endObserver: NSObjectProtocol? = null
    private var commandsWired = false

    /** Held across the gap for the same reason as on Android: the ayah's line must not rewind. */
    private var ayahPositionMs = 0L
    private var ayahDurationMs = 0L

    /**
     * How far into the gap the wait has got. The gap is a `delay`, not an item with a clock, so
     * the surah's clock keeps moving through it by reading a monotonic mark taken when the wait
     * began; a pause inside the gap freezes what had elapsed and a play starts the wait again.
     */
    private var gapStarted: TimeSource.Monotonic.ValueTimeMark? = null
    private var gapElapsedMs = 0L

    /** The last surah-clock pair published, for the lock screen's bar. */
    private var surahPositionMs = 0L
    private var surahDurationMs = 0L

    /** Set when an interruption paused us, so playback only resumes if it was our pause. */
    private var pausedByInterruption = false

    /** The app icon, made into artwork once: `UIImage` decoding is not free and it never changes. */
    private var cachedArtwork: MPMediaItemArtwork? = null

    actual suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText) {
        val file = library.fileFor(reciter.id, surah)
        val split = withContext(Dispatchers.Default) {
            runCatching { split(reciter.id, surah, file) }.getOrNull()
        } ?: return
        if (split.files.isEmpty()) return
        val built = RecitationQueue(
            surah = surah,
            ayahs = split.files.keys.sorted(),
            gapMs = reciter.gapMs.toLong(),
        )
        val clock = SurahTimeline.of(built) { split.durationsMs[it] ?: 0L }
        withContext(Dispatchers.Main) {
            queue = built
            timeline = clock
            files = split.files
            reciterId = reciter.id
            this@RecitationPlayer.text = text
            ayahPositionMs = 0L
            ayahDurationMs = 0L
            wantsPlay = true
            ensurePlayer()
            // Not inside `ensurePlayer`: `stop` removes the observers but keeps the `AVPlayer`, so
            // a second `load` would have found the player already built and gone on deaf to
            // interruptions and to the headphones being pulled out.
            observe()
            activateSession()
            wireCommands()
            go(built.indexOfAyah(startAyah) ?: 0)
        }
    }

    actual fun play() {
        val built = queue ?: return
        wantsPlay = true
        pausedByInterruption = false
        activateSession()
        // A gap is playing silence: there is no item to start, the wait simply resumes.
        if (built.isGap(at)) go(at) else player?.play()
        publish()
        startTicker()
    }

    actual fun pause() {
        wantsPlay = false
        gapJob?.cancel()
        gapJob = null
        // Freeze the gap's clock where it is; `play` restarts the whole wait, which is a fraction
        // of a second nobody will hear twice.
        gapElapsedMs = gapElapsedNow()
        gapStarted = null
        player?.pause()
        publish()
    }

    actual fun toggle() {
        if (_state.value.playing) pause() else play()
    }

    actual fun seekToAyah(n: Int) {
        val target = queue?.indexOfAyah(n) ?: return
        go(target)
    }

    actual fun next() {
        val target = queue?.next(at) ?: return
        go(target)
    }

    actual fun previous() {
        val built = queue ?: return
        go(built.previous(at, if (built.isGap(at)) 0L else ayahPositionMs))
    }

    actual fun stop() {
        gapJob?.cancel()
        gapJob = null
        ticker?.cancel()
        ticker = null
        wantsPlay = false
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        queue = null
        timeline = null
        gapStarted = null
        gapElapsedMs = 0L
        surahPositionMs = 0L
        surahDurationMs = 0L
        files = emptyMap()
        reciterId = null
        text = null
        at = 0
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        unwireCommands()
        unobserve()
        // Notify others: whatever was playing before recitation took the session — a podcast, the
        // Quran in another app — is told it may have it back.
        AVAudioSession.sharedInstance().setActive(
            false,
            withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
            error = null,
        )
        _state.value = PlaybackState.EMPTY
    }

    actual fun release() {
        stop()
        player = null
        scope.cancel()
    }

    // ---- the queue --------------------------------------------------------------------------

    /** Starts queue item [index]: an ayah's file, or the wait that is a gap. */
    private fun go(index: Int) {
        val built = queue ?: return
        gapJob?.cancel()
        gapJob = null
        at = index.coerceIn(0, built.size - 1)
        if (built.isGap(at)) {
            player?.pause()
            ayahPositionMs = ayahDurationMs
            gapElapsedMs = 0L
            gapStarted = if (wantsPlay) TimeSource.Monotonic.markNow() else null
            publish()
            if (wantsPlay) {
                gapJob = scope.launch {
                    delay(built.gapMs)
                    val next = built.next(at)
                    if (next != null) go(next) else finish()
                }
            }
            return
        }
        val path = files[built.ayahAt(at)] ?: return
        ayahPositionMs = 0L
        ayahDurationMs = 0L
        gapStarted = null
        gapElapsedMs = 0L
        val item = AVPlayerItem(uRL = NSURL.fileURLWithPath(path))
        observeEndOf(item)
        player?.replaceCurrentItemWithPlayerItem(item)
        if (wantsPlay) player?.play()
        publish()
        startTicker()
    }

    /** The item just played to its end. Move on, or stop at the last ayah of the surah. */
    private fun onItemEnded() {
        val built = queue ?: return
        if (built.isGap(at)) return
        val nextIndex = at + 1
        if (nextIndex >= built.size) {
            finish()
            return
        }
        go(nextIndex)
    }

    /**
     * The surah has read itself out. Everything goes: the bar, the lock screen's now-playing
     * entry, the remote commands and the audio session itself — the same teardown [stop] performs
     * for the bar's ×, because a surah that has ended and one the reader has dismissed leave
     * exactly the same nothing behind. Holding the session open for a player with nothing left to
     * play would keep whatever was playing before recitation from having it back.
     */
    private fun finish() = stop()

    // ---- the container ----------------------------------------------------------------------

    /** What [split] hands back: the per-ayah files, and each ayah's length off the container. */
    private class Split(val files: Map<Int, String>, val durationsMs: Map<Int, Long>)

    /**
     * Splits the surah's container into `Library/Caches/recitation/<reciter>/<surah>/<n>.mp3`,
     * reusing any file already there whose size is the one the index publishes. The bytes written
     * are the corpus's own MP3s, untouched — the licence forbids re-encoding and nothing here
     * decodes anything.
     */
    private fun split(reciterId: String, surah: Int, container: Path): Split {
        val fs = FileSystem.SYSTEM
        val taqa = TaqaFile(container, fs)
        val index = taqa.index()
        val dir = cachesRoot() / reciterId / surah.toString()
        fs.createDirectories(dir)
        val written = LinkedHashMap<Int, String>(index.ayahs.size)
        index.ayahs.forEach { ayah ->
            val out = dir / "${ayah.n}.mp3"
            val existing = fs.metadataOrNull(out)?.size
            if (existing != ayah.len) {
                fs.write(out) { write(taqa.readAyah(ayah.n)) }
            }
            written[ayah.n] = out.toString()
        }
        return Split(written, taqa.ayahDurationsMs())
    }

    private fun cachesRoot(): Path {
        val caches = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
        return caches.toPath() / "recitation"
    }

    // ---- the session, the lock screen and the interruptions -----------------------------------

    private fun ensurePlayer() {
        if (player != null) return
        player = AVPlayer()
        observe()
    }

    private fun activateSession() {
        val session = AVAudioSession.sharedInstance()
        // `spokenAudio` is the mode Apple defines for speech the listener is following, and it is
        // what makes CarPlay and the "pause other audio" behaviours treat recitation correctly.
        session.setCategory(
            AVAudioSessionCategoryPlayback,
            mode = AVAudioSessionModeSpokenAudio,
            options = 0u,
            error = null,
        )
        session.setActive(true, error = null)
    }

    private fun observe() {
        if (observers.isNotEmpty()) return
        val centre = NSNotificationCenter.defaultCenter
        observers += centre.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            onInterruption(notification)
        }
        observers += centre.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)
                ?.unsignedLongValue
            // Headphones pulled out. Never resume by itself: recitation out loud in a room is
            // exactly what the person unplugging did not ask for.
            if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) pause()
        }
    }

    /**
     * A call, an alarm, the app's own adhan. Pause when it begins; resume when it ends only if
     * the system says we may — `shouldResume` is false when the interrupting app is still playing.
     */
    private fun onInterruption(notification: NSNotification?) {
        val info = notification?.userInfo ?: return
        when ((info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue) {
            AVAudioSessionInterruptionTypeBegan -> {
                if (_state.value.playing) {
                    pausedByInterruption = true
                    pause()
                }
            }
            AVAudioSessionInterruptionTypeEnded -> {
                val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)
                    ?.unsignedLongValue ?: 0uL
                val shouldResume =
                    (options and AVAudioSessionInterruptionOptionShouldResume) != 0uL
                if (pausedByInterruption && shouldResume) play()
                pausedByInterruption = false
            }
            else -> Unit
        }
    }

    /** The one notification that has to name its item: see [endObserver]. */
    private fun observeEndOf(item: AVPlayerItem) {
        clearEndObserver()
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _: NSNotification? -> onItemEnded() }
    }

    private fun clearEndObserver() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
    }

    private fun unobserve() {
        clearEndObserver()
        val centre = NSNotificationCenter.defaultCenter
        observers.forEach { centre.removeObserver(it) }
        observers.clear()
    }

    private fun wireCommands() {
        if (commandsWired) return
        commandsWired = true
        val centre = MPRemoteCommandCenter.sharedCommandCenter()
        centre.playCommand.addTargetWithHandler { play(); MPRemoteCommandHandlerStatusSuccess }
        centre.pauseCommand.addTargetWithHandler { pause(); MPRemoteCommandHandlerStatusSuccess }
        centre.togglePlayPauseCommand.addTargetWithHandler {
            toggle()
            MPRemoteCommandHandlerStatusSuccess
        }
        centre.nextTrackCommand.addTargetWithHandler { next(); MPRemoteCommandHandlerStatusSuccess }
        centre.previousTrackCommand.addTargetWithHandler {
            previous()
            MPRemoteCommandHandlerStatusSuccess
        }
        // The lock screen's bar is on the surah's clock (spec §14.1), so a scrub on it means
        // something: it lands on the start of the ayah under the thumb, never inside one.
        centre.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val seconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
            if (seconds != null) seekToSurahTime((seconds * 1000.0).toLong())
            MPRemoteCommandHandlerStatusSuccess
        }
        // Skipping fifteen seconds of a recitation lands inside a word. Previous and next move by
        // ayah instead, and the bar moves by ayah too.
        listOf(
            centre.seekForwardCommand,
            centre.seekBackwardCommand,
            centre.skipForwardCommand,
            centre.skipBackwardCommand,
        ).forEach { it.enabled = false }
        listOf(
            centre.playCommand,
            centre.pauseCommand,
            centre.togglePlayPauseCommand,
            centre.nextTrackCommand,
            centre.previousTrackCommand,
            centre.changePlaybackPositionCommand,
        ).forEach { it.enabled = true }
    }

    /** A seek on the surah's clock: the start of the ayah that holds that moment. */
    private fun seekToSurahTime(ms: Long) {
        val built = queue ?: return
        val clock = timeline ?: return
        go(clock.snapToAyah(ms, built::isGap))
    }

    private fun unwireCommands() {
        if (!commandsWired) return
        commandsWired = false
        val centre = MPRemoteCommandCenter.sharedCommandCenter()
        listOf(
            centre.playCommand,
            centre.pauseCommand,
            centre.togglePlayPauseCommand,
            centre.nextTrackCommand,
            centre.previousTrackCommand,
            centre.changePlaybackPositionCommand,
        ).forEach {
            it.removeTarget(null)
            it.enabled = false
        }
    }

    private fun nowPlaying() {
        val label = text ?: return
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to label.title,
            MPMediaItemPropertyArtist to label.subtitle,
            // The surah's clock, not the ayah's: a lock-screen bar that ran five seconds and
            // started again was the one thing on that screen that did not look like a player.
            MPMediaItemPropertyPlaybackDuration to surahDurationMs / 1000.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to surahPositionMs / 1000.0,
            MPNowPlayingInfoPropertyPlaybackRate to if (wantsPlay) 1.0 else 0.0,
        )
        artwork()?.let { info[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    private fun artwork(): MPMediaItemArtwork? {
        cachedArtwork?.let { return it }
        val image = UIImage.imageNamed(ARTWORK) ?: return null
        val made = MPMediaItemArtwork(boundsSize = image.size) { _: CValue<CGSize> -> image }
        cachedArtwork = made
        return made
    }

    // ---- state --------------------------------------------------------------------------------

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(POLL_MS)
                if (queue == null || !wantsPlay) break
                publish()
            }
        }
    }

    private fun publish() {
        val built = queue ?: return
        val gap = built.isGap(at)
        if (!gap) {
            val item = player?.currentItem
            if (item != null) {
                val position = CMTimeGetSeconds(player!!.currentTime())
                if (!position.isNaN()) ayahPositionMs = (position * 1000).toLong().coerceAtLeast(0L)
                val duration = CMTimeGetSeconds(item.duration)
                if (!duration.isNaN() && duration > 0) ayahDurationMs = (duration * 1000).toLong()
            }
        } else {
            ayahPositionMs = ayahDurationMs
        }
        val clock = timeline
        if (clock != null) {
            surahPositionMs = clock.elapsed(at, if (gap) gapElapsedNow() else ayahPositionMs)
            surahDurationMs = clock.totalMs
        }
        _state.value = PlaybackState(
            reciterId = reciterId,
            surah = built.surah,
            ayah = built.ayahAt(at),
            ayahCount = built.ayahCount,
            positionMs = ayahPositionMs,
            durationMs = ayahDurationMs,
            playing = wantsPlay,
            buffering = false,
            surahPositionMs = surahPositionMs,
            surahDurationMs = surahDurationMs,
        )
        nowPlaying()
    }

    /** How far into the current gap the wait has got, frozen or running. */
    private fun gapElapsedNow(): Long =
        gapElapsedMs + (gapStarted?.elapsedNow()?.inWholeMilliseconds ?: 0L)

    private companion object {
        const val POLL_MS = 250L

        /** `iosApp/iosApp/Resources/recitation-artwork.png`, the app icon at 512 px (spec §12). */
        const val ARTWORK = "recitation-artwork"
    }
}
