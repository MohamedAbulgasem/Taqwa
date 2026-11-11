package world.taqwa.app.wear.tile

import androidx.wear.protolayout.expression.DynamicBuilders.DynamicBool
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.expression.RequiresSchemaVersion
import java.time.Instant

/**
 * The Tile's countdown and its spoken sentence as dynamic values, which the watch's system moves on
 * every minute with the app's process dead (the spike, P3.8 Step 0, killed it and watched).
 *
 * Plan 1's countdown (`CountdownExpression`) counts a whole period in one expression, spelling each
 * digit out with its own chain of conditions; for the night's 8 h 9 min it is 380 dynamic nodes,
 * and with its sentence 796. The Tile renderer gives a layout 200 (the spike: fixed values are free,
 * every operation costs one, and no repeated part of an expression is shared), and over them it
 * drops the dynamic value and keeps the static one, a countdown that never moves (erratum p3 #6).
 *
 * So the Tile's entries are short ([TilePlan.STEP_SECONDS]), and each says its countdown as a chain
 * of the rules' own words, one link for each minute it can be shown at ([TileNext.minutes]): "while
 * no more than m minutes are left, say the words for m". A link is five dynamic nodes (the
 * condition, its comparison, and the minutes of the time since the start, read from the platform's
 * clock) and three fixed values (the start, the minutes and the words), eight as Google counts
 * them, and the Tile is held to that count (`TileLayout.QUOTA`). Every language, digit set and
 * plural is the rules' by construction, since every word is `WatchRules.compactCountdown`'s or
 * `spokenCountdown`'s for its minute.
 *
 * The time is read as `CountdownExpression` reads it: how long ago the start was, in whole minutes,
 * which the platform's duration floors on its whole seconds. Since the start, a negative duration
 * of -71 min 59.8 s is -4320 s and 0.2 s over, -72 minutes: 72 minutes left, exactly what the rules
 * count at 14:58:00.2 for a start at 16:10, at any millisecond.
 *
 * **Past its last link a chain says the start's time** ([TileNext.clock], call 6's form, "16:10"
 * and "Asr, 16:10"), true however late it is shown, never a countdown that has passed (Ruling 65
 * item 3): a renderer may show an entry later than the plan allows for (deep idle held one on
 * emulator-5558, review round 4). Once fewer minutes are left than its last link counts, one
 * link more says it; a chain whose last link is "in under a minute", where the time left reads 0
 * minutes as it does past the start, ends on the whole seconds since the start instead. Either is
 * eight nodes a chain, and five-minute entries keep the heaviest, both chains with theirs, within
 * the quota ([TilePlan.STEP_SECONDS]).
 *
 * **Schema 1.2.** Every value here is a dynamic expression, and every call it is built of needs
 * ProtoLayout schema 1.2 (`@RequiresSchemaVersion(major = 1, minor = 200)` in
 * protolayout-expression 1.4.2): only a renderer that has it is sent them (`TileScreen.dynamic`).
 * A renderer that drops one (over its quota, on an evaluation error, or in the frame before it
 * binds it) keeps its static value, which is the start's time too (`TileLayout`, Ruling 65 item 2).
 */
internal object TileCountdown {

    /**
     * The countdown of [next] ("in 1h 12m"), moving from minute to minute, and the start's time once
     * it has none left, led by a mark of the feed's direction (`directed`), so that the line's text
     * view sets a time of digits alone against the name as it sets the words (`TileLayout`).
     */
    @RequiresSchemaVersion(major = 1, minor = 200)
    fun text(next: TileNext): DynamicString =
        chain(next.endAt, next.minutes.map { it.minutesLeft to it.text }, next.clock, shown = directed(next.clock, next.rtl))

    /** The sentence a screen reader is told of [next] ("Asr in 1 hour 12 minutes, at 16:10", then "Asr, 16:10"); null for a feed with no spoken words. */
    @RequiresSchemaVersion(major = 1, minor = 200)
    fun spoken(next: TileNext): DynamicString? {
        if (next.minutes.any { it.spoken == null }) return null
        return chain(next.endAt, next.minutes.map { it.minutesLeft to it.spoken!! }, next.clockSentence)
    }

    /**
     * [words], each the words for its number of minutes left, ascending and one for each minute, as
     * one chain: the first link whose minutes the time left has not passed is said. Runs of the
     * same words are one link, and more minutes left than the last are the last's words. Fewer
     * than the first's, and from the start on, are [clock], the start's time, said as [shown]: a
     * minute of [words] that says it already (the last minute of an entry that does not end at the
     * start) is said by the same link, around the chain.
     *
     * What every other link begins and ends with ("Asr in 1 hour 1" and " minutes, at 16:10") is
     * said once, around the links, so that a link carries only what differs: the timeline sends
     * each entry whole, and an entry's links are most of its weight. It costs two dynamic nodes,
     * the two joins.
     */
    @RequiresSchemaVersion(major = 1, minor = 200)
    private fun chain(endAt: Long, words: List<Pair<Int, String>>, clock: String, shown: String = clock): DynamicString {
        val runs = mutableListOf<Pair<Int, String>>()
        for ((minutes, said) in words) {
            if (said == clock) continue
            if (runs.isNotEmpty() && runs.last().second == said) runs[runs.lastIndex] = minutes to said else runs += minutes to said
        }
        if (runs.isEmpty()) return DynamicString.constant(shown)
        val (head, tail) = sharedEnds(runs.map { it.second })
        fun middle(said: String) = said.substring(head.length, said.length - tail.length)
        var chosen = DynamicString.constant(middle(runs.last().second))
        for (i in runs.lastIndex - 1 downTo 0) {
            val (upTo, said) = runs[i]
            chosen = DynamicString.onCondition(sinceStartMinutes(endAt).gte(-upTo)).use(middle(said)).elseUse(chosen)
        }
        if (head.isNotEmpty()) chosen = DynamicString.constant(head).concat(chosen)
        if (tail.isNotEmpty()) chosen = chosen.concat(DynamicString.constant(tail))
        // Fewer minutes left than the fewest it counts: from the minute below them, or, when the
        // fewest is the last minute, from the start.
        val fewest = runs.first().first
        val past = if (fewest > 0) sinceStartMinutes(endAt).gte(-(fewest - 1)) else sinceStartSeconds(endAt).gte(0)
        return DynamicString.onCondition(past).use(shown).elseUse(chosen)
    }

    /**
     * What all of [words] begin with and, after that, end with, never splitting a character that
     * takes two UTF-16 units: the pieces the platform joins again into each of them whole, so the
     * words it shapes and draws are the same.
     */
    internal fun sharedEnds(words: List<String>): Pair<String, String> {
        if (words.size < 2) return "" to ""
        val shortest = words.minOf { it.length }
        var head = 0
        while (head < shortest && words.all { it[head] == words[0][head] }) head++
        if (head > 0 && words[0][head - 1].isHighSurrogate()) head--
        var tail = 0
        while (tail < shortest - head && words.all { it[it.length - 1 - tail] == words[0][words[0].length - 1 - tail] }) tail++
        if (tail > 0 && words[0][words[0].length - tail].isLowSurrogate()) tail--
        return words[0].substring(0, head) to words[0].substring(words[0].length - tail)
    }

    /** The whole minutes since [endAt] by the platform's clock: -72 when 72 are left, and 0 or more from the start on. */
    private fun sinceStartMinutes(endAt: Long) = sinceStart(endAt).toIntMinutes()

    /** The whole seconds since [endAt] by the platform's clock: negative before it, and 0 or more from it on. */
    private fun sinceStartSeconds(endAt: Long) = sinceStart(endAt).toIntSeconds()

    private fun sinceStart(endAt: Long) =
        DynamicInstant.withSecondsPrecision(Instant.ofEpochSecond(endAt))
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())

    /**
     * Whether the platform's clock is at [at] (epoch seconds) or later: what shows an entry's
     * [TileEntry.after] in place of what it draws before it (`TileLayout`), and the reverse.
     */
    @RequiresSchemaVersion(major = 1, minor = 200)
    fun from(at: Long): DynamicBool = sinceStartSeconds(at).gte(0)

    /** Whether the platform's clock is still before [at]. */
    @RequiresSchemaVersion(major = 1, minor = 200)
    fun before(at: Long): DynamicBool = sinceStartSeconds(at).lt(0)
}
