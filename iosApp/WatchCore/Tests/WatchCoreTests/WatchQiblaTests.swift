import XCTest
@testable import WatchCore

/// The Qibla dial on the watch's own compass (plan 4 Task 9): Core Location's magnetic heading made
/// true by the feed's declination, as the iPhone's `IosCompassSource` makes it, judged by the
/// phone's gate and filter (`WatchQibla`) and drawn by `QiblaScreen`, through `QiblaTracker`. On the
/// Cape Town sample: bearing 23°, declination −27° (the phone's whole degree of the World Magnetic
/// Model; −25 when the plan was written), so magnetic 50 is true 23, facing the Qibla.
///
/// Review Focus 5: a negative magnetic heading, a negative accuracy, no declination, no compass at
/// all, a large declination, and 359° to 1°. No needle points at nothing, the needle points by true
/// north, the filter never swings through 180°, and a searching wearer is told which way to turn.
final class WatchQiblaTests: XCTestCase {
    /// A sample every 50 ms, from [start], for [millis].
    private func samples(magnetic: Double, accuracy: Double = 10, millis: Int64 = 50, from start: inout Int64) -> [HeadingSample] {
        let count = Int(millis / 50)
        defer { start += Int64(count) * 50 }
        return (0..<count).map { HeadingSample(magnetic: magnetic, accuracy: accuracy, millis: start + Int64($0) * 50) }
    }

    private func steps(_ tracker: QiblaTracker, magnetic: Double, accuracy: Double = 10, millis: Int64 = 50, from start: inout Int64) -> [QiblaStep] {
        samples(magnetic: magnetic, accuracy: accuracy, millis: millis, from: &start).map(tracker.sample)
    }

    func testTheSampleIsCapeTownAsTheUITestsTakeIt() throws {
        // QiblaTests.swift's -taqwaHeading figures are magnetic, worked out from these: facing is
        // 50, true 340 is 7, true 120 is 147. A sample that changes these changes them.
        let sample = try Vectors.feed("sample")
        XCTAssertEqual(sample.place.qiblaBearing, 23.0)
        XCTAssertEqual(sample.place.declination, -27.0)
        XCTAssertEqual(sample.place.qiblaKm, "6,600")
    }

    func testANegativeHeadingNeverDrawsANeedle() throws {
        // Core Location's "could not determine": the reading is low with a heading of 0, as the
        // iPhone's source sends it, never 359°.
        let reading = HeadingReading.reading(HeadingSample(magnetic: -1, accuracy: 10, millis: 0), declination: -27)
        XCTAssertEqual(reading.trueHeadingDegrees, 0)
        XCTAssertTrue(reading.isLowAccuracy)
        XCTAssertEqual(reading.lowReason, .CALIBRATION)

        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        // Two seconds of nothing but the sentinel: the gate's first second trusts it, and still no
        // needle; then Low.
        let blind = steps(tracker, magnetic: -1, millis: 2_000, from: &t)
        for (index, step) in blind.enumerated() {
            XCTAssertFalse(step.model.needle, "step \(index)")
            XCTAssertFalse(step.model.marker, "step \(index)")
            XCTAssertFalse(step.buzz, "step \(index)")
        }
        XCTAssertEqual(blind.first?.model.caption, "23°", "searching, with no heading to draw")
        XCTAssertEqual(blind.last?.model.caption, "Compass needs calibrating")

        // Facing the Qibla, then one sentinel among good headings: that one draws no needle.
        let fresh = QiblaTracker(feed: try Vectors.feed("sample"))
        let facing = steps(fresh, magnetic: 50, millis: 500, from: &t)
        XCTAssertTrue(facing.allSatisfy(\.model.needle))
        let sentinel = fresh.sample(HeadingSample(magnetic: -1, accuracy: 10, millis: t))
        XCTAssertFalse(sentinel.model.needle)
        XCTAssertFalse(sentinel.model.marker)
        XCTAssertFalse(sentinel.model.lit)
        XCTAssertFalse(sentinel.buzz)
        XCTAssertEqual(sentinel.model.dialRotation, 0, "the dial at rest")
    }

    func testANegativeAccuracyIsLowAfterTheGatesSecond() throws {
        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        let steps = steps(tracker, magnetic: 50, accuracy: -1, millis: 1_050, from: &t)
        XCTAssertEqual(steps.last?.model.caption, "Compass needs calibrating")
        XCTAssertFalse(steps.last?.model.needle ?? true)
    }

    func testNoDeclinationNeverDrawsANeedle() {
        // No declination: never magnetic north passed off as true north.
        let sample = HeadingSample(magnetic: 50, accuracy: 10, millis: 0)
        let reading = HeadingReading.reading(sample, declination: nil)
        XCTAssertEqual(reading.trueHeadingDegrees, 0, "not 50")
        XCTAssertTrue(reading.isLowAccuracy)
        XCTAssertFalse(HeadingReading.hasDirection(sample, declination: nil))
        XCTAssertTrue(HeadingReading.hasDirection(sample, declination: 0))

        let dial = WatchQibla(bearing: 23)
        for index in 0..<40 {
            let sample = HeadingSample(magnetic: 23, accuracy: 10, millis: Int64(index) * 50)
            let step = QiblaScreen.drawn(dial.onReading(HeadingReading.reading(sample, declination: nil)), hasDirection: HeadingReading.hasDirection(sample, declination: nil))
            if case .aligned = step.state { XCTFail("step \(index) is aligned with no declination") }
            XCTAssertFalse(step.buzz, "step \(index)")
            if case let .searching(heading, guidance) = step.state {
                XCTAssertTrue(heading.isNaN, "step \(index) draws no heading")
                XCTAssertNil(guidance, "step \(index) tells no turn")
            }
        }
    }

    func testTheSeamDoesNotSwingTheNeedle() throws {
        // True 359 then a steady 1 (magnetic 26 then 28 under −27): the needle moves the two degrees
        // the short way, never the long way round through 180.
        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        let steps = steps(tracker, magnetic: 26, from: &t) + steps(tracker, magnetic: 28, millis: 2_000, from: &t)
        for (index, step) in steps.enumerated() {
            let heading = -step.model.dialRotation
            XCTAssertLessThanOrEqual(abs(QiblaAngles.angleDelta(heading, 0)), 1.0 + 1e-9, "step \(index): \(heading)")
        }
        XCTAssertEqual(-steps.last!.model.dialRotation, 1, accuracy: 0.01, "settled on true 1")
    }

    func testFacingWithinFiveDegrees() throws {
        let sample = try Vectors.feed("sample")
        func first(_ magnetic: Double) -> QiblaStep {
            QiblaTracker(feed: sample).sample(HeadingSample(magnetic: magnetic, accuracy: 10, millis: 0))
        }
        // True 18 and 28 are five degrees either side of 23; 17.9 and 28.1 are past it.
        for magnetic in [45.0, 55.0] {
            XCTAssertTrue(first(magnetic).model.lit, "magnetic \(magnetic)")
            XCTAssertEqual(first(magnetic).model.caption, "Facing qibla")
        }
        for magnetic in [44.9, 55.1] {
            XCTAssertFalse(first(magnetic).model.lit, "magnetic \(magnetic)")
            XCTAssertEqual(first(magnetic).model.caption, "23°")
        }
    }

    func testTheDeclinationTurnsMagneticIntoTrue() throws {
        let sample = try Vectors.feed("sample")
        var t: Int64 = 0
        // Magnetic 50 is true 23: facing the Qibla, said in the accent.
        let facing = steps(QiblaTracker(feed: sample), magnetic: 50, millis: 2_000, from: &t).last!
        XCTAssertTrue(facing.model.lit)
        XCTAssertTrue(facing.model.needle)
        XCTAssertEqual(facing.model.caption, "Facing qibla")
        XCTAssertTrue(facing.model.captionIsAccent)
        XCTAssertEqual(facing.model.dialRotation, -23, accuracy: 1e-9, "the dial turns by true north's heading")
        // Magnetic 25 is true 358, 25° off: the needle would be lit if the declination were dropped.
        let off = steps(QiblaTracker(feed: sample), magnetic: 25, millis: 2_000, from: &t).last!
        XCTAssertFalse(off.model.lit)
        XCTAssertEqual(off.model.caption, "23°")
        XCTAssertEqual(off.model.spoken, "Turn right about 30 degrees")
        XCTAssertEqual(off.model.dialRotation, -358, accuracy: 1e-9)
    }

    func testSearchingSaysWhichWayToTurn() throws {
        let sample = try Vectors.feed("sample")
        var t: Int64 = 0
        // Magnetic 7 is true 340, 43° anticlockwise of the bearing: turn right.
        let west = steps(QiblaTracker(feed: sample), magnetic: 7, millis: 2_000, from: &t).last!
        XCTAssertEqual(west.model.caption, "23°")
        XCTAssertEqual(west.model.detail, "6,600 km")
        XCTAssertEqual(west.model.spoken, "Turn right about 45 degrees")
        XCTAssertEqual(west.model.spokenDetail, "Makkah · 6,600 km away")
        XCTAssertTrue(west.model.needle)
        XCTAssertTrue(west.model.marker)
        // Magnetic 147 is true 120, 97° clockwise of it: turn left.
        let east = steps(QiblaTracker(feed: sample), magnetic: 147, millis: 2_000, from: &t).last!
        XCTAssertEqual(east.model.spoken, "Turn left about 90 degrees")
    }

    func testTheBuzzComesOnceOnEnteringAlignment() throws {
        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        let facing = steps(tracker, magnetic: 50, millis: 2_000, from: &t)
        XCTAssertEqual(facing.map(\.buzz).filter { $0 }.count, 1)
        XCTAssertTrue(facing.first!.buzz, "as the alignment begins")
        let away = steps(tracker, magnetic: 110, millis: 2_000, from: &t)
        XCTAssertFalse(away.contains { $0.buzz })
        let back = steps(tracker, magnetic: 50, millis: 2_000, from: &t)
        XCTAssertEqual(back.map(\.buzz).filter { $0 }.count, 1, "once again on coming back")
    }

    func testALowCompassDimsAndDropsTheMarker() throws {
        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        let low = steps(tracker, magnetic: 50, accuracy: 60, millis: 1_050, from: &t).last!.model
        XCTAssertEqual(low.caption, "Compass needs calibrating")
        XCTAssertEqual(low.spoken, "Compass needs calibrating")
        XCTAssertTrue(low.dimmed)
        XCTAssertFalse(low.needle)
        XCTAssertFalse(low.marker)
        XCTAssertFalse(low.lit)
        XCTAssertEqual(low.detail, "", "no readout, as the phone's low state")
    }

    func testTwentySecondsLowIsBestEffort() throws {
        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        let steps = steps(tracker, magnetic: 50, accuracy: 60, millis: 20_050, from: &t)
        let model = steps.last!.model
        XCTAssertEqual(steps[steps.count - 2].model.caption, "Compass needs calibrating")
        XCTAssertEqual(model.caption, "Compass unavailable")
        XCTAssertEqual(model.detail, "23° · 6,600 km")
        XCTAssertEqual(model.spoken, "Compass unavailable")
        XCTAssertEqual(model.spokenDetail, "23° · 6,600 km to Makkah")
        XCTAssertTrue(model.dimmed)
        XCTAssertFalse(model.needle)
    }

    func testAHeadingThatIsNotANumberDrawsNoNeedleUntilReset() throws {
        // Ruling 58 item 1: the phone's filter keeps a NaN until it is reset; the page draws no
        // needle and tells no turn meanwhile, and keeps the Searching caption.
        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        XCTAssertTrue(steps(tracker, magnetic: 50, from: &t)[0].model.lit)
        let lost = steps(tracker, magnetic: .nan, from: &t) + steps(tracker, magnetic: 50, millis: 500, from: &t)
        for step in lost {
            XCTAssertFalse(step.model.needle)
            XCTAssertFalse(step.model.dimmed)
            XCTAssertEqual(step.model.caption, "23°")
            XCTAssertEqual(step.model.spoken, "23°")
        }
        tracker.reset()
        let again = steps(tracker, magnetic: 50, from: &t)[0]
        XCTAssertTrue(again.model.lit)
        XCTAssertTrue(again.buzz)
    }

    func testResetForgetsTheHeadingAndTheAlignment() throws {
        let tracker = QiblaTracker(feed: try Vectors.feed("sample"))
        var t: Int64 = 0
        _ = steps(tracker, magnetic: 50, millis: 500, from: &t)
        tracker.reset()
        // The first reading after a reset passes the filter as it is, and buzzes again.
        let next = tracker.sample(HeadingSample(magnetic: 147, accuracy: 10, millis: t))
        XCTAssertEqual(next.model.dialRotation, -120, accuracy: 1e-9)
        XCTAssertEqual(next.model.spoken, "Turn left about 90 degrees")
        let back = tracker.sample(HeadingSample(magnetic: 50, accuracy: 10, millis: t + 50))
        XCTAssertFalse(back.model.lit, "the filter moves 15 % of the way")
        tracker.reset()
        XCTAssertTrue(tracker.sample(HeadingSample(magnetic: 50, accuracy: 10, millis: t + 100)).buzz)
    }
}
