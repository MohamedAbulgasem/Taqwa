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
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.TodayState
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.UmmAlQuraCalendar
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

        val today = engine.timesFor(location, localDate, prefs)
        val tomorrow = engine.timesFor(location, localDate.plus(1, DateTimeUnit.DAY), prefs)

        // The offset is applied to the Gregorian date before conversion, never to the Hijri day
        // number — shifting the Hijri day directly can produce day 0 or day 31.
        val hijri = UmmAlQuraCalendar.fromGregorian(
            localDate.plus(prefs.hijriOffsetDays, DateTimeUnit.DAY),
        )

        _state.value = TodayUiState.Ready(
            location = location,
            hijri = HijriFormatter.format(hijri),
            today = TimelineBuilder.build(today, tomorrow, instant, prefs.showSunrise),
            highLatitudeNote = noteFor(today),
        )
    }

    /**
     * Two distinct cases, and conflating them would be the silent fudging the spec exists to
     * prevent. An ordinary seasonal adjustment substitutes only Fajr and Isha. True polar day or
     * night means adhan2 could not compute the day at all, so EVERY time on screen — Maghrib
     * included — came from a different latitude. The polar case therefore leads the sentence.
     *
     * It still names the Fajr/Isha rule afterwards, because that rule was selected from the
     * user's real latitude and is what produced the two times they are most likely to question.
     * Dropping it would leave a Nordic user unable to tell which substitution they are looking at.
     */
    private fun noteFor(day: DayPrayerTimes): String? = when {
        day.nearestLatitudeFallbackApplied -> buildString {
            append("The sun does not rise or set here today. All times are calculated for the ")
            append("nearest latitude where it does")
            ruleName(day.highLatitudeRuleApplied)?.let { append(", with Fajr and Isha using $it") }
            append(".")
        }
        day.highLatitudeRuleApplied == HighLatitudePreference.SEVENTH_OF_NIGHT ->
            "The sun never sets far enough here. Fajr and Isha use the one-seventh rule."
        day.highLatitudeRuleApplied == HighLatitudePreference.TWILIGHT_ANGLE ->
            "The sun never sets far enough here. Fajr and Isha use the twilight angle rule."
        day.highLatitudeRuleApplied != null ->
            "Fajr and Isha use the middle of the night rule at this latitude."
        else -> null
    }

    private fun ruleName(rule: HighLatitudePreference?): String? = when (rule) {
        HighLatitudePreference.SEVENTH_OF_NIGHT -> "the one-seventh rule"
        HighLatitudePreference.TWILIGHT_ANGLE -> "the twilight angle rule"
        HighLatitudePreference.MIDDLE_OF_NIGHT -> "the middle of the night rule"
        else -> null
    }
}
