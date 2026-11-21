package world.taqwa.timetables

import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.data.ReachLimits

/**
 * For a test of another rule that takes a real city as its vehicle (London's page, its months, its holes): every reach
 * figure `ReachLimits.kt` measures, accepted, so that whether the owner has ruled on a figure over the ceiling (ruling
 * R270's hold, which `ProvenTest`'s Kazan cases and `CheckStampsTest` hold on their own) does not decide what that test
 * reads. Never the release's or the site's map: those read `ReachRows.RULED` alone.
 */
object AcceptedReach {
    val ALL: Map<String, Map<TimedEvent, Int>> =
        ReachLimits.MINUTES.flatMap { (entry, units) -> units.map { (unit, events) -> "$entry/$unit" to events } }.toMap()
}
