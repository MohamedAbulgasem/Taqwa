package world.taqwa.timetables

import world.taqwa.app.domain.AsrMadhab
import java.io.File

/** The seven languages the site and the app are written in. */
val SITE_LANGUAGES = listOf("en", "ar", "fr", "tr", "id", "ur", "bn")

/**
 * One city of the curated list, resolved against the app's own city data: the coordinates, the
 * time zone and the country always come from the app's `cities.csv`, and the names from its
 * `city-names-<lang>.csv`, unless the list overrides a name it knows to be wrong.
 */
data class City(
    val slug: String,
    val id: Int,
    val countryCode: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    /** English first, then the others in the list's order. */
    val languages: List<String>,
    val madhab: AsrMadhab,
    /** The languages whose home page lists this city. */
    val featured: Set<String>,
    private val names: Map<String, String>,
) {
    fun name(language: String): String = names.getValue(language)
}

class CatalogError(problems: List<String>) :
    IllegalStateException("The city list has ${problems.size} problem(s):\n" + problems.joinToString("\n") { "  - $it" })

/**
 * Reads `site/cities.tsv`, one city per line, tab-separated:
 *
 *     slug  id  languages  madhab  featured  names
 *
 * `languages` and `featured` are space-separated language codes; `madhab` is blank (the country's
 * norm), `STANDARD` or `HANAFI`; `names` is `lang=Name` pairs separated by `;`, used only to
 * correct a name the app's data has wrong. Lines starting with `#` are comments.
 *
 * Every problem in the file is collected and reported together, so one run shows them all.
 */
object Catalog {

    /** Where the Hanafi school is the norm, so Asr is Hanafi unless the list says otherwise. */
    private val HANAFI_BY_NORM = setOf("PK", "IN", "BD", "AF", "UZ")

    private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    fun load(listFile: File, appFiles: File): List<City> {
        val cities = readCsv(File(appFiles, "cities.csv")).associateBy { it.getValue("id").toInt() }
        val localNames = SITE_LANGUAGES.filter { it != "en" }.associateWith { language ->
            val file = File(appFiles, "city-names-$language.csv")
            if (file.isFile) readCsv(file).associate { it.getValue("id").toInt() to it.getValue("name") } else emptyMap()
        }

        val problems = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<City>()

        listFile.readLines().forEachIndexed { index, raw ->
            val line = raw.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
            val where = "line ${index + 1}"
            val cells = line.split('\t').map { it.trim() } + List(6) { "" }
            val (slug, idText, languagesText, madhabText, featuredText, namesText) = cells

            if (!SLUG.matches(slug)) problems += "$where: slug '$slug' must be lowercase letters, digits and hyphens"
            if (!seen.add(slug)) problems += "$where: duplicate slug $slug"

            val id = idText.toIntOrNull()
            val row = id?.let(cities::get)
            if (row == null) {
                problems += "$where: unknown city id $idText"
                return@forEachIndexed
            }

            val languages = languagesText.split(' ').filter { it.isNotBlank() }
            languages.filter { it !in SITE_LANGUAGES }.forEach { problems += "$where: unknown language $it" }
            if ("en" !in languages) problems += "$where: $slug has no English page"
            val featured = featuredText.split(' ').filter { it.isNotBlank() }.toSet()
            featured.filter { it !in languages }.forEach { problems += "$where: $slug is featured in $it but has no $it page" }

            val madhab = when (madhabText.uppercase()) {
                "" -> if (row.getValue("countryCode") in HANAFI_BY_NORM) AsrMadhab.HANAFI else AsrMadhab.STANDARD
                "STANDARD" -> AsrMadhab.STANDARD
                "HANAFI" -> AsrMadhab.HANAFI
                else -> {
                    problems += "$where: unknown madhab $madhabText"
                    AsrMadhab.STANDARD
                }
            }

            val overrides = namesText.split(';').filter { it.contains('=') }.associate {
                it.substringBefore('=').trim() to it.substringAfter('=').trim()
            }
            val names = mutableMapOf<String, String>()
            for (language in languages.filter { it in SITE_LANGUAGES }) {
                val name = overrides[language]
                    ?: if (language == "en") row.getValue("name") else localNames.getValue(language)[id]
                if (name.isNullOrBlank()) problems += "$where: $slug has no $language name; add $language=… to its names"
                else names[language] = name
            }

            val country = row.getValue("countryCode")
            val region = Regions.of(country)
            if (region == null) problems += "$where: no region for country $country; add it to Regions"

            result += City(
                slug = slug,
                id = id,
                countryCode = country,
                region = region ?: "",
                latitude = row.getValue("lat").toDouble(),
                longitude = row.getValue("lon").toDouble(),
                timeZone = row.getValue("tz"),
                languages = listOf("en") + languages.filter { it != "en" && it in SITE_LANGUAGES },
                madhab = madhab,
                featured = featured,
                names = names,
            )
        }
        if (problems.isNotEmpty()) throw CatalogError(problems)
        return result
    }

    private operator fun <T> List<T>.component6(): T = this[5]

    /** The app's files are plain comma-separated with a header row and no quoting. */
    private fun readCsv(file: File): List<Map<String, String>> {
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = lines.first().split(',')
        return lines.drop(1).map { line -> header.zip(line.split(',')).toMap() }
    }
}

/** How the city index groups countries. The keys are the site's; their names live in each
 * language's `meta.json`. */
object Regions {
    val ORDER = listOf(
        "middle-east", "north-africa", "turkiye-central-asia", "south-asia", "southeast-asia",
        "africa", "europe", "americas", "oceania",
    )

    private val BY_COUNTRY: Map<String, String> = buildMap {
        listOf("SA", "AE", "KW", "QA", "BH", "OM", "JO", "PS", "LB", "SY", "IQ", "YE").forEach { put(it, "middle-east") }
        listOf("EG", "LY", "TN", "DZ", "MA", "MR", "SD").forEach { put(it, "north-africa") }
        listOf("TR", "UZ", "KZ", "KG", "TJ", "TM", "AZ").forEach { put(it, "turkiye-central-asia") }
        listOf("PK", "IN", "BD", "MV", "AF", "LK", "NP").forEach { put(it, "south-asia") }
        listOf("ID", "MY", "SG", "BN", "TH", "PH").forEach { put(it, "southeast-asia") }
        listOf(
            "NG", "KE", "TZ", "ET", "SO", "DJ", "SN", "ML", "NE", "BF", "GN", "CI", "TD", "GH", "ZA", "CM",
            "GM", "SL", "UG", "MZ", "MW", "ER",
        ).forEach { put(it, "africa") }
        listOf(
            "GB", "IE", "FR", "BE", "NL", "DE", "AT", "CH", "SE", "NO", "DK", "FI", "ES", "PT", "IT", "BA",
            "AL", "XK", "MK", "BG", "GR", "RU", "PL",
        ).forEach { put(it, "europe") }
        listOf("US", "CA", "MX", "BR", "AR", "TT", "GY").forEach { put(it, "americas") }
        listOf("AU", "NZ", "FJ").forEach { put(it, "oceania") }
    }

    fun of(countryCode: String): String? = BY_COUNTRY[countryCode]
}
