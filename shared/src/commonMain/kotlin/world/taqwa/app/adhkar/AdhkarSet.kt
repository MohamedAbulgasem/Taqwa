package world.taqwa.app.adhkar

/**
 * The two half days the adhkar are read in (spec §3.4): the morning set from Fajr to Dhuhr and the
 * evening set from Asr to Isha. They share most of their items; what differs is which items are in
 * each and, for a few, the wording (see [AdhkarItem.arabicFor]).
 */
enum class AdhkarSet { MORNING, EVENING }

/**
 * The key a wording is stored under in the bundled files: `"morning"` or `"evening"` for a wording
 * that belongs to one set, [BOTH_KEY] for one the two sets share. It is the file's own spelling,
 * not a display name.
 */
internal val AdhkarSet.variantKey: String
    get() = when (this) {
        AdhkarSet.MORNING -> "morning"
        AdhkarSet.EVENING -> "evening"
    }

/** The key of a wording both sets read. An item that is in one set only may still store it here. */
internal const val BOTH_KEY = "both"

/**
 * One set's wording out of a file's `{variant: text}` map: the set's own if the item has one, else
 * the shared one, else none.
 *
 * "Else none" is deliberate and is what keeps Hisn 90 out of the evening: its single wording says
 * "we have entered the morning", so the file stores it under `"morning"` rather than `"both"`, and
 * asking for the evening finds nothing instead of the wrong words.
 */
internal fun Map<String, String>.variantFor(set: AdhkarSet): String? = this[set.variantKey] ?: this[BOTH_KEY]
