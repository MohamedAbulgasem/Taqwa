package world.taqwa.app.quran

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tanzil's own files for two bundled translations carry corruption a reader would see under the
 * ayah, which `tools/build-quran-db.py` repairs from a reviewed table. These pin the bundled result,
 * reading every row the way the reader does. JVM-only for the same reason as [QuranRepositoryDbTest].
 */
class TranslationTextDbTest {

    private val repo = QuranRepository(
        driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath) },
        io = Dispatchers.Unconfined,
    )

    private suspend fun rows(translationId: String): List<Pair<String, String>> =
        (1..114).flatMap { surah -> repo.translationTexts(translationId, surah).map { (ayah, text) -> "$surah:$ayah" to text } }

    // A Bengali letter the source's conversion lost survives as the tail of an HTML numeric
    // character reference: a stray character, sometimes '#', three or four ASCII digits and ';'
    // (3:125 read "চিহিߦ#2468; ঘোড়ার" for "চিহ্নিত ঘোড়ার").
    @Test fun bengaliTranslationCarriesNoMangledCharacterReference() = runTest {
        val mangled = Regex("#?[0-9]{3,4};")
        assertEquals(emptyList(), rows("bn.bengali").filter { (_, text) -> mangled.containsMatchIn(text) }.map { it.first })
    }

    // The same corruption where the stray character swallowed a space instead of the reference
    // (20:59 "পূর্বাহেߠলোকজন"). The translation's own text is Bengali script, ASCII punctuation, the
    // danda and curly quotes; every stray character falls outside that.
    @Test fun bengaliTranslationCarriesOnlyItsOwnScriptAndPunctuation() = runTest {
        fun own(c: Char) = c in 'ঀ'..'৿' || c in ' '..'~' || c == '।' || c == '‘' || c == '’'
        val strays = rows("bn.bengali").filter { (_, text) -> !text.all(::own) }
            .map { (ref, text) -> ref + " " + text.filterNot(::own).map { "U+%04X".format(it.code) } }
        assertEquals(emptyList(), strays)
    }

    // OCR slips in Tanzil's Indonesian file: ']' for 'l' (7:192 "berha]a"), '}' for ')' (12:58
    // "(ke Mesir}"), and a footnote's leftover ']' (3:130 "berlipat ganda]"). The translation opens
    // no square bracket or brace of its own, so a closing one without its pair is a slip.
    @Test fun indonesianTranslationClosesNoBracketItNeverOpened() = runTest {
        fun closesUnopened(text: String): Boolean {
            var square = 0
            var curly = 0
            for (c in text) when (c) {
                '[' -> square++
                ']' -> if (square == 0) return true else square--
                '{' -> curly++
                '}' -> if (curly == 0) return true else curly--
            }
            return false
        }
        assertEquals(emptyList(), rows("id.indonesian").filter { (_, text) -> closesUnopened(text) }.map { it.first })
    }

    // 5:73 read "orang0orang" for "orang-orang"; a number in the text stands apart ("12 orang").
    @Test fun indonesianTranslationHasNoDigitInsideAWord() = runTest {
        val glued = Regex("\\p{L}[0-9]+\\p{L}")
        assertEquals(emptyList(), rows("id.indonesian").filter { (_, text) -> glued.containsMatchIn(text) }.map { it.first })
    }
}
