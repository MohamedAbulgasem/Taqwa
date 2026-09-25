package world.taqwa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
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
import world.taqwa.app.feature.onboarding.OnboardingStep
import world.taqwa.app.feature.quran.QuranRootUiState
import world.taqwa.app.feature.settings.AboutScreen
import world.taqwa.app.feature.today.TodayScreen
import world.taqwa.app.feature.today.TodayUiState
import world.taqwa.app.feature.today.TodayViewModel
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaTabScaffold
import world.taqwa.app.nav.LaunchRequests
import world.taqwa.app.nav.openReading
import world.taqwa.app.nav.recitationTarget
import world.taqwa.app.recitation.foregroundReturnsToRecitation
import world.taqwa.app.nav.ayahWidgetTarget
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.nav.SystemBackHandler
import world.taqwa.app.nav.Tab
import world.taqwa.app.nav.tabOf
import world.taqwa.app.notifications.NotificationOnboarding
import world.taqwa.app.notifications.canScheduleExactAlarms
import world.taqwa.app.notifications.isNotificationPermissionGranted
import world.taqwa.app.notifications.requestExactAlarmAccess
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.ui_language
import world.taqwa.app.feature.qibla.QiblaScreen
import world.taqwa.app.feature.qibla.QiblaViewModel
import world.taqwa.app.about.AboutLinks
import world.taqwa.app.crash.ReportMail
import world.taqwa.app.crash.crashLogStore
import world.taqwa.app.crash.deviceInfoOrUnknown
import world.taqwa.app.feature.crash.CrashReportSheet
import world.taqwa.app.feature.tasbeeh.TasbeehScreen
import world.taqwa.app.feature.tasbeeh.TasbeehViewModel
import world.taqwa.app.resources.crash_mail_no_report
import world.taqwa.app.feature.quran.MushafScreen
import world.taqwa.app.feature.quran.MushafUiState
import world.taqwa.app.feature.quran.MushafViewModel
import world.taqwa.app.feature.quran.QuranRootScreen
import world.taqwa.app.feature.quran.QuranRootViewModel
import world.taqwa.app.feature.quran.ReaderScreen
import world.taqwa.app.feature.quran.ReaderUiState
import world.taqwa.app.feature.quran.ReaderViewModel
import world.taqwa.app.feature.recitation.DownloadSheet
import world.taqwa.app.feature.recitation.PlayerBar
import world.taqwa.app.feature.recitation.playerBarHeight
import world.taqwa.app.feature.recitation.PlayerBarHost
import world.taqwa.app.feature.recitation.QuranRecitation
import world.taqwa.app.feature.recitation.ReciterPicker
import world.taqwa.app.resources.recitation_a11y_next_ayah
import world.taqwa.app.resources.recitation_a11y_previous_ayah
import world.taqwa.app.feature.recitation.RecitationStorage
import world.taqwa.app.feature.recitation.reciterName
import world.taqwa.app.feature.settings.DownloadedSurah
import world.taqwa.app.feature.settings.RecitationDownloadsScreen
import world.taqwa.app.feature.settings.RecitationSettingsScreen
import world.taqwa.app.feature.recitation.rememberSurahName
import world.taqwa.app.qibla.createCompassSource
import world.taqwa.app.qibla.createHaptics
import world.taqwa.app.quran.displayName
import world.taqwa.app.widget.AyahPoolMirrorWriter
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.refreshWidgets
import kotlin.time.Clock

@Composable
fun App(container: AppContainer) {
    val settings = container.settingsRepository
    val themeModeState = settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val themeMode by themeModeState
    val prayerSettingsState = settings.prayerSettings.collectAsState(initial = PrayerSettings())
    val notificationSettingsState = settings.notificationSettings.collectAsState(initial = NotificationSettings())
    val notificationSettings by notificationSettingsState
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
    var jumpToken by remember { mutableStateOf(0) }
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
    LaunchedEffect(Unit) {
        settings.onboardingComplete.filter { it }.first()
        LaunchRequests.pendingPlaying.collect { asked ->
            if (asked) {
                openPlaying()
                LaunchRequests.consumePlaying()
            }
        }
    }
    // The debug harnesses' "open this screen" (see LaunchRequests.openScreen): a tab root, or a
    // sub-screen on top of its own tab, exactly as a finger would reach it.
    LaunchedEffect(Unit) {
        settings.onboardingComplete.filter { it }.first()
        LaunchRequests.pendingScreen.collect { name ->
            name ?: return@collect
            when (name) {
                "prayer" -> navigator.selectTab(Tab.PRAYER)
                "quran" -> navigator.selectTab(Tab.QURAN)
                "settings" -> navigator.selectTab(Tab.SETTINGS)
                "notifications" -> { navigator.selectTab(Tab.SETTINGS); navigator.push(Screen.NotificationSettings) }
                "appearance" -> { navigator.selectTab(Tab.SETTINGS); navigator.push(Screen.Appearance) }
                "qibla" -> { navigator.selectTab(Tab.PRAYER); navigator.push(Screen.Qibla) }
                "tasbeeh" -> { navigator.selectTab(Tab.PRAYER); navigator.push(Screen.Tasbeeh) }
            }
            LaunchRequests.consumeScreen()
        }
    }
    val appLifecycle = LocalLifecycleOwner.current
    LaunchedEffect(appLifecycle) {
        appLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshPermissions()
            if (foregroundReturnsToRecitation && recitation.state.value.bar?.playing == true) openPlaying()
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

    // Recitation library reconciliation (spec 3a §7). The registry in DataStore is what every
    // recitation screen reads, and it can fall out of step with the disk without the app being
    // involved at all — the system clearing app storage, a commit that did not survive the process
    // being killed, a reinstall over files that were left behind. One pass at start puts the two
    // back in agreement, in both directions, before anything can be tapped.
    //
    // Off the main thread because it stats every downloaded surah, and swallowed on failure for
    // the same reason the mirror write is: a library that could not be scanned is a stale registry,
    // which the next start fixes, and not a reason to fail a launch.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { container.recitationLibrary.reconcile() }
            // ── Recitation catalogue refresh (spec 3a §4, slice 3a task 2) ──────────
            // After the reconciliation and on the same background pass: at most one fetch of
            // manifest.json a day, silent about every way it can fail. A reader who is offline
            // keeps yesterday's catalogue, or the one bundled with the build. And nothing at all
            // until the reader has used recitation — the refresher checks that itself (privacy
            // spec §2), which is why this line can stay unconditional.
            runCatching { container.manifestRefresher.refreshIfStale() }
            // ── end recitation catalogue refresh ────────────────────────────────────
        }
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
    // The other two things written in the interface language outside the composition: the
    // notifications already armed (title, body and channel names are baked at schedule time)
    // and the prayer widgets' mirror (written by Today's view model, which only exists while the
    // Prayer tab is on screen). Both would otherwise keep the previous language after an in-place
    // switch — the normal case on Android 13+, where the per-app language page returns to a
    // process that is still alive — until the next cold start or the twice-daily top-up. Skipped
    // on the first composition: the cold-start path already reschedules and Today writes its
    // own mirror.
    val languageAtStart = remember { uiLanguage }
    var lastLanguageApplied by remember { mutableStateOf(languageAtStart) }
    LaunchedEffect(uiLanguage) {
        if (uiLanguage == lastLanguageApplied) return@LaunchedEffect
        lastLanguageApplied = uiLanguage
        container.notificationCoordinator.reschedule(world.taqwa.app.notifications.RescheduleTrigger.SETTINGS_CHANGED)
        world.taqwa.app.widget.WidgetMirrorRefresher.refresh(settings, container.prayerTimesEngine, format = platformFormat)
        refreshWidgets()
    }

    LaunchedEffect(uiLanguage) {
        // `platformFormat` is itself keyed on `uiLanguage`, so by the time this effect restarts
        // it already reports the new language. It is kept, not just asked for its tag, because
        // the mirror also records the digit set this same format renders (D2).
        val format = platformFormat
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
                // The reader the user actually reads in, with the tapped ayah already picked out:
                // selected and showing its actions in translation mode, highlighted with its
                // reference bar in Mushaf mode. Resolved before the navigator is touched at all,
                // because `pageOf` is itself a database read that can fail and a failure must
                // leave the back stack exactly as it found it rather than half-applying a push.
                val reading = settings.readingSettings(platformFormat.languageTag()).first()
                val target = ayahWidgetTarget(reading.mode, surah, ayah) { s, a ->
                    container.quranRepository.pageOf(s, a)
                }
                // On the Quran tab, with the Quran root at the bottom of the stack: a tap from
                // the Prayer tab used to push the root and the reader on top of Prayer, which
                // kept the tab current at Prayer and so hid the player bar (spec §5.3 draws it
                // only on the Quran tab) until the tab was left and re-entered. `openReading`
                // also keeps the D3 rule: one Back from the reader reaches the surah list.
                navigator.openReading(target)
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
                var quranQuery by remember { mutableStateOf(TextFieldValue()) }

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
                            // The debug harnesses' typed search (see LaunchRequests.search).
                            LaunchedEffect(viewModel) {
                                LaunchRequests.pendingSearch.collect { asked ->
                                    asked ?: return@collect
                                    viewModel.state.first { it is QuranRootUiState.Ready }
                                    quranQuery = TextFieldValue(asked)
                                    viewModel.setFilter(asked, immediate = true)
                                    LaunchRequests.consumeSearch()
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
                                selectInitialAyah = screen.selectAyah,
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
                                recitation = QuranRecitation(
                                    header = recitationState.header(screen.surah),
                                    playing = bar?.let { it.surah to it.ayah },
                                    live = bar?.playing == true,
                                    barSpace = barSpace,
                                    jumpToken = jumpToken,
                                    onHeader = recitation::onHeaderTap,
                                    onPlayAyah = recitation::requestPlay,
                                    onToggle = recitation::toggle,
                                ),
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
                                initialHighlight = screen.highlightSurah?.let { sur ->
                                    screen.highlightAyah?.let { a -> sur to a }
                                },
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
                                recitation = QuranRecitation(
                                    // The page's own surah, which is the one the header names.
                                    // Whether the voice is on *this page* is a question only the
                                    // Mushaf can answer — it knows which page the recited ayah is
                                    // printed on — so it is the screen that raises this to
                                    // Playing when the ayah is here.
                                    header = recitationState.header(
                                        (mushafState as? MushafUiState.Ready)?.surah?.number ?: 0,
                                    ),
                                    playing = bar?.let { it.surah to it.ayah },
                                    live = bar?.playing == true,
                                    barSpace = barSpace,
                                    jumpToken = jumpToken,
                                    onHeader = recitation::onHeaderTap,
                                    onPlayAyah = recitation::requestPlay,
                                    onToggle = recitation::toggle,
                                ),
                                pageOfAyah = { surah, ayah -> container.quranRepository.pageOf(surah, ayah) },
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
                            // Off the disk, so it is asked for rather than observed: the scan
                            // stats every downloaded file. Re-read whenever the registry moves —
                            // a download committing, a delete — which is exactly when the number
                            // on screen would otherwise be wrong.
                            val byReciter = recitationState.downloadedByReciter
                            val storage by produceState(RecitationStorage(), byReciter) {
                                value = withContext(Dispatchers.Default) { recitation.storage() }
                            }
                            RecitationSettingsScreen(
                                state = recitationState,
                                storage = storage,
                                onBack = { navigator.pop() },
                                onOpened = recitation::onSettingsOpened,
                                onOpenPicker = recitation::openPicker,
                                onSetMobileData = recitation::setDownloadOnMobileData,
                                onSetAutoDownload = recitation::setAutoDownload,
                                onOpenDownloads = { navigator.push(Screen.RecitationDownloads(it)) },
                                onDownloadWholeQuran = { recitation.downloadWholeQuran() },
                                onCancelWholeQuran = recitation::cancelWholeQuran,
                            )
                        }

                        is Screen.RecitationDownloads -> {
                            val reciter = recitationState.reciters.firstOrNull { it.id == screen.reciterId }
                            val owned = recitationState.downloadedByReciter[screen.reciterId].orEmpty()
                            // The rows are the intersection of the registry and the catalogue: a
                            // size can only come from the manifest, and a surah on the phone that
                            // this manifest no longer publishes has no size to print.
                            val rows by produceState(emptyList<DownloadedSurah>(), reciter, owned, arabicUi) {
                                val voice = reciter
                                value = if (voice == null) {
                                    emptyList()
                                } else {
                                    runCatching {
                                        val names = container.quranRepository.surahs()
                                            .associate { it.number to it.displayName(arabicUi) }
                                        voice.surahs
                                            .filter { it.n in owned }
                                            .sortedBy { it.n }
                                            .map { DownloadedSurah(it.n, names[it.n].orEmpty(), it.bytes) }
                                    }.getOrDefault(emptyList())
                                }
                            }
                            val bytes by produceState(0L, screen.reciterId, owned) {
                                value = withContext(Dispatchers.Default) {
                                    recitation.storage().of(screen.reciterId)
                                }
                            }
                            // Popped when the last surah goes, rather than left on an empty
                            // screen whose title names a reciter with nothing under it.
                            LaunchedEffect(reciter, owned) {
                                if (reciter != null && owned.isEmpty()) navigator.pop()
                            }
                            RecitationDownloadsScreen(
                                reciterName = reciter?.let { reciterName(it) }.orEmpty(),
                                surahs = rows,
                                totalBytes = bytes,
                                onBack = { navigator.pop() },
                                onDelete = { surah ->
                                    scope.launch { recitation.deleteSurah(screen.reciterId, surah) }
                                },
                                onDeleteAll = {
                                    scope.launch { recitation.deleteReciter(screen.reciterId) }
                                },
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

                        Screen.Tasbeeh -> {
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
                    }
                }

                // The crash sheet (crash spec §3.1): read once per process, shown until answered.
                // Send and Not now both mark the report offered, so it never comes back for the
                // same crash; the report itself stays for the About screen's row.
                var crashOffer by remember { mutableStateOf(crashLogStore.pendingOffer()) }
                val uriHandler = LocalUriHandler.current
                val noReportLine = stringResource(Res.string.crash_mail_no_report)
                crashOffer?.let { report ->
                    CrashReportSheet(
                        onSend = {
                            crashLogStore.markOffered(report)
                            crashOffer = null
                            val info = deviceInfoOrUnknown()
                            val mail = ReportMail.mailto(
                                AboutLinks.SUPPORT_EMAIL,
                                ReportMail.subject(info),
                                ReportMail.body(info, report, noReportLine),
                            )
                            // A phone with no mail app fails silently rather than crashing again.
                            runCatching { uriHandler.openUri(mail) }
                        },
                        onDismiss = {
                            crashLogStore.markOffered(report)
                            crashOffer = null
                        },
                    )
                }

                // The two recitation sheets are hosted here, outside the scaffold, for the same
                // reason the bar is inside it: they can be opened from the reader's header, from
                // an ayah row, or from the player bar on any Quran screen, and a sheet owned by
                // one of those screens would go with it.
                val sheet = recitationState.sheet
                if (sheet != null) {
                    val sheetSurahName = rememberSurahName(sheet.surah) { container.quranRepository.surah(it) }
                    TaqwaBottomSheet(onDismissRequest = recitation::dismissSheet) {
                        DownloadSheet(
                            sheet = sheet,
                            surahName = sheetSurahName,
                            wholeQuran = recitationState.wholeQuran,
                            onConfirm = recitation::confirmDownload,
                            onWholeQuran = recitation::downloadWholeQuran,
                            onCancel = recitation::cancelDownload,
                            onRetry = recitation::retryDownload,
                            onChangeReciter = recitation::openPicker,
                        )
                    }
                }
                if (recitationState.pickerOpen) {
                    TaqwaBottomSheet(onDismissRequest = recitation::closePicker) {
                        ReciterPicker(
                            reciters = recitationState.reciters,
                            currentId = recitationState.reciter?.id,
                            downloadedCounts = recitationState.downloadedCounts,
                            previewable = recitationState.previewable,
                            onPick = recitation::pickReciter,
                            onPreview = recitation::previewReciter,
                        )
                    }
                }
            }
        }
    }
}
