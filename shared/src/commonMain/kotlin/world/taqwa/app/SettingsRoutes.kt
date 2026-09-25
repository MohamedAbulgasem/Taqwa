package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.audio.SoundPreviewPlayer
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.di.AppContainer
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.feature.onboarding.OnboardingStep
import world.taqwa.app.feature.recitation.RecitationState
import world.taqwa.app.feature.recitation.reciterName
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
import world.taqwa.app.i18n.methodDisplayName
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.notifications.openAppNotificationSettings
import world.taqwa.app.notifications.requestExactAlarmAccess
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.today_current_location
import world.taqwa.app.settings.ResolvedCityName
import world.taqwa.app.settings.SettingsRepository

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
        methodName = methodDisplayName(prayerSettings.method),
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

@Composable
internal fun PrayerTimesSettingsRoute(
    prayerSettingsState: State<PrayerSettings>,
    today: LocalDate,
    write: (PrayerSettings) -> Unit,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    PrayerTimesSettingsScreen(
        settings = prayerSettings,
        today = today,
        onChange = write,
        onBack = { navigator.pop() },
        onOpenMethodPicker = { navigator.push(Screen.MethodPicker) },
        onOpenHighLatitudePicker = { navigator.push(Screen.HighLatitudePicker) },
        onOpenManualAdjustments = { navigator.push(Screen.ManualAdjustments) },
    )
}

@Composable
internal fun MethodPickerRoute(
    prayerSettingsState: State<PrayerSettings>,
    scope: CoroutineScope,
    settings: SettingsRepository,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    MethodPickerScreen(
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
}

@Composable
internal fun HighLatitudePickerRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    write: (PrayerSettings) -> Unit,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location by locationState
    HighLatitudePickerScreen(
        current = prayerSettings.highLatitude,
        latitude = location?.latitude ?: 0.0,
        onPick = {
            write(prayerSettings.copy(highLatitude = it))
            navigator.pop()
        },
        onBack = { navigator.pop() },
    )
}

@Composable
internal fun ManualAdjustmentsRoute(
    prayerSettingsState: State<PrayerSettings>,
    locationState: State<GeoLocation?>,
    container: AppContainer,
    today: LocalDate,
    write: (PrayerSettings) -> Unit,
    navigator: Navigator,
) {
    val prayerSettings by prayerSettingsState
    val location by locationState
    ManualAdjustmentsScreen(
        settings = prayerSettings,
        location = location,
        engine = container.prayerTimesEngine,
        today = today,
        onChange = write,
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
