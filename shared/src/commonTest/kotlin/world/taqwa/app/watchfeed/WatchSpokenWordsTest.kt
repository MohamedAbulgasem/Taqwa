package world.taqwa.app.watchfeed

import kotlinx.coroutines.test.runTest
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchCount
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchPlace
import world.taqwa.app.watch.WatchRules
import world.taqwa.app.watch.WatchUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The screen reader's words the feed carries (`ui.spoken`, plan 1's Task 12 and Ruling 38): the
 * phone's own templates, and its plurals asked for every number from 0 to 99 and kept as runs, so
 * the watch never applies a plural rule of its own.
 */
class WatchSpokenWordsTest {

    @Test
    fun aCountIsKeptAsRunsOfOnePhrase() = runTest {
        assertEquals(
            listOf(WatchCount(0, "%1\$s hours"), WatchCount(1, "%1\$s hour"), WatchCount(99, "%1\$s hours")),
            countRuns { n -> englishHours(n) },
        )
        assertEquals(
            listOf(
                WatchCount(1, "%1\$s دقيقة"),
                WatchCount(2, "%1\$s دقيقتين"),
                WatchCount(10, "%1\$s دقائق"),
                WatchCount(99, "%1\$s دقيقة"),
            ),
            countRuns { n -> ARABIC_MINUTES.getValue(arabicCategory(n)) },
        )
        assertEquals(listOf(WatchCount(99, "%1\$s jam")), countRuns { "%1\$s jam" })
    }

    @Test
    fun theSentencesAreTheTemplates() = runTest {
        val spoken = watchSpoken(EnglishSpoken)
        assertEquals(EnglishSpoken.countdown(), spoken.countdown)
        assertEquals(EnglishSpoken.countdownSoon(), spoken.countdownSoon)
        assertEquals(EnglishSpoken.hoursMinutes(), spoken.hoursMinutes)
    }

    @Test
    fun aReadThatThrowsIsNotGuessed() = runTest {
        val failing = object : SpokenStrings by EnglishSpoken {
            override suspend fun minutes(n: Int): String = throw IllegalStateException("no plural for $n")
        }
        assertFailsWith<IllegalStateException> { watchSpoken(failing) }
    }

    @Test
    fun theWatchSaysWhatThePhoneSays() = runTest {
        val feed = WatchFeed(
            version = WatchFeedJson.VERSION,
            writtenAt = 0L,
            days = emptyList(),
            place = WatchPlace(city = "", qiblaBearing = 0.0, qiblaKm = "", declination = 0.0, southern = false),
            ui = WatchUi(
                language = "en",
                rtl = false,
                twelveHour = false,
                am = "AM",
                pm = "PM",
                countdown = WatchWords.countdown(UiLanguage.ENGLISH),
                nextIn = "%1\$s in",
                spoken = watchSpoken(EnglishSpoken),
            ),
        )
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", WatchRules.spokenCountdown(feed, "Asr", "16:10", 4_320))
        assertEquals("Asr in under a minute, at 16:10", WatchRules.spokenCountdown(feed, "Asr", "16:10", 59))
    }

    /** The phone's English strings, with English's plural rule: one for 1, other for every other number. */
    private object EnglishSpoken : SpokenStrings {
        override suspend fun countdown(): String = "%1\$s in %2\$s, at %3\$s"
        override suspend fun countdownSoon(): String = "%1\$s in under a minute, at %2\$s"
        override suspend fun hoursMinutes(): String = "%1\$s %2\$s"
        override suspend fun hours(n: Int): String = englishHours(n)
        override suspend fun minutes(n: Int): String = if (n == 1) "%1\$s minute" else "%1\$s minutes"
    }

    private companion object {
        fun englishHours(n: Int): String = if (n == 1) "%1\$s hour" else "%1\$s hours"

        /** CLDR's Arabic rule over 0 to 99: 0 zero, 1 one, 2 two, 3 to 10 few, 11 to 99 many. */
        fun arabicCategory(n: Int): String = when (n) {
            0 -> "zero"
            1 -> "one"
            2 -> "two"
            in 3..10 -> "few"
            else -> "many"
        }

        /** `values-ar`'s `a11y_minutes` items. */
        val ARABIC_MINUTES = mapOf(
            "zero" to "%1\$s دقيقة",
            "one" to "%1\$s دقيقة",
            "two" to "%1\$s دقيقتين",
            "few" to "%1\$s دقائق",
            "many" to "%1\$s دقيقة",
            "other" to "%1\$s دقيقة",
        )
    }
}
