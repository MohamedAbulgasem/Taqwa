package world.taqwa.app.prayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ClockChangesTest {

    private val hour = 3600

    // British Columbia: UTC−7 all year from 1 November 2026; old zone data says UTC−8 in winter.
    private val vancouverWinter = Instant.parse("2026-11-05T20:00:00Z")

    @Test
    fun britishColumbiaOnStaleZoneDataIsAnHourEarly() {
        val w = ClockChanges.warningFor("America/Vancouver", vancouverWinter, -8 * hour, clockSetByHand = false)!!
        assertEquals("british_columbia", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun britishColumbiaOnStaleZoneDataWithAHandSetClockNamesTheZoneData() {
        val w = ClockChanges.warningFor("America/Vancouver", vancouverWinter, -8 * hour, clockSetByHand = true)!!
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
    }

    @Test
    fun britishColumbiaWithCurrentZoneDataAndAutomaticTimeSaysNothing() {
        assertNull(ClockChanges.warningFor("America/Vancouver", vancouverWinter, -7 * hour, clockSetByHand = false))
    }

    @Test
    fun britishColumbiaWithAHandSetClockMayBeAnHourEarly() {
        val w = ClockChanges.warningFor("America/Vancouver", vancouverWinter, -7 * hour, clockSetByHand = true)!!
        assertEquals(ClockIssue.SET_BY_HAND, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun britishColumbiaWithAHandSetClockOnTheSixtiethDayStillWarns() {
        // 30 December 2026 is day 59 after 1 November, counted from 0.
        val w = ClockChanges.warningFor("America/Vancouver", Instant.parse("2026-12-30T20:00:00Z"), -7 * hour, clockSetByHand = true)
        assertEquals(ClockIssue.SET_BY_HAND, w?.issue)
    }

    @Test
    fun britishColumbiaWithAHandSetClockSixtyDaysOnSaysNothing() {
        // 31 December 2026, 60 days after the change: a clock set by hand is no longer flagged.
        assertNull(ClockChanges.warningFor("America/Vancouver", Instant.parse("2026-12-31T20:00:00Z"), -7 * hour, clockSetByHand = true))
    }

    @Test
    fun britishColumbiaOnStaleZoneDataStillWarnsLongAfterSixtyDays() {
        val w = ClockChanges.warningFor("America/Vancouver", Instant.parse("2027-06-01T20:00:00Z"), -8 * hour, clockSetByHand = false)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w?.issue)
    }

    @Test
    fun britishColumbiaBeforeItsChangeSaysNothing() {
        // 31 October 2026: still summer time, which old and new data agree on.
        val before = Instant.parse("2026-10-31T20:00:00Z")
        assertNull(ClockChanges.warningFor("America/Vancouver", before, -7 * hour, clockSetByHand = true))
    }

    // Casablanca: UTC+0 from 20 September 2026; old zone data says UTC+1.
    private val casablanca = Instant.parse("2026-09-26T21:02:00Z")

    @Test
    fun casablancaOnStaleZoneDataIsAnHourLate() {
        val w = ClockChanges.warningFor("Africa/Casablanca", casablanca, 1 * hour, clockSetByHand = false)!!
        assertEquals("morocco", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(false, w.timesEarly)
    }

    @Test
    fun casablancaWithAHandSetClockMayBeAnHourLate() {
        val w = ClockChanges.warningFor("Africa/Casablanca", casablanca, 0, clockSetByHand = true)!!
        assertEquals(ClockIssue.SET_BY_HAND, w.issue)
        assertEquals(false, w.timesEarly)
    }

    @Test
    fun casablancaWithCurrentZoneDataAndAutomaticTimeSaysNothing() {
        assertNull(ClockChanges.warningFor("Africa/Casablanca", casablanca, 0, clockSetByHand = false))
    }

    @Test
    fun whereThePlatformCannotTellOnlyTheZoneDataIsChecked() {
        // iOS: no automatic-time setting to read.
        assertNull(ClockChanges.warningFor("Africa/Casablanca", casablanca, 0, clockSetByHand = null))
        assertEquals(
            ClockIssue.STALE_ZONE_DATA,
            ClockChanges.warningFor("Africa/Casablanca", casablanca, 1 * hour, clockSetByHand = null)?.issue,
        )
    }

    @Test
    fun almatyOnStaleZoneDataIsAnHourLate() {
        val w = ClockChanges.warningFor("Asia/Almaty", Instant.parse("2026-09-26T12:00:00Z"), 6 * hour, clockSetByHand = false)!!
        assertEquals("kazakhstan", w.change.id)
        assertEquals(false, w.timesEarly)
    }

    @Test
    fun almatyWithAHandSetClockTwoYearsAfterItsChangeSaysNothing() {
        assertNull(ClockChanges.warningFor("Asia/Almaty", Instant.parse("2026-09-26T12:00:00Z"), 5 * hour, clockSetByHand = true))
    }

    @Test
    fun anExpiredChangeSaysNothing() {
        assertNull(ClockChanges.warningFor("Asia/Almaty", Instant.parse("2027-03-02T12:00:00Z"), 6 * hour, clockSetByHand = true))
    }

    @Test
    fun aZoneWithNoChangeSaysNothing() {
        assertNull(ClockChanges.warningFor("Europe/Istanbul", casablanca, 5 * hour, clockSetByHand = true))
    }
}
