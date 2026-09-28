package world.taqwa.app.prayer

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A change to a place's clock that a phone may not know about yet (spec §3.9).
 *
 * - [zones]: the IANA zones it applies to.
 * - [from]: the local date from which [trueOffsetSeconds] is the offset all year.
 * - [shiftHours]: which way the clocks moved: −1 when they went back (Morocco), +1 when they stayed
 *   forward (British Columbia).
 * - [until]: when the stale-zone-data check stops; by then every phone still in use has the new
 *   zone data. A clock set by hand is flagged for a shorter time, [ClockChanges.HAND_SET_DAYS].
 */
data class ClockChange(
    val id: String,
    val zones: Set<String>,
    val from: LocalDate,
    val trueOffsetSeconds: Int,
    val shiftHours: Int,
    val until: LocalDate,
)

/** Why the Prayer screen's clock line shows (spec §3.9). */
enum class ClockIssue {
    /** The phone's zone data gives the place's zone an offset other than the true one. */
    STALE_ZONE_DATA,

    /**
     * The phone's clock is set by hand (Android's automatic time is off) within
     * [ClockChanges.HAND_SET_DAYS] of a change in the place's own zone.
     */
    SET_BY_HAND,
}

/**
 * The clock line's reason: which [change], what is wrong, and which way the times on the phone may
 * be off. [timesEarly]: an hour early (British Columbia on old zone data) rather than late
 * (Morocco, Kazakhstan).
 */
data class ClockWarning(val change: ClockChange, val issue: ClockIssue, val timesEarly: Boolean)

/**
 * The small table of clock changes shared with the website (spec §3.9), and the one check the
 * Prayer screen makes against it. Plain Kotlin: the website's generator compiles it too.
 *
 * The screen draws the engine's instants in the place's zone as the phone's own zone data gives
 * it. Where that data predates a change, every time on screen, and the phone's own clock, is an
 * hour off; where the clock was set by hand to look right, the alerts are.
 */
object ClockChanges {

    /**
     * How long after a change a clock set by hand is flagged: long enough for anyone who moved the
     * clock to hide the old zone data to open the app, short enough that a phone set by hand for
     * any other reason is not told about it for years.
     */
    const val HAND_SET_DAYS = 60

    val table: List<ClockChange> = listOf(
        // Morocco moves to UTC+0 all year from 20 September 2026 (from UTC+1 outside Ramadan).
        ClockChange(
            id = "morocco",
            zones = setOf("Africa/Casablanca", "Africa/El_Aaiun"),
            from = LocalDate(2026, 9, 20),
            trueOffsetSeconds = 0,
            shiftHours = -1,
            until = LocalDate(2029, 9, 20),
        ),
        // British Columbia keeps UTC−7 all year from 1 November 2026, when it would have gone back
        // to UTC−8.
        ClockChange(
            id = "british_columbia",
            zones = setOf("America/Vancouver"),
            from = LocalDate(2026, 11, 1),
            trueOffsetSeconds = -7 * 3600,
            shiftHours = 1,
            until = LocalDate(2029, 11, 1),
        ),
        // Kazakhstan's east moved from UTC+6 to UTC+5 on 1 March 2024; the west already kept +5.
        ClockChange(
            id = "kazakhstan",
            zones = setOf("Asia/Almaty", "Asia/Qostanay"),
            from = LocalDate(2024, 3, 1),
            trueOffsetSeconds = 5 * 3600,
            shiftHours = -1,
            until = LocalDate(2027, 3, 1),
        ),
    )

    /**
     * The warning for a place in [zoneId] at [now], or null.
     *
     * [phoneOffsetSeconds] is the offset the phone's zone data gives [zoneId] at [now];
     * [clockSetByHand] is Android's automatic-time setting read backwards, null where the platform
     * cannot tell (iOS). The zone data is checked first, from the change until [ClockChange.until]:
     * when it is stale, that is the fix to name. A clock set by hand is flagged only in the first
     * [HAND_SET_DAYS] days after a change in [zoneId] itself.
     */
    fun warningFor(zoneId: String, now: Instant, phoneOffsetSeconds: Int, clockSetByHand: Boolean?): ClockWarning? {
        val change = table.firstOrNull { change ->
            zoneId in change.zones && trueLocalDate(change, now).let { it >= change.from && it < change.until }
        } ?: return null
        if (phoneOffsetSeconds != change.trueOffsetSeconds) {
            // Drawn at a smaller offset than the true one, every time reads earlier than it is.
            return ClockWarning(change, ClockIssue.STALE_ZONE_DATA, timesEarly = phoneOffsetSeconds < change.trueOffsetSeconds)
        }
        val recent = trueLocalDate(change, now) < change.from.plus(HAND_SET_DAYS, DateTimeUnit.DAY)
        if (clockSetByHand == true && recent) {
            // A clock set by hand to hide the old zone data is off the other way from the change:
            // clocks that went back leave its alerts an hour late.
            return ClockWarning(change, ClockIssue.SET_BY_HAND, timesEarly = change.shiftHours > 0)
        }
        return null
    }

    private fun trueLocalDate(change: ClockChange, now: Instant): LocalDate =
        (now + change.trueOffsetSeconds.seconds).toLocalDateTime(TimeZone.UTC).date
}
