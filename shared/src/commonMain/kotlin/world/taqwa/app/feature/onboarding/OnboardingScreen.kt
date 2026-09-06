package world.taqwa.app.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.rememberLocationPermissionRequester

/**
 * Hoisted out of this composable because "choose a city instead" navigates away to the city
 * search: the step must survive the round trip, or the user comes back to the welcome screen
 * having already answered its question.
 */
enum class OnboardingStep { WELCOME, LOCATION, NOTIFICATIONS }

/**
 * Three screens, each asking for exactly one thing and saying why *before* the system dialog
 * appears. Unexplained prompts get denied, and a denied location permission is the difference
 * between a working app and a dead one. Declining is a first-class path, never a dead end.
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    onStep: (OnboardingStep) -> Unit,
    locationRepository: LocationRepository,
    onLocationPermission: (LocationPermission) -> Unit,
    onChooseCity: () -> Unit,
    onComplete: () -> Unit,
) {
    val colors = LocalTaqwaColors.current

    val requestLocation = rememberLocationPermissionRequester(locationRepository) { permission ->
        onLocationPermission(permission)
        // Granted or denied, the flow moves on: the city picker remains available from Today
        // and from Settings, so a refusal is never a dead end.
        onStep(OnboardingStep.NOTIFICATIONS)
    }

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
                RingMark()
                Spacer(Modifier.height(28.dp))
                Headline("Taqwa")
                Spacer(Modifier.height(12.dp))
                Body(
                    "Prayer times, qibla and the Quran. Free forever. No ads, no account, " +
                        "works offline.",
                )
            }
            OnboardingStep.LOCATION -> {
                Headline("Where are you?")
                Spacer(Modifier.height(12.dp))
                Body(
                    "Prayer times depend on your exact position. Everything is calculated on " +
                        "your device — your location never leaves your phone.",
                )
            }
            OnboardingStep.NOTIFICATIONS -> {
                Headline("Never miss a prayer")
                Spacer(Modifier.height(12.dp))
                Body(
                    "A notification at each prayer time. You pick the sound for every prayer " +
                        "separately — and can change it whenever you like.",
                )
            }
        }

        Spacer(Modifier.weight(1f))
        StepDots(step)
        Spacer(Modifier.height(20.dp))

        when (step) {
            OnboardingStep.WELCOME -> {
                TaqwaPrimaryButton("Get started", onClick = { onStep(OnboardingStep.LOCATION) })
                Spacer(Modifier.height(44.dp))
            }
            OnboardingStep.LOCATION -> {
                TaqwaPrimaryButton("Use my location", requestLocation)
                // The city search is a pushed screen; it advances the step itself once a city
                // has actually been chosen, so backing out of it lands here again.
                TaqwaTextLink("Choose a city instead", onClick = onChooseCity)
            }
            OnboardingStep.NOTIFICATIONS -> {
                // Deliberately a no-op beyond advancing: plan 2 wires the real request once a
                // scheduler exists. A granted permission that produces no notifications is worse
                // than not asking.
                TaqwaPrimaryButton("Enable notifications", onComplete)
                TaqwaTextLink("Not now", onComplete)
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

/** The app mark: the same countdown ring, drawn empty. */
@Composable
private fun RingMark() {
    val colors = LocalTaqwaColors.current
    Canvas(Modifier.size(88.dp)) {
        val stroke = 7.dp.toPx()
        drawCircle(color = colors.hairline, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke))
        drawArc(
            color = colors.ring,
            startAngle = -90f,
            sweepAngle = 108f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
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
