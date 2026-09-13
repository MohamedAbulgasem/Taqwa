package world.taqwa.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import world.taqwa.app.audio.ClipPlayer
import world.taqwa.app.feature.recitation.RecitationController
import world.taqwa.app.feature.recitation.asPort
import world.taqwa.app.feature.recitation.asRecitationPort
import world.taqwa.app.city.CityRepository
import world.taqwa.app.location.LocationRefresher
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.createLocationProvider
import world.taqwa.app.notifications.NotificationCoordinator
import world.taqwa.app.notifications.createNotificationScheduler
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.quran.QuranRepository
import world.taqwa.app.recitation.ManifestRefresher
import world.taqwa.app.recitation.RecitationPlayer
import world.taqwa.app.recitation.createManifestProvider
import world.taqwa.app.recitation.createSurahDownloader
import world.taqwa.app.recitation.createRecitationLibrary
import world.taqwa.app.recitation.createRecitationPaths
import world.taqwa.app.recitation.recitationPreviewBytes
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

    // ── Recitation downloads (spec 3a §7, slice 3a task 2). One block, kept together. ──
    // Both are `by lazy` for the same reason as the three above: constructing the downloader
    // reaches for WorkManager on Android and builds a background URL session on iOS, and an
    // install that never opens the Quran should pay for neither.
    val surahDownloader by lazy {
        createSurahDownloader(recitationLibrary, manifestProvider, settingsRepository, quranRepository)
    }
    val manifestRefresher by lazy { ManifestRefresher(manifestProvider, dataStore) }
    // ── end recitation downloads ──────────────────────────────────────────────────────
    // Slice 3a task 3: the player. Lazy for a stronger reason than the three above — building it
    // is free, but its first `load` binds a MediaSessionService on Android and claims the audio
    // session on iOS, and nothing that never plays a recitation should pay for either. One per
    // process, because a media session is a process-wide thing and two would fight over the
    // notification.
    val recitationPlayer by lazy { RecitationPlayer(recitationLibrary) }

    /**
     * Slice 3a task 4a: the one place the recitation surface's decisions live. Its own scope,
     * because it outlives every composition — the bar goes on playing while the reader walks to
     * Settings — and `Main.immediate`, because the player it drives is a `MediaController` on
     * Android, which must be touched from the main thread.
     *
     * Not lazy in practice: `App` collects its state from the first frame, since the player bar
     * is a scaffold overlay that has to be able to appear on any Quran screen. That does make
     * every launch build the downloader, which on Android asks WorkManager for its instance —
     * cheap, and WorkManager is already initialised by its own start-up provider by then.
     */
    val recitationController by lazy {
        RecitationController(
            manifests = manifestProvider::current,
            library = recitationLibrary.asPort(),
            downloader = surahDownloader.asPort(),
            player = recitationPlayer.asPort(),
            settings = settingsRepository.asRecitationPort(),
            quran = quranRepository,
            clips = ClipPlayer().asPort(),
            previewBytes = ::recitationPreviewBytes,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

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
