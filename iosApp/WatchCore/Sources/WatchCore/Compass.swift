import Foundation

/// The compass rules of `:watchcore`'s qibla package in Swift (plan 4 Task 9), line for line: the
/// angle rules, true north, the iPhone's accuracy rules, the heading filter and the accuracy gate.
/// They are the phone's own (`QiblaMath`, `IosCompassSource` and its view model's), moved there
/// by plan 3's Task 2, so the phone, the Galaxy and the Apple Watch judge a compass by one rule.
/// `compass-vectors.json` holds the port to the Kotlin's answers (`CompassVectorsTests`).

/// The angle rules of pointing at the Kaaba: the signed shortest delta, and the aligned decision.
enum QiblaAngles {
    /// A dial not yet aligned lights when the heading comes within this many degrees (spec 4.3).
    static let alignedEnter = 5.0
    /// An aligned dial goes out only when the heading is more than this many degrees off: the two
    /// degrees between are hysteresis (Dev 1's B40), so a wrist resting near the edge does not
    /// flicker the rim and the buzz.
    static let alignedExit = 7.0

    /// The signed shortest angular distance from [a] to [b], in (−180, 180]: positive when [b] lies
    /// clockwise of [a]. Kotlin's `%` on a `Double` is `truncatingRemainder`.
    static func angleDelta(_ a: Double, _ b: Double) -> Double {
        var delta = (b - a).truncatingRemainder(dividingBy: 360.0)
        if delta > 180.0 { delta -= 360.0 }
        if delta <= -180.0 { delta += 360.0 }
        return delta
    }

    /// Whether [heading] is within [threshold] of [bearing]: one threshold, no memory.
    static func isAligned(_ heading: Double, _ bearing: Double, threshold: Double = alignedEnter) -> Bool {
        abs(angleDelta(heading, bearing)) <= threshold
    }

    /// The aligned decision with memory: in within `alignedEnter`, out only beyond `alignedExit`,
    /// [wasAligned] being the previous reading's answer.
    static func isAligned(_ heading: Double, _ bearing: Double, wasAligned: Bool) -> Bool {
        isAligned(heading, bearing, threshold: wasAligned ? alignedExit : alignedEnter)
    }
}

/// Arithmetic only: a magnetic heading made true by a declination in degrees, east-positive.
enum TrueNorth {
    static func correct(_ magnetic: Double, _ declination: Double) -> Double {
        let corrected = (magnetic + declination).truncatingRemainder(dividingBy: 360.0)
        return corrected < 0.0 ? corrected + 360.0 : corrected
    }

    /// The watch's true heading: Core Location's magnetic heading corrected by the feed's
    /// declination, or −1, Core Location's own "could not determine", when there is no magnetic
    /// heading or no declination, so that `CompassAccuracyRules.iosSampleIsLow` refuses it rather
    /// than passing magnetic north off as true north.
    static func fromMagneticOrInvalid(_ magnetic: Double, _ declination: Double?) -> Double {
        guard let declination, !(magnetic < 0.0) else { return -1.0 }
        return correct(magnetic, declination)
    }
}

/// The iOS accuracy rules: Core Location's own signals, a negative heading and a heading error in
/// degrees, as the iPhone's source reads them.
enum CompassAccuracyRules {
    /// The largest heading error iOS may report and still get a needle: measured on an iPhone 13
    /// in Cape Town, ordinary use ran from 10° to 33° (`CompassAccuracyRules.kt`).
    static let iosMaxHeadingError = 45.0

    /// Worse than `iosMaxHeadingError`, or negative (Core Location's "invalid").
    static func iosAccuracyIsLow(_ accuracy: Double) -> Bool {
        accuracy < 0.0 || accuracy > iosMaxHeadingError
    }

    /// Core Location's negative heading: not a direction, a sentinel.
    static func iosTrueHeadingIsInvalid(_ trueHeading: Double) -> Bool {
        trueHeading < 0.0
    }

    /// The whole per-sample verdict: a heading Core Location could not determine, or one whose
    /// own error estimate is beyond `iosAccuracyIsLow`'s bar.
    static func iosSampleIsLow(_ trueHeading: Double, _ accuracy: Double) -> Bool {
        iosTrueHeadingIsInvalid(trueHeading) || iosAccuracyIsLow(accuracy)
    }
}

/// A low-pass filter on a circular quantity: it averages by the shortest delta, so 359° and 1°
/// average to 0°, never 180°. A heading that is not a number is kept until `reset`, as the
/// phone's is.
final class HeadingFilter {
    private let smoothing: Double
    private var current: Double?

    init(smoothing: Double = 0.15) {
        self.smoothing = smoothing
    }

    func update(_ raw: Double) -> Double {
        let next: Double
        if let previous = current {
            let delta = QiblaAngles.angleDelta(previous, raw)
            next = (previous + smoothing * delta + 360.0).truncatingRemainder(dividingBy: 360.0)
        } else {
            next = raw
        }
        current = next
        return next
    }

    func reset() {
        current = nil
    }
}

/// Why a reading is not trustworthy (the vectors' spelling). The two want opposite advice.
enum CompassLowReason: String, Equatable {
    /// The magnetometer's calibration has drifted: a figure of eight can fix it.
    case CALIBRATION
    /// The field around the watch is not the Earth's: moving helps, waving does not.
    case INTERFERENCE
}

/// What the gate believes about the compass now.
enum CompassAccuracyState: Equatable {
    /// Point the needle.
    case good
    /// Do not point; ask for the fix that suits the reason.
    case low(CompassLowReason)
    /// Long enough low that asking again is not honest: show what needs no sensor.
    case bestEffort(CompassLowReason)
}

/// The one place that decides whether the compass is believable (`CompassAccuracyGate.kt`, line
/// for line): a second of low samples enters Low, a second and a half of good ones returns to
/// Good from Low and from BestEffort alike, and twenty unbroken seconds low give up, for good,
/// until a full recovery. The reason is the window's: INTERFERENCE within the last second wins.
/// A timestamp from before its run restarts the run. Time is the samples' monotonic clock, in
/// milliseconds.
final class CompassAccuracyGate {
    private let enterLowAfter: Int64
    private let recoverAfter: Int64
    private let bestEffortAfter: Int64
    private let reasonWindow: Int64

    private(set) var state: CompassAccuracyState = .good
    private var lowRunStartedAt: Int64?
    private var goodRunStartedAt: Int64?
    /// The current low run's samples still inside the reason window, oldest first.
    private var recentLowSamples: [(at: Int64, reason: CompassLowReason)] = []

    init(enterLowAfter: Int64 = 1_000, recoverAfter: Int64 = 1_500, bestEffortAfter: Int64 = 20_000, reasonWindow: Int64 = 1_000) {
        self.enterLowAfter = enterLowAfter
        self.recoverAfter = recoverAfter
        self.bestEffortAfter = bestEffortAfter
        self.reasonWindow = reasonWindow
    }

    /// Feeds one sample and answers the state it leaves. A low sample that names no reason is
    /// CALIBRATION.
    @discardableResult
    func update(_ timestamp: Int64, low: Bool, reason: CompassLowReason? = nil) -> CompassAccuracyState {
        if low {
            onLowSample(timestamp, reason ?? .CALIBRATION)
        } else {
            onGoodSample(timestamp)
        }
        return state
    }

    func reset() {
        state = .good
        lowRunStartedAt = nil
        goodRunStartedAt = nil
        recentLowSamples.removeAll()
    }

    private func onLowSample(_ now: Int64, _ reason: CompassLowReason) {
        goodRunStartedAt = nil
        if let start = lowRunStartedAt, !(now < start) {
            // The run goes on.
        } else {
            // It starts here, or the clock went back: the run and its window restart here.
            lowRunStartedAt = now
            recentLowSamples.removeAll()
        }
        let runStart = lowRunStartedAt ?? now
        recentLowSamples.append((now, reason))
        while let first = recentLowSamples.first, first.at <= now - reasonWindow {
            recentLowSamples.removeFirst()
        }
        let windowReason: CompassLowReason = recentLowSamples.contains { $0.reason == .INTERFERENCE } ? .INTERFERENCE : .CALIBRATION
        let elapsed = now - runStart
        let gaveUp: Bool
        if case .bestEffort = state { gaveUp = true } else { gaveUp = false }
        if elapsed >= bestEffortAfter || gaveUp {
            state = .bestEffort(windowReason)
        } else if elapsed >= enterLowAfter {
            state = .low(windowReason)
        }
    }

    private func onGoodSample(_ now: Int64) {
        lowRunStartedAt = nil
        recentLowSamples.removeAll()
        if state == .good {
            goodRunStartedAt = nil
            return
        }
        let runStart: Int64
        if let start = goodRunStartedAt, !(now < start) {
            runStart = start
        } else {
            runStart = now
            goodRunStartedAt = now
        }
        if now - runStart >= recoverAfter {
            state = .good
            goodRunStartedAt = nil
        }
    }
}

/// One raw reading: the heading already true, its time on the monotonic clock in milliseconds, and
/// whether this one sample is untrustworthy, and why. A single low sample is a fact, not a state:
/// `CompassAccuracyGate` makes the state.
struct CompassReading: Equatable {
    var trueHeadingDegrees: Double
    var timestampMillis: Int64
    var isLowAccuracy: Bool
    var lowReason: CompassLowReason?
}
