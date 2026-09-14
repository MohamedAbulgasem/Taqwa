package world.taqwa.app.widget

import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.RTL_TRANSLATION_LANGUAGES
import kotlin.random.Random

/**
 * Fills the widget pool mirror (design spec §4) from the Quran database: reads the hundred
 * [AyahPool.REFS] ayahs and the active translation, then writes an [AyahPoolMirror] and — the
 * first time only — a rotation seed. Stays in `shared` rather than `:widgetcore` because it needs
 * [QuranSource]; the mirror it produces is all either widget process ever reads.
 */
object AyahPoolMirrorWriter {
    /**
     * Reads the hundred pool ayahs and the [settings] translation, writes the mirror and, once per
     * install, the seed. Returns the mirror written.
     *
     * [format] is asked one question — what a `1` looks like — and the answer is recorded in the
     * mirror as [AyahPoolMirror.arabicIndicDigits], so the widget footer's reference uses the
     * digits the app itself is drawing rather than the ones CLDR's default for [languageTag]
     * would suggest (D2, S23 round). It is a parameter rather than a `createPlatformFormat()`
     * call inside so this stays a plain, testable function with no platform of its own.
     */
    suspend fun write(
        store: KeyValueStore,
        quran: QuranSource,
        settings: ReadingSettings,
        languageTag: String,
        format: PlatformFormat,
        newSeed: () -> Long = { Random.nextLong() },
    ): AyahPoolMirror {
        // Several pool ayahs share a surah (e.g. 2, 3, 7, 9, 17 each appear more than once), so
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
            arabicIndicDigits = format.usesNativeDigits(),
            entries = entries,
        )
        // Seed first, mirror second, and never the other way round. Both widget processes read
        // the two keys independently, so a draw that lands between the two writes sees whatever is
        // already there: with the mirror first, a first-ever write leaves a real pool next to a
        // missing seed and the card rotates on the fallback seed 0 — a different ayah from the one
        // every later draw shows. With the seed first, that same window shows *no* mirror, which
        // both widgets already handle (Android writes it, iOS shows the placeholder) and which the
        // very next draw corrects.
        if (AyahPoolMirror.seed(store) == null) {
            AyahPoolMirror.writeSeed(store, newSeed())
        }
        AyahPoolMirror.write(store, mirror)
        return mirror
    }
}
