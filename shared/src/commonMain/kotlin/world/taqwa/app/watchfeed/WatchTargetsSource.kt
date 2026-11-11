package world.taqwa.app.watchfeed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Settings › Watch's view of the user's own watches (plan 5 Task 6): which watch each row acts on,
 * a row's tap started on its watch, and when the rows may have changed. Android's is Play services'
 * ([PlayWatchTargets]); the iPhone's answers [WatchTargets.NONE], since its one watch row is plan
 * 4's "Add to Apple Watch".
 */
internal interface WatchTargetsSource {
    /** The rows' watches now. It may throw, or take as long as Play services does: [loadWatchTargets] bounds it. */
    suspend fun targets(): WatchTargets

    /**
     * Starts [action]'s intent on [target]'s watch: true once the watch took it, false when it did
     * not. It may wait as long as the watch's companion does: [openOnWatch] bounds the row's wait,
     * never this call.
     *
     * It runs as [WatchRequests]' request, on no thread of its own: it starts on the tap's thread,
     * and after its first suspension it may resume on any thread, where its answer came (a binder
     * thread for [PlayWatchTargets], whose `RemoteActivityHelper` answers there). So past that
     * point it must not touch UI state (Ruling 74 item 13): the row takes the answer on its own
     * thread.
     */
    suspend fun open(target: WatchTarget, action: WatchAction): Boolean

    /**
     * Says the rows' watches may have changed, each time the watch side says so, for as long as it
     * is collected: collecting it starts listening, and ending the collection stops it. Settings
     * collects it while it is in front and reads the rows again at each ([loadWatchTargets]), so a
     * watch that gets Taqwa or takes its face while Settings is open changes its rows in place (the
     * owner's report of 9 November). It reads nothing itself, and a source that cannot listen ends
     * at once, or with its throw, which Settings drops. Android's says when either capability
     * changes, and once when its listener is in place ([PlayWatchTargets]); the iPhone's, and a
     * source by default, says nothing.
     */
    fun changes(): Flow<Unit> = emptyFlow()
}

internal expect fun watchTargetsSource(): WatchTargetsSource

/**
 * [source]'s targets, or [otherwise] when it throws or has not answered within [bound]: Settings
 * never waits on a watch. [otherwise] is [WatchTargets.NONE] when Settings comes to the front, so a
 * watch it cannot ask shows no row; while it stays there, it is the rows already shown, so a
 * reading that fails changes nothing, and only an answer changes the rows. Leaving Settings cancels
 * the load, and that cancellation passes through.
 */
internal suspend fun loadWatchTargets(
    source: WatchTargetsSource,
    bound: Duration = 3.seconds,
    otherwise: WatchTargets = WatchTargets.NONE,
): WatchTargets = try {
    withTimeoutOrNull(bound) { source.targets() } ?: otherwise
} catch (e: Exception) {
    // The caller's own cancellation passes through; a stray one from inside the source is its failure.
    currentCoroutineContext().ensureActive()
    otherwise
}

/**
 * What a row's tap came to (Ruling 72 item 3). The row says "Continue on your watch" for [OPENED]
 * and [QUEUED], and "Couldn’t reach your watch" for [UNREACHABLE] alone: no new words.
 */
internal enum class WatchOpen {
    /** The watch's companion took it: the watch opens the screen. */
    OPENED,

    /**
     * Not answered by the bound, and still on its way. Android holds `RemoteActivityHelper`'s
     * `REMOTE_INTENT` while the phone's Wear OS companion is cached (DEFERRED), and delivers it when
     * the companion wakes; the watch opens the screen then (P5.6's finding; 45 s at P5.12 Step 3).
     */
    QUEUED,

    /** Refused or failed, a throw, or no watch to start it on (the iPhone's source; no Play services). */
    UNREACHABLE,
}

/**
 * The rows' taps still on their way to a watch: at most one request for each watch and action
 * (Ruling 72 item 3). A tap while one is on its way joins it and sends nothing more, so a held
 * request delivered late never opens the watch's screen twice. Each request runs on [scope], never
 * on the row's, so it outlives the bound, the row and the screen that sent it, as the system's own
 * copy of it does. [process] is the app's.
 *
 * A request is on its way until it ends, however it ends, or until [expiry] after it was sent
 * ([EXPIRY]), whichever comes first, and then it is let go (Ruling 74 items 9 and 11): a tap from
 * then on sends anew. An expired request is not cancelled, since cancelling recalls nothing the
 * system holds; an answer it still gets reaches no row. [clock] times both the expiry and a tap's
 * bound, which count from the sending ([Request.age]).
 */
internal class WatchRequests(
    private val scope: CoroutineScope,
    private val clock: TimeSource = TimeSource.Monotonic,
    private val expiry: Duration = EXPIRY,
) {
    /**
     * One request on its way: the companion's [answer] to come, sent [age] ago, let go at [expiry].
     */
    class Request internal constructor(val answer: Deferred<Boolean>, private val sent: TimeMark, private val expiry: Duration) {
        /** How long ago it was sent. */
        val age: Duration get() = sent.elapsedNow()

        /** How long until it expires: zero or less once it has. */
        val left: Duration get() = expiry - age
    }

    private val lock = Mutex()
    private val onTheirWay = mutableMapOf<Pair<String, WatchAction>, Request>()

    /** [action]'s request to [target]'s watch: the one still on its way, or a new one through [source]. */
    suspend fun request(source: WatchTargetsSource, target: WatchTarget, action: WatchAction): Request =
        lock.withLock {
            val row = target.nodeId to action
            onTheirWay[row]?.takeIf { it.answer.isActive && it.left.isPositive() } ?: send(row, source, target, action)
        }

    /** The rows (watch and action) with a request on its way now. Only the tests read it. */
    suspend fun rowsOnTheirWay(): Set<Pair<String, WatchAction>> = lock.withLock { onTheirWay.keys.toSet() }

    /** A new request for [row], held until it ends or expires; called with [lock] held. */
    private fun send(row: Pair<String, WatchAction>, source: WatchTargetsSource, target: WatchTarget, action: WatchAction): Request {
        val sent = clock.markNow()
        val request = Request(scope.async { source.open(target, action) }, sent, expiry)
        onTheirWay[row] = request
        scope.launch {
            withTimeoutOrNull(request.left) { request.answer.join() }
            // Only this request: a tap after its expiry may have put a newer one in its place.
            lock.withLock { if (onTheirWay[row] === request) onTheirWay.remove(row) }
        }
        return request
    }

    companion object {
        /**
         * How long a request on its way is joined (Ruling 74 item 9): 2 minutes after it was sent.
         * Long enough to cover what was seen of a companion that holds a request: Android delivered
         * the `REMOTE_INTENT` it had DEFERRED 45 s after the tap (P5.12 Step 3), and a cached
         * companion answered within seconds of the 10 s bound (wave 7), so a second tap meanwhile
         * never opens the watch's screen twice. Short enough that a request the companion never
         * answers does not leave the row sending nothing for the rest of the process: after it a
         * tap sends anew, since a second `FaceActivity` is better than a dead row.
         */
        val EXPIRY: Duration = 2.minutes

        /**
         * How long a row's tap waits for the companion's answer, counted from the request's
         * sending, before it says "Continue on your watch" (Ruling 72 item 3). The one place the
         * app's 10 s is written (Ruling 76 item 8): [openOnWatch]'s default and the rows'
         * `WatchSection`'s.
         */
        val BOUND: Duration = 10.seconds

        /**
         * The app's, for as long as its process lives. Unconfined, so a request starts on the tap's
         * own thread, as the tap's did before, and goes on where its answer comes (any thread:
         * [WatchTargetsSource.open]). A `SupervisorJob`, so one request's failure, kept in its
         * `Deferred`, ends no other.
         */
        val process: WatchRequests = WatchRequests(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    }
}

/**
 * A row's tap (Ruling 72 item 3): [action] on [target]'s watch through [source], as the request
 * [requests] holds for that row (the one still on its way, or a new one), answered as soon as it is
 * known:
 * - within [bound] of the request's sending, the companion's answer: [WatchOpen.OPENED], or
 *   [WatchOpen.UNREACHABLE] for a refusal, a failure or a throw;
 * - otherwise [WatchOpen.QUEUED] at that bound, the request left on its way and never cancelled,
 *   then its answer if it ends before it expires ([WatchRequests.EXPIRY]); at the expiry the tap
 *   stops following it, with nothing more said, and the row takes a tap again.
 *   `RemoteActivityHelper` has no bound of its own: it answers when the companion does, which a
 *   frozen companion may never do.
 *
 * The bound counts from the request's sending, so a tap that joins one already past it says
 * [WatchOpen.QUEUED] at once (Ruling 74 item 10), and one that joins it earlier says it with the
 * first. Leaving Settings cancels the tap's wait, never its request.
 */
internal fun openOnWatch(
    source: WatchTargetsSource,
    target: WatchTarget,
    action: WatchAction,
    bound: Duration = WatchRequests.BOUND,
    requests: WatchRequests = WatchRequests.process,
): Flow<WatchOpen> = flow {
    val request = requests.request(source, target, action)
    val answer = withTimeoutOrNull(bound - request.age) { answerOf(request.answer) }
    if (answer != null) {
        emit(answer)
    } else {
        emit(WatchOpen.QUEUED)
        withTimeoutOrNull(request.left) { answerOf(request.answer) }?.let { emit(it) }
    }
}

/** How [request] ended. A stray `CancellationException` from inside it is its failure; the caller's own passes. */
private suspend fun answerOf(request: Deferred<Boolean>): WatchOpen = try {
    if (request.await()) WatchOpen.OPENED else WatchOpen.UNREACHABLE
} catch (e: Exception) {
    currentCoroutineContext().ensureActive()
    WatchOpen.UNREACHABLE
}
