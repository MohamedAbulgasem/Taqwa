package world.taqwa.app.feature.settings

import world.taqwa.app.domain.Prayer
import kotlin.math.abs

/**
 * A timetable Match my mosque can offer (spec §2.2): its [id] ("automatic" or a registry entry) and
 * today's Fajr and Isha as minutes of the place's own day.
 */
data class MosqueCandidate(val id: String, val fajr: Int, val isha: Int)

/** What the board's two times found among the candidates. */
sealed interface MosqueMatch {
    /** Both times match: equal ([exact]) or at most a minute later, never earlier. */
    data class Both(val id: String, val exact: Boolean) : MosqueMatch

    /** Both times match an hour apart, the same way: the board may be on summer or winter time. */
    data class HourApart(val id: String) : MosqueMatch

    /** Only [matched] matches; [differs] is the prayer whose time is not the board's. */
    data class OneTime(val id: String, val matched: Prayer, val differs: Prayer) : MosqueMatch

    data object None : MosqueMatch
}

/**
 * Match my mosque (spec §2.2): the board's Fajr and Isha against each candidate's. A time matches
 * when the candidate's is the board's or at most [LATER_BY] minute later, never earlier, so the
 * timetable chosen never shows a prayer before the mosque does. Of the matches an exact one wins,
 * then the latest (the most minutes after the board), then the first in the list; the same holds
 * for the timetables that match an hour apart.
 *
 * Times compare on a 12-hour dial: a board that prints Isha as 8:27 means 20:27, and no Fajr and
 * Isha are twelve hours from where a timetable puts them.
 */
object MosqueMatcher {

    /** How much later than the board a candidate's time may be and still match. */
    const val LATER_BY = 1

    /** An hour: a board still on winter time, or on summer time in winter. */
    private const val HOUR = 60
    private const val HALF_DAY = 12 * 60

    fun match(fajr: Int, isha: Int, candidates: List<MosqueCandidate>): MosqueMatch {
        best(matchesShiftedBy(fajr, isha, candidates, listOf(0)))?.let { (fit, exact) ->
            return MosqueMatch.Both(fit.candidate.id, exact)
        }
        best(matchesShiftedBy(fajr, isha, candidates, listOf(HOUR, -HOUR)))?.let { (fit, _) ->
            return MosqueMatch.HourApart(fit.candidate.id)
        }

        // One time only: the candidate whose other time is nearest the board's.
        val one = candidates.mapNotNull { c ->
            val f = later(fajr, c.fajr)
            val i = later(isha, c.isha)
            when {
                matches(f) -> MosqueMatch.OneTime(c.id, Prayer.FAJR, Prayer.ISHA) to abs(i)
                matches(i) -> MosqueMatch.OneTime(c.id, Prayer.ISHA, Prayer.FAJR) to abs(f)
                else -> null
            }
        }
        return one.minByOrNull { it.second }?.first ?: MosqueMatch.None
    }

    /** A candidate whose two times match the board's moved by one of the shifts, [laterBy] minutes after them in all. */
    private class Fit(val index: Int, val candidate: MosqueCandidate, val laterBy: Int)

    private fun matchesShiftedBy(fajr: Int, isha: Int, candidates: List<MosqueCandidate>, shifts: List<Int>): List<Fit> =
        candidates.withIndex().flatMap { (index, c) ->
            shifts.mapNotNull { shift ->
                val f = later(fajr, c.fajr) - shift
                val i = later(isha, c.isha) - shift
                if (matches(f) && matches(i)) Fit(index, c, f + i) else null
            }
        }

    /** An exact fit first, then the latest, then the first listed; and whether it is exact. */
    private fun best(fits: List<Fit>): Pair<Fit, Boolean>? {
        if (fits.isEmpty()) return null
        fits.filter { it.laterBy == 0 }.minByOrNull { it.index }?.let { return it to true }
        return fits.maxWith(compareBy<Fit> { it.laterBy }.thenByDescending { it.index }) to false
    }

    private fun matches(laterBy: Int) = laterBy in 0..LATER_BY

    /** How many minutes [candidate] is after [board], on the 12-hour dial: −360 < result ≤ 360. */
    private fun later(board: Int, candidate: Int): Int {
        val d = ((candidate - board) % HALF_DAY + HALF_DAY) % HALF_DAY
        return if (d > HALF_DAY / 2) d - HALF_DAY else d
    }

    /**
     * A time as typed from a board, as minutes of the day, or null while it is not one yet:
     * "5:14", "05.14", "5h14", "514" and "20:27" all read, in Western, Arabic-Indic, Persian or
     * Bengali digits.
     */
    fun parseBoardTime(text: String): Int? {
        val groups = mutableListOf<StringBuilder>()
        var inGroup = false
        for (c in text) {
            val digit = digitOf(c)
            if (digit != null) {
                if (!inGroup) groups += StringBuilder()
                groups.last().append(digit)
                inGroup = true
            } else {
                inGroup = false
            }
        }
        val (hours, minutes) = when (groups.size) {
            1 -> groups[0].let { if (it.length in 3..4) it.dropLast(2).toString() to it.takeLast(2).toString() else return null }
            2 -> groups[0].toString() to groups[1].toString().takeIf { it.length == 2 }
            else -> return null
        }
        val h = hours.toIntOrNull() ?: return null
        val m = minutes?.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun digitOf(c: Char): Char? = when (c) {
        in '0'..'9' -> c
        in '٠'..'٩' -> '0' + (c - '٠')
        in '۰'..'۹' -> '0' + (c - '۰')
        in '০'..'৯' -> '0' + (c - '০')
        else -> null
    }
}
