package world.taqwa.app.di

import kotlinx.coroutines.flow.first
import world.taqwa.app.city.CityRepository
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.createLocationProvider
import world.taqwa.app.notifications.NotificationCoordinator
import world.taqwa.app.notifications.createNotificationScheduler
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.resources.Res
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.createDataStore
import kotlin.time.Clock

/** Manual construction. A DI framework earns its place when there is a graph worth managing. */
class AppContainer {
    val settingsRepository = SettingsRepository(createDataStore())
    val cityRepository = CityRepository { Res.readBytes("files/cities.csv").decodeToString() }
    val locationRepository = LocationRepository(createLocationProvider())
    val prayerTimesEngine = PrayerTimesEngine()
    val notificationCoordinator = NotificationCoordinator(
        engine = prayerTimesEngine,
        settingsRepository = settingsRepository,
        locationOf = { settingsRepository.location.first() },
        scheduler = createNotificationScheduler(),
        now = { Clock.System.now() },
    )
}

/**
 * One graph per process. DataStore refuses two live instances over the same file, so the
 * container must not be rebuilt when an Activity is recreated or a view controller re-created.
 * On Android this is first touched from `MainActivity.onCreate`, after `appContext` is assigned.
 */
val appContainer: AppContainer by lazy { AppContainer() }
