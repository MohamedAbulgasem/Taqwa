import XCTest
@testable import WatchCore

/// What the Tasbeeh page draws and says (plan 4 Task 8, `faces.js`'s `aTasbeeh`), and its phrase
/// list's rows: the dhikr on screen (a built-in's Arabic, his phrase as typed), its transliteration
/// only under a Latin interface, "ROUND n", the count and "of 100" in the feed's words and digits,
/// the ring's notches at each part's end, shown once the arc has passed them, and the crown's hint;
/// and for VoiceOver the name and the state as `TasbeehSpeech` says them, with the feed's actions.
final class TasbeehScreenTests: XCTestCase {
    private let set = TasbeehPresets.default

    /// The after-prayer set at [count] of [round].
    private func model(_ feed: WatchFeed, _ count: Int, round: Int = 1) -> TasbeehScreenModel {
        model(feed, set, count, round: round)
    }

    private func model(_ feed: WatchFeed, _ preset: TasbeehPreset, _ count: Int, round: Int = 1) -> TasbeehScreenModel {
        TasbeehScreen.model(feed, preset: preset, state: TasbeehState(presetId: preset.id, count: count, round: round))
    }

    private func builtIn(_ id: String) throws -> TasbeehPreset {
        try XCTUnwrap(TasbeehPresets.builtIn.first { $0.id == id })
    }

    func testTheSetsRingHasNotchesAt33And66() throws {
        let sample = try Vectors.feed("sample")
        let at40 = model(sample, 40)
        XCTAssertEqual(at40.notches, [0.33, 0.66])
        XCTAssertEqual(at40.partDots, 3)
        XCTAssertEqual(at40.currentPart, 1)
        let single = model(sample, try builtIn("astaghfirullah"), 64)
        XCTAssertEqual(single.notches, [], "a single phrase has no parts' ends")
        XCTAssertEqual(single.partDots, 0)
    }

    func testNotchesShowOnceTheArcPassesThem() throws {
        let sample = try Vectors.feed("sample")
        XCTAssertEqual(model(sample, 0).passedNotches, [])
        XCTAssertEqual(model(sample, 32).passedNotches, [])
        XCTAssertEqual(model(sample, 33).passedNotches, [0.33])
        XCTAssertEqual(model(sample, 65).passedNotches, [0.33])
        XCTAssertEqual(model(sample, 66).passedNotches, [0.33, 0.66])
        XCTAssertEqual(model(sample, 100).passedNotches, [0.33, 0.66])
        XCTAssertEqual(model(sample, 0, round: 2).passedNotches, [], "a new round starts the ring again")
    }

    func testTheTransliterationShowsOnlyUnderALatinInterface() throws {
        let english = model(try Vectors.feed("sample"), 40)
        XCTAssertEqual(english.arabic, "ٱلْحَمْدُ لِلَّٰهِ")
        XCTAssertEqual(english.transliteration, "Alhamdulillah")
        let arabic = model(try Vectors.feed("sampleArLy12"), 40)
        XCTAssertEqual(arabic.arabic, "ٱلْحَمْدُ لِلَّٰهِ", "the dhikr's Arabic in every language")
        XCTAssertNil(arabic.transliteration)
        let own = TasbeehPresets.custom(WatchPhrase(id: "custom_1", phrase: "Astaghfirullah wa atubu ilayh", target: 33))
        let his = model(try Vectors.feed("sample"), own, 5)
        XCTAssertEqual(his.arabic, "Astaghfirullah wa atubu ilayh", "his phrase as typed")
        XCTAssertNil(his.transliteration)
    }

    func testTheSpokenStateIsCountOfTarget() throws {
        let astaghfirullah = try builtIn("astaghfirullah")
        let english = model(try Vectors.feed("sample"), astaghfirullah, 64)
        XCTAssertEqual(english.spokenState, "64 of 100")
        XCTAssertEqual(english.spokenName, "Astaghfirullah")
        XCTAssertEqual(english.ofLabel, "of 100", "the drawn label stays the drawn one")
        let saudi = model(try Vectors.feed("sampleArSa"), astaghfirullah, 64)
        XCTAssertEqual(saudi.spokenState, "٦٤ من ١٠٠")
        XCTAssertEqual(saudi.language, "ar-SA")
    }

    /// The mockup's Astaghfirullah at 64 (`aTasbeeh`), in the phone's words and digits.
    func testTheDrawnLinesAreThePhones() throws {
        let astaghfirullah = try builtIn("astaghfirullah")
        let english = model(try Vectors.feed("sample"), astaghfirullah, 64)
        XCTAssertEqual(english.roundLabel, "ROUND 1")
        XCTAssertEqual(english.count, "64")
        XCTAssertEqual(english.ofLabel, "of 100")
        XCTAssertEqual(english.hint, "Turn the crown or tap")
        XCTAssertEqual(english.progress, 0.64, accuracy: 1e-12)
        XCTAssertEqual(english.countAction, "Count")
        XCTAssertEqual(english.undoAction, "Take one away")
        XCTAssertEqual(english.phrasesAction, "Choose a dhikr")
        XCTAssertEqual(english.actions, TasbeehSpeech.actions(WatchScreenWords.english), "take one away, then choose a dhikr")
        XCTAssertTrue(english.latinScript)
        let feed = try Vectors.feed("sampleArSa")
        let words = feed.words
        let saudi = model(feed, astaghfirullah, 64, round: 2)
        XCTAssertEqual(saudi.count, "٦٤")
        XCTAssertEqual(saudi.roundLabel, WatchRules.filled(words.round, "٢"), "as written, outside Latin letters")
        XCTAssertEqual(saudi.ofLabel, WatchRules.filled(words.of, "١٠٠"))
        XCTAssertEqual(saudi.hint, words.crownHint)
        XCTAssertFalse(saudi.latinScript)
    }

    /// A count kept above a target the phone has since lowered is drawn and said as the target.
    func testACountAboveTheTargetIsShownAsTheTarget() throws {
        let own = TasbeehPresets.custom(WatchPhrase(id: "custom_1", phrase: "Mine", target: 33))
        let held = model(try Vectors.feed("sample"), own, 50)
        XCTAssertEqual(held.count, "33")
        XCTAssertEqual(held.progress, 1)
        XCTAssertEqual(held.spokenState, "33 of 33. Round complete")
    }

    // MARK: - The phrase list

    /// Each row is one element for VoiceOver: the name, ", " and its total, with the chosen one
    /// marked (plan 4 erratum p4 #1); the set's target is drawn "33·33·34", as `gTasbeehList` draws
    /// it, and his own phrases follow the seven as typed.
    func testThePhraseListsRowsAreThePhonesWords() throws {
        let sample = try Vectors.feed("sample")
        let rows = TasbeehScreen.rows(sample, TasbeehPresets.list(sample), selected: TasbeehPresets.defaultId)
        XCTAssertEqual(rows.count, 9)
        XCTAssertEqual(rows[0], PhraseRow(id: "after_prayer", name: "After prayer", target: "33·33·34", spoken: "After prayer, 100", selected: true, custom: false))
        XCTAssertEqual(rows[1], PhraseRow(id: "subhanallah", name: "SubhanAllah", target: "100", spoken: "SubhanAllah, 100", selected: false, custom: false))
        XCTAssertEqual(rows[8], PhraseRow(id: "custom_1730000000001", name: "Astaghfirullah wa atubu ilayh", target: "33", spoken: "Astaghfirullah wa atubu ilayh, 33", selected: false, custom: true))
        XCTAssertEqual(rows.filter(\.selected).count, 1)
    }

    /// In Arabic-Indic digits, whose nought is a dot, the set's parts take the Arabic comma, as the
    /// Galaxy's list writes them: "٣٣·٣٣·٣٤" would read as one long number.
    func testASetsPartsAreNeverReadAsNoughts() throws {
        let saudi = try Vectors.feed("sampleArSa")
        let rows = TasbeehScreen.rows(saudi, TasbeehPresets.list(saudi), selected: "subhanallah")
        XCTAssertEqual(rows[0].target, "٣٣، ٣٣، ٣٤")
        XCTAssertEqual(rows[0].name, saudi.words.presets["after_prayer"])
        XCTAssertEqual(rows[0].spoken, "\(saudi.words.presets["after_prayer"] ?? ""), ١٠٠")
        XCTAssertTrue(rows[1].selected)
        let libya = try Vectors.feed("sampleArLy12")
        XCTAssertEqual(TasbeehScreen.rows(libya, TasbeehPresets.builtIn, selected: "x")[0].target, "33·33·34")
    }

    /// The lines grow with the watch's text size as the Galaxy's do (plan 3's call 15, `wearApp`'s
    /// `TasbeehType`): the list's names, targets and Reset, the transliteration, "ROUND n" and "of
    /// 100" up to 1.3 times, the dhikr up to 1.15, and the count and the hint keep their sizes. The
    /// floor is 1, so nothing drawn changes at the watch's own default size or a smaller one (spec,
    /// the standing rules: accessibility never changes the visible design at default settings).
    func testTheLinesGrowWithTheTextSizeUpToTheGalaxysCaps() {
        let caps: [(String, Double, Double)] = [
            ("dhikr", TasbeehType.dhikr, 1.15),
            ("transliteration", TasbeehType.transliteration, 1.3),
            ("round", TasbeehType.round, 1.3),
            ("count", TasbeehType.count, 1),
            ("of", TasbeehType.of, 1.3),
            ("hint", TasbeehType.hint, 1),
            ("row name and Reset", TasbeehType.rowName, 1.3),
            ("row target", TasbeehType.rowTarget, 1.3),
        ]
        for (line, cap, galaxy) in caps {
            XCTAssertEqual(cap, galaxy, "\(line): the Galaxy's cap")
            for smaller in [0.7, 0.85, 1.0] {
                XCTAssertEqual(TasbeehType.grow(smaller, cap: cap), 1, "\(line) at \(smaller): the drawing's size")
            }
            XCTAssertEqual(TasbeehType.grow(1.1, cap: cap), min(1.1, galaxy), accuracy: 1e-9, "\(line) a little larger")
            XCTAssertEqual(TasbeehType.grow(1.75, cap: cap), galaxy, "\(line) at the largest sizes: its cap")
        }
    }

    /// The text size is read from `@ScaledMetric(relativeTo: .body)` of `scaleBase` points, and
    /// SwiftUI rounds a scaled metric to the screen's pixel grid, half a point on an Apple Watch. A
    /// base of 1 point lost every standard step to it: on the SE 3 it read 1.0 from XS to XXXL and
    /// 1.5 at each accessibility size (review round 2 of plan 4 Task 8), so XL, XXL and XXXL grew no
    /// line. The ratios are body's to the watch's own default's that a base of 100 read: on the SE 3
    /// (40mm, default L) 105.5, 111 and 116 at XL, XXL and XXXL, and 127 at AccessibilityM; on the
    /// Series 12 (46mm, default XL) 105, 110.5 and 120.5 at XXL, XXXL and AccessibilityM. Rounded to
    /// the grid, the scale read keeps each to within half a percent, and each grows a line whose
    /// cap is above it.
    func testTheTextSizeIsReadFromABaseThePixelGridKeeps() {
        let measured: [(String, Double)] = [
            ("XL on the SE 3", 1.055), ("XXL on the SE 3", 1.11), ("XXXL on the SE 3", 1.16), ("AccessibilityM on the SE 3", 1.27),
            ("XXL on the Series 12", 1.05), ("XXXL on the Series 12", 1.105), ("AccessibilityM on the Series 12", 1.205),
        ]
        for (size, ratio) in measured {
            let onTheGrid = (TasbeehType.scaleBase * ratio * 2).rounded() / 2
            let scale = TasbeehType.scale(onTheGrid)
            XCTAssertEqual(scale, ratio, accuracy: 0.005, "\(size): the scale read is the size's own")
            XCTAssertGreaterThan(TasbeehType.grow(scale, cap: TasbeehType.transliteration), 1, "\(size) grows a line capped at 1.3")
        }
    }

    /// Each watch's own default text size draws the drawing (spec, the standing rules). Watches
    /// differ in that default: Apple gives 40, 41 and 42mm watches Large and 44, 45, 46 and 49mm
    /// ones XLarge ("Supporting multiple watch sizes"). The body style scales from each watch's own
    /// default, not from Large (review round 3 of plan 4 Task 8 asked; its fix round 3 measured):
    /// a base of 100 read 100 at L on the SE 3 and 100 at XL on the Series 12, so the scale is 1 at
    /// both defaults; the sizes below read 89, 94.5 and 100 (XS, S, M) on the SE 3 and 84.5, 89.5,
    /// 95 and 95 (XS, S, M, L) on the Series 12, and draw the drawing too.
    func testEachWatchsOwnDefaultDrawsTheDrawing() {
        let readings: [(String, Double)] = [
            ("L, the SE 3's default", 100), ("XL, the Series 12's default", 100),
            ("XS on the SE 3", 89), ("S on the SE 3", 94.5), ("M on the SE 3", 100),
            ("XS on the Series 12", 84.5), ("S on the Series 12", 89.5), ("M on the Series 12", 95), ("L on the Series 12", 95),
        ]
        let caps = [TasbeehType.dhikr, TasbeehType.transliteration, TasbeehType.round, TasbeehType.count, TasbeehType.of, TasbeehType.hint, TasbeehType.rowName, TasbeehType.rowTarget]
        for (size, points) in readings {
            for cap in caps {
                XCTAssertEqual(TasbeehType.grow(TasbeehType.scale(points), cap: cap), 1, "\(size): the drawing's size, cap \(cap)")
            }
        }
        XCTAssertEqual(TasbeehType.scale(TasbeehType.scaleBase), 1, "the base read back at a watch's own default is 1: nothing drawn changes")
    }

    /// The hint at the foot stands where `aTasbeeh` draws it on the SE 3 (40mm), at 198 units
    /// scaled by the width, and on a screen shorter for its width at the same height under the
    /// page's own foot: the bottom edge of the safe area the pager leaves above its page dots'
    /// band (review round 4 of plan 4 Task 8, which found the dots on the hint's letters on the
    /// Series 12 (46mm), where the drawing's 198 fell at 239.4 of its 248 points). Measured in its
    /// fix round 4: the page's foot at 171 of the SE 3's 197 points (162 wide) and at 212 of the
    /// Series 12's 248 (208 wide), and the dots' top at 193 and 240, 23.4 and 23.2 units under the
    /// foot. The hint's frame ends 3.2 units under its baseline (its letters with it), so a
    /// baseline 6.5 units above the dots leaves 3 points between them on the SE 3.
    func testTheHintStandsAboveThePageDotsOnEveryWatch() {
        XCTAssertEqual(TasbeehFoot.hintBaseline(width: 162, pageFoot: 171), 198 * 162 / 172, "the SE 3: the drawing's place, exactly")
        XCTAssertEqual(
            TasbeehFoot.hintBaseline(width: 208, pageFoot: 212), 212 + 16.45 * 208 / 172, accuracy: 1e-9,
            "the Series 12: 16.45 units under the page's foot, as on the SE 3, not the drawing's 239.4"
        )
        let watches: [(String, Double, Double, Double)] = [("the SE 3", 162, 171, 193), ("the Series 12", 208, 212, 240)]
        for (watch, width, foot, dotsTop) in watches {
            let k = width / 172
            let clear = (dotsTop - TasbeehFoot.hintBaseline(width: width, pageFoot: foot)) / k
            XCTAssertGreaterThan(clear, 6.5, "\(watch): the hint's baseline \(clear) units above the page dots")
        }
        XCTAssertEqual(TasbeehFoot.hintBaseline(width: 172, pageFoot: 200), 198, "a screen taller for its width keeps the drawing's place")
    }
}
