import XCTest
@testable import WatchCore

/// What the Qibla page draws and says (plan 4 Task 9, `faces.js`'s `aQibla` and `qiblaDial`), a
/// pure function of the dial's state and the feed: plan 3's `qiblaScreen` (`wearApp`'s
/// `QiblaModelTest`, restated) without the tilt, since the Apple Watch reads no Core Motion and so
/// never says "Hold your wrist level". On the committed sample: Cape Town, whose Qibla the phone
/// rounded to 23° and whose distance it wrote "6,600" (Ruling 39: the city, the bearing to a whole
/// degree and the phone's own figure, never a coordinate).
final class QiblaScreenTests: XCTestCase {
    private func sample() throws -> WatchFeed { try Vectors.feed("sample") }

    private func searching(_ heading: Double, _ turn: Turn? = nil, _ degrees: Int = 45) -> DialState {
        .searching(heading: heading, guidance: turn.map { Guidance(turn: $0, degrees: degrees) })
    }

    /// No needle, no marker, the dial dimmed and at rest, with no letters: every state with no
    /// heading to show but a searching one whose heading is not a number.
    private func assertNotPointing(_ model: QiblaDialModel, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(model.needle, "no needle", file: file, line: line)
        XCTAssertFalse(model.marker, "no marker", file: file, line: line)
        XCTAssertTrue(model.dimmed, "dimmed", file: file, line: line)
        XCTAssertFalse(model.lit, file: file, line: line)
        XCTAssertEqual(model.dialRotation, 0, "the dial at rest", file: file, line: line)
        XCTAssertEqual(model.cardinals, [], "no letters: at rest they would name a north the watch does not know", file: file, line: line)
        XCTAssertFalse(model.captionIsAccent, file: file, line: line)
    }

    func testSearchingShowsTheBearingAndTheDistanceAndSaysTheTurn() throws {
        let model = QiblaScreen.model(try sample(), searching(340, .RIGHT))
        XCTAssertTrue(model.needle)
        XCTAssertTrue(model.marker)
        XCTAssertFalse(model.dimmed)
        XCTAssertFalse(model.lit)
        XCTAssertEqual(model.dialRotation, -340)
        XCTAssertEqual(model.caption, "23°")
        XCTAssertFalse(model.captionIsAccent)
        XCTAssertEqual(model.detail, "6,600 km", "the dial's short form (Ruling 66)")
        XCTAssertEqual(model.spoken, "Turn right about 45 degrees")
        XCTAssertEqual(model.spokenDetail, "Makkah · 6,600 km away")
        XCTAssertEqual(model.cardinals, ["N", "E", "S", "W"])
        XCTAssertEqual(model.language, "en")
        XCTAssertTrue(model.latinScript)
    }

    func testSearchingToTheLeftSaysTheLeftTurn() throws {
        let model = QiblaScreen.model(try sample(), searching(60, .LEFT, 30))
        XCTAssertEqual(model.spoken, "Turn left about 30 degrees")
        XCTAssertEqual(model.dialRotation, -60)
    }

    func testSearchingWithNoTurnYetSaysTheBearing() throws {
        let model = QiblaScreen.model(try sample(), searching(340))
        XCTAssertTrue(model.needle)
        XCTAssertEqual(model.caption, "23°")
        XCTAssertEqual(model.spoken, "23°", "the caption, when the guidance is nil")
    }

    func testFacingTheQiblaLightsTheRimAndSaysSo() throws {
        let model = QiblaScreen.model(try sample(), .aligned(heading: 24))
        XCTAssertTrue(model.lit)
        XCTAssertTrue(model.needle)
        XCTAssertTrue(model.marker)
        XCTAssertFalse(model.dimmed)
        XCTAssertEqual(model.dialRotation, -24)
        XCTAssertEqual(model.cardinals, ["N", "E", "S", "W"])
        XCTAssertEqual(model.caption, "Facing qibla")
        XCTAssertTrue(model.captionIsAccent)
        XCTAssertEqual(model.spoken, "Facing qibla")
        XCTAssertEqual(model.detail, "6,600 km", "the dial's short form (Ruling 66)")
        XCTAssertEqual(model.spokenDetail, "Makkah · 6,600 km away", "the long phrase is heard")
    }

    func testACompassThatNeedsCalibratingDimsTheDialAndDropsTheNeedle() throws {
        let model = QiblaScreen.model(try sample(), .low(.CALIBRATION))
        assertNotPointing(model)
        XCTAssertEqual(model.caption, "Compass needs calibrating")
        XCTAssertEqual(model.spoken, "Compass needs calibrating")
        XCTAssertEqual(model.detail, "", "the phone's low state shows no readout")
        XCTAssertEqual(model.spokenDetail, "")
    }

    func testInterferenceSaysSo() throws {
        let model = QiblaScreen.model(try sample(), .low(.INTERFERENCE))
        assertNotPointing(model)
        XCTAssertEqual(model.caption, "Magnetic interference")
        XCTAssertEqual(model.spoken, "Magnetic interference")
    }

    func testBestEffortShowsTheBearingAndTheDistanceWithNoNeedle() throws {
        let model = QiblaScreen.model(try sample(), .bestEffort(.INTERFERENCE))
        assertNotPointing(model)
        XCTAssertEqual(model.caption, "Compass unavailable")
        XCTAssertEqual(model.spoken, "Compass unavailable")
        XCTAssertEqual(model.detail, "23° · 6,600 km", "the dial's short form (Ruling 66)")
        XCTAssertEqual(model.spokenDetail, "23° · 6,600 km to Makkah", "the long phrase is heard")
    }

    func testNoCompassShowsTheBearingAndDistanceOnly() throws {
        let model = QiblaScreen.model(try sample(), .noSensor)
        assertNotPointing(model)
        XCTAssertEqual(model.caption, "No compass sensor")
        XCTAssertEqual(model.spoken, "No compass sensor")
        XCTAssertEqual(model.detail, "23° · 6,600 km", "the dial's short form (Ruling 66)")
        XCTAssertEqual(model.spokenDetail, "23° · 6,600 km to Makkah", "the long phrase is heard")
    }

    func testBeforeTheFirstHeadingNoNeedleIsDrawn() throws {
        let model = QiblaScreen.model(try sample(), nil)
        assertNotPointing(model)
        XCTAssertEqual(model.caption, "23°")
        XCTAssertEqual(model.detail, "6,600 km")
        XCTAssertEqual(model.spoken, "23°, Makkah · 6,600 km away", "the long phrase, never the short form (Ruling 66 item 8)")
        XCTAssertEqual(model.spokenDetail, "", "the label already says the distance: VoiceOver does not say it twice")
    }

    func testAHeadingThatIsNotANumberDrawsNoNeedleAndSaysNoTurn() throws {
        // Ruling 58 item 1: a trusted heading that is not a number keeps the phone's behaviour,
        // Searching with no turn until reset. The page draws neither needle nor marker nor letters,
        // tells no turn, keeps the Searching caption, and is not dimmed: the compass is searching.
        let dial = WatchQibla(bearing: 23)
        _ = dial.onReading(CompassReading(trueHeadingDegrees: 340, timestampMillis: 0, isLowAccuracy: false, lowReason: nil))
        let step = dial.onReading(CompassReading(trueHeadingDegrees: .nan, timestampMillis: 50, isLowAccuracy: false, lowReason: nil))
        guard case let .searching(heading, guidance) = step.state else { return XCTFail("still Searching: \(step.state)") }
        XCTAssertTrue(heading.isNaN)
        XCTAssertNil(guidance)

        let model = QiblaScreen.model(try sample(), step.state)
        XCTAssertFalse(model.needle)
        XCTAssertFalse(model.marker)
        XCTAssertFalse(model.dimmed)
        XCTAssertFalse(model.lit)
        XCTAssertEqual(model.dialRotation, 0)
        XCTAssertEqual(model.cardinals, [])
        XCTAssertEqual(model.caption, "23°")
        XCTAssertEqual(model.spoken, "23°")
        XCTAssertEqual(model.detail, "6,600 km")
    }

    func testAnInfiniteHeadingIsNoHeadingEvenWithATurn() throws {
        let model = QiblaScreen.model(try sample(), searching(.infinity, .RIGHT))
        XCTAssertFalse(model.needle)
        XCTAssertFalse(model.marker)
        XCTAssertEqual(model.spoken, "23°", "no turn is told without a heading")
        XCTAssertEqual(model.dialRotation, 0)
        XCTAssertEqual(model.cardinals, [])
    }

    func testASampleWithNoDirectionIsDrawnAsNoHeading() {
        // A reading the gate still trusts in its first second, from Core Location's sentinel: drawn
        // as the searching dial with no heading, and never buzzed, whatever the dial made of it.
        for state in [DialState.aligned(heading: 23), searching(0, .RIGHT)] {
            let drawn = QiblaScreen.drawn(DialStep(state: state, buzz: true), hasDirection: false)
            XCTAssertFalse(drawn.buzz)
            guard case let .searching(heading, guidance) = drawn.state else { return XCTFail("\(drawn.state)") }
            XCTAssertTrue(heading.isNaN)
            XCTAssertNil(guidance)
        }
        // Low and BestEffort draw no needle already, and keep their words.
        for state in [DialState.low(.CALIBRATION), .bestEffort(.INTERFERENCE)] {
            XCTAssertEqual(QiblaScreen.drawn(DialStep(state: state, buzz: false), hasDirection: false).state, state)
        }
        // A sample with a direction is drawn as the dial answered it.
        let facing = DialStep(state: .aligned(heading: 23), buzz: true)
        XCTAssertEqual(QiblaScreen.drawn(facing, hasDirection: true), facing)
    }

    func testTheDistanceIsTheFeedsOwnFigure() throws {
        // The figure plan 2's Task 3 rounds under Ruling 39, as the feed writes it (the phone's own
        // 6,558 is never sent): the watch never rounds or regroups it.
        XCTAssertEqual(QiblaScreen.model(try sample(), .noSensor).detail, "23° · 6,600 km")
        let saudi = QiblaScreen.model(try Vectors.feed("sampleArSa"), nil)
        XCTAssertTrue(saudi.detail.contains("٦٬٦٠٠"), saudi.detail)
        for golden in try Vectors.files(in: "phone", endingIn: ".json") {
            guard case let .feed(feed) = FeedDecoder.decode(try Vectors.data("phone/\(golden)")) else { return XCTFail(golden) }
            let model = QiblaScreen.model(feed, .aligned(heading: feed.place.qiblaBearing))
            XCTAssertTrue(model.detail.contains(feed.place.qiblaKm), "\(golden): \(model.detail)")
            XCTAssertTrue(model.spokenDetail.contains(feed.place.qiblaKm), golden)
        }
        let ungrouped = try XCTUnwrap(String(data: try Vectors.data("sample-feed-capetown.json"), encoding: .utf8))
            .replacingOccurrences(of: "\"qiblaKm\":\"6,600\"", with: "\"qiblaKm\":\"6600\"")
        guard case let .feed(feed) = FeedDecoder.decode(Data(ungrouped.utf8)) else { return XCTFail("the ungrouped sample") }
        XCTAssertEqual(QiblaScreen.model(feed, nil).detail, "6600 km", "the feed's figure as written")
        XCTAssertEqual(QiblaScreen.model(feed, .noSensor).spokenDetail, "23° · 6600 km to Makkah")
    }

    func testTheDialDrawsTheShortFormAndSaysTheLongOne() throws {
        // Ruling 66 items 1 and 8: the turning dial draws distanceOnDial and the dimmed one
        // qiblaDetailOnDial, the figure and the unit alone; VoiceOver hears the phone's long phrase,
        // distance and qiblaDetail, in every sample and golden, whatever the phone's language.
        var feeds = try ["sample", "sampleEn12", "sampleArLy12", "sampleArSa"].map { try Vectors.feed($0) }
        for golden in try Vectors.files(in: "phone", endingIn: ".json") {
            guard case let .feed(feed) = FeedDecoder.decode(try Vectors.data("phone/\(golden)")) else { return XCTFail(golden) }
            feeds.append(feed)
        }
        for feed in feeds {
            let words = feed.words
            let degrees = WatchRules.digits(feed, QiblaScreen.wholeDegree(feed.place.qiblaBearing))
            let km = feed.place.qiblaKm
            let short = WatchRules.filled(words.distanceOnDial, km)
            let shortBoth = WatchRules.filled(words.qiblaDetailOnDial, degrees, km)
            let long = WatchRules.filled(words.distance, km)
            let longBoth = WatchRules.filled(words.qiblaDetail, degrees, km)
            for state in [DialState.searching(heading: 1, guidance: Guidance(turn: .RIGHT, degrees: 15)), .aligned(heading: feed.place.qiblaBearing)] {
                let model = QiblaScreen.model(feed, state)
                XCTAssertEqual(model.detail, short, "\(feed.ui.language) \(state)")
                XCTAssertEqual(model.spokenDetail, long, "\(feed.ui.language) \(state)")
            }
            XCTAssertEqual(QiblaScreen.model(feed, nil).detail, short, feed.ui.language)
            XCTAssertTrue(QiblaScreen.model(feed, nil).spoken.hasSuffix(long), feed.ui.language)
            for state in [DialState.bestEffort(.CALIBRATION), .noSensor] {
                let model = QiblaScreen.model(feed, state)
                XCTAssertEqual(model.detail, shortBoth, "\(feed.ui.language) \(state)")
                XCTAssertEqual(model.spokenDetail, longBoth, "\(feed.ui.language) \(state)")
            }
            XCTAssertEqual(QiblaScreen.model(feed, .low(.INTERFERENCE)).detail, "")
        }
        XCTAssertEqual(feeds.count, 4 + 5, "the four samples and the five goldens")
    }

    func testTheBearingIsThePhonesWholeDegree() {
        // Kotlin's `qiblaBearing.toInt()`: toward zero, so 23.9 is 23 and 359.99 is 359, as the
        // Galaxy writes it; and no number a text can hold traps (NaN is 0, a huge one Int32's end).
        XCTAssertEqual(QiblaScreen.wholeDegree(23.9), 23)
        XCTAssertEqual(QiblaScreen.wholeDegree(359.99), 359)
        XCTAssertEqual(QiblaScreen.wholeDegree(-0.5), 0)
        XCTAssertEqual(QiblaScreen.wholeDegree(.nan), 0)
        XCTAssertEqual(QiblaScreen.wholeDegree(1e300), Int64(Int32.max))
        XCTAssertEqual(QiblaScreen.wholeDegree(-.infinity), Int64(Int32.min))
    }

    func testArabicCaptionsAndAnUnmirroredDial() throws {
        let arSa = try Vectors.feed("sampleArSa")
        let arabic = QiblaScreen.model(arSa, searching(340, .RIGHT))
        let english = QiblaScreen.model(try sample(), searching(340, .RIGHT))
        XCTAssertEqual(arabic.caption, "٢٣°")
        XCTAssertEqual(arabic.cardinals, arSa.words.cardinals)
        XCTAssertEqual(arabic.cardinals, ["ش", "ق", "ج", "غ"])
        XCTAssertEqual(arabic.dialRotation, english.dialRotation, "a compass never mirrors")
        XCTAssertEqual(arabic.spoken, "استدر يمينًا حوالي ٤٥ درجة")
        XCTAssertEqual(arabic.detail, "٦٬٦٠٠ كم")
        XCTAssertEqual(arabic.spokenDetail, "مكة · على بُعد ٦٬٦٠٠ كم")
        XCTAssertEqual(arabic.language, "ar-SA")
        XCTAssertFalse(arabic.latinScript)
        // A right-to-left phone's comma joins the bearing and the distance before the first reading.
        let resting = QiblaScreen.model(arSa, nil)
        XCTAssertEqual(resting.spoken, "٢٣°، مكة · على بُعد ٦٬٦٠٠ كم")
    }

    func testTheMarkerStandsAtTheBearingOnTheTurnedDial() throws {
        // The needle and the Kaaba's marker at bearing − heading: the feed's bearing with the dial's
        // turn, 23 − 340 on the searching dial, the same in every language.
        let model = QiblaScreen.model(try sample(), searching(340, .RIGHT))
        XCTAssertEqual(model.bearing, 23)
        XCTAssertEqual(model.bearing + model.dialRotation, 23 - 340)
        XCTAssertEqual(QiblaScreen.model(try Vectors.feed("sampleArSa"), searching(340, .RIGHT)).bearing, 23)
    }

    func testATitleTooWideForOneLineBreaksAtTheSpaceNearestItsMiddle() {
        // The two lines as near the same length as a space allows, so neither runs wider than it must.
        XCTAssertEqual(QiblaCaption.split("Compass needs calibrating")?.first, "Compass needs")
        XCTAssertEqual(QiblaCaption.split("Compass needs calibrating")?.last, "calibrating")
        XCTAssertEqual(QiblaCaption.split("Magnetic interference")?.first, "Magnetic")
        XCTAssertEqual(QiblaCaption.split("Magnetic interference")?.last, "interference")
        XCTAssertEqual(QiblaCaption.split("No compass sensor")?.first, "No compass")
        XCTAssertEqual(QiblaCaption.split("No compass sensor")?.last, "sensor")
        XCTAssertEqual(QiblaCaption.split("البوصلة غير متاحة")?.first, "البوصلة")
        XCTAssertEqual(QiblaCaption.split("البوصلة غير متاحة")?.last, "غير متاحة")
        // One word has no second line; its one line shrinks instead.
        XCTAssertNil(QiblaCaption.split("23°"))
        XCTAssertNil(QiblaCaption.split("Kompassunavailable"))
        // The two lines stand 16/15 of their size apart, as the Galaxy's titles do.
        XCTAssertEqual(QiblaCaption.pitch, 16.0 / 15)
    }

    // MARK: - Where it is drawn

    func testTheDrawingIsScaledToFitTheScreenWhole() {
        // aQibla's 172 × 212 units, scaled to fit both ways and centred across: the SE 3 (40mm,
        // 162 × 197 points) and the Series 12 (46mm, 208 × 248) are both shorter for their width.
        let se3 = QiblaLayout(width: 162, height: 197)
        XCTAssertEqual(se3.k, 197.0 / 212, accuracy: 1e-12)
        XCTAssertEqual(se3.x(86), 81, accuracy: 1e-9, "the dial's middle in the screen's middle")
        XCTAssertEqual(se3.y(212), 197, accuracy: 1e-9, "the drawing's foot at the screen's")
        let series12 = QiblaLayout(width: 208, height: 248)
        XCTAssertEqual(series12.k, 248.0 / 212, accuracy: 1e-12)
        XCTAssertEqual(series12.x(86), 104, accuracy: 1e-9)
        // A screen wider for its height than the drawing is scaled by its width.
        XCTAssertEqual(QiblaLayout(width: 172, height: 300).k, 1, accuracy: 1e-12)
    }

    func testTheReadoutStandsClearOfThePageDots() {
        // The page dots' tops, measured in plan 4 Task 8's fix round 4: y 193 on the SE 3 and 240
        // on the Series 12. The detail's baseline at the drawing's 196, with its descent at the
        // largest size it grows to (13 units, 0.25 of it below the line), ends at least 3 points
        // above them.
        for (layout, dots) in [(QiblaLayout(width: 162, height: 197), 193.0), (QiblaLayout(width: 208, height: 248), 240.0)] {
            let foot = layout.y(QiblaLayout.detailBaseline + 0.25 * 10 * QiblaType.detail)
            XCTAssertLessThanOrEqual(foot, dots - 3, "the detail ends at \(foot), the dots start at \(dots)")
        }
        // The caption over it, and both under the dial's rim.
        XCTAssertEqual(QiblaLayout.captionBaseline, 180)
        XCTAssertEqual(QiblaLayout.detailBaseline, 196)
        XCTAssertLessThan(QiblaLayout.dialCentre.y + QiblaLayout.dialRadius, QiblaLayout.captionBaseline - 14 * 0.81 * QiblaType.caption)
    }

    func testTheLinesGrowWithTheTextSizeToTheGalaxysCaps() {
        // Plan 3's call 15, as Tasbeeh's lines: the caption to 1.2, the detail and the dial's
        // letters to 1.3 (Ruling 66 item 8: the detail from 10, never below), from the watch's own
        // default size, at which nothing drawn changes.
        XCTAssertEqual(QiblaType.caption, 1.2)
        XCTAssertEqual(QiblaType.detail, 1.3)
        XCTAssertEqual(QiblaType.letters, 1.3)
        XCTAssertEqual(TasbeehType.grow(0.89, cap: QiblaType.detail), 1, "never below the drawing's size")
        XCTAssertEqual(TasbeehType.grow(1.16, cap: QiblaType.caption), 1.16)
        XCTAssertEqual(TasbeehType.grow(1.38, cap: QiblaType.caption), 1.2)
        XCTAssertEqual(TasbeehType.grow(1.38, cap: QiblaType.detail), 1.3)
    }
}
