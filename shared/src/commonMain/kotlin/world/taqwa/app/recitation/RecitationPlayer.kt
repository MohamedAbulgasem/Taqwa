package world.taqwa.app.recitation

import kotlinx.coroutines.flow.StateFlow

/** One ayah, addressed the way the reader addresses it. */
data class AyahRef(val surah: Int, val ayah: Int)

/**
 * Everything a screen needs to draw the player bar and light the current ayah, and nothing a
 * screen would have to ask a platform for. [ayah] is the ayah being *heard*: while the reciter's
 * inter-ayah gap plays it stays on the ayah that just ended rather than blanking (see
 * [RecitationQueue]).
 *
 * [positionMs] and [durationMs] are within the current ayah, not the surah — the surah has no
 * single timeline, it is a queue of per-ayah files, and a progress line across a whole surah
 * would have to be the ayah count anyway.
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
data class NowPlayingText(val title: String, val subtitle: String)

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
