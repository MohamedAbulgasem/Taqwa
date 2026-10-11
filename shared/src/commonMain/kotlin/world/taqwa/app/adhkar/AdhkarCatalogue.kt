package world.taqwa.app.adhkar

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import world.taqwa.app.resources.Res
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reading the adhkar the build ships: `files/adhkar/items.json` (the items, the Arabic, the
 * transliteration, the counts and sources, built by `tools/adhkar`) and one
 * `files/adhkar/text/<language>.json` per translated language. Both are bundled, so the page works
 * offline and nothing is fetched. A language's file is read only once a reviewer has marked it
 * `"reviewed": true` (see [loadText]).
 *
 * The parsing is separate from the reading ([parse], [parseText]) so tests and the pipeline checks
 * can hand it a string.
 */
object AdhkarCatalogue {
    /** The only items schema this build reads; a file written for another is refused, not guessed at. */
    const val SCHEMA = 1

    /**
     * `ignoreUnknownKeys` as the recitation manifest has it: a field a later build adds must not
     * stop this one reading the rest. The [SCHEMA] number is the deliberate break.
     */
    private val format = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * @throws SerializationException if [json] is not an adhkar file of schema [SCHEMA], or names a
     * kind, set or prefix this build does not know — refused rather than dropped, because a page
     * missing from the set without a word is worse than a build that fails its tests.
     */
    fun parse(json: String): List<AdhkarItem> {
        val file = format.decodeFromString(ItemsFile.serializer(), json)
        if (file.schema != SCHEMA) {
            throw SerializationException("Adhkar items schema ${file.schema} is not the $SCHEMA this build reads")
        }
        return file.items.map { it.toItem() }
    }

    /**
     * Any well-formed translation file, reviewed or not: the content checks read the drafts too.
     * Nothing the reader sees comes through here; the app reads through [loadText], which is where
     * a file that has not been reviewed is turned away.
     *
     * @throws SerializationException if [json] is not a translation file.
     */
    fun parseText(json: String): AdhkarText {
        val file = format.decodeFromString(TextFile.serializer(), json)
        return AdhkarText(file.language, file.items)
    }

    /**
     * Whether [json]'s `reviewed` is `true`. Absent, `false` or `null` all mean not reviewed.
     *
     * @throws SerializationException if [json] is not a translation file.
     */
    internal fun isReviewed(json: String): Boolean =
        format.decodeFromString(TextFile.serializer(), json).reviewed == true

    suspend fun load(): List<AdhkarItem> = load { Res.readBytes(it) }

    /**
     * [language]'s translations, or null when the build has no file for it or its file has not been
     * reviewed — the caller falls back to English, or shows the Arabic alone. A file that is there
     * and does not parse is not "absent" and is thrown (see [parseText]), so a broken translation
     * cannot pass as a missing one.
     *
     * Why a reviewed flag and not a missing file: every file in `files/adhkar/text/` travels in every
     * build, including the drafts waiting for their reviewer, and an unreviewed translation of a
     * du'a must never reach a reader. A draft carries `"reviewed": false` and reads exactly as no
     * file; the reviewer's sign-off is flipping it to `true`, the one switch that ships a language.
     */
    suspend fun loadText(language: String): AdhkarText? = loadText(language) { Res.readBytes(it) }

    /** [load] with the file reader passed in: `Res.readBytes` does not run in the module's tests. */
    internal suspend fun load(read: suspend (path: String) -> ByteArray): List<AdhkarItem> =
        parse(read(ITEMS_PATH).decodeToString())

    /**
     * [loadText] with the file reader passed in. `Res.readBytes` throws for a file that is not
     * bundled, whatever the platform, and the languages the app can be read in outnumber the
     * languages translated here: any failure to *read* is "no file", as `CityRepository` has it.
     */
    internal suspend fun loadText(language: String, read: suspend (path: String) -> ByteArray): AdhkarText? {
        val bytes = try {
            read("files/adhkar/text/$language.json")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (missing: Exception) {
            return null
        }
        val file = format.decodeFromString(TextFile.serializer(), bytes.decodeToString())
        return if (file.reviewed == true) AdhkarText(file.language, file.items) else null
    }

    private const val ITEMS_PATH = "files/adhkar/items.json"
}

/** The pages [set] reads, in the order the book gives them (the order of the file). */
fun List<AdhkarItem>.pagesFor(set: AdhkarSet): List<AdhkarItem> = filter { set in it.sets }

// The wire shapes. Strings rather than enums, so that a value this build does not know is a
// refusal with a message (see toItem) rather than whatever the serializer's enum error says.

@Serializable
private class ItemsFile(val schema: Int, val items: List<ItemDto>)

@Serializable
private class ItemDto(
    val id: String,
    val hisn: Int,
    val kind: String,
    val sets: List<String>,
    // "absent means once": the pipeline writes the count from OpenDua's `times`, which is absent for 1.
    val count: Int = 1,
    val countDaily: Boolean = false,
    // The Quran items only.
    val quran: QuranDto? = null,
    // Empty for the Quls (the text is the Quran's) and for the Quran items' transliteration.
    val arabic: Map<String, String> = emptyMap(),
    val transliteration: Map<String, String> = emptyMap(),
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
private class QuranDto(val surah: Int, val ayah: Int? = null, val prefix: String? = null)

@Serializable
private class SourceDto(val collection: String, val number: String)

@Serializable
private class TextFile(
    val language: String,
    val items: Map<String, Map<String, String>> = emptyMap(),
    // Nullable and absent-means-no: only an explicit `true` ships a language (see loadText).
    val reviewed: Boolean? = null,
)

private fun ItemDto.toItem() = AdhkarItem(
    id = id,
    hisn = hisn,
    kind = when (kind) {
        "dua" -> AdhkarKind.DUA
        "quran" -> AdhkarKind.QURAN
        else -> throw SerializationException("Adhkar item $id has a kind this build does not know: $kind")
    },
    sets = sets.map { name ->
        when (name) {
            "morning" -> AdhkarSet.MORNING
            "evening" -> AdhkarSet.EVENING
            else -> throw SerializationException("Adhkar item $id has a set this build does not know: $name")
        }
    }.toSet(),
    count = count,
    countDaily = countDaily,
    quran = quran?.let { ref ->
        QuranRef(
            surah = ref.surah,
            ayah = ref.ayah,
            prefix = when (ref.prefix) {
                null -> null
                "istiadhah" -> QuranPrefix.ISTIADHAH
                "basmala" -> QuranPrefix.BASMALA
                else -> throw SerializationException("Adhkar item $id has a prefix this build does not know: ${ref.prefix}")
            },
        )
    },
    arabic = arabic,
    transliteration = transliteration,
    sources = sources.map { Source(it.collection, it.number) },
)
