package world.taqwa.app.widget

import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.PrayerNaming

data class WidgetPrayerRow(val prayer: Prayer, val displayName: String, val clockTime: String, val isCurrent: Boolean)

data class WidgetContent(
    val nextPrayerDisplayName: String,
    val countdownMinutes: Long,
    val nextClockTime: String,
    val rows: List<WidgetPrayerRow>,
    /** Only meaningful to iOS's lock-screen circular complication, which draws it as a ring
     * (spec §4.5) rather than showing plain numbers alone. */
    val ringProgress: Float,
    /** The fully-formed, already-localised "next prayer in" phrase (e.g. "Dhuhr in" /
     * "متبقٍ على الظهر"). Both widget targets show this verbatim instead of composing their own
     * suffix, so a hardcoded English "IN" never leaks into a non-English locale again. */
    val countdownLabel: String,
)

/** Turns a raw [WidgetSnapshot] into display strings, applying the same Arabic-alone naming
 * rule (Task 22) the timeline uses — a widget is not exempt from it. */
object WidgetContentBuilder {
    fun build(snapshot: WidgetSnapshot): WidgetContent {
        val nextPrayerDisplayName = displayName(snapshot.nextPrayer, snapshot.languageTag)
        return WidgetContent(
            nextPrayerDisplayName = nextPrayerDisplayName,
            countdownMinutes = snapshot.countdownMinutes,
            nextClockTime = snapshot.nextClockTime,
            rows = ObligatoryPrayers.map { prayer ->
                WidgetPrayerRow(
                    prayer = prayer,
                    displayName = displayName(prayer, snapshot.languageTag),
                    clockTime = snapshot.allClockTimes[prayer].orEmpty(),
                    isCurrent = prayer == snapshot.currentPrayer,
                )
            },
            ringProgress = snapshot.ringProgress,
            // An older mirror (written before countdownLabel existed) deserialises it as "" —
            // fall back to the plain name rather than showing an empty label.
            countdownLabel = snapshot.countdownLabel.ifBlank { nextPrayerDisplayName },
        )
    }

    private fun displayName(prayer: Prayer, languageTag: String) =
        PrayerNaming.display(prayer, languageTag, PrayerNaming.englishName(prayer))
}
