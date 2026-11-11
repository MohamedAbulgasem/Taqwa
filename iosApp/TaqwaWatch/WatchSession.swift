import Foundation
import WatchConnectivity

/// The watch's half of the link (plan 4 Task 5, the Lead's call 4, Ruling 50): `WCSession` on the
/// watch, which takes in the application context the iPhone's `TaqwaWatchSession` hands over,
/// stores it only when it moves the watch forward (`FeedIntake`, `FeedStore`), and shakes hands
/// when the phone may hold a newer feed. The watch sends nothing but the handshake's echo of the
/// delivery it holds: a random per-install number, a counter and a fingerprint (Ruling 39).
///
/// - **Intake.** At activation it takes in `receivedApplicationContext`, and then each
///   `didReceiveApplicationContext` (`FeedIntake.take`). An intake is in flight from the start of
///   `accept` until its store write and, when `FeedIntake.reloadsWidgets` says so (`.accepted`, or
///   a `.newerVersion` whose notice has just come), `NextPrayerWidgets.reload()` (every timeline,
///   and the Smart Stack's relevance) have returned (`.unchanged`, `.stale` and a newer format
///   already noticed reload nothing).
///   Each feed stored, and each newer format seen, is then told on the main thread
///   (`feedChanged`), so `AppModel` reads the store again.
/// - **The handshake**, `sendMessage([refresh: <echo>], replyHandler:)` while the phone is
///   reachable and `HandshakePolicy` says so: at activation, once the session has drained (Apple
///   delivers a context already waiting "shortly after you activate", and a handshake sent before
///   it would echo the older delivery and cost the iPhone a redundant send), when the phone
///   becomes reachable, and when the app opens. It is stamped as it goes out, and the stamp is
///   taken back if it fails, so the next chance asks again.
/// - **`drain()`** holds a WatchConnectivity background task open until the session is
///   activated, nothing is pending and no intake is in flight (`ConnectivityDrain`), checked
///   after each delegate callback and every 0.1 s, or for `ConnectivityDrain.limit` seconds, and
///   no longer than the system allows: when watchOS cancels the task for running out of time,
///   the wait ends at once (`ConnectivityDrain.wait`, `DrainWaiter`), as Apple's
///   `backgroundTask(_:action:)` asks.
///
/// Every decision is logged as `NSLog("%@", "TaqwaWatch …")`, no value ever in the format string,
/// with the result, the delivery's number and fingerprint (`<seq>/<fingerprint>`, `none` for no
/// delivery) and the day count: never the delivery's origin, the city or the bearing (Ruling 52
/// item 4: these are release log lines).
///
/// ```
/// xcrun simctl spawn <watch> log show --last 10m --predicate 'eventMessage CONTAINS "TaqwaWatch"'
/// ```
///
/// It is `@unchecked Sendable` because WatchConnectivity calls it on its own queue and `drain()`
/// waits on another: every mutable field is read and written under `lock` (or `askLock`, or the
/// waiter's own lock), and the DEBUG `intakeOff` is set once, in `start()`, before the session is
/// activated.
final class WatchSession: NSObject, WCSessionDelegate, @unchecked Sendable {
    static let shared = WatchSession()

    /// Posted on the main thread after a feed is stored or a newer format is seen.
    static let feedChanged = Notification.Name("TaqwaWatchFeedChanged")

    private let store = FeedStore.appGroup()
    private let clock: AppClock = SystemClock()
    private let lock = NSLock()
    private var intakesInFlight = 0
    /// The drains' pauses, woken by each delegate callback and each intake's end.
    private let waiter = DrainWaiter()
    /// Taken around each handshake's decision and stamp, so two moments at once ask once.
    private let askLock = NSLock()
    private var started = false
    #if DEBUG
    /// A DEBUG launch that set the store's state takes in no context and shakes no hands
    /// (plan 4 erratum p4 #8), so the state it set is the state looked at.
    private var intakeOff = false
    #endif

    private var supported: Bool { WCSession.isSupported() }

    /// Activates the session, once per process: `TaqwaWatchApp.init` calls it, at every launch.
    func start() {
        lock.lock()
        let first = !started
        started = true
        lock.unlock()
        guard first else { return }
        #if DEBUG
        intakeOff = DebugLaunch.arguments.holdsState
        if intakeOff { log("TaqwaWatch intake off (debug sample)") }
        #endif
        guard supported else {
            log("TaqwaWatch not supported: no session")
            return
        }
        let session = WCSession.default
        session.delegate = self
        session.activate()
        log("TaqwaWatch activate() asked")
    }

    /// Returns once the session is drained, as soon as the calling task is cancelled (watchOS
    /// cancels a background task that runs out of time), or after `ConnectivityDrain.limit`
    /// seconds. It logs `TaqwaWatch drain <drained|cancelled|limit> <ms> ms`.
    func drain() async {
        let waited = await ConnectivityDrain.wait(until: isDrained, waiter: waiter)
        log("TaqwaWatch drain \(waited.end.rawValue) \(Int(waited.seconds * 1000)) ms")
    }

    /// The app came to the screen: a handshake when the feed needs one (`AskWhy.opened`).
    func opened() {
        shakeHands(.opened)
    }

    // MARK: - WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        let activated = activationState == .activated
        log("TaqwaWatch activated=\(activated) state=\(activationState.rawValue) error=\(error.map { "\(type(of: $0))" } ?? "none")")
        if activated { intake(session.receivedApplicationContext, via: "activation") }
        wakeAll()
        guard activated else { return }
        Task {
            await self.drain()
            self.shakeHands(.activated)
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        intake(applicationContext, via: "context")
        wakeAll()
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        log("TaqwaWatch reachable=\(session.isReachable)")
        wakeAll()
        if session.isReachable { shakeHands(.reachable) }
    }

    #if DEBUG
    /// The iPhone's DEBUG size probe (`WatchHarness`, plan 4 Task 5B): each `transferUserInfo` it
    /// sends is delivered here, logged by the size of its `probe` value, and dropped. The phone
    /// sends the watch nothing else this way, so a release build does not implement it.
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        let bytes = (userInfo["probe"] as? Data).map { "\($0.count)" } ?? "none"
        log("TaqwaWatch userInfo \(bytes)")
        wakeAll()
    }
    #endif

    // MARK: - Intake

    private func intake(_ context: [String: Any], via: String) {
        #if DEBUG
        if intakeOff {
            log("TaqwaWatch intake off (debug sample), \(context.isEmpty ? "no context" : "a context") dropped via \(via)")
            return
        }
        #endif
        begin()
        defer { end() }
        do {
            let taken = try FeedIntake.take(context, into: store)
            let result = taken.result
            let days = result == .accepted || result == .unchanged ? store.read()?.days.count : nil
            if taken.reloadsWidgets { NextPrayerWidgets.reload() }
            log(FeedIntake.line(result, context: context, days: days, via: via))
            if result == .accepted || result == .unchanged || result == .newerVersion { publish() }
        } catch {
            let delivery = (context[FeedIntake.deliveryKey] as? String).flatMap(FeedIntake.deliveryOf)?.logged ?? "none"
            log("TaqwaWatch store failed \(delivery) via \(via): \(type(of: error))")
        }
    }

    private func publish() {
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: Self.feedChanged, object: nil)
        }
    }

    // MARK: - The handshake

    private func shakeHands(_ why: AskWhy) {
        #if DEBUG
        if intakeOff {
            log("TaqwaWatch handshake \(why) off (debug sample)")
            return
        }
        #endif
        guard supported else { return }
        let session = WCSession.default
        guard session.activationState == .activated else {
            log("TaqwaWatch handshake \(why) skipped: not activated")
            return
        }
        askLock.lock()
        let now = clock.now()
        guard HandshakePolicy.shouldAsk(why, feed: store.read(), now: now, lastAskedAt: store.lastAskedAt()) else {
            askLock.unlock()
            log("TaqwaWatch handshake \(why) skipped")
            return
        }
        guard session.isReachable else {
            askLock.unlock()
            log("TaqwaWatch handshake \(why) skipped: the phone is not reachable")
            return
        }
        let message = FeedIntake.handshake(store)
        store.stampAsked(now)
        askLock.unlock()
        let have = FeedIntake.echoLine(message[FeedIntake.refreshKey] as? String ?? "")
        log("TaqwaWatch handshake \(why) have=\(have)")
        session.sendMessage(message, replyHandler: { reply in
            let queued = (reply[FeedIntake.replyKey] as? Bool).map { "\($0)" } ?? "none"
            self.log("TaqwaWatch handshake \(why) answered queued=\(queued)")
        }, errorHandler: { error in
            self.store.unstampAsked(ifAt: now)
            self.log("TaqwaWatch handshake \(why) failed: \((error as NSError).domain) \((error as NSError).code)")
        })
    }

    // MARK: - Draining

    private func isDrained() -> Bool {
        guard supported else { return true }
        let session = WCSession.default
        lock.lock()
        let inFlight = intakesInFlight
        lock.unlock()
        return ConnectivityDrain.isDrained(
            activated: session.activationState == .activated, contentPending: session.hasContentPending, intakesInFlight: inFlight
        )
    }

    private func begin() {
        lock.lock()
        intakesInFlight += 1
        lock.unlock()
    }

    private func end() {
        lock.lock()
        intakesInFlight -= 1
        lock.unlock()
        wakeAll()
    }

    /// Wakes every drain now waiting, so it looks again at once.
    private func wakeAll() {
        waiter.wakeAll()
    }

    private func log(_ line: String) {
        NSLog("%@", line)
    }
}
