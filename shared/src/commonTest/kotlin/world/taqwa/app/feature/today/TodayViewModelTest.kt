package world.taqwa.app.feature.today

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

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
}
