package world.taqwa.app.feature.onboarding

import world.taqwa.app.resources.Res
import world.taqwa.app.resources.onboarding_widget_body
import world.taqwa.app.resources.onboarding_widget_body_ios
import world.taqwa.app.resources.onboarding_widget_body_ios_legacy
import world.taqwa.app.widget.WidgetAddPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The last onboarding screen and the launcher's answer to "Add widget". The rule: the steps by
 * hand appear only when the app knows the request did not work. A request the launcher took ends
 * onboarding exactly as before, under the launcher's own sheet. The answer itself is
 * [world.taqwa.app.widget.WidgetPinRequesterTest]; the tap on the real screen is the iOS
 * OnboardingWidgetPageTest.
 */
class OnboardingWidgetStepTest {

    @Test
    fun `a launcher that takes requests is offered Add widget until it refuses one`() {
        assertEquals(
            WidgetStepOffer.PIN,
            widgetStepOffer(canPin = true, pinRefused = false, addPath = WidgetAddPath.ANDROID_PIN),
        )
        assertEquals(
            WidgetStepOffer.ANDROID_STEPS,
            widgetStepOffer(canPin = true, pinRefused = true, addPath = WidgetAddPath.ANDROID_PIN),
        )
    }

    @Test
    fun `an android launcher with no pin support gets the steps by hand from the start`() {
        for (refused in listOf(false, true)) {
            assertEquals(
                WidgetStepOffer.ANDROID_STEPS,
                widgetStepOffer(canPin = false, pinRefused = refused, addPath = WidgetAddPath.ANDROID_PIN),
            )
        }
    }

    @Test
    fun `iOS keeps its steps in the body and offers only Done`() {
        for (path in listOf(WidgetAddPath.IOS_HOLD_ICON, WidgetAddPath.IOS_PLUS_BUTTON)) {
            for (refused in listOf(false, true)) {
                assertEquals(
                    WidgetStepOffer.DONE,
                    widgetStepOffer(canPin = false, pinRefused = refused, addPath = path),
                    "path=$path refused=$refused",
                )
            }
        }
    }

    @Test
    fun `an android body never gives the iPhone steps`() {
        assertEquals(Res.string.onboarding_widget_body, widgetStepBodyKey(WidgetAddPath.ANDROID_PIN))
        assertEquals(Res.string.onboarding_widget_body_ios, widgetStepBodyKey(WidgetAddPath.IOS_HOLD_ICON))
        assertEquals(Res.string.onboarding_widget_body_ios_legacy, widgetStepBodyKey(WidgetAddPath.IOS_PLUS_BUTTON))
    }
}
