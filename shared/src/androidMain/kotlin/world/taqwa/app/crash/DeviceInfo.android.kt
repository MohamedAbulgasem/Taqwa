package world.taqwa.app.crash

import android.os.Build
import world.taqwa.app.settings.appContext
import java.util.Locale

actual fun deviceInfo(): DeviceInfo {
    val context = appContext
    val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkg.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pkg.versionCode.toLong()
    }
    return DeviceInfo(
        appVersion = pkg.versionName ?: "?",
        build = code.toString(),
        platform = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        language = Locale.getDefault().toLanguageTag(),
    )
}
