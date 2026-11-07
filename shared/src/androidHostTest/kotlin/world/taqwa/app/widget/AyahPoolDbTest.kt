package world.taqwa.app.widget

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
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

/** A device whose own formatter renders Arabic-Indic digits, whatever its tag says — the S23 under
 * an `ar-LY` per-app locale (D2). */
private class ArabicIndicFormat(private val tag: String) : PlatformFormat {
    override fun languageTag() = tag
    override fun localizedDigits(number: Int) =
        number.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
    override fun clockTime(hour: Int, minute: Int) = "$hour:$minute"
}

/**
 * Guards [AyahPool.REFS] against the real bundled `quran.db` (design spec §3): every reference
 * must exist, and every ayah must fit what a 4×3 widget cell can draw with the Arabic at ≥ 17 sp
 * — at most 24 Arabic words and 245 characters of Saheeh International. Lives in
 * `androidHostTest` for the same reason [QuranRepositoryDbTest] does: it needs the JDBC SQLite
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

    // The sample card the Widgets screen and the iOS widget gallery show before a mirror exists is
    // typed into the source, so it is held to the database: 13:28 and, after it, 13:29, byte for
    // byte, the silent-letter sign included.
    @Test fun theSampleCardsCarryTheDatabasesOwnText() = runTest {
        val ayah = repo.ayahText(13, 28)!!
        val next = repo.ayahText(13, 29)!!
        val kotlin = source("src/commonMain/kotlin/world/taqwa/app/feature/settings/AyahWidgetPreview.kt")
        assertTrue("arabic = \"$ayah\"," in kotlin, "AyahWidgetPreview's sample is not 13:28 as stored")
        assertTrue("followedBy = \"$next\"," in kotlin, "AyahWidgetPreview's sample is not followed by 13:29")
        val swift = source("../iosApp/TaqwaWidget/TaqwaAyahWidget.swift")
        assertTrue("arabic: \"$ayah\"," in swift, "the iOS widget's sample is not 13:28 as stored")
        assertTrue("followedBy: \"$next\"" in swift, "the iOS widget's sample is not followed by 13:29")
    }

    private fun source(path: String): String =
        (java.io.File(path).takeIf { it.exists() } ?: java.io.File("shared/$path")).readText()
}

/**
 * [AyahPoolMirrorWriter] against the real database: the mirror it writes must actually carry the
 * hundred pool ayahs and the requested translation, and the once-only seed rule must hold.
 */
class AyahPoolMirrorWriterTest {

    private val repo = realRepository()

    @Test fun writesEveryPoolEntryInOrderWithTheRequestedTranslation() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "en.sahih")

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "en-US", EnglishPlatformFormat)

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

    // What is read after each pool ayah, which the widgets need to draw its last tanwin as the
    // Reader does (HafsSpelling R4): the next ayah, or the next basmala's ب at a surah's end.
    @Test fun everyEntryCarriesWhatIsReadAfterItsAyah() = runTest {
        val mirror = AyahPoolMirrorWriter.write(FakePoolKeyValueStore(), repo, ReadingSettings(translationId = "en.sahih"), "en-US", EnglishPlatformFormat)
        mirror.entries.forEach { entry ->
            val expected = repo.ayahs(entry.surah).single { it.number == entry.ayah }.followedBy.orEmpty()
            assertEquals(expected, entry.followedBy, "${entry.surah}:${entry.ayah}")
            assertTrue(entry.followedBy.isNotEmpty())
        }
        assertEquals("\u0628", mirror.entries.single { it.surah == 9 && it.ayah == 129 }.followedBy, "9:129 ends At-Tawbah")
    }

    @Test fun translationIsEmptyWhenTranslationsAreOff() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = ReadingSettings.NO_TRANSLATION)

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "en-US", EnglishPlatformFormat)

        assertTrue(mirror.entries.all { it.translation.isEmpty() })
        assertTrue(mirror.entries.all { it.arabic.isNotEmpty() })
    }

    @Test fun translationRtlIsTrueForAnRtlTranslation() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "ur.junagarhi")

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "ur", EnglishPlatformFormat)

        assertTrue(mirror.translationRtl)
    }

    @Test fun translationRtlIsFalseForALtrTranslation() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "en.sahih")

        val mirror = AyahPoolMirrorWriter.write(store, repo, settings, "en-US", EnglishPlatformFormat)

        assertEquals(false, mirror.translationRtl)
    }

    @Test fun seedIsWrittenOnlyOnce() = runTest {
        val store = FakePoolKeyValueStore()
        val settings = ReadingSettings(translationId = "en.sahih")

        AyahPoolMirrorWriter.write(store, repo, settings, "en-US", EnglishPlatformFormat, newSeed = { 111L })
        val firstSeed = AyahPoolMirror.seed(store)
        assertEquals(111L, firstSeed)

        AyahPoolMirrorWriter.write(store, repo, settings, "en-US", EnglishPlatformFormat, newSeed = { 222L })
        assertEquals(firstSeed, AyahPoolMirror.seed(store))
    }

    @Test fun theDigitChoiceComesFromTheFormatNotTheLanguageTag() = runTest {
        val settings = ReadingSettings(translationId = "en.sahih")

        // `ar-LY` defaults to Western digits under CLDR, but this device's formatter draws
        // Arabic-Indic ones — the mirror has to record what the *app* draws (D2).
        val arabicIndic = AyahPoolMirrorWriter.write(
            FakePoolKeyValueStore(), repo, settings, "ar-LY", ArabicIndicFormat("ar-LY"),
        )
        assertTrue(arabicIndic.arabicIndicDigits)
        assertEquals(false, WidgetDigits.defaultsToArabicIndic("ar-LY"))

        val western = AyahPoolMirrorWriter.write(
            FakePoolKeyValueStore(), repo, settings, "en-US", EnglishPlatformFormat,
        )
        assertEquals(false, western.arabicIndicDigits)
    }

    @Test fun noSeedIsWrittenBeforeTheFirstWrite() {
        assertNull(AyahPoolMirror.seed(FakePoolKeyValueStore()))
    }
}
