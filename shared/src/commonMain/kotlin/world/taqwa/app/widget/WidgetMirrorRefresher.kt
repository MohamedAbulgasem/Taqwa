package world.taqwa.app.widget

import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.TimelineBuilder
import world.taqwa.app.settings.SettingsRepository
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Rewrites the widget mirror from the stored location and settings, with no screen involved.
 *
 * `TodayViewModel` writes the mirror while Today is open, and the mirror's two-day schedule lets a
 * widget count across prayers on its own after that. This is what pushes the horizon forward when
 * the app has not been opened: Android calls it from the prayer alarm and the boot/time-change
 * receiver, iOS from its daily background refresh. Cheap (three days of prayer times) and quiet:
 * no location fix is requested, and a missing location simply writes nothing.
 */
object WidgetMirrorRefresher {

    suspend fun refresh(
        settings: SettingsRepository,
        engine: PrayerTimesEngine,
        store: KeyValueStore = createWidgetKeyValueStore(),
        format: PlatformFormat = createPlatformFormat(),
        now: Instant = Clock.System.now(),
    ): Boolean {
        val location = settings.location.first() ?: return false
        val prefs = settings.prayerSettings.first()
        val zone = TimeZone.of(location.timeZoneId)
        val localDate = now.toLocalDateTime(zone).date
        val yesterday = engine.timesFor(location, localDate.plus(-1, DateTimeUnit.DAY), prefs)
        val today = engine.timesFor(location, localDate, prefs)
        val tomorrow = engine.timesFor(location, localDate.plus(1, DateTimeUnit.DAY), prefs)
        val timeline = TimelineBuilder.build(yesterday, today, tomorrow, now, prefs.showSunrise, zone)
        WidgetMirrorWriter.write(store, timeline, location.timeZoneId, format, listOf(today, tomorrow))
        return true
    }
}
