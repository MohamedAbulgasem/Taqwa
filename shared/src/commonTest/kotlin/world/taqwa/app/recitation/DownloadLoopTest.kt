package world.taqwa.app.recitation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Source
import okio.Timeout
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private companion object {
        const val URL = "https://example.test/ar.alafasy-112.taqa"
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
    private val free: Long = 4L * 1024 * 1024 * 1024,
) : DownloadConditions {
    override suspend fun network(): NetworkKind = kind
    override suspend fun freeBytes(): Long = free
}
