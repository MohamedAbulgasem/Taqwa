package world.taqwa.app.feature.quran

import world.taqwa.app.quran.Ayah
import world.taqwa.app.quran.Juz
import world.taqwa.app.quran.MushafPage
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.Revelation
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.TranslationInfo

/**
 * A hand-built stand-in for [QuranSource], shared by [QuranRootViewModelTest] and
 * [ReaderViewModelTest] rather than duplicated. The default four surahs (1, 2, 18, 114 — matching
 * the real database's numbers, meanings and ayah counts) and juz starts are
 * [QuranRootViewModelTest]'s, which needs no ayah or translation data at all; [ReaderViewModelTest]
 * passes its own surah list plus [ayahsBySurah], [translationsList] and [translationTextsById] so
 * neither test pays for data it does not use. Surah 18's Arabic name carries invented tashkeel
 * deliberately (the real database stores bare consonants) so the harakat-insensitive search test
 * actually exercises [world.taqwa.app.quran.QuranText.normaliseForSearch] rather than trivially
 * matching on already-bare text.
 *
 * [MushafViewModelTest] adds [pagesByNumber] — real [MushafPage] rows dumped out of the bundled
 * database into [MUSHAF_PAGE_1] and friends — and [pageLoads], which counts how often a page was
 * actually fetched so the view model's own page cache can be shown to be doing something.
 */
internal class FakeQuranSource(
    private val surahList: List<Surah> = listOf(
        Surah(1, "الفاتحة", "Al-Fatihah", "The Opening", Revelation.MAKKI, 7, 1, 1, aliases = listOf("Al-Faatiha", "Fatiha")),
        Surah(2, "البقرة", "Al-Baqarah", "The Cow", Revelation.MADANI, 286, 2, 1, aliases = listOf("Al-Baqara", "Baqara")),
        Surah(18, "ٱلۡكَهۡفِ", "Al-Kahf", "The Cave", Revelation.MAKKI, 110, 293, 15),
        Surah(114, "الناس", "An-Nas", "Mankind", Revelation.MAKKI, 6, 604, 30, aliases = listOf("An-Naas")),
    ),
    private val juzList: List<Juz> = listOf(
        Juz(1, 1, 1),
        Juz(2, 2, 142),
        Juz(3, 18, 5),
        Juz(4, 114, 3),
    ),
    private val ayahsBySurah: Map<Int, List<Ayah>> = emptyMap(),
    private val translationsList: List<TranslationInfo> = emptyList(),
    /** id -> surah -> ayah number -> text. */
    private val translationTextsById: Map<String, Map<Int, Map<Int, String>>> = emptyMap(),
    private val pagesByNumber: Map<Int, MushafPage> = emptyMap(),
) : QuranSource {
    /** One entry per [page] call that reached this fake, newest last. */
    val pageLoads = mutableListOf<Int>()

    /** One entry per [translationTexts] call that reached this fake, newest last — lets a test
     * (e.g. [ReaderViewModelTest]'s debounce regression) assert a translation was fetched exactly
     * once rather than re-fetched on every unrelated state change. */
    val translationLoads = mutableListOf<Pair<String, Int>>()

    override suspend fun surahs(): List<Surah> = surahList
    override suspend fun surah(number: Int): Surah = surahList.first { it.number == number }
    override suspend fun juzs(): List<Juz> = juzList
    override suspend fun ayahs(surah: Int): List<Ayah> =
        ayahsBySurah[surah] ?: error("no ayahs configured for surah $surah in FakeQuranSource")
    override suspend fun translations(): List<TranslationInfo> =
        translationsList.ifEmpty { error("no translations configured in FakeQuranSource") }
    override suspend fun translationTexts(translationId: String, surah: Int): Map<Int, String> {
        translationLoads += translationId to surah
        return translationTextsById[translationId]?.get(surah) ?: emptyMap()
    }
    override suspend fun pageOf(surah: Int, ayah: Int): Int =
        ayahsBySurah[surah]?.firstOrNull { it.number == ayah }?.page ?: when (surah) {
            1 -> 1
            2 -> 2
            18 -> 293
            114 -> 604
            else -> error("unknown surah $surah")
        }
    override suspend fun page(number: Int): MushafPage {
        pageLoads += number
        return pagesByNumber[number] ?: error("no page $number configured in FakeQuranSource")
    }
}
