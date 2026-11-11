import Foundation

/// The five prayers whose start the watch counts to, in the order of the day (the vectors'
/// spelling). Sunrise is not one: it only ends Fajr.
enum WatchPrayer: String, CaseIterable {
    case FAJR, DHUHR, ASR, MAGHRIB, ISHA
}

/// One start in the feed: [prayer] begins at [at], in epoch seconds. [dayIndex] is the place in
/// `WatchFeed.days` of the day that holds it, which is not always the date it falls on.
struct WatchStart: Equatable {
    let prayer: WatchPrayer
    let at: Int64
    let dayIndex: Int
}

/// A moment between two starts: the [previous], which has arrived, and the [next], which has not.
/// [secondsLeft] is the time to [next], and [progress] the ring's fill, from 0 to 1.
struct WatchMoment: Equatable {
    let previous: WatchStart
    let next: WatchStart
    let secondsLeft: Int64
    let progress: Float
}

/// The rules every watch surface applies to the feed (spec 1.4): `watchcore`'s `WatchRules.kt`,
/// line for line, its private helpers included, and held to it by the vectors
/// (`watch-rules-vectors.json`, `feed-vectors.json`). Read the Kotlin for why each rule is as it
/// is; the comments here say only where Swift has to be told something Kotlin does by itself:
///
/// - Kotlin's `Long` arithmetic wraps, and Swift's traps, so every sum and difference of two
///   instants is written `&+` and `&-`: the same answer as Kotlin's on every feed, and no crash on
///   a feed whose numbers are extreme.
/// - Kotlin's `minByOrNull` and `maxByOrNull` keep the first of equal elements, and Swift's
///   `max(by:)` keeps the last, so the picks are written out as loops.
/// - Where Kotlin would throw on an index no feed has (`dayStart`'s `require`, an empty phrase
///   list in `counted`), the port answers without one, as said at each.
enum WatchRules {

    /// Every start in the feed as one list in time order: each day's five, never its Sunrise, with
    /// a start left out where it falls at or after the next day's Fajr.
    static func starts(_ feed: WatchFeed) -> [WatchStart] {
        var starts: [WatchStart] = []
        starts.reserveCapacity(feed.days.count * WatchPrayer.allCases.count)
        for (index, day) in feed.days.enumerated() {
            let nextFajr = index + 1 < feed.days.count ? feed.days[index + 1].times.fajr : nil
            for prayer in WatchPrayer.allCases {
                let at = startOf(day.times, prayer)
                if nextFajr == nil || at < nextFajr! { starts.append(WatchStart(prayer: prayer, at: at, dayIndex: index)) }
            }
        }
        return starts
    }

    /// Where [now] (epoch seconds) sits in the feed, as `TimelineBuilder.buildForWidgets` has it
    /// for the phone; nil when the feed has no honest answer.
    static func moment(_ feed: WatchFeed, now: Int64) -> WatchMoment? {
        guard let today = todayIndex(feed, now) else { return nil }
        let starts = yesterdaysStarts(feed, today) + startsOfDay(feed, today)

        guard let next = firstEarliest(starts.filter { $0.at > now }) ?? firstOfTomorrowAfter(feed, today + 1, now) else {
            return nil
        }
        // The fallback never fires on engine data; it is `TimelineBuilder`'s own.
        let previous: WatchStart
        if let latest = latestAtOrBefore(starts, now) {
            previous = latest
        } else if today - 1 >= 0, today - 1 < feed.days.count {
            previous = WatchStart(prayer: .ISHA, at: feed.days[today - 1].times.isha, dayIndex: today - 1)
        } else {
            return nil
        }

        let total = next.at &- previous.at
        let elapsed = now &- previous.at
        let progress: Float = total <= 0 ? 0 : min(max(Float(elapsed) / Float(total), 0), 1)
        return WatchMoment(previous: previous, next: next, secondsLeft: next.at &- now, progress: progress)
    }

    /// The time left to a start in the feed's own words and digits: whole minutes, floored; under
    /// a minute, and a time run out, the phrase with no number in it.
    static func countdown(_ feed: WatchFeed, secondsLeft: Int64) -> String {
        let phrases = feed.ui.countdown
        if secondsLeft < 60 { return phrases.underAMinute }
        let totalMinutes = secondsLeft / 60
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if hours == 0 { return filled(phrases.minutes, digits(feed, minutes)) }
        if minutes == 0 { return filled(phrases.hours, digits(feed, hours)) }
        return filled(phrases.hoursMinutes, digits(feed, hours), digits(feed, minutes))
    }

    /// The countdown to [name], whose clock time is [clock], as a screen reader is told it, in the
    /// feed's spoken words. Nil when the feed has no spoken words, or a list of phrases is empty.
    static func spokenCountdown(_ feed: WatchFeed, name: String, clock: String, secondsLeft: Int64) -> String? {
        guard let spoken = feed.ui.spoken, !spoken.hours.isEmpty, !spoken.minutes.isEmpty else { return nil }
        if secondsLeft < 60 { return filled(spoken.countdownSoon, name, clock) }
        let totalMinutes = secondsLeft / 60
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        let duration: String
        if hours == 0 {
            duration = counted(feed, spoken.minutes, minutes)
        } else if minutes == 0 {
            duration = counted(feed, spoken.hours, hours)
        } else {
            duration = filled(spoken.hoursMinutes, counted(feed, spoken.hours, hours), counted(feed, spoken.minutes, minutes))
        }
        return filled(spoken.countdown, name, duration, clock)
    }

    /// [n] in the phrase [counts] gives it (the first whose `upTo` is [n] or more, and the last for
    /// any number beyond them all), in the feed's digits. Kotlin throws on an empty list, which
    /// [spokenCountdown] never passes; the port then gives the number alone.
    static func counted(_ feed: WatchFeed, _ counts: [WatchCount], _ n: Int64) -> String {
        guard let count = counts.first(where: { n <= Int64($0.upTo) }) ?? counts.last else { return digits(feed, n) }
        return filled(count.phrase, digits(feed, n))
    }

    /// [n] in the digits the phone writes in: the nine that follow the feed's `digitZero`.
    static func digits(_ feed: WatchFeed, _ n: Int64) -> String {
        inDigits(String(n), feed.ui.digitZero)
    }

    /// [text] with each Western digit, 0 to 9, written as the digit that many places after [zero];
    /// everything else as it is. [zero] is one UTF-16 unit, as the feed's are, and the digit is
    /// that unit plus the digit's value, as Kotlin's `Char` arithmetic gives it (in 16 bits). A
    /// unit that is no Unicode scalar, which only a zero within nine of a surrogate could give,
    /// is written U+FFFD, where Kotlin would write the lone surrogate no Swift string can hold.
    static func inDigits(_ text: String, _ zero: Character) -> String {
        if zero == "0" { return text }
        let zeroUnit = UInt32(zero.utf16.first ?? 0x30)
        var scalars = String.UnicodeScalarView()
        for scalar in text.unicodeScalars {
            if scalar.value >= 0x30, scalar.value <= 0x39 {
                let unit = (zeroUnit + (scalar.value - 0x30)) & 0xFFFF
                scalars.append(Unicode.Scalar(unit) ?? "\u{FFFD}")
            } else {
                scalars.append(scalar)
            }
        }
        return String(scalars)
    }

    /// [pattern] with its places written in: each `%n$s` becomes the nth of [parts], wherever it
    /// stands, and may come more than once. A part goes in as it is and is never filled in turn. A
    /// place with no part stays as it is. Filling is text replacement, never `String(format:)`.
    ///
    /// Kotlin's `Regex("%([0-9]+)\\$s")`, scanned left to right by Unicode scalar, as the regex
    /// scans UTF-16: every unit of a place is ASCII, so the two find the same places, and a
    /// combining mark after a place's `s` is no part of it. The number is read as Kotlin's
    /// `toIntOrNull`, so one beyond an `Int32` names no part.
    static func filled(_ pattern: String, _ parts: String...) -> String {
        filled(pattern, parts)
    }

    static func filled(_ pattern: String, _ parts: [String]) -> String {
        if parts.isEmpty { return pattern }
        let scalars = Array(pattern.unicodeScalars)
        var out = String.UnicodeScalarView()
        var i = 0
        while i < scalars.count {
            if scalars[i] == "%" {
                var j = i + 1
                while j < scalars.count, scalars[j].value >= 0x30, scalars[j].value <= 0x39 { j += 1 }
                if j > i + 1, j + 1 < scalars.count, scalars[j] == "$", scalars[j + 1] == "s" {
                    let number = String(String.UnicodeScalarView(scalars[(i + 1)..<j]))
                    if let n = Int32(number), n >= 1, Int(n) <= parts.count {
                        out.append(contentsOf: parts[Int(n) - 1].unicodeScalars)
                    } else {
                        out.append(contentsOf: scalars[i...(j + 1)])
                    }
                    i = j + 2
                    continue
                }
            }
            out.append(scalars[i])
            i += 1
        }
        return String(out)
    }

    /// The clock time of [time] on [day] as a surface draws it: the app's own 12-hour time with its
    /// day period (`clockTime`, "4:10 PM") from a 12-hour phone that sent it, and the day's `clock`
    /// otherwise ("16:10"). Sunrise is drawn this way as the five are. The string is the phone's,
    /// never worked out here: its day period is the one the phone's locale writes (Ruling 58 item 3).
    static func clockText(_ day: WatchDay, _ time: WatchTime) -> String {
        day.clockTime?.of(time) ?? day.clock.of(time)
    }

    /// The clock time of [time] on [day] spliced into a spoken sentence: `clockText`, between a
    /// first-strong isolate and its pop (U+2068, U+2069) when it holds anything but digits and a
    /// colon, so the sentence around it cannot reorder it ("PM 6:32"). A 24-hour time, in any digit
    /// set, comes back as it is. Every spoken line splices its times through here, never the bare
    /// `clock`.
    ///
    /// A digit is Kotlin's `Char.isDigit` on the JVM, a decimal digit (Unicode's Nd), tested unit by
    /// unit as Kotlin iterates a string's `Char`s: so Bengali, Devanagari and extended Arabic-Indic
    /// digits are digits, and a digit outside 16 bits (the mathematical bold 𝟏), two units neither
    /// of which is a digit, is isolated as Kotlin isolates it (the `clockForms` vectors).
    static func spokenClock(_ day: WatchDay, _ time: WatchTime) -> String {
        let text = clockText(day, time)
        return text.utf16.allSatisfy(isDigitOrColon) ? text : "\u{2068}" + text + "\u{2069}"
    }

    /// The row the Prayer screen lights at [now] (`TimelineBuilder.build`), which is the watch
    /// app's Today list. Nil when nothing is.
    static func litOnList(_ feed: WatchFeed, now: Int64) -> WatchPrayer? {
        guard let index = todayIndex(feed, now) else { return nil }
        guard let latest = firstLatest(startsOfDay(feed, index).filter { $0.at <= now }) else { return nil }

        // `TimelineBuilder.build`'s own guard, which never fires on engine data.
        let latestOfYesterday = yesterdaysStarts(feed, index).filter { $0.at <= now }.map(\.at).max()
        if let latestOfYesterday, latest.at < latestOfYesterday { return nil }

        return hasEndedForTheHighlight(latest.prayer, feed.days[index].times, now) ? nil : latest.prayer
    }

    /// The row the home-screen widgets light at [now] (`TimelineBuilder.buildForWidgets`), which
    /// is the Smart Stack's: the latest of today's prayers to have begun; of two on one instant
    /// the last is lit. Nil when nothing is.
    static func litOnGlance(_ feed: WatchFeed, now: Int64) -> WatchPrayer? {
        guard let index = todayIndex(feed, now) else { return nil }
        return startsOfDay(feed, index).last(where: { $0.at <= now })?.prayer
    }

    /// Whether the watch should ask the phone for a new feed (spec 2.2): the last start is less
    /// than three days away, or past; or the feed was written more than a week ago.
    static func needsRefresh(_ feed: WatchFeed, now: Int64) -> Bool {
        guard let last = starts(feed).last else { return true }
        return last.at &- now < refreshWithin || now &- feed.writtenAt > refreshAfter
    }

    /// The place in `WatchFeed.days` of the day that [now] is on, or nil: the day every rule here
    /// works on, turned over at the feed's own midnights, never the watch's clock.
    static func today(_ feed: WatchFeed, now: Int64) -> Int? {
        todayIndex(feed, now)
    }

    /// Where the day at [index] begins (epoch seconds), as [today] turns the date over. Kotlin
    /// `require`s a day of the feed; the port answers nil for an index the feed has no day at.
    static func dayStart(_ feed: WatchFeed, _ index: Int) -> Int64? {
        guard feed.days.indices.contains(index) else { return nil }
        return turnOver(feed.days, index)
    }

    /// A prayer's name on day [day] in capitals, upper-cased by the feed's language tag, exactly as
    /// `ComplicationFields.nextPrayer` writes it (Turkish capitalises i as İ). "" for a day the
    /// feed has not.
    static func label(_ feed: WatchFeed, _ prayer: WatchPrayer, day: Int) -> String {
        guard feed.days.indices.contains(day) else { return "" }
        return capitals(feed.days[day].names.of(WatchTime.of(prayer)), language: feed.ui.language)
    }

    /// [text] in capitals, upper-cased by the language tag [language] as Kotlin's
    /// `uppercase(Locale.forLanguageTag(language))` cases it on the JVM: `label`'s casing, which
    /// Today's ring label shares (plan 3's `today`).
    static func capitals(_ text: String, language: String) -> String {
        text.uppercased(with: casingLocale(language))
    }

    /// The locale a label is upper-cased in, as Kotlin's `uppercase(Locale.forLanguageTag(tag))`
    /// cases it on the JVM (Ruling 57 item 6). Java reads the tag's primary language subtag alone,
    /// in any case, so `tr-u-ca-gregory` and `TR` are Turkish and the ill-formed `tr_TR` is no
    /// language, and its `toUpperCase` cases only Turkish, Azerbaijani and Lithuanian otherwise
    /// than the default. Foundation's ICU, handed the tag itself, misses the first
    /// (`tr-u-ca-gregory` gives `IKINDI`) and reads `tr_TR` as Turkish, and handed any language it
    /// also cases Greek (its tonos dropped), Armenian and the three-letter `tur`, `aze` and `lit` its
    /// own way (measured on macOS 27 against JDK 21). So the subtag picks one of those three
    /// languages or none, and none is the default casing.
    private static func casingLocale(_ tag: String) -> Locale? {
        let primary = tag.prefix { $0 != "-" }
        guard primary.allSatisfy(\.isASCII) else { return nil }
        let language = primary.lowercased()
        return ["tr", "az", "lt"].contains(language) ? Locale(identifier: language) : nil
    }

    // MARK: - The private helpers

    /// One UTF-16 unit of a time that `spokenClock` leaves unisolated: a colon or a decimal digit.
    private static func isDigitOrColon(_ unit: UInt16) -> Bool {
        if unit == 0x3A { return true }
        guard let scalar = Unicode.Scalar(unit) else { return false }
        return scalar.properties.generalCategory == .decimalNumber
    }

    /// See `todayIndex` in `WatchRules.kt`: day i holds [turnOver(i), turnOver(i + 1)).
    private static func todayIndex(_ feed: WatchFeed, _ now: Int64) -> Int? {
        let days = feed.days
        if days.isEmpty { return nil }
        guard let index = days.indices.last(where: { turnOver(days, $0) <= now }) else { return nil }
        return now < turnOver(days, index + 1) ? index : nil
    }

    /// Where day [i] begins, and for [i] one past the last day where the last one ends.
    private static func turnOver(_ days: [WatchDay], _ i: Int) -> Int64 {
        let midnight = days.indices.contains(i) ? days[i].midnight : 0
        if midnight != 0 { return midnight }
        if i == 0 { return days[0].times.dhuhr &- halfADay }
        if i == days.count {
            let last = days[days.count - 1]
            return last.midnight != 0 ? last.midnight &+ aDay : last.times.dhuhr &+ halfADay
        }
        return (days[i - 1].times.dhuhr &+ days[i].times.dhuhr) / 2
    }

    /// Day [index]'s five starts in the order of the day, at whatever hour each falls.
    private static func startsOfDay(_ feed: WatchFeed, _ index: Int) -> [WatchStart] {
        let times = feed.days[index].times
        return WatchPrayer.allCases.map { WatchStart(prayer: $0, at: startOf(times, $0), dayIndex: index) }
    }

    /// `TimelineBuilder.yesterdaysStarts`: the starts of the day before [today] that come before
    /// its Fajr.
    private static func yesterdaysStarts(_ feed: WatchFeed, _ today: Int) -> [WatchStart] {
        if today == 0 { return [] }
        let fajr = feed.days[today].times.fajr
        return startsOfDay(feed, today - 1).filter { $0.at < fajr }
    }

    /// `TimelineBuilder.firstOfTomorrowAfter`: the first of day [tomorrow]'s starts still after [now].
    private static func firstOfTomorrowAfter(_ feed: WatchFeed, _ tomorrow: Int, _ now: Int64) -> WatchStart? {
        if tomorrow > feed.days.count - 1 { return nil }
        return firstEarliest(startsOfDay(feed, tomorrow).filter { $0.at > now })
    }

    /// The latest of [starts] at or before [now]; of two on one instant, the later in the list.
    private static func latestAtOrBefore(_ starts: [WatchStart], _ now: Int64) -> WatchStart? {
        var latest: WatchStart?
        for start in starts where start.at <= now && (latest == nil || start.at >= latest!.at) {
            latest = start
        }
        return latest
    }

    /// Kotlin's `minByOrNull { it.at }`: the earliest, the first of equals.
    private static func firstEarliest(_ starts: [WatchStart]) -> WatchStart? {
        var earliest: WatchStart?
        for start in starts where earliest == nil || start.at < earliest!.at { earliest = start }
        return earliest
    }

    /// Kotlin's `maxByOrNull { it.at }`: the latest, the first of equals.
    private static func firstLatest(_ starts: [WatchStart]) -> WatchStart? {
        var latest: WatchStart?
        for start in starts where latest == nil || start.at > latest!.at { latest = start }
        return latest
    }

    /// `TimelineBuilder.hasEndedForTheHighlight`: Fajr from sunrise, Asr from sunset, none else.
    private static func hasEndedForTheHighlight(_ prayer: WatchPrayer, _ day: WatchTimes, _ now: Int64) -> Bool {
        switch prayer {
        case .FAJR: return day.fajrEnd.map { now >= $0 } ?? false
        case .ASR: return day.asrEnd.map { now >= $0 } ?? false
        default: return false
        }
    }

    private static func startOf(_ times: WatchTimes, _ prayer: WatchPrayer) -> Int64 {
        switch prayer {
        case .FAJR: return times.fajr
        case .DHUHR: return times.dhuhr
        case .ASR: return times.asr
        case .MAGHRIB: return times.maghrib
        case .ISHA: return times.isha
        }
    }

    private static let aDay: Int64 = 86_400
    private static let halfADay: Int64 = aDay / 2
    private static let refreshWithin: Int64 = 3 * aDay
    private static let refreshAfter: Int64 = 7 * aDay
}
