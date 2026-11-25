package world.taqwa.timetables

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Every published city's name, and its country's, in all seven site languages (the owner's decision of 25 November
 * 2026): every index lists every city, one with no page in its language by these labels, linked to its English page.
 * A city's label follows the rule its pages' names do (the list's override, else the app's city file; English from
 * `cities.csv`), and a language with no name for it fails nothing; a page language still needs one.
 */
class CityLabelsTest {

    private val app: File = createTempDirectory("app").toFile().apply {
        File(this, "cities.csv").writeText(
            """
            id,name,region,country,countryCode,lat,lon,tz
            2210247,Tripoli,Tripoli,Libya,LY,32.88743,13.18733,Africa/Tripoli
            2219905,Al Khums,Al Marqab,Libya,LY,32.64861,14.26191,Africa/Tripoli
            """.trimIndent() + "\n",
        )
        File(this, "city-names-ar.csv").writeText("id,name\n2210247,طرابلس\n2219905,المرقب\n")
        File(this, "city-names-fr.csv").writeText("id,name\n2210247,Tripoli\n")
        File(this, "city-names-tr.csv").writeText("id,name\n2210247,Trablus\n")
    }

    private fun list(vararg rows: String): File = File.createTempFile("cities", ".tsv").apply {
        writeText("# slug\tid\tlanguages\tfeatured\tnames\n" + rows.joinToString("\n") + "\n")
    }

    @Test
    fun aCityIsLabelledInEveryLanguageTheListOrTheAppNamesIt() {
        // An English page alone, yet named in Arabic, French and Turkish by the app's files, in Urdu by the list's own
        // override and in English by cities.csv; nothing names it in Indonesian or Bengali, and the list still loads.
        val city = Catalog.load(list("tripoli-libya\t2210247\ten\t\tur=طرابلس"), app).single()
        assertEquals(listOf("en"), city.languages)
        assertEquals(
            mapOf("en" to "Tripoli", "ar" to "طرابلس", "fr" to "Tripoli", "tr" to "Trablus", "id" to null, "ur" to "طرابلس", "bn" to null),
            SITE_LANGUAGES.associateWith { city.label(it) },
        )
    }

    @Test
    fun anOverrideWinsOverTheAppsNameInALanguageWithoutAPage() {
        val city = Catalog.load(list("al-khums-libya\t2219905\ten\t\tar=الخمس"), app).single()
        assertEquals("الخمس", city.label("ar"))
        assertEquals("Al Khums", city.label("en"))
        assertNull(city.label("fr"))
    }

    @Test
    fun aPageLanguagesLabelIsItsPagesNameAndStillMustExist() {
        val city = Catalog.load(list("al-khums-libya\t2219905\ten ar\t\tar=الخمس"), app).single()
        assertEquals(city.name("ar"), city.label("ar"))
        assertEquals(city.name("en"), city.label("en"))
        val error = assertFailsWith<CatalogError> { Catalog.load(list("al-khums-libya\t2219905\ten fr\t\t"), app) }
        assertTrue(error.message!!.contains("al-khums-libya has no fr name"), error.message)
    }

    @Test
    fun theSitesOwnListLabelsEachCityInItsPageLanguagesAsItsPagesNameIt() {
        val cities = Catalog.load(TestPaths.repoRoot.resolve("site/cities.tsv"), TestPaths.appFiles)
        assertTrue(cities.isNotEmpty())
        for (city in cities) {
            for (language in city.languages) assertEquals(city.name(language), city.label(language), "${city.slug} $language")
        }
        // Some city is named in a language it has no page in, which is what that language's index lists it by.
        assertTrue(cities.any { city -> SITE_LANGUAGES.any { it !in city.languages && city.label(it) != null } })
    }

    // ── The document: every published city carries its labels ──

    private val official = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val strings = AppStrings(TestPaths.appResources)
    private val stamps = StampsAsOf.londonAndTorontoThrough2026(official, Stamp.load(official.resolve("stamps")))
    private val friday = Instant.parse("2026-09-25T00:07:00Z")

    /** London with pages in English and Arabic, named in French and Bengali besides, and in no other language. */
    private val london = City(
        slug = "london-uk", id = 2643743, countryCode = "GB", region = "europe", latitude = 51.50853, longitude = -0.12574,
        timeZone = "Europe/London", languages = listOf("en", "ar"), featured = emptySet(),
        names = mapOf("en" to "London", "ar" to "لندن"), admin1 = "England",
        labels = mapOf("en" to "London", "ar" to "لندن", "fr" to "Londres", "bn" to "লন্ডন"),
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String) = getValue(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = getValue(key) as List<Map<String, Any?>>

    @Test
    fun everyPublishedCityCarriesItsAndItsCountrysNameInAllSevenLanguages() {
        val built = Document(strings, stamps, official, ruled = AcceptedReach.ALL).build(listOf(london), friday)
        val city = built.list("cities").single()
        val labels = city.map("labels")
        assertEquals(SITE_LANGUAGES, labels.keys.toList())
        assertEquals(
            mapOf("en" to "London", "ar" to "لندن", "fr" to "Londres", "tr" to null, "id" to null, "ur" to null, "bn" to "লন্ডন"),
            SITE_LANGUAGES.associateWith { labels.map(it)["city"] },
        )
        // The country in each language as a page there would print it, CLDR's.
        for (language in SITE_LANGUAGES) {
            assertEquals(Formats(language, "GB").countryName("GB"), labels.map(language)["country"], language)
        }
        assertEquals("Royaume-Uni", labels.map("fr")["country"])
        assertEquals("المملكة المتحدة", labels.map("ar")["country"])
        // Where the city has a page, its label is what the page prints.
        val pages = city.map("pages")
        assertEquals(listOf("en", "ar"), pages.keys.toList())
        for (language in pages.keys) {
            assertEquals(pages.map(language)["city"], labels.map(language)["city"], language)
            assertEquals(pages.map(language)["country"], labels.map(language)["country"], language)
        }
        // A language with no name for the city is written as JSON's null, the country beside it.
        val turkish = Json.write(linkedMapOf("city" to null, "country" to Formats("tr", "GB").countryName("GB")))
        assertTrue("\"tr\":$turkish" in Json.write(built), turkish)
    }

    @Test
    fun aCityMadeWithoutLabelsIsLabelledInItsPageLanguagesAlone() {
        // As the other tests make their cities: the labels are the page languages' names.
        val plain = City(
            slug = "london-uk", id = 2643743, countryCode = "GB", region = "europe", latitude = 51.50853, longitude = -0.12574,
            timeZone = "Europe/London", languages = listOf("en", "ar"), featured = emptySet(),
            names = mapOf("en" to "London", "ar" to "لندن"),
        )
        assertEquals(mapOf("en" to "London", "ar" to "لندن"), SITE_LANGUAGES.associateWith { plain.label(it) }.filterValues { it != null })
    }
}
