package world.taqwa.app.widget

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TodayState
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming
import kotlin.math.round
import kotlin.time.Instant

/** Stays in `shared`: producing a snapshot needs `TodayState` from the prayer engine. The read
 * side is `WidgetInputsMirror.read`, in `:widgetcore`, which is all the iOS widget extension
 * links. */
object WidgetMirrorWriter {

    /** Same key `TaqwaGlanceWidget.kt` (Android) and `TaqwaMirror.background()` (iOS) read
     * literally — the widget processes never see `SettingsRepository`/DataStore, only this
     * mirror, so the Appearance screen's choice has to be written here too. */
    private const val BACKGROUND_KEY = "widget_background"

    fun writeBackground(store: KeyValueStore, background: WidgetBackground) {
        store.putString(BACKGROUND_KEY, background.name)
    }

    /**
     * Mirrors the exact copy `Res.string.today_next_in` renders for Today's ring ("%1$s in" /
     * "متبقٍ على %1$s") as plain Kotlin instead of a resource lookup — for the same reason
     * [world.taqwa.app.i18n.HighLatitudeCopy] and
     * [world.taqwa.app.notifications.LocalizedNotificationCopy] are: this runs from a plain,
     * non-composable writer, which is what makes it testable without a resource loader. That
     * matters concretely here, not just stylistically — Compose Multiplatform's non-composable
     * `getString` calls `Resources.getSystem()` unconditionally on Android, which the local JVM
     * unit tests covering this file have no way to satisfy (there is no device, no Robolectric),
     * so it throws on every call. Two supported languages, so a plain `when` costs nothing extra
     * to keep in sync with the resource by hand.
     */
    private fun countdownLabel(prayer: Prayer, languageTag: String): String =
        if (PrayerNaming.isArabicLanguage(languageTag)) {
            "متبقٍ على ${PrayerNaming.arabicName(prayer)}"
        } else {
            "${PrayerNaming.englishName(prayer)} in"
        }

    /**
     * How many discrete steps the ring is rounded to before it reaches the mirror.
     *
     * Every other field in a [WidgetSnapshot] is minute-granular — `countdownMinutes` is
     * `inWholeMinutes`, the clock strings are `HH:mm` — but `TodayState.ringProgress` is
     * elapsed/total in *seconds*, so it takes a different value on every one of
     * `TodayViewModel`'s 1 Hz ticks. That single field is what made the serialised snapshot
     * differ every second, which defeated iOS's `lastSnapshot` dedupe and burned WidgetKit's
     * ~40-70 reloads/day budget in under a minute, after which the widget froze for the day.
     *
     * 120 steps is finer than a pixel on the lock-screen circular complication (a ~40 pt ring has
     * roughly 120 pixels of circumference at 1x), so nothing visible is lost; what is gained is
     * that the value only moves once per `total / 120` seconds — for a typical two-to-five-hour
     * gap between prayers, once every one to two and a half minutes, i.e. no more often than
     * `countdownMinutes` already moves.
     */
    private const val RING_STEPS = 120

    private fun quantisedRing(progress: Float): Float =
        round(progress.coerceIn(0f, 1f) * RING_STEPS) / RING_STEPS

    /** Builds the snapshot [write] would store, without storing it. */
    fun snapshotOf(today: TodayState, timeZoneId: String, format: PlatformFormat): WidgetSnapshot {
        val zone = TimeZone.of(timeZoneId)
        fun clock(instant: Instant): String {
            val t = instant.toLocalDateTime(zone)
            return format.clockTime(t.hour, t.minute)
        }
        return WidgetSnapshot(
            nextPrayer = today.next.prayer,
            countdownMinutes = today.countdown.inWholeMinutes,
            nextClockTime = clock(today.next.instant),
            allClockTimes = today.rows.associate { it.prayer to clock(it.instant) },
            currentPrayer = today.rows.firstOrNull { it.status == PrayerStatus.CURRENT }?.prayer,
            languageTag = format.languageTag(),
            ringProgress = quantisedRing(today.ringProgress),
            countdownLabel = countdownLabel(today.next.prayer, format.languageTag()),
            // Absolute instants, not elapsed durations. The mirror is only written while Today is
            // open, so anything relative in it is a lie the moment the screen closes; a widget
            // holding the instant can recompute against its own clock whenever it happens to draw.
            nextPrayerEpochSeconds = today.next.instant.epochSeconds,
            previousPrayerEpochSeconds = previousObligatoryInstant(today),
        )
    }

    /**
     * The other end of the interval the ring fills: the most recent obligatory prayer that has
     * already arrived. Read off the timeline's own statuses rather than recomputed, so it cannot
     * drift from what the screen is showing.
     *
     * Returns `0` — the "not recorded" sentinel — before the day's Fajr, when no obligatory prayer
     * has passed. Deliberately not synthesised: a widget reading `0` knows it has no start point,
     * which is the truth, and the interval a fabricated one would produce is precisely the flat
     * overnight ring finding I1 is about.
     */
    private fun previousObligatoryInstant(today: TodayState): Long =
        today.rows
            .filter { it.prayer in ObligatoryPrayers && it.status != PrayerStatus.UPCOMING }
            .maxByOrNull { it.instant }
            ?.instant
            ?.epochSeconds
            ?: 0L

    /**
     * The exact string [write] would store. Exposed so a caller that recomputes once a second can
     * compare it against what it last wrote and skip both the store write and `refreshWidgets()`
     * when nothing a widget could show has actually changed (see `TodayViewModel.refresh`).
     */
    fun serializedSnapshot(today: TodayState, timeZoneId: String, format: PlatformFormat): String =
        WidgetInputsMirror.serialize(snapshotOf(today, timeZoneId, format))

    fun write(store: KeyValueStore, today: TodayState, timeZoneId: String, format: PlatformFormat) {
        store.putString(WidgetInputsMirror.KEY, serializedSnapshot(today, timeZoneId, format))
    }

    fun read(store: KeyValueStore): WidgetSnapshot? = WidgetInputsMirror.read(store)
}
