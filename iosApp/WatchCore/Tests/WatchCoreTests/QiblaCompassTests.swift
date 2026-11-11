import XCTest
@testable import WatchCore

/// The Qibla page's compass over the page's life (plan 4 Task 9, review round 1): when it runs, what
/// the page draws while it is off, what VoiceOver is told, and the heading of the screen's top on a
/// watch worn with the crown on the left. On the Cape Town sample: bearing 23°, declination −27°, so
/// Core Location's magnetic 50 is true 23, facing the Qibla.
final class QiblaCompassTests: XCTestCase {
    private func sample() throws -> WatchFeed { try Vectors.feed("sample") }

    private func tromso() throws -> WatchFeed {
        guard case let .feed(feed) = FeedDecoder.decode(try Vectors.data("phone/phone-tromso-may-en.json")) else {
            throw VectorError("phone-tromso-may-en.json is no feed")
        }
        return feed
    }

    /// [compass] given a sample every 50 ms from [t] for [millis], the screen's top at [magnetic]
    /// as Core Location gives it; its changes.
    @discardableResult
    private func run(_ compass: QiblaCompass, magnetic: Double, accuracy: Double = 10, millis: Int64 = 500, crownOnLeft: Bool = false, from t: inout Int64) -> [QiblaChange?] {
        let count = Int(millis / 50)
        defer { t += Int64(count) * 50 }
        return (0..<count).map { index in
            compass.take(HeadingSample(magnetic: magnetic, accuracy: accuracy, millis: t + Int64(index) * 50), crownOnLeft: crownOnLeft)
        }
    }

    /// The resting dial (`QiblaScreen.model(feed, nil)`): no needle, no marker, no lit rim, dimmed
    /// and unturned, with no letters, the bearing over the distance, said together.
    private func assertResting(_ model: QiblaDialModel, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(model.needle, "no needle", file: file, line: line)
        XCTAssertFalse(model.marker, "no marker", file: file, line: line)
        XCTAssertFalse(model.lit, "no lit rim", file: file, line: line)
        XCTAssertTrue(model.dimmed, "dimmed", file: file, line: line)
        XCTAssertEqual(model.dialRotation, 0, "the dial at rest", file: file, line: line)
        XCTAssertEqual(model.cardinals, [], "no letters", file: file, line: line)
        XCTAssertFalse(model.captionIsAccent, file: file, line: line)
        XCTAssertEqual(model.caption, "23°", file: file, line: line)
        XCTAssertEqual(model.detail, "6,600 km", file: file, line: line)
        XCTAssertEqual(model.spoken, "23°, Makkah · 6,600 km away", "VoiceOver hears no old turn and no 'Facing qibla'", file: file, line: line)
    }

    // MARK: - The compass off: the resting dial (review round 1, Important 1)

    func testStoppingTheCompassDrawsTheRestingDial() throws {
        // A wearer facing the Qibla lowers the wrist (Always On: the scene inactive, the app still
        // frontmost and dimmed) or swipes away: the compass stops, so no heading is known any more,
        // and the page must not keep the lit rim, the needle and "Facing qibla" while the wearer turns.
        let feed = try sample()
        let compass = QiblaCompass(feed: feed, available: true)
        XCTAssertTrue(compass.start())
        var t: Int64 = 0
        run(compass, magnetic: 50, from: &t)
        XCTAssertTrue(compass.model.lit)
        XCTAssertTrue(compass.model.needle)
        XCTAssertEqual(compass.model.spoken, "Facing qibla")

        XCTAssertTrue(compass.stop(), "the source is to be stopped")
        assertResting(compass.model)
        XCTAssertEqual(compass.model, QiblaScreen.model(feed, nil))
    }

    func testStoppingWhileSearchingDrawsTheRestingDialToo() throws {
        let compass = QiblaCompass(feed: try sample(), available: true)
        compass.start()
        var t: Int64 = 0
        run(compass, magnetic: 7, from: &t)
        XCTAssertEqual(compass.model.spoken, "Turn right about 45 degrees")
        compass.stop()
        assertResting(compass.model)
    }

    func testStartingAgainRestsUntilTheFirstHeadingAndJudgesItAfresh() throws {
        let compass = QiblaCompass(feed: try sample(), available: true)
        compass.start()
        var t: Int64 = 0
        run(compass, magnetic: 50, from: &t)
        compass.stop()
        XCTAssertTrue(compass.start(), "the source is to be started again")
        assertResting(compass.model)
        // Back on the page the wearer now faces true 120: the first heading is taken as it is, not
        // smoothed from the heading before the spell away.
        let first = compass.take(HeadingSample(magnetic: 147, accuracy: 10, millis: t), crownOnLeft: false)
        XCTAssertEqual(compass.model.spoken, "Turn left about 90 degrees")
        XCTAssertEqual(compass.model.dialRotation, -120, accuracy: 1e-9)
        XCTAssertEqual(first?.buzz, false)
        // And facing again buzzes again, as the alignment begins anew.
        compass.stop()
        compass.start()
        let facing = compass.take(HeadingSample(magnetic: 50, accuracy: 10, millis: t + 50), crownOnLeft: false)
        XCTAssertEqual(facing?.buzz, true)
    }

    func testASampleAfterTheCompassStoppedChangesNothing() throws {
        // Core Location may hand over one more heading after `stopUpdatingHeading`.
        let compass = QiblaCompass(feed: try sample(), available: true)
        compass.start()
        var t: Int64 = 0
        run(compass, magnetic: 50, from: &t)
        compass.stop()
        XCTAssertNil(compass.take(HeadingSample(magnetic: 50, accuracy: 10, millis: t), crownOnLeft: false))
        assertResting(compass.model)
        XCTAssertFalse(compass.stop(), "stopped already: nothing to stop")
    }

    func testStartingTwiceStartsOnce() throws {
        let compass = QiblaCompass(feed: try sample(), available: true)
        XCTAssertTrue(compass.start())
        var t: Int64 = 0
        run(compass, magnetic: 50, from: &t)
        XCTAssertFalse(compass.start(), "running already: nothing to start, and the dial is kept")
        XCTAssertTrue(compass.model.lit)
    }

    func testAWatchWithNoCompassNeverStartsAndSaysSo() throws {
        let feed = try sample()
        let compass = QiblaCompass(feed: feed, available: false)
        XCTAssertEqual(compass.model, QiblaScreen.model(feed, .noSensor))
        XCTAssertFalse(compass.start())
        XCTAssertFalse(compass.running)
        XCTAssertNil(compass.take(HeadingSample(magnetic: 50, accuracy: 10, millis: 0), crownOnLeft: false))
        XCTAssertFalse(compass.stop())
        XCTAssertEqual(compass.model.caption, "No compass sensor")
    }

    func testANewFeedRestsTheDialAndJudgesByItsBearing() throws {
        let compass = QiblaCompass(feed: try sample(), available: true)
        compass.start()
        var t: Int64 = 0
        run(compass, magnetic: 50, from: &t)
        XCTAssertTrue(compass.model.lit)
        XCTAssertFalse(compass.use(try sample()), "the same feed changes nothing")
        XCTAssertTrue(compass.model.lit)

        // Tromsø: bearing 154°, declination 11°, so magnetic 50 is true 61, the Qibla 93° clockwise.
        let tromso = try tromso()
        XCTAssertTrue(compass.use(tromso))
        XCTAssertTrue(compass.running, "still on the page: the compass keeps running")
        XCTAssertEqual(compass.model, QiblaScreen.model(tromso, nil))
        run(compass, magnetic: 50, from: &t)
        XCTAssertFalse(compass.model.lit)
        XCTAssertEqual(compass.model.spoken, "Turn right about 90 degrees")
        run(compass, magnetic: 143, millis: 2_000, from: &t)
        XCTAssertTrue(compass.model.lit, "magnetic 143 is true 154: facing")
    }

    // MARK: - What VoiceOver is told

    func testOnlyAHeadingThatChangesTheLabelIsAnnounced() throws {
        // A stop, a start and a new feed announce nothing: none of them comes from the compass, and
        // the page may not be on the screen (a feed arriving while Today shows). A heading that
        // changes the label is announced; one that keeps it is not.
        let compass = QiblaCompass(feed: try sample(), available: true)
        compass.start()
        var t: Int64 = 0
        let first = run(compass, magnetic: 7, from: &t)
        XCTAssertEqual(first[0]?.announcement, "Turn right about 45 degrees")
        XCTAssertTrue(first.dropFirst().allSatisfy { $0?.announcement == nil }, "the same turn is not told again")
        let facing = run(compass, magnetic: 50, millis: 3_000, from: &t)
        XCTAssertEqual(facing.compactMap { $0?.announcement }.last, "Facing qibla")
        XCTAssertEqual(facing.compactMap { $0?.announcement }.filter { $0 == "Facing qibla" }.count, 1)

        compass.stop()
        compass.start()
        // Back on the page, the state is told afresh: what the wearer heard before the spell away
        // may no longer hold.
        XCTAssertEqual(compass.take(HeadingSample(magnetic: 50, accuracy: 10, millis: t), crownOnLeft: false)?.announcement, "Facing qibla")
    }

    // MARK: - The crown on the left (review round 1, Important 2)

    func testACrownOnTheLeftTurnsTheHeadingHalfRound() {
        // Core Location's heading is the watch's own top "regardless of device orientation as well as
        // the orientation of the user interface" (CLHeading.h); a watch set for the crown on the left
        // draws its screen half round, so the screen's top is the watch's foot.
        for (device, screen) in [(0.0, 180.0), (50, 230), (180, 0), (230, 50), (359.5, 179.5), (90, 270)] {
            let sample = HeadingSample(magnetic: device, accuracy: 12, millis: 7).onScreen(crownOnLeft: true)
            XCTAssertEqual(sample.magnetic, screen, accuracy: 1e-9, "Core Location's \(device)")
            XCTAssertEqual(sample.accuracy, 12)
            XCTAssertEqual(sample.millis, 7)
        }
    }

    func testACrownOnTheRightKeepsCoreLocationsHeading() {
        for device in [0.0, 50, 180, 359.5, -1] {
            XCTAssertEqual(HeadingSample(magnetic: device, accuracy: 12, millis: 7).onScreen(crownOnLeft: false).magnetic, device)
        }
    }

    func testACrownOnTheLeftKeepsCoreLocationsSentinel() throws {
        // A negative heading is Core Location's "could not determine", never a direction to turn.
        XCTAssertEqual(HeadingSample(magnetic: -1, accuracy: 10, millis: 0).onScreen(crownOnLeft: true).magnetic, -1)
        XCTAssertTrue(HeadingSample(magnetic: .nan, accuracy: 10, millis: 0).onScreen(crownOnLeft: true).magnetic.isNaN)
        let compass = QiblaCompass(feed: try sample(), available: true)
        compass.start()
        var t: Int64 = 0
        let blind = run(compass, magnetic: -1, millis: 2_000, crownOnLeft: true, from: &t)
        XCTAssertFalse(blind.contains { $0?.buzz == true })
        XCTAssertFalse(compass.model.needle)
        XCTAssertEqual(compass.model.caption, "Compass needs calibrating")
    }

    func testACrownOnTheLeftFacesTheQiblaByTheScreensTop() throws {
        // The wearer faces true 23 with the screen upright: the watch's own top points behind them,
        // so Core Location says 230 (screen 50, true 23), and the dial must say "Facing qibla".
        let compass = QiblaCompass(feed: try sample(), available: true)
        compass.start()
        var t: Int64 = 0
        let facing = run(compass, magnetic: 230, crownOnLeft: true, from: &t)
        XCTAssertEqual(facing.first??.buzz, true)
        XCTAssertTrue(compass.model.lit)
        XCTAssertEqual(compass.model.spoken, "Facing qibla")
        XCTAssertEqual(compass.model.dialRotation, -23, accuracy: 1e-9)

        // Core Location's 50 on such a watch is the screen facing true 203: the Qibla behind.
        compass.stop()
        compass.start()
        run(compass, magnetic: 50, crownOnLeft: true, from: &t)
        XCTAssertFalse(compass.model.lit)
        XCTAssertEqual(compass.model.dialRotation, -203, accuracy: 1e-9)
        XCTAssertEqual(compass.model.spoken, "Turn right about 180 degrees")
    }
}
