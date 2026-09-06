package world.taqwa.app.feature.today

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import world.taqwa.app.settings.SettingsRepository
import kotlin.time.Instant

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NeedsLocation : TodayUiState
    data class Ready(
        val location: GeoLocation,
        val hijri: String,
        val today: TodayState,
        val highLatitudeNote: String?,
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
) {
    private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                refresh()
                // The timeline is time-dependent: a pip must fill and a row must dim the
                // moment a prayer arrives, with no pull-to-refresh. The caller passes a scope
                // tied to the composable, so this loop dies with the screen.
                delay(1_000)
            }
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
            today = timeline,
            highLatitudeNote = noteFor(today),
        )
        world.taqwa.app.widget.WidgetMirrorWriter.write(
            store = world.taqwa.app.widget.createWidgetKeyValueStore(),
            today = timeline,
            timeZoneId = location.timeZoneId,
            format = world.taqwa.app.i18n.createPlatformFormat(),
        )
        world.taqwa.app.widget.refreshWidgets()
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
