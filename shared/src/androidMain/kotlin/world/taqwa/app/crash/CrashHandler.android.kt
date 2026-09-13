package world.taqwa.app.crash

private var installed = false

actual fun installCrashHandler(store: CrashLogStore) {
    if (installed) return
    installed = true
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        recordCrash(store, throwable, thread.name, deviceInfoOrUnknown())
        // The handler that was there before — the one that shows the system dialog and lets the
        // phone's own reporting see the crash — still runs. Without it the process would hang.
        previous?.uncaughtException(thread, throwable)
    }
}
