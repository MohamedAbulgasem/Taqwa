package world.taqwa.app.wear

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchLinkContract

class FeedStoreTest {

    private lateinit var directory: File
    private lateinit var feedFile: File

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory("feed-store").toFile()
        feedFile = File(directory, "feed.json")
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun writeThenReadReturnsTheFeed() {
        val store = FeedStore(feedFile)
        assertNull(store.read(), "a store nobody has written to has no feed")
        assertNull(store.changes.value)

        val feed = TestFeeds.sample
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(feed)))

        assertEquals(feed, store.read())
        assertEquals(feed, store.changes.value)
        // The store is the file and not the process: a watch app that starts later finds the feed.
        val restarted = FeedStore(feedFile)
        assertEquals(feed, restarted.read())
        assertEquals(feed, restarted.changes.value)
    }

    @Test
    fun aRejectedFeedLeavesTheOldOneInPlace() {
        val store = FeedStore(feedFile)
        val old = TestFeeds.sample
        val oldJson = TestFeeds.json(old)
        store.write(oldJson)

        val rejected = linkedMapOf(
            "empty text" to "",
            "text that is not JSON" to "this is not a feed",
            "JSON that is not an object" to "[1,2,3]",
            "a feed cut off in transit" to TestFeeds.sampleJson.take(TestFeeds.sampleJson.length / 2),
            "a feed without its days" to """{"version":1,"writtenAt":1}""",
            "a version that is no number" to TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":\"one\""),
        )
        rejected.forEach { (what, text) ->
            assertEquals(FeedWriteResult.MALFORMED, store.write(text), what)
            assertEquals(old, store.read(), what)
            assertEquals(old, store.changes.value, what)
            assertEquals(oldJson, feedFile.readText(), "$what: the file on disk is as it was")
            assertFalse(store.newerVersionSeen.value, "$what: garbage is not a newer version")
        }
    }

    @Test
    fun aNewerFeedIsReportedAndKeepsTheOldOne() {
        val store = FeedStore(feedFile)
        val old = TestFeeds.sample
        store.write(TestFeeds.json(old))
        assertFalse(store.newerVersionSeen.value, "nothing newer has been seen yet")

        // The phone has been updated and the watch has not: version 2, in the layout of version 1.
        val newer = TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")
        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(newer))
        assertEquals(old, store.read(), "the feed it understood is kept")
        assertEquals(old, store.changes.value)
        assertTrue(store.newerVersionSeen.value)

        // A newer feed is a newer feed whatever the rest of it looks like: this build cannot read it.
        val newerStill = """{"version":3,"days":"a layout this build has never seen"}"""
        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(newerStill))
        assertEquals(old, store.read())
        assertTrue(store.newerVersionSeen.value)

        // A feed this build can read ends the notice, and is the one kept.
        val fresh = TestFeeds.another()
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(fresh)))
        assertFalse(store.newerVersionSeen.value)
        assertEquals(fresh, store.read())
        assertEquals(fresh, store.changes.value)
    }

    @Test
    fun aNewerFeedIsReportedWhereNothingIsStoredToo() {
        // The first thing a new watch hears from an updated phone can be a feed it cannot read:
        // there is no old feed to keep, and the watch still has to say it must be updated (spec 2.6).
        val store = FeedStore(feedFile)
        assertNull(store.read())

        val newer = TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")
        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(newer))

        assertNull(store.read(), "a feed this build cannot read is not stored")
        assertNull(store.changes.value)
        assertFalse(feedFile.exists())
        assertTrue(store.newerVersionSeen.value)
        // The notice outlives the process, and an empty store is still an empty store when it is back.
        val restarted = FeedStore(feedFile)
        assertTrue(restarted.newerVersionSeen.value)
        assertNull(restarted.changes.value)

        // The first feed this build reads ends it.
        val fresh = TestFeeds.another()
        assertEquals(FeedWriteResult.ACCEPTED, restarted.write(TestFeeds.json(fresh)))
        assertFalse(restarted.newerVersionSeen.value)
        assertEquals(fresh, restarted.changes.value)
    }

    @Test
    fun aNoticeThatCannotBeKeptDoesNotFailTheWriteThatReportedIt() {
        // The notice's path is taken by a directory with something in it, so it cannot be written.
        val store = FeedStore(feedFile)
        val old = TestFeeds.sample
        store.write(TestFeeds.json(old))
        store.newerNotice.mkdirs()
        File(store.newerNotice, "in-the-way").writeText("x")

        val newer = TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")
        // Nothing is thrown: the watch is out of date whether or not the notice can be kept for the
        // next process, and it says so for as long as this one lives.
        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(newer))

        assertTrue(store.newerVersionSeen.value)
        assertEquals(old, store.read(), "the feed it understood is kept")
        assertEquals(old, store.changes.value)
        assertFalse(File(store.newerNotice.path + ".tmp").exists(), "and the half-done notice is cleaned up")
    }

    @Test
    fun garbageDoesNotEndTheUpdateNotice() {
        val store = FeedStore(feedFile)
        store.write(TestFeeds.json(TestFeeds.sample))
        store.write(TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2"))
        assertTrue(store.newerVersionSeen.value)

        assertEquals(FeedWriteResult.MALFORMED, store.write("not a feed"))

        assertTrue(store.newerVersionSeen.value, "text that is no feed says nothing about the format")
    }

    @Test
    fun theUpdateNoticeSurvivesTheProcessThatSawIt() {
        val store = FeedStore(feedFile)
        val old = TestFeeds.sample
        store.write(TestFeeds.json(old))
        store.write(TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2"))

        // Wear OS ends a process it has no use for within minutes, long before anyone looks.
        val restarted = FeedStore(feedFile)
        assertTrue(restarted.newerVersionSeen.value)
        assertEquals(old, restarted.changes.value)

        val fresh = TestFeeds.another()
        restarted.write(TestFeeds.json(fresh))
        assertFalse(FeedStore(feedFile).newerVersionSeen.value, "a readable feed ends it for good")
    }

    @Test
    fun anUpdateNoticeForAVersionThisBuildReadsHasExpired() {
        // What a build that did not read version 2 left behind, found by one that does: here a
        // notice for version 1, which every build reads.
        FeedStore(feedFile).newerNotice.writeText("1")

        assertFalse(FeedStore(feedFile).newerVersionSeen.value)
    }

    @Test
    fun writeIsAtomic() {
        val store = FeedStore(feedFile)
        val old = TestFeeds.sample
        store.write(TestFeeds.json(old))

        // A write that died after it began: part of the next feed is in the temporary file, and
        // the rename that would have made it the feed never happened.
        store.temporary.writeText(TestFeeds.json(TestFeeds.another()).take(5_000))
        assertEquals(old, store.read(), "the temporary file is not the feed")
        assertEquals(old, FeedStore(feedFile).changes.value, "nor is it what a restarted watch app starts with")

        // Not even a whole feed there counts before the rename.
        store.temporary.writeText(TestFeeds.json(TestFeeds.another()))
        assertEquals(old, store.read())
        val neverRenamed = FeedStore(File(directory, "never-renamed.json"))
        neverRenamed.temporary.writeText(TestFeeds.sampleJson)
        assertNull(neverRenamed.read(), "no feed was ever accepted here")
        assertNull(neverRenamed.changes.value)
        neverRenamed.temporary.delete()

        // The next write is not troubled by what the last one left behind, and leaves nothing.
        val next = TestFeeds.another(city = "Pretoria")
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(next)))
        assertEquals(next, store.read())
        assertFalse(store.temporary.exists(), "the temporary file was renamed away, not copied")
        assertEquals(listOf("feed.json"), directory.list().orEmpty().sorted())
    }

    @Test
    fun aReaderNeverSeesAFeedHalfWritten() {
        val store = FeedStore(feedFile)
        val first = TestFeeds.sample
        val second = TestFeeds.another()
        val firstJson = TestFeeds.json(first)
        val secondJson = TestFeeds.json(second)
        store.write(firstJson)

        val writing = AtomicBoolean(true)
        val reads = AtomicInteger()
        val torn = AtomicInteger()
        val readerFailure = AtomicReference<Throwable?>(null)
        val reader = thread {
            try {
                while (writing.get()) {
                    val seen = store.read()
                    reads.incrementAndGet()
                    if (seen != first && seen != second) torn.incrementAndGet()
                }
            } catch (e: Throwable) {
                readerFailure.set(e)
            }
        }
        repeat(400) { store.write(if (it % 2 == 0) secondJson else firstJson) }
        writing.set(false)
        reader.join()

        assertNull(readerFailure.get(), "the reader ran to the end")
        assertTrue(reads.get() > 0, "the reader read while the writes were going on")
        assertEquals(0, torn.get(), "reads that saw no feed, or half of one, out of ${reads.get()}")
    }

    @Test
    fun concurrentWritesLeaveOneWholeFeed() {
        val store = FeedStore(feedFile)
        val first = TestFeeds.sample
        val second = TestFeeds.another()
        val failures = ConcurrentLinkedQueue<Throwable>()

        listOf(TestFeeds.json(first), TestFeeds.json(second)).map { json ->
            thread {
                try {
                    repeat(60) { store.write(json) }
                } catch (e: Throwable) {
                    failures += e
                }
            }
        }.forEach { it.join() }

        assertTrue(failures.isEmpty(), "writers failed: ${failures.toList()}")
        val stored = store.read()
        assertTrue(stored == first || stored == second, "the file holds one of the two feeds, whole")
        assertEquals(listOf("feed.json"), directory.list().orEmpty().sorted())
    }

    @Test
    fun aWriteThatFailsChangesNothingAndLeavesNothingBehind() {
        // The feed's path is taken by a directory with something in it, so the rename cannot happen.
        feedFile.mkdirs()
        File(feedFile, "in-the-way").writeText("x")
        val store = FeedStore(feedFile)

        assertFailsWith<IOException> { store.write(TestFeeds.sampleJson) }

        assertFalse(store.temporary.exists(), "the half-done write is cleaned up")
        assertNull(store.changes.value, "and the feed is not said to have changed")
        assertFalse(store.newerVersionSeen.value)
    }

    @Test
    fun aNoticeThatCannotBeRemovedDoesNotFailAWriteThatStoredTheFeed() {
        // The notice's path is taken by a directory with something in it, so it cannot be deleted.
        val store = FeedStore(feedFile)
        store.newerNotice.mkdirs()
        File(store.newerNotice, "in-the-way").writeText("x")

        val feed = TestFeeds.sample
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(feed)))

        assertEquals(feed, store.read(), "the feed is stored")
        assertEquals(feed, store.changes.value, "and said to be")
    }

    // ---- Telling the complications

    @Test
    fun everyAcceptedWriteIsToldToTheListenerAndNothingElseIs() {
        var told = 0
        val store = FeedStore(feedFile) { told++ }
        val json = TestFeeds.json(TestFeeds.sample)

        assertEquals(FeedWriteResult.ACCEPTED, store.write(json))
        assertEquals(1, told)
        // The same feed again is told too: the phone sent it, the watch is in reach, and the
        // timelines the complications hold are a little older than they were.
        assertEquals(FeedWriteResult.ACCEPTED, store.write(json))
        assertEquals(2, told)
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(TestFeeds.another())))
        assertEquals(3, told)

        // A feed that is turned away changes nothing, so nothing is told.
        assertEquals(FeedWriteResult.MALFORMED, store.write("not a feed"))
        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")))
        assertEquals(3, told)
    }

    @Test
    fun aNewerFeedIsToldToTheNewerListenerAndNothingElseIs() {
        // Plan 3 Task 8: the Tile says "Update Taqwa on your watch" at once, not at its next refresh.
        var newerTold = 0
        var storedTold = 0
        val store = FeedStore(feedFile, onStored = { storedTold++ }, onNewerVersion = { newerTold++ })
        val newer = TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")

        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(newer))
        assertEquals(1, newerTold)
        assertEquals(0, storedTold, "a feed that is turned away is no feed stored")

        // An accepted write and a broken one tell it nothing.
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(TestFeeds.sample)))
        assertEquals(FeedWriteResult.MALFORMED, store.write("not a feed"))
        assertEquals(1, newerTold)
        assertEquals(1, storedTold)

        // A stale delivery is not even read, so it tells nothing either.
        val delivery = WatchDelivery("0123456789abcdef", 5, "fedcba9876543210")
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.sampleJson, delivery))
        assertEquals(FeedWriteResult.STALE, store.write(newer, delivery))
        assertEquals(1, newerTold)

        // The listener is told outside the write's lock: it may read the store it is told about.
        lateinit var reading: FeedStore
        var seen: Boolean? = null
        reading = FeedStore(File(directory, "other.json"), onNewerVersion = { seen = reading.newerVersionSeen.value })
        reading.write(newer)
        assertEquals(true, seen, "the store already says the watch is out of date when it tells")
    }

    @Test
    fun aNewerListenerThatFailsDoesNotFailTheWriteThatReportedIt() {
        val store = FeedStore(feedFile, onNewerVersion = { throw IllegalStateException("the Tile could not be asked") })
        store.write(TestFeeds.json(TestFeeds.sample))

        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")))

        assertTrue(store.newerVersionSeen.value)
        assertEquals(TestFeeds.sample, store.read())
    }

    @Test
    fun theListenerSeesTheFeedAlreadyStored() {
        lateinit var store: FeedStore
        val seen = mutableListOf<Pair<Any?, Any?>>()
        store = FeedStore(feedFile) { seen += store.changes.value to store.read() }
        val feed = TestFeeds.sample

        store.write(TestFeeds.json(feed))

        assertEquals(listOf<Pair<Any?, Any?>>(feed to feed), seen, "stored on disk and in memory before anyone is told")
    }

    @Test
    fun aListenerThatFailsDoesNotFailAWriteThatStoredTheFeed() {
        val store = FeedStore(feedFile) { throw IllegalStateException("the system would not take the request") }
        val feed = TestFeeds.sample

        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(feed)))

        assertEquals(feed, store.read())
        assertEquals(feed, store.changes.value)
    }

    @Test
    fun aWriteThatFailsTellsNobody() {
        feedFile.mkdirs()
        File(feedFile, "in-the-way").writeText("x")
        var told = 0
        val store = FeedStore(feedFile) { told++ }

        assertFailsWith<IOException> { store.write(TestFeeds.sampleJson) }

        assertEquals(0, told)
    }

    @Test
    fun aCorruptFileOnDiskIsNoFeedAndNoCrash() {
        feedFile.writeText("{\"version\":1,\"writtenAt\":17")

        val store = FeedStore(feedFile)

        assertNull(store.read())
        assertNull(store.changes.value)
        assertFalse(store.newerVersionSeen.value)
        val feed = TestFeeds.sample
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(feed)))
        assertEquals(feed, store.read(), "the next feed replaces it")
    }

    // ---- The delivery the phone sent (Ruling 50)

    private val dA = WatchDelivery("0123456789abcdef", 5, "fedcba9876543210")
    private val dB = WatchDelivery("0123456789abcdef", 6, "00112233aabbccdd")

    @Test
    fun aNewFeedReplacesTheDelivery() {
        val store = FeedStore(feedFile)
        assertNull(store.delivery(), "a store nobody has written to holds no delivery")

        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(TestFeeds.sample), dA))
        assertEquals(dA, store.delivery())
        assertEquals(dA.text, store.deliveryFile.readText(), "kept beside the feed, in the one form every echo carries")
        assertEquals(dA, FeedStore(feedFile).delivery(), "and kept for the next process")

        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(TestFeeds.another()), dB))
        assertEquals(dB, store.delivery())

        // A feed stored with none, as plan 1's sample is, never carries the phone's delivery.
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(TestFeeds.another(city = "Pretoria"))))
        assertNull(store.delivery())
        assertFalse(store.deliveryFile.exists())
        assertEquals(listOf("feed.json"), directory.list().orEmpty().sorted(), "nothing else is left behind")
    }

    @Test
    fun aNewerOrBrokenFeedKeepsTheDelivery() {
        val store = FeedStore(feedFile)
        store.write(TestFeeds.json(TestFeeds.sample), dA)

        val newer = TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")
        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(newer, dB))
        assertEquals(dA, store.delivery(), "the delivery stays the stored feed's")
        assertEquals(FeedWriteResult.MALFORMED, store.write("not a feed", dB))
        assertEquals(dA, store.delivery())
        assertEquals(TestFeeds.sample, store.read())
    }

    @Test
    fun aStaleOrDuplicateDeliveryTouchesNothing() {
        val store = FeedStore(feedFile)
        val json = TestFeeds.json(TestFeeds.sample)
        store.write(json, dA)
        var told = 0
        val watched = FeedStore(feedFile) { told++ }

        val stale = listOf(dA, dA.copy(seq = 4), dA.copy(seq = 1, fingerprint = "00112233aabbccdd"))
        for (delivery in stale) {
            // Another feed, and even one this build cannot read: the number says no first.
            assertEquals(FeedWriteResult.STALE, watched.write(TestFeeds.json(TestFeeds.another()), delivery), "$delivery")
            assertEquals(FeedWriteResult.STALE, watched.write(TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2"), delivery))
            assertEquals(FeedWriteResult.STALE, watched.write("not a feed", delivery))
        }
        assertEquals(TestFeeds.sample, watched.read())
        assertEquals(dA, watched.delivery())
        assertEquals(json, feedFile.readText())
        assertFalse(watched.newerVersionSeen.value, "a stale feed is not even read for its version")
        assertFalse(watched.newerNotice.exists())
        assertEquals(0, told, "nothing is redrawn")
    }

    @Test
    fun theStoreFollowsTheContractsSteps() {
        assertEquals(
            listOf(world.taqwa.app.watch.WatchStoreStep.REMOVE_DELIVERY, world.taqwa.app.watch.WatchStoreStep.PLACE_FEED, world.taqwa.app.watch.WatchStoreStep.WRITE_DELIVERY),
            WatchLinkContract.STORE_STEPS,
            "the steps this store is held to",
        )

        // The feed cannot be put in place (the path it is written aside to is taken by a directory),
        // so the write stops after the delivery's removal: the old feed, and no delivery, never the
        // old delivery beside a new feed or the new one beside the old feed.
        val store = FeedStore(feedFile)
        store.write(TestFeeds.json(TestFeeds.sample), dA)
        store.temporary.mkdirs()
        File(store.temporary, "in-the-way").writeText("x")

        assertFailsWith<IOException> { store.write(TestFeeds.json(TestFeeds.another()), dB) }

        assertEquals(TestFeeds.sample, store.read(), "the old feed")
        assertEquals(TestFeeds.sample, store.changes.value)
        assertNull(store.delivery(), "and no delivery: the handshake says nothing, and the phone sends once more")
        assertFalse(store.deliveryFile.exists())

        // The rename itself fails (the feed's path is taken by a directory): the delivery planted
        // there is gone too, and the new one was never written.
        val other = File(directory, "other.json")
        other.mkdirs()
        File(other, "in-the-way").writeText("x")
        val second = FeedStore(other)
        second.deliveryFile.writeText(dA.text)
        assertFailsWith<IOException> { second.write(TestFeeds.json(TestFeeds.another()), dB) }
        assertNull(second.delivery())
        assertFalse(second.deliveryFile.exists())
    }

    @Test
    fun aFailedDeliveryWriteLeavesNoneNeverTheOldOne() {
        val store = FeedStore(feedFile)
        store.write(TestFeeds.json(TestFeeds.sample), dA)
        val partial = File(store.deliveryFile.path + ".tmp")
        partial.mkdirs()
        File(partial, "in-the-way").writeText("x")

        val b = TestFeeds.another()
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.json(b), dB), "the feed is stored: nothing past that fails the write")

        assertEquals(b, store.read())
        assertEquals(b, store.changes.value)
        assertNull(store.delivery(), "none, not dA")
    }

    @Test
    fun aDeliveryFileThatHoldsNoDeliveryIsNone() {
        val store = FeedStore(feedFile)
        store.write(TestFeeds.json(TestFeeds.sample), dA)
        for (text in listOf("", "garbage", dA.text + "\n", "0123456789abcdef/0/fedcba9876543210", "fedcba9876543210")) {
            store.deliveryFile.writeText(text)
            assertNull(store.delivery(), "'$text'")
        }
        store.deliveryFile.delete()
        store.deliveryFile.mkdirs()
        assertNull(store.delivery(), "a directory in its place")
    }

    // ---- Forgetting everything (the debug build's `--ez clear true`, and tests)

    @Test
    fun clearForgetsTheFeedAndTheNotice() {
        val store = FeedStore(feedFile)
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.sampleJson, dA))
        assertEquals(FeedWriteResult.NEWER_VERSION, store.write(TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")))
        // What writes cut short leave behind: each file's temporary twin.
        store.temporary.writeText("half a feed")
        File(store.newerNotice.path + ".tmp").writeText("2")
        File(store.deliveryFile.path + ".tmp").writeText(dB.text)

        store.clear()

        assertNull(store.changes.value)
        assertFalse(store.newerVersionSeen.value)
        assertNull(store.read())
        assertNull(store.delivery())
        assertEquals(emptyList(), directory.listFiles().orEmpty().map { it.name }, "no feed, notice, delivery or temporary file on disk")
        // The next process finds nothing either.
        val restarted = FeedStore(feedFile)
        assertNull(restarted.changes.value)
        assertFalse(restarted.newerVersionSeen.value)
    }

    @Test
    fun clearTellsNobody() {
        var told = 0
        val store = FeedStore(feedFile) { told++ }
        store.write(TestFeeds.sampleJson)
        assertEquals(1, told)

        store.clear()

        assertEquals(1, told, "the debug receiver asks for the redraws itself")
    }

    @Test
    fun aClearedStoreTakesTheNextFeed() {
        val store = FeedStore(feedFile)
        store.write(TestFeeds.sampleJson, dB)
        store.clear()

        // A cleared watch holds no delivery, so a lower number of the same phone is new to it.
        assertEquals(FeedWriteResult.ACCEPTED, store.write(TestFeeds.sampleJson, dA))
        assertEquals(TestFeeds.sample, store.changes.value)
        assertEquals(dA, store.delivery())
    }
}
