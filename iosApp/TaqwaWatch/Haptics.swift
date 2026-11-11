import WatchKit

/// The watch's haptics (plan 4, the Lead's call 7): a count is `.click`, a turn back
/// `.directionDown`, a part's end `.directionUp` and the set's end `.success`. Which one a turn plays,
/// if any, is `TasbeehFeel`'s choice; this only plays it.
enum Haptics {
    static func play(_ haptic: TasbeehFeel.Haptic) {
        WKInterfaceDevice.current().play(type(of: haptic))
    }

    static func type(of haptic: TasbeehFeel.Haptic) -> WKHapticType {
        switch haptic {
        case .count: return .click
        case .undo: return .directionDown
        case .part: return .directionUp
        case .set: return .success
        }
    }
}
