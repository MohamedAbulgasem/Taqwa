package world.taqwa.app.share

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
actual fun shareText(text: String) {
    val root = keyWindow()?.rootViewController ?: return
    // Present from whatever is on top, or UIKit refuses with "already presenting".
    var top: UIViewController = root
    while (top.presentedViewController != null) top = top.presentedViewController!!
    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    // On iPad the activity sheet is a popover and UIKit raises (uncatchably from Kotlin) when it
    // has no anchor; the app targets iPad too. Anchored to the centre of the presenting view,
    // with no arrow, it appears as a centred sheet.
    sheet.popoverPresentationController?.let { popover ->
        popover.sourceView = top.view
        top.view.bounds.useContents {
            popover.sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
        }
        popover.permittedArrowDirections = 0uL
    }
    top.presentViewController(sheet, animated = true, completion = null)
}

/** The key window under the scene lifecycle (the app is a SwiftUI WindowGroup, so
 * `UIApplication.keyWindow` can be nil): the first key window of any foreground scene. */
private fun keyWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIWindowScene }
    return scenes.flatMap { scene -> scene.windows.mapNotNull { it as? UIWindow } }.firstOrNull { it.isKeyWindow() }
        ?: scenes.firstOrNull()?.windows?.firstOrNull() as? UIWindow
}
