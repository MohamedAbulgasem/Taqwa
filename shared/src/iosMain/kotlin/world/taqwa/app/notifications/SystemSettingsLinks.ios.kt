package world.taqwa.app.notifications

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/** iOS opens the app's own page in Settings, where the notification switch lives. */
actual fun openAppNotificationSettings() = openAppSettings()

/** The app's own page in Settings: its Location and Notifications switches are both there. */
actual fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}

/** No exact-alarm permission exists on iOS; see ExactAlarms.ios.kt. */
actual fun requestExactAlarmAccess() = Unit

/** The same Settings page: iOS lists "Preferred Language" there for a multi-language app. */
actual fun canOpenAppLanguageSettings(): Boolean = true

actual fun openAppLanguageSettings() = openAppNotificationSettings()
