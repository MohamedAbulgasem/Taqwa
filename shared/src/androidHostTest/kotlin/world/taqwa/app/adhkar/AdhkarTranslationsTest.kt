package world.taqwa.app.adhkar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The five drafted translations (`files/adhkar/text/{bn,fr,id,tr,ur}.json`) are read only at run
 * time, so no fixture covers them: parse each bundled file the way the app does and make sure every
 * du'a page has its translation in every set it is read in. `tools/adhkar/check_items.py` checks the
 * same files' shape; this checks that the app's own parser agrees.
 *
 * Lives in `androidHostTest` because it reads the bundled files from the file system, as
 * [AdhkarQuranReferencesTest] does.
 */
class AdhkarTranslationsTest {

    private fun resource(name: String): File {
        val moduleRelative = File("src/commonMain/composeResources/files/adhkar/$name")
        return listOf(moduleRelative, File("shared").resolve(moduleRelative)).firstOrNull { it.exists() }
            ?: error("adhkar/$name not found at ${moduleRelative.absolutePath}")
    }

    private val duas = AdhkarCatalogue.parse(resource("items.json").readText()).filter { it.kind == AdhkarKind.DUA }

    @Test fun `every drafted language carries every dua for each of its sets`() {
        assertEquals(15, duas.size)
        for (language in listOf("bn", "fr", "id", "tr", "ur")) {
            val text = AdhkarCatalogue.parseText(resource("text/$language.json").readText())
            assertEquals(language, text.language, "text/$language.json names another language")
            assertEquals(duas.map { it.id }.toSet(), text.items.keys, "text/$language.json ids")
            for (item in duas) {
                for (set in item.sets) {
                    val translation = assertNotNull(text.translationFor(item.id, set), "$language: ${item.id} has no $set translation")
                    assertFalse(translation.isBlank(), "$language: ${item.id} $set translation is blank")
                }
            }
        }
    }

    @Test fun `a morning only wording is never offered in the evening in any language`() {
        // Hisn 90 says "we have entered the morning": stored under "morning", never "both".
        for (language in listOf("bn", "fr", "id", "tr", "ur")) {
            val text = AdhkarCatalogue.parseText(resource("text/$language.json").readText())
            assertEquals(setOf("morning"), text.items.getValue("hisn-090").keys, "$language: hisn-090")
            assertNull(text.translationFor("hisn-090", AdhkarSet.EVENING), "$language: hisn-090 in the evening")
        }
    }
}
