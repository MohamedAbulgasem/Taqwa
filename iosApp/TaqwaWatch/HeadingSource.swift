import CoreLocation
import Foundation

/// Where the Qibla page's headings come from (plan 4 Task 9): the watch's own compass, or in a
/// DEBUG build a scripted one for the simulator, which has no magnetometer. With `available` false
/// the page draws `QiblaScreen.model(feed, .noSensor)` and starts nothing.
protocol HeadingSource: AnyObject {
    var available: Bool { get }
    /// Starts the headings, each handed to [onSample] on the main thread.
    func start(_ onSample: @escaping (HeadingSample) -> Void)
    func stop()
}

enum HeadingSources {
    /// The watch's own compass, or the DEBUG scripted one when a launch argument asks for it.
    static func make() -> HeadingSource {
        #if DEBUG
        if let compass = DebugLaunch.arguments.compass { return ScriptedHeading(compass) }
        #endif
        return CoreLocationHeading()
    }

    /// The watch's monotonic clock in milliseconds, which the gate's dwells run on and nothing shows.
    static func now() -> Int64 {
        Int64(ProcessInfo.processInfo.systemUptime * 1_000)
    }
}

/// The watch's own compass: Core Location's magnetic heading, as the iPhone's `IosCompassSource`
/// reads it, which needs no location and so no permission. It never asks for any authorization and
/// never asks for a location: heading alone, `startUpdatingHeading`, every change
/// (`kCLHeadingFilterNone`), with the system's own calibration screen allowed. The feed's
/// declination makes it true (`HeadingReading`), so Core Location's `trueHeading`, which needs a
/// location, is never read. The page starts it only while it is on the screen and the app active.
///
/// The heading is the watch's own top whichever way the screen is drawn (`CLHeading.h`), so it is
/// handed on as Core Location gives it and the page turns it to the screen's top for a watch worn
/// with the crown on the left (`HeadingSample.onScreen`, from `WKInterfaceDevice.crownOrientation`),
/// where the iPhone tells Core Location its interface's orientation (`headingOrientation`, which
/// watchOS 27 deprecates).
final class CoreLocationHeading: NSObject, HeadingSource, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var onSample: ((HeadingSample) -> Void)?
    #if DEBUG
    private static var loggedAuthorization = false
    #endif

    override init() {
        super.init()
        manager.delegate = self
        manager.headingFilter = kCLHeadingFilterNone
    }

    var available: Bool { CLLocationManager.headingAvailable() }

    func start(_ onSample: @escaping (HeadingSample) -> Void) {
        #if DEBUG
        if !Self.loggedAuthorization {
            Self.loggedAuthorization = true
            NSLog("TaqwaWatch heading available %d, authorizationStatus %d (never asked)", available ? 1 : 0, manager.authorizationStatus.rawValue)
        }
        #endif
        guard available else { return }
        self.onSample = onSample
        manager.startUpdatingHeading()
    }

    func stop() {
        manager.stopUpdatingHeading()
        onSample = nil
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let sample = HeadingSample(magnetic: newHeading.magneticHeading, accuracy: newHeading.headingAccuracy, millis: HeadingSources.now())
        // Core Location calls back on the run loop of the thread that made the manager: the main one.
        if Thread.isMainThread {
            onSample?(sample)
        } else {
            DispatchQueue.main.async { [weak self] in self?.onSample?(sample) }
        }
    }

    /// Lets watchOS put up its own calibration screen when it believes the compass needs one.
    func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool {
        true
    }
}

#if DEBUG
/// The DEBUG compass of `-taqwaHeading <deg>`, `-taqwaHeadingAccuracy <deg>` and `-taqwaNoCompass 1`
/// (`DebugCompass`): a steady sample every 50 ms. `-taqwaHeading` is the magnetic heading, what Core
/// Location's `magneticHeading` would give, so the page turns it for a crown on the left and makes it
/// true by the feed's declination as it does the real compass's: on the Cape Town sample (−27°),
/// with the crown on the right as the simulator has it, facing the Qibla is 50.
final class ScriptedHeading: HeadingSource {
    private let compass: DebugCompass
    private var timer: Timer?

    init(_ compass: DebugCompass) {
        self.compass = compass
    }

    deinit {
        timer?.invalidate()
    }

    var available: Bool { compass.available }

    func start(_ onSample: @escaping (HeadingSample) -> Void) {
        guard available else { return }
        stop()
        let compass = self.compass
        let send = { onSample(HeadingSample(magnetic: compass.magnetic, accuracy: compass.accuracy, millis: HeadingSources.now())) }
        send()
        let timer = Timer(timeInterval: 0.05, repeats: true) { _ in send() }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
        NSLog("TaqwaWatch debug compass: magnetic %.2f, accuracy %.2f", compass.magnetic, compass.accuracy)
    }

    func stop() {
        timer?.invalidate()
        timer = nil
    }
}
#endif
