package world.taqwa.app.city

import java.io.File
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds [WithdrawnCities] to the rows the bundle's builder leaves out (`DROPPED` in
 * `tools/build-city-db.py`): a row dropped there without a row here would leave the phones that
 * picked it showing another place's times under its name (ruling R212), and a row here that the
 * bundle still lists would send its people to pick again for nothing.
 */
class WithdrawnCitiesTest {

    private fun file(vararg candidates: String): File =
        candidates.map { File(it) }.firstOrNull { it.exists() } ?: error("none of ${candidates.toList()} found")

    private val dropped: Set<Int> by lazy {
        val source = file("../tools/build-city-db.py", "tools/build-city-db.py").readText()
        val block = source.substringAfter("DROPPED = {").substringBefore("\n}")
        Regex("""^\s*"(\d+)"\s*:""", RegexOption.MULTILINE).findAll(block).map { it.groupValues[1].toInt() }.toSet()
    }

    private val bundled: Set<Int> by lazy {
        file("src/commonMain/composeResources/files/cities.csv", "shared/src/commonMain/composeResources/files/cities.csv")
            .readText().lineSequence().drop(1).filter { it.isNotBlank() }.map { it.substringBefore(',').toInt() }.toSet()
    }

    @Test
    fun `every row the builder drops is withdrawn on the phones that picked it`() {
        assertTrue(dropped.isNotEmpty(), "DROPPED not read from tools/build-city-db.py")
        assertEquals(dropped, WithdrawnCities.rows.map { it.id }.toSet())
    }

    @Test
    fun `no withdrawn row is still in the bundle`() {
        assertEquals(emptyList(), WithdrawnCities.rows.filter { it.id in bundled }.map { it.name })
    }

    @Test
    fun `a withdrawn row is recognised at its stored point by its id and by its name`() {
        fun stored(v: Double) = round(v * 1000.0) / 1000.0
        for (row in WithdrawnCities.rows) {
            assertTrue(WithdrawnCities.picked(row.id, null, stored(row.latitude), stored(row.longitude)), "${row.name} by id")
            assertTrue(WithdrawnCities.picked(null, row.name, stored(row.latitude), stored(row.longitude)), "${row.name} by name")
            assertTrue(WithdrawnCities.picked(row.id, row.name, row.latitude, row.longitude), "${row.name} unrounded")
            assertTrue(!WithdrawnCities.picked(null, null, stored(row.latitude), stored(row.longitude)), "a fix at ${row.name}'s point")
        }
    }
}
