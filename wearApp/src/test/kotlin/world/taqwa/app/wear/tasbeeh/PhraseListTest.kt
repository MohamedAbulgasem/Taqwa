package world.taqwa.app.wear.tasbeeh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchPhrase
import world.taqwa.app.wear.TestFeeds

/**
 * The phrases the watch counts (plan 3 Task 6): the phone's seven built-ins in its order, then his
 * own from the feed in the feed's order, each with its target held to what the phone's store keeps.
 */
class PhraseListTest {

    private fun withPhrases(vararg phrases: WatchPhrase): WatchFeed = TestFeeds.sample.copy(tasbeeh = phrases.toList())

    private val builtInIds = TasbeehPresets.builtIn.map { it.id }

    @Test
    fun builtInsFirstInThePhonesOrder() {
        val list = PhraseList.of(withPhrases())
        assertEquals(builtInIds, list.map { it.id })
        assertEquals("after_prayer", list.first().id)
        assertEquals(TasbeehPresets.builtIn, list)
    }

    @Test
    fun hisPhrasesFollowInTheFeedsOrder() {
        val list = PhraseList.of(TestFeeds.sample)
        assertEquals(builtInIds + listOf("custom_1730000000000", "custom_1730000000001"), list.map { it.id })
        val (first, second) = list.drop(builtInIds.size)
        assertEquals("سبحان الله وبحمده، سبحان الله العظيم", first.parts.single().dhikr.arabic)
        assertEquals(100, first.total)
        assertEquals("Astaghfirullah wa atubu ilayh", second.parts.single().dhikr.arabic)
        assertEquals(33, second.total)
        assertTrue(first.custom && second.custom)
        assertEquals(null, second.parts.single().dhikr.transliteration, "a phrase of his own has no transliteration")
    }

    @Test
    fun targetsAreHeldToOneToAThousand() {
        val list = PhraseList.of(
            withPhrases(
                WatchPhrase("custom_0", "Zero", 0),
                WatchPhrase("custom_1", "One", 1),
                WatchPhrase("custom_1000", "Thousand", 1_000),
                WatchPhrase("custom_5000", "Five thousand", 5_000),
                WatchPhrase("custom_minus", "Below zero", -7),
            ),
        )
        assertEquals(listOf(1, 1, 1_000, 1_000, 1), list.drop(builtInIds.size).map { it.total })
    }

    @Test
    fun aBlankPhraseOrABuiltInsIdIsSkipped() {
        val list = PhraseList.of(
            withPhrases(
                WatchPhrase("custom_blank", "", 33),
                WatchPhrase("custom_spaces", "   ", 33),
                WatchPhrase("subhanallah", "Not the built-in", 33),
                WatchPhrase("after_prayer", "Not the set", 33),
                WatchPhrase("custom_kept", "Kept", 33),
                WatchPhrase("custom_kept", "A second with the same id", 10),
            ),
        )
        assertEquals(builtInIds + "custom_kept", list.map { it.id })
        assertEquals("Kept", list.last().parts.single().dhikr.arabic)
        assertEquals(33, list.last().total)
        assertEquals(TasbeehPresets.builtIn, list.take(builtInIds.size), "the built-ins are the phone's own, untouched")
    }

    @Test
    fun aSixtyCharacterPhraseAndEmojiAreKeptWhole() {
        val sixty = "La hawla wa la quwwata illa billah, al-Aliyy al-Azim, always"
        assertEquals(60, sixty.length)
        val emoji = "🤲 Alhamdulillah ✨"
        val spaced = "  as typed  "
        val list = PhraseList.of(
            withPhrases(WatchPhrase("custom_60", sixty, 100), WatchPhrase("custom_emoji", emoji, 33), WatchPhrase("custom_spaced", spaced, 7)),
        )
        assertEquals(listOf(sixty, emoji, spaced), list.drop(builtInIds.size).map { it.parts.single().dhikr.arabic })
    }

    @Test
    fun eachRowShowsItsNameAndTarget() {
        val feed = TestFeeds.sample
        val rows = phraseRows(feed, PhraseList.of(feed), selected = "after_prayer")
        assertEquals(
            listOf("After prayer", "SubhanAllah", "Alhamdulillah", "Allahu Akbar", "Astaghfirullah", "La ilaha illallah", "SubhanAllahi wa bihamdihi") +
                listOf("سبحان الله وبحمده، سبحان الله العظيم", "Astaghfirullah wa atubu ilayh"),
            rows.map { it.name },
        )
        assertEquals(listOf("33·33·34") + List(6) { "100" } + listOf("100", "33"), rows.map { it.target })
        assertEquals(listOf(true) + List(8) { false }, rows.map { it.selected })
        assertEquals(List(7) { false } + listOf(true, true), rows.map { it.custom })
        assertEquals("After prayer, 100", rows[0].spoken)
        assertEquals("Astaghfirullah wa atubu ilayh, 33", rows.last().spoken)
    }

    @Test
    fun theRowsAreInThePhonesWordsAndDigits() {
        val arSa = TestFeeds.other("ar-sa")
        val rows = phraseRows(arSa, PhraseList.of(arSa), selected = "subhanallah")
        assertEquals("بعد الصلاة", rows[0].name)
        assertEquals("٣٣، ٣٣، ٣٤", rows[0].target)
        assertEquals("١٠٠", rows[1].target)
        assertEquals("سبحان الله, ١٠٠", rows[1].spoken)
        assertEquals(listOf(false, true), rows.take(2).map { it.selected })
    }

    @Test
    fun aSetsPartsAreNeverReadAsNoughts() {
        // An Arabic-Indic or Urdu nought is a dot, so the drawing's middle dot would make the set
        // "٣٣·٣٣·٣٤" read as one long number: those digits are set apart by the Arabic comma.
        val urdu = TestFeeds.sample.copy(ui = TestFeeds.sample.ui.copy(language = "ur", rtl = true, digitZero = '۰'))
        assertEquals("۳۳، ۳۳، ۳۴", phraseRows(urdu, PhraseList.of(urdu), selected = "after_prayer")[0].target)
        val bengali = TestFeeds.sample.copy(ui = TestFeeds.sample.ui.copy(language = "bn", digitZero = '০'))
        assertEquals("৩৩·৩৩·৩৪", phraseRows(bengali, PhraseList.of(bengali), selected = "after_prayer")[0].target, "a Bengali nought is a ring")
        assertEquals("33·33·34", phraseRows(TestFeeds.sample, PhraseList.of(TestFeeds.sample), selected = "after_prayer")[0].target)
    }
}
