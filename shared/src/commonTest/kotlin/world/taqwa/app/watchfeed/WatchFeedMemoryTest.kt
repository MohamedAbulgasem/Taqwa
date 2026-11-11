package world.taqwa.app.watchfeed

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import world.taqwa.app.settings.SettingsKeys
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchLinkContract
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The phone's record of its watch (Ruling 50), in the settings store, over a real DataStore as
 * `TasbeehStoreTest` builds one. Order and outcome only: no budget and no virtual instant.
 */
class WatchFeedMemoryTest {

    private fun path(): Path = "/tmp/taqwa-watch-memory-${Random.nextULong()}.preferences_pb".toPath()

    private fun memory(path: Path = path(), newOrigin: () -> String = ::newWatchOrigin) =
        DataStoreWatchFeedMemory(PreferenceDataStoreFactory.createWithPath { path }, newOrigin)

    @Test
    fun theFirstReadMakesTheOriginOnce() = runTest {
        var made = 0
        val memory = memory(newOrigin = { made++; "0123456789abcdef" })
        assertEquals(WatchFeedRecord("0123456789abcdef"), memory.read())
        assertEquals(WatchFeedRecord("0123456789abcdef"), memory.read())
        assertEquals(1, made, "made once, then kept")
    }

    @Test
    fun anOriginIsARandomNumberOfTheContractsForm() = runTest {
        val first = memory().read().origin
        val second = memory().read().origin
        assertTrue(WatchLinkContract.isOrigin(first), first)
        assertTrue(WatchLinkContract.isOrigin(second), second)
        assertNotEquals(first, second, "two installs, two origins")
    }

    @Test
    fun theRecordIsWrittenAndReadBackWhole() = runTest {
        val memory = memory()
        val origin = memory.read().origin
        val full = WatchFeedRecord(origin, lastSeq = 7, lastStandsFor = "000000000000000a", lastSent = "00000000000000ce", confirmedSeq = 6, confirmedFor = "k0")
        memory.write(full)
        assertEquals(full, memory.read())
        val bare = WatchFeedRecord(origin, lastSeq = 8)
        memory.write(bare)
        assertEquals(bare, memory.read(), "a field the record no longer has is removed")
    }

    /**
     * Origin and numbers live and die together (the design check's constraint 2): a store whose
     * origin is gone, however that came about, starts a whole new record under a new origin, so a
     * new origin never carries the old numbers' confirmation and a kept origin never meets fresh
     * numbers.
     */
    @Test
    fun aStoreWithoutAnOriginStartsAWholeNewRecord() = runTest {
        val store = PreferenceDataStoreFactory.createWithPath { path() }
        store.edit {
            it[SettingsKeys.WATCH_FEED_LAST_SEQ] = 9
            it[SettingsKeys.WATCH_FEED_CONFIRMED_SEQ] = 9
            it[SettingsKeys.WATCH_FEED_LAST_STANDS_FOR] = "000000000000000a"
        }
        val memory = DataStoreWatchFeedMemory(store) { "0123456789abcdef" }
        assertEquals(WatchFeedRecord("0123456789abcdef"), memory.read())
    }

    /**
     * Ruling 52 item 3: a stored origin the contract does not take, however it came about, is no
     * origin. The store starts a whole new record under a new one, so no ask can fail for good on a
     * delivery that cannot be made (each send's `WatchDelivery` refuses such an origin).
     */
    @Test
    fun aStoredOriginOfAnotherFormStartsAWholeNewRecord() = runTest {
        val x = "000000000000000a"
        for (bad in listOf("", "0123456789ABCDEF", "0123456789abcde", "0123456789abcdef0", "not an origin!!!")) {
            val store = PreferenceDataStoreFactory.createWithPath { path() }
            store.edit {
                it[SettingsKeys.WATCH_FEED_ORIGIN] = bad
                it[SettingsKeys.WATCH_FEED_LAST_SEQ] = 9
                it[SettingsKeys.WATCH_FEED_LAST_STANDS_FOR] = x
                it[SettingsKeys.WATCH_FEED_LAST_SENT] = x
                it[SettingsKeys.WATCH_FEED_CONFIRMED_SEQ] = 9
            }
            val memory = DataStoreWatchFeedMemory(store) { "0123456789abcdef" }
            val record = memory.read()
            assertEquals(WatchFeedRecord("0123456789abcdef"), record, "stored origin '$bad'")
            assertEquals(WatchDelivery("0123456789abcdef", 1, x), record.sending(x, x).lastDelivery, "stored origin '$bad'")
            assertEquals(record, memory.read(), "stored origin '$bad': the new record is kept")
        }
    }

    @Test
    fun itSurvivesANewStoreOnTheSameFile() = runTest {
        val path = path()
        // DataStore 1.1.7 refuses two active stores on one file, so the first one's scope is
        // cancelled and joined before the second opens it.
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val first = DataStoreWatchFeedMemory(PreferenceDataStoreFactory.createWithPath(scope = scope) { path })
        val record = first.read().copy(lastSeq = 3, lastStandsFor = "000000000000000a", lastSent = "000000000000000a", confirmedSeq = 3)
        first.write(record)
        scope.coroutineContext[Job]!!.cancelAndJoin()

        assertEquals(record, memory(path).read())
    }
}
