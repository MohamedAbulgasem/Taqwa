import Foundation
import XCTest
@testable import WatchCore

/// The words of the watch's screens as the feed carries them (`ui.screens`, plan 3 Task 10 and plan
/// 4 Task 3), and the English a feed falls back to for each word it left blank (`orEnglish`), held
/// to Kotlin's `WatchFeed.words()` by the `words` vectors.
final class WatchScreenWordsTests: XCTestCase {

    func testEveryWordsCaseMatchesTheVectors() throws {
        for words in Vectors.nonEmpty(Vectors.feeds.words, "words") {
            XCTAssertEqual(words.caseClass, .ENCODER, "words on \(words.feed)")
            let feed = try Vectors.feed(words.feed)
            // Key by key, each word scalar for scalar, the presets as a map.
            XCTAssertGreaterThan(TextValues.assertHolds(feed.words, words.words, "\(words.feed).words"), 30, words.feed)
        }
    }

    /// Each words case carries its presets' ids in Kotlin's order (`presetIds`, Ruling 60 item 1e),
    /// which the port's dictionary does not keep: the seven built-ins in `TasbeehPresets.builtIn`'s
    /// order, whatever order the phone sent them in, then each id they lack in the order the phone
    /// sent it (`blankWords`: custom_2, then custom_1). The ids are exactly the port's names, so plan
    /// 4 Task 8 can list a watch's presets in Kotlin's order and hold that list to these.
    func testThePresetIdsAreTheBuiltInsInTheirOrderThenThePhonesOwn() throws {
        let cases = Vectors.nonEmpty(Vectors.feeds.words, "words")
        let count = WatchScreenWords.english.presets.count
        let builtIns = Array(try XCTUnwrap(cases.first).presetIds.prefix(count))
        XCTAssertEqual(count, 7)
        XCTAssertEqual(Set(builtIns), Set(WatchScreenWords.english.presets.keys))
        XCTAssertEqual(builtIns.first, "after_prayer", "the default first, as the phone lists them")
        for words in cases {
            let feed = try Vectors.feed(words.feed)
            XCTAssertEqual(words.presetIds.count, Set(words.presetIds).count, "\(words.feed): an id twice")
            XCTAssertEqual(Set(words.presetIds), Set(feed.words.presets.keys), words.feed)
            XCTAssertEqual(Array(words.presetIds.prefix(count)), builtIns, words.feed)
        }
        let blank = try XCTUnwrap(cases.first { $0.feed == "blankWords" })
        XCTAssertEqual(Array(blank.presetIds.dropFirst(count)), ["custom_2", "custom_1"])
    }

    /// Ruling 66 item 1: the Qibla dial's short forms, read as Kotlin reads them, the English for a
    /// blank one, and the samples' own (the dial that draws them is plan 4 Task 9's). The forms are
    /// `WatchWords`', which a native reviewer may change, so none is written here (Ruling 67 item
    /// 2): the `words` vectors hold each sample's to Kotlin's, and `olderPhone`'s, a feed with no
    /// words, to Kotlin's English, which is this port's `english`.
    func testTheDialsShortFormsAreReadWithTheEnglishForABlankOne() throws {
        let english = WatchScreenWords.english
        let older = try XCTUnwrap(Vectors.feeds.words.first { $0.feed == "olderPhone" })
        XCTAssertGreaterThan(TextValues.assertHolds(english, older.words, "english"), 30, "the port's English is Kotlin's")
        XCTAssertEqual(WatchScreenWords().orEnglish().distanceOnDial, english.distanceOnDial)
        XCTAssertEqual(WatchScreenWords(qiblaDetailOnDial: " ").orEnglish().qiblaDetailOnDial, english.qiblaDetailOnDial)
        for name in ["sample", "sampleArSa"] {
            let screens = try Vectors.feed(name).ui.screens
            XCTAssertTrue(screens.distanceOnDial.contains("%1$s") && !screens.distanceOnDial.contains("%2$s"), "\(name): \(screens.distanceOnDial)")
            XCTAssertTrue(screens.qiblaDetailOnDial.contains("%1$s°") && screens.qiblaDetailOnDial.contains("%2$s"), "\(name): \(screens.qiblaDetailOnDial)")
            XCTAssertEqual(screens.orEnglish().distanceOnDial, screens.distanceOnDial, name)
        }
    }

    func testAnOlderFeedSpeaksEnglish() throws {
        let older = try Vectors.feed("olderPhone")
        XCTAssertEqual(older.ui.screens, WatchScreenWords())
        XCTAssertEqual(older.words, WatchScreenWords.english)
        XCTAssertNil(older.ui.ringDigitZero)
        XCTAssertTrue(older.ui.latinScript)
        XCTAssertTrue(older.days.allSatisfy { $0.clockTime == nil })
    }

    /// The samples the UI tests start from carry every word, read before `orEnglish`, so a screen
    /// drawn from one says the phone's words and never the fallback. Blank is the port's own rule,
    /// `WatchScreenWords.isBlank`, the one `orEnglish` falls back by (Ruling 60 item 1c).
    func testTheSamplesCarryEveryScreenWord() throws {
        let builtIns = Set(WatchScreenWords.english.presets.keys)
        XCTAssertEqual(builtIns.count, 7)
        for name in ["sample", "sampleEn12", "sampleArLy12", "sampleArSa"] {
            let screens = try Vectors.feed(name).ui.screens
            for child in Mirror(reflecting: screens).children {
                let field = child.label ?? "?"
                switch child.value {
                case let word as String: XCTAssertFalse(WatchScreenWords.isBlank(word), "\(name): \(field)")
                case let map as [String: String]: XCTAssertTrue(map.values.allSatisfy { !WatchScreenWords.isBlank($0) } && !map.isEmpty, "\(name): \(field)")
                case let list as [String]: XCTAssertTrue(!list.isEmpty && list.allSatisfy { !WatchScreenWords.isBlank($0) }, "\(name): \(field)")
                case let counts as [WatchCount]: XCTAssertTrue(!counts.isEmpty && counts.allSatisfy { !WatchScreenWords.isBlank($0.phrase) }, "\(name): \(field)")
                default: XCTFail("\(name): \(field) is of a kind this test does not read")
                }
            }
            XCTAssertEqual(Set(screens.presets.keys), builtIns, name)
        }
        // The English the UI tests listen for.
        let english = try Vectors.feed("sample").ui.screens
        XCTAssertEqual(english.rowPassed, "Passed")
        XCTAssertEqual(english.of, "of %1$s")
        XCTAssertEqual(english.countSpoken, "%1$s of %2$s")
        XCTAssertEqual(english.appName, "Taqwa")
        XCTAssertEqual(english.crownHint, "Turn the crown or tap")
        XCTAssertEqual(english.facingQibla, "Facing qibla")
        XCTAssertEqual(english.distance, "Makkah · %1$s km away")
        let arabic = try Vectors.feed("sampleArLy12").ui.screens
        XCTAssertEqual(arabic.crownHint, "أدِر التاج أو اضغط")
        XCTAssertEqual(arabic.appName, "تقوى")
    }

    /// A `null` for any one word, list or map is refused, as kotlinx refuses it (every one has a
    /// default that is not null; `SwiftPortVectorsTest.plan3sKeysAreRefusedAsKotlinRefusesThem`
    /// measures Kotlin on each). The trap is `decodeIfPresent(...) ?? ""`, which reads it as absent.
    /// An absent one is its default.
    func testANullInEveryWordIsRefused() throws {
        let oneDay = try Vectors.text(of: try Vectors.decodeCase("oneDay"))
        let tree = try XCTUnwrap(try JSONSerialization.jsonObject(with: oneDay) as? [String: Any])
        func with(_ change: (inout [String: Any]) -> Void) throws -> Data {
            var root = tree
            var ui = try XCTUnwrap(root["ui"] as? [String: Any])
            var screens = try XCTUnwrap(ui["screens"] as? [String: Any])
            change(&screens)
            ui["screens"] = screens
            root["ui"] = ui
            return try JSONSerialization.data(withJSONObject: root)
        }
        // The tree written back is still oneDay's feed: only the change below tells.
        guard case let .feed(control) = FeedDecoder.decode(try with { _ in }) else { return XCTFail("oneDay rewritten is no feed") }
        let keys = try XCTUnwrap((tree["ui"] as? [String: Any])?["screens"] as? [String: Any]).keys.sorted()
        XCTAssertEqual(keys.count, Mirror(reflecting: WatchScreenWords()).children.count, "every word is in oneDay")
        for key in keys {
            XCTAssertEqual(FeedDecoder.decode(try with { $0[key] = NSNull() }), .malformed, "screens.\(key) null")
            guard case let .feed(absent) = FeedDecoder.decode(try with { $0[key] = nil }) else {
                XCTFail("screens.\(key) absent is no feed")
                continue
            }
            XCTAssertNotEqual(absent.ui.screens, control.ui.screens, "screens.\(key) absent takes its default")
        }
    }

    func testEveryPhoneGoldenCarriesTheWords() throws {
        for name in ["phone-tripoli-ar-ly12.json", "phone-capetown-en.json"] {
            guard case let .feed(feed) = FeedDecoder.decode(try Vectors.data("phone/\(name)")) else {
                XCTFail("\(name) is no feed")
                continue
            }
            XCTAssertFalse(feed.ui.screens.crownHint.isEmpty, name)
            XCTAssertFalse(feed.ui.screens.appName.isEmpty, name)
            XCTAssertFalse(feed.ui.screens.round.isEmpty, name)
        }
        guard case let .feed(tripoli) = FeedDecoder.decode(try Vectors.data("phone/phone-tripoli-ar-ly12.json")) else { return }
        XCTAssertEqual(tripoli.ui.screens.appName, "تقوى")
        XCTAssertEqual(tripoli.ui.screens.crownHint, "أدِر التاج أو اضغط")
    }

    /// Blank is Kotlin's `isBlank`: every UTF-16 unit a `Char.isWhitespace`, which is a space
    /// separator, a line or paragraph separator, or one of the controls tab to carriage return and
    /// U+001C to U+001F. Foundation's `whitespacesAndNewlines` differs both ways: it takes U+0085,
    /// which Kotlin keeps, and leaves U+001C, which Kotlin drops. Each of the four controls is
    /// blank alone (Ruling 60 item 1b), so a rule that ends at U+001E or takes U+001C alone fails.
    func testABlankWordIsKotlinsBlank() {
        let english = WatchScreenWords.english
        for blank in ["", " ", "\u{00A0}", "\u{3000}", "\t\n", "\u{001C}", "\u{001D}", "\u{001E}", "\u{001F}", "\u{2028}", "\u{2029}", "\u{000B}\u{000C}\r"] {
            XCTAssertEqual(WatchScreenWords(qibla: blank).orEnglish().qibla, english.qibla, blank.unicodeScalars.map { String($0.value, radix: 16) }.joined(separator: " "))
        }
        for kept in ["\u{0085}", "\u{200B}", "\u{FEFF}", "Taqwa ", "\u{0001}"] {
            XCTAssertEqual(WatchScreenWords(qibla: kept).orEnglish().qibla, kept, kept.unicodeScalars.map { String($0.value, radix: 16) }.joined(separator: " "))
        }
    }

    func testBlankFieldsTakeTheEnglish() {
        let english = WatchScreenWords.english
        let words = WatchScreenWords(appName: "", round: "الجولة %1$s", crownHint: " ").orEnglish()
        XCTAssertEqual(words.round, "الجولة %1$s")
        XCTAssertEqual(words.crownHint, "Turn the crown or tap")
        XCTAssertEqual(words.appName, "Taqwa")
        var wanted = english
        wanted.round = "الجولة %1$s"
        XCTAssertEqual(words, wanted)
        XCTAssertEqual(WatchScreenWords().orEnglish(), english)
        XCTAssertEqual(english.orEnglish(), english)
        // A list is kept whenever it holds anything, a blank item and all; only an empty one is English.
        XCTAssertEqual(WatchScreenWords(cardinals: [" ", "E", "S", "W"]).orEnglish().cardinals, [" ", "E", "S", "W"])
        // The presets overlay the English: a blank name is the English one, an id it lacks is kept.
        let presets = WatchScreenWords(presets: ["subhanallah": " ", "alhamdulillah": "الحمد لله", "custom_1": "Mine"]).orEnglish().presets
        XCTAssertEqual(presets["subhanallah"], "SubhanAllah")
        XCTAssertEqual(presets["alhamdulillah"], "الحمد لله")
        XCTAssertEqual(presets["custom_1"], "Mine")
        XCTAssertEqual(presets.count, 8)
    }
}
