package world.taqwa.app.widget

/** The widgets a user can be offered: the prayer widget and the ayah card. */
enum class PinnableWidget { PRAYER, AYAH }

/**
 * Asks the launcher to place a Taqwa widget, where the platform allows asking.
 *
 * Android launchers accept a pin request and show their own confirmation sheet; iOS has no such
 * call, so there [isSupported] is false for both widgets and the app shows the manual steps
 * instead of a button that could do nothing.
 */
interface WidgetPinRequester {
    fun isSupported(widget: PinnableWidget): Boolean

    /**
     * True when the launcher took the request and shows its own sheet; whether the user confirms
     * that sheet is not reported back reliably enough to build a flow on. False when it is known
     * the request did not work (the launcher refused to ask, or there is nothing to ask), which is
     * the one case the caller shows the steps by hand instead.
     */
    fun requestPin(widget: PinnableWidget): Boolean
}

/**
 * Asks the launcher for [widget]. [onTaken] runs when it took the request and shows its own sheet;
 * [onRefused] runs only when it is known the request did not work, the one case a caller puts the
 * steps by hand where its button was. Onboarding and Appearance both ask through here, so a
 * request that went through can never turn a screen into the steps.
 */
fun WidgetPinRequester.askToPin(widget: PinnableWidget, onTaken: () -> Unit, onRefused: () -> Unit) {
    if (requestPin(widget)) onTaken() else onRefused()
}

expect fun createWidgetPinRequester(): WidgetPinRequester
