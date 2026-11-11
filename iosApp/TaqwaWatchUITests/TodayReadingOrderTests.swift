import XCTest

/// Today as VoiceOver reads it (plan 4 Task 7, Ruling 42: the prayer first): the ring is one
/// element, read first, in one sentence (the prayer, the time left and its time); then each row is
/// one element in the order of the day, labelled as the phone says the row ("Fajr, 4:33") with its
/// state as the value. The list stands on the lit row, or with none lit the next, whenever it is
/// seen: opened again after the app has left the screen, and when the lit row moves with the app
/// open. And the three pages each show the watch's own line while it holds no feed.
///
/// Every test sets its whole state by launch arguments (plan 4 erratum p4 #9), as `LaunchTests`
/// says, and pins the watch's own language to English.
final class TodayReadingOrderTests: XCTestCase {
    private static let english = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]

    override func setUp() {
        continueAfterFailure = false
    }

    func testTodayReadsTheRingThenTheDay() throws {
        let app = launch(["-taqwaSampleFeed", "capetown", "-taqwaAt", "14:57", "-taqwaFrozen", "1", "-taqwaPage", "today"])

        let ring = app.descendants(matching: .any)["today.ring"]
        XCTAssertTrue(ring.waitForExistence(timeout: 30), "Today has drawn its ring")

        // Today's elements in the order VoiceOver reads them: the tree's own order, depth first. A
        // query's results come in another order (a ring declared after the rows still came first),
        // so the tree is walked here. XCUITest's tree also lists the texts drawn inside each
        // element, which VoiceOver does not visit, since each element ignores its children; so only
        // the elements themselves are read.
        let read = try inOrder(app.snapshot()).filter { $0.identifier.hasPrefix("today.") }
            .map { (id: $0.identifier, label: $0.label, value: ($0.value as? String) ?? "") }

        XCTAssertEqual(read.map(\.id), [
            "today.ring",
            "today.row.FAJR", "today.row.SUNRISE", "today.row.DHUHR",
            "today.row.ASR", "today.row.MAGHRIB", "today.row.ISHA",
        ], "the ring first, then the rows in the order of the day")
        XCTAssertEqual(read.first?.label, "Asr in 1 hour 13 minutes, at 16:10")
        XCTAssertEqual(read.dropFirst().map(\.label), [
            "Fajr, 4:33", "Sunrise, 6:01", "Dhuhr, 12:33", "Asr, 16:10", "Maghrib, 19:04", "Isha, 20:24",
        ])
        XCTAssertEqual(read.dropFirst().map(\.value), [
            "Passed", "Passed", "Current prayer", "Next prayer", "", "",
        ])
    }

    /// Opened again, the list stands on the lit row again (plan 4 Task 7: "opened scrolled to the
    /// lit row or the next"). An app that leaves for the clock face and is opened again keeps its
    /// views, so their first appearance alone is not enough: here the list is scrolled on past
    /// Dhuhr, the app leaves the screen for the background and comes back, and Dhuhr is at the
    /// list's top again. A lowered and raised wrist, which never sends it to the background, keeps
    /// the scroll (`TodayScrollTests`, Ruling 64 item 4).
    func testOpenedAgainTheListStandsOnTheLitRow() {
        let app = launch(["-taqwaSampleFeed", "capetown", "-taqwaAt", "14:57", "-taqwaFrozen", "1", "-taqwaPage", "today"])
        let list = app.descendants(matching: .any)["page.today"].scrollViews.firstMatch
        let dhuhr = app.descendants(matching: .any)["today.row.DHUHR"]
        XCTAssertTrue(list.waitForExistence(timeout: 30), "Today has drawn its list")
        XCTAssertTrue(dhuhr.waitForExistence(timeout: 10), "and its rows")
        XCTAssertTrue(holds { self.atTheTop(dhuhr, of: list) }, "opened on the lit row, Dhuhr")

        // Scrolled on with a drag inside the list, straight up, so the pager stays on Today.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.92))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.62)))
        XCTAssertTrue(holds { dhuhr.frame.minY < list.frame.minY - 10 }, "the list is scrolled on, past Dhuhr")

        XCUIDevice.shared.press(.home)
        XCTAssertTrue(holds { app.state != .runningForeground }, "the app has left the screen")
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10), "and is opened again")
        XCTAssertTrue(holds { self.atTheTop(dhuhr, of: list) }, "opened again on the lit row, Dhuhr")
    }

    /// When the next start comes with the app open, the list moves to the newly lit row, as it
    /// does when the day turns over at the feed's midnight. Opened at 16:09:00 on a running clock,
    /// Dhuhr is lit at the list's top; at 16:10 Asr is lit, at the list's top. The clock runs from
    /// the app's start, so the minute before Asr leaves about a minute's margin for the launch
    /// (Ruling 64 item 5).
    func testTheListMovesWithTheLitRow() {
        let app = launch(["-taqwaSampleFeed", "capetown", "-taqwaAt", "16:09:00", "-taqwaPage", "today"])
        let list = app.descendants(matching: .any)["page.today"].scrollViews.firstMatch
        let dhuhr = app.descendants(matching: .any)["today.row.DHUHR"]
        let asr = app.descendants(matching: .any)["today.row.ASR"]
        XCTAssertTrue(list.waitForExistence(timeout: 20), "Today has drawn its list")
        XCTAssertEqual(dhuhr.value as? String, "Current prayer", "opened before Asr's start, with Dhuhr lit")
        XCTAssertTrue(holds { self.atTheTop(dhuhr, of: list) }, "on Dhuhr")

        XCTAssertTrue(holds(for: 75) { (asr.value as? String) == "Current prayer" }, "Asr is lit at 16:10")
        XCTAssertTrue(holds { self.atTheTop(asr, of: list) }, "and the list has moved to Asr")
    }

    func testANewerFormatWithNoFeedSaysUpdate() {
        let app = launch(["-taqwaNoFeed", "1", "-taqwaNewerSeen", "1", "-taqwaPage", "today"])
        let update = "Update Taqwa on your watch"

        for page in ["today", "tasbeeh", "qibla"] {
            let shown = app.descendants(matching: .any)["page.\(page)"]
            XCTAssertTrue(shown.waitForExistence(timeout: 30), "the \(page) page exists")
            XCTAssertTrue(onScreen(shown, in: app), "the \(page) page is the one on the screen")
            XCTAssertTrue(shown.staticTexts[update].waitForExistence(timeout: 10), "the \(page) page says the update")
            XCTAssertFalse(app.staticTexts["Open Taqwa on your phone"].exists, "the notice comes first (plan 3's call 5)")
            if page != "qibla" { app.swipeLeft() }
        }
    }

    /// Every element of [snapshot]'s tree, depth first, each before its children.
    private func inOrder(_ snapshot: XCUIElementSnapshot) -> [XCUIElementSnapshot] {
        [snapshot] + snapshot.children.flatMap { inOrder($0) }
    }

    /// Whether [row] stands at the top of [list], as the list's `scrollTo(_:anchor: .top)` puts it.
    /// A row's element is its texts' frame, centred in the row's box, so at the top it starts a
    /// point or two under the list's top edge: about 1.5 on the SE 3 at the default text size.
    private func atTheTop(_ row: XCUIElement, of list: XCUIElement) -> Bool {
        let below = row.frame.minY - list.frame.minY
        return below >= 0 && below < 4
    }

    /// Whether [condition] comes to hold within [seconds], tried every quarter of a second.
    private func holds(for seconds: TimeInterval = 10, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(seconds)
        repeat {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return condition()
    }

    /// Whether [element] comes to fill the screen within ten seconds: a page swiped to, not one
    /// beside it that the pager keeps ready off the screen.
    private func onScreen(_ element: XCUIElement, in app: XCUIApplication) -> Bool {
        let screen = app.windows.firstMatch.frame
        let deadline = Date().addingTimeInterval(10)
        repeat {
            let frame = element.frame
            if abs(frame.midX - screen.midX) < 2, frame.width > 0 { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return false
    }

    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments + Self.english
        app.launch()
        return app
    }
}
