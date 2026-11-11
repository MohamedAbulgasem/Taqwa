package world.taqwa.app.tasbeeh

/**
 * One phrase said on the counter (spec §3). [arabic] is what the screen shows in both
 * interfaces; [transliteration] and [meaning] are English-only and null for a custom phrase,
 * which is shown exactly as it was typed and never translated.
 */
data class Dhikr(
    val id: String,
    val arabic: String,
    val transliteration: String? = null,
    val meaning: String? = null,
)

/** A [Dhikr] and how many of it a preset asks for before the next one begins. */
data class DhikrPart(val dhikr: Dhikr, val count: Int)

/**
 * A counted sequence. One part for a single dhikr, three for the post-prayer set; counting runs
 * continuously from 1 to [total] across the parts rather than restarting at each one.
 */
data class TasbeehPreset(val id: String, val parts: List<DhikrPart>, val custom: Boolean = false) {
    val total: Int get() = parts.sumOf { it.count }
}

/** Where one preset stands: [count] is 0..total within the round, [round] counts from 1. */
data class TasbeehState(val presetId: String, val count: Int, val round: Int)

/** What a tap did, so the screen knows which of the three haptics to fire. Exactly one per tap. */
sealed interface TapEvent {
    /** An ordinary count. Also the tap that rolls a completed set over into the next round. */
    data object Tick : TapEvent

    /** The count just reached the end of part [partIndex], and another part follows it. */
    data class PartComplete(val partIndex: Int) : TapEvent

    /** The count just reached the preset's total. */
    data object SetComplete : TapEvent
}
