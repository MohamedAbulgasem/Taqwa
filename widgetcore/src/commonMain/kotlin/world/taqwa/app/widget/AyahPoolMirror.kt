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

        /** The wire format's version tag, written as the first header field. */
        const val VERSION = 1

        /** Separates entry blocks. Never appears in any field: build it from the escape, not by
         * pasting the raw control character into source. */
        const val ENTRY_SEP = '\u001E'

        /** Separates fields within the header or an entry block. */
        const val FIELD_SEP = '\u001F'

        private const val HEADER_FIELD_COUNT = 4
        private const val ENTRY_FIELD_COUNT = 6

        /**
         * Wire form: a header block (`version`, `languageTag`, `translationId`, `0`/`1` for
         * [AyahPoolMirror.translationRtl]) followed by one block per entry (`surah`, `ayah`,
         * `surahLatin`, `surahArabic`, `arabic`, `translation`), joined with [ENTRY_SEP]; fields
         * within a block are joined with [FIELD_SEP]. About 30 KB for the full fifty-ayah pool.
         */
        fun serialize(m: AyahPoolMirror): String {
            val header = listOf(
                VERSION.toString(),
                m.languageTag,
                m.translationId,
                if (m.translationRtl) "1" else "0",
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
         * fields, a version other than `"1"`, a `0`/`1` field with any other value, an entry
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
