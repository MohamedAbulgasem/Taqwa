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
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.audio.createSoundPreviewPlayer
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaTheme
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.feature.onboarding.OnboardingScreen
import world.taqwa.app.feature.onboarding.OnboardingStep
import world.taqwa.app.feature.settings.AppearanceSettingsScreen
import world.taqwa.app.feature.settings.AttributionScreen
import world.taqwa.app.feature.settings.CitySearchScreen
import world.taqwa.app.feature.settings.HighLatitudePickerScreen
import world.taqwa.app.feature.settings.LocationSettingsScreen
import world.taqwa.app.feature.settings.ManualAdjustmentsScreen
import world.taqwa.app.feature.settings.MethodPickerScreen
import world.taqwa.app.feature.settings.NotificationSettingsScreen
import world.taqwa.app.feature.settings.PrayerTimesSettingsScreen
import world.taqwa.app.feature.settings.SettingsRootScreen
import world.taqwa.app.feature.settings.methodDisplayName
import world.taqwa.app.feature.settings.themeDisplayName
import world.taqwa.app.feature.today.TodayScreen
import world.taqwa.app.feature.today.TodayViewModel
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.nav.SystemBackHandler
import world.taqwa.app.notifications.NotificationOnboarding
import world.taqwa.app.notifications.RescheduleTrigger
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

@Composable
fun App(container: AppContainer) {
    val settings = container.settingsRepository
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val prayerSettings by settings.prayerSettings.collectAsState(initial = PrayerSettings())
    val notificationSettings by settings.notificationSettings.collectAsState(initial = NotificationSettings())
    val location by settings.location.collectAsState(initial = null)
    val navigator = remember { Navigator(Screen.Today) }
    val backStack by navigator.backStack.collectAsState()
    val scope = rememberCoroutineScope()
    val soundPreviewPlayer = remember { createSoundPreviewPlayer() }

    // Reused by both onboarding's "Enable notifications" and "Not now": the system ask (if any)
    // has already happened by the time this runs, so `requestSystemPermission` just returns the
    // already-known answer rather than asking again.
    fun notificationOnboarding(granted: Boolean) = NotificationOnboarding(
        requestSystemPermission = { granted },
        setNotificationsEnabled = { enabled ->
            settings.setNotificationSettings(settings.notificationSettings.first().copy(enabled = enabled))
        },
        rescheduleIfEnabled = {
            container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
        },
    )

    // Onboarding's step lives here rather than inside the screen: "choose a city instead"
    // navigates away to the city search, which would otherwise reset the flow to its first page.
    var onboardingStep by remember { mutableStateOf(OnboardingStep.WELCOME) }

    // Resolved before anything renders, so a returning user never sees Today flash behind
    // onboarding on launch.
    var startResolved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!settings.onboardingComplete.first()) {
            navigator.replaceAll(Screen.Onboarding)
        }
        startResolved = true
        container.notificationCoordinator.reschedule(world.taqwa.app.notifications.RescheduleTrigger.APP_FOREGROUND)
    }

    fun useGpsFix() {
        scope.launch {
            container.locationRepository.currentCoordinates()?.let {
                settings.setLocation(gpsLocation(it))
            }
        }
    }

    fun write(next: PrayerSettings) {
        // Every control in settings writes through on touch; there is no save button anywhere.
        scope.launch { settings.setPrayerSettings(next) }
    }

    // Both the Hijri preview and the adjusted times in manual adjustments are "today" in the
    // location's own zone, which is the zone Today's rows are already drawn in.
    val zone = remember(location) {
        location?.let { TimeZone.of(it.timeZoneId) } ?: TimeZone.currentSystemDefault()
    }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }

    TaqwaTheme(themeMode) {
        Box(Modifier.fillMaxSize().background(LocalTaqwaColors.current.background)) {
            if (!startResolved) return@Box
            // Android's back button walks the same stack as the on-screen chevron.
            SystemBackHandler(enabled = backStack.size > 1) { navigator.pop() }

            when (backStack.last()) {
                Screen.Onboarding -> OnboardingScreen(
                    step = onboardingStep,
                    onStep = { onboardingStep = it },
                    locationRepository = container.locationRepository,
                    onLocationPermission = { permission ->
                        if (permission == LocationPermission.GRANTED) useGpsFix()
                    },
                    onChooseCity = { navigator.push(Screen.CitySearch) },
                    onNotificationPermission = { granted ->
                        scope.launch { notificationOnboarding(granted).enable() }
                    },
                    onDeclineNotifications = {
                        scope.launch { notificationOnboarding(false).declineForNow() }
                    },
                    onComplete = {
                        scope.launch {
                            settings.setOnboardingComplete(true)
                            navigator.replaceAll(Screen.Today)
                        }
                    },
                )

                Screen.Today -> {
                    val viewModel = remember {
                        TodayViewModel(
                            engine = container.prayerTimesEngine,
                            settings = settings,
                            locationOf = { settings.location.first() },
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
                        onChooseCity = { navigator.push(Screen.CitySearch) },
                        onAllowLocation = requestLocation,
                    )
                }

                Screen.Settings -> SettingsRootScreen(
                    cityName = location?.let { it.cityName ?: "Current location" },
                    methodName = methodDisplayName(prayerSettings.method),
                    themeName = themeDisplayName(themeMode),
                    notificationSettings = notificationSettings,
                    onBack = { navigator.pop() },
                    onOpenLocation = { navigator.push(Screen.LocationSettings) },
                    onOpenPrayerTimes = { navigator.push(Screen.PrayerTimesSettings) },
                    onOpenNotifications = { navigator.push(Screen.NotificationSettings) },
                    onOpenAppearance = { navigator.push(Screen.Appearance) },
                    onOpenAttribution = { navigator.push(Screen.Attribution) },
                )

                Screen.NotificationSettings -> NotificationSettingsScreen(
                    settings = notificationSettings,
                    onBack = { navigator.pop() },
                    onToggleEnabled = { enabled ->
                        scope.launch {
                            settings.setNotificationSettings(notificationSettings.copy(enabled = enabled))
                            container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
                        }
                    },
                    onPickLead = { minutes ->
                        scope.launch {
                            settings.setNotificationSettings(
                                notificationSettings.copy(remindBeforeMinutes = minutes),
                            )
                            container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
                        }
                    },
                    onPickSound = { prayer, sound ->
                        scope.launch {
                            val updated = notificationSettings.copy(
                                sounds = notificationSettings.sounds + (prayer to sound),
                            )
                            settings.setNotificationSettings(updated)
                            container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
                        }
                    },
                    onPreviewSound = { soundPreviewPlayer.play(it) },
                )

                Screen.PrayerTimesSettings -> PrayerTimesSettingsScreen(
                    settings = prayerSettings,
                    today = today,
                    onChange = ::write,
                    onBack = { navigator.pop() },
                    onOpenMethodPicker = { navigator.push(Screen.MethodPicker) },
                    onOpenHighLatitudePicker = { navigator.push(Screen.HighLatitudePicker) },
                    onOpenManualAdjustments = { navigator.push(Screen.ManualAdjustments) },
                )

                Screen.MethodPicker -> MethodPickerScreen(
                    current = prayerSettings.method,
                    onPick = {
                        write(prayerSettings.copy(method = it))
                        navigator.pop()
                    },
                    onBack = { navigator.pop() },
                )

                Screen.HighLatitudePicker -> HighLatitudePickerScreen(
                    current = prayerSettings.highLatitude,
                    latitude = location?.latitude ?: 0.0,
                    onPick = {
                        write(prayerSettings.copy(highLatitude = it))
                        navigator.pop()
                    },
                    onBack = { navigator.pop() },
                )

                Screen.ManualAdjustments -> ManualAdjustmentsScreen(
                    settings = prayerSettings,
                    location = location,
                    engine = container.prayerTimesEngine,
                    today = today,
                    onChange = ::write,
                    onBack = { navigator.pop() },
                )

                Screen.LocationSettings -> LocationSettingsScreen(
                    location = location,
                    locationRepository = container.locationRepository,
                    onLocationPermission = { permission ->
                        if (permission == LocationPermission.GRANTED) useGpsFix()
                    },
                    onChooseCity = { navigator.push(Screen.CitySearch) },
                    onBack = { navigator.pop() },
                )

                Screen.CitySearch -> CitySearchScreen(
                    cityRepository = container.cityRepository,
                    onPick = { city ->
                        scope.launch { settings.setLocation(city.toGeoLocation()) }
                        // Reached from onboarding, a successful pick answers the location
                        // question, so the flow continues rather than re-asking it.
                        if (backStack.contains(Screen.Onboarding)) {
                            onboardingStep = OnboardingStep.NOTIFICATIONS
                        }
                        navigator.pop()
                    },
                    onBack = { navigator.pop() },
                )

                Screen.Appearance -> AppearanceSettingsScreen(
                    current = themeMode,
                    // No pop: the whole app repaints behind this screen, and seeing that happen
                    // is the confirmation the choice took effect.
                    onPick = { scope.launch { settings.setThemeMode(it) } },
                    onBack = { navigator.pop() },
                )

                Screen.Attribution -> AttributionScreen(onBack = { navigator.pop() })
            }
        }
    }
}
