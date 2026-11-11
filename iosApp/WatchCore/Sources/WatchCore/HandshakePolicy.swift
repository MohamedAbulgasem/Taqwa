import Foundation

/// Why the watch would shake hands with its iPhone (plan 4 Task 5).
enum AskWhy: Equatable {
    /// The session has activated, at every launch of the app, a background one included.
    case activated
    /// The phone has become reachable.
    case reachable
    /// The app has come to the screen (`AppModel`).
    case opened
}

/// When the watch shakes hands: `[refresh: <the delivery it holds>]` by `sendMessage`, which the
/// phone answers by sending the current feed unless the watch holds it (plan 2's handshake, the
/// Wear app's `RefreshPolicy` in the Apple Watch's three moments).
///
/// - Never twice within `gap`, for any reason. A stamp ahead of `now` (the watch's clock set back)
///   holds nothing back: it says nothing of when the watch last asked.
/// - `activated` and `reachable` always, past the gap: the phone damps its own answer, and says
///   `UNCHANGED` when the echo names the latest delivery.
/// - `opened` only when there is no feed or it needs one (`WatchRules.needsRefresh`: three days
///   left, or a week old; spec 2.6's "each time the app opens" once it is spent).
///
/// The stamp lives in the store's directory (`FeedStore.lastAskedAt`), in the file, never its date.
enum HandshakePolicy {
    /// The least time between two handshakes, in seconds.
    static let gap: Int64 = 60

    static func shouldAsk(_ why: AskWhy, feed: WatchFeed?, now: Int64, lastAskedAt: Int64?) -> Bool {
        if let lastAskedAt, lastAskedAt <= now, now - lastAskedAt < gap { return false }
        switch why {
        case .activated, .reachable:
            return true
        case .opened:
            guard let feed else { return true }
            return WatchRules.needsRefresh(feed, now: now)
        }
    }
}
