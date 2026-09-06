package world.taqwa.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * iOS derives the status bar style from the window's interface style, so the honest fix is to
 * override that style on the window itself rather than fight the status bar directly. Under
 * [ThemeMode.SYSTEM] the override is lifted and the system decides, as before.
 */
@Composable
actual fun SystemBarsAppearance(mode: ThemeMode, dark: Boolean) {
    SideEffect {
        val style = when (mode) {
            ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .forEach { window ->
                if (window.overrideUserInterfaceStyle != style) window.overrideUserInterfaceStyle = style
            }
    }
}
