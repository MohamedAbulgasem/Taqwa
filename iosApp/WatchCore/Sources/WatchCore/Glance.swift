import Foundation

/// What a complication or the Smart Stack draws at one instant (plan 4 Task 6, spec 4.3): the next
/// start, in the phone's words and digits, and the share of its period gone. Every string is the
/// feed's, filled by `WatchRules`; nothing here reads a clock, a formatter or the watch's language.
struct Glance: Equatable {
    /// The prayer whose start is next.
    let prayer: WatchPrayer
    /// Its name in capitals under a Latin-script interface (`WatchRules.label`, "ASR", "JUMUʿAH" on a
    /// Friday), as `ComplicationFields.nextPrayer` writes it, from the start's own day.
    let label: String
    /// Its name as the phone writes it ("Asr"): what VoiceOver says.
    let name: String
    /// Its clock time as the day's bare `clock` ("16:10", or "4:10" from a 12-hour phone), as the
    /// Galaxy's complications draw it.
    let time: String
    /// The time left in the feed's phrases and digits ("in 1 h 12 min").
    let countdown: String
    /// The one sentence VoiceOver reads: the feed's spoken countdown ("Asr in 1 hour 12 minutes, at
    /// 16:10"), or with no spoken words, the screen's ("Asr 16:10, in 1 h 12 min", plan 1's form,
    /// joined with the row's form's comma: "، " in Arabic and Urdu, Ruling 75 item 1).
    let spoken: String
    /// The row's form, the phone's `prayerRowDescription` ("Asr, 16:10", and "العصر، ١٦:١٠" with
    /// the Arabic comma in Arabic and Urdu, M8 point A5, Ruling 73 item 1): what an entry without
    /// its countdown says.
    let spokenWithoutCountdown: String
    /// The share of the period from the last start to the next, from 0 to 1: the ring's fill.
    let progress: Float
    let previousAt: Int64
    let nextAt: Int64
    /// Whether the feed's language is written right to left (`ui.rtl`).
    let rtl: Bool
    /// Whether the interface is in Latin letters (`ui.latinScript`): only then capitals and tracking.
    let latinScript: Bool
    /// The feed's language tag (`ui.language`): VoiceOver speaks the sentence in it, whatever the
    /// watch's own language (plan 4 erratum p4 #5, `Speech.line`).
    let language: String
}

/// One entry of the timeline: what holds from [at] until the next entry. [glance] is nil for the
/// empty form. [relevant] lifts the Smart Stack (the half hour before a start). An entry without
/// [countdownShown] draws the label and the time alone, and says the row's form.
struct GlanceEntry: Equatable {
    let at: Int64
    let glance: Glance?
    let relevant: Bool
    let countdownShown: Bool
}

/// How the feed writes, for a form drawn with no glance: the empty form of a spent feed is in the
/// feed's language, direction and type, as its glances were.
struct GlanceScript: Equatable {
    let language: String
    let rtl: Bool
    let latinScript: Bool
}

/// The timeline the widgets are handed: its [entries] in time order, the instant to reload at (nil:
/// never, until a new feed reloads it), and what the empty form needs: the feed's [words] (nil with
/// no feed, when the extension's own table speaks) read once for the whole timeline (plan 4 erratum
/// p4 #12), whether a newer format has been seen ([update], plan 3's call 5), and the feed's
/// [script] (nil with no feed).
struct GlanceTimeline: Equatable {
    let entries: [GlanceEntry]
    let reloadAt: Int64?
    let words: WatchScreenWords?
    let update: Bool
    let script: GlanceScript?
}

/// The glances and the timeline (the Lead's call 5, with plan 4 erratum p4 #10).
enum Glances {
    /// How long before a start the Smart Stack lifts Taqwa: half an hour (plan 4's question 2).
    static let relevantBefore: Int64 = 1_800
    /// How far the minute-by-minute entries reach: an hour, the iPhone widget's proven 60 a reload
    /// (at most 62 entries in it while every start is on a whole minute, `timeline`).
    static let horizon: Int64 = 3_600
    /// How many entries without the countdown follow the hour, so a reload that comes late still
    /// names the coming prayer (erratum p4 #10): one at each coming start, about two days, as plan
    /// 1's 10 periods; a turn of the date that changes the answer takes one of them (Ruling 64
    /// item 2), so a timeline never holds more than 72 entries.
    static let lateStarts = 10

    /// The glance at [now] (epoch seconds); nil when the feed has no honest answer.
    static func make(_ feed: WatchFeed, now: Int64) -> Glance? {
        guard let moment = WatchRules.moment(feed, now: now), feed.days.indices.contains(moment.next.dayIndex) else { return nil }
        let next = moment.next
        let day = feed.days[next.dayIndex]
        let time = WatchTime.of(next.prayer)
        let name = day.names.of(time)
        let countdown = WatchRules.countdown(feed, secondsLeft: moment.secondsLeft)
        let spokenClock = WatchRules.spokenClock(day, time)
        let spoken = WatchRules.spokenCountdown(feed, name: name, clock: spokenClock, secondsLeft: moment.secondsLeft)
            ?? name + " " + spokenClock + separator(feed.ui.rtl) + countdown
        return Glance(
            prayer: next.prayer,
            label: WatchRules.label(feed, next.prayer, day: next.dayIndex),
            name: name,
            time: day.clock.of(time),
            countdown: countdown,
            spoken: spoken,
            spokenWithoutCountdown: name + separator(feed.ui.rtl) + spokenClock,
            progress: moment.progress,
            previousAt: moment.previous.at,
            nextAt: next.at,
            rtl: feed.ui.rtl,
            latinScript: feed.ui.latinScript,
            language: feed.ui.language
        )
    }

    /// The comma every spoken join of a complication takes, as Today's lines are joined, and the one
    /// the Galaxy's complications put between their parts (`spokenSeparator`): Arabic for a
    /// right-to-left phone. The row's form, the screen's words with no spoken words, and the empty
    /// form's name and notice (`NextPrayerContent.spoken`) are joined with it (Ruling 75 item 1).
    static func separator(_ rtl: Bool) -> String { rtl ? "، " : ", " }

    /// The timeline from [now] (the Lead's call 5):
    /// - the first entry, at [now], is the glance that holds for now's minute: the glance at [now],
    ///   or, when [now] is a whole minute before its start (as a reload at a start always is), the
    ///   glance a second later (`make(now + 1)`), since the instant's own countdown holds for that
    ///   second alone (P4.6 review round 1);
    /// - then, inside `(now, now + horizon)`, an entry at each start and at every whole minute
    ///   before a start that falls after the start before it, each the glance that holds for its
    ///   minute (`make(at + 1)`), so an entry at a start already counts to the start after it;
    /// - then the entry at `now + horizon`, without the countdown: the label and the time alone;
    /// - then, past the hour, `lateStarts` entries without the countdown, each where the answer
    ///   next changes (erratum p4 #10): at the next start, or where the date turns over before it
    ///   if that changes what is said (Ruling 28, Ruling 64 item 2), as a far-north summer's
    ///   midnight does (a Maghrib after the next day's Fajr is dropped there) and the feed's last
    ///   midnight always does (nothing holds past it).
    ///
    /// Within the hour the minutes already step at every turn of the date, which falls on a whole
    /// minute (each day's midnight, in a zone whose offset is whole minutes). There are at most 62
    /// entries in the hour when every start is on a whole minute, as the phone's always are (the
    /// first, the 60 minutes after it, and the hour's end; 61 from a whole-minute [now]), and so at
    /// most 72 in all.
    ///
    /// A timeline that reaches the feed's end ends in the empty entry. `reloadAt` is the first
    /// start or the hour's end, whichever comes first. No feed, or no glance at [now], is one
    /// empty entry and no reload.
    static func timeline(_ feed: WatchFeed?, newerVersionSeen: Bool, now: Int64) -> GlanceTimeline {
        guard let feed else {
            return GlanceTimeline(entries: [empty(now)], reloadAt: nil, words: nil, update: newerVersionSeen, script: nil)
        }
        let words = feed.words // read once for the whole timeline (erratum p4 #12)
        let script = GlanceScript(language: feed.ui.language, rtl: feed.ui.rtl, latinScript: feed.ui.latinScript)
        func done(_ entries: [GlanceEntry], _ reloadAt: Int64?) -> GlanceTimeline {
            GlanceTimeline(entries: entries, reloadAt: reloadAt, words: words, update: newerVersionSeen, script: script)
        }
        guard let instant = make(feed, now: now) else { return done([empty(now)], nil) }
        // On a whole minute before its start (a reload at a start, or at 14:58:00 for 16:10), the
        // countdown of [now] holds for that instant alone and the next minute's from a second
        // later, so the first entry draws the minute that holds, as every other entry does.
        let onTheMinute = (instant.nextAt &- now) % 60 == 0
        guard let first = onTheMinute ? make(feed, now: now &+ 1) : instant else { return done([empty(now)], nil) }

        let end = now &+ horizon
        let reloadAt = min(first.nextAt, end)
        var entries = [entry(now, first, countdownShown: true)]
        // The minutes and starts of the hour, period by period: [after] is the instant the period
        // is looked at from (now, then each start), [current] the glance that holds there.
        var after = now
        var current = first
        while true {
            let start = current.nextAt
            var minute = start &- 60 * ((start &- after &- 1) / 60) // the first whole minute before it, after `after`
            while minute < start, minute < end {
                guard let glance = make(feed, now: minute &+ 1) else { return done(entries + [empty(minute)], reloadAt) }
                entries.append(entry(minute, glance, countdownShown: true))
                minute = minute &+ 60
            }
            if start >= end { break }
            guard let glance = make(feed, now: start &+ 1) else { return done(entries + [empty(start)], reloadAt) }
            entries.append(entry(start, glance, countdownShown: true))
            after = start
            current = glance
        }
        guard var last = make(feed, now: end) else { return done(entries + [empty(end)], reloadAt) }
        entries.append(entry(end, last, countdownShown: false))

        // Past the hour, without the countdown, an entry wherever the answer next changes: at each
        // coming start, and where the date turns over first and that changes it (erratum p4 #10,
        // Ruling 28, Ruling 64 item 2).
        var at = end
        for _ in 0..<lateStarts {
            let change = nextChange(feed, after: at, last)
            guard let glance = make(feed, now: change &+ 1) else { return done(entries + [empty(change)], reloadAt) }
            entries.append(entry(change, glance, countdownShown: false))
            at = change
            last = glance
        }
        return done(entries, reloadAt)
    }

    /// The windows in which the Smart Stack lifts Taqwa, for watchOS 11's `relevance()`: up to
    /// [count] of them, from [now], each `relevantBefore` up to a start, in the stretch where that
    /// start is the one counted to. They step as the late entries do (Ruling 64 item 2): so a
    /// start that a turn of the date drops before it comes has no window past the turn, and one
    /// the turn brings in has its window from the turn; a window begins no earlier than [now] or
    /// the start before; and none overlaps the next. So an instant is in a window exactly when an
    /// entry drawn for it is `relevant`.
    static func relevanceWindows(_ feed: WatchFeed, now: Int64, count: Int = 10) -> [ClosedRange<Int64>] {
        var windows: [ClosedRange<Int64>] = []
        var from = now
        var glance = make(feed, now: now)
        while let current = glance, windows.count < count {
            let until = nextChange(feed, after: from, current)
            let lower = max(current.nextAt &- relevantBefore, from)
            let upper = min(current.nextAt, until)
            if lower < upper { windows.append(lower...upper) }
            from = until
            glance = make(feed, now: until &+ 1)
        }
        return windows
    }

    /// Where what [glance] says next changes, looking on from [at]: its start, or before it, the
    /// first turn of the date (`WatchRules.today`) after which the answer is another (Ruling 28's
    /// split, `ComplicationTimelines.nextPrayer`'s on the Galaxy). A turn that leaves it as it was
    /// is no change.
    private static func nextChange(_ feed: WatchFeed, after at: Int64, _ glance: Glance) -> Int64 {
        var from = at
        while let turn = nextDayChange(feed, after: from), turn < glance.nextAt {
            guard let after = make(feed, now: turn), samePeriod(after, glance) else { return turn }
            from = turn
        }
        return glance.nextAt
    }

    /// The first instant after [after] at which the date, as the rules take it (`WatchRules.today`),
    /// is not the one [after] is on, or nil if it is still the same two days on, which no date is
    /// long enough for. It is found by halving the stretch between, as the Galaxy's
    /// `ComplicationTimelines.nextDayChange` finds it: exactly where the rules turn the date over (a
    /// date of 23 or 25 hours, the solar-midnight fallback and the feed's end included), with no
    /// second copy of that rule kept here.
    private static func nextDayChange(_ feed: WatchFeed, after: Int64) -> Int64? {
        let today = WatchRules.today(feed, now: after)
        var low = after
        var high = after &+ 2 * 86_400
        if WatchRules.today(feed, now: high) == today { return nil }
        while high &- low > 1 {
            let middle = low &+ (high &- low) / 2
            if WatchRules.today(feed, now: middle) == today { low = middle } else { high = middle }
        }
        return high
    }

    /// Whether two glances say the same of their period: everything but what moves with the
    /// instant (the countdown, the spoken sentence, the fill). Ruling 28's test of whether a turn
    /// of the date changes the answer, as the Galaxy's `answerChangesAt` is.
    private static func samePeriod(_ a: Glance, _ b: Glance) -> Bool {
        a.prayer == b.prayer && a.nextAt == b.nextAt && a.previousAt == b.previousAt && a.label == b.label
            && a.name == b.name && a.time == b.time && a.spokenWithoutCountdown == b.spokenWithoutCountdown
    }

    private static func entry(_ at: Int64, _ glance: Glance, countdownShown: Bool) -> GlanceEntry {
        let relevant = at >= glance.nextAt &- relevantBefore && at < glance.nextAt
        return GlanceEntry(at: at, glance: glance, relevant: relevant, countdownShown: countdownShown)
    }

    private static func empty(_ at: Int64) -> GlanceEntry {
        GlanceEntry(at: at, glance: nil, relevant: false, countdownShown: false)
    }
}

/// A line as VoiceOver is to speak it (plan 4 erratum p4 #5): the feed's words carry the feed's
/// language, so an English watch reads an Arabic feed with an Arabic voice. watchOS has no
/// `accessibilityLanguage`, and its AttributedString accessibility scope no speech-language key, so
/// the language is Foundation's `languageIdentifier` on the whole line, which SwiftUI hands to the
/// accessibility text. The watch's own words (`language` nil) carry none: they are in the watch's
/// language already.
enum Speech {
    static func line(_ text: String, language: String?) -> AttributedString {
        var line = AttributedString(text)
        if let language, !language.isEmpty { line.languageIdentifier = language }
        return line
    }
}
