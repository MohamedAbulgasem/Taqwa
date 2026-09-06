package world.taqwa.app.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

@Composable
actual fun rememberLocationPermissionRequester(
    repository: LocationRepository,
    onResult: (LocationPermission) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onResult)
    return remember(repository, scope) {
        { scope.launch { latest.value(repository.requestPermission()) } }
    }
}
