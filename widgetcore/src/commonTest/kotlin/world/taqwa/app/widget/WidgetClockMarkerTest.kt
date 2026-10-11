package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// What the platforms emit between the digits and the marker (U+00A0, spec §1.2); built from its
// code point so no invisible character sits in this source.
private val NBSP = Char(0x00A0).toString()

class WidgetClockMarkerTest {

    @Test
    fun aTwentyFourHourTimeCarriesNoMarker() {
        for (time in listOf("5:35", "05:35", "18:32", "٢٠:١٥", "০৫:৩৫", "۱۸:۳۲")) {
            assertFalse(clockTimesCarryMarker(listOf(time)), time)
        }
    }

    @Test
    fun aTwelveHourTimeCarriesAMarker() {
        for (time in listOf("5:10 PM", "5:10${NBSP}PM", "ÖS${NBSP}5:10", "٦:٣٢${NBSP}م", "٦:٣٢ م", "12:05 AM")) {
            assertTrue(clockTimesCarryMarker(listOf(time)), time)
        }
    }

    @Test
    fun oneMarkedTimeAmongBareOnesIsEnough() {
        assertTrue(clockTimesCarryMarker(listOf("5:35", "12:46", "4:03${NBSP}PM")))
    }

    @Test
    fun noTimesAndEmptyTimesCarryNoMarker() {
        assertFalse(clockTimesCarryMarker(emptyList()))
        assertFalse(clockTimesCarryMarker(listOf("")))
    }

    @Test
    fun aCardIsTwelveHourWhenAnyOfItsTimesIs() {
        val rows = listOf(
            WidgetPrayerRow(Prayer.FAJR, "Fajr", "5:35", false),
            WidgetPrayerRow(Prayer.DHUHR, "Dhuhr", "12:46", true),
        )
        fun content(next: String, rows: List<WidgetPrayerRow>) = WidgetContent(
            nextPrayerDisplayName = "Dhuhr", countdownMinutes = 21, nextClockTime = next,
            rows = rows, ringProgress = 0.5f, countdownLabel = "Dhuhr in",
        )
        assertFalse(content("12:46", rows).carriesDayPeriodMarker())
        assertTrue(content("12:46${NBSP}PM", rows).carriesDayPeriodMarker())
        assertTrue(
            content("12:46", rows + WidgetPrayerRow(Prayer.ASR, "Asr", "4:03${NBSP}PM", false)).carriesDayPeriodMarker(),
        )
        assertEquals(false, content("12:46", emptyList()).carriesDayPeriodMarker())
    }
}
