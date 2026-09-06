package world.taqwa.app.feature.today

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.TodayState
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.HighLatitudeCopy
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.TimelineBuilder
import world.taqwa.app.qibla.QiblaMath
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.widget.KeyValueStore
import world.taqwa.app.widget.WidgetInputsMirror
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.refreshWidgets
import kotlin.time.Instant

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NeedsLocation : TodayUiState
    data class Ready(
        val location: GeoLocation,
        val hijri: String,
        /** The same day as [hijri], Gregorian, via [PlatformFormat.longDate]. */
        val gregorian: String,
        val today: TodayState,
        val highLatitudeNote: String?,
        /** For the Qibla card: pure geometry from the location, no sensor involved. */
        val qiblaBearingDegrees: Double,
        val qiblaDistanceKm: Double,
    ) : TodayUiState
}

class TodayViewModel(
    private val engine: PrayerTimesEngine,
    private val settings: SettingsRepository,
    private val locationOf: suspend () -> GeoLocation?,
    private val now: () -> Instant,
    // The device's own formatter in the app; the locale-free English one by default, so these
    // tests read the same on a machine whose system language is Arabic.
    private val format: PlatformFormat = EnglishPlatformFormat,
    // Both are called lazily rather than resolved once, so nothing platform-specific is touched
    // until a refresh actually has something new to publish — and so a test can count the writes
    // and the refreshes this loop provokes.
    private val widgetStore: () -> KeyValueStore = { createWidgetKeyValueStore() },
    private val widgetFormat: () -> PlatformFormat = { world.taqwa.app.i18n.createPlatformFormat() },
    private val onWidgetsChanged: () -> Unit = { refreshWidgets() },
) {
    private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    /**
     * The last string actually written to the widget mirror, so [refresh] can tell a tick that
     * changed something a widget can show from the ~59 ticks a minute that changed nothing.
     *
     * Without this, [start]'s one-second loop rewrote the mirror and nudged both widget systems
     * 60 times a minute. On iOS that outran `WidgetCenter`'s ~40-70 reloads/day budget in under a
     * minute and the widget then stopped updating for the rest of the day; on Android it put two
     * Glance recompositions plus `AppWidgetManager` IPC on the caller's dispatcher every second.
     * Every field in the snapshot is now minute-granular (see `WidgetMirrorWriter.RING_STEPS`),
     * so comparing the serialised form collapses those 60 ticks to one.
     */
    private var lastWrittenMirror: String? = null

    /**
     * Runs the one-second tick until the calling coroutine is cancelled. No owned scope and no
     * `launch` of its own: the caller (`repeatOnLifecycle(STARTED) { … }` in `App.kt`) is what
     * ties this to the screen actually being on screen, not merely composed.
     *
     * `LaunchedEffect` alone was not enough — Android stops an activity (screen off, Home
     * pressed) without destroying it, so the composable, and the effect, stayed alive and this
     * loop kept writing the widget mirror once a minute for as long as the process lived.
     * `repeatOnLifecycle` cancels the block on STOP and restarts it fresh on the next START,
     * which is why the first thing this does on every (re)entry is an immediate [refresh] rather
     * than waiting out the first second — the screen must be correct the instant it reappears.
     */
    suspend fun tickWhileActive() {
        while (true) {
            refresh()
            // The timeline is time-dependent: a pip must fill and a row must dim the moment a
            // prayer arrives, with no pull-to-refresh.
            delay(1_000)
        }
    }

    suspend fun refresh() {
        val location = locationOf()
        if (location == null) {
            _state.value = TodayUiState.NeedsLocation
            return
        }
        val prefs = settings.prayerSettings.first()
        val zone = TimeZone.of(location.timeZoneId)
        val instant = now()
        val localDate = instant.toLocalDateTime(zone).date

        val yesterday = engine.timesFor(location, localDate.plus(-1, DateTimeUnit.DAY), prefs)
        val today = engine.timesFor(location, localDate, prefs)
        val tomorrow = engine.timesFor(location, localDate.plus(1, DateTimeUnit.DAY), prefs)

        // The offset is applied to the Gregorian date before conversion, never to the Hijri day
        // number — shifting the Hijri day directly can produce day 0 or day 31.
        val hijri = TabularHijriCalendar.fromGregorian(
            localDate.plus(prefs.hijriOffsetDays, DateTimeUnit.DAY),
        )

        val timeline = TimelineBuilder.build(yesterday, today, tomorrow, instant, prefs.showSunrise)
        _state.value = TodayUiState.Ready(
            location = location,
            hijri = HijriFormatter.format(hijri, format),
            gregorian = format.longDate(localDate),
            today = timeline,
            highLatitudeNote = noteFor(today),
            qiblaBearingDegrees = QiblaMath.bearing(location),
            qiblaDistanceKm = QiblaMath.distanceKm(location),
        )
        // Serialise first, then compare: the store write and the widget nudge are both skipped
        // when this tick produced a mirror identical to the last one published.
        val mirror = WidgetMirrorWriter.serializedSnapshot(
            today = timeline,
            timeZoneId = location.timeZoneId,
            format = widgetFormat(),
            days = listOf(today, tomorrow),
        )
        if (mirror != lastWrittenMirror) {
            lastWrittenMirror = mirror
            widgetStore().putString(WidgetInputsMirror.KEY, mirror)
            onWidgetsChanged()
        }
    }

    /**
     * Two distinct cases, and conflating them would be the silent fudging the spec exists to
     * prevent. An ordinary seasonal adjustment substitutes only Fajr and Isha; true polar day or
     * night means every time on screen came from a different latitude. Both sentences, in both
     * languages, live in [HighLatitudeCopy] — this only decides which one applies.
     */
    private fun noteFor(day: DayPrayerTimes): String? = HighLatitudeCopy.note(
        languageTag = format.languageTag(),
        polarFallback = day.nearestLatitudeFallbackApplied,
        rule = day.highLatitudeRuleApplied,
    )
}
