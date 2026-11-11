import XCTest

/// The Digital Crown on the counter (plan 4 Task 8): each detent turned up counts one and each
/// turned back takes one away, a fast spin through the hundred counts every detent into the next
/// round, and the counter's value says each step as the phone says it. The turns are made with
/// `XCUIDevice.rotateDigitalCrown(delta:velocity:)`, whose delta is in whole rotations of the crown;
/// `crownDetent` is one detent of the counter in those rotations, as measured on the SE 3.
///
/// Every test sets its whole state by launch arguments (plan 4 erratum p4 #9), as `TasbeehTests`.
final class TasbeehCrownTests: XCTestCase {
    /// One detent of the counter, in rotations of the crown, as measured on the SE 3 (40mm) at
    /// watchOS 27.0 on 31 October 2026 (Step 4) by a throwaway test: one rotation (`delta: 1.0`) from
    /// 0 counted n = 8 at `.slow` and 9 at `.fast`, twice each, and half a rotation back took 4 away
    /// at either. So `crownDetent` is 1/8. The crown accelerates by a detent over a whole fast turn,
    /// but a fast turn of three detents counted exactly 3 (three times), and every slow single detent
    /// one: `testAFastSpinFrom99OpensRoundTwo` asserts round 2 at the fast detents less one, 2.
    static let crownDetent: CGFloat = 1.0 / 8

    override func setUp() {
        continueAfterFailure = false
    }

    func testASlowTurnCountsAndATurnBackUndoes() {
        let counter = launch()
        XCTAssertEqual(counter.value as? String, "0 of 100")
        XCUIDevice.shared.rotateDigitalCrown(delta: 2 * Self.crownDetent, velocity: .slow)
        XCTAssertTrue(TasbeehTests.holds { (counter.value as? String) == "2 of 100" }, "two detents up count two")
        XCUIDevice.shared.rotateDigitalCrown(delta: -Self.crownDetent, velocity: .slow)
        XCTAssertTrue(TasbeehTests.holds { (counter.value as? String) == "1 of 100" }, "one back takes one away")
    }

    func testAFastSpinFrom99OpensRoundTwo() {
        let counter = launch(["-taqwaTasbeehCount", "99"])
        XCTAssertEqual(counter.value as? String, "99 of 100")
        XCUIDevice.shared.rotateDigitalCrown(delta: 3 * Self.crownDetent, velocity: .fast)
        let app = XCUIApplication()
        XCTAssertTrue(TasbeehTests.holds { app.staticTexts["ROUND 2"].exists }, "the next round is open")
        XCTAssertTrue(TasbeehTests.holds { (counter.value as? String) == "2 of 100" }, "every detent counted, through the hundred")
        XCTAssertEqual(counter.label, "SubhanAllah, round 2")
    }

    func testTheSpokenValueFollowsEachStep() {
        let counter = launch(["-taqwaTasbeehCount", "31"])
        XCTAssertEqual(counter.value as? String, "31 of 100")
        for said in ["32 of 100", "33 of 100. Next, Alhamdulillah", "34 of 100"] {
            XCUIDevice.shared.rotateDigitalCrown(delta: Self.crownDetent, velocity: .slow)
            XCTAssertTrue(TasbeehTests.holds { (counter.value as? String) == said }, said)
        }
        XCUIDevice.shared.rotateDigitalCrown(delta: -Self.crownDetent, velocity: .slow)
        XCTAssertTrue(TasbeehTests.holds { (counter.value as? String) == "33 of 100. Next, Alhamdulillah" }, "back across the part's end")
    }

    private func launch(_ more: [String] = []) -> XCUIElement {
        let app = XCUIApplication()
        app.launchArguments = ["-taqwaSampleFeed", "capetown", "-taqwaFresh", "1", "-taqwaPage", "tasbeeh"] + more + TasbeehTests.english
        app.launch()
        TasbeehTests.waitForThePage(app)
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 30), "the counter has drawn")
        return counter
    }
}
