import Foundation
import XCTest
@testable import WatchCore

/// What every complication and the Smart Stack draw (plan 4 Task 6): the glance at an instant, and
/// the timeline the widgets are handed, the Lead's call 5 with plan 4 erratum p4 #10's late
/// entries. Every instant is the Cape Town sample's, given as epoch seconds: its day 1 is Sunday
/// 18 October 2026 (the mockups' day), since the sample starts yesterday.
final class GlanceTests: XCTestCase {
    /// U+2068 and U+2069, around a 12-hour time spliced into a spoken sentence.
    private let FSI = "\u{2068}"
    private let PDI = "\u{2069}"

    /// Cape Town (UTC+2 all year) at [hours]:[minutes]:[seconds] on the sample's day [day], where
    /// day 1 is Sunday 18 October 2026, 00:00 at 1792274400.
    private func capeTown(_ hours: Int64, _ minutes: Int64, _ seconds: Int64 = 0, day: Int64 = 1) -> Int64 {
        1_792_274_400 + (day - 1) * 86_400 + hours * 3_600 + minutes * 60 + seconds
    }

    private var sample: WatchFeed { get throws { try StoreFixtures.sample } }

    /// The Urdu phone's feed: Cape Town, 24 hours, Eastern Arabic digits.
    private var urdu: WatchFeed {
        get throws {
            guard case let .feed(feed) = FeedDecoder.decode(try Vectors.data("phone/phone-capetown-ur24.json")) else {
                throw VectorError("phone-capetown-ur24.json is no feed")
            }
            return feed
        }
    }

    /// [feed] as a phone written before the spoken words sends it (`ui.spoken` nil, as
    /// `olderPhone`'s is), everything else as it was.
    private func withoutSpokenWords(_ feed: WatchFeed) -> WatchFeed {
        let ui = feed.ui
        return WatchFeed(
            version: feed.version, writtenAt: feed.writtenAt, days: feed.days, place: feed.place,
            ui: WatchUi(
                language: ui.language, rtl: ui.rtl, digitZero: ui.digitZero, twelveHour: ui.twelveHour, am: ui.am, pm: ui.pm,
                countdown: ui.countdown, nextIn: ui.nextIn, spoken: nil, screens: ui.screens,
                ringDigitZero: ui.ringDigitZero, latinScript: ui.latinScript
            ),
            tasbeeh: feed.tasbeeh
        )
    }

    /// The entries of the coming hour: those at or before `now + horizon`.
    private func hour(_ timeline: GlanceTimeline, from now: Int64) -> [GlanceEntry] {
        timeline.entries.filter { $0.at <= now + Glances.horizon }
    }

    // MARK: - The glance

    func testAtTwoFiftyEightTheGlanceIsAsr() throws {
        let glance = try XCTUnwrap(Glances.make(try sample, now: capeTown(14, 58)))

        XCTAssertEqual(glance.prayer, .ASR)
        XCTAssertEqual(glance.label, "ASR")
        XCTAssertEqual(glance.name, "Asr")
        XCTAssertEqual(glance.time, "16:10")
        XCTAssertEqual(glance.countdown, "in 1 h 12 min")
        XCTAssertEqual(glance.progress, Float(145) / Float(217), "from Dhuhr at 12:33 to Asr at 16:10")
        XCTAssertEqual(glance.spoken, "Asr in 1 hour 12 minutes, at 16:10")
        XCTAssertEqual(glance.spokenWithoutCountdown, "Asr, 16:10")
        XCTAssertEqual(glance.previousAt, capeTown(12, 33))
        XCTAssertEqual(glance.nextAt, capeTown(16, 10))
        XCTAssertFalse(glance.rtl)
        XCTAssertTrue(glance.latinScript)
        XCTAssertEqual(glance.language, "en", "the feed's language, which VoiceOver speaks it in (erratum p4 #5)")
    }

    func testATwelveHourGlanceSaysItsDayPeriod() throws {
        let glance = try XCTUnwrap(Glances.make(try Vectors.feed("sampleEn12"), now: capeTown(14, 58)))

        XCTAssertEqual(glance.time, "4:10", "drawn bare, as the Galaxy's complications draw it")
        // The phone's own 12-hour time, with its no-break space, isolated in the sentence.
        XCTAssertEqual(glance.spoken, "Asr in 1 hour 12 minutes, at " + FSI + "4:10\u{00A0}PM" + PDI)
        XCTAssertEqual(glance.spokenWithoutCountdown, "Asr, " + FSI + "4:10\u{00A0}PM" + PDI)
    }

    func testAFridaysDhuhrIsJumuah() throws {
        let feed = try sample
        let jumuah = try XCTUnwrap(Vectors.feeds.labels.first { $0.feed == "sample" && $0.day == 6 && $0.prayer == "DHUHR" })
        XCTAssertEqual(feed.days[6].date, "2026-10-23", "day 6 is Friday 23 October")

        let friday = try XCTUnwrap(Glances.make(feed, now: capeTown(11, 58, day: 6)))
        XCTAssertEqual(friday.prayer, .DHUHR)
        XCTAssertEqual(friday.label, jumuah.label)
        XCTAssertEqual(friday.label, "JUMUʿAH")
        XCTAssertEqual(friday.name, "Jumuʿah")

        let thursday = try XCTUnwrap(Glances.make(feed, now: capeTown(11, 58, day: 5)))
        XCTAssertEqual(thursday.label, "DHUHR")
    }

    func testArabicKeepsTheNameAndTheDigits() throws {
        let glance = try XCTUnwrap(Glances.make(try Vectors.feed("sampleArSa"), now: capeTown(14, 58)))

        XCTAssertEqual(glance.name, "العصر")
        XCTAssertEqual(glance.label, "العصر", "no capitals in Arabic")
        XCTAssertEqual(glance.time, "١٦:١٠")
        XCTAssertEqual(glance.countdown, "بعد ١ س ١٢ د")
        XCTAssertTrue(glance.rtl)
        XCTAssertFalse(glance.latinScript)
        XCTAssertEqual(glance.language, "ar-SA")
        // Ruling 73 item 1 (M8 point A5): the row form takes the Arabic comma, as Today's rows do.
        XCTAssertEqual(glance.spokenWithoutCountdown, "العصر، ١٦:١٠")
    }

    /// M8 point A5 reaches the complication too (Ruling 73 item 1, 6 November 2026): an Arabic or
    /// Urdu entry without its countdown joins the name and the time with "، ", as this watch's
    /// Today and the phone's rows do, so its next-prayer lines read one join. A left-to-right feed
    /// keeps ", ". The Galaxy's complications join their parts with this comma (`spokenSeparator`).
    func testAnArabicOrUrduRowFormIsJoinedByTheArabicComma() throws {
        let arabic = try XCTUnwrap(Glances.make(try Vectors.feed("sampleArSa"), now: capeTown(14, 58)))
        XCTAssertEqual(arabic.spokenWithoutCountdown, "العصر، ١٦:١٠")
        guard case let .feed(urdu) = FeedDecoder.decode(try Vectors.data("phone/phone-capetown-ur24.json")) else {
            throw VectorError("phone-capetown-ur24.json is no feed")
        }
        let urduGlance = try XCTUnwrap(Glances.make(urdu, now: capeTown(14, 58)))
        XCTAssertEqual(urduGlance.spokenWithoutCountdown, "عصر، ۱۶:۱۰")
        let english = try XCTUnwrap(Glances.make(try sample, now: capeTown(14, 58)))
        XCTAssertEqual(english.spokenWithoutCountdown, "Asr, 16:10")
    }

    func testTheScreenWordsSpeakWhenTheFeedHasNoSpokenWords() throws {
        let older = try Vectors.feed("olderPhone")
        XCTAssertNil(older.ui.spoken)

        let glance = try XCTUnwrap(Glances.make(older, now: capeTown(14, 58)))

        XCTAssertEqual(glance.spoken, "Asr 16:10, in 1 h 12 min")
        XCTAssertEqual(glance.spokenWithoutCountdown, "Asr, 16:10")
    }

    /// Ruling 75 item 1: with no spoken words, the screen's words (the name and its time, then the
    /// countdown) are joined by the comma the row's form takes: "، " in Arabic and Urdu, a
    /// right-to-left feed, as this watch's Today joins its lines, ", " otherwise. So the
    /// complication and Today say one join on one watch, as the Galaxy's do (`SpokenLine`, joined
    /// by `spokenSeparator`). Bengali is `testABengaliFeedKeepsTheLatinCommaInEveryJoin`'s.
    func testWithNoSpokenWordsTheScreensWordsJoinAsTheRowsFormDoes() throws {
        let arabic = try XCTUnwrap(Glances.make(withoutSpokenWords(try Vectors.feed("sampleArSa")), now: capeTown(14, 58)))
        XCTAssertEqual(arabic.spoken, "العصر ١٦:١٠، بعد ١ س ١٢ د")
        let urdu = try XCTUnwrap(Glances.make(withoutSpokenWords(try urdu), now: capeTown(14, 58)))
        XCTAssertEqual(urdu.spoken, "عصر ۱۶:۱۰، ۱ گھنٹے ۱۲ منٹ میں")
        let english = try XCTUnwrap(Glances.make(withoutSpokenWords(try sample), now: capeTown(14, 58)))
        XCTAssertEqual(english.spoken, "Asr 16:10, in 1 h 12 min")
    }

    /// Ruling 75 item 2: Bengali is a feed in letters of its own written left to right (`ui.rtl`
    /// false, `ui.latinScript` false), and every join it says keeps ", ": the row's form, the
    /// screen's words with no spoken words, and the empty form's name and notice. The comma
    /// follows the direction, so a join that took it from the script (`!latinScript`) fails here.
    func testABengaliFeedKeepsTheLatinCommaInEveryJoin() throws {
        let bengali = try XCTUnwrap(Vectors.rules.feeds["capetown-bn"])
        XCTAssertEqual(bengali.ui.language, "bn")
        XCTAssertFalse(bengali.ui.rtl)
        XCTAssertFalse(bengali.ui.latinScript)

        let glance = try XCTUnwrap(Glances.make(bengali, now: capeTown(14, 58)))
        XCTAssertEqual(glance.spokenWithoutCountdown, "Asr, ১৬:১০", "the row's form")
        let older = try XCTUnwrap(Glances.make(withoutSpokenWords(bengali), now: capeTown(14, 58)))
        XCTAssertEqual(older.spoken, "Asr ১৬:১০, in ১ h ১২ min", "the screen's words with no spoken words")

        let pastTheEnd = try XCTUnwrap(WatchRules.starts(bengali).last).at + 60
        let own = OwnLines(appName: "the watch's name", openOnPhone: "the watch's open line", updateOnWatch: "the watch's update line")
        for (newer, notice) in [(false, "Open Taqwa on your phone"), (true, "Update Taqwa on your watch")] {
            let timeline = Glances.timeline(bengali, newerVersionSeen: newer, now: pastTheEnd)
            let content = NextPrayerContent(timeline.entries[0], of: timeline, own: own)
            XCTAssertNil(content.glance, "the empty form")
            XCTAssertEqual(String(content.spoken(inline: false).characters), "Taqwa, " + notice, "the empty form's name and notice")
        }
    }

    func testNoMomentIsNoGlance() throws {
        let feed = try sample
        let last = try XCTUnwrap(WatchRules.starts(feed).last)
        XCTAssertNil(Glances.make(feed, now: last.at), "past the feed's last start")
        XCTAssertNil(Glances.make(feed, now: feed.days[0].midnight - 1), "before the feed's first day")
    }

    // MARK: - The timeline: an entry for each minute of the coming hour

    func testTheEntriesBreakWhereTheCountdownChanges() throws {
        let feed = try sample
        let now = capeTown(14, 57, 30)

        let timeline = Glances.timeline(feed, newerVersionSeen: false, now: now)
        let hour = hour(timeline, from: now)

        let minutes = stride(from: capeTown(14, 58), through: capeTown(15, 57), by: 60).map { $0 }
        XCTAssertEqual(hour.map(\.at), [now] + minutes + [capeTown(15, 57, 30)])
        XCTAssertEqual(hour.count, 62)
        XCTAssertEqual(hour[0].glance?.countdown, "in 1 h 12 min", "72 min 30 s left")
        XCTAssertEqual(hour[1].glance?.countdown, "in 1 h 11 min", "from 14:58:00 the minute that holds is 71")
        XCTAssertEqual(hour[2].glance?.countdown, "in 1 h 10 min")
        for entry in hour.dropFirst().dropLast() {
            XCTAssertEqual(entry.glance, Glances.make(feed, now: entry.at + 1), "the glance that holds for that minute")
            XCTAssertTrue(entry.countdownShown)
        }
        XCTAssertEqual(hour[60].glance?.countdown, "in 12 min", "15:57:00")
        XCTAssertEqual(timeline.reloadAt, capeTown(15, 57, 30), "the hour's end comes before Asr")
    }

    func testPastTheHourTheCountdownIsDropped() throws {
        let now = capeTown(14, 57, 30)

        let last = try XCTUnwrap(hour(Glances.timeline(try sample, newerVersionSeen: false, now: now), from: now).last)

        XCTAssertEqual(last.at, capeTown(15, 57, 30))
        XCTAssertFalse(last.countdownShown, "a late reload never leaves a stale countdown")
        XCTAssertEqual(last.glance?.label, "ASR")
        XCTAssertEqual(last.glance?.time, "16:10")
        XCTAssertEqual(last.glance?.spokenWithoutCountdown, "Asr, 16:10", "said as the row is")
    }

    func testAStartInsideTheHourRollsOverAndReloadsThere() throws {
        let now = capeTown(15, 30)

        let timeline = Glances.timeline(try sample, newerVersionSeen: false, now: now)
        let hour = hour(timeline, from: now)

        let beforeAsr = stride(from: capeTown(15, 31), through: capeTown(16, 9), by: 60).map { $0 }
        let afterAsr = stride(from: capeTown(16, 11), through: capeTown(16, 29), by: 60).map { $0 }
        XCTAssertEqual(hour.map(\.at), [now] + beforeAsr + [capeTown(16, 10)] + afterAsr + [capeTown(16, 30)])
        let atAsr = try XCTUnwrap(hour.first { $0.at == capeTown(16, 10) })
        XCTAssertEqual(atAsr.glance?.prayer, .MAGHRIB, "an entry at a start counts to the start after it")
        XCTAssertEqual(atAsr.glance?.label, "MAGHRIB")
        XCTAssertEqual(atAsr.glance?.time, "19:04")
        XCTAssertEqual(atAsr.glance?.countdown, "in 2 h 53 min")
        XCTAssertTrue(atAsr.countdownShown)
        XCTAssertEqual(hour.first { $0.at == capeTown(16, 29) }?.glance?.countdown, "in 2 h 34 min")
        XCTAssertFalse(try XCTUnwrap(hour.last).countdownShown, "16:30:00")
        XCTAssertEqual(timeline.reloadAt, capeTown(16, 10), "the start comes before the hour's end")
    }

    func testUnderAMinuteIsTheLastMinute() throws {
        let hour = hour(Glances.timeline(try sample, newerVersionSeen: false, now: capeTown(15, 30)), from: capeTown(15, 30))

        XCTAssertEqual(hour.first { $0.at == capeTown(16, 8) }?.glance?.countdown, "in 1 min")
        XCTAssertEqual(hour.first { $0.at == capeTown(16, 9) }?.glance?.countdown, "in under a minute")
        XCTAssertEqual(hour.first { $0.at == capeTown(16, 9) }?.glance?.spoken, "Asr in under a minute, at 16:10")
    }

    func testTheTimelineIsAnHourOfMinutes() throws {
        // Tromsø in May: its longest period, from the middle of it.
        let tromso = try XCTUnwrap(Vectors.rules.feeds["tromso-may"])
        let starts = WatchRules.starts(tromso)
        let longest = try XCTUnwrap(zip(starts, starts.dropFirst()).max { $0.1.at - $0.0.at < $1.1.at - $1.0.at })
        XCTAssertGreaterThan(longest.1.at - longest.0.at, 6 * 3_600)
        var cases: [(String, WatchFeed, Int64)] = [("tromso-may", tromso, longest.0.at + (longest.1.at - longest.0.at) / 2)]
        for name in ["sample", "sampleEn12", "sampleArLy12", "sampleArSa"] {
            // From a whole minute (61 in the hour) and from half a minute past one (62, the most).
            cases.append((name, try Vectors.feed(name), capeTown(0, 0)))
            cases.append((name, try Vectors.feed(name), capeTown(0, 0, 30)))
        }

        for (name, feed, now) in cases {
            let timeline = Glances.timeline(feed, newerVersionSeen: false, now: now)
            let hour = hour(timeline, from: now)
            let startTimes = Set(WatchRules.starts(feed).map(\.at))

            // The 62 holds while every start is on a whole minute, as the phone writes them: the
            // minutes are counted back from each start, so a start off the minute would put its
            // own entry between two of them.
            XCTAssertTrue(startTimes.allSatisfy { $0 % 60 == 0 }, "\(name): every start is on a whole minute")
            XCTAssertLessThanOrEqual(hour.count, 62, name)
            XCTAssertGreaterThanOrEqual(hour.count, 61, name)
            XCTAssertEqual(hour.first?.at, now, name)
            XCTAssertEqual(hour.last?.at, now + Glances.horizon, name)
            let inside = Array(hour.dropFirst().dropLast())
            for (earlier, later) in zip(inside, inside.dropFirst()) {
                let atAStart = startTimes.contains(earlier.at) || startTimes.contains(later.at)
                XCTAssertTrue(later.at - earlier.at == 60 || atAStart, "\(name): \(earlier.at) to \(later.at)")
            }
            XCTAssertEqual(hour.filter { !$0.countdownShown }.map(\.at), [now + Glances.horizon], "\(name): only the last, within the hour")
            XCTAssertTrue(timeline.entries.dropFirst(hour.count).allSatisfy { !$0.countdownShown }, "\(name): and every late entry")
            XCTAssertLessThanOrEqual(timeline.entries.count, 72, "\(name): the documented 72, the hour's 62 and the 10 after it")
        }
    }

    // MARK: - A timeline made on a whole minute (P4.6 review round 1)

    func testAWholeMinuteNowDrawsTheMinuteThatHolds() throws {
        let feed = try sample
        let now = capeTown(14, 58)

        let hour = hour(Glances.timeline(feed, newerVersionSeen: false, now: now), from: now)

        // 72 minutes are left at 14:58:00 itself, and 71 through the rest of its minute.
        XCTAssertEqual(hour.map(\.at), stride(from: now, through: capeTown(15, 58), by: 60).map { $0 })
        XCTAssertEqual(hour[0].glance?.countdown, "in 1 h 11 min", "the minute that holds, never one second's")
        XCTAssertEqual(hour[0].glance, Glances.make(feed, now: now + 1))
        XCTAssertTrue(hour[0].countdownShown)
        XCTAssertEqual(hour[1].glance?.countdown, "in 1 h 10 min", "14:59:00: no minute skipped")
        XCTAssertEqual(hour[59].glance?.countdown, "in 12 min", "15:57:00")
        XCTAssertFalse(try XCTUnwrap(hour.last).countdownShown, "15:58:00, the hour's end")
    }

    func testATimelineMadeAtAStartDrawsTheMinuteThatHolds() throws {
        // A WidgetKit `.after(start)` reload that runs in the start's first second.
        let feed = try sample
        let now = capeTown(16, 10)

        let timeline = Glances.timeline(feed, newerVersionSeen: false, now: now)
        let hour = hour(timeline, from: now)

        XCTAssertEqual(hour[0].at, now)
        XCTAssertEqual(hour[0].glance?.prayer, .MAGHRIB)
        XCTAssertEqual(hour[0].glance?.countdown, "in 2 h 53 min", "2 h 54 min holds at 16:10:00 alone")
        XCTAssertEqual(hour[1].at, capeTown(16, 11))
        XCTAssertEqual(hour[1].glance?.countdown, "in 2 h 52 min")
        XCTAssertEqual(timeline.reloadAt, capeTown(17, 10), "Maghrib is past the hour")
    }

    func testEveryEntryShowsWhatHoldsUntilTheNext() throws {
        // Whole-minute, half-minute and one-second-off nows, at and around the starts, on every
        // sample and on Tromsø's May feed: each entry of the hour draws the prayer and the
        // countdown that hold through its span, so no minute is shown late or skipped.
        var cases: [(String, WatchFeed)] = [("tromso-may", try XCTUnwrap(Vectors.rules.feeds["tromso-may"]))]
        for name in ["sample", "sampleEn12", "sampleArLy12", "sampleArSa"] {
            cases.append((name, try Vectors.feed(name)))
        }
        var checked = 0
        for (name, feed) in cases {
            let starts = WatchRules.starts(feed).map(\.at)
            var nows: [Int64] = [feed.days[1].midnight, feed.days[1].midnight + 30]
            for start in starts.dropFirst(5).prefix(10) {
                nows += [start - 3_600, start - 61, start - 60, start - 59, start - 30, start - 1, start, start + 1, start + 30, start + 60]
            }
            for now in nows {
                let hour = hour(Glances.timeline(feed, newerVersionSeen: false, now: now), from: now)
                XCTAssertEqual(hour.first?.at, now, "\(name) \(now)")
                for (entry, following) in zip(hour, hour.dropFirst()) where entry.countdownShown {
                    let drawn = try XCTUnwrap(entry.glance, "\(name) \(now) \(entry.at)")
                    for t in Set([entry.at + 1, following.at - 1]) where t > entry.at && t < following.at {
                        let holds = try XCTUnwrap(Glances.make(feed, now: t))
                        XCTAssertEqual(drawn.prayer, holds.prayer, "\(name) from \(now): \(entry.at) drawn, \(t) holds")
                        XCTAssertEqual(drawn.countdown, holds.countdown, "\(name) from \(now): \(entry.at) drawn, \(t) holds")
                        checked += 1
                    }
                    // A first entry one second before the next (now = a minute's or a start's
                    // last second) draws that instant's glance, the only one it holds.
                    if entry.at == now, following.at == now + 1 {
                        XCTAssertEqual(drawn, Glances.make(feed, now: now), "\(name) from \(now): its instant")
                    }
                }
            }
        }
        XCTAssertGreaterThan(checked, 10_000)
    }

    // MARK: - A late reload (erratum p4 #10)

    func testALateReloadStillNamesTheNextPrayer() throws {
        let feed = try sample
        let now = capeTown(14, 57, 30)

        let timeline = Glances.timeline(feed, newerVersionSeen: false, now: now)
        let late = Array(timeline.entries.dropFirst(62))

        // After the hour, one entry at each of the next ten starts, without the countdown, each
        // naming the prayer after its start: WidgetKit's `.after` reload is no earlier than, so a
        // late one never leaves the face naming a prayer that has begun.
        XCTAssertEqual(Glances.lateStarts, 10)
        XCTAssertEqual(late.map(\.at), [
            capeTown(16, 10), capeTown(19, 4), capeTown(20, 24),
            capeTown(4, 33, day: 2), capeTown(12, 33, day: 2), capeTown(16, 10, day: 2), capeTown(19, 4, day: 2), capeTown(20, 24, day: 2),
            capeTown(4, 33, day: 3), capeTown(12, 33, day: 3),
        ])
        XCTAssertEqual(late.compactMap(\.glance?.prayer), [.MAGHRIB, .ISHA, .FAJR, .DHUHR, .ASR, .MAGHRIB, .ISHA, .FAJR, .DHUHR, .ASR])
        for entry in late {
            XCTAssertFalse(entry.countdownShown)
            XCTAssertEqual(entry.glance, Glances.make(feed, now: entry.at + 1))
        }
        XCTAssertEqual(timeline.reloadAt, capeTown(15, 57, 30), "the reload is unchanged")
        XCTAssertEqual(timeline.entries.count, 72)
    }

    func testLateEntriesThatReachTheFeedsEndEndInTheEmptyEntry() throws {
        let feed = try sample
        let lastDay = Int64(feed.days.count - 1) // day 30, 16 November
        let now = capeTown(12, 0, day: lastDay)

        let timeline = Glances.timeline(feed, newerVersionSeen: false, now: now)
        let late = Array(timeline.entries.dropFirst(hour(timeline, from: now).count))

        XCTAssertEqual(late.map(\.at), [capeTown(16, 10, day: lastDay), capeTown(19, 4, day: lastDay), capeTown(20, 24, day: lastDay)])
        XCTAssertEqual(late.map(\.glance?.prayer), [.MAGHRIB, .ISHA, nil], "Isha is the feed's last start")
        XCTAssertEqual(timeline.entries.last, GlanceEntry(at: capeTown(20, 24, day: lastDay), glance: nil, relevant: false, countdownShown: false))
    }

    // MARK: - The date's turn (Ruling 28, Ruling 64 item 2)

    /// One of Tromsø's May feeds (`watch-rules-vectors.json`), in Norway's summer time, UTC+2: day
    /// 1 is Monday 18 May 2026, from midnight at 1779055200; the feed ends at 20 May's midnight.
    /// In `tromso-late-maghrib` 18 May's Maghrib (00:45 on the 19th) falls after the 19th's Fajr
    /// (00:38), so it is counted to until midnight and dropped there. In both, the 19th's Maghrib
    /// falls after the feed's end.
    private func tromso(_ name: String) throws -> WatchFeed {
        try XCTUnwrap(Vectors.rules.feeds[name], name)
    }

    /// Tromsø's local time on day [day] of the feed (day 1 is 18 May), past midnight when [hours]
    /// is 24 or more.
    private func tromsoTime(_ hours: Int64, _ minutes: Int64, day: Int64 = 1) -> Int64 {
        1_779_055_200 + (day - 1) * 86_400 + hours * 3_600 + minutes * 60
    }

    func testPastTheHourAMaghribDroppedAtMidnightIsDroppedThere() throws {
        let feed = try tromso("tromso-late-maghrib")
        let now = tromsoTime(12, 0)

        let timeline = Glances.timeline(feed, newerVersionSeen: false, now: now)
        let late = timeline.entries.filter { $0.at > now + Glances.horizon }

        // At midnight Fajr (00:38) is the next start, not the dropped Maghrib (00:45); from Fajr's
        // start, Dhuhr; and at the feed's last midnight the empty form, not at a Maghrib past it.
        XCTAssertEqual(late.map(\.at), [
            tromsoTime(17, 9), tromsoTime(24, 0), tromsoTime(24, 38),
            tromsoTime(12, 46, day: 2), tromsoTime(17, 8, day: 2), tromsoTime(24, 0, day: 2),
        ])
        XCTAssertEqual(late.map(\.glance?.prayer), [.MAGHRIB, .FAJR, .DHUHR, .ASR, .MAGHRIB, nil])
        XCTAssertEqual(late[1].glance?.nextAt, tromsoTime(24, 38), "Fajr's start, after midnight")
        for entry in late.dropLast() {
            XCTAssertFalse(entry.countdownShown)
            XCTAssertEqual(entry.glance, Glances.make(feed, now: entry.at + 1), "what holds from \(entry.at)")
        }
        XCTAssertEqual(timeline.entries.last, GlanceEntry(at: tromsoTime(24, 0, day: 2), glance: nil, relevant: false, countdownShown: false))
        XCTAssertEqual(timeline.reloadAt, tromsoTime(12, 46), "the reload is unchanged")
    }

    func testPastTheHourTheEmptyFormComesAtTheFeedsLastMidnight() throws {
        let feed = try tromso("tromso-may")
        let now = tromsoTime(12, 0)

        let late = Glances.timeline(feed, newerVersionSeen: false, now: now).entries.filter { $0.at > now + Glances.horizon }

        // 18 May's Maghrib (00:21 on the 19th) comes before the 19th's Fajr, so it is no turn of
        // the answer; the 19th's Maghrib (00:29 on the 20th) is past the feed's end.
        XCTAssertEqual(late.map(\.at), [
            tromsoTime(17, 9), tromsoTime(24, 21), tromsoTime(24, 34),
            tromsoTime(12, 46, day: 2), tromsoTime(17, 8, day: 2), tromsoTime(24, 0, day: 2),
        ])
        XCTAssertEqual(late.map(\.glance?.prayer), [.MAGHRIB, .FAJR, .DHUHR, .ASR, .MAGHRIB, nil])
    }

    func testTheRelevanceWindowsTurnOverWithTheDate() throws {
        // tromso-late-maghrib: before midnight the dropped Maghrib is more than half an hour off,
        // and after it Fajr is the start to lift Taqwa for; the 19th's Maghrib is past the end.
        XCTAssertEqual(Glances.relevanceWindows(try tromso("tromso-late-maghrib"), now: tromsoTime(12, 0)), [
            tromsoTime(12, 16)...tromsoTime(12, 46), tromsoTime(16, 39)...tromsoTime(17, 9),
            tromsoTime(24, 8)...tromsoTime(24, 38),
            tromsoTime(12, 16, day: 2)...tromsoTime(12, 46, day: 2), tromsoTime(16, 38, day: 2)...tromsoTime(17, 8, day: 2),
        ])
        // tromso-may: Fajr, 13 minutes after Maghrib, is lifted from Maghrib's start; the 19th's
        // Maghrib is counted to from 23:59 until the feed ends at midnight.
        XCTAssertEqual(Glances.relevanceWindows(try tromso("tromso-may"), now: tromsoTime(12, 0)), [
            tromsoTime(12, 16)...tromsoTime(12, 46), tromsoTime(16, 39)...tromsoTime(17, 9),
            tromsoTime(23, 51)...tromsoTime(24, 21), tromsoTime(24, 21)...tromsoTime(24, 34),
            tromsoTime(12, 16, day: 2)...tromsoTime(12, 46, day: 2), tromsoTime(16, 38, day: 2)...tromsoTime(17, 8, day: 2),
            tromsoTime(23, 59, day: 2)...tromsoTime(24, 0, day: 2),
        ])
    }

    /// Ruling 64 item 2's probe, committed: from instants around every start and every turn of the
    /// date of both Tromsø feeds, every entry draws, through its whole span, what holds there
    /// (the prayer, its label and time, and within the hour the countdown); an empty entry stands
    /// where nothing holds; and the relevance windows are exactly the instants the entries call
    /// relevant.
    func testEveryEntryAndWindowHoldsAcrossTheDatesTurn() throws {
        var spans = 0
        var mismatches: [String] = []
        for name in ["tromso-late-maghrib", "tromso-may"] {
            let feed = try tromso(name)
            let starts = WatchRules.starts(feed).map(\.at)
            let turns = feed.days.map(\.midnight) + [feed.days[feed.days.count - 1].midnight + 86_400]
            var nows: [Int64] = []
            for start in starts {
                nows += [start - 3_600, start - 3_599, start - 61, start - 60, start - 1, start, start + 1, start + 59]
            }
            for midnight in turns {
                nows += [midnight - 86_400 / 2, midnight - 3_600, midnight - 1_800, midnight - 60, midnight - 1, midnight, midnight + 1, midnight + 30]
            }
            for now in nows where Glances.make(feed, now: now) != nil {
                let timeline = Glances.timeline(feed, newerVersionSeen: false, now: now)
                let entries = timeline.entries
                XCTAssertLessThanOrEqual(entries.count, 72, "\(name) from \(now)")
                for (entry, following) in zip(entries, entries.dropFirst()) {
                    if following.at <= entry.at { mismatches.append("\(name) from \(now): \(entry.at) then \(following.at)") }
                    for t in Set([entry.at + 1, following.at - 1, (entry.at + following.at) / 2]) where t > entry.at && t < following.at {
                        spans += 1
                        let holds = Glances.make(feed, now: t)
                        guard let drawn = entry.glance, let holds else {
                            if entry.glance != nil || holds != nil {
                                mismatches.append("\(name) from \(now): \(entry.at) draws \(entry.glance?.label ?? "the empty form"), \(t) holds \(holds?.label ?? "nothing")")
                            }
                            continue
                        }
                        if drawn.prayer != holds.prayer || drawn.nextAt != holds.nextAt || drawn.label != holds.label || drawn.time != holds.time
                            || (entry.countdownShown && drawn.countdown != holds.countdown) {
                            mismatches.append("\(name) from \(now): \(entry.at) draws \(drawn.label) \(drawn.time), \(t) holds \(holds.label) \(holds.time)")
                        }
                    }
                }
                // Past the hour, at most `lateStarts` entries; a timeline that stops short of the
                // feed's end holds all of them.
                let late = entries.filter { $0.at > now + Glances.horizon }
                if late.count > Glances.lateStarts || (entries.last?.glance != nil && late.count != Glances.lateStarts) {
                    mismatches.append("\(name) from \(now): \(late.count) late entries")
                }

                // The windows, instant by instant, against the entries' own rule: every five
                // minutes from now, and either side of each window's ends, each start and each turn
                // of the date, where the rule's answer can change.
                let windows = Glances.relevanceWindows(feed, now: now, count: 100)
                for (earlier, later) in zip(windows, windows.dropFirst()) where later.lowerBound < earlier.upperBound {
                    mismatches.append("\(name) from \(now): windows overlap at \(later.lowerBound)")
                }
                let through = windows.last?.upperBound ?? now
                let changes = windows.flatMap { [$0.lowerBound, $0.upperBound] } + starts + turns
                let instants = Array(stride(from: now, to: through, by: 300)) + changes.flatMap { [$0 - 1, $0, $0 + 1] }
                for t in instants where t >= now && t <= through {
                    let relevant = Glances.make(feed, now: t).map { t >= $0.nextAt - Glances.relevantBefore && t < $0.nextAt } ?? false
                    let inAWindow = windows.contains { $0.lowerBound <= t && t < $0.upperBound }
                    if relevant != inAWindow { mismatches.append("\(name) from \(now): at \(t) relevant \(relevant), in a window \(inAWindow)") }
                }
            }
        }
        XCTAssertEqual(mismatches.count, 0, "\(mismatches.count) mismatches, the first: \(mismatches.prefix(5))")
        XCTAssertGreaterThan(spans, 10_000)
    }

    // MARK: - Without a usable feed

    func testASpentFeedEndsInTheEmptyEntry() throws {
        let feed = try sample
        let lastDay = Int64(feed.days.count - 1)
        let isha = capeTown(20, 24, day: lastDay)

        // Inside the hour: the minutes up to the feed's last start, which is the empty entry.
        let ending = Glances.timeline(feed, newerVersionSeen: false, now: capeTown(20, 0, day: lastDay))
        XCTAssertEqual(ending.entries.last, GlanceEntry(at: isha, glance: nil, relevant: false, countdownShown: false))
        XCTAssertEqual(ending.entries.filter { $0.glance == nil }.count, 1, "one empty entry, the last")
        XCTAssertEqual(ending.reloadAt, isha)

        // Past it: the empty entry alone, with the feed's words for its "Open Taqwa on your phone".
        let spent = Glances.timeline(feed, newerVersionSeen: false, now: isha + 60)
        XCTAssertEqual(spent.entries, [GlanceEntry(at: isha + 60, glance: nil, relevant: false, countdownShown: false)])
        XCTAssertNil(spent.reloadAt, "nothing is coming: the next feed reloads it")
        XCTAssertEqual(spent.words, feed.words)
        XCTAssertEqual(spent.words?.openOnPhone, "Open Taqwa on your phone")
        XCTAssertFalse(spent.update)
        XCTAssertEqual(spent.script, GlanceScript(language: "en", rtl: false, latinScript: true))
    }

    func testNoFeedIsOneEmptyEntry() {
        let now = capeTown(14, 58)

        let timeline = Glances.timeline(nil, newerVersionSeen: false, now: now)

        XCTAssertEqual(timeline, GlanceTimeline(
            entries: [GlanceEntry(at: now, glance: nil, relevant: false, countdownShown: false)],
            reloadAt: nil, words: nil, update: false, script: nil
        ))
    }

    func testANewerFormatSeenSaysUpdateInTheEmptyForm() throws {
        let now = capeTown(14, 58)
        let none = Glances.timeline(nil, newerVersionSeen: true, now: now)
        XCTAssertTrue(none.update)
        XCTAssertEqual(none.entries, [GlanceEntry(at: now, glance: nil, relevant: false, countdownShown: false)])
        XCTAssertNil(none.words, "the watch's own update_taqwa_on_watch, in the watch's language")

        let feed = try sample
        let pastTheEnd = try XCTUnwrap(WatchRules.starts(feed).last).at + 60
        let spent = Glances.timeline(feed, newerVersionSeen: true, now: pastTheEnd)
        XCTAssertTrue(spent.update)
        XCTAssertNil(spent.entries[0].glance)
        XCTAssertEqual(spent.words?.updateOnWatch, "Update Taqwa on your watch", "the feed's own words")

        // With a feed still running, the complications draw from it, as every page does.
        let running = Glances.timeline(feed, newerVersionSeen: true, now: now)
        XCTAssertTrue(running.update)
        XCTAssertEqual(running.entries[0].glance, Glances.make(feed, now: now + 1), "14:58:00, a whole minute: the minute that holds")
    }

    func testTheTimelineCarriesTheFeedsScriptAndWordsOnce() throws {
        let arabic = try Vectors.feed("sampleArSa")
        let timeline = Glances.timeline(arabic, newerVersionSeen: false, now: capeTown(14, 58))

        XCTAssertEqual(timeline.script, GlanceScript(language: "ar-SA", rtl: true, latinScript: false))
        XCTAssertEqual(timeline.words, arabic.words)
        XCTAssertEqual(timeline.words?.appName, "تقوى")
    }

    // MARK: - Relevance: the half hour before each start

    func testOnlyTheHalfHourBeforeAStartIsRelevant() throws {
        XCTAssertEqual(Glances.relevantBefore, 1_800)
        let timeline = Glances.timeline(try sample, newerVersionSeen: false, now: capeTown(15, 30))
        func relevant(_ at: Int64) throws -> Bool { try XCTUnwrap(timeline.entries.first { $0.at == at }).relevant }

        XCTAssertFalse(try relevant(capeTown(15, 30)))
        XCTAssertFalse(try relevant(capeTown(15, 39)))
        XCTAssertTrue(try relevant(capeTown(15, 40)), "half an hour before Asr")
        XCTAssertTrue(try relevant(capeTown(16, 9)))
        XCTAssertFalse(try relevant(capeTown(16, 10)), "Asr has begun: Maghrib is hours away")
        XCTAssertFalse(try relevant(capeTown(16, 30)))
        XCTAssertFalse(Glances.timeline(nil, newerVersionSeen: false, now: capeTown(15, 50)).entries[0].relevant)
    }

    func testTheRelevanceWindowsAreTheComingStarts() throws {
        let windows = Glances.relevanceWindows(try sample, now: capeTown(14, 58))

        let starts = [
            capeTown(16, 10), capeTown(19, 4), capeTown(20, 24),
            capeTown(4, 33, day: 2), capeTown(12, 33, day: 2), capeTown(16, 10, day: 2), capeTown(19, 4, day: 2), capeTown(20, 24, day: 2),
            capeTown(4, 33, day: 3), capeTown(12, 33, day: 3),
        ]
        XCTAssertEqual(windows, starts.map { ($0 - 1_800)...$0 })
        XCTAssertEqual(Glances.relevanceWindows(try sample, now: capeTown(14, 58), count: 2), Array(windows.prefix(2)))

        let feed = try sample
        let pastTheEnd = try XCTUnwrap(WatchRules.starts(feed).last).at
        XCTAssertEqual(Glances.relevanceWindows(feed, now: pastTheEnd), [], "no start is coming")
    }

    // MARK: - VoiceOver's language (erratum p4 #5)

    func testASpokenLineCarriesTheFeedsLanguage() {
        let line = Speech.line("العصر بعد ساعة واحدة", language: "ar-SA")

        XCTAssertEqual(String(line.characters), "العصر بعد ساعة واحدة")
        XCTAssertEqual(line.runs.count, 1)
        XCTAssertEqual(line.runs.first?.languageIdentifier, "ar-SA")
    }

    func testTheWatchsOwnLineCarriesNone() {
        let line = Speech.line("Open Taqwa on your phone", language: nil)

        XCTAssertEqual(String(line.characters), "Open Taqwa on your phone")
        XCTAssertNil(line.runs.first?.languageIdentifier, "the watch's own words are in the watch's language")
    }
}
