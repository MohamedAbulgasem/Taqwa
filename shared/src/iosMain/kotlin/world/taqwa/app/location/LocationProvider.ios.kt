package world.taqwa.app.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
private class IosLocationProvider : LocationProvider {

    private var managerOrNull: CLLocationManager? = null

    /**
     * CoreLocation delivers delegate callbacks on the run loop of the thread that created the
     * manager. Constructing it in the class initialiser put it on whatever thread first touched
     * `appContainer` — a `by lazy` reached from a coroutine, so typically a background worker
     * with no active run loop — and the callbacks then never arrived at all. Building it (and
     * driving it) on the main dispatcher is what makes the delegate fire; it also keeps the
     * `pending*` fields below single-threaded, as their KDoc claims.
     */
    private suspend fun manager(): CLLocationManager =
        managerOrNull ?: withContext(Dispatchers.Main) {
            managerOrNull ?: CLLocationManager().also {
                it.delegate = delegate
                managerOrNull = it
            }
        }

    /** The requests in flight, if any. Only ever touched on the main thread. */
    private var pendingPermission: CancellableContinuation<LocationPermission>? = null
    private var pendingFix: CancellableContinuation<Pair<Double, Double>?>? = null

    /**
     * `CLLocationManager.delegate` is a **weak** reference in Objective-C. A delegate created as
     * a local object inside `requestPermission()` has no other strong reference, so ARC is free
     * to deallocate it before `locationManagerDidChangeAuthorization` fires — the callback never
     * arrives and the coroutine hangs forever. Holding the delegate as a property of the provider
     * keeps it alive for as long as the manager it serves, and lets one delegate complete
     * whichever request happens to be pending.
     */
    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val continuation = pendingPermission ?: return
            // iOS calls this once when the manager is created, before the user has seen the
            // dialog, with the status still "not determined". Taking that as the answer sent
            // onboarding on to the next screen with no location while the dialog was still on
            // screen, and the Allow the user then tapped reached nobody: Today opened saying
            // location was off, and only its own button, asked with the permission already
            // granted, got a fix. The answer is the first status that is *not* undetermined.
            val status = map(CLLocationManager.authorizationStatus())
            if (status == LocationPermission.NOT_REQUESTED) return
            pendingPermission = null
            if (continuation.isActive) continuation.resume(status)
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val fix = didUpdateLocations.lastOrNull() as? CLLocation
            resumeFix(fix?.let { location ->
                var lat = 0.0
                var lon = 0.0
                location.coordinate.useContents { lat = latitude; lon = longitude }
                lat to lon
            })
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            resumeFix(null)
        }
    }

    private fun resumeFix(value: Pair<Double, Double>?) {
        val continuation = pendingFix ?: return
        pendingFix = null
        if (continuation.isActive) continuation.resume(value)
    }

    private fun map(status: CLAuthorizationStatus): LocationPermission = when (status) {
        kCLAuthorizationStatusAuthorizedWhenInUse,
        kCLAuthorizationStatusAuthorizedAlways -> LocationPermission.GRANTED
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted -> LocationPermission.DENIED
        kCLAuthorizationStatusNotDetermined -> LocationPermission.NOT_REQUESTED
        else -> LocationPermission.NOT_REQUESTED
    }

    override suspend fun permission(): LocationPermission = map(CLLocationManager.authorizationStatus())

    override suspend fun requestPermission(): LocationPermission {
        // iOS only shows the dialog — and only re-fires the delegate callback — while the status
        // is still undetermined. Asking again after the user has answered would otherwise wait on
        // a callback that never comes.
        val existing = permission()
        if (existing != LocationPermission.NOT_REQUESTED) return existing

        // The timeout is a wedge-breaker, not a UX deadline: without it a callback that never
        // arrives leaves onboarding's "Use my location" button pending forever with no recourse
        // but killing the app. On expiry the status is simply re-read — still NOT_REQUESTED if
        // the user is genuinely still deciding, which leaves the button tappable again.
        return withTimeoutOrNull(PERMISSION_TIMEOUT_MILLIS) {
            withContext(Dispatchers.Main) {
                val manager = manager()
                suspendCancellableCoroutine { cont ->
                    pendingPermission = cont
                    cont.invokeOnCancellation { pendingPermission = null }
                    manager.requestWhenInUseAuthorization()
                }
            }
        } ?: permission()
    }

    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        if (permission() != LocationPermission.GRANTED) return null
        val manager = manager()
        manager.location?.let { cached ->
            var lat = 0.0
            var lon = 0.0
            cached.coordinate.useContents { lat = latitude; lon = longitude }
            return lat to lon
        }
        // `CLLocationManager.location` is only the last cached fix, and it is nil until this
        // process has actually asked for one. Without this a freshly granted permission would
        // report "no location" on the very screen that just requested it. The timeout keeps a
        // silent GPS from wedging the caller — the UI treats null as "no fix yet".
        return withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    pendingFix = cont
                    cont.invokeOnCancellation { pendingFix = null }
                    manager.requestLocation()
                }
            }
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 15_000L
        const val PERMISSION_TIMEOUT_MILLIS = 60_000L
    }
}

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()
