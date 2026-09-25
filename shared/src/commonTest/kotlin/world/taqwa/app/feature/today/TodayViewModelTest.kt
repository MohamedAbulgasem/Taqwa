package world.taqwa.app.feature.today

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.ResolvedCityName
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Counts writes rather than only recording them: the whole point of C5 is how *often* the mirror
 * is written, not what ends up in it. */
private class CountingKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    var writes = 0
        private set
    override fun putString(key: String, value: String) {
        writes++
        map[key] = value
    }
    override fun getString(key: String): String? = map[key]
}

/**
 * Counts every write the settings store takes, for the same reason [CountingKeyValueStore] counts
 * the mirror's: remembering the header's name must cost one write when the answer changes, not one
 * per tick of a screen that refreshes every second.
 */
private class CountingDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    var writes = 0
        private set
    override val data: Flow<Preferences> get() = delegate.data
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writes++
        return delegate.updateData(transform)
    }
}

/**
 * English in every respect but the language tag, which is the only thing the city-name lookup
 * reads. Delegating keeps the dates and digits in these tests readable while the header resolves
 * as it would for a reader in Arabic.
 */
private class ArabicPlatformFormat : PlatformFormat by EnglishPlatformFormat {
    override fun languageTag(): String = "ar-LY"
}

/**
 * A [TodayViewModel] whose clock is driven by hand and whose two widget side effects — the mirror
 * write and the platform nudge — are counted instead of performed.
 *
 * `start()`'s own one-second loop is not used: it never terminates under `runTest`. Advancing
 * [now] by hand reproduces exactly what that loop does to `refresh()`.
 */
private class WidgetTrafficProbe(private val location: GeoLocation, private val repo: SettingsRepository) {
    val store = CountingKeyValueStore()
    var refreshes = 0
        private set
    var now: Instant = Instant.parse("2026-09-06T14:30:00Z")

    val viewModel = TodayViewModel(
        engine = PrayerTimesEngine(),
        settings = repo,
        locationOf = { location },
        now = { now },
        widgetStore = { store },
        widgetFormat = { EnglishPlatformFormat },
        onWidgetsChanged = { refreshes++ },
    )
}

class TodayViewModelTest {

    // Same absolute-path rule as SettingsRepositoryTest: DataStore's OkioStorage rejects relative
    // paths at runtime on iosSimulatorArm64Test. One file per test keeps the cases isolated, and
    // the file is removed first: it outlives the process, and a city name remembered by the last
    // run — or by the JVM run of this same suite, moments earlier — is exactly the stale state
    // the city-name tests exist to catch. The iOS run failed on precisely that until this did.
    // The store's writes run on the test's own scheduler (`scope = backgroundScope`), as Android's
    // DataStore testing guidance has it. On its default IO scope a write raced the virtual clock,
    // and a test waiting for the stored value could hang until runTest gave up (seen in CI).
    private fun TestScope.settings(name: String): SettingsRepository {
        val path = freshStorePath(name)
        return SettingsRepository(PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) { path })
    }

    private fun freshStorePath(name: String): Path {
        val path = "/tmp/taqwa-test-today-$name.preferences_pb".toPath()
        FileSystem.SYSTEM.delete(path, mustExist = false)
        return path
    }

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")

    /**
     * The view model is built with a fake location lookup and a frozen clock, so every case here
     * runs without a device, a GPS fix or a real wall clock. `refresh()` is awaited directly
     * rather than starting the one-second tick, which would never terminate under `runTest`.
     */
    private suspend fun TestScope.viewModel(
        store: String,
        location: GeoLocation?,
        now: Instant,
        hijriOffset: Int = 0,
    ): TodayViewModel {
        val repo = settings(store)
        repo.setPrayerSettings(PrayerSettings(hijriOffsetDays = hijriOffset))
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { location },
            now = { now },
        )
        vm.refresh()
        return vm
    }

    // Each case gets its own store file: DataStore refuses two live instances over one path,
    // and two tests sharing a name would collide inside a single test binary.
    private suspend fun TestScope.todayViewModelWithNoLocation() =
        viewModel("no-location", null, Instant.parse("2026-09-06T14:30:00Z"))

    private suspend fun TestScope.todayViewModelForLondon(
        store: String,
        now: Instant = Instant.parse("2026-09-06T14:30:00Z"),
        hijriOffset: Int = 0,
    ) = viewModel(store, london, now, hijriOffset)

    private suspend fun TestScope.todayViewModelForTromso(store: String, now: Instant) =
        viewModel(store, tromso, now)

    @Test
    fun withoutALocationTheScreenAsksForOneRatherThanShowingNothing() = runTest {
        val vm = todayViewModelWithNoLocation()
        assertEquals(TodayUiState.NeedsLocation, vm.state.first { it !is TodayUiState.Loading })
    }

    @Test
    fun withALocationTheScreenIsReadyAndNamesTheNextPrayer() = runTest {
        val vm = todayViewModelForLondon("ready", now = Instant.parse("2026-09-06T14:30:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals(5, ready.today.rows.size)
        assertTrue(ready.hijri.isNotBlank())
    }

    // Iteration 8: the Prayer screen shows both calendars on one line and carries a Qibla card,
    // so the state has to supply the Gregorian date in the location's zone and the geometry.
    @Test
    fun readyCarriesTheGregorianDateAndTheQiblaGeometry() = runTest {
        val vm = todayViewModelForLondon("ready-extras", now = Instant.parse("2026-09-06T14:30:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals("6 September 2026", ready.gregorian)
        // London to Makkah: roughly 119 degrees and 4,700 km (spec §12 quotes 118.9°).
        assertTrue(ready.qiblaBearingDegrees in 118.0..120.0, "bearing ${ready.qiblaBearingDegrees}")
        assertTrue(ready.qiblaDistanceKm in 4_600.0..4_800.0, "distance ${ready.qiblaDistanceKm}")
    }

    @Test
    fun tromsoSurfacesTheHighLatitudeNote() = runTest {
        val vm = todayViewModelForTromso("tromso-rule", Instant.parse("2026-06-21T12:00:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertTrue(ready.highLatitudeNote != null)
        assertTrue(
            ready.highLatitudeNote.contains("one-seventh") ||
                ready.highLatitudeNote.contains("twilight"),
        )
    }

    @Test
    fun polarDaySaysEveryTimeWasSubstitutedNotJustFajrAndIsha() = runTest {
        val vm = todayViewModelForTromso("tromso-polar", Instant.parse("2026-06-21T12:00:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertTrue(ready.highLatitudeNote!!.contains("does not rise or set"))
        assertTrue(ready.highLatitudeNote.contains("All times"))
    }

    @Test
    fun theHijriOffsetShiftsTheDisplayedDate() = runTest {
        val zero = todayViewModelForLondon("hijri-0", hijriOffset = 0).state.first { it is TodayUiState.Ready }
        val plus = todayViewModelForLondon("hijri-1", hijriOffset = 1).state.first { it is TodayUiState.Ready }
        assertTrue((zero as TodayUiState.Ready).hijri != (plus as TodayUiState.Ready).hijri)
    }

    // -- C5: the once-per-second widget refresh storm ------------------------------------------
    //
    // `start()` ticks once a second, and every tick used to re-serialise the mirror, write it, and
    // nudge both widget systems: 60 writes and 60 reloads a minute. On iOS that outran WidgetKit's
    // ~40-70 reloads/day budget in under a minute, after which the widget froze for the rest of
    // the day; on Android it blocked the composition's Main dispatcher on a SharedPreferences
    // write plus two Glance recompositions plus AppWidgetManager IPC, once a second, on the app's
    // primary screen.

    private suspend fun TestScope.probeFor(storeName: String): WidgetTrafficProbe {
        val repo = settings(storeName)
        repo.setPrayerSettings(PrayerSettings())
        return WidgetTrafficProbe(london, repo)
    }

    /**
     * Anchors the clock so the countdown to the real next prayer is exactly 40 min 30 s. Offsets
     * are taken from the engine's own instant rather than from a fixed wall-clock time, so the
     * "same minute" pair below is provably inside one minute of the countdown (40:30 and 40:29
     * both floor to 40 minutes) instead of depending on where 14:30 happens to fall.
     */
    private suspend fun WidgetTrafficProbe.anchorAtFortyMinutesThirtySeconds() {
        viewModel.refresh()
        val ready = viewModel.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        now = ready.today.next.instant - 40.minutes - 30.seconds
    }

    @Test
    fun twoTicksOneSecondApartInsideOneMinuteWriteTheMirrorOnceAndRefreshOnce() = runTest {
        val probe = probeFor("widget-traffic-same-minute")
        probe.anchorAtFortyMinutesThirtySeconds()

        val writesBefore = probe.store.writes
        val refreshesBefore = probe.refreshes
        probe.viewModel.refresh()
        probe.now += 1.seconds
        probe.viewModel.refresh()

        assertEquals(1, probe.store.writes - writesBefore)
        assertEquals(1, probe.refreshes - refreshesBefore)
    }

    @Test
    fun crossingAMinuteBoundaryWritesTheMirrorASecondTime() = runTest {
        val probe = probeFor("widget-traffic-minute-boundary")
        probe.anchorAtFortyMinutesThirtySeconds()

        val writesBefore = probe.store.writes
        val refreshesBefore = probe.refreshes
        probe.viewModel.refresh()
        // 40:30 -> 39:59. The whole-minute countdown changes, so the widgets must hear about it:
        // the dedupe must suppress redundant ticks, never a real one.
        probe.now += 31.seconds
        probe.viewModel.refresh()

        assertEquals(2, probe.store.writes - writesBefore)
        assertEquals(2, probe.refreshes - refreshesBefore)
    }

    /**
     * The device-level claim behind C5, asserted in code: 90 seconds with Today open must cost a
     * handful of widget updates, not ninety. Ninety seconds crosses one or two whole minutes, and
     * the ring — quantised to 1/120 of the gap between prayers — may add a step of its own, so the
     * ceiling is deliberately loose. What matters is that it is nowhere near the tick count.
     */
    @Test
    fun ninetySecondsOfTickingCostsAHandfulOfWidgetUpdatesNotNinety() = runTest {
        val probe = probeFor("widget-traffic-ninety-seconds")
        probe.anchorAtFortyMinutesThirtySeconds()

        val writesBefore = probe.store.writes
        val refreshesBefore = probe.refreshes
        repeat(90) {
            probe.viewModel.refresh()
            probe.now += 1.seconds
        }

        val writes = probe.store.writes - writesBefore
        assertTrue(writes in 1..6, "90 ticks produced $writes mirror writes")
        assertEquals(writes, probe.refreshes - refreshesBefore)
    }

    // -- the lifecycle-gated tick -----------------------------------------------------------
    //
    // `tickWhileActive()` replaced `start(scope)`: it owns no scope and no `launch` of its own,
    // so it runs only for as long as its *caller's* coroutine survives. In `App.kt` that caller is
    // `repeatOnLifecycle(STARTED) { … }`, which cancels the block the moment the activity is
    // stopped (screen off, Home pressed) rather than merely leaving composition. This is the
    // behaviour-level guard for that: drive the loop from a child job, let it tick for real
    // (through a minute boundary, so the dedupe in `refresh()` doesn't hide a stalled loop),
    // cancel the job, and prove nothing it does — mirror write or widget nudge — continues.

    @Test
    fun cancellingTheTickingJobStopsFurtherWidgetActivity() = runTest {
        val repo = settings("tick-while-active-cancel")
        repo.setPrayerSettings(PrayerSettings())
        val store = CountingKeyValueStore()
        var refreshes = 0
        val base = Instant.parse("2026-09-06T14:30:00Z")
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { london },
            // Tied to the test dispatcher's own virtual clock rather than a value advanced by
            // hand, so `tickWhileActive()`'s real `delay(1_000)` loop is what moves time forward —
            // exactly what a fake `now` driven manually (as the rest of this file uses) can't
            // exercise, since nothing here calls `refresh()` directly.
            now = { base + currentTime.milliseconds },
            widgetStore = { store },
            widgetFormat = { EnglishPlatformFormat },
            onWidgetsChanged = { refreshes++ },
        )

        val job = launch { vm.tickWhileActive() }
        // The loop refreshes once immediately, before its first delay — the screen must be
        // correct the instant Today appears, not a second late. Awaited through the state flow
        // rather than `runCurrent()`: the settings DataStore's first read is real (non-virtual)
        // I/O, which a scheduler advance does not wait out, but suspending on the state this
        // refresh produces does.
        vm.state.first { it is TodayUiState.Ready }
        assertEquals(1, store.writes, "entry must write the mirror once before the first tick")

        // Tick through whole-minute boundaries the dedupe cannot collapse into the first write.
        // Each virtual second only *schedules* the next `refresh()`; running it still crosses
        // through the settings DataStore's real (non-virtual) read, so a one-shot
        // `advanceTimeBy(3.minutes)` would race ahead of that real completion and starve the loop
        // after tick one. Ceding to a real dispatcher between virtual steps gives that read
        // somewhere to actually finish.
        repeat(180) {
            advanceTimeBy(1.seconds)
            runCurrent()
            withContext(Dispatchers.Default) { yield() }
            runCurrent()
        }
        val writesWhileActive = store.writes
        val refreshesWhileActive = refreshes
        assertTrue(writesWhileActive > 1, "expected minute-boundary writes while active, saw $writesWhileActive")

        job.cancelAndJoin()
        val writesAtCancel = store.writes
        val refreshesAtCancel = refreshes
        assertEquals(refreshesWhileActive, refreshesAtCancel)

        // Five more minutes would produce several more writes if the loop were still running.
        repeat(300) {
            advanceTimeBy(1.seconds)
            runCurrent()
        }

        assertEquals(writesAtCancel, store.writes, "no further mirror writes once the tick job is cancelled")
        assertEquals(refreshesAtCancel, refreshes, "no further widget refresh once the tick job is cancelled")
    }

    // -- the header's city name, and the id a pre-0.4.0 location was saved without ---------------
    //
    // The saved location carries the GeoNames id of the city it came from, so the header can be
    // re-rendered in whatever language the phone is in. `cityName` stays the English snapshot it
    // has always been, which is both the fallback and what an upgraded install has until the id
    // is backfilled.

    private val citiesCsv = """
        id,name,region,country,countryCode,lat,lon,tz
        2643743,London,England,United Kingdom,GB,51.50853,-0.12574,Europe/London
        360630,Cairo,Cairo Governorate,Egypt,EG,30.06263,31.24967,Africa/Cairo
    """.trimIndent()

    /** London has an Arabic name; Cairo deliberately does not, so the fallback is exercised. */
    private val arabicNames = """
        id,name
        2643743,لندن
    """.trimIndent()

    private fun cities() = CityRepository(
        loadCsv = { citiesCsv },
        loadNames = { language -> if (language == "ar") arabicNames else null },
    )

    /** Nothing is near anything: `nearest` returns null, which is the no-match case. */
    private fun noCities() = CityRepository(loadCsv = { "id,name,region,country,countryCode,lat,lon,tz" })

    private val londonWithId = london.copy(cityId = 2643743)
    private val cairoWithId =
        GeoLocation(30.06263, 31.24967, "Africa/Cairo", "Cairo", "EG", cityId = 360630)

    private suspend fun TestScope.readyFor(
        store: String,
        location: GeoLocation,
        format: PlatformFormat,
        cityRepository: CityRepository = cities(),
    ): TodayUiState.Ready {
        val repo = settings(store)
        repo.setPrayerSettings(PrayerSettings())
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { location },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cityRepository,
            format = format,
        )
        vm.refresh()
        return vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
    }

    @Test
    fun aSavedCityIsNamedInArabicWhenTheInterfaceIs() = runTest {
        val ready = readyFor("city-name-ar", londonWithId, ArabicPlatformFormat())
        assertEquals("لندن", ready.cityDisplayName)
        // The stored English snapshot is untouched — it is a record of what was saved, not what
        // is on screen.
        assertEquals("London", ready.location.cityName)
    }

    @Test
    fun theSameCityIsNamedInEnglishWhenTheInterfaceIs() = runTest {
        val ready = readyFor("city-name-en", londonWithId, EnglishPlatformFormat)
        assertEquals("London", ready.cityDisplayName)
    }

    @Test
    fun aCityWithNoNameInTheInterfaceLanguageFallsBackToTheEnglishOne() = runTest {
        val ready = readyFor("city-name-ar-missing", cairoWithId, ArabicPlatformFormat())
        assertEquals("Cairo", ready.cityDisplayName)
    }

    @Test
    fun aLocationWithNoIdAndNoMatchingCityKeepsShowingItsStoredName() = runTest {
        val ready = readyFor("city-name-no-match", london, ArabicPlatformFormat(), noCities())
        assertEquals("London", ready.cityDisplayName)
        assertNull(ready.location.cityId)
    }

    @Test
    fun aLocationSavedBeforeIdsExistedGetsItsIdFromItsCoordinates() = runTest {
        val repo = settings("city-id-migration")
        repo.setPrayerSettings(PrayerSettings())
        repo.setLocation(london)
        assertNull(repo.location.first()!!.cityId, "the fixture must start without an id")

        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = ArabicPlatformFormat(),
        )
        vm.refresh()

        assertEquals(2643743, repo.location.first()!!.cityId, "the id must be written back")
        // And the header follows immediately, without waiting for the next launch.
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals("لندن", ready.cityDisplayName)
    }

    /**
     * A language change swaps the view model for one whose format reports the new language. A
     * fresh one starts at `Loading` and the Prayer screen emptied to its spinner until the first
     * refresh landed; seeded with the outgoing state it simply re-renders in the new language.
     */
    @Test
    fun aLanguageChangeCarriesTheOutgoingStateIntoTheSuccessorRatherThanBlankingTheScreen() = runTest {
        val outgoing = readyFor("city-language-swap-ar", londonWithId, ArabicPlatformFormat())
        assertEquals("\u0644\u0646\u062F\u0646", outgoing.cityDisplayName)

        val repo = settings("city-language-swap-en")
        repo.setPrayerSettings(PrayerSettings())
        val successor = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { londonWithId },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = EnglishPlatformFormat,
            initialState = outgoing,
        )
        // Before a single refresh: the same rows, still on screen.
        assertEquals(outgoing, successor.state.value)

        successor.refresh()
        val ready = successor.state.value as TodayUiState.Ready
        assertEquals("London", ready.cityDisplayName, "and then the new language's name")
        assertEquals(outgoing.today.rows.size, ready.today.rows.size)
    }

    /**
     * The backfill writes the id, but `settings.location` is a DataStore flow — the next tick can
     * still read a location without one. Holding the migrated id keeps the header from flickering
     * Arabic \u2192 English \u2192 Arabic while the store catches up.
     */
    @Test
    fun theMigratedIdSurvivesTicksTakenBeforeTheStoreCatchesUp() = runTest {
        val repo = settings("city-id-migration-lag")
        repo.setPrayerSettings(PrayerSettings())
        repo.setLocation(london)
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { london }, // a store that never reports the id back
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = ArabicPlatformFormat(),
        )
        vm.refresh()
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            (vm.state.value as TodayUiState.Ready).cityDisplayName,
        )

        repeat(3) { vm.refresh() }
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            (vm.state.value as TodayUiState.Ready).cityDisplayName,
            "the header fell back to the English snapshot while the store caught up",
        )
    }

    /**
     * The name lookup suspends on a 1.6 MB parse. A city picked while it was running has already
     * published a state about somewhere else, and the resolved name must not be written over it —
     * the same guard [migrateCityId] has always had.
     */
    @Test
    fun aNameResolvedForACityTheScreenHasAlreadyLeftIsNotPublished() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = CityRepository(
            loadCsv = { gate.await(); citiesCsv },
            loadNames = { language -> if (language == "ar") arabicNames else null },
        )
        val store = settings("city-name-stale")
        store.setPrayerSettings(PrayerSettings())
        var current = londonWithId
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = store,
            locationOf = { current },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = slow,
            format = ArabicPlatformFormat(),
        )

        // Everything the state flow publishes, captured before the race rather than sampled after
        // it: the corrupt value the missing guard produced was overwritten by the next tick.
        val seen = mutableListOf<TodayUiState>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.toList(seen) }

        val first = launch { vm.refresh() } // publishes London, then blocks in the lookup
        runCurrent()
        current = cairoWithId
        val second = launch { vm.refresh() } // publishes Cairo, then queues behind the same lock
        runCurrent()

        gate.complete(Unit)
        first.join()
        second.join()
        watcher.cancel()

        val misattributed = seen.filterIsInstance<TodayUiState.Ready>().filter {
            it.location == cairoWithId && it.cityDisplayName == "\u0644\u0646\u062F\u0646"
        }
        assertTrue(misattributed.isEmpty(), "London's Arabic name was published under Cairo")
        assertEquals("Cairo", (vm.state.value as TodayUiState.Ready).cityDisplayName)
    }

    // -- D1: the header remembers its translated name -------------------------------------------
    //
    // The name cannot be looked up without parsing the 1.6 MB city list, and that parse is
    // deliberately off the first frame so the prayer times are not held behind it. On a cold start
    // the header therefore showed the English snapshot for ~0.4 s and then swapped — visibly,
    // after the screen had settled. The answer is remembered next to the id instead, together with
    // the language it was resolved in, so an ordinary launch needs no lookup at all.

    /** Any read of the city list at all fails the test outright. */
    private fun noCityListAtAll() = CityRepository(
        loadCsv = { fail("the city list was parsed on a launch that already knew the name") },
    )

    private fun viewModelFor(
        repo: SettingsRepository,
        location: GeoLocation,
        cityRepository: CityRepository,
        format: PlatformFormat,
    ) = TodayViewModel(
        engine = PrayerTimesEngine(),
        settings = repo,
        locationOf = { location },
        now = { Instant.parse("2026-09-06T14:30:00Z") },
        cityRepository = cityRepository,
        format = format,
    )

    @Test
    fun aColdStartWhoseStoredLanguageMatchesOpensOnTheTranslatedNameAndLooksNothingUp() = runTest {
        val repo = settings("city-name-remembered")
        repo.setPrayerSettings(PrayerSettings())
        repo.setLocation(londonWithId, ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"))

        val vm = viewModelFor(repo, londonWithId, noCityListAtAll(), ArabicPlatformFormat())
        val seen = mutableListOf<TodayUiState>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.toList(seen) }
        vm.refresh()
        // The collector is resumed by the scheduler, not by the emission itself: without this it
        // is cancelled before it has seen anything at all.
        runCurrent()
        watcher.cancel()

        val ready = seen.filterIsInstance<TodayUiState.Ready>()
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            ready.first().cityDisplayName,
            "the first state a cold start publishes must already carry the reader's own name",
        )
        // The English snapshot is never on screen, not even for one frame — this is D1 itself.
        assertTrue(
            ready.none { it.cityDisplayName == "London" },
            "the English snapshot was published before the stored name",
        )
    }

    @Test
    fun aColdStartWhoseStoredLanguageDiffersFallsBackToTheSnapshotAndSwapsWhenTheLookupLands() = runTest {
        val repo = settings("city-name-remembered-other-language")
        repo.setPrayerSettings(PrayerSettings())
        // Resolved when the phone was in English; the reader has since switched to Arabic.
        repo.setLocation(londonWithId, ResolvedCityName("London", "en-GB"))

        val gate = CompletableDeferred<Unit>()
        val slow = CityRepository(
            loadCsv = { gate.await(); citiesCsv },
            loadNames = { language -> if (language == "ar") arabicNames else null },
        )
        val vm = viewModelFor(repo, londonWithId, slow, ArabicPlatformFormat())

        val refreshing = launch { vm.refresh() }
        // Awaited through the state rather than with `runCurrent()`: the settings store's first
        // read is real I/O, which advancing the scheduler does not wait out. The lookup is still
        // held at the gate, so this is genuinely the state the first frame would paint.
        val first = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        // Today's behaviour, kept on purpose for exactly this case: a stored English name must not
        // be printed to a reader in Arabic, so this one launch flickers as it always did.
        assertEquals("London", first.cityDisplayName)

        gate.complete(Unit)
        refreshing.join()
        assertEquals(
            "\u0644\u0646\u062F\u0646",
            (vm.state.value as TodayUiState.Ready).cityDisplayName,
        )
        // And the next launch will not: the Arabic name is now what is stored.
        assertEquals(
            ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"),
            repo.resolvedCityName.first(),
        )
    }

    @Test
    fun theNameIsRememberedOnceNotOnEveryTick() = runTest {
        val writeOncePath = freshStorePath("city-name-write-once")
        val store = CountingDataStore(PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) { writeOncePath })
        val repo = SettingsRepository(store)
        repo.setPrayerSettings(PrayerSettings())
        repo.setLocation(londonWithId)
        assertNull(repo.resolvedCityName.first(), "a location stored without a name must have none")

        val vm = viewModelFor(repo, londonWithId, cities(), ArabicPlatformFormat())
        val writesBefore = store.writes
        // A minute and a half with the Prayer screen open.
        repeat(90) { vm.refresh() }

        assertEquals(
            1,
            store.writes - writesBefore,
            "90 ticks must write the remembered name exactly once",
        )
        assertEquals(
            ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"),
            repo.resolvedCityName.first(),
        )
    }

    @Test
    fun theIdBackfillIsAttemptedOnceARunNotOnceASecond() = runTest {
        val repo = settings("city-id-migration-once")
        repo.setPrayerSettings(PrayerSettings())
        repo.setLocation(london)
        val vm = TodayViewModel(
            engine = PrayerTimesEngine(),
            settings = repo,
            locationOf = { repo.location.first() },
            now = { Instant.parse("2026-09-06T14:30:00Z") },
            cityRepository = cities(),
            format = EnglishPlatformFormat,
        )
        vm.refresh()
        assertEquals(2643743, repo.location.first()!!.cityId)

        // Put the store back the way an old install left it. Ninety more ticks — a minute and a
        // half with Today open — must not scan the city list or write the id again, which is what
        // the id staying absent proves.
        repo.setLocation(london)
        repeat(90) { vm.refresh() }
        assertNull(repo.location.first()!!.cityId, "the backfill ran more than once")
    }
}
