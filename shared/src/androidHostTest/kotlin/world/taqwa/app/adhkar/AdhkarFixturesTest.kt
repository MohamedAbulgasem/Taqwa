package world.taqwa.app.adhkar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Keeps [ITEMS_JSON] and [EN_JSON], the copies `AdhkarCatalogueTest` parses on both targets, exactly
 * the bundled `items.json` and `text/en.json`. Without this a rebuilt resource would leave the
 * catalogue tests green against yesterday's data.
 *
 * Lives in `androidHostTest` because it reads the bundled files from the file system, as the
 * other bundle tests do.
 */
class AdhkarFixturesTest {

    private fun resource(name: String): File {
        val moduleRelative = File("src/commonMain/composeResources/files/adhkar/$name")
        if (moduleRelative.exists()) return moduleRelative
        val rootRelative = File("shared/src/commonMain/composeResources/files/adhkar/$name")
        if (rootRelative.exists()) return rootRelative
        error("adhkar/$name not found at either ${moduleRelative.absolutePath} or ${rootRelative.absolutePath}")
    }

    private val refresh = "Run `python3 -I tools/adhkar/embed_test_fixture.py` to refresh AdhkarFixtures.kt."

    @Test fun `the items copy is the bundled file`() {
        assertEquals(resource("items.json").readText(), ITEMS_JSON, refresh)
    }

    @Test fun `the english copy is the bundled file`() {
        assertEquals(resource("text/en.json").readText(), EN_JSON, refresh)
    }
}
