package world.taqwa.app.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject
import world.taqwa.app.domain.GeoLocation

/**
 * `CLHeading.trueHeading` already applies declination — [updateLocation] is a deliberate no-op,
 * since only the Android path needs a location to correct magnetic north. Never reads
 * `magneticHeading`: that is the classic qibla bug this task exists to avoid.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCompassSource : CompassSource {

    private val manager = CLLocationManager()

    override fun hasSensor(): Boolean = CLLocationManager.headingAvailable()

    override fun updateLocation(location: GeoLocation?) { /* no-op: trueHeading needs no correction */ }

    override val readings: Flow<CompassReading> = callbackFlow {
        if (!CLLocationManager.headingAvailable()) { close(); return@callbackFlow }

        // Held as a local captured by this still-running coroutine body (alive until awaitClose
        // returns), not handed off and forgotten — `CLLocationManager.delegate` is a weak
        // reference, so a delegate with no other strong owner would be deallocated before any
        // callback fires, exactly the bug documented on `LocationProvider.ios.kt`.
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
                val accuracy = didUpdateHeading.headingAccuracy
                trySend(
                    CompassReading(
                        trueHeadingDegrees = didUpdateHeading.trueHeading,
                        isLowAccuracy = CompassAccuracyRules.iosAccuracyIsLow(accuracy),
                    ),
                )
            }
        }
        manager.delegate = delegate
        manager.startUpdatingHeading()
        awaitClose { manager.stopUpdatingHeading() }
    }
}

actual fun createCompassSource(): CompassSource = IosCompassSource()
