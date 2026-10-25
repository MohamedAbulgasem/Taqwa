package world.taqwa.app.crash

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook

private var installed = false

@OptIn(ExperimentalNativeApi::class)
actual fun installCrashHandler(store: CrashLogStore) {
    if (installed) return
    installed = true
    setUnhandledExceptionHook { throwable ->
        recordCrash(store, throwable, "main", deviceInfoOrUnknown())
        // An exception that leaves Kotlin into Objective-C or Swift ends the process once the hook
        // returns. An uncaught coroutine throw does not: processUnhandledException calls the hook
        // and returns, and the process carries on, so a scope that can throw needs a handler of its
        // own, or the next launch reports a crash that never happened (SurahDownloader.ios.kt).
    }
}
