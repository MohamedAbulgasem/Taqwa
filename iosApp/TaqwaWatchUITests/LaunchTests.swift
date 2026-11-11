import XCTest

/// The watch app's launch (plan 4, Tasks 4 and 5): before it holds any feed, the one line it shows
/// is its own "Open Taqwa on your phone", from the watch target's own table in the watch's language
/// (spec 1.3); a newer format seen says "Update Taqwa on your watch" first (plan 3's call 5); and a
/// DEBUG sample, once stored, is the feed held.
///
/// Every UI test sets its whole state by launch arguments (plan 4 erratum p4 #9), so none depends
/// on what an earlier test or run left in the simulator's containers:
/// - `-taqwaNoFeed 1` empties the store at launch (the feed, its delivery and the notice);
/// - `-taqwaSampleFeed <name>` stores that sample, moved to today, which clears the notice;
/// - `-taqwaNewerSeen 1` then leaves the newer-version notice, as a version-2 feed would;
/// - `-AppleLanguages (en)` and `-AppleLocale en_US` pin the watch's own language to English, the
///   table these tests read, whatever language the simulator was left in.
/// Each of the store's arguments also turns the phone's feed and the handshake off for the run
/// (erratum p4 #8), so a paired phone can never replace the state a test set.
///
/// The watch's UI tests run on the Mac only, on the SE 3 (40mm): an unsigned CI build has no app
/// group, so it could not store the sample feeds the tests launch with.
final class LaunchTests: XCTestCase {
    private static let english = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]

    override func setUp() {
        continueAfterFailure = false
    }

    func testTheAppOpensOnTheFirstRunLine() {
        let app = launch(["-taqwaNoFeed", "1"])

        XCTAssertTrue(
            app.staticTexts["Open Taqwa on your phone"].waitForExistence(timeout: 30),
            "the first run shows the watch's own line, and VoiceOver reads it as one element"
        )
    }

    func testANewerFormatSeenWithNoFeedSaysUpdate() {
        let app = launch(["-taqwaNoFeed", "1", "-taqwaNewerSeen", "1"])

        XCTAssertTrue(
            app.staticTexts["Update Taqwa on your watch"].waitForExistence(timeout: 30),
            "the notice comes first, with no feed as with one"
        )
        XCTAssertFalse(app.staticTexts["Open Taqwa on your phone"].exists)
    }

    func testASampleFeedIsTheFeedHeld() {
        let app = launch(["-taqwaSampleFeed", "capetown"])

        XCTAssertTrue(app.staticTexts.firstMatch.waitForExistence(timeout: 30), "the app has drawn")
        XCTAssertFalse(app.staticTexts["Open Taqwa on your phone"].exists, "a feed is held")
        XCTAssertFalse(app.staticTexts["Update Taqwa on your watch"].exists, "and the sample cleared the notice")
    }

    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments + Self.english
        app.launch()
        return app
    }
}
