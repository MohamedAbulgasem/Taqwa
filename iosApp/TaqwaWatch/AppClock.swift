import Foundation

/// The instant the app's surfaces are drawn for, in epoch seconds (plan 4 Task 5). Only the
/// instant is read here: every time shown is the feed's, and nothing is formatted on the watch.
///
/// [at] maps a date of the watch's own clock to the app's instant: Today's `TimelineView` draws
/// each of its dates through it, the frames the always-on screen draws ahead of time among them
/// (plan 4 Task 7), so a frame shows its own instant and never the moment it was drawn at.
protocol AppClock {
    func at(_ date: Date) -> Int64
}

extension AppClock {
    /// The instant now.
    func now() -> Int64 { at(Date()) }
}

/// The watch's own clock.
struct SystemClock: AppClock {
    func at(_ date: Date) -> Int64 { Int64(date.timeIntervalSince1970.rounded(.down)) }
}

#if DEBUG
/// The DEBUG clock of `-taqwaAt HH:MM`: it starts at `start` and ticks with the watch's own, or
/// stands still with `-taqwaFrozen 1`, so a shot or a UI test sees the minute it asked for.
final class ScriptedClock: AppClock {
    private let start: Int64
    private let frozen: Bool
    private let launchedAt: Int64

    init(start: Int64, frozen: Bool, launchedAt: Int64 = SystemClock().now()) {
        self.start = start
        self.frozen = frozen
        self.launchedAt = launchedAt
    }

    func at(_ date: Date) -> Int64 { frozen ? start : start + (SystemClock().at(date) - launchedAt) }
}
#endif
