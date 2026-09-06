package world.taqwa.app.notifications

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
