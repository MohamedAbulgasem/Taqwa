package world.taqwa.app.watchfeed

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Why the watch is asked for (the Lead's call 1).
 *
 * - [RESCHEDULED]: a plan was armed, after any reschedule: a setting, a language, a journey, a
 *   boot, the twice-daily top-up.
 * - [PHRASES_CHANGED]: one of the user's own tasbeeh phrases was added, edited or deleted.
 * - [WATCH_ASKED]: the watch's handshake, echoing the delivery it holds.
 * - [WATCH_APPEARED]: a watch that may be back: Android's capability change with a nearby watch,
 *   the iPhone's activation at launch. Damped by the record, as a reschedule is.
 * - [WATCH_CHANGED]: the iPhone's watch state changed (the app installed or removed, another
 *   watch paired), and Task 7's harness `send`: the confirmation is forgotten before the ask is
 *   served.
 */
internal enum class WatchSyncReason { RESCHEDULED, PHRASES_CHANGED, WATCH_ASKED, WATCH_APPEARED, WATCH_CHANGED }

/**
 * When the phone sends its watch a feed, never at the reminders' cost (plan 2 Task 4), and what it
 * records of it (Ruling 50, the feed's delivery number, as the design checks amended it).
 *
 * **Asked, never awaited.** A reschedule, a phrase change or a watch appearing only [request]s,
 * which queues the ask and returns at once, so the coordinator's lock never waits on the watch.
 * One worker on [scope], on [work] (never the caller's dispatcher, which is Main for a settings
 * write), serves the asks one at a time in the order they were made, so two are never served in
 * swapped order and two sends never interleave.
 *
 * **The delivery number.** Every send carries the install's origin and the next number beside the
 * feed ([WatchFeedRecord]); the watch stores a feed only when it moves it forward and echoes what
 * it holds. So what the watch holds only moves forward, whatever order the links deliver in, and
 * the phone answers `UNCHANGED` only once the watch has echoed the latest delivery. Every forget
 * sets a number aside, so no echo sent before it confirms the latest number. A late, lost,
 * duplicated or reordered message can cost one more send, never a wrong `UNCHANGED`, outside the
 * known limits plan 2's call 1 names, each healed by the watch's next echo.
 *
 * **Each ask, in this order,** all inside one [budget] that starts when the ask is served, never
 * when it was queued, and inside the link's [WatchLink.oneAsk], so that Android answers steps 1
 * and 4 from one reading of its watches (Ruling 54 item 4):
 * 0. [WatchSyncReason.WATCH_CHANGED] forgets the confirmation and sets a number aside
 *    ([WatchFeedRecord.forgotten]), so even an answer of no watch leaves nothing confirmed, and no
 *    echo from before it confirms again (item 6, amendment A2); [WatchSyncReason.WATCH_ASKED] takes
 *    in the watch's echo ([WatchFeedRecord.echoed]): the number it names is confirmed for that
 *    watch, lower or higher, an echo of nothing or of another install's forgets in the same way,
 *    one above the phone's numbers lifts them; on the iPhone one from a watch that is not the
 *    session's is ignored, and on Android every one counts for the node it came from;
 * 1. no watch reachable is [WatchSendResult.NO_WATCH], with nothing read or built;
 * 2. no feed (no location, or no today) is [WatchSendResult.NO_FEED];
 * 3. a feed that cannot be encoded (a NaN) is [WatchSendResult.FAILED], with nothing sent;
 * 4. the record holding the feed for a watch a send now goes to ([WatchFeedRecord.holds]: the
 *    latest delivery echoed by it, or handed over to it where that counts, and it stood for this
 *    feed) is [WatchSendResult.UNCHANGED], whatever the reason: spec 1.2's idempotent send.
 *    Otherwise the feed is sent.
 *    On Android the watches judged here are the ask's one reading, taken at step 1, before the
 *    build (Ruling 54 item 4); the iPhone reads its session's watch here. So on Android a watch
 *    switched to while the feed is built is judged as the watch read, and is sent nothing at this
 *    ask: a known limit (plan 2 Review Focus 2, ratified by Ruling 55 item 1). Whatever tells the
 *    phone of it, its capability change (`WATCH_APPEARED`) or its handshake on finding the phone
 *    nearby, comes after that reading, so it is queued behind this ask and served on a reading of
 *    its own. Its capability change heals it once the old watch has left. Otherwise the old
 *    watch's confirmation still counts there, and its handshake heals it;
 * 5. a send takes the next number and writes it down, with the feed it stands for and the
 *    fingerprint it carries, BEFORE the link is called (item 1), so a number is never used for
 *    another feed.
 *    A [WatchSendResult.TOO_LARGE] (nothing handed over) sends the feed once more without its
 *    phrases, under the next number, standing for the full feed, so the times always arrive and
 *    an unchanged feed over the limit is not sent at every ask; a second one is
 *    [WatchSendResult.FAILED];
 * 6. a [WatchSendResult.SENT] on a link that does not confirm by echo (the iPhone) confirms its
 *    number for the watch it went to, since the system delivers the latest handed context and
 *    nothing older after it (item 4). Any other answer, or any answer on Android, confirms
 *    nothing: there the watch's [acknowledge] or its next handshake does.
 *
 * **Never a throw.** Any `Exception` is [WatchSendResult.FAILED] and the worker goes on, a
 * `CancellationException` thrown while [scope] is still active included (a library's own, such as
 * the `java.util.concurrent.CancellationException` of a Play services task). Only the cancellation
 * of [scope] ends the worker. Its queue is then closed, so [sync], [acknowledge] and [settle] made
 * after that answer at once, and one already waiting returns within [callerBound].
 *
 * **An ask the budget cuts is asked again, once** (Ruling 70 item 1): [AGAIN_AFTER] after the
 * cut, the same ask (its reason, location, echo and watch) is queued again with no caller waiting,
 * and served through the same path with a budget of its own; step 0's forget makes the repeat safe
 * against a wrong `UNCHANGED`. Only the budget's cut is asked again, never a `FAILED` the link
 * answered, a throw or a feed that cannot be encoded; and a second cut waits for the usual
 * triggers. Only a reschedule's ask carries a planned location, and an ask made after it and
 * served before its ask again comes due takes that one's place, so the watch is never sent an
 * older plan after a newer one; a settle, an acknowledgement or another ask asked again takes no
 * place. Every other ask builds from what is stored when it is served, so it can never send an
 * older plan, and is asked again whatever is served meanwhile: a cut in its step 0 (the forget, or
 * the echo taken in) is then never lost. The wait runs on [scope], never in the worker's turn, so
 * nothing behind the cut ask waits and [settle] does not wait for it. It lives as long as the
 * process does, and a cached Android process may run it only when it next thaws (P5.13 measures
 * it).
 *
 * [report] is called once per ask, with the milliseconds it took from when it was served on
 * [clock], and [timed] once per ask with the time of each of its steps ([AskTimes]), the step the
 * budget cut or a throw ended named unfinished, for the debug log (Ruling 70 item 1).
 */
internal class WatchFeedSync(
    private val feedFor: suspend (GeoLocation?) -> WatchFeed?,
    private val link: WatchLink,
    private val memory: WatchFeedMemory,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val budget: Duration = 5.seconds,
    private val callerBound: Duration = CALLER_BOUND,
    private val report: (WatchSyncReason, WatchSendResult, elapsedMillis: Long) -> Unit = { _, _, _ -> },
    private val clock: TimeSource = TimeSource.Monotonic,
    private val timed: (AskTimes) -> Unit = {},
) {
    private sealed interface Ask

    /**
     * One ask. [again] is true for the ask asked again after a cut ([askAgain]); [after] is, for
     * that ask when it carries a planned [location], the count of asks served at the cut, and null
     * otherwise.
     */
    private class Sync(
        val reason: WatchSyncReason,
        val location: GeoLocation?,
        val have: String?,
        val from: String?,
        val answer: CompletableDeferred<WatchSendResult>?,
        val again: Boolean = false,
        val after: Long? = null,
    ) : Ask

    private class Acknowledgement(val echo: String, val from: String?, val done: CompletableDeferred<Unit>) : Ask

    private class Settle(val done: CompletableDeferred<Unit>) : Ask

    private val asks = Channel<Ask>(Channel.UNLIMITED)

    /**
     * How many asks as made the worker has served, never a settle, an acknowledgement or an ask
     * asked again; only the worker reads or writes it.
     */
    private var served = 0L

    init {
        // When the worker ends, its queue is closed (Ruling 48), so an ask, an acknowledgement or
        // a settle made after that is refused and answered at once, never after [callerBound].
        // A completion handler rather than a `finally`: it also runs for a scope cancelled before
        // the worker could start, whose body never runs.
        scope.launch(work) {
            for (ask in asks) serve(ask)
        }.invokeOnCompletion { asks.close() }
    }

    /**
     * Queues an ask and returns at once. It never suspends, blocks or throws, even once the worker
     * is gone. [location] is the one just planned, or null for the stored one. For
     * [WatchSyncReason.WATCH_ASKED] alone, [have] is the watch's echo
     * (`WatchLinkContract.echoOf` what it holds) and [from] the key of the watch it came from: on
     * the iPhone [WatchLink.watchKey] when it arrived, on Android the message's source node id
     * (Ruling 52 item 9).
     */
    fun request(reason: WatchSyncReason, location: GeoLocation? = null, have: String? = null, from: String? = null) {
        asks.trySend(Sync(reason, location, have, from, answer = null))
    }

    /**
     * Queues an ask as [request] does and awaits its answer for at most [callerBound], then
     * answers [WatchSendResult.FAILED], the ask staying queued. For a caller that must answer the
     * watch: its handshake. A [have] that `WatchLinkContract.deliveryOf` refuses is an echo of
     * nothing.
     */
    suspend fun sync(
        reason: WatchSyncReason,
        location: GeoLocation? = null,
        have: String? = null,
        from: String? = null,
    ): WatchSendResult {
        val answer = CompletableDeferred<WatchSendResult>()
        if (asks.trySend(Sync(reason, location, have, from, answer)).isFailure) return WatchSendResult.FAILED
        return withTimeoutOrNull(callerBound) { answer.await() } ?: WatchSendResult.FAILED
    }

    /**
     * The Android watch's acknowledgement, [echo] being what it holds now
     * (`WatchLinkContract.echoOf`), from the watch [from] names (the message's source node id,
     * Ruling 52 item 9), queued behind every ask before it and awaited for at most [callerBound].
     * Served in its turn, it is taken in as a handshake's echo is (step 0), for that node. Never
     * throws.
     *
     * On the iPhone nothing calls it: the watch shakes hands and never acknowledges. One made there
     * is read as a handshake's echo from the watch [from] names (Ruling 54 item 5). So one with no
     * key is ignored while the session's watch has a key, and taken in for the session's watch
     * while it has none ([WatchLink.watchKey] null), as that watch's own word.
     */
    suspend fun acknowledge(echo: String, from: String? = null) {
        val done = CompletableDeferred<Unit>()
        if (asks.trySend(Acknowledgement(echo, from, done)).isFailure) return
        withTimeoutOrNull(callerBound) { done.await() }
    }

    /**
     * True once every ask made before this has been served, or false when [callerBound] passes
     * first. A background entry point whose process may stop when it returns calls it last.
     */
    suspend fun settle(): Boolean {
        val done = CompletableDeferred<Unit>()
        if (asks.trySend(Settle(done)).isFailure) return false
        return withTimeoutOrNull(callerBound) { done.await() } != null
    }

    private suspend fun serve(ask: Ask) {
        when (ask) {
            is Sync -> {
                // A planned location's ask asked again whose place a later ask took is not served (see askAgain).
                if (ask.after != null && ask.after != served) return
                if (!ask.again) served++
                val start = clock.markNow()
                val steps = StepTimes(clock)
                var cut = false
                val result = attempt {
                    withTimeoutOrNull(budget) { link.oneAsk { answer(ask, steps) } } ?: WatchSendResult.FAILED.also { cut = true }
                } ?: WatchSendResult.FAILED
                attempt { report(ask.reason, result, start.elapsedNow().inWholeMilliseconds) }
                attempt { timed(AskTimes(ask.reason, ask.again, result, cut, steps.taken())) }
                ask.answer?.complete(result)
                if (cut && !ask.again) askAgain(ask)
            }
            is Acknowledgement -> {
                attempt { withTimeoutOrNull(budget) { takeIn(ask.echo, ask.from) } }
                ask.done.complete(Unit)
            }
            is Settle -> ask.done.complete(Unit)
        }
    }

    /**
     * Queues [ask] once more [AGAIN_AFTER] after the budget cut it (Ruling 70 item 1), with no
     * answer awaited, on [scope] and never in the worker's turn, so nothing behind it waits. An ask
     * with a planned location (a reschedule's) carries the count of asks served at the cut, so a
     * later ask served before it, made with the newer plan or building from it, takes its place:
     * the watch is never sent the older plan after the newer. An ask with none builds from what is
     * stored when it is served, the newest plan, so nothing takes its place: asking it again costs
     * one more send at most, and keeps its step 0.
     */
    private fun askAgain(ask: Sync) {
        val after = if (ask.location != null) served else null
        val again = Sync(ask.reason, ask.location, ask.have, ask.from, answer = null, again = true, after = after)
        scope.launch(work) {
            delay(AGAIN_AFTER)
            asks.trySend(again)
        }
    }

    /** Steps 0 to 6 of one ask, each timed in [steps]. */
    private suspend fun answer(ask: Sync, steps: StepTimes): WatchSendResult {
        when (ask.reason) {
            WatchSyncReason.WATCH_CHANGED -> steps.time(WatchAskStep.FORGET) { memory.write(memory.read().forgotten()) }
            WatchSyncReason.WATCH_ASKED -> steps.time(WatchAskStep.ECHO) { takeIn(ask.have, ask.from) }
            WatchSyncReason.RESCHEDULED, WatchSyncReason.PHRASES_CHANGED, WatchSyncReason.WATCH_APPEARED -> Unit
        }
        if (!steps.time(WatchAskStep.WATCHES) { link.reachable() }) return WatchSendResult.NO_WATCH
        val feed = steps.time(WatchAskStep.BUILD) { feedFor(ask.location) } ?: return WatchSendResult.NO_FEED
        val (json, fingerprint) = steps.time(WatchAskStep.ENCODE) { WatchFeedJson.encode(feed) to watchFeedFingerprint(feed) }
        val (keys, held) = steps.time(WatchAskStep.RECORD) { link.watchKeys().let { it to memory.read().holds(fingerprint, it) } }
        if (held) return WatchSendResult.UNCHANGED

        // A link that does not confirm by echo sends to one watch, the session's.
        val key = keys.singleOrNull()
        val first = steps.time(WatchAskStep.SEND) { deliver(json, standsFor = fingerprint, sent = fingerprint, key) }
        if (first != WatchSendResult.TOO_LARGE) return first
        val timesAlone = feed.copy(tasbeeh = emptyList())
        val (alone, aloneFingerprint) = steps.time(WatchAskStep.ENCODE) { WatchFeedJson.encode(timesAlone) to watchFeedFingerprint(timesAlone) }
        val second = steps.time(WatchAskStep.SEND) { deliver(alone, standsFor = fingerprint, sent = aloneFingerprint, key) }
        return if (second == WatchSendResult.TOO_LARGE) WatchSendResult.FAILED else second
    }

    /**
     * One send under the next number, written down first (item 1). A `SENT` on a link that does
     * not confirm by echo confirms that number for the watch [key] names, read before the call.
     */
    private suspend fun deliver(json: String, standsFor: String, sent: String, key: String?): WatchSendResult {
        val numbered = memory.read().sending(standsFor, sent)
        memory.write(numbered)
        val result = link.send(json, checkNotNull(numbered.lastDelivery))
        if (result == WatchSendResult.SENT && !link.confirmsDelivery) memory.write(numbered.handedOver(key))
        return result
    }

    /**
     * The watch [from] names echoed [echo]: taken into the record. On the iPhone only from the
     * session's watch (amendment A3). On a link that confirms by echo (Android) from any watch, for
     * the node it came from, a watch no longer nearby among them (Ruling 52 item 9): it is that
     * watch's own word on what it holds, so a wipe heard from afar is never lost, and its
     * confirmation counts only while it is nearby ([WatchFeedRecord.holds]).
     */
    private suspend fun takeIn(echo: String?, from: String?) {
        val record = memory.read()
        val keys = if (link.confirmsDelivery) setOf(from) else link.watchKeys()
        val after = record.echoed(echo?.let(WatchLinkContract::deliveryOf), from, keys)
        if (after != record) memory.write(after)
    }

    /**
     * [block]'s value, or null for any `Exception` it throws, a stray `CancellationException`
     * among them. Only the worker's own cancellation passes, through `ensureActive`.
     */
    private suspend inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        null
    }

    /**
     * The time of each step of one ask on [clock] (Ruling 70 item 1), in the order taken. Only that
     * ask's own coroutine uses it, one step at a time; the worker reads it once the ask has ended.
     */
    private class StepTimes(private val clock: TimeSource) {
        private val ended = mutableListOf<AskStep>()
        private var running: Pair<WatchAskStep, TimeMark>? = null

        /** [block]'s value, its step's time kept once it returns. */
        suspend fun <T> time(step: WatchAskStep, block: suspend () -> T): T {
            val since = clock.markNow()
            running = step to since
            val value = block()
            ended += AskStep(step, since.elapsedNow().inWholeMilliseconds)
            running = null
            return value
        }

        /** The steps that ended, then the one the ask ended in, if any (cut or thrown), unfinished, timed up to now. */
        fun taken(): List<AskStep> {
            val (step, since) = running ?: return ended.toList()
            return ended + AskStep(step, since.elapsedNow().inWholeMilliseconds, done = false)
        }
    }

    companion object {
        /**
         * The longest any caller waits on the watch side: the ask being served, then its own
         * (10 s). `sync`, `acknowledge` and `settle` bound their own waits by it, and every caller
         * wraps its call in it too.
         */
        val CALLER_BOUND: Duration = 10.seconds

        /** How long after the budget cut an ask it is asked again, once (Ruling 70 item 1). */
        val AGAIN_AFTER: Duration = 30.seconds
    }
}

/**
 * The watch side's last step in a background entry point whose process may stop once it returns:
 * `SystemEventReceiver`, `PrayerAlarmReceiver`, `NotificationTopUpWorker` and the iPhone's
 * `BackgroundRefreshBridge`. It is [WatchFeedSync.settle] bounded by [WatchFeedSync.CALLER_BOUND]
 * once more around it, so that not even a sync whose worker is gone can hold the entry point
 * longer, and false when the bound passes first. [sync] is read inside, so a container that
 * cannot be built fails this step alone, inside the caller's own `runCatching`.
 */
internal suspend fun settleTheWatch(sync: () -> WatchFeedSync): Boolean =
    withTimeoutOrNull(WatchFeedSync.CALLER_BOUND) { sync().settle() } ?: false
