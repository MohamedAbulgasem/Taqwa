package world.taqwa.app.crash

import okio.Path.Companion.toPath
import world.taqwa.app.settings.dataStoreDirectory
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One store for the process, beside the settings file: the entry points install the hook on it
 * and the root composable and the About screen read from it.
 */
val crashLogStore: CrashLogStore by lazy { CrashLogStore(dataStoreDirectory().toPath()) }

/**
 * Installs the platform's uncaught-exception hook so a crash writes [store] before the process
 * dies (crash spec §2). Android chains to the handler that was there; Kotlin/Native terminates
 * after the hook returns. Safe to call more than once: only the first call installs.
 */
expect fun installCrashHandler(store: CrashLogStore)

/**
 * What both hooks do. Wrapped whole in runCatching: this runs inside a crash, and a second
 * exception here would replace the one being recorded with a much less useful one.
 */
fun recordCrash(
    store: CrashLogStore,
    throwable: Throwable,
    threadName: String,
    info: DeviceInfo,
    now: Instant = Clock.System.now(),
) {
    runCatching { store.write(CrashReportFormat.render(throwable, threadName, info, now)) }
}

/** The facts for a report, or the unknown-device placeholders if reading them is what failed. */
fun deviceInfoOrUnknown(): DeviceInfo = runCatching { deviceInfo() }.getOrElse { unknownDevice() }
