package world.taqwa.app.wear

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import world.taqwa.app.wear.complication.ComplicationTimelines
import world.taqwa.app.wear.complication.saysUntil
import world.taqwa.app.wear.link.DailyNudge

/**
 * When opening the watch app asks the complications and the Tile to be requested again (the
 * owner's decision of 9 November): only when something they show could have changed since the app
 * last asked for both, or that was [SurfaceRefresh.MAX_AGE_SECONDS] ago.
 */
class SurfaceRefreshTest {

    private val now = TestFeeds.at(0, 14, 58)

    private val inputs = SurfaceInputs(
        feed = TestFeeds.sample.writtenAt,
        newerVersion = false,
        day = LocalDate.of(2026, 10, 18).toEpochDay(),
        zone = "Africa/Johannesburg",
        locales = "en-ZA",
        twentyFourHour = true,
        versionCode = 100_043L,
        installedAt = 1_794_200_000_000L,
    )

    /** The app asked an hour ago, for [inputs]. */
    private val anHourAgo = SurfaceStamp(inputs, now - 3_600L)

    @Test
    fun neverAskedIsDue() {
        // A first install, and a reinstall, which clears the app's storage and the stamp with it.
        assertTrue(SurfaceRefresh.due(last = null, inputs = inputs, now = now))
    }

    @Test
    fun theSameFeedOnTheSameDayIsNotDue() {
        assertFalse(SurfaceRefresh.due(anHourAgo, inputs, now))
        assertFalse(SurfaceRefresh.due(SurfaceStamp(inputs, now), inputs, now), "nor at once, as an open after a feed is")
    }

    @Test
    fun anotherFeedIsDue() {
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(feed = TestFeeds.another().writtenAt), now))
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(feed = null), now), "a feed that is gone is a change too")
        assertTrue(SurfaceRefresh.due(SurfaceStamp(inputs.copy(feed = null), now - 60L), inputs, now), "and one that is new")
    }

    @Test
    fun aNewerFormatSeenIsDue() {
        // The Tile says "Update Taqwa on your watch" from then on.
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(newerVersion = true), now))
    }

    @Test
    fun aNewDateIsDue() {
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(day = inputs.day + 1), now))
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(day = inputs.day - 1), now), "a date moved back as well")
    }

    @Test
    fun aNewTimeZoneIsDue() {
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(zone = "Europe/London"), now))
    }

    @Test
    fun aNewLanguageIsDue() {
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(locales = "ar-EG"), now))
    }

    @Test
    fun aNewClockIsDue() {
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(twentyFourHour = false), now))
    }

    @Test
    fun anUpdateIsDue() {
        // An update keeps the app's storage, the stamp included, so the version tells it.
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(versionCode = inputs.versionCode + 1), now))
    }

    @Test
    fun aBuildInstalledOverOneOfTheSameCodeIsDue() {
        assertTrue(SurfaceRefresh.due(anHourAgo, inputs.copy(installedAt = inputs.installedAt + 60_000L), now))
    }

    @Test
    fun sixHoursIsDue() {
        assertFalse(SurfaceRefresh.due(SurfaceStamp(inputs, now - 21_599L), inputs, now))
        assertTrue(SurfaceRefresh.due(SurfaceStamp(inputs, now - 21_600L), inputs, now))
        assertTrue(SurfaceRefresh.due(SurfaceStamp(inputs, now - 3 * 86_400L), inputs, now))
    }

    @Test
    fun aStampFromTheFutureIsDue() {
        // The watch's clock was set back: the stamp says nothing of when the app last asked.
        assertTrue(SurfaceRefresh.due(SurfaceStamp(inputs, now + 60L), inputs, now))
    }

    /**
     * Why [SurfaceRefresh.MAX_AGE_SECONDS] is six hours: an opening that does not ask finds the
     * complications' timelines reaching a whole day of the daily nudge ([DailyNudge.DAY]) past it,
     * from wherever in the day the app last asked. Held on Cape Town's sample (40 hours at the
     * least) and on Tromsø in May, whose long morning (Fajr 1:31 to Dhuhr 12:52) gives the shortest
     * reach the vectors hold, 35 h 59 min, so twelve hours would fail here.
     */
    @Test
    fun anOpeningThatDoesNotAskLeavesTheTimelinesADayAhead() {
        val tromso = TestFeeds.phone("tromso-may-en")
        for ((feed, from) in listOf(TestFeeds.sample to TestFeeds.at(0, 0, 0), tromso to tromso.days[1].midnight)) {
            // A second before each whole minute: the feeds' starts are whole minutes, and a request
            // reaches least from the second before a start.
            for (asked in from - 1 until from + 2 * DailyNudge.DAY step 60L) {
                val reach = checkNotNull(ComplicationTimelines.nextPrayer(feed, asked).saysUntil())
                val lastSkipped = asked + SurfaceRefresh.MAX_AGE_SECONDS - 1
                assertTrue(reach - lastSkipped >= DailyNudge.DAY, "${feed.place.city}, asked at $asked: reaches $reach")
            }
        }
    }
}
