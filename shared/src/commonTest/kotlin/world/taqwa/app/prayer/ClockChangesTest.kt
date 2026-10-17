package world.taqwa.app.prayer

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    // Alberta: UTC−6 all year from 1 November 2026; old zone data says UTC−7 in winter.
    private val edmontonWinter = Instant.parse("2027-01-15T19:00:00Z")

    @Test
    fun albertaOnStaleZoneDataIsAnHourEarly() {
        val w = ClockChanges.warningFor("America/Edmonton", edmontonWinter, -7 * hour, clockSetByHand = false)!!
        assertEquals("alberta", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun albertaWithCurrentZoneDataAndAutomaticTimeSaysNothing() {
        assertNull(ClockChanges.warningFor("America/Edmonton", edmontonWinter, -6 * hour, clockSetByHand = false))
    }

    @Test
    fun albertaBeforeItsChangeSaysNothing() {
        // 31 October 2026: still summer time, which old and new data agree on.
        val before = Instant.parse("2026-10-31T19:00:00Z")
        assertNull(ClockChanges.warningFor("America/Edmonton", before, -6 * hour, clockSetByHand = true))
    }

    @Test
    fun albertaOnStaleZoneDataWarnsFromTheHourItsOldDataGoesBack() {
        // 02:00 on 1 November 2026 is 08:00 UTC: old zone data turns to UTC−7 there.
        val w = ClockChanges.warningFor("America/Edmonton", Instant.parse("2026-11-01T08:00:00Z"), -7 * hour, clockSetByHand = false)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w?.issue)
        assertEquals(true, w?.timesEarly)
    }

    @Test
    fun theNorthwestTerritoriesOnStaleZoneDataAreAnHourEarlyToo() {
        // America/Yellowknife is a link to America/Edmonton; America/Inuvik keeps UTC−6 from the same day.
        for (zone in listOf("America/Yellowknife", "America/Inuvik")) {
            val w = ClockChanges.warningFor(zone, edmontonWinter, -7 * hour, clockSetByHand = false)
            assertEquals("alberta", w?.change?.id, zone)
            assertEquals(true, w?.timesEarly, zone)
            assertNull(ClockChanges.warningFor(zone, edmontonWinter, -6 * hour, clockSetByHand = false), zone)
        }
    }

    // Manitoba: UTC−5 all year from 1 November 2026; old zone data says UTC−6 in winter.
    private val winnipegWinter = Instant.parse("2027-01-15T18:00:00Z")

    @Test
    fun manitobaOnStaleZoneDataIsAnHourEarly() {
        val w = ClockChanges.warningFor("America/Winnipeg", winnipegWinter, -6 * hour, clockSetByHand = false)!!
        assertEquals("manitoba", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun manitobaWithCurrentZoneDataAndAutomaticTimeSaysNothing() {
        assertNull(ClockChanges.warningFor("America/Winnipeg", winnipegWinter, -5 * hour, clockSetByHand = false))
    }

    @Test
    fun manitobaBeforeItsChangeSaysNothing() {
        // 31 October 2026: still summer time, which old and new data agree on.
        val before = Instant.parse("2026-10-31T18:00:00Z")
        assertNull(ClockChanges.warningFor("America/Winnipeg", before, -5 * hour, clockSetByHand = true))
    }

    @Test
    fun northwesternOntarioOnStaleZoneDataIsAnHourEarlyToo() {
        // America/Rainy_River is a link to America/Winnipeg.
        val w = ClockChanges.warningFor("America/Rainy_River", winnipegWinter, -6 * hour, clockSetByHand = false)
        assertEquals("manitoba", w?.change?.id)
        assertEquals(true, w?.timesEarly)
    }

    // Yukon: UTC−7 all year from 1 November 2020; old zone data says UTC−8 in winter.
    private val whitehorseWinter = Instant.parse("2027-01-15T20:00:00Z")

    @Test
    fun yukonOnStaleZoneDataIsAnHourEarly() {
        // America/Dawson (Dawson City) keeps Whitehorse's clock.
        for (zone in listOf("America/Whitehorse", "America/Dawson")) {
            val w = ClockChanges.warningFor(zone, whitehorseWinter, -8 * hour, clockSetByHand = false)
            assertEquals("yukon", w?.change?.id, zone)
            assertEquals(ClockIssue.STALE_ZONE_DATA, w?.issue, zone)
            assertEquals(true, w?.timesEarly, zone)
        }
    }

    @Test
    fun yukonWithCurrentZoneDataSaysNothing() {
        assertNull(ClockChanges.warningFor("America/Whitehorse", whitehorseWinter, -7 * hour, clockSetByHand = false))
    }

    @Test
    fun yukonBeforeItsChangeSaysNothing() {
        // 31 October 2020: still summer time, which old and new data agree on.
        val before = Instant.parse("2020-10-31T20:00:00Z")
        assertNull(ClockChanges.warningFor("America/Whitehorse", before, -7 * hour, clockSetByHand = true))
    }

    // Syria: UTC+3 all year from 28 October 2022; old zone data says UTC+2 in winter.
    private val damascusWinter = Instant.parse("2026-12-15T09:00:00Z")

    @Test
    fun syriaOnStaleZoneDataIsAnHourEarly() {
        val w = ClockChanges.warningFor("Asia/Damascus", damascusWinter, 2 * hour, clockSetByHand = false)!!
        assertEquals("syria", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun syriaOnStaleZoneDataWarnsFromTheHourItsOldDataGoesBack() {
        // Midnight on 30 October 2026 is 21:00 UTC the day before: old zone data turns to UTC+2 there.
        val w = ClockChanges.warningFor("Asia/Damascus", Instant.parse("2026-10-29T21:00:00Z"), 2 * hour, clockSetByHand = false)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w?.issue)
        assertEquals(true, w?.timesEarly)
    }

    @Test
    fun syriaWithCurrentZoneDataSaysNothing() {
        assertNull(ClockChanges.warningFor("Asia/Damascus", damascusWinter, 3 * hour, clockSetByHand = false))
    }

    @Test
    fun syriaOnStaleZoneDataInSummerSaysNothing() {
        // Old data's summer time is the true UTC+3: the times are right, so the line stays away.
        assertNull(ClockChanges.warningFor("Asia/Damascus", Instant.parse("2027-07-15T09:00:00Z"), 3 * hour, clockSetByHand = false))
    }

    @Test
    fun syriaBeforeItsChangeSaysNothing() {
        // 27 October 2022: still summer time, which old and new data agree on.
        val before = Instant.parse("2022-10-27T09:00:00Z")
        assertNull(ClockChanges.warningFor("Asia/Damascus", before, 3 * hour, clockSetByHand = true))
    }

    // Jordan: UTC+3 all year from 28 October 2022; old zone data says UTC+2 in winter.
    private val ammanWinter = Instant.parse("2026-12-15T09:00:00Z")

    @Test
    fun jordanOnStaleZoneDataIsAnHourEarly() {
        val w = ClockChanges.warningFor("Asia/Amman", ammanWinter, 2 * hour, clockSetByHand = false)!!
        assertEquals("jordan", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun jordanOnStaleZoneDataWarnsFromTheHourItsOldDataGoesBack() {
        // 01:00 on 30 October 2026 is 22:00 UTC the day before: old zone data turns to UTC+2 there.
        val w = ClockChanges.warningFor("Asia/Amman", Instant.parse("2026-10-29T22:00:00Z"), 2 * hour, clockSetByHand = false)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w?.issue)
        assertEquals(true, w?.timesEarly)
    }

    @Test
    fun jordanWithCurrentZoneDataSaysNothing() {
        assertNull(ClockChanges.warningFor("Asia/Amman", ammanWinter, 3 * hour, clockSetByHand = false))
    }

    @Test
    fun jordanBeforeItsChangeSaysNothing() {
        // 27 October 2022: still summer time, which old and new data agree on.
        val before = Instant.parse("2022-10-27T09:00:00Z")
        assertNull(ClockChanges.warningFor("Asia/Amman", before, 3 * hour, clockSetByHand = true))
    }

    // Chihuahua: UTC−6 all year from 30 October 2022; old zone data says UTC−7 in winter.
    private val chihuahuaWinter = Instant.parse("2026-12-15T18:00:00Z")

    @Test
    fun chihuahuaOnStaleZoneDataIsAnHourEarly() {
        val w = ClockChanges.warningFor("America/Chihuahua", chihuahuaWinter, -7 * hour, clockSetByHand = false)!!
        assertEquals("chihuahua", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun chihuahuaOnStaleZoneDataWarnsFromTheHourItsOldDataGoesBack() {
        // 02:00 on 25 October 2026 is 08:00 UTC: old zone data turns to UTC−7 there.
        val w = ClockChanges.warningFor("America/Chihuahua", Instant.parse("2026-10-25T08:00:00Z"), -7 * hour, clockSetByHand = false)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w?.issue)
        assertEquals(true, w?.timesEarly)
    }

    @Test
    fun chihuahuaWithCurrentZoneDataSaysNothing() {
        assertNull(ClockChanges.warningFor("America/Chihuahua", chihuahuaWinter, -6 * hour, clockSetByHand = false))
    }

    @Test
    fun chihuahuaBeforeItsChangeSaysNothing() {
        // 29 October 2022: still summer time, which old and new data agree on.
        val before = Instant.parse("2022-10-29T18:00:00Z")
        assertNull(ClockChanges.warningFor("America/Chihuahua", before, -6 * hour, clockSetByHand = true))
    }

    @Test
    fun theChihuahuaBorderWhoseClocksStillChangeHasNoRow() {
        // America/Ojinaga (and America/Ciudad_Juarez) keep the United States' summer time.
        assertNull(ClockChanges.warningFor("America/Ojinaga", chihuahuaWinter, -7 * hour, clockSetByHand = true))
    }

    // Paraguay: UTC−3 all year from 15 October 2024; old zone data says UTC−4 in the southern winter.
    private val asuncionWinter = Instant.parse("2027-07-15T15:00:00Z")

    @Test
    fun paraguayOnStaleZoneDataIsAnHourEarly() {
        val w = ClockChanges.warningFor("America/Asuncion", asuncionWinter, -4 * hour, clockSetByHand = false)!!
        assertEquals("paraguay", w.change.id)
        assertEquals(ClockIssue.STALE_ZONE_DATA, w.issue)
        assertEquals(true, w.timesEarly)
    }

    @Test
    fun paraguayWithCurrentZoneDataSaysNothing() {
        assertNull(ClockChanges.warningFor("America/Asuncion", asuncionWinter, -3 * hour, clockSetByHand = false))
    }

    @Test
    fun paraguayOnStaleZoneDataInTheSouthernSummerSaysNothing() {
        // 15 December 2026: old data's summer time is the true UTC−3.
        assertNull(ClockChanges.warningFor("America/Asuncion", Instant.parse("2026-12-15T15:00:00Z"), -3 * hour, clockSetByHand = false))
    }

    @Test
    fun paraguayBeforeItsChangeSaysNothing() {
        // 14 October 2024: already on summer time (from 6 October), which old and new data agree on.
        val before = Instant.parse("2024-10-14T15:00:00Z")
        assertNull(ClockChanges.warningFor("America/Asuncion", before, -3 * hour, clockSetByHand = true))
    }

    @Test
    fun aHandSetClockIsNeverFlaggedForTheChangesYearsBeforeThisBuild() {
        // The hand-set check covers the first HAND_SET_DAYS days after a row's `from`. For these
        // rows those days ended in 2020, 2022 and 2024, before any build that carries them (17
        // October 2026 at the earliest), so a clock set by hand on the true offset says nothing.
        val firstBuild = LocalDate(2026, 10, 17)
        for (id in listOf("yukon", "syria", "jordan", "chihuahua", "paraguay")) {
            val change = ClockChanges.table.single { it.id == id }
            assertTrue(change.from.plus(ClockChanges.HAND_SET_DAYS, DateTimeUnit.DAY) < firstBuild, id)
            for (zone in change.zones) {
                for (now in listOf(firstBuild.atStartOfDayIn(TimeZone.UTC), Instant.parse("2027-01-15T12:00:00Z"))) {
                    assertNull(ClockChanges.warningFor(zone, now, change.trueOffsetSeconds, clockSetByHand = true), "$zone $now")
                }
            }
        }
    }

    @Test
    fun theLongStandingChangesWarnUntilNovember2029LikeThe2026Ones() {
        // Phones before Android 10 never get newer zone data unless their maker updates it.
        for (id in listOf("yukon", "syria", "jordan", "chihuahua", "paraguay", "british_columbia", "alberta", "manitoba")) {
            assertEquals(LocalDate(2029, 11, 1), ClockChanges.table.single { it.id == id }.until, id)
        }
        val w = ClockChanges.warningFor("Asia/Damascus", Instant.parse("2029-03-15T09:00:00Z"), 2 * hour, clockSetByHand = false)
        assertEquals(true, w?.timesEarly)
        assertNull(ClockChanges.warningFor("Asia/Damascus", Instant.parse("2029-11-15T09:00:00Z"), 2 * hour, clockSetByHand = false))
    }

    @Test
    fun noZoneIsInTwoRows() {
        // warningFor takes the first row that names the zone: a second would never be read.
        val zones = ClockChanges.table.flatMap { it.zones }
        assertEquals(zones.size, zones.toSet().size, zones.toString())
    }

    @Test
    fun saskatchewanWhichKeptItsClockSaysNothing() {
        // America/Regina has kept UTC−6 all year for decades: no entry, whatever the data.
        assertNull(ClockChanges.warningFor("America/Regina", edmontonWinter, -6 * hour, clockSetByHand = true))
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
