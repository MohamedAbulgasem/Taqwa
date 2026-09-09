package world.taqwa.app.widget

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import world.taqwa.app.quran.QuranRepository
import world.taqwa.app.quran.QuranRepositoryDbTest
import world.taqwa.app.quran.ReadingSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun realRepository() = QuranRepository(
    driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath) },
    io = Dispatchers.Unconfined,
)

private class FakePoolKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String): String? = map[key]
}

/**
 * Guards [AyahPool.REFS] against the real bundled `quran.db` (design spec §3): every reference
 * must exist, and every ayah must fit what a 4×3 widget cell can draw with the Arabic at ≥ 17 sp
 * — at most 24 Arabic words and 245 characters of Saheeh International. Lives in
 * `androidUnitTest` for the same reason [QuranRepositoryDbTest] does: it needs the JDBC SQLite
 * driver, which is JVM-only.
 */
class AyahPoolDbTest {

    private val repo = realRepository()

    @Test fun everyPoolReferenceExistsInTheDatabase() = runTest {
        for ((surah, ayah) in AyahPool.REFS) {
            assertNotNull(repo.ayahText(surah, ayah), "surah $surah ayah $ayah is missing from the database")
        }
    }

    @Test fun everyPoolAyahHoldsToTheArabicWordBudget() = runTest {
        for ((surah, ayah) in AyahPool.REFS) {
            val text = repo.ayahText(surah, ayah)!!
            val words = text.trim().split(' ').filter { it.isNotBlank() }.size
            assertTrue(words <= 24, "surah $surah ayah $ayah has $words Arabic words, over the budget of 24")
        }
    }

    @Test fun everyPoolAyahHoldsToTheSaheehCharacterBudget() = runTest {
        for ((surah, ayah) in AyahPool.REFS) {
            val translation = repo.translationText("en.sahih", surah, ayah)
            assertNotNull(translation, "surah $surah ayah $ayah has no en.sahih translation")
            assertTrue(
                translation.length <= 245,
                "surah $surah ayah $ayah's en.sahih translation is ${translation.length} characters, over the budget of 245",
            )
        }
    }

    @Test fun thePoolHasNoDuplicateReferences() {
        assertEquals(AyahPool.REFS.size, AyahPool.REFS.distinct().size)
    }
}

/**
 * [AyahPoolMirrorWriter] against the real database: the mirror it writes must actually carry the
 * fifty pool ayahs and the requested translation, and the once-only seed rule must hold.
 */
class AyahPoolMirrorWriterTest {

    private val repo = realRepository()

    @Test fun writesFiftyEntriesInPoolOrderWithTheRequestedTranslation() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "en.sahih")

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "en-US")

        assertEquals(AyahPool.REFS.size, mirror.entries.size)
        AyahPool.REFS.forEachIndexed { index, (surah, ayah) ->
            val entry = mirror.entries[index]
            assertEquals(surah, entry.surah)
            assertEquals(ayah, entry.ayah)
            assertTrue(entry.arabic.isNotEmpty())
            assertTrue(entry.translation.isNotEmpty())
        }
        assertEquals(mirror, AyahPoolMirror.read(store))
    }

    @Test fun translationIsEmptyWhenTranslationsAreOff() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = ReadingSettings.NO_TRANSLATION)

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "en-US")

        assertTrue(mirror.entries.all { it.translation.isEmpty() })
        assertTrue(mirror.entries.all { it.arabic.isNotEmpty() })
    }

    @Test fun translationRtlIsTrueForAnRtlTranslation() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "ur.junagarhi")

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "ur")

        assertTrue(mirror.translationRtl)
    }

    @Test fun translationRtlIsFalseForALtrTranslation() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "en.sahih")

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "en-US")

        assertEquals(false, mirror.translationRtl)
    }

    @Test fun seedIsWrittenOnlyOnce() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "en.sahih")

        AyahPoolMirrorWriter.write(store, repo, settings, "en-US", newSeed = { 111L })
        val firstSeed = AyahPoolMirror.seed(store)
        assertEquals(111L, firstSeed)

        AyahPoolMirrorWriter.write(store, repo, settings, "en-US", newSeed = { 222L })
        assertEquals(firstSeed, AyahPoolMirror.seed(store))
    }

    @Test fun noSeedIsWrittenBeforeTheFirstWrite() {
        assertNull(AyahPoolMirror.seed(FakePoolKeyValueStore()))
    }
}
