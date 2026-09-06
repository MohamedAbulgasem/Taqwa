package world.taqwa.app.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationOnboardingTest {

    @Test
    fun grantingPermissionEnablesNotificationsAndTriggersTheFirstSchedule() = runTest {
        var enabledValue: Boolean? = null
        var rescheduled = false
        val onboarding = NotificationOnboarding(
            requestSystemPermission = { true },
            setNotificationsEnabled = { enabledValue = it },
            rescheduleIfEnabled = { rescheduled = true },
        )
        assertTrue(onboarding.enable())
        assertEquals(true, enabledValue)
        assertTrue(rescheduled)
    }

    @Test
    fun aDeniedPermissionDisablesNotificationsAndNeverSchedules() = runTest {
        var enabledValue: Boolean? = null
        var rescheduled = false
        val onboarding = NotificationOnboarding(
            requestSystemPermission = { false },
            setNotificationsEnabled = { enabledValue = it },
            rescheduleIfEnabled = { rescheduled = true },
        )
        assertFalse(onboarding.enable())
        assertEquals(false, enabledValue)
        assertFalse(rescheduled)
    }

    @Test
    fun decliningForNowDisablesNotificationsWithoutAskingTheSystemAtAll() = runTest {
        var permissionAsked = false
        var enabledValue: Boolean? = null
        val onboarding = NotificationOnboarding(
            requestSystemPermission = { permissionAsked = true; true },
            setNotificationsEnabled = { enabledValue = it },
            rescheduleIfEnabled = { },
        )
        onboarding.declineForNow()
        assertFalse(permissionAsked)
        assertEquals(false, enabledValue)
    }
}
