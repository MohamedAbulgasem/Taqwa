import Foundation
import XCTest
@testable import WatchCore

/// The vectors Kotlin writes and holds to its own answers, read where the repository keeps them,
/// `watchcore/vectors/`, which is found from this file's own path: the port is tested against the
/// very files `VectorsTest.kt` and `SwiftPortVectorsTest.kt` commit, never a copy. A rule changed
/// in Kotlin rewrites them, and these tests then fail until the port follows.
///
/// The files are read with `JSONDecoder` into the shapes below, never with `JSONSerialization`,
/// which drops a U+FEFF that opens a string value.
enum Vectors {
    /// `watchcore/vectors/`: up from `Tests/WatchCoreTests/` through `iosApp/WatchCore/`.
    static let directory: URL = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // WatchCoreTests
        .deletingLastPathComponent() // Tests
        .deletingLastPathComponent() // WatchCore
        .deletingLastPathComponent() // iosApp
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("watchcore/vectors", isDirectory: true)

    /// A file of the directory, by its path inside it, byte for byte.
    static func data(_ path: String) throws -> Data {
        try Data(contentsOf: directory.appendingPathComponent(path))
    }

    /// The files of a subdirectory whose names end in `suffix`, in name order.
    static func files(in subdirectory: String, endingIn suffix: String) throws -> [String] {
        try FileManager.default.contentsOfDirectory(atPath: directory.appendingPathComponent(subdirectory).path)
            .filter { $0.hasSuffix(suffix) }
            .sorted()
    }

    static let rules: RulesVectors = load("watch-rules-vectors.json")
    static let feeds: FeedVectors = load("feed-vectors.json")

    private static func load<T: Decodable>(_ name: String) -> T {
        do {
            return try JSONDecoder().decode(T.self, from: data(name))
        } catch {
            fatalError("\(directory.appendingPathComponent(name).path) does not read: \(error)")
        }
    }

    /// A decode case's text, as the watch would be handed it: its `json`, or the whole of the file
    /// it names, after a UTF-8 byte-order mark when it says `bom`.
    static func text(of decodeCase: DecodeCase) throws -> Data {
        let body: Data
        if let json = decodeCase.json {
            body = Data(json.utf8)
        } else if let file = decodeCase.file {
            body = try data(file)
        } else {
            throw VectorError("\(decodeCase.name) holds no text")
        }
        return decodeCase.bom == true ? Data([0xEF, 0xBB, 0xBF]) + body : body
    }

    /// The decode case of that name.
    static func decodeCase(_ name: String) throws -> DecodeCase {
        guard let found = feeds.decode.first(where: { $0.name == name }) else { throw VectorError("no decode case \(name)") }
        return found
    }

    /// The feed the decode case of that name decodes to: the feed every other section's case names.
    static func feed(_ name: String) throws -> WatchFeed {
        let decodeCase = try decodeCase(name)
        guard case let .feed(feed) = FeedDecoder.decode(try text(of: decodeCase)) else {
            throw VectorError("the decode case \(name) is no feed")
        }
        return feed
    }

    /// The cases of a section, failing the test when there are none, so that an empty or renamed
    /// section can never pass by testing nothing.
    static func nonEmpty<T>(_ cases: [T], _ section: String, file: StaticString = #filePath, line: UInt = #line) -> [T] {
        XCTAssertFalse(cases.isEmpty, "the \(section) section has no cases", file: file, line: line)
        print("\(section): \(cases.count) cases")
        return cases
    }
}

struct VectorError: Error, CustomStringConvertible {
    let description: String
    init(_ description: String) { self.description = description }
}

// MARK: - watch-rules-vectors.json

struct RulesVectors: Decodable {
    struct Tolerance: Decodable { let progress: Double }

    let tolerance: Tolerance
    /// The feeds the cases run on, by name, each read by the port's own model.
    let feeds: [String: WatchFeed]
    let cases: [RuleCase]
    let countdowns: [CountdownCase]
    let refreshes: [RefreshCase]
}

struct StartVector: Decodable, Equatable {
    let prayer: String
    let at: Int64
    let dayIndex: Int
}

struct RuleCase: Decodable {
    let name: String
    let feed: String
    let now: Int64
    let next: StartVector?
    let previous: StartVector?
    let secondsLeft: Int64?
    let progress: Double?
    let countdown: String?
    let litOnList: String?
    let litOnGlance: String?
}

struct CountdownCase: Decodable {
    let feed: String
    let secondsLeft: Int64
    let countdown: String
}

struct RefreshCase: Decodable {
    let name: String
    let feed: String
    let now: Int64
    let needsRefresh: Bool
}

// MARK: - feed-vectors.json

struct FeedVectors: Decodable {
    let decode: [DecodeCase]
    let spoken: [SpokenCase]
    let labels: [LabelCase]
    let days: [DayCase]
    let clock: [ClockCase]
    let words: [WordsCase]
    let moments: [MomentCase]
    let starts: [StartsCase]
    let filled: [FilledCase]
    let inDigits: [InDigitsCase]
    let contract: ContractVectors
}

/// `WatchLinkContract` as `SwiftPortVectorsTest.kt` writes it (plan 4 Task 1, Ruling 50): the
/// context and message keys, the size figures, the fingerprint's form, and the receivers' three
/// rules and their store order, each case the contract's own answer.
struct ContractVectors: Decodable {
    struct Keys: Decodable {
        let CONTEXT_FEED_KEY: String
        let CONTEXT_DELIVERY_KEY: String
        let REFRESH_MESSAGE_KEY: String
        let REFRESH_REPLY_KEY: String
    }

    struct Fingerprint: Decodable {
        let text: String
        let valid: Bool
    }

    /// `deliveryOf(text)`: the three parts when it reads a delivery, none of them when it does not.
    struct DeliveryCase: Decodable {
        let name: String
        let text: String
        let origin: String?
        let seq: Int64?
        let fingerprint: String?
    }

    /// `stores(held, arriving)`, each delivery as its text; no `held` for a watch that holds none.
    struct StoresCase: Decodable {
        let name: String
        let held: String?
        let arriving: String
        let stores: Bool
    }

    /// `echoOf(held)`; no `held` for a watch that holds none.
    struct EchoCase: Decodable {
        let held: String?
        let echo: String
    }

    let keys: Keys
    let fingerprints: [Fingerprint]
    let deliveries: [DeliveryCase]
    let stores: [StoresCase]
    let echoes: [EchoCase]
    let storeSteps: [String]
}

/// The three classes of Ruling 56, which say what binds the port on a case.
enum CaseClass: String, Decodable, CaseIterable {
    /// kotlinx's encoder's own text for a model the plans name: the port answers exactly.
    case ENCODER
    /// An encoder's text with one value changed, removed or set to null: the port answers exactly.
    case ONE_CHANGE
    /// A text no encoder configuration the app uses writes: the port never crashes, and a feed it
    /// reads holds the text's values, each integer exactly as far as a `Decimal` keeps a number's
    /// digits (`TextValues`).
    case FOREIGN
}

struct DecodeCase: Decodable {
    let name: String
    let caseClass: CaseClass
    /// Kotlin's answer: "feed", "newer" or "malformed".
    let result: String
    /// The port's answer where the Lead's call 8 and Ruling 57 item 2 make it differ, a wrong type
    /// kotlinx reads leniently: only ever "malformed".
    let swift: String?
    let expect: Expect?
    let bom: Bool?
    let file: String?
    let json: String?

    enum CodingKeys: String, CodingKey {
        case name, caseClass = "class", result, swift, expect, bom, file, json
    }
}

/// What Kotlin decodes from a feed's text.
struct Expect: Decodable {
    /// Day 0's Asr as `WatchRules.clockText` draws it and `spokenClock` says it.
    struct Clock: Decodable {
        let clockText: String
        let spokenClock: String
    }

    let days: Int
    let day0HijriDayMonth: String?
    let day0GregorianSpoken: String?
    let day0Midnight: Int64?
    let digitZero: String
    /// The ring's zero, plan 3's `ringDigitZero`: nil where the feed has none.
    let ringDigitZero: String?
    let latinScript: Bool
    /// The phone's Show sunrise, `ui.showSunrise`: true where the feed has no such key.
    let showSunrise: Bool
    /// Nil for a feed with no day.
    let day0Asr: Clock?
    let hasSpoken: Bool
    let phrases: Int
    let city: String
}

struct SpokenCase: Decodable {
    let feed: String
    let name: String
    let clock: String
    let secondsLeft: Int64
    let spoken: String?
    let caseClass: CaseClass

    enum CodingKeys: String, CodingKey { case feed, name, clock, secondsLeft, spoken, caseClass = "class" }
}

struct LabelCase: Decodable {
    let feed: String
    let day: Int
    let prayer: String
    let label: String
    let caseClass: CaseClass

    enum CodingKeys: String, CodingKey { case feed, day, prayer, label, caseClass = "class" }
}

struct DayCase: Decodable {
    let name: String
    let feed: String
    let now: Int64
    let today: Int?
    let dayStart: Int64?
    let caseClass: CaseClass

    enum CodingKeys: String, CodingKey { case name, feed, now, today, dayStart, caseClass = "class" }
}

/// `WatchRules.clockText` and `spokenClock` for one of day `day`'s six times, by its `WatchTime`.
struct ClockCase: Decodable {
    let feed: String
    let day: Int
    let time: String
    let clockText: String
    let spokenClock: String
    let caseClass: CaseClass

    enum CodingKeys: String, CodingKey { case feed, day, time, clockText, spokenClock, caseClass = "class" }
}

/// A feed's `WatchFeed.words()`, the screens' words with the English for every blank, as Kotlin
/// writes them whole: read as the text's own tree, never through the port's model, so the port's
/// words are held to it key by key (`TextValues`). `presetIds` are the ids of its presets in
/// Kotlin's order, which neither that tree nor the port's dictionary keeps (Ruling 60 item 1e):
/// `TasbeehPresets.builtIn`'s, then each id the built-ins lack in the order the phone sent it.
struct WordsCase: Decodable {
    let feed: String
    let words: JSONValue
    let presetIds: [String]
    let caseClass: CaseClass

    enum CodingKeys: String, CodingKey { case feed, words, presetIds, caseClass = "class" }
}

/// A rules case in `watch-rules-vectors.json`'s shape, on a feed of `decode` (Ruling 57 item 5).
/// `progress` is Kotlin's `Float` widened to a `Double`, so the port's is held exactly.
struct MomentCase: Decodable {
    let name: String
    let feed: String
    let now: Int64
    let next: StartVector?
    let previous: StartVector?
    let secondsLeft: Int64?
    let progress: Double?
    let countdown: String?
    let litOnList: String?
    let litOnGlance: String?
    let caseClass: CaseClass

    enum CodingKeys: String, CodingKey {
        case name, feed, now, next, previous, secondsLeft, progress, countdown, litOnList, litOnGlance, caseClass = "class"
    }
}

/// `WatchRules.starts` on a feed of `decode`, whole.
struct StartsCase: Decodable {
    let feed: String
    let starts: [StartVector]
    let caseClass: CaseClass

    enum CodingKeys: String, CodingKey { case feed, starts, caseClass = "class" }
}

/// `WatchRules.filled(pattern, parts)`.
struct FilledCase: Decodable {
    let pattern: String
    let parts: [String]
    let filled: String
}

/// `WatchRules.inDigits(text, zero)`: `zero` one UTF-16 unit.
struct InDigitsCase: Decodable {
    let text: String
    let zero: String
    let inDigits: String
}

// MARK: - The text's own values

/// A JSON text's tree, the reference a decoded feed is held to (`TextValues.assertHolds`). It is
/// read by `JSONDecoder`, so it is the same text the port read, a key written twice included
/// (the copy that reader keeps), but every value is read here by its JSON kind and found by its
/// key, never through the model's types or its hand-written `init(from:)`:
/// - a number as a `Decimal`, which keeps its digits (measured on Swift 6.4: every digit to 39
///   significant ones; past that it drops the rest), and the `Double` nearest it, for the model's
///   two `Double` fields; either is nil where it cannot be read (`1e400`, `1e-400`);
/// - a string as its own Unicode scalars.
indirect enum JSONValue: Decodable, CustomStringConvertible {
    case null
    case bool(Bool)
    case number(Decimal?, Double?)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    private struct AnyKey: CodingKey {
        let stringValue: String
        init?(stringValue: String) { self.stringValue = stringValue }
        var intValue: Int? { nil }
        init?(intValue: Int) { nil }
    }

    init(from decoder: Decoder) throws {
        if let object = try? decoder.container(keyedBy: AnyKey.self) {
            var members: [String: JSONValue] = [:]
            for key in object.allKeys {
                members[key.stringValue] = try object.decode(JSONValue.self, forKey: key)
            }
            self = .object(members)
        } else if var array = try? decoder.unkeyedContainer() {
            var elements: [JSONValue] = []
            while !array.isAtEnd { elements.append(try array.decode(JSONValue.self)) }
            self = .array(elements)
        } else {
            let single = try decoder.singleValueContainer()
            if single.decodeNil() {
                self = .null
            } else if let bool = try? single.decode(Bool.self) {
                self = .bool(bool)
            } else if let string = try? single.decode(String.self) {
                self = .string(string)
            } else {
                self = .number(try? single.decode(Decimal.self), try? single.decode(Double.self))
            }
        }
    }

    var description: String {
        switch self {
        case .null: return "null"
        case let .bool(bool): return "\(bool)"
        case let .number(exact, nearest): return exact.map { "\($0)" } ?? nearest.map { "\($0)" } ?? "a number no reader holds"
        case let .string(string): return "\"\(string)\""
        case let .array(elements): return "an array of \(elements.count)"
        case let .object(members): return "an object of \(members.count) keys"
        }
    }
}

/// Every value the port decoded, held to the value its text holds at the same place.
enum TextValues {
    /// The tree of a whole text.
    static func tree(_ text: Data) throws -> JSONValue {
        try JSONDecoder().decode(JSONValue.self, from: text)
    }

    /// What an absent key stands for: each Kotlin default that is not null, as the JSON value it
    /// equals, by `Type.key`. A nullable key's absence stands for nil and needs no line. Plan 3's
    /// (plan 4 Task 3): `WatchUi.screens`, an object whose every key is absent, so each of its
    /// words stands for its own default, "", an empty list or an empty map; and
    /// `WatchUi.latinScript`, true. And the phone's Show sunrise, `WatchUi.showSunrise`, true, and
    /// the Galaxy Tile's three compact countdown forms, "".
    static let defaults: [String: JSONValue] = [
        "WatchFeed.tasbeeh": .array([]),
        "WatchDay.midnight": .number(0, 0),
        "WatchDay.hijriDayMonth": .string(""),
        "WatchDay.gregorianSpoken": .string(""),
        "WatchUi.digitZero": .string("0"),
        "WatchUi.screens": .object([:]),
        "WatchUi.latinScript": .bool(true),
        "WatchUi.showSunrise": .bool(true),
        "WatchCountdownPhrases.compactHoursMinutes": .string(""),
        "WatchCountdownPhrases.compactHours": .string(""),
        "WatchCountdownPhrases.compactMinutes": .string(""),
    ].merging(screensDefaults) { kept, _ in kept }

    /// Every field of `WatchScreenWords` at its Kotlin default: "" for a word, an empty list for
    /// the cardinals and the turns, an empty map for the presets.
    private static let screensDefaults: [String: JSONValue] = {
        var defaults: [String: JSONValue] = [:]
        for child in Mirror(reflecting: WatchScreenWords()).children {
            guard let key = child.label else { continue }
            switch child.value {
            case is String: defaults["WatchScreenWords.\(key)"] = .string("")
            case is [String: String]: defaults["WatchScreenWords.\(key)"] = .object([:])
            default: defaults["WatchScreenWords.\(key)"] = .array([])
            }
        }
        return defaults
    }()

    /// Holds every stored property of `value`, which the port decoded, to `json`, the text's
    /// value at the same place, and returns how many single values it held. A property is found
    /// in the text by its own name, which is its key (`Feed.swift`), so a key read into another
    /// property fails here; a property whose key the text lacks must equal the Kotlin default
    /// (`defaults`) or be nil. A Kotlin `Int` or `Long` must be exactly the text's number (a
    /// `Decimal`, so a fraction rounded away fails), a `Double` the `Double` nearest the text's
    /// number, and a string or `Char` exactly the text's scalars (not merely canonically
    /// equivalent). Keys the model lacks, a later phone's, are not the port's to read.
    @discardableResult
    static func assertHolds(_ value: Any, _ json: JSONValue?, _ path: String, file: StaticString = #filePath, line: UInt = #line) -> Int {
        func fail(_ wanted: String) -> Int {
            XCTFail("\(path): the port read \(value), where the text has \(json?.description ?? "no value")\(wanted)", file: file, line: line)
            return 1
        }
        let mirror = Mirror(reflecting: value)
        if mirror.displayStyle == .optional {
            if let wrapped = mirror.children.first?.value { return assertHolds(wrapped, json, path, file: file, line: line) }
            switch json {
            case nil, .null?: return 1
            default: return fail("")
            }
        }
        guard let json else { return fail("") }
        switch (value, json) {
        case let (number as Int32, .number(exact?, _)):
            return exact == Decimal(Int64(number)) ? 1 : fail(", exactly")
        case let (number as Int64, .number(exact?, _)):
            return exact == Decimal(number) ? 1 : fail(", exactly")
        case let (number as Double, .number(_, nearest?)):
            return number.bitPattern == nearest.bitPattern ? 1 : fail(", as its nearest Double")
        case let (flag as Bool, .bool(text)):
            return flag == text ? 1 : fail("")
        case let (string as String, .string(text)):
            return string.unicodeScalars.elementsEqual(text.unicodeScalars) ? 1 : fail(", scalar for scalar")
        case let (character as Character, .string(text)):
            return String(character).unicodeScalars.elementsEqual(text.unicodeScalars) ? 1 : fail(", scalar for scalar")
        case let (map as [String: String], .object(members)):
            // A map, the presets: the same keys, each value its text's.
            guard Set(map.keys) == Set(members.keys) else { return fail(", key for key (\(map.keys.sorted()))") }
            return map.keys.sorted().reduce(0) { held, key in
                held + assertHolds(map[key]!, members[key], "\(path).\(key)", file: file, line: line)
            }
        case let (_, .array(elements)) where mirror.displayStyle == .collection:
            guard mirror.children.count == elements.count else { return fail(", of \(mirror.children.count) elements") }
            return zip(mirror.children, elements).enumerated().reduce(0) { held, element in
                held + assertHolds(element.element.0.value, element.element.1, "\(path)[\(element.offset)]", file: file, line: line)
            }
        case let (_, .object(members)) where mirror.displayStyle == .struct:
            let type = String(describing: Swift.type(of: value))
            return mirror.children.reduce(0) { held, child in
                guard let key = child.label else { return held + fail(", a property with no name") }
                return held + assertHolds(child.value, members[key] ?? defaults["\(type).\(key)"], "\(path).\(key)", file: file, line: line)
            }
        default:
            return fail("")
        }
    }

    /// Holds a feed the port read to the text it read it from, failing on a section that held
    /// nothing.
    static func assertFeedHolds(_ feed: WatchFeed, _ text: Data, _ name: String, file: StaticString = #filePath, line: UInt = #line) {
        do {
            let held = assertHolds(feed, try tree(text), name, file: file, line: line)
            XCTAssertGreaterThan(held, 0, name, file: file, line: line)
        } catch {
            XCTFail("\(name) is a feed to the port, and its text has no tree: \(error)", file: file, line: line)
        }
    }
}

// MARK: - Answers

extension FeedDecodeResult {
    /// The answer as the vectors spell it.
    var vectorName: String {
        switch self {
        case .feed: return "feed"
        case .newerVersion: return "newer"
        case .malformed: return "malformed"
        }
    }
}
