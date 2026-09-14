package world.taqwa.app.quran

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.lowercaseIn
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The two search paths the launch sweep found broken, against the real bundled `quran.db`: an
 * Urdu reader's own words reach the Urdu translation, and a Turkish search survives the dotted
 * capital İ. JVM-only for the same reason as [QuranRepositoryDbTest].
 */
class TranslationSearchDbTest {

    private val repo = QuranRepository(
        driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath) },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun anUrduWordFindsTheUrduTranslation() = runTest {
        // ہدایت carries the Urdu-only letter ہ, which the Arabic text never has: only the
        // translation search can answer it.
        assertTrue(SearchQuery.searchesTranslationFirst("ہدایت", "ur"))
        val hits = repo.searchTranslation("ur.junagarhi", "ہدایت", 10)
        assertTrue(hits.isNotEmpty(), "no Urdu hits")
        assertTrue(hits.all { it.translation?.contains("ہدایت") == true }, hits.toString())
    }

    @Test
    fun aTurkishSearchMatchesTheDottedCapital() = runTest {
        val hits = repo.searchTranslation("tr.diyanet", "iman", 400)
        assertTrue(hits.isNotEmpty(), "no Turkish hits")
        // Sentence-initial «İman» is in the Diyanet text; before lowercaseIn it never matched.
        assertTrue(hits.any { it.translation?.contains("İman") == true }, "no sentence-initial İman among ${hits.size} hits")
        assertTrue(hits.all { it.translation?.lowercaseIn(UiLanguage.TURKISH)?.contains("iman") == true })
    }
}
