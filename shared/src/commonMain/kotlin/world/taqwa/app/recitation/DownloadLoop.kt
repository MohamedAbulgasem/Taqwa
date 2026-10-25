package world.taqwa.app.recitation

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Source
import okio.buffer
import okio.use
import kotlin.coroutines.cancellation.CancellationException
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

/**
 * The bytes the downloads already accepted still have to write: room they will take that free
 * space does not show yet (bug hunt B2).
 *
 * A queued surah counts whole, because iOS reports Queued only when no part of it is on disk. A
 * downloading one counts only what is left: what has arrived, the part and the session's own
 * temporary file, is already out of free space. The manifest's size ([sizeOf]) comes before the
 * reported total, because the delegate falls back to a wrong total once it has lost the size it
 * was given. A download that ran past its size gives no room back. A surah being verified has
 * landed, and its commit is a rename within the same folder; a failed one has no task.
 */
internal fun stillToLand(states: Map<DownloadKey, DownloadState>, sizeOf: (DownloadKey) -> Long?): Long =
    states.entries.sumOf { (key, state) ->
        when (state) {
            DownloadState.Queued -> sizeOf(key) ?: 0L
            is DownloadState.Downloading -> ((sizeOf(key) ?: state.total) - state.bytes).coerceAtLeast(0L)
            DownloadState.Verifying, is DownloadState.Failed -> 0L
        }
    }

/** What iOS's start() does with one surah of a request ([admit]). */
internal sealed interface Admission {
    /** Already on its way: left to the task carrying it. */
    data object OnItsWay : Admission

    /** Not started, for [reason], which is reported as the surah's failure. */
    data class Refused(val reason: DownloadFailure) : Admission

    /** Start it, and report it before the next surah is asked about: that report reserves its bytes. */
    data object Start : Admission
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
     * A surah is refused for room exactly when free space is below the headroom, plus the surah,
     * plus [reservedBytes]. The headroom is kept once, as the floor left after everything already
     * promised has landed. Nothing is ever added to free space, because iOS answers
     * `Long.MAX_VALUE` when it cannot tell.
     *
     * @param allowMetered the setting or this download's own one-time override, already resolved.
     * @param reservedBytes what other accepted downloads still have to write ([stillToLand]). iOS
     * passes it: a background session starts its tasks when the system chooses, so free space has
     * not moved by the time the next surah of a request asks, and a whole Quran used to be accepted
     * surah by surah against the same figure until it filled the phone. Android passes nothing:
     * each worker asks at its own transfer, two at a time, and the headroom is larger than any one
     * surah.
     * @return the reason to refuse, or null to go ahead.
     */
    suspend fun precheck(asset: SurahAsset, allowMetered: Boolean, reservedBytes: Long = 0L): DownloadFailure? {
        conditions.refusal(allowMetered)?.let { return it }
        // The whole asset, not what is left to fetch: spec §8's rule is about the device having
        // room to hold the surah, and a part that is already there is room already spent. What is
        // reserved for the other downloads is only what they still have to fetch, because their
        // parts are already gone from free space.
        if (isShortOfRoom(conditions.freeBytes(), asset.bytes, reservedBytes)) {
            return DownloadFailure.NOT_ENOUGH_SPACE
        }
        return null
    }

    /**
     * The rule iOS's start() applies to each surah of a request, in order: a surah already on its
     * way ([isOnItsWay] in [states]: queued, downloading or being verified) is left to the task
     * carrying it, as Android's unique work with `KEEP` leaves it, because asking again would start
     * a second task on the same `.part`, book its room twice and write a refusal over a live task;
     * any other is [precheck]ed against what the downloads in [states] still have to write. A
     * failed surah is asked again, so tapping the offer again re-checks the ones refused for room.
     */
    internal suspend fun admit(
        key: DownloadKey,
        asset: SurahAsset,
        allowMetered: Boolean,
        states: Map<DownloadKey, DownloadState>,
        sizeOf: (DownloadKey) -> Long?,
    ): Admission {
        if (states[key].isOnItsWay) return Admission.OnItsWay
        precheck(asset, allowMetered, stillToLand(states, sizeOf))?.let { return Admission.Refused(it) }
        return Admission.Start
    }

    /**
     * Why a write of a surah of [bytes] failed: [DownloadFailure.NOT_ENOUGH_SPACE] by the rule a
     * lone Retry's [precheck] will apply, so the chip keeps the same sentence when Retry is
     * pressed, and [DownloadFailure.SERVER] otherwise. Deliberately not "free below the surah": by
     * the time a write has failed, its bytes are either already freed or still on disk. Free space
     * that cannot be read means SERVER.
     */
    internal suspend fun reasonForFailedWrite(bytes: Long): DownloadFailure {
        val free = try {
            conditions.freeBytes()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            return DownloadFailure.SERVER
        }
        return if (isShortOfRoom(free, bytes)) DownloadFailure.NOT_ENOUGH_SPACE else DownloadFailure.SERVER
    }

    /**
     * Hashes the finished `.part` against the manifest and, if it matches, makes it the surah the
     * reader owns. A mismatch has already deleted the part (see `RecitationLibrary.commit`), which
     * is why [DownloadFailure.CHECKSUM] is the one failure a Retry starts over from zero.
     *
     * A commit that throws (a full disk refusing the move, the registry's write or a folder) is a
     * reason, never an exception: on iOS an escaped throw left the Verifying chip on screen for
     * good and wrote a crash report for a crash that never happened. Nothing is cleaned up here: a
     * surah moved but not registered is adopted by the library's reconcile at the next start, and
     * a verified part that could not be moved is fetched again by Retry. Cancellation goes on.
     */
    suspend fun finish(key: DownloadKey, asset: SurahAsset): DownloadOutcome {
        val owned = try {
            library.commit(key.reciterId, key.surah, asset.sha256)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            return DownloadOutcome.Failed(reasonForFailedWrite(asset.bytes))
        }
        return if (owned) DownloadOutcome.Done else DownloadOutcome.Failed(DownloadFailure.CHECKSUM)
    }

    private fun isShortOfRoom(free: Long, bytes: Long, reserved: Long = 0L): Boolean =
        free < HEADROOM_BYTES + bytes + reserved

    /**
     * The whole download of one surah.
     *
     * Resume is the `.part`'s own length asked for with a `Range`; a server that ignores it and
     * sends the file from the beginning is not an error but a restart, and the part is truncated
     * so the two halves can never be spliced. Cancellation is left to propagate with the part
     * intact and flushed — the next attempt continues from it.
     *
     * [onState] is called at most every [PROGRESS_MILLIS], however fast the bytes arrive: on
     * Android every one of these is a `setProgress` round trip to the WorkManager database. It
     * used to fire every 256 KB as well, "whichever comes first" — which on good Wi-Fi is forty
     * times a second, per surah, with several surahs downloading at once (spec §18.1). The part
     * is still flushed every [PROGRESS_BYTES], because that is what a resume continues from.
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
                        if (written - lastBytes >= PROGRESS_BYTES) {
                            out.flush()
                            lastBytes = written
                        }
                        if (at - lastAt >= PROGRESS_MILLIS) {
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
        /**
         * Spec §8: a device below this much free refuses a download, plus the surah's own size.
         * It must stay larger than the largest surah (127.2 MiB today, Hudhaify's Al-Baqarah):
         * Android's two transfers at a time and iOS's copy of a landed temporary file both rely on
         * that.
         */
        const val HEADROOM_BYTES = 200L * 1024L * 1024L
        const val CHUNK_BYTES = 64L * 1024L
        const val PROGRESS_MILLIS = 500L
        const val PROGRESS_BYTES = 256L * 1024L
        const val PARTIAL_CONTENT = 206
    }
}
