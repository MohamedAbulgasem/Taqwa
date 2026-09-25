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
import androidx.compose.ui.unit.dp
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
import world.taqwa.app.feature.onboarding.OnboardingStep
import world.taqwa.app.feature.settings.AboutScreen
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.design.components.TaqwaTabScaffold
import world.taqwa.app.nav.openReading
import world.taqwa.app.nav.recitationTarget
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.nav.SystemBackHandler
import world.taqwa.app.nav.Tab
import world.taqwa.app.nav.tabOf
import world.taqwa.app.notifications.NotificationOnboarding
import world.taqwa.app.notifications.canScheduleExactAlarms
import world.taqwa.app.notifications.isNotificationPermissionGranted
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.ui_language
import world.taqwa.app.feature.quran.QuranRootScreen
import world.taqwa.app.feature.recitation.PlayerBar
import world.taqwa.app.feature.recitation.playerBarHeight
import world.taqwa.app.feature.recitation.PlayerBarHost
import world.taqwa.app.resources.recitation_a11y_next_ayah
import world.taqwa.app.resources.recitation_a11y_previous_ayah
import world.taqwa.app.feature.recitation.rememberSurahName
import world.taqwa.app.quran.displayName
import kotlin.time.Clock

@Composable
fun App(container: AppContainer) {
    val settings = container.settingsRepository
    val themeModeState = settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val themeMode by themeModeState
    val prayerSettingsState = settings.prayerSettings.collectAsState(initial = PrayerSettings())
    val notificationSettingsState = settings.notificationSettings.collectAsState(initial = NotificationSettings())
    val widgetBackgroundState = settings.widgetBackground.collectAsState(
        initial = world.taqwa.app.domain.WidgetBackground.FOLLOW_THEME,
    )
    val locationState = settings.location.collectAsState(initial = null)
    val location by locationState
    val locationSourceState = settings.locationSource.collectAsState(initial = LocationSource.MANUAL)
    val navigator = remember { Navigator(Screen.Today) }
    val backStackState = navigator.backStack.collectAsState()
    val backStack by backStackState
    val scope = rememberCoroutineScope()
    val soundPreviewPlayer = remember { createSoundPreviewPlayer() }
    // Recitation (spec 3a §5). One controller for the process, held by the container, so the bar
    // and the voice survive every navigation this screen can perform.
    val recitation = container.recitationController
    val recitationStateState = recitation.state.collectAsState()
    val recitationState by recitationStateState
    // Re-read each time the app comes to the front (below, with the lifecycle): both are granted
    // or revoked in system settings, which the Notifications screen can now send someone to, and
    // its notes have to show the answer they come back with.
    val exactAlarmsAllowedState = remember { mutableStateOf(true) }
    var exactAlarmsAllowed by exactAlarmsAllowedState
    val notificationsGrantedState = remember { mutableStateOf(true) }
    var notificationsGranted by notificationsGrantedState
    suspend fun refreshPermissions() {
        exactAlarmsAllowed = canScheduleExactAlarms()
        notificationsGranted = runCatching { isNotificationPermissionGranted() }.getOrDefault(true)
    }

    // The device locale decides both halves of localisation: which `values-*` strings Compose
    // resolves, and — through this — whether the whole tree is laid out right-to-left. Compose's
    // Row, Arrangement and padding(start=/end=) all consult LocalLayoutDirection, which is what
    // moves the timeline's pip gutter and the header's icon buttons across with no bespoke
    // mirroring code. Only Canvas geometry drawn from literal coordinates needs help; the
    // countdown ring's arc and the settings back chevron do that for themselves.
    //
    // Keyed on the resolved `ui_language` string, not on `Unit`: MainActivity declares `locale`
    // in configChanges, so switching the app's language recreates nothing, and a format built on
    // first composition would go on reporting the language the app *started* in for the rest of
    // the run. Everything downstream — the city search's language, the Prayer view model's, the
    // Quran screens', the widget mirror's — reads its language from this one value, so there is
    // exactly one place that decides what language the app is in.
    val uiLanguage = stringResource(Res.string.ui_language)
    val platformFormat = remember(uiLanguage) { createPlatformFormat() }
    // From the resolved strings, not the locale tag, for the reason given on isRtlLocale(): the
    // tree mirrors exactly when the words on it are Arabic.
    val layoutDirection = if (isRtlLocale()) LayoutDirection.Rtl else LayoutDirection.Ltr
    // The lock screen's two lines are baked when a surah is loaded, and a service outlives the
    // composition that started it, so the controller is told the language rather than asked.
    // Arabic-script interfaces (Arabic, Urdu) get the Arabic names; the rest the Latin ones.
    val arabicUi = uiLanguage().arabicScript
    LaunchedEffect(arabicUi) { recitation.setArabicUi(arabicUi) }
    // The Android notification's two ayah buttons (spec §15.1), in the interface's language.
    val previousAyahLabel = stringResource(Res.string.recitation_a11y_previous_ayah)
    val nextAyahLabel = stringResource(Res.string.recitation_a11y_next_ayah)
    LaunchedEffect(previousAyahLabel, nextAyahLabel) {
        recitation.setAyahButtonLabels(previousAyahLabel, nextAyahLabel)
    }

    // "Show me the ayah being recited" (spec §15.5), from the bar's words, the media
    // notification's tap, or - on iOS, which has no such tap - the app coming to the front with
    // a voice going. A screen already showing the surah is asked to scroll (the token); any other
    // is replaced or pushed with the reader the user reads in, at the ayah, following armed.
    val jumpTokenState = remember { mutableStateOf(0) }
    var jumpToken by jumpTokenState
    val jumpScope = rememberCoroutineScope()
    val openPlaying: () -> Unit = {
        val playing = recitation.state.value.bar
        if (playing != null) {
            jumpScope.launch {
                val current = navigator.current
                val onQuran = navigator.currentTab == Tab.QURAN
                val showsIt = onQuran && ((current is Screen.Reader && current.surah == playing.surah) || current is Screen.Mushaf)
                if (showsIt) {
                    jumpToken++
                } else {
                    runCatching {
                        val reading = settings.readingSettings(platformFormat.languageTag()).first()
                        val target = recitationTarget(reading.mode, playing.surah, playing.ayah) { s, a ->
                            container.quranRepository.pageOf(s, a)
                        }
                        navigator.openReading(target)
                    }
                }
            }
        }
    }
    PlayingLaunchRequests(
        settings = settings,
        openPlaying = openPlaying,
    )
    DebugScreenRequests(
        settings = settings,
        navigator = navigator,
    )
    ForegroundEffect(
        refreshPermissions = ::refreshPermissions,
        recitation = recitation,
        openPlaying = openPlaying,
    )

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
    val onboardingStepState = remember { mutableStateOf(OnboardingStep.WELCOME) }

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

    RecitationLibraryStartUp(
        container = container,
    )

    LanguageChangeReschedule(
        uiLanguage = uiLanguage,
        container = container,
        settings = settings,
        platformFormat = platformFormat,
    )

    AyahPoolMirror(
        uiLanguage = uiLanguage,
        platformFormat = platformFormat,
        settings = settings,
        container = container,
    )

    WidgetLaunchRequests(
        settings = settings,
        platformFormat = platformFormat,
        container = container,
        navigator = navigator,
    )

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

    // Straight off the shared format, which is already rebuilt on a language change.
    val languageTag = platformFormat.languageTag()

    // The stored location's city in the interface's language, for the Settings row and the
    // Location screen. Resolved once, here, rather than in each of those screens: they show the
    // same city and must never disagree about its name, and the lookup parses the bundled city
    // list on first use — twice would be twice that cost. Keyed on the language tag as well as
    // the location so a language change re-resolves; produceState rather than remember because
    // the lookup suspends, and its initial value is the stored English snapshot, which is what
    // both rows showed before this existed.
    val cityDisplayNameState = produceState(location?.cityName, location, languageTag) {
        val current = location
        val id = current?.cityId
        value = if (current == null || id == null) {
            current?.cityName
        } else {
            container.cityRepository.setLanguage(languageTag)
            container.cityRepository.displayName(id) ?: current.cityName
        }
    }

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
                val quranQueryState = remember { mutableStateOf(TextFieldValue()) }
                var quranQuery by quranQueryState

                // The bar belongs to the Quran tab (spec §5.3): on Prayer and Settings it is
                // hidden and playback simply goes on, which is what a media app does when you
                // leave the screen you started it from.
                val quranTab = navigator.currentTab == Tab.QURAN
                // A search belongs to one visit to the Quran tab. It survives the walk into a hit
                // and back, which never leaves the tab; going to Prayer or Settings ends the
                // visit, and coming back finds the surah list rather than last hour's results.
                LaunchedEffect(quranTab) {
                    if (!quranTab) quranQuery = TextFieldValue()
                }
                val bar = recitationState.bar
                val barSurahName = rememberSurahName(bar?.surah) { container.quranRepository.surah(it) }
                val incomingSurahName = rememberSurahName(bar?.incoming?.surah) { container.quranRepository.surah(it) }
                val barSpace = playerBarHeight(bar)
                // A surah skip (spec §15.1) takes the page with the voice: a reader on the surah
                // that was playing is moved to the one now playing, at its first ayah, as the
                // "Next" row at the foot of a surah would move them. A reader on some other surah
                // is left where they are - they were not following. The Mushaf follows by page
                // on its own, since its pages run across surahs.
                val playingSurah = bar?.surah
                var followedSurah by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(playingSurah) {
                    val before = followedSurah
                    // A bar that goes away keeps its surah remembered: a surah that played out and
                    // whose successor had to be fetched first (spec §16.1) comes back as a new
                    // surah on a reader still open on the old one, and that reader follows too.
                    if (playingSurah == null) return@LaunchedEffect
                    followedSurah = playingSurah
                    if (before == null || before == playingSurah) return@LaunchedEffect
                    val current = navigator.current
                    if (current is Screen.Reader && current.surah == before) {
                        navigator.replace(Screen.Reader(playingSurah, 1))
                    }
                }
                TaqwaTabScaffold(
                    tab,
                    navigator::selectTab,
                    bar = {
                        PlayerBarHost(visible = bar != null && quranTab) {
                            if (bar != null) {
                                PlayerBar(
                                    bar = bar,
                                    surahName = barSurahName,
                                    onToggle = recitation::toggle,
                                    onNext = recitation::nextSurah,
                                    onPrevious = recitation::previousSurah,
                                    onNextAyah = recitation::next,
                                    onPreviousAyah = recitation::previous,
                                    onOpenPicker = recitation::openPicker,
                                    onDismiss = recitation::dismissBar,
                                    incomingSurahName = incomingSurahName,
                                    onOpenPlaying = openPlaying,
                                    onSeek = recitation::seekToFraction,
                                )
                            }
                        }
                    },
                ) {
                    when (screen) {
                        Screen.Onboarding -> OnboardingRoute(
                            onboardingStepState = onboardingStepState,
                            container = container,
                            useGpsFix = ::useGpsFix,
                            navigator = navigator,
                            scope = scope,
                            notificationOnboarding = ::notificationOnboarding,
                            settings = settings,
                            exactAlarmsAllowedState = exactAlarmsAllowedState,
                        )

                        Screen.Today -> {
                            TodayRoute(
                                languageTag = languageTag,
                                container = container,
                                settings = settings,
                                platformFormat = platformFormat,
                                useGpsFix = ::useGpsFix,
                                navigator = navigator,
                                notificationSettingsState = notificationSettingsState,
                                exactAlarmsAllowedState = exactAlarmsAllowedState,
                            )
                        }

                        Screen.Quran -> {
                            QuranRootRoute(
                                container = container,
                                settings = settings,
                                platformFormat = platformFormat,
                                quranQueryState = quranQueryState,
                                navigator = navigator,
                            )
                        }

                        is Screen.Reader -> {
                            ReaderRoute(
                                screen = screen,
                                container = container,
                                settings = settings,
                                platformFormat = platformFormat,
                                navigator = navigator,
                                scope = scope,
                                layoutDirection = layoutDirection,
                                recitationStateState = recitationStateState,
                                bar = bar,
                                barSpace = barSpace,
                                jumpTokenState = jumpTokenState,
                                recitation = recitation,
                            )
                        }

                        is Screen.Mushaf -> {
                            MushafRoute(
                                screen = screen,
                                container = container,
                                settings = settings,
                                platformFormat = platformFormat,
                                navigator = navigator,
                                scope = scope,
                                layoutDirection = layoutDirection,
                                recitationStateState = recitationStateState,
                                bar = bar,
                                barSpace = barSpace,
                                jumpTokenState = jumpTokenState,
                                recitation = recitation,
                            )
                        }

                        Screen.Settings -> SettingsRootRoute(
                            locationState = locationState,
                            cityDisplayNameState = cityDisplayNameState,
                            prayerSettingsState = prayerSettingsState,
                            themeModeState = themeModeState,
                            notificationSettingsState = notificationSettingsState,
                            navigator = navigator,
                            recitationStateState = recitationStateState,
                        )

                        Screen.RecitationSettings -> {
                            RecitationSettingsRoute(
                                recitationStateState = recitationStateState,
                                recitation = recitation,
                                navigator = navigator,
                            )
                        }

                        is Screen.RecitationDownloads -> {
                            RecitationDownloadsRoute(
                                screen = screen,
                                recitationStateState = recitationStateState,
                                arabicUi = arabicUi,
                                container = container,
                                recitation = recitation,
                                navigator = navigator,
                                scope = scope,
                            )
                        }

                        Screen.NotificationSettings -> NotificationSettingsRoute(
                            notificationSettingsState = notificationSettingsState,
                            exactAlarmsAllowedState = exactAlarmsAllowedState,
                            notificationsGrantedState = notificationsGrantedState,
                            scope = scope,
                            refreshPermissions = ::refreshPermissions,
                            navigator = navigator,
                            settings = settings,
                            container = container,
                            soundPreviewPlayer = soundPreviewPlayer,
                        )

                        Screen.PrayerTimesSettings -> PrayerTimesSettingsRoute(
                            prayerSettingsState = prayerSettingsState,
                            today = today,
                            write = ::write,
                            navigator = navigator,
                        )

                        Screen.MethodPicker -> MethodPickerRoute(
                            prayerSettingsState = prayerSettingsState,
                            scope = scope,
                            settings = settings,
                            navigator = navigator,
                        )

                        Screen.HighLatitudePicker -> HighLatitudePickerRoute(
                            prayerSettingsState = prayerSettingsState,
                            locationState = locationState,
                            write = ::write,
                            navigator = navigator,
                        )

                        Screen.ManualAdjustments -> ManualAdjustmentsRoute(
                            prayerSettingsState = prayerSettingsState,
                            locationState = locationState,
                            container = container,
                            today = today,
                            write = ::write,
                            navigator = navigator,
                        )

                        Screen.LocationSettings -> LocationSettingsRoute(
                            locationState = locationState,
                            cityDisplayNameState = cityDisplayNameState,
                            locationSourceState = locationSourceState,
                            container = container,
                            useGpsFix = ::useGpsFix,
                            navigator = navigator,
                        )

                        Screen.CitySearch -> CitySearchRoute(
                            container = container,
                            scope = scope,
                            settings = settings,
                            languageTag = languageTag,
                            backStackState = backStackState,
                            onboardingStepState = onboardingStepState,
                            navigator = navigator,
                        )

                        Screen.Appearance -> AppearanceRoute(
                            themeModeState = themeModeState,
                            container = container,
                            scope = scope,
                            settings = settings,
                            widgetBackgroundState = widgetBackgroundState,
                            navigator = navigator,
                        )

                        Screen.About -> AboutScreen(onBack = { navigator.pop() })

                        Screen.Attribution -> {
                            AttributionRoute(
                                container = container,
                                recitationStateState = recitationStateState,
                                navigator = navigator,
                            )
                        }

                        Screen.Qibla -> {
                            QiblaRoute(
                                locationState = locationState,
                                navigator = navigator,
                            )
                        }

                        Screen.Tasbeeh -> {
                            TasbeehRoute(
                                container = container,
                                navigator = navigator,
                            )
                        }
                    }
                }

                CrashReportSheetHost()

                RecitationSheets(
                    recitationStateState = recitationStateState,
                    container = container,
                    recitation = recitation,
                )
            }
        }
    }
}
