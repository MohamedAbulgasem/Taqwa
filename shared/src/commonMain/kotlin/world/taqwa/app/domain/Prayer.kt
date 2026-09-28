package world.taqwa.app.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.registry.EntryClass

// `Prayer` and `ObligatoryPrayers` moved to `:widgetcore` (same package) so the iOS widget
// extension can link the widget model without linking Compose. `shared` re-exposes them via
// `api(project(":widgetcore"))`, so every import here is unchanged.

data class PrayerTime(val prayer: Prayer, val instant: Instant)

/**
 * One date's times as the app shows them, from the never-early engine (spec §3.3); every instant is
 * a whole minute.
 *
 * - [times]: the six the screen, the notifications and the widgets read.
 * - [asrOther]: the other school's Asr; [sunset]: the astronomical sunset, rounded down;
 *   [endOfEating]: when the fast begins, never after Fajr.
 * - [ends]: when each prayer's time ends (Fajr at sunrise, Dhuhr at the Standard Asr, Asr at
 *   sunset, Maghrib at the earlier of Isha and the red twilight, Isha at the next end of eating),
 *   where the day has one.
 * - [setByRule]: the times a high-latitude rule set; [polar]: the sun does not both rise and set
 *   at the place on this date.
 * - [sourceEntryId]: the registry entry the times follow; [entryClass]: how sure Taqwa is of them
 *   at this place.
 * - [highLatitudeRuleApplied] and [nearestLatitudeFallbackApplied] are the old engine's two flags,
 *   kept for their readers until the Prayer screen's rework (Task 10): the first is set when a rule
 *   set Fajr or Isha (the legacy rule when one applied, else [HighLatitudePreference.AUTOMATIC]),
 *   the second is [polar].
 */
data class DayPrayerTimes(
    val date: LocalDate,
    val times: List<PrayerTime>,
    val highLatitudeRuleApplied: HighLatitudePreference?,
    val nearestLatitudeFallbackApplied: Boolean = false,
    val asrOther: Instant,
    val sunset: Instant,
    val endOfEating: Instant,
    val ends: Map<Prayer, Instant> = emptyMap(),
    val setByRule: Set<Prayer> = emptySet(),
    val polar: Boolean = false,
    val sourceEntryId: String,
    val entryClass: EntryClass,
) {
    fun time(p: Prayer): Instant =
        times.firstOrNull { it.prayer == p }?.instant
            ?: error("No time computed for $p on $date")
}
