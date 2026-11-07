package world.taqwa.app.crash

import android.app.ActivityManager
import android.app.NotificationManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import world.taqwa.app.notifications.AlarmLog
import world.taqwa.app.notifications.canScheduleExactAlarms
import world.taqwa.app.settings.appContext
import kotlin.time.Instant

/**
 * Every fact read on its own, so one the phone refuses costs only its own part of a line. Nothing
 * here needs a permission: each is the app asking about itself, or the phone's ringer.
 */
actual fun notificationState(): NotificationState? {
    val context = appContext
    val notifications = context.getSystemService(NotificationManager::class.java)
    return NotificationState(
        notificationsAllowed = read { notifications.areNotificationsEnabled() },
        exactAlarmsAllowed = read { canScheduleExactAlarms() },
        batteryOptimised = read {
            !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        },
        backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            read { context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }
        } else {
            null
        },
        ringerMode = read { context.getSystemService(AudioManager::class.java).ringerMode },
        interruptionFilter = read { notifications.currentInterruptionFilter },
        recordsAlarms = true,
        lastAlarm = read { AlarmLog.lastAlarm(context) },
        appUpdatedAt = read {
            Instant.fromEpochMilliseconds(context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime)
        },
    )
}

private inline fun <T> read(block: () -> T): T? = runCatching(block).getOrNull()
