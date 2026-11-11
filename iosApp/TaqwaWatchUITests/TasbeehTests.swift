import WatchKit
import XCTest

/// The Tasbeeh page by touch (plan 4 Task 8): a tap anywhere counts, and the counter is one
/// element whose value says the count as the phone says it ("1 of 100"); the dhikr's line opens the
/// phrase list, each row one element, the seven built-ins first and his own after them, and the
/// phrase chosen is the one counted; and the list's Reset row asks the phone's question and goes
/// back to 0 (plan 4 erratum p4 #7).
///
/// Every test sets its whole state by launch arguments (plan 4 erratum p4 #9): a sample feed, and
/// `-taqwaFresh 1`, which forgets every count and the phrase chosen, so nothing an earlier test or
/// run counted is read back; `-taqwaTasbeehCount <n>` then opens the phrase chosen at n of round 1.
final class TasbeehTests: XCTestCase {
    static let english = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]

    override func setUp() {
        continueAfterFailure = false
    }

    func testATapCountsAndTheValueIsSpoken() {
        let app = launch()
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 30), "the counter has drawn")
        XCTAssertEqual(counter.label, "SubhanAllah")
        XCTAssertEqual(counter.value as? String, "0 of 100")

        counter.tap()
        XCTAssertTrue(Self.holds { (counter.value as? String) == "1 of 100" }, "one tap is one count, said as the phone says it")
    }

    /// The hint at the foot, "Turn the crown or tap", counts the tap it asks for. It stands below the
    /// page's own frame (y 40 to 171 of the SE 3's 197 points, 53 to 212 of the Series 12's 248,
    /// above the page dots), where review round 1 found a tap counted nothing and played no haptic.
    func testATapOnTheHintCounts() {
        let app = launch()
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 30), "the counter has drawn")
        XCTAssertEqual(counter.value as? String, "0 of 100")

        // The middle of the hint's letters.
        let hint = app.descendants(matching: .any)["tasbeeh.hint"]
        XCTAssertTrue(hint.exists, "the hint")
        Self.tap(app, at: CGPoint(x: hint.frame.midX, y: hint.frame.midY))
        XCTAssertTrue(Self.holds { (counter.value as? String) == "1 of 100" }, "a tap on the hint counts")
    }

    /// The hint at the foot stands clear of the system's page dots on every watch, in Latin and in
    /// Arabic letters (spec 2.5, "the hint at the bottom"). Review round 4 found the dots on the
    /// hint's letters on the Series 12 (46mm), whose screen is shorter for its width than the SE
    /// 3's (40mm): every place was the drawing's scaled by the width alone, and the drawing's
    /// baseline at 198 units fell at 239.4 of the 248 points, where the dots start at 240.
    ///
    /// The dots are the pager's element valued "page 2 of 3", whose frame reaches 10 points beyond
    /// the dots above and below (measured: the SE 3's 3-point dots at y 193 to 196 in a frame from
    /// 183, 23 high; the Series 12's 6-point dots at 240 to 246 in one from 230, 26 high). The
    /// hint's frame holds its letters, the Latin descender and the Arabic marks (on the SE 3 the
    /// 'p' ends at 189 in a frame to 189.5, and the Arabic at 189 in one to 189). It ends at least
    /// 3 points above the dots' top: on the SE 3, where `aTasbeeh` is drawn, 3.5.
    func testTheHintStandsClearOfThePageDots() {
        for (script, feed, more) in [
            ("Latin", "capetown", ["-taqwaTasbeehPhrase", "astaghfirullah", "-taqwaTasbeehCount", "64"]),
            ("Arabic", "ar-ly12", ["-taqwaTasbeehCount", "40"]),
        ] {
            let app = launch(more, feed: feed)
            let hint = app.descendants(matching: .any)["tasbeeh.hint"]
            XCTAssertTrue(hint.waitForExistence(timeout: 10), "the \(script) hint")
            let dots = app.descendants(matching: .other).matching(NSPredicate(format: "value BEGINSWITH 'page '")).firstMatch
            XCTAssertTrue(dots.exists, "the pager's page dots")
            let dotsTop = dots.frame.minY + 10
            XCTAssertLessThanOrEqual(
                hint.frame.maxY, dotsTop - 3,
                "the \(script) hint, to y \(hint.frame.maxY), stands 3 points clear of the page dots from y \(dotsTop)"
            )
            app.terminate()
        }
    }

    /// A tap anywhere below the system's clock counts but on the dhikr's line, which opens the list
    /// (spec 2.5, plan 4 Task 8): the band under the clock beside the line, the crown's arrows, the
    /// ring, the hint's ends and the page dots' row at the foot, each one count.
    func testATapAnywhereBelowTheClockCounts() {
        let app = launch()
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 30), "the counter has drawn")
        XCTAssertEqual(counter.value as? String, "0 of 100")

        // In `aTasbeeh`'s units (the screen's width is 172): the clock's foot is at 30, the dhikr's
        // line runs from 18 to 154 across and 30 to 61 down, and the screen ends at about 209 on
        // the SE 3 and 205 on the Series 12.
        let places: [(String, CGPoint)] = [
            ("under the clock, left of the line", CGPoint(x: 9, y: 35)),
            ("under the clock, right of the line", CGPoint(x: 163, y: 35)),
            ("the crown's arrows", CGPoint(x: 165, y: 59)),
            ("the ring's middle", CGPoint(x: 86, y: 118)),
            ("the hint's start", CGPoint(x: 30, y: 194)),
            ("the hint's end", CGPoint(x: 142, y: 194)),
            ("the page dots' row", CGPoint(x: 86, y: 205)),
            ("the foot's corner", CGPoint(x: 40, y: 205)),
        ]
        for (count, (place, point)) in places.enumerated() {
            Self.tap(app, atUnits: point)
            XCTAssertTrue(Self.holds { (counter.value as? String) == "\(count + 1) of 100" }, "a tap on \(place) counts")
        }
        XCTAssertFalse(app.descendants(matching: .any)["tasbeeh.reset"].exists, "no tap opened the list")
    }

    /// The dhikr's line opens the list: the seven built-ins in the phone's order, then his own as
    /// typed, each row one element ("After prayer, 100"), the one counted marked selected; a row
    /// chosen is counted from its own count, and the crown counts it once the list has closed.
    func testThePhraseListOpensFromTheDhikrAndChoosesAPhrase() {
        let app = launch(["-taqwaPhrase", "اللهم صل على محمد"])
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 30))
        Self.openTheList(app)

        let rows = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'tasbeeh.phrase.'"))
        XCTAssertTrue(rows.firstMatch.waitForExistence(timeout: 10), "the list is open")
        XCTAssertEqual(rows.allElementsBoundByIndex.map(\.label), [
            "After prayer, 100", "SubhanAllah, 100", "Alhamdulillah, 100", "Allahu Akbar, 100", "Astaghfirullah, 100",
            "La ilaha illallah, 100", "SubhanAllahi wa bihamdihi, 100",
            "سبحان الله وبحمده، سبحان الله العظيم, 100", "Astaghfirullah wa atubu ilayh, 33", "اللهم صل على محمد, 33",
        ])
        XCTAssertEqual(rows.allElementsBoundByIndex.map(\.isSelected), [true] + Array(repeating: false, count: 9))

        let astaghfirullah = app.descendants(matching: .any)["tasbeeh.phrase.astaghfirullah"]
        Self.reveal(astaghfirullah, in: app)
        astaghfirullah.tap()
        XCTAssertTrue(Self.holds { counter.exists && counter.label == "Astaghfirullah" }, "the phrase chosen is counted")
        XCTAssertEqual(counter.value as? String, "0 of 100")

        // Back on the counter, the crown counts again.
        XCUIDevice.shared.rotateDigitalCrown(delta: TasbeehCrownTests.crownDetent, velocity: .slow)
        XCTAssertTrue(Self.holds { (counter.value as? String) == "1 of 100" }, "the crown counts the phrase chosen")
    }

    /// Reset asks "Reset the count for this dhikr?" and, confirmed, goes back to 0 of round 1.
    func testResetAsksAndGoesBackToTheStart() {
        let app = launch(["-taqwaTasbeehCount", "64"])
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 30))
        XCTAssertEqual(counter.value as? String, "64 of 100")
        Self.openTheList(app)

        let reset = app.descendants(matching: .any)["tasbeeh.reset"]
        XCTAssertTrue(reset.waitForExistence(timeout: 10), "the list ends in Reset")
        XCTAssertEqual(reset.label, "Reset")
        Self.reveal(reset, in: app)
        reset.tap()
        XCTAssertTrue(app.staticTexts["Reset the count for this dhikr?"].waitForExistence(timeout: 10), "the phone's question")
        app.buttons["Reset"].firstMatch.tap()
        XCTAssertTrue(Self.holds { counter.exists && (counter.value as? String) == "0 of 100" }, "back to the start")
    }

    /// The watch's standard text sizes above its own default grow the lines, as its accessibility
    /// sizes do (plan 3's call 15): on Astaghfirullah the transliteration, capped at 1.3, and the
    /// dhikr, capped at 1.15, are wider at the first size above the default than at the default,
    /// and wider again at XXXL. That first size is XL on a watch whose default is L (40 to 42mm,
    /// the SE 3) and XXL on one whose default is XL (44mm and larger, the Series 12).
    /// Review round 2 found both lines the default's from XS to XXXL: a 1-point `@ScaledMetric` was
    /// rounded to the pixel grid (`TasbeehType.scaleBase`). The size is the app's process's alone
    /// (`-UIPreferredContentSizeCategoryName`), since `simctl ui content_size` is unsupported on
    /// watchOS: no setting of the watch changes.
    func testTheStandardSizesAboveTheDefaultGrowTheLines() {
        let own = Self.watchsOwnSize()
        let atDefault = lineWidths(nil)
        let next = own == "UICTContentSizeCategoryL" ? "UICTContentSizeCategoryXL" : "UICTContentSizeCategoryXXL"
        let atNext = lineWidths(next)
        let atXXXL = lineWidths("UICTContentSizeCategoryXXXL")
        // The steps measured: 5.5 % from the default to XL and 16 % to XXXL on the SE 3; 5 % from
        // the default to XXL and 10.5 % to XXXL on the Series 12.
        XCTAssertGreaterThan(atNext.transliteration, atDefault.transliteration * 1.03, "\(next) grows the transliteration")
        XCTAssertGreaterThan(atXXXL.transliteration, atNext.transliteration * 1.03, "XXXL grows it further")
        XCTAssertGreaterThan(atNext.dhikr, atDefault.dhikr * 1.03, "\(next) grows the dhikr")
        XCTAssertGreaterThan(atXXXL.dhikr, atNext.dhikr * 1.03, "XXXL grows the dhikr further")
    }

    /// The watch's own default text size draws the drawing (spec, the standing rules: accessibility
    /// never changes the visible design at default settings), on every watch size. Watches differ
    /// in that default: Apple gives 40, 41 and 42mm watches Large and 44, 45, 46 and 49mm ones
    /// XLarge ("Supporting multiple watch sizes"). The body style scales from each watch's own
    /// default, not from Large (review round 3 of plan 4 Task 8 asked; its fix round 3 measured a
    /// base of 100 at 100 on the SE 3 at L and on the Series 12 at XL, where L reads 95), so at
    /// the default both lines are as wide as at XS and at L, which floor to the drawing's sizes on
    /// every watch. A scale taken from Large would draw them 5 % wider on the Series 12 at its
    /// default; run there, this test finds it.
    func testTheWatchsOwnDefaultSizeDrawsTheDrawing() {
        let own = Self.watchsOwnSize()
        let atDefault = lineWidths(nil)
        for size in ["UICTContentSizeCategoryXS", "UICTContentSizeCategoryL"] {
            let below = lineWidths(size)
            XCTAssertEqual(atDefault.transliteration, below.transliteration, "at the watch's own \(own) the transliteration is as at \(size)")
            XCTAssertEqual(atDefault.dhikr, below.dhikr, "at the watch's own \(own) the dhikr is as at \(size)")
        }
    }

    /// The watch's own text size, as the system holds it (`WKInterfaceDevice`, read in this test's
    /// process, which no launch argument sizes): the watch's default, Large or XLarge, since no
    /// test changes the setting. Any other is a simulator left changed, and the tests above stop.
    private static func watchsOwnSize(file: StaticString = #filePath, line: UInt = #line) -> String {
        let own = WKInterfaceDevice.current().preferredContentSizeCategory
        XCTAssertTrue(["UICTContentSizeCategoryL", "UICTContentSizeCategoryXL"].contains(own), "the watch is at its default text size, not \(own)", file: file, line: line)
        return own
    }

    /// The widths of Astaghfirullah's dhikr and transliteration at the text size [size] (nil: the
    /// watch's own), in points.
    private func lineWidths(_ size: String?, file: StaticString = #filePath, line: UInt = #line) -> (dhikr: CGFloat, transliteration: CGFloat) {
        let app = launch(["-taqwaTasbeehPhrase", "astaghfirullah"] + (size.map { ["-UIPreferredContentSizeCategoryName", $0] } ?? []))
        let lines = app.descendants(matching: .any).matching(identifier: "tasbeeh.dhikr")
        XCTAssertTrue(lines.firstMatch.waitForExistence(timeout: 10), "the dhikr's line", file: file, line: line)
        let all = lines.allElementsBoundByIndex
        let transliteration = all.first { $0.label == "Astaghfirullah" }
        let dhikr = all.first { $0.label != "Astaghfirullah" && !$0.label.isEmpty }
        XCTAssertNotNil(transliteration, "the transliteration at \(size ?? "the default")", file: file, line: line)
        XCTAssertNotNil(dhikr, "the dhikr at \(size ?? "the default")", file: file, line: line)
        let widths = (dhikr: dhikr?.frame.width ?? 0, transliteration: transliteration?.frame.width ?? 0)
        app.terminate()
        return widths
    }

    // MARK: - Helpers

    private func launch(_ more: [String] = [], feed: String = "capetown") -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-taqwaSampleFeed", feed, "-taqwaFresh", "1", "-taqwaPage", "tasbeeh"] + more + Self.english
        app.launch()
        Self.waitForThePage(app)
        return app
    }

    /// Waits until the Tasbeeh page fills the screen. A launch on `-taqwaPage tasbeeh` opens the
    /// pager on Today and scrolls it to the second page, and a touch while it scrolls is the
    /// pager's: on the first launch after the SE 3 boots, the tap test's tap came inside that scroll
    /// and counted nothing (31 October), though a wearer, who swipes to the page, never meets it.
    static func waitForThePage(_ app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        let page = app.descendants(matching: .any)["page.tasbeeh"]
        XCTAssertTrue(page.waitForExistence(timeout: 30), "the Tasbeeh page exists", file: file, line: line)
        let screen = app.windows.firstMatch.frame
        XCTAssertTrue(holds { abs(page.frame.midX - screen.midX) < 2 && page.frame.width > 0 }, "the Tasbeeh page fills the screen", file: file, line: line)
        // The pager's scroll ends a moment after the page stands in place.
        RunLoop.current.run(until: Date().addingTimeInterval(1))
    }

    /// Taps the dhikr's line at the top of the page, which opens the list. The line's identifier is
    /// on each of its texts (the dhikr and its transliteration), so the first is tapped.
    static func openTheList(_ app: XCUIApplication) {
        let dhikr = app.descendants(matching: .any).matching(identifier: "tasbeeh.dhikr").firstMatch
        XCTAssertTrue(dhikr.waitForExistence(timeout: 10), "the dhikr's line")
        dhikr.tap()
    }

    /// Scrolls the list, a third of the screen at a time, towards [element] until it can be
    /// touched. Each drag is slow and held still before it lifts, so the list stops where the
    /// finger stops: a quick drag flings it on, and on the language samples' eight rows a third of
    /// the screen carried a row 266 points, from below the screen to above it and back, each time
    /// (P4.11 fix round 1).
    static func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        let screen = app.windows.firstMatch.frame
        for _ in 0..<12 where !element.isHittable {
            let below = element.frame.midY > screen.midY
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: below ? 0.7 : 0.35))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: below ? 0.35 : 0.7))
            from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.3)
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTAssertTrue(element.isHittable, "\(element.identifier) is on the screen")
    }

    /// Taps the screen at [point], in `aTasbeeh`'s units: the screen's width is 172 of them.
    static func tap(_ app: XCUIApplication, atUnits point: CGPoint) {
        let k = app.windows.firstMatch.frame.width / 172
        tap(app, at: CGPoint(x: point.x * k, y: point.y * k))
    }

    /// Taps the screen at [point], in points.
    static func tap(_ app: XCUIApplication, at point: CGPoint) {
        app.windows.firstMatch.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: point.x, dy: point.y)).tap()
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
