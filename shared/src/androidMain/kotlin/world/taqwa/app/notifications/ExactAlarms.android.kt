package world.taqwa.app.notifications

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import world.taqwa.app.settings.appContext

actual fun canScheduleExactAlarms(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        ?: return false
    return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
}
