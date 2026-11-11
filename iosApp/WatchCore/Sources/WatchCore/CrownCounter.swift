import Foundation

/// The Digital Crown in whole detents (plan 4 Task 8): the counter's page reads the crown through
/// `digitalCrownRotation(detent:)`, a value that moves a whole step a detent, and hands each new
/// value here. The answer is the detents turned since the last value, `+n` counts and `-n` takes
/// away, as `wearApp`'s `RotaryCounter` reads the Galaxy's bezel.
///
/// The first value seeds it and is no turn, so wherever the crown stands when the page first sees
/// it nothing is counted; `reseed()` starts it again, when another phrase is chosen. A value is read
/// as the detent it is nearest, so a part of a detent each way never adds up to a count.
struct CrownCounter: Equatable {
    private var detent: Int?

    /// The whole detents from the last value to [value].
    mutating func turned(to value: Double) -> Int {
        guard value.isFinite else { return 0 }
        // The page's crown runs from -1,000,000 to 1,000,000; held well inside a 32-bit Int, the
        // watch's, whatever it is handed.
        let now = Int(min(max(value, -1e9), 1e9).rounded(.toNearestOrAwayFromZero))
        defer { detent = now }
        guard let last = detent else { return 0 }
        return now - last
    }

    /// The next value seeds the counter again.
    mutating func reseed() {
        detent = nil
    }
}
