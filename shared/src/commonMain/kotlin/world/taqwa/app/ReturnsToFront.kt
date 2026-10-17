package world.taqwa.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

/**
 * Calls [onReturn] each time the app comes back to the front from the background: every entry
 * into STARTED but the first, which is the launch itself. On iOS, Compose Multiplatform's STARTED
 * is the app in front and RESUMED the app active, so Control Center, Notification Center and a
 * system alert, which only make the app inactive for a moment, are not returns.
 *
 * [onReturn] is given [requestsMade] as it was when the app left the front, so it can tell whether
 * a request arrived since, before the return or after it.
 */
internal suspend fun Lifecycle.onEachReturnToFront(
    requestsMade: () -> Long,
    onReturn: (requestsWhenItLeft: Long) -> Unit,
) {
    var requestsWhenItLeft: Long? = null
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        requestsWhenItLeft?.let(onReturn)
        try {
            awaitCancellation()
        } finally {
            requestsWhenItLeft = requestsMade()
        }
    }
}
