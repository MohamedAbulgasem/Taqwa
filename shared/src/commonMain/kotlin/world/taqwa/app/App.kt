package world.taqwa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.i18n.methodDisplayName
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.design.components.TaqwaTabScaffold
import world.taqwa.app.nav.LaunchRequests
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.nav.SystemBackHandler
import world.taqwa.app.nav.Tab
import world.taqwa.app.nav.isQuranScreen
import world.taqwa.app.nav.tabOf
import world.taqwa.app.notifications.NotificationOnboarding
import world.taqwa.app.notifications.canScheduleExactAlarms
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.today_current_location
import world.taqwa.app.resources.ui_language
import world.taqwa.app.feature.qibla.QiblaScreen
import world.taqwa.app.feature.qibla.QiblaViewModel
import world.taqwa.app.feature.quran.MushafScreen
import world.taqwa.app.feature.quran.MushafUiState
import world.taqwa.app.feature.quran.MushafViewModel
import world.taqwa.app.feature.quran.QuranRootScreen
import world.taqwa.app.feature.quran.QuranRootViewModel
import world.taqwa.app.feature.quran.ReaderScreen
import world.taqwa.app.feature.quran.ReaderUiState
import world.taqwa.app.feature.quran.ReaderViewModel
import world.taqwa.app.qibla.createCompassSource
import world.taqwa.app.qibla.createHaptics
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.displayName
import world.taqwa.app.widget.AyahPoolMirrorWriter
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.refreshWidgets
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
    // From the resolved strings, not the locale tag, for the reason given on isRtlLocale(): the
    // tree mirrors exactly when the words on it are Arabic.
    val layoutDirection = if (isRtlLocale()) LayoutDirection.Rtl else LayoutDirection.Ltr

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

    // Ayah widget pool mirror (design spec §4): fills the widget KeyValueStore from the Quran
    // database once on start, again whenever the reading translation changes, and again whenever
    // the UI language does, so neither widget process ever has to open the database itself. The
    // database work runs off the main thread, and a failure here (a locked store, a database that
    // failed to open) is swallowed rather than crashing the app — the mirror simply stays stale
    // until the next successful write.
    //
    // Keyed on the *resolved* `ui_language` string rather than on `Unit`, which is what spec §4's
    // "whenever the UI language changes" needs on Android: MainActivity declares `configChanges`
    // for locale, so nothing recreates when the user switches the app's language — the composition
    // simply re-resolves its strings. `ui_language` is the same string `isRtlLocale()` reads, so
    // this effect restarts on exactly the changes that flip the card's script. Restarting writes
    // the mirror once and then re-collects; the write ends in `refreshWidgets()`, which touches
    // nothing this key reads, so there is no loop.
    val uiLanguage = stringResource(Res.string.ui_language)
    LaunchedEffect(uiLanguage) {
        // A *fresh* format each run: the remembered `platformFormat` was built on first
        // composition and still reports the language tag the app started in, which is the whole
        // bug this key exists to fix. It is kept, not just asked for its tag, because the mirror
        // also records the digit set this same format renders (D2).
        val format = createPlatformFormat()
        val languageTag = format.languageTag()
        val store = createWidgetKeyValueStore()
        suspend fun writeMirror() {
            runCatching {
                val reading = settings.readingSettings(languageTag).first()
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        AyahPoolMirrorWriter.write(store, container.quranRepository, reading, languageTag, format)
                    }
                }
                result.onSuccess { refreshWidgets() }
            }
        }
        writeMirror()
        runCatching {
            settings.readingSettings(languageTag)
                .map { it.translationId }
                .distinctUntilChanged()
                .drop(1)
                .collect { writeMirror() }
        }
    }

    // Widget tap launch requests (design spec §8). Waits for onboarding to be known complete before
    // collecting: on a cold start the back stack still shows Screen.Today until the onboarding
    // effect above has read DataStore and (if needed) replaced it with Screen.Onboarding, so acting
    // on a pending request before that point would push a screen only to have replaceAll wipe it out
    // right after. Once onboarding is known complete it can never become incomplete again in this
    // session, so a plain collect on the pending flow is enough — a warm tap, where onboarding is
    // already behind the user, is simply the first emission this effect ever sees.
    LaunchedEffect(Unit) {
        settings.onboardingComplete.filter { it }.first()
        LaunchRequests.pendingAyah.collect { pending ->
            val (surah, ayah) = pending ?: return@collect
            runCatching {
                val reading = settings.readingSettings(platformFormat.languageTag()).first()
                // The reader is pushed on top of the Quran root, not on whatever happened to be
                // showing. Without this a widget tap put the reader straight on top of the Prayer
                // tab, so one Back left the Quran entirely and reaching the surah list — the list
                // the ayah came from — took another tap (D3, S23 round). Skipped when a Quran
                // screen is already on top, so a second tap while the reader is open does not
                // stack a root behind it.
                if (!isQuranScreen(navigator.current)) navigator.push(Screen.Quran)
                if (reading.mode == ReadingMode.MUSHAF) {
                    navigator.push(Screen.Mushaf(container.quranRepository.pageOf(surah, ayah)))
                } else {
                    navigator.push(Screen.Reader(surah, ayah))
                }
            }
            // Consumed unconditionally: a bad request (e.g. a database failure resolving the page)
            // must not be retried forever on every future emission.
            LaunchRequests.consume()
        }
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

                // The Quran field's text lives out here, not in QuranRootScreen: opening a hit
                // pushes the reader, which disposes the root and its view model both, and spec 2b
                // §2.1 says the query is still in the field when you come back. A TextFieldValue,
                // so the caret comes back with it rather than jumping to the start of the text.
                var quranQuery by remember { mutableStateOf(TextFieldValue()) }

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
                                    bookmarks = container.bookmarkStore,
                                    languageTag = platformFormat.languageTag(),
                                )
                            }
                            LaunchedEffect(viewModel) {
                                viewModel.load()
                                viewModel.start(this)
                                // The field outlived the screen (see quranQuery); the view model
                                // did not, so the restored query is searched again on the way back.
                                // start() first: setFilter needs the scope to run its search.
                                // immediate: nobody is typing, so the debounce would only leave the
                                // ayah section blank for a quarter of a second on the way back.
                                if (quranQuery.text.isNotEmpty()) {
                                    viewModel.setFilter(quranQuery.text, immediate = true)
                                }
                            }
                            val quranState by viewModel.state.collectAsState()
                            QuranRootScreen(
                                state = quranState,
                                query = quranQuery,
                                onQueryChange = { value ->
                                    // Only when the text itself changed: a TextFieldValue also
                                    // changes on a caret move or a selection, and setFilter
                                    // cancels the running search and re-queries, which would blank
                                    // the ayah section for the debounce every time the field is
                                    // tapped.
                                    val changed = value.text != quranQuery.text
                                    quranQuery = value
                                    if (changed) viewModel.setFilter(value.text)
                                },
                                onTabChange = viewModel::setTab,
                                pageFor = viewModel::pageFor,
                                onOpenReader = { surah, ayah -> navigator.push(Screen.Reader(surah, ayah)) },
                                onOpenMushaf = { page -> navigator.push(Screen.Mushaf(page)) },
                                onRemoveBookmark = viewModel::removeBookmark,
                            )
                        }

                        is Screen.Reader -> {
                            val viewModel = remember(screen) {
                                ReaderViewModel(
                                    source = container.quranRepository,
                                    settings = settings,
                                    bookmarks = container.bookmarkStore,
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
                                onToggleBookmark = viewModel::toggleBookmark,
                                // The surah's name and the reference digits follow the UI's own
                                // language (spec 2b §2.3), which only this layer knows — so the
                                // view model is handed both rather than resolving them itself.
                                shareTextFor = { ayah ->
                                    (readerState as? ReaderUiState.Ready)?.let { ready ->
                                        viewModel.shareTextFor(
                                            ayah = ayah,
                                            surahName = ready.surah.displayName(layoutDirection == LayoutDirection.Rtl),
                                            digits = platformFormat::localizedDigits,
                                        )
                                    }
                                },
                            )
                        }

                        is Screen.Mushaf -> {
                            val viewModel = remember(screen) {
                                MushafViewModel(
                                    source = container.quranRepository,
                                    settings = settings,
                                    bookmarks = container.bookmarkStore,
                                    languageTag = platformFormat.languageTag(),
                                    startPage = screen.page,
                                )
                            }
                            LaunchedEffect(viewModel) { viewModel.start(this) }
                            val mushafState by viewModel.state.collectAsState()
                            MushafScreen(
                                state = mushafState,
                                startPage = screen.page,
                                pageLoader = viewModel::page,
                                onBack = { navigator.pop() },
                                onToggleMode = {
                                    scope.launch {
                                        val (surah, ayah) = viewModel.switchToReader()
                                        navigator.replace(Screen.Reader(surah, ayah))
                                    }
                                },
                                onChangeSettings = viewModel::updateSettings,
                                onPageShown = viewModel::onPageShown,
                                onToggleBookmark = viewModel::toggleBookmark,
                                // As in the reader's branch: the surah's name and the reference
                                // digits are the UI language's business, so this layer resolves
                                // them. A page can straddle two surahs, so the name is looked up
                                // by the tapped ayah's own surah, never the header's.
                                shareTextFor = { surah, ayah ->
                                    (mushafState as? MushafUiState.Ready)?.surahsByNumber?.get(surah)?.let { named ->
                                        viewModel.shareTextFor(
                                            surah = surah,
                                            ayah = ayah,
                                            surahName = named.displayName(layoutDirection == LayoutDirection.Rtl),
                                            digits = platformFormat::localizedDigits,
                                        )
                                    }
                                },
                            )
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

                        Screen.Attribution -> {
                            // The translation credits must never drift from what is actually
                            // bundled (spec §6), so they are read from the database rather than
                            // hardcoded; an empty list while loading is fine, it fills in a frame
                            // later.
                            val translations by produceState(initialValue = emptyList<world.taqwa.app.quran.TranslationInfo>()) {
                                value = container.quranRepository.translations()
                            }
                            AttributionScreen(translations = translations, onBack = { navigator.pop() })
                        }

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
