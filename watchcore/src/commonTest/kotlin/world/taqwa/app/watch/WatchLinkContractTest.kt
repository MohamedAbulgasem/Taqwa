package world.taqwa.app.watch

import world.taqwa.app.watch.WatchStoreStep.PLACE_FEED
import world.taqwa.app.watch.WatchStoreStep.REMOVE_DELIVERY
import world.taqwa.app.watch.WatchStoreStep.WRITE_DELIVERY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The link's contract, which the phones and the watches read alike: the feed within every link's
 * limit with room for its envelope, the fingerprint's and the origin's form, the delivery each feed
 * travels with (Ruling 50), and the receivers' rules: what a watch stores, what it echoes, and the
 * order it stores in.
 */
class WatchLinkContractTest {

    private val origin = "0123456789abcdef"
    private val fp = "fedcba9876543210"
    private val other = "00000000000000ff"

    @Test
    fun theFeedBudgetFitsEveryLink() {
        val sent = WatchLinkContract.FEED_BUDGET_BYTES + WatchLinkContract.ENVELOPE_BYTES
        assertTrue(sent <= WatchLinkContract.MESSAGE_LIMIT_BYTES, "the Data Layer's message")
        assertTrue(sent <= WatchLinkContract.USER_INFO_LIMIT_BYTES, "WatchConnectivity's smallest figure")
        assertTrue(sent <= WatchLinkContract.CONTEXT_LIMIT_BYTES, "the application context")
    }

    @Test
    fun aFingerprintIsSixteenLowercaseHex() {
        assertTrue(WatchLinkContract.isFingerprint("0123456789abcdef"))
        assertTrue(WatchLinkContract.isFingerprint("fedcba9876543210"))
        for (text in listOf("", "0123456789abcde", "0123456789abcdef0", "0123456789ABCDEF", "0123456789abcdeF", "0123456789abcdeg")) {
            assertFalse(WatchLinkContract.isFingerprint(text), "\"$text\"")
        }
    }

    @Test
    fun anOriginIsSixteenLowercaseHexToo() {
        assertTrue(WatchLinkContract.isOrigin(origin))
        for (text in listOf("", "0123456789abcde", "0123456789abcdef0", "0123456789ABCDEF", "0123456789abcdeg", "0123456789abcde/")) {
            assertFalse(WatchLinkContract.isOrigin(text), "\"$text\"")
        }
    }

    @Test
    fun aDeliveryTravelsAsItsOriginItsNumberAndItsFingerprint() {
        val delivery = WatchDelivery(origin, 7, fp)
        assertEquals("$origin/7/$fp", delivery.text)
        assertEquals(delivery, WatchLinkContract.deliveryOf(delivery.text))
        val far = WatchDelivery(origin, Long.MAX_VALUE, fp)
        assertEquals(far, WatchLinkContract.deliveryOf(far.text), "every positive number a Long holds")
    }

    @Test
    fun anythingElseIsNoDelivery() {
        val refused = listOf(
            "",
            fp, // the fingerprint alone, as before Ruling 50
            "$origin/7",
            "$origin/0/$fp", // numbers start at 1
            "$origin/-1/$fp",
            "$origin/07/$fp", // one number has one form
            "$origin/+7/$fp",
            "$origin/ 7/$fp",
            "$origin//$fp",
            "$origin/7/$fp/x",
            "$origin/7/$fp/",
            "/$origin/7/$fp",
            "${origin.uppercase()}/7/$fp",
            "$origin/7/${fp.uppercase()}",
            "$origin/9223372036854775808/$fp", // one past Long.MAX_VALUE
            "$origin/99999999999999999999/$fp",
        )
        for (text in refused) assertNull(WatchLinkContract.deliveryOf(text), "\"$text\"")
    }

    @Test
    fun aDeliveryIsAlwaysWellFormed() {
        assertFailsWith<IllegalArgumentException> { WatchDelivery("XYZ", 1, fp) }
        assertFailsWith<IllegalArgumentException> { WatchDelivery(origin, 0, fp) }
        assertFailsWith<IllegalArgumentException> { WatchDelivery(origin, 1, "XYZ") }
    }

    @Test
    fun theFeedPathCarriesTheDelivery() {
        val delivery = WatchDelivery(origin, 42, fp)
        assertEquals("/taqwa/feed/$origin/42/$fp", WatchLinkContract.feedPath(delivery))
        assertEquals(delivery, WatchLinkContract.deliveryIn(WatchLinkContract.feedPath(delivery)))
        val notDeliveries = listOf(
            "/taqwa/feed",
            "/taqwa/feed/",
            "/taqwa/feed/$fp", // a feed path before Ruling 50: no delivery, so no feed
            "/taqwa/feed/$origin/42/$fp/x",
            "/taqwa/feedback/$origin/42/$fp",
            "/taqwa/refresh/$origin/42/$fp",
        )
        for (path in notDeliveries) assertNull(WatchLinkContract.deliveryIn(path), path)
        assertTrue(WatchLinkContract.isFeedPath("/taqwa/feed"))
        assertTrue(WatchLinkContract.isFeedPath(WatchLinkContract.feedPath(delivery)))
        assertFalse(WatchLinkContract.isFeedPath("/taqwa/feedback"))
        assertFalse(WatchLinkContract.isFeedPath("/taqwa/refresh"))
        assertFalse(WatchLinkContract.isFeedPath("/taqwa/ack"))
    }

    /**
     * The receivers' rule (Ruling 50 item 2): a watch stores an arriving feed only when its origin
     * differs from the one it holds, or its number is greater. So what it holds only moves
     * forward, whatever order the link delivers in. A greater number is stored even for the very
     * feed it holds, so that its echo names the newer number (only the redraw may be skipped).
     */
    @Test
    fun aWatchStoresOnlyWhatMovesItForward() {
        val held = WatchDelivery(origin, 5, fp)
        assertTrue(WatchLinkContract.stores(held = null, arriving = held), "a watch holding nothing")
        assertTrue(WatchLinkContract.stores(held, WatchDelivery(origin, 6, other)))
        assertTrue(WatchLinkContract.stores(held, WatchDelivery(origin, 6, fp)), "the same feed under a newer number")
        assertFalse(WatchLinkContract.stores(held, WatchDelivery(origin, 5, other)), "the same number: a duplicate, or stale")
        assertFalse(WatchLinkContract.stores(held, WatchDelivery(origin, 5, fp)))
        assertFalse(WatchLinkContract.stores(held, WatchDelivery(origin, 4, other)), "an older one, late")
        assertTrue(WatchLinkContract.stores(held, WatchDelivery("1111111111111111", 1, other)), "another install's")
    }

    /**
     * What a watch says back, in its acknowledgement and its handshake alike: the delivery it holds,
     * or nothing.
     */
    @Test
    fun theEchoIsTheDeliveryHeldOrNothing() {
        val held = WatchDelivery(origin, 5, fp)
        assertEquals(held.text, WatchLinkContract.echoOf(held))
        assertEquals(held, WatchLinkContract.deliveryOf(WatchLinkContract.echoOf(held)))
        assertEquals("", WatchLinkContract.echoOf(null))
        assertNull(WatchLinkContract.deliveryOf(WatchLinkContract.echoOf(null)))
    }

    /**
     * The order a receiver stores in (Ruling 50): the delivery it holds is removed before the feed
     * is put in place, and the arriving one written only after. A receiver stopped after any step
     * (a crash, or a write that fails) then echoes nothing or the feed it holds, never a delivery
     * that names a feed it does not hold. Either order the other way round leaves a step after
     * which it does, which is a wrong UNCHANGED at the phone.
     */
    @Test
    fun aDeliveryIsWrittenAfterItsFeedAndRemovedBefore() {
        assertEquals(listOf(REMOVE_DELIVERY, PLACE_FEED, WRITE_DELIVERY), WatchLinkContract.STORE_STEPS)
        assertEquals(emptyList(), namesAFeedNotHeld(WatchLinkContract.STORE_STEPS))
        assertEquals(1, namesAFeedNotHeld(listOf(WRITE_DELIVERY, REMOVE_DELIVERY, PLACE_FEED)).size, "written first")
        assertEquals(1, namesAFeedNotHeld(listOf(PLACE_FEED, REMOVE_DELIVERY, WRITE_DELIVERY)).size, "removed after the feed")
        assertEquals(1, namesAFeedNotHeld(listOf(REMOVE_DELIVERY, WRITE_DELIVERY, PLACE_FEED)).size, "written before the feed")
    }

    /**
     * The steps of [order] after which a receiver that held feed X under delivery 5 and is storing
     * Y under delivery 6 would echo a delivery naming a feed it does not hold.
     */
    private fun namesAFeedNotHeld(order: List<WatchStoreStep>): List<Int> {
        val old = WatchDelivery(origin, 5, other)
        val new = WatchDelivery(origin, 6, fp)
        var feed = old.fingerprint
        var delivery: WatchDelivery? = old
        val wrong = mutableListOf<Int>()
        order.forEachIndexed { done, step ->
            when (step) {
                REMOVE_DELIVERY -> delivery = null
                PLACE_FEED -> feed = new.fingerprint
                WRITE_DELIVERY -> delivery = new
            }
            // Stopped here: the echo is the delivery held, or nothing.
            val echoed = WatchLinkContract.deliveryOf(WatchLinkContract.echoOf(delivery))
            if (echoed != null && echoed.fingerprint != feed) wrong += done
        }
        return wrong
    }

    @Test
    fun thePathsAndNamesAreTheContracts() {
        assertEquals("/taqwa/feed", WatchLinkContract.FEED_PATH)
        assertEquals("/taqwa/refresh", WatchLinkContract.REFRESH_PATH)
        assertEquals("/taqwa/ack", WatchLinkContract.ACK_PATH)
        assertEquals("taqwa_watch", WatchLinkContract.WATCH_CAPABILITY)
        assertEquals("taqwa_phone", WatchLinkContract.PHONE_CAPABILITY)
        assertEquals("feed", WatchLinkContract.CONTEXT_FEED_KEY)
        assertEquals("delivery", WatchLinkContract.CONTEXT_DELIVERY_KEY)
        assertEquals("refresh", WatchLinkContract.REFRESH_MESSAGE_KEY)
        assertEquals("queued", WatchLinkContract.REFRESH_REPLY_KEY)
    }

    @Test
    fun theFacesCapabilityAndLinkAreTheContracts() {
        // Plan 5 Task 5: the watch says it has a face to add while it has one ("taqwa_face_addable"),
        // and the phone's row opens the watch's face screen by its link.
        assertEquals("taqwa_face_addable", WatchLinkContract.FACE_CAPABILITY)
        assertEquals("taqwa://face", WatchLinkContract.FACE_URI)
    }
}
