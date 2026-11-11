package world.taqwa.app.wear.link

import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.wear.FeedStore
import world.taqwa.app.wear.FeedWriteResult

/**
 * What the watch made of one message on the feed path: the store's [result], and the [echo] its
 * acknowledgement carries, [WatchLinkContract.echoOf] what it now holds, or null when nothing is
 * to be acknowledged.
 */
internal data class Received(val result: FeedWriteResult, val echo: String?)

/**
 * The Wear receiver's rule (plan 2 Task 6, Ruling 50), apart from Play services so that the JVM
 * tests hold it: a message on the feed path is a phone's feed only when its path carries a delivery
 * ([WatchLinkContract.deliveryIn]), and it is stored only when that delivery moves the watch
 * forward ([FeedStore.write], which asks [WatchLinkContract.stores]). Only a feed that was stored is
 * acknowledged, with what the watch then holds: a stale, a newer or a broken feed never is.
 */
internal object FeedReceiver {

    /**
     * How long the listener's acknowledgement may wait for Play services: a lost one costs only one
     * more send, or the next handshake.
     */
    val ACK_BOUND: Duration = 5.seconds

    /**
     * Stores the feed [data] (its UTF-8 JSON) that came on [path], and says what became of it. Null
     * for a path that is no feed's ([WatchLinkContract.isFeedPath]), for a feed path that carries no
     * delivery (no phone's: nothing stored, nothing acknowledged), and for a store that cannot be
     * written ([IOException], logged), which keeps the feed it held.
     */
    fun onMessage(path: String, data: ByteArray, store: FeedStore): Received? {
        if (!WatchLinkContract.isFeedPath(path)) return null
        val delivery = WatchLinkContract.deliveryIn(path) ?: return null
        val result = try {
            store.write(data.decodeToString(), delivery)
        } catch (e: IOException) {
            feedLog("NOT STORED ${logged(delivery)} ${e.javaClass.simpleName}")
            return null
        }
        val echo = if (result == FeedWriteResult.ACCEPTED) WatchLinkContract.echoOf(store.delivery()) else null
        return Received(result, echo)
    }
}

/**
 * One line of the watch's link in the log, as `TaqwaWearFeed <line>`, on standard output as the
 * phone's `TaqwaWatch` lines are, so Task 7 reads both with `logcat -s TaqwaWearFeed System.out`.
 * A release line names a delivery by [logged] or [loggedEcho] alone, never by its origin, and
 * never names the place or the bearing (Ruling 52 item 4, Ruling 39).
 */
internal fun feedLog(line: String) = println("TaqwaWearFeed $line")

/** [delivery] as a release log line names it: `<seq>/<fingerprint>`, never its origin; `none` for none. */
internal fun logged(delivery: WatchDelivery?): String = delivery?.let { "${it.seq}/${it.fingerprint}" } ?: "none"

/** An echo as a release log line names it: its delivery by [logged], `none` for an echo of nothing, `unreadable` otherwise. */
internal fun loggedEcho(echo: String): String =
    if (echo.isEmpty()) "none" else WatchLinkContract.deliveryOf(echo)?.let(::logged) ?: "unreadable"
