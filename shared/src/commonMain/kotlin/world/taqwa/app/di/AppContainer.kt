package world.taqwa.app.di

import kotlinx.coroutines.flow.first
import world.taqwa.app.city.CityRepository
import world.taqwa.app.location.LocationRefresher
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.createLocationProvider
import world.taqwa.app.notifications.NotificationCoordinator
import world.taqwa.app.notifications.createNotificationScheduler
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.quran.QuranRepository
import world.taqwa.app.resources.Res
import world.taqwa.app.settings.BookmarkStore
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.createDataStore
import world.taqwa.app.widget.createWidgetPinRequester
import kotlin.time.Clock

/** Manual construction. A DI framework earns its place when there is a graph worth managing. */
class AppContainer {
    private val dataStore = createDataStore()
    val settingsRepository = SettingsRepository(dataStore)
    val bookmarkStore = BookmarkStore(dataStore)
    val cityRepository = CityRepository { Res.readBytes("files/cities.csv").decodeToString() }
    val quranRepository by lazy { QuranRepository() }
    val locationRepository = LocationRepository(createLocationProvider())
    val prayerTimesEngine = PrayerTimesEngine()
    val widgetPinRequester = createWidgetPinRequester()
    val locationRefresher = LocationRefresher(locationRepository, cityRepository, settingsRepository)
    val notificationCoordinator = NotificationCoordinator(
        engine = prayerTimesEngine,
        settingsRepository = settingsRepository,
        locationOf = { settingsRepository.location.first() },
        scheduler = createNotificationScheduler(),
        now = { Clock.System.now() },
        locationFor = { locationRefresher.refreshFor(it) },
    )
}

/**
 * One graph per process. DataStore refuses two live instances over the same file; that risk is
 * now closed in `createDataStore()` itself, which hands out one process-wide, lazily created
 * instance to every caller (this container, `SystemEventReceiver`, `BackgroundRefreshBridge`).
 * The container still should not be rebuilt when an Activity is recreated or a view controller
 * re-created — there is no reason to pay for a second `AppContainer` graph — but doing so would
 * no longer crash on the DataStore file itself. On Android this is first touched from
 * `MainActivity.onCreate`, after `appContext` is assigned.
 */
val appContainer: AppContainer by lazy { AppContainer() }
