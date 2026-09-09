package world.taqwa.app.widget

/**
 * One pool ayah as mirrored for the widgets: the reference, the surah name in both scripts, the
 * Uthmani Arabic text, and the active translation's text (empty when translations are off).
 */
data class AyahPoolEntry(
    val surah: Int,
    val ayah: Int,
    val surahLatin: String,
    val surahArabic: String,
    val arabic: String,
    val translation: String,
)

/**
 * The serialisable snapshot the app writes into the widget [KeyValueStore] and both widgets
 * (Android Glance, iOS WidgetKit) read (design spec §4). Written under [KEY] whenever the pool
 * text, the UI language, or the translation choice changes; never opens the Quran database
 * itself, so a widget process never pays for that query.
 */
data class AyahPoolMirror(
    val languageTag: String,
    val translationId: String,
    val translationRtl: Boolean,
    /**
     * Whether the app itself renders numbers in Arabic-Indic digits, as its own `PlatformFormat`
     * answered at the moment the mirror was written — not a guess from [languageTag].
     *
     * The tag rule (`WidgetDigits.defaultsToArabicIndic`) is CLDR's, and a device's ICU data is
     * entitled to disagree with it: under an `ar-LY` per-app locale the S23 draws every number in
     * the app in Arabic-Indic digits while CLDR's default for that tag is Western, so the footer's
     * reference and the app behind it showed the same number in two scripts (D2, S23 round).
     * Carrying the answer instead of re-deriving it is what makes the two agree by construction.
     */
    val arabicIndicDigits: Boolean,
    val entries: List<AyahPoolEntry>,
) {
    /** True only when a translation is actually selected *and* at least one entry carries one —
     * guards against a mirror written mid-fetch, where the id is set but the text isn't in yet. */
    val showsTranslation: Boolean
        get() = translationId != "none" && entries.any { it.translation.isNotEmpty() }

    /** The entry to show on [epochDay] for an install fixed to [seed] (design spec §5), or null
     * when the mirror has no entries (nothing has been written yet, or the pool is empty). */
    fun entryFor(epochDay: Long, seed: Long): AyahPoolEntry? =
        entries.takeIf { it.isNotEmpty() }?.let { it[AyahRotation.indexFor(epochDay, seed, it.size)] }

    companion object {
        /** The [KeyValueStore] key the serialised mirror is written under. */
        const val KEY = "ayah_pool"

        /** The [KeyValueStore] key the per-install rotation seed is written under. */
        const val SEED_KEY = "ayah_seed"

        /**
         * The wire format's version tag, written as the first header field.
         *
         * 2 since the header gained [AyahPoolMirror.arabicIndicDigits] (D2). [deserialize]
         * rejects a version-1 mirror outright rather than defaulting the new field: it is the
         * app's digit choice, and there is nothing honest to default it to — an Arabic-UI install
         * would keep contradicting itself until the next write. Both platforms already handle
         * "no mirror" (the Android widget writes one itself; iOS shows the placeholder until the
         * app next runs), which is exactly the state a rejected mirror leaves behind.
         */
        const val VERSION = 2

        /** Separates entry blocks. Never appears in any field: build it from the escape, not by
         * pasting the raw control character into source. */
        const val ENTRY_SEP = '\u001E'

        /** Separates fields within the header or an entry block. */
        const val FIELD_SEP = '\u001F'

        private const val HEADER_FIELD_COUNT = 5
        private const val ENTRY_FIELD_COUNT = 6

        /**
         * Wire form: a header block (`version`, `languageTag`, `translationId`, `0`/`1` for
         * [AyahPoolMirror.translationRtl], `0`/`1` for [AyahPoolMirror.arabicIndicDigits])
         * followed by one block per entry (`surah`, `ayah`,
         * `surahLatin`, `surahArabic`, `arabic`, `translation`), joined with [ENTRY_SEP]; fields
         * within a block are joined with [FIELD_SEP]. About 30 KB for the full fifty-ayah pool.
         */
        fun serialize(m: AyahPoolMirror): String {
            val header = listOf(
                VERSION.toString(),
                m.languageTag,
                m.translationId,
                if (m.translationRtl) "1" else "0",
                if (m.arabicIndicDigits) "1" else "0",
            ).joinToString(FIELD_SEP.toString())
            val entryBlocks = m.entries.map { e ->
                listOf(
                    e.surah.toString(),
                    e.ayah.toString(),
                    e.surahLatin,
                    e.surahArabic,
                    e.arabic,
                    e.translation,
                ).joinToString(FIELD_SEP.toString())
            }
            return (listOf(header) + entryBlocks).joinToString(ENTRY_SEP.toString())
        }

        /**
         * The inverse of [serialize]. Returns null — never throws — on anything that isn't
         * exactly the shape [serialize] produces: a header with other than [HEADER_FIELD_COUNT]
         * fields, a version other than [VERSION] — a mirror from an older build included — a
         * `0`/`1` field with any other value, an entry
         * block with other than [ENTRY_FIELD_COUNT] fields, or a non-integer surah/ayah. A
         * trailing [ENTRY_SEP] (a stray empty final block) is malformed by that rule; a trailing
         * newline inside a field is not — the mirror format (spec §4) allows any character
         * within a field, so it is preserved and round-trips unchanged.
         */
        fun deserialize(raw: String): AyahPoolMirror? {
            if (raw.isEmpty()) return null
            val blocks = raw.split(ENTRY_SEP)
            val header = blocks.first().split(FIELD_SEP)
            if (header.size != HEADER_FIELD_COUNT) return null
            if (header[0] != VERSION.toString()) return null
            val translationRtl = when (header[3]) {
                "0" -> false
                "1" -> true
                else -> return null
            }
            val arabicIndicDigits = when (header[4]) {
                "0" -> false
                "1" -> true
                else -> return null
            }
            val entries = ArrayList<AyahPoolEntry>(blocks.size - 1)
            for (block in blocks.drop(1)) {
                val fields = block.split(FIELD_SEP)
                if (fields.size != ENTRY_FIELD_COUNT) return null
                val surah = fields[0].toIntOrNull() ?: return null
                val ayah = fields[1].toIntOrNull() ?: return null
                entries += AyahPoolEntry(
                    surah = surah,
                    ayah = ayah,
                    surahLatin = fields[2],
                    surahArabic = fields[3],
                    arabic = fields[4],
                    translation = fields[5],
                )
            }
            return AyahPoolMirror(
                languageTag = header[1],
                translationId = header[2],
                translationRtl = translationRtl,
                arabicIndicDigits = arabicIndicDigits,
                entries = entries,
            )
        }

        /** Reads and deserialises the mirror at [KEY], or null when absent or malformed. */
        fun read(store: KeyValueStore): AyahPoolMirror? = store.getString(KEY)?.let(::deserialize)

        /** Serialises [m] and stores it under [KEY] only — the seed is written separately with
         * [writeSeed]. */
        fun write(store: KeyValueStore, m: AyahPoolMirror) {
            store.putString(KEY, serialize(m))
        }

        /** The per-install rotation seed at [SEED_KEY], or null when absent or not a valid
         * `Long`. */
        fun seed(store: KeyValueStore): Long? = store.getString(SEED_KEY)?.toLongOrNull()

        /** Stores [seed] under [SEED_KEY]. Called once, the first time the mirror is written; a
         * seed already present must never be overwritten (that decision is the caller's). */
        fun writeSeed(store: KeyValueStore, seed: Long) {
            store.putString(SEED_KEY, seed.toString())
        }
    }
}
