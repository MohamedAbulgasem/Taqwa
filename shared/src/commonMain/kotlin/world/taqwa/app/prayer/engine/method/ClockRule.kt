package world.taqwa.app.prayer.engine.method

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.Prayer

/**
 * An authority's own clock rule (spec §3.8 rule 1): where its calendar leaves the sun in a place's
 * polar seasons (IRN Tromsø's "Makkah time", ruling R82), its own times for a date. The day computer
 * applies it last, after the daily rate limit (the times are the authority's own, as DUM RT's white
 * nights are), at every point it computes at: each start the latest, each end the earliest.
 *
 * The rule itself lives in the registry beside its authority; this is only the hook. An
 * implementation is a data class (or prints its parameters), so that a method's `toString`, which
 * the proof stamps fingerprint, is the same on every run.
 */
interface ClockRule {
    /** The rule's times on [date] at [point] in [zone]. */
    fun on(date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTimes

    /**
     * The About screen's string key saying what of the authority's calendar Taqwa does not follow
     * ([ClockTimes.notFollowed], and a Fajr the day cannot show before sunrise), or null.
     */
    val notFollowedNoteKey: String? get() = null
}

/** One time a [ClockRule] gives, in unrounded UTC epoch seconds; [estimated] where it is not a sign of the sun at the point. */
data class ClockTime(val epochSeconds: Double, val estimated: Boolean)

/**
 * A [ClockRule]'s times for one date at one point; a null time leaves that event to the method.
 * What the day shows (DayComputer):
 *
 * - [fajr]: shown where it is before the sunrise shown; otherwise the day keeps its own Fajr before
 *   that sunrise and declares Fajr not followed.
 * - [sunrise]: a ceiling: the earlier of it and the method's own; alone where the sun neither rises
 *   nor sets at the point (a polar day).
 * - [dhuhr], [asrStandard], [asrHanafi]: shown as they are.
 * - [maghrib]: the later of it and the method's own; alone where [noSunset].
 * - [isha]: shown where it is after the Maghrib shown; otherwise the method's own, after that Maghrib.
 * - [endOfEating]: the dawn the fast begins at (the authority's own end, spec §3.3): the end of
 *   eating where the rule's Fajr is shown, never after it; otherwise the earlier of it and the
 *   method's own.
 * - [sunset]: the sunset the day keeps for its invariants where [noSunset] (the authority's own).
 * - [noSunset]: the sun does not set at the point on this date.
 * - [notFollowed]: events whose printed time the rule does not give here (the day declares them).
 *
 * A time shown from the rule is set by rule exactly where it is [ClockTime.estimated].
 */
data class ClockTimes(
    val fajr: ClockTime?,
    val sunrise: ClockTime?,
    val dhuhr: ClockTime?,
    val asrStandard: ClockTime?,
    val asrHanafi: ClockTime?,
    val maghrib: ClockTime?,
    val isha: ClockTime?,
    val endOfEating: Double?,
    val sunset: Double?,
    val noSunset: Boolean,
    val notFollowed: Set<Prayer> = emptySet(),
)
