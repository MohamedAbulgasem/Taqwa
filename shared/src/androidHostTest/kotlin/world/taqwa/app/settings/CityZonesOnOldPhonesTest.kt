package world.taqwa.app.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every zone the bundled city list names is one an Android 9 phone can parse, under its own name or
 * the old name [ZoneIds.legacyNames] gives it. A zone the list gains that the phone's 2018 data
 * does not have (the next rename, or a zone split off later) fails here until it has an old name,
 * instead of crashing on every launch for the people who pick that city. Here because it reads
 * files, like `ZoneCountriesTest`.
 */
class CityZonesOnOldPhonesTest {

    private fun file(path: String): File =
        File(path).takeIf { it.exists() } ?: File("shared/$path")

    private val tzdata2018e: Set<String> by lazy {
        file("src/androidHostTest/fixtures/tzdata2018e-zones.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .toSet()
    }

    private val cityZones: Set<String> by lazy {
        file("src/commonMain/composeResources/files/cities.csv").readLines().drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",")[7] }
            .toSet()
    }

    @Test
    fun `the fixture is the Android 9 data, with Kiev and without Kyiv`() {
        assertEquals(591, tzdata2018e.size)
        assertTrue("Europe/Kiev" in tzdata2018e)
        assertTrue("Europe/Kyiv" !in tzdata2018e)
    }

    /**
     * Nuuk is the one exception, read as the phone's own zone: Greenland's clocks moved an hour
     * in 2023 and no zone in the 2018 data keeps its time, so any old name would be an hour off.
     */
    @Test
    fun `every city zone but Nuuk parses on an Android 9 phone under its name or its old one`() {
        val phonesOwnZone = cityZones.filter { zone ->
            ZoneIds.resolvable(zone, known = { it in tzdata2018e }, fallback = { "" }).isEmpty()
        }
        assertEquals(listOf("America/Nuuk"), phonesOwnZone)
    }

    @Test
    fun `every old name is for a zone the city list names and the old data lacks`() {
        for ((zone, old) in ZoneIds.legacyNames) {
            assertTrue(zone in cityZones, "$zone is not in the city list")
            assertTrue(zone !in tzdata2018e, "$zone is already in the 2018 data")
            assertTrue(old in tzdata2018e, "$old is not in the 2018 data")
        }
    }
}
