package world.taqwa.app.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

// `Prayer` and `ObligatoryPrayers` moved to `:widgetcore` (same package) so the iOS widget
// extension can link the widget model without linking Compose. `shared` re-exposes them via
// `api(project(":widgetcore"))`, so every import here is unchanged.

data class PrayerTime(val prayer: Prayer, val instant: Instant)

data class DayPrayerTimes(
    val date: LocalDate,
    val times: List<PrayerTime>,
    val highLatitudeRuleApplied: HighLatitudePreference?,
    val nearestLatitudeFallbackApplied: Boolean = false,
) {
    fun time(p: Prayer): Instant =
        times.firstOrNull { it.prayer == p }?.instant
            ?: error("No time computed for $p on $date")
}
