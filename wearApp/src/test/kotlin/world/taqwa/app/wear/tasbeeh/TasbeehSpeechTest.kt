package world.taqwa.app.wear.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.tasbeeh.TasbeehState
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.watch.words
import world.taqwa.app.wear.TestFeeds

/**
 * What a screen reader is told of the counter (plan 3 Task 6), by the phone's accessibility round 2
 * rules (its spec, section 1): the dhikr's name, and from round 2 the round with it; the count as
 * "13 of 100", with the next part's name at a part's end, the round complete at the set's, and the
 * completion again as a later round opens; and the counter's two actions, in the phone's language.
 */
class TasbeehSpeechTest {

    private val english = TestFeeds.sample
    private val arSa = TestFeeds.other("ar-sa")

    private fun ui(preset: TasbeehPreset, count: Int, round: Int = 1) = TasbeehUi.of(preset, TasbeehState(preset.id, count, round))

    private fun digitsOf(feed: WatchFeed): (Int) -> String = { WatchRules.digits(feed, it.toLong()) }

    private fun name(feed: WatchFeed, ui: TasbeehUi) = TasbeehSpeech.name(ui, feed.words(), digitsOf(feed))

    private fun state(feed: WatchFeed, ui: TasbeehUi) = TasbeehSpeech.state(ui, feed.words(), digitsOf(feed))

    private val afterPrayer = TasbeehPresets.default

    @Test
    fun aCountIsSaidAsTheCountAndTheTarget() {
        val at13 = ui(afterPrayer, 13)
        assertEquals("SubhanAllah", name(english, at13))
        assertEquals("13 of 100", state(english, at13))
    }

    @Test
    fun aPartsEndNamesTheNextPart() {
        assertEquals("33 of 100. Next, Alhamdulillah", state(english, ui(afterPrayer, 33)))
        assertEquals("66 of 100. Next, Allahu Akbar", state(english, ui(afterPrayer, 66)))
        assertEquals("Alhamdulillah", name(english, ui(afterPrayer, 33)), "the counter shows the next part from its first count")
    }

    @Test
    fun theSetsEndIsTheRoundComplete() {
        assertEquals("100 of 100. Round complete", state(english, ui(afterPrayer, 100)))
        assertEquals("Allahu Akbar", name(english, ui(afterPrayer, 100)))
    }

    @Test
    fun aLaterRoundOpensWithTheCompletionAndNamesItsRound() {
        val opened = ui(afterPrayer, 0, round = 2)
        assertEquals("Round complete. 0 of 100, round 2", state(english, opened))
        assertEquals("SubhanAllah, round 2", name(english, opened))
        assertEquals("Alhamdulillah, round 3", name(english, ui(afterPrayer, 50, round = 3)), "the name says the round at every count from round 2")
        assertEquals("50 of 100", state(english, ui(afterPrayer, 50, round = 3)), "the state says the round only at 0")
        assertEquals("0 of 100", state(english, ui(afterPrayer, 0)), "round 1 is the start, and is not said")
    }

    @Test
    fun saudiArabicIsSaidInItsWordsAndDigits() {
        val at13 = ui(afterPrayer, 13)
        assertEquals("١٣ من ١٠٠", state(arSa, at13))
        assertEquals("سبحان الله", name(arSa, at13))
        assertEquals("٣٣ من ١٠٠. التالي: الحمد لله", state(arSa, ui(afterPrayer, 33)))
        assertEquals("سبحان الله، الجولة ٢", name(arSa, ui(afterPrayer, 0, round = 2)))
    }

    @Test
    fun aSinglePhraseHasNoPartsEnd() {
        val subhanallah = TasbeehPresets.builtIn.first { it.id == "subhanallah" }
        assertEquals("33 of 100", state(english, ui(subhanallah, 33)))
        assertEquals("SubhanAllah", name(english, ui(subhanallah, 33)))
    }

    @Test
    fun aPhraseOfHisOwnIsNamedAsTyped() {
        val phrase = "Astaghfirullah wa atubu ilayh"
        val own = TasbeehPresets.custom("custom_1730000000001", phrase, 33)
        assertEquals(phrase, name(english, ui(own, 5)))
        assertEquals("5 of 33", state(english, ui(own, 5)))
        assertEquals("$phrase, round 2", name(english, ui(own, 0, round = 2)))
        val percent = TasbeehPresets.custom("custom_2", "100%2\$s sure", 10)
        assertEquals("100%2\$s sure, round 4", name(english, ui(percent, 1, round = 4)), "his words are never filled in turn")
        val underABuiltInsId = TasbeehPresets.custom("subhanallah", "My own words", 10)
        assertEquals("My own words", name(english, ui(underABuiltInsId, 1)), "his words, never a built-in's name for the same id")
        val arabic = TasbeehPresets.custom("custom_1730000000000", "سبحان الله وبحمده، سبحان الله العظيم", 100)
        assertEquals("سبحان الله وبحمده، سبحان الله العظيم", name(arSa, ui(arabic, 7)))
    }

    @Test
    fun theCounterOffersUndoAndThePhrases() {
        assertEquals(
            listOf(CounterAction("Take one away", CounterDoes.UNDO), CounterAction("Choose a dhikr", CounterDoes.PHRASES)),
            TasbeehSpeech.actions(WatchScreenWords.ENGLISH),
        )
        val words = arSa.words()
        val actions = TasbeehSpeech.actions(words)
        assertEquals(listOf(words.undoAction, words.phrasesAction), actions.map { it.label })
        assertEquals(listOf(CounterDoes.UNDO, CounterDoes.PHRASES), actions.map { it.does })
        assertNotEquals("Take one away", actions[0].label)
        assertNotEquals("Choose a dhikr", actions[1].label)
    }
}
