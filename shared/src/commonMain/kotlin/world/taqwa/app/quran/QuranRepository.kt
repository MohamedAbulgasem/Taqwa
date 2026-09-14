package world.taqwa.app.quran

import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.lowercaseIn
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
    /** One ayah's Uthmani Arabic, or null when the reference does not exist. Used by the ayah
     * widget pool mirror writer, which needs a hundred scattered ayahs rather than whole surahs. */
    suspend fun ayahText(surah: Int, ayah: Int): String?
    /** One ayah's text in [translationId], or null when the reference or the translation does
     * not exist. */
    suspend fun translationText(translationId: String, surah: Int, ayah: Int): String?
    suspend fun pageOf(surah: Int, ayah: Int): Int
    suspend fun page(number: Int): MushafPage
    /** Arabic search; [query] is raw user text, folded into tokens by
     * [SearchQuery.arabicTokens] and matched as substrings of the normalised ayah text. */
    suspend fun searchArabic(query: String, limit: Int): List<SearchHit>
    /** Case-insensitive substring search over one translation, folded in Kotlin so non-ASCII
     * case (Turkish, French) folds correctly, which SQLite's LIKE and lower() cannot do. */
    suspend fun searchTranslation(translationId: String, query: String, limit: Int): List<SearchHit>
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
    override suspend fun ayahText(surah: Int, ayah: Int): String? = withContext(io) {
        q.ayahByRef(surah.toLong(), ayah.toLong()).executeAsOneOrNull()?.text_uthmani
    }
    override suspend fun translationText(translationId: String, surah: Int, ayah: Int): String? = withContext(io) {
        q.translationByRef(translationId, surah.toLong(), ayah.toLong()).executeAsOneOrNull()
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

    // Not FTS5: Android's framework SQLite is built without that module, so `ayah_fts MATCH`
    // crashed with "no such module: fts5" on every phone even though it worked on desktop and
    // iOS SQLite. Bundling a SQLite build with FTS5 would add megabytes to the APK, so the
    // pre-normalised ayah.text_search column is scanned in Kotlin instead -- 6,236 short rows,
    // a few milliseconds, the same shape as searchTranslation. Matching is plain `contains`,
    // which is deliberately broader than FTS prefix terms: «رحمن» also finds «الرحمن».
    override suspend fun searchArabic(query: String, limit: Int): List<SearchHit> = withContext(io) {
        val tokens = SearchQuery.arabicTokens(query)
        if (tokens.isEmpty()) return@withContext emptyList()
        q.ayahSearchRows().executeAsList()
            .asSequence()
            .filter { row -> tokens.all { row.text_search.contains(it) } }
            .take(limit)
            .map { SearchHit(it.surah.toInt(), it.number.toInt(), it.text_uthmani, translation = null) }
            .toList()
    }

    // The whole translation is read once per search (6,236 short rows, a few milliseconds) and
    // the ayah rows for the hits are fetched by surah, so a phrase found in many surahs costs one
    // query per surah touched, not one per hit.
    override suspend fun searchTranslation(translationId: String, query: String, limit: Int): List<SearchHit> = withContext(io) {
        // Case folds in the translation's own language: the Diyanet text capitalises İman and
        // Işık, which Kotlin's locale-free lowercase() would never match against "iman".
        val language = UiLanguage.of(translationId.substringBefore('.'))
        val needle = query.trim().lowercaseIn(language)
        if (needle.isEmpty()) return@withContext emptyList()
        val hits = q.translationTextsAll(translationId).executeAsList()
            .asSequence()
            .filter { it.text.lowercaseIn(language).contains(needle) }
            .take(limit)
            .toList()
        val arabicBySurah = hits.map { it.surah.toInt() }.distinct().associateWith { surah ->
            q.ayahsOfSurah(surah.toLong()).executeAsList().associate { it.number.toInt() to it.text_uthmani }
        }
        hits.map { SearchHit(it.surah.toInt(), it.number.toInt(), arabicBySurah.getValue(it.surah.toInt()).getValue(it.number.toInt()), it.text) }
    }
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
    aliases = aliases_en.split('|').filter { it.isNotBlank() },
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
