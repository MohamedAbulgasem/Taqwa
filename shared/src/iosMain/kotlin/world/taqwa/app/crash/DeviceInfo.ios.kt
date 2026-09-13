package world.taqwa.app.crash

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
actual fun deviceInfo(): DeviceInfo {
    val bundle = NSBundle.mainBundle
    val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"
    // UIDevice.model is only "iPhone"; the hardware identifier (iPhone14,5) comes from uname.
    val model = memScoped {
        val u = alloc<utsname>()
        uname(u.ptr)
        u.machine.toKString()
    }
    return DeviceInfo(
        appVersion = version,
        build = build,
        platform = "iOS ${UIDevice.currentDevice.systemVersion}",
        device = model,
        language = NSLocale.currentLocale.localeIdentifier,
    )
}
