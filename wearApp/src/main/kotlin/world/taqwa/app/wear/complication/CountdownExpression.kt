package world.taqwa.app.wear.complication

import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import java.time.Instant
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchCountdownPhrases
import world.taqwa.app.watch.WatchSpoken

/**
 * The ring and the countdown as dynamic values, which the watch's system evaluates for itself every
 * minute while the app is not running (the spike's report, `docs/superpowers/reports/2026-10-19-wear-spike.md`).
 * A timeline entry carries these for its one period, so that nothing is asked of the app between
 * two starts, and the next entry takes over at the start without the app either.
 *
 * Both are written in the same terms the rules use: whole seconds between two instants, a countdown
 * floored to the minute, and the feed's own phrases and digits. `CountdownExpressionTest` evaluates
 * them at every edge of every minute, and at several milliseconds into each second because the
 * platform's clock has them (the emulator showed a countdown turn over a second early on an
 * expression that did not allow for it), and compares them with `WatchRules.countdown`, which is
 * where the wording is decided.
 */
internal object CountdownExpression {

    /**
     * The ring: where the platform's clock has got from [startAt] to [endAt] (epoch seconds), as
     * minutes from [dhuhrAt], the Sun style's twelve o'clock (`NextPrayerFields.dialStart` to
     * `dialEnd`, which are the complication's range). The whole seconds since the start are held to
     * the period on either side, so that an evaluation a little early or late draws an empty ring or
     * a full one and never a value outside the range, and are added to the start's minutes as
     * `NextPrayerFields.dialEnd` adds the period's, so that the value at the end is the range's end
     * to the bit.
     */
    fun ring(startAt: Long, endAt: Long, dhuhrAt: Long): DynamicFloat {
        val total = (endAt - startAt).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val elapsed = DynamicInstant.withSecondsPrecision(Instant.ofEpochSecond(startAt))
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
            .toIntSeconds()
        val held = DynamicInt32.onCondition(elapsed.gt(total)).use(total)
            .elseUse(DynamicInt32.onCondition(elapsed.lt(0)).use(0).elseUse(elapsed))
        return held.asFloat().div(60f).plus((startAt - dhuhrAt).toFloat() / 60f)
    }

    /**
     * The time left to [endAt] (epoch seconds) in the feed's [phrases] and in the digits that follow
     * [zero], the feed's `digitZero`. [longestSecondsLeft] is the most it will ever have to count
     * from, the seconds left when its entry begins, and bounds the hours it can be asked to write.
     * The hours are also held below 100 ([MAX_HOURS]), as [number] needs: no period between two
     * starts is a day long, so only a broken feed could ask for more, and it is then told 99.
     *
     * The digits are mapped here and not left to `DynamicInt32.format()`, which writes the digits of
     * the language the watch's system runs in and never the feed's: under an Arabic feed on an
     * English watch it wrote `بعد 2 د`. A number is a chain of conditions, one for each value its
     * digit can take, and the expression has no way to share a part of itself, so every condition
     * carries its own copy of the time arithmetic; the chains are bounded by what the period can
     * need (the hours left never exceed its length, the minutes' tens never exceed 5, and a number
     * below ten has no tens) so that the longest period's countdown, 8 h 9 min, is 3.4 KB of
     * protobuf and not ten.
     */
    fun countdown(
        phrases: WatchCountdownPhrases,
        zero: Char,
        endAt: Long,
        longestSecondsLeft: Long,
    ): DynamicString {
        val left = TimeLeft(endAt, longestSecondsLeft)
        val hours = left.hours
        val minutes = left.minutes
        val maxHours = left.maxHours

        return DynamicString.onCondition(left.seconds.lt(60)).use(phrases.underAMinute)
            .elseUse(
                DynamicString.onCondition(hours.eq(0)).use(fill(phrases.minutes, number(minutes, 59, zero)))
                    .elseUse(
                        DynamicString.onCondition(minutes.eq(0)).use(fill(phrases.hours, number(hours, maxHours, zero)))
                            .elseUse(
                                fill(phrases.hoursMinutes, number(hours, maxHours, zero), number(minutes, 59, zero)),
                            ),
                    ),
            )
    }

    /**
     * The countdown to [name], whose clock time is [clock], as a screen reader is told it, in the
     * feed's [spoken] words: `Asr in 1 hour 12 minutes, at 16:10`, the time left moving as
     * [countdown]'s does and counted the same way, each number in the phrase the phone chose for it
     * (`WatchCount`) and in the digits that follow [zero]. `WatchRules.spokenCountdown` is where the
     * wording is decided, and `SpokenTest` holds the two together at every step of two days.
     *
     * A phrase is split about its number, and only the words before and after it are chosen by a
     * chain of conditions (`n <= upTo` for each run of numbers that differs from the next), so that
     * the number, the costly part, is written once for each form of the sentence, as in [countdown].
     * A list whose phrases do not each hold one number is chosen whole, phrase by phrase.
     */
    fun spoken(
        spoken: WatchSpoken,
        zero: Char,
        name: String,
        clock: String,
        endAt: Long,
        longestSecondsLeft: Long,
    ): DynamicString {
        val left = TimeLeft(endAt, longestSecondsLeft)
        // Past the first minute the hours and the minutes that are said are at least 1 where they
        // are said at all: the minutes alone below an hour, the hours alone on the hour.
        fun hours() = counted(spoken.hours, left.hours, left.maxHours, zero)
        fun minutes() = counted(spoken.minutes, left.minutes, 59, zero)
        val duration = DynamicString.onCondition(left.hours.eq(0)).use(minutes())
            .elseUse(
                DynamicString.onCondition(left.minutes.eq(0)).use(hours())
                    .elseUse(fill(spoken.hoursMinutes, hours(), minutes())),
            )
        val prayer = DynamicString.constant(name)
        val time = DynamicString.constant(clock)
        return DynamicString.onCondition(left.seconds.lt(60)).use(fill(spoken.countdownSoon, prayer, time))
            .elseUse(fill(spoken.countdown, prayer, duration, time))
    }

    /**
     * [n], a number from 1 to [max], in the phrase [counts] gives it and written in the digits that
     * follow [zero]. Only the runs of [counts] that reach from 1 to [max] are asked for.
     */
    private fun counted(counts: List<WatchCount>, n: DynamicInt32, max: Int, zero: Char): DynamicString {
        // The phrase for every number that can be asked for, as runs: the last number of each run
        // and its phrase, in order. Past the list's last entry its phrase holds.
        val runs = mutableListOf<Pair<Int, String>>()
        for (k in 1..max) {
            val phrase = (counts.firstOrNull { k <= it.upTo } ?: counts.last()).phrase
            if (runs.isNotEmpty() && runs.last().second == phrase) runs[runs.lastIndex] = k to phrase else runs += k to phrase
        }
        val written = number(n, max, zero)
        if (runs.any { it.second.split(FIRST).size != 2 }) {
            return chosen(n, runs.map { (upTo, phrase) -> upTo to fill(phrase, written) })
        }
        val before = chosen(n, merged(runs.map { (upTo, phrase) -> upTo to phrase.substringBefore(FIRST) }).map { (upTo, words) -> upTo to DynamicString.constant(words) })
        val after = chosen(n, merged(runs.map { (upTo, phrase) -> upTo to phrase.substringAfter(FIRST) }).map { (upTo, words) -> upTo to DynamicString.constant(words) })
        return before.concat(written).concat(after)
    }

    /** [runs] with each run that has the same words as the next one joined to it. */
    private fun merged(runs: List<Pair<Int, String>>): List<Pair<Int, String>> {
        val joined = mutableListOf<Pair<Int, String>>()
        for ((upTo, words) in runs) {
            if (joined.isNotEmpty() && joined.last().second == words) joined[joined.lastIndex] = upTo to words else joined += upTo to words
        }
        return joined
    }

    /** The value of the first of [runs] whose last number is [n] or more, the last run's beyond them. */
    private fun chosen(n: DynamicInt32, runs: List<Pair<Int, DynamicString>>): DynamicString {
        var chosen = runs.last().second
        for (i in runs.lastIndex - 1 downTo 0) {
            chosen = DynamicString.onCondition(n.lte(runs[i].first)).use(runs[i].second).elseUse(chosen)
        }
        return chosen
    }

    /**
     * The time left to [endAt] (epoch seconds), as dynamic whole seconds, hours and minutes: the
     * arithmetic [countdown] and [spoken] share, which each condition that reads it carries a copy of.
     */
    private class TimeLeft(endAt: Long, longestSecondsLeft: Long) {
        // The time left is read as how long ago the end was, negated. The platform's clock keeps its
        // milliseconds ("seconds precision" is how often it is read, and not that it is rounded),
        // so at 14:58:00.2 the time left to 16:10:00 is 71 min 59.8 s, and a countdown made of its
        // floor would read 1 h 11 min a second before the rules do. A negative duration keeps its
        // whole seconds as the floor (-71 min 59.8 s is -4320 s and 0.2 s over), and negated that is
        // the end less the whole second now is in: exactly what the rules count, at any millisecond.
        private val sinceEnd = DynamicInstant.withSecondsPrecision(Instant.ofEpochSecond(endAt))
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
        val seconds: DynamicInt32 = sinceEnd.toIntSeconds().times(-1)

        // toIntMinutes divides those whole seconds, truncating toward zero: the floor, once negated.
        private val totalMinutes = sinceEnd.toIntMinutes().times(-1)
        private val reach = (longestSecondsLeft + EARLY_SLACK) / 3_600L

        /** The most hours this entry can be asked to write, at least 1 and below 100. */
        val maxHours: Int = reach.coerceIn(1L, MAX_HOURS.toLong()).toInt()
        private val anyHours = totalMinutes.div(60)
        val hours: DynamicInt32 = if (reach <= MAX_HOURS) {
            anyHours
        } else {
            DynamicInt32.onCondition(anyHours.gt(MAX_HOURS)).use(MAX_HOURS).elseUse(anyHours)
        }
        val minutes: DynamicInt32 = totalMinutes.rem(60)
    }

    /**
     * [n], a number from 0 to [max] (below 100), in the digit set that follows [zero] and with no
     * leading zero: the tens digit, or nothing below ten, then the ones digit.
     */
    private fun number(n: DynamicInt32, max: Int, zero: Char): DynamicString {
        if (max < 10) return digit(n, max, zero)
        val tens = DynamicString.onCondition(n.lt(10)).use("").elseUse(digit(n.div(10), max / 10, zero))
        return tens.concat(digit(n.rem(10), 9, zero))
    }

    /** One digit known to be from 0 to [max], as a chain of [max] conditions ending in [max]'s own character. */
    private fun digit(d: DynamicInt32, max: Int, zero: Char): DynamicString {
        var chosen = DynamicString.constant((zero + max).toString())
        for (k in max - 1 downTo 0) {
            chosen = DynamicString.onCondition(d.eq(k)).use((zero + k).toString()).elseUse(chosen)
        }
        return chosen
    }

    /**
     * [template] with `%1$s`, `%2$s` and `%3$s` replaced by [first], [second] and [third], the pieces
     * joined as one string.
     */
    private fun fill(
        template: String,
        first: DynamicString,
        second: DynamicString? = null,
        third: DynamicString? = null,
    ): DynamicString {
        var result: DynamicString? = null
        fun add(part: DynamicString) {
            result = result?.concat(part) ?: part
        }
        val parts = listOf(FIRST to first, SECOND to second, THIRD to third)
        var from = 0
        while (true) {
            val next = parts.map { (marker, part) -> Triple(template.indexOf(marker, from), marker, part) }
                .filter { it.first >= 0 }
                .minByOrNull { it.first } ?: break
            val (at, marker, part) = next
            if (at > from) add(DynamicString.constant(template.substring(from, at)))
            add(part ?: DynamicString.constant(""))
            from = at + marker.length
        }
        if (from < template.length) add(DynamicString.constant(template.substring(from)))
        return result ?: DynamicString.constant("")
    }

    /**
     * An entry shown a minute before its period begins, by a system that evaluates early, has that
     * minute more to count than the period is long; the hours must not run out a minute early.
     */
    private const val EARLY_SLACK = 60L

    /** The most hours a countdown writes: two digits, all [number] can make. */
    private const val MAX_HOURS = 99

    private const val FIRST = "%1\$s"
    private const val SECOND = "%2\$s"
    private const val THIRD = "%3\$s"
}
