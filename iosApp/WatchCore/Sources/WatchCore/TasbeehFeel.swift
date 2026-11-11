import Foundation

/// One haptic a turn (plan 4, the Lead's call 7; plan 3's call 14), so the two watches feel alike.
/// A new haptic cuts the one still playing short, so a count's click straight after a part's buzz
/// would cut the buzz. Each count, turn back or crown turn plays at most one: the strongest end it
/// crossed (the set's, then a part's), else the count's click, else the turn back's; nothing when
/// nothing changed. A part's or the set's is always played and opens a quiet window, `partQuiet` or
/// `setQuiet` from then, in which no count or turn back is played, though it is still counted.
///
/// `Haptics.swift` plays them: a count `.click`, a turn back `.directionDown`, a part's end
/// `.directionUp` and the set's `.success`. Mohamed judges the feel on her watch in plan 5, and the
/// two windows may change with it.
struct TasbeehFeel: Equatable {
    enum Haptic: Equatable {
        case count, undo, part, set
    }

    /// How long a part's buzz plays, in seconds: no click within it.
    static let partQuiet = 0.14
    /// How long the set's plays.
    static let setQuiet = 0.40

    /// Until when a buzz still plays.
    private(set) var quietUntil = -Double.infinity

    /// The haptic for [result], a turn at [now], or nil.
    mutating func choose(_ result: TurnResult, now: Double) -> Haptic? {
        if result.events.contains(.setComplete) {
            quietUntil = now + Self.setQuiet
            return .set
        }
        if result.events.contains(where: { if case .partComplete = $0 { return true } else { return false } }) {
            quietUntil = now + Self.partQuiet
            return .part
        }
        let changed: Haptic? = !result.events.isEmpty ? .count : (result.undos > 0 ? .undo : nil)
        guard let changed, now >= quietUntil else { return nil }
        return changed
    }
}
