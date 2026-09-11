package world.taqwa.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * `idleTimerDisabled` is application-wide, so it must be put back on dispose — leaving it set
 * would keep every other screen awake too, and drain the battery of a reader who has long since
 * moved on from the counter.
 */
@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
