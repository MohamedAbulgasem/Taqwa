package world.taqwa.app.widget

import kotlinx.coroutines.runBlocking

/** Set once from `TaqwaApplication.onCreate` (Step 9) — `shared` cannot reference the Glance
 * widget classes, which live in `androidApp` alongside the generated `R` class. */
var androidWidgetUpdateHook: (suspend () -> Unit)? = null

actual fun refreshWidgets() {
    androidWidgetUpdateHook?.let { hook -> runBlocking { hook() } }
}
