package world.taqwa.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * The flag lives on the view rather than on the window so it is undone by leaving the
 * composition, with no Activity reference to lose and nothing to restore on the way out.
 */
@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
