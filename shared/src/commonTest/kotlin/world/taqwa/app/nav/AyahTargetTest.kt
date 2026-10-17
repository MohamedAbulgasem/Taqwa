package world.taqwa.app.nav

import kotlinx.coroutines.test.runTest
import world.taqwa.app.quran.ReadingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    @Test fun theRecitedAyahOpensUnselectedInEitherMode() = runTest {
        assertEquals(
            Screen.Reader(2, 255, followVoice = true),
            recitationTarget(ReadingMode.TRANSLATION, 2, 255) { _, _ -> error("not asked") },
        )
        assertEquals(
            Screen.Mushaf(page = 42, followVoice = true),
            recitationTarget(ReadingMode.MUSHAF, 2, 255) { s, a -> if (s == 2 && a == 255) 42 else 0 },
        )
    }

    /**
     * The widget's ayah is the same all day, so a second tap can ask for the screen the first one
     * opened, which the reader has since read on from. Each tap's own request number makes it a
     * screen of its own, so it opens on its ayah again rather than being taken for the open one.
     */
    @Test fun aRepeatedWidgetTapIsAScreenOfItsOwnInEitherMode() = runTest {
        for (mode in listOf(ReadingMode.TRANSLATION, ReadingMode.MUSHAF)) {
            val first = ayahWidgetTarget(mode, 18, 10, request = 1) { _, _ -> 294 }
            val again = ayahWidgetTarget(mode, 18, 10, request = 2) { _, _ -> 294 }
            assertNotEquals(first, again, "$mode")
        }
        val navigator = Navigator(Screen.Quran)
        navigator.openReading(ayahWidgetTarget(ReadingMode.MUSHAF, 18, 10, request = 1) { _, _ -> 294 })
        val opened = navigator.backStack.value
        navigator.openReading(ayahWidgetTarget(ReadingMode.MUSHAF, 18, 10, request = 2) { _, _ -> 294 })
        assertNotEquals(opened, navigator.backStack.value)
    }

    /**
     * Two taps of the same widget ayah, the second over the Mushaf the first opened, each go through
     * the widget's own handling: the second still changes the back stack, so the page opens on its
     * ayah again. It is the tap's request number, stamped where the tap is handled, that does it.
     */
    @Test fun aSecondTapOfTheSameWidgetAyahOpensItAgain() = runTest {
        val navigator = Navigator(Screen.Quran)
        suspend fun tap() {
            LaunchRequests.openAyah(18, 10)
            openWidgetAyah(
                surah = 18,
                ayah = 10,
                mode = { ReadingMode.MUSHAF },
                requestsMade = { LaunchRequests.count },
                pageOf = { _, _ -> 294 },
                openReading = navigator::openReading,
            )
            LaunchRequests.consume()
        }
        tap()
        val first = navigator.backStack.value
        tap()
        assertNotEquals(first, navigator.backStack.value)
    }

    /** A page that cannot be looked up leaves the back stack as it was. */
    @Test fun aWidgetTapWhosePageCannotBeFoundLeavesTheStackAlone() = runTest {
        val navigator = Navigator(Screen.Quran)
        val before = navigator.backStack.value
        openWidgetAyah(18, 10, mode = { ReadingMode.MUSHAF }, requestsMade = { 1L }, pageOf = { _, _ -> error("no page") }, openReading = navigator::openReading)
        assertEquals(before, navigator.backStack.value)
    }
}
