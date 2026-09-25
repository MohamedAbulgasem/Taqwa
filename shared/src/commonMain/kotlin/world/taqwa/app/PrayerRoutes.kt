package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.feature.qibla.QiblaScreen
import world.taqwa.app.feature.qibla.QiblaViewModel
import world.taqwa.app.feature.tasbeeh.TasbeehScreen
import world.taqwa.app.feature.tasbeeh.TasbeehViewModel
import world.taqwa.app.feature.today.TodayScreen
import world.taqwa.app.feature.today.TodayUiState
import world.taqwa.app.feature.today.TodayViewModel
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.notifications.requestExactAlarmAccess
import world.taqwa.app.qibla.createCompassSource
import world.taqwa.app.qibla.createHaptics
import world.taqwa.app.settings.SettingsRepository
import kotlin.time.Clock

@Composable
internal fun TodayRoute(
    languageTag: String,
    container: AppContainer,
    settings: SettingsRepository,
    platformFormat: PlatformFormat,
    useGpsFix: () -> Unit,
    navigator: Navigator,
    notificationSettingsState: State<NotificationSettings>,
    exactAlarmsAllowedState: State<Boolean>,
) {
    val notificationSettings by notificationSettingsState
    val exactAlarmsAllowed by exactAlarmsAllowedState
    // The last state this screen showed, carried across the view-model
    // swap below. Plain `remember`, so a language change — which does not
    // recreate the composition — leaves it standing.
    val lastState = remember { mutableStateOf<TodayUiState>(TodayUiState.Loading) }
    // Rebuilt when the language changes, with a format that reports the
    // new one, because the header's city name, the Hijri and Gregorian
    // dates and the high-latitude note all read their language from it.
    // The cost is one extra widget-mirror write per language change.
    val viewModel = remember(languageTag) {
        TodayViewModel(
            engine = container.prayerTimesEngine,
            settings = settings,
            locationOf = { settings.location.first() },
            now = { Clock.System.now() },
            // For the header's city name in the reader's language, and
            // for the one-time backfill of a location saved without an id.
            cityRepository = container.cityRepository,
            format = platformFormat,
            // A language change swaps the view model; without this the
            // new one would start at Loading and blank the whole screen
            // until its first refresh landed.
            initialState = lastState.value,
        )
    }
    // Gated on the *lifecycle*, not just composition: leaving Today for another
    // tab cancels this via composition alone, but the activity being merely
    // stopped (screen off, Home pressed) does not tear down the composable —
    // it stays composed and a plain LaunchedEffect would keep ticking, writing
    // the widget mirror once a minute, for as long as the process lives.
    // repeatOnLifecycle suspends the block on STOP and restarts it on the next
    // START, so the tick truly runs only while Today is on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.tickWhileActive()
        }
    }
    val state by viewModel.state.collectAsState()
    SideEffect { lastState.value = state }

    val requestLocation = world.taqwa.app.location
        .rememberLocationPermissionRequester(container.locationRepository) {
            if (it == LocationPermission.GRANTED) useGpsFix()
        }

    TodayScreen(
        state = state,
        onChooseCity = { navigator.push(Screen.CitySearch) },
        onAllowLocation = requestLocation,
        onOpenQibla = { navigator.push(Screen.Qibla) },
        onOpenTasbeeh = { navigator.push(Screen.Tasbeeh) },
        // Only while there are notifications to be exact about; the flag
        // is re-read on every foreground, so the card leaves by itself.
        exactAlarmsOff = notificationSettings.enabled && !exactAlarmsAllowed,
        onAllowExactAlarms = ::requestExactAlarmAccess,
    )
}

@Composable
internal fun QiblaRoute(
    locationState: State<GeoLocation?>,
    navigator: Navigator,
) {
    val location by locationState
    val loc = location
    if (loc == null) {
        // Only reachable from the Prayer screen's Qibla card, which needs
        // a location to exist; if it has gone in between, go back.
        LaunchedEffect(Unit) { navigator.pop() }
    } else {
        val vm = remember(loc) {
            QiblaViewModel(
                location = loc,
                compassSource = createCompassSource(),
                haptics = createHaptics(),
            )
        }
        // Lifecycle-gated for the same reason Today's tick is: leaving the
        // screen cancels this through composition, but the activity merely
        // stopping (screen off, Home pressed) does not, and a plain
        // LaunchedEffect kept the magnetometer registered at
        // SENSOR_DELAY_GAME behind the lock screen for the life of the
        // process. repeatOnLifecycle unregisters the sensors on STOP and
        // registers them again on the next START.
        val qiblaLifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(vm, qiblaLifecycleOwner) {
            qiblaLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.collectWhileActive()
            }
        }
        val qiblaState by vm.state.collectAsState()
        QiblaScreen(qiblaState, onBack = { navigator.pop() })
    }
}

@Composable
internal fun TasbeehRoute(
    container: AppContainer,
    navigator: Navigator,
) {
    // Plain `remember`: the counter has no language of its own to be
    // rebuilt for — its phrases are Arabic in both interfaces — and it
    // owns the count on screen, which a swap would drop back to the last
    // written one. It reads the store once on creation and writes back
    // 300 ms after the last tap, or at once through `flush` below.
    val tasbeehVm = remember {
        TasbeehViewModel(
            store = container.tasbeehStore,
            haptics = createHaptics(),
        )
    }
    val tasbeehState by tasbeehVm.state.collectAsState()
    TasbeehScreen(
        state = tasbeehState,
        onBack = { navigator.pop() },
        onTap = tasbeehVm::tap,
        onSelect = tasbeehVm::select,
        onReset = tasbeehVm::reset,
        onAddCustom = tasbeehVm::addCustom,
        onUpdateCustom = tasbeehVm::updateCustom,
        onRemoveCustom = tasbeehVm::removeCustom,
        onLeave = tasbeehVm::flush,
    )
}
