package world.taqwa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaTheme
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.feature.onboarding.OnboardingScreen
import world.taqwa.app.feature.today.TodayScreen
import world.taqwa.app.feature.today.TodayViewModel
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import kotlin.time.Clock

/**
 * A GPS fix carries coordinates but no zone. The phone is where the user is, so the device's own
 * timezone is the right one — and it stays right when they travel. A manually chosen city brings
 * its own IANA id from the bundled database instead.
 */
private fun gpsLocation(coordinates: Pair<Double, Double>) = GeoLocation(
    latitude = coordinates.first,
    longitude = coordinates.second,
    timeZoneId = TimeZone.currentSystemDefault().id,
)

// TODO(Task 14): replace with CitySearch. Until that screen exists, "choose a city" resolves to a
// fixed London so the onboarding and location-denied paths can be walked end to end.
private val TemporaryCity = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")

@Composable
fun App(container: AppContainer) {
    val themeMode by container.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val navigator = remember { Navigator(Screen.Today) }
    val backStack by navigator.backStack.collectAsState()
    val scope = rememberCoroutineScope()

    // Resolved before anything renders, so a returning user never sees Today flash behind
    // onboarding on launch.
    var startResolved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!container.settingsRepository.onboardingComplete.first()) {
            navigator.replaceAll(Screen.Onboarding)
        }
        startResolved = true
    }

    fun useGpsFix() {
        scope.launch {
            container.locationRepository.currentCoordinates()?.let {
                container.settingsRepository.setLocation(gpsLocation(it))
            }
        }
    }

    TaqwaTheme(themeMode) {
        Box(Modifier.fillMaxSize().background(LocalTaqwaColors.current.background)) {
            if (!startResolved) return@Box
            when (backStack.last()) {
                Screen.Onboarding -> OnboardingScreen(
                    locationRepository = container.locationRepository,
                    onLocationPermission = { permission ->
                        if (permission == LocationPermission.GRANTED) useGpsFix()
                    },
                    onChooseCity = {
                        // TODO(Task 14): push Screen.CitySearch instead.
                        scope.launch { container.settingsRepository.setLocation(TemporaryCity) }
                    },
                    onComplete = {
                        scope.launch {
                            container.settingsRepository.setOnboardingComplete(true)
                            navigator.replaceAll(Screen.Today)
                        }
                    },
                )

                Screen.Today -> {
                    val viewModel = remember {
                        TodayViewModel(
                            engine = container.prayerTimesEngine,
                            settings = container.settingsRepository,
                            locationOf = { container.settingsRepository.location.first() },
                            now = { Clock.System.now() },
                        )
                    }
                    // Tied to this composable: the one-second tick starts when Today appears and
                    // is cancelled the moment it leaves the backstack.
                    LaunchedEffect(viewModel) { viewModel.start(this) }
                    val state by viewModel.state.collectAsState()

                    val requestLocation = world.taqwa.app.location
                        .rememberLocationPermissionRequester(container.locationRepository) {
                            if (it == LocationPermission.GRANTED) useGpsFix()
                        }

                    TodayScreen(
                        state = state,
                        onOpenQibla = { /* Slice 1, plan 2 */ },
                        onOpenSettings = { navigator.push(Screen.Settings) },
                        onChooseCity = {
                            // TODO(Task 14): push Screen.CitySearch instead.
                            scope.launch { container.settingsRepository.setLocation(TemporaryCity) }
                        },
                        onAllowLocation = requestLocation,
                    )
                }

                else -> {
                    // Settings and the city picker arrive in Task 14. Falling back to Today keeps
                    // the app usable rather than showing a blank screen in the meantime.
                    LaunchedEffect(backStack) { navigator.replaceAll(Screen.Today) }
                }
            }
        }
    }
}
