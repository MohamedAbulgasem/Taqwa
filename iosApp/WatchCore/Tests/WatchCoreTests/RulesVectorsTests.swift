import XCTest
@testable import WatchCore

/// The rules against `watch-rules-vectors.json`, plan 1's file "for the watchOS port": the same
/// feeds, the same instants and Kotlin's answers, which the port must give exactly.
final class RulesVectorsTests: XCTestCase {

    private func feed(_ name: String) throws -> WatchFeed {
        guard let feed = Vectors.rules.feeds[name] else { throw VectorError("no feed \(name)") }
        return feed
    }

    private func vector(_ start: WatchStart?) -> StartVector? {
        start.map { StartVector(prayer: $0.prayer.rawValue, at: $0.at, dayIndex: $0.dayIndex) }
    }

    func testEveryRuleCaseMatchesTheVectors() throws {
        let tolerance = Vectors.rules.tolerance.progress
        for rule in Vectors.nonEmpty(Vectors.rules.cases, "rules' cases") {
            let feed = try feed(rule.feed)
            let moment = WatchRules.moment(feed, now: rule.now)
            XCTAssertEqual(vector(moment?.next), rule.next, "next: \(rule.name)")
            XCTAssertEqual(vector(moment?.previous), rule.previous, "previous: \(rule.name)")
            XCTAssertEqual(moment?.secondsLeft, rule.secondsLeft, "secondsLeft: \(rule.name)")
            XCTAssertEqual(moment.map { WatchRules.countdown(feed, secondsLeft: $0.secondsLeft) }, rule.countdown, "countdown: \(rule.name)")
            switch (moment?.progress, rule.progress) {
            case (nil, nil):
                break
            case let (found?, wanted?):
                XCTAssertEqual(Double(found), wanted, accuracy: tolerance, "progress: \(rule.name)")
            default:
                XCTFail("progress: \(rule.name): \(String(describing: moment?.progress)) where \(String(describing: rule.progress)) is wanted")
            }
            XCTAssertEqual(WatchRules.litOnList(feed, now: rule.now)?.rawValue, rule.litOnList, "litOnList: \(rule.name)")
            XCTAssertEqual(WatchRules.litOnGlance(feed, now: rule.now)?.rawValue, rule.litOnGlance, "litOnGlance: \(rule.name)")
        }
    }

    func testEveryCountdownMatchesTheVectors() throws {
        for countdown in Vectors.nonEmpty(Vectors.rules.countdowns, "countdowns") {
            XCTAssertEqual(
                WatchRules.countdown(try feed(countdown.feed), secondsLeft: countdown.secondsLeft),
                countdown.countdown,
                "\(countdown.feed) at \(countdown.secondsLeft) s"
            )
        }
    }

    func testEveryRefreshMatchesTheVectors() throws {
        for refresh in Vectors.nonEmpty(Vectors.rules.refreshes, "refreshes") {
            XCTAssertEqual(WatchRules.needsRefresh(try feed(refresh.feed), now: refresh.now), refresh.needsRefresh, refresh.name)
        }
    }

    /// Every feed the rules run on is a feed the decoder reads, at this build's version, and holds
    /// its text's values key by key (`TextValues`).
    func testEveryRulesFeedIsOfThisVersion() throws {
        struct Texts: Decodable { let feeds: [String: JSONValue] }
        let texts = try JSONDecoder().decode(Texts.self, from: Vectors.data("watch-rules-vectors.json")).feeds
        for (name, feed) in Vectors.nonEmpty(Array(Vectors.rules.feeds), "rules' feeds") {
            XCTAssertEqual(Int(feed.version), FeedDecoder.version, name)
            XCTAssertGreaterThan(TextValues.assertHolds(feed, texts[name], name), 0, name)
        }
    }
}
