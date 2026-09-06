package world.taqwa.app.location

import androidx.compose.runtime.Composable

/**
 * Asking for location is the one part of the flow that cannot live in `commonMain`.
 *
 * On Android the system dialog can only be raised through an Activity-scoped
 * `ActivityResultLauncher`, which must be registered during composition — so
 * [LocationProvider.requestPermission] there does nothing but re-read the current state.
 * On iOS the request is a plain call on `CLLocationManager` and the answer arrives on a delegate,
 * so a coroutine is the natural shape.
 *
 * This is the smallest seam that satisfies both: the composable returns a function to invoke when
 * the user taps, and reports the answer to [onResult]. The [repository] is used by the iOS actual
 * (so it shares the app's single `CLLocationManager`) and ignored by the Android one.
 *
 * The caller decides what to do with a granted permission — typically fetching coordinates with
 * [LocationRepository.currentCoordinates].
 */
@Composable
expect fun rememberLocationPermissionRequester(
    repository: LocationRepository,
    onResult: (LocationPermission) -> Unit,
): () -> Unit
