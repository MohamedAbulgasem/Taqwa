package world.taqwa.app.watchfeed

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import world.taqwa.app.settings.appContext
import world.taqwa.app.watch.WatchLinkContract
import java.util.concurrent.Executor

/**
 * Settings › Watch on an Android phone (plan 5 Task 6, spec 5), through Play services, over the
 * five calls it makes: [availability], [connectedNodes], [capability], [remote] and [listen]. The
 * `Context` constructor makes them Play services' and `RemoteActivityHelper`'s own; a test hands it
 * tasks of its own, so the real reading of each answer is on its path.
 *
 * - [targets] first asks `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable`, as
 *   the feed's sender does ([PlayWatchNodes]), which shows nothing. Without a usable Play services
 *   it answers [WatchTargets.NONE] and makes no Wearable client, so no update prompt, dialog or
 *   notification can come from it. Then the connected watches (`NodeClient.getConnectedNodes`,
 *   each one's `displayName` and `isNearby`): an `ApiException` for which [noWearApi] holds (17, a
 *   phone without the Wear OS app) is no watch, and any other throw propagates, which
 *   [loadWatchTargets] makes no row. Only with a directly connected watch are its two
 *   capabilities asked, `taqwa_watch` and `taqwa_face_addable`, each `FILTER_REACHABLE`, and
 *   [watchTargets] decides.
 * - [open] starts [remoteIntentSpec]'s intent on the row's own watch through
 *   `RemoteActivityHelper.startRemoteActivity(intent, nodeId)`, awaited: true once the watch's
 *   companion app took it, false when it refused or failed, or let its answer go unsent (the
 *   future's garbage-collected path). Its answer comes on a binder thread, or on the finalizer's
 *   for that path, and the rest of the call runs there ([WatchTargetsSource.open]). It shows
 *   nothing on the phone. The helper has no bound of its own, and while the phone's Wear OS
 *   companion is cached Android may hold its broadcast for a while (P5.6's finding): [openOnWatch]
 *   answers the row at its bound and leaves this call running (Ruling 72 item 3).
 * - [changes], while Settings is in front, listens to both capabilities through one
 *   `CapabilityClient.addListener(listener, name)` each, and says each change: a watch getting
 *   Taqwa (`taqwa_watch` added), taking its face (`taqwa_face_addable` removed), or a watch with
 *   Taqwa coming or going, and says one of its own once both are added, for a change that came
 *   while they were being added. It asks Play services' availability first, as [targets] does, and
 *   without it listens to nothing and ends. The listener is removed from both when Settings stops
 *   collecting, whatever ends it. `NodeClient` has no listener (play-services-wearable 20.0.1), so a
 *   watch without Taqwa that comes or goes while Settings is open is read at the next resume, or
 *   a tap's follow (`TapFollow`).
 *
 * No permission comes with it: `wear-remote-interactions` declares none (the library check in
 * docs/superpowers/reports/2026-11-06-phone-watch-rows.md).
 */
internal class PlayWatchTargets(
    private val availability: () -> Int,
    private val connectedNodes: () -> Task<List<Node>>,
    private val capability: (name: String) -> Task<CapabilityInfo>,
    private val remote: suspend (spec: RemoteIntentSpec, nodeId: String) -> Unit,
    /** Adds [listener] for the capability [name], and answers how to remove it again. */
    private val listen: (name: String, listener: CapabilityClient.OnCapabilityChangedListener) -> () -> Unit,
) : WatchTargetsSource {

    constructor(context: Context) : this(
        availability = { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) },
        connectedNodes = { Wearable.getNodeClient(context).connectedNodes },
        capability = { name -> Wearable.getCapabilityClient(context).getCapability(name, CapabilityClient.FILTER_REACHABLE) },
        remote = { spec, nodeId ->
            // A helper made at the tap, so no Wearable client is made before Play services is known
            // to be there, and its listeners run where Play services completes them: no thread of its own.
            RemoteActivityHelper(context, directly).startRemoteActivity(spec.intent(), nodeId).await()
        },
        listen = { name, listener ->
            // One client adds the listener and removes it. The listener only hands a word to the
            // flow (trySend never waits), on whatever thread Play services calls it.
            val client = Wearable.getCapabilityClient(context)
            client.addListener(listener, name)
            val remove: () -> Unit = { client.removeListener(listener, name) }
            remove
        },
    )

    override suspend fun targets(): WatchTargets {
        if (noPlayServices(availability())) return WatchTargets.NONE
        val connected = try {
            connectedNodes().await().map { WatchPeer(id = it.id, name = it.displayName, nearby = it.isNearby) }
        } catch (e: ApiException) {
            if (!noWearApi(e.statusCode)) throw e
            return WatchTargets.NONE
        }
        // No capability is asked for a phone with no watch directly connected: there is no row to show.
        if (connected.none { it.nearby }) return WatchTargets.NONE
        return watchTargets(
            connected = connected,
            withApp = nodeIds(WatchLinkContract.WATCH_CAPABILITY),
            wantsFace = nodeIds(WatchLinkContract.FACE_CAPABILITY),
        )
    }

    override suspend fun open(target: WatchTarget, action: WatchAction): Boolean {
        if (noPlayServices(availability())) return false
        return try {
            remote(remoteIntentSpec(action), target.nodeId)
            true
        } catch (e: Throwable) {
            // RemoteIntentException (the companion app answered RESULT_FAILED), an ApiException, or
            // anything else the start threw: the watch did not take it. That includes the helper's
            // future failing with CallbackToFutureAdapter's FutureGarbageCollectedException, a
            // Throwable that is no Exception: its result receiver was let go unanswered, so no
            // answer can come (Ruling 74 item 14). The VM's own errors are no answer, and the
            // caller's own cancel passes.
            if (e is Error) throw e
            currentCoroutineContext().ensureActive()
            false
        }
    }

    override fun changes(): Flow<Unit> = flow {
        // As targets: no Wearable client before Play services is known to be there.
        if (noPlayServices(availability())) return@flow
        emitAll(capabilityChanges())
    }

    /**
     * One listener on both capabilities, each change said once, several at once as one (conflated):
     * Settings reads the whole rows at each. Added in order and removed from each one added,
     * whatever ends the collection: a listener that cannot be added ends it with its throw, after
     * the other is removed, and a removal that throws is dropped, since a throw while Settings
     * leaves would end its screen rather than the listening.
     *
     * Once both are added it says one change of its own (the review of 9 November). Settings starts
     * listening as its first reading asks the capabilities, and Play services adds a listener
     * asynchronously, so a change that landed after that reading asked and before the listener was
     * in place would be neither read nor heard. The rows are read once more after both adds, at the
     * cost of one reading each time Settings comes to the front, conflated behind the first if that
     * is still asking.
     */
    private fun capabilityChanges(): Flow<Unit> = callbackFlow {
        val listener = CapabilityClient.OnCapabilityChangedListener { trySend(Unit) }
        val removals = mutableListOf<() -> Unit>()
        try {
            for (name in LISTENED) removals += listen(name, listener)
            trySend(Unit)
            awaitClose()
        } finally {
            for (remove in removals) {
                try {
                    remove()
                } catch (e: Exception) {
                    // Nothing more to do: Settings has stopped listening.
                }
            }
        }
    }.buffer(Channel.CONFLATED)

    private suspend fun nodeIds(name: String): Set<String> = capability(name).await().nodes.mapTo(mutableSetOf()) { it.id }

    private companion object {
        /** The two capabilities the rows come from, as [targets] asks them. */
        val LISTENED = listOf(WatchLinkContract.WATCH_CAPABILITY, WatchLinkContract.FACE_CAPABILITY)
    }
}

/** The intent [RemoteActivityHelper] starts on the watch: a VIEW of the URI, in the BROWSABLE category. */
private fun RemoteIntentSpec.intent(): Intent = Intent(action).addCategory(category).setData(Uri.parse(uri))

/** Runs a listener on the thread that completes its task, as [await]'s own listener does. */
private val directly = Executor { it.run() }

internal actual fun watchTargetsSource(): WatchTargetsSource = PlayWatchTargets(appContext)
