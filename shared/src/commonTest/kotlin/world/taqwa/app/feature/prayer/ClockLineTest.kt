package world.taqwa.app.feature.prayer

import world.taqwa.app.prayer.ClockChanges
import world.taqwa.app.prayer.ClockIssue
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_clock_kept_manual_early
import world.taqwa.app.resources.prayer_clock_kept_manual_late
import world.taqwa.app.resources.prayer_clock_kept_stale_early
import world.taqwa.app.resources.prayer_clock_kept_stale_late
import world.taqwa.app.resources.prayer_clock_manual_early
import world.taqwa.app.resources.prayer_clock_manual_late
import world.taqwa.app.resources.prayer_clock_region_alberta
import world.taqwa.app.resources.prayer_clock_region_british_columbia
import world.taqwa.app.resources.prayer_clock_region_chihuahua
import world.taqwa.app.resources.prayer_clock_region_jordan
import world.taqwa.app.resources.prayer_clock_region_manitoba
import world.taqwa.app.resources.prayer_clock_region_paraguay
import world.taqwa.app.resources.prayer_clock_region_syria
import world.taqwa.app.resources.prayer_clock_region_yukon
import world.taqwa.app.resources.prayer_clock_stale_early
import world.taqwa.app.resources.prayer_clock_stale_late
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The place the clock sheet names and the sentence it picks (spec §3.9), for each entry of
 * [ClockChanges.table].
 */
class ClockLineTest {

    private fun change(id: String) = ClockChanges.table.single { it.id == id }

    @Test
    fun canadasThreeChangesNameTheirOwnProvinces() {
        assertEquals(Res.string.prayer_clock_region_british_columbia, clockRegionRes("british_columbia"))
        assertEquals(Res.string.prayer_clock_region_alberta, clockRegionRes("alberta"))
        assertEquals(Res.string.prayer_clock_region_manitoba, clockRegionRes("manitoba"))
    }

    @Test
    fun theChangesYearsAgoNameTheirOwnPlaces() {
        assertEquals(Res.string.prayer_clock_region_yukon, clockRegionRes("yukon"))
        assertEquals(Res.string.prayer_clock_region_syria, clockRegionRes("syria"))
        assertEquals(Res.string.prayer_clock_region_jordan, clockRegionRes("jordan"))
        assertEquals(Res.string.prayer_clock_region_chihuahua, clockRegionRes("chihuahua"))
        assertEquals(Res.string.prayer_clock_region_paraguay, clockRegionRes("paraguay"))
    }

    @Test
    fun everyChangeInTheTableNamesAPlaceOfItsOwn() {
        // The mapping falls back to Kazakhstan's name: an entry left out of it would be called Kazakhstan.
        val ids = ClockChanges.table.map { it.id }.distinct()
        val regions = ids.map { clockRegionRes(it) }
        assertEquals(ids.size, regions.toSet().size, ids.toString())
    }

    @Test
    fun aPlaceWhoseClocksWentBackChangedItsClocks() {
        // Morocco and Kazakhstan moved their clocks on their dates: "changed its clocks on".
        for (id in listOf("morocco", "kazakhstan")) {
            assertEquals(Res.string.prayer_clock_stale_late, clockBodyRes(change(id), ClockIssue.STALE_ZONE_DATA, timesEarly = false), id)
            assertEquals(Res.string.prayer_clock_stale_early, clockBodyRes(change(id), ClockIssue.STALE_ZONE_DATA, timesEarly = true), id)
            assertEquals(Res.string.prayer_clock_manual_late, clockBodyRes(change(id), ClockIssue.SET_BY_HAND, timesEarly = false), id)
            assertEquals(Res.string.prayer_clock_manual_early, clockBodyRes(change(id), ClockIssue.SET_BY_HAND, timesEarly = true), id)
        }
    }

    @Test
    fun aPlaceThatStoppedGoingBackHasKeptOneTimeAllYear() {
        // Their dates are the day the clocks did not go back (Paraguay's a law), no clock movement:
        // "has kept one time all year since", never "changed its clocks on".
        for (id in listOf("british_columbia", "alberta", "manitoba", "yukon", "syria", "jordan", "chihuahua", "paraguay")) {
            assertEquals(Res.string.prayer_clock_kept_stale_early, clockBodyRes(change(id), ClockIssue.STALE_ZONE_DATA, timesEarly = true), id)
            assertEquals(Res.string.prayer_clock_kept_stale_late, clockBodyRes(change(id), ClockIssue.STALE_ZONE_DATA, timesEarly = false), id)
            assertEquals(Res.string.prayer_clock_kept_manual_early, clockBodyRes(change(id), ClockIssue.SET_BY_HAND, timesEarly = true), id)
            assertEquals(Res.string.prayer_clock_kept_manual_late, clockBodyRes(change(id), ClockIssue.SET_BY_HAND, timesEarly = false), id)
        }
    }

    @Test
    fun everyRowPicksASentenceOfItsKind() {
        // No row falls through to the other kind's wording.
        for (change in ClockChanges.table) {
            val stale = clockBodyRes(change, ClockIssue.STALE_ZONE_DATA, timesEarly = change.shiftHours > 0)
            val expected = if (change.shiftHours > 0) Res.string.prayer_clock_kept_stale_early else Res.string.prayer_clock_stale_late
            assertEquals(expected, stale, change.id)
        }
    }
}
