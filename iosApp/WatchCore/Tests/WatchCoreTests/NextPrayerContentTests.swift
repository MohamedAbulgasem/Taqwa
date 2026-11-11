import Foundation
import XCTest
@testable import WatchCore

/// What one entry of the next-prayer widget draws and says (plan 4 Task 6), which only the
/// gallery's shots held before (Ruling 64 item 3): the inline and corner lines, the one sentence
/// VoiceOver reads for a complication, in the feed's language, the form without the countdown, and
/// the empty form's words in plan 3's call 5 order. Every instant is the Cape Town sample's, as in
/// `GlanceTests`: its day 1 is Sunday 18 October 2026.
final class NextPrayerContentTests: XCTestCase {
    /// The watch's own words, told apart from the feed's.
    private let own = OwnLines(appName: "Taqwa (the watch's)", openOnPhone: "the watch's own open line", updateOnWatch: "the watch's own update line")

    private func capeTown(_ hours: Int64, _ minutes: Int64, _ seconds: Int64 = 0, day: Int64 = 1) -> Int64 {
        1_792_274_400 + (day - 1) * 86_400 + hours * 3_600 + minutes * 60 + seconds
    }

    /// The content of the timeline's first entry from [now].
    private func first(_ feed: WatchFeed?, newer: Bool = false, now: Int64) -> NextPrayerContent {
        let timeline = Glances.timeline(feed, newerVersionSeen: newer, now: now)
        return NextPrayerContent(timeline.entries[0], of: timeline, own: own)
    }

    /// The sample's timeline from after its last start: a spent feed.
    private func spent(_ feed: WatchFeed, newer: Bool = false) throws -> NextPrayerContent {
        let pastTheEnd = try XCTUnwrap(WatchRules.starts(feed).last).at + 60
        return first(feed, newer: newer, now: pastTheEnd)
    }

    // MARK: - A glance

    func testWithItsCountdownItSaysThePrayerTheTimeLeftAndItsTime() throws {
        let content = first(try StoreFixtures.sample, now: capeTown(14, 57, 30))

        XCTAssertTrue(content.countdownShown)
        XCTAssertEqual(content.inlineLine, "ASR in 1 h 12 min")
        XCTAssertEqual(content.cornerLine, "ASR 16:10")
        XCTAssertEqual(content.spoken(inline: false), Speech.line("Asr in 1 hour 12 minutes, at 16:10", language: "en"))
        XCTAssertEqual(content.spoken(inline: true), content.spoken(inline: false), "every family says the one sentence")
        XCTAssertEqual(content.spoken(inline: false).runs.first?.languageIdentifier, "en")
        XCTAssertEqual(content.rtl, false)
        XCTAssertTrue(content.latin)
    }

    func testWithoutTheCountdownItSaysTheRowsForm() throws {
        let content = first(try StoreFixtures.sample, now: capeTown(14, 57, 30)).withoutCountdown

        XCTAssertFalse(content.countdownShown)
        XCTAssertNotNil(content.glance, "the same glance")
        XCTAssertEqual(content.inlineLine, "ASR 16:10")
        XCTAssertEqual(content.cornerLine, "ASR 16:10")
        XCTAssertEqual(String(content.spoken(inline: false).characters), "Asr, 16:10")
        XCTAssertEqual(String(content.spoken(inline: true).characters), "Asr, 16:10")
    }

    func testTheHoursEndDrawsWithoutTheCountdown() throws {
        let now = capeTown(14, 57, 30)
        let timeline = Glances.timeline(try StoreFixtures.sample, newerVersionSeen: false, now: now)
        let end = try XCTUnwrap(timeline.entries.first { $0.at == now + Glances.horizon })

        let content = NextPrayerContent(end, of: timeline, own: own)

        XCTAssertFalse(content.countdownShown)
        XCTAssertEqual(content.inlineLine, "ASR 16:10")
        XCTAssertEqual(String(content.spoken(inline: false).characters), "Asr, 16:10")
    }

    func testAnArabicGlanceSpeaksArabic() throws {
        let content = first(try Vectors.feed("sampleArSa"), now: capeTown(14, 57, 30))

        XCTAssertEqual(content.inlineLine, "العصر بعد ١ س ١٢ د")
        XCTAssertEqual(content.cornerLine, "العصر ١٦:١٠")
        XCTAssertEqual(content.spoken(inline: false).runs.first?.languageIdentifier, "ar-SA")
        XCTAssertEqual(content.rtl, true)
        XCTAssertFalse(content.latin)
    }

    func testAnEntryWithNoGlanceNeverShowsACountdown() throws {
        let timeline = Glances.timeline(try StoreFixtures.sample, newerVersionSeen: false, now: capeTown(14, 58))
        let odd = GlanceEntry(at: capeTown(14, 58), glance: nil, relevant: false, countdownShown: true)

        XCTAssertFalse(NextPrayerContent(odd, of: timeline, own: own).countdownShown)
    }

    // MARK: - The empty form, in plan 3's call 5 order

    func testASpentFeedSaysOpenTaqwaOnYourPhoneInItsOwnWords() throws {
        let feed = try StoreFixtures.sample
        let content = try spent(feed)

        XCTAssertNil(content.glance)
        XCTAssertFalse(content.countdownShown)
        XCTAssertEqual(content.appName, feed.words.appName)
        XCTAssertEqual(content.notice, "Open Taqwa on your phone")
        XCTAssertEqual(content.inlineLine, feed.words.appName, "the inline draws the app's name alone")
        XCTAssertNil(content.cornerLine, "the corner is the arch alone")
        XCTAssertEqual(content.spoken(inline: false), Speech.line("\(feed.words.appName), Open Taqwa on your phone", language: "en"))
        XCTAssertEqual(content.spoken(inline: true), Speech.line(feed.words.appName, language: "en"), "the inline says what it draws")
        XCTAssertEqual(content.rtl, false, "the feed's direction")
        XCTAssertTrue(content.latin, "and its type")
    }

    func testASpentArabicFeedKeepsItsWordsDirectionAndType() throws {
        let feed = try Vectors.feed("sampleArSa")
        let content = try spent(feed)

        XCTAssertEqual(content.appName, "تقوى")
        XCTAssertEqual(content.notice, feed.words.openOnPhone)
        XCTAssertEqual(content.spoken(inline: false).runs.first?.languageIdentifier, "ar-SA")
        XCTAssertEqual(content.rtl, true)
        XCTAssertFalse(content.latin)
    }

    /// Ruling 75 item 1: the empty form's sentence joins the app's name and its notice with the
    /// comma the row's form takes (`Glances.separator`): "، " in Arabic and Urdu, a right-to-left
    /// feed, as this watch's Today joins that notice ("…، حدّث تقوى على ساعتك", `TodayTests`),
    /// ", " otherwise. Bengali is `GlanceTests`' (`testABengaliFeedKeepsTheLatinCommaInEveryJoin`).
    func testTheEmptyFormJoinsTheNameAndTheNoticeAsTodayDoes() throws {
        let arabic = try Vectors.feed("sampleArSa")
        XCTAssertEqual(String(try spent(arabic).spoken(inline: false).characters), "تقوى، افتح تقوى على هاتفك")
        XCTAssertEqual(String(try spent(arabic, newer: true).spoken(inline: false).characters), "تقوى، حدّث تقوى على ساعتك")
        XCTAssertEqual(String(try spent(arabic).spoken(inline: true).characters), "تقوى", "the inline says the name alone")
        guard case let .feed(urdu) = FeedDecoder.decode(try Vectors.data("phone/phone-capetown-ur24.json")) else {
            throw VectorError("phone-capetown-ur24.json is no feed")
        }
        XCTAssertEqual(String(try spent(urdu).spoken(inline: false).characters), "تقویٰ، اپنے فون پر تقویٰ کھولیں")
        XCTAssertEqual(String(try spent(urdu, newer: true).spoken(inline: false).characters), "تقویٰ، اپنی گھڑی پر تقویٰ اپڈیٹ کریں")
        let english = try StoreFixtures.sample
        XCTAssertEqual(String(try spent(english).spoken(inline: false).characters), "Taqwa, Open Taqwa on your phone")
        XCTAssertEqual(String(try spent(english, newer: true).spoken(inline: false).characters), "Taqwa, Update Taqwa on your watch")
    }

    func testANewerFormatSaysTheUpdateFirstWithAFeedOrWithout() throws {
        let feed = try StoreFixtures.sample

        XCTAssertEqual(try spent(feed, newer: true).notice, "Update Taqwa on your watch", "the feed's own words")
        XCTAssertEqual(String(try spent(feed, newer: true).spoken(inline: false).characters), "\(feed.words.appName), Update Taqwa on your watch")
        XCTAssertEqual(first(nil, newer: true, now: capeTown(14, 58)).notice, own.updateOnWatch, "the watch's own")
        XCTAssertEqual(first(nil, newer: false, now: capeTown(14, 58)).notice, own.openOnPhone)
    }

    func testWithNoFeedTheWatchsOwnWordsSpeakInTheWatchsLanguage() {
        let content = first(nil, now: capeTown(14, 58))

        XCTAssertNil(content.glance)
        XCTAssertEqual(content.appName, own.appName)
        XCTAssertEqual(content.inlineLine, own.appName)
        XCTAssertNil(content.cornerLine)
        XCTAssertEqual(String(content.spoken(inline: false).characters), "\(own.appName), \(own.openOnPhone)")
        XCTAssertEqual(String(content.spoken(inline: true).characters), own.appName)
        XCTAssertNil(content.spoken(inline: false).runs.first?.languageIdentifier, "no language: the watch's own")
        XCTAssertNil(content.rtl, "the watch's own direction")
        XCTAssertFalse(content.latin, "the system font, as the watch draws its own words")
    }

    func testARunningFeedWithANewerFormatSeenStillSaysItsGlance() throws {
        let content = first(try StoreFixtures.sample, newer: true, now: capeTown(14, 57, 30))

        XCTAssertNotNil(content.glance)
        XCTAssertEqual(String(content.spoken(inline: false).characters), "Asr in 1 hour 12 minutes, at 16:10")
        XCTAssertEqual(content.notice, "Update Taqwa on your watch", "kept for the empty form")
    }
}
