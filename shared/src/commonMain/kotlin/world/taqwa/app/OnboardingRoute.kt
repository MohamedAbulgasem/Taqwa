package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import world.taqwa.app.di.AppContainer
import world.taqwa.app.feature.onboarding.OnboardingScreen
import world.taqwa.app.feature.onboarding.OnboardingStep
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.notifications.NotificationOnboarding
import world.taqwa.app.notifications.requestExactAlarmAccess
import world.taqwa.app.settings.SettingsRepository

@Composable
internal fun OnboardingRoute(
    onboardingStepState: MutableState<OnboardingStep>,
    container: AppContainer,
    useGpsFix: () -> Unit,
    navigator: Navigator,
    scope: CoroutineScope,
    notificationOnboarding: (Boolean) -> NotificationOnboarding,
    settings: SettingsRepository,
    exactAlarmsAllowedState: State<Boolean>,
) {
    var onboardingStep by onboardingStepState
    val exactAlarmsAllowed by exactAlarmsAllowedState
    OnboardingScreen(
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
        exactAlarmsNeeded = !exactAlarmsAllowed,
        onRequestExactAlarms = ::requestExactAlarmAccess,
    )
}
