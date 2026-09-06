package world.taqwa.app.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
private class IosLocationProvider : LocationProvider {

    private val manager = CLLocationManager()

    private fun map(status: CLAuthorizationStatus): LocationPermission = when (status) {
        kCLAuthorizationStatusAuthorizedWhenInUse,
        kCLAuthorizationStatusAuthorizedAlways -> LocationPermission.GRANTED
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted -> LocationPermission.DENIED
        kCLAuthorizationStatusNotDetermined -> LocationPermission.NOT_REQUESTED
        else -> LocationPermission.NOT_REQUESTED
    }

    override suspend fun permission(): LocationPermission = map(CLLocationManager.authorizationStatus())

    override suspend fun requestPermission(): LocationPermission =
        suspendCancellableCoroutine { cont ->
            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    if (cont.isActive) cont.resume(map(CLLocationManager.authorizationStatus()))
                }
            }
            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
        }

    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        if (permission() != LocationPermission.GRANTED) return null
        val loc = manager.location ?: return null
        var lat = 0.0
        var lon = 0.0
        loc.coordinate.useContents { lat = latitude; lon = longitude }
        return lat to lon
    }
}

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()
