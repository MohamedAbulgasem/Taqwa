package world.taqwa.app.watchfeed

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import world.taqwa.app.settings.appContext
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchLinkContract
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** A Data Layer node with Taqwa's watch app: its id, and whether it is directly connected. */
internal data class WatchNode(val id: String, val nearby: Boolean)

/**
 * The ids of [nodes] that are directly connected (`Node.isNearby`: "this node is directly connected
 * to this device. For example, when they are connected via Bluetooth"). Only those are sent the
 * feed, so the normal case stays on the direct link (Ruling 39).
 */
internal fun directlyConnected(nodes: List<WatchNode>): List<String> = nodes.filter { it.nearby }.map { it.id }

/** What one node's send came to: queued for delivery, or the node (or the Wear API) gone. */
internal enum class NodeSend { QUEUED, GONE }

/** The Data Layer as the Android link needs it, so its rules are tested over nodes a test tells. */
internal interface WatchNodes {
    /**
     * The nodes with Taqwa's watch app (`taqwa_watch`) that are reachable now, each with its nearby
     * flag; null when this phone has no Wear API (no Play services, or no Wear OS app). Any other
     * failure throws.
     */
    suspend fun watches(): List<WatchNode>?

    /** Sends [data] on [path] to [nodeId]: [NodeSend.GONE] when it or the API went away; any other failure throws. */
    suspend fun send(nodeId: String, path: String, data: ByteArray): NodeSend
}

/**
 * The Wear Data Layer through Play services (plan 2 Task 5), over the three calls the app makes:
 * [availability], [capability] and [message]. The `Context` constructor makes them Play services'
 * own; a test hands it tasks of its own, so the real reading of each answer is on its path.
 *
 * - [watches] first asks `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable`, its
 *   public reference's call (Ruling 54 item 1), which shows nothing. Without a usable Play
 *   services it answers null and makes no Wearable client, so no update prompt, dialog or
 *   notification can come from it. Then `getCapability("taqwa_watch", FILTER_REACHABLE)`: an
 *   `ApiException` for which [noWearApi] holds (17, a phone without the Wear OS app) is null, its
 *   `ConnectionResult` code logged; any other throw propagates, and the sync answers `FAILED`.
 * - [send] is `MessageClient.sendMessage`: an `ApiException` for which [nodeGone] or [noWearApi]
 *   holds is [NodeSend.GONE]; any other throw propagates.
 *
 * Never `DataClient`, `putDataItem`, a channel or an asset: a data item syncs to every node,
 * Google's cloud node included, and cannot be addressed to one (`WatchLinkContract`).
 */
internal class PlayWatchNodes(
    private val availability: () -> Int,
    private val capability: () -> Task<CapabilityInfo>,
    private val message: (nodeId: String, path: String, data: ByteArray) -> Task<Int>,
) : WatchNodes {

    constructor(context: Context) : this(
        availability = { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) },
        capability = {
            Wearable.getCapabilityClient(context)
                .getCapability(WatchLinkContract.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        },
        message = { nodeId, path, data -> Wearable.getMessageClient(context).sendMessage(nodeId, path, data) },
    )

    override suspend fun watches(): List<WatchNode>? {
        if (noPlayServices(availability())) return null
        val info = try {
            capability().await()
        } catch (e: ApiException) {
            if (!noWearApi(e.statusCode)) throw e
            // Logged, never compared: API_UNAVAILABLE (16) on a phone without the Wear OS app,
            // SERVICE_MISSING (1) and so on. No place, no person.
            println("TaqwaWatch no Wear API ${e.status.connectionResult?.errorCode}")
            return null
        }
        return info.nodes.map { WatchNode(it.id, it.isNearby) }
    }

    override suspend fun send(nodeId: String, path: String, data: ByteArray): NodeSend = try {
        message(nodeId, path, data).await()
        NodeSend.QUEUED
    } catch (e: ApiException) {
        if (nodeGone(e.statusCode) || noWearApi(e.statusCode)) NodeSend.GONE else throw e
    }
}

/**
 * The Android phone's link to the user's own watch: one `MessageClient` message to each node with
 * Taqwa's watch app that is directly connected when the send is made (plan 2 Task 5, Ruling 39).
 *
 * - It confirms by echo alone: a `MessageClient` success is only "queued for delivery", so only the
 *   watch's acknowledgement or its next handshake confirms a delivery (the Lead's call 1).
 * - [oneAsk] holds one reading of the watches for an ask of the sync, taken at its first need,
 *   inside the ask's budget (Ruling 54 item 4). [reachable] (is a watch with Taqwa directly
 *   connected?) and [watchKeys] answer from it. That first need is the sync's step 1, before the
 *   feed is built, so its step 4 judges by the watches nearby then.
 * - [watchKeys] are those watches' node ids (Ruling 52 item 9): a confirmation counts only for the
 *   node whose echo was taken in last, and only while it is nearby, so a watch switched to between
 *   asks never inherits another's. [watchKey] is null: a send goes to every nearby watch, not one.
 * - **A known limit of the one reading:** a watch switched to while an ask builds its feed is
 *   judged as the watch read, so that ask can answer `UNCHANGED` and send it nothing. Whatever
 *   tells the phone of the switch, the new watch's capability change or its handshake on finding
 *   the phone nearby, comes after the reading. So it is queued behind that ask and served on a
 *   reading of its own. The capability change sends the new watch the feed once the old watch has
 *   left. Otherwise the old watch's confirmation still counts there, and the new watch's
 *   handshake sends it (`WatchFeedSync` step 4, plan 2 Review Focus 2, Ruling 55 item 1).
 * - [send] asks again, so the nearby flag is read as close to the send as the API allows, and sends
 *   the JSON's UTF-8 on `WatchLinkContract.feedPath(delivery)` to each nearby node, each in its own
 *   `try`, so one node's throw never stops the next. Any node queued is `SENT`. Otherwise no node,
 *   or every node gone, is `NO_WATCH`, and any node that threw is `FAILED`. A cut by the ask's
 *   budget passes through, so no further node is called after it.
 *
 * **How many capability queries an ask makes:** one when it sends nothing (no watch, no feed, a
 * feed that cannot be encoded, or one the watch is known to hold), and two when it sends: the
 * ask's reading and the one at the send. Never three, since this link never answers `TOO_LARGE`
 * (the sync's only second send). An acknowledgement makes none: its echo counts for the node it
 * came from. Outside an ask (a direct call), [reachable] and [watchKeys] each read for themselves.
 */
internal class AndroidWatchLink(private val nodes: WatchNodes) : WatchLink {

    override val confirmsDelivery: Boolean = true

    override suspend fun <T> oneAsk(block: suspend () -> T): T = withContext(AskReading()) { block() }

    override suspend fun reachable(): Boolean = reading().isNotEmpty()

    override suspend fun watchKey(): String? = null

    override suspend fun watchKeys(): Set<String?> = reading().toSet()

    override suspend fun send(json: String, delivery: WatchDelivery): WatchSendResult {
        val ids = nearby()
        if (ids.isEmpty()) return WatchSendResult.NO_WATCH
        val path = WatchLinkContract.feedPath(delivery)
        val data = json.encodeToByteArray()
        var queued = false
        var failed = false
        for (id in ids) {
            try {
                if (nodes.send(id, path, data) == NodeSend.QUEUED) queued = true
            } catch (e: Exception) {
                // The ask's own budget passes through; a stray CancellationException is a failure.
                currentCoroutineContext().ensureActive()
                failed = true
            }
        }
        return when {
            queued -> WatchSendResult.SENT
            failed -> WatchSendResult.FAILED
            else -> WatchSendResult.NO_WATCH
        }
    }

    /** The ask's one reading of the nearby watches, taken at its first need; outside an ask, a reading of its own. */
    private suspend fun reading(): List<String> {
        val ask = currentCoroutineContext()[AskReading] ?: return nearby()
        return ask.ids ?: nearby().also { ask.ids = it }
    }

    /** One capability query: the ids of the watches with Taqwa directly connected now. */
    private suspend fun nearby(): List<String> = directlyConnected(nodes.watches() ?: emptyList())

    /**
     * The reading one ask shares, in that ask's coroutine context, so it never outlives the ask and
     * no other ask sees it. Only that ask's own coroutine reads or writes [ids], one call at a time.
     */
    private class AskReading : AbstractCoroutineContextElement(AskReading) {
        var ids: List<String>? = null

        companion object Key : CoroutineContext.Key<AskReading>
    }
}

internal actual fun createWatchLink(): WatchLink = AndroidWatchLink(PlayWatchNodes(appContext))
