package world.taqwa.app.share

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

actual fun shareText(text: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    // Present from whatever is on top, or UIKit refuses with "already presenting".
    var top: UIViewController = root
    while (top.presentedViewController != null) top = top.presentedViewController!!
    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    top.presentViewController(sheet, animated = true, completion = null)
}
