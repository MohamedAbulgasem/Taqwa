package world.taqwa.app.widget

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TodayState
import world.taqwa.app.i18n.PlatformFormat
import kotlin.time.Instant

/** Stays in `shared`: producing a snapshot needs `TodayState` from the prayer engine. The read
 * side is `WidgetInputsMirror.read`, in `:widgetcore`, which is all the iOS widget extension
 * links. */
object WidgetMirrorWriter {

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
        )
        store.putString(WidgetInputsMirror.KEY, WidgetInputsMirror.serialize(snapshot))
    }

    fun read(store: KeyValueStore): WidgetSnapshot? = WidgetInputsMirror.read(store)
}
