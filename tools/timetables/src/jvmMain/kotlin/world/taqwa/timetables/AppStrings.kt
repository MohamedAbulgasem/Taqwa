package world.taqwa.timetables

import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The app's own translations, read from its `composeResources/values/strings.xml` and the
 * per-language twins beside it (`values-ar`, `values-fr`, and so on), so a city
 * page names every prayer, method, Asr school and high-latitude rule in the words the app uses.
 * English is `values`; Indonesian is `values-in` or `values-id` (the app keeps both as twins).
 */
class AppStrings(private val resources: File) {

    private val byLanguage = mutableMapOf<String, Map<String, String>>()

    fun get(language: String, key: String): String =
        table(language)[key] ?: error("The app has no string '$key' in language '$language'")

    /** [key] with `%1$s`, `%2$s`... replaced by [args] in order. */
    fun format(language: String, key: String, vararg args: String): String {
        var text = get(language, key)
        args.forEachIndexed { i, arg -> text = text.replace("%${i + 1}\$s", arg) }
        return text
    }

    fun prayer(language: String, prayer: Prayer): String = get(language, "prayer_${prayer.name.lowercase()}")

    fun method(language: String, method: CalculationMethodId): String = get(language, METHOD_KEYS.getValue(method))

    fun madhab(language: String, madhab: AsrMadhab): String = get(
        language,
        when (madhab) {
            AsrMadhab.STANDARD -> "madhab_standard"
            AsrMadhab.HANAFI -> "madhab_hanafi"
        },
    )

    fun highLatitude(language: String, rule: HighLatitudePreference): String = get(
        language,
        when (rule) {
            HighLatitudePreference.AUTOMATIC -> "high_lat_automatic"
            HighLatitudePreference.MIDDLE_OF_NIGHT -> "high_lat_middle"
            HighLatitudePreference.SEVENTH_OF_NIGHT -> "high_lat_seventh"
            HighLatitudePreference.TWILIGHT_ANGLE -> "high_lat_twilight"
        },
    )

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

    private companion object {
        val METHOD_KEYS = mapOf(
            CalculationMethodId.MUSLIM_WORLD_LEAGUE to "method_muslim_world_league",
            CalculationMethodId.ISNA to "method_isna",
            CalculationMethodId.EGYPTIAN to "method_egyptian",
            CalculationMethodId.UMM_AL_QURA to "method_umm_al_qura",
            CalculationMethodId.KARACHI to "method_karachi",
            CalculationMethodId.TEHRAN to "method_tehran",
            CalculationMethodId.DUBAI to "method_dubai",
            CalculationMethodId.KUWAIT to "method_kuwait",
            CalculationMethodId.QATAR to "method_qatar",
            CalculationMethodId.SINGAPORE to "method_singapore",
            CalculationMethodId.TURKEY to "method_turkey",
            CalculationMethodId.MOONSIGHTING_COMMITTEE to "method_moonsighting",
        )
    }
}
