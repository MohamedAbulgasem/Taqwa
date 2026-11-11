import Foundation

/// What the Tasbeeh page draws and says (plan 4 Task 8, `faces.js`'s `aTasbeeh`), from the feed's
/// words and digits and the phrase's state.
struct TasbeehScreenModel: Equatable {
    /// The dhikr on screen: a built-in's Arabic, his phrase as typed. Drawn in the system font.
    var arabic: String
    /// A built-in's transliteration, only under a Latin interface (`ui.latinScript`).
    var transliteration: String?
    /// "ROUND 1": the feed's `round`, filled, in capitals under a Latin interface.
    var roundLabel: String
    /// The count, held to the target, in the feed's digits.
    var count: String
    /// "of 100": the feed's `of`, filled with the target.
    var ofLabel: String
    /// How far round the ring is: the count over the target.
    var progress: Double
    /// Each part's end but the last, as a share of the target: none for a single phrase.
    var notches: [Double]
    /// The notches the ring's arc has reached, which are drawn.
    var passedNotches: [Double]
    /// The parts' dots under the count: one a part, none for a single phrase.
    var partDots: Int
    /// The part the next count belongs to.
    var currentPart: Int
    /// "Turn the crown or tap": drawn, never spoken.
    var hint: String
    /// VoiceOver's label and value (`TasbeehSpeech`).
    var spokenName: String
    var spokenState: String
    /// The counter's default action's hint, and its two named actions, in plan 3's order.
    var countAction: String
    var undoAction: String
    var phrasesAction: String
    /// The named actions in that order (`TasbeehSpeech.actions`), which the page offers VoiceOver.
    var actions: [CounterAction]
    /// The feed's language, which every spoken line carries (plan 4 erratum p4 #5), and its type.
    var language: String
    var latinScript: Bool
}

/// One row of the phrase list (`gTasbeehList`): the phrase's name in the phone's words (a built-in's
/// from `words.presets`, his own as typed), its target as drawn ("33·33·34" for the set, else its
/// total, in the feed's digits), what VoiceOver is told of it (the name, ", " and the total: plan 4
/// erratum p4 #1, each row one element), whether it is the one counted, and whether it is his own,
/// drawn in the system font as the phone draws his phrases. `wearApp`'s `PhraseRow`, its twin.
struct PhraseRow: Equatable {
    let id: String
    let name: String
    let target: String
    let spoken: String
    let selected: Bool
    let custom: Bool
}

/// How far each Tasbeeh line grows with the watch's text size, as the Galaxy's do (plan 3's call
/// 15, `wearApp`'s `TasbeehType`): each line's size is the drawing's times `grow(scale, cap:)`, where
/// `scale` is the text size's own (`scale(_:)` of `@ScaledMetric(relativeTo: .body)` of
/// `scaleBase`). The floor is 1, so nothing drawn changes at the watch's own default size or a
/// smaller one (spec, the standing rules), and each line stops at its cap: the list's names,
/// targets and Reset, the transliteration, "ROUND n" and "of 100" at 1.3, the dhikr at 1.15, and
/// the count and the hint at their drawn sizes. The list's title is the system's, which follows
/// the text size itself.
enum TasbeehType {
    static let dhikr = 1.15
    static let transliteration = 1.3
    static let round = 1.3
    static let count = 1.0
    static let of = 1.3
    static let hint = 1.0
    /// A row's name, and the Reset row, which the Galaxy draws as a row.
    static let rowName = 1.3
    static let rowTarget = 1.3

    /// The points a view's `@ScaledMetric(relativeTo: .body)` is given, which it reads back at the
    /// watch's own default size. Watches differ in that default: Apple gives 40, 41 and 42mm
    /// watches Large and 44, 45, 46 and 49mm ones XLarge ("Supporting multiple watch sizes"). The
    /// body style scales from each watch's own default, not from Large: a base of 100 read 100 at
    /// L and M on the SE 3 (40mm), and 100 at XL on the Series 12 (46mm), where L and M read 95
    /// (review round 3 of plan 4 Task 8, measured in its fix round 3). SwiftUI rounds a scaled
    /// metric to the pixel grid, half a point on an Apple Watch, so a base of 1 read 1.0 at every
    /// standard size and 1.5 at every accessibility size: on the SE 3 it hid the steps of about
    /// 5.5 % each from L to XL, XXL and XXXL (review round 2). From 100 the grid's half point is
    /// half a percent.
    static let scaleBase = 100.0

    /// The text size's scale, from [points], what `@ScaledMetric(relativeTo: .body)` of
    /// [scaleBase] reads: 1 at the watch's own default size. On the SE 3 (default L) it is 1.055,
    /// 1.11 and 1.16 at XL, XXL and XXXL, 1.27 at AccessibilityM, 1.325 at AccessibilityL and 1.38
    /// above; on the Series 12 (default XL) 1.05 and 1.105 at XXL and XXXL, 1.205 at
    /// AccessibilityM, 1.255 at AccessibilityL and 1.31 above, and 0.95 at L and M.
    static func scale(_ points: Double) -> Double {
        points / scaleBase
    }

    /// The text size's [scale], from 1 to [cap].
    static func grow(_ scale: Double, cap: Double) -> Double {
        min(max(scale, 1), cap)
    }
}

/// Where the hint stands at the screen's foot (spec 2.5, "the hint at the bottom"): clear of the
/// system's page dots on every watch. Every other place on the page is `aTasbeeh`'s, in its 172
/// units scaled by the screen's width, and the drawing's hint baseline is at 198. The page dots
/// stand from the screen's foot, not from its top, and the 46mm screen is shorter for its width
/// than the 40mm's (205 units high, not 209; the 42mm's shape is the same, not measured): on the
/// Series 12 (46mm) the drawing's 198 fell at 239.4 of its 248 points, where the dots start at
/// 240, and the dots crossed the hint's letters (review round 4 of plan 4 Task 8).
///
/// So the baseline is the drawing's, but never lower than `belowThePagesFoot` under the page's
/// own foot: the bottom edge of the safe area the pager leaves above its dots' band, which the
/// system sets for each screen. That is the drawing's place on the SE 3 (40mm), where the page's
/// foot is at 171 of 197 points and the baseline at 186.5, so nothing drawn there changes.
/// Measured in its fix round 4: the page's foot at 171 on the SE 3 and 212 on the Series 12, and the
/// dots' top at 193 and 240, 23.4 and 23.2 units under the foot, so the hint keeps the SE 3's room
/// above the dots on the Series 12 too: its baseline 6.9 units above them on the SE 3 and 6.7 on
/// the Series 12, where the drawing's left 0.5.
enum TasbeehFoot {
    /// `aTasbeeh`'s hint baseline, in its units.
    static let drawnBaseline = 198.0
    /// The furthest the baseline stands under the page's foot, in units: a little more than the
    /// SE 3's 16.44, so that the SE 3 keeps the drawing's place exactly.
    static let belowThePagesFoot = 16.45

    /// The hint's baseline, in points from the screen's top, on a screen [width] points wide whose
    /// page's own frame ends [pageFoot] points from the top.
    static func hintBaseline(width: Double, pageFoot: Double) -> Double {
        let k = width / 172
        return min(drawnBaseline * k, pageFoot + belowThePagesFoot * k)
    }
}

enum TasbeehScreen {
    /// The page for [preset] at [state], in [feed]'s words, read once (plan 4 erratum p4 #12).
    static func model(_ feed: WatchFeed, preset: TasbeehPreset, state: TasbeehState) -> TasbeehScreenModel {
        let words = feed.words
        let total = preset.total
        let shown = min(max(state.count, 0), max(total, 0))
        let part = TasbeehEngine.currentPart(state, preset)
        let dhikr = preset.parts.indices.contains(part) ? preset.parts[part].dhikr : nil
        let progress = total > 0 ? Double(shown) / Double(total) : 0
        let notches: [Double] = preset.parts.count < 2 || total <= 0 ? [] : TasbeehEngine.partEnds(preset).dropLast().map { Double($0) / Double(total) }
        let round = WatchRules.filled(words.round, digits(feed, state.round))
        return TasbeehScreenModel(
            arabic: dhikr?.arabic ?? "",
            transliteration: feed.ui.latinScript ? dhikr?.transliteration : nil,
            roundLabel: feed.ui.latinScript ? WatchRules.capitals(round, language: feed.ui.language) : round,
            count: digits(feed, shown),
            ofLabel: WatchRules.filled(words.of, digits(feed, total)),
            progress: progress,
            notches: notches,
            passedNotches: notches.filter { $0 <= progress },
            partDots: preset.parts.count < 2 ? 0 : preset.parts.count,
            currentPart: part,
            hint: words.crownHint,
            spokenName: TasbeehSpeech.name(preset, state, words: words, feed: feed),
            spokenState: TasbeehSpeech.state(preset, state, words: words, feed: feed),
            countAction: words.countAction,
            undoAction: words.undoAction,
            phrasesAction: words.phrasesAction,
            actions: TasbeehSpeech.actions(words),
            language: feed.ui.language,
            latinScript: feed.ui.latinScript
        )
    }

    /// The phrase list's rows for [list] (`TasbeehPresets.list`), [selected] the id being counted.
    static func rows(_ feed: WatchFeed, _ list: [TasbeehPreset], selected: String) -> [PhraseRow] {
        let words = feed.words
        let separator = separator(feed.ui.digitZero)
        return list.map { preset in
            let name = preset.custom ? (preset.parts.first?.dhikr.arabic ?? "") : (words.presets[preset.id] ?? preset.id)
            return PhraseRow(
                id: preset.id,
                name: name,
                target: preset.parts.map { digits(feed, $0.count) }.joined(separator: separator),
                spoken: "\(name), \(digits(feed, preset.total))",
                selected: preset.id == selected,
                custom: preset.custom
            )
        }
    }

    /// What sets a set's parts apart: the middle dot, as `gTasbeehList` writes "33·33·34", but in
    /// digits whose nought is itself a dot (Arabic-Indic ٠, extended Arabic-Indic ۰), where
    /// "٣٣·٣٣·٣٤" would read as one long number: those take the Arabic comma, as the Galaxy's list.
    private static func separator(_ zero: Character) -> String {
        zero == "٠" || zero == "۰" ? "، " : "·"
    }

    private static func digits(_ feed: WatchFeed, _ n: Int) -> String {
        WatchRules.digits(feed, Int64(n))
    }
}
