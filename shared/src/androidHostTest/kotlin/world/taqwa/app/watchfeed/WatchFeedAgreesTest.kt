package world.taqwa.app.watchfeed

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TodayState
import world.taqwa.app.i18n.AndroidPlatformFormat
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.TimelineBuilder
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchRules
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * The real engine against itself, at run time: a rule, never a figure. The feed's instants are the
 * engine's own, and the watch's rules over the feed give what the Prayer screen and the widgets
 * give over the same engine days, every ten minutes of the month the feed holds.
 */
class WatchFeedAgreesTest {

    private val engine = PrayerTimesEngine()
    private val prefs = PrayerSettings()

    private class Place(val name: String, val location: GeoLocation, val now: Instant, val tag: String, val language: UiLanguage)

    private val places = listOf(
        Place("Cape Town", GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg"), Instant.parse("2026-10-18T06:00:00Z"), "en", UiLanguage.ENGLISH),
        // From 20 October, so the month crosses the clocks going back on the 25th.
        Place("London", GeoLocation(51.5074, -0.1278, "Europe/London"), Instant.parse("2026-10-20T07:00:00Z"), "en-GB", UiLanguage.ENGLISH),
        Place("Tromsø", GeoLocation(69.6492, 18.9553, "Europe/Oslo"), Instant.parse("2026-05-15T10:00:00Z"), "en", UiLanguage.ENGLISH),
        // A Saudi phone set to Western digits: its tag keeps the numbering extension in the feed.
        Place("Makkah", GeoLocation(21.4225, 39.8262, "Asia/Riyadh"), Instant.parse("2026-10-18T06:00:00Z"), "ar-SA-u-nu-latn", UiLanguage.ARABIC),
    )

    private fun feedFor(place: Place): WatchFeed = assertNotNull(
        WatchFeedBuilder.build(
            WatchFeedInputs(
                location = place.location,
                city = place.name,
                language = place.language,
                spoken = null,
                screens = BundledScreenStrings.words(place.language),
                prayerSettings = prefs,
                format = AndroidPlatformFormat(Locale.forLanguageTag(place.tag), ClockFormat.TWENTY_FOUR_HOUR),
                clockFormat = ClockFormat.TWENTY_FOUR_HOUR,
                phrases = emptyList(),
                now = place.now,
                timesFor = { engine.timesFor(place.location, it, prefs) },
            ),
        ),
        place.name,
    )

    @Test
    fun everyInstantIsTheEngines() {
        for (place in places) {
            val feed = feedFor(place)
            assertEquals(31, feed.days.size, place.name)
            for (day in feed.days) {
                val engineDay = engine.timesFor(place.location, LocalDate.parse(day.date), prefs)
                val label = "${place.name} ${day.date}"
                assertEquals(engineDay.time(Prayer.FAJR).epochSeconds, day.times.fajr, label)
                assertEquals(engineDay.time(Prayer.SUNRISE).epochSeconds, day.times.sunrise, label)
                assertEquals(engineDay.time(Prayer.DHUHR).epochSeconds, day.times.dhuhr, label)
                assertEquals(engineDay.time(Prayer.ASR).epochSeconds, day.times.asr, label)
                assertEquals(engineDay.time(Prayer.MAGHRIB).epochSeconds, day.times.maghrib, label)
                assertEquals(engineDay.time(Prayer.ISHA).epochSeconds, day.times.isha, label)
                assertEquals(engineDay.ends[Prayer.FAJR]?.epochSeconds, day.times.fajrEnd, label)
                assertEquals(engineDay.ends[Prayer.ASR]?.epochSeconds, day.times.asrEnd, label)
            }
            assertEquals(place.tag, feed.ui.language, place.name)
        }
    }

    @Test
    fun theWatchAgreesWithThePrayerScreenAndTheWidgets() {
        for (place in places) {
            val feed = feedFor(place)
            val zone = TimeZone.of(place.location.timeZoneId)
            val today = place.now.toLocalDateTime(zone).date
            // After the last Isha the watch has no tomorrow, and answers no moment.
            val end = feed.days.last().times.isha
            var t = today.atStartOfDayIn(zone).epochSeconds
            var samples = 0
            while (t < end) {
                val now = Instant.fromEpochSeconds(t)
                val date = now.toLocalDateTime(zone).date
                val yesterday = engine.timesFor(place.location, date.plus(-1, DateTimeUnit.DAY), prefs)
                val day = engine.timesFor(place.location, date, prefs)
                val tomorrow = engine.timesFor(place.location, date.plus(1, DateTimeUnit.DAY), prefs)
                val widgets = TimelineBuilder.buildForWidgets(yesterday, day, tomorrow, now, false, zone)
                val screen = TimelineBuilder.build(yesterday, day, tomorrow, now, false, zone)
                val label = "${place.name} at $now"

                val moment = assertNotNull(WatchRules.moment(feed, t), label)
                assertEquals(widgets.next.prayer.name, moment.next.prayer.name, label)
                assertEquals(widgets.next.instant.epochSeconds, moment.next.at, label)
                assertEquals(widgets.countdown.inWholeSeconds, moment.secondsLeft, label)
                assertEquals(widgets.ringProgress, moment.progress, label)
                assertEquals(screen.current(), WatchRules.litOnList(feed, t)?.name, "$label, the list")
                assertEquals(widgets.current(), WatchRules.litOnGlance(feed, t)?.name, "$label, the glance")

                samples++
                t += 600
            }
            // A month of samples: the feed's 30 days from today, less the evening after the last Isha.
            check(samples > 29 * 144) { "${place.name}: only $samples samples" }
        }
    }

    private fun TodayState.current(): String? = rows.firstOrNull { it.status == PrayerStatus.CURRENT }?.prayer?.name
}
