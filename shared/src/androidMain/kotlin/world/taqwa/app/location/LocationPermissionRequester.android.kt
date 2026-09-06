package world.taqwa.app.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberLocationPermissionRequester(
    repository: LocationRepository,
    onResult: (LocationPermission) -> Unit,
): () -> Unit {
    // The launcher outlives individual recompositions, so the callback it captured must not.
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        latest.value(if (granted) LocationPermission.GRANTED else LocationPermission.DENIED)
    }
    // Coarse is enough for prayer times: the calculation is insensitive to a few hundred metres,
    // and asking for fine accuracy invites a refusal we do not need.
    return remember(launcher) { { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) } }
}
