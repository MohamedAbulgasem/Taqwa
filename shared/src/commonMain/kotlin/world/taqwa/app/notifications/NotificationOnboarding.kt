package world.taqwa.app.notifications

import androidx.compose.runtime.Composable

/**
 * Whether the OS notification permission is currently granted, and a plain suspend request for
 * it. On Android these do not by themselves raise the system dialog — `POST_NOTIFICATIONS` can
 * only be requested through an Activity-scoped launcher registered during composition, which is
 * what [rememberNotificationPermissionRequester] below is for. On iOS `requestNotificationPermission`
 * is the real ask: `UNUserNotificationCenter.requestAuthorizationWithOptions` needs no Activity
 * equivalent and can be called from any coroutine.
 */
expect suspend fun requestNotificationPermission(): Boolean
expect suspend fun isNotificationPermissionGranted(): Boolean

/**
 * The onboarding button's actual trigger. Mirrors [world.taqwa.app.location.rememberLocationPermissionRequester]:
 * the composable returns a function to invoke on tap, and the answer arrives later on [onResult] —
 * because Android's `POST_NOTIFICATIONS` dialog can only be raised through
 * `rememberLauncherForActivityResult`, which must be registered while composing, not from inside
 * a plain suspend function.
 */
@Composable
expect fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit

/**
 * The onboarding screen's "Enable notifications" button. Plan 1 Task 13 Step 5 left it as a
 * no-op that only advanced the flow, deliberately, because a granted permission with no
 * scheduler behind it is worse than not asking. This is the real behaviour: request the OS
 * permission, persist the user's choice either way, and only schedule anything if it was
 * granted.
 */
class NotificationOnboarding(
    private val requestSystemPermission: suspend () -> Boolean,
    private val setNotificationsEnabled: suspend (Boolean) -> Unit,
    private val rescheduleIfEnabled: suspend () -> Unit,
) {
    /** "Enable notifications". Returns whether the user ends up enabled. */
    suspend fun enable(): Boolean {
        val granted = requestSystemPermission()
        setNotificationsEnabled(granted)
        if (granted) rescheduleIfEnabled()
        return granted
    }

    /** "Not now" — declining is a first-class path, not a dead end, and never touches the
     * system permission dialog at all. */
    suspend fun declineForNow() {
        setNotificationsEnabled(false)
    }
}
