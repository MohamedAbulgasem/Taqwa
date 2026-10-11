package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.adhkar.AdhkarCatalogue
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.design.KeepScreenOn
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.feature.adhkar.AdhkarReadingSheet
import world.taqwa.app.feature.adhkar.AdhkarScreen
import world.taqwa.app.feature.adhkar.AdhkarViewModel
import world.taqwa.app.feature.adhkar.recited
import world.taqwa.app.feature.qibla.QiblaScreen
import world.taqwa.app.feature.qibla.QiblaViewModel
import world.taqwa.app.feature.recitation.rememberSurahName
import world.taqwa.app.feature.tasbeeh.TasbeehScreen
import world.taqwa.app.feature.tasbeeh.TasbeehViewModel
import world.taqwa.app.feature.today.TodayScreen
import world.taqwa.app.feature.today.TodayUiState
import world.taqwa.app.feature.today.TodayViewModel
import world.taqwa.app.feature.today.OneTimeCardSession
import world.taqwa.app.feature.today.clockSetByHand
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.notifications.adhkarNotificationId
import world.taqwa.app.notifications.openAppSettings
import world.taqwa.app.notifications.requestExactAlarmAccess
import world.taqwa.app.qibla.createCompassSource
import world.taqwa.app.qibla.createHaptics
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhkar_evening
import world.taqwa.app.resources.adhkar_morning
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
            // A backfilled country or region can change the timetable (ruling R53).
            onLocationBackfilled = { refreshForNewTimes(container, settings, platformFormat) },
            // A language change swaps the view model; without this the
            // new one would start at Loading and blank the whole screen
            // until its first refresh landed.
            initialState = lastState.value,
            clockSetByHand = ::clockSetByHand,
            // The process's own: an answered card keeps the other one
            // away until the next launch, across a language change too.
            cardSession = OneTimeCardSession.process,
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
    val scope = rememberCoroutineScope()

    // "No location yet" sends someone to the system's settings (see TodayScreen), so coming back
    // with location allowed has to be enough: the fix is fetched on every return while there is
    // still no place, and on arriving here with access already granted but no fix yet.
    val needsLocation = state is TodayUiState.NeedsLocation
    LaunchedEffect(needsLocation, lifecycleOwner) {
        if (!needsLocation) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.locationRepository.permission() == LocationPermission.GRANTED) useGpsFix()
        }
    }

    TodayScreen(
        state = state,
        onChooseCity = { navigator.push(Screen.CitySearch) },
        onOpenLocationSettings = ::openAppSettings,
        onOpenQibla = { navigator.push(Screen.Qibla) },
        onOpenAdhkar = { navigator.push(Screen.Adhkar()) },
        onOpenTasbeeh = { navigator.push(Screen.Tasbeeh) },
        // Only while there are notifications to be exact about; the flag
        // is re-read on every foreground, so the card leaves by itself.
        exactAlarmsOff = notificationSettings.enabled && !exactAlarmsAllowed,
        onAllowExactAlarms = ::requestExactAlarmAccess,
        onOpenAboutTimes = { navigator.push(Screen.AboutTimes) },
        onMatchMyMosque = { navigator.push(Screen.MatchMyMosque) },
        onAnswerCard = { card -> scope.launch { viewModel.answerOneTimeCard(card) } },
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
internal fun AdhkarRoute(container: AppContainer, navigator: Navigator, set: AdhkarSet?) {
    // A set read with the phone in the hand is minutes without a touch on some pages; the display
    // staying on is what Tasbeeh and the compass do. Here rather than in the screen, so the screen
    // composes under the iOS UI test, where this effect cannot run.
    KeepScreenOn()

    val format = LocalPlatformFormat.current
    val language = uiLanguage().code
    val scope = rememberCoroutineScope()
    // Rebuilt when the language or the clock format changes, as TodayRoute's view model is: the
    // phase's time is written by the format, and the du'a translations are the language's. Nothing
    // on screen is lost — every count is already on disk — and the set the reader had switched to
    // is the one the new view model opens. Keyed on [set]: a new request (a reminder naming the
    // other set) is opened as asked. A plain holder, not state: it is written after every frame and
    // read only when an effect below restarts.
    val shown = remember(set) { ShownAdhkarSet() }
    val viewModel = remember(format, language) {
        AdhkarViewModel(
            catalogue = { AdhkarCatalogue.load() },
            textFor = { AdhkarCatalogue.loadText(it) },
            quran = container.quranRepository,
            store = container.adhkarStore,
            settings = container.settingsRepository,
            engine = container.prayerTimesEngine,
            haptics = createHaptics(),
            now = { Clock.System.now() },
            format = format,
            language = language,
            scope = scope,
            // A set said in full (spec §5): marked, so no later reschedule arms its reminder again,
            // and the day's reminder withdrawn at once, pending or already on screen.
            onSetCompleted = { finished, date ->
                container.adhkarStore.markDone(finished, date)
                container.notificationScheduler.cancel(setOf(adhkarNotificationId(finished, date)))
            },
        )
    }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    // Read here, in the composition that restarts the effect, not inside it: the SideEffect below
    // runs before the effect does and would hand a new request the old set.
    val reopen = shown.set ?: set
    LaunchedEffect(viewModel, set) { viewModel.open(reopen) }
    // The phase caption moves with the clock, once a minute while the screen is in front, and
    // straight away on coming back to it (Today's lifecycle gate, for Today's reasons).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.tick()
                delay(MINUTE_MS - Clock.System.now().toEpochMilliseconds() % MINUTE_MS)
            }
        }
    }
    val state by viewModel.state.collectAsState()
    SideEffect { if (state.loaded) shown.set = state.set }
    // The "Aa" sheet (spec §3.6). It edits the reader's own settings and writes them back whole, so
    // it is given them whole, as stored — never a copy built from defaults, which would overwrite
    // the reader's translation and mode with its first change. Null until read; the sheet waits.
    var readingOpen by remember { mutableStateOf(false) }
    val reading by remember(format) {
        container.settingsRepository.readingSettings(format.languageTag()).distinctUntilChanged()
    }.collectAsState(initial = null)
    // The Quran pages' speaker (spec §3.5): the page's passage in the voice chosen in Settings ›
    // Recitation, through the one player, with the set and the passage on the lock screen. A
    // surah not on the phone is offered on the download sheet, which the app hosts over every
    // screen. The passage stops when the page turns — a swipe, a chevron, a counted page moving
    // on, the other set — and when the screen is left. Nothing it plays is counted.
    val recitation = container.recitationController
    val playing by recitation.adhkarPlaying.collectAsState()
    LaunchedEffect(recitation, state.set, state.currentPage) { recitation.stopAdhkar() }
    DisposableEffect(recitation) { onDispose { recitation.stopAdhkar() } }
    val passage = state.pages.getOrNull(state.currentPage)?.recited
    val surahName = rememberSurahName(passage?.surah) { container.quranRepository.surah(it) }
    val setTitle = stringResource(if (state.set == AdhkarSet.EVENING) Res.string.adhkar_evening else Res.string.adhkar_morning)
    AdhkarScreen(
        state = state,
        onBack = { navigator.pop() },
        onSelectSet = viewModel::selectSet,
        onGoTo = viewModel::goTo,
        onTap = viewModel::tap,
        onPlay = {
            // Pause is stop here: a passage is a few seconds to a minute, and is started again.
            if (recitation.adhkarPlaying.value) {
                recitation.stopAdhkar()
            } else if (passage != null) {
                recitation.requestPlayAyahs(passage.surah, passage.ayahs, setTitle, passage.subtitle(surahName, format))
            }
        },
        playing = playing,
        onOpenReading = { readingOpen = true },
    )
    val current = reading
    if (readingOpen && current != null) {
        AdhkarReadingSheet(
            settings = current,
            showTranslation = state.showTranslation,
            preview = state.readingPreview,
            onChange = { changed -> scope.launch { container.settingsRepository.setReadingSettings(changed) } },
            onTranslation = viewModel::setTranslation,
            onDismiss = { readingOpen = false },
        )
    }
}

private const val MINUTE_MS = 60_000L

/** The set the adhkar screen last showed, for a view model rebuilt under it (see [AdhkarRoute]). */
private class ShownAdhkarSet(var set: AdhkarSet? = null)

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
