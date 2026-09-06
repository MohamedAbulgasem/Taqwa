package world.taqwa.app.widget

/** WidgetKit offers no way to place a widget from inside an app; the user does it from the
 * home screen, and onboarding tells them how. */
actual fun createWidgetPinRequester(): WidgetPinRequester = object : WidgetPinRequester {
    override val isSupported: Boolean = false
    override fun requestPin() = Unit
}
