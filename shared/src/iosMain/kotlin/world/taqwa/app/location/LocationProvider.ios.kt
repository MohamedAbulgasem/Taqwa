package world.taqwa.app.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
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

    private val manager = CLLocationManager()

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
            pendingPermission = null
            if (continuation.isActive) continuation.resume(map(CLLocationManager.authorizationStatus()))
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

    init {
        manager.delegate = delegate
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

        return suspendCancellableCoroutine { cont ->
            pendingPermission = cont
            cont.invokeOnCancellation { pendingPermission = null }
            manager.requestWhenInUseAuthorization()
        }
    }

    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        if (permission() != LocationPermission.GRANTED) return null
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
            suspendCancellableCoroutine { cont ->
                pendingFix = cont
                cont.invokeOnCancellation { pendingFix = null }
                manager.requestLocation()
            }
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 8_000L
    }
}

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()
