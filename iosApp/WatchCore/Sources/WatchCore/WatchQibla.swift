import Foundation

/// The dial's state machine and the turn a screen reader is told (plan 4 Task 9), `:watchcore`'s
/// `QiblaGuidance.kt` and `WatchQibla.kt` line for line, held to `compass-vectors.json`.

/// Which way the wearer turns to face the Qibla: clockwise is right (the vectors' spelling).
enum Turn: String, Equatable {
    case LEFT, RIGHT
}

/// A turn and its size, in whole steps of `QiblaGuidance.step` degrees.
struct Guidance: Equatable {
    let turn: Turn
    let degrees: Int
}

/// Which way to turn, in 15° steps: the phone's accessibility round 2's design, so the phone and
/// both watches tell a screen reader one thing.
enum QiblaGuidance {
    static let step = 15

    /// The guidance for [delta], `QiblaAngles.angleDelta(heading, bearing)`: RIGHT for 0 or more,
    /// the degrees |delta| rounded to a step (half up, as Kotlin's `roundToInt`), never under one
    /// step nor over a half turn. [delta] must be finite: `QiblaGuidanceTracker` answers nil for
    /// one that is not. The rounding is done in `Double`, so no finite delta can trap.
    static func of(_ delta: Double) -> Guidance {
        let steps = (abs(delta) / Double(step)).rounded(.toNearestOrAwayFromZero)
        let degrees = min(max(steps * Double(step), Double(step)), 180)
        return Guidance(turn: delta >= 0 ? .RIGHT : .LEFT, degrees: Int(degrees))
    }
}

/// The guidance being spoken: it changes only when the turn changes or the delta is more than 3°
/// past its step's edge (10.5° from the degrees it speaks), and at most once every 1,500 ms from
/// the last telling, so a hand that wobbles is not narrated. Nil while there is no direction to
/// give, a delta that is not finite included, and the next direction after that is told at once.
final class QiblaGuidanceTracker {
    private static let minInterval: Int64 = 1_500
    private static let margin = 3.0

    private var spoken: Guidance?
    private var spokenAt: Int64 = 0

    func update(nowMillis: Int64, delta: Double?) -> Guidance? {
        guard let delta, delta.isFinite else {
            spoken = nil
            return nil
        }
        let candidate = QiblaGuidance.of(delta)
        let take: Bool
        if let current = spoken {
            if candidate == current {
                take = false
            } else if nowMillis - spokenAt < Self.minInterval {
                take = false
            } else if candidate.turn != current.turn {
                take = true
            } else {
                take = abs(abs(delta) - Double(current.degrees)) > Double(QiblaGuidance.step) / 2.0 + Self.margin
            }
        } else {
            take = true
        }
        if take {
            spoken = candidate
            spokenAt = nowMillis
        }
        return spoken
    }

    /// Forgets what was spoken, so the next delta is taken at once.
    func reset() {
        spoken = nil
        spokenAt = 0
    }
}

/// What the watch's Qibla dial shows.
enum DialState: Equatable {
    /// No magnetometer: the bearing and the distance, with no needle. The screen's to choose,
    /// before any reading; `WatchQibla` never answers it.
    case noSensor
    /// A trusted heading away from the bearing, and the turn a screen reader is told, if any.
    case searching(heading: Double, guidance: Guidance?)
    /// A trusted heading facing the bearing: the rim lights.
    case aligned(heading: Double)
    /// The compass can probably be fixed: no needle, and the fix that suits the reason.
    case low(CompassLowReason)
    /// Low too long to ask again: the bearing and the distance, no needle.
    case bestEffort(CompassLowReason)
}

/// One reading's answer: the state to draw, and whether the watch buzzes for it.
struct DialStep: Equatable {
    let state: DialState
    let buzz: Bool
}

/// The watch's Qibla dial over the feed's bearing: the phone's `QiblaViewModel.collectWhileActive`
/// body restated over a bearing, so the watch judges its compass with the phone's own rules.
///
/// - The gate first, then the filter on every sample.
/// - BestEffort and Low clear the alignment.
/// - Good is Aligned while `QiblaAngles.isAligned` holds with the last step's answer as its memory
///   (in within 5°, out only beyond 7°), and buzzes only on entry; otherwise Searching, with the
///   turn from the smoothed heading to the bearing.
/// - Every state but Searching gives the tracker nil, so the turn is told afresh on searching again.
/// - A trusted NaN heading is Searching at NaN with no turn, and the filter keeps it until `reset`.
final class WatchQibla {
    private let bearing: Double
    private let filter: HeadingFilter
    private let gate: CompassAccuracyGate
    private let guidance: QiblaGuidanceTracker
    private var wasAligned = false

    init(bearing: Double, filter: HeadingFilter = .init(), gate: CompassAccuracyGate = .init(), guidance: QiblaGuidanceTracker = .init()) {
        self.bearing = bearing
        self.filter = filter
        self.gate = gate
        self.guidance = guidance
    }

    /// Forgets the heading, the gate's runs, the guidance and the alignment: on every entry to the
    /// page, as the phone resets on every `collectWhileActive`.
    func reset() {
        filter.reset()
        gate.reset()
        guidance.reset()
        wasAligned = false
    }

    func onReading(_ reading: CompassReading) -> DialStep {
        let accuracy = gate.update(reading.timestampMillis, low: reading.isLowAccuracy, reason: reading.lowReason)
        let smoothed = filter.update(reading.trueHeadingDegrees)
        switch accuracy {
        case let .bestEffort(reason):
            return notPointing(reading, .bestEffort(reason))
        case let .low(reason):
            return notPointing(reading, .low(reason))
        case .good:
            if QiblaAngles.isAligned(smoothed, bearing, wasAligned: wasAligned) {
                let buzz = !wasAligned
                wasAligned = true
                _ = guidance.update(nowMillis: reading.timestampMillis, delta: nil)
                return DialStep(state: .aligned(heading: smoothed), buzz: buzz)
            }
            wasAligned = false
            let turn = guidance.update(nowMillis: reading.timestampMillis, delta: QiblaAngles.angleDelta(smoothed, bearing))
            return DialStep(state: .searching(heading: smoothed, guidance: turn), buzz: false)
        }
    }

    private func notPointing(_ reading: CompassReading, _ state: DialState) -> DialStep {
        wasAligned = false
        _ = guidance.update(nowMillis: reading.timestampMillis, delta: nil)
        return DialStep(state: state, buzz: false)
    }
}
