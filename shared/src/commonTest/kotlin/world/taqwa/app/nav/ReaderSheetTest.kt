package world.taqwa.app.nav

import world.taqwa.app.adhkar.AdhkarSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ReaderSheet], the reader's reading-settings sheet held above the reader: kept across the voice's
 * own move to the next surah, closed by any opening made on purpose and by any screen that is not
 * a reader, so it never comes back by itself over a later one.
 */
class ReaderSheetTest {

    @Test
    fun `the voice moving the reader on to the next surah keeps the sheet`() {
        val navigator = Navigator(Screen.Quran)
        val sheet = ReaderSheet()
        navigator.push(Screen.Reader(112, 1))
        sheet.open.value = true

        navigator.replace(Screen.Reader(113, 1))
        sheet.onTop(navigator.current)

        assertTrue(sheet.open.value)
    }

    @Test
    fun `a reading screen opened on purpose opens without the sheet`() {
        val navigator = Navigator(Screen.Quran)
        val sheet = ReaderSheet()
        navigator.push(Screen.Reader(112, 1))
        sheet.open.value = true

        sheet.openReading(navigator, Screen.Reader(18, 10, selectAyah = true))

        assertFalse(sheet.open.value)
        assertEquals(Screen.Reader(18, 10, selectAyah = true), navigator.current)
    }

    /**
     * An adhkar reminder tapped with the sheet open takes the app to the Prayer tab; a reader later
     * pushed from the surah list (which does not go through openReading) opens without it.
     */
    @Test
    fun `a screen that is not a reader closes the sheet so a later reader opens without it`() {
        val navigator = Navigator(Screen.Quran)
        val sheet = ReaderSheet()
        navigator.push(Screen.Reader(112, 1))
        sheet.open.value = true

        navigator.selectTab(Tab.PRAYER)
        navigator.push(Screen.Adhkar(AdhkarSet.MORNING))
        sheet.onTop(navigator.current)
        navigator.selectTab(Tab.QURAN)
        navigator.push(Screen.Reader(67, 1))
        sheet.onTop(navigator.current)

        assertFalse(sheet.open.value)
    }
}
