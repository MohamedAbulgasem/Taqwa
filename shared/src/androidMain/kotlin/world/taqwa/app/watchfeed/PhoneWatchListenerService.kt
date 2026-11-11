package world.taqwa.app.watchfeed

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.di.appContainer
import world.taqwa.app.watch.WatchLinkContract
import kotlin.time.Duration

/**
 * What the watch tells the Android phone, over the Data Layer (plan 2 Task 5): its handshake on
 * `/taqwa/refresh`, its acknowledgement on `/taqwa/ack`, and `taqwa_watch` changing as a watch
 * comes and goes. Play services binds to it, and only Play services can: the manifest exports it
 * with the three filters `WearableListenerService` is found by, each on one literal path
 * (`android:path`, Ruling 54 item 2), and the library checks that its caller is Google Play
 * services.
 *
 * Each body waits for the sync at most [WatchFeedSync.CALLER_BOUND] (10 s), even with the sync's
 * worker gone, so no body holds the service's thread longer; and each catches `Exception`, as every
 * call into the watch side does. Nothing here can show UI or a notification.
 */
internal class PhoneWatchListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val data = event.data
        val from = event.sourceNodeId
        bounded { takeWatchMessage(appContainer.watchFeedSync, path, data, from) }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        val name = info.name
        val nodes = info.nodes.map { WatchNode(it.id, it.isNearby) }
        bounded { takeCapabilityChange(appContainer.watchFeedSync, name, nodes) }
    }
}

/**
 * One of the listener's bodies on the service's thread: [body] waits for the sync at most [bound]
 * ([WatchFeedSync.CALLER_BOUND] in the service), even with the sync's worker gone, and any
 * `Exception` it throws (a container that cannot be built among them) costs the watch this message
 * alone, since its next handshake asks again. So no body holds the thread longer, and none throws.
 */
internal fun bounded(bound: Duration = WatchFeedSync.CALLER_BOUND, body: suspend () -> Unit) {
    try {
        runBlocking { withTimeoutOrNull(bound) { body() } }
    } catch (e: Exception) {
        // The message is lost; the watch's next handshake asks again.
    }
}

/**
 * One message from the watch [from] names (its node id), served by [sync]:
 * - the handshake ([WatchLinkContract.REFRESH_PATH]): [data] is the watch's echo in UTF-8, taken in
 *   for that node (Ruling 52 item 9), then the current feed sent unless the record holds it;
 * - the acknowledgement ([WatchLinkContract.ACK_PATH]): the same echo, of the delivery the watch now
 *   holds, taken in for that node. Its log line names the delivery by number and fingerprint
 *   alone, never by the install's origin (Ruling 52 item 4);
 * - anything else is no message of ours. The filters' literal paths wake the service for the two
 *   alone; this rule holds whatever reaches it.
 */
internal suspend fun takeWatchMessage(sync: WatchFeedSync, path: String, data: ByteArray, from: String) {
    when (path) {
        WatchLinkContract.REFRESH_PATH -> sync.sync(WatchSyncReason.WATCH_ASKED, have = data.decodeToString(), from = from)
        WatchLinkContract.ACK_PATH -> {
            val echo = data.decodeToString()
            println("TaqwaWatch ACK ${loggedDelivery(echo)}")
            sync.acknowledge(echo, from = from)
        }
    }
}

/**
 * [name]'s nodes changed: for `taqwa_watch` with at least one of [nodes] nearby, a watch may be back
 * (every Bluetooth reconnect), which the record damps as it does a reschedule (the Lead's call 1).
 * A watch that holds nothing the phone knows of says so in its own handshake.
 */
internal suspend fun takeCapabilityChange(sync: WatchFeedSync, name: String, nodes: Collection<WatchNode>) {
    if (name == WatchLinkContract.WATCH_CAPABILITY && nodes.any { it.nearby }) {
        sync.sync(WatchSyncReason.WATCH_APPEARED)
    }
}
