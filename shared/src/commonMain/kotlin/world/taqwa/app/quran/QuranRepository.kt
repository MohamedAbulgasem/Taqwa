package world.taqwa.app.quran

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import world.taqwa.app.quran.db.QuranDatabase

/** Read-only access to the bundled Quran database. Introduced so later tasks can fake it in
 * tests without touching a real SQLite file. */
interface QuranSource {
    suspend fun surahs(): List<Surah>
    suspend fun surah(number: Int): Surah
    suspend fun juzs(): List<Juz>
    suspend fun ayahs(surah: Int): List<Ayah>
    suspend fun translations(): List<TranslationInfo>
    suspend fun translationTexts(translationId: String, surah: Int): Map<Int, String>
    suspend fun pageOf(surah: Int, ayah: Int): Int
    suspend fun page(number: Int): MushafPage
    suspend fun surahOfPage(number: Int): Surah
}

class QuranRepository(
    driverFactory: () -> SqlDriver = ::createQuranDriver,
    private val io: CoroutineDispatcher = ioDispatcher(),
) : QuranSource {
    private val db: QuranDatabase by lazy { QuranDatabase(driverFactory()) }
    private val q get() = db.quranQueries

    override suspend fun surahs(): List<Surah> = withContext(io) { q.surahs().executeAsList().map { it.toSurah() } }
    override suspend fun surah(number: Int): Surah = withContext(io) { q.surahByNumber(number.toLong()).executeAsOne().toSurah() }
    override suspend fun juzs(): List<Juz> = withContext(io) { q.juzs().executeAsList().map { Juz(it.number.toInt(), it.start_surah.toInt(), it.start_ayah.toInt()) } }
    override suspend fun ayahs(surah: Int): List<Ayah> = withContext(io) {
        q.ayahsOfSurah(surah.toLong()).executeAsList().map { Ayah(it.surah.toInt(), it.number.toInt(), it.text_uthmani, it.page.toInt(), it.juz.toInt(), it.hizb_quarter.toInt(), it.sajdah.toInt()) }
    }
    override suspend fun translations(): List<TranslationInfo> = withContext(io) { q.translations().executeAsList().map { it.toInfo() } }
    override suspend fun translationTexts(translationId: String, surah: Int): Map<Int, String> = withContext(io) {
        q.translationOfSurah(translationId, surah.toLong()).executeAsList().associate { it.number.toInt() to it.text }
    }
    override suspend fun pageOf(surah: Int, ayah: Int): Int = withContext(io) { q.pageOfAyah(surah.toLong(), ayah.toLong()).executeAsOne().toInt() }
    override suspend fun page(number: Int): MushafPage = withContext(io) {
        val header = q.pageHeader(number.toLong()).executeAsOne()
        val words = q.pageWords(number.toLong()).executeAsList().groupBy { it.line.toInt() }
        val lines = q.pageLines(number.toLong()).executeAsList().map { l ->
            MushafLine(
                line = l.line.toInt(),
                type = when (l.type) { "surah" -> LineType.SURAH; "basmala" -> LineType.BASMALA; else -> LineType.TEXT },
                surah = l.surah?.toInt(), text = l.text,
                firstSurah = l.first_surah?.toInt(), firstAyah = l.first_ayah?.toInt(),
                lastSurah = l.last_surah?.toInt(), lastAyah = l.last_ayah?.toInt(),
                endsSurah = l.ends_surah == 1L,
                words = words[l.line.toInt()].orEmpty().map { LineWord(it.position.toInt(), it.surah.toInt(), it.ayah.toInt(), it.word.toInt(), it.text) },
            )
        }
        val juz = q.juzOfAyah(header.first_surah, header.first_ayah).executeAsOne().toInt()
        MushafPage(number, header.first_surah.toInt(), header.first_ayah.toInt(), juz, lines)
    }
    override suspend fun surahOfPage(number: Int): Surah = withContext(io) { surah(q.pageHeader(number.toLong()).executeAsOne().first_surah.toInt()) }
}

private fun world.taqwa.app.quran.db.Surah.toSurah() = Surah(
    number = number.toInt(),
    nameArabic = name_ar,
    nameLatin = name_en,
    meaning = meaning_en,
    revelation = if (revelation == "Makki") Revelation.MAKKI else Revelation.MADANI,
    ayahCount = ayah_count.toInt(),
    startPage = start_page.toInt(),
    startJuz = start_juz.toInt(),
)

private fun world.taqwa.app.quran.db.Translation.toInfo() = TranslationInfo(
    id = id,
    language = language,
    name = name,
    translator = translator,
    licence = licence,
    sourceUrl = source_url,
    kind = when (kind) {
        "tafsir" -> TextKind.TAFSIR
        "transliteration" -> TextKind.TRANSLITERATION
        else -> TextKind.TRANSLATION
    },
)

internal expect fun ioDispatcher(): CoroutineDispatcher
