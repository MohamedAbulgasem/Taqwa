package world.taqwa.app.wear.link

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.wear.TaqwaWearApp

/** Debug builds only: what the debug nudge does before it nudges. */
internal object DebugNudge {

    /** Forgets when the last handshake went out (`refresh.asked`), so the daily one is due at once. */
    fun forgetLastAsk(filesDir: File) {
        File(filesDir, PhoneAsk.STAMP).delete()
    }
}

/**
 * Debug builds only: Task 7's daily handshake on demand. It forgets the last ask, then runs the
 * daily nudge ([DailyNudge.nudge]) under the same bound, and ends with a line `am` prints:
 *
 *     adb -s emulator-5558 shell am broadcast \
 *         -n world.taqwa.app/world.taqwa.app.wear.link.DebugNudgeReceiver -a world.taqwa.app.wear.DEBUG_NUDGE
 *
 * prints `nudged asked=true` when the handshake went out. It exists because the daily alarm's own
 * receiver is not exported, so a broadcast from `adb shell` (neither the app's uid nor system, on a
 * user build) is refused there; and because a handshake a minute earlier leaves `refresh.asked`
 * inside `RefreshPolicy.GAP_DAILY`, so the daily path would not ask.
 */
class DebugNudgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext as TaqwaWearApp
        val ordered = isOrderedBroadcast
        val pending = goAsync()
        app.scope.launch {
            var asked = false
            try {
                DebugNudge.forgetLastAsk(app.filesDir)
                asked = withTimeoutOrNull(PhoneAsk.BOUND) { DailyNudge.nudge(app, System.currentTimeMillis() / 1000) } == true
            } catch (_: Exception) {
                asked = false
            } finally {
                if (ordered) pending.setResult(Activity.RESULT_OK, "nudged asked=$asked", null)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "world.taqwa.app.wear.DEBUG_NUDGE"
    }
}
