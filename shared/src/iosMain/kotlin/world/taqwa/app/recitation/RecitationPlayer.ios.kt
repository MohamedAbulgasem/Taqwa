package world.taqwa.app.recitation

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
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
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
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
import world.taqwa.app.audio.IosAudioSession
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
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIImage
import platform.darwin.NSObject
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
 * **Read-aloud (read-aloud spec §5.6).** A translation is a timed phase like a gap, not an item:
 * the `AVPlayer` rests on the end of the ayah it follows while an `AVSpeechSynthesizer` reads the
 * text through this app's own audio session — playback, spoken audio — so it goes on reading with
 * the screen locked. Every move stops the voice, and the synthesizer reports a stopped utterance
 * as *finished*, exactly as it does one that read itself out (measured on the iOS 26.2 simulator;
 * the delegate's cancel callback, which a stop suggests, never came). So what advances the queue
 * is a finish for the translation still current: each stop lets go of it first, a late finish
 * matches nothing, and a move can never advance the queue twice.
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

    /** The lock screen's track buttons, which move by surah (spec §15.1); the app decides. */
    private val _skips = MutableSharedFlow<SurahSkip>(extraBufferCapacity = 4)
    actual val skips: SharedFlow<SurahSkip> = _skips.asSharedFlow()

    /** The surah read itself out (spec §16.1); the controller answers with `load` or `stop`. */
    private val _surahEnds = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    actual val surahEnds: SharedFlow<Int> = _surahEnds.asSharedFlow()

    /** True from the last ayah's end until a `load` takes the session on or the hold lapses. */
    private var ended = false
    private var holdJob: Job? = null

    /**
     * Keeps the process running across the breath and the next container's split when a surah
     * ends in the background: an app that has stopped playing audio is suspended soon after, and
     * a suspended app cannot start the next surah. Begun at the end, ended when the next surah is
     * playing or the recitation stops.
     */
    private var transition: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

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

    /**
     * Held across the gap and the translation for the same reason as on Android: the ayah's line
     * must not rewind.
     */
    private var ayahPositionMs = 0L
    private var ayahDurationMs = 0L

    /**
     * How far into the gap the wait has got. The gap is a `delay`, not an item with a clock, so
     * the surah's clock keeps moving through it by reading a monotonic mark taken when the wait
     * began; a pause inside the gap freezes what had elapsed and a play starts the wait again.
     */
    private var gapStarted: TimeSource.Monotonic.ValueTimeMark? = null
    private var gapElapsedMs = 0L

    /** Read-aloud (spec §5.6): what this surah reads, and what a rebuild needs to redo the queue. */
    private var speech: SpokenTranslation? = null
    private var gapMs = 0L
    private var ayahDurations: Map<Int, Long> = emptyMap()

    /**
     * The translation being read, from [speak] until it finishes or is stopped. Cleared *before*
     * any stop, so a finish the synthesizer reports for a translation already left behind finds
     * nothing to match and is ignored.
     */
    private var utterance: AVSpeechUtterance? = null

    /**
     * How far into the translation the voice has got, on the surah's clock: a monotonic mark, as
     * for the gap, since the synthesizer has no position to ask. A pause freezes it and a play
     * starts it again from there.
     */
    private var speechStarted: TimeSource.Monotonic.ValueTimeMark? = null
    private var speechElapsedMs = 0L

    /**
     * The synthesizer's delegate. Its `delegate` property is weak, so this is what keeps the
     * object alive. Apple promises no thread for the callback, and [utterance] and the queue are
     * main-thread state, so the finish is matched on the main thread — at once when the callback
     * is already there, which leaves no gap for a play to start the translation again between
     * its finish and the move. `==` is `isEqual:`, which for an utterance is the object itself,
     * whatever Kotlin wrapper the callback hands over.
     */
    private val speechEnd = SpeechEnd { finished ->
        scope.launch(Dispatchers.Main.immediate) { if (finished == utterance) onSpeechEnded() }
    }

    /** Made on the first translation read, not with the player: constructing this class is free. */
    private val synthesizer: AVSpeechSynthesizer by lazy {
        AVSpeechSynthesizer().also { it.delegate = speechEnd }
    }

    /** The last surah-clock pair published, for the lock screen's bar. */
    private var surahPositionMs = 0L
    private var surahDurationMs = 0L

    /** Set when an interruption paused us, so playback only resumes if it was our pause. */
    private var pausedByInterruption = false

    /** The app icon, made into artwork once: `UIImage` decoding is not free and it never changes. */
    private var cachedArtwork: MPMediaItemArtwork? = null

    actual suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText, speech: SpokenTranslation?) {
        // First of all, before the split: a load is the answer the hold was waiting for, and the
        // hold must not lapse into a stop while Al-Baqarah is still being written out.
        leaveHold()
        val file = library.fileFor(reciter.id, surah)
        val split = withContext(Dispatchers.Default) {
            runCatching { split(reciter.id, surah, file) }.getOrNull()
        } ?: return
        if (split.files.isEmpty()) return
        val built = RecitationQueue(
            surah = surah,
            ayahs = split.files.keys.sorted(),
            gapMs = reciter.gapMs.toLong(),
            spoken = speech?.spoken.orEmpty(),
        )
        val clock = SurahTimeline.of(built, { split.durationsMs[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }
        withContext(Dispatchers.Main) {
            queue = built
            timeline = clock
            this@RecitationPlayer.speech = speech
            gapMs = reciter.gapMs.toLong()
            ayahDurations = split.durationsMs
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
            endTransition()
        }
    }

    actual fun setSpeech(speech: SpokenTranslation?) {
        val old = queue ?: return
        this.speech = speech
        val built = RecitationQueue(old.surah, old.ayahs, gapMs, speech?.spoken.orEmpty())
        val clock = SurahTimeline.of(built, { ayahDurations[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }
        val ayah = old.ayahAt(at)
        val inAyah = old.isAyah(at)
        queue = built
        timeline = clock
        if (inAyah) {
            // The ayah's own item carries on playing; only its place in the queue has moved.
            at = built.indexOfAyah(ayah) ?: 0
            publish()
            return
        }
        // In a silence or a translation: on to the next ayah (read-aloud spec §2).
        stopSpeaking()
        gapJob?.cancel()
        gapJob = null
        val own = built.indexOfAyah(ayah) ?: 0
        val next = built.next(own)
        if (next != null) {
            go(next)
            return
        }
        // The last ayah's translation, or the hold after it: nothing follows, so the surah rests
        // on its last ayah, ended. `at` is moved first, because the index it held belonged to the
        // old queue, and a hold that has already begun is only shown again.
        at = own
        if (ended) publish() else finish()
    }

    actual fun play() {
        val built = queue ?: return
        wantsPlay = true
        pausedByInterruption = false
        activateSession()
        when {
            // A gap is playing silence: there is no item to start, the wait simply resumes — from
            // where the pause froze it, so the surah's clock does not rewind by the part already
            // waited.
            built.isGap(at) -> waitOutGap(built, built.silenceMs(at) - gapElapsedMs)
            built.isSpeech(at) -> {
                val n = (built.items[at] as QueueItem.Speech).n
                when {
                    // Not begun: the pause came in the breath before it. From the top.
                    utterance == null -> speak(n)
                    // Paused by [pause]: on from the word it stopped at. This player's own mark
                    // says so, not the synthesizer's `paused`, which it sets only a moment after
                    // the pause was asked for: a quick pause and play would find it still false
                    // and queue the text a second time behind the paused one. Should there be
                    // nothing to continue and nothing reading — the system let it go without a
                    // word — it is read again from the top rather than waited on for ever.
                    speechStarted == null -> {
                        speechStarted = TimeSource.Monotonic.markNow()
                        if (!synthesizer.continueSpeaking() && !synthesizer.speaking) {
                            speechElapsedMs = 0L
                            speak(n)
                        }
                    }
                    // Already reading — a second play from a headset or the lock screen. Speaking
                    // it again would queue the text behind itself and read the translation twice.
                    else -> Unit
                }
            }
            else -> player?.play()
        }
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
        // A translation pauses mid-word and its clock with it; `play` continues both.
        if (queue?.isSpeech(at) == true && utterance != null) {
            speechElapsedMs = speechElapsedNow()
            speechStarted = null
            synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
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
        go(built.previous(at, if (!built.isAyah(at)) 0L else ayahPositionMs))
    }

    actual fun stop() {
        leaveHold()
        gapJob?.cancel()
        gapJob = null
        stopSpeaking()
        speech = null
        speechElapsedMs = 0L
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
        // Before the deactivation, so a clip that ends in the same breath does not read a session
        // recitation still holds and decline to hand it back.
        IosAudioSession.recitationHoldsSession = false
        // Notify others: whatever was playing before recitation took the session — a podcast, the
        // Quran in another app — is told it may have it back.
        AVAudioSession.sharedInstance().setActive(
            false,
            withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
            error = null,
        )
        _state.value = PlaybackState.EMPTY
        endTransition()
    }

    actual fun release() {
        stop()
        player = null
        scope.cancel()
    }

    // ---- the queue --------------------------------------------------------------------------

    /**
     * Starts queue item [index]: an ayah's file, the wait that is a gap or a breath, or the voice
     * reading a translation. Whatever was playing stops first, the voice included.
     */
    private fun go(index: Int) {
        val built = queue ?: return
        // A move during the hold (a scrub on the lock screen, a tap on an ayah) takes the ended
        // surah up again from there, playing — as an ExoPlayer seek out of its ended state does.
        if (ended) {
            leaveHold()
            wantsPlay = true
        }
        gapJob?.cancel()
        gapJob = null
        stopSpeaking()
        at = index.coerceIn(0, built.size - 1)
        if (built.isGap(at)) {
            player?.pause()
            ayahPositionMs = ayahDurationMs
            gapElapsedMs = 0L
            publish()
            if (wantsPlay) waitOutGap(built, built.silenceMs(at))
            return
        }
        if (built.isSpeech(at)) {
            // The ayah it follows has played out; the player rests on its end, as in a gap.
            player?.pause()
            ayahPositionMs = ayahDurationMs
            gapStarted = null
            gapElapsedMs = 0L
            speechElapsedMs = 0L
            speechStarted = null
            publish()
            if (wantsPlay) speak((built.items[at] as QueueItem.Speech).n)
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

    /**
     * The rest of the silence at [at]: [remainingMs] of it, then the item after it — the next
     * ayah after a reciter's gap, the translation after a breath. Not [RecitationQueue.next],
     * which skips to the next ayah and would skip the translation with it.
     */
    private fun waitOutGap(built: RecitationQueue, remainingMs: Long) {
        gapJob?.cancel()
        gapStarted = TimeSource.Monotonic.markNow()
        gapJob = scope.launch {
            delay(remainingMs.coerceAtLeast(0L))
            val following = at + 1
            if (following < built.size) go(following) else finish()
        }
    }

    /** The ayah just played to its end. Move on, or stop at the last ayah of the surah. */
    private fun onItemEnded() {
        val built = queue ?: return
        // Only an ayah has an item that ends; a silence or a translation still resting on the
        // ayah's ended item must not be taken for it.
        if (!built.isAyah(at)) return
        val nextIndex = at + 1
        if (nextIndex >= built.size) {
            finish()
            return
        }
        go(nextIndex)
    }

    /**
     * The surah has read itself out (spec §16.1). Nothing goes yet: the bar shows the last ayah
     * paused with its line full, the lock screen keeps its entry, and — the part that matters in
     * the background — the audio session stays active, so the next surah the controller answers
     * with starts in the same session instead of asking for a new one from an app iOS may already
     * be suspending. Should the answer be a stop, or never come, [stop] hands the session back
     * exactly as it always did, at most [SURAH_END_HOLD_MS] later.
     */
    private fun finish() {
        val built = queue ?: return
        if (ended) return
        ended = true
        wantsPlay = false
        ticker?.cancel()
        ticker = null
        ayahPositionMs = ayahDurationMs
        publish()
        beginTransition()
        _surahEnds.tryEmit(built.surah)
        holdJob = scope.launch {
            delay(SURAH_END_HOLD_MS)
            if (ended) stop()
        }
    }

    private fun leaveHold() {
        ended = false
        holdJob?.cancel()
        holdJob = null
    }

    private fun beginTransition() {
        if (transition != UIBackgroundTaskInvalid) return
        transition = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
            endTransition()
        }
    }

    private fun endTransition() {
        val task = transition
        if (task == UIBackgroundTaskInvalid) return
        transition = UIBackgroundTaskInvalid
        UIApplication.sharedApplication.endBackgroundTask(task)
    }

    // ---- the voice --------------------------------------------------------------------------

    /**
     * Reads the translation after ayah [n] (read-aloud spec §5.6), at Apple's default rate. A
     * voice that has gone from the phone since it was chosen falls back to any voice for the
     * language; with none at all the translation is skipped, and the recitation goes on (§7).
     */
    private fun speak(n: Int) {
        val spoken = speech
        val text = spoken?.texts?.get(n)
        val voice = spoken?.voice?.id?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
            ?: spoken?.language?.let { AVSpeechSynthesisVoice.voiceWithLanguage(it) }
        if (text == null || voice == null) {
            onSpeechEnded()
            return
        }
        val next = AVSpeechUtterance(string = text)
        next.voice = voice
        next.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance = next
        speechStarted = TimeSource.Monotonic.markNow()
        synthesizer.speakUtterance(next)
        startTicker()
    }

    /** The translation read itself out: on to whatever follows it. */
    private fun onSpeechEnded() {
        utterance = null
        speechStarted = null
        val built = queue ?: return
        val following = at + 1
        if (following < built.size) {
            go(following)
            return
        }
        // The surah's last translation: its slot is full, as a last ayah's line is when it ends.
        speechElapsedMs = timeline?.durationOf(at) ?: 0L
        finish()
    }

    /**
     * Silences the voice without advancing. The synthesizer reports the stopped translation as
     * finished, so [utterance] is let go of before the stop and that finish matches nothing.
     */
    private fun stopSpeaking() {
        if (utterance == null) return
        utterance = null
        speechStarted = null
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    /** How far into the current translation the voice has got, frozen or running. */
    private fun speechElapsedNow(): Long =
        speechElapsedMs + (speechStarted?.elapsedNow()?.inWholeMilliseconds ?: 0L)

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
        evictSplitsExcept(dir, fs)
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

    /**
     * A split is the surah's own bytes a second time — 58 MB for a long one — and iOS only purges
     * `Library/Caches` under real disk pressure, never while the app is running. So the cache
     * holds exactly the surah about to be played: every other surah of this reciter and every
     * other reciter goes first. Best effort, and never [keep] itself: a cache that could not be
     * pruned is not a reason to refuse to play.
     */
    private fun evictSplitsExcept(keep: Path, fs: FileSystem) {
        val reciterDir = keep.parent ?: return
        runCatching {
            fs.listOrNull(cachesRoot())?.forEach { entry ->
                if (entry != reciterDir) {
                    runCatching { fs.deleteRecursively(entry) }
                } else {
                    fs.listOrNull(entry)?.forEach { surahDir ->
                        if (surahDir != keep) runCatching { fs.deleteRecursively(surahDir) }
                    }
                }
            }
        }
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
        // The adhan audition and the reciter clip share this session; from here until `stop()`
        // they leave it alone rather than deactivating a surah out from under itself.
        IosAudioSession.recitationHoldsSession = true
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
        // Previous and next are surah moves (spec §15.1), like a track skip: reported to the app,
        // which knows whether the neighbouring surah is on the phone.
        centre.nextTrackCommand.addTargetWithHandler {
            _skips.tryEmit(SurahSkip.NEXT)
            MPRemoteCommandHandlerStatusSuccess
        }
        centre.previousTrackCommand.addTargetWithHandler {
            _skips.tryEmit(SurahSkip.PREVIOUS)
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
    actual fun seekToSurahTime(positionMs: Long) {
        val built = queue ?: return
        val clock = timeline ?: return
        go(clock.snapToAyah(positionMs) { !built.isAyah(it) })
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
        val speaking = built.isSpeech(at)
        // Only an ayah is in the player; in a silence or a translation it rests on the ayah's end.
        if (!gap && !speaking) {
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
            // The ayah's slot is an estimate and the item's length is measured: scale into the
            // slot rather than clamp, so the clock neither stalls nor jumps at the seam. A
            // translation has no measured length to scale by: its clock runs from its mark and
            // waits at the end of its slot should the voice take longer than the estimate.
            val within = when {
                gap -> gapElapsedNow()
                speaking -> speechElapsedNow().coerceAtMost(clock.durationOf(at))
                else -> SurahTimeline.fitToSlot(ayahPositionMs, ayahDurationMs, clock.durationOf(at))
            }
            surahPositionMs = clock.elapsed(at, within)
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
            speaking = speaking,
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

/**
 * Tells the player the synthesizer has finished an utterance — one that read itself out, or one
 * the player stopped, which is reported the same way; the player tells the two apart by whether
 * the utterance is still its current one. Every method of the delegate protocol has the same
 * Kotlin signature, so the one overridden here is picked by its Objective-C selector.
 */
private class SpeechEnd(
    private val onFinish: (AVSpeechUtterance) -> Unit,
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {

    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
        onFinish(didFinishSpeechUtterance)
    }
}
