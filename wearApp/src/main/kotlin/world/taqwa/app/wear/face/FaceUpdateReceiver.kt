package world.taqwa.app.wear.face

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.wear.TaqwaWearApp

/**
 * After every update of the app (MY_PACKAGE_REPLACED): Google says a bundled face "isn't updated
 * should a newer version be bundled with any update", so the app updates the face in its slot
 * ([refreshAfterUpdate]), within [goAsync]'s 10 s, and tells the phone where it stands. The update
 * starts the process too, whose start runs the same; the process's one FacePush makes the second a
 * no-op.
 */
class FaceUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as TaqwaWearApp
        val pending = goAsync()
        app.scope.launch {
            try {
                withTimeoutOrNull(FACE_BOUND_MS) {
                    refreshAfterUpdate(FacePushes.of(app)) { state, spent -> FaceCapability.publish(app, state, spent) }
                }
            } catch (_: Exception) {
                // The next start, or the daily nudge, tries again.
            } finally {
                pending.finish()
            }
        }
    }
}

/** The bound on an update's refresh: goAsync's 10 s, with room to finish. */
internal const val FACE_BOUND_MS = 9_000L
