package world.taqwa.app.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual suspend fun requestNotificationPermission(): Boolean = suspendCancellableCoroutine { cont ->
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
    ) { granted, _ -> if (cont.isActive) cont.resume(granted) }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun isNotificationPermissionGranted(): Boolean = suspendCancellableCoroutine { cont ->
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        val status = settings?.authorizationStatus
        if (cont.isActive) {
            cont.resume(status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional)
        }
    }
}

/**
 * iOS needs no Activity-scoped launcher: the request is a plain suspend call, so the composable
 * shape exists only to keep the call site in `OnboardingScreen` identical on both platforms.
 */
@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onResult)
    return remember(scope) {
        { scope.launch { latest.value(requestNotificationPermission()) } }
    }
}
