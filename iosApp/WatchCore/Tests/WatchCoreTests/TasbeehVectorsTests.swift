import Foundation
import XCTest
@testable import WatchCore

/// The tasbeeh rules in Swift (plan 4 Task 8), held to `watchcore/vectors/tasbeeh-vectors.json`,
/// which `TasbeehVectorsTest.kt` writes from the phone's own engine (`TasbeehEngine`,
/// `TasbeehPresets` in `:watchcore`): every sequence step for step, the seven built-ins in the
/// phone's order with their Arabic and transliterations, and the constants. The phrase list is the
/// built-ins in that order, then his own from the feed in the phone's order (plan 4 errata p4 #13
/// and #14).
final class TasbeehVectorsTests: XCTestCase {

    func testEveryTasbeehSequenceMatchesTheVectors() throws {
        var steps = 0
        for sequence in Vectors.nonEmpty(TasbeehVectors.file.sequences, "sequences") {
            let preset = sequence.preset.preset
            var state = TasbeehState(presetId: preset.id, count: sequence.start.count, round: sequence.start.round)
            for (index, step) in sequence.steps.enumerated() {
                let at = "\(sequence.name) step \(index + 1) (\(step.do))"
                switch step.do {
                case "tap":
                    let (next, event) = TasbeehEngine.tap(state, preset)
                    XCTAssertEqual(TasbeehVectors.name(of: event), step.event, at)
                    state = next
                case "undo":
                    let back = TasbeehEngine.undo(state, preset)
                    XCTAssertEqual(back != nil, step.undone, at)
                    state = back ?? state
                case "nextRound":
                    state = TasbeehEngine.nextRound(state)
                case "reset":
                    state = TasbeehEngine.reset(state)
                default:
                    XCTFail("\(at): an action this port does not know")
                }
                XCTAssertEqual(state.count, step.count, "\(at): count")
                XCTAssertEqual(state.round, step.round, "\(at): round")
                XCTAssertEqual(state.presetId, preset.id, at)
                XCTAssertEqual(TasbeehEngine.currentPart(state, preset), step.part, "\(at): part")
                steps += 1
            }
        }
        XCTAssertGreaterThanOrEqual(steps, 240, "every step of the file was run")
    }

    func testTheBuiltInsAreThePhonesWithTheirWords() throws {
        let file = TasbeehVectors.file
        XCTAssertEqual(TasbeehPresets.builtIn.map(\.id), file.builtIn.map(\.id), "the phone's order")
        for (preset, vector) in zip(TasbeehPresets.builtIn, file.builtIn) {
            XCTAssertFalse(preset.custom, preset.id)
            XCTAssertEqual(preset.parts.map(\.count), vector.parts.map(\.count), preset.id)
            XCTAssertEqual(preset.parts.map(\.dhikr.id), vector.parts.map(\.dhikr), preset.id)
            XCTAssertEqual(preset.parts.map(\.dhikr.arabic), vector.parts.map(\.arabic), preset.id)
            XCTAssertEqual(preset.parts.map(\.dhikr.transliteration), vector.parts.map(\.transliteration), preset.id)
        }
        XCTAssertEqual(TasbeehPresets.defaultId, file.defaultId)
        XCTAssertEqual(TasbeehPresets.default.id, file.defaultId)
        XCTAssertEqual(TasbeehPresets.maxTarget, file.maxTarget)
        XCTAssertEqual(TasbeehTiming.setCompleteHold, Double(file.setCompleteHoldMs) / 1000)
        XCTAssertEqual(TasbeehTiming.writeDebounce, 0.3, "the phone's debounce, spec 6")
    }

    /// The built-ins are listed in the order the words vectors carry their names' ids (`presetIds`,
    /// Ruling 60 item 1e), the phone's: the port's dictionary of names keeps no order, so the list
    /// takes it from `TasbeehPresets.builtIn`, held here to Kotlin's (plan 4 erratum p4 #13).
    func testTheBuiltInsAreInTheOrderTheWordsCarry() throws {
        let count = TasbeehPresets.builtIn.count
        for words in Vectors.nonEmpty(Vectors.feeds.words, "words") {
            XCTAssertEqual(Array(words.presetIds.prefix(count)), TasbeehPresets.builtIn.map(\.id), words.feed)
        }
        let sample = try Vectors.feed("sample")
        XCTAssertEqual(Array(TasbeehPresets.list(sample).prefix(count)), TasbeehPresets.builtIn)
    }

    func testUndoAtZeroStaysAtZeroInTheRound() {
        let set = TasbeehPresets.default
        XCTAssertNil(TasbeehEngine.undo(at(0, 1), set))
        XCTAssertNil(TasbeehEngine.undo(at(0, 3), set), "round 3's 0 never reopens round 2's 100")
        XCTAssertEqual(TasbeehEngine.undo(at(1, 2), set), at(0, 2))
        XCTAssertEqual(TasbeehEngine.undo(at(100, 1), set), at(99, 1), "a finished set is taken back within its round")
    }

    func testUndoAcrossAPartBoundaryShowsTheEarlierPart() throws {
        let set = TasbeehPresets.default
        let from33 = try XCTUnwrap(TasbeehEngine.undo(at(33, 1), set))
        XCTAssertEqual(from33, at(32, 1))
        XCTAssertEqual(TasbeehEngine.currentPart(from33, set), 0, "33 to 32 shows SubhanAllah again")
        let from34 = try XCTUnwrap(TasbeehEngine.undo(at(34, 1), set))
        XCTAssertEqual(from34, at(33, 1))
        XCTAssertEqual(TasbeehEngine.currentPart(from34, set), 1, "34 to 33 still shows Alhamdulillah")
    }

    // MARK: - The phrase list

    /// The seven built-ins, then his own from the feed in the order the phone sent them, each as
    /// typed and counting to its target: `wearApp`'s `PhraseList.of`, its twin.
    func testHisOwnPhrasesFollowTheBuiltIns() throws {
        let sample = try Vectors.feed("sample")
        let list = TasbeehPresets.list(sample)
        XCTAssertEqual(list.map(\.id), TasbeehPresets.builtIn.map(\.id) + ["custom_1730000000000", "custom_1730000000001"])
        let own = Array(list.dropFirst(7))
        XCTAssertEqual(own.map(\.custom), [true, true])
        XCTAssertEqual(own.map { $0.parts.map(\.dhikr.arabic) }, [["سبحان الله وبحمده، سبحان الله العظيم"], ["Astaghfirullah wa atubu ilayh"]])
        XCTAssertEqual(own.map(\.total), [100, 33])
        XCTAssertEqual(own.map { $0.parts.map(\.dhikr.transliteration) }, [[nil], [nil]], "his words are never transliterated")
        XCTAssertEqual(TasbeehPresets.list(nil), TasbeehPresets.builtIn, "with no feed, the built-ins alone")
    }

    /// A phrase whose words are blank, by Kotlin's `isBlank` as both `orEnglish` read it, is left
    /// out, and so is an id that is a built-in's or one already listed: the counts are kept by id
    /// (plan 4 erratum p4 #14, Ruling 61 item 3).
    func testABlankPhraseOrATakenIdIsLeftOut() throws {
        let feed = try feedWith([
            WatchPhrase(id: "custom_a", phrase: " \u{2028}\u{1F}", target: 33),
            WatchPhrase(id: "subhanallah", phrase: "My own words", target: 10),
            WatchPhrase(id: "custom_b", phrase: "Kept", target: 7),
            WatchPhrase(id: "custom_b", phrase: "Kept twice", target: 9),
            WatchPhrase(id: "custom_c", phrase: "\u{0085}", target: 3),
        ])
        let own = TasbeehPresets.list(feed).dropFirst(7)
        XCTAssertEqual(own.map(\.id), ["custom_b", "custom_c"], "U+0085 is no space to Kotlin, so its phrase is kept")
        XCTAssertEqual(own.first?.parts.first?.dhikr.arabic, "Kept")
    }

    func testTargetsAreHeldToOneToAThousand() throws {
        let feed = try feedWith([
            WatchPhrase(id: "custom_0", phrase: "Nought", target: 0),
            WatchPhrase(id: "custom_n", phrase: "Below", target: -5),
            WatchPhrase(id: "custom_big", phrase: "Above", target: 5_000),
            WatchPhrase(id: "custom_k", phrase: "A thousand", target: 1_000),
        ])
        XCTAssertEqual(TasbeehPresets.list(feed).dropFirst(7).map(\.total), [1, 1, 1_000, 1_000])
    }

    // MARK: - Helpers

    private func at(_ count: Int, _ round: Int) -> TasbeehState {
        TasbeehState(presetId: TasbeehPresets.defaultId, count: count, round: round)
    }

    /// The Cape Town sample with [phrases] in place of its own.
    private func feedWith(_ phrases: [WatchPhrase]) throws -> WatchFeed {
        let sample = try Vectors.feed("sample")
        return WatchFeed(version: sample.version, writtenAt: sample.writtenAt, days: sample.days, place: sample.place, ui: sample.ui, tasbeeh: phrases)
    }
}

/// `tasbeeh-vectors.json`, as `TasbeehVectorsTest.kt` writes it.
struct TasbeehVectors: Decodable {
    let defaultId: String
    let setCompleteHoldMs: Int
    let maxTarget: Int
    let builtIn: [BuiltIn]
    let sequences: [Sequence]

    struct BuiltIn: Decodable {
        let id: String
        let parts: [Part]
    }

    struct Part: Decodable {
        let dhikr: String
        let arabic: String
        let transliteration: String?
        let count: Int
    }

    struct Sequence: Decodable {
        let name: String
        let preset: Preset
        let start: Start
        let steps: [Step]
    }

    struct Preset: Decodable {
        let id: String
        let parts: [Int]
        let custom: Bool

        /// The preset the engine counts: only the parts' counts matter to it.
        var preset: TasbeehPreset {
            TasbeehPreset(
                id: id,
                parts: parts.enumerated().map { DhikrPart(dhikr: Dhikr(id: "\(id)#\($0.offset)", arabic: "\($0.offset)"), count: $0.element) },
                custom: custom
            )
        }
    }

    struct Start: Decodable {
        let count: Int
        let round: Int
    }

    struct Step: Decodable {
        let `do`: String
        let count: Int
        let round: Int
        let part: Int
        let event: String?
        let undone: Bool?
    }

    static let file: TasbeehVectors = {
        do {
            return try JSONDecoder().decode(TasbeehVectors.self, from: Vectors.data("tasbeeh-vectors.json"))
        } catch {
            fatalError("tasbeeh-vectors.json does not read: \(error)")
        }
    }()

    /// A tap's event as the file writes it.
    static func name(of event: TapEvent) -> String {
        switch event {
        case .tick: return "tick"
        case let .partComplete(index): return "part:\(index)"
        case .setComplete: return "set"
        }
    }
}
