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
 * - [sunset]: the astronomical sunset at the point, rounded down, for the invariants and the
 *   Maghrib cap. Under a table the authority carries to a town by a printed figure
 *   (`FixedPointMode.TABLE`, ruling R118) it is the table point's sunset plus that figure, the floor
 *   Maghrib is held to; Asr's end in [ends] is then the earlier of it and the user's own sunset.
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
)
