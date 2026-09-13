package world.taqwa.app.notifications

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import world.taqwa.app.settings.appContext

actual fun openAppNotificationSettings() {
    val context = appContext
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

actual fun requestExactAlarmAccess() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = appContext
    // The package URI opens Taqwa's own row rather than the list of every app. Some OEM builds
    // ship no activity for this action at all; the note stays and nothing crashes.
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
