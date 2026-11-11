import Foundation
import XCTest
@testable import WatchCore

/// The decoder and the rules plan 4 adds against `feed-vectors.json` (plan 4 Task 1), each case
/// held as its class says (Ruling 56): exactly Kotlin's answer on a text an encoder can write, and
/// on one it cannot, no crash and a feed's values the text's own (`TextValues`).
final class FeedVectorsTests: XCTestCase {

    func testEveryDecodeCaseMatchesTheVectors() throws {
        let cases = Vectors.nonEmpty(Vectors.feeds.decode, "decode")
        for caseClass in CaseClass.allCases {
            XCTAssertTrue(cases.contains { $0.caseClass == caseClass }, "no \(caseClass) case")
        }
        for decodeCase in cases {
            let text = try Vectors.text(of: decodeCase)
            let answer = FeedDecoder.decode(text)
            switch decodeCase.caseClass {
            case .ENCODER, .ONE_CHANGE:
                let wanted = decodeCase.swift ?? decodeCase.result
                XCTAssertEqual(answer.vectorName, wanted, decodeCase.name)
                guard wanted == "feed", case let .feed(feed) = answer else { continue }
                guard let expect = decodeCase.expect else {
                    XCTFail("\(decodeCase.name) is a feed with no expect")
                    continue
                }
                check(feed, expect, decodeCase.name)
                // Every other field too, key by key, which `expect` does not name.
                TextValues.assertFeedHolds(feed, text, decodeCase.name)
            case .FOREIGN:
                // It has answered, so it has neither crashed nor hung. A feed it read must hold
                // the text's own values, each integer exactly.
                guard case let .feed(feed) = answer else { continue }
                TextValues.assertFeedHolds(feed, text, decodeCase.name)
            }
        }
    }

    func testTheKnownDifferencesAreRefusals() throws {
        let cases = Vectors.nonEmpty(Vectors.feeds.decode, "decode")
        let differing = cases.filter { $0.swift != nil }
        XCTAssertEqual(
            Set(differing.map(\.name)),
            [
                // The Lead's call 8: a quoted number or boolean,
                "quotedWrittenAt", "quotedVersion", "quotedBearing", "quotedRtl",
                // and Ruling 57 item 2: every other field the port reads, in the wrong type kotlinx reads leniently.
                "quotedFajr", "quotedSunrise", "quotedDhuhr", "quotedAsr", "quotedMaghrib", "quotedIsha", "quotedFajrEnd",
                "quotedAsrEnd", "quotedMidnight", "quotedDeclination", "quotedSouthern", "bareDigitZero", "quotedTwelveHour",
                "quotedHoursUpTo", "quotedMinutesUpTo", "quotedTurnRightUpTo", "quotedTurnLeftUpTo", "bareRingDigitZero",
                "quotedLatinScript", "quotedShowSunrise", "quotedTarget",
            ],
            "the cases where the port answers otherwise than Kotlin: a wrong type kotlinx reads leniently, refused"
        )
        for decodeCase in differing {
            XCTAssertEqual(decodeCase.result, "feed", decodeCase.name)
            XCTAssertEqual(decodeCase.swift, "malformed", decodeCase.name)
            XCTAssertEqual(FeedDecoder.decode(try Vectors.text(of: decodeCase)), .malformed, decodeCase.name)
        }
        for decodeCase in cases where decodeCase.caseClass != .FOREIGN && decodeCase.result != "feed" {
            XCTAssertNotEqual(FeedDecoder.decode(try Vectors.text(of: decodeCase)).vectorName, "feed", decodeCase.name)
        }
    }

    /// The phone's Show sunrise (the owner's decision of 9 November 2026), read as Kotlin reads it:
    /// `sunriseHidden` off, as a phone sends it until the user turns it on; the samples on;
    /// `olderPhone`, a 1.6.0 (43) phone's with no such key, on, as every watch showed Sunrise
    /// before; `nullShowSunrise` refused, never read as the default; and `quotedShowSunrise`, a
    /// string kotlinx reads leniently, refused (Ruling 57 item 2's safe difference).
    func testTheSunriseSettingIsReadAndAFeedWithoutItShowsSunrise() throws {
        XCTAssertFalse(try Vectors.feed("sunriseHidden").ui.showSunrise)
        XCTAssertTrue(try Vectors.feed("oneDay").ui.showSunrise)
        XCTAssertTrue(try Vectors.feed("olderPhone").ui.showSunrise)
        for name in ["sample", "sampleEn12", "sampleArLy12", "sampleArSa"] {
            XCTAssertTrue(try Vectors.feed(name).ui.showSunrise, name)
        }
        XCTAssertFalse(String(decoding: try Vectors.text(of: try Vectors.decodeCase("olderPhone")), as: UTF8.self).contains("showSunrise"))
        for name in ["nullShowSunrise", "quotedShowSunrise"] {
            XCTAssertEqual(FeedDecoder.decode(try Vectors.text(of: try Vectors.decodeCase(name))), .malformed, name)
        }
        // The Friday golden is a phone with Show sunrise off, as a phone has it by default.
        guard case let .feed(friday) = FeedDecoder.decode(try Vectors.data("phone/phone-capetown-friday-en.json")) else {
            return XCTFail("the Friday golden is no feed")
        }
        XCTAssertFalse(friday.ui.showSunrise)
    }

    /// `newerPhone`, a later phone's text at version 1, holds a key no build reads, `future`, in
    /// every kind of object the feed holds, each of the model's eleven types once (Ruling 60 item
    /// 1d; Kotlin holds the list to its model's classes): the countdown phrases, the screens' words,
    /// one of their counts and a day's 12-hour times among them. The port reads it as the same text
    /// with every `future` taken out, as kotlinx does
    /// (`SwiftPortVectorsTest.aLaterPhonesKeyIsIgnoredInEveryObject`), so none of its objects,
    /// `WatchCountdownPhrases`, `WatchScreenWords`, `WatchCount` and `WatchSix` included, refuses a
    /// key it does not know.
    func testALaterPhonesKeyIsIgnoredInEveryObject() throws {
        let text = try Vectors.text(of: try Vectors.decodeCase("newerPhone"))
        var found: [String] = []
        func withoutFuture(_ value: Any, _ path: String) -> Any {
            if let object = value as? [String: Any] {
                if object["future"] != nil { found.append(path) }
                var kept: [String: Any] = [:]
                for (key, member) in object where key != "future" {
                    kept[key] = withoutFuture(member, path.isEmpty ? key : "\(path).\(key)")
                }
                return kept
            }
            if let array = value as? [Any] {
                return array.enumerated().map { withoutFuture($0.element, "\(path)[\($0.offset)]") }
            }
            return value
        }
        let plain = try JSONSerialization.data(withJSONObject: withoutFuture(try JSONSerialization.jsonObject(with: text), ""))
        XCTAssertEqual(
            Set(found),
            [
                "", "place", "ui", "ui.countdown", "ui.spoken", "ui.screens", "ui.screens.turnRight[0]",
                // The Galaxy's face screen words (plan 5 Task 5), which this port does not read at all.
                "ui.face",
                "days[0]", "days[0].times", "days[0].clockTime", "tasbeeh[0]",
            ]
        )
        XCTAssertEqual(found.count, 12, "one future in each")
        guard case let .feed(newer) = FeedDecoder.decode(text) else { return XCTFail("newerPhone is no feed") }
        guard case let .feed(without) = FeedDecoder.decode(plain) else { return XCTFail("newerPhone without its futures is no feed") }
        XCTAssertEqual(newer, without)
        XCTAssertNotNil(newer.days.first?.clockTime)
        XCTAssertFalse(newer.ui.screens.turnRight.isEmpty)
    }

    func testSpokenCountdownsMatchTheVectors() throws {
        for spoken in Vectors.nonEmpty(Vectors.feeds.spoken, "spoken") {
            try assertOnAnEncoderFeed(spoken.caseClass, spoken.feed)
            XCTAssertEqual(
                WatchRules.spokenCountdown(try Vectors.feed(spoken.feed), name: spoken.name, clock: spoken.clock, secondsLeft: spoken.secondsLeft),
                spoken.spoken,
                "\(spoken.feed) at \(spoken.secondsLeft) s"
            )
        }
    }

    func testLabelsMatchTheVectors() throws {
        for label in Vectors.nonEmpty(Vectors.feeds.labels, "labels") {
            try assertOnAnEncoderFeed(label.caseClass, label.feed)
            guard let prayer = WatchPrayer(rawValue: label.prayer) else {
                XCTFail("no prayer \(label.prayer)")
                continue
            }
            XCTAssertEqual(WatchRules.label(try Vectors.feed(label.feed), prayer, day: label.day), label.label, "\(label.feed), day \(label.day), \(label.prayer)")
        }
    }

    func testDaysMatchTheVectors() throws {
        for day in Vectors.nonEmpty(Vectors.feeds.days, "days") {
            try assertOnAnEncoderFeed(day.caseClass, day.feed)
            let feed = try Vectors.feed(day.feed)
            let today = WatchRules.today(feed, now: day.now)
            XCTAssertEqual(today, day.today, "today: \(day.feed), \(day.name)")
            XCTAssertEqual(today.flatMap { WatchRules.dayStart(feed, $0) }, day.dayStart, "dayStart: \(day.feed), \(day.name)")
        }
    }

    /// Ruling 57 item 5's moments: a start exactly at the next day's Fajr, and a date turned before
    /// any start has arrived, which no case of `watch-rules-vectors.json` reaches. Every value is
    /// Kotlin's exactly, the ring's `Float` too.
    func testEveryMomentMatchesTheVectors() throws {
        for moment in Vectors.nonEmpty(Vectors.feeds.moments, "moments") {
            try assertOnAnEncoderFeed(moment.caseClass, moment.feed)
            let feed = try Vectors.feed(moment.feed)
            let found = WatchRules.moment(feed, now: moment.now)
            XCTAssertEqual(vector(found?.next), moment.next, "next: \(moment.name)")
            XCTAssertEqual(vector(found?.previous), moment.previous, "previous: \(moment.name)")
            XCTAssertEqual(found?.secondsLeft, moment.secondsLeft, "secondsLeft: \(moment.name)")
            XCTAssertEqual(found.map { Double($0.progress) }, moment.progress, "progress: \(moment.name)")
            XCTAssertEqual(found.map { WatchRules.countdown(feed, secondsLeft: $0.secondsLeft) }, moment.countdown, "countdown: \(moment.name)")
            XCTAssertEqual(WatchRules.litOnList(feed, now: moment.now)?.rawValue, moment.litOnList, "litOnList: \(moment.name)")
            XCTAssertEqual(WatchRules.litOnGlance(feed, now: moment.now)?.rawValue, moment.litOnGlance, "litOnGlance: \(moment.name)")
        }
    }

    /// `WatchRules.starts` whole, a start at or after the next day's Fajr left out.
    func testEveryStartsListMatchesTheVectors() throws {
        for starts in Vectors.nonEmpty(Vectors.feeds.starts, "starts") {
            try assertOnAnEncoderFeed(starts.caseClass, starts.feed)
            XCTAssertEqual(WatchRules.starts(try Vectors.feed(starts.feed)).map { vector($0) }, starts.starts.map { Optional($0) }, starts.feed)
        }
    }

    /// `WatchRules.filled`, a place that names no part kept as it is.
    func testEveryFilledPatternMatchesTheVectors() {
        for filled in Vectors.nonEmpty(Vectors.feeds.filled, "filled") {
            let found = WatchRules.filled(filled.pattern, filled.parts)
            XCTAssertTrue(found.unicodeScalars.elementsEqual(filled.filled.unicodeScalars), "\(filled.pattern): \(found), where Kotlin has \(filled.filled)")
        }
    }

    /// `WatchRules.inDigits`, in 16 bits as Kotlin's `Char` adds.
    func testEveryInDigitsCaseMatchesTheVectors() throws {
        for digits in Vectors.nonEmpty(Vectors.feeds.inDigits, "inDigits") {
            XCTAssertEqual(digits.zero.utf16.count, 1, "the zero of \(digits.text)")
            let zero = try XCTUnwrap(digits.zero.first)
            let found = WatchRules.inDigits(digits.text, zero)
            XCTAssertTrue(found.unicodeScalars.elementsEqual(digits.inDigits.unicodeScalars), "\(digits.text) from U+\(String(digits.zero.utf16.first ?? 0, radix: 16))")
        }
    }

    /// Ruling 56 item 4's types at their ends, on `oneDay` with one number changed: a Kotlin `Int`
    /// (the version, a phrase's target, a spoken count's `upTo`) reads as an `Int32` and a `Long`
    /// (`writtenAt`, a midnight, a time) as an `Int64`, every value within its range read exactly
    /// and one beyond it refused, as kotlinx refuses it. The vectors hold one end of two of them
    /// (`wideVersion`, `wideTarget`); this holds both ends of all six, so a field declared `Int`,
    /// which is 64 bits on a Mac and 32 on a watch, cannot pass here and differ there.
    func testEveryIntIsAnInt32AndEveryLongAnInt64() throws {
        let oneDay = try XCTUnwrap(try Vectors.decodeCase("oneDay").json)
        func decoded(_ member: String, _ value: String) throws -> FeedDecodeResult {
            let key = member.prefix { $0 != ":" }
            let range = try XCTUnwrap(oneDay.range(of: member), "oneDay has no \(member)")
            return FeedDecoder.decode(Data(oneDay.replacingCharacters(in: range, with: "\(key):\(value)").utf8))
        }
        func feed(_ member: String, _ value: String) throws -> WatchFeed {
            guard case let .feed(feed) = try decoded(member, value) else { throw VectorError("\(member) \(value) is no feed") }
            return feed
        }
        let int32s: [(String, (WatchFeed) -> Int32)] = [
            ("\"target\"", { $0.tasbeeh[0].target }),
            ("\"upTo\"", { $0.ui.spoken!.hours[0].upTo }),
        ]
        for (member, read) in int32s {
            let at = member == "\"target\"" ? "\"target\":100" : "\"upTo\":0"
            XCTAssertEqual(read(try feed(at, "2147483647")), Int32.max, member)
            XCTAssertEqual(read(try feed(at, "-2147483648")), Int32.min, member)
            XCTAssertEqual(try decoded(at, "2147483648"), .malformed, member)
            XCTAssertEqual(try decoded(at, "-2147483649"), .malformed, member)
        }
        XCTAssertEqual(try decoded("\"version\":1", "2147483647"), .newerVersion)
        XCTAssertEqual(try decoded("\"version\":1", "2147483648"), .malformed)
        XCTAssertEqual(try decoded("\"version\":1", "-2147483649"), .malformed)

        let int64s: [(String, (WatchFeed) -> Int64)] = [
            ("\"writtenAt\":1792303200", { $0.writtenAt }),
            ("\"midnight\":1792188000", { $0.days[0].midnight }),
            ("\"asr\":1792246200", { $0.days[0].times.asr }),
        ]
        for (member, read) in int64s {
            XCTAssertEqual(read(try feed(member, "9223372036854775807")), Int64.max, member)
            XCTAssertEqual(read(try feed(member, "-9223372036854775808")), Int64.min, member)
            XCTAssertEqual(try decoded(member, "9223372036854775808"), .malformed, member)
            XCTAssertEqual(try decoded(member, "-9223372036854775809"), .malformed, member)
        }
    }

    /// A `null` where Kotlin's type is not nullable is refused, as kotlinx refuses it (the Global
    /// Constraints' feed bullet), at each key with a default as at the required ones. The trap is
    /// `decodeIfPresent(...) ?? default`, the idiomatic read, which takes a `null` for an absent
    /// key and so the default where Kotlin answers malformed. The vectors hold four of the eight
    /// (`nullInADefaultedField`, since plan 4 Task 3 `nullScreens` and `nullLatinScript`, and
    /// `nullShowSunrise` for the phone's Show sunrise); this holds each, on `oneDay` with that one
    /// value set to `null`. Kotlin refuses all eight (measured in review round 1, for plan 3's two in
    /// Ruling 57 item 4's review, and for Show sunrise in `WatchFeedJsonTest`). A key whose Kotlin type
    /// is nullable reads `null` as nil. Plan 3's two such keys, `ringDigitZero` and `clockTime`, are
    /// `null` in `oneDay` itself, an English 24-hour phone's, so every case on it holds them.
    func testANullInEveryKeyWithADefaultIsRefused() throws {
        let oneDay = try XCTUnwrap(try Vectors.decodeCase("oneDay").json)
        /// `oneDay` with the member that begins with `start` (through the first `end` after it,
        /// when the value is a list or an object) written `"key":null`.
        func withNull(_ start: String, through end: String? = nil) throws -> Data {
            XCTAssertEqual(oneDay.components(separatedBy: start).count, 2, "\(start) is not once in oneDay")
            let found = try XCTUnwrap(oneDay.range(of: start), "oneDay has no \(start)")
            let upper = try end.map { try XCTUnwrap(oneDay.range(of: $0, range: found.upperBound..<oneDay.endIndex), "no \($0) after \(start)").upperBound }
            let key = start.prefix { $0 != ":" }
            return Data(oneDay.replacingCharacters(in: found.lowerBound..<(upper ?? found.upperBound), with: "\(key):null").utf8)
        }
        let defaulted: [(String, String?)] = [
            ("\"midnight\":1792188000", nil),
            ("\"hijriDayMonth\":\"5 Jumada al-Ula\"", nil),
            ("\"gregorianSpoken\":\"Saturday 17 October\"", nil),
            ("\"digitZero\":\"0\"", nil),
            ("\"tasbeeh\":[", "}]"),
            // Plan 3's (Ruling 57 item 4): the screens' words, through the end of their last list, and the script.
            ("\"screens\":{", "}]}"),
            ("\"latinScript\":true", nil),
            // The phone's Show sunrise (the owner's decision of 9 November 2026).
            ("\"showSunrise\":true", nil),
        ]
        for (start, end) in defaulted {
            XCTAssertEqual(FeedDecoder.decode(try withNull(start, through: end)), .malformed, start)
        }
        let nullable: [(String, String?, (WatchFeed) -> Bool)] = [
            ("\"fajrEnd\":1792209660", nil, { $0.days[0].times.fajrEnd == nil }),
            ("\"asrEnd\":1792256520", nil, { $0.days[0].times.asrEnd == nil }),
            ("\"spoken\":{", "}]}", { $0.ui.spoken == nil }),
        ]
        for (start, end, isNil) in nullable {
            let text = try withNull(start, through: end)
            guard case let .feed(feed) = FeedDecoder.decode(text) else {
                XCTFail("\(start) null is no feed")
                continue
            }
            XCTAssertTrue(isNil(feed), start)
            TextValues.assertFeedHolds(feed, text, "\(start) null")
        }
    }

    /// Ruling 56 item 2's value rule at every integer `oneDay` holds: where the port answers feed,
    /// each value it read is exactly the text's JSON value, with no rounding. `JSONDecoder`'s
    /// own `Int64` read takes a number with a point or an exponent through its nearest `Double`
    /// below 2^53, so on its own it reads `1792303200.00000000000000000001` as 1792303200 and
    /// `1792303199.99999999999999999999` as 1792303200 (measured), fractions no `Double` can
    /// hold. Each integer is written here in five forms, two of them such fractions and three the
    /// same whole number (`.0`, `e0`, `0e-1`). Then two whole numbers a `Double` cannot hold,
    /// which the port reads exactly (measured), so the reference must too: `writtenAt`
    /// 9007199254740993 with a point and with an exponent, which `JSONSerialization` rounds.
    /// The integers in plan 3's `screens` (the turns' `upTo`), which the port reads since plan 4
    /// Task 3, come in too.
    ///
    /// Every one of these texts is `FOREIGN` (no encoder writes a point or an exponent at an
    /// integer), so a refusal is always a right answer (Ruling 57 item 3): only a feed is held,
    /// and only to its values. So another Foundation, such as the `macos-26` runner's, that
    /// refuses a form this Mac reads can never turn a correct port red here. How many forms were
    /// feeds is printed, not asserted, for the same reason.
    func testEveryIntegerIsReadExactlyOrNotAtAll() throws {
        let oneDay = try XCTUnwrap(try Vectors.decodeCase("oneDay").json)
        let integer = try NSRegularExpression(pattern: #""([A-Za-z]+)":(-?[0-9]+)(?=[,}\]])"#)
        let members = integer.matches(in: oneDay, range: NSRange(oneDay.startIndex..., in: oneDay))
        XCTAssertGreaterThanOrEqual(members.count, 20, "oneDay's integers")
        var feeds = 0
        var forms = 0
        for member in members {
            let key = try XCTUnwrap(Range(member.range(at: 1), in: oneDay).map { oneDay[$0] })
            let at = try XCTUnwrap(Range(member.range(at: 2), in: oneDay))
            let number = oneDay[at]
            var spellings = ["\(number).00000000000000000001", "\(number).0", "\(number)e0", "\(number)0e-1"]
            if let whole = Int64(number), whole > 0 { spellings.append("\(whole - 1).99999999999999999999") }
            for form in spellings {
                forms += 1
                let text = Data(oneDay.replacingCharacters(in: at, with: form).utf8)
                guard case let .feed(feed) = FeedDecoder.decode(text) else { continue }
                feeds += 1
                TextValues.assertFeedHolds(feed, text, "\(key) \(form)")
            }
        }
        for form in ["9007199254740993.0", "9007199254740993e0"] {
            forms += 1
            let text = Data(oneDay.replacingOccurrences(of: "\"writtenAt\":1792303200", with: "\"writtenAt\":\(form)").utf8)
            guard case let .feed(feed) = FeedDecoder.decode(text) else { continue }
            feeds += 1
            XCTAssertEqual(feed.writtenAt, 9_007_199_254_740_993, form)
            TextValues.assertFeedHolds(feed, text, "writtenAt \(form)")
        }
        print("integer forms: \(feeds) of \(forms) were feeds, each held to its text")
        // The version read first is whole too: a number just above or below 2, which the integer
        // read alone rounds to 2, is neither this version nor a newer one.
        for form in ["2.00000000000000000001", "1.99999999999999999999"] {
            let text = Data(oneDay.replacingOccurrences(of: "\"version\":1,", with: "\"version\":\(form),").utf8)
            XCTAssertEqual(FeedDecoder.decode(text), .malformed, "version \(form)")
        }
    }

    /// A feed whose numbers are at the ends of an `Int64` is still a feed, and the rules answer on
    /// it without trapping, as Kotlin's wrapping `Long` arithmetic answers without throwing.
    func testTheRulesNeverTrapOnExtremeNumbers() throws {
        let oneDay = try XCTUnwrap(try Vectors.decodeCase("oneDay").json)
        for (member, value) in [
            ("\"writtenAt\":1792303200", "-9223372036854775808"), ("\"writtenAt\":1792303200", "9223372036854775807"),
            ("\"midnight\":1792188000", "9223372036854775807"), ("\"midnight\":1792188000", "-9223372036854775808"),
            ("\"asr\":1792246200", "9223372036854775807"), ("\"asr\":1792246200", "-9223372036854775808"),
        ] {
            let key = member.prefix { $0 != ":" }
            let text = oneDay.replacingOccurrences(of: member, with: "\(key):\(value)")
            guard case let .feed(feed) = FeedDecoder.decode(Data(text.utf8)) else {
                XCTFail("\(member) \(value) is no feed")
                continue
            }
            // Before Fajr, after Dhuhr, and at both ends: an extreme time is then a period's end.
            for now in [Int64.min, -1, 0, 1_792_190_000, 1_792_246_200, Int64.max] {
                _ = WatchRules.moment(feed, now: now)
                _ = WatchRules.needsRefresh(feed, now: now)
                _ = WatchRules.litOnList(feed, now: now)
                _ = WatchRules.litOnGlance(feed, now: now)
                _ = WatchRules.today(feed, now: now).flatMap { WatchRules.dayStart(feed, $0) }
                _ = WatchRules.countdown(feed, secondsLeft: now)
                _ = WatchRules.spokenCountdown(feed, name: "Asr", clock: "16:10", secondsLeft: now)
            }
        }
    }

    // MARK: - Helpers

    /// What Kotlin decoded from a feed, field by field.
    private func check(_ feed: WatchFeed, _ expect: Expect, _ name: String) {
        let day0 = feed.days.first
        XCTAssertEqual(feed.days.count, expect.days, "days: \(name)")
        XCTAssertEqual(day0?.hijriDayMonth, expect.day0HijriDayMonth, "day0HijriDayMonth: \(name)")
        XCTAssertEqual(day0?.gregorianSpoken, expect.day0GregorianSpoken, "day0GregorianSpoken: \(name)")
        XCTAssertEqual(day0?.midnight, expect.day0Midnight, "day0Midnight: \(name)")
        XCTAssertEqual(String(feed.ui.digitZero), expect.digitZero, "digitZero: \(name)")
        XCTAssertEqual(feed.ui.ringDigitZero.map { String($0) }, expect.ringDigitZero, "ringDigitZero: \(name)")
        XCTAssertEqual(feed.ui.latinScript, expect.latinScript, "latinScript: \(name)")
        XCTAssertEqual(feed.ui.showSunrise, expect.showSunrise, "showSunrise: \(name)")
        XCTAssertEqual(day0.map { WatchRules.clockText($0, .ASR) }, expect.day0Asr?.clockText, "day0Asr clockText: \(name)")
        XCTAssertEqual(day0.map { WatchRules.spokenClock($0, .ASR) }, expect.day0Asr?.spokenClock, "day0Asr spokenClock: \(name)")
        XCTAssertEqual(feed.ui.spoken != nil, expect.hasSpoken, "hasSpoken: \(name)")
        XCTAssertEqual(feed.tasbeeh.count, expect.phrases, "phrases: \(name)")
        XCTAssertEqual(feed.place.city, expect.city, "city: \(name)")
    }

    private func vector(_ start: WatchStart?) -> StartVector? {
        start.map { StartVector(prayer: $0.prayer.rawValue, at: $0.at, dayIndex: $0.dayIndex) }
    }

    /// A rules case runs on a feed an encoder writes, so the port gives exactly Kotlin's answer.
    private func assertOnAnEncoderFeed(_ caseClass: CaseClass, _ feed: String, line: UInt = #line) throws {
        XCTAssertEqual(caseClass, .ENCODER, "a case on \(feed)", line: line)
        XCTAssertEqual(try Vectors.decodeCase(feed).caseClass, .ENCODER, "the feed \(feed)", line: line)
    }

}
