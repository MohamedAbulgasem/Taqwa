import XCTest
@testable import WatchCore

/// When Today's list goes back to its lit row (Ruling 64 item 4). Apple's Always On: a lowered
/// wrist makes the app inactive and leaves it frontmost, dimmed, for up to an hour, and a raised
/// one makes it active again; a banner does the same. Neither is a reopening, so the wearer's scroll
/// stays. Opened again after it went to the background (from the clock face, a complication or the
/// Dock), the list stands on its focus again, and it always follows the lit row when that moves.
final class TodayScrollTests: XCTestCase {
    func testALoweredAndRaisedWristKeepsTheWearersScroll() {
        var scroll = TodayScroll()

        XCTAssertFalse(scroll.refocuses(entering: .inactive), "the wrist lowered")
        XCTAssertFalse(scroll.refocuses(entering: .active), "and raised again")
        XCTAssertFalse(scroll.refocuses(entering: .inactive), "a banner")
        XCTAssertFalse(scroll.refocuses(entering: .active), "dismissed")
    }

    func testOpenedAgainFromTheBackgroundTheListGoesToItsFocus() {
        var scroll = TodayScroll()

        XCTAssertFalse(scroll.refocuses(entering: .inactive), "the wrist lowered")
        XCTAssertFalse(scroll.refocuses(entering: .background), "the clock face takes the screen back")
        XCTAssertFalse(scroll.refocuses(entering: .inactive), "on its way back")
        XCTAssertTrue(scroll.refocuses(entering: .active), "opened again: the lit row")
    }

    func testOnlyTheFirstActiveAfterTheBackgroundRefocuses() {
        var scroll = TodayScroll()

        XCTAssertFalse(scroll.refocuses(entering: .background))
        XCTAssertTrue(scroll.refocuses(entering: .active), "straight from the background")
        XCTAssertFalse(scroll.refocuses(entering: .inactive))
        XCTAssertFalse(scroll.refocuses(entering: .active), "a raised wrist after that keeps the scroll")
    }

    func testTheListFollowsTheLitRowWhenItMoves() {
        XCTAssertTrue(TodayScroll.refocuses(focusFrom: .DHUHR, to: .ASR), "the next start lit")
        XCTAssertTrue(TodayScroll.refocuses(focusFrom: .FAJR, to: .DHUHR), "Sunrise ends Fajr: the next start's row")
        XCTAssertTrue(TodayScroll.refocuses(focusFrom: .ISHA, to: .FAJR), "the date turns over at the feed's midnight")
        XCTAssertFalse(TodayScroll.refocuses(focusFrom: .ASR, to: .ASR), "nothing moved")
    }
}
