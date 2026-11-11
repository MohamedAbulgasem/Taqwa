#if DEBUG
import Foundation
import XCTest
@testable import WatchCore

/// The DEBUG launch arguments every later task's shots and UI tests use (plan 4 Task 5), and the
/// sample they store: moved by whole days so that its 18 October is today, as the Wear sample is.
final class DebugSampleTests: XCTestCase {

    /// Friday 30 October 2026, 12:00 in Cape Town (UTC+2 all year).
    private let friday: Int64 = 1_793_354_400
    private let day: Int64 = 86_400

    func testTheArgumentsAreRead() {
        let arguments = DebugArguments([
            "/TaqwaWatch.app/TaqwaWatch", "-taqwaSampleFeed", "ar-sa", "-taqwaAt", "14:57", "-taqwaFrozen", "1",
            "-taqwaPage", "tasbeeh", "-taqwaNoFeed", "1", "-taqwaNewerSeen", "1", "-taqwaPhrase", "اللهم صل على محمد",
            "-AppleLanguages", "(en)",
        ])
        XCTAssertEqual(arguments.sample, "ar-sa")
        XCTAssertEqual(arguments.at, "14:57")
        XCTAssertTrue(arguments.frozen)
        XCTAssertEqual(arguments.page, .tasbeeh)
        XCTAssertTrue(arguments.noFeed)
        XCTAssertTrue(arguments.newerSeen)
        XCTAssertEqual(arguments.phrase, "اللهم صل على محمد")
    }

    func testNoArgumentIsNothing() {
        let none = DebugArguments(["/TaqwaWatch.app/TaqwaWatch"])
        XCTAssertNil(none.sample)
        XCTAssertNil(none.at)
        XCTAssertFalse(none.frozen)
        XCTAssertNil(none.page)
        XCTAssertFalse(none.noFeed)
        XCTAssertFalse(none.newerSeen)
        XCTAssertNil(none.phrase)
        XCTAssertFalse(none.holdsState)

        let zeros = DebugArguments(["app", "-taqwaFrozen", "0", "-taqwaNoFeed", "0", "-taqwaNewerSeen", "0", "-taqwaPage", "nowhere"])
        XCTAssertFalse(zeros.frozen)
        XCTAssertFalse(zeros.noFeed)
        XCTAssertFalse(zeros.newerSeen)
        XCTAssertNil(zeros.page, "a page there is none of")
        XCTAssertNil(DebugArguments(["app", "-taqwaSampleFeed"]).sample, "a flag with no value")
    }

    func testAnArgumentThatSetsTheStoreHoldsItsState() {
        // Plan 4 erratum p4 #8: such a run takes no phone's feed and shakes no hands, so the state
        // it set is the state looked at.
        XCTAssertTrue(DebugArguments(["app", "-taqwaSampleFeed", "capetown"]).holdsState)
        XCTAssertTrue(DebugArguments(["app", "-taqwaNoFeed", "1"]).holdsState)
        XCTAssertTrue(DebugArguments(["app", "-taqwaNewerSeen", "1"]).holdsState)
        XCTAssertTrue(DebugArguments(["app", "-taqwaStartIn", "12"]).holdsState, "plan 4 Task 6's argument stores a feed too")
        XCTAssertFalse(DebugArguments(["app", "-taqwaAt", "14:57", "-taqwaFrozen", "1", "-taqwaPage", "today"]).holdsState)
        XCTAssertFalse(DebugArguments(["app", "-taqwaPage", "gallery", "-taqwaGalleryPart", "2"]).holdsState)
    }

    // MARK: - Plan 4 Task 6: -taqwaStartIn and the gallery's second part

    /// Plan 4 Task 8's two: `-taqwaFresh 1` forgets every count and the phrase chosen (erratum p4
    /// #9), and `-taqwaTasbeehCount <n>` opens the phrase chosen at n of round 1. A count that is no
    /// whole number at or above 0 is none.
    func testTheTasbeehArgumentsAreRead() {
        let arguments = DebugArguments(["app", "-taqwaFresh", "1", "-taqwaTasbeehCount", "99"])
        XCTAssertTrue(arguments.fresh)
        XCTAssertEqual(arguments.tasbeehCount, 99)
        let none = DebugArguments(["app"])
        XCTAssertFalse(none.fresh)
        XCTAssertNil(none.tasbeehCount)
        XCTAssertFalse(DebugArguments(["app", "-taqwaFresh", "0"]).fresh)
        for wrong in ["-1", "ninety", "1.5", ""] {
            XCTAssertNil(DebugArguments(["app", "-taqwaTasbeehCount", wrong]).tasbeehCount, "not '\(wrong)'")
        }
        XCTAssertEqual(DebugArguments(["app", "-taqwaTasbeehCount", "0"]).tasbeehCount, 0)
        XCTAssertFalse(arguments.holdsState, "the tasbeeh's arguments set no feed")
        XCTAssertNil(none.tasbeehPhrase)
        XCTAssertEqual(DebugArguments(["app", "-taqwaTasbeehPhrase", "astaghfirullah"]).tasbeehPhrase, "astaghfirullah", "the shots' phrase")
    }

    /// `-taqwaHeading <deg>` is the magnetic heading a scripted compass reports, as Core Location's
    /// `magneticHeading` would, `-taqwaHeadingAccuracy <deg>` its accuracy (10 when not given), and
    /// `-taqwaNoCompass 1` a watch with none (plan 4 Task 9). Any of them scripts the compass; none
    /// sets a feed.
    func testTheCompassArgumentsAreRead() {
        let facing = DebugArguments(["app", "-taqwaHeading", "50"])
        XCTAssertEqual(facing.compass, DebugCompass(magnetic: 50, accuracy: 10, available: true))
        XCTAssertFalse(facing.holdsState, "the compass's arguments set no feed")
        XCTAssertEqual(
            DebugArguments(["app", "-taqwaHeading", "50", "-taqwaHeadingAccuracy", "60"]).compass,
            DebugCompass(magnetic: 50, accuracy: 60, available: true)
        )
        XCTAssertEqual(DebugArguments(["app", "-taqwaHeading", "-1"]).compass?.magnetic, -1, "Core Location's sentinel")
        XCTAssertEqual(DebugArguments(["app", "-taqwaHeading", "7.5"]).compass?.magnetic, 7.5)
        XCTAssertEqual(DebugArguments(["app", "-taqwaHeadingAccuracy", "60"]).compass, DebugCompass(magnetic: 0, accuracy: 60, available: true))
        XCTAssertEqual(DebugArguments(["app", "-taqwaNoCompass", "1"]).compass?.available, false)
        XCTAssertNil(DebugArguments(["app"]).compass, "the watch's own compass")
        XCTAssertNil(DebugArguments(["app", "-taqwaNoCompass", "0"]).compass)
        for wrong in ["north", "nan", "inf", ""] {
            XCTAssertNil(DebugArguments(["app", "-taqwaHeading", wrong]).compass, "not '\(wrong)'")
        }
    }

    func testTheStartAndTheGalleryPartAreRead() {
        let arguments = DebugArguments(["app", "-taqwaStartIn", "12", "-taqwaPage", "gallery", "-taqwaGalleryPart", "2"])
        XCTAssertEqual(arguments.startIn, 12)
        XCTAssertEqual(arguments.page, .gallery)
        XCTAssertEqual(arguments.galleryPart, 2)

        let none = DebugArguments(["app"])
        XCTAssertNil(none.startIn)
        XCTAssertEqual(none.galleryPart, 1, "the families with their countdown first")
        XCTAssertNil(DebugArguments(["app", "-taqwaStartIn", "twelve"]).startIn)
        XCTAssertEqual(DebugArguments(["app", "-taqwaGalleryPart", "3"]).galleryPart, 3)
        for wrong in ["0", "4", "two", ""] {
            XCTAssertEqual(DebugArguments(["app", "-taqwaGalleryPart", wrong]).galleryPart, 1, "three parts, not '\(wrong)'")
        }
    }

    func testStartInPutsTheNextStartThatManyMinutesAway() throws {
        let text = try StoreFixtures.sampleText
        let original = try StoreFixtures.decoded(text)
        for now in [friday, friday + 20, friday + 59] {
            let moved = try StoreFixtures.decoded(try DebugSample.startingIn(text, minutes: 12, now: now, phrase: nil))

            let moment = try XCTUnwrap(WatchRules.moment(moved, now: now))
            XCTAssertEqual(moment.next.prayer, .ASR, "the sample's own Asr, on its 18 October")
            XCTAssertEqual(moment.next.dayIndex, 1)
            XCTAssertEqual(moment.next.at % 60, 0, "a start on a whole minute")
            XCTAssertTrue((720..<780).contains(moment.secondsLeft), "\(moment.secondsLeft) s: the countdown reads 12 minutes")
            XCTAssertEqual(WatchRules.countdown(moved, secondsLeft: moment.secondsLeft), "in 12 min")
            // Every instant and midnight moves by one whole number of minutes; the words, the
            // clock strings among them, stay as written.
            let seconds = moved.days[1].times.asr - original.days[1].times.asr
            XCTAssertEqual(seconds % 60, 0)
            XCTAssertEqual(moved, Self.shifted(original, by: seconds))
        }
        XCTAssertEqual(
            try StoreFixtures.decoded(try DebugSample.startingIn(text, minutes: 12, now: friday, phrase: nil)).days[1].times.asr,
            friday + 720, "from a whole minute, exactly twelve minutes"
        )
    }

    func testStartInRefusesMinutesOutsideItsRange() throws {
        let text = try StoreFixtures.sampleText
        for minutes in [0, -1, 181] {
            XCTAssertThrowsError(try DebugSample.startingIn(text, minutes: minutes, now: friday, phrase: nil), "\(minutes)")
        }
        for minutes in [1, 180] {
            let moved = try StoreFixtures.decoded(try DebugSample.startingIn(text, minutes: minutes, now: friday, phrase: nil))
            XCTAssertEqual(WatchRules.moment(moved, now: friday)?.next.at, friday + Int64(minutes) * 60, "\(minutes): Asr is still the next")
        }
        XCTAssertThrowsError(try DebugSample.startingIn(Data(#"{"version":1,"days":[]}"#.utf8), minutes: 12, now: friday, phrase: nil))
    }

    func testStartInKeepsThePhrase() throws {
        let moved = try StoreFixtures.decoded(try DebugSample.startingIn(try StoreFixtures.sampleText, minutes: 12, now: friday, phrase: "سبحان الله"))
        XCTAssertEqual(moved.tasbeeh.last, WatchPhrase(id: "debug-phrase", phrase: "سبحان الله", target: 33))
    }

    func testTheSamplesAreTheFourInTheVectors() throws {
        XCTAssertEqual(DebugSample.names, ["capetown", "en12", "ar-ly12", "ar-sa"])
        for name in DebugSample.names {
            let resource = try XCTUnwrap(DebugSample.resourceName(name), name)
            XCTAssertNoThrow(try StoreFixtures.decoded(try Vectors.data("\(resource).json")), name)
        }
        XCTAssertEqual(DebugSample.resourceName("capetown"), "sample-feed-capetown")
        XCTAssertEqual(DebugSample.resourceName("en12"), "sample-feed-capetown-en12")
        XCTAssertNil(DebugSample.resourceName("london"))
        XCTAssertNil(DebugSample.resourceName(""))
    }

    func testTheMockDayIsTheSamplesEighteenthOfOctober() throws {
        for name in DebugSample.names {
            let feed = try StoreFixtures.decoded(try Vectors.data("\(try XCTUnwrap(DebugSample.resourceName(name))).json"))
            let mock = try XCTUnwrap(feed.days.first { $0.date == DebugSample.mockDate }, name)
            XCTAssertEqual(mock.midnight, DebugSample.mockMidnight, "\(name): 2026-10-18T00:00+02:00")
        }
        XCTAssertEqual(DebugSample.midnight(of: DebugSample.mockMidnight + 14 * 3_600), DebugSample.mockMidnight)
    }

    func testTheSampleMovesByWholeDaysToToday() throws {
        let text = try StoreFixtures.sampleText
        let original = try StoreFixtures.decoded(text)

        let moved = try StoreFixtures.decoded(try DebugSample.redated(text, now: friday, phrase: nil))

        XCTAssertEqual(moved, Self.shifted(original, by: 12 * day), "every instant twelve days on, every word as it was")
        let mock = try XCTUnwrap(moved.days.first { $0.date == DebugSample.mockDate })
        XCTAssertEqual(mock.midnight, DebugSample.midnight(of: friday), "its 18 October is today")
    }

    func testTheDayTurnsAtCapeTownsMidnight() throws {
        let text = try StoreFixtures.sampleText
        let original = try StoreFixtures.decoded(text)
        let midnight = DebugSample.midnight(of: friday)
        XCTAssertEqual(midnight, 1_793_311_200, "30 October 00:00 in Cape Town")

        XCTAssertEqual(try StoreFixtures.decoded(try DebugSample.redated(text, now: midnight - 1, phrase: nil)), Self.shifted(original, by: 11 * day))
        XCTAssertEqual(try StoreFixtures.decoded(try DebugSample.redated(text, now: midnight, phrase: nil)), Self.shifted(original, by: 12 * day))
        // Before the mock day the sample moves back, and on it, not at all.
        XCTAssertEqual(try StoreFixtures.decoded(try DebugSample.redated(text, now: DebugSample.mockMidnight - 1, phrase: nil)), Self.shifted(original, by: -day))
        XCTAssertEqual(try StoreFixtures.decoded(try DebugSample.redated(text, now: DebugSample.mockMidnight + 50_000, phrase: nil)), original)
    }

    func testEverySampleMoves() throws {
        for name in DebugSample.names {
            let text = try Vectors.data("\(try XCTUnwrap(DebugSample.resourceName(name))).json")
            let moved = try StoreFixtures.decoded(try DebugSample.redated(text, now: friday, phrase: nil))
            XCTAssertEqual(moved, Self.shifted(try StoreFixtures.decoded(text), by: 12 * day), name)
        }
    }

    func testAMidnightThePhoneCouldNotTellStaysNone() throws {
        // 0 says the phone could not tell (the rules then turn the day at solar midnight): it
        // stays 0 when the sample moves, as the Wear sample's does.
        var root = try XCTUnwrap(try JSONSerialization.jsonObject(with: try StoreFixtures.sampleText) as? [String: Any])
        var days = try XCTUnwrap(root["days"] as? [[String: Any]])
        days[5]["midnight"] = 0
        root["days"] = days
        let text = try JSONSerialization.data(withJSONObject: root)

        let moved = try StoreFixtures.decoded(try DebugSample.redated(text, now: friday, phrase: nil))

        XCTAssertEqual(moved.days[5].midnight, 0)
        XCTAssertEqual(moved.days[4].midnight, try StoreFixtures.sample.days[4].midnight + 12 * day, "its neighbours move")
    }

    func testAFeedWithoutTheMockDayIsRefused() {
        XCTAssertThrowsError(try DebugSample.redated(Data(#"{"version":1,"days":[]}"#.utf8), now: friday, phrase: nil))
        XCTAssertThrowsError(try DebugSample.redated(Data("{".utf8), now: friday, phrase: nil))
    }

    func testAPhraseIsAppendedToTheSamplesOwn() throws {
        let text = try StoreFixtures.sampleText
        let original = try StoreFixtures.decoded(text)

        let moved = try StoreFixtures.decoded(try DebugSample.redated(text, now: friday, phrase: "اللهم صل على محمد"))

        XCTAssertEqual(Array(moved.tasbeeh.dropLast()), original.tasbeeh, "after the sample's own")
        XCTAssertEqual(moved.tasbeeh.last, WatchPhrase(id: "debug-phrase", phrase: "اللهم صل على محمد", target: 33))
    }

    func testTheClockStartsAtTheMovedMidnightPlusTheTime() {
        let midnight = DebugSample.midnight(of: friday)
        XCTAssertEqual(DebugSample.clockStart("14:57", now: friday), midnight + 14 * 3_600 + 57 * 60)
        XCTAssertEqual(DebugSample.clockStart("00:00", now: friday), midnight)
        XCTAssertEqual(DebugSample.clockStart("23:59", now: friday), midnight + 86_340)
        // With seconds too (plan 4 Task 6's shot at 16:09:30).
        XCTAssertEqual(DebugSample.clockStart("16:09:30", now: friday), midnight + 16 * 3_600 + 9 * 60 + 30)
        XCTAssertEqual(DebugSample.clockStart("14:57:00", now: friday), midnight + 14 * 3_600 + 57 * 60)
        XCTAssertEqual(DebugSample.clockStart("23:59:59", now: friday), midnight + 86_399)
        for wrong in ["24:00", "14:60", "14:5", "1457", "", "-1:00", "ab:cd", "١٤:٥٧", "14:57:60", "14:57:0", "14:57:", "14:57:000", "14:57-00"] {
            XCTAssertNil(DebugSample.clockStart(wrong, now: friday), "'\(wrong)'")
        }
    }

    func testTheNewerNoticeIsAVersionTwoFeed() {
        XCTAssertEqual(FeedDecoder.decode(DebugSample.newerFeed), .newerVersion)
    }

    /// [feed] with every instant moved by [seconds], and nothing else: what `redated` must give.
    private static func shifted(_ feed: WatchFeed, by seconds: Int64) -> WatchFeed {
        WatchFeed(
            version: feed.version,
            writtenAt: feed.writtenAt + seconds,
            days: feed.days.map { day in
                let t = day.times
                return WatchDay(
                    date: day.date,
                    times: WatchTimes(
                        fajr: t.fajr + seconds, sunrise: t.sunrise + seconds, dhuhr: t.dhuhr + seconds,
                        asr: t.asr + seconds, maghrib: t.maghrib + seconds, isha: t.isha + seconds,
                        fajrEnd: t.fajrEnd.map { $0 + seconds }, asrEnd: t.asrEnd.map { $0 + seconds }
                    ),
                    clock: day.clock, names: day.names, hijri: day.hijri, gregorian: day.gregorian,
                    weekday: day.weekday, dayNumber: day.dayNumber,
                    midnight: day.midnight == 0 ? 0 : day.midnight + seconds,
                    hijriDayMonth: day.hijriDayMonth, gregorianSpoken: day.gregorianSpoken,
                    // A 12-hour phone's times are display text, as the words are: they stay.
                    clockTime: day.clockTime
                )
            },
            place: feed.place,
            ui: feed.ui,
            tasbeeh: feed.tasbeeh
        )
    }
}
#endif
