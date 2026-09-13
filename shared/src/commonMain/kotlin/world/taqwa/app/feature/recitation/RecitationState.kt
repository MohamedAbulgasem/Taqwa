package world.taqwa.app.feature.recitation

import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.DownloadState
import world.taqwa.app.recitation.PlaybackState
import world.taqwa.app.recitation.Reciter

/**
 * What the reader header's recitation button is showing (spec §5.1, §12.7). Four states and no
 * more: the button is one 36 dp disc and every extra state would be a shade of grey nobody can
 * name.
 */
sealed interface HeaderState {
    /** A speaker in the secondary tint, like its two neighbours. */
    data object Idle : HeaderState

    /** The same speaker with a progress ring around the disc; a tap re-opens the sheet. */
    data class Downloading(val fraction: Float) : HeaderState

    /** The accent, and the equaliser instead of the speaker — the one animated thing on screen. */
    data object Playing : HeaderState

    /** An accent-tinted speaker: loaded, stopped, a tap away from going on. */
    data object Paused : HeaderState
}

/**
 * The player bar (spec §5.3). Everything on it, already resolved: the composable looks up no
 * manifest and asks no library.
 *
 * [fraction] is position within the *surah*, which has no single timeline — it is the ayah's
 * ordinal plus how far through that ayah the voice is, over the ayah count. See [barFraction] for
 * why it is held rather than recomputed when the platform reports no duration.
 */
data class BarState(
    val reciter: Reciter,
    val surah: Int,
    val ayah: Int,
    val ayahCount: Int,
    val fraction: Float,
    val playing: Boolean,
    val buffering: Boolean,
)

/** Which of the download sheet's three faces is showing (spec §5.4). */
sealed interface SheetPhase {
    /** The primary button, priced. [needsWifiNote] carries the "Over Wi-Fi." line and its override. */
    data class Ready(val needsWifiNote: Boolean) : SheetPhase

    /**
     * The button has become the progress bar.
     *
     * [queued] is the difference between a download that is *moving* and one the scheduler has
     * accepted and not started — WorkManager holding it on a network constraint, most often
     * because there is no network. Both draw the same bar at the same place; what changes is the
     * sentence under it, and only after [WAITING_MS], because a second or two of "Queued" is what
     * every download begins with and a reader told to wait for a connection they already have
     * would be worse than a bar that sits still for a moment. See the sheet's `Running`.
     */
    data class Downloading(val bytes: Long, val total: Long, val queued: Boolean = false) : SheetPhase

    /** A plain sentence and a Retry. */
    data class Failed(val reason: DownloadFailure) : SheetPhase
}

data class DownloadSheetState(
    val surah: Int,
    val reciter: Reciter,
    /** What the manifest says this surah costs, so the button is priced before the first byte. */
    val bytes: Long,
    val phase: SheetPhase,
)

/**
 * Everything the recitation surface draws itself from, in one value. Assembled by
 * [RecitationController] from the manifest, the library, the downloader and the player, so that
 * no composable in the Quran screens ever touches any of the four.
 */
data class RecitationState(
    /** The voice the reader has chosen, from the catalogue in force. Null until it has loaded. */
    val reciter: Reciter? = null,
    val reciters: List<Reciter> = emptyList(),
    /** The current reciter's surahs on this phone. */
    val downloaded: Set<Int> = emptySet(),
    /**
     * Every reciter's surahs on this phone. The picker's caption wants only the size of each set
     * ([downloadedCounts]); Settings › Recitation wants which reciters have anything at all, and
     * its Downloads screen wants the numbers themselves.
     */
    val downloadedByReciter: Map<String, Set<Int>> = emptyMap(),
    val downloads: Map<DownloadKey, DownloadState> = emptyMap(),
    val bar: BarState? = null,
    val sheet: DownloadSheetState? = null,
    val pickerOpen: Boolean = false,
    /** The reciter whose fifteen-second preview is playing, if any. */
    val previewing: String? = null,
    /** The reciters this build actually bundles a preview clip for; the rest show no triangle. */
    val previewable: Set<String> = emptySet(),
    /** Settings › Recitation's toggle (spec §5.6), and the sheet's "Over Wi-Fi." note. */
    val downloadOnMobileData: Boolean = false,
    /** The whole-Quran offer for the current reciter, or null when there is nothing to offer. */
    val wholeQuran: WholeQuran? = null,
) {
    /** The header button's state for [surah], under the current reciter. */
    fun header(surah: Int): HeaderState = headerStateOf(surah, reciter?.id, downloads, bar)

    /** Every reciter's count, for the picker's caption. */
    val downloadedCounts: Map<String, Int> get() = downloadedByReciter.mapValues { it.value.size }

    /** The reciters with something on the phone, in the catalogue's own order. */
    val reciterDownloads: List<Reciter>
        get() = reciters.filter { downloadedByReciter[it.id].orEmpty().isNotEmpty() }
}

/**
 * What recitation occupies on the phone, counted off the disk rather than off the registry
 * (spec §8): the Downloads screen states a number the reader could check in system settings, and
 * a `.part` left by a cancelled download takes up room whether the registry knows it or not.
 *
 * [byReciter] holds only reciters with something to show; [total] is the whole audio root, so it
 * also carries the bytes of a reciter withdrawn from the catalogue since the files were fetched.
 */
data class RecitationStorage(
    val total: Long = 0L,
    val byReciter: Map<String, Long> = emptyMap(),
) {
    fun of(reciterId: String): Long = byReciter[reciterId] ?: 0L
}

/**
 * The header button's state (spec §5.1), as a function of what is playing and what is arriving.
 *
 * **Playing beats downloading**, which is not merely an ordering: a reader can start Al-Baqarah
 * for one reciter and, on the same screen, be waiting for another's copy of it. What the button
 * offers has to be what a tap will do, and a tap while a voice is coming out of the phone must be
 * pause.
 *
 * A download reports itself through [DownloadState] whether or not the sheet is open, so the ring
 * appears on the header of a reader who dismissed the sheet and went on reading — which is the
 * only way spec §5.4's "the sheet may be dismissed" is honest.
 */
fun headerStateOf(
    surah: Int,
    reciterId: String?,
    downloads: Map<DownloadKey, DownloadState>,
    bar: BarState?,
): HeaderState {
    if (bar != null && bar.surah == surah) return if (bar.playing) HeaderState.Playing else HeaderState.Paused
    val id = reciterId ?: return HeaderState.Idle
    return when (val download = downloads[DownloadKey(id, surah)]) {
        null, is DownloadState.Failed -> HeaderState.Idle
        DownloadState.Queued -> HeaderState.Downloading(0f)
        DownloadState.Verifying -> HeaderState.Downloading(1f)
        is DownloadState.Downloading -> HeaderState.Downloading(download.fraction)
    }
}

/**
 * How far through the surah the voice is, in the only units a surah has: ayahs.
 *
 * A surah is a queue of per-ayah files, so there is no single timeline to read a fraction off.
 * The ayah's own ordinal carries the bar most of the way and the position inside the current ayah
 * fills in between the notches, which is what stops the line standing still through a
 * two-minute ayah of Al-Baqarah.
 *
 * **[previous] is returned whole when the platform reports no duration.** That happens between
 * every two items — an item that has been selected but not prepared has `durationMs == 0` — and a
 * bar that fell back to the ayah ordinal alone at those moments would twitch backwards several
 * times a minute.
 */
fun barFraction(playback: PlaybackState, previous: Float): Float {
    val ayah = playback.ayah ?: return 0f
    val count = playback.ayahCount
    if (count <= 0) return previous
    if (playback.durationMs <= 0L) return previous
    val within = (playback.positionMs.toDouble() / playback.durationMs).coerceIn(0.0, 1.0)
    return (((ayah - 1) + within) / count).toFloat().coerceIn(0f, 1f)
}

/**
 * The sheet's face for one surah (spec §5.4), from the download machinery's own state. A surah
 * that has committed has no state at all — it is in the library by then — which is why the caller
 * closes the sheet on the library's word rather than on the absence of a [DownloadState].
 */
/**
 * How long a [DownloadState.Queued] with nothing behind it may keep saying "playback starts as
 * soon as it lands" before the sheet admits what it is actually doing (spec §5.4). Long enough
 * that an ordinary download — accepted, a slot found, the first bytes in — never shows it.
 */
const val WAITING_MS = 20_000L

fun sheetPhaseOf(download: DownloadState?, downloadOnMobileData: Boolean, total: Long): SheetPhase = when (download) {
    null -> SheetPhase.Ready(needsWifiNote = !downloadOnMobileData)
    DownloadState.Queued -> SheetPhase.Downloading(0L, total, queued = true)
    DownloadState.Verifying -> SheetPhase.Downloading(total, total)
    is DownloadState.Downloading -> SheetPhase.Downloading(download.bytes, download.total)
    is DownloadState.Failed -> SheetPhase.Failed(download.reason)
}
