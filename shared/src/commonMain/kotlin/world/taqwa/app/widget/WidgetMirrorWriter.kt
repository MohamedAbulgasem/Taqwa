package world.taqwa.app.widget

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TodayState
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming
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

    fun write(store: KeyValueStore, today: TodayState, timeZoneId: String, format: PlatformFormat) {
        val zone = TimeZone.of(timeZoneId)
        fun clock(instant: Instant): String {
            val t = instant.toLocalDateTime(zone)
            return format.clockTime(t.hour, t.minute)
        }
        val snapshot = WidgetSnapshot(
            nextPrayer = today.next.prayer,
            countdownMinutes = today.countdown.inWholeMinutes,
            nextClockTime = clock(today.next.instant),
            allClockTimes = today.rows.associate { it.prayer to clock(it.instant) },
            currentPrayer = today.rows.firstOrNull { it.status == PrayerStatus.CURRENT }?.prayer,
            languageTag = format.languageTag(),
            ringProgress = today.ringProgress,
            countdownLabel = countdownLabel(today.next.prayer, format.languageTag()),
        )
        store.putString(WidgetInputsMirror.KEY, WidgetInputsMirror.serialize(snapshot))
    }

    fun read(store: KeyValueStore): WidgetSnapshot? = WidgetInputsMirror.read(store)
}
