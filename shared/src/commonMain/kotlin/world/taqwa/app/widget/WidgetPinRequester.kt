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

    /** Fire-and-forget: the launcher's sheet is system UI, and whether the user confirms it is
     * not reported back reliably enough to build a flow on. */
    fun requestPin(widget: PinnableWidget)
}

expect fun createWidgetPinRequester(): WidgetPinRequester
