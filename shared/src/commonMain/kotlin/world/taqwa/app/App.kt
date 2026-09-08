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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.audio.createSoundPreviewPlayer
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaTheme
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
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
import world.taqwa.app.feature.settings.themeDisplayName
import world.taqwa.app.feature.today.TodayScreen
import world.taqwa.app.feature.today.TodayViewModel
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.i18n.methodDisplayName
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.design.components.TaqwaTabScaffold
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.nav.SystemBackHandler
import world.taqwa.app.nav.Tab
import world.taqwa.app.nav.tabOf
import world.taqwa.app.notifications.NotificationOnboarding
import world.taqwa.app.notifications.canScheduleExactAlarms
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.today_current_location
import world.taqwa.app.feature.qibla.QiblaScreen
import world.taqwa.app.feature.qibla.QiblaViewModel
import world.taqwa.app.feature.quran.QuranRootScreen
import world.taqwa.app.feature.quran.QuranRootViewModel
import world.taqwa.app.feature.quran.ReaderScreen
import world.taqwa.app.feature.quran.ReaderViewModel
import world.taqwa.app.qibla.createCompassSource
import world.taqwa.app.qibla.createHaptics
import kotlin.time.Clock

@Composable
fun App(container: AppContainer) {
    val settings = container.settingsRepository
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val prayerSettings by settings.prayerSettings.collectAsState(initial = PrayerSettings())
    val notificationSettings by settings.notificationSettings.collectAsState(initial = NotificationSettings())
    val widgetBackground by settings.widgetBackground.collectAsState(
        initial = world.taqwa.app.domain.WidgetBackground.FOLLOW_THEME,
    )
    val location by settings.location.collectAsState(initial = null)
    val locationSource by settings.locationSource.collectAsState(initial = LocationSource.MANUAL)
    val navigator = remember { Navigator(Screen.Today) }
    val backStack by navigator.backStack.collectAsState()
    val scope = rememberCoroutineScope()
    val soundPreviewPlayer = remember { createSoundPreviewPlayer() }
    // Read once per composition rather than per frame: the user can only change it by leaving
    // the app for system settings, which recreates this anyway.
    val exactAlarmsAllowed = remember { canScheduleExactAlarms() }

    // The device locale decides both halves of localisation: which `values-*` strings Compose
    // resolves, and — through this — whether the whole tree is laid out right-to-left. Compose's
    // Row, Arrangement and padding(start=/end=) all consult LocalLayoutDirection, which is what
    // moves the timeline's pip gutter and the header's icon buttons across with no bespoke
    // mirroring code. Only Canvas geometry drawn from literal coordinates needs help; the
    // countdown ring's arc and the settings back chevron do that for themselves.
    val platformFormat = remember { createPlatformFormat() }
    val layoutDirection = remember(platformFormat) {
        if (world.taqwa.app.i18n.LayoutDirection.isRtl(platformFormat.languageTag())) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

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
            container.locationRepository.resolveGpsLocation(container.cityRepository)?.let {
                settings.setLocation(it)
                // Only on a fix that actually arrived: a granted permission that then fails to
                // produce coordinates must not leave "Use my location" claiming to be on.
                settings.setLocationSource(LocationSource.GPS)
                // Spec §249: method auto-detection from the resolved country. A no-op once the
                // user has picked a method themselves.
                settings.applyCountryDefaultMethod(it.countryCode)
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

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalPlatformFormat provides platformFormat,
    ) {
        TaqwaTheme(themeMode) {
            Box(Modifier.fillMaxSize().background(LocalTaqwaColors.current.background)) {
                if (!startResolved) return@Box
                val screen = backStack.last()
                // The bar belongs to the three tab roots. A pushed sub-screen keeps the whole
                // screen and the back link it already had; onboarding has neither.
                val tab = tabOf(screen)

                // Android's back button walks the same stack as the on-screen chevron, and then
                // — the platform convention — falls back from any other tab root to Prayer. Only
                // the Prayer screen itself lets the gesture through to leave the app.
                SystemBackHandler(enabled = backStack.size > 1 || (tab != null && tab != Tab.PRAYER)) {
                    if (!navigator.pop()) navigator.selectTab(Tab.PRAYER)
                }

                TaqwaTabScaffold(tab, navigator::selectTab) {
                    when (screen) {
                        Screen.Onboarding -> OnboardingScreen(
                            step = onboardingStep,
                            onStep = { onboardingStep = it },
                            locationRepository = container.locationRepository,
                            widgetPinRequester = container.widgetPinRequester,
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
                                    format = platformFormat,
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

                            val requestLocation = world.taqwa.app.location
                                .rememberLocationPermissionRequester(container.locationRepository) {
                                    if (it == LocationPermission.GRANTED) useGpsFix()
                                }

                            TodayScreen(
                                state = state,
                                onChooseCity = { navigator.push(Screen.CitySearch) },
                                onAllowLocation = requestLocation,
                                onOpenQibla = { navigator.push(Screen.Qibla) },
                            )
                        }

                        Screen.Quran -> {
                            val viewModel = remember {
                                QuranRootViewModel(
                                    source = container.quranRepository,
                                    settings = settings,
                                    languageTag = platformFormat.languageTag(),
                                )
                            }
                            LaunchedEffect(viewModel) { viewModel.load() }
                            val quranState by viewModel.state.collectAsState()
                            QuranRootScreen(
                                state = quranState,
                                onFilterChange = viewModel::setFilter,
                                onTabChange = viewModel::setTab,
                                pageFor = viewModel::pageFor,
                                onOpenReader = { surah, ayah -> navigator.push(Screen.Reader(surah, ayah)) },
                                onOpenMushaf = { page -> navigator.push(Screen.Mushaf(page)) },
                            )
                        }

                        is Screen.Reader -> {
                            val viewModel = remember(screen) {
                                ReaderViewModel(
                                    source = container.quranRepository,
                                    settings = settings,
                                    languageTag = platformFormat.languageTag(),
                                    surah = screen.surah,
                                )
                            }
                            LaunchedEffect(viewModel) { viewModel.start(this) }
                            val readerState by viewModel.state.collectAsState()
                            ReaderScreen(
                                state = readerState,
                                initialAyah = screen.ayah,
                                onBack = { navigator.pop() },
                                onToggleMode = {
                                    scope.launch {
                                        val page = viewModel.switchToMushaf()
                                        navigator.replace(Screen.Mushaf(page))
                                    }
                                },
                                onChangeSettings = viewModel::updateSettings,
                                onFirstVisibleAyah = viewModel::onFirstVisibleAyah,
                                onOpenNextSurah = { next -> navigator.replace(Screen.Reader(next, 1)) },
                            )
                        }

                        // TODO(slice2a task 5/6/8): replace with the real mushaf screen.
                        is Screen.Mushaf -> Box(
                            Modifier.fillMaxSize().background(LocalTaqwaColors.current.background),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            androidx.compose.material3.Text("Mushaf")
                        }

                        Screen.Settings -> SettingsRootScreen(
                            cityName = location?.let {
                                it.cityName ?: stringResource(Res.string.today_current_location)
                            },
                            methodName = methodDisplayName(prayerSettings.method),
                            themeName = themeDisplayName(themeMode),
                            notificationSettings = notificationSettings,
                            onOpenLocation = { navigator.push(Screen.LocationSettings) },
                            onOpenPrayerTimes = { navigator.push(Screen.PrayerTimesSettings) },
                            onOpenNotifications = { navigator.push(Screen.NotificationSettings) },
                            onOpenAppearance = { navigator.push(Screen.Appearance) },
                            onOpenAttribution = { navigator.push(Screen.Attribution) },
                        )

                        Screen.NotificationSettings -> NotificationSettingsScreen(
                            settings = notificationSettings,
                            exactAlarmsUnavailable = !exactAlarmsAllowed,
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
                            onStopPreview = { soundPreviewPlayer.stop() },
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
                                scope.launch {
                                    settings.setPrayerSettings(prayerSettings.copy(method = it))
                                    // Latches the choice so a later relocation cannot overwrite it.
                                    settings.setMethodUserChosen()
                                }
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
                            locationSource = locationSource,
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
                                scope.launch {
                                    val picked = city.toGeoLocation()
                                    settings.setLocation(picked)
                                    // The only writer of MANUAL, which is what makes turning the
                                    // toggle off reversible: back out of the search and the
                                    // stored source — and so the toggle — is untouched.
                                    settings.setLocationSource(LocationSource.MANUAL)
                                    settings.applyCountryDefaultMethod(picked.countryCode)
                                }
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
                            widgetBackground = widgetBackground,
                            onPickWidgetBackground = { value ->
                                scope.launch {
                                    settings.setWidgetBackground(value)
                                    // The widget processes never see SettingsRepository/DataStore —
                                    // only the mirror — so the choice has to be written there too,
                                    // under the same key TaqwaGlanceWidget.kt (Android) and the iOS
                                    // TimelineProvider (Task 24) read.
                                    world.taqwa.app.widget.WidgetMirrorWriter.writeBackground(
                                        world.taqwa.app.widget.createWidgetKeyValueStore(),
                                        value,
                                    )
                                    world.taqwa.app.widget.refreshWidgets()
                                }
                            },
                            onBack = { navigator.pop() },
                        )

                        Screen.Attribution -> AttributionScreen(onBack = { navigator.pop() })

                        Screen.Qibla -> {
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
                                LaunchedEffect(vm) { vm.start(this) }
                                val qiblaState by vm.state.collectAsState()
                                QiblaScreen(qiblaState, onBack = { navigator.pop() })
                            }
                        }
                    }
                }
            }
        }
    }
}
