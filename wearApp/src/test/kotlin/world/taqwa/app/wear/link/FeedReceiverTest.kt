package world.taqwa.app.wear.link

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.wear.FeedStore
import world.taqwa.app.wear.FeedWriteResult
import world.taqwa.app.wear.TestFeeds

/**
 * The Wear receiver (plan 2 Task 6, Ruling 50): a feed on the feed path is stored only when its
 * delivery moves the watch forward ([WatchLinkContract.stores]), kept beside the delivery it came
 * with, and acknowledged with [WatchLinkContract.echoOf] what the watch then holds. Anything else is
 * stored and said nothing of.
 */
class FeedReceiverTest {

    private val d = WatchDelivery("0123456789abcdef", 5, "fedcba9876543210")
    private lateinit var directory: File
    private lateinit var store: FeedStore

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory("feed-receiver").toFile()
        store = FeedStore(File(directory, "feed.json"))
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun receive(path: String, text: String): Received? = FeedReceiver.onMessage(path, text.encodeToByteArray(), store)

    private fun receive(delivery: WatchDelivery, text: String): Received = assertNotNull(receive(WatchLinkContract.feedPath(delivery), text))

    @Test
    fun aFeedOnTheFeedPathIsStored() {
        val received = receive(d, TestFeeds.sampleJson)

        assertEquals(Received(FeedWriteResult.ACCEPTED, d.text), received)
        assertEquals(TestFeeds.sample, store.read())
        assertEquals(d, store.delivery())
    }

    @Test
    fun aStaleOrDuplicateFeedIsDroppedUnacknowledged() {
        receive(d, TestFeeds.sampleJson)
        val other = TestFeeds.json(TestFeeds.another())

        for (delivery in listOf(d, d.copy(seq = 4))) {
            assertEquals(Received(FeedWriteResult.STALE, null), receive(delivery, other), "${delivery.seq}")
            assertEquals(TestFeeds.sample, store.read(), "the feed is untouched")
            assertEquals(d, store.delivery(), "and so is its delivery")
        }
    }

    @Test
    fun theSameFeedUnderANewerNumberIsStoredAndAcknowledged() {
        receive(d, TestFeeds.sampleJson)
        val six = d.copy(seq = 6)

        assertEquals(Received(FeedWriteResult.ACCEPTED, six.text), receive(six, TestFeeds.sampleJson))
        assertEquals(six, store.delivery(), "the echo names the newer number")
    }

    @Test
    fun anotherInstallsFeedIsStored() {
        receive(d, TestFeeds.sampleJson)
        // Two origins cannot be ordered (the Lead's call 1, a known limit): number 1 of another
        // install is stored over number 5 of this one.
        val theirs = WatchDelivery("aaaaaaaaaaaaaaaa", 1, "1111111111111111")
        val feed = TestFeeds.another()

        assertEquals(Received(FeedWriteResult.ACCEPTED, theirs.text), receive(theirs, TestFeeds.json(feed)))
        assertEquals(feed, store.read())
        assertEquals(theirs, store.delivery())
    }

    @Test
    fun aNewerFeedKeepsTheLastAndSaysUpdate() {
        receive(d, TestFeeds.sampleJson)
        val newer = TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")

        assertEquals(Received(FeedWriteResult.NEWER_VERSION, null), receive(d.copy(seq = 6), newer))
        assertEquals(TestFeeds.sample, store.read())
        assertEquals(d, store.delivery())
        assertTrue(store.newerVersionSeen.value)
    }

    @Test
    fun aBrokenFeedIsIgnored() {
        receive(d, TestFeeds.sampleJson)

        assertEquals(Received(FeedWriteResult.MALFORMED, null), receive(d.copy(seq = 6), TestFeeds.sampleJson.take(100)))
        assertEquals(TestFeeds.sample, store.read())
        assertEquals(d, store.delivery())
        assertFalse(store.newerVersionSeen.value)
    }

    @Test
    fun anotherPathIsNotAFeed() {
        for (path in listOf(WatchLinkContract.REFRESH_PATH, "/taqwa/feedback", "/taqwa/feedback/${d.text}", WatchLinkContract.ACK_PATH)) {
            assertNull(receive(path, TestFeeds.sampleJson), path)
        }
        assertUntouched()
    }

    @Test
    fun aFeedWithoutADeliveryIsNotStored() {
        val paths = listOf(
            WatchLinkContract.FEED_PATH,
            "${WatchLinkContract.FEED_PATH}/",
            "${WatchLinkContract.FEED_PATH}/${d.fingerprint}", // the form before Ruling 50
            "${WatchLinkContract.FEED_PATH}/XYZ",
            "${WatchLinkContract.FEED_PATH}/${d.origin}/0/${d.fingerprint}",
            "${WatchLinkContract.FEED_PATH}/${d.text}/more",
        )
        for (path in paths) {
            assertNull(receive(path, TestFeeds.sampleJson), path)
        }
        assertUntouched()
    }

    @Test
    fun theWatchEchoesThePhonesDeliveryWhateverItsVersion() {
        val sample = Json.parseToJsonElement(TestFeeds.sampleJson).jsonObject

        // A newer phone's: keys this watch does not know, at the top level and in ui.
        val newerPhone = JsonObject(
            sample + ("future" to JsonPrimitive(1)) +
                ("ui" to JsonObject(sample.getValue("ui").jsonObject + ("future" to JsonPrimitive(1)))),
        ).toString()
        // An older phone's: no hijriDayMonth in any day, which this watch fills with its default.
        val olderPhone = JsonObject(
            sample + ("days" to kotlinx.serialization.json.JsonArray(sample.getValue("days").jsonArray.map { JsonObject(it.jsonObject - "hijriDayMonth") })),
        ).toString()
        assertTrue("hijriDayMonth" in TestFeeds.sampleJson && "hijriDayMonth" !in olderPhone)

        for ((delivery, text) in listOf(d to newerPhone, d.copy(seq = 6) to olderPhone)) {
            val received = receive(delivery, text)
            assertEquals(FeedWriteResult.ACCEPTED, received.result)
            assertEquals(delivery.text, received.echo)
            assertEquals(delivery, store.delivery())
            // The case is real: the watch's own re-encoding is another text, so a fingerprint the
            // watch computed would never have named the phone's feed.
            assertNotEquals(text, WatchFeedJson.encode(assertNotNull(store.read())))
        }
    }

    @Test
    fun aNewerOrBrokenFeedIsNotAcknowledged() {
        val newer = TestFeeds.sampleJson.replaceFirst("\"version\":1", "\"version\":2")
        assertEquals(Received(FeedWriteResult.NEWER_VERSION, null), receive(d, newer))
        assertEquals(Received(FeedWriteResult.MALFORMED, null), receive(d.copy(seq = 6), "not a feed"))
        assertNull(store.read())
        assertNull(store.delivery())
    }

    @Test
    fun aFeedTheStoreCannotWriteIsNoAnswer() {
        // The feed's path is taken by a directory: the store throws, and the receiver says nothing.
        val blocked = File(directory, "blocked.json")
        blocked.mkdirs()
        File(blocked, "in-the-way").writeText("x")

        assertNull(FeedReceiver.onMessage(WatchLinkContract.feedPath(d), TestFeeds.sampleJson.encodeToByteArray(), FeedStore(blocked)))
    }

    private fun assertUntouched() {
        assertNull(store.read())
        assertNull(store.delivery())
        assertFalse(store.newerVersionSeen.value)
        assertEquals(emptyList(), directory.list().orEmpty().toList(), "no file was made")
    }
}
