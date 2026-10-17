package world.taqwa.app.feature.prayer

import world.taqwa.app.prayer.ClockChanges
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_clock_region_alberta
import world.taqwa.app.resources.prayer_clock_region_british_columbia
import world.taqwa.app.resources.prayer_clock_region_manitoba
import kotlin.test.Test
import kotlin.test.assertEquals

/** The place the clock sheet names (spec §3.9), for each entry of [ClockChanges.table]. */
class ClockLineTest {

    @Test
    fun canadasThreeChangesNameTheirOwnProvinces() {
        assertEquals(Res.string.prayer_clock_region_british_columbia, clockRegionRes("british_columbia"))
        assertEquals(Res.string.prayer_clock_region_alberta, clockRegionRes("alberta"))
        assertEquals(Res.string.prayer_clock_region_manitoba, clockRegionRes("manitoba"))
    }

    @Test
    fun everyChangeInTheTableNamesAPlaceOfItsOwn() {
        // The mapping falls back to Kazakhstan's name: an entry left out of it would be called Kazakhstan.
        val regions = ClockChanges.table.map { clockRegionRes(it.id) }
        assertEquals(ClockChanges.table.size, regions.toSet().size, ClockChanges.table.map { it.id }.toString())
    }
}
