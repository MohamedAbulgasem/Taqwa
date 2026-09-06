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
)

/** Turns a raw [WidgetSnapshot] into display strings, applying the same Arabic-alone naming
 * rule (Task 22) the timeline uses — a widget is not exempt from it. */
object WidgetContentBuilder {
    fun build(snapshot: WidgetSnapshot): WidgetContent = WidgetContent(
        nextPrayerDisplayName = displayName(snapshot.nextPrayer, snapshot.languageTag),
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
    )

    private fun displayName(prayer: Prayer, languageTag: String) =
        PrayerNaming.display(prayer, languageTag, PrayerNaming.englishName(prayer))
}
