import XCTest
@testable import WatchCore

/// When the watch shakes hands with its iPhone (plan 4 Task 5): at activation and when the phone
/// becomes reachable, and on opening only when the feed needs one, never twice within a minute.
final class HandshakePolicyTests: XCTestCase {

    /// Sunday 18 October 2026, 14:58:24 in Cape Town: the sample is fresh, with weeks left.
    private let now: Int64 = 1_792_328_304

    func testActivationAsksAtMostOnceAMinute() throws {
        let feed = try StoreFixtures.sample
        XCTAssertEqual(HandshakePolicy.gap, 60)
        XCTAssertTrue(HandshakePolicy.shouldAsk(.activated, feed: feed, now: now, lastAskedAt: nil))
        XCTAssertFalse(HandshakePolicy.shouldAsk(.activated, feed: feed, now: now, lastAskedAt: now))
        XCTAssertFalse(HandshakePolicy.shouldAsk(.activated, feed: feed, now: now + 59, lastAskedAt: now))
        XCTAssertTrue(HandshakePolicy.shouldAsk(.activated, feed: feed, now: now + 60, lastAskedAt: now))
    }

    func testReachableAsksAtMostOnceAMinute() throws {
        let feed = try StoreFixtures.sample
        XCTAssertTrue(HandshakePolicy.shouldAsk(.reachable, feed: feed, now: now, lastAskedAt: nil))
        XCTAssertFalse(HandshakePolicy.shouldAsk(.reachable, feed: feed, now: now + 30, lastAskedAt: now))
        XCTAssertTrue(HandshakePolicy.shouldAsk(.reachable, feed: feed, now: now + 60, lastAskedAt: now))
    }

    func testOpeningAsksOnlyWhenTheFeedNeedsIt() throws {
        let feed = try StoreFixtures.sample
        XCTAssertFalse(WatchRules.needsRefresh(feed, now: now), "the premise: a fresh feed")
        XCTAssertFalse(HandshakePolicy.shouldAsk(.opened, feed: feed, now: now, lastAskedAt: nil), "a fresh feed")

        let weekOld = feed.writtenAt + 7 * 86_400 + 1
        XCTAssertTrue(WatchRules.needsRefresh(feed, now: weekOld), "the premise: a week old")
        XCTAssertTrue(HandshakePolicy.shouldAsk(.opened, feed: feed, now: weekOld, lastAskedAt: nil))
        XCTAssertFalse(HandshakePolicy.shouldAsk(.opened, feed: feed, now: weekOld, lastAskedAt: weekOld - 10), "and still once a minute")
    }

    func testNoFeedAlwaysNeedsOne() {
        for why in [AskWhy.activated, .reachable, .opened] {
            XCTAssertTrue(HandshakePolicy.shouldAsk(why, feed: nil, now: now, lastAskedAt: nil), "\(why)")
            XCTAssertTrue(HandshakePolicy.shouldAsk(why, feed: nil, now: now, lastAskedAt: now - 60), "\(why)")
            XCTAssertFalse(HandshakePolicy.shouldAsk(why, feed: nil, now: now, lastAskedAt: now - 59), "\(why): once a minute")
        }
    }

    func testAStampAheadOfTheClockHoldsNothingBack() throws {
        // The watch's clock set back: the stamp says nothing of when it last asked.
        let feed = try StoreFixtures.sample
        XCTAssertTrue(HandshakePolicy.shouldAsk(.activated, feed: feed, now: now, lastAskedAt: now + 1))
        XCTAssertTrue(HandshakePolicy.shouldAsk(.activated, feed: feed, now: now, lastAskedAt: now + 86_400))
    }
}
