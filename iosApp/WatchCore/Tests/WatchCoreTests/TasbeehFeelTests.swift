import XCTest
@testable import WatchCore

/// One haptic a turn (plan 4, the Lead's call 7; plan 3's call 14), restating `wearApp`'s
/// `WatchTasbeehTest` cases: each count, undo or crown turn plays at most one haptic, the strongest
/// end it crossed (the set's, then a part's, then the count or the undo); a part's or the set's is
/// always played, and no count or undo is played within 0.14 s of a part's or 0.40 s of a set's,
/// though it is still counted. A change that changes nothing is silent. On the after-prayer set.
final class TasbeehFeelTests: XCTestCase {
    private let set = TasbeehPresets.default

    private func counter(_ count: Int, round: Int = 1) -> TasbeehCounter {
        TasbeehCounter(preset: set, state: TasbeehState(presetId: set.id, count: count, round: round), now: 0)
    }

    func testAFastTurnAcrossAPartsEndBuzzesItAlone() {
        var feel = TasbeehFeel()
        var up = counter(31)
        XCTAssertEqual(feel.choose(up.turn(3, now: 0), now: 0), .part)
        XCTAssertEqual(up.state.count, 34)

        var back = TasbeehFeel()
        var down = counter(1)
        XCTAssertEqual(back.choose(down.turn(-3, now: 0), now: 0), .undo, "one undo for the turn")
        XCTAssertEqual(down.state.count, 0)
    }

    func testNoTickCutsThePartsBuzz() {
        var feel = TasbeehFeel()
        var tasbeeh = counter(31)
        XCTAssertEqual(feel.choose(tasbeeh.turn(3, now: 0), now: 0), .part)
        XCTAssertNil(feel.choose(tasbeeh.turn(1, now: 0.10), now: 0.10), "no tick at 0.10 s")
        XCTAssertEqual(tasbeeh.state.count, 35, "though it is counted")
        XCTAssertEqual(feel.choose(tasbeeh.turn(1, now: 0.14), now: 0.14), .count, "the tick comes back at 0.14 s")
        XCTAssertEqual(tasbeeh.state.count, 36)
    }

    func testNoTickCutsTheSetsBuzz() {
        var feel = TasbeehFeel()
        var tasbeeh = counter(98)
        XCTAssertEqual(feel.choose(tasbeeh.turn(2, now: 0), now: 0), .set)
        XCTAssertEqual(tasbeeh.state, TasbeehState(presetId: set.id, count: 100, round: 1))
        XCTAssertNil(feel.choose(tasbeeh.turn(-1, now: 0.399), now: 0.399), "no undo at 0.399 s")
        XCTAssertEqual(tasbeeh.state.count, 99)
        XCTAssertEqual(feel.choose(tasbeeh.turn(-1, now: 0.40), now: 0.40), .undo, "it comes back at 0.40 s")
        XCTAssertEqual(tasbeeh.state.count, 98)
    }

    func testUndoAtZeroIsSilent() {
        var feel = TasbeehFeel()
        var tasbeeh = counter(0)
        XCTAssertNil(feel.choose(tasbeeh.turn(-1, now: 0), now: 0))
        XCTAssertNil(feel.choose(tasbeeh.turn(-5, now: 0.5), now: 0.5))
        var later = counter(0, round: 3)
        XCTAssertNil(feel.choose(later.turn(-1, now: 0), now: 0), "round 3's 0 is not round 2's 100")
    }

    /// The strongest end crossed, not the first: from 60, forty detents cross Alhamdulillah's end
    /// at 66 and the set's at 100.
    func testATurnAcrossAPartsEndAndTheSetsBuzzesTheSet() {
        var feel = TasbeehFeel()
        var tasbeeh = counter(60)
        XCTAssertEqual(feel.choose(tasbeeh.turn(40, now: 0), now: 0), .set)
    }

    /// A part's end inside the set's quiet window still buzzes: it is never cut for a tick's sake.
    func testAPartsBuzzIsAlwaysPlayed() {
        var feel = TasbeehFeel()
        var tasbeeh = counter(98)
        XCTAssertEqual(feel.choose(tasbeeh.turn(2, now: 0), now: 0), .set)
        XCTAssertEqual(feel.choose(tasbeeh.turn(33, now: 0.1), now: 0.1), .part)
        XCTAssertEqual(tasbeeh.state, TasbeehState(presetId: set.id, count: 33, round: 2))
    }

    /// A tap is one count, and plays the count's click.
    func testATapPlaysTheCount() {
        var feel = TasbeehFeel()
        var tasbeeh = counter(5)
        XCTAssertEqual(feel.choose(tasbeeh.turn(1, now: 0), now: 0), .count)
        XCTAssertNil(feel.choose(TurnResult(events: [], undos: 0), now: 1), "nothing changed, nothing felt")
    }
}
