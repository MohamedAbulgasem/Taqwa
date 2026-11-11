import XCTest

/// The Qibla page to VoiceOver and by its drawing (plan 4 Task 9): one element, `qibla.dial`,
/// labelled with what the page says (the turn while searching, "Facing qibla", or a bad state's
/// title) and valued with the detail as it is heard. The simulator has no magnetometer, so the
/// DEBUG scripted compass stands in for it: `-taqwaHeading <deg>` is the magnetic heading, a sample
/// every 50 ms, made true by the feed's declination as the real one is. On the Cape Town sample
/// (bearing 23°, declination −27°, `WatchQiblaTests.testTheSampleIsCapeTownAsTheUITestsTakeIt`):
/// magnetic 50 is true 23, facing; 7 is true 340, the Qibla to the right.
///
/// Every test sets its whole state by launch arguments (plan 4 erratum p4 #9), and each waits up
/// to 5 s for the compass's gate.
final class QiblaTests: XCTestCase {
    static let english = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]

    override func setUp() {
        continueAfterFailure = false
    }

    func testFacingTheQiblaIsSpoken() {
        let dial = launch(["-taqwaHeading", "50"])
        XCTAssertTrue(Self.holds(for: 5) { dial.label == "Facing qibla" }, "facing: \(dial.label)")
        XCTAssertEqual(dial.value as? String, "Makkah · 6,600 km away", "the long phrase is heard")
        XCTAssertEqual(XCUIApplication().descendants(matching: .any)["qibla.detail"].label, "6,600 km", "the dial's short form is drawn (Ruling 66)")
    }

    func testSearchingSpeaksTheTurn() {
        let dial = launch(["-taqwaHeading", "7"])
        XCTAssertTrue(Self.holds(for: 5) { dial.label == "Turn right about 45 degrees" }, "searching: \(dial.label)")
        XCTAssertEqual(dial.value as? String, "Makkah · 6,600 km away")
    }

    func testNoCompassSpeaksTheBearing() {
        let dial = launch(["-taqwaNoCompass", "1"])
        XCTAssertTrue(Self.holds(for: 5) { dial.label == "No compass sensor" }, "no compass: \(dial.label)")
        XCTAssertEqual(dial.value as? String, "23° · 6,600 km to Makkah", "the long phrase is heard")
        XCTAssertEqual(XCUIApplication().descendants(matching: .any)["qibla.detail"].label, "23° · 6,600 km", "the dial's short form is drawn (Ruling 66)")
    }

    func testALowCompassAsksForCalibration() {
        let dial = launch(["-taqwaHeading", "50", "-taqwaHeadingAccuracy", "60"])
        XCTAssertTrue(Self.holds(for: 5) { dial.label == "Compass needs calibrating" }, "low: \(dial.label)")
    }

    /// The caption and the detail stand under the dial and above the system's page dots, inside the
    /// screen, in Latin and in Arabic letters, as the hint does on Tasbeeh: the dots are the pager's
    /// element valued "page 3 of 3", whose frame reaches 10 points beyond them (plan 4 Task 8's
    /// fix round 4).
    func testTheReadoutStandsClearOfThePageDotsAndInsideTheScreen() {
        for (script, feed, heading) in [("Latin", "capetown", "7"), ("Arabic", "ar-ly12", "7"), ("Latin, no compass", "capetown", nil)] {
            let app = XCUIApplication()
            let compass = heading.map { ["-taqwaHeading", $0] } ?? ["-taqwaNoCompass", "1"]
            app.launchArguments = ["-taqwaSampleFeed", feed, "-taqwaPage", "qibla"] + compass + Self.english
            app.launch()
            Self.waitForThePage(app)
            let screen = app.windows.firstMatch.frame
            let dots = app.descendants(matching: .other).matching(NSPredicate(format: "value BEGINSWITH 'page '")).firstMatch
            XCTAssertTrue(dots.exists, "the pager's page dots")
            let dotsTop = dots.frame.minY + 10
            for part in ["caption", "detail"] {
                let line = app.descendants(matching: .any)["qibla.\(part)"]
                XCTAssertTrue(line.waitForExistence(timeout: 5), "the \(script) \(part)")
                XCTAssertLessThanOrEqual(line.frame.maxY, dotsTop - 3, "the \(script) \(part), to y \(line.frame.maxY), stands clear of the dots from \(dotsTop)")
                XCTAssertGreaterThanOrEqual(line.frame.minX, screen.minX + 2, "the \(script) \(part) inside the screen")
                XCTAssertLessThanOrEqual(line.frame.maxX, screen.maxX - 2, "the \(script) \(part) inside the screen")
            }
            app.terminate()
        }
    }

    // MARK: - Helpers

    /// Launches on the Qibla page with the Cape Town sample and [more], and answers the dial.
    private func launch(_ more: [String], feed: String = "capetown") -> XCUIElement {
        let app = XCUIApplication()
        app.launchArguments = ["-taqwaSampleFeed", feed, "-taqwaPage", "qibla"] + more + Self.english
        app.launch()
        Self.waitForThePage(app)
        let dial = app.descendants(matching: .any)["qibla.dial"]
        XCTAssertTrue(dial.waitForExistence(timeout: 10), "the dial has drawn")
        return dial
    }

    /// Waits until the Qibla page fills the screen, as `TasbeehTests.waitForThePage` does for its
    /// own: a launch on `-taqwaPage qibla` scrolls the pager there from Today.
    static func waitForThePage(_ app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        let page = app.descendants(matching: .any)["page.qibla"]
        XCTAssertTrue(page.waitForExistence(timeout: 30), "the Qibla page exists", file: file, line: line)
        let screen = app.windows.firstMatch.frame
        XCTAssertTrue(holds { abs(page.frame.midX - screen.midX) < 2 && page.frame.width > 0 }, "the Qibla page fills the screen", file: file, line: line)
        RunLoop.current.run(until: Date().addingTimeInterval(1))
    }

    /// Whether [condition] comes to hold within [seconds], tried every tenth of a second.
    static func holds(for seconds: TimeInterval = 10, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(seconds)
        repeat {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        } while Date() < deadline
        return condition()
    }
}
