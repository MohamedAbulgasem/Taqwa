import Foundation

/// Where a row of the day's list stands at a moment: the lit prayer, a time whose start has passed,
/// or one still to come. Sunrise is never current: it is no prayer, and only ends Fajr.
enum RowState: Equatable {
    case current, passed, upcoming
}

/// One of the day's times as a row draws and says it (plan 3's `RowModel`): [name] and [clock]
/// as the phone writes them, [status] for the pip and the colours, [small] for Sunrise, [spoken]
/// as the phone says the row ("Asr, 16:10", and "العصر، ١٦:١٠" with the Arabic comma in Arabic and
/// Urdu, M8 point A5), and [stateWord], what VoiceOver is told after it ("Current prayer",
/// "Passed", "Next prayer"), or nil.
struct TodayRow: Equatable {
    let time: WatchTime
    let name: String
    let clock: String
    let status: RowState
    let small: Bool
    let spoken: String
    let stateWord: String?
}

/// Today, ready: the phone's countdown ring (`aToday`) above the day's rows.
///
/// - [ringLabel], "ASR IN"; [countdown], the ring's H:MM:SS (H:MM on the always-on screen);
///   [time], the next start's clock time, or the update notice in its place when [notice];
/// - [progress], the ring's fill, anticlockwise when [mirrorsRing];
/// - [spoken], the one sentence VoiceOver reads for the ring, which changes once a minute and
///   never each second;
/// - [rows], the day's six times, or its five prayers where the phone hides Sunrise;
/// - [language], the feed's language tag, which every spoken line carries (plan 4 erratum p4 #5),
///   and [latinScript], whether the type is Manrope with capitals (`ui.latinScript`).
struct TodayModel: Equatable {
    let ringLabel: String
    let countdown: String
    let time: String
    let notice: Bool
    let progress: Float
    let spoken: String
    let rows: [TodayRow]
    let mirrorsRing: Bool
    let language: String
    let latinScript: Bool

    /// The row the list opens scrolled to: the lit row, or with none lit, the next start's.
    var focus: WatchTime? {
        rows.first { $0.status == .current }?.time ?? rows.first { !$0.small && $0.status == .upcoming }?.time
    }
}

/// When Today's list goes back to its focus (`TodayModel.focus`), the lit row or the next, and
/// when it keeps the wearer's scroll (Ruling 64 item 4). It goes there when it first appears; when
/// the app is opened again after it went to the background (from the clock face, a complication or
/// the Dock), since watchOS resumes the views it kept and `onAppear` does not run again; and
/// whenever the focus moves with the app open (the next start lit, Sunrise ending Fajr, the date's
/// turn at the feed's midnight). A lowered wrist is not a reopening: by Apple's Always On rules it
/// makes the app inactive and leaves it frontmost, dimmed, and a raised wrist active again, as a
/// banner does, so a scroll the wearer made stays, and the always-on frame does not jump.
struct TodayScroll {
    /// The scene's phase, as SwiftUI's `ScenePhase` names it.
    enum Phase: Equatable {
        case active, inactive, background
    }

    /// The scene has been in the background since it was last active.
    private var wasInBackground = false

    /// Whether the list goes to its focus as the scene enters [phase]: only on the first active
    /// after a background.
    mutating func refocuses(entering phase: Phase) -> Bool {
        switch phase {
        case .background:
            wasInBackground = true
            return false
        case .inactive:
            return false
        case .active:
            defer { wasInBackground = false }
            return wasInBackground
        }
    }

    /// Whether the list goes to its focus when the model's focus is [new] where it was [old]: when
    /// it has moved, in any phase.
    static func refocuses(focusFrom old: WatchTime?, to new: WatchTime?) -> Bool {
        old != new
    }
}

/// The watch's own two lines, shown before it holds a feed, in the watch's own language (spec 1.3).
enum OwnMessage: Equatable {
    case openOnPhone, updateOnWatch
}

/// What the Today page shows (plan 3's call 5 order): the watch's own line with no feed, the
/// feed's word when the feed is spent, and otherwise the ring above the day.
enum TodayScreen: Equatable {
    case message(OwnMessage)
    case spent(String)
    case ready(TodayModel)
}

/// Today's rules (plan 4 Task 7): plan 3's `today` and `dayRows` (`wearApp`'s `TodayModel.kt`),
/// restated, with the list lit by the Prayer screen's rule (`WatchRules.litOnList`). Every word is
/// the feed's, every time the feed's string, and every number goes through `WatchRules`.
enum Today {
    /// Today at [now] (epoch seconds). With no feed, the watch's own line: the update once a newer
    /// format has been seen, else "Open Taqwa on your phone". A feed with no start ahead of [now]
    /// is spent, and says the same in the feed's own words. Otherwise the ring and the rows, with
    /// the update notice in the time's place once a newer format has been seen: the ring still
    /// counts down from the last feed it understood.
    ///
    /// Without [seconds] (the always-on screen, drawn once a minute on the minute) the screen is
    /// the one that holds for the minute from [now]: when [now] is a whole minute before the next
    /// start, its countdown holds for that instant alone, so the screen is the one a second later,
    /// as a complication's minute is (`Glances.timeline`, Review Focus 3).
    static func screen(_ feed: WatchFeed?, newerVersionSeen: Bool, now: Int64, seconds: Bool = true) -> TodayScreen {
        guard let feed else { return .message(newerVersionSeen ? .updateOnWatch : .openOnPhone) }
        let words = feed.words // read once for the whole screen (plan 4 erratum p4 #12)
        guard var moment = WatchRules.moment(feed, now: now) else {
            return .spent(newerVersionSeen ? words.updateOnWatch : words.openOnPhone)
        }
        var at = now
        if !seconds, moment.secondsLeft % 60 == 0, let held = WatchRules.moment(feed, now: now &+ 1) {
            at = now &+ 1
            moment = held
        }
        guard feed.days.indices.contains(moment.next.dayIndex) else {
            return .spent(newerVersionSeen ? words.updateOnWatch : words.openOnPhone)
        }

        let day = feed.days[moment.next.dayIndex]
        let next = WatchTime.of(moment.next.prayer)
        let name = day.names.of(next)
        let filled = WatchRules.filled(feed.ui.nextIn, name)
        let ringLabel = feed.ui.latinScript ? WatchRules.capitals(filled, language: feed.ui.language) : filled
        let clock = WatchRules.clockText(day, next)
        let said = WatchRules.spokenCountdown(feed, name: name, clock: WatchRules.spokenClock(day, next), secondsLeft: moment.secondsLeft)
            ?? "\(ringLabel) \(clock)"

        return .ready(TodayModel(
            ringLabel: ringLabel,
            countdown: Self.clock(feed, secondsLeft: moment.secondsLeft, seconds: seconds),
            time: newerVersionSeen ? words.updateOnWatch : clock,
            notice: newerVersionSeen,
            progress: moment.progress,
            spoken: newerVersionSeen ? said + separator(feed.ui.rtl) + words.updateOnWatch : said,
            rows: rows(feed, words, now: at, next: moment.next),
            mirrorsRing: feed.ui.rtl,
            language: feed.ui.language,
            latinScript: feed.ui.latinScript
        ))
    }

    /// [secondsLeft] as the phone ring's H:MM:SS (`CountdownFormatter`), or H:MM without
    /// [seconds], the hours unpadded and never negative, in the phone ring's digits:
    /// `ringDigitZero`, or the feed's `digitZero` when it names none.
    static func clock(_ feed: WatchFeed, secondsLeft: Int64, seconds: Bool) -> String {
        let total = max(secondsLeft, 0)
        let hours = total / 3_600
        let minutes = (total % 3_600) / 60
        let text = seconds ? "\(hours):\(twoDigits(minutes)):\(twoDigits(total % 60))" : "\(hours):\(twoDigits(minutes))"
        return WatchRules.inDigits(text, feed.ui.ringDigitZero ?? feed.ui.digitZero)
    }

    /// The day's rows at [now], those of the day [now] is on by the feed's own midnights
    /// (`WatchRules.today`), lit by the Prayer screen's rule; empty when the feed has no such day.
    /// They are its six times while the phone shows Sunrise (`ui.showSunrise`), and its five
    /// prayers alone while it does not, as the Prayer screen leaves its Sunrise row out and changes
    /// nothing else.
    /// A row is current for the lit prayer, passed once its start is at or before [now], and
    /// upcoming otherwise. Its state word is the phone's `PrayerTimeline` row's: current, passed,
    /// or the next start's ([next], the same prayer at the same instant), else none.
    private static func rows(_ feed: WatchFeed, _ words: WatchScreenWords, now: Int64, next: WatchStart) -> [TodayRow] {
        guard let index = WatchRules.today(feed, now: now) else { return [] }
        let day = feed.days[index]
        let lit = WatchRules.litOnList(feed, now: now).map(WatchTime.of)
        return WatchTime.allCases.filter { feed.ui.showSunrise || $0 != .SUNRISE }.map { time in
            let at = day.times.of(time)
            let status: RowState
            if time != .SUNRISE && time == lit {
                status = .current
            } else if at <= now {
                status = .passed
            } else {
                status = .upcoming
            }
            let isNext = time != .SUNRISE && WatchTime.of(next.prayer) == time && next.at == at
            let stateWord: String?
            switch status {
            case .current: stateWord = words.rowCurrent
            case .passed: stateWord = words.rowPassed
            case .upcoming: stateWord = isNext ? words.rowNext : nil
            }
            let name = day.names.of(time)
            return TodayRow(
                time: time,
                name: name,
                clock: WatchRules.clockText(day, time),
                status: status,
                small: time == .SUNRISE,
                spoken: name + separator(feed.ui.rtl) + WatchRules.spokenClock(day, time),
                stateWord: stateWord
            )
        }
    }

    /// The comma a spoken line is joined with: the Arabic one for a right-to-left phone
    /// (`ComplicationFields.spokenSeparator`).
    private static func separator(_ rtl: Bool) -> String { rtl ? "، " : ", " }

    private static func twoDigits(_ n: Int64) -> String { n < 10 ? "0\(n)" : "\(n)" }
}

extension WatchTimes {
    /// The instant of [time] on this day.
    func of(_ time: WatchTime) -> Int64 {
        switch time {
        case .FAJR: return fajr
        case .SUNRISE: return sunrise
        case .DHUHR: return dhuhr
        case .ASR: return asr
        case .MAGHRIB: return maghrib
        case .ISHA: return isha
        }
    }
}
