package world.taqwa.app.feature.quran

import androidx.compose.ui.text.intl.LocaleList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AyahSpeechTest {

    @Test
    fun anAyahIsItsNumberThenItsTranslation() {
        val spoken = spokenAyah("Ayah 255", "Allah — there is no deity except Him.", "en")
        assertEquals("Ayah 255. Allah — there is no deity except Him.", spoken.text)
    }

    @Test
    fun theTranslationRunCarriesItsOwnLanguage() {
        val spoken = spokenAyah("Verset 255", "Allah ! Point de divinité à part Lui.", "fr")
        val runs = spoken.spanStyles.filter { it.item.localeList != null }
        assertEquals(1, runs.size)
        assertEquals(LocaleList("fr"), runs.single().item.localeList)
        assertEquals("Verset 255. ".length, runs.single().start)
        assertEquals(spoken.text.length, runs.single().end)
    }

    @Test
    fun withoutATranslationOnlyTheNumberIsSpoken() {
        val spoken = spokenAyah("Ayah 1", null, "en")
        assertEquals("Ayah 1", spoken.text)
        assertTrue(spoken.spanStyles.isEmpty())
        assertEquals("Ayah 1", spokenAyah("Ayah 1", "  ", "en").text)
    }
}
