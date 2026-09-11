package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.tasbeeh.TasbeehState
import kotlin.time.Clock

/**
 * Where the counter keeps its place (spec Tasbeeh §6), in the app's DataStore beside
 * [BookmarkStore]: the selected preset, a count and round per preset, and the reader's own
 * phrases. Nothing is aggregated — there is no daily or lifetime total anywhere, by design.
 *
 * Deliberately plain: it writes what it is told, when it is told. Coalescing a run of taps into
 * one write is the view model's job, because only the screen knows when the taps stopped.
 * [now] is injectable so tests can pin the ids custom presets are given.
 */
class TasbeehStore(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val selectedId: Flow<String> =
        store.data.map { prefs -> prefs[SettingsKeys.TASBEEH_SELECTED] ?: TasbeehPresets.DEFAULT_ID }

    /** This preset's own place, count 0 and round 1 until it has been counted. */
    fun stateOf(presetId: String): Flow<TasbeehState> =
        store.data.map { prefs -> parseState(presetId, prefs[SettingsKeys.tasbeehStateKey(presetId)]) }

    /** Creation order, oldest first; an entry that does not parse is dropped, never thrown on. */
    val customPresets: Flow<List<TasbeehPreset>> =
        store.data.map { prefs -> parseCustom(prefs[SettingsKeys.TASBEEH_CUSTOM].orEmpty()) }

    suspend fun select(presetId: String) {
        store.edit { it[SettingsKeys.TASBEEH_SELECTED] = presetId }
    }

    suspend fun save(state: TasbeehState) {
        store.edit {
            it[SettingsKeys.tasbeehStateKey(state.presetId)] = "${state.count}:${state.round}"
        }
    }

    /**
     * Adds a phrase of the reader's own and returns it. The phrase is trimmed and cut to the 60
     * characters the sheet allows, the target held to 1..1000, and the id is the moment of
     * creation — which also orders the chips, and is nudged forward if a second phrase somehow
     * lands in the same millisecond, since the id is a storage key and must be unique.
     */
    suspend fun addCustom(phrase: String, target: Int): TasbeehPreset {
        // A pasted phrase could in principle carry the separator itself, which would split
        // one entry into four fields and be dropped on the next read as malformed.
        val cleaned = phrase.filterNot { it == FIELD_SEP }.trim().take(MAX_PHRASE)
        val bounded = target.coerceIn(1, MAX_TARGET)
        var made: TasbeehPreset? = null
        store.edit { prefs ->
            val existing = prefs[SettingsKeys.TASBEEH_CUSTOM].orEmpty()
            val taken = existing.map { it.substringBefore(FIELD_SEP) }.toSet()
            var stamp = now()
            while (taken.contains("custom_$stamp")) stamp++
            val preset = TasbeehPresets.custom("custom_$stamp", cleaned, bounded)
            made = preset
            prefs[SettingsKeys.TASBEEH_CUSTOM] = existing + encode(preset)
        }
        return made!!
    }

    /**
     * Forgets a custom phrase along with its count — leaving the state key behind would hand a
     * stale count to the next `custom_<millis>` that happened to collide with it — and falls the
     * selection back to the default when the phrase deleted is the one on screen.
     */
    suspend fun removeCustom(id: String) {
        store.edit { prefs ->
            prefs[SettingsKeys.TASBEEH_CUSTOM] = prefs[SettingsKeys.TASBEEH_CUSTOM].orEmpty()
                .filterNot { it.substringBefore(FIELD_SEP) == id }
                .toSet()
            prefs.remove(SettingsKeys.tasbeehStateKey(id))
            if (prefs[SettingsKeys.TASBEEH_SELECTED] == id) {
                prefs[SettingsKeys.TASBEEH_SELECTED] = TasbeehPresets.DEFAULT_ID
            }
        }
    }

    private fun encode(preset: TasbeehPreset): String =
        "${preset.id}$FIELD_SEP${preset.parts.first().dhikr.arabic}$FIELD_SEP${preset.total}"

    private fun parseState(presetId: String, raw: String?): TasbeehState {
        val blank = TasbeehState(presetId, 0, 1)
        val fields = raw?.split(':') ?: return blank
        if (fields.size != 2) return blank
        val count = fields[0].toIntOrNull() ?: return blank
        val round = fields[1].toIntOrNull() ?: return blank
        return TasbeehState(presetId, count.coerceAtLeast(0), round.coerceAtLeast(1))
    }

    /** Sorted by the millisecond in the id, which is creation order; a set has none of its own. */
    private fun parseCustom(raw: Set<String>): List<TasbeehPreset> = raw.mapNotNull { entry ->
        val fields = entry.split(FIELD_SEP)
        if (fields.size != 3) return@mapNotNull null
        val id = fields[0]
        val phrase = fields[1]
        if (id.isBlank() || phrase.isBlank()) return@mapNotNull null
        val target = fields[2].toIntOrNull() ?: return@mapNotNull null
        TasbeehPresets.custom(id, phrase, target.coerceIn(1, MAX_TARGET))
    }.sortedBy { it.id.removePrefix("custom_").toLongOrNull() ?: Long.MAX_VALUE }

    private companion object {
        // Written as an escape, never pasted raw: U+001F is invisible and would not survive an
        // edit. The same separator the widget mirrors use.
        const val FIELD_SEP = '\u001F'
        const val MAX_PHRASE = 60
        const val MAX_TARGET = 1000
    }
}
