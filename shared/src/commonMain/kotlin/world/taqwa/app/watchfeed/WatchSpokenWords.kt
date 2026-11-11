package world.taqwa.app.watchfeed

import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.a11y_hours
import world.taqwa.app.resources.a11y_minutes
import world.taqwa.app.resources.duration_hours_minutes
import world.taqwa.app.resources.prayer_a11y_countdown
import world.taqwa.app.resources.prayer_a11y_countdown_soon
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchSpoken

/**
 * The phone's own strings for the screen reader's countdown (`ui.spoken`, plan 1's Task 12 and
 * Ruling 38), each a template with its places kept: the Prayer screen's sentence and its
 * under-a-minute form, the hours-and-minutes join, and the two plurals for a number [n].
 */
internal interface SpokenStrings {
    suspend fun countdown(): String
    suspend fun countdownSoon(): String
    suspend fun hoursMinutes(): String
    suspend fun hours(n: Int): String
    suspend fun minutes(n: Int): String
}

/**
 * The strings from the app's resources, in the same environment `watchUiLanguage()` reads, so the
 * words and the language agree. Each place is passed back as itself (`"%1$s"`), so a template comes
 * back with its places whether or not a bare read would format it, and a plural is chosen by [n]
 * with the number's place kept.
 */
internal object ResourceSpokenStrings : SpokenStrings {
    override suspend fun countdown(): String =
        getString(Res.string.prayer_a11y_countdown, PLACE_1, PLACE_2, PLACE_3)

    override suspend fun countdownSoon(): String =
        getString(Res.string.prayer_a11y_countdown_soon, PLACE_1, PLACE_2)

    override suspend fun hoursMinutes(): String =
        getString(Res.string.duration_hours_minutes, PLACE_1, PLACE_2)

    override suspend fun hours(n: Int): String = getPluralString(Res.plurals.a11y_hours, n, PLACE_1)

    override suspend fun minutes(n: Int): String = getPluralString(Res.plurals.a11y_minutes, n, PLACE_1)

    private const val PLACE_1 = "%1\$s"
    private const val PLACE_2 = "%2\$s"
    private const val PLACE_3 = "%3\$s"
}

/** The largest number a run is asked for: the countdown's hours and minutes never reach 100. */
private const val LAST_COUNT = 99

/**
 * [phraseFor] asked for every number from 0 to 99, kept as one [WatchCount] for each run of equal
 * phrases, in order, each ending at its run's last number. A phrase may come back in a later run:
 * Arabic's دقيقة takes 0 and 1, and again 11 to 99.
 */
internal suspend fun countRuns(phraseFor: suspend (Int) -> String): List<WatchCount> {
    val runs = mutableListOf<WatchCount>()
    var phrase = phraseFor(0)
    for (n in 1..LAST_COUNT) {
        val next = phraseFor(n)
        if (next != phrase) {
            runs += WatchCount(upTo = n - 1, phrase = phrase)
            phrase = next
        }
    }
    runs += WatchCount(upTo = LAST_COUNT, phrase = phrase)
    return runs
}

/**
 * The screen reader's words for the feed: the three templates, then the hours and the minutes as
 * runs. A read that throws passes to the caller, whose sync then answers that it failed: the
 * words are never guessed.
 */
internal suspend fun watchSpoken(strings: SpokenStrings = ResourceSpokenStrings): WatchSpoken =
    WatchSpoken(
        countdown = strings.countdown(),
        countdownSoon = strings.countdownSoon(),
        hoursMinutes = strings.hoursMinutes(),
        hours = countRuns(strings::hours),
        minutes = countRuns(strings::minutes),
    )
