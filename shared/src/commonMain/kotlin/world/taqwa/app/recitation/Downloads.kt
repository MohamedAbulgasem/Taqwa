package world.taqwa.app.recitation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * One surah of one reciter, as every part of the download machinery names it (spec 3a §7). A
 * value class would be one field short; the pair is what a work name, a session task description
 * and a key in [SurahDownloader.states] are all built from.
 */
data class DownloadKey(val reciterId: String, val surah: Int) {

    /** The unique work name on Android and the task description on iOS — one shape, both sides. */
    val wire: String get() = "$reciterId/$surah"

    companion object {
        /** The inverse of [wire]; null for anything this scheme did not write. */
        fun parse(wire: String): DownloadKey? {
            val cut = wire.lastIndexOf('/')
            if (cut <= 0) return null
            val surah = wire.substring(cut + 1).toIntOrNull() ?: return null
            return DownloadKey(wire.substring(0, cut), surah)
        }
    }
}

/**
 * Where a download has got to. A surah that has **finished** has no state at all: it leaves
 * [SurahDownloader.states] the moment it commits, because from then on the library reports it and
 * two sources of truth for "the reader owns this" is one too many.
 */
sealed interface DownloadState {

    /** Accepted, waiting for a network the policy allows or for a slot. */
    data object Queued : DownloadState

    /** [total] is the size the manifest publishes, so the bar is honest before the first byte. */
    data class Downloading(val bytes: Long, val total: Long) : DownloadState {
        val fraction: Float
            get() = if (total <= 0L) 0f else (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

    /** All bytes in, hashing against the manifest. Al-Baqarah takes a moment. */
    data object Verifying : DownloadState

    /**
     * [attempt] tells two failures apart: a refusal the downloader reports itself is numbered
     * afresh ([freshFailure]), so a new request refused the very same way is still a change that
     * [SurahDownloader.states] shows and the controller surfaces (spec §15.3). Without it a surah
     * refused for room and asked for again was refused by an equal state, which a StateFlow does
     * not emit, and the tap did nothing. A failure derived again from the same record each time
     * (Android's finished work) keeps 0: a new attempt there passes through Queued on its way.
     */
    data class Failed(val reason: DownloadFailure, val attempt: Long = 0L) : DownloadState
}

private val failureSerial = MutableStateFlow(0L)

/** A failure for [reason] numbered afresh ([DownloadState.Failed.attempt]); safe from any thread. */
fun freshFailure(reason: DownloadFailure): DownloadState.Failed =
    DownloadState.Failed(reason, failureSerial.updateAndGet { it + 1 })

/**
 * True while a download is on its way: accepted, transferring, or landed and being verified. A
 * request for a surah in this state is already answered, as Android's unique work with `KEEP`
 * answers it. On iOS a second task would split the bandwidth, report its own progress over the
 * first one's (the bar jumping between them on a slow connection) and append its bytes to the
 * same `.part`. A failed download is not on its way: asking again starts it again.
 */
val DownloadState?.isOnItsWay: Boolean
    get() = this is DownloadState.Queued || this is DownloadState.Downloading || this is DownloadState.Verifying

/**
 * Why a download stopped. Every one of these becomes a plain sentence in the download sheet
 * (spec §5.4), so they are reasons a reader could act on rather than error codes: three of them
 * are answered by the user (turn on Wi-Fi, allow mobile data, free some space), two are answered
 * by Retry, and one is answered by nothing at all.
 */
enum class DownloadFailure {
    /** Nothing to download over. */
    NO_NETWORK,

    /** Mobile data, and neither the setting nor this download's own override allows it. */
    NEEDS_WIFI,

    /** Below the 200 MB headroom spec §8 keeps free, plus what this surah would take. */
    NOT_ENOUGH_SPACE,

    /** The server refused, vanished, or sent fewer bytes than it promised. Retry is honest. */
    SERVER,

    /** The bytes arrived and are not the bytes the manifest published. The part is gone. */
    CHECKSUM,

    /** Stopped by something other than the reader — the system reclaiming a background session. */
    CANCELLED,
}
