#if DEBUG
import Foundation

/// The pages a DEBUG launch can open on (`-taqwaPage`; plan 4 Task 7 and later).
enum DebugPage: String {
    case today, tasbeeh, qibla, gallery
}

/// The DEBUG launch arguments every later task's shots and UI tests use (plan 4 Task 5), read
/// from the process's own arguments, each flag followed by its value. Debug builds only: a Release
/// build compiles none of it.
///
/// - `-taqwaSampleFeed capetown|en12|ar-ly12|ar-sa` stores that sample, moved to today;
/// - `-taqwaAt HH:MM` (or `HH:MM:SS`, plan 4 Task 6) sets the scripted clock to the moved
///   18 October's midnight plus that time, ticking, and `-taqwaFrozen 1` stops it;
/// - `-taqwaPage today|tasbeeh|qibla|gallery` opens on that page, and `-taqwaGalleryPart 2|3` opens
///   the complication gallery on its second or third part (Task 6: the families, then the Smart
///   Stack card, then each family without its countdown);
/// - `-taqwaStartIn <minutes>` stores the `capetown` sample moved so that its next start is that
///   many minutes away (Task 6 Step 7, `DebugSample.startingIn`); it takes the place of
///   `-taqwaSampleFeed`;
/// - `-taqwaNoFeed 1` empties the store at launch: no feed, no delivery and no notice;
/// - `-taqwaNewerSeen 1` leaves the newer-version notice, as a version-2 feed would;
/// - `-taqwaPhrase <text>` adds a phrase of his own form to the stored sample's;
/// - `-taqwaFresh 1` forgets every tasbeeh count and the phrase chosen (plan 4 Task 8, erratum p4
///   #9), `-taqwaTasbeehPhrase <id>` then chooses that phrase (the shots' Astaghfirullah), and
///   `-taqwaTasbeehCount <n>` opens the phrase chosen at n of round 1;
/// - `-taqwaHeading <deg>` scripts the compass (plan 4 Task 9): a steady magnetic heading, as Core
///   Location's `magneticHeading` gives it, made true by the feed's declination as the real one is;
///   `-taqwaHeadingAccuracy <deg>` its accuracy (10 when not given), and `-taqwaNoCompass 1` a watch
///   with no compass.
///
/// Every UI test sets its whole state by them (plan 4 erratum p4 #9): `-taqwaNoFeed 1` clears the
/// feed and the notice, a sample stored clears the notice, and `-taqwaNewerSeen 1` sets it after
/// either; a tasbeeh test also passes `-taqwaFresh 1`, so no count an earlier test left is read.
struct DebugArguments: Equatable {
    var sample: String?
    var at: String?
    var frozen = false
    var page: DebugPage?
    var noFeed = false
    var newerSeen = false
    var phrase: String?
    /// `-taqwaStartIn`'s minutes, as written; `DebugSample.startingIn` says which it takes.
    var startIn: Int?
    /// The complication gallery's part, in the plan's order: 1, the four families; 2, the Smart
    /// Stack card; 3, each family without its countdown.
    var galleryPart = 1
    /// `-taqwaFresh 1`: the tasbeeh store emptied at launch.
    var fresh = false
    /// `-taqwaTasbeehCount <n>`: the phrase chosen at n of round 1, a whole number from 0.
    var tasbeehCount: Int?
    /// `-taqwaTasbeehPhrase <id>`: the phrase chosen at launch, before `tasbeehCount` applies.
    var tasbeehPhrase: String?
    /// The scripted compass of `-taqwaHeading`, `-taqwaHeadingAccuracy` and `-taqwaNoCompass`, or nil
    /// for the watch's own.
    var compass: DebugCompass?

    init(_ arguments: [String]) {
        func value(_ flag: String) -> String? {
            guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else { return nil }
            return arguments[index + 1]
        }
        sample = value("-taqwaSampleFeed")
        at = value("-taqwaAt")
        frozen = value("-taqwaFrozen") == "1"
        page = value("-taqwaPage").flatMap(DebugPage.init(rawValue:))
        noFeed = value("-taqwaNoFeed") == "1"
        newerSeen = value("-taqwaNewerSeen") == "1"
        phrase = value("-taqwaPhrase")
        startIn = value("-taqwaStartIn").flatMap { Int($0) }
        galleryPart = value("-taqwaGalleryPart").flatMap { Int($0) }.flatMap { (1...3).contains($0) ? $0 : nil } ?? 1
        fresh = value("-taqwaFresh") == "1"
        tasbeehCount = value("-taqwaTasbeehCount").flatMap { Int($0) }.flatMap { $0 >= 0 ? $0 : nil }
        tasbeehPhrase = value("-taqwaTasbeehPhrase")
        func degrees(_ flag: String) -> Double? { value(flag).flatMap(Double.init).flatMap { $0.isFinite ? $0 : nil } }
        let heading = degrees("-taqwaHeading")
        let accuracy = degrees("-taqwaHeadingAccuracy")
        let noCompass = value("-taqwaNoCompass") == "1"
        if heading != nil || accuracy != nil || noCompass {
            compass = DebugCompass(magnetic: heading ?? 0, accuracy: accuracy ?? 10, available: !noCompass)
        }
    }

    /// Whether this launch sets what the store holds. Such a run takes in no context and shakes no
    /// hands (plan 4 erratum p4 #8), so a paired phone's feed never replaces the state it set:
    /// the watch logs `TaqwaWatch intake off (debug sample)` instead.
    var holdsState: Bool { sample != nil || noFeed || newerSeen || startIn != nil }
}

/// The DEBUG compass (plan 4 Task 9): the simulator has no magnetometer, so the page's UI tests and
/// shots drive the dial with a steady sample every 50 ms of this magnetic heading and accuracy, or
/// with no compass at all.
struct DebugCompass: Equatable {
    var magnetic: Double
    var accuracy: Double
    var available: Bool
}

/// The DEBUG samples (plan 4 Task 5): the four Cape Town feeds `:watchcore` commits beside its
/// vectors, copied into a Debug build's bundle by the "Debug samples" phase, moved by whole days
/// so that the sample's Sunday 18 October 2026 is today, as the Wear app's `SampleFeed.redate`
/// moves its own. The day is read in Cape Town's zone, UTC+2 all year, so a day there is always
/// 86,400 seconds and the sample's midnights stay midnights once moved.
enum DebugSample {
    /// The names `-taqwaSampleFeed` takes, in the order the vectors list the samples.
    static let names = ["capetown", "en12", "ar-ly12", "ar-sa"]

    /// The bundle resource of the sample called `name`, without its `.json`; nil for no sample.
    static func resourceName(_ name: String) -> String? {
        switch name {
        case "capetown": return "sample-feed-capetown"
        case "en12", "ar-ly12", "ar-sa": return "sample-feed-capetown-\(name)"
        default: return nil
        }
    }

    /// The day the mockups draw and every vector is worked on: the sample's second day, since the
    /// phone's feed begins at yesterday (spec 1.1).
    static let mockDate = "2026-10-18"

    /// Its midnight in Cape Town, 2026-10-18T00:00+02:00, as the samples write it.
    static let mockMidnight: Int64 = 1_792_274_400

    /// The feed `-taqwaNewerSeen 1` writes: a version this build does not read, so the store keeps
    /// its notice and nothing else changes.
    static let newerFeed = Data(#"{"version":2}"#.utf8)

    private static let offset: Int64 = 7_200
    private static let day: Int64 = 86_400

    /// The Cape Town midnight that begins the day `now` falls on.
    static func midnight(of now: Int64) -> Int64 {
        let local = now + offset
        let days = local >= 0 ? local / day : (local - (day - 1)) / day
        return days * day - offset
    }

    /// The scripted clock's start for `-taqwaAt HH:MM` or `HH:MM:SS`: the moved 18 October's
    /// midnight, which is today's, plus that time. Nil for anything but two ASCII digits, a colon
    /// and two more, and optionally a colon and two more, within a day.
    static func clockStart(_ hhmm: String, now: Int64) -> Int64? {
        let units = Array(hhmm.utf8)
        guard units.count == 5 || units.count == 8, units[2] == UInt8(ascii: ":") else { return nil }
        guard units.count == 5 || units[5] == UInt8(ascii: ":") else { return nil }
        func number(_ range: Range<Int>) -> Int64? {
            var value: Int64 = 0
            for unit in units[range] {
                guard (UInt8(ascii: "0")...UInt8(ascii: "9")).contains(unit) else { return nil }
                value = value * 10 + Int64(unit - UInt8(ascii: "0"))
            }
            return value
        }
        guard let hours = number(0..<2), let minutes = number(3..<5), hours < 24, minutes < 60 else { return nil }
        let seconds: Int64
        if units.count == 8 {
            guard let read = number(6..<8), read < 60 else { return nil }
            seconds = read
        } else {
            seconds = 0
        }
        return midnight(of: now) + hours * 3_600 + minutes * 60 + seconds
    }

    /// The minutes `-taqwaStartIn` takes: up to three hours, so the sample's own Asr, whose period
    /// is 3 h 37 min long, stays the next start.
    static let startInMinutes = 1...180

    /// `sample` moved so that its 18 October's Asr is the next start after `now`, at the first whole
    /// minute `minutes` or more away, with `phrase` as `redated` adds it (`-taqwaStartIn`, plan 4
    /// Task 6 Step 7). Every instant and midnight moves by one whole number of minutes, and the
    /// words, the clock strings among them, stay as written: the face shows the sample's "16:10"
    /// at another hour, so only the timeline's observation uses it.
    static func startingIn(_ sample: Data, minutes: Int, now: Int64, phrase: String?) throws -> Data {
        guard startInMinutes.contains(minutes) else {
            throw SampleError("-taqwaStartIn takes \(startInMinutes.lowerBound) to \(startInMinutes.upperBound) minutes, not \(minutes)")
        }
        guard let root = try JSONSerialization.jsonObject(with: sample) as? [String: Any],
              let days = root["days"] as? [[String: Any]],
              let mock = days.first(where: { $0["date"] as? String == mockDate }),
              let asr = ((mock["times"] as? [String: Any])?["asr"] as? NSNumber)?.int64Value
        else { throw SampleError("the sample has no Asr dated \(mockDate)") }
        let soonest = now + Int64(minutes) * 60
        let start = (soonest + 59) / 60 * 60
        return try shifted(sample, by: start - asr, phrase: phrase)
    }

    /// `sample` moved by whole days so that its 18 October is the day of `now`, with `phrase`, when
    /// given, after the sample's own phrases as `WatchPhrase(id: "debug-phrase", phrase:, target: 33)`.
    /// Every instant moves by the same number of days: each day's six times, its two ends, its
    /// midnight (0, which says the phone could not tell, stays 0), and when the feed was written.
    /// The words stay as they are, the date strings among them: they are display text, and a
    /// sample may name a Sunday on a Friday. Everything else in the text, keys this build does not
    /// read among it, is kept.
    static func redated(_ sample: Data, now: Int64, phrase: String?) throws -> Data {
        try shifted(sample, by: midnight(of: now) - mockMidnight, phrase: phrase)
    }

    /// `sample` with every instant moved by `seconds` (`redated`'s and `startingIn`'s move), and
    /// `phrase`, when given, after its own phrases.
    private static func shifted(_ sample: Data, by seconds: Int64, phrase: String?) throws -> Data {
        guard var root = try JSONSerialization.jsonObject(with: sample) as? [String: Any],
              var days = root["days"] as? [[String: Any]],
              days.contains(where: { $0["date"] as? String == mockDate })
        else { throw SampleError("the sample has no day dated \(mockDate)") }
        func moved(_ value: Any?) -> Any? {
            guard let number = value as? NSNumber else { return value }
            return NSNumber(value: number.int64Value + seconds)
        }
        root["writtenAt"] = moved(root["writtenAt"])
        for index in days.indices {
            if var times = days[index]["times"] as? [String: Any] {
                for key in ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha", "fajrEnd", "asrEnd"] where times[key] is NSNumber {
                    times[key] = moved(times[key])
                }
                days[index]["times"] = times
            }
            if let midnight = days[index]["midnight"] as? NSNumber, midnight.int64Value != 0 {
                days[index]["midnight"] = moved(midnight)
            }
        }
        root["days"] = days
        if let phrase {
            var phrases = root["tasbeeh"] as? [[String: Any]] ?? []
            phrases.append(["id": "debug-phrase", "phrase": phrase, "target": 33])
            root["tasbeeh"] = phrases
        }
        return try JSONSerialization.data(withJSONObject: root, options: [.withoutEscapingSlashes])
    }

    struct SampleError: Error, CustomStringConvertible {
        let description: String
        init(_ description: String) { self.description = description }
    }
}
#endif
