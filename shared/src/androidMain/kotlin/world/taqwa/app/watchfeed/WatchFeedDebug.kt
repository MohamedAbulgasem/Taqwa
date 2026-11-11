package world.taqwa.app.watchfeed

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.di.appContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.settings.TasbeehStore
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson

/** A Data Layer node the phone is connected to, as the debug seam prints it: its id, its name and whether it is nearby. */
internal data class DebugNode(val id: String, val name: String, val nearby: Boolean)

/**
 * Plan 2 Task 7's debug seam: what the phone's debug harness (`androidApp`'s
 * `WatchHarnessReceiver`, debug builds only) asks of the watch side. It is public only because the
 * harness is another module; it speaks only in strings, Booleans and `Int`s, so no feed type
 * reaches `androidApp` (plan 2, Global Constraints, "Visibility"). Release code never calls it, so
 * R8 drops it from the release build (plan 2 Task 7 Step 5 reads `usage.txt` for it).
 *
 * Every call into the watch side is bounded by [WatchFeedSync.CALLER_BOUND] and catches
 * `Exception`, as every caller of the sync does: it answers "TIMEOUT" when the bound passes and
 * "FAILED <the exception's class>" for a throw, never a throw of its own. Its own lines name a
 * delivery by its number and fingerprint alone, as a release line does, though a debug harness may
 * name the origin (Ruling 52 item 4); no line names the place or the bearing.
 */
object WatchFeedDebug {

    /**
     * Every node the Data Layer connects this phone to, one line each, with its id, its name and
     * whether it is nearby, and which of them have Taqwa's watch app (`taqwa_watch`):
     * `node <id> "<name>" nearby=<true|false> taqwa_watch=<true|false>`. "no Wear API" when the
     * phone has none (no Play services, or no Wear OS app: [PlayWatchNodes.watches] is null, and
     * then no node list is asked for), and "no nodes" when nothing is connected.
     */
    suspend fun nodes(context: Context): String = bounded {
        describeNodes(PlayWatchNodes(context)) {
            Wearable.getNodeClient(context).connectedNodes.await().map { DebugNode(it.id, it.displayName, it.isNearby) }
        }
    }

    /**
     * One ask of the app's sync for [reason] (a [WatchSyncReason]'s name), awaited: the result's
     * name, "TIMEOUT", or "unknown reason <reason>". `WATCH_CHANGED` is the harness's `send`, which
     * forgets the confirmation first; `WATCH_APPEARED` its `appeared`, the damped ask of a reconnect.
     */
    suspend fun sync(reason: String): String = syncOn(appContainer.watchFeedSync, reason)

    /**
     * The feed the next ask would build, with its `version` raised to [version], sent through the
     * link directly, with that feed's own fingerprint, under the next number (check 7: a watch
     * that reads an older version keeps its feed and says "Update Taqwa on your watch"). The
     * result's name, "NO_FEED" with no location, "TIMEOUT" or "FAILED …".
     */
    suspend fun sendWithVersion(version: Int): String = appContainer.let {
        sendWithVersion(version, it.watchFeedSync, it.watchFeedFor, it.watchLink, it.watchFeedMemory)
    }

    /**
     * One of the user's own tasbeeh phrases added, [text] to [target], as the Tasbeeh sheet adds it
     * (`TasbeehStore.addCustom`: trimmed to 60 characters, the target held to 1..1000), then the ask
     * the Tasbeeh screen's hook makes, `PHRASES_CHANGED`, which the harness has to make itself
     * because the hook lives on the screen's view model: "added <id>; PHRASES_CHANGED <result>".
     * Here and not in the harness, so the phrase's own type, `:watchcore`'s, never reaches
     * `androidApp`. A phrase blank once cleaned is the store's `IllegalArgumentException`.
     */
    suspend fun phrase(text: String, target: Int): String =
        phraseOn(appContainer.tasbeehStore, appContainer.watchFeedSync, text, target)

    /** True once every ask made before it has been served, false when the bound passes first. */
    suspend fun settle(): Boolean = settleOn(appContainer.watchFeedSync)

    /** The app's interface language that the next feed is built in, by its code ([watchUiLanguage]). */
    suspend fun language(): String = watchUiLanguage().code

    /** [PhoneForms.json] on this phone's own ICU: the forms the face's tools measure, as this phone writes them. */
    fun phoneFormsJson(): String = PhoneForms.json()

    // ── The rules, over what a test hands them ──

    internal suspend fun describeNodes(watches: WatchNodes, connected: suspend () -> List<DebugNode>): String {
        val taqwa = watches.watches() ?: return "no Wear API"
        val peers = connected()
        val ids = (peers.map { it.id } + taqwa.map { it.id }).distinct()
        if (ids.isEmpty()) return "no nodes"
        return ids.joinToString("\n") { id ->
            val peer = peers.firstOrNull { it.id == id }
            val watch = taqwa.firstOrNull { it.id == id }
            val nearby = peer?.nearby ?: watch?.nearby ?: false
            "node $id \"${peer?.name.orEmpty()}\" nearby=$nearby taqwa_watch=${watch != null}"
        }
    }

    internal suspend fun phraseOn(store: TasbeehStore, sync: WatchFeedSync, text: String, target: Int): String {
        val added = store.addCustom(text, target)
        return "added ${added.id}; PHRASES_CHANGED ${syncOn(sync, WatchSyncReason.PHRASES_CHANGED.name)}"
    }

    internal suspend fun syncOn(sync: WatchFeedSync, reason: String): String {
        val why = WatchSyncReason.entries.firstOrNull { it.name == reason } ?: return "unknown reason $reason"
        return bounded { sync.sync(why).name }
    }

    internal suspend fun settleOn(sync: WatchFeedSync): Boolean = try {
        withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { sync.settle() } ?: false
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        false
    }

    /**
     * The raised feed's send. The asks queued before it are served first ([WatchFeedSync.settle]),
     * so its number follows theirs; then the next number is written down before the link is
     * called, as the sync writes its own (Ruling 50 item 1), so the watch takes it as new and no
     * number is used again whatever becomes of the send. Nothing is confirmed by it: only the
     * watch's echo confirms, and a watch that cannot read the version echoes nothing.
     *
     * A known limit of a seam outside the sync's one worker: an ask the sync serves in the very
     * moment between this read and write (a watch's handshake, say) could take the same number for
     * its own feed. The record's rule for an echo at its last number with another fingerprint
     * (`WatchFeedRecord.echoed`, Ruling 50 amendment A4) then costs one more send, never a wrong
     * `UNCHANGED`. The harness is driven one command at a time, and no release build holds this.
     */
    internal suspend fun sendWithVersion(
        version: Int,
        sync: WatchFeedSync,
        feedFor: suspend (GeoLocation?) -> WatchFeed?,
        link: WatchLink,
        memory: WatchFeedMemory,
    ): String = bounded {
        sync.settle()
        val feed = feedFor(null)?.copy(version = version) ?: return@bounded WatchSendResult.NO_FEED.name
        val json = WatchFeedJson.encode(feed)
        val fingerprint = watchFeedFingerprint(feed)
        val numbered = memory.read().sending(standsFor = fingerprint, sent = fingerprint)
        memory.write(numbered)
        val delivery = checkNotNull(numbered.lastDelivery)
        val result = link.send(json, delivery)
        println("TaqwaWatch DEBUG version $version $result ${loggedDelivery(delivery.text)}")
        result.name
    }

    /** [block] within the callers' bound: "TIMEOUT" when it passes, "FAILED <class>" for a throw. */
    private suspend fun bounded(block: suspend () -> String): String = try {
        withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { block() } ?: "TIMEOUT"
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        "FAILED ${e::class.simpleName}"
    }
}
