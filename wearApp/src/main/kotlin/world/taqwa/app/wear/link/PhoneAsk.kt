package world.taqwa.app.wear.link

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.wear.FeedStore

/** A Taqwa phone as Play services names it: its node [id], and whether it is directly connected. */
internal data class PhoneNode(val id: String, val nearby: Boolean)

/** The watch's side of the Data Layer, as the handshake uses it. */
internal interface PhoneLink {

    /**
     * The reachable nodes with `taqwa_phone` ([WatchLinkContract.PHONE_CAPABILITY]), or null when
     * this watch has no usable Play services or no Wear API. Any other failure is thrown.
     */
    suspend fun phones(): List<PhoneNode>?

    /** Sends [data] on [path] to the node [nodeId]: queued, or thrown. */
    suspend fun send(nodeId: String, path: String, data: ByteArray)
}

/**
 * [PhoneLink] over Play services, through its public API alone (Ruling 54 item 1): the availability
 * first, then `CapabilityClient` and `MessageClient`. Nothing here can show a dialog, an update
 * prompt or a notification.
 */
internal class PlayPhoneLink(private val context: Context) : PhoneLink {

    override suspend fun phones(): List<PhoneNode>? {
        if (noPlayServices(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context))) return null
        val info = try {
            Wearable.getCapabilityClient(context)
                .getCapability(WatchLinkContract.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
        } catch (e: ApiException) {
            if (noWearApi(e.statusCode)) return null
            throw e
        }
        return info.nodes.map { PhoneNode(it.id, it.isNearby) }
    }

    override suspend fun send(nodeId: String, path: String, data: ByteArray) {
        Wearable.getMessageClient(context).sendMessage(nodeId, path, data).await()
    }
}

/**
 * The watch's handshake (plan 2 Task 6, the Lead's call 1, Ruling 50): when [RefreshPolicy] says
 * it is due, the delivery the watch holds, as the phone sent it, goes on
 * [WatchLinkContract.REFRESH_PATH] to each nearby Taqwa phone, which takes the echo in and sends the
 * current feed unless the watch holds it. [stamp] keeps when the last one went out.
 *
 * Every caller bounds it by [BOUND]: the app's start, its opening, the listener's reconnect and the
 * daily nudge. A handshake that finds no phone, or fails, is not stamped, so the next chance asks
 * again; one that is lost costs at most the day until the next daily one.
 */
internal class PhoneAsk(context: Context, private val store: FeedStore, private val stamp: File = File(context.filesDir, STAMP)) {

    private val link: PhoneLink = PlayPhoneLink(context.applicationContext ?: context)

    /** Shakes hands for [why] at [now] (epoch seconds) when it is due; true when the handshake went out. */
    suspend fun askIfDue(why: AskWhy, now: Long): Boolean = handshake(why, now, store, stamp, link)

    companion object {
        /** The file in the app's files that holds when the last handshake went out, in epoch seconds. */
        const val STAMP = "refresh.asked"

        /** How long a handshake may wait for Play services, wherever it is made. */
        val BOUND: Duration = 10.seconds

        /**
         * What the handshake says: [WatchLinkContract.echoOf] the delivery [store] holds, in UTF-8,
         * when it holds a feed; empty bytes when it holds none. A random number, a counter and a
         * fingerprint: nothing of the place or the person.
         */
        fun payload(store: FeedStore): ByteArray =
            if (store.read() != null) WatchLinkContract.echoOf(store.delivery()).encodeToByteArray() else ByteArray(0)

        /** When the last handshake went out, as [stamp] holds it, or null when there is none to read. */
        fun lastAskedAt(stamp: File): Long? = readStamp(stamp)

        /**
         * The handshake over [link]: [RefreshPolicy.shouldAsk] for [why] with what [store] holds and
         * [stamp]'s time, then the nearby phones, then [payload] to each of them, each in its own
         * `try` so one phone's failure never stops the next. Stamped with [now] only when at least
         * one took it. A cut by the caller's bound passes through, so no further phone is asked.
         */
        internal suspend fun handshake(why: AskWhy, now: Long, store: FeedStore, stamp: File, link: PhoneLink): Boolean {
            val feed = store.read()
            val fromPhone = store.delivery() != null
            if (!RefreshPolicy.shouldAsk(why, feed, fromPhone, now, lastAskedAt(stamp))) {
                feedLog("ASK $why skipped")
                return false
            }
            val phones = try {
                link.phones()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                feedLog("ASK $why failed ${e.javaClass.simpleName}")
                return false
            }
            val nearby = phones.orEmpty().filter { it.nearby }.map { it.id }
            if (nearby.isEmpty()) {
                feedLog("ASK $why no phone")
                return false
            }
            val data = payload(store)
            var sent = false
            for (id in nearby) {
                try {
                    link.send(id, WatchLinkContract.REFRESH_PATH, data)
                    sent = true
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    feedLog("ASK $why not queued ${e.javaClass.simpleName}")
                }
            }
            if (!sent) {
                feedLog("ASK $why failed")
                return false
            }
            // Unstamped, the next chance asks again: one more handshake, never one fewer.
            writeStamp(stamp, now)
            feedLog("ASK $why sent")
            return true
        }
    }
}
