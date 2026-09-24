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
import platform.CoreLocation.kCLHeadingFilterNone
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UIInterfaceOrientationUnknown
import platform.UIKit.UIWindowScene
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
 *
 * That location is *only* ever used for declination, which varies over tens of kilometres and
 * months. It is asked for at the coarsest accuracy Core Location offers and with a half-kilometre
 * distance filter: a qibla dial has no use for a GPS fix, and asking for one would keep the
 * receiver awake for the life of the screen to compute a number that would not change.
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
            // The screen can be turned without the device being turned, and the other way round;
            // Core Location cannot know which way the phone is being held unless it is told, and
            // untold it answers for a portrait device, putting a sideways phone 90° out.
            syncHeadingOrientation(manager)
            val heading = didUpdateHeading.trueHeading
            val invalid = CompassAccuracyRules.iosTrueHeadingIsInvalid(heading)
            // Core Location's own verdict and nothing layered on top of it. A field-strength band
            // (20-70 µT on `CLHeading`'s x/y/z) used to sit here too: measured on an iPhone 13 in
            // Cape Town, whose field is about 25.6 µT, that vector is the *calibrated* field, and it
            // read 13-20 µT for long stretches while Core Motion rated the calibration High and
            // `headingAccuracy` sat at its best, 10° — so the screen said "magnetic interference"
            // to a compass that was fine. Core Location already marks a heading it cannot vouch
            // for, strong interference included, with a negative accuracy, which
            // [CompassAccuracyRules.iosSampleIsLow] refuses; its reason is calibration, whose copy
            // names metal, cases and speakers as the likely culprits.
            subscriber?.trySend(
                CompassReading(
                    // A negative heading is a sentinel, not a direction; passing it on as 359°
                    // would be the bug this rejects, so it is emitted as 0 and marked low.
                    trueHeadingDegrees = if (invalid) 0.0 else heading,
                    timestampMillis = monotonicMillis(),
                    isLowAccuracy = CompassAccuracyRules.iosSampleIsLow(heading, didUpdateHeading.headingAccuracy),
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

    /**
     * What Core Location wants is the orientation of the *interface*, not of the device: a phone
     * lying flat on a table has a device orientation of `faceUp`, which says nothing, while its
     * interface is still portrait or landscape and that is what the heading has to be measured
     * against. `UIDevice.orientation` also answers `unknown` unless orientation notifications are
     * being generated, and disagrees with the interface outright whenever the app or the device
     * has rotation locked.
     *
     * Recomputed on every heading callback rather than from
     * `UIApplicationDidChangeStatusBarOrientationNotification`: that notification has been
     * deprecated since iOS 13, and an observer is a second thing to register and — the part that
     * actually bites — to unregister, on a teardown path that already has to guard against a
     * replacement collector starting before the old one closes. This is two property reads at
     * heading rate (well under 100 Hz) and the write to the manager still only happens when the
     * value changes. Core Location delivers these callbacks on the run loop the manager was
     * created on, which is the main one, so the UIKit reads happen where UIKit requires.
     */
    private fun syncHeadingOrientation(manager: CLLocationManager) {
        val orientation = when (interfaceOrientation()) {
            UIInterfaceOrientationPortrait -> CLDeviceOrientationPortrait
            UIInterfaceOrientationPortraitUpsideDown -> CLDeviceOrientationPortraitUpsideDown
            // Mirrored on purpose, not a typo: the two enums name these from opposite ends. An
            // interface in landscapeLeft is a device rotated landscapeRight, and swapping these
            // is a silent 180° error in the one case the remap exists for.
            UIInterfaceOrientationLandscapeLeft -> CLDeviceOrientationLandscapeRight
            UIInterfaceOrientationLandscapeRight -> CLDeviceOrientationLandscapeLeft
            // `unknown`, and anything a future SDK adds: portrait is what Core Location assumes
            // untold, and it is what the app launches in.
            else -> CLDeviceOrientationPortrait
        }
        if (orientation != headingOrientation) {
            headingOrientation = orientation
            manager.headingOrientation = orientation
        }
    }

    /** The foreground window scene's interface orientation, or `unknown` before there is one. */
    private fun interfaceOrientation(): UIInterfaceOrientation =
        (UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene)
            ?.interfaceOrientation
            ?: UIInterfaceOrientationUnknown

    override val readings: Flow<CompassReading> = callbackFlow {
        if (!CLLocationManager.headingAvailable()) { close(); return@callbackFlow }

        subscriber = this
        manager.delegate = delegate
        syncHeadingOrientation(manager)
        // Only when the app has never asked: asking again when the user has said no is noise, and
        // the location screen owns that conversation. The instance property, not the class method
        // deprecated in iOS 14 — the deployment target is iOS 16, so there is no older path to
        // keep.
        if (manager.authorizationStatus == kCLAuthorizationStatusNotDetermined) {
            manager.requestWhenInUseAuthorization()
        }
        // The heading updates are what the dial needs; the location updates are what makes those
        // headings *true* north rather than a permanent -1. Declination is the only consumer, so
        // the coarsest fix Core Location has is more than enough, and half a kilometre of
        // movement is the point at which it could change by a hundredth of a degree.
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        manager.distanceFilter = 500.0
        // Every heading event, not only those after a degree of turning (the default filter). The
        // accuracy gate times its dwells on the samples it is given, as Android's continuous stream
        // gives them; under the default, a phone held still delivered nothing for 14.5 s, the gate
        // counted that silence as low, crossed its 20 s give-up line, and showed "compass
        // unavailable" to someone who was only holding still — and a still phone could never
        // recover, since the improving accuracy never arrived either.
        manager.headingFilter = kCLHeadingFilterNone
        manager.startUpdatingLocation()
        manager.startUpdatingHeading()
        awaitClose {
            // Guarded: a recomposition can start the replacement collection before this one's
            // close runs, and an unconditional teardown would then stop the new collector's
            // updates instead of the old one's.
            if (subscriber === this) {
                manager.stopUpdatingHeading()
                manager.stopUpdatingLocation()
                manager.delegate = null
                subscriber = null
            }
        }
    }
}

actual fun createCompassSource(): CompassSource = IosCompassSource()
