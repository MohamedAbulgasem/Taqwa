package world.taqwa.app.wear.complication

import kotlin.math.min
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules

/**
 * One stretch of a timeline as the sources plan it: what is shown from [from] until [until], both
 * in epoch seconds. [fields] is null for the empty form, the stretch that says no data.
 */
internal data class Step<out F : Any>(val from: Long, val until: Long, val fields: F?)

/**
 * The timelines the sources serve, planned as plain lists and made into complication data
 * afterwards (the sources, `toTimeline`). The library checks a timeline only inside its own update
 * path on the watch, where a timeline that breaks its rules ends the app's process (the spike's
 * report, evidence 4), and nothing on the watch can catch it: so the rules are held here, on a list
 * that a unit test can read, and `ComplicationTimelinesTest` holds them. They are the library's own
 * and the ones this app adds:
 *
 * - **A plan is a run of steps with no gap and no overlap**, the first beginning where it was asked
 *   from. The library shows the shortest of overlapping entries, and a gap shows the default, which
 *   is a period that has gone by.
 * - **A plan ends with exactly one empty step**, a month long, beginning where the answers stop.
 *   The face's runtime ignores `validTimeRange`, and past the last entry it goes on showing the last
 *   one ("in under a minute", a full ring) for good; an entry that says no data is what blanks it.
 *   That entry is a timeline entry, never the default of a timeline that has typed ones: the
 *   library refuses typed entries under a default that says no data. A plan that is only the empty
 *   step (no feed, or one that has run out) is answered by a default that says no data and nothing
 *   else, which has no entries to refuse.
 *
 *   The day dial is the one source whose empty step is typed data and not no data (`DayDialSource`,
 *   ledger Ruling 29): its title and text carry the face's own settings, which hold for as long as
 *   a feed is stored, and its range of 0 to 1 is a dial of nothing, which the Sun style draws as its
 *   empty frame. So it blanks a dial that has run out as an entry of no data would, and the face
 *   keeps the phone's clock and direction. `TimelineShapeTest` holds that mapping.
 * - **A plan holds [PERIODS] prayer periods**, about two days, however much more the feed holds,
 *   because each step's countdown is a dynamic expression of a few kilobytes (3.4 KB of protobuf for
 *   the longest period) that the system is sent whole. A period the date turns over in is two
 *   steps in the next-prayer plan, with the period's ring and countdown in both ([nextPrayer],
 *   ledger Ruling 28), so its plan is [PERIODS] periods and a step more for each such turn (two,
 *   over the plan's two days). The sources are asked again every few hours (their update period)
 *   and whenever the feed is written, and each answer reaches [PERIODS] periods on from where it
 *   was asked: the empty step at the end is what a watch that has been away from its phone for
 *   two days runs into, which is a face that says so and not one that says what is no longer true.
 *
 * What a step says is `ComplicationFields` at the step's first second: a step is as long as that
 * stays so, apart from what the system moves on its own, the ring and the countdown.
 */
internal object ComplicationTimelines {

    /** How many prayer periods a timeline holds, counting the one that is under way. */
    const val PERIODS = 10

    /** How long the empty step at the end lasts: far longer than anything is ever drawn ahead. */
    private const val EMPTY_FOR = 30L * 86_400L

    private const val A_DAY = 86_400L

    /**
     * The next-prayer source's plan: one step for each of the next [PERIODS] prayer periods, the
     * first from [now] to the next start. A period is split where the date turns over inside it if
     * that changes the answer, and it always does for the night's: the ring is counted in minutes
     * from the date's Dhuhr (the Sun style's twelve o'clock), which is the next date's from
     * midnight. In a high-latitude summer the answer changes there too (a Maghrib after the next
     * day's Fajr is counted to until midnight and dropped when the date turns over). A split is not
     * a period of its own: the ring and the countdown run on across it unchanged. The text types
     * take the same plan, where the night's two steps say the same: one plan for all three, at two
     * entries more a plan (ledger Ruling 28).
     */
    fun nextPrayer(feed: WatchFeed, now: Long): List<Step<NextPrayerFields>> {
        val steps = ArrayList<Step<NextPrayerFields>>()
        var at = now
        var periods = 0
        while (periods < PERIODS) {
            val fields = ComplicationFields.nextPrayer(feed, at) ?: break
            val turn = nextDayChange(feed, at)
            val end = if (turn != null && turn < fields.endAt && answerChangesAt(feed, turn, fields)) turn else fields.endAt
            steps += Step(at, end, fields)
            if (end == fields.endAt) periods++
            at = end
        }
        steps += Step(at, at + EMPTY_FOR, null)
        return steps
    }

    /**
     * The day source's plan: the dial changes with the date, and which of its stretches is lit
     * changes with the prayer period, so a step ends at every start and at every turn of the date,
     * until the next-prayer plan ends.
     */
    fun day(feed: WatchFeed, now: Long): List<Step<DayFields>> =
        plan(now, horizon(feed, now), { ComplicationFields.day(feed, it) }) { at ->
            listOfNotNull(WatchRules.moment(feed, at)?.next?.at, nextDayChange(feed, at)).minOrNull()
        }

    /**
     * The day dial's geometry for the Sun style (`ComplicationFields.dial`): it is counted from the
     * date's Dhuhr, so it changes at midnight, and the Fajr and the Isha it marks are the night's
     * own while the night is under way, so it changes at the starts: a step ends at every start
     * and every turn of the date, as the day's does.
     */
    fun dial(feed: WatchFeed, now: Long): List<Step<DialFields>> =
        plan(now, horizon(feed, now), { ComplicationFields.dial(feed, it) }) { at ->
            listOfNotNull(WatchRules.moment(feed, at)?.next?.at, nextDayChange(feed, at)).minOrNull()
        }

    /** The dates source's plan: the date lines change when the date turns over, at the date's own midnight. */
    fun dates(feed: WatchFeed, now: Long): List<Step<DatesFields>> =
        plan(now, horizon(feed, now), { ComplicationFields.dates(feed, it) }) { at -> nextDayChange(feed, at) }

    /**
     * Where the next-prayer plan stops saying anything: the end of its last period, or the feed's
     * last start if that comes first. Every source stops there, so that a face never shows a
     * countdown beside a date that is gone, or the other way about.
     */
    private fun horizon(feed: WatchFeed, now: Long): Long = nextPrayer(feed, now).last().from

    /**
     * Steps from [now] to [horizon], each as long as [changeAfter] says the answer lasts, cut off at
     * [horizon], then the empty step. It ends early, at the instant [fieldsAt] has nothing to say,
     * when the feed runs out.
     */
    private fun <F : Any> plan(
        now: Long,
        horizon: Long,
        fieldsAt: (Long) -> F?,
        changeAfter: (Long) -> Long?,
    ): List<Step<F>> {
        val steps = ArrayList<Step<F>>()
        var at = now
        while (at < horizon) {
            val fields = fieldsAt(at) ?: break
            val end = min(changeAfter(at)?.takeIf { it > at } ?: horizon, horizon)
            steps += Step(at, end, fields)
            at = end
        }
        steps += Step(at, at + EMPTY_FOR, null)
        return steps
    }

    /** Whether the date turning over at [turn] changes what the next-prayer source says inside the period [fields] is. */
    private fun answerChangesAt(feed: WatchFeed, turn: Long, fields: NextPrayerFields): Boolean {
        val after = ComplicationFields.nextPrayer(feed, turn) ?: return true
        return after.startAt != fields.startAt || after.endAt != fields.endAt ||
            after.label != fields.label || after.time != fields.time || after.dhuhrAt != fields.dhuhrAt
    }

    /**
     * The first instant after [after] at which the date, as the rules take it (`WatchRules.today`),
     * is not the one [after] is on, or null if it is still the same two days on, which no date is
     * long enough for. It is found by halving the stretch between, so it is exactly where the
     * rules turn the date over, a date of 23 or 25 hours and the solar-midnight fallback included,
     * and no second source of that rule is kept here.
     */
    private fun nextDayChange(feed: WatchFeed, after: Long): Long? {
        val today = WatchRules.today(feed, after)
        var low = after
        var high = after + 2 * A_DAY
        if (WatchRules.today(feed, high) == today) return null
        while (high - low > 1) {
            val middle = low + (high - low) / 2
            if (WatchRules.today(feed, middle) == today) low = middle else high = middle
        }
        return high
    }
}
