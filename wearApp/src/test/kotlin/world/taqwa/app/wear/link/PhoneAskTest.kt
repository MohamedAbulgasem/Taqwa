package world.taqwa.app.wear.link

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.wear.FeedStore
import world.taqwa.app.wear.TestFeeds

/**
 * The watch's handshake (plan 2 Task 6, the Lead's call 1, Ruling 50): it says back the delivery it
 * holds, as the phone sent it, or nothing; it goes to nearby Taqwa phones alone; and it is stamped
 * only when it went out, so a handshake that found no phone is tried again at the next chance.
 */
class PhoneAskTest {

    private val d = WatchDelivery("0123456789abcdef", 5, "fedcba9876543210")
    private lateinit var directory: File
    private lateinit var store: FeedStore
    private lateinit var stamp: File

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory("phone-ask").toFile()
        store = FeedStore(File(directory, "feed.json"))
        stamp = File(directory, PhoneAsk.STAMP)
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun theHandshakeCarriesTheStoredDelivery() {
        assertContentEquals(ByteArray(0), PhoneAsk.payload(store), "no feed: nothing")

        store.write(TestFeeds.sampleJson)
        assertContentEquals(ByteArray(0), PhoneAsk.payload(store), "the sample, stored without a delivery: nothing")

        store.write(TestFeeds.sampleJson, d)
        val payload = PhoneAsk.payload(store).decodeToString()
        assertEquals(d.text, payload)
        assertEquals(d, WatchLinkContract.deliveryOf(payload))
    }

    @Test
    fun theStampIsTheLastAskOrNothing() {
        assertNull(PhoneAsk.lastAskedAt(stamp), "never asked")
        stamp.writeText("1792310400")
        assertEquals(1_792_310_400L, PhoneAsk.lastAskedAt(stamp))
        stamp.writeText("garbage")
        assertNull(PhoneAsk.lastAskedAt(stamp))
    }

    /** A link to the phones as Play services answers it, which records what was sent where. */
    private class FakePhones(var phones: List<PhoneNode>?, var fails: Set<String> = emptySet()) : PhoneLink {
        var queries = 0
        val sent = mutableListOf<Triple<String, String, String>>()

        override suspend fun phones(): List<PhoneNode>? {
            queries++
            return phones
        }

        override suspend fun send(nodeId: String, path: String, data: ByteArray) {
            if (nodeId in fails) throw IllegalStateException("the message could not be queued")
            sent += Triple(nodeId, path, data.decodeToString())
        }
    }

    @Test
    fun aHandshakeGoesToNearbyPhonesAloneAndIsStampedOnlyWhenItWentOut() = runTest {
        store.write(TestFeeds.sampleJson, d)
        val now = TestFeeds.sample.writtenAt + 3_600L

        // No Play services or no Wear API (null), no phone, or a phone reached only through the cloud.
        for (phones in listOf(null, emptyList(), listOf(PhoneNode("cloud", nearby = false)))) {
            val link = FakePhones(phones)
            assertFalse(PhoneAsk.handshake(AskWhy.PHONE_NEARBY, now, store, stamp, link), "$phones")
            assertEquals(emptyList(), link.sent)
            assertFalse(stamp.exists(), "$phones: nothing went out, so the next chance asks again")
        }

        val link = FakePhones(listOf(PhoneNode("phone", nearby = true), PhoneNode("cloud", nearby = false)))
        assertTrue(PhoneAsk.handshake(AskWhy.PHONE_NEARBY, now, store, stamp, link))
        assertEquals(listOf(Triple("phone", WatchLinkContract.REFRESH_PATH, d.text)), link.sent)
        assertEquals(now, PhoneAsk.lastAskedAt(stamp))

        // Thirty seconds later the policy holds it back, without asking Play services anything.
        val later = FakePhones(link.phones)
        assertFalse(PhoneAsk.handshake(AskWhy.PHONE_NEARBY, now + 30, store, stamp, later))
        assertEquals(0, later.queries)
        assertEquals(now, PhoneAsk.lastAskedAt(stamp))
    }

    @Test
    fun aHandshakeThatReachesNoPhoneIsNotStamped() = runTest {
        val now = TestFeeds.sample.writtenAt
        val two = listOf(PhoneNode("a", nearby = true), PhoneNode("b", nearby = true))

        val failing = FakePhones(two, fails = setOf("a", "b"))
        assertFalse(PhoneAsk.handshake(AskWhy.DAILY, now, store, stamp, failing))
        assertFalse(stamp.exists())

        val throwing = object : PhoneLink {
            override suspend fun phones(): List<PhoneNode> = throw TaskCancelled()
            override suspend fun send(nodeId: String, path: String, data: ByteArray) = error("never sent")
        }
        assertFalse(PhoneAsk.handshake(AskWhy.DAILY, now, store, stamp, throwing), "a cancelled query is a failure, asked again later")
        assertFalse(stamp.exists())

        // One phone took it: the handshake went out, and the one that threw did not stop the next.
        val one = FakePhones(two, fails = setOf("a"))
        assertTrue(PhoneAsk.handshake(AskWhy.DAILY, now, store, stamp, one))
        assertEquals(listOf(Triple("b", WatchLinkContract.REFRESH_PATH, "")), one.sent, "a watch that holds nothing says so")
        assertEquals(now, PhoneAsk.lastAskedAt(stamp))
    }
}
