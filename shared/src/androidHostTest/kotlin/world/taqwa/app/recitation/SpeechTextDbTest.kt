package world.taqwa.app.recitation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import world.taqwa.app.quran.QuranRepository
import world.taqwa.app.quran.QuranRepositoryDbTest
import world.taqwa.app.quran.TextKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Read-aloud spec §3 held to every row of the bundled `quran.db`: what the voice would read in
 * all seven texts, all 6,236 ayahs. JVM-only for the same reason as [QuranRepositoryDbTest].
 */
class SpeechTextDbTest {

    private val repo = QuranRepository(
        driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath) },
        io = Dispatchers.Unconfined,
    )

    private val texts = listOf(
        Triple("en.sahih", "en", TextKind.TRANSLATION),
        Triple("fr.hamidullah", "fr", TextKind.TRANSLATION),
        Triple("tr.diyanet", "tr", TextKind.TRANSLATION),
        Triple("id.indonesian", "id", TextKind.TRANSLATION),
        Triple("bn.bengali", "bn", TextKind.TRANSLATION),
        Triple("ur.junagarhi", "ur", TextKind.TRANSLATION),
        Triple("ar.muyassar", "ar", TextKind.TAFSIR),
    )

    private suspend fun prepared(id: String, language: String, kind: TextKind): Map<Int, Map<Int, String>> =
        (1..114).associateWith { surah -> SpeechText.prepare(kind, language, surah, repo.translationTexts(id, surah)) }

    @Test
    fun muyassarKeepsNoQuotation() = runTest {
        prepared("ar.muyassar", "ar", TextKind.TAFSIR).forEach { (surah, spoken) ->
            spoken.forEach { (ayah, text) ->
                assertFalse('{' in text || '}' in text, "$surah:$ayah still has braces")
                Regex("\\(([^()]*)\\)").findAll(text).forEach { match ->
                    val inside = match.groupValues[1]
                    val letters = inside.count { it in 'ء'..'ي' }
                    val marks = inside.count { it in 'ً'..'ْ' || it == 'ٰ' }
                    assertTrue(letters == 0 || marks * 2 < letters, "$surah:$ayah still quotes: $inside")
                }
            }
        }
    }

    @Test
    fun muyassarDropsEveryOpeningLetterQuotation() = runTest {
        val all = prepared("ar.muyassar", "ar", TextKind.TAFSIR)
        listOf(10, 11, 12, 13, 14, 15, 19, 20, 26, 27, 28, 29, 30, 31, 32, 36, 38, 40, 41, 42, 43, 44, 45, 46, 50, 68)
            .forEach { surah ->
                val first = all.getValue(surah).entries.first().value
                assertTrue(first.startsWith("سبق"), "$surah opens with: ${first.take(30)}")
            }
    }

    @Test
    fun fatihaDropsItsQuotationsAndKeepsTheName() = runTest {
        val fatiha = SpeechText.prepare(TextKind.TAFSIR, "ar", 1, repo.translationTexts("ar.muyassar", 1))
        assertTrue(fatiha.getValue(2).startsWith("الثناء على الله"), fatiha.getValue(2).take(40))
        assertFalse("الرَّحْمَنِ" in fatiha.getValue(1))
        assertTrue("(اللهِ)" in fatiha.getValue(1))
    }

    @Test
    fun bengaliHasNoMangledReference() = runTest {
        prepared("bn.bengali", "bn", TextKind.TRANSLATION).forEach { (surah, spoken) ->
            spoken.forEach { (ayah, text) ->
                assertFalse(Regex("\\d{3,4};").containsMatchIn(text), "$surah:$ayah: $text")
            }
        }
    }

    @Test
    fun everyTextFitsAndNothingIsBlank() = runTest {
        texts.forEach { (id, language, kind) ->
            prepared(id, language, kind).forEach { (surah, spoken) ->
                spoken.forEach { (ayah, text) ->
                    assertTrue(text.isNotBlank(), "$id $surah:$ayah blank")
                    assertTrue(text.length <= SpeechText.MAX_CHARS, "$id $surah:$ayah is ${text.length}")
                }
            }
        }
    }

    @Test
    fun diyanetRunsAreReadOnce() = runTest {
        val raw = repo.translationTexts("tr.diyanet", 26)
        val spoken = SpeechText.prepare(TextKind.TRANSLATION, "tr", 26, raw)
        assertTrue(spoken.size < raw.size, "no run grouped: ${spoken.size} of ${raw.size}")
        // Every run is read after its last ayah: the ayah after a key never has the key's text.
        spoken.keys.forEach { n -> raw[n + 1]?.let { next -> assertFalse(next == raw[n], "26:$n is not a run's end") } }
    }

    @Test
    fun saheehReadsItsBracketsAsWords() = runTest {
        val fatiha = SpeechText.prepare(TextKind.TRANSLATION, "en", 1, repo.translationTexts("en.sahih", 1))
        assertEquals("All praise is due to Allah, Lord of the worlds", fatiha.getValue(2))
    }
}
