package world.taqwa.app.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLDeviceOrientation
import platform.CoreLocation.CLDeviceOrientationLandscapeLeft
import platform.CoreLocation.CLDeviceOrientationLandscapeRight
import platform.CoreLocation.CLDeviceOrientationPortrait
import platform.CoreLocation.CLDeviceOrientationPortraitUpsideDown
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.darwin.NSObject
import world.taqwa.app.domain.GeoLocation

/**
 * `CLHeading.trueHeading` already applies declination — [updateLocation] is a deliberate no-op,
 * since only the Android path needs a location to correct magnetic north. Never reads
 * `magneticHeading`: that is the classic qibla bug this task exists to avoid.
 *
 * True north is not free, though. Core Location computes declination from a location fix, and it
 * only hands one to the *same* manager that is delivering headings. A manager that calls
 * `startUpdatingHeading()` alone — as this one used to — reports `trueHeading` as `-1` forever,
 * which the smoothing filter turns into a heading of 359°: a needle that looks alive, points at
 * nothing, and never says so. Hence `startUpdatingLocation()` beside it, an authorisation request
 * when the app has not asked yet, and [CompassAccuracyRules.iosTrueHeadingIsInvalid] as a floor
 * under `headingAccuracy`.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCompassSource : CompassSource {

    private val manager = CLLocationManager()

    /** The collector currently receiving headings, if any. Set before `startUpdatingHeading()`. */
    private var subscriber: ProducerScope<CompassReading>? = null

    /** Last orientation pushed to the manager, so an unchanged one is not written 20 times a second. */
    private var headingOrientation: CLDeviceOrientation = CLDeviceOrientationPortrait

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
            // The device can be turned without the screen being turned; Core Location cannot know
            // which way the phone is being held unless it is told, and untold it answers for a
            // portrait device, putting a sideways phone 90° out.
            syncHeadingOrientation(manager)
            val accuracy = didUpdateHeading.headingAccuracy
            val heading = didUpdateHeading.trueHeading
            val invalid = CompassAccuracyRules.iosTrueHeadingIsInvalid(heading)
            subscriber?.trySend(
                CompassReading(
                    // A negative heading is a sentinel, not a direction; passing it on as 359°
                    // would be the bug this rejects, so it is emitted as 0 and marked low.
                    trueHeadingDegrees = if (invalid) 0.0 else heading,
                    timestampMillis = monotonicMillis(),
                    isLowAccuracy = invalid || CompassAccuracyRules.iosAccuracyIsLow(accuracy),
                    lowReason = CompassLowReason.CALIBRATION,
                ),
            )
        }

        /**
         * Lets iOS put up its own calibration dial when it believes the magnetometer needs one.
         * Returning false — the default when this is not implemented — means the system never
         * offers the one calibration UI the platform has.
         */
        override fun locationManagerShouldDisplayHeadingCalibration(manager: CLLocationManager): Boolean = true
    }

    override fun hasSensor(): Boolean = CLLocationManager.headingAvailable()

    override fun updateLocation(location: GeoLocation?) { /* no-op: trueHeading needs no correction */ }

    private fun monotonicMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000.0).toLong()

    private fun syncHeadingOrientation(manager: CLLocationManager) {
        val orientation = when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationPortrait -> CLDeviceOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> CLDeviceOrientationPortraitUpsideDown
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> CLDeviceOrientationLandscapeLeft
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> CLDeviceOrientationLandscapeRight
            // Face up, face down and unknown say nothing about which way the screen is pointing,
            // so the last real orientation stands.
            else -> headingOrientation
        }
        if (orientation != headingOrientation) {
            headingOrientation = orientation
            manager.headingOrientation = orientation
        }
    }

    override val readings: Flow<CompassReading> = callbackFlow {
        if (!CLLocationManager.headingAvailable()) { close(); return@callbackFlow }

        subscriber = this
        manager.delegate = delegate
        // `UIDevice.orientation` is `unknown` until something asks for these notifications.
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
        syncHeadingOrientation(manager)
        // Only when the app has never asked: asking again when the user has said no is noise, and
        // the location screen owns that conversation.
        if (CLLocationManager.authorizationStatus() == kCLAuthorizationStatusNotDetermined) {
            manager.requestWhenInUseAuthorization()
        }
        // The heading updates are what the dial needs; the location updates are what makes those
        // headings *true* north rather than a permanent -1.
        manager.startUpdatingLocation()
        manager.startUpdatingHeading()
        awaitClose {
            // Guarded: a recomposition can start the replacement collection before this one's
            // close runs, and an unconditional teardown would then stop the new collector's
            // updates instead of the old one's.
            if (subscriber === this) {
                manager.stopUpdatingHeading()
                manager.stopUpdatingLocation()
                UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
                manager.delegate = null
                subscriber = null
            }
        }
    }
}

actual fun createCompassSource(): CompassSource = IosCompassSource()
