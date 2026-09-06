package world.taqwa.app.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

// The Activity-scoped POST_NOTIFICATIONS request is driven from Compose via
// rememberLauncherForActivityResult in OnboardingScreen — the same pattern Task 10 uses for
// location — so by the time this suspend function is called the result already reflects it.
actual suspend fun requestNotificationPermission(): Boolean = NotificationPermissionRequester.isGranted()

actual suspend fun isNotificationPermissionGranted(): Boolean = NotificationPermissionRequester.isGranted()

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    // The launcher outlives individual recompositions, so the callback it captured must not.
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> latest.value(granted) }
    return remember(launcher) {
        {
            // Below API 33 there is no runtime permission to ask for at all: notifications are
            // simply allowed, and launching the contract on those OS versions is a silent no-op
            // that never calls back.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                latest.value(true)
            }
        }
    }
}
