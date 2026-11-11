package world.taqwa.app.wear.tasbeeh

import android.content.Context
import androidx.core.content.edit
import world.taqwa.app.tasbeeh.TasbeehState

/**
 * Where the watch keeps its tasbeeh (the Lead's call 10): the phrase chosen, and each phrase's own
 * count by its id, on the watch alone. Nothing here is ever sent anywhere, the phone included, and
 * the app's backup is off (the manifest), so the counts never leave the wrist.
 *
 * The counts are kept as [TasbeehCodec] writes them. Reads and writes are synchronous: a write
 * asked for on the way out ([WatchTasbeeh.flush]) is then made before the page or the activity has
 * gone, whatever becomes of the scope it was asked from.
 */
internal interface TasbeehMemory {
    /** The id of the phrase last chosen, or null before any. */
    fun selected(): String?

    fun select(id: String)

    /** What [save] last wrote for [id], or null. */
    fun state(id: String): String?

    fun save(id: String, raw: String)
}

/**
 * [TasbeehMemory] in the app's own `SharedPreferences`, the file `tasbeeh`: the key `selected`, and
 * `count.<id>` for each phrase. Each edit is applied (`edit`'s default, `apply`): written to disk
 * in the background, and Android waits for those writes before an activity is stopped, so a count
 * written on the way out is on disk by then.
 */
internal class PrefsTasbeehMemory(context: Context) : TasbeehMemory {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun selected(): String? = prefs.getString(SELECTED, null)

    override fun select(id: String) {
        prefs.edit { putString(SELECTED, id) }
    }

    override fun state(id: String): String? = prefs.getString(COUNT + id, null)

    override fun save(id: String, raw: String) {
        prefs.edit { putString(COUNT + id, raw) }
    }

    private companion object {
        const val FILE = "tasbeeh"
        const val SELECTED = "selected"
        const val COUNT = "count."
    }
}

/** A phrase's count as the watch keeps it: `"<count>,<round>"`. */
internal object TasbeehCodec {

    fun encode(state: TasbeehState): String = "${state.count},${state.round}"

    /**
     * [raw] read back as [id]'s state, as the counter can show it: anything but two whole numbers
     * with a comma between is a fresh start, 0 of round 1; the count is held to 0..[total] (a
     * target lowered on the phone may leave a count above it), and the round is at least 1.
     */
    fun decode(id: String, raw: String?, total: Int): TasbeehState {
        val parts = raw?.split(',')
        val count = parts?.getOrNull(0)?.toIntOrNull()
        val round = parts?.getOrNull(1)?.toIntOrNull()
        if (parts?.size != 2 || count == null || round == null) return TasbeehState(id, 0, 1)
        return TasbeehState(id, count.coerceIn(0, total.coerceAtLeast(0)), round.coerceAtLeast(1))
    }
}
