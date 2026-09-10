package world.taqwa.app.widget

/** WidgetKit offers no way to place a widget from inside an app — for either widget — so the user
 * does it from the home screen, and the app tells them how. */
actual fun createWidgetPinRequester(): WidgetPinRequester = object : WidgetPinRequester {
    override fun isSupported(widget: PinnableWidget): Boolean = false
    override fun requestPin(widget: PinnableWidget) = Unit
}
