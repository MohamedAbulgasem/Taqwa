package world.taqwa.app.widget

import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.RTL_TRANSLATION_LANGUAGES
import kotlin.random.Random

/**
 * Fills the widget pool mirror (design spec §4) from the Quran database: reads the fifty
 * [AyahPool.REFS] ayahs and the active translation, then writes an [AyahPoolMirror] and — the
 * first time only — a rotation seed. Stays in `shared` rather than `:widgetcore` because it needs
 * [QuranSource]; the mirror it produces is all either widget process ever reads.
 */
object AyahPoolMirrorWriter {
    /** Reads the fifty pool ayahs and the [settings] translation, writes the mirror and, once
     * per install, the seed. Returns the mirror written. */
    suspend fun write(
        store: KeyValueStore,
        quran: QuranSource,
        settings: ReadingSettings,
        languageTag: String,
        newSeed: () -> Long = { Random.nextLong() },
    ): AyahPoolMirror {
        // Several pool ayahs share a surah (e.g. 2, 3, 9, 39, 93 each appear more than once), so
        // each distinct surah is looked up once rather than once per pool entry.
        val surahByNumber = AyahPool.REFS.map { it.first }.distinct().associateWith { quran.surah(it) }
        val translationOff = settings.translationId == ReadingSettings.NO_TRANSLATION
        val entries = AyahPool.REFS.map { (surah, ayah) ->
            val surahInfo = surahByNumber.getValue(surah)
            AyahPoolEntry(
                surah = surah,
                ayah = ayah,
                surahLatin = surahInfo.nameLatin,
                surahArabic = surahInfo.nameArabic,
                arabic = quran.ayahText(surah, ayah).orEmpty(),
                translation = if (translationOff) "" else quran.translationText(settings.translationId, surah, ayah).orEmpty(),
            )
        }
        val mirror = AyahPoolMirror(
            languageTag = languageTag,
            translationId = settings.translationId,
            translationRtl = settings.translationId.substringBefore('.') in RTL_TRANSLATION_LANGUAGES,
            entries = entries,
        )
        AyahPoolMirror.write(store, mirror)
        if (AyahPoolMirror.seed(store) == null) {
            AyahPoolMirror.writeSeed(store, newSeed())
        }
        return mirror
    }
}
