package world.taqwa.app.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import world.taqwa.app.settings.appContext

/**
 * Android 13 (API 33) introduced a runtime `POST_NOTIFICATIONS` permission; below that,
 * notifications need no separate grant. The system prompt itself is driven from Compose via
 * `rememberLauncherForActivityResult` in the onboarding screen — the same pattern Task 10 uses
 * for location — so by the time this is read the result is already reflected here.
 */
object NotificationPermissionRequester {
    fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
