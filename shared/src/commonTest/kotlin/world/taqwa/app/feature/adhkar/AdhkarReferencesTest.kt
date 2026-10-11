package world.taqwa.app.feature.adhkar

import world.taqwa.app.adhkar.AdhkarCatalogue
import world.taqwa.app.adhkar.ITEMS_JSON
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The bundled references as [referenceLine] shows them: "<collection's name> <number>", the name in
 * the interface language and the number in its digits. Only the name is translated, so a word in
 * the number ("al-Kubra 10731") reaches an Arabic, Urdu or Bengali line in Latin letters, and a
 * collection with no name shows its raw id. These hold the shipped file ([ITEMS_JSON], kept equal
 * to it by `AdhkarFixturesTest`) to both rules.
 */
class AdhkarReferencesTest {

    private val sources = AdhkarCatalogue.parse(ITEMS_JSON).flatMap { item -> item.sources.map { item.id to it } }

    @Test fun `every source number is digits and reference punctuation only`() {
        val worded = sources.filterNot { (_, source) -> REFERENCE_NUMBER.matches(source.number) }
            .map { (id, source) -> "$id ${source.collection} ${source.number}" }
        assertEquals(emptyList(), worded, "numbers that are not digits joined by : / or -")
    }

    @Test fun `every collection the items cite has a name of its own`() {
        val unnamed = sources.map { (_, source) -> source.collection }.distinct().filter { sourceNameRes(it) == null }
        assertEquals(emptyList(), unnamed, "collections that would show their raw id")
    }

    @Test fun `a collection this build does not know has no name`() {
        assertNull(sourceNameRes("nobody"))
    }

    private companion object {
        /** "2:255", "1/562", "10731": digits, with a colon, slash or hyphen between groups of them. */
        val REFERENCE_NUMBER = Regex("[0-9]+([:/-][0-9]+)*")
    }
}
