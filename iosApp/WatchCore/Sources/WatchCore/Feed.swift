import Foundation

// The feed: the one document the phone builds and sends to the watch, and everything the watch
// draws (spec 1.1). This is `watchcore`'s `WatchFeed.kt` in Swift, type for type and key for key:
// the property names are the JSON keys, and the keys are the contract, so a key is never renamed
// or repurposed. The Kotlin types are kept exactly (Ruling 56 item 4): a Kotlin `Int` is an
// `Int32` and a `Long` an `Int64` (watchOS's own `Int` can be 32 bits, and a Mac's is 64), and a
// `Char` is one UTF-16 unit.
//
// Each type reads its JSON as kotlinx reads it (`WatchFeedJson`, with `ignoreUnknownKeys`):
// - a key the type lacks is ignored, as a later phone's;
// - a key with no default is required, and a `null` there is refused;
// - a key with a default may be absent, as an older phone leaves it, and takes the default; a
//   `null` there is refused, as kotlinx refuses it ([readDefaulted]);
// - a key whose Kotlin type is nullable, `T? = null`, may be absent or `null`;
// - a Kotlin `Int` or `Long` is exactly the text's number or the text is refused ([decodeWhole]):
//   never a fraction rounded to a whole number on its way in (Ruling 56 item 2), as far as a
//   `Decimal` keeps a number's digits, about 38 significant ones (Ruling 57 item 1);
// - a value of the wrong type is refused, even where kotlinx reads it leniently (a quoted number
//   or boolean, a bare digit for a `Char`): the safe differences of the Lead's call 8 and Ruling
//   57 item 2, which keep the last feed.
// Plan 3's fields joined in plan 4 Task 3, read the same way: `WatchUi.screens` (the words, in
// `WatchScreenWords.swift`) and `latinScript` with their defaults, and `ringDigitZero` and
// `WatchDay.clockTime` nil when absent or `null`. Each is read as the phone sends it: no clock time,
// day period or digit is worked out here (Ruling 58 item 3: a 12-hour phone's `clockTime` carries
// the day period its own locale writes, and `ui.am` and `ui.pm` stay the words locale's).
// `WatchUi.showSunrise`, the phone's Show sunrise (the owner's decision of 9 November 2026), is read
// the same way, true when absent, as a 1.6.0 (43) phone leaves it, and `WatchCountdownPhrases`'s
// three compact forms (Mohamed's report of the same day) empty when absent, as a 1.6.0 (44) phone
// leaves them, and as every later phone leaves an empty one (Kotlin writes them only when they
// hold a form, `EncodeDefault.Mode.NEVER`): the Galaxy Tile's words, which the Apple Watch reads
// for nothing else.

/// The feed. See `WatchFeed.kt`.
struct WatchFeed: Decodable, Equatable {
    /// The format this feed is in: `FeedDecoder.version` when the phone wrote it.
    let version: Int32
    /// When the phone wrote it, in epoch seconds.
    let writtenAt: Int64
    /// One entry for each date from the phone's yesterday, in date order: 31 in all, or fewer.
    let days: [WatchDay]
    let place: WatchPlace
    let ui: WatchUi
    /// The user's own tasbeeh phrases. The built-in seven are in the watch app itself.
    let tasbeeh: [WatchPhrase]

    init(version: Int32, writtenAt: Int64, days: [WatchDay], place: WatchPlace, ui: WatchUi, tasbeeh: [WatchPhrase] = []) {
        self.version = version
        self.writtenAt = writtenAt
        self.days = days
        self.place = place
        self.ui = ui
        self.tasbeeh = tasbeeh
    }

    private enum CodingKeys: String, CodingKey { case version, writtenAt, days, place, ui, tasbeeh }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        version = try json.decodeWhole(Int32.self, forKey: .version)
        writtenAt = try json.decodeWhole(Int64.self, forKey: .writtenAt)
        days = try json.decode([WatchDay].self, forKey: .days)
        place = try json.decode(WatchPlace.self, forKey: .place)
        ui = try json.decode(WatchUi.self, forKey: .ui)
        tasbeeh = try json.readDefaulted([WatchPhrase].self, .tasbeeh, []) // Kotlin: = emptyList()
    }
}

/// One day of the feed: its six instants, and the strings written for them.
struct WatchDay: Decodable, Equatable {
    /// The date, ISO 8601: `2026-10-18`.
    let date: String
    let times: WatchTimes
    /// Each time as the phone writes a clock time, in its digits: `16:10`, or the bare `4:10`.
    let clock: WatchSix<String>
    /// The prayer names for this day in the phone's language; Dhuhr reads Jumuʿah on a Friday.
    let names: WatchSix<String>
    /// The Hijri date as the Prayer screen writes it.
    let hijri: String
    /// The Gregorian date line in the face's form: `SUN 18 OCT` in Latin.
    let gregorian: String
    let weekday: String
    let dayNumber: String
    /// When this date begins at the place, in epoch seconds; 0 when the phone could not say, and
    /// the rules then turn the date over at solar midnight.
    let midnight: Int64
    /// The Hijri day and month with no year; empty in a feed written before the field.
    let hijriDayMonth: String
    /// The Gregorian date as a screen reader is told it; empty in a feed written before the field.
    let gregorianSpoken: String
    /// On a 12-hour phone, each of the six times as the app writes it, day period and all (`4:10 PM`,
    /// with a no-break space; the marker first in Turkish, `ÖS 4:10`), bare, with no isolate. Nil
    /// from a 24-hour phone and in a feed written before the field: the watch then draws `clock`
    /// (Kotlin: `WatchSix<String>? = null`). Read through `WatchRules.clockText` and `spokenClock`.
    let clockTime: WatchSix<String>?

    init(
        date: String, times: WatchTimes, clock: WatchSix<String>, names: WatchSix<String>, hijri: String,
        gregorian: String, weekday: String, dayNumber: String, midnight: Int64 = 0, hijriDayMonth: String = "",
        gregorianSpoken: String = "", clockTime: WatchSix<String>? = nil
    ) {
        self.date = date
        self.times = times
        self.clock = clock
        self.names = names
        self.hijri = hijri
        self.gregorian = gregorian
        self.weekday = weekday
        self.dayNumber = dayNumber
        self.midnight = midnight
        self.hijriDayMonth = hijriDayMonth
        self.gregorianSpoken = gregorianSpoken
        self.clockTime = clockTime
    }

    private enum CodingKeys: String, CodingKey {
        case date, times, clock, names, hijri, gregorian, weekday, dayNumber, midnight, hijriDayMonth, gregorianSpoken, clockTime
    }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        date = try json.decode(String.self, forKey: .date)
        times = try json.decode(WatchTimes.self, forKey: .times)
        clock = try json.decode(WatchSix<String>.self, forKey: .clock)
        names = try json.decode(WatchSix<String>.self, forKey: .names)
        hijri = try json.decode(String.self, forKey: .hijri)
        gregorian = try json.decode(String.self, forKey: .gregorian)
        weekday = try json.decode(String.self, forKey: .weekday)
        dayNumber = try json.decode(String.self, forKey: .dayNumber)
        midnight = try json.readDefaultedWhole(Int64.self, .midnight, 0) // Kotlin: = 0L
        hijriDayMonth = try json.readDefaulted(String.self, .hijriDayMonth, "") // Kotlin: = ""
        gregorianSpoken = try json.readDefaulted(String.self, .gregorianSpoken, "") // Kotlin: = ""
        clockTime = try json.decodeIfPresent(WatchSix<String>.self, forKey: .clockTime) // Kotlin: WatchSix<String>? = null
    }
}

/// A day's six instants in epoch seconds, and the two ends the Prayer screen's rule needs.
struct WatchTimes: Decodable, Equatable {
    let fajr: Int64
    let sunrise: Int64
    let dhuhr: Int64
    let asr: Int64
    let maghrib: Int64
    let isha: Int64
    /// When Fajr ends: sunrise. Nil on a day with none, such as a polar day (Kotlin: `Long? = null`).
    let fajrEnd: Int64?
    /// When Asr ends: the astronomical sunset. Nil on a day with none (Kotlin: `Long? = null`).
    let asrEnd: Int64?

    init(fajr: Int64, sunrise: Int64, dhuhr: Int64, asr: Int64, maghrib: Int64, isha: Int64, fajrEnd: Int64? = nil, asrEnd: Int64? = nil) {
        self.fajr = fajr
        self.sunrise = sunrise
        self.dhuhr = dhuhr
        self.asr = asr
        self.maghrib = maghrib
        self.isha = isha
        self.fajrEnd = fajrEnd
        self.asrEnd = asrEnd
    }

    private enum CodingKeys: String, CodingKey { case fajr, sunrise, dhuhr, asr, maghrib, isha, fajrEnd, asrEnd }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        fajr = try json.decodeWhole(Int64.self, forKey: .fajr)
        sunrise = try json.decodeWhole(Int64.self, forKey: .sunrise)
        dhuhr = try json.decodeWhole(Int64.self, forKey: .dhuhr)
        asr = try json.decodeWhole(Int64.self, forKey: .asr)
        maghrib = try json.decodeWhole(Int64.self, forKey: .maghrib)
        isha = try json.decodeWhole(Int64.self, forKey: .isha)
        fajrEnd = try json.decodeWholeIfPresent(Int64.self, forKey: .fajrEnd) // Kotlin: Long? = null
        asrEnd = try json.decodeWholeIfPresent(Int64.self, forKey: .asrEnd) // Kotlin: Long? = null
    }
}

/// One value for each of a day's six times, in the order of the day. The clock strings and the
/// names share this shape, and `of` reads it by `WatchTime`.
struct WatchSix<T: Decodable & Equatable>: Decodable, Equatable {
    let fajr: T
    let sunrise: T
    let dhuhr: T
    let asr: T
    let maghrib: T
    let isha: T

    /// The value for [time].
    func of(_ time: WatchTime) -> T {
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

/// The six times of a day, in the order of the day: the five prayers and Sunrise, which is no
/// prayer (`WatchPrayer` has none) but which Today's rows draw and say while the phone shows it
/// (`WatchUi.showSunrise`). The key every surface reads a day's `WatchSix` by (`WatchFeed.kt`'s
/// `WatchTime`, the vectors' spelling).
enum WatchTime: String, CaseIterable {
    case FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA

    /// The time a prayer begins at: its namesake.
    static func of(_ prayer: WatchPrayer) -> WatchTime {
        switch prayer {
        case .FAJR: return .FAJR
        case .DHUHR: return .DHUHR
        case .ASR: return .ASR
        case .MAGHRIB: return .MAGHRIB
        case .ISHA: return .ISHA
        }
    }
}

/// Where the feed was made, as Ruling 39 allows the watch to be told it: never a coordinate.
struct WatchPlace: Decodable, Equatable {
    /// The city's name as the Prayer screen shows it, or "".
    let city: String
    /// The Qibla's bearing in degrees clockwise from true north, to the phone's whole degree.
    let qiblaBearing: Double
    /// The distance to the Kaaba in kilometres, already written in the feed's digits.
    let qiblaKm: String
    /// The magnetic declination, in degrees east of true north, to a whole degree.
    let declination: Double
    /// Whether the place is south of the equator.
    let southern: Bool
}

/// How the phone writes things, so that the watch writes them the same way.
struct WatchUi: Decodable, Equatable {
    /// The words locale's tag. The watch takes letter case from it and nothing else.
    let language: String
    let rtl: Bool
    /// The zero of the digit set the phone writes its numbers in: on the wire a string of exactly
    /// one UTF-16 unit, as kotlinx reads a `Char` (so `"0\u{301}"`, an astral digit and `"\r\n"`
    /// are refused, though each is one Swift `Character`).
    let digitZero: Character
    let twelveHour: Bool
    let am: String
    let pm: String
    let countdown: WatchCountdownPhrases
    /// The "next prayer in" label, with `%1$s` where the prayer's name goes.
    let nextIn: String
    /// What a screen reader is told of the countdown. Nil in a feed written before the field
    /// (Kotlin: `WatchSpoken? = null`).
    let spoken: WatchSpoken?
    /// The words of the watch's screens in the phone's language. Empty in a feed written before
    /// them, which only a development feed is: read them through `WatchFeed.words`, which gives the
    /// English for any word left blank (Kotlin: `= WatchScreenWords()`).
    let screens: WatchScreenWords
    /// The zero of the digits the phone's countdown ring writes its H:MM:SS in, where they are not
    /// `digitZero`'s: "0" on an ar-SA, ar-EG or Bengali phone. Nil means `digitZero`'s. On the wire
    /// one UTF-16 unit, as `digitZero` is, or `null` (Kotlin: `Char? = null`).
    let ringDigitZero: Character?
    /// Whether the app's interface is written in Latin letters: only then does the watch show a
    /// dhikr's transliteration and write names in capitals. True in a feed written before the field
    /// (Kotlin: `= true`).
    let latinScript: Bool
    /// Whether the phone shows Sunrise, its Show sunrise setting: Today lists Sunrise only while it
    /// is true, as the phone's Prayer screen does. Nothing else follows it. True in a feed written
    /// before the field, whose watches always showed Sunrise (Kotlin: `= true`).
    let showSunrise: Bool

    init(
        language: String, rtl: Bool, digitZero: Character = "0", twelveHour: Bool, am: String, pm: String,
        countdown: WatchCountdownPhrases, nextIn: String, spoken: WatchSpoken? = nil,
        screens: WatchScreenWords = WatchScreenWords(), ringDigitZero: Character? = nil, latinScript: Bool = true,
        showSunrise: Bool = true
    ) {
        self.language = language
        self.rtl = rtl
        self.digitZero = digitZero
        self.twelveHour = twelveHour
        self.am = am
        self.pm = pm
        self.countdown = countdown
        self.nextIn = nextIn
        self.spoken = spoken
        self.screens = screens
        self.ringDigitZero = ringDigitZero
        self.latinScript = latinScript
        self.showSunrise = showSunrise
    }

    private enum CodingKeys: String, CodingKey {
        case language, rtl, digitZero, twelveHour, am, pm, countdown, nextIn, spoken, screens, ringDigitZero, latinScript, showSunrise
    }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        language = try json.decode(String.self, forKey: .language)
        rtl = try json.decode(Bool.self, forKey: .rtl)
        digitZero = try json.readDefaulted(UTF16Unit.self, .digitZero, UTF16Unit("0")).character // Kotlin: = '0'
        twelveHour = try json.decode(Bool.self, forKey: .twelveHour)
        am = try json.decode(String.self, forKey: .am)
        pm = try json.decode(String.self, forKey: .pm)
        countdown = try json.decode(WatchCountdownPhrases.self, forKey: .countdown)
        nextIn = try json.decode(String.self, forKey: .nextIn)
        spoken = try json.decodeIfPresent(WatchSpoken.self, forKey: .spoken)
        screens = try json.readDefaulted(WatchScreenWords.self, .screens, WatchScreenWords()) // Kotlin: = WatchScreenWords()
        ringDigitZero = try json.decodeIfPresent(UTF16Unit.self, forKey: .ringDigitZero)?.character // Kotlin: Char? = null
        latinScript = try json.readDefaulted(Bool.self, .latinScript, true) // Kotlin: = true
        showSunrise = try json.readDefaulted(Bool.self, .showSunrise, true) // Kotlin: = true
    }
}

/// The countdown as the phone's screen reader says it: the phone's own phrases, filled in order.
struct WatchSpoken: Decodable, Equatable {
    /// `%1$s in %2$s, at %3$s`: the prayer, the time left and its clock time.
    let countdown: String
    /// `%1$s in under a minute, at %2$s`.
    let countdownSoon: String
    /// `%1$s %2$s`: the hours and the minutes joined.
    let hoursMinutes: String
    /// `%1$s hours`, in the phrase each number of hours takes.
    let hours: [WatchCount]
    /// `%1$s minutes`, the same way.
    let minutes: [WatchCount]
}

/// The phrase for every number up to [upTo] that no earlier entry of its list takes.
struct WatchCount: Decodable, Equatable {
    let upTo: Int32
    /// The phrase, with `%1$s` where the number goes.
    let phrase: String
}

extension WatchCount {
    private enum CodingKeys: String, CodingKey { case upTo, phrase }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        upTo = try json.decodeWhole(Int32.self, forKey: .upTo)
        phrase = try json.decode(String.self, forKey: .phrase)
    }
}

/// The countdown's four forms, each worded in the phone's language, and the three compact forms
/// beside them, the Galaxy watch's Tile's alone: the Apple Watch writes the four, and reads the
/// compact ones only so that a feed is refused where Kotlin refuses it.
struct WatchCountdownPhrases: Decodable, Equatable {
    /// `in %1$s h %2$s min`.
    let hoursMinutes: String
    /// `in %1$s h`.
    let hours: String
    /// `in %1$s min`.
    let minutes: String
    /// `in under a minute`.
    let underAMinute: String
    /// `in %1$sh %2$sm`. Empty in a feed written before it, and where the phone left it out as
    /// empty (Kotlin: `= ""`, not written when empty).
    var compactHoursMinutes: String = ""
    /// `in %1$sh`. Empty when absent, as `compactHoursMinutes` (Kotlin: `= ""`).
    var compactHours: String = ""
    /// `in %1$sm`. Empty when absent, as `compactHoursMinutes` (Kotlin: `= ""`).
    var compactMinutes: String = ""
}

extension WatchCountdownPhrases {
    private enum CodingKeys: String, CodingKey {
        case hoursMinutes, hours, minutes, underAMinute, compactHoursMinutes, compactHours, compactMinutes
    }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        hoursMinutes = try json.decode(String.self, forKey: .hoursMinutes)
        hours = try json.decode(String.self, forKey: .hours)
        minutes = try json.decode(String.self, forKey: .minutes)
        underAMinute = try json.decode(String.self, forKey: .underAMinute)
        compactHoursMinutes = try json.readDefaulted(String.self, .compactHoursMinutes, "") // Kotlin: = ""
        compactHours = try json.readDefaulted(String.self, .compactHours, "") // Kotlin: = ""
        compactMinutes = try json.readDefaulted(String.self, .compactMinutes, "") // Kotlin: = ""
    }
}

/// One of the user's own tasbeeh phrases: its id, the phrase, and how many to count.
struct WatchPhrase: Decodable, Equatable {
    let id: String
    let phrase: String
    let target: Int32
}

extension WatchPhrase {
    private enum CodingKeys: String, CodingKey { case id, phrase, target }

    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        id = try json.decode(String.self, forKey: .id)
        phrase = try json.decode(String.self, forKey: .phrase)
        target = try json.decodeWhole(Int32.self, forKey: .target)
    }
}

/// A Kotlin `Char` on the wire: a JSON string of exactly one UTF-16 unit.
struct UTF16Unit: Decodable, Equatable {
    let character: Character

    init(_ character: Character) { self.character = character }

    init(from decoder: Decoder) throws {
        let text = try decoder.singleValueContainer().decode(String.self)
        guard text.utf16.count == 1, let character = text.first else {
            throw DecodingError.dataCorrupted(
                DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "a Char is one UTF-16 unit, not \(text.utf16.count)")
            )
        }
        self.character = character
    }
}

extension KeyedDecodingContainer {
    /// A key with a Kotlin default that is not null: the default when the key is absent, and the
    /// value otherwise, so that a `null` there throws as kotlinx refuses it. (`decodeIfPresent`
    /// would read a `null` as absent.)
    func readDefaulted<T: Decodable>(_ type: T.Type, _ key: Key, _ fallback: T) throws -> T {
        contains(key) ? try decode(type, forKey: key) : fallback
    }

    /// A Kotlin `Int` or `Long`, read as exactly the text's number or not at all, so that a feed
    /// holds the text's own value (Ruling 56 item 2, as Ruling 57 item 1 bounds it). `JSONDecoder`'s
    /// own read of the type keeps its range and refuses a quoted number or a boolean. A number
    /// written with a point or an exponent it reads through the nearest `Double` (measured on
    /// Swift 6.4): that `Double` must be a whole number within the type's range, or the number is
    /// refused (`1.5`, and `9223372036854775807.0`, whose nearest `Double` is 2^63). Below 2^53 in
    /// magnitude that `Double` is the answer, so on its own the read rounds away a fraction no
    /// `Double` holds: `1792303200.00000000000000000001` reads as 1792303200,
    /// `0.99999999999999999999` as 1 and `1e-400` as 0. From 2^53 up, where a `Double` no longer
    /// holds every integer, it reads the number's digits again as a `Decimal` and keeps their whole
    /// part, so `9007199254740993.0` reads exactly and `9007199254740993.5` as 9007199254740993.
    /// So the same number is read again here as a `Decimal`, which keeps those digits, and one that
    /// is not that whole number is refused, as kotlinx refuses a fraction there. A whole number in
    /// any spelling (`1792303200.0`, `1792303200e0`) is still read.
    ///
    /// The limit is the `Decimal`'s: it keeps about 38 significant digits (39 measured) and drops
    /// any after them, so past that a number can read as its rounded whole value (`1792303200.`,
    /// 29 zeros and a 1 reads as 1792303200). Only a `FOREIGN` text can hold such a number: the
    /// phone writes every integer as plain digits (the `ENCODER` and `ONE_CHANGE` texts), which
    /// this reads exactly.
    func decodeWhole<T: FixedWidthInteger & SignedInteger & Decodable>(_ type: T.Type, forKey key: Key) throws -> T {
        let value = try decode(type, forKey: key)
        guard try decode(Decimal.self, forKey: key) == Decimal(Int64(value)) else {
            throw DecodingError.dataCorruptedError(forKey: key, in: self, debugDescription: "not the whole number \(value), which an integer read rounds it to")
        }
        return value
    }

    /// A Kotlin `Long? = null`: nil when the key is absent or `null`, otherwise [decodeWhole].
    func decodeWholeIfPresent<T: FixedWidthInteger & SignedInteger & Decodable>(_ type: T.Type, forKey key: Key) throws -> T? {
        guard contains(key), !(try decodeNil(forKey: key)) else { return nil }
        return try decodeWhole(type, forKey: key)
    }

    /// A Kotlin `Long` with a default that is not null: [readDefaulted], read as [decodeWhole].
    func readDefaultedWhole<T: FixedWidthInteger & SignedInteger & Decodable>(_ type: T.Type, _ key: Key, _ fallback: T) throws -> T {
        contains(key) ? try decodeWhole(type, forKey: key) : fallback
    }
}
