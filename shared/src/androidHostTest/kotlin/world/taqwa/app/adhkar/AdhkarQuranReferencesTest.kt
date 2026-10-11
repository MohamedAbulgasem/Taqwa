package world.taqwa.app.adhkar

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import world.taqwa.app.quran.QuranRepository
import world.taqwa.app.quran.QuranRepositoryDbTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every Quran item of the bundled adhkar must point at something the bundled `quran.db` has: the
 * catalogue carries no Quranic text of its own (the page reads the verse from the database), so a
 * mistyped surah or ayah would otherwise show up as an empty page on a phone.
 *
 * Lives in `androidHostTest` because it needs the JDBC SQLite driver, and reads both bundled
 * files from the file system as [QuranRepositoryDbTest] and the city-names test do.
 */
class AdhkarQuranReferencesTest {

    private val repo = QuranRepository(
        driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath) },
        io = Dispatchers.Unconfined,
    )

    private fun items(): List<AdhkarItem> {
        val moduleRelative = File("src/commonMain/composeResources/files/adhkar/items.json")
        val file = listOf(moduleRelative, File("shared").resolve(moduleRelative)).firstOrNull { it.exists() }
            ?: error("adhkar/items.json not found at ${moduleRelative.absolutePath}")
        return AdhkarCatalogue.parse(file.readText())
    }

    @Test fun `every quran item resolves to text in the bundled database`() = runTest {
        val quranItems = items().filter { it.kind == AdhkarKind.QURAN }
        assertEquals(listOf("hisn-075", "hisn-076a", "hisn-076b", "hisn-076c"), quranItems.map { it.id })
        for (item in quranItems) {
            val ref = assertNotNull(item.quran, "${item.id} has no quran reference")
            val ayah = ref.ayah
            if (ayah != null) {
                assertTrue(!repo.ayahText(ref.surah, ayah).isNullOrBlank(), "${item.id}: ${ref.surah}:$ayah has no text")
            } else {
                assertTrue(repo.ayahs(ref.surah).isNotEmpty(), "${item.id}: surah ${ref.surah} has no ayahs")
            }
        }
    }

    @Test fun `the three quls are the whole surahs of four five and six ayahs`() = runTest {
        // A wrong number would still resolve, so pin that these are the surahs the Hisn means:
        // Al-Ikhlas, Al-Falaq and An-Nas have four, five and six ayahs.
        val quls = items().filter { it.id in setOf("hisn-076a", "hisn-076b", "hisn-076c") }
        assertEquals(listOf(112, 113, 114), quls.map { it.quran!!.surah })
        assertEquals(listOf(4, 5, 6), quls.map { repo.ayahs(it.quran!!.surah).size })
    }
}
