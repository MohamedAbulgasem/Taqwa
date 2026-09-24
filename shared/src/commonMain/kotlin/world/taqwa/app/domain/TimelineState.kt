package world.taqwa.app.domain

import kotlin.time.Duration
import kotlin.time.Instant

enum class PrayerStatus { PASSED, CURRENT, UPCOMING }

data class TimelineRow(
    val prayer: Prayer,
    val instant: Instant,
    val status: PrayerStatus,
    /** Friday's Dhuhr in the location's own zone; decided by `TimelineBuilder.isJumuah`. */
    val isJumuah: Boolean = false,
)

data class TodayState(
    val rows: List<TimelineRow>,
    val next: PrayerTime,
    val countdown: Duration,
    val ringProgress: Float,
)
