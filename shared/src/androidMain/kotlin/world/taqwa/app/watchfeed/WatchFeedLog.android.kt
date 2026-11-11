package world.taqwa.app.watchfeed

import android.content.pm.ApplicationInfo
import android.util.Log
import world.taqwa.app.settings.appContext

/** The phone app's own `FLAG_DEBUGGABLE`: set by its debug build, never by a release build. */
internal actual fun isDebugBuild(): Boolean = debuggable(appContext.applicationInfo.flags)

/** Whether [flags], an `ApplicationInfo`'s, mark a debuggable application. */
internal fun debuggable(flags: Int): Boolean = flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** `log.tag.TaqwaWatch` at DEBUG or below, read at each call (`adb shell setprop log.tag.TaqwaWatch DEBUG`). */
internal actual fun stepsTagEnabled(): Boolean = Log.isLoggable(WATCH_LOG_TAG, Log.DEBUG)
