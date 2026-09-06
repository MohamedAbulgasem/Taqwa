package world.taqwa.app.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.ProducerScope
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

    /** The collector currently receiving headings, if any. Set before `startUpdatingHeading()`. */
    private var subscriber: ProducerScope<CompassReading>? = null

    /**
     * `CLLocationManager.delegate` is a **weak** reference, so a delegate with no other strong
     * owner is deallocated before any callback fires and the dial freezes on its initial heading
     * with no error state. A delegate created as a local inside the `callbackFlow` body is not
     * referenced again after the `awaitClose` suspension point, so nothing requires the coroutine
     * frame to keep it alive. Holding it as a property of the source keeps it alive for as long
     * as the manager it serves — the same reasoning, and the same shape, as the delegate on
     * `LocationProvider.ios.kt`.
     */
    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
            val accuracy = didUpdateHeading.headingAccuracy
            subscriber?.trySend(
                CompassReading(
                    trueHeadingDegrees = didUpdateHeading.trueHeading,
                    isLowAccuracy = CompassAccuracyRules.iosAccuracyIsLow(accuracy),
                ),
            )
        }
    }

    override fun hasSensor(): Boolean = CLLocationManager.headingAvailable()

    override fun updateLocation(location: GeoLocation?) { /* no-op: trueHeading needs no correction */ }

    override val readings: Flow<CompassReading> = callbackFlow {
        if (!CLLocationManager.headingAvailable()) { close(); return@callbackFlow }

        subscriber = this
        manager.delegate = delegate
        manager.startUpdatingHeading()
        awaitClose {
            // Guarded: a recomposition can start the replacement collection before this one's
            // close runs, and an unconditional teardown would then stop the new collector's
            // updates instead of the old one's.
            if (subscriber === this) {
                manager.stopUpdatingHeading()
                manager.delegate = null
                subscriber = null
            }
        }
    }
}

actual fun createCompassSource(): CompassSource = IosCompassSource()
