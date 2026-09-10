package world.taqwa.app.nav

import kotlinx.coroutines.test.runTest
import world.taqwa.app.quran.ReadingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AyahTargetTest {

    @Test
    fun translationModeOpensTheAyahsOwnCardAlreadySelected() = runTest {
        val target = ayahWidgetTarget(ReadingMode.TRANSLATION, surah = 2, ayah = 255) { _, _ ->
            error("the page is a database read the translation reader has no use for")
        }
        assertEquals(Screen.Reader(surah = 2, ayah = 255, selectAyah = true), target)
    }

    @Test
    fun mushafModeOpensThePageWithThatAyahHighlighted() = runTest {
        val target = ayahWidgetTarget(ReadingMode.MUSHAF, surah = 2, ayah = 255) { s, a ->
            assertEquals(2 to 255, s to a)
            42
        }
        assertEquals(Screen.Mushaf(page = 42, highlightSurah = 2, highlightAyah = 255), target)
    }

    @Test
    fun theFirstAyahOfASurahIsSelectedTooRatherThanTreatedAsNoSelection() = runTest {
        // 112:1 and 96:1 are both in the widget's pool, and "ayah 1" is also what a tap on the
        // surah list asks for — the difference is this flag, not the number.
        val target = ayahWidgetTarget(ReadingMode.TRANSLATION, surah = 112, ayah = 1) { _, _ -> 1 }
        assertTrue((target as Screen.Reader).selectAyah)
    }
}
