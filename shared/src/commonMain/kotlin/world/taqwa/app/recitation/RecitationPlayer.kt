package world.taqwa.app.recitation

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** One ayah, addressed the way the reader addresses it. */
data class AyahRef(val surah: Int, val ayah: Int)

/**
 * Everything a screen needs to draw the player bar and light the current ayah, and nothing a
 * screen would have to ask a platform for. [ayah] is the ayah being *heard*: while the reciter's
 * inter-ayah gap plays it stays on the ayah that just ended rather than blanking (see
 * [RecitationQueue]).
 *
 * [positionMs] and [durationMs] are within the current ayah. [surahPositionMs] and
 * [surahDurationMs] are the same moment on the surah's own clock (spec §14.1, [SurahTimeline]):
 * gaps included, estimated from the container, and zero when the player has no timeline yet.
 * The bar and the lock screen draw the surah pair; the ayah pair stays for the previous-button
 * rule and for a player that has not built its timeline.
 */
data class PlaybackState(
    val reciterId: String? = null,
    val surah: Int? = null,
    val ayah: Int? = null,
    val ayahCount: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val surahPositionMs: Long = 0L,
    val surahDurationMs: Long = 0L,
) {
    /** The ayah playing, if one is. */
    val current: AyahRef? get() = if (surah != null && ayah != null) AyahRef(surah, ayah) else null

    companion object {
        /** Nothing loaded. What [RecitationPlayer.stop] returns the player to. */
        val EMPTY = PlaybackState()
    }
}

/**
 * The two lines the lock screen and the media notification show. Already localised: the caller
 * resolves the surah name and the reciter's name in the current language and hands them over, so
 * nothing below this line touches a string resource and nothing has to be re-resolved when the
 * app's language changes while a service is running.
 */
data class NowPlayingText(
    val title: String,
    val subtitle: String,
    /** The two extra notification buttons on Android (spec §15.1), localised by the caller. */
    val previousAyahLabel: String = "",
    val nextAyahLabel: String = "",
)

/**
 * The lock screen's previous and next move by *surah* (spec §15.1). The player cannot load a
 * surah by itself — that is the controller's decision, download and all — so it reports the
 * press and the controller acts on it.
 */
enum class SurahSkip { PREVIOUS, NEXT }

/**
 * How long a platform player holds an ended surah for the controller's decision (spec §16.1):
 * long enough for the breath before the next surah and its load, short enough that a decision
 * that never comes costs a few seconds of a paused notification and nothing more.
 */
const val SURAH_END_HOLD_MS = 5_000L

/**
 * Recitation playback, background and lock screen included (spec §6).
 *
 * Android puts an ExoPlayer inside a `MediaSessionService` and talks to it through a
 * `MediaController`; iOS drives an `AVPlayer` with `MPNowPlayingInfoCenter` and
 * `MPRemoteCommandCenter`. Neither is visible from here, and neither claims an audio session or
 * starts a service until [load] is called — constructing this class is free, which is what lets
 * `AppContainer` hold one for the life of the process.
 *
 * All of the commands are safe to call before [load] and after [stop]; they do nothing.
 */
expect class RecitationPlayer(library: RecitationLibrary) {

    val state: StateFlow<PlaybackState>

    /** Previous/next pressed on a lock screen, a headset or a car (spec §15.1). */
    val skips: SharedFlow<SurahSkip>

    /**
     * The surah has played out (spec §16.1). The player does not tear itself down at that point:
     * it holds the ended surah — the bar paused at its end, the session and its notification up —
     * for [SURAH_END_HOLD_MS] and reports the surah here, so the controller can [load] the next
     * one into the same session or [stop]. Should neither arrive, the hold lapses into [stop] by
     * itself, and a surah that ended never leaves a session standing for a player with nothing
     * left to play.
     */
    val surahEnds: SharedFlow<Int>

    /**
     * Builds the queue for [surah] from the reciter's downloaded `.taqa` — one item per ayah,
     * [Reciter.gapMs] of silence between them — and starts playing at [startAyah].
     *
     * Does nothing if the surah is not on disk or its container cannot be read; the caller offers
     * the download, and a player that threw would make every call site handle a case the UI has
     * already handled.
     */
    suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText)

    fun play()

    fun pause()

    fun toggle()

    /** Jumps to the start of ayah [n]; ignored if this surah has no such ayah. */
    fun seekToAyah(n: Int)

    /** The next ayah. At the last ayah, nothing. */
    fun next()

    /**
     * The previous ayah within the first two seconds of an ayah, otherwise the start of the ayah
     * playing — the behaviour every media player has, and the one the lock-screen button needs.
     */
    fun previous()

    /** Stops, clears the queue and tears the session and its notification down. */
    fun stop()

    /** Releases the platform player for good. The app calls this only when the process is going. */
    fun release()
}
