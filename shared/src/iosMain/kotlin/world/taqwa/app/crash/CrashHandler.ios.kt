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
        // Kotlin/Native terminates the process once the hook returns; nothing else to do here.
    }
}
