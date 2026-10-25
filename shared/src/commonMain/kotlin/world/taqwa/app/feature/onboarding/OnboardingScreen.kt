package world.taqwa.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.contentWidth
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.feature.settings.WidgetPreview
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.rememberLocationPermissionRequester
import world.taqwa.app.notifications.rememberNotificationPermissionRequester
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.notifications_allow_exact_alarms
import world.taqwa.app.resources.onboarding_exact_body
import world.taqwa.app.resources.onboarding_exact_title
import world.taqwa.app.resources.onboarding_location_body
import world.taqwa.app.resources.onboarding_continue
import world.taqwa.app.resources.onboarding_location_title
import world.taqwa.app.resources.onboarding_notifications_body
import world.taqwa.app.resources.onboarding_notifications_secondary
import world.taqwa.app.resources.onboarding_notifications_title
import world.taqwa.app.resources.onboarding_welcome_body
import world.taqwa.app.resources.onboarding_welcome_cta
import world.taqwa.app.resources.onboarding_welcome_title
import world.taqwa.app.resources.onboarding_widget_cta_add
import world.taqwa.app.resources.onboarding_widget_cta_done
import world.taqwa.app.resources.onboarding_widget_secondary
import world.taqwa.app.resources.onboarding_widget_steps_android
import world.taqwa.app.resources.onboarding_widget_title
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetAddPath
import world.taqwa.app.widget.WidgetPinRequester
import world.taqwa.app.widget.askToPin
import world.taqwa.app.widget.widgetAddPath

/**
 * Hoisted out of this composable because a declined location navigates away to the city search:
 * the step must survive the round trip, or the user comes back to the welcome screen having
 * already answered its question.
 */
enum class OnboardingStep { WELCOME, LOCATION, NOTIFICATIONS, EXACT_ALARMS, WIDGET }

/**
 * Four screens, each asking for exactly one thing and saying why *before* the system dialog
 * appears. Unexplained prompts get denied, and a denied location permission is the difference
 * between a working app and a dead one. Declining is a first-class path, never a dead end.
 *
 * A screen that comes before a system prompt has one button, Continue, and it always opens the
 * prompt. That is App Review's rule (guideline 5.1.1(iv), and the pre-alert screens of the Human
 * Interface Guidelines: one button, titled like Continue or Next, and no way to leave without
 * seeing the prompt). 1.0.0 (34) was rejected for "Use my location" beside "Choose a city
 * instead". The alternative comes after the prompt instead: a refused location opens the city
 * search ([locationAnswer]), and a refused notification prompt moves on.
 *
 * The last screen asks for nothing from the system. It exists because the widget is the surface
 * most people will read most often and the one nobody finds on their own; on Android it can place
 * the widget from here, on iOS it can only say how.
 *
 * Between notifications and the widget, Android 14+ gets one more: "Alarms & reminders". Play does
 * not let a prayer app hold the auto-granted exact-alarm permission, and the user-granted one is
 * off by default, so without this step most people would get an adhan up to an hour late and
 * never be told why. iOS, and an Android that already allows it, skip the step entirely.
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
    onComplete: () -> Unit,
    /** True on an Android that has not granted "Alarms & reminders"; always false on iOS. Re-read
     * by the caller each time the app comes to the front, which is how a grant made in system
     * settings ends the step. */
    exactAlarmsNeeded: Boolean = false,
    /** Opens the system's "Alarms & reminders" page for this app. */
    onRequestExactAlarms: () -> Unit = {},
) {
    val requestLocation = rememberLocationPermissionRequester(locationRepository) { permission ->
        onLocationPermission(permission)
        when (locationAnswer(permission)) {
            LocationAnswer.CONTINUE -> onStep(OnboardingStep.NOTIFICATIONS)
            // The city search moves the step on itself once a city is picked, and backing out
            // of it lands here again, where Continue leads straight back to it: the system asks
            // only once, so a second tap answers DENIED at once.
            LocationAnswer.CHOOSE_CITY -> onChooseCity()
            LocationAnswer.STAY -> Unit
        }
    }

    // Granted or denied, the flow moves on here too: the sound sheet and the master toggle in
    // Settings remain available afterwards.
    val requestNotifications = rememberNotificationPermissionRequester { granted ->
        onNotificationPermission(granted)
        // Exact alarms are only worth asking for once there are notifications to be exact about.
        onStep(if (granted && exactAlarmsNeeded) OnboardingStep.EXACT_ALARMS else OnboardingStep.WIDGET)
    }

    // The grant happens in system settings, out of the app's sight. The moment the app is back in
    // front holding the permission, this step has nothing left to ask and moves on by itself.
    LaunchedEffect(step, exactAlarmsNeeded) {
        if (step == OnboardingStep.EXACT_ALARMS && !exactAlarmsNeeded) onStep(OnboardingStep.WIDGET)
    }
    val steps = OnboardingStep.entries.filter { it != OnboardingStep.EXACT_ALARMS || exactAlarmsNeeded }

    val dots: @Composable () -> Unit = { StepDots(step, steps) }
    when (step) {
        OnboardingStep.WELCOME -> OnboardingPage(
            dots,
            copy = {
                MihrabMark()
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_welcome_title))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(Res.string.onboarding_welcome_body))
            },
        ) {
            TaqwaPrimaryButton(
                stringResource(Res.string.onboarding_welcome_cta),
                onClick = { onStep(OnboardingStep.LOCATION) },
            )
            Spacer(Modifier.height(44.dp))
        }
        // The two screens before a system prompt: Continue, and nothing beside it (see the class
        // comment). The spacer stands where a second link would be, so the button sits at the
        // same height on every screen.
        OnboardingStep.LOCATION -> OnboardingPage(
            dots,
            copy = {
                PinMark()
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_location_title))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(Res.string.onboarding_location_body))
            },
        ) {
            TaqwaPrimaryButton(stringResource(Res.string.onboarding_continue), requestLocation)
            Spacer(Modifier.height(44.dp))
        }
        OnboardingStep.NOTIFICATIONS -> OnboardingPage(
            dots,
            copy = {
                BellMark()
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_notifications_title))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(Res.string.onboarding_notifications_body))
            },
        ) {
            TaqwaPrimaryButton(stringResource(Res.string.onboarding_continue), requestNotifications)
            Spacer(Modifier.height(44.dp))
        }
        OnboardingStep.EXACT_ALARMS -> OnboardingPage(
            dots,
            copy = {
                ClockMark()
                Spacer(Modifier.height(28.dp))
                Headline(stringResource(Res.string.onboarding_exact_title))
                Spacer(Modifier.height(12.dp))
                Body(stringResource(Res.string.onboarding_exact_body))
            },
        ) {
            // The system page opens over the app; coming back with the grant advances the step
            // (the LaunchedEffect above), coming back without it leaves the choice here.
            TaqwaPrimaryButton(stringResource(Res.string.notifications_allow_exact_alarms), onRequestExactAlarms)
            TaqwaTextLink(
                stringResource(Res.string.onboarding_notifications_secondary),
                onClick = { onStep(OnboardingStep.WIDGET) },
            )
        }
        OnboardingStep.WIDGET -> OnboardingWidgetPage(widgetPinRequester, widgetAddPath, onComplete, dots)
    }
}

/**
 * One onboarding screen: the copy and the step dots in the middle, the buttons at the foot.
 *
 * The background is full-bleed; the copy and the buttons sit in a capped, centred column inside
 * it, so a sideways screen does not stretch a single sentence across 900 dp. safeDrawing, not
 * systemBars: sideways the navigation bar and the cutout are on the sides.
 *
 * The copy scrolls and the buttons do not, so the way on is always on screen. With the largest
 * text on a short phone the words can be taller than the screen: the widget steps by hand at
 * 200 % on a 408 x 760 dp phone pushed Done off the bottom, which left no way out. When the copy
 * fits, as it does at every default setting, the two weighted spacers centre it exactly as they
 * always did, and there is nothing to scroll.
 */
@Composable
private fun OnboardingPage(
    dots: @Composable () -> Unit,
    copy: @Composable ColumnScope.() -> Unit,
    /** Under the dots, where the button sits on the other screens; it scrolls with the copy. */
    belowDots: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .contentWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        // Draggable only when there is something to scroll, so a page that fits
                        // does not stretch under a finger.
                        .verticalScroll(scroll, enabled = scroll.canScrollForward || scroll.canScrollBackward)
                        // At least the height it is given, so the spacers have room to centre.
                        .heightIn(min = maxHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    copy()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(24.dp))
                    dots()
                    Spacer(Modifier.height(20.dp))
                    belowDots()
                }
            }
            actions()
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * The widget screen, and the launcher's answer to "Add widget". A launcher that takes the request
 * shows its own sheet over the app, and onboarding finishes underneath it, because the user's
 * answer never comes back reliably. One that refuses (no pin support, a work profile that forbids
 * it, or one that simply declines, as some vivo, OPPO and Xiaomi home screens do) would leave the
 * tap doing nothing, so the steps by hand replace the button. [addPath] is a parameter so a test
 * on either platform can drive the Android screen.
 */
@Composable
internal fun OnboardingWidgetPage(
    widgetPinRequester: WidgetPinRequester,
    addPath: WidgetAddPath,
    onComplete: () -> Unit,
    dots: @Composable () -> Unit = {},
) {
    val canPin = widgetPinRequester.isSupported(PinnableWidget.PRAYER)
    // Set only when the launcher turns "Add widget" down; a launcher that takes the request never
    // sets it, so for that phone the screen is what it always was.
    var pinRefused by rememberSaveable { mutableStateOf(false) }
    val offer = widgetStepOffer(canPin, pinRefused, addPath)
    // The steps sit under the dots, in the part that scrolls. When the copy above is too tall to
    // leave them room (the largest text on a short phone), they are scrolled up into view the
    // moment they replace the button, rather than appearing out of sight below it.
    val stepsInView = remember { BringIntoViewRequester() }
    LaunchedEffect(pinRefused) {
        if (pinRefused) {
            withFrameNanos {}
            stepsInView.bringIntoView()
        }
    }
    OnboardingPage(
        dots,
        copy = {
            // The widgets themselves stand in for a mark: the point of the screen is what they
            // look like. Drawn for the system appearance, since the widget will be.
            WidgetPreview(
                background = WidgetBackground.FOLLOW_THEME,
                systemIsDark = isSystemInDarkTheme(),
                content = null,
            )
            Spacer(Modifier.height(28.dp))
            Headline(stringResource(Res.string.onboarding_widget_title))
            Spacer(Modifier.height(12.dp))
            Body(stringResource(widgetStepBodyKey(addPath)))
        },
        belowDots = {
            if (offer == WidgetStepOffer.ANDROID_STEPS) {
                // A polite live region only after a refused tap: the button TalkBack was on has
                // just gone, and this is what took its place. On a phone that cannot pin at all
                // the steps are there from the first frame and are read like the rest.
                Body(
                    stringResource(Res.string.onboarding_widget_steps_android),
                    Modifier
                        .bringIntoViewRequester(stepsInView)
                        .then(if (pinRefused) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
                )
                Spacer(Modifier.height(20.dp))
            }
        },
    ) {
        when (offer) {
            WidgetStepOffer.PIN -> {
                TaqwaPrimaryButton(
                    stringResource(Res.string.onboarding_widget_cta_add),
                    onClick = {
                        widgetPinRequester.askToPin(
                            PinnableWidget.PRAYER,
                            onTaken = onComplete,
                            onRefused = { pinRefused = true },
                        )
                    },
                )
                TaqwaTextLink(stringResource(Res.string.onboarding_widget_secondary), onClick = onComplete)
            }
            WidgetStepOffer.ANDROID_STEPS, WidgetStepOffer.DONE -> {
                TaqwaPrimaryButton(stringResource(Res.string.onboarding_widget_cta_done), onClick = onComplete)
                Spacer(Modifier.height(44.dp))
            }
        }
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
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = TaqwaText.caption.copy(fontSize = 16.sp),
        color = LocalTaqwaColors.current.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepDots(step: OnboardingStep, steps: List<OnboardingStep>) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEach { entry ->
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
