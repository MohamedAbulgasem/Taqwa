package world.taqwa.app.notifications

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.createDataStore
import kotlin.time.Clock

/**
 * The Kotlin side of `BGAppRefreshTask`. Swift owns task registration and the execution budget;
 * this only does the rescheduling work and reports whether it produced a usable result, so
 * `iOSApp.swift` can call `setTaskCompleted(success:)` honestly rather than always `true`.
 */
object BackgroundRefreshBridge {

    fun runBackgroundRefresh(): Boolean = runBlocking {
        val settingsRepository = SettingsRepository(createDataStore())
        val coordinator = NotificationCoordinator(
            engine = PrayerTimesEngine(),
            settingsRepository = settingsRepository,
            locationOf = { settingsRepository.location.first() },
            scheduler = createNotificationScheduler(),
            now = { Clock.System.now() },
        )
        val plan = coordinator.reschedule(RescheduleTrigger.BACKGROUND_REFRESH)
        // No location is not a failure — there is nothing to schedule and nothing went wrong.
        plan.isNotEmpty() || settingsRepository.location.first() == null
    }
}
