package world.taqwa.app.recitation

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

    data class Failed(val reason: DownloadFailure) : DownloadState
}

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
