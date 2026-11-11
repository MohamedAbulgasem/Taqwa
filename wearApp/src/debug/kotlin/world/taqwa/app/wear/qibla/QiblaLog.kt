package world.taqwa.app.wear.qibla

import android.util.Log

/**
 * Debug builds only (the release build's `QiblaLog` writes nothing): the Qibla page's layout lines,
 * which name the detail it draws, for the emulator's checks:
 *
 *     adb -s emulator-5558 logcat -s TaqwaWearQibla
 *
 * The wrist compass's own line is every build's, on request ([qiblaLogOn]): a debug build is one
 * that always asks ([IN_DEBUG_BUILD]).
 */
internal object QiblaLog {
    const val TAG = QIBLA_TAG

    /** A debug build: the compass logs its line without the tag enabled ([qiblaLogOn]). */
    const val IN_DEBUG_BUILD = true

    inline fun line(text: () -> String) {
        Log.d(TAG, text())
    }
}
