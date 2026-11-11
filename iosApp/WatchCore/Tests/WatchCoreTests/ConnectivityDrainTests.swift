import Foundation
import XCTest
@testable import WatchCore

/// When a WatchConnectivity background task may end (plan 4 Task 5): Apple asks the app to keep
/// it open until the session has activated and every pending item has been received, and cancels
/// the task when it runs out of time, so the wait ends then too (review round 1).
final class ConnectivityDrainTests: XCTestCase {

    func testNotActivatedIsNotDrained() {
        // Before activation `hasContentPending` can already read false.
        XCTAssertFalse(ConnectivityDrain.isDrained(activated: false, contentPending: false, intakesInFlight: 0))
    }

    func testContentPendingIsNotDrained() {
        XCTAssertFalse(ConnectivityDrain.isDrained(activated: true, contentPending: true, intakesInFlight: 0))
    }

    func testAnIntakeInFlightIsNotDrained() {
        XCTAssertFalse(ConnectivityDrain.isDrained(activated: true, contentPending: false, intakesInFlight: 1))
        XCTAssertFalse(ConnectivityDrain.isDrained(activated: true, contentPending: false, intakesInFlight: 2))
    }

    func testActivatedWithNothingLeftIsDrained() {
        XCTAssertTrue(ConnectivityDrain.isDrained(activated: true, contentPending: false, intakesInFlight: 0))
        XCTAssertEqual(ConnectivityDrain.limit, 10)
    }

    // MARK: - When the wait ends

    func testUndrainedWithinTheLimitKeepsWaiting() {
        XCTAssertNil(ConnectivityDrain.end(drained: false, cancelled: false, elapsed: 0))
        XCTAssertNil(ConnectivityDrain.end(drained: false, cancelled: false, elapsed: 9.999))
    }

    func testADrainedSessionEndsTheWait() {
        XCTAssertEqual(ConnectivityDrain.end(drained: true, cancelled: false, elapsed: 0), .drained)
        XCTAssertEqual(ConnectivityDrain.end(drained: true, cancelled: false, elapsed: 12), .drained)
    }

    func testACancelledTaskEndsTheWait() {
        // Apple: "If the action closure has not returned when the task runs out of time to
        // complete, the system cancels the task." The closure then returns at once.
        XCTAssertEqual(ConnectivityDrain.end(drained: false, cancelled: true, elapsed: 0), .cancelled)
        XCTAssertEqual(ConnectivityDrain.end(drained: false, cancelled: true, elapsed: 9.5), .cancelled)
        XCTAssertEqual(ConnectivityDrain.end(drained: false, cancelled: true, elapsed: 10), .cancelled)
    }

    func testDrainedIsSaidOverCancelled() {
        XCTAssertEqual(ConnectivityDrain.end(drained: true, cancelled: true, elapsed: 3), .drained)
    }

    func testTheLimitEndsAWaitThatNeverDrains() {
        XCTAssertEqual(ConnectivityDrain.end(drained: false, cancelled: false, elapsed: 10), .limit)
        XCTAssertEqual(ConnectivityDrain.end(drained: false, cancelled: false, elapsed: 30), .limit)
        XCTAssertEqual(ConnectivityDrain.end(drained: false, cancelled: false, elapsed: 0.2, limit: 0.2), .limit)
    }

    func testTheEndsAreTheLoggedWords() {
        XCTAssertEqual(ConnectivityDrain.End.drained.rawValue, "drained")
        XCTAssertEqual(ConnectivityDrain.End.cancelled.rawValue, "cancelled")
        XCTAssertEqual(ConnectivityDrain.End.limit.rawValue, "limit")
    }

    // MARK: - The wait itself, on real tasks

    func testAWaitIsDrainedAsSoonAsItLooks() async {
        let ended = await ConnectivityDrain.wait(until: { true }, waiter: DrainWaiter())
        XCTAssertEqual(ended.end, .drained)
        XCTAssertLessThan(ended.seconds, 1)
    }

    func testACancelledWaitEndsAtOnceNotAtThePoll() async {
        // A 5 s poll and a 10 s limit: only the cancellation can end this wait inside a second, so
        // a pause that cancellation does not wake (a bare `withCheckedContinuation`) fails it.
        let waiter = DrainWaiter()
        let task = Task { await ConnectivityDrain.wait(until: { false }, waiter: waiter, limit: 10, poll: 5) }
        try? await Task.sleep(nanoseconds: 100_000_000)
        task.cancel()
        let ended = await task.value
        XCTAssertEqual(ended.end, .cancelled)
        XCTAssertLessThan(ended.seconds, 1)
    }

    func testAWaitInATaskAlreadyCancelledEndsAtOnce() async {
        let task = Task { () -> (end: ConnectivityDrain.End, seconds: Double) in
            withUnsafeCurrentTask { $0?.cancel() }
            return await ConnectivityDrain.wait(until: { false }, waiter: DrainWaiter(), limit: 10, poll: 5)
        }
        let ended = await task.value
        XCTAssertEqual(ended.end, .cancelled)
        XCTAssertLessThan(ended.seconds, 1)
    }

    func testAPauseInATaskAlreadyCancelledReturnsAtOnce() async {
        // The cancellation handler runs before the pause's continuation exists; the pause must
        // still not sleep out its time.
        let waiter = DrainWaiter()
        let task = Task { () -> Double in
            withUnsafeCurrentTask { $0?.cancel() }
            let began = Date()
            await waiter.pause(5)
            return Date().timeIntervalSince(began)
        }
        let seconds = await task.value
        XCTAssertLessThan(seconds, 1)
    }

    func testAWakeEndsThePauseAndTheWaitLooksAgain() async {
        let flag = Flag()
        let waiter = DrainWaiter()
        let task = Task { await ConnectivityDrain.wait(until: { flag.value }, waiter: waiter, limit: 10, poll: 5) }
        try? await Task.sleep(nanoseconds: 100_000_000)
        flag.value = true
        waiter.wakeAll()
        let ended = await task.value
        XCTAssertEqual(ended.end, .drained)
        XCTAssertLessThan(ended.seconds, 1)
    }

    func testTheLimitEndsAWaitOnTime() async {
        let ended = await ConnectivityDrain.wait(until: { false }, waiter: DrainWaiter(), limit: 0.3, poll: 0.05)
        XCTAssertEqual(ended.end, .limit)
        XCTAssertGreaterThanOrEqual(ended.seconds, 0.3)
        XCTAssertLessThan(ended.seconds, 2)
    }

    func testAPauseEndsAfterItsTime() async {
        let began = Date()
        await DrainWaiter().pause(0.1)
        let seconds = Date().timeIntervalSince(began)
        XCTAssertGreaterThanOrEqual(seconds, 0.09)
        XCTAssertLessThan(seconds, 2)
    }
}

/// A flag two tasks share, under a lock.
private final class Flag: @unchecked Sendable {
    private let lock = NSLock()
    private var stored = false

    var value: Bool {
        get { lock.lock(); defer { lock.unlock() }; return stored }
        set { lock.lock(); stored = newValue; lock.unlock() }
    }
}
