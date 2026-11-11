import Foundation
import XCTest
@testable import WatchCore

/// Today (plan 4 Task 7): the phone's countdown ring to the next start above the day's six times
/// (five where the phone hides Sunrise), each as the phone writes and says it, and the page's
/// states without a usable feed in plan 3's call 5 order. The rules restate plan 3's `today` and
/// `dayRows` (`wearApp`'s `TodayModel.kt`), and these tests restate `TodayModelTest`'s cases.
///
/// Every instant is the Cape Town sample's, given as epoch seconds: its day 1 is Sunday 18 October
/// 2026 (the mockups' day), since the sample starts yesterday. Every day is Fajr 4:33, Sunrise
/// 6:01, Dhuhr 12:33, Asr 16:10, Maghrib 19:04 and Isha 20:24.
final class TodayTests: XCTestCase {
    /// U+2068 and U+2069, around a 12-hour time spliced into a spoken sentence.
    private let FSI = "\u{2068}"
    private let PDI = "\u{2069}"
    /// The no-break space a 12-hour phone writes before its day period.
    private let NBSP = "\u{00A0}"

    /// Cape Town (UTC+2 all year) at [hours]:[minutes]:[seconds] on the sample's day [day], where
    /// day 1 is Sunday 18 October 2026, 00:00 at 1792274400.
    private func capeTown(_ hours: Int64, _ minutes: Int64, _ seconds: Int64 = 0, day: Int64 = 1) -> Int64 {
        1_792_274_400 + (day - 1) * 86_400 + hours * 3_600 + minutes * 60 + seconds
    }

    /// 14:58:24 on the mock Sunday: the drawing's "1:11:36" to Asr at 16:10.
    private var at145824: Int64 { capeTown(14, 58, 24) }

    private var sample: WatchFeed { get throws { try StoreFixtures.sample } }

    private func ready(
        _ feed: WatchFeed, _ now: Int64, newer: Bool = false, seconds: Bool = true, rows: Int = 6,
        file: StaticString = #filePath, line: UInt = #line
    ) throws -> TodayModel {
        guard case let .ready(model) = Today.screen(feed, newerVersionSeen: newer, now: now, seconds: seconds) else {
            XCTFail("Today has a ring at \(now)", file: file, line: line)
            throw VectorError("no ring at \(now)")
        }
        XCTAssertEqual(model.rows.count, rows, "the day's times", file: file, line: line)
        return model
    }

    /// The Cape Town sample with its JSON changed by [edit] before it is read: a phone that writes
    /// another way.
    private func sample(editing edit: (inout [String: Any]) -> Void) throws -> WatchFeed {
        var root = try XCTUnwrap(try JSONSerialization.jsonObject(with: try StoreFixtures.sampleText) as? [String: Any])
        edit(&root)
        return try StoreFixtures.decoded(try JSONSerialization.data(withJSONObject: root))
    }

    private func editingUi(_ change: @escaping (inout [String: Any]) -> Void) -> (inout [String: Any]) -> Void {
        { root in
            var ui = root["ui"] as? [String: Any] ?? [:]
            change(&ui)
            root["ui"] = ui
        }
    }

    // MARK: - The ring

    func testTodayAtTwoFiftyEight() throws {
        let today = try ready(try sample, at145824)

        XCTAssertEqual(today.ringLabel, "ASR IN")
        XCTAssertEqual(today.countdown, "1:11:36")
        XCTAssertEqual(today.time, "16:10")
        XCTAssertFalse(today.notice)
        // From Dhuhr at 12:33 to Asr at 16:10 is 13,020 s, of which 8,724 have passed.
        XCTAssertEqual(today.progress, Float(8_724) / Float(13_020), accuracy: 1e-4)
        XCTAssertEqual(today.spoken, "Asr in 1 hour 11 minutes, at 16:10")
        XCTAssertFalse(today.mirrorsRing)

        XCTAssertEqual(today.rows.map(\.time), WatchTime.allCases, "one row for each time, in the day's order")
        XCTAssertEqual(today.rows.map(\.name), ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"])
        XCTAssertEqual(today.rows.map(\.status), [.passed, .passed, .current, .upcoming, .upcoming, .upcoming])
        XCTAssertEqual(today.rows.map(\.small), [false, true, false, false, false, false])
        XCTAssertEqual(today.rows.map(\.stateWord), ["Passed", "Passed", "Current prayer", "Next prayer", nil, nil])
        XCTAssertEqual(today.rows.map(\.clock), ["4:33", "6:01", "12:33", "16:10", "19:04", "20:24"])
    }

    /// The phone's Show sunrise off (the owner's decision of 9 November 2026), as a phone has it
    /// until the user turns it on: the list is the five prayers, as the phone's Prayer screen leaves
    /// its Sunrise row out, and nothing else changes: the ring, the states, the focus. VoiceOver is
    /// told the five rows the screen shows (`TodayModelTest.withoutSunriseTheListIsTheFivePrayers`).
    func testWithoutSunriseTheListIsTheFivePrayers() throws {
        let hidden = try sample(editing: editingUi { $0["showSunrise"] = false })
        XCTAssertFalse(hidden.ui.showSunrise)
        let today = try ready(hidden, at145824, rows: 5)
        let shown = try ready(try sample, at145824)

        XCTAssertEqual(today.rows.map(\.time), [.FAJR, .DHUHR, .ASR, .MAGHRIB, .ISHA])
        XCTAssertEqual(today.rows.map(\.name), ["Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"])
        XCTAssertEqual(today.rows, shown.rows.filter { !$0.small })
        XCTAssertTrue(today.rows.allSatisfy { !$0.small })
        XCTAssertEqual(today.rows.map(\.spoken), ["Fajr, 4:33", "Dhuhr, 12:33", "Asr, 16:10", "Maghrib, 19:04", "Isha, 20:24"])
        XCTAssertEqual(today.rows.map(\.stateWord), ["Passed", "Current prayer", "Next prayer", nil, nil])
        XCTAssertEqual(today.ringLabel, shown.ringLabel)
        XCTAssertEqual(today.countdown, shown.countdown)
        XCTAssertEqual(today.spoken, shown.spoken)
        XCTAssertEqual(today.focus, .DHUHR)
        // Between Sunrise and Dhuhr nothing is lit (Fajr ends at sunrise), and the list goes to
        // Dhuhr, the next start, as it does with Sunrise shown.
        XCTAssertEqual(try ready(hidden, capeTown(6, 40), rows: 5).focus, .DHUHR)
        XCTAssertEqual(try ready(try sample, capeTown(6, 40)).focus, .DHUHR)
        // A feed with no such key, a 1.6.0 (43) phone's, shows Sunrise as before.
        let older = try sample(editing: editingUi { $0.removeValue(forKey: "showSunrise") })
        XCTAssertTrue(older.ui.showSunrise)
        XCTAssertEqual(try ready(older, at145824).rows.map(\.name), ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"])
    }

    func testSaudiTimesInArabicIndicAndTheRingInWestern() throws {
        let today = try ready(try Vectors.feed("sampleArSa"), at145824)

        XCTAssertEqual(today.ringLabel, "متبقٍّ على العصر")
        XCTAssertEqual(today.time, "١٦:١٠")
        XCTAssertEqual(today.countdown, "1:11:36", "the phone ring's own digits, Western on an ar-SA phone")
        XCTAssertEqual(today.rows.map(\.clock), ["٤:٣٣", "٦:٠١", "١٢:٣٣", "١٦:١٠", "١٩:٠٤", "٢٠:٢٤"])
        for row in today.rows {
            XCTAssertTrue(row.clock.unicodeScalars.allSatisfy { $0 == ":" || (0x0660...0x0669).contains($0.value) }, row.clock)
        }
    }

    func testTheRingTakesTheFeedsDigitsWhenItNamesNoneOfItsOwn() throws {
        // An Urdu phone's ring writes its H:MM:SS in the digits of its clock strings (no ringDigitZero).
        let urdu = try sample(editing: editingUi { $0["digitZero"] = "۰"; $0["ringDigitZero"] = NSNull() })

        XCTAssertEqual(try ready(urdu, at145824).countdown, "۱:۱۱:۳۶")
        XCTAssertEqual(Today.clock(urdu, secondsLeft: 4_296, seconds: true), "۱:۱۱:۳۶")
    }

    func testAlwaysOnDropsTheSeconds() throws {
        let feed = try sample

        XCTAssertEqual(Today.clock(feed, secondsLeft: 4_296, seconds: false), "1:11")
        XCTAssertEqual(Today.clock(feed, secondsLeft: 4_296, seconds: true), "1:11:36")
        XCTAssertEqual(try ready(feed, at145824, seconds: false).countdown, "1:11")
    }

    func testAlwaysOnOnAWholeMinuteShowsTheMinuteThatHolds() throws {
        // The always-on screen is drawn once a minute, on the minute. At 14:58:00 Asr is 1:12:00
        // away for that instant alone, and 1:11 holds for the minute it is shown, as a
        // complication's minute does (Review Focus 3).
        let feed = try sample

        XCTAssertEqual(try ready(feed, capeTown(14, 58), seconds: false).countdown, "1:11")
        XCTAssertEqual(try ready(feed, capeTown(14, 58), seconds: true).countdown, "1:12:00")
        // At a start's own minute the ring already counts to the start after it.
        let atAsr = try ready(feed, capeTown(16, 10), seconds: false)
        XCTAssertEqual(atAsr.ringLabel, "MAGHRIB IN")
        XCTAssertEqual(atAsr.countdown, "2:53")
    }

    func testTheHoursAreNotPaddedAndAStartBeginsTheRingAgain() throws {
        let feed = try sample
        // 04:10 on Monday, before its Fajr at 4:33: the night's countdown, its hour unpadded.
        XCTAssertEqual(try ready(feed, capeTown(4, 10, day: 2)).countdown, "0:23:00")

        // The instant Asr begins, the ring starts again from empty, counting to Maghrib at 19:04.
        let atAsr = try ready(feed, capeTown(16, 10))
        XCTAssertEqual(atAsr.ringLabel, "MAGHRIB IN")
        XCTAssertEqual(atAsr.countdown, "2:54:00")
        XCTAssertEqual(atAsr.progress, 0)
    }

    func testTheClockIsNeverNegative() throws {
        XCTAssertEqual(Today.clock(try sample, secondsLeft: -5, seconds: true), "0:00:00")
        XCTAssertEqual(Today.clock(try sample, secondsLeft: -5, seconds: false), "0:00")
        XCTAssertEqual(Today.clock(try sample, secondsLeft: 36_000, seconds: true), "10:00:00")
    }

    func testUnderAMinuteTheRingSaysSo() throws {
        let today = try ready(try sample, capeTown(16, 9, 30))

        XCTAssertEqual(today.countdown, "0:00:30")
        XCTAssertEqual(today.spoken, "Asr in under a minute, at 16:10")
    }

    func testCapitalsOnlyUnderALatinInterface() throws {
        // A phone whose interface is not in Latin letters writes its label as it is.
        let notLatin = try sample(editing: editingUi { $0["latinScript"] = false })

        XCTAssertEqual(try ready(notLatin, at145824).ringLabel, "Asr in")
    }

    func testTheLabelIsCasedInThePhonesLanguage() throws {
        // Turkish capitals dot their i, as the face's label does (ComplicationFields.nextPrayer).
        let turkish = try sample(editing: editingUi { $0["language"] = "tr" })

        XCTAssertEqual(try ready(turkish, at145824).ringLabel, "ASR İN")
    }

    func testWithoutSpokenWordsTheRingSaysWhatItShows() throws {
        // A feed from before the spoken words: the screen's own label and time.
        let unspoken = try sample(editing: editingUi { $0.removeValue(forKey: "spoken") })

        XCTAssertEqual(try ready(unspoken, at145824).spoken, "ASR IN 16:10")
    }

    func testTwelveHourRowsCarryTheDayPeriod() throws {
        let today = try ready(try Vectors.feed("sampleEn12"), at145824)
        let asr = today.rows[3]

        XCTAssertEqual(asr.clock, "4:10" + NBSP + "PM")
        XCTAssertEqual(asr.spoken, "Asr, " + FSI + "4:10" + NBSP + "PM" + PDI)
        XCTAssertEqual(today.time, "4:10" + NBSP + "PM")
        XCTAssertEqual(today.spoken, "Asr in 1 hour 11 minutes, at " + FSI + "4:10" + NBSP + "PM" + PDI)
    }

    func testATwelveHourSunriseRowCarriesItsDayPeriod() throws {
        let rows = try ready(try Vectors.feed("sampleEn12"), capeTown(14, 58)).rows
        let sunrise = rows[1]

        XCTAssertEqual(sunrise.time, .SUNRISE)
        XCTAssertEqual(sunrise.clock, "6:01" + NBSP + "AM")
        XCTAssertTrue(sunrise.small)
        XCTAssertEqual(sunrise.spoken, "Sunrise, " + FSI + "6:01" + NBSP + "AM" + PDI)
        for row in rows {
            XCTAssertTrue(row.clock.hasSuffix(NBSP + "AM") || row.clock.hasSuffix(NBSP + "PM"), row.clock)
        }
    }

    // MARK: - The rows

    func testARowIsSaidAsThePhoneSaysIt() throws {
        let rows = try ready(try sample, capeTown(14, 58)).rows

        XCTAssertEqual(rows[3].spoken, "Asr, 16:10")
        XCTAssertEqual(rows[3].clock, "16:10")
        XCTAssertEqual(rows[1].spoken, "Sunrise, 6:01")
    }

    /// M8 point A5 (6 November 2026): an Arabic or Urdu row joins its name and its time with the
    /// Arabic comma "، ", as the phone's rows and the Galaxy watch's do (Ruling 64 item 6).
    func testAnArabicOrUrduRowIsJoinedByTheArabicComma() throws {
        let arabic = try ready(try Vectors.feed("sampleArSa"), capeTown(14, 58)).rows
        XCTAssertEqual(arabic[3].spoken, "العصر، ١٦:١٠")
        XCTAssertEqual(arabic[1].spoken, "الشروق، ٦:٠١")
        guard case let .feed(urdu) = FeedDecoder.decode(try Vectors.data("phone/phone-capetown-ur24.json")) else {
            throw VectorError("phone-capetown-ur24.json is no feed")
        }
        XCTAssertEqual(try ready(urdu, capeTown(14, 58)).rows[3].spoken, "عصر، ۱۶:۱۰")
    }

    func testBeforeFajrNothingIsLit() throws {
        // 04:10 on Monday: yesterday's Isha is the last start, and it lights no row of today's.
        let today = try ready(try sample, capeTown(4, 10, day: 2))

        XCTAssertFalse(today.rows.contains { $0.status == .current })
        XCTAssertEqual(today.rows.map(\.status), Array(repeating: RowState.upcoming, count: 6))
        XCTAssertEqual(today.rows.map(\.stateWord), ["Next prayer", nil, nil, nil, nil, nil])
        XCTAssertEqual(today.focus, .FAJR, "the list opens on the next start")
    }

    func testFajrStopsBeingCurrentAtSunrise() throws {
        let feed = try sample
        XCTAssertEqual(try ready(feed, capeTown(6, 0, 59)).rows[0].status, .current, "Fajr is lit until sunrise")

        let today = try ready(feed, capeTown(6, 40))
        XCTAssertFalse(today.rows.contains { $0.status == .current })
        XCTAssertEqual(today.rows.map(\.status), [.passed, .passed, .upcoming, .upcoming, .upcoming, .upcoming])
        XCTAssertEqual(today.rows[2].stateWord, "Next prayer")
        XCTAssertEqual(today.focus, .DHUHR, "with nothing lit, the list opens on the next start")
    }

    func testSunriseIsNeverCurrent() throws {
        let feed = try sample
        // A time has passed from its own instant on, as the phone's rows count it.
        XCTAssertEqual(try ready(feed, capeTown(6, 1)).rows[1].status, .passed)
        XCTAssertEqual(try ready(feed, capeTown(6, 0, 59)).rows[1].status, .upcoming)
        XCTAssertNil(try ready(feed, capeTown(6, 0, 59)).rows[1].stateWord, "Sunrise is never the next prayer")
        for minute in stride(from: Int64(0), to: 1_440, by: 7) {
            let rows = try ready(feed, capeTown(minute / 60, minute % 60)).rows
            XCTAssertNotEqual(rows[1].status, .current, "at minute \(minute)")
        }
    }

    func testAfterIshaTheNextIsTomorrowsFajr() throws {
        let today = try ready(try sample, capeTown(22, 30))

        XCTAssertEqual(today.ringLabel, "FAJR IN")
        XCTAssertEqual(today.time, "4:33")
        // The rows are still Sunday's, until midnight: Isha lit, the rest passed, and no row is
        // the next start, which is Monday's.
        XCTAssertEqual(today.rows[5].status, .current)
        XCTAssertEqual(today.rows.prefix(5).map(\.status), Array(repeating: RowState.passed, count: 5))
        XCTAssertFalse(today.rows.contains { $0.stateWord == "Next prayer" })
        XCTAssertEqual(today.focus, .ISHA, "the list opens on the lit row")
    }

    func testYesterdaysIshaAfterMidnightIsNoRowOfTodays() throws {
        // A high-latitude summer: Saturday's Isha falls at 00:30 on Sunday, before Sunday's Fajr. At
        // 00:10 the ring counts to it, and no row of Sunday's is the next start: Sunday's own Isha,
        // at 20:24, is the same prayer at another instant.
        let feed = try sample(editing: { root in
            var days = root["days"] as? [[String: Any]] ?? []
            var times = days[0]["times"] as? [String: Any] ?? [:]
            times["isha"] = 1_792_274_400 + 1_800
            days[0]["times"] = times
            root["days"] = days
        })
        let today = try ready(feed, capeTown(0, 10))

        XCTAssertEqual(today.ringLabel, "ISHA IN")
        XCTAssertEqual(today.rows.map(\.status), Array(repeating: RowState.upcoming, count: 6))
        XCTAssertEqual(today.rows.map(\.stateWord), [nil, nil, nil, nil, nil, nil])
    }

    func testTheListOpensOnTheLitRow() throws {
        XCTAssertEqual(try ready(try sample, at145824).focus, .DHUHR)
    }

    func testAFridayNamesJumuah() throws {
        // Friday 23 October is the sample's day 6.
        let feed = try sample
        XCTAssertEqual(try ready(feed, capeTown(11, 0, day: 6)).ringLabel, "JUMUʿAH IN")
        XCTAssertEqual(try ready(feed, capeTown(11, 0, day: 6)).rows[2].name, "Jumuʿah")
        XCTAssertEqual(try ready(feed, capeTown(11, 0, day: 5)).ringLabel, "DHUHR IN")
    }

    func testTheListTurnsOverAtTheFeedsMidnightNotTheWatchs() throws {
        // A watch in Auckland, 11 hours ahead of the place: the day is still the feed's, from its
        // own midnights, whatever the watch's zone says.
        let zone = NSTimeZone.default
        NSTimeZone.default = try XCTUnwrap(TimeZone(identifier: "Pacific/Auckland"))
        defer { NSTimeZone.default = zone }
        let feed = try sample
        let midnight = feed.days[1].midnight
        XCTAssertEqual(midnight, capeTown(0, 0), "day 1's midnight")

        // A second after day 1's midnight the rows are day 1's: none of its times has come yet.
        let after = try ready(feed, midnight + 1)
        XCTAssertEqual(after.rows.map(\.status), Array(repeating: RowState.upcoming, count: 6))
        XCTAssertEqual(after.rows[0].stateWord, "Next prayer", "day 1's Fajr is the next start")

        // A second before it they are day 0's: its Isha lit, the rest passed.
        let before = try ready(feed, midnight - 1)
        XCTAssertEqual(before.rows.map(\.status), [.passed, .passed, .passed, .passed, .passed, .current])
    }

    func testTheRingMirrorsInArabic() throws {
        let arabic = try ready(try Vectors.feed("sampleArLy12"), at145824)

        XCTAssertTrue(arabic.mirrorsRing, "the ring fills anticlockwise right to left, as the phone's RingArc")
        XCTAssertEqual(arabic.ringLabel, "متبقٍّ على العصر", "no capitals in Arabic")
        XCTAssertEqual(arabic.countdown, "1:11:36")
        XCTAssertEqual(arabic.time, "4:10" + NBSP + "م")
        XCTAssertFalse(arabic.latinScript)
        XCTAssertTrue(try ready(try Vectors.feed("sampleArSa"), at145824).mirrorsRing)
        XCTAssertFalse(try ready(try sample, at145824).mirrorsRing)
    }

    func testASpokenLineCarriesTheFeedsLanguage() throws {
        // VoiceOver speaks the ring and the rows in the feed's language (plan 4 erratum p4 #5).
        XCTAssertEqual(try ready(try sample, at145824).language, "en")
        XCTAssertEqual(try ready(try Vectors.feed("sampleArSa"), at145824).language, "ar-SA")
        XCTAssertTrue(try ready(try sample, at145824).latinScript)
    }

    // MARK: - Without a usable feed (plan 3's call 5)

    func testNoFeedSaysOpenTaqwaOnYourPhone() {
        XCTAssertEqual(Today.screen(nil, newerVersionSeen: false, now: at145824), .message(.openOnPhone))
    }

    func testNoFeedAfterANewerFormatSaysUpdate() {
        XCTAssertEqual(Today.screen(nil, newerVersionSeen: true, now: at145824), .message(.updateOnWatch))
    }

    func testASpentFeedIsSpent() throws {
        // The sample's last day is Monday 16 November (day 30), whose Isha at 20:24 is its last start.
        let feed = try sample
        let spent = capeTown(21, 0, day: 30)

        XCTAssertEqual(Today.screen(feed, newerVersionSeen: false, now: spent), .spent("Open Taqwa on your phone"))
        XCTAssertEqual(Today.screen(feed, newerVersionSeen: true, now: spent), .spent("Update Taqwa on your watch"))
        XCTAssertEqual(Today.screen(feed, newerVersionSeen: false, now: capeTown(14, 58, day: 32)), .spent("Open Taqwa on your phone"))
        // In the feed's own words.
        let arabic = try Vectors.feed("sampleArSa")
        XCTAssertEqual(Today.screen(arabic, newerVersionSeen: true, now: spent), .spent("حدّث تقوى على ساعتك"))
        XCTAssertEqual(Today.screen(arabic, newerVersionSeen: false, now: spent), .spent("افتح تقوى على هاتفك"))
        // The minute before Isha still has a ring.
        XCTAssertNoThrow(try ready(feed, capeTown(20, 23, day: 30)))
    }

    func testANewerVersionTakesTheTimesPlace() throws {
        let today = try ready(try sample, at145824, newer: true)

        XCTAssertEqual(today.time, "Update Taqwa on your watch")
        XCTAssertTrue(today.notice)
        XCTAssertEqual(today.countdown, "1:11:36", "the ring still counts down from the last feed")
        XCTAssertEqual(today.spoken, "Asr in 1 hour 11 minutes, at 16:10, Update Taqwa on your watch")
        XCTAssertEqual(today.rows, try ready(try sample, at145824).rows, "the rows are the feed's as before")

        let arabic = try ready(try Vectors.feed("sampleArLy12"), at145824, newer: true)
        XCTAssertEqual(arabic.time, "حدّث تقوى على ساعتك")
        XCTAssertTrue(arabic.spoken.hasSuffix("، حدّث تقوى على ساعتك"), arabic.spoken)
        XCTAssertFalse(arabic.spoken.hasSuffix(", حدّث تقوى على ساعتك"), arabic.spoken)
    }

    func testABlankWordIsEnglish() throws {
        // A development feed with no words: the English, as WatchFeed.words gives it.
        let wordless = try sample(editing: editingUi { $0.removeValue(forKey: "screens") })

        XCTAssertEqual(try ready(wordless, at145824).rows[3].stateWord, "Next prayer")
        XCTAssertEqual(Today.screen(wordless, newerVersionSeen: false, now: capeTown(21, 0, day: 30)), .spent("Open Taqwa on your phone"))
    }
}
