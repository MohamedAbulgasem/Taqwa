package world.taqwa.app.widget

import world.taqwa.app.domain.Prayer

/**
 * The flattened, serialisable snapshot the main app writes every time prayer times or settings
 * change, and both widget processes read. A plain delimited string, not JSON — neither widget
 * target needs a JSON dependency for seven fixed fields. [ringProgress] exists only for iOS's
 * lock-screen circular complication, which the spec asks to show as a ring, not just numbers.
 */
data class WidgetSnapshot(
    val nextPrayer: Prayer,
    val countdownMinutes: Long,
    val nextClockTime: String,
    val allClockTimes: Map<Prayer, String>,
    val currentPrayer: Prayer?,
    val languageTag: String,
    val ringProgress: Float,
)

object WidgetInputsMirror {
    private const val FIELD_SEP = "|"
    private const val PAIR_SEP = ";"
    private const val KV_SEP = "="

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
        ).joinToString(FIELD_SEP)
    }

    fun deserialize(raw: String): WidgetSnapshot? {
        val parts = raw.split(FIELD_SEP)
        if (parts.size != 7) return null
        return try {
            WidgetSnapshot(
                nextPrayer = Prayer.valueOf(parts[0]),
                countdownMinutes = parts[1].toLong(),
                nextClockTime = parts[2],
                allClockTimes = parts[3].split(PAIR_SEP).filter { it.isNotEmpty() }.associate { pair ->
                    val (name, time) = pair.split(KV_SEP, limit = 2)
                    Prayer.valueOf(name) to time
                },
                currentPrayer = parts[4].takeIf { it.isNotEmpty() }?.let { Prayer.valueOf(it) },
                languageTag = parts[5],
                ringProgress = parts[6].toFloat(),
            )
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }
}
