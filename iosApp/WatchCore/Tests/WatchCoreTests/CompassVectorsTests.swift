import Foundation
import XCTest
@testable import WatchCore

/// The compass rules' port (plan 4 Task 9) held to `watchcore/vectors/compass-vectors.json`, which
/// `CompassVectorsTest.kt` writes from the Kotlin rules' own answers: the angles and the aligned
/// decision, true north from the magnetic heading, the iOS lowness, the heading filter, the gate,
/// the turn and its tracker, and every run of the dial (`WatchQiblaTest`'s, the review cases of
/// Ruling 58 item 4 among them, and `WatchDialParityTest`'s scripted run). A rule changed in Kotlin
/// rewrites the file, and these tests then fail until the port follows.
final class CompassVectorsTests: XCTestCase {
    private let vectors = CompassVectors.load()
    /// The plan's tolerance for a heading the filter worked out.
    private let tolerance = 1e-9

    func testTheConstantsAreKotlins() {
        XCTAssertEqual(QiblaAngles.alignedEnter, vectors.alignedEnterDegrees)
        XCTAssertEqual(QiblaAngles.alignedExit, vectors.alignedExitDegrees)
        XCTAssertEqual(CompassAccuracyRules.iosMaxHeadingError, vectors.iosMaxHeadingErrorDegrees)
        XCTAssertEqual(QiblaGuidance.step, vectors.guidanceStep)
    }

    func testEveryCompassCaseMatchesTheVectors() {
        for c in Vectors.nonEmpty(vectors.angleDelta, "angleDelta") {
            assertSame(QiblaAngles.angleDelta(c.a.value, c.b.value), c.delta.value, "angleDelta(\(c.a.value), \(c.b.value))")
        }
        for c in Vectors.nonEmpty(vectors.isAligned, "isAligned") {
            let answer = c.wasAligned.map { QiblaAngles.isAligned(c.heading.value, c.bearing.value, wasAligned: $0) }
                ?? QiblaAngles.isAligned(c.heading.value, c.bearing.value)
            XCTAssertEqual(answer, c.aligned, "isAligned(\(c.heading.value), \(c.bearing.value), wasAligned: \(String(describing: c.wasAligned)))")
        }
        for c in Vectors.nonEmpty(vectors.trueNorth, "trueNorth") {
            assertSame(TrueNorth.fromMagneticOrInvalid(c.magnetic.value, c.declination?.value), c.heading.value, "fromMagneticOrInvalid(\(c.magnetic.value), \(String(describing: c.declination?.value)))")
        }
        for c in Vectors.nonEmpty(vectors.correct, "correct") {
            assertSame(TrueNorth.correct(c.magnetic.value, c.declination!.value), c.heading.value, "correct(\(c.magnetic.value), \(c.declination!.value))")
        }
        for c in Vectors.nonEmpty(vectors.iosSampleIsLow, "iosSampleIsLow") {
            XCTAssertEqual(CompassAccuracyRules.iosSampleIsLow(c.trueHeading.value, c.accuracy.value), c.low, "iosSampleIsLow(\(c.trueHeading.value), \(c.accuracy.value))")
        }
        var filterSteps = 0
        for run in Vectors.nonEmpty(vectors.filter, "filter") {
            let filter = run.smoothing.map { HeadingFilter(smoothing: $0.value) } ?? HeadingFilter()
            for (index, step) in run.steps.enumerated() {
                filterSteps += 1
                if step.do == "reset" {
                    filter.reset()
                    continue
                }
                assertClose(filter.update(step.raw!.value), step.out!.value, "\(run.name) step \(index)")
            }
        }
        var gateSteps = 0
        for run in Vectors.nonEmpty(vectors.gate, "gate") {
            let gate = CompassAccuracyGate()
            for (index, step) in run.steps.enumerated() {
                gateSteps += 1
                let state: CompassAccuracyState
                if step.do == "reset" {
                    gate.reset()
                    state = gate.state
                } else {
                    state = gate.update(step.t!, low: step.low!, reason: step.reason.map(reason))
                }
                XCTAssertEqual(state, step.state.gateState, "\(run.name) step \(index) at \(String(describing: step.t))")
            }
        }
        print("filter: \(filterSteps) steps, gate: \(gateSteps) steps")
    }

    func testEveryGuidanceCaseMatchesTheVectors() {
        for c in Vectors.nonEmpty(vectors.guidanceOf, "guidanceOf") {
            XCTAssertEqual(QiblaGuidance.of(c.delta.value), Guidance(turn: turn(c.turn), degrees: c.degrees), "of(\(c.delta.value))")
        }
        var steps = 0
        for run in Vectors.nonEmpty(vectors.guidanceTracker, "guidanceTracker") {
            let tracker = QiblaGuidanceTracker()
            for (index, step) in run.steps.enumerated() {
                steps += 1
                if step.do == "reset" {
                    tracker.reset()
                    continue
                }
                let answer = tracker.update(nowMillis: step.t!, delta: step.delta?.value)
                XCTAssertEqual(answer, step.guidance.map { Guidance(turn: turn($0.turn), degrees: $0.degrees) }, "\(run.name) step \(index) at \(step.t!)")
            }
        }
        print("guidanceTracker: \(steps) steps")
    }

    func testEveryDialRunMatchesTheVectors() {
        var steps = 0
        var buzzes = 0
        for run in Vectors.nonEmpty(vectors.dial, "dial") {
            let dial = run.smoothing.map { WatchQibla(bearing: run.bearing.value, filter: HeadingFilter(smoothing: $0.value)) }
                ?? WatchQibla(bearing: run.bearing.value)
            for (index, step) in run.steps.enumerated() {
                steps += 1
                if step.do == "reset" {
                    dial.reset()
                    continue
                }
                let reading = step.reading!
                let answer = dial.onReading(CompassReading(
                    trueHeadingDegrees: reading.heading.value,
                    timestampMillis: reading.t,
                    isLowAccuracy: reading.low,
                    lowReason: reading.reason.map(reason)
                ))
                let wanted = step.state!
                let at = "\(run.name) step \(index) at \(reading.t) ms"
                XCTAssertEqual(answer.buzz, step.buzz, "\(at): buzz")
                if answer.buzz { buzzes += 1 }
                switch answer.state {
                case .noSensor:
                    XCTFail("\(at): the dial never answers NoSensor, and Kotlin's \(wanted.kind)")
                case let .searching(heading, guidance):
                    XCTAssertEqual(wanted.kind, "Searching", at)
                    assertClose(heading, wanted.heading!.value, "\(at): heading")
                    XCTAssertEqual(guidance?.turn.rawValue, wanted.turn, "\(at): turn")
                    XCTAssertEqual(guidance?.degrees, wanted.degrees, "\(at): degrees")
                case let .aligned(heading):
                    XCTAssertEqual(wanted.kind, "Aligned", at)
                    assertClose(heading, wanted.heading!.value, "\(at): heading")
                case let .low(reason):
                    XCTAssertEqual(wanted.kind, "Low", at)
                    XCTAssertEqual(reason.rawValue, wanted.reason, "\(at): reason")
                case let .bestEffort(reason):
                    XCTAssertEqual(wanted.kind, "BestEffort", at)
                    XCTAssertEqual(reason.rawValue, wanted.reason, "\(at): reason")
                }
            }
        }
        XCTAssertGreaterThan(buzzes, 0, "the runs buzz")
        print("dial: \(steps) steps, \(buzzes) buzzes")
    }

    // MARK: - Helpers

    private func reason(_ name: String) -> CompassLowReason {
        guard let reason = CompassLowReason(rawValue: name) else {
            XCTFail("no reason \(name)")
            return .CALIBRATION
        }
        return reason
    }

    private func turn(_ name: String) -> Turn {
        guard let turn = Turn(rawValue: name) else {
            XCTFail("no turn \(name)")
            return .RIGHT
        }
        return turn
    }

    /// The same number, bit for bit but for a NaN's payload: the rules are line for line.
    private func assertSame(_ answer: Double, _ wanted: Double, _ message: String, file: StaticString = #filePath, line: UInt = #line) {
        if wanted.isNaN {
            XCTAssertTrue(answer.isNaN, "\(message): \(answer), not NaN", file: file, line: line)
        } else {
            XCTAssertEqual(answer.bitPattern, wanted.bitPattern, "\(message): \(answer), not \(wanted)", file: file, line: line)
        }
    }

    /// Within the plan's tolerance, a NaN for a NaN and an infinity for the same infinity.
    private func assertClose(_ answer: Double, _ wanted: Double, _ message: String, file: StaticString = #filePath, line: UInt = #line) {
        if wanted.isNaN {
            XCTAssertTrue(answer.isNaN, "\(message): \(answer), not NaN", file: file, line: line)
        } else if wanted.isInfinite {
            XCTAssertEqual(answer, wanted, message, file: file, line: line)
        } else {
            XCTAssertEqual(answer, wanted, accuracy: tolerance, message, file: file, line: line)
        }
    }
}

// MARK: - compass-vectors.json

/// A number as the file writes it: a JSON number, or "NaN", "Infinity" or "-Infinity" for one that
/// is not finite, which JSON cannot write.
struct VectorNumber: Decodable {
    let value: Double

    init(from decoder: Decoder) throws {
        let single = try decoder.singleValueContainer()
        if let number = try? single.decode(Double.self) {
            value = number
            return
        }
        switch try single.decode(String.self) {
        case "NaN": value = .nan
        case "Infinity": value = .infinity
        case "-Infinity": value = -.infinity
        case let other: throw VectorError("\(other) is no number")
        }
    }
}

struct CompassVectors: Decodable {
    struct DeltaCase: Decodable { let a: VectorNumber; let b: VectorNumber; let delta: VectorNumber }
    struct AlignedCase: Decodable { let heading: VectorNumber; let bearing: VectorNumber; let wasAligned: Bool?; let aligned: Bool }
    struct NorthCase: Decodable { let magnetic: VectorNumber; let declination: VectorNumber?; let heading: VectorNumber }
    struct LownessCase: Decodable { let trueHeading: VectorNumber; let accuracy: VectorNumber; let low: Bool }

    struct FilterRun: Decodable {
        struct Step: Decodable { let `do`: String; let raw: VectorNumber?; let out: VectorNumber? }
        let name: String
        let smoothing: VectorNumber?
        let steps: [Step]
    }

    struct State: Decodable {
        let kind: String
        let heading: VectorNumber?
        let turn: String?
        let degrees: Int?
        let reason: String?

        /// The gate's state this names.
        var gateState: CompassAccuracyState? {
            let reason = self.reason.flatMap(CompassLowReason.init(rawValue:))
            switch (kind, reason) {
            case ("Good", nil): return .good
            case let ("Low", reason?): return .low(reason)
            case let ("BestEffort", reason?): return .bestEffort(reason)
            default: return nil
            }
        }
    }

    struct GateRun: Decodable {
        struct Step: Decodable { let `do`: String; let t: Int64?; let low: Bool?; let reason: String?; let state: State }
        let name: String
        let steps: [Step]
    }

    struct GuidanceCase: Decodable { let delta: VectorNumber; let turn: String; let degrees: Int }

    struct TrackerRun: Decodable {
        struct Told: Decodable { let turn: String; let degrees: Int }
        struct Step: Decodable { let `do`: String; let t: Int64?; let delta: VectorNumber?; let guidance: Told? }
        let name: String
        let steps: [Step]
    }

    struct DialRun: Decodable {
        struct Reading: Decodable { let heading: VectorNumber; let t: Int64; let low: Bool; let reason: String? }
        struct Step: Decodable { let `do`: String; let reading: Reading?; let state: State?; let buzz: Bool }

        let name: String
        let bearing: VectorNumber
        let smoothing: VectorNumber?
        let steps: [Step]
    }

    let alignedEnterDegrees: Double
    let alignedExitDegrees: Double
    let iosMaxHeadingErrorDegrees: Double
    let guidanceStep: Int
    let angleDelta: [DeltaCase]
    let isAligned: [AlignedCase]
    let trueNorth: [NorthCase]
    let correct: [NorthCase]
    let iosSampleIsLow: [LownessCase]
    let filter: [FilterRun]
    let gate: [GateRun]
    let guidanceOf: [GuidanceCase]
    let guidanceTracker: [TrackerRun]
    let dial: [DialRun]

    static func load() -> CompassVectors {
        do {
            return try JSONDecoder().decode(CompassVectors.self, from: Vectors.data("compass-vectors.json"))
        } catch {
            fatalError("compass-vectors.json does not read: \(error)")
        }
    }
}

extension CompassVectors.DialRun.Step {
    private enum CodingKeys: String, CodingKey { case `do`, reading, state, buzz }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        self.do = try json.decode(String.self, forKey: .do)
        reading = try json.decodeIfPresent(CompassVectors.DialRun.Reading.self, forKey: .reading)
        state = try json.decodeIfPresent(CompassVectors.State.self, forKey: .state)
        // A reset has no buzz.
        buzz = try json.decodeIfPresent(Bool.self, forKey: .buzz) ?? false
    }
}
