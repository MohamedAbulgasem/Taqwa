import XCTest
@testable import WatchCore

/// What VoiceOver is told of the counter (plan 4 Task 8), plan 3's `TasbeehSpeech` (its Task 6, the
/// phone's accessibility round 2 rules), restating `wearApp`'s `TasbeehSpeechTest`: the dhikr's name,
/// with its round from round 2; the count as "13 of 100", with the next part's name at a part's end,
/// the round complete at the set's, and the completion again as a later round opens; in the feed's
/// words and digits, never the drawn labels; and the counter's two actions in the phone's order.
final class TasbeehSpeechTests: XCTestCase {
    private let set = TasbeehPresets.default

    private func at(_ count: Int, round: Int = 1, _ preset: TasbeehPreset? = nil) -> (TasbeehPreset, TasbeehState) {
        let preset = preset ?? set
        return (preset, TasbeehState(presetId: preset.id, count: count, round: round))
    }

    private func name(_ feed: WatchFeed, _ at: (TasbeehPreset, TasbeehState)) -> String { TasbeehSpeech.name(at.0, at.1, feed) }
    private func state(_ feed: WatchFeed, _ at: (TasbeehPreset, TasbeehState)) -> String { TasbeehSpeech.state(at.0, at.1, feed) }

    func testACountIsSaidAsTheCountAndTheTarget() throws {
        let english = try Vectors.feed("sample")
        XCTAssertEqual(name(english, at(13)), "SubhanAllah")
        XCTAssertEqual(state(english, at(13)), "13 of 100")
    }

    func testAPartsEndNamesTheNextPart() throws {
        let english = try Vectors.feed("sample")
        XCTAssertEqual(state(english, at(33)), "33 of 100. Next, Alhamdulillah")
        XCTAssertEqual(state(english, at(66)), "66 of 100. Next, Allahu Akbar")
        XCTAssertEqual(name(english, at(33)), "Alhamdulillah", "the counter shows the next part from its first count")
    }

    func testTheSetsEndIsTheRoundComplete() throws {
        let english = try Vectors.feed("sample")
        XCTAssertEqual(state(english, at(100)), "100 of 100. Round complete")
        XCTAssertEqual(name(english, at(100)), "Allahu Akbar")
    }

    func testALaterRoundOpensWithTheCompletionAndNamesItsRound() throws {
        let english = try Vectors.feed("sample")
        XCTAssertEqual(state(english, at(0, round: 2)), "Round complete. 0 of 100, round 2")
        XCTAssertEqual(name(english, at(0, round: 2)), "SubhanAllah, round 2")
        XCTAssertEqual(name(english, at(50, round: 3)), "Alhamdulillah, round 3", "the name says the round at every count from round 2")
        XCTAssertEqual(state(english, at(50, round: 3)), "50 of 100", "the state says the round only at 0")
        XCTAssertEqual(state(english, at(0)), "0 of 100", "round 1 is the start, and is not said")
    }

    func testSaudiArabicIsSaidInItsWordsAndDigits() throws {
        let saudi = try Vectors.feed("sampleArSa")
        XCTAssertEqual(state(saudi, at(13)), "١٣ من ١٠٠")
        XCTAssertEqual(name(saudi, at(13)), "سبحان الله")
        XCTAssertEqual(state(saudi, at(33)), "٣٣ من ١٠٠. التالي: الحمد لله")
        XCTAssertEqual(name(saudi, at(0, round: 2)), "سبحان الله، الجولة ٢")
    }

    func testASinglePhraseHasNoPartsEnd() throws {
        let english = try Vectors.feed("sample")
        let subhanallah = try XCTUnwrap(TasbeehPresets.builtIn.first { $0.id == "subhanallah" })
        XCTAssertEqual(state(english, at(33, subhanallah)), "33 of 100")
        XCTAssertEqual(name(english, at(33, subhanallah)), "SubhanAllah")
    }

    func testAPhraseOfHisOwnIsNamedAsTyped() throws {
        let english = try Vectors.feed("sample")
        let typed = "Astaghfirullah wa atubu ilayh"
        let own = TasbeehPresets.custom(WatchPhrase(id: "custom_1730000000001", phrase: typed, target: 33))
        XCTAssertEqual(name(english, at(5, own)), typed)
        XCTAssertEqual(state(english, at(5, own)), "5 of 33")
        XCTAssertEqual(name(english, at(0, round: 2, own)), "\(typed), round 2")
        let percent = TasbeehPresets.custom(WatchPhrase(id: "custom_2", phrase: "100%2$s sure", target: 10))
        XCTAssertEqual(name(english, at(1, round: 4, percent)), "100%2$s sure, round 4", "his words are never filled in turn")
        let underABuiltInsId = TasbeehPresets.custom(WatchPhrase(id: "subhanallah", phrase: "My own words", target: 10))
        XCTAssertEqual(name(english, at(1, underABuiltInsId)), "My own words", "his words, never a built-in's name for the same id")
        let saudi = try Vectors.feed("sampleArSa")
        let arabic = TasbeehPresets.custom(WatchPhrase(id: "custom_1730000000000", phrase: "سبحان الله وبحمده، سبحان الله العظيم", target: 100))
        XCTAssertEqual(name(saudi, at(7, arabic)), "سبحان الله وبحمده، سبحان الله العظيم")
    }

    /// The phone's Turkish draws "12. TUR" and "/ 100", and says "13, toplam 100" (WatchWords.kt's
    /// Turkish `countSpoken` and `nameRoundSpoken`, `values-tr`'s `tasbeeh_round` and `tasbeeh_of`):
    /// what is said is never what is drawn.
    func testTheTurkishStateNeverHoldsTheDrawnLabels() throws {
        let sample = try Vectors.feed("sample")
        var words = sample.ui.screens
        words.round = "%1$s. TUR"
        words.of = "/ %1$s"
        words.countSpoken = "%1$s, toplam %2$s"
        words.nameRoundSpoken = "%1$s, %2$s. tur"
        words.roundSpoken = "Tur tamamlandı. %1$s, %2$s. tur"
        let ui = sample.ui
        let turkish = WatchFeed(
            version: sample.version, writtenAt: sample.writtenAt, days: sample.days, place: sample.place,
            ui: WatchUi(
                language: "tr", rtl: false, digitZero: ui.digitZero, twelveHour: ui.twelveHour, am: ui.am, pm: ui.pm,
                countdown: ui.countdown, nextIn: ui.nextIn, spoken: ui.spoken, screens: words,
                ringDigitZero: ui.ringDigitZero, latinScript: true
            ),
            tasbeeh: sample.tasbeeh
        )
        for (count, round) in [(13, 1), (0, 2), (64, 12), (100, 3)] {
            for said in [state(turkish, at(count, round: round)), name(turkish, at(count, round: round))] {
                XCTAssertFalse(said.contains("/ "), said)
                XCTAssertFalse(said.contains("TUR"), said)
            }
        }
        XCTAssertEqual(state(turkish, at(13)), "13, toplam 100")
        XCTAssertEqual(name(turkish, at(13, round: 12)), "SubhanAllah, 12. tur")
    }

    /// The counter's two actions beside its count, as the Galaxy's: take one away, then choose a
    /// dhikr (plan 3's order; plan 4 errata p4 #1 and #6), in the phone's words.
    func testTheCounterOffersUndoThenThePhrases() throws {
        XCTAssertEqual(
            TasbeehSpeech.actions(WatchScreenWords.english),
            [CounterAction(label: "Take one away", does: .undo), CounterAction(label: "Choose a dhikr", does: .phrases)]
        )
        let words = try Vectors.feed("sampleArSa").words
        let actions = TasbeehSpeech.actions(words)
        XCTAssertEqual(actions.map(\.label), [words.undoAction, words.phrasesAction])
        XCTAssertEqual(actions.map(\.does), [.undo, .phrases])
        XCTAssertNotEqual(actions[0].label, "Take one away")
        XCTAssertNotEqual(actions[1].label, "Choose a dhikr")
    }
}
