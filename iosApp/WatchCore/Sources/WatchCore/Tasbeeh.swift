import Foundation

// The tasbeeh rules of the phone's spec §3 in Swift (plan 4 Task 8): `:watchcore`'s
// `TasbeehEngine.kt`, `Dhikr.kt` and `TasbeehPresets.kt`, type for type, which the phone and the
// Galaxy count by. `tasbeeh-vectors.json`, written by Kotlin from its own engine, holds the port to
// them step for step (`TasbeehVectorsTests`). Pure: no clock, no storage; `TasbeehCounter` owns the
// hold and the writes, and the page the haptics.

/// One phrase said on the counter: a built-in's Arabic and transliteration, or his own phrase as
/// typed, with no transliteration, which is never translated.
struct Dhikr: Equatable {
    let id: String
    let arabic: String
    let transliteration: String?

    init(id: String, arabic: String, transliteration: String? = nil) {
        self.id = id
        self.arabic = arabic
        self.transliteration = transliteration
    }
}

/// A dhikr and how many of it a preset asks for before the next one begins.
struct DhikrPart: Equatable {
    let dhikr: Dhikr
    let count: Int
}

/// A counted sequence: one part for a single dhikr, three for the after-prayer set. Counting runs
/// on from 1 to `total` across the parts rather than starting again at each one.
struct TasbeehPreset: Equatable {
    let id: String
    let parts: [DhikrPart]
    var custom: Bool = false

    var total: Int { parts.reduce(0) { $0 + $1.count } }
}

/// Where one preset stands: `count` is 0 to the total within the round, `round` counts from 1.
struct TasbeehState: Codable, Equatable {
    var presetId: String
    var count: Int
    var round: Int
}

/// What a tap did, so the page knows which haptic is due: exactly one a tap.
enum TapEvent: Equatable {
    /// An ordinary count, and the tap that opens the next round from a finished set.
    case tick
    /// The count has just reached the end of the part at this index, and another part follows.
    case partComplete(Int)
    /// The count has just reached the preset's total.
    case setComplete
}

/// What a tap, a turn back, a new round and a reset do: `TasbeehEngine.kt`.
enum TasbeehEngine {
    /// One count. From a count below the total it is the count plus one, and its event is the set's
    /// end at the total, a part's end at a part's cumulative end before the last, and a tick
    /// otherwise. A tap on a finished set (the count at or past the total) opens the next round at
    /// 1, a tick: the set was celebrated when it ended. A preset with nothing to count is unchanged.
    static func tap(_ state: TasbeehState, _ preset: TasbeehPreset) -> (TasbeehState, TapEvent) {
        let total = preset.total
        if total <= 0 { return (state, .tick) }
        var next = state
        if state.count >= total {
            next.count = 1
            next.round = state.round + 1
            return (next, .tick)
        }
        next.count = state.count + 1
        if next.count >= total { return (next, .setComplete) }
        // The last part's end is the total, the set's end above, so a single part never ends a part.
        if let part = partEnds(preset).dropLast().firstIndex(of: next.count) { return (next, .partComplete(part)) }
        return (next, .tick)
    }

    /// The part the next count belongs to, which is the one the page shows: the dhikr changes as a
    /// part's end is reached. At the total it is the last part, so a finished set shows whole.
    static func currentPart(_ state: TasbeehState, _ preset: TasbeehPreset) -> Int {
        if preset.parts.isEmpty { return 0 }
        return partEnds(preset).firstIndex { $0 > state.count } ?? preset.parts.count - 1
    }

    /// A turn back: the last count taken away, in the same round, or nil with nothing to take. It
    /// takes one from the count as the page shows it, held to the total (a target lowered on the
    /// phone), so it never reopens the round before: at 0 it is nil, and from a finished set it is
    /// the total less one, in that round.
    static func undo(_ state: TasbeehState, _ preset: TasbeehPreset) -> TasbeehState? {
        let shown = min(state.count, preset.total)
        guard shown > 0 else { return nil }
        var back = state
        back.count = shown - 1
        return back
    }

    /// Back to the start of the preset: 0 of round 1.
    static func reset(_ state: TasbeehState) -> TasbeehState {
        var start = state
        start.count = 0
        start.round = 1
        return start
    }

    /// The round after this one, before anything is said in it: 0 of the next round, which a
    /// finished set rolls over to on its own a moment after it ends.
    static func nextRound(_ state: TasbeehState) -> TasbeehState {
        var next = state
        next.count = 0
        next.round = state.round + 1
        return next
    }

    /// The count each part ends on: 33, 66 and 100 for the after-prayer set.
    static func partEnds(_ preset: TasbeehPreset) -> [Int] {
        var running = 0
        return preset.parts.map { part in
            running += part.count
            return running
        }
    }
}

/// The seven built-ins (`TasbeehPresets.kt`), in the phone's order, and his own phrases from the
/// feed. The ids are the keys the counts are kept under.
enum TasbeehPresets {
    /// The phrase counted until another is chosen, and the fallback when his phrase has gone.
    static let defaultId = "after_prayer"

    /// The largest target a phrase of his may have: the phone's store holds every target to 1 to
    /// this, and the watch holds the targets it is sent to the same.
    static let maxTarget = 1_000

    private static let subhanallah = Dhikr(id: "subhanallah", arabic: "سُبْحَانَ ٱللَّٰهِ", transliteration: "SubhanAllah")
    private static let alhamdulillah = Dhikr(id: "alhamdulillah", arabic: "ٱلْحَمْدُ لِلَّٰهِ", transliteration: "Alhamdulillah")
    private static let allahuAkbar = Dhikr(id: "allahu_akbar", arabic: "ٱللَّٰهُ أَكْبَرُ", transliteration: "Allahu Akbar")
    private static let astaghfirullah = Dhikr(id: "astaghfirullah", arabic: "أَسْتَغْفِرُ ٱللَّٰهَ", transliteration: "Astaghfirullah")
    private static let laIlahaIllallah = Dhikr(id: "la_ilaha_illallah", arabic: "لَا إِلَٰهَ إِلَّا ٱللَّٰهُ", transliteration: "La ilaha illallah")
    private static let subhanallahiWaBihamdihi = Dhikr(
        id: "subhanallahi_wa_bihamdihi", arabic: "سُبْحَانَ ٱللَّٰهِ وَبِحَمْدِهِ", transliteration: "SubhanAllahi wa bihamdihi"
    )

    /// Every single dhikr counts to a hundred; 33 · 33 · 34 is the after-prayer set's alone.
    private static let singleTotal = 100

    /// The seven, in the phone's order: the order the words' names are sent in (the words vectors'
    /// `presetIds`) and the phrase list's (plan 4 erratum p4 #13).
    static let builtIn: [TasbeehPreset] = [
        TasbeehPreset(id: "after_prayer", parts: [
            DhikrPart(dhikr: subhanallah, count: 33), DhikrPart(dhikr: alhamdulillah, count: 33), DhikrPart(dhikr: allahuAkbar, count: 34),
        ]),
        TasbeehPreset(id: "subhanallah", parts: [DhikrPart(dhikr: subhanallah, count: singleTotal)]),
        TasbeehPreset(id: "alhamdulillah", parts: [DhikrPart(dhikr: alhamdulillah, count: singleTotal)]),
        TasbeehPreset(id: "allahu_akbar", parts: [DhikrPart(dhikr: allahuAkbar, count: singleTotal)]),
        TasbeehPreset(id: "astaghfirullah", parts: [DhikrPart(dhikr: astaghfirullah, count: singleTotal)]),
        TasbeehPreset(id: "la_ilaha_illallah", parts: [DhikrPart(dhikr: laIlahaIllallah, count: singleTotal)]),
        TasbeehPreset(id: "subhanallahi_wa_bihamdihi", parts: [DhikrPart(dhikr: subhanallahiWaBihamdihi, count: singleTotal)]),
    ]

    /// After prayer.
    static var `default`: TasbeehPreset { builtIn[0] }

    /// One of his phrases: one part, as typed, never transliterated, counting to its target held to
    /// 1 to `maxTarget`, the bound the phone's store keeps.
    static func custom(_ phrase: WatchPhrase) -> TasbeehPreset {
        let target = min(max(Int(phrase.target), 1), maxTarget)
        return TasbeehPreset(id: phrase.id, parts: [DhikrPart(dhikr: Dhikr(id: phrase.id, arabic: phrase.phrase), count: target)], custom: true)
    }

    /// The phrases the page counts, in the list's order: the seven, then his own from [feed] in the
    /// order the phone sent them (`wearApp`'s `PhraseList.of`). A phrase whose words are blank, by
    /// Kotlin's `isBlank` as `orEnglish` reads a word, is left out, and so is an id that is a
    /// built-in's or one already listed: the counts are kept by id (plan 4 erratum p4 #14).
    static func list(_ feed: WatchFeed?) -> [TasbeehPreset] {
        var list = builtIn
        var ids = Set(list.map(\.id))
        for phrase in feed?.tasbeeh ?? [] where !WatchScreenWords.isBlank(phrase.phrase) && ids.insert(phrase.id).inserted {
            list.append(custom(phrase))
        }
        return list
    }
}

/// The phone's two times (`TasbeehViewModel`), in seconds.
enum TasbeehTiming {
    /// How long a finished set holds its full ring before it rolls over: `SET_COMPLETE_HOLD_MS`.
    static let setCompleteHold = 1.0
    /// One write this long after the last change of a burst, not one for each (the phone's spec §6).
    static let writeDebounce = 0.3
}
