package world.taqwa.app.recitation

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Source
import okio.buffer
import okio.use
import kotlin.time.Clock

/** What a [ByteSource] answered when asked for a surah. */
sealed interface ByteResponse {

    /**
     * @param status the HTTP status. **206** means the `Range` was honoured and the body starts
     * where we asked; anything else 2xx means it was ignored and the body starts at byte zero.
     * @param bytes the length of *this* body, or -1 where the server did not say.
     * @param body closed by the loop, never by the caller.
     */
    data class Body(val status: Int, val bytes: Long, val body: Source) : ByteResponse

    data class Failure(val reason: DownloadFailure) : ByteResponse
}

/**
 * The one thing the download loop needs a platform for: bytes over HTTP, from an offset.
 *
 * Deliberately this small. It is what lets the whole resume-and-verify loop — the part of
 * downloading that has the interesting mistakes in it — be a pure class the JVM tests drive.
 */
fun interface ByteSource {
    /** A `Range: bytes=<fromByte>-` request when [fromByte] is above zero, a plain GET otherwise. */
    suspend fun open(url: String, fromByte: Long): ByteResponse
}

/** What kind of network there is, as the Wi-Fi-only policy of spec §12.6 sees it. */
enum class NetworkKind { NONE, METERED, UNMETERED }

/** The state of the device the policy is decided against. */
interface DownloadConditions {
    suspend fun network(): NetworkKind

    /** Free space on the volume the audio is written to. */
    suspend fun freeBytes(): Long
}

/**
 * The network's answer to "may this download start", as one rule both platforms and both moments
 * ask — the tap, and the transfer.
 *
 * The two refusals are not the same shape and that is the whole point of writing them together.
 * [DownloadFailure.NEEDS_WIFI] is a policy the reader can suspend from the sheet; **no network at
 * all is not a policy**, and until this was pulled out it was not asked at the tap either: the
 * metered check alone falls straight through a device in flight mode (nothing is metered when
 * nothing is connected), the work is enqueued, and WorkManager parks it on its `UNMETERED`
 * constraint. The reader is then shown a progress bar at zero and a ring on the header — a
 * download that has not started and will not, presented as one in progress.
 *
 * @param allowMetered the setting or this download's own one-time override, already resolved.
 * @return the reason to refuse, or null to go ahead.
 */
suspend fun DownloadConditions.refusal(allowMetered: Boolean): DownloadFailure? = when (network()) {
    NetworkKind.NONE -> DownloadFailure.NO_NETWORK
    NetworkKind.METERED -> if (allowMetered) null else DownloadFailure.NEEDS_WIFI
    NetworkKind.UNMETERED -> null
}

sealed interface DownloadOutcome {
    /** Verified, renamed, in the library. */
    data object Done : DownloadOutcome

    data class Failed(val reason: DownloadFailure) : DownloadOutcome
}

/**
 * A surah download, everything but the scheduling (spec 3a §7).
 *
 * Both platforms share [precheck] and [finish]; Android's worker also runs [run], which is the
 * whole thing — resume from the `.part`, stream, verify, commit. iOS cannot: a background
 * `NSURLSession` owns its own transfer and hands back a finished file, so it brackets that
 * transfer with the same two ends instead.
 *
 * Nothing here knows what a notification is, and nothing here throws for a reason a reader could
 * fix; the reasons come back as [DownloadFailure] so both platforms phrase them identically.
 */
class DownloadLoop(
    private val library: RecitationLibrary,
    private val source: ByteSource,
    private val conditions: DownloadConditions,
    private val fs: FileSystem = FileSystem.SYSTEM,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * The two refusals that must happen **before any bytes move**: a download that would run over
     * mobile data against the reader's wishes, and one that would fill the device.
     *
     * @param allowMetered the setting or this download's own one-time override, already resolved.
     * @return the reason to refuse, or null to go ahead.
     */
    suspend fun precheck(asset: SurahAsset, allowMetered: Boolean): DownloadFailure? {
        conditions.refusal(allowMetered)?.let { return it }
        // The whole asset, not what is left to fetch: spec §8's rule is about the device having
        // room to hold the surah, and a part that is already there is room already spent.
        if (conditions.freeBytes() < HEADROOM_BYTES + asset.bytes) {
            return DownloadFailure.NOT_ENOUGH_SPACE
        }
        return null
    }

    /**
     * Hashes the finished `.part` against the manifest and, if it matches, makes it the surah the
     * reader owns. A mismatch has already deleted the part (see `RecitationLibrary.commit`), which
     * is why [DownloadFailure.CHECKSUM] is the one failure a Retry starts over from zero.
     */
    suspend fun finish(key: DownloadKey, asset: SurahAsset): DownloadOutcome =
        if (library.commit(key.reciterId, key.surah, asset.sha256)) DownloadOutcome.Done
        else DownloadOutcome.Failed(DownloadFailure.CHECKSUM)

    /**
     * The whole download of one surah.
     *
     * Resume is the `.part`'s own length asked for with a `Range`; a server that ignores it and
     * sends the file from the beginning is not an error but a restart, and the part is truncated
     * so the two halves can never be spliced. Cancellation is left to propagate with the part
     * intact and flushed — the next attempt continues from it.
     *
     * [onState] is called at most every 250 ms or 256 KB, whichever comes first: on Android every
     * one of these is a `setProgress` round trip to the WorkManager database.
     */
    suspend fun run(
        key: DownloadKey,
        url: String,
        asset: SurahAsset,
        allowMetered: Boolean,
        onState: suspend (DownloadState) -> Unit,
    ): DownloadOutcome {
        precheck(asset, allowMetered)?.let { return DownloadOutcome.Failed(it) }

        val part = library.partFor(key.reciterId, key.surah)
        part.parent?.let { fs.createDirectories(it) }
        var have = fs.metadataOrNull(part)?.size ?: 0L
        // A part at or past the finished size is not a part of this asset — a truncated manifest
        // change, a half-written file from another build. Start again rather than resume into it.
        if (have >= asset.bytes) {
            runCatching { fs.delete(part) }
            have = 0L
        }
        onState(DownloadState.Downloading(have, asset.bytes))

        val response = when (val opened = source.open(url, have)) {
            is ByteResponse.Failure -> return DownloadOutcome.Failed(opened.reason)
            is ByteResponse.Body -> opened
        }

        var written = have
        try {
            if (response.status != PARTIAL_CONTENT && have > 0L) {
                // The server ignored the Range and is sending the whole file. Anything already on
                // disk would end up in front of a complete copy.
                runCatching { fs.delete(part) }
                written = 0L
            }
            val sink = if (written > 0L) fs.appendingSink(part) else fs.sink(part)
            sink.buffer().use { out ->
                response.body.use { input ->
                    val buffer = Buffer()
                    var lastAt = now()
                    var lastBytes = written
                    while (true) {
                        // The only cancellation point in the stream; a cancelled download leaves
                        // everything written so far, because `use` still flushes and closes.
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer, CHUNK_BYTES)
                        if (read == -1L) break
                        out.write(buffer, read)
                        written += read
                        val at = now()
                        if (at - lastAt >= PROGRESS_MILLIS || written - lastBytes >= PROGRESS_BYTES) {
                            out.flush()
                            lastAt = at
                            lastBytes = written
                            onState(DownloadState.Downloading(written, asset.bytes))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // The part stays: a connection that dropped at 80% is the case resume exists for.
            return DownloadOutcome.Failed(DownloadFailure.SERVER)
        }

        if (written != asset.bytes) {
            // Short is resumable and is left alone; long is a file this manifest cannot describe.
            if (written > asset.bytes) runCatching { fs.delete(part) }
            return DownloadOutcome.Failed(DownloadFailure.SERVER)
        }

        onState(DownloadState.Verifying)
        return finish(key, asset)
    }

    private companion object {
        /** Spec §8: a device below this much free refuses a download, plus the surah's own size. */
        const val HEADROOM_BYTES = 200L * 1024L * 1024L
        const val CHUNK_BYTES = 64L * 1024L
        const val PROGRESS_MILLIS = 250L
        const val PROGRESS_BYTES = 256L * 1024L
        const val PARTIAL_CONTENT = 206
    }
}
