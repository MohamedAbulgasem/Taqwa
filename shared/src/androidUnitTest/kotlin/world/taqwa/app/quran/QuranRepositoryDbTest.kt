package world.taqwa.app.quran

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import world.taqwa.app.feature.quran.computeJuzRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises [QuranRepository] against the real bundled `quran.db` (Task 1's pipeline output)
 * rather than a fake, so a schema or data mistake in either the pipeline or the queries above
 * shows up here instead of only at run time on a device.
 *
 * This lives in `androidUnitTest` (the module's JVM unit test source set, run by
 * `:shared:testDebugUnitTest`) because it needs the JDBC SQLite driver, which is JVM-only.
 */
class QuranRepositoryDbTest {

    private val repo = QuranRepository(driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + dbFile().absolutePath) }, io = Dispatchers.Unconfined)

    // The bundled file's own user_version must equal QuranDb.VERSION: both platform drivers open
    // the file at that version with no-op create/upgrade, so if the two ever drifted a stale file
    // would be stamped with the new version and never replaced. The pipeline asserts the pairing
    // only when the database is regenerated; this pins it on every test run.
    @Test fun bundledDatabaseCarriesTheVersionTheDriversOpenAt() {
        val driver = JdbcSqliteDriver("jdbc:sqlite:" + dbFile().absolutePath)
        val version = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            cursor.next()
            app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0))
        }, 0).value
        assertEquals(QuranDb.VERSION.toLong(), version)
    }

    @Test fun hasEverySurahInOrder() = runTest {
        val s = repo.surahs()
        assertEquals(114, s.size)
        assertEquals("Al-Fatihah", s.first().nameLatin)
        assertTrue("Al-Faatiha" in s.first().aliases)
        assertEquals("Al-Mursalat", s[76].nameLatin)
        assertEquals(6, s.last().ayahCount)
    }

    @Test fun alBaqarahHas286AyahsAndKursiIsOnPage42() = runTest {
        val a = repo.ayahs(2)
        assertEquals(286, a.size)
        assertEquals(42, a.first { it.number == 255 }.page)
    }

    @Test fun noSilentAlefSignSurvivesInText() = runTest {
        assertFalse(repo.ayahs(2).any { '۟' in it.text })
    }

    @Test fun eightBundledTextsWithLicences() = runTest {
        val t = repo.translations()
        assertEquals(8, t.size)
        assertTrue(t.all { it.licence.isNotBlank() })
        assertEquals(TextKind.TRANSLITERATION, t.first { it.id == "en.transliteration" }.kind)
    }

    @Test fun saheehFatihaHasSevenLines() = runTest {
        assertEquals(7, repo.translationTexts("en.sahih", 1).size)
    }

    @Test fun pageOneIsFatihaWithHeaderAndSevenTextLines() = runTest {
        val p = repo.page(1)
        assertEquals(LineType.SURAH, p.lines.first().type)
        assertEquals(8, p.lines.size)
        assertEquals(7, p.lines.count { it.type == LineType.TEXT })
        assertEquals(1, p.firstSurah)
        assertEquals(1, p.juz)
    }

    @Test fun pageThreeHasFifteenLinesStartingAtBaqarah6() = runTest {
        val p = repo.page(3)
        assertEquals(15, p.lines.size)
        assertEquals(2 to 6, p.firstSurah to p.firstAyah)
        assertTrue(p.lines.all { it.type != LineType.TEXT || it.words.isNotEmpty() })
    }

    @Test fun lastPageEndsAnNas() = runTest {
        val p = repo.page(604)
        assertTrue(p.lines.last { it.type == LineType.TEXT }.endsSurah)
    }

    @Test fun pageOfMapsBothWays() = runTest {
        assertEquals(2, repo.pageOf(2, 1))
        assertEquals(604, repo.pageOf(114, 6))
    }

    @Test fun everyPageHasLinesAndEveryTextLineHasWords() = runTest {
        for (n in 1..604) {
            val page = repo.page(n)
            assertTrue(page.lines.isNotEmpty(), "page $n has no lines")
            for (line in page.lines) {
                if (line.type == LineType.TEXT) {
                    assertTrue(line.words.isNotEmpty(), "page $n line ${line.line} is TEXT with no words")
                }
            }
        }
    }

    @Test fun everySurahsAyahCountMatchesItsOwnAyahRows() = runTest {
        for (s in 1..114) {
            val surah = repo.surah(s)
            assertEquals(surah.ayahCount, repo.ayahs(s).size, "surah $s ayah count mismatch")
        }
    }

    @Test fun computedJuzRowsCoverAllThirtyJuzsEndingAtTheirKnownBoundaries() = runTest {
        val rows = computeJuzRows(repo.juzs(), repo.surahs())
        assertEquals(30, rows.size)
        val first = rows.first()
        assertEquals(2, first.endSurah.number)
        assertEquals(141, first.endAyah)
        val last = rows.last()
        assertEquals(114, last.endSurah.number)
        assertEquals(6, last.endAyah)
    }

    @Test fun arabicSearchFindsPrefixesAcrossHarakat() = runTest {
        val hits = repo.searchArabic("الرحمن", limit = 100)
        assertTrue(hits.any { it.surah == 1 && it.ayah == 1 }, "1:1 should be a hit")
        assertTrue(hits.any { it.surah == 1 && it.ayah == 3 })
        assertTrue(hits.all { it.translation == null })
        assertEquals(hits, hits.sortedWith(compareBy({ it.surah }, { it.ayah })))
    }

    @Test fun arabicSearchMatchesInsideAWordNotOnlyAtItsStart() = runTest {
        // Substring, not FTS prefix: the definite article in front of the word must not hide it.
        val hits = repo.searchArabic("رحمن", limit = 100)
        assertTrue(hits.any { it.surah == 1 && it.ayah == 1 }, "1:1 should be a hit")
    }

    @Test fun arabicSearchWithTwoTokensNeedsBoth() = runTest {
        val hits = repo.searchArabic("رب العالمين", limit = 100)
        assertTrue(hits.any { it.surah == 1 && it.ayah == 2 })
        assertTrue(hits.none { it.surah == 1 && it.ayah == 1 })
    }

    @Test fun arabicSearchNonsenseFindsNothingAndTheCapHolds() = runTest {
        assertEquals(emptyList(), repo.searchArabic("ذذذذ", limit = 100))
        assertEquals(5, repo.searchArabic("الله", limit = 5).size)
    }

    @Test fun translationSearchIsCaseInsensitiveAndCarriesBothTexts() = runTest {
        val hits = repo.searchTranslation("en.sahih", "MERCIFUL", limit = 100)
        assertTrue(hits.any { it.surah == 1 && it.ayah == 1 })
        val first = hits.first()
        assertTrue(first.translation!!.contains("Merciful"))
        assertTrue(first.arabic.isNotBlank())
    }

    @Test fun translationSearchFoldsNonAsciiCase() = runTest {
        // Turkish "İ" lower-cases to "i̇" in Kotlin; a plain lower-case query must still match.
        val upper = repo.searchTranslation("tr.diyanet", "ALLAH", limit = 100)
        val lower = repo.searchTranslation("tr.diyanet", "allah", limit = 100)
        assertEquals(upper.map { it.surah to it.ayah }, lower.map { it.surah to it.ayah })
        assertTrue(upper.isNotEmpty())
    }

    companion object {
        /**
         * The Gradle test working directory is the module directory (`shared/`), so the resource
         * lives at `src/commonMain/composeResources/files/quran.db` from there. Also tries the
         * project root in case the working directory is ever set differently, and fails with a
         * message naming both candidates rather than a bare "file not found" from JDBC.
         */
        fun dbFile(): File {
            val moduleRelative = File("src/commonMain/composeResources/files/quran.db")
            if (moduleRelative.exists()) return moduleRelative
            val rootRelative = File("shared/src/commonMain/composeResources/files/quran.db")
            if (rootRelative.exists()) return rootRelative
            error(
                "quran.db not found at either ${moduleRelative.absolutePath} or " +
                    "${rootRelative.absolutePath}. Run Task 1's pipeline to generate it.",
            )
        }
    }
}
