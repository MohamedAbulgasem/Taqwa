package world.taqwa.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.feature.settings.WidgetPreview
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.rememberLocationPermissionRequester
import world.taqwa.app.notifications.rememberNotificationPermissionRequester
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.onboarding_location_body
import world.taqwa.app.resources.onboarding_location_cta
import world.taqwa.app.resources.onboarding_location_secondary
import world.taqwa.app.resources.onboarding_location_title
import world.taqwa.app.resources.onboarding_notifications_body
import world.taqwa.app.resources.onboarding_notifications_cta
import world.taqwa.app.resources.onboarding_notifications_secondary
import world.taqwa.app.resources.onboarding_notifications_title
import world.taqwa.app.resources.onboarding_welcome_body
import world.taqwa.app.resources.onboarding_welcome_cta
import world.taqwa.app.resources.onboarding_welcome_title
import world.taqwa.app.resources.onboarding_widget_body
import world.taqwa.app.resources.onboarding_widget_body_ios
import world.taqwa.app.resources.onboarding_widget_body_ios_legacy
import world.taqwa.app.resources.onboarding_widget_cta_add
import world.taqwa.app.resources.onboarding_widget_cta_done
import world.taqwa.app.resources.onboarding_widget_secondary
import world.taqwa.app.resources.onboarding_widget_title
import world.taqwa.app.widget.WidgetAddPath
import world.taqwa.app.widget.WidgetPinRequester
import world.taqwa.app.widget.widgetAddPath

/**
 * Hoisted out of this composable because "choose a city instead" navigates away to the city
 * search: the step must survive the round trip, or the user comes back to the welcome screen
 * having already answered its question.
 */
enum class OnboardingStep { WELCOME, LOCATION, NOTIFICATIONS, WIDGET }

/**
 * Four screens, each asking for exactly one thing and saying why *before* the system dialog
 * appears. Unexplained prompts get denied, and a denied location permission is the difference
 * between a working app and a dead one. Declining is a first-class path, never a dead end.
 *
 * The last screen asks for nothing from the system. It exists because the widget is the surface
 * most people will read most often and the one nobody finds on their own; on Android it can place
 * the widget from here, on iOS it can only say how.
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    onStep: (OnboardingStep) -> Unit,
    locationRepository: LocationRepository,
    widgetPinRequester: WidgetPinRequester,
    onLocationPermission: (LocationPermission) -> Unit,
    onChooseCity: () -> Unit,
    onNotificationPermission: (Boolean) -> Unit,
    onDeclineNotifications: () -> Unit,
    onComplete: () -> Unit,
) {
    val colors = LocalTaqwaColors.current

    val requestLocation = rememberLocationPermissionRequester(locationRepository) { permission ->
        onLocationPermission(permission)
        // Granted or denied, the flow moves on: the city picker remains available from Today
        // and from Settings, so a refusal is never a dead end.
        onStep(OnboardingStep.NOTIFICATIONS)
    }

    // Granted or denied, the flow moves on here too: the sound sheet and the master toggle in
    // Settings remain available afterwards.
    val requestNotifications = rememberNotificationPermissionRequester { granted ->
        onNotificationPermission(granted)
        onStep(OnboardingStep.WIDGET)
    }

    val canPinWidget = widgetPinRequester.isSupported

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        when (step) {
            OnboardingStep.WELCOME -> {
                MihrabMark()
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_welcome_title))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(Res.string.onboarding_welcome_body))
            }
            OnboardingStep.LOCATION -> {
                PinMark()
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_location_title))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(Res.string.onboarding_location_body))
            }
            OnboardingStep.NOTIFICATIONS -> {
                BellMark()
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_notifications_title))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(Res.string.onboarding_notifications_body))
            }
            OnboardingStep.WIDGET -> {
                // The widgets themselves stand in for a mark: the point of the screen is what
                // they look like. Drawn for the system appearance, since the widget will be.
                WidgetPreview(
                    background = WidgetBackground.FOLLOW_THEME,
                    systemIsDark = isSystemInDarkTheme(),
                    content = null,
                )
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_widget_title))
                Spacer(Modifier.height(12.dp))
                Body(
                    stringResource(
                        when {
                            canPinWidget -> Res.string.onboarding_widget_body
                            widgetAddPath == WidgetAddPath.IOS_HOLD_ICON -> Res.string.onboarding_widget_body_ios
                            else -> Res.string.onboarding_widget_body_ios_legacy
                        },
                    ),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
        StepDots(step)
        Spacer(Modifier.height(20.dp))

        when (step) {
            OnboardingStep.WELCOME -> {
                TaqwaPrimaryButton(
                    stringResource(Res.string.onboarding_welcome_cta),
                    onClick = { onStep(OnboardingStep.LOCATION) },
                )
                Spacer(Modifier.height(44.dp))
            }
            OnboardingStep.LOCATION -> {
                TaqwaPrimaryButton(stringResource(Res.string.onboarding_location_cta), requestLocation)
                // The city search is a pushed screen; it advances the step itself once a city
                // has actually been chosen, so backing out of it lands here again.
                TaqwaTextLink(
                    stringResource(Res.string.onboarding_location_secondary),
                    onClick = onChooseCity,
                )
            }
            OnboardingStep.NOTIFICATIONS -> {
                TaqwaPrimaryButton(
                    stringResource(Res.string.onboarding_notifications_cta),
                    requestNotifications,
                )
                TaqwaTextLink(
                    stringResource(Res.string.onboarding_notifications_secondary),
                    onClick = { onDeclineNotifications(); onStep(OnboardingStep.WIDGET) },
                )
            }
            OnboardingStep.WIDGET -> {
                if (canPinWidget) {
                    // The launcher's own sheet appears over the app; onboarding finishes
                    // underneath it either way, because the answer never comes back reliably.
                    TaqwaPrimaryButton(
                        stringResource(Res.string.onboarding_widget_cta_add),
                        onClick = { widgetPinRequester.requestPin(); onComplete() },
                    )
                    TaqwaTextLink(stringResource(Res.string.onboarding_widget_secondary), onClick = onComplete)
                } else {
                    TaqwaPrimaryButton(stringResource(Res.string.onboarding_widget_cta_done), onClick = onComplete)
                    Spacer(Modifier.height(44.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text,
        style = TaqwaText.screenTitle.copy(fontSize = 30.sp),
        color = LocalTaqwaColors.current.textPrimary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = TaqwaText.caption.copy(fontSize = 16.sp),
        color = LocalTaqwaColors.current.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepDots(step: OnboardingStep) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingStep.entries.forEach { entry ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(if (entry == step) 20.dp else 6.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (entry == step) colors.accent else colors.hairline),
            )
        }
    }
}
