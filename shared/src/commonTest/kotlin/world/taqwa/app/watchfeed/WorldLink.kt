package world.taqwa.app.watchfeed

import kotlinx.coroutines.awaitCancellation
import world.taqwa.app.watch.WatchDelivery

/**
 * What one send over a link comes to, as the platforms allow it (Ruling 50's design checks):
 * - [SENT]: handed over and answered so;
 * - [REFUSED] and [NO_WATCH]: answered so, nothing handed over;
 * - [CUT_OFF]: handed over, and the ask's budget cut the sync off before the answer;
 * - [CUT_OFF_BEFORE]: cut off before anything was handed over;
 * - [THROWN]: the call threw, nothing handed over (the iPhone's `updateApplicationContext` that
 *   throws leaves the earlier context in place);
 * - [THROWN_AFTER]: Android's task failed after the message was queued all the same.
 */
internal enum class Outcome(
    val answer: WatchSendResult,
    val handed: Boolean,
    val cut: Boolean = false,
    val throws: Boolean = false,
) {
    SENT(WatchSendResult.SENT, handed = true),
    REFUSED(WatchSendResult.FAILED, handed = false),
    NO_WATCH(WatchSendResult.NO_WATCH, handed = false),
    CUT_OFF(WatchSendResult.FAILED, handed = true, cut = true),
    CUT_OFF_BEFORE(WatchSendResult.FAILED, handed = false, cut = true),
    THROWN(WatchSendResult.FAILED, handed = false, throws = true),
    THROWN_AFTER(WatchSendResult.FAILED, handed = true, throws = true),
}

/**
 * A link whose every send is decided by its world: [outcome] for a send that fits, and
 * [WatchSendResult.TOO_LARGE] (nothing handed over) for one over [limitBytes]. A send handed over
 * goes to [onHanded], which puts it on its way to the watches. [reachable], [key] and [keys] are the
 * world's too: [key] is the session's watch key on the iPhone; [keys], when set, are the keys of
 * every watch a send goes to ([WatchLink.watchKeys]): on Android the nearby watches' node ids
 * (Ruling 52 item 9). Unset, they are the one [key].
 */
internal class WorldLink(override val confirmsDelivery: Boolean, private val limitBytes: Int) : WatchLink {
    var reachable: () -> Boolean = { true }
    var key: () -> String? = { null }
    var keys: (() -> Set<String?>)? = null
    var outcome: () -> Outcome = { Outcome.SENT }
    var onHanded: (FakeWatchLink.Sent) -> Unit = {}
    val sent: MutableList<FakeWatchLink.Sent> = mutableListOf()

    override suspend fun reachable(): Boolean = reachable.invoke()

    override suspend fun watchKey(): String? = key.invoke()

    override suspend fun watchKeys(): Set<String?> = keys?.invoke() ?: setOf(key.invoke())

    override suspend fun send(json: String, delivery: WatchDelivery): WatchSendResult {
        val one = FakeWatchLink.Sent(json, delivery)
        sent += one
        if (json.encodeToByteArray().size > limitBytes) return WatchSendResult.TOO_LARGE
        val decided = outcome()
        if (decided.handed) onHanded(one)
        if (decided.throws) throw IllegalStateException("the link broke")
        if (decided.cut) awaitCancellation()
        return decided.answer
    }
}
