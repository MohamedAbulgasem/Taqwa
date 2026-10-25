package world.taqwa.app.recitation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.IOException
import world.taqwa.app.crash.ReadOnlyFileSystem
import kotlin.coroutines.cancellation.CancellationException
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Source
import okio.Timeout
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The download loop, driven off a fake server and a fake disk.
 *
 * Everything worth getting wrong about a download is in here rather than in either platform's
 * scheduler: resuming from a part, a server that ignores the `Range` it was sent, a file that
 * hashes wrong, a cancellation in the middle of the stream, and the two refusals that have to
 * happen before a single byte moves.
 */
class DownloadLoopTest {

    private val fs = FakeFileSystem()
    private val paths = RecitationPaths("/files".toPath())
    private val reciter = "ar.alafasy"
    private val key = DownloadKey(reciter, 112)

    private val content = buildTaqa(
        surah = 112,
        ayahs = listOf(ByteArray(400) { 7 }, ByteArray(600) { 9 }),
    )

    private val library = RecitationLibrary(
        paths,
        PreferenceDataStoreFactory.createWithPath {
            "/tmp/taqwa-download-${Random.nextULong()}.preferences_pb".toPath()
        },
        fs,
    )

    private val part get() = paths.partFile(reciter, 112)
    private val file get() = paths.surahFile(reciter, 112)

    private fun asset(sha: String = hashOf(content)) =
        SurahAsset(n = 112, bytes = content.size.toLong(), sha256 = sha)

    private fun hashOf(bytes: ByteArray): String {
        val scratch = "/scratch.bin".toPath()
        fs.write(scratch) { write(bytes) }
        val hash = TaqaFile(scratch, fs).sha256()
        fs.delete(scratch)
        return hash
    }

    private fun writePart(bytes: ByteArray) {
        fs.createDirectories(paths.reciterDir(reciter))
        fs.write(part) { write(bytes) }
    }

    private fun sizeOf(path: okio.Path): Long? = fs.metadataOrNull(path)?.size

    private fun loop(
        server: FakeServer = FakeServer(content),
        conditions: FakeConditions = FakeConditions(),
    ) = DownloadLoop(library, server, conditions, fs) { 0L }

    @Test
    fun aFreshDownloadArrivesAndIsCommitted() = runTest {
        val server = FakeServer(content)
        val states = mutableListOf<DownloadState>()
        val outcome = loop(server).run(key, URL, asset(), allowMetered = false) { states += it }

        assertEquals(DownloadOutcome.Done, outcome)
        assertEquals(0L, server.lastFrom)
        assertFalse(fs.exists(part))
        assertEquals(content.size.toLong(), sizeOf(file))
        assertEquals(setOf(112), library.downloaded(reciter).first())
        assertTrue(states.contains(DownloadState.Verifying))
    }

    @Test
    fun aPartAtFortyPercentIsResumedRatherThanRefetched() = runTest {
        val already = (content.size * 4) / 10
        writePart(content.copyOfRange(0, already))
        val server = FakeServer(content)

        val outcome = loop(server).run(key, URL, asset(), allowMetered = false) {}

        assertEquals(DownloadOutcome.Done, outcome)
        // The server was asked for exactly the bytes that were missing.
        assertEquals(already.toLong(), server.lastFrom)
        assertEquals(content.size.toLong() - already, server.served)
        assertEquals(content.size.toLong(), sizeOf(file))
    }

    @Test
    fun aServerThatIgnoresTheRangeRestartsFromZero() = runTest {
        val already = (content.size * 4) / 10
        writePart(content.copyOfRange(0, already))
        // 200 with the whole file: the bytes already on disk would otherwise be spliced in front
        // of a complete copy and the hash would fail for a reason nobody could see.
        val server = FakeServer(content, honourRange = false)

        val outcome = loop(server).run(key, URL, asset(), allowMetered = false) {}

        assertEquals(DownloadOutcome.Done, outcome)
        assertEquals(already.toLong(), server.lastFrom)
        assertEquals(content.size.toLong(), server.served)
        assertEquals(content.size.toLong(), sizeOf(file))
    }

    @Test
    fun aHashThatDoesNotMatchLeavesNothingBehind() = runTest {
        val outcome = loop().run(key, URL, asset(sha = "00".repeat(32)), allowMetered = false) {}

        assertEquals(DownloadOutcome.Failed(DownloadFailure.CHECKSUM), outcome)
        assertFalse(fs.exists(part))
        assertFalse(fs.exists(file))
        assertTrue(library.downloaded(reciter).first().isEmpty())
    }

    @Test
    fun aCancellationMidStreamLeavesAPartToResumeFrom() = runTest {
        var job: Job? = null
        val stopAfter = content.size / 2
        val server = FakeServer(content, chunk = 64, onChunk = { at ->
            if (at >= stopAfter) job?.cancel()
        })

        job = launch { loop(server).run(key, URL, asset(), allowMetered = false) {} }
        job.join()

        val written = sizeOf(part) ?: 0L
        assertTrue(written >= 64L, "a cancelled download left no part")
        assertTrue(written < content.size.toLong(), "the whole file arrived despite the cancel")
        assertFalse(fs.exists(file))

        // And the part is a part: a second run resumes from it and finishes.
        val second = FakeServer(content)
        assertEquals(DownloadOutcome.Done, loop(second).run(key, URL, asset(), allowMetered = false) {})
        assertEquals(written, second.lastFrom)
    }

    @Test
    fun aDeviceWithoutRoomIsRefusedBeforeAnyBytesMove() = runTest {
        val conditions = FakeConditions(free = 200L * 1024 * 1024 + content.size - 1)
        val server = FakeServer(content)

        val outcome = loop(server, conditions).run(key, URL, asset(), allowMetered = false) {}

        assertEquals(DownloadOutcome.Failed(DownloadFailure.NOT_ENOUGH_SPACE), outcome)
        assertEquals(-1L, server.lastFrom)
        assertFalse(fs.exists(part))
    }

    @Test
    fun aMeteredNetworkIsRefusedUnlessThisDownloadOverridesIt() = runTest {
        val conditions = FakeConditions(kind = NetworkKind.METERED)
        val refused = FakeServer(content)
        assertEquals(
            DownloadOutcome.Failed(DownloadFailure.NEEDS_WIFI),
            loop(refused, conditions).run(key, URL, asset(), allowMetered = false) {},
        )
        assertEquals(-1L, refused.lastFrom)

        val allowed = FakeServer(content)
        assertEquals(
            DownloadOutcome.Done,
            loop(allowed, conditions).run(key, URL, asset(), allowMetered = true) {},
        )
        assertEquals(content.size.toLong(), sizeOf(file))
    }

    @Test
    fun noNetworkAtAllIsItsOwnAnswer() = runTest {
        val conditions = FakeConditions(kind = NetworkKind.NONE)
        assertEquals(
            DownloadOutcome.Failed(DownloadFailure.NO_NETWORK),
            loop(FakeServer(content), conditions).run(key, URL, asset(), allowMetered = true) {},
        )
    }

    // The same three answers asked at the tap rather than in the middle of a transfer. This is
    // the branch a device in flight mode falls through: nothing is metered when nothing is
    // connected, so before `refusal` existed the work was enqueued and WorkManager parked it on a
    // constraint for ever while the sheet drew a progress bar at zero.

    @Test
    fun theTapRefusesADeviceWithNoNetworkAtAll() = runTest {
        assertEquals(
            DownloadFailure.NO_NETWORK,
            FakeConditions(kind = NetworkKind.NONE).refusal(allowMetered = false),
        )
    }

    @Test
    fun noNetworkIsNotSomethingTheMobileDataOverrideCanAnswer() = runTest {
        assertEquals(
            DownloadFailure.NO_NETWORK,
            FakeConditions(kind = NetworkKind.NONE).refusal(allowMetered = true),
        )
    }

    @Test
    fun theTapRefusesMobileDataUntilThisDownloadOverridesIt() = runTest {
        val metered = FakeConditions(kind = NetworkKind.METERED)
        assertEquals(DownloadFailure.NEEDS_WIFI, metered.refusal(allowMetered = false))
        assertNull(metered.refusal(allowMetered = true))
    }

    @Test
    fun theTapLetsAnUnmeteredNetworkThrough() = runTest {
        assertNull(FakeConditions().refusal(allowMetered = false))
    }

    @Test
    fun aBodyShorterThanTheManifestPromisedIsAServerFailureAndKeepsThePart() = runTest {
        val server = FakeServer(content.copyOfRange(0, content.size - 100))
        val outcome = loop(server).run(key, URL, asset(), allowMetered = false) {}

        assertEquals(DownloadOutcome.Failed(DownloadFailure.SERVER), outcome)
        assertEquals(content.size.toLong() - 100, sizeOf(part))
        assertFalse(fs.exists(file))
    }

    @Test
    fun theServersRefusalIsReportedRatherThanThrown() = runTest {
        val server = FakeServer(content, refuse = DownloadFailure.SERVER)
        assertEquals(
            DownloadOutcome.Failed(DownloadFailure.SERVER),
            loop(server).run(key, URL, asset(), allowMetered = false) {},
        )
    }

    @Test
    fun precheckPassesWhenThereIsRoomAndWiFi() = runTest {
        assertNull(loop().precheck(asset(), allowMetered = false))
    }

    // ── B2: room for what accepted downloads still have to write ───────────────────────

    private val manifestSizes = mutableMapOf<DownloadKey, Long>()

    private fun sized(n: Int, mib: Long) = SurahAsset(n = n, bytes = mib * MIB, sha256 = "00".repeat(32))

    /**
     * What iOS's start() does with each surah of a request, in order: the decision is [admit],
     * and an accepted surah is reported Queued (which is what reserves its bytes for the next
     * check) and a refused one Failed, as start() reports them.
     */
    private suspend fun DownloadLoop.request(
        batch: List<SurahAsset>,
        states: MutableMap<DownloadKey, DownloadState> = mutableMapOf(),
        reciterId: String = reciter,
    ): List<DownloadFailure?> {
        // The manifest knows every surah's size, those of earlier requests included.
        manifestSizes += batch.associate { DownloadKey(reciterId, it.n) to it.bytes }
        return batch.map { asset ->
            val at = DownloadKey(reciterId, asset.n)
            when (val admission = admit(at, asset, false, states) { manifestSizes[it] ?: OTHER_SIZES[it] }) {
                Admission.OnItsWay -> null
                is Admission.Refused -> admission.reason.also { states[at] = DownloadState.Failed(it) }
                Admission.Start -> null.also { states[at] = DownloadState.Queued }
            }
        }
    }

    @Test
    fun precheckCountsWhatAcceptedDownloadsStillHaveToWrite() = runTest {
        val loop = loop(conditions = FakeConditions(free = HEADROOM + content.size + 1_000))
        assertNull(loop.precheck(asset(), allowMetered = false, reservedBytes = 0L))
        assertNull(loop.precheck(asset(), allowMetered = false, reservedBytes = 1_000L))
        assertEquals(DownloadFailure.NOT_ENOUGH_SPACE, loop.precheck(asset(), allowMetered = false, reservedBytes = 1_001L))
    }

    @Test
    fun theNetworkIsStillAskedBeforeTheReservation() = runTest {
        val loop = loop(conditions = FakeConditions(kind = NetworkKind.NONE, free = 0L))
        assertEquals(DownloadFailure.NO_NETWORK, loop.precheck(asset(), allowMetered = false, reservedBytes = 10L * 1024 * MIB))
    }

    /** iOS answers Long.MAX_VALUE when it cannot tell how full the volume is: nothing overflows. */
    @Test
    fun aVolumeThatCannotSayHowFullItIsIsNeverShortOfRoom() = runTest {
        val loop = loop(conditions = FakeConditions(free = Long.MAX_VALUE))
        assertNull(loop.precheck(asset(), allowMetered = false, reservedBytes = 5L * 1024 * MIB))
    }

    @Test
    fun aQueuedSurahReservesTheWholeSurahAndADownloadingOneWhatIsLeft() {
        val k1 = DownloadKey(reciter, 1)
        val k2 = DownloadKey(reciter, 2)
        val k3 = DownloadKey(reciter, 3)
        val k4 = DownloadKey(reciter, 4)
        val states = mapOf(
            k1 to DownloadState.Queued,
            k2 to DownloadState.Downloading(30, 100),
            k3 to DownloadState.Verifying,
            k4 to DownloadState.Failed(DownloadFailure.SERVER),
        )
        val sizes = mapOf(k1 to 50L, k2 to 100L, k3 to 100L, k4 to 999L)
        assertEquals(120L, stillToLand(states) { sizes[it] })
    }

    @Test
    fun aDownloadPastItsSizeGivesNoRoomBack() {
        assertEquals(0L, stillToLand(mapOf(key to DownloadState.Downloading(120, 100))) { 100L })
    }

    /** The delegate's fallback total is wrong once the size map has lost its entry; the manifest is not. */
    @Test
    fun theManifestSizeBeatsAReportedTotalThatLostItsSize() {
        val lost = mapOf(key to DownloadState.Downloading(10 * MIB, 10 * MIB - 1))
        assertEquals(290 * MIB, stillToLand(lost) { 300 * MIB })
        assertEquals(0L, stillToLand(lost) { null })
        assertEquals(0L, stillToLand(mapOf(key to DownloadState.Queued)) { null })
    }

    @Test
    fun aReciterLargerThanTheFreeSpaceIsAcceptedOnlyAsFarAsItFits() = runTest {
        val loop = loop(conditions = FakeConditions(free = 600 * MIB))
        val batch = listOf(sized(1, 100), sized(2, 250), sized(3, 120), sized(4, 40), sized(5, 20), sized(6, 10), sized(7, 200))
        val states = mutableMapOf<DownloadKey, DownloadState>()

        val answers = loop.request(batch, states)

        val no = DownloadFailure.NOT_ENOUGH_SPACE
        assertEquals(listOf(null, null, no, null, no, null, no), answers)
        val accepted = batch.filter { states[DownloadKey(reciter, it.n)] == DownloadState.Queued }.sumOf { it.bytes }
        assertEquals(400 * MIB, accepted)
    }

    /** A refusal never stops the batch: a smaller surah further on can still fit. */
    @Test
    fun aSmallerLaterSurahStillFitsAfterABigOneWasRefused() = runTest {
        val loop = loop(conditions = FakeConditions(free = 600 * MIB))
        val answers = loop.request(listOf(sized(1, 350), sized(2, 100), sized(3, 50)))
        assertEquals(listOf(null, DownloadFailure.NOT_ENOUGH_SPACE, null), answers)
    }

    @Test
    fun theHeadroomIsKeptOnceForTheWholeBatch() = runTest {
        val loop = loop(conditions = FakeConditions(free = HEADROOM + 300 * MIB))
        val states = mutableMapOf<DownloadKey, DownloadState>()
        assertEquals(listOf(null, null, null), loop.request(listOf(sized(1, 100), sized(2, 100), sized(3, 100)), states))
        val oneByte = SurahAsset(n = 4, bytes = 1L, sha256 = "00".repeat(32))
        assertEquals(listOf(DownloadFailure.NOT_ENOUGH_SPACE), loop.request(listOf(oneByte), states))
    }

    /**
     * Another reciter's downloads still in flight are counted too: 70 MiB left of one that is
     * downloading and 50 MiB of one queued, while a failed one and one being verified hold nothing.
     */
    @Test
    fun aSecondRequestCountsWhatTheFirstStillHasToFetch() = runTest {
        val loop = loop(conditions = FakeConditions(free = 450 * MIB))
        val states = OTHER_IN_FLIGHT.toMutableMap()
        assertEquals(listOf(null), loop.request(listOf(sized(1, 130)), states))

        val again = OTHER_IN_FLIGHT.toMutableMap()
        assertEquals(listOf(DownloadFailure.NOT_ENOUGH_SPACE), loop.request(listOf(SurahAsset(n = 1, bytes = 131 * MIB, sha256 = "00".repeat(32))), again))
    }

    /**
     * A surah already on its way is left to the task carrying it, room or not: asking again would
     * book its room twice and write a refusal over a live task. A failed one is asked again.
     */
    @Test
    fun aSurahAlreadyOnItsWayIsLeftToItsTask() = runTest {
        val loop = loop(conditions = FakeConditions(free = 0L))
        for (state in listOf(DownloadState.Queued, DownloadState.Downloading(1, 2), DownloadState.Verifying)) {
            assertEquals(Admission.OnItsWay, loop.admit(key, asset(), false, mapOf(key to state)) { null }, "$state")
        }
        assertEquals(
            Admission.Refused(DownloadFailure.NOT_ENOUGH_SPACE),
            loop.admit(key, asset(), false, mapOf(key to DownloadState.Failed(DownloadFailure.NOT_ENOUGH_SPACE))) { null },
        )
    }

    // ── B11: a commit that throws is a reason, never an exception ───────────────────────

    private fun libraryOver(store: DataStore<Preferences>, files: okio.FileSystem = fs) = RecitationLibrary(paths, store, files)

    private fun freshStore(): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-download-${Random.nextULong()}.preferences_pb".toPath()
    }

    @Test
    fun aCommitThatCannotMoveThePartOnAFullDiskIsNotEnoughSpace() = runTest {
        writePart(content)
        val loop = DownloadLoop(libraryOver(freshStore(), ReadOnlyFileSystem(fs)), FakeServer(content), FakeConditions(free = 0L), fs)

        assertEquals(DownloadOutcome.Failed(DownloadFailure.NOT_ENOUGH_SPACE), loop.finish(key, asset()))
        assertTrue(fs.exists(part))
        assertFalse(fs.exists(file))
        fs.checkNoOpenFiles()
    }

    @Test
    fun aCommitThatCannotMoveThePartWithRoomToSpareIsAServerFailure() = runTest {
        writePart(content)
        val loop = DownloadLoop(libraryOver(freshStore(), ReadOnlyFileSystem(fs)), FakeServer(content), FakeConditions(), fs)
        assertEquals(DownloadOutcome.Failed(DownloadFailure.SERVER), loop.finish(key, asset()))
    }

    /** The rule a lone Retry's precheck would apply, so the chip keeps its sentence when Retry is pressed. */
    @Test
    fun aFailedCommitIsJudgedByTheRuleARetryWouldBe() = runTest {
        writePart(content)
        val short = FakeConditions(free = HEADROOM + content.size - 1)
        val enough = FakeConditions(free = HEADROOM + content.size)
        val readOnly = libraryOver(freshStore(), ReadOnlyFileSystem(fs))
        assertEquals(DownloadOutcome.Failed(DownloadFailure.NOT_ENOUGH_SPACE), DownloadLoop(readOnly, FakeServer(content), short, fs).finish(key, asset()))
        assertEquals(DownloadOutcome.Failed(DownloadFailure.SERVER), DownloadLoop(readOnly, FakeServer(content), enough, fs).finish(key, asset()))
    }

    /**
     * The registry write fails after the move: the surah is at its own name, unregistered, and the
     * library's reconcile adopts it at the next start. Nothing is cleaned up by the catch.
     */
    @Test
    fun aRegistryThatCannotBeWrittenIsReportedRatherThanThrown() = runTest {
        writePart(content)
        val full = FailingDataStore { throw IOException("No space left on device") }
        assertEquals(DownloadOutcome.Failed(DownloadFailure.SERVER), DownloadLoop(libraryOver(full), FakeServer(content), FakeConditions(), fs).finish(key, asset()))
        assertTrue(fs.exists(file))
        assertFalse(fs.exists(part))

        fs.delete(file)
        writePart(content)
        assertEquals(DownloadOutcome.Failed(DownloadFailure.NOT_ENOUGH_SPACE), DownloadLoop(libraryOver(full), FakeServer(content), FakeConditions(free = 0L), fs).finish(key, asset()))
    }

    @Test
    fun freeSpaceThatCannotBeReadAfterAFailedCommitIsAServerFailure() = runTest {
        writePart(content)
        val conditions = FakeConditions(freeError = IOException("stat"))
        assertEquals(DownloadOutcome.Failed(DownloadFailure.SERVER), DownloadLoop(libraryOver(freshStore(), ReadOnlyFileSystem(fs)), FakeServer(content), conditions, fs).finish(key, asset()))
    }

    @Test
    fun aCancellationExceptionFromTheCommitIsRethrown() = runTest {
        writePart(content)
        val stopped = FailingDataStore { throw CancellationException("stopped") }
        assertFailsWith<CancellationException> { DownloadLoop(libraryOver(stopped), FakeServer(content), FakeConditions(), fs).finish(key, asset()) }
    }

    @Test
    fun aCommitCancelledWhileItWaitsIsNotTurnedIntoAFailure() = runTest {
        writePart(content)
        val waiting = FailingDataStore { awaitCancellation() }
        var outcome: DownloadOutcome? = null
        val job = launch { outcome = DownloadLoop(libraryOver(waiting), FakeServer(content), FakeConditions(), fs).finish(key, asset()) }
        runCurrent()
        job.cancel()
        job.join()
        assertNull(outcome)
        assertTrue(job.isCancelled)
    }

    /** Android's path, through run(): the disk fills while the surah arrives, and the commit cannot move it. */
    @Test
    fun aDiskThatFillsDuringTheDownloadIsNotEnoughSpaceAtTheCommit() = runTest {
        val conditions = FakeConditions()
        val server = FakeServer(content, onChunk = { conditions.free = 0L })
        val states = mutableListOf<DownloadState>()
        val outcome = DownloadLoop(libraryOver(freshStore(), ReadOnlyFileSystem(fs)), server, conditions, fs) { 0L }
            .run(key, URL, asset(), allowMetered = false) { states += it }
        assertEquals(DownloadOutcome.Failed(DownloadFailure.NOT_ENOUGH_SPACE), outcome)
        assertTrue(states.contains(DownloadState.Verifying))
    }

    /** iOS's append of a landed surah uses the same rule. */
    @Test
    fun aFailedWriteOnAFullDiskIsNotEnoughSpace() = runTest {
        val size = content.size.toLong()
        assertEquals(DownloadFailure.NOT_ENOUGH_SPACE, loop(conditions = FakeConditions(free = 0L)).reasonForFailedWrite(size))
        assertEquals(DownloadFailure.SERVER, loop(conditions = FakeConditions()).reasonForFailedWrite(size))
        assertEquals(DownloadFailure.SERVER, loop(conditions = FakeConditions(freeError = IOException("stat"))).reasonForFailedWrite(size))
    }

    private companion object {
        const val URL = "https://example.test/ar.alafasy-112.taqa"
        const val MIB = 1024L * 1024L
        const val HEADROOM = 200L * MIB

        /** Another reciter's downloads: 70 MiB still to come of one, 50 MiB of a queued one, nothing of the rest. */
        val OTHER_IN_FLIGHT: Map<DownloadKey, DownloadState> = mapOf(
            DownloadKey("ar.minshawi", 2) to DownloadState.Downloading(30 * MIB, 100 * MIB),
            DownloadKey("ar.minshawi", 3) to DownloadState.Queued,
            DownloadKey("ar.minshawi", 4) to DownloadState.Failed(DownloadFailure.SERVER),
            DownloadKey("ar.minshawi", 5) to DownloadState.Verifying,
        )
        val OTHER_SIZES: Map<DownloadKey, Long> = mapOf(
            DownloadKey("ar.minshawi", 2) to 100 * MIB,
            DownloadKey("ar.minshawi", 3) to 50 * MIB,
            DownloadKey("ar.minshawi", 4) to 999 * MIB,
            DownloadKey("ar.minshawi", 5) to 120 * MIB,
        )
    }
}

/** Serves [content] from whatever offset it is asked for — or refuses, or ignores the Range. */
private class FakeServer(
    private val content: ByteArray,
    private val honourRange: Boolean = true,
    private val refuse: DownloadFailure? = null,
    private val chunk: Int = 4096,
    private val onChunk: ((Long) -> Unit)? = null,
) : ByteSource {

    /** The offset the loop asked for, or -1 if it never asked. */
    var lastFrom: Long = -1L
        private set

    /** How many bytes this server handed over. */
    var served: Long = 0L
        private set

    override suspend fun open(url: String, fromByte: Long): ByteResponse {
        lastFrom = fromByte
        refuse?.let { return ByteResponse.Failure(it) }
        val start = if (honourRange) fromByte.toInt() else 0
        val slice = content.copyOfRange(start.coerceIn(0, content.size), content.size)
        served = slice.size.toLong()
        val status = if (honourRange && fromByte > 0L) 206 else 200
        return ByteResponse.Body(status, slice.size.toLong(), ChunkedSource(slice, chunk, onChunk))
    }
}

/** Hands the bytes over a few at a time, so a cancellation can land in the middle of one. */
private class ChunkedSource(
    private val bytes: ByteArray,
    private val chunk: Int,
    private val onChunk: ((Long) -> Unit)?,
) : Source {

    private var position = 0

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (position >= bytes.size) return -1L
        val count = minOf(chunk.toLong(), byteCount, (bytes.size - position).toLong()).toInt()
        sink.write(bytes, position, count)
        position += count
        onChunk?.invoke(position.toLong())
        return count.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}

private class FakeConditions(
    private val kind: NetworkKind = NetworkKind.UNMETERED,
    var free: Long = 4L * 1024 * 1024 * 1024,
    private val freeError: Exception? = null,
) : DownloadConditions {
    override suspend fun network(): NetworkKind = kind
    override suspend fun freeBytes(): Long = freeError?.let { throw it } ?: free
}

/** A registry store whose every write does [onUpdate]: throws, as a full disk does, or never returns. */
private class FailingDataStore(private val onUpdate: suspend () -> Nothing) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = onUpdate()
}
