package world.taqwa.timetables

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogTest {

    private val app: File = createTempDirectory("app").toFile().apply {
        File(this, "cities.csv").writeText(
            """
            id,name,region,country,countryCode,lat,lon,tz
            2210247,Tripoli,Tripoli,Libya,LY,32.88743,13.18733,Africa/Tripoli
            2219905,Al Khums,Al Marqab,Libya,LY,32.64861,14.26191,Africa/Tripoli
            1174872,Karachi,Sindh,Pakistan,PK,24.8608,67.0104,Asia/Karachi
            """.trimIndent() + "\n",
        )
        File(this, "city-names-ar.csv").writeText("id,name\n2210247,طرابلس\n2219905,المرقب\n")
        File(this, "city-names-ur.csv").writeText("id,name\n1174872,کراچی\n")
    }

    private fun list(vararg rows: String): File = File.createTempFile("cities", ".tsv").apply {
        writeText("# slug\tid\tlanguages\tfeatured\tnames\n" + rows.joinToString("\n") + "\n")
    }

    @Test
    fun aRowResolvesCoordinatesZoneAndNamesFromTheAppsData() {
        val city = Catalog.load(list("tripoli-libya\t2210247\ten ar\ten ar\t"), app).single()
        assertEquals("tripoli-libya", city.slug)
        assertEquals("LY", city.countryCode)
        assertEquals("Africa/Tripoli", city.timeZone)
        assertEquals(32.88743, city.latitude)
        assertEquals("Tripoli", city.name("en"))
        assertEquals("طرابلس", city.name("ar"))
        assertEquals(setOf("en", "ar"), city.featured)
        assertEquals("north-africa", city.region)
    }

    @Test
    fun anOverrideWinsOverTheAppsName() {
        val city = Catalog.load(list("al-khums-libya\t2219905\ten ar\t\tar=الخمس"), app).single()
        assertEquals("الخمس", city.name("ar"))
        assertEquals("Al Khums", city.name("en"))
    }

    @Test
    fun theFirstLevelRegionComesFromTheAppsData() {
        // What the engine picks an authority's unit by where the units are regions (ruling R32).
        val karachi = Catalog.load(list("karachi-pakistan\t1174872\ten ur\t\t"), app).single()
        assertEquals("Sindh", karachi.admin1)
        assertEquals("south-asia", karachi.region)
    }

    @Test
    fun commentsAndBlankLinesAreIgnored() {
        val cities = Catalog.load(list("", "# held back: somewhere", "tripoli-libya\t2210247\ten\t\t"), app)
        assertEquals(1, cities.size)
    }

    @Test
    fun theChecksPagesSlugIsReserved() {
        // `/prayer-times/how-taqwa-checks/` is the "How Taqwa checks" page (spec §5), never a city.
        val error = assertFailsWith<CatalogError> { Catalog.load(list("how-taqwa-checks\t2210247\ten\t\t"), app) }
        assertTrue(error.message!!.contains("slug how-taqwa-checks is reserved for the checks page"), error.message)
    }

    @Test
    fun everyProblemIsReportedAtOnce() {
        val error = assertFailsWith<CatalogError> {
            Catalog.load(
                list(
                    "tripoli-libya\t9999999\ten\t\t",
                    "tripoli-libya\t2210247\ten\t\t",
                    "Karachi Pakistan\t1174872\ten\t\t",
                    "karachi-pakistan\t1174872\ten xx\t\t",
                    "al-khums-libya\t2219905\tar\t\t",
                    "tripoli-2\t2210247\ten fr\t\t",
                    "tripoli-4\t2210247\ten\tar\t",
                ),
                app,
            )
        }
        val message = error.message!!
        listOf(
            "unknown city id 9999999",
            "duplicate slug tripoli-libya",
            "slug 'Karachi Pakistan'",
            "unknown language xx",
            "no English page",
            "no fr name",
            "featured in ar",
        ).forEach { assertTrue(message.contains(it), "missing '$it' in:\n$message") }
    }
}
