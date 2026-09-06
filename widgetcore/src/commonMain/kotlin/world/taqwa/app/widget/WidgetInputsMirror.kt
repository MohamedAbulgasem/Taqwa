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
    val countdownMinutes: Long,
    val nextClockTime: String,
    val allClockTimes: Map<Prayer, String>,
    val currentPrayer: Prayer?,
    val languageTag: String,
    val ringProgress: Float,
    val countdownLabel: String,
)

object WidgetInputsMirror {
    /** The one key both widget processes read. `WidgetMirrorWriter` (in `shared`, which needs the
     * prayer engine to produce a snapshot) writes under it; the read side lives here because the
     * iOS extension links `widgetcore` alone. */
    const val KEY = "snapshot"

    private const val FIELD_SEP = "|"
    private const val PAIR_SEP = ";"
    private const val KV_SEP = "="

    /** Snapshots written before [WidgetSnapshot.countdownLabel] existed serialise seven fields;
     * [deserialize] still has to read those without throwing. */
    private const val FIELD_COUNT_BEFORE_COUNTDOWN_LABEL = 7
    private const val FIELD_COUNT = 8

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
        ).joinToString(FIELD_SEP)
    }

    fun deserialize(raw: String): WidgetSnapshot? {
        val parts = raw.split(FIELD_SEP)
        if (parts.size != FIELD_COUNT && parts.size != FIELD_COUNT_BEFORE_COUNTDOWN_LABEL) return null
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
            )
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun read(store: KeyValueStore): WidgetSnapshot? = store.getString(KEY)?.let(::deserialize)
}
