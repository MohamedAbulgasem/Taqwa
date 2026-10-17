package world.taqwa.app.feature.prayer

import kotlin.time.Duration

/**
 * The whole hours and minutes of a countdown, as the spoken sentence needs them (accessibility
 * round 1, design §4): the ring shows seconds, but a screen reader is told "Fajr in 4 hours 10
 * minutes", so the description changes once a minute rather than sixty times.
 */
internal data class CountdownParts(val hours: Int, val minutes: Int) {
    /** Nothing left to count in minutes: the sentence says "under a minute" instead of "0 minutes". */
    val underAMinute: Boolean get() = hours == 0 && minutes == 0
}

/**
 * Truncated to whole minutes, as the ring's own H:MM:SS truncates to whole seconds, so the hours
 * and minutes spoken are the ones drawn; never negative.
 */
internal fun countdownParts(countdown: Duration): CountdownParts {
    val minutes = countdown.inWholeMinutes.coerceAtLeast(0L)
    return CountdownParts((minutes / 60L).toInt(), (minutes % 60L).toInt())
}

/**
 * One prayer row as a screen reader hears it: the name, any pill after it (Jumuʿah, Set by rule),
 * then the time — the visible order, as one line instead of three stops (audit sr-prayer-rows).
 */
internal fun prayerRowDescription(name: String, pills: List<String>, time: String): String =
    (listOf(name) + pills + time).joinToString(", ")
