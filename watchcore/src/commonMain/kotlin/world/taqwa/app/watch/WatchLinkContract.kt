package world.taqwa.app.watch

/**
 * One feed as a phone delivered it (Ruling 50): [origin], the phone install that sent it (a random
 * number made once per install), [seq], its delivery number, strictly increasing per origin, and
 * [fingerprint], the feed's name. It travels beside the feed, never inside it, as [text]; the
 * watch stores it beside the feed and echoes it, in its acknowledgement and its handshake.
 *
 * A fingerprint names WHAT a feed is, never WHEN it was sent. The number does: a watch stores a
 * feed only when it moves it forward ([WatchLinkContract.stores]), so an old message, a duplicate
 * or a late one is told from a new one, and the phone answers `UNCHANGED` only once the watch has
 * echoed the latest delivery.
 *
 * Always well formed: [origin] and [fingerprint] are 16 lowercase hex characters
 * ([WatchLinkContract.isOrigin], [WatchLinkContract.isFingerprint]) and [seq] is at least 1.
 */
data class WatchDelivery(val origin: String, val seq: Long, val fingerprint: String) {
    init {
        require(WatchLinkContract.isOrigin(origin)) { "not an origin: $origin" }
        require(seq >= 1) { "not a delivery number: $seq" }
        require(WatchLinkContract.isFingerprint(fingerprint)) { "not a fingerprint: $fingerprint" }
    }

    /** `origin/seq/fingerprint`, the number in decimal: the one form every echo and path carries. */
    val text: String get() = "$origin/$seq/$fingerprint"
}

/**
 * The steps a receiver takes to store a feed it [WatchLinkContract.stores], in the order
 * [WatchLinkContract.STORE_STEPS] alone (Ruling 50).
 */
enum class WatchStoreStep {
    /** The delivery the watch holds is removed, so for a moment it holds none. */
    REMOVE_DELIVERY,

    /** The arriving feed is put in place (written aside, then renamed over the old one). */
    PLACE_FEED,

    /** The arriving delivery is written beside it, the same way. */
    WRITE_DELIVERY,
}

/**
 * The link between a phone and its watch, as both ends read it: the paths, the capability names,
 * the context keys, the fingerprint's and the origin's form, the delivery each feed travels with,
 * the receivers' rules, each link's size limit and the feed's byte budget (plan 2, Global
 * Constraints, and Ruling 50). Plans 3 and 4 read the whole link here.
 *
 * **Every feed travels with its delivery** ([WatchDelivery]): the sending install's origin, a
 * number the phone writes down before it calls the link, so it is never used for another feed, and
 * the feed's fingerprint. The watch stores a feed only when [stores] says so, and says back what it
 * holds as [echoOf]: the delivery, or nothing. It stores in [STORE_STEPS]'s order alone.
 *
 * **Android, the Wear Data Layer.** The feed goes as one `MessageClient.sendMessage` to each node
 * that has [WATCH_CAPABILITY] and is directly connected (`Node.isNearby`) when the send is made,
 * asked again at the send, on the path [feedPath] with the feed's UTF-8 JSON as the data. Never
 * `DataClient`, `putDataItem`, a channel or an asset: a data item syncs to every node, Google's
 * cloud node included, and cannot be addressed to one. Google's own words on what that buys:
 * - `MessageClient` is "Bluetooth preferred, but can use Wi-Fi if it's the only type of connection
 *   available", has "No persistence and no retry", and does not take data larger than 100 KB;
 * - "A message is considered successful if it has been queued for delivery": a success is not a
 *   delivery, and no order is documented, so the watch acknowledges each feed it stores, on
 *   [ACK_PATH], with [echoOf] what it now holds as the payload, in UTF-8;
 * - "Assume that data transmitted using Data Layer may at some point use Google-owned servers",
 *   where "All data transferred through Google Cloud is end-to-end encrypted": the nearby-only
 *   send keeps the normal case on Bluetooth, and Ruling 39 accepts the rest.
 *
 * The watch shakes hands on [REFRESH_PATH], only to nearby nodes with [PHONE_CAPABILITY]: its
 * payload, in UTF-8, is [echoOf] what it holds.
 *
 * **iPhone, WatchConnectivity.** The feed goes as `updateApplicationContext` with the feed's
 * UTF-8 bytes as `Data` under [CONTEXT_FEED_KEY] and its delivery's [WatchDelivery.text] as a
 * string under [CONTEXT_DELIVERY_KEY]: the whole document, the latest winning. A property list
 * keeps `Data` as its own bytes, where a `String` holding any non-ASCII character would be stored
 * in UTF-16 at twice its size. Apple documents no WatchConnectivity size, only the error
 * `payloadTooLarge`, and no API to keep a transfer on Bluetooth: "Wi-Fi or cellular can be used
 * instead" (Apple Platform Security), between keys only the pair holds. On `payloadTooLarge` the
 * same feed goes once more without its phrases, under the next number. The watch shakes hands with
 * `sendMessage([REFRESH_MESSAGE_KEY: <echoOf what it holds>])`, and the phone replies
 * `[REFRESH_REPLY_KEY: Bool]`. The iPhone's watch does not acknowledge: the system delivers the
 * latest context it was handed.
 *
 * **The fingerprint** names a feed: 16 lowercase hex characters ([isFingerprint]), which the phone
 * alone computes (plan 2, Task 4). The watch stores it and echoes it unchanged, and never computes
 * one of its own: a watch on another version than its phone decodes and re-encodes a different
 * text.
 *
 * Neither the acknowledgement nor the handshake says anything about the place or the person: an
 * origin is a random number and a delivery number a counter. The code spaces each answer is read
 * in are not here: each side reads its library's own constants.
 */
object WatchLinkContract {

    /** The path a feed is sent on, with its delivery after it ([feedPath]). */
    const val FEED_PATH = "/taqwa/feed"

    /** The watch's handshake, which asks for the current feed. */
    const val REFRESH_PATH = "/taqwa/refresh"

    /** The watch's acknowledgement of a feed it has stored. */
    const val ACK_PATH = "/taqwa/ack"

    /** Declared by Taqwa's watch app alone (the same package and signing key as the phone's). */
    const val WATCH_CAPABILITY = "taqwa_watch"

    /**
     * Declared by the Galaxy watch app while it has the Taqwa face to offer: not the watch's face,
     * and its one activation unspent (plan 5 Task 5, the Lead's call 5). The phone shows "Add the
     * Taqwa face" while a watch declares it.
     */
    const val FACE_CAPABILITY = "taqwa_face_addable"

    /** The watch's face screen, which the phone's "Add the Taqwa face" row opens on the watch. */
    const val FACE_URI = "taqwa://face"

    /** Declared by Taqwa's phone app, so the watch shakes hands only with it. */
    const val PHONE_CAPABILITY = "taqwa_phone"

    /** The application context's key for the feed's UTF-8 bytes. */
    const val CONTEXT_FEED_KEY = "feed"

    /** The application context's key for the feed's delivery, as [WatchDelivery.text]. */
    const val CONTEXT_DELIVERY_KEY = "delivery"

    /** The handshake message's key, holding [echoOf] what the watch holds. */
    const val REFRESH_MESSAGE_KEY = "refresh"

    /** The phone's reply to the handshake: whether the watch has, or is being sent, the current feed. */
    const val REFRESH_REPLY_KEY = "queued"

    /**
     * The Data Layer's message limit. Google: `sendMessage`'s data is "Generally not larger than
     * 100k" (the `MessageClient` reference), and `MessageClient` does not support data larger than
     * 100 KB (the Data Layer's messages page).
     */
    const val MESSAGE_LIMIT_BYTES = 100_000

    /**
     * WatchConnectivity's smallest figure, for `sendMessage` and `transferUserInfo`. Apple
     * documents none: this is the framework's own constant as reported in 2016 and again in 2022.
     * Plan 4 measures it on the paired watch.
     */
    const val USER_INFO_LIMIT_BYTES = 65_536

    /**
     * `updateApplicationContext`'s figure, undocumented in the same way and reported with the one
     * above. Plan 4 measures it on the paired watch.
     */
    const val CONTEXT_LIMIT_BYTES = 262_144

    /** Room for what goes around the feed: the message's path, or the property list's dictionary. */
    const val ENVELOPE_BYTES = 1_024

    /**
     * The encoded feed's UTF-8 bytes, at most. With [ENVELOPE_BYTES] it sits under every limit
     * above, so even a later move to `transferUserInfo` would fit. The times always go: a month in
     * any language is at most 40,000 bytes. The user's phrases go, in order, while they fit.
     */
    const val FEED_BUDGET_BYTES = 60_000

    /**
     * The order a receiver stores a feed in: the delivery it holds removed before the feed is put
     * in place, the arriving one written after. Stopped after any step, by a crash or by a write
     * that fails, the watch then echoes nothing or the feed it holds, never a delivery naming a
     * feed it does not hold: the phone may send once more, and never answers `UNCHANGED` for a
     * feed the watch lacks. A delivery whose write fails leaves none.
     */
    val STORE_STEPS: List<WatchStoreStep> =
        listOf(WatchStoreStep.REMOVE_DELIVERY, WatchStoreStep.PLACE_FEED, WatchStoreStep.WRITE_DELIVERY)

    private const val NAME_LENGTH = 16

    private fun isName(text: String): Boolean =
        text.length == NAME_LENGTH && text.all { it in '0'..'9' || it in 'a'..'f' }

    /** Exactly 16 characters of `0-9a-f`: the only form the watch stores or echoes. */
    fun isFingerprint(text: String): Boolean = isName(text)

    /** Exactly 16 characters of `0-9a-f`, as a fingerprint: a phone install's random number. */
    fun isOrigin(text: String): Boolean = isName(text)

    /**
     * The delivery [text] holds as [WatchDelivery.text]: an origin, a number from 1 in its one
     * decimal form (no sign, no leading zero, within a `Long`; Swift reads it as `Int64`, since
     * watchOS's `Int` can be 32 bits), and a fingerprint, joined by `/`. Null for anything else,
     * the empty echo of a watch that holds nothing among it.
     */
    fun deliveryOf(text: String): WatchDelivery? {
        val parts = text.split('/')
        if (parts.size != 3) return null
        val (origin, number, fingerprint) = parts
        val seq = number.toLongOrNull()?.takeIf { it >= 1 && it.toString() == number } ?: return null
        if (!isOrigin(origin) || !isFingerprint(fingerprint)) return null
        return WatchDelivery(origin, seq, fingerprint)
    }

    /** The path the Android phone sends a feed on: [FEED_PATH], then its [delivery]'s text. */
    fun feedPath(delivery: WatchDelivery): String = "$FEED_PATH/${delivery.text}"

    /**
     * Whether [path] is a feed's: [FEED_PATH] itself or anything under it, never another path the
     * listener's `pathPrefix` also matches, such as `/taqwa/feedback`.
     */
    fun isFeedPath(path: String): Boolean = path == FEED_PATH || path.startsWith("$FEED_PATH/")

    /**
     * The delivery [path] carries after [FEED_PATH], or null when it carries none. A feed that
     * comes without one is no phone's: the watch neither stores nor acknowledges it.
     */
    fun deliveryIn(path: String): WatchDelivery? =
        if (path.startsWith("$FEED_PATH/")) deliveryOf(path.removePrefix("$FEED_PATH/")) else null

    /**
     * The receivers' rule (Ruling 50 item 2): a watch that holds [held] stores [arriving] only when
     * its origin differs, or its number is greater. Anything else is stale or a duplicate, and is
     * dropped unstored and unacknowledged. So what the watch holds only moves forward, whatever
     * order the link delivers in. A greater number is stored even for the very feed it holds, its
     * delivery written anew, so that the watch's echo names it; only the redraw may be skipped.
     *
     * Two origins cannot be ordered: a straggler of an earlier install, delivered after the new
     * one's feed, replaces it until the watch's acknowledgement or next handshake reaches the
     * phone, which then sends the current feed (plan 2, call 1's known limit).
     */
    fun stores(held: WatchDelivery?, arriving: WatchDelivery): Boolean =
        held == null || arriving.origin != held.origin || arriving.seq > held.seq

    /**
     * What a watch says back, in its acknowledgement and in its handshake alike: the delivery it
     * holds, or "" when it holds none (no feed, or one that came without a delivery, such as the
     * debug sample).
     */
    fun echoOf(held: WatchDelivery?): String = held?.text.orEmpty()
}
