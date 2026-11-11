import XCTest
@testable import WatchCore

/// The counter's hold and debounce (plan 4 Task 8), the phone's `TasbeehViewModel` (its `holdJob`,
/// `debounceJob`, `settle` and `flush`) as a state machine on explicit time, in seconds on a
/// monotonic clock: a finished set holds its full ring for a second and then rolls over to 0 of
/// the next round, saved at once; a count or a turn back inside the hold cancels it; each burst is
/// written once, 0.3 s after its last change; and leaving settles a pending hold first. On the
/// after-prayer set, 33 · 33 · 34, unless a case names its own phrase.
final class TasbeehCounterTests: XCTestCase {
    private let set = TasbeehPresets.default

    private func counter(_ count: Int, round: Int = 1, preset: TasbeehPreset? = nil, now: Double = 0) -> TasbeehCounter {
        let preset = preset ?? set
        return TasbeehCounter(preset: preset, state: TasbeehState(presetId: preset.id, count: count, round: round), now: now)
    }

    private func state(_ count: Int, _ round: Int, _ id: String = TasbeehPresets.defaultId) -> TasbeehState {
        TasbeehState(presetId: id, count: count, round: round)
    }

    func testTheHoldRollsOverToZeroOfTheNextRound() {
        var tasbeeh = counter(99)
        XCTAssertEqual(tasbeeh.tap(now: 0), .setComplete)
        XCTAssertEqual(tasbeeh.holdUntil, 1.0)
        _ = tasbeeh.advance(to: 0.99)
        XCTAssertEqual(tasbeeh.state, state(100, 1), "still held at 0.99 s")
        XCTAssertEqual(tasbeeh.advance(to: 1.0), state(0, 2), "rolled over at 1 s, and saved at once")
        XCTAssertNil(tasbeeh.holdUntil)
        XCTAssertNil(tasbeeh.nextDeadline, "nothing more to do")
    }

    /// The crown's three detents at 0, 0.4 and 0.5: the set's end, then the next round's first two
    /// counts. The hold the first opened is cancelled by the second, so it never lands on them.
    func testACrownSpinThroughTheHundredDuringTheHoldKeepsEveryCount() {
        var tasbeeh = counter(99)
        XCTAssertEqual(tasbeeh.tap(now: 0), .setComplete)
        XCTAssertEqual(tasbeeh.state, state(100, 1))
        XCTAssertEqual(tasbeeh.tap(now: 0.4), .tick)
        XCTAssertEqual(tasbeeh.state, state(1, 2))
        XCTAssertEqual(tasbeeh.tap(now: 0.5), .tick)
        XCTAssertEqual(tasbeeh.state, state(2, 2))
        XCTAssertEqual(tasbeeh.advance(to: 1.5), state(2, 2), "the debounced write: never 0, never round 3")
        XCTAssertEqual(tasbeeh.state, state(2, 2))
    }

    func testAnUndoDuringTheHoldIsKept() {
        var tasbeeh = counter(99)
        _ = tasbeeh.tap(now: 0)
        XCTAssertTrue(tasbeeh.undo(now: 0.5))
        XCTAssertEqual(tasbeeh.state, state(99, 1))
        XCTAssertNil(tasbeeh.holdUntil, "the turn back cancels the hold")
        XCTAssertEqual(tasbeeh.advance(to: 2), state(99, 1), "the debounced write, and no rollover")
        XCTAssertNil(tasbeeh.advance(to: 3))
    }

    func testLeavingDuringTheHoldSavesTheNextRound() {
        var tasbeeh = counter(99)
        _ = tasbeeh.tap(now: 0)
        XCTAssertEqual(tasbeeh.settle(), state(0, 2))
        XCTAssertNil(tasbeeh.holdUntil)
        XCTAssertNil(tasbeeh.writeDue)
    }

    func testOpeningOnAFinishedSetHolds() {
        var tasbeeh = counter(100, now: 10)
        XCTAssertEqual(tasbeeh.holdUntil, 11)
        XCTAssertNil(tasbeeh.writeDue)
        XCTAssertNil(tasbeeh.advance(to: 10.5))
        XCTAssertEqual(tasbeeh.advance(to: 11), state(0, 2))
    }

    func testOneWriteAfterTheLastCount() {
        var tasbeeh = counter(0)
        _ = tasbeeh.tap(now: 0)
        _ = tasbeeh.tap(now: 0.1)
        _ = tasbeeh.tap(now: 0.2)
        XCTAssertEqual(tasbeeh.writeDue, 0.5)
        XCTAssertNil(tasbeeh.advance(to: 0.45))
        XCTAssertEqual(tasbeeh.advance(to: 0.5), state(3, 1))
        XCTAssertNil(tasbeeh.advance(to: 0.9), "one write for the burst")
    }

    func testUndoAtZeroWritesNothing() {
        var tasbeeh = counter(0)
        XCTAssertFalse(tasbeeh.undo(now: 0))
        XCTAssertNil(tasbeeh.writeDue)
        XCTAssertEqual(tasbeeh.state, state(0, 1))
        var later = counter(0, round: 3)
        XCTAssertFalse(later.undo(now: 0), "never back into round 2")
        XCTAssertEqual(later.state, state(0, 3))
    }

    func testATurnAppliesEveryDetentInOrder() {
        var up = counter(31)
        XCTAssertEqual(up.turn(3, now: 0), TurnResult(events: [.tick, .partComplete(0), .tick], undos: 0))
        XCTAssertEqual(up.state, state(34, 1))
        var down = counter(1)
        XCTAssertEqual(down.turn(-3, now: 0), TurnResult(events: [], undos: 1), "one undo changed the count, at 1")
        XCTAssertEqual(down.state, state(0, 1))
        XCTAssertEqual(down.turn(0, now: 1), TurnResult(events: [], undos: 0))
    }

    /// 99 and three detents in one turn: the set, then round 2's 1, then its 2.
    func testAFastSpinPastTheEndOpensTheNextRound() {
        var tasbeeh = counter(99)
        XCTAssertEqual(tasbeeh.turn(3, now: 0), TurnResult(events: [.setComplete, .tick, .tick], undos: 0))
        XCTAssertEqual(tasbeeh.state, state(2, 2))
        XCTAssertNil(tasbeeh.holdUntil, "no hold: the set did not stand at its end")
        XCTAssertEqual(tasbeeh.advance(to: 5), state(2, 2))
    }

    /// A count inside the hold is the next round's first, as the phone's is.
    func testACountInsideTheHoldOpensTheNextRoundAtOne() {
        var tasbeeh = counter(99)
        _ = tasbeeh.tap(now: 0)
        XCTAssertEqual(tasbeeh.turn(1, now: 0.3), TurnResult(events: [.tick], undos: 0))
        XCTAssertEqual(tasbeeh.state, state(1, 2))
        XCTAssertEqual(tasbeeh.advance(to: 2), state(1, 2), "the hold never lands on the new round")
    }

    /// Settling with nothing pending answers the state as it stands, and clears the write.
    func testSettlingAfterABurstSavesItAtOnce() {
        var tasbeeh = counter(10)
        _ = tasbeeh.tap(now: 0)
        XCTAssertEqual(tasbeeh.settle(), state(11, 1))
        XCTAssertNil(tasbeeh.nextDeadline)
        XCTAssertNil(tasbeeh.advance(to: 1))
    }

    /// "Reset the count for this dhikr?" (plan 4 erratum p4 #7, the Galaxy's Reset row): back to 0
    /// of round 1, any hold cancelled, and saved at once.
    func testResetGoesBackToRoundOneAndIsSavedAtOnce() {
        var tasbeeh = counter(99, round: 4)
        _ = tasbeeh.tap(now: 0)
        XCTAssertEqual(tasbeeh.reset(now: 0.2), state(0, 1))
        XCTAssertEqual(tasbeeh.state, state(0, 1))
        XCTAssertNil(tasbeeh.holdUntil, "no rollover lands on the reset")
        XCTAssertNil(tasbeeh.writeDue, "saved at once")
        XCTAssertNil(tasbeeh.advance(to: 5))
    }

    /// A count kept on the watch above a target the phone has since lowered is held to it, as the
    /// phone's screen holds it, and a set so held is finished and holds.
    func testACountAboveTheTargetIsHeldToIt() {
        let own = TasbeehPresets.custom(WatchPhrase(id: "custom_1", phrase: "Mine", target: 33))
        let tasbeeh = counter(50, round: 2, preset: own, now: 0)
        XCTAssertEqual(tasbeeh.state, state(33, 2, "custom_1"))
        XCTAssertEqual(tasbeeh.holdUntil, 1)
        XCTAssertEqual(counter(-4, round: 0).state, state(0, 1), "never below 0, never before round 1")
    }

    /// A phrase with nothing to count counts nothing, writes nothing and never rolls over.
    func testAPhraseOfNothingCountsNothing() {
        let nought = TasbeehPreset(id: "custom_0", parts: [DhikrPart(dhikr: Dhikr(id: "custom_0", arabic: "Nought"), count: 0)], custom: true)
        var tasbeeh = counter(0, preset: nought)
        XCTAssertNil(tasbeeh.holdUntil)
        XCTAssertEqual(tasbeeh.turn(2, now: 0), TurnResult(events: [], undos: 0))
        XCTAssertNil(tasbeeh.writeDue)
        XCTAssertEqual(tasbeeh.state, state(0, 1, "custom_0"))
    }
}
