import Foundation

/// What one crown turn did: the events of its counts that changed the count, in order, and how many
/// of its turns back changed it.
struct TurnResult: Equatable {
    var events: [TapEvent]
    var undos: Int
}

/// The counter of one phrase (plan 4 Task 8): the phone's `TasbeehViewModel` hold and debounce
/// (`TasbeehViewModel.kt`, its `holdJob`, `debounceJob`, `settle` and `flush`) as a state machine on
/// explicit time, so that `swift test` reaches it. Time is seconds on a monotonic clock
/// (`ProcessInfo.systemUptime` in the app), never shown. The page keeps one pending task that sleeps
/// until `nextDeadline`, then calls `advance(to:)` and saves what it answers; it holds no hold,
/// debounce or haptic rule of its own.
///
/// - A finished set holds its full ring for `TasbeehTiming.setCompleteHold`, then rolls over to 0
///   of the next round, which is saved at once. A count or a turn back inside the hold cancels it:
///   a count opens the next round at 1, as the phone's does, and a turn back stays in the round.
/// - Each change is saved `TasbeehTiming.writeDebounce` after the last of its burst, once.
/// - `settle()` is the phone's `flush`: a pending hold rolls over first, so what is saved on the way
///   out is the round about to open, never the hundred.
/// - A change that changes nothing (a turn back at 0, a count of a phrase with nothing to count) is
///   no change: nothing is saved for it.
///
/// It is the Galaxy's `WatchTasbeeh` rule too (plan 3 Task 6), its twin on the other watch.
struct TasbeehCounter: Equatable {
    let preset: TasbeehPreset
    private(set) var state: TasbeehState
    /// When a finished set rolls over, or nil.
    private(set) var holdUntil: Double?
    /// When the burst's write is due, or nil.
    private(set) var writeDue: Double?

    /// The earlier of the two deadlines: when `advance(to:)` next has something to do.
    var nextDeadline: Double? {
        switch (holdUntil, writeDue) {
        case let (hold?, write?): return min(hold, write)
        case let (hold?, nil): return hold
        case let (nil, write?): return write
        case (nil, nil): return nil
        }
    }

    /// The counter of [preset] at [state], read back from the store: held to the phone's bounds (the
    /// count 0 to the total, a target lowered on the phone included, the round from 1), as the
    /// Galaxy's `TasbeehCodec` reads a count back. A state opened at its total holds, as the phone's
    /// `holdIfComplete` does, so a set left inside its hold rolls over when it is seen again.
    init(preset: TasbeehPreset, state: TasbeehState, now: Double) {
        self.preset = preset
        self.state = TasbeehState(presetId: preset.id, count: min(max(state.count, 0), max(preset.total, 0)), round: max(state.round, 1))
        holdIfComplete(now: now)
    }

    /// One count: a tap, or a detent of the crown turned up.
    mutating func tap(now: Double) -> TapEvent {
        let (next, event) = TasbeehEngine.tap(state, preset)
        guard next != state else { return event }
        holdUntil = nil
        state = next
        writeDue = now + TasbeehTiming.writeDebounce
        if event == .setComplete { holdUntil = now + TasbeehTiming.setCompleteHold }
        return event
    }

    /// One count taken away: a detent of the crown turned back, or VoiceOver's "Take one away".
    /// False at 0, with nothing changed.
    mutating func undo(now: Double) -> Bool {
        guard let back = TasbeehEngine.undo(state, preset) else { return false }
        holdUntil = nil
        state = back
        writeDue = now + TasbeehTiming.writeDebounce
        return true
    }

    /// [detents] of the crown, in order: a count for each one up, a turn back for each one down. A
    /// tap on the screen is `turn(1, now:)`.
    mutating func turn(_ detents: Int, now: Double) -> TurnResult {
        var result = TurnResult(events: [], undos: 0)
        if detents > 0 {
            for _ in 0..<detents {
                let before = state
                let event = tap(now: now)
                if state != before { result.events.append(event) }
            }
        } else if detents < 0 {
            for _ in 0..<(-detents) where undo(now: now) {
                result.undos += 1
            }
        }
        return result
    }

    /// The state to save now, or nil. A hold that is due rolls over (`rolledOver`'s guard: nothing
    /// for a total of 0 or a count below the total, so a 1 of round 2 stays a 1), and a rollover
    /// that moved the count is saved at once. A write that is due is saved. When both are due the
    /// answer is the state after the rollover.
    mutating func advance(to now: Double) -> TasbeehState? {
        var save: TasbeehState?
        if let write = writeDue, now >= write {
            writeDue = nil
            save = state
        }
        if let hold = holdUntil, now >= hold {
            holdUntil = nil
            if rollOver() {
                writeDue = nil
                save = state
            }
        }
        return save
    }

    /// The phone's `flush`, when the page is left (the app leaving the screen, a swipe to another
    /// page, another phrase chosen): a pending hold rolls over first, both deadlines clear, and the
    /// answer is what to save.
    mutating func settle() -> TasbeehState {
        if holdUntil != nil {
            holdUntil = nil
            _ = rollOver()
        }
        writeDue = nil
        return state
    }

    /// "Reset the count for this dhikr?" (the Galaxy's Reset row, plan 4 erratum p4 #7): 0 of round
    /// 1, any hold cancelled, and the answer saved at once.
    mutating func reset(now: Double) -> TasbeehState {
        holdUntil = nil
        writeDue = nil
        state = TasbeehEngine.reset(state)
        return state
    }

    /// A finished set becomes 0 of the next round; anything else is left as it is. True when it moved.
    private mutating func rollOver() -> Bool {
        guard preset.total > 0, state.count >= preset.total else { return false }
        state = TasbeehEngine.nextRound(state)
        return true
    }

    private mutating func holdIfComplete(now: Double) {
        if preset.total > 0, state.count >= preset.total { holdUntil = now + TasbeehTiming.setCompleteHold }
    }
}
