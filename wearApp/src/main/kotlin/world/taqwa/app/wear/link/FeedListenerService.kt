package world.taqwa.app.wear.link

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.wear.TaqwaWearApp

/**
 * What the phone tells the watch, over the Data Layer (plan 2 Task 6): its feed on
 * `/taqwa/feed/<origin>/<seq>/<fingerprint>`, and `taqwa_phone` changing as the phone comes and
 * goes. Play services binds to it, and only Play services can: the manifest exports it with the two
 * filters `WearableListenerService` is found by, and the library checks that its caller is Google
 * Play services.
 *
 * A feed the store takes ([FeedReceiver]) is acknowledged on [WatchLinkContract.ACK_PATH] to the
 * node it came from, with what the watch now holds, inside [FeedReceiver.ACK_BOUND]; a stale, newer
 * or broken one is not. The phone coming back nearby is a handshake ([AskWhy.PHONE_NEARBY]) inside
 * [PhoneAsk.BOUND]. The callbacks run one at a time on the service's own thread, so neither waits
 * longer than its bound. Each send catches `Exception`, and the store's write its `IOException`
 * ([FeedReceiver]): a lost acknowledgement or handshake costs one more send, or the next
 * handshake. Nothing here can show UI or a notification.
 *
 * Every line it logs names a delivery by its number and fingerprint alone ([logged]), never by its
 * origin, the place or the bearing (Ruling 52 item 4, Ruling 39), and never the path, which holds
 * the origin.
 */
class FeedListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        if (!WatchLinkContract.isFeedPath(path)) return
        if (FeedDrop.takeOne(filesDir)) {
            feedLog("DROPPED")
            return
        }
        val delivery = WatchLinkContract.deliveryIn(path)
        val received = FeedReceiver.onMessage(path, event.data, (application as TaqwaWearApp).feedStore)
        if (received == null) {
            if (delivery == null) feedLog("IGNORED no delivery")
            return
        }
        feedLog("${received.result} ${logged(delivery)}")
        val echo = received.echo ?: return
        if (FeedDrop.holdAck(filesDir)) {
            feedLog("ACK HELD")
            Thread.sleep(FeedDrop.ACK_HOLD.inWholeMilliseconds)
        }
        val sent = try {
            runBlocking {
                withTimeoutOrNull(FeedReceiver.ACK_BOUND) {
                    Wearable.getMessageClient(this@FeedListenerService)
                        .sendMessage(event.sourceNodeId, WatchLinkContract.ACK_PATH, echo.encodeToByteArray())
                        .await()
                }
            } != null
        } catch (_: Exception) {
            false
        }
        feedLog("ACK ${loggedEcho(echo)} ${if (sent) "sent" else "failed"}")
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name != WatchLinkContract.PHONE_CAPABILITY || info.nodes.none { it.isNearby }) return
        try {
            runBlocking {
                withTimeoutOrNull(PhoneAsk.BOUND) {
                    val app = application as TaqwaWearApp
                    PhoneAsk(app, app.feedStore).askIfDue(AskWhy.PHONE_NEARBY, System.currentTimeMillis() / 1000)
                }
            }
        } catch (_: Exception) {
            // The handshake is lost; the next reconnect, opening or daily nudge asks again.
        }
    }
}
