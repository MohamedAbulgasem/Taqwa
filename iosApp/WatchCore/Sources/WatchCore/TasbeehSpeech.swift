import Foundation

/// What one of the counter's actions does beside counting, which is its default action.
enum CounterDoes: Equatable {
    case undo, phrases
}

/// One of the counter's named actions for VoiceOver: its [label], in the phone's words, and what it
/// does.
struct CounterAction: Equatable {
    let label: String
    let does: CounterDoes
}

/// What VoiceOver is told of the counter (plan 4 Task 8): plan 3's `TasbeehSpeech` (its Task 6, the
/// phone's accessibility round 2 rules, its spec section 1), restated, in the feed's words and
/// digits, never the drawn labels ("ROUND 2", "of 100"). The counter is one element: its label is
/// [name], said when it is focused, and its value is [state], said after each count, so a count is
/// a few words. Each takes the words once read (`feed.words` builds them anew on each read: plan 4
/// erratum p4 #12); the forms with a feed read them for a test or a single use.
enum TasbeehSpeech {
    /// The dhikr being said: a built-in by its name in the phone's language (`words.presets`, the
    /// phone's chips' names), his phrase exactly as typed; from round 2 on, with its round
    /// ("SubhanAllah, round 2"), since the state says the round only at 0.
    static func name(_ preset: TasbeehPreset, _ state: TasbeehState, _ feed: WatchFeed) -> String {
        name(preset, state, words: feed.words, feed: feed)
    }

    static func name(_ preset: TasbeehPreset, _ state: TasbeehState, words: WatchScreenWords, feed: WatchFeed) -> String {
        let name = spokenName(preset, TasbeehEngine.currentPart(state, preset), words)
        return state.round >= 2 ? WatchRules.filled(words.nameRoundSpoken, name, digits(feed, state.round)) : name
    }

    /// The count: "13 of 100". At a part's end it names the part the next count begins ("33 of 100.
    /// Next, Alhamdulillah"), at the set's end it says the round is complete ("100 of 100. Round
    /// complete"), and at 0 of a later round it says the completion again, as the round it opens
    /// ("Round complete. 0 of 100, round 2"): the rollover comes a second after the set's end, and
    /// whichever of the two is cut off, the last says it whole. The count is the one shown, held to
    /// the target.
    static func state(_ preset: TasbeehPreset, _ state: TasbeehState, _ feed: WatchFeed) -> String {
        self.state(preset, state, words: feed.words, feed: feed)
    }

    static func state(_ preset: TasbeehPreset, _ state: TasbeehState, words: WatchScreenWords, feed: WatchFeed) -> String {
        let total = preset.total
        let count = min(max(state.count, 0), max(total, 0))
        let counted = WatchRules.filled(words.countSpoken, digits(feed, count), digits(feed, total))
        if total > 0, count >= total { return WatchRules.filled(words.setCompleteSpoken, counted) }
        if TasbeehEngine.partEnds(preset).dropLast().contains(count) {
            return WatchRules.filled(words.nextSpoken, counted, spokenName(preset, TasbeehEngine.currentPart(state, preset), words))
        }
        if count == 0, state.round >= 2 { return WatchRules.filled(words.roundSpoken, counted, digits(feed, state.round)) }
        return counted
    }

    /// The counter's actions beside its count, in plan 3's order: take one away, then choose a
    /// dhikr (plan 4 errata p4 #1 and #6), so a VoiceOver wearer reaches the list.
    static func actions(_ words: WatchScreenWords) -> [CounterAction] {
        [CounterAction(label: words.undoAction, does: .undo), CounterAction(label: words.phrasesAction, does: .phrases)]
    }

    /// A part's dhikr as it is said: his phrase as typed, a built-in by the phone's name for it.
    private static func spokenName(_ preset: TasbeehPreset, _ part: Int, _ words: WatchScreenWords) -> String {
        guard preset.parts.indices.contains(part) else { return "" }
        let dhikr = preset.parts[part].dhikr
        if preset.custom { return dhikr.arabic }
        return words.presets[dhikr.id] ?? dhikr.transliteration ?? dhikr.arabic
    }

    private static func digits(_ feed: WatchFeed, _ n: Int) -> String {
        WatchRules.digits(feed, Int64(n))
    }
}
