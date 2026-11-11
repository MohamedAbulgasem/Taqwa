package world.taqwa.app.watchfeed

import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson

/**
 * The feed's name, which the phone alone computes and sends beside the feed (the Lead's call 1),
 * as the last part of its delivery (`WatchDelivery.text`, Ruling 50): on Android in the message
 * path after `/taqwa/feed/`, on the iPhone under the context's `delivery` key. It is [fnv1a64Hex]
 * of the UTF-8 of [WatchFeedJson.encode] with [WatchFeed.writtenAt] set to 0, so a feed written
 * again unchanged keeps its name, and one more phrase or one minute moved gives another. One feed
 * always encodes to one text (`WatchFeedJson`'s own promise).
 *
 * The watch stores the name it was sent and echoes it, and never computes one of its own: a watch
 * on another version than its phone decodes and re-encodes a different text, without the keys a
 * newer phone added or with the defaults of fields an older phone never wrote, and its own name
 * would never match. So the name lives here, in `:shared`, and not in `:watchcore`.
 *
 * Throws what [WatchFeedJson.encode] throws, a NaN among it.
 */
internal fun watchFeedFingerprint(feed: WatchFeed): String =
    fnv1a64Hex(WatchFeedJson.encode(feed.copy(writtenAt = 0)).encodeToByteArray())

/**
 * The 64-bit FNV-1a hash of [bytes] (offset basis `0xcbf29ce484222325`, prime `0x100000001b3`),
 * as 16 lowercase hex characters, zeros in front included: the form
 * `WatchLinkContract.isFingerprint` takes. Not a secret and not a check against tampering: only a
 * name that changes when the feed does.
 */
internal fun fnv1a64Hex(bytes: ByteArray): String {
    var hash = FNV_OFFSET_BASIS
    for (byte in bytes) {
        hash = (hash xor byte.toUByte().toULong()) * FNV_PRIME
    }
    return hash.toString(16).padStart(16, '0')
}

private const val FNV_OFFSET_BASIS: ULong = 0xcbf29ce484222325uL
private const val FNV_PRIME: ULong = 0x100000001b3uL
