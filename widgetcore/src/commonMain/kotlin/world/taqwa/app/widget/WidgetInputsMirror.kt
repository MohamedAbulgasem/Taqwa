package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer

/**
 * The flattened, serialisable snapshot the main app writes every time prayer times or settings
 * change, and both widget processes read. A plain delimited string, not JSON — neither widget
 * target needs a JSON dependency for eight fixed fields. [ringProgress] exists only for iOS's
 * lock-screen circular complication, which the spec asks to show as a ring, not just numbers.
 * [countdownLabel] is the fully-formed, already-localised "next prayer in" phrase (e.g. "Dhuhr
 * in" / "متبقٍ على الظهر") — written once here so neither widget target has to hardcode an
 * English "in" suffix or reach into `shared`'s Compose resources itself.
 */
data class WidgetSnapshot(
    val nextPrayer: Prayer,
    /**
     * The countdown as it stood **when the mirror was written**. Kept for wire compatibility with
     * mirrors written before [nextPrayerEpochSeconds] existed, and as the value iOS's timeline
     * extrapolates from; it must never be rendered verbatim, because the mirror is only written
     * while the Today screen is open and can be hours old by the time a widget draws (I8).
     */
    val countdownMinutes: Long,
    val nextClockTime: String,
    val allClockTimes: Map<Prayer, String>,
    val currentPrayer: Prayer?,
    val languageTag: String,
    val ringProgress: Float,
    val countdownLabel: String,
    /**
     * When the next prayer actually falls, as epoch seconds — an absolute instant, so a widget can
     * derive the countdown at *render* time from its own clock instead of trusting a number frozen
     * at write time. `0` means "not recorded": a mirror written by a build from before this field
     * existed, in which case no countdown can honestly be shown.
     */
    val nextPrayerEpochSeconds: Long = 0L,
    /**
     * When the *previous* obligatory prayer fell, as epoch seconds — the other end of the interval
     * the ring fills. `0` means "not recorded", which also covers the genuine pre-Fajr case where
     * no obligatory prayer has passed yet today.
     */
    val previousPrayerEpochSeconds: Long = 0L,
    /**
     * Every obligatory prayer of the day the mirror was written and the day after, each as an
     * absolute instant with its clock string. This is what lets a widget keep counting *across*
     * prayers without the app: the next prayer is whichever entry is first after the widget's own
     * clock, not whichever one was next when the app last had Today open. Empty on a mirror from
     * a build before this field existed, in which case the older fields carry the render.
     */
    val schedule: List<ScheduledPrayer> = emptyList(),
)

/** One entry of [WidgetSnapshot.schedule]. [dayIndex] is 0 for the day of writing, 1 for the
 * day after, so a renderer can pick the five rows that belong together without a time zone. */
data class ScheduledPrayer(
    val prayer: Prayer,
    val epochSeconds: Long,
    val clockTime: String,
    val dayIndex: Int,
)

object WidgetInputsMirror {
    /** The one key both widget processes read. `WidgetMirrorWriter` (in `shared`, which needs the
     * prayer engine to produce a snapshot) writes under it; the read side lives here because the
     * iOS extension links `widgetcore` alone. */
    const val KEY = "snapshot"

    private const val FIELD_SEP = "|"
    private const val PAIR_SEP = ";"
    private const val KV_SEP = "="
    private const val SCHEDULE_SEP = "@"

    /**
     * The wire format has only ever grown, and always by appending. Seven fields is the original
     * shape, eight added [WidgetSnapshot.countdownLabel], ten added the absolute prayer instants,
     * eleven added the two-day [WidgetSnapshot.schedule].
     * [deserialize] therefore accepts *at least* the original seven and reads anything beyond that
     * positionally, defaulting what is absent — a mirror left behind by an older build must keep
     * rendering something rather than dropping the widget to its placeholder.
     */
    private const val MINIMUM_FIELD_COUNT = 7

    fun serialize(snapshot: WidgetSnapshot): String {
        val times = snapshot.allClockTimes.entries.joinToString(PAIR_SEP) { (p, t) -> "${p.name}$KV_SEP$t" }
        return listOf(
            snapshot.nextPrayer.name,
            snapshot.countdownMinutes.toString(),
            snapshot.nextClockTime,
            times,
            snapshot.currentPrayer?.name.orEmpty(),
            snapshot.languageTag,
            snapshot.ringProgress.toString(),
            snapshot.countdownLabel,
            snapshot.nextPrayerEpochSeconds.toString(),
            snapshot.previousPrayerEpochSeconds.toString(),
            snapshot.schedule.joinToString(PAIR_SEP) { e ->
                listOf(e.prayer.name, e.epochSeconds.toString(), e.clockTime, e.dayIndex.toString())
                    .joinToString(SCHEDULE_SEP)
            },
        ).joinToString(FIELD_SEP)
    }

    /** `PRAYER@epoch@clock@day` entries; anything malformed is dropped rather than thrown on, so
     * one bad entry costs one row, not the whole widget. */
    private fun parseSchedule(raw: String): List<ScheduledPrayer> =
        raw.split(PAIR_SEP).filter { it.isNotEmpty() }.mapNotNull { entry ->
            val bits = entry.split(SCHEDULE_SEP)
            if (bits.size != 4) return@mapNotNull null
            val prayer = Prayer.entries.firstOrNull { it.name == bits[0] } ?: return@mapNotNull null
            val epoch = bits[1].toLongOrNull() ?: return@mapNotNull null
            val day = bits[3].toIntOrNull() ?: return@mapNotNull null
            ScheduledPrayer(prayer, epoch, bits[2], day)
        }

    fun deserialize(raw: String): WidgetSnapshot? {
        val parts = raw.split(FIELD_SEP)
        if (parts.size < MINIMUM_FIELD_COUNT) return null
        return try {
            WidgetSnapshot(
                nextPrayer = Prayer.valueOf(parts[0]),
                countdownMinutes = parts[1].toLong(),
                nextClockTime = parts[2],
                // Destructuring `pair.split(KV_SEP)` here would throw IndexOutOfBoundsException on
                // a pair carrying no `=` at all, and that is not one of the exceptions caught
                // below — a single corrupt byte in the mirror would take the whole widget process
                // down instead of falling back to the placeholder. `substringBefore`/
                // `substringAfter` cannot throw: a pair with no separator yields the whole string
                // as the name (which `Prayer.valueOf` then rejects, giving a clean null snapshot)
                // and an empty clock time.
                allClockTimes = parts[3].split(PAIR_SEP).filter { it.isNotEmpty() }.associate { pair ->
                    Prayer.valueOf(pair.substringBefore(KV_SEP)) to pair.substringAfter(KV_SEP, "")
                },
                currentPrayer = parts[4].takeIf { it.isNotEmpty() }?.let { Prayer.valueOf(it) },
                languageTag = parts[5],
                ringProgress = parts[6].toFloat(),
                // Absent on a pre-countdownLabel mirror; the empty string tells
                // WidgetContentBuilder to fall back to the plain prayer name, no suffix.
                countdownLabel = parts.getOrElse(7) { "" },
                // Absent on any mirror written before I8. Zero is the "not recorded" sentinel that
                // tells a widget it cannot derive a countdown and must show the prayer name alone
                // rather than a stale number.
                nextPrayerEpochSeconds = parts.getOrElse(8) { "" }.toLongOrNull() ?: 0L,
                previousPrayerEpochSeconds = parts.getOrElse(9) { "" }.toLongOrNull() ?: 0L,
                schedule = parts.getOrElse(10) { "" }.let(::parseSchedule),
            )
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun read(store: KeyValueStore): WidgetSnapshot? = store.getString(KEY)?.let(::deserialize)
}
