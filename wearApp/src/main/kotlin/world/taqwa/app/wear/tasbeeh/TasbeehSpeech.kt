package world.taqwa.app.wear.tasbeeh

import world.taqwa.app.tasbeeh.Dhikr
import world.taqwa.app.tasbeeh.TasbeehEngine
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchScreenWords

/** What one of the counter's actions does, beside counting, which is its click. */
internal enum class CounterDoes { UNDO, PHRASES }

/** One of the counter's custom actions for a screen reader: its [label], in the phone's language, and what it [does]. */
internal data class CounterAction(val label: String, val does: CounterDoes)

/**
 * What a screen reader is told of the counter: the phone's accessibility round 2 rules (its spec,
 * section 1), in the feed's words ([WatchScreenWords]) and digits. The counter is one node: its
 * description is [name], said when it is focused, and its state is [state], said after each count,
 * so that a count stays three words.
 */
internal object TasbeehSpeech {

    /**
     * The dhikr being said: a built-in by its name in the phone's language (`words.presets`, the
     * phone's chips' words), a phrase of his own exactly as typed; from round 2 on, with its round
     * ("SubhanAllah, round 2"), since the state names the round only at 0. Round 1 is the start,
     * and is not said.
     */
    fun name(ui: TasbeehUi, words: WatchScreenWords, digits: (Int) -> String): String {
        val name = spokenName(ui, ui.preset.parts.getOrNull(ui.currentPart)?.dhikr, words)
        return if (ui.state.round >= 2) WatchRules.filled(words.nameRoundSpoken, name, digits(ui.state.round)) else name
    }

    /**
     * The count: "13 of 100". At a part's end it names the part the next count begins ("33 of 100.
     * Next, Alhamdulillah"), at the set's end it says the round is complete ("100 of 100. Round
     * complete"), and at 0 of a later round it says the completion again, as the round it opens
     * ("Round complete. 0 of 100, round 2"): the hold's rollover comes a second after the set's end,
     * and whichever of the two sentences is cut off, the last one says it whole.
     */
    fun state(ui: TasbeehUi, words: WatchScreenWords, digits: (Int) -> String): String {
        val total = ui.preset.total
        val count = ui.count
        val counted = WatchRules.filled(words.countSpoken, digits(count), digits(total))
        return when {
            total > 0 && count >= total -> WatchRules.filled(words.setCompleteSpoken, counted)
            count in TasbeehEngine.partEnds(ui.preset).dropLast(1) ->
                WatchRules.filled(words.nextSpoken, counted, spokenName(ui, ui.preset.parts.getOrNull(ui.currentPart)?.dhikr, words))
            count == 0 && ui.state.round >= 2 -> WatchRules.filled(words.roundSpoken, counted, digits(ui.state.round))
            else -> counted
        }
    }

    /** The counter's actions beside its click, in order: take one away, then choose a dhikr. */
    fun actions(words: WatchScreenWords): List<CounterAction> =
        listOf(CounterAction(words.undoAction, CounterDoes.UNDO), CounterAction(words.phrasesAction, CounterDoes.PHRASES))

    private fun spokenName(ui: TasbeehUi, dhikr: Dhikr?, words: WatchScreenWords): String = when {
        dhikr == null -> ""
        ui.preset.custom -> dhikr.arabic
        else -> words.presets[dhikr.id] ?: dhikr.transliteration ?: dhikr.arabic
    }
}
