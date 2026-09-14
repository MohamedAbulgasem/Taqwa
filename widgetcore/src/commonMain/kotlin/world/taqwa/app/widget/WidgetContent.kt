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

    /**
     * The content as it stands at [nowEpochSeconds], worked out from the snapshot's two-day
     * schedule: next is the first entry still ahead, current the last one behind, the rows the
     * five of the day the current prayer belongs to (or the next one's, before the day's first).
     * A mirror with no schedule, or one so old that every entry is behind, falls back to
     * [build], whose fields describe the moment of writing.
     */
    fun build(snapshot: WidgetSnapshot, nowEpochSeconds: Long): WidgetContent {
        val schedule = snapshot.schedule.sortedBy { it.epochSeconds }
        val next = schedule.firstOrNull { it.epochSeconds > nowEpochSeconds } ?: return build(snapshot)
        val current = schedule.lastOrNull { it.epochSeconds <= nowEpochSeconds }
        val rowDay = (current ?: next).dayIndex
        val rows = schedule.filter { it.dayIndex == rowDay }
        val languageTag = snapshot.languageTag
        val progress = current?.let {
            val total = (next.epochSeconds - it.epochSeconds).toFloat()
            if (total <= 0f) 0f else ((nowEpochSeconds - it.epochSeconds) / total).coerceIn(0f, 1f)
        } ?: 0f
        return WidgetContent(
            nextPrayerDisplayName = displayName(next.prayer, languageTag),
            countdownMinutes = (next.epochSeconds - nowEpochSeconds) / 60L,
            nextClockTime = next.clockTime,
            rows = rows.map { entry ->
                WidgetPrayerRow(
                    prayer = entry.prayer,
                    displayName = displayName(entry.prayer, languageTag),
                    clockTime = entry.clockTime,
                    isCurrent = current != null && entry.prayer == current.prayer && entry.dayIndex == current.dayIndex,
                )
            },
            ringProgress = progress,
            countdownLabel = PrayerNaming.countdownLabel(next.prayer, languageTag),
        )
    }

    private fun displayName(prayer: Prayer, languageTag: String) =
        // The interface language's own name (Sabah, Subuh, ফজর), never the English one — the
        // widget has no resources, so the table in PrayerNaming stands in for them.
        PrayerNaming.display(prayer, languageTag, PrayerNaming.name(prayer, languageTag))
}
