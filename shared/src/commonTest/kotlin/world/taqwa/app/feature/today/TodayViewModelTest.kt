package world.taqwa.app.feature.today

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    // paths at runtime on iosSimulatorArm64Test. One file per test keeps the cases isolated.
    private fun settings(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-test-today-$name.preferences_pb".toPath() }
    )

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")
    private val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")

    /**
     * The view model is built with a fake location lookup and a frozen clock, so every case here
     * runs without a device, a GPS fix or a real wall clock. `refresh()` is awaited directly
     * rather than starting the one-second tick, which would never terminate under `runTest`.
     */
    private suspend fun viewModel(
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
    private suspend fun todayViewModelWithNoLocation() =
        viewModel("no-location", null, Instant.parse("2026-09-06T14:30:00Z"))

    private suspend fun todayViewModelForLondon(
        store: String,
        now: Instant = Instant.parse("2026-09-06T14:30:00Z"),
        hijriOffset: Int = 0,
    ) = viewModel(store, london, now, hijriOffset)

    private suspend fun todayViewModelForTromso(store: String, now: Instant) =
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

    private suspend fun probeFor(storeName: String): WidgetTrafficProbe {
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
}
