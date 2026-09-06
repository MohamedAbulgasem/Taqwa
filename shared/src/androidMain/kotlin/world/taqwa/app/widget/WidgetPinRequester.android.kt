package world.taqwa.app.widget

import android.appwidget.AppWidgetManager
import world.taqwa.app.settings.appContext

/** Set from `TaqwaApplication.onCreate`, next to [androidWidgetUpdateHook]: the widget receiver
 * classes live in `androidApp`, which `shared` cannot see. Returns whether the launcher took
 * the request. */
var androidWidgetPinHook: (() -> Boolean)? = null

actual fun createWidgetPinRequester(): WidgetPinRequester = object : WidgetPinRequester {
    override val isSupported: Boolean
        get() = androidWidgetPinHook != null &&
            (AppWidgetManager.getInstance(appContext)?.isRequestPinAppWidgetSupported ?: false)

    override fun requestPin() {
        runCatching { androidWidgetPinHook?.invoke() }
    }
}
