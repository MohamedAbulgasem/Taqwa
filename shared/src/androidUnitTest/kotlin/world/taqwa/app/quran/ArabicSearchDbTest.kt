package world.taqwa.app.quran

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Arabic search and its highlight against the real bundled text (spec §17.4): the letters the
 * fold touches, which a hand-built fixture spells however its author happened to type them, and
 * the whole-Quran guarantee [ArabicWordAlignment] rests on.
 */
class ArabicSearchDbTest {
    private val path = "jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath
    private val repo = QuranRepository(driverFactory = { JdbcSqliteDriver(path) }, io = Dispatchers.Unconfined)

    private fun SearchHit.lit(): List<String> = arabic.split(" ").filterIndexed { i, _ -> i in matchedWords }

    @Test fun aQueryWithAHamzaFindsItsAyah() = runTest {
        val hits = repo.searchArabic("إياك نعبد", limit = 10)
        assertEquals(listOf(1 to 5), hits.map { it.surah to it.ayah })
    }

    @Test fun aQueryEndingInAlefMaksuraFindsItsAyahs() = runTest {
        assertTrue(repo.searchArabic("موسى", limit = 200).size > 100)
        assertTrue(repo.searchArabic("على", limit = 10).isNotEmpty())
    }

    @Test fun theSameWordIsFoundHoweverItsAlefIsTyped() = runTest {
        val typedBare = repo.searchArabic("انزل", limit = 400).map { it.surah to it.ayah }
        val typedWithHamza = repo.searchArabic("أنزل", limit = 400).map { it.surah to it.ayah }
        assertTrue(typedBare.isNotEmpty())
        assertEquals(typedBare, typedWithHamza)
    }

    @Test fun aHitNamesTheUthmaniWordsThatWereFound() = runTest {
        val fatiha2 = repo.searchArabic("رب العالمين", limit = 100).first { it.surah == 1 && it.ayah == 2 }
        assertEquals(2, fatiha2.lit().size)
        val prayer = repo.searchArabic("الصلاة", limit = 100).first { it.surah == 2 && it.ayah == 3 }
        assertEquals(1, prayer.lit().size)
        assertTrue('و' in prayer.lit().single(), "the Uthmani spelling writes this alef as a waw")
    }

    @Test fun everyHitOfACommonWordLightsAtLeastOneWord() = runTest {
        listOf("الله", "الذين", "قال", "يا أيها", "الصلاة", "موسى").forEach { query ->
            val hits = repo.searchArabic(query, limit = 3_000)
            assertTrue(hits.isNotEmpty(), query)
            assertTrue(hits.all { it.matchedWords.isNotEmpty() }, query)
        }
    }

    @Test fun everyAyahsTwoSpellingsWalkToTheEndTogether() {
        val adrift = JdbcSqliteDriver(path).executeQuery(null, "SELECT surah, number, text_uthmani, text_search FROM ayah", { cursor ->
            val found = mutableListOf<String>()
            while (cursor.next().value) {
                if (!ArabicWordAlignment.aligns(cursor.getString(2)!!, cursor.getString(3)!!)) {
                    found += "${cursor.getLong(0)}:${cursor.getLong(1)}"
                }
            }
            QueryResult.Value(found)
        }, 0).value
        assertEquals(emptyList(), adrift)
    }
}
