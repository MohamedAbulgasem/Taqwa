package world.taqwa.app.widget

actual val isIosPlatform: Boolean = true

actual val widgetAddPath: WidgetAddPath =
    if ((platform.UIKit.UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0) >= 18) {
        WidgetAddPath.IOS_HOLD_ICON
    } else {
        WidgetAddPath.IOS_PLUS_BUTTON
    }
