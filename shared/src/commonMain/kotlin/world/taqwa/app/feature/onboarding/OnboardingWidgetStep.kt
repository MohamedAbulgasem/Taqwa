package world.taqwa.app.feature.onboarding

import org.jetbrains.compose.resources.StringResource
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.onboarding_widget_body
import world.taqwa.app.resources.onboarding_widget_body_ios
import world.taqwa.app.resources.onboarding_widget_body_ios_legacy
import world.taqwa.app.widget.WidgetAddPath

/** What the widget screen offers below its copy. */
enum class WidgetStepOffer {
    /** "Add widget", which asks the launcher, and "Maybe later". */
    PIN,

    /** Android when the launcher cannot be asked, or has just refused: the steps by hand where
     * the button was, then Done. */
    ANDROID_STEPS,

    /** iOS: the body already gives the steps, so only Done. */
    DONE,
}

/**
 * [pinRefused] is only ever set by the refused branch of
 * [world.taqwa.app.widget.askToPin], so a launcher that takes the request never sees the steps:
 * the screen stays exactly as it was before they existed.
 */
fun widgetStepOffer(canPin: Boolean, pinRefused: Boolean, addPath: WidgetAddPath): WidgetStepOffer = when {
    canPin && !pinRefused -> WidgetStepOffer.PIN
    addPath == WidgetAddPath.ANDROID_PIN -> WidgetStepOffer.ANDROID_STEPS
    else -> WidgetStepOffer.DONE
}

/** The screen's body: on iOS it carries that system's steps; on Android it only says what the
 * widget is, and the Android steps, when needed, sit where the button was. */
fun widgetStepBodyKey(addPath: WidgetAddPath): StringResource = when (addPath) {
    WidgetAddPath.ANDROID_PIN -> Res.string.onboarding_widget_body
    WidgetAddPath.IOS_HOLD_ICON -> Res.string.onboarding_widget_body_ios
    WidgetAddPath.IOS_PLUS_BUTTON -> Res.string.onboarding_widget_body_ios_legacy
}
