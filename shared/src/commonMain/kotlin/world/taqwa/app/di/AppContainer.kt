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
import world.taqwa.app.recitation.createManifestProvider
import world.taqwa.app.recitation.createRecitationLibrary
import world.taqwa.app.recitation.createRecitationPaths
import world.taqwa.app.resources.Res
import world.taqwa.app.settings.BookmarkStore
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.TasbeehStore
import world.taqwa.app.settings.createDataStore
import world.taqwa.app.widget.createWidgetPinRequester
import world.taqwa.app.widget.createWidgetPlacementSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** Manual construction. A DI framework earns its place when there is a graph worth managing. */
class AppContainer {
    private val dataStore = createDataStore()
    val settingsRepository = SettingsRepository(dataStore)
    val bookmarkStore = BookmarkStore(dataStore)
    val tasbeehStore = TasbeehStore(dataStore)
    val cityRepository = CityRepository(
        loadCsv = { Res.readBytes("files/cities.csv").decodeToString() },
        // `Res.readBytes` throws rather than returning null for a file that is not bundled, and
        // the six name files cover only six of the languages the app can be read in.
        loadNames = { language ->
            try {
                Res.readBytes("files/city-names-$language.csv").decodeToString()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (missing: Exception) {
                null
            }
        },
    )
    val quranRepository by lazy { QuranRepository() }

    /**
     * Recitation (spec 3a). The paths are shared by both so the library and the manifest cache
     * agree on where `<files>/quran` is; both are `by lazy` because touching them asks the
     * platform for a directory (and, on iOS, creates it), which nothing that never opens the
     * Quran should pay for.
     */
    val recitationPaths by lazy { createRecitationPaths() }
    val recitationLibrary by lazy { createRecitationLibrary(dataStore, recitationPaths) }
    val manifestProvider by lazy { createManifestProvider(recitationPaths) }
    val locationRepository = LocationRepository(createLocationProvider())
    val prayerTimesEngine = PrayerTimesEngine()
    val widgetPinRequester = createWidgetPinRequester()
    // Stateless — it asks the platform on every call and caches nothing, because widgets are
    // added and removed outside the app.
    val widgetPlacementSource = createWidgetPlacementSource()
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
