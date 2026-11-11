package world.taqwa.app.watchfeed

import kotlinx.coroutines.delay
import kotlin.time.Duration

/** The origin the sync's tests run under. */
internal const val TEST_ORIGIN = "0123456789abcdef"

/**
 * The sync's record held in the test's own memory, with what was done to it, so a virtual-time
 * test never waits on a file. [DataStoreWatchFeedMemory] has its own tests over a real store. The
 * first read makes the record under [origin], as the real one makes it in its first edit. Each read
 * takes [readTakes] of virtual time, as a slow store's would, unless [takes] still holds a time for
 * it, in order.
 */
internal class FakeWatchFeedMemory(
    var record: WatchFeedRecord? = null,
    private val origin: String = TEST_ORIGIN,
) : WatchFeedMemory {
    val calls: MutableList<String> = mutableListOf()
    var readTakes: Duration = Duration.ZERO
    val takes: ArrayDeque<Duration> = ArrayDeque()

    override suspend fun read(): WatchFeedRecord {
        calls += "read"
        val wait = takes.removeFirstOrNull() ?: readTakes
        if (wait > Duration.ZERO) delay(wait)
        return record ?: WatchFeedRecord(origin).also { record = it }
    }

    override suspend fun write(record: WatchFeedRecord) {
        calls += "write ${record.lastSeq}"
        this.record = record
    }
}
