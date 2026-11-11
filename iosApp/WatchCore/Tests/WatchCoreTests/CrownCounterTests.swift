import XCTest
@testable import WatchCore

/// The Digital Crown in whole detents (plan 4 Task 8): `digitalCrownRotation(detent:)` hands the
/// view the crown's value, and `CrownCounter` turns each change into the detents it passed, `+n`
/// counts and `-n` takes away, as `wearApp`'s `RotaryCounter` turns the Galaxy's bezel.
final class CrownCounterTests: XCTestCase {

    func testTheCrownCountsWholeDetents() {
        var crown = CrownCounter()
        XCTAssertEqual(crown.turned(to: 0), 0, "the first value seeds it")
        XCTAssertEqual(crown.turned(to: 1), 1)
        XCTAssertEqual(crown.turned(to: 4), 3)
        XCTAssertEqual(crown.turned(to: 2), -2)
        XCTAssertEqual(crown.turned(to: 2.4), 0, "a part of a detent is no count")
    }

    /// Wherever the crown stands when the counter first sees it, nothing is counted for it.
    func testTheFirstValueIsNoTurn() {
        var crown = CrownCounter()
        XCTAssertEqual(crown.turned(to: 57), 0)
        XCTAssertEqual(crown.turned(to: 58), 1)
        XCTAssertEqual(crown.turned(to: -3), -61)
    }

    /// A part of a detent each way never adds up to a count: the crown's value is read as the
    /// detent it is nearest.
    func testPartsOfADetentNeverAddUp() {
        var crown = CrownCounter()
        _ = crown.turned(to: 2)
        XCTAssertEqual(crown.turned(to: 2.4), 0)
        XCTAssertEqual(crown.turned(to: 2.3), 0)
        XCTAssertEqual(crown.turned(to: 2.45), 0)
        XCTAssertEqual(crown.turned(to: 3), 1, "the whole detent counts once")
        XCTAssertEqual(crown.turned(to: 1.6), -1)
    }

    /// A new phrase starts the crown again: what it stood at under the last phrase is no turn.
    func testItIsSeededAgainWhenThePhraseChanges() {
        var crown = CrownCounter()
        _ = crown.turned(to: 10)
        XCTAssertEqual(crown.turned(to: 12), 2)
        crown.reseed()
        XCTAssertEqual(crown.turned(to: 30), 0, "seeded again")
        XCTAssertEqual(crown.turned(to: 29), -1)
    }
}
