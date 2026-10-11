package world.taqwa.app.adhkar

/** What a page is made of: a supplication the catalogue carries in full, or a Quran passage it only points at. */
enum class AdhkarKind { DUA, QURAN }

/** What is recited before a Quran passage: Hisn prints the istiadhah before Ayat al-Kursi and the basmala before each of the Quls. */
enum class QuranPrefix { ISTIADHAH, BASMALA }

/**
 * Where a Quran item's text lives in the app's own Quran database. The catalogue copies none of
 * it. [ayah] is null for a whole surah (the three Quls).
 */
data class QuranRef(val surah: Int, val ayah: Int?, val prefix: QuranPrefix?)

/** One hadith reference, [collection] as the file spells it (`bukhari`) and [number] as the collection numbers it. */
data class Source(val collection: String, val number: String)

/**
 * One page of the adhkar, as `files/adhkar/items.json` has it. The Quls are one entry in Hisn
 * al-Muslim (76) and three pages here, because each of the three surahs is said three times.
 *
 * @property id `hisn-NNN`, the Hisn al-Muslim number, with a letter where a number is split.
 * @property hisn the number in Hisn al-Muslim; the order the pages are read in.
 * @property sets the sets this item is a page of.
 * @property count how many times it is said.
 * @property countDaily true for the istighfar, said 100 times *a day*: its one counter is shared by
 * both sets and keyed by the date rather than kept per set.
 * @property quran set for [AdhkarKind.QURAN] items.
 * @property arabic `{"both" | "morning" | "evening": text}`; empty for the Quls, whose text is the Quran's.
 * @property transliteration the same shape; empty for the Quran items.
 */
data class AdhkarItem(
    val id: String,
    val hisn: Int,
    val kind: AdhkarKind,
    val sets: Set<AdhkarSet>,
    val count: Int,
    val countDaily: Boolean,
    val quran: QuranRef?,
    val arabic: Map<String, String>,
    val transliteration: Map<String, String>,
    val sources: List<Source>,
) {
    /** The Arabic [set] reads: the set's own wording or the shared one; null if there is neither (see [variantFor]). */
    fun arabicFor(set: AdhkarSet): String? = arabic.variantFor(set)

    /** The transliteration [set] reads, chosen like [arabicFor]. */
    fun transliterationFor(set: AdhkarSet): String? = transliteration.variantFor(set)
}

/**
 * One language's translations (`files/adhkar/text/<language>.json`), kept apart from the items so
 * a language is a file added and not an app rebuilt. [items] is `{id: {"both" | "morning" |
 * "evening": text}}`; the Quran items are not in it, since the reader's own Quran translation is
 * shown for them.
 */
data class AdhkarText(val language: String, val items: Map<String, Map<String, String>>) {
    /** The translation of [id] that [set] reads, chosen like [AdhkarItem.arabicFor]; null if there is none. */
    fun translationFor(id: String, set: AdhkarSet): String? = items[id]?.variantFor(set)
}
