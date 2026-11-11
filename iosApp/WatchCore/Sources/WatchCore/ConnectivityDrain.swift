import Foundation

/// When the watch app may let a WatchConnectivity background task end (plan 4 Task 5). Apple asks
/// that `WKWatchConnectivityRefreshBackgroundTask` be kept open until the session has activated and
/// every pending item has been received; before activation `hasContentPending` can already read
/// false. The watch also waits for its own intakes, so the task ends only once the feed is stored
/// and the timelines are reloaded.
///
/// The wait also ends as soon as the system cancels the task. Apple's `backgroundTask(_:action:)`:
/// "If the action closure has not returned when the task runs out of time to complete, the system
/// cancels the task." A closure that waits on past that risks the app's termination and a smaller
/// budget at its next wake (review round 1).
enum ConnectivityDrain {
    /// The longest the app keeps a background task open for it, in seconds.
    static let limit: Double = 10

    /// How long the wait sleeps between looks when no delegate callback wakes it, in seconds.
    static let poll: Double = 0.1

    /// How a wait ended, in the words `TaqwaWatch drain <end> <ms> ms` logs.
    enum End: String {
        case drained, cancelled, limit
    }

    /// True only when the session is activated, nothing is pending and no intake is in flight.
    static func isDrained(activated: Bool, contentPending: Bool, intakesInFlight: Int) -> Bool {
        activated && !contentPending && intakesInFlight == 0
    }

    /// How the wait ends now, or nil while it goes on: `.drained` once the session is (said even
    /// when the task was cancelled too, since the work is done), `.cancelled` as soon as the task is,
    /// and `.limit` once `limit` seconds have passed.
    static func end(drained: Bool, cancelled: Bool, elapsed: Double, limit: Double = limit) -> End? {
        if drained { return .drained }
        if cancelled { return .cancelled }
        if elapsed >= limit { return .limit }
        return nil
    }

    /// Waits until `drained()` holds, the current task is cancelled, or `limit` seconds have passed,
    /// looking again after each `waiter.wakeAll()` and every `poll` seconds. Returns how it ended
    /// and the seconds it took.
    static func wait(
        until drained: () -> Bool, waiter: DrainWaiter, limit: Double = limit, poll: Double = poll
    ) async -> (end: End, seconds: Double) {
        let began = DispatchTime.now()
        while true {
            let seconds = Double(DispatchTime.now().uptimeNanoseconds - began.uptimeNanoseconds) / 1_000_000_000
            if let ending = end(drained: drained(), cancelled: Task.isCancelled, elapsed: seconds, limit: limit) {
                return (ending, seconds)
            }
            await waiter.pause(min(poll, limit - seconds))
        }
    }
}

/// The pauses of `ConnectivityDrain.wait`. Each one ends after its time, at the next `wakeAll()`
/// (a delegate callback handled, an intake finished), or as soon as its task is cancelled,
/// whichever comes first. A bare `withCheckedContinuation` never ends early on cancellation, so the
/// cancellation handler resumes it.
///
/// It is `@unchecked Sendable` because the pauses, the wakes and the cancellation handlers run on
/// different threads: `waiters` is read and written under `lock` alone, and a continuation is
/// removed under it before it is resumed, so none is resumed twice.
final class DrainWaiter: @unchecked Sendable {
    private let lock = NSLock()
    private var waiters: [UUID: CheckedContinuation<Void, Never>] = [:]

    init() {}

    /// Waits `seconds`, or until `wakeAll()`, or until the current task is cancelled.
    func pause(_ seconds: Double) async {
        let id = UUID()
        await withTaskCancellationHandler {
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                lock.lock()
                waiters[id] = continuation
                lock.unlock()
                // A task cancelled before this pause began ran its handler at once, when there was
                // nothing yet to wake: it is woken here instead.
                if Task.isCancelled {
                    wake(id)
                    return
                }
                DispatchQueue.global().asyncAfter(deadline: .now() + max(0, seconds)) { [weak self] in
                    self?.wake(id)
                }
            }
        } onCancel: {
            wake(id)
        }
    }

    /// Ends every pause now waiting.
    func wakeAll() {
        lock.lock()
        let all = waiters
        waiters = [:]
        lock.unlock()
        all.values.forEach { $0.resume() }
    }

    private func wake(_ id: UUID) {
        lock.lock()
        let continuation = waiters.removeValue(forKey: id)
        lock.unlock()
        continuation?.resume()
    }
}
