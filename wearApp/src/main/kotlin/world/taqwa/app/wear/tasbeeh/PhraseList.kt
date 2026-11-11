package world.taqwa.app.wear.tasbeeh

import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.words

/** The phrases the watch counts, in the order the phrase list shows them. */
internal object PhraseList {

    /**
     * The phone's seven built-ins in its order ([TasbeehPresets.builtIn], the watch's own copy of the
     * phone's code), then his own from [feed] in the feed's order, each as typed, counting to its
     * target held to 1..[TasbeehPresets.MAX_TARGET], the bound the phone's store keeps. A blank
     * phrase is left out, and so is an id that is a built-in's or one already listed: the counts
     * are kept by id, so two phrases may never share one.
     */
    fun of(feed: WatchFeed): List<TasbeehPreset> {
        val list = TasbeehPresets.builtIn.toMutableList()
        val ids = list.mapTo(HashSet()) { it.id }
        for (phrase in feed.tasbeeh) {
            if (phrase.phrase.isBlank() || !ids.add(phrase.id)) continue
            list += TasbeehPresets.custom(phrase.id, phrase.phrase, phrase.target.coerceIn(1, TasbeehPresets.MAX_TARGET))
        }
        return list
    }
}

/**
 * One row of the phrase list (`gTasbeehList`): the phrase's [name] in the phone's words (a built-in's
 * from `words.presets`, his own as typed), its [target] as drawn ("33·33·34" for the set, else its
 * total, in the feed's digits, [separatorFor]), what a screen reader is told ([spoken]: the name, then ", " and the
 * total), whether it is the one being counted, and whether it is his own, which is drawn in the
 * system font as the phone draws his phrases.
 */
internal data class PhraseRow(
    val id: String,
    val name: String,
    val target: String,
    val spoken: String,
    val selected: Boolean,
    val custom: Boolean,
)

/** The rows for [phrases] ([PhraseList.of]), with [selected] the id being counted. */
internal fun phraseRows(feed: WatchFeed, phrases: List<TasbeehPreset>, selected: String): List<PhraseRow> {
    val words = feed.words()
    fun digits(n: Int) = WatchRules.digits(feed, n.toLong())
    return phrases.map { preset ->
        val name = if (preset.custom) preset.parts.single().dhikr.arabic else words.presets[preset.id] ?: preset.id
        PhraseRow(
            id = preset.id,
            name = name,
            target = preset.parts.joinToString(separatorFor(feed.ui.digitZero)) { digits(it.count) },
            spoken = "$name, ${digits(preset.total)}",
            selected = preset.id == selected,
            custom = preset.custom,
        )
    }
}

/**
 * What sets a set's parts apart: the middle dot, as `gTasbeehList` writes "33·33·34", except in
 * digits whose nought is itself a dot (Arabic-Indic ٠, extended Arabic-Indic ۰), where "٣٣·٣٣·٣٤"
 * would read as one long number. Those take the Arabic comma, as a list of numbers is written in
 * Arabic and Urdu: "٣٣، ٣٣، ٣٤".
 */
private fun separatorFor(digitZero: Char): String = if (digitZero == '٠' || digitZero == '۰') "، " else "·"
