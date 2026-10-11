package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwelveHourClockTest {
    @Test
    fun `midnight is twelve am and noon is twelve pm`() {
        assertEquals(12 to false, twelveHourParts(0))
        assertEquals(12 to true, twelveHourParts(12))
        assertEquals(1 to true, twelveHourParts(13))
        assertEquals(11 to true, twelveHourParts(23))
        assertEquals(5 to false, twelveHourParts(5))
    }

    @Test
    fun `the marker follows the digits joined by a no-break space`() {
        assertEquals("6:32\u00A0PM", TwelveHourClock.render("6", "32", "PM", markerFirst = false))
    }

    @Test
    fun `turkish puts the marker first`() {
        assertEquals("ÖS\u00A06:32", TwelveHourClock.render("6", "32", "ÖS", markerFirst = true))
        assertTrue(TwelveHourClock.markerFirst("tr-TR"))
        assertTrue(TwelveHourClock.markerFirst("tr"))
        assertFalse(TwelveHourClock.markerFirst("en-GB"))
        assertFalse(TwelveHourClock.markerFirst("ar-EG"))
    }
}
