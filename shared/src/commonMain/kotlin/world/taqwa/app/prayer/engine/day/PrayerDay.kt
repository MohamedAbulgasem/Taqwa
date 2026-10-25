package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.Prayer
import kotlin.time.Instant

/**
 * The engine's answer for one civil [date] (spec §3.3). Every instant is a whole minute, rounded
 * once inside the engine: starts up, sunrise and the end of eating down.
 *
 * - [fajr] … [isha]: when each prayer begins in the authority's own words; [asrOther] is the other
 *   school's Asr.
 * - [sunset]: the astronomical sunset at the point, rounded down, for the invariants. Under a table
 *   the authority carries to a town by a printed figure (`FixedPointMode.TABLE`, ruling R118) it is
 *   the table point's sunset plus that figure, the floor Maghrib is held to; Asr's end in [ends] is
 *   then the earlier of it and the user's own sunset.
 * - [endOfEating]: when the fast begins, never after [fajr]; [imsak]: the authority's printed
 *   precaution, where it has one.
 * - [earliestStart] (cautious places) is filled by [Cautious]; [ends] by [DayComputer] and
 *   [Cautious], Isha's once the next day is known ([Ends.withNextDay]); [setByRule] and [polar] by
 *   the high-latitude rules; [repaired] when [Invariants.repair] had to restore the order.
 * - [polar]: the sun does not rise or set at the place and some times follow the nearest latitude
 *   where it does; a day an authority's clock rule gives whole is not polar (ruling R82).
 * - [notFollowed]: the events whose authority time the day declares it does not show, because no
 *   day in order can (an authority's clock rule putting Fajr after the sun has risen, or printing
 *   the sun's lowest point as its sunrise; ruling R82). The gate counts those cells as declared,
 *   neither early nor late, and About says so.
 * - [maghribHeld]: the method's own day-length rule
 *   ([world.taqwa.app.prayer.engine.method.TimetableMethod.dayAroundDhuhrMinutes], Diyanet's
 *   five-hour winter day in the far north) set the Maghrib shown, later than the sun gives it;
 *   [maghribBeforeHold] is then the Maghrib the sun gives (the sunset plus the method's own minutes
 *   and margins), null on every other day. A cautious day is held only where its Maghrib is the
 *   most-followed member's held minute and nothing the sun gives reaches it (ruling R155).
 *   [maghribBySun] reads the two with [setByRule].
 */
data class PrayerDay(
    val date: LocalDate,
    val fajr: Instant, val sunrise: Instant, val dhuhr: Instant, val asr: Instant, val asrOther: Instant,
    val maghrib: Instant, val isha: Instant, val sunset: Instant,
    val endOfEating: Instant, val imsak: Instant?,
    val earliestStart: Map<Prayer, Instant> = emptyMap(),
    val ends: Map<Prayer, Instant> = emptyMap(),
    val setByRule: Set<Prayer> = emptySet(),
    val polar: Boolean = false,
    val repaired: Boolean = false,
    val methodId: String,
    val notFollowed: Set<Prayer> = emptySet(),
    val maghribHeld: Boolean = false,
    val maghribBeforeHold: Instant? = null,
) {
    /**
     * The Maghrib this day gives from the sun (ruling R155): [maghribBeforeHold] where a day-length
     * rule held it ([maghribHeld]), else [maghrib] itself; null where a rule set it instead
     * ([setByRule]: a polar day's borrowed Maghrib, a clock rule's estimate). A cautious day counts a
     * member's Maghrib at this minute.
     */
    val maghribBySun: Instant?
        get() = when {
            Prayer.MAGHRIB in setByRule -> null
            maghribHeld -> maghribBeforeHold
            else -> maghrib
        }
}
