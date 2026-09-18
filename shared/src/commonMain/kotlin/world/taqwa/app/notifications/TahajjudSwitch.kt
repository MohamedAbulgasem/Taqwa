package world.taqwa.app.notifications

import kotlinx.coroutines.flow.first
import world.taqwa.app.di.AppContainer

/**
 * Tahajjud on or off, and the plan rebuilt to match — what the settings screen's toggle does, for
 * the debug harnesses, which have no finger to toggle it with (the store-screenshot run switches
 * it on for the Notifications shot and off again afterwards). Nothing in a release build calls it.
 */
suspend fun AppContainer.setTahajjud(on: Boolean) {
    val current = settingsRepository.notificationSettings.first()
    if (current.tahajjud != on) settingsRepository.setNotificationSettings(current.copy(tahajjud = on))
    notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
}
