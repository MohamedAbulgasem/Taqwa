package world.taqwa.app.adhkar

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled translation files (`files/adhkar/text/<language>.json`) are read only at run time, so
 * apart from English no fixture covers them: parse each the way the app does, check every du'a page
 * has its translation in exactly the wordings the Arabic has, and check the review gate — the drafts
 * travel in every build and must read as no file until a reviewer marks them `"reviewed": true`.
 * `tools/adhkar/check_items.py` checks the same files' shape; this checks the app's own parser agrees.
 *
 * Lives in `androidHostTest` because it reads the bundled files from the file system, as
 * [AdhkarQuranReferencesTest] does.
 */
class AdhkarTranslationsTest {

    private companion object {
        /** OpenDua's published English, and the languages whose reviewer has passed them (tools/adhkar/review/). */
        val REVIEWED = listOf("en", "fr")

        /**
         * Drafted 11 October 2026 and awaiting one reviewer each (tools/adhkar/TRANSLATIONS-FOR-REVIEW.md).
         * A reviewer who flips a file to `"reviewed": true` moves its language from here to [REVIEWED].
         */
        val DRAFTS = listOf("bn", "id", "tr", "ur")
    }

    private fun resource(name: String): File {
        val moduleRelative = File("src/commonMain/composeResources/files/adhkar/$name")
        return listOf(moduleRelative, File("shared").resolve(moduleRelative)).firstOrNull { it.exists() }
            ?: error("adhkar/$name not found at ${moduleRelative.absolutePath}")
    }

    private val duas = AdhkarCatalogue.parse(resource("items.json").readText()).filter { it.kind == AdhkarKind.DUA }

    /** The bundled file the app's loader asks for, read from the source tree. */
    private val read: suspend (String) -> ByteArray = { path -> resource(path.removePrefix("files/adhkar/")).readBytes() }

    @Test fun `the text folder holds exactly the reviewed and drafted languages`() {
        val files = resource("text").listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json") }
        assertEquals((REVIEWED + DRAFTS).toSet(), files.toSet())
    }

    @Test fun `every language carries every dua in exactly the wordings of its arabic`() {
        assertEquals(15, duas.size)
        for (language in REVIEWED + DRAFTS) {
            val text = AdhkarCatalogue.parseText(resource("text/$language.json").readText())
            assertEquals(language, text.language, "text/$language.json names another language")
            assertEquals(duas.map { it.id }.toSet(), text.items.keys, "text/$language.json ids")
            for (item in duas) {
                assertEquals(item.arabic.keys, text.items.getValue(item.id).keys, "$language: ${item.id} wordings")
                for (set in item.sets) {
                    val translation = assertNotNull(text.translationFor(item.id, set), "$language: ${item.id} has no $set translation")
                    assertFalse(translation.isBlank(), "$language: ${item.id} $set translation is blank")
                }
            }
            // Hisn 90 says "we have entered the morning": it must never be offered in the evening.
            assertNull(text.translationFor("hisn-090", AdhkarSet.EVENING), "$language: hisn-090 in the evening")
        }
    }

    @Test fun `a draft is never shown and a reviewed language is`() = runTest {
        for (language in DRAFTS) {
            assertFalse(AdhkarCatalogue.isReviewed(resource("text/$language.json").readText()), "$language is marked reviewed")
            assertNull(AdhkarCatalogue.loadText(language, read), "$language: a draft reached the reader")
        }
        for (language in REVIEWED) {
            assertTrue(AdhkarCatalogue.isReviewed(resource("text/$language.json").readText()), "$language is not marked reviewed")
            assertEquals(language, assertNotNull(AdhkarCatalogue.loadText(language, read), "$language did not load").language)
        }
    }
}
