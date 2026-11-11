package world.taqwa.app.watchface

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * What the Swift app's `WatchFaceShare` does for "Add to Apple Watch" (plan 4 Task 10):
 * `WCSession` and ClockKit's `CLKWatchFaceLibrary` are reachable from Swift alone, as `WidgetCenter`
 * is, so the share is a hook the app installs at launch ([WatchFaceShareBridge.hook]), beside the
 * watch feed's session.
 *
 * Public, and in Swift a protocol in Booleans only: nothing of the face, the file or the error
 * crosses into Kotlin. Swift logs the error's code itself (`TaqwaWatch face …`).
 */
interface WatchFaceShareHook {
    /** The session is activated and paired, and `Taqwa.watchface` is in the app's bundle. */
    fun isAvailable(): Boolean

    /**
     * `CLKWatchFaceLibrary().addWatchFace(at:)` on the bundled file; [done] with whether its
     * completion came without an error, on the main thread.
     */
    fun add(done: (Boolean) -> Unit)
}

/**
 * The hook, and the session's word that the offer may have changed. [hook] is set from
 * `iOSApp.init`; null until then, and always in the widget extension, which offers nothing.
 */
object WatchFaceShareBridge {
    var hook: WatchFaceShareHook? = null

    /**
     * Swift calls it from `TaqwaWatchSession`'s delegate each time the session's own answer may
     * have changed: its activation completed, it became inactive or was deactivated (another watch
     * chosen), or the watch's state changed (`isPaired`, `isWatchAppInstalled`). It is said to
     * Settings if it is listening ([WatchFaceOffer.changes]), and kept for no one otherwise. On
     * WatchConnectivity's own queue; it never waits and never throws.
     */
    fun sessionChanged() {
        sessionChanges.tryEmit(Unit)
    }

    /** Each [sessionChanged], to whoever collects it then; never more than one waiting for a slow one. */
    internal val sessionChanges: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

/**
 * The hook's two answers as the row reads them. No hook, a hook that throws, and a hook that cannot
 * tell are no offer, and an add that cannot be asked fails at once. ClockKit's completion is not
 * promised to come once, so only its first answer reaches the row. Its changes are the session's
 * word through the bridge ([WatchFaceShareBridge.sessionChanged]).
 */
actual fun createWatchFaceOffer(): WatchFaceOffer = object : WatchFaceOffer {
    override fun available(): Boolean =
        runCatching { WatchFaceShareBridge.hook?.isAvailable() == true }.getOrDefault(false)

    override fun changes(): Flow<Unit> = WatchFaceShareBridge.sessionChanges

    override fun add(onDone: (WatchFaceResult) -> Unit) {
        var answered = false
        fun answer(result: WatchFaceResult) {
            if (answered) return
            answered = true
            onDone(result)
        }
        val hook = WatchFaceShareBridge.hook ?: return answer(WatchFaceResult.FAILED)
        runCatching {
            hook.add { added -> answer(if (added) WatchFaceResult.ADDED else WatchFaceResult.FAILED) }
        }.onFailure { answer(WatchFaceResult.FAILED) }
    }
}
