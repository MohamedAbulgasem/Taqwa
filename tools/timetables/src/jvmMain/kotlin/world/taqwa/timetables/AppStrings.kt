package world.taqwa.timetables

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The app's own translations, read from its `composeResources/values/strings.xml` and the
 * per-language twins beside it (`values-ar`, `values-fr`, and so on), so a city
 * page names every prayer, timetable and Asr school in the words the app uses.
 * English is `values`; Indonesian is `values-in` or `values-id` (the app keeps both as twins).
 */
class AppStrings(private val resources: File) {

    private val byLanguage = mutableMapOf<String, Map<String, String>>()

    fun get(language: String, key: String): String =
        table(language)[key] ?: error("The app has no string '$key' in language '$language'")

    /** [key] in [language], or null where the app has no such string yet (a key another task adds). */
    fun getOrNull(language: String, key: String): String? = table(language)[key]

    /** [key] with `%1$s`, `%2$s`... replaced by [args] in order. */
    fun format(language: String, key: String, vararg args: String): String {
        var text = get(language, key)
        args.forEachIndexed { i, arg -> text = text.replace("%${i + 1}\$s", arg) }
        return text
    }

    fun prayer(language: String, prayer: Prayer): String = get(language, "prayer_${prayer.name.lowercase()}")

    /**
     * [entry]'s short name, by its [RegistryEntry.shortNameKey], or null while the app has no
     * string for it yet (the authorities' names arrive with the app's own screens for them).
     */
    fun timetable(language: String, entry: RegistryEntry): String? = table(language)[entry.shortNameKey]

    fun school(language: String, school: AsrSchool): String = get(
        language,
        when (school) {
            AsrSchool.STANDARD -> "madhab_standard"
            AsrSchool.HANAFI -> "madhab_hanafi"
        },
    )

    private val cityNames = mutableMapOf<String, Map<Int, String>>()

    /**
     * The app's own name for city [id] (its GeoNames id in `files/cities.csv`) in [language], as the app's
     * `CityRepository.displayName` gives it: the language's `files/city-names-<lang>.csv` name, else the English one;
     * null where the app has no such city.
     */
    fun cityName(language: String, id: Int): String? {
        // As the app reads them: the list's second column; a translation is the whole rest of its line.
        fun read(file: File, name: (String) -> String): Map<Int, String> = if (!file.isFile) emptyMap() else
            file.readLines().drop(1).filter { it.contains(',') }.mapNotNull { line ->
                line.substringBefore(',').trim().toIntOrNull()?.let { it to name(line.substringAfter(',')).trim() }
            }.filter { it.second.isNotEmpty() }.toMap()
        val english = cityNames.getOrPut("en") { read(File(resources, "files/cities.csv")) { it.substringBefore(',') } }
        val local = if (language == "en") emptyMap() else {
            val file = if (language == "id") "in" else language
            cityNames.getOrPut(language) { read(File(resources, "files/city-names-$language.csv")) { it }.ifEmpty { read(File(resources, "files/city-names-$file.csv")) { it } } }
        }
        return local[id] ?: english[id]
    }

    private fun table(language: String): Map<String, String> = byLanguage.getOrPut(language) {
        val file = candidates(language).map { File(resources, "$it/strings.xml") }.firstOrNull { it.isFile }
            ?: error("The app has no strings for language '$language' under $resources")
        parse(file)
    }

    private fun candidates(language: String): List<String> = when (language) {
        "en" -> listOf("values")
        "id" -> listOf("values-id", "values-in")
        else -> listOf("values-$language")
    }

    private fun parse(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to unescape(element.textContent)
        }
    }

    /** Android resource escapes; XML entities are already decoded by the parser. */
    private fun unescape(text: String): String = text
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\@", "@")
}
