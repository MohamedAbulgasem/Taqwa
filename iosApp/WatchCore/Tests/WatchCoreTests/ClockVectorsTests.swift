import Foundation
import XCTest
@testable import WatchCore

/// The clock times and the ring's digits as the phone sends them (plan 4 Task 3): `clockText` and
/// `spokenClock` held to Kotlin's answers for each of a day's six times, Sunrise among them, and
/// the ring's zero read as the phone writes it. No time is worked out here: every string is the
/// feed's, and a 12-hour phone's day period is the one its `clockTime` carries (Ruling 58 item 3).
final class ClockVectorsTests: XCTestCase {

    func testEveryClockCaseMatchesTheVectors() throws {
        let cases = Vectors.nonEmpty(Vectors.feeds.clock, "clock")
        for clock in cases {
            XCTAssertEqual(clock.caseClass, .ENCODER, "a clock case on \(clock.feed)")
            let feed = try Vectors.feed(clock.feed)
            let time = try XCTUnwrap(WatchTime(rawValue: clock.time), "no time \(clock.time)")
            let day = try XCTUnwrap(feed.days.indices.contains(clock.day) ? feed.days[clock.day] : nil, "\(clock.feed) has no day \(clock.day)")
            let what = "\(clock.feed), day \(clock.day), \(clock.time)"
            // Scalar for scalar: a time that is only canonically equivalent is another text.
            XCTAssertTrue(
                WatchRules.clockText(day, time).unicodeScalars.elementsEqual(clock.clockText.unicodeScalars),
                "clockText: \(what): \(WatchRules.clockText(day, time)), where Kotlin has \(clock.clockText)"
            )
            XCTAssertTrue(
                WatchRules.spokenClock(day, time).unicodeScalars.elementsEqual(clock.spokenClock.unicodeScalars),
                "spokenClock: \(what): \(WatchRules.spokenClock(day, time)), where Kotlin has \(clock.spokenClock)"
            )
        }
        // Every one of the six times, on every feed the section names.
        for feed in Set(cases.map(\.feed)) {
            XCTAssertEqual(Set(cases.filter { $0.feed == feed }.map(\.time)), Set(WatchTime.allCases.map(\.rawValue)), feed)
        }
    }

    func testATwelveHourTimeIsDrawnAndSpokenWhole() throws {
        let fsi = "\u{2068}"
        let pdi = "\u{2069}"
        let en12 = try Vectors.feed("sampleEn12").days[0]
        XCTAssertEqual(WatchRules.clockText(en12, .ASR), "4:10\u{00A0}PM")
        XCTAssertEqual(WatchRules.spokenClock(en12, .ASR), fsi + "4:10\u{00A0}PM" + pdi)
        // Sunrise carries its day period too, from the same clockTime.
        XCTAssertEqual(WatchRules.clockText(en12, .SUNRISE), "6:01\u{00A0}AM")
        XCTAssertEqual(WatchRules.spokenClock(en12, .SUNRISE), fsi + "6:01\u{00A0}AM" + pdi)
        // The face's form stays the bare clock, as the complications draw it.
        XCTAssertEqual(en12.clock.of(.ASR), "4:10")

        let libya = try Vectors.feed("sampleArLy12").days[0]
        XCTAssertEqual(WatchRules.spokenClock(libya, .ASR), fsi + "4:10\u{00A0}م" + pdi)

        let capetown = try Vectors.feed("sample").days[0]
        XCTAssertNil(capetown.clockTime)
        XCTAssertEqual(WatchRules.clockText(capetown, .ASR), "16:10")
        XCTAssertEqual(WatchRules.spokenClock(capetown, .ASR), "16:10")
    }

    func testTheSaudiRingIsWestern() throws {
        let saudi = try Vectors.feed("sampleArSa")
        XCTAssertEqual(saudi.ui.ringDigitZero, "0")
        XCTAssertEqual(saudi.ui.digitZero, "٠")
        // Today's H:MM:SS in the ring's digits, its clock strings in the phone's.
        XCTAssertEqual(WatchRules.inDigits("1:11:36", saudi.ui.ringDigitZero ?? saudi.ui.digitZero), "1:11:36")
        XCTAssertEqual(WatchRules.clockText(saudi.days[0], .ASR), "١٦:١٠")
        // A phone whose ring follows its clock sends none.
        XCTAssertNil(try Vectors.feed("sampleArLy12").ui.ringDigitZero)
        XCTAssertNil(try Vectors.feed("sample").ui.ringDigitZero)
    }

    /// The six times in the order of the day, each the namesake of its prayer, as Kotlin's.
    func testTheTimesAreTheDaysSixInOrder() throws {
        XCTAssertEqual(WatchTime.allCases.map(\.rawValue), ["FAJR", "SUNRISE", "DHUHR", "ASR", "MAGHRIB", "ISHA"])
        XCTAssertEqual(WatchPrayer.allCases.map { WatchTime.of($0).rawValue }, WatchPrayer.allCases.map(\.rawValue))
        let names = try Vectors.feed("sample").days[0].names
        XCTAssertEqual(WatchTime.allCases.map { names.of($0) }, [names.fajr, names.sunrise, names.dhuhr, names.asr, names.maghrib, names.isha])
    }
}
