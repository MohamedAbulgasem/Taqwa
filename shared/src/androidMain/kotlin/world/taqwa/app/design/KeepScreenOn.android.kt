package world.taqwa.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * The flag lives on the view rather than on the window so it is undone by leaving the
 * composition, with no Activity reference to lose.
 *
 * What was there before is put back rather than cleared: the host may have set the flag for its
 * own reasons — a video surface, a kiosk mode, a debug toggle — and leaving the counter is no
 * reason to turn that off. Restoring what was read is right in both cases, since on a screen
 * nobody else asked to keep awake the previous value is false anyway.
 */
@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}
