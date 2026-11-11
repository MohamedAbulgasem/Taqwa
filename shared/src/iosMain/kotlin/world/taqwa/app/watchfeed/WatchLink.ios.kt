package world.taqwa.app.watchfeed

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import world.taqwa.app.di.appContainer
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchLinkContract
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration

/**
 * What the Swift app's `TaqwaWatchSession` does for the iPhone's link (plan 2 Task 8, Ruling 50):
 * `WCSession` is reachable from Swift alone, as `WidgetCenter` is, so the session is a hook the app
 * installs at launch ([WatchFeedBridge.hook]), the way `WidgetRefreshBridge` drives WidgetKit.
 *
 * Public, and in Swift a protocol with `Bool`, `String?` and `Int32` returns, a `Data` parameter
 * and a `String` one: no feed type crosses into Swift.
 */
interface WatchSessionHook {
    /** `activationState == .activated && isPaired && isWatchAppInstalled`, each logged. */
    fun isReady(): Boolean

    /**
     * Amendment A3's key for the watch the session now sends to: a random `UUID().uuidString` kept
     * in a file `taqwa-watch-key` inside `WCSession.default.watchDirectoryURL`, written there when
     * absent. The hook reads that URL only when `activationState == .activated` (Ruling 52 item 5),
     * and answers null otherwise or when the URL is nil.
     *
     * What Apple documents, which is all this relies on: the URL is "undefined and should not be
     * used" while the session is `notActivated`; it is a directory "specific to the currently
     * paired and active Apple Watch", and the URL "changes when the active Apple Watch changes";
     * "If the user uninstalls your app or unpairs their Apple Watch, iOS deletes this directory and
     * its contents"; and "If there is no paired watch, the value of this property is nil". The SDK
     * header (`WCSession.h`) adds: "If the watch app is not installed for the selected Watch the
     * value will be nil", and the directory "will be deleted upon next launch if the watch app is
     * uninstalled for the selected Watch, or that Watch is unpaired". So the key names one watch
     * and one install of its app.
     */
    fun watchKey(): String?

    /**
     * Hands [feed], the JSON's UTF-8 bytes, to `updateApplicationContext` under
     * [WatchFeedBridge.contextFeedKey], with [delivery] (`WatchDelivery.text`) under
     * [WatchFeedBridge.contextDeliveryKey], after checking the session is still ready. Answers
     * [WatchFeedBridge.sendQueued], [WatchFeedBridge.sendFailed] (the check, or any error) or
     * [WatchFeedBridge.sendTooLarge] (`WCError.payloadTooLarge`).
     */
    fun send(feed: NSData, delivery: String): Int
}

/**
 * The iPhone app's way into the watch feed, in strings, Booleans and `Int`s only, so that Swift
 * names no feed type and no key of its own (plan 2 Task 8).
 *
 * Swift calls it from `TaqwaWatchSession`'s `WCSessionDelegate` methods, on WatchConnectivity's
 * own queue. None of its calls waits there for the sync, the feed or the link (Ruling 52 item 6):
 * each puts its ask on the sync's queue and returns. What does run on that queue, briefly: at the
 * first call after a cold start, the app's container is built (`appContainer` is lazy); and a
 * handshake asks UIKit for background time and reads the watch's key file, under the session's
 * key lock, which the sync's own key read may hold for as long as a small file's read or write.
 * None of them throws: a Kotlin exception that reached Swift would end the app.
 */
object WatchFeedBridge {
    /**
     * Set from Swift at launch, before the session is activated. Null until then, and on a phone
     * without WatchConnectivity (an iPad).
     */
    @Volatile
    var hook: WatchSessionHook? = null

    /** The application context's key for the feed's UTF-8 bytes: the contract's. */
    val contextFeedKey: String = WatchLinkContract.CONTEXT_FEED_KEY

    /** The application context's key for the feed's delivery: the contract's. */
    val contextDeliveryKey: String = WatchLinkContract.CONTEXT_DELIVERY_KEY

    /** The watch's handshake message's key, holding its echo: the contract's. */
    val refreshMessageKey: String = WatchLinkContract.REFRESH_MESSAGE_KEY

    /** The key of the phone's reply to the handshake: the contract's. */
    val refreshReplyKey: String = WatchLinkContract.REFRESH_REPLY_KEY

    /** [WatchSessionHook.send]'s answer: the context was handed to the system. */
    val sendQueued: Int = 0

    /** [WatchSessionHook.send]'s answer: nothing was handed over (not ready, or any error). */
    val sendFailed: Int = 1

    /** [WatchSessionHook.send]'s answer: `WCError.payloadTooLarge`. */
    val sendTooLarge: Int = 2

    /**
     * Where the handshake waits for the sync's answer, once its ask is queued: off
     * WatchConnectivity's queue, on the default dispatcher. Never cancelled. The handler for the
     * same reason as the container's watch scope: a supervisor alone does not stop an uncaught
     * throw reaching the process.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

    private val calls = WatchBridgeCalls(sync = { appContainer.watchFeedSync }, hook = { hook }, scope = scope, time = UIKitHandshakeTime)

    /**
     * The watch's handshake, [have] being its echo (the contract's `echoOf` what it holds), from
     * `didReceiveMessage`. Its ask is on the sync's queue when this returns, in the order
     * WatchConnectivity delivered it (Ruling 53 item 4a), and [reply] is called once, within the
     * sync's caller bound (10 s): true when the watch has, or is being given, the current feed.
     * Until then the app holds UIKit background time, so an app the handshake woke in the
     * background is not suspended before it answers (Ruling 53 item 4b). In Swift, [reply] is
     * the block that answers WatchConnectivity's reply handler, which may be called on any thread.
     */
    fun refreshRequested(have: String, reply: (Boolean) -> Unit) {
        calls.refreshRequested(have, reply)
    }

    /**
     * How the Swift session's log lines name [delivery] (a `WatchDelivery.text`, or the watch's
     * echo): its number and fingerprint alone, `none` or `unreadable`, never its origin (Ruling 52
     * item 4).
     */
    fun logged(delivery: String): String = loggedDelivery(delivery)

    /** The session activated, at every launch: the damped ask (the Lead's call 1). Never waits. */
    fun watchActivated() {
        calls.watchActivated()
    }

    /** The watch app installed or removed, or another watch paired: forgets first. Never waits. */
    fun watchStateChanged() {
        calls.watchStateChanged()
    }
}

/**
 * [WatchFeedBridge]'s three calls over [sync], [hook] and [time], apart from the app's container
 * and UIKit, so that a test can hold them over a sync of its own. The handshake waits for the
 * sync's answer on [scope].
 */
internal class WatchBridgeCalls(
    private val sync: () -> WatchFeedSync,
    private val hook: () -> WatchSessionHook?,
    private val scope: CoroutineScope,
    private val time: HandshakeTime,
    private val bound: Duration = WatchFeedSync.CALLER_BOUND,
) {
    /**
     * First the background time is asked for ([time], Ruling 53 item 4b), then the key of the
     * watch the message came from is read, as it arrives (amendment A3). Then the handshake is
     * served by a coroutine on [scope], bounded by [bound], started `UNDISPATCHED`: its ask goes on
     * the sync's queue on the caller's thread, before this returns (the queue's `trySend` never
     * suspends or blocks), so the sync serves WatchConnectivity's messages in the order they came
     * (Ruling 53 item 4a). The wait for the answer is off the caller's thread, and there is no
     * `runBlocking` (Ruling 52 item 6).
     *
     * [reply] is called exactly once: true for `SENT` or `UNCHANGED`; false for anything else, a
     * bound passed, any throw, [scope] gone, or the system taking the background time back. Most
     * often that is from the coroutine, later; on the caller's thread when the ask is answered
     * before it suspends (a key or a container that throws, a sync whose worker has ended); and
     * on the main thread from the time's expiration handler. The time ends right after it. The
     * watch asks again at its next chance.
     */
    fun refreshRequested(have: String, reply: (Boolean) -> Unit) {
        val answer = HandshakeAnswer(reply, time)
        answer.begin()
        try {
            val from = hook()?.watchKey()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = try {
                    withTimeoutOrNull(bound) { sync().sync(WatchSyncReason.WATCH_ASKED, have = have, from = from) }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    null
                }
                answer(result == WatchSendResult.SENT || result == WatchSendResult.UNCHANGED)
            }.invokeOnCompletion { answer(false) }
        } catch (e: Exception) {
            answer(false)
        }
    }

    /** `WATCH_APPEARED`, only asked. */
    fun watchActivated() {
        ask(WatchSyncReason.WATCH_APPEARED)
    }

    /** `WATCH_CHANGED`, only asked. */
    fun watchStateChanged() {
        ask(WatchSyncReason.WATCH_CHANGED)
    }

    private fun ask(reason: WatchSyncReason) {
        try {
            sync().request(reason)
        } catch (e: Exception) {
            // A container that cannot be built costs the watch this ask, never the app.
        }
    }
}

/**
 * The background time a handshake holds until it is answered (Ruling 53 item 4b): the watch's
 * message can wake the iPhone app in the background, and an app with no background time may be
 * suspended before it answers.
 */
internal interface HandshakeTime {
    /**
     * Asks for background time, or answers null when none is given. [expired] is called if the
     * system takes the time back first; it must end the time.
     */
    fun begin(expired: () -> Unit): ULong?

    /** Ends the time [begin] gave. */
    fun end(task: ULong)
}

/**
 * UIKit's background time. Apple: both calls "can be safely called on a non-main thread";
 * `beginBackgroundTask` "returns `UIBackgroundTaskInvalid` if running in the background isn't
 * possible", and the system calls the expiration handler "synchronously on the main thread",
 * "shortly before the app's remaining background time reaches 0", and also "If the system is
 * unable to grant the task assertion". "Failure to end the task explicitly will result in the
 * termination of the app."
 */
internal object UIKitHandshakeTime : HandshakeTime {
    private const val NAME = "TaqwaWatch handshake"

    override fun begin(expired: () -> Unit): ULong? {
        val task = UIApplication.sharedApplication.beginBackgroundTaskWithName(NAME, expirationHandler = expired)
        return if (task == UIBackgroundTaskInvalid) null else task
    }

    override fun end(task: ULong) {
        UIApplication.sharedApplication.endBackgroundTask(task)
    }
}

/**
 * One handshake's answer. [reply] is called at most once, whichever path reaches it first: the
 * sync's answer, the false of a coroutine that ended without one, or the false of the time's
 * expiration handler. So WatchConnectivity's reply handler is called once. The background time
 * [begin] got from [time] is ended right after that reply, once, even when the reply came before
 * `begin` returned (the expiration handler may run that early when the system cannot grant the
 * time).
 */
@OptIn(ExperimentalAtomicApi::class)
private class HandshakeAnswer(private val reply: (Boolean) -> Unit, private val time: HandshakeTime) {
    private val replied = AtomicBoolean(false)
    private val ended = AtomicBoolean(false)

    /** The time [begin] was given; null until then, and when none was. */
    private val task = AtomicReference<ULong?>(null)

    /** A `begin` that throws costs the handshake only its background time. */
    fun begin() {
        val given = try {
            time.begin { invoke(false) }
        } catch (e: Exception) {
            null
        } ?: return
        task.store(given)
        if (replied.load()) end()
    }

    operator fun invoke(queued: Boolean) {
        if (!replied.compareAndSet(false, true)) return
        try {
            reply(queued)
        } finally {
            end()
        }
    }

    private fun end() {
        val given = task.load() ?: return
        if (!ended.compareAndSet(false, true)) return
        try {
            time.end(given)
        } catch (e: Exception) {
            // Nothing more to do: the reply is given.
        }
    }
}

/**
 * The JSON's UTF-8 bytes as `NSData`. A property list keeps `Data` as its own bytes, where a
 * `String` holding any non-ASCII character would be stored in UTF-16, at twice the size.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun feedData(json: String): NSData {
    val bytes = json.encodeToByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
}

/**
 * The iPhone's link to the user's own Apple Watch: WatchConnectivity's application context, through
 * [hook] (plan 2 Task 8, Ruling 50).
 *
 * - It does not confirm by echo: the system keeps the latest context it was handed and delivers it
 *   when the watch can take it, and nothing older after it, so a `SENT` confirms its number for
 *   the watch it went to (Ruling 50 item 4). The iPhone's watch does not acknowledge.
 * - [reachable] is the session's own readiness, so an iPhone without a watch builds nothing.
 * - [send] is `NO_WATCH` without a hook or a ready session; otherwise the hook's answer:
 *   `sendQueued` is `SENT`, `sendTooLarge` is `TOO_LARGE` (the sync then sends the times without
 *   the phrases) and anything else, or a throw, is `FAILED`.
 */
internal class IosWatchLink(private val hook: () -> WatchSessionHook? = { WatchFeedBridge.hook }) : WatchLink {

    override val confirmsDelivery: Boolean = false

    override suspend fun reachable(): Boolean = hook()?.isReady() == true

    override suspend fun watchKey(): String? = hook()?.watchKey()

    override suspend fun send(json: String, delivery: WatchDelivery): WatchSendResult {
        val session = hook() ?: return WatchSendResult.NO_WATCH
        val answer = try {
            if (!session.isReady()) return WatchSendResult.NO_WATCH
            session.send(feedData(json), delivery.text)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return WatchSendResult.FAILED
        }
        return when (answer) {
            WatchFeedBridge.sendQueued -> WatchSendResult.SENT
            WatchFeedBridge.sendTooLarge -> WatchSendResult.TOO_LARGE
            else -> WatchSendResult.FAILED
        }
    }
}

internal actual fun createWatchLink(): WatchLink = IosWatchLink()
