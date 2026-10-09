package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.audio.SoundPreviewPlayer
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.feature.onboarding.OnboardingStep
import world.taqwa.app.feature.recitation.RecitationState
import world.taqwa.app.feature.recitation.reciterName
import world.taqwa.app.feature.settings.AboutTimesScreen
import world.taqwa.app.feature.settings.AppearanceSettingsScreen
import world.taqwa.app.feature.settings.AttributionScreen
import world.taqwa.app.feature.settings.CitySearchScreen
import world.taqwa.app.feature.settings.HighLatitudePickerScreen
import world.taqwa.app.feature.settings.LocationSettingsScreen
import world.taqwa.app.feature.settings.AdjustmentsState
import world.taqwa.app.feature.settings.ManualAdjustmentsScreen
import world.taqwa.app.feature.settings.NotificationSettingsScreen
import world.taqwa.app.feature.settings.PrayerTimesSettingsScreen
import world.taqwa.app.feature.settings.SettingsRootScreen
import world.taqwa.app.feature.settings.TimetableCheck
import world.taqwa.app.feature.settings.TimetableChooser
import world.taqwa.app.feature.settings.TimetableStatus
import world.taqwa.app.feature.settings.highLatitudeNeedsCheck
import world.taqwa.app.feature.settings.timetableName
import world.taqwa.app.feature.settings.aboutTimesUiState
import world.taqwa.app.feature.settings.themeDisplayName
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.timetableDisplayName
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.notifications.openAppNotificationSettings
import world.taqwa.app.notifications.requestExactAlarmAccess
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.today_current_location
import world.taqwa.app.settings.ResolvedCityName
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.WidgetMirrorRefresher
import world.taqwa.app.widget.refreshWidgets

@Composable
internal fun SettingsRootRoute(
    locationState: State<GeoLocation?>,
    cityDisplayNameState: State<String?>,
    prayerSettingsState: State<PrayerSettings>,
    themeModeState: State<ThemeMode>,
    notificationSettingsState: State<NotificationSettings>,
    navigator: Navigator,
    recitationStateState: State<RecitationState>,
) {
    val location by locationState
    val cityDisplayName by cityDisplayNameState
    val prayerSettings by prayerSettingsState
    val themeMode by themeModeState
    val notificationSettings by notificationSettingsState
    val recitationState by recitationStateState
    SettingsRootScreen(
        // Null only when there is no location at all — the row then reads
        // "Not set" rather than naming a city nobody chose.
        cityName = if (location == null) {
            null
        } else {
            cityDisplayName ?: stringResource(Res.string.today_current_location)
        },
        methodName = timetableDisplayName(prayerSettings.timetable),
        themeName = themeDisplayName(themeMode),
        notificationSettings = notificationSettings,
        onOpenLocation = { navigator.push(Screen.LocationSettings) },
        onOpenPrayerTimes = { navigator.push(Screen.PrayerTimesSettings) },
        onOpenNotifications = { navigator.push(Screen.NotificationSettings) },
        onOpenAppearance = { navigator.push(Screen.Appearance) },
        onOpenAbout = { navigator.push(Screen.About) },
        onOpenAttribution = { navigator.push(Screen.Attribution) },
        reciterName = recitationState.reciter?.let { reciterName(it) }.orEmpty(),
        onOpenRecitation = { navigator.push(Screen.RecitationSettings) },
    )
}

@Composable
internal fun NotificationSettingsRoute(
    notificationSettingsState: State<NotificationSettings>,
    exactAlarmsAllowedState: State<Boolean>,
    notificationsGrantedState: State<Boolean>,
    scope: CoroutineScope,
    refreshPermissions: suspend () -> Unit,
    navigator: Navigator,
    settings: SettingsRepository,
    container: AppContainer,
    soundPreviewPlayer: SoundPreviewPlayer,
) {
    val notificationSettings by notificationSettingsState
    val exactAlarmsAllowed by exactAlarmsAllowedState
    val notificationsGranted by notificationsGrantedState
    NotificationSettingsScreen(
        settings = notificationSettings,
        exactAlarmsUnavailable = !exactAlarmsAllowed,
        notificationsGranted = notificationsGranted,
        onOpenNotificationSettings = ::openAppNotificationSettings,
        onRequestExactAlarms = ::requestExactAlarmAccess,
        onPermissionChecked = { scope.launch { refreshPermissions() } },
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
        onToggleTahajjud = { on ->
            scope.launch {
                settings.setNotificationSettings(notificationSettings.copy(tahajjud = on))
                container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
            }
        },
        onPickTahajjudSound = { sound ->
            scope.launch {
                settings.setNotificationSettings(notificationSettings.copy(tahajjudSound = sound))
                container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
            }
        },
        onPickVoice = { voice ->
            scope.launch {
                settings.setNotificationSettings(notificationSettings.copy(voice = voice))
                // The same reschedule a sound change needs, and for the same
                // reason: a channel's sound is immutable, so the new voice
                // only reaches the user through channels built from the new
                // plan.
                container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
            }
        },
        // The sheet's Takbir and Adhan buttons audition the voice that is
        // actually chosen, never the original by default.
        onPreviewSound = { soundPreviewPlayer.play(it, notificationSettings.voice) },
        onPreviewVoice = { voice -> soundPreviewPlayer.play(PrayerSound.ADHAN, voice) },
        onStopPreview = { soundPreviewPlayer.stop() },
    )
}

/**
 * After anything that changes the prayer times outside the Prayer screen (ruling R53): a
 * prayer-settings write, a new location (a city picked, a GPS fix), or the one-time backfill of a
 * location's country or region. The armed
 * notifications and the widgets' mirror move to the new times at once, as a language change moves
 * them to the new words (`LanguageChangeReschedule`), instead of at the next foreground, alarm or
 * top-up.
 *
 * One at a time, like the reschedule inside it: two quick taps refresh in the order they were
 * made, so the mirror the widgets end on is the latest settings'. The engine's days, cold after a
 * change, are computed off the main thread (the coordinator plans on the default dispatcher, and
 * so does the mirror here).
 */
internal suspend fun refreshForNewTimes(container: AppContainer, settings: SettingsRepository, format: PlatformFormat) {
    newTimesRefresh.withLock {
        container.notificationCoordinator.reschedule(RescheduleTrigger.SETTINGS_CHANGED)
        withContext(Dispatchers.Default) {
            WidgetMirrorRefresher.refresh(settings, container.prayerTimesEngine, format = format)
        }
        refreshWidgets()
    }
}

private val newTimesRefresh = Mutex()

/*
 * Every prayer-time control writes its own field through the repository on touch (spec §2.2's
 * per-field writes), then refreshes what was armed with the old times ([refreshForNewTimes]).
 */

@Composable
internal fun PrayerTimesSettingsRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    cityDisplayNameState: State<String?>,
    today: LocalDate,
    scope: CoroutineScope,
    settings: SettingsRepository,
    container: AppContainer,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location by locationState
    val cityDisplayName by cityDisplayNameState
    val format = LocalPlatformFormat.current
    fun write(change: suspend () -> Unit) {
        scope.launch {
            change()
            refreshForNewTimes(container, settings, format)
        }
    }
    // Today's day as the Prayer screen has it (the engine's cache already holds it): what applies
    // here, whether the stored timetable does, and what is paused.
    val day = location?.let { container.prayerTimesEngine.dayFor(it, today, prayerSettings) }
    PrayerTimesSettingsScreen(
        settings = prayerSettings,
        status = day?.let { TimetableStatus.of(prayerSettings, it) },
        adjustmentsPaused = day?.pausedAdjustments?.isNotEmpty() == true,
        placeName = cityDisplayName.takeIf { location != null },
        today = today,
        onSchool = { write { settings.setSchool(it) } },
        onHijriOffset = { write { settings.setHijriOffsetDays(it) } },
        onShowSunrise = { write { settings.setShowSunrise(it) } },
        onShowBothAsr = { write { settings.setShowBothAsr(it) } },
        onShowWhereDiffer = { write { settings.setShowWhereDiffer(it) } },
        onSaudiFajrLater = { write { settings.setSaudiFajrLater(it) } },
        onBack = { navigator.pop() },
        // A timetable belongs to a place: without one, the way forward is to set it.
        onOpenTimetable = { navigator.push(if (location != null) Screen.Timetable else Screen.LocationSettings) },
        onOpenHighLatitudePicker = { navigator.push(Screen.HighLatitudePicker) },
        onOpenManualAdjustments = { navigator.push(Screen.ManualAdjustments) },
        onOpenAboutTimes = { navigator.push(Screen.AboutTimes) },
    )
}

/**
 * "About these times" (spec §2.3, task 11): filled from the never-early engine's own resolution
 * for the stored location, exactly as the Prayer screen and Manual adjustments read it
 * ([world.taqwa.app.prayer.PrayerTimesEngine.dayFor]). Pops back if the location disappears from
 * under it (the location screen's own "clear" — unreachable today, but [ManualAdjustmentsScreen]
 * guards the same way for when it becomes one).
 */
@Composable
internal fun AboutTimesRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    cityDisplayNameState: State<String?>,
    today: LocalDate,
    container: AppContainer,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location by locationState
    val cityDisplayName by cityDisplayNameState
    val loc = location
    if (loc == null) {
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }
    val zone = remember(loc.timeZoneId) { TimeZone.of(loc.timeZoneId) }
    val place = remember(loc) { PrayerTimesEngine.placeOf(loc) }
    val engineDay = remember(loc, prayerSettings, today) {
        container.prayerTimesEngine.dayFor(loc, today, prayerSettings)
    }
    val state = remember(engineDay, place, today) {
        aboutTimesUiState(engineDay, place, today, prayerSettings.hijriOffsetDays)
    }
    // A city's own table (QMDB's cities, the owner's decision of 9 Oct 2026): About names the city whose
    // table the times follow, in the reader's language, also where the location's own nearest city is
    // another (a suburb's fix inside the city's reach). The location's own city needs no lookup.
    val unitCityId = state.resolution.unitCityId?.takeIf { it != loc.cityId }
    val unitCityName by produceState<String?>(null, unitCityId, cityDisplayName) {
        value = unitCityId?.let { container.cityRepository.displayName(it) }
    }
    AboutTimesScreen(
        state = state,
        engineDay = engineDay,
        place = place,
        cityLabel = unitCityName ?: cityDisplayName ?: stringResource(Res.string.today_current_location),
        zone = zone,
        onBack = { navigator.pop() },
        onMatchMyMosque = { navigator.push(Screen.MatchMyMosque) },
    )
}

/**
 * The high-latitude rule of an Other method (spec §2.2). A confirmed one is checked again with the
 * new rule before it is written (review I1): never earlier than Automatic, the rule is written and
 * the confirmation kept; earlier, the warning names the new figures, "Follow it" writes the rule
 * and confirms, and the other button changes nothing.
 */
@Composable
internal fun HighLatitudePickerRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    today: LocalDate,
    scope: CoroutineScope,
    settings: SettingsRepository,
    container: AppContainer,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location by locationState
    val format = LocalPlatformFormat.current
    val screenScope = rememberCoroutineScope()
    val current = prayerSettings.legacyHighLatitude ?: HighLatitudePreference.AUTOMATIC
    val timetable = prayerSettings.timetable
    val chooser = remember(location, prayerSettings, today) {
        TimetableChooser(
            checkScope = screenScope,
            check = { rule, onProgress ->
                val here = location ?: return@TimetableChooser TimetableCheck.NotHere
                val withRule = prayerSettings.copy(legacyHighLatitude = HighLatitudePreference.valueOf(rule))
                checkTimetable(container.prayerTimesEngine, here, withRule, today, timetable, onProgress)
            },
            nameKeyOf = { Registry.byId(timetable)?.shortNameKey ?: timetable },
            apply = { rule, confirmed ->
                scope.launch {
                    settings.setLegacyHighLatitude(HighLatitudePreference.valueOf(rule))
                    if (confirmed) {
                        settings.setTimetableConfirmed(timetable, location?.let { container.prayerTimesEngine.automaticEntryId(it) })
                    }
                    refreshForNewTimes(container, settings, format)
                }
                navigator.pop()
            },
            needsCheck = { location != null && highLatitudeNeedsCheck(prayerSettings) },
        )
    }
    HighLatitudePickerScreen(
        current = current,
        onPick = { picked -> if (picked == current) navigator.pop() else chooser.choose(picked.name) },
        onBack = { navigator.pop() },
        checking = chooser.checking?.progress,
        warning = chooser.warning,
        automatic = location?.let { container.prayerTimesEngine.dayFor(it, today, prayerSettings).resolution.timetableName() },
        onKeep = chooser::keep,
        onFollow = chooser::follow,
    )
}

@Composable
internal fun ManualAdjustmentsRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    container: AppContainer,
    today: LocalDate,
    scope: CoroutineScope,
    settings: SettingsRepository,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location by locationState
    val format = LocalPlatformFormat.current
    val state = location?.let { AdjustmentsState.of(container.prayerTimesEngine.dayFor(it, today, prayerSettings), prayerSettings, TimeZone.of(it.timeZoneId)) }
        ?: AdjustmentsState()
    ManualAdjustmentsScreen(
        adjustments = prayerSettings.minuteAdjustments,
        confirmed = prayerSettings.confirmedAdjustments,
        state = state,
        onSet = { prayer, minutes, confirmUnder ->
            scope.launch {
                settings.setMinuteAdjustment(prayer, minutes, confirmUnder)
                refreshForNewTimes(container, settings, format)
            }
        },
        onOpenTimetable = { navigator.push(Screen.Timetable) },
        onBack = { navigator.pop() },
    )
}

@Composable
internal fun LocationSettingsRoute(
    locationState: State<GeoLocation?>,
    cityDisplayNameState: State<String?>,
    locationSourceState: State<LocationSource>,
    container: AppContainer,
    useGpsFix: () -> Unit,
    navigator: Navigator,
) {
    val location by locationState
    val cityDisplayName by cityDisplayNameState
    val locationSource by locationSourceState
    LocationSettingsScreen(
        location = location,
        cityName = cityDisplayName,
        locationSource = locationSource,
        locationRepository = container.locationRepository,
        onLocationPermission = { permission ->
            if (permission == LocationPermission.GRANTED) useGpsFix()
        },
        onChooseCity = { navigator.push(Screen.CitySearch) },
        onBack = { navigator.pop() },
    )
}

@Composable
internal fun CitySearchRoute(
    container: AppContainer,
    scope: CoroutineScope,
    settings: SettingsRepository,
    languageTag: String,
    backStackState: State<List<Screen>>,
    onboardingStepState: MutableState<OnboardingStep>,
    navigator: Navigator,
) {
    val backStack by backStackState
    var onboardingStep by onboardingStepState
    val format = LocalPlatformFormat.current
    CitySearchScreen(
        cityRepository = container.cityRepository,
        onPick = { city ->
            scope.launch {
                val picked = city.toGeoLocation()
                // The row the user tapped was already rendered in their
                // language, so the name is known here — stored with the
                // location so the header needs no lookup on any later
                // launch, the first one after picking included.
                settings.setLocation(
                    picked,
                    ResolvedCityName(city.displayName, languageTag),
                )
                // The only writer of MANUAL, which is what makes turning the
                // toggle off reversible: back out of the search and the
                // stored source — and so the toggle — is untouched.
                settings.setLocationSource(LocationSource.MANUAL)
                // A location save no longer writes a calculation method (spec §8): the
                // never-early engine's Automatic timetable already follows the picked city.
                // The alarms and widgets armed for the old city follow it now (ruling R53).
                refreshForNewTimes(container, settings, format)
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
}

@Composable
internal fun AppearanceRoute(
    themeModeState: State<ThemeMode>,
    container: AppContainer,
    scope: CoroutineScope,
    settings: SettingsRepository,
    widgetBackgroundState: State<WidgetBackground>,
    navigator: Navigator,
) {
    val themeMode by themeModeState
    val widgetBackground by widgetBackgroundState
    AppearanceSettingsScreen(
        current = themeMode,
        widgetPinRequester = container.widgetPinRequester,
        widgetPlacementSource = container.widgetPlacementSource,
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
}

@Composable
internal fun AttributionRoute(
    container: AppContainer,
    recitationStateState: State<RecitationState>,
    navigator: Navigator,
) {
    val recitationState by recitationStateState
    // The translation credits must never drift from what is actually
    // bundled (spec §6), so they are read from the database rather than
    // hardcoded; an empty list while loading is fine, it fills in a frame
    // later.
    val translations by produceState(initialValue = emptyList<world.taqwa.app.quran.TranslationInfo>()) {
        value = container.quranRepository.translations()
    }
    AttributionScreen(
        translations = translations,
        // The catalogue in force, so a reciter withdrawn by a manifest
        // refresh leaves the credits with them (spec §2).
        reciters = recitationState.reciters,
        onBack = { navigator.pop() },
    )
}
