package world.taqwa.app.feature.today

import android.provider.Settings
import world.taqwa.app.settings.appContext

/**
 * `Settings.Global.AUTO_TIME` is 1 while the clock follows the network and 0 once the user has set
 * it by hand; reading it needs no permission. Null if it cannot be read, so the screen falls back
 * to the zone-offset check alone.
 */
actual fun clockSetByHand(): Boolean? = runCatching {
    Settings.Global.getInt(appContext.contentResolver, Settings.Global.AUTO_TIME) == 0
}.getOrNull()
