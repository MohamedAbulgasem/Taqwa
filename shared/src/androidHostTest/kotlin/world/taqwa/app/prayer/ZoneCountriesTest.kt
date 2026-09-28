package world.taqwa.app.prayer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [ZoneCountries] is generated from the bundled city list: every zone the list names maps to the
 * country most of its cities are in, and nothing else is in the table. Here because it reads the
 * list from the file system, like `RegistryCitiesTest`.
 */
class ZoneCountriesTest {

    private val rows: List<List<String>> by lazy {
        val moduleRelative = File("src/commonMain/composeResources/files/cities.csv")
        val file = if (moduleRelative.exists()) moduleRelative else File("shared/src/commonMain/composeResources/files/cities.csv")
        file.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
    }

    @Test
    fun `every zone in the city list maps to the country most of its cities are in`() {
        val byZone = rows.groupBy({ it[7] }, { it[4] })
        for ((zone, countries) in byZone) {
            val expected = countries.groupingBy { it }.eachCount().maxBy { it.value }.key
            assertEquals(expected, ZoneCountries.of(zone), zone)
        }
    }

    @Test
    fun `a zone the city list does not name has no country`() {
        assertNull(ZoneCountries.of("UTC"))
        assertNull(ZoneCountries.of("Mars/Olympus_Mons"))
        assertEquals("TR", ZoneCountries.of("Europe/Istanbul"))
        assertEquals("LY", ZoneCountries.of("Africa/Tripoli"))
    }
}
