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
 *   forward (British Columbia, Alberta, Manitoba, Yukon, Syria, Jordan, Chihuahua, Paraguay). It
 *   picks the sheet's sentence too: a −1 place "changed its clocks on" [from]; a +1 place "has kept
 *   one time all year since" it, because its [from] is the day the clocks did not go back, not a
 *   clock movement.
 * - [until]: when the stale-zone-data check stops; by then few phones still in use lack the new
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
 * be off. [timesEarly]: an hour early (the places that stayed forward, on old zone data) rather
 * than late (Morocco, Kazakhstan).
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
        // Alberta keeps UTC−6 all year from 1 November 2026, when it would have gone back to
        // UTC−7. America/Edmonton's clocks also keep the east of British Columbia (Cranbrook) and
        // the Northwest Territories' Yellowknife, whose America/Yellowknife is a link to it; the
        // territories' west, America/Inuvik, keeps UTC−6 from the same day.
        ClockChange(
            id = "alberta",
            zones = setOf("America/Edmonton", "America/Yellowknife", "America/Inuvik"),
            from = LocalDate(2026, 11, 1),
            trueOffsetSeconds = -6 * 3600,
            shiftHours = 1,
            until = LocalDate(2029, 11, 1),
        ),
        // Manitoba keeps UTC−5 all year from 1 November 2026, when it would have gone back to
        // UTC−6. America/Winnipeg's clocks also keep north-western Ontario (Kenora), whose
        // America/Rainy_River is a link to it.
        ClockChange(
            id = "manitoba",
            zones = setOf("America/Winnipeg", "America/Rainy_River"),
            from = LocalDate(2026, 11, 1),
            trueOffsetSeconds = -5 * 3600,
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
        // The places below stopped going back an hour in winter years ago; old zone data still
        // goes back every year, so on it every time is drawn an hour early each winter. Their rows
        // run to 1 November 2029 with the 2026 ones, more than three years after the change,
        // because a phone before Android 10 (Android 9 ships tzdata 2018e, Android 8 older) never
        // gets newer zone data unless its maker updates it. A clock set by hand is flagged only in
        // the first [HAND_SET_DAYS] days after a change, long past for these.
        //
        // Yukon has kept UTC−7 all year since 1 November 2020, when it would have gone back to
        // UTC−8. America/Dawson (Dawson City) keeps Whitehorse's clock.
        ClockChange(
            id = "yukon",
            zones = setOf("America/Whitehorse", "America/Dawson"),
            from = LocalDate(2020, 11, 1),
            trueOffsetSeconds = -7 * 3600,
            shiftHours = 1,
            until = LocalDate(2029, 11, 1),
        ),
        // Syria has kept UTC+3 all year since 28 October 2022, when it would have gone back to UTC+2.
        ClockChange(
            id = "syria",
            zones = setOf("Asia/Damascus"),
            from = LocalDate(2022, 10, 28),
            trueOffsetSeconds = 3 * 3600,
            shiftHours = 1,
            until = LocalDate(2029, 11, 1),
        ),
        // Jordan has kept UTC+3 all year since 28 October 2022, when it would have gone back to UTC+2.
        ClockChange(
            id = "jordan",
            zones = setOf("Asia/Amman"),
            from = LocalDate(2022, 10, 28),
            trueOffsetSeconds = 3 * 3600,
            shiftHours = 1,
            until = LocalDate(2029, 11, 1),
        ),
        // Chihuahua has kept UTC−6 all year since 30 October 2022, when Mexico dropped summer time
        // and it would have gone back to UTC−7. Its border towns (America/Ojinaga,
        // America/Ciudad_Juarez) still change their clocks with the United States, so no row fits.
        ClockChange(
            id = "chihuahua",
            zones = setOf("America/Chihuahua"),
            from = LocalDate(2022, 10, 30),
            trueOffsetSeconds = -6 * 3600,
            shiftHours = 1,
            until = LocalDate(2029, 11, 1),
        ),
        // Paraguay has kept UTC−3 all year since 15 October 2024; old zone data goes back to UTC−4
        // every March.
        ClockChange(
            id = "paraguay",
            zones = setOf("America/Asuncion"),
            from = LocalDate(2024, 10, 15),
            trueOffsetSeconds = -3 * 3600,
            shiftHours = 1,
            until = LocalDate(2029, 11, 1),
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
