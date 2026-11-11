package world.taqwa.app.di

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.audio.ClipPlayer
import world.taqwa.app.feature.recitation.RecitationController
import world.taqwa.app.feature.recitation.asPort
import world.taqwa.app.feature.recitation.asRecitationPort
import world.taqwa.app.feature.recitation.readAloudPort
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.location.LocationRefresher
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.createLocationProvider
import world.taqwa.app.notifications.NotificationCoordinator
import world.taqwa.app.notifications.adhkarNotificationId
import world.taqwa.app.notifications.createNotificationScheduler
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.quran.QuranRepository
import world.taqwa.app.recitation.ManifestRefresher
import world.taqwa.app.recitation.RecitationEngagement
import world.taqwa.app.recitation.RecitationPlayer
import world.taqwa.app.recitation.SpeechVoices
import world.taqwa.app.recitation.adhkarClipBytes
import world.taqwa.app.recitation.createManifestProvider
import world.taqwa.app.recitation.createSpeechVoices
import world.taqwa.app.recitation.createSurahDownloader
import world.taqwa.app.recitation.createRecitationLibrary
import world.taqwa.app.recitation.createRecitationPaths
import world.taqwa.app.recitation.recitationPreviewBytes
import world.taqwa.app.resources.Res
import world.taqwa.app.settings.AdhkarStore
import world.taqwa.app.settings.BookmarkStore
import world.taqwa.app.settings.DataStoreAdhkarStore
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.TasbeehStore
import world.taqwa.app.settings.createDataStore
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watchfeed.DataStoreWatchFeedMemory
import world.taqwa.app.watchfeed.WATCH_LOG_TAG
import world.taqwa.app.watchfeed.WatchFeedBuilder
import world.taqwa.app.watchfeed.WatchFeedInputs
import world.taqwa.app.watchfeed.WatchFeedMemory
import world.taqwa.app.watchfeed.WatchFeedSync
import world.taqwa.app.watchfeed.WatchLink
import world.taqwa.app.watchfeed.WatchSyncReason
import world.taqwa.app.watchfeed.askStepsLine
import world.taqwa.app.watchfeed.createWatchLink
import world.taqwa.app.watchfeed.stepsLogged
import world.taqwa.app.watchfeed.watchCityName
import world.taqwa.app.watchfeed.watchScreenWords
import world.taqwa.app.watchfeed.watchSpoken
import world.taqwa.app.watchfeed.watchUiLanguage
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
    val adhkarStore: AdhkarStore by lazy { DataStoreAdhkarStore(dataStore) }
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
    // Privacy spec §2: the daily catalogue fetch waits until the reader has used recitation, so
    // an install that never opens it never opens a socket.
    val recitationEngagement by lazy { RecitationEngagement(dataStore, recitationLibrary) }
    val manifestRefresher by lazy {
        ManifestRefresher(manifestProvider, dataStore, engaged = { recitationEngagement.isEngaged() })
    }
    // ── end recitation downloads ──────────────────────────────────────────────────────
    // Slice 3a task 3: the player. Lazy for a stronger reason than the three above — building it
    // is free, but its first `load` binds a MediaSessionService on Android and claims the audio
    // session on iOS, and nothing that never plays a recitation should pay for either. One per
    // process, because a media session is a process-wide thing and two would fight over the
    // notification.
    val recitationPlayer by lazy { RecitationPlayer(recitationLibrary) }

    /** Read-aloud spec §4: the phone's own offline voices, queried lazily like the player above. */
    val speechVoices: SpeechVoices by lazy { createSpeechVoices() }

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
            adhkarClipBytes = ::adhkarClipBytes,
            // The handler, not just the SupervisorJob: a supervisor stops a failed child taking
            // its siblings, but an uncaught throw still reaches the default handler and the
            // process with it.
            scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> },
            ),
            // Swallowed for the same reason the refresh is: a flag that failed to write is
            // retried by the next tap, and a tap to play must never be the thing that takes the
            // process down. Cancellation still propagates.
            markEngaged = {
                try {
                    recitationEngagement.mark()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    // Next tap.
                }
            },
            refreshCatalogue = {
                withContext(Dispatchers.Default) { runCatching { manifestRefresher.refreshIfStale() } }
            },
            readAloud = readAloudPort(settingsRepository, speechVoices),
        )
    }

    val locationRepository = LocationRepository(createLocationProvider())
    val prayerTimesEngine = PrayerTimesEngine()
    val widgetPinRequester = createWidgetPinRequester()
    // Stateless — it asks the platform on every call and caches nothing, because widgets are
    // added and removed outside the app.
    val widgetPlacementSource = createWidgetPlacementSource()
    val locationRefresher = LocationRefresher(locationRepository, cityRepository, settingsRepository)

    // ── The watch feed (plan 2 Task 4). Declared before the coordinator, which asks it. ──
    /**
     * The watch side's own process scope: the sync's one worker lives here, on the default
     * dispatcher and never the caller's. The handler for the same reason as the recitation
     * controller's: a supervisor alone does not stop an uncaught throw reaching the process.
     */
    internal val watchFeedScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> },
    )

    /**
     * The phone's one record of its watch (Ruling 50): this install's origin, its delivery numbers
     * and what the watch has echoed, in the settings store.
     */
    internal val watchFeedMemory: WatchFeedMemory = DataStoreWatchFeedMemory(dataStore)

    /**
     * The platform's link to the user's own watch: WatchConnectivity on the iPhone (plan 2 Task 8);
     * on Android the Wear Data Layer's messages, to watches directly connected at the send (Task 5).
     */
    internal val watchLink: WatchLink = createWatchLink()

    /**
     * The feed for one ask, built on the sync's worker inside its budget: the location planned (or
     * the stored one), the clock and the prayer settings, then the interface language, the screen
     * reader's words and the words of the watch's screens read one after the other, so that all
     * three come from one resource environment. The format is a fresh `createPlatformFormat` for each ask, as the coordinator
     * builds its own: an Android format's `NumberFormat` is not shared across threads
     * (`PlatformFormat.android.kt`). Null with no location: nothing goes.
     */
    internal val watchFeedFor: suspend (GeoLocation?) -> WatchFeed? = feed@{ planned ->
        val location = planned ?: settingsRepository.location.first() ?: return@feed null
        val clockFormat = settingsRepository.clockFormat.first()
        val prayerSettings = settingsRepository.prayerSettings.first()
        val language = watchUiLanguage()
        val spoken = watchSpoken()
        val screens = watchScreenWords(language)
        val format = createPlatformFormat(clockFormat)
        WatchFeedBuilder.build(
            WatchFeedInputs(
                location = location,
                city = watchCityName(location, format, cityRepository),
                language = language,
                spoken = spoken,
                screens = screens,
                prayerSettings = prayerSettings,
                format = format,
                clockFormat = clockFormat,
                phrases = tasbeehStore.customPresets.first(),
                now = Clock.System.now(),
                timesFor = { date -> prayerTimesEngine.timesFor(location, date, prayerSettings) },
            ),
        )
    }

    /**
     * Asked after every reschedule, every phrase change and every return of the watch. Its one
     * line per ask is a plain `println`, as `:shared` has no logger of its own (Ruling 48): on
     * Android it reaches logcat as `System.out`, which plan 2 Task 7's pass reads. It names the
     * reason, the result and the milliseconds, never the place or the person. A debug build, or a
     * signed one after `adb shell setprop log.tag.TaqwaWatch DEBUG` (`stepsLogged`, read at each
     * ask), adds a second line, the time of each of the ask's steps (Ruling 70 item 1), so a slow
     * step can be named on P5.13's candidate too: `askStepsLine`, which names no place, person or
     * delivery either.
     */
    internal val watchFeedSync = WatchFeedSync(
        feedFor = watchFeedFor,
        link = watchLink,
        memory = watchFeedMemory,
        scope = watchFeedScope,
        report = { reason, result, millis -> println("$WATCH_LOG_TAG $reason $result ${millis}ms") },
        timed = { if (stepsLogged()) println(askStepsLine(it)) },
    )
    // ── end watch feed ──────────────────────────────────────────────────────────────

    /** The one platform scheduler: the coordinator arms whole plans with it, and a finished set of
     * adhkar withdraws its own reminder through it (adhkar spec §5) without a reschedule. */
    val notificationScheduler = createNotificationScheduler()
    val notificationCoordinator = NotificationCoordinator(
        engine = prayerTimesEngine,
        settingsRepository = settingsRepository,
        locationOf = { settingsRepository.location.first() },
        scheduler = notificationScheduler,
        now = { Clock.System.now() },
        locationFor = { locationRefresher.refreshFor(it) },
        // A set said in full has no reminder armed for it again (adhkar spec §5): its id, from
        // the date the set was last finished. An old date names a day the plan no longer reaches.
        doneAdhkar = {
            AdhkarSet.entries.mapNotNull { set -> adhkarStore.done(set).first()?.let { adhkarNotificationId(set, it) } }.toSet()
        },
        // Only asks: the sync builds and sends on its own worker, never inside the lock.
        afterPlan = { watchFeedSync.request(WatchSyncReason.RESCHEDULED, it) },
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
